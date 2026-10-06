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
import inetsoft.uql.tabular.*;
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
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests that the Zoho CRM Authorize button tells the user what happened instead of failing
 * silently (Bug #77596).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, ZohoCRMAuthorizeTest.TestConfig.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
class ZohoCRMAuthorizeTest {
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
      lastRequest = null;
      responseBody = "{}";
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/oauth/v2/token", exchange -> {
         hits.incrementAndGet();
         lastRequest = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
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
   void staleCodeReportsInvalidCodeThroughButtonClick() {
      responseBody = "{\"error\":\"invalid_code\"}";
      TabularView view = createView(createDataSource());
      TabularButton button = findButton(view.getViews(), "authorize");
      assertNotNull(button);
      button.setClicked(true);

      ZohoCRMDataSource refreshed = new ZohoCRMDataSource();
      TabularUtil.refreshView(view, refreshed);

      assertEquals(1, hits.get());
      assertTrue(lastRequest.contains("grant_type=authorization_code"));
      assertEquals("fake-old-access", refreshed.getAccessToken());
      assertEquals("fake-code", refreshed.getAuthorizationCode());
      assertEquals(getString("zohocrm.token.invalidCode"), getMessage());
   }

   @Test
   void invalidClientIsReported() {
      responseBody = "{\"error\":\"invalid_client\"}";
      ZohoCRMDataSource dataSource = createDataSource();
      dataSource.authorize("session");

      assertEquals(1, hits.get());
      assertEquals(getString("zohocrm.token.invalidClient"), getMessage());
   }

   @Test
   void otherErrorIsReported() {
      responseBody = "{\"error\":\"access_denied\"}";
      ZohoCRMDataSource dataSource = createDataSource();
      dataSource.authorize("session");

      String message = getMessage();
      assertNotNull(message);
      assertTrue(message.contains("access_denied"), message);
   }

   @Test
   void missingErrorKeyIsReported() {
      responseBody = "{}";
      ZohoCRMDataSource dataSource = createDataSource();
      dataSource.authorize("session");

      assertEquals(1, hits.get());
      assertEquals(getString("zohocrm.token.unexpectedResponse"), getMessage());
   }

   @Test
   void emptyRequiredFieldIsReportedWithoutRequest() {
      ZohoCRMDataSource dataSource = createDataSource();
      dataSource.setClientSecret("");
      dataSource.authorize("session");

      assertEquals(0, hits.get());
      String message = getMessage();
      assertNotNull(message);
      assertTrue(message.contains(getString("Client Secret")), message);
   }

   @Test
   void emptyCodeAsksForNewCode() {
      ZohoCRMDataSource dataSource = createDataSource();
      dataSource.setAuthorizationCode(null);
      dataSource.authorize("session");

      assertEquals(0, hits.get());
      assertEquals(getString("zohocrm.authorize.missingCode"), getMessage());
   }

   @Test
   void undecryptableSecretIsReportedWithoutRequest() {
      ZohoCRMDataSource dataSource = createDataSource();
      // the value that a failed master password decryption leaves in place
      dataSource.setClientSecret("\\masterRmFrZUNpcGhlcnRleHQ=");
      dataSource.authorize("session");

      assertEquals(0, hits.get());
      assertEquals(getString("zohocrm.undecryptable"), getMessage());
   }

   @Test
   void undecryptableRefreshTokenIsReportedWithoutRequestAtQueryTime() {
      ZohoCRMDataSource dataSource = createDataSource();
      // the value that a failed master password decryption leaves in place
      dataSource.setRefreshToken("\\masterRmFrZVJlZnJlc2hUb2tlbg==");
      dataSource.setTokenExpiration(System.currentTimeMillis() - 60000L);
      dataSource.refreshTokens();

      assertEquals(0, hits.get());
      assertEquals("fake-old-access", dataSource.getAccessToken());
      assertEquals(getString("zohocrm.undecryptable"), getMessage());
   }

   @Test
   void successUpdatesTokensAndAsksToSave() {
      responseBody = "{\"access_token\":\"fake-new-access\",\"refresh_token\":\"fake-new-refresh\"," +
         "\"api_domain\":\"https://api.example\",\"expires_in\":3600}";
      ZohoCRMDataSource dataSource = createDataSource();
      dataSource.authorize("session");

      assertEquals(1, hits.get());
      assertEquals("fake-new-access", dataSource.getAccessToken());
      assertEquals("fake-new-refresh", dataSource.getRefreshToken());
      assertEquals("https://api.example", dataSource.getURL());
      assertTrue(dataSource.getTokenExpiration() > System.currentTimeMillis());
      assertNull(dataSource.getAuthorizationCode());
      assertEquals(getString("zohocrm.authorize.success"), getMessage());
   }

   private ZohoCRMDataSource createDataSource() {
      ZohoCRMDataSource dataSource = new ZohoCRMDataSource();
      dataSource.setClientId("fake-client-id");
      dataSource.setClientSecret("fake-client-secret");
      dataSource.setAccountDomain("http://127.0.0.1:" + server.getAddress().getPort());
      dataSource.setAuthorizationCode("fake-code");
      dataSource.setAccessToken("fake-old-access");
      dataSource.setRefreshToken("fake-old-refresh");
      return dataSource;
   }

   private static TabularView createView(ZohoCRMDataSource dataSource) {
      TabularView view = new LayoutCreator().createLayout(dataSource);
      TabularUtil.refreshView(view, dataSource);
      return view;
   }

   private static TabularButton findButton(TabularView[] views, String method) {
      if(views == null) {
         return null;
      }

      for(TabularView view : views) {
         TabularButton button = view.getButton();

         if(button != null && method.equals(button.getMethod())) {
            return button;
         }

         TabularButton child = findButton(view.getViews(), method);

         if(child != null) {
            return child;
         }
      }

      return null;
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
   private volatile String lastRequest;
   private volatile String responseBody;
}
