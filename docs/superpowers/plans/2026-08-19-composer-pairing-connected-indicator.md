# Composer Pairing Connected Indicator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** After an agent redeems a pairing code, the Composer that minted it visibly says an agent connected, instead of continuing to read "Connect to Claude".

**Architecture:** `SheetJoinService.join()` pushes a `PairingJoinedNotice` over STOMP to the minting browser's user destination, best-effort so a lost notice can never fail the join. `ConnectToClaudeComponent` holds a standing subscription and accepts a notice only when both `runtimeId` and the minted `editorContext` match, because the component is reused by three hosts and several instances can be alive on one page.

**Tech Stack:** Java 17 records + Spring `SimpMessagingTemplate` via `CommandDispatcherService`; Angular standalone component with STOMP over `ViewsheetClientService`; JUnit 5 + Mockito; Vitest via `ng test`.

**Design spec:** `docs/superpowers/specs/2026-08-19-composer-pairing-connected-indicator-design.md`

## Global Constraints

- **Scope is one-shot confirmation only.** Do not add continuous state, an agent count, TTL tracking, or agent identity. The spec records why each is out of scope.
- **A push failure must never fail `join()`.** The session is already open and valid when the notice is sent.
- **Destination errors are silent, not exceptions.** `SheetAgentBroadcastService`'s own javadoc records that addressing the wrong topic delivers to a destination with no handler and is dropped "while every call up the stack still reports success". Unit tests cannot prove this works; Task 3's live check is mandatory.
- **All code, comments and test names in English.**
- Java tests carry `@Tag("core")`, matching every other test in `inetsoft.web.wiz.pairing`.
- Repository root for every path below is the `community` submodule (remote `inetsoft-technology/stylebi`), **not** the outer `stylebi-enterprise` checkout.

---

## File Structure

| File | Responsibility |
|---|---|
| `core/src/main/java/inetsoft/web/wiz/pairing/PairingJoinedNotice.java` | **Create.** Wire payload: the three fields the browser needs to decide if a notice is for it. |
| `core/src/main/java/inetsoft/web/wiz/pairing/SheetAgentBroadcastService.java` | **Modify.** Add `sendPairingJoined(JoinSession)`; owns the destination constant and header shape. |
| `core/src/main/java/inetsoft/web/wiz/pairing/SheetJoinService.java` | **Modify.** Call the push after `sessions.open(...)`, wrapped so it cannot escape. |
| `core/src/test/java/inetsoft/web/wiz/pairing/SheetJoinServiceTest.java` | **Modify.** Constructor gains a 5th argument; add three cases. |
| `web/projects/portal/src/app/composer/gui/wiz/connect-to-claude.component.ts` | **Modify.** `connected` flag, standing subscription, two-way filter, lifecycle. |
| `web/projects/portal/src/app/composer/gui/wiz/connect-to-claude.component.html` | **Modify.** Show the connected line, hide the consumed code. |
| `web/projects/portal/src/app/composer/gui/wiz/connect-to-claude.component.spec.ts` | **Modify.** Add filter and lifecycle cases. |

---

## Task 1: Backend pushes a notice when an agent joins

**Files:**
- Create: `core/src/main/java/inetsoft/web/wiz/pairing/PairingJoinedNotice.java`
- Modify: `core/src/main/java/inetsoft/web/wiz/pairing/SheetAgentBroadcastService.java`
- Modify: `core/src/main/java/inetsoft/web/wiz/pairing/SheetJoinService.java` (constructor, and `join()` after `sessions.open`)
- Test: `core/src/test/java/inetsoft/web/wiz/pairing/SheetJoinServiceTest.java`

**Interfaces:**
- Consumes: `JoinSession(sessionToken, runtimeId, ownerIdentity, sheetType, lastAccess, ttlMillis, connectionMode, socketSessionId, socketUserName, editorContext)`; `EditorContext(kind, assembly, name, table)`; `CommandDispatcherService.convertAndSendToUser(user, destination, payload, headers)`.
- Produces: `PairingJoinedNotice(String runtimeId, SheetType sheetType, EditorContext editorContext)` serialized to `/user/commands/wiz/pairing/joined`; `SheetAgentBroadcastService.sendPairingJoined(JoinSession)`.

