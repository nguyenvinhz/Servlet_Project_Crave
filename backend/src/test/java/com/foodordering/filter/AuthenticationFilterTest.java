package com.foodordering.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.foodordering.config.SessionConfigurationListener;
import com.foodordering.dto.ProfileResponse;
import com.foodordering.enums.AccountType;
import com.foodordering.enums.EmployeeRole;
import com.foodordering.exception.AccountException;
import com.foodordering.service.AccountService;
import com.foodordering.utils.JsonProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.SessionCookieConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthenticationFilterTest {
    private static final ProfileResponse CUSTOMER = new ProfileResponse("KH01", AccountType.CUSTOMER,
            "Vinh", "vinh@example.com", "0901234567", null);
    private AccountService accounts;
    private AuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        accounts = mock(AccountService.class);
        filter = new AuthenticationFilter(accounts);
    }

    @Test
    void publicPagesCatalogAndHealthDoNotRequireSessionOrDatabase() throws Exception {
        for (String path : new String[]{"/", "/auth/login", "/auth/register", "/menu", "/menu/detail",
                "/api/foods", "/api/categories", "/api/health", "/assets/css/styles.css",
                "/promotions", "/api/promotions", "/api/promotions/active"}) {
            Exchange exchange = request("GET", path, null);
            exchange.send();
            verify(exchange.chain).doFilter(exchange.request, exchange.response);
            verify(exchange.request, never()).getSession(false);
        }
        verifyNoInteractions(accounts);
    }

    @Test
    void promotionMutationsRequireAuthenticationEvenWithPrefixMappingAndMatrixParameters() throws Exception {
        for (String path : new String[]{"/api/promotions", "/api/promotions/validate", "/api/promotions;ignored/validate"}) {
            Exchange exchange = request("POST", path, null);
            when(exchange.request.getServletPath()).thenReturn(path.contains(";") ? "/api/promotions;ignored" : "/api/promotions");
            when(exchange.request.getPathInfo()).thenReturn(path.endsWith("validate") ? "/validate" : null);
            exchange.send();
            exchange.assertError(401, "UNAUTHORIZED");
            verifyNoInteractions(exchange.chain);
        }
        verifyNoInteractions(accounts);
    }

    @Test
    void cartPageRedirectsAnonymousUserToLoginAndPreservesVoucher() throws Exception {
        Exchange exchange = request("GET", "/cart", null);
        when(exchange.request.getQueryString()).thenReturn("code=SAVE10");

        exchange.send();

        verify(exchange.response).sendRedirect(
                "/crave/auth/login?returnTo=%2Fcrave%2Fcart%3Fcode%3DSAVE10");
        verifyNoInteractions(exchange.chain);
        verifyNoInteractions(accounts);
    }

    @Test
    void employeeCannotValidateCustomerVoucher() throws Exception {
        ProfileResponse employee = employee(EmployeeRole.ADMIN);
        when(accounts.getProfile("NV01")).thenReturn(employee);
        Exchange exchange = request("POST", "/api/promotions/validate", employee);

        exchange.send();

        exchange.assertError(403, "FORBIDDEN");
        verifyNoInteractions(exchange.chain);
    }

    @Test
    void customerVoucherValidationRefreshesAccountBeforeProceeding() throws Exception {
        when(accounts.getProfile("KH01")).thenReturn(CUSTOMER);
        Exchange exchange = request("POST", "/api/promotions/validate", CUSTOMER);

        exchange.send();

        verify(accounts).getProfile("KH01");
        verify(exchange.session).setAttribute("currentUser", CUSTOMER);
        verify(exchange.chain).doFilter(exchange.request, exchange.response);
        verify(exchange.response).setHeader("Cache-Control", "no-store");
    }

    @Test
    void protectedApisRejectAnonymousRequestsWith401Json() throws Exception {
        for (String path : new String[]{"/api/profile", "/api/addresses", "/api/addresses/DC01",
                "/api/orders", "/api/orders/DH01", "/api/cart", "/api/payments/TT01", "/api/admin/foods"}) {
            Exchange exchange = request("GET", path, null);
            exchange.send();
            exchange.assertError(401, "UNAUTHORIZED");
            verifyNoInteractions(exchange.chain);
        }
        verifyNoInteractions(accounts);
    }

    @Test
    void servletMatrixParametersCannotBypassAuthentication() throws Exception {
        for (String path : new String[]{"/api/admin;ignored/foods", "/api;ignored/admin/foods", "/api/profile;ignored",
                "/api/addresses;ignored/DC01", "/api/orders;jsessionid=attacker/DH01"}) {
            Exchange exchange = request("GET", path, null);
            exchange.send();
            exchange.assertError(401, "UNAUTHORIZED");
            verifyNoInteractions(exchange.chain);
        }
        verifyNoInteractions(accounts);
    }

    @Test
    void servletMatrixParametersCannotBypassAdminAuthorization() throws Exception {
        when(accounts.getProfile("KH01")).thenReturn(CUSTOMER);
        Exchange customer = request("GET", "/api/admin;ignored/foods", CUSTOMER);
        customer.send();
        customer.assertError(403, "FORBIDDEN");
        verifyNoInteractions(customer.chain);

        ProfileResponse orderStaff = employee(EmployeeRole.ORDER_STAFF);
        when(accounts.getProfile("NV01")).thenReturn(orderStaff);
        Exchange wrongRole = request("GET", "/api/admin;ignored/foods", orderStaff);
        wrongRole.send();
        wrongRole.assertError(403, "FORBIDDEN");
        verifyNoInteractions(wrongRole.chain);
    }

    @Test
    void servletMappingCanonicalPathControlsAuthentication() throws Exception {
        Exchange anonymous = request("GET", "/public/../api/admin/foods", null);
        when(anonymous.request.getServletPath()).thenReturn("/api/admin/foods");
        anonymous.send();
        anonymous.assertError(401, "UNAUTHORIZED");
        verifyNoInteractions(anonymous.chain);
        verifyNoInteractions(accounts);
    }

    @Test
    void prefixServletMappingIncludesPathInfoForAuthenticatedAddresses() throws Exception {
        when(accounts.getProfile("KH01")).thenReturn(CUSTOMER);
        Exchange exchange = request("GET", "/api/addresses/DC01", CUSTOMER);
        when(exchange.request.getServletPath()).thenReturn("/api/addresses");
        when(exchange.request.getPathInfo()).thenReturn("/DC01");
        exchange.send();
        verify(accounts).getProfile("KH01");
        verify(exchange.session).setAttribute("currentUser", CUSTOMER);
        verify(exchange.chain).doFilter(exchange.request, exchange.response);
    }

    @Test
    void protectedPageRedirectsToLoginWithEncodedOriginalUrl() throws Exception {
        Exchange exchange = request("GET", "/customer/addresses", null);
        when(exchange.request.getQueryString()).thenReturn("selected=DC01");
        exchange.send();
        verify(exchange.response).sendRedirect(
                "/crave/auth/login?returnTo=%2Fcrave%2Fcustomer%2Faddresses%3Fselected%3DDC01");
        verifyNoInteractions(exchange.chain);
    }

    @Test
    void authenticatedCustomerRefreshesPrincipalBeforeProtectedRequest() throws Exception {
        ProfileResponse fresh = new ProfileResponse("KH01", AccountType.CUSTOMER,
                "Vinh Updated", "new@example.com", "0912345678", null);
        when(accounts.getProfile("KH01")).thenReturn(fresh);
        Exchange exchange = request("GET", "/api/addresses", CUSTOMER);
        exchange.send();
        verify(exchange.session).setAttribute("currentUser", fresh);
        verify(exchange.session).setAttribute("customerId", "KH01");
        verify(exchange.session).removeAttribute("role");
        verify(exchange.chain).doFilter(exchange.request, exchange.response);
        verify(exchange.response).setHeader("Cache-Control", "no-store");
    }

    @Test
    void employeeMayReadProfileButCannotAccessCustomerAddresses() throws Exception {
        ProfileResponse employee = employee(EmployeeRole.ADMIN);
        when(accounts.getProfile("NV01")).thenReturn(employee);
        for (String path : new String[]{"/api/profile", "/customer/profile"}) {
            Exchange profile = request("GET", path, employee);
            profile.send();
            verify(profile.chain).doFilter(profile.request, profile.response);
        }
        Exchange addresses = request("GET", "/api/addresses", employee);
        addresses.send();
        addresses.assertError(403, "FORBIDDEN");
        verifyNoInteractions(addresses.chain);
    }

    @Test
    void customerCannotReachAdminApi() throws Exception {
        when(accounts.getProfile("KH01")).thenReturn(CUSTOMER);
        Exchange exchange = request("GET", "/api/admin/orders", CUSTOMER);
        exchange.send();
        exchange.assertError(403, "FORBIDDEN");
        verifyNoInteractions(exchange.chain);
    }

    @Test
    void staffRolesOnlyAccessTheirAssignedAdminResources() throws Exception {
        Object[][] cases = {
                {EmployeeRole.ORDER_STAFF, "/api/admin/orders", true},
                {EmployeeRole.ORDER_STAFF, "/api/admin/payments/TT01", true},
                {EmployeeRole.ORDER_STAFF, "/api/admin/foods", false},
                {EmployeeRole.MENU_MANAGER, "/api/admin/foods/MA01", true},
                {EmployeeRole.MENU_MANAGER, "/api/admin/categories", true},
                {EmployeeRole.MENU_MANAGER, "/api/admin/options/TC01", true},
                {EmployeeRole.MENU_MANAGER, "/api/admin/orders", false},
                {EmployeeRole.PROMOTION_MANAGER, "/api/admin/promotions", true},
                {EmployeeRole.HR_MANAGER, "/api/admin/foods", false},
                {EmployeeRole.ADMIN, "/api/admin/orders", true},
                {EmployeeRole.ADMIN, "/api/admin/foods", true}
        };
        for (Object[] entry : cases) {
            ProfileResponse employee = employee((EmployeeRole) entry[0]);
            when(accounts.getProfile("NV01")).thenReturn(employee);
            Exchange exchange = request("GET", (String) entry[1], employee);
            exchange.send();
            if ((Boolean) entry[2]) {
                verify(exchange.chain).doFilter(exchange.request, exchange.response);
            } else {
                exchange.assertError(403, "FORBIDDEN");
                verifyNoInteractions(exchange.chain);
            }
        }
    }

    @Test
    void roleDemotionTakesEffectEvenWhenSessionStillContainsAdmin() throws Exception {
        when(accounts.getProfile("NV01")).thenReturn(employee(EmployeeRole.ORDER_STAFF));
        Exchange exchange = request("GET", "/api/admin/foods", employee(EmployeeRole.ADMIN));
        exchange.send();
        exchange.assertError(403, "FORBIDDEN");
        verifyNoInteractions(exchange.chain);
    }

    @Test
    void inactiveAccountInvalidatesSessionAndRejectsRequest() throws Exception {
        when(accounts.getProfile("KH01")).thenThrow(new AccountException("ACCOUNT_INACTIVE", "Tài khoản bị khóa.", 401));
        Exchange exchange = request("GET", "/api/addresses", CUSTOMER);
        exchange.send();
        exchange.assertError(401, "ACCOUNT_INACTIVE");
        verify(exchange.session).invalidate();
        verifyNoInteractions(exchange.chain);
    }

    @Test
    void accountDatabaseFailureReturns503WithoutLeakingDetails() throws Exception {
        when(accounts.getProfile("KH01")).thenThrow(new IllegalStateException("jdbc:mysql://private-host secret"));
        Exchange exchange = request("GET", "/api/profile", CUSTOMER);
        exchange.send();
        exchange.assertError(503, "SERVICE_UNAVAILABLE");
        assertFalse(exchange.body.toString().contains("private-host"));
        verifyNoInteractions(exchange.chain);
    }

    @Test
    void crossOriginMutationIsRejectedEvenForPublicLogin() throws Exception {
        for (String origin : new String[]{"https://evil.example", "http://localhost:9090", "https://localhost:8080",
                "null", "://invalid"}) {
            Exchange exchange = request("POST", "/api/auth/login", null);
            when(exchange.request.getHeader("Origin")).thenReturn(origin);
            exchange.send();
            exchange.assertError(403, "FORBIDDEN");
            verifyNoInteractions(exchange.chain);
        }
        verifyNoInteractions(accounts);
    }

    @Test
    void crossSiteFetchMetadataRejectsMutationWithoutOrigin() throws Exception {
        Exchange exchange = request("POST", "/api/auth/logout", CUSTOMER);
        when(exchange.request.getHeader("Sec-Fetch-Site")).thenReturn("cross-site");
        exchange.send();
        exchange.assertError(403, "FORBIDDEN");
        verifyNoInteractions(exchange.chain);
    }

    @Test
    void sameOriginJsonMutationAndNonBrowserClientMayProceed() throws Exception {
        for (String origin : new String[]{"http://localhost:8080", null}) {
            Exchange exchange = request("POST", "/api/auth/login", null);
            when(exchange.request.getHeader("Origin")).thenReturn(origin);
            exchange.send();
            verify(exchange.chain).doFilter(exchange.request, exchange.response);
        }
        verifyNoInteractions(accounts);
    }

    @Test
    void sessionCookiesAreHttpOnlyAndSameSiteLax() {
        ServletContext context = mock(ServletContext.class);
        SessionCookieConfig cookie = mock(SessionCookieConfig.class);
        when(context.getSessionCookieConfig()).thenReturn(cookie);
        new SessionConfigurationListener().contextInitialized(new ServletContextEvent(context));
        verify(cookie).setHttpOnly(true);
        verify(cookie).setAttribute("SameSite", "Lax");
    }

    private static ProfileResponse employee(EmployeeRole role) {
        return new ProfileResponse("NV01", AccountType.EMPLOYEE, "Admin", "admin@example.com", "0907654321", role);
    }

    private Exchange request(String method, String path, ProfileResponse principal) throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpSession session = principal == null ? null : mock(HttpSession.class);
        FilterChain chain = mock(FilterChain.class);
        StringWriter body = new StringWriter();
        when(request.getMethod()).thenReturn(method);
        when(request.getRequestURI()).thenReturn("/crave" + path);
        when(request.getContextPath()).thenReturn("/crave");
        when(request.getScheme()).thenReturn("http");
        when(request.getServerName()).thenReturn("localhost");
        when(request.getServerPort()).thenReturn(8080);
        when(request.getSession(false)).thenReturn(session);
        if (session != null) {
            when(session.getAttribute("currentUser")).thenReturn(principal);
        }
        when(response.getWriter()).thenReturn(new PrintWriter(body));
        return new Exchange(request, response, session, chain, body, filter);
    }

    private record Exchange(HttpServletRequest request, HttpServletResponse response, HttpSession session,
                            FilterChain chain, StringWriter body, AuthenticationFilter filter) {
        void send() throws Exception {
            filter.doFilter(request, response, chain);
        }

        void assertError(int status, String code) throws Exception {
            verify(response).setStatus(status);
            JsonNode json = JsonProvider.objectMapper().readTree(body.toString());
            assertFalse(json.path("success").asBoolean());
            assertEquals(code, json.path("error").path("code").asText());
            verify(response).setContentType("application/json");
        }
    }
}
