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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #82260: AuthorizationClient.refreshTokens (used by OAuthAuthenticator and the OData client)
 * returned null when the refresh failed, which callers read as "no refresh needed" and went on
 * with the stale token. A failed refresh must throw, naming the data source and the cause.
 */
@Tag("core")
class AuthorizationClientRefreshFailureTest {
   @Test
   void failedRefreshThrows() {
      try(MockedStatic<AuthorizationClient> client =
             mockStatic(AuthorizationClient.class, CALLS_REAL_METHODS))
      {
         client.when(() -> AuthorizationClient.refresh(any(), any(), any(), any(), any(), any(),
                                                       anyBoolean(), any()))
            .thenThrow(new Exception("Failed to refresh token [invalid_grant]: expired"));

         RuntimeException ex = assertThrows(
            RuntimeException.class,
            () -> AuthorizationClient.refreshTokens(source(1L), false));

         String message = ex.getMessage();
         assertTrue(message.contains("svc"), message);
         assertTrue(message.toLowerCase().contains("refresh"), message);
         assertTrue(message.contains("invalid_grant"), message);
         assertTrue(message.toLowerCase().contains("authorize"), message);
         assertNotNull(ex.getCause());
      }
   }

   @Test
   void validTokenIsNotRefreshed() {
      try(MockedStatic<AuthorizationClient> client =
             mockStatic(AuthorizationClient.class, CALLS_REAL_METHODS))
      {
         assertNull(AuthorizationClient.refreshTokens(
            source(System.currentTimeMillis() + 3_600_000L), false));
         client.verify(() -> AuthorizationClient.refresh(any(), any(), any(), any(), any(), any(),
                                                         anyBoolean(), any()),
                       never());
      }
   }

   private static OAuthDataSource source(long expiration) {
      return new OAuthDataSource() {
         @Override public void updateTokens(Tokens tokens) { }
         @Override public String getServiceName() { return "svc"; }
         @Override public String getRefreshToken() { return "R"; }
         @Override public long getTokenExpiration() { return expiration; }
      };
   }
}
