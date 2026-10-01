package io.github.akshitaav.commerce.extensions;

/** An extension could not be called or returned something the core cannot use. */
public class ExtensionException extends Exception {
    public ExtensionException(String message) {
        super(message);
    }

    public ExtensionException(String message, Throwable cause) {
        super(message, cause);
    }
}
