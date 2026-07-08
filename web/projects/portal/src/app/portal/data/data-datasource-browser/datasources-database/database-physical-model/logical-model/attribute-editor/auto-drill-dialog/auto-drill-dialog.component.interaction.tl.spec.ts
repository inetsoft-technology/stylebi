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
 * AutoDrillDialog — Pass 1 (interaction / HTTP / lifecycle)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — ngOnInit: loads repository tree via HTTP; initialises editIndex
 *                       and selectedDrills from model on startup
 *   Group 2 [Risk 3] — addDrill(): pushes path with correct defaults, selects it,
 *                       names it via getNewDrillName()
 *   Group 3 [Risk 3] — ok(): blocks and shows message dialog on empty link or duplicate
 *                       names; emits onCommit when all paths are valid
 *   Group 4 [Risk 2] — getNewDrillName(): name-sequence logic — empty list, no conflict,
 *                       direct conflict, gap skipping
 *   Group 5 [Risk 2] — changeLinkType(): clears link, reinitialises form, reloads tree
 *                       only for non-web link types
 *   Group 6 [Risk 1] — drillLabel getter, getDisplayParam(), changeLinkTarget()
 *
 * Confirmed bugs (it.fails):
 *   getNewDrillName() returns "New Drill0" instead of "New Drill" when all existing paths
 *   are unrelated (none match the "New Drill*" prefix). Root cause: existIndexs stays
 *   empty, existIndexs.length=0, and the final return is "New Drill" + 0. The missing
 *   guard is: if existIndexs is empty, return "New Drill" directly.
 *
 * Out of scope this pass:
 *   ok() null.trim() crash: `!path.link && "" == path.link.trim()` throws TypeError when
 *     path.link is null; reachable only via external mutation, not any in-scope user flow.
 *   openSelectWorksheetDialog / openParameterDialog — modal interaction (showDialog
 *     callback wiring) deferred to Pass 2 alongside other destructive-action flows
 *   selectDrill multi-select (ctrlKey/shiftKey) — template interaction test
 *   selectNode — depends on live NgbDropdown interaction
 */

import { waitFor } from "@testing-library/angular";
import { http, HttpResponse as MswHttpResponse } from "msw";
import { XSchema } from "../../../../../../../../common/data/xschema";
import { ComponentTool } from "../../../../../../../../common/util/component-tool";

import { server } from "@test-mocks/server";
import {
   MODAL_MOCK,
   makeDrillPath,
   makeModel,
   renderComp,
} from "./data-auto-drill-dialog.test-helpers";

// ---------------------------------------------------------------------------
// Global lifecycle
// ---------------------------------------------------------------------------

beforeEach(() => {
   MODAL_MOCK.open.mockClear();
});

afterEach(() => {
   vi.restoreAllMocks();
});

// ---------------------------------------------------------------------------
// Group 1 — ngOnInit: HTTP tree loading + editIndex initialisation [Risk 3]
// ---------------------------------------------------------------------------

describe("AutoDrillDialog — ngOnInit: repository tree and editIndex", () => {
   // 🔁 Regression-sensitive: if loadRepositoryTree stops firing, the viewsheet picker
   // renders empty — users cannot select a drill target at all.
   it("should load repository tree via HTTP on init and populate assetTreeRoot", async () => {
      server.use(
         http.get("*/api/composer/vs/hyperlink-dialog-model/tree", () =>
            MswHttpResponse.json({
               label: "MyRoot",
               leaf: false,
               expanded: false,
               data: { path: "/" },
               children: [],
            })
         )
      );
      const { comp } = await renderComp({ model: makeModel([]) });
      await waitFor(() => expect(comp.assetTreeRoot.label).toBe("MyRoot"));
      expect(comp.assetTreeRoot.expanded).toBe(true); // component forces expanded=true on load
   });

   // 🔁 Regression-sensitive: editIndex=0 is required for the form to bind to the first drill;
   // if it stays -1, the form is empty and the user cannot edit any drill on open.
   it("should set editIndex=0 and selectedDrills=[0] when model has paths", async () => {
      const { comp } = await renderComp({ model: makeModel([makeDrillPath()]) });
      expect(comp.editIndex).toBe(0);
      expect(comp.selectedDrills).toEqual([0]);
   });

   it("should leave editIndex at -1 when model has no paths", async () => {
      const { comp } = await renderComp({ model: makeModel([]) });
      expect(comp.editIndex).toBe(-1);
      expect(comp.selectedDrills).toEqual([]);
   });
});

// ---------------------------------------------------------------------------
// Group 2 — addDrill(): new path defaults + selection + naming [Risk 3]
// ---------------------------------------------------------------------------

