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
 * PagingControlComponent — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] — pageDown/pageUp: scrollTopChange must emit a clamped value; wrong
 *                       arithmetic causes over-scroll (past content) or under-scroll (past 0)
 *   Group 2 [Risk 2] — pageRight/pageLeft: same clamping contract for the horizontal axis
 *   Group 3 [Risk 2] — scroll enabled getters: all four getters must return false at their
 *                       exact boundary; a wrong operator (< vs <=) silently disables or
 *                       enables the button at the boundary position
 *   Group 4 [Risk 1] — startXPosition/startYPosition: moved flag selects between startX/Y
 *                       and movedX/Y
 *   Group 5 [Risk 1] — ngOnDestroy: touchmove listener must be removed to prevent memory leak
 *   Group 6 [Risk 2] — moveListener: -40 offset arithmetic and inViewport/multi-touch guards;
 *                       wrong offset misaligns the drag handle; missing guard lets out-of-viewport
 *                       or multi-touch events corrupt the position
 *
 * Confirmed bugs: none
 *
 * Out of scope:
 *   ngAfterViewInit — addEventListener call is the mirror of the ngOnDestroy test; verified
 *                     transitively when the listener removal test passes
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";

import { PagingControlComponent } from "./paging-control.component";
import { PagingControlService } from "../../common/services/paging-control.service";

const PAGING_SERVICE_MOCK = { inViewport: vi.fn().mockReturnValue(true) };

beforeEach(() => {
   PAGING_SERVICE_MOCK.inViewport.mockClear();
   PAGING_SERVICE_MOCK.inViewport.mockReturnValue(true);
});

interface RenderOpts {
   viewportWidth?: number;
   viewportHeight?: number;
   contentWidth?: number;
   contentHeight?: number;
   scrollTop?: number;
   scrollLeft?: number;
   startX?: number;
   startY?: number;
}

async function renderComp(opts: RenderOpts = {}) {
   const { fixture } = await render(PagingControlComponent, {
      providers: [{ provide: PagingControlService, useValue: PAGING_SERVICE_MOCK }],
      schemas: [NO_ERRORS_SCHEMA],
      componentInputs: {
         viewportWidth: opts.viewportWidth ?? 200,
         viewportHeight: opts.viewportHeight ?? 100,
         contentWidth: opts.contentWidth ?? 600,
         contentHeight: opts.contentHeight ?? 400,
         scrollTop: opts.scrollTop ?? 150,
         scrollLeft: opts.scrollLeft ?? 150,
         startX: opts.startX ?? 10,
         startY: opts.startY ?? 20,
      },
   });
   return { comp: fixture.componentInstance, fixture };
}

// ---------------------------------------------------------------------------
// Group 1: pageDown / pageUp — vertical scroll arithmetic
// ---------------------------------------------------------------------------

describe("PagingControlComponent — pageDown / pageUp", () => {

   // 🔁 Regression-sensitive: the -5/+5 overlap and Math.min/max clamps are the exact
   // boundary contract; an off-by-one here over- or under-scrolls the content pane.
   it("pageDown: should emit scrollTop + viewportHeight - 5 in the normal case", async () => {
      // Math.min(150 + 100 - 5, 400 - 100) = Math.min(245, 300) = 245
      const { comp } = await renderComp({ scrollTop: 150, viewportHeight: 100, contentHeight: 400 });
      const spy = vi.fn();
      comp.scrollTopChange.subscribe(spy);
      comp.pageDown();
      expect(spy).toHaveBeenCalledWith(245);
   });

   it("pageDown: should clamp to contentHeight - viewportHeight when near the bottom", async () => {
      // Math.min(250 + 100 - 5, 400 - 100) = Math.min(345, 300) = 300
      const { comp } = await renderComp({ scrollTop: 250, viewportHeight: 100, contentHeight: 400 });
      const spy = vi.fn();
      comp.scrollTopChange.subscribe(spy);
      comp.pageDown();
      expect(spy).toHaveBeenCalledWith(300);
   });

   it("pageUp: should emit scrollTop - viewportHeight + 5 in the normal case", async () => {
      // Math.max(150 - 100 + 5, 0) = Math.max(55, 0) = 55
      const { comp } = await renderComp({ scrollTop: 150, viewportHeight: 100 });
      const spy = vi.fn();
      comp.scrollTopChange.subscribe(spy);
      comp.pageUp();
      expect(spy).toHaveBeenCalledWith(55);
   });

   it("pageUp: should clamp to 0 when near the top", async () => {
      // Math.max(50 - 100 + 5, 0) = Math.max(-45, 0) = 0
      const { comp } = await renderComp({ scrollTop: 50, viewportHeight: 100 });
      const spy = vi.fn();
      comp.scrollTopChange.subscribe(spy);
      comp.pageUp();
      expect(spy).toHaveBeenCalledWith(0);
   });
});

