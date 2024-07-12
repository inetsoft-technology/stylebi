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
import { NgModule, NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { TestUtils } from "../../../common/test/test-utils";
import { ColorEditor } from "../../../widget/color-picker/color-editor.component";
import { ColorPicker } from "../../../widget/color-picker/color-picker.component";
import { ColorPane } from "../../../widget/color-picker/cp-color-pane.component";
import { FixedDropdownDirective } from "../../../widget/fixed-dropdown/fixed-dropdown.directive";
import { DynamicComboBox } from "../../../widget/dynamic-combo-box/dynamic-combo-box.component";
import { DropdownStackService } from "../../../widget/fixed-dropdown/dropdown-stack.service";
import { FixedDropdownContextmenuComponent } from "../../../widget/fixed-dropdown/fixed-dropdown-contextmenu.component";
import { FixedDropdownComponent } from "../../../widget/fixed-dropdown/fixed-dropdown.component";
import { FixedDropdownService } from "../../../widget/fixed-dropdown/fixed-dropdown.service";
import { AlphaDropdown } from "../../../widget/format/alpha-dropdown.component";
import { TooltipDirective } from "../../../widget/tooltip/tooltip.directive";
import { TreeDropdownComponent } from "../../../widget/tree/tree-dropdown.component";
import { TreeNodeComponent } from "../../../widget/tree/tree-node.component";
import { TreeSearchPipe } from "../../../widget/tree/tree-search.pipe";
import { TreeComponent } from "../../../widget/tree/tree.component";
import { FillPropPaneModel } from "../../data/vs/fill-prop-pane-model";
import { FillPropPane } from "./fill-prop-pane.component";
import { DebounceService } from "../../../widget/services/debounce.service";

@NgModule({
   declarations: [
      FixedDropdownComponent,
      FixedDropdownContextmenuComponent
   ],
   entryComponents: [
      FixedDropdownComponent,
      FixedDropdownContextmenuComponent
   ]
})
class TestModule {}

let createMode: () => FillPropPaneModel = () => {
   return {
      alpha: 100,
      color: "Static",
      colorValue: "#fffff"
   };
};

describe("fill prop pane unit case: ", () => {
   let fixture: ComponentFixture<FillPropPane>;
   let fillProPane: FillPropPane;

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [TestModule, ReactiveFormsModule, FormsModule, NgbModule],
         declarations: [AlphaDropdown, FillPropPane, DynamicComboBox, ColorEditor, ColorPicker, ColorPane, TreeComponent, TreeNodeComponent, TreeDropdownComponent, TreeSearchPipe, FixedDropdownDirective,  TooltipDirective],
         providers: [FixedDropdownService, DropdownStackService, DebounceService],
         schemas: [NO_ERRORS_SCHEMA]
      }).compileComponents();

      fixture = TestBed.createComponent(FillPropPane);
   });

   //Bug #20246, Bug #20262
   it("change value type should be rightly", () => {
      fillProPane = <FillPropPane>fixture.componentInstance;
      fillProPane.model = createMode();
      fillProPane.variables = ["$(var1)", "$(var2)"];
      fixture.detectChanges();

      let typeToggle = fixture.nativeElement.querySelector("button.type-toggle");
      typeToggle.click();
      TestUtils.changeDynamicComboValueType(1);
      fixture.detectChanges();

      let colorInput = fixture.nativeElement.querySelector("dynamic-combo-box select");
      let colorEditor = fixture.nativeElement.querySelector("div.col-auto.ps-1");
      expect(colorInput.getAttribute("ng-reflect-model")).toBe("$(var1)");
      expect(colorEditor.getAttribute("class")).toContain("disable-actions-fade");

      typeToggle.click();
      TestUtils.changeDynamicComboValueType(2);
      fixture.detectChanges();
      colorInput = fixture.nativeElement.querySelector("dynamic-combo-box input");
      expect(colorInput.getAttribute("ng-reflect-model")).toBe("=");
      expect(colorEditor.getAttribute("class")).toContain("disable-actions-fade");

      //Bug #20262
      typeToggle.click();
      TestUtils.changeDynamicComboValueType(0);
      fixture.detectChanges();
      colorInput = fixture.nativeElement.querySelector("dynamic-combo-box");
      expect(colorInput.getAttribute("ng-reflect-value")).toBe("Static");
      expect(fixture.nativeElement.querySelector("cp-color-picker").getAttribute("ng-reflect-color")).toBe("#fffff");
   });
});
