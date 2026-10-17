package vn.edu.crave.controller.order;

import vn.edu.crave.controller.BaseServlet;

import vn.edu.crave.model.dao.CustomerOrderDAO;
import vn.edu.crave.model.dao.PaymentDAO;
import vn.edu.crave.model.form.OrderRequest;
import vn.edu.crave.model.enums.FulfillmentType;
import vn.edu.crave.model.enums.PaymentMethod;
import vn.edu.crave.exception.AppException;
import vn.edu.crave.exception.ApiException;
import vn.edu.crave.security.SessionAuth;
import vn.edu.crave.model.service.CustomerOrderService;
import vn.edu.crave.model.service.PayosPaymentService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@WebServlet(name = "OrderServlet", urlPatterns = {"/orders/create", "/orders/cancel"})
public class OrderServlet extends BaseServlet {
    private CustomerOrderService orders;
    private PayosPaymentService payos;
    public OrderServlet() { }
    public OrderServlet(CustomerOrderService orders) { this.orders = orders; }
    @Override public void init() {
        if (orders == null) orders = new CustomerOrderService(new CustomerOrderDAO(), new PaymentDAO());
        if (payos == null) payos = new PayosPaymentService();
    }
    @Override public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String destination = "/checkout";
        try {
            String customerId = SessionAuth.requireCustomer(request).id();
            if ("/orders/cancel".equals(request.getServletPath())) {
                String orderId = parameter(request, "orderId");
                destination = "/orders/" + URLEncoder.encode(orderId, StandardCharsets.UTF_8);
                orders.cancelOrder(orderId, customerId, parameter(request, "reason"));
                try { if (payos != null) payos.cancelForOrder(orderId); }
                catch (RuntimeException unavailable) { log("Chưa đồng bộ được hủy link payOS."); }
                request.getSession().setAttribute("orderMessage", "Đã hủy đơn hàng.");
            } else {
                OrderRequest form = new OrderRequest();
                form.setFulfillmentType(FulfillmentType.valueOf(parameter(request, "fulfillmentType")));
                form.setReceiverName(parameter(request, "receiverName"));
                form.setReceiverPhone(parameter(request, "receiverPhone"));
                form.setDeliveryAddress(parameter(request, "deliveryAddress"));
                form.setCustomerNote(parameter(request, "customerNote"));
                form.setPaymentMethod(PaymentMethod.valueOf(parameter(request, "paymentMethod")));
                if (form.getFulfillmentType() == FulfillmentType.DELIVERY) {
                    form.setDeliveryLatitude(number(request, "deliveryLatitude"));
                    form.setDeliveryLongitude(number(request, "deliveryLongitude"));
                }
                form.setPromotionId((String) request.getSession().getAttribute("selectedPromotionId"));
                var order = orders.createOrder(customerId, form);
                request.getSession().removeAttribute("selectedPromotionId");
                request.getSession().removeAttribute("selectedPromotionCode");
                request.getSession().setAttribute("cartItemCount", 0);
                destination = "/orders/" + URLEncoder.encode(order.getOrderId(), StandardCharsets.UTF_8);
                if (form.getPaymentMethod() != PaymentMethod.CASH) {
                    try { payos.create(order.getOrderId(), customerId); }
                    catch (RuntimeException unavailable) { log("Chưa tạo được link payOS; khách có thể thử lại trên chi tiết đơn."); }
                }
            }
        } catch (AppException | ApiException | IllegalArgumentException failure) {
            if (failure instanceof AppException account && (account.getStatusCode() == 401 || account.getStatusCode() == 403)) {
                response.sendError(account.getStatusCode(), account.getMessage());
                return;
            }
            request.getSession().setAttribute("orderError", failure instanceof IllegalArgumentException
                    ? "Vui lòng kiểm tra hình thức nhận món, phương thức thanh toán và điểm nhận hàng." : failure.getMessage());
        } catch (RuntimeException failure) {
            log("Không xử lý được đơn hàng.", failure);
            request.getSession().setAttribute("orderError", "Chưa xử lý được đơn hàng. Vui lòng kiểm tra và thử lại.");
        }
        redirect(request, response, destination);
    }
    private static Double number(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        return value == null || value.isBlank() ? null : Double.valueOf(value);
    }
}
