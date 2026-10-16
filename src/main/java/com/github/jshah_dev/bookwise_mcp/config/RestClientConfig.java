package com.github.jshah_dev.bookwise_mcp.config;

import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;

/**
 * Spring configuration for the {@link org.springframework.web.client.RestClient} used
 * by all external provider clients (Open Library, Gutendex).
 * <p>
 * Centralising timeout configuration here ensures:
 * <ul>
 *   <li>No external API call can hang the MCP server indefinitely.</li>
 *   <li>Timeout values are defined in one place, not scattered across provider classes.</li>
 *   <li>They can be overridden per-environment via {@code application.yml} properties.</li>
 * </ul>
 * <p>
 * {@link EnableCaching} activates Spring's caching abstraction, which is used via
 * {@code @Cacheable} in the provider clients to avoid repeated identical API calls.
 */
@Configuration
@EnableCaching
public class RestClientConfig {

    /** Maximum time to wait for the TCP connection to be established. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    /**
     * Maximum time to wait for the server to begin streaming the response after
     * the connection is established (read timeout).
     */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    /**
     * Registers a {@link RestClientCustomizer} that applies connection and read
     * timeouts to every {@link org.springframework.web.client.RestClient.Builder}
     * in the application context.
     * <p>
     * Individual provider clients ({@code OpenLibraryClient}, {@code GutendexClient})
     * inject {@code RestClient.Builder} and receive these defaults automatically.
     *
     * @return customizer bean that configures timeouts on the underlying HTTP factory
     */
    @Bean
    public RestClientCustomizer restClientTimeoutCustomizer() {
        return builder -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
            factory.setReadTimeout((int) READ_TIMEOUT.toMillis());
            builder.requestFactory(factory);
        };
    }
}
