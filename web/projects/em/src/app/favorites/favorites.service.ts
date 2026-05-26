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
import { HttpClient } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { BehaviorSubject, EMPTY, Observable } from "rxjs";
import { catchError, map, take } from "rxjs/operators";
import { Favorite } from "./favorite";
import { FavoriteList } from "./favorite-list";

@Injectable({
   providedIn: "root"
})
export class FavoritesService {
   private readonly _favorites = new BehaviorSubject<Favorite[]>([]);

   get favorites(): Observable<Favorite[]> {
      return this._favorites.asObservable();
   }

   constructor(private http: HttpClient) {
      this.http.get<FavoriteList>("../api/em/favorites").pipe(
         catchError(() => EMPTY)
      ).subscribe((list) => this._favorites.next(list.favorites));
   }

   addFavorite(path: string, label: string): void {
      this.favorites.pipe(
         take(1)
      ).subscribe(
         (favs) => {
            const index = favs.findIndex(f => f.path === path);
            let updated: Favorite[];

            if(index < 0) {
               updated = favs.concat([{path, label}]);
            }
            else {
               updated = favs.concat([]);
               updated[index].label = label;
            }

            this.setFavorites(updated);
         }
      );
   }

   removeFavorite(path: string): void {
      this.favorites.pipe(
         take(1)
      ).subscribe(
         (favs) => {
            const updated = favs.filter(f => f.path !== path);
            this.setFavorites(updated);
         }
      );
   }

   isFavorite(path: string): Observable<boolean> {
      return this.favorites.pipe(
         take(1),
         map(f => !!f.find(item => item.path === path))
      );
   }

   private setFavorites(favorites: Favorite[]): void {
      this.http.put("../api/em/favorites", { favorites }).pipe(
         catchError(err => { console.error("Failed to save favorites", err); return EMPTY; })
      ).subscribe(() => this._favorites.next(favorites));
   }
}
