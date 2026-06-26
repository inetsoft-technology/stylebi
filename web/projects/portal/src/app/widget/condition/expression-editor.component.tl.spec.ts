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
 * ExpressionEditor — single pass (+内存泄漏)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — ngOnChanges: default value init; type change re-fetches column tree
 *   Group 2 [Risk 3] — changeExpressionType: toggles SQL/JS and emits valueChange
 *   Group 3 [Risk 2] — isMV: detects MV child in column tree
 *   Group 4 [Risk 3] — ngOnDestroy: unsubscribes columnTree and scriptDefinitions
 *   Group 5 [Risk 3] — openFormulaWindow: modal commit updates value and emits
 *   Group 6 [Risk 3] — updateColumnTree: stale subscription must not write after resubscribe
 *
 * Out of scope: openFormulaWindow dismiss path (empty catch, no state change)
 */

import { NO_ERRORS_SCHEMA, TemplateRef } from "@angular/core";
import { render, waitFor } from "@testing-library/angular";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { of, Subject } from "rxjs";
import { ExpressionType } from "../../common/data/condition/expression-type";
import { ExpressionValue } from "../../common/data/condition/expression-value";
import { TreeNodeModel } from "../tree/tree-node-model";
import { ExpressionEditor } from "./expression-editor.component";

async function renderEditor(props: {
   value?: ExpressionValue | null;
   expressionTypes?: ExpressionType[];
   columnTreeFunction?: (v: ExpressionValue) => any;
   scriptDefinitionsFunction?: (v: ExpressionValue) => any;
} = {}) {
   const columnTreeFunction = props.columnTreeFunction ?? (() => of({ children: [] } as TreeNodeModel));
   const result = await render(ExpressionEditor, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [{ provide: NgbModal, useValue: { open: vi.fn() } }],
      componentProperties: {
         expressionTypes: props.expressionTypes ?? [ExpressionType.JS, ExpressionType.SQL],
         columnTreeFunction,
         scriptDefinitionsFunction: props.scriptDefinitionsFunction,
         value: props.value ?? null,
      },
   });
   return result.fixture.componentInstance;
}

describe("ExpressionEditor — ngOnChanges — value initialization [Group 1, Risk 3]", () => {

   it("should create default ExpressionValue when value input is null", async () => {
      const comp = await renderEditor({ value: null });

      expect(comp.value.type).toBe(ExpressionType.JS);
      expect(comp.value.expression).toBeNull();
      expect(comp.expressionType).toBe(ExpressionType.JS);
   });

   it("should sync expressionType from existing value", async () => {
      const existing: ExpressionValue = { expression: "1+1", type: ExpressionType.SQL };
      const comp = await renderEditor({ value: existing });

      expect(comp.expressionType).toBe(ExpressionType.SQL);
   });

   it("should re-fetch column tree when value type changes", async () => {
      const columnTreeFn = vi.fn(() => of({ children: [] } as TreeNodeModel));
      const comp = await renderEditor({
         value: { expression: "a", type: ExpressionType.JS },
         columnTreeFunction: columnTreeFn,
      });
      columnTreeFn.mockClear();

      comp.ngOnChanges({
         value: {
            previousValue: { expression: "a", type: ExpressionType.JS },
            currentValue: { expression: "a", type: ExpressionType.SQL },
            firstChange: false,
            isFirstChange: () => false,
         },
      });

      expect(columnTreeFn).toHaveBeenCalled();
   });
});

describe("ExpressionEditor — changeExpressionType — toggle and emit [Group 2, Risk 3]", () => {

   it("should toggle from JS to SQL, preserve expression, and emit valueChange", async () => {
      const comp = await renderEditor({
         value: { expression: "old", type: ExpressionType.JS },
      });
      comp.expressionType = ExpressionType.JS;
      const emitSpy = vi.spyOn(comp.valueChange, "emit");

      comp.changeExpressionType();

      expect(comp.expressionType).toBe(ExpressionType.SQL);
      expect(comp.value.type).toBe(ExpressionType.SQL);
      expect(comp.value.expression).toBe("old");
      expect(emitSpy).toHaveBeenCalledWith(comp.value);
   });

   it("should toggle from SQL back to JS", async () => {
      const comp = await renderEditor({
         value: { expression: null, type: ExpressionType.SQL },
      });
      comp.expressionType = ExpressionType.SQL;

      comp.changeExpressionType();

      expect(comp.expressionType).toBe(ExpressionType.JS);
   });
});

