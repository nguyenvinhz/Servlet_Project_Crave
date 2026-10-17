package com.foodordering.servlet;

import com.foodordering.dto.ProfileResponse;
import com.foodordering.enums.AccountType;
import com.foodordering.enums.EmployeeRole;
import com.foodordering.gateway.MapHttpClient;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MapTileServletTest {
    private static final String REFERER = "http://localhost:8081/crave/checkout";
    private static final URI TILE = URI.create("https://tile.openstreetmap.org/14/13050/7690.png");
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLbtAAAAABJRU5ErkJggg==");

    private final MapHttpClient client = mock(MapHttpClient.class);
    private final MapTileServlet servlet = new MapTileServlet(client);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);
    private final HttpSession session = mock(HttpSession.class);
    private final ServletContext context = mock(ServletContext.class);
    private final ByteArrayOutputStream body = new ByteArrayOutputStream();

    @BeforeEach
    void setup() throws Exception {
        ServletConfig config = mock(ServletConfig.class);
        when(config.getServletContext()).thenReturn(context);
        servlet.init(config);
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("currentUser")).thenReturn(new ProfileResponse("KH01", AccountType.CUSTOMER,
                "Customer", "customer@example.com", "0901234567", null));
        when(request.getPathInfo()).thenReturn("/14/13050/7690.png");
        when(request.getHeader("Referer")).thenReturn(REFERER);
        when(response.getOutputStream()).thenReturn(new ServletOutputStream() {
            @Override public boolean isReady() { return true; }
            @Override public void setWriteListener(WriteListener listener) { }
            @Override public void write(int value) { body.write(value); }
        });
    }

    @Test
    void servesTheExactProviderPngFromTheFixedOsmEndpointWithoutSendingSessionCookies() throws Exception {
        Map<String, String> headers = Map.of("Referer", REFERER,
                "If-None-Match", "\"osm-tile-1\"", "If-Modified-Since", "Fri, 16 Oct 2026 10:00:00 GMT");
        when(request.getHeader("If-None-Match")).thenReturn(headers.get("If-None-Match"));
        when(request.getHeader("If-Modified-Since")).thenReturn(headers.get("If-Modified-Since"));
        when(request.getHeader("Cookie")).thenReturn("JSESSIONID=private-session");
        when(request.getHeader("Authorization")).thenReturn("Bearer private-token");
        when(client.get(TILE, "image/png", 1024 * 1024, headers))
                .thenReturn(new MapHttpClient.Reply(200, PNG, Map.of("Cache-Control", "public, max-age=86400",
                        "ETag", "\"osm-tile-2\"", "Last-Modified", "Sat, 17 Oct 2026 10:00:00 GMT")));

        servlet.doGet(request, response);

        assertArrayEquals(PNG, body.toByteArray());
        verify(client).get(TILE, "image/png", 1024 * 1024, headers);
        verifyNoMoreInteractions(client);
        verify(request, never()).getHeader("Cookie");
        verify(request, never()).getHeader("Authorization");
        verify(response).setContentType("image/png");
        verify(response).setStatus(200);
        verify(response).setHeader("Cache-Control", "public, max-age=86400");
        verify(response).setHeader("ETag", "\"osm-tile-2\"");
        verify(response).setHeader("Last-Modified", "Sat, 17 Oct 2026 10:00:00 GMT");
        verify(response, never()).sendError(anyInt(), anyString());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"/14/13050/7690.jpg", "/14/13050/7690.png/extra", "/14/-1/7690.png",
            "/20/0/0.png", "/0/1/0.png", "/0/0/1.png", "/14/16384/0.png", "/14/0/16384.png",
            "/19/524288/0.png", "/99/0/0.png", "/14/9999999/0.png", "/../14/0/0.png",
            "/https://example.com/tile.png"})
    void rejectsMalformedAndOutOfRangeTilesBeforeCallingTheProvider(String path) throws Exception {
        when(request.getPathInfo()).thenReturn(path);

        servlet.doGet(request, response);

        verify(response).sendError(404);
        verifyNoInteractions(client);
        verify(response, never()).getOutputStream();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/0/0/0.png", "/19/524287/524287.png"})
    void acceptsTheWorldTileAndTheMaximumSupportedTile(String path) throws Exception {
        URI endpoint = URI.create("https://tile.openstreetmap.org" + path);
        when(client.get(endpoint, "image/png", 1024 * 1024, Map.of("Referer", REFERER)))
                .thenReturn(new MapHttpClient.Reply(200, PNG, Map.of()));
        when(request.getPathInfo()).thenReturn(path);

        servlet.doGet(request, response);

        assertArrayEquals(PNG, body.toByteArray());
        verify(client).get(endpoint, "image/png", 1024 * 1024, Map.of("Referer", REFERER));
        verify(response).setHeader("Cache-Control", "public, max-age=604800");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"file:///checkout", "https://", "not a URI", "/crave/checkout",
            "ftp://example.com/checkout", "https://user:password@example.com/checkout"})
    void requiresAValidHttpPageRefererBeforeFetchingTiles(String referer) throws Exception {
        when(request.getHeader("Referer")).thenReturn(referer);

        servlet.doGet(request, response);

        verify(response).sendError(eq(400), anyString());
        verifyNoInteractions(client);
        verify(response, never()).getOutputStream();
    }

    @Test
    void guestsCannotUseTheTileProxy() throws Exception {
        when(request.getSession(false)).thenReturn(null);

        servlet.doGet(request, response);

        verify(response).sendError(401, "Vui lòng đăng nhập để tiếp tục.");
        verifyNoInteractions(client);
    }

    @Test
    void employeesCannotUseTheCustomerTileProxy() throws Exception {
        when(session.getAttribute("currentUser")).thenReturn(new ProfileResponse("NV01", AccountType.EMPLOYEE,
                "Admin", "admin@example.com", "0901234568", EmployeeRole.ADMIN));

        servlet.doGet(request, response);

        verify(response).sendError(403, "Chức năng này chỉ dành cho khách hàng.");
        verifyNoInteractions(client);
    }

    @ParameterizedTest
    @ValueSource(ints = {301, 302, 403, 404, 429, 500, 503})
    void doesNotReturnProviderErrorPagesAsMapImages(int status) throws Exception {
        reply(status, PNG, Map.of("Cache-Control", "public, max-age=86400"));

        servlet.doGet(request, response);

        verify(response).sendError(eq(502), anyString());
        verify(response, never()).getOutputStream();
        verify(response, never()).setHeader(eq("Cache-Control"), anyString());
        assertEquals(0, body.size());
    }

    @Test
    void rejectsNonPngContentReturnedWithAnOkStatus() throws Exception {
        reply(200, "<html>service unavailable</html>".getBytes(java.nio.charset.StandardCharsets.UTF_8), Map.of());

        servlet.doGet(request, response);

        verify(response).sendError(eq(502), anyString());
        verify(response, never()).getOutputStream();
    }

    @Test
    void rejectsAnEmptyOkResponse() throws Exception {
        reply(200, new byte[0], Map.of());

        servlet.doGet(request, response);

        verify(response).sendError(eq(502), anyString());
        verify(response, never()).getOutputStream();
    }

    @Test
    void forwardsNotModifiedAndItsCacheMetadataWithoutAnImageBody() throws Exception {
        reply(304, new byte[0], Map.of("Cache-Control", "public, max-age=86400", "ETag", "\"osm-tile-1\""));

        servlet.doGet(request, response);

        verify(response).setStatus(304);
        verify(response).setHeader("ETag", "\"osm-tile-1\"");
        verify(response).setHeader("Cache-Control", "public, max-age=86400");
        verify(response, never()).getOutputStream();
        assertEquals(0, body.size());
    }

    @Test
    void preservesTheProvidersExpiryRatherThanAddingAnotherCacheLifetime() throws Exception {
        reply(200, PNG, Map.of("Expires", "Sun, 18 Oct 2026 10:00:00 GMT"));

        servlet.doGet(request, response);

        verify(response).setHeader("Expires", "Sun, 18 Oct 2026 10:00:00 GMT");
        verify(response, never()).setHeader(eq("Cache-Control"), anyString());
    }

    @Test
    void providerConnectionFailuresReturnAServiceUnavailableResponse() throws Exception {
        IOException failure = new IOException("Fixture connection failure");
        when(client.get(eq(TILE), eq("image/png"), anyInt(), anyMap())).thenThrow(failure);

        servlet.doGet(request, response);

        verify(response).sendError(eq(503), anyString());
        verify(context).log(contains("ảnh bản đồ"), same(failure));
        verify(response, never()).getOutputStream();
    }

    @Test
    void destroyingTheServletClosesItsProviderConnections() throws Exception {
        servlet.destroy();

        verify(client).close();
    }

    @Test
    void closeFailuresAreLoggedWithoutPreventingShutdown() throws Exception {
        IOException failure = new IOException("Fixture close failure");
        doThrow(failure).when(client).close();

        assertDoesNotThrow(servlet::destroy);

        verify(context).log(contains("kết nối ảnh bản đồ"), same(failure));
    }

    private void reply(int status, byte[] image, Map<String, String> headers) throws IOException {
        when(client.get(TILE, "image/png", 1024 * 1024, Map.of("Referer", REFERER)))
                .thenReturn(new MapHttpClient.Reply(status, image, headers));
    }
}
