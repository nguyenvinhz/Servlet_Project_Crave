package vn.edu.crave.filter;

import vn.edu.crave.model.viewmodel.ProfileResponse;
import vn.edu.crave.model.enums.AccountType;
import vn.edu.crave.model.enums.EmployeeRole;
import vn.edu.crave.exception.AccountException;
import vn.edu.crave.security.SessionAuth;
import vn.edu.crave.model.service.AccountService;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@WebFilter(filterName = "authenticationFilter", urlPatterns = "/*")
public class AuthenticationFilter implements Filter {
    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private final AccountService accountService;

    public AuthenticationFilter() {
        this(new AccountService());
    }

    public AuthenticationFilter(AccountService accountService) {
        this.accountService = accountService;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        // Authorize the container's decoded, normalized servlet mapping, including any path-info suffix.
        String path = request.getServletPath();
        if (path == null || path.isEmpty()) {
            path = request.getRequestURI().substring(request.getContextPath().length());
        } else if (request.getPathInfo() != null) {
            path += request.getPathInfo();
        }
        path = path.replaceAll(";[^/]*", "");
        boolean protectedRoute = isProtected(path, request.getMethod());
        SessionAuth.Snapshot snapshot = null;
        try {
            if (!READ_METHODS.contains(request.getMethod()) && !path.equals("/api/payos/webhook")) {
                validateOrigin(request);
            }
            if (protectedRoute) {
                response.setHeader("Cache-Control", "no-store");
                snapshot = SessionAuth.capture(request);
                ProfileResponse fresh = accountService.getProfile(snapshot.user().id());
                SessionAuth.refresh(request, snapshot, fresh);
                authorize(request, path, fresh);
            }
        } catch (AccountException exception) {
            if (exception.getStatusCode() == 401) {
                SessionAuth.invalidateIfCurrent(request, snapshot);
            }
            reject(request, response, path, exception.getStatusCode(), exception.getErrorCode(), exception.getMessage());
            return;
        } catch (RuntimeException exception) {
            reject(request, response, path, 503, "SERVICE_UNAVAILABLE", "Dịch vụ tài khoản tạm thời không khả dụng.");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean isProtected(String path, String method) {
        return under(path, "/customer") || under(path, "/admin") || under(path, "/api/admin")
                || under(path, "/map") || path.equals("/auth/change-password") || under(path, "/api/auth/password/change")
                || under(path, "/auth/password/change") || under(path, "/delivery") || under(path, "/payos")
                || path.equals("/api/profile") || under(path, "/api/addresses")
                || under(path, "/api/orders") || under(path, "/api/payments")
                || under(path, "/api/payos/orders")
                || under(path, "/api/delivery")
                || under(path, "/api/cart") || under(path, "/cart")
                || (under(path, "/api/promotions") && !READ_METHODS.contains(method))
                || (under(path, "/promotions") && !READ_METHODS.contains(method))
                || under(path, "/checkout") || under(path, "/orders");
    }

    private static boolean under(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private static void authorize(HttpServletRequest request, String path, ProfileResponse user) {
        if (under(path, "/admin") || under(path, "/api/admin")) {
            String resource = path.startsWith("/api/") ? path.substring(4) : path;
            if (resource.equals("/admin") || resource.equals("/admin/")) {
                SessionAuth.requireEmployee(request, EmployeeRole.values());
                return;
            }
            EmployeeRole expected = under(resource, "/admin/orders") || under(resource, "/admin/payments")
                    ? EmployeeRole.ORDER_STAFF
                    : under(resource, "/admin/menu") || under(resource, "/admin/foods")
                    || under(resource, "/admin/categories") || under(resource, "/admin/options")
                    ? EmployeeRole.MENU_MANAGER
                    : under(resource, "/admin/promotions") ? EmployeeRole.PROMOTION_MANAGER
                    : under(resource, "/admin/customers") || under(resource, "/admin/employees")
                    ? EmployeeRole.HR_MANAGER : EmployeeRole.ADMIN;
            SessionAuth.requireEmployee(request, expected);
        } else if (!path.equals("/api/profile") && !under(path, "/customer/profile")
                && !path.equals("/auth/change-password") && !under(path, "/api/auth/password/change")
                && !under(path, "/auth/password/change")
                && user.accountType() != AccountType.CUSTOMER) {
            throw new AccountException("FORBIDDEN", "Chức năng này chỉ dành cho khách hàng.", 403);
        }
    }

    /** Kiểm tra nguồn gửi form bên cạnh cookie SameSite. */
    private static void validateOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        boolean crossSite = "cross-site".equals(request.getHeader("Sec-Fetch-Site"));
        if (origin != null) {
            try {
                URI uri = URI.create(origin);
                int port = uri.getPort() == -1 ? ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80) : uri.getPort();
                crossSite |= !request.getScheme().equalsIgnoreCase(uri.getScheme())
                        || !request.getServerName().equalsIgnoreCase(uri.getHost()) || request.getServerPort() != port;
            } catch (IllegalArgumentException exception) {
                crossSite = true;
            }
        }
        if (crossSite) {
            throw new AccountException("FORBIDDEN", "Nguồn gửi yêu cầu không hợp lệ.", 403);
        }
    }

    private static void reject(HttpServletRequest request, HttpServletResponse response, String path,
                               int status, String code, String message) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        if (path.startsWith("/api/")) {
            response.sendError(status, message);
        } else if (status == 401) {
            String target = READ_METHODS.contains(request.getMethod()) ? request.getRequestURI()
                    : formPage(request, path);
            if (READ_METHODS.contains(request.getMethod()) && request.getQueryString() != null) {
                target += "?" + request.getQueryString();
            }
            response.sendRedirect(request.getContextPath() + "/auth/login?returnTo="
                    + URLEncoder.encode(target, StandardCharsets.UTF_8));
        } else {
            response.sendError(status, message);
        }
    }

    private static String formPage(HttpServletRequest request, String path) {
        String page;
        if (under(path, "/admin")) page = "/admin";
        else if (under(path, "/auth/password/change")) page = "/auth/change-password";
        else if (under(path, "/customer/profile")) page = "/customer/profile";
        else if (under(path, "/customer/addresses")) page = "/customer/addresses";
        else if (path.equals("/orders/create") || under(path, "/delivery")) page = "/checkout";
        else if (under(path, "/orders") || under(path, "/payos")) page = "/orders";
        else if (under(path, "/promotions")) page = "/checkout".equals(request.getParameter("returnTo")) ? "/checkout" : "/cart";
        else if (under(path, "/cart")) {
            String returnTo = request.getParameter("returnTo");
            page = returnTo != null && (returnTo.equals("/menu") || returnTo.equals("/cart")
                    || returnTo.startsWith("/menu?") || returnTo.startsWith("/menu/detail?"))
                    && !returnTo.contains("\\") && returnTo.chars().noneMatch(c -> c < 32 || c == 127)
                    ? returnTo : "/cart";
        } else page = "/menu";
        return request.getContextPath() + page;
    }
}
