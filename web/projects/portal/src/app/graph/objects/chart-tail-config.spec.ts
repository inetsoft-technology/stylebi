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
import { GraphTypes } from "../../common/graph-types";
import { tailAxisForChartType } from "./chart-tail-config";

describe("tailAxisForChartType", () => {
   it("uses the horizontal axis for hierarchical, relational and step-line types", () => {
      const horizontal = [
         GraphTypes.CHART_TREE, GraphTypes.CHART_NETWORK, GraphTypes.CHART_CIRCULAR,
         GraphTypes.CHART_TREEMAP, GraphTypes.CHART_ICICLE, GraphTypes.CHART_CIRCLE_PACKING,
         GraphTypes.CHART_MEKKO, GraphTypes.CHART_STEP, GraphTypes.CHART_JUMP
      ];

      for(const type of horizontal) {
         expect(tailAxisForChartType(type)).toBe("horizontal");
      }
   });

   it("uses the vertical axis for everything else", () => {
      const vertical = [
         GraphTypes.CHART_BAR, GraphTypes.CHART_BAR_STACK, GraphTypes.CHART_PIE,
         GraphTypes.CHART_DONUT, GraphTypes.CHART_LINE, GraphTypes.CHART_AREA,
         GraphTypes.CHART_CANDLE, GraphTypes.CHART_BOXPLOT, GraphTypes.CHART_RADAR,
         GraphTypes.CHART_WATERFALL, GraphTypes.CHART_PARETO, GraphTypes.CHART_GANTT,
         GraphTypes.CHART_FUNNEL, GraphTypes.CHART_POINT, GraphTypes.CHART_AUTO
      ];

      for(const type of vertical) {
         expect(tailAxisForChartType(type)).toBe("vertical");
      }
   });

   it("keeps sunburst vertical even though icicle is horizontal", () => {
      expect(tailAxisForChartType(GraphTypes.CHART_SUNBURST)).toBe("vertical");
      expect(tailAxisForChartType(GraphTypes.CHART_ICICLE)).toBe("horizontal");
   });

   it("keeps the step-area family vertical even though step-line is horizontal", () => {
      expect(tailAxisForChartType(GraphTypes.CHART_STEP_AREA)).toBe("vertical");
      expect(tailAxisForChartType(GraphTypes.CHART_STEP_AREA_STACK)).toBe("vertical");
      expect(tailAxisForChartType(GraphTypes.CHART_STEP)).toBe("horizontal");
   });

   it("defaults to vertical for an unknown type", () => {
      expect(tailAxisForChartType(null)).toBe("vertical");
      expect(tailAxisForChartType(-1)).toBe("vertical");
   });
});
