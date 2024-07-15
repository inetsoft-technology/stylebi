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

import { ComponentTool } from "../../common/util/component-tool";
import { ChartTargetDialog } from "./chart-target-dialog.component";
import { TargetInfo } from "./target-info";

describe("ChartTargetDialog Unit Test", () => {
   let createModel: () => TargetInfo = () => {
      return {
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
      };
   };

   let chartTargetDialog: ChartTargetDialog;

   beforeEach(() => {
      chartTargetDialog = new ChartTargetDialog(
         <any> { open: jest.fn() }, <any> { isAdhoc: jest.fn() });
   });

   // Bug #9954 show error when trying to create statistics line with time field
   it("should show error", () => {
      chartTargetDialog.chartTarget = createModel();
      chartTargetDialog.chartTarget.tabFlag = 2;
      chartTargetDialog.chartTarget.measure.dateField = true;
      let showMessageDialog = jest.spyOn(ComponentTool, "showMessageDialog");
      showMessageDialog.mockImplementation(() => Promise.resolve("ok"));

      chartTargetDialog.saveChanges();

      expect(showMessageDialog).toHaveBeenCalled();
      expect(showMessageDialog.mock.calls[0][1]).toEqual("_#(js:Error)");
      expect(showMessageDialog.mock.calls[0][2]).toEqual("_#(js:designer.chartProp.statisticsDateField)");
   });
});
