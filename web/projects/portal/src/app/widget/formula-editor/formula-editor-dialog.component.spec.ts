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
import { of, throwError } from "rxjs";

import { ComponentTool } from "../../common/util/component-tool";
import { FormulaEditorDialog } from "./formula-editor-dialog.component";
import { TreeNodeModel } from "../tree/tree-node-model";

describe("FormulaEditorDialog Unit Test", () => {
   let dialog: FormulaEditorDialog;
   let editorService: any;
   let modalService: any;

   beforeEach(() => {
      editorService = {
         getColumnTreeNode: vi.fn()
      };
      modalService = { open: vi.fn() };

      dialog = new FormulaEditorDialog(editorService, modalService, null, null, null);
      dialog.vsId = "vs1";
      dialog.assemblyName = "Table1";
      dialog.isCube = false;
      dialog.isCondition = false;
   });

   afterEach(() => {
      vi.restoreAllMocks();
   });

   // Bug #76409 - editing a script-based Detail calc field from the Wizard left the
   // Formula Editor's right-hand column tree pane empty with no error surfaced, because
   // getColumnTreeNode() was subscribed to with no error callback. This verifies a failed
   // request now surfaces a visible error dialog instead of failing silently.
   it("should show an error dialog when getColumnTreeNode fails, instead of failing silently", () => {
      const showMessageDialog = vi.spyOn(ComponentTool, "showMessageDialog")
         .mockImplementation(() => Promise.resolve("ok"));
      editorService.getColumnTreeNode.mockReturnValue(throwError("Server error"));

      (dialog as any).populateColumnTree();

      expect(showMessageDialog).toHaveBeenCalledTimes(1);
      expect(showMessageDialog.mock.calls[0][1]).toBe("_#(js:Error)");
      expect(showMessageDialog.mock.calls[0][2]).toBe("Server error");

      // the pane state must not be silently left in a broken/half-populated state
      expect(dialog.columnTreeRoot).toBeUndefined();
   });

   it("should populate the column tree on success without showing an error dialog", () => {
      const showMessageDialog = vi.spyOn(ComponentTool, "showMessageDialog");
      const treeData: TreeNodeModel = <TreeNodeModel> {
         label: "root",
         leaf: false,
         children: []
      };
      editorService.getColumnTreeNode.mockReturnValue(of(treeData));

      (dialog as any).populateColumnTree();

      expect(showMessageDialog).not.toHaveBeenCalled();
      expect(dialog.columnTreeRoot).toBe(treeData);
   });
});
