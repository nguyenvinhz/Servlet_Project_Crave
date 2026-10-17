package com.foodordering.servlet;

import com.foodordering.exception.AccountException;
import com.foodordering.gateway.MapHttpClient;
import com.foodordering.security.SessionAuth;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.regex.Pattern;

/** Serves the original OSM images when browser DNS cannot reach the provider. */
@WebServlet(name = "MapTileServlet", urlPatterns = "/map/tiles/*")
public class MapTileServlet extends HttpServlet {
    private static final Pattern TILE = Pattern.compile("^/(\\d{1,2})/(\\d{1,6})/(\\d{1,6})\\.png$");
    private static final byte[] PNG = {(byte)137, 80, 78, 71, 13, 10, 26, 10};
    private final MapHttpClient client;

    public MapTileServlet() { this(new MapHttpClient(true)); }
    MapTileServlet(MapHttpClient client) { this.client = client; }

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            SessionAuth.requireCustomer(request);
            var tile = TILE.matcher(request.getPathInfo() == null ? "" : request.getPathInfo());
            if (!tile.matches()) { response.sendError(404); return; }
            int zoom = Integer.parseInt(tile.group(1));
            int x = Integer.parseInt(tile.group(2)), y = Integer.parseInt(tile.group(3));
            if (zoom > 19 || x >= (1L << zoom) || y >= (1L << zoom)) { response.sendError(404); return; }

            // Keep the web Referer for OSM's identification policy; do not send session cookies upstream.
            String referer = request.getHeader("Referer");
            URI page = referer == null ? null : URI.create(referer);
            if (page == null || page.getHost() == null || page.getUserInfo() != null
                    || !("http".equalsIgnoreCase(page.getScheme()) || "https".equalsIgnoreCase(page.getScheme()))) {
                response.sendError(400, "Thiếu nguồn trang tải bản đồ."); return;
            }
            var headers = new LinkedHashMap<String, String>();
            headers.put("Referer", referer);
            for (String name : new String[]{"If-None-Match", "If-Modified-Since"}) {
                String value = request.getHeader(name);
                if (value != null) headers.put(name, value);
            }
            URI endpoint = URI.create("https://tile.openstreetmap.org/" + zoom + "/" + x + "/" + y + ".png");
            var result = client.get(endpoint, "image/png", 1024 * 1024, headers);
            if (result.status() != 200 && result.status() != 304) {
                response.sendError(502, "Chưa tải được ảnh bản đồ. Vui lòng thử lại."); return;
            }
            if (result.status() == 200 && (result.body().length < PNG.length
                    || !Arrays.equals(PNG, Arrays.copyOf(result.body(), PNG.length)))) {
                response.sendError(502, "Dịch vụ bản đồ trả dữ liệu không hợp lệ."); return;
            }
            result.headers().forEach(response::setHeader);
            if (!result.headers().containsKey("Cache-Control") && !result.headers().containsKey("Expires")) {
                response.setHeader("Cache-Control", "public, max-age=604800");
            }
            response.setContentType("image/png");
            response.setStatus(result.status());
            if (result.status() == 200) response.getOutputStream().write(result.body());
        } catch (AccountException failure) { response.sendError(failure.getStatusCode(), failure.getMessage()); }
        catch (IllegalArgumentException failure) { response.sendError(400, "Yêu cầu bản đồ không hợp lệ."); }
        catch (IOException failure) {
            log("Không tải được ảnh bản đồ.", failure);
            if (!response.isCommitted()) response.sendError(503, "Chưa kết nối được dịch vụ ảnh bản đồ.");
        }
    }

    @Override public void destroy() {
        try { client.close(); }
        catch (IOException failure) { log("Không đóng được kết nối ảnh bản đồ.", failure); }
        finally { super.destroy(); }
    }
}
