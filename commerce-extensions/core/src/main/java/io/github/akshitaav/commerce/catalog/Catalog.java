package io.github.akshitaav.commerce.catalog;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Read-only, in-memory product catalog. */
public final class Catalog {

    private final Map<String, Product> products = new LinkedHashMap<>();

    public Catalog(List<Product> items) {
        for (Product p : items) {
            if (products.putIfAbsent(p.sku(), p) != null) {
                throw new IllegalArgumentException("Duplicate sku " + p.sku());
            }
        }
    }

    /** A small demo catalog so the service is usable straight after start. */
    public static Catalog demo() {
        return new Catalog(List.of(
                new Product("COFFEE-1KG", "Espresso beans 1 kg", 2490),
                new Product("MUG-CER", "Ceramic mug", 1290),
                new Product("GRINDER-H", "Hand grinder", 5990),
                new Product("FILTER-100", "Paper filters (100)", 490)));
    }

    public Optional<Product> find(String sku) {
        return Optional.ofNullable(products.get(sku));
    }

    public Collection<Product> all() {
        return Collections.unmodifiableCollection(products.values());
    }
}
