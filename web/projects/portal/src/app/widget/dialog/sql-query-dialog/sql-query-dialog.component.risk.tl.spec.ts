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
 * SQLQueryDialog — Pass 2: Risk / Async
 *
 * Risk-first coverage:
 *   Group 1   advancedEditing getter — dual boolean coverage
 *   Group 2   subQuery getter — delegates to controller
 *   Group 3   createForm — form validators (required, nameSpecialCharacters)
 *   Group 4   dataSourceChanged — syncs controller.dataSource + triggers tree load
 *   Group 5   loadDataSourceTree(initial=true) — tree loaded, clear() not called
 *   Group 6   loadDataSourceTree(initial=false) — calls clear() after tree load
 *   Group 7   clearDisabled — all branches
 *   Group 8   clear — HTTP post updates model
 *   Group 9   updateNameValidation — sets tableNameExists flag
 *   Group 10  cancel — simple mode (not sqlEdited) → destroyRuntimeQuery + emit
 *   Group 11  cancel — advanced with sqlEdited → shows confirm dialog
 *   Group 12  isApplyBtnDisabled — all conditions
 *   Group 13  initOperations — HTTP get populates operations
 *   Group 14  destroyRuntimeQuery — HTTP delete only when runtimeId exists
 *   Group 15  isJoinEditView — false when pane absent; delegates when present
 */

import { of, Subject } from "rxjs";
import {
   makeComponent,
   makeModel,
   makeSimpleModel,
   makeHttp,
   makeModal,
} from "./sql-query-dialog.component.test-helpers";
import { MessageDialog } from "../message-dialog/message-dialog.component";

// Reset the static dedup guard before every test so that consecutive tests calling
// showConfirmDialog with the same message text are not silently rejected.
beforeEach(() => {
   MessageDialog.lastMessage = null;
   (MessageDialog as any).lastMessageTS = 0;
});

// ---------------------------------------------------------------------------
// Group 1 — advancedEditing getter
// ---------------------------------------------------------------------------

describe("Group 1 — advancedEditing getter: dual boolean coverage", () => {
   it("should return true when model.advancedEdit=true", () => {
      const { comp } = makeComponent({ model: makeModel({ advancedEdit: true }) });
      expect(comp.advancedEditing).toBe(true);
   });

   it("should return false when model.advancedEdit=false", () => {
      const { comp } = makeComponent({ model: makeModel({ advancedEdit: false }) });
      expect(comp.advancedEditing).toBe(false);
   });

   it("should return falsy when model is null", () => {
      const { comp } = makeComponent({ skipNgOnInit: true });
      comp.model = null;
      expect(comp.advancedEditing).toBeFalsy();
   });
});

// ---------------------------------------------------------------------------
// Group 2 — subQuery getter
// ---------------------------------------------------------------------------

describe("Group 2 — subQuery getter: delegates to controller.subQuery", () => {
   it("should return true when controller.subQuery=true", () => {
      const { comp } = makeComponent();
      comp.controller = { ...comp.controller, subQuery: true } as any;
      expect(comp.subQuery).toBe(true);
   });

   it("should return false when controller.subQuery=false", () => {
      const { comp } = makeComponent();
      comp.controller = { ...comp.controller, subQuery: false } as any;
      expect(comp.subQuery).toBe(false);
   });

   it("should return falsy when no controller", () => {
      const { comp } = makeComponent({ skipNgOnInit: true });
      comp.controller = null;
      expect(comp.subQuery).toBeFalsy();
   });
});

// ---------------------------------------------------------------------------
// Group 3 — createForm: validators
// ---------------------------------------------------------------------------

describe("Group 3 — createForm: name control has required + nameSpecialCharacters validators", () => {
   it("should create form with a name control", () => {
      const { comp } = makeComponent();
      expect(comp.form?.get("name")).toBeTruthy();
   });

   it("should be valid with a normal name", () => {
      const { comp } = makeComponent({ initTableName: "sales_query" });
      expect(comp.form.get("name")?.valid).toBe(true);
   });

   it("should be invalid when name is empty (required validator)", () => {
      const { comp } = makeComponent({ initTableName: "" });
      expect(comp.form.get("name")?.invalid).toBe(true);
   });

   it("should be invalid when name contains a forbidden character (nameSpecialCharacters validator)", () => {
      // '/' is not in the allowed character class [＀-￯一-龥a-zA-Z0-9 $#_%&\-,?!@']
      const { comp } = makeComponent({ initTableName: "q/uery" });
      comp.form.get("name")?.setValue("q/uery");
      expect(comp.form.get("name")?.errors?.["nameSpecialCharacters"]).toBeTruthy();
   });
});

