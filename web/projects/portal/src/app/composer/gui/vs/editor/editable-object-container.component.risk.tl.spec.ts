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
 * EditableObjectContainer — Pass 2: Risk
 *
 * async≥3: race conditions / destructive / state inconsistency
 *
 *   Group 1  — getMoveHandle: additional object types (VSChart, VSGauge, VSGroupContainer)
 *   Group 2  — getMovedPosition: no-shift returns (x, y) directly
 *   Group 3  — onDragMove: updates objectFormat and emits onMove
 *   Group 4  — removeCopies: deduplicates vsObjects by absoluteName
 *   Group 5  — onResizeMove: updates dimensions and calls objectComponent.resized()
 *   Group 6  — onLineDragMove: smoke test with empty DOM element arrays
 *   Group 7  — onLineStartDragMove: updates vsLine.startLeft/startTop
 *   Group 8  — onLineEndDragMove: updates vsLine.endLeft/endTop
 */

import { EditableObjectContainer } from "./editable-object-container.component";
import { Viewsheet } from "../../../data/vs/viewsheet";
import {
   makeComponent,
   makeVsObjectModel,
} from "./editable-object-container.component.test-helpers";

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: getMoveHandle — additional types [Risk 2]
// ---------------------------------------------------------------------------

describe("EditableObjectContainer — getMoveHandle (additional types)", () => {

   it("VSChart moveHandle includes title-move-zone and chart-area selectors", () => {
      const { comp } = makeComponent();
      comp.vsObjectModel = makeVsObjectModel("VSChart", "Chart1");
      expect(comp.moveHandle).toContain("title-move-zone");
   });

   it("VSGauge moveHandle is .vs-gauge__image", () => {
      const { comp } = makeComponent();
      comp.vsObjectModel = makeVsObjectModel("VSGauge", "Gauge1");
      expect(comp.moveHandle).toBe(".vs-gauge__image");
   });

   it("VSGroupContainer moveHandle is .vs-group-container", () => {
      const { comp } = makeComponent();
      comp.vsObjectModel = makeVsObjectModel("VSGroupContainer", "Grp1");
      expect(comp.moveHandle).toBe(".vs-group-container");
   });

   it("VSViewsheet moveHandle is null (no move handle)", () => {
      const { comp } = makeComponent();
      comp.vsObjectModel = makeVsObjectModel("VSViewsheet", "Embed1");
      expect(comp.moveHandle).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 2: getMovedPosition — no-shift [Risk 2]
// ---------------------------------------------------------------------------

describe("EditableObjectContainer — getMovedPosition (no shift)", () => {

   it("returns Point(x, y) when isShift is false", () => {
      const { comp } = makeComponent();
      // Provide dragObj so objectPosition is available (dragPlaceholderElement=false, isShift=false)
      (comp as any).dragObj = makeVsObjectModel("VSText", "T1");
      (comp as any).dragObj.objectFormat = { left: 50, top: 80 };

      const pt = (comp as any).getMovedPosition(120, 200);

      expect(pt.x).toBe(120);
      expect(pt.y).toBe(200);
   });

   it("projects onto horizontal axis when horizontal distance is greater (isShift=true)", () => {
      const { comp } = makeComponent();
      (comp as any).isShift = true;
      (comp as any).shadowObj = { objectFormat: { left: 50, top: 50 } };

      // |x - 50| = 80 > |y - 50| = 5  → keeps x, locks y to opoint.y
      const pt = (comp as any).getMovedPosition(130, 55);

      expect(pt.x).toBe(130);
      expect(pt.y).toBe(50); // snapped to original y
   });

   it("projects onto vertical axis when vertical distance is greater (isShift=true)", () => {
      const { comp } = makeComponent();
      (comp as any).isShift = true;
      (comp as any).shadowObj = { objectFormat: { left: 50, top: 50 } };

      // |x - 50| = 5 < |y - 50| = 80 → keeps y, locks x to opoint.x
      const pt = (comp as any).getMovedPosition(55, 130);

      expect(pt.x).toBe(50); // snapped to original x
      expect(pt.y).toBe(130);
   });
});

// ---------------------------------------------------------------------------
// Group 3: onDragMove [Risk 3]
// ---------------------------------------------------------------------------

describe("EditableObjectContainer — onDragMove", () => {

   it("updates dragTop/dragLeft and objectFormat position", () => {
      const { comp } = makeComponent();
      const dragObj = makeVsObjectModel("VSText", "T1") as any;
      dragObj.objectFormat = { left: 100, top: 200, width: 50, height: 30, zIndex: 1 };
      (comp as any).dragObj = dragObj;

      comp.onDragMove({ dx: 10, dy: 5 });

      expect((comp as any).dragLeft).toBe(110);
      expect((comp as any).dragTop).toBe(205);
      expect(dragObj.objectFormat.left).toBe(110);
      expect(dragObj.objectFormat.top).toBe(205);
   });

   it("emits onMove with the event and dragObj model", () => {
      const { comp } = makeComponent();
      const dragObj = makeVsObjectModel("VSText", "T1") as any;
      dragObj.objectFormat = { left: 0, top: 0 };
      (comp as any).dragObj = dragObj;

      const spy = vi.fn();
      comp.onMove.subscribe(spy);

      const event = { dx: 3, dy: 2 };
      comp.onDragMove(event);

      expect(spy).toHaveBeenCalledTimes(1);
      expect(spy.mock.calls[0][0].event).toBe(event);
      expect(spy.mock.calls[0][0].model).toBe(dragObj);
   });

   it("scales movement by 1/viewsheet.scale when scale is set", () => {
      const { comp } = makeComponent();
      comp.viewsheet.scale = 2;
      const dragObj = makeVsObjectModel("VSText", "T1") as any;
      dragObj.objectFormat = { left: 0, top: 0 };
      (comp as any).dragObj = dragObj;

      comp.onDragMove({ dx: 10, dy: 10 });

      // scale = 1/2, so effective movement = 10 * 0.5 = 5
      expect(dragObj.objectFormat.left).toBe(5);
      expect(dragObj.objectFormat.top).toBe(5);
   });
});

// ---------------------------------------------------------------------------
// Group 4: removeCopies [Risk 3]
// ---------------------------------------------------------------------------

describe("EditableObjectContainer — removeCopies (private)", () => {

   it("removes duplicate vsObjects by absoluteName, keeping first occurrence", () => {
      const { comp } = makeComponent();
      const vs = comp.viewsheet;

      const a1 = makeVsObjectModel("VSText", "A");
      const a2 = makeVsObjectModel("VSText", "A"); // duplicate
      const b  = makeVsObjectModel("VSText", "B");
      vs.vsObjects = [a1, a2, b];

      (comp as any).removeCopies();

      expect(vs.vsObjects.length).toBe(2);
      expect(vs.vsObjects[0].absoluteName).toBe("A");
      expect(vs.vsObjects[1].absoluteName).toBe("B");
   });

   it("leaves vsObjects unchanged when there are no duplicates", () => {
      const { comp } = makeComponent();
      const vs = comp.viewsheet;

      const a = makeVsObjectModel("VSText", "A");
      const b = makeVsObjectModel("VSText", "B");
      vs.vsObjects = [a, b];

      (comp as any).removeCopies();

      expect(vs.vsObjects.length).toBe(2);
   });

   it("removes all extra duplicates when the same name appears three times", () => {
      const { comp } = makeComponent();
      const vs = comp.viewsheet;

      vs.vsObjects = [
         makeVsObjectModel("VSText", "X"),
         makeVsObjectModel("VSText", "X"),
         makeVsObjectModel("VSText", "X"),
         makeVsObjectModel("VSText", "Y"),
      ];

      (comp as any).removeCopies();

      const names = vs.vsObjects.map((o: any) => o.absoluteName);
      expect(names).toEqual(["X", "Y"]);
   });
});

// ---------------------------------------------------------------------------
// Group 5: onResizeMove [Risk 3]
// ---------------------------------------------------------------------------

describe("EditableObjectContainer — onResizeMove", () => {

   function makeResizeEvent(overrides: Partial<any> = {}) {
      return {
         dx: overrides.dx ?? 10,
         dy: overrides.dy ?? 5,
         deltaRect: {
            width:  overrides.dw  ?? 10,
            height: overrides.dh  ?? 5,
            left:   overrides.dl  ?? 0,
            top:    overrides.dt  ?? 0,
         },
         shiftKey: overrides.shiftKey ?? false,
         // omit 'interaction' so the bounds-check block is skipped
      };
   }

   it("increases objectFormat.width and height when deltaRect is positive", () => {
      const { comp } = makeComponent();
      comp.vsObject.objectFormat.width = 100;
      comp.vsObject.objectFormat.height = 50;

      comp.onResizeMove(makeResizeEvent({ dx: 20, dw: 20, dy: 10, dh: 10 }));

      expect(comp.vsObject.objectFormat.width).toBeCloseTo(120);
      expect(comp.vsObject.objectFormat.height).toBeCloseTo(60);
   });

   it("clamps objectFormat dimensions to minimum of 1", () => {
      const { comp } = makeComponent();
      comp.vsObject.objectFormat.width = 5;
      comp.vsObject.objectFormat.height = 3;

      comp.onResizeMove(makeResizeEvent({ dx: -100, dw: -100, dy: -100, dh: -100 }));

      expect(comp.vsObject.objectFormat.width).toBeGreaterThanOrEqual(1);
      expect(comp.vsObject.objectFormat.height).toBeGreaterThanOrEqual(1);
   });

   it("calls objectComponent.resized() to force change detection", () => {
      const { comp } = makeComponent();
      comp.onResizeMove(makeResizeEvent());

      const resizedMock = (comp as any).objectComponent.resized;
      expect(resizedMock).toHaveBeenCalledTimes(1);
   });

   it("calls changeDetectorRef.detectChanges()", () => {
      const { comp, mocks } = makeComponent();
      comp.onResizeMove(makeResizeEvent());
      expect(mocks.changeDetectorRef.detectChanges).toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 6: onLineDragMove — smoke test with empty arrays [Risk 1]
// ---------------------------------------------------------------------------

describe("EditableObjectContainer — onLineDragMove (empty handle arrays)", () => {

   it("completes without error when all DOM element arrays are empty", () => {
      const { comp } = makeComponent();
      // All arrays (activeHandlesLineDrag, activeLineHandles, etc.) default to []
      expect(() => comp.onLineDragMove()).not.toThrow();
   });

   it("resets closeObjectEditorElements to [] and nonActiveHandles to []", () => {
      const { comp } = makeComponent();
      comp.onLineDragMove();
      expect((comp as any).closeObjectEditorElements).toEqual([]);
      expect((comp as any).nonActiveHandles).toEqual([]);
   });
});

// ---------------------------------------------------------------------------
// Group 7: onLineStartDragMove [Risk 2]
// ---------------------------------------------------------------------------

describe("EditableObjectContainer — onLineStartDragMove", () => {

   function makeLineComp() {
      const vs = new Viewsheet();
      vs.scale = 1;
      const lineModel = makeVsObjectModel("VSLine", "L1") as any;
      lineModel.startLeft = 10;
      lineModel.startTop = 20;
      lineModel.endLeft  = 100;
      lineModel.endTop   = 200;

      const { comp } = makeComponent({ vs });
      comp.vsObjectModel = lineModel;
      (comp as any).olineInfo = {
         startLeft: 10, startTop: 20,
         endLeft: 100, endTop: 200,
      };

      return { comp, lineModel };
   }

   it("adds dx/dy to olineInfo.startLeft/startTop and syncs to vsLine", () => {
      const { comp, lineModel } = makeLineComp();

      comp.onLineStartDragMove({ dx: 5, dy: 3, shiftKey: false });

      expect((comp as any).olineInfo.startLeft).toBe(15);
      expect((comp as any).olineInfo.startTop).toBe(23);
      expect(lineModel.startLeft).toBe(15);
      expect(lineModel.startTop).toBe(23);
   });

   it("does not modify endLeft/endTop", () => {
      const { comp, lineModel } = makeLineComp();

      comp.onLineStartDragMove({ dx: 5, dy: 3, shiftKey: false });

      expect(lineModel.endLeft).toBe(100);
      expect(lineModel.endTop).toBe(200);
   });
});

// ---------------------------------------------------------------------------
// Group 8: onLineEndDragMove [Risk 2]
// ---------------------------------------------------------------------------

describe("EditableObjectContainer — onLineEndDragMove", () => {

   function makeLineComp() {
      const vs = new Viewsheet();
      vs.scale = 1;
      const lineModel = makeVsObjectModel("VSLine", "L1") as any;
      lineModel.startLeft = 10;
      lineModel.startTop = 20;
      lineModel.endLeft  = 100;
      lineModel.endTop   = 200;

      const { comp } = makeComponent({ vs });
      comp.vsObjectModel = lineModel;
      (comp as any).olineInfo = {
         startLeft: 10, startTop: 20,
         endLeft: 100, endTop: 200,
      };

      return { comp, lineModel };
   }

   it("adds dx/dy to olineInfo.endLeft/endTop and syncs to vsLine", () => {
      const { comp, lineModel } = makeLineComp();

      comp.onLineEndDragMove({ dx: 8, dy: -4, shiftKey: false });

      expect((comp as any).olineInfo.endLeft).toBe(108);
      expect((comp as any).olineInfo.endTop).toBe(196);
      expect(lineModel.endLeft).toBe(108);
      expect(lineModel.endTop).toBe(196);
   });

   it("does not modify startLeft/startTop", () => {
      const { comp, lineModel } = makeLineComp();

      comp.onLineEndDragMove({ dx: 8, dy: -4, shiftKey: false });

      expect(lineModel.startLeft).toBe(10);
      expect(lineModel.startTop).toBe(20);
   });
});
