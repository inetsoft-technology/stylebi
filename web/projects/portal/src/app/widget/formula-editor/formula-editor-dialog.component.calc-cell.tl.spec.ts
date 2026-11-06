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
 * FormulaEditorDialog - the Freehand (calc table cell) formula dialog's Connect to Claude
 * pairing (bug #80459): it mints a code scoped to the ONE selected cell, is hidden without a
 * single selected cell or without a transport, and reports agent writes to its host.
 */
import { Subject } from "rxjs";
import { ViewsheetCommandMessage } from "../../common/viewsheet-client";
import { createDialog } from "./formula-editor-dialog.component.test-helpers";

function calcTableDialog(contextCell: {row: number, col: number} | null,
                         withTransport = true)
{
   const { comp, ...rest } = createDialog();
   const commands = new Subject<ViewsheetCommandMessage>();
   comp.isCalcTable = true;
   comp.isCondition = true;
   comp.assemblyName = "Calc1";
   comp.expression = "$A + $B";

   if(withTransport) {
      comp.runtimeId = "Viewsheet1-clone";
      comp.socketConnection = { commands } as any;
   }

   comp.contextCell = contextCell;
   return { comp, commands, ...rest };
}

describe("FormulaEditorDialog - Freehand cell pairing (bug #80459)", () => {
   it("mints a calcTableCell context for the single selected cell, not the table script", () => {
      const { comp } = calcTableDialog({row: 2, col: 1});

      expect(comp.canPair).toBe(true);
      expect(comp.editorContext).toEqual({ kind: "calcTableCell", assembly: "Calc1", name: "2,1" });
   });

   it("hides the button when no single cell is selected (multi-selection)", () => {
      const { comp } = calcTableDialog(null);

      expect(comp.canPair).toBe(false);
   });

   it("hides the button without a pairing transport", () => {
      const { comp } = calcTableDialog({row: 0, col: 0}, false);

      expect(comp.canPair).toBe(false);
   });

   it("applies an agent push for exactly this cell and tells the host", () => {
      const { comp, commands } = calcTableDialog({row: 2, col: 1});
      const pushed: string[] = [];
      comp.agentScriptPushed.subscribe((t: string) => pushed.push(t));
      comp.ngOnInit();

      commands.next(new ViewsheetCommandMessage(null, "RefreshAgentScriptCommand", {
         kind: "calcTableCell", assembly: "Calc1", name: "2,1", text: "$A * 2",
      } as any));

      expect(comp.expression).toBe("$A * 2");
      expect(pushed).toEqual(["$A * 2"]);
   });

   it("ignores a push for another cell", () => {
      const { comp, commands } = calcTableDialog({row: 2, col: 1});
      const pushed: string[] = [];
      comp.agentScriptPushed.subscribe((t: string) => pushed.push(t));
      comp.ngOnInit();

      commands.next(new ViewsheetCommandMessage(null, "RefreshAgentScriptCommand", {
         kind: "calcTableCell", assembly: "Calc1", name: "0,0", text: "x",
      } as any));

      expect(comp.expression).toBe("$A + $B");
      expect(pushed).toEqual([]);
   });

   it("defers a push over unsaved human edits instead of overwriting them", () => {
      const { comp, commands } = calcTableDialog({row: 2, col: 1});
      comp.ngOnInit();
      comp.expression = "$A + $B + 1";

      commands.next(new ViewsheetCommandMessage(null, "RefreshAgentScriptCommand", {
         kind: "calcTableCell", assembly: "Calc1", name: "2,1", text: "$A * 2",
      } as any));

      expect(comp.expression).toBe("$A + $B + 1");
      expect(comp.staleExpression).toBe("$A * 2");
   });
});
