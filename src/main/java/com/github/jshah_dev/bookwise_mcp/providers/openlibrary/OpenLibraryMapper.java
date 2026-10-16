package com.github.jshah_dev.bookwise_mcp.providers.openlibrary;

import com.github.jshah_dev.bookwise_mcp.model.Book;
import com.github.jshah_dev.bookwise_mcp.providers.openlibrary.OpenLibraryModels.SearchDoc;
import com.github.jshah_dev.bookwise_mcp.providers.openlibrary.OpenLibraryModels.WorkDetail;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Maps raw Open Library DTO objects to the normalized {@link Book} domain model.
 * <p>
 * Mapping rules:
 * <ul>
 *   <li>Missing or blank text fields are mapped to {@code null} — never fabricated.</li>
 *   <li>Missing list fields are mapped to empty lists.</li>
 *   <li>Cover URLs are built from the Open Library cover service when a cover ID is present.</li>
 *   <li>The description field in a work record can be either a plain {@code String}
 *       or a Jackson-deserialized {@code Map<String,Object>} with a {@code "value"} key —
 *       both cases are handled.</li>
 * </ul>
 */
@Component
public class OpenLibraryMapper {

    /** Provider identifier used in all {@link Book} records produced by this mapper. */
    static final String PROVIDER = "openlibrary";

    /** Base URL of the Open Library cover image service. */
    private static final String COVER_BASE_URL = "https://covers.openlibrary.org/b/id/%d-L.jpg";

    /** Base URL for a canonical work page on openlibrary.org. */
    private static final String WORK_BASE_URL = "https://openlibrary.org";

    // ── Search result mapping ─────────────────────────────────────────────────

    /**
     * Converts a list of {@link SearchDoc} records from a search response into
     * normalized {@link Book} objects.
     *
     * @param docs list of raw search documents; must not be {@code null}
     * @return normalized books, preserving the original ordering
     */
    public List<Book> toBooks(List<SearchDoc> docs) {
        return docs.stream()
                .map(this::toBook)
                .toList();
    }

    /**
     * Converts a single {@link SearchDoc} into a {@link Book}.
     *
     * @param doc raw search document; must not be {@code null}
     * @return normalized {@link Book}
     */
    public Book toBook(SearchDoc doc) {
        // Extract the work ID from the key path, e.g. "/works/OL123W" → "OL123W"
        String providerId = extractWorkId(doc.key());

        return new Book.Builder()
                .provider(PROVIDER)
                .providerId(providerId)
                .title(doc.title())
                .authors(nullSafeList(doc.authorName()))
                .publicationYear(doc.firstPublishYear())
                .subjects(limitList(nullSafeList(doc.subject()), 20))
                .languages(nullSafeList(doc.language()))
                .isbn(primaryIsbn(doc.isbn()))
                .sourceUrl(WORK_BASE_URL + doc.key())
                .coverUrl(buildCoverUrl(doc.coverI()))
                .build();
    }

    // ── Work detail mapping ───────────────────────────────────────────────────

    /**
     * Converts a {@link WorkDetail} into a detailed {@link Book}.
     * <p>
     * Author names are not available in a work detail response without additional
     * API calls, so {@code authors} will be an empty list — callers may enrich
     * this from a prior search result if needed.
     *
     * @param detail raw work detail; must not be {@code null}
     * @return normalized {@link Book} with available fields populated
     */
    public Book toBookDetail(WorkDetail detail) {
        String providerId = extractWorkId(detail.key());

        // The primary cover ID is the first entry in the covers list.
        Long coverId = (detail.covers() != null && !detail.covers().isEmpty())
                ? detail.covers().get(0)
                : null;

        return new Book.Builder()
                .provider(PROVIDER)
                .providerId(providerId)
                .title(detail.title())
                .authors(List.of()) // Work detail endpoint does not embed author names directly
                .subjects(nullSafeList(detail.subjects()))
                .description(extractDescription(detail.description()))
                .sourceUrl(WORK_BASE_URL + detail.key())
                .coverUrl(buildCoverUrl(coverId))
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Extracts the bare work ID from a key path.
     * <p>
     * Open Library keys look like {@code /works/OL123W}.  This method strips the
     * leading path component and returns only {@code OL123W}.
     *
     * @param key raw key from the API, e.g. {@code /works/OL123W}
     * @return bare identifier, or the original value if parsing fails
     */
    private String extractWorkId(String key) {
        if (key == null) return null;
        int lastSlash = key.lastIndexOf('/');
        return (lastSlash >= 0 && lastSlash < key.length() - 1)
                ? key.substring(lastSlash + 1)
                : key;
    }

    /**
     * Builds a cover image URL from an Open Library cover ID.
     *
     * @param coverId cover image ID; {@code null} when no cover is available
     * @return fully-qualified cover URL, or {@code null}
     */
    private String buildCoverUrl(Long coverId) {
        return coverId != null ? String.format(COVER_BASE_URL, coverId) : null;
    }

    /**
     * Extracts the primary ISBN from a list of ISBNs.
     * Prefers 13-digit ISBNs over 10-digit; returns the first available otherwise.
     *
     * @param isbns list of ISBN strings; may be {@code null} or empty
     * @return first 13-digit ISBN, else first ISBN, else {@code null}
     */
    private String primaryIsbn(List<String> isbns) {
        if (isbns == null || isbns.isEmpty()) return null;
        return isbns.stream()
                .filter(i -> i != null && i.length() == 13)
                .findFirst()
                .orElse(isbns.get(0));
    }

    /**
     * Extracts a plain text description from the polymorphic {@code description}
     * field in a work detail response.
     * <p>
     * Open Library encodes descriptions in two ways:
     * <ul>
     *   <li>A plain {@code String}: returned as-is.</li>
     *   <li>A {@code Map<String, Object>} with a {@code "value"} key: the value is extracted.</li>
     * </ul>
     *
     * @param raw raw Jackson-deserialized object; may be {@code null}
     * @return plain text description, or {@code null}
     */
    @SuppressWarnings("unchecked")
    private String extractDescription(Object raw) {
        if (raw == null) return null;
        if (raw instanceof String s) return blankToNull(s);
        if (raw instanceof Map<?, ?> map) {
            Object value = ((Map<String, Object>) map).get("value");
            return value instanceof String s ? blankToNull(s) : null;
        }
        return null;
    }

    /** Returns {@code null} if the string is blank, otherwise the trimmed string. */
    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    /**
     * Returns the list itself if non-null, or an empty list.
     * The result is a mutable copy safe for modification by the builder's compact constructor.
     */
    private <T> List<T> nullSafeList(List<T> list) {
        return list != null ? list : List.of();
    }

    /**
     * Returns the first {@code maxSize} elements of a list.
     * Avoids sending excessively large subject lists to MCP clients.
     */
    private <T> List<T> limitList(List<T> list, int maxSize) {
        if (list.size() <= maxSize) return list;
        return list.subList(0, maxSize);
    }
}

