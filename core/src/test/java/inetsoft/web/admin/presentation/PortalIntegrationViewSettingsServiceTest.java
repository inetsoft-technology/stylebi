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
package inetsoft.web.admin.presentation;

/*
 * Test strategy
 *
 * PortalIntegrationViewSettingsService.setModel() used to write the portal tool buttons and
 * portal tabs to the global PortalThemesManager even when globalSettings=false (an org admin in
 * a multi-tenant deployment), so an org admin could add a tab to every organization's portal
 * (Bug #77054). resetSettings() already guarded the same writes with globalSettings.
 *
 * Behavioral guarantees covered:
 *
 * [G1] An org-scoped save does not change the global portal buttons or tabs.
 * [G2] An org-scoped save still writes the org-scoped loading text and home links.
 * [G3] A global save still writes the portal buttons and tabs.
 */

import inetsoft.sree.SreeEnv;
import inetsoft.sree.portal.PortalTab;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.web.admin.presentation.model.PortalIntegrationSettingsModel;
import inetsoft.web.admin.presentation.model.PortalTabModel;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class PortalIntegrationViewSettingsServiceTest {
   private PortalIntegrationViewSettingsService service;
   private PortalThemesManager manager;
   private MockedStatic<SreeEnv> sreeEnvStatic;

   @BeforeEach
   void setUp() throws Exception {
      manager = mock(PortalThemesManager.class);
      when(manager.getPortalTabs()).thenReturn(new ArrayList<>());
      sreeEnvStatic = mockStatic(SreeEnv.class, withSettings().lenient());

      service = new PortalIntegrationViewSettingsService();
      Field field = PortalIntegrationViewSettingsService.class.getDeclaredField("portalThemesManager");
      field.setAccessible(true);
      field.set(service, manager);
   }

   @AfterEach
   void tearDown() {
      sreeEnvStatic.close();
   }

   @Test
   void orgScopedSaveDoesNotChangeGlobalButtonsOrTabs() throws Exception {
      service.setModel(model(), mock(Principal.class), false);

      verify(manager, never()).setButtonVisible(anyInt(), anyBoolean());
      verify(manager, never()).setPortalTabs(any());
      verify(manager, never()).save();
   }

   @Test
   void orgScopedSaveStillWritesOrgScopedProperties() throws Exception {
      service.setModel(model(), mock(Principal.class), false);

      sreeEnvStatic.verify(() -> SreeEnv.setProperty("portal.customLoadingText", "Loading", true));
      sreeEnvStatic.verify(() -> SreeEnv.setProperty("portal.home.link", "https://home", true));
      sreeEnvStatic.verify(() -> SreeEnv.setProperty("em.home.link", "https://em", true));
      sreeEnvStatic.verify(SreeEnv::save);
   }

   @Test
   @SuppressWarnings("unchecked")
   void globalSaveStillWritesButtonsAndTabs() throws Exception {
      service.setModel(model(), mock(Principal.class), true);

      verify(manager).setButtonVisible(PortalThemesManager.HELP_BUTTON, false);
      verify(manager).setButtonVisible(PortalThemesManager.HOME_BUTTON, true);
      verify(manager).save();

      var captor = org.mockito.ArgumentCaptor.forClass(List.class);
      verify(manager).setPortalTabs(captor.capture());
      List<PortalTab> tabs = captor.getValue();
      assertEquals(1, tabs.size());
      assertEquals("X", tabs.get(0).getName());
      sreeEnvStatic.verify(() -> SreeEnv.setProperty("portal.home.link", "https://home", false));
   }

   private PortalIntegrationSettingsModel model() {
      return PortalIntegrationSettingsModel.builder()
         .addTabs(PortalTabModel.builder()
                     .name("X")
                     .label("X")
                     .uri("https://example.com")
                     .visible(true)
                     .editable(true)
                     .build())
         .help(false)
         .preference(true)
         .logout(true)
         .search(true)
         .dashboardAvailable(true)
         .home(true)
         .customLoadingText("Loading")
         .homeLink("https://home")
         .emHomeLink("https://em")
         .build();
   }
}
