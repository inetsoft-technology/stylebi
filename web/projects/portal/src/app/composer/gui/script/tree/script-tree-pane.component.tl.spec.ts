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
 * ScriptTreePane — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — functionTree setter: sets _functionTree.expanded = false;
 *                       ngOnChanges("functionTree") sets activeRoot = functionTree
 *   Group 2 [Risk 2] — getCSSIcon: covers all 6 CSS class branches
 *                       (summary-icon, db-model-icon, data-table-icon, column-icon,
 *                        folder-open-icon, folder-icon, empty string)
 *   Group 3 [Risk 2] — itemClicked: calls scriptService.setClickedNode(node, target)
 *   Group 4 [Risk 2] — searchStart(true): sets activeRoot = Tool.clone(functionTree)
 *                       (different reference)
 *   Group 5 [Risk 2] — searchStart(false): restores activeRoot = functionTree (same reference)
 *   Group 6 [Risk 1] — ngOnChanges with unrelated key: does NOT update activeRoot
 */

import { NO_ERRORS_SCHEMA, SimpleChanges } from "@angular/core";
import { render } from "@testing-library/angular";

import { ScriptTreePane } from "./script-tree-pane.component";
import { ScriptService } from "../script.service";
import { Tool } from "../../../../../../../shared/util/tool";
import { TreeNodeModel } from "../../../../widget/tree/tree-node-model";

// ---------------------------------------------------------------------------
// Shared mocks
// ---------------------------------------------------------------------------

const SCRIPT_SERVICE_MOCK = {
   setClickedNode: vi.fn(),
};

// ---------------------------------------------------------------------------
// Shared factories
// ---------------------------------------------------------------------------

async function renderComponent() {
   const { fixture } = await render(ScriptTreePane, {
      schemas: [NO_ERRORS_SCHEMA],
      componentImports: [],
      providers: [
         { provide: ScriptService, useValue: SCRIPT_SERVICE_MOCK },
      ],
   });
   return fixture;
}

/** Makes a minimal TreeNodeModel with optional overrides. */
function makeNode(overrides: Partial<TreeNodeModel> & { data?: any } = {}): TreeNodeModel {
   return {
      label: "node",
      leaf: true,
      children: [],
      ...overrides,
   } as TreeNodeModel;
}

/** Triggers the functionTree input-change lifecycle. */
function triggerFunctionTreeChange(comp: ScriptTreePane, value: TreeNodeModel): void {
   comp.functionTree = value;
   const changes: SimpleChanges = {
      functionTree: {
         currentValue: value,
         previousValue: null,
         firstChange: true,
         isFirstChange: () => true,
      },
   };
   comp.ngOnChanges(changes);
}

// ---------------------------------------------------------------------------
// Per-test reset
// ---------------------------------------------------------------------------

beforeEach(() => {
   SCRIPT_SERVICE_MOCK.setClickedNode.mockReset();
});

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: functionTree setter and ngOnChanges [Risk 3]
// ---------------------------------------------------------------------------

describe("ScriptTreePane — functionTree setter and ngOnChanges", () => {

   // 🔁 Regression-sensitive: the setter must collapse the tree by forcing expanded=false;
   //    if omitted, the function-tree panel opens fully expanded on every script pane load.
   it("should set expanded=false on the node when functionTree setter is called", async () => {
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      const node = makeNode({ expanded: true });

      comp.functionTree = node;

      expect(node.expanded).toBe(false);
   });

   // 🔁 Regression-sensitive: ngOnChanges must sync activeRoot to the new functionTree;
   //    if missed, the tree pane renders the stale root after the function list reloads.
   it("should set activeRoot to functionTree when ngOnChanges includes functionTree key", async () => {
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      const node = makeNode();

      triggerFunctionTreeChange(comp, node);

      expect(comp.activeRoot).toBe(comp.functionTree);
   });

   it("should store the exact node reference returned by the getter", async () => {
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      const node = makeNode({ label: "Functions" });

      comp.functionTree = node;

      expect(comp.functionTree).toBe(node);
   });
});

// ---------------------------------------------------------------------------
// Group 2: getCSSIcon — all branches [Risk 2]
// ---------------------------------------------------------------------------

