package com.github.jshah_dev.bookwise_mcp.providers.gutendex;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Jackson-mapped DTOs for the Gutendex (Project Gutenberg) REST API responses.
 * <p>
 * All records use {@code @JsonIgnoreProperties(ignoreUnknown = true)} for resilience
 * against future schema extensions.
 * <p>
 * These types are package-private to the {@code gutendex} provider package.
 *
 * @see <a href="https://gutendex.com/">Gutendex API</a>
 */
public final class GutendexModels {

    /** Prevent instantiation – static container class. */
    private GutendexModels() {}

    /**
     * Top-level response from {@code GET /books/}.
     *
     * @param count    total count of matching Gutenberg books
     * @param next     URL for the next page of results, or {@code null}
     * @param previous URL for the previous page of results, or {@code null}
     * @param results  list of book records in the current page
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BookListResponse(
            @JsonProperty("count")    int count,
            @JsonProperty("next")     String next,
            @JsonProperty("previous") String previous,
            @JsonProperty("results")  List<GutendexBook> results
    ) {}

    /**
     * Representation of a single Project Gutenberg book returned by Gutendex.
     * Returned in search results as well as by {@code GET /books/{id}/}.
     *
     * @param id            Project Gutenberg identifier (e.g. 1342)
     * @param title         title of the book
     * @param authors       list of authors with names and birth/death years
     * @param subjects      subject categories and Library of Congress classifications
     * @param languages     list of ISO 639-1 language codes (e.g. ["en"])
     * @param formats       map of MIME types to download/read URLs
     * @param downloadCount download count on Project Gutenberg
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GutendexBook(
            @JsonProperty("id")             int id,
            @JsonProperty("title")          String title,
            @JsonProperty("authors")        List<Person> authors,
            @JsonProperty("subjects")       List<String> subjects,
            @JsonProperty("languages")      List<String> languages,
            @JsonProperty("formats")        Map<String, String> formats,
            @JsonProperty("download_count") Integer downloadCount
    ) {}

    /**
     * Representation of an author or contributor in Gutendex.
     *
     * @param name      author's name (typically in "Last, First" format, e.g. "Austen, Jane")
     * @param birthYear birth year, or {@code null}
     * @param deathYear death year, or {@code null}
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Person(
            @JsonProperty("name")       String name,
            @JsonProperty("birth_year") Integer birthYear,
            @JsonProperty("death_year") Integer deathYear
    ) {}
}

