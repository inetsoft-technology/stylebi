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
 * DateComparisonIntervalPaneComponent — Pass 3: Display
 *
 * Risk-first coverage:
 *   Group 1  [Risk 2] — visibleIntervalLevel: VALUE match / VALUE self-heal (mutates model) / VARIABLE passthrough
 *   Group 2  [Risk 2] — visibleGranularity: VALUE match / VALUE fallback (does NOT mutate model) / VARIABLE passthrough
 *   Group 3  [Risk 2] — contextLevelValue: VALUE match / VALUE self-heal (mutates model) / VARIABLE passthrough
 *   Group 4  [Risk 3] — getIntervalLevels: 7-branch dispatch (isCustomPeriod / type!=VALUE / YEAR / QUARTER / MONTH / WEEK / DAY / fallback)
 *   Group 5  [Risk 3] — getGranularities: early-return branches + 6-case level switch
 *   Group 6  [Risk 2] — granularitiesAllIntervalVisible: guard short-circuits + 5-case switch, bidirectional per case
 *   Group 7  [Risk 3] — getContextLevels: 4-branch dispatch, including strict(>) vs inclusive(>=) isSameLevel nuance
 *   Group 8  [Risk 2] — intervalLevelConvertToGroupLevel: 5-branch bit-mask dispatch + fallback -1
 *   Group 9  [Risk 1] — toDateLabel: outer/inner switch, all label combinations
 *   Group 10 [Risk 2] — toDate: end-date source selection, null/type guards, format parsing
 *   Group 11 [Risk 1] — getToDateLabel: dead code, always returns null
 *
 * Fixed bugs (previously it.fails, now passing):
 *   Bug #75653 — contextLevelValue (Group 3): used to throw a TypeError reading
 *   levels[0].value when getContextLevels() returns an empty array. Reachable via a real input
 *   combination (standardPeriodLevel=DAY_DATE_GROUP with a VALUE-type interval level), and
 *   crashed ngOnInit, breaking the whole component rather than degrading gracefully. Fixed by
 *   guarding both levels[0] accesses and falling back to the raw contextLevel value / null.
 *
 * Suspected bugs (header only):
 *   Suspicion A — visibleGranularity (Group 2): unlike visibleIntervalLevel and contextLevelValue, this getter
 *   does NOT write its resolved fallback value back into intervalPaneModel.granularity.value. If the model is
 *   submitted without the user touching the granularity dropdown, a stale/invalid granularity value could reach
 *   the server even though the UI displayed the corrected one. Not converted to it.fails: unclear whether this
 *   is intentional (e.g. getGranularities() may always be recomputed server-side from the healed level/context).
 *
 * Out of scope this pass: ngOnInit, updateLevelType, updateGranularityType, updateContextLevelType,
 * updateVisibleLevel, updateVisibleGranularity, updateContextLevel, isValidInterval, verifyEndDate,
 * toDateIsValue, isEndDateDisable, showEndDate, showInclusive
 *   — covered in date-comparison-interval-pane.component.interaction.tl.spec.ts (Pass 1)
 */

import { IntervalLevel } from "../../model/interval-pane-model";
import { ValueTypes } from "../../model/dynamic-value-model";
import { XConstants } from "../../../common/util/xconstants";
import { makeDynamicValue, makeIntervalPaneModel, renderComponent } from "./date-comparison-interval-pane.component.test-helpers";

afterEach(() => {
   vi.restoreAllMocks();

   if(vi.isFakeTimers()) {
      vi.useRealTimers();
   }
});

// ---------------------------------------------------------------------------
// Group 1: visibleIntervalLevel getter
// ---------------------------------------------------------------------------

describe("DateComparisonIntervalPaneComponent — visibleIntervalLevel", () => {
   it("should return the model value unchanged when it is a valid VALUE-type level", async () => {
      const model = makeIntervalPaneModel({ level: makeDynamicValue(IntervalLevel.YEAR_TO_DATE + "") });
      const { comp } = await renderComponent({ intervalPaneModel: model });

      expect(comp.visibleIntervalLevel).toBe(IntervalLevel.YEAR_TO_DATE + "");
      expect(comp.intervalPaneModel.level.value).toBe(IntervalLevel.YEAR_TO_DATE + "");
   });

   // 🔁 Regression-sensitive: self-heal writes back into the model, not just the display value.
   it("should self-heal to the first available level and mutate the model when the VALUE-type value is filtered out", async () => {
      // isCustomPeriod=true excludes SAME_DATE-masked levels; SAME_DAY is one of them.
      const model = makeIntervalPaneModel({ level: makeDynamicValue(IntervalLevel.SAME_DAY + "") });
      const { comp } = await renderComponent({ intervalPaneModel: model, isCustomPeriod: true });

      expect(comp.visibleIntervalLevel).toBe("0");
      expect(comp.intervalPaneModel.level.value).toBe("0");
   });

   it("should return the raw value for a VARIABLE-type level without validating it against the level list", async () => {
      const model = makeIntervalPaneModel({ level: makeDynamicValue("MyVar", ValueTypes.VARIABLE) });
      const { comp } = await renderComponent({ intervalPaneModel: model });

      expect(comp.visibleIntervalLevel).toBe("MyVar");
   });
});

