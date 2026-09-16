# stylebi-viz-chat

Claude Code plugin for creating and modifying StyleBI data visualizations.

## Install

**Prerequisites**

- [Claude Code](https://claude.com/claude-code) installed.
- The `STYLEBI_WIZ_URL` environment variable set to your StyleBI deployment URL
  (e.g. `https://stylebi.example.com`). The plugin connects to
  `${STYLEBI_WIZ_URL}/api/wiz/mcp` over HTTP; there is no URL argument to the login
  command.

**Steps** — run these inside Claude Code, from any project / any directory:

```
/plugin marketplace add inetsoft-technology/stylebi
/plugin install stylebi-viz-chat@stylebi-chat
```

Then verify your connection:

```
/stylebi-viz-chat:login
/stylebi-viz-chat:status
```

(`login` reports who you are connected as; it does not perform the login on
Claude Code — see below.)

The first time a viz-chat tool is called, Claude Code opens a browser window for
StyleBI OAuth login. Complete the login there; once done, tools work immediately
with no restart. The token refreshes automatically in the background — you will
not be prompted again until you explicitly log out or the server revokes your
session.

If the tools ever go unavailable or a call reports "not connected" / "requires
re-authorization", run **`/mcp`** and reconnect `plugin:stylebi-viz-chat:viz-chat`.
The lazy browser prompt above does not always fire for an already-disconnected
server, and nothing inside the session can start the OAuth flow for you — the MCP
client owns it, and no `viz-chat` tool is callable while auth is gating the
connection.

**Updating** — when a new version is published, refresh the marketplace catalog
and then update the plugin:

```
/plugin marketplace update stylebi-chat
/plugin update stylebi-viz-chat@stylebi-chat
```

## Commands

- `/stylebi-viz-chat:login` — verify the connection and report who you are logged
  in as. On Claude Code this does NOT log you in — the MCP client owns the OAuth
  flow and no tool can run on a connection that auth is gating, so when you are
  disconnected the command reports that and points you at `/mcp`. No URL argument
  needed — the deployment is fixed by `${STYLEBI_WIZ_URL}`.
- `/stylebi-viz-chat:status` — show login + thread + datasource state
- `/stylebi-viz-chat:logout` — clear credentials for the active deployment; to use `viz-chat` again, reconnect via `/mcp`
- `/stylebi-viz-chat:new [datasource-id]` — start a new conversation thread
- `/stylebi-viz-chat:annotate` — trigger annotation workflow for a datasource
- `/stylebi-viz-chat:review` — run the annotation reviewer subagent

## Subagents

- `chart-type-selector` — recommends a visualization type + starting binding + rationale for the user's data and intent.
- `viz-reviewer` — reviews a proposed worksheet / binding / conditionModel before commit and returns `{ ok, issues[] }`.
- `annotation-reviewer` — adversarially reviews a saved table annotation and returns a verdict (`reject` / `escalate` / `low-touch`).

## Known limitations

- **Subagents are advisory and read-only.** They recommend/review but cannot commit; the main assistant still performs every create/modify/save.
- **Skill guidance is advisory.** Nothing enforces the tool workflow or the conditionModel/binding shapes at runtime; StyleBI is still the validator and rejects a bad worksheet/binding/conditionModel with an error message.
- **Only filtering is supported among chart modifications today.** Highlighting and sort/rank/aggregate field-binding changes are not yet available through chat.
- **Applying a filter replaces the chart's filter** on each call; there is no incremental merge.
- **The companion viewer requires a recorded chart** to open.
