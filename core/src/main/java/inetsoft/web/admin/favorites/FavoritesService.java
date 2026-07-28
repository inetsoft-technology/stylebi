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
   public void initStorage() {
      favorites = keyValueStorageManager.getStorage("emFavorites");
   }

   @PreDestroy
   public void closeStorage() throws Exception {
      if(favorites != null) {
         favorites.close();
         favorites = null;
      }
   }

   /**
    * Gets the EM favorites for the given identity.
    *
    * @param identityKey the identity key (see {@link IdentityID#convertToKey()}).
    *
    * @return the favorites, never {@code null}.
    */
   public FavoriteList getFavorites(String identityKey) {
      FavoriteList list = favorites.get(identityKey);

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
      try {
         if(userFavorites.getFavorites().isEmpty()) {
            favorites.remove(identityKey).get(10L, TimeUnit.SECONDS);
         }
         else {
            favorites.put(identityKey, userFavorites).get(10L, TimeUnit.SECONDS);
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
      FavoriteList list = favorites.get(fromKey);

      if(list != null) {
         try {
            favorites.put(toKey, list).get(10L, TimeUnit.SECONDS);
            favorites.remove(fromKey).get(10L, TimeUnit.SECONDS);
         }
         catch(InterruptedException | ExecutionException | TimeoutException e) {
            LOG.error("Failed to move favorites from {} to {}", fromKey, toKey, e);
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

      for(IdentityID id : identities) {
         if(id != null) {
            try {
               favorites.remove(id.convertToKey()).get(10L, TimeUnit.SECONDS);
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
      Set<String> keys = favorites.keys()
         .filter(key -> Tool.equals(orgID, IdentityID.getIdentityIDFromKey(key).orgID))
         .collect(Collectors.toSet());

      if(!keys.isEmpty()) {
         try {
            favorites.removeAll(keys).get(10L, TimeUnit.SECONDS);
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

   private final KeyValueStorageManager keyValueStorageManager;
   private KeyValueStorage<FavoriteList> favorites;
   private static final Logger LOG = LoggerFactory.getLogger(FavoritesService.class);
}
