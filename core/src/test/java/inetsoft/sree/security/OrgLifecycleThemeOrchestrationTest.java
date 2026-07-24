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
 * Scenario 3c (matrix row): community/core/src/test/resources/docs/org-lifecycle-resource-matrix.md,
 * section "三、其他机制" / "3.1 主题（Theme）" -- orchestration-level integration coverage for
 * AbstractEditableAuthenticationProvider.copyOrganizationInternal()'s theme wiring, not duplicated
 * here. AbstractEditableAuthenticationProviderStaticDepTest already covers copyThemes() in
 * isolation (reflection + mockStatic); this file drives the full copyOrganization(...) entry point
 * so the "does newOrg.getTheme() actually end up consistent with what copyThemes() did to the
 * CustomTheme set/selection pointer" question -- which only exists at the orchestration layer, one
 * level above copyThemes() itself -- has real coverage.
 *
 * Both scenarios use replace=false (copy, not rename) and editedNewOrganization=null, so the
 * large `if(replace)` source-org cleanup subtree (FSService/XJobPool/RepletRegistryManager
 * .getInstance()) and the `if(editedNewOrganization != null && replace)` branch are never reached
 * -- neither is relevant to the theme wiring under test here.
 *
 * Deviation from a "fully real" integration test -- CustomThemesManager is driven via mockStatic,
 * not a real Spring bean, and this is deliberate, not a shortcut:
 *   1. CustomThemesManager(KeyValueStorageManager, DataSpace)'s constructor tries
 *      Class.forName("inetsoft.enterprise.theme.CustomThemesImpl") and falls back to the plain
 *      community inetsoft.sree.portal.CustomThemesImpl on ClassNotFoundException. Under
 *      community/core that fallback is ALWAYS what runs, and (verified by reading the class) it is
 *      a pure no-op stub: getCustomThemes() always returns a brand-new empty HashSet,
 *      setCustomThemes(...) does nothing, getOrgSelectedTheme() always returns the literal string
 *      "default". A "real" CustomThemesManager bean in this module cannot persist or expose any of
 *      the state this scenario needs to assert on.
 *   2. There is no getOrgSelectedTheme(String orgId) overload anywhere in the codebase (community
 *      or enterprise) -- only a no-arg getOrgSelectedTheme() that resolves against the *current*
 *      org context, not an arbitrary orgId. The per-org "SreeEnv-backed pointer" this scenario
 *      needs to inspect for an arbitrary toOrgId is only reachable by capturing the arguments of
 *      setOrgSelectedTheme(String themeId, String orgId) calls -- which is exactly what the mock
 *      below does (into orgSelectedThemePointer), standing in for the real SreeEnv-backed storage.
 * Everything else in the copyOrganizationInternal() call graph that matters for this scenario
 * (DataSpace, PortalThemesManager, OrganizationManager.runInOrgScope, ScheduleManager) is exercised
 * for real through the Spring context below -- only the theme manager itself is mocked, and only
 * because no working "real" implementation of it is reachable from this module.
 */

