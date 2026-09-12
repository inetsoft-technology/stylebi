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
 * SelectDataSourceDialog — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — saveChanges(): path contains "^" → shows error dialog and does NOT emit;
 *     path without "^" → emits onCommit(model); dataSource==null → emits (null path never
 *     contains "^")
 *   Group 2 [Risk 3] — selectNode(): folder-type node (DATA_SOURCE, PHYSICAL_FOLDER,
 *     DATA_SOURCE_FOLDER, QUERY_FOLDER, FOLDER) → sets dataSource=null; leaf (non-folder) →
 *     sets dataSource=node.data
 *   Group 3 [Risk 2] — doubleclickNode(): non-leaf node → no-op; null node → no-op; leaf node
 *     → calls selectNode + saveChanges
 *   Group 4 [Risk 2] — clearDisabled(): null model → true; dataSource==null → true; dataSource
 *     set → false
 *   Group 5 [Risk 1] — clear(): sets model.dataSource=null
 *   Group 6 [Risk 1] — cancelChanges(): emits onCancel("cancel")
 *
 * Confirmed bugs (it.fails):
 *   None.
 *
 * Out of scope:
 *   AssetTreeComponent tree rendering — library-level; selectNode/doubleclickNode are tested
 *     by calling them directly.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { SelectDataSourceDialog } from "./select-data-source-dialog.component";
import { SelectDataSourceDialogModel } from "../../data/vs/select-data-source-dialog-model";
import { AssetType } from "../../../../../../shared/data/asset-type";
import { ComponentTool } from "../../../common/util/component-tool";

// ---------------------------------------------------------------------------
// Shared mocks
// ---------------------------------------------------------------------------

const MODAL_SERVICE_MOCK = { open: vi.fn() };

// ---------------------------------------------------------------------------
// Shared fixture helpers
// ---------------------------------------------------------------------------

function makeModel(dataSource: any = null): SelectDataSourceDialogModel {
   return { title: "Select Data Source", dataSource };
}

function makeNode(type: string, leaf: boolean, path: string = "/ds/source"): any {
   return {
      leaf,
      data: { type, path, identifier: "node-id" },
   };
}

async function renderComponent(dataSource: any = null) {
   const model = makeModel(dataSource);
   const { fixture } = await render(SelectDataSourceDialog, {
      schemas: [NO_ERRORS_SCHEMA],
      componentImports: [],
      providers: [
         { provide: NgbModal, useValue: MODAL_SERVICE_MOCK },
      ],
      componentInputs: { model },
   });
   const comp = fixture.componentInstance as SelectDataSourceDialog;
   return { comp, fixture, model };
}

beforeEach(() => {
   MODAL_SERVICE_MOCK.open.mockReset();
});

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: saveChanges [Risk 3]
// ---------------------------------------------------------------------------

describe("SelectDataSourceDialog — saveChanges", () => {
   // 🔁 Regression-sensitive: a path containing "^" is invalid and must show an error dialog
   //    rather than silently emitting a corrupt model.
   it("should show error dialog and NOT emit when dataSource.path contains '^'", async () => {
      const { comp } = await renderComponent({ path: "ds/path^invalid", type: "WORKSHEET" });
      const msgSpy = vi.spyOn(ComponentTool, "showMessageDialog" as any).mockResolvedValue(undefined);
      const emitSpy = vi.spyOn(comp.onCommit, "emit");

      comp.saveChanges();

      expect(msgSpy).toHaveBeenCalled();
      expect(emitSpy).not.toHaveBeenCalled();
   });

   it("should emit onCommit(model) when dataSource.path has no '^'", async () => {
      const ds = { path: "/datasource/mysql", type: "DATA_SOURCE" };
      const { comp, model } = await renderComponent(ds);
      const emitSpy = vi.spyOn(comp.onCommit, "emit");

      comp.saveChanges();

      expect(emitSpy).toHaveBeenCalledWith(model);
   });

   it("should emit onCommit(model) when dataSource is null", async () => {
      const { comp, model } = await renderComponent(null);
      const emitSpy = vi.spyOn(comp.onCommit, "emit");

      comp.saveChanges();

      expect(emitSpy).toHaveBeenCalledWith(model);
   });
});

// ---------------------------------------------------------------------------
// Group 2: selectNode [Risk 3]
// ---------------------------------------------------------------------------

