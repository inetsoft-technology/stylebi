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
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ConditionValueType } from "../../common/data/condition/condition-value-type";
import { OneOfConditionEditor } from "./one-of-condition-editor.component";

describe("OneOfConditionEditor add() without a field", () => {
   // Bug #77902: in the grouping condition dialog the field combo starts empty, so
   // the editor's field is null while values are added to the "one of" list.
   it("should add successive values and reset the editor when no field is chosen", () => {
      const comp = new OneOfConditionEditor({} as NgbModal);
      comp.field = null;
      comp.values = [];
      comp.value = { value: "1", type: ConditionValueType.VALUE };
      const emitSpy = vi.spyOn(comp.valuesChange, "emit");

      expect(() => comp.add()).not.toThrow();
      expect(comp.values.map(v => v.value)).toEqual(["1"]);

      // the editor must hold a fresh value, not the one now in the list
      expect(comp.value).not.toBe(comp.values[0]);
      comp.value.value = "2";

      expect(() => comp.add()).not.toThrow();
      expect(comp.values.map(v => v.value)).toEqual(["1", "2"]);
      expect(emitSpy).toHaveBeenCalledTimes(2);
   });
});
