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
import { authorizationGuard } from "../../authorization/authorization-guard.service";
import { PresentationNavViewComponent } from "./presentation-nav-view/presentation-nav-view.component";
import { presentationOrgSaveGuard } from "./presentation-settings-view/presentation-org-save-guard";
import { PresentationOrgSettingsViewComponent } from "./presentation-settings-view/presentation-org-settings-view.component";
import { presentationSaveGuard } from "./presentation-settings-view/presentation-save.guard";
import { PresentationSettingsViewComponent } from "./presentation-settings-view/presentation-settings-view.component";
import { PresentationThemesViewComponent } from "./presentation-themes-view/presentation-themes-view.component";
import { themesSaveGuard } from "./presentation-themes-view/themes-save.guard";

const routes: Routes = [
   {
      path: "",
      component: PresentationNavViewComponent,
      children: [
         {
            path: "settings",
            component: PresentationSettingsViewComponent,
            canActivate: [authorizationGuard],
            canDeactivate: [presentationSaveGuard],
            data: {
               permissionParentPath: "settings/presentation",
               permissionChild: "settings"
            }
         },
         {
            path: "org-settings",
            component: PresentationOrgSettingsViewComponent,
            canActivate: [authorizationGuard],
            canDeactivate: [presentationOrgSaveGuard],
            data: {
               permissionParentPath: "settings/presentation",
               permissionChild: "org-settings"
            }
         },
         {
            path: "themes",
            component: PresentationThemesViewComponent,
            canActivate: [authorizationGuard],
            canDeactivate: [themesSaveGuard],
            data: {
               permissionParentPath: "settings/presentation",
               permissionChild: "themes"
            }
         },
         {
            path: "**",
            redirectTo: "settings"
         }
      ]
   }
];

@NgModule({
   imports: [RouterModule.forChild(routes)],
   exports: [RouterModule]
})
export class PresentationRoutingModule {
}
