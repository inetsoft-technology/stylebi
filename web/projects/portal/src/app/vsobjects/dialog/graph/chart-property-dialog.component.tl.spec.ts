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
 * ChartPropertyDialog - single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - defaultTab and verifyLastTab: script-open priority, saved-tab reuse,
 *     composer fallback, and line-pane gating
 *   Group 2 [Risk 3] - closing(): reduced payload and trap check callbacks for commit/apply
 *   Group 3 [Risk 2] - ngOnInit and getScripts(): form scaffold and inherited script seed input
 *   Group 4 [Risk 2] - isValid(): form invalid and alphaInvalid guard paths
 *   Group 5 [Risk 1] - composer getter, linePaneVisible OR branches, and default-tab persistence
 *
 * Confirmed bugs (it.fails): none
 *
 * Out of scope:
 *   Template tab rendering is not exercised because the regression-prone logic is concentrated in
 *   class getters and closing payload construction rather than in standalone child rendering.
 *
 * Mocking strategy:
 *   - Direct class instantiation avoids heavy standalone child imports because the contracts under
 *     test are service wiring, form setup, and emitted payload shape.
 *   - UIContextService, PropertyDialogService, and VSTrapService are replaced with vi.fn() mocks.
 */

import { Subject } from "rxjs";

import { UntypedFormGroup } from "@angular/forms";

import { TrapInfo } from "../../../common/data/trap-info";
import { UIContextService } from "../../../common/services/ui-context.service";
import { PropertyDialogService } from "../../util/property-dialog.service";
import { VSTrapService } from "../../util/vs-trap.service";
import { ContextProvider } from "../../context-provider.service";
import { ChartPropertyDialog } from "./chart-property-dialog.component";
import { ChartPropertyDialogModel } from "../../model/chart-property-dialog-model";
import { ChartGeneralPaneModel } from "../../model/chart-general-pane-model";
import { ChartAdvancedPaneModel } from "../../model/chart-advanced-pane-model";
import { ChartLinePaneModel } from "../../model/chart-line-pane-model";
import { HierarchyPropertyPaneModel } from "../../model/hierarchy-property-pane-model";
import { VSAssemblyScriptPaneModel } from "../../../widget/dialog/vsassembly-script-pane/vsassembly-script-pane-model";
import { GeneralPropPaneModel } from "../../model/general-prop-pane-model";
import { BasicGeneralPaneModel } from "../../model/basic-general-pane-model";
import { ChartGeneralPane } from "./chart-general-pane.component";

// The component exposes the behavior under test only through protected hooks inherited from
// PropertyDialog, so this narrow subclass is the least-invasive way to exercise those contracts.
class TestChartPropertyDialog extends ChartPropertyDialog {
   public callClosing(isApply: boolean, collapse: boolean = false) {
      super.closing(isApply, collapse);
   }

   public callGetScripts(): string[] {
      return super.getScripts();
   }
}

type MockUIContextService = Pick<UIContextService, "getDefaultTab" | "setDefaultTab" | "getObjectChange">;
type MockPropertyDialogService = Pick<PropertyDialogService, "checkScript">;
type MockTrapService = Pick<VSTrapService, "checkTrap">;
type MockContextProvider = Pick<ContextProvider, "composer" | "composerBinding">;

function makeBasicGeneralPaneModel(
   overrides: Partial<BasicGeneralPaneModel> = {}
): BasicGeneralPaneModel {
   return {
      name: "Revenue Chart",
      visible: "true",
      shadow: false,
      enabled: true,
      primary: false,
      refresh: false,
      editable: false,
      nameEditable: true,
      showShadowCheckbox: false,
      showEnabledCheckbox: true,
      showRefreshCheckbox: false,
      showEditableCheckbox: false,
      objectNames: [],
      ...overrides,
   };
}

function makeGeneralPropPaneModel(
   overrides: Partial<GeneralPropPaneModel> = {}
): GeneralPropPaneModel {
   return {
      basicGeneralPaneModel: makeBasicGeneralPaneModel(),
      showSubmitCheckbox: false,
      submitOnChange: false,
      showEnabledGroup: false,
      popLocation: null,
      ...overrides,
   };
}

function makeChartGeneralPaneModel(
   overrides: Partial<ChartGeneralPaneModel> = {}
): ChartGeneralPaneModel {
   return {
      generalPropPaneModel: makeGeneralPropPaneModel(),
      tipPaneModel: {} as ChartGeneralPaneModel["tipPaneModel"],
      titlePropPaneModel: {} as ChartGeneralPaneModel["titlePropPaneModel"],
      sizePositionPaneModel: {} as ChartGeneralPaneModel["sizePositionPaneModel"],
      paddingPaneModel: {} as ChartGeneralPaneModel["paddingPaneModel"],
      ...overrides,
   };
}

