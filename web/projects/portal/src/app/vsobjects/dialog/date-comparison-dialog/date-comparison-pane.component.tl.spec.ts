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
 * DateComparisonPaneComponent — single pass
 *
 * Risk-first coverage:
 *   Group 1  [Risk 1] — isChart / showAsFacet: assemblyType dispatch + trivial passthrough
 *   Group 2  [Risk 2] — isValidDateComparison: AND of two ViewChild results
 *   Group 3  [Risk 1] — editColorDisable: AND of disable + shareAssemblyType
 *   Group 4  [Risk 2] — openChanged: oframes snapshot vs. dropdown-close side effect
 *   Group 5  [Risk 2] — getPeriodEndDay: custom vs. standard period dispatch + empty/missing guards
 *   Group 6  [Risk 2] — isWeekly: 4-way OR across granularity/level/contextLevel
 *   Group 7  [Risk 3] — isOnlyShowRecentDateVisible: nested dispatch, showPartLevelAsDate delegation
 *   Group 8  [Risk 2] — dcIntervalLevelToDateGroupLevel (private, via cast): bit-mask dispatch
 *
 * Fixed bugs (previously it.fails, now passing):
 *   Bug #75654 — isOnlyShowRecentDateVisible (Group 7): dcIntervalLevelToDateGroupLevel()
 *   is a bit-mask decoder written for the IntervalLevel domain (level/granularity store
 *   bit-flags: YEAR=16, QUARTER=8, MONTH=4, WEEK=2, DAY=1), but isOnlyShowRecentDateVisible()
 *   used to also call it on contextLevel, which stores direct XConstants group values
 *   (YEAR_DATE_GROUP=5, QUARTER_DATE_GROUP=4, MONTH_DATE_GROUP=3, WEEK_DATE_GROUP=2) — a
 *   different domain. Because 3 of those 4 values numerically overlapped with unrelated
 *   IntervalLevel bits (5=MONTH|DAY, 4=MONTH, 3=WEEK|DAY), the decoded "contextLevel" was
 *   silently wrong whenever the user's context-level selection was Year, Quarter, or Month
 *   (only Week, value 2, coincidentally decoded correctly), flipping the "Only Show Most Recent
 *   Date" checkbox's visibility to the wrong value instead of delegating to
 *   showPartLevelAsDate() as intended. Fixed by decoding contextLevel with a dedicated
 *   `contextLevelToDateGroupLevel()` that just parses the raw XConstants value directly,
 *   instead of running it through the IntervalLevel bit-mask decoder.
 *
 * Out of scope: NgbNav tab switching (`(navChange)` inline template binding that flips
 * dateComparisonPaneModel.periodPaneModel.custom) — library-level, tested via ngb's own tests,
 * consistent with edit-custom-patterns-dialog.component.tl.spec.ts. showPartLevelAsDate is
 * private with its only caller being isOnlyShowRecentDateVisible(); it is exercised via that
 * method's Group 7 delegation cases rather than a separate describe block.
 */

import { Component, Input, QueryList } from "@angular/core";
import { render } from "@testing-library/angular";

import { DateComparisonPaneComponent } from "./date-comparison-pane.component";
import { DateComparisonPeriodsPaneComponent } from "./date-comparison-periods-pane.component";
import { DateComparisonIntervalPaneComponent } from "./date-comparison-interval-pane.component";
import { FixedDropdownDirective } from "../../../widget/fixed-dropdown/fixed-dropdown.directive";
import { AssemblyType } from "../../../composer/gui/vs/assembly-type";
import { IntervalLevel, IntervalPaneModel } from "../../model/interval-pane-model";
import { DynamicValueModel, ValueTypes } from "../../model/dynamic-value-model";
import { XConstants } from "../../../common/util/xconstants";
import { ComparisonOption, DateComparisonPaneModel } from "../../model/date-comparison-pane-model";
import { PeriodPaneModel } from "../../model/period-pane-model";
import { StandardPeriodPaneModel } from "../../model/standard-period-pane-model";
import { CustomPeriodPaneModel } from "../../model/custom-period-pane-model";
import { DatePeriodModel } from "../../model/date-period-model";
import { VisualFrameModel } from "../../../common/data/visual-frame-model";