// ---------------------------------------------------------------------------
// Group 2: visibleGranularity getter
// ---------------------------------------------------------------------------

describe("DateComparisonIntervalPaneComponent — visibleGranularity", () => {
   it("should return the model value unchanged when it is a valid VALUE-type granularity", async () => {
      const model = makeIntervalPaneModel({
         level: makeDynamicValue(IntervalLevel.YEAR_TO_DATE + ""),
         granularity: makeDynamicValue(IntervalLevel.WEEK + ""),
      });
      const { comp } = await renderComponent({ intervalPaneModel: model });

      expect(comp.visibleGranularity).toBe(IntervalLevel.WEEK + "");
   });

   it("should fall back to the first available granularity WITHOUT mutating the model when the value is filtered out", async () => {
      // level=SAME_DAY only allows the DAY granularity, so a stale MONTH value is filtered out.
      const model = makeIntervalPaneModel({
         level: makeDynamicValue(IntervalLevel.SAME_DAY + ""),
         granularity: makeDynamicValue(IntervalLevel.MONTH + ""),
      });
      const { comp } = await renderComponent({ intervalPaneModel: model });

      expect(comp.visibleGranularity).toBe(IntervalLevel.DAY + "");
      // Suspicion A: the underlying model value is left stale, unlike visibleIntervalLevel/contextLevelValue.
      expect(comp.intervalPaneModel.granularity.value).toBe(IntervalLevel.MONTH + "");
   });

   it("should return the raw value for a VARIABLE-type granularity without validating it against the list", async () => {
      const model = makeIntervalPaneModel({ granularity: makeDynamicValue("GVar", ValueTypes.VARIABLE) });
      const { comp } = await renderComponent({ intervalPaneModel: model });

      expect(comp.visibleGranularity).toBe("GVar");
   });
});

// ---------------------------------------------------------------------------
// Group 3: contextLevelValue getter
// ---------------------------------------------------------------------------

describe("DateComparisonIntervalPaneComponent — contextLevelValue", () => {
   it("should return the model value unchanged when it is a valid VALUE-type context level", async () => {
      const model = makeIntervalPaneModel({
         level: makeDynamicValue(IntervalLevel.YEAR_TO_DATE + ""),
         contextLevel: makeDynamicValue(XConstants.YEAR_DATE_GROUP),
      });
      const { comp } = await renderComponent({ intervalPaneModel: model });

      expect(comp.contextLevelValue).toBe(XConstants.YEAR_DATE_GROUP);
   });

   // 🔁 Regression-sensitive: self-heal writes back into the model (mirrors visibleIntervalLevel,
   // unlike visibleGranularity — see Suspicion A).
   it("should self-heal to the first available context level and mutate the model when the value is filtered out", async () => {
      // standardPeriodLevel=MONTH_DATE_GROUP caps contextLevels to <= MONTH; YEAR is filtered out.
      const model = makeIntervalPaneModel({
         level: makeDynamicValue(IntervalLevel.MONTH_TO_DATE + ""),
         contextLevel: makeDynamicValue(XConstants.YEAR_DATE_GROUP),
      });
      const { comp } = await renderComponent({
         intervalPaneModel: model,
         standardPeriodLevel: makeDynamicValue(XConstants.MONTH_DATE_GROUP),
      });

      expect(comp.contextLevelValue).toBe(XConstants.MONTH_DATE_GROUP);
      expect(comp.intervalPaneModel.contextLevel.value).toBe(XConstants.MONTH_DATE_GROUP);
   });

   it("should return the raw value for a VARIABLE-type context level without validating it against the list", async () => {
      const model = makeIntervalPaneModel({ contextLevel: makeDynamicValue("CVar", ValueTypes.VARIABLE) });
      const { comp } = await renderComponent({ intervalPaneModel: model });

      expect(comp.contextLevelValue).toBe("CVar");
   });

   // Bug #75653 (fixed): getContextLevels() applies a ceiling of
   // standardPeriodLevel.value, but contextLevels only contains {YEAR:5, QUARTER:4, MONTH:3,
   // WEEK:2} (no DAY entry). Whenever standardPeriodLevel is DAY_DATE_GROUP(1) (a real,
   // selectable "Period" option) and the interval level is a plain VALUE type, the ceiling
   // filter empties the list. contextLevelValue used to unconditionally read levels[0].value
   // with no empty-array guard, throwing inside ngOnInit itself. The fix falls back to the
   // raw (unmatched) contextLevel value instead of dereferencing the empty list.
   it("should not throw when getContextLevels() returns an empty array (standardPeriodLevel=DAY_DATE_GROUP) (Bug #75653)", async () => {
      const model = makeIntervalPaneModel({ level: makeDynamicValue(IntervalLevel.YEAR_TO_DATE + "") });

      const { comp } = await renderComponent({
         intervalPaneModel: model,
         standardPeriodLevel: makeDynamicValue(XConstants.DAY_DATE_GROUP),
      });

      // No entry in contextLevels matches or survives the (now-empty) ceiling filter, so the
      // getter falls back to the model's existing (unmatched) contextLevel value rather than
      // dereferencing levels[0].
      expect(comp.contextLevelValue).toBe(XConstants.YEAR_DATE_GROUP);
   });
});

