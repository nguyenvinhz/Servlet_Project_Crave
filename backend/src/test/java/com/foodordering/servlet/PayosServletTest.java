package com.foodordering.servlet;

import com.foodordering.dto.ProfileResponse;
import com.foodordering.enums.AccountType;
import com.foodordering.enums.EmployeeRole;
import com.foodordering.exception.AccountException;
import com.foodordering.gateway.PayosClient;
import com.foodordering.security.SessionAuth;
import com.foodordering.service.PayosPaymentService;
import com.foodordering.gateway.PayosJson;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PayosServletTest {
    private static final ProfileResponse CUSTOMER = new ProfileResponse("KH01", AccountType.CUSTOMER,
            "Customer", "customer@example.com", "0901234567", null);

    @Test
    void paymentRoutesRequireCustomerBeforeCallingGatewayOrReadingOrder() throws Exception {
        PayosPaymentService payments = mock(PayosPaymentService.class);
        PayosPaymentServlet servlet = new PayosPaymentServlet(payments);
        for (ProfileResponse principal : new ProfileResponse[]{null, new ProfileResponse("NV01", AccountType.EMPLOYEE,
                "Admin", "admin@example.com", "0901234568", EmployeeRole.ADMIN)}) {
            Exchange exchange = exchange(principal, "/payos/create", "");
            servlet.doPost(exchange.request, exchange.response);
            verify(exchange.response).sendError(eq(principal == null ? 401 : 403), anyString());
            verify(exchange.request, never()).getSession(true);
            verify(exchange.request, never()).getSession();
        }
        verifyNoInteractions(payments);
    }

    @Test
    void createUsesCustomerFromSessionAndOrderFromForm() throws Exception {
        PayosPaymentService payments = mock(PayosPaymentService.class);
        PayosPaymentServlet servlet = new PayosPaymentServlet(payments);
        Exchange exchange = exchange(CUSTOMER, "/payos/create", "");
        when(exchange.request.getParameter("customerId")).thenReturn("KH99");
        when(exchange.request.getParameter("amount")).thenReturn("1");
        servlet.doPost(exchange.request, exchange.response);
        verify(payments).create("DH01", "KH01");
        verify(exchange.request, never()).getInputStream();
        verify(exchange.response).sendRedirect("/crave/orders/DH01");
        assertTrue(exchange.text.toString().isEmpty());
    }

    @Test
    void invalidPathsAndUnsupportedQrMutationsDoNotCreateLinks() throws Exception {
        PayosPaymentService payments = mock(PayosPaymentService.class);
        PayosPaymentServlet servlet = new PayosPaymentServlet(payments);
        for (String path : new String[]{null, "/payos/extra", "/payos/create", "/payos/qr/extra"}) {
            Exchange exchange = exchange(CUSTOMER, path, "");
            servlet.doGet(exchange.request, exchange.response);
            verify(exchange.response).sendError(405);
        }
        Exchange mutation = exchange(CUSTOMER, "/payos/qr", "");
        servlet.doPost(mutation.request, mutation.response);
        verify(mutation.response).sendError(405);
        verifyNoInteractions(payments);
    }

    @Test
    void qrPngDecodesToExactServerPayload() throws Exception {
        PayosPaymentService payments = mock(PayosPaymentService.class);
        String payload = "00020101021238570010A000000727012700069704220113113366668888020899998888530370454061000005802VN62230819THANH TOAN DON HANG6304BE36";
        when(payments.qrPayload("DH01", "KH01")).thenReturn(payload);
        Exchange exchange = exchange(CUSTOMER, "/payos/qr", "");
        new PayosPaymentServlet(payments).doGet(exchange.request, exchange.response);
        verify(exchange.response).setContentType("image/png");
        verify(exchange.response).setHeader("Cache-Control", "no-store");
        verify(exchange.response).setHeader("X-Content-Type-Options", "nosniff");
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(exchange.binary.toByteArray()));
        assertEquals(320, image.getWidth());
        LuminanceSource luminance = new LuminanceSource(image.getWidth(), image.getHeight()) {
            @Override public byte[] getRow(int y, byte[] row) {
                byte[] pixels = row == null || row.length < getWidth() ? new byte[getWidth()] : row;
                for (int x = 0; x < getWidth(); x++) pixels[x] = (byte) (image.getRGB(x, y) & 0xff);
                return pixels;
            }
            @Override public byte[] getMatrix() {
                byte[] pixels = new byte[getWidth() * getHeight()];
                for (int y = 0; y < getHeight(); y++) System.arraycopy(getRow(y, null), 0, pixels, y * getWidth(), getWidth());
                return pixels;
            }
        };
        assertEquals(payload, new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(luminance))).getText());
    }

    @Test
    void anotherCustomersOrderCannotProduceQr() throws Exception {
        PayosPaymentService payments = mock(PayosPaymentService.class);
        when(payments.qrPayload("DH99", "KH01")).thenThrow(new AccountException("FORBIDDEN", "Không có quyền.", 403));
        Exchange exchange = exchange(CUSTOMER, "/payos/qr", "");
        when(exchange.request.getParameter("orderId")).thenReturn("DH99");
        new PayosPaymentServlet(payments).doGet(exchange.request, exchange.response);
        verify(exchange.response).sendError(eq(403), anyString());
        assertEquals(0, exchange.binary.size());
    }

    @Test
    void statusForwardsPaymentFragmentWithSessionCustomerAndServerState() throws Exception {
        PayosPaymentService payments = mock(PayosPaymentService.class);
        var payment = new PayosPaymentService.PaymentLinkResponse("DH01", "TT01", 123, 10000,
                "VND", "PENDING", "PENDING", "https://pay.example/123", "qr", "123", "CRAVE", "CR123", "970422");
        when(payments.status("DH01", "KH01")).thenReturn(payment);
        Exchange exchange = exchange(CUSTOMER, "/payos/status", "");
        when(exchange.request.getParameter("customerId")).thenReturn("KH99");
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(exchange.request.getRequestDispatcher("/WEB-INF/views/order/payos-status.jsp")).thenReturn(dispatcher);
        new PayosPaymentServlet(payments).doGet(exchange.request, exchange.response);
        verify(payments).status("DH01", "KH01");
        verify(exchange.request).setAttribute("payos", payment);
        verify(dispatcher).forward(exchange.request, exchange.response);
        verify(exchange.response).setHeader("Cache-Control", "no-store");
        assertTrue(exchange.text.toString().isEmpty());
    }

    @Test
    void qrAndStatusRejectAnonymousAndEmployeeUsers() throws Exception {
        PayosPaymentService payments = mock(PayosPaymentService.class);
        for (ProfileResponse user : new ProfileResponse[]{null, new ProfileResponse("NV01", AccountType.EMPLOYEE,
                "Admin", "admin@example.com", "0901234568", EmployeeRole.ADMIN)}) {
            for (String path : new String[]{"/payos/qr", "/payos/status"}) {
                Exchange exchange = exchange(user, path, "");
                new PayosPaymentServlet(payments).doGet(exchange.request, exchange.response);
                verify(exchange.response).sendError(eq(user == null ? 401 : 403), anyString());
                assertEquals(0, exchange.binary.size());
            }
        }
        verifyNoInteractions(payments);
    }

    @Test
    void webhookPassesOnlyVerifiedDataToPaymentService() throws Exception {
        PayosClient client = mock(PayosClient.class);
        PayosPaymentService payments = mock(PayosPaymentService.class);
        String payload = "{\"data\":{\"orderCode\":123},\"signature\":\"signed\"}";
        var verified = PayosJson.objectMapper().readTree("{\"orderCode\":123,\"amount\":10000}");
        when(client.verifyWebhook(any())).thenReturn(verified);
        Exchange exchange = exchange(null, null, payload);
        new PayosWebhookServlet(client, payments).doPost(exchange.request, exchange.response);
        verify(payments).webhook(verified);
        verify(exchange.response).setStatus(200);
        assertTrue(PayosJson.objectMapper().readTree(exchange.text.toString()).path("success").asBoolean());
        verify(exchange.response).setContentType("application/json");
        verify(exchange.request, never()).getSession(anyBoolean());
    }

    @Test
    void malformedWebhookNeverReachesVerifierOrPaymentService() throws Exception {
        PayosClient client = mock(PayosClient.class);
        PayosPaymentService payments = mock(PayosPaymentService.class);
        Exchange exchange = exchange(null, null, "{invalid");
        new PayosWebhookServlet(client, payments).doPost(exchange.request, exchange.response);
        verify(exchange.response).setStatus(400);
        assertFalse(PayosJson.objectMapper().readTree(exchange.text.toString()).path("success").asBoolean());
        verifyNoInteractions(client, payments);
    }

    @Test
    void temporaryWebhookFailureReturnsRetryableResponseWithoutInternalDetails() throws Exception {
        PayosClient client = mock(PayosClient.class);
        PayosPaymentService payments = mock(PayosPaymentService.class);
        var verified = PayosJson.objectMapper().readTree("{\"orderCode\":123,\"amount\":10000}");
        when(client.verifyWebhook(any())).thenReturn(verified);
        doThrow(new IllegalStateException("jdbc private-host secret")).when(payments).webhook(verified);
        PayosWebhookServlet servlet = new PayosWebhookServlet(client, payments);
        ServletConfig config = mock(ServletConfig.class);
        when(config.getServletContext()).thenReturn(mock(ServletContext.class));
        servlet.init(config);
        Exchange exchange = exchange(null, null, "{}");
        servlet.doPost(exchange.request, exchange.response);
        verify(exchange.response).setStatus(503);
        assertEquals("{\"success\":false}", exchange.text.toString());
        assertFalse(exchange.text.toString().contains("secret"));
    }

    @Test
    void invalidSignaturesAndOversizedWebhookNeverReachPaymentService() throws Exception {
        PayosClient client = mock(PayosClient.class);
        PayosPaymentService payments = mock(PayosPaymentService.class);
        when(client.verifyWebhook(any())).thenThrow(new AccountException("INVALID_SIGNATURE", "Chữ ký không hợp lệ.", 400));
        Exchange tampered = exchange(null, null, "{}");
        new PayosWebhookServlet(client, payments).doPost(tampered.request, tampered.response);
        verify(tampered.response).setStatus(400);
        Exchange oversized = exchange(null, null, " ".repeat(65537));
        new PayosWebhookServlet(client, payments).doPost(oversized.request, oversized.response);
        verify(oversized.response).setStatus(413);
        verify(client, times(1)).verifyWebhook(any());
        verifyNoInteractions(payments);
    }

    private static Exchange exchange(ProfileResponse principal, String path, String body) throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpSession session = principal == null ? null : mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        when(request.getSession()).thenReturn(session);
        if (session != null) when(session.getAttribute(SessionAuth.CURRENT_USER)).thenReturn(principal);
        when(request.getServletPath()).thenReturn(path);
        when(request.getContextPath()).thenReturn("/crave");
        when(request.getParameter("orderId")).thenReturn("DH01");
        ByteArrayInputStream input = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        when(request.getInputStream()).thenReturn(new ServletInputStream() {
            @Override public boolean isFinished() { return input.available() == 0; }
            @Override public boolean isReady() { return true; }
            @Override public void setReadListener(ReadListener listener) {}
            @Override public int read() { return input.read(); }
        });
        StringWriter text = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(text));
        ByteArrayOutputStream binary = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(new ServletOutputStream() {
            @Override public boolean isReady() { return true; }
            @Override public void setWriteListener(WriteListener listener) {}
            @Override public void write(int value) { binary.write(value); }
        });
        return new Exchange(request, response, text, binary);
    }

    private record Exchange(HttpServletRequest request, HttpServletResponse response,
                            StringWriter text, ByteArrayOutputStream binary) {}
}
