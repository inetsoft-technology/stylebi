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
import { Component, OnDestroy, OnInit, ViewChild } from "@angular/core";
import { NavigationEnd, Router } from "@angular/router";
import { Subscription } from "rxjs";
import {
   AiAssistantService,
   ContextType
} from "../../../../../shared/ai-assistant/ai-assistant.service";
import { RepositoryClientService } from "../../common/repository-client/repository-client.service";
import { SplitPane } from "../../widget/split-pane/split-pane.component";
import { DataDetailsPaneService } from "./services/data-details-pane.service";
import { DataPhysicalModelService } from "./services/data-physical-model.service";

@Component({
   selector: "p-data-tab",
   templateUrl: "./data-tab.component.html",
   styleUrls: ["./data-tab.component.scss"],
   providers: [RepositoryClientService, DataDetailsPaneService]
})
export class DataTabComponent implements OnInit, OnDestroy {
   @ViewChild(SplitPane) splitPane: SplitPane;

   readonly INIT_TREE_PANE_SIZE = 25;
   treePaneSize: number = this.INIT_TREE_PANE_SIZE;
   treePaneCollapsed: boolean = false;
   private subscription: Subscription;
   public hiddenCollapsed: boolean = false;
   currentUrl: string = "";
   searchView: boolean = false;

   constructor(private readonly physicalModelService: DataPhysicalModelService,
               private aiAssistantSerivice: AiAssistantService,
               private readonly dataDetailsPaneService: DataDetailsPaneService,
               private readonly router: Router)
   {
      this.aiAssistantSerivice.setContextTypeFieldValue(ContextType.PORTAL_DATA);
      this.subscription = this.physicalModelService.onFullScreen.subscribe((fullScreen: boolean) => {
         this.hiddenCollapsed = fullScreen;
         this.treePaneCollapsed = fullScreen;
         this.updateDataTreePane();
      });
   }

   ngOnInit(): void {
      this.currentUrl = this.router.url;
      this.updateSearchView();
      this.subscription.add(this.router.events.subscribe((event) => {
         if(event instanceof NavigationEnd) {
            this.currentUrl = event.urlAfterRedirects;
            this.updateSearchView();

            if(!event.urlAfterRedirects.startsWith("/portal/tab/data/folder") &&
               !event.urlAfterRedirects.startsWith("/portal/tab/data?") &&
               event.urlAfterRedirects !== "/portal/tab/data")
            {
               this.dataDetailsPaneService.clear();
            }
         }
      }));
   }

   ngOnDestroy(): void {
      if(!!this.subscription) {
         this.subscription.unsubscribe();
         this.subscription = null;
      }
   }

   get showSearchResults(): boolean {
      return this.searchView;
   }

   get showMainPane(): boolean {
      return !this.isEmptyLandingRoute() || this.showSearchResults;
   }

   get showEmptyState(): boolean {
      return this.isEmptyLandingRoute() && !this.showSearchResults;
   }

   toggleDataTreePane(): void {
      this.treePaneCollapsed = !this.treePaneCollapsed;
      this.updateDataTreePane();
   }

   updateDataTreePane(): void {
      if(!this.treePaneCollapsed) {
         this.splitPane.setSizes([this.treePaneSize, 100 - this.treePaneSize]);
      }
      else {
         this.splitPane.collapse(0);
      }
   }

   splitPaneDragEnd(): void {
      if(this.hiddenCollapsed) {
         return;
      }

      this.treePaneSize = this.splitPane.getSizes()[0];

      if(this.treePaneSize > 1) {
         this.treePaneCollapsed = false;
      }
      else {
         this.treePaneCollapsed = true;
         this.treePaneSize = this.INIT_TREE_PANE_SIZE;
      }
   }

   private updateSearchView(): void {
      const [path, query = ""] = this.currentUrl.split("?");
      this.searchView = path.startsWith("/portal/tab/data/folder") &&
         new URLSearchParams(query).has("query");
   }

   private isDatasourceLandingRoute(): boolean {
      const [path, query = ""] = this.currentUrl.split("?");

      if(path !== "/portal/tab/data/datasources") {
         return false;
      }

      const params = new URLSearchParams(query);
      return !params.has("path") && !params.has("scope") && !params.has("query");
   }

   private isEmptyLandingRoute(): boolean {
      const [path, query = ""] = this.currentUrl.split("?");
      const params = new URLSearchParams(query);
      const isWorksheetLanding = path === "/portal/tab/data/folder" &&
         !params.has("path") && !params.has("scope") && !params.has("query");

      return isWorksheetLanding || this.isDatasourceLandingRoute();
   }
}
