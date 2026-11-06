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
 * CalcDataPane.openFormulaEdit - Connect to Claude wiring for the Freehand formula dialog
 * (bug #80459): the dialog gets the binding editor's runtime and socket plus the single selected
 * cell, and an agent write reloads the pane's cached cell binding.
 */
import { EventEmitter } from "@angular/core";
import { TestUtils } from "../../../common/test/test-utils";
import {
   clientServiceMock,
   editorServiceMock,
   modalMock,
   renderCalcDataPane,
} from "./calc-data-pane.component.test-helpers";

function fakeDialog() {
   return {
      onCommit: new EventEmitter<any>(),
      onCancel: new EventEmitter<any>(),
      onApply: new EventEmitter<any>(),
      agentScriptPushed: new EventEmitter<string>(),
   } as any;
}

function openDialog(comp: any) {
   const dialog = fakeDialog();
   modalMock.open.mockReturnValue({ componentInstance: dialog, result: Promise.resolve() });
   comp.openFormulaEdit();
   return dialog;
}

describe("CalcDataPane.openFormulaEdit - Connect to Claude wiring (bug #80459)", () => {
   it("passes the pane runtime, the pane socket and the single selected cell", async () => {
      const cell = TestUtils.createMockCalcTableCell();
      cell.row = 3;
      cell.col = 2;
      const { comp } = await renderCalcDataPane({ vsId: "vs-clone" });
      editorServiceMock.getSelectCells.mockReturnValue([cell]);

      const dialog = openDialog(comp);

      expect(dialog.runtimeId).toBe("vs-clone");
      expect(dialog.socketConnection).toBe(clientServiceMock);
      expect(dialog.contextCell).toEqual({ row: 3, col: 2 });
      expect(dialog.isCalcTable).toBe(true);
   });

   it("omits the cell for a multi-cell selection so the button is hidden", async () => {
      const { comp } = await renderCalcDataPane();
      editorServiceMock.getSelectCells.mockReturnValue([
         TestUtils.createMockCalcTableCell(), TestUtils.createMockCalcTableCell()]);

      const dialog = openDialog(comp);

      expect(dialog.contextCell).toBeNull();
   });

   it("reloads the cached cell binding when an agent write reaches the open dialog", async () => {
      const { comp } = await renderCalcDataPane();
      const dialog = openDialog(comp);

      dialog.agentScriptPushed.emit("$A * 2");

      expect(editorServiceMock.invalidateCellBinding).toHaveBeenCalledTimes(1);
      expect(editorServiceMock.loadCellBinding).toHaveBeenCalledTimes(1);
   });
});
