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
package inetsoft.uql.rest.auth;

import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BasicAuthTest {
   @Test
   void basicAuthHeadersAreSet() {
      final String username = "user";
      final String password = "password";

      final String credentials = String.format("%s:%s", username, password);
      final String encodedCredentials = Base64.getEncoder()
         .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
      final String expectedAuthorization = "Basic " + encodedCredentials;

      final BasicAuthenticator basicAuth = new BasicAuthenticator(BasicAuthConfig.builder()
                                                   .username(username)
                                                   .password(password)
                                                   .build());
      final HttpGet get = new HttpGet();

      basicAuth.authenticateRequest(get, HttpClientContext.create());
      final String actualAuthorization = get.getFirstHeader(HttpHeaders.AUTHORIZATION)
         .getValue();

      assertEquals(expectedAuthorization, actualAuthorization);
   }

   @Test
   void allowEmptyUsernameOrPassword() {
      BasicAuthConfig.builder().username("user").build();
      BasicAuthConfig.builder().password("password").build();
   }
}
