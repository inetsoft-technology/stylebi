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
import { ChartTool } from "./chart-tool";

describe("ChartTool.regionPointToViewport", () => {
   const canvas = { left: 100, top: 50 };

   it("offsets by the canvas origin at scale 1", () => {
      expect(ChartTool.regionPointToViewport({ x: 30, y: 40 }, canvas, 0, 0, 1))
         .toEqual({ x: 130, y: 90 });
   });

   it("subtracts the scroll offset before scaling, matching drawRegions", () => {
      // drawRegions transforms by scale then translates by -offset, so the offset is in
      // region space and is removed before the scale is applied.
      expect(ChartTool.regionPointToViewport({ x: 30, y: 40 }, canvas, 10, 20, 2))
         .toEqual({ x: 140, y: 90 });
   });

   it("keeps a point at the offset origin on the canvas origin", () => {
      expect(ChartTool.regionPointToViewport({ x: 10, y: 20 }, canvas, 10, 20, 3))
         .toEqual({ x: 100, y: 50 });
   });
});
