package com.github.jshah_dev.bookwise_mcp.providers.gutendex;

import com.github.jshah_dev.bookwise_mcp.exception.BookProviderException;
import com.github.jshah_dev.bookwise_mcp.model.Book;
import com.github.jshah_dev.bookwise_mcp.providers.gutendex.GutendexModels.BookListResponse;
import com.github.jshah_dev.bookwise_mcp.providers.gutendex.GutendexModels.GutendexBook;
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
import java.util.Optional;

/**
 * HTTP client for the Gutendex REST API.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Execute queries against {@code https://gutendex.com/books/}.</li>
 *   <li>Fetch individual Project Gutenberg book metadata by ID.</li>
 *   <li>Apply response caching to prevent excess traffic to public infrastructure.</li>
 *   <li>Translate transport/HTTP errors to {@link BookProviderException}.</li>
 *   <li>Provide structured operational logging matching the project specification.</li>
 * </ul>
 *
 * @see <a href="https://gutendex.com/">Gutendex API Documentation</a>
 */
@Component
public class GutendexClient {

    private static final Logger log = LoggerFactory.getLogger(GutendexClient.class);

    /** Provider name for logs and exceptions. */
    static final String PROVIDER = "gutendex";

    /** Default public base URL for Gutendex API. */
    private static final String DEFAULT_BASE_URL = "https://gutendex.com";

    /** Maximum allowed limit for query results. */
    private static final int MAX_LIMIT = 20;

    /** Descriptive User-Agent. */
    private static final String USER_AGENT = "BookWise-MCP/1.0.0 (https://github.com/jshah-dev/bookwise-mcp)";

    private final RestClient restClient;
    private final GutendexMapper mapper;

    /**
     * Production constructor used by Spring context with autowired builder.
     *
     * @param restClientBuilder configured RestClient.Builder
     * @param mapper            Gutendex response mapper
     */
    @Autowired
    public GutendexClient(RestClient.Builder restClientBuilder, GutendexMapper mapper) {
        this(restClientBuilder, mapper, DEFAULT_BASE_URL);
    }

    /**
     * Package-private constructor allowing base URL override for isolated testing.
     *
     * @param restClientBuilder builder to configure
     * @param mapper            Gutendex response mapper
     * @param baseUrl           base URL (e.g., WireMock server URL)
     */
    GutendexClient(RestClient.Builder restClientBuilder, GutendexMapper mapper, String baseUrl) {
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("User-Agent", USER_AGENT)
                .build();
        this.mapper = mapper;
    }

    /**
     * Searches Project Gutenberg books via Gutendex.
     *
     * @param query    search query (search term)
     * @param language optional ISO 639-1 language code filter (e.g. "en")
     * @param limit    maximum number of results to return (clamped up to 20)
     * @return list of normalized {@link Book} objects
     * @throws BookProviderException on network or API failure
     */
    @Cacheable(value = "gutendexSearch", key = "#query + '-' + #language + '-' + #limit")
    public List<Book> searchBooks(String query, String language, int limit) {
        long start = System.currentTimeMillis();
        int clampedLimit = Math.min(Math.max(1, limit), MAX_LIMIT);

        try {
            String uri = UriComponentsBuilder.fromPath("/books/")
                    .queryParam("search", query)
                    .queryParamIfPresent("languages", Optional.ofNullable(language).filter(l -> !l.isBlank()))
                    .toUriString();

            BookListResponse response = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new BookProviderException(PROVIDER,
                                "Search request failed with HTTP " + res.getStatusCode() + " for query: " + query);
                    })
                    .body(BookListResponse.class);

            List<Book> books = (response != null && response.results() != null)
                    ? mapper.toBooks(response.results().stream().limit(clampedLimit).toList())
                    : List.of();

            logOperation("search", System.currentTimeMillis() - start, "success", books.size());
            return books;

        } catch (BookProviderException e) {
            logOperation("search", System.currentTimeMillis() - start, "error", 0);
            throw e;
        } catch (ResourceAccessException e) {
            logOperation("search", System.currentTimeMillis() - start, "timeout", 0);
            throw new BookProviderException(PROVIDER,
                    "Search timed out or could not connect for query: " + query, e);
        } catch (RestClientResponseException e) {
            logOperation("search", System.currentTimeMillis() - start, "error", 0);
            handleHttpError(e, "search");
            return List.of();
        }
    }

    /**
     * Retrieves details and formats for a single Project Gutenberg book by ID.
     *
     * @param bookId Gutenberg numerical ID
     * @return normalized {@link Book} object
     * @throws BookProviderException on 404, rate limits, or network failures
     */
    @Cacheable(value = "gutendexBookDetail", key = "#bookId")
    public Book getBookDetail(int bookId) {
        long start = System.currentTimeMillis();

        try {
            GutendexBook book = restClient.get()
                    .uri("/books/{id}/", bookId)
                    .retrieve()
                    .onStatus(status -> status == HttpStatus.NOT_FOUND, (req, res) -> {
                        throw new BookProviderException(PROVIDER, "Book not found with ID: " + bookId);
                    })
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new BookProviderException(PROVIDER,
                                "Book detail request failed with HTTP " + res.getStatusCode() + " for ID: " + bookId);
                    })
                    .body(GutendexBook.class);

            if (book == null) {
                throw new BookProviderException(PROVIDER, "Empty response for book ID: " + bookId);
            }

            Book normalized = mapper.toBook(book);
            logOperation("detail", System.currentTimeMillis() - start, "success", 1);
            return normalized;

        } catch (BookProviderException e) {
            logOperation("detail", System.currentTimeMillis() - start, "error", 0);
            throw e;
        } catch (ResourceAccessException e) {
            logOperation("detail", System.currentTimeMillis() - start, "timeout", 0);
            throw new BookProviderException(PROVIDER,
                    "Book detail timed out for ID: " + bookId, e);
        } catch (RestClientResponseException e) {
            logOperation("detail", System.currentTimeMillis() - start, "error", 0);
            handleHttpError(e, "detail");
            return null;
        }
    }

    private void handleHttpError(RestClientResponseException e, String operation) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        if (status == HttpStatus.TOO_MANY_REQUESTS) {
            throw new BookProviderException(PROVIDER,
                    "Rate limit exceeded during " + operation + " — retry later", e);
        }
        throw new BookProviderException(PROVIDER,
                "HTTP " + e.getStatusCode() + " during " + operation, e);
    }

    private void logOperation(String operation, long durationMs, String status, int resultCount) {
        log.info("provider={} operation={} duration={}ms status={} resultCount={}",
                PROVIDER, operation, durationMs, status, resultCount);
    }
}

