# stylebi-composer-chat

Claude Code plugin for editing a StyleBI sheet that is **open in the Composer**: worksheet
structure, viewsheet layout and formatting, chart and table data binding, and the JavaScript
attached to a viewsheet. Tools are exposed under the `mcp__composer-chat__*` namespace.

One pairing code per open sheet serves all four domains.

## It replaces four plugins — uninstall them

`stylebi-worksheet-chat`, `stylebi-script-chat`, `stylebi-viewsheet-chat` and
`stylebi-binding-chat` are merged into this one and their directories are deleted. There is no
compatibility shim and none is planned; there are no external customers.

**Installing this alongside any of the old four is worse than not installing it.** Every tool
then appears twice, and the agent picks between two identical names attached to two different
processes holding two different sessions — which is the exact failure this merge exists to end.

```
/plugin uninstall stylebi-worksheet-chat@stylebi-wiz
/plugin uninstall stylebi-script-chat@stylebi-wiz
/plugin uninstall stylebi-viewsheet-chat@stylebi-wiz
/plugin uninstall stylebi-binding-chat@stylebi-wiz
```

## Why one plugin instead of four

The four were never separated by anything architectural — each simply declared its own workspace
key, so each held its own pairing session. A user editing one viewsheet paired up to four times,
and no tool could see another domain's state.

They also did not share a *runtime*. The Composer's binding editor runs a **cloned** runtime
(`VSBindingService.createRuntimeSheet` → `cloneForBindingEditor()`), which reaches the parent
viewsheet only through `commit()`. Sessions are therefore keyed by **runtime type**, not by plugin
name: `viewsheet`, `binding` and `script` all resolve to the `viewsheet` runtime, and `worksheet`
to its own. That is why one `connect_sheet` on a viewsheet lights up the layout, binding and
script tools together, while a worksheet needs its own code.

## Prerequisite — enable the feature flag on StyleBI (required)

Sheet agent pairing is **off by default** server-side, gated by one `SreeEnv` property:

```
wiz.agent.pairing.enabled=true
```

Without it, every call that reaches the server fails: pairing mint, `join`, and the read/edit
endpoints all check `SheetAgentFeature.isEnabled()` and reject with a 403. This is a StyleBI
deployment setting — nothing in this repo or in `wiz-services` can set it. Either:

- **StyleBI admin console** (any deployment, no restart): Server → Configure → Properties →
  search `wiz.agent.pairing.enabled` → `true`.
- **`sree.properties`** (with file access): add the line and restart StyleBI.

Verify it before debugging anything else. A "pairing disabled" 403 on `connect_sheet` is almost
always this flag, not a plugin bug.

## Install (colleagues, via marketplace)

```
/plugin marketplace add inetsoft-technology/stylebi-wiz
/plugin install stylebi-composer-chat@stylebi-wiz
```

This runs as a local stdio MCP server, unlike the HTTP-based `stylebi-viz-chat`. The runnable
server is a single esbuild bundle with all of `plugin/shared/core` inlined, and it is **committed
to the repo** — a marketplace install works immediately, with no build step, no `npm install`, and
no `node_modules` at runtime.

Restart Claude Code so it loads the server. Tools appear as `mcp__composer-chat__*`.

**Updating:**

```
/plugin marketplace update stylebi-wiz
/plugin update stylebi-composer-chat@stylebi-wiz
```

## 1. Build (local development only)

Only needed if you are changing the source. Re-run after any edit under `plugin/composer/src/` or
`plugin/shared/core/src/`, and commit the resulting `dist/bin.js`.

```bash
cd plugin/shared/core
npm install

cd ../../composer
npm install
npm run build
```

`build` runs `tsc --noEmit`, then bundles `src/bin.ts` — together with `plugin/shared/core/src`
via relative import — into `plugin/composer/dist/bin.js`. Everything else under `dist/` is
gitignored.

`shared/core` needs no *build* — esbuild reads its TypeScript directly. Its `npm install` **is**
required, because this package's `tsconfig.json` sets `rootDir: ".."` and so typechecks
`shared/core`'s sources too. `wizClient.ts` does `import axios from "axios"`, and TypeScript
resolves that by walking up from `plugin/shared/core/`, which never reaches
`plugin/composer/node_modules`. Skip it and the typecheck fails with `TS2307: Cannot find module
'axios'` in a file you did not write.

