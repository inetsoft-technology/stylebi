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
 * DateComparisonIntervalPaneComponent — Pass 1: Interaction
 *
 * Risk-first coverage:
 *   Group 1  [Risk 2] — ngOnInit: firstDayOfWeekService subscription
 *   Group 2  [Risk 1] — updateLevelType / updateGranularityType / updateContextLevelType: symmetric type setters
 *   Group 3  [Risk 2] — updateVisibleLevel: value assignment, variable detection, granularity-reset side effect
 *   Group 4  [Risk 1] — updateVisibleGranularity: value assignment + variable detection
 *   Group 5  [Risk 1] — updateContextLevel: value assignment + variable detection
 *   Group 6  [Risk 2] — verifyEndDate: date-format validation delegate
 *   Group 7  [Risk 3] — isValidInterval: multi-guard validity gate that drives the error banner
 *   Group 8  [Risk 1] — toDateIsValue: end-date value-type check
 *   Group 9  [Risk 2] — isEndDateDisable getter: OR of endDayAsToDate / disable
 *   Group 10 [Risk 2] — showEndDate: dispatch on isCustomPeriod / level type / level value
 *   Group 11 [Risk 2] — showInclusive: dispatch on TO_DATE bit / level type
 *
 * Confirmed bugs (it.fails): none
 *
 * Out of scope this pass: visibleIntervalLevel, visibleGranularity, contextLevelValue,
 * getIntervalLevels, getGranularities, granularitiesAllIntervalVisible, getContextLevels,
 * intervalLevelConvertToGroupLevel, toDateLabel, toDate, getToDateLabel
 *   — covered in date-comparison-interval-pane.component.display.tl.spec.ts (Pass 3)
 */

import { of } from "rxjs";
import { IntervalLevel } from "../../model/interval-pane-model";
import { ValueTypes } from "../../model/dynamic-value-model";
import { XConstants } from "../../../common/util/xconstants";
import { ComboMode } from "../../../widget/dynamic-combo-box/dynamic-combo-box-model";
import { makeDynamicValue, makeIntervalPaneModel, renderComponent } from "./date-comparison-interval-pane.component.test-helpers";

afterEach(() => {
   vi.restoreAllMocks();
});

// ---------------------------------------------------------------------------
// Group 1: ngOnInit — firstDayOfWeekService subscription
// ---------------------------------------------------------------------------

describe("DateComparisonIntervalPaneComponent — ngOnInit", () => {
   // 🔁 Regression-sensitive: subscription silently dropped on a refactor would leave
   // firstDayOfWeek permanently undefined with no visible symptom in the UI.
   // Bypass: firstDayOfWeek is a private field with no public getter, so it is read via
   // an `as any` cast — this test targets the ngOnInit subscription assignment only.
   it("should populate firstDayOfWeek from the firstDayOfWeekService subscription", async () => {
      const firstDayOfWeekService = { getFirstDay: vi.fn(() => of({ javaFirstDay: 7, isoFirstDay: 0 })) };
      const { comp } = await renderComponent({ firstDayOfWeekService });

      expect(firstDayOfWeekService.getFirstDay).toHaveBeenCalled();
      expect((comp as any).firstDayOfWeek).toBe(7);
   });
});

// ---------------------------------------------------------------------------
// Group 2: updateLevelType / updateGranularityType / updateContextLevelType
// ---------------------------------------------------------------------------

describe("DateComparisonIntervalPaneComponent — type setters", () => {
   // Symmetric setters: each mutates a different DynamicValueModel.type field via the
   // same dateComparisonService.getDateComparisonValueTypeStr() conversion.
   it("updateLevelType should set intervalPaneModel.level.type from the ComboMode value", async () => {
      const { comp } = await renderComponent();

      comp.updateLevelType(ComboMode.VARIABLE);

      expect(comp.intervalPaneModel.level.type).toBe(ValueTypes.VARIABLE);
   });

   it("updateGranularityType should set intervalPaneModel.granularity.type from the ComboMode value", async () => {
      const { comp } = await renderComponent();

      comp.updateGranularityType(ComboMode.EXPRESSION);

      expect(comp.intervalPaneModel.granularity.type).toBe(ValueTypes.EXPRESSION);
   });

   it("updateContextLevelType should set intervalPaneModel.contextLevel.type from the ComboMode value", async () => {
      const model = makeIntervalPaneModel({ contextLevel: makeDynamicValue(XConstants.YEAR_DATE_GROUP, ValueTypes.VARIABLE) });
      const { comp } = await renderComponent({ intervalPaneModel: model });

      comp.updateContextLevelType(ComboMode.VALUE);

      expect(comp.intervalPaneModel.contextLevel.type).toBe(ValueTypes.VALUE);
   });
});

