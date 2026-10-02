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

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.portal.CustomTheme;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.portal.CustomThemesManagerMocks;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
class IdentityThemeServiceTest {

   private CustomThemesManager manager;
   private IdentityThemeService service;

   @BeforeEach
   void setUp() {
      manager = mock(CustomThemesManager.class);
      CustomThemesManagerMocks.applyUpdates(manager);
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

   // Bug #77304: the reserved default theme id selects the default theme like an empty string,
   // so it removes the user from the previously selected theme instead of being ignored
   @Test
   void updateUserTheme_defaultThemeId_clearsPreviousAssignment() {
      CustomTheme x = theme("x", HOST_ORG);
      x.getUsers().add("bob");
      CustomTheme y = theme("y", HOST_ORG);
      when(manager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(x, y)));

      service.updateUserTheme("bob", "bob", HOST_ORG, CustomTheme.DEFAULT_THEME_ID);

      assertTrue(x.getUsers().isEmpty(), "the default theme id must clear the user's theme");
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

   // review finding MINOR-1: the organizations list holds organization IDs, so a non-default
   // organization's default global theme is still shown for that organization
   @Test
   void getTheme_organizationsList_globalThemeShownForNonDefaultOrg() {
      CustomTheme globalTheme = theme("globalTheme", null);
      globalTheme.getOrganizations().add(ORG_B);
      when(manager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(globalTheme)));
      OrganizationManager orgManager = mock(OrganizationManager.class);

      try(MockedStatic<OrganizationManager> orgManagerStatic =
             mockStatic(OrganizationManager.class))
      {
         orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
         when(orgManager.getCurrentOrgID()).thenReturn(ORG_B);
         assertEquals("globalTheme",
            service.getTheme(new IdentityID(ORG_B, ORG_B), CustomTheme::getOrganizations));
      }
   }

   // review finding MINOR-2: when a user is listed on several themes, the identity editor shows
   // the theme with the lowest ID, which is the one applied at runtime
   @Test
   void getTheme_severalMatchingThemes_returnsLowestId() {
      Set<CustomTheme> themes = new HashSet<>();

      for(String id : List.of("t5", "t3", "t9", "t1", "t7")) {
         CustomTheme theme = theme(id, HOST_ORG);
         theme.getUsers().add("bob");
         themes.add(theme);
      }

      when(manager.getCustomThemes()).thenReturn(themes);
      OrganizationManager orgManager = mock(OrganizationManager.class);

      try(MockedStatic<OrganizationManager> orgManagerStatic =
             mockStatic(OrganizationManager.class))
      {
         orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
         when(orgManager.getCurrentOrgID()).thenReturn(HOST_ORG);
         assertEquals("t1",
            service.getTheme(new IdentityID("bob", HOST_ORG), CustomTheme::getUsers));
      }
   }

   // Issue #77084: a deleted user must be removed from its organization's themes, so a new
   // same-named user of that organization does not inherit the theme
   @Test
   void removeIdentity_orgUser_removedFromOwnOrgThemeOnly() {
      CustomTheme aTheme = theme("aTheme", ORG_A);
      aTheme.getUsers().addAll(List.of("bob", "alice"));
      CustomTheme bTheme = theme("bTheme", ORG_B);
      bTheme.getUsers().add("bob");
      CustomTheme globalTheme = theme("globalTheme", null);
      globalTheme.getUsers().add("bob");
      when(manager.getCustomThemes())
         .thenReturn(new HashSet<>(Set.of(aTheme, bTheme, globalTheme)));

      service.removeIdentity("bob", ORG_A, CustomTheme::getUsers);

      assertEquals(List.of("alice"), aTheme.getUsers());
      assertEquals(List.of("bob"), bTheme.getUsers(), "org B's bob must keep its theme");
      assertEquals(List.of("bob"), globalTheme.getUsers(),
         "a global theme's bob belongs to the default organization, not to org A");
      verify(manager).setCustomThemes(any());
   }

