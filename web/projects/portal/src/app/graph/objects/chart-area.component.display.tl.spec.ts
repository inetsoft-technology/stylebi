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
 * ChartArea — Pass 3: Display
 *
 * Uses the same direct-instantiation fixtures as Pass 1/2 (chart-area.component.test-helpers.ts).
 *
 * Scope (per prescan Pass 3 method list, dispatchPoints=3): drop-type acceptance/hit-detection
 * dispatch (isDropAcceptable, drawDropRegion, isXAcceptable/isYAcceptable/isGeoAcceptable,
 * isDrawAxisBorder), isY2Supported's 15-chart-type exclusion list, and the pure display/layout
 * getters (paintNoDataChart, noData, chartContainer*, getBorderWidth, getPlotErrorStyle,
 * chartContainerVisible, isNavMap, getLegendContentHeight, *ScrollEnabled).
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — isDropAcceptable: multiStyles/no-text bypass, empty-entries rejection,
 *                       cubeType vs bindingModel measure-detection paths, Y2-unsupported short-
 *                       circuit, X/Y/GEO dispatch, default-true fallthrough
 *   Group 2 [Risk 2] — drawDropRegion: hit-detection for all 5 named drop zones + legend overlay
 *                       + Y2-unsupported null-box
 *   Group 3 [Risk 2] — isY2Supported: 15-chart-type exclusion list (parameterized)
 *   Group 4 [Risk 2] — chartContainerWidth/Height/Top/Left: isVSChart padding branch
 *   Group 8 [Risk 2] — chartContainerVisible: multi-condition OR gate
 *   Groups 5/6/7/9/10/11/12 [Risk 1] — single-purpose display getters/helpers
 *
 * Confirmed bugs (it.fails): none
 *
 * Out of scope this pass: selectRegion is already fully covered in
 * chart-area.component.interaction.tl.spec.ts Group 7 — not duplicated here despite appearing in
 * the prescan's Pass 3 method list (its risk profile — modifier-key dispatch, radar expansion,
 * map guard — is interaction-shaped, not a pure display computation).
 */

import { AssetEntry } from "../../../../../shared/data/asset-entry";
import { ChartTool } from "../model/chart-tool";
import { GraphTypes } from "../../common/graph-types";
import { Rectangle } from "../../common/data/rectangle";
import { VSChartModel } from "../../vsobjects/model/vs-chart-model";
import { createComponent, makeChartObject, makeLegend, makeModel } from "./chart-area.component.test-helpers";

afterEach(() => vi.restoreAllMocks());

function makeDropEvent(text: string | null) {
   return { dataTransfer: { getData: vi.fn(() => text ?? "") } } as any;
}

function makeEntry(overrides: Record<string, any> = {}): AssetEntry {
   return Object.assign({ path: "/Dimensions/Field1", properties: {} }, overrides) as AssetEntry;
}

// ---------------------------------------------------------------------------
// Group 1: isDropAcceptable [Risk 3]
// ---------------------------------------------------------------------------

