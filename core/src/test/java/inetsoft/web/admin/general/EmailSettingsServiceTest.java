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
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
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
}
