---
description: Verify the StyleBI connection and report who you are logged in as. Usage - /stylebi-viz-chat:login
---

Verify the StyleBI connection for the `viz-chat` MCP server and report the identity.

**What this command can and cannot do.** `viz-chat` is an HTTP MCP server whose connection is gated
by OAuth, and the MCP CLIENT owns that flow — not this command and not the server. There is no
`authenticate` tool to call: a tool cannot run on a connection that authentication is gating, so at
the exact moment the user needs to log in, every `viz-chat` tool is unavailable by definition. Do
NOT try to start the OAuth flow, open an authorize URL, or poll waiting for a token.

(The pre-HTTP plugin shipped a LOCAL stdio server exposing `login_start`/`login_complete`, which
worked precisely because a stdio server needs no auth to be reachable. The HTTP+OAuth migration
deleted that server, so the old choreography is not restorable on this architecture — a later
revision of this file re-added it anyway, against a tool that does not exist, which is what this
version removes.)

1. **Connected** — call `whoami` and report who the user is logged in as (user, organization,
   deployment) from its result. This is the case the command exists for. Done.

2. **Not connected** (the `viz-chat` tools are unavailable, or a call returns 401 / "not connected" /
   "requires re-authorization") — you cannot fix this yourself. Tell the user to run **`/mcp`** and
   reconnect `plugin:stylebi-viz-chat:viz-chat`, which runs the OAuth flow and opens the browser.

   Do NOT loop, poll, or blind-retry `whoami` waiting for it to come back — the user has to complete
   a browser login, and retrying cannot make that happen sooner.

   Claude Code is documented to open the login browser lazily on the first tool call, so that may
   also resolve it. Treat that as a possibility, not a promise: in practice a fully disconnected
   server has needed an explicit `/mcp`.

   Rule out the server being DOWN before pointing at the login, because both present as unavailable
   tools while the remedy is completely different — if `wiz-services` is not running, no amount of
   re-authenticating will help. Check with
   `curl -sk -o /dev/null -w '%{http_code}' https://localhost:3003/api/wiz/v1/health`: `401` means up
   and auth-gated (so it IS a login problem), `000` means down. Note it serves HTTPS — plain `http://`
   returns `000` and looks dead when it is fine.

3. **After the user reports being reconnected** — call `whoami` once to confirm, and report the
   identity. If it still fails, say so plainly rather than retrying.

Do NOT ask for a StyleBI URL — the deployment is fixed by the server's `${STYLEBI_WIZ_URL}`
configuration.
