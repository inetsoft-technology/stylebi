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
 * OneOfConditionEditor — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — add / remove: emit contract, dedup, empty list reset
 *   Group 2 [Risk 2] — modify / valueChanged: special-type propagation
 *   Group 3 [Risk 2] — isSelected / isEmpty / isSpecialType guards
 *
 * Direct instantiation — ConditionEditor child not rendered.
 */

import { ConditionValue } from "../../common/data/condition/condition-value";
import { ConditionValueType } from "../../common/data/condition/condition-value-type";
import { DataRef } from "../../common/data/data-ref";
import { XSchema } from "../../common/data/xschema";
import { OneOfConditionEditor } from "./one-of-condition-editor.component";

function createEditor(fieldType = XSchema.STRING) {
   const comp = new OneOfConditionEditor();
   comp.field = { dataType: fieldType, attribute: "col" } as DataRef;
   comp.values = [];
   comp.value = { value: "", type: ConditionValueType.VALUE };
   return comp;
}

function valueEntry(val: string, classType = "ColumnRef"): ConditionValue {
   return {
      value: { classType, attribute: val },
      type: ConditionValueType.VALUE,
   } as ConditionValue;
}

describe("OneOfConditionEditor — add and remove [Group 1, Risk 3]", () => {

   it("should emit valuesChange when adding a new value", () => {
      const comp = createEditor();
      comp.value = valueEntry("A");
      const emitSpy = vi.spyOn(comp.valuesChange, "emit");

      comp.add();

      expect(comp.values).toHaveLength(1);
      expect(emitSpy).toHaveBeenCalledWith(comp.values);
   });

   it("should not emit when adding a duplicate value", () => {
      const comp = createEditor();
      comp.values = [valueEntry("A")];
      comp.value = valueEntry("A");
      const emitSpy = vi.spyOn(comp.valuesChange, "emit");

      comp.add();

      expect(comp.values).toHaveLength(1);
      expect(emitSpy).not.toHaveBeenCalled();
   });

   it("should filter out values with mismatched classType before add", () => {
      const comp = createEditor();
      comp.values = [valueEntry("old", "AggregateRef")];
      comp.value = valueEntry("new", "ColumnRef");
      vi.spyOn(comp.valuesChange, "emit");

      comp.add();

      expect(comp.values).toHaveLength(1);
      expect(comp.values[0].value.attribute).toBe("new");
   });

   // 🔁 Regression-sensitive: Bug #18994 — removing all items resets editor state
   it("should reset value and selectedIndex when last item is removed", () => {
      const comp = createEditor();
      comp.values = [valueEntry("only")];
      comp.selectedIndex = 0;
      comp.selectValues = [comp.values[0]];
      const emitSpy = vi.spyOn(comp.valuesChange, "emit");

      comp.remove();

      expect(comp.values).toHaveLength(0);
      expect(comp.selectedIndex).toBe(-1);
      expect(comp.selectValues).toEqual([]);
      expect(comp.value.value).toBe("");
      expect(emitSpy).toHaveBeenCalledWith([]);
   });
});

describe("OneOfConditionEditor — modify and valueChanged [Group 2, Risk 2]", () => {

   it("should skip modify when value object is the same reference as another entry", () => {
      const comp = createEditor();
      comp.values = [valueEntry("A"), valueEntry("B")];
      comp.selectedIndex = 0;
      comp.value = comp.values[1];
      const emitSpy = vi.spyOn(comp.valuesChange, "emit");

      comp.modify();

      expect(comp.values[0].value.attribute).toBe("A");
      expect(emitSpy).not.toHaveBeenCalled();
   });

   it("should emit valuesChange when modify updates selected entry", () => {
      const comp = createEditor();
      comp.values = [valueEntry("A"), valueEntry("B")];
      comp.selectedIndex = 0;
      comp.value = valueEntry("C");
      const emitSpy = vi.spyOn(comp.valuesChange, "emit");

      comp.modify();

      expect(comp.values[0].value.attribute).toBe("C");
      expect(emitSpy).toHaveBeenCalledWith(comp.values);
   });

   it("should replace values with a single special-type entry on valueChanged", () => {
      const comp = createEditor();
      comp.values = [valueEntry("A"), valueEntry("B")];
      comp.value = {
         value: { name: "var1" },
         type: ConditionValueType.VARIABLE,
      } as ConditionValue;
      const emitSpy = vi.spyOn(comp.valuesChange, "emit");

      comp.valueChanged();

      expect(comp.values).toHaveLength(1);
      expect(comp.values[0].type).toBe(ConditionValueType.VARIABLE);
      expect(emitSpy).toHaveBeenCalledWith(comp.values);
   });

   it("should clear values when switching from special type to value type", () => {
      const comp = createEditor();
      comp.values = [{
         value: { name: "var1" },
         type: ConditionValueType.VARIABLE,
      } as ConditionValue];
      comp.value = { value: "plain", type: ConditionValueType.VALUE } as ConditionValue;
      const emitSpy = vi.spyOn(comp.valuesChange, "emit");

      comp.valueChanged();

      expect(comp.values).toEqual([]);
      expect(emitSpy).toHaveBeenCalledWith([]);
   });
});

describe("OneOfConditionEditor — selection helpers [Group 3, Risk 2]", () => {

   it("should report isSelected for matching values", () => {
      const comp = createEditor();
      const entry = valueEntry("X");
      comp.values = [entry];
      comp.selectValues = [entry];

      expect(comp.isSelected(entry)).toBe(true);
      expect(comp.isSelected(valueEntry("Y"))).toBe(false);
   });

   it("should treat null expression as empty for expression type", () => {
      const comp = createEditor();

      expect(comp.isEmpty({
         type: ConditionValueType.EXPRESSION,
         value: { expression: null },
      } as ConditionValue)).toBe(true);

      expect(comp.isEmpty({
         type: ConditionValueType.EXPRESSION,
         value: { expression: "col > 1" },
      } as ConditionValue)).toBe(false);
   });

   it("should classify variable subquery and session data as special types", () => {
      const comp = createEditor();

      expect(comp.isSpecialType(ConditionValueType.VARIABLE)).toBe(true);
      expect(comp.isSpecialType(ConditionValueType.SUBQUERY)).toBe(true);
      expect(comp.isSpecialType(ConditionValueType.SESSION_DATA)).toBe(true);
      expect(comp.isSpecialType(ConditionValueType.VALUE)).toBe(false);
   });
});
