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
 * ChartPlotArea — Pass 1: Interaction
 *
 * Direct instantiation via TestBed.runInInjectionContext (see
 * chart-plot-area.component.test-helpers.ts — required because the base class
 * ChartObjectAreaBase's constructor calls Angular's `inject(NgZone)` directly).
 *
 * Scope (per prescan Pass 1 method list): ngOnChanges (scroll debounce), onDown/onUp
 * (pan mode start/end + onPanMap emit), onSelectionBox -> selectChart (region emit,
 * hyperlink emit, flyover emit, dataTipOnClick path), onContextMenu (unselected-region
 * select), onLeave (debounce cancel + dataTip clear + flyover clear), onScroll
 * (scroll sync emit), updateChartObject (canvas clear + selection redraw), loaded
 * (success path + HTTP error path with modal/embed branch).
 *
 * Risk-first coverage:
 *   Group 3 [Risk 3] — onSelectionBox/selectChart: the core click/drag-select flow,
 *                       hyperlink/flyover/dataTip dispatch, resize-in-progress bypass
 *   Group 8 [Risk 3] — loaded: success vs failure status, destroyed guard, embed vs
 *                       modal error-reporting branch
 *   Group 2 [Risk 3] — onDown/onUp pan mode: which-button gating, percentage math
 *   Group 1/4/5/6/7 [Risk 2] — single-purpose lifecycle/emit methods
 *
 * Confirmed bugs (it.fails): none
 *
 * Out of scope this pass (covered in chart-plot-area.component.risk/display.tl.spec.ts):
 *   getSrc oSrc-change gating, fireOnLoading/fireOnLoad tile-visibility gate, destroyed-guard
 *   race on loaded, plotScaleInfo setter — Pass 2.
 *   onMove cursor paths, changeCursor0, onAltDown/onAltUp, getSingleClickRegions,
 *   selectIntersect, scrollContainerWidth/Height, isMaxModeHidden, click (double-tap) — Pass 3.
 *
 * Every other test in this file mocks ChartTool.getTreeRegions to isolate selectChart's own
 * dispatch logic from region-tree geometry. Group 3 also includes one real-geometry
 * integration test (ported from the deleted chart-plot-area.component.spec.ts, whose
 * equivalent case was an `it.skip` that had never actually run) that exercises the real
 * ChartTool.getTreeRegions/which-polygon tree against real polygon region data — see
 * makeRealisticRegions() in the test-helpers file.
 */

import { ComponentTool } from "../../common/util/component-tool";
import { GuiTool } from "../../common/util/gui-tool";
import { ChartTool } from "../model/chart-tool";
import { SelectionBoxEvent } from "../../widget/directive/selection-box.directive";
import { Rectangle } from "../../common/data/rectangle";
import {
   createComponent, makePlot, makeRealisticRegions, makeTile,
} from "./chart-plot-area.component.test-helpers";

afterEach(() => vi.restoreAllMocks());

function setupCanvasWithContext(comp: any) {
   const canvas = document.createElement("canvas");
   const ctx = {} as CanvasRenderingContext2D;
   vi.spyOn(canvas, "getContext").mockReturnValue(ctx as any);
   comp._objectCanvas = { nativeElement: canvas };
   return ctx;
}

// ---------------------------------------------------------------------------
// Group 1: ngOnChanges [Risk 2]
// ---------------------------------------------------------------------------

