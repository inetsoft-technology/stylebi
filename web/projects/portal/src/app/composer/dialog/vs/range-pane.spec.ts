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
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModal, NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { DropDownTestModule } from "../../../common/test/test-module";
import { ColorEditor } from "../../../widget/color-picker/color-editor.component";
import { FixedDropdownDirective } from "../../../widget/fixed-dropdown/fixed-dropdown.directive";
import { RangePaneModel } from "../../data/vs/range-pane-model";
import { RangePane } from "./range-pane.component";

describe("Range pane Test", () => {
   const createModel: () => RangePaneModel = () => {
      return {
         gradient: true,
         rangeValues: ["35", "70", "100", "", ""],
         rangeColorValues: [null, null, null, null, null, null]
      };
   };

   let fixture: ComponentFixture<RangePane>;
   let rangePane: RangePane;

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [
            FormsModule, ReactiveFormsModule, NgbModule, DropDownTestModule
         ],
         declarations: [
            RangePane, FixedDropdownDirective, ColorEditor
         ],
         providers: [
            NgbModal
         ],
         schemas: [NO_ERRORS_SCHEMA]
      });
      TestBed.compileComponents();
   });

   //Bug #18751 range input check
   xit("range input check", () => {
      fixture = TestBed.createComponent(RangePane);
      rangePane = <RangePane> fixture.componentInstance;
      rangePane.model = createModel();
      fixture.detectChanges();

      let ranges = fixture.nativeElement.querySelectorAll(
         "div.dynamic-combo-box-body.w-100.input-group input");
      let range5 = ranges[0];

      range5.value = "aa";
      range5.dispatchEvent(new Event("input"));
      fixture.detectChanges();

      expect(range5.getAttribute("ng-reflect-model")).not.toBe("aa");
   });
});