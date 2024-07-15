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
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { By } from "@angular/platform-browser";
import { NgbModal, NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { GraphTypes } from "../../common/graph-types";
import { DropDownTestModule } from "../../common/test/test-module";
import { ColorComponentEditor } from "../color-picker/color-component-editor.component";
import { ColorEditorDialog } from "../color-picker/color-editor-dialog.component";
import { ColorEditor } from "../color-picker/color-editor.component";
import { ColorMap } from "../color-picker/color-map.component";
import { ColorPicker } from "../color-picker/color-picker.component";
import { ColorSlider } from "../color-picker/color-slider.component";
import { ColorPane } from "../color-picker/cp-color-pane.component";
import { RecentColorService } from "../color-picker/recent-color.service";
import { MessageDialog } from "../dialog/message-dialog/message-dialog.component";
import { NewAggrDialog } from "../dialog/new-aggr-dialog/new-aggr-dialog.component";
import { ScriptPane } from "../dialog/script-pane/script-pane.component";
import { FixedDropdownDirective } from "../fixed-dropdown/fixed-dropdown.directive";
import { ComboMode } from "../dynamic-combo-box/dynamic-combo-box-model";
import { AlphaDropdown } from "../format/alpha-dropdown.component";
import { GridLineDropdown } from "../format/grid-line-dropdown.component";
import { FormulaEditorDialog } from "../formula-editor/formula-editor-dialog.component";
import { FormulaEditorService } from "../formula-editor/formula-editor.service";
import { DragService } from "../services/drag.service";
import { TreeNodeComponent } from "../tree/tree-node.component";
import { TreeSearchPipe } from "../tree/tree-search.pipe";
import { TreeComponent } from "../tree/tree.component";
import { DateInputField } from "./date-input-field.component";
import { LabelInputField } from "./label-input-field.component";
import { LinePanel } from "./line-panel.component";
import { TargetComboBox } from "./target-combo-box.component";
import { TargetInfo } from "./target-info";
import { TargetLabelPane } from "./target-label-pane.component";
import { ValueInputField } from "./value-input-field.component";
import { DebounceService } from "../services/debounce.service";

let createModel: () => TargetInfo = () => {
   return {
      measure: {
         name: "",
         label: "",
         dateField: false
      },
      fieldLabel: "",
      genericLabel: "",
      value: "",
      label: "",
      toValue: "",
      toLabel: "",
      labelFormats: "",
      lineStyle: 0,
      lineColor: {
         type: "",
         color: ""
      },
      fillAboveColor: {
         type: "",
         color: ""
      },
      fillBelowColor: {
         type: "",
         color: ""
      },
      alpha: "",
      fillBandColor: {
         type: "",
         color: ""
      },
      chartScope: false,
      index: 0,
      tabFlag: 0,
      changed: false,
      targetString: "",
      strategyInfo: {
         name: "",
         value: "",
         percentageAggregateVal: "",
         standardIsSample: false
      },
      bandFill: {
         name: "",
         field: "",
         summary: false,
         changed: false,
         clazz: "inetsoft.web.binding.model.graph.aesthetic.CategoricalColorModel",
         colors: [],
         cssColors: [],
         defaultColors: [],
         colorMaps: [],
         globalColorMaps: [],
         useGlobal: true,
         shareColors: true,
         dateFormat: 0
      },
      supportFill: false
   };
};

describe("LinePanel Unit Tests", () => {
   let fixture: ComponentFixture<LinePanel>;
   let linePanel: LinePanel;

   beforeEach(async(() => {
      TestBed.configureTestingModule({
         imports: [
            CommonModule,
            NgbModule,
            FormsModule,
            ReactiveFormsModule,
            DropDownTestModule,
         ],
         declarations: [
            LinePanel,
            ValueInputField,
            DateInputField,
            LabelInputField,
            TargetComboBox,
            TargetLabelPane,
            GridLineDropdown,
            ColorEditor,
            AlphaDropdown,
            FormulaEditorDialog,
            TreeComponent,
            TreeNodeComponent,
            TreeSearchPipe,
            NewAggrDialog,
            MessageDialog,
            ColorPicker,
            ColorEditorDialog,
            ColorMap,
            ColorSlider,
            ColorComponentEditor,
            ColorPane,
            ScriptPane,
            FixedDropdownDirective
         ],
         providers: [
            NgbModal,
            FormulaEditorService,
            DragService,
            RecentColorService,
            DebounceService,
         ],
         schemas: [ NO_ERRORS_SCHEMA ]
      });
      TestBed.compileComponents();

      fixture = TestBed.createComponent(LinePanel);
      linePanel = <LinePanel>fixture.componentInstance;
      linePanel.model = createModel();
   }));

   // Bug #10190 disable fill dropdowns when 3d bar chart
   it("should toggle truncate variable", () => {
      linePanel.model.supportFill = false;
      linePanel.availableFields = [];
      fixture.detectChanges();

      let above: any = fixture.nativeElement.querySelectorAll(".fill-above");
      expect(above.enabled).toBeFalsy();
   });

   //Bug #16946 disabled entire chart when value is input number
   //Bug #19234
   it("should disable entire chart when value is input number", () => {
      linePanel.model.value = "1.33";
      fixture.detectChanges();

      let entire: HTMLInputElement = fixture.debugElement.query(By.css("input.entire_chart_id")).nativeElement;
      expect(entire.getAttribute("ng-reflect-is-disabled")).toBeTruthy();

      linePanel.valueType = ComboMode.EXPRESSION;
      linePanel.model.value = "=${aa}";
      fixture.detectChanges();
      expect(entire.getAttribute("ng-reflect-is-disabled")).toBeTruthy();
   });

   //Bug #17366 should show right promptstring on datefield column
   xit("should show right promptstring when field type is date", () => {
      linePanel.model.measure.dateField = true;
      fixture.detectChanges();


      let valueElem: HTMLElement = fixture.debugElement.query(By.css("date-input-field dynamic-combo-box")).nativeElement;
      expect(valueElem.getAttribute("promptstring")).toBe("yyyy-MM-dd HH:mm:ss");

      const valueInput = valueElem.querySelector("input");
      valueInput.dispatchEvent(new Event("click"));
      fixture.detectChanges();
      let dropdown: HTMLElement = fixture.nativeElement.ownerDocument.querySelector("fixed-dropdown");
      expect(dropdown).toBeTruthy();
   });

   //Bug #19222 and Bug #18990
   it("value input warning check", () => {
      fixture.detectChanges();
      let warning = document.querySelector(".alert-danger");
      expect(warning.textContent).toContain("_#(common.invalidNumber)");

      linePanel.model.value = "11";
      fixture.detectChanges();
      warning = document.querySelector(".alert-danger");
      expect(warning).toBe(null);
   });

   //Bug #19596, Bug #19654 alpha should be enable for 3Dbar
   it("alpha should be enable for 3Dbar", () => {
      linePanel.chartType = GraphTypes.CHART_3D_BAR;
      fixture.detectChanges();
      let alpha = fixture.debugElement.query(By.css("alpha-dropdown")).nativeElement;
      expect(alpha.disabled).toBeFalsy();
   });

   //Bug #19651
   it("should not load date column when group all other togetther is true", () => {
      linePanel.availableFields = [
         {name: "", label: "", groupOthers: false, dateField: false},
         {name: "Sum(id)", label: "Sum(id)", groupOthers: false, dateField: false},
         {name: "Year(date)", label: "Year(date)", groupOthers: true, dateField: true}];
      fixture.detectChanges();
      let fileds = fixture.nativeElement.querySelectorAll("select option");
      expect(fileds.length).toBe(2);
      expect(fileds[0].textContent).toContain("");
      expect(fileds[1].textContent).toContain("Sum(id)");
   });
});
