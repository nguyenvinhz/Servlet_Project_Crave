package com.foodordering.api;

import com.foodordering.dto.ProfileResponse;
import com.foodordering.enums.AccountType;
import com.foodordering.service.DeliveryFeeService;
import com.foodordering.service.CartService;
import com.foodordering.gateway.AddressGeocoder;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import com.foodordering.utils.JsonProvider;
import jakarta.servlet.http.*;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import org.junit.jupiter.api.Test;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeliveryApiServletTest {
    private final CartService carts = mock(CartService.class);
    private final AddressGeocoder geocoder = mock(AddressGeocoder.class);
    private final DeliveryApiServlet servlet = new DeliveryApiServlet(
            new DeliveryFeeService(new DeliveryFeeService.Store("Store", 10.89, 106.76)), carts, geocoder);

    @BeforeEach void cartSubtotal() {
        when(carts.getVerifiedSubtotal("KH01")).thenReturn(new BigDecimal("90000"));
    }

    @Test
    void searchReturnsAllCandidatesWithStoreFocus() throws Exception {
        when(geocoder.search("Lê Lợi", 10.89, 106.76)).thenReturn(java.util.List.of(
                new AddressGeocoder.Location(10.88, 106.77, "Lê Lợi", true),
                new AddressGeocoder.Location(10.86, 106.78, "Lê Lợi, nơi khác", true)));
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter output = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(output));
        servlet.doPost(request("/search", "{\"address\":\"Lê Lợi\"}", true), resp);
        verify(resp).setStatus(200);
        assertEquals(2, JsonProvider.objectMapper().readTree(output.toString()).path("data").size());
    }

    @Test
    void reverseReturnsSelectedCoordinatesAndRequiresCustomerSession() throws Exception {
        when(geocoder.reverse(10.88, 106.77)).thenReturn(new AddressGeocoder.Location(10.88, 106.77, "Đường nhận hàng", true));
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter output = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(output));
        servlet.doPost(request("/reverse", "{\"latitude\":10.88,\"longitude\":106.77}", true), resp);
        verify(resp).setStatus(200);
        assertEquals(10.88, JsonProvider.objectMapper().readTree(output.toString()).path("data").path("latitude").asDouble());
        for (String path : new String[]{"/search", "/reverse"}) {
            HttpServletResponse unauthorized = mock(HttpServletResponse.class);
            when(unauthorized.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
            servlet.doPost(request(path, "{}", false), unauthorized);
            verify(unauthorized).setStatus(401);
        }
    }

    @Test
    void freeShippingUsesAuthenticatedCustomersVerifiedCart() throws Exception {
        when(carts.getVerifiedSubtotal("KH01")).thenReturn(new BigDecimal("150001"));
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter output = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(output));
        servlet.doPost(request("/quote", "{\"latitude\":10.91,\"longitude\":106.76}", true), resp);
        verify(resp).setStatus(200);
        assertEquals(0, JsonProvider.objectMapper().readTree(output.toString()).path("data").path("deliveryFee").asInt());
        verify(carts).getVerifiedSubtotal("KH01");
    }

    @Test
    void resolvesAddressForMapWithoutDisclosingPricingRules() throws Exception {
        when(geocoder.resolve("1 Võ Văn Ngân")).thenReturn(new AddressGeocoder.Location(10.8528, 106.7726, "1 Võ Văn Ngân", false));
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter output = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(output));
        servlet.doPost(request("/resolve", "{\"address\":\"1 Võ Văn Ngân\"}", true), resp);
        verify(resp).setStatus(200);
        var data = JsonProvider.objectMapper().readTree(output.toString()).path("data");
        assertEquals(10.8528, data.path("latitude").asDouble());
        assertFalse(data.has("bands"));
    }

    private HttpServletRequest request(String path, String body, boolean authenticated) throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getPathInfo()).thenReturn(path);
        when(req.getReader()).thenReturn(new BufferedReader(new StringReader(body)));
        ByteArrayInputStream bytes = new ByteArrayInputStream(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(req.getInputStream()).thenReturn(new ServletInputStream() {
            @Override public int read() { return bytes.read(); }
            @Override public boolean isFinished() { return bytes.available() == 0; }
            @Override public boolean isReady() { return true; }
            @Override public void setReadListener(ReadListener listener) { }
        });
        if (authenticated) {
            HttpSession session = mock(HttpSession.class);
            when(req.getSession(false)).thenReturn(session);
            when(session.getAttribute("currentUser")).thenReturn(new ProfileResponse("KH01", AccountType.CUSTOMER,
                    "Customer", "customer@example.com", "0901234567", null));
        }
        return req;
    }

    @Test
    void endpointsRequireAnAuthenticatedCustomer() throws Exception {
        for (String method : new String[]{"GET", "POST"}) {
            HttpServletResponse resp = mock(HttpServletResponse.class);
            when(resp.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
            if (method.equals("GET")) servlet.doGet(request("/policy", "", false), resp);
            else servlet.doPost(request("/quote", "{}", false), resp);
            verify(resp).setStatus(401);
        }
    }

    @Test
    void returnsPolicyAndServerCalculatedQuoteAndRejectsMissingCoordinates() throws Exception {
        HttpServletResponse policyResponse = mock(HttpServletResponse.class);
        StringWriter policyBody = new StringWriter();
        when(policyResponse.getWriter()).thenReturn(new PrintWriter(policyBody));
        servlet.doGet(request("/policy", "", true), policyResponse);
        var policy = JsonProvider.objectMapper().readTree(policyBody.toString()).path("data");
        assertTrue(policy.path("configured").asBoolean());
        assertFalse(policy.has("bands"), "Customer API must not publish the pricing table");
        assertFalse(policy.has("maxDistanceKm"));
        assertFalse(policy.has("distanceMethod"));
        for (String body : new String[]{"{\"latitude\":10.91,\"longitude\":106.76}",
                "{\"latitude\":10.89,\"longitude\":106.76}", "{\"latitude\":11.1,\"longitude\":106.76}", "{}"}) {
            HttpServletResponse resp = mock(HttpServletResponse.class);
            StringWriter output = new StringWriter();
            when(resp.getWriter()).thenReturn(new PrintWriter(output));
            servlet.doPost(request("/quote", body, true), resp);
            var json = JsonProvider.objectMapper().readTree(output.toString());
            if (body.equals("{}")) {
                verify(resp).setStatus(400);
                assertEquals("DELIVERY_LOCATION_REQUIRED", json.path("error").path("code").asText());
            } else {
                verify(resp).setStatus(200);
                if (body.contains("11.1")) {
                    assertFalse(json.path("data").path("deliverable").asBoolean());
                    assertTrue(json.path("data").path("deliveryFee").isNull());
                } else {
                    assertTrue(json.path("data").path("deliverable").asBoolean());
                    assertEquals(body.contains("10.91") ? 4000 : 0, json.path("data").path("deliveryFee").asInt());
                }
                assertFalse(json.path("data").has("distanceKm"));
                assertFalse(json.path("data").has("bands"));
                assertFalse(json.path("data").path("message").asText().contains("bán kính"));
                assertFalse(json.path("data").path("message").asText().contains("10 km"));
            }
        }
    }
}
