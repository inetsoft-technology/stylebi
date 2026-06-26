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
import { TestBed } from "@angular/core/testing";
import { BehaviorSubject } from "rxjs";
import { Rectangle } from "../../common/data/rectangle";
import { Legend } from "../model/legend";
import { ChartService } from "../services/chart.service";
import { ScaleService } from "../../widget/services/scale/scale-service";
import { ChartLegendArea } from "./chart-legend-area.component";

describe("ChartLegendArea canvas offsets", () => {
   let area: ChartLegendArea;

   beforeEach(() => {
      const chartService = new ChartService();
      const scaleService = <ScaleService> <unknown> { getScale: () => new BehaviorSubject(1) };
      area = TestBed.runInInjectionContext(
         () => new ChartLegendArea(chartService, scaleService));
   });

   function makeChartObject(areaName: string, scalar: boolean): Legend {
      return <Legend> <unknown> {
         areaName,
         scalar,
         bounds: new Rectangle(0, 0, 110, 38),
         layoutBounds: new Rectangle(0, 16, 110, 38),
         tiles: [],
         regions: [],
         titleLabel: "",
         titleVisible: true,
         aestheticType: "Color",
         targetFields: [],
      };
   }

   it("returns borderWidth for scalar legend_content", () => {
      area.chartObject = makeChartObject("legend_content", true);
      area.borderWidth = 3;
      expect((<any> area).canvasX).toBe(3);
      expect((<any> area).canvasY).toBe(3);
   });

   it("returns 0 for categorical legend_content", () => {
      area.chartObject = makeChartObject("legend_content", false);
      area.borderWidth = 3;
      expect((<any> area).canvasX).toBe(0);
      expect((<any> area).canvasY).toBe(0);
   });

   it("returns 0 for legend_title regardless of border width", () => {
      area.chartObject = makeChartObject("legend_title", false);
      area.borderWidth = 3;
      expect((<any> area).canvasX).toBe(0);
      expect((<any> area).canvasY).toBe(0);
   });

   it("returns 0 when borderWidth is 0 even for scalar", () => {
      area.chartObject = makeChartObject("legend_content", true);
      area.borderWidth = 0;
      expect((<any> area).canvasX).toBe(0);
      expect((<any> area).canvasY).toBe(0);
   });

   it("returns 0 when chartObject is null", () => {
      area.borderWidth = 3;
      expect((<any> area).canvasX).toBe(0);
      expect((<any> area).canvasY).toBe(0);
   });
});
