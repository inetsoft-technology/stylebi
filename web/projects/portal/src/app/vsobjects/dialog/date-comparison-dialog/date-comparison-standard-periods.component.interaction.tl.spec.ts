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
 * DateComparisonStandardPeriodsComponent — Pass 1: Interaction
 *
 * Direct instantiation (see test-helpers.ts) — single constructor dependency
 * (DateComparisonService), no `inject()` calls anywhere in the chain.
 *
 * Scope (per prescan Pass 1 method list): ngOnChanges, updateVisibleLevel,
 * updateLevelType, updatePreviousCountValue, updatePreviousCountType, updateValid,
 * isValid, isInvalidStandardPeriodPreCount, isValidStandardPeriodEndDay, verifyEndDate,
 * showInclusive, endDate (getter).
 *
 * Risk-first coverage:
 *   Group 7 [Risk 2] — endDate getter: toDayAsEndDay branch, null-guards, real date parsing
 *   Group 6 [Risk 2] — isValid/isInvalidStandardPeriodPreCount/isValidStandardPeriodEndDay:
 *                       the validity combination that ngOnChanges/updateValid emit
 *   Groups 1/3/4 [Risk 2] — ngOnChanges and the two updatePreviousCount* methods, since
 *                       they emit validChange as a side effect of a model mutation
 *   Remaining groups [Risk 1] — single-purpose model setters and pass-throughs
 *
 * Confirmed bugs (it.fails): none
 *
 * Out of scope this pass (covered in
 * date-comparison-standard-periods.component.display.tl.spec.ts):
 *   visibleStandardPeriodLevel, toDateLabel, toDateVisible, inclusiveLabel getters;
 *   dayOfWeek/monthOfQuarter private helpers.
 */

import { DateComparisonService } from "../../util/date-comparison.service";
import { ValueTypes } from "../../model/dynamic-value-model";
import { XConstants } from "../../../common/util/xconstants";
import { DateTypeFormatter } from "../../../../../../shared/util/date-type-formatter";
import {
   createComponent, makeDynamicValue, makeStandardPeriodModel,
} from "./date-comparison-standard-periods.component.test-helpers";

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: ngOnChanges [Risk 2]
// ---------------------------------------------------------------------------

describe("DateComparisonStandardPeriodsComponent — ngOnChanges", () => {
   it("should emit validChange(true) when the model is currently valid", () => {
      const { comp, dateComparisonService } = createComponent();
      dateComparisonService.isValidDate.mockReturnValue(true);
      const emitted: boolean[] = [];
      comp.validChange.subscribe((v: boolean) => emitted.push(v));

      comp.ngOnChanges();

      expect(emitted).toEqual([true]);
   });

   it("should emit validChange(false) when the pre-count is a negative, non-dynamic value", () => {
      const { comp } = createComponent({
         model: makeStandardPeriodModel({ preCount: makeDynamicValue({ value: "-1" }) }),
      });
      const emitted: boolean[] = [];
      comp.validChange.subscribe((v: boolean) => emitted.push(v));

      comp.ngOnChanges();

      expect(emitted).toEqual([false]);
   });
});

// ---------------------------------------------------------------------------
// Group 2: updateVisibleLevel / updateLevelType [Risk 1]
// ---------------------------------------------------------------------------

describe("DateComparisonStandardPeriodsComponent — updateVisibleLevel / updateLevelType", () => {
   it("should set the dateLevel value directly", () => {
      const { comp } = createComponent();
      comp.updateVisibleLevel(XConstants.WEEK_DATE_GROUP + "");
      expect(comp.standardPeriodPaneModel.dateLevel.value).toBe(XConstants.WEEK_DATE_GROUP + "");
   });

   it("should set the dateLevel type via the date-comparison service", () => {
      const { comp, dateComparisonService } = createComponent();
      dateComparisonService.getDateComparisonValueTypeStr.mockReturnValue(ValueTypes.VARIABLE);

      comp.updateLevelType(2);

      expect(dateComparisonService.getDateComparisonValueTypeStr).toHaveBeenCalledWith(2);
      expect(comp.standardPeriodPaneModel.dateLevel.type).toBe(ValueTypes.VARIABLE);
   });
});

