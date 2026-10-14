package com.foodordering.api;

import com.foodordering.dto.ApiResponse;
import com.foodordering.security.SessionAuth;
import com.foodordering.service.DeliveryFeeService;
import com.foodordering.service.CartService;
import com.foodordering.gateway.AddressGeocoder;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;

@WebServlet(name = "DeliveryApiServlet", urlPatterns = {"/api/delivery", "/api/delivery/*"})
public class DeliveryApiServlet extends BaseApiServlet {
    public record QuoteRequest(Double latitude, Double longitude) {}
    public record ResolveRequest(String address) {}
    public record CustomerDeliveryInfo(DeliveryFeeService.Store store, boolean configured) {}
    public record CustomerQuote(BigDecimal deliveryFee, boolean deliverable, String message) {}
    private final DeliveryFeeService fees;
    private final CartService carts;
    private final AddressGeocoder geocoder;
    public DeliveryApiServlet() { this(new DeliveryFeeService()); }
    public DeliveryApiServlet(DeliveryFeeService fees) { this(fees, new CartService(), new AddressGeocoder()); }
    public DeliveryApiServlet(DeliveryFeeService fees, CartService carts, AddressGeocoder geocoder) {
        this.fees = fees; this.carts = carts; this.geocoder = geocoder;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            SessionAuth.requireCustomer(req);
            if (!"/policy".equals(req.getPathInfo())) { badRequest(resp, "Đường dẫn giao hàng không hợp lệ."); return; }
            var policy = fees.policy();
            writeJson(resp, 200, ApiResponse.success(new CustomerDeliveryInfo(policy.store(), policy.configured())));
        } catch (Exception e) { handleError(resp, e); }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            var customer = SessionAuth.requireCustomer(req);
            if ("/search".equals(req.getPathInfo())) {
                ResolveRequest search = readJson(req, ResolveRequest.class);
                var store = fees.policy().store();
                writeJson(resp, 200, ApiResponse.success(geocoder.search(search == null ? null : search.address(), store.latitude(), store.longitude())));
                return;
            }
            if ("/reverse".equals(req.getPathInfo())) {
                QuoteRequest point = readJson(req, QuoteRequest.class);
                writeJson(resp, 200, ApiResponse.success(geocoder.reverse(point == null ? null : point.latitude(), point == null ? null : point.longitude())));
                return;
            }
            if ("/resolve".equals(req.getPathInfo())) {
                ResolveRequest resolve = readJson(req, ResolveRequest.class);
                writeJson(resp, 200, ApiResponse.success(geocoder.resolve(resolve == null ? null : resolve.address())));
                return;
            }
            if (!"/quote".equals(req.getPathInfo())) { badRequest(resp, "Đường dẫn giao hàng không hợp lệ."); return; }
            QuoteRequest request = readJson(req, QuoteRequest.class);
            if (request == null) { badRequest(resp, "Vui lòng chọn vị trí nhận hàng."); return; }
            var quote = fees.quote(request.latitude(), request.longitude(), carts.getVerifiedSubtotal(customer.id()));
            writeJson(resp, 200, ApiResponse.success(new CustomerQuote(quote.deliveryFee(), quote.deliverable(), quote.message())));
        } catch (Exception e) { handleError(resp, e); }
    }
}
