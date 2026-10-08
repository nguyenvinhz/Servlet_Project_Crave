package com.foodordering.utils;

import java.security.SecureRandom;
import java.util.Random;

/**
 * Tiện ích sinh mã định danh tự động theo quy chuẩn của Crave (ví dụ: GH01, CTGH01, KM01).
 */
public class IdGenerator {
    private static final Random RANDOM = new SecureRandom();

    private IdGenerator() {
    }

    /**
     * Sinh ID ngẫu nhiên có độ dài tối đa 10 ký tự, phù hợp cột VARCHAR(10) của database Crave.
     * Ví dụ: prefix="GH", độ dài số=4 => GH1234.
     */
    public static String generateId(String prefix, int numberDigits) {
        StringBuilder sb = new StringBuilder(prefix);
        int max = (int) Math.pow(10, numberDigits);
        int val = RANDOM.nextInt(max);
        String formatted = String.format("%0" + numberDigits + "d", val);
        sb.append(formatted);
        return sb.toString();
    }

    public static String generateCartId() {
        return generateId("GH", 6);
    }

    public static String generateCartItemId() {
        return generateId("CTGH", 4);
    }

    public static String generatePromotionId() {
        return generateId("KM", 6);
    }
}
