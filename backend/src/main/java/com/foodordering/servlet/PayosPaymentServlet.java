package com.foodordering.servlet;

import com.foodordering.security.SessionAuth;
import com.foodordering.service.PayosPaymentService;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@WebServlet(name = "PayosPaymentServlet", urlPatterns = {"/payos/create", "/payos/status", "/payos/qr"})
public class PayosPaymentServlet extends BaseServlet {
    private PayosPaymentService payments;
    public PayosPaymentServlet() { }
    public PayosPaymentServlet(PayosPaymentService payments) { this.payments = payments; }
    @Override public void init() { if (payments == null) payments = new PayosPaymentService(); }
    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!"/payos/create".equals(request.getServletPath())) { response.sendError(405); return; }
        String orderId = parameter(request, "orderId");
        try { payments.create(orderId, SessionAuth.requireCustomer(request).id()); }
        catch (RuntimeException failure) {
            if (failure instanceof com.foodordering.exception.AppException account
                    && (account.getStatusCode() == 401 || account.getStatusCode() == 403)) {
                response.sendError(account.getStatusCode(), account.getMessage());
                return;
            }
            request.getSession().setAttribute("orderError", failure instanceof com.foodordering.exception.AppException
                    ? failure.getMessage() : "Chưa tạo được mã thanh toán. Vui lòng thử lại.");
        }
        redirect(request, response, "/orders/" + URLEncoder.encode(orderId, StandardCharsets.UTF_8));
    }
    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        response.setHeader("Cache-Control", "no-store");
        try {
            String customerId = SessionAuth.requireCustomer(request).id();
            String orderId = parameter(request, "orderId");
            if ("/payos/qr".equals(request.getServletPath())) {
                String payload = payments.qrPayload(orderId, customerId);
                BitMatrix matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 320, 320,
                        Map.of(EncodeHintType.CHARACTER_SET, "UTF-8", EncodeHintType.MARGIN, 4,
                                EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M));
                BufferedImage image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_INT_RGB);
                for (int y = 0; y < matrix.getHeight(); y++)
                    for (int x = 0; x < matrix.getWidth(); x++) image.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xffffff);
                ByteArrayOutputStream png = new ByteArrayOutputStream();
                ImageIO.write(image, "PNG", png);
                response.setContentType("image/png");
                response.setHeader("X-Content-Type-Options", "nosniff");
                response.getOutputStream().write(png.toByteArray());
            } else if ("/payos/status".equals(request.getServletPath())) {
                var payment = payments.status(orderId, customerId);
                var order = new com.foodordering.dto.OrderResponse();
                order.setOrderId(orderId);
                request.setAttribute("payos", payment);
                request.setAttribute("order", order);
                request.getRequestDispatcher("/WEB-INF/views/order/payos-status.jsp").forward(request, response);
            } else response.sendError(405);
        } catch (RuntimeException failure) { handleError(response, failure); }
        catch (com.google.zxing.WriterException failure) { response.sendError(500, "Chưa tạo được ảnh QR."); }
    }
}
