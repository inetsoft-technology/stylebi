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

import inetsoft.sree.security.IdentityID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Tests for SheetJoinService.
 *
 * [validCode]       feature on, valid code, same user -> JoinSession opened
 * [wrongCode]       join with unknown code -> PairingException
 * [wrongUser]       mint for alice, join as bob -> PairingException
 * [codeConsumed]    second join with same code -> PairingException
 * [featureOff]      feature off -> PairingException; code not consumed
 * [viewsheet]       viewsheet code -> PairingException (not yet supported)
 * [lockout]         8 failed lookups from the same caller -> 9th call (even with a valid code)
 *                   throws PairingException with Kind.RATE_LIMITED
 * [differentKeys]   lockout of one caller does not affect a different caller
 * [resetOnSuccess]  a successful join resets the failure counter for that caller
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

   private SheetPairingService pairing;
   private SheetSessionService sessions;
   private SheetJoinService svc;

   @BeforeEach
   void setUp() {
      pairing  = new SheetPairingService(() -> FIXED_NOW);
      sessions = new SheetSessionService(() -> FIXED_NOW);
      svc      = new SheetJoinService(pairing, sessions, feature, runtimeAccess);
   }

   // ---------------------------------------------------------------------------
   // 1. validCodeSameLogicalUserGrantsSession
   // ---------------------------------------------------------------------------
   @Test
   void validCodeSameLogicalUserGrantsSession() throws PairingException {
      when(feature.isEnabled()).thenReturn(true);
      String code = pairing.mint("Worksheet/foo-7", ALICE_KEY, "sock-1", null, SheetType.WORKSHEET);
      Principal alice = TestPrincipals.user("alice", "host-org");

      JoinSession session = svc.join(code, alice);

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
   void differentLogicalUserIsRejected() {
      when(feature.isEnabled()).thenReturn(true);
      String code = pairing.mint("Worksheet/foo-8", ALICE_KEY, "sock-2", null, SheetType.WORKSHEET);
      Principal bob = TestPrincipals.user("bob", "host-org");

      assertThrows(PairingException.class, () -> svc.join(code, bob));
   }

   // ---------------------------------------------------------------------------
   // 4. codeIsConsumedAfterSuccessfulJoin
   // ---------------------------------------------------------------------------
   @Test
   void codeIsConsumedAfterSuccessfulJoin() throws PairingException {
      when(feature.isEnabled()).thenReturn(true);
      String code = pairing.mint("Worksheet/foo-9", ALICE_KEY, "sock-3", null, SheetType.WORKSHEET);
      Principal alice = TestPrincipals.user("alice", "host-org");

      svc.join(code, alice);

      // Second attempt with the same code must fail — code was consumed.
      assertThrows(PairingException.class, () -> svc.join(code, alice));
   }

   // ---------------------------------------------------------------------------
   // 5. featureFlagOffRejectsJoinAndDoesNotConsumeCode
   // ---------------------------------------------------------------------------
   @Test
   void featureFlagOffRejectsJoinAndDoesNotConsumeCode() {
      when(feature.isEnabled()).thenReturn(false);
      String code = pairing.mint("Worksheet/foo-10", ALICE_KEY, "sock-4", null, SheetType.WORKSHEET);
      Principal alice = TestPrincipals.user("alice", "host-org");

      assertThrows(PairingException.class, () -> svc.join(code, alice));

      // Code must still be present (not consumed).
      assertNotNull(pairing.peek(code), "Code must not have been consumed when feature is off");
   }

   // ---------------------------------------------------------------------------
   // 6. viewsheetCodeIsRejected
   // ---------------------------------------------------------------------------
   @Test
   void viewsheetCodeIsRejected() {
      when(feature.isEnabled()).thenReturn(true);
      String code = pairing.mint("Viewsheet/bar-1", ALICE_KEY, "sock-5", null, SheetType.VIEWSHEET);
      Principal alice = TestPrincipals.user("alice", "host-org");

      assertThrows(PairingException.class, () -> svc.join(code, alice));
   }

   // ---------------------------------------------------------------------------
   // 7. lockoutAfterThresholdBlocksSubsequentValidJoin
   // ---------------------------------------------------------------------------
   @Test
   void lockoutAfterThresholdBlocksSubsequentValidJoin() {
      when(feature.isEnabled()).thenReturn(true);
      Principal alice = TestPrincipals.user("alice", "host-org");

      // 8 failed lookups (invalid code) from the same caller trip the lockout.
      for(int i = 0; i < 8; i++) {
         assertThrows(PairingException.class, () -> svc.join("NOPE", alice));
      }

      // The 9th call is rejected as rate-limited even though the code is valid — the lockout
      // blocks all attempts, not just repeats of the same failure.
      String code = pairing.mint("Worksheet/lockout-1", ALICE_KEY, "sock-lockout-1", null,
                                  SheetType.WORKSHEET);

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
                                       SheetType.WORKSHEET);
      PairingException ex = assertThrows(PairingException.class, () -> svc.join(aliceCode, alice));
      assertEquals(PairingException.Kind.RATE_LIMITED, ex.getKind());

      // bob is a different throttle key (no HTTP request bound in this unit test, so the key
      // falls back to "user:" + agent name) and must be unaffected by alice's lockout.
      String bobCode = pairing.mint("Worksheet/lockout-2b", BOB_KEY, "sock-lockout-2b", null,
                                     SheetType.WORKSHEET);
      JoinSession session = svc.join(bobCode, bob);

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
                                  SheetType.WORKSHEET);
      JoinSession session = svc.join(code, alice);
      assertNotNull(session);

      // 5 more failures post-success. If the earlier 5 failures had carried over instead of
      // being reset, this would total 10 cumulative failures and alice would already be locked
      // out by now.
      for(int i = 0; i < 5; i++) {
         assertThrows(PairingException.class, () -> svc.join("NOPE", alice));
      }

      String code2 = pairing.mint("Worksheet/reset-2", ALICE_KEY, "sock-reset-2", null,
                                   SheetType.WORKSHEET);
      JoinSession session2 = svc.join(code2, alice);

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
   void runtimeOwnedByAnotherUserIsRejected() {
      when(feature.isEnabled()).thenReturn(true);
      // Attacker (alice) mints a code for carol's runtime — grant.ownerIdentity == ALICE_KEY.
      String code = pairing.mint("Worksheet/victim-1", ALICE_KEY, "sock-idor", null,
                                 SheetType.WORKSHEET);
      when(runtimeAccess.getRuntimeOwner(SheetType.WORKSHEET, "Worksheet/victim-1"))
         .thenReturn(TestPrincipals.user("carol", "host-org"));
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
                                 SheetType.WORKSHEET);
      // A distinct Principal object with the same logical identity (name+org) as the agent.
      when(runtimeAccess.getRuntimeOwner(SheetType.WORKSHEET, "Worksheet/mine-1"))
         .thenReturn(TestPrincipals.user("alice", "host-org"));
      Principal alice = TestPrincipals.user("alice", "host-org");

      JoinSession session = svc.join(code, alice);

      assertNotNull(session);
      assertEquals("Worksheet/mine-1", session.runtimeId());
   }
}
