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

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.web.wiz.viewsheet.ViewsheetAssemblyAgentController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The mode a join records (A2, C1) and who may join at all (A8): the mode is derived from the
 * runtime after the user and ownership checks, and no client input exists that could choose it.
 */
@Tag("core")
class SheetJoinServiceViewerModeTest {
   private static final String ALICE_KEY = new IdentityID("alice", "host-org").convertToKey();
   private static final String BOB_KEY = new IdentityID("bob", "host-org").convertToKey();
   private static final Principal ALICE = TestPrincipals.user("alice", "host-org");
   private static final Principal BOB = TestPrincipals.user("bob", "host-org");

   private SheetPairingService pairing;
   private SheetSessionService sessions;
   private SheetRuntimeAccess runtimeAccess;
   private SheetAgentFeature feature;
   private SheetJoinService svc;

   @BeforeEach
   void setUp() {
      pairing = new SheetPairingService(() -> 1_000_000L);
      sessions = new SheetSessionService(() -> 1_000_000L);
      runtimeAccess = mock(SheetRuntimeAccess.class);
      feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);
      svc = new SheetJoinService(pairing, sessions, feature, runtimeAccess,
                                 mock(SheetAgentBroadcastService.class));
   }

   private RuntimeViewsheet runtime(boolean viewer, Principal owner) {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.isViewer()).thenReturn(viewer);
      when(rvs.getUser()).thenReturn(owner);
      when(rvs.getEntry()).thenReturn(mock(AssetEntry.class));
      when(runtimeAccess.getRuntimeSheetDirect(SheetType.VIEWSHEET, "vs-1")).thenReturn(rvs);
      return rvs;
   }

   private String mintWholeSheet(String ownerKey) throws PairingException {
      return pairing.mint("vs-1", ownerKey, "sock-1", "alice", SheetType.VIEWSHEET, null);
   }

   @Test
   void aViewerRuntimeYieldsAViewerSession() throws Exception {
      runtime(true, ALICE);

      JoinSession s = svc.join(mintWholeSheet(ALICE_KEY), ALICE).session();

      assertEquals(JoinSession.ConnectionMode.VIEWER, s.connectionMode());
      assertNull(s.editorContext());
   }

   @Test
   void aComposerRuntimeYieldsADesignSession() throws Exception {
      runtime(false, ALICE);

      assertEquals(JoinSession.ConnectionMode.PAIRED,
                   svc.join(mintWholeSheet(ALICE_KEY), ALICE).session().connectionMode());
   }

   /** A viewsheet nobody could resolve at mint or at join is refused, never guessed PAIRED. */
   @Test
   void anUnresolvableViewsheetWithNoRecordedModeIsRefused() throws Exception {
      String code = mintWholeSheet(ALICE_KEY);

      assertEquals(PairingException.Kind.SESSION_EXPIRED,
         assertThrows(PairingException.class, () -> svc.join(code, ALICE)).getKind());
   }

   @Test
   void aWorksheetRuntimeIsNeverViewer() throws Exception {
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getUser()).thenReturn(ALICE);
      when(rws.getEntry()).thenReturn(mock(AssetEntry.class));
      when(runtimeAccess.getRuntimeSheetDirect(SheetType.WORKSHEET, "ws-1")).thenReturn(rws);
      String code = pairing.mint("ws-1", ALICE_KEY, "sock-1", "alice", SheetType.WORKSHEET, null);

      assertEquals(JoinSession.ConnectionMode.PAIRED,
                   svc.join(code, ALICE).session().connectionMode());
   }

   /** C1: nothing in the mint payload, the grant or the join request is a mode. */
   @Test
   void noClientVisibleStructureCarriesAMode() {
      for(Class<?> type : new Class<?>[] { PairingGrant.class, SheetPairingController.MintRequest.class,
                                           ViewsheetAssemblyAgentController.JoinRequest.class })
      {
         for(RecordComponent c : type.getRecordComponents()) {
            assertFalse(c.getName().toLowerCase().contains("mode") ||
                        c.getName().toLowerCase().contains("viewer") ||
                        c.getName().toLowerCase().contains("designer"),
                        type.getSimpleName() + " carries " + c.getName());
         }
      }
   }

   @Test
   void theJoinResponseCarriesTheModeAndTheOldConstructorDefaultsToPaired() throws Exception {
      runtime(true, ALICE);
      JoinSession s = svc.join(mintWholeSheet(ALICE_KEY), ALICE).session();
      ViewsheetAssemblyAgentController.JoinResponse viewer =
         new ViewsheetAssemblyAgentController.JoinResponse(
            s.sessionToken(), s.runtimeId(), s.ownerIdentity(), "viewsheet", null, "L", 1,
            s.connectionMode().name());
      ViewsheetAssemblyAgentController.JoinResponse old =
         new ViewsheetAssemblyAgentController.JoinResponse(
            "t", "r", "o", "viewsheet", null, null, null);
      ObjectMapper json = new ObjectMapper();

      assertEquals("VIEWER", json.readTree(json.writeValueAsString(viewer)).get("connectionMode").asText());
      assertEquals("PAIRED", json.readTree(json.writeValueAsString(old)).get("connectionMode").asText());
      assertEquals(8, ViewsheetAssemblyAgentController.JoinResponse.class.getRecordComponents().length);
   }

   // ---- A8 server half: who may join -------------------------------------------------------

   @Test
   void aCodeMintedByOneUserCannotBeJoinedByAnother() throws Exception {
      runtime(true, ALICE);
      String code = mintWholeSheet(ALICE_KEY);

      PairingException e = assertThrows(PairingException.class, () -> svc.join(code, BOB));

      assertEquals(PairingException.Kind.USER_MISMATCH, e.getKind());
   }

   @Test
   void aCodeMintedByYForXsRuntimeIsRefusedAtTheOwnershipCheckAndNeverRevealsAMode()
      throws Exception
   {
      runtime(true, ALICE);   // alice's viewer runtime
      String code = mintWholeSheet(BOB_KEY);   // bob mints naming it

      PairingException e = assertThrows(PairingException.class, () -> svc.join(code, BOB));

      assertEquals(PairingException.Kind.USER_MISMATCH, e.getKind());
      assertEquals("Pairing code does not belong to this user", e.getMessage());
      assertTrue(sessions.findAllOnRuntime("vs-1").isEmpty(), "no session may be opened");
   }

   /** A user without edit rights can pair (Q2) and is then refused every non-read route. */
   @Test
   void aViewOnlyUserJoinsAViewerSessionAndGetsNoEditPath() throws Exception {
      runtime(true, ALICE);
      JoinSession s = svc.join(mintWholeSheet(ALICE_KEY), ALICE).session();

      assertTrue(ViewerModePolicy.isViewer(s, runtimeAccess));
      assertThrows(PairingException.class,
                   () -> ViewerModePolicy.requireNotViewer(s, runtimeAccess));
   }

   @Test
   void featureOffStillRefusesBeforeAnyModeIsDerived() throws Exception {
      String code = mintWholeSheet(ALICE_KEY);
      when(feature.isEnabled()).thenReturn(false);
      runtime(true, ALICE);

      PairingException e = assertThrows(PairingException.class,
         () -> svc.join(code, ALICE));

      assertEquals(PairingException.Kind.FEATURE_DISABLED, e.getKind());
   }

   // ---- C7: a viewer join whose page socket is already gone (review I2) ---------------------

   private SheetJoinService withSocketCheck(java.util.function.Predicate<String> connected) {
      return new SheetJoinService(pairing, sessions, feature, runtimeAccess,
                                  mock(SheetAgentBroadcastService.class), connected);
   }

   @Test
   void aViewerJoinWhoseSocketClosedBetweenMintAndJoinIsRefusedAndOpensNothing() throws Exception {
      RuntimeViewsheet rvs = runtime(true, ALICE);
      String code = mintWholeSheet(ALICE_KEY);

      PairingException e = assertThrows(PairingException.class,
         () -> withSocketCheck(id -> false).join(code, ALICE));

      assertEquals(PairingException.Kind.SESSION_EXPIRED, e.getKind());
      assertEquals(ViewerModePolicy.VIEWER_CLOSED_TEXT, e.getMessage());
      assertTrue(sessions.findAllOnRuntime("vs-1").isEmpty(), "no session may be opened");
      org.mockito.Mockito.verify(rvs, org.mockito.Mockito.never()).setSocketSessionId(any());
      assertNull(pairing.peek(code), "the code is spent");
   }

   @Test
   void aViewerJoinWhileThePageSocketIsConnectedWorks() throws Exception {
      runtime(true, ALICE);
      String code = mintWholeSheet(ALICE_KEY);

      JoinSession s = withSocketCheck("sock-1"::equals).join(code, ALICE).session();

      assertEquals(JoinSession.ConnectionMode.VIEWER, s.connectionMode());
      assertEquals("sock-1", s.socketSessionId());
   }

   /** Design sessions deliberately survive a socket drop, so the join is not socket-gated. */
   @Test
   void aComposerJoinIsNotSubjectToTheSocketCheck() throws Exception {
      runtime(false, ALICE);
      String code = mintWholeSheet(ALICE_KEY);

      assertEquals(JoinSession.ConnectionMode.PAIRED,
                   withSocketCheck(id -> false).join(code, ALICE).session().connectionMode());
   }

   @Test
   void theStompUserRegistryDecidesWhetherASocketIsConnected() throws Exception {
      runtime(true, ALICE);
      org.springframework.messaging.simp.user.SimpUser user =
         mock(org.springframework.messaging.simp.user.SimpUser.class);
      org.springframework.messaging.simp.user.SimpSession live =
         mock(org.springframework.messaging.simp.user.SimpSession.class);
      when(user.getSession("sock-1")).thenReturn(live);
      org.springframework.messaging.simp.user.SimpUserRegistry registry =
         mock(org.springframework.messaging.simp.user.SimpUserRegistry.class);
      @SuppressWarnings("unchecked")
      org.springframework.beans.factory.ObjectProvider<
         org.springframework.messaging.simp.user.SimpUserRegistry> provider =
            mock(org.springframework.beans.factory.ObjectProvider.class);
      when(provider.getIfAvailable()).thenReturn(registry);
      SheetJoinService svc2 = new SheetJoinService(pairing, sessions, feature, runtimeAccess,
                                                   mock(SheetAgentBroadcastService.class),
                                                   provider);

      when(registry.getUsers()).thenReturn(java.util.Set.of(user));
      assertEquals(JoinSession.ConnectionMode.VIEWER,
                   svc2.join(mintWholeSheet(ALICE_KEY), ALICE).session().connectionMode());

      when(registry.getUsers()).thenReturn(java.util.Set.of());
      String code = mintWholeSheet(ALICE_KEY);
      assertEquals(PairingException.Kind.SESSION_EXPIRED,
                   assertThrows(PairingException.class, () -> svc2.join(code, ALICE)).getKind());

      when(provider.getIfAvailable()).thenReturn(null);   // no registry: nothing to check against
      assertNotNull(svc2.join(mintWholeSheet(ALICE_KEY), ALICE).session());
   }
}
