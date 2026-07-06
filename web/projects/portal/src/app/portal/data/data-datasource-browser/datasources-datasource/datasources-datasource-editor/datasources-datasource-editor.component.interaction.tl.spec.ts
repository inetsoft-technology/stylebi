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
 * DatasourcesDatasourceEditorComponent — Pass 1: Interaction tests (pure logic, no HTTP).
 *
 * Covers: hasRefreshButton, hasCancelButton, getDependsOn, onValidChanged,
 * clearButtonClicks, clearButtonLoading, updateDatasourceName, initView,
 * datasource setter, usedNames setter, buttonClicked routing, memory leak (ngOnDestroy).
 *
 * HTTP flows (onViewChanged → refreshView, refreshMetadata, authorize OAuth chain) → Pass 2 (risk).
 *
 * Mocking strategy: DebounceService, OAuthAuthorizationService, and NgbModal are provided
 * as vi.fn() mocks (see test-helpers.ts). No MSW is used in this file — there are no HTTP
 * calls in the pure-logic paths under test. The sibling risk spec uses MSW for HTTP flows.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
   makeDataSource,
   makeTabularButton,
   makeTabularView,
   renderEditor,
   resetMocks,
} from "./datasources-datasource-editor.component.test-helpers";

beforeEach(() => resetMocks());
afterEach(() => vi.restoreAllMocks());

// ── Group 1: hasRefreshButton ──────────────────────────────────────────────

describe("DatasourcesDatasourceEditor — hasRefreshButton", () => {

   it("returns false for empty views array", async () => {
      const { comp } = await renderEditor();
      expect(comp.hasRefreshButton([])).toBe(false);
   });

   it("returns true when a direct BUTTON+REFRESH view exists", async () => {
      const { comp } = await renderEditor();
      const view = makeTabularView({ type: "BUTTON", button: makeTabularButton({ type: "REFRESH" }), views: [] });
      expect(comp.hasRefreshButton([view])).toBe(true);
   });

   it("returns false when BUTTON type is not REFRESH", async () => {
      const { comp } = await renderEditor();
      const view = makeTabularView({ type: "BUTTON", button: makeTabularButton({ type: "CANCEL" }), views: [] });
      expect(comp.hasRefreshButton([view])).toBe(false);
   });

   it("returns true when REFRESH button is nested in a child view", async () => {
      const { comp } = await renderEditor();
      const inner = makeTabularView({ type: "BUTTON", button: makeTabularButton({ type: "REFRESH" }), views: [] });
      const outer = makeTabularView({ type: "EDITOR", views: [inner] });
      expect(comp.hasRefreshButton([outer])).toBe(true);
   });

   it("returns false when no nested view contains REFRESH", async () => {
      const { comp } = await renderEditor();
      const inner = makeTabularView({ type: "EDITOR", views: [] });
      const outer = makeTabularView({ type: "EDITOR", views: [inner] });
      expect(comp.hasRefreshButton([outer])).toBe(false);
   });
});

// ── Group 2: hasCancelButton ──────────────────────────────────────────────

describe("DatasourcesDatasourceEditor — hasCancelButton", () => {

   it("returns false for empty views array", async () => {
      const { comp } = await renderEditor();
      expect(comp.hasCancelButton([])).toBe(false);
   });

   it("returns true when a direct BUTTON+CANCEL view exists", async () => {
      const { comp } = await renderEditor();
      const view = makeTabularView({ type: "BUTTON", button: makeTabularButton({ type: "CANCEL" }), views: [] });
      expect(comp.hasCancelButton([view])).toBe(true);
   });

   it("returns false for BUTTON with non-CANCEL type", async () => {
      const { comp } = await renderEditor();
      const view = makeTabularView({ type: "BUTTON", button: makeTabularButton({ type: "REFRESH" }), views: [] });
      expect(comp.hasCancelButton([view])).toBe(false);
   });

   it("returns true when CANCEL button is nested in a child view", async () => {
      const { comp } = await renderEditor();
      const inner = makeTabularView({ type: "BUTTON", button: makeTabularButton({ type: "CANCEL" }), views: [] });
      const outer = makeTabularView({ type: "EDITOR", views: [inner] });
      expect(comp.hasCancelButton([outer])).toBe(true);
   });
});

// ── Group 3: getDependsOn ─────────────────────────────────────────────────

