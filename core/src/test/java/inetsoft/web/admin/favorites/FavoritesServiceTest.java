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
package inetsoft.web.admin.favorites;

import inetsoft.sree.security.IdentityID;
import inetsoft.storage.KeyValueStorage;
import inetsoft.storage.KeyValueStorageManager;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Covers {@link FavoritesService}, which owns the emFavorites key-value store and is the
 * single point of access to EM favorites.
 */
@Tag("core")
class FavoritesServiceTest {
   @SuppressWarnings("unchecked")
   private final KeyValueStorage<FavoriteList> storage = mock(KeyValueStorage.class);
   private FavoritesService service;

   @BeforeEach
   void setUp() {
      KeyValueStorageManager manager = mock(KeyValueStorageManager.class);
      doReturn(storage).when(manager).getStorage("emFavorites");
      lenient().when(storage.put(anyString(), any())).thenReturn(completed(null));
      lenient().when(storage.remove(anyString())).thenReturn(completed(null));
      // removeAll returns Future<?> (wildcard), so doReturn avoids the generic-capture mismatch
      lenient().doReturn(completed(null)).when(storage).removeAll(anySet());

      service = new FavoritesService(manager);
      service.initStorage();
   }

   @Test
   void getFavorites_missingEntry_returnsEmptyList() {
      when(storage.get("alice~;~org1")).thenReturn(null);

      FavoriteList list = service.getFavorites("alice~;~org1");

      assertNotNull(list, "must never return null");
      assertTrue(list.getFavorites().isEmpty(), "missing entry should yield an empty list");
   }

   @Test
   void getFavorites_existingEntry_returnsStoredList() {
      FavoriteList stored = listOf(favorite("Users", "/settings/security/users"));
      when(storage.get("alice~;~org1")).thenReturn(stored);

      assertSame(stored, service.getFavorites("alice~;~org1"));
   }

   @Test
   void setFavorites_nonEmpty_putsEntry() {
      FavoriteList list = listOf(favorite("Users", "/settings/security/users"));

      service.setFavorites("alice~;~org1", list);

      verify(storage).put("alice~;~org1", list);
      verify(storage, never()).remove(anyString());
   }

   @Test
   void setFavorites_empty_removesEntry() {
      service.setFavorites("alice~;~org1", listOf());

      verify(storage).remove("alice~;~org1");
      verify(storage, never()).put(anyString(), any());
   }

   @Test
   void setFavorites_storageFailure_throws() throws Exception {
      when(storage.put(anyString(), any())).thenReturn(failing());

      assertThrows(RuntimeException.class,
                   () -> service.setFavorites("alice~;~org1",
                                              listOf(favorite("Users", "/settings/security/users"))));
   }

   @Test
   void moveFavorites_presentEntry_movesToNewKey() {
      FavoriteList list = listOf(favorite("Users", "/settings/security/users"));
      when(storage.get("alice~;~org1")).thenReturn(list);

      service.moveFavorites("alice~;~org1", "alice~;~org2");

      verify(storage).put("alice~;~org2", list);
      verify(storage).remove("alice~;~org1");
   }

   @Test
   void moveFavorites_noEntry_doesNothing() {
      when(storage.get("alice~;~org1")).thenReturn(null);

      service.moveFavorites("alice~;~org1", "alice~;~org2");

      verify(storage, never()).put(anyString(), any());
      verify(storage, never()).remove(anyString());
   }

   @Test
   void moveFavorites_storageFailure_doesNotThrow() throws Exception {
      when(storage.get("alice~;~org1"))
         .thenReturn(listOf(favorite("Users", "/settings/security/users")));
      when(storage.put(anyString(), any())).thenReturn(failing());

      assertDoesNotThrow(() -> service.moveFavorites("alice~;~org1", "alice~;~org2"));
   }

   @Test
   void removeFavorites_identities_removesEachKey() {
      IdentityID alice = new IdentityID("alice", "org1");
      IdentityID bob = new IdentityID("bob", "org2");

      service.removeFavorites(List.of(alice, bob));

      verify(storage).remove(alice.convertToKey());
      verify(storage).remove(bob.convertToKey());
   }

   @Test
   void removeFavorites_identities_emptyOrNull_noStorageAccess() {
      service.removeFavorites((Collection<IdentityID>) null);
      service.removeFavorites(Collections.emptyList());

      verify(storage, never()).remove(anyString());
   }

   @Test
   void removeFavorites_identities_storageFailure_doesNotThrow() throws Exception {
      when(storage.remove(anyString())).thenReturn(failing());

      assertDoesNotThrow(() -> service.removeFavorites(List.of(new IdentityID("alice", "org1"))));
   }

   @Test
   void removeFavorites_org_removesOnlyMatchingOrgKeys() {
      IdentityID alice = new IdentityID("alice", "org1");
      IdentityID bob = new IdentityID("bob", "org1");
      IdentityID carol = new IdentityID("carol", "org2");
      when(storage.keys()).thenReturn(
         Stream.of(alice.convertToKey(), bob.convertToKey(), carol.convertToKey()));

      service.removeFavorites("org1");

      verify(storage).removeAll(Set.of(alice.convertToKey(), bob.convertToKey()));
   }

   @Test
   void removeFavorites_org_noMatchingKeys_noRemoval() {
      when(storage.keys()).thenReturn(Stream.of(new IdentityID("carol", "org2").convertToKey()));

      service.removeFavorites("org1");

      verify(storage, never()).removeAll(anySet());
   }

   @Test
   void removeFavorites_org_storageFailure_doesNotThrow() throws Exception {
      when(storage.keys()).thenReturn(Stream.of(new IdentityID("alice", "org1").convertToKey()));
      doReturn(failing()).when(storage).removeAll(anySet());

      assertDoesNotThrow(() -> service.removeFavorites("org1"));
   }

   private static CompletableFuture<FavoriteList> completed(FavoriteList value) {
      return CompletableFuture.completedFuture(value);
   }

   private static Future<FavoriteList> failing() {
      // A future whose get(...) throws ExecutionException, mirroring a failed storage op.
      // Built as a real completed future (not a Mockito mock) so it can be passed inline to
      // when(...).thenReturn(...) without tripping Mockito's unfinished-stubbing detection.
      CompletableFuture<FavoriteList> future = new CompletableFuture<>();
      future.completeExceptionally(new RuntimeException("boom"));
      return future;
   }

   private static FavoriteList listOf(Favorite... favorites) {
      FavoriteList list = new FavoriteList();
      list.setFavorites(new ArrayList<>(Arrays.asList(favorites)));
      return list;
   }

   private static Favorite favorite(String label, String path) {
      Favorite favorite = new Favorite();
      favorite.setLabel(label);
      favorite.setPath(path);
      return favorite;
   }
}
