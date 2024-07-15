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
import { HttpClientTestingModule, HttpTestingController } from "@angular/common/http/testing";
import { inject, TestBed } from "@angular/core/testing";
import { FEATURE_FLAGS_URI, FeatureFlagsService, FeatureFlagValue } from "./feature-flags.service";

describe('feature-flags.service', () => {
   let httpTestingController: HttpTestingController;

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [ HttpClientTestingModule ],
         providers: [ FeatureFlagsService ]
      })

      httpTestingController = TestBed.inject(HttpTestingController);
   });

   it("present feature should be enabled", inject([FeatureFlagsService], (service: FeatureFlagsService) => {
      service.loadConfig().then((flags: any) => {
         expect(flags).toBeTruthy();
      });

      const request = httpTestingController.expectOne(FEATURE_FLAGS_URI);
      request.flush({
         enabledFeatures: [ "FEATURE_TEST_1", "FEATURE_TEST_2" ]
      });

      httpTestingController.verify();

      expect(service.isFeatureEnabled(FeatureFlagValue.FEATURE_TEST_1)).toBeTruthy();
   }));

   it("missing feature should be disabled", inject([FeatureFlagsService], (service: FeatureFlagsService) => {
      service.loadConfig().then((flags: any) => {
         expect(flags).toBeTruthy();
      });

      const request = httpTestingController.expectOne(FEATURE_FLAGS_URI);
      request.flush({
         enabledFeatures: [ "FEATURE_TEST_1" ]
      });

      httpTestingController.verify();

      expect(service.isFeatureEnabled(FeatureFlagValue.FEATURE_TEST_2)).toBeFalsy();
   }));
});