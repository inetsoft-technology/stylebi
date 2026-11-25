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
package inetsoft.uql.rest.datasource.zohocrm;

import com.sun.net.httpserver.HttpServer;
import inetsoft.test.*;
import inetsoft.uql.util.Config;
import inetsoft.util.CoreTool;
import inetsoft.util.UserMessage;
import inetsoft.util.credential.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests how the Zoho CRM data source handles token responses that omit optional fields or are
 * malformed (Bug #81960). {@code expires_in} is only RECOMMENDED by RFC 6749 section 5.1, a
 * response without it must not fail with a {@code NullPointerException}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, ZohoCRMTokenResponseTest.TestConfig.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
class ZohoCRMTokenResponseTest {
   @Configuration
   static class TestConfig {
      @Bean
      public CredentialService credentialService() {
         CredentialService credentialService = mock(CredentialService.class);
         when(credentialService.createCredential(eq(CredentialType.AUTHORIZATION_CODE), anyBoolean()))
            .thenAnswer(invocation -> new LocalAuthorizationCodeGrant());
         return credentialService;
      }

      @Bean
      public Config config() {
         return mock(Config.class);
      }
   }

   @BeforeEach
   void startServer() throws Exception {
      hits.set(0);
      responseBody = "{}";
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/oauth/v2/token", exchange -> {
         hits.incrementAndGet();
         exchange.getRequestBody().readAllBytes();
         byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
         exchange.getResponseHeaders().add("Content-Type", "application/json");
         exchange.sendResponseHeaders(200, body.length);

         try(OutputStream output = exchange.getResponseBody()) {
            output.write(body);
         }
      });
      server.start();
      CoreTool.clearUserMessage();
   }

   @AfterEach
   void stopServer() {
      server.stop(0);
      CoreTool.clearUserMessage();
   }

   @Test
   void authorizeWithoutExpiresInUsesDefaultLifetime() {
      responseBody = "{\"access_token\":\"new-access\",\"refresh_token\":\"new-refresh\"," +
         "\"api_domain\":\"https://api.example\"}";
      ZohoCRMDataSource dataSource = createDataSource();
      long before = System.currentTimeMillis();

      assertDoesNotThrow(() -> dataSource.authorize("session"));

      assertEquals(1, hits.get());
      assertEquals("new-access", dataSource.getAccessToken());
      assertEquals("new-refresh", dataSource.getRefreshToken());
      assertEquals("https://api.example", dataSource.getURL());
      assertDefaultLifetime(dataSource, before);
      assertEquals(getString("zohocrm.authorize.success"), getMessage());
   }

   @Test
   void refreshWithoutExpiresInUsesDefaultLifetime() {
      responseBody = "{\"access_token\":\"new-access\",\"api_domain\":\"https://api.example\"}";
      ZohoCRMDataSource dataSource = createDataSource();
      dataSource.setTokenExpiration(System.currentTimeMillis() - 60000L);
      long before = System.currentTimeMillis();

      assertDoesNotThrow(dataSource::refreshTokens);

      assertEquals(1, hits.get());
      assertEquals("new-access", dataSource.getAccessToken());
      // the refresh response does not carry a refresh token, the old one is kept
      assertEquals("old-refresh", dataSource.getRefreshToken());
      assertDefaultLifetime(dataSource, before);
   }

   @Test
   void queryParametersWithoutExpiresInDoNotFail() {
      responseBody = "{\"access_token\":\"new-access\",\"api_domain\":\"https://api.example\"}";
      ZohoCRMDataSource dataSource = createDataSource();
      dataSource.setTokenExpiration(System.currentTimeMillis() - 60000L);

      var parameters = assertDoesNotThrow(dataSource::getQueryHttpParameters);

      assertEquals(1, parameters.length);
      assertEquals("Zoho-oauthtoken new-access", parameters[0].getValue());
   }

   @Test
   void expiresInIsStillHonored() {
      responseBody = "{\"access_token\":\"new-access\",\"api_domain\":\"https://api.example\"," +
         "\"expires_in\":120}";
      ZohoCRMDataSource dataSource = createDataSource();
      long before = System.currentTimeMillis();
      dataSource.authorize("session");
      long after = System.currentTimeMillis();

      assertTrue(dataSource.getTokenExpiration() >= before + 120000L);
      assertTrue(dataSource.getTokenExpiration() <= after + 120000L);
   }

   @Test
   void unusableExpiresInFallsBackToDefaultLifetime() {
      for(String expiresIn : new String[] { "\"abc\"", "0", "-5", "null" }) {
         responseBody = "{\"access_token\":\"new-access\",\"api_domain\":\"https://api.example\"," +
            "\"expires_in\":" + expiresIn + "}";
         ZohoCRMDataSource dataSource = createDataSource();
         long before = System.currentTimeMillis();

         assertDoesNotThrow(() -> dataSource.authorize("session"), expiresIn);

         assertEquals("new-access", dataSource.getAccessToken(), expiresIn);
         assertDefaultLifetime(dataSource, before);
         CoreTool.clearUserMessage();
      }
   }

   @Test
   void missingAccessTokenIsReportedAndKeepsStoredValues() {
      responseBody = "{\"api_domain\":\"https://api.example\",\"expires_in\":3600}";
      ZohoCRMDataSource dataSource = createDataSource();
      long expiration = dataSource.getTokenExpiration();

      assertDoesNotThrow(() -> dataSource.authorize("session"));

      assertEquals(getString("zohocrm.token.unexpectedResponse"), getMessage());
      assertUnchanged(dataSource, expiration);
   }

   @Test
   void nullAccessTokenIsReportedAndKeepsStoredValues() {
      responseBody = "{\"access_token\":null,\"api_domain\":\"https://api.example\"," +
         "\"expires_in\":3600}";
      ZohoCRMDataSource dataSource = createDataSource();
      long expiration = dataSource.getTokenExpiration();

      assertDoesNotThrow(() -> dataSource.authorize("session"));

      assertEquals(getString("zohocrm.token.unexpectedResponse"), getMessage());
      assertUnchanged(dataSource, expiration);
   }

   @Test
   void nonObjectResponseIsReported() {
      for(String body : new String[] { "[]", "\"text\"", "", "not json" }) {
         responseBody = body;
         ZohoCRMDataSource dataSource = createDataSource();
         long expiration = dataSource.getTokenExpiration();
         CoreTool.clearUserMessage();

         assertDoesNotThrow(() -> dataSource.authorize("session"), body);

         assertNotNull(getMessage(), body);
         assertUnchanged(dataSource, expiration);
      }
   }

   private static void assertDefaultLifetime(ZohoCRMDataSource dataSource, long before) {
      long lifetime = ZohoCRMDataSource.DEFAULT_EXPIRES_IN * 1000L;
      assertTrue(dataSource.getTokenExpiration() >= before + lifetime);
      assertTrue(dataSource.getTokenExpiration() <= System.currentTimeMillis() + lifetime);
   }

   private static void assertUnchanged(ZohoCRMDataSource dataSource, long expiration) {
      assertEquals("old-access", dataSource.getAccessToken());
      assertEquals("old-refresh", dataSource.getRefreshToken());
      assertEquals("https://www.zohoapis.com", dataSource.getURL());
      assertEquals(expiration, dataSource.getTokenExpiration());
   }

   private ZohoCRMDataSource createDataSource() {
      ZohoCRMDataSource dataSource = new ZohoCRMDataSource();
      dataSource.setClientId("fake-client-id");
      dataSource.setClientSecret("fake-client-secret");
      dataSource.setAccountDomain("http://127.0.0.1:" + server.getAddress().getPort());
      dataSource.setAuthorizationCode("fake-code");
      dataSource.setAccessToken("old-access");
      dataSource.setRefreshToken("old-refresh");
      return dataSource;
   }

   private static String getMessage() {
      UserMessage message = CoreTool.getUserMessage();
      return message == null ? null : message.getMessage();
   }

   private static String getString(String key) {
      return ResourceBundle.getBundle("inetsoft.uql.rest.datasource.zohocrm.Bundle", Locale.ROOT)
         .getString(key);
   }

   private HttpServer server;
   private final AtomicInteger hits = new AtomicInteger();
   private volatile String responseBody;
}
