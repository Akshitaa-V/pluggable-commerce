package io.github.akshitaav.commerce.extensions;

import io.github.akshitaav.commerce.json.JsonWritable;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A registered extension: an external HTTP endpoint that the core calls at one hook.
 * Records are immutable; enabling or disabling produces a new instance.
 */
public record Extension(
        String id,
        String name,
        Hook hook,
        URI url,
        boolean enabled,
        int timeoutMs,
        FailurePolicy failurePolicy) implements JsonWritable {

    public static final int MIN_TIMEOUT_MS = 50;
    public static final int MAX_TIMEOUT_MS = 5000;

    public Extension {
        if (name == null || name.isBlank() || name.length() > 60) {
            throw new IllegalArgumentException("name must be 1 to 60 characters");
        }
        if (hook == null) {
            throw new IllegalArgumentException("hook is required");
        }
        if (url == null || url.getHost() == null
                || !("http".equals(url.getScheme()) || "https".equals(url.getScheme()))) {
            throw new IllegalArgumentException("url must be an absolute http or https URL");
        }
        if (timeoutMs < MIN_TIMEOUT_MS || timeoutMs > MAX_TIMEOUT_MS) {
            throw new IllegalArgumentException(
                    "timeoutMs must be between " + MIN_TIMEOUT_MS + " and " + MAX_TIMEOUT_MS);
        }
        if (failurePolicy == null) {
            failurePolicy = FailurePolicy.SKIP;
        }
    }

    public Extension withEnabled(boolean value) {
        return new Extension(id, name, hook, url, value, timeoutMs, failurePolicy);
    }

    @Override
    public Object toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", id);
        json.put("name", name);
        json.put("hook", hook.id());
        json.put("url", url.toString());
        json.put("enabled", enabled);
        json.put("timeoutMs", timeoutMs);
        json.put("failurePolicy", failurePolicy.name());
        return json;
    }
}
