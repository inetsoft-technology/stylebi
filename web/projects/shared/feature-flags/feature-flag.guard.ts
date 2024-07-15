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
import { Injectable } from "@angular/core";
import {CanLoad, Route, Router, UrlSegment, UrlTree} from "@angular/router";
import { Observable } from "rxjs";
import { FeatureFlagsService } from "./feature-flags.service";

/**
 * FeatureFlagGuard, can be set on the route as such:
 *
 * {
 *    path: "some-route",
 *    loadChildren: () => import("app/new-feature/some-app.module").then(m => m.SomeAppModule),
 *    canLoad: [FeatureFlagGuard],
 *    data: {
 *       feature: FeatureFlagValue.FEATURE_FLAG_VALUE
 *    }
 * }
 */
@Injectable({
   providedIn: 'root',
})
export class FeatureFlagGuard implements CanLoad {
   private featureFlagsService: FeatureFlagsService;

   constructor(featureFlagsService: FeatureFlagsService, private router: Router) {
      this.featureFlagsService = featureFlagsService;
   }

   canLoad(route: Route, segments: UrlSegment[]): | Observable<boolean | UrlTree>
      | Promise<boolean | UrlTree> | boolean | UrlTree
   {
      const feature = (<any>route)?.data?.feature;

      if(!!feature) {
         const isEnabled = this.featureFlagsService.isFeatureEnabled(feature);
         const negated = ((route as any)?.data?.featureEnabled) === false;

         if(isEnabled && !negated || !isEnabled && negated) {
            return true;
         }
      }

      this.router.navigate(['/']);
      return false;
   }
}
