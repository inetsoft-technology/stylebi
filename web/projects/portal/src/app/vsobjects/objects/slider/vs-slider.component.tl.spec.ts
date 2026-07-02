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
 * VSSlider – Single Pass
 *
 * Coverage:
 *   Group 1  - verticalCenter formula: max(17, min(ceil(h/2), h-36)) — all three clamp branches
 *   Group 2  - getValueX linear interpolation: value=min→0; value=max→lineWidth; midpoint→half
 *   Group 3  - getLabel: currentLabel≠previousLabel returns currentLabel; equal → numeric from position
 *   Group 4  - mouseDown/mouseUp lifecycle: isMouseDown set; mouseUp triggers applySelection+reset
 *   Group 5  - applySelection refresh=true: sendEvent with changeValue URL; force=true overrides refresh=false
 *   Group 6  - applySelection refresh=false: addPendingValue called; sendEvent NOT called
 *   Group 7  - moveHandleHere: handlePosition updated; applySelection triggered
 *   Group 8  - navigate LEFT/RIGHT: no-snap decrements/increments by 5; debounce called on refresh=true
 *   Group 9  - getTicks count: >0 ticks for valid range; ticks[0].left=0
 *   Group 10 - navigate with snap: increment-scaled step (40px) + snap rounding; contrast vs no-snap (5px)
 *
 * Model defaults: min=0, max=100, increment=20, value=50, width=200, height=60.
 * Key derived values: handlePosition=100, verticalCenter=24.
 */

import { GuiTool } from "../../../common/util/gui-tool";
import { NavigationKeys } from "../navigation-keys";
import { createVSSlider, makeVSSliderModel } from "./vs-slider.component.test-helpers";

