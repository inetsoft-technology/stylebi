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
 * ChartArea — Pass 1: Interaction
 *
 * Direct instantiation (see chart-area.component.test-helpers.ts) — ChartArea has no base class
 * and heavy DOM/canvas dependencies, matching the established pattern for graph/vsobjects
 * components. `ChartTool`'s canvas-drawing/geometry helpers (`drawRegions`,
 * `getSelectedAxisRegions`) are spied where the test's focus is dispatch/emission correctness,
 * not chart geometry (geometry itself belongs to chart-tool's own tests, out of scope here).
 *
 * Scope (per prescan Pass 1 method list): lifecycle (ngOnInit/ngOnChanges/ngOnDestroy), model/
 * selected setters (subscription setup/teardown), user-triggered emissions (selectRegion,
 * sendFlyover, showDataTip/Tooltip, drill, sortAxis, showHyperlink), scroll sync, drag/drop entry
 * points, resize flow, touch/pinch-zoom, paging, and the loading-state emit gates.
 *
 * Risk-first coverage:
 *   Group 1  [Risk 3] — model setter: clearCanvasSubject wiring, chartSelection init, scroll
 *                        subscription setup
 *   Group 7  [Risk 3] — selectRegion: plain/ctrl/shift dispatch, radar line expansion, map-
 *                        selection guard
 *   Group 2  [Risk 2] — selected setter: showPagingControl side effect
 *   Group 3  [Risk 2] — ngOnInit/ngOnDestroy: window/matchMedia listener lifecycle
 *   Group 4  [Risk 2] — ngOnChanges: modelTS-triggered initDropRegions + scroll nudge
 *   Group 8  [Risk 2] — sendFlyover: current-selection vs payload-selection branch
 *   Group 11 [Risk 2] — onWrapperScroll/onScrollArea/onScrollAxis: scroll sync + axis-type guard
 *   Group 13 [Risk 2] — onDrop/dragOver/dragLeave: drop-type gate, entriesValue guard
 *   Group 17 [Risk 2] — touchStart/touchMove/touchEnd: pinch distance + zoom direction
 *   Group 18 [Risk 2] — resize flow: start/end axis+legend resize, endAreaResize dispatch
 *   Group 19 [Risk 2] — loading state machine: axisLoading/axisLoaded/plotLoading/plotLoaded gates
 *   Groups 5/6/9/10/12/14/15/16/20 [Risk 1] — single-purpose emit/delegate methods
 *
 * Confirmed bugs (it.fails): none
 *
 * Out of scope this pass (covered in chart-area.component.risk/display.tl.spec.ts):
 *   isDropAcceptable/isXAcceptable/isYAcceptable/isGeoAcceptable/drawDropRegion/isY2Supported —
 *     Pass 3 (dispatch-heavy pure conditionals).
 *   model setter rapid-reassignment leak, clearCanvasSubject flyoverApplied race, stale-assembly
 *     scroll guard, axisLoading/_loadingAxesSet Bug #74260 cycle-reset — Pass 2.
 *   getAssemblyName/getSelectedString/getDropRegionContext/isDrawAxisBorder/getAllAxisNames —
 *     private helpers with no independent entry point; exercised transitively.
 *   chartContainerWidth/Height/Top/Left/getBorderWidth/getPlotErrorStyle/noData/chartContainerVisible/
 *     *ScrollEnabled getters — Pass 3 (pure display computations).
 */

import { Subject } from "rxjs";
import { ChartTool } from "../model/chart-tool";
import { ChartArea } from "./chart-area.component";
import { Rectangle } from "../../common/data/rectangle";
import { Point } from "../../common/data/point";
import { GraphTypes } from "../../common/graph-types";
import { AreaResizeInfo } from "../model/area-resize-info";
import { LegendResizeInfo } from "../model/legend-resize-info";
import { createComponent, makeChartObject, makeRegion, makePlot, makeLegend, makeModel } from "./chart-area.component.test-helpers";

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: model setter [Risk 3]
// ---------------------------------------------------------------------------

describe("ChartArea — model setter", () => {
   it("should create a clearCanvasSubject when the model doesn't already have one", () => {
      const { comp } = createComponent();
      expect(comp.model.clearCanvasSubject).toBeTruthy();
   });

   it("should reuse an existing clearCanvasSubject instead of creating a new one", () => {
      const existing = new Subject<any>();
      const model = makeModel({ clearCanvasSubject: existing } as any);
      const { comp } = createComponent({ model });
      expect(comp.model.clearCanvasSubject).toBe(existing);
   });

   it("should initialize chartSelection when the model doesn't have one", () => {
      const model = makeModel({ chartSelection: null });
      const { comp } = createComponent({ model });
      expect(comp.model.chartSelection).toEqual({ chartObject: null, regions: null });
   });

   it("should reset imageError to false on every model assignment", () => {
      const { comp } = createComponent();
      (comp as any).imageError = true;
      comp.model = makeModel();
      expect(comp.imageError).toBe(false);
   });

   it("should clear all chart-object canvases when clearCanvasSubject fires", () => {
      const { comp } = createComponent();
      const clearCanvas = vi.fn();
      comp.chartObjectRef = [{ clearCanvas }] as any;

      comp.model.clearCanvasSubject.next(null);

      expect(clearCanvas).toHaveBeenCalled();
   });

   it("should unsubscribe the previous clearCanvasSubject subscription on reassignment", () => {
      const { comp } = createComponent();
      const firstSubject = comp.model.clearCanvasSubject;
      const clearCanvas = vi.fn();
      comp.chartObjectRef = [{ clearCanvas }] as any;

      comp.model = makeModel();
      clearCanvas.mockClear();
      firstSubject.next(null);

      expect(clearCanvas).not.toHaveBeenCalled();
   });

   it("should subscribe to pagingControlService scroll observables and sync scrollTop/scrollLeft for the current assembly", () => {
      const { comp, scrollTopSubject, scrollLeftSubject } = createComponent();
      scrollTopSubject.next(42);
      expect(comp.scrollTop).toBe(42);
      scrollLeftSubject.next(17);
      expect(comp.scrollLeft).toBe(17);
   });

   it("should ignore scroll updates for a different assembly", () => {
      const { comp, pagingControlService, scrollTopSubject } = createComponent();
      pagingControlService.getCurrentAssembly.mockReturnValue("OtherChart");
      comp.scrollTop = 0;

      scrollTopSubject.next(99);

      expect(comp.scrollTop).toBe(0);
   });
});

// ---------------------------------------------------------------------------
// Group 2: selected setter [Risk 2]
// ---------------------------------------------------------------------------

describe("ChartArea — selected setter", () => {
   it("should call showPagingControl when selected becomes true on a mobile device", () => {
      const { comp, pagingControlService } = createComponent();
      (comp as any).mobileDevice = true;
      comp.selected = true;
      expect(pagingControlService.setPagingControlModel).toHaveBeenCalled();
   });

   it("should NOT call showPagingControl when selected becomes false", () => {
      const { comp, pagingControlService } = createComponent();
      (comp as any).mobileDevice = true;
      comp.selected = false;
      expect(pagingControlService.setPagingControlModel).not.toHaveBeenCalled();
   });

   it("should reflect the assigned value via the getter", () => {
      const { comp } = createComponent();
      comp.selected = true;
      expect(comp.selected).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 3: ngOnInit / ngOnDestroy [Risk 2]
// ---------------------------------------------------------------------------

describe("ChartArea — ngOnInit / ngOnDestroy", () => {
   // jsdom does not implement matchMedia; ngOnInit() calls it unconditionally to watch for
   // devicePixelRatio changes.
   let originalMatchMedia: typeof window.matchMedia;

   beforeAll(() => {
      originalMatchMedia = window.matchMedia;
      window.matchMedia = vi.fn().mockReturnValue({
         addEventListener: vi.fn(),
         removeEventListener: vi.fn(),
      }) as any;
   });

   afterAll(() => {
      window.matchMedia = originalMatchMedia;
   });

   it("should register window resize and devicePixelRatio listeners on init", () => {
      const addEventListenerSpy = vi.spyOn(window, "addEventListener");
      const { comp } = createComponent();
      comp.ngOnInit();
      expect(addEventListenerSpy).toHaveBeenCalledWith("resize", expect.any(Function));
   });

   it("should remove the window resize listener and unsubscribe all subscriptions on destroy", () => {
      const { comp } = createComponent();
      comp.ngOnInit();
      const removeEventListenerSpy = vi.spyOn(window, "removeEventListener");
      const clearCanvasSub = (comp as any).clearCanvasSubscription;
      const scrollTopSub = (comp as any).scrollTopSubscription;
      const clearUnsubSpy = vi.spyOn(clearCanvasSub, "unsubscribe");
      const scrollUnsubSpy = vi.spyOn(scrollTopSub, "unsubscribe");

      comp.ngOnDestroy();

      expect(removeEventListenerSpy).toHaveBeenCalledWith("resize", expect.any(Function));
      expect(clearUnsubSpy).toHaveBeenCalled();
      expect(scrollUnsubSpy).toHaveBeenCalled();
   });

   it("should recompute scrollbarWidth and request change detection on window resize", () => {
      const { comp, changeDetectorRef } = createComponent();
      comp.ngOnInit();
      (comp as any).onResize();
      expect(changeDetectorRef.detectChanges).toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 4: ngOnChanges [Risk 2]
// ---------------------------------------------------------------------------

describe("ChartArea — ngOnChanges", () => {
   const modelTSChange = { currentValue: 1, previousValue: 0, firstChange: false, isFirstChange: () => false };

   it("should rebuild drop regions when modelTS changes", () => {
      const { comp } = createComponent();
      const initSpy = vi.spyOn(comp as any, "initDropRegions");
      comp.ngOnChanges({ modelTS: modelTSChange });
      expect(initSpy).toHaveBeenCalled();
   });

   it("should NOT rebuild drop regions when an unrelated input changes", () => {
      const { comp } = createComponent();
      const initSpy = vi.spyOn(comp as any, "initDropRegions");
      comp.ngOnChanges({ zIndex: modelTSChange });
      expect(initSpy).not.toHaveBeenCalled();
   });

   it("should nudge scrollLeft/scrollTop after a modelTS change to re-fire scroll bindings", () => {
      vi.useFakeTimers();
      try {
         const { comp } = createComponent();
         comp.scrollLeft = 5;
         comp.scrollTop = 8;
         comp.ngOnChanges({ modelTS: modelTSChange });
         vi.runAllTimers();
         expect(comp.scrollLeft).toBeCloseTo(5.00000001, 8);
         expect(comp.scrollTop).toBeCloseTo(8.00000001, 8);
      }
      finally {
         vi.useRealTimers();
      }
   });
});

// ---------------------------------------------------------------------------
// Group 5: resetScrollPosition [Risk 1]
// ---------------------------------------------------------------------------

describe("ChartArea — resetScrollPosition", () => {
   it("should reset both scroll positions to 0 when either plot is null", () => {
      const { comp } = createComponent();
      comp.scrollLeft = 10;
      comp.scrollTop = 20;
      comp.resetScrollPosition(null, makePlot());
      expect(comp.scrollLeft).toBe(0);
      expect(comp.scrollTop).toBe(0);
   });

   it("should reset scrollLeft and emit onScroll when the plot width changed", () => {
      const { comp } = createComponent();
      const emitted: Point[] = [];
      comp.onScroll.subscribe(p => emitted.push(p));
      comp.scrollLeft = 10;
      const oplot = makePlot({ bounds: new Rectangle(0, 0, 100, 50), layoutBounds: new Rectangle(0, 0, 100, 50) });
      const nplot = makePlot({ bounds: new Rectangle(0, 0, 200, 50), layoutBounds: new Rectangle(0, 0, 200, 50) });

      comp.resetScrollPosition(oplot, nplot);

      expect(comp.scrollLeft).toBe(0);
      expect(emitted.length).toBeGreaterThanOrEqual(1);
   });

   it("should leave scroll positions untouched when width and height are unchanged", () => {
      const { comp } = createComponent();
      comp.scrollLeft = 10;
      comp.scrollTop = 20;
      const bounds = new Rectangle(0, 0, 100, 50);
      comp.resetScrollPosition(makePlot({ bounds, layoutBounds: bounds }), makePlot({ bounds, layoutBounds: bounds }));
      expect(comp.scrollLeft).toBe(10);
      expect(comp.scrollTop).toBe(20);
   });
});

// ---------------------------------------------------------------------------
// Group 6: clearSelection / clearPan [Risk 1]
// ---------------------------------------------------------------------------

describe("ChartArea — clearSelection", () => {
   it("should clear the current selection, clear all canvases, and emit onSelectRegion with no argument", () => {
      const { comp } = createComponent();
      comp.model.chartSelection = { chartObject: makeChartObject("plot_area"), regions: [makeRegion()] };
      const clearCanvas = vi.fn();
      comp.chartObjectRef = [{ clearCanvas }] as any;
      const emitted: any[] = [];
      comp.onSelectRegion.subscribe(v => emitted.push(v));

      comp.clearSelection();

      expect(comp.model.chartSelection.regions).toEqual([]);
      expect(comp.model.chartSelection.chartObject).toBeNull();
      expect(clearCanvas).toHaveBeenCalled();
      expect(emitted).toEqual([undefined]);
   });

   it("should also clear the plot area's own selection when chartPlotArea is set", () => {
      const { comp } = createComponent();
      const clearSelection = vi.fn();
      (comp as any).chartPlotArea = { clearSelection };
      comp.clearSelection();
      expect(clearSelection).toHaveBeenCalled();
   });
});

describe("ChartArea — clearPan", () => {
   it("should delegate to chartPlotArea.clearPan when present", () => {
      const { comp } = createComponent();
      const clearPan = vi.fn();
      (comp as any).chartPlotArea = { clearPan };
      comp.clearPan();
      expect(clearPan).toHaveBeenCalled();
   });

   it("should not throw when chartPlotArea is not set", () => {
      const { comp } = createComponent();
      expect(() => comp.clearPan()).not.toThrow();
   });
});

// ---------------------------------------------------------------------------
// Group 7: selectRegion [Risk 3]
// ---------------------------------------------------------------------------

describe("ChartArea — selectRegion", () => {
   function basePayload(overrides: Record<string, any> = {}) {
      return {
         chartObject: makeChartObject("plot_area"),
         regions: [makeRegion({ rowIdx: 1 })],
         context: {} as CanvasRenderingContext2D,
         canvasX: 0,
         canvasY: 0,
         isCtrl: false,
         isShift: false,
         rangeSelection: false,
         ...overrides,
      };
   }

   beforeEach(() => {
      vi.spyOn(ChartTool, "drawRegions").mockImplementation(() => {});
   });

   it("should replace the selection on a plain click", () => {
      const { comp } = createComponent();
      comp.chartObjectRef = [] as any;
      const chartObject = makeChartObject("plot_area");
      comp.model.chartSelection = { chartObject, regions: [makeRegion({ rowIdx: 0 })] };
      const payload = basePayload({ chartObject, regions: [makeRegion({ rowIdx: 5 })] });

      comp.selectRegion(payload);

      expect(comp.model.chartSelection.regions.map(r => r.rowIdx)).toEqual([5]);
      expect(comp.model.chartSelection.chartObject).toBe(chartObject);
   });

   it("should append to the selection on ctrl+click of the same chartObject", () => {
      const { comp } = createComponent();
      comp.chartObjectRef = [] as any;
      const chartObject = makeChartObject("plot_area");
      const existing = makeRegion({ rowIdx: 0 });
      comp.model.chartSelection = { chartObject, regions: [existing] };
      const payload = basePayload({ chartObject, regions: [makeRegion({ rowIdx: 5 })], isCtrl: true });

      comp.selectRegion(payload);

      expect(comp.model.chartSelection.regions.map(r => r.rowIdx)).toEqual([0, 5]);
   });

   it("should replace (not append) on ctrl+click when the chartObject differs", () => {
      const { comp } = createComponent();
      comp.chartObjectRef = [] as any;
      comp.model.chartSelection = { chartObject: makeChartObject("legend_content"), regions: [makeRegion({ rowIdx: 0 })] };
      const payload = basePayload({ regions: [makeRegion({ rowIdx: 5 })], isCtrl: true });

      comp.selectRegion(payload);

      expect(comp.model.chartSelection.regions.map(r => r.rowIdx)).toEqual([5]);
   });

   it("should range-select from the first selected region to the clicked region on shift+click", () => {
      const { comp } = createComponent();
      comp.chartObjectRef = [] as any;
      const regions = [makeRegion({ rowIdx: 0 }), makeRegion({ rowIdx: 1 }), makeRegion({ rowIdx: 2 }), makeRegion({ rowIdx: 3 })];
      const chartObject = makeChartObject("plot_area", { regions });
      comp.model.chartSelection = { chartObject, regions: [regions[0]] };
      const payload = basePayload({ chartObject, regions: [regions[2]], isShift: true });

      comp.selectRegion(payload);

      expect(comp.model.chartSelection.regions).toEqual([regions[0], regions[1], regions[2]]);
   });

   it("should expand the selection to all points on the same line for radar charts", () => {
      const { comp } = createComponent({ model: makeModel({ chartType: GraphTypes.CHART_RADAR }) });
      comp.chartObjectRef = [] as any;
      const lineRegion = makeRegion({ rowIdx: 7, metaIdx: undefined as any });
      const otherPointOnLine = makeRegion({ rowIdx: 7, metaIdx: undefined as any });
      comp.model.plot.regions = [lineRegion, otherPointOnLine];
      comp.model.chartSelection = { chartObject: makeChartObject("plot_area"), regions: [] };
      const payload = basePayload({ chartObject: makeChartObject("plot_area"), regions: [lineRegion] });

      comp.selectRegion(payload);

      expect(comp.model.chartSelection.regions).toContain(otherPointOnLine);
   });

   it("should emit onSelectRegion with the updated chartSelection", () => {
      const { comp } = createComponent();
      comp.chartObjectRef = [] as any;
      comp.model.chartSelection = { chartObject: makeChartObject("plot_area"), regions: [] };
      const emitted: any[] = [];
      comp.onSelectRegion.subscribe(v => emitted.push(v));

      comp.selectRegion(basePayload());

      expect(emitted).toEqual([comp.model.chartSelection]);
   });

   it("should clear canvases on all chart object refs", () => {
      const { comp } = createComponent();
      const clearCanvas = vi.fn();
      comp.chartObjectRef = [{ clearCanvas }] as any;
      comp.model.chartSelection = { chartObject: makeChartObject("plot_area"), regions: [] };

      comp.selectRegion(basePayload());

      expect(clearCanvas).toHaveBeenCalled();
   });

   // Note: `AbstractVSActions.isActionVisible(actionNames, name)` returns
   // `!(actionNames.indexOf(name) >= 0)` — actionNames is a list of DISABLED action names, so an
   // action's presence in the array means it is hidden/disabled, not enabled.
   it("should skip drawing the selection for map charts when MapSelectionEnabled is disabled (present in actionNames)", () => {
      const { comp } = createComponent({
         model: makeModel({ chartType: GraphTypes.CHART_MAP, actionNames: ["MapSelectionEnabled"] } as any)
      });
      comp.chartObjectRef = [] as any;
      comp.model.chartSelection = { chartObject: makeChartObject("plot_area"), regions: [] };

      comp.selectRegion(basePayload());

      expect(ChartTool.drawRegions).not.toHaveBeenCalled();
   });

   it("should draw the selection for map charts when MapSelectionEnabled is not disabled (absent from actionNames)", () => {
      const { comp } = createComponent({ model: makeModel({ chartType: GraphTypes.CHART_MAP, actionNames: [] } as any) });
      comp.chartObjectRef = [] as any;
      comp.model.chartSelection = { chartObject: makeChartObject("plot_area"), regions: [] };

      comp.selectRegion(basePayload());

      expect(ChartTool.drawRegions).toHaveBeenCalled();
   });

   it("should clear the plot's flyover selection when the newly selected area is not the plot and hasFlyovers is true", () => {
      const { comp } = createComponent({ model: makeModel({ hasFlyovers: true } as any) });
      comp.chartObjectRef = [] as any;
      comp.model.chartSelection = { chartObject: makeChartObject("plot_area"), regions: [] };
      const clearSelection = vi.fn();
      (comp as any).chartPlotArea = { clearSelection };
      const payload = basePayload({ chartObject: makeChartObject("legend_content") });

      comp.selectRegion(payload);

      expect(clearSelection).toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 8: sendFlyover [Risk 2]
// ---------------------------------------------------------------------------

describe("ChartArea — sendFlyover", () => {
   it("should use the payload's own selection when there is no current axis/plot selection", () => {
      const { comp } = createComponent();
      vi.spyOn(ChartTool, "getSelectedAxisRegions").mockReturnValue([]);
      const getSelectedStringSpy = vi.spyOn(comp as any, "getSelectedString").mockReturnValue("cond");
      const payload = { chartObject: makeChartObject("plot_area"), regions: [makeRegion()] } as any;

      comp.sendFlyover(payload);

      expect(getSelectedStringSpy).toHaveBeenCalledWith(payload);
      expect((comp as any).flyoverApplied).toBe(true);
   });

   it("should use the current selection instead of the payload when a plot selection already exists", () => {
      const { comp } = createComponent();
      vi.spyOn(ChartTool, "getSelectedAxisRegions").mockReturnValue([]);
      comp.model.chartSelection = { chartObject: makeChartObject("plot_area"), regions: [makeRegion()] };
      const getSelectedStringSpy = vi.spyOn(comp as any, "getSelectedString").mockReturnValue("cond");
      const payload = { chartObject: makeChartObject("plot_area"), regions: [makeRegion()] } as any;

      comp.sendFlyover(payload);

      expect(getSelectedStringSpy).toHaveBeenCalledWith(undefined);
   });

   it("should emit onSendFlyover with noCurrentSelection/plotCondition/payload", () => {
      const { comp } = createComponent();
      vi.spyOn(ChartTool, "getSelectedAxisRegions").mockReturnValue([]);
      vi.spyOn(comp as any, "getSelectedString").mockReturnValue("cond-str");
      const emitted: any[] = [];
      comp.onSendFlyover.subscribe(v => emitted.push(v));
      const payload = { chartObject: makeChartObject("plot_area"), regions: [] } as any;

      comp.sendFlyover(payload);

      expect(emitted).toEqual([{ noCurrentSelection: true, plotCondition: "cond-str", payload }]);
   });
});

// ---------------------------------------------------------------------------
// Group 9: showDataTip [Risk 1]
// ---------------------------------------------------------------------------

describe("ChartArea — showDataTip", () => {
   it("should emit onShowDataTip with the payload and current tooltip position", () => {
      const { comp } = createComponent();
      comp.tooltipLeft = 12;
      comp.tooltipTop = 34;
      const emitted: any[] = [];
      comp.onShowDataTip.subscribe(v => emitted.push(v));
      const payload = { chartObject: makeChartObject("plot_area"), regions: [] } as any;

      comp.showDataTip(payload);

      expect(emitted).toEqual([{ payload, tooltipLeft: 12, tooltipTop: 34 }]);
   });
});

// ---------------------------------------------------------------------------
// Group 10: showTooltip [Risk 2]
// ---------------------------------------------------------------------------

describe("ChartArea — showTooltip", () => {
   it("should set tooltipString from the stringDictionary and request change detection when it changes", () => {
      const { comp, changeDetectorRef } = createComponent();
      comp.showTooltip({ tipIndex: 0, region: makeRegion() });
      expect(comp.tooltipString).toBe("Tooltip A");
      expect(changeDetectorRef.detectChanges).toHaveBeenCalled();
   });

   it("should NOT request change detection when the tooltip text is unchanged", () => {
      const { comp, changeDetectorRef } = createComponent();
      comp.showTooltip({ tipIndex: 0, region: makeRegion() });
      changeDetectorRef.detectChanges.mockClear();

      comp.showTooltip({ tipIndex: 0, region: makeRegion() });

      expect(changeDetectorRef.detectChanges).not.toHaveBeenCalled();
   });

   it("should append the ctrl-select hint when a single hyperlink is present (desktop only)", () => {
      const { comp } = createComponent();
      (comp as any).mobileDevice = false;
      const region = makeRegion({ hyperlinks: [{ name: "link1" } as any] });
      comp.showTooltip({ tipIndex: 0, region });
      expect(comp.tooltipString).toContain("Tooltip A");
      expect(comp.tooltipString).not.toBe("Tooltip A"); // hint text appended
   });

   it("should append the empty-plot-link tooltip when there is no tipInfo and the call came from the plot in viewer mode", () => {
      const { comp } = createComponent();
      (comp as any).mobileDevice = false;
      comp.emptyPlotLinkTooltip = " (click for details)";
      comp.viewerMode = true;

      comp.showTooltip(null, true);

      expect(comp.tooltipString).toContain("(click for details)");
   });
});

// ---------------------------------------------------------------------------
// Group 11: onWrapperScroll / onScrollArea / onScrollAxis [Risk 2]
// ---------------------------------------------------------------------------

describe("ChartArea — onWrapperScroll", () => {
   it("should update scrollLeft and prevent default for horizontal scroll", () => {
      const { comp } = createComponent();
      const emitted: Point[] = [];
      comp.onScroll.subscribe(p => emitted.push(p));
      const event = { target: { scrollLeft: 30, scrollTop: 0 }, preventDefault: vi.fn() };

      comp.onWrapperScroll(event, true);

      expect(event.preventDefault).toHaveBeenCalled();
      expect(comp.scrollLeft).toBe(30);
      expect(emitted).toEqual([new Point(30, 0)]);
   });

   it("should update scrollTop for vertical scroll", () => {
      const { comp } = createComponent();
      const event = { target: { scrollLeft: 0, scrollTop: 15 }, preventDefault: vi.fn() };
      comp.onWrapperScroll(event, false);
      expect(comp.scrollTop).toBe(15);
   });
});

describe("ChartArea — onScrollArea", () => {
   it("should sync scrollLeft/scrollTop, clear the tooltip, and emit onScroll", () => {
      const { comp } = createComponent();
      comp.tooltipString = "old tip";
      const emitted: Point[] = [];
      comp.onScroll.subscribe(p => emitted.push(p));

      comp.onScrollArea({ target: { scrollLeft: 5, scrollTop: 6 } });

      expect(comp.scrollLeft).toBe(5);
      expect(comp.scrollTop).toBe(6);
      expect(comp.tooltipString).toBeNull();
      expect(emitted).toEqual([new Point(5, 6)]);
   });
});

describe("ChartArea — onScrollAxis", () => {
   it("should update only scrollLeft when axis.axisType is 'x'", () => {
      const { comp } = createComponent();
      comp.scrollTop = 99;
      comp.onScrollAxis({ target: { scrollLeft: 7, scrollTop: 8 } }, { axisType: "x" } as any);
      expect(comp.scrollLeft).toBe(7);
      expect(comp.scrollTop).toBe(99);
   });

   it("should update only scrollTop when axis.axisType is not 'x'", () => {
      const { comp } = createComponent();
      comp.scrollLeft = 99;
      comp.onScrollAxis({ target: { scrollLeft: 7, scrollTop: 8 } }, { axisType: "y" } as any);
      expect(comp.scrollTop).toBe(8);
      expect(comp.scrollLeft).toBe(99);
   });
});

// ---------------------------------------------------------------------------
// Group 12: onDown / onMove [Risk 1]
// ---------------------------------------------------------------------------

describe("ChartArea — onDown", () => {
   it("should record the mouse-down position", () => {
      const { comp } = createComponent();
      comp.onDown({ clientX: 11, clientY: 22 } as MouseEvent);
      expect(comp.eventXdown).toBe(11);
      expect(comp.eventYdown).toBe(22);
   });

   it("should initialize the resize line and stop propagation when an area resize is in progress", () => {
      const { comp } = createComponent();
      (comp as any)._clientRect = { left: 0, top: 0 } as ClientRect;
      comp.areaResizeInfo = { type: "axis" } as AreaResizeInfo;
      comp.resizeVertical = true;
      const event = { clientX: 50, clientY: 0, stopPropagation: vi.fn() } as unknown as MouseEvent;

      comp.onDown(event);

      expect(event.stopPropagation).toHaveBeenCalled();
      expect(comp.resizeLineLeft).toBe(50);
   });

   it("should NOT touch the resize line when no area resize is in progress", () => {
      const { comp } = createComponent();
      comp.resizeLineLeft = 0;
      comp.onDown({ clientX: 50, clientY: 0, stopPropagation: vi.fn() } as unknown as MouseEvent);
      expect(comp.resizeLineLeft).toBe(0);
   });
});

describe("ChartArea — onMove", () => {
   it("should track the mouse position for tooltip placement", () => {
      const { comp } = createComponent();
      comp.onMove({ clientX: 100, clientY: 200 } as MouseEvent);
      expect(comp.tooltipLeft).toBe(100);
      expect(comp.tooltipTop).toBe(200);
   });
});

// ---------------------------------------------------------------------------
// Group 13: onDrop / dragOver / dragLeave [Risk 2]
// ---------------------------------------------------------------------------

describe("ChartArea — dragOver / dragLeave", () => {
   function makeDragEvent(overrides: Record<string, any> = {}) {
      return {
         clientX: 10, clientY: 10,
         currentTarget: { getBoundingClientRect: () => ({ left: 0, top: 0, width: 100, height: 100 }) },
         preventDefault: vi.fn(),
         ...overrides,
      } as any;
   }

   it("should draw the drop region and prevent default when the chart is not empty", () => {
      const { comp } = createComponent();
      comp.emptyChart = false;
      comp.dropRegionCanvas = { nativeElement: document.createElement("canvas") } as any;
      const drawSpy = vi.spyOn(comp as any, "drawDropRegion").mockImplementation(() => {});

      const event = makeDragEvent();
      comp.dragOver(event);

      expect(drawSpy).toHaveBeenCalled();
      expect(event.preventDefault).toHaveBeenCalled();
   });

   it("should do nothing when the chart is empty", () => {
      const { comp } = createComponent();
      comp.emptyChart = true;
      const drawSpy = vi.spyOn(comp as any, "drawDropRegion").mockImplementation(() => {});

      comp.dragOver(makeDragEvent());

      expect(drawSpy).not.toHaveBeenCalled();
   });

   it("should reset dropType and clear the canvas on dragLeave", () => {
      const { comp, chartService } = createComponent();
      comp.emptyChart = false;
      comp.dropRegionCanvas = { nativeElement: document.createElement("canvas") } as any;
      (comp as any).dropType = 3;

      comp.dragLeave(makeDragEvent());

      expect((comp as any).dropType).toBe(-1);
      expect(chartService.clearCanvas).toHaveBeenCalled();
   });
});

describe("ChartArea — onDrop", () => {
   function makeDropEvent(text: string | null) {
      return {
         preventDefault: vi.fn(),
         dataTransfer: { getData: vi.fn(() => text ?? "") },
      } as any;
   }

   it("should clear the canvas and do nothing when dropType is -1 (no active drop target)", () => {
      const { comp, chartService, dndService } = createComponent();
      comp.emptyChart = false;
      comp.dropRegionCanvas = { nativeElement: document.createElement("canvas") } as any;
      (comp as any).dropType = -1;

      comp.onDrop(makeDropEvent(JSON.stringify({ column: "x" })));

      expect(chartService.clearCanvas).toHaveBeenCalled();
      expect(dndService.processOnDrop).not.toHaveBeenCalled();
   });

   it("should clear the canvas and do nothing when the drop has no column data", () => {
      const { comp, chartService, dndService } = createComponent();
      comp.emptyChart = false;
      comp.dropRegionCanvas = { nativeElement: document.createElement("canvas") } as any;
      (comp as any).dropType = 1;
      vi.spyOn(comp as any, "isDropAcceptable").mockReturnValue(true);

      comp.onDrop(makeDropEvent(null));

      expect(chartService.clearCanvas).toHaveBeenCalled();
      expect(dndService.processOnDrop).not.toHaveBeenCalled();
   });

   it("should process the drop and clear the canvas when a valid column is dropped on an acceptable target", () => {
      const { comp, chartService, dndService } = createComponent();
      comp.emptyChart = false;
      comp.dropRegionCanvas = { nativeElement: document.createElement("canvas") } as any;
      (comp as any).dropType = 1;
      vi.spyOn(comp as any, "isDropAcceptable").mockReturnValue(true);

      const event = makeDropEvent(JSON.stringify({ column: "SomeColumn" }));
      comp.onDrop(event);

      expect(dndService.processOnDrop).toHaveBeenCalled();
      expect(chartService.clearCanvas).toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 14: onAxisDrop / onAxisEnter / onAxisLeave [Risk 1]
// ---------------------------------------------------------------------------

describe("ChartArea — onAxisEnter / onAxisLeave", () => {
   it("should increment xAxisHover for the x axis and yAxisHover for the y axis", () => {
      const { comp } = createComponent();
      comp.onAxisEnter(true);
      expect(comp.xAxisHover).toBe(1);
      comp.onAxisEnter(false);
      expect(comp.yAxisHover).toBe(1);
   });

   it("should decrement xAxisHover/yAxisHover on leave", () => {
      const { comp } = createComponent();
      comp.xAxisHover = 1;
      comp.yAxisHover = 1;
      comp.onAxisLeave(true);
      comp.onAxisLeave(false);
      expect(comp.xAxisHover).toBe(0);
      expect(comp.yAxisHover).toBe(0);
   });
});

describe("ChartArea — onAxisDrop", () => {
   it("should reset hover counters and delegate to onDrop with the X drop type", () => {
      const { comp } = createComponent();
      comp.xAxisHover = 3;
      comp.yAxisHover = 3;
      const onDropSpy = vi.spyOn(comp, "onDrop").mockImplementation(() => {});

      comp.onAxisDrop({ preventDefault: vi.fn() } as any, true);

      expect(comp.xAxisHover).toBe(0);
      expect(comp.yAxisHover).toBe(0);
      expect(onDropSpy).toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 15: drill / sortAxis / showHyperlink [Risk 1]
// ---------------------------------------------------------------------------

describe("ChartArea — drill / sortAxis / showHyperlink", () => {
   it("should emit onDrill with the payload", () => {
      const { comp } = createComponent();
      const emitted: any[] = [];
      comp.onDrill.subscribe(v => emitted.push(v));
      const payload = { name: "Field1" } as any;
      comp.drill(payload);
      expect(emitted).toEqual([payload]);
   });

   it("should emit onSortAxis with the axis", () => {
      const { comp } = createComponent();
      const emitted: any[] = [];
      comp.onSortAxis.subscribe(v => emitted.push(v));
      const axis = { axisType: "x" } as any;
      comp.sortAxis(axis);
      expect(emitted).toEqual([axis]);
   });

   it("should emit onShowHyperlink with the event on desktop", () => {
      const { comp } = createComponent();
      (comp as any).mobileDevice = false;
      const emitted: MouseEvent[] = [];
      comp.onShowHyperlink.subscribe(v => emitted.push(v));
      const event = new MouseEvent("click");
      comp.showHyperlink(event);
      expect(emitted).toEqual([event]);
   });
});

// ---------------------------------------------------------------------------
// Group 16: showPagingControl / paging [Risk 1]
// ---------------------------------------------------------------------------

describe("ChartArea — showPagingControl", () => {
   it("should push a paging control model when on a mobile device", () => {
      const { comp, pagingControlService } = createComponent();
      (comp as any).mobileDevice = true;
      comp.showPagingControl();
      expect(pagingControlService.setPagingControlModel).toHaveBeenCalledWith(
         expect.objectContaining({ assemblyName: "Chart1", enabled: true })
      );
   });

   it("should do nothing on a non-mobile device", () => {
      const { comp, pagingControlService } = createComponent();
      (comp as any).mobileDevice = false;
      comp.showPagingControl();
      expect(pagingControlService.setPagingControlModel).not.toHaveBeenCalled();
   });
});

describe("ChartArea — pageUp/Down/Left/Right", () => {
   it("should clamp pageDown to the bottom of the plot content", () => {
      const { comp } = createComponent();
      comp.plotRegion = new Rectangle(0, 0, 100, 100);
      comp.model.plot.bounds = new Rectangle(0, 0, 100, 110);
      comp.scrollTop = 0;
      comp.pageDown();
      expect(comp.scrollTop).toBe(10); // clamped to bounds.height - plotRegion.height
   });

   it("should clamp pageUp to a minimum of 0", () => {
      const { comp } = createComponent();
      comp.plotRegion = new Rectangle(0, 0, 100, 100);
      comp.scrollTop = 3;
      comp.pageUp();
      expect(comp.scrollTop).toBe(0);
   });

   it("should clamp pageRight to the right edge of the plot content", () => {
      const { comp } = createComponent();
      comp.plotRegion = new Rectangle(0, 0, 100, 100);
      comp.model.plot.bounds = new Rectangle(0, 0, 110, 100);
      comp.scrollLeft = 0;
      comp.pageRight();
      expect(comp.scrollLeft).toBe(10);
   });

   it("should clamp pageLeft to a minimum of 0", () => {
      const { comp } = createComponent();
      comp.plotRegion = new Rectangle(0, 0, 100, 100);
      comp.scrollLeft = 3;
      comp.pageLeft();
      expect(comp.scrollLeft).toBe(0);
   });
});

// ---------------------------------------------------------------------------
// Group 17: touchStart / touchMove / touchEnd (pinch zoom) [Risk 2]
// ---------------------------------------------------------------------------

describe("ChartArea — touch pinch zoom", () => {
   function makeTouchEvent(points: Array<{ pageX: number, pageY: number }>) {
      return {
         touches: points,
         stopImmediatePropagation: vi.fn(),
         preventDefault: vi.fn(),
      } as unknown as TouchEvent;
   }

   it("should record the initial pinch distance on a two-finger touchStart", () => {
      const { comp } = createComponent();
      const event = makeTouchEvent([{ pageX: 0, pageY: 0 }, { pageX: 3, pageY: 4 }]);
      comp.touchStart(event);
      expect((comp as any).pinchStart).toBe(5); // 3-4-5 triangle
      expect(event.preventDefault).toHaveBeenCalled();
   });

   it("should ignore single-finger touchStart", () => {
      const { comp } = createComponent();
      comp.touchStart(makeTouchEvent([{ pageX: 0, pageY: 0 }]));
      expect((comp as any).pinchStart).toBe(0);
   });

   it("should emit onZoomIn when fingers move apart (pinchMove > pinchStart)", () => {
      const { comp } = createComponent();
      const emitted: number[] = [];
      comp.onZoomIn.subscribe(v => emitted.push(v));
      comp.touchStart(makeTouchEvent([{ pageX: 0, pageY: 0 }, { pageX: 3, pageY: 4 }])); // dist=5
      comp.touchMove(makeTouchEvent([{ pageX: 0, pageY: 0 }, { pageX: 6, pageY: 8 }]));  // dist=10

      comp.touchEnd(makeTouchEvent([]));

      expect(emitted).toEqual([1]); // ceil((10-5)/10)
   });

   it("should emit onZoomOut when fingers move together (pinchMove < pinchStart)", () => {
      const { comp } = createComponent();
      const emitted: number[] = [];
      comp.onZoomOut.subscribe(v => emitted.push(v));
      comp.touchStart(makeTouchEvent([{ pageX: 0, pageY: 0 }, { pageX: 6, pageY: 8 }])); // dist=10
      comp.touchMove(makeTouchEvent([{ pageX: 0, pageY: 0 }, { pageX: 3, pageY: 4 }]));  // dist=5

      comp.touchEnd(makeTouchEvent([]));

      expect(emitted).toEqual([1]); // ceil((10-5)/10)
   });

   it("should reset pinch state after touchEnd", () => {
      const { comp } = createComponent();
      comp.touchStart(makeTouchEvent([{ pageX: 0, pageY: 0 }, { pageX: 3, pageY: 4 }]));
      comp.touchMove(makeTouchEvent([{ pageX: 0, pageY: 0 }, { pageX: 6, pageY: 8 }]));
      comp.touchEnd(makeTouchEvent([]));
      expect((comp as any).pinchStart).toBe(0);
      expect((comp as any).pinchMove).toBe(0);
   });

   it("should not emit any zoom event when there was no pinch gesture", () => {
      const { comp } = createComponent();
      const zoomIn: number[] = [];
      const zoomOut: number[] = [];
      comp.onZoomIn.subscribe(v => zoomIn.push(v));
      comp.onZoomOut.subscribe(v => zoomOut.push(v));
      comp.touchEnd(makeTouchEvent([]));
      expect(zoomIn).toHaveLength(0);
      expect(zoomOut).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 18: resize flow [Risk 2]
// ---------------------------------------------------------------------------

describe("ChartArea — axis/legend resize flow", () => {
   function withClientRect(comp: ChartArea) {
      (comp as any)._chartContainer = null;
      (comp as any)._clientRect = { left: 10, top: 20, right: 0, bottom: 0, width: 0, height: 0 } as ClientRect;
   }

   it("should record resizeVertical=true and request change detection on startAxisResize for a y axis", () => {
      const { comp, changeDetectorRef } = createComponent();
      const payload = { chartObject: { axisType: "y" }, regions: [] } as any;
      comp.startAxisResize(payload);
      expect(comp.areaResizeInfo).toBe(payload);
      expect(comp.resizeVertical).toBe(true);
      expect(changeDetectorRef.detectChanges).toHaveBeenCalled();
   });

   it("should record resizeVertical=false on startAxisResize for an x axis", () => {
      const { comp } = createComponent();
      comp.startAxisResize({ chartObject: { axisType: "x" }, regions: [] } as any);
      expect(comp.resizeVertical).toBe(false);
   });

   it("should record the resize orientation from the payload on startLegendResize", () => {
      const { comp, changeDetectorRef } = createComponent();
      const payload = { vertical: true, type: "legend" } as unknown as AreaResizeInfo;
      comp.startLegendResize(payload);
      expect(comp.areaResizeInfo).toBe(payload);
      expect(comp.resizeVertical).toBe(true);
      expect(changeDetectorRef.detectChanges).toHaveBeenCalled();
   });

   it("should emit onEndAxisResize with chart-relative coordinates and clear resize state", () => {
      const { comp, changeDetectorRef } = createComponent();
      withClientRect(comp);
      comp.axisResize_ = { nativeElement: document.createElement("div") } as any;
      comp.eventXdown = 15;
      comp.eventYdown = 25;
      comp.areaResizeInfo = { type: "axis" } as AreaResizeInfo;
      const emitted: any[] = [];
      comp.onEndAxisResize.subscribe(v => emitted.push(v));

      comp.endAxisResize({ clientX: 30, clientY: 40 } as MouseEvent);

      expect(emitted).toEqual([{
         axisResizeInfo: expect.objectContaining({ type: "axis" }),
         eventXdown: 5, eventYdown: 5, clientX: 20, clientY: 20,
      }]);
      expect(comp.areaResizeInfo).toBeNull();
      expect(changeDetectorRef.detectChanges).toHaveBeenCalled();
   });

   it("should emit onEndLegendResize with legend aesthetic fields and clear resize state", () => {
      const { comp } = createComponent();
      withClientRect(comp);
      comp.axisResize_ = { nativeElement: document.createElement("div") } as any;
      comp.eventXdown = 0;
      comp.eventYdown = 0;
      comp.areaResizeInfo = { type: "legend" } as AreaResizeInfo;
      const legend = makeLegend({ aestheticType: "shape", field: "F1", targetFields: ["F1", "F2"] });
      const emitted: any[] = [];
      comp.onEndLegendResize.subscribe(v => emitted.push(v));

      comp.endLegendResize({ clientX: 5, clientY: 5 } as MouseEvent, legend);

      expect(emitted[0]).toEqual(expect.objectContaining({
         aestheticType: "shape", field: "F1", targetFields: ["F1", "F2"],
      }));
      expect(comp.areaResizeInfo).toBeNull();
   });

   it("should dispatch endAreaResize to endAxisResize when areaResizeInfo.type is 'axis'", () => {
      const { comp } = createComponent();
      withClientRect(comp);
      comp.axisResize_ = { nativeElement: document.createElement("div") } as any;
      comp.areaResizeInfo = { type: "axis" } as AreaResizeInfo;
      const endAxisResizeSpy = vi.spyOn(comp, "endAxisResize").mockImplementation(() => {});

      comp.endAreaResize({ clientX: 0, clientY: 0 } as MouseEvent);

      expect(endAxisResizeSpy).toHaveBeenCalled();
   });

   it("should dispatch endAreaResize to endLegendResize when areaResizeInfo.type is 'legend'", () => {
      const { comp } = createComponent();
      const legend = makeLegend();
      comp.areaResizeInfo = { type: "legend", legend } as LegendResizeInfo;
      const endLegendResizeSpy = vi.spyOn(comp, "endLegendResize").mockImplementation(() => {});

      comp.endAreaResize({ clientX: 0, clientY: 0 } as MouseEvent);

      expect(endLegendResizeSpy).toHaveBeenCalledWith(expect.anything(), legend);
   });

   it("should emit onEndLegendMove with the interact info and legend aesthetic fields", () => {
      const { comp } = createComponent();
      const legend = makeLegend({ aestheticType: "size" });
      const emitted: any[] = [];
      comp.onEndLegendMove.subscribe(v => emitted.push(v));
      const interactInfo = { dx: 1, dy: 2 } as any;

      comp.endLegendMove(interactInfo, legend);

      expect(emitted[0]).toEqual(expect.objectContaining({ interactInfo, aestheticType: "size" }));
   });
});

// ---------------------------------------------------------------------------
// Group 19: loading state machine [Risk 2]
// ---------------------------------------------------------------------------

describe("ChartArea — axisLoading/axisLoaded/plotLoading/plotLoaded", () => {
   // fireLoading()'s gate is `!(axisLoaded || plotLoaded)` — the INVERSE of fireLoaded()'s gate
   // (`axisLoaded && plotLoaded`). onLoading only fires once BOTH axis and plot are concurrently
   // loading (a full reload), not merely when one of them starts (e.g. a lone axis re-sort).
   it("should NOT emit onLoading when only the axis starts loading and the plot is still loaded", () => {
      const { comp } = createComponent();
      const emitted: any[] = [];
      comp.onLoading.subscribe(v => emitted.push(v));
      comp.axisLoading("bottom_x_axis");
      expect(emitted).toHaveLength(0);
   });

   it("should emit onLoading once both the axis and the plot are loading", () => {
      const { comp } = createComponent();
      const emitted: any[] = [];
      comp.onLoading.subscribe(v => emitted.push(v));
      comp.axisLoading("bottom_x_axis");
      comp.plotLoading();
      expect(emitted).toEqual([true]);
   });

   it("should NOT emit onLoad while an axis is still loading", () => {
      const { comp } = createComponent();
      const emitted: any[] = [];
      comp.onLoad.subscribe(v => emitted.push(v));
      comp.axisLoading("bottom_x_axis");
      expect(emitted).toHaveLength(0);
   });

   it("should emit onLoad once the loading axis reports axisLoaded(true) and the plot is already loaded", () => {
      const { comp } = createComponent();
      comp.axisLoading("bottom_x_axis");
      const emitted: any[] = [];
      comp.onLoad.subscribe(v => emitted.push(v));

      comp.axisLoaded(true, "bottom_x_axis");

      expect(emitted).toEqual([true]);
   });

   it("should emit onError instead of onLoad when axisLoaded(false) reports a failure", () => {
      const { comp } = createComponent();
      comp.axisLoading("bottom_x_axis");
      const loadEmitted: any[] = [];
      const errorEmitted: any[] = [];
      comp.onLoad.subscribe(v => loadEmitted.push(v));
      comp.onError.subscribe(v => errorEmitted.push(v));

      comp.axisLoaded(false, "bottom_x_axis");

      expect(errorEmitted).toEqual([true]);
      expect(loadEmitted).toHaveLength(0);
   });

   it("should treat an empty-string areaName as a sentinel that clears all pending axes", () => {
      const { comp } = createComponent();
      comp.axisLoading("bottom_x_axis");
      comp.axisLoading("left_y_axis");
      const emitted: any[] = [];
      comp.onLoad.subscribe(v => emitted.push(v));

      comp.axisLoaded(true, "");

      expect(emitted).toEqual([true]);
   });

   it("should NOT emit onLoading when only the plot starts loading and the axis is still loaded", () => {
      const { comp } = createComponent();
      const emitted: any[] = [];
      comp.onLoading.subscribe(v => emitted.push(v));
      comp.plotLoading();
      expect(emitted).toHaveLength(0);
   });

   it("should only emit onLoad once BOTH axis and plot report loaded", () => {
      const { comp } = createComponent();
      comp.axisLoading("bottom_x_axis");
      comp.plotLoading();
      const emitted: any[] = [];
      comp.onLoad.subscribe(v => emitted.push(v));

      comp.plotLoaded(true);
      expect(emitted).toHaveLength(0); // axis still pending

      comp.axisLoaded(true, "bottom_x_axis");
      expect(emitted).toEqual([true]);
   });
});

// ---------------------------------------------------------------------------
// Group 20: changeCursor / selectLegendBackground / clearSelectionOnBackground [Risk 1]
// ---------------------------------------------------------------------------

describe("ChartArea — changeCursor", () => {
   it("should update currentCursor and request change detection when the cursor changes", () => {
      const { comp, changeDetectorRef } = createComponent();
      comp.changeCursor("pointer");
      expect(comp.currentCursor).toBe("pointer");
      expect(changeDetectorRef.detectChanges).toHaveBeenCalled();
   });

   it("should NOT request change detection when the cursor is unchanged", () => {
      const { comp, changeDetectorRef } = createComponent();
      comp.changeCursor("pointer");
      changeDetectorRef.detectChanges.mockClear();
      comp.changeCursor("pointer");
      expect(changeDetectorRef.detectChanges).not.toHaveBeenCalled();
   });
});

describe("ChartArea — selectLegendBackground", () => {
   it("should clear the selection when clicking directly on the background element", () => {
      const { comp } = createComponent();
      const clearSelectionSpy = vi.spyOn(comp, "clearSelection").mockImplementation(() => {});
      const target = document.createElement("div");
      comp.selectLegendBackground({ target, currentTarget: target } as unknown as MouseEvent);
      expect(clearSelectionSpy).toHaveBeenCalled();
   });

   it("should NOT clear the selection when clicking on a child element", () => {
      const { comp } = createComponent();
      const clearSelectionSpy = vi.spyOn(comp, "clearSelection").mockImplementation(() => {});
      comp.selectLegendBackground({ target: document.createElement("span"), currentTarget: document.createElement("div") } as unknown as MouseEvent);
      expect(clearSelectionSpy).not.toHaveBeenCalled();
   });
});

describe("ChartArea — clearSelectionOnBackground", () => {
   it("should clear the selection when the click target is outside any .chart-object-area", () => {
      const { comp } = createComponent();
      const clearSelectionSpy = vi.spyOn(comp, "clearSelection").mockImplementation(() => {});
      const target = document.createElement("div");
      comp.clearSelectionOnBackground({ target } as unknown as MouseEvent);
      expect(clearSelectionSpy).toHaveBeenCalled();
   });

   it("should NOT clear the selection when the click target is inside a .chart-object-area", () => {
      const { comp } = createComponent();
      const clearSelectionSpy = vi.spyOn(comp, "clearSelection").mockImplementation(() => {});
      const container = document.createElement("div");
      container.className = "chart-object-area";
      const target = document.createElement("span");
      container.appendChild(target);
      comp.clearSelectionOnBackground({ target } as unknown as MouseEvent);
      expect(clearSelectionSpy).not.toHaveBeenCalled();
   });
});
