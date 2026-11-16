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
 * The binding editor's dynamic combos name their field for the Expression pairing (bug #80459,
 * PR C). Each pane gets the (shelf, index) of its own field from the field component that owns
 * it, and a combo resolves a pairing only when that name, the binding assembly and the binding
 * runtime id all reach it. Pinned through the real panes:
 *  - chart-fieldmc: x/y/group fields by position, single-field shelves by name, and NO name for
 *    an aesthetic channel, the geo shelf or a field with no position;
 *  - dimension-editor / aggregate-editor / formula-option / sort-option: the dropdown panes get
 *    the shelf and index from chart-fieldmc;
 *  - table-fieldmc / group-option / crosstab-option: crosstab rows, cols and aggregates; a plain
 *    table's groups have no shelf in the plugin and stay unpaired;
 *  - calculate-pane-dialog: the crosstab-wide percentage direction.
 */
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { TestBed } from "@angular/core/testing";
import { By } from "@angular/platform-browser";
import { render } from "@testing-library/angular";
import userEvent from "@testing-library/user-event";
import { of } from "rxjs";
import { XSchema } from "../../common/data/xschema";
import { DateLevelExamplesService } from "../../common/services/date-level-examples.service";
import { UIContextService } from "../../common/services/ui-context.service";
import { TestUtils } from "../../common/test/test-utils";
import { ComponentTool } from "../../common/util/component-tool";
import { StyleConstants } from "../../common/util/style-constants";
import { XConstants } from "../../common/util/xconstants";
import { SheetClientRegistry } from "../../common/viewsheet-client/sheet-client-registry.service";
import { DynamicComboBox } from "../../widget/dynamic-combo-box/dynamic-combo-box.component";
import { PercentCalcInfo } from "../data/chart/calculate-info";
import { BindingService } from "../services/binding.service";
import { TableEditorService } from "../services/table/table-editor.service";
import { AggregateEditor } from "./chart/field/aggregate-editor.component";
import { CalculatePaneDialog } from "./chart/field/calculate-pane-dialog.component";
import { renderChartFieldmc } from "./chart/field/chart-fieldmc.component.test-helpers";
import { DimensionEditor } from "./chart/field/dimension-editor.component";
import { FormulaOption } from "./formula-option.component";
import { SortOption } from "./sort-option.component";
import { AggregateOption } from "./table/aggregate-option.component";
import { CrosstabOption } from "./table/crosstab-option.component";
import { GroupOption } from "./table/group-option.component";
import {
   createCrosstabModel, renderTableFieldmc, bindingServiceMock as tableBindingMock
} from "./table/table-fieldmc.component.test-helpers";

const RUNTIME = "binding-rt-7";

function binding(objectType: string, assemblyName: string): any {
   return {
      runtimeId: RUNTIME, assemblyName, objectType, variableValues: [],
      bindingModel: null, getBindingModel: vi.fn(() => null), getURLParams: vi.fn()
   };
}

/** Registers the binding pane's client for RUNTIME, as vs-binding-pane does outside the viewer. */
function registerBindingClient() {
   TestBed.inject(SheetClientRegistry).register({ runtimeId: RUNTIME } as any, "VIEWSHEET");
}

function combosOf(fixture: any): DynamicComboBox[] {
   return fixture.debugElement.queryAll(By.directive(DynamicComboBox))
      .map((d: any) => d.componentInstance);
}

/** The pairing the combo's formula editor would be given. */
function pairingOf(combo: DynamicComboBox): any {
   const dialog: any = {};
   vi.spyOn(ComponentTool, "showDialog").mockReturnValue(dialog);
   combo.showFormulaEditor();
   return dialog;
}

function expectPaired(combo: DynamicComboBox, name: string, assembly: string) {
   const dialog = pairingOf(combo);
   expect(dialog.dynamicProperty).toBe(name);
   expect(dialog.contextTable).toBe(assembly);
   expect(dialog.runtimeId).toBe(RUNTIME);
   expect(dialog.isVSContext).toBe(true);
}

function crosstabWithOption(): any {
   const model = createCrosstabModel();
   model.option = { percentageByValue: "1" } as any;
   return model;
}

const examplesMock = { loadDateLevelExamples: vi.fn(() => of({ dateLevelExamples: [] })) };

afterEach(() => vi.restoreAllMocks());

