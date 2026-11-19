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

   /** The live per-call check still gates a runtime this node could not resolve at join. */
   @Test
   void anUnresolvableRuntimeIsStampedPairedAndNotRefused() throws Exception {
      assertEquals(JoinSession.ConnectionMode.PAIRED,
                   svc.join(mintWholeSheet(ALICE_KEY), ALICE).session().connectionMode());
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
}
