/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.web.wiz.pairing;

import inetsoft.web.wiz.script.PaneScopeService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SheetSessionService.
 *
 * [Open]             open returns a non-null session with a token
 * [Resolve: reuse]   resolve after open returns the same session (not single-use)
 * [Resolve: wrong]   resolve rejects a different ownerIdentity
 * [Resolve: unknown] resolve on unknown token returns null
 * [Resolve: expired] resolve returns null for expired session
 * [Resolve: refresh] resolve refreshes TTL (lastAccess advances)
 * [Close]            close invalidates the token
 * [Socket close: pane]    socketClosed expires a pane-scoped session on that socket
 * [Socket close: toolbar] socketClosed leaves a whole-sheet session's TTL behaviour alone
 * [Detach]                detach ends the one session matching socket + editorContext
 *
 * Follow Focus (retarget/popFocus/setFollowFocus):
 * [Follow focus: default off]     a freshly opened session has followFocusEnabled == false
 * [Follow focus: toggle]          setFollowFocus flips the flag; resolve reflects it and does
 *                                 not reset it back to false on its own TTL-refresh reconstruct
 * [Retarget: opt-in required]     retarget refuses a session that never opted in, named, and
 *                                 leaves the session untouched
 * [Retarget: happy path]          retarget on an opted-in session moves editorContext, visible
 *                                 through a fresh resolve(); popFocus recovers the prior value
 * [Retarget: nesting]             two sequential retargets push twice; two pops restore in
 *                                 reverse order, ending at the session's original editorContext
 * [Retarget: unknown session]     retarget naming a (socketSessionId, runtimeId) pair with no
 *                                 matching live session refuses, named, and touches nothing
 * [Retarget: invalid target]      retarget with an editorContext validateEditorContext rejects
 *                                 refuses before mutating anything
 * [PopFocus: empty stack]         popFocus past the bottom of an empty stack is a no-op, not an
 *                                 error, leaving the session at its original scope
 * [PopFocus: null on no-op]       popFocus returns null (not the unchanged session) both when
 *                                 nothing was ever pushed and once a real push has been drained --
 *                                 the caller has one signal for "nothing to broadcast"
 * [FocusStacks cleanup: close]        close() removes the matching focusStacks entry, not just
 *                                     the session, or a retargeted session leaks one forever
 * [FocusStacks cleanup: socketClosed] socketClosed() does the same for a pane-scoped session
 * [FocusStacks cleanup: evictExpired] the scheduled sweep does the same for an expired session
 * [Exit criterion]                after a successful retarget, PaneScopeService.requireWholeSheetSession
 *                                 against a FRESH resolve() of the same token reflects the new
 *                                 pane-scoped target -- proving enforcement needed no code change
 */
@Tag("core")
class SheetSessionServiceTest {

   private static final long FIXED_NOW = 2_000_000L;

   private SheetSessionService serviceAt(long now) {
      return new SheetSessionService(() -> now);
   }

   @Test
   void openReturnsSessionWithToken() {
      SheetSessionService svc = serviceAt(FIXED_NOW);
      JoinSession session = svc.open("rt-1", "alice~;~org", SheetType.WORKSHEET, null, null, null);
      assertNotNull(session);
      assertNotNull(session.sessionToken());
      assertFalse(session.sessionToken().isEmpty());
   }

   @Test
   void resolveReturnsSessionMultipleTimes() {
      SheetSessionService svc = serviceAt(FIXED_NOW);
      JoinSession session = svc.open("rt-1", "alice~;~org", SheetType.WORKSHEET, null, null, null);
      String token = session.sessionToken();

      JoinSession r1 = svc.resolve(token, "alice~;~org");
      JoinSession r2 = svc.resolve(token, "alice~;~org");
      assertNotNull(r1);
      assertNotNull(r2, "session must be reusable (not single-use)");
      assertEquals(token, r1.sessionToken());
   }

