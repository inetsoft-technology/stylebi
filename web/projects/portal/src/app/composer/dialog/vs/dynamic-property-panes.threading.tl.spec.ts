/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

/**
 * The one-hop property dialog panes of bug #80459 PR B1 get their assembly and runtime id from
 * the real dialogs: label text, numeric / number range, line and fill color, text, data output
 * and gauge range panes. For each dialog tab the Expression editor of every B1 field resolves a
 * pairing with the right (assembly, property name), checked through the editor the combo opens.
 */
import { provideHttpClient } from "@angular/common/http";
import { provideHttpClientTesting } from "@angular/common/http/testing";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { By } from "@angular/platform-browser";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { render } from "@testing-library/angular";
import { of as observableOf } from "rxjs";
import { ComponentTool } from "../../../common/util/component-tool";
import { SheetClientRegistry } from "../../../common/viewsheet-client/sheet-client-registry.service";
import { UIContextService } from "../../../common/services/ui-context.service";
import { PropertyDialogService } from "../../../vsobjects/util/property-dialog.service";
import { VSTrapService } from "../../../vsobjects/util/vs-trap.service";
import { DynamicComboBox } from "../../../widget/dynamic-combo-box/dynamic-combo-box.component";
import { PopComponentService } from "../../../vsobjects/objects/data-tip/pop-component.service";
import { ComboBoxPropertyDialog } from "./combobox-property-dialog.component";
import { DataOutputPane } from "./data-output-pane.component";
import { GaugeGeneralPane } from "./gauge-general-pane.component";
import { GaugePropertyDialog } from "./gauge-property-dialog.component";
import { PaddingPane } from "../../../vsobjects/dialog/padding-pane.component";
import { SizePositionPane } from "../../../vsobjects/dialog/size-position-pane.component";
import { TipPane } from "../../../vsobjects/dialog/graph/tip-pane.component";
import { FacePane } from "./face-pane.component";
import { OutputGeneralPane } from "./output-general-pane.component";
import { ImagePropertyDialog } from "./image-property-dialog.component";
import { LinePropertyDialog } from "./line-property-dialog.component";
import { OvalPropertyDialog } from "./oval-property-dialog.component";
import { RectanglePropertyDialog } from "./rectangle-property-dialog.component";
import { SliderPropertyDialog } from "./slider-property-dialog.component";
import { SpinnerPropertyDialog } from "./spinner-property-dialog.component";
import { TextGeneralPane } from "./text-general-pane.component";
import { TextPropertyDialog } from "./text-property-dialog.component";
import { TextInputPropertyDialog } from "./textinput-property-dialog.component";

const RUNTIME = "rt-42";

/** The property names this spec owns; every other combo of a rendered tab is left alone. */
const B1_NAMES = /^(labelText|min|max|increment|minorIncrement|majorIncrement|fillColor|lineColor|text|column|aggregate|with|nthOperand|targetValue|rangeValues\[\d+\])$/;

const inputLabel = () => ({ showLabel: true, labelText: "=x", labelPosition: "left", labelGap: 5 });
const lineProp = () => ({ color: "Static", colorValue: "#000000", style: "1" });
const fillProp = () => ({
   color: "Static", colorValue: "#ffffff", alpha: 100,
   gradientColor: { apply: false, direction: "linear", colors: [], angle: 0 }
});
const dataOutput = () => ({
   column: "C", aggregate: "Sum", with: "W", num: "3", table: "T", tableType: "table",
   columnType: "string", magnitude: 1, targetTree: { children: [] }, grayedOutFields: []
});
const CS = { scriptExpression: "", onClickExpression: "" };
const numericRange = () => ({ minimum: "1", maximum: "10", increment: "1" });

interface Case {
   label: string;
   component: any;
   tab: string;
   model: any;
   names: string[];
   /** Components whose heavy sibling panes are not under test, replaced by inert elements. */
   strip?: { pane: any, imports: any[] }[];
   prepare?: (fixture: any) => void;
}

