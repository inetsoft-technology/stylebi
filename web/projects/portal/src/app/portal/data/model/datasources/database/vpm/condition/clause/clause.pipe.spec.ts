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
import { ClauseModel } from "./clause-model";
import { ClausePipe } from "./clause.pipe";
import { ClauseValueModel } from "./clause-value-model";
import { ClauseValueTypes } from "./clause-value-types";

describe("ClausePipe", () => {
   let pipe: ClausePipe;

   const field = (name: string): ClauseValueModel =>
      ({ type: ClauseValueTypes.FIELD, expression: name });

   // the number/boolean value editors emit non-string values at runtime
   const value = (expression: any): ClauseValueModel =>
      ({ type: ClauseValueTypes.VALUE, expression });

   const clause = (symbol: string, value2: ClauseValueModel,
                   value3: ClauseValueModel = value(null), level: number = 0): ClauseModel => ({
      type: "clause",
      junc: false,
      level,
      negated: false,
      operation: { name: symbol, symbol },
      value1: field("ORDERS.DISCOUNT"),
      value2,
      value3
   });

   beforeEach(() => {
      pipe = new ClausePipe();
   });

   // Bug #77881: a value of 0 was dropped from the condition list
   it("should render a numeric value of 0", () => {
      expect(pipe.transform(clause(">", value(0)))).toBe("ORDERS.DISCOUNT > 0");
      expect(pipe.transform(clause("=", value(0)))).toBe("ORDERS.DISCOUNT = 0");
   });

   it("should render a boolean value of false", () => {
      expect(pipe.transform(clause("=", value(false)))).toBe("ORDERS.DISCOUNT = false");
   });

   it("should render 0 as the upper bound of BETWEEN", () => {
      expect(pipe.transform(clause("BETWEEN", value(-5), value(0))))
         .toBe("ORDERS.DISCOUNT BETWEEN -5 and 0");
   });

   it("should render a string value of \"0\"", () => {
      expect(pipe.transform(clause(">", value("0")))).toBe("ORDERS.DISCOUNT > 0");
   });

   it("should render normal values unchanged", () => {
      expect(pipe.transform(clause("=", value(1)))).toBe("ORDERS.DISCOUNT = 1");
      expect(pipe.transform(clause("=", value(true)))).toBe("ORDERS.DISCOUNT = true");
      expect(pipe.transform(clause("LIKE", value("abc%")))).toBe("ORDERS.DISCOUNT LIKE abc%");
      expect(pipe.transform(clause("=", field("ORDERS.PAID")))).toBe("ORDERS.DISCOUNT = ORDERS.PAID");
   });

   it("should render null, undefined and empty values as empty", () => {
      expect(pipe.transform(clause(">", value(null)))).toBe("ORDERS.DISCOUNT > ");
      expect(pipe.transform(clause(">", value(undefined)))).toBe("ORDERS.DISCOUNT > ");
      expect(pipe.transform(clause(">", value("")))).toBe("ORDERS.DISCOUNT > ");
      expect(pipe.transform(clause("BETWEEN", value(1), value(null))))
         .toBe("ORDERS.DISCOUNT BETWEEN 1");
      expect(pipe.transform(clause("BETWEEN", value(1), value(""))))
         .toBe("ORDERS.DISCOUNT BETWEEN 1");
   });

   it("should render unary operations, negation and level indent", () => {
      expect(pipe.transform(clause("IS NULL", value(0)))).toBe("ORDERS.DISCOUNT IS NULL");
      const c = clause(">", value(0), value(null), 2);
      c.negated = true;
      expect(pipe.transform(c)).toBe("........not ORDERS.DISCOUNT > 0");
   });

   it("should render a subquery and an empty subquery", () => {
      const sub: ClauseValueModel = {
         type: ClauseValueTypes.SUBQUERY,
         query: { simpleModel: { sqlString: "select 1" } } as any
      };
      expect(pipe.transform(clause("IN", sub))).toBe("ORDERS.DISCOUNT IN (select 1)");
      expect(pipe.transform(clause("IN", { type: ClauseValueTypes.SUBQUERY })))
         .toBe("ORDERS.DISCOUNT IN ");
   });
});
