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
import {
   ChangeDetectionStrategy,
   ChangeDetectorRef,
   Component,
   OnDestroy,
   OnInit
} from "@angular/core";
import { ActivatedRoute, ActivatedRouteSnapshot, ParamMap, Router, RouterStateSnapshot } from "@angular/router";
import { Observable, Subscription } from "rxjs";
import { AssetConstants } from "../../../../common/data/asset-constants";
import { DataSourceListing } from "./datasource-listing/datasource-listing";
import { DatasourceSelectionViewModel } from "./datasource-selection-view-model";
import { DatasourceSelectionService } from "./datasource-selection.service";
import { SearchTool } from "../../../../common/util/search-tool";
import { SearchComparator } from "../../../../widget/tree/search-comparator";
import { CanComponentDeactivate } from "../../../../../../../shared/util/guard/can-component-deactivate";
import {
   GettingStartedService
} from "../../../../widget/dialog/getting-started-dialog/service/getting-started.service";
import { DatasourceType } from "../datasource-type";

@Component({
   selector: "datasource-selection-view",
   templateUrl: "datasource-selection-view.component.html",
   styleUrls: ["datasource-selection-view.component.scss"],
   changeDetection: ChangeDetectionStrategy.OnPush,
   providers: [DatasourceSelectionService]
})
export class DatasourceSelectionViewComponent implements OnInit, OnDestroy, CanComponentDeactivate {
   model: DatasourceSelectionViewModel | null = null;
   selectedCategory: string | null = null;
   selectedListingName: string | null = null;
   searchString = "";
   private parentPath: string = "";
   private openByGettingStarted: boolean;
   private subscriptions: Subscription = new Subscription();

   constructor(private datasourceService: DatasourceSelectionService,
               private gettingStartedService: GettingStartedService,
               private cd: ChangeDetectorRef,
               private router: Router,
               private route: ActivatedRoute)
   {
   }

   ngOnInit(): void {
      this.subscriptions.add(
         this.datasourceService.getDatasourceSelectionViewModel().subscribe((model) => {
            this.model = model;
            this.cd.detectChanges();
         }));

      this.subscriptions.add(
         this.route.paramMap.subscribe((routeParams: ParamMap) => {
            this.parentPath = routeParams.get("parentPath");
         }));

      this.subscriptions.add(
         this.route.queryParamMap.subscribe((routeParams: ParamMap) => {
            this.openByGettingStarted = routeParams.has("gettingStartedRouteTime");
         }));
   }

   ngOnDestroy(): void {
      this.subscriptions.unsubscribe();
      this.subscriptions = null;
   }

   public canDeactivate(component?: CanComponentDeactivate, route?: ActivatedRouteSnapshot,
                        currentState?: RouterStateSnapshot,
                        nextState?: RouterStateSnapshot): Observable<boolean> | Promise<boolean> | boolean
   {
      if(this.openByGettingStarted && this.gettingStartedService.isConnectTo()) {
         if(!currentState?.url?.startsWith("/portal/tab/data/datasources/listing/") ||
            !nextState?.url?.startsWith("/portal/tab/data/datasources/database/listing") &&
            !nextState?.url?.startsWith("/portal/tab/data/datasources/datasource/listing") &&
            !nextState?.url?.startsWith("/portal/tab/data/datasources/datasource/xmla/new"))
         {
            setTimeout(() => this.gettingStartedService.continue(), 100);
         }
      }

      return true;
   }

   getListings(): DataSourceListing[] {
      if(this.model == null) {
         return [];
      }

      let listings = this.model.listings;

      if(this.searchString) {
         listings = listings
            .filter((l) =>
               SearchTool.match(l.name, this.searchString) ||
               SearchTool.anyMatch(l.keywords, this.searchString))
            .sort((a, b) => new SearchComparator(this.searchString).searchSortStr(a.name, b.name));
      }

      if(this.selectedCategory != null) {
         listings = listings.filter((l) => l.category === this.selectedCategory);
      }

      return listings;
   }

   isCreateDisabled(): boolean {
      return this.selectedListingName == null ||
         this.getListings().find((l) => l.name === this.selectedListingName) == null;
   }

   create(listingName: string): void {
      const name = listingName ? listingName : this.selectedListingName;

      this.datasourceService.getDataSourceType(name)
         .subscribe(sourceType => {
            if(sourceType == DatasourceType.TABULAR) {
               this.router.navigate(
                  ["datasources/datasource/listing", name, this.parentPath],
                  {relativeTo: this.route.parent});
            }
            else if(sourceType == DatasourceType.CUBE) {
               this.router.navigate(
                  ["datasources/datasource/xmla/new", this.parentPath],
                  {relativeTo: this.route.parent});
            }
            else {
               this.router.navigate(
                  ["datasources/database/listing", name, this.parentPath],
                  {relativeTo: this.route.parent});
            }
         });
   }

   cancel(): void {
      if(this.openByGettingStarted && this.gettingStartedService.isConnectTo()) {
         setTimeout(() => this.gettingStartedService.continue(), 100);
      }
      else {
         this.router.navigate(["/portal/tab/data/datasources"], {
            queryParams: {
               path: this.parentPath ? this.parentPath : "/",
               scope: AssetConstants.QUERY_SCOPE
            }
         });
      }
   }
}