// ---------------------------------------------------------------------------
// Group 3: updatePreviousCountValue [Risk 2]
// ---------------------------------------------------------------------------

describe("DateComparisonStandardPeriodsComponent — updatePreviousCountValue", () => {
   it("should set the preCount value and emit the resulting validity", () => {
      const { comp } = createComponent();
      const emitted: boolean[] = [];
      comp.validChange.subscribe((v: boolean) => emitted.push(v));

      comp.updatePreviousCountValue("3");

      expect(comp.standardPeriodPaneModel.preCount.value).toBe("3");
      expect(emitted).toEqual([true]);
   });

   it("should emit invalid when the new count is negative", () => {
      const { comp } = createComponent();
      const emitted: boolean[] = [];
      comp.validChange.subscribe((v: boolean) => emitted.push(v));

      comp.updatePreviousCountValue("-5");

      expect(emitted).toEqual([false]);
   });
});

// ---------------------------------------------------------------------------
// Group 4: updatePreviousCountType [Risk 2]
// ---------------------------------------------------------------------------

describe("DateComparisonStandardPeriodsComponent — updatePreviousCountType", () => {
   it("should set the preCount type via the service and emit validity", () => {
      const { comp, dateComparisonService } = createComponent();
      dateComparisonService.getDateComparisonValueTypeStr.mockReturnValue(ValueTypes.EXPRESSION);
      const emitted: boolean[] = [];
      comp.validChange.subscribe((v: boolean) => emitted.push(v));

      comp.updatePreviousCountType(3);

      expect(dateComparisonService.getDateComparisonValueTypeStr).toHaveBeenCalledWith(3);
      expect(comp.standardPeriodPaneModel.preCount.type).toBe(ValueTypes.EXPRESSION);
      expect(emitted).toEqual([true]);
   });
});

// ---------------------------------------------------------------------------
// Group 5: updateValid / showInclusive / verifyEndDate [Risk 1]
// ---------------------------------------------------------------------------

describe("DateComparisonStandardPeriodsComponent — updateValid / showInclusive / verifyEndDate", () => {
   it("should emit the current validity without mutating the model", () => {
      const { comp } = createComponent();
      const emitted: boolean[] = [];
      comp.validChange.subscribe((v: boolean) => emitted.push(v));

      comp.updateValid();

      expect(emitted).toEqual([true]);
   });

   it("should expose toDate as the inclusive-visibility flag", () => {
      const { comp } = createComponent({ model: makeStandardPeriodModel({ toDate: true }) });
      expect(comp.showInclusive()).toBe(true);
   });

   it("should delegate end-date validation to the date-comparison service", () => {
      const { comp, dateComparisonService } = createComponent({
         model: makeStandardPeriodModel({ endDay: makeDynamicValue({ value: "2024-05-01" }) }),
      });
      dateComparisonService.isValidDate.mockReturnValue(false);

      expect(comp.verifyEndDate()).toBe(false);
      expect(dateComparisonService.isValidDate).toHaveBeenCalledWith("2024-05-01");
   });
});

// ---------------------------------------------------------------------------
// Group 6: isValid / isInvalidStandardPeriodPreCount / isValidStandardPeriodEndDay [Risk 2]
// ---------------------------------------------------------------------------

describe("DateComparisonStandardPeriodsComponent — isInvalidStandardPeriodPreCount", () => {
   it("should be invalid for a negative, non-dynamic pre-count", () => {
      const { comp } = createComponent({
         model: makeStandardPeriodModel({ preCount: makeDynamicValue({ value: "-2" }) }),
      });
      expect(comp.isInvalidStandardPeriodPreCount()).toBe(true);
   });

   it("should be valid for a non-negative pre-count", () => {
      const { comp } = createComponent({
         model: makeStandardPeriodModel({ preCount: makeDynamicValue({ value: "0" }) }),
      });
      expect(comp.isInvalidStandardPeriodPreCount()).toBe(false);
   });

   it("should treat a negative-parsing dynamic expression as valid (not a real negative count)", () => {
      // Tool.isDynamic() treats any string containing "($" as dynamic; parseInt("-1($x)")
      // still parses to -1, so this specifically exercises the "!isDynamic" guard rather
      // than accidentally passing because parseInt returned NaN.
      const { comp } = createComponent({
         model: makeStandardPeriodModel({ preCount: makeDynamicValue({ value: "-1($x)" }) }),
      });
      expect(comp.isInvalidStandardPeriodPreCount()).toBe(false);
   });
});

