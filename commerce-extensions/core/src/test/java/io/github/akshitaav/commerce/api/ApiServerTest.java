package io.github.akshitaav.commerce.api;

import com.sun.net.httpserver.HttpServer;
import io.github.akshitaav.commerce.catalog.Catalog;
import io.github.akshitaav.commerce.extensions.ExtensionRegistry;
import io.github.akshitaav.commerce.extensions.HttpExtensionInvoker;
import io.github.akshitaav.commerce.json.Json;
import io.github.akshitaav.commerce.orders.OrderService;
import io.github.akshitaav.commerce.pricing.PricingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end tests over real HTTP: the core API on one port and fake extensions,
 * each on its own JDK HttpServer, on other ports.
 */
class ApiServerTest {

    private ApiServer api;
    private HttpServer extensions;
    private String base;
    private String extBase;
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void start() throws IOException {
        extensions = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        respond("/discount", 0,
                "{\"adjustments\":[{\"sku\":\"MUG-CER\",\"label\":\"Mug deal\",\"amountCents\":-290}]}");
        respond("/slow", 500, "{\"adjustments\":[]}");
        respond("/deny", 0, "{\"allowed\":false,\"reason\":\"Too many grinders\"}");
        extensions.start();
        extBase = "http://127.0.0.1:" + extensions.getAddress().getPort();

        Catalog catalog = Catalog.demo();
        ExtensionRegistry registry = new ExtensionRegistry();
        HttpExtensionInvoker invoker = new HttpExtensionInvoker();
        PricingService pricing = new PricingService(catalog, registry, invoker);
        OrderService orders = new OrderService(pricing, registry, invoker);
        api = new ApiServer(catalog, registry, pricing, orders, "http://localhost:4200");
        base = "http://127.0.0.1:" + api.start(0);
    }

    @AfterEach
    void stop() {
        api.stop();
        extensions.stop(0);
    }

    private void respond(String path, int delayMs, String body) {
        extensions.createContext(path, exchange -> {
            try {
                exchange.getRequestBody().readAllBytes();
                if (delayMs > 0) {
                    Thread.sleep(delayMs);
                }
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                // The core gave up waiting (timeout test); nothing to do.
            } finally {
                exchange.close();
            }
        });
    }

    private HttpResponse<String> call(String method, String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path));
        builder.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        builder.header("Content-Type", "application/json");
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String register(String name, String hook, String path, int timeoutMs) throws Exception {
        String body = "{\"name\":\"" + name + "\",\"hook\":\"" + hook + "\",\"url\":\""
                + extBase + path + "\",\"timeoutMs\":" + timeoutMs + "}";
        HttpResponse<String> response = call("POST", "/api/extensions", body);
        assertEquals(201, response.statusCode());
        return (String) Json.parseObject(response.body()).get("id");
    }

    private static final String CART = "{\"lines\":[{\"sku\":\"MUG-CER\",\"quantity\":2}]}";

    @Test
    void healthAndProductsAnswer() throws Exception {
        assertEquals(200, call("GET", "/health", null).statusCode());
        HttpResponse<String> products = call("GET", "/api/products", null);
        assertEquals(200, products.statusCode());
        assertEquals(4, ((List<?>) Json.parse(products.body())).size());
        assertEquals("http://localhost:4200",
                products.headers().firstValue("Access-Control-Allow-Origin").orElse(""));
    }

    @Test
    void registeredExtensionChangesTheQuoteUntilItIsDisabled() throws Exception {
        String id = register("Mug deal", "cart.price", "/discount", 1000);

        Map<String, Object> quote = Json.parseObject(call("POST", "/api/quotes", CART).body());
        assertEquals(2580L, quote.get("subtotalCents"));
        assertEquals(2290L, quote.get("totalCents"));

        assertEquals(200, call("PATCH", "/api/extensions/" + id, "{\"enabled\":false}").statusCode());
        quote = Json.parseObject(call("POST", "/api/quotes", CART).body());
        assertEquals(2580L, quote.get("totalCents"));
    }

    @Test
    void slowExtensionTimesOutAndPricingStillAnswers() throws Exception {
        register("Slow one", "cart.price", "/slow", 100);
        long start = System.nanoTime();
        HttpResponse<String> response = call("POST", "/api/quotes", CART);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertEquals(200, response.statusCode());
        Map<String, Object> quote = Json.parseObject(response.body());
        assertEquals(2580L, quote.get("totalCents"));
        assertTrue(((List<?>) quote.get("warnings")).get(0).toString().contains("timed out"));
        assertTrue(elapsedMs < 450, "the core should not wait for the slow extension");
    }

    @Test
    void validatorRejectionReturns422() throws Exception {
        register("Grinder limit", "order.validate", "/deny", 1000);
        HttpResponse<String> response = call("POST", "/api/orders", CART);
        assertEquals(422, response.statusCode());
        Map<String, Object> body = Json.parseObject(response.body());
        assertEquals("Too many grinders", body.get("error"));
        assertEquals("Grinder limit", body.get("rejectedBy"));
    }

    @Test
    void placedOrderCanBeReadBack() throws Exception {
        HttpResponse<String> created = call("POST", "/api/orders", CART);
        assertEquals(201, created.statusCode());
        String id = (String) Json.parseObject(created.body()).get("id");
        assertEquals(200, call("GET", "/api/orders/" + id, null).statusCode());
        assertEquals(404, call("GET", "/api/orders/ord-404", null).statusCode());
    }

    @Test
    void badRequestsGetClearErrors() throws Exception {
        assertEquals(400, call("POST", "/api/quotes", "{not json").statusCode());
        assertEquals(400, call("POST", "/api/quotes", "{\"lines\":[]}").statusCode());
        assertEquals(400, call("POST", "/api/quotes",
                "{\"lines\":[{\"sku\":\"NOPE\",\"quantity\":1}]}").statusCode());
        assertEquals(404, call("GET", "/api/nothing", null).statusCode());
        assertEquals(405, call("DELETE", "/api/products", null).statusCode());
        assertEquals(404, call("DELETE", "/api/extensions/ext-404", null).statusCode());
    }
}
