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
 * ComponentTree — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — selectNode: Ctrl+click vs plain click routes to different sheet APIs
 *   Group 2 [Risk 2] — copyAssembly / cutAssembly / removeAssembly: EventEmitter output contracts
 *   Group 3 [Risk 2] — bringAssemblyToFront / sendAssemblyToBack: EventEmitter output contracts
 *   Group 4 [Risk 2] — getCssClass: maps VSObject types to CSS class strings
 *   Group 5 [Risk 2] — getToggleIcon: expanded → caret-down, collapsed → caret-right
 *   Group 6 [Risk 1] — hasChildren: true / false branches
 *   Group 7 [Risk 1] — expand: toggles node.expanded
 *
 * Out of scope: isSelected() — depends on focusedAssemblies observable pipe(find()), not
 *   easily testable without a live Observable; test value is low (display-only).
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { ComponentTree } from "./component-tree.component";
import { VSObjectTreeNode } from "../../../data/vs-object-tree-node";
import { VSObjectModel } from "../../../../vsobjects/model/vs-object-model";

function makeModel(objectType: string): VSObjectModel {
   return { objectType } as VSObjectModel;
}

function makeNode(objectType: string, expanded = false, children: VSObjectTreeNode[] = []): VSObjectTreeNode {
   return { model: makeModel(objectType), expanded, children } as VSObjectTreeNode;
}

function makeSheet() {
   return {
      selectAssembly: vi.fn(),
      currentFocusedAssemblies: [] as VSObjectModel[],
      focusedAssemblies: { pipe: vi.fn(() => ({ subscribe: vi.fn() })) },
   } as any;
}

async function renderComponent() {
   const { fixture } = await render(ComponentTree, {
      schemas: [NO_ERRORS_SCHEMA],
   });
   return fixture.componentInstance as ComponentTree;
}

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: selectNode [Risk 3]
// ---------------------------------------------------------------------------

