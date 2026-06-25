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
 * [viewsheet]       viewsheet code -> JoinSession with VIEWSHEET sheetType
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class SheetJoinServiceTest {

   private static final long FIXED_NOW = 1_000_000L;
   private static final String ALICE_KEY = new IdentityID("alice", "host-org").convertToKey();
   private static final String BOB_KEY   = new IdentityID("bob",   "host-org").convertToKey();

   @Mock
   private SheetAgentFeature feature;

   private SheetPairingService pairing;
   private SheetSessionService sessions;
   private SheetJoinService svc;

   @BeforeEach
   void setUp() {
      pairing  = new SheetPairingService(() -> FIXED_NOW);
      sessions = new SheetSessionService(() -> FIXED_NOW);
      svc      = new SheetJoinService(pairing, sessions, feature);
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
   // 6. viewsheetCodeGrantsViewsheetSession
   // ---------------------------------------------------------------------------
   @Test
   void viewsheetCodeGrantsViewsheetSession() throws PairingException {
      when(feature.isEnabled()).thenReturn(true);
      String code = pairing.mint("Viewsheet/bar-1", ALICE_KEY, "sock-5", null, SheetType.VIEWSHEET);
      Principal alice = TestPrincipals.user("alice", "host-org");

      JoinSession session = svc.join(code, alice);

      assertNotNull(session);
      assertEquals(SheetType.VIEWSHEET, session.sheetType());
   }
}
