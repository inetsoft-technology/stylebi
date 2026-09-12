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
 * TabularQueryDialog — Pass 2: Risk / Async
 *
 * Risk-first coverage:
 *   Group 1   refreshView — HTTP post updates tabularView; cancelClicked skips update
 *   Group 2   initView — sets dependsOn, refreshButtonExists, cancelButtonExists
 *   Group 3   getDependsOn — recursive collection from editors and buttons
 *   Group 4   hasRefreshButton — recursive search through nested views
 *   Group 5   hasCancelButton — recursive search through nested views
 *   Group 6   clearButtonClicks — sets button.clicked=false recursively
 *   Group 7   clearButtonLoading — sets button.loading=false recursively
 *   Group 8   updateNameValidation — case-insensitive name-exists check
 *   Group 9   createForm — form validators (required, nameSpecialCharacters)
 *   Group 10  nestedViewChanged — updates the correct nested view by row+col
 *   Group 11  formValid — all gating conditions
 */

import { of } from "rxjs";
import {
   makeComponent,
   makeModel,
   makeTabularView,
   makeHttp,
} from "./tabular-query-dialog.component.test-helpers";

// ---------------------------------------------------------------------------
// Group 1 — refreshView
// ---------------------------------------------------------------------------

describe("Group 1 — refreshView: HTTP post updates tabularView", () => {
   it("should call http.post to the refreshView endpoint", () => {
      const http = makeHttp();
      const { comp } = makeComponent({ http });
      http.post.mockClear();

      comp.refreshView();

      const calls = http.post.mock.calls.filter((c: any[]) =>
         (c[0] as string).includes("refreshView"),
      );
      expect(calls.length).toBeGreaterThan(0);
   });

   it("should update model.tabularView from the HTTP response", () => {
      const newView = makeTabularView({ value: "refreshed" });
      const http = makeHttp();
      http.post.mockReturnValue(of(newView));
      const { comp } = makeComponent({ http });
      http.post.mockClear();
      http.post.mockReturnValue(of(newView));

      comp.refreshView();

      expect(comp.model.tabularView).toEqual(newView);
   });

   it("should NOT update model.tabularView when cancelClicked=true", () => {
      const http = makeHttp();
      const originalView = makeTabularView({ value: "original" });
      const newView = makeTabularView({ value: "shouldNotAppear" });
      http.post.mockReturnValue(of(newView));
      const { comp } = makeComponent({ model: makeModel({ tabularView: originalView }), http });
      http.post.mockClear();
      http.post.mockReturnValue(of(newView));

      comp.refreshView(true);

      expect(comp.model.tabularView).toEqual(originalView);
   });

   it("should set showLoading=false on success", () => {
      const http = makeHttp();
      const { comp } = makeComponent({ http });
      comp.showLoading = true;
      http.post.mockClear();

      comp.refreshView();

      expect(comp.showLoading).toBe(false);
   });

   it("should show error dialog when HTTP post errors", () => {
      const http = makeHttp();
      const { comp, modal } = makeComponent({ http });
      http.post.mockClear();
      http.post.mockReturnValue(
         new (require("rxjs").Observable)((observer: any) => {
            observer.error({ error: { message: "Connection refused" } });
         }),
      );

      comp.refreshView();

      expect(modal.open).toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 2 — initView
// ---------------------------------------------------------------------------

describe("Group 2 — initView: sets dependsOn, refreshButtonExists, cancelButtonExists", () => {
   it("should set refreshButtonExists=true when views contain a REFRESH button", () => {
      const model = makeModel({
         tabularView: makeTabularView({
            views: [makeTabularView({ type: "BUTTON", button: { type: "REFRESH", clicked: false, loading: false, views: [] } as any })],
         }),
      });
      const { comp } = makeComponent({ model });
      expect(comp.refreshButtonExists).toBe(true);
   });

   it("should set cancelButtonExists=true when views contain a CANCEL button", () => {
      const model = makeModel({
         tabularView: makeTabularView({
            views: [makeTabularView({ type: "BUTTON", button: { type: "CANCEL", clicked: false, loading: false, views: [] } as any })],
         }),
      });
      const { comp } = makeComponent({ model });
      expect(comp.cancelButtonExists).toBe(true);
   });

   it("should set refreshButtonExists=false when no REFRESH button present", () => {
      const { comp } = makeComponent({ model: makeModel({ tabularView: makeTabularView() }) });
      expect(comp.refreshButtonExists).toBe(false);
   });

   it("should do nothing when tabularView is null", () => {
      const model = makeModel({ tabularView: makeTabularView() });
      const { comp } = makeComponent({ model });
      // Override tabularView after init to simulate null for a fresh initView call
      (comp as any).model = { ...comp.model, tabularView: null };
      expect(() => (comp as any).initView()).not.toThrow();
   });
});

// ---------------------------------------------------------------------------
// Group 3 — getDependsOn
// ---------------------------------------------------------------------------

describe("Group 3 — getDependsOn: collects dependsOn from editors and buttons recursively", () => {
   it("should return empty set for views with no dependsOn", () => {
      const { comp } = makeComponent();
      const result = comp.getDependsOn([makeTabularView()]);
      expect(result.size).toBe(0);
   });

   it("should collect dependsOn from editor", () => {
      const { comp } = makeComponent();
      const view = makeTabularView({ editor: { dependsOn: ["field1", "field2"] } as any });
      const result = comp.getDependsOn([view]);
      expect(result.has("field1")).toBe(true);
      expect(result.has("field2")).toBe(true);
   });

   it("should collect dependsOn from button", () => {
      const { comp } = makeComponent();
      const view = makeTabularView({
         type: "BUTTON",
         button: { type: "REFRESH", clicked: false, loading: false, dependsOn: ["ds"] } as any,
      });
      const result = comp.getDependsOn([view]);
      expect(result.has("ds")).toBe(true);
   });

   it("should collect dependsOn recursively from nested views", () => {
      const { comp } = makeComponent();
      const inner = makeTabularView({ editor: { dependsOn: ["nested-dep"] } as any });
      const outer = makeTabularView({ views: [inner] });
      const result = comp.getDependsOn([outer]);
      expect(result.has("nested-dep")).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 4 — hasRefreshButton
// ---------------------------------------------------------------------------

describe("Group 4 — hasRefreshButton: recursive REFRESH button search", () => {
   it("should return false for empty views", () => {
      const { comp } = makeComponent();
      expect(comp.hasRefreshButton([])).toBe(false);
   });

   it("should return true when a REFRESH button is at the top level", () => {
      const { comp } = makeComponent();
      const view = makeTabularView({
         type: "BUTTON",
         button: { type: "REFRESH", clicked: false, loading: false } as any,
      });
      expect(comp.hasRefreshButton([view])).toBe(true);
   });

   it("should return true when REFRESH button is nested", () => {
      const { comp } = makeComponent();
      const inner = makeTabularView({
         type: "BUTTON",
         button: { type: "REFRESH", clicked: false, loading: false } as any,
      });
      const outer = makeTabularView({ views: [inner] });
      expect(comp.hasRefreshButton([outer])).toBe(true);
   });

   it("should return false when no REFRESH button exists (only CANCEL)", () => {
      const { comp } = makeComponent();
      const view = makeTabularView({
         type: "BUTTON",
         button: { type: "CANCEL", clicked: false, loading: false } as any,
      });
      expect(comp.hasRefreshButton([view])).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 5 — hasCancelButton
// ---------------------------------------------------------------------------

describe("Group 5 — hasCancelButton: recursive CANCEL button search", () => {
   it("should return false for empty views", () => {
      const { comp } = makeComponent();
      expect(comp.hasCancelButton([])).toBe(false);
   });

   it("should return true for a top-level CANCEL button", () => {
      const { comp } = makeComponent();
      const view = makeTabularView({
         type: "BUTTON",
         button: { type: "CANCEL", clicked: false, loading: false } as any,
      });
      expect(comp.hasCancelButton([view])).toBe(true);
   });

   it("should return false when only REFRESH button exists", () => {
      const { comp } = makeComponent();
      const view = makeTabularView({
         type: "BUTTON",
         button: { type: "REFRESH", clicked: false, loading: false } as any,
      });
      expect(comp.hasCancelButton([view])).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 6 — clearButtonClicks
// ---------------------------------------------------------------------------

describe("Group 6 — clearButtonClicks: sets button.clicked=false on all button views", () => {
   it("should set button.clicked=false on top-level button views", () => {
      const { comp } = makeComponent();
      const btn = { type: "REFRESH", clicked: true, loading: false };
      const view = makeTabularView({ type: "BUTTON", button: btn as any });

      comp.clearButtonClicks([view]);

      expect(btn.clicked).toBe(false);
   });

   it("should set button.clicked=false recursively", () => {
      const { comp } = makeComponent();
      const btn = { type: "CANCEL", clicked: true, loading: false };
      const inner = makeTabularView({ type: "BUTTON", button: btn as any });
      const outer = makeTabularView({ views: [inner] });

      comp.clearButtonClicks([outer]);

      expect(btn.clicked).toBe(false);
   });

   it("should NOT modify non-button views", () => {
      const { comp } = makeComponent();
      const view = makeTabularView({ type: "TEXT", button: null });

      expect(() => comp.clearButtonClicks([view])).not.toThrow();
   });
});

// ---------------------------------------------------------------------------
// Group 7 — clearButtonLoading
// ---------------------------------------------------------------------------

describe("Group 7 — clearButtonLoading: sets button.loading=false on all button views", () => {
   it("should set button.loading=false on top-level button views", () => {
      const { comp } = makeComponent();
      const btn = { type: "REFRESH", clicked: false, loading: true };
      comp.model.tabularView = makeTabularView({
         views: [makeTabularView({ type: "BUTTON", button: btn as any })],
      });

      comp.clearButtonLoading();

      expect(btn.loading).toBe(false);
   });

   it("should set button.loading=false recursively when views passed explicitly", () => {
      const { comp } = makeComponent();
      const btn = { type: "CANCEL", clicked: false, loading: true };
      const inner = makeTabularView({ type: "BUTTON", button: btn as any });
      const outer = makeTabularView({ views: [inner] });

      comp.clearButtonLoading([outer]);

      expect(btn.loading).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 8 — updateNameValidation
// ---------------------------------------------------------------------------

describe("Group 8 — updateNameValidation: case-insensitive name-exists check", () => {
   it("should set tableNameExists=true when name matches a table name (case insensitive)", () => {
      const { comp } = makeComponent({ tables: [{ name: "ORDERS" } as any], initTableName: "orders" });
      comp.initTableName = "orders";

      comp.updateNameValidation();

      expect(comp.tableNameExists).toBe(true);
   });

   it("should set tableNameExists=false when name is not in tables", () => {
      const { comp } = makeComponent({ tables: [{ name: "ORDERS" } as any], initTableName: "products" });
      comp.initTableName = "products";

      comp.updateNameValidation();

      expect(comp.tableNameExists).toBe(false);
   });

   it("should set tableNameExists=false when tables is empty", () => {
      const { comp } = makeComponent({ tables: [], initTableName: "anything" });
      comp.initTableName = "anything";

      comp.updateNameValidation();

      expect(comp.tableNameExists).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 9 — createForm
// ---------------------------------------------------------------------------

describe("Group 9 — createForm: name control required + nameSpecialCharacters", () => {
   it("should create a form with a name control", () => {
      const { comp } = makeComponent();
      expect(comp.form?.get("name")).toBeTruthy();
   });

   it("should be valid with a normal name", () => {
      const { comp } = makeComponent({ initTableName: "my_table" });
      expect(comp.form.get("name")?.valid).toBe(true);
   });

   it("should be invalid when name is empty (required validator)", () => {
      const { comp } = makeComponent({ initTableName: "" });
      expect(comp.form.get("name")?.invalid).toBe(true);
   });

   it("should have nameSpecialCharacters error for name with '['", () => {
      const { comp } = makeComponent({ initTableName: "bad[name" });
      comp.form.get("name")?.setValue("bad[name");
      expect(comp.form.get("name")?.errors?.["nameSpecialCharacters"]).toBeTruthy();
   });
});

// ---------------------------------------------------------------------------
// Group 10 — nestedViewChanged
// ---------------------------------------------------------------------------

describe("Group 10 — nestedViewChanged: updates the matching row+col in nested views", () => {
   it("should replace the matching child view by row+col under the given parent", () => {
      const { comp } = makeComponent();
      const original = makeTabularView({ row: 1, col: 0, value: "old" });
      const parent = makeTabularView({ views: [original] });
      const root = makeTabularView({ views: [parent] });

      const updated = makeTabularView({ row: 1, col: 0, value: "new" });
      comp.nestedViewChanged(root, updated, parent);

      expect(parent.views[0].value).toBe("new");
   });

   it("should NOT replace when row or col does not match", () => {
      const { comp } = makeComponent();
      const original = makeTabularView({ row: 1, col: 0, value: "old" });
      const parent = makeTabularView({ views: [original] });
      const root = makeTabularView({ views: [parent] });

      const updated = makeTabularView({ row: 2, col: 0, value: "new" });
      comp.nestedViewChanged(root, updated, parent);

      expect(parent.views[0].value).toBe("old");
   });
});

// ---------------------------------------------------------------------------
// Group 11 — formValid lambda
// ---------------------------------------------------------------------------

describe("Group 11 — formValid: gating condition checks", () => {
   it("should return false when valid=false", () => {
      const { comp } = makeComponent();
      comp.valid = false;
      expect(comp.formValid()).toBe(false);
   });

   it("should return false when model is null", () => {
      const { comp } = makeComponent({ skipNgOnInit: true });
      comp.valid = true;
      comp.model = null;
      comp.form = (comp as any).createForm();
      expect(comp.formValid()).toBe(false);
   });

   it("should return true when valid=true, model and dataSource set, form valid, no tables", () => {
      const { comp } = makeComponent();
      comp.valid = true;
      comp.tables = null;
      expect(comp.formValid()).toBe(true);
   });

   it("should return false when form is invalid (empty name)", () => {
      const { comp } = makeComponent({ initTableName: "" });
      comp.valid = true;
      comp.tables = [{ name: "T1" } as any];
      expect(comp.formValid()).toBe(false);
   });
});
