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
 * DatasourcesDatasourceComponent — Pass 1: Interaction tests (pure logic, no HTTP).
 *
 * Covers: beforeunloadHandler, datasourceChanged, canDeactivate, originalName,
 * updateAdditionalList, selectAdditional, additionalSelected, checkDuplicate,
 * deleteAdditionals, updateDatasourceValid, close, newAdditional/editAdditional/renameAdditional
 * (dialog-open routing), memory leak (ngOnDestroy unsubscribes route).
 *
 * HTTP flows (refreshDataSource, ok → saveDataSource, deleteAdditional confirm+HTTP,
 * ngOnInit listing/type/path branches) → Pass 2 (risk).
 *
 * Mocking strategy: ActivatedRoute.paramMap is a Subject that is never emitted in this file,
 * so ngOnInit subscribes without triggering HTTP. Component state is set directly before
 * calling each method under test. No MSW is used here.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { MessageDialog } from "../../../../widget/dialog/message-dialog/message-dialog.component";
import {
   lastRenderedFixture,
   makeDataSource,
   MODAL_MOCK,
   ROUTER_MOCK,
   renderDatasource,
   resetMocks,
} from "./datasources-datasource.component.test-helpers";

beforeEach(() => {
   resetMocks();
   MessageDialog.lastMessage = null;
   (MessageDialog as any).lastMessageTS = 0;
});
afterEach(() => {
   vi.restoreAllMocks();
   lastRenderedFixture?.destroy();
});

// ── Group 1: beforeunloadHandler ──────────────────────────────────────────

describe("DatasourcesDatasource — beforeunloadHandler", () => {

   it("returns unsaved-changes message when datasource differs from originalDatasource", async () => {
      const { comp } = await renderDatasource();
      comp.datasource = makeDataSource({ name: "Modified" });
      comp.originalDatasource = makeDataSource({ name: "Original" });

      const result = comp.beforeunloadHandler({} as BeforeUnloadEvent);

      expect(result).toContain("unsave.changes.message");
   });

   it("returns null when datasource matches originalDatasource", async () => {
      const { comp } = await renderDatasource();
      const ds = makeDataSource({ name: "Same" });
      comp.datasource = ds;
      comp.originalDatasource = { ...ds };

      const result = comp.beforeunloadHandler({} as BeforeUnloadEvent);

      expect(result).toBeNull();
   });
});

// ── Group 2: datasourceChanged ────────────────────────────────────────────

describe("DatasourcesDatasource — datasourceChanged", () => {

   it("stores the new datasource reference", async () => {
      const { comp } = await renderDatasource();
      const newDs = makeDataSource({ name: "NewDS" });

      comp.datasourceChanged(newDs);

      expect(comp.datasource).toBe(newDs);
   });

   it("clones to originalDatasource so further mutations are tracked", async () => {
      const { comp } = await renderDatasource();
      const newDs = makeDataSource({ name: "NewDS" });

      comp.datasourceChanged(newDs);
      newDs.name = "MutatedAfter";

      expect(comp.originalDatasource.name).toBe("NewDS");
   });
});

// ── Group 3: canDeactivate ────────────────────────────────────────────────

describe("DatasourcesDatasource — canDeactivate", () => {

   it("returns true immediately when datasource matches originalDatasource", async () => {
      const { comp } = await renderDatasource();
      const ds = makeDataSource({ name: "Same" });
      comp.datasource = ds;
      comp.originalDatasource = { ...ds };

      const result = await comp.canDeactivate();

      expect(result).toBe(true);
   });

   it("opens a dialog and resolves true when user clicks Yes", async () => {
      const { comp } = await renderDatasource();
      comp.datasource = makeDataSource({ name: "Changed" });
      comp.originalDatasource = makeDataSource({ name: "Original" });

      MODAL_MOCK.open.mockImplementationOnce(() => ({
         result: Promise.resolve("Yes"),
         componentInstance: { onCommit: { subscribe: vi.fn() }, onCancel: { subscribe: vi.fn() } },
         close: vi.fn(),
         dismiss: vi.fn(),
      }));

      const result = await comp.canDeactivate();

      expect(result).toBe(true);
   });

   it("opens a dialog and resolves false when user clicks No", async () => {
      const { comp } = await renderDatasource();
      comp.datasource = makeDataSource({ name: "Changed" });
      comp.originalDatasource = makeDataSource({ name: "Original" });

      MODAL_MOCK.open.mockImplementationOnce(() => ({
         result: Promise.resolve("No"),
         componentInstance: { onCommit: { subscribe: vi.fn() }, onCancel: { subscribe: vi.fn() } },
         close: vi.fn(),
         dismiss: vi.fn(),
      }));

      const result = await comp.canDeactivate();

      expect(result).toBe(false);
   });
});

