package com.foodordering.api;

import com.foodordering.utils.JsonProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CartPromotionApiContractTest {

    @TestFactory
    Stream<DynamicTest> contractedOperationsReturnCommonJsonWithoutReadingCustomerData() {
        return Stream.of(
                new Operation(new CartApiServlet(), "doGet"),
                new Operation(new CartApiServlet(), "doPost"),
                new Operation(new CartApiServlet(), "doPut"),
                new Operation(new CartApiServlet(), "doDelete"),
                new Operation(new PromotionApiServlet(), "doGet"),
                new Operation(new PromotionApiServlet(), "doPost")
        ).map(operation -> DynamicTest.dynamicTest(
                operation.servlet().getClass().getSimpleName() + "." + operation.method(), () -> {
                    HttpServletRequest request = mock(HttpServletRequest.class);
                    HttpServletResponse response = mock(HttpServletResponse.class);
                    StringWriter body = new StringWriter();
                    when(response.getWriter()).thenReturn(new PrintWriter(body));
                    var method = operation.servlet().getClass().getDeclaredMethod(
                            operation.method(), HttpServletRequest.class, HttpServletResponse.class);
                    method.setAccessible(true);
                    method.invoke(operation.servlet(), request, response);

                    verify(response).setStatus(HttpServletResponse.SC_NOT_IMPLEMENTED);
                    verify(response).setContentType("application/json");
                    var json = JsonProvider.objectMapper().readTree(body.toString());
                    assertFalse(json.path("success").asBoolean());
                    assertTrue(json.path("data").isNull());
                    assertEquals("NOT_IMPLEMENTED", json.path("error").path("code").asText());
                    verifyNoInteractions(request);
                }));
    }

    private record Operation(BaseApiServlet servlet, String method) {}
}
