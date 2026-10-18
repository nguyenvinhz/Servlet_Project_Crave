package vn.edu.crave.model.validator;

import vn.edu.crave.model.form.OrderRequest;
import vn.edu.crave.model.enums.FulfillmentType;
import vn.edu.crave.model.enums.PaymentMethod;
import vn.edu.crave.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class OrderValidatorTest {

    @Test
    void pickupClearsAnAddressLeftOverFromDeliveryAndTrimsFields() {
        OrderRequest request = validRequest();
        request.setFulfillmentType(FulfillmentType.PICKUP);
        request.setDeliveryAddress("Địa chỉ trước khi chuyển hình thức nhận món");
        request.setReceiverName("  Nguyễn Văn A  ");
        request.setReceiverPhone(" 0987654321 ");
        request.setCustomerNote("  Không cay  ");

        OrderValidator.validate(request);

        assertNull(request.getDeliveryAddress());
        assertNull(request.getDeliveryLatitude());
        assertNull(request.getDeliveryLongitude());
        assertEquals("Nguyễn Văn A", request.getReceiverName());
        assertEquals("0987654321", request.getReceiverPhone());
        assertEquals("Không cay", request.getCustomerNote());
    }

    @Test
    void missingFulfillmentOrPaymentMethodIsAClientError() {
        OrderRequest request = validRequest();
        request.setFulfillmentType(null);
        assertThrows(BadRequestException.class, () -> OrderValidator.validate(request));
        request.setFulfillmentType(FulfillmentType.PICKUP);
        request.setPaymentMethod(null);
        assertThrows(BadRequestException.class, () -> OrderValidator.validate(request));
        assertThrows(BadRequestException.class, () -> OrderValidator.validate(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "123", "098765432123456", "098 765 4321"})
    void malformedPhonesAreRejected(String phone) {
        OrderRequest request = validRequest();
        request.setReceiverPhone(phone);
        assertThrows(BadRequestException.class, () -> OrderValidator.validate(request));
    }

    @Test
    void overlongCheckoutFieldsAreRejectedBeforePersistence() {
        OrderRequest request = validRequest();
        request.setReceiverName("a".repeat(101));
        OrderRequest nameRequest = request;
        assertThrows(BadRequestException.class, () -> OrderValidator.validate(nameRequest));
        request = validRequest();
        request.setDeliveryAddress("a".repeat(256));
        OrderRequest addressRequest = request;
        assertThrows(BadRequestException.class, () -> OrderValidator.validate(addressRequest));
        request = validRequest();
        request.setCustomerNote("a".repeat(256));
        OrderRequest noteRequest = request;
        assertThrows(BadRequestException.class, () -> OrderValidator.validate(noteRequest));
    }

    private OrderRequest validRequest() {
        OrderRequest request = new OrderRequest();
        request.setFulfillmentType(FulfillmentType.DELIVERY);
        request.setPaymentMethod(PaymentMethod.CASH);
        request.setReceiverName("Nguyễn Văn A");
        request.setReceiverPhone("0987654321");
        request.setDeliveryAddress("123 Nguyễn Huệ");
        request.setDeliveryLatitude(10.8925871);
        request.setDeliveryLongitude(106.7637161);
        return request;
    }

    @Test
    void deliveryRequiresFiniteValidCoordinates() {
        OrderRequest request = validRequest();
        for (Double latitude : new Double[]{null, Double.NaN, Double.POSITIVE_INFINITY, 91.0}) {
            request.setDeliveryLatitude(latitude);
            assertThrows(BadRequestException.class, () -> OrderValidator.validate(request));
        }
        request.setDeliveryLatitude(10.89);
        request.setDeliveryLongitude(-181.0);
        assertThrows(BadRequestException.class, () -> OrderValidator.validate(request));
    }
}
