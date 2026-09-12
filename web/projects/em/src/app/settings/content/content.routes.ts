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
import { authorizationGuard } from "../../authorization/authorization-guard.service";
import { ContentSettingsViewComponent } from "./content-settings-view/content-settings-view.component";

export const CONTENT_ROUTES: Routes = [
   {
      path: "",
      component: ContentSettingsViewComponent,
      children: [
         {
            path: "repository",
            loadChildren: () => import("./repository/repository.routes").then(m => m.REPOSITORY_ROUTES),
            canActivate: [authorizationGuard],
            data: {
               permissionParentPath: "settings/content",
               permissionChild: "repository"
            }
         },
         {
            path: "data-space",
            loadChildren: () => import("./data-space/data-space.routes").then(m => m.DATA_SPACE_ROUTES),
            canActivate: [authorizationGuard],
            data: {
               permissionParentPath: "settings/content",
               permissionChild: "data-space"
            }
         },
         {
            path: "drivers-and-plugins",
            loadChildren: () => import("./drivers-and-plugins/drivers-and-plugins.routes").then(m => m.DRIVERS_AND_PLUGINS_ROUTES),
            canActivate: [authorizationGuard],
            data: {
               permissionParentPath: "settings/content",
               permissionChild: "drivers-and-plugins"
            }
         },
         {
            path: "materialized-views",
            loadChildren: () => import("./materialized-views/materialized-views.routes").then(m => m.MATERIALIZED_VIEWS_ROUTES),
            canActivate: [authorizationGuard],
            data: {
               permissionParentPath: "settings/content",
               permissionChild: "materialized-views"
            }
         },
         {
            path: "**",
            redirectTo: "repository"
         }
      ]
   }
];
