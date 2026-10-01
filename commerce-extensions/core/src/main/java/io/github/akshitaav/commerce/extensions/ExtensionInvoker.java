package io.github.akshitaav.commerce.extensions;

import java.util.Map;

/** Calls one extension with a JSON payload and returns its JSON object response. */
@FunctionalInterface
public interface ExtensionInvoker {
    Map<String, Object> invoke(Extension extension, Map<String, Object> payload)
            throws ExtensionException;
}
