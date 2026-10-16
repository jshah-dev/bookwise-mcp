package com.github.jshah_dev.bookwise_mcp.providers.openlibrary;

import com.github.jshah_dev.bookwise_mcp.exception.BookProviderException;
import com.github.jshah_dev.bookwise_mcp.model.Book;
import com.github.jshah_dev.bookwise_mcp.providers.openlibrary.OpenLibraryModels.SearchResponse;
import com.github.jshah_dev.bookwise_mcp.providers.openlibrary.OpenLibraryModels.WorkDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

/**
 * HTTP client for the Open Library public REST API.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Build and execute HTTP requests against the Open Library endpoints.</li>
 *   <li>Translate HTTP and I/O errors into {@link BookProviderException}.</li>
 *   <li>Apply lightweight caching so repeat identical queries avoid redundant network calls.</li>
 *   <li>Provide structured operational logging for every request.</li>
 * </ul>
 * <p>
 * This class owns only the <em>transport layer</em>.  Business logic and mapping live in
 * {@link OpenLibraryMapper} and the service layer.
 *
 * @see <a href="https://openlibrary.org/dev/docs/api/search">Open Library Search API</a>
 */
@Component
public class OpenLibraryClient {

    private static final Logger log = LoggerFactory.getLogger(OpenLibraryClient.class);

    /** Short identifier used in log messages and exception messages. */
    static final String PROVIDER = "openlibrary";

    /** Maximum number of results the server will ever request from the API. */
    private static final int MAX_LIMIT = 20;

    /** Descriptive User-Agent header required by the Open Library API Terms of Service. */
    private static final String USER_AGENT = "BookWise-MCP/0.1.0 (https://github.com/jshah-dev/bookwise-mcp)";

    private final RestClient restClient;
    private final OpenLibraryMapper mapper;

    /**
     * Constructs the client using a pre-configured {@link RestClient} builder.
     * The builder is supplied by {@link com.github.jshah_dev.bookwise_mcp.config.RestClientConfig}.
     * <p>
     * {@code @Autowired} is required because a second package-private constructor exists
     * for testing; Spring needs an explicit hint to select the correct one.
     *
     * @param restClientBuilder Spring-managed builder with timeout and base URL configured
     * @param mapper            mapper that converts raw API DTOs to normalized {@link Book} objects
     */
    @Autowired
    public OpenLibraryClient(RestClient.Builder restClientBuilder, OpenLibraryMapper mapper) {
        this(restClientBuilder, mapper, "https://openlibrary.org");
    }