// ---------------------------------------------------------------------------
// Group 2: pageRight / pageLeft — horizontal scroll arithmetic
// ---------------------------------------------------------------------------

describe("PagingControlComponent — pageRight / pageLeft", () => {

   // 🔁 Regression-sensitive: same clamping contract as vertical; asymmetric bugs
   // between horizontal and vertical are common after partial refactors.
   it("pageRight: should emit scrollLeft + viewportWidth - 5 in the normal case", async () => {
      // Math.min(150 + 200 - 5, 600 - 200) = Math.min(345, 400) = 345
      const { comp } = await renderComp({ scrollLeft: 150, viewportWidth: 200, contentWidth: 600 });
      const spy = vi.fn();
      comp.scrollLeftChange.subscribe(spy);
      comp.pageRight();
      expect(spy).toHaveBeenCalledWith(345);
   });

   it("pageRight: should clamp to contentWidth - viewportWidth when near the right edge", async () => {
      // Math.min(280 + 200 - 5, 600 - 200) = Math.min(475, 400) = 400
      const { comp } = await renderComp({ scrollLeft: 280, viewportWidth: 200, contentWidth: 600 });
      const spy = vi.fn();
      comp.scrollLeftChange.subscribe(spy);
      comp.pageRight();
      expect(spy).toHaveBeenCalledWith(400);
   });

   it("pageLeft: should emit scrollLeft - viewportWidth + 5 in the normal case", async () => {
      // Math.max(250 - 200 + 5, 0) = Math.max(55, 0) = 55
      const { comp } = await renderComp({ scrollLeft: 250, viewportWidth: 200 });
      const spy = vi.fn();
      comp.scrollLeftChange.subscribe(spy);
      comp.pageLeft();
      expect(spy).toHaveBeenCalledWith(55);
   });

   it("pageLeft: should clamp to 0 when near the left edge", async () => {
      // Math.max(100 - 200 + 5, 0) = Math.max(-95, 0) = 0
      const { comp } = await renderComp({ scrollLeft: 100, viewportWidth: 200 });
      const spy = vi.fn();
      comp.scrollLeftChange.subscribe(spy);
      comp.pageLeft();
      expect(spy).toHaveBeenCalledWith(0);
   });
});

// ---------------------------------------------------------------------------
// Group 3: scroll enabled getters — boundary detection
// ---------------------------------------------------------------------------

