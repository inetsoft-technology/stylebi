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
import { CanActivateComposerService } from "./composer/services/can-activate-composer.service";
import { CanActivateRootService } from "./can-activate-root.service";

const routes: Routes = [
   {
      path: "",
      canActivate: [CanActivateRootService],
      children: [
         {
            path: "composer",
            canActivate: [CanActivateComposerService],
            loadChildren: () => import("./composer/composer-app.module").then(m => m.ComposerAppModule)
         },
         {
            path: "portal",
            loadChildren: () => import("./portal/portal-app.module").then(m => m.PortalAppModule)
         },
         {
            path: "viewer",
            loadChildren: () => import("./viewer/viewer-app.module").then(m => m.ViewerAppModule)
         },
         {
            path: "embed/chart",
            loadChildren: () => import("./embed/chart/embed-chart.module").then(m => m.EmbedChartModule)
         },
         {
            path: "**",
            redirectTo: "portal"
         }
      ]}
];

@NgModule({
   imports: [RouterModule.forRoot(routes, { onSameUrlNavigation: "reload" })],
   exports: [RouterModule]
})
export class AppRoutingModule {
}
