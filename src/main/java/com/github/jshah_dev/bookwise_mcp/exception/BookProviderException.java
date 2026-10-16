package com.github.jshah_dev.bookwise_mcp.exception;

/**
 * Thrown when an external book provider (Open Library, Gutendex, etc.) returns an
 * unexpected error response or is otherwise unavailable.
 * <p>
 * This exception deliberately distinguishes a <em>provider failure</em> (server error,
 * timeout, unexpected response) from an <em>empty result set</em> (successful response
 * with zero matches).  Callers should never present an empty result to the MCP client
 * when the provider actually failed.
 */
public class BookProviderException extends RuntimeException {

    /** The short name of the provider that raised this exception (e.g. {@code "openlibrary"}). */
    private final String provider;

    /**
     * Constructs a {@code BookProviderException} with a descriptive message.
     *
     * @param provider the provider identifier
     * @param message  human-readable description of the failure
     */
    public BookProviderException(String provider, String message) {
        super("[" + provider + "] " + message);
        this.provider = provider;
    }

    /**
     * Constructs a {@code BookProviderException} wrapping an underlying cause.
     *
     * @param provider the provider identifier
     * @param message  human-readable description of the failure
     * @param cause    the original exception that triggered this failure
     */
    public BookProviderException(String provider, String message, Throwable cause) {
        super("[" + provider + "] " + message, cause);
        this.provider = provider;
    }

    /**
     * Returns the short name of the provider that raised this exception.
     *
     * @return provider identifier
     */
    public String getProvider() {
        return provider;
    }
}