describe("ChartPlotArea — ngOnChanges", () => {
   const scrollChange = { currentValue: 5, previousValue: 0, firstChange: false, isFirstChange: () => false };

   it("should clear the canvas and debounce updateChartObject when scrollTop changes and a context exists", () => {
      const { comp, chartService, debounceService } = createComponent();
      const ctx = setupCanvasWithContext(comp);
      const updateSpy = vi.spyOn(comp, "updateChartObject").mockImplementation(() => {});

      comp.ngOnChanges({ scrollTop: scrollChange });

      expect(chartService.clearCanvas).toHaveBeenCalledWith(ctx);
      expect(debounceService.debounce).toHaveBeenCalledWith(
         "plot.scrolled", expect.any(Function), 500, []
      );
      expect(updateSpy).toHaveBeenCalled();
   });

   it("should also react to scrollLeft changes", () => {
      const { comp, chartService } = createComponent();
      const ctx = setupCanvasWithContext(comp);
      vi.spyOn(comp, "updateChartObject").mockImplementation(() => {});

      comp.ngOnChanges({ scrollLeft: scrollChange });

      expect(chartService.clearCanvas).toHaveBeenCalledWith(ctx);
   });

   it("should NOT touch the canvas when there is no context", () => {
      const { comp, chartService, debounceService } = createComponent();

      comp.ngOnChanges({ scrollTop: scrollChange });

      expect(chartService.clearCanvas).not.toHaveBeenCalled();
      expect(debounceService.debounce).not.toHaveBeenCalled();
   });

   it("should ignore unrelated changes", () => {
      const { comp, chartService } = createComponent();
      setupCanvasWithContext(comp);

      comp.ngOnChanges({ dataTip: scrollChange });

      expect(chartService.clearCanvas).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 2: onDown / onUp pan mode [Risk 3]
// ---------------------------------------------------------------------------

describe("ChartPlotArea — onDown (pan mode)", () => {
   it("should start panning on a primary-button press while pan mode is enabled", () => {
      const { comp } = createComponent();
      comp.panMode = true;
      vi.spyOn(comp, "getSrc").mockReturnValue("src1" as any);
      vi.spyOn(comp, "getBackground").mockReturnValue("bg1" as any);
      const event = { which: 1, preventDefault: vi.fn(), stopImmediatePropagation: vi.fn(), clientX: 10, clientY: 20 } as any;

      comp.onDown(event);

      expect(event.preventDefault).toHaveBeenCalled();
      expect(event.stopImmediatePropagation).toHaveBeenCalled();
      expect(comp.panning).toEqual({ x: 10, y: 20 });
      expect(comp.panSnapshot).toBe("src1");
      expect(comp.panBackground).toBe("bg1");
   });

   it("should also start panning on which=0 (touch/synthetic press)", () => {
      const { comp } = createComponent();
      comp.panMode = true;
      const event = { which: 0, preventDefault: vi.fn(), stopImmediatePropagation: vi.fn(), clientX: 1, clientY: 2 } as any;

      comp.onDown(event);

      expect(comp.panning).toEqual({ x: 1, y: 2 });
   });

   it("should NOT start panning when pan mode is disabled", () => {
      const { comp } = createComponent();
      comp.panMode = false;
      const event = { which: 1, preventDefault: vi.fn(), stopImmediatePropagation: vi.fn(), clientX: 1, clientY: 2 } as any;

      comp.onDown(event);

      expect(comp.panning).toBeNull();
      expect(event.preventDefault).not.toHaveBeenCalled();
   });

   it("should NOT start panning on a non-primary button press (e.g. right click)", () => {
      const { comp } = createComponent();
      comp.panMode = true;
      const event = { which: 2, preventDefault: vi.fn(), stopImmediatePropagation: vi.fn(), clientX: 1, clientY: 2 } as any;

      comp.onDown(event);

      expect(comp.panning).toBeNull();
   });
});

describe("ChartPlotArea — onUp (pan mode)", () => {
   it("should emit the pan percentage and clear panning state when a pan was in progress", () => {
      const { comp, changeRef } = createComponent({
         plot: makePlot({ layoutBounds: { x: 0, y: 0, width: 200, height: 100 } as any })
      });
      (comp as any).panning = { x: 0, y: 0 };
      (comp as any).panX = 20;
      (comp as any).panY = 10;
      const emitted: any[] = [];
      comp.onPanMap.subscribe((v: any) => emitted.push(v));

      comp.onUp({} as any);

      expect(emitted).toEqual([{ x: -0.1, y: 0.1 }]);
      expect(comp.panning).toBeNull();
      expect(changeRef.detectChanges).toHaveBeenCalled();
   });

   it("should do nothing when no pan was in progress", () => {
      const { comp, changeRef } = createComponent();
      const emitted: any[] = [];
      comp.onPanMap.subscribe((v: any) => emitted.push(v));

      comp.onUp({} as any);

      expect(emitted).toHaveLength(0);
      expect(changeRef.detectChanges).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 3: onSelectionBox -> selectChart [Risk 3]
// ---------------------------------------------------------------------------

describe("ChartPlotArea — onSelectionBox / selectChart", () => {
   function boxEvent(overrides: Record<string, any> = {}): SelectionBoxEvent {
      return {
         box: { x: 0, y: 0, width: 0, height: 0 },
         ctrlKey: false, metaKey: false,
         ...overrides,
      } as any;
   }

   it("should reset isResize and emit a default cursor instead of selecting when a resize just finished", () => {
      const { comp } = createComponent();
      (comp as any).isResize = true;
      const emitted: any[] = [];
      comp.selectRegion.subscribe((v: any) => emitted.push(v));
      const cursorEmitted: any[] = [];
      comp.changeCursor.subscribe((v: any) => cursorEmitted.push(v));

      comp.onSelectionBox(boxEvent());

      expect((comp as any).isResize).toBe(false);
      expect(emitted).toHaveLength(0);
      expect(cursorEmitted).toEqual(["default"]);
   });

   it("should emit selectRegion for a plain click (zero-size box)", () => {
      const { comp } = createComponent();
      vi.spyOn(ChartTool, "getTreeRegions").mockReturnValue([]);
      const emitted: any[] = [];
      comp.selectRegion.subscribe((v: any) => emitted.push(v));

      comp.onSelectionBox(boxEvent());

      expect(emitted).toEqual([
         expect.objectContaining({ chartObject: comp.chartObject, regions: [], rangeSelection: false, isCtrl: false }),
      ]);
   });

   it("should mark rangeSelection=true and pass isCtrl through for a drag-selected box", () => {
      const { comp } = createComponent();
      vi.spyOn(ChartTool, "getTreeRegions").mockReturnValue([]);
      const emitted: any[] = [];
      comp.selectRegion.subscribe((v: any) => emitted.push(v));

      comp.onSelectionBox(boxEvent({ box: { x: 0, y: 0, width: 10, height: 10 }, ctrlKey: true }));

      expect(emitted).toEqual([
         expect.objectContaining({ rangeSelection: true, isCtrl: true }),
      ]);
   });

   it("should emit showTooltip(null) first when a mobile drag actually moved", () => {
      const { comp } = createComponent();
      (comp as any).mobile = true;
      vi.spyOn(ChartTool, "getTreeRegions").mockReturnValue([]);
      const order: string[] = [];
      comp.showTooltip.subscribe(() => order.push("tooltip"));
      comp.selectRegion.subscribe(() => order.push("select"));

      comp.onSelectionBox(boxEvent({ box: { x: 0, y: 0, width: 5, height: 5 } }));

      expect(order).toEqual(["tooltip", "select"]);
   });

   it("should emit showHyperlink for a click landing on a hyperlinked region", () => {
      const { comp } = createComponent();
      const region = { hyperlinks: [{ name: "l1" }], noselect: false } as any;
      vi.spyOn(ChartTool, "getTreeRegions").mockReturnValue([0]);
      comp.chartObject.regions = [region];
      const emitted: any[] = [];
      comp.showHyperlink.subscribe((v: any) => emitted.push(v));
      const event = boxEvent();

      comp.onSelectionBox(event);

      expect(emitted).toEqual([event]);
   });

   it("should emit showHyperlink when hostCursor is already 'pointer', even without a hyperlink region", () => {
      const { comp } = createComponent();
      vi.spyOn(ChartTool, "getTreeRegions").mockReturnValue([]);
      comp.hostCursor = "pointer";
      const emitted: any[] = [];
      comp.showHyperlink.subscribe((v: any) => emitted.push(v));

      comp.onSelectionBox(boxEvent());

      expect(emitted).toHaveLength(1);
   });

   it("should NOT emit showHyperlink for a plain click with no hyperlink region and a non-pointer cursor", () => {
      const { comp } = createComponent();
      vi.spyOn(ChartTool, "getTreeRegions").mockReturnValue([]);
      const emitted: any[] = [];
      comp.showHyperlink.subscribe((v: any) => emitted.push(v));

      comp.onSelectionBox(boxEvent());

      expect(emitted).toHaveLength(0);
   });

   it("should emit sendFlyover when flyover is enabled", () => {
      const { comp } = createComponent();
      vi.spyOn(ChartTool, "getTreeRegions").mockReturnValue([]);
      comp.flyover = true;
      const emitted: any[] = [];
      comp.sendFlyover.subscribe((v: any) => emitted.push(v));

      comp.onSelectionBox(boxEvent());

      expect(emitted).toEqual([
         expect.objectContaining({ chartObject: comp.chartObject, regions: [] }),
      ]);
   });

   it("should emit showDataTip via the click-data-tip path when dataTipOnClick is set", () => {
      const { comp } = createComponent();
      vi.spyOn(ChartTool, "getTreeRegions").mockReturnValue([]);
      comp.dataTipOnClick = true;
      comp.dataTip = "Tip1";
      const emitted: any[] = [];
      comp.showDataTip.subscribe((v: any) => emitted.push(v));

      comp.onSelectionBox(boxEvent());

      expect(emitted).toEqual([{ chartObject: comp.chartObject, regions: [] }]);
   });

   it("should reset selectionWidth/selectionHeight to 0 after a successful selection", () => {
      const { comp } = createComponent();
      vi.spyOn(ChartTool, "getTreeRegions").mockReturnValue([]);
      comp.selectionWidth = 99;
      comp.selectionHeight = 88;

      comp.onSelectionBox(boxEvent());

      expect(comp.selectionWidth).toBe(0);
      expect(comp.selectionHeight).toBe(0);
   });

   it("[real geometry] should select every region when the drag box covers the entire chart", () => {
      // Deliberately does NOT mock ChartTool.getTreeRegions — this exercises the real
      // which-polygon region tree (built by updateRegionTree() when chartObject.regions is
      // assigned) against real polygon coordinates, unlike every other test in this file.
      const layoutBounds = new Rectangle(32, 2, 367, 255);
      const { comp } = createComponent({
         plot: makePlot({
            layoutBounds, bounds: layoutBounds, regions: makeRealisticRegions(),
         }),
      });
      const emitted: any[] = [];
      comp.selectRegion.subscribe((v: any) => emitted.push(v));
      const byIndex = (a: any, b: any) => a.index - b.index;

      comp.onSelectionBox(boxEvent({
         box: { x: 0, y: 0, width: layoutBounds.x + layoutBounds.width, height: layoutBounds.y + layoutBounds.height },
      }));

      expect(emitted).toHaveLength(1);
      const selectedRegions = [...emitted[0].regions].sort(byIndex);
      const allRegions = [...comp.chartObject.regions].sort(byIndex);
      expect(selectedRegions).toEqual(allRegions);
      expect(emitted[0].rangeSelection).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 4: onContextMenu [Risk 2]
// ---------------------------------------------------------------------------

describe("ChartPlotArea — onContextMenu", () => {
   it("should select the region under the cursor when nothing there is already selected", () => {
      const { comp } = createComponent();
      vi.spyOn(comp as any, "clientRect", "get").mockReturnValue({ left: 0, top: 0 } as any);
      vi.spyOn(ChartTool, "getTreeRegions").mockReturnValue([]);
      vi.spyOn(ChartTool, "isRegionSelected").mockReturnValue(false);
      const emitted: any[] = [];
      comp.selectRegion.subscribe((v: any) => emitted.push(v));

      comp.onContextMenu({ clientX: 5, clientY: 5 });

      expect(emitted).toEqual([
         expect.objectContaining({ chartObject: comp.chartObject, rangeSelection: false, isCtrl: false }),
      ]);
   });

   it("should NOT re-select when right-clicking on an already-selected region", () => {
      const { comp } = createComponent();
      vi.spyOn(comp as any, "clientRect", "get").mockReturnValue({ left: 0, top: 0 } as any);
      const region = { noselect: false } as any;
      vi.spyOn(ChartTool, "getTreeRegions").mockReturnValue([0]);
      comp.chartObject.regions = [region];
      vi.spyOn(ChartTool, "isRegionSelected").mockReturnValue(true);
      const emitted: any[] = [];
      comp.selectRegion.subscribe((v: any) => emitted.push(v));

      comp.onContextMenu({ clientX: 5, clientY: 5 });

      expect(emitted).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 5: onLeave [Risk 2]
// ---------------------------------------------------------------------------

describe("ChartPlotArea — onLeave", () => {
   afterEach(() => { document.body.innerHTML = ""; });

   it("should cancel the pending data-tip debounce and clear the tooltip", () => {
      const { comp, debounceService } = createComponent();
      const emitted: any[] = [];
      comp.showTooltip.subscribe((v: any) => emitted.push(v));

      comp.onLeave({ relatedTarget: null } as any);

      expect(debounceService.cancel).toHaveBeenCalledWith("chart_dataTipEvent");
      expect(emitted).toEqual([null]);
   });

   it("should emit showDataTip with an empty selection when a data tip is configured and no matching element exists", () => {
      const { comp } = createComponent();
      comp.dataTip = "Tip 1";
      comp.dataTipOnClick = false;
      const emitted: any[] = [];
      comp.showDataTip.subscribe((v: any) => emitted.push(v));

      comp.onLeave({ relatedTarget: null } as any);

      expect(emitted).toEqual([{ chartObject: comp.chartObject, regions: [] }]);
   });

   it("should NOT clear the data tip on mobile", () => {
      const { comp } = createComponent();
      (comp as any).mobile = true;
      comp.dataTip = "Tip 1";
      comp.dataTipOnClick = false;
      const emitted: any[] = [];
      comp.showDataTip.subscribe((v: any) => emitted.push(v));

      comp.onLeave({ relatedTarget: null } as any);

      expect(emitted).toHaveLength(0);
   });

   it("should NOT clear the data tip when dataTipOnClick suppresses hover-driven tips", () => {
      const { comp } = createComponent();
      comp.dataTip = "Tip 1";
      comp.dataTipOnClick = true;
      const emitted: any[] = [];
      comp.showDataTip.subscribe((v: any) => emitted.push(v));

      comp.onLeave({ relatedTarget: null } as any);

      expect(emitted).toHaveLength(0);
   });

   it("should clear the flyover selection when flyover is enabled and not click-gated", () => {
      const { comp } = createComponent();
      comp.flyover = true;
      comp.flyOnClick = false;
      const emitted: any[] = [];
      comp.sendFlyover.subscribe((v: any) => emitted.push(v));

      comp.onLeave({ relatedTarget: null } as any);

      expect(emitted).toEqual([expect.objectContaining({ chartObject: null, regions: [] })]);
   });

   it("should NOT clear the flyover selection when flyOnClick suppresses hover-triggered flyover", () => {
      const { comp } = createComponent();
      comp.flyover = true;
      comp.flyOnClick = true;
      const emitted: any[] = [];
      comp.sendFlyover.subscribe((v: any) => emitted.push(v));

      comp.onLeave({ relatedTarget: null } as any);

      expect(emitted).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 6: onScroll [Risk 2]
// ---------------------------------------------------------------------------

describe("ChartPlotArea — onScroll", () => {
   it("should emit scrollArea when the scroll position actually changed", () => {
      const { comp } = createComponent();
      comp.scrollTop = 0;
      comp.scrollLeft = 0;
      const emitted: any[] = [];
      comp.scrollArea.subscribe((v: any) => emitted.push(v));
      const event = { preventDefault: vi.fn(), target: { scrollTop: 10, scrollLeft: 0 } };

      comp.onScroll(event);

      expect(event.preventDefault).toHaveBeenCalled();
      expect(emitted).toEqual([event]);
   });

   it("should NOT emit scrollArea when the scroll position is unchanged", () => {
      const { comp } = createComponent();
      comp.scrollTop = 10;
      comp.scrollLeft = 5;
      const emitted: any[] = [];
      comp.scrollArea.subscribe((v: any) => emitted.push(v));

      comp.onScroll({ preventDefault: vi.fn(), target: { scrollTop: 10, scrollLeft: 5 } });

      expect(emitted).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 7: updateChartObject [Risk 2]
// ---------------------------------------------------------------------------

describe("ChartPlotArea — updateChartObject", () => {
   it("should fire onLoad immediately when the new chart object has no tiles", () => {
      const { comp } = createComponent({ plot: makePlot({ tiles: [] }) });
      const emitted: boolean[] = [];
      comp.onLoad.subscribe((v: boolean) => emitted.push(v));

      comp.updateChartObject(makePlot({ tiles: [] }));

      expect(emitted).toEqual([true]);
   });

   it("should clear the canvas when a context is available", () => {
      const { comp, chartService } = createComponent();
      setupCanvasWithContext(comp);
      chartService.clearCanvas.mockClear();

      comp.updateChartObject();

      expect(chartService.clearCanvas).toHaveBeenCalled();
   });

   it("should redraw the current selection when it belongs to the plot area", () => {
      const { comp } = createComponent();
      setupCanvasWithContext(comp);
      comp.chartSelection = { chartObject: { areaName: "plot_area" }, regions: [{}] } as any;
      const drawSpy = vi.spyOn(ChartTool, "drawRegions").mockImplementation(() => {});

      comp.updateChartObject();

      expect(drawSpy).toHaveBeenCalled();
   });

   it("should NOT redraw when the current selection belongs to a different area", () => {
      const { comp } = createComponent();
      setupCanvasWithContext(comp);
      comp.chartSelection = { chartObject: { areaName: "bottom_x_axis" }, regions: [{}] } as any;
      const drawSpy = vi.spyOn(ChartTool, "drawRegions").mockImplementation(() => {});

      comp.updateChartObject();

      expect(drawSpy).not.toHaveBeenCalled();
   });

   it("should carry forward the loaded flag for tiles whose src did not change", () => {
      const oldTile = makeTile({ loaded: true });
      const oldPlot = makePlot({ tiles: [oldTile] });
      const { comp } = createComponent({ plot: makePlot({ tiles: [makeTile({ loaded: false })] }) });
      vi.spyOn(comp, "getSrc").mockReturnValue("same-src" as any);

      comp.updateChartObject(oldPlot);

      expect(comp.chartObject.tiles[0].loaded).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 8: loaded [Risk 3]
// ---------------------------------------------------------------------------

describe("ChartPlotArea — loaded", () => {
   it("should mark the tile loaded and fire onLoad on success, without calling the server", () => {
      const { comp, http } = createComponent();
      const tile = makeTile();
      comp.chartObject.tiles = [tile];
      vi.spyOn(comp, "isTileVisible").mockReturnValue(true);
      const emitted: boolean[] = [];
      comp.onLoad.subscribe((v: boolean) => emitted.push(v));

      comp.loaded(true, tile);

      expect((tile as any).loaded).toBe(true);
      expect(emitted).toEqual([true]);
      expect(http.get).not.toHaveBeenCalled();
   });

   it("should NOT touch tile state or call the server for a tile that is not currently visible", () => {
      const { comp, http } = createComponent();
      const tile = makeTile();
      comp.chartObject.tiles = [tile];
      vi.spyOn(comp, "isTileVisible").mockReturnValue(false);

      comp.loaded(false, tile);

      expect((tile as any).loaded).toBeUndefined();
      expect(http.get).not.toHaveBeenCalled();
   });

   it("should always call clearPan, even when the tile is not visible", () => {
      const { comp, changeRef } = createComponent();
      const tile = makeTile();
      vi.spyOn(comp, "isTileVisible").mockReturnValue(false);

      comp.loaded(true, tile);

      expect((comp as any).hideTile).toBe(false);
      expect(changeRef.detectChanges).toHaveBeenCalled();
   });

   it("should re-fetch the tile URL via plain HTTP GET on a failed load", () => {
      const { comp, http } = createComponent();
      const tile = makeTile();
      comp.chartObject.tiles = [tile];
      vi.spyOn(comp, "isTileVisible").mockReturnValue(true);
      vi.spyOn(comp, "getSrc").mockReturnValue("http://x/img.png" as any);
      http.get.mockReturnValue({ subscribe: () => {} });

      comp.loaded(false, tile);

      expect(http.get).toHaveBeenCalledWith("http://x/img.png", { responseType: "text" });
   });

   it("should NOT call the server when the component has already been destroyed", () => {
      const { comp, http } = createComponent();
      const tile = makeTile();
      comp.chartObject.tiles = [tile];
      vi.spyOn(comp, "isTileVisible").mockReturnValue(true);
      (comp as any).destroyed = true;

      comp.loaded(false, tile);

      expect(http.get).not.toHaveBeenCalled();
   });

   it("should log to the console instead of showing a modal when embedded", () => {
      const { comp, http, contextProvider, debounceService } = createComponent();
      const tile = makeTile();
      comp.chartObject.tiles = [tile];
      vi.spyOn(comp, "isTileVisible").mockReturnValue(true);
      vi.spyOn(comp, "getSrc").mockReturnValue("http://x/img.png" as any);
      contextProvider.embed = true;
      const consoleSpy = vi.spyOn(console, "error").mockImplementation(() => {});
      const err = { status: 500 };
      http.get.mockReturnValue({ subscribe: (_success: any, error: any) => error(err) });

      comp.loaded(false, tile);

      expect(consoleSpy).toHaveBeenCalledWith(err);
      expect(debounceService.debounce).not.toHaveBeenCalled();
   });

   it("should debounce a modal error dialog when not embedded", () => {
      const { comp, http, contextProvider, debounceService } = createComponent();
      const tile = makeTile();
      comp.chartObject.tiles = [tile];
      vi.spyOn(comp, "isTileVisible").mockReturnValue(true);
      vi.spyOn(comp, "getSrc").mockReturnValue("http://x/img.png" as any);
      contextProvider.embed = false;
      // Override the default auto-executing debounce mock: this test only checks that
      // debounce was scheduled with the right key, not the downstream showHttpError effect
      // (covered separately below) — letting it auto-run would call the real
      // ComponentTool.showHttpError against a bare-stub NgbModal and crash.
      debounceService.debounce.mockImplementation(() => {});
      const err = { status: 500 };
      http.get.mockReturnValue({ subscribe: (_success: any, error: any) => error(err) });

      comp.loaded(false, tile);

      expect(debounceService.debounce).toHaveBeenCalledWith(
         "chart-plot-error-http://x/img.png", expect.any(Function), 1000
      );
   });

   it("should show the http error modal when the debounced callback runs and the component is still alive", () => {
      const { comp, http, contextProvider, debounceService, modal } = createComponent();
      const tile = makeTile();
      comp.chartObject.tiles = [tile];
      vi.spyOn(comp, "isTileVisible").mockReturnValue(true);
      vi.spyOn(comp, "getSrc").mockReturnValue("http://x/img.png" as any);
      contextProvider.embed = false;
      debounceService.debounce.mockImplementation((_key: string, fn: Function) => fn());
      const err = { status: 500 };
      http.get.mockReturnValue({ subscribe: (_success: any, error: any) => error(err) });
      const showHttpErrorSpy = vi.spyOn(ComponentTool, "showHttpError").mockImplementation(() => {});

      comp.loaded(false, tile);

      expect(showHttpErrorSpy).toHaveBeenCalledWith("_#(js:Error)", err, modal);
   });

   it("should NOT show the modal when the debounced callback runs after the component was destroyed", () => {
      const { comp, http, contextProvider, debounceService } = createComponent();
      const tile = makeTile();
      comp.chartObject.tiles = [tile];
      vi.spyOn(comp, "isTileVisible").mockReturnValue(true);
      vi.spyOn(comp, "getSrc").mockReturnValue("http://x/img.png" as any);
      contextProvider.embed = false;
      debounceService.debounce.mockImplementation((_key: string, fn: Function) => fn());
      const err = { status: 500 };
      http.get.mockReturnValue({
         subscribe: (_success: any, error: any) => {
            (comp as any).destroyed = true;
            error(err);
         }
      });
      const showHttpErrorSpy = vi.spyOn(ComponentTool, "showHttpError").mockImplementation(() => {});

      comp.loaded(false, tile);

      expect(showHttpErrorSpy).not.toHaveBeenCalled();
   });
});
