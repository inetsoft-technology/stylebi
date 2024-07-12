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
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { SliderLabelPane } from "./slider-label-pane.component";
import { SliderLabelPaneModel } from "../model/slider-label-pane-model";

describe("Slider Label Pane Unit Test", () => {
   let sliderModel: () => SliderLabelPaneModel = () => {
      return {
         tick: false,
         currentValue: true,
         showLabel: true,
         label: false,
         minimum: false,
         maximum: false
      };
   };
   let fixture: ComponentFixture<SliderLabelPane>;
   let sliderLabelPane: SliderLabelPane;

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [
            ReactiveFormsModule, FormsModule, NgbModule
         ],
         declarations: [SliderLabelPane],
      }).compileComponents();
   });

   //Bug #21009
   it("the checkbox status should be right on Slider Label Pane", () => {
      fixture = TestBed.createComponent(SliderLabelPane);
      sliderLabelPane = <SliderLabelPane>fixture.componentInstance;
      sliderLabelPane.model = sliderModel();
      fixture.detectChanges();

      let lblCheckbox: HTMLInputElement = fixture.nativeElement.querySelector(".label_checkbox_id input[type=checkbox]");
      let minCheckbox: HTMLInputElement = fixture.nativeElement.querySelector(".min_checkbox_id input[type=checkbox]");
      let maxCheckbox: HTMLInputElement = fixture.nativeElement.querySelector(".max_checkbox_id input[type=checkbox]");
      expect(lblCheckbox.attributes["ng-reflect-is-disabled"].value).toEqual("true");
      expect(minCheckbox.attributes["ng-reflect-is-disabled"].value).toEqual("true");
      expect(maxCheckbox.attributes["ng-reflect-is-disabled"].value).toEqual("true");

      let tickCheck: HTMLInputElement = fixture.nativeElement.querySelector(".tick_checkbox_id input[type=checkbox]");
      tickCheck.checked = true;
      tickCheck.dispatchEvent(new Event("change"));
      fixture.detectChanges();

      lblCheckbox = fixture.nativeElement.querySelector(".label_checkbox_id input[type=checkbox]");
      minCheckbox = fixture.nativeElement.querySelector(".min_checkbox_id input[type=checkbox]");
      maxCheckbox = fixture.nativeElement.querySelector(".max_checkbox_id input[type=checkbox]");
      expect(lblCheckbox.attributes["ng-reflect-is-disabled"].value).toEqual("false");
      expect(minCheckbox.attributes["ng-reflect-is-disabled"].value).toEqual("false");
      expect(maxCheckbox.attributes["ng-reflect-is-disabled"].value).toEqual("false");

      lblCheckbox.checked = true;
      lblCheckbox.dispatchEvent(new Event("change"));
      fixture.detectChanges();

      minCheckbox = fixture.nativeElement.querySelector(".min_checkbox_id input[type=checkbox]");
      maxCheckbox = fixture.nativeElement.querySelector(".max_checkbox_id input[type=checkbox]");
      expect(minCheckbox.attributes["ng-reflect-is-disabled"].value).toEqual("true");
      expect(minCheckbox.attributes["ng-reflect-model"].value).toEqual("true");
      expect(maxCheckbox.attributes["ng-reflect-is-disabled"].value).toEqual("true");
      expect(maxCheckbox.attributes["ng-reflect-model"].value).toEqual("true");
   });
});