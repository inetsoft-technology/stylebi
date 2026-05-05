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
 * Class type: behavioral-orchestration controller — SecurityConfigController has real
 * in-controller logic in setEnableSecurity(), setEnableMultiTenancy(), and getEnableSecurity().
 *
 * Coverage scope (9 cases in 3 groups):
 *
 * --- setEnableSecurity() ---
 *
 *  [enable: success]           enable=true, AdminCredentialUtil succeeds
 *                              → enableSecurity() called; SecurityEngine.touch() called
 *  [enable: no admin password] enable=true, AdminCredentialUtil throws IllegalStateException
 *                              → enableSecurity() NOT called; returned event has warning
 *  [disable]                   enable=false
 *                              → disableSecurity() called; SecurityEngine.touch() called
 *
 * --- getEnableSecurity() ---
 *
 *  [security enabled]          engine reports enabled=true → event.enable()==true
 *  [toggle disabled]           security enabled + non-site-admin → toggleDisabled==true
 *
 * --- setEnableMultiTenancy() ---
 *
 *  [enable: no named-user keys]   SUtil.setMultiTenant(true) called; warning is null/empty
 *  [enable: has named-user keys]  SUtil.setMultiTenant(true) called; warning is non-null/empty
 *  [disable: has added orgs]      SUtil.setMultiTenant() NOT called; warning is non-null/empty
 *  [disable: clean state]         SUtil.setMultiTenant(false) called; no warning
 *
 * Static singletons (AdminCredentialUtil, SecurityEngine.touch, OrganizationManager,
 * SUtil, Organization, SreeEnv, InetsoftConfig, Catalog) are intercepted with
 * Mockito.mockStatic() using lenient() where possible to suppress
 * UnnecessaryStubbingException on methods not consumed by every test.
 */

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.Catalog;
import inetsoft.util.config.InetsoftConfig;
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
class SecurityConfigControllerTest {

   @Mock private SecurityEngine securityEngine;
   @Mock private LicenseManager licenseManager;
   @Mock private DataSourceRegistry dataSourceRegistry;
   @Mock private OrganizationManager orgManager;
   @Mock private SecurityProvider securityProvider;
   @Mock private Catalog catalog;
   @Mock private InetsoftConfig inetsoftConfig;
   @Mock private Principal principal;

   private SecurityConfigController controller;

   private MockedStatic<AdminCredentialUtil> adminCredentialUtilStatic;
   private MockedStatic<SecurityEngine> securityEngineStatic;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<SUtil> sUtilStatic;
   private MockedStatic<Organization> organizationStatic;
   private MockedStatic<SreeEnv> sreeEnvStatic;
   private MockedStatic<InetsoftConfig> inetsoftConfigStatic;
   private MockedStatic<Catalog> catalogStatic;

