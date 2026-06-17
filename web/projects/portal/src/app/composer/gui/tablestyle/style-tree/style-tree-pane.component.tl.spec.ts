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
 * StyleTreePane — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — selectNode: when node.type is set and not CUSTOM_FOLDER →
 *                       sets tableStyle.selectedRegion and selectedRegionLabel;
 *                       when node.type == CUSTOM_FOLDER → no change
 *   Group 2 [Risk 2] — hasMenu(node): returns true for CUSTOM node (has edit+delete actions);
 *                       returns true for CUSTOM_FOLDER node (has new action, visible=true);
 *                       returns false for REGION node (no actions pushed)
 *   Group 3 [Risk 2] — openEdit: emits onOpenCustomEdit with {new: false, model: specList[specId]}
 *   Group 4 [Risk 2] — newCustom: sets tableStyle.isModified = true; emits onOpenCustomEdit with
 *                       {new: true, model: {...}}; id equals specList.length before call
 *   Group 5 [Risk 2] — deleteCustom: calls TableStyleUtil.deleteCustom, initRegionsTree,
 *                       addUndoList; emits onRemoveCustom
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { HttpClient } from "@angular/common/http";

import { StyleTreePane } from "./style-tree-pane.component";
import { FixedDropdownService } from "../../../../widget/fixed-dropdown/fixed-dropdown.service";
import { TableStyleUtil } from "../../../../common/util/table-style-util";
import { TableStyleModel } from "../../../data/tablestyle/table-style-model";
import { TreeNodeModel } from "../../../../widget/tree/tree-node-model";
import { SpecificationModel } from "../../../data/tablestyle/specification-model";

// ---------------------------------------------------------------------------
// Shared mocks
// ---------------------------------------------------------------------------

const HTTP_CLIENT_MOCK = {
   get: vi.fn(),
   put: vi.fn(),
};

const DROPDOWN_SERVICE_MOCK = {
   open: vi.fn(),
};

// ---------------------------------------------------------------------------
// Shared factories
// ---------------------------------------------------------------------------

async function renderComponent() {
   const { fixture } = await render(StyleTreePane, {
      schemas: [NO_ERRORS_SCHEMA],
      componentImports: [],
      providers: [
         { provide: HttpClient, useValue: HTTP_CLIENT_MOCK },
         { provide: FixedDropdownService, useValue: DROPDOWN_SERVICE_MOCK },
      ],
   });
   return fixture;
}

/** Makes a minimal TableStyleModel suitable for tests. */
function makeTableStyle(overrides: {
   selectedRegion?: string;
   selectedRegionLabel?: string;
   specList?: SpecificationModel[];
   isModified?: boolean;
} = {}): TableStyleModel {
   return {
      selectedRegion: overrides.selectedRegion ?? "",
      selectedRegionLabel: overrides.selectedRegionLabel ?? "",
      isModified: overrides.isModified ?? false,
      styleFormat: {
         specList: overrides.specList ?? [],
         origianlIndex: 0,
      },
      undoRedoList: [],
      currentIndex: -1,
      regionsTreeRoot: null,
      selectedTreeNode: null,
   } as any;
}

/** Makes a minimal TreeNodeModel. */
function makeNode(overrides: Partial<TreeNodeModel> & { data?: any } = {}): TreeNodeModel {
   return {
      label: "node",
      leaf: true,
      children: [],
      ...overrides,
   } as TreeNodeModel;
}

// ---------------------------------------------------------------------------
// Per-test reset
// ---------------------------------------------------------------------------

beforeEach(() => {
   HTTP_CLIENT_MOCK.get.mockReset();
   HTTP_CLIENT_MOCK.put.mockReset();
   DROPDOWN_SERVICE_MOCK.open.mockReset();
});

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: selectNode [Risk 3]
// ---------------------------------------------------------------------------

