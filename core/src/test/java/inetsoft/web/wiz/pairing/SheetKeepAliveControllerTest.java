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
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug 81017: pins the keep-alive wire contract the plugin depends on. A missing runtime or session
 * must answer 404 with errorCode SESSION_EXPIRED, which is how the plugin tells "this session is
 * gone" apart from "this server has no such route".
 */
@Tag("core")
class SheetKeepAliveControllerTest {
   private static final Principal ALICE = TestPrincipals.user("alice", "org");

   private final SheetAgentFeature feature = mock(SheetAgentFeature.class);
   private final SheetKeepAliveService service = mock(SheetKeepAliveService.class);
   private final SheetKeepAliveController controller =
      new SheetKeepAliveController(feature, service);

   @Test
   void okAnswersAlive() throws Exception {
      when(feature.isEnabled()).thenReturn(true);

      assertEquals(Map.of("alive", true), controller.keepAlive("tok", ALICE));
      verify(service).keepAlive("tok", ALICE);
   }

   @Test
   void missingRuntimeIsHttp404WithSessionExpiredCode() throws Exception {
      when(feature.isEnabled()).thenReturn(true);
      PairingException e = new PairingException(PairingException.Kind.SESSION_EXPIRED, "gone");
      doThrow(e).when(service).keepAlive("tok", ALICE);

      PairingException thrown =
         assertThrows(PairingException.class, () -> controller.keepAlive("tok", ALICE));
      ResponseEntity<Map<String, String>> response = controller.handlePairingException(thrown);

      assertEquals(404, response.getStatusCode().value());
      assertEquals("SESSION_EXPIRED", response.getBody().get("errorCode"));
   }

   @Test
   void disabledFeatureIsForbiddenAndNeverTouchesTheRuntime() {
      when(feature.isEnabled()).thenReturn(false);

      assertThrows(ResponseStatusException.class, () -> controller.keepAlive("tok", ALICE));
      verifyNoInteractions(service);
   }
}
