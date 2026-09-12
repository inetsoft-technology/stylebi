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
 * AutoDrillDialog — Pass 2 (async / destructive / state-consistency)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — deleteDrill(false): confirm → splice + editIndex adjustment;
 *                       cancel → no-op; last-item deletion moves editIndex to new tail
 *   Group 2 [Risk 3] — deleteDrill(true): confirm → clears all paths + editIndex=-1;
 *                       cancel → paths unchanged
 *   Group 3 [Risk 3] — removeAllParameters(): confirm → empties params array;
 *                       cancel → params unchanged
 *   Group 4 [Risk 2] — moveDrillDown() / moveDrillUp(): swaps adjacent paths and tracks
 *                       editIndex; boundary guards prevent out-of-range moves
 *   Group 5 [Risk 2] — getDrillWorksheet(): null query, alias present, alias absent
 *   Group 6 [Risk 1] — cancel(), getFirstErrorMessage()
 *
 * Confirmed bugs (it.fails): none
 *
 * Out of scope this pass:
 *   selectedAssetNode — delegates to repositoryTree.selectedNode; stub always returns null
 *   clearSelectedNode — private; tested transitively through selectDrill and changeLinkType
 *   getAssetNodeIdentifier — private; tested transitively through selectNode
 */

import { waitFor } from "@testing-library/angular";
import { Validators } from "@angular/forms";
import { ComponentTool } from "../../../../../../../../common/util/component-tool";

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
// Group 1 — deleteDrill(false): single-drill deletion [Risk 3]
// ---------------------------------------------------------------------------

describe("AutoDrillDialog — deleteDrill(false): single drill", () => {
   // 🔁 Regression-sensitive: the splice must use selectedDrills (which may be a
   // multi-selection) and work backward from the last index; wrong order corrupts the array.
   it("should splice the selected drill and leave remaining paths intact on confirm", async () => {
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");
      const paths = [
         makeDrillPath({ name: "Drill A", link: "https://a.com" }),
         makeDrillPath({ name: "Drill B", link: "https://b.com" }),
      ];
      const { comp } = await renderComp({ model: makeModel(paths) });
      // ngOnInit selects index 0

      comp.deleteDrill(false);

      await waitFor(() => expect(comp.autoDrillModel.paths).toHaveLength(1));
      expect(comp.autoDrillModel.paths[0].name).toBe("Drill B");
   });

   it("should leave paths unchanged when the user cancels the confirm dialog", async () => {
      const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog")
         .mockResolvedValue("cancel");
      const paths = [makeDrillPath({ name: "Keep Me" })];
      const { comp } = await renderComp({ model: makeModel(paths) });

      comp.deleteDrill(false);

      await waitFor(() => expect(confirmSpy).toHaveBeenCalled());
      expect(comp.autoDrillModel.paths).toHaveLength(1);
   });

   it("should move editIndex to the new last item when the last path is deleted", async () => {
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");
      const paths = [
         makeDrillPath({ name: "Drill A" }),
         makeDrillPath({ name: "Drill B" }),
         makeDrillPath({ name: "Drill C" }),
      ];
      const { comp } = await renderComp({ model: makeModel(paths) });
      comp.selectDrill(null, 2); // select the last item

      comp.deleteDrill(false);

      await waitFor(() => expect(comp.autoDrillModel.paths).toHaveLength(2));
      expect(comp.editIndex).toBe(1); // clamped to new last index
   });
});

// ---------------------------------------------------------------------------
// Group 2 — deleteDrill(true): delete all [Risk 3]
// ---------------------------------------------------------------------------

describe("AutoDrillDialog — deleteDrill(true): delete all", () => {
   it("should clear all paths and reset editIndex to -1 on confirm", async () => {
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");
      const paths = [makeDrillPath({ name: "A" }), makeDrillPath({ name: "B" })];
      const { comp } = await renderComp({ model: makeModel(paths) });

      comp.deleteDrill(true);

      await waitFor(() => expect(comp.autoDrillModel.paths).toHaveLength(0));
      expect(comp.editIndex).toBe(-1);
   });

   it("should leave paths unchanged when user cancels", async () => {
      const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog")
         .mockResolvedValue("cancel");
      const paths = [makeDrillPath({ name: "A" }), makeDrillPath({ name: "B" })];
      const { comp } = await renderComp({ model: makeModel(paths) });

      comp.deleteDrill(true);

      await waitFor(() => expect(confirmSpy).toHaveBeenCalled());
      expect(comp.autoDrillModel.paths).toHaveLength(2);
   });
});

// ---------------------------------------------------------------------------
// Group 3 — removeAllParameters() [Risk 3]
// ---------------------------------------------------------------------------

describe("AutoDrillDialog — removeAllParameters()", () => {
   it("should empty the params array on confirm", async () => {
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");
      const path = makeDrillPath({
         params: [
            { name: "p1", field: "col1", type: "string" },
            { name: "p2", field: "col2", type: "string" },
         ],
      });
      const { comp } = await renderComp({ model: makeModel([path]) });

      comp.removeAllParameters();

      await waitFor(() =>
         expect(comp.autoDrillModel.paths[0].params).toHaveLength(0)
      );
   });

   it("should leave params unchanged when user cancels", async () => {
      const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog")
         .mockResolvedValue("cancel");
      const path = makeDrillPath({
         params: [{ name: "p1", field: "col1", type: "string" }],
      });
      const { comp } = await renderComp({ model: makeModel([path]) });

      comp.removeAllParameters();

      await waitFor(() => expect(confirmSpy).toHaveBeenCalled());
      expect(comp.autoDrillModel.paths[0].params).toHaveLength(1);
   });
});

