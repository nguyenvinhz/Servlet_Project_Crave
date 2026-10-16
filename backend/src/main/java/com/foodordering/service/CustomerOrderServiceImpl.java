package com.foodordering.service;

import com.foodordering.config.DatabaseConfig;
import com.foodordering.bean.OrderForm;
import com.foodordering.bean.OrderBean;
import com.foodordering.bean.OrderSummaryBean;
import com.foodordering.model.*;
import com.foodordering.enums.ErrorCode;
import com.foodordering.enums.FulfillmentType;
import com.foodordering.enums.OrderStatus;
import com.foodordering.enums.OptionType;
import com.foodordering.enums.PaymentStatus;
import com.foodordering.exception.BusinessException;
import com.foodordering.exception.AppException;
import com.foodordering.exception.BadRequestException;
import com.foodordering.exception.ForbiddenException;
import com.foodordering.exception.ResourceNotFoundException;
import com.foodordering.dao.OrderDAO;
import com.foodordering.dao.PaymentDAO;
import com.foodordering.validator.PromotionValidator;
import com.foodordering.validator.OrderValidator;
import com.foodordering.validator.OptionSelectionValidator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.Query;
import jakarta.persistence.LockModeType;

import java.math.BigDecimal;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.stream.Collectors;

public class CustomerOrderServiceImpl implements CustomerOrderService {

    private final OrderDAO orderDAO;
    private final PaymentDAO paymentDAO;
    private final EntityManagerFactory emf;
    private final DeliveryFeeService deliveryFees;

    public CustomerOrderServiceImpl(OrderDAO orderDAO, PaymentDAO paymentDAO) {
        this(orderDAO, paymentDAO, null);
    }

    public CustomerOrderServiceImpl(OrderDAO orderDAO, PaymentDAO paymentDAO, EntityManagerFactory emf) {
        this(orderDAO, paymentDAO, emf, new DeliveryFeeService());
    }

    public CustomerOrderServiceImpl(OrderDAO orderDAO, PaymentDAO paymentDAO,
                                    EntityManagerFactory emf, DeliveryFeeService deliveryFees) {
        this.orderDAO = orderDAO;
        this.paymentDAO = paymentDAO;
        this.emf = emf;
        this.deliveryFees = deliveryFees;
    }

    private EntityManager getEntityManager() {
        return emf != null ? emf.createEntityManager() : DatabaseConfig.getEntityManagerFactory().createEntityManager();
    }

    private String generateId(int length) {
        return UUID.randomUUID().toString().replace("-", "").substring(0, length).toUpperCase();
    }

