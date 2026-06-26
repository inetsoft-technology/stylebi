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
 * FormulaEditorDialog — Pass 1: Interaction
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — ok guards (duplicate name, cycle, calcFieldsGroup) + commit/cancel
 *   Group 2 [Risk 3] — isCycle / checkExpression self-reference chain
 *   Group 3 [Risk 2] — isDuplicateFormulaName / isDuplicateName
 *   Group 4 [Risk 3] — showAggregateDialog modal result, deleteAggregate, showContextMenu
 *   Group 5 [Risk 3] — initForm, ngOnInit, ngOnDestroy, ngAfterViewInit
 *
 * Out of scope this pass:
 *   expressionChange (15+ branches) → formula-editor-dialog.component.display.tl.spec.ts
 *   populate* HTTP subscribe races, valueChanges side-effects → formula-editor-dialog.component.risk.tl.spec.ts
 *
 * Direct instantiation — ScriptPane / CodeMirror not rendered.
 */

import { ElementRef, Renderer2, TemplateRef } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { of } from "rxjs";
import { ComponentTool } from "../../common/util/component-tool";
import { AggregateRef } from "../../common/data/aggregate-ref";
import { FormulaField } from "../../common/data/formula-field";
import { FormulaType } from "../../common/data/formula-type";
import { ActionsContextmenuComponent } from "../fixed-dropdown/actions-contextmenu.component";
import { FixedDropdownService } from "../fixed-dropdown/fixed-dropdown.service";
import { TreeNodeModel } from "../tree/tree-node-model";
import { FormulaEditorDialog } from "./formula-editor-dialog.component";
import { FormulaEditorService } from "./formula-editor.service";

function flushPromises(): Promise<void> {
   return new Promise(resolve => setTimeout(resolve, 0));
}

function createDialog() {
   const editorService = {
      getColumnTreeNode: vi.fn(() => of({ children: [] } as TreeNodeModel)),
      getFunctionTreeNode: vi.fn(() => of({ children: [] } as TreeNodeModel)),
      getOperationTreeNode: vi.fn(() => of({ children: [] } as TreeNodeModel)),
      getScriptDefinitions: vi.fn(() => of({})),
      getTaskScriptDefinitions: vi.fn(() => of({})),
   };
   const modalService = { open: vi.fn() };
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
   comp.submitCallback = () => Promise.resolve(true);
   return { comp, editorService, modalService, dropdownService };
}

describe("FormulaEditorDialog — ok and cancel [Group 1, Risk 3]", () => {

   it("should emit onCommit when form is valid and submitCallback resolves true", async () => {
      const { comp } = createDialog();
      comp.initForm();
      const emitSpy = vi.spyOn(comp.onCommit, "emit");

      comp.ok();
      await flushPromises();

      expect(emitSpy).toHaveBeenCalledWith(expect.objectContaining({
         expression: "1 + 1",
         formulaName: "CalcField1",
         formulaType: FormulaType.SCRIPT,
      }));
   });

   it("should not emit onCommit when submitCallback resolves false", async () => {
      const { comp } = createDialog();
      comp.submitCallback = () => Promise.resolve(false);
      comp.initForm();
      const emitSpy = vi.spyOn(comp.onCommit, "emit");

      comp.ok();
      await flushPromises();

      expect(emitSpy).not.toHaveBeenCalled();
   });

   it("should emit cancel on cancel", () => {
      const { comp } = createDialog();
      const emitSpy = vi.spyOn(comp.onCancel, "emit");

      comp.cancel();

      expect(emitSpy).toHaveBeenCalledWith("cancel");
   });

   // 🔁 Regression-sensitive: duplicate field name in column tree blocked on ok
   it("should block ok when formula name duplicates an existing column tree label", () => {
      const { comp } = createDialog();
      comp.initForm();
      comp.form.get("formulaName").patchValue("State");
      const showMessageDialog = vi.spyOn(ComponentTool, "showMessageDialog")
         .mockResolvedValue("ok");
      const emitSpy = vi.spyOn(comp.onCommit, "emit");

      comp.ok();

      expect(showMessageDialog).toHaveBeenCalledWith(
         expect.anything(),
         "_#(js:Error)",
         "_#(js:viewer.formulaNameInUseError)!",
      );
      expect(emitSpy).not.toHaveBeenCalled();
   });

   it("should block ok when calcFieldsGroup already contains the formula name", () => {
      const { comp } = createDialog();
      comp.isCalc = true;
      comp.calcFieldsGroup = ["CalcField1"];
      comp.initForm();
      const showMessageDialog = vi.spyOn(ComponentTool, "showMessageDialog")
         .mockResolvedValue("ok");
      const emitSpy = vi.spyOn(comp.onCommit, "emit");

      comp.ok();

      expect(showMessageDialog).toHaveBeenCalledWith(
         expect.anything(),
         "_#(js:Error)",
         "_#(js:Duplicate Name)!",
      );
      expect(emitSpy).not.toHaveBeenCalled();
   });

   it("should block ok when expression references itself through formulaFields", () => {
      const { comp } = createDialog();
      comp.formulaName = "CalcA";
      comp.expression = "field['CalcA'] + 1";
      comp.formulaFields = [{ name: "Other", exp: "1" } as FormulaField];
      comp.columnTreeRoot = { label: "Fields", children: [], leaf: false };
      comp.checkDuplicatesInColumnTree = false;
      comp.initForm();
      const showMessageDialog = vi.spyOn(ComponentTool, "showMessageDialog")
         .mockResolvedValue("ok");
      const emitSpy = vi.spyOn(comp.onCommit, "emit");

      comp.ok();

      expect(showMessageDialog).toHaveBeenCalledWith(
         expect.anything(),
         "_#(js:Error)",
         "_#(js:viewer.formulaUseSelf)!",
      );
      expect(emitSpy).not.toHaveBeenCalled();
   });
});

