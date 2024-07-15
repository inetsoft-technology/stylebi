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
import { HttpClient, HttpParams } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { ActivatedRouteSnapshot, Resolve, RouterStateSnapshot } from "@angular/router";
import { Observable, of as observableOf } from "rxjs";
import { map } from "rxjs/operators";
import { DashboardModel } from "../common/data/dashboard-model";
import { GuiTool } from "../common/util/gui-tool";
import { ViewDataService } from "./services/view-data.service";
import { ViewData } from "./view-data";
import { PreviousSnapshot } from "../widget/hyperlink/previous-snapshot";
import { ShowHyperlinkService } from "../vsobjects/show-hyperlink.service";
import { ViewConstants } from "./view-constants";

interface ViewsheetRouteDataModel {
   scaleToScreen: boolean;
   fitToWidth: boolean;
}

@Injectable()
export class ViewDataResolver implements Resolve<ViewData> {
   constructor(private editDataService: ViewDataService, private http: HttpClient) {
   }

   resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<ViewData> {
      let data = this.editDataService.data;
      const routeParamMap = route.paramMap;
      const routeQueryParamMap = ShowHyperlinkService.getQueryParams(route.queryParamMap);

      if(data && (data.assetId !== routeParamMap.get("assetId") ||
         data.dashboardName !== routeParamMap.get("dashboardName")) ||
         routeQueryParamMap.get("resetData") === "true")
      {
         // different viewsheet, reset view data
         data = null;
         this.editDataService.data = null;
      }

      const { inPortal, inDashboard } = route.parent.data;

      if(inPortal || !data) {
         let assetId = routeParamMap.get("assetId");

         if(!!assetId) {
            assetId = decodeURIComponent(assetId);
         }

         data = {
            ...data,
            assetId,
            queryParameters: GuiTool.getQueryParameters(),
            portal: inPortal,
            dashboard: inDashboard,
            isMetadata: routeParamMap.get("isMetadata") == "true",
            dashboardName: routeParamMap.get("dashboardName"),
            fullScreenId: routeQueryParamMap.get("fullscreenId"),
            runtimeId: routeQueryParamMap.get("runtimeId"),
            collapseTree: routeQueryParamMap.get("collapseTree") == "true",
            previousSnapshots: routeQueryParamMap.getAll(ViewConstants.PRE_SNAPSHOT_PARAM_NAME),
         };

         this.editDataService.data = data;
      }

      if(routeQueryParamMap) {
         for(const k of routeQueryParamMap.keys) {
            data.queryParameters.set(k, routeQueryParamMap.getAll(k));
         }
      }

      if(data.previousSnapshots) {
         data.previousSnapshots
            .map((snapshot: string) => <PreviousSnapshot> JSON.parse(snapshot))
            .forEach((snapshot: PreviousSnapshot) => decodeURIComponent(snapshot.url));
      }

      data.fullScreen = routeQueryParamMap.get("fullScreen") === "true";

      if(!route.url || route.url.length < 1 || route.url[0].path !== "edit") {
         data.tableModel = null;
         data.variableValues = null;
      }

      if(!data.assetId && !!data.dashboardName) {
         const name = encodeURIComponent(data.dashboardName);
         const uri = `../api/portal/dashboard-tab-model/${name}`;
         return this.http.get<DashboardModel>(uri).pipe(
            map((model) => {
               data.assetId = model.identifier;
               data.scaleToScreen = model.scaleToScreen;
               data.fitToWidth = model.fitToWidth;
               return data;
            })
         );
      }

      if(!data.runtimeId && !!data.assetId) {
         const uri = "../api/vs/route-data";
         const params = new HttpParams()
            .set("id", data.assetId);
         return this.http.get<ViewsheetRouteDataModel>(uri, {params}).pipe(
            map(model => {
               data.scaleToScreen = model.scaleToScreen;
               data.fitToWidth = model.fitToWidth;
               return data;
            })
         );
      }

      return observableOf(data);
   }
}
