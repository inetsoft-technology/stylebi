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
 * SQLQueryDialog — Pass 1: Interaction
 *
 * Method coverage:
 *   Group 1   ngOnInit — loads model and data-source tree from controller
 *   Group 2   detachedCrossJoin — simple mode (sqlEdited / tables / joins)
 *   Group 3   detachedCrossJoin — advanced mode (delegates to queryModelService)
 *   Group 4   ok — happy path (simple mode, no cross join)
 *   Group 5   ok — cross-join detected, crossJoinEnabled=false → message dialog, no emit
 *   Group 6   ok — cross-join detected, crossJoinEnabled=true → confirm dialog
 *   Group 7   ok — advancedEditing mode → checkQueryValidity
 *   Group 8   apply — simple mode emits onApply
 *   Group 9   checkQueryValidity — calls advancedQueryPane.checkQuery() → emits
 *   Group 10  onSwitchChange — switching to advanced calls refreshModelOnModeChange(true)
 *   Group 11  onSwitchChange — switching to simple shows confirm dialog
 *   Group 12  refreshModelOnModeChange — HTTP post updates model and calls loadDataSourceTree
 *   Group 13  checkIfNotSaved — stops escape when sqlEdited and not advanced
 */

import { of } from "rxjs";
import {
   makeComponent,
   makeController,
   makeModel,
   makeSimpleModel,
   makeModal,
   makeHttp,
} from "./sql-query-dialog.component.test-helpers";
import { ComponentTool } from "../../../common/util/component-tool";

// ---------------------------------------------------------------------------
// Group 1 — ngOnInit
// ---------------------------------------------------------------------------