describe("FormulaEditorDialog — cycle detection [Group 2, Risk 3]", () => {

   it("should return false from isCycle when formulaFields is empty", () => {
      const { comp } = createDialog();
      comp.formulaFields = null;

      expect(comp.isCycle()).toBe(false);
   });

   it("should detect direct self-reference in checkExpression", () => {
      const { comp } = createDialog();
      comp.formulaFields = [];

      expect(comp.checkExpression("CalcA", "field['CalcA']")).toBe(true);
      expect(comp.checkExpression("CalcA", "field['Other']")).toBe(false);
   });

   it("should detect transitive cycle through other formula fields", () => {
      const { comp } = createDialog();
      comp.formulaName = "CalcA";
      comp.expression = "field['CalcB']";
      comp.formulaFields = [
         { name: "CalcB", exp: "field['CalcA']" } as FormulaField,
      ];

      expect(comp.isCycle()).toBe(true);
   });
});

describe("FormulaEditorDialog — duplicate name helpers [Group 3, Risk 2]", () => {

   it("should match duplicate formula names case-insensitively in column tree", () => {
      const { comp } = createDialog();
      comp.formulaName = "CalcField1";
      comp.columnTreeRoot = {
         label: "Fields",
         children: [{ label: "STATE", leaf: true }],
         leaf: false,
      };

      expect(comp.isDuplicateFormulaName(comp.columnTreeRoot, "state")).toBe(true);
      expect(comp.isDuplicateFormulaName(comp.columnTreeRoot, "CalcField1")).toBe(false);
   });

   it("should detect duplicates in availableFields after tree scan", () => {
      const { comp } = createDialog();
      comp.formulaName = "CalcField1";
      comp.availableFields = [{ attribute: "Revenue", name: "Revenue" } as any];
      const treeRoot = {
         label: "Fields",
         children: [{ label: "Unrelated", leaf: true }],
         leaf: false,
      };

      expect(comp.isDuplicateFormulaName(treeRoot, "Revenue")).toBe(true);
   });

   it("should return true from isDuplicateName when another formula field shares the name", () => {
      const { comp } = createDialog();
      comp.formulaName = "CalcField1";
      comp.formulaFields = [
         { name: "Other" } as FormulaField,
         { name: "NewName" } as FormulaField,
      ];
      comp.initForm();
      comp.form.get("formulaName").patchValue("NewName");

      expect(comp.isDuplicateName()).toBe(true);
   });
});

