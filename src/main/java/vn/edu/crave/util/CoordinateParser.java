package vn.edu.crave.util;

import vn.edu.crave.exception.BadRequestException;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Decimal latitude/longitude pasted from a map. No network lookup or inferred destination. */
public final class CoordinateParser {
    public record Point(double latitude, double longitude) {
        public String label() { return String.format(Locale.ROOT, "%.6f, %.6f", latitude, longitude); }
    }
    private static final String NUMBER = "[+-]?(?:\\d+(?:\\.\\d+)?|\\.\\d+)";
    private static final Pattern PAIR = Pattern.compile("^(" + NUMBER + ")(?:\\s*[,;]\\s*|\\s+)(" + NUMBER + ")$");
    private CoordinateParser() {}

    public static Optional<Point> parse(String input) {
        if (input == null || input.isBlank()) return Optional.empty();
        var embedded = Pattern.compile("\\((" + NUMBER + "\\s*,\\s*" + NUMBER + ")\\)\\s*$").matcher(input);
        if (embedded.find()) return parse(embedded.group(1));
        String text = Normalizer.normalize(input, Normalizer.Form.NFKC).trim()
                .replaceAll("[\\p{Z}\\s]+", " ").replaceAll("\\p{Cf}", "")
                .replaceFirst("(?i)^geo:\\s*", "")
                .replaceAll("(?iu)(?:latitude|longitude|lat|lng|lon|vĩ\\s*độ|vi\\s*do|kinh\\s*độ|kinh\\s*do)\\s*[:=]\\s*", "")
                .replaceAll("^[\\[(]\\s*|\\s*[\\])]$", "").trim();
        // Vietnamese decimal commas are unambiguous when the pair uses a semicolon.
        if (text.contains(";")) text = text.replace(',', '.');
        var match = PAIR.matcher(text);
        if (!match.matches()) {
            if (text.matches("[+\\-\\d.,;\\s]+") && text.matches(".*\\d.*") && text.matches(".*[,;].*")) throw invalid();
            return Optional.empty();
        }
        double first = Double.parseDouble(match.group(1)), second = Double.parseDouble(match.group(2));
        // Longitude first is accepted only when the first value cannot be a latitude.
        if (Math.abs(first) > 90 && Math.abs(first) <= 180 && Math.abs(second) <= 90) {
            double swap = first; first = second; second = swap;
        }
        validate(first, second);
        return Optional.of(new Point(first, second));
    }

    public static void validate(Double latitude, Double longitude) {
        if (latitude == null || longitude == null || !Double.isFinite(latitude) || !Double.isFinite(longitude)
                || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) throw invalid();
    }
    private static BadRequestException invalid() {
        return new BadRequestException("Tọa độ không hợp lệ. Nhập vĩ độ, kinh độ, ví dụ: 10.852791, 106.772558.");
    }
}
