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
import { VPMConditionItemPane } from "./vpm-condition-item-pane.component";
import { ClauseModel } from "../../../../data/model/datasources/database/vpm/condition/clause/clause-model";
import { ClauseValueModel } from "../../../../data/model/datasources/database/vpm/condition/clause/clause-value-model";
import { ClauseValueTypes } from "../../../../data/model/datasources/database/vpm/condition/clause/clause-value-types";
import { DataConditionItemPaneProvider } from "../../../../data/model/datasources/database/vpm/condition/clause/data-condition-item-pane-provider";
import { OperationModel } from "../../../../data/model/datasources/database/vpm/condition/clause/operation-model";
import { isValidCondition } from "../../../../data/model/datasources/database/vpm/condition/util/vpm-condition.util";
import { XSchema } from "../../../../../common/data/xschema";

describe("VPMConditionItemPane", () => {
   const operations: OperationModel[] = [
      { name: "equal to", symbol: "=" },
      { name: "one of", symbol: "IN" },
      { name: "greater than", symbol: ">" }
   ];

   const field = (type: string): ClauseValueModel => ({
      type: ClauseValueTypes.FIELD,
      expression: "ORDERS.QUANTITY",
      field: { name: "ORDERS.QUANTITY", type, columnName: "QUANTITY", tableName: "ORDERS" }
   });

   // the number and boolean value editors emit numbers and booleans at runtime
   const value = (expression: any): ClauseValueModel =>
      ({ type: ClauseValueTypes.VALUE, expression });

   const clause = (fieldType: string, value2: ClauseValueModel): ClauseModel => ({
      type: "clause",
      junc: false,
      level: 0,
      negated: false,
      operation: { name: "equal to", symbol: "=" },
      value1: field(fieldType),
      value2,
      value3: value(null)
   });

   let pane: VPMConditionItemPane;
   let emitted: ClauseModel[];

   const setUp = (condition: ClauseModel) => {
      pane = new VPMConditionItemPane();
      pane.provider = new DataConditionItemPaneProvider(null, "ds", "partition", operations, []);
      pane.condition = condition;
      pane.ngOnInit();
      emitted = [];
      pane.conditionChange.subscribe((c: ClauseModel) => emitted.push(c));
   };

   // same sequence as the operator <select>: [(ngModel)]="optSymbol" then (ngModelChange)
   const changeOperator = (symbol: string) => {
      pane.optSymbol = symbol;
      pane.optionChange(symbol);
   };

   // Bug #77890
   it("should keep an integer 0 when the operator changes", () => {
      setUp(clause(XSchema.INTEGER, value(0)));

      changeOperator(">");

      expect(pane.condition.value2.expression + "").toBe("0");
      expect(isValidCondition(pane.condition)).toBe(true);
      expect(emitted.length).toBe(1);
   });

   it("should keep a non-zero number and notify the parent when the operator changes", () => {
      setUp(clause(XSchema.INTEGER, value(5)));

      expect(() => changeOperator(">")).not.toThrow();

      expect(pane.condition.value2.expression).toBe("5");
      expect(pane.condition.operation.symbol).toBe(">");
      expect(emitted.length).toBe(1);
   });

   it("should wrap a typed number in parentheses when the operator changes to IN", () => {
      setUp(clause(XSchema.INTEGER, value(5)));

      changeOperator("IN");

      expect(pane.condition.value2.expression).toBe("(5)");
      expect(emitted.length).toBe(1);
   });

   it("should wrap an integer 0 in parentheses when the operator changes to IN", () => {
      setUp(clause(XSchema.INTEGER, value(0)));

      changeOperator("IN");

      expect(pane.condition.value2.expression).toBe("(0)");
   });

   it("should keep a decimal number when the operator changes", () => {
      setUp(clause(XSchema.DOUBLE, value(1.5)));

      changeOperator(">");

      expect(pane.condition.value2.expression).toBe("1.5");
   });

   it("should keep a boolean true when the operator changes", () => {
      setUp(clause(XSchema.BOOLEAN, value(true)));

      expect(() => changeOperator("IN")).not.toThrow();

      expect(pane.condition.value2.expression).toBe("(true)");
      expect(emitted.length).toBe(1);
   });

   it("should keep a boolean false when the operator changes", () => {
      setUp(clause(XSchema.BOOLEAN, value(false)));

      changeOperator(">");

      expect(pane.condition.value2.expression).toBe("false");
   });

   it("should take the first value of a list when the operator changes from IN", () => {
      setUp(clause(XSchema.INTEGER, value("(1,2)")));
      pane.condition.operation = { name: "one of", symbol: "IN" };

      changeOperator("=");

      expect(pane.condition.value2.expression).toBe("1");
   });

   it("should keep a string 0 when the operator changes", () => {
      setUp(clause(XSchema.STRING, value("0")));

      changeOperator(">");

      expect(pane.condition.value2.expression).toBe("0");
   });

   it("should leave an empty value empty when the operator changes", () => {
      setUp(clause(XSchema.INTEGER, value("")));

      changeOperator(">");

      expect(pane.condition.value2.expression).toBeNull();
      expect(isValidCondition(pane.condition)).toBe(false);
   });
});
