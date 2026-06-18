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
 * VSWizardAggregatePane — single pass
 *
 * Risk-first coverage:
 *   Group 1  [Risk 3] — addGroup(): clones dim, inserts next dateLevel at idx+1, updates
 *                        originalIndex of subsequent dims, emits onAddDimension
 *   Group 2  [Risk 2] — addAggregate(): clones agg, clears colorFrame, inserts at idx+1,
 *                        updates originalIndex, emits onAddAggregate
 *   Group 3  [Risk 3] — deleteGroup() + fixDetails(): removes dim from array; when isDetail=true
 *                        also removes matching detail refs by columnValue; emits onDeleteDimension
 *   Group 4  [Risk 2] — deleteAggregate() + fixDetails(): mirrors Group 3 for measures;
 *                        fixDetails no-op when isDetail=false
 *   Group 5  [baseline] — deleteDetail(): splices details array, emits onUpdateDetails with name
 *   Group 6  [Risk 2] — showDimensionName(): idx=0 always true; non-date always true;
 *                        date with same name as prev → false; date with different name → true
 *   Group 7  [baseline] — showAggregateName(): idx=0 true; same name as prev → false;
 *                          different name → true
 *   Group 8  [baseline] — showDimensionMore() / showAggregateMore(): non-date → false;
 *                          last element → true; name differs from next → true; name matches next → false
 *   Group 9  [Risk 3] — moveUpDim() / moveDownDim(): swaps two non-date dims; clears original;
 *                        emits onAddDimension (autoOrder=false) OR calls setAutoOrder(false)
 *                        (autoOrder=true)
 *   Group 10 [Risk 2] — moveUpMeasure() / moveDownMeasure(): swaps two measures; clears original;
 *                        emits onAddAggregate or setAutoOrder(false)
 *   Group 11 [baseline] — moveUpDetail() / moveDownDetail(): sends UPDATE_COLUMNS event with
 *                          correct transfer/dropTarget indices
 *   Group 12 [baseline] — isMoveDownDimEnabled() / isMoveDownMeasureEnabled()
 *   Group 13 [baseline] — hasItem(): true/false combinations of empty arrays
 *   Group 14 [baseline] — setAutoOrder(): sets property and emits onAutoOrderChange
 *   Group 15 [Risk 2] — getFormat(): uses ref.name when isDetail=true, ref.fullName when false;
 *                         null when formatMap absent
 *   Group 16 [baseline] — getGrayedOutValues(): extracts names; empty when null/empty input
 *   Group 17 [Risk 2] — updateAggregateFormat(): sends UPDATE_WIZARD_BINDING_FORMAT with
 *                         correct fieldName+format; no-op when formatMap absent or key missing
 *
 * Confirmed bugs (it.fails): none
 *
 * Out of scope:
 *   getMeasureFormat() + getAggrFormatKey() formula-branch paths — depend on
 *     AggregateFormula.isSameTypeFormula and SummaryAttrUtil which require representative measure
 *     data not available without fixture data; display-pass candidate
 *   getForceDisplayFormula() — only meaningful with a populated fixedFormulaMap; tested
 *     indirectly via updateAggregateFormat() Group 17
 */

