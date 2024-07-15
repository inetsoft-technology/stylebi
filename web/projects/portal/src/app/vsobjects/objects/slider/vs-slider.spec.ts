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
import { CommonModule } from "@angular/common";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { TestUtils } from "../../../common/test/test-utils";
import { StompClientService, ViewsheetClientService } from "../../../common/viewsheet-client";
import { DebounceService } from "../../../widget/services/debounce.service";
import { ContextProvider } from "../../context-provider.service";
import { SafeFontDirective } from "../../directives/safe-font.directive";
import { CheckFormDataService } from "../../util/check-form-data.service";
import { FormInputService } from "../../util/form-input.service";
import { DataTipService } from "../data-tip/data-tip.service";
import { PopComponentService } from "../data-tip/pop-component.service";
import { VSPopComponentDirective } from "../data-tip/vs-pop-component.directive";
import { VSSlider } from "./vs-slider.component";

describe("VSSlider Unit Tests", () => {
   let stompClient: any;
   let dataTipService: any;
   let fixture: ComponentFixture<VSSlider>;

   beforeEach(async(() => {
      stompClient = TestUtils.createMockStompClientService();
      dataTipService = {isDataTip: jest.fn()};
      const contextProvider = {};
      const formDataService = {
         checkFormData: jest.fn(),
         removeObject: jest.fn(),
         addObject: jest.fn(),
         replaceObject: jest.fn()
      };

      TestBed.configureTestingModule({
         imports: [
            CommonModule,
            NgbModule
         ],
         declarations: [
            VSSlider, VSPopComponentDirective, SafeFontDirective
         ],
         schemas: [NO_ERRORS_SCHEMA],
         providers: [
            { provide: StompClientService, useValue: stompClient },
            { provide: CheckFormDataService, useValue: formDataService },
            FormInputService,
            PopComponentService,
            ViewsheetClientService,
            DebounceService,
            { provide: DataTipService, useValue: dataTipService },
            { provide: ContextProvider, useValue: contextProvider },
         ]
      });
      TestBed.compileComponents();
   }));

   beforeEach(() => {
      fixture = TestBed.createComponent(VSSlider);
      fixture.componentInstance.model = TestUtils.createMockVSSliderModel("slider1");
      fixture.detectChanges();
   });

   // Bug #10226 Current Value label should not show when currentVisible set to false
   it("should not have current value label", () => {
      let currentLabel: any = fixture.nativeElement.querySelector(".slider-value");
      expect(currentLabel).toBeTruthy();

      fixture.componentInstance.model.currentVisible = false;
      fixture.detectChanges();
      currentLabel = fixture.nativeElement.querySelector(".slider-value");
      expect(currentLabel).toBeFalsy();
   });

   //Bug #18430 apply text-decoration to vs-slider value label and tick labels
   it("should use right format on tick label and value label on slider", () => {
      fixture.componentInstance.model.ticksVisible = true;
      fixture.componentInstance.model.labelVisible = true;
      fixture.componentInstance.model.currentVisible = true;
      fixture.componentInstance.model.currentLabel = "10";
      fixture.componentInstance.model.objectFormat.decoration = "underline line-through";

      fixture.detectChanges();
      let valueLabel = fixture.nativeElement.querySelector(".slider-value");
      let tickLabel = fixture.nativeElement.querySelectorAll(".slider-label")[0];
      expect(valueLabel.style["text-decoration"]).toEqual("underline line-through");
      expect(tickLabel.style["text-decoration"]).toEqual("underline line-through");
   });

   //Bug #20993 should display in correct position
   it("should display in correct position", () => {
      let model = TestUtils.createMockVSSliderModel("slider1");
      model.objectFormat.height = 89;
      model.objectFormat.top = 210;
      model.objectFormat.left = 401;
      model.objectFormat.width = 140;

      fixture.componentInstance.model = model;
      fixture.detectChanges();

      let slider = fixture.nativeElement.querySelector("div.wrapper");
      expect(slider.style["top"]).toBe("45px");
   });
});
