package com.github.jshah_dev.bookwise_mcp.tools;

import com.github.jshah_dev.bookwise_mcp.model.Book;
import com.github.jshah_dev.bookwise_mcp.service.BookCatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link BookTools} input validation and service delegation.
 */
@ExtendWith(MockitoExtension.class)
class BookToolsTest {

    @Mock
    private BookCatalogService catalogService;

    private BookTools tools;

    @BeforeEach
    void setUp() {
        tools = new BookTools(catalogService);
    }

    @Nested
    @DisplayName("search_books validation")
    class SearchBooksValidation {

        @Test
        @DisplayName("validates search query cannot be null or blank")
        void rejectsBlankQuery() {
            assertThatThrownBy(() -> tools.searchBooks(null, "en", 10))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be null or blank");

            assertThatThrownBy(() -> tools.searchBooks("   ", "en", 10))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be null or blank");
        }

        @Test
        @DisplayName("validates limit boundary")
        void rejectsInvalidLimits() {
            assertThatThrownBy(() -> tools.searchBooks("valid", "en", 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("between 1 and 20");

            assertThatThrownBy(() -> tools.searchBooks("valid", "en", 21))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("between 1 and 20");
        }

        @Test
        @DisplayName("validates language code format")
        void rejectsInvalidLanguage() {
            assertThatThrownBy(() -> tools.searchBooks("valid", "english", 10))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid language code");
        }

        @Test
        @DisplayName("delegates properly with defaults")
        void delegatesSuccessfully() {
            Book mockBook = new Book.Builder().title("Book").provider("openlibrary").build();
            when(catalogService.searchOpenLibrary("java", null, 10)).thenReturn(List.of(mockBook));

            BookTools.BooksResult result = tools.searchBooks("java", null, null);

            assertThat(result.books()).containsExactly(mockBook);
            verify(catalogService).searchOpenLibrary("java", null, 10);
        }
    }

    @Nested
    @DisplayName("get_book_details validation")
    class GetBookDetailsValidation {

        @Test
        @DisplayName("rejects blank or invalid work IDs")
        void rejectsInvalidWorkIds() {
            assertThatThrownBy(() -> tools.getBookDetails(null))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> tools.getBookDetails("invalid-id"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid Open Library workId format");
        }

        @Test
        @DisplayName("accepts valid OL work ID with and without prefix")
        void acceptsValidWorkId() {
            Book mockBook = new Book.Builder().title("Work").provider("openlibrary").providerId("OL123W").build();
            when(catalogService.getOpenLibraryWorkDetail("OL123W")).thenReturn(mockBook);

            Book res1 = tools.getBookDetails("OL123W");
            Book res2 = tools.getBookDetails("/works/OL123W");

            assertThat(res1).isEqualTo(mockBook);
            assertThat(res2).isEqualTo(mockBook);
            verify(catalogService, times(2)).getOpenLibraryWorkDetail("OL123W");
        }
    }

    @Nested
    @DisplayName("search_free_ebooks validation")
    class SearchFreeEbooksValidation {

        @Test
        @DisplayName("rejects blank query")
        void rejectsBlankQuery() {
            assertThatThrownBy(() -> tools.searchFreeEbooks("", null, 5))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("delegates successfully")
        void delegatesSuccessfully() {
            Book mockBook = new Book.Builder().title("Gutenberg").provider("gutendex").build();
            when(catalogService.searchFreeEbooks("science", "en", 5)).thenReturn(List.of(mockBook));

            BookTools.BooksResult result = tools.searchFreeEbooks("science", "en", 5);

            assertThat(result.books()).containsExactly(mockBook);
            verify(catalogService).searchFreeEbooks("science", "en", 5);
        }
    }

    @Nested
    @DisplayName("get_free_ebook_details validation")
    class GetFreeEbookDetailsValidation {

        @Test
        @DisplayName("rejects non-positive book ID")
        void rejectsInvalidBookId() {
            assertThatThrownBy(() -> tools.getFreeEbookDetails(null))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> tools.getFreeEbookDetails(0))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> tools.getFreeEbookDetails(-5))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("delegates valid book ID")
        void delegatesSuccessfully() {
            Book mockBook = new Book.Builder().title("Pride and Prejudice").provider("gutendex").providerId("1342").build();
            when(catalogService.getFreeEbookDetail(1342)).thenReturn(mockBook);

            Book result = tools.getFreeEbookDetails(1342);

            assertThat(result).isEqualTo(mockBook);
            verify(catalogService).getFreeEbookDetail(1342);
        }
    }
}