- [ ] **Step 1: Write the failing tests**

In `SheetJoinServiceTest.java`, add the mock and change the constructor call in `setUp`:

```java
   @Mock
   private SheetAgentBroadcastService broadcast;
```

```java
      svc      = new SheetJoinService(pairing, sessions, feature, runtimeAccess, broadcast);
```

Add these imports alongside the existing ones:

```java
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
```

Append these three tests to the class:

```java
   // ---------------------------------------------------------------------------
   // notifiesBrowserOnJoin
   // ---------------------------------------------------------------------------
   @Test
   void notifiesBrowserOnJoin() throws PairingException {
      when(feature.isEnabled()).thenReturn(true);
      EditorContext context = new EditorContext("assemblyMain", "Chart1", null, null);
      String code = pairing.mint("vs-9", ALICE_KEY, "sock-1", "alice-dest",
                                 SheetType.VIEWSHEET, context);
      Principal alice = TestPrincipals.user("alice", "host-org");

      svc.join(code, alice);

      ArgumentCaptor<JoinSession> sent = ArgumentCaptor.forClass(JoinSession.class);
      verify(broadcast).sendPairingJoined(sent.capture());
      assertEquals("vs-9", sent.getValue().runtimeId());
      assertEquals(SheetType.VIEWSHEET, sent.getValue().sheetType());
      assertEquals(context, sent.getValue().editorContext());
      assertEquals("sock-1", sent.getValue().socketSessionId());
      assertEquals("alice-dest", sent.getValue().socketUserName());
   }

   // ---------------------------------------------------------------------------
   // toolbarJoinNotifiesWithNoEditorContext
   // ---------------------------------------------------------------------------
   @Test
   void toolbarJoinNotifiesWithNoEditorContext() throws PairingException {
      when(feature.isEnabled()).thenReturn(true);
      String code = pairing.mint("Worksheet/foo-7", ALICE_KEY, "sock-1", "alice-dest",
                                 SheetType.WORKSHEET, null);
      Principal alice = TestPrincipals.user("alice", "host-org");

      svc.join(code, alice);

      ArgumentCaptor<JoinSession> sent = ArgumentCaptor.forClass(JoinSession.class);
      verify(broadcast).sendPairingJoined(sent.capture());
      assertNull(sent.getValue().editorContext());
   }

   // ---------------------------------------------------------------------------
   // notifyFailureDoesNotFailTheJoin
   //
   // The session is already open and valid by the time the notice is sent, so a broken
   // notification must cost the indicator and nothing else.
   // ---------------------------------------------------------------------------
   @Test
   void notifyFailureDoesNotFailTheJoin() throws PairingException {
      when(feature.isEnabled()).thenReturn(true);
      doThrow(new RuntimeException("socket gone")).when(broadcast).sendPairingJoined(any());
      String code = pairing.mint("vs-9", ALICE_KEY, "sock-1", "alice-dest",
                                 SheetType.VIEWSHEET, null);
      Principal alice = TestPrincipals.user("alice", "host-org");

      JoinSession session = svc.join(code, alice);

      assertNotNull(session);
      assertEquals("vs-9", session.runtimeId());
   }
```

Add `any` to the Mockito static imports:

```java
import static org.mockito.ArgumentMatchers.any;
```

Also extend the class javadoc's case index, matching the existing style:

```java
 * [notifiesBrowser] a successful join pushes a PairingJoinedNotice to the minting browser
 * [toolbarNotice]   a whole-sheet grant notifies with a null editorContext
 * [notifyFailure]   a throwing broadcast does not fail the join
```

- [ ] **Step 2: Run the tests to verify they fail**

Run from the `community` directory:

```bash
mvn test -pl core -Dtest=SheetJoinServiceTest
```

Expected: compilation failure — `SheetJoinService` has no 5-argument constructor and `SheetAgentBroadcastService` has no `sendPairingJoined`.

- [ ] **Step 3: Create the notice record**

