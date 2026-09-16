---
description: Show current StyleBI connection status. Usage - /stylebi-viz-chat:status
---

Report the current StyleBI connection:

1. **If the `viz-chat` MCP tools are available**, call `whoami` and report the user, organization, and StyleBI URL. If a thread is active, also call `get_thread_state` and show the current thread + datasource.
2. **If the `viz-chat` tools are unavailable, or a call returns an auth error (401 / session expired)**, tell the user they are not connected. Running `/stylebi-viz-chat:login` will verify the connection and report the identity, but it does not itself start OAuth or wait for a token — if the connection is down, it will point the user at `/mcp` to reconnect `viz-chat`, which is what actually runs the OAuth flow and opens the browser.
