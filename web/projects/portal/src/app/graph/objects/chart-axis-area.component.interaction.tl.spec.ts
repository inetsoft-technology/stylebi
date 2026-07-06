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
 * ChartAxisArea — Pass 1: Interaction
 *
 * Direct instantiation via TestBed.runInInjectionContext (see
 * chart-axis-area.component.test-helpers.ts — required because the base class
 * ChartObjectAreaBase's constructor calls Angular's `inject(NgZone)` directly).
 *
 * IMPORTANT: this project's jsdom exposes a real `window.PointerEvent` constructor, so
 * `GuiTool.supportPointEvent()` returns TRUE in this test environment. The onDown/onUp guards
 * (`event.type === "mousedown" && !supportPointEvent()`) therefore only fire for
 * `event.type === "pointerdown"/"pointerup"` here, NOT "mousedown"/"mouseup" — tests use pointer
 * event types accordingly, not because that's the only real-world path but because it's the only
 * one this environment's supportPointEvent() branch takes.
 *
 * Scope (per prescan Pass 1 method list): ngOnChanges, onDown/onUp (resize start + region
 * select + flyover/dataTip dispatch), onClick (hyperlink), onDblClick (brush), onLeave, onMove,
 * onScroll, clickSort, drillDown/drillUp, showDrill/hideDrill/hoverOverDrill, onAltDown/onAltUp,
 * loaded/loading, updateChartObject.
 *
 * Risk-first coverage:
 *   Group 2  [Risk 3] — onDown/onUp: resize-start dispatch + document mouseup listener
 *                       registration, region selection with right-click-on-selected guard,
 *                       flyover/dataTip-on-click dispatch
 *   Group 5  [Risk 2] — onLeave: cursor reset, drill-icon hide, dataTip debounce, title-overlap
 *                       guard, flyover clear
 *   Group 6  [Risk 2] — onMove: sort-icon bypass, resize-in-progress bypass, tooltip vs debounced
 *                       dataTip dispatch, cursor emission, flyover
 *   Group 12 [Risk 2] — loading/loaded: per-tile visibility gate, aggregate onLoading/onLoad
 *   Group 1  [Risk 2] — ngOnChanges: scrollTop/scrollLeft axisType-matched updateCanvas trigger
 *   Group 7  [Risk 2] — onScroll: axisType-gated scrollAxis emit
 *   Groups 3/4/8/9/10/11/13 [Risk 1] — single-purpose emit/toggle methods
 *
 * Confirmed bugs (it.fails): none
 *
 * Out of scope this pass (covered in chart-axis-area.component.risk/display.tl.spec.ts):
 *   updateCanvas debounce->ngAfterViewInit, clearDocMouseListener leak risk, onMove/onLeave
 *   debounce race for dataTip — Pass 2.
 *   cursor getter, emitCursor axis-position math, createAxisResizeInfo bounds per axis,
 *   getSortIconTop/getDrillIconLeft/getDrillIconPosition, showSortIcon, minWidth, canvasX/canvasY
 *   — Pass 3.
 */

import { GuiTool } from "../../common/util/gui-tool";
import { ChartTool } from "../model/chart-tool";
import { ChartTile } from "../model/chart-tile";
import { createComponent, makeAxis } from "./chart-axis-area.component.test-helpers";

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: ngOnChanges [Risk 2]
// ---------------------------------------------------------------------------

