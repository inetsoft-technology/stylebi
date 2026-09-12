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
 * VPMHiddenColumnsComponent — Pass 1 (interaction / lifecycle / loading flows)
 *
 * Group 1 — ngOnInit: initDataSourceTree sends POST to tree URI; currOrg set from service
 * Group 2 — refreshFilterTreeModel: loads full tree on non-empty filter; no-op on empty; skip
 *            if already loaded
 * Group 3 — expandNode: loads children when not loaded; no-op when already loaded
 * Group 4 — addColumnNodeToHiddenColumn: builds AttributeRef; deduplication; MAX_HIDDEN_COLUMN
 *            warning and abort
 * Group 5 — addTableNodeToHiddenColumn: adds pre-loaded columns; expands unloaded table
 * Group 6 — addHiddenColumn: skips unsupported node types; emits hiddenColumnsChange
 * Group 7 — selectGrantRole / selectAvailableRole: single vs ctrl multi-select
 * Group 8 — addGrantRoles: adds available to granted roles; skips duplicates
 * Group 9 — getTreeNodeIcon / getBaseName: display helpers
 *
 * See also: vpm-hidden-columns.component.test-helpers.ts
 */

import { waitFor } from "@testing-library/angular";
import { http, HttpResponse as MswHttpResponse } from "msw";

import { server } from "@test-mocks/server";
import { ComponentTool } from "../../../../../../common/util/component-tool";
import { MessageDialog } from "../../../../../../widget/dialog/message-dialog/message-dialog.component";
import { DatabaseTreeNodeType } from "../../../../model/datasources/database/database-tree-node-type";
import {
   makeColumnNode,
   makeHidden,
   makeTableNode,
   renderComp,
} from "./vpm-hidden-columns.component.test-helpers";

// ---------------------------------------------------------------------------
// Global lifecycle
// ---------------------------------------------------------------------------

beforeEach(() => {
   MessageDialog.lastMessage = null;
   (MessageDialog as any).lastMessageTS = 0;
});

afterEach(() => {
   vi.restoreAllMocks();
});

// ---------------------------------------------------------------------------
// Group 1 — ngOnInit
// ---------------------------------------------------------------------------

describe("VPMHiddenColumnsComponent — ngOnInit", () => {
   it("should POST to DATABASE_TREE_URI and populate databaseRoot.children on init", async () => {
      const rootNodes = [{ label: "TABLE1", type: DatabaseTreeNodeType.TABLE, leaf: false, children: [] }];
      server.use(
         http.post("*/api/data/vpm/hiddenColumn/tree", () => MswHttpResponse.json(rootNodes))
      );
      const { comp } = await renderComp();

      await waitFor(() => expect(comp.databaseRoot.children).toHaveLength(1));
      expect(comp.databaseRoot.children[0].label).toBe("TABLE1");
   });

   it("should set currOrg from currentUserService on init", async () => {
      const { comp } = await renderComp({ currentOrgID: "myOrg" });

      await waitFor(() => expect((comp as any).currOrg).toBe("myOrg"));
   });
});

// ---------------------------------------------------------------------------
// Group 2 — refreshFilterTreeModel
// ---------------------------------------------------------------------------

