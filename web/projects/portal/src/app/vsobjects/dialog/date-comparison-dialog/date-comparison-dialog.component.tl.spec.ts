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
 * DateComparisonDialog — single pass
 *
 * Direct instantiation — two constructor dependencies (DateComparisonService,
 * ModelService), neither with `inject()` calls, so no TestBed wiring is needed.
 *
 * Scope: ngOnInit, isSharePaneVisible/shareFromAvailableNames getters, toDateDisabled
 * (the guard chain + convertIntervalLevel dispatch), intervalEndDate getter,
 * updateShareStatus/updateShareFrom (HTTP via ModelService), close/ok/apply/submit,
 * isValid, clearDateComparison, updateShareAssemblyType.
 *
 * Risk-first coverage:
 *   Group 4 [Risk 3] — toDateDisabled: 4-operand guard chain + convertIntervalLevel's
 *                       10-case dispatch, the most complex logic in the component
 *   Group 7 [Risk 2] — updateShareFrom: HTTP round-trip via ModelService.getModel
 *   Group 8 [Risk 2] — submit (ok/apply): shareFromAssembly reset + toDate-disabled
 *                       side effect before emitting onCommit/onApply
 *   Remaining groups [Risk 1/2] — single-purpose getters/emitters
 *
 * Confirmed bugs (it.fails): none
 */

import { of } from "rxjs";
import { DateComparisonDialog } from "./date-comparison-dialog.component";
import { DateComparisonService } from "../../util/date-comparison.service";
import { ModelService } from "../../../widget/services/model.service";
import { DateComparisonDialogModel } from "../../model/date-comparison-dialog-model";
import { DateComparisonPaneModel } from "../../model/date-comparison-pane-model";
import { PeriodPaneModel } from "../../model/period-pane-model";
import { IntervalPaneModel, IntervalLevel } from "../../model/interval-pane-model";
import { DynamicValueModel, ValueTypes } from "../../model/dynamic-value-model";
import { XConstants } from "../../../common/util/xconstants";

afterEach(() => vi.restoreAllMocks());

function makeDynamicValue(overrides: Partial<DynamicValueModel> = {}): DynamicValueModel {
   return Object.assign({ value: "1", type: ValueTypes.VALUE as string }, overrides);
}

function makeIntervalPaneModel(overrides: Partial<IntervalPaneModel> = {}): IntervalPaneModel {
   return Object.assign({
      level: makeDynamicValue({ value: IntervalLevel.YEAR }),
      granularity: makeDynamicValue(),
      endDayAsToDate: true,
      intervalEndDate: null,
      inclusive: false,
      contextLevel: makeDynamicValue({ value: XConstants.YEAR_DATE_GROUP }),
   }, overrides);
}

function makePeriodPaneModel(overrides: Partial<PeriodPaneModel> = {}): PeriodPaneModel {
   return Object.assign({
      customPeriodPaneModel: {} as any,
      standardPeriodPaneModel: {
         preCount: makeDynamicValue(), dateLevel: makeDynamicValue({ value: XConstants.YEAR_DATE_GROUP + "" }),
         toDate: true, endDay: makeDynamicValue(), toDayAsEndDay: true, inclusive: false,
      },
      custom: false,
   }, overrides);
}

function makeDateComparisonPaneModel(overrides: Partial<DateComparisonPaneModel> = {}): DateComparisonPaneModel {
   return Object.assign({
      periodPaneModel: makePeriodPaneModel(),
      intervalPaneModel: makeIntervalPaneModel(),
      comparisonOption: 1,
      useFacet: false,
      onlyShowMostRecentDate: false,
      visualFrameModel: {} as any,
   }, overrides);
}

function makeDialogModel(overrides: Partial<DateComparisonDialogModel> = {}): DateComparisonDialogModel {
   return Object.assign({
      dateComparisonPaneModel: makeDateComparisonPaneModel(),
      shareFromAssembly: null,
      shareFromAvailableAssemblies: [],
   }, overrides);
}

