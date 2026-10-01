package io.github.akshitaav.commerce.json;

/** Thrown when text is not valid JSON or a value cannot be written as JSON. */
public class JsonException extends RuntimeException {
    public JsonException(String message) {
        super(message);
    }
}