// ── Group 4: originalName ────────────────────────────────────────────────

describe("DatasourcesDatasource — originalName", () => {

   it("returns last path segment when datasourcePath contains slashes", async () => {
      const { comp } = await renderDatasource();
      comp.datasourcePath = "folder/subfolder/MyDS";

      expect(comp.originalName).toBe("MyDS");
   });

   it("returns the full path when it has no slashes", async () => {
      const { comp } = await renderDatasource();
      comp.datasourcePath = "MyDS";

      expect(comp.originalName).toBe("MyDS");
   });

   it("returns null when datasourcePath is falsy", async () => {
      const { comp } = await renderDatasource();
      comp.datasourcePath = "";

      expect(comp.originalName).toBeNull();
   });
});

// ── Group 5: updateAdditionalList ────────────────────────────────────────

describe("DatasourcesDatasource — updateAdditionalList", () => {

   it("sets additionalList from additionalConnections when tabularView is set", async () => {
      const { comp } = await renderDatasource();
      comp.datasource = makeDataSource({
         tabularView: { views: [] } as any,
         additionalConnections: [
            makeDataSource({ name: "Extra1", description: "Tip1" }),
            makeDataSource({ name: "Extra2", description: "Tip2" }),
         ],
      });

      comp.updateAdditionalList();

      expect(comp.additionalList.map(a => a.name)).toEqual(["Extra1", "Extra2"]);
   });

   it("maps description to tooltip", async () => {
      const { comp } = await renderDatasource();
      comp.datasource = makeDataSource({
         tabularView: { views: [] } as any,
         additionalConnections: [makeDataSource({ name: "E1", description: "MyTip" })],
      });

      comp.updateAdditionalList();

      expect(comp.additionalList[0].tooltip).toBe("MyTip");
   });

   it("sets additionalList to [] when additionalConnections is null", async () => {
      const { comp } = await renderDatasource();
      comp.datasource = makeDataSource({
         tabularView: { views: [] } as any,
         additionalConnections: null,
      });

      comp.updateAdditionalList();

      expect(comp.additionalList).toEqual([]);
   });

   it("does not populate additionalList when tabularView is null", async () => {
      const { comp } = await renderDatasource();
      comp.datasource = makeDataSource({ tabularView: null });
      comp.additionalList = [{ name: "Existing", tooltip: "" }];

      comp.updateAdditionalList();

      // List not modified since tabularView is falsy
      expect(comp.additionalList).toHaveLength(1);
   });
});

// ── Group 6: selectAdditional ────────────────────────────────────────────

describe("DatasourcesDatasource — selectAdditional", () => {

   it("replaces selection with single index on plain click", async () => {
      const { comp } = await renderDatasource();
      comp.selectedAdditionalIndex = [2];

      comp.selectAdditional({ ctrlKey: false } as MouseEvent, 1);

      expect(comp.selectedAdditionalIndex).toEqual([1]);
   });

   it("appends index to existing selection on ctrl+click", async () => {
      const { comp } = await renderDatasource();
      comp.selectedAdditionalIndex = [0];

      comp.selectAdditional({ ctrlKey: true } as MouseEvent, 2);

      expect(comp.selectedAdditionalIndex).toEqual([0, 2]);
   });
});

