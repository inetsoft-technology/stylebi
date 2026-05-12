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
package inetsoft.web.admin.general;

/*
 * Test strategy
 *
 * GeneralSettingsPageController has two endpoints with in-controller conditional logic:
 *
 * --- getPageModel ---
 *   A multi-tenancy + admin gate decides which sub-models to include:
 *     [multi-tenant non-site-admin]  security on AND isMultiTenant AND !isSiteAdmin
 *                                    → only mvSettingsModel populated; all other fields null
 *     [site admin / not multi-tenant] all conditions not met → full model; all service
 *                                    getters called; securityEnabled = !provider.isVirtual()
 *
 * --- setPageModel ---
 *   Same gate controls which service setters are called:
 *     [multi-tenant non-site-admin]  only mvSettingsService.setModel() called
 *     [site admin]                   all non-null sub-model setters called
 *     [null sub-model]               corresponding service.setModel() is skipped
 *   Always ends by delegating to getPageModel and returning its result.
 */

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.web.admin.general.model.*;
import inetsoft.web.admin.general.model.model.OAuthParams;
import inetsoft.web.admin.general.model.model.OAuthParamsRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class GeneralSettingsPageControllerTest {

   @Mock private LicenseKeySettingsService licenseKeySettingsService;
   @Mock private LocalizationSettingsService localizationSettingsService;
   @Mock private MVSettingsService mvSettingsService;
   @Mock private CacheSettingsService cacheSettingsService;
   @Mock private DataSpaceSettingsService dataSpaceSettingsService;
   @Mock private EmailSettingsService emailSettingsService;
   @Mock private PerformanceSettingsService performanceSettingsService;
   @Mock private SecurityEngine securityEngine;
   @Mock private SecurityProvider securityProvider;
   @Mock private OrganizationManager orgManager;
   @Mock private Principal principal;

   @Mock private MVSettingsModel mvSettingsModel;
   @Mock private LicenseKeySettingsModel licenseKeySettingsModel;
   @Mock private LocalizationSettingsModel localizationSettingsModel;
   @Mock private CacheSettingsModel cacheSettingsModel;
   @Mock private DataSpaceSettingsModel dataSpaceSettingsModel;
   @Mock private EmailSettingsModel emailSettingsModel;
   @Mock private PerformanceSettingsModel performanceSettingsModel;

   private GeneralSettingsPageController controller;
   private MockedStatic<SUtil> sUtilStatic;
   private MockedStatic<OrganizationManager> orgManagerStatic;

   @BeforeEach
   void setUp() {
      controller = new GeneralSettingsPageController(
         licenseKeySettingsService, localizationSettingsService, mvSettingsService,
         cacheSettingsService, dataSpaceSettingsService, emailSettingsService,
         performanceSettingsService, securityEngine);

      sUtilStatic = mockStatic(SUtil.class, withSettings().lenient());
      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);

      lenient().when(principal.getName()).thenReturn("user");
      lenient().when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);
      lenient().when(securityProvider.isVirtual()).thenReturn(false);
   }

   @AfterEach
   void tearDown() {
      sUtilStatic.close();
      orgManagerStatic.close();
   }

   // -------------------------------------------------------------------------
   // getPageModel
   // -------------------------------------------------------------------------

   // [multi-tenant non-site-admin] only mvSettingsModel populated; all others null
   @Test
   void getPageModel_multiTenantNonSiteAdmin_returnsMvModelOnly() throws Exception {
      when(securityEngine.isSecurityEnabled()).thenReturn(true);
      sUtilStatic.when(SUtil::isMultiTenant).thenReturn(true);
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);
      when(mvSettingsService.getModel(principal)).thenReturn(mvSettingsModel);

      GeneralSettingsPageModel model = controller.getPageModel(principal);

      assertSame(mvSettingsModel, model.mvSettingsModel());
      assertNull(model.licenseKeySettingsModel());
      assertNull(model.localizationSettingsModel());
      assertNull(model.cacheSettingsModel());
      assertNull(model.dataSpaceSettingsModel());
      assertNull(model.emailSettingsModel());
      assertNull(model.performanceSettingsModel());
   }

   // [site admin] all service getters called; full model returned
   @Test
   void getPageModel_siteAdmin_returnsFullModel() throws Exception {
      when(securityEngine.isSecurityEnabled()).thenReturn(true);
      sUtilStatic.when(SUtil::isMultiTenant).thenReturn(true);
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      when(licenseKeySettingsService.getModel()).thenReturn(licenseKeySettingsModel);
      when(localizationSettingsService.getModel()).thenReturn(localizationSettingsModel);
      when(mvSettingsService.getModel(principal)).thenReturn(mvSettingsModel);
      when(cacheSettingsService.getModel()).thenReturn(cacheSettingsModel);
      when(dataSpaceSettingsService.getModel(principal)).thenReturn(dataSpaceSettingsModel);
      when(emailSettingsService.getModel()).thenReturn(emailSettingsModel);
      when(performanceSettingsService.getModel()).thenReturn(performanceSettingsModel);

      GeneralSettingsPageModel model = controller.getPageModel(principal);

      assertSame(licenseKeySettingsModel, model.licenseKeySettingsModel());
      assertSame(localizationSettingsModel, model.localizationSettingsModel());
      assertSame(mvSettingsModel, model.mvSettingsModel());
      assertSame(cacheSettingsModel, model.cacheSettingsModel());
      assertSame(dataSpaceSettingsModel, model.dataSpaceSettingsModel());
      assertSame(emailSettingsModel, model.emailSettingsModel());
      assertSame(performanceSettingsModel, model.performanceSettingsModel());
      assertTrue(model.securityEnabled()); // !isVirtual()=false → securityEnabled=true
   }

   // [not multi-tenant] security enabled but not multi-tenant → full model path taken
   @Test
   void getPageModel_securityEnabledNotMultiTenant_returnsFullModel() throws Exception {
      when(securityEngine.isSecurityEnabled()).thenReturn(true);
      sUtilStatic.when(SUtil::isMultiTenant).thenReturn(false);
      when(licenseKeySettingsService.getModel()).thenReturn(licenseKeySettingsModel);
      when(localizationSettingsService.getModel()).thenReturn(localizationSettingsModel);
      when(mvSettingsService.getModel(principal)).thenReturn(mvSettingsModel);
      when(cacheSettingsService.getModel()).thenReturn(cacheSettingsModel);
      when(dataSpaceSettingsService.getModel(principal)).thenReturn(dataSpaceSettingsModel);
      when(emailSettingsService.getModel()).thenReturn(emailSettingsModel);
      when(performanceSettingsService.getModel()).thenReturn(performanceSettingsModel);

      GeneralSettingsPageModel model = controller.getPageModel(principal);

      assertNotNull(model.licenseKeySettingsModel());
      assertNotNull(model.mvSettingsModel());
   }

   // securityEnabled field = !provider.isVirtual()
   @Test
   void getPageModel_virtualProvider_securityEnabledIsFalse() throws Exception {
      when(securityEngine.isSecurityEnabled()).thenReturn(false);
      when(securityProvider.isVirtual()).thenReturn(true);
      lenient().when(mvSettingsService.getModel(principal)).thenReturn(mvSettingsModel);

      GeneralSettingsPageModel model = controller.getPageModel(principal);

      assertFalse(model.securityEnabled());
   }

   // -------------------------------------------------------------------------
   // setPageModel
   // -------------------------------------------------------------------------

   // [multi-tenant non-site-admin] only mvSettingsService.setModel called
   @Test
   void setPageModel_multiTenantNonSiteAdmin_onlyCallsMvService() throws Exception {
      when(securityEngine.isSecurityEnabled()).thenReturn(true);
      sUtilStatic.when(SUtil::isMultiTenant).thenReturn(true);
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);
      when(mvSettingsService.getModel(principal)).thenReturn(mvSettingsModel);

      GeneralSettingsPageModel input = GeneralSettingsPageModel.builder()
         .mvSettingsModel(mvSettingsModel)
         .licenseKeySettingsModel(licenseKeySettingsModel)
         .build();

      controller.setPageModel(input, principal, null);

      verify(mvSettingsService).setModel(mvSettingsModel, principal);
      verify(licenseKeySettingsService, never()).setModel(any(), any());
      verify(localizationSettingsService, never()).setModel(any(), any());
      verify(cacheSettingsService, never()).setModel(any(), any());
      verify(emailSettingsService, never()).setModel(any(), any());
      verify(performanceSettingsService, never()).setModel(any(), any());
   }

   // [site admin] all non-null sub-model setters called
   @Test
   void setPageModel_siteAdmin_callsAllNonNullSubModelSetters() throws Exception {
      when(securityEngine.isSecurityEnabled()).thenReturn(true);
      sUtilStatic.when(SUtil::isMultiTenant).thenReturn(true);
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      stubAllGetters();

      GeneralSettingsPageModel input = GeneralSettingsPageModel.builder()
         .licenseKeySettingsModel(licenseKeySettingsModel)
         .localizationSettingsModel(localizationSettingsModel)
         .mvSettingsModel(mvSettingsModel)
         .cacheSettingsModel(cacheSettingsModel)
         .emailSettingsModel(emailSettingsModel)
         .performanceSettingsModel(performanceSettingsModel)
         .build();

      controller.setPageModel(input, principal, null);

      verify(licenseKeySettingsService).setModel(licenseKeySettingsModel, principal);
      verify(localizationSettingsService).setModel(localizationSettingsModel, principal);
      verify(mvSettingsService).setModel(mvSettingsModel, principal);
      verify(cacheSettingsService).setModel(cacheSettingsModel, principal);
      verify(emailSettingsService).setModel(emailSettingsModel, principal);
      verify(performanceSettingsService).setModel(performanceSettingsModel, principal);
   }

   // [null sub-model] null mvSettingsModel → mvSettingsService.setModel not called
   @Test
   void setPageModel_nullMvModel_skipsMvService() throws Exception {
      when(securityEngine.isSecurityEnabled()).thenReturn(true);
      sUtilStatic.when(SUtil::isMultiTenant).thenReturn(true);
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      stubAllGetters();

      GeneralSettingsPageModel input = GeneralSettingsPageModel.builder()
         .licenseKeySettingsModel(licenseKeySettingsModel)
         .build();

      controller.setPageModel(input, principal, null);

      verify(mvSettingsService, never()).setModel(any(), any());
      verify(licenseKeySettingsService).setModel(licenseKeySettingsModel, principal);
   }

   // -------------------------------------------------------------------------
   // Simple delegation endpoints
   // -------------------------------------------------------------------------

   // backup delegates to dataSpaceSettingsService.doBackup
   @Test
   void backup_delegatesToDataSpaceService() {
      BackupDataModel model = mock(BackupDataModel.class);
      when(dataSpaceSettingsService.doBackup(model)).thenReturn("backup-ok");

      assertEquals("backup-ok", controller.backup(model, principal).getStatus());
   }

   // cleanUpCache delegates to cacheSettingsService
   @Test
   void cleanUpCache_delegatesToCacheService() {
      controller.cleanUpCache();

      verify(cacheSettingsService).cleanUpCache();
   }

   // getOAuthParams delegates to emailSettingsService
   @Test
   void getOAuthParams_delegatesToEmailService() throws Exception {
      OAuthParamsRequest request = mock(OAuthParamsRequest.class);
      OAuthParams params = mock(OAuthParams.class);
      when(emailSettingsService.getOAuthParams(request)).thenReturn(params);

      assertSame(params, controller.getOAuthParams(request));
   }

   // -------------------------------------------------------------------------
   // helpers
   // -------------------------------------------------------------------------

   private void stubAllGetters() throws Exception {
      lenient().when(licenseKeySettingsService.getModel()).thenReturn(licenseKeySettingsModel);
      lenient().when(localizationSettingsService.getModel()).thenReturn(localizationSettingsModel);
      lenient().when(mvSettingsService.getModel(principal)).thenReturn(mvSettingsModel);
      lenient().when(cacheSettingsService.getModel()).thenReturn(cacheSettingsModel);
      lenient().when(dataSpaceSettingsService.getModel(principal)).thenReturn(dataSpaceSettingsModel);
      lenient().when(emailSettingsService.getModel()).thenReturn(emailSettingsModel);
      lenient().when(performanceSettingsService.getModel()).thenReturn(performanceSettingsModel);
   }
}
