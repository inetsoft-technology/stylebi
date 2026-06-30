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
 * DatabaseVPMBrowserComponent — Pass 1 (Interaction / lifecycle / user flows)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 1]  — sortOptionsChanged: re-sorts models[] by current sortOptions
 *   Group 2 [Risk 1]  — toggleSelectionState: flips selectionOn boolean (both directions)
 *   Group 3 [Risk 2]  — editModel: router.navigate uses model.path, not model.name/id
 *   Group 4 [Risk 3]  — addModel: dialog configured correctly; commit triggers router.navigate
 *                        with databaseName+"/"+result.name and {create: true}
 *   Group 5 [Risk 3]  — renameModel: same name+desc → no HTTP; different name → PUT → success
 *                        notification + model.name update + folderChange; PUT error → danger
 *   Group 6 [Risk 2]  — setShowDetailsItem: toggles between item and null
 *   Group 7 [Risk 2]  — openTreeContextmenu + createActions: dropdown opened; actions contain
 *                        rename/delete/details entries with correct visibility functions
 *
 * Confirmed bugs (it.fails): none
 *
 * Out of scope this pass:
 *   editable, deletable, currentSearchFolderLabel, search(), reSearch(), clearSearch(),
 *   refreshModels(), deleteModel(), deleteSelected() — async/risk → Pass 2.
 *   ngOnInit (route subscription), ngOnDestroy (cleanup) — exercised in P2 via refreshModels.
 */

import { waitFor } from "@testing-library/angular";
import { http, HttpResponse as MswHttpResponse } from "msw";

import { server } from "@test-mocks/server";
import { SortTypes } from "../../../../../../../../../shared/util/sort/sort-types";
import { ComponentTool } from "../../../../../../common/util/component-tool";
import { MOCK_BROWSE_RESPONSE, MOCK_VPM, renderComp } from "./database-vpm-browser.component.test-helpers";

// ── Global lifecycle ─────────────────────────────────────────────────────────

afterEach(() => {
   vi.restoreAllMocks();
});

// ── Group 1 — sortOptionsChanged [Risk 1] ────────────────────────────────────

describe("DatabaseVPMBrowserComponent — sortOptionsChanged", () => {
   it("should re-sort models[] by the current sortOptions", async () => {
      const { comp } = await renderComp();
      const vpmA = { ...MOCK_VPM, name: "Zebra" };
      const vpmB = { ...MOCK_VPM, name: "Apple" };
      comp.models = [vpmA, vpmB];
      comp.sortOptions = { keys: ["name"], type: SortTypes.ASCENDING, caseSensitive: false };

      comp.sortOptionsChanged();

      expect(comp.models[0].name).toBe("Apple");
      expect(comp.models[1].name).toBe("Zebra");
   });

   it("should sort descending when SortTypes.DESCENDING is set", async () => {
      const { comp } = await renderComp();
      const vpmA = { ...MOCK_VPM, name: "Apple" };
      const vpmB = { ...MOCK_VPM, name: "Zebra" };
      comp.models = [vpmA, vpmB];
      comp.sortOptions = { keys: ["name"], type: SortTypes.DESCENDING, caseSensitive: false };

      comp.sortOptionsChanged();

      expect(comp.models[0].name).toBe("Zebra");
      expect(comp.models[1].name).toBe("Apple");
   });
});

// ── Group 2 — toggleSelectionState [Risk 1] ──────────────────────────────────

describe("DatabaseVPMBrowserComponent — toggleSelectionState", () => {
   it("should flip selectionOn from false to true", async () => {
      const { comp } = await renderComp();
      expect(comp.selectionOn).toBe(false);

      comp.toggleSelectionState();

      expect(comp.selectionOn).toBe(true);
   });

   it("should flip selectionOn from true back to false", async () => {
      const { comp } = await renderComp();
      comp.selectionOn = true;

      comp.toggleSelectionState();

      expect(comp.selectionOn).toBe(false);
   });
});

// ── Group 3 — editModel [Risk 2] ─────────────────────────────────────────────

describe("DatabaseVPMBrowserComponent — editModel", () => {
   // 🔁 Regression-sensitive: must pass model.path — not model.name or model.id —
   // as the route segment so the server can locate the VPM in nested database paths.
   it("should navigate to the vpm page using model.path", async () => {
      const { comp, routerMock } = await renderComp();
      const vpm = { ...MOCK_VPM, path: "SalesDB/MyVPM", name: "MyVPM" };

      comp.editModel(vpm);

      expect(routerMock.navigate).toHaveBeenCalledWith(
         ["/portal/tab/data/datasources/database/vpm", "SalesDB/MyVPM"],
         expect.objectContaining({ relativeTo: expect.anything() }),
      );
   });
});

