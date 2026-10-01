package io.github.akshitaav.commerce.pricing;

import io.github.akshitaav.commerce.json.JsonWritable;

import java.util.LinkedHashMap;
import java.util.Map;

/** A cart line after the catalog price has been applied. */
public record PricedLine(String sku, String name, long quantity, long unitPriceCents,
                         long lineTotalCents) implements JsonWritable {

    @Override
    public Object toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("sku", sku);
        json.put("name", name);
        json.put("quantity", quantity);
        json.put("unitPriceCents", unitPriceCents);
        json.put("lineTotalCents", lineTotalCents);
        return json;
    }
}
