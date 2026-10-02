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
package inetsoft.web.admin.security;

/*
 * Bug #77235: an organization admin must not be able to assign another organization's private
 * theme to their own organization. setIdentity is shared by the EM and the REST API, so it must
 * reject the theme before any change. Global themes, the organization's own themes, the default
 * theme and the theme already stored are still allowed, and a site admin is not restricted.
 */

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.portal.CustomTheme;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.security.*;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.security.user.EditOrganizationPaneModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class IdentityServiceOrganizationThemeTest {
   @BeforeEach
   void setUp() {
      sUtilStatic = mockStatic(SUtil.class, withSettings().strictness(Strictness.LENIENT));
      sUtilStatic.when(() -> SUtil.getActionRecord(any(Principal.class), anyString(), any(), anyString()))
         .thenReturn(mock(ActionRecord.class));
      sUtilStatic.when(SUtil::isMultiTenant).thenReturn(true);
      auditStatic = mockStatic(Audit.class, withSettings().strictness(Strictness.LENIENT));
      auditStatic.when(Audit::getInstance).thenReturn(mock(Audit.class));
      securityEngineStatic = mockStatic(SecurityEngine.class, withSettings().strictness(Strictness.LENIENT));

      orgManager = mock(OrganizationManager.class, withSettings().lenient());
      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().strictness(Strictness.LENIENT));
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);

      securityEngine = mock(SecurityEngine.class, withSettings().lenient());
      when(securityEngine.isSecurityEnabled()).thenReturn(true);

      themes = new HashSet<>();
      themes.add(theme("btheme", "mktg1"));
      themes.add(theme("gtheme", null));
      themes.add(theme("etheme", ""));
      themes.add(theme("stheme", "sales1"));
      themesManager = mock(CustomThemesManager.class, withSettings().lenient());
      when(themesManager.getCustomThemes()).thenReturn(themes);
      cluster = mock(Cluster.class);

      service = mock(IdentityService.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
      ReflectionTestUtils.setField(service, "securityEngine", securityEngine);
      ReflectionTestUtils.setField(service, "customThemesManager", themesManager);
      ReflectionTestUtils.setField(service, "cluster", cluster);
      ReflectionTestUtils.setField(service, "LOG", LoggerFactory.getLogger(IdentityService.class));

      eprovider = mock(EditableAuthenticationProvider.class, withSettings().lenient());
      principal = mock(Principal.class);
      sales1 = new FSOrganization(new IdentityID("Sales", "sales1"));
   }

   @AfterEach
   void tearDown() {
      sUtilStatic.close();
      auditStatic.close();
      securityEngineStatic.close();
      orgManagerStatic.close();
   }

   @ParameterizedTest
   @ValueSource(strings = { "btheme", "missing" })
   void setIdentity_foreignPrivateOrMissingTheme_rejectedBeforeAnyChange(String themeId) {
      assertThrows(java.lang.SecurityException.class,
                   () -> service.setIdentity(sales1, model("sales1", themeId), eprovider, principal));

      verify(eprovider, never()).getUsers();
      verify(eprovider, never()).setOrganization(any(), any());
      verify(themesManager, never()).updateCustomThemes(any());
      verify(themesManager, never()).setOrgSelectedTheme(any(), any());
      verifyNoInteractions(cluster);
   }

   @Test
   void setIdentity_ownTheme_passesTheGate() throws Exception {
      try {
         service.setIdentity(sales1, model("sales1", "stheme"), eprovider, principal);
      }
      catch(java.lang.SecurityException e) {
         fail("the org's own theme must be allowed: " + e.getMessage());
      }
      catch(Exception ignore) {
         // later steps of the save are out of scope, only the gate matters here
      }

      verify(eprovider, atLeastOnce()).getUsers();
   }

   @ParameterizedTest
   @ValueSource(strings = { "gtheme", "etheme", "stheme" })
   void globalOrOwnTheme_allowed(String themeId) {
      assertDoesNotThrow(() -> service.checkOrganizationTheme(sales1, model("sales1", themeId), principal));
   }

   @Test
   void ownTheme_orgIdCaseDiffers_allowed() {
      // a new private theme is stamped with the lower-cased current org id
      FSOrganization org = new FSOrganization(new IdentityID("Sales", "Sales1"));

      assertDoesNotThrow(() -> service.checkOrganizationTheme(org, model("Sales1", "stheme"), principal));
   }

   @Test
   void sharedThemeId_ownCloneExists_allowed() {
      // deleting a global theme clones it per affected org with the same id
      themes.add(theme("clone", "mktg1"));
      themes.add(theme("clone", "sales1"));

      assertDoesNotThrow(() -> service.checkOrganizationTheme(sales1, model("sales1", "clone"), principal));
   }

   @ParameterizedTest
   @NullAndEmptySource
   @ValueSource(strings = { CustomTheme.DEFAULT_THEME_ID })
   void defaultTheme_allowed(String themeId) {
      assertDoesNotThrow(() -> service.checkOrganizationTheme(sales1, model("sales1", themeId), principal));
   }

   @Test
   void unchangedStoredTheme_allowed() {
      // e.g. a site admin assigned a theme of another organization earlier
      sales1.setTheme("btheme");

      assertDoesNotThrow(() -> service.checkOrganizationTheme(sales1, model("sales1", "btheme"), principal));
   }

   @Test
   void rename_ownThemeStillHasOldOrgId_allowed() {
      // the org's themes are only moved to the new id after the theme is assigned
      assertDoesNotThrow(() -> service.checkOrganizationTheme(sales1, model("sales2", "stheme"), principal));
      assertThrows(java.lang.SecurityException.class,
                   () -> service.checkOrganizationTheme(sales1, model("mktg1", "btheme"), principal));
   }

   @Test
   void siteAdmin_foreignTheme_allowed() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);

      assertDoesNotThrow(() -> service.checkOrganizationTheme(sales1, model("sales1", "btheme"), principal));
   }

   @Test
   void singleTenantOrSecurityOff_notChecked() {
      sUtilStatic.when(SUtil::isMultiTenant).thenReturn(false);
      assertDoesNotThrow(() -> service.checkOrganizationTheme(sales1, model("sales1", "btheme"), principal));

      sUtilStatic.when(SUtil::isMultiTenant).thenReturn(true);
      when(securityEngine.isSecurityEnabled()).thenReturn(false);
      assertDoesNotThrow(() -> service.checkOrganizationTheme(sales1, model("sales1", "btheme"), principal));
   }

   private static CustomTheme theme(String id, String orgID) {
      CustomTheme theme = new CustomTheme();
      theme.setId(id);
      theme.setName(id);
      theme.setOrgID(orgID);
      return theme;
   }

   private static EditOrganizationPaneModel model(String id, String themeId) {
      return EditOrganizationPaneModel.builder()
         .name("Sales")
         .oldName("Sales")
         .id(id)
         .theme(themeId)
         .build();
   }

   private IdentityService service;
   private SecurityEngine securityEngine;
   private CustomThemesManager themesManager;
   private Set<CustomTheme> themes;
   private OrganizationManager orgManager;
   private EditableAuthenticationProvider eprovider;
   private Cluster cluster;
   private Principal principal;
   private FSOrganization sales1;
   private MockedStatic<SUtil> sUtilStatic;
   private MockedStatic<Audit> auditStatic;
   private MockedStatic<SecurityEngine> securityEngineStatic;
   private MockedStatic<OrganizationManager> orgManagerStatic;
}
