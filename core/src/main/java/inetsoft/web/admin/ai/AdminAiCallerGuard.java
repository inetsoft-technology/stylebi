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

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

/**
 * CSRF backstop for the admin-chat endpoints (`/api/wiz/v1/admin/*`).
 *
 * <p>These endpoints are called server-to-server by the wiz-services broker, which always presents
 * the operator's SSO token as {@code Authorization: Bearer <jwt>}. They must be mapped under
 * {@code /api/wiz/**} for that call to work at all: {@code WizServiceAuthenticationFilter}
 * validates the JWT only on that prefix, and {@code CSRFFilter} exempts only that prefix (a broker
 * has no session and therefore no CSRF token).
 *
 * <p>That exemption is a path match, so it assumes all wiz traffic is bearer-authenticated — an
 * assumption the filters do not enforce. {@code WizServiceAuthenticationFilter} falls through when
 * no bearer header is present ("let other filters handle authentication"), and
 * {@code RequestPrincipalFilter} then resolves an ordinary browser-session principal into
 * {@code getUserPrincipal()}, which is what {@code @Secured} and the Site-Administrator check read.
 * Without this guard, a cross-site request could ride a logged-in administrator's session cookie
 * into a server-property mutation, with CSRF protection skipped.
 *
 * <p>The check is on the request's {@code Authorization} header rather than the principal's
 * {@code wiz} property: {@code WizServiceAuthenticationFilter} stores its principal in the HTTP
 * session, so that property shows only that the *session* once presented a JWT, not that *this
 * request* did. A cross-site form POST cannot set an {@code Authorization} header, and a scripted
 * request that tries requires a CORS preflight an attacker cannot satisfy.
 */
public final class AdminAiCallerGuard {
   private AdminAiCallerGuard() {
   }

   /**
    * Requires that the in-flight request presented a bearer token, i.e. that it came from the
    * broker rather than from a browser session riding the {@code /api/wiz/**} CSRF exemption.
    *
    * @throws ResponseStatusException with {@code 403} if there is no bearer token — including when
    *                                 called outside a request context, so the guard fails closed
    *                                 rather than silently passing.
    */
   public static void requireBearerAuthenticatedRequest() {
      RequestAttributes attributes = RequestContextHolder.getRequestAttributes();

      if(!(attributes instanceof ServletRequestAttributes)) {
         throw forbidden();
      }

      HttpServletRequest request = ((ServletRequestAttributes) attributes).getRequest();
      String header = request.getHeader("Authorization");

      if(header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())
         || header.substring(BEARER_PREFIX.length()).isBlank())
      {
         throw forbidden();
      }
   }

   private static ResponseStatusException forbidden() {
      // Deliberately terse: the caller is either the broker (which always sends the token) or an
      // unintended browser-session caller, and neither benefits from detail here.
      return new ResponseStatusException(
         HttpStatus.FORBIDDEN, "Admin-chat endpoints require bearer-token authentication");
   }

   private static final String BEARER_PREFIX = "Bearer ";
}
