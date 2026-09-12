/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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

import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
public class FavoritesController {
   public FavoritesController(FavoritesService favoritesService) {
      this.favoritesService = favoritesService;
   }

   @GetMapping("/api/em/favorites")
   public FavoriteList getFavorites(Principal user) {
      return favoritesService.getFavorites(user.getName());
   }

   @PutMapping("/api/em/favorites")
   public void setFavorites(@RequestBody FavoriteList userFavorites, Principal user) {
      favoritesService.setFavorites(user.getName(), userFavorites);
   }

   private final FavoritesService favoritesService;
}