async function renderCase(c: Case, registry: SheetClientRegistry): Promise<DynamicComboBox[]> {
   const { fixture } = await render(c.component, {
      providers: [
         provideHttpClient(),
         provideHttpClientTesting(),
         { provide: NgbModal, useValue: { open: vi.fn() } },
         { provide: PopComponentService, useValue: { getPopLocationLabel: vi.fn(() => ""), getPopComponents: vi.fn(() => []) } },
         { provide: SheetClientRegistry, useValue: registry },
         { provide: PropertyDialogService, useValue: { checkScript: vi.fn() } },
         { provide: VSTrapService, useValue: { checkTrap: vi.fn() } },
         {
            provide: UIContextService,
            useValue: {
               isVS: vi.fn(), isAdhoc: vi.fn(), getDefaultTab: vi.fn(() => c.tab),
               setDefaultTab: vi.fn(), getObjectChange: vi.fn(() => observableOf({}))
            }
         }
      ],
      configureTestBed: (bed) => {
         (c.strip || []).forEach(s => bed.overrideComponent(s.pane, {
            remove: { imports: s.imports }, add: { schemas: [NO_ERRORS_SCHEMA] }
         }));
      },
      componentProperties: {
         model: c.model, runtimeId: RUNTIME, assemblyName: "Obj1", variableValues: [],
         scriptTreeModel: null
      },
   });
   fixture.detectChanges();
   c.prepare?.(fixture);
   fixture.detectChanges();

   return fixture.debugElement.queryAll(By.directive(DynamicComboBox))
      .map(d => d.componentInstance)
      .filter(combo => !!combo.dynamicProperty && B1_NAMES.test(combo.dynamicProperty));
}

function registry(): SheetClientRegistry {
   const registry = new SheetClientRegistry();
   registry.register({ runtimeId: RUNTIME } as any, "VIEWSHEET");
   return registry;
}

function openFormulaEditor(combo: DynamicComboBox): any {
   const dialog: any = {};
   vi.spyOn(ComponentTool, "showDialog").mockReturnValue(dialog);
   combo.showFormulaEditor();
   return dialog;
}

const SIZE = {
   left: 0, top: 0, width: 10, height: 10, titleHeight: 0, scaleVertical: false,
   container: false, lockAspectRatio: false, ratio: 1
};
const BASIC = {
   name: "Obj1", visible: "Show", shadow: false, enabled: true, sendSelections: false,
   primary: true, refresh: false, editable: false, nameEditable: true, showShadowCheckbox: false,
   showEnabledCheckbox: false, showRefreshCheckbox: false, showEditableCheckbox: false,
   showSendSelectionsCheckbox: false, objectNames: []
};
const GENERAL = {
   basicGeneralPaneModel: BASIC, showSubmitCheckbox: false, submitOnChange: true,
   showEnabledGroup: false, enabled: "True", popLocation: null
};
const OUTPUT_GENERAL = {
   generalPropPaneModel: GENERAL, titlePropPaneModel: { title: "T", visible: true },
   showSubmitCheckbox: false, sizePositionPaneModel: SIZE
};
const TIP = { tipOption: "none", tipView: null, customTip: null, alpha: "100", dataTip: null };

