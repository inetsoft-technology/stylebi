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
import { Routes, UrlSegment } from "@angular/router";
import { canDeactivateGuard } from "../../common/services/can-deactivate-guard.service";
import { createEmbedUrlMatcher } from "../embed-url-matcher";
import { EmbedGaugeComponent } from "./embed-gauge.component";

export function EMBED_GAUGE_URL_MATCHER(url: UrlSegment[]) {
   return createEmbedUrlMatcher(url);
}

export const embedGaugeRoutes: Routes = [
   {
      component: EmbedGaugeComponent,
      canDeactivate: [canDeactivateGuard],
      matcher: EMBED_GAUGE_URL_MATCHER
   }
];
