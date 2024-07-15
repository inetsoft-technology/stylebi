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
      modelService = { open: jest.fn() };
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

});