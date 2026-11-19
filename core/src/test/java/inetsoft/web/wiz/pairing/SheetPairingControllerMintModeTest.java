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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * C1 / C3 / C4 / C6 on the mint: the viewer button mints exactly what a Composer toolbar does, an
 * extra "mode" property in the STOMP frame is ignored and never read, the REST mint stays
 * test-only, and the feature flag stays the single switch.
 */
@Tag("core")
class SheetPairingControllerMintModeTest {
   private final Principal alice = TestPrincipals.user("alice", "host-org");

   private SheetPairingController controller(SheetPairingService pairing, boolean featureOn,
                                             boolean restMint)
   {
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(featureOn);
      return new SheetPairingController(pairing, new SheetSessionService(), feature,
                                        mock(SheetAgentBroadcastService.class),
                                        mock(SheetJoinService.class), restMint);
   }

   private static SimpMessageHeaderAccessor accessor() {
      SimpMessageHeaderAccessor a = mock(SimpMessageHeaderAccessor.class);
      when(a.getSessionId()).thenReturn("stomp-1");
      return a;
   }

   /** Spring's STOMP converter tolerates unknown properties; the frame below is what a modified client sends. */
   private static SheetPairingController.MintRequest parse(String frame) throws Exception {
      ObjectMapper m = new ObjectMapper().configure(
         DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
      return m.readValue(frame, SheetPairingController.MintRequest.class);
   }

   @Test
   void extraModeFieldsInTheMintFrameAreIgnoredAndTheGrantIsIdentical() throws Exception {
      SheetPairingService plainPairing = new SheetPairingService();
      SheetPairingService craftedPairing = new SheetPairingService();

      String plain = controller(plainPairing, true, false).mintViaSocket(
         parse("{\"runtimeId\":\"vs-1\",\"sheetType\":\"VIEWSHEET\"}"), alice, accessor()).code();
      String crafted = controller(craftedPairing, true, false).mintViaSocket(
         parse("{\"runtimeId\":\"vs-1\",\"sheetType\":\"VIEWSHEET\"," +
               "\"mode\":\"PAIRED\",\"connectionMode\":\"PAIRED\",\"designer\":true," +
               "\"viewer\":false}"), alice, accessor()).code();

      PairingGrant a = plainPairing.peek(plain);
      PairingGrant b = craftedPairing.peek(crafted);
      assertNotNull(a);
      assertNotNull(b);
      assertEquals(a.runtimeId(), b.runtimeId());
      assertEquals(a.sheetType(), b.sheetType());
      assertEquals(a.ownerIdentity(), b.ownerIdentity());
      assertEquals(a.socketSessionId(), b.socketSessionId());
      assertEquals(a.editorContext(), b.editorContext());
      assertNull(b.editorContext());
   }

   @Test
   void theMintReplyShapeIsUnchanged() {
      assertArrayEquals(new String[] { "code", "error" },
         java.util.Arrays.stream(SheetPairingController.MintResponse.class.getRecordComponents())
            .map(java.lang.reflect.RecordComponent::getName).toArray(String[]::new));
      assertArrayEquals(new String[] { "runtimeId", "sheetType", "editorContext" },
         java.util.Arrays.stream(SheetPairingController.MintRequest.class.getRecordComponents())
            .map(java.lang.reflect.RecordComponent::getName).toArray(String[]::new));
   }

   @Test
   void restMintIsStillNotFoundUnlessExplicitlyEnabled() {
      SheetPairingController off = controller(new SheetPairingService(), true, false);

      ResponseStatusException e = assertThrows(ResponseStatusException.class,
         () -> off.mint("vs-1", "stomp-1", SheetType.VIEWSHEET, alice));

      assertEquals(404, e.getStatusCode().value());
   }

   @Test
   void featureOffStillRefusesTheStompMintWithTheExistingError() {
      SheetPairingController c = controller(new SheetPairingService(), false, false);

      SheetPairingController.MintResponse r = c.mintViaSocket(
         new SheetPairingController.MintRequest("vs-1", SheetType.VIEWSHEET, null), alice,
         accessor());

      assertNull(r.code());
      assertTrue(r.error().startsWith("Sheet agent pairing is disabled"), r.error());
   }

   @Test
   void theViewerAndTheToolbarMintTheSameWholeSheetGrant() throws Exception {
      SheetPairingService pairing = new SheetPairingService();
      SheetPairingController c = controller(pairing, true, false);

      String code = c.mintViaSocket(
         new SheetPairingController.MintRequest("vs-1", SheetType.VIEWSHEET, null), alice,
         accessor()).code();

      PairingGrant g = pairing.peek(code);
      assertEquals("vs-1", g.runtimeId());
      assertEquals(SheetType.VIEWSHEET, g.sheetType());
      assertNull(g.editorContext());
      assertEquals("stomp-1", g.socketSessionId());
   }
}
