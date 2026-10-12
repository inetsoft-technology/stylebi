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

import inetsoft.sree.RepletRegistry;
import inetsoft.sree.RepletRegistryManager;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * Bug #78118: while the ID of the session's current organization is changed, the session's
 * current organization must always name an organization that exists. The rename removes the old
 * organization before the caller sets the new ID, so the session is moved to the new ID before the
 * old organization is removed, and the copy of the replet registry must not use the session's
 * current organization as a temporary scope.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  CopyOrganizationRenameCurrentOrgTest.PortalThemesManagerConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class CopyOrganizationRenameCurrentOrgTest {
   @Test
   void rename_currentOrg_sessionMovedAfterCopyStoragesAndBeforeRemove() {
      String fromOrgId = "rename_curr_from";
      String toOrgId = "rename_curr_to";
      List<String> events = new ArrayList<>();
      RecordingOrganizationManager orgManager = new RecordingOrganizationManager(fromOrgId, events);

      renameOrganization(fromOrgId, toOrgId, orgManager, events);

      assertEquals(List.of("copyStorages", "copyRepletRegistry", "setCurrentOrgID:" + toOrgId,
                           "removeOrganization:" + fromOrgId + " current=" + toOrgId),
                   events,
                   "the session must be moved to the new ID after the new organization's storage " +
                   "is copied and before the old organization is removed");
   }

   @Test
   void rename_otherOrg_sessionNotMoved() {
      String fromOrgId = "rename_other_from";
      String toOrgId = "rename_other_to";
      List<String> events = new ArrayList<>();
      RecordingOrganizationManager orgManager = new RecordingOrganizationManager("host-org", events);

      renameOrganization(fromOrgId, toOrgId, orgManager, events);

      assertTrue(events.contains("removeOrganization:" + fromOrgId + " current=host-org"),
                 "the old organization was removed while the session stayed on its own org: " + events);
      assertTrue(events.stream().noneMatch(e -> e.startsWith("setCurrentOrgID")),
                 "the rename of another organization must not move the session: " + events);
   }

   @Test
   void copyRepletRegistry_scopesThreadOnly_sessionUntouched() throws Exception {
      String fromOrgId = "replet_scope_from";
      String toOrgId = "replet_scope_to";
      List<String> events = new ArrayList<>();
      RecordingOrganizationManager orgManager = new RecordingOrganizationManager(fromOrgId, events);
      List<String> scopes = new ArrayList<>();
      RepletRegistry registry = mock(RepletRegistry.class);
      when(registry.getAllFolders()).thenReturn(new String[0]);
      RepletRegistryManager registryManager = mock(RepletRegistryManager.class);
      when(registryManager.getRegistry(any(String.class))).thenAnswer(inv -> {
         scopes.add(OrganizationContextHolder.getCurrentOrgId());
         return registry;
      });
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      IdentityService identityService = new IdentityService(
         securityEngine,
         null, null, null, null, null, null, null, null, null, null, null, null,
         null,
         Optional.empty(),
         null, null, null, null, null, null, null, null,
         null,
         null, null, null,
         registryManager,
         Optional.empty());
      String originalScope = OrganizationContextHolder.getCurrentOrgId();

      try(MockedStatic<OrganizationManager> om = mockStatic(OrganizationManager.class, CALLS_REAL_METHODS)) {
         om.when(OrganizationManager::getInstance).thenReturn(orgManager);
         identityService.copyRepletRegistry(fromOrgId, toOrgId);
      }

      assertEquals(List.of(toOrgId, toOrgId), scopes,
                   "the registries are copied in the new organization's thread scope");
      assertTrue(events.isEmpty(), "the session's current organization must not be changed: " + events);
      assertEquals(fromOrgId, orgManager.current);
      assertEquals(originalScope, OrganizationContextHolder.getCurrentOrgId(),
                   "the thread scope is restored");
      verify(registry).save();
   }

   private static void renameOrganization(String fromOrgId, String toOrgId,
                                          RecordingOrganizationManager orgManager,
                                          List<String> events)
   {
      FSOrganization fromOrg = new FSOrganization(fromOrgId);
      fromOrg.setName(fromOrgId);
      StubProvider provider = new StubProvider(orgManager, events);
      IdentityService identityService = mock(IdentityService.class);
      doAnswer(inv -> events.add("copyStorages")).when(identityService)
         .copyStorages(any(), any(), anyBoolean());
      doAnswer(inv -> events.add("copyRepletRegistry")).when(identityService)
         .copyRepletRegistry(any(), any());
      CustomThemesManager themesManager = mock(CustomThemesManager.class);
      CustomThemesManagerMocks.applyUpdates(themesManager);
      when(themesManager.getCustomThemes()).thenReturn(new HashSet<>());

      try(MockedStatic<CustomThemesManager> ctm = mockStatic(CustomThemesManager.class);
          MockedStatic<OrganizationManager> om = mockStatic(OrganizationManager.class, CALLS_REAL_METHODS))
      {
         ctm.when(CustomThemesManager::getManager).thenReturn(themesManager);
         om.when(OrganizationManager::getInstance).thenReturn(orgManager);
         provider.copyOrganization(fromOrg, null, toOrgId, toOrgId, identityService,
            mock(IdentityThemeService.class), mock(DashboardRegistryManager.class),
            mock(DataCycleManager.class), mock(Principal.class), true);
      }
      catch(Exception e) {
         // Tolerated: the replace=true tail after removeOrganization() needs registries that this
         // minimal context does not provide (see CopyOrganizationRenameRemoveFailureTest). The
         // ordering up to removeOrganization() is what is verified.
      }
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
    * Stands in for the session's current organization, which the enterprise OrganizationManager
    * keeps in the session principal.
    */
   static class RecordingOrganizationManager extends OrganizationManager {
      RecordingOrganizationManager(String current, List<String> events) {
         this.current = current;
         this.events = events;
      }

      @Override
      public void setCurrentOrgID(String newOrgID) {
         events.add("setCurrentOrgID:" + newOrgID);
         current = newOrgID;
      }

      @Override
      public String getCurrentOrgID(Principal principal) {
         return current;
      }

      String current;
      private final List<String> events;
   }

   static class StubProvider extends AbstractEditableAuthenticationProvider {
      StubProvider(RecordingOrganizationManager orgManager, List<String> events) {
         this.orgManager = orgManager;
         this.events = events;
      }

      @Override
      public void removeOrganization(String id) {
         events.add("removeOrganization:" + id + " current=" + orgManager.current);
      }

      @Override public IdentityID[] getUsers() { return new IdentityID[0]; }
      @Override public IdentityID[] getRoles() { return new IdentityID[0]; }
      @Override public IdentityID[] getGroups() { return new IdentityID[0]; }
      @Override public User  getUser(IdentityID id)  { return null; }
      @Override public Group getGroup(IdentityID id) { return null; }
      @Override public Role  getRole(IdentityID id)  { return null; }
      @Override public void addOrganization(Organization organization) {}
      @Override public boolean authenticate(IdentityID userIdentity, Object credential) { return false; }
      @Override public Organization getOrganization(String id)  { return null; }
      @Override public String getOrgIdFromName(String name)     { return null; }
      @Override public String getOrgNameFromID(String id)       { return null; }
      @Override public String[] getOrganizationIDs()            { return new String[0]; }
      @Override public String[] getOrganizationNames()          { return new String[0]; }
      @Override public void tearDown() {}

      private final RecordingOrganizationManager orgManager;
      private final List<String> events;
   }
}
