package com.foodordering.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.foodordering.dto.AddressResponse;
import com.foodordering.dto.AddressWriteRequest;
import com.foodordering.dto.LoginRequest;
import com.foodordering.dto.ProfileResponse;
import com.foodordering.dto.ProfileUpdateRequest;
import com.foodordering.dto.RegisterRequest;
import com.foodordering.enums.AccountType;
import com.foodordering.enums.EmployeeRole;
import com.foodordering.exception.AccountException;
import com.foodordering.service.AccountService;
import com.foodordering.utils.JsonProvider;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AccountApiServletTest {

    private static final ProfileResponse CUSTOMER = new ProfileResponse("KH01", AccountType.CUSTOMER,
            "Nguyễn Quang Vinh", "vinh@example.com", "0901234567", null);
    private static final ProfileResponse EMPLOYEE = new ProfileResponse("NV01", AccountType.EMPLOYEE,
            "Admin", "admin@example.com", "0907654321", EmployeeRole.ADMIN);
    private static final String PROFILE_JSON = "{\"fullName\":\"Nguyễn Quang Vinh\","
            + "\"email\":\"vinh@example.com\",\"phone\":\"0901234567\"}";
    private static final String ADDRESS_JSON = "{\"addressLine\":\"12 Nguyễn Trãi\",\"note\":\"Cổng trước\",\"isDefault\":true}";
    private static final AddressResponse ADDRESS = new AddressResponse("DC01", "12 Nguyễn Trãi", "Cổng trước", true,
            LocalDateTime.of(2026, 10, 8, 12, 0), LocalDateTime.of(2026, 10, 8, 12, 0));
    private AccountService accounts;

    @BeforeEach
    void setUp() {
        accounts = mock(AccountService.class);
    }

    @Test
    void registerCreatesCustomerWithoutStartingSessionOrExposingPassword() throws Exception {
        when(accounts.register(any(RegisterRequest.class))).thenReturn(CUSTOMER);
        Exchange exchange = request("POST", "/api/auth/register", null,
                "{\"fullName\":\"Nguyễn Quang Vinh\",\"email\":\"vinh@example.com\",\"phone\":\"0901234567\","
                        + "\"password\":\"strongPassword123\",\"confirmPassword\":\"strongPassword123\"}", null);

        exchange.send(new AuthApiServlet(accounts));

        exchange.assertStatus(201);
        assertEquals("KH01", exchange.json().path("data").path("id").asText());
        assertTrue(exchange.json().path("success").asBoolean());
        assertFalse(exchange.body.toString().contains("password"));
        verify(exchange.request, never()).getSession();
        verify(exchange.request, never()).getSession(anyBoolean());
    }

    @Test
    void loginRotatesExistingSessionAndPublishesPrincipalForOtherDomains() throws Exception {
        when(accounts.login(any(LoginRequest.class))).thenReturn(CUSTOMER);
        Exchange exchange = request("POST", "/api/auth/login", null,
                "{\"email\":\"vinh@example.com\",\"password\":\"strongPassword123\",\"rememberMe\":false}", CUSTOMER);

        exchange.send(new AuthApiServlet(accounts));

        exchange.assertStatus(200);
        verify(exchange.request).changeSessionId();
        verify(exchange.session).setAttribute("currentUser", CUSTOMER);
        verify(exchange.session).setAttribute("userId", "KH01");
        verify(exchange.session).setMaxInactiveInterval(30 * 60);
        assertEquals("CUSTOMER", exchange.json().path("data").path("accountType").asText());
        assertFalse(exchange.body.toString().contains("strongPassword123"));
    }

    @Test
    void loginStartsNewSessionAndEmployeeRoleIsReturned() throws Exception {
        when(accounts.login(any(LoginRequest.class))).thenReturn(EMPLOYEE);
        Exchange exchange = request("POST", "/api/auth/login", null,
                "{\"email\":\"admin@example.com\",\"password\":\"strongPassword123\"}", null);
        HttpSession newSession = mock(HttpSession.class);
        when(exchange.request.getSession()).thenReturn(newSession);
        when(exchange.request.getSession(true)).thenReturn(newSession);

        exchange.send(new AuthApiServlet(accounts));

        exchange.assertStatus(200);
        verify(newSession).setAttribute("currentUser", EMPLOYEE);
        verify(newSession).setAttribute("userId", "NV01");
        assertEquals("ADMIN", exchange.json().path("data").path("role").asText());
    }

    @Test
    void switchingAccountTypesClearsPreviousRoleAndOwnerSessionAttributes() throws Exception {
        when(accounts.login(any(LoginRequest.class))).thenReturn(CUSTOMER);
        Exchange customerLogin = request("POST", "/api/auth/login", null,
                "{\"email\":\"vinh@example.com\",\"password\":\"password123\"}", EMPLOYEE);
        customerLogin.send(new AuthApiServlet(accounts));
        customerLogin.assertStatus(200);
        verify(customerLogin.session).setAttribute("customerId", "KH01");
        verify(customerLogin.session).removeAttribute("employeeId");
        verify(customerLogin.session).removeAttribute("role");

        when(accounts.login(any(LoginRequest.class))).thenReturn(EMPLOYEE);
        Exchange employeeLogin = request("POST", "/api/auth/login", null,
                "{\"email\":\"admin@example.com\",\"password\":\"password123\"}", CUSTOMER);
        employeeLogin.send(new AuthApiServlet(accounts));
        employeeLogin.assertStatus(200);
        verify(employeeLogin.session).setAttribute("employeeId", "NV01");
        verify(employeeLogin.session).setAttribute("role", "ADMIN");
        verify(employeeLogin.session).removeAttribute("customerId");
    }

    @Test
    void sessionReturnsAuthenticatedPrincipalAndRejectsAnonymousRequests() throws Exception {
        when(accounts.getProfile("KH01")).thenReturn(CUSTOMER);
        Exchange signedIn = request("GET", "/api/auth/session", null, "", CUSTOMER);
        signedIn.send(new AuthApiServlet(accounts));
        signedIn.assertStatus(200);
        assertEquals("KH01", signedIn.json().path("data").path("id").asText());

        Exchange anonymous = request("GET", "/api/auth/session", null, "", null);
        anonymous.send(new AuthApiServlet(accounts));
        anonymous.assertError(401, "UNAUTHORIZED");
    }

    @Test
    void logoutInvalidatesExistingSessionAndIsIdempotentForAnonymousUsers() throws Exception {
        Exchange signedIn = request("POST", "/api/auth/logout", null, "", CUSTOMER);
        signedIn.send(new AuthApiServlet(accounts));
        signedIn.assertStatus(200);
        verify(signedIn.session).invalidate();

        Exchange anonymous = request("POST", "/api/auth/logout", null, "", null);
        anonymous.send(new AuthApiServlet(accounts));
        anonymous.assertStatus(200);
        assertTrue(anonymous.json().path("success").asBoolean());
        verify(anonymous.request, never()).getSession(true);
        verifyNoInteractions(accounts);
    }

    @Test
    void malformedUnknownAndEmptyJsonAreBadRequestsWithoutCallingService() throws Exception {
        for (String payload : new String[]{"{", "", "null", "[]", "{\"unexpected\":true}", "{} {}"}) {
            Exchange exchange = request("POST", "/api/auth/login", null, payload, null);
            exchange.send(new AuthApiServlet(accounts));
            exchange.assertStatus(400);
            assertFalse(exchange.json().path("success").asBoolean(), payload);
        }
        verifyNoInteractions(accounts);
    }

    @Test
    void loginRejectsScalarTypesThatDoNotMatchRequestSchema() throws Exception {
        for (String payload : new String[]{
                "{\"email\":123,\"password\":\"password123\"}",
                "{\"email\":\"vinh@example.com\",\"password\":12345678}",
                "{\"email\":\"vinh@example.com\",\"password\":12345678.5}",
                "{\"email\":\"vinh@example.com\",\"password\":\"password123\",\"rememberMe\":1}",
                "{\"email\":\"vinh@example.com\",\"password\":\"password123\",\"rememberMe\":\"true\"}"}) {
            Exchange exchange = request("POST", "/api/auth/login", null, payload, null);
            exchange.send(new AuthApiServlet(accounts));
            exchange.assertStatus(400);
        }
        verifyNoInteractions(accounts);
    }

    @Test
    void registrationRejectsNumericContactAndPasswordFields() throws Exception {
        for (String payload : new String[]{
                "{\"fullName\":123,\"email\":\"vinh@example.com\",\"phone\":\"0901234567\",\"password\":\"password123\",\"confirmPassword\":\"password123\"}",
                "{\"fullName\":\"Vinh\",\"email\":\"vinh@example.com\",\"phone\":901234567,\"password\":\"password123\",\"confirmPassword\":\"password123\"}",
                "{\"fullName\":\"Vinh\",\"email\":\"vinh@example.com\",\"phone\":\"0901234567\",\"password\":12345678,\"confirmPassword\":12345678}"}) {
            Exchange exchange = request("POST", "/api/auth/register", null, payload, null);
            exchange.send(new AuthApiServlet(accounts));
            exchange.assertStatus(400);
        }
        verifyNoInteractions(accounts);
    }

    @Test
    void profileUpdateRejectsBooleanNameAndNumericPhone() throws Exception {
        for (String payload : new String[]{
                "{\"fullName\":true,\"email\":\"vinh@example.com\",\"phone\":\"0901234567\"}",
                "{\"fullName\":\"Vinh\",\"email\":\"vinh@example.com\",\"phone\":901234567}"}) {
            Exchange exchange = request("PUT", "/api/profile", null, payload, CUSTOMER);
            exchange.send(new ProfileApiServlet(accounts));
            exchange.assertStatus(400);
        }
        verifyNoInteractions(accounts);
    }

    @Test
    void addressWriteRejectsScalarCoercionBeforeChangingDefaultAddress() throws Exception {
        for (String payload : new String[]{
                "{\"addressLine\":123,\"isDefault\":true}",
                "{\"addressLine\":\"demo\",\"note\":false,\"isDefault\":true}",
                "{\"addressLine\":\"demo\",\"isDefault\":1}",
                "{\"addressLine\":\"demo\",\"isDefault\":0.5}",
                "{\"addressLine\":\"demo\",\"isDefault\":\"\"}",
                "{\"addressLine\":\"demo\",\"isDefault\":\"true\"}"}) {
            Exchange exchange = request("POST", "/api/addresses", null, payload, CUSTOMER);
            exchange.send(new AddressApiServlet(accounts));
            exchange.assertStatus(400);
        }
        verifyNoInteractions(accounts);
    }

    @Test
    void duplicateJsonFieldsAreRejectedWithoutCallingService() throws Exception {
        Exchange login = request("POST", "/api/auth/login", null,
                "{\"email\":\"first@example.com\",\"email\":\"second@example.com\",\"password\":\"password123\"}", null);
        login.send(new AuthApiServlet(accounts));
        login.assertStatus(400);
        Exchange address = request("POST", "/api/addresses", null,
                "{\"addressLine\":\"demo\",\"isDefault\":false,\"isDefault\":true}", CUSTOMER);
        address.send(new AddressApiServlet(accounts));
        address.assertStatus(400);
        verifyNoInteractions(accounts);
    }

    @Test
    void clientsCannotInjectAccountRolesOrAddressOwnershipThroughJson() throws Exception {
        Exchange registration = request("POST", "/api/auth/register", null,
                "{\"fullName\":\"Vinh\",\"email\":\"vinh@example.com\",\"phone\":\"0901234567\","
                        + "\"password\":\"password123\",\"confirmPassword\":\"password123\",\"accountType\":\"EMPLOYEE\",\"role\":\"ADMIN\"}", null);
        registration.send(new AuthApiServlet(accounts));
        registration.assertStatus(400);
        Exchange address = request("POST", "/api/addresses", null,
                "{\"addressLine\":\"demo\",\"isDefault\":true,\"customerId\":\"KH02\"}", CUSTOMER);
        address.send(new AddressApiServlet(accounts));
        address.assertStatus(400);
        verifyNoInteractions(accounts);
    }

    @Test
    void loginRememberMeExtendsSessionInactivityTimeout() throws Exception {
        when(accounts.login(any(LoginRequest.class))).thenReturn(CUSTOMER);
        Exchange exchange = request("POST", "/api/auth/login", null,
                "{\"email\":\"vinh@example.com\",\"password\":\"strongPassword123\",\"rememberMe\":true}", CUSTOMER);
        exchange.send(new AuthApiServlet(accounts));
        exchange.assertStatus(200);
        verify(exchange.session).setMaxInactiveInterval(7 * 24 * 60 * 60);
    }

    @Test
    void incorrectCredentialsReturn401WithoutCreatingSession() throws Exception {
        when(accounts.login(any(LoginRequest.class))).thenThrow(
                new AccountException("INVALID_CREDENTIALS", "Email hoặc mật khẩu không đúng.", 401));
        Exchange exchange = request("POST", "/api/auth/login", null,
                "{\"email\":\"vinh@example.com\",\"password\":\"wrongPassword123\"}", null);
        exchange.send(new AuthApiServlet(accounts));
        exchange.assertError(401, "INVALID_CREDENTIALS");
        verify(exchange.request, never()).getSession(true);
    }

    @Test
    void duplicateRegistrationReturnsConflictWithFieldErrors() throws Exception {
        when(accounts.register(any(RegisterRequest.class))).thenThrow(new AccountException(
                "ACCOUNT_CONFLICT", "Email đã được sử dụng.", 409, Map.of("email", "Email đã được sử dụng.")));
        Exchange exchange = request("POST", "/api/auth/register", null, "{}", null);
        exchange.send(new AuthApiServlet(accounts));
        exchange.assertError(409, "ACCOUNT_CONFLICT");
        assertEquals("Email đã được sử dụng.", exchange.json().path("error").path("fieldErrors").path("email").asText());
    }

    @Test
    void inactiveSessionIsInvalidatedBySessionEndpoint() throws Exception {
        when(accounts.getProfile("KH01")).thenThrow(new AccountException("ACCOUNT_INACTIVE", "Tài khoản bị khóa.", 401));
        Exchange exchange = request("GET", "/api/auth/session", null, "", CUSTOMER);
        exchange.send(new AuthApiServlet(accounts));
        exchange.assertError(401, "ACCOUNT_INACTIVE");
        verify(exchange.session).invalidate();
    }

    @Test
    void formEncodedRequestIsRejectedWith415() throws Exception {
        Exchange exchange = request("POST", "/api/auth/login", null, "email=test", null);
        when(exchange.request.getContentType()).thenReturn("application/x-www-form-urlencoded");
        exchange.send(new AuthApiServlet(accounts));
        exchange.assertError(415, "UNSUPPORTED_MEDIA_TYPE");
        verifyNoInteractions(accounts);
    }

    @Test
    void missingJsonContentTypeIsRejectedWith415() throws Exception {
        Exchange exchange = request("POST", "/api/auth/login", null,
                "{\"email\":\"vinh@example.com\",\"password\":\"password123\"}", null);
        when(exchange.request.getContentType()).thenReturn(null);
        exchange.send(new AuthApiServlet(accounts));
        exchange.assertError(415, "UNSUPPORTED_MEDIA_TYPE");
        verifyNoInteractions(accounts);
    }

    @Test
    void jsonContentTypeMayIncludeCharsetAndMixedCase() throws Exception {
        when(accounts.register(any(RegisterRequest.class))).thenReturn(CUSTOMER);
        Exchange exchange = request("POST", "/api/auth/register", null,
                "{\"fullName\":\"Vinh\",\"email\":\"vinh@example.com\",\"phone\":\"0901234567\","
                        + "\"password\":\"password123\",\"confirmPassword\":\"password123\"}", null);
        when(exchange.request.getContentType()).thenReturn("Application/JSON; charset=UTF-8");
        exchange.send(new AuthApiServlet(accounts));
        exchange.assertStatus(201);
        verify(accounts).register(any(RegisterRequest.class));
    }

    @Test
    void getLoginRegisterAndLogoutAndPostSessionAreMethodNotAllowed() throws Exception {
        for (String path : new String[]{"/api/auth/login", "/api/auth/register", "/api/auth/logout"}) {
            Exchange exchange = request("GET", path, null, "", null);
            exchange.send(new AuthApiServlet(accounts));
            exchange.assertError(405, "METHOD_NOT_ALLOWED");
            verify(exchange.response).setHeader("Allow", "POST");
        }
        Exchange sessionPost = request("POST", "/api/auth/session", null, "", CUSTOMER);
        sessionPost.send(new AuthApiServlet(accounts));
        sessionPost.assertStatus(405);
        verify(sessionPost.response).setHeader("Allow", "GET");
        verifyNoInteractions(accounts);
    }

    @Test
    void unsupportedAuthHttpMethodReturnsJson405() throws Exception {
        Exchange exchange = request("DELETE", "/api/auth/login", null, "", null);
        exchange.send(new AuthApiServlet(accounts));
        exchange.assertError(405, "METHOD_NOT_ALLOWED");
        verify(exchange.response).setHeader("Allow", "POST");
        verifyNoInteractions(accounts);
    }

    @Test
    void profileLoadsCurrentCustomerAndEmployeeFromService() throws Exception {
        for (ProfileResponse principal : new ProfileResponse[]{CUSTOMER, EMPLOYEE}) {
            when(accounts.getProfile(principal.id())).thenReturn(principal);
            Exchange exchange = request("GET", "/api/profile", null, "", principal);
            exchange.send(new ProfileApiServlet(accounts));
            exchange.assertStatus(200);
            assertEquals(principal.id(), exchange.json().path("data").path("id").asText());
            verify(accounts).getProfile(principal.id());
        }
    }

    @Test
    void profileUpdateRefreshesSessionPrincipalAndPreservesItsIdentity() throws Exception {
        ProfileResponse updated = new ProfileResponse("KH01", AccountType.CUSTOMER,
                "Vinh Updated", "new@example.com", "0912345678", null);
        when(accounts.updateProfile(eq("KH01"), any(ProfileUpdateRequest.class))).thenReturn(updated);
        Exchange exchange = request("PUT", "/api/profile", null, PROFILE_JSON, CUSTOMER);
        exchange.send(new ProfileApiServlet(accounts));
        exchange.assertStatus(200);
        verify(exchange.session).setAttribute("currentUser", updated);
        assertEquals("new@example.com", exchange.json().path("data").path("email").asText());
    }

    @Test
    void profileRequiresSessionAndRejectsEmployeeMutations() throws Exception {
        Exchange anonymous = request("GET", "/api/profile", null, "", null);
        anonymous.send(new ProfileApiServlet(accounts));
        anonymous.assertError(401, "UNAUTHORIZED");
        for (String method : new String[]{"PUT", "POST"}) {
            Exchange employee = request(method, "/api/profile", null, PROFILE_JSON, EMPLOYEE);
            employee.send(new ProfileApiServlet(accounts));
            employee.assertError(403, "FORBIDDEN");
        }
        verifyNoInteractions(accounts);
    }

    @Test
    void legacyStringSessionPrincipalIsRejected() throws Exception {
        Exchange exchange = request("GET", "/api/profile", null, "", CUSTOMER);
        when(exchange.session.getAttribute("currentUser")).thenReturn("KH01");
        exchange.send(new ProfileApiServlet(accounts));
        exchange.assertError(401, "UNAUTHORIZED");
        verifyNoInteractions(accounts);
    }

    @Test
    void addressesListAndDetailUseCustomerIdFromSession() throws Exception {
        when(accounts.listAddresses("KH01")).thenReturn(List.of(ADDRESS));
        when(accounts.getAddress("KH01", "DC01")).thenReturn(ADDRESS);
        Exchange list = request("GET", "/api/addresses", null, "", CUSTOMER);
        list.send(new AddressApiServlet(accounts));
        list.assertStatus(200);
        assertEquals("DC01", list.json().path("data").get(0).path("id").asText());

        Exchange detail = request("GET", "/api/addresses", "/DC01", "", CUSTOMER);
        detail.send(new AddressApiServlet(accounts));
        detail.assertStatus(200);
        assertTrue(detail.json().path("data").path("isDefault").asBoolean());
        verify(accounts).getAddress("KH01", "DC01");
    }

    @Test
    void addressCreateUpdateAndDeleteReturnContractStatuses() throws Exception {
        when(accounts.createAddress(eq("KH01"), any(AddressWriteRequest.class))).thenReturn(ADDRESS);
        when(accounts.updateAddress(eq("KH01"), eq("DC01"), any(AddressWriteRequest.class))).thenReturn(ADDRESS);
        Exchange create = request("POST", "/api/addresses", null, ADDRESS_JSON, CUSTOMER);
        create.send(new AddressApiServlet(accounts));
        create.assertStatus(201);
        assertEquals("DC01", create.json().path("data").path("id").asText());

        Exchange update = request("PUT", "/api/addresses", "/DC01", ADDRESS_JSON, CUSTOMER);
        update.send(new AddressApiServlet(accounts));
        update.assertStatus(200);

        Exchange delete = request("DELETE", "/api/addresses", "/DC01", "", CUSTOMER);
        delete.send(new AddressApiServlet(accounts));
        delete.assertStatus(204);
        assertEquals("", delete.body.toString());
        verify(accounts).deleteAddress("KH01", "DC01");
    }

    @Test
    void addressMethodsRequireCustomerSessionBeforeCallingService() throws Exception {
        for (String method : new String[]{"GET", "POST", "PUT", "DELETE"}) {
            String path = "PUT".equals(method) || "DELETE".equals(method) ? "/DC01" : null;
            Exchange anonymous = request(method, "/api/addresses", path, ADDRESS_JSON, null);
            anonymous.send(new AddressApiServlet(accounts));
            anonymous.assertError(401, "UNAUTHORIZED");
            Exchange employee = request(method, "/api/addresses", path, ADDRESS_JSON, EMPLOYEE);
            employee.send(new AddressApiServlet(accounts));
            employee.assertError(403, "FORBIDDEN");
        }
        verifyNoInteractions(accounts);
    }

    @Test
    void invalidAddressIdsAndMissingMutationIdsAreBadRequests() throws Exception {
        for (String path : new String[]{"/DC01/extra", "/bad!", "/ABCDEFGHIJK"}) {
            for (String method : new String[]{"GET", "PUT", "DELETE"}) {
                Exchange exchange = request(method, "/api/addresses", path, ADDRESS_JSON, CUSTOMER);
                exchange.send(new AddressApiServlet(accounts));
                exchange.assertStatus(400);
            }
        }
        for (String method : new String[]{"PUT", "DELETE"}) {
            Exchange exchange = request(method, "/api/addresses", null, ADDRESS_JSON, CUSTOMER);
            exchange.send(new AddressApiServlet(accounts));
            exchange.assertStatus(400);
        }
        Exchange invalidCreate = request("POST", "/api/addresses", "/DC01", ADDRESS_JSON, CUSTOMER);
        invalidCreate.send(new AddressApiServlet(accounts));
        invalidCreate.assertStatus(400);
        verifyNoInteractions(accounts);
    }

    @Test
    void addressOutsideCustomerScopeReturns404WithoutDisclosingOwnership() throws Exception {
        when(accounts.getAddress("KH01", "DC99")).thenThrow(new AccountException("NOT_FOUND", "Không tìm thấy địa chỉ.", 404));
        Exchange exchange = request("GET", "/api/addresses", "/DC99", "", CUSTOMER);
        exchange.send(new AddressApiServlet(accounts));
        exchange.assertError(404, "NOT_FOUND");
    }

    @Test
    void unexpectedFailureReturnsGeneric500WithoutInternalDetails() throws Exception {
        when(accounts.listAddresses("KH01")).thenThrow(new IllegalStateException("jdbc:mysql://private-host secret"));
        Exchange exchange = request("GET", "/api/addresses", null, "", CUSTOMER);
        exchange.send(new AddressApiServlet(accounts));
        exchange.assertError(500, "INTERNAL_SERVER_ERROR");
        assertFalse(exchange.body.toString().contains("private-host"));
        assertFalse(exchange.body.toString().contains("secret"));
    }

    private Exchange request(String method, String servletPath, String pathInfo, String payload,
                             ProfileResponse principal) throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpSession session = principal == null ? null : mock(HttpSession.class);
        StringWriter body = new StringWriter();
        when(request.getMethod()).thenReturn(method);
        when(request.getServletPath()).thenReturn(servletPath);
        when(request.getPathInfo()).thenReturn(pathInfo);
        when(request.getProtocol()).thenReturn("HTTP/1.1");
        when(request.getContentType()).thenReturn("application/json");
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader(payload)));
        when(request.getInputStream()).thenReturn(jsonStream(payload));
        when(request.getSession(false)).thenReturn(session);
        when(request.getSession()).thenReturn(session);
        when(request.getSession(true)).thenReturn(session);
        if (session != null) {
            when(session.getAttribute("currentUser")).thenReturn(principal);
        }
        when(response.getWriter()).thenReturn(new PrintWriter(body));
        return new Exchange(request, response, session, body);
    }

    static ServletInputStream jsonStream(String json) {
        ByteArrayInputStream bytes = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        return new ServletInputStream() {
            @Override public boolean isFinished() { return bytes.available() == 0; }
            @Override public boolean isReady() { return true; }
            @Override public void setReadListener(ReadListener listener) { }
            @Override public int read() { return bytes.read(); }
        };
    }

    private record Exchange(HttpServletRequest request, HttpServletResponse response,
                            HttpSession session, StringWriter body) {
        void send(HttpServlet servlet) throws Exception {
            servlet.service((ServletRequest) request, (ServletResponse) response);
        }

        void assertStatus(int status) {
            verify(response).setStatus(status);
        }

        JsonNode json() throws Exception {
            return JsonProvider.objectMapper().readTree(body.toString());
        }

        void assertError(int status, String code) throws Exception {
            assertStatus(status);
            assertFalse(json().path("success").asBoolean());
            assertEquals(code, json().path("error").path("code").asText());
        }
    }
}
