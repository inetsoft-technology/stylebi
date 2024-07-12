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
import { NgModule } from "@angular/core";
import { RouterModule, Routes } from "@angular/router";
import { AuthorizationGuard } from "./authorization/authorization-guard.service";
import { ManageFavoritesComponent } from "./manage-favorites/manage-favorites.component";
import { PasswordComponent } from "./password/password.component";

const routes: Routes = [
   {
      path: "",
      children: [
         {
            path: "monitoring",
            loadChildren: () => import("./monitoring/monitoring.module").then(m => m.MonitoringModule),
            canActivate: [AuthorizationGuard],
            data: {
               permissionParentPath: "",
               permissionChild: "monitoring"
            }
         },
         {
            path: "auditing",
            loadChildren: () => import("./auditing/auditing.module").then(m => m.AuditingModule),
            canActivate: [AuthorizationGuard],
            data: {
               permissionParentPath: "",
               permissionChild: "auditing"
            }
         },
         {
            path: "settings",
            loadChildren: () => import("./settings/settings.module").then(m => m.SettingsModule),
            canActivate: [AuthorizationGuard],
            data: {
               permissionParentPath: "",
               permissionChild: "settings"
            }
         },
         {
            path: "password",
            component: PasswordComponent,
            data: {
               permissionParentPath: "",
               permissionChild: "password"
            }
         },
         {
            path: "favorites",
            component: ManageFavoritesComponent,
            data: {
               permissionParentPath: "",
               permissionChild: "favorites"
            }
         },
         {
            path: "**",
            redirectTo: "monitoring"
         }
      ]
   }
];

@NgModule({
   imports: [RouterModule.forRoot(routes, { onSameUrlNavigation: "reload", anchorScrolling: "enabled" })],
   exports: [RouterModule],
   providers: [AuthorizationGuard]
})
export class AppRoutingModule {
}