describe("FormulaEditorDialog — aggregate dialog and context menu [Group 4, Risk 3]", () => {

   function columnTreeWithFields(): TreeNodeModel {
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

   it("should add aggregate to tree and emit aggregateModify after modal confirms", async () => {
      const { comp, modalService } = createDialog();
      comp.columnTreeRoot = columnTreeWithFields();
      comp._columnTreeRoot = comp.columnTreeRoot;
      comp.columns = [{ name: "Sales", dataType: "double" } as any];
      comp.newAggrDialog = {} as TemplateRef<unknown>;
      vi.mocked(modalService.open).mockReturnValue({
         result: Promise.resolve({
            field: "Sales",
            aggregate: "Sum",
            numValue: "0",
         }),
      } as any);
      const emitSpy = vi.spyOn(comp.aggregateModify, "emit");

      comp.showAggregateDialog();
      await flushPromises();

      expect(modalService.open).toHaveBeenCalled();
      expect(emitSpy).toHaveBeenCalledWith(expect.objectContaining({
         nref: expect.objectContaining({ formulaName: "Sum" }),
         oref: null,
      }));
   });

   it("should emit aggregateDelete when deleteAggregate matches a user aggregate", () => {
      const { comp } = createDialog();
      const aggregate: AggregateRef = {
         name: "Sales",
         formulaName: "Sum",
         ref: { name: "Sales" },
      } as AggregateRef;
      comp.aggregates = [aggregate];
      const label = comp["getFullName"](aggregate);
      const node: TreeNodeModel = { label, data: { useragg: "true" } };
      const emitSpy = vi.spyOn(comp.aggregateDelete, "emit");

      comp["deleteAggregate"](node);

      expect(emitSpy).toHaveBeenCalledWith({ nref: null, oref: aggregate });
   });

   it("should wire delete action through createActions for user aggregates", () => {
      const { comp } = createDialog();
      const aggregate: AggregateRef = {
         name: "Sales",
         formulaName: "Sum",
         ref: { name: "Sales" },
      } as AggregateRef;
      comp.aggregates = [aggregate];
      const label = comp["getFullName"](aggregate);
      const node: TreeNodeModel = { label, data: { useragg: "true" } };
      const emitSpy = vi.spyOn(comp.aggregateDelete, "emit");

      const actions = comp["createActions"](node);
      actions[0].actions[0].action({} as MouseEvent);

      expect(emitSpy).toHaveBeenCalled();
      expect(actions[0].actions[0].visible()).toBe(true);
   });

   it("should open context menu dropdown at mouse position", () => {
      const { comp, dropdownService } = createDialog();
      comp.formElementRef = { nativeElement: document.createElement("form") } as ElementRef;
      const contextmenu = { actions: null, sourceEvent: null };
      vi.mocked(dropdownService.open).mockReturnValue({
         componentInstance: contextmenu,
      } as any);
      const event = new MouseEvent("contextmenu", { clientX: 10, clientY: 20 });
      const node: TreeNodeModel = { label: "Sum([Sales])", data: { useragg: "true" } };

      comp.showContextMenu([event, node, []]);

      expect(dropdownService.open).toHaveBeenCalledWith(
         ActionsContextmenuComponent,
         expect.objectContaining({
            contextmenu: true,
            position: { x: 12, y: 22 },
         }),
      );
      expect(contextmenu.actions).not.toBeNull();
   });
});

describe("FormulaEditorDialog — lifecycle and initForm [Group 5, Risk 3]", () => {

   it("should build required form controls in initForm for calc fields", () => {
      const { comp } = createDialog();
      comp.isCalc = true;
      comp.calcType = "field";

      comp.initForm();

      expect(comp.form.contains("formulaName")).toBe(true);
      expect(comp.form.contains("formulaType")).toBe(true);
      expect(comp.form.contains("calcType")).toBe(true);
      expect(comp.form.get("calcType").value).toBe("field");
   });

   it("should initialize form and set init flag on ngOnInit", () => {
      const { comp } = createDialog();
      comp.vsId = null;

      comp.ngOnInit();

      expect(comp.form).toBeTruthy();
      expect(comp.oname).toBe("CalcField1");
      expect(comp.returnTypes).toEqual(FormulaEditorService.returnTypes);
      expect(comp["init"]).toBe(true);
   });

   // 🔁 Regression-sensitive: leaked valueChanges subscriptions on repeated open/close
   it("should unsubscribe all subscriptions on ngOnDestroy", () => {
      const { comp } = createDialog();
      comp.isCalc = true;
      comp.calcType = "field";
      comp.ngOnInit();
      const unsubscribeSpy = vi.spyOn(comp.subscriptions, "unsubscribe");

      comp.ngOnDestroy();

      expect(unsubscribeSpy).toHaveBeenCalled();
      expect(comp.subscriptions).toBeNull();
   });

   it("should run checkValid after view init when formulaType control exists", async () => {
      const { comp } = createDialog();
      comp.resizeable = false;
      comp.isCalc = true;
      comp.calcType = "aggregate";
      comp.initForm();
      comp.form.get("formulaType").setValue(FormulaType.SQL);
      const showMessageDialog = vi.spyOn(ComponentTool, "showMessageDialog")
         .mockResolvedValue("ok");

      comp.ngAfterViewInit();
      await flushPromises();

      expect(showMessageDialog).toHaveBeenCalledWith(
         expect.anything(),
         "_#(js:Warning)",
         "_#(js:common.calcfieldAggrSqlUnsupport)",
         expect.anything(),
         expect.anything(),
      );
   });
});