describe("chart-fieldmc names the field by shelf and position", () => {
   async function column(props: Record<string, any>) {
      const field = TestUtils.createMockChartDimensionRef("state");
      field.columnValue = "state";
      const view = await renderChartFieldmc({ field, ...props });
      return combosOf(view.fixture)[0];
   }

   it.each([
      ["xfields", 2, "binding:x[2].column"],
      ["yfields", 0, "binding:y[0].column"],
      ["groupfields", 1, "binding:group[1].column"],
   ])("%s field %i is named by its position", async (fieldType, index, expected) => {
      expect((await column({ fieldType, index })).dynamicProperty).toBe(expected);
   });

   it("a single-field shelf is its own name at position 0", async () => {
      expect((await column({ fieldType: "milestone" })).dynamicProperty)
         .toBe("binding:milestone[0].column");
   });

   it.each([
      ["an aesthetic channel", { fieldType: "color", isAesthetic: true }],
      ["an aesthetic x field", { fieldType: "xfields", index: 0, isAesthetic: true }],
      ["the geo shelf", { fieldType: "geofields", index: 0 }],
      ["a list field without a position", { fieldType: "xfields" }],
   ])("%s stays unnamed", async (_label, props) => {
      expect((await column(props)).dynamicProperty).toBeNull();
   });

   it("the column combo pairs against the binding runtime and assembly", async () => {
      const field = TestUtils.createMockChartDimensionRef("state");
      field.columnValue = "state";
      const view = await renderChartFieldmc({ field, fieldType: "yfields", index: 3 });
      TestBed.inject(SheetClientRegistry).register({ runtimeId: "runtime-1" } as any, "VIEWSHEET");

      const dialog = pairingOf(combosOf(view.fixture)[0]);
      expect(dialog.dynamicProperty).toBe("binding:y[3].column");
      expect(dialog.contextTable).toBe("Chart1");
      expect(dialog.runtimeId).toBe("runtime-1");
   });

   it("is not paired when no pane registered the binding runtime", async () => {
      const field = TestUtils.createMockChartDimensionRef("state");
      field.columnValue = "state";
      const view = await renderChartFieldmc({ field, fieldType: "yfields", index: 3 });

      expect(pairingOf(combosOf(view.fixture)[0]).dynamicProperty).toBeUndefined();
   });
});

describe("chart-fieldmc hands its shelf and index to its dropdown panes", () => {
   async function openDropdown(field: any, props: Record<string, any>) {
      const { container } = await renderChartFieldmc({ field, ...props });
      await userEvent.click(container.querySelector(".fieldEditIcon i.btn-icon") as HTMLElement);
   }

   it("a y dimension reaches dimension-editor", async () => {
      await openDropdown(TestUtils.createMockChartDimensionRef("state"),
                         { fieldType: "yfields", index: 1 });
      const editor = document.querySelector("dimension-editor");

      expect(editor?.getAttribute("ng-reflect-binding-shelf")).toBe("y");
      expect(editor?.getAttribute("ng-reflect-binding-index")).toBe("1");
   });

   it("an x measure reaches aggregate-editor", async () => {
      const aggregate = TestUtils.createMockChartAggregateRef("sales");
      await openDropdown(aggregate, { fieldType: "xfields", index: 3 });
      const editor = document.querySelector("aggregate-editor");

      expect(editor?.getAttribute("ng-reflect-binding-shelf")).toBe("x");
      expect(editor?.getAttribute("ng-reflect-binding-index")).toBe("3");
   });
});

describe("chart dropdown panes", () => {
   it("dimension-editor: date level and the sort/ranking combos carry the shelf and index",
      async () =>
   {
      const dimension = TestUtils.createMockChartDimensionRef("orderdate");
      dimension.dataType = XSchema.DATE;
      dimension.dateLevel = XConstants.YEAR_DATE_GROUP + "";
      dimension.timeSeries = false;
      dimension.order = StyleConstants.SORT_VALUE_ASC;
      dimension.sortOptionModel = { aggregateRefs: [{ label: "Sum(a)", value: "Sum(a)" }] } as any;
      const bindingModel = TestUtils.createMockChartBindingModel();
      const { fixture } = await render(DimensionEditor, {
         schemas: [NO_ERRORS_SCHEMA],
         providers: [
            { provide: BindingService, useValue: binding("VSChart", "Chart1") },
            { provide: UIContextService, useValue: { isAdhoc: vi.fn(() => false) } },
            { provide: DateLevelExamplesService, useValue: examplesMock },
         ],
         componentProperties: {
            dimension, bindingModel, fieldType: "xfields", vsId: RUNTIME, variables: [],
            sortSupported: true, bindingShelf: "x", bindingIndex: 1
         },
      });
      registerBindingClient();

      const names = combosOf(fixture).map(c => c.dynamicProperty).sort();
      expect(names).toEqual([
         "binding:x[1].dateLevel", "binding:x[1].ranking.measure", "binding:x[1].ranking.mode",
         "binding:x[1].ranking.n", "binding:x[1].sort.sortByField"]);

      // a disabled combo (ranking N / measure without a ranking) never pairs
      for(const combo of combosOf(fixture).filter(c => !c.disable)) {
         expectPaired(combo, combo.dynamicProperty, "Chart1");
      }

      expectPaired(combosOf(fixture).find(c => c.dynamicProperty?.endsWith(".dateLevel")),
                   "binding:x[1].dateLevel", "Chart1");
   });

   it("aggregate-editor hands its shelf and index to formula-option", async () => {
      const aggregate = TestUtils.createMockChartAggregateRef("sales");
      aggregate.measure = true;
      const { fixture } = await render(AggregateEditor, {
         schemas: [NO_ERRORS_SCHEMA],
         providers: [
            { provide: BindingService, useValue: binding("VSChart", "Chart1") },
            { provide: UIContextService, useValue: { isAdhoc: vi.fn(() => false) } },
         ],
         componentProperties: {
            aggregate, fieldType: "yfields", vsId: RUNTIME, variables: [], availableFields: [],
            bindingShelf: "y", bindingIndex: 2
         },
      });

      const formula = fixture.debugElement.query(By.directive(FormulaOption)).componentInstance;
      expect(formula.bindingShelf).toBe("y");
      expect(formula.bindingIndex).toBe(2);
   });
});

