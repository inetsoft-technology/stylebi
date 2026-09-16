---
name: admin-chat
description: Use when the user wants to inspect or change StyleBI Enterprise Manager server properties from Claude Code — covers the preview → review → confirm → apply flow, the safety model (drift gate, snapshot, verify, rollback, audit), recovery, and every admin-chat MCP tool.
---

# StyleBI admin-chat

Administer StyleBI server properties from Claude Code. Every change is previewed as a plan, reviewed, confirmed by a human, then applied all-or-nothing and recorded in an audit trail.

## When to use

The user wants to read or change StyleBI Enterprise Manager server settings — "set the mail server", "raise the max query rows", "turn on X", "what is property Y". Not for editing worksheets (worksheet-chat), viewsheet scripts (script-chat), or building charts (viz-chat).

## Prerequisites

1. Logged in — if `status` says otherwise, run `/stylebi-admin-chat:login <stylebi-url>`.
2. `status` must report `adminReachable: true`. It establishes that with an authenticated call to the admin API, so false means admin calls genuinely will not work now; `reachability` says why — `unauthorized` (credential expired: log in again), `misrouted` (a legacy `wizServicesUrl` field on the stored credential, from before `login_start` dropped that parameter: log in again), `forbidden` (wrong account), `server-error` (server up, login fine, its own logs are where to look), `unreachable` (server down or wrong URL).
3. The logged-in user must be a Site Administrator with Enterprise Manager properties access — this is what `reachability: "forbidden"` reports.

## The safety model

- **Preview writes nothing.** `preview_changes` resolves and validates a plan and returns a `planHash`. The server is untouched.
- **The hash is a drift gate.** `apply_changes` re-resolves the plan from your request body and recomputes the hash. If any current value moved in between, it returns `status: "conflict"` and applies nothing. This guarantees the plan a human approved is the plan that executes — it does **not** prove a human reviewed it, which is why the confirmation step below is yours to enforce.
- **The request body IS the plan.** The server never trusts stored state. Pass back the same `task` and `changes` you previewed.
- **Snapshot before side effects.** When the plan says `requiresStorageBackup`, `apply_changes` takes a full storage snapshot first and records its path in the audit trail.
- **All-or-nothing.** Apply re-reads each value to verify it. If any change fails, the applied ones are undone.
- **Audited.** Every property change is recorded, correlated by `transactionId`, with before/after values and the reviewer's verdict.

## Canonical workflow

```
search_product_docs(task description)       ← learn the feature and the property name(s)
  → list_properties(filter) / get_property  ← confirm they exist, read current values
  → preview_changes(task, changes[])        ← writes nothing; returns the plan + planHash
  → show the whole plan to the user
  → [if requiresAgentSignoff] dispatch the admin-reviewer subagent
  → GET EXPLICIT HUMAN CONFIRMATION         ← always, every time
  → apply_changes(task, changes[], planHash, reviewOutcome)
```

1. **Research.** When the user describes a task rather than naming a property — "make cached report results last longer", "turn on SSO" — call `search_product_docs` first. You do not have the product source in normal use; the documentation is how a task becomes a property name.

   **Search `corpus: "properties"` first.** That corpus is per-property reference documentation written from the source, so it answers "which property, what default, what type, is it org-scoped" directly. `modules` does not apply to it and is rejected.

   Fall back to the default `corpus: "docs"` when the property corpus has nothing, or when you need to understand the *feature* rather than the setting. There, start with `modules: ["administration", "integration", "install"]` for a server-configuration question and **retry without `modules` if results are thin** — plenty of administrative answers live in end-user modules. Results are ranked but not filtered in either corpus: judge relevance from the score and the text.

   The property corpus does not yet cover every property. An empty result there is not evidence the property does not exist — try `docs`, then `list_properties`.
