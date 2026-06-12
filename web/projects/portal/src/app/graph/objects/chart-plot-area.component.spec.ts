/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
import { Component } from "@angular/core";
import { waitForAsync, TestBed } from "@angular/core/testing";
import { By } from "@angular/platform-browser";
import { HttpClient } from "@angular/common/http";
import { NEVER } from "rxjs";
import { Rectangle } from "../../common/data/rectangle";
import { ContextProvider } from "../../vsobjects/context-provider.service";
import { SelectionBoxDirective } from "../../widget/directive/selection-box.directive";
import { DomService } from "../../widget/dom-service/dom.service";
import { MouseEventDirective } from "../../widget/mouse-event/mouse-event.directive";
import { DebounceService } from "../../widget/services/debounce.service";
import { DefaultScaleService } from "../../widget/services/scale/default-scale-service";
import { ModelService } from "../../widget/services/model.service";
import { ScaleService } from "../../widget/services/scale/scale-service";
import { ChartObject } from "../model/chart-object";
import { Plot } from "../model/plot";
import { ChartTool } from "../model/chart-tool";
import { ChartService } from "../services/chart.service";
import { ChartConfigService } from "../services/chart-config.service";
import { ChartPlotArea } from "./chart-plot-area.component";
import { ChartInlineSvgDirective } from "./chart-inline-svg.directive";

