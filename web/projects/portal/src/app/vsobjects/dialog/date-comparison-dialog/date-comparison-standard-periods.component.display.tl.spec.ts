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
 * DateComparisonStandardPeriodsComponent — Pass 3: Display
 *
 * Scope (per prescan): visibleStandardPeriodLevel, toDateLabel, toDateVisible,
 * inclusiveLabel getters; dayOfWeek/monthOfQuarter private helpers.
 *
 * These getters call through to the real (non-mocked) Tool.formatCatalogString and
 * DateTypeFormatter — expected label strings are derived from those same real utilities
 * in each test rather than hand-guessed, since the untranslated "_#(js:...)" catalog keys
 * used in this codebase don't contain the "%s$N" placeholders formatCatalogString actually
 * substitutes, so interpolated values often don't appear in the unlocalized test output.
 */

import { ValueTypes } from "../../model/dynamic-value-model";
import { XConstants } from "../../../common/util/xconstants";
import { Tool } from "../../../../../../shared/util/tool";
import { DateTypeFormatter } from "../../../../../../shared/util/date-type-formatter";
import { DateComparisonUtil } from "./date-comparison-utill";
import {
   createComponent, makeDynamicValue, makeStandardPeriodModel,
} from "./date-comparison-standard-periods.component.test-helpers";

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: visibleStandardPeriodLevel [Risk 1]
// ---------------------------------------------------------------------------

describe("DateComparisonStandardPeriodsComponent — visibleStandardPeriodLevel", () => {
   it("should return the current value when it is one of the known standard levels", () => {
      const { comp } = createComponent({
         model: makeStandardPeriodModel({
            dateLevel: makeDynamicValue({ value: XConstants.WEEK_DATE_GROUP + "", type: ValueTypes.VALUE }),
         }),
      });
      expect(comp.visibleStandardPeriodLevel).toBe(XConstants.WEEK_DATE_GROUP + "");
   });

   it("should fall back to the first standard level for an unrecognized VALUE", () => {
      const { comp } = createComponent({
         model: makeStandardPeriodModel({
            dateLevel: makeDynamicValue({ value: "9999", type: ValueTypes.VALUE }),
         }),
      });
      expect(comp.visibleStandardPeriodLevel).toBe(XConstants.YEAR_DATE_GROUP + "");
   });

   it("should pass a VARIABLE value straight through", () => {
      const { comp } = createComponent({
         model: makeStandardPeriodModel({
            dateLevel: makeDynamicValue({ value: "$(myVar)", type: ValueTypes.VARIABLE }),
         }),
      });
      expect(comp.visibleStandardPeriodLevel).toBe("$(myVar)");
   });

   it("should pass an EXPRESSION value straight through", () => {
      const { comp } = createComponent({
         model: makeStandardPeriodModel({
            dateLevel: makeDynamicValue({ value: "=1+1", type: ValueTypes.EXPRESSION }),
         }),
      });
      expect(comp.visibleStandardPeriodLevel).toBe("=1+1");
   });
});

// ---------------------------------------------------------------------------
// Group 2: toDateVisible [Risk 2]
// ---------------------------------------------------------------------------

describe("DateComparisonStandardPeriodsComponent — toDateVisible", () => {
   it("should be false for the DAY level regardless of the computed label", () => {
      const { comp } = createComponent({
         model: makeStandardPeriodModel({
            dateLevel: makeDynamicValue({ value: XConstants.DAY_DATE_GROUP + "", type: ValueTypes.VALUE }),
         }),
      });
      expect(comp.toDateVisible).toBe(false);
   });

   it("should be true for the YEAR level, which always has a non-null label", () => {
      const { comp } = createComponent({
         model: makeStandardPeriodModel({
            dateLevel: makeDynamicValue({ value: XConstants.YEAR_DATE_GROUP + "", type: ValueTypes.VALUE }),
         }),
      });
      expect(comp.toDateVisible).toBe(true);
   });

   it("should be true for a VARIABLE level (label falls back to the range-end placeholder)", () => {
      const { comp } = createComponent({
         model: makeStandardPeriodModel({
            dateLevel: makeDynamicValue({ value: "$(myVar)", type: ValueTypes.VARIABLE }),
         }),
      });
      expect(comp.toDateVisible).toBe(true);
   });

   // Bug: toDateVisible guards its own DAY_DATE_GROUP shortcut with `!!dateLevel`, implying
   // a null dateLevel is an anticipated state — but the fallback path it takes instead
   // (`!!this.toDateLabel`) calls straight into toDateLabel's unguarded
   // `this.standardPeriodPaneModel.dateLevel.type`, which throws on that same null value.
   it.fails("should not crash when dateLevel is null (currently throws inside toDateLabel)", () => {
      const { comp } = createComponent({
         model: makeStandardPeriodModel({ dateLevel: null as any }),
      });
      expect(() => comp.toDateVisible).not.toThrow();
   });
});