// ---------------------------------------------------------------------------
// Group 3: updateVisibleLevel — value assignment + variable detection + granularity reset
// ---------------------------------------------------------------------------

describe("DateComparisonIntervalPaneComponent — updateVisibleLevel", () => {
   // 🔁 Regression-sensitive: the granularity reset reads getGranularities() AFTER the new
   // level value/type is written, so it must observe the update, not the stale value.
   it("should assign the new level value and reset granularity to the first option valid for that level", async () => {
      const model = makeIntervalPaneModel({
         level: makeDynamicValue(IntervalLevel.YEAR_TO_DATE + ""),
         granularity: makeDynamicValue(IntervalLevel.MONTH + ""),
      });
      const { comp } = await renderComponent({ intervalPaneModel: model, variableValues: ["MyVar"] });

      comp.updateVisibleLevel(IntervalLevel.SAME_DAY + "");

      expect(comp.intervalPaneModel.level.type).toBe(ValueTypes.VALUE);
      expect(comp.intervalPaneModel.level.value).toBe(IntervalLevel.SAME_DAY + "");
      // SAME_DAY only allows the DAY granularity.
      expect(comp.intervalPaneModel.granularity.value).toBe(IntervalLevel.DAY + "");
   });

   it("should switch level.type to VARIABLE when the new value matches a bound variable, and still reset granularity", async () => {
      const model = makeIntervalPaneModel({
         level: makeDynamicValue(IntervalLevel.YEAR_TO_DATE + ""),
         granularity: makeDynamicValue(IntervalLevel.MONTH + ""),
      });
      const { comp } = await renderComponent({ intervalPaneModel: model, variableValues: ["MyVar"] });

      comp.updateVisibleLevel("MyVar");

      expect(comp.intervalPaneModel.level.type).toBe(ValueTypes.VARIABLE);
      expect(comp.intervalPaneModel.level.value).toBe("MyVar");
      // Once level.type != VALUE, getGranularities() returns the full unfiltered list;
      // granularities[0] is YEAR.
      expect(comp.intervalPaneModel.granularity.value).toBe(IntervalLevel.YEAR + "");
   });
});

// ---------------------------------------------------------------------------
// Group 4: updateVisibleGranularity — value assignment + variable detection
// ---------------------------------------------------------------------------

describe("DateComparisonIntervalPaneComponent — updateVisibleGranularity", () => {
   it("should assign the new granularity value without touching its type when not a variable", async () => {
      const model = makeIntervalPaneModel({ granularity: makeDynamicValue(IntervalLevel.MONTH + "") });
      const { comp } = await renderComponent({ intervalPaneModel: model, variableValues: ["GVar"] });

      comp.updateVisibleGranularity(IntervalLevel.WEEK + "");

      expect(comp.intervalPaneModel.granularity.type).toBe(ValueTypes.VALUE);
      expect(comp.intervalPaneModel.granularity.value).toBe(IntervalLevel.WEEK + "");
   });

   it("should switch granularity.type to VARIABLE when the new value matches a bound variable", async () => {
      const model = makeIntervalPaneModel({ granularity: makeDynamicValue(IntervalLevel.MONTH + "") });
      const { comp } = await renderComponent({ intervalPaneModel: model, variableValues: ["GVar"] });

      comp.updateVisibleGranularity("GVar");

      expect(comp.intervalPaneModel.granularity.type).toBe(ValueTypes.VARIABLE);
      expect(comp.intervalPaneModel.granularity.value).toBe("GVar");
   });
});

// ---------------------------------------------------------------------------
// Group 5: updateContextLevel — value assignment + variable detection
// ---------------------------------------------------------------------------