2. **Preview.** Call `preview_changes` with a clear `task` — it is written into every audit record. One call carries every property in the task; do not preview them one at a time.
3. **Show the plan.** Present every `currentValue → proposedValue` with its risk, and say whether a storage snapshot will be taken. This is the human's single decision point.
4. **Agent sign-off, when required.** If `requiresAgentSignoff` is true, dispatch the **admin-reviewer** subagent, relay its verdict and issues to the user, and keep its `reviewOutcome` string.
5. **Confirm.** Always get explicit human confirmation before applying — including for low-risk plans. Never apply on your own initiative.
6. **Apply.** Call `apply_changes` with the same `task` and `changes`, the `planHash` from step 2, and a non-blank `reviewOutcome`. **A signoff-required plan is refused without one** — use the subagent's verdict; for a low-risk plan a short note on who approved it is enough.

### Documentation is not the catalog

Three rules, each covering a mistake the documentation actively invites:

- **The catalog decides what can be VALIDATED, not what exists.** `recognized: false` means the server has no type, range or allowed values to check against — nothing more. It is not evidence the property is absent. The catalog is small; most real properties are not in it.
- **Read `exists`, not `currentValue`, to decide whether a property is real.** `exists: "confirmed"` means it is catalogued, is one of the seven application credentials, or holds a value. `exists: "unknown"` means the server genuinely cannot tell, because an uncatalogued property nobody has set looks exactly like a misspelled name; the response carries a `guidance` string saying so. **A null `currentValue` is not evidence of anything.** On a server where a feature has never been configured, every one of its properties reads null — so "they all came back null" means the feature is unconfigured, never that its settings live somewhere other than server properties. If you find yourself concluding that a documented setting is stored outside server properties, you are misreading this signal: say what you found and ask, rather than falling back to hand-entry instructions.
- **Never carry a property name straight from a doc into `preview_changes`.** Confirm it with `get_property` first. An uncatalogued property means the server cannot validate the value and will take a full storage snapshot; a doc typo must not reach that path silently. When `exists` is `unknown`, `search_product_docs(corpus: "properties")` is what settles the spelling — it is written from the source and gives the exact name.
- **Empty results are not an answer.** `matches: []` with a `modules` filter set means search again unfiltered. Only after an unfiltered search comes back empty should you tell the user the documentation does not cover it.

`search_product_docs` needs the deployment's AI assistant server configured. If it returns 503, say so plainly and fall back to filtering `list_properties` — do not silently guess a property name instead.

## Reading the result

- `applied` — every change verified. Report the before/after values.
- `conflict` — the plan drifted; **nothing was applied**. Show the returned `plan` as a fresh diff, get confirmation again, and apply with the new `planHash`. Never re-send the old hash.
- `rolled-back` — a change failed and every applied change was undone. Say which one failed and that the server is back to its prior state.
- `rollback-failed` — **the server may be partially changed.** Do not retry. Report `rollbackFailures` to the user as requiring manual operator intervention, along with the `backupRef` if one was taken.

A non-200 error never means "partially applied". Only `rollback-failed` does.

## Errors worth recognizing

