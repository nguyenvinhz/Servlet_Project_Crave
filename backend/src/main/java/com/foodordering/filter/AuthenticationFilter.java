package com.foodordering.filter;

import com.foodordering.dto.ApiError;
import com.foodordering.dto.ApiResponse;
import com.foodordering.dto.ProfileResponse;
import com.foodordering.enums.AccountType;
import com.foodordering.enums.EmployeeRole;
import com.foodordering.exception.AccountException;
import com.foodordering.security.SessionAuth;
import com.foodordering.service.AccountService;
import com.foodordering.utils.JsonProvider;
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
import java.util.Map;
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
            if (!READ_METHODS.contains(request.getMethod())) {
                validateOrigin(request);
            }
            if (protectedRoute) {
                response.setHeader("Cache-Control", "no-store");
                snapshot = SessionAuth.capture(request);
                ProfileResponse fresh = accountService.getProfile(snapshot.user().id());
                SessionAuth.refresh(request, snapshot, fresh);
                authorize(path, fresh);
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
                || path.equals("/api/profile") || under(path, "/api/addresses")
                || under(path, "/api/orders") || under(path, "/api/payments")
                || under(path, "/api/cart") || under(path, "/cart")
                || (under(path, "/api/promotions") && !READ_METHODS.contains(method))
                || under(path, "/checkout") || under(path, "/orders");
    }

    private static boolean under(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private static void authorize(String path, ProfileResponse user) {
        if (under(path, "/admin") || under(path, "/api/admin")) {
            String resource = path.startsWith("/api/") ? path.substring(4) : path;
            EmployeeRole expected = under(resource, "/admin/orders") || under(resource, "/admin/payments")
                    ? EmployeeRole.ORDER_STAFF
                    : under(resource, "/admin/menu") || under(resource, "/admin/foods")
                    || under(resource, "/admin/categories") || under(resource, "/admin/options")
                    ? EmployeeRole.MENU_MANAGER
                    : under(resource, "/admin/promotions") ? EmployeeRole.PROMOTION_MANAGER : EmployeeRole.ADMIN;
            if (user.accountType() != AccountType.EMPLOYEE
                    || (user.role() != EmployeeRole.ADMIN && user.role() != expected)) {
                throw new AccountException("FORBIDDEN", "Bạn không có quyền truy cập chức năng này.", 403);
            }
        } else if (!path.equals("/api/profile") && !path.equals("/customer/profile")
                && user.accountType() != AccountType.CUSTOMER) {
            throw new AccountException("FORBIDDEN", "Chức năng này chỉ dành cho khách hàng.", 403);
        }
    }

    /** JSON requests and SameSite cookies are supplemented by same-origin checks for mutations. */
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
            response.setStatus(status);
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/json");
            JsonProvider.objectMapper().writeValue(response.getWriter(),
                    ApiResponse.failure(new ApiError(code, message, Map.of())));
        } else if (status == 401) {
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
