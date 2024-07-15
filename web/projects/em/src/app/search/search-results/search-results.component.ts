/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { Component, Input, OnInit } from "@angular/core";
import { Observable } from "rxjs";
import { SearchResult } from "../search-result";

@Component({
   selector: "em-search-results",
   templateUrl: "./search-results.component.html",
   styleUrls: ["./search-results.component.scss"]
})
export class SearchResultsComponent implements OnInit {
   @Input() searchResults: Observable<SearchResult[]>;
   @Input() searchParam: Observable<string>;

   constructor() {
   }

   ngOnInit() {
   }
}