`core/src/main/java/inetsoft/web/wiz/pairing/PairingJoinedNotice.java` — copy the AGPL header from `JoinSession.java` verbatim, then:

```java
package inetsoft.web.wiz.pairing;

/**
 * Tells the browser that minted a pairing code that an agent has redeemed it.
 *
 * <p>Pairing is split across two processes: the browser mints the code, and the agent joins over
 * HTTP. Nothing else closes that loop, so without this notice the Composer cannot know the code it
 * displayed was ever used, and the only evidence pairing worked is the agent saying so.
 *
 * <p>Carries exactly the three fields the client needs to decide whether a notice is addressed to
 * it. The destination is per-user, not per-sheet and not per-pane, while
 * {@code ConnectToClaudeComponent} is reused by the composer toolbar, the viewsheet script pane and
 * the formula editor dialog — several instances can be alive at once on one page. Both
 * {@code runtimeId} and {@code editorContext} are therefore load-bearing filters, not diagnostics.
 *
 * @param editorContext the paired location, or {@code null} for a whole-sheet ("Connect to Claude"
 *                      toolbar) pairing. {@code null} is the normal case, never an error.
 */
public record PairingJoinedNotice(String runtimeId, SheetType sheetType,
                                  EditorContext editorContext) {
}
```

- [ ] **Step 4: Add the push to SheetAgentBroadcastService**

In `SheetAgentBroadcastService.java`, add the constant next to the class's other fields:

```java
   /**
    * Where a paired browser learns that an agent joined. The client subscribes to this prefixed
    * with {@code /user}, exactly as it does for mint's {@code @SendToUser} destination.
    *
    * <p>Deliberately neither {@code CommandDispatcher.COMMANDS_TOPIC} nor
    * {@code ComposerClientService.COMMANDS_TOPIC} — see {@link #sendToComposer}'s javadoc for why
    * confusing those two is a silent failure. This is a pairing-domain destination with its own
    * client handler.
    */
   static final String PAIRING_JOINED_TOPIC = "/commands/wiz/pairing/joined";
```

Add the method after `sendToComposer`:

```java
   /**
    * Tell the browser that minted the code that an agent has now joined with it.
    *
    * <p>Addresses {@code socketUserName} — the destination user resolved at mint time, the same
    * value mint's {@code @SendToUser} resolved to — and sets the session id header as the other
    * senders here do.
    *
    * <p>Callers must treat this as best-effort. By the time it runs the agent's session is open and
    * valid, so a failure here costs the browser's indicator and nothing more.
    */
   public void sendPairingJoined(JoinSession session) {
      SimpMessageHeaderAccessor headers =
         SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
      headers.setSessionId(session.socketSessionId());
      headers.setLeaveMutable(true);

      commandDispatcherService.convertAndSendToUser(
         session.socketUserName(), PAIRING_JOINED_TOPIC,
         new PairingJoinedNotice(session.runtimeId(), session.sheetType(),
                                 session.editorContext()),
         headers.getMessageHeaders());
   }
```

- [ ] **Step 5: Call it from join()**

In `SheetJoinService.java`, add the field and constructor parameter:

```java
   private final SheetAgentBroadcastService broadcast;
```

```java
   @Autowired
   public SheetJoinService(SheetPairingService pairing,
                           SheetSessionService sessions,
                           SheetAgentFeature feature,
                           SheetRuntimeAccess runtimeAccess,
                           SheetAgentBroadcastService broadcast) {
      this.pairing = pairing;
      this.sessions = sessions;
      this.feature = feature;
      this.runtimeAccess = runtimeAccess;
      this.broadcast = broadcast;
   }
```

In `join()`, between `failedAttempts.remove(throttleKey);` and the existing `LOG.info(...)`:

```java
      // Close the loop back to the browser that minted this code — it has no other way to learn
      // the code was redeemed, since the join arrived over HTTP from the agent. Best-effort by
      // design: the session above is already open and valid, so a lost notice degrades the
      // Composer's indicator and must never turn a successful pairing into a failed one.
      try {
         broadcast.sendPairingJoined(session);
      }
      catch(Exception ex) {
         LOG.warn("Pairing joined, but notifying the browser failed (runtimeId={})",
                  grant.runtimeId(), ex);
      }
```

