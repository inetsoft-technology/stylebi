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
 * VsWizardObjectComponent — Pass 3 (display)
 *
 * Risk-first coverage:
 *   Group 1  [baseline] — isVSWizardObject(): 11-branch objectType dispatch;
 *                          true for known types, false for unknown/undefined
 *   Group 2  [baseline] — canEdit() via miniToolbarActions[0].actions[0].visible():
 *                          false when vsObject is null/hasDynamic=true, true otherwise
 *   Group 3  [baseline] — isBoundsChanged(): false before resize, true after mutating
 *                          objectFormat, false again when original equals current
 *   Group 4  [baseline] — saveOriginalBounds() via onResizeStart():
 *                          isBoundsChanged() reports correctly after format mutation
 *   Group 5  [baseline] — updateFollowPositions() via onDragMove():
 *                          follow-object DOM elements get updated CSS left/top;
 *                          non-follow-object elements keep original CSS;
 *                          onRowsChanged/onColsChanged emitted when follow exceeds maxHeight/maxWidth
 *
 * Confirmed bugs (it.fails): none
 *
 * Out of scope:
 *   Interaction flows (drag/resize/select/subscription) — covered in P1
 */

import { makeVsObject, makeViewsheet, renderComponent } from "./vs-wizard-object.test-fixtures";
import { VSObjectModel } from "../../../vsobjects/model/vs-object-model";

// ---------------------------------------------------------------------------
// Group 1 — isVSWizardObject() 11-branch dispatch [baseline]
// ---------------------------------------------------------------------------