// ---------------------------------------------------------------------------
// Group 3: inclusiveLabel [Risk 2]
// ---------------------------------------------------------------------------

describe("DateComparisonStandardPeriodsComponent — inclusiveLabel", () => {
   it("should use the dynamic-default label for a VARIABLE/EXPRESSION level", () => {
      const { comp } = createComponent({
         model: makeStandardPeriodModel({
            dateLevel: makeDynamicValue({ value: "$(myVar)", type: ValueTypes.VARIABLE }),
         }),
      });
      expect(comp.inclusiveLabel).toBe("_#(js:date.comparison.inclusiveDefault)");
   });

   it.each([
      [XConstants.QUARTER_DATE_GROUP, "_#(js:date.comparison.inclusiveQuarter)"],
      [XConstants.MONTH_DATE_GROUP, "_#(js:date.comparison.inclusiveMonth)"],
      [XConstants.WEEK_DATE_GROUP, "_#(js:date.comparison.inclusiveWeek)"],
      [XConstants.DAY_DATE_GROUP, "_#(js:date.comparison.inclusiveDay)"],
      [XConstants.YEAR_DATE_GROUP, "_#(js:date.comparison.inclusiveYear)"],
   ])("should label date level %s as %s", (level, expected) => {
      const { comp } = createComponent({
         model: makeStandardPeriodModel({
            dateLevel: makeDynamicValue({ value: level + "", type: ValueTypes.VALUE }),
         }),
      });
      expect(comp.inclusiveLabel).toBe(expected);
   });
});

// ---------------------------------------------------------------------------
// Group 4: toDateLabel [Risk 2]
// ---------------------------------------------------------------------------

