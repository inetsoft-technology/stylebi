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
package inetsoft.web.composer.model.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77151: the composer tabular query dialog posts the OAuth result to
 * /api/composer/ws/tabular-query-dialog/oauth-tokens. The body is built by
 * OAuthAuthorizationService and must deserialize with the server's default
 * (FAIL_ON_UNKNOWN_PROPERTIES enabled) object mapper.
 */
@Tag("core")
class TabularQueryOAuthTokensTest {
   private final ObjectMapper mapper = new ObjectMapper().registerModule(new Jdk8Module());

   @Test
   void deserializesBrowserFlowBodyWithCompleteFlagAndMissingOptionalTokens() throws Exception {
      // browser flow: {view, ...AuthorizationResult} where the result always has complete=true
      // and a provider may omit refreshToken/scope/issued
      String json = "{\"view\":{},\"complete\":true,\"accessToken\":\"abc\"," +
         "\"expiration\":\"2026-09-27T00:00:00Z\",\"properties\":{\"n\":1}," +
         "\"method\":\"updateTokens\"}";

      TabularQueryOAuthTokens tokens = mapper.readValue(json, TabularQueryOAuthTokens.class);

      assertEquals("abc", tokens.accessToken());
      assertNull(tokens.refreshToken());
      assertNull(tokens.issued());
      assertNull(tokens.scope());
      assertEquals("2026-09-27T00:00:00Z", tokens.expiration());
      assertEquals(1, tokens.properties().get("n"));
      assertEquals("updateTokens", tokens.method());
      assertNotNull(tokens.view());
   }

   @Test
   void deserializesPasswordGrantBody() throws Exception {
      // password grant flow: {view, accessToken, scope, refreshToken, expiration, method}
      String json = "{\"view\":{},\"accessToken\":\"abc\",\"scope\":\"s\"," +
         "\"refreshToken\":\"r\",\"expiration\":null,\"method\":\"updateTokens\"}";

      TabularQueryOAuthTokens tokens = mapper.readValue(json, TabularQueryOAuthTokens.class);

      assertEquals("r", tokens.refreshToken());
      assertNull(tokens.issued());
      assertTrue(tokens.properties().isEmpty());
   }
}
