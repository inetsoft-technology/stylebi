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

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.portal.CustomTheme;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.security.AuthenticationProvider;
import inetsoft.sree.security.AuthorizationChain;
import inetsoft.sree.security.EditableAuthenticationProvider;
import inetsoft.sree.security.FSOrganization;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.IdentityInfo;
import inetsoft.sree.security.Organization;
import inetsoft.sree.security.OrganizationContextHolder;
import inetsoft.sree.security.Permission;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.SecurityProvider;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.util.DataSpace;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.web.admin.security.user.EditOrganizationPaneModel;
import inetsoft.uql.util.Identity;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.util.IndexedStorage;
import inetsoft.web.admin.favorites.FavoritesService;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Covers the orphaned-favorites cleanup that runs during user deletion: removeUserFavorites
 * strips deleted users from shared-asset favorites lists (IndexedStorage) and delegates the
 * EM admin-panel favorites cleanup to {@link FavoritesService}.
 *
 * The service is created without invoking its constructor and only the dependencies these
 * methods touch are injected, so the tests stay decoupled from the rest of the service's
 * wiring.
 */
@Tag("core")
class IdentityServiceTest {
   private IndexedStorage indexedStorage;
   private FavoritesService favoritesService;
   private IdentityService service;

   @BeforeEach
   void setUp() {
      indexedStorage = mock(IndexedStorage.class);
      favoritesService = mock(FavoritesService.class);

      service = mock(IdentityService.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
      ReflectionTestUtils.setField(service, "indexedStorage", indexedStorage);
      ReflectionTestUtils.setField(service, "favoritesService", favoritesService);
      // LOG is a final instance field set by the constructor, which the mock bypasses
      ReflectionTestUtils.setField(service, "LOG",
                                   org.slf4j.LoggerFactory.getLogger(IdentityService.class));
   }

   @Test
   void removeUserFavorites_emptyInput_noStorageAccess() throws Exception {
      invokeRemoveUserFavorites(Collections.emptyList());

      verifyNoInteractions(indexedStorage);
      verifyNoInteractions(favoritesService);
   }

   @Test
   void removeUserFavorites_removesUserKeyFromMatchingFolderEntry() throws Exception {
      IdentityID alice = new IdentityID("alice", "org1");
      IdentityID bob = new IdentityID("bob", "org1");

      AssetEntry mine = entryWithFavorites(alice);
      AssetEntry theirs = entryWithFavorites(bob);
      AssetFolder folder = new AssetFolder();
      folder.addEntry(mine);
      folder.addEntry(theirs);

      when(indexedStorage.getKeys(any(), eq("org1"))).thenReturn(Set.of("folderKey"));
      when(indexedStorage.getXMLSerializable(eq("folderKey"), isNull(), eq("org1")))
         .thenReturn(folder);

      invokeRemoveUserFavorites(List.of(alice));

      assertFalse(mine.getFavoritesUsers().contains(alice.convertToKey()),
                  "alice's favorite should be removed");
      assertTrue(theirs.getFavoritesUsers().contains(bob.convertToKey()),
                 "bob's favorite must be untouched");
      verify(indexedStorage).putXMLSerializable("folderKey", folder);
   }

   @Test
   void removeUserFavorites_writeScopedToTargetOrg() throws Exception {
      IdentityID alice = new IdentityID("alice", "org1");
      AssetFolder folder = new AssetFolder();
      folder.addEntry(entryWithFavorites(alice));

      when(indexedStorage.getKeys(any(), eq("org1"))).thenReturn(Set.of("folderKey"));
      when(indexedStorage.getXMLSerializable(eq("folderKey"), isNull(), eq("org1")))
         .thenReturn(folder);

      String[] orgAtWrite = new String[1];
      doAnswer(inv -> {
         orgAtWrite[0] = OrganizationContextHolder.getCurrentOrgId();
         return null;
      }).when(indexedStorage).putXMLSerializable(eq("folderKey"), any());

      // simulate a caller whose thread context is a different org
      OrganizationContextHolder.setCurrentOrgId("callerOrg");

      try {
         invokeRemoveUserFavorites(List.of(alice));
      }
      finally {
         OrganizationContextHolder.setCurrentOrgId(null);
      }

      assertEquals("org1", orgAtWrite[0],
                   "write must run in the target user's org, not the caller's");
   }

   @Test
   void removeUserFavorites_unmodifiedFolder_notRewritten() throws Exception {
      IdentityID alice = new IdentityID("alice", "org1");
      IdentityID bob = new IdentityID("bob", "org1");

      AssetFolder folder = new AssetFolder();
      folder.addEntry(entryWithFavorites(bob));

      when(indexedStorage.getKeys(any(), eq("org1"))).thenReturn(Set.of("folderKey"));
      when(indexedStorage.getXMLSerializable(eq("folderKey"), isNull(), eq("org1")))
         .thenReturn(folder);

      invokeRemoveUserFavorites(List.of(alice));

      verify(indexedStorage, never()).putXMLSerializable(anyString(), any());
   }

   @Test
   void removeUserFavorites_delegatesEMFavoritesRemovalToService() throws Exception {
      IdentityID alice = new IdentityID("alice", "org1");
      IdentityID bob = new IdentityID("bob", "org2");
      List<IdentityID> ids = List.of(alice, bob);

      invokeRemoveUserFavorites(ids);

      // the EM favorites cleanup is delegated wholesale to the favorites service
      verify(favoritesService).removeFavorites(ids);
   }

   @Test
   void removeUserFavorites_nullOrgUser_skipsAssetScan() throws Exception {
      IdentityID noOrg = new IdentityID("legacy", null);

      invokeRemoveUserFavorites(List.of(noOrg));

      // a null-org user has no org-scoped folders to scan
      verify(indexedStorage, never()).getKeys(any(), any());
      // but its EM favorites are still handed to the service for cleanup
      verify(favoritesService).removeFavorites(List.of(noOrg));
   }

   @Test
   void copyUserFavorites_replaceFalse_copiesFavoritesLeavingSourceIntact() {
      IdentityID from = new IdentityID("alice", "org1");
      IdentityID to = new IdentityID("alice", "org2");

      service.copyUserFavorites(from, to, false);

      verify(favoritesService).copyFavorites(from.convertToKey(), to.convertToKey());
      verify(favoritesService, never()).moveFavorites(anyString(), anyString());
   }

   @Test
   void copyUserFavorites_replaceTrue_movesFavorites() {
      IdentityID from = new IdentityID("alice", "org1");
      IdentityID to = new IdentityID("alice", "org2");

      service.copyUserFavorites(from, to, true);

      verify(favoritesService).moveFavorites(from.convertToKey(), to.convertToKey());
      verify(favoritesService, never()).copyFavorites(anyString(), anyString());
   }

   @Test
   void getIdentityInfo_nullIdentity_returnsEmptyInfo() {
      IdentityID missing = new IdentityID("ghost", "org1");
      AuthenticationProvider provider = mock(AuthenticationProvider.class);
      // simulate a transient/stale lookup returning no identity
      when(provider.getUser(missing)).thenReturn(null);

      IdentityInfo info = service.getIdentityInfo(missing, Identity.USER, provider);

      assertNotNull(info, "a null identity must not produce a null IdentityInfo");
      assertNull(info.getIdentityID(), "empty info should have no identity id");
      assertFalse(info.isActive(), "empty info should be inactive");
      assertTrue(info.getMembers().isEmpty(), "empty info should have no members");
   }

   private static AssetEntry entryWithFavorites(IdentityID user) {
      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "/" + user.getName(), user);
      entry.addFavoritesUser(user.convertToKey());
      return entry;
   }