// ── Group 4 — addModel [Risk 3] ──────────────────────────────────────────────

describe("DatabaseVPMBrowserComponent — addModel", () => {
   // 🔁 Regression-sensitive: after dialog commit, navigate must include {create: true} and
   // the full "databaseName/result.name" path so the VPM edit page creates a new model.

   it("should open InputNameDescDialog with correct title and label", async () => {
      const { comp } = await renderComp();
      const dialogStub: any = {};
      const showDialogSpy = vi.spyOn(ComponentTool, "showDialog").mockImplementation((_modal, _type, _onCommit) => {
         return dialogStub;
      });
      try {
         comp.addModel();

         expect(dialogStub.title).toBe("_#(js:data.datasources.newVPM)");
         expect(dialogStub.label).toBe("_#(js:data.vpm.modelName)");
         expect(dialogStub.helpLinkKey).toBe("CreateVPM");
      } finally {
         showDialogSpy.mockRestore();
      }
   });

   it("should navigate with databaseName+name and create:true when dialog commits", async () => {
      const { comp, routerMock } = await renderComp();
      let capturedOnCommit: (result: any) => void;
      const showDialogSpy = vi.spyOn(ComponentTool, "showDialog").mockImplementation((_modal, _type, onCommit) => {
         capturedOnCommit = onCommit;
         return {};
      });
      try {
         comp.addModel();
         capturedOnCommit!({ name: "NewVPM", description: "My desc" });

         expect(routerMock.navigate).toHaveBeenCalledWith(
            ["/portal/tab/data/datasources/database/vpm", "SalesDB/NewVPM", { create: true, desc: "My desc" }],
            expect.objectContaining({ relativeTo: expect.anything() }),
         );
      } finally {
         showDialogSpy.mockRestore();
      }
   });
});

// ── Group 5 — renameModel [Risk 3] ───────────────────────────────────────────

describe("DatabaseVPMBrowserComponent — renameModel", () => {
   // 🔁 Regression-sensitive: must call PUT with a RenameModelEvent containing the correct
   // database, oldName, and newName — any field mismatch silently renames the wrong model.

   it("should not make an HTTP call when name and description are unchanged", async () => {
      const { comp } = await renderComp();
      let capturedOnCommit: (result: any) => void;
      const showDialogSpy = vi.spyOn(ComponentTool, "showDialog").mockImplementation((_modal, _type, onCommit) => {
         capturedOnCommit = onCommit;
         return {};
      });
      const vpm = { ...MOCK_VPM, name: "VPM1", description: "Test VPM" };
      try {
         const putSpy = vi.fn();
         server.use(http.put("*/api/data/vpm/rename", () => { putSpy(); return MswHttpResponse.json({}); }));

         comp.renameModel(vpm);
         capturedOnCommit!({ name: "VPM1", description: "Test VPM" }); // same → no change

         expect(putSpy).not.toHaveBeenCalled();
      } finally {
         showDialogSpy.mockRestore();
      }
   });

   it("should PUT the rename event and show success notification when name changes", async () => {
      const { comp, folderChangeMock } = await renderComp();
      let capturedOnCommit: (result: any) => void;
      const showDialogSpy = vi.spyOn(ComponentTool, "showDialog").mockImplementation((_modal, _type, onCommit) => {
         capturedOnCommit = onCommit;
         return {};
      });
      const vpm = { ...MOCK_VPM, name: "VPM1", description: "Test VPM" };
      try {
         comp.renameModel(vpm);
         capturedOnCommit!({ name: "VPM1_renamed", description: "Test VPM" });

         await waitFor(() =>
            expect((comp.notifications as any).success).toHaveBeenCalledWith(
               "_#(js:data.vpm.renameModelSuccess)",
            ),
         );
         expect(vpm.name).toBe("VPM1_renamed");
         expect(folderChangeMock.emitFolderChange).toHaveBeenCalledTimes(1);
      } finally {
         showDialogSpy.mockRestore();
      }
   });

   it("should show danger notification when the PUT rename request fails", async () => {
      server.use(
         http.put("*/api/data/vpm/rename", () =>
            MswHttpResponse.json({ error: "Server error" }, { status: 500 }),
         ),
      );
      const { comp } = await renderComp();
      let capturedOnCommit: (result: any) => void;
      const showDialogSpy = vi.spyOn(ComponentTool, "showDialog").mockImplementation((_modal, _type, onCommit) => {
         capturedOnCommit = onCommit;
         return {};
      });
      try {
         comp.renameModel({ ...MOCK_VPM });
         capturedOnCommit!({ name: "FailVPM", description: "Test VPM" });

         await waitFor(() =>
            expect((comp.notifications as any).danger).toHaveBeenCalledWith(
               "_#(js:data.vpm.modelNameDuplicate)",
            ),
         );
      } finally {
         showDialogSpy.mockRestore();
      }
   });

   it("should return of(false) from hasDuplicateCheck without HTTP when name is unchanged", async () => {
      const { comp } = await renderComp();
      let capturedDialogStub: any = {};
      const showDialogSpy = vi.spyOn(ComponentTool, "showDialog").mockImplementation((_modal, _type, _onCommit) => {
         return capturedDialogStub;
      });
      const vpm = { ...MOCK_VPM, name: "VPM1" };
      try {
         comp.renameModel(vpm);

         const checkFn = capturedDialogStub.hasDuplicateCheck as (v: string) => import("rxjs").Observable<boolean>;
         let emittedValue: boolean | undefined;
         checkFn("VPM1").subscribe((v) => (emittedValue = v));

         expect(emittedValue).toBe(false); // synchronous of(false) — no HTTP
      } finally {
         showDialogSpy.mockRestore();
      }
   });
});

