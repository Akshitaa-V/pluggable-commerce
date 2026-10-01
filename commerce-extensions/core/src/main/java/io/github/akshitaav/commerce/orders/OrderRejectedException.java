package io.github.akshitaav.commerce.orders;

/** An order.validate extension refused the order, or could not be asked and is set to BLOCK. */
public class OrderRejectedException extends RuntimeException {

    private final String extensionName;

    public OrderRejectedException(String extensionName, String reason) {
        super(reason);
        this.extensionName = extensionName;
    }

    public String extensionName() {
        return extensionName;
    }
}
