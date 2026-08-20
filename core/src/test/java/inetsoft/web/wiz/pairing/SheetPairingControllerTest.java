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
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
class SheetPairingControllerTest {

   @Test
   void restMintBindsOpenRuntimeToOwnerAndSocketSession() throws PairingException {
      SheetPairingService pairing = new SheetPairingService();
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);
      SheetPairingController c = new SheetPairingController(pairing, new SheetSessionService(), feature, mock(SheetAgentBroadcastService.class), true);
      Principal owner = TestPrincipals.user("alice", "host-org");

      String code = c.mint("Worksheet/foo-7", "stomp-1", SheetType.WORKSHEET, owner).code();

      PairingGrant g = pairing.peek(code);
      assertNotNull(g);
      assertEquals("Worksheet/foo-7", g.runtimeId());
      assertEquals("alice~;~host-org", g.ownerIdentity());
      assertEquals("stomp-1", g.socketSessionId());
      assertEquals(SheetType.WORKSHEET, g.sheetType());
   }

   @Test
   void restMintRefusedWhenFeatureOff() {
      SheetPairingService pairing = new SheetPairingService();
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(false);
      SheetPairingController c = new SheetPairingController(pairing, new SheetSessionService(), feature, mock(SheetAgentBroadcastService.class), true);
      Principal owner = TestPrincipals.user("alice", "host-org");

      assertThrows(ResponseStatusException.class,
                   () -> c.mint("Worksheet/foo-7", "stomp-1", SheetType.WORKSHEET, owner));
   }

   @Test
   void restMintRejectsNullPrincipal() {
      SheetPairingService pairing = new SheetPairingService();
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);
      SheetPairingController c = new SheetPairingController(pairing, new SheetSessionService(), feature, mock(SheetAgentBroadcastService.class), true);

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> c.mint("Worksheet/foo-7", "stomp-1", SheetType.WORKSHEET, null));
      assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
   }

   @Test
   void stompMintDerivesSessionFromAccessor() {
      SheetPairingService pairing = new SheetPairingService();
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);
      SheetPairingController c = new SheetPairingController(pairing, new SheetSessionService(), feature, mock(SheetAgentBroadcastService.class), true);
      Principal owner = TestPrincipals.user("alice", "host-org");

      // Build a SimpMessageHeaderAccessor with a known session id
      SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
      accessor.setSessionId("derived-stomp-9");

      SheetPairingController.MintRequest req =
         new SheetPairingController.MintRequest("Worksheet/foo-7", SheetType.WORKSHEET, null);
      String code = c.mintViaSocket(req, owner, accessor).code();

      assertEquals("derived-stomp-9", pairing.peek(code).socketSessionId());
   }

   @Test
   void stompMintRefusedWhenFeatureOff() {
      SheetPairingService pairing = new SheetPairingService();
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(false);
      SheetPairingController c = new SheetPairingController(pairing, new SheetSessionService(), feature, mock(SheetAgentBroadcastService.class), true);
      Principal owner = TestPrincipals.user("alice", "host-org");
      SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
      accessor.setSessionId("stomp-x");

      SheetPairingController.MintResponse resp =
         c.mintViaSocket(new SheetPairingController.MintRequest("WS/1", SheetType.WORKSHEET, null),
                         owner, accessor);
      assertNull(resp.code(), "code should be null when feature is off");
      assertNotNull(resp.error(), "error should be non-null when feature is off");
   }

   @Test
   void detachEndsThePaneScopedSessionBoundToTheCallersSocket() {
      SheetPairingService pairing = new SheetPairingService();
      SheetSessionService sessions = new SheetSessionService();
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      SheetPairingController c = new SheetPairingController(pairing, sessions, feature, mock(SheetAgentBroadcastService.class), true);

      EditorContext ctx = new EditorContext("assemblyMain", "Chart1", null, null);
      JoinSession pane = sessions.open("Viewsheet/vs-1", "alice~;~host-org", SheetType.VIEWSHEET,
                                       "stomp-9", "alice", ctx);

      SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
      accessor.setSessionId("stomp-9");

      c.detachViaSocket(new SheetPairingController.DetachRequest(ctx), accessor);

      assertNull(sessions.resolve(pane.sessionToken(), "alice~;~host-org"),
                 "detach should end the pane-scoped session bound to this socket + editorContext");
   }

   @Test
   void detachIgnoresARequestWithNoEditorContext() {
      SheetPairingService pairing = new SheetPairingService();
      SheetSessionService sessions = new SheetSessionService();
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      SheetPairingController c = new SheetPairingController(pairing, sessions, feature, mock(SheetAgentBroadcastService.class), true);

      JoinSession sheet = sessions.open("Viewsheet/vs-1", "alice~;~host-org", SheetType.VIEWSHEET,
                                        "stomp-9", "alice", null);

      SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
      accessor.setSessionId("stomp-9");

      c.detachViaSocket(new SheetPairingController.DetachRequest(null), accessor);

      assertNotNull(sessions.resolve(sheet.sessionToken(), "alice~;~host-org"),
                    "a whole-sheet session must never be reachable from the detach endpoint");
   }

   // ---- Follow Focus: follow-focus / retarget / pop-focus STOMP endpoints --------------------

   @Test
   void followFocusViaSocketTogglesTheCallersOwnSocketBoundSession() {
      SheetPairingService pairing = new SheetPairingService();
      SheetSessionService sessions = new SheetSessionService();
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      SheetPairingController c = new SheetPairingController(pairing, sessions, feature, mock(SheetAgentBroadcastService.class), true);

      JoinSession sheet = sessions.open("Viewsheet/vs-1", "alice~;~host-org", SheetType.VIEWSHEET,
                                        "stomp-9", "alice", null);
      SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
      accessor.setSessionId("stomp-9");

      c.followFocusViaSocket(new SheetPairingController.FollowFocusRequest("Viewsheet/vs-1", true),
                             accessor);

      assertTrue(sessions.resolve(sheet.sessionToken(), "alice~;~host-org").followFocusEnabled());
   }

   @Test
   void retargetViaSocketRefusesWhenTheSessionNeverOptedIn() {
      SheetPairingService pairing = new SheetPairingService();
      SheetSessionService sessions = new SheetSessionService();
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      SheetAgentBroadcastService broadcast = mock(SheetAgentBroadcastService.class);
      SheetPairingController c = new SheetPairingController(pairing, sessions, feature, broadcast, true);

      JoinSession sheet = sessions.open("Viewsheet/vs-1", "alice~;~host-org", SheetType.VIEWSHEET,
                                        "stomp-9", "alice", null);
      SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
      accessor.setSessionId("stomp-9");

      EditorContext pane = new EditorContext("viewsheetOnInit", null, null, null);
      SheetPairingController.RetargetResponse resp = c.retargetViaSocket(
         new SheetPairingController.RetargetRequest("Viewsheet/vs-1", pane), accessor);

      assertFalse(resp.ok());
      assertNotNull(resp.error());
      assertNull(sessions.resolve(sheet.sessionToken(), "alice~;~host-org").editorContext(),
                "a refused retarget must never move the session's target");
      verifyNoInteractions(broadcast);
   }

   @Test
   void retargetViaSocketMovesAnOptedInSessionAndPopFocusRestoresIt() {
      SheetPairingService pairing = new SheetPairingService();
      SheetSessionService sessions = new SheetSessionService();
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      SheetAgentBroadcastService broadcast = mock(SheetAgentBroadcastService.class);
      SheetPairingController c = new SheetPairingController(pairing, sessions, feature, broadcast, true);

      JoinSession sheet = sessions.open("Viewsheet/vs-1", "alice~;~host-org", SheetType.VIEWSHEET,
                                        "stomp-9", "alice", null);
      SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
      accessor.setSessionId("stomp-9");

      c.followFocusViaSocket(new SheetPairingController.FollowFocusRequest("Viewsheet/vs-1", true),
                             accessor);

      EditorContext pane = new EditorContext("viewsheetOnInit", null, null, null);
      SheetPairingController.RetargetResponse resp = c.retargetViaSocket(
         new SheetPairingController.RetargetRequest("Viewsheet/vs-1", pane), accessor);

      assertTrue(resp.ok());
      assertNull(resp.error());
      assertEquals(pane, sessions.resolve(sheet.sessionToken(), "alice~;~host-org").editorContext());

      ArgumentCaptor<JoinSession> afterRetarget = ArgumentCaptor.forClass(JoinSession.class);
      verify(broadcast).sendFocusChanged(afterRetarget.capture());
      assertEquals(pane, afterRetarget.getValue().editorContext(),
                  "the retarget broadcast must carry the NEW target, not the one it replaced");

      c.popFocusViaSocket(new SheetPairingController.PopFocusRequest("Viewsheet/vs-1"), accessor);

      assertNull(sessions.resolve(sheet.sessionToken(), "alice~;~host-org").editorContext(),
                "pop-focus must restore the session's original (whole-sheet) target");

      ArgumentCaptor<JoinSession> afterPop = ArgumentCaptor.forClass(JoinSession.class);
      verify(broadcast, times(2)).sendFocusChanged(afterPop.capture());
      assertNull(afterPop.getValue().editorContext(),
                "the pop-focus broadcast must carry the restored (whole-sheet) target");
   }

   @Test
   void popFocusViaSocketDoesNotBroadcastWhenNoSessionMatches() {
      SheetPairingService pairing = new SheetPairingService();
      SheetSessionService sessions = new SheetSessionService();
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      SheetAgentBroadcastService broadcast = mock(SheetAgentBroadcastService.class);
      SheetPairingController c = new SheetPairingController(pairing, sessions, feature, broadcast, true);

      SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
      accessor.setSessionId("no-such-socket");

      c.popFocusViaSocket(new SheetPairingController.PopFocusRequest("Viewsheet/vs-1"), accessor);

      verifyNoInteractions(broadcast);
   }

   /**
    * Distinct from {@link #popFocusViaSocketDoesNotBroadcastWhenNoSessionMatches}: here a real
    * session exists and matches, but its focus stack has nothing left to pop (never retargeted
    * at all). This is the exhausted-but-found case the original defect report singled out as
    * uncovered -- popFocus used to return the unchanged session for it, which this controller
    * would have (wrongly) treated as "something changed, broadcast it".
    */
   @Test
   void popFocusViaSocketDoesNotBroadcastWhenTheSessionsStackIsAlreadyExhausted() {
      SheetPairingService pairing = new SheetPairingService();
      SheetSessionService sessions = new SheetSessionService();
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      SheetAgentBroadcastService broadcast = mock(SheetAgentBroadcastService.class);
      SheetPairingController c = new SheetPairingController(pairing, sessions, feature, broadcast, true);

      sessions.open("Viewsheet/vs-1", "alice~;~host-org", SheetType.VIEWSHEET, "stomp-9",
                   "alice", null);
      SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
      accessor.setSessionId("stomp-9");

      c.popFocusViaSocket(new SheetPairingController.PopFocusRequest("Viewsheet/vs-1"), accessor);

      verifyNoInteractions(broadcast);
   }
}
