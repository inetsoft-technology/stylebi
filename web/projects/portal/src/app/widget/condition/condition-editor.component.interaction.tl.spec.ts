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
 * ConditionEditor - Pass 1: Interaction
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - ngOnChanges defaulting and invalid session-data reset
 *   Group 2 [Risk 3] - selectType, valueChanged, conditionValueChanged, conditionValuesChanged emit contracts
 *   Group 3 [Risk 2] - browse dropdown close and updateChoiceQuery interaction paths
 *
 * Direct instantiation - child editors are not rendered.
 */

import { ConditionValueType } from "../../common/data/condition/condition-value-type";
import { ExpressionType } from "../../common/data/condition/expression-type";
import { DataRef } from "../../common/data/data-ref";
import { XSchema } from "../../common/data/xschema";
import { SourceInfo } from "../../binding/data/source-info";
import { ConditionEditor } from "./condition-editor.component";

afterEach(() => vi.restoreAllMocks());

describe("ConditionEditor - lifecycle defaults [Group 1, Risk 3]", () => {
   it("should initialize a missing value from the first allowed value type", () => {
      const { comp } = createEditor();
      comp.value = null;

      comp.ngOnChanges({});

      expect(comp.value).toEqual({
         value: "",
         type: ConditionValueType.VALUE,
         choiceQuery: null
      });
   });

   it("should clear session-data values when the selected field is not string", () => {
      const { comp } = createEditor();
      comp.value = {
         value: "session.user",
         type: ConditionValueType.SESSION_DATA,
         choiceQuery: "old"
      };
      comp.field = makeField("Orders.amount", XSchema.INTEGER);

      comp.ngOnChanges({ field: {} as any });

      expect(comp.value).toEqual({
         value: "",
         type: ConditionValueType.VALUE,
         choiceQuery: null
      });
   });
});

describe("ConditionEditor - value mutation emits [Group 2, Risk 3]", () => {
   it("should switch value type and emit the new value object", () => {
      const { comp } = createEditor();
      const emitSpy = vi.spyOn(comp.valueChange, "emit");

      comp.selectType(ConditionValueType.VARIABLE);

      expect(comp.value).toEqual({
         value: "$()",
         type: ConditionValueType.VARIABLE,
         choiceQuery: null
      });
      expect(emitSpy).toHaveBeenCalledWith(comp.value);
   });

   it("should not emit when selectType receives the current type", () => {
      const { comp } = createEditor();
      const emitSpy = vi.spyOn(comp.valueChange, "emit");

      comp.selectType(ConditionValueType.VALUE);

      expect(emitSpy).not.toHaveBeenCalled();
   });

   it("should skip empty-to-empty value edits and emit non-empty edits", () => {
      const { comp } = createEditor();
      const emitSpy = vi.spyOn(comp.valueChange, "emit");

      comp.conditionValueChanged("");
      expect(emitSpy).not.toHaveBeenCalled();

      comp.conditionValueChanged("CA");
      expect(comp.value.value).toBe("CA");
      expect(emitSpy).toHaveBeenCalledWith(comp.value);
   });

   it("should map multi-select values and preserve known existing value types", () => {
      const { comp } = createEditor();
      comp.values = [
         {
            value: { value: "Orders.state" },
            type: ConditionValueType.FIELD,
            choiceQuery: null
         },
         {
            value: "old",
            type: ConditionValueType.VARIABLE,
            choiceQuery: null
         }
      ];
      const emitSpy = vi.spyOn(comp.valueChanges, "emit");

      comp.conditionValuesChanged(["Orders.state", "literal"]);

      expect(comp.values).toEqual([
         { value: "Orders.state", type: ConditionValueType.FIELD, choiceQuery: null },
         { value: "literal", type: ConditionValueType.VALUE, choiceQuery: null }
      ]);
      expect(emitSpy).toHaveBeenCalledWith(comp.values);
   });
});

describe("ConditionEditor - dropdown and choice query interactions [Group 3, Risk 2]", () => {
   it("should hide browse data when the type dropdown opens or closes", () => {
      const { comp, valueEditor } = createEditor();

      comp.openChange(true);
      comp.openChange(false);

      expect(valueEditor.hideBrowse).toHaveBeenCalledTimes(2);
   });

   it("should close the fixed dropdown when a nested editor opens browse", () => {
      const { comp, dropdown } = createEditor();

      comp.closeDropDown();

      expect(dropdown.close).toHaveBeenCalled();
   });

   it("should update and clear choiceQuery from use-list changes", () => {
      const { comp } = createEditor();
      const emitSpy = vi.spyOn(comp.valueChange, "emit");

      comp.source = {
         source: "DataSource",
         type: SourceInfo.QUERY
      } as SourceInfo;
      comp.updateChoiceQuery(true);

      expect(comp.value.choiceQuery).toBe("[DataSource].[Orders.state]");
      expect(emitSpy).toHaveBeenCalledWith(comp.value);

      comp.updateChoiceQuery(false);
      expect(comp.value.choiceQuery).toBeNull();
   });
});

function createEditor() {
   const comp = new ConditionEditor();
   const dropdown = { close: vi.fn() };
   const valueEditor = { hideBrowse: vi.fn() };

   comp.valueTypes = [
      ConditionValueType.VALUE,
      ConditionValueType.VARIABLE,
      ConditionValueType.EXPRESSION,
      ConditionValueType.FIELD,
      ConditionValueType.SUBQUERY,
      ConditionValueType.SESSION_DATA
   ];
   comp.expressionTypes = [ExpressionType.JS, ExpressionType.SQL];
   comp.fieldsModel = {
      list: [makeField()],
      tree: { children: [] }
   };
   comp.field = makeField();
   comp.table = "Orders";
   comp.value = {
      value: "",
      type: ConditionValueType.VALUE,
      choiceQuery: null
   };
   comp.values = [];
   comp.fieldsDropdown = dropdown as any;
   comp.valueEditor = valueEditor as any;

   return { comp, dropdown, valueEditor };
}

function makeField(name: string = "Orders.state", type: string = XSchema.STRING): DataRef {
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
