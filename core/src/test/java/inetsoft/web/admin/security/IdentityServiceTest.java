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
import inetsoft.sree.security.OrganizationContextHolder;
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
}
