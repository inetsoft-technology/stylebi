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
 * Tests for SheetSessionService.
 *
 * [Open]             open returns a non-null session with a token
 * [Resolve: reuse]   resolve after open returns the same session (not single-use)
 * [Resolve: wrong]   resolve rejects a different ownerIdentity
 * [Resolve: unknown] resolve on unknown token returns null
 * [Resolve: expired] resolve returns null for expired session
 * [Resolve: refresh] resolve refreshes TTL (lastAccess advances)
 * [Close]            close invalidates the token
 */
@Tag("core")
class SheetSessionServiceTest {

   private static final long FIXED_NOW = 2_000_000L;

   private SheetSessionService serviceAt(long now) {
      return new SheetSessionService(() -> now);
   }

   @Test
   void openReturnsSessionWithToken() {
      SheetSessionService svc = serviceAt(FIXED_NOW);
      JoinSession session = svc.open("rt-1", "alice~;~org", SheetType.WORKSHEET, null, null);
      assertNotNull(session);
      assertNotNull(session.sessionToken());
      assertFalse(session.sessionToken().isEmpty());
   }

   @Test
   void resolveReturnsSessionMultipleTimes() {
      SheetSessionService svc = serviceAt(FIXED_NOW);
      JoinSession session = svc.open("rt-1", "alice~;~org", SheetType.WORKSHEET, null, null);
      String token = session.sessionToken();

      JoinSession r1 = svc.resolve(token, "alice~;~org");
      JoinSession r2 = svc.resolve(token, "alice~;~org");
      assertNotNull(r1);
      assertNotNull(r2, "session must be reusable (not single-use)");
      assertEquals(token, r1.sessionToken());
   }

   @Test
   void resolveRejectsWrongUser() {
      SheetSessionService svc = serviceAt(FIXED_NOW);
      JoinSession session = svc.open("rt-2", "alice~;~org", SheetType.WORKSHEET, null, null);
      assertNull(svc.resolve(session.sessionToken(), "mallory~;~org"));
   }

   @Test
   void resolveUnknownTokenReturnsNull() {
      SheetSessionService svc = serviceAt(FIXED_NOW);
      assertNull(svc.resolve("NONEXISTENT_TOKEN", "alice~;~org"));
   }

   @Test
   void resolveExpiredSessionReturnsNull() {
      SheetSessionService svc = serviceAt(FIXED_NOW);
      JoinSession session = svc.open("rt-3", "alice~;~org", SheetType.WORKSHEET, null, null);
      String token = session.sessionToken();

      // advance clock past TTL
      SheetSessionService svcLater = new SheetSessionService(
         () -> FIXED_NOW + SheetSessionService.TTL_MILLIS + 1, svc);
      assertNull(svcLater.resolve(token, "alice~;~org"));
   }

   @Test
   void resolveRefreshesTtl() {
      long[] clock = { FIXED_NOW };
      SheetSessionService svc = new SheetSessionService(() -> clock[0]);
      JoinSession session = svc.open("rt-4", "bob~;~org", SheetType.VIEWSHEET, null, null);
      String token = session.sessionToken();

      // advance to just before TTL
      clock[0] = FIXED_NOW + SheetSessionService.TTL_MILLIS - 100;
      JoinSession resolved = svc.resolve(token, "bob~;~org");
      assertNotNull(resolved);

      // advance beyond original TTL but within refreshed TTL
      clock[0] = FIXED_NOW + SheetSessionService.TTL_MILLIS + 500;
      JoinSession stillAlive = svc.resolve(token, "bob~;~org");
      assertNotNull(stillAlive, "TTL should have been refreshed on last resolve");
   }

   @Test
   void closeInvalidatesToken() {
      SheetSessionService svc = serviceAt(FIXED_NOW);
      JoinSession session = svc.open("rt-5", "carol~;~org", SheetType.WORKSHEET, null, null);
      String token = session.sessionToken();
      svc.close(token);
      assertNull(svc.resolve(token, "carol~;~org"));
   }
}