describe("DateComparisonIntervalPaneComponent — updateContextLevel", () => {
   it("should assign the new context level value without touching its type when not a variable", async () => {
      const model = makeIntervalPaneModel({ contextLevel: makeDynamicValue(XConstants.YEAR_DATE_GROUP) });
      const { comp } = await renderComponent({ intervalPaneModel: model, variableValues: ["CVar"] });

      comp.updateContextLevel(XConstants.MONTH_DATE_GROUP);

      expect(comp.intervalPaneModel.contextLevel.type).toBe(ValueTypes.VALUE);
      expect(comp.intervalPaneModel.contextLevel.value).toBe(XConstants.MONTH_DATE_GROUP);
   });

   it("should switch contextLevel.type to VARIABLE when the new value matches a bound variable", async () => {
      const model = makeIntervalPaneModel({ contextLevel: makeDynamicValue(XConstants.YEAR_DATE_GROUP) });
      const { comp } = await renderComponent({ intervalPaneModel: model, variableValues: ["CVar"] });

      comp.updateContextLevel("CVar");

      expect(comp.intervalPaneModel.contextLevel.type).toBe(ValueTypes.VARIABLE);
      expect(comp.intervalPaneModel.contextLevel.value).toBe("CVar");
   });
});

// ---------------------------------------------------------------------------
// Group 6: verifyEndDate — date-format validation delegate
// ---------------------------------------------------------------------------