describe("StyleTreePane — selectNode", () => {

   // 🔁 Regression-sensitive: selectedRegion is the key used throughout the style editor to
   //    know which region is active; a wrong assignment breaks region-specific property display.
   it("should set selectedRegion and selectedRegionLabel when node.type is a non-CUSTOM_FOLDER type", async () => {
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      const style = makeTableStyle();
      comp.tableStyle = style;

      const node = makeNode({ type: TableStyleUtil.REGION, data: TableStyleUtil.HEADER_ROW, label: "Header Row" });
      comp.selectNode(node);

      expect(style.selectedRegion).toBe(TableStyleUtil.HEADER_ROW);
      expect(style.selectedRegionLabel).toBe("Header Row");
   });

   it("should set selectedRegion from node.data converted to string", async () => {
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      const style = makeTableStyle();
      comp.tableStyle = style;

      const node = makeNode({ type: TableStyleUtil.CUSTOM, data: 2, label: "Custom Pattern 2" });
      comp.selectNode(node);

      expect(style.selectedRegion).toBe("2");
   });

   it("should NOT change selectedRegion when node.type == CUSTOM_FOLDER", async () => {
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      const style = makeTableStyle({ selectedRegion: "Body", selectedRegionLabel: "Body" });
      comp.tableStyle = style;

      const node = makeNode({ type: TableStyleUtil.CUSTOM_FOLDER, data: 0, label: "Custom Patterns" });
      comp.selectNode(node);

      expect(style.selectedRegion).toBe("Body");
      expect(style.selectedRegionLabel).toBe("Body");
   });

   it("should NOT change selectedRegion when node.type is falsy (null/undefined)", async () => {
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      const style = makeTableStyle({ selectedRegion: "Body" });
      comp.tableStyle = style;

      const node = makeNode({ type: undefined, data: "something", label: "no-type" });
      comp.selectNode(node);

      expect(style.selectedRegion).toBe("Body");
   });
});

// ---------------------------------------------------------------------------
// Group 2: hasMenu [Risk 2]
// ---------------------------------------------------------------------------

