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
package inetsoft.uql.rest.datasource.salesforce;

import com.sun.net.httpserver.HttpServer;
import inetsoft.test.*;
import inetsoft.util.credential.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.OutputStream;
import java.util.regex.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #82125: a null password or security token must be sent as empty text, not as "null".
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, SalesforceLoginPasswordNullTest.CredentialConfig.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
class SalesforceLoginPasswordNullTest {
   @Configuration
   static class CredentialConfig {
      @Bean
      public CredentialService credentialService() {
         CredentialService credentialService = mock(CredentialService.class);
         when(credentialService.createCredential(
            eq(CredentialType.PASSWORD_OAUTH2_WITH_FLAGS), anyBoolean()))
            .thenAnswer(invocation -> new LocalPasswordAndOAuth2WithFlagCredentialsGrant());
         return credentialService;
      }
   }

   static class TestSource extends SalesforceDataSource<TestSource> {
      TestSource(String loginUrl) {
         super("Rest.Remedyforce", TestSource.class);
         this.loginUrl = loginUrl;
      }

      @Override
      protected String getUrlSuffix() {
         return "/data/v50.0";
      }

      @Override
      protected String getTestSuffix() {
         return "/data";
      }

      @Override
      protected String getLoginUrl() {
         return loginUrl;
      }

      private final String loginUrl;
   }

   @BeforeEach
   void startServer() throws Exception {
      sent = "";
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/", exchange -> {
         sent = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
         exchange.sendResponseHeaders(500, -1);
         exchange.close();
      });
      server.start();
   }

   @AfterEach
   void stopServer() {
      server.stop(0);
   }

   private String loginPassword(String password, String token) {
      TestSource ds = new TestSource("http://127.0.0.1:" + server.getAddress().getPort() +
                                     "/services/Soap/u/35.0");
      ds.setName("sf-" + System.nanoTime());
      ds.setUser("user-" + System.nanoTime());
      ds.setPassword(password);
      ds.setSecurityToken(token);
      assertThrows(RuntimeException.class, ds::getURL);
      Matcher m = Pattern.compile("<password>(.*?)</password>|<password/>").matcher(sent);
      assertTrue(m.find(), sent);
      return m.group(1) == null ? "" : m.group(1);
   }

   @Test
   void passwordIsSentUnchangedWhenThereIsNoSecurityToken() {
      assertEquals("pw", loginPassword("pw", null));
   }

   @Test
   void passwordAndTokenAreConcatenated() {
      assertEquals("pwTOKEN", loginPassword("pw", "TOKEN"));
   }

   @Test
   void nullPasswordIsSentAsEmpty() {
      assertEquals("TOKEN", loginPassword(null, "TOKEN"));
   }

   @Test
   void bothNullSendEmptyPassword() {
      assertEquals("", loginPassword(null, null));
   }

   private HttpServer server;
   private volatile String sent;
}
