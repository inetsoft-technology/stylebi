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
import { VSChartModel } from "../../model/vs-chart-model";
import {
   horizontalPlotResizerFits,
   horizontalPlotResizerShown,
   plotResizerLength,
   verticalPlotResizerFits,
   verticalPlotResizerShown
} from "./plot-resizer-length";

function chartModel(plotWidth: number, plotHeight: number,
                    flags: Partial<VSChartModel> = {}): VSChartModel
{
   return <VSChartModel> <any> {
      plot: { layoutBounds: { x: 0, y: 0, width: plotWidth, height: plotHeight } },
      horizontallyResizable: true,
      verticallyResizable: true,
      showPlotResizers: true,
      ...flags
   };
}

describe("plotResizerLength", () => {
   it("caps at 150px on a wide plot", () => {
      expect(plotResizerLength(1000)).toBe(150);
      expect(plotResizerLength(250)).toBe(150);
   });

   it("takes 60% of the edge once 60% is under the cap", () => {
      expect(plotResizerLength(200)).toBe(120);
      expect(plotResizerLength(150)).toBe(90);
   });

   it("renders at the first edge that clears the floor", () => {
      // 80 / 0.6 = 133.33, so 134 is the first edge that still clears the floor
      expect(plotResizerLength(134)).toBeCloseTo(80.4, 1);
   });

   it("returns null below the floor, so the slider does not render", () => {
      expect(plotResizerLength(133)).toBeNull();
      expect(plotResizerLength(60)).toBeNull();
      expect(plotResizerLength(0)).toBeNull();
   });

   it("returns null for a missing or nonsensical edge", () => {
      expect(plotResizerLength(undefined)).toBeNull();
      expect(plotResizerLength(null)).toBeNull();
      expect(plotResizerLength(-10)).toBeNull();
   });
});

describe("plot resizer fits", () => {
   it("pairs each edge with its own resizable flag", () => {
      // wide but short, and only the vertical axis is resizable: nothing can be drawn
      const model = chartModel(400, 100, { horizontallyResizable: false });

      expect(horizontalPlotResizerFits(model)).toBe(false);
      expect(verticalPlotResizerFits(model)).toBe(false);
   });

   it("fits the axis whose edge clears the floor", () => {
      const narrowTall = chartModel(100, 400);

      expect(horizontalPlotResizerFits(narrowTall)).toBe(false);
      expect(verticalPlotResizerFits(narrowTall)).toBe(true);
   });

   it("does not fit an axis that is not resizable", () => {
      const model = chartModel(400, 400, { verticallyResizable: false });

      expect(horizontalPlotResizerFits(model)).toBe(true);
      expect(verticalPlotResizerFits(model)).toBe(false);
   });

   it("ignores showPlotResizers, since Resize Plot asks before setting it", () => {
      const model = chartModel(400, 400, { showPlotResizers: false });

      expect(horizontalPlotResizerFits(model)).toBe(true);
      expect(verticalPlotResizerFits(model)).toBe(true);
   });

   it("tolerates a model with no plot yet", () => {
      const model = <VSChartModel> <any> { horizontallyResizable: true, verticallyResizable: true };

      expect(horizontalPlotResizerFits(model)).toBe(false);
      expect(verticalPlotResizerFits(model)).toBe(false);
   });
});

describe("plot resizer shown", () => {
   it("draws nothing until Resize Plot turns the sliders on", () => {
      const model = chartModel(400, 400, { showPlotResizers: false });

      expect(horizontalPlotResizerShown(model)).toBe(false);
      expect(verticalPlotResizerShown(model)).toBe(false);
   });

   it("leaves the horizontal slider undrawn on a plot too narrow for it", () => {
      // the case that must not hide the mini toolbar: vertical draws, horizontal does not
      const narrowTall = chartModel(100, 400);

      expect(horizontalPlotResizerShown(narrowTall)).toBe(false);
      expect(verticalPlotResizerShown(narrowTall)).toBe(true);
   });

   it("draws both on a plot large enough for either", () => {
      const model = chartModel(400, 400);

      expect(horizontalPlotResizerShown(model)).toBe(true);
      expect(verticalPlotResizerShown(model)).toBe(true);
   });
});