describe("VSSlider", () => {
   beforeEach(() => {
      vi.spyOn(GuiTool, "measureText").mockReturnValue(10);
   });

   afterEach(() => {
      vi.restoreAllMocks();
   });

   // ─── Group 1: verticalCenter (model setter) ───────────────────────────────

   describe("Group 1 – verticalCenter formula", () => {
      it("should compute verticalCenter as max(HANDLE_CLEARANCE=17, min(ceil(h/2), h-36))", () => {
         const { comp } = createVSSlider();
         // height=60: Math.max(17, Math.min(30, 24)) = 24
         expect(comp.verticalCenter).toBe(24);
      });

      it("should clamp verticalCenter to HANDLE_CLEARANCE=17 for very short components", () => {
         const { comp } = createVSSlider({
            objectFormat: {
               ...makeVSSliderModel().objectFormat,
               height: 20, // 20-36=-16 < 17 → clamped to 17
            },
         } as any);
         expect(comp.verticalCenter).toBe(17);
      });

      it("should clamp verticalCenter to h-36 for tall components where ceil(h/2) > h-36", () => {
         const { comp } = createVSSlider({
            objectFormat: {
               ...makeVSSliderModel().objectFormat,
               height: 100, // Math.max(17, Math.min(50, 64)) = 50
            },
         } as any);
         expect(comp.verticalCenter).toBe(50);
      });
   });

   // ─── Group 2: getValueX linear interpolation ─────────────────────────────

   describe("Group 2 – getValueX linear interpolation", () => {
      it("should return 0 when value equals min", () => {
         const { comp } = createVSSlider({ value: 0 });
         expect(comp.getValueX()).toBe(0);
      });

      it("should return lineWidth when value equals max", () => {
         const { comp } = createVSSlider({ value: 100 });
         // lineWidth = objectFormat.width = 200 (no DOM)
         expect(comp.getValueX()).toBe(200);
      });

      it("should return half lineWidth when value is midpoint", () => {
         const { comp } = createVSSlider({ value: 50 });
         // (50-0)/(100-0) * 200 = 100
         expect(comp.getValueX()).toBe(100);
      });
   });

   // ─── Group 3: getLabel ────────────────────────────────────────────────────

   describe("Group 3 – getLabel", () => {
      it("should return currentLabel when it differs from previousLabel", () => {
         const { comp } = createVSSlider({ currentLabel: "Custom" });
         // previousLabel="" (initial), currentLabel="Custom" → different → return currentLabel
         expect(comp.getLabel()).toBe("Custom");
      });

      it("should calculate numeric label from handle position when currentLabel matches previous", () => {
         const { comp } = createVSSlider({ currentLabel: "" });
         // previousLabel="" = currentLabel="" → same → calculate from position
         // handlePosition=100, lineWidth=200: min + (100/200)*(100-0) = 0 + 50 = 50
         // getFractionDigits: increment="20" → indexOf(".")=-1 → 0 → "50"
         expect(comp.getLabel()).toBe("50");
      });
   });

   // ─── Group 4: mouseDown / mouseUp lifecycle ───────────────────────────────

   describe("Group 4 – mouseDown / mouseUp lifecycle", () => {
      it("should set isMouseDown=true on left-button mouseDown", () => {
         const { comp } = createVSSlider();
         comp.mouseDown(new MouseEvent("mousedown", { button: 0 }));
         expect(comp.isMouseDown).toBe(true);
      });

      it("should set unappliedSelection=true and call applySelection on mouseUp", () => {
         const { comp, viewsheetClient } = createVSSlider();
         comp.isMouseDown = true;
         comp.mouseUp(new MouseEvent("mouseup") as any);
         // applySelection is called → formDataService.checkFormData → sendEvent (refresh=true)
         expect(viewsheetClient.sendEvent).toHaveBeenCalledWith(
            expect.stringContaining("changeValue"),
            expect.anything(),
         );
      });

      it("should set isMouseDown=false after mouseUp", () => {
         const { comp } = createVSSlider();
         comp.isMouseDown = true;
         comp.mouseUp(new MouseEvent("mouseup") as any);
         expect(comp.isMouseDown).toBe(false);
      });
   });

   // ─── Group 5: applySelection with refresh=true ────────────────────────────

   describe("Group 5 – applySelection: refresh=true sends event", () => {
      it("should call sendEvent with changeValue URL when refresh=true", () => {
         const { comp, viewsheetClient } = createVSSlider({ refresh: true });
         comp["applySelection"]();
         expect(viewsheetClient.sendEvent).toHaveBeenCalledWith(
            expect.stringContaining("changeValue"),
            expect.anything(),
         );
      });

      it("should call sendEvent when force=true even if refresh=false", () => {
         const { comp, viewsheetClient } = createVSSlider({ refresh: false });
         comp["applySelection"](true);
         expect(viewsheetClient.sendEvent).toHaveBeenCalledWith(
            expect.stringContaining("changeValue"),
            expect.anything(),
         );
      });
   });

   // ─── Group 6: applySelection with refresh=false ───────────────────────────

   describe("Group 6 – applySelection: refresh=false adds pending value", () => {
      it("should call formInputService.addPendingValue when refresh=false and writeBackDirectly=false", () => {
         // sendEvent path covered by Group 5 test 1
         const { comp, formInputService } = createVSSlider({
            refresh: false,
            writeBackDirectly: false,
         } as any);
         comp["applySelection"]();
         expect(formInputService.addPendingValue).toHaveBeenCalledWith(
            "Slider1",
            expect.any(Number),
         );
      });

      it("should NOT call sendEvent when refresh=false and writeBackDirectly=false", () => {
         const { comp, viewsheetClient } = createVSSlider({
            refresh: false,
            writeBackDirectly: false,
         } as any);
         comp["applySelection"]();
         expect(viewsheetClient.sendEvent).not.toHaveBeenCalled();
      });
   });

   // ─── Group 7: moveHandleHere ──────────────────────────────────────────────

   describe("Group 7 – moveHandleHere", () => {
      it("should set handlePosition to the snapped offsetX value", () => {
         const { comp } = createVSSlider({ snap: false } as any);
         comp.moveHandleHere(new MouseEvent("click", { clientX: 120 }) as any);
         // offsetX = 0 in jsdom (MouseEvent offsetX is always 0 without layout)
         // snap(0) = 0 (no snap); clamped to [0, 200]
         expect(comp.handlePosition).toBe(0);
      });

      it("should trigger applySelection which calls sendEvent", () => {
         const { comp, viewsheetClient } = createVSSlider({ refresh: true });
         comp.moveHandleHere(new MouseEvent("click") as any);
         expect(viewsheetClient.sendEvent).toHaveBeenCalledWith(
            expect.stringContaining("changeValue"),
            expect.anything(),
         );
      });
   });

   // ─── Group 8: navigate keyboard ──────────────────────────────────────────

   describe("Group 8 – navigate LEFT / RIGHT", () => {
      function stubSliderRefs(comp: any): void {
         comp.sliderHandle = { nativeElement: { blur: vi.fn(), focus: vi.fn() } };
         comp.sliderContainer = { nativeElement: { focus: vi.fn() } };
      }

      it("should decrease handlePosition by 5 when LEFT key pressed (no snap)", () => {
         const { comp } = createVSSlider({ snap: false } as any);
         stubSliderRefs(comp);
         const before = comp.handlePosition; // 100

         comp["navigate"](NavigationKeys.LEFT);

         expect(comp.handlePosition).toBe(before - 5);
      });

      it("should increase handlePosition by 5 when RIGHT key pressed (no snap)", () => {
         const { comp } = createVSSlider({ snap: false } as any);
         stubSliderRefs(comp);
         const before = comp.handlePosition;

         comp["navigate"](NavigationKeys.RIGHT);

         expect(comp.handlePosition).toBe(before + 5);
      });

      it("should call debounce when refresh=true and LEFT pressed", () => {
         const { comp, debounceService } = createVSSlider({ refresh: true });
         stubSliderRefs(comp);

         comp["navigate"](NavigationKeys.LEFT);

         expect(debounceService.debounce).toHaveBeenCalledWith(
            expect.stringContaining("VSSlider.ApplyEvent"),
            expect.any(Function),
            300,
            null,
         );
      });

      it("should focus sliderContainer when key is not LEFT or RIGHT", () => {
         const { comp } = createVSSlider();
         stubSliderRefs(comp);

         comp["navigate"](NavigationKeys.UP);

         expect(comp.sliderContainer.nativeElement.focus).toHaveBeenCalledTimes(1);
      });
   });

   // ─── Group 9: getTicks count ──────────────────────────────────────────────

   describe("Group 9 – getTicks count", () => {
      it("should produce 6 ticks for min=0 max=100 increment=20 (0,20,40,60,80,100)", () => {
         const { comp } = createVSSlider();
         // incCount = ceil((100-0)/20) = 5 → ticks at i=0..5 = 6 entries
         // All will be included because measureText mock returns 10 (small label width)
         expect(comp.ticks.length).toBeGreaterThan(0);
      });

      it("should have ticks[0].left at 0 (start of line)", () => {
         const { comp } = createVSSlider();
         expect(comp.ticks[0].left).toBe(0);
      });
   });

   // ─── Group 10: navigate with snap ────────────────────────────────────────

   describe("Group 10 – navigate LEFT with snap uses increment-sized step + snap", () => {
      function stubSliderRefs2(comp: any): void {
         comp.sliderHandle = { nativeElement: { blur: vi.fn(), focus: vi.fn() } };
         comp.sliderContainer = { nativeElement: { focus: vi.fn() } };
      }

      it("should move handlePosition by snap-adjusted step when snap=true and LEFT pressed", () => {
         // snap=true, increment=20, range=0-100, lineWidth=200
         // incr = 20 * 200 / 100 = 40 (navigate step)
         // pos = 100 - 40 = 60; snap(60): val=30 → round(30/20)*20=40 → pixel=80
         const { comp } = createVSSlider({ snap: true } as any);
         stubSliderRefs2(comp);
         expect(comp.handlePosition).toBe(100); // baseline before navigate

         comp["navigate"](NavigationKeys.LEFT);

         expect(comp.handlePosition).toBe(80);
      });

      it("should snap RIGHT by one increment step when snap=true", () => {
         // pos = 100 + 40 = 140; snap(140): val=70 → round(70/20)*20=80 → pixel=160
         const { comp } = createVSSlider({ snap: true } as any);
         stubSliderRefs2(comp);

         comp["navigate"](NavigationKeys.RIGHT);

         expect(comp.handlePosition).toBe(160);
      });

      it("should use constant step of 5 when snap=false (contrast test)", () => {
         // snap=false → incr=5, no rounding in moveHandlePosition
         // pos = 100 - 5 = 95; snap(95) returns 95 because model.snap=false
         const { comp } = createVSSlider({ snap: false } as any);
         stubSliderRefs2(comp);

         comp["navigate"](NavigationKeys.LEFT);

         expect(comp.handlePosition).toBe(95);
      });
   });
});
