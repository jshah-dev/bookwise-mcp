package com.github.jshah_dev.bookwise_mcp.providers.gutendex;

import com.github.jshah_dev.bookwise_mcp.model.Book;
import com.github.jshah_dev.bookwise_mcp.providers.gutendex.GutendexModels.GutendexBook;
import com.github.jshah_dev.bookwise_mcp.providers.gutendex.GutendexModels.Person;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GutendexMapper}.
 */
class GutendexMapperTest {

    private GutendexMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new GutendexMapper();
    }

    @Nested
    @DisplayName("toBook(GutendexBook)")
    class ToBook {

        @Test
        @DisplayName("correctly maps full GutendexBook to normalized Book")
        void mapsFullyPopulatedBook() {
            GutendexBook raw = new GutendexBook(
                    1342,
                    "Pride and Prejudice",
                    List.of(new Person("Austen, Jane", 1775, 1817)),
                    List.of("Courtship -- Fiction", "Sisters -- Fiction"),
                    List.of("en"),
                    Map.of(
                            "text/html", "https://www.gutenberg.org/ebooks/1342.html.images",
                            "application/epub+zip", "https://www.gutenberg.org/ebooks/1342.epub3.images",
                            "image/jpeg", "https://www.gutenberg.org/cache/epub/1342/pg1342.cover.medium.jpg"
                    ),
                    50000
            );

            Book book = mapper.toBook(raw);

            assertThat(book).isNotNull();
            assertThat(book.provider()).isEqualTo("gutendex");
            assertThat(book.providerId()).isEqualTo("1342");
            assertThat(book.title()).isEqualTo("Pride and Prejudice");
            assertThat(book.authors()).containsExactly("Jane Austen");
            assertThat(book.subjects()).containsExactly("Courtship -- Fiction", "Sisters -- Fiction");
            assertThat(book.languages()).containsExactly("en");
            assertThat(book.sourceUrl()).isEqualTo("https://www.gutenberg.org/ebooks/1342");
            assertThat(book.coverUrl()).isEqualTo("https://www.gutenberg.org/cache/epub/1342/pg1342.cover.medium.jpg");
            assertThat(book.formats())
                    .containsEntry("text/html", "https://www.gutenberg.org/ebooks/1342.html.images")
                    .containsEntry("application/epub+zip", "https://www.gutenberg.org/ebooks/1342.epub3.images");
        }

        @Test
        @DisplayName("handles book with null or missing fields gracefully")
        void handlesMissingFields() {
            GutendexBook raw = new GutendexBook(
                    999,
                    "Minimal Book",
                    null,
                    null,
                    null,
                    null,
                    null
            );

            Book book = mapper.toBook(raw);

            assertThat(book).isNotNull();
            assertThat(book.providerId()).isEqualTo("999");
            assertThat(book.title()).isEqualTo("Minimal Book");
            assertThat(book.authors()).isEmpty();
            assertThat(book.subjects()).isEmpty();
            assertThat(book.languages()).isEmpty();
            assertThat(book.coverUrl()).isNull();
            assertThat(book.formats()).isEmpty();
            assertThat(book.sourceUrl()).isEqualTo("https://www.gutenberg.org/ebooks/999");
        }

        @Test
        @DisplayName("correctly formats authors without commas")
        void handlesAuthorsWithoutCommas() {
            GutendexBook raw = new GutendexBook(
                    100,
                    "Anonymous Work",
                    List.of(new Person("Various", null, null)),
                    List.of(),
                    List.of("en"),
                    Map.of(),
                    10
            );

            Book book = mapper.toBook(raw);
            assertThat(book.authors()).containsExactly("Various");
        }

        @Test
        @DisplayName("returns null when mapping null input")
        void returnsNullOnNullInput() {
            assertThat(mapper.toBook(null)).isNull();
        }
    }

    @Nested
    @DisplayName("toBooks(List<GutendexBook>)")
    class ToBooks {

        @Test
        @DisplayName("returns empty list on null or empty input")
        void handlesEmptyList() {
            assertThat(mapper.toBooks(null)).isEmpty();
            assertThat(mapper.toBooks(List.of())).isEmpty();
        }

        @Test
        @DisplayName("maps list of books and skips null items")
        void mapsListOfBooks() {
            GutendexBook b1 = new GutendexBook(1, "Book 1", List.of(), List.of(), List.of(), Map.of(), 5);
            GutendexBook b2 = new GutendexBook(2, "Book 2", List.of(), List.of(), List.of(), Map.of(), 10);

            List<Book> books = mapper.toBooks(List.of(b1, b2));
            assertThat(books).hasSize(2);
            assertThat(books.get(0).title()).isEqualTo("Book 1");
            assertThat(books.get(1).title()).isEqualTo("Book 2");
        }
    }
}

