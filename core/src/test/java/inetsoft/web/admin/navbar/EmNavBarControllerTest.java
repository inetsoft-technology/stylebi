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
package inetsoft.web.admin.navbar;

/*
 * Test strategy
 *
 * EmNavBarController builds an EmNavBarModel from several independent concerns.
 * Each field has its own in-controller logic worth verifying:
 *
 * --- logoutUrl ---
 *   The controller reads sso.protocol.type and sso.logout.url from SreeEnv and applies
 *   three rules:
 *     [SSO, no '?'] protocol != NONE and configured URL has no '?' → appends "?fromEm=true"
 *     [SSO, has '?'] protocol != NONE and URL already contains '?' → appends "&fromEm=true"
 *     [no SSO / blank URL] protocol == NONE OR URL blank → uses DEFAULT_LOGOUT_URI
 *
 * --- ssoUser ---
 *   [non-XPrincipal]        plain Principal → false
 *   [XPrincipal internal]   __internal__ == "true" → false (managed/internal account)
 *   [XPrincipal external]   __internal__ != "true" → true (federated/SSO user)
 *
 * --- elasticLicenseExhausted ---
 *   [elastic, 0 hours]      elastic license and remaining hours == 0 → true
 *   [elastic, hours left]   elastic license and remaining hours > 0 → false
 *   [hosted, 0 hours]       hosted license and SRPrincipal with 0 remaining hours → true
 *   [hosted, hours left]    hosted license and SRPrincipal with hours > 0 → false
 *
 * --- aiAssistantVisible ---
 *   [service off]           aiSettingsService.isAiAssistantVisible() == false → false
 *   [service on, permitted] service on AND permission granted → true
 *   [service on, denied]    service on AND permission denied → false
 *
 * --- simple delegation endpoints ---
 *   getCurrOrg      — returns OrganizationManager.getCurrentOrgID()
 *   isMultiTenant   — returns SUtil.isMultiTenant()
 *   isOrgAdminOnly  — isOrgAdmin && !isSiteAdmin
 *   isSiteAdmin     — delegates to OrganizationManager.isSiteAdmin()
 *   getUserInfo     — returns (orgId, isSiteAdmin) pair
 */

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.web.admin.presentation.AISettingsService;
import inetsoft.web.admin.security.SSOType;
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
class EmNavBarControllerTest {

   @Mock private AISettingsService aiSettingsService;
   @Mock private LicenseManager licenseManager;
   @Mock private PortalThemesManager portalThemesManager;
   @Mock private SecurityEngine securityEngine;
   @Mock private OrganizationManager orgManager;

   private EmNavBarController controller;
   private MockedStatic<SreeEnv> sreeEnvStatic;
   private MockedStatic<LicenseManager> licenseManagerStatic;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<SUtil> sUtilStatic;

   @BeforeEach
   void setUp() {
      controller = new EmNavBarController(
         aiSettingsService, licenseManager, portalThemesManager, securityEngine);

      sreeEnvStatic = mockStatic(SreeEnv.class, withSettings().lenient());
      licenseManagerStatic = mockStatic(LicenseManager.class, withSettings().lenient());
      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      sUtilStatic = mockStatic(SUtil.class, withSettings().lenient());

      // safe defaults so unrelated tests don't need to stub everything
      sreeEnvStatic.when(() -> SreeEnv.getProperty("sso.protocol.type"))
         .thenReturn(SSOType.NONE.getName());
      sreeEnvStatic.when(() -> SreeEnv.getProperty("sso.logout.url")).thenReturn("");
      sreeEnvStatic.when(() -> SreeEnv.getProperty("em.home.link", "..")).thenReturn("..");
      licenseManagerStatic.when(LicenseManager::isEnterprise).thenReturn(false);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      lenient().when(orgManager.getCurrentOrgID()).thenReturn("testOrg");
   }

   @AfterEach
   void tearDown() {
      sreeEnvStatic.close();
      licenseManagerStatic.close();
      orgManagerStatic.close();
      sUtilStatic.close();
   }

   // -------------------------------------------------------------------------
   // logoutUrl
   // -------------------------------------------------------------------------

   // [no SSO] protocol == NONE → DEFAULT_LOGOUT_URI used regardless of configured URL
   @Test
   void getNavBarModel_noSso_usesDefaultLogoutUri() throws Exception {
      sreeEnvStatic.when(() -> SreeEnv.getProperty("sso.protocol.type"))
         .thenReturn(SSOType.NONE.getName());
      sreeEnvStatic.when(() -> SreeEnv.getProperty("sso.logout.url"))
         .thenReturn("https://idp.example.com/logout");

      EmNavBarModel model = controller.getNavBarModel(mock(Principal.class));

      assertEquals("../logout?fromEm=true", model.logoutUrl());
   }