describe("formula-option and sort-option", () => {
   it("formula-option names aggregate, secondaryColumn and n", async () => {
      const aggregate = TestUtils.createMockBAggregateRef("sales");
      aggregate.formula = "Correlation";
      aggregate.secondaryColumnValue = "profit";
      aggregate.numValue = "2";
      const { fixture } = await render(FormulaOption, {
         schemas: [NO_ERRORS_SCHEMA],
         providers: [{ provide: BindingService, useValue: binding("VSCrosstab", "Crosstab1") }],
         componentProperties: {
            aggregate, vsId: RUNTIME, variables: [], availableFields: [], formulaObjs: [],
            bindingShelf: "aggregates", bindingIndex: 4
         },
      });
      registerBindingClient();
      fixture.componentInstance.hasN = () => true;
      fixture.detectChanges();

      const names = combosOf(fixture).map(c => c.dynamicProperty);
      expect(names).toContain("binding:aggregates[4].aggregate");
      expect(names).toContain("binding:aggregates[4].secondaryColumn");
      expect(names).toContain("binding:aggregates[4].n");

      for(const combo of combosOf(fixture)) {
         expectPaired(combo, combo.dynamicProperty, "Crosstab1");
      }
   });

   it("formula-option without a shelf stays unpaired", async () => {
      const aggregate = TestUtils.createMockBAggregateRef("sales");
      const { fixture } = await render(FormulaOption, {
         schemas: [NO_ERRORS_SCHEMA],
         providers: [{ provide: BindingService, useValue: binding("VSTable", "Table1") }],
         componentProperties: {
            aggregate, vsId: RUNTIME, variables: [], availableFields: [], formulaObjs: []
         },
      });
      registerBindingClient();

      expect(combosOf(fixture).map(c => c.dynamicProperty)).toEqual([null]);
      expect(pairingOf(combosOf(fixture)[0]).dynamicProperty).toBeUndefined();
   });
});

