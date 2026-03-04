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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class BasicAuthTest {
   @ParameterizedTest(name = "username={0}, password={1}")
   @MethodSource("authCredentials")
   void should_set_correct_authorization_header(String username, String password) {
      String expectedHeader = "Basic " + Base64.getEncoder()
         .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));

      BasicAuthenticator auth = new BasicAuthenticator(BasicAuthConfig.builder()
         .username(username)
         .password(password)
         .build());
      HttpGet request = new HttpGet();
      auth.authenticateRequest(request, HttpClientContext.create());

      assertEquals(expectedHeader, request.getFirstHeader(HttpHeaders.AUTHORIZATION).getValue());
   }

   static Stream<Arguments> authCredentials() {
      return Stream.of(
         Arguments.of("user", "password"),
         Arguments.of("admin", "secret"),
         Arguments.of("", "password"),
         Arguments.of("user", "")
      );
   }

   @ParameterizedTest(name = "username={0}, password={1}")
   @CsvSource(value = {"user,null", "null,password"}, nullValues = "null")
   void should_allow_incomplete_builder_config(String username, String password) {
      assertDoesNotThrow(() -> {
         BasicAuthConfig.Builder builder = BasicAuthConfig.builder();
         if(username != null) {
            builder.username(username);
         }
         if(password != null) {
            builder.password(password);
         }
         builder.build();
      });
   }
}