   @Test
   void resolveRejectsWrongUser() {
      SheetSessionService svc = serviceAt(FIXED_NOW);
      JoinSession session = svc.open("rt-2", "alice~;~org", SheetType.WORKSHEET, null, null, null);
      assertNull(svc.resolve(session.sessionToken(), "mallory~;~org"));
   }

   @Test
   void resolveUnknownTokenReturnsNull() {
      SheetSessionService svc = serviceAt(FIXED_NOW);
      assertNull(svc.resolve("NONEXISTENT_TOKEN", "alice~;~org"));
   }

   @Test
   void resolveExpiredSessionReturnsNull() {
      SheetSessionService svc = serviceAt(FIXED_NOW);
      JoinSession session = svc.open("rt-3", "alice~;~org", SheetType.WORKSHEET, null, null, null);
      String token = session.sessionToken();

      // advance clock past TTL
      SheetSessionService svcLater = new SheetSessionService(
         () -> FIXED_NOW + SheetSessionService.TTL_MILLIS + 1, svc);
      assertNull(svcLater.resolve(token, "alice~;~org"));
   }

   /**
    * {@code isLive} is a pure presence+non-expiry check with NO owner match -- unlike
    * {@code resolve}, it exists for internal cleanup callers (e.g. a sibling service's own
    * scheduled sweep) that have no {@code Principal} to check against and no need for one.
    */
   @Test
   void isLiveIgnoresOwnerButRespectsPresenceAndExpiry() {
      SheetSessionService svc = serviceAt(FIXED_NOW);
      JoinSession session = svc.open("rt-live", "alice~;~org", SheetType.WORKSHEET, null, null, null);
      String token = session.sessionToken();

      assertTrue(svc.isLive(token), "a fresh session must be live");
      assertFalse(svc.isLive("NONEXISTENT_TOKEN"), "an unknown token is never live");

      SheetSessionService svcLater = new SheetSessionService(
         () -> FIXED_NOW + SheetSessionService.TTL_MILLIS + 1, svc);
      assertFalse(svcLater.isLive(token), "isLive must respect expiry just like resolve does");
   }

   @Test
   void resolveRefreshesTtl() {
      long[] clock = { FIXED_NOW };
      SheetSessionService svc = new SheetSessionService(() -> clock[0]);
      JoinSession session = svc.open("rt-4", "bob~;~org", SheetType.VIEWSHEET, null, null, null);
      String token = session.sessionToken();

      // advance to just before TTL
      clock[0] = FIXED_NOW + SheetSessionService.TTL_MILLIS - 100;
      JoinSession resolved = svc.resolve(token, "bob~;~org");
      assertNotNull(resolved);

      // advance beyond original TTL but within refreshed TTL
      clock[0] = FIXED_NOW + SheetSessionService.TTL_MILLIS + 500;
      JoinSession stillAlive = svc.resolve(token, "bob~;~org");
      assertNotNull(stillAlive, "TTL should have been refreshed on last resolve");
   }

   @Test
   void closeInvalidatesToken() {
      SheetSessionService svc = serviceAt(FIXED_NOW);
      JoinSession session = svc.open("rt-5", "carol~;~org", SheetType.WORKSHEET, null, null, null);
      String token = session.sessionToken();
      svc.close(token);
      assertNull(svc.resolve(token, "carol~;~org"));
   }

   @Test
   void findOpenReturnsTheMatchingSessionForOwnerAndType() {
      SheetSessionService svc = serviceAt(FIXED_NOW);
      JoinSession session = svc.open("rt-6", "dave~;~org", SheetType.WORKSHEET, null, null, null);

      JoinSession found = svc.findOpen("dave~;~org", SheetType.WORKSHEET);

      assertNotNull(found);
      assertEquals(session.sessionToken(), found.sessionToken());
   }

