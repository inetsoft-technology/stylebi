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
}
