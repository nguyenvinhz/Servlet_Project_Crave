package com.foodordering.api;

import com.foodordering.dto.ApiResponse;
import com.foodordering.exception.AccountException;
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
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@WebServlet(name = "payosPaymentServlet", urlPatterns = "/api/payos/orders/*")
public class PayosPaymentServlet extends BaseApiServlet {
    private static final Pattern ORDER_PATH = Pattern.compile("/([A-Za-z0-9_-]{1,40})(/qr)?");
    private PayosPaymentService payments;

    public PayosPaymentServlet() {}

    public PayosPaymentServlet(PayosPaymentService payments) {
        this.payments = Objects.requireNonNull(payments);
    }

    @Override
    public void init() throws ServletException {
        if (payments == null) payments = new PayosPaymentService();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        try {
            String customerId = SessionAuth.requireCustomer(request).id();
            Matcher path = path(request);
            if (path.group(2) != null) {
                methodNotAllowed(response, "GET");
                return;
            }
            writeJson(response, 200, ApiResponse.success(payments.create(path.group(1), customerId)));
        } catch (Exception failure) {
            handleError(response, failure);
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        try {
            String customerId = SessionAuth.requireCustomer(request).id();
            Matcher path = path(request);
            if (path.group(2) == null) {
                writeJson(response, 200, ApiResponse.success(payments.status(path.group(1), customerId)));
                return;
            }
            // Render the verified VietQR payload locally; no credentials or payment data go to an image service.
            String payload = payments.qrPayload(path.group(1), customerId);
            BitMatrix matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 320, 320,
                    Map.of(EncodeHintType.CHARACTER_SET, "UTF-8", EncodeHintType.MARGIN, 4,
                            EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M));
            BufferedImage image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < matrix.getHeight(); y++) {
                for (int x = 0; x < matrix.getWidth(); x++) image.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xffffff);
            }
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", png);
            response.setStatus(200);
            response.setContentType("image/png");
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.getOutputStream().write(png.toByteArray());
        } catch (Exception failure) {
            handleError(response, failure);
        }
    }

    private static Matcher path(HttpServletRequest request) {
        Matcher result = ORDER_PATH.matcher(Objects.toString(request.getPathInfo(), ""));
        if (!result.matches()) throw new AccountException("NOT_FOUND", "Đường dẫn thanh toán không tồn tại.", 404);
        return result;
    }
}
