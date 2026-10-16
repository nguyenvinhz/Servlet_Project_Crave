package com.foodordering.filter;

import com.foodordering.bean.ProfileBean;
import com.foodordering.enums.AccountType;
import com.foodordering.enums.EmployeeRole;
import com.foodordering.exception.AccountException;
import com.foodordering.security.SessionAuth;
import com.foodordering.service.AccountService;
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
                ProfileBean fresh = accountService.getProfile(snapshot.user().id());
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
        return under(path, "/customer") || under(path, "/admin") || path.equals("/auth/change-password")
                || under(path, "/payments") || under(path, "/cart") || under(path, "/checkout") || under(path, "/orders");
    }
    private static boolean under(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private static void authorize(HttpServletRequest request, String path, ProfileBean user) {
        if (under(path, "/admin")) {
            String resource = path;
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
        } else if (!path.equals("/customer/profile")
                && !path.equals("/auth/change-password")
                && user.accountType() != AccountType.CUSTOMER) {
            throw new AccountException("FORBIDDEN", "Chức năng này chỉ dành cho khách hàng.", 403);
        }
    }

    /** SameSite cookies and same-origin checks also protect ordinary POST forms. */
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
        if (status == 401) {
            String target = request.getRequestURI();
            if (request.getQueryString() != null) {
                target += "?" + request.getQueryString();
            }
            response.sendRedirect(request.getContextPath() + "/auth/login?returnTo="
                    + URLEncoder.encode(target, StandardCharsets.UTF_8));
        } else {
            response.sendError(status, message);
        }
    }
}
