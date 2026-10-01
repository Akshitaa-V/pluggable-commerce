package io.github.akshitaav.commerce.json;

/** Domain objects implement this to control how they appear in API responses. */
public interface JsonWritable {
    Object toJson();
}
