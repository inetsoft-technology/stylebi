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

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import inetsoft.util.PasswordEncryption;
import inetsoft.web.wiz.pairing.SessionEstablishController;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link WizAuthCallbackController#pickup(String)} and, since Lane D
 * (design doc section 8), the {@code grant=portal} branch of
 * {@link WizAuthCallbackController#callback}.
 *
 * <p>The pickup-only cases need no external dependencies (the pending-token store is an
 * in-memory map owned by the instance). The callback cases additionally need a real RSA key
 * pair to sign a token {@code verifySignature} will accept -- {@link PasswordEncryption} is
 * mocked statically to supply one, the same key pair a real SSO token would be signed/verified
 * with.
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

   private final SessionEstablishController sessionEstablish = mock(SessionEstablishController.class);
   private final WizAuthCallbackController controller = new WizAuthCallbackController(sessionEstablish);

   private KeyPair keyPair;
   private MockedStatic<PasswordEncryption> passwordEncryptionMock;

   @BeforeEach
   void setUpKeyPair() throws Exception {
      KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
      gen.initialize(2048);
      keyPair = gen.generateKeyPair();

      PasswordEncryption pe = mock(PasswordEncryption.class);
      when(pe.getSSOKeyPair()).thenReturn(keyPair);
      passwordEncryptionMock = mockStatic(PasswordEncryption.class);
      passwordEncryptionMock.when(PasswordEncryption::newInstance).thenReturn(pe);
   }

   @AfterEach
   void tearDownKeyPair() {
      passwordEncryptionMock.close();
   }

   private String signedToken(String subject) throws Exception {
      JWTClaimsSet claims = new JWTClaimsSet.Builder()
         .subject(subject)
         .expirationTime(new Date(System.currentTimeMillis() + 600_000))
         .build();
      SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
      jwt.sign(new RSASSASigner((RSAPrivateKey) keyPair.getPrivate()));
      return jwt.serialize();
   }

   private HttpServletResponse mockResponse() throws Exception {
      HttpServletResponse response = mock(HttpServletResponse.class);
      when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
      return response;
   }

   @Test
   void callbackWithPortalGrantCallsEstablishAndPickupSurfacesIt() throws Exception {
      String token = signedToken("alice~;~host-org");
      SessionEstablishController.JoinResponse established = new SessionEstablishController.JoinResponse(
         "sess-established-1", null, "alice~;~host-org", null, null, null, null);
      when(sessionEstablish.establishForOwner("alice~;~host-org")).thenReturn(established);

      controller.callback("nonce-portal-grant01", token, "portal", mockResponse());
      ResponseEntity<Map<String, Object>> pickup = controller.pickup("nonce-portal-grant01");

      assertEquals(HttpStatus.OK, pickup.getStatusCode());
      assertEquals(established, pickup.getBody().get("portalSession"));
      verify(sessionEstablish).establishForOwner("alice~;~host-org");
   }

   @Test
   void callbackWithoutGrantNeverCallsEstablishAndOmitsThePortalSessionKeyEntirely() throws Exception {
      String token = signedToken("bob~;~host-org");

      controller.callback("nonce-no-grant-abcd1", token, null, mockResponse());
      ResponseEntity<Map<String, Object>> pickup = controller.pickup("nonce-no-grant-abcd1");

      assertEquals(HttpStatus.OK, pickup.getStatusCode());
      assertFalse(pickup.getBody().containsKey("portalSession"),
                  "a plain login must never carry a portalSession key -- present-but-null is " +
                  "still a behavior change from today's shape");
      verifyNoInteractions(sessionEstablish);
   }

   @Test
   void callbackWithNonPortalGrantValueIsTreatedIdenticallyToAbsent() throws Exception {
      String token = signedToken("carol~;~host-org");

      controller.callback("nonce-bogus-grant001", token, "bogus", mockResponse());
      ResponseEntity<Map<String, Object>> pickup = controller.pickup("nonce-bogus-grant001");

      assertFalse(pickup.getBody().containsKey("portalSession"));
      verifyNoInteractions(sessionEstablish);
   }

   @Test
   void callbackSwallowsEstablishFailureAndStillStashesTheLoginNormally() throws Exception {
      String token = signedToken("dave~;~host-org");
      when(sessionEstablish.establishForOwner(anyString())).thenThrow(new RuntimeException("boom"));

      controller.callback("nonce-establish-fail1", token, "portal", mockResponse());
      ResponseEntity<Map<String, Object>> pickup = controller.pickup("nonce-establish-fail1");

      assertEquals(HttpStatus.OK, pickup.getStatusCode());
      assertEquals(token, pickup.getBody().get("accessToken"),
                   "the callback's own success must not depend on establish succeeding");
      assertFalse(pickup.getBody().containsKey("portalSession"));
   }

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
