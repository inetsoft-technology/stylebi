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
 * DatabaseVPMComponent — Pass 1 (Interaction / lifecycle / user flows)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — ngOnInit create mode: databaseName/originalName extracted from vpmPath;
 *                        editing=false; vpm.name/description initialized; _isModified=true
 *   Group 2 [Risk 3] — ngOnInit edit mode: editing=true; refreshVPM fires HTTP GET → vpm populated
 *   Group 3 [Risk 3] — ngOnInit nameChange subscription: rename updates originalName+vpm.name;
 *                        null newName navigates to datasources; non-matching oldName is ignored
 *   Group 4 [Risk 3] — vpm setter: initializes hidden when null; preserves existing hidden;
 *                        always calls updateLookupList
 *   Group 5 [Risk 2] — changeHiddenExpression: sets hidden.script when hidden exists;
 *                        creates hidden with script when hidden is null
 *   Group 6 [Risk 2] — resetVPM: restores vpm to resetModel; clears conditionPane.selectedCondition
 *   Group 7 [Risk 2] — refreshedColumns: sets updateResetModel=true when !modified; =false when
 *                        modified; updates resetModel when refreshed=true + updateResetModel=true
 *   Group 8 [Risk 2] — canDeactivate: true when not modified; dialog ok→true; dialog cancel→false
 *   Group 9 [Risk 1] — selectVPMTab: sets currentVPMTab
 *   Group 10 [Risk 1] — updateLookupList: builds from conditions.tableName and hidden.hiddens.entity;
 *                         deduplicates
 *   Group 11 [Risk 1] — ngOnDestroy: unsubscribes subscriptions
 *
 * Out of scope this pass:
 *   refreshTestData(), refreshOperations(), refreshVPM(), isModified, saveVPM() —
 *   HTTP loading, race conditions, and async behavior → deferred to Pass 2.
 *
 * See also: database-vpm.component.test-helpers.ts for stubs, route factories, renderComp.
 */

import { waitFor } from "@testing-library/angular";
import { http, HttpResponse as MswHttpResponse } from "msw";

import { server } from "@test-mocks/server";
import { ComponentTool } from "../../../../../common/util/component-tool";
import { Tool } from "../../../../../../../../shared/util/tool";
import { makeCreateRoute, makeEditRoute, NameChangeModel, renderComp } from "./database-vpm.component.test-helpers";

// ---------------------------------------------------------------------------
// Global lifecycle
// ---------------------------------------------------------------------------

afterEach(() => {
   vi.restoreAllMocks();
});

// ---------------------------------------------------------------------------
// Group 1 — ngOnInit: create mode [Risk 3]
// ---------------------------------------------------------------------------

describe("DatabaseVPMComponent — ngOnInit: create mode", () => {
   // 🔁 Regression-sensitive: if databaseName/originalName are not set, saveVPM() sends
   // a malformed SaveVpmEvent with empty database/vpm fields to the server.

   it("should extract databaseName and originalName from vpmPath", async () => {
      const { comp } = await renderComp({
         route: makeCreateRoute({ vpmPath: "SalesDB/MySalesVPM", create: "true" }),
      });
      expect(comp.databaseName).toBe("SalesDB");
      expect(comp.originalName).toBe("MySalesVPM");
   });

   it("should set editing=false when create param is present", async () => {
      const { comp } = await renderComp({ route: makeCreateRoute() });
      expect(comp.editing).toBe(false);
   });

   it("should initialize vpm.name from the originalName route param", async () => {
      const { comp } = await renderComp({
         route: makeCreateRoute({ vpmPath: "myDB/NewPolicy", create: "true" }),
      });
      expect(comp.vpm.name).toBe("NewPolicy");
   });

   it("should initialize vpm.description from the desc route param", async () => {
      const { comp } = await renderComp({
         route: makeCreateRoute({ desc: "My policy description" }),
      });
      expect(comp.vpm.description).toBe("My policy description");
   });

   it("should set _isModified=true in create mode", async () => {
      const { comp } = await renderComp({ route: makeCreateRoute() });
      // Access backing field directly — isModified getter is tested in Pass 2
      expect((comp as any)._isModified).toBe(true);
   });

   it("should return early without updating state when vpmPath has no slash", async () => {
      const { comp } = await renderComp({
         route: makeCreateRoute({ vpmPath: "NoSlashPath" }),
      });
      // early return leaves databaseName/originalName as undefined
      expect(comp.databaseName).toBeUndefined();
      expect(comp.originalName).toBeUndefined();
   });
});

