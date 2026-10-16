package com.github.jshah_dev.bookwise_mcp.tools;

import com.github.jshah_dev.bookwise_mcp.model.Book;
import com.github.jshah_dev.bookwise_mcp.service.BookCatalogService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * MCP Tools exposed by the BookWise MCP server.
 * <p>
 * Provides four book-discovery capabilities:
 * <ol>
 *   <li>{@code search_books} — Searches Open Library for book and edition metadata.</li>
 *   <li>{@code get_book_details} — Retrieves detailed work information from Open Library.</li>
 *   <li>{@code search_free_ebooks} — Searches Gutendex / Project Gutenberg for public domain ebooks.</li>
 *   <li>{@code get_free_ebook_details} — Retrieves Gutendex ebook details and format download URLs.</li>
 * </ol>
 * <p>
 * Performs parameter validation on all inputs before delegating to {@link BookCatalogService}.
 */
@Component
public class BookTools {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 20;

    /** Open Library work IDs follow patterns like OL123W or /works/OL123W */
    private static final Pattern WORK_ID_PATTERN = Pattern.compile("^(?:/works/)?OL[0-9]+W$", Pattern.CASE_INSENSITIVE);

    /** Language code pattern (2-3 letter ISO 639 codes) */
    private static final Pattern LANG_PATTERN = Pattern.compile("^[a-zA-Z]{2,3}$");

    private final BookCatalogService catalogService;

    public BookTools(BookCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    /**
     * DTO envelope for returning book lists to MCP clients.
     */
    public record BooksResult(List<Book> books) {}

    /**
     * Search books in Open Library.
     *
     * @param query    search query (title, author, topic)
     * @param language optional ISO language code (e.g. 'en')
     * @param limit    maximum number of results to return (1 to 20, default 10)
     * @return normalized search results
     */
    @McpTool(
            name = "search_books",
            description = "Search Open Library for books by query, topic, author, or keyword. Returns normalized book metadata."
    )
    public BooksResult searchBooks(
            @McpToolParam(required = true, description = "Search query string (e.g. 'distributed systems', 'clean code')")
            String query,
            @McpToolParam(required = false, description = "Optional ISO 639 language code (e.g. 'en')")
            String language,
            @McpToolParam(required = false, description = "Maximum number of books to return (1-20, default: 10)")
            Integer limit
    ) {
        validateQuery(query);
        validateLanguage(language);
        int effectiveLimit = sanitizeLimit(limit);

        List<Book> results = catalogService.searchOpenLibrary(query.trim(), language != null ? language.trim() : null, effectiveLimit);
        return new BooksResult(results);
    }

    /**
     * Retrieve detailed work information from Open Library.
     *
     * @param workId Open Library work ID (e.g. 'OL12345W' or '/works/OL12345W')
     * @return detailed book metadata
     */
    @McpTool(
            name = "get_book_details",
            description = "Retrieve detailed metadata for a specific work from Open Library using its work identifier."
    )
    public Book getBookDetails(
            @McpToolParam(required = true, description = "Open Library work identifier (e.g. 'OL12345W')")
            String workId
    ) {
        if (workId == null || workId.isBlank()) {
            throw new IllegalArgumentException("Parameter 'workId' must not be blank.");
        }
        String cleanId = workId.trim();
        if (!WORK_ID_PATTERN.matcher(cleanId).matches()) {
            throw new IllegalArgumentException("Invalid Open Library workId format: '" + workId + "'. Expected format e.g. 'OL12345W'.");
        }
        if (cleanId.startsWith("/works/")) {
            cleanId = cleanId.substring("/works/".length());
        }

        return catalogService.getOpenLibraryWorkDetail(cleanId);
    }

    /**
     * Search free public domain ebooks in Project Gutenberg via Gutendex.
     *
     * @param query    search query
     * @param language optional ISO language code
     * @param limit    maximum results (1 to 20, default 10)
     * @return normalized books with available ebook formats
     */
    @McpTool(
            name = "search_free_ebooks",
            description = "Search Project Gutenberg via Gutendex for free, public domain ebooks and download/read formats."
    )
    public BooksResult searchFreeEbooks(
            @McpToolParam(required = true, description = "Search query string for free ebooks (e.g. 'pride and prejudice')")
            String query,
            @McpToolParam(required = false, description = "Optional ISO 639 language code (e.g. 'en')")
            String language,
            @McpToolParam(required = false, description = "Maximum number of ebooks to return (1-20, default: 10)")
            Integer limit
    ) {
        validateQuery(query);
        validateLanguage(language);
        int effectiveLimit = sanitizeLimit(limit);

        List<Book> results = catalogService.searchFreeEbooks(query.trim(), language != null ? language.trim() : null, effectiveLimit);
        return new BooksResult(results);
    }

    /**
     * Retrieve details and format URLs for a specific Project Gutenberg ebook.
     *
     * @param bookId numerical Gutenberg book ID
     * @return ebook metadata and download URLs
     */
    @McpTool(
            name = "get_free_ebook_details",
            description = "Retrieve metadata and read/download format URLs for a specific Project Gutenberg ebook by its numerical ID."
    )
    public Book getFreeEbookDetails(
            @McpToolParam(required = true, description = "Numerical Project Gutenberg ID (e.g. 1342)")
            Integer bookId
    ) {
        if (bookId == null || bookId <= 0) {
            throw new IllegalArgumentException("Parameter 'bookId' must be a positive integer.");
        }

        return catalogService.getFreeEbookDetail(bookId);
    }

    // ── Input Validation Helpers ──────────────────────────────────────────────

    private void validateQuery(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Search query must not be null or blank.");
        }
    }

    private void validateLanguage(String language) {
        if (language != null && !language.isBlank()) {
            if (!LANG_PATTERN.matcher(language.trim()).matches()) {
                throw new IllegalArgumentException("Invalid language code: '" + language + "'. Expected 2 or 3-letter ISO code (e.g. 'en').");
            }
        }
    }

    private int sanitizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < MIN_LIMIT || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("Parameter 'limit' must be between " + MIN_LIMIT + " and " + MAX_LIMIT + ".");
        }
        return limit;
    }
}