   private void invokeRemoveUserFavorites(Collection<IdentityID> ids) throws Exception {
      Method m = IdentityService.class.getDeclaredMethod("removeUserFavorites", Collection.class);
      m.setAccessible(true);
      m.invoke(service, ids);
   }

   // Bug 76448: deleting one identity (USER/GROUP/ROLE) was wiping every other identity's
   // grant for the same action, because the delete-case guard compared a plain IdentityID
   // against a Set<Permission.PermissionIdentity> -- a cross-type comparison that always
   // returns false, so the "keep everyone but the deleted identity" branch never ran and an
   // empty grant set was installed instead.
   @Test
   void updateIdentityPermission_deleteRole_keepsOtherRoleGrant() throws Exception {
      IdentityID roleA = new IdentityID("RoleA", "org1");
      IdentityID roleB = new IdentityID("RoleB", "org1");

      Permission permission = new Permission();
      permission.setRoleGrants(ResourceAction.READ, new HashSet<>(Set.of(
         new Permission.PermissionIdentity(roleA), new Permission.PermissionIdentity(roleB))));

      invokeUpdateIdentityPermission(Identity.ROLE, null, roleA, permission, ResourceAction.READ);

      Set<Permission.PermissionIdentity> remaining = permission.getAllRoleGrants(ResourceAction.READ);
      assertFalse(remaining.contains(new Permission.PermissionIdentity(roleA)),
                  "deleted role's own grant should be removed");
      assertTrue(remaining.contains(new Permission.PermissionIdentity(roleB)),
                 "co-granted role's grant must survive the delete");
   }

