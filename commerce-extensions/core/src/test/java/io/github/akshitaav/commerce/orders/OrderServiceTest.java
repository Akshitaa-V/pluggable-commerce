package io.github.akshitaav.commerce.orders;

import io.github.akshitaav.commerce.catalog.Catalog;
import io.github.akshitaav.commerce.catalog.Product;
import io.github.akshitaav.commerce.extensions.ExtensionException;
import io.github.akshitaav.commerce.extensions.ExtensionInvoker;
import io.github.akshitaav.commerce.extensions.ExtensionRegistry;
import io.github.akshitaav.commerce.extensions.FailurePolicy;
import io.github.akshitaav.commerce.extensions.Hook;
import io.github.akshitaav.commerce.json.Json;
import io.github.akshitaav.commerce.pricing.CartLine;
import io.github.akshitaav.commerce.pricing.PricingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderServiceTest {

    private ExtensionRegistry registry;
    private Map<String, String> responses;
    private OrderService orders;

    @BeforeEach
    void setUp() {
        Catalog catalog = new Catalog(List.of(new Product("A", "Item A", 1000)));
        registry = new ExtensionRegistry();
        responses = new HashMap<>();
        ExtensionInvoker invoker = (ext, payload) -> {
            String body = responses.get(ext.name());
            if ("FAIL".equals(body)) {
                throw new ExtensionException("could not be reached");
            }
            return Json.parseObject(body);
        };
        orders = new OrderService(new PricingService(catalog, registry, invoker), registry, invoker);
    }

    private void validator(String name, String response, FailurePolicy policy) {
        responses.put(name, response);
        registry.register(name, Hook.ORDER_VALIDATE,
                URI.create("http://ext/" + name.replace(' ', '-')), 100, policy);
    }

    @Test
    void placesOrderWhenValidatorsAllowIt() {
        validator("Limit", "{\"allowed\":true}", FailurePolicy.SKIP);
        Order order = orders.place(List.of(new CartLine("A", 2)));
        assertEquals("ord-1", order.id());
        assertEquals(2000, order.quote().totalCents());
        assertTrue(orders.find("ord-1").isPresent());
    }

    @Test
    void validatorCanRejectWithAReason() {
        validator("Limit", "{\"allowed\":false,\"reason\":\"Max 10 per order\"}", FailurePolicy.SKIP);
        OrderRejectedException e = assertThrows(OrderRejectedException.class,
                () -> orders.place(List.of(new CartLine("A", 20))));
        assertEquals("Max 10 per order", e.getMessage());
        assertEquals("Limit", e.extensionName());
        assertTrue(orders.find("ord-1").isEmpty());
    }

    @Test
    void failingValidatorWithSkipPolicyOnlyWarns() {
        validator("Fraud check", "FAIL", FailurePolicy.SKIP);
        Order order = orders.place(List.of(new CartLine("A", 1)));
        assertEquals(1, order.warnings().size());
        assertTrue(order.warnings().get(0).contains("placed without its check"));
    }

    @Test
    void failingValidatorWithBlockPolicyRejects() {
        validator("Fraud check", "FAIL", FailurePolicy.BLOCK);
        OrderRejectedException e = assertThrows(OrderRejectedException.class,
                () -> orders.place(List.of(new CartLine("A", 1))));
        assertTrue(e.getMessage().contains("could not be validated"));
    }

    @Test
    void invalidValidatorAnswerFollowsThePolicy() {
        validator("Odd", "{\"ok\":true}", FailurePolicy.BLOCK);
        assertThrows(OrderRejectedException.class,
                () -> orders.place(List.of(new CartLine("A", 1))));
    }
}
