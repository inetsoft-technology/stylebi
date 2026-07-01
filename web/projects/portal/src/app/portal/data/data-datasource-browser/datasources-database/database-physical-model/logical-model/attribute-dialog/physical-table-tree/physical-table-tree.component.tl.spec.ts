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
 * PhysicalTableTreeComponent - single-pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - selectNode parent/child propagation and nodesSelected emission
 *   Group 2 [Risk 2] - shift-range selection and selectAndExpandToNode
 *   Group 3 [Risk 2] - removeLockedNodes parent updates
 */

import { PhysicalTableTreeComponent } from "./physical-table-tree.component";

function makeLeaf(name: string, table = "T1", disabled = false) {
   return {
      label: name,
      leaf: true,
      disabled,
      data: { qualifiedName: name, table },
      children: [],
    };
}

function makeParent(label: string, children: any[] = []) {
   return {
      label,
      leaf: false,
      expanded: false,
      disabled: false,
      data: { path: `/${label}` },
      children,
   };
}

afterEach(() => {
   vi.restoreAllMocks();
});

describe("PhysicalTableTreeComponent - single pass", () => {
   describe("Group 1 - selectNode propagation", () => {
      it("should select a parent node, its children, and emit the selected nodes", () => {
         const comp = new PhysicalTableTreeComponent();
         const child1 = makeLeaf("c1", "T1");
         const child2 = makeLeaf("c2", "T1");
         const parent = makeParent("T1", [child1, child2]);
         comp.root = makeParent("root", [parent]) as never;
         const emitSpy = vi.spyOn(comp.nodesSelected, "emit");

         comp.selectNode(parent as never, { shiftKey: false } as MouseEvent);

         expect(parent.expanded).toBe(true);
         expect(comp.selectedNodes).toEqual([parent, child1, child2]);
         expect(emitSpy).toHaveBeenCalledWith(comp.selectedNodes);
      });

      it("should deselect a selected child and remove its parent selection", () => {
         const comp = new PhysicalTableTreeComponent();
         const child1 = makeLeaf("c1", "T1");
         const child2 = makeLeaf("c2", "T1");
         const parent = makeParent("T1", [child1, child2]);
         comp.root = makeParent("root", [parent]) as never;
         comp.selectedNodes = [parent as never, child1 as never, child2 as never];

         comp.selectNode(child1 as never, { shiftKey: false } as MouseEvent);

         expect(comp.selectedNodes).toEqual([child2]);
      });
   });

   describe("Group 2 - shift and expand selection", () => {
      it("should add the contiguous expanded range when shift-selecting another leaf", () => {
         const comp = new PhysicalTableTreeComponent();
         const child1 = makeLeaf("c1", "T1");
         const child2 = makeLeaf("c2", "T1");
         const child3 = makeLeaf("c3", "T1");
         const parent = makeParent("T1", [child1, child2, child3]);
         parent.expanded = true;
         const root = makeParent("root", [parent]);
         root.expanded = true;
         comp.root = root as never;
         comp.selectedNodes = [child1 as never];

         comp.selectNode(child3 as never, { shiftKey: true } as MouseEvent);

         expect(comp.selectedNodes).toEqual([child1, child2, child3]);
      });

      it("should expand to an existing node and select its parent chain", () => {
         const comp = new PhysicalTableTreeComponent();
         const child = makeLeaf("c1", "T1");
         const parent = makeParent("T1", [child]);
         comp.root = makeParent("root", [parent]) as never;

         comp.selectAndExpandToNode(child as never);

         expect(parent.expanded).toBe(true);
         expect(comp.selectedNodes).toContain(child);
      });
   });

   describe("Group 3 - locked node cleanup", () => {
      it("should remove locked leaves and drop the parent when not all children remain selected", () => {
         const comp = new PhysicalTableTreeComponent();
         const locked = makeLeaf("locked", "T1", true);
         const unlocked = makeLeaf("unlocked", "T1");
         const parent = makeParent("T1", [locked, unlocked]);
         comp.root = makeParent("root", [parent]) as never;
         comp.selectedNodes = [locked as never, unlocked as never, parent as never];

         comp.removeLockedNodes();

         expect(comp.selectedNodes).toEqual([unlocked]);
      });

      it("should re-add the parent when every child of it is still selected", () => {
         const comp = new PhysicalTableTreeComponent();
         const child1 = makeLeaf("c1", "T1");
         const child2 = makeLeaf("c2", "T1");
         const parent = makeParent("T1", [child1, child2]);
         comp.selectedNodes = [child1 as never, child2 as never];

         // updateParent is private and is only invoked internally from removeLockedNodes,
         // which always removes the triggering child first - making this branch unreachable
         // through the public API. Invoke it directly to cover the allChildrenSelected path.
         (comp as any)["updateParent"](parent as never);

         expect(comp.selectedNodes).toEqual([child1, child2, parent]);
      });
   });
});
