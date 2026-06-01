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
import { MAT_PAGINATOR_DEFAULT_OPTIONS } from "@angular/material/paginator";
import { authorizationGuard } from "../authorization/authorization-guard.service";
import { searchResultResolver } from "../search/search-result-resolver.service";
import { SearchResultsViewComponent } from "../search/search-results-view/search-results-view.component";
import { SettingsSidenavComponent } from "./settings-sidenav/settings-sidenav.component";

export const SETTINGS_ROUTES: Routes = [
   {
      path: "",
      component: SettingsSidenavComponent,
      providers: [
         { provide: MAT_PAGINATOR_DEFAULT_OPTIONS, useValue: { formFieldAppearance: "fill" } }
      ],
      children: [
         {
            path: "general",
            loadChildren: () => import("./general/general.routes").then(m => m.GENERAL_ROUTES),
            canActivate: [authorizationGuard],
            data: {
               permissionParentPath: "settings",
               permissionChild: "general"
            }
         },
         {
            path: "security",
            loadChildren: () => import("./security/security.routes").then(m => m.SECURITY_ROUTES),
            canActivate: [authorizationGuard],
            data: {
               permissionParentPath: "settings",
               permissionChild: "security"
            }
         },
         {
            path: "content",
            loadChildren: () => import("./content/content.routes").then(m => m.CONTENT_ROUTES),
            canActivate: [authorizationGuard],
            data: {
               permissionParentPath: "settings",
               permissionChild: "content"
            }
         },
         {
            path: "schedule",
            loadChildren: () => import("./schedule/schedule.routes").then(m => m.SCHEDULE_ROUTES),
            canActivate: [authorizationGuard],
            data: {
               permissionParentPath: "settings",
               permissionChild: "schedule"
            }
         },
         {
            path: "presentation",
            loadChildren: () => import("./presentation/presentation.routes").then(m => m.PRESENTATION_ROUTES),
            canActivate: [authorizationGuard],
            data: {
               permissionParentPath: "settings",
               permissionChild: "presentation"
            }
         },
         {
            path: "logging",
            loadChildren: () => import("./logging/logging.routes").then(m => m.LOGGING_ROUTES),
            canActivate: [authorizationGuard],
            data: {
               permissionParentPath: "settings",
               permissionChild: "logging"
            }
         },
         {
            path: "properties",
            loadChildren: () => import("./properties/properties.routes").then(m => m.PROPERTIES_ROUTES),
            canActivate: [authorizationGuard],
            data: {
               permissionParentPath: "settings",
               permissionChild: "properties"
            }
         },
         {
            path: "search",
            component: SearchResultsViewComponent,
            resolve: {
               searchResults: searchResultResolver
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
