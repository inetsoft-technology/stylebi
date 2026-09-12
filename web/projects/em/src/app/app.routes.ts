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
import { authorizationGuard } from "./authorization/authorization-guard.service";
import { ManageFavoritesComponent } from "./manage-favorites/manage-favorites.component";
import { PasswordComponent } from "./password/password.component";

export const APP_ROUTES: Routes = [
   {
      path: "",
      children: [
         {
            path: "monitoring",
            loadChildren: () => import("./monitoring/monitoring.routes").then(m => m.MONITORING_ROUTES),
            canActivate: [authorizationGuard],
            data: {
               permissionParentPath: "",
               permissionChild: "monitoring"
            }
         },
         {
            path: "auditing",
            loadChildren: () => import("./auditing/auditing.routes").then(m => m.AUDITING_ROUTES),
            canActivate: [authorizationGuard],
            data: {
               permissionParentPath: "",
               permissionChild: "auditing"
            }
         },
         {
            path: "settings",
            loadChildren: () => import("./settings/settings.routes").then(m => m.SETTINGS_ROUTES),
            canActivate: [authorizationGuard],
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
