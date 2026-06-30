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
 * ConditionItemPane - Pass 3: Display
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] - showUseList and date-range guards
 *   Group 2 [Risk 2] - default condition value dispatch
 *   Group 3 [Risk 1] - formula and provider-backed predicate getters
 *   Group 4 [Risk 2] - updateCondition null-values guard on DATE_IN path
 *
 * Suspected bugs (header only — no test yet):
 *   Suspicion A - showUseList assumes ColumnRef.dataRefModel exists; partial ColumnRef inputs can throw before returning.
 *   Suspicion C - numeric BETWEEN defaults to an empty values array, while binary-condition-editor indexes values[0]/values[1].
 *
 * Out of scope this pass: modal and mutation emit flows.
 */

import { SourceInfo } from "../../binding/data/source-info";
import { ConditionOperation } from "../../common/data/condition/condition-operation";
import { ConditionValueType } from "../../common/data/condition/condition-value-type";
import { FormulaType } from "../../common/data/formula-type";
import { XSchema } from "../../common/data/xschema";
import {
   createConditionItemPane,
   makeCondition,
   makeField,
   makeProvider
} from "./condition-pane.test-helpers";

afterEach(() => vi.restoreAllMocks());

describe("ConditionItemPane - showUseList and date-range guards [Group 1, Risk 2]", () => {
   it("should hide use-list for DATE_IN operation and ColumnRef date-range fields", () => {
      const { comp } = createConditionItemPane({
         condition: makeCondition({ operation: ConditionOperation.DATE_IN })
      });

      expect(comp.showUseList).toBe(false);

      comp.condition.operation = ConditionOperation.EQUAL_TO;
      comp.condition.field = {
         ...makeField("OrderDate"),
         classType: "ColumnRef",
         dataRefModel: { classType: "DateRangeRef" }
      } as any;

      expect(comp.showUseList).toBe(false);
   });

   it("should hide use-list for highlight date ranges or disabled browse-data", () => {
      const provider = makeProvider({
         isBrowseDataEnabled: vi.fn(() => false)
      } as any);
      const { comp } = createConditionItemPane({ provider });
      comp.isHighlight = true;
      comp.condition.field = makeField("OrderDate", {
         classType: "BaseField",
         dataType: XSchema.DATE,
         attribute: "OrderDate",
         gfld: true
      } as any);

      expect(comp.showUseList).toBe(false);

      comp.condition.field = makeField("state");
      expect(comp.showUseList).toBe(false);
   });

   it("should allow use-list for normal VS context fields", () => {
      const { comp } = createConditionItemPane();

      expect(comp.showUseList).toBe(true);
   });

   it("should detect grouped date fields and date-part group names as date ranges", () => {
      const { comp } = createConditionItemPane();
      const datePartNames = [
         "QuarterOfYear(OrderDate)",
         "MonthOfYear(OrderDate)",
         "WeekOfYear(OrderDate)",
         "DayOfMonth(OrderDate)",
         "DayOfWeek(OrderDate)",
         "HourOfDay(OrderDate)"
      ];

      expect((comp as any).isDateRange(makeField("OrderDate", {
         classType: "BaseField",
         dataType: XSchema.DATE,
         attribute: "OrderDate",
         gfld: true
      } as any))).toBe(true);

      for(const attribute of datePartNames) {
         expect((comp as any).isDateRange(makeField(attribute, {
            classType: "BaseField",
            dataType: XSchema.INTEGER,
            attribute
         } as any))).toBe(true);
      }

      expect((comp as any).isDateRange(makeField("plain", {
         classType: "BaseField",
         dataType: XSchema.STRING,
         attribute: "plain"
      } as any))).toBe(false);
   });
});