const CASES: Case[] = [
   {
      label: "combobox label tab", component: ComboBoxPropertyDialog, tab: "labelTab",
      model: {
         inputLabelPaneModel: inputLabel(), vsAssemblyScriptPaneModel: {},
         comboboxGeneralPaneModel: { listValuesPaneModel: { comboBoxEditorModel: {} } }
      },
      names: ["labelText"]
   },
   {
      label: "slider label tab", component: SliderPropertyDialog, tab: "labelTab",
      model: { inputLabelPaneModel: inputLabel(), vsAssemblyScriptPaneModel: {} },
      names: ["labelText"]
   },
   {
      label: "spinner label tab", component: SpinnerPropertyDialog, tab: "labelTab",
      model: { inputLabelPaneModel: inputLabel(), vsAssemblyScriptPaneModel: {} },
      names: ["labelText"]
   },
   {
      label: "textinput label tab", component: TextInputPropertyDialog, tab: "labelTab",
      model: { inputLabelPaneModel: inputLabel(), vsAssemblyScriptPaneModel: {}, clickableScriptPaneModel: CS },
      names: ["labelText"]
   },
   {
      label: "slider general tab", component: SliderPropertyDialog,
      tab: "slider-property-property-dialog-general-tab",
      model: {
         sliderGeneralPaneModel: {
            generalPropPaneModel: GENERAL, numericRangePaneModel: numericRange(),
            sizePositionPaneModel: SIZE
         },
         vsAssemblyScriptPaneModel: {}
      },
      names: ["min", "max", "increment"]
   },
   {
      label: "spinner general tab", component: SpinnerPropertyDialog,
      tab: "spinner-property-dialog-general-tab",
      model: {
         spinnerGeneralPaneModel: {
            generalPropPaneModel: GENERAL, numericRangePaneModel: numericRange(),
            sizePositionPaneModel: SIZE
         },
         vsAssemblyScriptPaneModel: {}
      },
      names: ["min", "max", "increment"]
   },
   {
      label: "oval shape tab", component: OvalPropertyDialog, tab: "shapeTab",
      model: {
         ovalPropertyPaneModel: {
            linePropPaneModel: lineProp(), fillPropPaneModel: fillProp(), shadowPropPaneModel: {}
         },
         vsAssemblyScriptPaneModel: {}
      },
      names: ["lineColor", "fillColor"]
   },
   {
      label: "rectangle shape tab", component: RectanglePropertyDialog, tab: "shapeTab",
      model: {
         rectanglePropertyPaneModel: {
            linePropPaneModel: lineProp(), fillPropPaneModel: fillProp(), shadowPropPaneModel: {},
            radius: 0
         },
         shapeGeneralPaneModel: { sizePositionPaneModel: SIZE },
         vsAssemblyScriptPaneModel: {}
      },
      names: ["lineColor", "fillColor"]
   },
   {
      label: "line shape tab", component: LinePropertyDialog, tab: "shapeTab",
      model: {
         linePropertyPaneModel: { linePropPaneModel: lineProp(), begin: 0, end: 0 },
         vsAssemblyScriptPaneModel: {}
      },
      names: ["lineColor"]
   },
   {
      label: "text general tab", component: TextPropertyDialog, tab: "text-property-dialog-general-tab",
      model: {
         textGeneralPaneModel: {
            outputGeneralPaneModel: OUTPUT_GENERAL, textPaneModel: { text: "=x", autoSize: false },
            tipPaneModel: TIP, paddingPaneModel: {}, sizePositionPaneModel: SIZE
         },
         vsAssemblyScriptPaneModel: {}, clickableScriptPaneModel: CS
      },
      strip: [{ pane: TextGeneralPane, imports: [TipPane] }],
      names: ["text"]
   },
   {
      label: "text data tab", component: TextPropertyDialog, tab: "dataTab",
      model: { dataOutputPaneModel: dataOutput(), vsAssemblyScriptPaneModel: {}, clickableScriptPaneModel: CS },
      names: ["column", "aggregate", "with", "nthOperand"],
      prepare: fixture => openNth(fixture, DataOutputPane)
   },
   {
      label: "image data tab", component: ImagePropertyDialog, tab: "dataTab",
      model: { dataOutputPaneModel: dataOutput(), vsAssemblyScriptPaneModel: {}, clickableScriptPaneModel: CS },
      names: ["column", "aggregate", "with", "nthOperand"],
      prepare: fixture => openNth(fixture, DataOutputPane)
   },
   {
      label: "gauge data tab", component: GaugePropertyDialog, tab: "dataTab",
      model: {
         gaugeGeneralPaneModel: { facePaneModel: { face: 0 } },
         dataOutputPaneModel: dataOutput(), vsAssemblyScriptPaneModel: {}
      },
      names: ["column", "aggregate", "with", "nthOperand"],
      prepare: fixture => openNth(fixture, DataOutputPane)
   },
   {
      label: "gauge advanced tab (bullet graph: three ranges and a target)",
      component: GaugePropertyDialog, tab: "advancedTab",
      model: {
         gaugeGeneralPaneModel: { facePaneModel: { face: 90820 } },
         gaugeAdvancedPaneModel: {
            showValue: true,
            rangePaneModel: {
               gradient: false, rangeValues: ["10", "20", "30"],
               rangeColorValues: ["a", "b", "c", "d", "e", "f"], targetValue: "5"
            },
         },
         vsAssemblyScriptPaneModel: {}
      },
      names: ["rangeValues[0]", "rangeValues[1]", "rangeValues[2]", "targetValue"]
   },
   {
      label: "gauge general tab", component: GaugePropertyDialog,
      tab: "gauge-property-dialog-general-tab",
      model: {
         gaugeGeneralPaneModel: {
            outputGeneralPaneModel: OUTPUT_GENERAL, facePaneModel: { face: 0 },
            numberRangePaneModel: { min: "0", max: "100", minorIncrement: "5", majorIncrement: "10" },
            tipPaneModel: TIP, paddingPaneModel: {}, sizePositionPaneModel: SIZE
         },
         vsAssemblyScriptPaneModel: {}
      },
      strip: [{
         pane: GaugeGeneralPane,
         imports: [FacePane, TipPane, PaddingPane, SizePositionPane, OutputGeneralPane]
      }],
      names: ["min", "max", "minorIncrement", "majorIncrement"]
   },
];

