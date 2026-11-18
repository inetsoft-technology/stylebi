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
 * FormulaEditorDialog - `paneAggregateMode`, the "Calculate Field From" Detail/Aggregate choice the
 * paired agent has to see (bug #81628). Only a calculated field formula has the choice, and it
 * follows the radio live because the user can flip it after pairing.
 */
import { createDialog } from "./formula-editor-dialog.component.test-helpers";

function calcDialog(calcType: string, pushed: boolean = false) {
   const { comp, followFocusService } = createDialog();
   comp.runtimeId = "rt-1";
   comp.socketConnection = {} as any;
   comp.isCalc = true;
   comp.isVSContext = true;
   comp.contextTable = "Orders";
   comp.assemblyName = "Orders";
   comp.returnTypeVisible = true;
   comp.calcType = calcType;
   (followFocusService.pushFocus as any).mockReturnValue(pushed);
   comp.ngOnInit();
   return { comp, report: followFocusService.reportPaneState as any };
}

describe("FormulaEditorDialog - paneAggregateMode (bug #81628)", () => {
   it("is false while the calculated field dialog is on Detail", () => {
      const { comp } = calcDialog("detail");

      expect(comp.paneAggregateMode).toBe(false);
   });

   it("follows the Calculate Field From radio in both directions", () => {
      const { comp } = calcDialog("detail");
      const before = comp.editorContext;

      comp.form.get("calcType").setValue("aggregate");
      expect(comp.paneAggregateMode).toBe(true);

      comp.form.get("calcType").setValue("detail");
      expect(comp.paneAggregateMode).toBe(false);
      expect(comp.editorContext).toEqual(before);
      expect(Object.keys(comp.editorContext)).not.toContain("aggregate");
   });

   it("reports nothing for a location without the choice", () => {
      const { comp } = calcDialog("detail");
      comp.isCalc = false;
      comp.dynamicProperty = "visible";

      expect(comp.editorContext.kind).toBe("dynamicValue");
      expect(comp.paneAggregateMode).toBeUndefined();
   });

   it("reports the choice for a Follow Focus pushed context on every flip", () => {
      const { comp, report } = calcDialog("detail", true);
      const pushed = report.mock.calls[0][2];

      comp.form.get("calcType").setValue("aggregate");
      comp.form.get("calcType").setValue("detail");

      expect(report.mock.calls.map((c: any[]) => c[4])).toContain(true);
      expect(report.mock.calls[report.mock.calls.length - 1][4]).toBe(false);
      expect(report.mock.calls.every((c: any[]) => c[2] === pushed)).toBe(true);
   });
});
