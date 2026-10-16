# BookWise MCP Server

## 1. Project Objective

Build `bookwise-mcp`, a standalone MCP server using **Java 25 + Spring Boot + Spring AI**.

The server provides book-discovery capabilities to AI editors through MCP. The editor's existing LLM can use the tools to search books, find free ebooks, inspect metadata, and build reading lists.

The server supports two transport modes:

1. **STDIO** — local process launched directly by an AI editor.
2. **HTTP** — MCP Streamable HTTP server.

Both modes use the same MCP tools and business logic.

---

## 2. Project Scope

### Included

- Java 25
- Spring Boot
- Spring AI MCP Server
- Maven
- MCP STDIO transport
- MCP Streamable HTTP transport
- Open Library integration
- Gutendex integration
- Four MCP tools
- Normalized book model
- External API error handling
- Timeouts
- Basic caching
- Rate-limit awareness
- Unit and integration tests
- MCP client/editor configuration examples
- Complete README

### Not included in v1

- LLM integration inside the server
- Chat UI
- Web frontend
- Database
- User accounts
- Reading-list persistence
- Authentication
- Kubernetes deployment
- Separate MCP client application
- Multiple MCP servers

The **AI editor is the MCP client and LLM host**.

The MCP server only provides reliable book-discovery capabilities.

---

# 3. High-Level Architecture

