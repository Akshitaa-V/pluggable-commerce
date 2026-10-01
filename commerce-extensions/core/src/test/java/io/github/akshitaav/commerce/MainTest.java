package io.github.akshitaav.commerce;

import io.github.akshitaav.commerce.extensions.ExtensionRegistry;
import io.github.akshitaav.commerce.extensions.Hook;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainTest {

    @Test
    void bootstrapRegistersExtensionsFromJson() {
        ExtensionRegistry registry = new ExtensionRegistry();
        Main.bootstrap(registry, "[{\"name\":\"Rust discounts\",\"hook\":\"cart.price\","
                + "\"url\":\"http://discount-extension:8081/hooks/cart-price\",\"timeoutMs\":800}]");
        assertEquals(1, registry.activeFor(Hook.CART_PRICE).size());
        assertEquals(800, registry.all().get(0).timeoutMs());
    }

    @Test
    void emptyBootstrapIsAllowedAndBadBootstrapFailsFast() {
        ExtensionRegistry registry = new ExtensionRegistry();
        Main.bootstrap(registry, null);
        Main.bootstrap(registry, "  ");
        assertTrue(registry.all().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> Main.bootstrap(registry, "{}"));
        assertThrows(IllegalArgumentException.class,
                () -> Main.bootstrap(registry, "[{\"name\":\"x\"}]"));
    }
}
