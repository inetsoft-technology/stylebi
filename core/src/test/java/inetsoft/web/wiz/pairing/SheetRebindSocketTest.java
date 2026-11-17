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

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.security.IdentityID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * A browser that reconnects gets a new websocket session id; {@code rebindSocket} re-points the
 * paired sheet at it. The id comes from the live STOMP request, so what is checked is who may ask
 * and which sessions move.
 *
 * [rebindsRuntimeAndWholeSheetSession] the owner's runtime and whole-sheet session take the new id
 * [paneScopedSessionKeepsItsSocket]    a pane-scoped session keeps the id socketClosed matches on
 * [onlyPaneScopedSession]              nothing is re-stamped when no whole-sheet session exists
 * [nonOwnerIsRefused]                  another user naming the runtime changes nothing
 * [otherUsersSessionIsLeftAlone]       a session of another owner on the same runtime is untouched
 * [unknownRuntimeIsRefused]            a runtime this node does not hold is refused
 * [featureOffIsRefused]                the pairing feature gate applies
 */
@Tag("core")
class SheetRebindSocketTest {
   private static final String RUNTIME = "Viewsheet/foo-7";
   private static final String ALICE_KEY = new IdentityID("alice", "host-org").convertToKey();
   private static final String BOB_KEY = new IdentityID("bob", "host-org").convertToKey();

   private SheetAgentFeature feature;
   private SheetRuntimeAccess runtimeAccess;
   private SheetSessionService sessions;
   private SheetJoinService svc;
   private RuntimeViewsheet rvs;
   private final Principal alice = TestPrincipals.user("alice", "host-org");
   private final Principal bob = TestPrincipals.user("bob", "host-org");

   @BeforeEach
   void setUp() {
      feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);
      runtimeAccess = mock(SheetRuntimeAccess.class);
      sessions = new SheetSessionService(() -> 1_000_000L);
      svc = new SheetJoinService(new SheetPairingService(() -> 1_000_000L), sessions, feature,
                                 runtimeAccess, mock(SheetAgentBroadcastService.class));
      rvs = mock(RuntimeViewsheet.class);
      when(rvs.getUser()).thenReturn(alice);
      when(runtimeAccess.getRuntimeSheetDirect(SheetType.VIEWSHEET, RUNTIME)).thenReturn(rvs);
   }

   private JoinSession open(String owner, EditorContext context) {
      return sessions.open(RUNTIME, owner, SheetType.VIEWSHEET, "sock-old", "alice-dest", context);
   }

   private String socketOf(JoinSession s) {
      return sessions.peek(s.sessionToken(), s.ownerIdentity()).socketSessionId();
   }

   @Test
   void rebindsRuntimeAndWholeSheetSession() {
      JoinSession whole = open(ALICE_KEY, null);

      assertTrue(svc.rebindSocket(SheetType.VIEWSHEET, RUNTIME, alice, "sock-new", "dest-new"));

      assertEquals("sock-new", socketOf(whole));
      assertEquals("dest-new", sessions.peek(whole.sessionToken(), ALICE_KEY).socketUserName());
      verify(rvs).setSocketSessionId("sock-new");
      verify(rvs).setSocketUserName("dest-new");
   }

   @Test
   void rebindKeepsTheRestOfTheSession() {
      JoinSession whole = open(ALICE_KEY, null);
      sessions.setFollowFocus("sock-old", RUNTIME, true);

      svc.rebindSocket(SheetType.VIEWSHEET, RUNTIME, alice, "sock-new", "dest-new");

      JoinSession after = sessions.peek(whole.sessionToken(), ALICE_KEY);
      assertTrue(after.followFocusEnabled());
      assertEquals(whole.lastAccess(), after.lastAccess());
      assertEquals(whole.sessionToken(), after.sessionToken());
   }

   @Test
   void paneScopedSessionKeepsItsSocket() {
      JoinSession whole = open(ALICE_KEY, null);
      JoinSession pane = open(ALICE_KEY, new EditorContext("viewsheetOnInit", null, null, null));

      svc.rebindSocket(SheetType.VIEWSHEET, RUNTIME, alice, "sock-new", "dest-new");

      assertEquals("sock-new", socketOf(whole));
      assertEquals("sock-old", socketOf(pane));

      // the old socket's disconnect, processed after the rebind, still ends the pane session
      sessions.socketClosed("sock-old");
      assertNull(sessions.peek(pane.sessionToken(), ALICE_KEY));
      assertNotNull(sessions.peek(whole.sessionToken(), ALICE_KEY));
   }

   @Test
   void onlyPaneScopedSession() {
      open(ALICE_KEY, new EditorContext("viewsheetOnInit", null, null, null));

      assertFalse(svc.rebindSocket(SheetType.VIEWSHEET, RUNTIME, alice, "sock-new", "dest-new"));

      verify(rvs, never()).setSocketSessionId(anyString());
   }

   @Test
   void nonOwnerIsRefused() {
      JoinSession whole = open(ALICE_KEY, null);

      assertFalse(svc.rebindSocket(SheetType.VIEWSHEET, RUNTIME, bob, "sock-bob", "dest-bob"));

      assertEquals("sock-old", socketOf(whole));
      verify(rvs, never()).setSocketSessionId(anyString());
      verify(rvs, never()).setSocketUserName(anyString());
   }

   @Test
   void otherUsersSessionIsLeftAlone() {
      JoinSession alices = open(ALICE_KEY, null);
      JoinSession bobs = open(BOB_KEY, null);

      assertTrue(svc.rebindSocket(SheetType.VIEWSHEET, RUNTIME, alice, "sock-new", "dest-new"));

      assertEquals("sock-new", socketOf(alices));
      assertEquals("sock-old", socketOf(bobs));
   }

   @Test
   void unknownRuntimeIsRefused() {
      when(runtimeAccess.getRuntimeSheetDirect(any(), anyString())).thenReturn(null);
      JoinSession whole = open(ALICE_KEY, null);

      assertFalse(svc.rebindSocket(SheetType.VIEWSHEET, RUNTIME, alice, "sock-new", "dest-new"));

      assertEquals("sock-old", socketOf(whole));
   }

   @Test
   void unownedRuntimeIsRefused() {
      when(rvs.getUser()).thenReturn(null);
      JoinSession whole = open(ALICE_KEY, null);

      assertFalse(svc.rebindSocket(SheetType.VIEWSHEET, RUNTIME, alice, "sock-new", "dest-new"));

      assertEquals("sock-old", socketOf(whole));
   }

   @Test
   void featureOffIsRefused() {
      when(feature.isEnabled()).thenReturn(false);
      JoinSession whole = open(ALICE_KEY, null);

      assertFalse(svc.rebindSocket(SheetType.VIEWSHEET, RUNTIME, alice, "sock-new", "dest-new"));

      assertEquals("sock-old", socketOf(whole));
      verify(rvs, never()).setSocketSessionId(anyString());
   }
}
