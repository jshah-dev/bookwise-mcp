package com.github.jshah_dev.bookwise_mcp.providers.openlibrary;

import com.github.jshah_dev.bookwise_mcp.exception.BookProviderException;
import com.github.jshah_dev.bookwise_mcp.model.Book;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration-style tests for {@link OpenLibraryClient} using WireMock to simulate
 * the Open Library REST API.
 * <p>
 * No Spring context is loaded — the client and its dependencies are wired manually
 * and pointed directly at WireMock.  No actual network calls are made so these tests
 * run fully offline.
 * <p>
 * Note: {@code @Cacheable} is <em>not</em> active here because the client is
 * instantiated directly without a Spring proxy.  This is intentional — it allows
 * each test to verify the real HTTP path without cache interference.
 * <p>
 * Covered scenarios:
 * <ul>
 *   <li>Successful search returning multiple books</li>
 *   <li>Search returning zero results (empty docs array)</li>
 *   <li>HTTP 500 — provider unavailable, must throw, not return empty list</li>
 *   <li>HTTP 429 — rate-limited, must throw</li>
 *   <li>HTTP 404 on work detail — must throw</li>
 *   <li>HTTP 503 on work detail — must throw</li>
 *   <li>Successful work detail fetch</li>
 *   <li>Malformed JSON — must surface an exception</li>
 * </ul>
 */
class OpenLibraryClientTest {

    private WireMockServer wireMock;

    /** A fresh, non-proxied client pointing at WireMock — no caching layer. */
    private OpenLibraryClient client;

    @BeforeEach
    void setUp() {
        // Start WireMock on a random port to avoid port conflicts when tests run in parallel
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        // Configure the RestClient with a short timeout and WireMock as the backend
        RestClient.Builder builder = RestClient.builder();
        RestClientCustomizer timeoutCustomizer = b -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(2_000);
            factory.setReadTimeout(3_000);
            b.requestFactory(factory);
        };
        timeoutCustomizer.customize(builder);

        // Point the builder at WireMock; use the package-private 3-arg constructor
        // to override the base URL without a Spring context.
        client = new OpenLibraryClient(builder, new OpenLibraryMapper(),
                "http://localhost:" + wireMock.port());
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    // ── Search tests ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("returns normalized books for a successful search response")
    void successfulSearch() {
        wireMock.stubFor(get(urlPathEqualTo("/search.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "numFound": 1,
                                  "docs": [
                                    {
                                      "key": "/works/OL12345W",
                                      "title": "Designing Data-Intensive Applications",
                                      "author_name": ["Martin Kleppmann"],
                                      "first_publish_year": 2017,
                                      "subject": ["Distributed systems"],
                                      "language": ["eng"],
                                      "isbn": ["9781449373320"],
                                      "cover_i": 8739161
                                    }
                                  ]
                                }
                                """)));

        List<Book> books = client.searchBooks("distributed systems", null, 5);

        assertThat(books).hasSize(1);
        Book book = books.get(0);
        assertThat(book.provider()).isEqualTo("openlibrary");
        assertThat(book.providerId()).isEqualTo("OL12345W");
        assertThat(book.title()).isEqualTo("Designing Data-Intensive Applications");
        assertThat(book.authors()).containsExactly("Martin Kleppmann");
        assertThat(book.publicationYear()).isEqualTo(2017);
        assertThat(book.isbn()).isEqualTo("9781449373320");
        assertThat(book.coverUrl()).contains("8739161");
    }

    @Test
    @DisplayName("returns empty list when search yields zero results")
    void emptySearchResults() {
        wireMock.stubFor(get(urlPathEqualTo("/search.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"numFound": 0, "docs": []}
                                """)));

        // An empty result set is valid — it must NOT be treated as a provider failure
        List<Book> books = client.searchBooks("xyzzy-nonexistent", null, 10);
        assertThat(books).isEmpty();
    }

    @Test
    @DisplayName("throws BookProviderException on HTTP 500 — must not silently return empty list")
    void throws_on_server_error() {
        wireMock.stubFor(get(urlPathEqualTo("/search.json"))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> client.searchBooks("anything", null, 5))
                .isInstanceOf(BookProviderException.class)
                .hasMessageContaining("openlibrary");
    }

    @Test
    @DisplayName("throws BookProviderException on HTTP 429 (rate limited)")
    void throws_on_rate_limit() {
        wireMock.stubFor(get(urlPathEqualTo("/search.json"))
                .willReturn(aResponse().withStatus(429)));

        assertThatThrownBy(() -> client.searchBooks("popular book", null, 5))
                .isInstanceOf(BookProviderException.class)
                .hasMessageContaining("openlibrary");
    }

    @Test
    @DisplayName("throws an exception when the response body is not valid JSON")
    void throws_on_malformed_json() {
        wireMock.stubFor(get(urlPathEqualTo("/search.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("NOT_JSON{")));

        // Jackson decode failure wraps as RestClientException
        assertThatThrownBy(() -> client.searchBooks("test", null, 5))
                .isInstanceOf(Exception.class);
    }

    // ── Work detail tests ─────────────────────────────────────────────────────

    @Test
    @DisplayName("returns detailed book for a successful work detail response")
    void successfulWorkDetail() {
        wireMock.stubFor(get(urlPathEqualTo("/works/OL12345W.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "key": "/works/OL12345W",
                                  "title": "Designing Data-Intensive Applications",
                                  "description": {
                                    "type": "/type/text",
                                    "value": "A comprehensive guide to data systems."
                                  },
                                  "subjects": ["Distributed systems", "Databases"],
                                  "covers": [8739161]
                                }
                                """)));

        Book book = client.getBookDetail("OL12345W");

        assertThat(book.providerId()).isEqualTo("OL12345W");
        assertThat(book.title()).isEqualTo("Designing Data-Intensive Applications");
        assertThat(book.description()).isEqualTo("A comprehensive guide to data systems.");
        assertThat(book.subjects()).containsExactlyInAnyOrder("Distributed systems", "Databases");
        assertThat(book.coverUrl()).contains("8739161");
    }

    @Test
    @DisplayName("throws BookProviderException on HTTP 404 for unknown work")
    void throws_on_work_not_found() {
        wireMock.stubFor(get(urlPathEqualTo("/works/INVALID.json"))
                .willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> client.getBookDetail("INVALID"))
                .isInstanceOf(BookProviderException.class)
                .hasMessageContaining("openlibrary");
    }

    @Test
    @DisplayName("throws BookProviderException on HTTP 503 for work detail")
    void throws_on_work_service_unavailable() {
        wireMock.stubFor(get(urlPathEqualTo("/works/OL99W.json"))
                .willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> client.getBookDetail("OL99W"))
                .isInstanceOf(BookProviderException.class)
                .hasMessageContaining("openlibrary");
    }
}