   // [SSO, no '?'] configured URL without '?' → "?fromEm=true" appended
   @Test
   void getNavBarModel_ssoWithoutQueryString_appendsQuestionMarkParam() throws Exception {
      sreeEnvStatic.when(() -> SreeEnv.getProperty("sso.protocol.type")).thenReturn("SAML2");
      sreeEnvStatic.when(() -> SreeEnv.getProperty("sso.logout.url"))
         .thenReturn("https://idp.example.com/logout");

      EmNavBarModel model = controller.getNavBarModel(mock(Principal.class));

      assertEquals("https://idp.example.com/logout?fromEm=true", model.logoutUrl());
   }

   // [SSO, has '?'] configured URL already contains '?' → "&fromEm=true" appended
   @Test
   void getNavBarModel_ssoWithExistingQueryString_appendsAmpersandParam() throws Exception {
      sreeEnvStatic.when(() -> SreeEnv.getProperty("sso.protocol.type")).thenReturn("SAML2");
      sreeEnvStatic.when(() -> SreeEnv.getProperty("sso.logout.url"))
         .thenReturn("https://idp.example.com/logout?session=abc");

      EmNavBarModel model = controller.getNavBarModel(mock(Principal.class));

      assertEquals("https://idp.example.com/logout?session=abc&fromEm=true", model.logoutUrl());
   }

   // [blank URL] protocol is not NONE but URL is blank → DEFAULT_LOGOUT_URI
   @Test
   void getNavBarModel_ssoBlankLogoutUrl_usesDefaultLogoutUri() throws Exception {
      sreeEnvStatic.when(() -> SreeEnv.getProperty("sso.protocol.type")).thenReturn("SAML2");
      sreeEnvStatic.when(() -> SreeEnv.getProperty("sso.logout.url")).thenReturn("");

      EmNavBarModel model = controller.getNavBarModel(mock(Principal.class));

      assertEquals("../logout?fromEm=true", model.logoutUrl());
   }

   // -------------------------------------------------------------------------
   // ssoUser
   // -------------------------------------------------------------------------

   // [non-XPrincipal] plain Principal → ssoUser is false
   @Test
   void getNavBarModel_plainPrincipal_ssoUserIsFalse() throws Exception {
      EmNavBarModel model = controller.getNavBarModel(mock(Principal.class));

      assertFalse(model.ssoUser());
   }

   // [XPrincipal internal] __internal__ == "true" → ssoUser is false
   @Test
   void getNavBarModel_internalXPrincipal_ssoUserIsFalse() throws Exception {
      XPrincipal principal = mock(XPrincipal.class);
      when(principal.getProperty("__internal__")).thenReturn("true");

      EmNavBarModel model = controller.getNavBarModel(principal);

      assertFalse(model.ssoUser());
   }

   // [XPrincipal external] __internal__ != "true" → ssoUser is true
   @Test
   void getNavBarModel_externalXPrincipal_ssoUserIsTrue() throws Exception {
      XPrincipal principal = mock(XPrincipal.class);
      when(principal.getProperty("__internal__")).thenReturn(null);

      EmNavBarModel model = controller.getNavBarModel(principal);

      assertTrue(model.ssoUser());
   }

   // -------------------------------------------------------------------------
   // elasticLicenseExhausted
   // -------------------------------------------------------------------------

   // [elastic, 0 hours] → exhausted
   @Test
   void getNavBarModel_elasticLicenseNoHoursRemaining_exhaustedIsTrue() throws Exception {
      when(licenseManager.isElasticLicense()).thenReturn(true);
      when(licenseManager.getElasticRemainingHours()).thenReturn(0);

      EmNavBarModel model = controller.getNavBarModel(mock(Principal.class));

      assertTrue(model.elasticLicenseExhausted());
   }

   // [elastic, hours left] → not exhausted
   @Test
   void getNavBarModel_elasticLicenseHoursRemaining_exhaustedIsFalse() throws Exception {
      when(licenseManager.isElasticLicense()).thenReturn(true);
      when(licenseManager.getElasticRemainingHours()).thenReturn(5);

      EmNavBarModel model = controller.getNavBarModel(mock(Principal.class));

      assertFalse(model.elasticLicenseExhausted());
   }

   // [hosted, 0 hours] SRPrincipal with zero remaining hours → exhausted
   @Test
   void getNavBarModel_hostedLicenseNoHoursRemaining_exhaustedIsTrue() throws Exception {
      when(licenseManager.isElasticLicense()).thenReturn(false);
      when(licenseManager.isHostedLicense()).thenReturn(true);
      SRPrincipal principal = mock(SRPrincipal.class);
      when(principal.getOrgId()).thenReturn("org1");
      when(principal.getName()).thenReturn("user1");
      when(licenseManager.getHostedRemainingHours("org1", "user1")).thenReturn(0);

      EmNavBarModel model = controller.getNavBarModel(principal);

      assertTrue(model.elasticLicenseExhausted());
   }

