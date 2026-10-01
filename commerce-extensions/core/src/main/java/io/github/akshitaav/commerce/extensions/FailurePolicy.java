package io.github.akshitaav.commerce.extensions;

/** What the core does when an extension cannot be reached or answers with invalid data. */
public enum FailurePolicy {
    /** Ignore the extension for this request and report a warning. */
    SKIP,
    /** Stop the operation. Only meaningful for validation hooks. */
    BLOCK
}
