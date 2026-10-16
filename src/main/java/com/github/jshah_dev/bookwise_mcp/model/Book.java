package com.github.jshah_dev.bookwise_mcp.model;

import java.util.List;
import java.util.Map;

/**
 * Normalized representation of a book, shared across all provider integrations.
 * <p>
 * Fields are {@code null} when not supplied by a particular provider — the server
 * never fabricates missing metadata.  Use {@link Builder} to construct instances.
 *
 * @param provider        Identifier of the data source (e.g. {@code "openlibrary"}, {@code "gutendex"}).
 * @param providerId      Provider-specific unique identifier (e.g. Open Library work key, Gutenberg ID).
 * @param title           Title of the book.
 * @param authors         Ordered list of author names; may be empty but never {@code null}.
 * @param publicationYear Year of first publication, or {@code null} if unknown.
 * @param subjects        Subject / topic tags; may be empty but never {@code null}.
 * @param languages       ISO 639-1 language codes; may be empty but never {@code null}.
 * @param description     Summary or blurb; {@code null} when not available.
 * @param isbn            Primary ISBN (10 or 13 digits); {@code null} when not available.
 * @param sourceUrl       Canonical URL on the provider's website.
 * @param coverUrl        URL of the cover image; {@code null} when not available.
 * @param formats         Map of MIME type → download URL for ebook formats (Gutendex only).
 */
public record Book(
        String provider,
        String providerId,
        String title,
        List<String> authors,
        Integer publicationYear,
        List<String> subjects,
        List<String> languages,
        String description,
        String isbn,
        String sourceUrl,
        String coverUrl,
        Map<String, String> formats
) {

    /**
     * Compact constructor that enforces non-null list fields.
     * Ensures {@code authors}, {@code subjects}, and {@code languages} are never {@code null},
     * even if a provider omits them.
     */
    public Book {
        authors  = authors  != null ? List.copyOf(authors)  : List.of();
        subjects = subjects != null ? List.copyOf(subjects) : List.of();
        languages = languages != null ? List.copyOf(languages) : List.of();
        formats  = formats  != null ? Map.copyOf(formats)  : Map.of();
    }

    // ── Builder ──────────────────────────────────────────────────────────────

    /**
     * Fluent builder for {@link Book}.
     * Allows partial construction where providers do not supply every field.
     */
    public static final class Builder {

        private String provider;
        private String providerId;
        private String title;
        private List<String> authors;
        private Integer publicationYear;
        private List<String> subjects;
        private List<String> languages;
        private String description;
        private String isbn;
        private String sourceUrl;
        private String coverUrl;
        private Map<String, String> formats;

        public Builder provider(String provider) {
            this.provider = provider;
            return this;
        }

        public Builder providerId(String providerId) {
            this.providerId = providerId;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder authors(List<String> authors) {
            this.authors = authors;
            return this;
        }

        public Builder publicationYear(Integer publicationYear) {
            this.publicationYear = publicationYear;
            return this;
        }

        public Builder subjects(List<String> subjects) {
            this.subjects = subjects;
            return this;
        }

        public Builder languages(List<String> languages) {
            this.languages = languages;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder isbn(String isbn) {
            this.isbn = isbn;
            return this;
        }

        public Builder sourceUrl(String sourceUrl) {
            this.sourceUrl = sourceUrl;
            return this;
        }

        public Builder coverUrl(String coverUrl) {
            this.coverUrl = coverUrl;
            return this;
        }

        public Builder formats(Map<String, String> formats) {
            this.formats = formats;
            return this;
        }

        /** Constructs and returns the immutable {@link Book} record. */
        public Book build() {
            return new Book(
                    provider, providerId, title, authors, publicationYear,
                    subjects, languages, description, isbn,
                    sourceUrl, coverUrl, formats
            );
        }
    }
}