describe("ChartArea — isDropAcceptable", () => {
   it("should always accept when the chart has multiStyles (per-measure chart types)", () => {
      const { comp } = createComponent({ model: makeModel({ multiStyles: true } as any) });
      expect((comp as any).isDropAcceptable(makeDropEvent(null))).toBe(true);
   });

   it("should accept when the drag payload carries no text data", () => {
      const { comp } = createComponent();
      expect((comp as any).isDropAcceptable(makeDropEvent(null))).toBe(true);
   });

   it("should reject when the parsed column list is empty", () => {
      const { comp } = createComponent();
      const event = makeDropEvent(JSON.stringify({ column: [] }));
      expect((comp as any).isDropAcceptable(event)).toBe(false);
   });

   it("should derive isBindMeasure from the entry's cubeType bit flag (measure bit set)", () => {
      const { comp } = createComponent();
      (comp as any).dropType = 2; // DROP_REGION_X
      const entry = makeEntry({ properties: { "cube.column.type": "1" } });
      const event = makeDropEvent(JSON.stringify({ column: [entry] }));

      // isXAcceptable(isBindMeasure=true, ...) = !true || supportsInvertedChart(chartType)
      const supportsInvertedSpy = vi.spyOn(GraphTypes, "supportsInvertedChart").mockReturnValue(false);
      expect((comp as any).isDropAcceptable(event)).toBe(false);
      supportsInvertedSpy.mockReturnValue(true);
      expect((comp as any).isDropAcceptable(event)).toBe(true);
   });

   it("should derive isBindMeasure=false from a cubeType with the measure bit clear", () => {
      const { comp } = createComponent();
      (comp as any).dropType = 2; // DROP_REGION_X
      const entry = makeEntry({ properties: { "cube.column.type": "0" } });
      const event = makeDropEvent(JSON.stringify({ column: [entry] }));

      // isXAcceptable(isBindMeasure=false, ...) = !false || ... = true unconditionally
      expect((comp as any).isDropAcceptable(event)).toBe(true);
   });

   it("should derive isBindMeasure from the chart's own bindingModel fields when no cubeType is present", () => {
      const bindingModel: any = {
         xfields: [], yfields: [{ name: "Field1", measure: true }],
         colorField: null, shapeField: null, sizeField: null, textField: null,
         openField: null, closeField: null, lowField: null, highField: null,
      };
      const { comp } = createComponent();
      comp.bindingModel = bindingModel;
      (comp as any).dropType = 3; // DROP_REGION_Y
      const entry = makeEntry({ path: "/Measures/Field1", properties: {} });
      const event = makeDropEvent(JSON.stringify({ column: [entry] }));

      // isYAcceptable(isBindMeasure=true) depends on candle/stock/treemap exclusions; default
      // chart type (CHART_BAR) is none of those, so it stays acceptable.
      expect((comp as any).isDropAcceptable(event)).toBe(true);
   });

   it("should reject a Y2 drop when the current chart type does not support a secondary Y axis", () => {
      const { comp } = createComponent({ model: makeModel({ chartType: GraphTypes.CHART_STOCK } as any) });
      (comp as any).dropType = 5; // DROP_REGION_Y2
      const entry = makeEntry({ properties: { "cube.column.type": "0" } });
      const event = makeDropEvent(JSON.stringify({ column: [entry] }));

      expect((comp as any).isDropAcceptable(event)).toBe(false);
   });

   it("should dispatch to isGeoAcceptable for a GEO drop", () => {
      const { comp } = createComponent();
      (comp as any).dropType = 16; // DROP_REGION_GEO
      const entry = makeEntry({ properties: { "cube.column.type": "0", mappingStatus: "OK" } });
      const event = makeDropEvent(JSON.stringify({ column: [entry] }));

      expect((comp as any).isDropAcceptable(event)).toBe(true);
   });

   it("should accept drop types with no specific acceptance rule (e.g. the plot area)", () => {
      const { comp } = createComponent();
      (comp as any).dropType = 1; // DROP_REGION_PLOT
      const entry = makeEntry({ properties: { "cube.column.type": "0" } });
      const event = makeDropEvent(JSON.stringify({ column: [entry] }));

      expect((comp as any).isDropAcceptable(event)).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 2: drawDropRegion [Risk 2]
// ---------------------------------------------------------------------------

describe("ChartArea — drawDropRegion (hit-detection)", () => {
   function setupRegions(comp: any) {
      comp.dropRegionCanvas = { nativeElement: document.createElement("canvas") } as any;
      comp.plotRegion = new Rectangle(50, 50, 100, 100);
      (comp as any).xTopRegion = new Rectangle(50, 0, 100, 20);
      (comp as any).xBottomRegion = new Rectangle(50, 150, 100, 20);
      (comp as any).yLeftRegion = new Rectangle(0, 50, 20, 100);
      (comp as any).yRightRegion = new Rectangle(150, 50, 20, 100);
      vi.spyOn(comp, "isDrawAxisBorder").mockReturnValue(true);
   }

   it("should set dropType=X2 and draw a rectangle when hitting the top axis region", () => {
      const { comp, chartService } = createComponent();
      setupRegions(comp);
      (comp as any).drawDropRegion(60, 10);
      expect((comp as any).dropType).toBe(4); // DROP_REGION_X2
      expect(chartService.drawRectangle).toHaveBeenCalled();
   });

   it("should set dropType=X and draw a rectangle when hitting the bottom axis region", () => {
      const { comp, chartService } = createComponent();
      setupRegions(comp);
      (comp as any).drawDropRegion(60, 155);
      expect((comp as any).dropType).toBe(2); // DROP_REGION_X
      expect(chartService.drawRectangle).toHaveBeenCalled();
   });

   it("should set dropType=Y and draw a rectangle when hitting the left axis region", () => {
      const { comp, chartService } = createComponent();
      setupRegions(comp);
      (comp as any).drawDropRegion(5, 60);
      expect((comp as any).dropType).toBe(3); // DROP_REGION_Y
      expect(chartService.drawRectangle).toHaveBeenCalled();
   });

   it("should set dropType=Y2 and draw a rectangle when hitting the right axis region (Y2 supported)", () => {
      const { comp, chartService } = createComponent({ model: makeModel({ chartType: GraphTypes.CHART_BAR } as any) });
      setupRegions(comp);
      (comp as any).drawDropRegion(155, 60);
      expect((comp as any).dropType).toBe(5); // DROP_REGION_Y2
      expect(chartService.drawRectangle).toHaveBeenCalled();
   });

   it("should NOT draw a rectangle when hitting the right axis region on a chart that doesn't support Y2", () => {
      const { comp, chartService } = createComponent({ model: makeModel({ chartType: GraphTypes.CHART_STOCK } as any) });
      setupRegions(comp);
      (comp as any).drawDropRegion(155, 60);
      expect(chartService.drawRectangle).not.toHaveBeenCalled();
   });

   it("should set dropType=PLOT and draw a rectangle when hitting the plot interior", () => {
      const { comp, chartService } = createComponent();
      setupRegions(comp);
      (comp as any).drawDropRegion(100, 100);
      expect((comp as any).dropType).toBe(1); // DROP_REGION_PLOT
      expect(chartService.drawRectangle).toHaveBeenCalled();
   });

   it("should target the legend's own aesthetic type when the drop point is inside a legend's bounds", () => {
      const { comp, chartService } = createComponent();
      setupRegions(comp);
      const legend = makeLegend({ bounds: new Rectangle(300, 300, 40, 40), aestheticType: "shape" });
      comp.model.legends = [legend];
      vi.spyOn(ChartTool, "getAestheticType").mockReturnValue(7); // DROP_REGION_SHAPE

      (comp as any).drawDropRegion(310, 310);

      expect((comp as any).dropType).toBe(7);
      expect(chartService.drawRectangle).toHaveBeenCalled();
   });

   it("should not draw anything when the point is outside every named region and every legend", () => {
      const { comp, chartService } = createComponent();
      setupRegions(comp);
      comp.plotRegion = new Rectangle(50, 50, 100, 100);

      (comp as any).drawDropRegion(-100, -100);

      expect(chartService.drawRectangle).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 3: isY2Supported [Risk 2]
// ---------------------------------------------------------------------------

describe("ChartArea — isY2Supported", () => {
   it("should return true for a chart type with no Y2 restriction (e.g. bar)", () => {
      const { comp } = createComponent({ model: makeModel({ chartType: GraphTypes.CHART_BAR } as any) });
      expect(comp.isY2Supported()).toBe(true);
   });

   const exclusions: Array<[string, number]> = [
      ["3D bar", GraphTypes.CHART_3D_BAR],
      ["polar (radar)", GraphTypes.CHART_RADAR],
      ["waterfall", GraphTypes.CHART_WATERFALL],
      ["pareto", GraphTypes.CHART_PARETO],
      ["stock", GraphTypes.CHART_STOCK],
      ["map", GraphTypes.CHART_MAP],
      ["candle", GraphTypes.CHART_CANDLE],
      ["treemap", GraphTypes.CHART_TREEMAP],
      ["mekko", GraphTypes.CHART_MEKKO],
      ["boxplot", GraphTypes.CHART_BOXPLOT],
      ["tree", GraphTypes.CHART_TREE],
      ["network", GraphTypes.CHART_NETWORK],
      ["circular network", GraphTypes.CHART_CIRCULAR],
      ["gantt", GraphTypes.CHART_GANTT],
      ["funnel", GraphTypes.CHART_FUNNEL],
   ];

   for(const [label, chartType] of exclusions) {
      it(`should return false for ${label} charts`, () => {
         const { comp } = createComponent({ model: makeModel({ chartType } as any) });
         expect(comp.isY2Supported()).toBe(false);
      });
   }
});

// ---------------------------------------------------------------------------
// Group 4: chartContainerWidth/Height/Top/Left [Risk 2]
// ---------------------------------------------------------------------------

describe("ChartArea — chartContainer dimensions", () => {
   function withFormat(comp: any, overrides: Record<string, any> = {}) {
      comp.format = Object.assign({ width: 400, height: 300, border: { left: "0px", right: "0px", top: "0px", bottom: "0px" } }, overrides);
      comp.titleFormat = { height: 20 } as any;
      comp.titleVisible = false;
   }

   // Asymmetry in the source: chartContainerWidth/Height have NO `else` branch, so border is
   // never subtracted from them when isVSChart is false (only the `if(isVSChart)` branch computes
   // border-based padding for width/height). chartContainerTop/Left DO have an `else` branch that
   // subtracts border width even when isVSChart is false. This test locks in that asymmetry.
   it("should ignore border width for width/height but still account for it in top/left when isVSChart is false", () => {
      const { comp } = createComponent();
      withFormat(comp, { border: { left: "2px", right: "3px", top: "1px", bottom: "4px" } });
      comp.isVSChart = false;

      expect(comp.chartContainerWidth).toBe(400);
      expect(comp.chartContainerHeight).toBe(300);
      expect(comp.chartContainerTop).toBe(1);
      expect(comp.chartContainerLeft).toBe(2);
   });

   it("should ALSO subtract the VSChartModel's own padding fields when isVSChart is true", () => {
      const { comp } = createComponent({
         model: makeModel({
            paddingLeft: 5, paddingRight: 6, paddingTop: 7, paddingBottom: 8,
            objectFormat: { border: { left: "2px", right: "3px", top: "1px", bottom: "4px" } },
         } as any),
      });
      withFormat(comp, { border: { left: "0px", right: "0px", top: "0px", bottom: "0px" } });
      comp.isVSChart = true;

      expect(comp.chartContainerWidth).toBe(400 - (5 + 2) - (6 + 3));
      expect(comp.chartContainerHeight).toBe(300 - (7 + 1) - (8 + 4));
      expect(comp.chartContainerTop).toBe(7 + 1);
      expect(comp.chartContainerLeft).toBe(5 + 2);
   });

   it("should subtract the title bar height from chartContainerHeight and add it to chartContainerTop when titleVisible", () => {
      const { comp } = createComponent();
      withFormat(comp);
      comp.isVSChart = false;
      comp.titleVisible = true;

      expect(comp.chartContainerHeight).toBe(300 - 20);
      expect(comp.chartContainerTop).toBe(20);
   });
});

describe("ChartArea — getBorderWidth", () => {
   it("should parse a pixel border string to its numeric width", () => {
      const { comp } = createComponent();
      expect((comp as any).getBorderWidth("3px solid #000")).toBe(3);
   });

   it("should return 0 for an empty/falsy border", () => {
      const { comp } = createComponent();
      expect((comp as any).getBorderWidth("")).toBe(0);
      expect((comp as any).getBorderWidth(null)).toBe(0);
   });

   it("should return 0 for a non-positive parsed width", () => {
      const { comp } = createComponent();
      expect((comp as any).getBorderWidth("0px")).toBe(0);
   });
});

// ---------------------------------------------------------------------------
// Group 5: paintNoDataChart / noData [Risk 1]
// ---------------------------------------------------------------------------

describe("ChartArea — paintNoDataChart", () => {
   it("should return false when in the binding editor, regardless of other flags", () => {
      const { comp } = createComponent();
      comp.isBinding = true;
      comp.previewMode = true;
      comp.noChartData = true;
      expect(comp.paintNoDataChart()).toBe(false);
   });

   it("should reflect noChartData when in preview or viewer mode outside of binding", () => {
      const { comp } = createComponent();
      comp.isBinding = false;
      comp.previewMode = true;
      comp.noChartData = true;
      expect(comp.paintNoDataChart()).toBe(true);

      comp.noChartData = false;
      expect(comp.paintNoDataChart()).toBe(false);
   });

   it("should return false outside of preview/viewer mode", () => {
      const { comp } = createComponent();
      comp.isBinding = false;
      comp.previewMode = false;
      comp.viewerMode = false;
      comp.noChartData = true;
      expect(comp.paintNoDataChart()).toBe(false);
   });
});

describe("ChartArea — noData", () => {
   it("should be true when the model has no axes and the chart is not marked invalid or showEmptyArea", () => {
      const { comp } = createComponent({ model: makeModel({ axes: [], invalid: false } as any) });
      comp.showEmptyArea = false;
      expect(comp.noData).toBe(true);
   });

   it("should be false when showEmptyArea is true, even with no axes", () => {
      const { comp } = createComponent({ model: makeModel({ axes: [] } as any) });
      comp.showEmptyArea = true;
      expect(comp.noData).toBe(false);
   });

   it("should be false when the model is marked invalid", () => {
      const { comp } = createComponent({ model: makeModel({ axes: [], invalid: true } as any) });
      comp.showEmptyArea = false;
      expect(comp.noData).toBe(false);
   });

   it("should be false when the model has at least one axis", () => {
      const { comp } = createComponent({ model: makeModel({ axes: [makeChartObject("bottom_x_axis")] } as any) });
      comp.showEmptyArea = false;
      expect(comp.noData).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 6: getPlotErrorStyle [Risk 1]
// ---------------------------------------------------------------------------

describe("ChartArea — getPlotErrorStyle", () => {
   it("should return null when the model has no errorFormat", () => {
      const { comp } = createComponent({ model: makeModel({ errorFormat: null } as any) });
      expect(comp.getPlotErrorStyle()).toBeNull();
   });

   it("should build a style object from the model's errorFormat", () => {
      const { comp } = createComponent({
         model: makeModel({
            errorFormat: {
               color: "#ff0000", backgroundColor: "#000000",
               align: { valign: "middle", halign: "center" },
               font: { fontFamily: "Arial", fontStyle: "italic", fontSize: "12", fontWeight: "bold" },
            },
         } as any),
      });

      const style = comp.getPlotErrorStyle();

      expect(style).toEqual(expect.objectContaining({
         color: "#ff0000",
         "background-color": "#000000",
         "font-family": "Arial",
         "font-style": "italic",
         "font-size": "12px",
         "font-weight": "bold",
      }));
   });
});

// ---------------------------------------------------------------------------
// Group 7: getLegendContentHeight [Risk 1]
// ---------------------------------------------------------------------------

describe("ChartArea — getLegendContentHeight", () => {
   it("should compute the legend's usable content height from its bounds minus the legend object's layout offset", () => {
      const { comp } = createComponent();
      const legend = makeLegend({ bounds: new Rectangle(0, 0, 100, 200) });
      const legendObject = makeChartObject("legend_content", { layoutBounds: new Rectangle(0, 30, 100, 170) });
      expect(comp.getLegendContentHeight(legend, legendObject as any)).toBe(170); // 200 - 30
   });
});

// ---------------------------------------------------------------------------
// Group 8: chartContainerVisible [Risk 2]
// ---------------------------------------------------------------------------

describe("ChartArea — chartContainerVisible", () => {
   function baseModel(overrides: Partial<VSChartModel> = {}) {
      return makeModel({ axes: [makeChartObject("bottom_x_axis")], invalid: false, empty: false, ...overrides } as any);
   }

   it("should be false for a normal, valid chart with data and no error", () => {
      const { comp } = createComponent({ model: baseModel() });
      comp.emptyChart = false;
      (comp as any).imageError = false;
      expect(comp.chartContainerVisible).toBe(false);
   });

   it("should be true when the model reports noData", () => {
      const { comp } = createComponent({ model: makeModel({ axes: [] } as any) });
      expect(comp.chartContainerVisible).toBeTruthy();
   });

   it("should be true when the model is marked invalid", () => {
      const { comp } = createComponent({ model: baseModel({ invalid: true }) });
      expect(comp.chartContainerVisible).toBeTruthy();
   });

   it("should be true when emptyChart is set", () => {
      const { comp } = createComponent({ model: baseModel() });
      comp.emptyChart = true;
      expect(comp.chartContainerVisible).toBeTruthy();
   });

   it("should be true when the VSChartModel itself is empty (no bound table)", () => {
      const { comp } = createComponent({ model: baseModel({ empty: true }) });
      expect(comp.chartContainerVisible).toBeTruthy();
   });

   it("should be true when there is an image load error and the model is not invalid", () => {
      const { comp } = createComponent({ model: baseModel() });
      (comp as any).imageError = true;
      expect(comp.chartContainerVisible).toBeTruthy();
   });

   it("should NOT be forced true by imageError alone when the model IS invalid (invalid already covers it)", () => {
      const { comp } = createComponent({ model: baseModel({ invalid: true }) });
      (comp as any).imageError = true;
      // Still true overall (via the invalid branch) — this asserts the OR doesn't throw/short-circuit oddly.
      expect(comp.chartContainerVisible).toBeTruthy();
   });
});

// ---------------------------------------------------------------------------
// Group 9: *ScrollEnabled getters [Risk 1]
// ---------------------------------------------------------------------------

describe("ChartArea — scroll-enabled getters", () => {
   it("should enable down/right scrolling when there is more content beyond the visible plot region", () => {
      const { comp } = createComponent();
      comp.plotRegion = new Rectangle(0, 0, 100, 100);
      comp.model.plot.bounds = new Rectangle(0, 0, 200, 200);
      comp.scrollTop = 0;
      comp.scrollLeft = 0;
      expect(comp.downScrollEnabled).toBe(true);
      expect(comp.rightScrollEnabled).toBe(true);
   });

   it("should disable down/right scrolling once scrolled to the content edge", () => {
      const { comp } = createComponent();
      comp.plotRegion = new Rectangle(0, 0, 100, 100);
      comp.model.plot.bounds = new Rectangle(0, 0, 100, 100);
      comp.scrollTop = 0;
      comp.scrollLeft = 0;
      expect(comp.downScrollEnabled).toBe(false);
      expect(comp.rightScrollEnabled).toBe(false);
   });

   it("should enable up/left scrolling only when scrolled away from the origin", () => {
      const { comp } = createComponent();
      comp.scrollTop = 0;
      comp.scrollLeft = 0;
      expect(comp.upScrollEnabled).toBe(false);
      expect(comp.leftScrollEnabled).toBe(false);

      comp.scrollTop = 5;
      comp.scrollLeft = 5;
      expect(comp.upScrollEnabled).toBe(true);
      expect(comp.leftScrollEnabled).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 10: isNavMap [Risk 1]
// ---------------------------------------------------------------------------

describe("ChartArea — isNavMap", () => {
   it("should be true for a navigation-enabled geo chart with no facets", () => {
      const { comp } = createComponent({
         model: makeModel({ chartType: GraphTypes.CHART_MAP, navEnabled: true, facets: [] } as any),
      });
      expect(comp.isNavMap()).toBe(true);
   });

   it("should be false when navEnabled is false", () => {
      const { comp } = createComponent({
         model: makeModel({ chartType: GraphTypes.CHART_MAP, navEnabled: false, facets: [] } as any),
      });
      expect(comp.isNavMap()).toBe(false);
   });

   it("should be false when the chart has facets", () => {
      const { comp } = createComponent({
         model: makeModel({ chartType: GraphTypes.CHART_MAP, navEnabled: true, facets: [makeChartObject("facetTL")] } as any),
      });
      expect(comp.isNavMap()).toBe(false);
   });

   it("should be false for a non-geo chart type", () => {
      const { comp } = createComponent({
         model: makeModel({ chartType: GraphTypes.CHART_BAR, navEnabled: true, facets: [] } as any),
      });
      expect(comp.isNavMap()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 11: isDrawAxisBorder [Risk 2]
// ---------------------------------------------------------------------------

describe("ChartArea — isDrawAxisBorder", () => {
   it("should return true when there is no active column drag", () => {
      const { comp, dndService } = createComponent();
      dndService.getTransfer.mockReturnValue({ column: null });
      expect(comp.isDrawAxisBorder()).toBe(true);
   });

   it("should return false when a dragged dimension column matches an existing axis field name", () => {
      const { comp, dndService } = createComponent({
         model: makeModel({ axes: [makeChartObject("bottom_x_axis", { axisFields: ["Field1"] } as any)] } as any),
      });
      // The parent-group check compares against the literal i18n key string "_#(js:Dimensions)",
      // not the translated word "Dimensions" — the path segment must match that key exactly.
      dndService.getTransfer.mockReturnValue({ column: [{ path: "/_#(js:Dimensions)/Field1" }] });
      expect(comp.isDrawAxisBorder()).toBe(false);
   });

   it("should return true when the dragged column's parent group is not '_#(js:Dimensions)'", () => {
      const { comp, dndService } = createComponent({
         model: makeModel({ axes: [makeChartObject("bottom_x_axis", { axisFields: ["Field1"] } as any)] } as any),
      });
      dndService.getTransfer.mockReturnValue({ column: [{ path: "/Measures/Field1" }] });
      expect(comp.isDrawAxisBorder()).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 12: isXAcceptable / isYAcceptable / isGeoAcceptable [Risk 2]
// ---------------------------------------------------------------------------

describe("ChartArea — isXAcceptable", () => {
   it("should accept a dimension (non-measure) drag unconditionally", () => {
      const { comp } = createComponent();
      expect((comp as any).isXAcceptable(false, true)).toBe(true);
   });

   it("should accept a measure drag only when the chart type supports an inverted axis", () => {
      const { comp } = createComponent();
      vi.spyOn(GraphTypes, "supportsInvertedChart").mockReturnValue(true);
      expect((comp as any).isXAcceptable(true, false)).toBe(true);
   });

   it("should reject a measure drag when the chart type does not support an inverted axis", () => {
      const { comp } = createComponent();
      vi.spyOn(GraphTypes, "supportsInvertedChart").mockReturnValue(false);
      expect((comp as any).isXAcceptable(true, false)).toBe(false);
   });
});

describe("ChartArea — isYAcceptable", () => {
   it("should accept a dimension (non-measure) drag unconditionally", () => {
      const { comp } = createComponent();
      expect((comp as any).isYAcceptable(false)).toBe(true);
   });

   it("should accept a measure drag on chart types with no Y-measure restriction", () => {
      const { comp } = createComponent({ model: makeModel({ chartType: GraphTypes.CHART_BAR } as any) });
      expect((comp as any).isYAcceptable(true)).toBe(true);
   });

   it("should reject a measure drag on candle/stock/treemap chart types", () => {
      const candle = createComponent({ model: makeModel({ chartType: GraphTypes.CHART_CANDLE } as any) });
      expect((candle.comp as any).isYAcceptable(true)).toBe(false);

      const stock = createComponent({ model: makeModel({ chartType: GraphTypes.CHART_STOCK } as any) });
      expect((stock.comp as any).isYAcceptable(true)).toBe(false);

      const treemap = createComponent({ model: makeModel({ chartType: GraphTypes.CHART_TREEMAP } as any) });
      expect((treemap.comp as any).isYAcceptable(true)).toBe(false);
   });
});

describe("ChartArea — isGeoAcceptable", () => {
   it("should accept an entry whose properties define a mappingStatus", () => {
      const { comp } = createComponent();
      const entry = makeEntry({ properties: { mappingStatus: "OK" } });
      expect((comp as any).isGeoAcceptable(entry)).toBe(true);
   });

   it("should reject an entry with no mappingStatus property", () => {
      const { comp } = createComponent();
      const entry = makeEntry({ properties: {} });
      expect((comp as any).isGeoAcceptable(entry)).toBe(false);
   });

   it("should reject a null/undefined entry", () => {
      const { comp } = createComponent();
      expect((comp as any).isGeoAcceptable(null)).toBe(false);
   });
});
