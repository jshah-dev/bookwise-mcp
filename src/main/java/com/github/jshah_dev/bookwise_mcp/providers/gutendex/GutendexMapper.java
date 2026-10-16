package com.github.jshah_dev.bookwise_mcp.providers.gutendex;

import com.github.jshah_dev.bookwise_mcp.model.Book;
import com.github.jshah_dev.bookwise_mcp.providers.gutendex.GutendexModels.GutendexBook;
import com.github.jshah_dev.bookwise_mcp.providers.gutendex.GutendexModels.Person;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Maps raw Gutendex API response DTOs to the normalized {@link Book} domain model.
 * <p>
 * Key considerations:
 * <ul>
 *   <li>Provider identifier is always {@code "gutendex"}.</li>
 *   <li>The source URL is canonical: {@code https://www.gutenberg.org/ebooks/{id}}.</li>
 *   <li>Download URLs and ebook formats are taken strictly as provided by Gutendex.</li>
 *   <li>Cover images are extracted from formats if present (e.g. {@code image/jpeg}).</li>
 *   <li>Missing metadata fields remain {@code null} or empty lists, never fabricated.</li>
 * </ul>
 */
@Component
public class GutendexMapper {

    /** Provider identifier used across normalized {@link Book} records. */
    static final String PROVIDER = "gutendex";

    /** Canonical Project Gutenberg web page base URL. */
    private static final String GUTENBERG_BOOK_URL = "https://www.gutenberg.org/ebooks/%d";

    /** MIME types commonly used for cover images in Gutenberg formats. */
    private static final String MIME_IMAGE_JPEG = "image/jpeg";

    /**
     * Converts a collection of raw {@link GutendexBook} items to a list of normalized {@link Book} objects.
     *
     * @param books raw book records
     * @return normalized books list, preserving order
     */
    public List<Book> toBooks(List<GutendexBook> books) {
        if (books == null || books.isEmpty()) {
            return List.of();
        }
        return books.stream()
                .filter(Objects::nonNull)
                .map(this::toBook)
                .toList();
    }

    /**
     * Converts an individual {@link GutendexBook} to a normalized {@link Book}.
     *
     * @param book raw Gutendex book record
     * @return normalized {@link Book}
     */
    public Book toBook(GutendexBook book) {
        if (book == null) {
            return null;
        }

        String providerId = String.valueOf(book.id());
        String sourceUrl = String.format(GUTENBERG_BOOK_URL, book.id());

        List<String> authorNames = extractAuthorNames(book.authors());
        Map<String, String> formats = book.formats() != null ? book.formats() : Collections.emptyMap();
        String coverUrl = extractCoverUrl(formats);

        return new Book.Builder()
                .provider(PROVIDER)
                .providerId(providerId)
                .title(cleanTitle(book.title()))
                .authors(authorNames)
                .subjects(book.subjects() != null ? book.subjects() : List.of())
                .languages(book.languages() != null ? book.languages() : List.of())
                .sourceUrl(sourceUrl)
                .coverUrl(coverUrl)
                .formats(formats)
                .build();
    }

    /**
     * Extracts author display names from Person objects.
     * Converts "Last, First" into "First Last" when possible, preserving original if unusual.
     *
     * @param persons list of Person objects
     * @return formatted author names
     */
    private List<String> extractAuthorNames(List<Person> persons) {
        if (persons == null || persons.isEmpty()) {
            return List.of();
        }
        return persons.stream()
                .filter(Objects::nonNull)
                .map(Person::name)
                .filter(name -> name != null && !name.isBlank())
                .map(this::formatAuthorName)
                .toList();
    }

    /**
     * Formats Gutendex author names from "LastName, FirstName" to "FirstName LastName".
     */
    private String formatAuthorName(String rawName) {
        if (rawName == null) return null;
        String trimmed = rawName.trim();
        int commaIndex = trimmed.indexOf(',');
        if (commaIndex > 0 && commaIndex < trimmed.length() - 1) {
            String last = trimmed.substring(0, commaIndex).trim();
            String first = trimmed.substring(commaIndex + 1).trim();
            return first + " " + last;
        }
        return trimmed;
    }

    /**
     * Attempts to find a cover image URL from the formats map.
     */
    private String extractCoverUrl(Map<String, String> formats) {
        if (formats == null || formats.isEmpty()) {
            return null;
        }
        return formats.get(MIME_IMAGE_JPEG);
    }

    /**
     * Trims and cleans the title string.
     */
    private String cleanTitle(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        return title.trim();
    }
}