   @Test
   void updateIdentityPermission_deleteUser_keepsOtherUserGrant() throws Exception {
      IdentityID alice = new IdentityID("alice", "org1");
      IdentityID bob = new IdentityID("bob", "org1");

      Permission permission = new Permission();
      permission.setUserGrants(ResourceAction.READ, new HashSet<>(Set.of(
         new Permission.PermissionIdentity(alice), new Permission.PermissionIdentity(bob))));

      invokeUpdateIdentityPermission(Identity.USER, null, alice, permission, ResourceAction.READ);

      Set<Permission.PermissionIdentity> remaining = permission.getAllUserGrants(ResourceAction.READ);
      assertFalse(remaining.contains(new Permission.PermissionIdentity(alice)),
                  "deleted user's own grant should be removed");
      assertTrue(remaining.contains(new Permission.PermissionIdentity(bob)),
                 "co-granted user's grant must survive the delete");
   }

   @Test
   void updateIdentityPermission_deleteGroup_keepsOtherGroupGrant() throws Exception {
      IdentityID groupA = new IdentityID("GroupA", "org1");
      IdentityID groupB = new IdentityID("GroupB", "org1");

      Permission permission = new Permission();
      permission.setGroupGrants(ResourceAction.READ, new HashSet<>(Set.of(
         new Permission.PermissionIdentity(groupA), new Permission.PermissionIdentity(groupB))));

      invokeUpdateIdentityPermission(Identity.GROUP, null, groupA, permission, ResourceAction.READ);

      Set<Permission.PermissionIdentity> remaining = permission.getAllGroupGrants(ResourceAction.READ);
      assertFalse(remaining.contains(new Permission.PermissionIdentity(groupA)),
                  "deleted group's own grant should be removed");
      assertTrue(remaining.contains(new Permission.PermissionIdentity(groupB)),
                 "co-granted group's grant must survive the delete");
   }

   @Test
   void permissionIdentity_hashCode_matchesEqualsContract() {
      Permission.PermissionIdentity a = new Permission.PermissionIdentity("RoleA", "org1");
      Permission.PermissionIdentity b = new Permission.PermissionIdentity("RoleA", "org1");

      assertEquals(a, b);
      assertEquals(a.hashCode(), b.hashCode());
      assertTrue(new HashSet<>(Set.of(a)).contains(b),
                 "field-equal PermissionIdentity instances must collide in a HashSet");
   }

   private void invokeUpdateIdentityPermission(int type, IdentityID newIdentityID, IdentityID oldIdentityID,
                                                Permission permission, ResourceAction action) throws Exception {
      Method m = IdentityService.class.getDeclaredMethod("updateIdentityPermission", int.class,
         IdentityID.class, IdentityID.class, Organization.class, String.class, Permission.class,
         ResourceAction.class);
      m.setAccessible(true);
      m.invoke(service, type, newIdentityID, oldIdentityID, null, null, permission, action);
   }

