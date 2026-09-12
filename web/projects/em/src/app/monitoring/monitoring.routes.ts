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
import { Routes } from "@angular/router";
import { authorizationGuard } from "../authorization/authorization-guard.service";
import { searchResultResolver } from "../search/search-result-resolver.service";
import { SearchResultsViewComponent } from "../search/search-results-view/search-results-view.component";
import { monitoringLevelGuard } from "./monitoring-level-guard.service";
import { MonitoringSidenavComponent } from "./monitoring-sidenav/monitoring-sidenav.component";
import { MonitorLevelService } from "./monitor-level.service";
import { MonitoringDataService } from "./monitoring-data.service";

export const MONITORING_ROUTES: Routes = [
   {
      path: "",
      component: MonitoringSidenavComponent,
      providers: [MonitoringDataService, MonitorLevelService],
      children: [
         {
            path: "summary",
            loadChildren: () => import("./summary/summary.routes").then(m => m.SUMMARY_ROUTES),
            canActivate: [authorizationGuard, monitoringLevelGuard],
            data: {
               permissionParentPath: "monitoring",
               permissionChild: "summary"
            }
         },
         {
            path: "viewsheets",
            loadChildren: () => import("./viewsheets/viewsheets.routes").then(m => m.VIEWSHEETS_ROUTES),
            canActivate: [authorizationGuard, monitoringLevelGuard],
            data: {
               permissionParentPath: "monitoring",
               permissionChild: "viewsheets"
            }
         },
         {
            path: "queries",
            loadChildren: () => import("./queries/queries.routes").then(m => m.QUERIES_ROUTES),
            canActivate: [authorizationGuard, monitoringLevelGuard],
            data: {
               permissionParentPath: "monitoring",
               permissionChild: "queries"
            }
         },
         {
            path: "cache",
            loadChildren: () => import("./cache/cache.routes").then(m => m.CACHE_ROUTES),
            canActivate: [authorizationGuard, monitoringLevelGuard],
            data: {
               permissionParentPath: "monitoring",
               permissionChild: "cache"
            }
         },
         {
            path: "users",
            loadChildren: () => import("./users/users.routes").then(m => m.USERS_ROUTES),
            canActivate: [authorizationGuard, monitoringLevelGuard],
            data: {
               permissionParentPath: "monitoring",
               permissionChild: "users"
            }
         },
         {
            path: "cluster",
            loadChildren: () => import("./cluster/cluster.routes").then(m => m.CLUSTER_ROUTES),
            canActivate: [authorizationGuard, monitoringLevelGuard],
            data: {
               permissionParentPath: "monitoring",
               permissionChild: "cluster"
            }
         },
         {
            path: "log",
            loadChildren: () => import("./log/log.routes").then(m => m.LOG_ROUTES),
            canActivate: [authorizationGuard],
            data: {
               permissionParentPath: "monitoring",
               permissionChild: "log"
            }
         },
         {
            path: "search",
            component: SearchResultsViewComponent,
            resolve: {
               searchResults: searchResultResolver
            },
            runGuardsAndResolvers: "paramsOrQueryParamsChange",
            canActivate: [monitoringLevelGuard],
            data: {
               permissionParentPath: "monitoring",
               permissionChild: "search"
            }
         },
         {
            path: "monitoringoff",
            loadChildren: () => import("./monitoring-off/monitoring-off.routes").then(m => m.MONITORING_OFF_ROUTES)
         },
         {
            path: "**",
            redirectTo: "summary"
         }
      ]
   }
];
