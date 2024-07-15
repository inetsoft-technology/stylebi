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
import { APP_INITIALIZER, NgModule } from "@angular/core";
import { FeatureFlagDirective } from "./feature-flag.directive";
import { FeatureFlagsService } from "./feature-flags.service";

const featureFactory = (featureFlagsService: FeatureFlagsService) => () =>
   featureFlagsService.loadConfig();

@NgModule({
   providers: [{
      provide: APP_INITIALIZER,
      useFactory: featureFactory,
      deps: [FeatureFlagsService],
      multi: true
   }],
   exports: [FeatureFlagDirective],
   declarations: [FeatureFlagDirective]
})
export class FeatureFlagsModule {
}