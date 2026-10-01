package io.github.akshitaav.commerce.pricing;

/** One requested cart line: which product and how many. */
public record CartLine(String sku, long quantity) {
    public static final long MAX_QUANTITY = 999;

    public CartLine {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("Each line needs a sku");
        }
        if (quantity < 1 || quantity > MAX_QUANTITY) {
            throw new IllegalArgumentException("quantity must be between 1 and " + MAX_QUANTITY);
        }
    }
}