function makeChartAdvancedPaneModel(
   overrides: Partial<ChartAdvancedPaneModel> = {}
): ChartAdvancedPaneModel {
   return {
      glossyEffect: false,
      enableAdhocEditing: false,
      sparkline: false,
      enableDrilling: false,
      chartPlotOptionsPaneModel: {} as ChartAdvancedPaneModel["chartPlotOptionsPaneModel"],
      chartTargetLinesPaneModel: {} as ChartAdvancedPaneModel["chartTargetLinesPaneModel"],
      ...overrides,
   };
}

function makeChartLinePaneModel(
   overrides: Partial<ChartLinePaneModel> = {}
): ChartLinePaneModel {
   return {
      xGridLineStyle: 0,
      xGridLineColor: "",
      yGridLineStyle: 0,
      yGridLineColor: "",
      gridLineVisible: false,
      quadrantGridLineStyle: 0,
      quadrantGridLineColor: "",
      diagonalLineStyle: 0,
      diagonalLineColor: "",
      innerLineVisible: false,
      trendLineType: "",
      trendPerColor: false,
      trendLineStyle: 0,
      trendLineColor: "",
      trendLineVisible: false,
      projectForward: 0,
      projectForwardEnabled: false,
      lineTabVisible: false,
      facetGrid: false,
      facetGridColor: "",
      facetGridVisible: false,
      facetGridEnabled: false,
      trendLineMeasures: [],
      measures: [],
      ...overrides,
   };
}

function makeHierarchyPropertyPaneModel(
   overrides: Partial<HierarchyPropertyPaneModel> = {}
): HierarchyPropertyPaneModel {
   return {
      isCube: true,
      hierarchyEditorModel: {} as HierarchyPropertyPaneModel["hierarchyEditorModel"],
      columnList: [{ attribute: "order_date" } as HierarchyPropertyPaneModel["columnList"][number]],
      dimensions: [{ members: [] }],
      grayedOutFields: [],
      ...overrides,
   };
}

function makeScriptPaneModel(
   overrides: Partial<VSAssemblyScriptPaneModel> = {}
): VSAssemblyScriptPaneModel {
   return {
      expression: "sum(price)",
      scriptEnabled: true,
      ...overrides,
   };
}

function makeModel(overrides: Partial<ChartPropertyDialogModel> = {}): ChartPropertyDialogModel {
   return {
      chartGeneralPaneModel: makeChartGeneralPaneModel(),
      chartAdvancedPaneModel: makeChartAdvancedPaneModel(),
      chartLinePaneModel: makeChartLinePaneModel(),
      hierarchyPropertyPaneModel: makeHierarchyPropertyPaneModel(),
      vsAssemblyScriptPaneModel: makeScriptPaneModel(),
      ...overrides,
   };
}

function createComponent(options: {
   lastTab?: string | null;
   contextProvider?: Partial<MockContextProvider>;
   model?: ChartPropertyDialogModel;
} = {}) {
   const objectChange$ = new Subject<{ action: string }>();
   const uiContextService: MockUIContextService = {
      getDefaultTab: vi.fn().mockReturnValue(options.lastTab ?? null),
      setDefaultTab: vi.fn(),
      getObjectChange: vi.fn().mockReturnValue(objectChange$),
   };
   const propertyDialogService: MockPropertyDialogService = {
      checkScript: vi.fn(),
   };
   const trapService: MockTrapService = {
      checkTrap: vi.fn(),
   };
   const contextProvider: MockContextProvider = {
      composer: false,
      composerBinding: null,
      ...options.contextProvider,
   };

   const comp = new TestChartPropertyDialog(
      uiContextService as UIContextService,
      propertyDialogService as PropertyDialogService,
      trapService as VSTrapService,
      contextProvider as ContextProvider
   );
   comp.model = options.model ?? makeModel();
   comp.runtimeId = "runtime-1";

   return {
      comp,
      uiContextService,
      propertyDialogService,
      trapService,
      objectChange$,
   };
}

afterEach(() => vi.restoreAllMocks());