describe("ScriptTreePane — getCSSIcon", () => {

   it("should return 'summary-icon' for node.data.data == 'New Aggregate'", async () => {
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      const node = makeNode({ data: { data: "New Aggregate" } });

      expect(comp.getCSSIcon(node)).toBe("summary-icon");
   });

   it("should return 'summary-icon' for node.data.useragg == 'true'", async () => {
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      const node = makeNode({ data: { useragg: "true" } });

      expect(comp.getCSSIcon(node)).toBe("summary-icon");
   });

   it("should return 'summary-icon' for node.data.useragg == 'false'", async () => {
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      const node = makeNode({ data: { useragg: "false" } });

      expect(comp.getCSSIcon(node)).toBe("summary-icon");
   });

   it("should return 'db-model-icon' for node.data.name == 'LOGIC_MODEL'", async () => {
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      const node = makeNode({ data: { name: "LOGIC_MODEL" } });

      expect(comp.getCSSIcon(node)).toBe("db-model-icon");
   });

   it("should return 'data-table-icon' for node.data.isTable == 'true'", async () => {
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      const node = makeNode({ data: { isTable: "true" } });

      expect(comp.getCSSIcon(node)).toBe("data-table-icon");
   });

   it("should return 'data-table-icon' for node.type == 'entity'", async () => {
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      const node = makeNode({ type: "entity", data: {} });

      expect(comp.getCSSIcon(node)).toBe("data-table-icon");
   });

   it("should return 'data-table-icon' for node.data.name == 'TABLE'", async () => {
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      const node = makeNode({ data: { name: "TABLE" } });

      expect(comp.getCSSIcon(node)).toBe("data-table-icon");
   });

   it("should return 'data-table-icon' for node.data.name == 'PHYSICAL_TABLE'", async () => {
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      const node = makeNode({ data: { name: "PHYSICAL_TABLE" } });

      expect(comp.getCSSIcon(node)).toBe("data-table-icon");
   });

   it("should return 'column-icon' for node.data.isField == 'true'", async () => {
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      const node = makeNode({ data: { isField: "true" } });

      expect(comp.getCSSIcon(node)).toBe("column-icon");
   });

   it("should return 'column-icon' for node.type === 'field'", async () => {
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      const node = makeNode({ type: "field", data: {} });

      expect(comp.getCSSIcon(node)).toBe("column-icon");
   });

   it("should return 'column-icon' for node.data.name == 'COLUMN'", async () => {
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      const node = makeNode({ data: { name: "COLUMN" } });

      expect(comp.getCSSIcon(node)).toBe("column-icon");
   });

   it("should return 'column-icon' for node.data.name == 'DATE_COLUMN'", async () => {
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      const node = makeNode({ data: { name: "DATE_COLUMN" } });

      expect(comp.getCSSIcon(node)).toBe("column-icon");
   });

   it("should return 'folder-open-icon' for a node with children that is expanded", async () => {
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      const node = makeNode({ children: [makeNode()], expanded: true, data: null });

      expect(comp.getCSSIcon(node)).toBe("folder-open-icon");
   });

   it("should return 'folder-icon' for a node with children that is collapsed", async () => {
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      const node = makeNode({ children: [makeNode()], expanded: false, data: null });

      expect(comp.getCSSIcon(node)).toBe("folder-icon");
   });

   it("should return empty string for a plain leaf node with no special data", async () => {
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      const node = makeNode({ data: null, children: [] });

      expect(comp.getCSSIcon(node)).toBe("");
   });
});

// ---------------------------------------------------------------------------
// Group 3: itemClicked [Risk 2]
// ---------------------------------------------------------------------------

describe("ScriptTreePane — itemClicked", () => {

   it("should call scriptService.setClickedNode with the node and target", async () => {
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      const node = makeNode({ label: "SomeFunction" });

      comp.itemClicked(node, "editor");

      expect(SCRIPT_SERVICE_MOCK.setClickedNode).toHaveBeenCalledOnce();
      expect(SCRIPT_SERVICE_MOCK.setClickedNode).toHaveBeenCalledWith(node, "editor");
   });

   it("should forward different target strings unchanged", async () => {
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      const node = makeNode();

      comp.itemClicked(node, "condition");

      expect(SCRIPT_SERVICE_MOCK.setClickedNode).toHaveBeenCalledWith(node, "condition");
   });
});

// ---------------------------------------------------------------------------
// Group 4: searchStart(true) — sets activeRoot to clone [Risk 2]
// ---------------------------------------------------------------------------

describe("ScriptTreePane — searchStart(true)", () => {

   it("should set activeRoot to a clone of functionTree (different reference)", async () => {
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      const node = makeNode({ label: "root" });
      triggerFunctionTreeChange(comp, node);

      const cloned = makeNode({ label: "cloned" });
      vi.spyOn(Tool, "clone").mockReturnValue(cloned);

      comp.searchStart(true);

      expect(comp.activeRoot).toBe(cloned);
      expect(comp.activeRoot).not.toBe(comp.functionTree);
   });

   it("should call Tool.clone with the current functionTree when search starts", async () => {
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      const node = makeNode();
      triggerFunctionTreeChange(comp, node);
      const cloneSpy = vi.spyOn(Tool, "clone").mockReturnValue(makeNode());

      comp.searchStart(true);

      expect(cloneSpy).toHaveBeenCalledWith(comp.functionTree);
   });
});

// ---------------------------------------------------------------------------
// Group 5: searchStart(false) — restores original functionTree reference [Risk 2]
// ---------------------------------------------------------------------------

describe("ScriptTreePane — searchStart(false)", () => {

   it("should restore activeRoot to the original functionTree reference", async () => {
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      const node = makeNode({ label: "root" });
      triggerFunctionTreeChange(comp, node);

      // Simulate a search having replaced activeRoot with a clone
      comp.activeRoot = makeNode({ label: "cloneUsedDuringSearch" });

      comp.searchStart(false);

      expect(comp.activeRoot).toBe(comp.functionTree);
   });

   it("should NOT call Tool.clone when search ends", async () => {
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      triggerFunctionTreeChange(comp, makeNode());
      const cloneSpy = vi.spyOn(Tool, "clone");

      comp.searchStart(false);

      expect(cloneSpy).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 6: ngOnChanges — unrelated key is no-op [Risk 1]
// ---------------------------------------------------------------------------

describe("ScriptTreePane — ngOnChanges: unrelated input", () => {

   it("should NOT update activeRoot when an unrelated input changes", async () => {
      const fixture = await renderComponent();
      const comp = fixture.componentInstance;
      const original = makeNode({ label: "originalRoot" });
      triggerFunctionTreeChange(comp, original);

      const priorActiveRoot = comp.activeRoot;

      comp.ngOnChanges({
         inactive: {
            currentValue: true,
            previousValue: false,
            firstChange: false,
            isFirstChange: () => false,
         },
      });

      expect(comp.activeRoot).toBe(priorActiveRoot);
   });
});