function createComponent(modelOverrides: Partial<DateComparisonDialogModel> = {}) {
   const dateComparisonService = {} as DateComparisonService;
   const modelService = { getModel: vi.fn(() => of(makeDateComparisonPaneModel())) };
   const comp = new DateComparisonDialog(dateComparisonService, modelService as unknown as ModelService);
   comp.dateComparisonDialogModel = makeDialogModel(modelOverrides);
   comp.runtimeId = "rt1";
   return { comp, modelService };
}

// ---------------------------------------------------------------------------
// Group 1: ngOnInit [Risk 2]
// ---------------------------------------------------------------------------

describe("DateComparisonDialog — ngOnInit", () => {
   it("should NOT enable share mode and skip assembly-type lookup when there is no shareFromAssembly", () => {
      const { comp } = createComponent({ shareFromAssembly: null });
      comp.ngOnInit();
      expect(comp.isShareDateComparison).toBe(false);
      expect(comp.shareAssemblyType).toBeUndefined();
   });

   it("should enable share mode and resolve the assembly type when shareFromAssembly is set", () => {
      const { comp } = createComponent({
         shareFromAssembly: "Chart1",
         shareFromAvailableAssemblies: [{ key: "Chart1", value: 5 }],
      });
      comp.ngOnInit();
      expect(comp.isShareDateComparison).toBe(true);
      expect(comp.shareAssemblyType).toBe(5);
   });
});

// ---------------------------------------------------------------------------
// Group 2: isSharePaneVisible / shareFromAvailableNames [Risk 1]
// ---------------------------------------------------------------------------

describe("DateComparisonDialog — isSharePaneVisible / shareFromAvailableNames", () => {
   it("should be visible when there is at least one shareable assembly", () => {
      const { comp } = createComponent({ shareFromAvailableAssemblies: [{ key: "a", value: 1 }] });
      expect(comp.isSharePaneVisible).toBe(true);
   });

   it("should be hidden when there are no shareable assemblies", () => {
      const { comp } = createComponent({ shareFromAvailableAssemblies: [] });
      expect(comp.isSharePaneVisible).toBe(false);
   });

   it("should map the shareable assemblies down to their keys", () => {
      const { comp } = createComponent({
         shareFromAvailableAssemblies: [{ key: "a", value: 1 }, { key: "b", value: 2 }],
      });
      expect(comp.shareFromAvailableNames).toEqual(["a", "b"]);
   });

   it("should return an empty array when shareFromAvailableAssemblies is null", () => {
      const { comp } = createComponent({ shareFromAvailableAssemblies: null });
      expect(comp.shareFromAvailableNames).toEqual([]);
   });
});

// ---------------------------------------------------------------------------
// Group 3: intervalEndDate [Risk 1]
// ---------------------------------------------------------------------------

describe("DateComparisonDialog — intervalEndDate", () => {
   it("should be null when endDayAsToDate is set (interval end tracks 'today')", () => {
      const { comp } = createComponent({
         dateComparisonPaneModel: makeDateComparisonPaneModel({
            intervalPaneModel: makeIntervalPaneModel({ endDayAsToDate: true, intervalEndDate: makeDynamicValue({ value: "2024-01-01" }) }),
         }),
      });
      expect(comp.intervalEndDate).toBeNull();
   });

   it("should be null when there is no explicit interval end date", () => {
      const { comp } = createComponent({
         dateComparisonPaneModel: makeDateComparisonPaneModel({
            intervalPaneModel: makeIntervalPaneModel({ endDayAsToDate: false, intervalEndDate: null }),
         }),
      });
      expect(comp.intervalEndDate).toBeNull();
   });

   it("should return the literal end-date value when explicitly set", () => {
      const { comp } = createComponent({
         dateComparisonPaneModel: makeDateComparisonPaneModel({
            intervalPaneModel: makeIntervalPaneModel({
               endDayAsToDate: false,
               intervalEndDate: makeDynamicValue({ value: "2024-06-01" }),
            }),
         }),
      });
      expect(comp.intervalEndDate).toBe("2024-06-01");
   });
});

