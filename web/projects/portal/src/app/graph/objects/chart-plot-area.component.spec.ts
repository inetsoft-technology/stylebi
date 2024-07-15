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
import { async, TestBed } from "@angular/core/testing";
import { By } from "@angular/platform-browser";
import { HttpClient } from "@angular/common/http";
import { BehaviorSubject, Subject } from "rxjs";
import { Rectangle } from "../../common/data/rectangle";
import { SelectionBoxDirective } from "../../widget/directive/selection-box.directive";
import { DomService } from "../../widget/dom-service/dom.service";
import { MouseEventDirective } from "../../widget/mouse-event/mouse-event.directive";
import { DebounceService } from "../../widget/services/debounce.service";
import { DefaultScaleService } from "../../widget/services/scale/default-scale-service";
import { ModelService } from "../../widget/services/model.service";
import { ScaleService } from "../../widget/services/scale/scale-service";
import { ChartObject } from "../model/chart-object";
import { ChartService } from "../services/chart.service";
import { ChartPlotArea } from "./chart-plot-area.component";

@Component({
   selector: "test-app",
   template: `<chart-plot-area [chartObject]="mockObject" [viewerMode]="true"
                               (selectRegion)="selectRegion($event)">
              </chart-plot-area>
   `
})
class TestApp {
   public mockObject: ChartObject = {
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
   let httpService = { get: jest.fn(), post: jest.fn() };
   let responseObservable = new BehaviorSubject(new Subject());
   httpService.get.mockImplementation(() => responseObservable);
   httpService.post.mockImplementation(() => responseObservable);

   beforeEach(async(() => {
      modelService = {};

      TestBed.configureTestingModule({
         declarations: [
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
            }
         ]
      });

      TestBed.compileComponents();
   }));

   it("should have valid regions", (done) => {
      let fixture = TestBed.createComponent(TestApp);
      let testComponent = fixture.componentInstance;
      let chartObjectDebugElement = fixture.debugElement.query(By.css("chart-plot-area"));
      let chartObjectComponent: ChartPlotArea = chartObjectDebugElement.componentInstance;
      jest.spyOn(chartObjectComponent, "getSrc").mockImplementation((width, height) => "http://placehold.it/" + width + "x" + height);
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
         done.fail(e);
      }

      done();
   });

   xit("should be able to select regions", () => {
      let fixture = TestBed.createComponent(TestApp);
      let testComponent = fixture.componentInstance;
      let chartObjectDebugElement = fixture.debugElement.query(By.directive(ChartPlotArea));
      let chartObjectComponent: ChartPlotArea = chartObjectDebugElement.componentInstance;
      const selectRegionSpy = jest.spyOn(testComponent, "selectRegion");
      selectRegionSpy.mockImplementation(() => {});
      jest.spyOn(chartObjectComponent, "getSrc").mockImplementation((width, height) => "http://placehold.it/" + width + "x" + height);
      const selectRegionOutput = jest.spyOn(chartObjectComponent.selectRegion, "emit");
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
});
