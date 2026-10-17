package vn.edu.crave.model.service;

import jakarta.persistence.EntityManagerFactory;
import vn.edu.crave.model.dao.CustomerOrderDAO;
import vn.edu.crave.model.dao.PaymentDAO;

/**
 * Service xu ly don hang (OrderService).
 */
public class OrderService extends CustomerOrderService {
    public OrderService(CustomerOrderDAO orderDAO, PaymentDAO paymentDAO) {
        super(orderDAO, paymentDAO);
    }
    public OrderService(CustomerOrderDAO orderDAO, PaymentDAO paymentDAO, EntityManagerFactory emf) {
        super(orderDAO, paymentDAO, emf);
    }
    public OrderService(CustomerOrderDAO orderDAO, PaymentDAO paymentDAO, EntityManagerFactory emf, DeliveryFeeService deliveryFees) {
        super(orderDAO, paymentDAO, emf, deliveryFees);
    }
}
