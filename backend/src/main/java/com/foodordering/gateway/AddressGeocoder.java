package com.foodordering.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodordering.enums.ErrorCode;
import com.foodordering.exception.BadRequestException;
import com.foodordering.utils.CoordinateParser;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Photon GeoJSON lookup; the customer confirms the final delivery point. */
public class AddressGeocoder implements AutoCloseable {
    public record Location(double latitude, double longitude, String label, boolean approximate) {}
    private record Cached(List<Location> locations, long expiresAt) {}
    private static final Pattern HOUSE = Pattern.compile("^(\\d+[\\p{L}\\d/.-]*)\\s+(.+)$");
    private static final Pattern DATE_STREET = Pattern.compile("(?iu)^(?:[1-9]|[12]\\d|3[01])\\s+th[aá]ng\\s+(?:[1-9]|1[0-2])\\b.*");
    private final URI endpoint;
    private final MapHttpClient client = new MapHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, Cached> cache = new LinkedHashMap<>();
    private long lastRequestAt;

    public AddressGeocoder() {
        this(URI.create(setting()));
    }

    public AddressGeocoder(URI endpoint) {
        this.endpoint = endpoint;
    }

    private static String setting() {
        String value = System.getenv("CRAVE_GEOCODER_URL");
        if (value == null || value.isBlank()) value = System.getProperty("CRAVE_GEOCODER_URL");
        return value == null || value.isBlank() ? "https://photon.komoot.io/api/" : value;
    }

    public Location resolve(String address) {
        List<Location> locations = search(address);
        if (CoordinateParser.parse(address).isPresent()) return locations.get(0);
        if (locations.size() != 1) throw new BadRequestException(ErrorCode.DELIVERY_LOCATION_REQUIRED,
                "Địa chỉ khớp nhiều vị trí khác nhau. Vui lòng chọn và xác nhận điểm nhận trên bản đồ.");
        // Legacy resolve never presents a street fallback as the requested house.
        Matcher house = HOUSE.matcher(normalizeInput(address).split(",")[0]);
        if (house.matches() && !DATE_STREET.matcher(normalizeInput(address).split(",")[0]).matches()
                && !locations.get(0).label().regionMatches(true, 0, house.group(1) + " ", 0, house.group(1).length() + 1)) throw notFound();
        return locations.get(0);
    }

    public List<Location> search(String address) { return search(address, null, null); }

