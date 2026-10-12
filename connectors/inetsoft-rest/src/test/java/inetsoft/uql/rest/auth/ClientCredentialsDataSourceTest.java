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
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.rest.*;
import inetsoft.uql.rest.json.RestJsonQuery;
import inetsoft.uql.rest.json.RestJsonDataSource;
import inetsoft.uql.rest.json.RestJsonRuntime;
import inetsoft.util.credential.*;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, ClientCredentialsDataSourceTest.CredentialConfig.class },
   initializers = ConfigurationContextInitializer.class)
@SreeHome
class ClientCredentialsDataSourceTest {
   @BeforeEach
   void createClient() {
      client = HttpAsyncClients.createDefault();
      client.start();
   }

   @AfterEach
   void closeClient() throws IOException {
      client.close();
   }

   @Test
   void createsClientCredentialsAuthenticator() {
      assertInstanceOf(ClientCredentialsAuthenticator.class,
                       RestAuthenticatorFactory.createFrom(createDataSource(), client));
   }

   @Test
   void requiresTokenUri() {
      final RestJsonDataSource dataSource = createDataSource();
      dataSource.setTokenUri("");

      assertThrows(IllegalArgumentException.class,
                   () -> RestAuthenticatorFactory.createFrom(dataSource, client));
   }

   @Test
   void requiresClientId() {
      final RestJsonDataSource dataSource = createDataSource();
      dataSource.setClientId(null);

      assertThrows(IllegalArgumentException.class,
                   () -> RestAuthenticatorFactory.createFrom(dataSource, client));
   }

   @Test
   void requiresClientSecret() {
      final RestJsonDataSource dataSource = createDataSource();
      dataSource.setClientSecret("");

      assertThrows(IllegalArgumentException.class,
                   () -> RestAuthenticatorFactory.createFrom(dataSource, client));
   }

   @Test
   void showsOnlyClientCredentialsFields() {
      final RestJsonDataSource dataSource = createDataSource();

      assertTrue(dataSource.isOauthClientCredentials());
      assertTrue(dataSource.useCredentialForOauthClient());
      // the authorization code fields, button and tokens are hidden
      assertFalse(dataSource.isOauth());
      assertFalse(dataSource.useCredentialForOauth());

      dataSource.setAuthType(AuthType.OAUTH);
      assertTrue(dataSource.useCredentialForOauthClient());
      assertTrue(dataSource.useCredentialForOauth());
      assertFalse(dataSource.isOauthClientCredentials());
   }

   @Test
   void persistsClientCredentialsSettings() throws Exception {
      final RestJsonDataSource dataSource = createDataSource();
      dataSource.setClientAuthMethod(ClientAuthMethod.POST);
      dataSource.setAudience("https://api.example.com");

      final RestJsonDataSource copy = roundTrip(dataSource);

      assertEquals(AuthType.OAUTH_CLIENT_CREDENTIALS, copy.getAuthType());
      assertEquals("client", copy.getClientId());
      assertEquals("secret", copy.getClientSecret());
      assertEquals("https://auth.example.com/token", copy.getTokenUri());
      assertEquals("read", copy.getScope());
      assertEquals(ClientAuthMethod.POST, copy.getClientAuthMethod());
      assertEquals("https://api.example.com", copy.getAudience());
   }

   @Test
   void defaultsClientAuthMethodForExistingDataSources() throws Exception {
      final RestJsonDataSource dataSource = new RestJsonDataSource();
      dataSource.setName("test");
      dataSource.setAuthType(AuthType.OAUTH);
      dataSource.setClientId("client");

      final RestJsonDataSource copy = roundTrip(dataSource);

      assertEquals(AuthType.OAUTH, copy.getAuthType());
      assertEquals(ClientAuthMethod.BASIC, copy.getClientAuthMethod());
      assertNull(copy.getAudience());
   }

   @Test
   void cloneCopiesClientCredentialsSettings() {
      final RestJsonDataSource dataSource = createDataSource();
      dataSource.setClientAuthMethod(ClientAuthMethod.POST);
      dataSource.setAudience("aud");

      final RestJsonDataSource clone = (RestJsonDataSource) dataSource.clone();

      assertEquals(ClientAuthMethod.POST, clone.getClientAuthMethod());
      assertEquals("aud", clone.getAudience());
      assertEquals(dataSource, clone);

      clone.setAudience("other");
      assertNotEquals(dataSource, clone);
   }

   @Test
   void connectionTestRequestsToken() throws Exception {
      final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      final int[] status = { 401 };
      server.createContext("/", exchange -> {
         exchange.getRequestBody().readAllBytes();
         final byte[] body = (status[0] == 200 ?
            "{\"access_token\":\"abc\",\"expires_in\":3600}" : "{\"error\":\"invalid_client\"}")
            .getBytes(StandardCharsets.UTF_8);
         exchange.sendResponseHeaders(status[0], body.length);

         try(OutputStream output = exchange.getResponseBody()) {
            output.write(body);
         }
      });
      server.start();

      try {
         final String base = "http://127.0.0.1:" + server.getAddress().getPort();
         final RestJsonDataSource dataSource = createDataSource();
         dataSource.setURL(base + "/api");
         // a secret unique to this test so that a token cached by another test isn't reused
         dataSource.setClientSecret("connection-test-" + System.nanoTime());
         dataSource.setTokenUri(base + "/token");

         final IOException e = assertThrows(
            IOException.class,
            () -> new RestJsonRuntime().testDataSource(dataSource, new VariableTable()));
         assertTrue(e.getMessage().contains("invalid_client"), e.getMessage());

         status[0] = 200;
         new RestJsonRuntime().testDataSource(dataSource, new VariableTable());
      }
      finally {
         server.stop(0);
      }
   }

