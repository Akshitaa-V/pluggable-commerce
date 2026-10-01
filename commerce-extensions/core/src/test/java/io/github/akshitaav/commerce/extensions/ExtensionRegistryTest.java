package io.github.akshitaav.commerce.extensions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtensionRegistryTest {

    private ExtensionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ExtensionRegistry();
    }

    private static Map<String, Object> body(String name, String hook, String url) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("hook", hook);
        body.put("url", url);
        return body;
    }

    @Test
    void registersWithDefaults() {
        Extension ext = registry.register(body("Bulk discount", "cart.price",
                "http://localhost:9000/hooks/cart-price"));
        assertEquals("ext-1", ext.id());
        assertEquals(Hook.CART_PRICE, ext.hook());
        assertTrue(ext.enabled());
        assertEquals(1000, ext.timeoutMs());
        assertEquals(FailurePolicy.SKIP, ext.failurePolicy());
    }

    @Test
    void rejectsInvalidRegistrations() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.register(body("X", "cart.unknown", "http://a")));
        assertThrows(IllegalArgumentException.class,
                () -> registry.register(body("X", "cart.price", "ftp://host/file")));
        assertThrows(IllegalArgumentException.class,
                () -> registry.register(body("X", "cart.price", "not a url")));
        assertThrows(IllegalArgumentException.class,
                () -> registry.register(body(" ", "cart.price", "http://a")));

        Map<String, Object> slow = body("Slow", "cart.price", "http://a");
        slow.put("timeoutMs", 60_000L);
        assertThrows(IllegalArgumentException.class, () -> registry.register(slow));
    }

    @Test
    void blockPolicyIsOnlyAllowedForValidators() {
        Map<String, Object> pricing = body("P", "cart.price", "http://a");
        pricing.put("failurePolicy", "BLOCK");
        assertThrows(IllegalArgumentException.class, () -> registry.register(pricing));

        Map<String, Object> validator = body("V", "order.validate", "http://a");
        validator.put("failurePolicy", "BLOCK");
        assertEquals(FailurePolicy.BLOCK, registry.register(validator).failurePolicy());
    }

    @Test
    void activeForReturnsOnlyEnabledExtensionsOfThatHookInOrder() {
        Extension first = registry.register("A", Hook.CART_PRICE, URI.create("http://a"), 500,
                FailurePolicy.SKIP);
        registry.register("B", Hook.ORDER_VALIDATE, URI.create("http://b"), 500,
                FailurePolicy.SKIP);
        Extension third = registry.register("C", Hook.CART_PRICE, URI.create("http://c"), 500,
                FailurePolicy.SKIP);

        assertEquals(List.of(first, third), registry.activeFor(Hook.CART_PRICE));

        registry.setEnabled(first.id(), false);
        assertEquals(List.of(third), registry.activeFor(Hook.CART_PRICE));
        assertFalse(registry.find(first.id()).orElseThrow().enabled());
    }

    @Test
    void removeAndUpdateReportUnknownIds() {
        assertTrue(registry.setEnabled("ext-99", true).isEmpty());
        assertFalse(registry.remove("ext-99"));

        Extension ext = registry.register("A", Hook.CART_PRICE, URI.create("http://a"), 500,
                FailurePolicy.SKIP);
        assertTrue(registry.remove(ext.id()));
        assertTrue(registry.all().isEmpty());
    }
}
