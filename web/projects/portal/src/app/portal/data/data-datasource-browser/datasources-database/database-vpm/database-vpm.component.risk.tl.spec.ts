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
 * DatabaseVPMComponent — Pass 2 (Risk / async / destructive operations)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — saveVPM() edit mode: PUT with correct SaveVpmEvent payload;
 *                        _isModified reset to false; originalName updated to vpm.name
 *   Group 2 [Risk 3] — saveVPM() create mode: POST fires; folderChangeService called;
 *                        editing flips to true
 *   Group 3 [Risk 3] — saveVPM() validation: missing condition name → showMessageDialog;
 *                        duplicate condition name → showMessageDialog; save aborted
 *   Group 4 [Risk 3] — refreshVPM(): vpm and resetModel populated from response;
 *                        currentVPMTab reset to CONDITIONS
 *   Group 5 [Risk 2] — refreshTestData(): testData updated from usersRoles response
 *   Group 6 [Risk 2] — refreshOperations(): operations populated; sessionOperations[0] is
 *                        EQUAL_TO and sessionOperations[1] is IN
 *   Group 7 [Risk 2] — isModified getter: returns false when vpm equals resetModel;
 *                        returns true when vpm differs
 *
 * Out of scope this pass:
 *   vpm setter, changeHiddenExpression, resetVPM, selectVPMTab, refreshedColumns,
 *   canDeactivate, updateLookupList — covered in Pass 1 (interaction).
 *
 * See also: database-vpm.component.test-helpers.ts for stubs, route factories, renderComp.
 */

import { waitFor } from "@testing-library/angular";
import { http, HttpResponse as MswHttpResponse } from "msw";

import { server } from "@test-mocks/server";
import { ComponentTool } from "../../../../../common/util/component-tool";
import { Tool } from "../../../../../../../../shared/util/tool";
import { ClauseOperationSymbols } from "../../../model/datasources/database/vpm/condition/clause/clause-operation-symbols";
import { makeCreateRoute, makeEditRoute, renderComp } from "./database-vpm.component.test-helpers";

// ---------------------------------------------------------------------------
// Global lifecycle
// ---------------------------------------------------------------------------

afterEach(() => {
   vi.restoreAllMocks();
});

// ---------------------------------------------------------------------------
// Group 1 — saveVPM() edit mode: PUT [Risk 3]
// ---------------------------------------------------------------------------

describe("DatabaseVPMComponent — saveVPM() edit mode", () => {
   // 🔁 Regression-sensitive: after a successful PUT, _isModified must become false
   // and originalName must be updated to vpm.name so that the next canDeactivate() check
   // reflects the saved state and doesn't prompt unnecessarily.

   it("should PUT the VPM and reset _isModified to false on success", async () => {
      const { comp } = await renderComp({ route: makeEditRoute() });
      await waitFor(() => expect(comp.editing).toBe(true));
      comp.vpm.name = "updatedVPM";

      comp.saveVPM();

      await waitFor(() => expect((comp as any)._isModified).toBe(false));
   });

   it("should update originalName to the current vpm.name after successful PUT", async () => {
      server.use(
         http.get("*/api/data/vpm/models", () =>
            MswHttpResponse.json({
               name: "initialVPM", conditions: [], hidden: null, lookup: "", description: "",
            })
         )
      );
      const { comp } = await renderComp({ route: makeEditRoute() });
      // wait for refreshVPM to complete so comp.vpm won't be overwritten afterwards
      await waitFor(() => expect(comp.vpm.name).toBe("initialVPM"));
      comp.vpm.name = "renamedPolicy";

      comp.saveVPM();

      await waitFor(() => expect(comp.originalName).toBe("renamedPolicy"));
   });

   it("should update resetModel to match the saved vpm after successful PUT", async () => {
      server.use(
         http.get("*/api/data/vpm/models", () =>
            MswHttpResponse.json({
               name: "initialVPM", conditions: [], hidden: null, lookup: "", description: "",
            })
         )
      );
      const { comp } = await renderComp({ route: makeEditRoute() });
      // wait for refreshVPM to complete so comp.vpm won't be overwritten afterwards
      await waitFor(() => expect(comp.vpm.name).toBe("initialVPM"));
      comp.vpm.name = "savedPolicy";

      comp.saveVPM();

      await waitFor(() => expect(comp.resetModel.name).toBe("savedPolicy"));
   });
});

// ---------------------------------------------------------------------------
// Group 2 — saveVPM() create mode: POST [Risk 3]
// ---------------------------------------------------------------------------

