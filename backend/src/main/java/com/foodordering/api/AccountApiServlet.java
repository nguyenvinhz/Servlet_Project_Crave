package com.foodordering.api;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.foodordering.exception.AccountException;
import com.foodordering.utils.JsonProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Arrays;

/** Common JSON/error and method handling for the account endpoints. */
public abstract class AccountApiServlet extends BaseApiServlet {
    private static final ObjectMapper ACCOUNT_JSON = accountJsonMapper();

    private static ObjectMapper accountJsonMapper() {
        ObjectMapper mapper = JsonProvider.objectMapper().copy()
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        for (CoercionInputShape shape : new CoercionInputShape[]{CoercionInputShape.Integer,
                CoercionInputShape.Float, CoercionInputShape.Boolean}) {
            mapper.coercionConfigFor(LogicalType.Textual).setCoercion(shape, CoercionAction.Fail);
        }
        for (CoercionInputShape shape : new CoercionInputShape[]{CoercionInputShape.Integer,
                CoercionInputShape.Float, CoercionInputShape.String, CoercionInputShape.EmptyString}) {
            mapper.coercionConfigFor(LogicalType.Boolean).setCoercion(shape, CoercionAction.Fail);
        }
        return mapper;
    }

    protected abstract String allowedMethods();

    protected String allowedMethods(HttpServletRequest request) {
        return allowedMethods();
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        try {
            String allowed = allowedMethods(request);
            if (!Arrays.asList(allowed.split(", ")).contains(request.getMethod())) {
                methodNotAllowed(response, allowed);
                return;
            }
            super.service(request, response);
        } catch (Exception exception) {
            handleError(response, exception);
        }
    }

    protected <T> T readAccountJson(HttpServletRequest request, Class<T> type) throws IOException {
        String contentType = request.getContentType();
        if (contentType == null || !contentType.split(";", 2)[0].trim().equalsIgnoreCase("application/json")) {
            throw new AccountException("UNSUPPORTED_MEDIA_TYPE", "Vui lòng gửi dữ liệu dạng application/json.", 415);
        }
        T body = ACCOUNT_JSON.readerFor(type)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .readValue(request.getInputStream());
        if (body == null) {
            throw new AccountException("INVALID_REQUEST", "Nội dung yêu cầu không được để trống.", 400);
        }
        return body;
    }
}
