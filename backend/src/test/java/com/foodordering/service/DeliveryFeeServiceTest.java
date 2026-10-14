package com.foodordering.service;

import com.foodordering.enums.ErrorCode;
import com.foodordering.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class DeliveryFeeServiceTest {
    private final DeliveryFeeService fees = new DeliveryFeeService(new DeliveryFeeService.Store("Fixture store", 10.89, 106.76));

    @Test
    void onlyOrdersStrictlyAbove150kGetFreeShippingAndRadiusStillApplies() {
        assertEquals(new BigDecimal("4000"), fees.quote(10.91, 106.76, new BigDecimal("150000")).deliveryFee());
        assertEquals(BigDecimal.ZERO, fees.quote(10.91, 106.76, new BigDecimal("150001")).deliveryFee());
        assertEquals(BigDecimal.ZERO, fees.requireDeliverable(10.91, 106.76, new BigDecimal("200000")).deliveryFee());
        assertFalse(fees.quote(11.1, 106.76, new BigDecimal("999999")).deliverable());
        assertEquals(ErrorCode.DELIVERY_OUT_OF_RANGE, assertThrows(BadRequestException.class,
                () -> fees.requireDeliverable(11.1, 106.76, new BigDecimal("999999"))).getErrorCode());
    }

    @Test
    void everyBoundaryHasOneUnambiguousBandAndTenKmIsIncluded() {
        double[] distances = {0, 0.999999, 1, 2.999999, 3, 4.999999, 5, 6.999999, 7, 9.999999, 10};
        int[] expected = {0, 0, 4000, 4000, 7000, 7000, 12000, 12000, 15000, 15000, 15000};
        for (int i = 0; i < distances.length; i++) {
            assertEquals(0, BigDecimal.valueOf(expected[i]).compareTo(DeliveryFeeService.feeForDistance(distances[i])));
        }
        assertNull(DeliveryFeeService.feeForDistance(10.000001));
        assertNull(DeliveryFeeService.feeForDistance(50));
        for (double invalid : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> DeliveryFeeService.feeForDistance(invalid));
        }
    }

    @Test
    void serverUsesCoordinatesAndRejectsOutsideRadius() {
        assertEquals(0, fees.quote(10.89, 106.76).distanceKm().compareTo(BigDecimal.ZERO));
        assertEquals(BigDecimal.ZERO, fees.quote(10.89, 106.76).deliveryFee());
        assertEquals(new BigDecimal("4000"), fees.quote(10.91, 106.76).deliveryFee());
        var outside = fees.quote(11.0, 106.76);
        assertFalse(outside.deliverable());
        assertNull(outside.deliveryFee());
        BadRequestException failure = assertThrows(BadRequestException.class, () -> fees.requireDeliverable(11.0, 106.76));
        assertEquals(ErrorCode.DELIVERY_OUT_OF_RANGE, failure.getErrorCode());
    }

    @Test
    void validatesOriginDestinationAndDoesNotRoundIntoLowerFeeBand() {
        for (Double invalid : new Double[]{null, Double.NaN, 91.0}) {
            assertThrows(BadRequestException.class, () -> fees.quote(invalid, 106.76));
        }
        DeliveryFeeService unconfigured = new DeliveryFeeService(new DeliveryFeeService.Store("", null, null));
        assertFalse(unconfigured.policy().configured());
        assertEquals(ErrorCode.DELIVERY_NOT_CONFIGURED,
                assertThrows(BadRequestException.class, () -> unconfigured.quote(10.89, 106.76)).getErrorCode());
        double justAbove10 = 10.89 + Math.toDegrees(10.00001 / 6371.0088);
        var quote = fees.quote(justAbove10, 106.76);
        assertEquals(new BigDecimal("10.000"), quote.distanceKm());
        assertFalse(quote.deliverable(), "The radius check must use unrounded distance");
    }
}
