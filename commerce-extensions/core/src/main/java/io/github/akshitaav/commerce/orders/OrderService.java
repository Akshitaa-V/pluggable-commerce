package io.github.akshitaav.commerce.orders;

import io.github.akshitaav.commerce.extensions.Extension;
import io.github.akshitaav.commerce.extensions.ExtensionException;
import io.github.akshitaav.commerce.extensions.ExtensionInvoker;
import io.github.akshitaav.commerce.extensions.ExtensionRegistry;
import io.github.akshitaav.commerce.extensions.FailurePolicy;
import io.github.akshitaav.commerce.extensions.Hook;
import io.github.akshitaav.commerce.json.Fields;
import io.github.akshitaav.commerce.pricing.CartLine;
import io.github.akshitaav.commerce.pricing.PricingService;
import io.github.akshitaav.commerce.pricing.Quote;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Places orders. Each enabled {@code order.validate} extension can veto the order.
 * If a validator fails, its failure policy decides: SKIP continues with a warning,
 * BLOCK rejects the order because it could not be checked.
 */
public final class OrderService {

    private static final int MAX_REASON_LENGTH = 200;

    private final PricingService pricing;
    private final ExtensionRegistry registry;
    private final ExtensionInvoker invoker;
    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    public OrderService(PricingService pricing, ExtensionRegistry registry,
                        ExtensionInvoker invoker) {
        this.pricing = pricing;
        this.registry = registry;
        this.invoker = invoker;
    }

    public Order place(List<CartLine> lines) {
        Quote quote = pricing.quote(lines);
        List<String> warnings = new ArrayList<>(quote.warnings());

        for (Extension extension : registry.activeFor(Hook.ORDER_VALIDATE)) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("hook", Hook.ORDER_VALIDATE.id());
            payload.put("quote", quote);
            try {
                Map<String, Object> response = invoker.invoke(extension, payload);
                boolean allowed = Fields.requireBoolean(response, "allowed");
                if (!allowed) {
                    String reason = Optional.ofNullable(Fields.optionalString(response, "reason"))
                            .filter(r -> !r.isBlank())
                            .map(r -> r.length() > MAX_REASON_LENGTH
                                    ? r.substring(0, MAX_REASON_LENGTH) : r)
                            .orElse("Rejected without a reason");
                    throw new OrderRejectedException(extension.name(), reason);
                }
            } catch (ExtensionException | IllegalArgumentException e) {
                String problem = e instanceof ExtensionException
                        ? e.getMessage() : "returned invalid data (" + e.getMessage() + ")";
                if (extension.failurePolicy() == FailurePolicy.BLOCK) {
                    throw new OrderRejectedException(extension.name(),
                            "The order could not be validated: extension " + problem);
                }
                warnings.add("Validator '" + extension.name() + "' " + problem
                        + "; the order was placed without its check.");
            }
        }

        String id = "ord-" + sequence.incrementAndGet();
        Order order = new Order(id, quote, warnings);
        orders.put(id, order);
        return order;
    }

    public Optional<Order> find(String id) {
        return Optional.ofNullable(orders.get(id));
    }
}
