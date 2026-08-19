# Composer pairing: tell the browser an agent joined

## Problem

After a successful `connect_sheet`, the Composer gives no sign whatsoever that pairing worked — the
toolbar button still reads **"Connect to Claude"**, unchanged, and nothing anywhere distinguishes a
paired sheet from an unpaired one. Confirmed by operator observation 2026-08-19 while running the L0
lane of `stylebi-wiz`'s `docs/superpowers/plans/2026-08-17-consolidated-composer-plugin-test-plan.md`
(case 0.11, recorded there as FAIL).

The cause is structural, not a missing element in a template. **The browser mints the code; the agent
joins over HTTP.** The two halves of pairing happen in different processes, and nothing closes the
loop back to the browser, so the browser has no way to know the code it displayed was ever redeemed.

The consequence is precisely what case 0.11 exists to rule out: the only evidence that pairing
succeeded is the agent's own claim that it succeeded.

## Scope

**One-shot confirmation only.** When an agent joins, the browser that minted the code says so.

Deliberately **not** in scope. Each is a real capability, each was considered and dropped as YAGNI
until someone asks for it:

- **Continuous connected/disconnected state.** Would require reacting to three separate ways a
  session ends, one of which produces no server-side event at all (see TTL below).
- **A count of concurrent agents.** The server permits unlimited agents per sheet — `sessions` in
  `SheetSessionService` is keyed by `sessionToken`, and `open()` never deduplicates by `runtimeId`.
  Showing a count is therefore meaningful, but it is a different feature.
- **TTL expiry tracking.** TTL is 30 minutes, refreshed on every `resolve()`, and `evictExpired()`
  runs on a 10-minute fixed delay — so "expired" and "removed from the map" differ by up to 10
  minutes. Any count would have to filter by `isExpired()` itself rather than trust map size.
- **Agent identity.** `ownerIdentity` is the logged-in StyleBI user, not the Claude session. Two
  agents paired by the same user are indistinguishable, so "who is connected" is not answerable
  today regardless of UI.

## Approach

Push on successful join to the browser that minted the code; the component already on screen
subscribes and updates.

Rejected alternatives:

- **A new Command on the `/commands` sheet channel.** Filtered by `RUNTIME_ID_ATTR`, which would be
  convenient, but it requires defining a Command type and adding a branch to the sheet-level command
  handler — a larger change into a channel whose semantics are *sheet content*. "A pairing
  completed" is not sheet content.
- **Polling a new REST status endpoint.** Correct after a page refresh and needs no push, but
  over-built for one-shot confirmation. This is the right foundation for continuous state later, and
  is deliberately left for that work.

## Backend

**Hook point**: `SheetJoinService.join()`, immediately after `sessions.open(...)` returns.

Everything required is already in hand at that line — this is the part that makes the change small:

- `grant.socketSessionId()` — the minting browser's STOMP session, carried through `PairingGrant`
- `JoinSession.socketUserName()` — the destination user resolved at mint time
- `grant.editorContext()` / `session.editorContext()` — the paired location, `null` for a toolbar mint

**Payload** — a new record (e.g. `PairingJoinedNotice`) carrying exactly the three things the client
needs to decide whether the notice is for it:

| Field | Notes |
|---|---|
| `runtimeId` | which sheet runtime was paired |
| `sheetType` | `VIEWSHEET` / `WORKSHEET` |
| `editorContext` | nullable; `null` is the normal whole-sheet toolbar case, never an error |

**Destination**: `/commands/wiz/pairing/joined`, sent via `convertAndSendToUser` so the client
subscribes to `/user/commands/wiz/pairing/joined`. This mirrors mint's
`@SendToUser("/commands/wiz/pairing/mint")` exactly, which is why the client already has the
subscription machinery for it.

**Why not `@SendToUser`**: join arrives over HTTP, not as a STOMP inbound message. There is no
inbound message to annotate, so this must be an active push.

### The trap: a wrong destination fails silently

`SheetAgentBroadcastService` already documents this hazard against itself. Its two helpers address
*different* users and *different* topics — `sendToComposer` uses `socketSessionId` as the
destination user with `ComposerClientService.COMMANDS_TOPIC`, while `sendToBrowser` uses
`socketUserName` with `CommandDispatcher.COMMANDS_TOPIC` plus a runtime header. **Both constants are
named `COMMANDS_TOPIC`.** Its javadoc records the outcome of picking the wrong one: the message is
delivered to a destination with no handler and dropped, "while every call up the stack still reports
success."