describe("AutoDrillDialog — addDrill()", () => {
   // 🔁 Regression-sensitive: default linkType must be VIEWSHEET_LINK (8); if it defaults
   // to WEB_LINK the user is forced to switch type for every new drill.
   it("should push a new path with correct defaults", async () => {
      const { comp } = await renderComp({ model: makeModel([]) });
      comp.addDrill();
      expect(comp.autoDrillModel.paths).toHaveLength(1);
      expect(comp.autoDrillModel.paths[0]).toMatchObject({
         link: "",
         targetFrame: "",
         params: [],
         passParams: true,
         disablePrompting: false,
         linkType: 8, // VIEWSHEET_LINK
         query: null,
      });
   });

   it("should select the newly added drill (editIndex and selectedDrills)", async () => {
      const { comp } = await renderComp({ model: makeModel([makeDrillPath()]) });
      comp.addDrill();
      expect(comp.editIndex).toBe(1);
      expect(comp.selectedDrills).toEqual([1]);
   });

   it("should use getNewDrillName() to generate the name", async () => {
      const { comp } = await renderComp({
         model: makeModel([makeDrillPath({ name: "New Drill" })]),
      });
      comp.addDrill();
      expect(comp.autoDrillModel.paths[1].name).toBe("New Drill1");
   });
});

// ---------------------------------------------------------------------------
// Group 3 — ok(): validation guards + successful emit [Risk 3]
// ---------------------------------------------------------------------------

describe("AutoDrillDialog — ok() validation", () => {
   // 🔁 Regression-sensitive: the empty-link guard must fire BEFORE emit; losing it
   // means a drill with no target link reaches the server and causes a silent failure.
   it("should show message dialog and not emit when a path has an empty link", async () => {
      const msgSpy = vi.spyOn(ComponentTool, "showMessageDialog")
         .mockImplementation(() => undefined as any);
      const { comp } = await renderComp({
         model: makeModel([makeDrillPath({ link: "" })]),
      });
      const committed: any[] = [];
      comp.onCommit.subscribe(v => committed.push(v));

      comp.ok();

      expect(msgSpy).toHaveBeenCalledTimes(1);
      expect(committed).toHaveLength(0);
   });

   it("should show message dialog and not emit when paths have duplicate names", async () => {
      const msgSpy = vi.spyOn(ComponentTool, "showMessageDialog")
         .mockImplementation(() => undefined as any);
      const paths = [
         makeDrillPath({ name: "DrillA", link: "https://a.com" }),
         makeDrillPath({ name: "DrillA", link: "https://b.com" }),
      ];
      const { comp } = await renderComp({ model: makeModel(paths) });
      const committed: any[] = [];
      comp.onCommit.subscribe(v => committed.push(v));

      comp.ok();

      expect(msgSpy).toHaveBeenCalledTimes(1);
      expect(committed).toHaveLength(0);
   });

   it("should emit onCommit with the model when all paths are valid", async () => {
      const paths = [
         makeDrillPath({ name: "DrillA", link: "https://a.com" }),
         makeDrillPath({ name: "DrillB", link: "https://b.com" }),
      ];
      const { comp } = await renderComp({ model: makeModel(paths) });
      const committed: any[] = [];
      comp.onCommit.subscribe(v => committed.push(v));

      comp.ok();

      expect(committed).toHaveLength(1);
      expect(committed[0].paths).toHaveLength(2);
   });
});

// ---------------------------------------------------------------------------
// Group 4 — getNewDrillName(): name-sequence logic [Risk 2]
// ---------------------------------------------------------------------------

describe("AutoDrillDialog — getNewDrillName()", () => {
   it("should return 'New Drill' when no paths exist", async () => {
      const { comp } = await renderComp({ model: makeModel([]) });
      expect(comp.getNewDrillName()).toBe("New Drill");
   });

   // Fixed Issue #75589: the fallback return now special-cases an empty existIndexs
   // (`existIndexs.length == 0 ? newNamePre : newNamePre + existIndexs.length`),
   // matching the same index-0 special-casing already used by the gap-search loop above it.
   it("should return 'New Drill' when existing paths use unrelated names", async () => {
      const { comp } = await renderComp({
         model: makeModel([makeDrillPath({ name: "My Custom Drill" })]),
      });
      expect(comp.getNewDrillName()).toBe("New Drill");
   });

   it("should return 'New Drill1' when 'New Drill' is already taken", async () => {
      const { comp } = await renderComp({
         model: makeModel([makeDrillPath({ name: "New Drill" })]),
      });
      expect(comp.getNewDrillName()).toBe("New Drill1");
   });

   it("should skip used indices and return the first gap", async () => {
      const paths = [
         makeDrillPath({ name: "New Drill" }),  // index 0 taken
         makeDrillPath({ name: "New Drill1" }), // index 1 taken
         makeDrillPath({ name: "New Drill3" }), // index 3 taken → gap at 2
      ];
      const { comp } = await renderComp({ model: makeModel(paths) });
      expect(comp.getNewDrillName()).toBe("New Drill2");
   });
});