   // Issue #77084: a global theme's user entries refer to users of the default organization
   @Test
   void removeIdentity_defaultOrgUser_removedFromGlobalTheme() {
      CustomTheme globalTheme = theme("globalTheme", null);
      globalTheme.getUsers().add("bob");
      CustomTheme aTheme = theme("aTheme", ORG_A);
      aTheme.getUsers().add("bob");
      when(manager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(globalTheme, aTheme)));

      service.removeIdentity("bob", HOST_ORG, CustomTheme::getUsers);

      assertTrue(globalTheme.getUsers().isEmpty());
      assertEquals(List.of("bob"), aTheme.getUsers(), "org A's bob must keep its theme");
   }

   // Issue #77084: a global role is referenced from every organization's themes
   @Test
   void removeIdentity_globalRole_removedFromEveryTheme() {
      CustomTheme aTheme = theme("aTheme", ORG_A);
      aTheme.getRoles().add("Designer");
      CustomTheme bTheme = theme("bTheme", ORG_B);
      bTheme.getRoles().add("Designer");
      CustomTheme globalTheme = theme("globalTheme", null);
      globalTheme.getRoles().addAll(List.of("Designer", "Designer", "Viewer"));
      when(manager.getCustomThemes())
         .thenReturn(new HashSet<>(Set.of(aTheme, bTheme, globalTheme)));

      service.removeIdentity("Designer", null, CustomTheme::getRoles);

      assertTrue(aTheme.getRoles().isEmpty());
      assertTrue(bTheme.getRoles().isEmpty());
      assertEquals(List.of("Viewer"), globalTheme.getRoles(), "duplicates must be removed too");
   }

   // Issue #77084: a deleted group is removed from the group list only
   @Test
   void removeIdentity_group_removedFromGroupListOnly() {
      CustomTheme aTheme = theme("aTheme", ORG_A);
      aTheme.getGroups().add("sales");
      aTheme.getUsers().add("sales");
      aTheme.getRoles().add("sales");
      when(manager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(aTheme)));

      service.removeIdentity("sales", ORG_A, CustomTheme::getGroups);

      assertTrue(aTheme.getGroups().isEmpty());
      assertEquals(List.of("sales"), aTheme.getUsers());
      assertEquals(List.of("sales"), aTheme.getRoles());
   }

   // Issue #77084: deleting an identity that no theme refers to must not rewrite the themes
   @Test
   void removeIdentity_notReferenced_noWrite() {
      CustomTheme aTheme = theme("aTheme", ORG_A);
      aTheme.getUsers().add("alice");
      CustomTheme bTheme = theme("bTheme", ORG_B);
      bTheme.getUsers().add("bob");
      when(manager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(aTheme, bTheme)));

      service.removeIdentity("bob", ORG_A, CustomTheme::getUsers);
      service.removeIdentity(null, ORG_A, CustomTheme::getUsers);

      verify(manager, never()).setCustomThemes(any());
      assertEquals(List.of("alice"), aTheme.getUsers());
      assertEquals(List.of("bob"), bTheme.getUsers());
   }

   // Issue #77116: a site admin editing another organization is offered the global themes and
   // the edited organization's themes, not the themes of the admin's current organization
   @Test
   void getThemes_siteAdminEditsOtherOrg_listsEditedOrgThemes() {
      Principal principal = mock(Principal.class);

      try(MockedStatic<OrganizationManager> ignored = mockThemesAndOrg(ORG_A, true, principal)) {
         assertEquals(Set.of("tb", "g", "e"), themeIds(service.getThemes(ORG_B, principal)));
      }
   }

   // Issue #77116: an organization admin can only list its own organization's themes, so another
   // organization ID falls back to the current organization
   @Test
   void getThemes_nonSiteAdminPassesOtherOrg_listsCurrentOrgThemes() {
      Principal principal = mock(Principal.class);

      try(MockedStatic<OrganizationManager> ignored = mockThemesAndOrg(ORG_A, false, principal)) {
         assertEquals(Set.of("ta", "g", "e"), themeIds(service.getThemes(ORG_B, principal)));
      }
   }

   // Issue #77116: without an organization ID the current organization's themes are listed
   @Test
   void getThemes_noOrgId_listsCurrentOrgThemes() {
      Principal principal = mock(Principal.class);

      try(MockedStatic<OrganizationManager> ignored = mockThemesAndOrg(ORG_A, true, principal)) {
         assertEquals(Set.of("ta", "g", "e"), themeIds(service.getThemes()));
         assertEquals(Set.of("ta", "g", "e"), themeIds(service.getThemes("", principal)));
         assertEquals(Set.of("ta", "g", "e"), themeIds(service.getThemes(ORG_A, principal)));
      }
   }

   // Bug #76638: `theme` was accepted and echoed at preview for user/group/role create and
   // update, but never actually persisted as CustomTheme membership -- SecurityService's
   // createUser/createGroup/createRole never read request.getTheme() at all, and
   // updateUser/updateGroup/updateRole captured it into an Edit*PaneModel that was never read
   // back out. assignTheme() generalizes updateUserTheme's add-to-theme logic across
   // getUsers/getGroups/getRoles/getOrganizations so all unit types can be assigned, not just
   // renamed.
   @Test
   void assignTheme_newIdentity_addsToMatchingTheme() {
      CustomTheme theme = theme("theme-1", ORG_A);
      when(manager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(theme)));

      service.assignTheme("newgroup1", "newgroup1", ORG_A, "theme-1", CustomTheme::getGroups);

      assertTrue(theme.getGroups().contains("newgroup1"),
         "a brand-new identity with no prior membership must be added to the theme named by "
         + "ntheme");
   }

   @Test
   void assignTheme_renamedIdentity_migratesMembershipAndKeepsThemeAssignment() {
      CustomTheme theme = theme("theme-1", ORG_A);
      theme.getRoles().add("oldRoleName");
      when(manager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(theme)));

      service.assignTheme("oldRoleName", "newRoleName", ORG_A, "theme-1", CustomTheme::getRoles);

      assertFalse(theme.getRoles().contains("oldRoleName"));
      assertTrue(theme.getRoles().contains("newRoleName"),
         "renaming an identity already assigned to a theme must migrate its membership entry, "
         + "not drop it");
   }

   @Test
   void assignTheme_nullNtheme_onlyRenamesDoesNotAssign() {
      CustomTheme theme = theme("theme-1", ORG_A);
      when(manager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(theme)));

      service.assignTheme("newuser1", "newuser1", ORG_A, null, CustomTheme::getUsers);

      assertFalse(theme.getUsers().contains("newuser1"),
         "a null ntheme (no theme requested) must not add the identity to an unrelated theme");
   }

   // Round-2 review finding (05-review-r1.md): reassigning an identity to a DIFFERENT theme
   // without a rename (oldId == id) must not leave it a member of both its previous theme and
   // the new one.
   @Test
   void assignTheme_reassignWithoutRename_removesFromPreviousThemeAndAddsToNewOne() {
      CustomTheme themeA = theme("theme-a", HOST_ORG);
      themeA.getUsers().add("user1");
      CustomTheme themeB = theme("theme-b", HOST_ORG);
      when(manager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(themeA, themeB)));

      service.assignTheme("user1", "user1", HOST_ORG, "theme-b", CustomTheme::getUsers);

      assertFalse(themeA.getUsers().contains("user1"),
         "reassigning to a different theme (no rename) must remove the identity from its "
         + "previous theme, not leave it a member of both");
      assertTrue(themeB.getUsers().contains("user1"),
         "reassigning to a different theme (no rename) must add the identity to the new theme");
   }

   // Issue #77056 applied to assignTheme: the requested theme id comes from the client and must
   // not add a group to a theme of another organization
   @Test
   void assignTheme_otherOrgThemeId_ignored() {
      CustomTheme bTheme = theme("bTheme", ORG_B);
      when(manager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(bTheme)));

      service.assignTheme("sales", "sales", ORG_A, "bTheme", CustomTheme::getGroups);

      assertTrue(bTheme.getGroups().isEmpty(), "org A must not assign groups to org B's theme");
      verify(manager, never()).setCustomThemes(any());
   }

   // the organizations list holds organization IDs, so a global theme can be assigned to a
   // non-default organization, while another organization's private theme cannot
   @Test
   void assignTheme_organizationsList_globalThemeAssignableToNonDefaultOrg() {
      CustomTheme globalTheme = theme("globalTheme", null);
      CustomTheme aTheme = theme("aTheme", ORG_A);
      when(manager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(globalTheme, aTheme)));

      service.assignTheme(ORG_B, ORG_B, ORG_B, "globalTheme", CustomTheme::getOrganizations);
      service.assignTheme(ORG_B, ORG_B, ORG_B, "aTheme", CustomTheme::getOrganizations);

      assertEquals(List.of(ORG_B), globalTheme.getOrganizations(),
         "the ignored org A theme must leave the global theme assignment in place");
      assertTrue(aTheme.getOrganizations().isEmpty());
   }

   // the organizations list is keyed by organization ID, not by the organization's name
   @Test
   void getTheme_organizationKey_matchesOrganizationId() {
      CustomTheme globalTheme = theme("globalTheme", null);
      globalTheme.getOrganizations().add(ORG_B);
      when(manager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(globalTheme)));
      OrganizationManager orgManager = mock(OrganizationManager.class);

      try(MockedStatic<OrganizationManager> orgManagerStatic =
             mockStatic(OrganizationManager.class))
      {
         orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
         when(orgManager.getCurrentOrgID()).thenReturn(ORG_B);
         assertEquals("globalTheme", service.getTheme(ORG_B, CustomTheme::getOrganizations));
         assertNull(service.getTheme("Organization B", CustomTheme::getOrganizations));
      }
   }

   // Bug #77352: the group and role editors assign the selected theme, like the user editor
   @Test
   void updateIdentityTheme_group_assignsKeepsAndClears() {
      CustomTheme x = theme("x", ORG_A);
      CustomTheme y = theme("y", ORG_A);
      y.getGroups().add("sales");
      when(manager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(x, y)));

      service.updateIdentityTheme("sales", "sales", ORG_A, "x", CustomTheme::getGroups, null);

      assertEquals(List.of("sales"), x.getGroups());
      assertTrue(y.getGroups().isEmpty(), "a group is assigned to at most one theme");
      assertTrue(x.getUsers().isEmpty() && x.getRoles().isEmpty(),
                 "only the group list is changed");

      service.updateIdentityTheme("sales", "sales", ORG_A, null, CustomTheme::getGroups, null);

      assertEquals(List.of("sales"), x.getGroups(), "a null theme keeps the assignment");

      service.updateIdentityTheme("sales", "sales", ORG_A, "", CustomTheme::getGroups, null);

      assertTrue(x.getGroups().isEmpty(), "an empty theme selects the default theme");
      assertTrue(y.getGroups().isEmpty());
   }

   // Bug #77352: a rename without a theme carries the role's assignment to the new name
   @Test
   void updateIdentityTheme_roleRenameWithoutTheme_keepsAssignment() {
      CustomTheme x = theme("x", ORG_A);
      x.getRoles().add("designer");
      when(manager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(x)));

      service.updateIdentityTheme("designer", "lead", ORG_A, null, CustomTheme::getRoles, null);

      assertEquals(List.of("lead"), x.getRoles());
   }

   // Bug #77352: an unknown theme, another organization's theme or a global theme (which only
   // refers to identities of the default organization) is ignored for an organization's group
   @Test
   void updateIdentityTheme_ineligibleTheme_ignored() {
      CustomTheme aTheme = theme("aTheme", ORG_A);
      aTheme.getGroups().add("sales");
      CustomTheme bTheme = theme("bTheme", ORG_B);
      CustomTheme globalTheme = theme("globalTheme", null);
      when(manager.getCustomThemes())
         .thenReturn(new HashSet<>(Set.of(aTheme, bTheme, globalTheme)));

      for(String ntheme : List.of("bTheme", "globalTheme", "unknown")) {
         service.updateIdentityTheme("sales", "sales", ORG_A, ntheme, CustomTheme::getGroups,
                                     null);
      }

      assertEquals(List.of("sales"), aTheme.getGroups(), "an ignored theme keeps the old theme");
      assertTrue(bTheme.getGroups().isEmpty());
      assertTrue(globalTheme.getGroups().isEmpty());
      verify(manager, never()).setCustomThemes(any());
   }

   // Bug #77352 / #77304: the reserved default theme id selects the default theme for a group
   // or role like an empty string, removing it from the previously selected theme
   @Test
   void updateIdentityTheme_defaultThemeId_clearsGroupAndRole() {
      CustomTheme x = theme("x", ORG_A);
      x.getGroups().add("sales");
      x.getRoles().add("designer");
      when(manager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(x)));

      service.updateIdentityTheme("sales", "sales", ORG_A, CustomTheme.DEFAULT_THEME_ID,
                                  CustomTheme::getGroups, null);
      service.updateIdentityTheme("designer", "designer", ORG_A, CustomTheme.DEFAULT_THEME_ID,
                                  CustomTheme::getRoles, null);

      assertTrue(x.getGroups().isEmpty());
      assertTrue(x.getRoles().isEmpty());
   }

   // review C-m1: without a theme or a rename there is nothing to change, so the themes are
   // neither locked nor read
   @Test
   void updateIdentityTheme_noThemeSameName_skipsThemesUpdate() {
      service.updateIdentityTheme("sales", "sales", ORG_A, null, CustomTheme::getGroups, null);
      service.updateUserTheme("bob", "bob", ORG_A, null);

      verify(manager, never()).updateCustomThemes(any());
   }

   // Bug #77352: a global theme can be assigned to a group of the default organization
   @Test
   void updateIdentityTheme_defaultOrgGroup_globalThemeAssigned() {
      CustomTheme globalTheme = theme("globalTheme", null);
      when(manager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(globalTheme)));

      service.updateIdentityTheme("sales", "sales", HOST_ORG, "globalTheme",
                                  CustomTheme::getGroups, null);

      assertEquals(List.of("sales"), globalTheme.getGroups());
   }

   // Bug #77352: a global role is assigned only among the current organization's themes, so an
   // edit in organization A leaves the role's theme in organization B unchanged
   @Test
   void updateIdentityTheme_globalRoleAssign_otherOrgThemeUntouched() {
      CustomTheme aTheme = theme("aTheme", ORG_A);
      CustomTheme bTheme = theme("bTheme", ORG_B);
      bTheme.getRoles().add("Designer");

      try(MockedStatic<OrganizationManager> ignored = mockOrg(ORG_A, false);
          MockedStatic<SUtil> ignored2 = mockMultiTenant(true))
      {
         when(manager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(aTheme, bTheme)));

         service.updateIdentityTheme("Designer", "Designer", null, "aTheme",
                                     CustomTheme::getRoles, null);
         assertEquals(List.of("Designer"), aTheme.getRoles());
         assertEquals(List.of("Designer"), bTheme.getRoles(), "org B's assignment must be kept");

         service.updateIdentityTheme("Designer", "Designer", null, "", CustomTheme::getRoles,
                                     null);
         assertTrue(aTheme.getRoles().isEmpty());
         assertEquals(List.of("Designer"), bTheme.getRoles(), "org B's assignment must be kept");
      }
   }

   // Bug #77352: organization A cannot assign a global role to organization B's theme
   @Test
   void updateIdentityTheme_globalRoleOtherOrgTheme_ignored() {
      CustomTheme aTheme = theme("aTheme", ORG_A);
      aTheme.getRoles().add("Designer");
      CustomTheme bTheme = theme("bTheme", ORG_B);

      try(MockedStatic<OrganizationManager> ignored = mockOrg(ORG_A, true);
          MockedStatic<SUtil> ignored2 = mockMultiTenant(true))
      {
         when(manager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(aTheme, bTheme)));

         service.updateIdentityTheme("Designer", "Designer", null, "bTheme",
                                     CustomTheme::getRoles, null);

         assertEquals(List.of("Designer"), aTheme.getRoles());
         assertTrue(bTheme.getRoles().isEmpty());
         verify(manager, never()).setCustomThemes(any());
      }
   }

   // Bug #77352: renaming a global role still renames it in every organization's themes, while
   // the selected theme is only assigned among the current organization's themes
   @Test
   void updateIdentityTheme_globalRoleRename_renamesInEveryOrgTheme() {
      CustomTheme aTheme = theme("aTheme", ORG_A);
      CustomTheme bTheme = theme("bTheme", ORG_B);
      bTheme.getRoles().add("Designer");
      CustomTheme globalTheme = theme("globalTheme", null);
      globalTheme.getRoles().add("Designer");

      try(MockedStatic<OrganizationManager> ignored = mockOrg(ORG_A, false);
          MockedStatic<SUtil> ignored2 = mockMultiTenant(true))
      {
         when(manager.getCustomThemes())
            .thenReturn(new HashSet<>(Set.of(aTheme, bTheme, globalTheme)));

         service.updateIdentityTheme("Designer", "Lead", null, "aTheme", CustomTheme::getRoles,
                                     null);

         assertEquals(List.of("Lead"), aTheme.getRoles());
         assertEquals(List.of("Lead"), bTheme.getRoles());
         assertEquals(List.of("Lead"), globalTheme.getRoles(),
                      "an organization admin must not strip a global role from a global theme");
      }
   }

   // Bug #77352: in multi-tenant mode a global theme applies to every organization's users of a
   // global role, so only a site admin can assign a global role to, or remove it from, one
   @Test
   void updateIdentityTheme_globalRoleGlobalTheme_onlySiteAdmin() {
      CustomTheme aTheme = theme("aTheme", ORG_A);
      CustomTheme globalTheme = theme("globalTheme", null);
      globalTheme.getRoles().add("Designer");

      try(MockedStatic<SUtil> ignored2 = mockMultiTenant(true)) {
         when(manager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(aTheme, globalTheme)));

         try(MockedStatic<OrganizationManager> ignored = mockOrg(ORG_A, false)) {
            service.updateIdentityTheme("Designer", "Designer", null, "aTheme",
                                        CustomTheme::getRoles, null);
            assertEquals(List.of("Designer"), aTheme.getRoles());
            assertEquals(List.of("Designer"), globalTheme.getRoles(),
                         "an organization admin must not change a global theme");

            aTheme.getRoles().clear();
            service.updateIdentityTheme("Designer", "Designer", null, "globalTheme",
                                        CustomTheme::getRoles, null);
            assertTrue(aTheme.getRoles().isEmpty(), "the global theme is ignored");
         }

         try(MockedStatic<OrganizationManager> ignored = mockOrg(ORG_A, true)) {
            service.updateIdentityTheme("Designer", "Designer", null, "aTheme",
                                        CustomTheme::getRoles, null);
            assertEquals(List.of("Designer"), aTheme.getRoles());
            assertTrue(globalTheme.getRoles().isEmpty(),
                       "a site admin's selection replaces the global theme");
         }
      }
   }

   // Bug #77352: in single-tenant mode every theme is global, so a global role (e.g. the
   // built-in Administrator role) can be assigned to any theme
   @Test
   void updateIdentityTheme_singleTenantGlobalRole_globalThemeAssigned() {
      CustomTheme x = theme("x", null);
      CustomTheme y = theme("y", null);
      y.getRoles().add("Administrator");

      try(MockedStatic<OrganizationManager> ignored = mockOrg(HOST_ORG, false);
          MockedStatic<SUtil> ignored2 = mockMultiTenant(false))
      {
         when(manager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(x, y)));

         service.updateIdentityTheme("Administrator", "Administrator", null, "x",
                                     CustomTheme::getRoles, null);

         assertEquals(List.of("Administrator"), x.getRoles());
         assertTrue(y.getRoles().isEmpty());
      }
   }

   // Bug #77352 review: a mixed-case organization ID must still match its own themes for a
   // global role, so the current organization is compared with its case preserved
   @Test
   void updateIdentityTheme_globalRoleMixedCaseOrg_currentOrgThemeAssigned() {
      CustomTheme mixed = theme("mixed", "OrgMixed");
      CustomTheme lower = theme("lower", "orglower");

      try(MockedStatic<SUtil> ignored2 = mockMultiTenant(true)) {
         when(manager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(mixed, lower)));

         try(MockedStatic<OrganizationManager> ignored = mockOrg("OrgMixed", false)) {
            service.updateIdentityTheme("Designer", "Designer", null, "mixed",
                                        CustomTheme::getRoles, null);
         }

         try(MockedStatic<OrganizationManager> ignored = mockOrg("orglower", false)) {
            service.updateIdentityTheme("Designer", "Designer", null, "lower",
                                        CustomTheme::getRoles, null);
         }

         assertEquals(List.of("Designer"), mixed.getRoles());
         assertEquals(List.of("Designer"), lower.getRoles(),
                      "a lower-case organization ID still matches");
      }
   }

   private static MockedStatic<OrganizationManager> mockOrg(String currentOrgID,
                                                            boolean siteAdmin)
   {
      OrganizationManager orgManager = mock(OrganizationManager.class, withSettings().lenient());
      MockedStatic<OrganizationManager> orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      // like OrganizationManager, the no-argument getter lower-cases the organization ID
      when(orgManager.getCurrentOrgID()).thenReturn(currentOrgID.toLowerCase());
      when(orgManager.getCurrentOrgID(nullable(Principal.class))).thenReturn(currentOrgID);
      when(orgManager.isSiteAdmin(nullable(Principal.class))).thenReturn(siteAdmin);
      return orgManagerStatic;
   }

   private static MockedStatic<SUtil> mockMultiTenant(boolean multiTenant) {
      MockedStatic<SUtil> sUtilStatic = mockStatic(SUtil.class);
      sUtilStatic.when(SUtil::isMultiTenant).thenReturn(multiTenant);
      return sUtilStatic;
   }

   private MockedStatic<OrganizationManager> mockThemesAndOrg(String currentOrgID,
                                                              boolean siteAdmin,
                                                              Principal principal)
   {
      // a theme with an empty organization ID is global, see IdentityService.getEligibleOrgTheme()
      when(manager.getCustomThemes()).thenReturn(new HashSet<>(Set.of(
         theme("ta", ORG_A), theme("tb", ORG_B), theme("g", null), theme("e", ""))));
      OrganizationManager orgManager = mock(OrganizationManager.class);
      MockedStatic<OrganizationManager> orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      when(orgManager.getCurrentOrgID()).thenReturn(currentOrgID);
      when(orgManager.isSiteAdmin(principal)).thenReturn(siteAdmin);
      return orgManagerStatic;
   }

   private static Set<String> themeIds(IdentityThemeList list) {
      return list.themes().stream().map(IdentityTheme::id).collect(Collectors.toSet());
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