   // [hosted, hours left] SRPrincipal with remaining hours → not exhausted
   @Test
   void getNavBarModel_hostedLicenseHoursRemaining_exhaustedIsFalse() throws Exception {
      when(licenseManager.isElasticLicense()).thenReturn(false);
      when(licenseManager.isHostedLicense()).thenReturn(true);
      SRPrincipal principal = mock(SRPrincipal.class);
      when(principal.getOrgId()).thenReturn("org1");
      when(principal.getName()).thenReturn("user1");
      when(licenseManager.getHostedRemainingHours("org1", "user1")).thenReturn(3);

      EmNavBarModel model = controller.getNavBarModel(principal);

      assertFalse(model.elasticLicenseExhausted());
   }

   // -------------------------------------------------------------------------
   // aiAssistantVisible
   // -------------------------------------------------------------------------

   // [service off] isAiAssistantVisible() == false → field is false; permission not checked
   @Test
   void getNavBarModel_aiServiceOff_aiAssistantNotVisible() throws Exception {
      when(aiSettingsService.isAiAssistantVisible()).thenReturn(false);

      EmNavBarModel model = controller.getNavBarModel(mock(Principal.class));

      assertFalse(model.aiAssistantVisible());
      verify(securityEngine, never()).checkPermission(any(), any(), anyString(), any());
   }

   // [service on, permitted] service on AND permission granted → true
   @Test
   void getNavBarModel_aiServiceOnAndPermitted_aiAssistantVisible() throws Exception {
      when(aiSettingsService.isAiAssistantVisible()).thenReturn(true);
      when(securityEngine.checkPermission(
            any(), eq(ResourceType.AI_ASSISTANT), eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);

      EmNavBarModel model = controller.getNavBarModel(mock(Principal.class));

      assertTrue(model.aiAssistantVisible());
   }

   // [service on, denied] service on AND permission denied → false
   @Test
   void getNavBarModel_aiServiceOnButDenied_aiAssistantNotVisible() throws Exception {
      when(aiSettingsService.isAiAssistantVisible()).thenReturn(true);
      when(securityEngine.checkPermission(
            any(), eq(ResourceType.AI_ASSISTANT), eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(false);

      EmNavBarModel model = controller.getNavBarModel(mock(Principal.class));

      assertFalse(model.aiAssistantVisible());
   }

   // -------------------------------------------------------------------------
   // Simple delegation endpoints
   // -------------------------------------------------------------------------

   // getCurrOrg returns the current org ID from OrganizationManager
   @Test
   void getCurrOrg_returnsCurrentOrgId() {
      when(orgManager.getCurrentOrgID()).thenReturn("acmeOrg");

      String result = controller.getCurrOrg(mock(Principal.class));

      assertEquals("acmeOrg", result);
   }

   // isMultiTenant delegates to SUtil.isMultiTenant()
   @Test
   void isMultiTenant_delegatesToSUtil() {
      sUtilStatic.when(SUtil::isMultiTenant).thenReturn(true);

      assertTrue(controller.isMultiTenant(mock(Principal.class)));
   }

   // isOrgAdminOnly: org admin AND not site admin → true
   @Test
   void isOrgAdminOnly_orgAdminNotSiteAdmin_returnsTrue() {
      Principal principal = mock(Principal.class);
      when(orgManager.isOrgAdmin(principal)).thenReturn(true);
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);

      assertTrue(controller.isOrgAdminOnly(principal));
   }

   // isOrgAdminOnly: site admin → false even if also org admin
   @Test
   void isOrgAdminOnly_siteAdmin_returnsFalse() {
      Principal principal = mock(Principal.class);
      when(orgManager.isOrgAdmin(principal)).thenReturn(true);
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);

      assertFalse(controller.isOrgAdminOnly(principal));
   }

   // isSiteAdmin delegates to OrganizationManager.isSiteAdmin
   @Test
   void isSiteAdmin_delegatesToOrgManager() {
      Principal principal = mock(Principal.class);
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);

      assertTrue(controller.isSiteAdmin(principal));
   }

   // getUserInfo returns orgId and isSiteAdmin from OrganizationManager
   @Test
   void getUserInfo_returnsOrgIdAndSiteAdminFlag() {
      Principal principal = mock(Principal.class);
      when(orgManager.getCurrentOrgID()).thenReturn("myOrg");
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);

      var result = controller.getUserInfo(principal);

      assertEquals("myOrg", result.getKey());
      assertEquals(false, result.getValue());
   }
}
