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
package inetsoft.web.security.auth;

import inetsoft.sree.security.SRPrincipal;

import java.security.Principal;
import java.util.Locale;

public interface JwtService {
   /**
    * Creates a new authorization token for a user.
    *
    * @param principal the principal that identifies the user.
    *
    * @return the authorization token.
    */
   AuthorizationToken createToken(Principal principal);

   /**
    * Gets the principal associated with the specified authorization token.
    *
    * @param host  the host or IP address from which the request originated.
    * @param token the authorization token.
    *
    * @return the associated principal.
    *
    * @throws UnauthorizedAccessException if the authorization token is invalid.
    */
   SRPrincipal getPrincipal(String host, String token, Locale locale)
      throws UnauthorizedAccessException;

   /**
    * Gets the principal associated with the specified authorization token.
    *
    * @param host  the host or IP address from which the request originated.
    * @param token the authorization token.
    *
    * @return the associated principal.
    *
    * @throws UnauthorizedAccessException if the authorization token is invalid.
    */
   SRPrincipal getPrincipal(String host, String token, String sessionId, Locale locale)
      throws UnauthorizedAccessException;
}
