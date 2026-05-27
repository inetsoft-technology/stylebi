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
import { Component, OnDestroy, OnInit } from "@angular/core";
import { BehaviorSubject, Subject } from "rxjs";
import { takeUntil } from "rxjs/operators";
import { ContextHelp } from "../context-help";
import { Favorite } from "../favorites/favorite";
import { FavoritesService } from "../favorites/favorites.service";
import { PageHeaderService } from "../page-header/page-header.service";
import { Searchable } from "../searchable";
import { MatIcon } from "@angular/material/icon";
import { MatIconButton } from "@angular/material/button";
import { MatTable, MatColumnDef, MatHeaderCellDef, MatHeaderCell, MatCellDef, MatCell, MatHeaderRowDef, MatHeaderRow, MatRowDef, MatRow } from "@angular/material/table";
import { MatCard, MatCardContent } from "@angular/material/card";
import { MatToolbar } from "@angular/material/toolbar";

@Searchable({
   route: "/favorites",
   title: "Manage Favorites",
   keywords: []
})
@ContextHelp({
   route: "/favorites",
   link: "EMFavorites"
})
@Component({
    selector: "em-manage-favorites",
    templateUrl: "./manage-favorites.component.html",
    styleUrls: ["./manage-favorites.component.scss"],
    standalone: true,
    imports: [MatToolbar, MatCard, MatCardContent, MatTable, MatColumnDef, MatHeaderCellDef, MatHeaderCell, MatCellDef, MatCell, MatIconButton, MatIcon, MatHeaderRowDef, MatHeaderRow, MatRowDef, MatRow]
})
export class ManageFavoritesComponent implements OnInit, OnDestroy {
   favorites = new BehaviorSubject<Favorite[]>([]);
   displayedColumns = ["page", "actions"];

   get title(): string {
      return this.pageTitle.title;
   }

   private destroy$ = new Subject<void>();

   constructor(private pageTitle: PageHeaderService, private favoritesService: FavoritesService) {
   }

   ngOnInit() {
      this.pageTitle.title = "_#(js:Manage Favorites)";
      this.favoritesService.favorites
         .pipe(takeUntil(this.destroy$))
         .subscribe((value) => this.favorites.next(value));
   }

   ngOnDestroy(): void {
      this.destroy$.next();
      this.destroy$.unsubscribe();
   }

   removeFavorite(favorite: Favorite): void {
      this.favoritesService.removeFavorite(favorite.path);
   }
}