// ---------------------------------------------------------------------------
// Group 5 — changeLinkType(): clears link, reinits form, reloads tree [Risk 2]
// ---------------------------------------------------------------------------

describe("AutoDrillDialog — changeLinkType()", () => {
   // 🔁 Regression-sensitive: if the link is not cleared on type-switch, the old URL
   // remains in the field and the user submits the wrong link for the new type.
   it("should clear editDrill.link and reset the link form control", async () => {
      const path = makeDrillPath({ link: "https://example.com", linkType: 1 });
      const { comp } = await renderComp({ model: makeModel([path]) });

      comp.changeLinkType();

      expect(comp.autoDrillModel.paths[0].link).toBe("");
      expect(comp.linkControl.value).toBe("");
   });

   it("should reinitialise the form group so nameControl still reflects the drill name", async () => {
      const path = makeDrillPath({ name: "My Drill", link: "https://x.com", linkType: 1 });
      const { comp } = await renderComp({ model: makeModel([path]) });

      comp.changeLinkType();

      expect(comp.nameControl.value).toBe("My Drill");
   });

   it("should reload the repository tree when switching to a non-web link type", async () => {
      let treeCalls = 0;
      server.use(
         http.get("*/api/composer/vs/hyperlink-dialog-model/tree", () => {
            treeCalls++;
            return MswHttpResponse.json({
               label: "Root", leaf: false, expanded: false, data: { path: "/" }, children: [],
            });
         })
      );
      const path = makeDrillPath({ link: "", linkType: 8 }); // VIEWSHEET_LINK
      const { comp } = await renderComp({ model: makeModel([path]) });
      await waitFor(() => expect(treeCalls).toBeGreaterThan(0)); // wait for ngOnInit call
      treeCalls = 0;

      comp.changeLinkType(); // linkType is still 8 → should reload

      await waitFor(() => expect(treeCalls).toBeGreaterThan(0));
   });
});

// ---------------------------------------------------------------------------
// Group 6 — drillLabel, getDisplayParam(), changeLinkTarget() [Risk 1]
// ---------------------------------------------------------------------------

describe("AutoDrillDialog — drillLabel, getDisplayParam, changeLinkTarget", () => {
   it("drillLabel should return the link string directly for WEB_LINK type", async () => {
      const path = makeDrillPath({ link: "https://example.com", linkType: 1 });
      const { comp } = await renderComp({ model: makeModel([path]) });
      expect(comp.drillLabel).toBe("https://example.com");
   });

   it("drillLabel should return empty string for VIEWSHEET_LINK with no link set", async () => {
      const path = makeDrillPath({ link: "", linkType: 8 });
      const { comp } = await renderComp({ model: makeModel([path]) });
      expect(comp.drillLabel).toBe("");
   });

   it("getDisplayParam should replace 'T' with space for TIME_INSTANT type", async () => {
      const { comp } = await renderComp();
      const param = { name: "start", field: "2024-01-01T12:00:00", type: XSchema.TIME_INSTANT };
      expect(comp.getDisplayParam(param)).toBe("start:[2024-01-01 12:00:00]");
   });

   it("getDisplayParam should leave field unchanged for non-TIME_INSTANT types", async () => {
      const { comp } = await renderComp();
      const param = { name: "col", field: "category", type: "string" };
      expect(comp.getDisplayParam(param)).toBe("col:[category]");
   });

   it("changeLinkTarget should set targetFrame to empty string when val is true", async () => {
      const path = makeDrillPath({ targetFrame: "_blank" });
      const { comp } = await renderComp({ model: makeModel([path]) });
      comp.changeLinkTarget(true);
      expect(comp.autoDrillModel.paths[0].targetFrame).toBe("");
   });

   it("changeLinkTarget should not modify targetFrame when val is false", async () => {
      const path = makeDrillPath({ targetFrame: "_blank" });
      const { comp } = await renderComp({ model: makeModel([path]) });
      comp.changeLinkTarget(false);
      expect(comp.autoDrillModel.paths[0].targetFrame).toBe("_blank");
   });
});
