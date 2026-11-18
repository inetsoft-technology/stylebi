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
 * Bug #81587: a plain clone writes the keys of the new id before it registers the organization,
 * so the new id must be recorded as changing (cluster wide) from the first write until the clone
 * ends, whatever the outcome, and the source organization must stay writable.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  CopyOrganizationCloneGuardTest.PortalThemesManagerConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class CopyOrganizationCloneGuardTest {
   // the new id is guarded when the organization is registered, the source is not, and the
   // record is gone when the clone ends
   @Test
   void cloneGuardsOnlyTheNewIdUntilTheEnd() {
      StubProvider provider = new StubProvider(null);
      clone(provider, "cg_ok", false);

      assertTrue(provider.newIdBlocked, "the new id was not guarded while the clone ran");
      assertFalse(provider.sourceIdBlocked, "the source organization must stay writable");
      assertFalse(OrgIdChangeGuard.isBlocked("cg_ok_to"), "the guard was not ended");
   }

   // a failed clone ends the guard too
   @Test
   void failedCloneEndsTheGuard() {
      StubProvider provider = new StubProvider(new IllegalStateException("registration failed"));
      clone(provider, "cg_fail", false);

      assertTrue(provider.newIdBlocked);
      assertFalse(OrgIdChangeGuard.isBlocked("cg_fail_to"), "the guard stayed after a failure");
      assertFalse(OrgIdChangeGuard.isBlocked("cg_fail_from"));
   }

   // control: an id change (replace) is guarded as before, both ids, and ended
   @Test
   void idChangeStillGuardsBothIds() {
      StubProvider provider = new StubProvider(null);
      clone(provider, "cg_replace", true);

      assertTrue(provider.newIdBlocked);
      assertTrue(provider.sourceIdBlocked, "the id change must still guard the old id");
      assertFalse(OrgIdChangeGuard.isBlocked("cg_replace_to"));
   }

   private static void clone(StubProvider provider, String prefix, boolean replace) {
      String fromId = prefix + "_from";
      String toId = prefix + "_to";
      provider.fromId = fromId;
      provider.toId = toId;
      FSOrganization from = new FSOrganization(fromId);
      from.setName(fromId);
      FSOrganization edited = new FSOrganization(toId);
      edited.setName(toId);
      CustomThemesManager themes = mock(CustomThemesManager.class);
      CustomThemesManagerMocks.applyUpdates(themes);
      when(themes.getCustomThemes()).thenReturn(new HashSet<>());

      try(MockedStatic<CustomThemesManager> ctm = mockStatic(CustomThemesManager.class)) {
         ctm.when(CustomThemesManager::getManager).thenReturn(themes);
         provider.copyOrganization(from, edited, toId, toId, mock(IdentityService.class),
            mock(IdentityThemeService.class), mock(DashboardRegistryManager.class),
            mock(DataCycleManager.class), mock(Principal.class), replace);
      }
      catch(Exception e) {
         // Tolerated: the tail after addOrganization() needs registries that this minimal
         // context does not provide
      }

      assertTrue(provider.registered, "addOrganization was not reached");
   }

   @Configuration
   public static class PortalThemesManagerConfig {
      @Bean
      public PortalThemesManager portalThemesManager() {
         PortalThemesManager manager = mock(PortalThemesManager.class);
         when(manager.getCssEntries()).thenReturn(new HashMap<>());
         return manager;
      }
   }

   static class StubProvider extends AbstractEditableAuthenticationProvider {
      StubProvider(RuntimeException failure) {
         this.failure = failure;
      }

      @Override
      public void addOrganization(Organization organization) {
         registered = true;
         newIdBlocked = OrgIdChangeGuard.isBlocked(toId);
         sourceIdBlocked = OrgIdChangeGuard.isBlocked(fromId);

         if(failure != null) {
            throw failure;
         }
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

      String fromId;
      String toId;
      boolean registered;
      boolean newIdBlocked;
      boolean sourceIdBlocked;
      final RuntimeException failure;
   }
}
