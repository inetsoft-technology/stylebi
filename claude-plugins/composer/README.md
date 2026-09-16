# stylebi-composer-chat

Claude Code plugin for editing a StyleBI sheet that is **open in the Composer**: worksheet
structure, viewsheet layout and formatting, chart and table data binding, and the JavaScript
attached to a viewsheet. Tools are exposed under the `mcp__composer-chat__*` namespace.

One pairing code per open sheet serves all four domains.

## Prerequisite — enable the feature flag on StyleBI (required)

Sheet agent pairing is **off by default** server-side, gated by one `SreeEnv` property:

```
wiz.agent.pairing.enabled=true
```

Without it, every call that reaches the server fails: pairing mint, `join`, and the read/edit
endpoints all check `SheetAgentFeature.isEnabled()` and reject with a 403. Either:

- **StyleBI admin console** (any deployment, no restart): Server → Configure → Properties →
  search `wiz.agent.pairing.enabled` → `true`.
- **`sree.properties`** (with file access): add the line and restart StyleBI.

Verify it before debugging anything else. A "pairing disabled" 403 on `connect_sheet` is almost
always this flag, not a plugin bug.

## Install

```
/plugin marketplace add inetsoft-technology/stylebi
/plugin install stylebi-composer-chat@stylebi-chat
```

This runs as a local stdio MCP server. The runnable server is a single bundle with no build step,
no `npm install`, and no `node_modules` required at runtime. Restart Claude Code so it loads the
server. Tools appear as `mcp__composer-chat__*`.

**Updating:**

```
/plugin marketplace update stylebi-chat
/plugin update stylebi-composer-chat@stylebi-chat
```

## Use it

1. Confirm the feature flag above is set on the target deployment.
2. `login_start` with a StyleBI URL — it opens an authorize page; click Authorize.
3. `login_complete` with the `sessionId` from step 2. Call it immediately; do not wait for the
   user between the two.
4. Open a sheet in the Composer, click the pairing button in the toolbar, and pass the displayed
   code straight to `connect_sheet` — codes expire in seconds.
5. Work. On a viewsheet the layout, binding and script tools all operate on that one session; a
   worksheet is a separate code and a separate session, and both can be held at once.

No environment variables, no Redis, no database access. The plugin needs Node and a reachable
StyleBI with the flag on.

**Two failures whose obvious reading is wrong**, both worth recognising before you retry:

- **403 "Identity mismatch"** is not a permissions problem. The plugin is logged in as a different
  user than the browser holding the sheet. Log in as the same user.
- **A rejected pairing code** is not necessarily expiry. On a second consecutive failure with a
  fresh code, check `status` and the deployment URL — a rebuild can change the exposed port, and
  the plugin will still be authenticated against the old one.

## Commands

- `/stylebi-composer-chat:login`
- `/stylebi-composer-chat:logout`
- `/stylebi-composer-chat:status`
- `/stylebi-composer-chat:connect`
- `/stylebi-composer-chat:save`

## Subagents

- `worksheet-editor` — subagent for complex multi-step worksheet edits.

## Known limitations

- **Write coordination is unsolved.** Nothing serialises a plugin write against a concurrent
  human edit in the Composer, so a lost update is possible on every write tool.
- **Script scoping is advisory only.** Nothing yet steers the agent away from writing JavaScript
  for something a GUI-backed tool already does.
