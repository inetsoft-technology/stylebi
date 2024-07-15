/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { Tool } from "../../../../../shared/util/tool";
import { Condition } from "../../common/data/condition/condition";
import { ConditionOperation } from "../../common/data/condition/condition-operation";
import { ConditionValueType } from "../../common/data/condition/condition-value-type";
import { JunctionOperatorType } from "../../common/data/condition/junction-operator-type";
import { TestUtils } from "../../common/test/test-utils";
import { ComponentTool } from "../../common/util/component-tool";
import { ConditionList } from "../../common/util/condition-list";
import { ConditionPane } from "./condition-pane.component";

describe("Condition Pane Tests", () => {
   let conditionPane: ConditionPane;
   let route: any;

   beforeEach(() => {
      route = { open: jest.fn() };
      conditionPane = new ConditionPane(route);
   });

   it("should clear current condition", () => {
      conditionPane.condition = <Condition> {};
      conditionPane.conditionList = [];
      conditionPane.clear();
      expect(Tool.isEquals(conditionPane.emptyCondition(), conditionPane.condition))
         .toBeTruthy();
      expect(conditionPane.conditionList.length === 0).toBeTruthy();
   });

   //Bug #17784 should delete the selected condition.
   it("should delete the selected condition", () => {
      let condition1: Condition = TestUtils.createMockCondition();
      let condition2: Condition = TestUtils.createMockCondition();
      condition1.field.attribute = "state";
      condition1.field.dataType = "string";
      condition1.values = [{type: ConditionValueType.VALUE, value: "AK"}];

      condition2.field.attribute = "id";
      condition2.field.dataType = "integer";
      condition2.operation = ConditionOperation.LESS_THAN;
      condition2.values = [{type: ConditionValueType.VALUE, value: "10"}];

      let junction = {
         jsonType: "junction",
         level: 0,
         type: JunctionOperatorType.AND
      };

      let list: any[] = [condition1, junction, condition2];
      conditionPane.conditionList = list;
      conditionPane.selectedIndex = 2;
      conditionPane.delete();
      expect(conditionPane.selectedIndex).toBe(0);
      expect(conditionPane.condition).toEqual(condition1);
   });

    //#18359 should keep highlight after indent
   it("check function of indent", () => {
      const condition1: Condition = TestUtils.createMockCondition();
      const condition2: Condition = TestUtils.createMockCondition();
      const condition3: Condition = TestUtils.createMockCondition();

      condition1.field.attribute = "state";
      condition1.field.dataType = "string";
      condition1.values = [{type: ConditionValueType.VALUE, value: "AK"}];

      condition2.field.attribute = "state";
      condition2.field.dataType = "string";
      condition2.values = [{type: ConditionValueType.VALUE, value: "NJ"}];

      condition3.field.attribute = "id";
      condition3.field.dataType = "integer";
      condition3.operation = ConditionOperation.LESS_THAN;
      condition3.values = [{type: ConditionValueType.VALUE, value: "10"}];

      const junction1 = {
         jsonType: "junction",
         level: 0,
         type: JunctionOperatorType.OR
      };

      const junction2 = {
         jsonType: "junction",
         level: 0,
         type: JunctionOperatorType.AND
      };

      const junction2New = {
         jsonType: "junction",
         level: 1,
         type: JunctionOperatorType.AND
      };

      const condition2New = {
         ...condition2,
         level: 1
      };

      const condition3New = {
         ...condition3,
         level: 1
      };

      const list: any[] = [condition1, junction1, condition2, junction2, condition3];
      const listNew: any[] = [{...condition1}, {...junction1},
         condition2New, junction2New, condition3New];
      conditionPane.conditionList = list;
      let emittedList: ConditionList = null;
      conditionPane.conditionListChange.subscribe((condList) => emittedList = condList);
      conditionPane.selectedIndex = 3;
      conditionPane.indent();
      expect(conditionPane.selectedIndex).toBe(3);
      expect(emittedList).toEqual(listNew);
   });

   //Bug #20324 should pop up warning when no selected field.
   it("should pop up warning when no select field", () => {
      let con1 = TestUtils.createMockCondition();
      con1.field = null;
      con1.values = [{value: "111", type: ConditionValueType.VALUE}];
      conditionPane.condition = con1;
      let showMessageDialog = jest.spyOn(ComponentTool, "showMessageDialog");
      showMessageDialog.mockImplementation(() => Promise.resolve("ok"));

      conditionPane.insert();
      expect(showMessageDialog).toHaveBeenCalled();
      expect(showMessageDialog.mock.calls[0][1]).toBe("_#(js:Error)");
      expect(showMessageDialog.mock.calls[0][2]).toBe("_#(js:field.required)");

      showMessageDialog.mockClear();
      con1.values = [{value: "${var}", type: ConditionValueType.VARIABLE}];
      conditionPane.insert();
      expect(showMessageDialog).toHaveBeenCalled();
   });
});