   @BeforeEach
   void setUp() {
      controller = new SecurityConfigController(securityEngine, licenseManager, dataSourceRegistry);

      adminCredentialUtilStatic = mockStatic(AdminCredentialUtil.class, withSettings().lenient());
      securityEngineStatic = mockStatic(SecurityEngine.class, withSettings().lenient());
      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      sUtilStatic = mockStatic(SUtil.class, withSettings().lenient());
      organizationStatic = mockStatic(Organization.class, withSettings().lenient());
      sreeEnvStatic = mockStatic(SreeEnv.class, withSettings().lenient());
      inetsoftConfigStatic = mockStatic(InetsoftConfig.class, withSettings().lenient());
      catalogStatic = mockStatic(Catalog.class, withSettings().lenient());

      // Common lenient stubs for infrastructure used by getEnableSecurity() / getMultiTenancy()
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      securityEngineStatic.when(SecurityEngine::touch).then(i -> null);
      sreeEnvStatic.when(() -> SreeEnv.getProperty(anyString(), anyString())).thenReturn("false");
      inetsoftConfigStatic.when(InetsoftConfig::getInstance).thenReturn(inetsoftConfig);
      catalogStatic.when(Catalog::getCatalog).thenReturn(catalog);
      lenient().when(catalog.getString(anyString())).thenReturn("warning-message");
      lenient().when(inetsoftConfig.getCloudRunner()).thenReturn(null);
      lenient().when(orgManager.isSiteAdmin(any(Principal.class))).thenReturn(true);
      organizationStatic.when(Organization::getDefaultOrganizationID).thenReturn("host-org");
      organizationStatic.when(Organization::getSelfOrganizationID).thenReturn("self-org");
      lenient().when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);
      lenient().when(securityProvider.getOrganizationIDs()).thenReturn(new String[]{"host-org"});
      lenient().when(securityProvider.getUsers()).thenReturn(new IdentityID[0]);
      lenient().when(securityEngine.getAuthenticationChain()).thenReturn(java.util.Optional.empty());
   }

   @AfterEach
   void tearDown() {
      adminCredentialUtilStatic.close();
      securityEngineStatic.close();
      orgManagerStatic.close();
      sUtilStatic.close();
      organizationStatic.close();
      sreeEnvStatic.close();
      inetsoftConfigStatic.close();
      catalogStatic.close();
   }

   // -------------------------------------------------------------------------
   // setEnableSecurity()
   // -------------------------------------------------------------------------

   // [enable: success] AdminCredentialUtil succeeds → enableSecurity() and touch() called
   @Test
   void setEnableSecurity_enable_callsEnableSecurity() throws Exception {
      adminCredentialUtilStatic.when(AdminCredentialUtil::getRequiredAdminPassword)
         .thenReturn("Admin1234!");

      SecurityEnabledEvent event = SecurityEnabledEvent.builder().enable(true).build();
      controller.setEnableSecurity(event, principal);

      verify(securityEngine).enableSecurity();
      securityEngineStatic.verify(SecurityEngine::touch);
      verify(securityEngine, never()).disableSecurity();
   }

   // [enable: no admin password] AdminCredentialUtil throws → enableSecurity() not called;
   // returned event carries a warning and has enable == false
   @Test
   void setEnableSecurity_enable_adminPasswordMissing_returnsWarningEvent() throws Exception {
      adminCredentialUtilStatic.when(AdminCredentialUtil::getRequiredAdminPassword)
         .thenThrow(new IllegalStateException("INETSOFT_ADMIN_PASSWORD not set"));

      SecurityEnabledEvent event = SecurityEnabledEvent.builder().enable(true).build();
      SecurityEnabledEvent result = controller.setEnableSecurity(event, principal);

      verify(securityEngine, never()).enableSecurity();
      assertFalse(result.enable());
      assertNotNull(result.warning());
      assertFalse(result.warning().isEmpty());
   }

   // [disable] enable=false → disableSecurity() called; enableSecurity() never called
   @Test
   void setEnableSecurity_disable_callsDisableSecurity() throws Exception {
      SecurityEnabledEvent event = SecurityEnabledEvent.builder().enable(false).build();
      controller.setEnableSecurity(event, principal);

      verify(securityEngine).disableSecurity();
      securityEngineStatic.verify(SecurityEngine::touch);
      verify(securityEngine, never()).enableSecurity();
   }

   // -------------------------------------------------------------------------
   // getEnableSecurity()
   // -------------------------------------------------------------------------

   // [security enabled] engine returns true → event.enable() == true
   @Test
   void getEnableSecurity_securityEnabled_returnsEnabledTrue() {
      when(securityEngine.isSecurityEnabled()).thenReturn(true);

      SecurityEnabledEvent result = controller.getEnableSecurity(principal);

      assertTrue(result.enable());
   }

   // [toggle disabled] security on + non-site-admin → toggleDisabled == true
   @Test
   void getEnableSecurity_securityEnabled_nonSiteAdmin_toggleDisabledTrue() {
      when(securityEngine.isSecurityEnabled()).thenReturn(true);
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);

      SecurityEnabledEvent result = controller.getEnableSecurity(principal);

      assertTrue(result.toggleDisabled());
   }

   // -------------------------------------------------------------------------
   // setEnableMultiTenancy()
   // -------------------------------------------------------------------------

   // [enable: no named-user keys] SUtil.setMultiTenant(true) called; no warning in response
   @Test
   void setEnableMultiTenancy_enable_noNamedUserKeys_setsMultiTenantTrue() {
      when(licenseManager.hasNamedUserKeys()).thenReturn(false);
      SecurityEnabledEvent event = SecurityEnabledEvent.builder().enable(true).build();

      SecurityEnabledEvent result = controller.setEnableMultiTenancy(event, principal);

      sUtilStatic.verify(() -> SUtil.setMultiTenant(true));
      assertTrue(result.warning() == null || result.warning().isEmpty());
   }

   // [enable: has named-user keys] SUtil.setMultiTenant(true) still called; warning present
   @Test
   void setEnableMultiTenancy_enable_hasNamedUserKeys_setsMultiTenantTrueAndWarns() {
      when(licenseManager.hasNamedUserKeys()).thenReturn(true);
      SecurityEnabledEvent event = SecurityEnabledEvent.builder().enable(true).build();

      SecurityEnabledEvent result = controller.setEnableMultiTenancy(event, principal);

      sUtilStatic.verify(() -> SUtil.setMultiTenant(true));
      assertNotNull(result.warning());
      assertFalse(result.warning().isEmpty());
   }

   // [disable: clean state] no added orgs, no self-org users → SUtil.setMultiTenant(false) called
   @Test
   void setEnableMultiTenancy_disable_cleanState_setsMultiTenantFalse() {
      // provider returns only the default org → hasAddedOrganizations() == false
      when(securityProvider.getOrganizationIDs()).thenReturn(new String[]{"host-org"});
      // no users in self-org → selfOrganizationHasUsers() == false
      when(securityProvider.getUsers()).thenReturn(new IdentityID[0]);

      SecurityEnabledEvent event = SecurityEnabledEvent.builder().enable(false).build();
      SecurityEnabledEvent result = controller.setEnableMultiTenancy(event, principal);

      sUtilStatic.verify(() -> SUtil.setMultiTenant(false));
      assertTrue(result.warning() == null || result.warning().isEmpty());
   }

   // [disable: has added orgs] SUtil.setMultiTenant() NOT called; warning is present
   @Test
   void setEnableMultiTenancy_disable_hasAddedOrganizations_doesNotDisable() {
      // provider returns the default org plus an extra org → hasAddedOrganizations() == true
      when(securityProvider.getOrganizationIDs())
         .thenReturn(new String[]{"host-org", "extra-org"});

      SecurityEnabledEvent event = SecurityEnabledEvent.builder().enable(false).build();
      SecurityEnabledEvent result = controller.setEnableMultiTenancy(event, principal);

      sUtilStatic.verify(() -> SUtil.setMultiTenant(anyBoolean()), never());
      assertNotNull(result.warning());
      assertFalse(result.warning().isEmpty());
   }
}
