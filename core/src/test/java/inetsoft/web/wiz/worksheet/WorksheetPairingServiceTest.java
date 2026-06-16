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

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class WorksheetPairingServiceTest {
   private long now;
   private WorksheetPairingService svc;

   @BeforeEach
   void setUp() {
      now = 1_000_000L;
      svc = new WorksheetPairingService(() -> now);   // injectable clock
   }

   @Test
   void mintProducesLookupableCode() {
      String code = svc.mint("Worksheet/foo-7", "alice~;~host-org", "stomp-1");
      assertNotNull(code);
      PairingGrant g = svc.peek(code);
      assertEquals("Worksheet/foo-7", g.runtimeId());
      assertEquals("alice~;~host-org", g.ownerIdentity());
      assertEquals("stomp-1", g.socketSessionId());
   }

   @Test
   void consumeIsSingleUse() {
      String code = svc.mint("Worksheet/foo-7", "alice~;~host-org", "stomp-1");
      assertNotNull(svc.consume(code));
      assertNull(svc.consume(code), "second consume must return null");
   }

   @Test
   void expiredCodeIsNotConsumable() {
      String code = svc.mint("Worksheet/foo-7", "alice~;~host-org", "stomp-1");
      now += WorksheetPairingService.TTL_MILLIS + 1;
      assertNull(svc.consume(code), "expired code must not consume");
   }

   @Test
   void nullCodeDoesNotThrow() {
      assertNull(svc.consume(null), "consume(null) must return null, not throw");
      assertNull(svc.peek(null), "peek(null) must return null, not throw");
   }
}
