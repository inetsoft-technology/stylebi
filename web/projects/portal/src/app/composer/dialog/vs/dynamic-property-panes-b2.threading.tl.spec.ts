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
 * The deeper-chain property dialog panes of bug #80459 PR B2 and the calendar of PR B3: data
 * input (column / row), dynamic image, selection measure, selection tree ids, combo box date
 * range and the calendar min / max. For each real dialog tab the Expression editor of every
 * field resolves a pairing with the right (assembly, property name). The column option dialog and
 * a checkbox data tab must stay without one.
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
import { ColumnOptionDialog } from "../../../vsobjects/dialog/column-option-dialog.component";
import { ColumnOptionType } from "../../../vsobjects/model/column-option-type";
import { TreeComponent } from "../../../widget/tree/tree.component";
import { AdditionalTableSelectionPaneComponent } from "../../../widget/dialog/additional-table-selection-pane/additional-table-selection-pane.component";
import { CalendarPropertyDialog } from "./calendar-property-dialog.component";
import { CheckboxPropertyDialog } from "./checkbox-property-dialog.component";
import { DataInputPane } from "./data-input-pane.component";
import { ComboBoxPropertyDialog } from "./combobox-property-dialog.component";
import { ComboboxGeneralPane } from "./combobox-general-pane.component";
import { ImageAdvancedPane } from "./image-advanced-pane.component";
import { ImageScalePane } from "./image-scale-pane.component";
import { ImagePropertyDialog } from "./image-property-dialog.component";
import { RadioButtonPropertyDialog } from "./radiobutton-property-dialog.component";
import { SelectionListPane } from "./selection-list-pane.component";
import { SelectionListPropertyDialog } from "./selection-list-property-dialog.component";
import { SelectionTreeColumnsPane } from "./selection-tree-columns-pane.component";
import { SelectionTreePane } from "./selection-tree-pane.component";
import { SelectionTreePropertyDialog } from "./selection-tree-property-dialog.component";
import { SliderPropertyDialog } from "./slider-property-dialog.component";
import { SpinnerPropertyDialog } from "./spinner-property-dialog.component";
import { TextInputPropertyDialog } from "./textinput-property-dialog.component";
import { GeneralPropPane } from "../../../vsobjects/dialog/general-prop-pane.component";
import { SizePositionPane } from "../../../vsobjects/dialog/size-position-pane.component";

const RUNTIME = "rt-42";

/** The names this spec owns; every other combo of a rendered tab is left alone. */
const B2_NAMES = /^(dataInputPaneModel\.(columnValue|rowValue)|imageAdvancedPaneModel\..*|source:(measure|parentIdColumn|idColumn|labelColumn)|formula|minDate|maxDate|min|max)$/;

const CS = { scriptExpression: "", onClickExpression: "" };
const dataInput = () => ({
   table: "T", tableLabel: "T", variable: false, columnValue: "C", rowValue: "1",
   targetTree: { children: [] }, writeBackDirectly: false
});
const measurePane = () => ({ measure: "=m", formula: "=f", showText: false, showBar: false });
const calValue = (value: string) => ({ value, type: "EXPRESSION" });

interface Case {
   label: string;
   component: any;
   tab: string;
   model: any;
   names: string[];
   /** The model value each name must be bound to: pins WHICH field a name edits. */
   expected?: Record<string, string>;
   prepare?: (fixture: any) => void;
   /** The inner editor of a dynamic-value-editor names the assembly, not its own assemblyName. */
   valueEditor?: boolean;
   strip?: { pane: any, imports: any[] }[];
}