@Component({
   selector: "test-app",
   template: `<chart-plot-area [chartObject]="mockObject" [viewerMode]="true"
                               (selectRegion)="selectRegion($event)">
              </chart-plot-area>
   `,
   standalone: true,
   imports: [ChartPlotArea]
})
class TestApp {
   public mockObject: Plot = {
      showReferenceLine: false,
      xboundaries: [],
      yboundaries: [],
      areaName: "plot_area",
      bounds: new Rectangle(32, 2, 367, 255),
      layoutBounds: new Rectangle(32, 2, 367, 255),
      tiles: [{
         bounds: new Rectangle(0, 0, 367, 255),
         row: 0,
         col: 0
      }],
      regions: [
         {
            segTypes: [[0, 1, 1, 1, 1]],
            pts: [[[
               [339, 187, 0, 0, 0, 0],
               [358, 187, 0, 0, 0, 0],
               [358, 253, 0, 0, 0, 0],
               [339, 253, 0, 0, 0, 0],
               [339, 187, 0, 0, 0, 0]
            ]]],
            centroid: null,
            index: 0,
            tipIdx: 1,
            metaIdx: 0,
            rowIdx: 10,
            valIdx: -1,
            hyperlinks: [],
            noselect: false,
            grouped: false,
            boundaryIdx: -1,
         },
         {
            segTypes: [[0, 1, 1, 1, 1]],
            pts: [[[
               [302, 184, 0, 0, 0, 0],
               [321, 184, 0, 0, 0, 0],
               [321, 253, 0, 0, 0, 0],
               [302, 253, 0, 0, 0, 0],
               [302, 184, 0, 0, 0, 0]
            ]]],
            centroid: null,
            index: 1,
            tipIdx: 2,
            metaIdx: 0,
            rowIdx: 8,
            valIdx: -1,
            hyperlinks: [],
            noselect: false,
            grouped: false,
            boundaryIdx: -1,
         },
         {
            segTypes: [[0, 1, 1, 1, 1]],
            pts: [[[
               [265, 177, 0, 0, 0, 0],
               [284, 177, 0, 0, 0, 0],
               [284, 253, 0, 0, 0, 0],
               [265, 253, 0, 0, 0, 0],
               [265, 177, 0, 0, 0, 0]
            ]]],
            centroid: null,
            index: 2,
            tipIdx: 3,
            metaIdx: 0,
            rowIdx: 7,
            valIdx: -1,
            hyperlinks: [],
            noselect: false,
            grouped: false,
            boundaryIdx: -1,
         },
         {
            segTypes: [[0, 1, 1, 1, 1]],
            pts: [[[
               [228, 8, 0, 0, 0, 0],
               [247, 8, 0, 0, 0, 0],
               [247, 75, 0, 0, 0, 0],
               [228, 75, 0, 0, 0, 0],
               [228, 8, 0, 0, 0, 0]
            ]]],
            centroid: null,
            index: 3,
            tipIdx: 4,
            metaIdx: 0,
            rowIdx: 1,
            valIdx: -1,
            hyperlinks: [],
            noselect: false,
            grouped: false,
            boundaryIdx: -1,
         },
         {
            segTypes: [[0, 1, 1, 1, 1]],
            pts: [[[
               [228, 75, 0, 0, 0, 0],
               [247, 75, 0, 0, 0, 0],
               [247, 253, 0, 0, 0, 0],
               [228, 253, 0, 0, 0, 0],
               [228, 75, 0, 0, 0, 0]
            ]]],
            centroid: null,
            index: 4,
            tipIdx: 5,
            metaIdx: 0,
            rowIdx: 0,
            valIdx: -1,
            hyperlinks: [],
            noselect: false,
            grouped: false,
            boundaryIdx: -1,
         },
         {
            segTypes: [[0, 1, 1, 1, 1]],
            pts: [[[
               [191, 126, 0, 0, 0, 0],
               [210, 126, 0, 0, 0, 0],
               [210, 253, 0, 0, 0, 0],
               [191, 253, 0, 0, 0, 0],
               [191, 126, 0, 0, 0, 0]
            ]]],
            centroid: null,
            index: 5,
            tipIdx: 6,
            metaIdx: 0,
            rowIdx: 2,
            valIdx: -1,
            hyperlinks: [],
            noselect: false,
            grouped: false,
            boundaryIdx: -1,
         },
         {
            segTypes: [[0, 1, 1, 1, 1]],
            pts:
               [[[
                  [155, 187, 0, 0, 0, 0],
                  [174, 187, 0, 0, 0, 0],
                  [174, 253, 0, 0, 0, 0],
                  [155, 253, 0, 0, 0, 0],
                  [155, 187, 0, 0, 0, 0]
               ]]],
            centroid: null,
            index: 6,
            tipIdx: 7,
            metaIdx: 0,
            rowIdx: 9,
            valIdx: -1,
            hyperlinks: [],
            noselect: false,
            grouped: false,
            boundaryIdx: -1,
         },
         {
            segTypes: [[0, 1, 1, 1, 1]],
            pts: [[[
               [118, 145, 0, 0, 0, 0],
               [137, 145, 0, 0, 0, 0],
               [137, 253, 0, 0, 0, 0],
               [118, 253, 0, 0, 0, 0],
               [118, 145, 0, 0, 0, 0]
            ]]],
            centroid: null,
            index: 7,
            tipIdx: 8,
            metaIdx: 0,
            rowIdx: 4,
            valIdx: -1,
            hyperlinks: [],
            noselect: false,
            grouped: false,
            boundaryIdx: -1,
         },
         {
            segTypes: [[0, 1, 1, 1, 1]],
            pts: [[[
               [81, 133, 0, 0, 0, 0],
               [100, 133, 0, 0, 0, 0],
               [100, 253, 0, 0, 0, 0],
               [81, 253, 0, 0, 0, 0],
               [81, 133, 0, 0, 0, 0]
            ]]],
            centroid: null,
            index: 8,
            tipIdx: 9,
            metaIdx: 0,
            rowIdx: 3,
            valIdx: -1,
            hyperlinks: [],
            noselect: false,
            grouped: false,
            boundaryIdx: -1,
         },
         {
            segTypes: [[0, 1, 1, 1, 1]],
            pts: [[[
               [44, 168, 0, 0, 0, 0],
               [63, 168, 0, 0, 0, 0],
               [63, 203, 0, 0, 0, 0],
               [44, 203, 0, 0, 0, 0],
               [44, 168, 0, 0, 0, 0]
            ]]],
            centroid: null,
            index: 9,
            tipIdx: 10,
            metaIdx: 0,
            rowIdx: 6,
            valIdx: -1,
            hyperlinks: [],
            noselect: false,
            grouped: false,
            boundaryIdx: -1,
         },
         {
            segTypes: [[0, 1, 1, 1, 1]],
            pts: [[[
               [44, 203, 0, 0, 0, 0],
               [63, 203, 0, 0, 0, 0],
               [63, 253, 0, 0, 0, 0],
               [44, 253, 0, 0, 0, 0],
               [44, 203, 0, 0, 0, 0]
            ]]],
            centroid: null,
            index: 10,
            tipIdx: 11,
            metaIdx: 0,
            rowIdx: 5,
            valIdx: -1,
            hyperlinks: [],
            noselect: false,
            grouped: false,
            boundaryIdx: -1,
         },
         {
            segTypes: [[0, 1, 1, 1, 1]],
            pts: [[[
               [7, 190, 0, 0, 0, 0],
               [26, 190, 0, 0, 0, 0],
               [26, 253, 0, 0, 0, 0],
               [7, 253, 0, 0, 0, 0],
               [7, 190, 0, 0, 0, 0]
            ]]],
            centroid: null,
            index: 11,
            tipIdx: 12,
            metaIdx: 0,
            rowIdx: 11,
            valIdx: -1,
            hyperlinks: [],
            noselect: false,
            grouped: false,
            boundaryIdx: -1,
         }]
   };

