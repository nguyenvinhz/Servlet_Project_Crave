package com.foodordering.utils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.Reader;

public class JsonUtils {

    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();

    private JsonUtils() {}

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        try {
            Class<?> jtmClass = Class.forName("com.fasterxml.jackson.datatype.jsr310.JavaTimeModule");
            com.fasterxml.jackson.databind.Module jtm = (com.fasterxml.jackson.databind.Module) jtmClass.getDeclaredConstructor().newInstance();
            mapper.registerModule(jtm);
        } catch (Throwable ignored) {
        }
        return mapper;
    }

    public static <T> T toDTO(String json, Class<T> clazz) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi chuyển đổi JSON sang " + clazz.getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    public static <T> T toEntity(String json, Class<T> clazz) {
        return toDTO(json, clazz);
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        return toDTO(json, clazz);
    }

    public static String toJson(Object obj) {
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi chuyển đổi đối tượng sang JSON: " + e.getMessage(), e);
        }
    }
}
