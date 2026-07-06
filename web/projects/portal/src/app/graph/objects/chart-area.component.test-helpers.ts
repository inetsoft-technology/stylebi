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

/**
 * Shared fixtures for chart-area.component.{interaction,risk,display}.tl.spec.ts.
 *
 * Direct instantiation (not ATL render()) — ChartArea has no base class and heavy DOM/canvas
 * dependencies, matching the established pattern for graph/vsobjects components:
 * constructor order (chartService, dndService, scaleService, changeDetectorRef,
 * pagingControlService, renderer, ngZone).
 */

import { ChangeDetectorRef, NgZone, Renderer2 } from "@angular/core";
import { Subject } from "rxjs";
import { ChartArea } from "./chart-area.component";
import { ChartService } from "../services/chart.service";
import { DndService } from "../../common/dnd/dnd.service";
import { ScaleService } from "../../widget/services/scale/scale-service";
import { PagingControlService } from "../../common/services/paging-control.service";
import { ChartModel } from "../model/chart-model";
import { ChartObject } from "../model/chart-object";
import { ChartAreaName } from "../model/chart-area-name";
import { ChartRegion } from "../model/chart-region";
import { Plot } from "../model/plot";
import { LegendContainer } from "../model/legend-container";
import { Rectangle } from "../../common/data/rectangle";
import { GraphTypes } from "../../common/graph-types";
import { TestUtils } from "../../common/test/test-utils";

export function makeChartObject(areaName: ChartAreaName, overrides: Partial<ChartObject> = {}): ChartObject {
   return Object.assign({
      areaName,
      layoutBounds: new Rectangle(0, 0, 100, 100),
      bounds: new Rectangle(0, 0, 100, 100),
      regions: [],
      tiles: [],
   }, overrides) as ChartObject;
}

export function makeRegion(overrides: Partial<ChartRegion> = {}): ChartRegion {
   return Object.assign({
      segTypes: [[8]], pts: [[[[0, 0], [10, 10]]]], tipIdx: 0, rowIdx: 0, valIdx: 0,
      hyperlinks: null, noselect: false, grouped: false, boundaryIdx: 0,
   }, overrides) as ChartRegion;
}

export function makePlot(overrides: Partial<Plot> = {}): Plot {
   return Object.assign(
      makeChartObject("plot_area"),
      { xboundaries: [], yboundaries: [], showReferenceLine: false },
      overrides
   ) as Plot;
}

export function makeLegend(overrides: Partial<LegendContainer> = {}): LegendContainer {
   return Object.assign({
      legendIndex: 0,
      bounds: new Rectangle(0, 0, 50, 50),
      border: "",
      field: "Field1",
      targetFields: ["Field1"],
      minSize: { width: 10, height: 10 },
      legendObjects: [],
      aestheticType: "color",
   }, overrides) as LegendContainer;
}

export function makeModel(overrides: Partial<ChartModel> = {}): ChartModel {
   const model = TestUtils.createMockVSChartModel("Chart1") as unknown as ChartModel;
   model.plot = makePlot();
   model.chartSelection = { chartObject: null, regions: null };
   model.chartType = GraphTypes.CHART_BAR;
   model.stringDictionary = ["Tooltip A", "Tooltip B"];
   return Object.assign(model, overrides);
}

export interface CreateComponentOpts {
   model?: ChartModel;
}

export function createComponent(opts: CreateComponentOpts = {}) {
   const scrollTopSubject = new Subject<number>();
   const scrollLeftSubject = new Subject<number>();
   const chartService = { clearCanvas: vi.fn(), drawRectangle: vi.fn() };
   const dndService = { getTransfer: vi.fn(() => ({ column: null })), processOnDrop: vi.fn() };
   const scaleService = { getCurrentScale: vi.fn(() => 1) };
   const changeDetectorRef = { detectChanges: vi.fn() };
   const pagingControlService = {
      scrollTop: vi.fn(() => scrollTopSubject.asObservable()),
      scrollLeft: vi.fn(() => scrollLeftSubject.asObservable()),
      getCurrentAssembly: vi.fn(() => "Chart1"),
      setPagingControlModel: vi.fn(),
   };
   const renderer = { setStyle: vi.fn() };
   const ngZone = { runOutsideAngular: (fn: Function) => fn(), run: (fn: Function) => fn() };

   const comp = new ChartArea(
      chartService as unknown as ChartService,
      dndService as unknown as DndService,
      scaleService as unknown as ScaleService,
      changeDetectorRef as unknown as ChangeDetectorRef,
      pagingControlService as unknown as PagingControlService,
      renderer as unknown as Renderer2,
      ngZone as unknown as NgZone,
   );
   comp.model = opts.model ?? makeModel();

   return {
      comp, chartService, dndService, scaleService, changeDetectorRef,
      pagingControlService, renderer, scrollTopSubject, scrollLeftSubject,
   };
}