describe("SelectDataSourceDialog — selectNode", () => {
   // 🔁 Regression-sensitive: folder-type nodes must set dataSource=null so the user cannot
   //    accidentally select a folder as a data source.
   it("should set dataSource=null for DATA_SOURCE type", async () => {
      const { comp, model } = await renderComponent({ path: "/prev", type: "OTHER" });
      comp.selectNode(makeNode(AssetType.DATA_SOURCE, false));
      expect(model.dataSource).toBeNull();
   });

   it("should set dataSource=null for PHYSICAL_FOLDER type", async () => {
      const { comp, model } = await renderComponent({ path: "/prev", type: "OTHER" });
      comp.selectNode(makeNode(AssetType.PHYSICAL_FOLDER, false));
      expect(model.dataSource).toBeNull();
   });

   it("should set dataSource=null for DATA_SOURCE_FOLDER type", async () => {
      const { comp, model } = await renderComponent();
      comp.selectNode(makeNode(AssetType.DATA_SOURCE_FOLDER, false));
      expect(model.dataSource).toBeNull();
   });

   it("should set dataSource=null for QUERY_FOLDER type", async () => {
      const { comp, model } = await renderComponent();
      comp.selectNode(makeNode(AssetType.QUERY_FOLDER, false));
      expect(model.dataSource).toBeNull();
   });

   it("should set dataSource=null for FOLDER type", async () => {
      const { comp, model } = await renderComponent();
      comp.selectNode(makeNode(AssetType.FOLDER, false));
      expect(model.dataSource).toBeNull();
   });

   it("should set dataSource=node.data for non-folder leaf node", async () => {
      const { comp, model } = await renderComponent();
      const nodeData = { type: "WORKSHEET", path: "/worksheets/ws1" };
      comp.selectNode({ leaf: true, data: nodeData });
      expect(model.dataSource).toBe(nodeData);
   });

   it("should set dataSource=node.data for non-folder non-leaf node (e.g. WORKSHEET)", async () => {
      const { comp, model } = await renderComponent();
      const nodeData = { type: "WORKSHEET", path: "/worksheets/ws1" };
      comp.selectNode({ leaf: false, data: nodeData });
      expect(model.dataSource).toBe(nodeData);
   });
});

// ---------------------------------------------------------------------------
// Group 3: doubleclickNode [Risk 2]
// ---------------------------------------------------------------------------

describe("SelectDataSourceDialog — doubleclickNode", () => {
   it("should be a no-op when node is null", async () => {
      const { comp } = await renderComponent();
      const emitSpy = vi.spyOn(comp.onCommit, "emit");
      comp.doubleclickNode(null);
      expect(emitSpy).not.toHaveBeenCalled();
   });

   it("should be a no-op when node.leaf is false", async () => {
      const { comp } = await renderComponent();
      const emitSpy = vi.spyOn(comp.onCommit, "emit");
      comp.doubleclickNode({ leaf: false, data: { type: "WORKSHEET", path: "/ws" } });
      expect(emitSpy).not.toHaveBeenCalled();
   });

   it("should call selectNode and saveChanges when node.leaf is true", async () => {
      const { comp, model } = await renderComponent();
      const nodeData = { type: "WORKSHEET", path: "/ws/mysheet" };
      const emitSpy = vi.spyOn(comp.onCommit, "emit");

      comp.doubleclickNode({ leaf: true, data: nodeData });

      expect(model.dataSource).toBe(nodeData);
      expect(emitSpy).toHaveBeenCalledWith(model);
   });
});

// ---------------------------------------------------------------------------
// Group 4: clearDisabled [Risk 2]
// ---------------------------------------------------------------------------

describe("SelectDataSourceDialog — clearDisabled", () => {
   it("should return true when model is null/undefined", async () => {
      const { comp } = await renderComponent();
      comp["model"] = null;
      expect(comp.clearDisabled()).toBe(true);
   });

   it("should return true when dataSource is null", async () => {
      const { comp } = await renderComponent(null);
      expect(comp.clearDisabled()).toBe(true);
   });

   it("should return false when dataSource is set", async () => {
      const { comp } = await renderComponent({ path: "/ds", type: "DATA_SOURCE" });
      expect(comp.clearDisabled()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 5: clear [Risk 1]
// ---------------------------------------------------------------------------

describe("SelectDataSourceDialog — clear", () => {
   it("should set model.dataSource to null", async () => {
      const { comp, model } = await renderComponent({ path: "/ds", type: "DATA_SOURCE" });
      comp.clear();
      expect(model.dataSource).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 6: cancelChanges [Risk 1]
// ---------------------------------------------------------------------------

describe("SelectDataSourceDialog — cancelChanges", () => {
   it("should emit onCancel with 'cancel'", async () => {
      const { comp } = await renderComponent();
      const emitSpy = vi.spyOn(comp.onCancel, "emit");
      comp.cancelChanges();
      expect(emitSpy).toHaveBeenCalledWith("cancel");
   });
});
