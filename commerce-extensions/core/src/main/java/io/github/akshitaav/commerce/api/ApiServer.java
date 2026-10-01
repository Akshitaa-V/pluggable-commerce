package io.github.akshitaav.commerce.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.akshitaav.commerce.catalog.Catalog;
import io.github.akshitaav.commerce.extensions.Extension;
import io.github.akshitaav.commerce.extensions.ExtensionRegistry;
import io.github.akshitaav.commerce.json.Fields;
import io.github.akshitaav.commerce.json.Json;
import io.github.akshitaav.commerce.json.JsonException;
import io.github.akshitaav.commerce.orders.OrderRejectedException;
import io.github.akshitaav.commerce.orders.OrderService;
import io.github.akshitaav.commerce.pricing.CartLine;
import io.github.akshitaav.commerce.pricing.PricingService;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * REST API of the core service, built on the JDK's HttpServer.
 *
 * <pre>
 * GET    /health
 * GET    /api/products
 * GET    /api/extensions          POST /api/extensions
 * GET    /api/extensions/{id}     PATCH /api/extensions/{id}   DELETE /api/extensions/{id}
 * POST   /api/quotes
 * POST   /api/orders              GET  /api/orders/{id}
 * </pre>
 */
public final class ApiServer {

    private static final Logger LOG = Logger.getLogger(ApiServer.class.getName());
    private static final int MAX_BODY_BYTES = 64 * 1024;

    private final Catalog catalog;
    private final ExtensionRegistry registry;
    private final PricingService pricing;
    private final OrderService orders;
    private final String allowedOrigin;
    private HttpServer server;
    private ExecutorService executor;

    public ApiServer(Catalog catalog, ExtensionRegistry registry, PricingService pricing,
                     OrderService orders, String allowedOrigin) {
        this.catalog = catalog;
        this.registry = registry;
        this.pricing = pricing;
        this.orders = orders;
        this.allowedOrigin = allowedOrigin;
    }

    /** Starts the server. Port 0 picks a free port, which tests use. */
    public synchronized int start(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        server.start();
        return server.getAddress().getPort();
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            executor.shutdownNow();
            server = null;
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            addCorsHeaders(exchange);
            String method = exchange.getRequestMethod();
            if ("OPTIONS".equals(method)) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            String path = exchange.getRequestURI().getPath();
            if (path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            route(exchange, method, path);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Unhandled error", e);
        }
    }

    private void route(HttpExchange ex, String method, String path) throws IOException {
        try {
            String[] parts = path.split("/");
            if (path.equals("/health")) {
                requireMethod(method, "GET");
                send(ex, 200, Map.of("status", "UP"));
            } else if (path.equals("/api/products")) {
                requireMethod(method, "GET");
                send(ex, 200, catalog.all());
            } else if (path.equals("/api/extensions")) {
                if (method.equals("GET")) {
                    send(ex, 200, registry.all());
                } else {
                    requireMethod(method, "POST");
                    send(ex, 201, registry.register(readObject(ex)));
                }
            } else if (parts.length == 4 && path.startsWith("/api/extensions/")) {
                handleExtension(ex, method, parts[3]);
            } else if (path.equals("/api/quotes")) {
                requireMethod(method, "POST");
                send(ex, 200, pricing.quote(readLines(readObject(ex))));
            } else if (path.equals("/api/orders")) {
                requireMethod(method, "POST");
                send(ex, 201, orders.place(readLines(readObject(ex))));
            } else if (parts.length == 4 && path.startsWith("/api/orders/")) {
                requireMethod(method, "GET");
                Object order = orders.find(parts[3]).orElse(null);
                if (order == null) {
                    sendError(ex, 404, "Order not found");
                } else {
                    send(ex, 200, order);
                }
            } else {
                sendError(ex, 404, "No route for " + path);
            }
        } catch (MethodNotAllowed e) {
            sendError(ex, 405, "Method " + method + " is not allowed here");
        } catch (BodyTooLarge e) {
            sendError(ex, 413, "Request body is larger than 64 KB");
        } catch (JsonException e) {
            sendError(ex, 400, "Invalid JSON: " + e.getMessage());
        } catch (OrderRejectedException e) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", e.getMessage());
            body.put("rejectedBy", e.extensionName());
            send(ex, 422, body);
        } catch (IllegalArgumentException e) {
            sendError(ex, 400, e.getMessage());
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Request failed", e);
            sendError(ex, 500, "Internal error");
        }
    }

    private void handleExtension(HttpExchange ex, String method, String id) throws IOException {
        switch (method) {
            case "GET" -> {
                Extension ext = registry.find(id).orElse(null);
                if (ext == null) {
                    sendError(ex, 404, "Extension not found");
                } else {
                    send(ex, 200, ext);
                }
            }
            case "PATCH" -> {
                boolean enabled = Fields.requireBoolean(readObject(ex), "enabled");
                Extension ext = registry.setEnabled(id, enabled).orElse(null);
                if (ext == null) {
                    sendError(ex, 404, "Extension not found");
                } else {
                    send(ex, 200, ext);
                }
            }
            case "DELETE" -> {
                if (registry.remove(id)) {
                    ex.sendResponseHeaders(204, -1);
                } else {
                    sendError(ex, 404, "Extension not found");
                }
            }
            default -> throw new MethodNotAllowed();
        }
    }

    private static List<CartLine> readLines(Map<String, Object> body) {
        List<CartLine> lines = new ArrayList<>();
        for (Object item : Fields.requireList(body, "lines")) {
            Map<String, Object> line = Fields.asObject(item, "each line");
            lines.add(new CartLine(Fields.requireString(line, "sku"),
                    Fields.requireLong(line, "quantity")));
        }
        return lines;
    }

    private static Map<String, Object> readObject(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            byte[] bytes = in.readNBytes(MAX_BODY_BYTES + 1);
            if (bytes.length > MAX_BODY_BYTES) {
                throw new BodyTooLarge();
            }
            return Json.parseObject(new String(bytes, StandardCharsets.UTF_8));
        }
    }

    private static void requireMethod(String actual, String expected) {
        if (!expected.equals(actual)) {
            throw new MethodNotAllowed();
        }
    }

    private void addCorsHeaders(HttpExchange ex) {
        if (allowedOrigin != null && !allowedOrigin.isBlank()) {
            ex.getResponseHeaders().add("Access-Control-Allow-Origin", allowedOrigin);
            ex.getResponseHeaders().add("Access-Control-Allow-Methods",
                    "GET, POST, PATCH, DELETE, OPTIONS");
            ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        }
    }

    private static void sendError(HttpExchange ex, int status, String message) throws IOException {
        send(ex, status, Map.of("error", message == null ? "Bad request" : message));
    }

    private static void send(HttpExchange ex, int status, Object body) throws IOException {
        byte[] bytes = Json.write(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static final class MethodNotAllowed extends RuntimeException {
    }

    private static final class BodyTooLarge extends RuntimeException {
    }
}
