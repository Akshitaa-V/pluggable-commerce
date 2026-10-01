package io.github.akshitaav.commerce.catalog;

import io.github.akshitaav.commerce.json.JsonWritable;

import java.util.LinkedHashMap;
import java.util.Map;

/** A sellable product. Prices are integer cents to avoid floating-point rounding. */
public record Product(String sku, String name, long unitPriceCents) implements JsonWritable {

    public Product {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (unitPriceCents < 0) {
            throw new IllegalArgumentException("price must not be negative");
        }
    }

    @Override
    public Object toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("sku", sku);
        json.put("name", name);
        json.put("unitPriceCents", unitPriceCents);
        return json;
    }
}