   selectRegion(event: any): void {
      // Spy Stub
   }
}

describe("ChartPlotArea Integration Tests", () => {
   let modelService: any;
   let httpService = { get: vi.fn(), post: vi.fn() };
   const contextProvider = {};
   // chart-image.directive subscribes to the chart image GET and calls
   // URL.createObjectURL(response.body). The previous BehaviorSubject<Subject>
   // emitted a Subject (with no `.body`) which threw an "obj argument must be an
   // instance of Blob" error after the test fixture was destroyed. Use NEVER so
   // the subscriber callback is never invoked.
   httpService.get.mockImplementation(() => NEVER);
   httpService.post.mockImplementation(() => NEVER);

   beforeEach(waitForAsync(() => {
      modelService = {};

      TestBed.configureTestingModule({
         imports: [
            TestApp,
            ChartPlotArea,
            MouseEventDirective,
            SelectionBoxDirective
         ],
         providers: [
            ChartService,
            DebounceService,
            DomService,
            {
               provide: ScaleService,
               useClass: DefaultScaleService
            },
            {
               provide: ModelService,
               useValue: modelService
            },
            {
               provide: HttpClient, useValue: httpService
            },
            {
               provide: ContextProvider, useValue: contextProvider
            },
         ]
      });

      TestBed.compileComponents();
   }));

   it("should have valid regions", () => new Promise<void>((done, fail) => {
      let fixture = TestBed.createComponent(TestApp);
      let testComponent = fixture.componentInstance;
      let chartObjectDebugElement = fixture.debugElement.query(By.css("chart-plot-area"));
      let chartObjectComponent: ChartPlotArea = chartObjectDebugElement.componentInstance;
      vi.spyOn(chartObjectComponent, "getSrc").mockImplementation((width, height) => "http://placehold.it/" + width + "x" + height);
      fixture.detectChanges();

      try {
         let testRegions = testComponent.mockObject.regions;
         let chartObjectRegions = chartObjectComponent.chartObject.regions;

         // Java path iterator returns 6 coordinates
         const numCoordinates = 6;
         let numSegments = -1;

         //
         testRegions.forEach((region) => {
            region.pts.forEach((rings) => {
               rings.forEach((segments) => {
                  numSegments = segments.length;

                  segments.forEach((points) => {
                     expect(points.length).toEqual(numCoordinates);
                  });
               });
            });

            region.segTypes.forEach((segmentType) => {
               expect(segmentType.length).toEqual(numSegments);
            });
         });

         expect(testRegions).toEqual(chartObjectRegions);
      }
      catch(e) {
         fail(e);
         return;
      }

      done();
   }));

   it("clears stale snap state when the Plot reference is replaced", () => {
      const fixture = TestBed.createComponent(TestApp);
      const debugEl = fixture.debugElement.query(By.css("chart-plot-area"));
      const component: ChartPlotArea = debugEl.componentInstance;
      vi.spyOn(component, "getSrc").mockImplementation(() => "");
      fixture.detectChanges();
      const oldPlot = component.chartObject;
      // Pretend a prior hover seeded the snap cache and a prior click left
      // a selection that still references the now-stale Plot.
      (component as any).snapXTicksFor = oldPlot;
      component.chartSelection = {
         chartObject: oldPlot,
         regions: [oldPlot.regions[0]]
      } as any;
      const clearSnapSpy = vi.spyOn(component as any, "clearSnapGuideline");
      const drawRegionsSpy = vi.spyOn(ChartTool, "drawRegions");
      // Swap in a new Plot reference (chart-type rebuild / data refresh).
      const newPlot: Plot = { ...oldPlot } as Plot;
      component.chartObject = newPlot;
      expect(clearSnapSpy).toHaveBeenCalled();
      expect((component as any).snapXTicksFor).toBeNull();
      // Stale-selection branch must not paint synchronously.
      expect(drawRegionsSpy).not.toHaveBeenCalled();
      drawRegionsSpy.mockRestore();
   });

   it("leaves snap state untouched when updateChartObject is called with no oldObj", () => {
      const fixture = TestBed.createComponent(TestApp);
      const debugEl = fixture.debugElement.query(By.css("chart-plot-area"));
      const component: ChartPlotArea = debugEl.componentInstance;
      vi.spyOn(component, "getSrc").mockImplementation(() => "");
      fixture.detectChanges();
      (component as any).snapXTicksFor = component.chartObject;
      const clearSnapSpy = vi.spyOn(component as any, "clearSnapGuideline");
      // Scroll-debounce path: updateChartObject() is called with no argument.
      component.updateChartObject();
      expect(clearSnapSpy).not.toHaveBeenCalled();
      expect((component as any).snapXTicksFor).toBe(component.chartObject);
   });

   it("snaps to the region nearest the cursor within an x-bucket", () => {
      const fixture = TestBed.createComponent(TestApp);
      const debugEl = fixture.debugElement.query(By.css("chart-plot-area"));
      const component: ChartPlotArea = debugEl.componentInstance;
      vi.spyOn(component, "getSrc").mockImplementation(() => "");
      fixture.detectChanges();

      // Pass both indices directly to exercise X-distance selection in isolation.
      // region 9 centerX ~53.5 (x 44-63), region 0 centerX ~348.5 (x 339-358).
      const left = (component as any).findPrimarySnapRegion([0, 9], 55, 200);
      expect(left.region.index).toBe(9);

      const right = (component as any).findPrimarySnapRegion([0, 9], 345, 200);
      expect(right.region.index).toBe(0);
   });

   it("skips wide area polygons when snapping to the nearest point", () => {
      const fixture = TestBed.createComponent(TestApp);
      const debugEl = fixture.debugElement.query(By.css("chart-plot-area"));
      const component: ChartPlotArea = debugEl.componentInstance;
      vi.spyOn(component, "getSrc").mockImplementation(() => "");
      fixture.detectChanges();

      const regions: any[] = component.chartObject.regions;
      const narrowIdx = regions.length;
      regions.push({ segTypes: [[8]], pts: [[[[100, 0], [10, 50]]]], centroid: null,
         index: narrowIdx, tipIdx: 20, rowIdx: 50, valIdx: -1, hyperlinks: [],
         noselect: false, grouped: false, boundaryIdx: -1 });
      const wideIdx = regions.length;
      regions.push({ segTypes: [[0, 1]], pts: [[[[0, 0], [400, 0]]]], centroid: null,
         index: wideIdx, tipIdx: 21, rowIdx: 50, valIdx: -1, hyperlinks: [],
         noselect: false, grouped: false, boundaryIdx: -1 });

      // Cursor at 200 is the wide polygon's center but should snap to the point.
      const primary = (component as any).findPrimarySnapRegion([narrowIdx, wideIdx], 200, 25);
      expect(primary.region.index).toBe(narrowIdx);
   });

   it("attaches the inline-svg directive when inlineSvg is enabled", () => {
      TestBed.inject(ChartConfigService).inlineSvg = true;
      const fixture = TestBed.createComponent(TestApp);
      const debugEl = fixture.debugElement.query(By.css("chart-plot-area"));
      const component: ChartPlotArea = debugEl.componentInstance;
      vi.spyOn(component, "getSrc").mockImplementation(() => "");
      fixture.detectChanges();

      // Directive must be registered in the component imports, else [chartInlineSvg]
      // binds to nothing and the plot never inlines (hover dimming breaks).
      expect(fixture.debugElement.query(By.directive(ChartInlineSvgDirective))).toBeTruthy();
      expect(fixture.debugElement.query(By.css(".chart-plot-area__tile img"))).toBeNull();
      expect(component.inlineSvgTiles.length).toBeGreaterThan(0);
   });

   it.skip("should be able to select regions", () => {
      let fixture = TestBed.createComponent(TestApp);
      let testComponent = fixture.componentInstance;
      let chartObjectDebugElement = fixture.debugElement.query(By.directive(ChartPlotArea));
      let chartObjectComponent: ChartPlotArea = chartObjectDebugElement.componentInstance;
      const selectRegionSpy = vi.spyOn(testComponent, "selectRegion");
      selectRegionSpy.mockImplementation(() => {});
      vi.spyOn(chartObjectComponent, "getSrc").mockImplementation((width, height) => "http://placehold.it/" + width + "x" + height);
      const selectRegionOutput = vi.spyOn(chartObjectComponent.selectRegion, "emit");
      fixture.detectChanges();

      // Mock mouseup event,
      const objLeft = chartObjectComponent.chartObject.layoutBounds.x;
      const objTop = chartObjectComponent.chartObject.layoutBounds.y;
      const objWidth = chartObjectComponent.chartObject.layoutBounds.width;
      const objHeight = chartObjectComponent.chartObject.layoutBounds.height;

      // Call event handler
      let plotArea = fixture.debugElement.query(By.css(".chart-plot-area"));
      plotArea.triggerEventHandler("onSelectionBox", {
         box: new Rectangle(0, 0, objLeft + objWidth, objTop + objHeight)
      });

      expect(selectRegionOutput).toHaveBeenCalled();
      const emitArgs: any = selectRegionOutput.mock.calls[0][0];
      let idxSort = (a: any, b: any) => a.index - b.index;

      // All regions are emitted (since we're selecting the entire region area)
      expect(emitArgs.regions).toBeTruthy();
      expect(emitArgs.regions.sort(idxSort)).toEqual(chartObjectComponent.chartObject.regions.sort(idxSort));

      // Chart select region should have been called
      expect(selectRegionSpy).toHaveBeenCalled();
   });

   describe("cross-tile series dim coordination", () => {
      function setup() {
         const fixture = TestBed.createComponent(TestApp);
         const component: ChartPlotArea =
            fixture.debugElement.query(By.css("chart-plot-area")).componentInstance;
         vi.spyOn(component, "getSrc").mockImplementation(() => "");
         fixture.detectChanges();
         const tileA = { setExternalSeriesDim: vi.fn() };
         const tileB = { setExternalSeriesDim: vi.fn() };
         // QueryList exposes forEach; an array stands in for the test.
         (component as any).inlineSvgTiles = [tileA, tileB];
         return { component, tileA, tileB };
      }

      it("applies the dim to every tile when a tile reports a color", () => {
         const { component, tileA, tileB } = setup();
         const t1 = {} as any;
         component.onSeriesDimChange("1,2,3", t1);
         expect(tileA.setExternalSeriesDim).toHaveBeenCalledWith("1,2,3");
         expect(tileB.setExternalSeriesDim).toHaveBeenCalledWith("1,2,3");
         expect((component as any).activeDimTile).toBe(t1);
      });

      // Crossing a tile boundary, the leaving tile's null and the entering tile's color can
      // arrive in either order. A null from a tile that is not the active source must not clear
      // the dim the active tile just set.
      it("ignores a null from a tile that is not the active source", () => {
         vi.useFakeTimers();
         try {
            const { component, tileA, tileB } = setup();
            const t1 = {} as any, t2 = {} as any;
            component.onSeriesDimChange("c", t1);
            tileA.setExternalSeriesDim.mockClear();
            tileB.setExternalSeriesDim.mockClear();
            component.onSeriesDimChange(null, t2);
            vi.advanceTimersByTime(500);
            expect(tileA.setExternalSeriesDim).not.toHaveBeenCalledWith(null);
            expect(tileB.setExternalSeriesDim).not.toHaveBeenCalledWith(null);
            expect((component as any).activeDimTile).toBe(t1);
         }
         finally {
            vi.useRealTimers();
         }
      });

      it("debounces a clear from the active tile and cancels it on re-hover", () => {
         vi.useFakeTimers();
         try {
            const { component, tileA } = setup();
            const t1 = {} as any;
            component.onSeriesDimChange("c", t1);
            component.onSeriesDimChange(null, t1);
            component.onSeriesDimChange("c", t1);
            tileA.setExternalSeriesDim.mockClear();
            vi.advanceTimersByTime(500);
            expect(tileA.setExternalSeriesDim).not.toHaveBeenCalledWith(null);
         }
         finally {
            vi.useRealTimers();
         }
      });

      it("clears every tile after the debounce when the active tile leaves", () => {
         vi.useFakeTimers();
         try {
            const { component, tileA, tileB } = setup();
            const t1 = {} as any;
            component.onSeriesDimChange("c", t1);
            tileA.setExternalSeriesDim.mockClear();
            tileB.setExternalSeriesDim.mockClear();
            component.onSeriesDimChange(null, t1);
            expect(tileA.setExternalSeriesDim).not.toHaveBeenCalled();
            vi.advanceTimersByTime(150);
            expect(tileA.setExternalSeriesDim).toHaveBeenCalledWith(null);
            expect(tileB.setExternalSeriesDim).toHaveBeenCalledWith(null);
            expect((component as any).activeDimTile).toBeNull();
         }
         finally {
            vi.useRealTimers();
         }
      });

      it("cancels a pending dim-clear timer on cleanup", () => {
         vi.useFakeTimers();
         try {
            const { component, tileA } = setup();
            const t1 = {} as any;
            component.onSeriesDimChange("c", t1);
            component.onSeriesDimChange(null, t1);
            (component as any).cleanup();
            tileA.setExternalSeriesDim.mockClear();
            vi.advanceTimersByTime(500);
            expect(tileA.setExternalSeriesDim).not.toHaveBeenCalledWith(null);
         }
         finally {
            vi.useRealTimers();
         }
      });
   });

   describe("cross-tile relation highlight coordination", () => {
      function setup(edgesA: any[] = [], edgesB: any[] = []) {
         const fixture = TestBed.createComponent(TestApp);
         const component: ChartPlotArea =
            fixture.debugElement.query(By.css("chart-plot-area")).componentInstance;
         vi.spyOn(component, "getSrc").mockImplementation(() => "");
         fixture.detectChanges();
         const tileA = { getRelationEdges: vi.fn(() => edgesA), setExternalRelationHighlight: vi.fn() };
         const tileB = { getRelationEdges: vi.fn(() => edgesB), setExternalRelationHighlight: vi.fn() };
         (component as any).inlineSvgTiles = [tileA, tileB];
         return { component, tileA, tileB };
      }

      // Connectivity is split across tiles: N->M lives in tile A, N->P in tile B. Hovering N must
      // resolve both neighbours by merging every tile's edges, then broadcast to all tiles.
      it("merges edges across tiles to resolve neighbours and broadcasts the active set", () => {
         const { component, tileA, tileB } = setup(
            [{ source: "N", target: "M" }], [{ source: "N", target: "P" }]);
         const t = {} as any;
         component.onRelationHover("N", t);
         const [ids, hoveredId] = tileA.setExternalRelationHighlight.mock.calls[0];
         expect(hoveredId).toBe("N");
         expect([...(ids as Set<string>)].sort()).toEqual(["M", "N", "P"]);
         expect(tileB.setExternalRelationHighlight).toHaveBeenCalledWith(ids, "N");
         expect((component as any).activeRelationTile).toBe(t);
      });

      it("ignores a null from a tile that is not the active source", () => {
         vi.useFakeTimers();
         try {
            const { component, tileA } = setup([{ source: "N", target: "M" }]);
            const t1 = {} as any, t2 = {} as any;
            component.onRelationHover("N", t1);
            tileA.setExternalRelationHighlight.mockClear();
            component.onRelationHover(null, t2);
            vi.advanceTimersByTime(500);
            expect(tileA.setExternalRelationHighlight).not.toHaveBeenCalledWith(null, null);
            expect((component as any).activeRelationTile).toBe(t1);
         }
         finally {
            vi.useRealTimers();
         }
      });

      it("clears every tile after the debounce when the active tile leaves", () => {
         vi.useFakeTimers();
         try {
            const { component, tileA, tileB } = setup([{ source: "N", target: "M" }]);
            const t1 = {} as any;
            component.onRelationHover("N", t1);
            tileA.setExternalRelationHighlight.mockClear();
            tileB.setExternalRelationHighlight.mockClear();
            component.onRelationHover(null, t1);
            expect(tileA.setExternalRelationHighlight).not.toHaveBeenCalled();
            vi.advanceTimersByTime(150);
            expect(tileA.setExternalRelationHighlight).toHaveBeenCalledWith(null, null);
            expect(tileB.setExternalRelationHighlight).toHaveBeenCalledWith(null, null);
            expect((component as any).activeRelationTile).toBeNull();
         }
         finally {
            vi.useRealTimers();
         }
      });
   });
});
