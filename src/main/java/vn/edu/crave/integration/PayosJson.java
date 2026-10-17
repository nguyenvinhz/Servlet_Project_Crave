package vn.edu.crave.integration;

import com.fasterxml.jackson.databind.ObjectMapper;

/** JSON dành riêng cho giao thức bắt buộc của payOS. */
public final class PayosJson {
    private static final ObjectMapper JSON = new ObjectMapper();
    private PayosJson() { }
    public static ObjectMapper objectMapper() { return JSON; }
}