/** The N / P combo is only shown for a formula that takes one. */
function openNth(fixture: any, pane: any): void {
   const instance = fixture.debugElement.query(By.directive(pane)).componentInstance;
   instance.sqlProvider = true;
   instance.twoColumns = true;
   instance.hasN = true;
}

afterEach(() => vi.restoreAllMocks());

describe("property dialog panes thread the assembly and runtime id to B1 combos (bug #80459)", () => {
   it.each(CASES)("$label: every B1 Expression editor resolves its own pairing", async c => {
      const combos = await renderCase(c, registry());

      expect(combos.map(x => x.dynamicProperty).sort()).toEqual([...c.names].sort());

      for(const combo of combos) {
         const dialog = openFormulaEditor(combo);
         expect(dialog.dynamicProperty, combo.dynamicProperty).toBe(combo.dynamicProperty);
         expect(dialog.contextTable, combo.dynamicProperty).toBe("Obj1");
         expect(dialog.runtimeId, combo.dynamicProperty).toBe(RUNTIME);
         expect(dialog.assemblyName, combo.dynamicProperty).toBe("Obj1");
         expect(dialog.isVSContext, combo.dynamicProperty).toBe(true);
      }
   });

   it("an unpaired registry keeps the editors without a Connect to Claude button", async () => {
      const combos = await renderCase(CASES[0], new SheetClientRegistry());
      const dialog = openFormulaEditor(combos[0]);

      expect(dialog.dynamicProperty).toBeUndefined();
      expect(dialog.runtimeId).toBeUndefined();
   });

   it("range editors name the model element they edit, not their display position", async () => {
      const c = CASES.find(x => x.label.startsWith("gauge advanced"));
      const combos = await renderCase(c, registry());
      const values = ["10", "20", "30"];

      for(const combo of combos.filter(x => x.dynamicProperty.startsWith("rangeValues"))) {
         const index = +/\[(\d+)\]/.exec(combo.dynamicProperty)[1];
         expect(combo.value, combo.dynamicProperty).toBe(values[index]);
      }
   });
});
