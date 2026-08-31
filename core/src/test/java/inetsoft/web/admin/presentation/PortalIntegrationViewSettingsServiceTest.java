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
package inetsoft.web.admin.presentation;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.portal.PortalTab;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.util.MessageException;
import inetsoft.web.admin.presentation.model.PortalIntegrationSettingsModel;
import inetsoft.web.admin.presentation.model.PortalTabModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * setModel resolves a submitted tab by a caller-echoed originalIndex into the manager's live,
 * shared tab list. If another request reordered or shrank that list between when the caller read
 * the model and when it submitted, the stale index either throws a raw IndexOutOfBoundsException
 * or silently applies the edit to the wrong tab. These tests pin the fix: an out-of-range or
 * identity-mismatched index must fail loud with a clean, field-named error instead.
 *
 * Tier: [mock] -- PortalThemesManager is mocked directly and field-injected, no Spring context.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class PortalIntegrationViewSettingsServiceTest {
   @Mock private PortalThemesManager manager;
   @Mock private Principal principal;

   @InjectMocks
   private PortalIntegrationViewSettingsService service;

   private MockedStatic<SreeEnv> sreeEnv;

   @BeforeEach
   void setUp() {
      sreeEnv = Mockito.mockStatic(SreeEnv.class);
   }

   @AfterEach
   void tearDown() {
      sreeEnv.close();
   }

   @Test
   void setModel_staleIndexOutOfBounds_throwsCleanError() throws Exception {
      List<PortalTab> currentTabs = Arrays.asList(
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

      assertThrows(MessageException.class, () -> service.setModel(model, principal, true));
      verify(manager, never()).setPortalTabs(any());
   }

   @Test
   void setModel_staleIndexPointsAtDifferentBuiltInTab_throwsCleanError() throws Exception {
      // caller read [Dashboard(0), Report(1)] but another request reordered it to
      // [Dashboard(0), Schedule(1)] before this submission arrived
      List<PortalTab> currentTabs = Arrays.asList(
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

      assertThrows(MessageException.class, () -> service.setModel(model, principal, true));
      verify(manager, never()).setPortalTabs(any());
   }

   @Test
   void setModel_nonRacingBaseline_appliesNormally() throws Exception {
      PortalTab editableTab = new PortalTab("OldName", "/old-uri", true, true);
      List<PortalTab> currentTabs = Arrays.asList(
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

      service.setModel(model, principal, true);

      ArgumentCaptor<List<PortalTab>> captor = ArgumentCaptor.forClass(List.class);
      verify(manager).setPortalTabs(captor.capture());
      List<PortalTab> saved = captor.getValue();
      assertEquals(2, saved.size());
      assertEquals("NewName", saved.get(1).getName());
      assertEquals("/new-uri", saved.get(1).getURI());
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
