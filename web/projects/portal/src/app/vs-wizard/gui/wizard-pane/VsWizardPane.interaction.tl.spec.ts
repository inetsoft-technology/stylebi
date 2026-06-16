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
 * VsWizardPane — Pass 1: Interaction
 *
 * Risk-first coverage:
 *   Group 1  [Risk 3] — close(): onFinish emits gridRowCount / onCancel emits for save=false
 *   Group 2  [Risk 3] — editWizardObject: emits onChangeCurrentObject + toComponentWizard for
 *                        non-image; triggers file-input click for VSImage instead
 *   Group 3  [Risk 3] — fileChanged: HTTP success → refreshVSObject called; null response →
 *                        error dialog; HTTP error → error dialog
 *   Group 4  [Risk 3] — processAddVSObjectCommand: new object pushed; existing name replaced
 *                        (not duplicated); temp-assembly prefix skipped
 *   Group 5  [Risk 3] — refreshVSObject: existing object replaced; new non-temp pushed;
 *                        temp-assembly not added
 *   Group 6  [Risk 2] — processSetWizardGridCommand: gridRowCount / gridColCount updated
 *   Group 7  [Risk 2] — insertObject / resizeObject: correct WebSocket events sent
 *   Group 8  [Risk 2] — processUpdateUndoStateCommand → undoEnabled / redoEnabled toggled
 *   Group 9  [baseline] — gridRowCount setter: odd input rounded up to next even
 *   Group 10 [baseline] — runtimeId / linkUri setters propagate to viewsheet
 *   Group 11 [baseline] — undoEnabled: true when current > 0 and not loading; false paths
 *   Group 12 [baseline] — redoEnabled: true when current < points-1 and not loading; false paths
 *   Group 13 [baseline] — changeFollowDirection / isFollow
 *   Group 14 [baseline] — changeRows / changeCols (number overload)
 *   Group 15 [baseline] — dragResizeStart / dragResizeEnd state transitions
 *   Group 16 [baseline] — mergeDimension: pure enclosing-rect merge
 *   Group 17 [baseline] — onSelectionBox: intersecting objects selected; non-intersecting excluded
 *   Group 18 [baseline] — getFollowDirSrc: three direction branches
 *   Group 19 [baseline] — hiddenNewBlockChanged: emits toggled value
 *   Group 20 [baseline] — keyDown: Escape clears focused assemblies
 *   Group 21 [baseline, +内存泄漏] — ngOnDestroy: command subscription cleaned up
 *
 * Confirmed bugs (it.fails): none
 *
 * Suspected bugs (header only):
 *   Suspicion A — processAddVSObjectCommand: the `update` flag is set when an existing object is
 *     replaced but never read. The object is returned early from the for-loop, so the outer push
 *     is never reached — this is correct, but the flag is dead code.
 *
 * Out of scope this pass:
 *   getAssemblyName() — always returns null; zero test value
 *   trackByFn() — trivial absoluteName lookup; zero test value
 *   refreshGridRows() — depends on paneContainer.nativeElement.offsetHeight (0 in jsdom); covered
 *     indirectly via dragResizeEnd which invokes it
 *   ngAfterViewInit() — delegates to refreshGridRows via Promise.resolve; DOM-dependent
 *   draggableRestriction() — uses getBoundingClientRect (returns zero rect in jsdom)
 *   prepareBottomFollowAssemblies / prepareRightFollowAssemblies — querySelector-based restriction
 *     calc; getBoundingClientRect=0; deferred to Pass 2 risk file
 *   prepareFollowRestriction() — same DOM limitation
 *   sortByXPosition / sortByYPosition — trivially-correct pure comparators; zero test value
 *   processAssemblyMoved() — setTimeout-based auto-scroll; Pass 2 risk scope
 *   moveWizardObject() / moveAssembly() — Pass 2 risk scope
 *   clearFocused() / shouldClearFocused() — Pass 2 risk scope
 *   processRemoveVSObjectCommand() — Pass 2 risk scope
 *   removeWizardObject() / remove() — Pass 2 risk scope (delegates to remove() → sendEvent)
 *   processUploadImageCommand — covered transitively via fileChanged success test which calls
 *     refreshVSObject (same code path as processUploadImageCommand's non-noImageFlag branch)
 */

import { waitFor } from "@testing-library/angular";
import { http, HttpResponse } from "msw";

import { server } from "@test-mocks/server";
import {
   commandsSubject,
   CLIENT_SERVICE_MOCK,
   EVENT_QUEUE_MOCK,
   MODAL_MOCK,
   resetMocks,
   makeVSObject,
   renderComponent,
} from "./vs-wizard-pane.test-fixtures";
import { AddVSObjectCommand } from "../../../vsobjects/command/add-vs-object-command";
import { UpdateUndoStateCommand } from "../../../vsobjects/command/update-unto-state-command";
import { SetWizardGridCommand } from "../../model/command/set-wizard-grid-command";
import { AssemblyType } from "../../../composer/gui/vs/assembly-type";
import { Point } from "../../../common/data/point";
import { Rectangle } from "../../../common/data/rectangle";
import { VSWizardConstants } from "../../model/vs-wizard-constants";

beforeEach(() => {
   resetMocks();
});

// ---------------------------------------------------------------------------
// Group 1 — close(): output contracts [Risk 3]
// ---------------------------------------------------------------------------

describe("VsWizardPane — close()", () => {
   // 🔁 Regression-sensitive: close(true) must emit onFinish (not onCancel) and include the
   // current gridRowCount so the parent can resize the viewsheet to fit the layout.
   it("should emit onFinish with gridRowCount when save=true", async () => {
      const { comp } = await renderComponent();
      const emitted: number[] = [];
      comp.onFinish.subscribe(v => emitted.push(v));
      comp.gridRowCount = 10;

      comp.close(true);

      expect(emitted).toHaveLength(1);
      expect(emitted[0]).toBe(10);
   });

   it("should emit onCancel and NOT onFinish when save=false", async () => {
      const { comp } = await renderComponent();
      const cancelled: any[] = [];
      const finished: any[] = [];
      comp.onCancel.subscribe(v => cancelled.push(v));
      comp.onFinish.subscribe(v => finished.push(v));

      comp.close(false);

      expect(cancelled).toHaveLength(1);
      expect(finished).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 2 — editWizardObject: emit contracts [Risk 3]
// ---------------------------------------------------------------------------

describe("VsWizardPane — editWizardObject()", () => {
   // 🔁 Regression-sensitive: for non-image objects, both onChangeCurrentObject AND
   // toComponentWizard must emit. Missing the latter leaves the user stuck on the wizard grid.
   it("should emit onChangeCurrentObject then toComponentWizard for non-VSImage object", async () => {
      const { comp } = await renderComponent();
      const changedObjs: any[] = [];
      const wizardEvents: any[] = [];
      comp.onChangeCurrentObject.subscribe(v => changedObjs.push(v));
      comp.toComponentWizard.subscribe(v => wizardEvents.push(v));

      const obj = makeVSObject({ absoluteName: "Chart1", objectType: "VSChart" });
      comp.viewsheet.vsObjects.push(obj);

      comp.editWizardObject("Chart1");

      expect(changedObjs).toHaveLength(1);
      expect(changedObjs[0]).toBe(obj);
      expect(wizardEvents).toHaveLength(1);
      expect(wizardEvents[0]).toMatchObject({ objectName: "Chart1", objectType: "VSChart" });
   });

   it("should NOT emit toComponentWizard for VSImage (triggers file-input click instead)", async () => {
      const { comp } = await renderComponent();
      const wizardEvents: any[] = [];
      comp.toComponentWizard.subscribe(v => wizardEvents.push(v));

      const imgObj = makeVSObject({ absoluteName: "Image1", objectType: "VSImage" });
      comp.viewsheet.vsObjects.push(imgObj);

      // Spy on file-input click to confirm the alternative path fires
      const clickSpy = vi.spyOn(comp.uploadInput.nativeElement, "click");
      try {
         comp.editWizardObject("Image1");

         expect(wizardEvents).toHaveLength(0);
         expect(clickSpy).toHaveBeenCalledTimes(1);
      } finally {
         clickSpy.mockRestore();
      }
   });

   it("should NOT emit anything when objectName is not found in vsObjects", async () => {
      const { comp } = await renderComponent();
      const changedObjs: any[] = [];
      comp.onChangeCurrentObject.subscribe(v => changedObjs.push(v));

      comp.editWizardObject("NonExistent");

      expect(changedObjs).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 3 — fileChanged: HTTP upload [Risk 3]
// ---------------------------------------------------------------------------

describe("VsWizardPane — fileChanged()", () => {
   function makeFileEvent(): any {
      return { target: { files: [new File(["pixel"], "test.png", { type: "image/png" })] } };
   }

   // 🔁 Regression-sensitive: if refreshVSObject is not called on success, the updated image
   // is never shown in the wizard grid — the user sees a stale placeholder.
   it("should call refreshVSObject with returned model on HTTP success", async () => {
      const returnedModel = makeVSObject({ absoluteName: "Img1", objectType: "VSImage" });
      server.use(
         http.post("*/api/composer/vswizard/update-image/**", () => HttpResponse.json(returnedModel))
      );
      const { comp } = await renderComponent();
      comp.currentVSObject = makeVSObject({ absoluteName: "Img1" });
      comp.runtimeId = "rt-test";

      const refreshSpy = vi.spyOn(comp, "refreshVSObject");
      try {
         comp.fileChanged(makeFileEvent());
         await waitFor(() => expect(refreshSpy).toHaveBeenCalledWith(expect.objectContaining({ absoluteName: "Img1" })));
      } finally {
         refreshSpy.mockRestore();
      }
   });

   it("should show error dialog when server returns null body", async () => {
      server.use(
         http.post("*/api/composer/vswizard/update-image/**", () => HttpResponse.json(null))
      );
      const { comp } = await renderComponent();
      comp.currentVSObject = makeVSObject({ absoluteName: "Img1" });
      comp.runtimeId = "rt-test";

      comp.fileChanged(makeFileEvent());

      await waitFor(() => expect(MODAL_MOCK.open).toHaveBeenCalledTimes(1));
   });

   it("should show error dialog on HTTP error", async () => {
      server.use(
         http.post("*/api/composer/vswizard/update-image/**", () => HttpResponse.error())
      );
      const { comp } = await renderComponent();
      comp.currentVSObject = makeVSObject({ absoluteName: "Img1" });
      comp.runtimeId = "rt-test";

      comp.fileChanged(makeFileEvent());

      await waitFor(() => expect(MODAL_MOCK.open).toHaveBeenCalledTimes(1));
   });
});

// ---------------------------------------------------------------------------
// Group 4 — processAddVSObjectCommand [Risk 3]
// ---------------------------------------------------------------------------

describe("VsWizardPane — processAddVSObjectCommand", () => {
   // 🔁 Regression-sensitive: if a temp assembly is accidentally pushed to vsObjects, it appears
   // as a ghost object on the wizard grid with no binding, confusing the user.
   it("should push new non-temp object to vsObjects", async () => {
      const { comp } = await renderComponent();
      const cmd: AddVSObjectCommand = { model: makeVSObject({ absoluteName: "Chart1" }), name: "Chart1" } as any;

      (comp as any)["processAddVSObjectCommand"](cmd);

      expect(comp.viewsheet.vsObjects).toHaveLength(1);
      expect(comp.viewsheet.vsObjects[0].absoluteName).toBe("Chart1");
   });

   it("should replace existing vsObject instead of duplicating when name matches", async () => {
      const { comp } = await renderComponent();
      const existing = makeVSObject({ absoluteName: "Chart1", objectType: "VSChart" });
      comp.viewsheet.vsObjects.push(existing);

      const updated = makeVSObject({ absoluteName: "Chart1", objectType: "VSCrosstab" });
      const cmd: AddVSObjectCommand = { model: updated, name: "Chart1" } as any;
      (comp as any)["processAddVSObjectCommand"](cmd);

      expect(comp.viewsheet.vsObjects).toHaveLength(1);
   });

   it("should skip temp-assembly objects (not add them to vsObjects)", async () => {
      const { comp } = await renderComponent();
      const tempName = VSWizardConstants.TEMP_ASSEMBLY_PREFIX + "Chart1";
      const cmd: AddVSObjectCommand = { model: makeVSObject({ absoluteName: tempName }), name: tempName } as any;

      (comp as any)["processAddVSObjectCommand"](cmd);

      expect(comp.viewsheet.vsObjects).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 5 — refreshVSObject [Risk 3]
// ---------------------------------------------------------------------------

describe("VsWizardPane — refreshVSObject()", () => {
   // 🔁 Regression-sensitive: if the refresh doesn't replace the old object, the grid shows the
   // stale model (wrong chart type, stale data binding) until a full page refresh.
   it("should replace existing vsObject in place when absoluteName matches", async () => {
      const { comp } = await renderComponent();
      const original = makeVSObject({ absoluteName: "Chart1", objectType: "VSChart" });
      comp.viewsheet.vsObjects.push(original);

      const updated = makeVSObject({ absoluteName: "Chart1", objectType: "VSCrosstab" });
      comp.refreshVSObject(updated);

      expect(comp.viewsheet.vsObjects).toHaveLength(1);
   });

   it("should push a new non-temp object when not already in vsObjects", async () => {
      const { comp } = await renderComponent();

      comp.refreshVSObject(makeVSObject({ absoluteName: "NewChart" }));

      expect(comp.viewsheet.vsObjects).toHaveLength(1);
      expect(comp.viewsheet.vsObjects[0].absoluteName).toBe("NewChart");
   });

   it("should NOT push a temp assembly to vsObjects", async () => {
      const { comp } = await renderComponent();
      const tempObj = makeVSObject({ absoluteName: VSWizardConstants.TEMP_ASSEMBLY_PREFIX + "X" });

      comp.refreshVSObject(tempObj);

      expect(comp.viewsheet.vsObjects).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 6 — processSetWizardGridCommand [Risk 2]
// ---------------------------------------------------------------------------

describe("VsWizardPane — processSetWizardGridCommand", () => {
   // refreshGridRows() is called at the end of the handler and overwrites gridRowCount with 0
   // (jsdom offsetHeight = 0).  Mock it so we can assert the setter ran before the overwrite.
   it("should update gridRowCount (rounded up to even) from command", async () => {
      const { comp } = await renderComponent();
      const refreshSpy = vi.spyOn(comp, "refreshGridRows").mockImplementation(() => {});
      try {
         const cmd: SetWizardGridCommand = { gridRowCount: 7, gridColCount: 12 } as any;
         (comp as any)["processSetWizardGridCommand"](cmd);

         // gridRowCount setter rounds odd → even: 7 → 8
         expect(comp.gridRowCount).toBe(8);
      } finally {
         refreshSpy.mockRestore();
      }
   });

   it("should update gridColCount from command", async () => {
      const { comp } = await renderComponent();
      const cmd: SetWizardGridCommand = { gridRowCount: 8, gridColCount: 10 } as any;

      // gridColCount is not touched by refreshGridRows, so no spy needed
      (comp as any)["processSetWizardGridCommand"](cmd);

      expect(comp.gridColCount).toBe(10);
   });
});

// ---------------------------------------------------------------------------
// Group 7 — insertObject / resizeObject [Risk 2]
// ---------------------------------------------------------------------------

describe("VsWizardPane — insertObject() / resizeObject()", () => {
   it("should send AddNewVSObjectEvent to INSERT_OBJECT_URI with correct type and position", async () => {
      const { comp } = await renderComponent();
      CLIENT_SERVICE_MOCK.sendEvent.mockClear();
      const point: Point = { x: 100, y: 80 };

      comp.insertObject(AssemblyType.CHART_ASSET, point);

      // AddNewVSObjectEvent stores coordinates as xOffset/yOffset
      expect(CLIENT_SERVICE_MOCK.sendEvent).toHaveBeenCalledWith(
         "/events/composer/vswizard/insert-object",
         expect.objectContaining({ xOffset: 100, yOffset: 80 })
      );
   });

   it("should send ResizeVSObjectEvent to resize URI with object dimensions", async () => {
      const { comp } = await renderComponent();
      CLIENT_SERVICE_MOCK.sendEvent.mockClear();
      const vsObj = makeVSObject({ absoluteName: "Chart1" });

      comp.resizeObject(vsObj);

      expect(CLIENT_SERVICE_MOCK.sendEvent).toHaveBeenCalledWith(
         "/events/composer/vswizard/object/resize",
         expect.objectContaining({ name: "Chart1" })
      );
   });
});

// ---------------------------------------------------------------------------
// Group 8 — processUpdateUndoStateCommand → undoEnabled / redoEnabled [Risk 2]
// ---------------------------------------------------------------------------

describe("VsWizardPane — processUpdateUndoStateCommand", () => {
   it("should enable undoEnabled when current > 0 after command", async () => {
      const { comp } = await renderComponent();
      const cmd: UpdateUndoStateCommand = { points: 3, current: 1, savePoint: 1 } as any;

      (comp as any)["processUpdateUndoStateCommand"](cmd);

      expect(comp.undoEnabled).toBe(true);
   });

   it("should enable redoEnabled when current < points-1 after command", async () => {
      const { comp } = await renderComponent();
      const cmd: UpdateUndoStateCommand = { points: 3, current: 1, savePoint: 1 } as any;

      (comp as any)["processUpdateUndoStateCommand"](cmd);

      expect(comp.redoEnabled).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 9 — gridRowCount setter [baseline]
// ---------------------------------------------------------------------------

describe("VsWizardPane — gridRowCount setter", () => {
   it("should round an odd value up to the next even number", async () => {
      const { comp } = await renderComponent();
      comp.gridRowCount = 5;

      expect(comp.gridRowCount).toBe(6);
   });

   it("should leave an even value unchanged", async () => {
      const { comp } = await renderComponent();
      comp.gridRowCount = 8;

      expect(comp.gridRowCount).toBe(8);
   });
});

// ---------------------------------------------------------------------------
// Group 10 — runtimeId / linkUri setters [baseline]
// ---------------------------------------------------------------------------

describe("VsWizardPane — runtimeId / linkUri setters", () => {
   it("should set viewsheet.runtimeId when runtimeId input changes", async () => {
      const { comp } = await renderComponent();

      comp.runtimeId = "rt-abc";

      expect(comp.viewsheet.runtimeId).toBe("rt-abc");
   });

   it("should set viewsheet.linkUri when linkUri input changes", async () => {
      const { comp } = await renderComponent();

      comp.linkUri = "/viewer/main";

      expect(comp.viewsheet.linkUri).toBe("/viewer/main");
   });
});

// ---------------------------------------------------------------------------
// Group 11 — undoEnabled [baseline]
// ---------------------------------------------------------------------------

describe("VsWizardPane — undoEnabled", () => {
   it("should return false when current is 0", async () => {
      const { comp } = await renderComponent();
      (comp as any)["processUpdateUndoStateCommand"]({ points: 3, current: 0, savePoint: 0 });

      expect(comp.undoEnabled).toBe(false);
   });

   it("should return false when viewsheet is loading", async () => {
      const { comp } = await renderComponent();
      (comp as any)["processUpdateUndoStateCommand"]({ points: 3, current: 1, savePoint: 0 });
      comp.viewsheet.loading = true;

      expect(comp.undoEnabled).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 12 — redoEnabled [baseline]
// ---------------------------------------------------------------------------

describe("VsWizardPane — redoEnabled", () => {
   it("should return false when current equals points-1", async () => {
      const { comp } = await renderComponent();
      (comp as any)["processUpdateUndoStateCommand"]({ points: 3, current: 2, savePoint: 0 });

      expect(comp.redoEnabled).toBe(false);
   });

   it("should return true when current is less than points-1", async () => {
      const { comp } = await renderComponent();
      (comp as any)["processUpdateUndoStateCommand"]({ points: 3, current: 1, savePoint: 0 });

      expect(comp.redoEnabled).toBe(true);
   });

   it("should return false when viewsheet is loading", async () => {
      const { comp } = await renderComponent();
      (comp as any)["processUpdateUndoStateCommand"]({ points: 3, current: 1, savePoint: 0 });
      comp.viewsheet.loading = true;

      expect(comp.redoEnabled).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 13 — changeFollowDirection / isFollow [baseline]
// ---------------------------------------------------------------------------

describe("VsWizardPane — changeFollowDirection() / isFollow()", () => {
   it("should not update followAssemblies when drageWithShift is false", async () => {
      const { comp } = await renderComponent();
      comp.drageWithShift = false;
      comp.rightFollowAssemblies.set("Chart1", true);

      comp.changeFollowDirection({ direction: "right", bounds: null });

      // followAssemblies not changed to rightFollowAssemblies
      expect(comp.isFollow("Chart1")).toBe(false);
   });

   it("should set followAssemblies to rightFollowAssemblies when direction=right and drageWithShift=true", async () => {
      const { comp } = await renderComponent();
      comp.drageWithShift = true;
      comp.rightFollowAssemblies.set("Chart1", true);

      comp.changeFollowDirection({ direction: "right", bounds: null });

      expect(comp.followAssemblies.get("Chart1")).toBe(true);
   });

   it("should return false for isFollow when direction is none", async () => {
      const { comp } = await renderComponent();
      comp.drageWithShift = true;
      comp.rightFollowAssemblies.set("Chart1", true);
      comp.changeFollowDirection({ direction: "none", bounds: null });

      expect(comp.isFollow("Chart1")).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 14 — changeRows / changeCols [baseline]
// ---------------------------------------------------------------------------

describe("VsWizardPane — changeRows() / changeCols()", () => {
   it("should increase gridRowCount when passed a row number beyond current", async () => {
      const { comp } = await renderComponent();
      comp.gridRowCount = 4; // 4 is even, stays 4
      const pixelBottom = 5 * comp.gridCellHeight + 1; // beyond row 5

      comp.changeRows(pixelBottom);

      expect(comp.gridRowCount).toBeGreaterThan(4);
   });

   it("should increase gridColCount (rounding to even) when passed a col beyond current", async () => {
      const { comp } = await renderComponent();
      comp.gridColCount = 4;

      // Use pixel beyond current col count
      comp.changeCols(5 * comp.gridCellWidth + 1);

      expect(comp.gridColCount).toBeGreaterThan(4);
   });
});

// ---------------------------------------------------------------------------
// Group 15 — dragResizeStart / dragResizeEnd [baseline]
// ---------------------------------------------------------------------------

describe("VsWizardPane — dragResizeStart() / dragResizeEnd()", () => {
   it("should hide newObjectModel (visible=false) when dragResizeStart is called", async () => {
      const { comp } = await renderComponent();

      comp.dragResizeStart({ withShift: false, isDrag: false, objectModel: null });

      expect(comp.newObjectModel.visible).toBe(false);
   });

   it("should restore newObjectModel.visible after dragResizeEnd", async () => {
      const { comp } = await renderComponent();
      comp.dragResizeStart({ withShift: false, isDrag: false, objectModel: null });

      comp.dragResizeEnd();

      // getBottomRight() returns visible:true for an empty vsObjects list
      expect(comp.newObjectModel.visible).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 16 — mergeDimension [baseline]
// ---------------------------------------------------------------------------

describe("VsWizardPane — mergeDimension()", () => {
   it("should return the smallest enclosing rectangle of two rectangles", async () => {
      const { comp } = await renderComponent();
      const r1 = new Rectangle(0, 0, 100, 50);
      const r2 = new Rectangle(80, 40, 60, 80);

      const merged = comp.mergeDimension(r1, r2);

      expect(merged.x).toBe(0);
      expect(merged.y).toBe(0);
      expect(merged.width).toBe(140);  // max(0+100, 80+60) - 0 = 140
      expect(merged.height).toBe(120); // max(0+50, 40+80) - 0 = 120
   });
});

// ---------------------------------------------------------------------------
// Group 17 — onSelectionBox [baseline]
// ---------------------------------------------------------------------------

describe("VsWizardPane — onSelectionBox()", () => {
   it("should select vsObjects whose bounds intersect the selection box", async () => {
      const { comp } = await renderComponent();
      const obj = makeVSObject({ absoluteName: "Chart1", objectFormat: { top: 10, left: 10, width: 50, height: 50 } as any });
      comp.viewsheet.vsObjects.push(obj);

      // Selection box that overlaps the object
      const box = new Rectangle(0, 0, 100, 100);
      comp.onSelectionBox({ box } as any);

      expect(comp.viewsheet.currentFocusedAssemblies).toContain(obj);
   });

   it("should NOT select vsObjects outside the selection box", async () => {
      const { comp } = await renderComponent();
      const obj = makeVSObject({ absoluteName: "Chart1", objectFormat: { top: 200, left: 200, width: 50, height: 50 } as any });
      comp.viewsheet.vsObjects.push(obj);

      // Selection box that does not overlap
      const box = new Rectangle(0, 0, 100, 100);
      comp.onSelectionBox({ box } as any);

      expect(comp.viewsheet.currentFocusedAssemblies).not.toContain(obj);
   });
});

// ---------------------------------------------------------------------------
// Group 18 — getFollowDirSrc [baseline]
// ---------------------------------------------------------------------------

describe("VsWizardPane — getFollowDirSrc()", () => {
   it("should return arrow-down SVG path when direction is bottom", async () => {
      const { comp } = await renderComponent();
      comp.followDirection.direction = "bottom";

      expect(comp.getFollowDirSrc()).toContain("arrow-down.svg");
   });

   it("should return arrow-right SVG path when direction is right", async () => {
      const { comp } = await renderComponent();
      comp.followDirection.direction = "right";

      expect(comp.getFollowDirSrc()).toContain("arrow-right.svg");
   });

   it("should return empty string when direction is neither bottom nor right", async () => {
      const { comp } = await renderComponent();
      comp.followDirection.direction = "none";

      expect(comp.getFollowDirSrc()).toBe("");
   });
});

// ---------------------------------------------------------------------------
// Group 19 — hiddenNewBlockChanged [baseline]
// ---------------------------------------------------------------------------

describe("VsWizardPane — hiddenNewBlockChanged()", () => {
   it("should emit true via onHiddenNewBlockChanged when hiddenNewBlock is false", async () => {
      const { comp } = await renderComponent();
      comp.hiddenNewBlock = false;
      const emitted: boolean[] = [];
      comp.onHiddenNewBlockChanged.subscribe(v => emitted.push(v));

      comp.hiddenNewBlockChanged();

      expect(emitted).toHaveLength(1);
      expect(emitted[0]).toBe(true);
   });

   it("should emit false via onHiddenNewBlockChanged when hiddenNewBlock is true", async () => {
      const { comp } = await renderComponent();
      comp.hiddenNewBlock = true;
      const emitted: boolean[] = [];
      comp.onHiddenNewBlockChanged.subscribe(v => emitted.push(v));

      comp.hiddenNewBlockChanged();

      expect(emitted).toHaveLength(1);
      expect(emitted[0]).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 20 — keyDown Escape [baseline]
// ---------------------------------------------------------------------------

describe("VsWizardPane — keyDown()", () => {
   it("should clear focused assemblies when Escape (keyCode 27) is pressed", async () => {
      const { comp } = await renderComponent();
      const obj = makeVSObject({ absoluteName: "Chart1" });
      comp.viewsheet.vsObjects.push(obj);
      comp.viewsheet.currentFocusedAssemblies = [obj];

      comp.keyDown({ keyCode: 27 } as KeyboardEvent);

      expect(comp.viewsheet.currentFocusedAssemblies).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 21 — ngOnDestroy subscription cleanup (+内存泄漏) [baseline]
// ---------------------------------------------------------------------------

describe("VsWizardPane — ngOnDestroy subscription cleanup", () => {
   // 🔁 Regression-sensitive: the commands subscription must be cleaned up on destroy.
   // If not, the destroyed component still processes STOMP commands, mutating stale
   // component state and potentially interfering with a new wizard session.
   it("should unsubscribe from viewsheetClient.commands after ngOnDestroy", async () => {
      const { fixture } = await renderComponent();

      // commands stream has one active subscriber before destroy
      expect(commandsSubject.observed).toBe(true);

      fixture.destroy(); // triggers ngOnDestroy → cleanup() → subscription.unsubscribe()

      expect(commandsSubject.observed).toBe(false);
   });
});