    public List<Location> search(String address, Double latitude, Double longitude) {
        if (address == null || address.trim().length() < 3 || address.length() > 255) throw notFound();
        var coordinate = CoordinateParser.parse(address);
        if (coordinate.isPresent()) {
            var point = coordinate.get();
            return List.of(new Location(point.latitude(), point.longitude(), point.label(), false));
        }
        if (latitude != null || longitude != null) CoordinateParser.validate(latitude, longitude);
        String query = normalizeInput(address);
        String key = fold(query) + ":" + latitude + ":" + longitude;
        List<Location> cached = cached(key);
        if (cached != null) {
            if (cached.isEmpty()) throw notFound();
            return cached;
        }
        try {
            String focus = latitude != null ? "&lat=" + latitude + "&lon=" + longitude
                    + "&bbox=" + (longitude - .2) + "," + (latitude - .2) + "," + (longitude + .2) + "," + (latitude + .2) : "";
            List<Location> matches = candidates(query, get(endpoint, "q=" + encode(query) + "&limit=8&countrycode=VN" + focus), latitude, longitude);
            if (matches.isEmpty()) {
                String firstPart = query.split(",")[0];
                Matcher house = HOUSE.matcher(firstPart);
                String fallback = house.matches() && !DATE_STREET.matcher(firstPart).matches() ? house.group(2) : firstPart;
                // Older district/ward names can suppress valid results at the provider.
                for (String part : query.split(",")) {
                    if (!part.equals(firstPart) && administrativePrefix(part).isEmpty()) fallback += ", " + part.trim();
                }
                matches = candidates(query,
                        get(endpoint, "q=" + encode(fallback) + "&limit=8&countrycode=VN&layer=street" + focus), latitude, longitude);
            }
            remember(key, matches);
            if (matches.isEmpty()) throw notFound();
            return matches;
        } catch (BadRequestException e) { throw e; }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw unavailable(); }
        catch (Exception e) { throw unavailable(); }
    }

    private List<Location> candidates(String query, JsonNode features, Double storeLatitude, Double storeLongitude) {
            String firstPart = query.split(",")[0].trim();
            Matcher houseMatch = HOUSE.matcher(firstPart);
            boolean hasHouseNumber = houseMatch.matches() && !DATE_STREET.matcher(firstPart).matches();
            String requestedHouse = hasHouseNumber ? houseMatch.group(1) : "";
            String streetQuery = normalize(hasHouseNumber ? houseMatch.group(2) : firstPart);
            List<Location> matches = new ArrayList<>();
            for (JsonNode feature : features) {
                JsonNode properties = feature.path("properties"), coordinates = feature.path("geometry").path("coordinates");
                String type = properties.path("type").asText();
                String street = properties.path("street").asText();
                if (street.isBlank() && type.equals("street")) street = properties.path("name").asText();
                if (!"VN".equalsIgnoreCase(properties.path("countrycode").asText()) || !coordinates.isArray() || coordinates.size() < 2) continue;
                String normalizedStreet = normalize(street);
                boolean road = nameMatches(firstPart, street, properties) && type.equals("street");
                boolean exactHouse = hasHouseNumber && nameMatches(hasHouseNumber ? houseMatch.group(2) : firstPart, street, properties)
                        && properties.path("housenumber").asText().replaceAll("\\s+", "").equalsIgnoreCase(requestedHouse);
                boolean streetFallback = hasHouseNumber && type.equals("street") && normalizedStreet.equals(streetQuery);
                String name = properties.path("name").asText();
                boolean place = !hasHouseNumber && !name.isBlank() && (nameMatches(firstPart, name, properties)
                        || (!type.equals("street") && normalize(firstPart).length() >= 5
                        && (" " + normalize(name) + " ").contains(" " + normalize(firstPart) + " ")));
                if (!(exactHouse || road || streetFallback || place)) continue;
                double longitude = coordinates.get(0).asDouble(Double.NaN), latitude = coordinates.get(1).asDouble(Double.NaN);
                if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                        || latitude < 8 || latitude > 24 || longitude < 102 || longitude > 110) continue;
                if (storeLatitude != null && (Math.abs(latitude - storeLatitude) > .2 || Math.abs(longitude - storeLongitude) > .2)) continue;
                String house = properties.path("housenumber").asText();
                int areaConfidence = areaConfidence(query, properties);
                if (areaConfidence < 0) continue;
                LinkedHashSet<String> parts = new LinkedHashSet<>();
                if (!street.isBlank()) parts.add((house + " " + street).trim());
                for (String field : new String[]{"name", "locality", "district", "county", "city", "state", "country"}) {
                    String value = properties.path(field).asText();
                    if (!value.isBlank()) parts.add(value);
                }
                Location location = new Location(latitude, longitude, String.join(", ", parts), !exactHouse || areaConfidence == 0);
                if (matches.stream().noneMatch(existing -> existing.label().equals(location.label())
                        && Math.abs(existing.latitude() - latitude) < 0.0001 && Math.abs(existing.longitude() - longitude) < 0.0001)) matches.add(location);
            }
            matches.sort(Comparator.comparing(Location::approximate));
            // Prefer an actual house over whole-road suggestions, but preserve distinct house matches.
            if (matches.stream().anyMatch(location -> !location.approximate())) matches.removeIf(Location::approximate);
            if (storeLatitude != null) matches.sort(Comparator.comparingDouble(location -> Math.hypot(
                    location.latitude() - storeLatitude, (location.longitude() - storeLongitude) * Math.cos(Math.toRadians(storeLatitude)))));
            return List.copyOf(matches);
    }

    private static boolean nameMatches(String query, String name, JsonNode properties) {
        String expected = normalize(query), actual = normalize(name);
        if (actual.isBlank()) return false;
        if (actual.equals(expected)) return true;
        // Parse an omitted comma only if the remaining text really matches result areas.
        return expected.startsWith(actual + " ") && areaConfidence("_, " + expected.substring(actual.length()).trim(), properties) == 1;
    }

    public Location reverse(Double latitude, Double longitude) {
        CoordinateParser.validate(latitude, longitude);
        var point = new CoordinateParser.Point(latitude, longitude);
        String key = "reverse:" + point.label();
        List<Location> cached = cached(key);
        if (cached != null) return new Location(latitude, longitude, cached.get(0).label(), cached.get(0).approximate());
        try {
            String path = endpoint.getPath().replaceFirst("/?api/?$", "/reverse");
            if (path.equals(endpoint.getPath())) path = endpoint.resolve("reverse").getPath();
            URI reverseEndpoint = new URI(endpoint.getScheme(), endpoint.getAuthority(), path, null, null);
            JsonNode features = get(reverseEndpoint, "lat=" + latitude + "&lon=" + longitude + "&radius=0.05&limit=1");
            for (JsonNode feature : features) {
                JsonNode properties = feature.path("properties"), coordinates = feature.path("geometry").path("coordinates");
                if (!"VN".equalsIgnoreCase(properties.path("countrycode").asText()) || !coordinates.isArray() || coordinates.size() < 2) continue;
                double foundLat = coordinates.get(1).asDouble(Double.NaN), foundLon = coordinates.get(0).asDouble(Double.NaN);
                double latMeters = (foundLat - latitude) * 111200, lonMeters = (foundLon - longitude) * 111200 * Math.cos(Math.toRadians(latitude));
                if (!Double.isFinite(latMeters) || !Double.isFinite(lonMeters) || Math.hypot(latMeters, lonMeters) > 75) continue;
                LinkedHashSet<String> parts = new LinkedHashSet<>();
                String street = (properties.path("housenumber").asText() + " " + properties.path("street").asText()).trim();
                if (!street.isBlank()) parts.add(street);
                for (String field : new String[]{"name", "locality", "district", "county", "city", "state"}) {
                    String value = properties.path(field).asText();
                    if (!value.isBlank()) parts.add(value);
                }
                if (parts.isEmpty()) continue;
                // The returned name describes nearby map data; keep the customer's precise pin.
                Location location = new Location(latitude, longitude, String.join(", ", parts), true);
                remember(key, List.of(location));
                return location;
            }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        catch (Exception ignored) { /* Reverse lookup is optional: coordinates remain usable. */ }
        return new Location(latitude, longitude, point.label(), true);
    }

    private JsonNode get(URI base, String parameters) throws Exception {
        // Keep use of the public demo modest even when several customers search at once.
        synchronized (this) {
            long delay = 1100 - (System.currentTimeMillis() - lastRequestAt);
            if (delay > 0) Thread.sleep(delay);
            lastRequestAt = System.currentTimeMillis();
        }
        URI uri = URI.create(base + (base.toString().contains("?") ? "&" : "?") + parameters);
        var response = client.get(uri, "application/json", 262144);
        if (response.status() != 200) throw unavailable();
        JsonNode features = json.readTree(response.body()).path("features");
        if (!features.isArray()) throw unavailable();
        return features;
    }
    @Override
    public void close() throws IOException { client.close(); }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private synchronized List<Location> cached(String key) {
        Cached value = cache.get(key);
        return value != null && value.expiresAt() > System.currentTimeMillis() ? value.locations() : null;
    }
    private synchronized void remember(String key, List<Location> locations) {
        if (cache.size() >= 128) cache.remove(cache.keySet().iterator().next());
        long lifetime = (locations.isEmpty() ? Duration.ofMinutes(2) : Duration.ofHours(6)).toMillis();
        cache.put(key, new Cached(List.copyOf(locations), System.currentTimeMillis() + lifetime));
    }

    private static String normalizeInput(String address) {
        return Normalizer.normalize(address, Normalizer.Form.NFKC)
                .replaceAll("[\\p{Z}\\s]+", " ").replaceAll("\\p{Cf}", "").trim()
                .replaceAll("[;，]", ",")
                .replaceFirst("(?iu)^(?:số|so)(?:\\s+(?:nhà|nha))?\\s*[:.]?\\s*(?=\\d)", "")
                .replaceAll("(?<=\\d)\\s*/\\s*(?=\\d)", "/")
                .replaceFirst("^(\\d+)\\s+([A-Za-z])(?=\\s)", "$1$2")
                // Recover omitted commas when an explicit administrative prefix follows the street.
                .replaceAll("(?iu)\\s+(?=(?:[pq]\\.?\\s*\\d|p\\.|q\\.|tp\\.|tp\\s*hcm\\b|tphcm\\b|tx\\.|h\\.|phường |phuong |quận |quan |thành phố |thanh pho |huyện |huyen |tỉnh |tinh |thị xã |thi xa ))", ", ")
                .replaceAll("(?iu)(?<![\\p{L}\\d])(?:tp\\.?\\s*hcm|tphcm|hcmc)(?![\\p{L}\\d])", "Thành phố Hồ Chí Minh")
                .replaceAll("(?iu)(?<![\\p{L}\\d])tp\\.\\s*", "Thành phố ")
                .replaceAll("(?iu)(?<![\\p{L}\\d])tx\\.\\s*", "Thị xã ")
                .replaceAll("(?iu)(?<![\\p{L}\\d])q\\.?\\s*(?=\\d)", "Quận ")
                .replaceAll("(?iu)(?<![\\p{L}\\d])p\\.?\\s*(?=\\d)", "Phường ")
                .replaceAll("(?iu)(?<![\\p{L}\\d])q\\.\\s*", "Quận ")
                .replaceAll("(?iu)(?<![\\p{L}\\d])p\\.\\s*", "Phường ")
                .replaceAll("(?iu)(?<![\\p{L}\\d])h\\.\\s*", "Huyện ")
                .replaceAll("(?iu)(?<![\\p{L}\\d])(?:đ|d)\\.\\s*", "Đường ")
                .replaceAll("(?iu)(?<=,)\\s*(?:sài gòn|sai gon|hcm)\\s*(?=,|$)", " Thành phố Hồ Chí Minh")
                .replaceAll("\\s*,\\s*", ", ").replaceAll("(?:,\\s*){2,}", ", ").replaceAll(",\\s*$", "").trim();
    }

    private static String fold(String value) {
        return Normalizer.normalize(value.toLowerCase(Locale.ROOT).replace('đ', 'd'), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }

    private static String normalize(String value) {
        return fold(value).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\bduong\\b", "")
                .replaceAll("\\s+", " ").trim();
    }

    private static String normalizeArea(String value) {
        return normalize(value).replaceAll("\\b(?:ho chi minh city|sai gon|hcmc|hcm)\\b", "ho chi minh")
                .replaceAll("\\b(?:vietnam|vn)\\b", "viet nam")
                .replaceAll("\\b(?:thanh pho|phuong|quan|huyen|tinh|thi xa|xa|tp)\\b", "")
                .replaceAll("\\b\\d{5,6}\\b", "").replaceAll("\\s+", " ").trim();
    }
    // Missing former ward/district names can be suggested for manual confirmation, never auto-quoted.
    // Explicitly conflicting cities or administrative names remain rejected.
    private static int areaConfidence(String query, JsonNode properties) {
        StringBuilder area = new StringBuilder();
        Map<String, String> typedAreas = new LinkedHashMap<>();
        for (String field : new String[]{"locality", "suburb", "district", "county", "city", "state", "country"}) {
            String value = properties.path(field).asText();
            area.append(' ').append(normalizeArea(value)).append(' ');
            String prefix = administrativePrefix(value);
            if (!prefix.isEmpty()) typedAreas.merge(prefix, " " + normalizeArea(value) + " ", (first, next) -> first + next);
        }
        String actual = area.toString().replaceAll("\\s+", " ");
        String[] parts = query.split(",");
        int confidence = 1;
        for (int index = 1; index < parts.length; index++) {
            String expected = normalizeArea(parts[index]);
            if (expected.isEmpty()) continue;
            String prefix = administrativePrefix(parts[index]);
            if (!prefix.isEmpty() && typedAreas.containsKey(prefix)) {
                if (!typedAreas.get(prefix).contains(" " + expected + " ")) return -1;
                continue;
            }
            // Ward 1 / Khu phố 1 cannot prove that a result is in Quận 1.
            if (actual.contains(" " + expected + " ") && !(expected.matches("\\d+") && !prefix.isEmpty())) continue;
            if (prefix.isEmpty()) return -1;
            confidence = 0;
        }
        return confidence;
    }
    private static String administrativePrefix(String value) {
        Matcher prefix = Pattern.compile("^(phuong|quan|huyen|thi xa|xa)\\s+").matcher(normalize(value));
        return prefix.find() ? prefix.group(1) : "";
    }
    private static BadRequestException notFound() {
        return new BadRequestException(ErrorCode.DELIVERY_LOCATION_REQUIRED,
                "Chưa tìm được vị trí chính xác từ địa chỉ. Vui lòng bổ sung địa chỉ hoặc ghim điểm nhận trên bản đồ.");
    }
    private static BadRequestException unavailable() {
        return new BadRequestException(ErrorCode.BAD_REQUEST,
                "Chưa tra được địa chỉ lúc này. Bạn có thể ghim điểm nhận trên bản đồ để tiếp tục.");
    }
}
