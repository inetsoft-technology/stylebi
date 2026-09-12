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
package inetsoft.web.portal.controller;

import inetsoft.sree.SreeEnv;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
class GoogleSignInSupportTest {
   private static final String CLIENT_ID_PROPERTY = "styleBI.google.openid.client.id";
   private static final String SCOPES_PROPERTY = "styleBI.google.openid.scopes";
   private static final String DEFAULT_SCOPES = "openid email profile";

   private MockedStatic<SreeEnv> sreeEnvStatic;
   private MockedStatic<Tool> toolStatic;

   @BeforeEach
   void setUp() {
      sreeEnvStatic = mockStatic(SreeEnv.class, withSettings().lenient());
      toolStatic = mockStatic(Tool.class, withSettings().lenient());
   }

   @AfterEach
   void tearDown() {
      toolStatic.close();
      sreeEnvStatic.close();
   }

   // the client id property may hold a reference to a secret kept in a cloud secrets manager,
   // so the page must be given the resolved client id, not the stored reference
   @Test
   void clientIdIsResolvedThroughSecretsManager() {
      sreeEnvStatic.when(() -> SreeEnv.getProperty(CLIENT_ID_PROPERTY)).thenReturn("secret-ref");
      toolStatic.when(() -> Tool.getClientSecretRealValue("secret-ref", "client_id"))
         .thenReturn("real-id.apps.googleusercontent.com");

      assertEquals("real-id.apps.googleusercontent.com", GoogleSignInSupport.getClientId());
   }

   // the stored value and the "client_id" key of the secret are what identify the client id
   // within the secret, so both must reach the resolver
   @Test
   void clientIdPassesPropertyValueAndSecretKeyToResolver() {
      sreeEnvStatic.when(() -> SreeEnv.getProperty(CLIENT_ID_PROPERTY)).thenReturn("secret-ref");

      GoogleSignInSupport.getClientId();

      toolStatic.verify(() -> Tool.getClientSecretRealValue("secret-ref", "client_id"));
   }

   // an unset client id must stay unset so the pages leave the google button out entirely
   @Test
   void clientIdIsNullWhenPropertyNotConfigured() {
      sreeEnvStatic.when(() -> SreeEnv.getProperty(CLIENT_ID_PROPERTY)).thenReturn(null);
      toolStatic.when(() -> Tool.getClientSecretRealValue(null, "client_id")).thenReturn(null);

      assertNull(GoogleSignInSupport.getClientId());
   }

   @Test
   void scopesFallBackToDefaultWhenNotConfigured() {
      sreeEnvStatic.when(() -> SreeEnv.getProperty(SCOPES_PROPERTY, DEFAULT_SCOPES))
         .thenReturn(DEFAULT_SCOPES);

      assertEquals(DEFAULT_SCOPES, GoogleSignInSupport.getScopes());
   }

   @Test
   void scopesUseConfiguredValue() {
      sreeEnvStatic.when(() -> SreeEnv.getProperty(SCOPES_PROPERTY, DEFAULT_SCOPES))
         .thenReturn("openid email");

      assertEquals("openid email", GoogleSignInSupport.getScopes());
   }

   @Test
   void enabledFollowsProperty() {
      sreeEnvStatic.when(() -> SreeEnv.getBooleanProperty("security.googleSignIn.enabled"))
         .thenReturn(true);

      assertTrue(GoogleSignInSupport.isEnabled());
   }
}
