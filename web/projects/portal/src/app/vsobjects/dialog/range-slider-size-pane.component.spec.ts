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
import { ComponentFixture, TestBed, async } from "@angular/core/testing";
import { By } from "@angular/platform-browser";
import { DebugElement } from "@angular/core";
import { FormsModule, ReactiveFormsModule, FormGroup } from "@angular/forms";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { RangeSliderSizePane } from "./range-slider-size-pane.component";
import { RangeSliderSizePaneModel } from "../model/range-slider-size-pane-model";
import { RangeSliderDataPaneModel } from "../model/range-slider-data-pane-model";
import { TestUtils } from "../../common/test/test-utils";

let createModel = () => <RangeSliderSizePaneModel> {
   length: 3,
   logScale: false,
   upperInclusive: true,
   rangeType: 2,
   rangeSize: 5,
   maxRangeSize: 0
};

let createDataModel = () => <RangeSliderDataPaneModel> {
   composite: false
};

describe("Range Slider Size Pane Component Unit Test:", () => {
   let model: RangeSliderSizePaneModel;
   let dataModel: RangeSliderDataPaneModel;
   let fixture: ComponentFixture<RangeSliderSizePane>;
   let de: DebugElement;
   let el: HTMLSelectElement;

   beforeEach(async(() => {
      TestBed.configureTestingModule({
         imports: [ReactiveFormsModule, FormsModule, NgbModule],
         declarations: [RangeSliderSizePane]
      });
      TestBed.compileComponents();

      fixture = TestBed.createComponent(RangeSliderSizePane);
      model = createModel();
      dataModel = createDataModel();
      fixture.componentInstance.model = model;
      fixture.componentInstance.dataModel = dataModel;
      fixture.componentInstance.form = new FormGroup({});
      fixture.detectChanges();
   }));

   it("should instantiate RangeSliderSizePane comboBox with string Month", async(() => {
      fixture.whenStable().then(() => {
         de = fixture.debugElement.query(By.css("select.form-control"));
         el = de.nativeElement;
         expect(TestUtils.toString((<HTMLOptionElement>el.options[el.selectedIndex]).text)).toBe("Month");

         fixture.componentInstance.model.rangeType = 16;
         fixture.detectChanges();

         fixture.whenStable().then(() => {
            expect(TestUtils.toString((<HTMLOptionElement>el.options[el.selectedIndex]).text)).toBe("Day");
         });
      });
   }));

   //bug #18465, #18469, slider size input check
   xit("slider size input check", async(() => { // broken test
      let sliderSize = fixture.debugElement.query(By.css("input#length")).nativeElement;
      sliderSize.value = "0.75";
      sliderSize.dispatchEvent(new Event("input"));
      fixture.detectChanges();
      let warning1 = fixture.debugElement.query(By.css("div.alert.alert-danger")).nativeElement;
      expect(warning1.textContent).toContain(
         "_#(js:viewer.viewsheet.timeSlider.sliderSizeWarning)");

      sliderSize.value = "5";
      sliderSize.dispatchEvent(new Event("input"));
      fixture.detectChanges();
      let warning2 = fixture.debugElement.query(By.css("div.alert.alert-danger")).nativeElement;
      expect(warning2).toBeNull();

      sliderSize.value = "";
      sliderSize.dispatchEvent(new Event("input"));
      fixture.detectChanges();
      let warning3 = fixture.debugElement.query(By.css("div.alert.alert-danger")).nativeElement;
      expect(warning3.textContent).toContain(
         "_#(js:viewer.viewsheet.timeSlider.sliderSizeWarning)");
   }));

   //Bug #19076 Bug #19079
   it("check max/min range size status", (done) => {
      //Bug #19079
      fixture.componentInstance.model.rangeType = 3;
      let minRangeSize = fixture.debugElement.query(By.css("#rangeSize")).nativeElement;
      let maxRangeSize = fixture.debugElement.query(By.css("#maxRangeSize")).nativeElement;
      fixture.detectChanges();
      expect(minRangeSize.disabled).toBeTruthy();
      expect(maxRangeSize.disabled).toBeFalsy();

      //Bug #19076
      let logari: HTMLInputElement = fixture.debugElement.query(By.css("input.logScale-cb_id")).nativeElement;
      logari.checked = true;
      logari.dispatchEvent(new Event("select"));
      logari.dispatchEvent(new Event("change"));
      fixture.detectChanges();
      fixture.whenStable().then(() => {
         expect(minRangeSize.disabled).toBeTruthy();
         expect(maxRangeSize.disabled).toBeTruthy();
         done();
      });
   });
});