describe("DateComparisonStandardPeriodsComponent — isValidStandardPeriodEndDay", () => {
   it("should always be valid when toDayAsEndDay is set, without checking the end date", () => {
      const { comp, dateComparisonService } = createComponent({
         model: makeStandardPeriodModel({ toDayAsEndDay: true }),
      });
      dateComparisonService.isValidDate.mockReturnValue(false);

      expect(comp.isValidStandardPeriodEndDay()).toBe(true);
      expect(dateComparisonService.isValidDate).not.toHaveBeenCalled();
   });

   it("should defer to verifyEndDate when toDayAsEndDay is not set", () => {
      const { comp, dateComparisonService } = createComponent({
         model: makeStandardPeriodModel({ toDayAsEndDay: false }),
      });
      dateComparisonService.isValidDate.mockReturnValue(false);

      expect(comp.isValidStandardPeriodEndDay()).toBe(false);
   });
});

describe("DateComparisonStandardPeriodsComponent — isValid", () => {
   it("should be valid only when both the pre-count and end-day checks pass", () => {
      const { comp, dateComparisonService } = createComponent({
         model: makeStandardPeriodModel({ preCount: makeDynamicValue({ value: "1" }) }),
      });
      dateComparisonService.isValidDate.mockReturnValue(true);
      expect(comp.isValid()).toBe(true);
   });

   it("should be invalid when the pre-count check fails, even if the end day is fine", () => {
      const { comp, dateComparisonService } = createComponent({
         model: makeStandardPeriodModel({ preCount: makeDynamicValue({ value: "-1" }) }),
      });
      dateComparisonService.isValidDate.mockReturnValue(true);
      expect(comp.isValid()).toBe(false);
   });

   it("should be invalid when the end-day check fails, even if the pre-count is fine", () => {
      const { comp, dateComparisonService } = createComponent({
         model: makeStandardPeriodModel({ preCount: makeDynamicValue({ value: "1" }) }),
      });
      dateComparisonService.isValidDate.mockReturnValue(false);
      expect(comp.isValid()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 7: endDate getter [Risk 2]
// ---------------------------------------------------------------------------

describe("DateComparisonStandardPeriodsComponent — endDate", () => {
   it("should return today's date when toDayAsEndDay is set", () => {
      const { comp } = createComponent({ model: makeStandardPeriodModel({ toDayAsEndDay: true }) });
      expect(comp.endDate).toBeInstanceOf(Date);
   });

   it("should return null when endDay is unset", () => {
      const { comp } = createComponent({
         model: makeStandardPeriodModel({ toDayAsEndDay: false, endDay: null }),
      });
      expect(comp.endDate).toBeNull();
   });

   it("should return null when endDay is not a literal VALUE (e.g. a variable)", () => {
      const { comp } = createComponent({
         model: makeStandardPeriodModel({
            toDayAsEndDay: false,
            endDay: makeDynamicValue({ value: "$(myVar)", type: ValueTypes.VARIABLE }),
         }),
      });
      expect(comp.endDate).toBeNull();
   });

   it("should parse a literal ISO end-day value into the matching real Date", () => {
      const { comp } = createComponent({
         model: makeStandardPeriodModel({
            toDayAsEndDay: false,
            endDay: makeDynamicValue({ value: "2024-03-15" }),
         }),
      });

      const timeInstant = DateTypeFormatter.toTimeInstant("2024-03-15", DateTypeFormatter.ISO_8601_DATE_FORMAT);
      const expected = DateTypeFormatter.timeInstantToDate(timeInstant);

      expect(comp.endDate.getTime()).toBe(expected.getTime());
   });

   it("should return null when the literal end-day value is not a parseable date", () => {
      const { comp } = createComponent({
         model: makeStandardPeriodModel({
            toDayAsEndDay: false,
            endDay: makeDynamicValue({ value: "not-a-date" }),
         }),
      });
      expect(comp.endDate).toBeNull();
   });
});
