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
package inetsoft.uql.tabular.oauth;

/*
 * Bug #79609: a password-grant refresh response that omits refresh_token must keep the existing
 * refresh token, and a rotated one must replace it. Otherwise the first refresh erases the stored
 * refresh token and the next expiry can neither refresh nor re-grant.
 */

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class AuthorizationClientRefreshTokenTest {
   private HttpServer server;
   private String tokenUri;
   private volatile String responseBody;
   private volatile int responseCode;
   private final List<String> requests = new CopyOnWriteArrayList<>();

   @BeforeEach
   void setUp() throws IOException {
      responseCode = 200;
      server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
      server.createContext("/token", exchange -> {
         requests.add(new String(exchange.getRequestBody().readAllBytes(),
                                 StandardCharsets.UTF_8));
         byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
         exchange.getResponseHeaders().add("Content-Type", "application/json");
         exchange.sendResponseHeaders(responseCode, body.length);

         try(OutputStream out = exchange.getResponseBody()) {
            out.write(body);
         }
      });
      server.start();
      tokenUri = "http://" + server.getAddress().getHostString() + ":" +
         server.getAddress().getPort() + "/token";
   }

   @AfterEach
   void tearDown() {
      server.stop(0);
   }

   @Test
   void omittedRefreshTokenKeepsOldOne() {
      responseBody = "{\"access_token\":\"AT-1\",\"expires_in\":3600}";
      Source ds = expiredSource("OLD-RT");

      Tokens tokens = AuthorizationClient.refreshPasswordGrantToken(ds);

      assertEquals("AT-1", tokens.accessToken());
      assertEquals("OLD-RT", tokens.refreshToken());
      assertEquals(1, requests.size());
      assertTrue(requests.get(0).contains("grant_type=refresh_token"));
      assertTrue(requests.get(0).contains("refresh_token=OLD-RT"));
   }

   @Test
   void emptyRefreshTokenKeepsOldOne() {
      responseBody = "{\"access_token\":\"AT-1\",\"refresh_token\":\"\",\"expires_in\":3600}";

      Tokens tokens = AuthorizationClient.refreshPasswordGrantToken(expiredSource("OLD-RT"));

      assertEquals("OLD-RT", tokens.refreshToken());
   }

   @Test
   void rotatedRefreshTokenReplacesOldOne() {
      responseBody =
         "{\"access_token\":\"AT-1\",\"refresh_token\":\"NEW-RT\",\"expires_in\":3600}";

      Tokens tokens = AuthorizationClient.refreshPasswordGrantToken(expiredSource("OLD-RT"));

      assertEquals("NEW-RT", tokens.refreshToken());
   }

   @Test
   void secondExpiryRefreshesAgain() {
      responseBody = "{\"access_token\":\"AT-1\",\"expires_in\":3600}";
      Source ds = expiredSource("OLD-RT");

      ds.updateTokens(AuthorizationClient.refreshPasswordGrantToken(ds));
      assertEquals("OLD-RT", ds.getRefreshToken());

      // the access token expires again
      ds.setTokenExpiration(1L);
      Tokens tokens = AuthorizationClient.refreshPasswordGrantToken(ds);

      assertNotNull(tokens, "the refresh token must survive the first refresh");
      assertEquals(2, requests.size());
      assertTrue(requests.get(1).contains("refresh_token=OLD-RT"));
   }

   @Test
   void failedRefreshFallsBackToPasswordGrant() {
      responseCode = 400;
      responseBody = "{\"error\":\"invalid_grant\"}";

      assertThrows(RuntimeException.class,
                   () -> AuthorizationClient.refreshPasswordGrantToken(expiredSource("OLD-RT")));
      // refresh attempt followed by the password grant re-auth
      assertEquals(2, requests.size());
      assertTrue(requests.get(1).contains("grant_type=password"));
   }

   private Source expiredSource(String refreshToken) {
      Source ds = new Source();
      ds.setTokenUri(tokenUri);
      ds.setRefreshToken(refreshToken);
      ds.setTokenExpiration(1L);
      return ds;
   }

   /** Mirrors ODataDataSource.updateTokens, which writes the refresh token unconditionally. */
   private static final class Source implements OAuthDataSource {
      private String tokenUri;
      private String refreshToken;
      private String accessToken;
      private long tokenExpiration;

      @Override
      public void updateTokens(Tokens tokens) {
         accessToken = tokens.accessToken();
         refreshToken = tokens.refreshToken();
         tokenExpiration = tokens.expiration();
      }

      @Override public String getTokenUri() { return tokenUri; }
      @Override public void setTokenUri(String uri) { tokenUri = uri; }
      @Override public String getRefreshToken() { return refreshToken; }
      @Override public void setRefreshToken(String token) { refreshToken = token; }
      @Override public String getAccessToken() { return accessToken; }
      @Override public long getTokenExpiration() { return tokenExpiration; }
      @Override public void setTokenExpiration(long expiration) { tokenExpiration = expiration; }
   }
}
