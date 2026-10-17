package vn.edu.crave.controller.order;

import vn.edu.crave.controller.BaseServlet;
import vn.edu.crave.controller.HealthServlet;
import vn.edu.crave.controller.account.*;
import vn.edu.crave.controller.menu.*;
import vn.edu.crave.controller.cart.*;
import vn.edu.crave.controller.order.*;
import vn.edu.crave.controller.payment.*;
import vn.edu.crave.controller.admin.*;


import vn.edu.crave.model.viewmodel.ProfileResponse;
import vn.edu.crave.model.enums.AccountType;
import vn.edu.crave.model.enums.EmployeeRole;
import vn.edu.crave.model.enums.ErrorCode;
import vn.edu.crave.exception.ApiException;
import vn.edu.crave.exception.BadRequestException;
import vn.edu.crave.integration.AddressGeocoder;
import vn.edu.crave.model.service.CartService;
import vn.edu.crave.model.service.DeliveryFeeService;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeliveryServletTest {
    private final CartService carts = mock(CartService.class);
    private final AddressGeocoder geocoder = mock(AddressGeocoder.class);
    private final DeliveryServlet servlet = new DeliveryServlet(
            new DeliveryFeeService(new DeliveryFeeService.Store("Store", 10.89, 106.76)), carts, geocoder);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);
    private final HttpSession session = mock(HttpSession.class);
    private final RequestDispatcher dispatcher = mock(RequestDispatcher.class);
    private final ServletContext context = mock(ServletContext.class);
    private final Map<String, Object> attributes = new HashMap<>();

    @BeforeEach
    void setup() throws Exception {
        ServletConfig config = mock(ServletConfig.class);
        when(config.getServletContext()).thenReturn(context);
        servlet.init(config);
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("currentUser")).thenReturn(new ProfileResponse("KH01", AccountType.CUSTOMER,
                "Customer", "customer@example.com", "0901234567", null));
        when(request.getRequestDispatcher("/WEB-INF/views/order/delivery.jsp")).thenReturn(dispatcher);
        doAnswer(call -> { attributes.put(call.getArgument(0), call.getArgument(1)); return null; })
                .when(request).setAttribute(anyString(), any());
        when(carts.getVerifiedSubtotal("KH01")).thenReturn(new BigDecimal("90000"));
    }

    @Test
    void searchUsesTheFormAddressAndForwardsAllCandidatesToTheHtmlFragment() throws Exception {
        when(request.getServletPath()).thenReturn("/delivery/search");
        when(request.getParameter("address")).thenReturn("  Lê Lợi  ");
        var matches = List.of(new AddressGeocoder.Location(10.88, 106.77, "Lê Lợi, nơi A", true),
                new AddressGeocoder.Location(10.86, 106.78, "Lê Lợi, nơi B", true));
        when(geocoder.search("Lê Lợi", 10.89, 106.76)).thenReturn(matches);

        servlet.doPost(request, response);

        assertEquals(matches, attributes.get("locations"));
        verify(geocoder).search("Lê Lợi", 10.89, 106.76);
        verify(dispatcher).forward(request, response);
        verify(response).setHeader("Cache-Control", "no-store");
        verifyNoInteractions(carts);
        verify(request, never()).getInputStream();
    }

    @Test
    void reverseUsesExactFormCoordinatesAndForwardsTheSelectedLocation() throws Exception {
        when(request.getServletPath()).thenReturn("/delivery/reverse");
        coordinates("10.8800001", "106.7700002");
        var point = new AddressGeocoder.Location(10.8800001, 106.7700002, "Điểm nhận hàng", true);
        when(geocoder.reverse(10.8800001, 106.7700002)).thenReturn(point);

        servlet.doPost(request, response);

        assertEquals(point, attributes.get("location"));
        verify(dispatcher).forward(request, response);
        verifyNoInteractions(carts);
    }

    @Test
    void shippingPriceUsesTheAuthenticatedCartInsteadOfPostedSubtotalOrCustomerId() throws Exception {
        when(request.getServletPath()).thenReturn("/delivery/quote");
        coordinates("10.91", "106.76");
        when(request.getParameter("subtotal")).thenReturn("999999");
        when(request.getParameter("customerId")).thenReturn("KH99");

        servlet.doPost(request, response);

        DeliveryFeeService.Quote quote = (DeliveryFeeService.Quote) attributes.get("quote");
        assertTrue(quote.deliverable());
        assertEquals(new BigDecimal("4000"), quote.deliveryFee());
        verify(carts).getVerifiedSubtotal("KH01");
        verify(carts, never()).getVerifiedSubtotal("KH99");
        verify(dispatcher).forward(request, response);
    }

    @Test
    void qualifyingVerifiedCartReceivesFreeShipping() throws Exception {
        when(request.getServletPath()).thenReturn("/delivery/quote");
        coordinates("10.91", "106.76");
        when(carts.getVerifiedSubtotal("KH01")).thenReturn(new BigDecimal("150001"));

        servlet.doPost(request, response);

        DeliveryFeeService.Quote quote = (DeliveryFeeService.Quote) attributes.get("quote");
        assertTrue(quote.deliverable());
        assertEquals(BigDecimal.ZERO, quote.deliveryFee());
    }

    @Test
    void outOfRangePinStaysUndeliverableEvenForAHighValueCart() throws Exception {
        when(request.getServletPath()).thenReturn("/delivery/quote");
        coordinates("11.1", "106.76");
        when(carts.getVerifiedSubtotal("KH01")).thenReturn(new BigDecimal("999999"));

        servlet.doPost(request, response);

        DeliveryFeeService.Quote quote = (DeliveryFeeService.Quote) attributes.get("quote");
        assertFalse(quote.deliverable());
        assertNull(quote.deliveryFee());
    }

    @Test
    void missingOrMalformedCoordinatesReturnAnHtmlError() throws Exception {
        when(request.getServletPath()).thenReturn("/delivery/quote");
        servlet.doPost(request, response);
        assertFragmentError(400, "Vui lòng chọn vị trí nhận hàng hợp lệ trên bản đồ.");

        clearInvocations(response, dispatcher);
        coordinates("invalid", "106.77");
        servlet.doPost(request, response);
        assertFragmentError(400, "Thông tin gửi lên không hợp lệ.");
    }

    @Test
    void noAddressMatchesReturnTheSpecificGuidanceInsideTheHtmlFragment() throws Exception {
        when(request.getServletPath()).thenReturn("/delivery/search");
        when(request.getParameter("address")).thenReturn("Địa chỉ chưa có trên bản đồ");
        String message = "Chưa tìm thấy địa chỉ gần cửa hàng. Hãy nhập thêm tên phường, thành phố hoặc ghim điểm nhận trên bản đồ.";
        when(geocoder.search(anyString(), anyDouble(), anyDouble()))
                .thenThrow(new BadRequestException(ErrorCode.DELIVERY_LOCATION_REQUIRED, message));

        servlet.doPost(request, response);

        assertFragmentError(400, message);
        assertFalse(attributes.containsKey("locations"));
        verifyNoInteractions(carts);
    }

    @Test
    void geocoderConnectionFailureKeepsItsDifferentMessageAndStatusInTheFragment() throws Exception {
        when(request.getServletPath()).thenReturn("/delivery/search");
        when(request.getParameter("address")).thenReturn("99 Đường Số 9");
        String message = "Không kết nối được dịch vụ tìm địa chỉ lúc này. Bạn có thể ghim điểm nhận trên bản đồ để tiếp tục.";
        when(geocoder.search(anyString(), anyDouble(), anyDouble()))
                .thenThrow(new BadRequestException(ErrorCode.BAD_REQUEST, message));

        servlet.doPost(request, response);

        assertFragmentError(400, message);
        assertFalse(attributes.containsKey("locations"));
        verifyNoInteractions(carts);
    }

    @Test
    void errorMarkupIsPassedAsJspDataInsteadOfWrittenIntoTheResponse() throws Exception {
        when(request.getServletPath()).thenReturn("/delivery/search");
        when(request.getParameter("address")).thenReturn("99 Đường Số 9");
        String message = "Không tìm được <img src=x onerror=alert(1)> & \"địa chỉ\".";
        when(geocoder.search(anyString(), anyDouble(), anyDouble()))
                .thenThrow(new ApiException(ErrorCode.BAD_REQUEST, message, 503));

        servlet.doPost(request, response);

        assertFragmentError(503, message);
        verify(response, never()).getWriter();
        verify(response, never()).getOutputStream();
    }

    @Test
    void unexpectedFailuresAreLoggedAndReturnAGenericHtmlFragment() throws Exception {
        when(request.getServletPath()).thenReturn("/delivery/search");
        when(request.getParameter("address")).thenReturn("99 Đường Số 9");
        RuntimeException failure = new IllegalStateException("private provider configuration");
        when(geocoder.search(anyString(), anyDouble(), anyDouble())).thenThrow(failure);

        servlet.doPost(request, response);

        assertFragmentError(500, "Đã xảy ra lỗi hệ thống. Vui lòng thử lại sau.");
        verify(context).log(contains("vị trí giao hàng"), same(failure));
    }

    @Test
    void guestsAndEmployeesCannotSearchOrPriceCustomerDelivery() throws Exception {
        when(request.getServletPath()).thenReturn("/delivery/search");
        when(request.getSession(false)).thenReturn(null);

        servlet.doPost(request, response);

        assertFragmentError(401, "Vui lòng đăng nhập để tiếp tục.");
        verifyNoInteractions(carts, geocoder);

        clearInvocations(response, dispatcher);
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("currentUser")).thenReturn(new ProfileResponse("NV01", AccountType.EMPLOYEE,
                "Admin", "admin@example.com", "0901234568", EmployeeRole.ADMIN));
        servlet.doPost(request, response);

        assertFragmentError(403, "Chức năng này chỉ dành cho khách hàng.");
        verifyNoInteractions(carts, geocoder);
    }

    @Test
    void destroyingTheServletClosesTheGeocoderConnections() throws Exception {
        servlet.destroy();
        verify(geocoder).close();
    }

    @Test
    void closingFailureIsLoggedWithoutPreventingServletShutdown() throws Exception {
        IOException failure = new IOException("Connection manager close failed");
        doThrow(failure).when(geocoder).close();
        assertDoesNotThrow(servlet::destroy);
        verify(context).log(contains("kết nối tìm địa chỉ"), same(failure));
    }

    private void assertFragmentError(int status, String message) throws Exception {
        verify(response).setStatus(status);
        assertEquals(message, attributes.get("deliveryError"));
        verify(dispatcher).forward(request, response);
        verify(response, never()).sendError(anyInt(), anyString());
    }

    private void coordinates(String latitude, String longitude) {
        when(request.getParameter("latitude")).thenReturn(latitude);
        when(request.getParameter("longitude")).thenReturn(longitude);
    }
}
