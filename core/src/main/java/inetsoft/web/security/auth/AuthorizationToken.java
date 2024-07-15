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

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.Objects;

/**
 * AuthorizationToken contains the security token identifying the user to the API.
 */
@Schema(description = "Contains the security token identifying the user to the API.")
public class AuthorizationToken {
   /**
    * Creates a new instance of {@code AuthorizationToken}.
    */
   @SuppressWarnings("unused")
   public AuthorizationToken() {
   }

   /**
    * Creates a new instance of {@code AuthorizationToken}.
    *
    * @param token   the authorization token.
    * @param expires the expiration timestamp.
    */
   public AuthorizationToken(String token, long expires) {
      setToken(token);
      setExpires(expires);
   }

   /**
    * Gets the JSON web token (JWT) used to identify the authenticated user. This must be passed in
    * all other API requests using the HTTP bearer scheme.
    *
    * @return the authorization token.
    */
   @Schema(
      description = "The JSON web token (JWT) used to identify the authenticated user. This must be passed in all other API requests using the `X-Inetsoft-Api-Token` HTTP header.",
      example = "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJteXVzZXIiLCJleHAiOjE2MzE5OTkxOTIsInJvbGVzIjpbIkRlc2lnbmVyIl0sImdyb3VwcyI6WyJEZXZlbG9wbWVudCJdLCJzZWN1cmVJZCI6MTIzNDU2Nzg5fQ.KZGHQV1LbVp3S2ndv6VpVUNqvXtvRKlDE5YK7bupEt4Ri6N2crAFepv1sxUx_g0t72HbYE9wA7acT9a1k-Z2gA")
   @NotNull
   public String getToken() {
      return token;
   }

   /**
    * Sets the JSON web token (JWT) used to identify the authenticated user. This must be passed in
    * all other API requests using the HTTP bearer scheme.
    *
    * @param token the authorization token.
    */
   public void setToken(String token) {
      this.token = token;
   }

   /**
    * Gets the timestamp for the time at which the token expires.
    *
    * @return the expiration timestamp.
    */
   @Schema(
      description = "The timestamp for the time at which the token expires.",
      example = "1631999192000")
   @NotNull
   public long getExpires() {
      return expires;
   }

   /**
    * Sets the timestamp for the time at which the token expires.
    *
    * @param expires the expiration timestamp.
    */
   public void setExpires(long expires) {
      this.expires = expires;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      AuthorizationToken that = (AuthorizationToken) o;
      return expires == that.expires &&
         Objects.equals(token, that.token);
   }

   @Override
   public int hashCode() {
      return Objects.hash(token, expires);
   }

   @Override
   public String toString() {
      return "AuthorizationToken{" +
         "token='" + token + '\'' +
         ", expires=" + expires +
         '}';
   }

   private String token;
   private long expires;
}