   @Test
   void unauthorizedRequestIsRetriedWithNewToken() throws Exception {
      // the first token is revoked by the API before it expires
      try(TokenServer server = new TokenServer(Set.of("token2"))) {
         final RestJsonDataSource dataSource = server.createDataSource();

         final HttpResponse response = executeRequest(dataSource);

         assertEquals(200, response.getResponseStatusCode());
         assertEquals(2, server.tokenRequests.get());
         assertEquals(List.of("Bearer token1", "Bearer token2"), server.apiAuthorizations);

         // the replacement token is cached
         executeRequest(dataSource);
         assertEquals(2, server.tokenRequests.get());
      }
   }

   @Test
   void unauthorizedRequestIsRetriedOnlyOnce() throws Exception {
      try(TokenServer server = new TokenServer(Set.of())) {
         final RestJsonDataSource dataSource = server.createDataSource();

         final RestResponseException e =
            assertThrows(RestResponseException.class, () -> executeRequest(dataSource));

         assertEquals(401, e.getResponse().getResponseStatusCode());
         assertEquals(2, server.tokenRequests.get());
         assertEquals(2, server.apiAuthorizations.size());
      }
   }

   @Test
   void persistentUnauthorizedDoesNotRequestTokenPerRequest() throws Exception {
      // e.g. the client lacks the scope for the resource, a new token doesn't help
      try(TokenServer server = new TokenServer(Set.of())) {
         final RestJsonDataSource dataSource = server.createDataSource();

         for(int i = 0; i < 10; i++) {
            assertThrows(RestResponseException.class, () -> executeRequest(dataSource));
         }

         // the first request retries once with a new token, later requests don't retry
         assertEquals(2, server.tokenRequests.get());
         assertEquals(11, server.apiAuthorizations.size());
      }
   }

   @Test
   void connectionTestDoesNotReplaceCachedToken() throws Exception {
      try(TokenServer server = new TokenServer(Set.of("token1"))) {
         final RestJsonDataSource dataSource = server.createDataSource();
         executeRequest(dataSource);

         // e.g. the data source status refresh while a query is running
         new RestJsonRuntime().testDataSource(dataSource, new VariableTable());
         assertEquals(2, server.tokenRequests.get());

         // the query still uses its token, it isn't discarded by the test
         assertEquals(200, executeRequest(dataSource).getResponseStatusCode());
         assertEquals(2, server.tokenRequests.get());
         assertEquals("Bearer token1",
                      server.apiAuthorizations.get(server.apiAuthorizations.size() - 1));
      }
   }

   @Test
   void connectionTestDoesNotUseCachedToken() throws Exception {
      try(TokenServer server = new TokenServer(Set.of("token1"))) {
         final RestJsonDataSource dataSource = server.createDataSource();
         executeRequest(dataSource);
         assertEquals(1, server.tokenRequests.get());

         // the client is disabled in the authorization server while its token is cached
         server.tokenStatus = 401;

         assertThrows(IOException.class,
                      () -> new RestJsonRuntime().testDataSource(dataSource, new VariableTable()));
         assertEquals(2, server.tokenRequests.get());
      }
   }

   @Test
   void connectionTestAcceptsUrlThatIsNotAValidUri() throws Exception {
      try(TokenServer server = new TokenServer(Set.of())) {
         final RestJsonDataSource dataSource = server.createDataSource();
         dataSource.setURL(dataSource.getURL() + "/{tenant}");

         new RestJsonRuntime().testDataSource(dataSource, new VariableTable());
         assertEquals(1, server.tokenRequests.get());
      }
   }

   private static HttpResponse executeRequest(RestJsonDataSource dataSource) throws Exception {
      final RestJsonQuery query = new RestJsonQuery();
      query.setDataSource(dataSource);
      query.setSuffix("/users");

      try(HttpHandler handler = new HttpHandler()) {
         return handler.executeRequest(RestRequest.builder().query(query).build());
      }
   }

   /**
    * A token endpoint that issues token1, token2, ... and an API that only accepts the tokens
    * it is given.
    */
   private static final class TokenServer implements AutoCloseable {
      TokenServer(Set<String> acceptedTokens) throws IOException {
         server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
         server.createContext("/token", exchange -> {
            exchange.getRequestBody().readAllBytes();
            final String body = tokenStatus == 200 ?
               "{\"access_token\":\"token" + tokenRequests.incrementAndGet() +
               "\",\"expires_in\":3600}" :
               "{\"error\":\"invalid_client\"}";

            if(tokenStatus != 200) {
               tokenRequests.incrementAndGet();
            }

            respond(exchange, tokenStatus, body);
         });
         server.createContext("/api", exchange -> {
            final String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            apiAuthorizations.add(authorization);
            final boolean accepted = authorization != null &&
               acceptedTokens.contains(authorization.substring("Bearer ".length()));
            respond(exchange, accepted ? 200 : 401, accepted ? "[]" : "{}");
         });
         server.start();
      }

