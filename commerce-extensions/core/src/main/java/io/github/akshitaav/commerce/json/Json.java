package io.github.akshitaav.commerce.json;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader and writer so the core service runs on the JDK alone.
 *
 * <p>Parsed values map to: {@code Map<String,Object>} (objects, insertion order kept),
 * {@code List<Object>} (arrays), {@code String}, {@code Long} (integers),
 * {@code Double} (fractions), {@code Boolean} and {@code null}.
 */
public final class Json {

    private Json() {
    }

    public static Object parse(String text) {
        if (text == null) {
            throw new JsonException("No JSON input");
        }
        Parser parser = new Parser(text);
        parser.skipWhitespace();
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw parser.error("Unexpected trailing characters");
        }
        return value;
    }

    /** Parses text that must be a JSON object. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object value = parse(text);
        if (!(value instanceof Map)) {
            throw new JsonException("Expected a JSON object");
        }
        return (Map<String, Object>) value;
    }

    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        writeValue(value, out);
        return out.toString();
    }

    private static void writeValue(Object value, StringBuilder out) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String s) {
            writeString(s, out);
        } else if (value instanceof Boolean b) {
            out.append(b);
        } else if (value instanceof Double d) {
            if (d.isNaN() || d.isInfinite()) {
                throw new JsonException("NaN and Infinity are not valid JSON");
            }
            out.append(BigDecimal.valueOf(d).stripTrailingZeros().toPlainString());
        } else if (value instanceof Float f) {
            writeValue(f.doubleValue(), out);
        } else if (value instanceof Number n) {
            out.append(n);
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            Iterator<? extends Map.Entry<?, ?>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<?, ?> entry = it.next();
                writeString(String.valueOf(entry.getKey()), out);
                out.append(':');
                writeValue(entry.getValue(), out);
                if (it.hasNext()) {
                    out.append(',');
                }
            }
            out.append('}');
        } else if (value instanceof Iterable<?> items) {
            out.append('[');
            Iterator<?> it = items.iterator();
            while (it.hasNext()) {
                writeValue(it.next(), out);
                if (it.hasNext()) {
                    out.append(',');
                }
            }
            out.append(']');
        } else if (value instanceof JsonWritable writable) {
            writeValue(writable.toJson(), out);
        } else if (value instanceof Enum<?> e) {
            writeString(e.name(), out);
        } else {
            throw new JsonException("Cannot write " + value.getClass().getSimpleName() + " as JSON");
        }
    }

    private static void writeString(String s, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    private static final class Parser {
        private static final int MAX_DEPTH = 64;
        private final String text;
        private int pos;
        private int depth;

        Parser(String text) {
            this.text = text;
        }

        boolean atEnd() {
            return pos >= text.length();
        }

        JsonException error(String message) {
            return new JsonException(message + " at position " + pos);
        }

        void skipWhitespace() {
            while (!atEnd()) {
                char c = text.charAt(pos);
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        Object readValue() {
            if (atEnd()) {
                throw error("Unexpected end of input");
            }
            char c = text.charAt(pos);
            return switch (c) {
                case '{' -> readObject();
                case '[' -> readArray();
                case '"' -> readString();
                case 't' -> readLiteral("true", Boolean.TRUE);
                case 'f' -> readLiteral("false", Boolean.FALSE);
                case 'n' -> readLiteral("null", null);
                default -> {
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        yield readNumber();
                    }
                    throw error("Unexpected character '" + c + "'");
                }
            };
        }

        private void enter() {
            if (++depth > MAX_DEPTH) {
                throw error("JSON nested too deeply");
            }
        }

        private Map<String, Object> readObject() {
            enter();
            pos++; // {
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (peek('}')) {
                pos++;
                depth--;
                return map;
            }
            while (true) {
                skipWhitespace();
                if (!peek('"')) {
                    throw error("Expected a string key");
                }
                String key = readString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                map.put(key, readValue());
                skipWhitespace();
                if (peek(',')) {
                    pos++;
                } else if (peek('}')) {
                    pos++;
                    depth--;
                    return map;
                } else {
                    throw error("Expected ',' or '}'");
                }
            }
        }

        private List<Object> readArray() {
            enter();
            pos++; // [
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (peek(']')) {
                pos++;
                depth--;
                return list;
            }
            while (true) {
                skipWhitespace();
                list.add(readValue());
                skipWhitespace();
                if (peek(',')) {
                    pos++;
                } else if (peek(']')) {
                    pos++;
                    depth--;
                    return list;
                } else {
                    throw error("Expected ',' or ']'");
                }
            }
        }

        private String readString() {
            pos++; // opening quote
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (atEnd()) {
                    throw error("Unterminated string");
                }
                char c = text.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (atEnd()) {
                        throw error("Unterminated escape");
                    }
                    char e = text.charAt(pos++);
                    switch (e) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'u' -> {
                            if (pos + 4 > text.length()) {
                                throw error("Incomplete unicode escape");
                            }
                            try {
                                sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                            } catch (NumberFormatException ex) {
                                throw error("Invalid unicode escape");
                            }
                            pos += 4;
                        }
                        default -> throw error("Invalid escape '\\" + e + "'");
                    }
                } else if (c < 0x20) {
                    throw error("Control character in string");
                } else {
                    sb.append(c);
                }
            }
        }

        private Object readNumber() {
            int start = pos;
            if (peek('-')) {
                pos++;
            }
            boolean fraction = false;
            while (!atEnd()) {
                char c = text.charAt(pos);
                if (c >= '0' && c <= '9') {
                    pos++;
                } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    fraction = true;
                    pos++;
                } else {
                    break;
                }
            }
            String token = text.substring(start, pos);
            try {
                if (!fraction) {
                    return Long.parseLong(token);
                }
                return Double.parseDouble(token);
            } catch (NumberFormatException ex) {
                throw new JsonException("Invalid number '" + token + "' at position " + start);
            }
        }

        private Object readLiteral(String literal, Object value) {
            if (!text.startsWith(literal, pos)) {
                throw error("Invalid literal");
            }
            pos += literal.length();
            return value;
        }

        private boolean peek(char c) {
            return !atEnd() && text.charAt(pos) == c;
        }

        private void expect(char c) {
            if (!peek(c)) {
                throw error("Expected '" + c + "'");
            }
            pos++;
        }
    }
}
