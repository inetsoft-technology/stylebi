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
 * TreeNodeComponent — Pass 3: Display
 *
 * Coverage plan:
 *   Group 1 [Risk 1] — getIcon: folder/expanded-folder/leaf branches and iconFunction override
 *   Group 2 [Risk 1] — getToggleIcon: expanded vs collapsed caret; custom toggle icons
 *   Group 3 [Risk 1] — nodeLabel: alias > baseLabel > localStr > label fallback chain
 *   Group 4 [Risk 1] — getVirtualScrollShowChildren: no-virtual-scroll returns sorted children
 */

import { vi } from "vitest";
import {
   DRAG_SERVICE_MOCK,
   makeFolder,
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
// Group 1 — getIcon
// ===========================================================================

describe("Group 1 — getIcon", () => {
   it("should return empty string when showIcon is false", async () => {
      const { comp } = await renderTreeNode({ componentProperties: { showIcon: false } });
      expect(comp.getIcon()).toBe("");
   });

   it("should return folder-icon for collapsed folder", async () => {
      const node = makeFolder([], { expanded: false });
      const { comp } = await renderTreeNode({ node });
      expect(comp.getIcon()).toBe("folder-icon");
   });

   it("should return folder-open-icon for expanded folder", async () => {
      const node = makeFolder([], { expanded: true });
      const { comp } = await renderTreeNode({ node });
      expect(comp.getIcon()).toBe("folder-open-icon");
   });

   it("should return node.collapsedIcon when set for collapsed folder", async () => {
      const node = makeFolder([], { expanded: false, collapsedIcon: "my-closed-icon" });
      const { comp } = await renderTreeNode({ node });
      expect(comp.getIcon()).toBe("my-closed-icon");
   });

   it("should return node.expandedIcon when set for expanded folder", async () => {
      const node = makeFolder([], { expanded: true, expandedIcon: "my-open-icon" });
      const { comp } = await renderTreeNode({ node });
      expect(comp.getIcon()).toBe("my-open-icon");
   });

   it("should return data-table-icon for table-type folder", async () => {
      const node = makeFolder([makeNode()], { type: "table" });
      const { comp } = await renderTreeNode({ node });
      expect(comp.getIcon()).toBe("data-table-icon");
   });

   it("should return worksheet-icon for a leaf with non-empty data", async () => {
      const node = makeNode({ data: { path: "/ws/Sheet" } });
      const { comp } = await renderTreeNode({ node });
      expect(comp.getIcon()).toBe("worksheet-icon");
   });

   it("should prefer node.icon over default icon for a leaf", async () => {
      const node = makeNode({ data: { path: "/ws" }, icon: "custom-leaf-icon" });
      const { comp } = await renderTreeNode({ node });
      expect(comp.getIcon()).toBe("custom-leaf-icon");
   });

   it("should use iconFunction result when provided and no node.icon", async () => {
      const node = makeFolder([], { expanded: false });
      const iconFn = vi.fn().mockReturnValue("fn-icon");
      const { comp } = await renderTreeNode({ node, componentProperties: { iconFunction: iconFn } });
      // folder with no explicit icon → falls back to iconFunction result
      expect(comp.getIcon()).toBe("fn-icon");
   });
});

// ===========================================================================
// Group 2 — getToggleIcon
// ===========================================================================

describe("Group 2 — getToggleIcon", () => {
   it("should return caret-down-icon for expanded node", async () => {
      const node = makeFolder([], { expanded: true });
      const { comp } = await renderTreeNode({ node });
      expect(comp.getToggleIcon()).toBe("caret-down-icon icon-lg");
   });

   it("should return caret-right-icon for collapsed node", async () => {
      const node = makeFolder([], { expanded: false });
      const { comp } = await renderTreeNode({ node });
      expect(comp.getToggleIcon()).toBe("caret-right-icon icon-lg");
   });

   it("should return node.toggleExpandedIcon when set and expanded", async () => {
      const node = makeFolder([], { expanded: true, toggleExpandedIcon: "my-expanded-icon" });
      const { comp } = await renderTreeNode({ node });
      expect(comp.getToggleIcon()).toBe("my-expanded-icon");
   });

   it("should return node.toggleCollapsedIcon when set and collapsed", async () => {
      const node = makeFolder([], { expanded: false, toggleCollapsedIcon: "my-collapsed-icon" });
      const { comp } = await renderTreeNode({ node });
      expect(comp.getToggleIcon()).toBe("my-collapsed-icon");
   });
});

// ===========================================================================
// Group 3 — nodeLabel getter
// ===========================================================================

describe("Group 3 — nodeLabel getter", () => {
   it("should return alias when set", async () => {
      const node = makeNode({ label: "Original", baseLabel: "Base", alias: "Alias" });
      const { comp } = await renderTreeNode({ node });
      expect(comp.nodeLabel).toBe("Alias");
   });

   it("should return baseLabel when no alias", async () => {
      const node = makeNode({ label: "Original", baseLabel: "BaseLabel" });
      const { comp } = await renderTreeNode({ node });
      expect(comp.nodeLabel).toBe("BaseLabel");
   });

   it("should return data.properties.localStr when no alias or baseLabel", async () => {
      const node = makeNode({
         label: "Original",
         data: { properties: { localStr: "Localized" } },
      });
      const { comp } = await renderTreeNode({ node });
      expect(comp.nodeLabel).toBe("Localized");
   });

   it("should fall back to label when no alias, baseLabel, or localStr", async () => {
      const node = makeNode({ label: "Plain Label" });
      const { comp } = await renderTreeNode({ node });
      expect(comp.nodeLabel).toBe("Plain Label");
   });
});

// ===========================================================================
// Group 4 — getVirtualScrollShowChildren
// ===========================================================================

describe("Group 4 — getVirtualScrollShowChildren", () => {
   it("should return empty array when node is null", async () => {
      const { comp } = await renderTreeNode();
      comp.node = null as any;
      expect(comp.getVirtualScrollShowChildren()).toEqual([]);
   });

   it("should return all children sorted by searchStr when no virtual scroll", async () => {
      const child1 = makeNode({ label: "other" });
      const child2 = makeNode({ label: "nodeA" });
      const node = makeFolder([child1, child2]);
      const { comp } = await renderTreeNode({ node, componentProperties: { searchStr: "node" } });

      const result = comp.getVirtualScrollShowChildren();

      // SearchComparator: "nodeA" (starts with "node") ranks before "other"
      expect(result[0]).toBe(child2);
   });

   it("should return children in original order when no searchStr and no virtual scroll", async () => {
      const child1 = makeNode({ label: "B" });
      const child2 = makeNode({ label: "A" });
      const node = makeFolder([child1, child2]);
      const { comp } = await renderTreeNode({ node, componentProperties: { searchStr: "" } });

      const result = comp.getVirtualScrollShowChildren();

      expect(result[0]).toBe(child1);
      expect(result[1]).toBe(child2);
   });

   it("should filter children by dataSource.nodeVisible when virtual scroll is active", async () => {
      const visibleChild = makeNode({ label: "visible" });
      const hiddenChild = makeNode({ label: "hidden" });
      const node = makeFolder([visibleChild, hiddenChild]);
      const dataSourceMock = {
         virtualScroll: {
            subscribe: vi.fn(() => ({ unsubscribe: vi.fn() })),
         },
         inViewport: vi.fn().mockReturnValue(true),
         nodeVisible: vi.fn((n: any) => n === visibleChild),
         inSearchCollapsed: vi.fn().mockReturnValue(false),
      };

      const { comp } = await renderTreeNode({
         node,
         componentProperties: {
            useVirtualScroll: true,
            dataSource: dataSourceMock as any,
            searchStr: "",
         },
      });

      const result = comp.getVirtualScrollShowChildren();

      expect(result).toContain(visibleChild);
      expect(result).not.toContain(hiddenChild);
   });
});