- **Secret property.** Reading and writing are separate rules, and conflating them will make you refuse work you can do.
  - **Reading is always refused**, on either of two grounds: the property's **name** contains `password`, `secret` or `credential`, ends `.key`, or starts `license.`; or it is one of the **seven application credentials** listed below, whatever its name looks like. Either way `currentValue` comes back null no matter what is stored, and the value is not read at all — not even to test whether one is set. The name half over-matches a few harmless properties (e.g. `enable.changepassword`, `sso.rsa.public.key`), so a null there is the rule firing, not an empty property.
  - **A null on a credential says nothing about whether it is set.** For the seven below `exists` still reports `confirmed`, because the server knows they are real without reading them — so do not read a null as "not configured" and do not go looking for the setting somewhere other than server properties.
  - **Writing is allowed for those same seven application credentials**, which the server writes through the same encrypting accessor Enterprise Manager uses. Being unable to read one does not mean you cannot set it. Put them in `preview_changes` like any other property, and **do not tell the user to type them into Enterprise Manager by hand:**

    | | |
    |---|---|
    | SSO | `openid.client.secret`, `stylebi.google.openid.client.secret` |
    | Mail | `mail.smtp.pass`, `mail.smtp.clientSecret`, `mail.smtp.accessToken`, `mail.smtp.refreshToken` |
    | Logging | `log.fluentd.security.sharedKey` |

  - **Neighbouring properties that look like they belong but do not** — and they do not behave alike either, so never assume from a name in either direction. `mail.smtp.tokenUri` sits in the same mail settings block and is an ordinary property: readable, and set the ordinary way. `log.fluentd.security.password`, the direct sibling of the shared key, is stored as plain text and is *not* one of the seven — but its name matches the pattern, so admin-chat refuses to read it or to write it. Enterprise Manager is the answer for that one.
  - **Every other secret-named property is still refused (400)** for a write, including `password.encryption.key`, `password.hash.key`, `jwt.signing.key` and `license.*`. The error names the property. Do not work around it — Enterprise Manager is the right answer for those.
  - Two refusals you may hit on the seven writable ones: an **empty value** (use a null value to reset instead — an empty one would silently leave the old secret in place), and a deployment using **cloud secrets**, where a value stored under the property is resolved as a *reference* to a secret rather than used directly, so admin-chat will not set it at all. Relay the server's message and point the user at Enterprise Manager; do not guess which page or field they need, because it differs per property.
  - **You cannot verify a secret write by reading it back** — the read rule still applies. Say so, and tell the user to confirm in Enterprise Manager or by an actual login.
  - **Neither rule catches every secret, so handle any value that looks like one as a secret.** The list of seven is maintained by hand, so a property the product has newly started encrypting is readable until someone adds it; and a credential can sit under a name matching no pattern at all — a connection string with an inline password, a `*.token`, a `*.pat`. If a value that looks like a credential ever comes back, **do not repeat it to the user, put it in a summary, or carry it into another tool call**, whatever the rules above say about its name.
- **Uncatalogued property.** `recognized: false` means the server cannot validate the value and will take a full storage snapshot. Warn the user before proceeding. It does **not** mean the property does not exist — check `exists` for that.
- **Missing `reviewOutcome` (400).** The plan required sign-off. Run the subagent and retry with its verdict.
- **A field-named input error** (`changes[0].value: required`, `task: required`) is the tool telling you the call was malformed. Fix the call; do not retry it unchanged. In particular, a reset must be spelled `value: null` — omitting `value` is refused on purpose, because a reset is destructive.

## Recovery

There is **no restore tool**, deliberately: restoring storage on a running StyleBI server is untested and requires a restart, so it is not something to trigger from a chat.

To undo a past change: `list_changesets`, then `get_changeset(transactionId)` to read the per-property before-values, then run those before-values back through the normal `preview_changes` → confirm → `apply_changes` flow as a new, audited change. If a snapshot is genuinely needed, give the operator the `backupRef` from the audit record and tell them it is a manual, offline restore requiring a restart.

Timestamps in audit records are formatted strings in server-local time (`"2026-08-03 14:46:43"`), not epoch numbers. Show them as they are.

## Tool reference

`login_start`, `login_complete`, `status`, `logout` — login, current deployment, and clearing stored credentials. `logout(deployment?)` really deletes the stored token; it does not end a StyleBI browser session and does not undo applied changes.
`search_product_docs(query, corpus?, modules?, topK?)` — semantic search, returning ranked chunks with `text`, `module` and a `docUrl`. `corpus: "properties"` searches per-property server-property reference documentation; `corpus: "docs"` (the default) searches the end-user product documentation. `modules` applies only to `docs` and is rejected with `properties`. Needs the deployment's AI assistant server configured (`chat.app.internal.url` or `chat.app.server.url`); returns 503 otherwise.
`list_properties(filter?)`, `get_property(name)` — discovery, with current values.
`preview_changes(task, changes[])`, `apply_changes(task, changes[], planHash, reviewOutcome?)` — the change flow.
`list_changesets(limit?, offset?)`, `get_changeset(transactionId)` — the audit trail (enterprise only; a community-only deployment returns 404).
`backup(transactionId)` — an extra snapshot; apply takes its own when needed.

In `changes`, each entry is `{ property, value }`, and `value: null` means reset to the default. `value` must always be spelled explicitly — a reset is destructive and is never inferred from omission.