describe("ConditionItemPane - default condition values [Group 2, Risk 2]", () => {
   it("should create string default values using provider-selected value type", () => {
      const provider = makeProvider({
         getConditionValueTypes: vi.fn(() => [ConditionValueType.VARIABLE])
      } as any);
      const { comp } = createConditionItemPane({ provider });
      comp.condition.operation = ConditionOperation.EQUAL_TO;

      expect((comp as any).getDefaultConditionValues()).toEqual([
         { value: "", type: ConditionValueType.VARIABLE }
      ]);
   });

   it("should create two string values for BETWEEN and no numeric values behind the null guard", () => {
      const { comp } = createConditionItemPane({
         condition: makeCondition({
            field: makeField("state", { dataType: XSchema.STRING }),
            operation: ConditionOperation.BETWEEN
         })
      });

      expect((comp as any).getDefaultConditionValues()).toEqual([
         { value: "", type: ConditionValueType.VALUE },
         { value: "", type: ConditionValueType.VALUE }
      ]);

      comp.condition.field = makeField("amount", { dataType: XSchema.INTEGER });
      expect((comp as any).getDefaultConditionValue()).toBeNull();
      expect((comp as any).getDefaultConditionValues()).toEqual([]);
   });

   it("should create ranking defaults only for non-date TOP_N and BOTTOM_N operations", () => {
      const { comp } = createConditionItemPane({
         condition: makeCondition({
            field: makeField("amount", { dataType: XSchema.INTEGER }),
            operation: ConditionOperation.TOP_N
         })
      });

      expect((comp as any).getDefaultConditionValues()).toEqual([
         { value: { n: 3 }, type: ConditionValueType.VALUE }
      ]);

      comp.condition.operation = ConditionOperation.BOTTOM_N;
      expect((comp as any).getDefaultConditionValues()).toEqual([
         { value: { n: 3 }, type: ConditionValueType.VALUE }
      ]);

      comp.condition.field = makeField("OrderDate", { dataType: XSchema.DATE });
      expect((comp as any).getDefaultConditionValues()).toEqual([]);
   });

   it("should return no default values for NULL operation", () => {
      const { comp } = createConditionItemPane({
         condition: makeCondition({ operation: ConditionOperation.NULL })
      });

      expect((comp as any).getDefaultConditionValues()).toEqual([]);
   });
});

describe("ConditionItemPane - formula and provider-backed predicates [Group 3, Risk 1]", () => {
   it("should expose formula expression and type, with report worksheet source forcing script type", () => {
      const { comp } = createConditionItemPane();
      comp.formula = {
         ...makeField("Calc"),
         classType: "FormulaField",
         formulaType: FormulaType.SQL,
         exp: "a + b"
      } as any;

      expect(comp.formulaExpression).toBe("a + b");
      expect(comp.formulaType).toBe(FormulaType.SQL);

      vi.spyOn(comp, "getSource").mockReturnValue({ type: SourceInfo.ASSET } as SourceInfo);
      expect(comp.formulaType).toBe(FormulaType.SCRIPT);
   });

   it("should expose formula-field, source, browse-data, and grayed-field predicates", () => {
      const provider = makeProvider({
         isBrowseDataEnabled: vi.fn(() => false),
         getGrayedOutFields: vi.fn(() => [makeField("blocked")])
      } as any);
      const { comp } = createConditionItemPane({ provider });

      comp.condition.field = { ...makeField("Calc"), classType: "FormulaField" } as any;
      expect(comp.isFormulaField()).toBe(true);

      comp.condition.field = makeField("state");
      expect(comp.isFormulaField()).toBe(false);
      expect(comp.isReportWorksheetSource()).toBe(false);
      expect(comp.isBrowseDataEnabled()).toBe(false);
      expect(comp.getGrayedOutFields()).toEqual([makeField("blocked")]);
   });
});

describe("ConditionItemPane - updateCondition null-values guard [Group 4, Risk 2]", () => {
   it("should not throw when condition.values is null on DATE_IN path", () => {
      const { comp } = createConditionItemPane({
         condition: makeCondition({
            operation: ConditionOperation.DATE_IN,
            values: null as any
         })
      });

      expect(() => comp.updateCondition()).not.toThrow();
   });
});
