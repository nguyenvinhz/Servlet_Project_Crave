package vn.edu.crave.controller.account;

import vn.edu.crave.controller.BaseServlet;
import vn.edu.crave.controller.HealthServlet;
import vn.edu.crave.controller.account.*;
import vn.edu.crave.controller.menu.*;
import vn.edu.crave.controller.cart.*;
import vn.edu.crave.controller.order.*;
import vn.edu.crave.controller.payment.*;
import vn.edu.crave.controller.admin.*;


import vn.edu.crave.model.form.*;
import vn.edu.crave.model.viewmodel.*;
import vn.edu.crave.model.enums.AccountType;
import vn.edu.crave.model.enums.EmployeeRole;
import vn.edu.crave.exception.AccountException;
import vn.edu.crave.model.service.AccountService;
import vn.edu.crave.model.service.EmailVerificationService;
import vn.edu.crave.model.service.EmailVerificationService.PendingRegistration;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AccountBaseServletTest {
    private static final ProfileResponse CUSTOMER = new ProfileResponse("KH01", AccountType.CUSTOMER,
            "Nguyễn Văn An", "an@example.com", "0901234567", null);
    private static final AddressResponse ADDRESS = new AddressResponse("DC01", "12 Nguyễn Trãi",
            "Cổng trước", true, LocalDateTime.now(), LocalDateTime.now());
    private AccountService accounts;
    private EmailVerificationService verification;

    @BeforeEach
    void setUp() {
        accounts = mock(AccountService.class);
        verification = mock(EmailVerificationService.class);
    }

    @Test
    void loginReadsFormRotatesSessionAndReturnsToCheckout() throws Exception {
        ProfileResponse previousCustomer = new ProfileResponse("KH02", AccountType.CUSTOMER,
                "Previous customer", "previous@example.com", "0901234568", null);
        Exchange e = new Exchange("/auth/login/submit", previousCustomer);
        e.sessionValues.putAll(Map.of("selectedPromotionId", "PROMO-PREVIOUS",
                "selectedPromotionCode", "PREVIOUS10", "cartItemCount", 7));
        e.params.putAll(Map.of("email", "an@example.com", "password", "password123", "returnTo", "/crave/checkout"));
        when(accounts.login(new LoginRequest("an@example.com", "password123", false))).thenReturn(CUSTOMER);
        new AuthServlet(accounts, verification).doPost(e.request, e.response);
        verify(e.request).changeSessionId();
        assertSame(CUSTOMER, e.sessionValues.get("currentUser"));
        assertEquals("KH01", e.sessionValues.get("customerId"));
        assertFalse(e.sessionValues.containsKey("selectedPromotionId"));
        assertFalse(e.sessionValues.containsKey("selectedPromotionCode"));
        assertFalse(e.sessionValues.containsKey("cartItemCount"));
        verify(e.response).sendRedirect("/crave/checkout");
    }

    @Test
    void loginStartsSessionAndRejectsExternalReturnAddress() throws Exception {
        Exchange e = new Exchange("/auth/login/submit", null);
        e.params.put("returnTo", "//example.com/steal");
        ProfileResponse employee = new ProfileResponse("NV01", AccountType.EMPLOYEE,
                "Quản lý", "staff@example.com", "0901234568", EmployeeRole.MENU_MANAGER);
        when(accounts.login(any())).thenReturn(employee);
        new AuthServlet(accounts, verification).doPost(e.request, e.response);
        verify(e.response).sendRedirect("/crave/admin/menu");
        assertEquals("NV01", e.sessionValues.get("employeeId"));
        assertNull(e.sessionValues.get("customerId"));
    }

    @Test
    void invalidLoginRendersTheSameAlertWithoutRedirectLoop() throws Exception {
        Exchange e = new Exchange("/auth/login/submit", CUSTOMER);
        e.sessionValues.putAll(Map.of("selectedPromotionId", "PROMO-CURRENT",
                "selectedPromotionCode", "CURRENT10", "cartItemCount", 3));
        when(accounts.login(any())).thenThrow(new AccountException("INVALID_CREDENTIALS", "Email hoặc mật khẩu không đúng.", 401));
        new AuthServlet(accounts, verification).doPost(e.request, e.response);
        verify(e.response).setStatus(401);
        assertEquals("Email hoặc mật khẩu không đúng.", e.requestValues.get("error"));
        e.assertForward("auth/login");
        assertSame(CUSTOMER, e.sessionValues.get("currentUser"));
        assertEquals("PROMO-CURRENT", e.sessionValues.get("selectedPromotionId"));
        assertEquals("CURRENT10", e.sessionValues.get("selectedPromotionCode"));
        assertEquals(3, e.sessionValues.get("cartItemCount"));
        verify(e.request, never()).changeSessionId();
        verify(e.session, never()).removeAttribute("selectedPromotionId");
        verify(e.session, never()).removeAttribute("selectedPromotionCode");
        verify(e.session, never()).removeAttribute("cartItemCount");
        verify(e.response, never()).sendRedirect(anyString());
    }

    @Test
    void registrationRequiresSixDigitCodeAndKeepsPendingRegistration() throws Exception {
        Exchange e = new Exchange("/auth/register/submit", null);
        PendingRegistration pending = pendingRegistration();
        e.sessionStarted = true;
        e.sessionValues.put(RegistrationSession.ATTRIBUTE, pending);
        e.params.put("verificationCode", "123");
        new AuthServlet(accounts, verification).doPost(e.request, e.response);
        verifyNoInteractions(verification);
        verify(e.response).setStatus(400);
        assertEquals(true, e.requestValues.get("otpRequested"));
        e.assertForward("auth/register");
        assertSame(pending, e.sessionValues.get(RegistrationSession.ATTRIBUTE));
    }

    @Test
    void validRegistrationUsesSessionDraftAndIgnoresTamperedContactAndPassword() throws Exception {
        Exchange e = new Exchange("/auth/register/submit", null);
        PendingRegistration pending = pendingRegistration();
        e.sessionStarted = true;
        e.sessionValues.put(RegistrationSession.ATTRIBUTE, pending);
        e.params.putAll(Map.of("email", "other@example.com", "password", "different-password",
                "verificationCode", "012345"));
        new AuthServlet(accounts, verification).doPost(e.request, e.response);
        verify(verification).register(pending, "012345");
        verify(verification, never()).register(any(RegisterRequest.class), anyString());
        verify(e.response).sendRedirect("/crave/auth/login?registered=1");
        assertFalse(e.sessionValues.containsKey(RegistrationSession.ATTRIBUTE));
    }

    @Test
    void requestingRegistrationCodeStoresDraftAndRedirectsToAvoidPostingAgainOnRefresh() throws Exception {
        Exchange e = new Exchange("/auth/register/code", null);
        e.params.putAll(Map.of("fullName", "Nguyễn Văn An", "email", "an@example.com", "phone", "0901234567",
                "password", "password123", "confirmPassword", "password123"));
        RegisterRequest form = new RegisterRequest("Nguyễn Văn An", "an@example.com", "0901234567",
                "password123", "password123");
        PendingRegistration pending = pendingRegistration();
        when(verification.requestRegistrationCode(form)).thenReturn(pending);
        new EmailVerificationServlet(verification).doPost(e.request, e.response);
        verify(verification).requestRegistrationCode(form);
        assertSame(pending, e.sessionValues.get(RegistrationSession.ATTRIBUTE));
        assertFalse(e.requestValues.containsKey("password"));
        assertFalse(e.requestValues.containsKey("passwordHash"));
        verify(e.response).sendRedirect("/crave/auth/register?codeSent=1");
        verify(e.dispatcher, never()).forward(any(), any());
    }

    @Test
    void registrationCanCompleteWithOnlyOtpInTheSubmittedForm() throws Exception {
        Exchange e = new Exchange("/auth/register/submit", null);
        PendingRegistration pending = pendingRegistration();
        e.sessionStarted = true;
        e.sessionValues.put(RegistrationSession.ATTRIBUTE, pending);
        e.params.put("verificationCode", "012345");
        new AuthServlet(accounts, verification).doPost(e.request, e.response);
        verify(verification).register(pending, "012345");
        verify(e.response).sendRedirect("/crave/auth/login?registered=1");
        assertFalse(e.sessionValues.containsKey(RegistrationSession.ATTRIBUTE));
    }

    @Test
    void resendingRegistrationCodeUsesStoredEmailWithoutPasswordFields() throws Exception {
        Exchange e = new Exchange("/auth/register/code", null);
        PendingRegistration pending = pendingRegistration();
        PendingRegistration refreshed = pendingRegistration();
        e.sessionStarted = true;
        e.sessionValues.put(RegistrationSession.ATTRIBUTE, pending);
        e.params.putAll(Map.of("otpActive", "true", "email", "other@example.com"));
        when(verification.resendRegistrationCode(pending)).thenReturn(refreshed);
        new EmailVerificationServlet(verification).doPost(e.request, e.response);
        verify(verification).resendRegistrationCode(pending);
        verify(verification, never()).requestRegistrationCode(any(RegisterRequest.class));
        assertSame(refreshed, e.sessionValues.get(RegistrationSession.ATTRIBUTE));
        verify(e.response).sendRedirect("/crave/auth/register?codeSent=1");
    }

    @Test
    void rejectedRegistrationCodeKeepsDraftForRetry() throws Exception {
        Exchange e = new Exchange("/auth/register/submit", null);
        PendingRegistration pending = pendingRegistration();
        e.sessionStarted = true;
        e.sessionValues.put(RegistrationSession.ATTRIBUTE, pending);
        e.params.put("verificationCode", "111111");
        doThrow(new AccountException("VERIFICATION_INVALID", "Mã không đúng.", 400,
                Map.of("verificationCode", "Kiểm tra mã trong email.")))
                .when(verification).register(pending, "111111");
        new AuthServlet(accounts, verification).doPost(e.request, e.response);
        assertSame(pending, e.sessionValues.get(RegistrationSession.ATTRIBUTE));
        assertEquals(true, e.requestValues.get("otpRequested"));
        assertEquals("an@example.com", e.requestValues.get("registrationEmail"));
        assertEquals(Map.of("verificationCode", "Kiểm tra mã trong email."), e.requestValues.get("fieldErrors"));
        e.assertForward("auth/register");
    }

    @Test
    void resendCooldownKeepsExistingDraftAndOtpForm() throws Exception {
        Exchange e = new Exchange("/auth/register/code", null);
        PendingRegistration pending = pendingRegistration();
        e.sessionStarted = true;
        e.sessionValues.put(RegistrationSession.ATTRIBUTE, pending);
        e.params.put("otpActive", "true");
        when(verification.resendRegistrationCode(pending))
                .thenThrow(new AccountException("VERIFICATION_THROTTLED", "Vui lòng chờ để gửi lại mã.", 429));
        new EmailVerificationServlet(verification).doPost(e.request, e.response);
        assertSame(pending, e.sessionValues.get(RegistrationSession.ATTRIBUTE));
        assertEquals(true, e.requestValues.get("otpRequested"));
        verify(e.response).setStatus(429);
        e.assertForward("auth/register");
    }

    @Test
    void missingOrExpiredRegistrationReturnsToFirstStepWithoutCreatingSession() throws Exception {
        for (boolean expired : List.of(false, true)) {
            Exchange e = new Exchange("/auth/register/submit", null);
            if (expired) {
                PendingRegistration pending = pendingRegistration();
                when(pending.getExpiresAtMillis()).thenReturn(System.currentTimeMillis() - 1);
                e.sessionStarted = true;
                e.sessionValues.put(RegistrationSession.ATTRIBUTE, pending);
            }
            e.params.put("verificationCode", "012345");
            new AuthServlet(accounts, verification).doPost(e.request, e.response);
            verifyNoInteractions(verification);
            verify(e.response).setStatus(400);
            assertEquals(false, e.requestValues.get("otpRequested"));
            assertFalse(e.sessionValues.containsKey(RegistrationSession.ATTRIBUTE));
            assertEquals(expired, e.sessionStarted);
            e.assertForward("auth/register");
        }
    }

    @Test
    void refreshingRegistrationPageResumesOtpAndRestartClearsDraft() throws Exception {
        for (boolean restart : List.of(false, true)) {
            Exchange e = new Exchange("/auth/register", null);
            PendingRegistration pending = pendingRegistration();
            e.sessionStarted = true;
            e.sessionValues.put(RegistrationSession.ATTRIBUTE, pending);
            if (restart) e.params.put("restart", "1");
            new AccountPageServlet(accounts).doGet(e.request, e.response);
            assertEquals(!restart, e.requestValues.get("otpRequested"));
            assertEquals("Nguyễn Văn An", e.requestValues.get("registrationFullName"));
            assertEquals("an@example.com", e.requestValues.get("registrationEmail"));
            assertEquals("0901234567", e.requestValues.get("registrationPhone"));
            assertEquals(!restart, e.sessionValues.containsKey(RegistrationSession.ATTRIBUTE));
            e.assertForward("auth/register");
        }
    }

    @Test
    void invalidInitialRegistrationDoesNotCreatePendingSession() throws Exception {
        Exchange e = new Exchange("/auth/register/code", null);
        e.params.put("email", "an@example.com");
        when(verification.requestRegistrationCode(any(RegisterRequest.class)))
                .thenThrow(new AccountException("VALIDATION_ERROR", "Vui lòng kiểm tra thông tin đăng ký.", 400));
        new EmailVerificationServlet(verification).doPost(e.request, e.response);
        verify(e.response).setStatus(400);
        assertEquals(false, e.requestValues.get("otpRequested"));
        assertFalse(e.sessionStarted);
        e.assertForward("auth/register");
    }

    @Test
    void duplicateInitialCodeRequestDoesNotDiscardExistingDraftWhenThrottled() throws Exception {
        Exchange e = new Exchange("/auth/register/code", null);
        PendingRegistration pending = pendingRegistration();
        e.sessionStarted = true;
        e.sessionValues.put(RegistrationSession.ATTRIBUTE, pending);
        e.params.put("email", "an@example.com");
        when(verification.requestRegistrationCode(any(RegisterRequest.class)))
                .thenThrow(new AccountException("VERIFICATION_RATE_LIMIT", "Vui lòng chờ để gửi lại mã.", 429));
        new EmailVerificationServlet(verification).doPost(e.request, e.response);
        assertSame(pending, e.sessionValues.get(RegistrationSession.ATTRIBUTE));
        assertEquals(true, e.requestValues.get("otpRequested"));
        assertEquals("an@example.com", e.requestValues.get("registrationEmail"));
        e.assertForward("auth/register");
    }

    private static PendingRegistration pendingRegistration() {
        PendingRegistration pending = mock(PendingRegistration.class);
        when(pending.getFullName()).thenReturn("Nguyễn Văn An");
        when(pending.getEmail()).thenReturn("an@example.com");
        when(pending.getPhone()).thenReturn("0901234567");
        when(pending.getExpiresAtMillis()).thenReturn(System.currentTimeMillis() + 600_000);
        return pending;
    }

    @Test
    void rejectedCodeKeepsOtpFormVisibleAndShowsFieldErrors() throws Exception {
        Exchange e = new Exchange("/auth/password/reset/submit", null);
        e.params.putAll(Map.of("email", "an@example.com", "verificationCode", "111111", "password", "password123", "confirmPassword", "password123"));
        doThrow(new AccountException("VERIFICATION_INVALID", "Mã không đúng.", 400,
                Map.of("verificationCode", "Kiểm tra mã trong email.")))
                .when(verification).resetPassword(any(), any(), any(), any());
        new EmailVerificationServlet(verification).doPost(e.request, e.response);
        assertEquals(true, e.requestValues.get("otpRequested"));
        assertEquals(Map.of("verificationCode", "Kiểm tra mã trong email."), e.requestValues.get("fieldErrors"));
        e.assertForward("auth/forgot-password");
    }

    @Test
    void passwordResetRedirectsToLoginAfterSingleUseVerification() throws Exception {
        Exchange e = new Exchange("/auth/password/reset/submit", null);
        e.params.putAll(Map.of("email", "an@example.com", "verificationCode", "012345",
                "password", "password123", "confirmPassword", "password123"));
        new EmailVerificationServlet(verification).doPost(e.request, e.response);
        verify(verification).resetPassword("an@example.com", "012345", "password123", "password123");
        verify(e.response).sendRedirect("/crave/auth/login?passwordReset=1");
    }

    @Test
    void passwordChangeUsesSessionOwnerAndInvalidatesSession() throws Exception {
        Exchange e = new Exchange("/auth/password/change/submit", CUSTOMER);
        e.params.putAll(Map.of("userId", "KH_OTHER", "verificationCode", "012345",
                "password", "password123", "confirmPassword", "password123"));
        new EmailVerificationServlet(verification).doPost(e.request, e.response);
        verify(verification).changePassword("KH01", "012345", "password123", "password123");
        verify(e.session).invalidate();
        verify(e.response).sendRedirect("/crave/auth/login?passwordChanged=1");
    }

    @Test
    void anonymousPasswordChangeDoesNotCallVerification() throws Exception {
        Exchange e = new Exchange("/auth/password/change/code", null);
        new EmailVerificationServlet(verification).doPost(e.request, e.response);
        verifyNoInteractions(verification);
        verify(e.response).sendRedirect("/crave/auth/login?reason=session-expired");
    }

    @Test
    void profileSaveRefreshesSessionAndRedirectsAfterPost() throws Exception {
        Exchange e = new Exchange("/customer/profile/save", CUSTOMER);
        e.params.putAll(Map.of("fullName", "Nguyễn Văn An", "email", "an@example.com", "phone", "0901234567"));
        when(accounts.updateProfile("KH01", new ProfileUpdateRequest("Nguyễn Văn An", "an@example.com", "0901234567")))
                .thenReturn(CUSTOMER);
        new ProfileServlet(accounts).doPost(e.request, e.response);
        verify(e.response).sendRedirect("/crave/customer/profile?saved=1");
        assertSame(CUSTOMER, e.sessionValues.get("currentUser"));
    }

    @Test
    void addressActionsAlwaysUseSessionCustomer() throws Exception {
        for (String action : List.of("save", "delete", "default")) {
            Exchange e = new Exchange("/customer/addresses/" + action, CUSTOMER);
            e.params.putAll(Map.of("addressId", "DC01", "customerId", "KH_OTHER",
                    "addressLine", "12 Nguyễn Trãi", "note", "Cổng trước", "isDefault", "true"));
            when(accounts.getAddress("KH01", "DC01")).thenReturn(ADDRESS);
            new AddressServlet(accounts).doPost(e.request, e.response);
            verify(e.response).sendRedirect("/crave/customer/addresses?saved=1");
        }
        verify(accounts).deleteAddress("KH01", "DC01");
        verify(accounts, times(2)).updateAddress("KH01", "DC01", new AddressWriteRequest("12 Nguyễn Trãi", "Cổng trước", true));
        verify(accounts, never()).getAddress(eq("KH_OTHER"), any());
    }

    @Test
    void addressPageLoadsListAndEditValuesBeforeForwarding() throws Exception {
        Exchange e = new Exchange("/customer/addresses", CUSTOMER);
        e.params.put("edit", "DC01");
        when(accounts.listAddresses("KH01")).thenReturn(List.of(ADDRESS));
        when(accounts.getAddress("KH01", "DC01")).thenReturn(ADDRESS);
        new AccountPageServlet(accounts).doGet(e.request, e.response);
        assertEquals(List.of(ADDRESS), e.requestValues.get("addresses"));
        assertSame(ADDRESS, e.requestValues.get("address"));
        assertEquals("DC01", e.requestValues.get("editing"));
        e.assertForward("customer/addresses");
    }

    @Test
    void safeReturnToRejectsEncodedSeparatorsAndEmployeeCustomerRoutes() {
        assertNull(AuthServlet.safeReturnTo("/crave/%2f%2fevil", "/crave", CUSTOMER));
        assertNull(AuthServlet.safeReturnTo("/crave/admin", "/crave", CUSTOMER));
        assertEquals("/crave/orders/DH01", AuthServlet.safeReturnTo("/crave/orders/DH01", "/crave", CUSTOMER));
    }

    private static class Exchange {
        final HttpServletRequest request = mock(HttpServletRequest.class);
        final HttpServletResponse response = mock(HttpServletResponse.class);
        final HttpSession session = mock(HttpSession.class);
        final RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        final Map<String, String> params = new HashMap<>();
        final Map<String, Object> requestValues = new HashMap<>();
        final Map<String, Object> sessionValues = new HashMap<>();
        boolean sessionStarted;

        Exchange(String path, ProfileResponse user) {
            sessionStarted = user != null;
            if (user != null) sessionValues.put("currentUser", user);
            when(request.getServletPath()).thenReturn(path);
            when(request.getContextPath()).thenReturn("/crave");
            when(request.getParameter(anyString())).thenAnswer(call -> params.get(call.getArgument(0)));
            when(request.getAttribute(anyString())).thenAnswer(call -> requestValues.get(call.getArgument(0)));
            doAnswer(call -> { requestValues.put(call.getArgument(0), call.getArgument(1)); return null; })
                    .when(request).setAttribute(anyString(), any());
            when(request.getSession(false)).thenAnswer(call -> sessionStarted ? session : null);
            when(request.getSession(true)).thenAnswer(call -> { sessionStarted = true; return session; });
            when(session.getAttribute(anyString())).thenAnswer(call -> sessionValues.get(call.getArgument(0)));
            doAnswer(call -> { sessionValues.put(call.getArgument(0), call.getArgument(1)); return null; })
                    .when(session).setAttribute(anyString(), any());
            doAnswer(call -> { sessionValues.remove(call.getArgument(0)); return null; })
                    .when(session).removeAttribute(anyString());
            when(request.getRequestDispatcher(anyString())).thenReturn(dispatcher);
        }

        void assertForward(String page) throws Exception {
            verify(request).getRequestDispatcher("/WEB-INF/views/" + page + ".jsp");
            verify(dispatcher).forward(request, response);
        }
    }
}
