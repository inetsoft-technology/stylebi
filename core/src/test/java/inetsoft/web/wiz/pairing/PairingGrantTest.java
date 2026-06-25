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
 * Tests for PairingGrant record.
 *
 * [Fields]      constructor stores all fields accessibly
 * [TTL: live]   isExpired returns false before TTL elapses
 * [TTL: dead]   isExpired returns true after TTL elapses
 */
@Tag("core")
class PairingGrantTest {

   @Test
   void fieldsAreAccessible() {
      long now = System.currentTimeMillis();
      PairingGrant grant = new PairingGrant("CODE1", "rt-001", "alice~;~defaultOrg",
                                             "sock-123", "alice~;~defaultOrg[sec]@127.0.0.1",
                                             now, 5000L, SheetType.WORKSHEET);
      assertEquals("CODE1", grant.code());
      assertEquals("rt-001", grant.runtimeId());
      assertEquals("alice~;~defaultOrg", grant.ownerIdentity());
      assertEquals("sock-123", grant.socketSessionId());
      assertEquals("alice~;~defaultOrg[sec]@127.0.0.1", grant.socketUserName());
      assertEquals(now, grant.createdAt());
      assertEquals(5000L, grant.ttlMillis());
      assertEquals(SheetType.WORKSHEET, grant.sheetType());
   }

   @Test
   void isExpiredReturnsFalseBeforeTtl() {
      long now = 1_000_000L;
      PairingGrant grant = new PairingGrant("C", "r", "o", "s", null, now, 5000L, SheetType.VIEWSHEET);
      assertFalse(grant.isExpired(now + 4999L));
   }

   @Test
   void isExpiredReturnsTrueAfterTtl() {
      long now = 1_000_000L;
      PairingGrant grant = new PairingGrant("C", "r", "o", "s", null, now, 5000L, SheetType.VIEWSHEET);
      assertTrue(grant.isExpired(now + 5001L));
   }
}
