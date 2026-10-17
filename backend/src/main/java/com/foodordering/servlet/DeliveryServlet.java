package com.foodordering.servlet;

import com.foodordering.exception.ApiException;
import com.foodordering.exception.AppException;
import com.foodordering.gateway.AddressGeocoder;
import com.foodordering.security.SessionAuth;
import com.foodordering.service.CartService;
import com.foodordering.service.DeliveryFeeService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet(name = "DeliveryServlet", urlPatterns = {"/delivery/search", "/delivery/reverse", "/delivery/quote"})
public class DeliveryServlet extends BaseServlet {
    private final DeliveryFeeService fees;
    private final CartService carts;
    private final AddressGeocoder geocoder;
    public DeliveryServlet() { this(new DeliveryFeeService(), new CartService(), new AddressGeocoder()); }
    public DeliveryServlet(DeliveryFeeService fees, CartService carts, AddressGeocoder geocoder) {
        this.fees = fees; this.carts = carts; this.geocoder = geocoder;
    }
    @Override public void destroy() {
        try { geocoder.close(); }
        catch (IOException failure) { log("Không đóng được kết nối tìm địa chỉ.", failure); }
        finally { super.destroy(); }
    }
    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        try {
            String customerId = SessionAuth.requireCustomer(request).id();
            String path = request.getServletPath();
            if (path.endsWith("/search")) {
                var store = fees.policy().store();
                request.setAttribute("locations", geocoder.search(parameter(request, "address"), store.latitude(), store.longitude()));
            } else {
                Double lat = number(request, "latitude"), lon = number(request, "longitude");
                if (path.endsWith("/reverse")) request.setAttribute("location", geocoder.reverse(lat, lon));
                else request.setAttribute("quote", fees.quote(lat, lon, carts.getVerifiedSubtotal(customerId)));
            }
        } catch (RuntimeException failure) {
            int status;
            String message;
            if (failure instanceof AppException problem) {
                status = problem.getStatusCode();
                message = problem.getMessage();
            } else if (failure instanceof ApiException problem) {
                status = problem.getHttpStatus();
                message = problem.getMessage();
            } else if (failure instanceof IllegalArgumentException) {
                status = 400;
                message = "Thông tin gửi lên không hợp lệ.";
            } else {
                log("Không xử lý được vị trí giao hàng.", failure);
                status = 500;
                message = "Đã xảy ra lỗi hệ thống. Vui lòng thử lại sau.";
            }
            response.setStatus(status);
            request.setAttribute("deliveryError", message);
        }
        request.getRequestDispatcher("/WEB-INF/views/order/delivery.jsp").forward(request, response);
    }
    private static Double number(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        return value == null || value.isBlank() ? null : Double.valueOf(value);
    }
}
