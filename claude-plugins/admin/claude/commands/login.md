---
description: Log in to a StyleBI deployment. Usage - /stylebi-admin-chat:login <stylebi-url>
---

The user wants to log into a StyleBI deployment. Do this in ONE turn — the user does not need to confirm anything; they just log in via the browser while `login_complete` waits.

1. Extract the StyleBI URL from `$ARGUMENTS`. If empty or not a URL, ask the user for it and stop.
2. Call the MCP tool `login_start` with `{ styleBIUrl: "<the-url>" }` — this is its only parameter; there is no way to route the login anywhere but StyleBI itself. It auto-opens the authorize URL in the user's default browser and returns `{ authorizeUrl, sessionId, deployment, browserOpened }`.
3. Print the `authorizeUrl` to the user in a fenced code block, with one line: if a browser window did not open, open this URL manually and log in. (The browser usually opens automatically; the printed URL is just a fallback for headless/remote setups.)
4. In the SAME turn, call the MCP tool `login_complete` with `{ sessionId, styleBIUrl }` from step 2. It polls (up to 5 min) while the user logs in, then returns once the SSO callback delivers the token. The user does NOT confirm anything — completing the browser login is enough.
5. When `login_complete` returns, print its `summary` to confirm the login.
6. Errors: surface the error message and its suggested remediation; do not retry without user direction. If `login_complete` times out, the `sessionId` is already consumed and cannot be reused — call `login_start` again to get a fresh `sessionId`, then call `login_complete` with that new one.