```text
                    ┌─────────────────────────┐
                    │   AI Editor / Client    │
                    │                         │
                    │ VS Code / Cursor / etc. │
                    └────────────┬────────────┘
                                 │
                         MCP STDIO / HTTP
                                 │
                                 ▼
                    ┌─────────────────────────┐
                    │     BookWise MCP        │
                    │      Spring Boot        │
                    │                         │
                    │      MCP Tools          │
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

The transport layer must remain independent from the book provider and MCP tool implementation.

---

# 4. External APIs

## 4.1 Open Library

Official API:

```text
https://openlibrary.org/dev/docs/api/search
```

Primary endpoint:

```text
GET https://openlibrary.org/search.json
```

Use it for:

- Book search
- Author search
- Topic/subject search
- Publication information
- ISBN information
- Work identifiers
- Cover information

For detailed work information:

```text
GET https://openlibrary.org/works/{workId}.json
```

### Requirements

- Add a descriptive `User-Agent`.
- Respect Open Library rate limits.
- Cache repeat searches where appropriate.
- Do not assume every book has all metadata.
- Never fabricate missing metadata.

---

## 4.2 Gutendex

Official API:

```text
https://gutendex.com/
```

Primary endpoint:

```text
GET https://gutendex.com/books/
```

Use it for:

- Project Gutenberg book search
- Free ebook discovery
- Author information
- Language information
- Subjects
- Ebook formats
- Download/read URLs

Use only URLs returned by Gutendex.

Do not construct download URLs manually.

---

# 5. MCP Tools

The first version should expose four tools.

## 5.1 `search_books`

Search Open Library.

### Input

```json
{
  "query": "distributed systems",
  "language": "en",
  "limit": 10
}
```

### Parameters

| Parameter | Required | Description |
|---|---|---|
| `query` | Yes | Search query |
| `language` | No | ISO language code |
| `limit` | No | Maximum results, default 10, maximum 20 |

### Result

Return normalized book objects.

Example:

```json
{
  "books": [
    {
      "provider": "openlibrary",
      "providerId": "OL123W",
      "title": "Example Book",
      "authors": [
        "Example Author"
      ],
      "publicationYear": 2022,
      "subjects": [
        "Distributed systems"
      ],
      "languages": [
        "en"
      ],
      "sourceUrl": "https://openlibrary.org/works/OL123W",
      "coverUrl": null
    }
  ]
}
```

The example values above are illustrative.

---

# 6. `get_book_details`

Retrieve detailed Open Library work information.

### Input

```json
{
  "workId": "OL123W"
}
```

### Requirements

Return:

- Title
- Authors
- Subjects
- Description
- Publication information when available
- Identifiers when available
- Source URL
- Cover information

Do not invent missing information.

---

# 7. `search_free_ebooks`

Search Gutendex.

### Input

```json
{
  "query": "computer science",
  "language": "en",
  "limit": 10
}
```

### Result

Return:

- Gutenberg ID
- Title
- Authors
- Languages
- Subjects
- Source URL
- Available ebook formats
- Reading/download URLs

Example:

```json
{
  "books": [
    {
      "provider": "gutendex",
      "providerId": "12345",
      "title": "Example Book",
      "authors": [
        "Example Author"
      ],
      "languages": [
        "en"
      ],
      "subjects": [
        "Computer science"
      ],
      "sourceUrl": "https://www.gutenberg.org/ebooks/12345",
      "formats": {
        "text/html": "https://example.com/book.html"
      }
    }
  ]
}
```

The example values are illustrative.

---

# 8. `get_free_ebook_details`

Retrieve a specific Gutendex book.

### Input

```json
{
  "bookId": 12345
}
```

Return the available Gutendex metadata and format URLs.

---

# 9. Normalized Domain Model

Create a common `Book` / `BookCandidate` model.

Suggested fields:

```text
provider
providerId
title
authors
publicationYear
subjects
languages
description
isbn
sourceUrl
coverUrl
formats
```

Not every provider will populate every field.

Use nullable fields rather than inventing values.

Provider-specific response DTOs should remain inside their respective provider packages.

---

# 10. Project Structure

```text
bookwise-mcp/
│
├── pom.xml
├── mvnw
├── mvnw.cmd
├── README.md
├── LICENSE
├── .gitignore
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/bookwise/
│   │   │       │
│   │   │       ├── BookWiseMcpApplication.java
│   │   │       │
│   │   │       ├── tools/
│   │   │       │   └── BookTools.java
│   │   │       │
│   │   │       ├── service/
│   │   │       │   └── BookCatalogService.java
│   │   │       │
│   │   │       ├── providers/
│   │   │       │   ├── openlibrary/
│   │   │       │   │   ├── OpenLibraryClient.java
│   │   │       │   │   ├── OpenLibraryModels.java
│   │   │       │   │   └── OpenLibraryMapper.java
│   │   │       │   │
│   │   │       │   └── gutendex/
│   │   │       │       ├── GutendexClient.java
│   │   │       │       ├── GutendexModels.java
│   │   │       │       └── GutendexMapper.java
│   │   │       │
│   │   │       ├── model/
│   │   │       │   └── Book.java
│   │   │       │
│   │   │       ├── config/
│   │   │       │   └── ...
│   │   │       │
│   │   │       └── exception/
│   │   │           └── ...
│   │   │
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-stdio.yml
│   │       ├── application-http.yml
│   │       └── logback-spring.xml
│   │
│   └── test/
│       └── java/
│           └── com/example/bookwise/
│
└── examples/
    └── editor-configs/
        ├── vscode.mcp.json
        ├── cursor.mcp.json
        ├── claude-desktop.json
        ├── claude-code.json
        ├── windsurf.mcp.json
        ├── continue.yaml
        └── README.md
```

---

# 11. Transport Configuration

The application must support:

```text
stdio
http
```

through Spring profiles.

---

## 11.1 Common Configuration

`application.yml`

```yaml
spring:
  application:
    name: bookwise-mcp

  ai:
    mcp:
      server:
        name: bookwise-mcp
        version: 0.1.0
        type: SYNC
        annotation-scanner:
          enabled: true
```

Use the correct properties for the selected Spring AI version.

---

# 12. STDIO Mode

File:

```text
application-stdio.yml
```

Configure:

- STDIO MCP transport.
- No embedded HTTP server.
- MCP communication through stdin/stdout.
- Application logs through stderr.

Example:

```yaml
spring:
  main:
    web-application-type: none

  ai:
    mcp:
      server:
        stdio: true