afterEach(() => {
   vi.restoreAllMocks();
});

// ---------------------------------------------------------------------------
// Child component stubs
// ---------------------------------------------------------------------------
// DateComparisonIntervalPaneComponent injects FirstDayOfWeekService (HttpClient-backed);
// stubbing both real children avoids pulling in their dependency trees entirely, since this
// spec is scoped to DateComparisonPaneComponent's own methods only.

@Component({ selector: "date-comparison-periods-pane", standalone: true, template: "" })
class DateComparisonPeriodsPaneComponentStub {
   @Input() periodPaneModel: PeriodPaneModel;
   @Input() variableValues: string[] = [];
   @Input() disable = false;
   @Input() columnTreeRoot: unknown = null;
   @Input() functionTreeRoot: unknown = null;
   @Input() operatorTreeRoot: unknown = null;
   @Input() scriptDefinitions: unknown = null;
   @Input() toDateDisabled = false;
   @Input() intervalEndDate: unknown = null;
   @Input() weekly = false;
}

@Component({ selector: "date-comparison-interval-pane", standalone: true, template: "" })
class DateComparisonIntervalPaneComponentStub {
   @Input() intervalPaneModel: unknown;
   @Input() disable = false;
   @Input() variableValues: string[] = [];
   @Input() standardPeriodLevel: unknown;
   @Input() periodEndDay: unknown;
   @Input() isCustomPeriod = false;
   @Input() columnTreeRoot: unknown = null;
   @Input() functionTreeRoot: unknown = null;
   @Input() operatorTreeRoot: unknown = null;
   @Input() scriptDefinitions: unknown = null;
}

// ---------------------------------------------------------------------------
// Model fixtures
// ---------------------------------------------------------------------------

function makeDynamicValue(value: any, type: string = ValueTypes.VALUE): DynamicValueModel {
   return { type, value };
}

function makeStandardPeriodPaneModel(overrides: Partial<StandardPeriodPaneModel> = {}): StandardPeriodPaneModel {
   return {
      preCount: makeDynamicValue("1"),
      dateLevel: makeDynamicValue(XConstants.YEAR_DATE_GROUP + ""),
      toDate: false,
      endDay: makeDynamicValue("2024-01-15"),
      toDayAsEndDay: false,
      inclusive: false,
      ...overrides,
   };
}

function makeCustomPeriodPaneModel(overrides: Partial<CustomPeriodPaneModel> = {}): CustomPeriodPaneModel {
   return {
      datePeriods: [],
      ...overrides,
   };
}

function makePeriodPaneModel(overrides: Partial<PeriodPaneModel> = {}): PeriodPaneModel {
   return {
      customPeriodPaneModel: makeCustomPeriodPaneModel(),
      standardPeriodPaneModel: makeStandardPeriodPaneModel(),
      custom: false,
      ...overrides,
   };
}

function makeIntervalPaneModel(overrides: Partial<IntervalPaneModel> = {}): IntervalPaneModel {
   return {
      level: makeDynamicValue(IntervalLevel.YEAR_TO_DATE + ""),
      granularity: makeDynamicValue(IntervalLevel.MONTH + ""),
      endDayAsToDate: false,
      intervalEndDate: makeDynamicValue("2024-01-15"),
      inclusive: false,
      contextLevel: makeDynamicValue(XConstants.YEAR_DATE_GROUP),
      ...overrides,
   };
}

function makeDatePeriodModel(overrides: Partial<DatePeriodModel> = {}): DatePeriodModel {
   return {
      start: makeDynamicValue("2024-01-01"),
      end: makeDynamicValue("2024-01-31"),
      ...overrides,
   };
}

function makeDateComparisonPaneModel(overrides: Partial<DateComparisonPaneModel> = {}): DateComparisonPaneModel {
   return {
      periodPaneModel: makePeriodPaneModel(),
      intervalPaneModel: makeIntervalPaneModel(),
      comparisonOption: ComparisonOption.VALUE,
      useFacet: false,
      onlyShowMostRecentDate: false,
      visualFrameModel: Object.assign(new VisualFrameModel(), { name: "Frame1", field: "Sales" }),
      ...overrides,
   };
}