   /**
    * Pins the invariant PSM-006's fix (open_base_worksheet's {@code force} flag closing exactly
    * the one session {@code findOpen} returned) relies on: {@code open()} never consults
    * {@code findOpen} itself, so two pane-scoped sessions of the same (ownerIdentity, sheetType)
    * -- e.g. a whole-sheet toolbar pairing plus a script-pane pairing on the same viewsheet -- may
    * legitimately coexist. A fix that made {@code open()} evict whatever {@code findOpen} returns
    * before minting a new session would silently kill a live, unrelated pane session the first
    * time a second one is paired; this test would then fail because only one of the two tokens
    * would still resolve.
    */
   @Test
   void openTwiceForTheSameOwnerAndTypeLeavesBothSessionsLiveAndIndependentlyReachable() {
      SheetSessionService svc = serviceAt(FIXED_NOW);
      JoinSession first = svc.open("rt-7a", "erin~;~org", SheetType.VIEWSHEET, "sock-1", "erin", null);
      JoinSession second = svc.open(
         "rt-7b", "erin~;~org", SheetType.VIEWSHEET, "sock-1", "erin",
         new EditorContext("assemblyMain", "Chart1", null, null));

      assertNotEquals(first.sessionToken(), second.sessionToken());
      assertNotNull(svc.resolve(first.sessionToken(), "erin~;~org"),
                    "the first session must still resolve after the second was opened");
      assertNotNull(svc.resolve(second.sessionToken(), "erin~;~org"),
                    "the second session must resolve independently of the first");
   }

   @Test
   void expiresAPaneSessionWhenItsSocketGoesAway() {
      SheetSessionService svc = serviceAt(FIXED_NOW);
      EditorContext ctx = new EditorContext("assemblyMain", "Chart1", null, null);
      JoinSession pane = svc.open("vs-1", "owner~;~org", SheetType.VIEWSHEET, "sock-1", "owner", ctx);

      svc.socketClosed("sock-1");

      assertNull(svc.resolve(pane.sessionToken(), "owner~;~org"));
   }

   /**
    * The regression guard: whole-sheet ("Connect to Claude" toolbar) sessions must NOT
    * acquire editor-bound lifetime. Pane-scoping must never leak into them, or every existing
    * toolbar user loses their session the moment their socket blips.
    */
   @Test
   void aToolbarSessionKeepsTodaysTtlBehaviour() {
      SheetSessionService svc = serviceAt(FIXED_NOW);
      JoinSession sheet = svc.open("vs-1", "owner~;~org", SheetType.VIEWSHEET, "sock-1", "owner", null);

      svc.socketClosed("sock-1");

      assertNotNull(svc.resolve(sheet.sessionToken(), "owner~;~org"));
   }

   @Test
   void socketClosedIgnoresASocketWithNoSessionsOnIt() {
      SheetSessionService svc = serviceAt(FIXED_NOW);
      EditorContext ctx = new EditorContext("assemblyMain", "Chart1", null, null);
      JoinSession pane = svc.open("vs-1", "owner~;~org", SheetType.VIEWSHEET, "sock-1", "owner", ctx);

      svc.socketClosed("some-other-socket");

      assertNotNull(svc.resolve(pane.sessionToken(), "owner~;~org"),
                    "a different socket disconnecting must not touch this session");
   }

   @Test
   void detachEndsTheSessionMatchingBothSocketAndEditorContext() {
      SheetSessionService svc = serviceAt(FIXED_NOW);
      EditorContext ctx = new EditorContext("assemblyMain", "Chart1", null, null);
      JoinSession pane = svc.open("vs-1", "owner~;~org", SheetType.VIEWSHEET, "sock-1", "owner", ctx);

      svc.detach("sock-1", ctx);

      assertNull(svc.resolve(pane.sessionToken(), "owner~;~org"));
   }