describe("DatasourcesDatasourceEditor — getDependsOn", () => {

   it("returns empty set for empty views array", async () => {
      const { comp } = await renderEditor();
      expect(comp.getDependsOn([]).size).toBe(0);
   });

   it("collects dependsOn from editor property", async () => {
      const { comp } = await renderEditor();
      const view = makeTabularView({ editor: { dependsOn: ["propA", "propB"] } as any, views: [] });
      const result = comp.getDependsOn([view]);
      expect(result.has("propA")).toBe(true);
      expect(result.has("propB")).toBe(true);
   });

   it("collects dependsOn from button property", async () => {
      const { comp } = await renderEditor();
      const view = makeTabularView({
         type: "BUTTON",
         button: makeTabularButton({ type: "REFRESH", dependsOn: ["propC"] }),
         views: [],
      });
      const result = comp.getDependsOn([view]);
      expect(result.has("propC")).toBe(true);
   });

   it("collects dependsOn recursively from nested views", async () => {
      const { comp } = await renderEditor();
      const inner = makeTabularView({ editor: { dependsOn: ["innerProp"] } as any, views: [] });
      const outer = makeTabularView({ views: [inner] });
      const result = comp.getDependsOn([outer]);
      expect(result.has("innerProp")).toBe(true);
   });

   it("deduplicates values appearing in multiple views", async () => {
      const { comp } = await renderEditor();
      const v1 = makeTabularView({ editor: { dependsOn: ["shared"] } as any, views: [] });
      const v2 = makeTabularView({ editor: { dependsOn: ["shared"] } as any, views: [] });
      const result = comp.getDependsOn([v1, v2]);
      expect(result.size).toBe(1);
   });
});

// ── Group 4: onValidChanged ───────────────────────────────────────────────

describe("DatasourcesDatasourceEditor — onValidChanged", () => {

   it("emits datasourceValid=false when called with false", async () => {
      const { comp } = await renderEditor();
      const emitted: boolean[] = [];
      comp.datasourceValid.subscribe(v => emitted.push(v));

      comp.onValidChanged(false);

      expect(emitted).toContain(false);
   });

   it("emits datasourceValid=true after being reset to true", async () => {
      const { comp } = await renderEditor();
      const emitted: boolean[] = [];
      comp.datasourceValid.subscribe(v => emitted.push(v));

      comp.onValidChanged(false);
      comp.onValidChanged(true);

      expect(emitted[emitted.length - 1]).toBe(true);
   });
});

// ── Group 5: clearButtonClicks ────────────────────────────────────────────

describe("DatasourcesDatasourceEditor — clearButtonClicks", () => {

   it("sets clicked=false on direct BUTTON views", async () => {
      const { comp } = await renderEditor();
      const btn = makeTabularButton({ clicked: true });
      const view = makeTabularView({ type: "BUTTON", button: btn, views: [] });

      comp.clearButtonClicks([view]);

      expect(btn.clicked).toBe(false);
   });

   it("clears clicked on nested BUTTON views recursively", async () => {
      const { comp } = await renderEditor();
      const innerBtn = makeTabularButton({ clicked: true });
      const inner = makeTabularView({ type: "BUTTON", button: innerBtn, views: [] });
      const outer = makeTabularView({ type: "EDITOR", views: [inner] });

      comp.clearButtonClicks([outer]);

      expect(innerBtn.clicked).toBe(false);
   });
});

// ── Group 6: clearButtonLoading ───────────────────────────────────────────

describe("DatasourcesDatasourceEditor — clearButtonLoading", () => {

   it("sets loading=false on a BUTTON view", async () => {
      const { comp } = await renderEditor();
      const btn = makeTabularButton({ loading: true });
      const view = makeTabularView({ type: "BUTTON", button: btn, views: [] });

      comp.clearButtonLoading([view]);

      expect(btn.loading).toBe(false);
   });

   it("clears loading on nested BUTTON views recursively", async () => {
      const { comp } = await renderEditor();
      const innerBtn = makeTabularButton({ loading: true });
      const inner = makeTabularView({ type: "BUTTON", button: innerBtn, views: [] });
      const outer = makeTabularView({ type: "EDITOR", views: [inner] });

      comp.clearButtonLoading([outer]);

      expect(innerBtn.loading).toBe(false);
   });
});

// ── Group 7: updateDatasourceName ─────────────────────────────────────────

describe("DatasourcesDatasourceEditor — updateDatasourceName", () => {

   it("sets oldName to current name when name changes and oldName is not set", async () => {
      const { comp } = await renderEditor({ datasource: makeDataSource({ name: "Original", oldName: null }) });

      comp.updateDatasourceName("NewName");

      expect(comp.datasource.oldName).toBe("Original");
   });

   it("does not override oldName when it is already set", async () => {
      const { comp } = await renderEditor({ datasource: makeDataSource({ name: "Current", oldName: "AlreadySet" }) });

      comp.updateDatasourceName("Another");

      expect(comp.datasource.oldName).toBe("AlreadySet");
   });

   it("updates datasource.name to the new value", async () => {
      const { comp } = await renderEditor({ datasource: makeDataSource({ name: "Original", oldName: null }) });

      comp.updateDatasourceName("Updated");

      expect(comp.datasource.name).toBe("Updated");
   });
});

// ── Group 8: initView ──────────────────────────────────────────────────────

