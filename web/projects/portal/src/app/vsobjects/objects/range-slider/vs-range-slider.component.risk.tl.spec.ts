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
 * VSRangeSlider – Pass 2: Risk / Edge Cases
 *
 * Coverage:
 *   Group 1  - submitOnChange=false: no sendEvent; updateState called; _unappliedSelections stored
 *   Group 2  - globalSubmit fires: flushes pending → sendEvent; no-op when nothing pending
 *   Group 3  - handleMoved clamping: returns 0 at lower bound, rangeLineWidth at upper bound
 *   Group 4  - moveMiddle edge backtracking: left/right clamping; selectStart/End updated
 *   Group 5  - mouseMove Middle handle: both handles shift; no change on zero movement
 *   Group 6  - mouseMove Left handle: selectStart updated; _leftHandlePosition updated
 *   Group 7  - mouseMove Right handle rollback: _rightHandlePosition restored when crossing left handle
 *   Group 8  - mouseUp quick-click (viewer): showRangeSliderEditDialog called in viewer context; skipped otherwise
 */

import { GuiTool } from "../../../common/util/gui-tool";
import {
   createVSRangeSlider,
} from "./vs-range-slider.component.test-helpers";

describe("VSRangeSlider – risk / edge cases (P2)", () => {
   beforeEach(() => {
      vi.spyOn(GuiTool, "measureText").mockReturnValue(10);
   });

   afterEach(() => {
      vi.restoreAllMocks();
   });

   // ─── Group 1: submitOnChange=false → deferred via globalSubmit ───────────

   describe("Group 1 – submitOnChange=false stores unapplied selection", () => {
      it("should NOT call sendEvent immediately when submitOnChange=false", () => {
         const { comp, viewsheetClient } = createVSRangeSlider({ submitOnChange: false });
         comp["startingSelectStart"] = comp.model.selectStart;
         comp["startingSelectEnd"] = comp.model.selectEnd;
         comp["mouseHandle"] = comp.handleType.Left;
         comp.isMouseDown = true;
         comp.model.selectStart = 0;

         comp.mouseUp(new MouseEvent("mouseup") as any);

         expect(viewsheetClient.sendEvent).not.toHaveBeenCalled();
      });

      it("should call globalSubmitService.updateState with the pending selection when submitOnChange=false", () => {
         const { comp, globalSubmitService } = createVSRangeSlider({ submitOnChange: false });
         comp["startingSelectStart"] = comp.model.selectStart;
         comp["startingSelectEnd"] = comp.model.selectEnd;
         comp["mouseHandle"] = comp.handleType.Left;
         comp.isMouseDown = true;
         comp.model.selectStart = 0;

         comp.mouseUp(new MouseEvent("mouseup") as any);

         expect(globalSubmitService.updateState).toHaveBeenCalledWith(
            comp.model.absoluteName,
            [{ start: 0, end: 3 }],
         );
      });

      it("should store unapplied selection so globalSubmit can flush it", () => {
         const { comp } = createVSRangeSlider({ submitOnChange: false });
         comp["startingSelectStart"] = comp.model.selectStart;
         comp["startingSelectEnd"] = comp.model.selectEnd;
         comp["mouseHandle"] = comp.handleType.Left;
         comp.isMouseDown = true;
         comp.model.selectStart = 0;

         comp.mouseUp(new MouseEvent("mouseup") as any);

         expect(comp["_unappliedSelections"]).toEqual({ start: 0, end: 3 });
      });
   });

   // ─── Group 2: globalSubmit fires → flushes unapplied selection ───────────

   describe("Group 2 – globalSubmit fires: flushes pending selection and sends event", () => {
      it("should call sendEvent when globalSubmit fires with a pending selection", () => {
         const { comp, viewsheetClient, globalSubmitSubject } = createVSRangeSlider({
            submitOnChange: false,
         });
         // Plant an unapplied selection directly (simulates a prior drag)
         comp["_unappliedSelections"] = { start: 0, end: 4 };

         globalSubmitSubject.next("RangeSlider1");

         expect(viewsheetClient.sendEvent).toHaveBeenCalledWith(
            expect.stringContaining("/events/selectionList/update/"),
            expect.anything(),
         );
      });

      it("should NOT call sendEvent when globalSubmit fires but no pending selection", () => {
         // no _unappliedSelections path — complement of test 1 above
         const { comp, viewsheetClient, globalSubmitSubject } = createVSRangeSlider({
            submitOnChange: false,
         });
         comp["_unappliedSelections"] = null;

         globalSubmitSubject.next("RangeSlider1");

         expect(viewsheetClient.sendEvent).not.toHaveBeenCalled();
      });
   });

   // ─── Group 3: handleMoved clamping ───────────────────────────────────────

   describe("Group 3 – handleMoved clamps at 0 and rangeLineWidth", () => {
      it("should return 0 when movement makes position go below 0", () => {
         const { comp } = createVSRangeSlider();
         // handlePosition=10, movement=-50 → 10-50=-40 → clamp to 0
         const result = comp.handleMoved(-50, 10);
         expect(result).toBe(0);
      });

      it("should return rangeLineWidth when movement makes position exceed rangeLineWidth", () => {
         const { comp } = createVSRangeSlider();
         // handlePosition=100, movement=200 → 300 > 191 → clamp to rangeLineWidth=191
         const result = comp.handleMoved(200, 100);
         expect(result).toBe(comp.rangeLineWidth);
      });

      it("should return unmodified position when movement stays within bounds", () => {
         const { comp } = createVSRangeSlider();
         // handlePosition=50, movement=30 → 80 (within 0..191)
         const result = comp.handleMoved(30, 50);
         expect(result).toBe(80);
      });
   });

   // ─── Group 4: moveMiddle edge backtracking ────────────────────────────────

   describe("Group 4 – moveMiddle backtracks at edges", () => {
      it("should backtrack right handle when left handle would go below 0", () => {
         const { comp } = createVSRangeSlider();
         // leftPos=47.75, rightPos=143.25 — move far left so left goes below 0
         comp["_leftHandlePosition"] = 10;
         comp["_rightHandlePosition"] = 60;
         comp.moveMiddle(-50);
         expect(comp["_leftHandlePosition"]).toBe(0);
      });

      it("should backtrack left handle when right handle would exceed rangeLineWidth", () => {
         const { comp } = createVSRangeSlider();
         // Move right so right exceeds rangeLineWidth=191
         comp["_leftHandlePosition"] = 140;
         comp["_rightHandlePosition"] = 185;
         comp.moveMiddle(50);
         expect(comp["_rightHandlePosition"]).toBe(comp.rangeLineWidth);
      });

      it("should update selectStart and selectEnd indices after moveMiddle", () => {
         const { comp } = createVSRangeSlider();
         comp["_leftHandlePosition"] = 47.75;
         comp["_rightHandlePosition"] = 143.25;
         // Move right by one tick width
         comp.moveMiddle(47.75);
         expect(comp.model.selectStart).toBe(2);
         expect(comp.model.selectEnd).toBe(4);
      });
   });

   // ─── Group 5: mouseMove Middle handle ────────────────────────────────────

   describe("Group 5 – mouseMove Middle handle delegates to moveMiddle", () => {
      it("should update both selectStart and selectEnd when Middle handle moves right", () => {
         const { comp } = createVSRangeSlider();
         comp["mouseHandle"] = comp.handleType.Middle;
         comp["startingXPosition"] = 0;
         const pageXSpy = vi.spyOn(GuiTool, "pageX" as any).mockReturnValue(47.75);
         try {
            comp.mouseMove(new MouseEvent("mousemove") as any);
            // movement=47.75 → moveMiddle(47.75): both handles shift right by 1 tick
            expect(comp.model.selectStart).toBe(2);
            expect(comp.model.selectEnd).toBe(4);
         } finally {
            pageXSpy.mockRestore();
         }
      });

      it("should not change selectStart/selectEnd when Middle handle has zero movement", () => {
         const { comp } = createVSRangeSlider();
         comp["mouseHandle"] = comp.handleType.Middle;
         comp["startingXPosition"] = 0;
         const pageXSpy = vi.spyOn(GuiTool, "pageX" as any).mockReturnValue(0);
         try {
            comp.mouseMove(new MouseEvent("mousemove") as any);
            // movement=0 → moveMiddle(0) → no change
            expect(comp.model.selectStart).toBe(1);
            expect(comp.model.selectEnd).toBe(3);
         } finally {
            pageXSpy.mockRestore();
         }
      });
   });

   // ─── Group 6: mouseMove Left handle ───────────────────────────────────────

   describe("Group 6 – mouseMove Left handle updates selectStart", () => {
      it("should update selectStart when Left handle moves right by 1 tick", () => {
         const { comp } = createVSRangeSlider();
         comp["mouseHandle"] = comp.handleType.Left;
         comp["startingXPosition"] = 0;
         const pageXSpy = vi.spyOn(GuiTool, "pageX" as any).mockReturnValue(47.75);
         try {
            comp.mouseMove(new MouseEvent("mousemove") as any);
            // movement = 47.75, leftPos = 47.75 + 47.75 = 95.5
            // selectStart = round(95.5 / 47.75) = 2
            expect(comp.model.selectStart).toBe(2);
         } finally {
            pageXSpy.mockRestore();
         }
      });

      it("should update _leftHandlePosition after mouseMove Left", () => {
         const { comp } = createVSRangeSlider();
         comp["mouseHandle"] = comp.handleType.Left;
         comp["startingXPosition"] = 0;
         const pageXSpy = vi.spyOn(GuiTool, "pageX" as any).mockReturnValue(47.75);
         try {
            comp.mouseMove(new MouseEvent("mousemove") as any);
            expect(comp["_leftHandlePosition"]).toBeCloseTo(95.5, 1);
         } finally {
            pageXSpy.mockRestore();
         }
      });
   });

   // ─── Group 7: mouseMove Right handle rollback ─────────────────────────────

   describe("Group 7 – mouseMove Right handle rollback when crossing left handle", () => {
      it("should restore _rightHandlePosition when movement would cross left handle", () => {
         const { comp } = createVSRangeSlider();
         const originalRightPos = comp["_rightHandlePosition"]; // 143.25
         comp["mouseHandle"] = comp.handleType.Right;
         comp["startingXPosition"] = 200;
         const pageXSpy = vi.spyOn(GuiTool, "pageX" as any).mockReturnValue(0);
         try {
            comp.mouseMove(new MouseEvent("mousemove") as any);
            // movement = 0 - 200 = -200, rightPos clamped to 0, selectEnd=0
            // selectStart(1) >= selectEnd(0) → rollback restores _rightHandlePosition
            expect(comp["_rightHandlePosition"]).toBeCloseTo(originalRightPos, 1);
         } finally {
            pageXSpy.mockRestore();
         }
      });

      it("should ensure selectEnd stays above selectStart after rollback", () => {
         const { comp } = createVSRangeSlider();
         comp["mouseHandle"] = comp.handleType.Right;
         comp["startingXPosition"] = 200;
         const pageXSpy = vi.spyOn(GuiTool, "pageX" as any).mockReturnValue(0);
         try {
            comp.mouseMove(new MouseEvent("mousemove") as any);
            // rollback: this.model.selectEnd += 1 (0 → 1); source: "this.model.selectEnd += 1"
            expect(comp.model.selectEnd).toBe(1);
         } finally {
            pageXSpy.mockRestore();
         }
      });
   });

   // ─── Group 8: mouseUp quick-click in viewer context ───────────────────────

   describe("Group 8 – mouseUp quick-click in viewer context opens edit dialog", () => {
      it("should call showRangeSliderEditDialog when viewer + quick click + no drag", () => {
         const { comp } = createVSRangeSlider({}, { viewer: true });
         comp["mouseHandle"] = comp.handleType.Left;
         comp["startingSelectStart"] = comp.model.selectStart;
         comp["startingSelectEnd"] = comp.model.selectEnd;
         // D5 note: Date.now() is inside an it block (not a factory/constant), so it is allowed
         comp["timeHandleClicked"] = Date.now();
         comp.isMouseDown = true;

         const dialogSpy = vi.spyOn(comp as any, "showRangeSliderEditDialog")
            .mockImplementation(() => {});
         try {
            comp.mouseUp(new MouseEvent("mouseup") as any);
            expect(dialogSpy).toHaveBeenCalledTimes(1);
         } finally {
            dialogSpy.mockRestore();
         }
      });

      it("should NOT call showRangeSliderEditDialog when not in viewer/preview context", () => {
         // viewer=false (default factory) → dialog path skipped entirely
         // Complement: viewer=true path covered by test 1 above
         const { comp } = createVSRangeSlider();
         comp["mouseHandle"] = comp.handleType.Left;
         comp["startingSelectStart"] = comp.model.selectStart;
         comp["startingSelectEnd"] = comp.model.selectEnd;
         comp["timeHandleClicked"] = Date.now();
         comp.isMouseDown = true;

         const dialogSpy = vi.spyOn(comp as any, "showRangeSliderEditDialog")
            .mockImplementation(() => {});
         try {
            comp.mouseUp(new MouseEvent("mouseup") as any);
            expect(dialogSpy).not.toHaveBeenCalled();
         } finally {
            dialogSpy.mockRestore();
         }
      });
   });
});
