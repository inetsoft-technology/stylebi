---
description: Connect to a live StyleBI sheet open in the Composer. Usage - /stylebi-composer-chat:connect <pairing-code>
---

The user has a worksheet or viewsheet open in the StyleBI Composer and clicked the AI agent pairing
button, which displays a pairing code. That code is the only thing this command actually consumes
— but the user may instead hand you the deployment URL first, and that's a login request, not an
error.

One pairing code covers every domain for whichever sheet it was minted from. A viewsheet code
serves layout, formatting, binding and script tools together — no second code is needed to move
between them. Ask for another code only when pairing a genuinely different sheet, such as the
worksheet feeding an already-connected viewsheet.

1. Look at `$ARGUMENTS`:
   - **Looks like a pairing code** (short alphanumeric token, no `://`, no host:port shape — e.g.
     `EMVMG496`): go straight to step 2.
   - **Looks like a URL or host:port** (starts with `http://`/`https://`, or is a bare
     `host:port`): the user is telling you where to log in, not giving you a pairing code yet.
     Don't ask "do you want to log in?" and don't call `status` first to check — immediately
     follow the `login` command's flow (`login_start` + `login_complete` in the same turn)
     against that URL. When login completes, tell the user to click the AI agent pairing button
     in the Composer to get a code, then stop and wait for it. Pairing always comes after login,
     never before.
   - **Missing entirely**: tell the user to click the AI agent pairing button in the StyleBI
     Composer to get a code.
2. Call the MCP tool `connect_sheet` with `{ code: "<code>" }`.
3. On success, print the confirmation summary (runtimeId + ownerIdentity). The result's
   `sheetType` field says whether this code paired a `viewsheet` or a `worksheet` — note which.
   If the summary also mentions being paired from a specific script location, this is a
   **pane-scoped session** (see below) rather than a whole-sheet one — say which location.
4. In the SAME turn, call the read tool matching that `sheetType` so the user knows what the
   agent sees:
   - `viewsheet` → call `read_viewsheet_model` with no arguments and summarize the layout
     (how many assemblies, of what types) in 1–2 sentences.
   - `worksheet` → call `read_worksheet_model` with no arguments and summarize the worksheet
     structure (tables, column counts, any existing joins or aggregates) in 1–2 sentences.
5. A successful connect starts a live editing session. Before making any further edits (layout,
   formatting, binding, conditions, script, or worksheet structure) or saving, load the
   `composer-chat` skill via the Skill tool for the full tool reference and workflow — this
   command alone only covers connecting, not the rest of the edit/save/detach lifecycle.
6. Errors:
   - 403 "Sheet agent pairing is disabled": the `wiz.agent.pairing.enabled` feature flag is off —
     tell the user to enable it in their StyleBI configuration.
   - 403 "Identity mismatch": the agent is logged in as a different user than the one who opened
     the sheet in the browser. This is not a permissions problem to work around — log in (via the
     `login` command) as the same user who has the sheet open, then retry `connect_sheet`.
   - 404 "Invalid or expired pairing code": usually the code expired (short TTL) or was already
     used — ask for a fresh one and retry `connect_sheet` once. But if this happens on a code the
     user says is fresh, or fails again on the retry, **stop assuming code expiry** —
     `connect_sheet`'s own error message will say so explicitly on the 2nd+ consecutive failure.
     Confirm the deployment URL you're logged in against (`status`) actually matches what the
     user's browser is pointed at right now, and redo `login_start`/`login_complete` against the
     correct URL before asking for yet another code. A rebuild or restart changing the exposed
     port is the single most common cause of this loop, and it's easy to overlook because
     `status`/`login_start` will happily proceed against whatever URL they're given without
     validating it.
   - Any other error: surface the message and stop.

## Pane-scoped pairing: a code minted from inside a script location

The toolbar's AI agent pairing button is not the only place a code comes from. A code can also be
minted from **inside** a specific script pane or formula editor — this scopes the whole session to
that one location rather than the whole sheet, and it is the only way to reach an
expression-level script (a calculated field's formula, a worksheet expression column, a worksheet
condition). Where the user clicks depends on which location they want to pair:

| Script location (`kind`) | Where to click |
|---|---|
| `viewsheetOnInit` / `viewsheetOnLoad` | The viewsheet's own Script pane (via the viewsheet toolbar, or the VS Options dialog's Script tab) — pick Init or Load, then click **Connect Agent** in that pane. |
| `assemblyMain` | The assembly's property dialog → **Script** tab → **Connect Agent**. |
| `assemblyOnClick` | The assembly's property dialog → its **onClick**/**Click** script sub-tab (present on clickable assemblies) → **Connect Agent**. |
| `calcField` | The worksheet's "New calculated field" / edit-calc-field dialog → the formula editor → **Connect Agent**. |
| `worksheetExpression` | An existing worksheet column's expression editor → **Connect Agent**. Only `read_script`/`update_script` reach it — see caveat below. |
| `worksheetCondition` | A worksheet condition's expression editor → **Connect Agent**. Replaces the whole condition (operator + all values) when edited. Only `read_script`/`update_script` reach it — see caveat below. |
| `worksheetConditionValue` | A single-value worksheet condition's own value editor → **Connect Agent**. Narrower than `worksheetCondition`: touches only that one value, never the operator or other slots — only offered when the condition's operator takes exactly one value. Only `read_script`/`update_script` reach it — see caveat below. |

If a user asks "how do I get a code for X" (a calc field, an onClick handler, etc.), or if a
script tool call comes back refused because the target is outside the current session's grant,
point them at the matching row above — the fix is to open **that** location's own editor and pair
again from there, not to try to widen the existing session (the server will not allow it, and this
plugin will not try to talk it into allowing it either).

Once paired this way, `status`'s summary and the `connect_sheet` result both say which location the
session is scoped to. From then on, script tools (`read_script`, `update_script`,
`set_script_enabled`, `execute_script`, `run_script_live`, `get_script_context`) default their
target to that location, so the normal call needs no target argument at all — pass one explicitly
only to address a different location, and expect the server to refuse it (reported plainly, not
retried) if that location falls outside this session's one-location grant.

**Caveat: `worksheetExpression`, `worksheetCondition` and `worksheetConditionValue` are served by a
narrower pair of tools than the other kinds.** Clicking Connect Agent from any of the three editors
pairs a session under StyleBI's *worksheet* runtime (not the viewsheet one the other kinds use), and
`read_script`/`update_script` route to it automatically — the normal call, exactly as for any other
kind, needs no target argument once paired this way. But `execute_script`/`run_script_live`/
`set_script_enabled`/`get_script_context` have no worksheet-side equivalent at all (there is no
execute, no live-run, no enable flag, and no separate scripting context for a plain expression/
condition value) — calling one of them on a worksheet-hosted target is refused outright, pointing
back at `read_script`/`update_script`. A pane-scoped session may only rewrite the expression/
condition *text* of the one location it was paired from — structural changes to the column itself
(type, SQL flag, a rename) still need the worksheet tools' own `edit_expression`/`edit_condition`
on the ordinary, non-pane-scoped worksheet session.

**`save_viewsheet` is not narrowed by pane-scoped pairing** — it always persists the whole
viewsheet. Calling it from a pane session asks for confirmation first, since it writes more than
that session's own grant covers; a worksheet-hosted pane session has no `save_viewsheet` at all
(use `save_worksheet`, or save from the Composer).
