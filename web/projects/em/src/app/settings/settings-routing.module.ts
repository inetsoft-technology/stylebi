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
import { NgModule } from "@angular/core";
import { RouterModule, Routes } from "@angular/router";
import { AuthorizationGuard } from "../authorization/authorization-guard.service";
import { SearchResultResolver } from "../search/search-result-resolver.service";
import { SearchResultsViewComponent } from "../search/search-results-view/search-results-view.component";
import { SettingsSidenavComponent } from "./settings-sidenav/settings-sidenav.component";

const routes: Routes = [
   {
      path: "",
      component: SettingsSidenavComponent,
      children: [
         {
            path: "general",
            loadChildren: () => import("./general/general.module").then(m => m.GeneralModule),
            canActivate: [AuthorizationGuard],
            data: {
               permissionParentPath: "settings",
               permissionChild: "general"
            }
         },
         {
            path: "security",
            loadChildren: () => import("./security/security.module").then(m => m.SecurityModule),
            canActivate: [AuthorizationGuard],
            data: {
               permissionParentPath: "settings",
               permissionChild: "security"
            }
         },
         {
            path: "content",
            loadChildren: () => import("./content/content.module").then(m => m.ContentModule),
            canActivate: [AuthorizationGuard],
            data: {
               permissionParentPath: "settings",
               permissionChild: "content"
            }
         },
         {
            path: "schedule",
            loadChildren: () => import("./schedule/schedule.module").then(m => m.ScheduleModule),
            canActivate: [AuthorizationGuard],
            data: {
               permissionParentPath: "settings",
               permissionChild: "schedule"
            }
         },
         {
            path: "presentation",
            loadChildren: () => import("./presentation/presentation.module").then(m => m.PresentationModule),
            canActivate: [AuthorizationGuard],
            data: {
               permissionParentPath: "settings",
               permissionChild: "presentation"
            }
         },
         {
            path: "logging",
            loadChildren: () => import("./logging/logging.module").then(m => m.LoggingModule),
            canActivate: [AuthorizationGuard],
            data: {
               permissionParentPath: "settings",
               permissionChild: "logging"
            }
         },
         {
            path: "properties",
            loadChildren: () => import("./properties/properties.module").then(m => m.PropertiesModule),
            canActivate: [AuthorizationGuard],
            data: {
               permissionParentPath: "settings",
               permissionChild: "properties"
            }
         },
         {
            path: "search",
            component: SearchResultsViewComponent,
            resolve: {
               searchResults: SearchResultResolver
            },
            runGuardsAndResolvers: "paramsOrQueryParamsChange",
            data: {
               permissionParentPath: "settings",
               permissionChild: "search"
            }
         },
         {
            path: "**",
            redirectTo: "general"
         }
      ]
   }
];

@NgModule({
   imports: [RouterModule.forChild(routes)],
   exports: [RouterModule],
   providers: [SearchResultResolver, AuthorizationGuard]
})
export class SettingsRoutingModule {
}