describe("VPMHiddenColumnsComponent — refreshFilterTreeModel", () => {
   it("should load full tree when filter is non-empty and fullTreeLoaded is false", async () => {
      const initNode = { label: "INIT", type: DatabaseTreeNodeType.TABLE, leaf: false, children: [] };
      const fullNodes = [{ label: "TABLE2", type: DatabaseTreeNodeType.TABLE, leaf: false, children: [] }];
      server.use(
         // use a distinctive initial POST response so we can wait for it to complete
         http.post("*/api/data/vpm/hiddenColumn/tree", () => MswHttpResponse.json([initNode])),
         http.get("*/api/data/vpm/hiddenColumn/fullTree/*", () =>
            MswHttpResponse.json({ nodes: fullNodes, timeOut: false })
         )
      );
      const { comp } = await renderComp();
      // wait for the initial POST to settle before triggering loadFullDatabaseTree
      await waitFor(() => expect(comp.databaseRoot.children).toHaveLength(1));

      comp.refreshFilterTreeModel("TABLE");

      await waitFor(() => expect(comp.fullTreeLoaded).toBe(true));
      expect(comp.databaseRoot.children[0].label).toBe("TABLE2");
   });

   it("should not load full tree when filter is empty or whitespace", async () => {
      const loadSpy = vi.fn();
      server.use(
         http.get("*/api/data/vpm/hiddenColumn/fullTree/*", () => {
            loadSpy();
            return MswHttpResponse.json({ nodes: [], timeOut: false });
         })
      );
      const { comp } = await renderComp();

      comp.refreshFilterTreeModel("");
      comp.refreshFilterTreeModel("   ");

      await waitFor(() => {});
      expect(loadSpy).not.toHaveBeenCalled();
   });

   it("should not load full tree again when fullTreeLoaded is already true", async () => {
      const loadSpy = vi.fn();
      server.use(
         http.get("*/api/data/vpm/hiddenColumn/fullTree/*", () => {
            loadSpy();
            return MswHttpResponse.json({ nodes: [], timeOut: false });
         })
      );
      const { comp } = await renderComp();
      (comp as any).fullTreeLoaded = true;

      comp.refreshFilterTreeModel("TABLE");

      await waitFor(() => {});
      expect(loadSpy).not.toHaveBeenCalled();
   });

   it("should show a warning dialog when the full tree times out", async () => {
      const dialogSpy = vi.spyOn(ComponentTool, "showMessageDialog").mockReturnValue(
         new Promise(() => {})
      );
      server.use(
         http.get("*/api/data/vpm/hiddenColumn/fullTree/*", () =>
            MswHttpResponse.json({ nodes: [], timeOut: true })
         )
      );
      const { comp } = await renderComp();

      comp.refreshFilterTreeModel("TABLE");

      await waitFor(() =>
         expect(dialogSpy).toHaveBeenCalledWith(expect.anything(), "_#(js:Warning)", expect.any(String))
      );
   });
});

// ---------------------------------------------------------------------------
// Group 3 — expandNode
// ---------------------------------------------------------------------------

describe("VPMHiddenColumnsComponent — expandNode", () => {
   it("should load children via POST when node has no children", async () => {
      const childNodes = [makeColumnNode({ label: "col1" })];
      server.use(
         http.post("*/api/data/vpm/hiddenColumn/tree", () => MswHttpResponse.json(childNodes))
      );
      const tableNode = makeTableNode([], { childrenLoaded: false });
      const { comp } = await renderComp();

      // wait for initial tree load to settle first
      await waitFor(() => {});
      comp.expandNode(tableNode);

      await waitFor(() => expect(tableNode.children).toHaveLength(1));
      expect(tableNode.children[0].label).toBe("col1");
   });

   it("should not make HTTP call when node already has children loaded", async () => {
      const postSpy = vi.fn();
      server.use(
         http.post("*/api/data/vpm/hiddenColumn/tree", () => {
            postSpy();
            return MswHttpResponse.json([]);
         })
      );
      const tableNode = makeTableNode([makeColumnNode()], { childrenLoaded: true });
      const { comp } = await renderComp();

      await waitFor(() => {});
      const initialCallCount = postSpy.mock.calls.length;

      comp.expandNode(tableNode);

      await waitFor(() => {});
      expect(postSpy.mock.calls.length).toBe(initialCallCount);
   });
});

// ---------------------------------------------------------------------------
// Group 4 — addColumnNodeToHiddenColumn
// ---------------------------------------------------------------------------

describe("VPMHiddenColumnsComponent — addColumnNodeToHiddenColumn", () => {
   it("should build an AttributeRef with the correct name and entity and push to hiddens", async () => {
      const hidden = makeHidden();
      const { comp } = await renderComp({ hidden });
      const colNode = makeColumnNode({
         data: { attribute: "col1", entity: "ORDERS", fullName: "ORDERS.col1", qualifiedName: "col1" },
      });

      comp.addColumnNodeToHiddenColumn(colNode);

      expect(hidden.hiddens).toHaveLength(1);
      expect(hidden.hiddens[0].name).toBe("col1.col1");
      expect(hidden.hiddens[0].entity).toBe("ORDERS");
   });

   it("should not add duplicate columns (same name and entity)", async () => {
      const hidden = makeHidden();
      const { comp } = await renderComp({ hidden });
      const colNode = makeColumnNode({
         data: { attribute: "col1", entity: "T", fullName: "T.col1", qualifiedName: "col1" },
      });

      comp.addColumnNodeToHiddenColumn(colNode);
      comp.addColumnNodeToHiddenColumn(colNode);

      expect(hidden.hiddens).toHaveLength(1);
   });

   it("should show warning dialog and return false when MAX_HIDDEN_COLUMN (500) is reached", async () => {
      const dialogSpy = vi.spyOn(ComponentTool, "showMessageDialog").mockReturnValue(
         new Promise(() => {})
      );
      const existingRefs = Array.from({ length: 500 }, (_, i) => ({
         classType: "AttributeRef", name: `col${i}`, entity: "", attribute: `col${i}`, caption: "",
      }));
      const hidden = makeHidden({ hiddens: existingRefs as any });
      const { comp } = await renderComp({ hidden });
      const colNode = makeColumnNode();

      const result = comp.addColumnNodeToHiddenColumn(colNode);

      expect(result).toBe(false);
      expect(dialogSpy).toHaveBeenCalledWith(expect.anything(), "_#(js:Warning)", expect.any(String));
      expect(hidden.hiddens).toHaveLength(500);
   });
});

