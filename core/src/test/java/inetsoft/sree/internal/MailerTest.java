/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.sree.internal;

import inetsoft.sree.SreeEnv;
import inetsoft.uql.tabular.oauth.AuthorizationClient;
import inetsoft.uql.tabular.oauth.Tokens;
import inetsoft.web.admin.general.model.model.SMTPAuthType;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class MailerTest {
   private MockedStatic<SreeEnv> sreeEnvStatic;
   private MockedStatic<AuthorizationClient> authorizationClientStatic;

   @BeforeEach
   void setUp() throws Exception {
      sreeEnvStatic = mockStatic(SreeEnv.class, withSettings().lenient());
      authorizationClientStatic =
         mockStatic(AuthorizationClient.class, withSettings().lenient());

      // no unexpired access token, so the token is always refreshed
      sreeEnvStatic.when(() -> SreeEnv.getProperty("mail.smtp.tokenExpiration")).thenReturn(null);
      sreeEnvStatic.when(() -> SreeEnv.getProperty("mail.smtp.tokenUri"))
         .thenReturn("https://example.com/token");
      sreeEnvStatic.when(() -> SreeEnv.getPassword("mail.smtp.refreshToken"))
         .thenReturn("refresh-token");

      authorizationClientStatic
         .when(() -> AuthorizationClient.refresh(
            any(), any(), any(), any(), any(), anySet(), anyBoolean(), any()))
         .thenReturn(Tokens.builder()
                        .accessToken("access-token")
                        .refreshToken("refresh-token")
                        .issued(0L)
                        .expiration(0L)
                        .build());
   }

   @AfterEach
   void tearDown() {
      authorizationClientStatic.close();
      sreeEnvStatic.close();
   }

   // the flags configured in the EM must be applied when the access token is refreshed
   @Test
   void refreshAppliesConfiguredOAuthFlags() {
      sreeEnvStatic.when(() -> SreeEnv.getProperty("mail.smtp.oauthFlags"))
         .thenReturn("flag1 flag2");

      Mailer.getAccessToken(SMTPAuthType.SASL_XOAUTH2);

      assertEquals(Set.of("flag1", "flag2"), captureFlags());
   }

   // deployments that set the misspelled property directly must keep working
   @Test
   void refreshFallsBackToLegacyOAuthFlagsProperty() {
      sreeEnvStatic.when(() -> SreeEnv.getProperty("mail.smtp.outhFlags")).thenReturn("legacyFlag");

      Mailer.getAccessToken(SMTPAuthType.SASL_XOAUTH2);

      assertEquals(Set.of("legacyFlag"), captureFlags());
   }

   @Test
   void refreshSendsNoFlagsWhenNotConfigured() {
      Mailer.getAccessToken(SMTPAuthType.SASL_XOAUTH2);

      assertEquals(Set.of(), captureFlags());
   }

   // a blank value must not turn into a single empty flag
   @Test
   void refreshSendsNoFlagsWhenOAuthFlagsAreBlank() {
      sreeEnvStatic.when(() -> SreeEnv.getProperty("mail.smtp.oauthFlags")).thenReturn("   ");

      Mailer.getAccessToken(SMTPAuthType.SASL_XOAUTH2);

      assertEquals(Set.of(), captureFlags());
   }

   // extra whitespace must not turn into empty flags
   @Test
   void refreshIgnoresExtraWhitespaceInOAuthFlags() {
      sreeEnvStatic.when(() -> SreeEnv.getProperty("mail.smtp.oauthFlags"))
         .thenReturn("  flag1   flag2  ");

      Mailer.getAccessToken(SMTPAuthType.SASL_XOAUTH2);

      assertEquals(Set.of("flag1", "flag2"), captureFlags());
   }

   // the flags are only configurable for SASL XOAUTH2. flags left over from a previous SASL
   // XOAUTH2 configuration must not change the google token refresh.
   @Test
   void refreshSendsNoFlagsForGoogleAuth() {
      sreeEnvStatic.when(() -> SreeEnv.getProperty("mail.smtp.oauthFlags"))
         .thenReturn("flag1 flag2");

      Mailer.getAccessToken(SMTPAuthType.GOOGLE_AUTH);

      assertEquals(Set.of(), captureFlags());
   }

   @SuppressWarnings("unchecked")
   private Set<String> captureFlags() {
      ArgumentCaptor<Set<String>> captor = ArgumentCaptor.forClass(Set.class);
      authorizationClientStatic.verify(() -> AuthorizationClient.refresh(
         any(), any(), any(), any(), any(), captor.capture(), anyBoolean(), any()));
      return captor.getValue();
   }
}
