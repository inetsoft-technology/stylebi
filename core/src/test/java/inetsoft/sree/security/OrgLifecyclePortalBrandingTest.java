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
package inetsoft.sree.security;

/*
 * Scenario 12a (org-lifecycle-resource-matrix.md, 三、3.8.1) -- control group proving cssEntries IS
 * kept in sync on copy/rename, unlike logoEntries/faviconEntries (PR #4469, still open) and separately
 * from welcomePageEntries (fixed by PR #4454). Only asserts the addCSSEntry/removeCSSEntry map calls;
 * physical file relocation is copyDataSpace()'s job, already covered by
 * OrgLifecycleDataSpaceIntegrationTest. 12d (delete) is covered by IdentityServiceAutoSaveOrgLifecycle
 * Test, not duplicated here. Harness mirrors OrgLifecycleThemeOrchestrationTest, since the cssEntries
 * block is inlined in copyOrganizationInternal() rather than factored into its own testable method.
 */

import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.util.DataSpace;
import inetsoft.web.admin.security.IdentityService;
import inetsoft.web.admin.security.user.IdentityThemeService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.Collections;
import java.util.Map;

import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  OrgLifecyclePortalBrandingTest.PortalThemesManagerConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class OrgLifecyclePortalBrandingTest {

   @Autowired
   private PortalThemesManager portalThemesManager;

   // copy branch (replace=false)
   @Test
   void copy_cssEntrySynced_newOrgGetsMapEntry_sourceEntryNotRemoved() {
      String fromOrgId = "css_orch_copy_from";
      String toOrgId = "css_orch_copy_to";
      String cssName = "brand.css";

      when(portalThemesManager.getCssEntries())
         .thenReturn(Map.of(fromOrgId, fromOrgId + "/" + cssName));

      StubProvider provider = new StubProvider();

      CustomThemesManager noopThemesManager = noopThemesManager();

      try(MockedStatic<CustomThemesManager> ctm = mockStatic(CustomThemesManager.class)) {
         ctm.when(CustomThemesManager::getManager).thenReturn(noopThemesManager);

         provider.copyOrganization(fromOrganization(fromOrgId), toOrgId,
            mock(IdentityService.class), mock(IdentityThemeService.class),
            mock(DashboardRegistryManager.class), mock(DataCycleManager.class),
            mock(Principal.class), false, null);
      }

      verify(portalThemesManager).addCSSEntry(toOrgId, toOrgId + "/" + cssName);
      verify(portalThemesManager, never()).removeCSSEntry(fromOrgId);
   }

   // rename branch (replace=true)
   @Test
   void rename_cssEntrySynced_newOrgGetsMapEntry_sourceEntryRemoved() {
      String fromOrgId = "css_orch_rename_from";
      String toOrgId = "css_orch_rename_to";
      String cssName = "brand.css";

      when(portalThemesManager.getCssEntries())
         .thenReturn(Map.of(fromOrgId, fromOrgId + "/" + cssName));

      StubProvider provider = new StubProvider();

      CustomThemesManager noopThemesManager = noopThemesManager();

      try(MockedStatic<CustomThemesManager> ctm = mockStatic(CustomThemesManager.class)) {
         ctm.when(CustomThemesManager::getManager).thenReturn(noopThemesManager);

         provider.copyOrganization(fromOrganization(fromOrgId), toOrgId,
            mock(IdentityService.class), mock(IdentityThemeService.class),
            mock(DashboardRegistryManager.class), mock(DataCycleManager.class),
            mock(Principal.class), true, null);
      }

      verify(portalThemesManager).addCSSEntry(toOrgId, toOrgId + "/" + cssName);
      verify(portalThemesManager).removeCSSEntry(fromOrgId);
   }

   private static Organization fromOrganization(String fromOrgId) {
      FSOrganization fromOrganization = new FSOrganization(fromOrgId);
      fromOrganization.setName(fromOrgId);
      return fromOrganization;
   }

   // copyThemes() runs first and needs a non-null empty theme set to no-op safely; unrelated to
   // this scenario, see OrgLifecycleThemeOrchestrationTest for why it's mocked rather than a real bean.
   private static CustomThemesManager noopThemesManager() {
      CustomThemesManager mockManager = mock(CustomThemesManager.class);
      when(mockManager.getCustomThemes()).thenReturn(Collections.emptySet());
      return mockManager;
   }

   // unstubbed PortalThemesManager NPEs on getCssEntries(); each test re-stubs it before use.
   @Configuration
   public static class PortalThemesManagerConfig {
      @Bean
      public PortalThemesManager portalThemesManager() {
         PortalThemesManager mockPortalThemesManager = mock(PortalThemesManager.class);
         when(mockPortalThemesManager.getCssEntries()).thenReturn(Map.of());
         return mockPortalThemesManager;
      }

      // rename's cleanup path calls RepletRegistryManager.getInstance().clearOrgCache(); not
      // provided by BaseTestConfiguration, so registered here as a real instance.
      @Bean
      public RepletRegistryManager repletRegistryManager(DataSpace dataSpace) {
         return new RepletRegistryManager(dataSpace);
      }
   }

   // mirrors OrgLifecycleThemeOrchestrationTest.StubProvider; unused abstract members are no-ops.
   static class StubProvider extends AbstractEditableAuthenticationProvider {
      @Override public User  getUser(IdentityID id)  { return null; }
      @Override public Group getGroup(IdentityID id) { return null; }
      @Override public Role  getRole(IdentityID id)  { return null; }

      @Override public boolean authenticate(IdentityID userIdentity, Object credential) { return false; }
      @Override public Organization getOrganization(String id)  { return null; }
      @Override public String getOrgIdFromName(String name)     { return null; }
      @Override public String getOrgNameFromID(String id)       { return null; }
      @Override public String[] getOrganizationIDs()            { return new String[0]; }
      @Override public String[] getOrganizationNames()          { return new String[0]; }
      @Override public void tearDown() {}
   }
}
