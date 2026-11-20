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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * A7 / C7: a viewer grant cannot outlive its viewer; and C6: design and pane sessions keep
 * exactly today's lifetime rules.
 */
@Tag("core")
class SheetSessionServiceViewerLifecycleTest {
   private static final String OWNER = "alice~;~host-org";
   private static final JoinSession.ConnectionMode VIEWER = JoinSession.ConnectionMode.VIEWER;

   private AtomicLong now;
   private SheetAgentBroadcastService broadcast;
   private SheetSessionService sessions;

   @BeforeEach
   void setUp() {
      now = new AtomicLong(1_000_000L);
      broadcast = mock(SheetAgentBroadcastService.class);
      sessions = new SheetSessionService(now::get, broadcast);
   }

   private JoinSession open(String runtime, String socket, JoinSession.ConnectionMode mode,
                            EditorContext ctx)
   {
      return sessions.open(runtime, OWNER, SheetType.VIEWSHEET, socket, "alice", ctx, mode);
   }

   @Test
   void aSocketCloseReapsTheViewerSessionsOfThatSocketAndTombstonesThem() {
      JoinSession v = open("vs-1", "sock-1", VIEWER, null);
      JoinSession other = open("vs-2", "sock-2", VIEWER, null);

      sessions.socketClosed("sock-1");

      assertNull(sessions.resolve(v.sessionToken(), OWNER));
      assertEquals(SheetSessionService.VIEWER_CLOSED, sessions.endedReason(v.sessionToken()));
      assertNotNull(sessions.resolve(other.sessionToken(), OWNER));
      assertNull(sessions.endedReason(other.sessionToken()));
      verify(broadcast).sendAgentInactive(v);
   }

   /** C6: a whole-sheet DESIGN session still survives a socket blip. */
   @Test
   void aWholeSheetDesignSessionStillSurvivesASocketClose() {
      JoinSession d = open("vs-1", "sock-1", JoinSession.ConnectionMode.PAIRED, null);

      sessions.socketClosed("sock-1");

      assertNotNull(sessions.resolve(d.sessionToken(), OWNER));
      assertNull(sessions.endedReason(d.sessionToken()));
   }

   /** C6: pane-scoped reaping is unchanged, and a pane session leaves no viewer tombstone. */
   @Test
   void aPaneSessionIsStillReapedAndIsNotTombstoned() {
      EditorContext ctx = new EditorContext("assemblyMain", "Chart1", null, null);
      JoinSession p = open("vs-1", "sock-1", JoinSession.ConnectionMode.PAIRED, ctx);

      sessions.socketClosed("sock-1");

      assertNull(sessions.resolve(p.sessionToken(), OWNER));
      assertNull(sessions.endedReason(p.sessionToken()));
   }

   @Test
   void aRebindBeforeTheOldSocketClosesKeepsTheViewerSession() {
      JoinSession v = open("vs-1", "sock-old", VIEWER, null);

      sessions.rebindSocket("vs-1", OWNER, "sock-new", "alice");
      sessions.socketClosed("sock-old");

      assertNotNull(sessions.resolve(v.sessionToken(), OWNER));
   }

   @Test
   void endViewerSessionsEndsOnlyTheMatchingSocketAndRuntimeAndOnlyViewerSessions() {
      JoinSession match = open("vs-1", "sock-1", VIEWER, null);
      JoinSession otherRuntime = open("vs-2", "sock-1", VIEWER, null);
      JoinSession otherSocket = open("vs-1", "sock-2", VIEWER, null);
      JoinSession design = open("vs-1", "sock-1", JoinSession.ConnectionMode.PAIRED, null);

      List<JoinSession> ended = sessions.endViewerSessions("sock-1", "vs-1");

      assertEquals(List.of(match), ended);
      assertNull(sessions.resolve(match.sessionToken(), OWNER));
      assertEquals(SheetSessionService.VIEWER_CLOSED, sessions.endedReason(match.sessionToken()));
      assertNotNull(sessions.resolve(otherRuntime.sessionToken(), OWNER));
      assertNotNull(sessions.resolve(otherSocket.sessionToken(), OWNER));
      assertNotNull(sessions.resolve(design.sessionToken(), OWNER));
   }

