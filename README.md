# BookWise MCP Server

A standalone Model Context Protocol (MCP) server built with **Java 25**, **Spring Boot**, and **Spring AI**.

`bookwise-mcp` exposes book-discovery and metadata tools to AI editors (VS Code, Cursor, Windsurf, Claude Desktop, Claude Code, Continue.dev). The editor's LLM hosts the chat and reading list workflows while this server provides access to Open Library and Project Gutenberg (Gutendex).

---

## Architecture

```text
                    ┌─────────────────────────┐
                    │   AI Editor / Client    │
                    │ (VS Code, Cursor, etc.) │
                    └────────────┬────────────┘
                                 │
                          MCP STDIO / HTTP
                                 │
                                 ▼
                    ┌─────────────────────────┐
                    │     BookWise MCP        │
                    │  (Spring Boot / Java 25)│
                    │                         │
                    │       BookTools         │
                    └────────────┬────────────┘
                                 │
                         BookCatalogService
                                 │
                    ┌────────────┴────────────┐
                    │                         │
                    ▼                         ▼
             ┌──────────────┐          ┌──────────────┐
             │ Open Library │          │   Gutendex   │
             │    Client    │          │    Client    │
             └──────┬───────┘          └──────┬───────┘
                    │                         │
                    ▼                         ▼
             Open Library API          Gutendex API
```

---

## Features

- **MCP Transports**: Full support for both **STDIO** (local child process) and **Streamable HTTP** (`/mcp`).
- **Normalized Domain Model**: Common `Book` record across providers with safe nullability and no fabricated metadata.
- **Resilient HTTP Clients**: Timeout management, structured logging, in-memory caching (`@Cacheable`), and 429/5xx error classification.
- **Safe STDIO Logging**: Profile-aware Logback configuration routing logs to `System.err` in STDIO mode to protect JSON-RPC stdout integrity.
- **Offline Mock Testing**: Full unit and integration test coverage using WireMock (no internet connection required for tests).

---

## MCP Tools

The server provides four tools:

### 1. `search_books`
Searches Open Library for books matching a query.
- **Parameters**:
  - `query` (*string*, required): Search term (title, author, topic).
  - `language` (*string*, optional): ISO 639 language code (e.g., `en`).
  - `limit` (*integer*, optional): Maximum results (1-20, default `10`).

### 2. `get_book_details`
Retrieves detailed metadata for a specific work from Open Library.
- **Parameters**:
  - `workId` (*string*, required): Open Library work ID (e.g., `OL12345W` or `/works/OL12345W`).

### 3. `search_free_ebooks`
Searches Project Gutenberg via Gutendex for free, public domain ebooks.
- **Parameters**:
  - `query` (*string*, required): Search query.
  - `language` (*string*, optional): ISO 639 language code (e.g., `en`).
  - `limit` (*integer*, optional): Maximum results (1-20, default `10`).

### 4. `get_free_ebook_details`
Retrieves metadata and available reading/download format URLs for a Project Gutenberg ebook.
- **Parameters**:
  - `bookId` (*integer*, required): Numerical Project Gutenberg ID (e.g., `1342`).

---

## Prerequisites

- **Java**: JDK 25 or later.
- **Maven**: 3.9+ (or use the provided `./mvnw`).

---

## Build

Compile and run the full test suite:

```bash
./mvnw clean verify
```

Package the executable JAR:

```bash
./mvnw clean package -DskipTests
```

---

## Running the Server

### Option A: STDIO Transport (Default for Local AI Editors)

```bash
java -jar target/bookwise-mcp-0.0.1-SNAPSHOT.jar --spring.profiles.active=stdio
```
*Note: In STDIO mode, web server is disabled (`web-application-type: none`) and logging routes to `stderr`.*

### Option B: Streamable HTTP Transport

```bash
java -jar target/bookwise-mcp-0.0.1-SNAPSHOT.jar --spring.profiles.active=http
```
*Endpoint: `http://127.0.0.1:8080/mcp`*

---

## AI Editor Setup

Example configurations are available in [examples/editor-configs](examples/editor-configs/):

- **VS Code**: [`vscode.mcp.json`](examples/editor-configs/vscode.mcp.json)
- **Cursor**: [`cursor.mcp.json`](examples/editor-configs/cursor.mcp.json)
- **Claude Desktop**: [`claude-desktop.json`](examples/editor-configs/claude-desktop.json)
- **Claude Code**: [`claude-code.json`](examples/editor-configs/claude-code.json)
- **Windsurf**: [`windsurf.mcp.json`](examples/editor-configs/windsurf.mcp.json)
- **Continue.dev**: [`continue.yaml`](examples/editor-configs/continue.yaml)

---

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.