// ---------------------------------------------------------------------------
// Group 4: getIntervalLevels — 7-branch dispatch
// ---------------------------------------------------------------------------

describe("DateComparisonIntervalPaneComponent — getIntervalLevels", () => {
   it("should exclude all SAME_DATE-masked levels when isCustomPeriod is true", async () => {
      const { comp } = await renderComponent({ isCustomPeriod: true });

      const values = comp.getIntervalLevels().map(l => l.value);

      expect(values).toHaveLength(5);
      expect(values).not.toContain(IntervalLevel.SAME_DAY + "");
      expect(values).not.toContain(IntervalLevel.SAME_QUARTER + "");
   });

   it("should return the full level list when standardPeriodLevel.type is not VALUE", async () => {
      const { comp } = await renderComponent({
         isCustomPeriod: false,
         standardPeriodLevel: makeDynamicValue("Var", ValueTypes.VARIABLE),
      });

      expect(comp.getIntervalLevels()).toHaveLength(9);
   });

   it("should return the full level list when standardPeriodLevel.value is YEAR_DATE_GROUP", async () => {
      const { comp } = await renderComponent({
         isCustomPeriod: false,
         standardPeriodLevel: makeDynamicValue(XConstants.YEAR_DATE_GROUP),
      });

      expect(comp.getIntervalLevels()).toHaveLength(9);
   });

   it("should exclude YEAR-masked levels and SAME_QUARTER when standardPeriodLevel.value is QUARTER_DATE_GROUP", async () => {
      const { comp } = await renderComponent({
         isCustomPeriod: false,
         standardPeriodLevel: makeDynamicValue(XConstants.QUARTER_DATE_GROUP),
      });

      const values = comp.getIntervalLevels().map(l => l.value);

      expect(values).toHaveLength(7);
      expect(values).not.toContain(IntervalLevel.YEAR_TO_DATE + "");
      expect(values).not.toContain(IntervalLevel.SAME_QUARTER + "");
   });

   it("should exclude YEAR/QUARTER-masked levels and SAME_MONTH when standardPeriodLevel.value is MONTH_DATE_GROUP", async () => {
      const { comp } = await renderComponent({
         isCustomPeriod: false,
         standardPeriodLevel: makeDynamicValue(XConstants.MONTH_DATE_GROUP),
      });

      const values = comp.getIntervalLevels().map(l => l.value);

      expect(values).toHaveLength(5);
      expect(values).not.toContain(IntervalLevel.QUARTER_TO_DATE + "");
      expect(values).not.toContain(IntervalLevel.SAME_MONTH + "");
   });

   it("should exclude YEAR/QUARTER/MONTH-masked levels and SAME_WEEK when standardPeriodLevel.value is WEEK_DATE_GROUP", async () => {
      const { comp } = await renderComponent({
         isCustomPeriod: false,
         standardPeriodLevel: makeDynamicValue(XConstants.WEEK_DATE_GROUP),
      });

      const values = comp.getIntervalLevels().map(l => l.value);

      expect(values).toHaveLength(3);
      expect(values).toEqual(["0", IntervalLevel.WEEK_TO_DATE + "", IntervalLevel.SAME_DAY + ""]);
   });

   // standardPeriodLevel is mutated AFTER the initial render (rather than passed as an @Input)
   // because DAY_DATE_GROUP/NONE_DATE_GROUP make getContextLevels() return an empty array for
   // the default level — ngOnInit's contextLevelValue self-heal would crash on levels[0] before
   // the test ever reaches getIntervalLevels(). See the confirmed bug in the contextLevelValue
   // group below; this is unrelated collateral, not the behavior under test here.
   it("should reduce to only the ALL level when standardPeriodLevel.value is DAY_DATE_GROUP", async () => {
      const { comp } = await renderComponent({ isCustomPeriod: false });
      comp.standardPeriodLevel = makeDynamicValue(XConstants.DAY_DATE_GROUP);

      expect(comp.getIntervalLevels().map(l => l.value)).toEqual(["0"]);
   });

   it("should fall back to the full level list when standardPeriodLevel.value matches no named group", async () => {
      const { comp } = await renderComponent({ isCustomPeriod: false });
      comp.standardPeriodLevel = makeDynamicValue(XConstants.NONE_DATE_GROUP);

      expect(comp.getIntervalLevels()).toHaveLength(9);
   });
});