// ── Group 6 — setShowDetailsItem [Risk 2] ────────────────────────────────────

describe("DatabaseVPMBrowserComponent — setShowDetailsItem", () => {
   it("should set showDetailsItem when a different item is passed", async () => {
      const { comp } = await renderComp();
      const vpm = { ...MOCK_VPM };

      comp.setShowDetailsItem(vpm);

      expect(comp.showDetailsItem).toBe(vpm);
   });

   it("should clear showDetailsItem when the same item is passed again", async () => {
      const { comp } = await renderComp();
      const vpm = { ...MOCK_VPM };
      comp.showDetailsItem = vpm;

      comp.setShowDetailsItem(vpm);

      expect(comp.showDetailsItem).toBeNull();
   });
});

// ── Group 7 — openTreeContextmenu + createActions [Risk 2] ───────────────────

describe("DatabaseVPMBrowserComponent — openTreeContextmenu + createActions", () => {
   it("should open the dropdown service with the contextmenu item's MouseEvent position", async () => {
      const { comp, dropdownServiceMock } = await renderComp();
      const mouseEvent = new MouseEvent("contextmenu", { clientX: 120, clientY: 240, bubbles: false });
      const vpm = { ...MOCK_VPM };

      comp.openTreeContextmenu([vpm, mouseEvent]);

      expect(dropdownServiceMock.open).toHaveBeenCalledTimes(1);
      const [, opts] = dropdownServiceMock.open.mock.calls[0];
      expect(opts.position.x).toBe(121); // clientX + 1
      expect(opts.position.y).toBe(240);
      expect(opts.contextmenu).toBe(true);
   });

   it("should populate contextmenu actions with rename, delete, and details entries", async () => {
      const { comp, dropdownInstanceMock } = await renderComp();
      comp.model = { ...MOCK_BROWSE_RESPONSE } as any; // sets editable/deletable via getter
      const vpm = { ...MOCK_VPM };

      comp.openTreeContextmenu([vpm, new MouseEvent("contextmenu")]);

      const groups = dropdownInstanceMock.actions;
      expect(groups.length).toBeGreaterThan(0);
      const actions = groups[0].actions;
      expect(actions.length).toBeGreaterThanOrEqual(3);
      const renameAction = actions.find((a: any) => a.label().includes("Rename"));
      const deleteAction = actions.find((a: any) => a.label().includes("Delete"));
      const detailsAction = actions.find((a: any) => a.label().includes("Details"));
      expect(renameAction?.label()).toContain("Rename");
      expect(deleteAction?.label()).toContain("Delete");
      expect(detailsAction?.label()).toContain("Details");
   });

   it("should set visible=editable for rename and visible=deletable for delete actions", async () => {
      const { comp, dropdownInstanceMock } = await renderComp();
      // model null → editable=false, deletable=false
      comp.model = null;

      comp.openTreeContextmenu([MOCK_VPM, new MouseEvent("contextmenu")]);

      const actions = dropdownInstanceMock.actions[0].actions;
      const renameAction = actions.find((a: any) => a.label().includes("Rename"));
      const deleteAction = actions.find((a: any) => a.label().includes("Delete"));
      const detailsAction = actions.find((a: any) => a.label().includes("Details"));
      expect(renameAction.visible()).toBe(false); // editable=false
      expect(deleteAction.visible()).toBe(false); // deletable=false
      expect(detailsAction.visible()).toBe(true); // always true
   });
});
