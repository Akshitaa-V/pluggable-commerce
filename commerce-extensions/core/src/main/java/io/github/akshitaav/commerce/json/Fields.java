package io.github.akshitaav.commerce.json;

import java.util.List;
import java.util.Map;

/**
 * Typed access to fields of a parsed JSON object, with clear error messages.
 * Used for request bodies and for responses coming back from extensions.
 */
public final class Fields {

    private Fields() {
    }

    public static String requireString(Map<String, Object> obj, String key) {
        Object value = obj.get(key);
        if (!(value instanceof String s) || s.isBlank()) {
            throw new IllegalArgumentException("'" + key + "' must be a non-empty string");
        }
        return s;
    }

    public static String optionalString(Map<String, Object> obj, String key) {
        Object value = obj.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String s)) {
            throw new IllegalArgumentException("'" + key + "' must be a string");
        }
        return s;
    }

    public static long requireLong(Map<String, Object> obj, String key) {
        Object value = obj.get(key);
        if (!(value instanceof Long l)) {
            throw new IllegalArgumentException("'" + key + "' must be an integer");
        }
        return l;
    }

    public static Long optionalLong(Map<String, Object> obj, String key) {
        Object value = obj.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Long l)) {
            throw new IllegalArgumentException("'" + key + "' must be an integer");
        }
        return l;
    }

    public static boolean requireBoolean(Map<String, Object> obj, String key) {
        Object value = obj.get(key);
        if (!(value instanceof Boolean b)) {
            throw new IllegalArgumentException("'" + key + "' must be true or false");
        }
        return b;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> requireList(Map<String, Object> obj, String key) {
        Object value = obj.get(key);
        if (!(value instanceof List<?>)) {
            throw new IllegalArgumentException("'" + key + "' must be an array");
        }
        return (List<Object>) value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asObject(Object value, String what) {
        if (!(value instanceof Map<?, ?>)) {
            throw new IllegalArgumentException(what + " must be a JSON object");
        }
        return (Map<String, Object>) value;
    }
}
