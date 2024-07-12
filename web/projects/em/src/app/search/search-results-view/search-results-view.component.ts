/*
 * inetsoft-web - StyleBI is a business intelligence web application.
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
import { Component, OnDestroy, OnInit } from "@angular/core";
import { ActivatedRoute } from "@angular/router";
import { BehaviorSubject, Observable, Subject } from "rxjs";
import { map, takeUntil } from "rxjs/operators";
import { PageHeaderService } from "../../page-header/page-header.service";
import { SearchResult } from "../search-result";

@Component({
   selector: "em-search-results-view",
   templateUrl: "./search-results-view.component.html",
   styleUrls: ["./search-results-view.component.scss"]
})
export class SearchResultsViewComponent implements OnInit, OnDestroy {
   private _results = new BehaviorSubject<SearchResult[]>([]);
   private destroy$ = new Subject<void>();
   searchParam: string = "";

   get results(): Observable<SearchResult[]> {
      return this._results.asObservable();
   }

   constructor(private route: ActivatedRoute, private pageTitleService: PageHeaderService) {
   }

   ngOnInit() {
      this.pageTitleService.title = "_#(js:Search Results)";

      if(this.route.queryParams) {
         this.route.queryParams.pipe(
            takeUntil(this.destroy$)
         ).subscribe(
            val => this.searchParam = val.search
         );
      }

      this.route.data.pipe(
         map(data => data.searchResults),
         takeUntil(this.destroy$)
      ).subscribe(
         searchResults => this._results.next(searchResults)
      );
   }

   ngOnDestroy(): void {
      this.destroy$.next();
      this.destroy$.unsubscribe();
   }
}