describe("DatasourcesDatasourceEditor — initView", () => {

   it("sets nameGroup 'name' control value to datasource.name", async () => {
      const { comp } = await renderEditor({ datasource: makeDataSource({ name: "MySource" }) });

      expect(comp.nameGroup.get("name").value).toBe("MySource");
   });

   it("sets cancelButtonExists=true when tabularView contains a CANCEL button", async () => {
      const cancelView = makeTabularView({
         type: "BUTTON",
         button: makeTabularButton({ type: "CANCEL" }),
         views: [],
      });
      const tabView = makeTabularView({ views: [cancelView] });
      const { comp } = await renderEditor({ datasource: makeDataSource({ tabularView: tabView }) });

      expect(comp.cancelButtonExists).toBe(true);
   });

   it("sets cancelButtonExists=false when tabularView has no CANCEL button", async () => {
      const refreshView = makeTabularView({
         type: "BUTTON",
         button: makeTabularButton({ type: "REFRESH" }),
         views: [],
      });
      const tabView = makeTabularView({ views: [refreshView] });
      const { comp } = await renderEditor({ datasource: makeDataSource({ tabularView: tabView }) });

      expect(comp.cancelButtonExists).toBe(false);
   });
});

// ── Group 9: datasource setter ────────────────────────────────────────────

describe("DatasourcesDatasourceEditor — datasource setter", () => {

   it("stores the provided datasource reference (accessible via comp.datasource)", async () => {
      const ds = makeDataSource({ name: "TestDS" });
      const { comp } = await renderEditor({ datasource: ds });

      // The setter stores the reference directly in _datasource
      expect(comp.datasource).toBe(ds);
   });

   it("calls initView which updates the nameGroup 'name' control", async () => {
      const { comp } = await renderEditor({ datasource: makeDataSource({ name: "First" }) });

      comp.datasource = makeDataSource({ name: "Second" });

      expect(comp.nameGroup.get("name").value).toBe("Second");
   });
});

// ── Group 10: usedNames setter ────────────────────────────────────────────

describe("DatasourcesDatasourceEditor — usedNames setter", () => {

   it("replaces internal array contents when a new array is provided", async () => {
      const { comp } = await renderEditor({ usedNames: ["a", "b"] });

      comp.usedNames = ["c", "d"];

      expect(comp.usedNames).toEqual(["c", "d"]);
   });

   it("clears internal array when null is provided", async () => {
      const { comp } = await renderEditor({ usedNames: ["a"] });

      comp.usedNames = null;

      expect(comp.usedNames).toEqual([]);
   });
});

// ── Group 11: buttonClicked routing ───────────────────────────────────────

describe("DatasourcesDatasourceEditor — buttonClicked", () => {

   it("routes OAUTH button to authorize()", async () => {
      const { comp } = await renderEditor();
      const spy = vi.spyOn(comp, "authorize").mockImplementation(() => {});
      try {
         const btn = makeTabularButton({ type: "OAUTH" });
         comp.buttonClicked(btn);
         expect(spy).toHaveBeenCalledWith(btn);
      } finally {
         spy.mockRestore();
      }
   });

   it("does not call authorize for non-OAUTH button", async () => {
      const { comp } = await renderEditor({
         datasource: makeDataSource({ tabularView: makeTabularView({ views: [] }) }),
      });
      const authSpy = vi.spyOn(comp, "authorize").mockImplementation(() => {});
      const refreshSpy = vi.spyOn(comp as any, "refreshView").mockImplementation(() => {});
      try {
         comp.buttonClicked(makeTabularButton({ type: "REFRESH" }));
         expect(authSpy).not.toHaveBeenCalled();
      } finally {
         authSpy.mockRestore();
         refreshSpy.mockRestore();
      }
   });

   it("passes the CANCEL button itself to refreshView when type is CANCEL", async () => {
      const { comp } = await renderEditor({
         datasource: makeDataSource({ tabularView: makeTabularView({ views: [] }) }),
      });
      const spy = vi.spyOn(comp as any, "refreshView").mockImplementation(() => {});
      try {
         const btn = makeTabularButton({ type: "CANCEL" });
         comp.buttonClicked(btn);
         expect(spy).toHaveBeenCalledWith(btn);
      } finally {
         spy.mockRestore();
      }
   });

   it("passes null to refreshView for non-CANCEL, non-OAUTH button", async () => {
      const { comp } = await renderEditor({
         datasource: makeDataSource({ tabularView: makeTabularView({ views: [] }) }),
      });
      const spy = vi.spyOn(comp as any, "refreshView").mockImplementation(() => {});
      try {
         comp.buttonClicked(makeTabularButton({ type: "REFRESH" }));
         expect(spy).toHaveBeenCalledWith(null);
      } finally {
         spy.mockRestore();
      }
   });
});

// ── Group 12: memory leak — ngOnDestroy unsubscription ────────────────────

describe("DatasourcesDatasourceEditor — memory leak", () => {

   it("stops emitting datasourceValid after fixture.destroy()", async () => {
      const { comp, fixture } = await renderEditor();
      const emitted: boolean[] = [];
      comp.datasourceValid.subscribe(v => emitted.push(v));
      const beforeDestroy = emitted.length;

      fixture.destroy();
      // datasourceValid$ is completed — next() is a no-op; datasourceValid EventEmitter won't fire
      comp.onValidChanged(false);

      expect(emitted.length).toBe(beforeDestroy);
   });
});