describe("StyleTreePane — hasMenu", () => {

   it("should return true for a CUSTOM node (edit and delete actions are visible)", async () => {
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      comp.tableStyle = makeTableStyle({ specList: [{ id: 0, label: "Pat1" } as SpecificationModel] });

      const node = makeNode({ type: TableStyleUtil.CUSTOM, data: 0 });

      expect(comp.hasMenu(node)).toBe(true);
   });

   it("should return true for a CUSTOM_FOLDER node (new-pattern action is visible)", async () => {
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      comp.tableStyle = makeTableStyle();

      const node = makeNode({ type: TableStyleUtil.CUSTOM_FOLDER, data: 0 });

      expect(comp.hasMenu(node)).toBe(true);
   });

   it("should return false for a REGION node (no actions are pushed to the group)", async () => {
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      comp.tableStyle = makeTableStyle();

      const node = makeNode({ type: TableStyleUtil.REGION, data: TableStyleUtil.BODY });

      expect(comp.hasMenu(node)).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 3: openEdit [Risk 2]
// ---------------------------------------------------------------------------

describe("StyleTreePane — openEdit", () => {

   it("should emit onOpenCustomEdit with {new: false, model: specList[specId]}", async () => {
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      const spec0: SpecificationModel = { id: 0, label: "Pattern A" } as SpecificationModel;
      const spec1: SpecificationModel = { id: 1, label: "Pattern B" } as SpecificationModel;
      comp.tableStyle = makeTableStyle({ specList: [spec0, spec1] });

      const emitted: Array<{ new: boolean; model: SpecificationModel }> = [];
      comp.onOpenCustomEdit.subscribe(v => emitted.push(v));

      comp.openEdit(1);

      expect(emitted).toHaveLength(1);
      expect(emitted[0].new).toBe(false);
      expect(emitted[0].model).toBe(spec1);
   });

   it("should emit the correct spec for specId 0", async () => {
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      const spec0: SpecificationModel = { id: 0, label: "First" } as SpecificationModel;
      comp.tableStyle = makeTableStyle({ specList: [spec0] });

      const emitted: Array<{ new: boolean; model: SpecificationModel }> = [];
      comp.onOpenCustomEdit.subscribe(v => emitted.push(v));

      comp.openEdit(0);

      expect(emitted[0].model).toBe(spec0);
   });
});

// ---------------------------------------------------------------------------
// Group 4: newCustom [Risk 2]
// ---------------------------------------------------------------------------

describe("StyleTreePane — newCustom", () => {

   it("should set tableStyle.isModified to true", async () => {
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      const style = makeTableStyle({ specList: [] });
      comp.tableStyle = style;

      comp.newCustom();

      expect(style.isModified).toBe(true);
   });

   it("should emit onOpenCustomEdit with {new: true} and a model whose id equals specList.length", async () => {
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      const existingSpecs: SpecificationModel[] = [
         { id: 0, label: "P0" } as SpecificationModel,
         { id: 1, label: "P1" } as SpecificationModel,
      ];
      comp.tableStyle = makeTableStyle({ specList: existingSpecs });

      const emitted: Array<{ new: boolean; model: SpecificationModel }> = [];
      comp.onOpenCustomEdit.subscribe(v => emitted.push(v));

      comp.newCustom();

      expect(emitted).toHaveLength(1);
      expect(emitted[0].new).toBe(true);
      // id must equal the length of specList before the call (2)
      expect(emitted[0].model.id).toBe(2);
   });

   it("should emit a model with customType == TableStyleUtil.ROW", async () => {
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      comp.tableStyle = makeTableStyle({ specList: [] });

      const emitted: Array<{ new: boolean; model: SpecificationModel }> = [];
      comp.onOpenCustomEdit.subscribe(v => emitted.push(v));

      comp.newCustom();

      expect(emitted[0].model.customType).toBe(TableStyleUtil.ROW);
   });

   it("should use id=0 and emit when specList is null", async () => {
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      const style = makeTableStyle();
      style.styleFormat.specList = null as any;
      comp.tableStyle = style;

      const emitted: Array<{ new: boolean; model: SpecificationModel }> = [];
      comp.onOpenCustomEdit.subscribe(v => emitted.push(v));

      comp.newCustom();

      expect(emitted[0].model.id).toBe(0);
   });
});

// ---------------------------------------------------------------------------
// Group 5: deleteCustom [Risk 2]
// ---------------------------------------------------------------------------

describe("StyleTreePane — deleteCustom", () => {

   it("should call TableStyleUtil.deleteCustom with the tableStyle and specId", async () => {
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      const style = makeTableStyle({
         specList: [
            { id: 0, label: "P0" } as SpecificationModel,
            { id: 1, label: "P1" } as SpecificationModel,
         ],
      });
      comp.tableStyle = style;
      const deleteSpy = vi.spyOn(TableStyleUtil, "deleteCustom").mockImplementation(() => {});
      vi.spyOn(TableStyleUtil, "initRegionsTree").mockImplementation(() => {});
      vi.spyOn(TableStyleUtil, "addUndoList").mockImplementation(() => {});

      comp.deleteCustom(0);

      expect(deleteSpy).toHaveBeenCalledWith(style, 0);
   });

   it("should call TableStyleUtil.initRegionsTree after deleteCustom", async () => {
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      const style = makeTableStyle({ specList: [{ id: 0, label: "P0" } as SpecificationModel] });
      comp.tableStyle = style;
      vi.spyOn(TableStyleUtil, "deleteCustom").mockImplementation(() => {});
      const initSpy = vi.spyOn(TableStyleUtil, "initRegionsTree").mockImplementation(() => {});
      vi.spyOn(TableStyleUtil, "addUndoList").mockImplementation(() => {});

      comp.deleteCustom(0);

      expect(initSpy).toHaveBeenCalledWith(style);
   });

   it("should call TableStyleUtil.addUndoList after initRegionsTree", async () => {
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      const style = makeTableStyle({ specList: [{ id: 0, label: "P0" } as SpecificationModel] });
      comp.tableStyle = style;
      vi.spyOn(TableStyleUtil, "deleteCustom").mockImplementation(() => {});
      vi.spyOn(TableStyleUtil, "initRegionsTree").mockImplementation(() => {});
      const undoSpy = vi.spyOn(TableStyleUtil, "addUndoList").mockImplementation(() => {});

      comp.deleteCustom(0);

      expect(undoSpy).toHaveBeenCalledWith(style);
   });

   it("should emit onRemoveCustom after all util calls", async () => {
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      const style = makeTableStyle({ specList: [{ id: 0, label: "P0" } as SpecificationModel] });
      comp.tableStyle = style;
      vi.spyOn(TableStyleUtil, "deleteCustom").mockImplementation(() => {});
      vi.spyOn(TableStyleUtil, "initRegionsTree").mockImplementation(() => {});
      vi.spyOn(TableStyleUtil, "addUndoList").mockImplementation(() => {});

      let emitted = false;
      comp.onRemoveCustom.subscribe(() => { emitted = true; });

      comp.deleteCustom(0);

      expect(emitted).toBe(true);
   });
});