```

Launch:

```bash
java -jar bookwise-mcp.jar \
  --spring.profiles.active=stdio
```

### Important

STDOUT is reserved for MCP protocol messages.

Application logs must never be written to stdout.

---

# 13. HTTP Mode

File:

```text
application-http.yml
```

Use MCP Streamable HTTP.

Example:

```yaml
server:
  address: 127.0.0.1
  port: 8080

spring:
  main:
    web-application-type: servlet

  ai:
    mcp:
      server:
        stdio: false
        protocol: STREAMABLE
        streamable-http:
          mcp-endpoint: /mcp
```

Launch:

```bash
java -jar bookwise-mcp.jar \
  --spring.profiles.active=http
```

Expected endpoint:

```text
http://127.0.0.1:8080/mcp
```

The exact Spring AI property names must be verified against the version selected for the project.

---

# 14. Transport Independence

The application architecture should look like:

```text
                 ┌──────────────────────┐
                 │      MCP Tools       │
                 │                      │
                 │    BookTools.java    │
                 └──────────┬───────────┘
                            │
                            ▼
                 ┌──────────────────────┐
                 │ BookCatalogService   │
                 └──────────┬───────────┘
                            │
                  ┌─────────┴─────────┐
                  ▼                   ▼
           OpenLibraryClient     GutendexClient


MCP Transport
     │
     ├── STDIO
     │
     └── Streamable HTTP
```

Business logic must not contain transport-specific code.

---

# 15. External API Resilience

Implement:

### Timeouts

Configure:

- Connection timeout
- Request timeout

Do not allow an external API call to hang indefinitely.

### Error handling

Handle:

```text
400
404
429
500
502
503
504
connection timeout
connection failure
malformed response
```

Differentiate:

```text
No results
```

from:

```text
Provider unavailable
```

Do not return an empty search result when the provider actually failed.

### Retry

Use limited retries only for appropriate transient failures.

Do not retry:

```text
400
404
invalid parameters
```

### Caching

Add lightweight caching for repeat searches and book details.

Avoid excessive calls to public APIs.

---

# 16. MCP Tool Validation

Validate:

```text
query != null
query != blank
limit >= 1
limit <= 20
workId is valid
bookId is valid
language is valid when supplied
```

Return useful MCP errors when validation fails.

Do not allow arbitrary URLs to be supplied to tools and fetched by the server.

---

# 17. Testing Strategy

## Unit Tests

Test:

- Open Library response mapping
- Gutendex response mapping
- Missing fields
- Empty results
- Invalid input
- Error mapping
- Service logic

## HTTP Client Tests

Mock external APIs.

Test:

```text
successful response
empty response
404
429
500
timeout
malformed JSON
```

Routine tests must not depend on the internet.

## MCP Tests

Verify:

- MCP server startup
- Tool discovery
- Tool schemas
- Tool invocation
- Invalid tool parameters
- Provider failures

## Transport Tests

Verify STDIO mode separately.

Verify HTTP mode separately.

Ensure only the selected transport is active.

---

# 18. Editor Configuration Examples

Create:

```text
examples/editor-configs/
```

Include tested configurations for:

- VS Code
- Cursor
- Claude Desktop
- Claude Code
- Windsurf
- Continue

Each configuration should show both options where supported:

```text
STDIO
HTTP
```

---

## Generic STDIO Configuration

```json
{
  "mcpServers": {
    "bookwise": {
      "command": "java",
      "args": [
        "-jar",
        "/ABSOLUTE/PATH/bookwise-mcp.jar",
        "--spring.profiles.active=stdio"
      ]
    }
  }
}
```

---

## Generic HTTP Configuration

Use the editor's supported MCP HTTP configuration format and point it to:

```text
http://127.0.0.1:8080/mcp
```

Do not assume that every editor uses the same HTTP configuration schema.

Document each editor's configuration separately.

---

# 19. Example User Experience

After installing the MCP server, a user should be able to ask their AI editor:

```text
Create a reading list to learn distributed systems.

My current level is beginner.
I know Java and Spring Boot.

