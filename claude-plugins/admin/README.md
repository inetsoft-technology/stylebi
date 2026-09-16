# stylebi-admin-chat

Claude Code plugin for administering StyleBI Enterprise Manager **server properties** in natural
language: inspect the catalog, preview a change as a reviewable plan, and apply it all-or-nothing
with an audit trail. Tools are exposed under the `mcp__admin-chat__*` namespace.

## No server-side feature flag is required

Unlike `worksheet-chat` and `script-chat`, admin-chat needs no `wiz.agent.pairing.enabled` — there
is no pairing and no sheet to connect to. The only requirements are:

- a StyleBI deployment carrying the admin endpoints (`/api/wiz/v1/admin/*`), and
- a **Site Administrator** account with Enterprise Manager properties access.

The two changeset-audit endpoints are served by the StyleBI **enterprise** module. On a
community-only deployment `list_changesets` and `get_changeset` return 404; everything else works.

## Install (colleagues, via marketplace)

```
/plugin marketplace add inetsoft-technology/stylebi-wiz
/plugin install stylebi-admin-chat@stylebi-wiz
```

This runs as a local stdio MCP server, the same shape as `stylebi-composer-chat`. The runnable
server is a single esbuild bundle with all of `plugin/shared/core` inlined, and it is **committed
to the repo** — a marketplace install works immediately, with no build step, no `npm install`, and
no `node_modules` at runtime.

Restart Claude Code so it loads the server. Tools appear as `mcp__admin-chat__*`.

**Updating:**

```
/plugin marketplace update stylebi-wiz
/plugin update stylebi-admin-chat@stylebi-wiz
```

## 1. Build

```bash
cd plugin/shared/core
npm install

cd ../../admin
npm install
npm run build
```

This produces the runnable server at `plugin/admin/dist/bin.js`.

`shared/core` needs no *build* — esbuild pulls its TypeScript in through the relative imports and
bundles it. But its `npm install` **is** required: `wizClient.ts` does `import axios from "axios"`,
and TypeScript resolves that by walking up from `plugin/shared/core/`, which never reaches
`plugin/admin/node_modules`. Skip it and the typecheck fails with `TS2307: Cannot find module
'axios'` in a file you did not write.

## 2. Register it as an MCP server

The checked-in `.mcp.json` works when Claude Code starts in this directory. Otherwise register the
absolute path:

```json
{
  "mcpServers": {
    "admin-chat": {
      "type": "stdio",
      "command": "node",
      "args": ["/absolute/path/to/plugin/admin/dist/bin.js"]
    }
  }
}
```

## 3. Log in — with the StyleBI URL only

```
/stylebi-admin-chat:login https://your-stylebi-host
```

`login_start` takes only `styleBIUrl` — there is no way to route the login anywhere else, and
every admin call always talks to StyleBI's own `/api/wiz` agent API directly. (A credentials file
from a much older plugin version could still carry a legacy `wizServicesUrl` field from before
this was the case; the plugin refuses such a login with an explanatory error rather than silently
ignoring stale state, and `status` reports `adminReachable: false` with
`reachability: "misrouted"` — the fix is simply to log in again.)

`status` answers "will admin calls work right now" by making one, so `adminReachable` also goes
false for an expired credential (`unauthorized`), an account that is not a Site Administrator
(`forbidden`), a server that answers the admin API with an error (`server-error`), and a server
that does not answer at all (`unreachable`). The last two are kept apart because their remedies
differ: a live server returning 500 is not fixed by starting it.

## The safety model in three sentences

`preview_changes` writes nothing — it resolves a plan and returns a `planHash`. `apply_changes`
re-resolves that plan from its own request body and refuses with a conflict if any current value
drifted in between, which guarantees the plan a human approved is the plan that executes but does
*not* prove a human reviewed it. There is no restore tool: restoring storage on a running server is
untested and needs a restart, so recovery is a manual offline operation or a revert of the
individual changes recorded in the audit trail.

Read `shared/skills/admin-chat/SKILL.md` for the full model, and `CLAUDE.md` for the operating
rules.

## Documentation search

