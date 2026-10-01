package io.github.akshitaav.commerce.pricing;

import io.github.akshitaav.commerce.json.JsonWritable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The priced cart: lines, extension adjustments, final total and any warnings. */
public record Quote(List<PricedLine> lines, long subtotalCents, List<Adjustment> adjustments,
                    long totalCents, List<String> warnings) implements JsonWritable {

    public Quote {
        lines = List.copyOf(lines);
        adjustments = List.copyOf(adjustments);
        warnings = List.copyOf(warnings);
    }

    @Override
    public Object toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("currency", "EUR");
        json.put("lines", lines);
        json.put("subtotalCents", subtotalCents);
        json.put("adjustments", adjustments);
        json.put("totalCents", totalCents);
        json.put("warnings", warnings);
        return json;
    }
}
