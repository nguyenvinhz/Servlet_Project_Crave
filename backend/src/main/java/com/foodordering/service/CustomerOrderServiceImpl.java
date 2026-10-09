package com.foodordering.service;

import com.foodordering.config.DatabaseConfig;
import com.foodordering.dto.OrderRequest;
import com.foodordering.dto.OrderResponse;
import com.foodordering.dto.OrderSummaryResponse;
import com.foodordering.entity.*;
import com.foodordering.enums.OrderStatus;
import com.foodordering.enums.PaymentStatus;
import com.foodordering.repository.CustomerOrderRepository;
import com.foodordering.repository.PaymentRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class CustomerOrderServiceImpl implements CustomerOrderService {

    private final CustomerOrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    public CustomerOrderServiceImpl(CustomerOrderRepository orderRepository, PaymentRepository paymentRepository) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
    }

    private String generateId(int length) {
        return UUID.randomUUID().toString().replace("-", "").substring(0, length).toUpperCase();
    }

    @Override
    public OrderResponse createOrder(String customerId, OrderRequest request) {
        if (request.getReceiverName() == null || request.getReceiverName().trim().isEmpty()) {
            throw new IllegalArgumentException("Tên người nhận không được để trống");
        }
        if (request.getReceiverPhone() == null || request.getReceiverPhone().trim().isEmpty()) {
            throw new IllegalArgumentException("Số điện thoại không được để trống");
        }
        if (com.foodordering.enums.FulfillmentType.DELIVERY.equals(request.getFulfillmentType())) {
            if (request.getDeliveryAddress() == null || request.getDeliveryAddress().trim().isEmpty()) {
                throw new IllegalArgumentException("Địa chỉ giao hàng không được để trống khi chọn giao tận nơi");
            }
        }

        EntityManager em = DatabaseConfig.getEntityManagerFactory().createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();

            Customer customer = em.find(Customer.class, customerId);
            if (customer == null) {
                throw new RuntimeException("Khách hàng không tồn tại");
            }

            // Lấy Cart ID của khách
            Query cartQuery = em.createNativeQuery("SELECT cart_id FROM cart WHERE customer_id = ?");
            cartQuery.setParameter(1, customerId);
            List<?> carts = cartQuery.getResultList();
            if (carts.isEmpty()) {
                throw new RuntimeException("Giỏ hàng trống");
            }
            String cartId = carts.get(0).toString();

            // Lấy tổng tiền giỏ hàng (subtotal)
            Query subtotalQuery = em.createNativeQuery("SELECT subtotal FROM v_cart_summary WHERE cart_id = ?");
            subtotalQuery.setParameter(1, cartId);
            BigDecimal subtotal = (BigDecimal) subtotalQuery.getSingleResult();

            if (subtotal == null || subtotal.compareTo(BigDecimal.ZERO) == 0) {
                throw new RuntimeException("Giỏ hàng không có món ăn hợp lệ");
            }

            CustomerOrder order = new CustomerOrder();
            order.setId(generateId(10));
            order.setCustomer(customer);
            order.setFulfillmentType(request.getFulfillmentType());
            order.setReceiverName(request.getReceiverName());
            order.setReceiverPhone(request.getReceiverPhone());
            order.setDeliveryAddress(request.getDeliveryAddress());
            order.setCustomerNote(request.getCustomerNote());
            order.setPromotionId(request.getPromotionId());
            order.setSubtotal(subtotal);
            order.setDeliveryFee(new BigDecimal("15000")); // Hardcode fee cho demo

            em.persist(order);

            // Copy từ CartItem sang OrderDetail
            Query itemsQuery = em.createNativeQuery("SELECT ci.cart_item_id, ci.food_id, f.name, ci.quantity, f.price, ci.note " +
                    "FROM cart_item ci JOIN food f ON f.food_id = ci.food_id " +
                    "WHERE ci.cart_id = ?");
            itemsQuery.setParameter(1, cartId);
            List<Object[]> items = itemsQuery.getResultList();

            for (Object[] item : items) {
                String cartItemId = (String) item[0];
                OrderDetail detail = new OrderDetail();
                detail.setId(generateId(10));
                detail.setOrder(order);
                detail.setFoodId((String) item[1]);
                detail.setFoodNameSnapshot((String) item[2]);
                detail.setQuantity(((Number) item[3]).intValue());
                BigDecimal price = (BigDecimal) item[4];
                detail.setNote((String) item[5]);

                // Copy tùy chọn (options)
                Query optionsQuery = em.createNativeQuery("SELECT cio.option_id, fo.name, fo.extra_price " +
                        "FROM cart_item_option cio JOIN food_option fo ON fo.option_id = cio.option_id " +
                        "WHERE cio.cart_item_id = ?");
                optionsQuery.setParameter(1, cartItemId);
                List<Object[]> options = optionsQuery.getResultList();

                BigDecimal optionTotal = BigDecimal.ZERO;
                for (Object[] opt : options) {
                    OrderDetailOption detailOpt = new OrderDetailOption();
                    detailOpt.setOrderDetail(detail);
                    detailOpt.setOptionId((String) opt[0]);
                    detailOpt.setOptionNameSnapshot((String) opt[1]);
                    BigDecimal extraPrice = (BigDecimal) opt[2];
                    detailOpt.setExtraPriceSnapshot(extraPrice);
                    detail.getOptions().add(detailOpt);
                    optionTotal = optionTotal.add(extraPrice);
                }

                detail.setUnitPrice(price.add(optionTotal));
                em.persist(detail);
            }

            Payment payment = new Payment();
            payment.setId(generateId(12));
            payment.setOrder(order);
            payment.setPaymentMethod(request.getPaymentMethod());
            payment.setStatus(PaymentStatus.PENDING);
            payment.setAmount(subtotal.add(order.getDeliveryFee())); // Rough calculation
            em.persist(payment);

            OrderStatusHistory history = new OrderStatusHistory();
            history.setId(generateId(10));
            history.setOrder(order);
            history.setStatus(OrderStatus.PENDING_CONFIRMATION);
            history.setNote("Đơn hàng được tạo mới");
            em.persist(history);

            // Xóa giỏ hàng
            Query delCartQuery = em.createNativeQuery("DELETE FROM cart WHERE cart_id = ?");
            delCartQuery.setParameter(1, cartId);
            delCartQuery.executeUpdate();

            tx.commit();
            return getOrderById(order.getId());
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw new RuntimeException("Lỗi tạo đơn hàng: " + e.getMessage(), e);
        } finally {
            em.close();
        }
    }

    @Override
    public OrderResponse getOrderById(String orderId) {
        CustomerOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Đơn hàng không tồn tại"));
        return mapToResponse(order);
    }

    @Override
    public List<OrderSummaryResponse> getOrdersByCustomer(String customerId) {
        return orderRepository.findByCustomerId(customerId).stream()
                .map(this::mapToSummaryResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<OrderResponse> getAllOrdersForAdmin() {
        return orderRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void updateOrderStatus(String orderId, OrderStatus status, String employeeId, String note) {
        EntityManager em = DatabaseConfig.getEntityManagerFactory().createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            CustomerOrder order = em.find(CustomerOrder.class, orderId);
            if (order == null) throw new RuntimeException("Đơn hàng không tồn tại");

            OrderStatus currentStatus = order.getStatus();
            boolean isValidTransition = false;
            if (currentStatus == OrderStatus.PENDING_CONFIRMATION) {
                isValidTransition = (status == OrderStatus.PREPARING || status == OrderStatus.CANCELLED);
            } else if (currentStatus == OrderStatus.PREPARING) {
                isValidTransition = (status == OrderStatus.DELIVERING || status == OrderStatus.CANCELLED);
            } else if (currentStatus == OrderStatus.DELIVERING) {
                isValidTransition = (status == OrderStatus.COMPLETED);
            }
            if (!isValidTransition && currentStatus != status) {
                throw new IllegalArgumentException("Không thể chuyển trạng thái từ " + currentStatus + " sang " + status);
            }

            order.setStatus(status);
            order.setAssignedEmployeeId(employeeId);
            em.merge(order);

            OrderStatusHistory history = new OrderStatusHistory();
            history.setId(generateId(10));
            history.setOrder(order);
            history.setStatus(status);
            history.setChangedByEmployeeId(employeeId);
            history.setNote(note);
            em.persist(history);

            tx.commit();
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw new RuntimeException("Lỗi cập nhật trạng thái: " + e.getMessage(), e);
        } finally {
            em.close();
        }
    }

    private OrderSummaryResponse mapToSummaryResponse(CustomerOrder order) {
        OrderSummaryResponse res = new OrderSummaryResponse();
        res.setOrderId(order.getId());
        res.setOrderedAt(order.getOrderedAt());
        res.setFulfillmentType(order.getFulfillmentType().name());
        res.setTotalAmount(order.getTotalAmount() != null ? order.getTotalAmount() : order.getSubtotal().add(order.getDeliveryFee()).subtract(order.getDiscountAmount()));
        res.setStatus(order.getStatus().name());
        if (order.getPayment() != null) {
            res.setPaymentStatus(order.getPayment().getStatus().name());
        }
        return res;
    }

    private OrderResponse mapToResponse(CustomerOrder order) {
        OrderResponse res = new OrderResponse();
        res.setOrderId(order.getId());
        res.setCustomerId(order.getCustomer().getId());
        res.setOrderedAt(order.getOrderedAt());
        res.setFulfillmentType(order.getFulfillmentType());
        res.setReceiverName(order.getReceiverName());
        res.setReceiverPhone(order.getReceiverPhone());
        res.setDeliveryAddress(order.getDeliveryAddress());
        res.setCustomerNote(order.getCustomerNote());
        res.setSubtotal(order.getSubtotal());
        res.setDiscountAmount(order.getDiscountAmount());
        res.setDeliveryFee(order.getDeliveryFee());
        res.setTotalAmount(order.getTotalAmount() != null ? order.getTotalAmount() : order.getSubtotal().add(order.getDeliveryFee()).subtract(order.getDiscountAmount()));
        res.setStatus(order.getStatus());

        if (order.getOrderDetails() != null) {
            List<OrderResponse.OrderItemResponse> items = order.getOrderDetails().stream().map(detail -> {
                OrderResponse.OrderItemResponse itemRes = new OrderResponse.OrderItemResponse();
                itemRes.setFoodId(detail.getFoodId());
                itemRes.setFoodNameSnapshot(detail.getFoodNameSnapshot());
                itemRes.setQuantity(detail.getQuantity());
                itemRes.setUnitPrice(detail.getUnitPrice());
                itemRes.setNote(detail.getNote());
                
                // Map options if needed (skipping option details in basic response for now or mapping it manually if needed)
                return itemRes;
            }).collect(Collectors.toList());
            res.setItems(items);
        }

        if (order.getPayment() != null) {
            OrderResponse.PaymentSummary paymentRes = new OrderResponse.PaymentSummary();
            paymentRes.setPaymentId(order.getPayment().getId());
            paymentRes.setMethod(order.getPayment().getPaymentMethod().name());
            paymentRes.setStatus(order.getPayment().getStatus().name());
            paymentRes.setAmount(order.getPayment().getAmount());
            res.setPayment(paymentRes);
        }
        
        if (order.getStatusHistories() != null) {
            List<OrderResponse.StatusHistoryEntry> history = order.getStatusHistories().stream().map(h -> {
                OrderResponse.StatusHistoryEntry entry = new OrderResponse.StatusHistoryEntry();
                entry.setStatus(h.getStatus().name());
                entry.setChangedAt(h.getChangedAt());
                entry.setNote(h.getNote());
                return entry;
            }).collect(java.util.stream.Collectors.toList());
            res.setHistory(history);
        }

        return res;
    }
}