   @Test
   void detachLeavesASiblingPaneOnTheSameSocketAlone() {
      SheetSessionService svc = serviceAt(FIXED_NOW);
      EditorContext closed = new EditorContext("assemblyMain", "Chart1", null, null);
      EditorContext stillOpen = new EditorContext("assemblyMain", "Chart2", null, null);
      JoinSession closedPane = svc.open("vs-1", "owner~;~org", SheetType.VIEWSHEET, "sock-1",
                                        "owner", closed);
      JoinSession openPane = svc.open("vs-1", "owner~;~org", SheetType.VIEWSHEET, "sock-1",
                                      "owner", stillOpen);

      svc.detach("sock-1", closed);

      assertNull(svc.resolve(closedPane.sessionToken(), "owner~;~org"));
      assertNotNull(svc.resolve(openPane.sessionToken(), "owner~;~org"),
                    "detaching one pane must not end a different pane's session on the same socket");
   }

   @Test
   void detachNeverReachesAWholeSheetSession() {
      SheetSessionService svc = serviceAt(FIXED_NOW);
      EditorContext ctx = new EditorContext("assemblyMain", "Chart1", null, null);
      JoinSession sheet = svc.open("vs-1", "owner~;~org", SheetType.VIEWSHEET, "sock-1", "owner", null);

      svc.detach("sock-1", ctx);

      assertNotNull(svc.resolve(sheet.sessionToken(), "owner~;~org"));
   }

   @Test
   void findOpenIgnoresWrongTypeWrongOwnerAndExpiredSessions() {
      SheetSessionService svc = serviceAt(FIXED_NOW);
      svc.open("rt-7", "erin~;~org", SheetType.VIEWSHEET, null, null, null);
      svc.open("rt-8", "frank~;~org", SheetType.WORKSHEET, null, null, null);

      assertNull(svc.findOpen("erin~;~org", SheetType.WORKSHEET),
                 "same owner but a viewsheet session should not satisfy a worksheet lookup");
      assertNull(svc.findOpen("frank~;~org", SheetType.VIEWSHEET),
                 "same owner but a worksheet session should not satisfy a viewsheet lookup");
      assertNull(svc.findOpen("nobody~;~org", SheetType.WORKSHEET));

      SheetSessionService svcLater = new SheetSessionService(
         () -> FIXED_NOW + SheetSessionService.TTL_MILLIS + 1, svc);
      assertNull(svcLater.findOpen("frank~;~org", SheetType.WORKSHEET),
                 "an expired session must not be reported as held");
   }

   // ---- Follow Focus: retarget / popFocus / setFollowFocus -----------------------------------

   @Test
   void freshlyOpenedSessionHasFollowFocusDisabledByDefault() {
      SheetSessionService svc = serviceAt(FIXED_NOW);
      JoinSession session = svc.open("vs-1", "owner~;~org", SheetType.VIEWSHEET, "sock-1",
                                     "owner", null);
      assertFalse(session.followFocusEnabled());
   }

   @Test
   void setFollowFocusFlipsTheFlagAndSurvivesAResolveRefresh() {
      SheetSessionService svc = serviceAt(FIXED_NOW);
      JoinSession session = svc.open("vs-1", "owner~;~org", SheetType.VIEWSHEET, "sock-1",
                                     "owner", null);

      JoinSession updated = svc.setFollowFocus("sock-1", "vs-1", true);
      assertNotNull(updated);
      assertTrue(updated.followFocusEnabled());

      // resolve() reconstructs the session to refresh lastAccess -- it must carry
      // followFocusEnabled through, not silently reset it to the record's default.
      JoinSession resolved = svc.resolve(session.sessionToken(), "owner~;~org");
      assertTrue(resolved.followFocusEnabled(),
                 "resolve()'s TTL-refresh reconstruct must not un-opt-in a session");
   }

   @Test
   void retargetRefusesASessionThatNeverOptedIn() {
      SheetSessionService svc = serviceAt(FIXED_NOW);
      JoinSession session = svc.open("vs-1", "owner~;~org", SheetType.VIEWSHEET, "sock-1",
                                     "owner", null);
      EditorContext pane = new EditorContext("viewsheetOnInit", null, null, null);

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.retarget("sock-1", "vs-1", pane));
      assertNotNull(ex.getMessage());
      assertTrue(ex.getMessage().toLowerCase().contains("follow focus"),
                "refusal must name the real reason (Follow Focus not enabled), not a generic error");

