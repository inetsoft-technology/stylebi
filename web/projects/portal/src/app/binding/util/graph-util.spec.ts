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
import { GraphTypes } from "../../common/graph-types";
import { StaticColorModel } from "../../common/data/visual-frame-model";
import { TestUtils } from "../../common/test/test-utils";
import { ChartAggregateRef } from "../data/chart/chart-aggregate-ref";
import { ChartBindingModel } from "../data/chart/chart-binding-model";
import { GraphUtil } from "./graph-util";

function color(value: string): StaticColorModel {
   return {
      clazz: "inetsoft.web.binding.model.graph.aesthetic.StaticColorModel",
      name: null,
      field: null,
      summary: false,
      changed: false,
      color: value,
      cssColor: "",
      defaultColor: value
   };
}

function measure(name: string): ChartAggregateRef {
   const aref = TestUtils.createMockChartAggregateRef(name);
   aref.view = name;
   aref.colorFrame = color("#0490ff");
   aref.summaryColorFrame = color("#767676");
   return aref;
}

function binding(chartType: number, direction: boolean): ChartBindingModel {
   const model = TestUtils.createMockChartBindingModel();
   model.chartType = chartType;
   model.rtchartType = chartType;
   model.yfields = [measure("Sum(v)")];
   model.colorFrame = color("#0490ff");

   if(direction) {
      model.upColorFrame = color("#1c857b");
      model.downColorFrame = color("#ad6119");
   }

   return model;
}

describe("GraphUtil.getVisualFrames direction frames", () => {
   it("getVisualFrames lists Up and Down for a candle with direction frames", () => {
      const model = binding(GraphTypes.CHART_CANDLE, true);

      const frames = GraphUtil.getVisualFrames("colorFrame", model);

      expect(frames.map(f => f.name)).toEqual(["Up", "Down"]);
      expect(frames[0].frame).toBe(model.upColorFrame);
      expect(frames[1].frame).toBe(model.downColorFrame);
   });

   it("getVisualFrames lists Increase, Decrease and Summary for a waterfall", () => {
      const model = binding(GraphTypes.CHART_WATERFALL, true);
      const aref = <ChartAggregateRef> model.yfields[0];

      const frames = GraphUtil.getVisualFrames("colorFrame", model);

      expect(frames.map(f => f.name)).toEqual(["Increase", "Decrease", "Summary"]);
      expect(frames[0].frame).toBe(model.upColorFrame);
      expect(frames[1].frame).toBe(model.downColorFrame);
      expect(frames[2].summary).toBe(true);
      expect(frames[2].frame).toBe(aref.summaryColorFrame);
   });

   it("getVisualFrames is unchanged without them", () => {
      const waterfall = binding(GraphTypes.CHART_WATERFALL, false);
      const aref = <ChartAggregateRef> waterfall.yfields[0];

      const frames = GraphUtil.getVisualFrames("colorFrame", waterfall);

      expect(frames.map(f => f.name)).toEqual(["Sum(v)", "Summary"]);
      expect(frames[0].frame).toBe(aref.colorFrame);
      expect(GraphUtil.getVisualFrames("colorFrame", binding(GraphTypes.CHART_BAR, false))
         .map(f => f.name)).toEqual(["Sum(v)"]);
   });

   it("getVisualFrames ignores the direction frames for other properties", () => {
      const model = binding(GraphTypes.CHART_CANDLE, true);

      expect(GraphUtil.getVisualFrames("sizeFrame", model)).toEqual([]);
   });
});
