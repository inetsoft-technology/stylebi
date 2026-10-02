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
 *
 * setModel also resolves a submitted tab by a caller-echoed originalIndex into the manager's
 * live, shared tab list. If another request reordered or shrank that list between when the
 * caller read the model and when it submitted, the stale index either threw a raw
 * IndexOutOfBoundsException or silently applied the edit to the wrong tab:
 *
 * [G4] An out-of-range or identity-mismatched index fails with a clean MessageException and
 *      does not write the tabs.
 * [G5] A non-racing edit of an editable tab is still applied.
 */

import inetsoft.sree.SreeEnv;
import inetsoft.sree.portal.PortalTab;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.util.MessageException;
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

   @Test
   void setModel_staleIndexOutOfBounds_throwsCleanError() throws Exception {
      List<PortalTab> currentTabs = List.of(
         new PortalTab("Dashboard", "/dashboard", true, false),
         new PortalTab("Report", "/report", true, false));
      when(manager.getPortalTabs()).thenReturn(currentTabs);

      PortalTabModel staleTabModel = PortalTabModel.builder()
         .name("Data")
         .label("Data")
         .uri("/data")
         .visible(true)
         .editable(false)
         .originalIndex(2)
         .build();

      PortalIntegrationSettingsModel model = baseModelBuilder()
         .addTabs(staleTabModel)
         .build();

      assertThrows(MessageException.class, () -> service.setModel(model, mock(Principal.class), true));
      verify(manager, never()).setPortalTabs(any());
   }

   @Test
   void setModel_staleIndexPointsAtDifferentBuiltInTab_throwsCleanError() throws Exception {
      // caller read [Dashboard(0), Report(1)] but another request reordered it to
      // [Dashboard(0), Schedule(1)] before this submission arrived
      List<PortalTab> currentTabs = List.of(
         new PortalTab("Dashboard", "/dashboard", true, false),
         new PortalTab("Schedule", "/schedule", true, false));
      when(manager.getPortalTabs()).thenReturn(currentTabs);

      PortalTabModel staleTabModel = PortalTabModel.builder()
         .name("Report")
         .label("Repository")
         .uri("/report")
         .visible(false)
         .editable(false)
         .originalIndex(1)
         .build();

      PortalIntegrationSettingsModel model = baseModelBuilder()
         .addTabs(staleTabModel)
         .build();

      assertThrows(MessageException.class, () -> service.setModel(model, mock(Principal.class), true));
      verify(manager, never()).setPortalTabs(any());
   }

   @Test
   @SuppressWarnings("unchecked")
   void setModel_nonRacingBaseline_appliesNormally() throws Exception {
      PortalTab editableTab = new PortalTab("OldName", "/old-uri", true, true);
      List<PortalTab> currentTabs = List.of(
         new PortalTab("Dashboard", "/dashboard", true, false),
         editableTab);
      when(manager.getPortalTabs()).thenReturn(currentTabs);

      PortalTabModel builtInTabModel = PortalTabModel.builder()
         .name("Dashboard")
         .label("Dashboard")
         .uri("/dashboard")
         .visible(true)
         .editable(false)
         .originalIndex(0)
         .build();

      // legitimate, non-racing rename of an editable/custom tab
      PortalTabModel renamedTabModel = PortalTabModel.builder()
         .name("NewName")
         .label("NewName")
         .uri("/new-uri")
         .visible(true)
         .editable(true)
         .originalIndex(1)
         .build();

      PortalIntegrationSettingsModel model = baseModelBuilder()
         .addTabs(builtInTabModel, renamedTabModel)
         .build();

      service.setModel(model, mock(Principal.class), true);

      org.mockito.ArgumentCaptor<List<PortalTab>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
      verify(manager).setPortalTabs(captor.capture());
      List<PortalTab> saved = captor.getValue();
      assertEquals(2, saved.size());
      assertEquals("NewName", saved.get(1).getName());
      assertEquals("/new-uri", saved.get(1).getURI());
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

   private PortalIntegrationSettingsModel.Builder baseModelBuilder() {
      return PortalIntegrationSettingsModel.builder()
         .help(true)
         .preference(true)
         .logout(true)
         .search(true)
         .dashboardAvailable(true)
         .home(true);
   }
}