describe("PagingControlComponent — scroll enabled getters", () => {

   // 🔁 Regression-sensitive: these getters use strict < which means scrollTop + viewportHeight
   // == contentHeight is at-boundary and should return false. Changing < to <= would hide
   // the down-scroll button one row too early. Both true and false cases are required —
   // testing only false allows a "return false" mutation to pass undetected.
   it("downScrollEnabled: should be true when content extends beyond the viewport bottom", async () => {
      const { comp } = await renderComp({ scrollTop: 150, viewportHeight: 100, contentHeight: 400 });
      expect(comp.downScrollEnabled).toBe(true);
   });

   it("downScrollEnabled: should be false when scrollTop + viewportHeight equals contentHeight", async () => {
      const { comp } = await renderComp({ scrollTop: 300, viewportHeight: 100, contentHeight: 400 });
      expect(comp.downScrollEnabled).toBe(false);
   });

   it("upScrollEnabled: should be true when scrollTop is greater than 0", async () => {
      const { comp } = await renderComp({ scrollTop: 150 });
      expect(comp.upScrollEnabled).toBe(true);
   });

   it("upScrollEnabled: should be false when scrollTop is 0", async () => {
      const { comp } = await renderComp({ scrollTop: 0 });
      expect(comp.upScrollEnabled).toBe(false);
   });

   it("rightScrollEnabled: should be true when content extends beyond the viewport right edge", async () => {
      const { comp } = await renderComp({ scrollLeft: 150, viewportWidth: 200, contentWidth: 600 });
      expect(comp.rightScrollEnabled).toBe(true);
   });

   it("rightScrollEnabled: should be false when scrollLeft + viewportWidth equals contentWidth", async () => {
      const { comp } = await renderComp({ scrollLeft: 400, viewportWidth: 200, contentWidth: 600 });
      expect(comp.rightScrollEnabled).toBe(false);
   });

   it("leftScrollEnabled: should be true when scrollLeft is greater than 0", async () => {
      const { comp } = await renderComp({ scrollLeft: 150 });
      expect(comp.leftScrollEnabled).toBe(true);
   });

   it("leftScrollEnabled: should be false when scrollLeft is 0", async () => {
      const { comp } = await renderComp({ scrollLeft: 0 });
      expect(comp.leftScrollEnabled).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 4: startXPosition / startYPosition
// ---------------------------------------------------------------------------

describe("PagingControlComponent — startXPosition / startYPosition", () => {

   it("startXPosition: should return startX when not moved", async () => {
      const { comp } = await renderComp({ startX: 10 });
      expect(comp.startXPosition).toBe(10);
   });

   it("startXPosition: should return movedX when moved", async () => {
      const { comp } = await renderComp();
      comp.moved = true;
      comp.movedX = 80;
      expect(comp.startXPosition).toBe(80);
   });

   it("startYPosition: should return startY when not moved", async () => {
      const { comp } = await renderComp({ startY: 20 });
      expect(comp.startYPosition).toBe(20);
   });

   it("startYPosition: should return movedY when moved", async () => {
      const { comp } = await renderComp();
      comp.moved = true;
      comp.movedY = 60;
      expect(comp.startYPosition).toBe(60);
   });
});

// ---------------------------------------------------------------------------
// Group 6: moveListener — offset arithmetic and guards
// ---------------------------------------------------------------------------

describe("PagingControlComponent — moveListener", () => {

   // 🔁 Regression-sensitive: preventDefault/stopPropagation must fire unconditionally on
   // every touch event to prevent the page from scrolling while the paging handle is dragged.
   it("should call preventDefault and stopPropagation on every single-touch event", async () => {
      const { comp } = await renderComp();
      const mockEvent = {
         preventDefault: vi.fn(),
         stopPropagation: vi.fn(),
         targetTouches: [{ pageX: 120, pageY: 160 }],
      } as unknown as TouchEvent;

      comp.moveListener(mockEvent);

      expect(mockEvent.preventDefault).toHaveBeenCalled();
      expect(mockEvent.stopPropagation).toHaveBeenCalled();
   });

   // 🔁 Regression-sensitive: the -40 offset positions the drag handle relative to the
   // finger; changing this value shifts the handle off-center. inViewport guards against
   // updating coordinates when the finger is outside the viewport.
   it("should set moved=true and update movedX/movedY with -40 offset when inViewport returns true", async () => {
      const { comp } = await renderComp();
      const mockEvent = {
         preventDefault: vi.fn(),
         stopPropagation: vi.fn(),
         targetTouches: [{ pageX: 120, pageY: 160 }],
      } as unknown as TouchEvent;

      comp.moveListener(mockEvent);

      expect(PAGING_SERVICE_MOCK.inViewport).toHaveBeenCalledWith({ pageX: 120, pageY: 160 }, true);
      expect(comp.moved).toBe(true);
      expect(comp.movedX).toBe(80);   // 120 - 40
      expect(comp.movedY).toBe(120);  // 160 - 40
   });

   it("should set moved=true but not update movedX/movedY when inViewport returns false", async () => {
      const { comp } = await renderComp();
      PAGING_SERVICE_MOCK.inViewport.mockReturnValue(false);
      const mockEvent = {
         preventDefault: vi.fn(),
         stopPropagation: vi.fn(),
         targetTouches: [{ pageX: 120, pageY: 160 }],
      } as unknown as TouchEvent;

      comp.moveListener(mockEvent);

      expect(comp.moved).toBe(true);
      expect(comp.movedX).toBeUndefined();
      expect(comp.movedY).toBeUndefined();
   });

   it("should not update any state when targetTouches has more than one touch point", async () => {
      const { comp } = await renderComp();
      const mockEvent = {
         preventDefault: vi.fn(),
         stopPropagation: vi.fn(),
         targetTouches: [{ pageX: 120, pageY: 160 }, { pageX: 200, pageY: 200 }],
      } as unknown as TouchEvent;

      comp.moveListener(mockEvent);

      expect(comp.moved).toBe(false);
      expect(comp.movedX).toBeUndefined();
      expect(comp.movedY).toBeUndefined();
      expect(PAGING_SERVICE_MOCK.inViewport).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 5: ngOnDestroy — touchmove listener cleanup
// ---------------------------------------------------------------------------

describe("PagingControlComponent — ngOnDestroy listener cleanup", () => {

   // 🔁 Regression-sensitive: the constructor captures moveListener as an arrow-function
   // property so the same reference is used for both add and remove. If someone extracts
   // an inline function instead, removeEventListener receives a different reference and the
   // listener leaks, continuing to mutate movedX/movedY on a destroyed instance.
   // Expected failure mode: if ngOnDestroy is removed or the reference changes,
   // removeEventListener is not called with the correct listener.
   it("should remove the touchmove listener from document on destroy", async () => {
      const removeSpy = vi.spyOn(document, "removeEventListener");
      try {
         const { comp, fixture } = await renderComp();
         fixture.destroy();
         expect(removeSpy).toHaveBeenCalledWith("touchmove", comp.moveListener);
      } finally {
         removeSpy.mockRestore();
      }
   });
});