import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.portal.CustomTheme;
import inetsoft.sree.portal.CustomThemesManager;
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

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  OrgLifecycleThemeOrchestrationTest.PortalThemesManagerConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class OrgLifecycleThemeOrchestrationTest {

   // ── scenario 3c, branch 1: org-owned theme (also the org's own selected default) ──

   @Test
   void copy_orgOwnedSelectedTheme_threeStatesAgree() throws Exception {
      String fromOrgId = "theme_orch_owned_from";
      String toOrgId = "theme_orch_owned_to";

      CustomTheme ownTheme = new CustomTheme();
      ownTheme.setId("theme-owned-1");
      ownTheme.setOrgID(fromOrgId);
      ownTheme.setOrganizations(new ArrayList<>(List.of(fromOrgId)));

      Set<CustomTheme>[] storedThemes = new Set[] { new HashSet<>(Set.of(ownTheme)) };
      Map<String, String> orgSelectedThemePointer = new HashMap<>();

      CustomThemesManager mockManager = mockThemesManager(storedThemes, orgSelectedThemePointer);
      StubProvider provider = new StubProvider();

      try(MockedStatic<CustomThemesManager> ctm = mockStatic(CustomThemesManager.class)) {
         ctm.when(CustomThemesManager::getManager).thenReturn(mockManager);

         provider.copyOrganization(fromOrganization(fromOrgId), toOrgId,
            mock(IdentityService.class), mock(IdentityThemeService.class),
            mock(DashboardRegistryManager.class), mock(DataCycleManager.class),
            mock(Principal.class), false, null);
      }

      assertNotNull(provider.capturedOrganization,
                    "addOrganization(...) must have been called with the new org");
      String cloneId = provider.capturedOrganization.getTheme();

      // state 3: Organization.theme -- the literal value copyOrganizationInternal() wires onto
      // the new Organization object via newOrg.setTheme(newOrgThemeId)
      assertNotNull(cloneId,
                    "org-owned selected theme: newOrg.getTheme() must carry the clone's id");
      assertNotEquals("theme-owned-1", cloneId,
                      "newOrg.getTheme() must be the CLONE's id, not the original theme's id");

      Set<CustomTheme> finalThemes = storedThemes[0];
      assertEquals(2, finalThemes.size(), "original + clone expected in the persisted set");

      // state 2: CustomTheme.organizations / identity -- the clone that actually landed in the
      // set setCustomThemes() was called with
      List<CustomTheme> toOrgThemes = finalThemes.stream()
         .filter(t -> toOrgId.equals(t.getOrgID())).toList();
      assertEquals(1, toOrgThemes.size(), "exactly one cloned theme must belong to toOrgId");
      assertEquals(cloneId, toOrgThemes.get(0).getId(),
                  "the cloned theme's id must be the same id newOrg.getTheme() carries -- state 2 "
                  + "and state 3 must agree");

      // state 1: SreeEnv-backed selection pointer (captured via setOrgSelectedTheme(id, orgId),
      // see class-level comment for why this stands in for a real SreeEnv-backed read)
      assertEquals(cloneId, orgSelectedThemePointer.get(toOrgId),
                  "CustomThemesManager's org-selection pointer for toOrgId must point at the same "
                  + "clone id -- state 1 must also agree with states 2 and 3");

      // replace=false: the source org's own theme must be completely untouched
      List<CustomTheme> fromOrgThemes = finalThemes.stream()
         .filter(t -> fromOrgId.equals(t.getOrgID())).toList();
      assertEquals(1, fromOrgThemes.size(), "the original fromOrgId-owned theme must still exist");
      assertEquals("theme-owned-1", fromOrgThemes.get(0).getId(),
                  "the original theme's id must be unchanged");
      assertTrue(fromOrgThemes.get(0).getOrganizations().contains(fromOrgId),
                "the original theme must still list fromOrgId as a selecting org (replace=false "
                + "must not touch the source)");
   }

   // ── scenario 3c, branch 2: global theme merely selected by fromOrg (known gap) ──

   @Test
   void copy_globalThemeSelectedByFromOrg_organizationThemeFieldStaysNullDespiteSreeEnvAndOrganizationsListAgreeing()
      throws Exception
   {
      String fromOrgId = "theme_orch_global_from";
      String toOrgId = "theme_orch_global_to";

      CustomTheme globalTheme = new CustomTheme();
      globalTheme.setId("theme-global-1");
      globalTheme.setOrgID(null);
      globalTheme.setOrganizations(new ArrayList<>(List.of(fromOrgId)));

      Set<CustomTheme>[] storedThemes = new Set[] { new HashSet<>(Set.of(globalTheme)) };
      Map<String, String> orgSelectedThemePointer = new HashMap<>();

      CustomThemesManager mockManager = mockThemesManager(storedThemes, orgSelectedThemePointer);
      StubProvider provider = new StubProvider();

      try(MockedStatic<CustomThemesManager> ctm = mockStatic(CustomThemesManager.class)) {
         ctm.when(CustomThemesManager::getManager).thenReturn(mockManager);

         provider.copyOrganization(fromOrganization(fromOrgId), toOrgId,
            mock(IdentityService.class), mock(IdentityThemeService.class),
            mock(DashboardRegistryManager.class), mock(DataCycleManager.class),
            mock(Principal.class), false, null);
      }

      assertNotNull(provider.capturedOrganization,
                    "addOrganization(...) must have been called with the new org");

      // This pair of assertions is the documented gap itself (matrix row 3c, "已知问题，测试记录当前
      // 行为不假定应修复"): copyThemes() only ever assigns its return value inside branch 1's
      // "org-owned AND selected" inner if -- a merely-selected GLOBAL theme never reaches that
      // line, so newOrgThemeId stays null all the way out to newOrg.setTheme(newOrgThemeId), even
      // though the selection pointer WAS propagated to toOrgId one line below in copyThemes().
      // Recorded here as current behavior, not asserted as correct.
      assertNull(provider.capturedOrganization.getTheme(),
                "known gap: newOrg.getTheme() stays null for a merely-selected global theme -- "
                + "copyThemes()'s newOrgThemeId is never assigned outside the org-owned branch");
      assertEquals("theme-global-1", orgSelectedThemePointer.get(toOrgId),
                  "yet CustomThemesManager's own org-selection pointer for toOrgId IS populated -- "
                  + "state 1 (SreeEnv-backed pointer) and state 2 (organizations list, asserted "
                  + "below) agree with each other, but state 3 (Organization.theme) disagrees "
                  + "with both");

      Set<CustomTheme> finalThemes = storedThemes[0];
      assertEquals(1, finalThemes.size(), "a merely-selected global theme must never be cloned");

      CustomTheme onlyTheme = finalThemes.iterator().next();
      assertNull(onlyTheme.getOrgID(), "the theme must remain global (orgID stays null)");
      assertTrue(onlyTheme.getOrganizations().contains(toOrgId),
                "toOrgId must be appended to the global theme's organizations list -- state 2");
   }

   private static Organization fromOrganization(String fromOrgId) {
      FSOrganization fromOrganization = new FSOrganization(fromOrgId);
      fromOrganization.setName(fromOrgId);
      return fromOrganization;
   }

   /**
    * Builds a mock {@link CustomThemesManager} whose {@code getCustomThemes()}/
    * {@code setCustomThemes(...)} round-trip through {@code storedThemes[0]}, and whose
    * {@code setOrgSelectedTheme(id, orgId)} calls are captured into {@code orgSelectedThemePointer}
    * -- see the class-level comment for why a mock (rather than a real bean) is necessary here.
    */
   private static CustomThemesManager mockThemesManager(Set<CustomTheme>[] storedThemes,
                                                         Map<String, String> orgSelectedThemePointer)
   {
      CustomThemesManager mockManager = mock(CustomThemesManager.class);

      when(mockManager.getCustomThemes()).thenAnswer(invocation -> storedThemes[0]);

      doAnswer(invocation -> {
         storedThemes[0] = invocation.getArgument(0);
         return null;
      }).when(mockManager).setCustomThemes(any());

      doAnswer(invocation -> {
         String themeId = invocation.getArgument(0);
         String orgId = invocation.getArgument(1);
         orgSelectedThemePointer.put(orgId, themeId);
         return null;
      }).when(mockManager).setOrgSelectedTheme(any(), any());

      return mockManager;
   }

   // ── PortalThemesManager override -- BaseTestConfiguration's is a bare unstubbed mock, whose
   //    getCssEntries() returns null by default. copyOrganizationInternal() calls
   //    manager.getCssEntries().get(fromOrgId) unconditionally, unguarded by any try/catch, so an
   //    unstubbed mock NPEs before copyThemes() is ever reached. ──

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
    * Mirrors AbstractEditableAuthenticationProviderStaticDepTest.StubProvider, plus one addition:
    * addOrganization(...) is itself a no-op in AbstractEditableAuthenticationProvider (never
    * called on this subclass otherwise), so overriding it here is the only way to observe the
    * Organization object copyOrganizationInternal() actually builds and wires
    * newOrg.setTheme(newOrgThemeId) onto -- copyOrganization(...) never returns it.
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
