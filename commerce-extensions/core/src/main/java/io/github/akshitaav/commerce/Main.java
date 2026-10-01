package io.github.akshitaav.commerce;

import io.github.akshitaav.commerce.api.ApiServer;
import io.github.akshitaav.commerce.catalog.Catalog;
import io.github.akshitaav.commerce.extensions.Extension;
import io.github.akshitaav.commerce.extensions.ExtensionRegistry;
import io.github.akshitaav.commerce.extensions.HttpExtensionInvoker;
import io.github.akshitaav.commerce.json.Fields;
import io.github.akshitaav.commerce.json.Json;
import io.github.akshitaav.commerce.orders.OrderService;
import io.github.akshitaav.commerce.pricing.PricingService;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Starts the core service.
 *
 * <p>Environment variables:
 * <ul>
 *   <li>{@code PORT} (default 8080)</li>
 *   <li>{@code ALLOWED_ORIGIN} for CORS (default http://localhost:4200, empty disables it)</li>
 *   <li>{@code BOOTSTRAP_EXTENSIONS}: a JSON array of extensions to register at start-up,
 *       e.g. from a Kubernetes ConfigMap</li>
 * </ul>
 */
public final class Main {

    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(env("PORT", "8080"));
        String origin = env("ALLOWED_ORIGIN", "http://localhost:4200");

        Catalog catalog = Catalog.demo();
        ExtensionRegistry registry = new ExtensionRegistry();
        bootstrap(registry, System.getenv("BOOTSTRAP_EXTENSIONS"));

        HttpExtensionInvoker invoker = new HttpExtensionInvoker();
        PricingService pricing = new PricingService(catalog, registry, invoker);
        OrderService orders = new OrderService(pricing, registry, invoker);
        ApiServer server = new ApiServer(catalog, registry, pricing, orders, origin);

        int actual = server.start(port);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        LOG.info("Commerce core listening on port " + actual);
    }

    /** Registers extensions from a JSON array. Bad entries stop start-up with a clear error. */
    static void bootstrap(ExtensionRegistry registry, String json) {
        if (json == null || json.isBlank()) {
            return;
        }
        Object parsed = Json.parse(json);
        if (!(parsed instanceof List<?> items)) {
            throw new IllegalArgumentException("BOOTSTRAP_EXTENSIONS must be a JSON array");
        }
        for (Object item : items) {
            Map<String, Object> entry = Fields.asObject(item, "Each bootstrap extension");
            Extension ext = registry.register(entry);
            LOG.info("Registered extension " + ext.name() + " for " + ext.hook().id());
        }
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null ? fallback : value;
    }
}