// ── Group 7: additionalSelected ──────────────────────────────────────────

describe("DatasourcesDatasource — additionalSelected", () => {

   it("returns true when index is in selectedAdditionalIndex", async () => {
      const { comp } = await renderDatasource();
      comp.selectedAdditionalIndex = [1, 3];
      expect(comp.additionalSelected(3)).toBe(true);
   });

   it("returns false when index is not selected", async () => {
      const { comp } = await renderDatasource();
      comp.selectedAdditionalIndex = [1];
      expect(comp.additionalSelected(2)).toBe(false);
   });
});

// ── Group 8: checkDuplicate ───────────────────────────────────────────────

describe("DatasourcesDatasource — checkDuplicate", () => {

   it("returns duplicate=true when value matches the main datasource name", async () => {
      const { comp } = await renderDatasource();
      comp.datasource = makeDataSource({ name: "MainDS", additionalConnections: [] });

      const result = comp.checkDuplicate("MainDS");

      expect(result.duplicate).toBe(true);
   });

   it("returns duplicate=true when value matches another additional connection name", async () => {
      const { comp } = await renderDatasource();
      const existing = makeDataSource({ name: "Extra1" });
      const selected = makeDataSource({ name: "Selected" });
      comp.datasource = makeDataSource({ name: "MainDS", additionalConnections: [existing, selected] });
      comp.selectedAdditionalIndex = [1]; // selected is at index 1

      const result = comp.checkDuplicate("Extra1");

      expect(result.duplicate).toBe(true);
   });

   it("returns duplicate=false when value is unique", async () => {
      const { comp } = await renderDatasource();
      // additionalConnections: null to avoid component bug where getSelectedAdditional()[0]
      // throws when selectedAdditionalIndex=[] and additionalConnections=[].
      comp.datasource = makeDataSource({ name: "MainDS", additionalConnections: null });

      const result = comp.checkDuplicate("UniqueNew");

      expect(result.duplicate).toBe(false);
   });
});

// ── Group 9: deleteAdditionals ────────────────────────────────────────────

describe("DatasourcesDatasource — deleteAdditionals", () => {

   it("removes the selected additional connections and resets selection", async () => {
      const { comp } = await renderDatasource();
      const e1 = makeDataSource({ name: "Extra1" });
      const e2 = makeDataSource({ name: "Extra2" });
      comp.datasource = makeDataSource({
         name: "MainDS",
         tabularView: { views: [] } as any,
         additionalConnections: [e1, e2],
      });
      comp.selectedAdditionalIndex = [0];

      comp.deleteAdditionals();

      expect(comp.datasource.additionalConnections.map(d => d.name)).toEqual(["Extra2"]);
      expect(comp.selectedAdditionalIndex).toEqual([]);
   });
});

// ── Group 10: updateDatasourceValid ──────────────────────────────────────

describe("DatasourcesDatasource — updateDatasourceValid", () => {

   it("sets datasourceValid to the provided value", async () => {
      const { comp } = await renderDatasource();
      comp.datasourceValid = false;

      comp.updateDatasourceValid(true);

      expect(comp.datasourceValid).toBe(true);
   });
});

// ── Group 11: close ────────────────────────────────────────────────────────