// ---------------------------------------------------------------------------
// Group 5: getGranularities — early-return branches + level switch
// ---------------------------------------------------------------------------

describe("DateComparisonIntervalPaneComponent — getGranularities", () => {
   it("should prepend an ALL option when isCustomPeriod is true, regardless of level", async () => {
      const model = makeIntervalPaneModel({ level: makeDynamicValue(IntervalLevel.SAME_DAY + "") });
      const { comp } = await renderComponent({ intervalPaneModel: model, isCustomPeriod: true });

      const values = comp.getGranularities().map(g => g.value);

      expect(values).toEqual(["0", IntervalLevel.YEAR + "", IntervalLevel.QUARTER + "",
         IntervalLevel.MONTH + "", IntervalLevel.WEEK + "", IntervalLevel.DAY + ""]);
   });

   it("should return the full granularity list when level.type is not VALUE", async () => {
      const model = makeIntervalPaneModel({ level: makeDynamicValue("MyVar", ValueTypes.VARIABLE) });
      const { comp } = await renderComponent({ intervalPaneModel: model, isCustomPeriod: false });

      expect(comp.getGranularities()).toHaveLength(5);
   });

   it("should filter via granularitiesAllIntervalVisible when level is \"0\" (All)", async () => {
      const model = makeIntervalPaneModel({ level: makeDynamicValue("0") });
      const { comp } = await renderComponent({
         intervalPaneModel: model,
         isCustomPeriod: false,
         standardPeriodLevel: makeDynamicValue(XConstants.QUARTER_DATE_GROUP),
      });

      const values = comp.getGranularities().map(g => g.value);

      expect(values).toEqual([IntervalLevel.QUARTER + "", IntervalLevel.MONTH + "",
         IntervalLevel.WEEK + "", IntervalLevel.DAY + ""]);
   });

   it("should return all granularities for level YEAR_TO_DATE", async () => {
      const model = makeIntervalPaneModel({ level: makeDynamicValue(IntervalLevel.YEAR_TO_DATE + "") });
      const { comp } = await renderComponent({ intervalPaneModel: model, isCustomPeriod: false });

      expect(comp.getGranularities()).toHaveLength(5);
   });

   it("should limit to Quarter/Month/Week/Day for level QUARTER_TO_DATE", async () => {
      const model = makeIntervalPaneModel({ level: makeDynamicValue(IntervalLevel.QUARTER_TO_DATE + "") });
      const { comp } = await renderComponent({ intervalPaneModel: model, isCustomPeriod: false });

      const values = comp.getGranularities().map(g => g.value);

      expect(values).toEqual([IntervalLevel.QUARTER + "", IntervalLevel.MONTH + "",
         IntervalLevel.WEEK + "", IntervalLevel.DAY + ""]);
   });

   it("should limit to Month/Week/Day for level MONTH_TO_DATE", async () => {
      const model = makeIntervalPaneModel({ level: makeDynamicValue(IntervalLevel.MONTH_TO_DATE + "") });
      const { comp } = await renderComponent({ intervalPaneModel: model, isCustomPeriod: false });

      const values = comp.getGranularities().map(g => g.value);

      expect(values).toEqual([IntervalLevel.MONTH + "", IntervalLevel.WEEK + "", IntervalLevel.DAY + ""]);
   });

   it("should limit to Week/Day for level WEEK_TO_DATE", async () => {
      const model = makeIntervalPaneModel({ level: makeDynamicValue(IntervalLevel.WEEK_TO_DATE + "") });
      const { comp } = await renderComponent({ intervalPaneModel: model, isCustomPeriod: false });

      const values = comp.getGranularities().map(g => g.value);

      expect(values).toEqual([IntervalLevel.WEEK + "", IntervalLevel.DAY + ""]);
   });

   it("should limit to Day only for level SAME_DAY", async () => {
      const model = makeIntervalPaneModel({ level: makeDynamicValue(IntervalLevel.SAME_DAY + "") });
      const { comp } = await renderComponent({ intervalPaneModel: model, isCustomPeriod: false });

      expect(comp.getGranularities().map(g => g.value)).toEqual([IntervalLevel.DAY + ""]);
   });

   // level.value is mutated AFTER render (not passed as an @Input) because ngOnInit's
   // visibleIntervalLevel self-heal would otherwise reset an out-of-domain "999" back to "0"
   // before the test reaches getGranularities(). Every value in the real intervalLevels domain
   // already has an explicit switch case, so this default branch is only reachable this way.
   it("should fall back to Day only for an unrecognized level value (default case)", async () => {
      const { comp } = await renderComponent({ isCustomPeriod: false });
      comp.intervalPaneModel.level.value = "999";

      expect(comp.getGranularities().map(g => g.value)).toEqual([IntervalLevel.DAY + ""]);
   });
});

