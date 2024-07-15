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
import { HttpClient } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { tap } from "rxjs/operators";

export const FEATURE_FLAGS_URI = "../api/feature-flags";

export interface FeatureFlagsModel {
   enabledFeatures: string[];
}

export enum FeatureFlagValue {
   FEATURE_TEST_1, // used for test cases, not a real flag -- do not delete
   FEATURE_TEST_2, // used for test cases, not a real flag -- do not delete
   FEATURE_51339_AXIS_LABEL_SPACING,
   FEATURE_55166_SCRIPT_FIELD,
   FEATURE_45441_LAYOUTINFO_SCRIPT_TREE_FOLDER,
   FEATURE_46983_DATA_TREE_CONTEXT_MENU,
   FEATURE_58615_CHANGE_QUERY_DATASOURCE,
   FEATURE_61456_SUPPORT_PORTAL_VPM_SUB_QUERY,
   FEATURE_63601_UX_WORKFLOW
}

/**
 * A service that is used to check whether a feature is enabled.
 */
@Injectable({
   providedIn: "root"
})
export class FeatureFlagsService {
   private featureFlags: FeatureFlagValue[];

   constructor(private http: HttpClient) {
   }

   /**
    * We convert it to promise so that this function can be called by the APP_INITIALIZER
    */
   loadConfig(): Promise<any> {
      return this.http
         .get<FeatureFlagsModel>(FEATURE_FLAGS_URI)
         .pipe(tap(data => {
            this.featureFlags = data.enabledFeatures
               .map(k => FeatureFlagValue[k as keyof typeof FeatureFlagValue]);
         }))
         .toPromise();
   }

   /**
    * Check if a feature with the flag is enabled.
    *
    * @param flag feature flag
    */
   public isFeatureEnabled(flag: FeatureFlagValue): boolean {
      return (!!this.featureFlags && this.featureFlags.includes(flag));
   }
}
