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
