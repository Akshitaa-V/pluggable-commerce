package io.github.akshitaav.commerce.pricing;

import io.github.akshitaav.commerce.json.JsonWritable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A price change contributed by an extension. Negative amounts are discounts, positive
 * amounts are fees. {@code sku} is null for an adjustment on the whole cart.
 */
public record Adjustment(String extensionId, String extensionName, String sku, String label,
                         long amountCents) implements JsonWritable {

    @Override
    public Object toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("extensionId", extensionId);
        json.put("extensionName", extensionName);
        json.put("sku", sku);
        json.put("label", label);
        json.put("amountCents", amountCents);
        return json;
    }
}
