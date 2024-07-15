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
import { NO_ERRORS_SCHEMA, NgModule } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { ColorPicker } from "./color-picker.component";
import { ColorPane } from "./cp-color-pane.component";
import { FixedDropdownDirective } from "../fixed-dropdown/fixed-dropdown.directive";
import { FixedDropdownService } from "../fixed-dropdown/fixed-dropdown.service";
import { DropDownTestModule } from "../../common/test/test-module";

describe("Color Picker Unit Test", () => {
   let fixture: ComponentFixture<ColorPicker>;
   let colorPicker: ColorPicker;

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [
            ReactiveFormsModule, FormsModule, NgbModule, DropDownTestModule
         ],
         declarations: [
            ColorPicker, ColorPane, FixedDropdownDirective
         ],
         providers: [ FixedDropdownService ],
         schemas: [NO_ERRORS_SCHEMA]
      }).compileComponents();

      fixture = TestBed.createComponent(ColorPicker);
      colorPicker = <ColorPicker>fixture.componentInstance;
   });

   //Bug #19987
   it("default color is not right", () => {
      fixture.detectChanges();
      let colorSpan: Element = fixture.nativeElement.querySelector(".color-picker button > span");
      expect(colorSpan.attributes["style"].value).toContain("background-color: rgb(0, 0, 0)");
   });
});