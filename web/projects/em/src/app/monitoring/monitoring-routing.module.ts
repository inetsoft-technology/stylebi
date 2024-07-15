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
import { NgModule } from "@angular/core";
import { RouterModule, Routes } from "@angular/router";
import { AuthorizationGuard } from "../authorization/authorization-guard.service";
import { SearchResultResolver } from "../search/search-result-resolver.service";
import { SearchResultsViewComponent } from "../search/search-results-view/search-results-view.component";
import { MonitoringLevelGuard } from "./monitoring-level-guard.service";
import { MonitoringSidenavComponent } from "./monitoring-sidenav/monitoring-sidenav.component";

const routes: Routes = [
   {
      path: "",
      component: MonitoringSidenavComponent,
      children: [
         {
            path: "summary",
            loadChildren: () => import("./summary/summary.module").then(m => m.SummaryModule),
            canActivate: [AuthorizationGuard, MonitoringLevelGuard],
            data: {
               permissionParentPath: "monitoring",
               permissionChild: "summary"
            }
         },
         {
            path: "viewsheets",
            loadChildren: () => import("./viewsheets/viewsheets.module").then(m => m.ViewsheetsModule),
            canActivate: [AuthorizationGuard, MonitoringLevelGuard],
            data: {
               permissionParentPath: "monitoring",
               permissionChild: "viewsheets"
            }
         },
         {
            path: "queries",
            loadChildren: () => import("./queries/queries.module").then(m => m.QueriesModule),
            canActivate: [AuthorizationGuard, MonitoringLevelGuard],
            data: {
               permissionParentPath: "monitoring",
               permissionChild: "queries"
            }
         },
         {
            path: "cache",
            loadChildren: () => import("./cache/cache.module").then(m => m.CacheModule),
            canActivate: [AuthorizationGuard, MonitoringLevelGuard],
            data: {
               permissionParentPath: "monitoring",
               permissionChild: "cache"
            }
         },
         {
            path: "users",
            loadChildren: () => import("./users/users.module").then(m => m.UsersModule),
            canActivate: [AuthorizationGuard, MonitoringLevelGuard],
            data: {
               permissionParentPath: "monitoring",
               permissionChild: "users"
            }
         },
         {
            path: "cluster",
            loadChildren: () => import("./cluster/cluster.module").then(m => m.ClusterModule),
            canActivate: [AuthorizationGuard, MonitoringLevelGuard],
            data: {
               permissionParentPath: "monitoring",
               permissionChild: "cluster"
            }
         },
         {
            path: "log",
            loadChildren: () => import("./log/log.module").then(m => m.LogModule),
            canActivate: [AuthorizationGuard],
            data: {
               permissionParentPath: "monitoring",
               permissionChild: "log"
            }
         },
         {
            path: "search",
            component: SearchResultsViewComponent,
            resolve: {
               searchResults: SearchResultResolver
            },
            runGuardsAndResolvers: "paramsOrQueryParamsChange",
            canActivate: [MonitoringLevelGuard],
            data: {
               permissionParentPath: "monitoring",
               permissionChild: "search"
            }
         },
         {
            path: "monitoringoff",
            loadChildren: () => import("./monitoring-off/monitoring-off.module").then(m => m.MonitoringOffModule),
         },
         {
            path: "**",
            redirectTo: "summary"
         }
      ]
   }
];

@NgModule({
   imports: [RouterModule.forChild(routes)],
   exports: [RouterModule],
   providers: [SearchResultResolver, AuthorizationGuard, MonitoringLevelGuard]
})
export class MonitoringRoutingModule {
}
