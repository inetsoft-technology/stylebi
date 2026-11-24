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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.report.composition.RuntimeSheet;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.asset.AssetEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The viewer gate must fail closed in a cluster (review of #8572): the mode is derived once at
 * mint, on the node that holds the page's socket, and carried in the grant; the join's node-local
 * runtime lookup may add "viewer" but can never remove it.
 */
@Tag("core")
class ViewerModeClusterTest {
   private static final String ALICE_KEY = new IdentityID("alice", "host-org").convertToKey();
   private static final Principal ALICE = TestPrincipals.user("alice", "host-org");

   /** What the minting node sees for each runtime id; absent means unresolvable there. */
   private final Map<String, RuntimeSheet> mintSide = new HashMap<>();
   private final AtomicReference<String> node = new AtomicReference<>("node-A");
   private SheetPairingService pairing;
   private SheetSessionService sessions;
   private SheetRuntimeAccess joinSide;
   private SheetAgentFeature feature;

   @BeforeEach
   void setUp() {
      pairing = new SheetPairingService(() -> 1_000_000L, mintSide::get, mintSide::get,
                                        node::get);
      sessions = new SheetSessionService(() -> 1_000_000L);
      joinSide = mock(SheetRuntimeAccess.class);
      feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);
   }

   private SheetJoinService join(Predicate<String> socket) {
      return new SheetJoinService(pairing, sessions, feature, joinSide,
                                  mock(SheetAgentBroadcastService.class), socket);
   }

   private RuntimeViewsheet vs(boolean viewer) {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.isViewer()).thenReturn(viewer);
      when(rvs.getUser()).thenReturn(ALICE);
      when(rvs.getEntry()).thenReturn(mock(AssetEntry.class));
      return rvs;
   }

   private String mintVs() throws PairingException {
      return pairing.mint("vs-1", ALICE_KEY, "sock-1", "alice", SheetType.VIEWSHEET, null);
   }

   // ---- mint records what the runtime is ---------------------------------------------------

   @Test
   void mintOnAViewerRuntimeRecordsViewerAndOnAComposerRuntimeRecordsDesign() throws Exception {
      mintSide.put("vs-1", vs(true));
      mintSide.put("vs-2", vs(false));
      mintSide.put("ws-1", mock(RuntimeWorksheet.class));

      assertEquals(PairingGrant.RuntimeKind.VIEWER, pairing.peek(mintVs()).runtimeKind());
      assertEquals(PairingGrant.RuntimeKind.DESIGN, pairing.peek(
         pairing.mint("vs-2", ALICE_KEY, "s", "alice", SheetType.VIEWSHEET, null)).runtimeKind());
      assertEquals(PairingGrant.RuntimeKind.DESIGN, pairing.peek(
         pairing.mint("ws-1", ALICE_KEY, "s", "alice", SheetType.WORKSHEET, null)).runtimeKind());
   }

   @Test
   void mintOnAnUnresolvableViewsheetRecordsNothingAndTheNodeIsRecorded() throws Exception {
      PairingGrant g = pairing.peek(mintVs());

      assertNull(g.runtimeKind());
      assertEquals("node-A", g.mintNode());
   }

   @Test
   void aClientSentModeInTheMintFrameIsStillIgnored() throws Exception {
      mintSide.put("vs-1", vs(true));
      SheetPairingController c = new SheetPairingController(pairing, new SheetSessionService(),
         feature, mock(SheetAgentBroadcastService.class), mock(SheetJoinService.class), false);
      SimpMessageHeaderAccessor acc = mock(SimpMessageHeaderAccessor.class);
      when(acc.getSessionId()).thenReturn("stomp-1");
      ObjectMapper m = new ObjectMapper().configure(
         DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

      String code = c.mintViaSocket(m.readValue(
         "{\"runtimeId\":\"vs-1\",\"sheetType\":\"VIEWSHEET\",\"runtimeKind\":\"DESIGN\"," +
         "\"mode\":\"PAIRED\",\"viewer\":false}", SheetPairingController.MintRequest.class),
         ALICE, acc).code();

      assertEquals(PairingGrant.RuntimeKind.VIEWER, pairing.peek(code).runtimeKind());
   }

   @Test
   void theCompatibilityConstructorRecordsNothing() {
      PairingGrant g =
         new PairingGrant("C", "r", "o", "s", null, 0L, 1L, SheetType.VIEWSHEET, null);

      assertNull(g.runtimeKind());
      assertNull(g.mintNode());
   }

   // ---- join: the local lookup may add viewer, never remove it -----------------------------

   /** The blocker: runtime on another node, so the join's lookup is null; the grant says viewer. */
   @Test
   void unresolvedAtJoinButGrantSaysViewerGivesAViewerSessionTheGateRefuses() throws Exception {
      mintSide.put("vs-1", vs(true));
      String code = mintVs();   // joinSide resolves nothing: the runtime is on another node

      JoinSession s = join(id -> true).join(code, ALICE).session();

      assertEquals(JoinSession.ConnectionMode.VIEWER, s.connectionMode());
      assertTrue(ViewerModePolicy.isViewer(s, joinSide));
      assertThrows(PairingException.class, () -> ViewerModePolicy.requireNotViewer(s, joinSide));
   }

   @Test
   void unresolvedAtJoinButGrantSaysDesignGivesPaired() throws Exception {
      mintSide.put("vs-1", vs(false));
      String code = mintVs();

      JoinSession s = join(id -> true).join(code, ALICE).session();

      assertEquals(JoinSession.ConnectionMode.PAIRED, s.connectionMode());
      assertFalse(ViewerModePolicy.isViewer(s, joinSide));
   }

   @Test
   void aViewsheetNeitherSideCanResolveIsRefusedNotPaired() throws Exception {
      String code = mintVs();

      PairingException e = assertThrows(PairingException.class,
         () -> join(id -> true).join(code, ALICE));

      assertEquals(PairingException.Kind.SESSION_EXPIRED, e.getKind());
      assertTrue(sessions.findAllOnRuntime("vs-1").isEmpty(), "no session may be opened");
   }

   @Test
   void aWorksheetOnAnotherNodeStillJoinsPaired() throws Exception {
      mintSide.put("ws-1", mock(RuntimeWorksheet.class));
      String code = pairing.mint("ws-1", ALICE_KEY, "s", "alice", SheetType.WORKSHEET, null);

      assertEquals(JoinSession.ConnectionMode.PAIRED,
                   join(id -> true).join(code, ALICE).session().connectionMode());
   }

   @Test
   void theLocalViewerLookupStillWinsOverAGrantThatSaidDesign() throws Exception {
      mintSide.put("vs-1", vs(false));
      String code = mintVs();
      RuntimeViewsheet local = vs(true);
      when(joinSide.getRuntimeSheetDirect(SheetType.VIEWSHEET, "vs-1")).thenReturn(local);

      assertEquals(JoinSession.ConnectionMode.VIEWER,
                   join(id -> true).join(code, ALICE).session().connectionMode());
   }

   // ---- the page-socket check is node-local ------------------------------------------------

   @Test
   void theSocketCheckRunsOnTheMintingNodeAndIsSkippedOnAnother() throws Exception {
      mintSide.put("vs-1", vs(true));
      String onMintNode = mintVs();
      String offNode = mintVs();

      assertEquals(PairingException.Kind.SESSION_EXPIRED,
         assertThrows(PairingException.class, () -> join(id -> false).join(onMintNode, ALICE))
            .getKind());

      node.set("node-B");   // this node is no longer the one that minted
      assertEquals(JoinSession.ConnectionMode.VIEWER,
                   join(id -> false).join(offNode, ALICE).session().connectionMode());
   }
}
