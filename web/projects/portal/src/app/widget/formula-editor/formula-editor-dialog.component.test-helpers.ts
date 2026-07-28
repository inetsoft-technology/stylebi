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

import { ElementRef, Renderer2, TemplateRef } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { of } from "rxjs";
import { FormulaType } from "../../common/data/formula-type";
import { ActionsContextmenuComponent } from "../fixed-dropdown/actions-contextmenu.component";
import { FixedDropdownService } from "../fixed-dropdown/fixed-dropdown.service";
import { TreeNodeModel } from "../tree/tree-node-model";
import {
   flushMicrotasks,
   syncReject,
   syncResolve,
} from "../../../testing/tl-async.util";
import { FormulaEditorDialog } from "./formula-editor-dialog.component";
import { FormulaEditorService } from "./formula-editor.service";

export { syncResolve, syncReject } from "../../../testing/tl-async.util";

/** @deprecated Prefer flushMicrotasks from testing/tl-async.util — alias kept for local imports. */
export const flushPromises = flushMicrotasks;

export function createDialog() {
   const editorService = {
      getColumnTreeNode: vi.fn(() => of({ children: [] } as TreeNodeModel)),
      getFunctionTreeNode: vi.fn(() => of({ children: [] } as TreeNodeModel)),
      getOperationTreeNode: vi.fn(() => of({ children: [] } as TreeNodeModel)),
      getScriptDefinitions: vi.fn(() => of({ defs: "vs" })),
      getTaskScriptDefinitions: vi.fn(() => of({ defs: "task" })),
   };
   // Default open() must return a settled sync result so accidental showMessageDialog
   // calls cannot leave Zone waiting on an unresolved modal promise.
   const modalService = {
      open: vi.fn(() => ({
         componentInstance: {
            options: null,
            title: null,
            message: null,
            onCommit: { subscribe: () => ({ unsubscribe() {} }) },
            onCancel: { subscribe: () => ({ unsubscribe() {} }) },
         },
         result: syncResolve("ok"),
         close: vi.fn(),
         dismiss: vi.fn(),
      })),
   };
   const dropdownService = {
      open: vi.fn(() => ({
         componentInstance: {} as ActionsContextmenuComponent,
      })),
   };
   const renderer = {
      createElement: vi.fn(() => document.createElement("div")),
      addClass: vi.fn(),
      appendChild: vi.fn(),
      listen: vi.fn(() => vi.fn()),
   };
   const comp = new FormulaEditorDialog(
      editorService as unknown as FormulaEditorService,
      modalService as unknown as NgbModal,
      renderer as unknown as Renderer2,
      { nativeElement: document.createElement("form") } as ElementRef,
      dropdownService as unknown as FixedDropdownService,
   );
   comp.formulaName = "CalcField1";
   comp.formulaType = FormulaType.SCRIPT;
   comp.dataType = "string";
   comp.expression = "1 + 1";
   comp.columnTreeRoot = {
      label: "Fields",
      children: [
         { label: "State", leaf: true },
         { label: "Total", leaf: true },
      ],
      leaf: false,
   };
   comp._columnTreeRoot = comp.columnTreeRoot;
   comp.originalColumnTreeRoot = comp.columnTreeRoot;
   comp.submitCallback = () => syncResolve(true);
   return { comp, editorService, modalService, dropdownService, renderer };
}

export function prepareExpressionEditor(comp: FormulaEditorDialog) {
   comp.expression = null;
   comp.initForm();
}

export function expressionChangePayload(overrides: Record<string, unknown> = {}) {
   return {
      expression: "",
      target: "columnTree",
      selection: {
         from: { ch: 0, line: 0, sticky: null },
         to: { ch: 0, line: 0, sticky: null },
      },
      node: null,
      ...overrides,
   };
}

export function leafNode(data: Record<string, unknown>, label = "node"): TreeNodeModel {
   return {
      children: [],
      data,
      expanded: true,
      label,
      leaf: true,
   } as TreeNodeModel;
}

export function columnTreeWithFields(): TreeNodeModel {
   return {
      label: "root",
      children: [{
         label: "_#(js:Fields)",
         leaf: false,
         children: [
            { label: "Sales", leaf: true },
            { label: "_#(js:New Aggregate)", leaf: true, data: { data: "New Aggregate" } },
         ],
      }],
   };
}

export function mockAggrDialogOpen(modalService: { open: ReturnType<typeof vi.fn> }) {
   vi.mocked(modalService.open).mockReturnValue({
      result: syncResolve({
         field: "Sales",
         aggregate: "Sum",
         numValue: "0",
      }),
   } as any);
   return {} as TemplateRef<unknown>;
}
