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

import { ChangeDetectorRef, NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { By } from "@angular/platform-browser";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { DropDownTestModule } from "../../common/test/test-module";
import { TestUtils } from "../../common/test/test-utils";
import { EnterClickDirective } from "../directive/enter-click.directive";
import { FixedDropdownDirective } from "../fixed-dropdown/fixed-dropdown.directive";
import { TooltipIfDirective } from "../tooltip/tooltip-if.directive";
import { DomService } from "../dom-service/dom.service";
import { ComboMode } from "../dynamic-combo-box/dynamic-combo-box-model";
import { DynamicComboBox } from "../dynamic-combo-box/dynamic-combo-box.component";
import { TooltipDirective } from "../tooltip/tooltip.directive";
import { TreeDropdownComponent } from "../tree/tree-dropdown.component";
import { TreeNodeComponent } from "../tree/tree-node.component";
import { TreeSearchPipe } from "../tree/tree-search.pipe";
import { TreeComponent } from "../tree/tree.component";
import { LabelInputField } from "./label-input-field.component";
import { TargetComboBox } from "./target-combo-box.component";

describe("Label Input Field Test", () => {
   let labelInputField: LabelInputField;
   let changeDetectorRef: any;
   let domService: any;
   let fixture: ComponentFixture<LabelInputField>;

   beforeEach(() => {
      changeDetectorRef = { detectChanges: jest.fn() };
      domService = {
         requestRead: jest.fn(),
         cancelAnimationFrame: jest.fn()
      };

      TestBed.configureTestingModule({
         imports: [DropDownTestModule, ReactiveFormsModule, FormsModule, NgbModule],
         declarations: [
            LabelInputField, DynamicComboBox, TargetComboBox, TreeComponent,
            TreeNodeComponent, TreeDropdownComponent, FixedDropdownDirective,
            TreeSearchPipe, TooltipDirective, EnterClickDirective, TooltipIfDirective
         ],
         providers: [
            {provide: ChangeDetectorRef, useValue: changeDetectorRef},
            {provide: DomService, useValue: domService}
         ],
         schemas: [NO_ERRORS_SCHEMA]
      }).compileComponents();

      fixture = TestBed.createComponent(LabelInputField);
   });

   // Bug #10186 make target labels uneditable
   it("should not be an editable label", () => {
      labelInputField = new LabelInputField(changeDetectorRef);
      labelInputField.label = labelInputField.dataProviders[1].value;
      let editable: boolean = labelInputField.isLabelEditable();

      expect(editable).toBeFalsy();
   });

   //Bug #19879
   it("should return right value when change variable to value", () => {
      labelInputField = <LabelInputField>fixture.componentInstance;
      labelInputField.label = "${var1}";
      labelInputField.variables = ["${var1}", "${var2}"];
      fixture.detectChanges();

      labelInputField.onTypeChange(ComboMode.VALUE);
      fixture.detectChanges();

      let labelInput = fixture.debugElement.query(By.css("div.dynamic-combo-box-body .custom-select")).nativeElement;
      expect(TestUtils.toString((labelInput.textContent).trim())).toEqual("_#(js:Target Value)");
   });
});