      RestJsonDataSource createDataSource() {
         final String base = "http://127.0.0.1:" + server.getAddress().getPort();
         final RestJsonDataSource dataSource = ClientCredentialsDataSourceTest.createDataSource();
         dataSource.setURL(base + "/api");
         dataSource.setTokenUri(base + "/token");
         // a secret unique to this server so that a token cached by another test isn't reused
         dataSource.setClientSecret("secret-" + System.nanoTime());
         return dataSource;
      }

      private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status,
                                  String body) throws IOException
      {
         final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
         exchange.getResponseHeaders().add("Content-Type", "application/json");
         exchange.sendResponseHeaders(status, bytes.length);

         try(OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
         }
      }

      @Override
      public void close() {
         server.stop(0);
      }

      private final HttpServer server;
      private final AtomicInteger tokenRequests = new AtomicInteger();
      private final List<String> apiAuthorizations = new CopyOnWriteArrayList<>();
      private volatile int tokenStatus = 200;
   }

   @Test
   void clientAuthMethodSurvivesSwitchingAuthType() throws Exception {
      final RestJsonDataSource dataSource = createDataSource();
      dataSource.setClientAuthMethod(ClientAuthMethod.POST);
      dataSource.setAuthType(AuthType.OAUTH);

      final RestJsonDataSource copy = roundTrip(dataSource);
      assertEquals(ClientAuthMethod.POST, copy.getClientAuthMethod());

      copy.setAuthType(AuthType.OAUTH_CLIENT_CREDENTIALS);
      assertEquals(ClientAuthMethod.POST, copy.getClientAuthMethod());
   }

   @Test
   void unknownClientAuthMethodStillLoads() throws Exception {
      final RestJsonDataSource dataSource = createDataSource();
      dataSource.setClientAuthMethod(ClientAuthMethod.POST);
      final String xml = toXML(dataSource);
      assertTrue(xml.contains("<clientAuthMethod>POST</clientAuthMethod>"), xml);

      // e.g. imported from a later version, or an empty value
      for(String value : new String[] { "PRIVATE_KEY_JWT", "", "null" }) {
         final RestJsonDataSource copy = parse(xml.replace(
            "<clientAuthMethod>POST</clientAuthMethod>",
            "<clientAuthMethod>" + value + "</clientAuthMethod>"));

         assertEquals(ClientAuthMethod.BASIC, copy.getClientAuthMethod());
         assertEquals(AuthType.OAUTH_CLIENT_CREDENTIALS, copy.getAuthType());
         assertEquals("https://auth.example.com/token", copy.getTokenUri());
      }
   }

   private static String toXML(RestJsonDataSource dataSource) {
      final StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         dataSource.writeXML(writer);
      }

      return buffer.toString();
   }

   private static RestJsonDataSource parse(String xml) throws Exception {
      final Element root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
         .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
         .getDocumentElement();
      final RestJsonDataSource copy = new RestJsonDataSource();
      copy.parseXML(root);
      return copy;
   }

   private static RestJsonDataSource roundTrip(RestJsonDataSource dataSource) throws Exception {
      final StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         dataSource.writeXML(writer);
      }

      final Element root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
         .parse(new ByteArrayInputStream(buffer.toString().getBytes(StandardCharsets.UTF_8)))
         .getDocumentElement();
      final RestJsonDataSource copy = new RestJsonDataSource();
      copy.parseXML(root);
      return copy;
   }

   private static RestJsonDataSource createDataSource() {
      final RestJsonDataSource dataSource = new RestJsonDataSource();
      dataSource.setName("test");
      dataSource.setURL("https://api.example.com");
      dataSource.setAuthType(AuthType.OAUTH_CLIENT_CREDENTIALS);
      dataSource.setClientId("client");
      dataSource.setClientSecret("secret");
      dataSource.setTokenUri("https://auth.example.com/token");
      dataSource.setScope("read");
      return dataSource;
   }

   @Configuration
   static class CredentialConfig {
      @Bean
      public CredentialService credentialService() {
         CredentialService credentialService = mock(CredentialService.class);
         when(credentialService.createCredential(
            eq(CredentialType.PASSWORD_OAUTH2_WITH_FLAGS), anyBoolean()))
            .thenAnswer(invocation -> new LocalPasswordAndOAuth2WithFlagCredentialsGrant());
         when(credentialService.createCredential(CredentialType.PASSWORD_OAUTH2_WITH_FLAGS))
            .thenAnswer(invocation -> new LocalPasswordAndOAuth2WithFlagCredentialsGrant());
         return credentialService;
      }
   }

   private CloseableHttpAsyncClient client;
}
