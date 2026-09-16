---
description: Log out of StyleBI (revokes the MCP access token). Usage - /stylebi-viz-chat:logout
---

1. If the user provided a URL, call the MCP tool `logout` with `{ deployment: "<the-url>" }`. Otherwise call with no arguments (clears the active deployment).
2. Print the returned `summary` verbatim.
3. Tell the user that to use `viz-chat` again they must re-authenticate by running `/stylebi-viz-chat:login`, which starts the OAuth flow and opens a browser for the StyleBI login.
