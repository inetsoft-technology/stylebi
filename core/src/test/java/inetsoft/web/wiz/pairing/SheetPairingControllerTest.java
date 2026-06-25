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
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
class SheetPairingControllerTest {

   @Test
   void restMintBindsOpenRuntimeToOwnerAndSocketSession() {
      SheetPairingService pairing = new SheetPairingService();
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);
      SheetPairingController c = new SheetPairingController(pairing, feature, true);
      Principal owner = TestPrincipals.user("alice", "host-org");

      String code = c.mint("Worksheet/foo-7", "stomp-1", SheetType.WORKSHEET, owner).code();

      PairingGrant g = pairing.peek(code);
      assertNotNull(g);
      assertEquals("Worksheet/foo-7", g.runtimeId());
      assertEquals("alice~;~host-org", g.ownerIdentity());
      assertEquals("stomp-1", g.socketSessionId());
      assertEquals(SheetType.WORKSHEET, g.sheetType());
   }

   @Test
   void restMintRefusedWhenFeatureOff() {
      SheetPairingService pairing = new SheetPairingService();
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(false);
      SheetPairingController c = new SheetPairingController(pairing, feature, true);
      Principal owner = TestPrincipals.user("alice", "host-org");

      assertThrows(ResponseStatusException.class,
                   () -> c.mint("Worksheet/foo-7", "stomp-1", SheetType.WORKSHEET, owner));
   }

   @Test
   void restMintRejectsNullPrincipal() {
      SheetPairingService pairing = new SheetPairingService();
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);
      SheetPairingController c = new SheetPairingController(pairing, feature, true);

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> c.mint("Worksheet/foo-7", "stomp-1", SheetType.WORKSHEET, null));
      assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
   }

   @Test
   void stompMintDerivesSessionFromAccessor() {
      SheetPairingService pairing = new SheetPairingService();
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);
      SheetPairingController c = new SheetPairingController(pairing, feature, true);
      Principal owner = TestPrincipals.user("alice", "host-org");

      // Build a SimpMessageHeaderAccessor with a known session id
      SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
      accessor.setSessionId("derived-stomp-9");

      SheetPairingController.MintRequest req =
         new SheetPairingController.MintRequest("Worksheet/foo-7", SheetType.WORKSHEET);
      String code = c.mintViaSocket(req, owner, accessor).code();

      assertEquals("derived-stomp-9", pairing.peek(code).socketSessionId());
   }

   @Test
   void stompMintRefusedWhenFeatureOff() {
      SheetPairingService pairing = new SheetPairingService();
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(false);
      SheetPairingController c = new SheetPairingController(pairing, feature, true);
      Principal owner = TestPrincipals.user("alice", "host-org");
      SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
      accessor.setSessionId("stomp-x");

      SheetPairingController.MintResponse resp =
         c.mintViaSocket(new SheetPairingController.MintRequest("WS/1", SheetType.WORKSHEET),
                         owner, accessor);
      assertNull(resp.code(), "code should be null when feature is off");
      assertNotNull(resp.error(), "error should be non-null when feature is off");
   }
}