    @Override
    public OrderBean createOrder(String customerId, OrderForm request) {
        OrderValidator.validate(request);
        DeliveryFeeService.Quote deliveryQuote = request.getFulfillmentType() == FulfillmentType.DELIVERY
                ? deliveryFees.requireDeliverable(request.getDeliveryLatitude(), request.getDeliveryLongitude()) : null;

        EntityManager em = getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();

            Customer customer = em.find(Customer.class, customerId);
            if (customer == null) {
                throw new ResourceNotFoundException("Khách hàng không tồn tại");
            }

            // Lấy Cart ID của khách và lock bằng FOR UPDATE để tránh checkout trùng
            Query cartQuery = em.createNativeQuery("SELECT cart_id FROM cart WHERE customer_id = ? FOR UPDATE");
            cartQuery.setParameter(1, customerId);
            List<?> carts = cartQuery.getResultList();
            if (carts.isEmpty()) {
                throw new BadRequestException(ErrorCode.CART_EMPTY);
            }
            String cartId = carts.get(0).toString();

            // Lấy tổng tiền giỏ hàng (subtotal)
            Query subtotalQuery = em.createNativeQuery("SELECT subtotal FROM v_cart_summary WHERE cart_id = ?");
            subtotalQuery.setParameter(1, cartId);
            BigDecimal subtotal = (BigDecimal) subtotalQuery.getSingleResult();

            if (subtotal == null || subtotal.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException(ErrorCode.CART_EMPTY);
            }
            if (deliveryQuote != null) {
                deliveryQuote = deliveryFees.requireDeliverable(request.getDeliveryLatitude(), request.getDeliveryLongitude(), subtotal);
            }

            CustomerOrder order = new CustomerOrder();
            order.setId(generateId(10));
            order.setCustomer(customer);
            order.setFulfillmentType(request.getFulfillmentType());
            order.setReceiverName(request.getReceiverName());
            order.setReceiverPhone(request.getReceiverPhone());
            order.setDeliveryAddress(request.getDeliveryAddress());
            order.setCustomerNote(request.getCustomerNote());
            
            // Xác thực Promotion
            if (request.getPromotionId() != null && !request.getPromotionId().trim().isEmpty()) {
                Promotion promotion = em.find(Promotion.class, request.getPromotionId().trim());
                PromotionValidator.validateApplicable(promotion, subtotal, order.getOrderedAt());
                order.setPromotionId(promotion.getPromotionId());
            }
            
            order.setSubtotal(subtotal);
            order.setDeliveryFee(deliveryQuote != null ? deliveryQuote.deliveryFee() : BigDecimal.ZERO);
            order.setDeliveryLatitude(request.getDeliveryLatitude());
            order.setDeliveryLongitude(request.getDeliveryLongitude());
            order.setDeliveryDistanceKm(deliveryQuote != null ? deliveryQuote.distanceKm() : null);

            em.persist(order);

            // Copy từ CartItem sang OrderDetail
            Query itemsQuery = em.createNativeQuery("SELECT ci.cart_item_id, ci.food_id, f.name, ci.quantity, f.price, ci.note, f.status " +
                    "FROM cart_item ci JOIN food f ON f.food_id = ci.food_id " +
                    "WHERE ci.cart_id = ?");
            itemsQuery.setParameter(1, cartId);
            List<Object[]> items = itemsQuery.getResultList();

            for (Object[] item : items) {
                String foodStatus = (String) item[6];
                if (!"AVAILABLE".equals(foodStatus)) {
                    throw new BadRequestException(ErrorCode.FOOD_NOT_AVAILABLE,
                            "Món " + item[2] + " hiện không khả dụng");
                }

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
                Query optionsQuery = em.createNativeQuery("SELECT cio.option_id, fo.name, fo.extra_price, fo.status, fo.food_id, fo.option_type " +
                        "FROM cart_item_option cio JOIN food_option fo ON fo.option_id = cio.option_id " +
                        "WHERE cio.cart_item_id = ?");
                optionsQuery.setParameter(1, cartItemId);
                List<Object[]> options = optionsQuery.getResultList();

                BigDecimal optionTotal = BigDecimal.ZERO;
                List<OptionType> optionTypes = new ArrayList<>();
                for (Object[] opt : options) {
                    if (!"ACTIVE".equals(opt[3]) || !detail.getFoodId().equals(opt[4])) {
                        throw new BadRequestException(ErrorCode.INVALID_OPTION);
                    }
                    OrderDetailOption detailOpt = new OrderDetailOption();
                    detailOpt.setOrderDetail(detail);
                    detailOpt.setOptionId((String) opt[0]);
                    detailOpt.setOptionNameSnapshot((String) opt[1]);
                    BigDecimal extraPrice = (BigDecimal) opt[2];
                    detailOpt.setExtraPriceSnapshot(extraPrice);
                    detail.getOptions().add(detailOpt);
                    optionTotal = optionTotal.add(extraPrice);
                    optionTypes.add(OptionType.valueOf((String) opt[5]));
                }
                OptionSelectionValidator.validate(optionTypes);

                detail.setUnitPrice(price.add(optionTotal));
                em.persist(detail);
            }

            // Flush để các trigger và database xử lý discount_amount, subtotal và total_amount
            em.flush();
            // Refresh order để lấy lại giá trị từ DB
            em.refresh(order);

            Payment payment = new Payment();
            payment.setId(generateId(12));
            payment.setOrder(order);
            payment.setPaymentMethod(request.getPaymentMethod());
            payment.setStatus(PaymentStatus.PENDING);
            
            BigDecimal finalAmount = order.getTotalAmount() != null ? order.getTotalAmount() : 
                    order.getSubtotal().add(order.getDeliveryFee()).subtract(order.getDiscountAmount());
            payment.setAmount(finalAmount); 
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
        } catch (BusinessException e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw new RuntimeException("Lỗi tạo đơn hàng: " + e.getMessage(), e);
        } finally {
            em.close();
        }
    }

    @Override
    public OrderBean getOrderById(String orderId) {
        EntityManager em = getEntityManager();
        try {
            CustomerOrder order = em.find(CustomerOrder.class, orderId);
            if (order == null) throw new ResourceNotFoundException("Đơn hàng không tồn tại");
            // Kích hoạt lazy loading trong scope của em
            if (order.getOrderDetails() != null) order.getOrderDetails().size();
            if (order.getPayment() != null) order.getPayment().getId();
            if (order.getStatusHistories() != null) order.getStatusHistories().size();
            return mapToResponse(order);
        } finally {
            em.close();
        }
    }

    @Override
    public List<OrderSummaryBean> getOrdersByCustomer(String customerId) {
        EntityManager em = getEntityManager();
        try {
            String jpql = "SELECT o FROM CustomerOrder o WHERE o.customer.id = :customerId ORDER BY o.orderedAt DESC";
            List<CustomerOrder> orders = em.createQuery(jpql, CustomerOrder.class)
                    .setParameter("customerId", customerId)
                    .getResultList();
            for (CustomerOrder o : orders) {
                if (o.getPayment() != null) o.getPayment().getId();
            }
            return orders.stream()
                    .map(this::mapToSummaryResponse)
                    .collect(Collectors.toList());
        } finally {
            em.close();
        }
    }

    @Override
    public List<OrderBean> getAllOrdersForAdmin() {
        EntityManager em = getEntityManager();
        try {
            String jpql = "SELECT o FROM CustomerOrder o ORDER BY o.orderedAt DESC";
            List<CustomerOrder> orders = em.createQuery(jpql, CustomerOrder.class).getResultList();
            for (CustomerOrder o : orders) {
                if (o.getOrderDetails() != null) o.getOrderDetails().size();
                if (o.getPayment() != null) o.getPayment().getId();
                if (o.getStatusHistories() != null) o.getStatusHistories().size();
            }
            return orders.stream()
                    .map(this::mapToResponse)
                    .collect(Collectors.toList());
        } finally {
            em.close();
        }
    }

    @Override
    public void updateOrderStatus(String orderId, OrderStatus status, String employeeId, String note) {
        if (status == null) {
            throw new BadRequestException("Trạng thái đơn hàng không được để trống");
        }
        if (note != null && note.trim().length() > 255) {
            throw new BadRequestException("Ghi chú trạng thái không được vượt quá 255 ký tự");
        }
        EntityManager em = getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            CustomerOrder order = em.find(CustomerOrder.class, orderId);
            if (order == null) throw new ResourceNotFoundException("Đơn hàng không tồn tại");
            em.refresh(order, LockModeType.PESSIMISTIC_WRITE);

            OrderStatus currentStatus = order.getStatus();
            if (currentStatus == status) {
                tx.commit();
                return;
            }
            if (currentStatus == OrderStatus.COMPLETED || currentStatus == OrderStatus.CANCELLED) {
                throw new BadRequestException("Không thể cập nhật đơn hàng đã " + (currentStatus == OrderStatus.COMPLETED ? "hoàn tất" : "hủy"));
            }

            boolean isValidTransition = false;
            if (currentStatus == OrderStatus.PENDING_CONFIRMATION) {
                isValidTransition = (status == OrderStatus.PREPARING || status == OrderStatus.CANCELLED);
            } else if (currentStatus == OrderStatus.PREPARING) {
                isValidTransition = status == OrderStatus.CANCELLED
                        || (order.getFulfillmentType() == FulfillmentType.PICKUP
                            ? status == OrderStatus.COMPLETED : status == OrderStatus.DELIVERING);
            } else if (currentStatus == OrderStatus.DELIVERING) {
                isValidTransition = (status == OrderStatus.COMPLETED);
            }
            if (!isValidTransition && currentStatus != status) {
                throw new BadRequestException("Không thể chuyển trạng thái từ " + currentStatus + " sang " + status);
            }

            order.setStatus(status);
            order.setAssignedEmployeeId(employeeId);
            em.merge(order);

            OrderStatusHistory history = new OrderStatusHistory();
            history.setId(generateId(10));
            history.setOrder(order);
            history.setStatus(status);
            history.setChangedByEmployeeId(employeeId);
            history.setNote(note != null ? note.trim() : null);
            em.persist(history);

            tx.commit();
        } catch (BusinessException e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        } catch (AppException e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        } catch (IllegalArgumentException e) {
            if (tx.isActive()) tx.rollback();
            throw new BadRequestException(e.getMessage());
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw new RuntimeException("Lỗi cập nhật trạng thái: " + e.getMessage(), e);
        } finally {
            em.close();
        }
    }

    @Override
    public void cancelOrder(String orderId, String customerId, String reason) {
        if (orderId == null || orderId.trim().isEmpty()) {
            throw new BadRequestException("Mã đơn hàng không hợp lệ");
        }
        if (customerId == null || customerId.trim().isEmpty()) {
            throw new BadRequestException("Mã khách hàng không hợp lệ");
        }
        if (reason != null && reason.trim().length() > 255) {
            throw new BadRequestException("Lý do hủy không được vượt quá 255 ký tự");
        }

        EntityManager em = getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            CustomerOrder order = em.find(CustomerOrder.class, orderId);
            if (order == null) {
                throw new ResourceNotFoundException("Đơn hàng không tồn tại");
            }
            em.refresh(order, LockModeType.PESSIMISTIC_WRITE);

            if (!customerId.equals(order.getCustomer().getId())) {
                throw new ForbiddenException("Bạn không có quyền thao tác trên đơn hàng này");
            }

            OrderStatus currentStatus = order.getStatus();
            if (currentStatus == OrderStatus.CANCELLED) {
                throw new BadRequestException("Đơn hàng này đã được hủy trước đó");
            }
            if (currentStatus == OrderStatus.COMPLETED) {
                throw new BadRequestException("Không thể hủy đơn hàng đã hoàn tất");
            }
            if (currentStatus == OrderStatus.DELIVERING) {
                throw new BadRequestException("Không thể hủy đơn hàng đang được giao");
            }
            if (currentStatus != OrderStatus.PENDING_CONFIRMATION && currentStatus != OrderStatus.PREPARING) {
                throw new BadRequestException("Không thể hủy đơn hàng ở trạng thái: " + currentStatus);
            }

            order.setStatus(OrderStatus.CANCELLED);
            if (order.getPayment() != null && order.getPayment().getStatus() == PaymentStatus.PENDING) {
                order.getPayment().setStatus(PaymentStatus.FAILED);
            }
            em.merge(order);

            String note = "Khách hàng hủy";
            if (reason != null && !reason.trim().isEmpty()) {
                note += ": " + reason.trim();
            }
            if (note.length() > 255) {
                note = note.substring(0, 255);
            }

            OrderStatusHistory history = new OrderStatusHistory();
            history.setId(generateId(10));
            history.setOrder(order);
            history.setStatus(OrderStatus.CANCELLED);
            history.setChangedByEmployeeId(null);
            history.setNote(note);
            em.persist(history);

            tx.commit();
        } catch (BusinessException e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        } catch (AppException e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw new RuntimeException("Lỗi hủy đơn hàng: " + e.getMessage(), e);
        } finally {
            em.close();
        }
    }

    private OrderSummaryBean mapToSummaryResponse(CustomerOrder order) {
        OrderSummaryBean res = new OrderSummaryBean();
        res.setOrderId(order.getId());
        res.setOrderedAt(order.getOrderedAt());
        res.setFulfillmentType(order.getFulfillmentType());
        res.setTotalAmount(order.getTotalAmount() != null ? order.getTotalAmount() : order.getSubtotal().add(order.getDeliveryFee()).subtract(order.getDiscountAmount()));
        res.setStatus(order.getStatus());
        if (order.getPayment() != null) {
            res.setPaymentStatus(order.getPayment().getStatus().name());
        }
        return res;
    }

    private OrderBean mapToResponse(CustomerOrder order) {
        OrderBean res = new OrderBean();
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
        res.setDeliveryLatitude(order.getDeliveryLatitude());
        res.setDeliveryLongitude(order.getDeliveryLongitude());
        res.setDeliveryDistanceKm(order.getDeliveryDistanceKm());
        res.setTotalAmount(order.getTotalAmount() != null ? order.getTotalAmount() : order.getSubtotal().add(order.getDeliveryFee()).subtract(order.getDiscountAmount()));
        res.setStatus(order.getStatus());

        if (order.getOrderDetails() != null) {
            List<OrderBean.OrderItemBean> items = order.getOrderDetails().stream().map(detail -> {
                OrderBean.OrderItemBean itemRes = new OrderBean.OrderItemBean();
                itemRes.setFoodId(detail.getFoodId());
                itemRes.setFoodName(detail.getFoodNameSnapshot());
                itemRes.setFoodNameSnapshot(detail.getFoodNameSnapshot());
                itemRes.setQuantity(detail.getQuantity());
                itemRes.setUnitPrice(detail.getUnitPrice());
                itemRes.setLineTotal(detail.getUnitPrice().multiply(BigDecimal.valueOf(detail.getQuantity())));
                itemRes.setNote(detail.getNote());
                itemRes.setOptions(detail.getOptions().stream().map(option -> {
                    OrderBean.OptionSnapshot snapshot = new OrderBean.OptionSnapshot();
                    snapshot.setOptionName(option.getOptionNameSnapshot());
                    snapshot.setExtraPrice(option.getExtraPriceSnapshot());
                    return snapshot;
                }).collect(Collectors.toList()));
                return itemRes;
            }).collect(Collectors.toList());
            res.setItems(items);
        }

        if (order.getPayment() != null) {
            OrderBean.PaymentSummary paymentRes = new OrderBean.PaymentSummary();
            paymentRes.setPaymentId(order.getPayment().getId());
            paymentRes.setMethod(order.getPayment().getPaymentMethod().name());
            paymentRes.setStatus(order.getPayment().getStatus().name());
            paymentRes.setAmount(order.getPayment().getAmount());
            res.setPayment(paymentRes);
        }
        
        if (order.getStatusHistories() != null) {
            List<OrderBean.StatusHistoryEntry> history = order.getStatusHistories().stream().map(h -> {
                OrderBean.StatusHistoryEntry entry = new OrderBean.StatusHistoryEntry();
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