describe("ComponentTree — selectNode", () => {
   // 🔁 Regression-sensitive: Ctrl+click must call selectAssembly (multi-select); plain click
   //    must set currentFocusedAssemblies (single-select). Swapping these breaks multi-select UX.
   it("should call sheet.selectAssembly when Ctrl key is held", async () => {
      const comp = await renderComponent();
      const sheet = makeSheet();
      comp.sheet = sheet;
      const node = makeNode("VSChart");
      const event = { ctrlKey: true, stopPropagation: vi.fn() } as any;

      comp.selectNode(event, node);

      expect(sheet.selectAssembly).toHaveBeenCalledWith(node.model);
      expect(event.stopPropagation).toHaveBeenCalled();
   });

   it("should set sheet.currentFocusedAssemblies to [model] on plain click", async () => {
      const comp = await renderComponent();
      const sheet = makeSheet();
      comp.sheet = sheet;
      const node = makeNode("VSTable");
      const event = { ctrlKey: false, stopPropagation: vi.fn() } as any;

      comp.selectNode(event, node);

      expect(sheet.currentFocusedAssemblies).toEqual([node.model]);
      expect(sheet.selectAssembly).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 2: copy / cut / remove EventEmitter contracts [Risk 2]
// ---------------------------------------------------------------------------

describe("ComponentTree — copyAssembly / cutAssembly / removeAssembly", () => {
   it("should emit the model via onCopy when copyAssembly is called", async () => {
      const comp = await renderComponent();
      const emitted: VSObjectModel[] = [];
      comp.onCopy.subscribe(m => emitted.push(m));
      const model = makeModel("VSChart");

      comp.copyAssembly(model);

      expect(emitted).toHaveLength(1);
      expect(emitted[0]).toBe(model);
   });

   it("should emit the model via onCut when cutAssembly is called", async () => {
      const comp = await renderComponent();
      const emitted: VSObjectModel[] = [];
      comp.onCut.subscribe(m => emitted.push(m));
      const model = makeModel("VSTable");

      comp.cutAssembly(model);

      expect(emitted).toHaveLength(1);
      expect(emitted[0]).toBe(model);
   });

   it("should emit the model via onRemove when removeAssembly is called", async () => {
      const comp = await renderComponent();
      const emitted: VSObjectModel[] = [];
      comp.onRemove.subscribe(m => emitted.push(m));
      const model = makeModel("VSText");

      comp.removeAssembly(model);

      expect(emitted).toHaveLength(1);
      expect(emitted[0]).toBe(model);
   });
});

// ---------------------------------------------------------------------------
// Group 3: bringToFront / sendToBack EventEmitter contracts [Risk 2]
// ---------------------------------------------------------------------------

describe("ComponentTree — bringAssemblyToFront / sendAssemblyToBack", () => {
   it("should emit via onBringToFront when bringAssemblyToFront is called", async () => {
      const comp = await renderComponent();
      const emitted: VSObjectModel[] = [];
      comp.onBringToFront.subscribe(m => emitted.push(m));
      const model = makeModel("VSImage");

      comp.bringAssemblyToFront(model);

      expect(emitted).toHaveLength(1);
      expect(emitted[0]).toBe(model);
   });

   it("should emit via onSendToBack when sendAssemblyToBack is called", async () => {
      const comp = await renderComponent();
      const emitted: VSObjectModel[] = [];
      comp.onSendToBack.subscribe(m => emitted.push(m));
      const model = makeModel("VSGauge");

      comp.sendAssemblyToBack(model);

      expect(emitted).toHaveLength(1);
      expect(emitted[0]).toBe(model);
   });
});

// ---------------------------------------------------------------------------
// Group 4: getCssClass [Risk 2]
// ---------------------------------------------------------------------------

describe("ComponentTree — getCssClass", () => {
   it.each([
      ["VSChart", "chart"],
      ["VSCrosstab", "crosstab"],
      ["VSTable", "table"],
      ["VSCalcTable", "freehand-table"],
      ["VSSelectionList", "selection-list"],
      ["VSText", "text"],
      ["VSImage", "image"],
      ["VSGauge", "gauge"],
   ])("should return '%s' css class for %s type", async (objectType, expected) => {
      const comp = await renderComponent();
      const node = makeNode(objectType);
      expect(comp.getCssClass(node)).toBe(expected);
   });

   it("should return undefined for unknown object type", async () => {
      const comp = await renderComponent();
      const node = makeNode("UnknownType");
      expect(comp.getCssClass(node)).toBeUndefined();
   });
});

// ---------------------------------------------------------------------------
// Group 5: getToggleIcon [Risk 2]
// ---------------------------------------------------------------------------

describe("ComponentTree — getToggleIcon", () => {
   // 🔁 Regression-sensitive: icon must swap on expand/collapse; wrong icon misleads users.
   it("should return caret-down icon when node is expanded", async () => {
      const comp = await renderComponent();
      const node = makeNode("VSChart", true);
      expect(comp.getToggleIcon(node)).toBe("caret-down-icon icon-lg");
   });

   it("should return caret-right icon when node is collapsed", async () => {
      const comp = await renderComponent();
      const node = makeNode("VSChart", false);
      expect(comp.getToggleIcon(node)).toBe("caret-right-icon icon-lg");
   });
});

// ---------------------------------------------------------------------------
// Group 6: hasChildren [Risk 1]
// ---------------------------------------------------------------------------

describe("ComponentTree — hasChildren", () => {
   it("should return true when the node has children", async () => {
      const comp = await renderComponent();
      const child = makeNode("VSText");
      const parent = makeNode("VSTab", false, [child]);
      expect(comp.hasChildren(parent)).toBe(true);
   });

   it("should return false when the node has no children", async () => {
      const comp = await renderComponent();
      const node = makeNode("VSText", false, []);
      expect(comp.hasChildren(node)).toBe(false);
   });

   it("should return false when children is null/undefined", async () => {
      const comp = await renderComponent();
      const node = { model: makeModel("VSText"), expanded: false } as VSObjectTreeNode;
      // hasChildren returns `undefined` (not strict false) when node.children is absent
      expect(comp.hasChildren(node)).toBeFalsy();
   });
});

// ---------------------------------------------------------------------------
// Group 7: expand [Risk 1]
// ---------------------------------------------------------------------------

describe("ComponentTree — expand", () => {
   it("should toggle expanded from false to true", async () => {
      const comp = await renderComponent();
      const node = makeNode("VSChart", false);

      comp.expand(node);

      expect(node.expanded).toBe(true);
   });

   it("should toggle expanded from true to false", async () => {
      const comp = await renderComponent();
      const node = makeNode("VSChart", true);

      comp.expand(node);

      expect(node.expanded).toBe(false);
   });
});
