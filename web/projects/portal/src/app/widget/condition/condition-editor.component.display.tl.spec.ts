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
 * ConditionEditor - Pass 3: Display
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - selectType default value dispatch for FIELD/VARIABLE/EXPRESSION/SUBQUERY/VALUE
 *   Group 2 [Risk 3] - getChoiceQuery MODEL/ASSET/query/empty dispatch
 *   Group 3 [Risk 2] - source-null and embedded-source updateChoiceQuery guards plus getSelectValues
 *
 * Direct instantiation - pure branch coverage only.
 */

import { ConditionValueType } from "../../common/data/condition/condition-value-type";
import { ExpressionType } from "../../common/data/condition/expression-type";
import { DataRef } from "../../common/data/data-ref";
import { XSchema } from "../../common/data/xschema";
import { SourceInfo } from "../../binding/data/source-info";
import { ConditionEditor } from "./condition-editor.component";

afterEach(() => vi.restoreAllMocks());

describe("ConditionEditor - selectType default dispatch [Group 1, Risk 3]", () => {
   it("should seed FIELD VARIABLE EXPRESSION and SUBQUERY values", () => {
      const { comp, firstField } = createEditor();

      comp.selectType(ConditionValueType.FIELD);
      expect(comp.value).toEqual({
         value: firstField,
         type: ConditionValueType.FIELD,
         choiceQuery: null
      });

      comp.selectType(ConditionValueType.VARIABLE);
      expect(comp.value).toEqual({
         value: "$()",
         type: ConditionValueType.VARIABLE,
         choiceQuery: null
      });

      comp.selectType(ConditionValueType.EXPRESSION);
      expect(comp.value).toEqual({
         value: {
            expression: null,
            type: ExpressionType.SQL
         },
         type: ConditionValueType.EXPRESSION,
         choiceQuery: null
      });

      comp.selectType(ConditionValueType.SUBQUERY);
      expect(comp.value).toEqual({
         value: {
            query: "",
            attribute: null,
            subAttribute: null,
            mainAttribute: null
         },
         type: ConditionValueType.SUBQUERY,
         choiceQuery: null
      });
   });

   it("should use null as the default value for types without a specialized seed", () => {
      const { comp } = createEditor();
      comp.value = {
         value: "$()",
         type: ConditionValueType.VARIABLE,
         choiceQuery: "old"
      };

      comp.selectType(ConditionValueType.VALUE);

      expect(comp.value).toEqual({
         value: null,
         type: ConditionValueType.VALUE,
         choiceQuery: null
      });
   });
});

describe("ConditionEditor - getChoiceQuery dispatch [Group 2, Risk 3]", () => {
   it("should build model choice queries with source prefix and entity attribute", () => {
      const { comp } = createEditor();
      comp.source = {
         source: "Sales",
         prefix: "OrdersModel",
         type: SourceInfo.MODEL
      } as SourceInfo;

      expect((comp as any).getChoiceQuery()).toBe("[Sales::OrdersModel].[Orders::state]");
   });

   it("should build asset choice queries with caret separator", () => {
      const { comp } = createEditor();
      comp.source = {
         source: "/Assets/Query1",
         type: SourceInfo.ASSET
      } as SourceInfo;

      expect((comp as any).getChoiceQuery()).toBe("[/Assets/Query1]^[Orders.state]");
   });

   it("should build regular source choice queries and return empty for missing source or field names", () => {
      const { comp } = createEditor();
      comp.source = {
         source: "DataSource",
         type: SourceInfo.QUERY
      } as SourceInfo;

      expect((comp as any).getChoiceQuery()).toBe("[DataSource].[Orders.state]");

      comp.source = null;
      expect((comp as any).getChoiceQuery()).toBe("");

      comp.source = { source: "DataSource", type: SourceInfo.QUERY } as SourceInfo;
      comp.field = { ...comp.field, name: null };
      expect((comp as any).getChoiceQuery()).toBe("");
   });
});

describe("ConditionEditor - choice-query guards and select values [Group 3, Risk 2]", () => {
   it("should map values to select values without mutating the backing entries", () => {
      const { comp } = createEditor();

      expect(comp.getSelectValues()).toEqual([]);

      comp.values = [
         { value: "A", type: ConditionValueType.VALUE },
         { value: "B", type: ConditionValueType.VARIABLE }
      ];

      expect(comp.getSelectValues()).toEqual(["A", "B"]);
   });

   it("should build source-null choiceQuery from table and field name", () => {
      const { comp } = createEditor();
      comp.source = null;
      comp.table = "Orders";

      comp.updateChoiceQuery(true);

      expect(comp.value.choiceQuery).toBe("Orders]:[Orders.state");

      comp.field = {
         ...comp.field,
         name: "Orders].[state"
      };
      comp.updateChoiceQuery(true);

      expect(comp.value.choiceQuery).toBe("Orders].[state");
   });

   it("should clear choiceQuery for embedded data sources even when useList is requested", () => {
      const { comp } = createEditor();
      comp.value.choiceQuery = "old";
      comp.source = {
         source: "Embedded",
         type: SourceInfo.EMBEDDED_DATA
      } as SourceInfo;

      comp.updateChoiceQuery(true);

      expect(comp.value.choiceQuery).toBeNull();
   });
});

function createEditor() {
   const firstField = makeField("Orders.state");
   const comp = new ConditionEditor();

   comp.valueTypes = [
      ConditionValueType.VALUE,
      ConditionValueType.VARIABLE,
      ConditionValueType.EXPRESSION,
      ConditionValueType.FIELD,
      ConditionValueType.SUBQUERY,
      ConditionValueType.SESSION_DATA
   ];
   comp.expressionTypes = [ExpressionType.SQL];
   comp.fieldsModel = {
      list: [firstField],
      tree: { children: [] }
   };
   comp.field = firstField;
   comp.table = "Orders";
   comp.value = {
      value: "",
      type: ConditionValueType.VALUE,
      choiceQuery: null
   };
   comp.values = [];
   comp.fieldsDropdown = { close: vi.fn() } as any;

   return { comp, firstField };
}

function makeField(name: string, type: string = XSchema.STRING): DataRef {
   const parts = name.split(".");

   return {
      classType: "ColumnRef",
      name,
      view: name,
      entity: parts[0],
      attribute: parts[1],
      dataType: type
   };
}
