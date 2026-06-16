/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.wiz.worksheet;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class PairingGrantTest {
   @Test
   void exposesBoundFieldsAndExpiry() {
      PairingGrant g = new PairingGrant(
         "ABC123", "Worksheet/foo-7", "alice~;~host-org", "stomp-sess-1", 1000L, 60_000L);
      assertEquals("ABC123", g.code());
      assertEquals("Worksheet/foo-7", g.runtimeId());
      assertEquals("alice~;~host-org", g.ownerIdentity());
      assertEquals("stomp-sess-1", g.socketSessionId());
      assertFalse(g.isExpired(1000L + 59_999L));
      assertTrue(g.isExpired(1000L + 60_001L));
   }
}
