package com.github.jshah_dev.bookwise_mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the BookWise MCP Server.
 * <p>
 * Exposes book discovery and metadata tools to AI editors and clients
 * via the Model Context Protocol (MCP) supporting STDIO and Streamable HTTP transports.
 */
@SpringBootApplication
public class BookwiseMcpApplication {

	public static void main(String[] args) {
		SpringApplication.run(BookwiseMcpApplication.class, args);
	}

}

