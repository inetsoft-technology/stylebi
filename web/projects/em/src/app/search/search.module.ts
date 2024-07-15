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
import { CommonModule } from "@angular/common";
import { NgModule } from "@angular/core";
import { MatPaginatorModule } from "@angular/material/paginator";
import { MatTableModule } from "@angular/material/table";
import { RouterModule } from "@angular/router";
import { SearchResultsViewComponent } from "./search-results-view/search-results-view.component";
import { SearchResultsComponent } from "./search-results/search-results.component";

@NgModule({
   imports: [
      CommonModule,
      RouterModule,
      MatPaginatorModule,
      MatTableModule
   ],
   declarations: [SearchResultsComponent, SearchResultsViewComponent],
   exports: [SearchResultsComponent, SearchResultsViewComponent]
})
export class SearchModule {
}
