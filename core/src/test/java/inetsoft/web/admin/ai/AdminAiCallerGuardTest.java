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
package inetsoft.web.admin.ai;

import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the CSRF backstop for the admin-chat endpoints.
 *
 * <p>These endpoints live under {@code /api/wiz/**}, which {@code CSRFFilter} exempts from CSRF
 * protection on the assumption that wiz traffic is bearer-JWT-authenticated. That assumption is
 * not enforced by the filters: {@code WizServiceAuthenticationFilter} falls through when no bearer
 * header is present, and {@code RequestPrincipalFilter} then resolves an ordinary browser session
 * principal into {@code getUserPrincipal()}. Without this guard, a site administrator holding a
 * live StyleBI session could be made to mutate server properties by a cross-site request.
 *
 * <p>Checking the request's {@code Authorization} header — rather than the principal's {@code wiz}
 * property — is deliberate: {@code WizServiceAuthenticationFilter} persists its principal into the
 * HTTP session, so that property proves only that the *session* once presented a JWT, not that
 * *this request* did. A cross-site form POST cannot set an {@code Authorization} header.
 */
@Tag("core")
class AdminAiCallerGuardTest {
   @AfterEach
   void tearDown() {
      RequestContextHolder.resetRequestAttributes();
   }

   @Test
   void bearerToken_isAccepted() {
      bindRequestWith("Bearer some-jwt");
      assertDoesNotThrow(AdminAiCallerGuard::requireBearerAuthenticatedRequest);
   }

   @Test
   void bearerToken_isCaseInsensitiveOnTheScheme() {
      // RFC 7235 makes the auth scheme case-insensitive; be forgiving where intent is unambiguous.
      bindRequestWith("bearer some-jwt");
      assertDoesNotThrow(AdminAiCallerGuard::requireBearerAuthenticatedRequest);
   }

   @Test
   void missingAuthorizationHeader_isRejected() {
      // The CSRF scenario: a browser session cookie carries the request, with no bearer header.
      bindRequestWith(null);
      assertForbidden();
   }

   @Test
   void nonBearerScheme_isRejected() {
      bindRequestWith("Basic dXNlcjpwYXNz");
      assertForbidden();
   }

   @Test
   void blankBearerToken_isRejected() {
      bindRequestWith("Bearer   ");
      assertForbidden();
   }

   @Test
   void noRequestContext_isRejected() {
      // Fail closed rather than silently passing when invoked outside a request (defence against
      // this guard quietly becoming a no-op).
      RequestContextHolder.resetRequestAttributes();
      assertForbidden();
   }

   private static void assertForbidden() {
      ResponseStatusException thrown = assertThrows(
         ResponseStatusException.class, AdminAiCallerGuard::requireBearerAuthenticatedRequest);
      assertEquals(HttpStatus.FORBIDDEN, thrown.getStatusCode());
   }

   private static void bindRequestWith(String authorization) {
      MockHttpServletRequest request = new MockHttpServletRequest();

      if(authorization != null) {
         request.addHeader("Authorization", authorization);
      }

      RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
   }
}