// ---------------------------------------------------------------------------
// Group 4 — moveDrillDown() / moveDrillUp() [Risk 2]
// ---------------------------------------------------------------------------

describe("AutoDrillDialog — moveDrillDown() and moveDrillUp()", () => {
   // 🔁 Regression-sensitive: the swap must also update editIndex so the form keeps
   // editing the same logical drill after the move. A missing editIndex update leaves
   // the user editing the wrong drill.
   it("moveDrillDown should swap the path with its successor and increment editIndex", async () => {
      const paths = [
         makeDrillPath({ name: "First" }),
         makeDrillPath({ name: "Second" }),
         makeDrillPath({ name: "Third" }),
      ];
      const { comp } = await renderComp({ model: makeModel(paths) });
      // ngOnInit selects index 0

      comp.moveDrillDown(0);

      expect(comp.autoDrillModel.paths[0].name).toBe("Second");
      expect(comp.autoDrillModel.paths[1].name).toBe("First");
      expect(comp.editIndex).toBe(1); // editIndex follows the moved item
   });

   it("moveDrillDown should be a no-op when called on the last item", async () => {
      const paths = [makeDrillPath({ name: "First" }), makeDrillPath({ name: "Second" })];
      const { comp } = await renderComp({ model: makeModel(paths) });
      comp.selectDrill(null, 1); // select last

      comp.moveDrillDown(1);

      expect(comp.autoDrillModel.paths[0].name).toBe("First");
      expect(comp.autoDrillModel.paths[1].name).toBe("Second");
      expect(comp.editIndex).toBe(1); // unchanged
   });

   it("moveDrillUp should swap the path with its predecessor and decrement editIndex", async () => {
      const paths = [
         makeDrillPath({ name: "First" }),
         makeDrillPath({ name: "Second" }),
         makeDrillPath({ name: "Third" }),
      ];
      const { comp } = await renderComp({ model: makeModel(paths) });
      comp.selectDrill(null, 2); // select last

      comp.moveDrillUp(2);

      expect(comp.autoDrillModel.paths[1].name).toBe("Third");
      expect(comp.autoDrillModel.paths[2].name).toBe("Second");
      expect(comp.editIndex).toBe(1);
   });

   it("moveDrillUp should be a no-op when called on the first item", async () => {
      const paths = [makeDrillPath({ name: "First" }), makeDrillPath({ name: "Second" })];
      const { comp } = await renderComp({ model: makeModel(paths) });
      // ngOnInit selects index 0

      comp.moveDrillUp(0);

      expect(comp.autoDrillModel.paths[0].name).toBe("First");
      expect(comp.editIndex).toBe(0); // unchanged
   });
});

// ---------------------------------------------------------------------------
// Group 5 — getDrillWorksheet() [Risk 2]
// ---------------------------------------------------------------------------

describe("AutoDrillDialog — getDrillWorksheet()", () => {
   it("should return '_#(None)' when the drill has no query", async () => {
      const path = makeDrillPath({ query: null });
      const { comp } = await renderComp({ model: makeModel([path]) });
      expect(comp.getDrillWorksheet()).toBe("_#(js:None)");
   });

   it("should return the path with alias substituted at the last segment", async () => {
      const path = makeDrillPath({
         query: {
            entry: {
               path: "Folder/SubFolder/MySheet",
               alias: "Renamed",
            } as any,
         },
      });
      const { comp } = await renderComp({ model: makeModel([path]) });
      expect(comp.getDrillWorksheet()).toBe("Folder/SubFolder/Renamed");
   });

   it("should return the plain path when no alias is set", async () => {
      const path = makeDrillPath({
         query: {
            entry: { path: "Folder/MySheet", alias: null } as any,
         },
      });
      const { comp } = await renderComp({ model: makeModel([path]) });
      expect(comp.getDrillWorksheet()).toBe("Folder/MySheet");
   });
});

// ---------------------------------------------------------------------------
// Group 6 — cancel() and getFirstErrorMessage() [Risk 1]
// ---------------------------------------------------------------------------

describe("AutoDrillDialog — cancel() and getFirstErrorMessage()", () => {
   it("cancel should emit 'cancel' via onCancel", async () => {
      const { comp } = await renderComp();
      const cancelled: string[] = [];
      comp.onCancel.subscribe(v => cancelled.push(v));

      comp.cancel();

      expect(cancelled).toEqual(["cancel"]);
   });

   it("getFirstErrorMessage should return the message for the first matching validator", async () => {
      const path = makeDrillPath({ name: "", link: "" }); // required fails
      const { comp } = await renderComp({ model: makeModel([path]) });
      // nameControl has Validators.required — the value "" leaves it invalid
      comp.nameControl.setValue("");
      comp.nameControl.markAsTouched();

      const msg = comp.getFirstErrorMessage(comp.nameControl);

      expect(msg).toBe("_#(js:data.logicalmodel.drillNameRequired)");
   });

   it("getFirstErrorMessage should return null when the control is valid", async () => {
      const path = makeDrillPath({ name: "ValidName" });
      const { comp } = await renderComp({ model: makeModel([path]) });

      const msg = comp.getFirstErrorMessage(comp.nameControl);

      expect(msg).toBeNull();
   });
});