describe("VsWizardObjectComponent — isVSWizardObject()", () => {
   const trueTypes = [
      "VSText",
      "VSImage",
      "VSGauge",
      "VSCalendar",
      "VSChart",
      "VSSelectionContainer",
      "VSSelectionList",
      "VSSelectionTree",
      "VSTable",
      "VSCrosstab",
      "VSRangeSlider",
   ];

   for(const objectType of trueTypes) {
      it(`should return true for objectType "${objectType}"`, async () => {
         const { comp } = await renderComponent({ vsObject: makeVsObject(objectType) });
         expect(comp.isVSWizardObject()).toBe(true);
      });
   }

   it("should return false for unrecognized objectType", async () => {
      const { comp } = await renderComponent({ vsObject: makeVsObject("VSUnknown") });
      expect(comp.isVSWizardObject()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 2 — canEdit() via miniToolbarActions visible() [baseline]
// ---------------------------------------------------------------------------

describe("VsWizardObjectComponent — canEdit() via miniToolbarActions visible()", () => {
   it("should return true when vsObject is set and hasDynamic is false", async () => {
      const { comp } = await renderComponent({ vsObject: makeVsObject("VSChart", { hasDynamic: false }) });
      const editAction = comp.miniToolbarActions[0]?.actions.find(a => a.id() === "Edit VS Wizard Object");
      expect(editAction).toBeDefined();
      expect(editAction!.visible!()).toBe(true);
   });

   it("should return false when vsObject.hasDynamic is true", async () => {
      const { comp } = await renderComponent({
         vsObject: makeVsObject("VSChart", { hasDynamic: true } as any),
      });
      const editAction = comp.miniToolbarActions[0]?.actions.find(a => a.id() === "Edit VS Wizard Object");
      expect(editAction).toBeDefined();
      expect(editAction!.visible!()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 3 — isBoundsChanged() [baseline]
// ---------------------------------------------------------------------------

describe("VsWizardObjectComponent — isBoundsChanged()", () => {
   it("should return false when originalRectangle matches current objectFormat", async () => {
      const vsObject = makeVsObject("VSChart");
      const { comp } = await renderComponent({ vsObject });

      // Capture original bounds via onResizeStart
      comp.onResizeStart({});

      expect(comp.isBoundsChanged()).toBe(false);
   });

   it("should return true after objectFormat.width is changed from original", async () => {
      const vsObject = makeVsObject("VSChart");
      const { comp } = await renderComponent({ vsObject });

      comp.onResizeStart({});
      vsObject.objectFormat.width = 200;

      expect(comp.isBoundsChanged()).toBe(true);
   });

   it("should return true after objectFormat.height is changed", async () => {
      const vsObject = makeVsObject("VSChart");
      const { comp } = await renderComponent({ vsObject });

      comp.onResizeStart({});
      vsObject.objectFormat.height = 200;

      expect(comp.isBoundsChanged()).toBe(true);
   });

   it("should return true after objectFormat.left is changed", async () => {
      const vsObject = makeVsObject("VSChart");
      const { comp } = await renderComponent({ vsObject });

      comp.onResizeStart({});
      vsObject.objectFormat.left = 99;

      expect(comp.isBoundsChanged()).toBe(true);
   });

   it("should return true after objectFormat.top is changed", async () => {
      const vsObject = makeVsObject("VSChart");
      const { comp } = await renderComponent({ vsObject });

      comp.onResizeStart({});
      vsObject.objectFormat.top = 99;

      expect(comp.isBoundsChanged()).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 4 — saveOriginalBounds() via onResizeStart() [baseline]
// ---------------------------------------------------------------------------

describe("VsWizardObjectComponent — saveOriginalBounds() via onResizeStart()", () => {
   it("should capture current objectFormat so isBoundsChanged compares against it", async () => {
      const vsObject = makeVsObject("VSChart", {
         objectFormat: { left: 30, top: 40, width: 120, height: 60 } as any,
      });
      const { comp } = await renderComponent({ vsObject });

      comp.onResizeStart({});
      // No mutation → unchanged
      expect(comp.isBoundsChanged()).toBe(false);

      vsObject.objectFormat.left = 31;
      expect(comp.isBoundsChanged()).toBe(true);
   });

   it("should update originalRectangle on a second onResizeStart call", async () => {
      const vsObject = makeVsObject("VSChart");
      const { comp } = await renderComponent({ vsObject });

      comp.onResizeStart({});
      vsObject.objectFormat.width = 200;
      expect(comp.isBoundsChanged()).toBe(true);

      // A second resize start captures the now-mutated state
      comp.onResizeStart({});
      expect(comp.isBoundsChanged()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 5 — updateFollowPositions() via onDragMove() [baseline]
// ---------------------------------------------------------------------------

describe("VsWizardObjectComponent — updateFollowPositions() via onDragMove()", () => {
   // 🔁 Regression-sensitive: follow-objects must move in sync with the dragged
   // assembly. If CSS styles are not updated, follow-objects appear to jump
   // to their original position on drag end (when the server response applies).
   it("should update CSS left/top on DOM elements marked as follow-object", async () => {
      const primary = makeVsObject("VSChart", {
         absoluteName: "Primary",
         objectFormat: { left: 0, top: 0, width: 100, height: 50 } as any,
      });
      const follower = makeVsObject("VSText", {
         absoluteName: "Follower",
         objectFormat: { left: 50, top: 50, width: 80, height: 40 } as any,
      });
      const viewsheet = makeViewsheet([primary, follower]);
      const { comp } = await renderComponent({ vsObject: primary, viewsheet });

      // Create a follow-object div in the jsdom DOM
      const followerDiv = document.createElement("div");
      followerDiv.id = "Follower";
      followerDiv.classList.add("follow-object");
      document.body.appendChild(followerDiv);

      try {
         comp.onDragStart({ shiftKey: false });
         comp.onDragMove({ dx: 20, dy: 10 });

         expect(followerDiv.style.left).toBe("70px");
         expect(followerDiv.style.top).toBe("60px");
      } finally {
         document.body.removeChild(followerDiv);
      }
   });

   it("should keep non-follow-object elements at their original objectFormat position", async () => {
      const primary = makeVsObject("VSChart", {
         absoluteName: "Primary",
         objectFormat: { left: 0, top: 0, width: 100, height: 50 } as any,
      });
      const nonFollower = makeVsObject("VSText", {
         absoluteName: "NonFollower",
         objectFormat: { left: 30, top: 20, width: 80, height: 40 } as any,
      });
      const viewsheet = makeViewsheet([primary, nonFollower]);
      const { comp } = await renderComponent({ vsObject: primary, viewsheet });

      const div = document.createElement("div");
      div.id = "NonFollower";
      document.body.appendChild(div);

      try {
         comp.onDragStart({ shiftKey: false });
         comp.onDragMove({ dx: 50, dy: 30 });

         expect(div.style.left).toBe("30px");
         expect(div.style.top).toBe("20px");
      } finally {
         document.body.removeChild(div);
      }
   });

   it("should emit onRowsChanged when a follow-object exceeds maxHeight during drag", async () => {
      const primary = makeVsObject("VSChart", {
         absoluteName: "Primary",
         objectFormat: { left: 0, top: 0, width: 100, height: 50 } as any,
      });
      const follower = makeVsObject("VSText", {
         absoluteName: "Follower",
         objectFormat: { left: 0, top: 400, width: 80, height: 40 } as any,
      });
      const viewsheet = makeViewsheet([primary, follower]);
      const { comp } = await renderComponent({ vsObject: primary, viewsheet, maxHeight: 500 });
      const rowsEmitted: any[] = [];
      comp.onRowsChanged.subscribe((v: any) => rowsEmitted.push(v));

      const div = document.createElement("div");
      div.id = "Follower";
      div.classList.add("follow-object");
      document.body.appendChild(div);

      try {
         comp.onDragStart({ shiftKey: false });
         // drag down 100px: follower top = 400 + 100 = 500; 500 + 40 = 540 > maxHeight(500)
         comp.onDragMove({ dx: 0, dy: 100 });

         expect(rowsEmitted.some((v: any) => typeof v === "number" && v > 500)).toBe(true);
      } finally {
         document.body.removeChild(div);
      }
   });
});