// ---------------------------------------------------------------------------
// Group 5 — addTableNodeToHiddenColumn
// ---------------------------------------------------------------------------

describe("VPMHiddenColumnsComponent — addTableNodeToHiddenColumn", () => {
   it("should add all column children when the table node has pre-loaded children", async () => {
      const hidden = makeHidden();
      const colNode = makeColumnNode({
         data: { attribute: "c1", entity: "T", fullName: "T.c1", qualifiedName: "c1" },
      });
      const tableNode = makeTableNode([colNode]);
      const { comp } = await renderComp({ hidden });

      comp.addTableNodeToHiddenColumn(tableNode);

      expect(hidden.hiddens).toHaveLength(1);
      expect(hidden.hiddens[0].attribute).toBe("c1");
   });

   it("should emit hiddenColumnsChange after adding table's columns", async () => {
      const hidden = makeHidden();
      const colNode = makeColumnNode({ data: { attribute: "c1", entity: "T", fullName: "", qualifiedName: "c1" } });
      const tableNode = makeTableNode([colNode]);
      const { comp } = await renderComp({ hidden });
      const changeSpy = vi.fn();
      comp.hiddenColumnsChange.subscribe(changeSpy);

      comp.addTableNodeToHiddenColumn(tableNode);

      expect(changeSpy).toHaveBeenCalledTimes(1);
   });

   it("should load table children via expandNode when children are not yet loaded", async () => {
      const colNode = makeColumnNode({
         data: { attribute: "lazyCol", entity: "T", fullName: "T.lazyCol", qualifiedName: "lazyCol" },
      });
      server.use(
         http.post("*/api/data/vpm/hiddenColumn/tree", () => MswHttpResponse.json([colNode]))
      );
      const tableNode = makeTableNode([], { childrenLoaded: false });
      const hidden = makeHidden();
      const { comp } = await renderComp({ hidden });

      // wait for initial load to settle
      await waitFor(() => {});
      comp.addTableNodeToHiddenColumn(tableNode);

      await waitFor(() => expect(hidden.hiddens).toHaveLength(1));
      expect(hidden.hiddens[0].attribute).toBe("lazyCol");
   });
});

// ---------------------------------------------------------------------------
// Group 6 — addHiddenColumn
// ---------------------------------------------------------------------------

describe("VPMHiddenColumnsComponent — addHiddenColumn", () => {
   it("should not add nodes that are not COLUMN, TABLE, or ALIAS_TABLE type", async () => {
      const hidden = makeHidden();
      const { comp } = await renderComp({ hidden });
      const folderNode = { type: DatabaseTreeNodeType.FOLDER, label: "folder" };
      (comp.columnTree as any).selectedNodes = [folderNode];

      comp.addHiddenColumn();

      expect(hidden.hiddens).toHaveLength(0);
   });

   it("should emit hiddenColumnsChange after adding", async () => {
      const hidden = makeHidden();
      const { comp } = await renderComp({ hidden });
      const changeSpy = vi.fn();
      comp.hiddenColumnsChange.subscribe(changeSpy);
      const colNode = makeColumnNode({ data: { attribute: "c", entity: "T", fullName: "", qualifiedName: "c" } });
      (comp.columnTree as any).selectedNodes = [colNode];

      comp.addHiddenColumn();

      expect(changeSpy).toHaveBeenCalledTimes(1);
   });
});

// ---------------------------------------------------------------------------
// Group 7 — selectGrantRole / selectAvailableRole
// ---------------------------------------------------------------------------

