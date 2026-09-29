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
package inetsoft.web.admin;

/*
 * Test strategy
 *
 * Class type: controller — the EM index page's customTheme/darkTheme flags that come from
 * Organization.theme (Bug #77285). The selected-theme chain (isCustomThemeApplied/isEMDarkTheme)
 * is mocked to "no custom theme", so only the Organization.theme contribution is observed.
 *
 *  ├─ [A] Organization.theme names another org's private dark theme, MT on → no flags
 *  ├─ [B] Organization.theme names the org's own private dark theme        → both flags
 *  ├─ [C] Organization.theme names a global dark theme                     → both flags
 *  └─ [D] another org's private dark theme, MT off (themes keep orgID)     → both flags
 */

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.portal.CustomTheme;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.security.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.ModelAndView;

import java.security.Principal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class AdminPageControllerThemeTest {
   @Mock private SecurityEngine securityEngine;
   @Mock private SecurityProvider securityProvider;
   @Mock private CustomThemesManager customThemesManager;
   @Mock private PortalThemesManager portalThemesManager;
   @Mock private OrganizationManager organizationManager;
   @Mock private Organization organization;

   private MockedStatic<SUtil> sUtilStatic;
   private MockedStatic<OrganizationManager> organizationManagerStatic;
   private AdminPageController controller;

   private static final String ORG = "org2";
   private static final String THEME = "t1";

   @BeforeEach
   void setUp() throws Exception {
      sUtilStatic = mockStatic(SUtil.class);
      sUtilStatic.when(SUtil::isMultiTenant).thenReturn(true);
      organizationManagerStatic = mockStatic(OrganizationManager.class);
      organizationManagerStatic.when(OrganizationManager::getInstance).thenReturn(organizationManager);
      when(organizationManager.getCurrentOrgID()).thenReturn(ORG);

      when(securityEngine.checkPermission(any(Principal.class), eq(ResourceType.EM), eq("*"),
                                          eq(ResourceAction.ACCESS))).thenReturn(true);
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);
      when(securityProvider.getOrganization(ORG)).thenReturn(organization);
      when(organization.getTheme()).thenReturn(THEME);

      // the selected-theme chain reports no custom theme
      when(customThemesManager.isCustomThemeApplied()).thenReturn(false);
      when(customThemesManager.isEMDarkTheme()).thenReturn(false);

      controller = new AdminPageController(securityEngine, customThemesManager, portalThemesManager);
   }

   @AfterEach
   void tearDown() {
      sUtilStatic.close();
      organizationManagerStatic.close();
   }

   // [Path A] a pointer left on the organization to another organization's private theme: the CSS
   // is not served (GlobalStyleController), so the index page must not claim a custom/dark theme
   @Test
   void showAdminPage_orgThemeOfOtherOrg_noThemeFlags() throws Exception {
      when(customThemesManager.getCustomThemes()).thenReturn(Set.of(darkTheme("org1")));

      ModelAndView model = showAdminPage();

      assertEquals(false, model.getModel().get("customTheme"));
      assertEquals(false, model.getModel().get("darkTheme"));
   }

   // [Path B] the organization's own private theme still sets both flags
   @Test
   void showAdminPage_orgThemeOfOwnOrg_themeFlags() throws Exception {
      when(customThemesManager.getCustomThemes()).thenReturn(Set.of(darkTheme(ORG)));

      ModelAndView model = showAdminPage();

      assertEquals(true, model.getModel().get("customTheme"));
      assertEquals(true, model.getModel().get("darkTheme"));
   }

   // [Path C] a global theme is visible to every organization
   @Test
   void showAdminPage_globalOrgTheme_themeFlags() throws Exception {
      when(customThemesManager.getCustomThemes()).thenReturn(Set.of(darkTheme(null)));

      ModelAndView model = showAdminPage();

      assertEquals(true, model.getModel().get("customTheme"));
      assertEquals(true, model.getModel().get("darkTheme"));
   }

   // [Path D] single tenant: every theme is visible, even one that kept another orgID
   @Test
   void showAdminPage_singleTenant_otherOrgIdTheme_themeFlags() throws Exception {
      sUtilStatic.when(SUtil::isMultiTenant).thenReturn(false);
      when(customThemesManager.getCustomThemes()).thenReturn(Set.of(darkTheme("org1")));

      ModelAndView model = showAdminPage();

      assertEquals(true, model.getModel().get("customTheme"));
      assertEquals(true, model.getModel().get("darkTheme"));
   }

   private ModelAndView showAdminPage() throws Exception {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/em/settings");
      return controller.showAdminPage(request, new MockHttpServletResponse(),
                                      mock(Principal.class), "http://localhost/");
   }

   private static CustomTheme darkTheme(String orgID) {
      CustomTheme theme = new CustomTheme();
      theme.setId(THEME);
      theme.setName(THEME);
      theme.setOrgID(orgID);
      theme.setEMDark(true);
      return theme;
   }
}
