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
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModal, NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { DropDownTestModule } from "../../../common/test/test-module";
import { ColorEditor } from "../../../widget/color-picker/color-editor.component";
import { DynamicComboBox } from "../../../widget/dynamic-combo-box/dynamic-combo-box.component";
import { FixedDropdownDirective } from "../../../widget/fixed-dropdown/fixed-dropdown.directive";
import { RangePaneModel } from "../../data/vs/range-pane-model";
import { RangePaneValueModel } from "../../data/vs/range-pane-value-model";
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
            RangePane, FixedDropdownDirective, ColorEditor, DynamicComboBox
         ],
         providers: [
            NgbModal
         ],
         schemas: [NO_ERRORS_SCHEMA]
      });
      TestBed.compileComponents();
   });

   //Bug #18751 range input check
   it("range input check", () => {
      fixture = TestBed.createComponent(RangePane);
      rangePane = <RangePane> fixture.componentInstance;
      rangePane.model = createModel();
      fixture.componentInstance.values = [];
      fixture.componentInstance.values.push(new RangePaneValueModel(rangePane.model, 0))
      fixture.detectChanges();

      let ranges = fixture.debugElement.nativeElement.querySelectorAll(
         "div.dynamic-combo-box-body.w-100.input-group input");
      let range5 = ranges[0];

      range5.value = "aa";
      range5.dispatchEvent(new Event("input"));
      fixture.detectChanges();

      expect(range5.getAttribute("ng-reflect-model")).not.toBe("aa");
   });
});