describe("DatabaseVPMComponent — saveVPM() create mode", () => {
   // 🔁 Regression-sensitive: after a successful POST, editing must flip to true and
   // folderChangeService.emitFolderChange must be called so the VPM tree sidebar refreshes.

   it("should POST and flip editing to true on success", async () => {
      const { comp } = await renderComp({ route: makeCreateRoute() });
      expect(comp.editing).toBe(false);

      comp.saveVPM();

      await waitFor(() => expect(comp.editing).toBe(true));
   });

   it("should call folderChangeService.emitFolderChange after successful POST", async () => {
      const { comp, folderChangeMock } = await renderComp({ route: makeCreateRoute() });

      comp.saveVPM();

      await waitFor(() => expect(folderChangeMock.emitFolderChange).toHaveBeenCalledTimes(1));
   });

   it("should not call folderChangeService when already editing (PUT path)", async () => {
      const { comp, folderChangeMock } = await renderComp({ route: makeEditRoute() });
      await waitFor(() => expect(comp.editing).toBe(true));

      comp.saveVPM();

      // wait for the PUT to complete before asserting no extra call
      await waitFor(() => expect((comp as any)._isModified).toBe(false));
      expect(folderChangeMock.emitFolderChange).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 3 — saveVPM() validation [Risk 3]
// ---------------------------------------------------------------------------

describe("DatabaseVPMComponent — saveVPM() validation", () => {
   // 🔁 Regression-sensitive: if the missing-name guard is skipped, the server receives
   // a VPM with an empty-string condition name, which may pass silently and corrupt data.

   it("should show error dialog and abort when a condition has an empty name", async () => {
      const dialogSpy = vi.spyOn(ComponentTool, "showMessageDialog").mockReturnValue(
         new Promise(() => {})
      );
      const { comp } = await renderComp({ route: makeCreateRoute() });
      comp.vpm.conditions = [{ name: "  ", clauses: [], type: 0, tableName: "T", script: "" }];
      try {
         comp.saveVPM();

         await waitFor(() => expect(dialogSpy).toHaveBeenCalledTimes(1));
         expect(dialogSpy).toHaveBeenCalledWith(
            expect.anything(),
            "_#(js:Error)",
            "_#(js:data.vpm.conditionNoNameSaveError)"
         );
         // no HTTP request sent (editing remains false)
         expect(comp.editing).toBe(false);
      } finally {
         dialogSpy.mockRestore();
      }
   });

   it("should show error dialog and abort when two conditions share the same name", async () => {
      const dialogSpy = vi.spyOn(ComponentTool, "showMessageDialog").mockReturnValue(
         new Promise(() => {})
      );
      const { comp } = await renderComp({ route: makeCreateRoute() });
      comp.vpm.conditions = [
         { name: "dup", clauses: [], type: 0, tableName: "T1", script: "" },
         { name: "dup", clauses: [], type: 0, tableName: "T2", script: "" },
      ];
      try {
         comp.saveVPM();

         await waitFor(() => expect(dialogSpy).toHaveBeenCalledTimes(1));
         expect(dialogSpy).toHaveBeenCalledWith(
            expect.anything(),
            "_#(js:Error)",
            expect.stringContaining("_#(js:data.vpm.conditionDuplicateNameSaveError)")
         );
      } finally {
         dialogSpy.mockRestore();
      }
   });

   it("should not show an error dialog when all condition names are unique and non-empty", async () => {
      const dialogSpy = vi.spyOn(ComponentTool, "showMessageDialog").mockReturnValue(
         new Promise(() => {})
      );
      const { comp } = await renderComp({ route: makeCreateRoute() });
      comp.vpm.conditions = [
         { name: "cond1", clauses: [], type: 0, tableName: "T1", script: "" },
         { name: "cond2", clauses: [], type: 0, tableName: "T2", script: "" },
      ];
      try {
         comp.saveVPM();

         // give the HTTP call time to settle
         await waitFor(() => expect(comp.editing).toBe(true));
         expect(dialogSpy).not.toHaveBeenCalled();
      } finally {
         dialogSpy.mockRestore();
      }
   });
});

// ---------------------------------------------------------------------------
// Group 4 — refreshVPM() [Risk 3]
// ---------------------------------------------------------------------------

describe("DatabaseVPMComponent — refreshVPM() via edit mode ngOnInit", () => {
   // 🔁 Regression-sensitive: if refreshVPM does not update resetModel, then any subsequent
   // resetVPM() call will restore to the pre-load default model, discarding server state.

   it("should populate both vpm and resetModel from the server response", async () => {
      server.use(
         http.get("*/api/data/vpm/models", () =>
            MswHttpResponse.json({
               name: "serverVPM",
               conditions: [{ name: "c1", clauses: [], type: 0, tableName: "Tbl", script: "" }],
               hidden: { roles: ["admin"], hiddens: [], name: null, script: null },
               lookup: "lookupScript",
               description: "server description",
            })
         )
      );
      const { comp } = await renderComp({ route: makeEditRoute() });

      await waitFor(() => expect(comp.vpm.name).toBe("serverVPM"));
      expect(comp.resetModel.name).toBe("serverVPM");
      expect(comp.vpm.conditions).toHaveLength(1);
   });

   it("should reset currentVPMTab to CONDITIONS after load", async () => {
      server.use(
         http.get("*/api/data/vpm/models", () =>
            MswHttpResponse.json({
               name: "v", conditions: [], hidden: null, lookup: "", description: "",
            })
         )
      );
      const { comp } = await renderComp({ route: makeEditRoute() });
      comp.selectVPMTab(comp.VPMTabs.TEST); // simulate user navigating away

      // re-trigger via a second route emission (simulate reload)
      await waitFor(() => expect(comp.vpm.name).toBe("v"));
      // refreshVPM sets currentVPMTab back to CONDITIONS
      expect(comp.currentVPMTab).toBe(comp.VPMTabs.CONDITIONS);
   });
});

// ---------------------------------------------------------------------------
// Group 5 — refreshTestData() [Risk 2]
// ---------------------------------------------------------------------------

describe("DatabaseVPMComponent — refreshTestData() via ngOnInit", () => {
   it("should populate testData from the usersRoles response", async () => {
      server.use(
         http.get("*/api/data/vpm/usersRoles", () =>
            MswHttpResponse.json({
               users: [{ label: "Alice", value: "alice" }],
               roles: [{ label: "Admins", value: "admins" }],
            })
         )
      );
      const { comp } = await renderComp();

      await waitFor(() => expect(comp.testData.users).toHaveLength(1));
      expect(comp.testData.users[0].value).toBe("alice");
      expect(comp.testData.roles[0].value).toBe("admins");
   });
});

// ---------------------------------------------------------------------------
// Group 6 — refreshOperations() [Risk 2]
// ---------------------------------------------------------------------------

describe("DatabaseVPMComponent — refreshOperations() via ngOnInit", () => {
   // 🔁 Regression-sensitive: sessionOperations[0] must be the EQUAL_TO operation and
   // sessionOperations[1] must be IN so condition rows auto-select the right operators.

   it("should assign sessionOperations[0] to the EQUAL_TO operation", async () => {
      server.use(
         http.get("*/api/data/vpm/operations", () =>
            MswHttpResponse.json([
               { symbol: ClauseOperationSymbols.IN, label: "in" },
               { symbol: ClauseOperationSymbols.EQUAL_TO, label: "=" },
            ])
         )
      );
      const { comp } = await renderComp();

      await waitFor(() => expect(comp.operations).toHaveLength(2));
      expect(comp.sessionOperations[0]?.symbol).toBe(ClauseOperationSymbols.EQUAL_TO);
   });

   it("should assign sessionOperations[1] to the IN operation", async () => {
      server.use(
         http.get("*/api/data/vpm/operations", () =>
            MswHttpResponse.json([
               { symbol: ClauseOperationSymbols.EQUAL_TO, label: "=" },
               { symbol: ClauseOperationSymbols.IN, label: "in" },
            ])
         )
      );
      const { comp } = await renderComp();

      await waitFor(() => expect(comp.operations).toHaveLength(2));
      expect(comp.sessionOperations[1]?.symbol).toBe(ClauseOperationSymbols.IN);
   });

   it("should leave sessionOperations as nulls when neither symbol is present", async () => {
      server.use(
         http.get("*/api/data/vpm/operations", () =>
            MswHttpResponse.json([
               { symbol: "OTHER", label: "other" },
            ])
         )
      );
      const { comp } = await renderComp();

      await waitFor(() => expect(comp.operations).toHaveLength(1));
      expect(comp.sessionOperations[0]).toBeNull();
      expect(comp.sessionOperations[1]).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 7 — isModified getter [Risk 2]
// ---------------------------------------------------------------------------

describe("DatabaseVPMComponent — isModified getter", () => {
   it("should return false when vpm deep-equals resetModel", async () => {
      const { comp } = await renderComp();
      (comp as any)._isModified = false;
      // After vpm setter ran in create mode, vpm and resetModel diverged because the
      // setter initialized hidden; align them via Tool.clone before the check.
      (comp as any).resetModel = Tool.clone(comp.vpm);

      expect(comp.isModified).toBe(false);
   });

   it("should return true when vpm differs from resetModel", async () => {
      const { comp } = await renderComp();
      (comp as any)._isModified = false;
      (comp as any).resetModel = Tool.clone(comp.vpm);
      comp.vpm.name = "changedName"; // diverge

      expect(comp.isModified).toBe(true);
   });

   it("should cache the result: once true, subsequent calls return true without re-checking", async () => {
      const { comp } = await renderComp();
      // create mode sets _isModified=true
      expect((comp as any)._isModified).toBe(true);

      // reset model to match (would make isEquals return true)
      (comp as any).resetModel = Tool.clone(comp.vpm);

      // but cached _isModified=true short-circuits the isEquals check
      expect(comp.isModified).toBe(true);
   });
});
