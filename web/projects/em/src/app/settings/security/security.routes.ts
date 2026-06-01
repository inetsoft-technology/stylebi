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
import { SecuritySettingsPageComponent } from "./security-settings-page/security-settings-page.component";

export const SECURITY_ROUTES: Routes = [
   {
      path: "",
      component: SecuritySettingsPageComponent,
      children: [
         {
            path: "provider",
            loadChildren: () => import("./security-provider/security-provider.routes").then(m => m.SECURITY_PROVIDER_ROUTES),
            canActivate: [authorizationGuard],
            data: {
               permissionParentPath: "settings/security",
               permissionChild: "provider"
            }
         },
         {
            path: "users",
            loadChildren: () => import("./users/users-settings.routes").then(m => m.USERS_SETTINGS_ROUTES),
            canActivate: [authorizationGuard],
            data: {
               permissionParentPath: "settings/security",
               permissionChild: "users"
            }
         },
         {
            path: "actions",
            loadChildren: () => import("./security-actions/security-actions.routes").then(m => m.SECURITY_ACTIONS_ROUTES),
            canActivate: [authorizationGuard],
            data: {
               permissionParentPath: "settings/security",
               permissionChild: "actions"
            }
         },
         {
            path: "sso",
            loadChildren: () => import("./sso/sso-settings.routes").then(m => m.SSO_SETTINGS_ROUTES),
            canActivate: [authorizationGuard],
            data: {
               permissionParentPath: "settings/security",
               permissionChild: "sso"
            }
         },
         {
            path: "googleSignIn",
            loadChildren: () => import("./google-sign-in/google-sign-in.routes").then(m => m.GOOGLE_SIGN_IN_ROUTES),
            canActivate: [authorizationGuard],
            data: {
               permissionParentPath: "settings/security",
               permissionChild: "googleSignIn"
            }
         },
         {
            path: "**",
            redirectTo: "provider"
         }
      ]
   }
];