describe("DatasourcesDatasource — close", () => {

   it("navigates to /portal/tab/data/datasources", async () => {
      const { comp } = await renderDatasource();
      comp.datasourcePath = "";
      comp.parentPath = "";

      comp.close();

      expect(ROUTER_MOCK.navigate).toHaveBeenCalledWith(
         ["/portal/tab/data/datasources"],
         expect.objectContaining({ queryParams: expect.objectContaining({ scope: 0 }) }),
      );
   });

   it("uses parentPath as path when set", async () => {
      const { comp } = await renderDatasource();
      comp.parentPath = "/folder1";
      comp.datasourcePath = "";

      comp.close();

      expect(ROUTER_MOCK.navigate).toHaveBeenCalledWith(
         ["/portal/tab/data/datasources"],
         expect.objectContaining({ queryParams: expect.objectContaining({ path: "/folder1" }) }),
      );
   });

   it("derives parentPath from datasourcePath when parentPath is empty", async () => {
      const { comp } = await renderDatasource();
      comp.parentPath = "";
      comp.datasourcePath = "folder/subfolder/MyDS";

      comp.close();

      expect(ROUTER_MOCK.navigate).toHaveBeenCalledWith(
         ["/portal/tab/data/datasources"],
         expect.objectContaining({ queryParams: expect.objectContaining({ path: "folder/subfolder" }) }),
      );
   });

   it("uses '/' as path when neither parentPath nor slash in datasourcePath", async () => {
      const { comp } = await renderDatasource();
      comp.parentPath = "";
      comp.datasourcePath = "MyDS";

      comp.close();

      expect(ROUTER_MOCK.navigate).toHaveBeenCalledWith(
         ["/portal/tab/data/datasources"],
         expect.objectContaining({ queryParams: expect.objectContaining({ path: "/" }) }),
      );
   });
});

// ── Group 12: newAdditional opens dialog ──────────────────────────────────

describe("DatasourcesDatasource — newAdditional", () => {

   it("opens DatasourcesDatasourceDialogComponent via NgbModal", async () => {
      const { comp } = await renderDatasource();
      comp.datasource = makeDataSource({ name: "MainDS", tabularView: { views: [] } as any });
      comp.originalDatasource = makeDataSource({ name: "MainDS" });
      comp.additionalList = [];

      comp.newAdditional();

      expect(MODAL_MOCK.open).toHaveBeenCalled();
   });
});

// ── Group 13: editAdditional opens dialog ─────────────────────────────────

describe("DatasourcesDatasource — editAdditional", () => {

   it("opens dialog when exactly one additional is selected", async () => {
      const { comp } = await renderDatasource();
      const existing = makeDataSource({ name: "Extra1" });
      comp.datasource = makeDataSource({ name: "MainDS", additionalConnections: [existing] });
      comp.additionalList = [{ name: "Extra1", tooltip: "" }];
      comp.selectedAdditionalIndex = [0];

      comp.editAdditional();

      expect(MODAL_MOCK.open).toHaveBeenCalled();
   });

   it("does not open dialog when no additional is selected", async () => {
      const { comp } = await renderDatasource();
      comp.datasource = makeDataSource({ name: "MainDS", additionalConnections: [] });
      comp.selectedAdditionalIndex = [];

      comp.editAdditional();

      expect(MODAL_MOCK.open).not.toHaveBeenCalled();
   });
});

// ── Group 14: renameAdditional opens dialog ───────────────────────────────

describe("DatasourcesDatasource — renameAdditional", () => {

   it("opens InputNameDescDialog when exactly one additional is selected", async () => {
      const { comp } = await renderDatasource();
      const existing = makeDataSource({ name: "Extra1", description: "Desc1" });
      comp.datasource = makeDataSource({ name: "MainDS", additionalConnections: [existing] });
      comp.selectedAdditionalIndex = [0];

      comp.renameAdditional();

      expect(MODAL_MOCK.open).toHaveBeenCalled();
   });

   it("does not open dialog when no additional is selected", async () => {
      const { comp } = await renderDatasource();
      comp.datasource = makeDataSource({ name: "MainDS", additionalConnections: [] });
      comp.selectedAdditionalIndex = [];

      comp.renameAdditional();

      expect(MODAL_MOCK.open).not.toHaveBeenCalled();
   });
});

// ── Group 15: memory leak — ngOnDestroy unsubscribes route ────────────────

describe("DatasourcesDatasource — memory leak", () => {

   it("unsubscribes routeParamSubscription on fixture.destroy()", async () => {
      const { comp, fixture } = await renderDatasource();

      // Access private subscription via cast
      const sub = (comp as any).routeParamSubscription;
      expect(sub).not.toBeNull();

      fixture.destroy();

      expect((comp as any).routeParamSubscription).toBeNull();
   });
});