   // Bug #76671 (06-fix-r2.md), finding 3: SecurityApiService.updateOrganization's round-1 fix
   // added its own, separately name-keyed CustomTheme.getOrganizations() write on top of this
   // method's already-correct id-keyed one, producing a duplicate entry for any organization
   // whose id differs from its display name. That extra write was removed in round 2, leaving
   // this method as the sole writer -- this test guards it directly.
   @Test
   void updateCustomThemeOrganization_idDiffersFromName_writesSingleIdKeyedEntryAndSetsPointer()
      throws Exception
   {
      inetsoft.sree.portal.CustomThemesManager customThemesManager =
         mock(inetsoft.sree.portal.CustomThemesManager.class, withSettings().lenient());
      inetsoft.sree.portal.CustomTheme theme = new inetsoft.sree.portal.CustomTheme();
      theme.setId("theme-1");
      theme.setOrganizations(new ArrayList<>());
      when(customThemesManager.getCustomThemes())
         .thenReturn(new HashSet<>(Set.of(theme)));
      ReflectionTestUtils.setField(service, "customThemesManager", customThemesManager);

      invokeUpdateCustomThemeOrganization(null, "theme-1", "org-xyz", "org-xyz");

      assertEquals(List.of("org-xyz"), theme.getOrganizations(),
         "organization theme membership must be written keyed by org id, exactly once -- not "
         + "duplicated under the organization's display name");
      verify(customThemesManager).setOrgSelectedTheme("theme-1", "org-xyz");
   }

   private void invokeUpdateCustomThemeOrganization(String oldThemeId, String themeID,
                                                      String oldOrgID, String newOrgID)
      throws Exception
   {
      Method m = IdentityService.class.getDeclaredMethod("updateCustomThemeOrganization",
         String.class, String.class, String.class, String.class);
      m.setAccessible(true);
      m.invoke(service, oldThemeId, themeID, oldOrgID, newOrgID);
   }

   // ------------------------------------------------------------------------------------------
   // setOrganizationInfo: an id-preserving rename must not short-circuit the sync tail.
   //
   // The method used to return early (right after writing the new display name) whenever the
   // organization's name changed but its id did not, skipping updateOrganizationMembers, the
   // locale reverse-lookup and updateCustomThemeOrganization. The EM posts name, theme, locale
   // and members in a single request, so "rename it and re-theme it" silently dropped everything
   // but the rename.
   // ------------------------------------------------------------------------------------------

   private static final String ORG_ID = "org-xyz";
   private static final String OLD_ORG_NAME = "Old Name";
   private static final String NEW_ORG_NAME = "New Name";

   private final Properties localeProperties = new Properties();
   private EditableAuthenticationProvider eprovider;
   private CustomThemesManager themesManager;
   private DashboardRegistryManager dashboardRegistryManager;
   private RepletRegistryManager repletRegistryManager;
   private DataSpace dataSpace;

   @Test
   void setOrganizationInfo_renameWithThemeChange_appliesThemeAndPointer() throws Exception {
      CustomTheme theme = new CustomTheme();
      theme.setId("theme-1");
      theme.setOrganizations(new ArrayList<>());
      FSOrganization stored = setUpRenameFixture(null, theme);

      invokeSetOrganizationInfo(stored, renameModel().theme("theme-1").build());

      assertEquals(NEW_ORG_NAME, stored.getName(), "the rename itself must still be applied");
      assertEquals("theme-1", stored.getTheme(),
                   "a theme change bundled with a rename must not be dropped");
      assertEquals(List.of(ORG_ID), theme.getOrganizations(),
                   "the theme's organization membership must be written, keyed by org id");
      verify(themesManager).setOrgSelectedTheme("theme-1", ORG_ID);
      verify(eprovider).setOrganization(ORG_ID, stored);
   }

   @Test
   void setOrganizationInfo_renameWithLocaleChange_appliesLocale() throws Exception {
      localeProperties.setProperty("en_US", "English(America)");
      FSOrganization stored = setUpRenameFixture(null);

      invokeSetOrganizationInfo(stored, renameModel().locale("English(America)").build());

      assertEquals(NEW_ORG_NAME, stored.getName(), "the rename itself must still be applied");
      assertEquals("en_US", stored.getLocale(),
                   "a locale change bundled with a rename must not be dropped; the model carries "
                   + "the label and the stored value is the code it reverse-looks-up to");
   }

