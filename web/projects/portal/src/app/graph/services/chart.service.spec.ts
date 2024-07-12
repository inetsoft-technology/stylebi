/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { async } from "@angular/core/testing";
import { ChartAggregateRef } from "../../binding/data/chart/chart-aggregate-ref";
import { FormatInfoModel } from "../../common/data/format-info-model";
import { Axis } from "../model/axis";
import { ChartModel } from "../model/chart-model";
import { LegendOption } from "../model/legend-option";
import { ChartService } from "./chart.service";
import { ChartTool } from "../model/chart-tool";

export class TestChartService extends ChartService {
   TestChartService() {
   }

   refreshTextFormatPane(model: ChartModel, aggr: ChartAggregateRef, client: any) {
   }
}

describe("Chart service test", () => {
   let createMockChartModel: () => ChartModel = () => {
      return {
         chartType: 0,
         multiStyles: false,
         maxMode: false,
         showValues: false,
         brushed: false,
         zoomed: false,
         hasFlyovers: false,
         flyOnClick: false,
         axes: [],
         facets: [],
         legends: [],
         plot: null,
         titles: [],
         stringDictionary: [],
         regionMetaDictionary: [{colIdx: 0}],
         genTime: null,
         axisHidden: false,
         titleHidden: false,
         legendHidden: false,
         legendOption: LegendOption.RIGHT,
         chartSelection: {
            chartObject: <Axis> {
               areaName: null,
               bounds: null,
               layoutBounds: null,
               tiles: null,
               regions: null,
               secondary: true
            },
            regions: [{
               segTypes: null,
               pts: null,
               centroid: null,
               index: -1,
               tipIdx: -1,
               metaIdx: -1,
               rowIdx: -1,
               valIdx: -1,
               hyperlinks: null,
               noselect: false,
               grouped: false,
               boundaryIdx: -1,
               vertical: false,
            }]
         },
         legendsBounds: null,
         contentBounds: null,
         enableAdhoc: false,
         clearCanvasSubject: null,
         wordCloud: false
      };
   };

   let chartService: TestChartService;

   beforeEach(async(() => {
      chartService = new TestChartService();
   }));

   // Bug #10792, #9838 find axis column name by the select measure label
   // or dimension label.
   it("should find the axis column name by index of the selected region", async(() => {
      let chartModel = createMockChartModel();
      chartModel.stringDictionary = ["AxisColumnName"];
      chartModel.regionMetaDictionary = [{meaIdx: 0}];
      chartModel.chartSelection.regions[0].metaIdx = 0;
      expect(ChartTool.getSelectedAxisColumnName(chartModel)).toBe("AxisColumnName");

      chartModel.regionMetaDictionary = [{dimIdx: 0}];
      expect(ChartTool.getSelectedAxisColumnName(chartModel)).toBe("AxisColumnName");

      chartModel.regionMetaDictionary = [{dimIdx: -1, axisFldIdx: 0}];
      expect(ChartTool.getSelectedAxisColumnName(chartModel)).toBe("AxisColumnName");
   }));
});
