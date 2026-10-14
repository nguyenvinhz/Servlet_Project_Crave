package com.foodordering.utils;

import com.foodordering.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class CoordinateParserTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "10.8527907, 106.7725584", "10.8527907 106.7725584", "(10.8527907,106.7725584)",
            "[10.8527907; 106.7725584]", "geo:10.8527907,106.7725584", "106.7725584, 10.8527907",
            "lat:10.8527907, lng:106.7725584", "Vĩ độ: 10.8527907; Kinh độ: 106.7725584",
            "10,8527907;106,7725584", "10.8527907\n106.7725584", "Nhà gần đây (10.8527907, 106.7725584)"
    })
    void normalizesCommonPastedCoordinateFormats(String text) {
        var point = CoordinateParser.parse(text).orElseThrow();
        assertEquals(10.8527907, point.latitude());
        assertEquals(106.7725584, point.longitude());
        assertEquals("10.852791, 106.772558", point.label());
    }

    @ParameterizedTest
    @ValueSource(strings = {"999,106", "91,181", "10.85,106.77,123", "10,85,106,77", "10.8;", "-999,106"})
    void malformedOrOutOfRangeCoordinatesCannotBecomeAnAddressSearch(String text) {
        assertThrows(BadRequestException.class, () -> CoordinateParser.parse(text));
    }

    @Test
    void streetAddressesAreNotMistakenForCoordinates() {
        assertTrue(CoordinateParser.parse("12/3 Nguyễn Trãi, Q.1, TP.HCM").isEmpty());
        assertTrue(CoordinateParser.parse("Đường 3 Tháng 2").isEmpty());
        assertTrue(CoordinateParser.parse(null).isEmpty());
        assertEquals(new CoordinateParser.Point(-10, -20), CoordinateParser.parse("-10,-20").orElseThrow());
    }
}
