package com.github.jshah_dev.bookwise_mcp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies that the STDIO profile boots cleanly with web-application-type=none
 * and STDIO MCP transport enabled without starting an HTTP servlet container.
 */
@SpringBootTest
@ActiveProfiles("stdio")
class StdioTransportProfileTest {

    @Test
    @DisplayName("stdio context loads successfully")
    void contextLoads() {
        // Confirms all beans, MCP tools, and providers wire cleanly under stdio profile
    }
}