// ---------------------------------------------------------------------------
// Group 6: granularitiesAllIntervalVisible
// ---------------------------------------------------------------------------

describe("DateComparisonIntervalPaneComponent — granularitiesAllIntervalVisible", () => {
   it("should return true when isCustomPeriod is true, regardless of periodLevel or granularity", async () => {
      const { comp } = await renderComponent({ isCustomPeriod: true });

      const result = comp.granularitiesAllIntervalVisible(
         makeDynamicValue(XConstants.DAY_DATE_GROUP), { value: IntervalLevel.YEAR + "" });

      expect(result).toBe(true);
   });

   it("should return true when periodLevel.type is not VALUE, regardless of its value", async () => {
      const { comp } = await renderComponent({ isCustomPeriod: false });

      const result = comp.granularitiesAllIntervalVisible(
         makeDynamicValue("Var", ValueTypes.VARIABLE), { value: IntervalLevel.YEAR + "" });

      expect(result).toBe(true);
   });

   it("should return true for every granularity when periodLevel.value is YEAR_DATE_GROUP", async () => {
      const { comp } = await renderComponent({ isCustomPeriod: false });
      const periodLevel = makeDynamicValue(XConstants.YEAR_DATE_GROUP);

      expect(comp.granularitiesAllIntervalVisible(periodLevel, { value: IntervalLevel.DAY + "" })).toBe(true);
   });

   it("should include Month but exclude Year when periodLevel.value is QUARTER_DATE_GROUP", async () => {
      const { comp } = await renderComponent({ isCustomPeriod: false });
      const periodLevel = makeDynamicValue(XConstants.QUARTER_DATE_GROUP);

      expect(comp.granularitiesAllIntervalVisible(periodLevel, { value: IntervalLevel.MONTH + "" })).toBe(true);
      expect(comp.granularitiesAllIntervalVisible(periodLevel, { value: IntervalLevel.YEAR + "" })).toBe(false);
   });

   it("should include Week but exclude Quarter when periodLevel.value is MONTH_DATE_GROUP", async () => {
      const { comp } = await renderComponent({ isCustomPeriod: false });
      const periodLevel = makeDynamicValue(XConstants.MONTH_DATE_GROUP);

      expect(comp.granularitiesAllIntervalVisible(periodLevel, { value: IntervalLevel.WEEK + "" })).toBe(true);
      expect(comp.granularitiesAllIntervalVisible(periodLevel, { value: IntervalLevel.QUARTER + "" })).toBe(false);
   });

   it("should include Day but exclude Month when periodLevel.value is WEEK_DATE_GROUP", async () => {
      const { comp } = await renderComponent({ isCustomPeriod: false });
      const periodLevel = makeDynamicValue(XConstants.WEEK_DATE_GROUP);

      expect(comp.granularitiesAllIntervalVisible(periodLevel, { value: IntervalLevel.DAY + "" })).toBe(true);
      expect(comp.granularitiesAllIntervalVisible(periodLevel, { value: IntervalLevel.MONTH + "" })).toBe(false);
   });

   it("should include only Day and exclude Week when periodLevel.value is DAY_DATE_GROUP", async () => {
      const { comp } = await renderComponent({ isCustomPeriod: false });
      const periodLevel = makeDynamicValue(XConstants.DAY_DATE_GROUP);

      expect(comp.granularitiesAllIntervalVisible(periodLevel, { value: IntervalLevel.DAY + "" })).toBe(true);
      expect(comp.granularitiesAllIntervalVisible(periodLevel, { value: IntervalLevel.WEEK + "" })).toBe(false);
   });

   it("should return false when periodLevel.value matches no named group", async () => {
      const { comp } = await renderComponent({ isCustomPeriod: false });
      const periodLevel = makeDynamicValue(XConstants.NONE_DATE_GROUP);

      expect(comp.granularitiesAllIntervalVisible(periodLevel, { value: IntervalLevel.DAY + "" })).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 7: getContextLevels — 4-branch dispatch
// ---------------------------------------------------------------------------

describe("DateComparisonIntervalPaneComponent — getContextLevels", () => {
   it("should return the full context list when neither standardPeriodLevel nor level is a VALUE type", async () => {
      const model = makeIntervalPaneModel({ level: makeDynamicValue("LVar", ValueTypes.VARIABLE) });
      const { comp } = await renderComponent({
         intervalPaneModel: model,
         standardPeriodLevel: makeDynamicValue("SVar", ValueTypes.VARIABLE),
      });

      expect(comp.getContextLevels().map(l => l.value)).toEqual([
         XConstants.YEAR_DATE_GROUP, XConstants.QUARTER_DATE_GROUP,
         XConstants.MONTH_DATE_GROUP, XConstants.WEEK_DATE_GROUP,
      ]);
   });

   it("should apply an inclusive (>=) floor from the interval level when standardPeriodLevel is not a VALUE type", async () => {
      const model = makeIntervalPaneModel({ level: makeDynamicValue(IntervalLevel.MONTH_TO_DATE + "") });
      const { comp } = await renderComponent({
         intervalPaneModel: model,
         standardPeriodLevel: makeDynamicValue("SVar", ValueTypes.VARIABLE),
      });

      // MONTH_TO_DATE -> MONTH_DATE_GROUP(3); inclusive floor keeps MONTH(3), excludes WEEK(2).
      expect(comp.getContextLevels().map(l => l.value)).toEqual([
         XConstants.YEAR_DATE_GROUP, XConstants.QUARTER_DATE_GROUP, XConstants.MONTH_DATE_GROUP,
      ]);
   });

   it("should apply a strict (>) floor from the interval level for a SAME_* level (isSameLevel)", async () => {
      const model = makeIntervalPaneModel({ level: makeDynamicValue(IntervalLevel.SAME_MONTH + "") });
      const { comp } = await renderComponent({
         intervalPaneModel: model,
         standardPeriodLevel: makeDynamicValue("SVar", ValueTypes.VARIABLE),
      });

      // SAME_MONTH -> MONTH_DATE_GROUP(3); strict floor excludes MONTH(3) itself, unlike the inclusive case above.
      expect(comp.getContextLevels().map(l => l.value)).toEqual([
         XConstants.YEAR_DATE_GROUP, XConstants.QUARTER_DATE_GROUP,
      ]);
   });

   it("should apply a ceiling from standardPeriodLevel when level is not a VALUE type", async () => {
      const model = makeIntervalPaneModel({ level: makeDynamicValue("LVar", ValueTypes.VARIABLE) });
      const { comp } = await renderComponent({
         intervalPaneModel: model,
         standardPeriodLevel: makeDynamicValue(XConstants.MONTH_DATE_GROUP),
      });

      expect(comp.getContextLevels().map(l => l.value)).toEqual([
         XConstants.MONTH_DATE_GROUP, XConstants.WEEK_DATE_GROUP,
      ]);
   });

   it("should apply both the standardPeriodLevel ceiling and the interval-level floor together", async () => {
      const model = makeIntervalPaneModel({ level: makeDynamicValue(IntervalLevel.WEEK_TO_DATE + "") });
      const { comp } = await renderComponent({
         intervalPaneModel: model,
         standardPeriodLevel: makeDynamicValue(XConstants.QUARTER_DATE_GROUP),
      });

      // ceiling <= QUARTER(4); floor >= WEEK_DATE_GROUP(2) (inclusive, not SAME_*).
      expect(comp.getContextLevels().map(l => l.value)).toEqual([
         XConstants.QUARTER_DATE_GROUP, XConstants.MONTH_DATE_GROUP, XConstants.WEEK_DATE_GROUP,
      ]);
   });
});

// ---------------------------------------------------------------------------
// Group 8: intervalLevelConvertToGroupLevel
// ---------------------------------------------------------------------------

describe("DateComparisonIntervalPaneComponent — intervalLevelConvertToGroupLevel", () => {
   it("should map each granularity bit to its XConstants date-group value", async () => {
      const { comp } = await renderComponent();

      expect(comp.intervalLevelConvertToGroupLevel(IntervalLevel.YEAR)).toBe(XConstants.YEAR_DATE_GROUP);
      expect(comp.intervalLevelConvertToGroupLevel(IntervalLevel.QUARTER)).toBe(XConstants.QUARTER_DATE_GROUP);
      expect(comp.intervalLevelConvertToGroupLevel(IntervalLevel.MONTH)).toBe(XConstants.MONTH_DATE_GROUP);
      expect(comp.intervalLevelConvertToGroupLevel(IntervalLevel.WEEK)).toBe(XConstants.WEEK_DATE_GROUP);
      expect(comp.intervalLevelConvertToGroupLevel(IntervalLevel.DAY)).toBe(XConstants.DAY_DATE_GROUP);
   });

   it("should return -1 when no recognized bit is set", async () => {
      const { comp } = await renderComponent();

      expect(comp.intervalLevelConvertToGroupLevel(0)).toBe(-1);
   });
});

// ---------------------------------------------------------------------------
// Group 9: toDateLabel — outer/inner switch, all label combinations
// ---------------------------------------------------------------------------

describe("DateComparisonIntervalPaneComponent — toDateLabel", () => {
   const CASES: Array<{ name: string; level: number; contextLevel?: number; expected: string }> = [
      { name: "YEAR_TO_DATE", level: IntervalLevel.YEAR_TO_DATE,
         expected: "_#(js:date.comparison.xToDate.year)" },

      { name: "QUARTER_TO_DATE / context=YEAR", level: IntervalLevel.QUARTER_TO_DATE,
         contextLevel: XConstants.YEAR_DATE_GROUP, expected: "_#(js:date.comparison.xToDate.quarterByYear)" },
      // QUARTER_DATE_GROUP is the only context level valid for QUARTER_TO_DATE besides YEAR
      // (getContextLevels floors at the interval's own group); it is not explicitly cased, so
      // it exercises the "default" branch without being healed away by contextLevelValue.
      { name: "QUARTER_TO_DATE / context=other (default)", level: IntervalLevel.QUARTER_TO_DATE,
         contextLevel: XConstants.QUARTER_DATE_GROUP, expected: "_#(js:date.comparison.xToDate.quarter)" },

      { name: "MONTH_TO_DATE / context=YEAR", level: IntervalLevel.MONTH_TO_DATE,
         contextLevel: XConstants.YEAR_DATE_GROUP, expected: "_#(js:date.comparison.xToDate.monthByYear)" },
      { name: "MONTH_TO_DATE / context=QUARTER", level: IntervalLevel.MONTH_TO_DATE,
         contextLevel: XConstants.QUARTER_DATE_GROUP, expected: "_#(js:date.comparison.xToDate.monthByQuarter)" },
      // WEEK_DATE_GROUP would be healed away by contextLevelValue (below the interval's own
      // MONTH floor); MONTH_DATE_GROUP is the lowest value that survives and is not explicitly
      // cased, so it reaches the "default" branch.
      { name: "MONTH_TO_DATE / context=other (default)", level: IntervalLevel.MONTH_TO_DATE,
         contextLevel: XConstants.MONTH_DATE_GROUP, expected: "_#(js:date.comparison.xToDate.month)" },

      { name: "WEEK_TO_DATE / context=YEAR", level: IntervalLevel.WEEK_TO_DATE,
         contextLevel: XConstants.YEAR_DATE_GROUP, expected: "_#(js:date.comparison.xToDate.weekByYear)" },
      { name: "WEEK_TO_DATE / context=QUARTER", level: IntervalLevel.WEEK_TO_DATE,
         contextLevel: XConstants.QUARTER_DATE_GROUP, expected: "_#(js:date.comparison.xToDate.weekByQuarter)" },
      { name: "WEEK_TO_DATE / context=MONTH", level: IntervalLevel.WEEK_TO_DATE,
         contextLevel: XConstants.MONTH_DATE_GROUP, expected: "_#(js:date.comparison.xToDate.weekByMonth)" },
      { name: "WEEK_TO_DATE / context=other (default)", level: IntervalLevel.WEEK_TO_DATE,
         contextLevel: XConstants.WEEK_DATE_GROUP, expected: "_#(js:date.comparison.xToDate.week)" },

      { name: "SAME_QUARTER (context ignored — no inner switch)", level: IntervalLevel.SAME_QUARTER,
         contextLevel: XConstants.MONTH_DATE_GROUP, expected: "_#(js:date.comparison.sameX.quarterByYear)" },

      { name: "SAME_MONTH / context=QUARTER", level: IntervalLevel.SAME_MONTH,
         contextLevel: XConstants.QUARTER_DATE_GROUP, expected: "_#(js:date.comparison.sameX.monthByQuarter)" },
      { name: "SAME_MONTH / context=other (default)", level: IntervalLevel.SAME_MONTH,
         contextLevel: XConstants.YEAR_DATE_GROUP, expected: "_#(js:date.comparison.sameX.monthByYear)" },

      { name: "SAME_WEEK / context=QUARTER", level: IntervalLevel.SAME_WEEK,
         contextLevel: XConstants.QUARTER_DATE_GROUP, expected: "_#(js:date.comparison.sameX.weekByQuarter)" },
      { name: "SAME_WEEK / context=MONTH", level: IntervalLevel.SAME_WEEK,
         contextLevel: XConstants.MONTH_DATE_GROUP, expected: "_#(js:date.comparison.sameX.weekByMonth)" },
      { name: "SAME_WEEK / context=other (default)", level: IntervalLevel.SAME_WEEK,
         contextLevel: XConstants.YEAR_DATE_GROUP, expected: "_#(js:date.comparison.sameX.weekByYear)" },

      { name: "SAME_DAY / context=QUARTER", level: IntervalLevel.SAME_DAY,
         contextLevel: XConstants.QUARTER_DATE_GROUP, expected: "_#(js:date.comparison.sameX.dayByQuarter)" },
      { name: "SAME_DAY / context=MONTH", level: IntervalLevel.SAME_DAY,
         contextLevel: XConstants.MONTH_DATE_GROUP, expected: "_#(js:date.comparison.sameX.dayByMonth)" },
      { name: "SAME_DAY / context=WEEK", level: IntervalLevel.SAME_DAY,
         contextLevel: XConstants.WEEK_DATE_GROUP, expected: "_#(js:date.comparison.sameX.dayByWeek)" },
      { name: "SAME_DAY / context=other (default)", level: IntervalLevel.SAME_DAY,
         contextLevel: XConstants.YEAR_DATE_GROUP, expected: "_#(js:date.comparison.sameX.dayByYear)" },

      { name: "unmatched level (outer default)", level: 0, expected: "_#(js:date.comparison.unknown)" },
   ];

   for(const testCase of CASES) {
      it(`should return "${testCase.expected}" for ${testCase.name}`, async () => {
         const model = makeIntervalPaneModel({
            level: makeDynamicValue(testCase.level + ""),
            contextLevel: makeDynamicValue(testCase.contextLevel ?? 0),
         });
         const { comp } = await renderComponent({ intervalPaneModel: model });

         expect(comp.toDateLabel).toBe(testCase.expected);
      });
   }
});

// ---------------------------------------------------------------------------
// Group 10: toDate getter
// ---------------------------------------------------------------------------

describe("DateComparisonIntervalPaneComponent — toDate", () => {
   it("should parse from periodEndDay when endDayAsToDate is true", async () => {
      const model = makeIntervalPaneModel({ endDayAsToDate: true });
      const { comp } = await renderComponent({
         intervalPaneModel: model,
         periodEndDay: makeDynamicValue("2024-03-10"),
      });

      const result = comp.toDate;

      expect(result).not.toBeNull();
      expect(result!.getFullYear()).toBe(2024);
      expect(result!.getMonth()).toBe(2); // 0-indexed: March
      expect(result!.getDate()).toBe(10);
   });

   it("should parse from intervalEndDate when endDayAsToDate is false", async () => {
      const model = makeIntervalPaneModel({
         endDayAsToDate: false,
         intervalEndDate: makeDynamicValue("2024-01-15"),
      });
      const { comp } = await renderComponent({ intervalPaneModel: model });

      const result = comp.toDate;

      expect(result).not.toBeNull();
      expect(result!.getFullYear()).toBe(2024);
      expect(result!.getMonth()).toBe(0); // January
      expect(result!.getDate()).toBe(15);
   });

   // periodEndDay is set to null AFTER render, and this test never calls fixture.detectChanges()
   // again afterward — so Angular's change detection never re-evaluates the template against
   // the null value, and the toDate getter is read directly instead. This is necessary because
   // BOTH ways of supplying the null value up front crash during the initial render itself:
   // (1) periodEndDay: null as a componentInput crashes the parent template's own
   //     `[defaultValue]="periodEndDay.value"` binding (line 94 of the .html), and
   // (2) intervalPaneModel.intervalEndDate: null (the other operand of the ternary this getter
   //     reads) crashes via `@if (!isValidInterval() && !disable)` in the template, since
   //     isValidInterval() -> verifyEndDate() -> dateComparisonService.isValidDate(...value)
   //     dereferences intervalEndDate.value unconditionally whenever showEndDate() is true.
   // There is no @Input-safe way to reach this branch; mutate-then-read-without-detectChanges
   // is the only viable technique for this particular getter.
   it("should return null when the selected end-date source is null", async () => {
      const model = makeIntervalPaneModel({ endDayAsToDate: true });
      const { comp } = await renderComponent({ intervalPaneModel: model });
      comp.periodEndDay = null as any;

      expect(comp.toDate).toBeNull();
   });

   it("should return null when the end-date value's type is set but is not VALUE", async () => {
      const model = makeIntervalPaneModel({
         endDayAsToDate: false,
         intervalEndDate: makeDynamicValue("MyVar", ValueTypes.VARIABLE),
      });
      const { comp } = await renderComponent({ intervalPaneModel: model });

      expect(comp.toDate).toBeNull();
   });

   it("should return the current date when the end-date value is empty", async () => {
      vi.useFakeTimers();
      vi.setSystemTime(new Date(2024, 5, 1, 0, 0, 0, 0));

      const model = makeIntervalPaneModel({
         endDayAsToDate: false,
         intervalEndDate: makeDynamicValue(""),
      });
      const { comp } = await renderComponent({ intervalPaneModel: model });

      expect(comp.toDate).toEqual(new Date(2024, 5, 1, 0, 0, 0, 0));
   });

   it("should return null when the end-date value cannot be parsed into a valid date", async () => {
      const model = makeIntervalPaneModel({
         endDayAsToDate: false,
         intervalEndDate: makeDynamicValue("garbage-value"),
      });
      const { comp } = await renderComponent({ intervalPaneModel: model });

      expect(comp.toDate).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 11: getToDateLabel — dead code path
// ---------------------------------------------------------------------------

describe("DateComparisonIntervalPaneComponent — getToDateLabel", () => {
   // The entire body is commented out except the trailing `return null;` — this always
   // returns null regardless of model state. Documented as a baseline happy-path check.
   it("should always return null", async () => {
      const model = makeIntervalPaneModel({ level: makeDynamicValue(IntervalLevel.YEAR_TO_DATE + "") });
      const { comp } = await renderComponent({ intervalPaneModel: model });

      expect(comp.getToDateLabel()).toBeNull();
   });
});