describe("DateComparisonIntervalPaneComponent — verifyEndDate", () => {
   it("should return true for a well-formed ISO date string", async () => {
      const model = makeIntervalPaneModel({ intervalEndDate: makeDynamicValue("2024-01-15") });
      const { comp } = await renderComponent({ intervalPaneModel: model });

      expect(comp.verifyEndDate()).toBe(true);
   });

   it("should return false for a malformed date string", async () => {
      const model = makeIntervalPaneModel({ intervalEndDate: makeDynamicValue("not-a-date") });
      const { comp } = await renderComponent({ intervalPaneModel: model });

      expect(comp.verifyEndDate()).toBe(false);
   });

   // Boundary: a dynamic expression bypasses the date-format regex entirely (Tool.isDynamic).
   it("should return true for a dynamic expression value even though it is not a date string", async () => {
      const model = makeIntervalPaneModel({ intervalEndDate: makeDynamicValue("$(myVar)") });
      const { comp } = await renderComponent({ intervalPaneModel: model });

      expect(comp.verifyEndDate()).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 7: isValidInterval — multi-guard validity gate
// ---------------------------------------------------------------------------

describe("DateComparisonIntervalPaneComponent — isValidInterval", () => {
   // Risk Point: three independent guards OR together; each must be exercised in isolation
   // so a guard collapsing to AND semantics would be caught.
   it("should be valid when showEndDate() is false, regardless of the end-date content", async () => {
      const model = makeIntervalPaneModel({
         level: makeDynamicValue("0"), // "All" — showEndDate() is false
         endDayAsToDate: false,
         intervalEndDate: makeDynamicValue("not-a-date"),
      });
      const { comp } = await renderComponent({ intervalPaneModel: model, isCustomPeriod: false });

      expect(comp.showEndDate()).toBe(false);
      expect(comp.isValidInterval()).toBe(true);
   });

   it("should be valid when endDayAsToDate is true, regardless of the end-date content", async () => {
      const model = makeIntervalPaneModel({
         level: makeDynamicValue(IntervalLevel.YEAR_TO_DATE + ""),
         endDayAsToDate: true,
         intervalEndDate: makeDynamicValue("not-a-date"),
      });
      const { comp } = await renderComponent({ intervalPaneModel: model });

      expect(comp.showEndDate()).toBe(true);
      expect(comp.isValidInterval()).toBe(true);
   });

   it("should be valid when the end date is present and passes verifyEndDate", async () => {
      const model = makeIntervalPaneModel({
         level: makeDynamicValue(IntervalLevel.YEAR_TO_DATE + ""),
         endDayAsToDate: false,
         intervalEndDate: makeDynamicValue("2024-01-15"),
      });
      const { comp } = await renderComponent({ intervalPaneModel: model });

      expect(comp.isValidInterval()).toBe(true);
   });

   it("should be invalid when the end date is present but fails verifyEndDate", async () => {
      const model = makeIntervalPaneModel({
         level: makeDynamicValue(IntervalLevel.YEAR_TO_DATE + ""),
         endDayAsToDate: false,
         intervalEndDate: makeDynamicValue("not-a-date"),
      });
      const { comp } = await renderComponent({ intervalPaneModel: model });

      expect(comp.isValidInterval()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 8: toDateIsValue
// ---------------------------------------------------------------------------

describe("DateComparisonIntervalPaneComponent — toDateIsValue", () => {
   it("should return true when intervalEndDate.type is VALUE", async () => {
      const model = makeIntervalPaneModel({ intervalEndDate: makeDynamicValue("2024-01-15", ValueTypes.VALUE) });
      const { comp } = await renderComponent({ intervalPaneModel: model });

      expect(comp.toDateIsValue()).toBe(true);
   });

   it("should return false when intervalEndDate.type is VARIABLE", async () => {
      const model = makeIntervalPaneModel({ intervalEndDate: makeDynamicValue("MyVar", ValueTypes.VARIABLE) });
      const { comp } = await renderComponent({ intervalPaneModel: model });

      expect(comp.toDateIsValue()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 9: isEndDateDisable getter
// ---------------------------------------------------------------------------

describe("DateComparisonIntervalPaneComponent — isEndDateDisable", () => {
   it("should be true when endDayAsToDate is true even if disable is false", async () => {
      const model = makeIntervalPaneModel({ endDayAsToDate: true });
      const { comp } = await renderComponent({ intervalPaneModel: model, disable: false });

      expect(comp.isEndDateDisable).toBe(true);
   });

   it("should be true when disable is true even if endDayAsToDate is false", async () => {
      const model = makeIntervalPaneModel({ endDayAsToDate: false });
      const { comp } = await renderComponent({ intervalPaneModel: model, disable: true });

      expect(comp.isEndDateDisable).toBe(true);
   });

   it("should be false when both endDayAsToDate and disable are false", async () => {
      const model = makeIntervalPaneModel({ endDayAsToDate: false });
      const { comp } = await renderComponent({ intervalPaneModel: model, disable: false });

      expect(comp.isEndDateDisable).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 10: showEndDate
// ---------------------------------------------------------------------------

describe("DateComparisonIntervalPaneComponent — showEndDate", () => {
   it("should be false whenever isCustomPeriod is true, even for a level that would otherwise show it", async () => {
      const model = makeIntervalPaneModel({ level: makeDynamicValue(IntervalLevel.YEAR_TO_DATE + "") });
      const { comp } = await renderComponent({ intervalPaneModel: model, isCustomPeriod: true });

      expect(comp.showEndDate()).toBe(false);
   });

   it("should be false for a VALUE-type level at or below the TO_DATE threshold (e.g. \"All\")", async () => {
      const model = makeIntervalPaneModel({ level: makeDynamicValue("0") });
      const { comp } = await renderComponent({ intervalPaneModel: model, isCustomPeriod: false });

      expect(comp.showEndDate()).toBe(false);
   });

   it("should be true for a VALUE-type level above the TO_DATE threshold", async () => {
      const model = makeIntervalPaneModel({ level: makeDynamicValue(IntervalLevel.YEAR_TO_DATE + "") });
      const { comp } = await renderComponent({ intervalPaneModel: model, isCustomPeriod: false });

      expect(comp.showEndDate()).toBe(true);
   });

   it("should be true for a non-VALUE level type regardless of its value", async () => {
      const model = makeIntervalPaneModel({ level: makeDynamicValue("MyVar", ValueTypes.VARIABLE) });
      const { comp } = await renderComponent({ intervalPaneModel: model, isCustomPeriod: false });

      expect(comp.showEndDate()).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 11: showInclusive
// ---------------------------------------------------------------------------

describe("DateComparisonIntervalPaneComponent — showInclusive", () => {
   it("should be true when the level value has the TO_DATE bit set", async () => {
      const model = makeIntervalPaneModel({ level: makeDynamicValue(IntervalLevel.YEAR_TO_DATE + "") });
      const { comp } = await renderComponent({ intervalPaneModel: model });

      expect(comp.showInclusive()).toBe(true);
   });

   it("should be false when the level value lacks the TO_DATE bit and type is VALUE", async () => {
      const model = makeIntervalPaneModel({ level: makeDynamicValue(IntervalLevel.YEAR + "") });
      const { comp } = await renderComponent({ intervalPaneModel: model });

      expect(comp.showInclusive()).toBe(false);
   });

   it("should be true for a non-VALUE level type even without the TO_DATE bit", async () => {
      const model = makeIntervalPaneModel({ level: makeDynamicValue("MyVar", ValueTypes.VARIABLE) });
      const { comp } = await renderComponent({ intervalPaneModel: model });

      expect(comp.showInclusive()).toBe(true);
   });
});
