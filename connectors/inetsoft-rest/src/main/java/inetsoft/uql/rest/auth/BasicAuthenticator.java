/*
 * inetsoft-rest - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.rest.auth;

import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Performs Basic Authentication.
 */
public class BasicAuthenticator implements RestAuthenticator {
   BasicAuthenticator(BasicAuthConfig config) {
      this.config = config;
   }

   @Override
   public void authenticateRequest(HttpRequestBase request, HttpClientContext context) {
      final String credentials = String.format("%s:%s", config.username(), config.password());
      final String encodedCredentials = Base64.getEncoder()
         .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
      request.setHeader(HttpHeaders.AUTHORIZATION, "Basic " + encodedCredentials);
   }

   private final BasicAuthConfig config;
}
