---
description: Log out from the current StyleBI deployment.
---

1. If `status` shows `agentConnected: true`, call `detach_sheet` first to clean up the
   server-side session. Print the detach confirmation.
2. Call the MCP tool `logout` with `{ deployment: "<the-url>" }` if the user named a specific
   deployment, or with no arguments to clear the currently active one.
3. Print the returned `summary` verbatim.
4. Tell the user they will need to run `/stylebi-composer-chat:login <stylebi-url>` again to use
   any Composer tool.
