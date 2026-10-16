package com.github.jshah_dev.bookwise_mcp.providers.openlibrary;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Jackson-mapped DTOs for the Open Library REST API responses.
 * <p>
 * All records use {@code @JsonIgnoreProperties(ignoreUnknown = true)} so that new
 * fields added by the API in the future do not break deserialization.
 * <p>
 * These types are intentionally package-private to the {@code openlibrary} provider
 * package and must not leak into service or tool layers.
 */
public final class OpenLibraryModels {

    /** Prevent instantiation – this is a static container class. */
    private OpenLibraryModels() {}

    // ── Search API (/search.json) ─────────────────────────────────────────────

    /**
     * Top-level response from {@code GET /search.json}.
     *
     * @param numFound total number of matching works across all pages
     * @param docs     list of matching work summaries in this page
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SearchResponse(
            @JsonProperty("numFound") int numFound,
            @JsonProperty("docs")     List<SearchDoc> docs
    ) {}

    /**
     * Single work summary returned inside a search result page.
     * <p>
     * Fields may be absent — the Open Library API only guarantees {@code key} and
     * {@code title}.  All other fields are best-effort.
     *
     * @param key              Work key in the form {@code /works/OL123W}.
     * @param title            Title of the work.
     * @param authorName       Ordered list of author display names.
     * @param firstPublishYear Year of the earliest known edition.
     * @param subject          Subject tags for the work.
     * @param language         ISO language codes of known editions.
     * @param isbn             List of ISBNs across all editions.
     * @param coverI           Cover image ID (used to build cover URLs).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SearchDoc(
            @JsonProperty("key")               String       key,
            @JsonProperty("title")             String       title,
            @JsonProperty("author_name")       List<String> authorName,
            @JsonProperty("first_publish_year") Integer     firstPublishYear,
            @JsonProperty("subject")           List<String> subject,
            @JsonProperty("language")          List<String> language,
            @JsonProperty("isbn")              List<String> isbn,
            @JsonProperty("cover_i")           Long         coverI
    ) {}

    // ── Works API (/works/{id}.json) ─────────────────────────────────────────

    /**
     * Detailed work record from {@code GET /works/{workId}.json}.
     *
     * @param key         Work key, e.g. {@code /works/OL123W}.
     * @param title       Title of the work.
     * @param description Description field — Open Library uses either a plain
     *                    {@code String} or a {@code {"type": "...", "value": "..."}}
     *                    object for this field, so it is modelled as {@link Object}
     *                    and extracted by {@link OpenLibraryMapper}.
     * @param subjects    Subject tags.
     * @param authors     Author references (each contains a nested author key).
     * @param covers      Cover image IDs; the first entry is the primary cover.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WorkDetail(
            @JsonProperty("key")         String            key,
            @JsonProperty("title")       String            title,
            @JsonProperty("description") Object            description,
            @JsonProperty("subjects")    List<String>      subjects,
            @JsonProperty("authors")     List<AuthorRef>   authors,
            @JsonProperty("covers")      List<Long>        covers
    ) {}

    /**
     * Reference to an author embedded inside a {@link WorkDetail}.
     * The actual author name must be fetched separately if needed.
     *
     * @param author nested object containing the author's key
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AuthorRef(
            @JsonProperty("author") AuthorKey author
    ) {}

    /**
     * Minimal author key reference used within a work record.
     *
     * @param key author key, e.g. {@code /authors/OL123A}
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AuthorKey(
            @JsonProperty("key") String key
    ) {}

    /**
     * Minimal author record returned by {@code GET /authors/{authorId}.json}.
     *
     * @param name display name of the author
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AuthorDetail(
            @JsonProperty("name") String name
    ) {}
}

