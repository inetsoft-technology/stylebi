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
 * ChartAxisArea — Pass 2: Risk
 *
 * Covers the items explicitly deferred from Pass 1 (see that file's header):
 *   - updateCanvas() context-gating (chartService.clearCanvas + debounceService.debounce
 *     wiring) and the debounce->ngAfterViewInit->drawSelectedRegions chain not throwing
 *     when chartSelection is unset.
 *   - onDown's document "mouseup" listener: the ACTUAL handler passed to renderer.listen
 *     (not just that listen was called), plus the register/unregister/re-register
 *     lifecycle across a resize-start -> resize-end -> resize-start sequence.
 *   - onMove/onLeave dataTip debounce: Pass 1 only checked that debounceService.debounce
 *     was called with the right key; this pass verifies the debounced callback actually
 *     produces (or correctly suppresses) the showDataTip emission.
 *
 * Uses the same TestBed.runInInjectionContext direct-instantiation + pointer-event-type
 * conventions documented in chart-axis-area.component.interaction.tl.spec.ts.
 */

import { GuiTool } from "../../common/util/gui-tool";
import { ChartTool } from "../model/chart-tool";
import { createComponent, makeAxis } from "./chart-axis-area.component.test-helpers";

// drawSelectedRegions() (invoked via the updateCanvas -> debounce -> ngAfterViewInit chain)
// schedules a setTimeout(..., 0) that dereferences the canvas context. Fake timers keep that
// callback from firing after the test's mocks are torn down (which would otherwise crash with
// "Cannot read properties of undefined" from the restored real getContext()). [C7]
beforeEach(() => vi.useFakeTimers());

afterEach(() => {
   vi.clearAllTimers();
   vi.useRealTimers();
   vi.restoreAllMocks();
   document.body.innerHTML = "";
});

function setupCanvasWithContext(comp: any) {
   const canvas = document.createElement("canvas");
   const ctx = {} as CanvasRenderingContext2D;
   vi.spyOn(canvas, "getContext").mockReturnValue(ctx as any);
   comp._objectCanvas = { nativeElement: canvas };
   return ctx;
}

// ---------------------------------------------------------------------------
// Group 1: updateCanvas context-gating [Risk 2]
// ---------------------------------------------------------------------------

describe("ChartAxisArea — updateCanvas (via scrollTop/scrollLeft setters)", () => {
   it("should clear the canvas and debounce a redraw when a context is available", () => {
      const { comp, chartService, debounceService } = createComponent({ axis: makeAxis({ axisType: "y" }) });
      const ctx = setupCanvasWithContext(comp);
      chartService.clearCanvas.mockClear();
      debounceService.debounce.mockClear();

      comp.scrollTop = 5;

      expect(chartService.clearCanvas).toHaveBeenCalledWith(ctx);
      expect(debounceService.debounce).toHaveBeenCalledWith(
         "axis.scrolled", expect.any(Function), 500, []
      );
   });

   it("should NOT touch chartService/debounceService when there is no canvas context", () => {
      const { comp, chartService, debounceService } = createComponent({ axis: makeAxis({ axisType: "y" }) });
      chartService.clearCanvas.mockClear();
      debounceService.debounce.mockClear();

      comp.scrollTop = 5;

      expect(chartService.clearCanvas).not.toHaveBeenCalled();
      expect(debounceService.debounce).not.toHaveBeenCalled();
   });

   it("should not throw when the debounced redraw (ngAfterViewInit -> drawSelectedRegions) runs without a chartSelection", () => {
      const { comp } = createComponent({ axis: makeAxis({ axisType: "y" }) });
      setupCanvasWithContext(comp);
      expect(comp.chartSelection).toBeUndefined();

      // debounceService mock runs the callback synchronously, so this exercises the
      // real ngAfterViewInit -> drawSelectedRegions chain with an unset chartSelection.
      expect(() => comp.scrollTop = 5).not.toThrow();
   });

   it("should NOT update scrollTop or touch the canvas when chartObject is not yet set", () => {
      const { comp, chartService } = createComponent({ axis: makeAxis({ axisType: "y" }) });
      setupCanvasWithContext(comp);
      (comp as any)._chartObject = undefined;
      chartService.clearCanvas.mockClear();

      comp.scrollTop = 5;

      expect(comp.scrollTop).toBe(0);
      expect(chartService.clearCanvas).not.toHaveBeenCalled();
   });
});

describe("ChartAxisArea — ngOnChanges calls updateCanvas at most once", () => {
   it("should call updateCanvas exactly once when only scrollTop changes on a y axis", () => {
      const { comp } = createComponent({ axis: makeAxis({ axisType: "y" }) });
      const updateCanvasSpy = vi.spyOn(comp as any, "updateCanvas");
      const change = { currentValue: 5, previousValue: 0, firstChange: false, isFirstChange: () => false };

      comp.ngOnChanges({ scrollTop: change, scrollLeft: change });

      // axisType is "y", so only the scrollTop branch applies -> exactly one call.
      expect(updateCanvasSpy).toHaveBeenCalledTimes(1);
   });
});

// ---------------------------------------------------------------------------
// Group 2: onDown document mouseup listener lifecycle [Risk 3]
// ---------------------------------------------------------------------------

describe("ChartAxisArea — document mouseup listener lifecycle", () => {
   function startResize(comp: any) {
      comp.resizeCursor = "row-resize";
      vi.spyOn(ChartTool, "getTreeRegions").mockReturnValue([]);
      comp.onDown({ type: "pointerdown", clientX: 10, clientY: 20 } as any);
   }

   it("should invoke clearResizeStatus when the registered document mouseup handler fires", () => {
      const { comp, renderer } = createComponent();
      startResize(comp);
      const handler = (renderer.listen as any).mock.calls[0]?.[2] as ((event: MouseEvent) => void);
      const emitted: any[] = [];
      comp.changeCursor.subscribe((v: any) => emitted.push(v));

      expect(handler).toBeTruthy();
      handler!({} as MouseEvent);

      expect(comp.isMouseDown).toBe(false);
      expect(emitted).toEqual([null]);
   });

   it("should call the renderer's unlisten function when the resize ends via onUp", () => {
      const { comp, renderer } = createComponent();
      const unlisten = vi.fn();
      renderer.listen.mockReturnValueOnce(unlisten);
      startResize(comp);

      comp.onUp({ type: "pointerup", clientX: 999, clientY: 999, which: 1 } as any);

      expect(unlisten).toHaveBeenCalled();
   });

   it("should re-register a fresh listener on a subsequent resize-start after the previous one ended", () => {
      const { comp, renderer } = createComponent();
      startResize(comp);
      comp.onUp({ type: "pointerup", clientX: 999, clientY: 999, which: 1 } as any);
      renderer.listen.mockClear();

      startResize(comp);

      expect(renderer.listen).toHaveBeenCalledTimes(1);
   });
});

describe("ChartAxisArea — clearDocMouseListener / clearResizeStatus", () => {
   it("should be a no-op when no listener has ever been registered", () => {
      const { comp, renderer } = createComponent();

      expect(() => (comp as any).clearDocMouseListener()).not.toThrow();
      expect(renderer.listen).not.toHaveBeenCalled();
   });

   it("should always emit a null changeCursor and clear isMouseDown, even if already cleared", () => {
      const { comp } = createComponent();
      const emitted: any[] = [];
      comp.changeCursor.subscribe((v: any) => emitted.push(v));

      (comp as any).clearResizeStatus();

      expect(comp.isMouseDown).toBe(false);
      expect(emitted).toEqual([null]);
   });
});

// ---------------------------------------------------------------------------
// Group 3: onMove/onLeave dataTip debounce — actual emission, not just call-args [Risk 2]
// ---------------------------------------------------------------------------

describe("ChartAxisArea — onMove debounced data tip actually emits", () => {
   it("should emit showDataTip through the debounced callback when the hovered region contains a dimension", () => {
      const { comp } = createComponent();
      const region = { hyperlinks: null } as any;
      vi.spyOn(ChartTool, "getTreeRegions").mockReturnValue([0]);
      vi.spyOn(ChartTool, "getDim").mockReturnValue("Field1");
      comp.chartObject.regions = [region];
      comp.dataTip = "Tip1";
      comp.dataTipOnClick = false;
      const emitted: any[] = [];
      comp.showDataTip.subscribe((v: any) => emitted.push(v));

      comp.onMove({ target: { className: "" }, offsetX: 0, offsetY: 0 } as any);

      expect(emitted).toEqual([{ chartObject: comp.chartObject, regions: [region] }]);
   });

   it("should NOT emit showDataTip through the debounced callback when the region has no dimension", () => {
      const { comp } = createComponent();
      const region = { hyperlinks: null } as any;
      vi.spyOn(ChartTool, "getTreeRegions").mockReturnValue([0]);
      vi.spyOn(ChartTool, "getDim").mockReturnValue(null);
      comp.chartObject.regions = [region];
      comp.dataTip = "Tip1";
      comp.dataTipOnClick = false;
      const emitted: any[] = [];
      comp.showDataTip.subscribe((v: any) => emitted.push(v));

      comp.onMove({ target: { className: "" }, offsetX: 0, offsetY: 0 } as any);

      expect(emitted).toHaveLength(0);
   });
});

describe("ChartAxisArea — onLeave debounced data-tip-clear actually emits", () => {
   it("should emit showDataTip with an empty selection when no matching data-tip element exists in the DOM", () => {
      const { comp } = createComponent();
      comp.axisArea = { nativeElement: document.createElement("div") };
      (comp as any).drillVisible = true;
      comp.dataTip = "Tip 1";
      comp.dataTipOnClick = false;
      const emitted: any[] = [];
      comp.showDataTip.subscribe((v: any) => emitted.push(v));

      comp.onLeave({ relatedTarget: null } as any);

      expect(emitted).toEqual([{ chartObject: comp.chartObject, regions: [] }]);
   });

   it("should NOT emit showDataTip when the mouse is still within the existing data-tip element", () => {
      const { comp } = createComponent();
      comp.axisArea = { nativeElement: document.createElement("div") };
      (comp as any).drillVisible = true;
      comp.dataTip = "Tip 1";
      comp.dataTipOnClick = false;
      const tipElement = document.createElement("div");
      tipElement.className = "current-datatip-Tip_1";
      document.body.appendChild(tipElement);
      vi.spyOn(GuiTool, "isMouseIn").mockImplementation(
         (el: any) => el === tipElement ? true : false
      );
      const emitted: any[] = [];
      comp.showDataTip.subscribe((v: any) => emitted.push(v));

      comp.onLeave({ relatedTarget: null } as any);

      expect(emitted).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 4: ngOnDestroy / cleanup [Risk 2]
// ---------------------------------------------------------------------------

describe("ChartAxisArea — ngOnDestroy cleanup", () => {
   it("should unregister a pending document mouseup listener on destroy", () => {
      const { comp, renderer } = createComponent();
      const unlisten = vi.fn();
      renderer.listen.mockReturnValueOnce(unlisten);
      comp.resizeCursor = "row-resize";
      vi.spyOn(ChartTool, "getTreeRegions").mockReturnValue([]);
      comp.onDown({ type: "pointerdown", clientX: 10, clientY: 20 } as any);

      expect(() => comp.ngOnDestroy()).not.toThrow();

      expect(unlisten).toHaveBeenCalled();
   });

   it("should not throw when destroyed without ever having started a resize", () => {
      const { comp } = createComponent();
      expect(() => comp.ngOnDestroy()).not.toThrow();
   });
});
