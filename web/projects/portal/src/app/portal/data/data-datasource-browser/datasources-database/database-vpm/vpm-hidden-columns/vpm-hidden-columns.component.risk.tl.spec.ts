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
 * P2 risk tests for VPMHiddenColumnsComponent.
 *
 * Covers: MAX column limit dialog, duplicate prevention, addTableNode HTTP path,
 * addAllToHiddenColumns (sync and async), timeout warning dialogs,
 * removeHiddenColumn / clearHiddenColumns / removeGrantRoles mutations,
 * and selectHiddenColumn ctrl-key branching.
 */

import { waitFor } from "@testing-library/angular";
import { http, HttpResponse as MswHttpResponse } from "msw";
import { server } from "@test-mocks/server";
import { ComponentTool } from "../../../../../../common/util/component-tool";
import { MessageDialog } from "../../../../../../widget/dialog/message-dialog/message-dialog.component";
import { DatabaseTreeNodeType } from "../../../../model/datasources/database/database-tree-node-type";
import {
   makeColumnNode, makeDataRef, makeHidden, makeTableNode, renderComp,
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
// Group 1: addColumnNodeToHiddenColumn — MAX limit (500)
// ---------------------------------------------------------------------------

describe("VPMHiddenColumnsComponent — addColumnNodeToHiddenColumn: MAX limit", () => {
   it("should show a warning dialog and return false when 500 hidden columns are already present", async () => {
      const hiddens = Array.from({ length: 500 }, (_, i) => makeDataRef(`col${i}`, "T"));
      const { comp } = await renderComp({ hidden: makeHidden({ hiddens }) });
      const dialogSpy = vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("ok");
      try {
         const newNode = makeColumnNode({
            data: { attribute: "extra", entity: "T", fullName: "T.extra", qualifiedName: "extra" },
         });
         const result = comp.addColumnNodeToHiddenColumn(newNode);

         expect(result).toBe(false);
         expect(dialogSpy).toHaveBeenCalledWith(expect.anything(), "_#(js:Warning)", expect.any(String));
         expect(comp.hidden.hiddens).toHaveLength(500);
      } finally {
         dialogSpy.mockRestore();
      }
   });
});

// ---------------------------------------------------------------------------
// Group 2: addColumnNodeToHiddenColumn — duplicate prevention
// ---------------------------------------------------------------------------

describe("VPMHiddenColumnsComponent — addColumnNodeToHiddenColumn: duplicate", () => {
   it("should not add a second entry for a column with the same name and entity", async () => {
      const { comp } = await renderComp({ hidden: makeHidden() });
      const node = makeColumnNode();

      comp.addColumnNodeToHiddenColumn(node);
      comp.addColumnNodeToHiddenColumn(node);

      expect(comp.hidden.hiddens).toHaveLength(1);
   });
});

// ---------------------------------------------------------------------------
// Group 3: addTableNodeToHiddenColumn — table already loaded (synchronous)
// ---------------------------------------------------------------------------

describe("VPMHiddenColumnsComponent — addTableNodeToHiddenColumn: already loaded", () => {
   it("should add all pre-loaded child column nodes synchronously and emit hiddenColumnsChange", async () => {
      const colA = makeColumnNode({ label: "colA", data: { attribute: "colA", entity: "T", fullName: "T.colA", qualifiedName: "q1" } });
      const colB = makeColumnNode({ label: "colB", data: { attribute: "colB", entity: "T", fullName: "T.colB", qualifiedName: "q2" } });
      const tableNode = makeTableNode([colA, colB]);
      const { comp } = await renderComp({ hidden: makeHidden() });
      const emitSpy = vi.spyOn(comp.hiddenColumnsChange, "emit");

      comp.addTableNodeToHiddenColumn(tableNode);

      expect(comp.hidden.hiddens).toHaveLength(2);
      expect(emitSpy).toHaveBeenCalledOnce();
   });
});

// ---------------------------------------------------------------------------
// Group 4: addTableNodeToHiddenColumn — table not loaded (HTTP path)
// ---------------------------------------------------------------------------

describe("VPMHiddenColumnsComponent — addTableNodeToHiddenColumn: not loaded", () => {
   it("should fetch table children via HTTP and add them to hiddens", async () => {
      const colNode = makeColumnNode();
      server.use(
         http.post("*/api/data/vpm/hiddenColumn/tree", () => MswHttpResponse.json([colNode]))
      );
      const tableNode = makeTableNode(); // childrenLoaded=false, children=[]
      const { comp } = await renderComp({ hidden: makeHidden() });

      comp.addTableNodeToHiddenColumn(tableNode);

      await waitFor(() => expect(comp.hidden.hiddens).toHaveLength(1));
   });
});

// ---------------------------------------------------------------------------
// Group 5: addAllToHiddenColumns
// ---------------------------------------------------------------------------

describe("VPMHiddenColumnsComponent — addAllToHiddenColumns", () => {
   it("should clear existing hiddens and repopulate from already-loaded tree without HTTP", async () => {
      const colNode = makeColumnNode();
      const tableNode = makeTableNode([colNode]);
      const preexisting = makeDataRef("old");
      const { comp } = await renderComp({ hidden: makeHidden({ hiddens: [preexisting] }) });
      comp.fullTreeLoaded = true;
      comp.databaseRoot.children = [tableNode];
      const emitSpy = vi.spyOn(comp.hiddenColumnsChange, "emit");

      comp.addAllToHiddenColumns();

      expect(comp.hidden.hiddens).toHaveLength(1);
      expect(comp.hidden.hiddens[0].attribute).toBe("col1");
      expect(emitSpy).toHaveBeenCalledOnce(); // clearHiddenColumns emits once
   });

   it("should load full tree via HTTP then add all columns when tree is not yet loaded", async () => {
      const colNode = makeColumnNode();
      const tableNode = makeTableNode([colNode]);
      server.use(
         // distinctive init response so we can wait for the initial POST to settle
         http.post("*/api/data/vpm/hiddenColumn/tree", () =>
            MswHttpResponse.json([{ label: "__init__", type: DatabaseTreeNodeType.TABLE, leaf: false, children: [] }])
         ),
         http.get("*/api/data/vpm/hiddenColumn/fullTree/*", () =>
            MswHttpResponse.json({ nodes: [tableNode], timeOut: false })
         )
      );
      const { comp } = await renderComp({ hidden: makeHidden() });
      await waitFor(() => expect(comp.databaseRoot.children).toHaveLength(1)); // init POST settled

      comp.addAllToHiddenColumns();

      await waitFor(() => expect(comp.fullTreeLoaded).toBe(true));
      expect(comp.hidden.hiddens).toHaveLength(1);
   });

   it("should show a timeout warning dialog when full-tree load exceeds the server limit", async () => {
      server.use(
         http.get("*/api/data/vpm/hiddenColumn/fullTree/*", () =>
            MswHttpResponse.json({ nodes: [], timeOut: true })
         )
      );
      const { comp } = await renderComp({ hidden: makeHidden() });
      const dialogSpy = vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("ok");
      try {
         comp.addAllToHiddenColumns();
         await waitFor(() => expect(comp.fullTreeLoaded).toBe(true));
         expect(dialogSpy).toHaveBeenCalledWith(expect.anything(), "_#(js:Warning)", expect.any(String));
      } finally {
         dialogSpy.mockRestore();
      }
   });
});

// ---------------------------------------------------------------------------
// Group 6: refreshFilterTreeModel — timeout warning
// ---------------------------------------------------------------------------

describe("VPMHiddenColumnsComponent — refreshFilterTreeModel: timeout", () => {
   it("should show a timeout warning dialog when loadFullDatabaseTree times out", async () => {
      server.use(
         http.get("*/api/data/vpm/hiddenColumn/fullTree/*", () =>
            MswHttpResponse.json({ nodes: [], timeOut: true })
         )
      );
      const { comp } = await renderComp({ hidden: makeHidden() });
      const dialogSpy = vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("ok");
      try {
         comp.refreshFilterTreeModel("TABLE");
         await waitFor(() => expect(comp.fullTreeLoaded).toBe(true));
         expect(dialogSpy).toHaveBeenCalledWith(expect.anything(), "_#(js:Warning)", expect.any(String));
      } finally {
         dialogSpy.mockRestore();
      }
   });
});

// ---------------------------------------------------------------------------
// Group 7: removeHiddenColumn
// ---------------------------------------------------------------------------

describe("VPMHiddenColumnsComponent — removeHiddenColumn", () => {
   it("should remove only the selected columns and emit hiddenColumnsChange", async () => {
      const col1 = makeDataRef("col1", "T");
      const col2 = makeDataRef("col2", "T");
      const { comp } = await renderComp({ hidden: makeHidden({ hiddens: [col1, col2] }) });
      const emitSpy = vi.spyOn(comp.hiddenColumnsChange, "emit");
      comp.selectedHiddenColumns = [col1];

      comp.removeHiddenColumn();

      expect(comp.hidden.hiddens).toEqual([col2]);
      expect(comp.selectedHiddenColumns).toHaveLength(0);
      expect(emitSpy).toHaveBeenCalledOnce();
   });
});

// ---------------------------------------------------------------------------
// Group 8: clearHiddenColumns
// ---------------------------------------------------------------------------

describe("VPMHiddenColumnsComponent — clearHiddenColumns", () => {
   it("should clear all hiddens, deselect, and emit hiddenColumnsChange", async () => {
      const col1 = makeDataRef("col1", "T");
      const { comp } = await renderComp({ hidden: makeHidden({ hiddens: [col1] }) });
      const emitSpy = vi.spyOn(comp.hiddenColumnsChange, "emit");
      comp.selectedHiddenColumns = [col1];

      comp.clearHiddenColumns();

      expect(comp.hidden.hiddens).toHaveLength(0);
      expect(comp.selectedHiddenColumns).toHaveLength(0);
      expect(emitSpy).toHaveBeenCalledOnce();
   });
});

// ---------------------------------------------------------------------------
// Group 9: removeGrantRoles
// ---------------------------------------------------------------------------

describe("VPMHiddenColumnsComponent — removeGrantRoles", () => {
   it("should remove the selected grant roles and clear the selection", async () => {
      const { comp } = await renderComp({ hidden: makeHidden({ roles: ["r1", "r2"] }) });
      comp.selectedGrantRoles = ["r1"];

      comp.removeGrantRoles();

      expect(comp.hidden.roles).toEqual(["r2"]);
      expect(comp.selectedGrantRoles).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 10: selectHiddenColumn — ctrl-key branching
// ---------------------------------------------------------------------------

describe("VPMHiddenColumnsComponent — selectHiddenColumn", () => {
   it("should replace selection when ctrl key is not held", async () => {
      const col1 = makeDataRef("col1", "T");
      const col2 = makeDataRef("col2", "T");
      const { comp } = await renderComp({ hidden: makeHidden({ hiddens: [col1, col2] }) });
      comp.selectedHiddenColumns = [col1];

      comp.selectHiddenColumn(new MouseEvent("click", { ctrlKey: false }), col2);

      expect(comp.selectedHiddenColumns).toEqual([col2]);
   });

   it("should add to existing selection when ctrl key is held", async () => {
      const col1 = makeDataRef("col1", "T");
      const col2 = makeDataRef("col2", "T");
      const { comp } = await renderComp({ hidden: makeHidden({ hiddens: [col1, col2] }) });
      comp.selectedHiddenColumns = [col1];

      comp.selectHiddenColumn(new MouseEvent("click", { ctrlKey: true }), col2);

      expect(comp.selectedHiddenColumns).toEqual([col1, col2]);
   });
});
