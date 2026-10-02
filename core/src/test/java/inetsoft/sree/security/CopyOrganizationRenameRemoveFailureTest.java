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
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77502: removeUser/removeRole/removeOrganization now report storage failures. An
 * organization rename (copyOrganization with replace=true) removes the old identities after the
 * new ones were created and the org-scoped state was re-keyed, so a failed remove must not abort
 * the rename half way: it is logged and the rename continues.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  CopyOrganizationRenameRemoveFailureTest.PortalThemesManagerConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class CopyOrganizationRenameRemoveFailureTest {
   @Test
   void rename_oldIdentityRemovesFail_renameContinues() {
      String fromOrgId = "rename_rmfail_from";
      String toOrgId = "rename_rmfail_to";
      FSOrganization fromOrg = new FSOrganization(fromOrgId);
      fromOrg.setName(fromOrgId);
      IdentityID oldRole = new IdentityID("rmRole", fromOrgId);
      IdentityID oldUser = new IdentityID("rmUser", fromOrgId);
      StubProvider provider = new StubProvider();
      FSRole role = new FSRole(oldRole);
      role.setOrganization(fromOrgId);
      provider.roles.put(oldRole, role);
      FSUser user = new FSUser(oldUser);
      user.setOrganization(fromOrgId);
      provider.users.put(oldUser, user);
      IdentityService identityService = mock(IdentityService.class);
      CustomThemesManager themesManager = mock(CustomThemesManager.class);
      CustomThemesManagerMocks.applyUpdates(themesManager);
      when(themesManager.getCustomThemes()).thenReturn(new HashSet<>());

      try(MockedStatic<CustomThemesManager> ctm = mockStatic(CustomThemesManager.class)) {
         ctm.when(CustomThemesManager::getManager).thenReturn(themesManager);
         provider.copyOrganization(fromOrg, null, toOrgId, toOrgId, identityService,
            mock(IdentityThemeService.class), mock(DashboardRegistryManager.class),
            mock(DataCycleManager.class), mock(Principal.class), true);
      }
      catch(Exception e) {
         // Tolerated: the replace=true tail after removeOrganization() needs registries that this
         // minimal context does not provide. What matters is that the rename got past the
         // failed removes, which is verified below.
      }

      assertAll(
         () -> assertEquals(List.of(oldRole), provider.removeRoleCalls,
                            "the old role remove was attempted"),
         () -> assertEquals(List.of(oldUser), provider.removeUserCalls,
                            "the old user remove was attempted"),
         () -> assertEquals(List.of(new IdentityID("rmUser", toOrgId)),
                            provider.addedUsers.stream().map(User::getIdentityID).toList(),
                            "the user was copied although the role remove before it failed"),
         () -> assertNotNull(provider.addedOrganization,
                             "the new organization was saved although the removes failed"),
         () -> assertEquals(List.of(fromOrgId), provider.removeOrganizationCalls,
                            "the old organization remove was attempted"));
      // the step right after removeOrganization() ran, so its failure didn't abort the rename
      verify(identityService).removeOrgProperties(fromOrgId);
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
    * A provider whose removes of the old identities fail like a failed storage remove of
    * FileAuthenticationProvider.
    */
   static class StubProvider extends AbstractEditableAuthenticationProvider {
      final Map<IdentityID, User> users = new LinkedHashMap<>();
      final Map<IdentityID, Role> roles = new LinkedHashMap<>();
      final List<User> addedUsers = new ArrayList<>();
      final List<IdentityID> removeRoleCalls = new ArrayList<>();
      final List<IdentityID> removeUserCalls = new ArrayList<>();
      final List<String> removeOrganizationCalls = new ArrayList<>();
      Organization addedOrganization;

      @Override public IdentityID[] getUsers() { return users.keySet().toArray(new IdentityID[0]); }
      @Override public IdentityID[] getRoles() { return roles.keySet().toArray(new IdentityID[0]); }
      @Override public IdentityID[] getGroups() { return new IdentityID[0]; }
      @Override public User  getUser(IdentityID id)  { return users.get(id); }
      @Override public Group getGroup(IdentityID id) { return null; }
      @Override public Role  getRole(IdentityID id)  { return roles.get(id); }

      @Override public void addUser(User user) { addedUsers.add(user); }
      @Override public void addOrganization(Organization organization) {
         addedOrganization = organization;
      }

      @Override
      public void removeRole(IdentityID roleIdentity) {
         removeRoleCalls.add(roleIdentity);
         throw new RuntimeException("simulated remove failure");
      }

      @Override
      public void removeUser(IdentityID userIdentity) {
         removeUserCalls.add(userIdentity);
         throw new RuntimeException("simulated remove failure");
      }

      @Override
      public void removeOrganization(String id) {
         removeOrganizationCalls.add(id);
         throw new RuntimeException("simulated remove failure");
      }

      @Override public boolean authenticate(IdentityID userIdentity, Object credential) { return false; }
      @Override public Organization getOrganization(String id)  { return null; }
      @Override public String getOrgIdFromName(String name)     { return null; }
      @Override public String getOrgNameFromID(String id)       { return null; }
      @Override public String[] getOrganizationIDs()            { return new String[0]; }
      @Override public String[] getOrganizationNames()          { return new String[0]; }
      @Override public void tearDown() {}
   }
}
