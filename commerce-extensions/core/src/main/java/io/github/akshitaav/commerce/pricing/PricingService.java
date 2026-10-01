package io.github.akshitaav.commerce.pricing;

import io.github.akshitaav.commerce.catalog.Catalog;
import io.github.akshitaav.commerce.catalog.Product;
import io.github.akshitaav.commerce.extensions.Extension;
import io.github.akshitaav.commerce.extensions.ExtensionException;
import io.github.akshitaav.commerce.extensions.ExtensionInvoker;
import io.github.akshitaav.commerce.extensions.ExtensionRegistry;
import io.github.akshitaav.commerce.extensions.Hook;
import io.github.akshitaav.commerce.json.Fields;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Prices a cart, then lets every enabled {@code cart.price} extension add adjustments.
 *
 * <p>The core never trusts extension output blindly. An extension's response is accepted
 * as a whole or rejected as a whole: if any adjustment is invalid (unknown sku, a discount
 * larger than what is left of the line, a missing label), none of its adjustments apply and
 * the quote carries a warning instead. A failing or slow extension never breaks pricing.
 */
public final class PricingService {

    public static final int MAX_LINES = 50;
    private static final int MAX_ADJUSTMENTS_PER_EXTENSION = 20;
    private static final int MAX_LABEL_LENGTH = 80;
    private static final long MAX_ABS_AMOUNT_CENTS = 10_000_000; // 100,000 EUR

    private final Catalog catalog;
    private final ExtensionRegistry registry;
    private final ExtensionInvoker invoker;

    public PricingService(Catalog catalog, ExtensionRegistry registry, ExtensionInvoker invoker) {
        this.catalog = catalog;
        this.registry = registry;
        this.invoker = invoker;
    }

    public Quote quote(List<CartLine> requested) {
        List<PricedLine> lines = priceLines(requested);
        long subtotal = lines.stream().mapToLong(PricedLine::lineTotalCents).sum();

        Map<String, Long> remainingPerLine = new HashMap<>();
        lines.forEach(l -> remainingPerLine.put(l.sku(), l.lineTotalCents()));
        long runningTotal = subtotal;

        List<Adjustment> applied = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (Extension extension : registry.activeFor(Hook.CART_PRICE)) {
            Map<String, Object> payload = payload(lines, subtotal, runningTotal);
            List<Adjustment> proposed;
            try {
                Map<String, Object> response = invoker.invoke(extension, payload);
                proposed = readAdjustments(extension, response, remainingPerLine);
            } catch (ExtensionException e) {
                warnings.add("Extension '" + extension.name() + "' " + e.getMessage()
                        + "; its adjustments were skipped.");
                continue;
            } catch (IllegalArgumentException e) {
                warnings.add("Extension '" + extension.name() + "' returned invalid data ("
                        + e.getMessage() + "); its adjustments were skipped.");
                continue;
            }
            for (Adjustment a : proposed) {
                if (a.sku() != null) {
                    remainingPerLine.merge(a.sku(), a.amountCents(), Long::sum);
                }
                runningTotal += a.amountCents();
                applied.add(a);
            }
        }

        long total = runningTotal;
        if (total < 0) {
            warnings.add("Discounts exceeded the cart value, so the total was set to 0.");
            total = 0;
        }
        return new Quote(lines, subtotal, applied, total, warnings);
    }

    private List<PricedLine> priceLines(List<CartLine> requested) {
        if (requested == null || requested.isEmpty()) {
            throw new IllegalArgumentException("The cart is empty");
        }
        if (requested.size() > MAX_LINES) {
            throw new IllegalArgumentException("A cart can have at most " + MAX_LINES + " lines");
        }
        // Merge repeated skus so each product appears once.
        Map<String, Long> quantities = new LinkedHashMap<>();
        for (CartLine line : requested) {
            quantities.merge(line.sku(), line.quantity(), Long::sum);
        }
        List<PricedLine> lines = new ArrayList<>();
        for (Map.Entry<String, Long> entry : quantities.entrySet()) {
            Product product = catalog.find(entry.getKey()).orElseThrow(() ->
                    new IllegalArgumentException("Unknown product '" + entry.getKey() + "'"));
            long quantity = entry.getValue();
            if (quantity > CartLine.MAX_QUANTITY) {
                throw new IllegalArgumentException("quantity for " + product.sku()
                        + " must not exceed " + CartLine.MAX_QUANTITY);
            }
            lines.add(new PricedLine(product.sku(), product.name(), quantity,
                    product.unitPriceCents(), product.unitPriceCents() * quantity));
        }
        return lines;
    }

    private static Map<String, Object> payload(List<PricedLine> lines, long subtotal, long total) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("hook", Hook.CART_PRICE.id());
        payload.put("currency", "EUR");
        payload.put("lines", lines);
        payload.put("subtotalCents", subtotal);
        payload.put("currentTotalCents", total);
        return payload;
    }

    /** Parses and validates one extension's response. Throws if anything is wrong. */
    private static List<Adjustment> readAdjustments(Extension extension,
                                                    Map<String, Object> response,
                                                    Map<String, Long> remainingPerLine) {
        List<Object> raw = Fields.requireList(response, "adjustments");
        if (raw.size() > MAX_ADJUSTMENTS_PER_EXTENSION) {
            throw new IllegalArgumentException("more than " + MAX_ADJUSTMENTS_PER_EXTENSION
                    + " adjustments");
        }
        Map<String, Long> remaining = new HashMap<>(remainingPerLine);
        List<Adjustment> result = new ArrayList<>();
        for (Object item : raw) {
            Map<String, Object> obj = Fields.asObject(item, "each adjustment");
            String label = Fields.requireString(obj, "label");
            if (label.length() > MAX_LABEL_LENGTH) {
                throw new IllegalArgumentException("label longer than " + MAX_LABEL_LENGTH
                        + " characters");
            }
            long amount = Fields.requireLong(obj, "amountCents");
            if (amount == 0 || Math.abs(amount) > MAX_ABS_AMOUNT_CENTS) {
                throw new IllegalArgumentException("amountCents must be non-zero and at most "
                        + MAX_ABS_AMOUNT_CENTS + " in absolute value");
            }
            String sku = Fields.optionalString(obj, "sku");
            if (sku != null) {
                Long left = remaining.get(sku);
                if (left == null) {
                    throw new IllegalArgumentException("sku '" + sku + "' is not in the cart");
                }
                if (left + amount < 0) {
                    throw new IllegalArgumentException("discount on " + sku
                            + " is larger than the line value");
                }
                remaining.put(sku, left + amount);
            }
            result.add(new Adjustment(extension.id(), extension.name(), sku, label, amount));
        }
        return result;
    }
}