Prefer books that are freely available online when possible.

Organize the books from beginner to advanced and explain
why each book is useful.
```

The AI editor should then discover and invoke tools such as:

```text
search_books("distributed systems")
search_books("computer networks")
search_books("operating systems")
search_free_ebooks("distributed systems")
get_book_details(...)
get_free_ebook_details(...)
```

The editor's LLM creates the final reading plan.

The MCP server itself does not generate the final plan.

---

# 20. Example MCP Interaction

```text
User
 │
 │ "Find books to learn distributed systems"
 ▼
AI Editor
 │
 │ MCP tools/list
 ▼
BookWise MCP
 │
 │ search_books()
 ▼
Open Library
 │
 │ results
 ▼
BookWise MCP
 │
 │ normalized results
 ▼
AI Editor / LLM
 │
 │ identifies useful candidates
 │
 │ get_book_details()
 ▼
BookWise MCP
 │
 ▼
Open Library
 │
 ▼
AI Editor / LLM
 │
 │ final reading plan
 ▼
User
```

---

# 21. Optional Phase 2 Features

Do not implement these in the initial release.

Potential future MCP capabilities:

### MCP Resources

Expose resources such as:

```text
book://openlibrary/{workId}
ebook://gutendex/{bookId}
```

### MCP Prompts

Expose a reusable prompt such as:

```text
create_reading_plan
```

Parameters:

```text
topic
experienceLevel
duration
language
preferFreeBooks
```

### Additional providers

Potential future providers:

- Google Books
- Crossref
- OpenAlex
- Internet Archive

### Additional tools

Potential future tools:

```text
search_authors
search_by_subject
find_books_by_author
compare_books
find_free_alternatives
```

Keep these out of v1 unless the core implementation is already complete.

---

# 22. Observability

Add lightweight operational logging.

For each provider operation log:

```text
provider
operation
duration
status
resultCount
```

Example:

```text
provider=openlibrary
operation=search
duration=245ms
status=success
resultCount=10
```

Do not log:

- API credentials
- unnecessary user information
- complete external API responses

Micrometer metrics can be added later if useful.

---

# 23. Security

## STDIO

The local editor launches the MCP server directly.

No authentication is required.

## HTTP

Default binding:

```text
127.0.0.1
```

Do not default to:

```text
0.0.0.0
```

If remote HTTP access is eventually supported, add:

- Authentication
- Authorization
- TLS
- Request limits
- CORS policy where applicable
- Rate limiting

Do not present the development HTTP server as production-secure.

---

# 24. README Requirements

The README must contain:

## Overview

Explain:

- What BookWise MCP is.
- Why MCP is used.
- What external APIs are used.

## Architecture

Include an architecture diagram.

## Requirements

```text
Java 25
Maven
```

## Build

```bash
./mvnw clean verify
```

## Run STDIO

```bash
java -jar bookwise-mcp.jar \
  --spring.profiles.active=stdio
```

## Run HTTP

```bash
java -jar bookwise-mcp.jar \
  --spring.profiles.active=http
