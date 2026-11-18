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
import { ConditionOperation } from "../../common/data/condition/condition-operation";
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

describe("FormulaEditorDialog - paneSqlMode for worksheet dialogs (bug #81557)", () => {
   function worksheetDialog(configure: (comp: any) => void) {
      const { comp } = createDialog();
      comp.runtimeId = "rt-1";
      comp.socketConnection = {} as any;
      comp.isVSContext = false;
      comp.contextTable = "Orders";
      comp.assemblyName = "Orders";
      comp.formulaName = "Margin";
      comp.returnTypeVisible = true;
      configure(comp);
      comp.initForm();
      return comp;
   }

   it("reports the switch of a worksheet expression column and follows the radio", () => {
      const comp = worksheetDialog(c => c.formulaType = FormulaType.SCRIPT);

      expect(comp.editorContext.kind).toBe("worksheetExpression");
      expect(comp.paneSqlMode).toBe(false);

      comp.form.get("formulaType").setValue(FormulaType.SQL);
      expect(comp.paneSqlMode).toBe(true);
   });

   it("reports the switch of a worksheet condition's Expression value", () => {
      const comp = worksheetDialog(c => {
         c.isCondition = true;
         c.conditionOperation = ConditionOperation.GREATER_THAN;
         c.formulaType = FormulaType.SQL;
      });

      expect(comp.editorContext.kind).toBe("worksheetConditionValue");
      expect(comp.paneSqlMode).toBe(true);

      comp.form.get("formulaType").setValue(FormulaType.SCRIPT);
      expect(comp.paneSqlMode).toBe(false);
   });

   it("reports nothing for a worksheet variable default, which has no switch", () => {
      const comp = worksheetDialog(c => {
         c.pairingContext = { kind: "worksheetVariableDefault", assembly: "Var1" };
      });

      expect(comp.editorContext.kind).toBe("worksheetVariableDefault");
      expect(comp.paneSqlMode).toBeUndefined();
   });
});

describe("FormulaEditorDialog - paneSqlMode through Follow Focus (bug #81541)", () => {
   // A session paired from the toolbar and retargeted here never minted a code from this
   // dialog, so the connect component reports nothing for it; the dialog reports for the context
   // it pushed.
   function followingDialog(pushed: boolean) {
      const { comp, followFocusService } = createDialog();
      comp.runtimeId = "rt-1";
      comp.socketConnection = {} as any;
      comp.isCalc = true;
      comp.isVSContext = true;
      comp.contextTable = "Orders";
      comp.assemblyName = "Orders";
      comp.returnTypeVisible = true;
      (followFocusService.pushFocus as any).mockReturnValue(pushed);
      comp.ngOnInit();
      return { comp, report: followFocusService.reportPaneState as any };
   }

   it("reports the switch for the pushed context when the dialog opens", () => {
      const { comp, report } = followingDialog(true);

      expect(report).toHaveBeenCalledTimes(1);
      expect(report).toHaveBeenCalledWith("rt-1", comp.socketConnection, comp.editorContext, false,
         false);
   });

   it("reports every flip, under the context that was pushed even after a rename", () => {
      const { comp, report } = followingDialog(true);
      const pushed = report.mock.calls[0][2];

      comp.formulaName = "Renamed";
      comp.form.get("formulaType").setValue(FormulaType.SQL);
      comp.form.get("formulaType").setValue(FormulaType.SCRIPT);

      expect(report.mock.calls.map((c: any[]) => c[3])).toEqual([false, true, false]);
      expect(report.mock.calls.every((c: any[]) => c[2] === pushed)).toBe(true);
      expect(pushed.name).toBe("CalcField1");
   });

   it("reports nothing when Follow Focus did not push this dialog", () => {
      const { comp, report } = followingDialog(false);

      comp.form.get("formulaType").setValue(FormulaType.SQL);

      expect(report).not.toHaveBeenCalled();
   });

   it("reports nothing for a pushed location without the switch", () => {
      const { comp, followFocusService } = createDialog();
      comp.runtimeId = "rt-1";
      comp.socketConnection = {} as any;
      comp.isVSContext = true;
      comp.contextTable = "Text1";
      comp.assemblyName = "Text1";
      comp.dynamicProperty = "visible";
      (followFocusService.pushFocus as any).mockReturnValue(true);
      comp.ngOnInit();

      expect(followFocusService.pushFocus).toHaveBeenCalled();
      expect(followFocusService.reportPaneState).not.toHaveBeenCalled();
   });
});
