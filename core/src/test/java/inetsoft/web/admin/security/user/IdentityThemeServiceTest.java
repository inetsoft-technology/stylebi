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
package inetsoft.web.admin.security.user;

/*
 * Issue #75739 (fixed) / matrix row 3d: community/core/src/test/resources/docs/org-lifecycle-resource-matrix.md,
 * section "三、其他机制" / "3.1 主题（Theme）", Delete 场景.
 *
 * removeTheme(orgID) now also strips the deleted org's ID out of every remaining (e.g.
 * globally-shared) theme's `organizations` list, not just themes owned by the deleted org.
 */

import inetsoft.sree.portal.CustomTheme;
import inetsoft.sree.portal.CustomThemesManager;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
class IdentityThemeServiceTest {

   private CustomThemesManager manager;
   private IdentityThemeService service;

   @BeforeEach
   void setUp() {
      manager = mock(CustomThemesManager.class);
      service = new IdentityThemeService(manager);
   }

   // sanity check for the behavior that IS already correct today -- kept here so this file
   // doesn't consist solely of the disabled regression test below.
   @Test
   void removeTheme_orgOwnedTheme_removedFromSetAndSelectionReset() {
      CustomTheme ownedTheme = new CustomTheme();
      ownedTheme.setId("owned-1");
      ownedTheme.setOrgID("deletedOrg");
      ownedTheme.setOrganizations(new ArrayList<>());

      when(manager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(ownedTheme)));

      service.removeTheme("deletedOrg");

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Set<CustomTheme>> captor = ArgumentCaptor.forClass(Set.class);
      verify(manager).setCustomThemes(captor.capture());

      assertTrue(captor.getValue().isEmpty(), "the org-owned theme itself must be removed");
      verify(manager).setOrgSelectedTheme("default", "deletedOrg");
   }

   // Issue #75739 (fixed)
   @Test
   void removeTheme_globalThemeStillListsDeletedOrg_organizationsEntryIsStripped() {
      CustomTheme globalTheme = new CustomTheme();
      globalTheme.setId("global-1");
      globalTheme.setOrgID(null);
      globalTheme.setOrganizations(new ArrayList<>(List.of("deletedOrg", "otherOrg")));

      when(manager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(globalTheme)));

      service.removeTheme("deletedOrg");

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Set<CustomTheme>> captor = ArgumentCaptor.forClass(Set.class);
      verify(manager).setCustomThemes(captor.capture());

      CustomTheme saved = captor.getValue().iterator().next();
      assertFalse(saved.getOrganizations().contains("deletedOrg"),
         "deleting an organization must strip its ID from every globally-shared theme's "
         + "organizations list, not just the org-owned themes -- otherwise a future organization "
         + "that reuses this same ID silently inherits the stale selection (Issue #75739)");
      assertTrue(saved.getOrganizations().contains("otherOrg"),
         "an unrelated organization's own membership must survive the cleanup");
   }

   // Bug #76638: `theme` was accepted and echoed at preview for user/group/role create and
   // update, but never actually persisted as CustomTheme membership -- SecurityApiService's
   // createUser/createGroup/createRole never read request.getTheme() at all, and
   // updateUser/updateGroup/updateRole captured it into an Edit*PaneModel that was never read
   // back out. assignTheme() generalizes updateUserTheme's add-to-theme logic across
   // getUsers/getGroups/getRoles so all three unit types can be assigned, not just renamed.
   @Test
   void assignTheme_newIdentity_addsToMatchingTheme() {
      CustomTheme theme = new CustomTheme();
      theme.setId("theme-1");

      when(manager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(theme)));

      service.assignTheme("newgroup1", "newgroup1", "theme-1", CustomTheme::getGroups);

      assertTrue(theme.getGroups().contains("newgroup1"),
         "a brand-new identity with no prior membership must be added to the theme named by "
         + "ntheme");
   }

   @Test
   void assignTheme_renamedIdentity_migratesMembershipAndKeepsThemeAssignment() {
      CustomTheme theme = new CustomTheme();
      theme.setId("theme-1");
      theme.setRoles(new ArrayList<>(List.of("oldRoleName")));

      when(manager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(theme)));

      service.assignTheme("oldRoleName", "newRoleName", "theme-1", CustomTheme::getRoles);

      assertFalse(theme.getRoles().contains("oldRoleName"));
      assertTrue(theme.getRoles().contains("newRoleName"),
         "renaming an identity already assigned to a theme must migrate its membership entry, "
         + "not drop it");
   }

   @Test
   void assignTheme_nullNtheme_onlyRenamesDoesNotAssign() {
      CustomTheme theme = new CustomTheme();
      theme.setId("theme-1");

      when(manager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(theme)));

      service.assignTheme("newuser1", "newuser1", null, CustomTheme::getUsers);

      assertFalse(theme.getUsers().contains("newuser1"),
         "a null ntheme (no theme requested) must not add the identity to an unrelated theme");
   }

   // Round-2 review finding (05-review-r1.md): reassigning an identity to a DIFFERENT theme
   // without a rename (oldId == id) previously only ever added it to the new theme -- the
   // remove(oldId)+add(id) in the same theme netted to a no-op whenever oldId == id, so the
   // identity was left a member of BOTH its previous theme and the new one.
   @Test
   void assignTheme_reassignWithoutRename_removesFromPreviousThemeAndAddsToNewOne() {
      CustomTheme themeA = new CustomTheme();
      themeA.setId("theme-a");
      themeA.setUsers(new ArrayList<>(List.of("user1")));

      CustomTheme themeB = new CustomTheme();
      themeB.setId("theme-b");

      when(manager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(themeA, themeB)));

      service.assignTheme("user1", "user1", "theme-b", CustomTheme::getUsers);

      assertFalse(themeA.getUsers().contains("user1"),
         "reassigning to a different theme (no rename) must remove the identity from its "
         + "previous theme, not leave it a member of both");
      assertTrue(themeB.getUsers().contains("user1"),
         "reassigning to a different theme (no rename) must add the identity to the new theme");
   }

   // Bug #76671, finding 2b: updateTheme(oldId=null, ...) used to treat a null oldId as
   // matching every global (orgID == null) theme via Tool.equals(null, null) == true, silently
   // re-scoping that global theme to the new identity's id and then NPEing on
   // getJarPath().replace(oldId, id). oldId == null is a create-time call (no prior identity to
   // rename from) and must never be treated as a rename match against a global theme's null orgID.
   @Test
   void updateTheme_oldIdNull_doesNotMutateGlobalThemeOrgId() {
      CustomTheme globalTheme = new CustomTheme();
      globalTheme.setId("global-1");
      globalTheme.setOrgID(null);
      globalTheme.setJarPath("/orig/path.jar");
      globalTheme.setUsers(new ArrayList<>());

      when(manager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(globalTheme)));

      assertDoesNotThrow(() -> service.updateTheme(null, "newOrg", CustomTheme::getUsers),
         "a null oldId (create, not rename) must not be treated as matching a global theme's "
         + "null orgID");

      assertNull(globalTheme.getOrgID(),
         "a global theme's orgID must not be silently re-scoped by a create-time call");
      assertEquals("/orig/path.jar", globalTheme.getJarPath());
   }
}
