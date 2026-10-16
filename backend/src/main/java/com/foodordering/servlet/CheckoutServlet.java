package com.foodordering.servlet;

import com.foodordering.bean.OrderForm;
import com.foodordering.enums.FulfillmentType;
import com.foodordering.enums.PaymentMethod;
import com.foodordering.integration.AddressGeocoder;
import com.foodordering.security.SessionAuth;
import com.foodordering.service.*;
import com.foodordering.dao.JpaOrderDAO;
import com.foodordering.dao.JpaPaymentDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.math.BigDecimal;

@WebServlet(name = "CheckoutServlet", urlPatterns = "/checkout")
public class CheckoutServlet extends HttpServlet {
    private final CartService carts;
    private final AccountService accounts;
    private final DeliveryFeeService delivery;
    private final AddressGeocoder geocoder;
    private final PromotionService promotions;
    private final CustomerOrderService orders;

    public CheckoutServlet() {
        this(new CartService(), new AccountService(), new DeliveryFeeService(), new AddressGeocoder(),
                new PromotionService(), new CustomerOrderServiceImpl(new JpaOrderDAO(), new JpaPaymentDAO()));
    }

    public CheckoutServlet(CartService carts, AccountService accounts, DeliveryFeeService delivery,
            AddressGeocoder geocoder, PromotionService promotions, CustomerOrderService orders) {
        this.carts = carts; this.accounts = accounts; this.delivery = delivery;
        this.geocoder = geocoder; this.promotions = promotions; this.orders = orders;
    }

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        render(request, response);
    }

    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            String customerId = SessionAuth.requireCustomer(request).id();
            String action = request.getParameter("action");
            if ("search".equals(action)) {
                var store = delivery.policy().store();
                request.setAttribute("locations", geocoder.search(request.getParameter("deliveryAddress"), store.latitude(), store.longitude()));
            } else if ("reverse".equals(action)) {
                var location = geocoder.reverse(ServletSupport.decimal(request.getParameter("deliveryLatitude")),
                        ServletSupport.decimal(request.getParameter("deliveryLongitude")));
                request.setAttribute("resolvedAddress", location.label());
            } else if ("placeOrder".equals(action)) {
                OrderForm form = new OrderForm();
                form.setFulfillmentType(FulfillmentType.valueOf(request.getParameter("fulfillmentType")));
                form.setPaymentMethod(PaymentMethod.valueOf(request.getParameter("paymentMethod")));
                form.setReceiverName(request.getParameter("receiverName"));
                form.setReceiverPhone(request.getParameter("receiverPhone"));
                if (form.getFulfillmentType() == FulfillmentType.DELIVERY) {
                    form.setDeliveryAddress(request.getParameter("deliveryAddress"));
                    form.setDeliveryLatitude(ServletSupport.decimal(request.getParameter("deliveryLatitude")));
                    form.setDeliveryLongitude(ServletSupport.decimal(request.getParameter("deliveryLongitude")));
                }
                form.setCustomerNote(request.getParameter("customerNote"));
                Object code = request.getSession().getAttribute("appliedPromotionCode");
                if (code != null) {
                    var validation = promotions.validatePromotion(code.toString(), customerId, null, null);
                    if (!validation.isValid()) throw new IllegalArgumentException(validation.getMessage());
                    form.setPromotionId(validation.getPromotion().getPromotionId());
                }
                var order = orders.createOrder(customerId, form);
                request.getSession().removeAttribute("appliedPromotionCode");
                request.getSession().setAttribute("cartItemCount", 0);
                ServletSupport.redirect(request, response, "/orders/" + order.getOrderId());
                return;
            } else if (!"quote".equals(action)) {
                response.sendError(400, "Thao tác không hợp lệ."); return;
            }
        } catch (Exception failure) {
            request.setAttribute("flashError", ServletSupport.message(failure));
        }
        render(request, response);
    }

    private void render(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            String customerId = SessionAuth.requireCustomer(request).id();
            var cart = carts.getOrCreateCart(customerId);
            request.setAttribute("cart", cart);
            request.setAttribute("addresses", accounts.listAddresses(customerId));
            var store = delivery.policy().store();
            request.setAttribute("storeAddress", store.address());
            request.setAttribute("storeLatitude", store.latitude());
            request.setAttribute("storeLongitude", store.longitude());
            BigDecimal subtotal = carts.getVerifiedSubtotal(customerId);
            BigDecimal discount = BigDecimal.ZERO;
            Object code = request.getSession().getAttribute("appliedPromotionCode");
            if (code != null) {
                var validation = promotions.validatePromotion(code.toString(), customerId, subtotal, null);
                if (validation.isValid()) discount = validation.getDiscountAmount();
                else {
                    request.getSession().removeAttribute("appliedPromotionCode");
                    request.setAttribute("flashError", validation.getMessage());
                }
            }
            BigDecimal fee = BigDecimal.ZERO;
            boolean ready = "PICKUP".equals(request.getParameter("fulfillmentType"));
            if (!ready) {
                Double latitude = ServletSupport.decimal(request.getParameter("deliveryLatitude"));
                Double longitude = ServletSupport.decimal(request.getParameter("deliveryLongitude"));
                if (latitude != null && longitude != null) {
                    var quote = delivery.quote(latitude, longitude, subtotal);
                    request.setAttribute("deliveryMessage", quote.message());
                    ready = quote.deliverable();
                    if (ready) fee = quote.deliveryFee();
                }
            }
            request.setAttribute("subtotal", subtotal);
            request.setAttribute("discount", discount);
            request.setAttribute("fee", fee);
            request.setAttribute("total", subtotal.subtract(discount).add(fee).max(BigDecimal.ZERO));
            request.setAttribute("deliveryReady", ready);
        } catch (Exception failure) {
            request.setAttribute("flashError", ServletSupport.message(failure));
        }
        ServletSupport.forward(request, response, "order/checkout");
    }
}
