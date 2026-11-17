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
 * FormulaEditorDialog - `paneSqlMode`, the SQL/Script switch the paired agent has to see (bug
 * #81541). Only a calculated field formula has the switch; every other location this dialog
 * serves is script, so it reports nothing for them. The value follows the radio live, because the
 * user can flip it after pairing.
 */
import { FormulaType } from "../../common/data/formula-type";
import { createDialog } from "./formula-editor-dialog.component.test-helpers";

function calcDialog() {
   const { comp } = createDialog();
   comp.runtimeId = "rt-1";
   comp.socketConnection = {} as any;
   comp.isCalc = true;
   comp.isVSContext = true;
   comp.contextTable = "Orders";
   comp.assemblyName = "Orders";
   comp.returnTypeVisible = true;
   comp.initForm();
   return comp;
}

describe("FormulaEditorDialog - paneSqlMode (bug #81541)", () => {
   it("is false while the calculated field dialog is on Script", () => {
      const comp = calcDialog();

      expect(comp.editorContext.kind).toBe("calcField");
      expect(comp.paneSqlMode).toBe(false);
   });

   it("follows the SQL/Script radio in both directions without a new context", () => {
      const comp = calcDialog();
      const before = comp.editorContext;

      comp.form.get("formulaType").setValue(FormulaType.SQL);
      expect(comp.paneSqlMode).toBe(true);

      comp.form.get("formulaType").setValue(FormulaType.SCRIPT);
      expect(comp.paneSqlMode).toBe(false);
      expect(comp.editorContext).toEqual(before);
   });

   it("keeps the switch out of the editorContext the server matches for equality", () => {
      const comp = calcDialog();
      const script = comp.editorContext;

      comp.form.get("formulaType").setValue(FormulaType.SQL);

      expect(comp.editorContext).toEqual(script);
      expect(Object.keys(comp.editorContext)).not.toContain("sql");
   });

   it("reports nothing for a location without the switch", () => {
      const comp = calcDialog();
      comp.isCalc = false;
      comp.dynamicProperty = "visible";

      expect(comp.editorContext.kind).toBe("dynamicValue");
      expect(comp.paneSqlMode).toBeUndefined();
   });

   it("reports nothing when the dialog cannot pair at all", () => {
      const comp = calcDialog();
      comp.socketConnection = undefined;

      expect(comp.editorContext).toBeNull();
      expect(comp.paneSqlMode).toBeUndefined();
   });
});
