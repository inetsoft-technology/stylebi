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

import inetsoft.test.*;
import inetsoft.uql.rest.json.RestJsonDataSource;
import inetsoft.util.credential.*;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, TwoStepAuthTest.TestConfig.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
public class TwoStepAuthTest {
   private CloseableHttpAsyncClient client;

   @BeforeEach
   public void createClient() {
      client = HttpAsyncClients.createDefault();
      client.start();
   }

   @AfterEach
   public void closeClient() throws IOException {
      client.close();
   }

   @Test
   void validCreation() {
      final RestJsonDataSource datasource = createValidDatasource();
      RestAuthenticatorFactory.createFrom(datasource, client);
   }

   @Test
   void emptyAuthURL() {
      final RestJsonDataSource datasource = createValidDatasource();
      datasource.setAuthURL(null);

      assertThrows(
         IllegalArgumentException.class,
         () -> RestAuthenticatorFactory.createFrom(datasource, client));
   }

   @Test
   public void emptyTokenPattern() {
      final RestJsonDataSource datasource = createValidDatasource();
      datasource.setTokenPattern(null);

      assertThrows(
         IllegalArgumentException.class,
         () -> RestAuthenticatorFactory.createFrom(datasource, client));
   }

   private RestJsonDataSource createValidDatasource() {
      final RestJsonDataSource restJsonDataSource = new RestJsonDataSource();
      restJsonDataSource.setAuthType(AuthType.TWO_STEP);
      restJsonDataSource.setAuthURL("auth url");
      restJsonDataSource.setTokenPattern("token");
      return restJsonDataSource;
   }

   @Configuration
   static class TestConfig {
      @Bean
      public CredentialService credentialService() {
         CredentialService credentialService = mock(CredentialService.class);
         when(credentialService.createCredential(CredentialType.PASSWORD_OAUTH2_WITH_FLAGS)).thenReturn(new LocalPasswordAndOAuth2WithFlagCredentialsGrant());
         when(credentialService.createCredential(CredentialType.PASSWORD_OAUTH2_WITH_FLAGS, true)).thenReturn(new LocalPasswordAndOAuth2WithFlagCredentialsGrant());
         return credentialService;
      }
   }
}