- [ ] **Step 6: Run the tests to verify they pass**

```bash
mvn test -pl core -Dtest=SheetJoinServiceTest
```

Expected: PASS, all cases including the three new ones.

- [ ] **Step 7: Run the neighbouring suites for regressions**

The constructor changed, so anything building a `SheetJoinService` is affected.

```bash
mvn test -pl core -Dtest='Sheet*Test,Pairing*Test'
```

Expected: PASS. If another test constructs `SheetJoinService` directly, add a mock `SheetAgentBroadcastService` there the same way.

- [ ] **Step 8: Commit**

```bash
git add core/src/main/java/inetsoft/web/wiz/pairing/PairingJoinedNotice.java \
        core/src/main/java/inetsoft/web/wiz/pairing/SheetAgentBroadcastService.java \
        core/src/main/java/inetsoft/web/wiz/pairing/SheetJoinService.java \
        core/src/test/java/inetsoft/web/wiz/pairing/SheetJoinServiceTest.java
git commit -m "feat(pairing): notify the minting browser when an agent joins"
```

---

## Task 2: Composer shows that an agent connected

**Files:**
- Modify: `web/projects/portal/src/app/composer/gui/wiz/connect-to-claude.component.ts`
- Modify: `web/projects/portal/src/app/composer/gui/wiz/connect-to-claude.component.html`
- Test: `web/projects/portal/src/app/composer/gui/wiz/connect-to-claude.component.spec.ts`

**Interfaces:**
- Consumes: notices on `/user/commands/wiz/pairing/joined` shaped `{runtimeId, sheetType, editorContext}` from Task 1; the existing `EditorContext { kind: string; assembly?: string; name?: string; table?: string }`.
- Produces: `ConnectToClaudeComponent.connected: boolean`, true only for a notice matching this instance.

- [ ] **Step 1: Write the failing tests**

Append to `connect-to-claude.component.spec.ts`, inside the top-level `describe`:

```ts
   describe("connected indicator", () => {
      /** Captures the handler for a given destination, since the component now holds two. */
      function handlerFor(dest: string): (msg: any) => void {
         const call = mockStompConnection.subscribe.mock.calls
            .filter((c: any[]) => c[0] === dest).pop();
         expect(call).toBeTruthy();
         return call[1];
      }

      function notice(body: any): any {
         return { frame: { body: JSON.stringify(body) } };
      }

      it("subscribes to the joined destination on init", () => {
         expect(mockStompConnection.subscribe).toHaveBeenCalledWith(
            "/user/commands/wiz/pairing/joined",
            expect.any(Function)
         );
      });

      it("shows connected and drops the consumed code for a matching notice", () => {
         component.code = "ABC123";

         handlerFor("/user/commands/wiz/pairing/joined")(
            notice({ runtimeId: "rt-1", sheetType: "WORKSHEET", editorContext: null }));
         fixture.detectChanges();

         expect(component.connected).toBe(true);
         expect(component.code).toBeNull();
         expect(fixture.nativeElement.querySelector(".wiz-connect-connected")).toBeTruthy();
         expect(fixture.nativeElement.querySelector(".wiz-connect-code")).toBeNull();
      });

      it("ignores a notice for another runtime", () => {
         handlerFor("/user/commands/wiz/pairing/joined")(
            notice({ runtimeId: "rt-other", sheetType: "WORKSHEET", editorContext: null }));

         expect(component.connected).toBe(false);
      });

      /*
       * The failure this filter exists for: the destination is per-user, so a pane pairing is
       * delivered to the toolbar instance too. Without the editorContext check the toolbar would
       * claim an agent joined it, which is a false statement rather than a missing feature.
       */
      it("ignores a pane notice on a toolbar instance", () => {
         handlerFor("/user/commands/wiz/pairing/joined")(
            notice({ runtimeId: "rt-1", sheetType: "WORKSHEET",
                     editorContext: { kind: "calcField", assembly: "Query1", name: "Margin" } }));

         expect(component.connected).toBe(false);
      });

      /*
       * Jackson serializes a Java record's absent components as explicit nulls, while the browser
       * never sent those keys at all. Comparing them naively (JSON.stringify, or === on each key)
       * makes every pane notice a mismatch and the indicator never appears.
       */
      it("matches a pane notice whose absent fields arrive as explicit nulls", () => {
         let handler: ((msg: any) => void) | null = null;
         mockStompConnection.subscribe.mockImplementation(
            (dest: string, h: (msg: any) => void) => {
               if(dest === "/user/commands/wiz/pairing/mint") { handler = h; }
               return { unsubscribe: vi.fn() };
            });
         component.editorContext = { kind: "assemblyMain", assembly: "Chart1" };
         component.requestCode();
         handler!(notice({ code: "ABC123" }));

         handlerFor("/user/commands/wiz/pairing/joined")(
            notice({ runtimeId: "rt-1", sheetType: "WORKSHEET",
                     editorContext: { kind: "assemblyMain", assembly: "Chart1",
                                      name: null, table: null } }));

         expect(component.connected).toBe(true);
      });

      it("clears the indicator when the runtimeId changes", () => {
         handlerFor("/user/commands/wiz/pairing/joined")(
            notice({ runtimeId: "rt-1", sheetType: "WORKSHEET", editorContext: null }));
         expect(component.connected).toBe(true);

         component.runtimeId = "rt-2";
         component.ngOnChanges(
            { runtimeId: { currentValue: "rt-2", previousValue: "rt-1",
                           firstChange: false, isFirstChange: () => false } } as any);

         expect(component.connected).toBe(false);
      });

      it("releases the joined subscription on destroy", () => {
         const subs: Array<{ unsubscribe: ReturnType<typeof vi.fn> }> = [];
         mockStompConnection.subscribe.mockImplementation(() => {
            const s = { unsubscribe: vi.fn() };
            subs.push(s);
            return s;
         });

         const f = TestBed.createComponent(ConnectToClaudeComponent);
         f.componentInstance.runtimeId = "rt-1";
         f.componentInstance.sheetType = "WORKSHEET";
         f.componentInstance.socketConnection = mockSocketConnection;
         f.detectChanges();
         f.destroy();

         expect(subs.some(s => s.unsubscribe.mock.calls.length > 0)).toBe(true);
      });
   });
```

- [ ] **Step 2: Run the tests to verify they fail**

Run from the `community/web` directory:

```bash
npm run test:portal
```

Expected: FAIL — `component.connected` is undefined and nothing subscribes to the joined destination.

- [ ] **Step 3: Implement the component changes**

In `connect-to-claude.component.ts`, change the class declaration and add `OnInit` to the imports:

```ts
import { Component, Input, NgZone, OnChanges, OnDestroy, OnInit, SimpleChanges } from "@angular/core";
```

```ts
export class ConnectToClaudeComponent implements OnInit, OnChanges, OnDestroy {
```

Add the state and subscription field next to the existing ones:

```ts
   /**
    * An agent has redeemed a code minted from this exact component instance.
    *
    * One-shot by design: this says a pairing happened, not that one is still live. Tracking
    * liveness would mean reacting to a TTL that expires with no server-side event, which the
    * design spec records as deliberately out of scope.
    */
   connected = false;

   private joinedSubscription: Subscription | null = null;
```

Add `ngOnInit`:

```ts
   /**
    * Opens the standing subscription for join notices.
    *
    * Standing, not per-mint: the agent may redeem the code seconds or minutes after it is
    * displayed, long after the mint round-trip has completed and unsubscribed itself.
    */
   ngOnInit(): void {
      this.socketConnection.whenConnected().pipe(take(1)).subscribe(
         (conn: StompClientConnection) => {
            this.joinedSubscription = conn.subscribe(
               "/user/commands/wiz/pairing/joined", (msg: any) => this.onJoined(msg));
         });
   }
```

Add the handler and the comparison, after `requestCode()`:

```ts
   /**
    * Accepts a join notice only when it is for this sheet AND this location.
    *
    * The destination is per-user, so every instance on the page receives every notice. This
    * component is reused by the composer toolbar, the viewsheet script pane and the formula editor
    * dialog, so more than one instance is routinely alive at once. Dropping either filter makes an
    * instance announce a pairing that did not happen to it — a false statement, which is worse
    * than the missing indicator this whole change exists to fix.
    */
   private onJoined(msg: any): void {
      let body: any;

      try {
         body = JSON.parse(msg.frame.body);
      }
      catch(e) {
         return;
      }

      if(body.runtimeId !== this.runtimeId ||
         !this.sameEditorContext(body.editorContext, this.mintedEditorContext))
      {
         return;
      }

      this.zone.run(() => {
         this.connected = true;
         // The code is single-use and has now been used. Leaving it on screen invites an attempt
         // to redeem it again.
         this.code = null;
         this.error = null;
      });
   }

   /**
    * Compares two editor contexts, treating an absent field and an explicit null as equal.
    *
    * Required, not defensive: the server sends a Java record, and Jackson writes its unset
    * components as explicit nulls, while the browser omits those keys from the mint payload
    * entirely. A structural comparison (JSON.stringify, or === per key) would therefore never
    * match a pane pairing, and the indicator would never appear for one.
    */
   private sameEditorContext(a: EditorContext | null | undefined,
                             b: EditorContext | null | undefined): boolean {
      if(!a && !b) {
         return true;
      }

      if(!a || !b) {
         return false;
      }

      return a.kind === b.kind &&
         (a.assembly ?? null) === (b.assembly ?? null) &&
         (a.name ?? null) === (b.name ?? null) &&
         (a.table ?? null) === (b.table ?? null);
   }
```

In `ngOnChanges`, reset the flag alongside the existing resets (add one line inside the existing `if` block):

```ts
            this.connected = false;
```

Do **not** tear down `joinedSubscription` there — the destination is per-user, not per-runtime, so only the filter input changed, never the channel.

In `ngOnDestroy`, release it:

```ts
   ngOnDestroy(): void {
      if(this.mintSubscription) {
         this.mintSubscription.unsubscribe();
         this.mintSubscription = null;
      }

      // The formula editor dialog creates and destroys this component repeatedly; a standing
      // subscription that outlives its component is a leak.
      if(this.joinedSubscription) {
         this.joinedSubscription.unsubscribe();
         this.joinedSubscription = null;
      }
   }
```

- [ ] **Step 4: Update the template**

Replace the code block in `connect-to-claude.component.html` so the connected line takes its place:

```html
<div class="wiz-connect-to-claude">
  <button (click)="requestCode()" [disabled]="loading" class="btn btn-primary btn-sm">
    {{ loading ? 'Generating...' : 'Connect to Claude' }}
  </button>

  <div *ngIf="error" class="wiz-connect-error alert alert-danger mt-2">{{ error }}</div>

  <div *ngIf="connected" class="wiz-connect-connected alert alert-success mt-2">
    ✓ Agent connected
  </div>

  <div *ngIf="code && !connected" class="wiz-connect-code mt-2">
    <span class="wiz-connect-label">Pairing Code:</span>
    <code class="wiz-connect-value ms-2">{{ code }}</code>
    <button ngxClipboard [cbContent]="code" (cbOnSuccess)="onCopySuccess()" (cbOnError)="onCopyError()"
      class="btn btn-sm btn-outline-secondary ms-2">
      {{ copied ? 'Copied!' : 'Copy' }}
    </button>
  </div>
</div>
```

- [ ] **Step 5: Run the tests to verify they pass**

```bash
npm run test:portal
```

Expected: PASS, including every pre-existing case in this spec file.

- [ ] **Step 6: Commit**

```bash
git add web/projects/portal/src/app/composer/gui/wiz/connect-to-claude.component.ts \
        web/projects/portal/src/app/composer/gui/wiz/connect-to-claude.component.html \
        web/projects/portal/src/app/composer/gui/wiz/connect-to-claude.component.spec.ts
git commit -m "feat(pairing): show in the Composer that an agent connected"
```

---

## Task 3: Verify live and close test-plan case 0.11