```

## MCP Tools

Document all four tools with:

- Description
- Parameters
- Example input
- Example output

## Editor Setup

Document:

- VS Code
- Cursor
- Claude Desktop
- Claude Code
- Windsurf
- Continue

## Troubleshooting

Include:

- MCP server not starting
- Tool not appearing
- STDIO protocol errors
- HTTP connection problems
- External API failures
- Rate limiting

---

# 25. Implementation Phases

## Phase 1 — Project Setup

- [x] Create Spring Boot project.
- [x] Configure Java 25.
- [x] Configure Maven.
- [x] Add Spring AI MCP dependencies.
- [x] Verify application startup.
- [x] Add basic tests.

### Deliverable

A clean Spring Boot project with MCP dependencies.

---

## Phase 2 — Open Library Integration

- [x] Implement Open Library client.
- [x] Add response DTOs.
- [x] Add normalized `Book` model.
- [x] Implement mapping.
- [x] Add timeout handling.
- [x] Add unit tests.
- [x] Add mock HTTP tests.

### Deliverable

Working Open Library integration.

---

## Phase 3 — Gutendex Integration

- [x] Implement Gutendex client.
- [x] Add response DTOs.
- [x] Implement mapping.
- [x] Handle ebook formats.
- [x] Add error handling.
- [x] Add tests.

### Deliverable

Working Gutendex integration.

---

## Phase 4 — MCP Tools

Implement:

- [x] `search_books`
- [x] `get_book_details`
- [x] `search_free_ebooks`
- [x] `get_free_ebook_details`

Then:

- [x] Add MCP schemas.
- [x] Add validation.
- [x] Add tool descriptions.
- [x] Verify tool discovery.
- [x] Verify tool invocation.

### Deliverable

Fully functional BookWise MCP server.

---

## Phase 5 — STDIO Transport

- [x] Configure STDIO profile.
- [x] Disable HTTP server.
- [x] Configure logging to stderr.
- [x] Test server as local MCP process.
- [x] Test with at least one real MCP client.

### Deliverable

Local editor integration.

---

## Phase 6 — HTTP Transport

- [x] Configure Streamable HTTP.
- [x] Configure `/mcp`.
- [x] Configure host and port.
- [x] Default to localhost.
- [x] Test MCP HTTP communication.
- [x] Verify STDIO and HTTP don't run simultaneously unintentionally.

### Deliverable

HTTP-accessible MCP server.

---

## Phase 7 — Resilience

- [x] Add caching.
- [x] Add rate-limit handling.
- [x] Add timeout handling.
- [x] Add limited retries.
- [x] Add structured error handling.
- [x] Add useful logging.

### Deliverable

Robust public-API integration.

---

## Phase 8 — Editor Configuration

- [x] VS Code configuration.
- [x] Cursor configuration.
- [x] Claude Desktop configuration.
- [x] Claude Code configuration.
- [x] Windsurf configuration.
- [x] Continue configuration.
- [x] Document STDIO.
- [x] Document HTTP where supported.

### Deliverable

Easy installation for common AI editors.

---

## Phase 9 — Documentation

- [x] Complete README.
- [x] Architecture diagram.
- [x] Tool documentation.
- [x] Setup instructions.
- [x] Editor configuration.
- [x] Example prompts.
- [x] Troubleshooting.
- [x] API references.
- [x] Security notes.
- [x] Known limitations.

### Deliverable

Portfolio-ready GitHub repository.

---

# 26. Definition of Done

The project is complete when:

- [x] `./mvnw clean verify` passes.
- [x] Open Library integration works.
- [x] Gutendex integration works.
- [x] All four MCP tools are discoverable.
- [x] All four MCP tools return normalized results.
- [x] STDIO mode works with a real MCP editor.
- [x] HTTP mode works with a compatible MCP client.
- [x] The same tool implementation works in both modes.
- [x] External API failures are handled gracefully.
- [x] Routine tests do not require internet access.
- [x] Editor configuration examples are included.
- [x] README allows a new user to run the server without reading the source code.
- [x] No secrets or machine-specific paths are committed.
- [x] No unnecessary LLM, database or UI implementation exists.

---

# 27. Important Implementation Principle

Keep the project deliberately small.

The purpose of this repository is to demonstrate:

```text
Spring Boot
     +
Spring AI
     +
MCP Server
     +
MCP Tools
     +
External APIs
     +
Multiple MCP transports
```

The project should **not** become a complete reading-management application.

The interesting part is that an AI editor can discover the BookWise MCP server and use its tools to perform real book research.

The final experience should be:

```text
User
  │
  ▼
AI Editor
  │
  │ MCP
  ▼
BookWise MCP
  │
  ├── Open Library
  │
  └── Gutendex
  │
  ▼
AI Editor
  │
  ▼
Useful reading list
```

This keeps the implementation small while giving the GitHub project a clear and demonstrable MCP use case.