## 2. Register it as an MCP server (local development only)

The checked-in `.mcp.json` at the repo root already points here. Otherwise register the absolute
path:

```json
{
  "mcpServers": {
    "composer-chat": {
      "type": "stdio",
      "command": "node",
      "args": ["/absolute/path/to/plugin/composer/dist/bin.js"]
    }
  }
}
```

or from the repo root:

```bash
claude mcp add composer-chat -- node plugin/composer/dist/bin.js
```

## 3. Use it

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

## Tests

```bash
cd plugin/shared/core && npx tsc --noEmit && node --experimental-vm-modules node_modules/jest/bin/jest.js
cd ../composer      && npx tsc --noEmit && node --experimental-vm-modules node_modules/jest/bin/jest.js
```

800 tests across 25 suites here, plus 148 across 12 in `shared/core`. **Run both gates.** Jest uses
`ts-jest`, which transpiles without typechecking, so a green Jest run does not mean the branch
compiles — `tsc --noEmit` is a separate and necessary check.

`test/server.test.ts` asserts the merged tool surface exactly (169 tools, all unique). It is a
drift alarm: when you add a tool, record the new number here rather than loosening the assertion to
make a failure go away. It used to be a window and a merge slipped a tool past it — two branches
each added one and each left the figure unchanged, so the recorded count was two behind reality.

## Related

- `shared/skills/composer-chat/SKILL.md` — the full tool reference, workflow, and the
  domain caveats. This is what the agent actually reads.
- `claude/agents/worksheet-editor.md` — subagent for complex multi-step worksheet edits.
- `claude/commands/` — slash commands (`login`, `logout`, `status`, `connect`, `save`).
- `test/scenarios/*.md` — scenario suites per datasource (sakila, olist, suitecrm, inventree,
  csv, orders-db) exercising the tool surface against live data.
- `docs/superpowers/specs/2026-08-15-composer-plugin-consolidation-design.md` — why this is one
  plugin, including the three-runtime finding that decided how sessions are keyed.
- `docs/superpowers/plans/2026-08-16-composer-plugin-consolidation.md` — how the merge was carried
  out, task by task.

## Known gaps and follow-ups

1. **The merged surface is verified live only in part.** The shared session model was verified on a
   real sheet before the merge — one code driving both a viewsheet and its binding editor — and the
   L0 connection/session lane of
   `docs/superpowers/plans/2026-08-17-consolidated-composer-plugin-test-plan.md` has since run
   green against a live Composer (2026-08-19, 9/9 automated). That settled the lane's open
   question: **binding and script have no bootstrap path of their own.** Sessions are keyed by
   runtime type, so one viewsheet code drives layout, binding and script together, while a
   worksheet is its own runtime and its own code; both are held at once with no cross-talk, and
   each tool refuses correctly when *its* runtime is not the one connected. Also confirmed live:
   `detach_sheet` refusing to guess between two live sessions, and a second viewsheet connect
   replacing the first with a warning rather than silently.

   Still outstanding: three GUI-covered requests satisfied with no script tool invoked, and `undo`
   itself with two sheets connected — only its sibling `detach_sheet` has been exercised live, and
   the two share the shape of that refusal but not the code path.

2. **Write coordination is unsolved.** Nothing serialises a plugin write against a concurrent
   human edit in the Composer, so a lost update is possible on every write tool. This predates the
   merge and applies to all four domains equally.

3. **The JSON-RPC loop in `src/bin.ts` is a second copy** of the one in `plugin/admin/src/bin.ts`.
   The merge reduced this from three copies to two. Extracting it into `shared/core` is the right
   fix but touches a module two shipping plugins depend on, so it stays deferred.

4. **Script scoping is designed but not built.** Nothing in this plugin yet distinguishes kinds of
   script or steers the agent away from writing JavaScript for something a GUI-backed tool already
   does — the skill says so in prose, and prose is the whole enforcement. The `kind` taxonomy and
   the per-kind `requiresPaneSession` derivation are a proposal in
   `docs/superpowers/specs/2026-08-14-script-plugin-scope-and-script-kinds.md`; no part of it has
   landed here. Merging the four plugins is what makes it implementable on one tool surface
   instead of four.
