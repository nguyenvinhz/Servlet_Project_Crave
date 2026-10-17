package vn.edu.crave.controller.admin;

import vn.edu.crave.controller.BaseServlet;

import vn.edu.crave.model.dao.CustomerOrderDAO;
import vn.edu.crave.model.dao.PaymentDAO;
import vn.edu.crave.model.viewmodel.OrderResponse;
import vn.edu.crave.model.enums.EmployeeRole;
import vn.edu.crave.model.enums.OrderStatus;
import vn.edu.crave.security.SessionAuth;
import vn.edu.crave.model.service.CustomerOrderService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet(name = "AdminOrdersPageServlet", urlPatterns = "/admin/orders")
public class AdminOrdersPageServlet extends BaseServlet {
    private CustomerOrderService orders;
    @Override public void init() { orders = new CustomerOrderService(new CustomerOrderDAO(), new PaymentDAO()); }
    @Override public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            SessionAuth.requireEmployee(request, EmployeeRole.ORDER_STAFF);
            var all = orders.getAllOrdersForAdmin();
            request.setAttribute("orders", all);
            request.setAttribute("orderTotal", all.size());
            request.setAttribute("orderPending", all.stream().filter(o -> o.getStatus() == OrderStatus.PENDING_CONFIRMATION).count());
            request.setAttribute("orderCompleted", all.stream().filter(o -> o.getStatus() == OrderStatus.COMPLETED).count());
            request.setAttribute("orderProcessing", all.stream().filter(o -> o.getStatus() == OrderStatus.PREPARING || o.getStatus() == OrderStatus.DELIVERING).count());
            String selected = parameter(request, "detail");
            OrderResponse detail = all.stream().filter(o -> o.getOrderId().equals(selected)).findFirst().orElse(null);
            request.setAttribute("detailOrder", detail);
            request.getRequestDispatcher("/WEB-INF/views/admin/admin-orders.jsp").forward(request, response);
        } catch (RuntimeException failure) { handleError(response, failure); }
    }
}