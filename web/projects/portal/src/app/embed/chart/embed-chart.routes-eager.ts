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
import { EmbedChartComponent } from "./embed-chart.component";

// Bug #76468: the "elements" web-component bundle (main-elements.ts) already imports
// EmbedChartComponent statically and uses it immediately (createCustomElement) -- it is never
// actually deferred there. But embed-chart.routes.ts's embedChartRoutes (used by the full portal
// app, where the component genuinely is lazy) defines its route via
// `loadComponent: () => import("./embed-chart.component")...`, and esbuild's application builder
// treats that dynamic import() as a hard chunk-split point for *any* build that pulls in the
// module containing it -- even if the specific export using it (embedChartRoutes) is otherwise
// unused and would tree-shake away. main-elements.ts must therefore avoid importing
// embed-chart.routes.ts (or anything that imports it) entirely; this file exists purely so it can
// get an equivalent route with zero `import()` syntax anywhere in its own module graph, so
// EmbedChartComponent's ~1.9MB of code bundles directly into main-elements.ts's main.js instead
// of being split into a separate chunk that the gulp-concatenated elements.js never references or
// copies (which produced a broken, non-functional bundle -- see PR #5007 review).
//
// Keep this file's providers in sync with embed-chart.routes.ts's embedChartRoutes by sharing
// EMBED_CHART_ROUTE_PROVIDERS from embed-chart.route-providers.ts rather than duplicating it.
export const embedChartRoutesEager: Routes = [
   {
      component: EmbedChartComponent,
      canDeactivate: [canDeactivateGuard],
      matcher: EMBED_CHART_URL_MATCHER,
      providers: EMBED_CHART_ROUTE_PROVIDERS
   }
];
