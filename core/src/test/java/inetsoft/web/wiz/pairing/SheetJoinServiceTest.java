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
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for SheetJoinService.
 *
 * [validCode]       feature on, valid code, same user -> JoinSession opened
 * [wrongCode]       join with unknown code -> PairingException
 * [wrongUser]       mint for alice, join as bob -> PairingException
 * [codeConsumed]    second join with same code -> PairingException
 * [featureOff]      feature off -> PairingException; code not consumed
 * [viewsheet]       viewsheet code, valid + same user -> JoinSession opened
 * [lockout]         8 failed lookups from the same caller -> 9th call (even with a valid code)
 *                   throws PairingException with Kind.RATE_LIMITED
 * [differentKeys]   lockout of one caller does not affect a different caller
 * [resetOnSuccess]  a successful join resets the failure counter for that caller
 * [editorContext]   a pane-scoped grant's editorContext is carried through to the JoinSession
 * [notifiesBrowser] a successful join pushes a PairingJoinedNotice to the minting browser
 * [toolbarNotice]   a whole-sheet grant notifies with a null editorContext
 * [notifyFailure]   a throwing broadcast does not fail the join
 * [notifiesTabBar]  a successful join also pushes a SetAgentActiveCommand via sendAgentActive
 * [tabBarNotifyFailure] a throwing sendAgentActive does not fail the join or suppress
 *                   sendPairingJoined
 * [labelResolved]   resolveSheetLabel returns the real AssetEntry.toView() label when the
 *                   runtime is found -- the happy path no earlier test covered, since every
 *                   other test leaves runtimeAccess.getRuntimeSheetDirect unstubbed (Mockito
 *                   default null), which is why the missing sheetLabel field went unnoticed
 * [labelNotFound]   resolveSheetLabel degrades to null (not a thrown exception) when the
 *                   runtime cannot be found via SheetRuntimeAccess.getRuntimeSheetDirect --
 *                   the same tolerant lookup the step-3b ownership check already uses, so a
 *                   join can succeed (null owner is not a mismatch) while the label is simply
 *                   absent, never fatal
 * [labelDegrades]   resolveSheetLabel degrades to null (not a thrown exception) when reading
 *                   the found runtime's label itself throws (e.g. AssetEntry.toView())
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class SheetJoinServiceTest {

   private static final long FIXED_NOW = 1_000_000L;
   private static final String ALICE_KEY = new IdentityID("alice", "host-org").convertToKey();
   private static final String BOB_KEY   = new IdentityID("bob",   "host-org").convertToKey();

   @Mock
   private SheetAgentFeature feature;

   @Mock
   private SheetRuntimeAccess runtimeAccess;

   @Mock
   private SheetAgentBroadcastService broadcast;

   private SheetPairingService pairing;
   private SheetSessionService sessions;
   private SheetJoinService svc;

   @BeforeEach
   void setUp() {
      pairing  = new SheetPairingService(() -> FIXED_NOW);
      sessions = new SheetSessionService(() -> FIXED_NOW);
      svc      = new SheetJoinService(pairing, sessions, feature, runtimeAccess, broadcast);
   }

   // ---------------------------------------------------------------------------
   // 1. validCodeSameLogicalUserGrantsSession
   // ---------------------------------------------------------------------------
   @Test
   void validCodeSameLogicalUserGrantsSession() throws PairingException {
      when(feature.isEnabled()).thenReturn(true);
      String code = pairing.mint("Worksheet/foo-7", ALICE_KEY, "sock-1", null, SheetType.WORKSHEET, null);
      Principal alice = TestPrincipals.user("alice", "host-org");

      JoinSession session = svc.join(code, alice).session();

      assertNotNull(session);
      assertEquals("Worksheet/foo-7", session.runtimeId());
      assertEquals(SheetType.WORKSHEET, session.sheetType());
   }

   // ---------------------------------------------------------------------------
   // 2. wrongCodeIsRejected
   // ---------------------------------------------------------------------------
   @Test
   void wrongCodeIsRejected() {
      when(feature.isEnabled()).thenReturn(true);
      Principal alice = TestPrincipals.user("alice", "host-org");

      assertThrows(PairingException.class, () -> svc.join("NOPE", alice));
   }

   // ---------------------------------------------------------------------------
   // 3. differentLogicalUserIsRejected
   // ---------------------------------------------------------------------------
   @Test
   void differentLogicalUserIsRejected() throws PairingException {
      when(feature.isEnabled()).thenReturn(true);
      String code = pairing.mint("Worksheet/foo-8", ALICE_KEY, "sock-2", null, SheetType.WORKSHEET, null);
      Principal bob = TestPrincipals.user("bob", "host-org");

      assertThrows(PairingException.class, () -> svc.join(code, bob));
   }

   // ---------------------------------------------------------------------------
   // 4. codeIsConsumedAfterSuccessfulJoin
   // ---------------------------------------------------------------------------
   @Test
   void codeIsConsumedAfterSuccessfulJoin() throws PairingException {
      when(feature.isEnabled()).thenReturn(true);
      String code = pairing.mint("Worksheet/foo-9", ALICE_KEY, "sock-3", null, SheetType.WORKSHEET, null);
      Principal alice = TestPrincipals.user("alice", "host-org");

      svc.join(code, alice);

      // Second attempt with the same code must fail — code was consumed.
      assertThrows(PairingException.class, () -> svc.join(code, alice));
   }

   // ---------------------------------------------------------------------------
   // 5. featureFlagOffRejectsJoinAndDoesNotConsumeCode
   // ---------------------------------------------------------------------------
   @Test
   void featureFlagOffRejectsJoinAndDoesNotConsumeCode() throws PairingException {
      when(feature.isEnabled()).thenReturn(false);
      String code = pairing.mint("Worksheet/foo-10", ALICE_KEY, "sock-4", null, SheetType.WORKSHEET, null);
      Principal alice = TestPrincipals.user("alice", "host-org");

      assertThrows(PairingException.class, () -> svc.join(code, alice));

      // Code must still be present (not consumed).
      assertNotNull(pairing.peek(code), "Code must not have been consumed when feature is off");
   }

   // ---------------------------------------------------------------------------
   // 6. viewsheetCodeGrantsSession
   // ---------------------------------------------------------------------------
   @Test
   void viewsheetCodeGrantsSession() throws PairingException {
      when(feature.isEnabled()).thenReturn(true);
      String code = pairing.mint("Viewsheet/bar-1", ALICE_KEY, "sock-5", null, SheetType.VIEWSHEET, null);
      Principal alice = TestPrincipals.user("alice", "host-org");

      JoinSession session = svc.join(code, alice).session();

      assertNotNull(session);
      assertEquals("Viewsheet/bar-1", session.runtimeId());
      assertEquals(SheetType.VIEWSHEET, session.sheetType());
   }

   // ---------------------------------------------------------------------------
   // 6b. differentLogicalUserIsRejectedForViewsheet
   // ---------------------------------------------------------------------------
   @Test
   void differentLogicalUserIsRejectedForViewsheet() throws PairingException {
      when(feature.isEnabled()).thenReturn(true);
      String code = pairing.mint("Viewsheet/bar-2", ALICE_KEY, "sock-6", null, SheetType.VIEWSHEET, null);
      Principal bob = TestPrincipals.user("bob", "host-org");

      assertThrows(PairingException.class, () -> svc.join(code, bob));
   }

   // ---------------------------------------------------------------------------
   // 7. lockoutAfterThresholdBlocksSubsequentValidJoin
   // ---------------------------------------------------------------------------
   @Test
   void lockoutAfterThresholdBlocksSubsequentValidJoin() throws PairingException {
      when(feature.isEnabled()).thenReturn(true);
      Principal alice = TestPrincipals.user("alice", "host-org");

      // 8 failed lookups (invalid code) from the same caller trip the lockout.
      for(int i = 0; i < 8; i++) {
         assertThrows(PairingException.class, () -> svc.join("NOPE", alice));
      }

      // The 9th call is rejected as rate-limited even though the code is valid — the lockout
      // blocks all attempts, not just repeats of the same failure.
      String code = pairing.mint("Worksheet/lockout-1", ALICE_KEY, "sock-lockout-1", null,
                                  SheetType.WORKSHEET, null);

      PairingException ex = assertThrows(PairingException.class, () -> svc.join(code, alice));
      assertEquals(PairingException.Kind.RATE_LIMITED, ex.getKind());
   }

   // ---------------------------------------------------------------------------
   // 8. differentThrottleKeysDoNotInterfere
   // ---------------------------------------------------------------------------
   @Test
   void differentThrottleKeysDoNotInterfere() throws PairingException {
      when(feature.isEnabled()).thenReturn(true);
      Principal alice = TestPrincipals.user("alice", "host-org");
      Principal bob = TestPrincipals.user("bob", "host-org");

      // Lock out alice.
      for(int i = 0; i < 8; i++) {
         assertThrows(PairingException.class, () -> svc.join("NOPE", alice));
      }

      String aliceCode = pairing.mint("Worksheet/lockout-2a", ALICE_KEY, "sock-lockout-2a", null,
                                       SheetType.WORKSHEET, null);
      PairingException ex = assertThrows(PairingException.class, () -> svc.join(aliceCode, alice));
      assertEquals(PairingException.Kind.RATE_LIMITED, ex.getKind());

      // bob is a different throttle key (no HTTP request bound in this unit test, so the key
      // falls back to "user:" + agent name) and must be unaffected by alice's lockout.
      String bobCode = pairing.mint("Worksheet/lockout-2b", BOB_KEY, "sock-lockout-2b", null,
                                     SheetType.WORKSHEET, null);
      JoinSession session = svc.join(bobCode, bob).session();

      assertNotNull(session);
   }

   // ---------------------------------------------------------------------------
   // 9. successfulJoinResetsFailureCounter
   // ---------------------------------------------------------------------------
   @Test
   void successfulJoinResetsFailureCounter() throws PairingException {
      when(feature.isEnabled()).thenReturn(true);
      Principal alice = TestPrincipals.user("alice", "host-org");

      // 5 failures — below the lockout threshold of 8.
      for(int i = 0; i < 5; i++) {
         assertThrows(PairingException.class, () -> svc.join("NOPE", alice));
      }

      String code = pairing.mint("Worksheet/reset-1", ALICE_KEY, "sock-reset-1", null,
                                  SheetType.WORKSHEET, null);
      JoinSession session = svc.join(code, alice).session();
      assertNotNull(session);

      // 5 more failures post-success. If the earlier 5 failures had carried over instead of
      // being reset, this would total 10 cumulative failures and alice would already be locked
      // out by now.
      for(int i = 0; i < 5; i++) {
         assertThrows(PairingException.class, () -> svc.join("NOPE", alice));
      }

      String code2 = pairing.mint("Worksheet/reset-2", ALICE_KEY, "sock-reset-2", null,
                                   SheetType.WORKSHEET, null);
      JoinSession session2 = svc.join(code2, alice).session();

      assertNotNull(session2);
   }

   // ---------------------------------------------------------------------------
   // 10. runtimeOwnedByAnotherUserIsRejected
   //
   // The core IDOR guard: a caller mints a code naming a runtime they do NOT own (the grant's
   // ownerIdentity is stamped with the CALLER's identity, so the same-logical-user check in
   // step 4 passes — attacker == attacker). Step 4b must still reject the join because the
   // runtime's real owner is a different logical user.
   // ---------------------------------------------------------------------------
   @Test
   void runtimeOwnedByAnotherUserIsRejected() throws PairingException {
      when(feature.isEnabled()).thenReturn(true);
      // Attacker (alice) mints a code for carol's runtime — grant.ownerIdentity == ALICE_KEY.
      String code = pairing.mint("Worksheet/victim-1", ALICE_KEY, "sock-idor", null,
                                 SheetType.WORKSHEET, null);
      RuntimeWorksheet victimRuntime = mock(RuntimeWorksheet.class);
      when(victimRuntime.getUser()).thenReturn(TestPrincipals.user("carol", "host-org"));
      when(runtimeAccess.getRuntimeSheetDirect(SheetType.WORKSHEET, "Worksheet/victim-1"))
         .thenReturn(victimRuntime);
      Principal alice = TestPrincipals.user("alice", "host-org");

      PairingException ex = assertThrows(PairingException.class, () -> svc.join(code, alice));
      assertEquals(PairingException.Kind.USER_MISMATCH, ex.getKind());

      // The code was still consumed (single-use), so a retry also fails.
      assertNull(pairing.peek(code));
   }

   // ---------------------------------------------------------------------------
   // 11. runtimeOwnedBySameUserIsAllowed
   //
   // The legitimate case: the runtime's real owner is the same logical user as the agent, even
   // though they are different Principal objects (browser session vs JWT-rebuilt agent).
   // ---------------------------------------------------------------------------
   @Test
   void runtimeOwnedBySameUserIsAllowed() throws PairingException {
      when(feature.isEnabled()).thenReturn(true);
      String code = pairing.mint("Worksheet/mine-1", ALICE_KEY, "sock-mine", null,
                                 SheetType.WORKSHEET, null);
      // A distinct Principal object with the same logical identity (name+org) as the agent.
      RuntimeWorksheet mineRuntime = mock(RuntimeWorksheet.class);
      when(mineRuntime.getUser()).thenReturn(TestPrincipals.user("alice", "host-org"));
      when(runtimeAccess.getRuntimeSheetDirect(SheetType.WORKSHEET, "Worksheet/mine-1"))
         .thenReturn(mineRuntime);
      Principal alice = TestPrincipals.user("alice", "host-org");

      JoinSession session = svc.join(code, alice).session();

      assertNotNull(session);
      assertEquals("Worksheet/mine-1", session.runtimeId());
   }

   // ---------------------------------------------------------------------------
   // 12. carriesTheEditorContextFromGrantToSession
   //
   // Task 5: the editorContext recorded on the PairingGrant at mint time must survive onto the
   // JoinSession that join() opens -- this is what lets every one of the four JoinResponse
   // records (script/binding/viewsheet/worksheet) report it back to the agent. Minted through
   // SheetPairingService directly rather than through REST: the REST mint endpoint
   // (SheetPairingController#mint) still hardcodes a null editorContext and only the STOMP
   // production path forwards a real one, so a pane-scoped grant can only be constructed here
   // via the service.
   // ---------------------------------------------------------------------------
   @Test
   void carriesTheEditorContextFromGrantToSession() throws PairingException {
      when(feature.isEnabled()).thenReturn(true);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      Viewsheet vs = mock(Viewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(vs.getAssembly("Chart1")).thenReturn(mock(VSAssembly.class));
      // A pairing service whose VIEWSHEET runtime lookup resolves "vs-1" to rvs, so mint's
      // editorContext validation (added in Task 4) accepts the assembly it names.
      SheetPairingService paneScopedPairing = new SheetPairingService(
         () -> FIXED_NOW, runtimeId -> null, runtimeId -> "vs-1".equals(runtimeId) ? rvs : null);
      SheetJoinService paneScopedSvc =
         new SheetJoinService(paneScopedPairing, sessions, feature, runtimeAccess, broadcast);

      EditorContext ctx = new EditorContext("assemblyMain", "Chart1", null, null);
      String code = paneScopedPairing.mint("vs-1", ALICE_KEY, "sock-pane-1", null,
                                           SheetType.VIEWSHEET, ctx);
      Principal alice = TestPrincipals.user("alice", "host-org");

      JoinSession session = paneScopedSvc.join(code, alice).session();

      assertEquals(ctx, session.editorContext());
   }

   // ---------------------------------------------------------------------------
   // notifiesBrowserOnJoin
   //
   // Minted with a VIEWSHEET editorContext, so mint()'s own validation (added for pane-scoped
   // grants) requires a real runtime lookup that resolves the named assembly -- the shared
   // `pairing` field's accessors always return null, so this uses a locally-scoped pairing
   // service configured the same way carriesTheEditorContextFromGrantToSession is.
   // ---------------------------------------------------------------------------
   @Test
   void notifiesBrowserOnJoin() throws PairingException {
      when(feature.isEnabled()).thenReturn(true);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      Viewsheet vs = mock(Viewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(vs.getAssembly("Chart1")).thenReturn(mock(VSAssembly.class));
      SheetPairingService paneScopedPairing = new SheetPairingService(
         () -> FIXED_NOW, runtimeId -> null, runtimeId -> "vs-9".equals(runtimeId) ? rvs : null);
      SheetJoinService paneScopedSvc =
         new SheetJoinService(paneScopedPairing, sessions, feature, runtimeAccess, broadcast);

      EditorContext context = new EditorContext("assemblyMain", "Chart1", null, null);
      String code = paneScopedPairing.mint("vs-9", ALICE_KEY, "sock-1", "alice-dest",
                                           SheetType.VIEWSHEET, context);
      Principal alice = TestPrincipals.user("alice", "host-org");

      paneScopedSvc.join(code, alice);

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

      JoinSession session = svc.join(code, alice).session();

      assertNotNull(session);
      assertEquals("vs-9", session.runtimeId());
   }

   // ---------------------------------------------------------------------------
   // notifiesTabBarOnJoin
   //
   // C1/C4: a successful join must also push a SetAgentActiveCommand to the tab bar, independent
   // of the pairing-domain sendPairingJoined notice above.
   // ---------------------------------------------------------------------------
   @Test
   void notifiesTabBarOnJoin() throws PairingException {
      when(feature.isEnabled()).thenReturn(true);
      String code = pairing.mint("Worksheet/foo-11", ALICE_KEY, "sock-1", "alice-dest",
                                 SheetType.WORKSHEET, null);
      Principal alice = TestPrincipals.user("alice", "host-org");

      svc.join(code, alice);

      ArgumentCaptor<JoinSession> sent = ArgumentCaptor.forClass(JoinSession.class);
      verify(broadcast).sendAgentActive(sent.capture());
      assertEquals("Worksheet/foo-11", sent.getValue().runtimeId());
   }

   // ---------------------------------------------------------------------------
   // tabBarNotifyFailureDoesNotFailTheJoin
   //
   // A broken tab-bar notification must not fail the join, and must not suppress the separate
   // sendPairingJoined notice (independent best-effort try/catch blocks).
   // ---------------------------------------------------------------------------
   @Test
   void tabBarNotifyFailureDoesNotFailTheJoin() throws PairingException {
      when(feature.isEnabled()).thenReturn(true);
      doThrow(new RuntimeException("socket gone")).when(broadcast).sendAgentActive(any());
      String code = pairing.mint("Worksheet/foo-12", ALICE_KEY, "sock-1", "alice-dest",
                                 SheetType.WORKSHEET, null);
      Principal alice = TestPrincipals.user("alice", "host-org");

      JoinSession session = svc.join(code, alice).session();

      assertNotNull(session);
      verify(broadcast).sendPairingJoined(any());
   }

   // ---------------------------------------------------------------------------
   // resolveSheetLabelReturnsRealLabelWhenRuntimeIsFound
   //
   // The happy path: SheetRuntimeAccess.getRuntimeSheetDirect finds the runtime (the same
   // tolerant lookup step 3b's ownership check uses), so resolveSheetLabel must read the real
   // AssetEntry.toView() label off it -- not the (unreachable in production) null default every
   // other test in this file gets for free from an unstubbed mock.
   // ---------------------------------------------------------------------------
   @Test
   void resolveSheetLabelReturnsRealLabelWhenRuntimeIsFound() throws PairingException {
      when(feature.isEnabled()).thenReturn(true);
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      AssetEntry entry = mock(AssetEntry.class);
      when(entry.toView()).thenReturn("Sales Analysis");
      when(rws.getEntry()).thenReturn(entry);
      when(runtimeAccess.getRuntimeSheetDirect(SheetType.WORKSHEET, "Worksheet/foo-14"))
         .thenReturn(rws);
      String code = pairing.mint("Worksheet/foo-14", ALICE_KEY, "sock-1", "alice-dest",
                                 SheetType.WORKSHEET, null);
      Principal alice = TestPrincipals.user("alice", "host-org");

      SheetJoinService.JoinOutcome outcome = svc.join(code, alice);

      assertNotNull(outcome.session());
      assertEquals("Sales Analysis", outcome.sheetLabel());
   }

   // ---------------------------------------------------------------------------
   // sheetLabelIsNullWhenRuntimeIsNotFound
   //
   // getRuntimeSheetDirect returning null (runtime not in this node's cache) must degrade the
   // label to null without throwing -- and, since step 3b's ownership check tolerates the same
   // null (see runtimeOwnedBySameUserIsAllowed's sibling default-mock behavior), the join itself
   // still succeeds. This is the case that used to reach the throwing getSheetForPairing and get
   // silently swallowed by resolveSheetLabel's catch block.
   // ---------------------------------------------------------------------------
   @Test
   void sheetLabelIsNullWhenRuntimeIsNotFound() throws PairingException {
      when(feature.isEnabled()).thenReturn(true);
      when(runtimeAccess.getRuntimeSheetDirect(SheetType.WORKSHEET, "Worksheet/foo-15"))
         .thenReturn(null);
      String code = pairing.mint("Worksheet/foo-15", ALICE_KEY, "sock-1", "alice-dest",
                                 SheetType.WORKSHEET, null);
      Principal alice = TestPrincipals.user("alice", "host-org");

      SheetJoinService.JoinOutcome outcome = svc.join(code, alice);

      assertNotNull(outcome.session());
      assertNull(outcome.sheetLabel());
   }

   // ---------------------------------------------------------------------------
   // sheetLabelDegradesToNullWhenResolutionFails
   //
   // A4/label-resolution counterpart: resolveSheetLabel must never turn a successful join into a
   // failed one, even when reading the found runtime's label itself throws.
   // ---------------------------------------------------------------------------
   @Test
   void sheetLabelDegradesToNullWhenResolutionFails() throws PairingException {
      when(feature.isEnabled()).thenReturn(true);
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      AssetEntry entry = mock(AssetEntry.class);
      when(entry.toView()).thenThrow(new RuntimeException("toView failed"));
      when(rws.getEntry()).thenReturn(entry);
      when(runtimeAccess.getRuntimeSheetDirect(SheetType.WORKSHEET, "Worksheet/foo-13"))
         .thenReturn(rws);
      String code = pairing.mint("Worksheet/foo-13", ALICE_KEY, "sock-1", "alice-dest",
                                 SheetType.WORKSHEET, null);
      Principal alice = TestPrincipals.user("alice", "host-org");

      SheetJoinService.JoinOutcome outcome = svc.join(code, alice);

      assertNotNull(outcome.session());
      assertNull(outcome.sheetLabel());
   }
}
