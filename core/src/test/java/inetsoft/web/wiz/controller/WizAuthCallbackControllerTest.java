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
package inetsoft.web.wiz.controller;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link WizAuthCallbackController#pickup(String)}.
 *
 * <p>The controller has no external dependencies (the pending-token store is an in-memory
 * map owned by the instance), so it can be exercised directly without mocks or a Spring
 * context.
 *
 * [blankNonce]           blank nonce -> badRequest()
 * [tooShortNonce]        non-blank, valid charset, < 16 chars -> badRequest()
 * [invalidCharsSpace]    >= 16 chars but contains a space -> badRequest()
 * [invalidCharsScript]   >= 16 chars but contains "<script>"-style characters -> badRequest()
 * [wellFormedUnknown]    well-formed (>= 16 chars, valid charset) but unknown nonce ->
 *                        notFound(), proving the format check does not overreject
 */
@Tag("core")
class WizAuthCallbackControllerTest {

   private final WizAuthCallbackController controller = new WizAuthCallbackController();

   @Test
   void blankNonceIsRejected() {
      ResponseEntity<Map<String, Object>> response = controller.pickup("   ");

      assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
   }

   @Test
   void emptyNonceIsRejected() {
      ResponseEntity<Map<String, Object>> response = controller.pickup("");

      assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
   }

   @Test
   void tooShortNonceIsRejected() {
      // 15 chars, valid charset (< MIN_NONCE_LENGTH of 16).
      ResponseEntity<Map<String, Object>> response = controller.pickup("Abc123_-Xyz9876");

      assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
   }

   @Test
   void nonceWithSpaceIsRejected() {
      // >= 16 chars but contains a space, which is outside [A-Za-z0-9_-].
      String nonce = "Abc123 456789012";
      assertEquals(16, nonce.length());

      ResponseEntity<Map<String, Object>> response = controller.pickup(nonce);

      assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
   }

   @Test
   void nonceWithScriptTagCharsIsRejected() {
      // >= 16 chars, contains characters outside the allowed nonce charset.
      String nonce = "<script>alert(1)</script>";

      ResponseEntity<Map<String, Object>> response = controller.pickup(nonce);

      assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
   }

   @Test
   void wellFormedButUnknownNonceIsNotFoundRatherThanBadRequest() {
      // 32 chars, valid charset, but no pending token was ever stored for it. This proves the
      // well-formed check passes and the request fails for a *different* reason (unknown/expired
      // token), not because the format check is overly strict.
      String nonce = "Abcdefgh12345678_-ABCDEFGH123456";
      assertEquals(32, nonce.length());

      ResponseEntity<Map<String, Object>> response = controller.pickup(nonce);

      assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
   }
}
