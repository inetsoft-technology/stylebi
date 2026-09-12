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
package inetsoft.web.admin.security;

/*
 * Test strategy
 *
 * Class type: pure-delegation controller — SSOSettingsController contains no in-controller
 * logic; it assembles SSOSettingsModel from service building methods and delegates saves.
 *
 * Coverage scope:
 *   [getSSOSettings]       assembles model from service, passes principal to getRoles()
 *   [updateSSOSettings]    delegates model to service without modification
 */

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class SSOSettingsControllerTest {

   @Mock private SSOSettingsService service;
   @Mock private Principal principal;

   private SSOSettingsController controller;

   @BeforeEach
   void setUp() {
      controller = new SSOSettingsController(service);
   }

   // -------------------------------------------------------------------------
   // getSSOSettings()
   // -------------------------------------------------------------------------

   // model assembled from service; getRoles() receives the principal
   @Test
   void getSSOSettings_assemblesModelFromService() {
      SAMLAttributesModel saml = mock(SAMLAttributesModel.class);
      OpenIdAttributesModel openId = mock(OpenIdAttributesModel.class);
      CustomSSOAttributesModel custom = mock(CustomSSOAttributesModel.class);

      when(service.buildSAMLModel()).thenReturn(saml);
      when(service.buildOpenIdModel()).thenReturn(openId);
      when(service.buildCustomModel()).thenReturn(custom);
      when(service.getActiveFilterType()).thenReturn(SSOType.NONE);
      when(service.getRoles(principal)).thenReturn(null);
      when(service.getSelectedRoles()).thenReturn(null);
      when(service.getLogoutUrl()).thenReturn(null);
      when(service.getLogoutPath()).thenReturn(null);
      when(service.isFallbackLogin()).thenReturn(false);

      SSOSettingsModel result = controller.getSSOSettings(principal);

      assertNotNull(result);
      assertSame(saml, result.samlAttributesModel());
      assertSame(openId, result.openIdAttributesModel());
      assertSame(custom, result.customAttributesModel());
      verify(service).getRoles(principal);
   }

   // -------------------------------------------------------------------------
   // updateSSOSettings()
   // -------------------------------------------------------------------------

   @Test
   void updateSSOSettings_delegatesToService() {
      SSOSettingsModel model = new SSOSettingsModel.Builder()
         .activeFilterType(SSOType.NONE)
         .fallbackLogin(false)
         .build();

      controller.updateSSOSettings(model);

      verify(service).updateSSOSettings(model);
   }
}