describe("ExpressionEditor — isMV — column tree inspection [Group 3, Risk 2]", () => {

   it("should return true when column tree has MV child", async () => {
      const comp = await renderEditor();
      comp.columnTreeModel = {
         children: [{ data: "MV" }],
      } as TreeNodeModel;

      expect(comp.isMV()).toBe(true);
   });

   it("should return false when column tree has no MV child", async () => {
      const comp = await renderEditor();
      comp.columnTreeModel = {
         children: [{ data: "field" }],
      } as TreeNodeModel;

      expect(comp.isMV()).toBe(false);
   });

   it("should return false when column tree is null", async () => {
      const comp = await renderEditor();
      comp.columnTreeModel = null;

      expect(comp.isMV()).toBe(false);
   });
});

describe("ExpressionEditor — ngOnDestroy — subscription cleanup [Group 4, Risk 3]", () => {

   it("should unsubscribe columnTreeSub and scriptDefinitionsSub on destroy", async () => {
      const columnSubject = new Subject<TreeNodeModel>();
      const scriptSubject = new Subject<any>();
      const comp = await renderEditor({
         columnTreeFunction: () => columnSubject.asObservable(),
         scriptDefinitionsFunction: () => scriptSubject.asObservable(),
      });

      expect(comp.columnTreeSub.closed).toBe(false);
      expect(comp.scriptDefinitionsSub.closed).toBe(false);

      comp.ngOnDestroy();

      expect(comp.columnTreeSub.closed).toBe(true);
      expect(comp.scriptDefinitionsSub).toBeNull();
   });
});

describe("ExpressionEditor — openFormulaWindow — modal commit [Group 5, Risk 3]", () => {

   it("should apply modal result to value and emit valueChange", async () => {
      const modalResult = Promise.resolve({
         expression: "sum(field['x'])",
         formulaType: ExpressionType.JS,
      });
      const open = vi.fn().mockReturnValue({ result: modalResult });
      const result = await render(ExpressionEditor, {
         schemas: [NO_ERRORS_SCHEMA],
         providers: [{ provide: NgbModal, useValue: { open } }],
         componentProperties: {
            expressionTypes: [ExpressionType.JS, ExpressionType.SQL],
            columnTreeFunction: () => of({ children: [] } as TreeNodeModel),
            value: { expression: "", type: ExpressionType.JS },
         },
      });
      const comp = result.fixture.componentInstance;
      comp.formulaEditorDialog = {} as TemplateRef<unknown>;
      const emitSpy = vi.spyOn(comp.valueChange, "emit");

      comp.openFormulaWindow();

      expect(open).toHaveBeenCalledWith(comp.formulaEditorDialog,
         { size: "lg", backdrop: false, keyboard: false });

      await waitFor(() => {
         expect(comp.value.expression).toBe("sum(field['x'])");
         expect(comp.value.type).toBe(ExpressionType.JS);
         expect(emitSpy).toHaveBeenCalledWith(comp.value);
      });
   });
});

describe("ExpressionEditor — updateColumnTree — stale subscription guard [Group 6, Risk 3]", () => {

   // 🔁 Regression-sensitive: duplicate live subscriptions cause redundant tree rebuilds and UI flicker
   it("should ignore emissions from a superseded column-tree subscription", async () => {
      const first = new Subject<TreeNodeModel>();
      const second = new Subject<TreeNodeModel>();
      let call = 0;
      const columnTreeFn = vi.fn(() => {
         call++;
         return call === 1 ? first.asObservable() : second.asObservable();
      });
      const comp = await renderEditor({ columnTreeFunction: columnTreeFn });

      comp.updateColumnTree();
      comp.updateColumnTree();

      first.next({ children: [{ data: "STALE" }] } as TreeNodeModel);
      expect(comp.columnTreeModel?.children?.[0]?.data).not.toBe("STALE");

      second.next({ children: [{ data: "FRESH" }] } as TreeNodeModel);
      expect(comp.columnTreeModel.children[0].data).toBe("FRESH");
      expect(comp.columnTreeSub.closed).toBe(false);
   });
});
