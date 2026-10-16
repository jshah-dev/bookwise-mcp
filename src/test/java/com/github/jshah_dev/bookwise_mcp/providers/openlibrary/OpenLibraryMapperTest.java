package com.github.jshah_dev.bookwise_mcp.providers.openlibrary;

import com.github.jshah_dev.bookwise_mcp.model.Book;
import com.github.jshah_dev.bookwise_mcp.providers.openlibrary.OpenLibraryModels.SearchDoc;
import com.github.jshah_dev.bookwise_mcp.providers.openlibrary.OpenLibraryModels.WorkDetail;
import com.github.jshah_dev.bookwise_mcp.providers.openlibrary.OpenLibraryModels.AuthorRef;
import com.github.jshah_dev.bookwise_mcp.providers.openlibrary.OpenLibraryModels.AuthorKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OpenLibraryMapper}.
 * <p>
 * Each test constructs DTOs directly (no Spring context, no network) and verifies
 * that the mapper produces correctly normalized {@link Book} instances.
 * Covers: normal data, missing fields, polymorphic description, ISBN preference,
 * cover URL construction, and subject list truncation.
 */
class OpenLibraryMapperTest {

    private OpenLibraryMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new OpenLibraryMapper();
    }

    // ── Search doc mapping ────────────────────────────────────────────────────

    @Nested
    @DisplayName("toBook(SearchDoc)")
    class ToBook {

        @Test
        @DisplayName("maps all fields when fully populated")
        void mapsFullyPopulatedDoc() {
            SearchDoc doc = new SearchDoc(
                    "/works/OL12345W",
                    "Designing Data-Intensive Applications",
                    List.of("Martin Kleppmann"),
                    2017,
                    List.of("Distributed systems", "Databases"),
                    List.of("en"),
                    List.of("9781449373320", "1449373321"),
                    98765L
            );

            Book book = mapper.toBook(doc);

            assertThat(book.provider()).isEqualTo("openlibrary");
            assertThat(book.providerId()).isEqualTo("OL12345W");
            assertThat(book.title()).isEqualTo("Designing Data-Intensive Applications");
            assertThat(book.authors()).containsExactly("Martin Kleppmann");
            assertThat(book.publicationYear()).isEqualTo(2017);
            assertThat(book.subjects()).containsExactlyInAnyOrder("Distributed systems", "Databases");
            assertThat(book.languages()).containsExactly("en");
            // Prefers 13-digit ISBN
            assertThat(book.isbn()).isEqualTo("9781449373320");
            assertThat(book.sourceUrl()).isEqualTo("https://openlibrary.org/works/OL12345W");
            assertThat(book.coverUrl()).isEqualTo("https://covers.openlibrary.org/b/id/98765-L.jpg");
            assertThat(book.description()).isNull(); // SearchDoc has no description
        }

        @Test
        @DisplayName("handles missing optional fields without NPE")
        void handlesMissingOptionalFields() {
            SearchDoc doc = new SearchDoc(
                    "/works/OL99W",
                    "Minimal Book",
                    null,   // no authors
                    null,   // no year
                    null,   // no subjects
                    null,   // no languages
                    null,   // no isbn
                    null    // no cover
            );

            Book book = mapper.toBook(doc);

            assertThat(book.providerId()).isEqualTo("OL99W");
            assertThat(book.title()).isEqualTo("Minimal Book");
            assertThat(book.authors()).isEmpty();
            assertThat(book.publicationYear()).isNull();
            assertThat(book.subjects()).isEmpty();
            assertThat(book.languages()).isEmpty();
            assertThat(book.isbn()).isNull();
            assertThat(book.coverUrl()).isNull();
        }

        @Test
        @DisplayName("prefers 13-digit ISBN over 10-digit when both present")
        void prefersIsbn13() {
            SearchDoc doc = new SearchDoc(
                    "/works/OL1W", "Some Book",
                    null, null, null, null,
                    List.of("1449373321", "9781449373320"), // 10-digit first
                    null
            );

            assertThat(mapper.toBook(doc).isbn()).isEqualTo("9781449373320");
        }

        @Test
        @DisplayName("falls back to first ISBN when no 13-digit available")
        void fallsBackToFirstIsbn() {
            SearchDoc doc = new SearchDoc(
                    "/works/OL2W", "Other Book",
                    null, null, null, null,
                    List.of("1449373321"), // 10-digit only
                    null
            );

            assertThat(mapper.toBook(doc).isbn()).isEqualTo("1449373321");
        }

        @Test
        @DisplayName("truncates subject list to 20 entries")
        void truncatesSubjectList() {
            List<String> manySubjects = java.util.stream.IntStream.rangeClosed(1, 30)
                    .mapToObj(i -> "Subject " + i)
                    .toList();
            SearchDoc doc = new SearchDoc(
                    "/works/OL3W", "Big Book",
                    null, null, manySubjects, null, null, null
            );

            assertThat(mapper.toBook(doc).subjects()).hasSize(20);
        }
    }

    // ── Work detail mapping ───────────────────────────────────────────────────

    @Nested
    @DisplayName("toBookDetail(WorkDetail)")
    class ToBookDetail {

        @Test
        @DisplayName("maps work detail with plain String description")
        void mapsStringDescription() {
            WorkDetail detail = new WorkDetail(
                    "/works/OL55W",
                    "Clean Code",
                    "A handbook of agile software craftsmanship.",
                    List.of("Software engineering"),
                    List.of(new AuthorRef(new AuthorKey("/authors/OL1A"))),
                    List.of(11111L, 22222L)
            );

            Book book = mapper.toBookDetail(detail);

            assertThat(book.providerId()).isEqualTo("OL55W");
            assertThat(book.description()).isEqualTo("A handbook of agile software craftsmanship.");
            // Primary cover = first in list
            assertThat(book.coverUrl()).isEqualTo("https://covers.openlibrary.org/b/id/11111-L.jpg");
            // Authors not resolved by this endpoint
            assertThat(book.authors()).isEmpty();
        }

        @Test
        @DisplayName("maps work detail with Map-typed description (Open Library type/value envelope)")
        void mapsMapDescription() {
            Map<String, Object> descMap = Map.of("type", "/type/text", "value", "A great book about systems.");
            WorkDetail detail = new WorkDetail(
                    "/works/OL66W", "Systems Book", descMap,
                    null, null, null
            );

            assertThat(mapper.toBookDetail(detail).description())
                    .isEqualTo("A great book about systems.");
        }

        @Test
        @DisplayName("returns null description for blank string")
        void returnsNullForBlankDescription() {
            WorkDetail detail = new WorkDetail(
                    "/works/OL77W", "Empty Desc Book", "   ",
                    null, null, null
            );

            assertThat(mapper.toBookDetail(detail).description()).isNull();
        }

        @Test
        @DisplayName("returns null coverUrl when covers list is empty")
        void nullCoverWhenCoversEmpty() {
            WorkDetail detail = new WorkDetail(
                    "/works/OL88W", "No Cover Book", null,
                    null, null, List.of()
            );

            assertThat(mapper.toBookDetail(detail).coverUrl()).isNull();
        }
    }
}

