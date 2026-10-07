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
import { OneOfVpmConditionEditor } from "./one-of-vpm-condition-editor.component";
import { ClauseValueModel } from "../../../../../data/model/datasources/database/vpm/condition/clause/clause-value-model";
import { ClauseValueTypes } from "../../../../../data/model/datasources/database/vpm/condition/clause/clause-value-types";

describe("OneOfVpmConditionEditor", () => {
   let editor: OneOfVpmConditionEditor;
   let valueModel: ClauseValueModel;

   beforeEach(() => {
      editor = new OneOfVpmConditionEditor();
      valueModel = { type: ClauseValueTypes.VALUE, expression: "(1,2)" };
      editor.valueModel = valueModel;
   });

   // the number value editor emits numbers at runtime
   const typeValue = (val: any) => editor.editingModel.expression = val;

   // Bug #77890
   it("should modify the selected value to 0", () => {
      editor.selectValue(0, "1");
      typeValue(0);

      editor.modify();

      expect(editor.values).toEqual(["0", "2"]);
      expect(valueModel.expression).toBe("(0,2)");
   });

   it("should modify the selected value to a number", () => {
      editor.selectValue(0, "1");
      typeValue(7);

      editor.modify();

      expect(editor.values).toEqual(["7", "2"]);
      expect(valueModel.expression).toBe("(7,2)");
   });

   it("should not modify the selected value to an empty value", () => {
      editor.selectValue(0, "1");
      typeValue("  ");

      editor.modify();

      expect(editor.values).toEqual(["1", "2"]);
      expect(valueModel.expression).toBe("(1,2)");
   });

   it("should add 0 as a string", () => {
      typeValue(0);

      editor.add();

      expect(editor.values).toEqual(["1", "2", "0"]);
      expect(valueModel.expression).toBe("(1,2,0)");
   });

   it("should reject a number that was already added as a number", () => {
      typeValue(0);
      editor.add();
      typeValue(0);

      editor.add();

      expect(editor.values).toEqual(["1", "2", "0"]);
   });

   it("should reject a number that is already in the list as a string", () => {
      typeValue(2);

      editor.add();

      expect(editor.values).toEqual(["1", "2"]);
   });
});
