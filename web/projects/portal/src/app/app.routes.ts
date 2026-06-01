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
import { canActivateRoot } from "./can-activate-root.service";
import { canActivateComposer } from "./composer/services/can-activate-composer.service";

export const routes: Routes = [
   {
      path: "",
      canActivate: [canActivateRoot],
      children: [
         {
            path: "composer",
            canActivate: [canActivateComposer],
            loadChildren: () => import("./composer/composer.routes").then(m => m.composerRoutes)
         },
         {
            path: "portal",
            loadChildren: () => import("./portal/portal.routes").then(m => m.portalRoutes)
         },
         {
            path: "viewer",
            loadChildren: () => import("./viewer/viewer.routes").then(m => m.viewerRoutes)
         },
         {
            path: "embed/chart",
            loadChildren: () => import("./embed/chart/embed-chart.routes").then(m => m.embedChartRoutes)
         },
         {
            path: "**",
            redirectTo: "portal"
         }
      ]}
];
