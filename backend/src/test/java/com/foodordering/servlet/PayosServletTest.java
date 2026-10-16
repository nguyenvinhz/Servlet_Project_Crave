package com.foodordering.servlet;

import com.foodordering.bean.ProfileBean;
import com.foodordering.enums.AccountType;
import com.foodordering.enums.EmployeeRole;
import com.foodordering.exception.AccountException;
import com.foodordering.integration.PayosClient;
import com.foodordering.security.SessionAuth;
import com.foodordering.service.PayosPaymentService;
import com.foodordering.util.IntegrationJson;
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
    private static final ProfileBean CUSTOMER = new ProfileBean("KH01", AccountType.CUSTOMER,
            "Customer", "customer@example.com", "0901234567", null);

    @Test
    void paymentRoutesRequireCustomerBeforeCallingGatewayOrReadingOrder() throws Exception {
        PayosPaymentService payments = mock(PayosPaymentService.class);
        PayosPaymentServlet servlet = new PayosPaymentServlet(payments);
        for (ProfileBean principal : new ProfileBean[]{null, new ProfileBean("NV01", AccountType.EMPLOYEE,
                "Admin", "admin@example.com", "0901234568", EmployeeRole.ADMIN)}) {
            Exchange exchange = exchange(principal, "/DH01", "");
            servlet.doPost(exchange.request, exchange.response);
            verify(exchange.response).sendError(eq(principal == null ? 401 : 403), anyString());
            verify(exchange.request, never()).getSession(true);
        }
        verifyNoInteractions(payments);
    }

    @Test
    void createUsesCustomerFromSessionAndOrderFromPath() throws Exception {
        PayosPaymentService payments = mock(PayosPaymentService.class);
        PayosPaymentServlet servlet = new PayosPaymentServlet(payments);
        Exchange exchange = exchange(CUSTOMER, "/DH01", "{\"amount\":1,\"customerId\":\"KH99\"}");
        when(exchange.request.getParameter("customerId")).thenReturn("KH99");
        servlet.doPost(exchange.request, exchange.response);
        verify(payments).create("DH01", "KH01");
        verify(exchange.request, never()).getInputStream();
        verify(exchange.response).sendRedirect("/crave/orders/DH01");
    }

    @Test
    void invalidPathsAndUnsupportedQrMutationsDoNotCreateLinks() throws Exception {
        PayosPaymentService payments = mock(PayosPaymentService.class);
        PayosPaymentServlet servlet = new PayosPaymentServlet(payments);
        for (String path : new String[]{null, "/DH01/extra", "/../DH01", "/DH01/qr/extra"}) {
            Exchange exchange = exchange(CUSTOMER, path, "");
            servlet.doGet(exchange.request, exchange.response);
            verify(exchange.response).sendError(eq(404), anyString());
        }
        Exchange mutation = exchange(CUSTOMER, "/DH01/qr", "");
        servlet.doPost(mutation.request, mutation.response);
        verify(mutation.response).sendError(405);
        verifyNoInteractions(payments);
    }

    @Test
    void qrPngDecodesToExactServerPayload() throws Exception {
        PayosPaymentService payments = mock(PayosPaymentService.class);
        String payload = "00020101021238570010A000000727012700069704220113113366668888020899998888530370454061000005802VN62230819THANH TOAN DON HANG6304BE36";
        when(payments.qrPayload("DH01", "KH01")).thenReturn(payload);
        Exchange exchange = exchange(CUSTOMER, "/DH01/qr", "");
        new PayosPaymentServlet(payments).doGet(exchange.request, exchange.response);
        verify(exchange.response).setContentType("image/png");
        verify(exchange.response).setHeader("Cache-Control", "no-store");
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
        Exchange exchange = exchange(CUSTOMER, "/DH99/qr", "");
        new PayosPaymentServlet(payments).doGet(exchange.request, exchange.response);
        verify(exchange.response).sendError(eq(403), anyString());
        assertEquals(0, exchange.binary.size());
    }

    @Test
    void webhookPassesOnlyVerifiedDataToPaymentService() throws Exception {
        PayosClient client = mock(PayosClient.class);
        PayosPaymentService payments = mock(PayosPaymentService.class);
        String payload = "{\"data\":{\"orderCode\":123},\"signature\":\"signed\"}";
        var verified = IntegrationJson.objectMapper().readTree("{\"orderCode\":123,\"amount\":10000}");
        when(client.verifyWebhook(any())).thenReturn(verified);
        Exchange exchange = exchange(null, null, payload);
        new PayosWebhookServlet(client, payments).doPost(exchange.request, exchange.response);
        verify(payments).webhook(verified);
        verify(exchange.response).setStatus(200);
        verify(exchange.request, never()).getSession(anyBoolean());
    }

    @Test
    void invalidSignaturesAndOversizedWebhookNeverReachPaymentService() throws Exception {
        PayosClient client = mock(PayosClient.class);
        PayosPaymentService payments = mock(PayosPaymentService.class);
        when(client.verifyWebhook(any())).thenThrow(new AccountException("INVALID_SIGNATURE", "Chữ ký không hợp lệ.", 400));
        Exchange tampered = exchange(null, null, "{}");
        new PayosWebhookServlet(client, payments).doPost(tampered.request, tampered.response);
        verify(tampered.response).sendError(eq(400), anyString());
        Exchange oversized = exchange(null, null, " ".repeat(65537));
        new PayosWebhookServlet(client, payments).doPost(oversized.request, oversized.response);
        verify(oversized.response).sendError(eq(413), anyString());
        verify(client, times(1)).verifyWebhook(any());
        verifyNoInteractions(payments);
    }

    private static Exchange exchange(ProfileBean principal, String path, String body) throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpSession session = principal == null ? null : mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        if (session != null) when(session.getAttribute(SessionAuth.CURRENT_USER)).thenReturn(principal);
        when(request.getPathInfo()).thenReturn(path);
        when(request.getContextPath()).thenReturn("/crave");
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