This notice uses **neither** constant — its destination is the pairing-specific
`/commands/wiz/pairing/joined`. Use `socketUserName` as the destination user (the value mint's
`@SendToUser` resolved to) and set the session id header, as both existing helpers do.

Because the failure mode is silence rather than an exception, **this cannot be considered working on
the strength of unit tests.** See Testing.

### Push failure must never fail the join

By the time the notice is sent, the agent's session is open and valid. A lost notification degrades
the UI; it does not invalidate the pairing. Wrap the push and log — never let it propagate out of
`join()`.

## Frontend

`ConnectToClaudeComponent` gains a `connected` flag and a **standing** subscription to
`/user/commands/wiz/pairing/joined` — not only while a mint is in flight, because the agent may join
seconds or minutes after the code is displayed.

Three mechanics this implies, none of which the component does today:

- **The component has no `ngOnInit`** — it implements only `OnChanges` and `OnDestroy`. Add
  `OnInit` and open the subscription there, via the same
  `socketConnection.whenConnected().pipe(take(1))` handshake `requestCode()` already uses.
- **Do not tear the subscription down on a `runtimeId` change.** The destination is per socket
  session, not per runtime, so the existing `ngOnChanges` reset should clear `connected` and leave
  the subscription alone. Only the *filter input* changed, not the channel.
- **`ngOnDestroy` must release it.** Today it only unsubscribes `mintSubscription`; a standing
  subscription that outlives its component is a leak, and this component is created and destroyed
  repeatedly by the formula-editor dialog.

On a message, accept it only if **both** hold:

1. `body.runtimeId === this.runtimeId`
2. the notice's `editorContext` equals `this.mintedEditorContext` (both `null` for a toolbar mint)

Then set `connected = true` and clear `code`.

### Why both filters are load-bearing

The component is reused by three hosts — the composer toolbar, the viewsheet script pane, and the
formula editor dialog — and **more than one instance can be alive on one page**. A user-destination
is delivered per *user*, not per sheet and not per pane. So:

- without the `runtimeId` filter, pairing one sheet lights up every open sheet;
- without the `editorContext` filter, pairing from a formula editor lights up the toolbar button too,
  and vice versa.

Either failure produces an indicator asserting something untrue — worse than having no indicator,
because the whole point of 0.11 is to obtain trustworthy independent confirmation.

Compare against `mintedEditorContext`, **not** the live `editorContext` input, for the reason that
field's own javadoc already documents: several hosts derive `editorContext` from mutable state
(`viewsheet-script-pane` follows the onInit/onLoad radio; `formula-editor-dialog` includes the
editable `formulaName`), so the current value can differ from what was actually sent.

### Template and persistence

Replace the `Pairing Code: XXXX` + Copy block with a `✓ Agent connected` line once connected. The
code has been consumed; continuing to show it invites an attempt to reuse it. The button keeps its
label — it remains the "pair another one" entry point.

**No timeout.** The indicator clears via the existing `ngOnChanges` reset on `runtimeId` change, and
when `requestCode()` starts a new mint. A confirmation that erases itself after two seconds is barely
more independent than the agent saying so, which is the thing 0.11 already does not accept.

## Error handling

| Situation | Behaviour |
|---|---|
| Push throws | Logged; `join()` unaffected and still returns the session |
| Notice arrives for another runtime or another pane | Ignored by the filters; nothing surfaced |
| Socket down at join time | Notice lost. No confirmation shown, pairing still live. Accepted for one-shot scope — the future continuous-state work is what closes this |

## Testing

**Backend unit** — join pushes once carrying the grant's `runtimeId`, `sheetType` and
`editorContext`; a toolbar grant pushes `editorContext == null`; a push that throws does not fail
`join()`.

**Frontend unit** — a notice matching both filters sets `connected` and clears `code`; a mismatched
`runtimeId` is ignored; a mismatched `editorContext` is ignored, specifically that a pane-scoped
notice does not light a toolbar instance.

**Live, and required** — because a wrong destination is silent, not an error:

1. Pair from the toolbar; confirm the button area shows connected.
2. Pair from a formula editor; confirm the **toolbar** instance does not change.

Step 1 is what closes case 0.11 in the consolidated test plan.

## Recorded for later

Continuous state needs a "which sessions are live for this `runtimeId`" query, which does not exist
today — `findOpen()` searches by `ownerIdentity` + `sheetType`, not by runtime — plus a decision on
how to present a TTL that expires with no event. Deferred deliberately, not overlooked.
