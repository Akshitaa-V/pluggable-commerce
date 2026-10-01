package io.github.akshitaav.commerce.orders;

import io.github.akshitaav.commerce.json.JsonWritable;
import io.github.akshitaav.commerce.pricing.Quote;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A placed order. The quote is frozen at the moment the order was accepted. */
public record Order(String id, Quote quote, List<String> warnings) implements JsonWritable {

    public Order {
        warnings = List.copyOf(warnings);
    }

    @Override
    public Object toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", id);
        json.put("status", "PLACED");
        json.put("quote", quote);
        json.put("warnings", warnings);
        return json;
    }
}