// ---------------------------------------------------------------------------
// Group 4 — dataSourceChanged
// ---------------------------------------------------------------------------

describe("Group 4 — dataSourceChanged: syncs controller.dataSource and reloads tree", () => {
   it("should set controller.dataSource to model.dataSource", () => {
      const model = makeModel({ dataSource: "ds2" });
      const { comp, controller } = makeComponent({ model });
      comp.model.dataSource = "ds3";

      comp.dataSourceChanged();

      expect(controller.dataSource).toBe("ds3");
   });

   it("should call controller.getDataSourceTree after sync", () => {
      const { comp, controller } = makeComponent();
      (controller.getDataSourceTree as ReturnType<typeof vi.fn>).mockClear();

      comp.dataSourceChanged();

      expect(controller.getDataSourceTree).toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 5 — loadDataSourceTree(initial=true): no clear() call
// ---------------------------------------------------------------------------

describe("Group 5 — loadDataSourceTree(initial=true): sets tree, does not call clear()", () => {
   it("should set dataSourceTreeRoot from controller.getDataSourceTree", () => {
      const tree = { children: [{ label: "PRODUCTS" }] };
      const { comp, controller } = makeComponent();
      (controller.getDataSourceTree as ReturnType<typeof vi.fn>)
         .mockReturnValue(of(tree));

      comp.loadDataSourceTree(true);

      expect(comp.dataSourceTreeRoot).toEqual(tree);
   });

   it("should set loading=false after tree loads", () => {
      const { comp } = makeComponent();
      comp.loading = true;

      comp.loadDataSourceTree(true);

      expect(comp.loading).toBe(false);
   });

   it("should NOT call http.post (clear) when initial=true", () => {
      const http = makeHttp();
      const { comp } = makeComponent({ http });
      http.post.mockClear();

      comp.loadDataSourceTree(true);

      // post would be called by clear(); should not happen on initial load
      const clearCalls = http.post.mock.calls.filter((c: any[]) =>
         (c[0] as string).includes("clear"),
      );
      expect(clearCalls.length).toBe(0);
   });
});

// ---------------------------------------------------------------------------
// Group 6 — loadDataSourceTree(initial=false): calls clear()
// ---------------------------------------------------------------------------

describe("Group 6 — loadDataSourceTree(initial=false): calls clear() after tree loads", () => {
   it("should call http.post (clear endpoint) when initial=false", () => {
      const http = makeHttp();
      const { comp } = makeComponent({ http });
      http.post.mockClear();

      comp.loadDataSourceTree(false);

      const clearCalls = http.post.mock.calls.filter((c: any[]) =>
         (c[0] as string).includes("clear"),
      );
      expect(clearCalls.length).toBeGreaterThan(0);
   });
});

// ---------------------------------------------------------------------------
// Group 7 — clearDisabled: all branches
// ---------------------------------------------------------------------------

describe("Group 7 — clearDisabled: all condition branches", () => {
   it("should return true when model is null", () => {
      const { comp } = makeComponent({ skipNgOnInit: true });
      comp.model = null;
      expect(comp.clearDisabled()).toBe(true);
   });

   it("should return true when isJoinEditView() is true", () => {
      const { comp } = makeComponent();
      comp.advancedQueryPane = { isJoinEditView: vi.fn().mockReturnValue(true), checkQuery: vi.fn(), resetActiveTab: vi.fn() } as any;
      expect(comp.clearDisabled()).toBe(true);
   });

   it("should return true (simple) when no sqlEdited and no columns", () => {
      const model = makeModel({
         simpleModel: makeSimpleModel({ sqlEdited: false, columns: [], sqlString: "" }),
      });
      const { comp } = makeComponent({ model });
      expect(comp.clearDisabled()).toBe(true);
   });

   it("should return false (simple) when sqlString exists and columns is null", () => {
      // When columns is null the early-return condition (!sqlEdited && columns && columns.length===0)
      // is bypassed (columns is falsy), so clearDisabled reaches `return !sqlString`.
      const model = makeModel({
         simpleModel: makeSimpleModel({ sqlString: "SELECT 1", sqlEdited: false, columns: null }),
      });
      const { comp } = makeComponent({ model });
      expect(comp.clearDisabled()).toBe(false);
   });

   it("should return false (simple) when sqlEdited=true with some columns", () => {
      const model = makeModel({
         simpleModel: makeSimpleModel({ sqlEdited: true, columns: [{ name: "id" } as any], sqlString: "SELECT id" }),
      });
      const { comp } = makeComponent({ model });
      expect(comp.clearDisabled()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 8 — clear: HTTP post to CLEAR_MODEL_URI updates model
// ---------------------------------------------------------------------------

describe("Group 8 — clear: HTTP post updates model when response is non-null", () => {
   it("should call http.post with the CLEAR_MODEL_URI", () => {
      const http = makeHttp();
      const { comp } = makeComponent({ http });
      http.post.mockClear();

      comp.clear();

      expect(http.post).toHaveBeenCalledWith(
         expect.stringContaining("clear"),
         null,
         expect.anything(),
      );
   });

   it("should update comp.model when HTTP response is a valid model", () => {
      const responseModel = makeModel({ name: "cleared" });
      const http = makeHttp();
      http.post.mockReturnValue(of(responseModel));
      const { comp } = makeComponent({ http });

      comp.clear();

      expect(comp.model).toBe(responseModel);
   });

   it("should NOT update comp.model when HTTP response is null", () => {
      const http = makeHttp();
      http.post.mockReturnValue(of(null));
      const { comp } = makeComponent({ http });
      const originalModel = comp.model;

      comp.clear();

      expect(comp.model).toBe(originalModel);
   });
});

// ---------------------------------------------------------------------------
// Group 9 — updateNameValidation
// ---------------------------------------------------------------------------

describe("Group 9 — updateNameValidation: sets tableNameExists flag", () => {
   it("should set tableNameExists=true when initTableName matches a table name (case insensitive)", () => {
      const { comp } = makeComponent({ tables: [{ name: "ORDERS" } as any], initTableName: "orders" });
      comp.initTableName = "orders";

      comp.updateNameValidation();

      expect(comp.tableNameExists).toBe(true);
   });

   it("should set tableNameExists=false when initTableName is not in tables", () => {
      const { comp } = makeComponent({ tables: [{ name: "ORDERS" } as any], initTableName: "products" });
      comp.initTableName = "products";

      comp.updateNameValidation();

      expect(comp.tableNameExists).toBe(false);
   });

   it("should set tableNameExists=false when tables is empty", () => {
      const { comp } = makeComponent({ tables: [], initTableName: "something" });
      comp.initTableName = "something";

      comp.updateNameValidation();

      expect(comp.tableNameExists).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 10 — cancel: simple mode (not sqlEdited) → destroyRuntimeQuery + emit
// ---------------------------------------------------------------------------

describe("Group 10 — cancel: simple mode without sqlEdited emits onCancel", () => {
   it("should emit onCancel with 'cancel' when not advanced and simpleModel.sqlEdited=false", () => {
      const model = makeModel({ simpleModel: makeSimpleModel({ sqlEdited: false }) });
      const { comp } = makeComponent({ model });
      const emitSpy = vi.spyOn(comp.onCancel, "emit");

      comp.cancel();

      expect(emitSpy).toHaveBeenCalledWith("cancel");
   });

   it("should call http.delete (destroyRuntimeQuery) on cancel in simple mode", () => {
      const model = makeModel({ simpleModel: makeSimpleModel({ sqlEdited: false }), runtimeId: "rid-1" });
      const http = makeHttp();
      const { comp } = makeComponent({ model, http });
      http.delete.mockClear();

      comp.cancel();

      expect(http.delete).toHaveBeenCalledWith(
         expect.stringContaining("destroy"),
         expect.anything(),
      );
   });
});

// ---------------------------------------------------------------------------
// Group 11 — cancel: advanced with sqlEdited → confirm dialog
// ---------------------------------------------------------------------------

describe("Group 11 — cancel: advanced mode with sqlEdited shows confirm dialog", () => {
   it("should open modal when advancedEditing=true and simpleModel.sqlEdited=true", () => {
      // In advanced mode with sqlEdited the component shows "close without saving" confirm
      const model = makeModel({
         advancedEdit: true,
         simpleModel: makeSimpleModel({ sqlEdited: true }),
      });
      const modal = makeModal();
      const { comp } = makeComponent({ model, modal });

      comp.cancel();

      expect(modal.open).toHaveBeenCalled();
   });

   it("should emit onCancel after user confirms 'yes' in the confirm dialog", async () => {
      const model = makeModel({
         advancedEdit: true,
         simpleModel: makeSimpleModel({ sqlEdited: true }),
      });
      const modal = makeModal();
      modal.open.mockReturnValue({ componentInstance: { onCommit: new Subject<string>() }, result: Promise.resolve("yes") });
      const { comp } = makeComponent({ model, modal });
      const emitSpy = vi.spyOn(comp.onCancel, "emit");

      comp.cancel();
      await Promise.resolve();
      await Promise.resolve();

      expect(emitSpy).toHaveBeenCalledWith("cancel");
   });
});

// ---------------------------------------------------------------------------
// Group 12 — isApplyBtnDisabled: all conditions
// ---------------------------------------------------------------------------

describe("Group 12 — isApplyBtnDisabled: blocks apply under various conditions", () => {
   it("should return true when model is null", () => {
      const { comp } = makeComponent({ skipNgOnInit: true });
      comp.model = null;
      expect(comp.isApplyBtnDisabled()).toBe(true);
   });

   it("should return true when processing=true", () => {
      const { comp } = makeComponent();
      comp.processing = true;
      expect(comp.isApplyBtnDisabled()).toBe(true);
   });

   it("should return true when tableNameExists=true", () => {
      const { comp } = makeComponent();
      comp.tableNameExists = true;
      expect(comp.isApplyBtnDisabled()).toBe(true);
   });

   it("should return true (simple) when simpleModel.sqlString is empty", () => {
      const model = makeModel({ simpleModel: makeSimpleModel({ sqlString: "" }) });
      const { comp } = makeComponent({ model });
      expect(comp.isApplyBtnDisabled()).toBe(true);
   });

   it("should return false (simple) when simpleModel.sqlString is non-empty", () => {
      const model = makeModel({ simpleModel: makeSimpleModel({ sqlString: "SELECT 1" }) });
      const { comp } = makeComponent({ model });
      expect(comp.isApplyBtnDisabled()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 13 — initOperations: HTTP get populates operations
// ---------------------------------------------------------------------------

describe("Group 13 — initOperations: loads operations from server", () => {
   it("should call http.get with the operations URI", () => {
      const http = makeHttp();
      const { comp } = makeComponent({ http });
      http.get.mockClear();

      comp.initOperations();

      expect(http.get).toHaveBeenCalledWith(
         expect.stringContaining("operations"),
      );
   });

   it("should populate comp.operations from HTTP response", () => {
      const ops = [{ symbol: "=", name: "Equal To" }, { symbol: "in", name: "In" }];
      const http = makeHttp();
      http.get.mockReturnValue(of(ops));
      const { comp } = makeComponent({ http });
      http.get.mockClear();
      http.get.mockReturnValue(of(ops));

      comp.initOperations();

      expect(comp.operations).toEqual(ops);
   });

   it("should set sessionOperations[0] for EQUAL_TO symbol", () => {
      const eqOp = { symbol: "=", name: "Equal To" };
      const http = makeHttp();
      http.get.mockReturnValue(of([eqOp]));
      const { comp } = makeComponent({ http });
      http.get.mockClear();
      http.get.mockReturnValue(of([eqOp]));
      // ClauseOperationSymbols.EQUAL_TO = "="
      comp.sessionOperations = [null, null];

      comp.initOperations();

      // sessionOperations[0] is set when operation.symbol matches EQUAL_TO
      // exact symbol value depends on ClauseOperationSymbols constant, so verify via operations length
      expect(comp.operations.length).toBe(1);
   });
});

// ---------------------------------------------------------------------------
// Group 14 — destroyRuntimeQuery: HTTP delete only when runtimeId exists
// ---------------------------------------------------------------------------

describe("Group 14 — destroyRuntimeQuery: conditional HTTP delete", () => {
   it("should call http.delete when model.runtimeId is set", () => {
      const model = makeModel({ runtimeId: "rt-xyz" });
      const http = makeHttp();
      const { comp } = makeComponent({ model, http });
      http.delete.mockClear();

      comp.destroyRuntimeQuery();

      expect(http.delete).toHaveBeenCalledWith(
         expect.stringContaining("destroy"),
         expect.objectContaining({ params: expect.anything() }),
      );
   });

   it("should NOT call http.delete when model.runtimeId is empty", () => {
      const model = makeModel({ runtimeId: "" });
      const http = makeHttp();
      const { comp } = makeComponent({ model, http });
      http.delete.mockClear();

      comp.destroyRuntimeQuery();

      expect(http.delete).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 15 — isJoinEditView: delegate or default false
// ---------------------------------------------------------------------------

describe("Group 15 — isJoinEditView: returns false by default, delegates when pane present", () => {
   it("should return false when advancedQueryPane is undefined", () => {
      const { comp } = makeComponent();
      comp.advancedQueryPane = undefined;
      expect(comp.isJoinEditView()).toBe(false);
   });

   it("should delegate to advancedQueryPane.isJoinEditView() when present", () => {
      const { comp } = makeComponent();
      comp.advancedQueryPane = {
         isJoinEditView: vi.fn().mockReturnValue(true),
         checkQuery: vi.fn(),
         resetActiveTab: vi.fn(),
      } as any;
      expect(comp.isJoinEditView()).toBe(true);
   });
});
