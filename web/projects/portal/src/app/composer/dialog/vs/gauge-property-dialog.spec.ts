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
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { of as observableOf } from "rxjs";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { UIContextService } from "../../../common/services/ui-context.service";
import { BasicGeneralPane } from "../../../vsobjects/dialog/basic-general-pane.component";
import { GeneralPropPane } from "../../../vsobjects/dialog/general-prop-pane.component";
import { SizePositionPane } from "../../../vsobjects/dialog/size-position-pane.component";
import { GeneralPropPaneModel } from "../../../vsobjects/model/general-prop-pane-model";
import { SizePositionPaneModel } from "../../../vsobjects/model/size-position-pane-model";
import { VSTrapService } from "../../../vsobjects/util/vs-trap.service";
import { PropertyDialogService } from "../../../vsobjects/util/property-dialog.service";
import { ColorComponentEditor } from "../../../widget/color-picker/color-component-editor.component";
import { ColorEditorDialog } from "../../../widget/color-picker/color-editor-dialog.component";
import { ColorEditor } from "../../../widget/color-picker/color-editor.component";
import { ColorMap } from "../../../widget/color-picker/color-map.component";
import { ColorPicker } from "../../../widget/color-picker/color-picker.component";
import { ColorSlider } from "../../../widget/color-picker/color-slider.component";
import { ColorPane } from "../../../widget/color-picker/cp-color-pane.component";
import { NewAggrDialog } from "../../../widget/dialog/new-aggr-dialog/new-aggr-dialog.component";
import { ScriptPane } from "../../../widget/dialog/script-pane/script-pane.component";
import { VSAssemblyScriptPaneModel } from "../../../widget/dialog/vsassembly-script-pane/vsassembly-script-pane-model";
import { VSAssemblyScriptPane } from "../../../widget/dialog/vsassembly-script-pane/vsassembly-script-pane.component";
import { DefaultFocusDirective } from "../../../widget/directive/default-focus.directive";
import { EnterSubmitDirective } from "../../../widget/directive/enter-submit.directive";
import { FixedDropdownDirective } from "../../../widget/fixed-dropdown/fixed-dropdown.directive";
import { FixedDropdownService } from "../../../widget/fixed-dropdown/fixed-dropdown.service";
import { FormulaEditorDialog } from "../../../widget/formula-editor/formula-editor-dialog.component";
import { TreeDropdownComponent } from "../../../widget/tree/tree-dropdown.component";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { TreeNodeComponent } from "../../../widget/tree/tree-node.component";
import { TreeSearchPipe } from "../../../widget/tree/tree-search.pipe";
import { TreeComponent } from "../../../widget/tree/tree.component";
import { DataOutputPaneModel } from "../../data/vs/data-output-pane-model";
import { FacePaneModel } from "../../data/vs/face-pane-model";
import { GaugeAdvancedPaneModel } from "../../data/vs/gauge-advanced-pane-model";
import { GaugeGeneralPaneModel } from "../../data/vs/gauge-general-pane-model";
import { GaugePropertyDialogModel } from "../../data/vs/gauge-property-dialog-model";
import { NumberRangePaneModel } from "../../data/vs/number-range-pane-model";
import { OutputGeneralPaneModel } from "../../data/vs/output-general-pane-model";
import { RangePaneModel } from "../../data/vs/range-pane-model";
import { DataOutputPane } from "./data-output-pane.component";
import { FacePane } from "./face-pane.component";
import { GaugeAdvancedPane } from "./gauge-advanced-pane.component";
import { GaugeGeneralPane } from "./gauge-general-pane.component";
import { GaugePropertyDialog } from "./gauge-property-dialog.component";
import { NumberRangePane } from "./number-range-pane.component";
import { OutputGeneralPane } from "./output-general-pane.component";
import { RangePane } from "./range-pane.component";


