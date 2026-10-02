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
import inetsoft.util.Tool;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Owns the {@code emFavorites} key-value store and encapsulates all access to it, so
 * callers (e.g. the favorites REST controller, identity/organization management) do not
 * depend on how EM favorites are stored or keyed.
 */
@Service
public class FavoritesService {
   public FavoritesService(KeyValueStorageManager keyValueStorageManager) {
      this.keyValueStorageManager = keyValueStorageManager;
   }

   @PostConstruct
   public synchronized void initStorage() {
      favorites = keyValueStorageManager.getStorage("emFavorites");
   }

   @PreDestroy
   public synchronized void closeStorage() throws Exception {
      if(favorites != null) {
         favorites.close();
         favorites = null;
      }
   }

   /**
    * Gets the emFavorites storage, re-fetching it if the cached reference is null or has been
    * closed. The shared emFavorites KeyValueStorage can be LRU-evicted and closed by
    * KeyValueStorageManager once more than 50 stores are open at once (easily reached in
    * multi-tenant setups). A closed store's keys()/stream() return empty while get()/put()/
    * remove() still work against the underlying shared map, so holding on to a closed instance
    * makes {@link #removeFavorites(String)} silently remove nothing (Bug #77244).
    */
   private synchronized KeyValueStorage<FavoriteList> getStorage() {
      if(favorites == null || favorites.isClosed()) {
         favorites = keyValueStorageManager.getStorage("emFavorites");
      }

      return favorites;
   }

   /**
    * Gets the EM favorites for the given identity.
    *
    * @param identityKey the identity key (see {@link IdentityID#convertToKey()}).
    *
    * @return the favorites, never {@code null}.
    */
   public FavoriteList getFavorites(String identityKey) {
      FavoriteList list = getStorage().get(identityKey);

      if(list == null) {
         list = new FavoriteList();
         list.setFavorites(Collections.emptyList());
      }

      return list;
   }

   /**
    * Stores the EM favorites for the given identity. An empty list removes the entry.
    *
    * @param identityKey   the identity key.
    * @param userFavorites the favorites to store.
    */
   public void setFavorites(String identityKey, FavoriteList userFavorites) {
      KeyValueStorage<FavoriteList> storage = getStorage();

      try {
         if(userFavorites.getFavorites().isEmpty()) {
            storage.remove(identityKey).get(10L, TimeUnit.SECONDS);
         }
         else {
            storage.put(identityKey, userFavorites).get(10L, TimeUnit.SECONDS);
         }
      }
      catch(InterruptedException | ExecutionException | TimeoutException e) {
         throw new RuntimeException(e);
      }
   }

   /**
    * Moves the EM favorites entry from one identity key to another, e.g. when an identity
    * is renamed or moved to another organization. Does nothing if there is no entry for
    * {@code fromKey}.
    *
    * @param fromKey the source identity key.
    * @param toKey   the target identity key.
    */
   public void moveFavorites(String fromKey, String toKey) {
      KeyValueStorage<FavoriteList> storage = getStorage();
      FavoriteList list = storage.get(fromKey);

      if(list != null) {
         try {
            storage.put(toKey, list).get(10L, TimeUnit.SECONDS);
            storage.remove(fromKey).get(10L, TimeUnit.SECONDS);
         }
         catch(InterruptedException | ExecutionException | TimeoutException e) {
            LOG.error("Failed to move favorites from {} to {}", fromKey, toKey, e);
         }
      }
   }

   /**
    * Copies the EM favorites entry from one identity key to another, leaving the source
    * entry intact, e.g. when an organization is cloned and its members are copied into the
    * new organization. Does nothing if there is no entry for {@code fromKey}.
    *
    * @param fromKey the source identity key.
    * @param toKey   the target identity key.
    */
   public void copyFavorites(String fromKey, String toKey) {
      KeyValueStorage<FavoriteList> storage = getStorage();
      FavoriteList list = storage.get(fromKey);

      if(list != null) {
         try {
            storage.put(toKey, list).get(10L, TimeUnit.SECONDS);
         }
         catch(InterruptedException | ExecutionException | TimeoutException e) {
            LOG.error("Failed to copy favorites from {} to {}", fromKey, toKey, e);
         }
      }
   }

   /**
    * Removes the EM favorites entries for the given identities, so they are not left
    * orphaned after the identities are deleted.
    *
    * @param identities the identities whose favorites should be removed.
    */
   public void removeFavorites(Collection<IdentityID> identities) {
      if(identities == null || identities.isEmpty()) {
         return;
      }

      KeyValueStorage<FavoriteList> storage = getStorage();

      for(IdentityID id : identities) {
         if(id != null) {
            try {
               storage.remove(id.convertToKey()).get(10L, TimeUnit.SECONDS);
            }
            catch(InterruptedException e) {
               Thread.currentThread().interrupt();
               LOG.warn("Interrupted while removing EM favorites for deleted user {}", id, e);
            }
            catch(Exception e) {
               LOG.warn("Failed to remove EM favorites for deleted user {}", id, e);
            }
         }
      }
   }

   /**
    * Removes every EM favorites entry belonging to the given organization, so the members'
    * favorites are not left orphaned after the organization is deleted.
    *
    * @param orgID the id of the organization being removed.
    */
   public void removeFavorites(String orgID) {
      KeyValueStorage<FavoriteList> storage = getStorage();
      Set<String> keys = getOrgKeys(storage, orgID);

      // the storage may have been evicted and closed after getStorage() checked it, in which
      // case keys() may have silently returned nothing. Closing is permanent, so if it is still
      // open now, keys() ran on an open instance; otherwise re-fetch and collect again.
      if(storage.isClosed()) {
         storage = getStorage();
         keys = getOrgKeys(storage, orgID);
      }

      if(!keys.isEmpty()) {
         try {
            storage.removeAll(keys).get(10L, TimeUnit.SECONDS);
         }
         catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Interrupted while removing EM favorites for deleted organization {}", orgID, e);
         }
         catch(Exception e) {
            LOG.warn("Failed to remove EM favorites for deleted organization {}", orgID, e);
         }
      }
   }

   private static Set<String> getOrgKeys(KeyValueStorage<FavoriteList> storage, String orgID) {
      return storage.keys()
         .filter(key -> Tool.equals(orgID, IdentityID.getIdentityIDFromKey(key).orgID))
         .collect(Collectors.toSet());
   }

   private final KeyValueStorageManager keyValueStorageManager;
   private KeyValueStorage<FavoriteList> favorites;
   private static final Logger LOG = LoggerFactory.getLogger(FavoritesService.class);
}
