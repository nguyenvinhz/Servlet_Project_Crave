package com.foodordering.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public final class JsonProvider {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private JsonProvider() {
    }

    public static ObjectMapper objectMapper() {
        return OBJECT_MAPPER;
    }
}