    /**
     * Package-private constructor that allows overriding the base URL.
     * <p>
     * Intended for use in tests that point the client at a WireMock server instead of
     * the real Open Library API.  Not for production use.
     *
     * @param restClientBuilder builder to configure
     * @param mapper            mapper for DTO → {@link Book} conversion
     * @param baseUrl           custom base URL (e.g. {@code "http://localhost:8089"} for WireMock)
     */
    OpenLibraryClient(RestClient.Builder restClientBuilder, OpenLibraryMapper mapper, String baseUrl) {
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("User-Agent", USER_AGENT)
                .build();
        this.mapper = mapper;
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Searches Open Library for books matching the given query.
     * <p>
     * Results are cached so that identical queries within the cache TTL do not
     * trigger additional network calls.
     *
     * @param query    free-text search query; must not be blank
     * @param language optional ISO 639-1 language code filter (e.g. {@code "en"}); {@code null} means all
     * @param limit    maximum number of results to return; clamped to [{@value #MAX_LIMIT}]
     * @return normalized {@link Book} list — empty list means no matches (not a failure)
     * @throws BookProviderException if the API returns an error or is unreachable
     */
    @Cacheable(value = "openLibrarySearch", key = "#query + '-' + #language + '-' + #limit")
    public List<Book> searchBooks(String query, String language, int limit) {
        long start = System.currentTimeMillis();
        int clampedLimit = Math.min(limit, MAX_LIMIT);

        try {
            // Build the search URL; the 'lang' parameter filters by editions in that language
            String uri = UriComponentsBuilder.fromPath("/search.json")
                    .queryParam("q", query)
                    .queryParam("limit", clampedLimit)
                    .queryParam("fields", "key,title,author_name,first_publish_year,subject,language,isbn,cover_i")
                    .queryParamIfPresent("lang", java.util.Optional.ofNullable(language))
                    .toUriString();

            SearchResponse response = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new BookProviderException(PROVIDER,
                                "Search request failed with HTTP " + res.getStatusCode() + " for query: " + query);
                    })
                    .body(SearchResponse.class);

            List<Book> books = (response != null && response.docs() != null)
                    ? mapper.toBooks(response.docs())
                    : List.of();

            logOperation("search", System.currentTimeMillis() - start, "success", books.size());
            return books;

        } catch (BookProviderException e) {
            logOperation("search", System.currentTimeMillis() - start, "error", 0);
            throw e;
        } catch (ResourceAccessException e) {
            // Network-level failure: timeout, DNS resolution, connection refused, etc.
            logOperation("search", System.currentTimeMillis() - start, "timeout", 0);
            throw new BookProviderException(PROVIDER,
                    "Search timed out or could not connect for query: " + query, e);
        } catch (RestClientResponseException e) {
            logOperation("search", System.currentTimeMillis() - start, "error", 0);
            handleHttpError(e, "search");
            return List.of(); // unreachable – handleHttpError always throws
        }
    }

    /**
     * Fetches the detailed work record for a given Open Library work ID.
     * <p>
     * Results are cached so that repeated lookups for the same work avoid network calls.
     *
     * @param workId bare work key, e.g. {@code "OL123W"} (without {@code /works/} prefix)
     * @return normalized {@link Book} with all available work detail fields populated
     * @throws BookProviderException if the work is not found, the API returns an error,
     *                               or the API is unreachable
     */
    @Cacheable(value = "openLibraryWorkDetail", key = "#workId")
    public Book getBookDetail(String workId) {
        long start = System.currentTimeMillis();

        try {
            WorkDetail detail = restClient.get()
                    .uri("/works/{workId}.json", workId)
                    .retrieve()
                    .onStatus(status -> status == HttpStatus.NOT_FOUND, (req, res) -> {
                        throw new BookProviderException(PROVIDER,
                                "Work not found: " + workId);
                    })
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new BookProviderException(PROVIDER,
                                "Work detail request failed with HTTP " + res.getStatusCode()
                                        + " for workId: " + workId);
                    })
                    .body(WorkDetail.class);

            if (detail == null) {
                throw new BookProviderException(PROVIDER, "Empty response for workId: " + workId);
            }

            Book book = mapper.toBookDetail(detail);
            logOperation("detail", System.currentTimeMillis() - start, "success", 1);
            return book;

        } catch (BookProviderException e) {
            logOperation("detail", System.currentTimeMillis() - start, "error", 0);
            throw e;
        } catch (ResourceAccessException e) {
            logOperation("detail", System.currentTimeMillis() - start, "timeout", 0);
            throw new BookProviderException(PROVIDER,
                    "Work detail timed out for workId: " + workId, e);
        } catch (RestClientResponseException e) {
            logOperation("detail", System.currentTimeMillis() - start, "error", 0);
            handleHttpError(e, "detail");
            return null; // unreachable
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Translates a {@link RestClientResponseException} into a more informative
     * {@link BookProviderException}.
     * <p>
     * Rate-limit responses (HTTP 429) are called out explicitly so that future
     * retry logic can identify them.
     *
     * @param e         the HTTP response exception
     * @param operation short label for the failing operation (used in the message)
     * @throws BookProviderException always
     */
    private void handleHttpError(RestClientResponseException e, String operation) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        if (status == HttpStatus.TOO_MANY_REQUESTS) {
            throw new BookProviderException(PROVIDER,
                    "Rate limit exceeded during " + operation + " — retry later", e);
        }
        throw new BookProviderException(PROVIDER,
                "HTTP " + e.getStatusCode() + " during " + operation, e);
    }

    /**
     * Writes a structured operational log line for each provider operation.
     * <p>
     * Format: {@code provider=openlibrary operation=search duration=245ms status=success resultCount=10}
     *
     * @param operation   short name of the operation (e.g. {@code "search"}, {@code "detail"})
     * @param durationMs  elapsed time in milliseconds
     * @param status      outcome label (e.g. {@code "success"}, {@code "error"}, {@code "timeout"})
     * @param resultCount number of results returned (0 for non-search operations on failure)
     */
    private void logOperation(String operation, long durationMs, String status, int resultCount) {
        log.info("provider={} operation={} duration={}ms status={} resultCount={}",
                PROVIDER, operation, durationMs, status, resultCount);
    }
}
