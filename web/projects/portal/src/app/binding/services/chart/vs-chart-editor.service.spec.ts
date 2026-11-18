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
import { DataRefType } from "../../../common/data/data-ref-type";
import { DndService } from "../../../common/dnd/dnd.service";
import { GraphTypes } from "../../../common/graph-types";
import { ChartBindingModel } from "../../data/chart/chart-binding-model";
import { VSChartEditorService } from "./vs-chart-editor.service";

describe("VSChartEditorService.isDropPaneAccept candle color", () => {
   const service = new VSChartEditorService(<any> {}, <any> {}, <any> {}, <any> {});

   const dimension = { path: "Orders/_#(js:Dimensions)/Region", properties: {} };
   const measure = {
      path: "Orders/_#(js:Measures)/Total",
      properties: { refType: `${DataRefType.MEASURE}` }
   };

   function dnd(entry: any): DndService {
      return <any> { getTransfer: () => ({ column: [entry] }) };
   }

   function model(chartType: number, vizModern: boolean): ChartBindingModel {
      return <any> { chartType, vizModern };
   }

   it("refuses a dimension on a marked candle's color", () => {
      expect(service.isDropPaneAccept(
         dnd(dimension), model(GraphTypes.CHART_CANDLE, true), "color")).toBe(false);
   });

   it("accepts a measure on a marked candle's color", () => {
      expect(service.isDropPaneAccept(
         dnd(measure), model(GraphTypes.CHART_CANDLE, true), "color")).toBe(true);
   });

   it("accepts a dimension on a classic candle's color", () => {
      expect(service.isDropPaneAccept(
         dnd(dimension), model(GraphTypes.CHART_CANDLE, false), "color")).toBe(true);
   });

   it("accepts a dimension on a marked stock's color", () => {
      expect(service.isDropPaneAccept(
         dnd(dimension), model(GraphTypes.CHART_STOCK, true), "color")).toBe(true);
   });
});
