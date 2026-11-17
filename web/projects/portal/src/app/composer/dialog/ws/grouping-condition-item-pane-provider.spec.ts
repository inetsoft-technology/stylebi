/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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

import { ConditionOperation } from "../../../common/data/condition/condition-operation";
import { ConditionValueType } from "../../../common/data/condition/condition-value-type";
import { TestUtils } from "../../../common/test/test-utils";
import { GroupingConditionItemPaneProvider } from "./grouping-condition-item-pane-provider";

describe("grouping condition dialog component unit case", () => {
   let modelService: any;
   let groupConItemProvider: GroupingConditionItemPaneProvider;

   beforeEach(() => {
      modelService = { open: vi.fn() };
      groupConItemProvider = new GroupingConditionItemPaneProvider(modelService, "grouping-0");

   });

   //Bug #19094 should get right value type
   it("should get right value type", () => {
      let condition = TestUtils.createMockCondition();
      condition.field.attribute = "this";
      condition.field.dataType = "string";
      let valueTypes = groupConItemProvider.getConditionValueTypes(condition);

      expect(valueTypes.length).toBe(3);
      expect(valueTypes[valueTypes.length - 1]).toBe(ConditionValueType.SESSION_DATA);

      condition.field.dataType = "integer";
      valueTypes = groupConItemProvider.getConditionValueTypes(condition);
      expect(valueTypes.length).toBe(2);

      condition.field.dataType = "string";
      condition.operation = ConditionOperation.CONTAINS;
      valueTypes = groupConItemProvider.getConditionValueTypes(condition);
      expect(valueTypes.length).toBe(2);
   });

   //Bug #19088 check operations on diff column type
   it("check operations on diff column type", () => {
      let condition = TestUtils.createMockCondition();
      condition.field.attribute = "this";
      condition.field.dataType = "boolean";
      let operators = groupConItemProvider.getConditionOperations(condition);
      expect(operators.length).toBe(2);

      condition.field.dataType = "string";
      operators = groupConItemProvider.getConditionOperations(condition);
      expect(operators.length).toBe(9);

      condition.field.dataType = "date";
      operators = groupConItemProvider.getConditionOperations(condition);
      expect(operators.length).toBe(7);

      condition.field.dataType = "time";
      operators = groupConItemProvider.getConditionOperations(condition);
      expect(operators.length).toBe(6);

      condition.field.dataType = "string";
      condition.values[0].type = ConditionValueType.SESSION_DATA;
      operators = groupConItemProvider.getConditionOperations(condition);
      expect(operators.length).toBe(1);
   });

   // Bug #80459 pin: the grouping / named-group condition dialog offers no Expression value, so
   // its value slots have no Connect to Claude pairing target. If EXPRESSION is ever added to the
   // value types (for any operator, field type or value type), a pairing kind for it has to be
   // designed first (see the worksheet variable default / hyperlink kinds), and this must fail.
   it("never offers the Expression value type", () => {
      const operations = Object.values(ConditionOperation)
         .filter((v) => typeof v === "number") as ConditionOperation[];
      const dataTypes = [null, "string", "integer", "double", "boolean", "date", "time",
                         "timeInstant", "char"];
      const valueTypes = Object.values(ConditionValueType);
      let checked = 0;

      for(let dataType of dataTypes) {
         for(let operation of operations) {
            for(let valueType of valueTypes) {
               let condition = TestUtils.createMockCondition();
               condition.field = dataType == null ? null : {
                  attribute: "this", dataType: dataType
               } as any;
               condition.operation = operation;
               condition.values[0].type = valueType as ConditionValueType;
               const offered = groupConItemProvider.getConditionValueTypes(condition);

               expect(offered).not.toContain(ConditionValueType.EXPRESSION);
               expect(offered.length).toBeGreaterThan(0);
               checked++;
            }
         }
      }

      expect(checked).toBe(dataTypes.length * operations.length * valueTypes.length);
   });

});