import { Component, NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";

import { VSWizardAggregatePane } from "./wizard-aggregate-pane.component";
import { VSWizardGroupItem } from "./wizard-group-item.component";
import { VSWizardAggregateItem } from "./wizard-aggregate-item.component";
import { VSWizardDetailItem } from "./wizard-detail-item.component";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { DateRangeRef } from "../../../common/util/date-range-ref";
import { ChartDimensionRef } from "../../../binding/data/chart/chart-dimension-ref";
import { ChartAggregateRef } from "../../../binding/data/chart/chart-aggregate-ref";
import { DataRef } from "../../../common/data/data-ref";
import { VSObjectType } from "../../../common/data/vs-object-type";

// Stub the three child components so their injected deps don't leak into these tests.
// NO_ERRORS_SCHEMA alone does not suppress dependencies of components in imports[].
const StubGroupItem = Component({ selector: "wizard-group-item", template: "" })(class {});
const StubAggregateItem = Component({ selector: "wizard-aggregate-item", template: "" })(class {});
const StubDetailItem = Component({ selector: "wizard-detail-item", template: "" })(class {});

const SEND_EVENT_MOCK = vi.fn();

const VS_CLIENT_MOCK = { sendEvent: SEND_EVENT_MOCK };

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeDim(
   name: string,
   opts: { dateLevel?: string; dataType?: string; original?: any } = {},
): ChartDimensionRef {
   return {
      name,
      fullName: name,
      columnValue: name,
      dateLevel: opts.dateLevel ?? "0",
      dataType: opts.dataType ?? "string",
      original: opts.original ?? null,
   } as unknown as ChartDimensionRef;
}

function makeAgg(
   name: string,
   opts: { formula?: string; colorFrame?: any; original?: any } = {},
): ChartAggregateRef {
   return {
      name,
      fullName: `Sum(${name})`,
      columnValue: name,
      attribute: name,
      formula: opts.formula ?? "Sum",
      colorFrame: opts.colorFrame ?? undefined,
      original: opts.original ?? null,
   } as unknown as ChartAggregateRef;
}

function makeDetail(name: string): DataRef {
   return { name } as unknown as DataRef;
}

interface RenderInputs {
   dimensions?: ChartDimensionRef[];
   measures?: ChartAggregateRef[];
   details?: DataRef[];
   isDetail?: boolean;
   autoOrder?: boolean;
   showAutoOrder?: boolean;
   formatMap?: Map<string, any> | null;
   fixedFormulaMap?: any[];
   objectType?: VSObjectType;
   grayedOutFields?: DataRef[];
}

async function renderComponent(inputs: RenderInputs = {}) {
   const result = await render(VSWizardAggregatePane, {
      inputs: {
         dimensions: inputs.dimensions ?? [],
         measures: inputs.measures ?? [],
         details: inputs.details ?? [],
         isDetail: inputs.isDetail ?? true,
         autoOrder: inputs.autoOrder ?? false,
         showAutoOrder: inputs.showAutoOrder ?? false,
         formatMap: inputs.formatMap ?? null,
         fixedFormulaMap: inputs.fixedFormulaMap ?? [],
         objectType: inputs.objectType ?? "VSTable" as any,
         grayedOutFields: inputs.grayedOutFields ?? [],
      },
      providers: [
         { provide: ViewsheetClientService, useValue: VS_CLIENT_MOCK },
      ],
      importOverrides: [
         { replace: VSWizardGroupItem, with: StubGroupItem },
         { replace: VSWizardAggregateItem, with: StubAggregateItem },
         { replace: VSWizardDetailItem, with: StubDetailItem },
      ],
      schemas: [NO_ERRORS_SCHEMA],
   });
   return { comp: result.fixture.componentInstance as VSWizardAggregatePane, fixture: result.fixture };
}

beforeEach(() => {
   SEND_EVENT_MOCK.mockClear();
});

// ---------------------------------------------------------------------------
// Group 1 — addGroup() [Risk 3]
// ---------------------------------------------------------------------------

describe("VSWizardAggregatePane — addGroup()", () => {
   // 🔁 Regression-sensitive: the inserted dim must get the NEXT date level (not the same or
   // a random one), and subsequent dims' originalIndex must be incremented. If either is wrong
   // the wizard displays duplicate or out-of-order date granularities.
   it("should insert a clone with the next date level at idx+1", async () => {
      // YEAR_INTERVAL (5) → next is QUARTER_OF_YEAR_PART (513) for "timeInstant"
      const dim = makeDim("SaleDate", { dateLevel: "5", dataType: "timeInstant" });
      const { comp } = await renderComponent({ dimensions: [dim] });

      comp.addGroup(0);

      expect(comp.dimensions).toHaveLength(2);
      expect(comp.dimensions[1].dateLevel).toBe(String(DateRangeRef.QUARTER_OF_YEAR_PART));
   });

   it("should not mutate the original dim — the inserted one is a clone", async () => {
      const dim = makeDim("SaleDate", { dateLevel: "5", dataType: "timeInstant" });
      const { comp } = await renderComponent({ dimensions: [dim] });

      comp.addGroup(0);

      expect(comp.dimensions[0]).toBe(dim);
      expect(comp.dimensions[1]).not.toBe(dim);
   });

   it("should increment originalIndex for dims after the insertion point", async () => {
      const dimA = makeDim("A", { dateLevel: "5", dataType: "timeInstant" });
      const dimB = makeDim("B", { original: { index: 1 } });
      const { comp } = await renderComponent({ dimensions: [dimA, dimB] });

      comp.addGroup(0);

      // dimB shifted from index 1 to index 2
      expect(comp.dimensions[2].original?.index).toBe(2);
   });

   it("should emit onAddDimension", async () => {
      const dim = makeDim("D", { dateLevel: "5", dataType: "timeInstant" });
      const { comp } = await renderComponent({ dimensions: [dim] });
      const emitted: null[] = [];
      comp.onAddDimension.subscribe(() => emitted.push(null));

      comp.addGroup(0);

      expect(emitted).toHaveLength(1);
   });
});

// ---------------------------------------------------------------------------
// Group 2 — addAggregate() [Risk 2]
// ---------------------------------------------------------------------------

describe("VSWizardAggregatePane — addAggregate()", () => {
   // 🔁 Regression-sensitive: cloning without clearing colorFrame causes duplicate color
   // assignments — two measures share the same frame and one appears invisible.
   it("should insert a clone of the measure at idx+1", async () => {
      const agg = makeAgg("Sales");
      const { comp } = await renderComponent({ measures: [agg] });

      comp.addAggregate(0);

      expect(comp.measures).toHaveLength(2);
      expect(comp.measures[1]).not.toBe(agg);
      expect(comp.measures[1].name).toBe("Sales");
   });

   it("should clear colorFrame on the inserted clone", async () => {
      const agg = makeAgg("Sales", { colorFrame: { type: "StaticColorFrame" } });
      const { comp } = await renderComponent({ measures: [agg] });

      comp.addAggregate(0);

      expect(comp.measures[1].colorFrame).toBeNull();
   });

   it("should emit onAddAggregate", async () => {
      const agg = makeAgg("Revenue");
      const { comp } = await renderComponent({ measures: [agg] });
      const emitted: null[] = [];
      comp.onAddAggregate.subscribe(() => emitted.push(null));

      comp.addAggregate(0);

      expect(emitted).toHaveLength(1);
   });
});

// ---------------------------------------------------------------------------
// Group 3 — deleteGroup() + fixDetails() [Risk 3]
// ---------------------------------------------------------------------------

describe("VSWizardAggregatePane — deleteGroup() + fixDetails()", () => {
   // 🔁 Regression-sensitive: fixDetails() scans the remaining dimensions array and removes
   // any other entries whose name matches the deleted dimension's columnValue. This handles
   // the case where a date column has multiple expanded granularity rows (year, quarter, month)
   // — deleting one should remove all siblings. When isDetail=false the cleanup is skipped.
   it("should remove the dim at the given index", async () => {
      const dimA = makeDim("Region");
      const dimB = makeDim("Year");
      const { comp } = await renderComponent({ dimensions: [dimA, dimB] });

      comp.deleteGroup(0);

      expect(comp.dimensions).toHaveLength(1);
      expect(comp.dimensions[0].name).toBe("Year");
   });

   it("should remove remaining dimensions with the same name when isDetail=true", async () => {
      // Simulates two date-level expansions of the same "SaleDate" column
      const dimA = makeDim("SaleDate");
      const dimB = makeDim("SaleDate");
      const dimC = makeDim("Year");
      const { comp } = await renderComponent({ dimensions: [dimA, dimB, dimC], isDetail: true });

      comp.deleteGroup(0);

      // fixDetails removes dimB (same name "SaleDate") from the remaining dimensions
      expect(comp.dimensions.map(d => d.name)).toEqual(["Year"]);
   });

   it("should NOT remove same-name siblings when isDetail=false", async () => {
      const dimA = makeDim("SaleDate");
      const dimB = makeDim("SaleDate");
      const { comp } = await renderComponent({ dimensions: [dimA, dimB], isDetail: false });

      comp.deleteGroup(0);

      // Only the explicitly deleted dim is removed; fixDetails is a no-op
      expect(comp.dimensions.map(d => d.name)).toEqual(["SaleDate"]);
   });

   it("should emit onDeleteDimension with the removed dim", async () => {
      const dim = makeDim("Region");
      const { comp } = await renderComponent({ dimensions: [dim] });
      const emitted: ChartDimensionRef[] = [];
      comp.onDeleteDimension.subscribe(v => emitted.push(v));

      comp.deleteGroup(0);

      expect(emitted).toHaveLength(1);
      expect(emitted[0]).toBe(dim);
   });
});

// ---------------------------------------------------------------------------
// Group 4 — deleteAggregate() + fixDetails() [Risk 2]
// ---------------------------------------------------------------------------

describe("VSWizardAggregatePane — deleteAggregate() + fixDetails()", () => {
   it("should remove the measure at the given index", async () => {
      const aggA = makeAgg("Sales");
      const aggB = makeAgg("Profit");
      const { comp } = await renderComponent({ measures: [aggA, aggB] });

      comp.deleteAggregate(0);

      expect(comp.measures).toHaveLength(1);
      expect(comp.measures[0].name).toBe("Profit");
   });

   it("should emit onDeleteAggregate with the removed measure", async () => {
      const agg = makeAgg("Sales");
      const { comp } = await renderComponent({ measures: [agg, makeAgg("Profit")] });
      const emitted: ChartAggregateRef[] = [];
      comp.onDeleteAggregate.subscribe(v => emitted.push(v));

      comp.deleteAggregate(0);

      expect(emitted).toHaveLength(1);
      expect(emitted[0]).toBe(agg);
   });

   it("should remove remaining measures with the same name when isDetail=true", async () => {
      // Two measures with the same column name — deleting one removes the sibling
      const aggA = makeAgg("Sales");
      const aggB = makeAgg("Sales");
      const aggC = makeAgg("Profit");
      const { comp } = await renderComponent({ measures: [aggA, aggB, aggC], isDetail: true });

      comp.deleteAggregate(0);

      expect(comp.measures.map(m => m.name)).toEqual(["Profit"]);
   });
});

// ---------------------------------------------------------------------------
// Group 5 — deleteDetail() [baseline]
// ---------------------------------------------------------------------------

describe("VSWizardAggregatePane — deleteDetail()", () => {
   it("should remove the detail at the given index", async () => {
      const details = [makeDetail("Col1"), makeDetail("Col2"), makeDetail("Col3")];
      const { comp } = await renderComponent({ details });

      comp.deleteDetail(1);

      expect(comp.details.map(d => d.name)).toEqual(["Col1", "Col3"]);
   });

   it("should emit onUpdateDetails with the name of the removed detail", async () => {
      const details = [makeDetail("Revenue"), makeDetail("Units")];
      const { comp } = await renderComponent({ details });
      const emitted: string[] = [];
      comp.onUpdateDetails.subscribe(v => emitted.push(v));

      comp.deleteDetail(0);

      expect(emitted).toEqual(["Revenue"]);
   });
});

// ---------------------------------------------------------------------------
// Group 6 — showDimensionName() [Risk 2]
// ---------------------------------------------------------------------------

describe("VSWizardAggregatePane — showDimensionName()", () => {
   // 🔁 Regression-sensitive: returning the wrong value causes duplicate dimension headers
   // or missing headers in the wizard's field list, confusing users about which date
   // granularity they're configuring.
   it("should return true for idx=0 regardless of dataType", async () => {
      const dim = makeDim("Date", { dataType: "timeInstant" });
      const { comp } = await renderComponent({ dimensions: [dim] });

      expect(comp.showDimensionName(0)).toBe(true);
   });

   it("should return true for non-date dataType at any index", async () => {
      const dims = [makeDim("Region"), makeDim("Region")];
      const { comp } = await renderComponent({ dimensions: dims });

      expect(comp.showDimensionName(1)).toBe(true);
   });

   it("should return false for date dim with same name as previous dim", async () => {
      const dims = [
         makeDim("SaleDate", { dataType: "timeInstant" }),
         makeDim("SaleDate", { dataType: "timeInstant" }),
      ];
      const { comp } = await renderComponent({ dimensions: dims });

      expect(comp.showDimensionName(1)).toBe(false);
   });

   it("should return true for date dim with different name from previous dim", async () => {
      const dims = [
         makeDim("OrderDate", { dataType: "timeInstant" }),
         makeDim("ShipDate", { dataType: "timeInstant" }),
      ];
      const { comp } = await renderComponent({ dimensions: dims });

      expect(comp.showDimensionName(1)).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 7 — showAggregateName() [baseline]
// ---------------------------------------------------------------------------

describe("VSWizardAggregatePane — showAggregateName()", () => {
   it("should return true for idx=0", async () => {
      const { comp } = await renderComponent({ measures: [makeAgg("Sales")] });
      expect(comp.showAggregateName(0)).toBe(true);
   });

   it("should return false when same name as previous aggregate", async () => {
      const { comp } = await renderComponent({ measures: [makeAgg("Sales"), makeAgg("Sales")] });
      expect(comp.showAggregateName(1)).toBe(false);
   });

   it("should return true when different name from previous aggregate", async () => {
      const { comp } = await renderComponent({ measures: [makeAgg("Sales"), makeAgg("Revenue")] });
      expect(comp.showAggregateName(1)).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 8 — showDimensionMore() / showAggregateMore() [baseline]
// ---------------------------------------------------------------------------

describe("VSWizardAggregatePane — showDimensionMore() / showAggregateMore()", () => {
   it("showDimensionMore() should return false for non-date dataType", async () => {
      const { comp } = await renderComponent({ dimensions: [makeDim("Region")] });
      expect(comp.showDimensionMore(0)).toBe(false);
   });

   it("showDimensionMore() should return true for last date dim (no next)", async () => {
      const dim = makeDim("Date", { dataType: "timeInstant" });
      const { comp } = await renderComponent({ dimensions: [dim] });
      expect(comp.showDimensionMore(0)).toBe(true);
   });

   it("showDimensionMore() should return false when next date dim has same name", async () => {
      const dims = [
         makeDim("SaleDate", { dataType: "timeInstant" }),
         makeDim("SaleDate", { dataType: "timeInstant" }),
      ];
      const { comp } = await renderComponent({ dimensions: dims });
      expect(comp.showDimensionMore(0)).toBe(false);
   });

   it("showAggregateMore() should return true for last measure", async () => {
      const { comp } = await renderComponent({ measures: [makeAgg("Sales")] });
      expect(comp.showAggregateMore(0)).toBe(true);
   });

   it("showAggregateMore() should return false when next measure has same name", async () => {
      const { comp } = await renderComponent({ measures: [makeAgg("Sales"), makeAgg("Sales")] });
      expect(comp.showAggregateMore(0)).toBe(false);
   });

   it("showAggregateMore() should return true when next measure has different name", async () => {
      const { comp } = await renderComponent({ measures: [makeAgg("Sales"), makeAgg("Revenue")] });
      expect(comp.showAggregateMore(0)).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 9 — moveUpDim() / moveDownDim() [Risk 3]
// ---------------------------------------------------------------------------

describe("VSWizardAggregatePane — moveUpDim() / moveDownDim()", () => {
   // 🔁 Regression-sensitive: the array-splice block-move algorithm is non-trivial.
   // An off-by-one error produces an out-of-order or duplicated dimensions list,
   // silently generating a wrong chart grouping on the next render.
   it("moveUpDim(1) should swap two non-date dimensions: [A, B] → [B, A]", async () => {
      const dimA = makeDim("Region");
      const dimB = makeDim("Year");
      const { comp } = await renderComponent({ dimensions: [dimA, dimB] });

      comp.moveUpDim(1);

      expect(comp.dimensions.map(d => d.name)).toEqual(["Year", "Region"]);
   });

   it("moveDownDim(0) should swap two non-date dimensions: [A, B] → [B, A]", async () => {
      const dimA = makeDim("Region");
      const dimB = makeDim("Year");
      const { comp } = await renderComponent({ dimensions: [dimA, dimB] });

      comp.moveDownDim(0);

      expect(comp.dimensions.map(d => d.name)).toEqual(["Year", "Region"]);
   });

   it("moveUpDim should clear original on all dims after reorder", async () => {
      const dimA = makeDim("Region", { original: { index: 0 } });
      const dimB = makeDim("Year", { original: { index: 1 } });
      const { comp } = await renderComponent({ dimensions: [dimA, dimB] });

      comp.moveUpDim(1);

      expect(comp.dimensions.every(d => d.original === null)).toBe(true);
   });

   it("moveUpDim should emit onAddDimension when autoOrder=false", async () => {
      const dims = [makeDim("A"), makeDim("B")];
      const { comp } = await renderComponent({ dimensions: dims, autoOrder: false });
      const emitted: null[] = [];
      comp.onAddDimension.subscribe(() => emitted.push(null));

      comp.moveUpDim(1);

      expect(emitted).toHaveLength(1);
   });

   it("moveUpDim should call setAutoOrder(false) when autoOrder=true, not emit onAddDimension", async () => {
      const dims = [makeDim("A"), makeDim("B")];
      const { comp } = await renderComponent({ dimensions: dims, autoOrder: true });
      const dimEmitted: null[] = [];
      const autoOrderEmitted: boolean[] = [];
      comp.onAddDimension.subscribe(() => dimEmitted.push(null));
      comp.onAutoOrderChange.subscribe(v => autoOrderEmitted.push(v));

      comp.moveUpDim(1);

      expect(dimEmitted).toHaveLength(0);
      expect(autoOrderEmitted).toEqual([false]);
   });
});

// ---------------------------------------------------------------------------
// Group 10 — moveUpMeasure() / moveDownMeasure() [Risk 2]
// ---------------------------------------------------------------------------

describe("VSWizardAggregatePane — moveUpMeasure() / moveDownMeasure()", () => {
   it("moveUpMeasure(1) should swap two measures: [A, B] → [B, A]", async () => {
      const aggA = makeAgg("Sales");
      const aggB = makeAgg("Revenue");
      const { comp } = await renderComponent({ measures: [aggA, aggB] });

      comp.moveUpMeasure(1);

      expect(comp.measures.map(m => m.name)).toEqual(["Revenue", "Sales"]);
   });

   it("moveDownMeasure(0) should swap two measures: [A, B] → [B, A]", async () => {
      const aggA = makeAgg("Sales");
      const aggB = makeAgg("Revenue");
      const { comp } = await renderComponent({ measures: [aggA, aggB] });

      comp.moveDownMeasure(0);

      expect(comp.measures.map(m => m.name)).toEqual(["Revenue", "Sales"]);
   });

   it("moveUpMeasure should call setAutoOrder(false) when autoOrder=true", async () => {
      const measures = [makeAgg("A"), makeAgg("B")];
      const { comp } = await renderComponent({ measures, autoOrder: true });
      const emitted: boolean[] = [];
      comp.onAutoOrderChange.subscribe(v => emitted.push(v));

      comp.moveUpMeasure(1);

      expect(emitted).toEqual([false]);
   });
});

// ---------------------------------------------------------------------------
// Group 11 — moveUpDetail() / moveDownDetail() [baseline]
// ---------------------------------------------------------------------------

describe("VSWizardAggregatePane — moveUpDetail() / moveDownDetail()", () => {
   // 🔁 Regression-sensitive: the event must carry the source index as `transfer` and the
   // target index as `dropTarget`. Swapping them silently moves the column in the wrong
   // direction and the user sees columns jump unpredictably.
   it("moveUpDetail(2) should send UPDATE_COLUMNS with transfer=2, dropTarget=1", async () => {
      const { comp } = await renderComponent();

      comp.moveUpDetail(2);

      expect(SEND_EVENT_MOCK).toHaveBeenCalledWith(
         "/events/vswizard/binding/update-columns",
         expect.objectContaining({ transfer: 2, dropTarget: 1 }),
      );
   });

   it("moveDownDetail(1) should send UPDATE_COLUMNS with transfer=1, dropTarget=2", async () => {
      const { comp } = await renderComponent();

      comp.moveDownDetail(1);

      expect(SEND_EVENT_MOCK).toHaveBeenCalledWith(
         "/events/vswizard/binding/update-columns",
         expect.objectContaining({ transfer: 1, dropTarget: 2 }),
      );
   });
});

// ---------------------------------------------------------------------------
// Group 12 — isMoveDownDimEnabled() / isMoveDownMeasureEnabled() [baseline]
// ---------------------------------------------------------------------------

describe("VSWizardAggregatePane — isMoveDownDimEnabled() / isMoveDownMeasureEnabled()", () => {
   it("isMoveDownDimEnabled() should return true for non-last dim", async () => {
      const { comp } = await renderComponent({ dimensions: [makeDim("A"), makeDim("B")] });
      expect(comp.isMoveDownDimEnabled(0)).toBe(true);
   });

   it("isMoveDownDimEnabled() should return false for last dim", async () => {
      const { comp } = await renderComponent({ dimensions: [makeDim("A"), makeDim("B")] });
      expect(comp.isMoveDownDimEnabled(1)).toBe(false);
   });

   it("isMoveDownMeasureEnabled() should return true for non-last measure", async () => {
      const { comp } = await renderComponent({ measures: [makeAgg("A"), makeAgg("B")] });
      expect(comp.isMoveDownMeasureEnabled(0)).toBe(true);
   });

   it("isMoveDownMeasureEnabled() should return false for last measure", async () => {
      const { comp } = await renderComponent({ measures: [makeAgg("A"), makeAgg("B")] });
      expect(comp.isMoveDownMeasureEnabled(1)).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 13 — hasItem() [baseline]
// ---------------------------------------------------------------------------

describe("VSWizardAggregatePane — hasItem()", () => {
   it("should return false when all arrays are empty", async () => {
      const { comp } = await renderComponent({ dimensions: [], measures: [], details: [] });
      expect(comp.hasItem()).toBe(false);
   });

   it("should return true when dimensions is non-empty", async () => {
      const { comp } = await renderComponent({ dimensions: [makeDim("X")] });
      expect(comp.hasItem()).toBe(true);
   });

   it("should return true when measures is non-empty", async () => {
      const { comp } = await renderComponent({ measures: [makeAgg("Y")] });
      expect(comp.hasItem()).toBe(true);
   });

   it("should return true when details is non-empty", async () => {
      const { comp } = await renderComponent({ details: [makeDetail("Z")] });
      expect(comp.hasItem()).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 14 — setAutoOrder() [baseline]
// ---------------------------------------------------------------------------

describe("VSWizardAggregatePane — setAutoOrder()", () => {
   it("should set the autoOrder property", async () => {
      const { comp } = await renderComponent({ autoOrder: false });

      comp.setAutoOrder(true);

      expect(comp.autoOrder).toBe(true);
   });

   it("should emit onAutoOrderChange with the new value", async () => {
      const { comp } = await renderComponent();
      const emitted: boolean[] = [];
      comp.onAutoOrderChange.subscribe(v => emitted.push(v));

      comp.setAutoOrder(true);

      expect(emitted).toEqual([true]);
   });
});

// ---------------------------------------------------------------------------
// Group 15 — getFormat() [Risk 2]
// ---------------------------------------------------------------------------

describe("VSWizardAggregatePane — getFormat()", () => {
   // 🔁 Regression-sensitive: using the wrong key (name vs fullName) causes the format pane
   // to show the wrong color/font settings — or nothing — for aggregate measures.
   it("should return null when formatMap is absent", async () => {
      const { comp } = await renderComponent({ formatMap: null });
      const dim = makeDim("Region");

      expect(comp.getFormat(dim)).toBeNull();
   });

   it("should look up by ref.name when isDetail=true", async () => {
      const format = { type: "currency" } as any;
      const formatMap = new Map([["Region", format]]);
      const { comp } = await renderComponent({ formatMap, isDetail: true });
      const dim = makeDim("Region");

      expect(comp.getFormat(dim)).toBe(format);
   });

   it("should look up by ref.fullName when isDetail=false", async () => {
      const format = { type: "percent" } as any;
      const agg = makeAgg("Sales");  // fullName = "Sum(Sales)"
      const formatMap = new Map([["Sum(Sales)", format]]);
      const { comp } = await renderComponent({ formatMap, isDetail: false });

      expect(comp.getFormat(agg)).toBe(format);
   });
});

// ---------------------------------------------------------------------------
// Group 16 — getGrayedOutValues() [baseline]
// ---------------------------------------------------------------------------

describe("VSWizardAggregatePane — getGrayedOutValues()", () => {
   it("should return empty array when grayedOutFields is empty", async () => {
      const { comp } = await renderComponent({ grayedOutFields: [] });
      expect(comp.getGrayedOutValues()).toEqual([]);
   });

   it("should return name of each grayed-out field", async () => {
      const grayedOutFields = [makeDetail("Field1"), makeDetail("Field2")];
      const { comp } = await renderComponent({ grayedOutFields });
      expect(comp.getGrayedOutValues()).toEqual(["Field1", "Field2"]);
   });
});

// ---------------------------------------------------------------------------
// Group 17 — updateAggregateFormat() [Risk 2]
// ---------------------------------------------------------------------------

describe("VSWizardAggregatePane — updateAggregateFormat()", () => {
   // 🔁 Regression-sensitive: the event must carry the computed field name key (which
   // includes the formula prefix for aggregate fields), not just the column name. A wrong
   // key causes the server to ignore the format update or apply it to the wrong column.
   it("should be a no-op when formatMap is absent", async () => {
      const { comp } = await renderComponent({ measures: [makeAgg("Sales")], formatMap: null });

      comp.updateAggregateFormat(0);

      expect(SEND_EVENT_MOCK).not.toHaveBeenCalled();
   });

   it("should be a no-op when formatMap has no entry for the computed key", async () => {
      const { comp } = await renderComponent({
         measures: [makeAgg("Sales")],
         formatMap: new Map([["Other(Sales)", { type: "currency" } as any]]),
         fixedFormulaMap: [],
      });

      comp.updateAggregateFormat(0);

      expect(SEND_EVENT_MOCK).not.toHaveBeenCalled();
   });

   it("should send UPDATE_WIZARD_BINDING_FORMAT with the field name and format when key matches", async () => {
      const format = { type: "currency" } as any;
      const agg = makeAgg("Sales");  // fullName = "Sum(Sales)"
      const formatMap = new Map([["Sum(Sales)", format]]);
      const { comp } = await renderComponent({
         measures: [agg],
         formatMap,
         fixedFormulaMap: [],
      });

      comp.updateAggregateFormat(0);

      expect(SEND_EVENT_MOCK).toHaveBeenCalledWith(
         "/events/vswizard/object/format",
         expect.objectContaining({ field: "Sum(Sales)", model: format }),
      );
   });
});
