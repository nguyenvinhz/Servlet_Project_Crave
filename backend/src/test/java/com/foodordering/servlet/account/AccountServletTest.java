package com.foodordering.servlet.account;

import com.foodordering.bean.AddressBean;
import com.foodordering.bean.AddressWriteForm;
import com.foodordering.bean.LoginForm;
import com.foodordering.bean.ProfileBean;
import com.foodordering.bean.ProfileUpdateForm;
import com.foodordering.bean.RegisterForm;
import com.foodordering.enums.AccountType;
import com.foodordering.enums.EmployeeRole;
import com.foodordering.exception.AccountException;
import com.foodordering.security.SessionAuth;
import com.foodordering.service.AccountService;
import com.foodordering.service.EmailVerificationService;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AccountServletTest {
    private static final ProfileBean CUSTOMER = new ProfileBean("KH01", AccountType.CUSTOMER,
            "Nguyễn Quang Vinh", "vinh@example.com", "0901234567", null);
    private static final ProfileBean EMPLOYEE = new ProfileBean("NV01", AccountType.EMPLOYEE,
            "Admin", "admin@example.com", "0907654321", EmployeeRole.ADMIN);
    private static final AddressBean ADDRESS = new AddressBean("DC01", "12 Nguyễn Trãi", "Cổng trước", true,
            LocalDateTime.of(2026, 10, 8, 12, 0), LocalDateTime.of(2026, 10, 8, 12, 0));
    private AccountService accounts;
    private EmailVerificationService verification;

    @BeforeEach void setUp() {
        accounts = mock(AccountService.class);
        verification = mock(EmailVerificationService.class);
    }

    @Test void accountRoutesUsePagesAndFormPosts() {
        assertEquals(Set.of("/auth/login", "/auth/register", "/auth/logout"), routes(AuthServlet.class));
        assertEquals(Set.of("/auth/forgot-password", "/auth/change-password"), routes(EmailVerificationServlet.class));
        assertEquals(Set.of("/customer/profile"), routes(ProfileServlet.class));
        assertEquals(Set.of("/customer/addresses"), routes(AddressServlet.class));
    }

    @Test void loginReadsFormAndRotatesSessionBeforeRedirect() throws Exception {
        when(accounts.login(any(LoginForm.class))).thenReturn(CUSTOMER);
        Exchange exchange = exchange("POST", "/auth/login", EMPLOYEE,
                Map.of("email", CUSTOMER.email(), "password", "password123", "rememberMe", "true"));
        exchange.send(new AuthServlet(accounts, verification));
        verify(accounts).login(new LoginForm(CUSTOMER.email(), "password123", true));
        verify(exchange.request).changeSessionId();
        verify(exchange.session).setMaxInactiveInterval(7 * 24 * 60 * 60);
        verify(exchange.session).removeAttribute("employeeId");
        verify(exchange.session).removeAttribute("role");
        assertEquals(CUSTOMER, exchange.sessionAttributes.get("currentUser"));
        verify(exchange.response).sendRedirect("/crave/menu");
        verify(exchange.request, never()).getInputStream();
        verify(exchange.response, never()).getWriter();
    }

    @Test void newEmployeeLoginUsesRoleDestination() throws Exception {
        when(accounts.login(any(LoginForm.class))).thenReturn(EMPLOYEE);
        Exchange exchange = exchange("POST", "/auth/login", null, Map.of());
        exchange.send(new AuthServlet(accounts, verification));
        assertEquals("NV01", exchange.sessionAttributes.get("employeeId"));
        assertEquals("ADMIN", exchange.sessionAttributes.get("role"));
        verify(exchange.response).sendRedirect("/crave/admin");
    }

    @Test void loginOnlyAcceptsSafeReturnPathsCompatibleWithAccount() throws Exception {
        when(accounts.login(any(LoginForm.class))).thenReturn(CUSTOMER);
        for (String returnTo : List.of("https://evil.test", "//evil.test", "/crave/auth/logout", "/crave/admin",
                "/outside/menu", "/crave/%2f%2fevil.test", "/crave/../admin", "/crave/\\evil.test")) {
            Exchange exchange = exchange("POST", "/auth/login", null, Map.of("returnTo", returnTo));
            exchange.send(new AuthServlet(accounts, verification));
            verify(exchange.response).sendRedirect("/crave/menu");
        }
        Exchange safe = exchange("POST", "/auth/login", null, Map.of("returnTo", "/crave/checkout?step=payment"));
        safe.send(new AuthServlet(accounts, verification));
        verify(safe.response).sendRedirect("/crave/checkout?step=payment");
    }

    @Test void invalidLoginForwardsErrorWithoutPuttingPasswordInSession() throws Exception {
        when(accounts.login(any())).thenThrow(new AccountException("INVALID_CREDENTIALS", "Email hoặc mật khẩu không đúng.", 401));
        Exchange exchange = exchange("POST", "/auth/login", null, Map.of("password", "password123"));
        exchange.send(new AuthServlet(accounts, verification));
        assertEquals("Email hoặc mật khẩu không đúng.", exchange.attributes.get("error"));
        exchange.forwarded("auth/login");
        assertFalse(exchange.sessionAttributes.containsValue("password123"));
    }

    @Test void registrationCodeAndVerifiedRegistrationUseFormParameters() throws Exception {
        Exchange code = exchange("POST", "/auth/register", null, Map.of("action", "sendCode", "email", CUSTOMER.email()));
        code.send(new AuthServlet(accounts, verification));
        verify(verification).requestRegistrationCode(CUSTOMER.email());
        code.forwarded("auth/register");
        assertNotNull(code.attributes.get("message"));

        Exchange registration = exchange("POST", "/auth/register", null, Map.of("action", "register", "fullName", CUSTOMER.fullName(),
                "email", CUSTOMER.email(), "phone", CUSTOMER.phone(), "password", "password123",
                "confirmPassword", "password123", "verificationCode", "012345"));
        registration.send(new AuthServlet(accounts, verification));
        verify(verification).register(new RegisterForm(CUSTOMER.fullName(), CUSTOMER.email(), CUSTOMER.phone(),
                "password123", "password123"), "012345");
        verify(registration.response).sendRedirect("/crave/auth/login");
        assertNull(registration.sessionAttributes.get("currentUser"));
    }

    @Test void missingRegistrationCodeKeepsFormAndDoesNotRegister() throws Exception {
        Exchange exchange = exchange("POST", "/auth/register", null, Map.of("verificationCode", "123"));
        exchange.send(new AuthServlet(accounts, verification));
        exchange.forwarded("auth/register");
        assertNotNull(exchange.attributes.get("fieldErrors"));
        verifyNoInteractions(verification);
    }

    @Test void registrationAndPasswordResetKeepReturnPathForTheLoginForm() throws Exception {
        String returnTo = "/crave/checkout?step=payment";
        Exchange registration = exchange("POST", "/auth/register", null,
                Map.of("action", "register", "verificationCode", "012345", "returnTo", returnTo));
        registration.send(new AuthServlet(accounts, verification));
        verify(registration.response).sendRedirect("/crave/auth/login?returnTo=%2Fcrave%2Fcheckout%3Fstep%3Dpayment");
        Exchange reset = exchange("POST", "/auth/forgot-password", null,
                Map.of("action", "reset", "verificationCode", "012345", "returnTo", returnTo));
        reset.send(new EmailVerificationServlet(verification));
        verify(reset.response).sendRedirect("/crave/auth/login?returnTo=%2Fcrave%2Fcheckout%3Fstep%3Dpayment");
    }

    @Test void logoutInvalidatesSessionAndRedirects() throws Exception {
        Exchange exchange = exchange("POST", "/auth/logout", CUSTOMER, Map.of());
        exchange.send(new AuthServlet(accounts, verification));
        verify(exchange.session).invalidate();
        verify(exchange.response).sendRedirect("/crave/auth/login");
        verifyNoInteractions(accounts, verification);
    }

    @Test void profileGetRefreshesSessionAndForwardsPopulatedPage() throws Exception {
        when(accounts.getProfile("KH01")).thenReturn(CUSTOMER);
        Exchange exchange = exchange("GET", "/customer/profile", CUSTOMER, Map.of());
        exchange.send(new ProfileServlet(accounts));
        assertEquals(CUSTOMER, exchange.attributes.get("profile"));
        assertEquals("Hồ sơ", exchange.attributes.get("pageTitle"));
        exchange.forwarded("customer/profile");
    }

    @Test void profilePostUpdatesCustomerAndRedirects() throws Exception {
        when(accounts.updateProfile(eq("KH01"), any())).thenReturn(CUSTOMER);
        Exchange exchange = exchange("POST", "/customer/profile", CUSTOMER,
                Map.of("fullName", CUSTOMER.fullName(), "email", CUSTOMER.email(), "phone", CUSTOMER.phone()));
        exchange.send(new ProfileServlet(accounts));
        verify(accounts).updateProfile("KH01", new ProfileUpdateForm(CUSTOMER.fullName(), CUSTOMER.email(), CUSTOMER.phone()));
        verify(exchange.response).sendRedirect("/crave/customer/profile");
        assertEquals("Đã cập nhật hồ sơ.", exchange.sessionAttributes.get("accountMessage"));
    }

    @Test void employeeCannotUpdateCustomerProfile() throws Exception {
        Exchange exchange = exchange("POST", "/customer/profile", EMPLOYEE, Map.of());
        exchange.send(new ProfileServlet(accounts));
        verify(exchange.response).sendError(eq(403), anyString());
        verifyNoInteractions(accounts);
    }

    @Test void profileRefreshCannotReplaceOrInvalidateConcurrentLogin() throws Exception {
        Exchange exchange = exchange("GET", "/customer/profile", CUSTOMER, Map.of());
        when(accounts.getProfile("KH01")).thenAnswer(call -> {
            SessionAuth.store(exchange.session, EMPLOYEE);
            return CUSTOMER;
        });
        exchange.send(new ProfileServlet(accounts));
        assertEquals(EMPLOYEE, exchange.sessionAttributes.get("currentUser"));
        verify(exchange.session, never()).invalidate();
        verify(exchange.response).sendRedirect("/crave/auth/login?reason=session-expired");
    }

    @Test void addressesPageLoadsOwnedListAndSelectedEditAddress() throws Exception {
        when(accounts.listAddresses("KH01")).thenReturn(List.of(ADDRESS));
        when(accounts.getAddress("KH01", "DC01")).thenReturn(ADDRESS);
        Exchange exchange = exchange("GET", "/customer/addresses", CUSTOMER, Map.of("edit", "DC01"));
        exchange.send(new AddressServlet(accounts));
        assertEquals("DC01", ((Map<?, ?>) exchange.attributes.get("editing")).get("id"));
        assertEquals(1, ((List<?>) exchange.attributes.get("addresses")).size());
        exchange.forwarded("customer/addresses");
    }

    @Test void addressSaveCreateAndUpdateUseOwnerFromSession() throws Exception {
        Map<String, String> fields = new HashMap<>(Map.of("action", "save", "addressLine", ADDRESS.addressLine(),
                "note", ADDRESS.note(), "isDefault", "true", "customerId", "KH_OTHER"));
        Exchange create = exchange("POST", "/customer/addresses", CUSTOMER, fields);
        create.send(new AddressServlet(accounts));
        verify(accounts).createAddress("KH01", new AddressWriteForm(ADDRESS.addressLine(), ADDRESS.note(), true));
        verify(create.response).sendRedirect("/crave/customer/addresses");
        fields.put("id", "DC01");
        Exchange update = exchange("POST", "/customer/addresses", CUSTOMER, fields);
        update.send(new AddressServlet(accounts));
        verify(accounts).updateAddress("KH01", "DC01", new AddressWriteForm(ADDRESS.addressLine(), ADDRESS.note(), true));
    }

    @Test void addressDefaultAndDeleteArePostActions() throws Exception {
        when(accounts.getAddress("KH01", "DC01")).thenReturn(ADDRESS);
        Exchange selected = exchange("POST", "/customer/addresses", CUSTOMER, Map.of("action", "default", "id", "DC01"));
        selected.send(new AddressServlet(accounts));
        verify(accounts).updateAddress("KH01", "DC01", new AddressWriteForm(ADDRESS.addressLine(), ADDRESS.note(), true));
        Exchange deleted = exchange("POST", "/customer/addresses", CUSTOMER, Map.of("action", "delete", "id", "DC01"));
        deleted.send(new AddressServlet(accounts));
        verify(accounts).deleteAddress("KH01", "DC01");
        verify(deleted.response).sendRedirect("/crave/customer/addresses");
    }

    @Test void addressActionsRejectAnonymousAndEmployeeSessions() throws Exception {
        Exchange anonymous = exchange("POST", "/customer/addresses", null, Map.of("action", "delete", "id", "DC01"));
        anonymous.send(new AddressServlet(accounts));
        verify(anonymous.response).sendRedirect("/crave/auth/login?reason=session-expired");
        Exchange employee = exchange("POST", "/customer/addresses", EMPLOYEE, Map.of("action", "delete", "id", "DC01"));
        employee.send(new AddressServlet(accounts));
        verify(employee.response).sendError(eq(403), anyString());
        verifyNoInteractions(accounts);
    }

    @Test void resetCodeAndPasswordSubmitKeepEmailVerification() throws Exception {
        Exchange code = exchange("POST", "/auth/forgot-password", null, Map.of("action", "sendCode", "email", CUSTOMER.email()));
        code.send(new EmailVerificationServlet(verification));
        verify(verification).requestPasswordResetCode(CUSTOMER.email());
        code.forwarded("auth/forgot-password");
        Exchange reset = exchange("POST", "/auth/forgot-password", null, Map.of("action", "reset", "email", CUSTOMER.email(),
                "verificationCode", "012345", "password", "password123", "confirmPassword", "password123"));
        reset.send(new EmailVerificationServlet(verification));
        verify(verification).resetPassword(CUSTOMER.email(), "012345", "password123", "password123");
        verify(reset.response).sendRedirect("/crave/auth/login");
    }

    @Test void passwordChangeUsesSessionAccountAndInvalidatesAfterSuccess() throws Exception {
        Exchange code = exchange("POST", "/auth/change-password", EMPLOYEE,
                Map.of("action", "sendCode", "currentPassword", "oldPassword123", "userId", "NV_OTHER"));
        code.send(new EmailVerificationServlet(verification));
        verify(verification).requestPasswordChangeCode("NV01", "oldPassword123");
        Exchange change = exchange("POST", "/auth/change-password", EMPLOYEE, Map.of("action", "change", "verificationCode", "012345",
                "password", "newPassword123", "confirmPassword", "newPassword123"));
        change.send(new EmailVerificationServlet(verification));
        verify(verification).changePassword("NV01", "012345", "newPassword123", "newPassword123");
        verify(change.session).invalidate();
        verify(change.response).sendRedirect("/crave/auth/login");
    }

    @Test void accountPagesForwardWithoutReadingRequestBody() throws Exception {
        for (String path : List.of("/auth/login", "/auth/register")) {
            Exchange exchange = exchange("GET", path, null, Map.of());
            exchange.send(new AuthServlet(accounts, verification));
            exchange.forwarded(path.substring(1));
            verify(exchange.request, never()).getInputStream();
        }
    }

    private static Set<String> routes(Class<?> servlet) {
        return Arrays.stream(servlet.getAnnotation(WebServlet.class).urlPatterns()).collect(toSet());
    }

    private static Exchange exchange(String method, String path, ProfileBean user, Map<String, String> fields) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpSession session = mock(HttpSession.class);
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        Map<String, Object> attributes = new HashMap<>();
        Map<String, Object> sessionAttributes = new HashMap<>();
        if (user != null) sessionAttributes.put("currentUser", user);
        AtomicReference<HttpSession> current = new AtomicReference<>(user == null ? null : session);
        when(request.getMethod()).thenReturn(method);
        when(request.getServletPath()).thenReturn(path);
        when(request.getProtocol()).thenReturn("HTTP/1.1");
        when(request.getContextPath()).thenReturn("/crave");
        when(request.getParameter(anyString())).thenAnswer(call -> fields.get(call.getArgument(0)));
        when(request.getAttribute(anyString())).thenAnswer(call -> attributes.get(call.getArgument(0)));
        doAnswer(call -> { attributes.put(call.getArgument(0), call.getArgument(1)); return null; })
                .when(request).setAttribute(anyString(), any());
        when(request.getRequestDispatcher(anyString())).thenReturn(dispatcher);
        when(request.getSession(false)).thenAnswer(call -> current.get());
        when(request.getSession(true)).thenAnswer(call -> { current.set(session); return session; });
        when(request.getSession()).thenAnswer(call -> { current.set(session); return session; });
        when(session.getAttribute(anyString())).thenAnswer(call -> sessionAttributes.get(call.getArgument(0)));
        doAnswer(call -> { sessionAttributes.put(call.getArgument(0), call.getArgument(1)); return null; })
                .when(session).setAttribute(anyString(), any());
        doAnswer(call -> { sessionAttributes.remove(call.getArgument(0)); return null; })
                .when(session).removeAttribute(anyString());
        doAnswer(call -> { current.set(null); sessionAttributes.clear(); return null; }).when(session).invalidate();
        return new Exchange(request, response, session, dispatcher, attributes, sessionAttributes);
    }

    private record Exchange(HttpServletRequest request, HttpServletResponse response, HttpSession session,
                            RequestDispatcher dispatcher, Map<String, Object> attributes, Map<String, Object> sessionAttributes) {
        void send(HttpServlet servlet) throws Exception {
            ServletConfig config = mock(ServletConfig.class);
            when(config.getServletContext()).thenReturn(mock(ServletContext.class));
            servlet.init(config);
            servlet.service((ServletRequest) request, (ServletResponse) response);
        }
        void forwarded(String view) throws Exception {
            verify(request).getRequestDispatcher("/WEB-INF/views/" + view + ".jsp");
            verify(dispatcher).forward(request, response);
        }
    }
}