// ---------------------------------------------------------------------------
// Group 4: toDateDisabled [Risk 3]
// ---------------------------------------------------------------------------

describe("DateComparisonDialog — toDateDisabled", () => {
   it("should be enabled (false) when using a custom period", () => {
      const { comp } = createComponent({
         dateComparisonPaneModel: makeDateComparisonPaneModel({
            periodPaneModel: makePeriodPaneModel({ custom: true }),
         }),
      });
      expect(comp.toDateDisabled).toBe(false);
   });

   it("should be enabled (false) when the standard period's dateLevel is not a literal VALUE", () => {
      const { comp } = createComponent({
         dateComparisonPaneModel: makeDateComparisonPaneModel({
            periodPaneModel: makePeriodPaneModel({
               standardPeriodPaneModel: {
                  preCount: makeDynamicValue(), dateLevel: makeDynamicValue({ type: ValueTypes.VARIABLE }),
                  toDate: true, endDay: makeDynamicValue(), toDayAsEndDay: true, inclusive: false,
               },
            }),
         }),
      });
      expect(comp.toDateDisabled).toBe(false);
   });

   it("should be enabled (false) when the interval level is not a literal VALUE", () => {
      const { comp } = createComponent({
         dateComparisonPaneModel: makeDateComparisonPaneModel({
            intervalPaneModel: makeIntervalPaneModel({ level: makeDynamicValue({ type: ValueTypes.VARIABLE }) }),
         }),
      });
      expect(comp.toDateDisabled).toBe(false);
   });

   it("should be enabled (false) when the context level is not a literal VALUE", () => {
      const { comp } = createComponent({
         dateComparisonPaneModel: makeDateComparisonPaneModel({
            intervalPaneModel: makeIntervalPaneModel({ contextLevel: makeDynamicValue({ type: ValueTypes.VARIABLE }) }),
         }),
      });
      expect(comp.toDateDisabled).toBe(false);
   });

   it("should be disabled (true) when the period, interval, and context levels all resolve to the same date group", () => {
      const { comp } = createComponent({
         dateComparisonPaneModel: makeDateComparisonPaneModel({
            periodPaneModel: makePeriodPaneModel({
               custom: false,
               standardPeriodPaneModel: {
                  preCount: makeDynamicValue(), dateLevel: makeDynamicValue({ value: XConstants.YEAR_DATE_GROUP + "" }),
                  toDate: true, endDay: makeDynamicValue(), toDayAsEndDay: true, inclusive: false,
               },
            }),
            intervalPaneModel: makeIntervalPaneModel({
               level: makeDynamicValue({ value: IntervalLevel.YEAR_TO_DATE }),
               contextLevel: makeDynamicValue({ value: XConstants.YEAR_DATE_GROUP + "" }),
            }),
         }),
      });
      expect(comp.toDateDisabled).toBe(true);
   });

   it("should stay enabled (false) when the period level does not match the interval level", () => {
      const { comp } = createComponent({
         dateComparisonPaneModel: makeDateComparisonPaneModel({
            periodPaneModel: makePeriodPaneModel({
               custom: false,
               standardPeriodPaneModel: {
                  preCount: makeDynamicValue(), dateLevel: makeDynamicValue({ value: XConstants.MONTH_DATE_GROUP + "" }),
                  toDate: true, endDay: makeDynamicValue(), toDayAsEndDay: true, inclusive: false,
               },
            }),
            intervalPaneModel: makeIntervalPaneModel({
               level: makeDynamicValue({ value: IntervalLevel.YEAR_TO_DATE }),
               contextLevel: makeDynamicValue({ value: XConstants.YEAR_DATE_GROUP + "" }),
            }),
         }),
      });
      expect(comp.toDateDisabled).toBe(false);
   });

   it("should stay enabled (false) when the period matches the interval level but the context level differs", () => {
      // Exercises the second half of the `periodLevel == intervalLevel && intervalLevel ==
      // contextLevel` chain specifically — the first test above only ever fails on the first
      // comparison, so the second comparison's own falsy branch was never hit.
      const { comp } = createComponent({
         dateComparisonPaneModel: makeDateComparisonPaneModel({
            periodPaneModel: makePeriodPaneModel({
               custom: false,
               standardPeriodPaneModel: {
                  preCount: makeDynamicValue(), dateLevel: makeDynamicValue({ value: XConstants.YEAR_DATE_GROUP + "" }),
                  toDate: true, endDay: makeDynamicValue(), toDayAsEndDay: true, inclusive: false,
               },
            }),
            intervalPaneModel: makeIntervalPaneModel({
               level: makeDynamicValue({ value: IntervalLevel.YEAR_TO_DATE }),
               contextLevel: makeDynamicValue({ value: XConstants.MONTH_DATE_GROUP + "" }),
            }),
         }),
      });
      expect(comp.toDateDisabled).toBe(false);
   });

   it.each([
      [IntervalLevel.YEAR, XConstants.YEAR_DATE_GROUP],
      [IntervalLevel.YEAR_TO_DATE, XConstants.YEAR_DATE_GROUP],
      [IntervalLevel.QUARTER, XConstants.QUARTER_DATE_GROUP],
      [IntervalLevel.QUARTER_TO_DATE, XConstants.QUARTER_DATE_GROUP],
      [IntervalLevel.MONTH, XConstants.MONTH_DATE_GROUP],
      [IntervalLevel.MONTH_TO_DATE, XConstants.MONTH_DATE_GROUP],
      [IntervalLevel.WEEK, XConstants.WEEK_DATE_GROUP],
      [IntervalLevel.WEEK_TO_DATE, XConstants.WEEK_DATE_GROUP],
      [IntervalLevel.DAY, XConstants.DAY_DATE_GROUP],
   ])("should map interval level %s to date group %s via convertIntervalLevel", (intervalLevel, dateGroup) => {
      const { comp } = createComponent({
         dateComparisonPaneModel: makeDateComparisonPaneModel({
            periodPaneModel: makePeriodPaneModel({
               custom: false,
               standardPeriodPaneModel: {
                  preCount: makeDynamicValue(), dateLevel: makeDynamicValue({ value: dateGroup + "" }),
                  toDate: true, endDay: makeDynamicValue(), toDayAsEndDay: true, inclusive: false,
               },
            }),
            intervalPaneModel: makeIntervalPaneModel({
               level: makeDynamicValue({ value: intervalLevel }),
               contextLevel: makeDynamicValue({ value: dateGroup + "" }),
            }),
         }),
      });
      expect(comp.toDateDisabled).toBe(true);
   });

   it("should treat an unrecognized interval level as -1, never matching a real date group", () => {
      // dateLevel/contextLevel are set to a real date group (YEAR); convertIntervalLevel of an
      // unrecognized level falls back to -1, which must NOT accidentally equal that real group.
      const { comp } = createComponent({
         dateComparisonPaneModel: makeDateComparisonPaneModel({
            periodPaneModel: makePeriodPaneModel({
               custom: false,
               standardPeriodPaneModel: {
                  preCount: makeDynamicValue(), dateLevel: makeDynamicValue({ value: XConstants.YEAR_DATE_GROUP + "" }),
                  toDate: true, endDay: makeDynamicValue(), toDayAsEndDay: true, inclusive: false,
               },
            }),
            intervalPaneModel: makeIntervalPaneModel({
               level: makeDynamicValue({ value: 0xFFFF }),
               contextLevel: makeDynamicValue({ value: XConstants.YEAR_DATE_GROUP + "" }),
            }),
         }),
      });
      expect(comp.toDateDisabled).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 5: updateShareStatus [Risk 2]
// ---------------------------------------------------------------------------

describe("DateComparisonDialog — updateShareStatus", () => {
   it("should fetch the shared model when turning share mode on with an assembly already selected", () => {
      const { comp, modelService } = createComponent({ shareFromAssembly: "Chart1" });

      comp.updateShareStatus(true);

      expect(comp.isShareDateComparison).toBe(true);
      expect(modelService.getModel).toHaveBeenCalledWith(
         "../api/composer/vs/date-comparison-model/Chart1/rt1", null
      );
   });

   it("should NOT fetch anything when turning share mode on without a selected assembly", () => {
      const { comp, modelService } = createComponent({ shareFromAssembly: null });

      comp.updateShareStatus(true);

      expect(modelService.getModel).not.toHaveBeenCalled();
   });

   it("should turn share mode off without fetching", () => {
      const { comp, modelService } = createComponent({ shareFromAssembly: "Chart1" });

      comp.updateShareStatus(false);

      expect(comp.isShareDateComparison).toBe(false);
      expect(modelService.getModel).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 6: updateShareFrom [Risk 2]
// ---------------------------------------------------------------------------

describe("DateComparisonDialog — updateShareFrom", () => {
   it("should record the source assembly and replace the pane model from the fetched response", () => {
      const fetchedModel = makeDateComparisonPaneModel({ comparisonOption: 99 });
      const { comp, modelService } = createComponent({
         shareFromAssembly: "old",
         shareFromAvailableAssemblies: [{ key: "Chart2", value: 7 }],
      });
      modelService.getModel.mockReturnValue(of(fetchedModel));

      comp.updateShareFrom("Chart2");

      expect(comp.dateComparisonDialogModel.shareFromAssembly).toBe("Chart2");
      expect(modelService.getModel).toHaveBeenCalledWith(
         "../api/composer/vs/date-comparison-model/Chart2/rt1", null
      );
      expect(comp.dateComparisonDialogModel.dateComparisonPaneModel).toBe(fetchedModel);
   });

   it("should resolve the new assembly's share type as part of the switch", () => {
      const { comp, modelService } = createComponent({
         shareFromAvailableAssemblies: [{ key: "Chart2", value: 7 }],
      });
      comp.isShareDateComparison = true;
      modelService.getModel.mockReturnValue(of(makeDateComparisonPaneModel()));

      comp.updateShareFrom("Chart2");

      expect(comp.shareAssemblyType).toBe(7);
   });
});

// ---------------------------------------------------------------------------
// Group 7: close / clearDateComparison [Risk 1]
// ---------------------------------------------------------------------------

describe("DateComparisonDialog — close / clearDateComparison", () => {
   it("should emit onCancel with 'cancel'", () => {
      const { comp } = createComponent();
      const emitted: string[] = [];
      comp.onCancel.subscribe((v: string) => emitted.push(v));
      comp.close();
      expect(emitted).toEqual(["cancel"]);
   });

   it("should emit onClear", () => {
      const { comp } = createComponent();
      const emitted: any[] = [];
      comp.onClear.subscribe((v: any) => emitted.push(v));
      comp.clearDateComparison();
      expect(emitted).toHaveLength(1);
   });
});

// ---------------------------------------------------------------------------
// Group 8: ok / apply -> submit [Risk 2]
// ---------------------------------------------------------------------------

describe("DateComparisonDialog — ok / apply (submit)", () => {
   it("should clear shareFromAssembly and emit onCommit when confirming outside share mode", () => {
      const { comp } = createComponent({ shareFromAssembly: "stale" });
      comp.isShareDateComparison = false;
      const emitted: DateComparisonDialogModel[] = [];
      comp.onCommit.subscribe((v: DateComparisonDialogModel) => emitted.push(v));

      comp.ok();

      expect(comp.dateComparisonDialogModel.shareFromAssembly).toBeNull();
      expect(emitted).toEqual([comp.dateComparisonDialogModel]);
   });

   it("should keep shareFromAssembly when confirming while sharing is active", () => {
      const { comp } = createComponent({ shareFromAssembly: "Chart1" });
      comp.isShareDateComparison = true;

      comp.ok();

      expect(comp.dateComparisonDialogModel.shareFromAssembly).toBe("Chart1");
   });

   it("should force toDate=false on the standard-period model when the to-date option is disabled", () => {
      const { comp } = createComponent({
         dateComparisonPaneModel: makeDateComparisonPaneModel({
            periodPaneModel: makePeriodPaneModel({
               custom: false,
               standardPeriodPaneModel: {
                  preCount: makeDynamicValue(), dateLevel: makeDynamicValue({ value: XConstants.YEAR_DATE_GROUP + "" }),
                  toDate: true, endDay: makeDynamicValue(), toDayAsEndDay: true, inclusive: false,
               },
            }),
            intervalPaneModel: makeIntervalPaneModel({
               level: makeDynamicValue({ value: IntervalLevel.YEAR_TO_DATE }),
               contextLevel: makeDynamicValue({ value: XConstants.YEAR_DATE_GROUP + "" }),
            }),
         }),
      });

      comp.ok();

      expect(comp.dateComparisonDialogModel.dateComparisonPaneModel
         .periodPaneModel.standardPeriodPaneModel.toDate).toBe(false);
   });

   it("should emit onApply with the collapse flag and the dialog model when applying", () => {
      const { comp } = createComponent();
      const emitted: any[] = [];
      comp.onApply.subscribe((v: any) => emitted.push(v));

      comp.apply(true);

      expect(emitted).toEqual([{ collapse: true, result: comp.dateComparisonDialogModel }]);
   });
});

// ---------------------------------------------------------------------------
// Group 9: isValid [Risk 2]
// ---------------------------------------------------------------------------

describe("DateComparisonDialog — isValid", () => {
   it("should require a selected share-from assembly while sharing", () => {
      const { comp } = createComponent({ shareFromAssembly: "Chart1" });
      comp.isShareDateComparison = true;
      expect(comp.isValid()).toBe(true);
   });

   it("should be invalid while sharing with no assembly selected", () => {
      const { comp } = createComponent({ shareFromAssembly: null });
      comp.isShareDateComparison = true;
      expect(comp.isValid()).toBe(false);
   });

   it("should defer to the date-comparison pane's own validity outside share mode", () => {
      const { comp } = createComponent();
      comp.isShareDateComparison = false;
      (comp as any).dateComparisonPane = { isValidDateComparison: true };
      expect(comp.isValid()).toBe(true);
   });

   it("should be invalid outside share mode when the pane hasn't been rendered yet", () => {
      const { comp } = createComponent();
      comp.isShareDateComparison = false;
      (comp as any).dateComparisonPane = undefined;
      expect(comp.isValid()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 10: updateShareAssemblyType [Risk 2]
// ---------------------------------------------------------------------------

describe("DateComparisonDialog — updateShareAssemblyType", () => {
   it("should do nothing outside share mode", () => {
      const { comp } = createComponent();
      comp.isShareDateComparison = false;
      comp.shareAssemblyType = 42;

      comp.updateShareAssemblyType();

      expect(comp.shareAssemblyType).toBe(42);
   });

   it("should resolve the matching assembly's type while sharing", () => {
      const { comp } = createComponent({
         shareFromAssembly: "Chart1",
         shareFromAvailableAssemblies: [{ key: "Chart1", value: 3 }, { key: "Chart2", value: 4 }],
      });
      comp.isShareDateComparison = true;

      comp.updateShareAssemblyType();

      expect(comp.shareAssemblyType).toBe(3);
   });

   it("should default to 0 when the selected assembly is no longer in the available list", () => {
      const { comp } = createComponent({
         shareFromAssembly: "Gone",
         shareFromAvailableAssemblies: [{ key: "Chart1", value: 3 }],
      });
      comp.isShareDateComparison = true;

      comp.updateShareAssemblyType();

      expect(comp.shareAssemblyType).toBe(0);
   });
});