describe("ChartPropertyDialog - Group 1: defaultTab and verifyLastTab", () => {
   it("should prefer the script tab when openToScript is true", () => {
      const { comp } = createComponent({ lastTab: "advanced-tab" });
      comp.openToScript = true;

      expect(comp.defaultTab).toBe(comp.scriptTab);
   });

   it("should reuse the saved line tab when the line pane is visible", () => {
      const { comp } = createComponent({
         lastTab: "line-tab",
         model: makeModel({
            chartLinePaneModel: makeChartLinePaneModel({ trendLineVisible: true }),
         }),
      });

      expect(comp.defaultTab).toBe(comp.lineTab);
   });

   it("should fall back to the advanced tab when the saved tab is invalid and composer is unavailable", () => {
      const { comp } = createComponent({ lastTab: "general-tab" });

      expect(comp.defaultTab).toBe(comp.advancedTab);
   });

   it("should fall back to the general tab when the saved tab is invalid and composer is available", () => {
      const { comp } = createComponent({
         lastTab: "line-tab",
         contextProvider: { composer: true },
      });

      expect(comp.defaultTab).toBe(comp.generalTab);
   });

   it("should persist the selected default tab through UIContextService", () => {
      const { comp, uiContextService } = createComponent();

      comp.defaultTab = comp.hierarchyTab;

      expect(uiContextService.setDefaultTab).toHaveBeenCalledWith(
         "chart-property-dialog",
         comp.hierarchyTab
      );
   });

   it("should reject a falsy saved tab", () => {
      const { comp } = createComponent();

      expect(comp.verifyLastTab("")).toBe(false);
   });

   it("should reject the line tab when the line pane is hidden", () => {
      const { comp } = createComponent();

      expect(comp.verifyLastTab(comp.lineTab)).toBe(false);
   });

   it("should allow the line tab when the line pane is visible", () => {
      const { comp } = createComponent({
         model: makeModel({
            chartLinePaneModel: makeChartLinePaneModel({ facetGridVisible: true }),
         }),
      });

      expect(comp.verifyLastTab(comp.lineTab)).toBe(true);
   });

   it("should require composer mode for the general tab", () => {
      const { comp } = createComponent({
         contextProvider: { composerBinding: true },
      });

      expect(comp.verifyLastTab(comp.generalTab)).toBe(true);
   });

   it("should require composer mode for the script tab", () => {
      const { comp } = createComponent({
         contextProvider: { composerBinding: true },
      });

      expect(comp.verifyLastTab(comp.scriptTab)).toBe(true);
   });

   it("should require composer mode for the hierarchy tab", () => {
      const { comp } = createComponent({
         contextProvider: { composerBinding: true },
      });

      expect(comp.verifyLastTab(comp.hierarchyTab)).toBe(true);
   });

   it("should always allow the advanced tab", () => {
      const { comp } = createComponent();

      expect(comp.verifyLastTab(comp.advancedTab)).toBe(true);
   });
});

describe("ChartPropertyDialog - Group 2: closing", () => {
   it("should emit a reduced model through onCommit after trap validation succeeds", () => {
      const model = makeModel({
         hierarchyPropertyPaneModel: makeHierarchyPropertyPaneModel({
            columnList: [
               { attribute: "order_date" } as HierarchyPropertyPaneModel["columnList"][number],
               { attribute: "ship_date" } as HierarchyPropertyPaneModel["columnList"][number],
            ],
         }),
      });
      const { comp, trapService } = createComponent({ model });
      const committed: unknown[] = [];
      comp.onCommit.subscribe(payload => committed.push(payload));

      vi.mocked(trapService.checkTrap).mockImplementation(
         (trapInfo: TrapInfo, onOk: () => void) => {
            expect(trapInfo.controllerURI).toBe(
               "../api/composer/vs/chart-property-dialog-model/checkTrap/Revenue Chart/runtime-1"
            );
            expect(trapInfo.payload).toEqual({
               chartGeneralPaneModel: model.chartGeneralPaneModel,
               chartAdvancedPaneModel: model.chartAdvancedPaneModel,
               chartLinePaneModel: model.chartLinePaneModel,
               hierarchyPropertyPaneModel: {
                  isCube: true,
                  hierarchyEditorModel: model.hierarchyPropertyPaneModel.hierarchyEditorModel,
                  columnList: [],
                  dimensions: model.hierarchyPropertyPaneModel.dimensions,
               },
               vsAssemblyScriptPaneModel: model.vsAssemblyScriptPaneModel,
            });
            onOk();
         }
      );

      comp.callClosing(false);

      expect(committed).toEqual([
         {
            chartGeneralPaneModel: model.chartGeneralPaneModel,
            chartAdvancedPaneModel: model.chartAdvancedPaneModel,
            chartLinePaneModel: model.chartLinePaneModel,
            hierarchyPropertyPaneModel: {
               isCube: true,
               hierarchyEditorModel: model.hierarchyPropertyPaneModel.hierarchyEditorModel,
               columnList: [],
               dimensions: model.hierarchyPropertyPaneModel.dimensions,
            },
            vsAssemblyScriptPaneModel: model.vsAssemblyScriptPaneModel,
         },
      ]);
   });

   it("should emit apply payload with collapse through the no-trap callback path", () => {
      const { comp, trapService } = createComponent();
      const applied: Array<{ collapse: boolean; result: unknown }> = [];
      comp.onApply.subscribe(payload => applied.push(payload));

      vi.mocked(trapService.checkTrap).mockImplementation(
         (_trapInfo: TrapInfo, _onOk: () => void, _onCancel: () => void, onNoTrap: () => void) => {
            onNoTrap();
         }
      );

      comp.callClosing(true, true);

      expect(applied).toEqual([
         {
            collapse: true,
            result: {
               chartGeneralPaneModel: comp.model.chartGeneralPaneModel,
               chartAdvancedPaneModel: comp.model.chartAdvancedPaneModel,
               chartLinePaneModel: comp.model.chartLinePaneModel,
               hierarchyPropertyPaneModel: {
                  isCube: comp.model.hierarchyPropertyPaneModel.isCube,
                  hierarchyEditorModel: comp.model.hierarchyPropertyPaneModel.hierarchyEditorModel,
                  columnList: [],
                  dimensions: comp.model.hierarchyPropertyPaneModel.dimensions,
               },
               vsAssemblyScriptPaneModel: comp.model.vsAssemblyScriptPaneModel,
            },
         },
      ]);
   });
});

