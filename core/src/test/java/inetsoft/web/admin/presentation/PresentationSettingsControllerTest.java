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

/*
 * Test strategy
 *
 * PresentationSettingsController has three endpoints sharing a "globalProperty /
 * globalSettings" flag that controls whether global-only or org-scoped sub-models
 * are read and written.
 *
 * --- getSettings ---
 *   globalProperty = (isSiteAdmin && !orgSettings) || !securityEnabled
 *                    || (securityEnabled && !isMultiTenant)
 *     [site admin, orgSettings=false]           globalProperty=true
 *     [site admin, orgSettings=true + MT + sec] globalProperty=false → orgSettings=true in result
 *     [invalid org]                             InvalidOrgException thrown
 *
 * --- applySettings ---
 *   globalSettings controls whether fontMapping and AI setters are called:
 *     [global]     fontMappingSettingsService.setModel and aiSettingsService.setModel called
 *     [non-global] those two services skipped even when non-null sub-models present
 *   Distributed lock acquired and released around service calls.
 *
 * --- resetSettings ---
 *   Same globalSettings gate for fontMapping and AI:
 *     [global]     fontMappingSettingsService.resetSettings and aiSettingsService.resetSettings called
 *     [non-global] those two services skipped
 */

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.*;
import inetsoft.util.InvalidOrgException;
import inetsoft.web.admin.content.dataspace.DataSpaceContentSettingsService;
import inetsoft.web.admin.general.WebMapSettingsService;
import inetsoft.web.admin.general.model.WebMapSettingsModel;
import inetsoft.web.admin.presentation.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.util.concurrent.locks.Lock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class PresentationSettingsControllerTest {

   @Mock private PresentationFormatsSettingsService formatsSettingsService;
   @Mock private LookAndFeelService lookAndFeelService;
   @Mock private WelcomePageService welcomePageService;
   @Mock private PresentationLoginBannerSettingsService loginBannerSettingsService;
   @Mock private PresentationToolbarSettingsService toolbarSettingsService;
   @Mock private PortalIntegrationViewSettingsService portalIntegrationViewSettingsService;
   @Mock private PresentationDashboardSettingsService dashboardSettingsService;
   @Mock private PresentationPdfGenerationSettingsService pdfGenerationSettingsService;
   @Mock private ExportMenuSettingsService exportMenuSettingsService;
   @Mock private PresentationFontMappingSettingsService fontMappingSettingsService;
   @Mock private ShareSettingsService shareSettingsService;
   @Mock private SecurityEngine securityEngine;
   @Mock private PresentationComposerMessageSettingsService composerMessageSettingsService;
   @Mock private PresentationTimeSettingsService timeSettingsService;
   @Mock private PresentationDataSourceVisibilitySettingsService dataSourceVisibilitySettingsService;
   @Mock private WebMapSettingsService webMapSettingsService;
   @Mock private DataSpaceContentSettingsService dataSpaceContentSettingsService;
   @Mock private AISettingsService aiSettingsService;
   @Mock private Cluster cluster;
   @Mock private Lock settingsLock;
   @Mock private SecurityProvider securityProvider;
   @Mock private OrganizationManager orgManager;
   @Mock private Organization organization;
   @Mock private Principal principal;

   private PresentationSettingsController controller;
   private MockedStatic<SUtil> sUtilStatic;
   private MockedStatic<OrganizationManager> orgManagerStatic;

   @BeforeEach
   void setUp() {
      controller = new PresentationSettingsController(
         formatsSettingsService, lookAndFeelService, welcomePageService,
         loginBannerSettingsService, toolbarSettingsService,
         portalIntegrationViewSettingsService, dashboardSettingsService,
         pdfGenerationSettingsService, exportMenuSettingsService,
         fontMappingSettingsService, shareSettingsService, securityEngine,
         composerMessageSettingsService, timeSettingsService,
         dataSourceVisibilitySettingsService, webMapSettingsService,
         dataSpaceContentSettingsService, aiSettingsService, cluster);

      sUtilStatic = mockStatic(SUtil.class, withSettings().lenient());
      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);

      // safe defaults
      lenient().when(principal.getName()).thenReturn("user");
      lenient().when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);
      lenient().when(securityProvider.isVirtual()).thenReturn(false);    // security enabled
      lenient().when(securityProvider.getOrganization(any())).thenReturn(organization);
      lenient().when(orgManager.getCurrentOrgID()).thenReturn("org1");
      sUtilStatic.when(SUtil::isMultiTenant).thenReturn(true);
      lenient().when(cluster.getLock(any())).thenReturn(settingsLock);
   }

   @AfterEach
   void tearDown() {
      sUtilStatic.close();
      orgManagerStatic.close();
   }

   // -------------------------------------------------------------------------
   // getSettings — globalProperty flag
   // -------------------------------------------------------------------------

   // [site admin, orgSettings=false] globalProperty=true → orgSettings field false in result
   @Test
   void getSettings_siteAdminOrgSettingsFalse_globalPropertyTrue() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      stubAllGetters(true);

      PresentationSettingsModel result = controller.getSettings(principal, false);

      assertFalse(result.orgSettings());
      verify(lookAndFeelService).getModel(principal, true);
      verify(aiSettingsService).getModel();    // aiSettingsModel only included when global
   }

   // [site admin, orgSettings=true, security enabled, multi-tenant] globalProperty=false
   @Test
   void getSettings_siteAdminOrgSettingsTrue_multiTenant_globalPropertyFalse() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      stubAllGetters(false);

      PresentationSettingsModel result = controller.getSettings(principal, true);

      assertTrue(result.orgSettings());
      verify(lookAndFeelService).getModel(principal, false);
      verify(aiSettingsService, never()).getModel(); // AI skipped when non-global
   }

   // [invalid org] getOrganization returns null → InvalidOrgException
   @Test
   void getSettings_invalidOrg_throwsInvalidOrgException() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      when(securityProvider.getOrganization(any())).thenReturn(null);

      assertThrows(InvalidOrgException.class,
         () -> controller.getSettings(principal, false));
   }

   // -------------------------------------------------------------------------
   // applySettings — lock + globalSettings gate
   // -------------------------------------------------------------------------

   // [global] fontMapping and AI setters called when globalSettings=true
   @Test
   void applySettings_globalSettings_callsFontMappingAndAi() throws Exception {
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      stubAllGetters(true);

      PresentationFontMappingSettingsModel fontModel = mock(PresentationFontMappingSettingsModel.class);
      PresentationAISettingsModel aiModel = mock(PresentationAISettingsModel.class);

      PresentationSettingsModel input = PresentationSettingsModel.builder()
         .orgSettings(false)
         .fontMappingSettingsModel(fontModel)
         .aiSettingsModel(aiModel)
         .build();

      controller.applySettings(input, principal);

      verify(fontMappingSettingsService).setModel(fontModel);
      verify(aiSettingsService).setModel(aiModel);
   }

   // [non-global] fontMapping and AI setters skipped when globalSettings=false
   @Test
   void applySettings_nonGlobalSettings_skipsFontMappingAndAi() throws Exception {
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);
      sUtilStatic.when(SUtil::isMultiTenant).thenReturn(true);
      when(securityProvider.isVirtual()).thenReturn(false);
      stubAllGetters(false);

      PresentationFontMappingSettingsModel fontModel = mock(PresentationFontMappingSettingsModel.class);
      PresentationAISettingsModel aiModel = mock(PresentationAISettingsModel.class);

      PresentationSettingsModel input = PresentationSettingsModel.builder()
         .orgSettings(true)
         .fontMappingSettingsModel(fontModel)
         .aiSettingsModel(aiModel)
         .build();

      controller.applySettings(input, principal);

      verify(fontMappingSettingsService, never()).setModel(any());
      verify(aiSettingsService, never()).setModel(any());
   }

   // lock is acquired and released around service calls
   @Test
   void applySettings_acquiresAndReleasesLock() throws Exception {
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      stubAllGetters(true);

      controller.applySettings(
         PresentationSettingsModel.builder().orgSettings(false).build(), principal);

      InOrder order = inOrder(settingsLock);
      order.verify(settingsLock).lock();
      order.verify(settingsLock).unlock();
   }

   // -------------------------------------------------------------------------
   // resetSettings — globalSettings gate for fontMapping and AI
   // -------------------------------------------------------------------------

   // [global] fontMapping and AI reset methods called
   @Test
   void resetSettings_globalSettings_resetsFontMappingAndAi() throws Exception {
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      stubAllGetters(true);

      controller.resetSettings(
         PresentationSettingsModel.builder().orgSettings(false).build(), principal);

      verify(fontMappingSettingsService).resetSettings();
      verify(aiSettingsService).resetSettings();
   }

   // [non-global] fontMapping and AI reset skipped
   @Test
   void resetSettings_nonGlobalSettings_skipsFontMappingAndAiReset() throws Exception {
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);
      when(securityProvider.isVirtual()).thenReturn(false);
      sUtilStatic.when(SUtil::isMultiTenant).thenReturn(true);
      stubAllGetters(false);

      // checkPermission returns false so the "non-MT override" doesn't flip globalSettings
      lenient().when(securityProvider.checkPermission(
            any(), eq(ResourceType.EM), eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(false);

      controller.resetSettings(
         PresentationSettingsModel.builder().orgSettings(true).build(), principal);

      verify(fontMappingSettingsService, never()).resetSettings();
      verify(aiSettingsService, never()).resetSettings();
   }

   // -------------------------------------------------------------------------
   // helpers
   // -------------------------------------------------------------------------

   private void stubAllGetters(boolean global) {
      lenient().when(lookAndFeelService.getModel(any(), eq(global)))
         .thenReturn(mock(LookAndFeelSettingsModel.class));
      lenient().when(welcomePageService.getModel(global))
         .thenReturn(mock(WelcomePageSettingsModel.class));
      lenient().when(formatsSettingsService.getModel(global))
         .thenReturn(mock(PresentationFormatsSettingsModel.class));
      lenient().when(loginBannerSettingsService.getModel(global))
         .thenReturn(mock(PresentationLoginBannerSettingsModel.class));
      lenient().when(toolbarSettingsService.getViewsheetOptions(global))
         .thenReturn(mock(PresentationViewsheetToolbarOptionsModel.class));
      lenient().when(portalIntegrationViewSettingsService.getModel(any(), eq(global)))
         .thenReturn(mock(PortalIntegrationSettingsModel.class));
      lenient().when(dashboardSettingsService.getModel(global))
         .thenReturn(mock(PresentationDashboardSettingsModel.class));
      lenient().when(pdfGenerationSettingsService.getModel(global))
         .thenReturn(mock(PresentationPdfGenerationSettingsModel.class));
      lenient().when(exportMenuSettingsService.getExportMenuSettings(global))
         .thenReturn(mock(PresentationExportMenuSettingsModel.class));
      lenient().when(fontMappingSettingsService.getModel())
         .thenReturn(mock(PresentationFontMappingSettingsModel.class));
      lenient().when(shareSettingsService.getModel(global))
         .thenReturn(mock(PresentationShareSettingsModel.class));
      lenient().when(composerMessageSettingsService.getModel(global))
         .thenReturn(mock(PresentationComposerMessageSettingsModel.class));
      lenient().when(timeSettingsService.getModel(global))
         .thenReturn(mock(PresentationTimeSettingsModel.class));
      lenient().when(dataSourceVisibilitySettingsService.getModel(global))
         .thenReturn(mock(PresentationDataSourceVisibilitySettingsModel.class));
      lenient().when(webMapSettingsService.getModel(global))
         .thenReturn(mock(WebMapSettingsModel.class));
      if(global) {
         lenient().when(aiSettingsService.getModel())
            .thenReturn(mock(PresentationAISettingsModel.class));
      }
   }
}
