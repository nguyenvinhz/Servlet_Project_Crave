package vn.edu.crave.model.service;

import vn.edu.crave.model.enums.ErrorCode;
import vn.edu.crave.exception.BadRequestException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** One shared store origin; distances are geographic radii, not driving routes. */
public class DeliveryFeeService {
    public record Band(double fromKm, double toKm, BigDecimal baseFee, BigDecimal ratePerKm) {}
    public record Store(String address, Double latitude, Double longitude) {
        public boolean configured() {
            return address != null && !address.isBlank() && validCoordinates(latitude, longitude);
        }
    }
    public record Policy(Store store, boolean configured, double maxDistanceKm, List<Band> bands, String distanceMethod) {}
    public record Quote(BigDecimal distanceKm, BigDecimal deliveryFee, boolean deliverable, String message) {}
    private static final List<Band> BANDS = List.of(
            new Band(0, 1, BigDecimal.ZERO, BigDecimal.ZERO),
            new Band(1, 3, new BigDecimal("4000"), BigDecimal.ZERO),
            new Band(3, 5, new BigDecimal("7000"), BigDecimal.ZERO),
            new Band(5, 7, new BigDecimal("7000"), new BigDecimal("2500")),
            new Band(7, 10, new BigDecimal("12000"), new BigDecimal("3000")));
    private final Store store;

    public DeliveryFeeService() {
        // Demonstration origin on Đường số 9, Nhị Đồng 2; override with the exact entrance pin for real operation.
        this(new Store(setting("CRAVE_STORE_ADDRESS", "99 Đường Số 9, Dĩ An, Hồ Chí Minh 825030, Việt Nam"),
                number("CRAVE_STORE_LATITUDE", "10.8925871"), number("CRAVE_STORE_LONGITUDE", "106.7637161")));
    }

    public DeliveryFeeService(Store store) { this.store = store; }

    public Policy policy() { return new Policy(store, store.configured(), 10, BANDS, "STRAIGHT_LINE"); }

    public Quote quote(Double latitude, Double longitude) {
        return quote(latitude, longitude, BigDecimal.ZERO);
    }

    public Quote quote(Double latitude, Double longitude, BigDecimal subtotal) {
        if (!store.configured()) {
            throw new BadRequestException(ErrorCode.DELIVERY_NOT_CONFIGURED,
                    "Cửa hàng chưa thiết lập vị trí giao hàng. Bạn có thể chọn lấy tại quán.");
        }
        validateCoordinates(latitude, longitude);
        double distance = distanceKm(store.latitude(), store.longitude(), latitude, longitude);
        BigDecimal fee = feeForDistance(distance);
        boolean freeShipping = fee != null && subtotal != null && subtotal.compareTo(new BigDecimal("150000")) > 0;
        if (freeShipping) fee = BigDecimal.ZERO;
        return new Quote(BigDecimal.valueOf(distance).setScale(3, RoundingMode.HALF_UP), fee, fee != null,
                freeShipping ? "Đơn hàng được miễn phí giao hàng."
                        : fee != null ? "Đã xác nhận điểm nhận hàng."
                        : "Khu vực này hiện chưa được hỗ trợ giao hàng. Bạn có thể chọn lấy tại quán.");
    }

    public Quote requireDeliverable(Double latitude, Double longitude) {
        return requireDeliverable(latitude, longitude, BigDecimal.ZERO);
    }

    public Quote requireDeliverable(Double latitude, Double longitude, BigDecimal subtotal) {
        Quote quote = quote(latitude, longitude, subtotal);
        if (!quote.deliverable()) throw new BadRequestException(ErrorCode.DELIVERY_OUT_OF_RANGE, quote.message());
        return quote;
    }

    public static BigDecimal feeForDistance(double distance) {
        if (!Double.isFinite(distance) || distance < 0) throw new IllegalArgumentException("Invalid distance");
        // [0,1), [1,3), [3,5), [5,7), [7,10]. Compare before display rounding.
        for (Band band : BANDS) {
            if (distance < band.toKm() || (band.toKm() == 10 && distance <= 10)) {
                // Accumulate from the band's base fare using only the distance beyond its lower bound.
                return BigDecimal.valueOf(distance).subtract(BigDecimal.valueOf(band.fromKm()))
                        .multiply(band.ratePerKm()).add(band.baseFee()).setScale(0, RoundingMode.HALF_UP);
            }
        }
        return null;
    }

    public static void validateCoordinates(Double latitude, Double longitude) {
        if (!validCoordinates(latitude, longitude)) {
            throw new BadRequestException(ErrorCode.DELIVERY_LOCATION_REQUIRED,
                    "Vui lòng chọn vị trí nhận hàng hợp lệ trên bản đồ.");
        }
    }

    private static boolean validCoordinates(Double latitude, Double longitude) {
        return latitude != null && longitude != null && Double.isFinite(latitude) && Double.isFinite(longitude)
                && latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
    }

    public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        validateCoordinates(lat1, lon1);
        validateCoordinates(lat2, lon2);
        double latDelta = Math.toRadians(lat2 - lat1), lonDelta = Math.toRadians(lon2 - lon1);
        double a = Math.pow(Math.sin(latDelta / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(lonDelta / 2), 2);
        return 6371.0088 * 2 * Math.asin(Math.sqrt(Math.min(1, Math.max(0, a))));
    }

    private static String setting(String key, String fallback) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) value = System.getProperty(key);
        return value == null ? fallback : value.trim();
    }

    private static Double number(String key, String fallback) {
        try { return Double.valueOf(setting(key, fallback)); }
        catch (NumberFormatException ignored) { return null; }
    }
}
