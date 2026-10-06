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

import com.sun.net.httpserver.HttpServer;
import inetsoft.uql.rest.auth.ClientCredentialsTokenCache.ClientCredentialsRequest;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class ClientCredentialsAuthenticatorTest {
   @BeforeEach
   void startServer() throws IOException {
      requests.clear();
      responseStatus = 200;
      responseBody = "{\"access_token\":\"abc123\",\"token_type\":\"Bearer\",\"expires_in\":3600}";

      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/token", exchange -> {
         final String body = new String(
            exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
         requests.add(new TokenRequest(
            exchange.getRequestMethod(),
            exchange.getRequestHeaders().getFirst("Authorization"),
            exchange.getRequestHeaders().getFirst("Content-Type"),
            parseForm(body)));

         final byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
         exchange.getResponseHeaders().add("Content-Type", "application/json");
         exchange.sendResponseHeaders(responseStatus, response.length);

         try(OutputStream output = exchange.getResponseBody()) {
            output.write(response);
         }
      });
      server.start();
      tokenUri = "http://127.0.0.1:" + server.getAddress().getPort() + "/token";

      client = HttpAsyncClients.createDefault();
      client.start();
      cache = new ClientCredentialsTokenCache(Clock.systemUTC());
   }

   @AfterEach
   void stopServer() throws IOException {
      client.close();
      server.stop(0);
   }

   @Test
   void basicClientAuthentication() throws Exception {
      final HttpGet request = authenticate(createRequest(ClientAuthMethod.BASIC, "read write", null));

      assertEquals("Bearer abc123", request.getFirstHeader("Authorization").getValue());
      assertEquals(1, requests.size());

      final TokenRequest tokenRequest = requests.get(0);
      assertEquals("POST", tokenRequest.method());
      assertTrue(tokenRequest.contentType().startsWith("application/x-www-form-urlencoded"));
      // the client ID and secret are form encoded before they are base 64 encoded
      assertEquals(
         "Basic " + Base64.getEncoder().encodeToString(
            "my+client:p%3Aw%26d".getBytes(StandardCharsets.UTF_8)),
         tokenRequest.authorization());
      assertEquals("client_credentials", tokenRequest.form().get("grant_type"));
      assertEquals("read write", tokenRequest.form().get("scope"));
      assertFalse(tokenRequest.form().containsKey("client_id"));
      assertFalse(tokenRequest.form().containsKey("client_secret"));
      assertFalse(tokenRequest.form().containsKey("audience"));
   }

   @Test
   void postClientAuthentication() throws Exception {
      final HttpGet request = authenticate(
         createRequest(ClientAuthMethod.POST, null, "https://api.example.com"));

      assertEquals("Bearer abc123", request.getFirstHeader("Authorization").getValue());

      final TokenRequest tokenRequest = requests.get(0);
      assertNull(tokenRequest.authorization());
      assertEquals("client_credentials", tokenRequest.form().get("grant_type"));
      assertEquals("my client", tokenRequest.form().get("client_id"));
      assertEquals("p:w&d", tokenRequest.form().get("client_secret"));
      assertEquals("https://api.example.com", tokenRequest.form().get("audience"));
      assertFalse(tokenRequest.form().containsKey("scope"));
   }

   @Test
   void tokenIsCachedAcrossAuthenticators() throws Exception {
      final ClientCredentialsRequest tokenRequest =
         createRequest(ClientAuthMethod.BASIC, "read", null);

      // a new authenticator is created for each HTTP request
      for(int i = 0; i < 5; i++) {
         assertEquals("Bearer abc123",
                      authenticate(tokenRequest).getFirstHeader("Authorization").getValue());
      }

      assertEquals(1, requests.size());
   }

   @Test
   void replacesExistingAuthorizationHeader() throws Exception {
      final HttpGet request = new HttpGet("http://127.0.0.1/data");
      request.addHeader("Authorization", "Basic old");
      new ClientCredentialsAuthenticator(
         createRequest(ClientAuthMethod.BASIC, null, null), client, cache)
         .authenticateRequest(request, HttpClientContext.create());

      assertEquals(1, request.getHeaders("Authorization").length);
      assertEquals("Bearer abc123", request.getFirstHeader("Authorization").getValue());
   }

   @Test
   void rejectedTokenIsReplaced() throws Exception {
      final ClientCredentialsRequest tokenRequest =
         createRequest(ClientAuthMethod.BASIC, null, null);
      final ClientCredentialsAuthenticator authenticator =
         new ClientCredentialsAuthenticator(tokenRequest, client, cache);

      // nothing to discard before a token is applied
      assertFalse(authenticator.credentialsRejected());

      authenticator.authenticateRequest(new HttpGet("http://127.0.0.1/data"),
                                        HttpClientContext.create());
      assertTrue(authenticator.credentialsRejected());

      responseBody = "{\"access_token\":\"def456\",\"expires_in\":3600}";
      final HttpGet retry = new HttpGet("http://127.0.0.1/data");
      authenticator.authenticateRequest(retry, HttpClientContext.create());

      assertEquals("Bearer def456", retry.getFirstHeader("Authorization").getValue());
      assertEquals(2, requests.size());
   }

   @Test
   void verifyCredentialsRequestsTokenWithoutReplacingCachedToken() throws Exception {
      final ClientCredentialsRequest tokenRequest =
         createRequest(ClientAuthMethod.BASIC, null, null);
      authenticate(tokenRequest);

      responseBody = "{\"access_token\":\"def456\",\"expires_in\":3600}";
      new ClientCredentialsAuthenticator(tokenRequest, client, cache).verifyCredentials();
      assertEquals(2, requests.size());

      // queries keep using the cached token
      assertEquals("Bearer abc123",
                   authenticate(tokenRequest).getFirstHeader("Authorization").getValue());
      assertEquals(2, requests.size());
   }

   @Test
   void verifyCredentialsReportsInvalidClient() {
      responseStatus = 401;
      responseBody = "{\"error\":\"invalid_client\"}";

      final IOException e = assertThrows(
         IOException.class,
         () -> new ClientCredentialsAuthenticator(
            createRequest(ClientAuthMethod.BASIC, null, null), client, cache).verifyCredentials());
      assertTrue(e.getMessage().contains("invalid_client"), e.getMessage());
   }

   @Test
   void expiresInAsString() throws Exception {
      responseBody = "{\"access_token\":\"abc123\",\"expires_in\":\"3600\"}";
      final ClientCredentialsRequest tokenRequest =
         createRequest(ClientAuthMethod.BASIC, null, null);

      authenticate(tokenRequest);
      authenticate(tokenRequest);
      assertEquals(1, requests.size());
   }

   @Test
   void errorResponseIncludesOAuthError() {
      responseStatus = 401;
      responseBody = "{\"error\":\"invalid_client\"," +
         "\"error_description\":\"Invalid client credentials\"}";

      final IOException e = assertThrows(
         IOException.class,
         () -> authenticate(createRequest(ClientAuthMethod.BASIC, null, null)));

      assertTrue(e.getMessage().contains("401"), e.getMessage());
      assertTrue(e.getMessage().contains("invalid_client"), e.getMessage());
      assertTrue(e.getMessage().contains("Invalid client credentials"), e.getMessage());
      assertFalse(e.getMessage().contains("p:w&d"), e.getMessage());
   }

   @Test
   void failedRequestIsRetried() throws Exception {
      responseStatus = 500;
      responseBody = "error";
      final ClientCredentialsRequest tokenRequest =
         createRequest(ClientAuthMethod.BASIC, null, null);

      assertThrows(IOException.class, () -> authenticate(tokenRequest));

      responseStatus = 200;
      responseBody = "{\"access_token\":\"abc123\",\"expires_in\":3600}";
      assertEquals("Bearer abc123",
                   authenticate(tokenRequest).getFirstHeader("Authorization").getValue());
      assertEquals(2, requests.size());
   }

   @Test
   void missingAccessToken() {
      responseBody = "{\"token_type\":\"Bearer\"}";

      assertThrows(IOException.class,
                   () -> authenticate(createRequest(ClientAuthMethod.BASIC, null, null)));
   }

   @Test
   void nonJsonResponse() {
      responseBody = "<html>login</html>";

      assertThrows(IOException.class,
                   () -> authenticate(createRequest(ClientAuthMethod.BASIC, null, null)));
   }

   private HttpGet authenticate(ClientCredentialsRequest tokenRequest) throws Exception {
      final HttpGet request = new HttpGet("http://127.0.0.1/data");
      new ClientCredentialsAuthenticator(tokenRequest, client, cache)
         .authenticateRequest(request, HttpClientContext.create());
      return request;
   }

   private ClientCredentialsRequest createRequest(ClientAuthMethod method, String scope,
                                                  String audience)
   {
      return new ClientCredentialsRequest(tokenUri, "my client", "p:w&d", scope, audience, method);
   }

   private static Map<String, String> parseForm(String body) {
      final Map<String, String> form = new HashMap<>();

      for(String pair : body.split("&")) {
         if(pair.isEmpty()) {
            continue;
         }

         final int index = pair.indexOf('=');
         form.put(URLDecoder.decode(pair.substring(0, index), StandardCharsets.UTF_8),
                  URLDecoder.decode(pair.substring(index + 1), StandardCharsets.UTF_8));
      }

      return form;
   }

   private record TokenRequest(String method, String authorization, String contentType,
                               Map<String, String> form)
   {
   }

   private HttpServer server;
   private String tokenUri;
   private CloseableHttpAsyncClient client;
   private ClientCredentialsTokenCache cache;
   private volatile int responseStatus;
   private volatile String responseBody;
   private final List<TokenRequest> requests = new CopyOnWriteArrayList<>();
}
