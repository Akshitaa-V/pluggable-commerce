package io.github.akshitaav.commerce.extensions;

import io.github.akshitaav.commerce.json.Json;
import io.github.akshitaav.commerce.json.JsonException;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;

/** Calls extensions over HTTP: POST with a JSON body, per-extension timeout. */
public final class HttpExtensionInvoker implements ExtensionInvoker {

    private static final int MAX_RESPONSE_CHARS = 64 * 1024;

    private final HttpClient client;

    public HttpExtensionInvoker() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .version(HttpClient.Version.HTTP_1_1)
                .build());
    }

    public HttpExtensionInvoker(HttpClient client) {
        this.client = client;
    }

    @Override
    public Map<String, Object> invoke(Extension extension, Map<String, Object> payload)
            throws ExtensionException {
        HttpRequest request = HttpRequest.newBuilder(extension.url())
                .timeout(Duration.ofMillis(extension.timeoutMs()))
                .header("Content-Type", "application/json")
                .header("X-Commerce-Hook", extension.hook().id())
                .header("X-Commerce-Extension-Id", extension.id())
                .POST(HttpRequest.BodyPublishers.ofString(Json.write(payload)))
                .build();
        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new ExtensionException("timed out after " + extension.timeoutMs() + " ms", e);
        } catch (IOException e) {
            throw new ExtensionException("could not be reached", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExtensionException("call was interrupted", e);
        }
        if (response.statusCode() != 200) {
            throw new ExtensionException("answered HTTP " + response.statusCode());
        }
        String body = response.body();
        if (body.length() > MAX_RESPONSE_CHARS) {
            throw new ExtensionException("response is larger than 64 KB");
        }
        try {
            return Json.parseObject(body);
        } catch (JsonException e) {
            throw new ExtensionException("returned invalid JSON", e);
        }
    }
}