let createModel = () => {
   return <GaugePropertyDialogModel> {
      gaugeGeneralPaneModel: <GaugeGeneralPaneModel> {
         outputGeneralPaneModel: <OutputGeneralPaneModel> {
            generalPropPaneModel: <GeneralPropPaneModel> {
               basicGeneralPaneModel: {
                  name: "MockGauge",
                  visible: "false",
                  shadow: false,
                  enabled: false,
                  primary: false,
                  showShadowCheckbox: false,
                  showEnabledCheckbox: false,
                  showRefreshCheckbox: false,
                  objectNames: []
               },
               showSubmitCheckbox: false,
               submitOnChange: false,
               showEnabledGroup: false
            }
         },
         numberRangePaneModel: <NumberRangePaneModel> {
            min: "0",
            max: "1",
            majorIncrement: "5",
            minorIncrement: "1"
         },
         facePaneModel: <FacePaneModel> {
         },
         sizePositionPaneModel: <SizePositionPaneModel> {
            top: 10,
            left: 10,
            width: 10,
            height: 10,
            container: false
         },
      },
      dataOutputPaneModel: <DataOutputPaneModel> {
         column: "",
         aggregate: "",
         with: "",
         table: "",
         columnType: "",
         magnitude: 1,
         targetTree: <TreeNodeModel> {}
      },
      gaugeAdvancedPaneModel: <GaugeAdvancedPaneModel> {
         rangePaneModel: <RangePaneModel> {
            gradient: false,
            rangeValues: [],
            rangeColorValues: []
         },
         showValue: false
      },
      vsAssemblyScriptPaneModel: <VSAssemblyScriptPaneModel> {
      }
   };
};

describe("GaugePropertyDialog Integration Test", () => {
   let fixedDropdownService: any;
   let contextService: any;
   let trapService: any;
   let dialogService: any;

   beforeEach(async(() => {
      trapService = { checkTrap: jest.fn() };
      fixedDropdownService = { open: jest.fn() };
      contextService = {
         isVS: jest.fn(),
         isAdhoc: jest.fn(),
         getDefaultTab: jest.fn(),
         setDefaultTab: jest.fn(),
         getObjectChange: jest.fn(() => observableOf({}))
      };
      dialogService = { checkScript: jest.fn() };

      TestBed.configureTestingModule({
         imports: [
            FormsModule, ReactiveFormsModule, NgbModule
         ],
         declarations: [
            GaugePropertyDialog, GaugeGeneralPane, DataOutputPane, GaugeAdvancedPane,
            VSAssemblyScriptPane, OutputGeneralPane, NumberRangePane, FacePane,
            TreeDropdownComponent, RangePane, ScriptPane,
            GeneralPropPane, TreeComponent, FormulaEditorDialog, ColorEditor,
            BasicGeneralPane, TreeNodeComponent, NewAggrDialog, ColorPicker,
            ColorEditorDialog, ColorMap, ColorSlider, ColorComponentEditor, ColorPane,
            TreeSearchPipe, FixedDropdownDirective, EnterSubmitDirective,
            DefaultFocusDirective, SizePositionPane
         ],
         providers: [
            { provide: FixedDropdownService, useValue: fixedDropdownService },
            { provide: UIContextService, useValue: contextService },
            { provide: VSTrapService, useValue: trapService },
            { provide: PropertyDialogService, useValue: dialogService }
         ],
         schemas: [ NO_ERRORS_SCHEMA ]
      });
      TestBed.compileComponents();
   }));

   it("should create the GaugePropertyDialog", () => {
      let fixture: ComponentFixture<GaugePropertyDialog> =
         TestBed.createComponent(GaugePropertyDialog);
      // fixture.detectChanges();
      let dialog: GaugePropertyDialog = <GaugePropertyDialog> fixture.componentInstance;
      dialog.model = createModel();
      fixture.detectChanges();
      let element = fixture.nativeElement;
      let componentName = element.querySelector("[id=basicGeneralPaneName]").value;
      expect(componentName).toBe("MockGauge");
   });
});
