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
import { AuthorizationGuard } from "../../authorization/authorization-guard.service";
import { ContentSettingsViewComponent } from "./content-settings-view/content-settings-view.component";

const routes: Routes = [
   {
      path: "",
      component: ContentSettingsViewComponent,
      children: [
         {
            path: "repository",
            loadChildren: () => import("./repository/repository.module").then(m => m.RepositoryModule),
            canActivate: [AuthorizationGuard],
            data: {
               permissionParentPath: "settings/content",
               permissionChild: "repository"
            },
         },
         {
            path: "data-space",
            loadChildren: () => import("./data-space/data-space.module").then(m => m.DataSpaceModule),
            canActivate: [AuthorizationGuard],
            data: {
               permissionParentPath: "settings/content",
               permissionChild: "data-space"
            }
         },
         {
            path: "drivers-and-plugins",
            loadChildren: () => import("./drivers-and-plugins/drivers-and-plugins.module").then(m => m.DriversAndPluginsModule),
            canActivate: [AuthorizationGuard],
            data: {
               permissionParentPath: "settings/content",
               permissionChild: "drivers-and-plugins"
            }
         },
         {
            path: "materialized-views",
            loadChildren: () => import("./materialized-views/materialized-views.module").then(m => m.MaterializedViewsModule),
            canActivate: [AuthorizationGuard],
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

@NgModule({
   imports: [RouterModule.forChild(routes)],
   exports: [RouterModule]
})
export class ContentRoutingModule {
}