Mandatory, not optional. A wrong STOMP destination is dropped silently while every call reports success, so Tasks 1 and 2 passing their unit tests does **not** establish that this works.

**Files:**
- Modify (in the `stylebi-wiz` repo): `docs/superpowers/plans/2026-08-17-consolidated-composer-plugin-test-plan.md`

- [ ] **Step 1: Build and start StyleBI with both changes**

```bash
mvn -pl core install -DskipTests
```

Then start the server as usual and confirm the feature flag is on: `wiz.agent.pairing.enabled=true`.

- [ ] **Step 2: Toolbar pairing shows connected**

1. Open a viewsheet in the Composer.
2. Click **Connect to Claude**; note the code.
3. Redeem it from a Claude Code session: `connect_sheet` with that code.
4. Expected: the button area now shows **✓ Agent connected**, and the pairing code is gone.

If nothing appears, grep the server log for **both** of these before concluding anything:

- `Pairing joined, but notifying the browser failed` — the push itself threw; the stack trace names why.
- `session not found, message dropped` — `CommandDispatcherService.convertAndSendToUser` logs this and **returns without throwing** when the STOMP session is no longer registered, which a browser reconnect between mint and join produces. The destination is fine in this case; re-pair and retry.

Only when **neither** line appears does suspicion fall on the destination string — then re-check `PAIRING_JOINED_TOPIC` against the client's subscription, which is the silent-failure mode the spec warns about. Checking for the first line alone gives a false "the destination must be wrong" on every reconnect.

- [ ] **Step 3: A pane pairing must not light the toolbar**

1. On the same viewsheet, open a calculated field's formula editor and click **Connect Agent** there.
2. Redeem that code with `connect_sheet`.
3. Expected: the **formula editor's** indicator shows connected; the **toolbar** instance does not change.

This is the case the `editorContext` filter exists for; a failure here means the filter is not doing its job even though every unit test passed.

- [ ] **Step 4: Record the result in the test plan**

In the `stylebi-wiz` repo, update case 0.11 in the Human table and the L0 Status Board Notes: change `FAIL — the indication does not exist` to PASS with the date, and drop 0.11 from the "Still outstanding for L0" list.

Note that another session may be editing that document concurrently — make a narrow, exact edit rather than replacing whole sections.

- [ ] **Step 5: Commit and open the PR**

The spec and this plan are already committed on `composer-plugin-l0-pairing-indicator`, so this step
only pushes and opens the PR:

```bash
git push -u origin composer-plugin-l0-pairing-indicator
gh pr create --title "feat(pairing): show in the Composer that an agent connected" --body-file <path to a body describing the three tasks and the live verification result>
```

The `stylebi-wiz` test-plan edit is a separate repo and belongs in its own commit there.

---

## Self-Review

**Spec coverage.** Backend push (Task 1 steps 3–5), the `socketUserName` destination and header shape (Task 1 step 4), best-effort push (Task 1 step 5 plus its test), the `PairingJoinedNotice` field list (Task 1 step 3), standing subscription with `ngOnInit` (Task 2 step 3), both filters (Task 2 step 3, tested in step 1), no timeout and reset on `runtimeId` change (Task 2 step 3), `ngOnDestroy` release (Task 2 step 3), template swap (Task 2 step 4), the two live checks the spec marks as required (Task 3 steps 2–3). The spec's out-of-scope list is carried into Global Constraints so no task drifts into it.

**Type consistency.** `PairingJoinedNotice(runtimeId, sheetType, editorContext)` is produced in Task 1 step 3 and consumed by the exact same three keys in Task 2's tests and handler. `sendPairingJoined(JoinSession)` is defined in Task 1 step 4 and called with a `JoinSession` in step 5, and the test verifies it with `ArgumentCaptor<JoinSession>`. `connected` is the same name in the component, the template and every test.

**One gap deliberately left to the implementer:** Task 1 step 7 says to add a mock if another test constructs `SheetJoinService` directly, without naming which — `SheetPairingControllerTest` is the likely one, but that depends on whether it builds the service or mocks it, and the grep in step 7 finds the truth faster than a guess here would.