describe("Group 1 — ngOnInit: loads model and tree from controller", () => {
   it("should call controller.getModel() during init", () => {
      const { controller } = makeComponent();
      expect(controller.getModel).toHaveBeenCalled();
   });

   it("should set comp.model from controller.getModel() response", () => {
      const model = makeModel({ name: "salesQuery" });
      const { comp } = makeComponent({ model });
      expect(comp.model?.name).toBe("salesQuery");
   });

   it("should call controller.getDataSourceTree when model has a dataSource", () => {
      const { controller } = makeComponent({ model: makeModel({ dataSource: "ds1" }) });
      expect(controller.getDataSourceTree).toHaveBeenCalled();
   });

   it("should set dataSourceTreeRoot from controller.getDataSourceTree response", () => {
      const tree = { children: [{ label: "ORDERS" }] };
      const controller = makeController({
         getDataSourceTree: vi.fn().mockReturnValue(of(tree)),
      });
      const { comp } = makeComponent({ controller });
      expect(comp.dataSourceTreeRoot).toEqual(tree);
   });

   it("should skip getDataSourceTree when model has no dataSource", () => {
      const model = makeModel({ dataSource: null });
      const { controller } = makeComponent({ model });
      expect(controller.getDataSourceTree).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 2 — detachedCrossJoin: simple mode
// ---------------------------------------------------------------------------

describe("Group 2 — detachedCrossJoin: simple mode", () => {
   it("should return false when only one table (no cross-join risk)", () => {
      const model = makeModel({
         simpleModel: makeSimpleModel({
            tables: { "T1": {} as any },
            joins: [],
            sqlEdited: false,
         }),
      });
      const { comp } = makeComponent({ model });
      expect(comp.detachedCrossJoin()).toBe(false);
   });

   it("should return true when multiple tables and no joins (detached cross join)", () => {
      const model = makeModel({
         simpleModel: makeSimpleModel({
            tables: { "T1": {} as any, "T2": {} as any },
            joins: [],
            sqlEdited: false,
         }),
      });
      const { comp } = makeComponent({ model });
      expect(comp.detachedCrossJoin()).toBe(true);
   });

   it("should return false when multiple tables but joins exist", () => {
      const model = makeModel({
         simpleModel: makeSimpleModel({
            tables: { "T1": {} as any, "T2": {} as any },
            joins: [{ joinType: "=" } as any],
            sqlEdited: false,
         }),
      });
      const { comp } = makeComponent({ model });
      expect(comp.detachedCrossJoin()).toBe(false);
   });

   it("should return false when sqlEdited=true even if tables are unjoined", () => {
      const model = makeModel({
         simpleModel: makeSimpleModel({
            tables: { "T1": {} as any, "T2": {} as any },
            joins: [],
            sqlEdited: true,
         }),
      });
      const { comp } = makeComponent({ model });
      expect(comp.detachedCrossJoin()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 3 — detachedCrossJoin: advanced mode
// ---------------------------------------------------------------------------

describe("Group 3 — detachedCrossJoin: advanced mode", () => {
   it("should return true when queryModelService has unjoined tables", () => {
      const model = makeModel({ advancedEdit: true });
      const queryModelSvc = { emitGraphViewChange: vi.fn(), getUnjoinedTables: vi.fn().mockReturnValue(["T1"]) };
      const { comp } = makeComponent({ model, queryModelSvc: queryModelSvc as any });
      expect(comp.detachedCrossJoin()).toBe(true);
   });

   it("should return false when queryModelService reports no unjoined tables", () => {
      const model = makeModel({ advancedEdit: true });
      const queryModelSvc = { emitGraphViewChange: vi.fn(), getUnjoinedTables: vi.fn().mockReturnValue([]) };
      const { comp } = makeComponent({ model, queryModelSvc: queryModelSvc as any });
      expect(comp.detachedCrossJoin()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 4 — ok: happy path, simple mode, no cross join
// ---------------------------------------------------------------------------

describe("Group 4 — ok: simple mode, no cross join, emits onCommit", () => {
   it("should emit onCommit with model and controller url", () => {
      const { comp } = makeComponent();
      const emitSpy = vi.spyOn(comp.onCommit, "emit");

      comp.ok();

      expect(emitSpy).toHaveBeenCalledOnce();
      const payload = emitSpy.mock.calls[0][0];
      expect(payload.model).toBe(comp.model);
      expect(payload.controller).toBe("/events/ws/sql");
   });

   it("should set model.name from initTableName when tables are provided", () => {
      const { comp } = makeComponent({ tables: [{ name: "other" } as any], initTableName: "newName" });
      comp.ok();
      expect(comp.model?.name).toBe("newName");
   });

   it("should set model.closeDialog=true before emitting", () => {
      const { comp } = makeComponent();
      comp.ok();
      expect(comp.model?.closeDialog).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 5 — ok: cross-join detected, crossJoinEnabled=false → message, no emit
// ---------------------------------------------------------------------------

describe("Group 5 — ok: cross join forbidden → shows message dialog, does not emit", () => {
   it("should show message dialog and not emit onCommit", () => {
      const model = makeModel({
         simpleModel: makeSimpleModel({
            tables: { "T1": {} as any, "T2": {} as any },
            joins: [],
            sqlEdited: false,
         }),
      });
      const modal = makeModal();
      const { comp } = makeComponent({ model, modal, crossJoinEnabled: false });
      const commitSpy = vi.spyOn(comp.onCommit, "emit");
      const msgSpy = vi.spyOn(ComponentTool, "showMessageDialog").mockReturnValue(
         Promise.resolve("ok"),
      );

      comp.ok();

      expect(msgSpy).toHaveBeenCalled();
      expect(commitSpy).not.toHaveBeenCalled();
      msgSpy.mockRestore();
   });
});

// ---------------------------------------------------------------------------
// Group 6 — ok: cross-join detected, crossJoinEnabled=true → confirm dialog
// ---------------------------------------------------------------------------

describe("Group 6 — ok: cross join allowed → shows confirm dialog", () => {
   it("should open confirm dialog when crossJoinEnabled and tables unjoined", () => {
      const model = makeModel({
         simpleModel: makeSimpleModel({
            tables: { "T1": {} as any, "T2": {} as any },
            joins: [],
            sqlEdited: false,
         }),
      });
      const { comp, modal } = makeComponent({ model, crossJoinEnabled: true });

      comp.ok();

      expect(modal.open).toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 7 — ok: advanced mode → checkQueryValidity
// ---------------------------------------------------------------------------

describe("Group 7 — ok: advanced mode delegates to checkQueryValidity", () => {
   it("should not emit directly when advancedEditing, instead calls advancedQueryPane", () => {
      const model = makeModel({ advancedEdit: true, advancedModel: {} as any });
      const { comp } = makeComponent({ model });
      const commitSpy = vi.spyOn(comp.onCommit, "emit");
      const mockPane = { checkQuery: vi.fn().mockResolvedValue(undefined), isJoinEditView: vi.fn().mockReturnValue(false), resetActiveTab: vi.fn() };
      comp.advancedQueryPane = mockPane as any;

      comp.ok();

      expect(mockPane.checkQuery).toHaveBeenCalled();
      // onCommit is emitted inside the checkQuery Promise — not synchronously
      expect(commitSpy).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 8 — apply: simple mode emits onApply
// ---------------------------------------------------------------------------

describe("Group 8 — apply: simple mode emits onApply with collapse flag", () => {
   it("should emit onApply with collapse=false payload", () => {
      const { comp } = makeComponent();
      const applySpy = vi.spyOn(comp.onApply, "emit");

      comp.apply(false);

      expect(applySpy).toHaveBeenCalledOnce();
      const payload = applySpy.mock.calls[0][0];
      expect(payload.collapse).toBe(false);
      expect(payload.result?.model).toBe(comp.model);
   });

   it("should emit onApply with collapse=true when event=true", () => {
      const { comp } = makeComponent();
      const applySpy = vi.spyOn(comp.onApply, "emit");

      comp.apply(true);

      const payload = applySpy.mock.calls[0][0];
      expect(payload.collapse).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 9 — checkQueryValidity: calls advancedQueryPane.checkQuery then emits
// ---------------------------------------------------------------------------

describe("Group 9 — checkQueryValidity: calls advancedQueryPane then emits", () => {
   it("should emit onCommit after advancedQueryPane.checkQuery resolves", async () => {
      const { comp } = makeComponent();
      const commitSpy = vi.spyOn(comp.onCommit, "emit");
      const mockPane = { checkQuery: vi.fn().mockResolvedValue(undefined) };
      comp.advancedQueryPane = mockPane as any;
      const payload = { model: comp.model, controller: "/events/ws/sql" };

      comp.checkQueryValidity(payload);
      await Promise.resolve();

      expect(commitSpy).toHaveBeenCalledWith(payload);
   });

   it("should emit onApply (not onCommit) when apply=true", async () => {
      const { comp } = makeComponent();
      const commitSpy = vi.spyOn(comp.onCommit, "emit");
      const applySpy = vi.spyOn(comp.onApply, "emit");
      const mockPane = { checkQuery: vi.fn().mockResolvedValue(undefined) };
      comp.advancedQueryPane = mockPane as any;
      const payload = { result: { model: comp.model } };

      comp.checkQueryValidity(payload, true);
      await Promise.resolve();

      expect(applySpy).toHaveBeenCalledWith(payload);
      expect(commitSpy).not.toHaveBeenCalled();
   });

   it("should do nothing when advancedQueryPane is undefined", () => {
      const { comp } = makeComponent();
      const commitSpy = vi.spyOn(comp.onCommit, "emit");
      comp.advancedQueryPane = undefined;

      comp.checkQueryValidity({ model: comp.model });

      expect(commitSpy).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 10 — onSwitchChange: switching to advanced
// ---------------------------------------------------------------------------

describe("Group 10 — onSwitchChange: switching to advanced calls refreshModelOnModeChange(true)", () => {
   it("should set helpLinkKey to AdvancedQuery when switching to advanced", () => {
      const { comp } = makeComponent();
      const event = { target: { checked: true } };

      comp.onSwitchChange(event);

      expect(comp.helpLinkKey).toBe("AdvancedQuery");
   });

   it("should call http.post (via refreshModelOnModeChange) when switching to advanced", () => {
      const http = makeHttp();
      const { comp } = makeComponent({ http });
      const event = { target: { checked: true } };
      // refreshModelOnModeChange calls http.post
      http.post.mockClear();

      comp.onSwitchChange(event);

      expect(http.post).toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 11 — onSwitchChange: switching to simple shows confirm dialog
// ---------------------------------------------------------------------------

describe("Group 11 — onSwitchChange: switching to simple shows confirm dialog", () => {
   it("should open confirm dialog when switching from advanced to simple", () => {
      const model = makeModel({ advancedEdit: true });
      const { comp, modal } = makeComponent({ model });
      const event = { target: { checked: false } };

      comp.onSwitchChange(event);

      expect(modal.open).toHaveBeenCalled();
   });

   it("should reset model.advancedEdit=true and event.target.checked when user picks no", async () => {
      const model = makeModel({ advancedEdit: true });
      const modal = makeModal();
      modal.open.mockReturnValue({
         componentInstance: {},
         result: Promise.resolve("no"),
      });
      const { comp } = makeComponent({ model, modal });
      const event = { target: { checked: false } };

      comp.onSwitchChange(event);
      await Promise.resolve();
      await Promise.resolve();

      expect(comp.model?.advancedEdit).toBe(true);
      expect(event.target.checked).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 12 — refreshModelOnModeChange: HTTP post updates model
// ---------------------------------------------------------------------------

describe("Group 12 — refreshModelOnModeChange: HTTP post updates model and reloads tree", () => {
   it("should call http.post with CHANGE_EDIT_MODE_URI", () => {
      const http = makeHttp();
      const { comp } = makeComponent({ http });
      http.post.mockClear();

      comp.refreshModelOnModeChange(true);

      expect(http.post).toHaveBeenCalledWith(
         expect.stringContaining("change-edit-mode"),
         comp.model,
         expect.anything(),
      );
   });

   it("should update comp.model from the HTTP response", () => {
      const responseModel = makeModel({ name: "updatedTable" });
      const http = makeHttp();
      http.post.mockReturnValue(of(responseModel));
      const { comp } = makeComponent({ http });

      comp.refreshModelOnModeChange(false);

      expect(comp.model).toBe(responseModel);
   });

   it("should call controller.getDataSourceTree after updating the model", () => {
      const http = makeHttp();
      http.post.mockReturnValue(of(makeModel()));
      const { comp, controller } = makeComponent({ http });
      (controller.getDataSourceTree as ReturnType<typeof vi.fn>).mockClear();

      comp.refreshModelOnModeChange(true);

      expect(controller.getDataSourceTree).toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 13 — checkIfNotSaved: stops ESC when sqlEdited in simple mode
// ---------------------------------------------------------------------------

describe("Group 13 — checkIfNotSaved: calls cancel on ESC when sqlEdited", () => {
   it("should call cancel() when not advanced, sqlEdited=true, and not repeat", () => {
      const model = makeModel({
         simpleModel: makeSimpleModel({ sqlEdited: true }),
      });
      const { comp } = makeComponent({ model });
      const cancelSpy = vi.spyOn(comp, "cancel");
      const event = { repeat: false, stopPropagation: vi.fn() } as any;

      comp.checkIfNotSaved(event);

      expect(event.stopPropagation).toHaveBeenCalled();
      expect(cancelSpy).toHaveBeenCalled();
   });

   it("should not call cancel() when sqlEdited=false", () => {
      const model = makeModel({ simpleModel: makeSimpleModel({ sqlEdited: false }) });
      const { comp } = makeComponent({ model });
      const cancelSpy = vi.spyOn(comp, "cancel");
      const event = { repeat: false, stopPropagation: vi.fn() } as any;

      comp.checkIfNotSaved(event);

      expect(cancelSpy).not.toHaveBeenCalled();
   });

   it("should not call cancel() when event.repeat=true", () => {
      const model = makeModel({ simpleModel: makeSimpleModel({ sqlEdited: true }) });
      const { comp } = makeComponent({ model });
      const cancelSpy = vi.spyOn(comp, "cancel");
      const event = { repeat: true, stopPropagation: vi.fn() } as any;

      comp.checkIfNotSaved(event);

      expect(cancelSpy).not.toHaveBeenCalled();
   });

   it("should not call cancel() when advancedEditing=true", () => {
      const model = makeModel({ advancedEdit: true, simpleModel: makeSimpleModel({ sqlEdited: true }) });
      const { comp } = makeComponent({ model });
      const cancelSpy = vi.spyOn(comp, "cancel");
      const event = { repeat: false, stopPropagation: vi.fn() } as any;

      comp.checkIfNotSaved(event);

      expect(cancelSpy).not.toHaveBeenCalled();
   });
});
