package vn.edu.crave.integration;

import vn.edu.crave.exception.BadRequestException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class AddressGeocoderTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @Test
    void roadSearchRetriesWithStreetLayerWhenProminentBuildingsHideTheRoad() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/", exchange -> {
            calls.incrementAndGet();
            String query = exchange.getRequestURI().getRawQuery();
            assertTrue(query.contains("countrycode=VN") && query.contains("lat=10.89&lon=106.76"));
            String feature = query.contains("layer=street")
                    ? "{\"type\":\"street\",\"name\":\"Võ Văn Ngân\"}"
                    : "{\"type\":\"house\",\"street\":\"Võ Văn Ngân\",\"name\":\"Vincom\"}";
            var properties = JSON.readTree(feature);
            ((com.fasterxml.jackson.databind.node.ObjectNode) properties).put("district", "Thủ Đức").put("city", "Hồ Chí Minh").put("countrycode", "VN");
            byte[] body = ("{\"features\":[{\"properties\":" + properties + ",\"geometry\":{\"coordinates\":[106.77,10.85]}}]}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            var geocoder = new AddressGeocoder(URI.create("http://localhost:" + server.getAddress().getPort() + "/api/"));
            assertTrue(geocoder.search("Vo Van Ngan Thu Duc TP HCM", 10.89, 106.76).get(0).approximate());
            assertEquals(2, calls.get());
        } finally { server.stop(0); }
    }

    @Test
    void coordinatesSkipProviderAndPreserveFullPrecision() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = server(calls, "{\"features\":[]}");
        try {
            var geocoder = new AddressGeocoder(URI.create("http://localhost:" + server.getAddress().getPort() + "/api/"));
            var point = geocoder.search("106.7725584, 10.8527907").get(0);
            assertEquals(10.8527907, point.latitude());
            assertEquals(106.7725584, point.longitude());
            assertFalse(point.approximate());
            assertThrows(BadRequestException.class, () -> geocoder.search("999,106"));
            assertEquals(0, calls.get());
        } finally { server.stop(0); }
    }

    @Test
    void searchReturnsBothHouseMatchesInsteadOfDiscardingAmbiguousAddress() throws Exception {
        String house = "{\"properties\":{\"type\":\"house\",\"housenumber\":\"12\",\"street\":\"Lê Lợi\",\"name\":\"Quán A\",\"countrycode\":\"VN\"},\"geometry\":{\"coordinates\":[106.77,10.85]}}";
        String other = house.replace("Quán A", "Quán B").replace("106.77,10.85", "106.78,10.86");
        HttpServer server = server(new AtomicInteger(), "{\"features\":[" + house + "," + other + "]}");
        try {
            var matches = new AddressGeocoder(URI.create("http://localhost:" + server.getAddress().getPort() + "/api/")).search("12 Lê Lợi");
            assertEquals(2, matches.size());
            assertNotEquals(matches.get(0).longitude(), matches.get(1).longitude());
        } finally { server.stop(0); }
    }

    @Test
    void searchAcceptsPlaceNameAndNumberedRoadButMissingHouseIsOnlyASuggestion() throws Exception {
        String place = "{\"properties\":{\"type\":\"house\",\"street\":\"Võ Văn Ngân\",\"name\":\"Trường Đại học Công nghệ Kỹ thuật\",\"countrycode\":\"VN\"},\"geometry\":{\"coordinates\":[106.77,10.85]}}";
        String road = "{\"properties\":{\"type\":\"street\",\"name\":\"Đường 3 Tháng 2\",\"city\":\"Hồ Chí Minh\",\"countrycode\":\"VN\"},\"geometry\":{\"coordinates\":[106.68,10.75]}}";
        HttpServer server = server(new AtomicInteger(), "{\"features\":[" + place + "," + road + "]}");
        try {
            var geocoder = new AddressGeocoder(URI.create("http://localhost:" + server.getAddress().getPort() + "/api/"));
            assertTrue(geocoder.search("Đại học Công nghệ Kỹ thuật").get(0).approximate());
            assertTrue(geocoder.search("3 Tháng 2, TP.HCM").get(0).approximate());
            assertTrue(geocoder.search("227 Đường 3 Tháng 2, TP.HCM").get(0).approximate());
        } finally { server.stop(0); }
    }

    @Test
    void reverseLookupDescribesNearbyAddressButNeverSnapsSelectedCoordinates() throws Exception {
        String house = "{\"properties\":{\"type\":\"house\",\"street\":\"Lê Lợi\",\"housenumber\":\"12\",\"countrycode\":\"VN\"},\"geometry\":{\"coordinates\":[106.77,10.8501]}}";
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = server(calls, "{\"features\":[" + house + "]}");
        try {
            var geocoder = new AddressGeocoder(URI.create("http://localhost:" + server.getAddress().getPort() + "/api/"));
            var point = geocoder.reverse(10.8500001, 106.77);
            assertEquals(10.8500001, point.latitude());
            assertEquals("12 Lê Lợi", point.label());
            var cached = geocoder.reverse(10.8500002, 106.77);
            assertEquals(10.8500002, cached.latitude());
            assertEquals(1, calls.get());
        } finally { server.stop(0); }
    }

    @Test
    void reverseFailureOrDistantResultReturnsCoordinateLabel() throws Exception {
        String distant = "{\"properties\":{\"street\":\"Lê Lợi\",\"countrycode\":\"VN\"},\"geometry\":{\"coordinates\":[106.77,10.89]}}";
        HttpServer server = server(new AtomicInteger(), "{\"features\":[" + distant + "]}");
        try {
            var geocoder = new AddressGeocoder(URI.create("http://localhost:" + server.getAddress().getPort() + "/api/"));
            assertEquals("10.850000, 106.770000", geocoder.reverse(10.85,106.77).label());
            assertThrows(BadRequestException.class, () -> geocoder.reverse(null, 106.77));
            server.stop(0);
            assertEquals("10.860000, 106.780000", geocoder.reverse(10.86,106.78).label());
        } finally { server.stop(0); }
    }

    // Provider responses are fixtures, not claims that these example coordinates are real entrances.
    static Stream<Arguments> variedAddresses() {
        return Stream.of(
                Arguments.of(" SỐ NHÀ: 12 Nguyễn Trãi; Q.1; TP.HCM ", "12", "Nguyễn Trãi", "Quận 1", "Hồ Chí Minh"),
                Arguments.of("12 nguyen trai, quan 1, tp.hcm", "12", "Nguyễn Trãi", "Quận 1", "Hồ Chí Minh"),
                Arguments.of("12 Nguyễn Trãi Q.1 TP.HCM", "12", "Nguyễn Trãi", "Quận 1", "Hồ Chí Minh"),
                Arguments.of("12 Nguyễn Trãi Q1 TP HCM", "12", "Nguyễn Trãi", "Quận 1", "Hồ Chí Minh"),
                Arguments.of("12 Nguyễn Trãi, Quận 1, Ho Chi Minh City, VN", "12", "Nguyễn Trãi", "Quận 1", "Hồ Chí Minh"),
                Arguments.of("12A Điện Biên Phủ, P.25, Q.Bình Thạnh, TP. Hồ Chí Minh", "12A", "Điện Biên Phủ", "Phường 25, Bình Thạnh", "Hồ Chí Minh"),
                Arguments.of("12 a Đ. Điện Biên Phủ, Phường 25, Bình Thạnh, HCMC", "12A", "Điện Biên Phủ", "Phường 25, Bình Thạnh", "Hồ Chí Minh"),
                Arguments.of("129 / 12 Nguyễn Đình Chiểu, Q.3, TPHCM", "129/12", "Nguyễn Đình Chiểu", "Quận 3", "Hồ Chí Minh"),
                Arguments.of("99 Đường Số 9, Dĩ An, Hồ Chí Minh 825030, Việt Nam", "99", "Đường Số 9", "Dĩ An", "Hồ Chí Minh"),
                Arguments.of("227 Nguyễn Văn Cừ, Quận 5, TP.HCM", "227", "Nguyễn Văn Cừ", "Quận 5", "Hồ Chí Minh"),
                Arguments.of("268 Lý Thường Kiệt, Quận 10, Sài Gòn", "268", "Lý Thường Kiệt", "Quận 10", "Hồ Chí Minh"),
                Arguments.of("2 Trường Sa, Bình Thạnh, HCM", "2", "Trường Sa", "Bình Thạnh", "Hồ Chí Minh"),
                Arguments.of("12 Trần Phú, Hải Châu, Đà Nẵng", "12", "Trần Phú", "Hải Châu", "Đà Nẵng"),
                Arguments.of("8 Tràng Thi, Hoàn Kiếm, Hà Nội", "8", "Tràng Thi", "Hoàn Kiếm", "Hà Nội"),
                Arguments.of("12 Đường 3 Tháng 2, Quận 10, TP HCM", "12", "Đường 3 Tháng 2", "Quận 10", "Hồ Chí Minh"),
                Arguments.of("12\u00a0Lê\u200b Lợi， Quận 1， Hồ Chí Minh", "12", "Lê Lợi", "Quận 1", "Hồ Chí Minh")
        );
    }

    @ParameterizedTest
    @MethodSource("variedAddresses")
    void normalizesDifferentVietnameseAddresses(String input, String house, String street, String district, String city) throws Exception {
        var feature = JSON.createObjectNode();
        feature.putObject("properties").put("type", "house").put("housenumber", house).put("street", street)
                .put("district", district).put("city", city).put("country", "Việt Nam").put("countrycode", "VN");
        feature.putObject("geometry").putArray("coordinates").add(106.77).add(10.85);
        var body = JSON.createObjectNode();
        body.putArray("features").add(feature);
        HttpServer server = server(new AtomicInteger(), body.toString());
        try {
            var location = new AddressGeocoder(URI.create("http://localhost:" + server.getAddress().getPort() + "/api/")).resolve(input);
            assertTrue(location.label().startsWith(house + " " + street));
            assertFalse(location.approximate());
        } finally { server.stop(0); }
    }

    @Test
    void rejectsSimilarStreetAndAmbiguousDistantMatches() throws Exception {
        String first = "{\"properties\":{\"type\":\"house\",\"street\":\"Lê Lợi\",\"housenumber\":\"12\",\"countrycode\":\"VN\"},\"geometry\":{\"coordinates\":[106.77,10.85]}}";
        String second = first.replace("106.77,10.85", "106.68,10.75");
        HttpServer server = server(new AtomicInteger(), "{\"features\":[" + first + "," + second + "]}");
        try {
            var geocoder = new AddressGeocoder(URI.create("http://localhost:" + server.getAddress().getPort() + "/api/"));
            assertThrows(BadRequestException.class, () -> geocoder.resolve("12 Lê Lợi"));
            assertThrows(BadRequestException.class, () -> geocoder.resolve("12 Lê Lợi nối dài"));
            assertThrows(BadRequestException.class, () -> geocoder.resolve("Lê Lợi"));
        } finally { server.stop(0); }
    }

    @Test
    void streetWithoutHouseIsOnlyAnApproximateSuggestion() throws Exception {
        String street = "{\"properties\":{\"type\":\"street\",\"name\":\"Nguyễn Văn Cừ\",\"city\":\"Hồ Chí Minh\",\"countrycode\":\"VN\"},\"geometry\":{\"coordinates\":[106.68,10.75]}}";
        HttpServer server = server(new AtomicInteger(), "{\"features\":[" + street + "]}");
        try {
            var geocoder = new AddressGeocoder(URI.create("http://localhost:" + server.getAddress().getPort() + "/api/"));
            assertTrue(geocoder.resolve("Đường Nguyễn Văn Cừ, TP.HCM").approximate());
            assertThrows(BadRequestException.class, () -> geocoder.resolve("227 Nguyễn Văn Cừ, TP.HCM"));
        } finally { server.stop(0); }
    }

    @Test
    void missingFormerDistrictNeedsManualConfirmationButConflictingCityIsRejected() throws Exception {
        String house = "{\"properties\":{\"type\":\"house\",\"street\":\"Nguyễn Văn Cừ\",\"housenumber\":\"227\",\"district\":\"Phường Chợ Quán\",\"city\":\"Hồ Chí Minh\",\"countrycode\":\"VN\"},\"geometry\":{\"coordinates\":[106.68,10.75]}}";
        HttpServer server = server(new AtomicInteger(), "{\"features\":[" + house + "]}");
        try {
            var geocoder = new AddressGeocoder(URI.create("http://localhost:" + server.getAddress().getPort() + "/api/"));
            assertTrue(geocoder.resolve("227 Nguyễn Văn Cừ, Quận 5, TP.HCM").approximate());
            assertFalse(geocoder.resolve("227 Nguyễn Văn Cừ, Phường Chợ Quán, TP.HCM").approximate());
            assertThrows(BadRequestException.class, () -> geocoder.resolve("227 Nguyễn Văn Cừ, Phường 1, TP.HCM"));
            assertThrows(BadRequestException.class, () -> geocoder.resolve("227 Nguyễn Văn Cừ, Quận 5, Hà Nội"));
        } finally { server.stop(0); }
    }

    @Test
    void wardOneCannotMatchAnIncorrectDistrictOne() throws Exception {
        String house = "{\"properties\":{\"type\":\"house\",\"street\":\"Lê Lợi\",\"housenumber\":\"12\",\"district\":\"Phường 1\",\"county\":\"Quận 5\",\"city\":\"Hồ Chí Minh\",\"countrycode\":\"VN\"},\"geometry\":{\"coordinates\":[106.68,10.75]}}";
        HttpServer server = server(new AtomicInteger(), "{\"features\":[" + house + "]}");
        try {
            var geocoder = new AddressGeocoder(URI.create("http://localhost:" + server.getAddress().getPort() + "/api/"));
            assertThrows(BadRequestException.class, () -> geocoder.resolve("12 Lê Lợi, Q.1, TP.HCM"));
            assertFalse(geocoder.resolve("12 Lê Lợi, P.1, Q.5, TP.HCM").approximate());
        } finally { server.stop(0); }
    }

    @Test
    void resolvesVietnamStreetCoordinatesAndCachesRepeatedLookup() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = server(calls, "{\"features\":[{\"properties\":{\"type\":\"house\",\"street\":\"Võ Văn Ngân\",\"housenumber\":\"1\",\"countrycode\":\"VN\",\"district\":\"Thủ Đức\",\"city\":\"Hồ Chí Minh\"},\"geometry\":{\"coordinates\":[106.7726,10.8528]}}]}");
        try {
            AddressGeocoder geocoder = new AddressGeocoder(URI.create("http://localhost:" + server.getAddress().getPort() + "/api/"));
            var location = geocoder.resolve("1 Võ Văn Ngân, Thủ Đức, TP.HCM");
            assertEquals(10.8528, location.latitude());
            assertEquals(106.7726, location.longitude());
            assertFalse(location.approximate());
            assertEquals(location, geocoder.resolve("1 Võ Văn Ngân, Thủ Đức, TP.HCM"));
            assertEquals(1, calls.get());
        } finally { server.stop(0); }
    }

    @Test
    void soPrefixCannotTurnHouseOneIntoHouse228AndAliasesShareCache() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        String wrong = "{\"properties\":{\"type\":\"house\",\"street\":\"Võ Văn Ngân\",\"housenumber\":\"228\",\"countrycode\":\"VN\",\"district\":\"Thủ Đức\",\"city\":\"Hồ Chí Minh\"},\"geometry\":{\"coordinates\":[106.7558,10.851]}}";
        String correct = wrong.replace("\"228\"", "\"1\"").replace("106.7558,10.851", "106.7726,10.8528");
        HttpServer server = server(calls, "{\"features\":[" + wrong + "," + correct + "]}");
        try {
            AddressGeocoder geocoder = new AddressGeocoder(URI.create("http://localhost:" + server.getAddress().getPort() + "/api/"));
            var location = geocoder.resolve("Số 1 Võ Văn Ngân, Thủ Đức, TP.HCM");
            assertEquals(106.7726, location.longitude());
            assertTrue(location.label().startsWith("1 Võ Văn Ngân"));
            assertEquals(location, geocoder.resolve("1 Võ Văn Ngân, Thủ Đức, TP.HCM"));
            assertEquals(location, geocoder.resolve("So 1 Võ Văn Ngân, Thủ Đức, TP.HCM"));
            assertEquals(1, calls.get());
        } finally { server.stop(0); }
    }

    @Test
    void numberedAddressRejectsStreetMidpointAndWrongCity() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        String street = "{\"properties\":{\"type\":\"street\",\"name\":\"Võ Văn Ngân\",\"countrycode\":\"VN\"},\"geometry\":{\"coordinates\":[106.77,10.85]}}";
        String wrongCity = "{\"properties\":{\"type\":\"house\",\"street\":\"Võ Văn Ngân\",\"housenumber\":\"1\",\"city\":\"Hà Nội\",\"countrycode\":\"VN\"},\"geometry\":{\"coordinates\":[105.8,21.0]}}";
        HttpServer server = server(calls, "{\"features\":[" + street + "," + wrongCity + "]}");
        try {
            AddressGeocoder geocoder = new AddressGeocoder(URI.create("http://localhost:" + server.getAddress().getPort() + "/api/"));
            assertThrows(BadRequestException.class, () -> geocoder.resolve("Số 1 Võ Văn Ngân, Thủ Đức, TP.HCM"));
        } finally { server.stop(0); }
    }

    @Test
    void rejectsWrongHouseNumberInsteadOfInventingAConfirmedLocation() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = server(calls, "{\"features\":[{\"properties\":{\"type\":\"house\",\"street\":\"Nguyễn Trãi\",\"housenumber\":\"129/12\",\"countrycode\":\"VN\"},\"geometry\":{\"coordinates\":[106.68,10.75]}}]}");
        try {
            AddressGeocoder geocoder = new AddressGeocoder(URI.create("http://localhost:" + server.getAddress().getPort() + "/api/"));
            assertThrows(BadRequestException.class, () -> geocoder.resolve("12 Nguyễn Trãi, Quận 1"));
            assertThrows(BadRequestException.class, () -> geocoder.resolve("ab"));
            assertEquals(2, calls.get());
        } finally { server.stop(0); }
    }

    @Test
    void nativePhotonRequestUsesCountryFocusAndBoundingBoxAndKeepsTheClosestValidCandidate() throws Exception {
        String street = "{\"properties\":{\"type\":\"street\",\"name\":\"Đường số 9\",\"countrycode\":\"VN\"},\"geometry\":{\"coordinates\":[106.77,10.70]}}";
        String near = street.replace("106.77,10.70", "106.76,10.89");
        String far = street.replace("106.77,10.70", "105.85,21.03");
        String foreign = near.replace("\"VN\"", "\"KH\"");
        String invalid = near.replace("106.76,10.89", "\"invalid\",10.89");
        AtomicReference<String> uri = new AtomicReference<>(), accept = new AtomicReference<>();
        HttpServer server = server(new AtomicInteger(), "{\"features\":[" + street + "," + far + "," + foreign + "," + invalid + "," + near + "]}", 200, uri, accept);
        try (var geocoder = new AddressGeocoder(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/"))) {
            var matches = geocoder.search("Đường số 9", 10.8925871, 106.7637161);
            assertEquals(2, matches.size());
            assertEquals(10.89, matches.get(0).latitude());
            assertEquals(10.70, matches.get(1).latitude());
            String query = URLDecoder.decode(uri.get(), StandardCharsets.UTF_8);
            assertTrue(query.contains("q=Đường số 9"));
            assertTrue(query.contains("countrycode=VN"));
            assertTrue(query.contains("lat=10.8925871&lon=106.7637161"));
            String[] bounds = query.substring(query.indexOf("bbox=") + 5).split(",");
            assertEquals(106.5637161, Double.parseDouble(bounds[0]), .0000001);
            assertEquals(10.6925871, Double.parseDouble(bounds[1]), .0000001);
            assertEquals(106.9637161, Double.parseDouble(bounds[2]), .0000001);
            assertEquals(11.0925871, Double.parseDouble(bounds[3]), .0000001);
            assertEquals("application/json", accept.get());
        } finally { server.stop(0); }
    }

    @Test
    void repeatedEmptySearchIsCachedAndCannotProduceAnInventedLocation() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = server(calls, "{\"features\":[]}");
        try (var geocoder = new AddressGeocoder(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/"))) {
            assertThrows(BadRequestException.class, () -> geocoder.search("Đường số 9"));
            assertThrows(BadRequestException.class, () -> geocoder.search("  đường Số 9  "));
            assertEquals(2, calls.get(), "The second search reuses the empty result instead of requesting Photon again");
        } finally { server.stop(0); }
    }

    @Test
    void aDayAndMonthStreetKeepsItsDayInTheStreetFallback() throws Exception {
        for (String address : new String[]{"3 Tháng 2, Quận 10", "30 Thang 4, Dĩ An"}) {
            AtomicReference<String> uri = new AtomicReference<>();
            HttpServer server = server(new AtomicInteger(), "{\"features\":[]}", 200, uri, new AtomicReference<>());
            try (var geocoder = new AddressGeocoder(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/"))) {
                assertThrows(BadRequestException.class, () -> geocoder.search(address));
                String query = URLDecoder.decode(uri.get(), StandardCharsets.UTF_8);
                assertTrue(query.contains("q=" + address.split(",")[0]));
                assertTrue(query.contains("layer=street"));
            } finally { server.stop(0); }
        }
    }

    @Test
    void reverseUsesTheProviderReverseRouteAndRetainsCustomDeploymentPrefix() throws Exception {
        String place = "{\"properties\":{\"street\":\"Lê Lợi\",\"housenumber\":\"12\",\"countrycode\":\"VN\"},\"geometry\":{\"coordinates\":[106.77,10.8501]}}";
        AtomicReference<String> uri = new AtomicReference<>();
        HttpServer server = server(new AtomicInteger(), "{\"features\":[" + place + "]}", 200, uri, new AtomicReference<>());
        try {
            for (String base : new String[]{"/api/", "/api", "/photon/api/"}) {
                try (var geocoder = new AddressGeocoder(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + base))) {
                    var match = geocoder.reverse(10.8500001, 106.7700001);
                    assertEquals("12 Lê Lợi", match.label());
                    assertEquals(10.8500001, match.latitude());
                    assertEquals(106.7700001, match.longitude());
                    assertTrue(uri.get().startsWith(base.startsWith("/photon/") ? "/photon/reverse?" : "/reverse?"));
                    assertTrue(uri.get().contains("radius=0.05"));
                    assertFalse(uri.get().contains("countrycode="), "Photon only accepts the country filter for forward search");
                }
            }
        } finally { server.stop(0); }
    }

    @Test
    void malformedEmptyAndDownProviderResponsesCannotProduceAMatch() throws Exception {
        for (String body : new String[]{"{\"features\":[", "{}", "{\"features\":{}}", "{\"features\":[]}",
                "{\"features\":[{\"properties\":{\"type\":\"street\",\"name\":\"Lê Lợi\",\"countrycode\":\"VN\"},\"geometry\":{\"coordinates\":{\"0\":106.77,\"1\":10.85}}}]}",
                "{\"features\":[{\"properties\":{\"type\":\"street\",\"name\":\"Lê Lợi\",\"countrycode\":\"VN\"},\"geometry\":{\"coordinates\":[106.77,90]}}]}"}) {
            HttpServer server = server(new AtomicInteger(), body);
            try (var geocoder = new AddressGeocoder(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/"))) {
                assertThrows(BadRequestException.class, () -> geocoder.search("Lê Lợi"));
            } finally { server.stop(0); }
        }
        HttpServer server = server(new AtomicInteger(), "{\"features\":[]}", 503, new AtomicReference<>(), new AtomicReference<>());
        try (var geocoder = new AddressGeocoder(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/"))) {
            assertThrows(BadRequestException.class, () -> geocoder.search("Lê Lợi"));
        } finally { server.stop(0); }
    }

    @Test
    void invalidFocusAndShortOrLongInputNeverRequestTheProvider() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = server(calls, "{\"features\":[]}");
        try (var geocoder = new AddressGeocoder(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/"))) {
            assertThrows(BadRequestException.class, () -> geocoder.search("Lê Lợi", 10.85, null));
            assertThrows(BadRequestException.class, () -> geocoder.search("Lê Lợi", Double.NaN, 106.77));
            assertThrows(BadRequestException.class, () -> geocoder.search("ab"));
            assertThrows(BadRequestException.class, () -> geocoder.search("a".repeat(256)));
            assertEquals(0, calls.get());
        } finally { server.stop(0); }
    }

    private HttpServer server(AtomicInteger calls, String body) throws Exception {
        return server(calls, body, 200, new AtomicReference<>(), new AtomicReference<>());
    }

    private HttpServer server(AtomicInteger calls, String body, int status, AtomicReference<String> uri, AtomicReference<String> accept) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            uri.set(exchange.getRequestURI().toString());
            accept.set(exchange.getRequestHeaders().getFirst("Accept"));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        return server;
    }
}
