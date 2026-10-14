package com.foodordering.service;

import com.foodordering.dto.OrderRequest;
import com.foodordering.dto.OrderResponse;
import com.foodordering.entity.Customer;
import com.foodordering.entity.CustomerOrder;
import com.foodordering.enums.FulfillmentType;
import com.foodordering.enums.OrderStatus;
import com.foodordering.enums.PaymentMethod;
import com.foodordering.exception.BadRequestException;
import com.foodordering.exception.ForbiddenException;
import com.foodordering.exception.ResourceNotFoundException;
import com.foodordering.repository.CustomerOrderRepository;
import com.foodordering.repository.PaymentRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerOrderServiceTest {

    private CustomerOrderRepository orderRepository;
    private PaymentRepository paymentRepository;
    private EntityManagerFactory emf;
    private EntityManager em;
    private EntityTransaction tx;
    private CustomerOrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(CustomerOrderRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        emf = mock(EntityManagerFactory.class);
        em = mock(EntityManager.class);
        tx = mock(EntityTransaction.class);

        when(emf.createEntityManager()).thenReturn(em);
        when(em.getTransaction()).thenReturn(tx);
        when(tx.isActive()).thenReturn(true);

        orderService = new CustomerOrderServiceImpl(orderRepository, paymentRepository, emf);
    }

    @Test
    @DisplayName("Tạo đơn hàng thất bại khi thiếu tên người nhận")
    void createOrderFailsWhenReceiverNameIsEmpty() {
        OrderRequest request = new OrderRequest();
        request.setReceiverName("   ");
        request.setReceiverPhone("0987654321");
        request.setFulfillmentType(FulfillmentType.PICKUP);
        request.setPaymentMethod(PaymentMethod.CASH);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> orderService.createOrder("C001", request));
        assertTrue(ex.getMessage().contains("Tên người nhận không được để trống"));
    }

    @Test
    void outsideRadiusCannotCreateAnOrderEvenWithoutCallingQuoteApi() {
        CustomerOrderServiceImpl service = new CustomerOrderServiceImpl(orderRepository, paymentRepository, emf,
                new DeliveryFeeService(new DeliveryFeeService.Store("Fixture store", 10.89, 106.76)));
        OrderRequest request = new OrderRequest();
        request.setReceiverName("Customer");
        request.setReceiverPhone("0901234567");
        request.setFulfillmentType(FulfillmentType.DELIVERY);
        request.setPaymentMethod(PaymentMethod.CASH);
        request.setDeliveryAddress("Outside the delivery radius");
        request.setDeliveryLatitude(11.1);
        request.setDeliveryLongitude(106.76);
        BadRequestException failure = assertThrows(BadRequestException.class, () -> service.createOrder("KH01", request));
        assertEquals(com.foodordering.enums.ErrorCode.DELIVERY_OUT_OF_RANGE, failure.getErrorCode());
        verify(emf, never()).createEntityManager();
        verify(em, never()).persist(any());
    }

    @Test
    @DisplayName("Tạo đơn hàng thất bại khi thiếu số điện thoại người nhận")
    void createOrderFailsWhenReceiverPhoneIsEmpty() {
        OrderRequest request = new OrderRequest();
        request.setReceiverName("Nguyễn Văn A");
        request.setReceiverPhone("");
        request.setFulfillmentType(FulfillmentType.PICKUP);
        request.setPaymentMethod(PaymentMethod.CASH);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> orderService.createOrder("C001", request));
        assertTrue(ex.getMessage().contains("Số điện thoại không được để trống"));
    }

    @Test
    @DisplayName("Tạo đơn giao hàng thất bại khi thiếu địa chỉ giao hàng")
    void createDeliveryOrderFailsWhenDeliveryAddressIsEmpty() {
        OrderRequest request = new OrderRequest();
        request.setReceiverName("Nguyễn Văn A");
        request.setReceiverPhone("0987654321");
        request.setFulfillmentType(FulfillmentType.DELIVERY);
        request.setDeliveryAddress("   ");
        request.setPaymentMethod(PaymentMethod.CASH);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> orderService.createOrder("C001", request));
        assertTrue(ex.getMessage().contains("Địa chỉ giao hàng không được để trống khi chọn giao tận nơi"));
    }

    @Test
    @DisplayName("Lấy chi tiết đơn hàng thất bại với ResourceNotFoundException khi đơn không tồn tại")
    void getOrderByIdThrowsNotFoundWhenOrderDoesNotExist() {
        when(em.find(CustomerOrder.class, "NON_EXISTENT")).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> orderService.getOrderById("NON_EXISTENT"));
        verify(em).close();
    }

    @Test
    @DisplayName("Lấy chi tiết đơn hàng thành công khi đơn hàng tồn tại")
    void getOrderByIdSucceedsWhenOrderExists() {
        Customer customer = new Customer();
        customer.setId("KH01");

        CustomerOrder order = new CustomerOrder();
        order.setId("DH01");
        order.setCustomer(customer);
        order.setOrderedAt(LocalDateTime.now());
        order.setStatus(OrderStatus.PENDING_CONFIRMATION);
        order.setFulfillmentType(FulfillmentType.DELIVERY);
        order.setReceiverName("Nguyễn Văn A");
        order.setReceiverPhone("0987654321");
        order.setDeliveryAddress("123 Đường ABC");
        order.setSubtotal(new BigDecimal("100000"));
        order.setDeliveryFee(new BigDecimal("15000"));
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setTotalAmount(new BigDecimal("115000"));

        when(em.find(CustomerOrder.class, "DH01")).thenReturn(order);

        OrderResponse res = orderService.getOrderById("DH01");

        assertNotNull(res);
        assertEquals("DH01", res.getOrderId());
        assertEquals("KH01", res.getCustomerId());
        assertEquals(OrderStatus.PENDING_CONFIRMATION, res.getStatus());
        verify(em).close();
    }

    @Test
    @DisplayName("Cập nhật trạng thái thất bại với ResourceNotFoundException khi đơn không tồn tại")
    void updateOrderStatusThrowsNotFoundWhenOrderDoesNotExist() {
        when(em.find(CustomerOrder.class, "NON_EXISTENT")).thenReturn(null);

        assertThrows(ResourceNotFoundException.class,
                () -> orderService.updateOrderStatus("NON_EXISTENT", OrderStatus.PREPARING, "NV01", "Ghi chú"));
        verify(em).close();
    }

    @Test
    @DisplayName("State machine: Cập nhật thất bại khi đơn hàng đã COMPLETED")
    void updateOrderStatusFailsWhenAlreadyCompleted() {
        CustomerOrder order = new CustomerOrder();
        order.setId("DH01");
        order.setStatus(OrderStatus.COMPLETED);

        when(em.find(CustomerOrder.class, "DH01")).thenReturn(order);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> orderService.updateOrderStatus("DH01", OrderStatus.CANCELLED, "NV01", "Hủy đơn"));
        assertTrue(ex.getMessage().contains("hoàn tất"));
        verify(tx).rollback();
        verify(em).close();
    }

    @Test
    @DisplayName("State machine: Cập nhật thất bại khi đơn hàng đã CANCELLED")
    void updateOrderStatusFailsWhenAlreadyCancelled() {
        CustomerOrder order = new CustomerOrder();
        order.setId("DH01");
        order.setStatus(OrderStatus.CANCELLED);

        when(em.find(CustomerOrder.class, "DH01")).thenReturn(order);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> orderService.updateOrderStatus("DH01", OrderStatus.PREPARING, "NV01", "Chuẩn bị"));
        assertTrue(ex.getMessage().contains("hủy"));
        verify(tx).rollback();
        verify(em).close();
    }

    @Test
    @DisplayName("State machine: Chuyển từ PENDING sang PREPARING thành công")
    void updateOrderStatusFromPendingToPreparingSucceeds() {
        CustomerOrder order = new CustomerOrder();
        order.setId("DH01");
        order.setStatus(OrderStatus.PENDING_CONFIRMATION);

        when(em.find(CustomerOrder.class, "DH01")).thenReturn(order);

        orderService.updateOrderStatus("DH01", OrderStatus.PREPARING, "NV01", "Bếp nhận món");

        assertEquals(OrderStatus.PREPARING, order.getStatus());
        assertEquals("NV01", order.getAssignedEmployeeId());
        verify(em).merge(order);
        verify(tx).commit();
        verify(em).close();
    }

    @Test
    @DisplayName("State machine: Chuyển từ PREPARING sang DELIVERING thành công")
    void updateOrderStatusFromPreparingToDeliveringSucceeds() {
        CustomerOrder order = new CustomerOrder();
        order.setId("DH01");
        order.setStatus(OrderStatus.PREPARING);

        when(em.find(CustomerOrder.class, "DH01")).thenReturn(order);

        orderService.updateOrderStatus("DH01", OrderStatus.DELIVERING, "NV02", "Shipper nhận đơn");

        assertEquals(OrderStatus.DELIVERING, order.getStatus());
        verify(tx).commit();
        verify(em).close();
    }

    @Test
    @DisplayName("State machine: Chuyển từ DELIVERING sang COMPLETED thành công")
    void updateOrderStatusFromDeliveringToCompletedSucceeds() {
        CustomerOrder order = new CustomerOrder();
        order.setId("DH01");
        order.setStatus(OrderStatus.DELIVERING);

        when(em.find(CustomerOrder.class, "DH01")).thenReturn(order);

        orderService.updateOrderStatus("DH01", OrderStatus.COMPLETED, "NV02", "Giao thành công");

        assertEquals(OrderStatus.COMPLETED, order.getStatus());
        verify(tx).commit();
        verify(em).close();
    }

    @Test
    @DisplayName("State machine: Chuyển trạng thái nhảy cóc phi logic bị chặn (PENDING sang COMPLETED)")
    void updateOrderStatusInvalidTransitionBlocked() {
        CustomerOrder order = new CustomerOrder();
        order.setId("DH01");
        order.setStatus(OrderStatus.PENDING_CONFIRMATION);

        when(em.find(CustomerOrder.class, "DH01")).thenReturn(order);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> orderService.updateOrderStatus("DH01", OrderStatus.COMPLETED, "NV01", "Nhảy cóc"));
        assertTrue(ex.getMessage().contains("Không thể chuyển trạng thái"));
        verify(tx).rollback();
        verify(em).close();
    }

    @Test
    void pickupOrderCanCompleteDirectlyAfterPreparing() {
        CustomerOrder order = new CustomerOrder();
        order.setId("DH01");
        order.setStatus(OrderStatus.PREPARING);
        order.setFulfillmentType(FulfillmentType.PICKUP);
        when(em.find(CustomerOrder.class, "DH01")).thenReturn(order);

        orderService.updateOrderStatus("DH01", OrderStatus.COMPLETED, "NV01", "Khách đã nhận món");

        assertEquals(OrderStatus.COMPLETED, order.getStatus());
        verify(tx).commit();
    }

    @Test
    void pickupOrderCannotEnterDeliveryState() {
        CustomerOrder order = new CustomerOrder();
        order.setId("DH01");
        order.setStatus(OrderStatus.PREPARING);
        order.setFulfillmentType(FulfillmentType.PICKUP);
        when(em.find(CustomerOrder.class, "DH01")).thenReturn(order);

        assertThrows(BadRequestException.class,
                () -> orderService.updateOrderStatus("DH01", OrderStatus.DELIVERING, "NV01", null));

        assertEquals(OrderStatus.PREPARING, order.getStatus());
        verify(tx).rollback();
    }

    @Test
    void repeatedStatusUpdateDoesNotCreateDuplicateHistory() {
        CustomerOrder order = new CustomerOrder();
        order.setId("DH01");
        order.setStatus(OrderStatus.COMPLETED);
        when(em.find(CustomerOrder.class, "DH01")).thenReturn(order);

        orderService.updateOrderStatus("DH01", OrderStatus.COMPLETED, "NV01", null);

        verify(em, never()).persist(any());
        verify(em, never()).merge(any());
        verify(tx).commit();
    }

    @Test
    @DisplayName("Hủy đơn thất bại khi đơn không tồn tại")
    void cancelOrderThrowsNotFoundWhenOrderDoesNotExist() {
        when(em.find(CustomerOrder.class, "DH_NONE")).thenReturn(null);

        assertThrows(ResourceNotFoundException.class,
                () -> orderService.cancelOrder("DH_NONE", "KH01", "Đổi ý"));
        verify(tx, never()).commit();
        verify(em).close();
    }

    @Test
    @DisplayName("Hủy đơn thất bại khi đơn không thuộc về khách hàng")
    void cancelOrderThrowsForbiddenWhenNotOwner() {
        Customer owner = new Customer();
        owner.setId("KH01");

        CustomerOrder order = new CustomerOrder();
        order.setId("DH01");
        order.setCustomer(owner);
        order.setStatus(OrderStatus.PENDING_CONFIRMATION);

        when(em.find(CustomerOrder.class, "DH01")).thenReturn(order);

        assertThrows(ForbiddenException.class,
                () -> orderService.cancelOrder("DH01", "KH99", "Đổi ý"));
        verify(tx).rollback();
        verify(em).close();
    }

    @Test
    @DisplayName("Hủy đơn thất bại khi đơn đã hủy trước đó")
    void cancelOrderThrowsBadRequestWhenAlreadyCancelled() {
        Customer owner = new Customer();
        owner.setId("KH01");

        CustomerOrder order = new CustomerOrder();
        order.setId("DH01");
        order.setCustomer(owner);
        order.setStatus(OrderStatus.CANCELLED);

        when(em.find(CustomerOrder.class, "DH01")).thenReturn(order);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> orderService.cancelOrder("DH01", "KH01", "Đổi ý"));
        assertTrue(ex.getMessage().contains("đã được hủy"));
        verify(tx).rollback();
    }

    @Test
    @DisplayName("Hủy đơn thất bại khi đơn đã hoàn tất")
    void cancelOrderThrowsBadRequestWhenCompleted() {
        Customer owner = new Customer();
        owner.setId("KH01");

        CustomerOrder order = new CustomerOrder();
        order.setId("DH01");
        order.setCustomer(owner);
        order.setStatus(OrderStatus.COMPLETED);

        when(em.find(CustomerOrder.class, "DH01")).thenReturn(order);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> orderService.cancelOrder("DH01", "KH01", "Đổi ý"));
        assertTrue(ex.getMessage().contains("hoàn tất"));
        verify(tx).rollback();
    }

    @Test
    @DisplayName("Hủy đơn thất bại khi đơn đang giao hàng")
    void cancelOrderThrowsBadRequestWhenDelivering() {
        Customer owner = new Customer();
        owner.setId("KH01");

        CustomerOrder order = new CustomerOrder();
        order.setId("DH01");
        order.setCustomer(owner);
        order.setStatus(OrderStatus.DELIVERING);

        when(em.find(CustomerOrder.class, "DH01")).thenReturn(order);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> orderService.cancelOrder("DH01", "KH01", "Đổi ý"));
        assertTrue(ex.getMessage().contains("đang được giao"));
        verify(tx).rollback();
    }

    @Test
    @DisplayName("Hủy đơn thành công khi trạng thái là PENDING_CONFIRMATION")
    void cancelOrderSucceedsWhenPendingConfirmation() {
        Customer owner = new Customer();
        owner.setId("KH01");

        CustomerOrder order = new CustomerOrder();
        order.setId("DH01");
        order.setCustomer(owner);
        order.setStatus(OrderStatus.PENDING_CONFIRMATION);

        when(em.find(CustomerOrder.class, "DH01")).thenReturn(order);

        orderService.cancelOrder("DH01", "KH01", "Bận việc đột xuất");

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        verify(em).merge(order);
        verify(em).persist(any());
        verify(tx).commit();
        verify(em).close();
    }

    @Test
    @DisplayName("Hủy đơn thành công khi trạng thái là PREPARING")
    void cancelOrderSucceedsWhenPreparing() {
        Customer owner = new Customer();
        owner.setId("KH01");

        CustomerOrder order = new CustomerOrder();
        order.setId("DH01");
        order.setCustomer(owner);
        order.setStatus(OrderStatus.PREPARING);

        when(em.find(CustomerOrder.class, "DH01")).thenReturn(order);

        orderService.cancelOrder("DH01", "KH01", null);

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        verify(em).merge(order);
        verify(tx).commit();
        verify(em).close();
    }
}