`search_product_docs` gives the agent the product documentation, so a task described in natural
language can be turned into the right property without reading product source. The request goes
plugin → StyleBI `/api/wiz/v1/docs/search` → the deployment's configured AI assistant server →
Pinecone; the Pinecone and Voyage credentials stay on the assistant server and are never exposed
to the plugin or to StyleBI.

Two corpora are searchable via `corpus`. The default, `"docs"`, is the end-user product
documentation — good for finding the feature and the page. `"properties"` is per-property
reference documentation for server properties, generated from the source, and answers the
property name, default, type and organization scope directly. It lives in its own Pinecone
namespace, so it is invisible to the assistant's own chat retrieval and unaffected by a
documentation re-upload. `modules` applies only to `"docs"` and is rejected with `"properties"`.

The property corpus does not cover every property yet, so an empty result there is not evidence
a property does not exist — fall back to `"docs"`, then to `list_properties`.

It requires `chat.app.internal.url` or `chat.app.server.url` to be configured on the StyleBI
server, and a StyleBI build carrying `/api/wiz/v1/docs/search`. Without the former it returns 503
naming both properties; against an assistant server that predates the endpoint it returns 502
asking for an upgrade.

## Tests

```bash
cd plugin/admin && npm test
```

117 tests across eight suites. Every one mocks `WizClient` — see the coverage note below.

## Known gaps and follow-ups

1. **Three behaviours remain unverified against a live server.** A live smoke test on 2026-08-03
   passed end to end — login, catalog reads, alias resolution, the secret-property refusal, preview,
   apply with read-back verification, the 409 drift gate returning its fresh plan, and the audit
   trail. Not exercised, and so still mocks-only: the `backup` / storage-snapshot path (the only
   storage-scoped catalogued property fires repository-wide events on a live server); the
   `requiresAgentSignoff` path, and therefore the `admin-reviewer` dispatch and the
   blank-`reviewOutcome` 400; and `rolled-back` / `rollback-failed`, which cannot be induced through
   the public API. All cluster behaviour is also unverified — the test was single-node.
2. **The JSON-RPC loop in `src/bin.ts` is a second copy** of the same loop in
   `plugin/composer/src/bin.ts` (formerly two separate copies under `plugin/worksheet` and
   `plugin/script`, before those merged into `plugin/composer`). Extracting it into `shared/core`
   is the right fix but touches a module two shipping plugins depend on, so it was deliberately
   deferred rather than done here.
3. **Enterprise-only endpoints are not detected.** `list_changesets`/`get_changeset` surface a raw
   404 on a community-only deployment instead of explaining why.
4. **`isSecret` over-matched on the server side — fixed, pending merge.** `enable.changepassword`,
   `sso.rsa.public.key`, and `google.maps.key` were refused though they are not secrets (the SSO
   *private* key half still correctly refuses). `stylebi` PR #4799 adds a verified
   `CONFIRMED_NOT_SECRET` allow-list to `AdminPropertyCatalog.isSecret`, checked ahead of the
   name-shape test, with regression tests for both directions.
5. **`search_product_docs` retrieval quality — smoke-tested 2026-08-05, mixed but usable.** Run
   against a local StyleBI plus the AI assistant server, driven only through the plugin's MCP
   tools. Of three real tasks:

   - *"raise the maximum query rows"* — the docs named `query.runtime.maxrow` outright. Catalogued,
     low risk, no sign-off. **Documentation search alone was sufficient.**
   - *"make cached report results last longer"* — the docs named `query.cache.timeout` outright,
     and the stated default ("10 minutes") matched the live value (`600000`). **Sufficient.**
   - *"change the mail server"* — the docs found the right page (`ConfigureServerEmail`, score
     0.51) and identified the SMTP/'Mail Host' setting, but named **no property**; the page defers
     to an "Email Properties" link that was not in the returned chunk. `list_properties("mail")`
     completed it as `mail.smtp.host`. **Documentation alone was NOT sufficient** — the
     search → catalog workflow in the skill was what closed the gap, not the search on its own.

   Expect roughly this: the docs reliably identify the *feature and page*, and often but not always
   the property name. The `list_properties` follow-up is load-bearing, not ceremonial.