describe("ChartPropertyDialog - Group 3: ngOnInit and getScripts", () => {
   it("should create all pane form groups during ngOnInit", () => {
      const { comp } = createComponent();

      comp.ngOnInit();

      expect(comp.form.controls["chartGeneralPaneForm"]).toBeInstanceOf(UntypedFormGroup);
      expect(comp.form.controls["chartAdvancePaneForm"]).toBeInstanceOf(UntypedFormGroup);
      expect(comp.form.controls["chartLinePaneForm"]).toBeInstanceOf(UntypedFormGroup);
   });

   it("should expose the current script expression through getScripts", () => {
      const { comp } = createComponent({
         model: makeModel({
            vsAssemblyScriptPaneModel: makeScriptPaneModel({ expression: "avg(discount)" }),
         }),
      });

      expect(comp.callGetScripts()).toEqual(["avg(discount)"]);
   });
});

describe("ChartPropertyDialog - Group 4: isValid", () => {
   it("should return true when the form is valid and alpha is valid", () => {
      const { comp } = createComponent();
      comp.ngOnInit();

      expect(comp.isValid()).toBe(true);
   });

   it("should return false when the form is invalid", () => {
      const { comp } = createComponent();
      comp.ngOnInit();
      comp.form.setErrors({ invalid: true });

      expect(comp.isValid()).toBe(false);
   });

   it("should return false when the chart general pane reports alphaInvalid", () => {
      const { comp } = createComponent();
      comp.ngOnInit();
      // The ViewChild is not created in this direct-instantiation spec, so inject the minimal
      // shape needed to drive the alphaInvalid guard deterministically.
      comp.chartGeneralPane = { alphaInvalid: true } as ChartGeneralPane;

      expect(comp.isValid()).toBe(false);
   });
});

describe("ChartPropertyDialog - Group 5: lightweight getters", () => {
   it("should detect composer mode from either composer object or composerBinding", () => {
      const composerDialog = createComponent({
         contextProvider: { composer: true },
      }).comp;
      const bindingDialog = createComponent({
         contextProvider: { composerBinding: true },
      }).comp;
      const plainDialog = createComponent().comp;

      expect(composerDialog.composer).toBe(true);
      expect(bindingDialog.composer).toBe(true);
      expect(plainDialog.composer).toBe(false);
   });

   it("should make the line pane visible from gridLineVisible", () => {
      const { comp } = createComponent({
         model: makeModel({
            chartLinePaneModel: makeChartLinePaneModel({ gridLineVisible: true }),
         }),
      });

      expect(comp.linePaneVisible).toBe(true);
   });

   it("should make the line pane visible from innerLineVisible", () => {
      const { comp } = createComponent({
         model: makeModel({
            chartLinePaneModel: makeChartLinePaneModel({ innerLineVisible: true }),
         }),
      });

      expect(comp.linePaneVisible).toBe(true);
   });

   it("should make the line pane visible from facetGridVisible", () => {
      const { comp } = createComponent({
         model: makeModel({
            chartLinePaneModel: makeChartLinePaneModel({ facetGridVisible: true }),
         }),
      });

      expect(comp.linePaneVisible).toBe(true);
   });

   it("should make the line pane visible from trendLineVisible", () => {
      const { comp } = createComponent({
         model: makeModel({
            chartLinePaneModel: makeChartLinePaneModel({ trendLineVisible: true }),
         }),
      });

      expect(comp.linePaneVisible).toBe(true);
   });

   it("should hide the line pane when all visibility flags are false", () => {
      const { comp } = createComponent();

      expect(comp.linePaneVisible).toBe(false);
   });
});
