package com.foodordering.utils;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Tiện ích chuyển đổi đối tượng sang JSON và phân tích JSON cơ bản không phụ thuộc thư viện ngoài.
 * Giúp API hoạt động trơn tru trong mọi môi trường servlet container.
 */
public class JsonUtils {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private JsonUtils() {
    }

    /**
     * Chuyển đổi bất kỳ đối tượng nào thành chuỗi JSON hợp lệ.
     */
    public static String toJson(Object obj) {
        if (obj == null) {
            return "null";
        }
        if (obj instanceof String) {
            return "\"" + escapeString((String) obj) + "\"";
        }
        if (obj instanceof Number || obj instanceof Boolean) {
            return obj.toString();
        }
        if (obj instanceof Enum<?>) {
            return "\"" + ((Enum<?>) obj).name() + "\"";
        }
        if (obj instanceof LocalDateTime) {
            return "\"" + ((LocalDateTime) obj).format(DATE_TIME_FORMATTER) + "\"";
        }
        if (obj instanceof LocalDate) {
            return "\"" + obj.toString() + "\"";
        }
        if (obj instanceof Collection<?>) {
            Collection<?> col = (Collection<?>) obj;
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : col) {
                if (!first) sb.append(",");
                sb.append(toJson(item));
                first = false;
            }
            sb.append("]");
            return sb.toString();
        }
        if (obj instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) obj;
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(escapeString(String.valueOf(entry.getKey()))).append("\":");
                sb.append(toJson(entry.getValue()));
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }

        // Tự động phân tích các trường của đối tượng DTO / POJO
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        Class<?> currentClass = obj.getClass();
        List<Field> allFields = new ArrayList<>();
        while (currentClass != null && currentClass != Object.class) {
            Collections.addAll(allFields, currentClass.getDeclaredFields());
            currentClass = currentClass.getSuperclass();
        }

        for (Field field : allFields) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true);
            try {
                Object value = field.get(obj);
                if (!first) sb.append(",");
                sb.append("\"").append(field.getName()).append("\":");
                sb.append(toJson(value));
                first = false;
            } catch (IllegalAccessException ignored) {
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private static String escapeString(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    /**
     * Phân tích một chuỗi JSON dạng object phẳng thành Map<String, Object>.
     * Hỗ trợ đọc các request JSON gửi lên từ client.
     */
    public static Map<String, Object> parseJsonObject(String json) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (json == null) return result;
        String trimmed = json.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return result;
        }

        // Đọc các cặp key - value
        int index = 1;
        int len = trimmed.length() - 1;

        while (index < len) {
            // Bỏ khoảng trắng
            while (index < len && Character.isWhitespace(trimmed.charAt(index))) index++;
            if (index >= len) break;

            if (trimmed.charAt(index) == ',') {
                index++;
                continue;
            }

            // Đọc key
            if (trimmed.charAt(index) != '"') break;
            index++;
            int keyStart = index;
            while (index < len && trimmed.charAt(index) != '"') {
                if (trimmed.charAt(index) == '\\') index++;
                index++;
            }
            String key = trimmed.substring(keyStart, index);
            index++; // Bỏ dấu đóng ngoặc kép

            // Bỏ khoảng trắng tới dấu hai chấm
            while (index < len && trimmed.charAt(index) != ':') index++;
            if (index < len && trimmed.charAt(index) == ':') index++;
            while (index < len && Character.isWhitespace(trimmed.charAt(index))) index++;

            // Đọc value
            if (index >= len) break;
            char firstChar = trimmed.charAt(index);
            if (firstChar == '"') {
                // String
                index++;
                int valStart = index;
                while (index < len && trimmed.charAt(index) != '"') {
                    if (trimmed.charAt(index) == '\\') index++;
                    index++;
                }
                String val = trimmed.substring(valStart, index);
                result.put(key, val);
                index++;
            } else if (firstChar == '[') {
                // Array (ví dụ danh sách optionIds dạng chuỗi)
                index++;
                List<String> list = new ArrayList<>();
                while (index < len && trimmed.charAt(index) != ']') {
                    while (index < len && (Character.isWhitespace(trimmed.charAt(index)) || trimmed.charAt(index) == ',')) index++;
                    if (index < len && trimmed.charAt(index) == '"') {
                        index++;
                        int itemStart = index;
                        while (index < len && trimmed.charAt(index) != '"') {
                            if (trimmed.charAt(index) == '\\') index++;
                            index++;
                        }
                        list.add(trimmed.substring(itemStart, index));
                        index++;
                    } else if (index < len && trimmed.charAt(index) != ']') {
                        index++;
                    }
                }
                if (index < len && trimmed.charAt(index) == ']') index++;
                result.put(key, list);
            } else {
                // Number / boolean / null
                int valStart = index;
                while (index < len && trimmed.charAt(index) != ',' && trimmed.charAt(index) != '}' && !Character.isWhitespace(trimmed.charAt(index))) {
                    index++;
                }
                String token = trimmed.substring(valStart, index).trim();
                if ("true".equalsIgnoreCase(token)) {
                    result.put(key, Boolean.TRUE);
                } else if ("false".equalsIgnoreCase(token)) {
                    result.put(key, Boolean.FALSE);
                } else if ("null".equalsIgnoreCase(token)) {
                    result.put(key, null);
                } else {
                    result.put(key, token);
                }
            }
        }
        return result;
    }

    public static String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString().trim() : null;
    }

    public static Integer getInteger(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return null;
        if (val instanceof Integer) return (Integer) val;
        try {
            return Integer.parseInt(val.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static BigDecimal getBigDecimal(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return null;
        try {
            return new BigDecimal(val.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static List<String> getStringList(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof List<?>) {
            List<String> res = new ArrayList<>();
            for (Object item : (List<?>) val) {
                if (item != null) res.add(item.toString().trim());
            }
            return res;
        }
        return Collections.emptyList();
    }
}