describe("crosstab panes", () => {
   afterEach(() => {
      tableBindingMock.objectType = "vstable";
   });

   it.each([
      ["VSCrosstab", "rows", "binding:rows[1].column"],
      ["VSCrosstab", "cols", "binding:cols[1].column"],
      ["VSCrosstab", "aggregates", "binding:aggregates[1].column"],
      ["VSCrosstab", "groups", null],
      ["VSCrosstab", "details", null],
      ["VSTable", "rows", null],
      ["VSTable", "groups", null],
      ["VSTable", "aggregates", null],
   ])("table-fieldmc of a %s names a %s field %s", async (objectType, fieldType, expected) => {
      tableBindingMock.objectType = objectType;
      const { fixture } = await renderTableFieldmc({
         _model: createCrosstabModel(), field: TestUtils.createMockBDimensionRef("state"),
         fieldType, dragIndex: 1
      });
      expect(combosOf(fixture)[0].dynamicProperty).toBe(expected);
   });

   it("aggregate-option hands its shelf and index to formula-option", async () => {
      const aggregate = TestUtils.createMockBAggregateRef("sales");
      aggregate.formula = "Sum";
      const { fixture } = await render(AggregateOption, {
         schemas: [NO_ERRORS_SCHEMA],
         providers: [
            {
               provide: BindingService,
               useValue: { ...binding("VSCrosstab", "Crosstab1"),
                           bindingModel: crosstabWithOption() }
            },
            { provide: UIContextService, useValue: { isAdhoc: vi.fn(() => false) } },
         ],
         componentProperties: {
            aggregate, vsId: RUNTIME, variables: [], availableFields: [], groupNum: 1,
            bindingShelf: "aggregates", bindingIndex: 5
         },
      });

      const formula = fixture.debugElement.query(By.directive(FormulaOption)).componentInstance;
      expect(formula.bindingShelf).toBe("aggregates");
      expect(formula.bindingIndex).toBe(5);
   });

   it("table-fieldmc hands its shelf and index to aggregate-option", async () => {
      tableBindingMock.objectType = "VSCrosstab";
      const field: any = TestUtils.createMockBAggregateRef("sales");
      field.formula = "Sum";
      const { container } = await renderTableFieldmc({
         _model: crosstabWithOption(), field, fieldType: "aggregates", dragIndex: 2
      });
      await userEvent.click(container.querySelector(".fieldEditIcon i.measure-setting-icon, " +
                            ".fieldEditIcon i.dimension-setting-icon") as HTMLElement);
      const option = document.querySelector("aggregate-option");

      expect(option?.getAttribute("ng-reflect-binding-shelf")).toBe("aggregates");
      expect(option?.getAttribute("ng-reflect-binding-index")).toBe("2");
   });

   it("group-option names the crosstab dimension's sort, ranking and date level", async () => {
      const field = TestUtils.createMockBDimensionRef("orderdate");
      field.dataType = XSchema.DATE;
      field.dateLevel = XConstants.YEAR_DATE_GROUP + "";
      field.order = StyleConstants.SORT_ASC;
      field.sortOptionModel = { aggregateRefs: [] } as any;
      const { fixture } = await render(GroupOption, {
         schemas: [NO_ERRORS_SCHEMA],
         providers: [
            {
               provide: BindingService,
               useValue: { ...binding("VSCrosstab", "Crosstab1"),
                           getBindingModel: vi.fn(() => TestUtils.createMockCrosstabBindingModel()) }
            },
            { provide: UIContextService, useValue: { isAdhoc: vi.fn(() => false) } },
            { provide: DateLevelExamplesService, useValue: examplesMock },
         ],
         componentProperties: {
            field, fieldType: "cols", dragIndex: 2, vsId: RUNTIME, variables: [], source: null
         },
      });
      registerBindingClient();

      const names = combosOf(fixture).map(c => c.dynamicProperty).filter(n => !!n).sort();
      expect(names).toEqual([
         "binding:cols[2].dateLevel", "binding:cols[2].ranking.measure",
         "binding:cols[2].ranking.mode", "binding:cols[2].ranking.n"]);

      const level = combosOf(fixture).find(c => c.dynamicProperty?.endsWith(".dateLevel"));
      expectPaired(level, "binding:cols[2].dateLevel", "Crosstab1");
   });

   it("crosstab-option names the row and column totals", async () => {
      const option: any = { rowTotalVisibleValue: "true", colTotalVisibleValue: "false" };
      const { fixture } = await render(CrosstabOption, {
         schemas: [NO_ERRORS_SCHEMA],
         providers: [
            { provide: BindingService, useValue: binding("VSCrosstab", "Crosstab1") },
            { provide: UIContextService, useValue: { isVS: vi.fn(() => true) } },
            { provide: TableEditorService, useValue: { setBindingModel: vi.fn() } },
         ],
         componentProperties: { option, vsId: RUNTIME, variables: [], formatOptionVisible: false },
      });
      registerBindingClient();

      expect(combosOf(fixture).map(c => c.dynamicProperty))
         .toEqual(["tableOption:rowTotals", "tableOption:colTotals"]);
      expectPaired(combosOf(fixture)[0], "tableOption:rowTotals", "Crosstab1");
      expectPaired(combosOf(fixture)[1], "tableOption:colTotals", "Crosstab1");
   });

   it("calculate-pane-dialog names the crosstab-wide percentage direction", async () => {
      const calculator = new PercentCalcInfo();
      calculator.classType = "PERCENT";
      calculator.level = 1;
      const { fixture } = await render(CalculatePaneDialog, {
         schemas: [NO_ERRORS_SCHEMA],
         componentInputs: {
            calculator, crosstab: true, isVs: true, supportPercentageDirection: true,
            runtimeId: RUNTIME, assemblyName: "Crosstab1", variables: [],
            percentageDirection: String(StyleConstants.PERCENTAGE_BY_COL),
            percOfDims: [], percOfLevels: [], valueOfDatas: [],
            resetOptsMap: new Map(), supportResetMap: new Map()
         },
      });
      registerBindingClient();

      const combo = combosOf(fixture).find(c => c.dynamicProperty);
      expect(combo?.dynamicProperty).toBe("tableOption:percentageBy");
      expectPaired(combo, "tableOption:percentageBy", "Crosstab1");
   });
});
