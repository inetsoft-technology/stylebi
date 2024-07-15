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
import { Condition } from "../../../common/data/condition/condition";
import { ConditionOperation } from "../../../common/data/condition/condition-operation";
import { ConditionValueType } from "../../../common/data/condition/condition-value-type";
import { TestUtils } from "../../../common/test/test-utils";
import { isValidConditionList } from "../../../common/util/condition.util";
import { LocalStorage } from "../../../common/util/local-storage.util";
import { AssemblyConditionDialogModel } from "../../data/ws/assembly-condition-dialog-model";
import { AssemblyConditionDialog } from "./assembly-condition-dialog.component";

declare const document: any;

let createModel: () => AssemblyConditionDialogModel = () => {
   return {
      advanced: false,
      preAggregateFields: [],
      postAggregateFields: [],
      preAggregateConditionList: [],
      postAggregateConditionList: [],
      rankingConditionList: [],
      mvConditionPaneModel: null,
      subqueryTables: [],
      variableNames: [],
      expressionFields: [],
      scriptDefinitions: {}
   };
};

describe("Assembly Condition Dialog Tests", () => {
   let conditionDialog: AssemblyConditionDialog;

   beforeEach(() => {
      let modelService: any = { getModel: jest.fn() };
      let worksheetClient: any = { sendEvent: jest.fn() };
      let http: any = { get: jest.fn(), post: jest.fn() };
      let modalService: any = { open: jest.fn() };

      conditionDialog = new AssemblyConditionDialog(modelService, worksheetClient, http,
         modalService, document);
      conditionDialog.model = createModel();
   });

   it("should use local storage to set advanced", () => {
      conditionDialog.model = <AssemblyConditionDialogModel> {
         advanced: false
      };

      let fakeLocalStorage = (id: string) => {
         if(id === "ws.condition") {
            return "advanced";
         }

         return null;
      };

      let localStorageSpy = jest.spyOn(LocalStorage, "getItem");
      localStorageSpy.mockImplementation(fakeLocalStorage);
      conditionDialog.updateModelFromLocalStorage();

      expect(localStorageSpy.mock.calls[0][0]).toEqual("ws.condition");
      expect(conditionDialog.model.advanced).toBeTruthy();
   });

   it("should check local storage and not change advanced", () => {
      conditionDialog.model = <AssemblyConditionDialogModel> {
         advanced: true
      };

      let fakeLocalStorage = (id: string) => {
         if(id === "ws.condition") {
            return "basic";
         }

         return null;
      };

      let localStorageSpy = jest.spyOn(LocalStorage, "getItem");
      localStorageSpy.mockImplementation(fakeLocalStorage);
      conditionDialog.updateModelFromLocalStorage();

      expect(localStorageSpy.mock.calls[0][0]).toEqual("ws.condition");
      expect(conditionDialog.model.advanced).toBeTruthy();
   });

   it("should set local storage when submitted", () => {
      conditionDialog.model = <AssemblyConditionDialogModel> {
         advanced: true
      };

      let localStorageSpy = jest.spyOn(LocalStorage, "setItem");
      conditionDialog.ok();
      expect(localStorageSpy).toHaveBeenCalled();
      expect(localStorageSpy.mock.calls[0][0]).toEqual("ws.condition");
      expect(localStorageSpy.mock.calls[0][1]).toEqual("advanced");
   });

   //Bug #15985 #17275 #17273 #17279 check the condition list validation
   it("check condition list is valid", () => {
      let condition1: Condition = TestUtils.createMockCondition();
      let isValid: boolean;
      condition1.field.attribute = "state";
      condition1.field.dataType = "string";

      //Bug #15985
      condition1.values = [{type: ConditionValueType.VALUE, value: null}];
      isValid = isValidConditionList([condition1]);
      expect(isValid).toBeFalsy();

      //Bug #17275
      condition1.values = [{type: ConditionValueType.VARIABLE, value: "$()"}];
      isValid = isValidConditionList([condition1]);
      expect(isValid).toBeFalsy();

      condition1.values = [{type: ConditionValueType.EXPRESSION, value: null}];
      isValid = isValidConditionList([condition1]);
      expect(isValid).toBeFalsy();

      //Bug #17279
      condition1.operation = ConditionOperation.BETWEEN;
      condition1.values = [{type: ConditionValueType.VALUE, value: "a"},
         {type: ConditionValueType.VALUE, value: null}];
      isValid = isValidConditionList([condition1]);
      expect(isValid).toBeFalsy();
   });
});