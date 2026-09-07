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
import { canDeactivateGuard } from "../../common/services/can-deactivate-guard.service";
import { EMBED_CHART_URL_MATCHER } from "./embed-chart-url-matcher";
import { EMBED_CHART_ROUTE_PROVIDERS } from "./embed-chart.route-providers";

// Lazily loaded route used by the full portal app (app.routes.ts's `loadChildren` on this whole
// module). NOTE: nothing in the "elements" web-component bundle (main-elements.ts) may import
// this file -- see embed-chart.routes-eager.ts for the equivalent eager route that bundle uses
// instead, and why (Bug #76468: this file's `import()` below is a hard chunk-split point for
// esbuild in *any* build that pulls this module in, whether or not embedChartRoutes itself ends
// up used by that build).
export const embedChartRoutes: Routes = [
   {
      loadComponent: () => import("./embed-chart.component").then(m => m.EmbedChartComponent),
      canDeactivate: [canDeactivateGuard],
      matcher: EMBED_CHART_URL_MATCHER,
      providers: EMBED_CHART_ROUTE_PROVIDERS
   }
];
