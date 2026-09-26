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
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

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

   // Issue #77056: renaming a group whose name equals another organization's ID must not move
   // that organization's themes (orgID and jar path) into an organization named like the group
   @Test
   void updateTheme_groupNamedLikeOtherOrgId_otherOrgThemeUntouched() {
      CustomTheme bTheme = theme("bTheme", ORG_B);
      when(manager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(bTheme)));

      service.updateTheme(ORG_B, "renamedGroup", ORG_A, CustomTheme::getGroups);

      assertEquals(ORG_B, bTheme.getOrgID(), "another organization's theme must keep its owner");
      assertEquals("portal/" + ORG_B + "/theme/bTheme.jar", bTheme.getJarPath(),
         "the jar path (directory and file name) must not be rewritten");
      verify(manager, never()).setCustomThemes(any());
   }

   // Issue #77056: renaming a role to the caller's own organization ID must not move the themes
   // of the organization the old role name happened to match into the caller's organization
   @Test
   void updateTheme_roleNamedLikeOtherOrgId_otherOrgThemeNotMovedIntoCallerOrg() {
      CustomTheme bTheme = theme("bTheme", ORG_B);
      when(manager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(bTheme)));

      service.updateTheme(ORG_B, ORG_A, ORG_A, CustomTheme::getRoles);

      assertEquals(ORG_B, bTheme.getOrgID());
      assertEquals("portal/" + ORG_B + "/theme/bTheme.jar", bTheme.getJarPath());
   }

   // Issue #77056: identity names are only unique per organization, so renaming a user or group
   // in one organization must not rename a same-named identity of another organization, neither
   // in that organization's private themes nor in global themes (whose entries refer to
   // identities of the default organization)
   @Test
   void updateTheme_orgScopedRename_onlyRenamesInOwnOrgThemes() {
      CustomTheme aTheme = theme("aTheme", ORG_A);
      aTheme.getUsers().add("bob");
      aTheme.getGroups().add("sales");
      CustomTheme bTheme = theme("bTheme", ORG_B);
      bTheme.getUsers().add("bob");
      bTheme.getGroups().add("sales");
      CustomTheme globalTheme = theme("globalTheme", null);
      globalTheme.getUsers().add("bob");
      globalTheme.getGroups().add("sales");
      when(manager.getCustomThemes())
         .thenReturn(new HashSet<>(Set.of(aTheme, bTheme, globalTheme)));

      service.updateTheme("bob", "robert", ORG_A, CustomTheme::getUsers);
      service.updateTheme("sales", "sales2", ORG_A, CustomTheme::getGroups);

      assertEquals(List.of("robert"), aTheme.getUsers());
      assertEquals(List.of("sales2"), aTheme.getGroups());
      assertEquals(List.of("bob"), bTheme.getUsers(), "org B's bob must keep its theme");
      assertEquals(List.of("sales"), bTheme.getGroups(), "org B's sales must keep its theme");
      assertEquals(List.of("bob"), globalTheme.getUsers(),
         "a global theme's bob belongs to the default organization, not to org A");
      assertEquals(List.of("sales"), globalTheme.getGroups());
   }

   // Issue #77056: the default organization (and so a single-tenant installation, where every
   // theme is global) still renames identities in global themes
   @Test
   void updateTheme_defaultOrgRename_renamesInGlobalTheme() {
      CustomTheme globalTheme = theme("globalTheme", null);
      globalTheme.getUsers().add("bob");
      when(manager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(globalTheme)));

      service.updateTheme("bob", "robert", HOST_ORG, CustomTheme::getUsers);

      assertEquals(List.of("robert"), globalTheme.getUsers());
      verify(manager).setCustomThemes(any());
   }

   // Issue #77056: a global role (no organization) is referenced from every organization's
   // themes, so renaming it must still update all of them
   @Test
   void updateTheme_globalRoleRename_renamesInEveryOrgTheme() {
      CustomTheme aTheme = theme("aTheme", ORG_A);
      aTheme.getRoles().add("Designer");
      CustomTheme bTheme = theme("bTheme", ORG_B);
      bTheme.getRoles().add("Designer");
      CustomTheme globalTheme = theme("globalTheme", null);
      globalTheme.getRoles().add("Designer");
      when(manager.getCustomThemes())
         .thenReturn(new HashSet<>(Set.of(aTheme, bTheme, globalTheme)));

      service.updateTheme("Designer", "Lead", null, CustomTheme::getRoles);

      assertEquals(List.of("Lead"), aTheme.getRoles());
      assertEquals(List.of("Lead"), bTheme.getRoles());
      assertEquals(List.of("Lead"), globalTheme.getRoles());
   }

   // Issue #77056: the REST create paths passed a null old name, which matched the null orgID
   // of every global theme and threw a NullPointerException from the jar path rewrite
   @Test
   void updateTheme_nullOldName_noExceptionAndNoChange() {
      CustomTheme globalTheme = theme("globalTheme", null);
      when(manager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(globalTheme)));

      assertDoesNotThrow(
         () -> service.updateTheme(null, "newGroup", ORG_A, CustomTheme::getUsers));

      assertNull(globalTheme.getOrgID());
      assertEquals("portal/theme/globalTheme.jar", globalTheme.getJarPath());
      assertTrue(globalTheme.getUsers().isEmpty());
   }

   // Issue #77056: the selected theme id comes from the client and must not add the user to a
   // theme of another organization
   @Test
   void updateUserTheme_otherOrgThemeId_ignored() {
      CustomTheme aTheme = theme("aTheme", ORG_A);
      aTheme.getUsers().add("bob");
      CustomTheme bTheme = theme("bTheme", ORG_B);
      when(manager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(aTheme, bTheme)));

      service.updateUserTheme("bob", "bob", ORG_A, "bTheme");

      assertTrue(bTheme.getUsers().isEmpty(), "org A must not assign users to org B's theme");
      assertEquals(List.of("bob"), aTheme.getUsers(), "an ignored selection keeps the old theme");
   }

   // Issue #77056: renaming a user in one organization must not strip a same-named user of
   // another organization from its theme
   @Test
   void updateUserTheme_rename_otherOrgSameNamedUserUntouched() {
      CustomTheme aTheme = theme("aTheme", ORG_A);
      aTheme.getUsers().add("bob");
      CustomTheme bTheme = theme("bTheme", ORG_B);
      bTheme.getUsers().add("bob");
      when(manager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(aTheme, bTheme)));

      service.updateUserTheme("bob", "robert", ORG_A, "aTheme");

      assertEquals(List.of("robert"), aTheme.getUsers());
      assertEquals(List.of("bob"), bTheme.getUsers());
   }

   // Issue #77056: the identity editor shows a single theme per user, so selecting another
   // theme (or the default theme) replaces the previous assignment instead of adding to it
   @Test
   void updateUserTheme_selectOtherTheme_replacesPreviousAssignment() {
      CustomTheme x = theme("x", HOST_ORG);
      x.getUsers().add("bob");
      CustomTheme y = theme("y", HOST_ORG);
      when(manager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(x, y)));

      service.updateUserTheme("bob", "bob", HOST_ORG, "y");

      assertTrue(x.getUsers().isEmpty());
      assertEquals(List.of("bob"), y.getUsers());

      service.updateUserTheme("bob", "bob", HOST_ORG, "");

      assertTrue(x.getUsers().isEmpty());
      assertTrue(y.getUsers().isEmpty());
   }

   // Issue #77056: a global theme's bare user entry refers to the default organization's user,
   // so it must not be shown as the theme of another organization's same-named user
   @Test
   void getTheme_globalThemeUserEntry_notShownForOtherOrgSameNamedUser() {
      CustomTheme globalTheme = theme("globalTheme", null);
      globalTheme.getUsers().add("bob");
      when(manager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(globalTheme)));
      OrganizationManager orgManager = mock(OrganizationManager.class);

      try(MockedStatic<OrganizationManager> orgManagerStatic =
             mockStatic(OrganizationManager.class))
      {
         orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
         when(orgManager.getCurrentOrgID()).thenReturn(ORG_B);
         assertNull(service.getTheme(new IdentityID("bob", ORG_B), CustomTheme::getUsers));

         when(orgManager.getCurrentOrgID()).thenReturn(HOST_ORG);
         assertEquals("globalTheme",
            service.getTheme(new IdentityID("bob", HOST_ORG), CustomTheme::getUsers));
      }
   }

   private static CustomTheme theme(String id, String orgID) {
      CustomTheme theme = new CustomTheme();
      theme.setId(id);
      theme.setName(id);
      theme.setOrgID(orgID);
      theme.setJarPath(orgID == null ? "portal/theme/" + id + ".jar" :
                          "portal/" + orgID + "/theme/" + id + ".jar");
      return theme;
   }

   private static final String HOST_ORG = "host-org";
   private static final String ORG_A = "organizationA";
   private static final String ORG_B = "organization1";
}
