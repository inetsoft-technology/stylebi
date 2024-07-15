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
import { Component } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { By } from "@angular/platform-browser";
import { FeatureFlagDirective } from "./feature-flag.directive";
import { FeatureFlagsService, FeatureFlagValue } from "./feature-flags.service";

@Component({
   selector: "feature-flag-directive-test",
   template: `
      <ng-container *featureFlag="FeatureFlagValue.FEATURE_TEST_1">
         <span class="feature-flag-enabled-span">CREATED</span>
      </ng-container>
      <ng-container *featureFlag="FeatureFlagValue.FEATURE_TEST_2">
         <span class="feature-flag-disabled-span">NOT CREATED</span>
      </ng-container>
      <ng-container *featureFlag="FeatureFlagValue.FEATURE_TEST_1; enabled: false">
         <span class="feature-flag-negated-enabled-span">NOT CREATED</span>
      </ng-container>
      <ng-container *featureFlag="FeatureFlagValue.FEATURE_TEST_2; enabled: false">
         <span class="feature-flag-negated-disabled-span">CREATED</span>
      </ng-container>
   `
})
class FeatureFlagTestComponent {
   FeatureFlagValue = FeatureFlagValue;
}

describe("feature-flag.directive", () => {
   let testComponent: ComponentFixture<FeatureFlagTestComponent>;
   let featureFlagsService: any = {isFeatureEnabled: jest.fn()};

   featureFlagsService.isFeatureEnabled.mockImplementation((name) => name == FeatureFlagValue.FEATURE_TEST_1);

   beforeEach(() => {
      TestBed.configureTestingModule({
         declarations: [FeatureFlagDirective, FeatureFlagTestComponent],
         providers: [
            {provide: FeatureFlagsService, useValue: featureFlagsService}
         ]
      }).compileComponents();

      testComponent = TestBed.createComponent(FeatureFlagTestComponent);
   });

   it("span of enabled feature should be created", () => {
      testComponent.detectChanges();

      let span = testComponent.debugElement.query(By.css(".feature-flag-enabled-span"));
      expect(span).toBeTruthy();
      let spanContent = span.nativeElement.textContent;
      expect(spanContent).toBe("CREATED");
   });

   it("span of disabled feature should not be created", () => {
      testComponent.detectChanges();

      let span = testComponent.debugElement.query(By.css(".feature-flag-disabled-span"));
      expect(span).toBeNull();
   });

   it("span of enabled feature should not be created when enabled is false", () => {
      testComponent.detectChanges();

      let span = testComponent.debugElement.query(By.css(".feature-flag-negated-enabled-span"));
      expect(span).toBeNull();
   });

   it("span of disabled feature should be created when enabled is false", () => {
      testComponent.detectChanges();

      let span = testComponent.debugElement.query(By.css(".feature-flag-negated-disabled-span"));
      expect(span).toBeTruthy();
      let spanContent = span.nativeElement.textContent;
      expect(spanContent).toBe("CREATED");
   });
});