   @Test
   void setOrganizationInfo_renameOnly_stillRenamesAndSkipsIdMigration() throws Exception {
      FSOrganization stored = setUpRenameFixture("theme-1");

      invokeSetOrganizationInfo(stored, renameModel().theme("theme-1").build());

      assertEquals(NEW_ORG_NAME, stored.getName(), "the rename itself must still be applied");
      assertEquals("theme-1", stored.getTheme(), "an unchanged theme must survive the rename");
      verify(eprovider).setOrganization(ORG_ID, stored);
      // none of these are org-id migration: the id did not change, so they must stay untouched
      verifyNoInteractions(dashboardRegistryManager);
      verifyNoInteractions(repletRegistryManager);
      verify(dataSpace, never()).rename(anyString(), anyString());
   }

   private EditOrganizationPaneModel.Builder renameModel() {
      return EditOrganizationPaneModel.builder()
         .name(NEW_ORG_NAME)
         .oldName(OLD_ORG_NAME)
         .id(ORG_ID)
         .members(Collections.emptyList());
   }

   /**
    * Wires only the collaborators the same-id organization edit path touches and returns the
    * stored organization the provider hands back (the object the method mutates and saves).
    */
   private FSOrganization setUpRenameFixture(String storedTheme, CustomTheme... themes) {
      SecurityProvider securityProvider = mock(SecurityProvider.class, withSettings().lenient());
      when(securityProvider.getAuthorizationProvider()).thenReturn(mock(AuthorizationChain.class));

      themesManager = mock(CustomThemesManager.class, withSettings().lenient());
      when(themesManager.getCustomThemes()).thenReturn(new HashSet<>(Arrays.asList(themes)));

      dashboardRegistryManager = mock(DashboardRegistryManager.class);
      repletRegistryManager = mock(RepletRegistryManager.class);
      dataSpace = mock(DataSpace.class, withSettings().lenient());
      when(dataSpace.getOrgScopedPaths(any())).thenReturn(new String[0]);

      ReflectionTestUtils.setField(service, "securityProvider", securityProvider);
      ReflectionTestUtils.setField(service, "customThemesManager", themesManager);
      ReflectionTestUtils.setField(service, "dashboardRegistryManager", dashboardRegistryManager);
      ReflectionTestUtils.setField(service, "repletRegistryManager", repletRegistryManager);
      ReflectionTestUtils.setField(service, "dataSpace", dataSpace);

      FSOrganization stored = new FSOrganization(ORG_ID);
      stored.setName(OLD_ORG_NAME);
      stored.setTheme(storedTheme);
      stored.setMembers(new String[0]);

      eprovider = mock(EditableAuthenticationProvider.class, withSettings().lenient());
      when(eprovider.getUsers()).thenReturn(new IdentityID[0]);
      when(eprovider.getGroups()).thenReturn(new IdentityID[0]);
      when(eprovider.getRoles()).thenReturn(new IdentityID[0]);
      when(eprovider.getOrgIdFromName(OLD_ORG_NAME)).thenReturn(ORG_ID);
      when(eprovider.getOrganization(ORG_ID)).thenReturn(stored);

      return stored;
   }

   private void invokeSetOrganizationInfo(FSOrganization oldOrg, EditOrganizationPaneModel model)
      throws Exception
   {
      Method m = IdentityService.class.getDeclaredMethod(
         "setOrganizationInfo", FSOrganization.class, EditOrganizationPaneModel.class,
         EditableAuthenticationProvider.class, Principal.class);
      m.setAccessible(true);

      // the locale reverse-lookup reads the locale list off the DataSpace, which needs a running
      // Spring context; everything else on SUtil keeps calling the real method
      try(MockedStatic<SUtil> sutil = mockStatic(SUtil.class, CALLS_REAL_METHODS)) {
         sutil.when(SUtil::loadLocaleProperties).thenReturn(localeProperties);
         m.invoke(service, oldOrg, model, eprovider, null);
      }
   }
}
