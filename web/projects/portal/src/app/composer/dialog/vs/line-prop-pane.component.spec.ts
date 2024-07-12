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
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { DropDownTestModule } from "../../../common/test/test-module";
import { TestUtils } from "../../../common/test/test-utils";
import { ColorEditor } from "../../../widget/color-picker/color-editor.component";
import { ColorPicker } from "../../../widget/color-picker/color-picker.component";
import { ColorPane } from "../../../widget/color-picker/cp-color-pane.component";
import { FixedDropdownDirective } from "../../../widget/fixed-dropdown/fixed-dropdown.directive";
import { ComboMode } from "../../../widget/dynamic-combo-box/dynamic-combo-box-model";
import { DynamicComboBox } from "../../../widget/dynamic-combo-box/dynamic-combo-box.component";
import { StyleDropdown } from "../../../widget/format/style-dropdown.component";
import { TargetComboBox } from "../../../widget/target/target-combo-box.component";
import { TooltipDirective } from "../../../widget/tooltip/tooltip.directive";
import { TreeDropdownComponent } from "../../../widget/tree/tree-dropdown.component";
import { TreeNodeComponent } from "../../../widget/tree/tree-node.component";
import { TreeSearchPipe } from "../../../widget/tree/tree-search.pipe";
import { TreeComponent } from "../../../widget/tree/tree.component";
import { LinePropPaneModel } from "../../data/vs/line-prop-pane-model";
import { LinePropPane } from "./line-prop-pane.component";

let createModel: () => LinePropPaneModel = () => {
   return {
      color: "Static",
      colorValue: "#555555",
      style: "THIN_LINE"
   };
};

describe("line prop pane component unit case", () => {
   let fixture: ComponentFixture<LinePropPane>;
   let lineProPane: LinePropPane;

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [DropDownTestModule, ReactiveFormsModule, FormsModule, NgbModule],
         declarations: [LinePropPane, ColorEditor, StyleDropdown, ColorPicker, ColorPane, DynamicComboBox, TargetComboBox, TreeComponent, TreeNodeComponent, TreeDropdownComponent, FixedDropdownDirective, TreeSearchPipe, TooltipDirective],
         schemas: [NO_ERRORS_SCHEMA]
      }).compileComponents();

      fixture = TestBed.createComponent(LinePropPane);
   });

   //Bug #20081
   it("should return right status when change value type", () => {
      let model = createModel();
      model.color = "$(var1)";
      lineProPane = <LinePropPane>fixture.componentInstance;
      lineProPane.model = model;
      lineProPane.variables = ["$(var1)", "$(var2)"];
      fixture.detectChanges();

      lineProPane.fixColor(ComboMode.VALUE);
      fixture.detectChanges();
      let colorInput = fixture.nativeElement.querySelector("dynamic-combo-box");
      expect(colorInput.getAttribute("ng-reflect-value")).toBe("Static");
      let colorEditor = fixture.nativeElement.querySelector("div.col-auto.ps-1");
      expect(colorEditor.getAttribute("class")).not.toContain("disable-actions-fade");
      expect(colorEditor.querySelector("color-editor").getAttribute("ng-reflect-color")).toBe("#555555");

      let typeToggle = fixture.nativeElement.querySelector("button.type-toggle");
      typeToggle.click();
      TestUtils.changeDynamicComboValueType(1);
      fixture.detectChanges();
      colorInput = fixture.nativeElement.querySelector("dynamic-combo-box select");
      expect(colorInput.getAttribute("ng-reflect-model")).toBe("$(var1)");
      expect(colorEditor.getAttribute("class")).toContain("disable-actions-fade");

      typeToggle.click();
      TestUtils.changeDynamicComboValueType(2);
      fixture.detectChanges();
      colorInput = fixture.nativeElement.querySelector("dynamic-combo-box input");
      expect(colorInput.getAttribute("ng-reflect-model")).toBe("=");
      expect(colorEditor.getAttribute("class")).toContain("disable-actions-fade");
   });
});