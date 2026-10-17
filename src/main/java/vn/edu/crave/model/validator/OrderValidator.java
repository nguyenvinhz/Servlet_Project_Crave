package vn.edu.crave.model.validator;

import vn.edu.crave.model.form.OrderRequest;
import vn.edu.crave.model.enums.FulfillmentType;
import vn.edu.crave.exception.BadRequestException;

/** Validates checkout before any cart or order data is changed. */
public final class OrderValidator {

    private OrderValidator() {
    }

    public static void validate(OrderRequest request) {
        if (request == null) {
            throw new BadRequestException("Dữ liệu đặt hàng không được để trống");
        }
        if (request.getFulfillmentType() == null) {
            throw new BadRequestException("Vui lòng chọn hình thức nhận món");
        }
        if (request.getPaymentMethod() == null) {
            throw new BadRequestException("Vui lòng chọn phương thức thanh toán");
        }
        String name = required(request.getReceiverName(), 100, "Tên người nhận");
        String phone = required(request.getReceiverPhone(), 15, "Số điện thoại");
        if (!phone.matches("\\+?[0-9]{9,14}")) {
            throw new BadRequestException("Số điện thoại phải gồm 9–14 chữ số, có thể bắt đầu bằng dấu +");
        }
        String address = null;
        if (request.getFulfillmentType() == FulfillmentType.DELIVERY) {
            if (request.getDeliveryAddress() == null || request.getDeliveryAddress().isBlank()) {
                throw new BadRequestException("Địa chỉ giao hàng không được để trống khi chọn giao tận nơi");
            }
            address = required(request.getDeliveryAddress(), 255, "Địa chỉ giao hàng");
            vn.edu.crave.model.service.DeliveryFeeService.validateCoordinates(request.getDeliveryLatitude(), request.getDeliveryLongitude());
        } else {
            request.setDeliveryLatitude(null);
            request.setDeliveryLongitude(null);
        }
        String note = optional(request.getCustomerNote(), 255, "Ghi chú đơn hàng");
        String promotionId = optional(request.getPromotionId(), 10, "Mã khuyến mãi");

        request.setReceiverName(name);
        request.setReceiverPhone(phone);
        request.setDeliveryAddress(address);
        request.setCustomerNote(note);
        request.setPromotionId(promotionId);
    }

    private static String required(String value, int maxLength, String label) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(label + " không được để trống");
        }
        return optional(value, maxLength, label);
    }

    private static String optional(String value, int maxLength, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new BadRequestException(label + " không được vượt quá " + maxLength + " ký tự");
        }
        return trimmed;
    }
}
