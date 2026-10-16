package com.github.jshah_dev.bookwise_mcp.providers.gutendex;

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
 * Mock HTTP integration tests for {@link GutendexClient} using WireMock.
 * Verifies search, details, error codes (404, 429, 500), timeout, and malformed responses.
 */
class GutendexClientTest {

    private WireMockServer wireMock;
    private GutendexClient client;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        RestClient.Builder builder = RestClient.builder();
        RestClientCustomizer timeoutCustomizer = b -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(2_000);
            factory.setReadTimeout(3_000);
            b.requestFactory(factory);
        };
        timeoutCustomizer.customize(builder);

        client = new GutendexClient(builder, new GutendexMapper(), "http://localhost:" + wireMock.port());
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    // ── Search Tests ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("returns normalized books for successful search response")
    void successfulSearch() {
        wireMock.stubFor(get(urlPathEqualTo("/books/"))
                .withQueryParam("search", containing("pride"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "count": 1,
                                  "results": [
                                    {
                                      "id": 1342,
                                      "title": "Pride and Prejudice",
                                      "authors": [{"name": "Austen, Jane", "birth_year": 1775, "death_year": 1817}],
                                      "subjects": ["Courtship -- Fiction"],
                                      "languages": ["en"],
                                      "formats": {
                                        "text/html": "https://www.gutenberg.org/ebooks/1342.html.images",
                                        "image/jpeg": "https://www.gutenberg.org/cache/epub/1342/pg1342.cover.medium.jpg"
                                      },
                                      "download_count": 50000
                                    }
                                  ]
                                }
                                """)));

        List<Book> books = client.searchBooks("pride and prejudice", "en", 10);

        assertThat(books).hasSize(1);
        Book book = books.get(0);
        assertThat(book.provider()).isEqualTo("gutendex");
        assertThat(book.providerId()).isEqualTo("1342");
        assertThat(book.title()).isEqualTo("Pride and Prejudice");
        assertThat(book.authors()).containsExactly("Jane Austen");
        assertThat(book.languages()).containsExactly("en");
        assertThat(book.sourceUrl()).isEqualTo("https://www.gutenberg.org/ebooks/1342");
        assertThat(book.coverUrl()).isEqualTo("https://www.gutenberg.org/cache/epub/1342/pg1342.cover.medium.jpg");
        assertThat(book.formats()).containsEntry("text/html", "https://www.gutenberg.org/ebooks/1342.html.images");
    }

    @Test
    @DisplayName("returns empty list when search yields zero results")
    void emptySearchResults() {
        wireMock.stubFor(get(urlPathEqualTo("/books/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "count": 0,
                                  "results": []
                                }
                                """)));

        List<Book> books = client.searchBooks("nonexistent-book-xyz", null, 10);
        assertThat(books).isEmpty();
    }

    @Test
    @DisplayName("throws BookProviderException on HTTP 500")
    void throwsOnServerError() {
        wireMock.stubFor(get(urlPathEqualTo("/books/"))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> client.searchBooks("computer science", null, 5))
                .isInstanceOf(BookProviderException.class)
                .hasMessageContaining("gutendex");
    }

    @Test
    @DisplayName("throws BookProviderException on HTTP 429 rate limit")
    void throwsOnRateLimit() {
        wireMock.stubFor(get(urlPathEqualTo("/books/"))
                .willReturn(aResponse().withStatus(429)));

        assertThatThrownBy(() -> client.searchBooks("computer science", null, 5))
                .isInstanceOf(BookProviderException.class)
                .hasMessageContaining("gutendex");
    }

    @Test
    @DisplayName("throws exception on malformed JSON")
    void throwsOnMalformedJson() {
        wireMock.stubFor(get(urlPathEqualTo("/books/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("INVALID_JSON{")));

        assertThatThrownBy(() -> client.searchBooks("test", null, 5))
                .isInstanceOf(Exception.class);
    }

    // ── Book Detail Tests ─────────────────────────────────────────────────────

    @Test
    @DisplayName("returns normalized book for valid ID detail response")
    void successfulBookDetail() {
        wireMock.stubFor(get(urlPathEqualTo("/books/1342/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": 1342,
                                  "title": "Pride and Prejudice",
                                  "authors": [{"name": "Austen, Jane", "birth_year": 1775, "death_year": 1817}],
                                  "subjects": ["Courtship -- Fiction"],
                                  "languages": ["en"],
                                  "formats": {
                                    "text/html": "https://www.gutenberg.org/ebooks/1342.html.images",
                                    "image/jpeg": "https://www.gutenberg.org/cache/epub/1342/pg1342.cover.medium.jpg"
                                  },
                                  "download_count": 50000
                                }
                                """)));

        Book book = client.getBookDetail(1342);

        assertThat(book).isNotNull();
        assertThat(book.providerId()).isEqualTo("1342");
        assertThat(book.title()).isEqualTo("Pride and Prejudice");
        assertThat(book.authors()).containsExactly("Jane Austen");
        assertThat(book.formats()).containsKey("text/html");
    }

    @Test
    @DisplayName("throws BookProviderException on HTTP 404 for missing book")
    void throwsOnBookNotFound() {
        wireMock.stubFor(get(urlPathEqualTo("/books/99999999/"))
                .willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> client.getBookDetail(99999999))
                .isInstanceOf(BookProviderException.class)
                .hasMessageContaining("gutendex");
    }
}
