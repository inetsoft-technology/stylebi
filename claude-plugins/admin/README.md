# stylebi-admin-chat

Claude Code plugin for administering StyleBI Enterprise Manager **server properties** in natural
language: inspect the catalog, preview a change as a reviewable plan, and apply it all-or-nothing
with an audit trail. Tools are exposed under the `mcp__admin-chat__*` namespace.

## No server-side feature flag is required

Unlike `stylebi-composer-chat`, admin-chat needs no `wiz.agent.pairing.enabled` — there is no
pairing and no sheet to connect to. The only requirements are:

- a StyleBI deployment carrying the admin endpoints (`/api/wiz/v1/admin/*`), and
- a **Site Administrator** account with Enterprise Manager properties access.

The two changeset-audit endpoints (`list_changesets`/`get_changeset`) are served by the StyleBI
**enterprise** module. On a community-only deployment they return 404; everything else works.

## Install

```
/plugin marketplace add inetsoft-technology/stylebi
/plugin install stylebi-admin-chat@stylebi-chat
```

This runs as a local stdio MCP server. The runnable server is a single bundle with no build step,
no `npm install`, and no `node_modules` required at runtime. Restart Claude Code so it loads the
server. Tools appear as `mcp__admin-chat__*`.

**Updating:**

```
/plugin marketplace update stylebi-chat
/plugin update stylebi-admin-chat@stylebi-chat
```

## Log in — with the StyleBI URL only

```
/stylebi-admin-chat:login https://your-stylebi-host
```

`login_start` takes only `styleBIUrl` — there is no way to route the login anywhere else, and
every admin call always talks to StyleBI's own `/api/wiz` agent API directly.

`status` answers "will admin calls work right now" by making one, so `adminReachable` also goes
false for an expired credential, an account that is not a Site Administrator, a server that
answers the admin API with an error, and a server that does not answer at all.

## The safety model in three sentences

`preview_changes` writes nothing — it resolves a plan and returns a `planHash`. `apply_changes`
re-resolves that plan from its own request body and refuses with a conflict if any current value
drifted in between, which guarantees the plan a human approved is the plan that executes but does
*not* prove a human reviewed it. There is no restore tool: restoring storage on a running server is
untested and needs a restart, so recovery is a manual offline operation or a revert of the
individual changes recorded in the audit trail.

## Documentation search

`search_product_docs` gives the agent the product documentation, so a task described in natural
language can be turned into the right property without reading product source. Two corpora are
searchable via `corpus`. The default, `"docs"`, is the end-user product documentation — good for
finding the feature and the page. `"properties"` is per-property reference documentation for
server properties, generated from the source, and answers the property name, default, type and
organization scope directly. The property corpus does not cover every property yet, so an empty
result there is not evidence a property does not exist — fall back to `"docs"`, then to
`list_properties`.

It requires `chat.app.internal.url` or `chat.app.server.url` to be configured on the StyleBI
server, and a StyleBI build carrying `/api/wiz/v1/docs/search`. Without the former it returns 503
naming both properties; against an assistant server that predates the endpoint it returns 502
asking for an upgrade.

## Commands

- `/stylebi-admin-chat:login`
- `/stylebi-admin-chat:logout`
- `/stylebi-admin-chat:status`

## Subagents

- `admin-reviewer` — dispatched automatically whenever a proposed plan requires sign-off before
  it can be applied.

This plugin covers server properties, schedule tasks, resource permissions, identities,
authentication/authorization providers, data sources, cluster server node pause/resume,
viewsheet/folder structural CRUD, server license keys, presentation settings, custom shapes,
themes, the recycle bin, materialized views, and repository asset export/import. Ask the
assistant what it can help with, or describe the task in plain language — it will route to the
right area and, for anything storage-scoped or high-risk, always show a reviewable plan before
applying anything.
