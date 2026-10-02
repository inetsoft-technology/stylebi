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
import inetsoft.sree.security.Organization;
import inetsoft.storage.KeyValueStorage;
import inetsoft.storage.KeyValueStorageManager;
import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77244: uses the real {@link KeyValueStorageManager} (backed by MockCluster) and forces a
 * genuine Caffeine eviction of the emFavorites store by fetching more than 50 unrelated store
 * ids, then confirms {@link FavoritesService#removeFavorites(String)} still removes every entry
 * of the deleted organization instead of silently enumerating the closed instance as empty.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class FavoritesServiceStorageEvictionTest {
   @BeforeEach
   void setUp() {
      manager = KeyValueStorageManager.getInstance();
      service = new FavoritesService(manager);
      service.initStorage();
   }

   @AfterEach
   void tearDown() {
      service.removeFavorites(List.of(alice, bob, carol, admin));
   }

   @Test
   void removeFavoritesOrgAfterRealCaffeineEviction() throws Exception {
      service.setFavorites(alice.convertToKey(), listOf(favorite("Users", "/settings/users")));
      service.setFavorites(bob.convertToKey(), listOf(favorite("Roles", "/settings/roles")));
      service.setFavorites(carol.convertToKey(), listOf(favorite("Users", "/settings/users")));
      service.setFavorites(admin.convertToKey(), listOf(favorite("Users", "/settings/users")));

      KeyValueStorage<FavoriteList> before = getFavoritesField();
      assertNotNull(before);
      assertFalse(before.isClosed());

      // push more than MAX_SIZE(50) unrelated ids through the manager so Caffeine evicts the
      // idle emFavorites entry and its removal listener closes it (asynchronously)
      for(int i = 0; i < 60; i++) {
         manager.<Serializable>getStorage("test77244.evict." + i);
      }

      waitFor(before::isClosed);
      assertTrue(before.keys().findAny().isEmpty(),
                 "premise: a closed store enumerates as empty");

      service.removeFavorites(ORG);

      assertTrue(service.getFavorites(alice.convertToKey()).getFavorites().isEmpty(),
                 "the deleted organization's favorites were left behind");
      assertTrue(service.getFavorites(bob.convertToKey()).getFavorites().isEmpty(),
                 "the deleted organization's favorites were left behind");
      assertEquals(1, service.getFavorites(carol.convertToKey()).getFavorites().size(),
                   "another organization's favorites must be kept");
      assertEquals(1, service.getFavorites(admin.convertToKey()).getFavorites().size(),
                   "the global organization's favorites must be kept");

      KeyValueStorage<FavoriteList> after = getFavoritesField();
      assertNotSame(before, after, "the service kept using the evicted, closed storage");
      assertFalse(after.isClosed());
      Set<String> keys = after.keys().collect(Collectors.toSet());
      assertFalse(keys.contains(alice.convertToKey()));
      assertFalse(keys.contains(bob.convertToKey()));
   }

   @SuppressWarnings("unchecked")
   private KeyValueStorage<FavoriteList> getFavoritesField() throws Exception {
      Field field = FavoritesService.class.getDeclaredField("favorites");
      field.setAccessible(true);
      return (KeyValueStorage<FavoriteList>) field.get(service);
   }

   private static void waitFor(BooleanSupplier condition) throws InterruptedException {
      long end = System.currentTimeMillis() + 10000L;

      while(!condition.getAsBoolean()) {
         if(System.currentTimeMillis() > end) {
            fail("Timed out waiting for the condition");
         }

         Thread.sleep(50L);
      }
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

   private static final String ORG = "test77244orgX";
   private final IdentityID alice = new IdentityID("alice", ORG);
   private final IdentityID bob = new IdentityID("bob", ORG);
   private final IdentityID carol = new IdentityID("carol", "test77244orgY");
   private final IdentityID admin =
      new IdentityID("test77244admin", Organization.getDefaultOrganizationID());
   private KeyValueStorageManager manager;
   private FavoritesService service;
}
