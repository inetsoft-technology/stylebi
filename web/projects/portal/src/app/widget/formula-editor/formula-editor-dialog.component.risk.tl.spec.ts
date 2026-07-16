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
 * FormulaEditorDialog — Pass 2: Risk
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — populateColumnTree / populateFunctionTree / populateOperatorTree HTTP callbacks
 *   Group 2 [Risk 3] — populateScriptDefinitions dual subscribe + stale response ordering
 *   Group 3 [Risk 3] — calcType / formulaType valueChanges side-effects
 *   Group 4 [Risk 3] — showAggregateDialog rejected modal + destroy stops valueChanges
 *
 * Out of scope this pass:
 *   expressionChange branches → formula-editor-dialog.component.display.tl.spec.ts
 *   ok / cancel / isCycle → formula-editor-dialog.component.interaction.tl.spec.ts
 */

import { Subject, of } from "rxjs";
import { ComponentTool } from "../../common/util/component-tool";
import { FormulaType } from "../../common/data/formula-type";
import { TreeNodeModel } from "../tree/tree-node-model";
import {
   columnTreeWithFields,
   createDialog,
   flushPromises,
   syncReject,
} from "./formula-editor-dialog.component.test-helpers";

describe("FormulaEditorDialog — populate tree HTTP callbacks [Group 1, Risk 3]", () => {

   it("should replace column tree when getColumnTreeNode emits", async () => {
      const columnSubject = new Subject<TreeNodeModel>();
      const { comp, editorService } = createDialog();
      vi.mocked(editorService.getColumnTreeNode).mockReturnValue(columnSubject);
      comp.vsId = "vs-1";
      comp.assemblyName = "Chart1";

      comp["populateColumnTree"]();
      columnSubject.next({
         label: "Server Fields",
         children: [{ label: "Revenue", leaf: true }],
      } as TreeNodeModel);
      await flushPromises();

      expect(editorService.getColumnTreeNode).toHaveBeenCalledWith("vs-1", "Chart1", false);
      expect(comp._columnTreeRoot.label).toBe("Server Fields");
   });

   it("should set functionTreeRoot from getFunctionTreeNode for script formulas", async () => {
      const fnTree = { label: "Functions", children: [{ label: "abs", leaf: true }] };
      const { comp, editorService } = createDialog();
      vi.mocked(editorService.getFunctionTreeNode).mockReturnValue(of(fnTree as TreeNodeModel));
      comp.initForm();

      comp["populateFunctionTree"]();
      await flushPromises();

      expect(comp.functionTreeRoot).toEqual(fnTree);
   });

   it("should set operatorTreeRoot from getOperationTreeNode for script formulas", async () => {
      const opTree = { label: "Operators", children: [{ label: "+", leaf: true }] };
      const { comp, editorService } = createDialog();
      vi.mocked(editorService.getOperationTreeNode).mockReturnValue(of(opTree as TreeNodeModel));
      comp.initForm();

      comp["populateOperatorTree"]();
      await flushPromises();

      expect(comp.operatorTreeRoot).toEqual(opTree);
   });
});

describe("FormulaEditorDialog — script definitions and stale responses [Group 2, Risk 3]", () => {

   it("should load task and viewsheet script definitions on separate subscribes", async () => {
      const { comp, editorService } = createDialog();
      comp.task = true;
      comp.vsId = "vs-1";
      comp.assemblyName = "Table1";
      comp.initForm();

      comp["populateScriptDefinitions"]();
      await flushPromises();

      expect(editorService.getTaskScriptDefinitions).toHaveBeenCalled();
      expect(editorService.getScriptDefinitions).toHaveBeenCalledWith("vs-1", "Table1", false);
      expect(comp.scriptDefinitions).toEqual({ defs: "vs" });
   });

   // Known race condition bug: populateColumnTree() uses subscribe() without switchMap, so a
   // delayed earlier response overwrites a newer one. The desired behavior is "Fresh" wins;
   // fix by switching to switchMap (which cancels the first request). Until then, this test
   // documents the broken behavior — update expect to "Fresh" when the defect is resolved.
   it("should be overwritten by stale response when earlier request resolves after later one (known race condition)", async () => {
      const first = new Subject<TreeNodeModel>();
      const second = new Subject<TreeNodeModel>();
      const { comp, editorService } = createDialog();
      let call = 0;
      vi.mocked(editorService.getColumnTreeNode).mockImplementation(() => {
         return call++ === 0 ? first : second;
      });
      comp.vsId = "vs-1";

      comp["populateColumnTree"]();
      comp["populateColumnTree"]();
      second.next({ label: "Fresh", children: [] } as TreeNodeModel);
      first.next({ label: "Stale", children: [] } as TreeNodeModel);
      await flushPromises();

      expect(comp._columnTreeRoot.label).toBe("Stale");
   });
});

describe("FormulaEditorDialog — form valueChanges side-effects [Group 3, Risk 3]", () => {

   it("should switch calc field to aggregate defaults when calcType changes", () => {
      const { comp } = createDialog();
      comp.isCalc = true;
      comp.calcType = "field";
      comp.initForm();

      comp.form.get("calcType").setValue("aggregate");

      expect(comp.calcType).toBe("aggregate");
      expect(comp.form.get("dataType").value).toBe("double");
      expect(comp.form.get("formulaType").value).toBe(FormulaType.SCRIPT);
   });

   it("should repopulate trees and warn when formulaType changes to SQL for aggregate calc", () => {
      const { comp, editorService } = createDialog();
      comp.isCalc = true;
      comp.calcType = "aggregate";
      comp.initForm();
      const showMessageDialog = vi.spyOn(ComponentTool, "showMessageDialog")
         .mockResolvedValue("ok");
      vi.mocked(editorService.getFunctionTreeNode).mockClear();

      comp.form.get("formulaType").setValue(FormulaType.SQL);

      expect(editorService.getFunctionTreeNode).not.toHaveBeenCalled();
      expect(comp.functionTreeRoot?.children?.length).toBeGreaterThan(0);
      expect(showMessageDialog).toHaveBeenCalledWith(
         expect.anything(),
         "_#(js:Warning)",
         "_#(js:common.calcfieldAggrSqlUnsupport)",
         expect.anything(),
         expect.anything(),
      );
   });

   // 🔁 Regression-sensitive: destroyed dialog must not react to calcType valueChanges
   it("should stop calcType side-effects after ngOnDestroy", () => {
      const { comp } = createDialog();
      comp.isCalc = true;
      comp.calcType = "field";
      comp.ngOnInit();

      comp.ngOnDestroy();
      comp.form.get("calcType").setValue("aggregate");

      expect(comp.calcType).toBe("field");
   });
});

describe("FormulaEditorDialog — modal promise handling [Group 4, Risk 3]", () => {

   it("should not emit aggregateModify when new aggregate dialog is dismissed", () => {
      const { comp, modalService } = createDialog();
      comp.columnTreeRoot = columnTreeWithFields();
      comp._columnTreeRoot = comp.columnTreeRoot;
      comp.columns = [{ name: "Sales", dataType: "double" } as any];
      comp.newAggrDialog = {} as any;
      vi.mocked(modalService.open).mockReturnValue({
         result: syncReject("dismissed"),
      } as any);
      const emitSpy = vi.spyOn(comp.aggregateModify, "emit");

      comp.showAggregateDialog();

      expect(emitSpy).not.toHaveBeenCalled();
   });
});
