package io.github.akshitaav.commerce.json;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonTest {

    @Test
    void parsesNestedObjectsAndArrays() {
        Map<String, Object> obj = Json.parseObject(
                "{ \"a\": 1, \"b\": [true, false, null], \"c\": {\"d\": \"x\"}, \"e\": -2.5 }");
        assertEquals(1L, obj.get("a"));
        assertEquals(List.of(true, false), ((List<?>) obj.get("b")).subList(0, 2));
        assertNull(((List<?>) obj.get("b")).get(2));
        assertEquals("x", ((Map<?, ?>) obj.get("c")).get("d"));
        assertEquals(-2.5, obj.get("e"));
    }

    @Test
    void decodesEscapesAndUnicode() {
        Object value = Json.parse("\"line\\nbreak \\\"quoted\\\" \\u00e4\"");
        assertEquals("line\nbreak \"quoted\" \u00e4", value);
    }

    @Test
    void writesAndReadsBackTheSameValue() {
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("name", "Tab\tand \"quote\"");
        original.put("count", 3L);
        original.put("items", List.of(1L, "two", false));
        original.put("missing", null);
        String text = Json.write(original);
        assertEquals(original, Json.parse(text));
    }

    @Test
    void rejectsMalformedInput() {
        assertThrows(JsonException.class, () -> Json.parse("{\"a\": 1,}"));
        assertThrows(JsonException.class, () -> Json.parse("[1, 2"));
        assertThrows(JsonException.class, () -> Json.parse("\"unterminated"));
        assertThrows(JsonException.class, () -> Json.parse("{} extra"));
        assertThrows(JsonException.class, () -> Json.parse(""));
    }

    @Test
    void rejectsDeeplyNestedInput() {
        String deep = "[".repeat(100) + "]".repeat(100);
        assertThrows(JsonException.class, () -> Json.parse(deep));
    }

    @Test
    void parseObjectRejectsNonObjects() {
        assertThrows(JsonException.class, () -> Json.parseObject("[1]"));
        assertTrue(Json.parseObject("{}").isEmpty());
    }
}
