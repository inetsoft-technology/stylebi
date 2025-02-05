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
import { CommonModule } from "@angular/common";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormGroup, FormsModule, ReactiveFormsModule } from "@angular/forms";
import { By } from "@angular/platform-browser";
import { NgbModal, NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { ColorEditor } from "../../widget/color-picker/color-editor.component";
import { ColorComponentEditor } from "../../widget/color-picker/color-component-editor.component";
import { ColorEditorDialog } from "../../widget/color-picker/color-editor-dialog.component";
import { ColorMap } from "../../widget/color-picker/color-map.component";
import { ColorPicker } from "../../widget/color-picker/color-picker.component";
import { GradientColorPicker } from "../../widget/color-picker/gradient-color-picker.component";
import { GradientColorPane } from "../../widget/color-picker/gradient-color-pane.component";
import { GradientColorItem } from "../../widget/color-picker/gradient-color-item.component";
import { ColorSlider } from "../../widget/color-picker/color-slider.component";
import { ColorPane } from "../../widget/color-picker/cp-color-pane.component";
import { RecentColorService } from "../../widget/color-picker/recent-color.service";
import { FixedDropdownDirective } from "../../widget/fixed-dropdown/fixed-dropdown.directive";
import { AxisLinePaneModel } from "../model/dialog/axis-line-pane-model";
import { AxisLinePane } from "./axis-line-pane.component";
import { ActionsContextmenuAnchorDirective } from "../../widget/fixed-dropdown/actions-contextmenu-anchor.directive";
import { DropDownTestModule } from "../../common/test/test-module";
import { TestUtils } from "../../common/test/test-utils";

let createModel: () => AxisLinePaneModel = () => {
   return {
      ignoreNull: true,
      truncate: true,
      logarithmicScale: true,
      showAxisLine: true,
      showAxisLineEnabled: true,
      showTicks: true,
      lineColorEnabled: true,
      reverse: true,
      shared: true,
      lineColor: "#ffffff",
      minimum: 0,
      maximum: 100,
      minorIncrement: 20,
      increment: 20,
      axisType: ""
   };
};

describe("AxisLinePane Unit Tests", () => {
   let fixture: ComponentFixture<AxisLinePane>;

   beforeEach(async(() => {
      TestBed.configureTestingModule({
         imports: [
            CommonModule,
            NgbModule,
            FormsModule,
            ReactiveFormsModule,
            DropDownTestModule
         ],
         declarations: [
            AxisLinePane,
            ActionsContextmenuAnchorDirective,
            ColorEditor,
            ColorPicker,
            GradientColorPicker,
            GradientColorPane,
            GradientColorItem,
            ColorEditorDialog,
            ColorMap,
            ColorSlider,
            ColorComponentEditor,
            ColorPane,
            FixedDropdownDirective
         ],
         providers: [
            NgbModal,
            RecentColorService
         ]
      });
      TestBed.compileComponents();

      fixture = TestBed.createComponent(AxisLinePane);
      fixture.componentInstance.model = createModel();
      fixture.componentInstance.form = new FormGroup({});
   }));

   // Bug #10087 Truncate variable does not update
   it("should toggle truncate variable", (done) => {
      fixture.componentInstance.linear = false;
      fixture.componentInstance.model.truncate = false;
      fixture.detectChanges();
      let truncateInput: any = fixture.nativeElement.querySelector(
         ".truncate_long_label_id");
      truncateInput.click();
      fixture.detectChanges();

      fixture.whenStable().then(() => {
         expect(fixture.componentInstance.model.truncate).toBe(true);
         done();
      });
   });

   // Bug #16369 Use correct checks for axis line pane elements visibility
   // Bug #19013 major increment should not allow negative number
   // Bug #21133 major increment should allow float number
   it("should only have major increment visible", () => { // broken test
      fixture.componentInstance.model.truncate = false;
      fixture.componentInstance.linear = false;
      fixture.componentInstance.timeSeries = true;
      fixture.componentInstance.outer = false;
      fixture.componentInstance.incrementValid = true;
      fixture.detectChanges();

      let minimum: any = fixture.debugElement.query(By.css("[id=minimum]"));
      let maximum = fixture.debugElement.query(By.css("[id=maximum]"));
      let minorIncrement = fixture.debugElement.query(By.css("[id=minorIncrement]"));
      let majorIncrement = fixture.debugElement.query(By.css("[id=majorIncrement]")).nativeElement;

      expect(minimum).toBeFalsy();
      expect(maximum).toBeFalsy();
      expect(minorIncrement).toBeFalsy();
      expect(majorIncrement).toBeTruthy();

      majorIncrement.value = "-2";
      majorIncrement.dispatchEvent(new Event("input"));
      fixture.detectChanges();

      let warningsHTML = [];
      fixture.nativeElement.querySelectorAll("div.alert.alert-danger")
         .forEach((warning) => warningsHTML.push(TestUtils.toString(warning.textContent.trim())));
      expect(warningsHTML).toContain("viewer.viewsheet.numberRange.notzeroWarning");

      majorIncrement.value = "0.5";
      majorIncrement.dispatchEvent(new Event("input"));
      fixture.detectChanges();

      let warnings = fixture.debugElement.query(By.css("div.alert.alert-danger"));
      expect(warnings).toBeNull();
   });

   // Bug #18322 should disable major increment when logarithmic scale
   it("should disable major increment when logarithmic scale", (done) => {
      fixture.componentInstance.linear = true;
      fixture.detectChanges();

      fixture.whenStable().then(() => {
         let majorIncre = fixture.debugElement.query(By.css("[id=majorIncrement]")).nativeElement;
         expect(majorIncre.hasAttribute("disabled")).toBeTruthy();
         done();
      });
   });

   // Bug #19084 minor increment should not allow negative number
   // Bug #18317 maximum should be greater than minimum
   it("check value input", () => {
      fixture.componentInstance.linear = true;
      fixture.detectChanges();

      let minorIncre = fixture.debugElement.query(By.css("[id=minorIncrement]")).nativeElement;
      let minimum: any = fixture.debugElement.query(By.css("[id=minimum]")).nativeElement;
      let maximum = fixture.debugElement.query(By.css("[id=maximum]")).nativeElement;
      minorIncre.value = "-2";
      minorIncre.dispatchEvent(new Event("input"));
      minimum.value = "100";
      minimum.dispatchEvent(new Event("input"));
      maximum.value = "50";
      maximum.dispatchEvent(new Event("input"));
      fixture.detectChanges();

      let warningsHTML = [];
      fixture.nativeElement.querySelectorAll("div.alert.alert-danger")
         .forEach((warning) => warningsHTML.push(TestUtils.toString(warning.textContent.trim())));
      expect(warningsHTML).toContain("viewer.viewsheet.numberRange.incrementWarning");
      expect(warningsHTML).toContain("viewer.viewsheet.numberRange.maxMinWarning");
   });

   //Bug #21089
   it("options status on axis line pane", () => {
      fixture.componentInstance.model.axisType = "top_x_axis";
      fixture.componentInstance.model.showTicks = false;
      fixture.componentInstance.model.ignoreNull = false;
      fixture.componentInstance.linear = false;
      fixture.componentInstance.outer = true;
      fixture.componentInstance.timeSeries = false;
      fixture.componentInstance.incrementValid = true;
      fixture.componentInstance.minmaxValid = true;
      fixture.detectChanges();

      let showAxisLine: HTMLInputElement = fixture.debugElement.query(By.css(".show_axis_line_id input[type=checkbox]")).nativeElement;
      let showTicks: HTMLInputElement = fixture.debugElement.query(By.css(".show_ticks_id input[type=checkbox]")).nativeElement;
      let axis_ticks: HTMLElement = fixture.nativeElement.querySelectorAll("fieldset")[1];
      expect(showAxisLine.attributes["ng-reflect-is-disabled"].value).toEqual("false");
      expect(showTicks.attributes["ng-reflect-is-disabled"].value).toEqual("true");
      expect(axis_ticks.hasAttribute("disabled")).toBeFalsy();
   });
});