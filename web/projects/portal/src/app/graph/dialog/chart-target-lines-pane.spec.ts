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
import { Tool } from "../../../../../shared/util/tool";
import { TargetInfo } from "../../widget/target/target-info";
import { ChartTargetLinesPaneModel } from "../model/dialog/chart-target-lines-pane-model";
import { ChartTargetLinesPane } from "./chart-target-lines-pane.component";

let createModel: () => ChartTargetLinesPaneModel = () => {
   return {
      mapInfo: false,
      supportsTarget: true,
      chartTargets: [],
      newTargetInfo: {
         measure: {
            name: "",
            label: "",
            dateField: false
         },
         fieldLabel: "",
         genericLabel: "",
         value: "",
         label: "",
         toValue: "",
         toLabel: "",
         labelFormats: "",
         lineStyle: 0,
         lineColor: {
            type: "",
            color: ""
         },
         fillAboveColor: {
            type: "",
            color: ""
         },
         fillBelowColor: {
            type: "",
            color: ""
         },
         alpha: "",
         fillBandColor: {
            type: "",
            color: ""
         },
         chartScope: false,
         index: 0,
         tabFlag: 0,
         changed: false,
         targetString: "",
         strategyInfo: {
            name: "",
            value: "",
            percentageAggregateVal: "",
            standardIsSample: false
         },
         bandFill: {
            name: "",
            field: "",
            summary: false,
            changed: false,
            clazz: "inetsoft.web.binding.model.graph.aesthetic.CategoricalColorModel",
            colors: [],
            cssColors: [],
            defaultColors: [],
            colorMaps: [],
            globalColorMaps: [],
            useGlobal: true,
            shareColors: true,
            dateFormat: 0
         },
         supportFill: false
      },
      availableFields: [],
      deletedIndexList: []
   };
};

let chartTargetLinesPane: ChartTargetLinesPane;
let ngbModal: any;

describe("ChartTargetLinesPane Unit Tests", () => {
   beforeEach(() => {
      ngbModal = { close: jest.fn(), dismiss: jest.fn() };
      chartTargetLinesPane = new ChartTargetLinesPane(ngbModal);
      chartTargetLinesPane.model = createModel();
      chartTargetLinesPane.variables = [];
   });

   // Bug #10187 Delete target line properly
   it("should delete chart target", () => {
      let targetInfo: TargetInfo = Tool.clone(chartTargetLinesPane.model.newTargetInfo);
      chartTargetLinesPane.model.chartTargets.push(targetInfo);
      chartTargetLinesPane.selectedIndexes = [0];

      chartTargetLinesPane.deleteTarget();
      expect(chartTargetLinesPane.model.chartTargets.length).toBe(0);
      expect(chartTargetLinesPane.selectedIndexes).toBeUndefined();
   });
});