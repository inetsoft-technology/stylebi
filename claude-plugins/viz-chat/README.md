# stylebi-viz-chat

Claude Code plugin for creating and modifying StyleBI data visualizations.

## Install (colleagues)

You **do not** need to clone this repo. Claude Code installs the plugin from a
private marketplace hosted in the repo itself.

**Prerequisites**

- [Claude Code](https://claude.com/claude-code) installed.
- Read access to `inetsoft-technology/stylebi-wiz` on GitHub, with working
  `gh` or SSH auth (Claude Code clones the repo into its own plugin cache using
  your credentials).
- The `STYLEBI_WIZ_URL` environment variable set to your StyleBI deployment URL
  (e.g. `https://stylebi.example.com`). The plugin connects to
  `${STYLEBI_WIZ_URL}/api/wiz/mcp` over HTTP; there is no URL argument to the login
  command.

**Steps** — run these inside Claude Code, from any project / any directory:

```
/plugin marketplace add inetsoft-technology/stylebi-wiz
/plugin install stylebi-viz-chat@stylebi-wiz
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
/plugin marketplace update stylebi-wiz
/plugin update stylebi-viz-chat@stylebi-wiz
```

> If you *are* working inside a clone of this repo, you can skip the commands
> above: the repo's `.claude/settings.json` registers the marketplace, so
> Claude Code prompts you to install on first trust. For local plugin
> development, load the working tree directly with `claude --plugin-dir plugin/viz-chat/`.

## Build

A single build script is available under `plugin/viz-chat/build/scripts/`:

### `package-plugin.sh` — HTTP transport (Claude + Codex, current)

```bash
bash plugin/viz-chat/build/scripts/package-plugin.sh
```

This is the single packager for the HTTP-transport plugin. It produces two
output directories under `plugin/viz-chat/dist/`:

- `plugin-dist-claude/` — Claude Code format. `plugin.json` configures an HTTP
  MCP server named `viz-chat` at `${STYLEBI_WIZ_URL}/api/wiz/mcp`; no local binary
  is needed or included.
- `codex-plugin/` — Codex format. `.mcp.json` inside `stylebi-viz-chat/`
  configures the same HTTP MCP server. No local binary is needed or included.

Both packages exclude source, tests, and build config. The build is a fast
copy/flatten — no dependency install step is required.

> `package-plugin-http.sh` was the predecessor script used during migration; it
> has been consolidated into `package-plugin.sh`. There is no separate http
> variant anymore.

### Build output structure

**Claude Code output** (`plugin/viz-chat/dist/plugin-dist-claude/`):

```
plugin-dist-claude/
├── .claude-plugin/
│   └── plugin.json             # HTTP MCP server: viz-chat → ${STYLEBI_WIZ_URL}/api/wiz/mcp
├── agents/                     # Subagent definitions (complete source files, copied as-is)
│   ├── annotation-reviewer.md  # Adversarial annotation reviewer (Claude-only)
│   ├── chart-type-selector.md  # Chart type + field binding recommender
│   └── viz-reviewer.md         # Worksheet / binding / filter pre-commit reviewer
├── commands/                   # Slash commands (/stylebi-viz-chat:<name>)
│   ├── annotate.md
│   ├── login.md
│   ├── logout.md
│   ├── new.md
│   ├── review.md
│   └── status.md
├── hooks/
│   ├── hooks.json
│   └── session-start.sh        # Only hook (user-prompt-submit.sh was removed)
├── skills/
│   ├── annotation/
│   │   ├── SKILL.md
│   │   └── instructions/
│   │       ├── annotation-guidance.md
│   │       └── key-detection.md
│   └── viz-chat/
│       ├── SKILL.md
│       └── instructions/
│           ├── condition-model.md
│           ├── viewsheet-binding.md
│           └── worksheet-construction.md
└── README.md
```

**Codex output** (`plugin/viz-chat/dist/codex-plugin/`):

```
codex-plugin/
├── .agents/
│   └── plugins/
│       └── marketplace.json    # Self-referential plugin registry (path: "./stylebi-viz-chat")
├── .codex/
│   └── agents/                 # Codex subagent definitions (TOML)
│       ├── chart-type-selector.toml
│       └── viz-reviewer.toml
└── stylebi-viz-chat/
    ├── .codex-plugin/
    │   └── plugin.json
    ├── .mcp.json               # MCP server config: viz-chat → ${STYLEBI_WIZ_URL}/api/wiz/mcp (HTTP)
    ├── hooks/
    │   ├── hooks.json
    │   └── session-start.sh
    ├── skills/
    └── README.md
```

Neither package contains an `mcp-server/` directory or any binary bundle.

## Local development (Claude, no build)

You can run the plugin directly from source — no build step required for everyday Claude development.

**Launch Claude against source:**

```bash
claude --plugin-dir /absolute/path/to/plugin/viz-chat
```

The Claude plugin connects to StyleBI over HTTP MCP (`${STYLEBI_WIZ_URL}/api/wiz/mcp`).
Set `STYLEBI_WIZ_URL` in your environment before launching Claude. No local MCP
server process is started.

**What takes effect on the next Claude restart (no build required):**

- Commands: `claude/commands/`
- Agents: `claude/agents/` — each file is a complete frontmatter + body definition, ready to load as-is
- Skills: `shared/skills/`
- Hooks: `shared/hooks/`

`plugin.json` (in `plugin/viz-chat/.claude-plugin/`) declares component paths (`commands`, `agents`, `skills`, `hooks`) so Claude loads them directly from the source subdirectories — no flattening or merging is performed during development.

**Note:** `plugin/viz-chat/build/scripts/package-plugin.sh` is only needed to produce the
distributable packages (`plugin/viz-chat/dist/plugin-dist-claude/` and
`plugin/viz-chat/dist/codex-plugin/`) — it is not required for local Claude development.

## Use — Claude Code (distributable package)

To use the pre-built, source-free distributable, point Claude Code at the built plugin directory:

```bash
STYLEBI_WIZ_URL=https://stylebi.example.com claude --plugin-dir /abs/path/to/plugin/viz-chat/dist/plugin-dist-claude
```

## Use — Codex

Both Claude Code and Codex connect to StyleBI over HTTP MCP — the same
`${STYLEBI_WIZ_URL}/api/wiz/mcp` endpoint. The Codex package (`plugin/viz-chat/codex/.mcp.json`)
configures `type: http` transport, identical to the Claude package.

Register the plugin from the built output directory:

```bash
cd plugin/viz-chat/dist/codex-plugin
codex plugin marketplace add .
```

Then start Codex from the same directory so it picks up the `.codex/agents/` subagents:

```bash
codex
```

Login in Codex is client-specific: Codex cannot initiate the MCP OAuth flow from
inside a tool call, so authenticate with the Codex CLI — run `codex mcp login
viz-chat` (it performs the StyleBI OAuth flow and opens a browser). The
`/stylebi-viz-chat:login` skill drives this. If the `viz-chat` tools are
unavailable you are not authenticated — log in, then retry.

## Commands (Claude Code only)

- `/stylebi-viz-chat:login` — verify the connection and report who you are logged
  in as. Client-specific: on Codex it runs `codex mcp login viz-chat`, which does
  perform the login. On Claude Code it does NOT log you in — the MCP client owns
  the OAuth flow and no tool can run on a connection that auth is gating, so when
  you are disconnected the command reports that and points you at `/mcp`. No URL
  argument needed — the deployment is fixed by `${STYLEBI_WIZ_URL}`.
- `/stylebi-viz-chat:status` — show login + thread + datasource state
- `/stylebi-viz-chat:logout` — clear credentials for the active deployment; to use `viz-chat` again, reconnect via `/mcp`
- `/stylebi-viz-chat:new [datasource-id]` — start a new conversation thread
- `/stylebi-viz-chat:annotate` — trigger annotation workflow for a datasource
- `/stylebi-viz-chat:review` — run the annotation reviewer subagent

> Codex has no slash commands. Drive the plugin with natural language instead,
> e.g. "Show my StyleBI login status", "Create a chart from my data". (See
> `defaultPrompt` in `.codex-plugin/plugin.json`.)

## Subagents

- `chart-type-selector` — recommends a visualization type + starting binding + rationale for the user's data and intent.
- `viz-reviewer` — reviews a proposed worksheet / binding / conditionModel before commit and returns `{ ok, issues[] }`.
- `annotation-reviewer` — adversarially reviews a saved table annotation and returns a verdict (`reject` / `escalate` / `low-touch`).

### Editing agents

Agent files live in `claude/agents/<name>.md` and are complete frontmatter + body files — no merging is needed at build time. Edit them directly; changes take effect on the next Claude restart (no build required). For the Codex platform, `codex/agents/<name>.toml` files are generated by `package-plugin.sh` when producing the distributable package.

See `docs/superpowers/specs/2026-05-29-claude-code-plugin-design.md` for the design.

## Verified

- 2026-05-29: mocked only, awaiting StyleBI SSO change.
  - No real StyleBI deployment was available; the StyleBI SSO endpoints (`/sso/authorize`,
    `/sso/token`) have not yet been updated to support the loopback OAuth redirect URI required
    by the plugin (`http://127.0.0.1:<port>/cb`). The live SSO round-trip is deferred until
    that change ships.
  - Contract verified via automated test suite:
    - `wiz-services` v1 routes + services: **8 tests** across 3 suites (auth routes, index routes, CLI token service)
    - `plugin/mcp-server`: **29 tests** across 8 suites
  - MCP server binary (`plugin/mcp-server/dist/server.js`) builds cleanly and exits with code 0
    when given EOF on stdin (correct behaviour for a stdio MCP server).
  - Slash commands proven by mocked tests: `/stylebi-viz-chat:login`, `/stylebi-viz-chat:status`,
    `/stylebi-viz-chat:logout`.
- 2026-05-29: Plan 2 implemented — login_start/login_complete split, thread state CRUD (12 MCP tools total), datasource list. 62 plugin tests + 44 wiz-services v1 tests passing. Mocked only; live SSO round-trip still pending StyleBI SSO update.
- 2026-05-29: Plan 3 implemented — search_schema (wraps hierarchicalRetrieval) + get_table_details. 14 MCP tools total. 69 plugin tests + 59 wiz-services v1 tests passing. Mocked only.
- 2026-05-29: Plan 4 implemented — validate_worksheet + create_viewsheet + close_viewsheet + open_companion_viewer. 18 MCP tools total. 84 plugin tests + 78 wiz-services v1 tests passing. Mocked only.
- 2026-05-29: Plan 5 implemented — validate_binding + change_chart_type + lookup_column_values + save_viewsheet + delete_saved_visualization. 23 MCP tools total. 104 plugin tests + 105 wiz-services v1 tests passing. Mocked only.
- 2026-05-30: Plan 6 implemented — companion viewer page (`/viewer/:threadId` in portal) + `GET /v1/threads/:id/current-chart`. `open_companion_viewer` URLs now resolve. 17 Plan-6 portal tests + 113 wiz-services tests passing. (Portal has pre-existing build/test breakage in unrelated files — see limitations.) Mocked only.
- 2026-05-30: Plan 7 implemented — apply_filter (deterministic: commits a Claude-built conditionModel via /viewsheet/create modification-only). 24 MCP tools total. 107 plugin tests + 120 wiz-services tests passing. Mocked only.
- 2026-05-30: Plan 8 implemented — bundled skill content: `skills/viz-chat/SKILL.md` (workflow + 24-tool reference) + `instructions/worksheet-construction.md` + `instructions/condition-model.md`; SessionStart now points logged-in sessions at the skill. No MCP tools added (still 24); no new tests. 107 plugin tests still passing. Enums/chart-types/binding shapes copied verbatim from `wiz-services` source as of this date.
- 2026-05-30: Plan 9 implemented — two bundled read-only subagents (`chart-type-selector`, `viz-reviewer`) under `agents/`; SKILL.md documents when to delegate. No MCP tools added (still 24); no new tests. 107 plugin tests still passing. Step events dropped (Claude Code does not surface MCP progress to users). Plugin is v1 feature-complete (mocked; live SSO still pending StyleBI).
- 2026-05-30: Auth rewired to StyleBI's token-callback SSO — login_start builds a `/sso/authorize?callback={deployment}/api/wiz/auth/callback?nonce=` URL; wiz-services `POST /api/auth/callback` (nonce branch) verifies + stashes the JWT; `login_complete` polls `GET /api/wiz/v1/auth/pickup`. WizClient prefix corrected to `/api/wiz`. Dead `/v1/auth/exchange` + cliTokenService removed. Zero StyleBI code change (dev `wiz.service.url=http://localhost:8000`). 100 plugin tests + 154 wiz-services tests passing. Mocked only; awaiting a live StyleBI run.
- 2026-05-30: create/modify rewired to StyleBI's autoBinding (SP1) — create_viewsheet sends {wsId, intentCategory, fieldConfigs} to /viewsheet/autoBinding (StyleBI's recommender binds + renders); change_chart_type → /viewsheet/changeType threading autoBindingRuntimeId/wizRuntimeId; validate_binding dropped (23 MCP tools); currentChart tracks autoBindingRuntimeId; result rows mapped as objects. Zero StyleBI change. 95 plugin tests + 91 wiz-services (v1+routes) tests passing. Mocked only. (SP2 = skill realignment; SP3 = save/table-meta/datasources still pending.)
- 2026-05-30: skill content realigned to autoBinding (SP2) — worksheet-construction.md, SKILL.md, and the chart-type-selector agent now describe `create_viewsheet` as `{wsId, intentCategory, fieldConfigs}` (StyleBI's recommender binds + renders); slot taxonomy replaced by an explicitBindings escape hatch; all `validate_binding` references removed (23 tools). Docs/agent only — no code change; plugin suite still green (95 tests). (SP3 = save/table-meta/datasources still pending.)
- 2026-05-30: peripheral data fixes (SP3) — save_viewsheet drops the StyleBI-ignored sourceWorksheetIdentifier; list_datasources and get_table_details now read wiz-services' local annotated Mongo store (databases/tables collections, like search_schema) instead of StyleBI (no datasource-list endpoint existed; /datasource/table/meta returned an incomplete shape). Plugin tools + result shapes unchanged (23 tools). wiz-services v1+routes suite green (95 tests); plugin suite green (95 tests). Mocked only. REST reconciliation complete.

## Known limitations (Plan 9)

- **Subagents are advisory and read-only.** `chart-type-selector` and `viz-reviewer` recommend/review but cannot commit; the main assistant still performs every create/modify/save. A plugin agent's `tools:` allowlist cannot be narrowed to *only* this plugin's MCP tools (it could in principle see other configured MCP tools), but it is restricted to the read-only viz-chat tools listed and to no mutating ones.
- **Step events not implemented.** Real-time per-step progress was cut: Claude Code does not surface MCP progress notifications to the user, so there is nothing to display. Tools return a `summary` instead.
- **Skill guidance is advisory.** The SKILL.md + instruction files tell Claude the tool workflow and the conditionModel/binding shapes, but nothing enforces them at runtime; StyleBI is still the validator (it rejects a bad worksheet/binding/conditionModel with an error message).
- **Sourced enums can drift.** Chart types, condition operations, and aggregate formulas in the instruction files were copied from `wiz-services` source on 2026-05-30. If those enums change, re-sync the instruction files (they are not generated).
- **Only `apply_filter` among modifications is implemented.** `apply_highlight` and `set_field_binding` (sort/rank/aggregate) are deferred: StyleBI applies highlight and field-binding changes client-side and exposes no server-side REST endpoint for `wiz-services` to commit them, and the plugin tools must be deterministic (no wrapping the LLM modification agents). They are tracked in `docs/superpowers/plans/ROADMAP.md`.
- **`apply_filter` replaces the chart's filter** on each call (sends the full conditionModel); there is no incremental merge.
- **Companion viewer requires a recorded chart** and a `/wiz` mount (see Plan 6 notes, unchanged).
- **Pre-existing portal build/test breakage** in unrelated files persists (see Plan 6 notes).
- **StyleBI endpoints assumed:** `/ws/generate`, `/viewsheet/create` (create + modification-only filter), `/viewsheet/close`, `/vs/condition/browse-data`, `/visualization/save`, `/visualization/delete`, embed `/runtime/:runtimeId/:assemblyName`.
