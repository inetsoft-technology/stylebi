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

import { Rectangle } from "../../common/data/rectangle";
import { Dimension } from "../../common/data/dimension";
import { ChartAreaName } from "../model/chart-area-name";
import { Legend } from "../model/legend";
import { LegendContainer } from "../model/legend-container";
import { LegendOption } from "../model/legend-option";
import { ChartLegendContainer } from "./chart-legend-container.component";

export function makeRect(overrides: Partial<DOMRect> = {}): DOMRect {
   return {
      x: 0,
      y: 0,
      width: 200,
      height: 100,
      top: 0,
      left: 0,
      right: 200,
      bottom: 100,
      toJSON: () => ({}),
      ...overrides,
   } as DOMRect;
}

export function makeLegend(overrides: Partial<LegendContainer> = {}): LegendContainer {
   const legendObject: Partial<Legend> = { background: "#ccc" };

   return {
      legendIndex: 0,
      bounds: new Rectangle(20, 30, 40, 20),
      border: "1px solid #000",
      field: "Measure",
      targetFields: [],
      minSize: new Dimension(12, 8),
      legendObjects: [legendObject as Legend],
      aestheticType: "color",
      ...overrides,
   };
}

export function makeChartContainer(rect: DOMRect = makeRect()): Element {
   const element = document.createElement("div");
   vi.spyOn(element, "getBoundingClientRect").mockReturnValue(rect);
   return element;
}

export function createComponent(overrides: {
   plotRegion?: Rectangle;
   legend?: LegendContainer;
   legendOption?: LegendOption;
   chartRect?: DOMRect;
   contextProvider?: { viewer?: boolean; preview?: boolean };
   mouseoverLegendRegion?: ChartAreaName;
   resizeEnable?: boolean;
   moveEnable?: boolean;
} = {}) {
   const contextProvider = {
      viewer: false,
      preview: false,
      ...overrides.contextProvider,
   };
   const comp = new ChartLegendContainer(contextProvider as any);
   comp.plotRegion = overrides.plotRegion ?? new Rectangle(20, 10, 160, 80);
   comp.legend = overrides.legend ?? makeLegend();
   comp.legendOption = overrides.legendOption ?? LegendOption.TOP;
   comp.chartContainer = makeChartContainer(overrides.chartRect ?? makeRect());
   comp.mouseoverLegendRegion = overrides.mouseoverLegendRegion ?? null;
   comp.resizeEnable = overrides.resizeEnable ?? true;
   comp.moveEnable = overrides.moveEnable ?? true;
   return { comp, contextProvider };
}

export function makeLegendContainerElement(rect: DOMRect = makeRect({
   left: 20,
   top: 30,
   right: 60,
   bottom: 50,
   width: 40,
   height: 20,
})) {
   const element = document.createElement("div");
   vi.spyOn(element, "getBoundingClientRect").mockReturnValue(rect);
   return element;
}
