package com.github.jshah_dev.bookwise_mcp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies that the HTTP profile boots cleanly as a web servlet application
 * with Streamable HTTP MCP transport configured.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("http")
class HttpTransportProfileTest {

    @Test
    @DisplayName("http context loads and servlet container starts")
    void contextLoads() {
        // Confirms Streamable HTTP transport and WebMvc servlet stack boot cleanly
    }
}

