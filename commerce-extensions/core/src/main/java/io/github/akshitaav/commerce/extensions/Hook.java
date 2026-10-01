package io.github.akshitaav.commerce.extensions;

import java.util.Arrays;
import java.util.Optional;

/** The points in the core flow where customers can plug in their own logic. */
public enum Hook {
    /** Called while a cart is priced. Extensions return price adjustments (discounts or fees). */
    CART_PRICE("cart.price"),
    /** Called before an order is placed. Extensions may reject the order with a reason. */
    ORDER_VALIDATE("order.validate");

    private final String id;

    Hook(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<Hook> fromId(String id) {
        return Arrays.stream(values()).filter(h -> h.id.equals(id)).findFirst();
    }
}