// ---------------------------------------------------------------------------
// renderComponent
// ---------------------------------------------------------------------------

async function renderComponent(opts: {
   dateComparisonPaneModel?: DateComparisonPaneModel;
   assemblyType?: string;
   disable?: boolean;
   shareAssemblyType?: number;
} = {}) {
   const { fixture } = await render(DateComparisonPaneComponent, {
      componentInputs: {
         dateComparisonPaneModel: opts.dateComparisonPaneModel ?? makeDateComparisonPaneModel(),
         assemblyType: (opts.assemblyType ?? "VSChart") as any,
         disable: opts.disable ?? false,
         shareAssemblyType: opts.shareAssemblyType,
      },
      importOverrides: [
         { replace: DateComparisonPeriodsPaneComponent, with: DateComparisonPeriodsPaneComponentStub },
         { replace: DateComparisonIntervalPaneComponent, with: DateComparisonIntervalPaneComponentStub },
      ],
   });

   return { fixture, comp: fixture.componentInstance as DateComparisonPaneComponent };
}

// ---------------------------------------------------------------------------
// Group 1: isChart / showAsFacet
// ---------------------------------------------------------------------------

describe("DateComparisonPaneComponent — isChart / showAsFacet", () => {
   it("isChart should return true when assemblyType is VSChart", async () => {
      const { comp } = await renderComponent({ assemblyType: "VSChart" });

      expect(comp.isChart()).toBe(true);
   });

   it("isChart should return false when assemblyType is not VSChart", async () => {
      const { comp } = await renderComponent({ assemblyType: "VSTable" });

      expect(comp.isChart()).toBe(false);
   });

   // showAsFacet has no logic beyond `return this.isChart()` — one test proves the
   // passthrough; both directions of isChart() itself are covered above.
   it("showAsFacet should delegate directly to isChart()", async () => {
      const { comp } = await renderComponent({ assemblyType: "VSChart" });

      expect(comp.showAsFacet()).toBe(comp.isChart());
      expect(comp.showAsFacet()).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 2: isValidDateComparison
// ---------------------------------------------------------------------------

describe("DateComparisonPaneComponent — isValidDateComparison", () => {
   // Bypass: periodPane/intervalPane are @ViewChild-resolved instances of the REAL child
   // component classes; since the children are stubbed by class reference (importOverrides),
   // Angular's @ViewChild query never matches the rendered stub instances. Assigning plain
   // mock objects directly isolates the getter's own AND logic from child-rendering concerns.
   it("should be true when both periodPane.isValidPeriod and intervalPane.isValidInterval() are true", async () => {
      const { comp } = await renderComponent();
      (comp as any).periodPane = { isValidPeriod: true };
      (comp as any).intervalPane = { isValidInterval: () => true };

      expect(comp.isValidDateComparison).toBe(true);
   });

   it("should be false when periodPane.isValidPeriod is false, even if intervalPane is valid", async () => {
      const { comp } = await renderComponent();
      (comp as any).periodPane = { isValidPeriod: false };
      (comp as any).intervalPane = { isValidInterval: () => true };

      expect(comp.isValidDateComparison).toBe(false);
   });

   it("should be false when intervalPane.isValidInterval() is false, even if periodPane is valid", async () => {
      const { comp } = await renderComponent();
      (comp as any).periodPane = { isValidPeriod: true };
      (comp as any).intervalPane = { isValidInterval: () => false };

      expect(comp.isValidDateComparison).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 3: editColorDisable
// ---------------------------------------------------------------------------

describe("DateComparisonPaneComponent — editColorDisable", () => {
   it("should be true when disable is true and shareAssemblyType is CHART_ASSET", async () => {
      const { comp } = await renderComponent({ disable: true, shareAssemblyType: AssemblyType.CHART_ASSET });

      expect(comp.editColorDisable()).toBe(true);
   });

   it("should be false when disable is true but shareAssemblyType is not CHART_ASSET", async () => {
      const { comp } = await renderComponent({ disable: true, shareAssemblyType: AssemblyType.TABLE_VIEW_ASSET });

      expect(comp.editColorDisable()).toBe(false);
   });

   it("should be false when shareAssemblyType is CHART_ASSET but disable is false", async () => {
      const { comp } = await renderComponent({ disable: false, shareAssemblyType: AssemblyType.CHART_ASSET });

      expect(comp.editColorDisable()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 4: openChanged
// ---------------------------------------------------------------------------

describe("DateComparisonPaneComponent — openChanged", () => {
   it("should snapshot visualFrameModel to oframes as JSON when opening", async () => {
      const model = makeDateComparisonPaneModel({
         visualFrameModel: Object.assign(new VisualFrameModel(), { name: "Frame2", field: "Profit" }),
      });
      const { comp } = await renderComponent({ dateComparisonPaneModel: model });

      comp.openChanged(true);

      // Bypass: oframes is a `protected` field with no public getter; bracket notation reads it
      // without a public accessor since this test targets openChanged()'s side effect only.
      expect(comp["oframes"]).toBe(JSON.stringify(model.visualFrameModel));
   });

   it("should close every registered dropdown when closing", async () => {
      const { comp } = await renderComponent();
      const dropdown1 = { close: vi.fn() } as unknown as FixedDropdownDirective;
      const dropdown2 = { close: vi.fn() } as unknown as FixedDropdownDirective;
      comp.dropdowns = new QueryList<FixedDropdownDirective>();
      comp.dropdowns.reset([dropdown1, dropdown2]);

      comp.openChanged(false);

      expect(dropdown1.close).toHaveBeenCalledTimes(1);
      expect(dropdown2.close).toHaveBeenCalledTimes(1);
   });
});

// ---------------------------------------------------------------------------
// Group 5: getPeriodEndDay
// ---------------------------------------------------------------------------

describe("DateComparisonPaneComponent — getPeriodEndDay", () => {
   it("should return the first custom period's end date when custom periods are present", async () => {
      const endDate = makeDynamicValue("2024-06-15");
      const model = makeDateComparisonPaneModel({
         periodPaneModel: makePeriodPaneModel({
            custom: true,
            customPeriodPaneModel: makeCustomPeriodPaneModel({
               datePeriods: [makeDatePeriodModel({ end: endDate }), makeDatePeriodModel()],
            }),
         }),
      });
      const { comp } = await renderComponent({ dateComparisonPaneModel: model });

      expect(comp.getPeriodEndDay()).toBe(endDate);
   });

   it("should return null when custom is true but datePeriods is empty", async () => {
      const model = makeDateComparisonPaneModel({
         periodPaneModel: makePeriodPaneModel({
            custom: true,
            customPeriodPaneModel: makeCustomPeriodPaneModel({ datePeriods: [] }),
         }),
      });
      const { comp } = await renderComponent({ dateComparisonPaneModel: model });

      expect(comp.getPeriodEndDay()).toBeNull();
   });

   it("should return null when custom is true but datePeriods is missing", async () => {
      const model = makeDateComparisonPaneModel({
         periodPaneModel: makePeriodPaneModel({
            custom: true,
            customPeriodPaneModel: makeCustomPeriodPaneModel({ datePeriods: undefined as any }),
         }),
      });
      const { comp } = await renderComponent({ dateComparisonPaneModel: model });

      expect(comp.getPeriodEndDay()).toBeNull();
   });

   it("should return null when custom is true and the first datePeriod entry is falsy", async () => {
      const model = makeDateComparisonPaneModel({
         periodPaneModel: makePeriodPaneModel({
            custom: true,
            customPeriodPaneModel: makeCustomPeriodPaneModel({ datePeriods: [null as any] }),
         }),
      });
      const { comp } = await renderComponent({ dateComparisonPaneModel: model });

      expect(comp.getPeriodEndDay()).toBeNull();
   });

   it("should return the standard period's endDay when custom is false", async () => {
      const endDay = makeDynamicValue("2024-12-31");
      const model = makeDateComparisonPaneModel({
         periodPaneModel: makePeriodPaneModel({
            custom: false,
            standardPeriodPaneModel: makeStandardPeriodPaneModel({ endDay }),
         }),
      });
      const { comp } = await renderComponent({ dateComparisonPaneModel: model });

      expect(comp.getPeriodEndDay()).toBe(endDay);
   });
});

// ---------------------------------------------------------------------------
// Group 6: isWeekly
// ---------------------------------------------------------------------------

describe("DateComparisonPaneComponent — isWeekly", () => {
   it("should be true when granularity is WEEK", async () => {
      const model = makeDateComparisonPaneModel({
         intervalPaneModel: makeIntervalPaneModel({ granularity: makeDynamicValue(IntervalLevel.WEEK + "") }),
      });
      const { comp } = await renderComponent({ dateComparisonPaneModel: model });

      expect(comp.isWeekly()).toBe(true);
   });

   it("should be true when level is SAME_WEEK", async () => {
      const model = makeDateComparisonPaneModel({
         intervalPaneModel: makeIntervalPaneModel({ level: makeDynamicValue(IntervalLevel.SAME_WEEK + "") }),
      });
      const { comp } = await renderComponent({ dateComparisonPaneModel: model });

      expect(comp.isWeekly()).toBe(true);
   });

   it("should be true when level is WEEK_TO_DATE", async () => {
      const model = makeDateComparisonPaneModel({
         intervalPaneModel: makeIntervalPaneModel({ level: makeDynamicValue(IntervalLevel.WEEK_TO_DATE + "") }),
      });
      const { comp } = await renderComponent({ dateComparisonPaneModel: model });

      expect(comp.isWeekly()).toBe(true);
   });

   it("should be true when contextLevel is WEEK_DATE_GROUP", async () => {
      const model = makeDateComparisonPaneModel({
         intervalPaneModel: makeIntervalPaneModel({ contextLevel: makeDynamicValue(XConstants.WEEK_DATE_GROUP) }),
      });
      const { comp } = await renderComponent({ dateComparisonPaneModel: model });

      expect(comp.isWeekly()).toBe(true);
   });

   it("should be false when none of granularity, level, or contextLevel indicate week", async () => {
      const model = makeDateComparisonPaneModel({
         intervalPaneModel: makeIntervalPaneModel({
            granularity: makeDynamicValue(IntervalLevel.MONTH + ""),
            level: makeDynamicValue(IntervalLevel.YEAR_TO_DATE + ""),
            contextLevel: makeDynamicValue(XConstants.YEAR_DATE_GROUP),
         }),
      });
      const { comp } = await renderComponent({ dateComparisonPaneModel: model });

      expect(comp.isWeekly()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 7: isOnlyShowRecentDateVisible
// ---------------------------------------------------------------------------

describe("DateComparisonPaneComponent — isOnlyShowRecentDateVisible", () => {
   it("should be false when the assembly is not a chart, regardless of the model", async () => {
      const { comp } = await renderComponent({ assemblyType: "VSTable" });

      expect(comp.isOnlyShowRecentDateVisible()).toBe(false);
   });

   // Bypass: dateComparisonPaneModel is mutated to null AFTER render (not passed as an initial
   // componentInput) because the template unconditionally dereferences
   // dateComparisonPaneModel.periodPaneModel.custom in the <ul ngbNav> [activeId] binding,
   // which would crash change detection during the initial render.
   it("should be false when dateComparisonPaneModel is falsy", async () => {
      const { comp } = await renderComponent({ assemblyType: "VSChart" });
      (comp as any).dateComparisonPaneModel = null;

      expect(comp.isOnlyShowRecentDateVisible()).toBe(false);
   });

   it("should be true whenever the period is custom, regardless of interval/context/granularity values", async () => {
      const model = makeDateComparisonPaneModel({
         periodPaneModel: makePeriodPaneModel({ custom: true }),
         intervalPaneModel: makeIntervalPaneModel({
            level: makeDynamicValue(IntervalLevel.SAME_DAY + ""),
            contextLevel: makeDynamicValue(XConstants.DAY_DATE_GROUP),
            granularity: makeDynamicValue(IntervalLevel.DAY + ""),
         }),
      });
      const { comp } = await renderComponent({ assemblyType: "VSChart", dateComparisonPaneModel: model });

      expect(comp.isOnlyShowRecentDateVisible()).toBe(true);
   });

   // contextLevel=WEEK_DATE_GROUP(2) — used here to reach the "all equal" branch as intended,
   // isolating the showPartLevelAsDate() delegation.
   it("should delegate to showPartLevelAsDate()=true when context/interval/granularity groups all match", async () => {
      const model = makeDateComparisonPaneModel({
         periodPaneModel: makePeriodPaneModel({
            custom: false,
            standardPeriodPaneModel: makeStandardPeriodPaneModel({ dateLevel: makeDynamicValue(XConstants.QUARTER_DATE_GROUP + "") }),
         }),
         intervalPaneModel: makeIntervalPaneModel({
            level: makeDynamicValue(IntervalLevel.WEEK_TO_DATE + ""), // -> WEEK_DATE_GROUP(2)
            contextLevel: makeDynamicValue(XConstants.WEEK_DATE_GROUP), // 2 -> WEEK_DATE_GROUP(2)
            granularity: makeDynamicValue(IntervalLevel.WEEK + ""), // -> WEEK_DATE_GROUP(2)
         }),
      });
      const { comp } = await renderComponent({ assemblyType: "VSChart", dateComparisonPaneModel: model });

      expect(comp.isOnlyShowRecentDateVisible()).toBe(true);
   });

   it("should delegate to showPartLevelAsDate()=false when context/interval/granularity groups all match", async () => {
      const model = makeDateComparisonPaneModel({
         periodPaneModel: makePeriodPaneModel({
            custom: false,
            standardPeriodPaneModel: makeStandardPeriodPaneModel({ dateLevel: makeDynamicValue(XConstants.MONTH_DATE_GROUP + "") }),
         }),
         intervalPaneModel: makeIntervalPaneModel({
            level: makeDynamicValue(IntervalLevel.WEEK_TO_DATE + ""), // -> WEEK_DATE_GROUP(2)
            contextLevel: makeDynamicValue(XConstants.WEEK_DATE_GROUP), // -> WEEK_DATE_GROUP(2)
            granularity: makeDynamicValue(IntervalLevel.WEEK + ""), // -> WEEK_DATE_GROUP(2)
         }),
      });
      const { comp } = await renderComponent({ assemblyType: "VSChart", dateComparisonPaneModel: model });

      // showPartLevelAsDate() returns false immediately: dateLevel=MONTH_DATE_GROUP is neither
      // YEAR nor QUARTER.
      expect(comp.isOnlyShowRecentDateVisible()).toBe(false);
   });

   it("should be true when periodVal differs from contextLevel (first OR operand)", async () => {
      const model = makeDateComparisonPaneModel({
         periodPaneModel: makePeriodPaneModel({
            custom: false,
            standardPeriodPaneModel: makeStandardPeriodPaneModel({ dateLevel: makeDynamicValue(XConstants.YEAR_DATE_GROUP + "") }),
         }),
         intervalPaneModel: makeIntervalPaneModel({
            level: makeDynamicValue(IntervalLevel.WEEK_TO_DATE + ""), // -> WEEK_DATE_GROUP(2)
            contextLevel: makeDynamicValue(XConstants.WEEK_DATE_GROUP), // -> WEEK_DATE_GROUP(2), == intervalLevel
            granularity: makeDynamicValue(IntervalLevel.MONTH + ""), // -> MONTH_DATE_GROUP(3), != contextLevel (avoids "all equal" branch)
         }),
      });
      const { comp } = await renderComponent({ assemblyType: "VSChart", dateComparisonPaneModel: model });

      expect(comp.isOnlyShowRecentDateVisible()).toBe(true);
   });

   it("should be true when contextLevel differs from intervalLevel (second OR operand)", async () => {
      const model = makeDateComparisonPaneModel({
         periodPaneModel: makePeriodPaneModel({
            custom: false,
            standardPeriodPaneModel: makeStandardPeriodPaneModel({ dateLevel: makeDynamicValue(XConstants.WEEK_DATE_GROUP + "") }),
         }),
         intervalPaneModel: makeIntervalPaneModel({
            level: makeDynamicValue(IntervalLevel.YEAR_TO_DATE + ""), // -> YEAR_DATE_GROUP(5)
            contextLevel: makeDynamicValue(XConstants.WEEK_DATE_GROUP), // -> WEEK_DATE_GROUP(2), == periodVal, != intervalLevel
            granularity: makeDynamicValue(IntervalLevel.MONTH + ""), // -> MONTH_DATE_GROUP(3), != contextLevel (avoids "all equal" branch)
         }),
      });
      const { comp } = await renderComponent({ assemblyType: "VSChart", dateComparisonPaneModel: model });

      expect(comp.isOnlyShowRecentDateVisible()).toBe(true);
   });

   it("should delegate to showPartLevelAsDate()=true when both OR operands are false but groups aren't all three equal", async () => {
      const model = makeDateComparisonPaneModel({
         periodPaneModel: makePeriodPaneModel({
            custom: false,
            standardPeriodPaneModel: makeStandardPeriodPaneModel({ dateLevel: makeDynamicValue(XConstants.QUARTER_DATE_GROUP + "") }),
         }),
         intervalPaneModel: makeIntervalPaneModel({
            level: makeDynamicValue(IntervalLevel.QUARTER_TO_DATE + ""), // -> QUARTER_DATE_GROUP(4), == periodVal & contextLevel
            contextLevel: makeDynamicValue(XConstants.QUARTER_DATE_GROUP), // -> QUARTER_DATE_GROUP(4)
            granularity: makeDynamicValue(IntervalLevel.DAY + ""), // -> DAY_DATE_GROUP(1), != contextLevel (avoids "all equal" branch)
         }),
      });
      const { comp } = await renderComponent({ assemblyType: "VSChart", dateComparisonPaneModel: model });

      // showPartLevelAsDate() returns true: dateLevel=QUARTER and granularity=DAY.
      expect(comp.isOnlyShowRecentDateVisible()).toBe(true);
   });

   it("should delegate to showPartLevelAsDate()=false when both OR operands are false but groups aren't all three equal", async () => {
      const model = makeDateComparisonPaneModel({
         periodPaneModel: makePeriodPaneModel({
            custom: false,
            standardPeriodPaneModel: makeStandardPeriodPaneModel({ dateLevel: makeDynamicValue(XConstants.WEEK_DATE_GROUP + "") }),
         }),
         intervalPaneModel: makeIntervalPaneModel({
            level: makeDynamicValue(IntervalLevel.WEEK_TO_DATE + ""), // -> WEEK_DATE_GROUP(2), == periodVal & contextLevel
            contextLevel: makeDynamicValue(XConstants.WEEK_DATE_GROUP), // -> WEEK_DATE_GROUP(2)
            granularity: makeDynamicValue(IntervalLevel.MONTH + ""), // -> MONTH_DATE_GROUP(3), != contextLevel (avoids "all equal" branch)
         }),
      });
      const { comp } = await renderComponent({ assemblyType: "VSChart", dateComparisonPaneModel: model });

      // showPartLevelAsDate() returns false immediately: dateLevel=WEEK_DATE_GROUP is neither
      // YEAR nor QUARTER.
      expect(comp.isOnlyShowRecentDateVisible()).toBe(false);
   });

   // Bug #75654 (fixed): context/interval/granularity all genuinely resolve to the YEAR group
   // here, so this should reach the "all equal" branch and delegate to showPartLevelAsDate().
   // Before the fix, contextLevel was miscalculated as MONTH_DATE_GROUP(3) via the wrong
   // bit-mask decoder, so contextLevel != intervalLevel(5), skipping the "all equal" branch
   // and short-circuiting the final OR to true via periodVal(5) != contextLevel(3).
   it("should delegate to showPartLevelAsDate() when context/interval/granularity all represent the YEAR group (Bug #75654)", async () => {
      const model = makeDateComparisonPaneModel({
         periodPaneModel: makePeriodPaneModel({
            custom: false,
            standardPeriodPaneModel: makeStandardPeriodPaneModel({ dateLevel: makeDynamicValue(XConstants.YEAR_DATE_GROUP + "") }),
         }),
         intervalPaneModel: makeIntervalPaneModel({
            level: makeDynamicValue(IntervalLevel.YEAR_TO_DATE + ""), // -> YEAR_DATE_GROUP(5)
            contextLevel: makeDynamicValue(XConstants.YEAR_DATE_GROUP), // intended: YEAR_DATE_GROUP(5)
            granularity: makeDynamicValue(IntervalLevel.YEAR + ""), // -> YEAR_DATE_GROUP(5)
         }),
      });
      const { comp } = await renderComponent({ assemblyType: "VSChart", dateComparisonPaneModel: model });

      // showPartLevelAsDate() would return false: granularity "16" is neither DAY("1") nor WEEK("2").
      expect(comp.isOnlyShowRecentDateVisible()).toBe(false);
   });

   it("should short-circuit past the \"all equal\" branch when interval/context/granularity are all unrecognized (-1)", async () => {
      // Isolates the FIRST operand of the "all equal" guard (contextLevel != -1) being false
      // alone. All three dynamic values here have a non-VALUE type, so
      // dcIntervalLevelToDateGroupLevel returns -1 for all of them. Without the `contextLevel
      // != -1 &&` guard, `contextLevel==intervalLevel && contextLevel==granularity` would be
      // true (-1==-1==-1) and wrongly delegate to showPartLevelAsDate().
      const model = makeDateComparisonPaneModel({
         periodPaneModel: makePeriodPaneModel({
            custom: false,
            standardPeriodPaneModel: makeStandardPeriodPaneModel({ dateLevel: makeDynamicValue(XConstants.MONTH_DATE_GROUP + "") }),
         }),
         intervalPaneModel: makeIntervalPaneModel({
            level: makeDynamicValue("", ValueTypes.VARIABLE), // -> -1
            contextLevel: makeDynamicValue("", ValueTypes.VARIABLE), // -> -1
            granularity: makeDynamicValue("", ValueTypes.VARIABLE), // -> -1
         }),
      });
      const { comp } = await renderComponent({ assemblyType: "VSChart", dateComparisonPaneModel: model });

      // Guarded (real) path: contextLevel(-1) != -1 is false, so the "all equal" branch is
      // skipped; falls to periodVal("3") != contextLevel(-1) -> true -> result true. Without
      // the guard, showPartLevelAsDate() would instead be delegated to and return false
      // (dateLevel=MONTH_DATE_GROUP is neither YEAR nor QUARTER) — the two paths diverge.
      expect(comp.isOnlyShowRecentDateVisible()).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 8: dcIntervalLevelToDateGroupLevel (private, via cast) — IntervalLevel domain
// ---------------------------------------------------------------------------

// Bypass: dcIntervalLevelToDateGroupLevel is private with no public wrapper; all tests in this
// group call it via an `as any` cast to isolate its bit-mask logic directly, independent of the
// isOnlyShowRecentDateVisible() call sites exercised in Group 7 (including the buggy one).
describe("DateComparisonPaneComponent — dcIntervalLevelToDateGroupLevel", () => {
   it("should map each IntervalLevel bit to its XConstants date-group value", async () => {
      const { comp } = await renderComponent();
      const convert = (v: any) => (comp as any).dcIntervalLevelToDateGroupLevel(makeDynamicValue(v + ""));

      expect(convert(IntervalLevel.YEAR)).toBe(XConstants.YEAR_DATE_GROUP);
      expect(convert(IntervalLevel.QUARTER)).toBe(XConstants.QUARTER_DATE_GROUP);
      expect(convert(IntervalLevel.MONTH)).toBe(XConstants.MONTH_DATE_GROUP);
      expect(convert(IntervalLevel.WEEK)).toBe(XConstants.WEEK_DATE_GROUP);
      expect(convert(IntervalLevel.DAY)).toBe(XConstants.DAY_DATE_GROUP);
   });

   it("should return -1 for a value with no recognized bit set", async () => {
      const { comp } = await renderComponent();

      expect((comp as any).dcIntervalLevelToDateGroupLevel(makeDynamicValue("0"))).toBe(-1);
   });

   it("should return -1 when the value's type is not VALUE", async () => {
      const { comp } = await renderComponent();

      expect((comp as any).dcIntervalLevelToDateGroupLevel(
         makeDynamicValue(IntervalLevel.YEAR + "", ValueTypes.VARIABLE))).toBe(-1);
   });
});
