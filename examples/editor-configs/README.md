# Editor Configurations for BookWise MCP

This directory provides ready-to-use configuration files for connecting popular AI editors and tools to the BookWise MCP Server.

## Transports Supported

1. **STDIO**
   - The AI editor launches BookWise as a local child process.
   - MCP protocol messages use `stdin`/`stdout`.
   - Logging is safely redirected to `stderr`.
   - Command: `java -jar /PATH/TO/bookwise-mcp-0.0.1-SNAPSHOT.jar --spring.profiles.active=stdio`

2. **HTTP (Streamable HTTP)**
   - Run the server in a separate terminal:
     ```bash
     java -jar target/bookwise-mcp-0.0.1-SNAPSHOT.jar --spring.profiles.active=http
     ```
   - Connect your editor to: `http://127.0.0.1:8080/mcp`

## Configuration Files

- [`vscode.mcp.json`](vscode.mcp.json) — VS Code with Claude or Copilot MCP support.
- [`cursor.mcp.json`](cursor.mcp.json) — Cursor IDE (`~/.cursor/mcp.json`).
- [`claude-desktop.json`](claude-desktop.json) — Claude Desktop (`claude_desktop_config.json`).
- [`claude-code.json`](claude-code.json) — Claude Code CLI.
- [`windsurf.mcp.json`](windsurf.mcp.json) — Codeium Windsurf IDE.
- [`continue.yaml`](continue.yaml) — Continue.dev extension config (`~/.continue/config.yaml`).

> **Note**: Remember to replace `/ABSOLUTE/PATH/TO/` with the real path on your system.

