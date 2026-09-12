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
 * Shared test helpers for TreeNodeComponent and TreeComponent multi-pass TL specs.
 * Consumed by:
 *   tree-node.component.interaction.tl.spec.ts
 *   tree-node.component.risk.tl.spec.ts
 *   tree-node.component.display.tl.spec.ts
 *   tree.component.interaction.tl.spec.ts
 *   tree.component.risk.tl.spec.ts
 *   tree.component.display.tl.spec.ts
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { Subject } from "rxjs";
import { vi } from "vitest";

import { DragService } from "../services/drag.service";
import { TreeComponent } from "./tree.component";
import { TreeNodeComponent } from "./tree-node.component";
import { TreeNodeModel } from "./tree-node-model";

// ---------------------------------------------------------------------------
// Do NOT reassign properties of these mocks — use mockReturnValueOnce /
// mockImplementationOnce for per-test overrides.
// ---------------------------------------------------------------------------

export const DRAG_SERVICE_MOCK = {
   reset: vi.fn(),
   put: vi.fn(),
};

// ---------------------------------------------------------------------------
// Factory functions
// ---------------------------------------------------------------------------

export function makeNode(overrides: Partial<TreeNodeModel> = {}): TreeNodeModel {
   return {
      label: "Node",
      data: { path: "/Node" },
      leaf: true,
      children: [],
      ...overrides,
   };
}

export function makeFolder(
   children: TreeNodeModel[] = [],
   overrides: Partial<TreeNodeModel> = {}
): TreeNodeModel {
   return {
      label: "Folder",
      data: { path: "/Folder" },
      leaf: false,
      expanded: false,
      children,
      ...overrides,
   };
}

// ---------------------------------------------------------------------------
// TreeComponent mock interface used by TreeNodeComponent tests
// ---------------------------------------------------------------------------

export interface TreeMock {
   focusedSubject: Subject<TreeNodeModel>;
   focusedObservable: any;
   selectedNodes: TreeNodeModel[];
   highLightNodes: TreeNodeModel[];
   grayedOutFields: any[];
   grayedOutValues: any[];
   isGrayFunction: null;
   hasMenuFunction: null;
   initExpanded: boolean;
   isFocusedNode: ReturnType<typeof vi.fn>;
   isSelectedNode: (node: TreeNodeModel) => boolean;
   setHighLightNodes: ReturnType<typeof vi.fn>;
   expandNode: ReturnType<typeof vi.fn>;
   collapseNode: ReturnType<typeof vi.fn>;
   selectNode: ReturnType<typeof vi.fn>;
   clickNode: ReturnType<typeof vi.fn>;
   exclusiveSelectNode: ReturnType<typeof vi.fn>;
   doubleclickNode: ReturnType<typeof vi.fn>;
   onDrag: ReturnType<typeof vi.fn>;
   onDragOver: ReturnType<typeof vi.fn>;
   onDrop: ReturnType<typeof vi.fn>;
}

export function makeTreeMock(overrides: Partial<TreeMock> = {}): TreeMock {
   const focusedSubject = new Subject<TreeNodeModel>();
   const isSelectedNodeFn = vi.fn().mockReturnValue(false);

   return {
      focusedSubject,
      focusedObservable: focusedSubject.asObservable(),
      selectedNodes: [],
      highLightNodes: [],
      grayedOutFields: [],
      grayedOutValues: [],
      isGrayFunction: null,
      hasMenuFunction: null,
      initExpanded: false,
      isFocusedNode: vi.fn().mockReturnValue(false),
      isSelectedNode: isSelectedNodeFn,
      setHighLightNodes: vi.fn(),
      expandNode: vi.fn(),
      collapseNode: vi.fn(),
      selectNode: vi.fn(),
      clickNode: vi.fn(),
      exclusiveSelectNode: vi.fn(),
      doubleclickNode: vi.fn(),
      onDrag: vi.fn(),
      onDragOver: vi.fn(),
      onDrop: vi.fn(),
      ...overrides,
   };
}

// ---------------------------------------------------------------------------
// Generic DOM event factory — usable across all passes
// ---------------------------------------------------------------------------

export function makeMockEvent(overrides: Record<string, any> = {}) {
   return {
      button: 0,
      target: {},
      ctrlKey: false,
      metaKey: false,
      shiftKey: false,
      preventDefault: vi.fn(),
      stopPropagation: vi.fn(),
      dataTransfer: {
         getData: vi.fn().mockReturnValue(""),
         setData: vi.fn(),
      },
      ...overrides,
   } as any;
}

// ---------------------------------------------------------------------------
// renderTreeNode — renders TreeNodeComponent with a mock TreeComponent
// ---------------------------------------------------------------------------

interface RenderTreeNodeOpts {
   node?: TreeNodeModel;
   tree?: TreeMock;
   componentProperties?: Record<string, any>;
}

export async function renderTreeNode(opts: RenderTreeNodeOpts = {}) {
   const node = opts.node ?? makeNode();
   const tree = opts.tree ?? makeTreeMock();

   const result = await render(TreeNodeComponent, {
      componentProperties: {
         node,
         tree: tree as any,
         showRoot: true,
         ...opts.componentProperties,
      },
      providers: [
         { provide: DragService, useValue: DRAG_SERVICE_MOCK },
      ],
      schemas: [NO_ERRORS_SCHEMA],
   });

   return { comp: result.fixture.componentInstance, fixture: result.fixture, tree };
}

// ---------------------------------------------------------------------------
// renderTree — renders TreeComponent directly
// ---------------------------------------------------------------------------

interface RenderTreeOpts {
   root?: TreeNodeModel;
   componentProperties?: Record<string, any>;
}

export async function renderTree(opts: RenderTreeOpts = {}) {
   const root = opts.root ?? makeFolder([makeNode()]);

   const result = await render(TreeComponent, {
      componentProperties: {
         root,
         selectedNodes: [],
         ...opts.componentProperties,
      },
      schemas: [NO_ERRORS_SCHEMA],
   });

   return { comp: result.fixture.componentInstance, fixture: result.fixture };
}