6. **A documented property can be uncatalogued.** `query.cache.timeout` is named in the product
   documentation and is live on the server (`600000`), but `get_property` returns
   `recognized: false` — so a change to it is `risk: "high"`, `snapshotScope: "storage"`, and
   `preview_changes` reports `requiresStorageBackup: true` and `requiresAgentSignoff: true`. The
   "documentation is not the catalog" guardrail in the skill is not hypothetical; it fired on the
   first realistic task. Widening the catalog to cover documented performance properties would
   avoid a full storage snapshot for an ordinary tuning change.

7. **The module list in the tool description can drift.** It is written in
   `plugin/shared/core/src/tools/searchProductDocs.ts` (and duplicated in
   `wiz-services/src/tools/docs/searchProductDocs.ts`) but derived, server-side, from
   `chat-app/server/src/utils/mappings/titles/*.json` in the separate `assistant` repo. As of
   2026-08-26 the hardcoded list had drifted: `dashboard` and `dashboardscript` were carried over
   from before the product's viewsheet rename and no longer correspond to any titles mapping file
   (`viewsheet`/`viewsheetscript` are the real, current entries) — fixed in both files. Nothing
   at runtime spans the two repos to catch a future divergence, and there is no CI hook to check
   it automatically (this repo has no build/test CI pipeline today, and CI has no checkout of the
   separate `assistant` repo to diff against) — run `npm run check:doc-modules-drift` from the
   repo root (`scripts/check-doc-modules-drift.js`, needs `ASSISTANT_REPO_PATH` pointed at an
   `assistant` checkout) by hand whenever a doc module is added or removed in either repo. A
   stale entry is recoverable — the 400 returns the real list — but costs a round trip. A doc
   module ingested without a titles mapping file is also not selectable at all, though an
   unfiltered search still reaches it.

8. **Corpus cosmetics.** Returned `path` values use Windows separators
   (`modules\administration\pages\Cache.md`), reflecting where ingestion ran, and `docUrl` values
   are pinned to docs version `1.0.0`. Neither affects retrieval; both are visible to the agent.

9. **Smoke testing should move into containers — planned, needs its own issue.** The 2026-08-05
   run above was driven from a developer machine, which has two problems. It *pollutes local
   configuration*: registering the plugin meant editing the repo-tracked root `.mcp.json`, and the
   test set a live server property. And it *fails to isolate the agent from the product source*,
   so the run is not faithful to end-user conditions — in that run, two blocking transport defects
   were root-caused by reading StyleBI's `WebConfig.java`, which a deployed agent could not do.

   The intended shape is **two configurations**: a sealed container with no source mounted, whose
   job is to *find* problems under end-user-faithful conditions; and a separate diagnosis
   configuration, source-mounted or otherwise instrumented, whose job is to *explain* a problem the
   sealed run already found. Keeping the diagnosis escape hatch out of the sealed run is the point
   — once source is reachable it gets reached, and the run stops being evidence.

   Worth building into the sealed run: **capture artifacts on failure**, the StyleBI server log
   above all. The only reason the source read was needed in 2026-08-05 is that the stack trace was
   invisible — the agent saw an opaque `HttpMessageNotReadableException` string and nothing behind
   it. With logs captured, most diagnosis would not need source at all, and what did would start
   from a stack trace rather than a guess.

   **The "sealed container with no source mounted" half of this now exists, built for a related but
   narrower goal.** `test/isolation/docker/` (added alongside the process-level tool-isolation
   harness's own container follow-up) builds the plugin into a multi-stage Docker image — final
   layer holds only the compiled `dist/bin.js` and the plugin's static manifest files, no
   `node_modules`, no TypeScript source, no `test/` — and runs it with a `--read-only` root
   filesystem, dropped capabilities, and default-bridge network isolation. It was built to
   red-team *tool access* (can the agent reach a host shell, another session, the filesystem)
   rather than to run *this* item's doc-search smoke test, so it is not itself the fix for this
   gap — but it is the reusable "no source, sealed" building block this item calls for, and a
   future smoke-test container should extend `test/isolation/docker/Dockerfile` rather than write a
   second one. Still open, unchanged by that work: `.mcp.json` pollution avoidance for a live smoke
   run, the diagnosis-configuration half, and the artifact/log-capture-on-failure mechanism
   described above.
