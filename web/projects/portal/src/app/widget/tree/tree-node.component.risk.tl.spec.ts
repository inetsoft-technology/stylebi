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
 * TreeNodeComponent — Pass 2: Risk / Async
 *
 * Coverage plan:
 *   Group 1 [Risk 2] — touchmoveListener: clears the long-press timer
 *   Group 2 [Risk 2] — updateInViewport (via ngOnChanges): non-virtual-scroll path keeps
 *                       inViewport=true; virtual-scroll path delegates to dataSource
 */

import { vi } from "vitest";
import {
   DRAG_SERVICE_MOCK,
   makeNode,
   makeTreeMock,
   renderTreeNode,
} from "./tree-node.component.test-helpers";

beforeEach(() => {
   Object.values(DRAG_SERVICE_MOCK).forEach(m => typeof m.mockClear === "function" && m.mockClear());
});

afterEach(() => {
   vi.restoreAllMocks();
});

// ===========================================================================
// Group 1 — touchmoveListener
// ===========================================================================

describe("Group 1 — touchmoveListener", () => {
   it("should cancel the long-press timer so touchend does NOT call clickSelectNode", async () => {
      const { comp } = await renderTreeNode({
         componentProperties: { contextmenu: true, isRepositoryTree: true, nodeSelectable: true },
      });
      vi.useFakeTimers();
      try {
         const clickSpy = vi.spyOn(comp, "clickSelectNode");
         const evt = { stopPropagation: vi.fn() } as any;

         comp.touchstartListener(evt);      // starts 500ms timer
         comp.touchmoveListener(evt);       // cancels timer, sets timeOutEvent = 0
         comp.touchendListener(evt);        // timeOutEvent == 0 → no clickSelectNode

         expect(clickSpy).not.toHaveBeenCalled();
      }
      finally {
         vi.useRealTimers();
      }
   });

   it("should also cancel the long-press contextmenu emission", async () => {
      const tree = makeTreeMock();
      const node = makeNode();
      tree.isSelectedNode = vi.fn().mockReturnValue(true);
      const { comp } = await renderTreeNode({
         node,
         tree,
         componentProperties: { contextmenu: true, isRepositoryTree: true },
      });
      vi.useFakeTimers();
      try {
         const spy = vi.spyOn(comp.onContextmenu, "emit");

         comp.touchstartListener({ stopPropagation: vi.fn() } as any);
         comp.touchmoveListener({ stopPropagation: vi.fn() } as any); // cancels timer
         vi.advanceTimersByTime(500); // advance past 500ms — callback was cancelled

         expect(spy).not.toHaveBeenCalled();
      }
      finally {
         vi.useRealTimers();
      }
   });
});

// ===========================================================================
// Group 2 — updateInViewport (non-virtual-scroll path via ngOnChanges)
// ===========================================================================

describe("Group 2 — updateInViewport (non-virtual-scroll path)", () => {
   it("should keep inViewport=true when useVirtualScroll is false (default)", async () => {
      const { comp } = await renderTreeNode();
      // ngOnChanges ran during render with no virtual scroll → inViewport stays true
      expect(comp.inViewport).toBe(true);
   });

   it("should keep inViewport=true after re-triggering ngOnChanges with a new node", async () => {
      const { comp, fixture } = await renderTreeNode();

      fixture.componentRef.setInput("node", makeNode({ label: "Updated" }));
      fixture.detectChanges();

      expect(comp.inViewport).toBe(true);
   });

   it("should subscribe to dataSource.virtualScroll and set inViewport based on visibility", async () => {
      const node = makeNode();
      const visibleNodes = [node];
      const dataSourceMock = {
         virtualScroll: {
            subscribe: vi.fn((cb: any) => {
               cb(visibleNodes); // immediately emit
               return { unsubscribe: vi.fn() };
            }),
         },
         inViewport: vi.fn().mockReturnValue(true),
         nodeVisible: vi.fn().mockReturnValue(true),
         inSearchCollapsed: vi.fn().mockReturnValue(false),
      };

      const { comp } = await renderTreeNode({
         node,
         componentProperties: {
            useVirtualScroll: true,
            dataSource: dataSourceMock as any,
         },
      });

      // node is in visibleNodes → inViewport = true
      expect(comp.inViewport).toBe(true);
   });

   it("should set inViewport=false when node is not in virtual scroll visible list", async () => {
      const node = makeNode();
      const otherNode = makeNode({ label: "Other" });
      const dataSourceMock = {
         virtualScroll: {
            subscribe: vi.fn((cb: any) => {
               cb([otherNode]); // node NOT in visible list
               return { unsubscribe: vi.fn() };
            }),
         },
         inViewport: vi.fn().mockReturnValue(false),
         nodeVisible: vi.fn().mockReturnValue(false),
         inSearchCollapsed: vi.fn().mockReturnValue(false),
      };

      const { comp } = await renderTreeNode({
         node,
         componentProperties: {
            useVirtualScroll: true,
            dataSource: dataSourceMock as any,
         },
      });

      expect(comp.inViewport).toBe(false);
   });
});
