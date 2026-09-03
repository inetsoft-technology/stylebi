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

import inetsoft.sree.security.AuthenticationProvider;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.IdentityInfo;
import inetsoft.sree.security.Organization;
import inetsoft.sree.security.OrganizationContextHolder;
import inetsoft.sree.security.Permission;
import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.util.Identity;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.util.IndexedStorage;
import inetsoft.web.admin.favorites.FavoritesService;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
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
}
