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
package inetsoft.uql.rest.auth;

import inetsoft.test.*;
import inetsoft.uql.rest.datasource.quickbooks.QuickbooksReportsDataSource;
import inetsoft.uql.tabular.HttpParameter;
import inetsoft.uql.tabular.oauth.AuthorizationClient;
import inetsoft.uql.tabular.oauth.Tokens;
import inetsoft.util.credential.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #82260: a failed OAuth token refresh was only logged, the query was then sent with the stale
 * or missing access token and failed with a misleading 401 "Malformed bearer token". The refresh
 * failure must surface, naming the refresh failure and asking to authorize the data source again,
 * and no request header may be built from the stale token.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, RefreshTokenFailureTest.CredentialConfig.class },
   initializers = ConfigurationContextInitializer.class)
@SreeHome
class RefreshTokenFailureTest {
   @Test
   void failedRefreshThrowsAndNoBearerHeaderIsBuilt() {
      QuickbooksReportsDataSource ds = source(System.currentTimeMillis() - 60_000L);

      try(MockedStatic<AuthorizationClient> client = mockStatic(AuthorizationClient.class)) {
         stubRefresh(client).thenThrow(
            new Exception("Failed to refresh token [invalid_grant]: refresh token expired"));

         HttpParameter[][] headers = new HttpParameter[1][];
         RuntimeException ex =
            assertThrows(RuntimeException.class, () -> headers[0] = ds.getQueryHttpParameters());

         assertNull(headers[0], "no Authorization header may be built from the stale token");
         String message = ex.getMessage();
         assertTrue(message.contains("qbDs"), message);
         assertTrue(message.toLowerCase().contains("refresh"), message);
         assertTrue(message.contains("invalid_grant"), message);
         assertTrue(message.toLowerCase().contains("authorize"), message);
         assertNotNull(ex.getCause());
      }
   }

   @Test
   void validTokenDoesNotRefreshOrThrow() {
      QuickbooksReportsDataSource ds = source(System.currentTimeMillis() + 3_600_000L);

      try(MockedStatic<AuthorizationClient> client = mockStatic(AuthorizationClient.class)) {
         HttpParameter[] params = ds.getQueryHttpParameters();

         assertEquals("Bearer stale", params[0].getValue());
         client.verifyNoInteractions();
      }
   }

   @Test
   void successfulRefreshUsesTheNewToken() {
      QuickbooksReportsDataSource ds = source(System.currentTimeMillis() - 60_000L);
      Tokens tokens = Tokens.builder().accessToken("fresh").refreshToken("R2")
         .issued(System.currentTimeMillis())
         .expiration(System.currentTimeMillis() + 3_600_000L).build();

      try(MockedStatic<AuthorizationClient> client = mockStatic(AuthorizationClient.class)) {
         stubRefresh(client).thenReturn(tokens);

         assertEquals("Bearer fresh", ds.getQueryHttpParameters()[0].getValue());
      }
   }

   private static org.mockito.stubbing.OngoingStubbing<Tokens> stubRefresh(
      MockedStatic<AuthorizationClient> client)
   {
      return client.when(() -> AuthorizationClient.refresh(any(), any(), any(), any(), any(),
                                                           any(), anyBoolean(), any()));
   }

   private static QuickbooksReportsDataSource source(long expiration) {
      QuickbooksReportsDataSource ds = new QuickbooksReportsDataSource();
      ds.setName("qbDs");
      ds.setClientId("client");
      ds.setClientSecret("secret");
      ds.setAccessToken("stale");
      ds.setRefreshToken("R");
      ds.setTokenExpiration(expiration);
      return ds;
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
}
