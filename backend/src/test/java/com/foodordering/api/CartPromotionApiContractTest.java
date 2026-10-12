package com.foodordering.api;

import com.foodordering.servlet.CartPageServlet;
import com.foodordering.servlet.PromotionPageServlet;
import com.foodordering.utils.JsonProvider;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServlet;
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
    Stream<DynamicTest> cartOperationsWithoutCustomerReturnCommonUnauthorizedJson() {
        return Stream.of("doGet", "doPost", "doPut", "doDelete")
                .map(operation -> DynamicTest.dynamicTest(operation + " without customer", () -> {
                    CartApiServlet servlet = new CartApiServlet();
                    HttpServletRequest request = mock(HttpServletRequest.class);
                    HttpServletResponse response = mock(HttpServletResponse.class);
                    StringWriter body = new StringWriter();
                    when(response.getWriter()).thenReturn(new PrintWriter(body));
                    var method = CartApiServlet.class.getDeclaredMethod(
                            operation, HttpServletRequest.class, HttpServletResponse.class);
                    method.setAccessible(true);
                    method.invoke(servlet, request, response);

                    verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    var json = JsonProvider.objectMapper().readTree(body.toString());
                    assertFalse(json.path("success").asBoolean());
                    assertTrue(json.path("data").isNull());
                    assertEquals("UNAUTHORIZED", json.path("error").path("code").asText());
                }));
    }

    @TestFactory
    Stream<DynamicTest> cartAndPromotionPagesForwardToTheirContractedViews() {
        return Stream.of(
                new Page(new CartPageServlet(), "/WEB-INF/views/cart/index.jsp"),
                new Page(new PromotionPageServlet(), "/WEB-INF/views/promotions/index.jsp")
        ).map(page -> DynamicTest.dynamicTest(page.view(), () -> {
            HttpServletRequest request = mock(HttpServletRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);
            RequestDispatcher dispatcher = mock(RequestDispatcher.class);
            when(request.getRequestDispatcher(page.view())).thenReturn(dispatcher);
            var method = page.servlet().getClass().getDeclaredMethod(
                    "doGet", HttpServletRequest.class, HttpServletResponse.class);
            method.setAccessible(true);
            method.invoke(page.servlet(), request, response);
            verify(dispatcher).forward(request, response);
        }));
    }

    private record Page(HttpServlet servlet, String view) {}
}