async function renderCase(c: Case, registry: SheetClientRegistry): Promise<DynamicComboBox[]> {
   const { fixture } = await render(c.component, {
      providers: [
         provideHttpClient(),
         provideHttpClientTesting(),
         { provide: NgbModal, useValue: { open: vi.fn() } },
         { provide: PopComponentService, useValue: { getPopLocationLabel: vi.fn(() => "") } },
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
      .filter(combo => !!combo.dynamicProperty && B2_NAMES.test(combo.dynamicProperty));
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

const DATA_NAMES = ["dataInputPaneModel.columnValue", "dataInputPaneModel.rowValue"];
const IDS = ["source:parentIdColumn", "source:idColumn", "source:labelColumn"];
const COMBO_EDITOR = {
   embedded: true, calendar: true, dataType: "date", minDate: "=a", maxDate: "=b", serverTZ: false,
   noDefault: true, defaultValue: null, query: false, type: "ComboBox", valid: true,
   selectionListDialogModel: {
      selectionListEditorModel: { column: "c", value: "c", tables: [], localizedTables: [] }
   },
   variableListDialogModel: { dataType: "date", labels: [], values: [] }
};
const COMBO_GENERAL = {
   listValuesPaneModel: {
      comboBoxEditorModel: COMBO_EDITOR, sortType: 1, embeddedDataDown: false
   },
   generalPropPaneModel: { basicGeneralPaneModel: { name: "Obj1" } }, sizePositionPaneModel: {}
};
const TREE_PANE = {
   mode: 2, targetTree: { children: [] }, selectedColumns: [], additionalTables: ["T"],
   selectedTable: null, parentId: "=p", id: "=i", label: "=l", selectChildren: false,
   selectionMeasurePaneModel: measurePane()
};
const GENERAL_NAME = {
   selectionGeneralPaneModel: {
      generalPropPaneModel: { basicGeneralPaneModel: { name: "Obj1" } }, singleSelectionLevels: []
   }
};

const CASES: Case[] = [
   ...[
      ["combobox", ComboBoxPropertyDialog, {
         comboboxGeneralPaneModel: COMBO_GENERAL
      }],
      ["radiobutton", RadioButtonPropertyDialog, {}],
      ["slider", SliderPropertyDialog, {}],
      ["spinner", SpinnerPropertyDialog, {}],
      ["textinput", TextInputPropertyDialog, {}],
   ].map(([type, component, extra]: any): Case => ({
      label: type + " data tab", component, tab: "dataTab",
      model: { dataInputPaneModel: dataInput(), vsAssemblyScriptPaneModel: {},
         clickableScriptPaneModel: CS, ...extra },
      names: DATA_NAMES,
      expected: { "dataInputPaneModel.columnValue": "C", "dataInputPaneModel.rowValue": "=R" },
      prepare: fixture => {
         fixture.debugElement.query(By.directive(DataInputPane)).componentInstance.selectedRow = "=R";
      }
   })),
   {
      label: "image advanced tab", component: ImagePropertyDialog, tab: "advancedTab",
      model: {
         imageGeneralPaneModel: { staticImagePaneModel: { imagePreviewPaneModel: { selectedImage: null } } },
         imageAdvancedPaneModel: {
            dynamicImagePaneModel: { dynamicImageSelected: true, dynamicImageValue: "=img" },
            scaleImagePaneModel: {}
         },
         vsAssemblyScriptPaneModel: {}, clickableScriptPaneModel: CS
      },
      strip: [{ pane: ImageAdvancedPane, imports: [ImageScalePane] }],
      names: ["imageAdvancedPaneModel.dynamicImagePaneModel.dynamicImageValue"],
      expected: { "imageAdvancedPaneModel.dynamicImagePaneModel.dynamicImageValue": "=img" }
   },
   {
      label: "selection list data tab", component: SelectionListPropertyDialog, tab: "dataTab",
      model: {
         ...GENERAL_NAME,
         selectionListPaneModel: {
            targetTree: { children: [] }, selectedTable: null, selectedColumn: null,
            additionalTables: ["T"], grayedOutFields: [], selectionMeasurePaneModel: measurePane()
         },
         vsAssemblyScriptPaneModel: {}
      },
      strip: [{ pane: SelectionListPane, imports: [TreeComponent, AdditionalTableSelectionPaneComponent] }],
      names: ["source:measure", "formula"],
      expected: { "source:measure": "=m", formula: "=f" }
   },
   {
      label: "selection tree data tab", component: SelectionTreePropertyDialog, tab: "dataTab",
      model: {
         ...GENERAL_NAME, selectionTreePaneModel: TREE_PANE, vsAssemblyScriptPaneModel: {}
      },
      strip: [{ pane: SelectionTreePane, imports: [SelectionTreeColumnsPane] }],
      names: ["source:measure", "formula", ...IDS],
      expected: {
         "source:measure": "=m", formula: "=f", "source:parentIdColumn": "=p",
         "source:idColumn": "=i", "source:labelColumn": "=l"
      }
   },
   {
      label: "combobox general tab (list values)", component: ComboBoxPropertyDialog,
      tab: "combobox-property-dialog-general-tab",
      model: {
         comboboxGeneralPaneModel: COMBO_GENERAL, vsAssemblyScriptPaneModel: {},
         dataInputPaneModel: dataInput(), inputLabelPaneModel: {}
      },
      strip: [{ pane: ComboboxGeneralPane, imports: [GeneralPropPane, SizePositionPane] }],
      names: ["minDate", "maxDate"],
      expected: { minDate: "=a", maxDate: "=b" }
   },
   {
      label: "calendar advanced tab", component: CalendarPropertyDialog, tab: "advancedTab",
      model: {
         calendarAdvancedPaneModel: {
            min: calValue("=a"), max: calValue("=b"), viewMode: 0, showType: 0, dateRangeSelection: true
         },
         vsAssemblyScriptPaneModel: {}
      },
      names: ["min", "max"], valueEditor: true, expected: { min: "=a", max: "=b" }
   },
];

afterEach(() => vi.restoreAllMocks());

describe("deeper-chain property dialog panes thread the assembly and runtime id (bug #80459 B2/B3)", () => {
   it.each(CASES)("$label: every Expression editor resolves its own pairing", async c => {
      const combos = await renderCase(c, registry());

      expect(combos.map(x => x.dynamicProperty).sort()).toEqual([...c.names].sort());

      // each name must be the combo that edits that model field, not just a name in the set
      for(const combo of combos) {
         expect(combo.value, combo.dynamicProperty).toBe(c.expected[combo.dynamicProperty]);
      }

      for(const combo of combos) {
         const dialog = openFormulaEditor(combo);
         expect(dialog.dynamicProperty, combo.dynamicProperty).toBe(combo.dynamicProperty);
         expect(dialog.contextTable, combo.dynamicProperty).toBe("Obj1");
         expect(dialog.runtimeId, combo.dynamicProperty).toBe(RUNTIME);

         if(!c.valueEditor) {
            expect(dialog.assemblyName, combo.dynamicProperty).toBe("Obj1");
         }

         expect(dialog.isVSContext, combo.dynamicProperty).toBe(true);
      }
   });

   it("checkbox data tab shows no Column / Row editor, so none pairs", async () => {
      const combos = await renderCase({
         label: "checkbox", component: CheckboxPropertyDialog, tab: "dataTab", names: [],
         model: { dataInputPaneModel: dataInput(), vsAssemblyScriptPaneModel: {} }
      }, registry());

      expect(combos).toEqual([]);
   });

   it("an empty registry (viewer / preview) keeps the editors without a Connect button", async () => {
      const combos = await renderCase(CASES[0], new SheetClientRegistry());
      const dialog = openFormulaEditor(combos[0]);

      expect(dialog.dynamicProperty).toBeUndefined();
      expect(dialog.runtimeId).toBeUndefined();
   });

   it("the column option dialog's date range editors never pair (no assembly name)", async () => {
      const { fixture } = await render(ColumnOptionDialog, {
         providers: [
            provideHttpClient(), provideHttpClientTesting(),
            { provide: NgbModal, useValue: { open: vi.fn() } },
            { provide: SheetClientRegistry, useValue: registry() }
         ],
         componentProperties: {
            model: { enableColumnEditing: true, inputControl: ColumnOptionType.COMBOBOX, editor: { ...COMBO_EDITOR } } as any, runtimeId: RUNTIME
         } as any,
      });
      fixture.detectChanges();

      const combos: DynamicComboBox[] = fixture.debugElement.queryAll(By.directive(DynamicComboBox))
         .map(d => d.componentInstance)
         .filter(c => c.dynamicProperty === "minDate" || c.dynamicProperty === "maxDate");

      for(const combo of combos) {
         const dialog = openFormulaEditor(combo);
         expect(dialog.dynamicProperty, combo.dynamicProperty).toBeUndefined();
         expect(dialog.runtimeId, combo.dynamicProperty).toBeUndefined();
      }
   });
});
