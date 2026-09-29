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

import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.portal.CustomThemesManagerMocks;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.web.admin.security.IdentityService;
import inetsoft.web.admin.security.user.IdentityThemeService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.HashMap;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77147: an organization id change (copyOrganization with an edited organization and
 * replace=true) must store the new organization with the status of the edited organization.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  CopyOrganizationStatusTest.PortalThemesManagerConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class CopyOrganizationStatusTest {
   @Test
   void idChange_inactiveOrg_staysInactive() {
      assertFalse(copyWithEditedStatus("status_copy_inactive", false).isActive(),
                  "stored org under the new id must be inactive");
   }

   @Test
   void idChange_activeOrg_staysActive() {
      assertTrue(copyWithEditedStatus("status_copy_active", true).isActive(),
                 "stored org under the new id must be active");
   }

   private static Organization copyWithEditedStatus(String prefix, boolean active) {
      String fromOrgId = prefix + "_from";
      String toOrgId = prefix + "_to";
      FSOrganization fromOrg = new FSOrganization(fromOrgId);
      fromOrg.setName(fromOrgId);
      fromOrg.setActive(active);
      FSOrganization edited = new FSOrganization(toOrgId);
      edited.setName(toOrgId);
      edited.setActive(active);
      StubProvider provider = new StubProvider();
      CustomThemesManager themesManager = mock(CustomThemesManager.class);
      CustomThemesManagerMocks.applyUpdates(themesManager);
      when(themesManager.getCustomThemes()).thenReturn(new HashSet<>());

      try(MockedStatic<CustomThemesManager> ctm = mockStatic(CustomThemesManager.class)) {
         ctm.when(CustomThemesManager::getManager).thenReturn(themesManager);
         provider.copyOrganization(fromOrg, edited, toOrgId, toOrgId,
            mock(IdentityService.class), mock(IdentityThemeService.class),
            mock(DashboardRegistryManager.class), mock(DataCycleManager.class),
            mock(Principal.class), true);
      }
      catch(Exception e) {
         // Tolerated: the replace=true tail after addOrganization() needs registries that this
         // minimal context does not provide, the new org has already been captured by then.
      }

      assertNotNull(provider.capturedOrganization,
                    "addOrganization(...) must have been called with the new org");
      return provider.capturedOrganization;
   }

   @Configuration
   public static class PortalThemesManagerConfig {
      @Bean
      public PortalThemesManager portalThemesManager() {
         PortalThemesManager mockPortalThemesManager = mock(PortalThemesManager.class);
         when(mockPortalThemesManager.getCssEntries()).thenReturn(new HashMap<>());
         return mockPortalThemesManager;
      }
   }

   /**
    * Mirrors OrgLifecycleThemeOrchestrationTest.StubProvider, captures the organization that
    * copyOrganizationInternal() builds and stores.
    */
   static class StubProvider extends AbstractEditableAuthenticationProvider {
      Organization capturedOrganization;

      @Override
      public void addOrganization(Organization organization) {
         this.capturedOrganization = organization;
      }

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
