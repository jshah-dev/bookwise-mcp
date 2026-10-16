package com.github.jshah_dev.bookwise_mcp.service;

import com.github.jshah_dev.bookwise_mcp.model.Book;
import com.github.jshah_dev.bookwise_mcp.providers.gutendex.GutendexClient;
import com.github.jshah_dev.bookwise_mcp.providers.openlibrary.OpenLibraryClient;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Core domain service orchestrating book search, retrieval, and metadata aggregation
 * across different provider integrations (Open Library, Gutendex).
 * <p>
 * This service sits between transport/tool-facing layers and external API clients.
 * It remains completely agnostic of the MCP protocol and transport mechanism (STDIO vs HTTP).
 */
@Service
public class BookCatalogService {

    private final OpenLibraryClient openLibraryClient;
    private final GutendexClient gutendexClient;

    public BookCatalogService(OpenLibraryClient openLibraryClient, GutendexClient gutendexClient) {
        this.openLibraryClient = openLibraryClient;
        this.gutendexClient = gutendexClient;
    }

    /**
     * Searches Open Library for books matching query and language.
     *
     * @param query    search query
     * @param language optional language code
     * @param limit    result limit
     * @return list of matching normalized books
     */
    public List<Book> searchOpenLibrary(String query, String language, int limit) {
        return openLibraryClient.searchBooks(query, language, limit);
    }

    /**
     * Retrieves detailed work metadata from Open Library.
     *
     * @param workId Open Library work ID
     * @return detailed normalized book
     */
    public Book getOpenLibraryWorkDetail(String workId) {
        return openLibraryClient.getBookDetail(workId);
    }

    /**
     * Searches Project Gutenberg via Gutendex for free ebooks.
     *
     * @param query    search query
     * @param language optional language code
     * @param limit    result limit
     * @return list of matching normalized books with download formats
     */
    public List<Book> searchFreeEbooks(String query, String language, int limit) {
        return gutendexClient.searchBooks(query, language, limit);
    }

    /**
     * Retrieves Project Gutenberg book details and available formats.
     *
     * @param bookId numerical Gutenberg book ID
     * @return detailed normalized book with download formats
     */
    public Book getFreeEbookDetail(int bookId) {
        return gutendexClient.getBookDetail(bookId);
    }
}