describe("DateComparisonStandardPeriodsComponent — toDateLabel", () => {
   it("should use the range-end placeholder for a VARIABLE/EXPRESSION level", () => {
      const { comp } = createComponent({
         model: makeStandardPeriodModel({
            dateLevel: makeDynamicValue({ value: "$(myVar)", type: ValueTypes.VARIABLE }),
         }),
      });
      expect(comp.toDateLabel).toBe("_#(js:date.comparison.toRangeEnd)");
   });

   it("should be null for the DAY level", () => {
      const { comp } = createComponent({
         model: makeStandardPeriodModel({
            dateLevel: makeDynamicValue({ value: XConstants.DAY_DATE_GROUP + "", type: ValueTypes.VALUE }),
         }),
      });
      expect(comp.toDateLabel).toBeNull();
   });

   it("should use the quarter-default label when there is no end date", () => {
      const { comp } = createComponent({
         model: makeStandardPeriodModel({
            dateLevel: makeDynamicValue({ value: XConstants.QUARTER_DATE_GROUP + "", type: ValueTypes.VALUE }),
            toDayAsEndDay: false, endDay: null,
         }),
      });
      expect(comp.toDateLabel).toBe("_#(js:date.comparison.toDateQuarter.default)");
   });

   it("should build the quarter label from the real end date when one is set", () => {
      const { comp } = createComponent({
         model: makeStandardPeriodModel({
            dateLevel: makeDynamicValue({ value: XConstants.QUARTER_DATE_GROUP + "", type: ValueTypes.VALUE }),
            endDay: makeDynamicValue({ value: "2024-03-15" }),
         }),
      });

      const expected = Tool.formatCatalogString("_#(js:date.comparison.toDateQuarter)",
         [DateComparisonUtil.monthOfQuarter(comp.endDate), DateTypeFormatter.format(comp.endDate, "Do"), ""]);

      expect(comp.toDateLabel).toBe(expected);
   });

   it("should use the month-default label when there is no end date", () => {
      const { comp } = createComponent({
         model: makeStandardPeriodModel({
            dateLevel: makeDynamicValue({ value: XConstants.MONTH_DATE_GROUP + "", type: ValueTypes.VALUE }),
            toDayAsEndDay: false, endDay: null,
         }),
      });
      expect(comp.toDateLabel).toBe("_#(js:date.comparison.toDateMonth.default)");
   });

   it("should build the month label from the real end date when one is set", () => {
      const { comp } = createComponent({
         model: makeStandardPeriodModel({
            dateLevel: makeDynamicValue({ value: XConstants.MONTH_DATE_GROUP + "", type: ValueTypes.VALUE }),
            endDay: makeDynamicValue({ value: "2024-03-15" }),
         }),
      });

      const expected = Tool.formatCatalogString("_#(js:date.comparison.toDateMonth)",
         [DateTypeFormatter.format(comp.endDate, "Do"), ""]);

      expect(comp.toDateLabel).toBe(expected);
   });

   it("should use the week-default label when there is no end date", () => {
      const { comp } = createComponent({
         model: makeStandardPeriodModel({
            dateLevel: makeDynamicValue({ value: XConstants.WEEK_DATE_GROUP + "", type: ValueTypes.VALUE }),
            toDayAsEndDay: false, endDay: null,
         }),
      });
      expect(comp.toDateLabel).toBe("_#(js:date.comparison.toDateWeek.default)");
   });

   it("should build the week label with the day-of-week name from the real end date", () => {
      // 2024-03-15 is a Friday.
      const { comp } = createComponent({
         model: makeStandardPeriodModel({
            dateLevel: makeDynamicValue({ value: XConstants.WEEK_DATE_GROUP + "", type: ValueTypes.VALUE }),
            endDay: makeDynamicValue({ value: "2024-03-15" }),
         }),
      });

      const expected = Tool.formatCatalogString("_#(js:date.comparison.toDateWeek)", ["_#(js:Friday)"]);

      expect(comp.toDateLabel).toBe(expected);
   });

   it("should fall back to the range-end placeholder text when the year end date can't be formatted", () => {
      const { comp } = createComponent({
         model: makeStandardPeriodModel({
            dateLevel: makeDynamicValue({ value: XConstants.YEAR_DATE_GROUP + "", type: ValueTypes.VALUE }),
            toDayAsEndDay: false, endDay: null,
         }),
      });

      const expected = Tool.formatCatalogString("_#(js:date.comparison.toDateYear)",
         ["_#(js:date.comparison.range.endDate)", ""]);

      expect(comp.toDateLabel).toBe(expected);
   });

   it("should build the year label from the real end date when one is set", () => {
      const { comp } = createComponent({
         model: makeStandardPeriodModel({
            dateLevel: makeDynamicValue({ value: XConstants.YEAR_DATE_GROUP + "", type: ValueTypes.VALUE }),
            endDay: makeDynamicValue({ value: "2024-03-15" }),
         }),
      });

      const expected = Tool.formatCatalogString("_#(js:date.comparison.toDateYear)",
         [DateTypeFormatter.format(comp.endDate, "MMM DD", false), ""]);

      expect(comp.toDateLabel).toBe(expected);
   });

   it("should append the weekly day-of-week suffix when weekly is enabled", () => {
      const { comp } = createComponent({
         model: makeStandardPeriodModel({
            dateLevel: makeDynamicValue({ value: XConstants.YEAR_DATE_GROUP + "", type: ValueTypes.VALUE }),
            endDay: makeDynamicValue({ value: "2024-03-15" }),
         }),
      });
      comp.weekly = true;

      const dayOfWeek = "&nbsp;<b>(" + DateTypeFormatter.format(comp.endDate, "ddd", false) + ")</b>&nbsp;";
      const expected = Tool.formatCatalogString("_#(js:date.comparison.toDateYear)",
         [DateTypeFormatter.format(comp.endDate, "MMM DD", false), dayOfWeek]);

      expect(comp.toDateLabel).toBe(expected);
   });
});
