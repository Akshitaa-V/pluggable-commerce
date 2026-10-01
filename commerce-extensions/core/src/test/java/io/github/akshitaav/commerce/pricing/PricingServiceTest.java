package io.github.akshitaav.commerce.pricing;

import io.github.akshitaav.commerce.catalog.Catalog;
import io.github.akshitaav.commerce.catalog.Product;
import io.github.akshitaav.commerce.extensions.Extension;
import io.github.akshitaav.commerce.extensions.ExtensionException;
import io.github.akshitaav.commerce.extensions.ExtensionRegistry;
import io.github.akshitaav.commerce.extensions.FailurePolicy;
import io.github.akshitaav.commerce.extensions.Hook;
import io.github.akshitaav.commerce.json.Json;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PricingServiceTest {

    private ExtensionRegistry registry;
    /** Response text per extension name; "FAIL" makes the fake invoker throw. */
    private Map<String, String> responses;
    private PricingService pricing;

    @BeforeEach
    void setUp() {
        Catalog catalog = new Catalog(List.of(
                new Product("A", "Item A", 1000),
                new Product("B", "Item B", 250)));
        registry = new ExtensionRegistry();
        responses = new HashMap<>();
        pricing = new PricingService(catalog, registry, (ext, payload) -> {
            String body = responses.get(ext.name());
            if ("FAIL".equals(body)) {
                throw new ExtensionException("timed out after 100 ms");
            }
            return Json.parseObject(body);
        });
    }

    private Extension add(String name, String response) {
        responses.put(name, response);
        return registry.register(name, Hook.CART_PRICE,
                URI.create("http://ext/" + name.replace(' ', '-')), 100, FailurePolicy.SKIP);
    }

    @Test
    void withoutExtensionsTheTotalIsTheSubtotal() {
        Quote quote = pricing.quote(List.of(new CartLine("A", 2), new CartLine("B", 1)));
        assertEquals(2250, quote.subtotalCents());
        assertEquals(2250, quote.totalCents());
        assertTrue(quote.adjustments().isEmpty());
        assertTrue(quote.warnings().isEmpty());
    }

    @Test
    void mergesRepeatedSkus() {
        Quote quote = pricing.quote(List.of(new CartLine("A", 1), new CartLine("A", 2)));
        assertEquals(1, quote.lines().size());
        assertEquals(3, quote.lines().get(0).quantity());
        assertEquals(3000, quote.totalCents());
    }

    @Test
    void appliesLineAndCartAdjustmentsFromExtensions() {
        add("Bulk", "{\"adjustments\":[{\"sku\":\"A\",\"label\":\"Bulk 10%\",\"amountCents\":-200}]}");
        add("Fee", "{\"adjustments\":[{\"label\":\"Packaging fee\",\"amountCents\":150}]}");

        Quote quote = pricing.quote(List.of(new CartLine("A", 2)));

        assertEquals(2000, quote.subtotalCents());
        assertEquals(2, quote.adjustments().size());
        assertEquals("Bulk", quote.adjustments().get(0).extensionName());
        assertEquals(1950, quote.totalCents());
        assertTrue(quote.warnings().isEmpty());
    }

    @Test
    void failingExtensionIsSkippedAndOthersStillApply() {
        add("Broken", "FAIL");
        add("Good", "{\"adjustments\":[{\"label\":\"Welcome\",\"amountCents\":-100}]}");

        Quote quote = pricing.quote(List.of(new CartLine("B", 4)));

        assertEquals(900, quote.totalCents());
        assertEquals(1, quote.warnings().size());
        assertTrue(quote.warnings().get(0).contains("'Broken' timed out"));
    }

    @Test
    void invalidResponseIsRejectedAsAWhole() {
        // The second adjustment points at a sku that is not in the cart,
        // so the valid first one must not be applied either.
        add("Sloppy", "{\"adjustments\":["
                + "{\"sku\":\"A\",\"label\":\"ok\",\"amountCents\":-100},"
                + "{\"sku\":\"B\",\"label\":\"wrong sku\",\"amountCents\":-50}]}");

        Quote quote = pricing.quote(List.of(new CartLine("A", 1)));

        assertEquals(1000, quote.totalCents());
        assertTrue(quote.adjustments().isEmpty());
        assertTrue(quote.warnings().get(0).contains("not in the cart"));
    }

    @Test
    void lineDiscountCannotExceedWhatIsLeftOfTheLine() {
        add("First", "{\"adjustments\":[{\"sku\":\"B\",\"label\":\"half\",\"amountCents\":-125}]}");
        add("Second", "{\"adjustments\":[{\"sku\":\"B\",\"label\":\"too much\",\"amountCents\":-200}]}");

        Quote quote = pricing.quote(List.of(new CartLine("B", 1)));

        assertEquals(125, quote.totalCents());
        assertEquals(1, quote.adjustments().size());
        assertTrue(quote.warnings().get(0).contains("larger than the line value"));
    }

    @Test
    void cartDiscountsNeverMakeTheTotalNegative() {
        add("Generous", "{\"adjustments\":[{\"label\":\"Voucher\",\"amountCents\":-5000}]}");

        Quote quote = pricing.quote(List.of(new CartLine("B", 1)));

        assertEquals(0, quote.totalCents());
        assertTrue(quote.warnings().get(0).contains("total was set to 0"));
    }

    @Test
    void responseWithoutAdjustmentsArrayIsInvalid() {
        add("Odd", "{\"discount\":-100}");
        Quote quote = pricing.quote(List.of(new CartLine("A", 1)));
        assertEquals(1000, quote.totalCents());
        assertTrue(quote.warnings().get(0).contains("returned invalid data"));
    }

    @Test
    void disabledExtensionsAreNotCalled() {
        Extension ext = add("Off", "FAIL");
        registry.setEnabled(ext.id(), false);
        Quote quote = pricing.quote(List.of(new CartLine("A", 1)));
        assertTrue(quote.warnings().isEmpty());
    }

    @Test
    void rejectsEmptyCartsAndUnknownProducts() {
        assertThrows(IllegalArgumentException.class, () -> pricing.quote(List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> pricing.quote(List.of(new CartLine("NOPE", 1))));
        assertThrows(IllegalArgumentException.class, () -> new CartLine("A", 0));
    }
}