// ---------------------------------------------------------------------------
// Group 2 — ngOnInit: edit mode [Risk 3]
// ---------------------------------------------------------------------------

describe("DatabaseVPMComponent — ngOnInit: edit mode", () => {
   // 🔁 Regression-sensitive: if editing is not set to true, saveVPM() always POSTs (create)
   // instead of PUTting (update), producing duplicate VPM entries.

   it("should set editing=true when no create param is present", async () => {
      const { comp } = await renderComp({ route: makeEditRoute() });
      expect(comp.editing).toBe(true);
   });

   it("should populate vpm from the HTTP response after refreshVPM()", async () => {
      server.use(
         http.get("*/api/data/vpm/models", () =>
            MswHttpResponse.json({
               name: "existingVPM",
               conditions: [],
               hidden: { roles: [], hiddens: [], name: null, script: null },
               lookup: "SELECT * FROM users",
               description: "Existing policy",
            })
         )
      );
      const { comp } = await renderComp({ route: makeEditRoute() });
      await waitFor(() => expect(comp.vpm.name).toBe("existingVPM"));
      expect(comp.vpm.lookup).toBe("SELECT * FROM users");
   });

   it("should set _isModified=false after successful refreshVPM()", async () => {
      server.use(
         http.get("*/api/data/vpm/models", () =>
            MswHttpResponse.json({
               name: "loadedVPM", conditions: [], hidden: null, lookup: "", description: "",
            })
         )
      );
      const { comp } = await renderComp({ route: makeEditRoute() });
      await waitFor(() => expect(comp.vpm.name).toBe("loadedVPM"));
      expect((comp as any)._isModified).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 3 — ngOnInit: nameChange subscription [Risk 3]
// ---------------------------------------------------------------------------

describe("DatabaseVPMComponent — nameChange subscription", () => {
   // 🔁 Regression-sensitive: if originalName is not updated on rename, the next saveVPM()
   // sends a PUT with the stale name, overwriting the wrong VPM entry on the server.

   it("should update originalName and vpm.name when matching rename arrives", async () => {
      const { comp, nameChangeSubject } = await renderComp({
         route: makeCreateRoute({ vpmPath: "myDB/OldName" }),
      });

      nameChangeSubject.next(new NameChangeModel("OldName", "NewName"));

      expect(comp.originalName).toBe("NewName");
      expect(comp.vpm.name).toBe("NewName");
   });

   it("should navigate to datasources when newName=null (model deleted)", async () => {
      const { comp, nameChangeSubject, routerMock } = await renderComp({
         route: makeCreateRoute({ vpmPath: "myDB/ToDelete" }),
      });

      nameChangeSubject.next(new NameChangeModel("ToDelete", null));

      expect(routerMock.navigate).toHaveBeenCalledWith(
         ["/portal/tab/data/datasources"],
         expect.objectContaining({ queryParams: expect.objectContaining({ path: "/" }) })
      );
   });

   it("should not update when oldName does not match originalName", async () => {
      const { comp, nameChangeSubject } = await renderComp({
         route: makeCreateRoute({ vpmPath: "myDB/myVPM" }),
      });
      const originalName = comp.originalName;

      nameChangeSubject.next(new NameChangeModel("SomeOtherVPM", "Renamed"));

      expect(comp.originalName).toBe(originalName);
   });
});

// ---------------------------------------------------------------------------
// Group 4 — vpm setter [Risk 3]
// ---------------------------------------------------------------------------

describe("DatabaseVPMComponent — vpm setter", () => {
   // 🔁 Regression-sensitive: if hidden is not initialized, accessing vpm.hidden.hiddens
   // in VPMHiddenColumnsComponent throws TypeError immediately.

   it("should initialize hidden with empty defaults when hidden is null", async () => {
      const { comp } = await renderComp();
      comp.vpm = { name: "v", conditions: [], hidden: null, lookup: "", description: "" };
      expect(comp.vpm.hidden).not.toBeNull();
      expect(comp.vpm.hidden.hiddens).toEqual([]);
      expect(comp.vpm.hidden.roles).toEqual([]);
      expect(comp.vpm.hidden.script).toBeNull();
   });

   it("should preserve existing hidden when already set", async () => {
      const { comp } = await renderComp();
      const existingHidden = { roles: ["admin"], hiddens: [], name: "h1", script: "script" };
      comp.vpm = { name: "v", conditions: [], hidden: existingHidden, lookup: "", description: "" };
      expect(comp.vpm.hidden).toBe(existingHidden);
   });

   it("should update lookupList from conditions after setting vpm", async () => {
      const { comp } = await renderComp();
      comp.vpm = {
         name: "v",
         conditions: [{ name: "c1", clauses: [], type: 0, tableName: "Orders", script: "" }],
         hidden: null,
         lookup: "",
         description: "",
      };
      expect(comp.lookupList).toContain("Orders");
   });
});

// ---------------------------------------------------------------------------
// Group 5 — changeHiddenExpression [Risk 2]
// ---------------------------------------------------------------------------

describe("DatabaseVPMComponent — changeHiddenExpression()", () => {
   it("should set hidden.script when hidden already exists", async () => {
      const { comp } = await renderComp();
      comp.vpm.hidden = { roles: [], hiddens: [], name: null, script: null };
      comp.changeHiddenExpression("WHERE user = 'admin'");
      expect(comp.vpm.hidden.script).toBe("WHERE user = 'admin'");
   });

   it("should create a hidden object with the script when hidden is null", async () => {
      const { comp } = await renderComp();
      comp.vpm.hidden = null;
      comp.changeHiddenExpression("WHERE role = 'guest'");
      expect(comp.vpm.hidden).not.toBeNull();
      expect(comp.vpm.hidden.script).toBe("WHERE role = 'guest'");
   });
});

// ---------------------------------------------------------------------------
// Group 6 — resetVPM() [Risk 2]
// ---------------------------------------------------------------------------

describe("DatabaseVPMComponent — resetVPM()", () => {
   // 🔁 Regression-sensitive: if resetModel is not cloned when creating a new VPM, editing
   // the vpm in-place will mutate resetModel too, making reset a no-op.

   it("should restore vpm to the saved resetModel state", async () => {
      const { comp } = await renderComp();
      const savedName = comp.vpm.name;
      comp.vpm.name = "modifiedName";

      comp.resetVPM();

      expect(comp.vpm.name).toBe(savedName);
   });

   it("should set conditionPane.selectedCondition to null", async () => {
      const { comp } = await renderComp();
      comp.conditionPane.selectedCondition = { name: "activeCondition" };

      comp.resetVPM();

      expect(comp.conditionPane.selectedCondition).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 7 — refreshedColumns() [Risk 2]
// ---------------------------------------------------------------------------

describe("DatabaseVPMComponent — refreshedColumns()", () => {
   // 🔁 Regression-sensitive: if updateResetModel is not set correctly, a column refresh
   // that completes after the user starts editing will silently overwrite their resetModel,
   // making Reset restore to post-refresh state instead of pre-edit state.

   it("should set updateResetModel=true when refreshed=false and not modified", async () => {
      const { comp } = await renderComp();
      (comp as any)._isModified = false;
      // Also ensure isModified getter returns false (vpm == resetModel)
      comp.vpm = { ...comp.resetModel };

      comp.refreshedColumns(false);

      expect((comp as any).updateResetModel).toBe(true);
   });

   it("should set updateResetModel=false when refreshed=false and modified", async () => {
      const { comp } = await renderComp();
      // create mode starts with _isModified=true
      expect((comp as any)._isModified).toBe(true);

      comp.refreshedColumns(false);

      expect((comp as any).updateResetModel).toBe(false);
   });

   it("should update resetModel when refreshed=true and updateResetModel=true", async () => {
      const { comp } = await renderComp();
      (comp as any).updateResetModel = true;
      comp.vpm.name = "refreshedVPM";

      comp.refreshedColumns(true);

      expect(comp.resetModel.name).toBe("refreshedVPM");
      expect((comp as any).updateResetModel).toBe(false);
   });

   it("should not update resetModel when refreshed=true but updateResetModel=false", async () => {
      const { comp } = await renderComp();
      (comp as any).updateResetModel = false;
      const originalResetName = comp.resetModel.name;
      comp.vpm.name = "changedVPM";

      comp.refreshedColumns(true);

      expect(comp.resetModel.name).toBe(originalResetName);
   });
});

// ---------------------------------------------------------------------------
// Group 8 — canDeactivate() [Risk 2]
// ---------------------------------------------------------------------------

describe("DatabaseVPMComponent — canDeactivate()", () => {
   it("should return true immediately when there are no unsaved changes", async () => {
      const { comp } = await renderComp();
      // Align resetModel to current vpm (which already had hidden initialized by the setter)
      // so Tool.isEquals returns true and _isModified stays false.
      (comp as any).resetModel = Tool.clone(comp.vpm);
      (comp as any)._isModified = false;

      const result = await comp.canDeactivate();

      expect(result).toBe(true);
   });

   it("should show confirm dialog and return true when user clicks ok", async () => {
      const spy = vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");
      const { comp } = await renderComp();
      // create mode starts with _isModified=true
      try {
         const result = await comp.canDeactivate();
         expect(result).toBe(true);
      } finally {
         spy.mockRestore();
      }
   });

   it("should return false when user cancels the confirm dialog", async () => {
      const spy = vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("cancel");
      const { comp } = await renderComp();
      try {
         const result = await comp.canDeactivate();
         expect(result).toBe(false);
      } finally {
         spy.mockRestore();
      }
   });
});

// ---------------------------------------------------------------------------
// Group 9 — selectVPMTab() [Risk 1]
// ---------------------------------------------------------------------------

describe("DatabaseVPMComponent — selectVPMTab()", () => {
   it("should update currentVPMTab to the specified tab index", async () => {
      const { comp } = await renderComp();
      expect(comp.currentVPMTab).toBe(comp.VPMTabs.CONDITIONS);

      comp.selectVPMTab(comp.VPMTabs.HIDDEN_COLUMNS);

      expect(comp.currentVPMTab).toBe(comp.VPMTabs.HIDDEN_COLUMNS);
   });

   it("should switch to TEST tab", async () => {
      const { comp } = await renderComp();
      comp.selectVPMTab(comp.VPMTabs.TEST);
      expect(comp.currentVPMTab).toBe(comp.VPMTabs.TEST);
   });
});

// ---------------------------------------------------------------------------
// Group 10 — updateLookupList() [Risk 1]
// ---------------------------------------------------------------------------

describe("DatabaseVPMComponent — updateLookupList()", () => {
   it("should build lookupList from condition tableNames", async () => {
      const { comp } = await renderComp();
      comp.vpm.conditions = [
         { name: "c1", clauses: [], type: 0, tableName: "Orders", script: "" },
         { name: "c2", clauses: [], type: 0, tableName: "Customers", script: "" },
      ];
      comp.updateLookupList();
      expect(comp.lookupList).toContain("Orders");
      expect(comp.lookupList).toContain("Customers");
   });

   it("should build lookupList from hidden.hiddens entity names", async () => {
      const { comp } = await renderComp();
      comp.vpm.conditions = [];
      comp.vpm.hidden = {
         roles: [],
         hiddens: [
            { entity: "SalesData", attribute: "revenue", dataType: "double", refType: 0, description: "" } as any,
         ],
         name: null,
         script: null,
      };
      comp.updateLookupList();
      expect(comp.lookupList).toContain("SalesData");
   });

   it("should deduplicate entries that appear in both conditions and hiddens", async () => {
      const { comp } = await renderComp();
      comp.vpm.conditions = [
         { name: "c1", clauses: [], type: 0, tableName: "SharedTable", script: "" },
      ];
      comp.vpm.hidden = {
         roles: [],
         hiddens: [
            { entity: "SharedTable", attribute: "col", dataType: "string", refType: 0, description: "" } as any,
         ],
         name: null,
         script: null,
      };
      comp.updateLookupList();
      expect(comp.lookupList.filter(t => t === "SharedTable")).toHaveLength(1);
   });
});

// ---------------------------------------------------------------------------
// Group 11 — ngOnDestroy [Risk 1]
// ---------------------------------------------------------------------------

describe("DatabaseVPMComponent — ngOnDestroy()", () => {
   it("should stop reacting to nameChange events after destroy", async () => {
      const { comp, fixture, nameChangeSubject } = await renderComp({
         route: makeCreateRoute({ vpmPath: "myDB/myVPM" }),
      });
      const nameBefore = comp.originalName;

      fixture.destroy();
      nameChangeSubject.next(new NameChangeModel("myVPM", "PostDestroyName"));

      // subscription was torn down — originalName should not change
      expect(comp.originalName).toBe(nameBefore);
   });
});