      JoinSession stillUnchanged = svc.resolve(session.sessionToken(), "owner~;~org");
      assertNull(stillUnchanged.editorContext(), "a refused retarget must leave the session untouched");
      assertFalse(stillUnchanged.followFocusEnabled());
   }

   @Test
   void retargetMovesAnOptedInSessionsTargetAndPopFocusRecoversThePrior() throws PairingException {
      SheetSessionService svc = serviceAt(FIXED_NOW);
      JoinSession session = svc.open("vs-1", "owner~;~org", SheetType.VIEWSHEET, "sock-1",
                                     "owner", null);
      svc.setFollowFocus("sock-1", "vs-1", true);
      EditorContext pane = new EditorContext("viewsheetOnInit", null, null, null);

      svc.retarget("sock-1", "vs-1", pane);

      JoinSession afterRetarget = svc.resolve(session.sessionToken(), "owner~;~org");
      assertEquals(pane, afterRetarget.editorContext());

      svc.popFocus("sock-1", "vs-1");

      JoinSession afterPop = svc.resolve(session.sessionToken(), "owner~;~org");
      assertNull(afterPop.editorContext(), "popFocus must restore the session's original scope");
   }

   @Test
   void twoSequentialRetargetsNestAndTwoPopsUnwindInReverseOrder() throws PairingException {
      SheetSessionService svc = serviceAt(FIXED_NOW);
      JoinSession session = svc.open("vs-1", "owner~;~org", SheetType.VIEWSHEET, "sock-1",
                                     "owner", null);
      svc.setFollowFocus("sock-1", "vs-1", true);
      EditorContext outer = new EditorContext("viewsheetOnInit", null, null, null);
      EditorContext inner = new EditorContext("viewsheetOnLoad", null, null, null);

      svc.retarget("sock-1", "vs-1", outer);
      svc.retarget("sock-1", "vs-1", inner);

      JoinSession atInner = svc.resolve(session.sessionToken(), "owner~;~org");
      assertEquals(inner, atInner.editorContext());

      svc.popFocus("sock-1", "vs-1");
      JoinSession backToOuter = svc.resolve(session.sessionToken(), "owner~;~org");
      assertEquals(outer, backToOuter.editorContext(),
                  "first pop must restore the ENCLOSING target, not jump straight to whole-sheet");

      svc.popFocus("sock-1", "vs-1");
      JoinSession backToOriginal = svc.resolve(session.sessionToken(), "owner~;~org");
      assertNull(backToOriginal.editorContext(),
                "second pop must restore the session's original (whole-sheet) scope");
   }

   @Test
   void retargetWithNoMatchingSessionRefusesAndTouchesNothing() {
      SheetSessionService svc = serviceAt(FIXED_NOW);
      svc.open("vs-1", "owner~;~org", SheetType.VIEWSHEET, "sock-1", "owner", null);
      EditorContext pane = new EditorContext("viewsheetOnInit", null, null, null);

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.retarget("wrong-socket", "vs-1", pane));
      assertNotNull(ex.getMessage());
   }

   @Test
   void retargetRefusesAnEditorContextTheRuntimeDoesNotHave() {
      // The back-compat SheetPairingService validates against no configured runtime accessor --
      // any editorContext naming a non-blank assembly therefore fails validateEditorContext's
      // "does the runtime actually have this" check, exactly like an unconfigured mint would.
      SheetSessionService svc = serviceAt(FIXED_NOW);
      JoinSession session = svc.open("vs-1", "owner~;~org", SheetType.VIEWSHEET, "sock-1",
                                     "owner", null);
      svc.setFollowFocus("sock-1", "vs-1", true);
      EditorContext ghost = new EditorContext("assemblyMain", "GhostChart", null, null);

      assertThrows(PairingException.class, () -> svc.retarget("sock-1", "vs-1", ghost));

      JoinSession stillUnchanged = svc.resolve(session.sessionToken(), "owner~;~org");
      assertNull(stillUnchanged.editorContext(),
                "a retarget refused by validateEditorContext must leave the session untouched");
   }

   @Test
   void popFocusPastAnEmptyStackIsANoOpAtOriginalScope() {
      SheetSessionService svc = serviceAt(FIXED_NOW);
      JoinSession session = svc.open("vs-1", "owner~;~org", SheetType.VIEWSHEET, "sock-1",
                                     "owner", null);

      svc.popFocus("sock-1", "vs-1");

      JoinSession stillOriginal = svc.resolve(session.sessionToken(), "owner~;~org");
      assertNull(stillOriginal.editorContext());
      assertFalse(stillOriginal.followFocusEnabled(), "popFocus must not itself flip followFocusEnabled");
   }

   /**
    * A session found but with nothing left to pop must return null, not the unchanged session --
    * SheetPairingController.popFocusViaSocket broadcasts a focusChanged notice on any non-null
    * return, so returning the unchanged session here (an earlier version of this method did)
    * fires a spurious broadcast for every no-op pop, e.g. a pane that closes without ever having
    * been retargeted.
    */
   @Test
   void popFocusOnAnExhaustedStackReturnsNullNotTheUnchangedSession() {
      SheetSessionService svc = serviceAt(FIXED_NOW);
      svc.open("vs-1", "owner~;~org", SheetType.VIEWSHEET, "sock-1", "owner", null);

      assertNull(svc.popFocus("sock-1", "vs-1"),
                "a session with an empty/never-pushed stack must pop to null, exactly like " +
                "'no session found', so the caller has one signal for 'nothing to broadcast'");
   }

   /**
    * Same exhausted-stack case, but after the stack genuinely HAD an entry and it was already
    * consumed -- distinct from "never pushed at all" above, and the case the original defect
    * report singled out as uncovered.
    */
   @Test
   void popFocusReturnsNullOnceTheStackIsDrainedEvenAfterARealPush() throws PairingException {
      SheetSessionService svc = serviceAt(FIXED_NOW);
      svc.open("vs-1", "owner~;~org", SheetType.VIEWSHEET, "sock-1", "owner", null);
      svc.setFollowFocus("sock-1", "vs-1", true);
      svc.retarget("sock-1", "vs-1", new EditorContext("viewsheetOnInit", null, null, null));

      assertNotNull(svc.popFocus("sock-1", "vs-1"), "the first pop drains a real pushed frame");
      assertNull(svc.popFocus("sock-1", "vs-1"),
                "the second pop, on the now-exhausted stack, must return null rather than the " +
                "unchanged session");
   }

   /**
    * SheetSessionService.focusStacks is keyed by sessionToken, and tokens are never reused --
    * so every removal path that forgets to clear the matching focusStacks entry leaks it for
    * the remaining life of the process. Covers close() directly; socketClosed/detach/
    * evictExpired all route through the same private removeSessionsIf helper this exercises.
    */
   @Test
   void closeRemovesTheFocusStacksEntryTooRatherThanLeakingIt() throws PairingException {
      SheetSessionService svc = serviceAt(FIXED_NOW);
      JoinSession session = svc.open("vs-1", "owner~;~org", SheetType.VIEWSHEET, "sock-1",
                                     "owner", null);
      svc.setFollowFocus("sock-1", "vs-1", true);
      svc.retarget("sock-1", "vs-1", new EditorContext("viewsheetOnInit", null, null, null));

      assertTrue(svc.hasFocusStackEntryForTesting(session.sessionToken()),
                "retarget should have pushed a focus-stack entry to observe cleanup against");

      svc.close(session.sessionToken());

      assertFalse(svc.hasFocusStackEntryForTesting(session.sessionToken()),
                 "close must remove the focusStacks entry too, or every retargeted session " +
                 "leaks one for the remaining life of the process");
   }

   /**
    * The Follow Focus-specific leak path the defect report called out by name: pane-scoped
    * sessions churn constantly as panes open/close, and socketClosed (not close()) is what
    * actually ends one when its browser socket drops.
    */
   @Test
   void socketClosedRemovesTheFocusStacksEntryForAPaneScopedSessionToo() throws PairingException {
      SheetSessionService svc = serviceAt(FIXED_NOW);
      EditorContext pane = new EditorContext("viewsheetOnInit", null, null, null);
      JoinSession session = svc.open("vs-1", "owner~;~org", SheetType.VIEWSHEET, "sock-1",
                                     "owner", pane);
      svc.setFollowFocus("sock-1", "vs-1", true);
      svc.retarget("sock-1", "vs-1", new EditorContext("viewsheetOnLoad", null, null, null));

      assertTrue(svc.hasFocusStackEntryForTesting(session.sessionToken()));

      svc.socketClosed("sock-1");

      assertFalse(svc.hasFocusStackEntryForTesting(session.sessionToken()),
                 "socketClosed must remove the focusStacks entry for the pane session it ends");
   }

   /**
    * The scheduled sweep must not be the one removal path that forgets the same cleanup --
    * proven by advancing the clock past TTL rather than asserting on evictExpired() in isolation
    * from a real expiry.
    */
   @Test
   void evictExpiredRemovesTheFocusStacksEntryForAnExpiredSessionToo() throws PairingException {
      SheetSessionService svc = serviceAt(FIXED_NOW);
      JoinSession session = svc.open("vs-1", "owner~;~org", SheetType.VIEWSHEET, "sock-1",
                                     "owner", null);
      svc.setFollowFocus("sock-1", "vs-1", true);
      svc.retarget("sock-1", "vs-1", new EditorContext("viewsheetOnInit", null, null, null));
      assertTrue(svc.hasFocusStackEntryForTesting(session.sessionToken()));

      SheetSessionService afterTtl = new SheetSessionService(
         () -> FIXED_NOW + SheetSessionService.TTL_MILLIS + 1, svc);
      afterTtl.evictExpired();

      assertFalse(svc.hasFocusStackEntryForTesting(session.sessionToken()),
                 "evictExpired must remove the focusStacks entry for a session it expires too");
   }

   /**
    * The Phase-1-row-4 exit criterion, written as a test: after a successful retarget, a call
    * through PaneScopeService.requireWholeSheetSession against a FRESH resolve() of the same
    * token reflects the new scope -- proving enforcement needed no code change because it never
    * caches. If this ever surprises us by failing, that is a real caching bug to fix, not a
    * reason to add a parallel enforcement path.
    */
   @Test
   void retargetIsVisibleToPaneScopeServiceThroughAFreshResolveWithNoEnforcementChange()
      throws PairingException
   {
      SheetSessionService svc = serviceAt(FIXED_NOW);
      JoinSession session = svc.open("vs-1", "owner~;~org", SheetType.VIEWSHEET, "sock-1",
                                     "owner", null);
      svc.setFollowFocus("sock-1", "vs-1", true);

      // Before retargeting: a whole-sheet session must pass requireWholeSheetSession.
      JoinSession beforeResolved = svc.resolve(session.sessionToken(), "owner~;~org");
      assertDoesNotThrow(() -> PaneScopeService.requireWholeSheetSession(beforeResolved));

      EditorContext pane = new EditorContext("viewsheetOnInit", null, null, null);
      svc.retarget("sock-1", "vs-1", pane);

      // After retargeting: the SAME token, freshly resolved, must now be refused as a
      // whole-sheet session by PaneScopeService -- with zero changes to that class.
      JoinSession afterResolved = svc.resolve(session.sessionToken(), "owner~;~org");
      assertThrows(PairingException.class,
                  () -> PaneScopeService.requireWholeSheetSession(afterResolved),
                  "a fresh resolve() after retarget must reflect the new pane-scoped target");
   }
}
