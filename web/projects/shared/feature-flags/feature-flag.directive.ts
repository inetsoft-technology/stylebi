/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { Directive, Input, OnInit, TemplateRef, ViewContainerRef } from "@angular/core";
import { FeatureFlagsService, FeatureFlagValue } from "./feature-flags.service";

/**
 * A directive that can be used on ng-container elements as such:
 *
 *   <ng-container *featureFlag="FeatureFlag.FEATURE_FLAG_VALUE">
 *       <span>Feature related content here</span>
 *   </ng-container>
 *
 * Content can be included only if a flags is not enabled like:
 *
 *   <ng-container *featureFlag="FeatureFlag.FEATURE_FLAG_VALUE; enabled: false">
 *       <span>Feature related content here</span>
 *   </ng-container>
 */
@Directive({
   selector: "[featureFlag]"
})
export class FeatureFlagDirective implements OnInit {
   @Input() featureFlag: FeatureFlagValue;
   @Input() featureFlagEnabled: boolean = true;

   constructor(private templateRef: TemplateRef<any>, private viewContainerRef: ViewContainerRef,
               private featureFlagService: FeatureFlagsService)
   {
   }

   ngOnInit() {
      const enabled = this.featureFlagService.isFeatureEnabled(this.featureFlag);
      const negated = !this.featureFlagEnabled;

      if(!negated && enabled || negated && !enabled) {
         this.viewContainerRef.createEmbeddedView(this.templateRef);
      }
   }
}