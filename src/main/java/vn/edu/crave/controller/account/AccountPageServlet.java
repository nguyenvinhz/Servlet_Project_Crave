package vn.edu.crave.controller.account;

import vn.edu.crave.model.viewmodel.ProfileResponse;
import vn.edu.crave.security.SessionAuth;
import vn.edu.crave.model.service.AccountService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet(name = "accountPageServlet", urlPatterns = {
        "/auth/login", "/auth/register", "/auth/forgot-password", "/auth/change-password",
        "/customer/profile", "/customer/addresses"
})
public class AccountPageServlet extends AccountBaseServlet {
    private final AccountService accounts;
    public AccountPageServlet() { this(new AccountService()); }
    public AccountPageServlet(AccountService accounts) { this.accounts = accounts; }

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String page = switch (request.getServletPath()) {
            case "/auth/login" -> "auth/login";
            case "/auth/register" -> "auth/register";
            case "/auth/forgot-password" -> "auth/forgot-password";
            case "/auth/change-password" -> "auth/change-password";
            case "/customer/profile" -> "customer/profile";
            case "/customer/addresses" -> "customer/addresses";
            default -> null;
        };
        if (page == null) { response.sendError(404); return; }
        try {
            if ("auth/register".equals(page) && "1".equals(request.getParameter("restart"))) {
                RegistrationSession.restart(request);
            }
            if ("auth/register".equals(page) && "1".equals(request.getParameter("codeSent"))
                    && RegistrationSession.current(request) != null) {
                request.setAttribute("message", "Mã xác minh đã được gửi đến email của bạn. Nhập mã để hoàn tất đăng ký.");
            }
            if ("customer/profile".equals(page) || "auth/change-password".equals(page)) {
                SessionAuth.Snapshot before = SessionAuth.capture(request);
                ProfileResponse profile = accounts.getProfile(before.user().id());
                SessionAuth.refresh(request, before, profile);
                request.setAttribute("profile", profile);
            } else if ("customer/addresses".equals(page)) {
                String customerId = SessionAuth.requireCustomer(request).id();
                request.setAttribute("addresses", accounts.listAddresses(customerId));
                String edit = request.getParameter("edit");
                if (edit != null && !edit.isBlank()) {
                    request.setAttribute("address", accounts.getAddress(customerId, edit));
                    request.setAttribute("editing", edit);
                }
            }
            if ("1".equals(request.getParameter("saved"))) request.setAttribute("message", "Đã lưu thay đổi.");
            if ("1".equals(request.getParameter("registered"))) request.setAttribute("message", "Đăng ký thành công. Vui lòng đăng nhập để tiếp tục.");
            if ("1".equals(request.getParameter("passwordReset")) || "1".equals(request.getParameter("passwordChanged")))
                request.setAttribute("message", "Mật khẩu đã được cập nhật. Vui lòng đăng nhập bằng mật khẩu mới.");
            if ("1".equals(request.getParameter("loggedOut"))) request.setAttribute("message", "Bạn đã đăng xuất.");
            if ("session-expired".equals(request.getParameter("reason"))) request.setAttribute("error", "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
            show(request, response, page);
        } catch (Exception exception) {
            formError(request, response, page, exception);
        }
    }
}