   @Test
   void endViewerSessionsIsNullSafe() {
      assertTrue(sessions.endViewerSessions(null, "vs-1").isEmpty());
      assertTrue(sessions.endViewerSessions("sock-1", null).isEmpty());
   }

   @Test
   void tombstonesAreSweptOnceOlderThanTheSessionTtl() {
      JoinSession v = open("vs-1", "sock-1", VIEWER, null);
      sessions.socketClosed("sock-1");

      now.addAndGet(SheetSessionService.TTL_MILLIS - 1);
      sessions.evictExpired();
      assertNotNull(sessions.endedReason(v.sessionToken()));

      now.addAndGet(2);
      sessions.evictExpired();
      assertNull(sessions.endedReason(v.sessionToken()));
   }

   /** C7: once reaped, the keep-alive's own lookup finds nothing, so nothing keeps the runtime. */
   @Test
   void aReapedViewerSessionNoLongerResolvesForTheKeepAlive() {
      JoinSession v = open("vs-1", "sock-1", VIEWER, null);
      assertNotNull(sessions.peek(v.sessionToken(), OWNER));

      sessions.socketClosed("sock-1");

      assertNull(sessions.peek(v.sessionToken(), OWNER));
      assertFalse(sessions.isLive(v.sessionToken()));
   }

   @Test
   void theSixArgOpenStaysPairedAndAnExplicitModeIsKept() {
      assertEquals(JoinSession.ConnectionMode.PAIRED,
                   sessions.open("vs-1", OWNER, SheetType.VIEWSHEET, "s", "a", null)
                      .connectionMode());
      assertEquals(VIEWER, open("vs-1", "s", VIEWER, null).connectionMode());
      assertEquals(JoinSession.ConnectionMode.PAIRED,
                   sessions.open("vs-1", OWNER, SheetType.VIEWSHEET, "s", "a", null, null)
                      .connectionMode());
   }

   @Test
   void theModeSurvivesEveryReconstructionOfTheSession() {
      JoinSession v = open("vs-1", "sock-1", VIEWER, null);

      assertEquals(VIEWER, sessions.resolve(v.sessionToken(), OWNER).connectionMode());
      sessions.setFollowFocus("sock-1", "vs-1", true);
      assertEquals(VIEWER, sessions.peek(v.sessionToken(), OWNER).connectionMode());
      sessions.rebindSocket("vs-1", OWNER, "sock-2", "alice");
      assertEquals(VIEWER, sessions.peek(v.sessionToken(), OWNER).connectionMode());
   }

   // ---- STOMP viewer-detach (W5) ------------------------------------------------------------

   private SheetPairingController controller() {
      return new SheetPairingController(new SheetPairingService(), sessions,
                                        mock(SheetAgentFeature.class), broadcast,
                                        mock(SheetJoinService.class), false);
   }

   private static SimpMessageHeaderAccessor accessor(String socket) {
      SimpMessageHeaderAccessor a = mock(SimpMessageHeaderAccessor.class);
      when(a.getSessionId()).thenReturn(socket);
      return a;
   }

   @Test
   void viewerDetachUsesTheCallersOwnSocketNeverThePayload() {
      JoinSession mine = open("vs-1", "sock-me", VIEWER, null);
      JoinSession theirs = open("vs-1", "sock-them", VIEWER, null);

      controller().viewerDetachViaSocket(new SheetPairingController.ViewerDetachRequest("vs-1"),
                                         accessor("sock-me"));

      assertNull(sessions.resolve(mine.sessionToken(), OWNER));
      assertNotNull(sessions.resolve(theirs.sessionToken(), OWNER),
                    "another browser's viewer session must not end");
   }

   @Test
   void viewerDetachIsNullSafeAndIgnoresADesignSession() {
      JoinSession design = open("vs-1", "sock-me", JoinSession.ConnectionMode.PAIRED, null);

      controller().viewerDetachViaSocket(null, accessor("sock-me"));
      controller().viewerDetachViaSocket(new SheetPairingController.ViewerDetachRequest(null),
                                         accessor("sock-me"));
      controller().viewerDetachViaSocket(new SheetPairingController.ViewerDetachRequest("vs-1"),
                                         accessor("sock-me"));

      assertNotNull(sessions.resolve(design.sessionToken(), OWNER));
   }
}
