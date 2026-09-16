---
description: Show current StyleBI connection status. Usage - /stylebi-viz-chat:status
---

Report the current StyleBI connection:

1. **If the `viz-chat` MCP tools are available**, call `whoami` and report the user, organization, and StyleBI URL. If a thread is active, also call `get_thread_state` and show the current thread + datasource.
2. **If the `viz-chat` tools are unavailable, or a call returns an auth error (401 / session expired)**, tell the user they are not connected and to run `/stylebi-viz-chat:login` to connect — it starts the OAuth flow, opens the browser, and waits until authenticated.
