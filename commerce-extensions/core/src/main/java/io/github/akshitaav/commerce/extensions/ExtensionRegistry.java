package io.github.akshitaav.commerce.extensions;

import io.github.akshitaav.commerce.json.Fields;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe registry of extensions. Extensions for one hook run in the order they were
 * registered, so the result of a request is deterministic.
 */
public final class ExtensionRegistry {

    private final Map<String, Extension> extensions = new LinkedHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    public synchronized Extension register(String name, Hook hook, URI url, int timeoutMs,
                                           FailurePolicy policy) {
        String id = "ext-" + sequence.incrementAndGet();
        Extension extension = new Extension(id, name, hook, url, true, timeoutMs, policy);
        extensions.put(id, extension);
        return extension;
    }

    /** Registers an extension from a JSON request body or bootstrap entry. */
    public Extension register(Map<String, Object> body) {
        String name = Fields.requireString(body, "name");
        String hookId = Fields.requireString(body, "hook");
        Hook hook = Hook.fromId(hookId).orElseThrow(() -> new IllegalArgumentException(
                "Unknown hook '" + hookId + "'. Use cart.price or order.validate"));
        URI url;
        try {
            url = new URI(Fields.requireString(body, "url"));
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("url is not a valid URI");
        }
        Long timeout = Fields.optionalLong(body, "timeoutMs");
        String policyName = Fields.optionalString(body, "failurePolicy");
        FailurePolicy policy;
        try {
            policy = policyName == null ? FailurePolicy.SKIP : FailurePolicy.valueOf(policyName);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("failurePolicy must be SKIP or BLOCK");
        }
        if (policy == FailurePolicy.BLOCK && hook == Hook.CART_PRICE) {
            throw new IllegalArgumentException("BLOCK is only allowed for order.validate extensions");
        }
        int timeoutMs = timeout == null ? 1000 : (int) Math.max(Integer.MIN_VALUE,
                Math.min(Integer.MAX_VALUE, timeout));
        return register(name, hook, url, timeoutMs, policy);
    }

    public synchronized List<Extension> all() {
        return List.copyOf(extensions.values());
    }

    public synchronized Optional<Extension> find(String id) {
        return Optional.ofNullable(extensions.get(id));
    }

    /** Enabled extensions for a hook, in registration order. */
    public synchronized List<Extension> activeFor(Hook hook) {
        List<Extension> result = new ArrayList<>();
        for (Extension e : extensions.values()) {
            if (e.enabled() && e.hook() == hook) {
                result.add(e);
            }
        }
        return result;
    }

    public synchronized Optional<Extension> setEnabled(String id, boolean enabled) {
        Extension current = extensions.get(id);
        if (current == null) {
            return Optional.empty();
        }
        Extension updated = current.withEnabled(enabled);
        extensions.put(id, updated);
        return Optional.of(updated);
    }

    public synchronized boolean remove(String id) {
        return extensions.remove(id) != null;
    }
}