describe("ChartAxisArea — ngOnChanges", () => {
   const scrollTopChange = { currentValue: 5, previousValue: 0, firstChange: false, isFirstChange: () => false };
   const scrollLeftChange = { currentValue: 5, previousValue: 0, firstChange: false, isFirstChange: () => false };

   it("should call updateCanvas when scrollTop changes on a y axis", () => {
      const { comp } = createComponent({ axis: makeAxis({ axisType: "y" }) });
      const updateCanvasSpy = vi.spyOn(comp as any, "updateCanvas");
      comp.ngOnChanges({ scrollTop: scrollTopChange });
      expect(updateCanvasSpy).toHaveBeenCalled();
   });

   it("should NOT call updateCanvas when scrollTop changes on an x axis", () => {
      const { comp } = createComponent({ axis: makeAxis({ axisType: "x" }) });
      const updateCanvasSpy = vi.spyOn(comp as any, "updateCanvas");
      comp.ngOnChanges({ scrollTop: scrollTopChange });
      expect(updateCanvasSpy).not.toHaveBeenCalled();
   });

   it("should call updateCanvas when scrollLeft changes on an x axis", () => {
      const { comp } = createComponent({ axis: makeAxis({ axisType: "x" }) });
      const updateCanvasSpy = vi.spyOn(comp as any, "updateCanvas");
      comp.ngOnChanges({ scrollLeft: scrollLeftChange });
      expect(updateCanvasSpy).toHaveBeenCalled();
   });

   it("should NOT call updateCanvas when neither scrollTop nor scrollLeft changed", () => {
      const { comp } = createComponent();
      const updateCanvasSpy = vi.spyOn(comp as any, "updateCanvas");
      comp.ngOnChanges({ hideSortIcon: scrollTopChange });
      expect(updateCanvasSpy).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 2: onDown / onUp [Risk 3]
// ---------------------------------------------------------------------------

describe("ChartAxisArea — onDown", () => {
   it("should record the pointer-down position for a pointerdown event", () => {
      const { comp } = createComponent();
      comp.onDown({ type: "pointerdown", clientX: 10, clientY: 20 } as any);
      expect(comp.isMouseDown).toBe(true);
      expect(comp.eventXdown).toBe(10);
      expect(comp.eventYdown).toBe(20);
   });

   it("should ignore an event type it doesn't recognize as a primary pointer press", () => {
      const { comp } = createComponent();
      comp.onDown({ type: "touchstart", clientX: 10, clientY: 20 } as any);
      expect(comp.isMouseDown).toBe(false);
   });

   it("should emit startAxisResize and register a document mouseup listener when a resize is in progress", () => {
      const { comp, renderer } = createComponent();
      (comp as any).resizeCursor = "row-resize";
      vi.spyOn(ChartTool, "getTreeRegions").mockReturnValue([]);
      const emitted: any[] = [];
      comp.startAxisResize.subscribe(v => emitted.push(v));

      comp.onDown({ type: "pointerdown", clientX: 10, clientY: 20 } as any);

      expect(emitted).toHaveLength(1);
      expect(renderer.listen).toHaveBeenCalledWith("document", "mouseup", expect.any(Function));
   });

   it("should NOT register a second document mouseup listener on a repeated resize-start", () => {
      const { comp, renderer } = createComponent();
      (comp as any).resizeCursor = "row-resize";
      vi.spyOn(ChartTool, "getTreeRegions").mockReturnValue([]);
      comp.onDown({ type: "pointerdown", clientX: 10, clientY: 20 } as any);
      renderer.listen.mockClear();

      comp.onDown({ type: "pointerdown", clientX: 11, clientY: 21 } as any);

      expect(renderer.listen).not.toHaveBeenCalled();
   });

   it("should NOT emit startAxisResize when not currently in a resize-cursor state", () => {
      const { comp } = createComponent();
      const emitted: any[] = [];
      comp.startAxisResize.subscribe(v => emitted.push(v));
      comp.onDown({ type: "pointerdown", clientX: 10, clientY: 20 } as any);
      expect(emitted).toHaveLength(0);
   });
});

describe("ChartAxisArea — onUp", () => {
   function setupCanvasRect(comp: any) {
      const canvas = document.createElement("canvas");
      vi.spyOn(canvas, "getBoundingClientRect")
         .mockReturnValue({ left: 0, top: 0, width: 0, height: 0 } as DOMRect);
      comp._objectCanvas = { nativeElement: canvas };
   }

   function primeMouseDown(comp: any, x = 10, y = 20) {
      vi.spyOn(ChartTool, "getTreeRegions").mockReturnValue([]);
      comp.onDown({ type: "pointerdown", clientX: x, clientY: y } as any);
   }

   it("should do nothing when the pointer moved between down and up (drag, not click)", () => {
      const { comp } = createComponent();
      setupCanvasRect(comp);
      primeMouseDown(comp, 10, 20);
      const emitted: any[] = [];
      comp.selectRegion.subscribe(v => emitted.push(v));

      comp.onUp({ type: "pointerup", clientX: 50, clientY: 50, which: 1 } as any);

      expect(emitted).toHaveLength(0);
   });

   it("should select the region under the pointer on a plain click", () => {
      const { comp } = createComponent();
      setupCanvasRect(comp);
      primeMouseDown(comp, 10, 20);
      vi.spyOn(ChartTool, "isRegionSelected").mockReturnValue(false);
      const emitted: any[] = [];
      comp.selectRegion.subscribe(v => emitted.push(v));

      comp.onUp({ type: "pointerup", clientX: 10, clientY: 20, which: 1, ctrlKey: false, metaKey: false, shiftKey: false } as any);

      expect(emitted).toHaveLength(1);
      expect(emitted[0]).toEqual(expect.objectContaining({ isCtrl: false, isShift: false }));
   });

   it("should NOT re-select when right-clicking on an already-selected region", () => {
      const { comp } = createComponent();
      setupCanvasRect(comp);
      const region = { noselect: false } as any;
      vi.spyOn(ChartTool, "getTreeRegions").mockReturnValue([0]);
      comp.chartObject.regions = [region];
      comp.onDown({ type: "pointerdown", clientX: 10, clientY: 20 } as any);
      vi.spyOn(ChartTool, "isRegionSelected").mockReturnValue(true);
      const emitted: any[] = [];
      comp.selectRegion.subscribe(v => emitted.push(v));

      comp.onUp({ type: "pointerup", clientX: 10, clientY: 20, which: 3 } as any);

      expect(emitted).toHaveLength(0);
   });

   it("should emit sendFlyover when flyover is enabled", () => {
      const { comp } = createComponent();
      setupCanvasRect(comp);
      primeMouseDown(comp, 10, 20);
      vi.spyOn(ChartTool, "isRegionSelected").mockReturnValue(false);
      comp.flyover = true;
      const emitted: any[] = [];
      comp.sendFlyover.subscribe(v => emitted.push(v));

      comp.onUp({ type: "pointerup", clientX: 10, clientY: 20, which: 1 } as any);

      expect(emitted).toHaveLength(1);
      expect(emitted[0]).toEqual(expect.objectContaining({ chartObject: comp.chartObject, regions: [] }));
   });

   it("should emit showDataTip (via emitDataTip) when dataTipOnClick is set and a dataTip target is configured", () => {
      const { comp } = createComponent();
      setupCanvasRect(comp);
      primeMouseDown(comp, 10, 20);
      vi.spyOn(ChartTool, "isRegionSelected").mockReturnValue(false);
      vi.spyOn(ChartTool, "getDim").mockReturnValue("Field1"); // containsDim=true -> not suppressed
      comp.dataTipOnClick = true;
      comp.dataTip = "DataTip1";
      const emitted: any[] = [];
      comp.showDataTip.subscribe(v => emitted.push(v));

      comp.onUp({ type: "pointerup", clientX: 10, clientY: 20, which: 1 } as any);

      expect(emitted).toEqual([{ chartObject: comp.chartObject, regions: [] }]);
   });

   it("should always clear the resize status at the end of onUp", () => {
      const { comp } = createComponent();
      setupCanvasRect(comp);
      (comp as any).resizeCursor = "row-resize";
      const emitted: any[] = [];
      comp.changeCursor.subscribe(v => emitted.push(v));

      comp.onUp({ type: "pointerup", clientX: 0, clientY: 0, which: 1 } as any);

      expect(emitted).toEqual([null]);
   });
});

// ---------------------------------------------------------------------------
// Group 3: onClick [Risk 2]
// ---------------------------------------------------------------------------

describe("ChartAxisArea — onClick", () => {
   it("should emit showHyperlink when a hyperlinked region is under the click", () => {
      const { comp } = createComponent();
      const region = { hyperlinks: [{ name: "l1" }] } as any;
      vi.spyOn(ChartTool, "getTreeRegions").mockReturnValue([0]);
      comp.chartObject.regions = [region];
      const emitted: MouseEvent[] = [];
      comp.showHyperlink.subscribe(v => emitted.push(v));
      const event = { offsetX: 0, offsetY: 0 } as MouseEvent;

      comp.onClick(event);

      expect(emitted).toEqual([event]);
   });

   it("should emit showHyperlink when isShowPointer is already true, even without a hyperlink region", () => {
      const { comp } = createComponent();
      vi.spyOn(ChartTool, "getTreeRegions").mockReturnValue([]);
      (comp as any).isShowPointer = true;
      const emitted: MouseEvent[] = [];
      comp.showHyperlink.subscribe(v => emitted.push(v));
      const event = { offsetX: 0, offsetY: 0 } as MouseEvent;

      comp.onClick(event);

      expect(emitted).toEqual([event]);
   });

   it("should NOT emit showHyperlink when there is no hyperlink region and isShowPointer is false", () => {
      const { comp } = createComponent();
      vi.spyOn(ChartTool, "getTreeRegions").mockReturnValue([]);
      (comp as any).isShowPointer = false;
      const emitted: MouseEvent[] = [];
      comp.showHyperlink.subscribe(v => emitted.push(v));

      comp.onClick({ offsetX: 0, offsetY: 0 } as MouseEvent);

      expect(emitted).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 4: onDblClick [Risk 1]
// ---------------------------------------------------------------------------

describe("ChartAxisArea — onDblClick", () => {
   it("should emit brushChart", () => {
      const { comp } = createComponent();
      const emitted: any[] = [];
      comp.brushChart.subscribe(v => emitted.push(v));
      comp.onDblClick({} as MouseEvent);
      expect(emitted).toHaveLength(1);
   });
});

// ---------------------------------------------------------------------------
// Group 5: onLeave [Risk 2]
// ---------------------------------------------------------------------------

describe("ChartAxisArea — onLeave", () => {
   function withAxisArea(comp: any) {
      comp.axisArea = { nativeElement: document.createElement("div") };
      vi.spyOn(GuiTool, "isMouseIn").mockReturnValue(false);
   }

   it("should reset the resize cursor when not actively resizing", () => {
      const { comp } = createComponent();
      withAxisArea(comp);
      (comp as any).resizeCursor = "row-resize";
      const emitted: any[] = [];
      comp.changeCursor.subscribe(v => emitted.push(v));

      comp.onLeave({} as MouseEvent);

      expect(emitted).toEqual([null]);
   });

   it("should NOT reset the resize cursor while a resize is in progress", () => {
      const { comp } = createComponent();
      withAxisArea(comp);
      comp.isMouseDown = true;
      (comp as any).resizeCursor = "row-resize";
      const emitted: any[] = [];
      comp.changeCursor.subscribe(v => emitted.push(v));

      comp.onLeave({} as MouseEvent);

      expect(emitted).toHaveLength(0);
   });

   it("should hide the drill icon and clear the tooltip when the drill icon is visible", () => {
      const { comp } = createComponent();
      withAxisArea(comp);
      (comp as any).drillVisible = true;
      const tooltipEmitted: any[] = [];
      comp.showTooltip.subscribe(v => tooltipEmitted.push(v));

      comp.onLeave({ relatedTarget: null } as any);

      expect(comp.drillVisible).toBe(false);
      expect(tooltipEmitted).toEqual([null]);
   });

   it("should NOT hide the drill icon when the mouse moved onto a floating-icon element", () => {
      const { comp } = createComponent();
      withAxisArea(comp);
      (comp as any).drillVisible = true;
      const floatingIcon = { classList: { contains: (c: string) => c === "floating-icon" } };

      comp.onLeave({ relatedTarget: floatingIcon } as any);

      expect(comp.drillVisible).toBe(true);
   });

   it("should NOT hide the drill icon when the mouse is still within the axis title area (Bug #55606 guard)", () => {
      const { comp } = createComponent();
      withAxisArea(comp);
      vi.spyOn(GuiTool, "isMouseIn").mockImplementation((el: any) => el === comp.axisArea.nativeElement);
      (comp as any).drillVisible = true;

      comp.onLeave({ relatedTarget: null } as any);

      expect(comp.drillVisible).toBe(true);
   });

   it("should emit clearFlyover (empty selection) when flyover is enabled and not click-gated", () => {
      const { comp } = createComponent();
      withAxisArea(comp);
      comp.flyover = true;
      comp.flyOnClick = false;
      vi.spyOn(ChartTool, "getDim"); // not called since regions=[] short-circuits containsDim=false but regions.length==0 too
      const emitted: any[] = [];
      comp.sendFlyover.subscribe(v => emitted.push(v));

      comp.onLeave({} as MouseEvent);

      expect(emitted).toHaveLength(1);
      expect(emitted[0].regions).toEqual([]);
   });

   it("should NOT emit clearFlyover when flyOnClick suppresses hover-triggered flyover", () => {
      const { comp } = createComponent();
      withAxisArea(comp);
      comp.flyover = true;
      comp.flyOnClick = true;
      const emitted: any[] = [];
      comp.sendFlyover.subscribe(v => emitted.push(v));

      comp.onLeave({} as MouseEvent);

      expect(emitted).toHaveLength(0);
   });

   it("should debounce a data-tip-clear when a hover data tip is configured and the mouse truly left it", () => {
      const { comp, debounceService } = createComponent();
      withAxisArea(comp);
      (comp as any).drillVisible = true;
      comp.dataTip = "Tip 1";
      comp.dataTipOnClick = false;

      comp.onLeave({ relatedTarget: null } as any);

      expect(debounceService.debounce).toHaveBeenCalledWith(
         "chart_dataTipEvent", expect.any(Function), 100, []
      );
   });
});

// ---------------------------------------------------------------------------
// Group 6: onMove [Risk 2]
// ---------------------------------------------------------------------------

describe("ChartAxisArea — onMove", () => {
   function moveEvent(overrides: Record<string, any> = {}) {
      return { target: { className: "" }, offsetX: 0, offsetY: 0, ...overrides } as any;
   }

   it("should bail out immediately when hovering the sort-ascending icon", () => {
      const { comp } = createComponent();
      const target = { className: "sort-ascending-icon" };
      const event = { target, stopPropagation: vi.fn(), offsetX: 0, offsetY: 0 } as any;
      const emitted: any[] = [];
      comp.showTooltip.subscribe(v => emitted.push(v));

      comp.onMove(event);

      expect(event.stopPropagation).toHaveBeenCalled();
      expect(emitted).toHaveLength(0);
   });

   it("should bail out while a resize is actively being dragged", () => {
      const { comp } = createComponent();
      comp.isMouseDown = true;
      (comp as any).resizeCursor = "row-resize";
      const emitted: any[] = [];
      comp.showTooltip.subscribe(v => emitted.push(v));

      comp.onMove(moveEvent());

      expect(emitted).toHaveLength(0);
   });

   it("should emit a tooltip for the hovered region when there is no debounced data tip", () => {
      const { comp } = createComponent();
      const region = { tipIdx: 0 } as any;
      vi.spyOn(ChartTool, "getTreeRegions").mockReturnValue([0]);
      comp.chartObject.regions = [region];
      comp.dataTip = null;
      const emitted: any[] = [];
      comp.showTooltip.subscribe(v => emitted.push(v));

      comp.onMove(moveEvent());

      expect(emitted).toEqual([{ tipIndex: 0, region }]);
   });

   it("should debounce showDataTip instead of the plain tooltip when a hover data tip is configured", () => {
      const { comp, debounceService } = createComponent();
      vi.spyOn(ChartTool, "getTreeRegions").mockReturnValue([]);
      vi.spyOn(ChartTool, "getDim").mockReturnValue(null);
      comp.dataTip = "Tip1";
      comp.dataTipOnClick = false;

      comp.onMove(moveEvent());

      expect(debounceService.debounce).toHaveBeenCalledWith(
         "chart_dataTipEvent", expect.any(Function), 100, []
      );
   });

   it("should emit sendFlyover when flyover is enabled and not click-gated", () => {
      const { comp } = createComponent();
      vi.spyOn(ChartTool, "getTreeRegions").mockReturnValue([]);
      comp.flyover = true;
      comp.flyOnClick = false;
      const emitted: any[] = [];
      comp.sendFlyover.subscribe(v => emitted.push(v));

      comp.onMove(moveEvent());

      expect(emitted).toHaveLength(1);
      expect(emitted[0]).toEqual(expect.objectContaining({ chartObject: comp.chartObject, regions: [] }));
   });

   it("should NOT emit sendFlyover when flyOnClick suppresses hover-triggered flyover", () => {
      const { comp } = createComponent();
      vi.spyOn(ChartTool, "getTreeRegions").mockReturnValue([]);
      comp.flyover = true;
      comp.flyOnClick = true;
      const emitted: any[] = [];
      comp.sendFlyover.subscribe(v => emitted.push(v));

      comp.onMove(moveEvent());

      expect(emitted).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 7: onScroll [Risk 2]
// ---------------------------------------------------------------------------

describe("ChartAxisArea — onScroll", () => {
   it("should emit scrollAxis when the x-axis scroll position actually changed", () => {
      const { comp } = createComponent({ axis: makeAxis({ axisType: "x" }) });
      comp.scrollLeft = 0;
      const emitted: any[] = [];
      comp.scrollAxis.subscribe(v => emitted.push(v));
      const event = { preventDefault: vi.fn(), target: { scrollLeft: 10, scrollTop: 0 } };

      comp.onScroll(event);

      expect(event.preventDefault).toHaveBeenCalled();
      expect(emitted).toEqual([event]);
   });

   it("should NOT emit scrollAxis when the x-axis scroll position is unchanged", () => {
      const { comp } = createComponent({ axis: makeAxis({ axisType: "x" }) });
      comp.scrollLeft = 10;
      const emitted: any[] = [];
      comp.scrollAxis.subscribe(v => emitted.push(v));

      comp.onScroll({ preventDefault: vi.fn(), target: { scrollLeft: 10, scrollTop: 0 } });

      expect(emitted).toHaveLength(0);
   });

   it("should ignore scrollTop changes on an x axis (only scrollLeft matters)", () => {
      const { comp } = createComponent({ axis: makeAxis({ axisType: "x" }) });
      comp.scrollLeft = 10;
      const emitted: any[] = [];
      comp.scrollAxis.subscribe(v => emitted.push(v));

      comp.onScroll({ preventDefault: vi.fn(), target: { scrollLeft: 10, scrollTop: 99 } });

      expect(emitted).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 8: clickSort [Risk 1]
// ---------------------------------------------------------------------------

describe("ChartAxisArea — clickSort", () => {
   it("should stop propagation and emit sortAxis", () => {
      const { comp } = createComponent();
      const event = { stopPropagation: vi.fn() } as unknown as MouseEvent;
      const emitted: any[] = [];
      comp.sortAxis.subscribe(v => emitted.push(v));

      comp.clickSort(event);

      expect(event.stopPropagation).toHaveBeenCalled();
      expect(emitted).toHaveLength(1);
   });
});

// ---------------------------------------------------------------------------
// Group 9: drillDown / drillUp [Risk 1]
// ---------------------------------------------------------------------------

describe("ChartAxisArea — drillDown / drillUp", () => {
   it("should emit drill with drillOp '+' and the field at the given index for a y axis", () => {
      const { comp } = createComponent({ axis: makeAxis({ axisType: "y", axisFields: ["A", "B"] }) });
      const emitted: any[] = [];
      comp.drill.subscribe(v => emitted.push(v));

      comp.drillDown(1);

      expect(emitted).toEqual([{ axisType: "y", field: "B", drillOp: "+" }]);
   });

   it("should reverse the index for an x axis before looking up the field", () => {
      const { comp } = createComponent({ axis: makeAxis({ axisType: "x", axisFields: ["A", "B"], axisOps: ["", "", ""] }) });
      const emitted: any[] = [];
      comp.drill.subscribe(v => emitted.push(v));

      // axisOps.length(3) - index(0) - 1 = 2 -> looks up axisFields[2], which is undefined here;
      // assert the reversed index is what drives the lookup, not the raw index.
      comp.drillDown(0);

      expect(emitted[0].field).toBeUndefined(); // axisFields has only 2 entries, index 2 is out of range
      expect(emitted[0].drillOp).toBe("+");
   });

   it("should emit drill with drillOp '-' for drillUp", () => {
      const { comp } = createComponent({ axis: makeAxis({ axisType: "y", axisFields: ["A"] }) });
      const emitted: any[] = [];
      comp.drill.subscribe(v => emitted.push(v));

      comp.drillUp(0);

      expect(emitted).toEqual([{ axisType: "y", field: "A", drillOp: "-" }]);
   });
});

// ---------------------------------------------------------------------------
// Group 10: showDrill / hideDrill / hoverOverDrill [Risk 1]
// ---------------------------------------------------------------------------

describe("ChartAxisArea — showDrill / hideDrill / hoverOverDrill", () => {
   it("should show the drill icon and request change detection when it was hidden", () => {
      const { comp, changeRef } = createComponent();
      comp.showDrill();
      expect(comp.drillVisible).toBe(true);
      expect(changeRef.detectChanges).toHaveBeenCalled();
   });

   it("should NOT re-trigger change detection when the drill icon is already visible", () => {
      const { comp, changeRef } = createComponent();
      comp.showDrill();
      changeRef.detectChanges.mockClear();
      comp.showDrill();
      expect(changeRef.detectChanges).not.toHaveBeenCalled();
   });

   it("should hide the drill icon when not hovered", () => {
      const { comp } = createComponent();
      comp.showDrill();
      comp.hideDrill(false);
      expect(comp.drillVisible).toBe(false);
   });

   it("should NOT hide the drill icon while it is being hovered", () => {
      const { comp } = createComponent();
      comp.showDrill();
      comp.hoverOverDrill();
      comp.hideDrill(false);
      expect(comp.drillVisible).toBe(true);
   });

   it("should clear the hover flag when leaving the drill area, allowing it to hide", () => {
      const { comp } = createComponent();
      comp.showDrill();
      comp.hoverOverDrill();
      comp.hideDrill(true);
      expect((comp as any).drillHover).toBe(false);
      expect(comp.drillVisible).toBe(false);
   });

   it("should request change detection only on the hover transition, not repeatedly", () => {
      const { comp, changeRef } = createComponent();
      comp.hoverOverDrill();
      changeRef.detectChanges.mockClear();
      comp.hoverOverDrill();
      expect(changeRef.detectChanges).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 11: onAltDown / onAltUp [Risk 1]
// ---------------------------------------------------------------------------

describe("ChartAxisArea — onAltDown / onAltUp", () => {
   it("should set altDown=true on onAltDown", () => {
      const { comp } = createComponent();
      comp.onAltDown({} as any);
      expect((comp as any).altDown).toBe(true);
   });

   it("should set altDown=false and prevent default on onAltUp", () => {
      const { comp } = createComponent();
      (comp as any).altDown = true;
      const event = { preventDefault: vi.fn() } as unknown as KeyboardEvent;
      comp.onAltUp(event);
      expect((comp as any).altDown).toBe(false);
      expect(event.preventDefault).toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 12: loading / loaded [Risk 2]
// ---------------------------------------------------------------------------

describe("ChartAxisArea — loading / loaded", () => {
   function makeTile(overrides: Partial<ChartTile> = {}): ChartTile {
      return Object.assign({ row: 0, col: 0, bounds: { x: 0, y: 0, width: 10, height: 10 } }, overrides) as ChartTile;
   }

   it("should mark the tile as not-loaded and emit onLoading when a visible tile starts loading", () => {
      const { comp } = createComponent();
      const tile = makeTile();
      comp.chartObject.tiles = [tile];
      vi.spyOn(comp, "isTileVisible").mockReturnValue(true);
      const emitted: any[] = [];
      comp.onLoading.subscribe(() => emitted.push(true));

      comp.loading(tile);

      expect((tile as any).loaded).toBe(false);
      expect(emitted).toHaveLength(1);
   });

   it("should NOT emit onLoading for a tile that isn't currently visible", () => {
      const { comp } = createComponent();
      const tile = makeTile();
      comp.chartObject.tiles = [tile];
      vi.spyOn(comp, "isTileVisible").mockReturnValue(false);
      const emitted: any[] = [];
      comp.onLoading.subscribe(() => emitted.push(true));

      comp.loading(tile);

      expect(emitted).toHaveLength(0);
   });

   it("should mark the tile loaded and emit onLoad once every visible tile has finished", () => {
      const { comp } = createComponent();
      const tile = makeTile();
      comp.chartObject.tiles = [tile];
      vi.spyOn(comp, "isTileVisible").mockReturnValue(true);
      const emitted: boolean[] = [];
      comp.onLoad.subscribe(v => emitted.push(v));

      comp.loaded(true, tile);

      expect((tile as any).loaded).toBe(true);
      expect(emitted).toEqual([true]);
   });

   it("should NOT emit onLoad while another visible tile is still loading", () => {
      const { comp } = createComponent();
      const tile1 = makeTile({ row: 0 });
      const tile2 = makeTile({ row: 1 });
      (tile2 as any).loaded = false;
      comp.chartObject.tiles = [tile1, tile2];
      vi.spyOn(comp, "isTileVisible").mockReturnValue(true);
      const emitted: boolean[] = [];
      comp.onLoad.subscribe(v => emitted.push(v));

      comp.loaded(true, tile1);

      expect(emitted).toHaveLength(0);
   });

   it("should NOT mark or fire onLoad for a tile that is not currently visible", () => {
      const { comp } = createComponent();
      const tile = makeTile();
      comp.chartObject.tiles = [tile];
      vi.spyOn(comp, "isTileVisible").mockReturnValue(false);
      const emitted: boolean[] = [];
      comp.onLoad.subscribe(v => emitted.push(v));

      comp.loaded(true, tile);

      expect((tile as any).loaded).toBeUndefined();
      expect(emitted).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 13: updateChartObject [Risk 1]
// ---------------------------------------------------------------------------

describe("ChartAxisArea — updateChartObject", () => {
   it("should reverse axisOps for an x axis", () => {
      const { comp } = createComponent({ axis: makeAxis({ axisType: "x", axisOps: ["a", "b", "c"] }) });
      expect(comp.chartObject.axisOps).toEqual(["c", "b", "a"]);
   });

   it("should leave axisOps untouched for a y axis", () => {
      const { comp } = createComponent({ axis: makeAxis({ axisType: "y", axisOps: ["a", "b", "c"] }) });
      expect(comp.chartObject.axisOps).toEqual(["a", "b", "c"]);
   });
});
