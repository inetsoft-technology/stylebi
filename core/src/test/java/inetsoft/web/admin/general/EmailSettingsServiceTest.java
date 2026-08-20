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

package inetsoft.web.admin.general;

import inetsoft.sree.SreeEnv;
import inetsoft.util.Tool;
import inetsoft.web.admin.general.model.*;
import inetsoft.web.admin.general.model.model.*;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class EmailSettingsServiceTest {
   private EmailSettingsService service;
   private MockedStatic<SreeEnv> sreeEnvStatic;
   private MockedStatic<Tool> toolStatic;

   @BeforeEach
   void setUp() {
      service = new EmailSettingsService();
      sreeEnvStatic = mockStatic(SreeEnv.class, withSettings().lenient());
      toolStatic =
         mockStatic(Tool.class, withSettings().lenient().defaultAnswer(CALLS_REAL_METHODS));
      toolStatic.when(Tool::isCloudSecrets).thenReturn(false);

      // properties that are required to build the model
      sreeEnvStatic.when(() -> SreeEnv.getProperty("mail.smtp.auth")).thenReturn("saslXOauth2");
      sreeEnvStatic.when(() -> SreeEnv.getProperty("mail.from.address"))
         .thenReturn("noreply@example.com");
   }

   @AfterEach
   void tearDown() {
      toolStatic.close();
      sreeEnvStatic.close();
   }

   // the OAuth flags are written to mail.smtp.oauthFlags, so they must be read from the same
   // property and as a plain text property
   @Test
   void getModelReadsOAuthFlagsFromWrittenProperty() {
      sreeEnvStatic.when(() -> SreeEnv.getProperty("mail.smtp.oauthFlags"))
         .thenReturn("flag1 flag2");

      assertEquals("flag1 flag2", service.getModel().smtpOAuthFlags());
      sreeEnvStatic.verify(() -> SreeEnv.getPassword("mail.smtp.oauthFlags"), never());
   }

   // deployments that set the misspelled property directly must keep working
   @Test
   void getModelFallsBackToLegacyOAuthFlagsProperty() {
      sreeEnvStatic.when(() -> SreeEnv.getProperty("mail.smtp.outhFlags")).thenReturn("legacyFlag");

      assertEquals("legacyFlag", service.getModel().smtpOAuthFlags());
   }

   @Test
   void getModelPrefersOAuthFlagsOverLegacyProperty() {
      sreeEnvStatic.when(() -> SreeEnv.getProperty("mail.smtp.oauthFlags")).thenReturn("newFlag");
      sreeEnvStatic.when(() -> SreeEnv.getProperty("mail.smtp.outhFlags")).thenReturn("legacyFlag");

      assertEquals("newFlag", service.getModel().smtpOAuthFlags());
   }

   @Test
   void getModelReturnsNullOAuthFlagsWhenNotConfigured() {
      assertNull(service.getModel().smtpOAuthFlags());
   }

   // the flags are a distinct parameter of the authorization request, so they must not be
   // folded into the requested scopes
   @Test
   void getOAuthParamsSendsFlagsAsFlags() {
      OAuthParams params = service.getOAuthParams(oauthParamsRequest("scope1 scope2", "flag1 flag2"));

      assertEquals(Set.of("flag1", "flag2"), params.flags());
      assertEquals(List.of("scope1", "scope2"), params.scope());
   }

   @Test
   void getOAuthParamsIgnoresExtraWhitespaceInFlags() {
      OAuthParams params = service.getOAuthParams(oauthParamsRequest("scope1", "  flag1   flag2  "));

      assertEquals(Set.of("flag1", "flag2"), params.flags());
   }

   // a blank value must not turn into a single empty flag
   @Test
   void getOAuthParamsSendsNoFlagsWhenNotConfigured() {
      assertNull(service.getOAuthParams(oauthParamsRequest("scope1", null)).flags());
      assertNull(service.getOAuthParams(oauthParamsRequest("scope1", "")).flags());
      assertNull(service.getOAuthParams(oauthParamsRequest("scope1", "   ")).flags());
   }

   // the misspelled property is a read-only fallback, so saving must not leave it behind to
   // shadow flags that were cleared in the EM
   @Test
   void setModelClearsLegacyOAuthFlagsProperty() throws Exception {
      service.setModel(emailSettingsModel(SMTPAuthType.SASL_XOAUTH2, "flag1"), null);

      sreeEnvStatic.verify(() -> SreeEnv.setProperty("mail.smtp.oauthFlags", "flag1"));
      sreeEnvStatic.verify(() -> SreeEnv.remove("mail.smtp.outhFlags"));
   }

   @Test
   void setModelLeavesOAuthFlagsAloneForGoogleAuth() throws Exception {
      service.setModel(emailSettingsModel(SMTPAuthType.GOOGLE_AUTH, null), null);

      sreeEnvStatic.verify(() -> SreeEnv.setProperty(eq("mail.smtp.oauthFlags"), any()), never());
      sreeEnvStatic.verify(() -> SreeEnv.remove("mail.smtp.outhFlags"), never());
   }

   private EmailSettingsModel emailSettingsModel(SMTPAuthType authType, String flags) {
      return EmailSettingsModel.builder()
         .smtpAuthentication(authType)
         .smtpHost("smtp.example.com")
         .fromAddress("noreply@example.com")
         .ssl(false)
         .tls(false)
         .smtpOAuthFlags(flags)
         .build();
   }

   private OAuthParamsRequest oauthParamsRequest(String scope, String flags) {
      sreeEnvStatic.when(() -> SreeEnv.getProperty("license.key")).thenReturn("LICENSE-KEY,OTHER");

      return OAuthParamsRequest.builder()
         .user("user@example.com")
         .clientId("client-id")
         .clientSecret("client-secret")
         .authorizationUri("https://example.com/authorize")
         .tokenUri("https://example.com/token")
         .scope(scope)
         .flags(flags)
         .build();
   }
}