describe("VPMHiddenColumnsComponent — selectGrantRole", () => {
   it("should replace selectedGrantRoles on plain click", async () => {
      const { comp } = await renderComp({ hidden: makeHidden({ roles: ["roleA", "roleB"] }) });
      comp.selectGrantRole(new MouseEvent("click"), "roleA");
      comp.selectGrantRole(new MouseEvent("click"), "roleB");

      expect(comp.selectedGrantRoles).toHaveLength(1);
      expect(comp.selectedGrantRoles[0]).toBe("roleB");
   });

   it("should append to selectedGrantRoles on ctrl+click", async () => {
      const { comp } = await renderComp({ hidden: makeHidden({ roles: ["roleA", "roleB"] }) });
      comp.selectGrantRole(new MouseEvent("click"), "roleA");
      comp.selectGrantRole(new MouseEvent("click", { ctrlKey: true }), "roleB");

      expect(comp.selectedGrantRoles).toHaveLength(2);
   });

   it("should not add duplicate to selectedGrantRoles on ctrl+click", async () => {
      const { comp } = await renderComp({ hidden: makeHidden({ roles: ["roleA"] }) });
      comp.selectGrantRole(new MouseEvent("click"), "roleA");
      comp.selectGrantRole(new MouseEvent("click", { ctrlKey: true }), "roleA");

      expect(comp.selectedGrantRoles).toHaveLength(1);
   });
});

describe("VPMHiddenColumnsComponent — selectAvailableRole", () => {
   it("should replace selectedAvailableRoles on plain click", async () => {
      const { comp } = await renderComp({ availableRoles: [{ label: "R1", value: "R1" }, { label: "R2", value: "R2" }] });
      comp.selectAvailableRole(new MouseEvent("click"), "R1");
      comp.selectAvailableRole(new MouseEvent("click"), "R2");

      expect(comp.selectedAvailableRoles).toHaveLength(1);
      expect(comp.selectedAvailableRoles[0]).toBe("R2");
   });
});

// ---------------------------------------------------------------------------
// Group 8 — addGrantRoles
// ---------------------------------------------------------------------------

describe("VPMHiddenColumnsComponent — addGrantRoles", () => {
   it("should push selectedAvailableRoles into hidden.roles", async () => {
      const hidden = makeHidden();
      const { comp } = await renderComp({ hidden, availableRoles: [{ label: "admin", value: "admin" }] });
      comp.selectedAvailableRoles = ["admin", "viewer"];

      comp.addGrantRoles();

      expect(hidden.roles).toContain("admin");
      expect(hidden.roles).toContain("viewer");
   });

   it("should not add duplicate roles", async () => {
      const hidden = makeHidden({ roles: ["admin"] });
      const { comp } = await renderComp({ hidden });
      comp.selectedAvailableRoles = ["admin"];

      comp.addGrantRoles();

      expect(hidden.roles.filter(r => r === "admin")).toHaveLength(1);
   });
});

// ---------------------------------------------------------------------------
// Group 9 — getTreeNodeIcon / getBaseName
// ---------------------------------------------------------------------------

describe("VPMHiddenColumnsComponent — getTreeNodeIcon", () => {
   it("should return folder-icon for FOLDER type", async () => {
      const { comp } = await renderComp();
      expect(comp.getTreeNodeIcon({ type: DatabaseTreeNodeType.FOLDER } as any)).toBe("folder-icon");
   });

   it("should return db-model-icon for PHYSICAL_MODEL type", async () => {
      const { comp } = await renderComp();
      expect(comp.getTreeNodeIcon({ type: DatabaseTreeNodeType.PHYSICAL_MODEL } as any)).toBe("db-model-icon");
   });

   it("should return data-table-icon for TABLE type", async () => {
      const { comp } = await renderComp();
      expect(comp.getTreeNodeIcon({ type: DatabaseTreeNodeType.TABLE } as any)).toBe("data-table-icon");
   });

   it("should return column-icon for COLUMN type", async () => {
      const { comp } = await renderComp();
      expect(comp.getTreeNodeIcon({ type: DatabaseTreeNodeType.COLUMN } as any)).toBe("column-icon");
   });

   it("should return folder-icon when node is null", async () => {
      const { comp } = await renderComp();
      expect(comp.getTreeNodeIcon(null)).toBe("folder-icon");
   });
});

describe("VPMHiddenColumnsComponent — getBaseName", () => {
   it("should strip the org suffix when it appears in the role name", async () => {
      const { comp } = await renderComp({ currentOrgID: "corp" });
      await waitFor(() => expect((comp as any).currOrg).toBe("corp"));

      const result = comp.getBaseName("adminOFcorp");

      // "adminOFcorp".indexOf("corp") === 7 → substring(0, 7-3) === "admi"
      expect(result).toBe("admi");
   });

   it("should return the full name when no org suffix is present", async () => {
      const { comp } = await renderComp({ currentOrgID: "corp" });
      await waitFor(() => expect((comp as any).currOrg).toBe("corp"));

      expect(comp.getBaseName("viewer")).toBe("viewer");
   });
});
