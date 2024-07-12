/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.admin.favorites;

import inetsoft.storage.KeyValueStorage;
import inetsoft.util.SingletonManager;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Collections;

@RestController
public class FavoritesController {
   @GetMapping("/api/em/favorites")
   public FavoriteList getFavorites(Principal user) {
      FavoriteList list = favorites.get(user.getName());


      if(list == null) {
         list = FavoriteList.builder()
            .favorites(Collections.emptyList())
            .build();
      }

      return list;
   }

   @PutMapping("/api/em/favorites")
   public void setFavorites(@RequestBody FavoriteList userFavorites, Principal user) {
      if(userFavorites.favorites().isEmpty()) {
         favorites.remove(user.getName());
      }
      else {
         favorites.put(user.getName(), userFavorites);
      }
   }

   @PostConstruct
   public void initStorage() {
      favorites = SingletonManager.getInstance(KeyValueStorage.class,"emFavorites");
   }

   @PreDestroy
   public void closeStorage() throws Exception {
      if(favorites != null) {
         favorites.close();
         favorites = null;
      }
   }

   private KeyValueStorage<FavoriteList> favorites;
}
