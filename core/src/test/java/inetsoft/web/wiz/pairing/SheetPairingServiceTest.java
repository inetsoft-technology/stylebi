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
 * Tests for SheetPairingService.
 *
 * [Mint]           mint returns a non-null, non-empty 8-char code
 * [Peek]           peek returns the grant for a valid code without consuming it
 * [Peek: repeat]   peek can be called multiple times for the same code
 * [Consume: once]  consume removes the grant (single-use)
 * [Consume: null]  consume on unknown code returns null
 * [Expired: peek]  peek returns null for expired grants
 * [Expired: consume] consume returns null for expired grants
 * [Code: format]   code uses only allowed alphabet characters
 */
@Tag("core")
class SheetPairingServiceTest {

   private final long FIXED_NOW = 1_000_000L;

   private SheetPairingService serviceAt(long now) {
      return new SheetPairingService(() -> now);
   }

   @Test
   void mintReturnsEightCharCode() {
      SheetPairingService svc = serviceAt(FIXED_NOW);
      String code = svc.mint("rt-1", "alice~;~org", "sock-1", null, SheetType.WORKSHEET);
      assertNotNull(code);
      assertEquals(8, code.length());
   }

   @Test
   void mintedCodeUsesAllowedAlphabet() {
      SheetPairingService svc = serviceAt(FIXED_NOW);
      String code = svc.mint("rt-1", "alice~;~org", "sock-1", null, SheetType.WORKSHEET);
      for (char c : code.toCharArray()) {
         assertTrue(SheetPairingService.ALPHABET.indexOf(c) >= 0,
                    "Unexpected char: " + c);
      }
   }

   @Test
   void peekReturnsGrantForValidCode() {
      SheetPairingService svc = serviceAt(FIXED_NOW);
      String code = svc.mint("rt-2", "bob~;~org", "sock-2", null, SheetType.VIEWSHEET);
      PairingGrant grant = svc.peek(code);
      assertNotNull(grant);
      assertEquals("rt-2", grant.runtimeId());
      assertEquals("bob~;~org", grant.ownerIdentity());
      assertEquals(SheetType.VIEWSHEET, grant.sheetType());
   }

   @Test
   void peekDoesNotConsumeGrant() {
      SheetPairingService svc = serviceAt(FIXED_NOW);
      String code = svc.mint("rt-3", "carol~;~org", "sock-3", null, SheetType.WORKSHEET);
      svc.peek(code);
      assertNotNull(svc.peek(code), "peek should be non-destructive");
   }

   @Test
   void consumeRemovesGrant() {
      SheetPairingService svc = serviceAt(FIXED_NOW);
      String code = svc.mint("rt-4", "dave~;~org", "sock-4", null, SheetType.WORKSHEET);
      PairingGrant first = svc.consume(code);
      assertNotNull(first);
      assertNull(svc.consume(code), "second consume must return null (single-use)");
   }

   @Test
   void consumeUnknownCodeReturnsNull() {
      SheetPairingService svc = serviceAt(FIXED_NOW);
      assertNull(svc.consume("XXXXXXXX"));
   }

   @Test
   void peekReturnsNullForExpiredGrant() {
      long mintTime = FIXED_NOW;
      SheetPairingService svc = serviceAt(mintTime);
      String code = svc.mint("rt-5", "eve~;~org", "sock-5", null, SheetType.WORKSHEET);
      // advance clock beyond TTL
      SheetPairingService svcLater = new SheetPairingService(
         () -> mintTime + SheetPairingService.TTL_MILLIS + 1, svc);
      assertNull(svcLater.peek(code));
   }

   @Test
   void consumeReturnsNullForExpiredGrant() {
      long mintTime = FIXED_NOW;
      SheetPairingService svc = serviceAt(mintTime);
      String code = svc.mint("rt-6", "frank~;~org", "sock-6", null, SheetType.WORKSHEET);
      SheetPairingService svcLater = new SheetPairingService(
         () -> mintTime + SheetPairingService.TTL_MILLIS + 1, svc);
      assertNull(svcLater.consume(code));
   }
}
