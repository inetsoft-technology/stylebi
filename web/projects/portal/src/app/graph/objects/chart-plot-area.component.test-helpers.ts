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
 * Shared fixtures for chart-plot-area.component.{interaction,risk,display}.tl.spec.ts.
 *
 * Direct instantiation via `new ChartPlotArea(...)`, matching the ChartAxisArea/ChartArea
 * convention. The base class `ChartObjectAreaBase`'s constructor calls Angular's `inject(NgZone)`
 * directly, which needs `TestBed.runInInjectionContext(...)` around the `new` call.
 */

import { ChangeDetectorRef, NgZone } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { TestBed } from "@angular/core/testing";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Subject } from "rxjs";
import { ChartPlotArea } from "./chart-plot-area.component";
import { ChartService } from "../services/chart.service";
import { DebounceService } from "../../widget/services/debounce.service";
import { ModelService } from "../../widget/services/model.service";
import { ContextProvider } from "../../vsobjects/context-provider.service";
import { ScaleService } from "../../widget/services/scale/scale-service";
import { ChartModel } from "../model/chart-model";
import { Plot } from "../model/plot";
import { ChartRegion } from "../model/chart-region";
import { ChartTile } from "../model/chart-tile";
import { Rectangle } from "../../common/data/rectangle";

export function makeTile(overrides: Partial<ChartTile> = {}): ChartTile {
   return Object.assign({
      row: 0,
      col: 0,
      bounds: new Rectangle(0, 0, 400, 300),
   }, overrides) as ChartTile;
}

export function makePlot(overrides: Partial<Plot> = {}): Plot {
   return Object.assign({
      areaName: "plot_area",
      bounds: new Rectangle(0, 0, 400, 300),
      layoutBounds: new Rectangle(0, 0, 400, 300),
      tiles: [makeTile()],
      regions: [],
      xboundaries: [],
      yboundaries: [],
      showReferenceLine: false,
   }, overrides) as Plot;
}

/**
 * Real (non-mocked) polygon region data, ported from the deleted
 * chart-plot-area.component.spec.ts fixture — used only by the real-geometry integration
 * test in chart-plot-area.component.interaction.tl.spec.ts, which exercises the actual
 * ChartTool.getTreeRegions/which-polygon region tree instead of mocking it. Every other
 * test in the suite mocks ChartTool.getTreeRegions and does not need this data.
 */
export function makeRealisticRegions(): ChartRegion[] {
   return [
      {
         segTypes: [[0, 1, 1, 1, 1]],
         pts: [[[
            [339, 187, 0, 0, 0, 0], [358, 187, 0, 0, 0, 0],
            [358, 253, 0, 0, 0, 0], [339, 253, 0, 0, 0, 0], [339, 187, 0, 0, 0, 0]
         ]]],
         centroid: null, index: 0, tipIdx: 1, metaIdx: 0, rowIdx: 10, valIdx: -1,
         hyperlinks: [], noselect: false, grouped: false, boundaryIdx: -1,
      },
      {
         segTypes: [[0, 1, 1, 1, 1]],
         pts: [[[
            [302, 184, 0, 0, 0, 0], [321, 184, 0, 0, 0, 0],
            [321, 253, 0, 0, 0, 0], [302, 253, 0, 0, 0, 0], [302, 184, 0, 0, 0, 0]
         ]]],
         centroid: null, index: 1, tipIdx: 2, metaIdx: 0, rowIdx: 8, valIdx: -1,
         hyperlinks: [], noselect: false, grouped: false, boundaryIdx: -1,
      },
      {
         segTypes: [[0, 1, 1, 1, 1]],
         pts: [[[
            [265, 177, 0, 0, 0, 0], [284, 177, 0, 0, 0, 0],
            [284, 253, 0, 0, 0, 0], [265, 253, 0, 0, 0, 0], [265, 177, 0, 0, 0, 0]
         ]]],
         centroid: null, index: 2, tipIdx: 3, metaIdx: 0, rowIdx: 7, valIdx: -1,
         hyperlinks: [], noselect: false, grouped: false, boundaryIdx: -1,
      },
      {
         segTypes: [[0, 1, 1, 1, 1]],
         pts: [[[
            [228, 8, 0, 0, 0, 0], [247, 8, 0, 0, 0, 0],
            [247, 75, 0, 0, 0, 0], [228, 75, 0, 0, 0, 0], [228, 8, 0, 0, 0, 0]
         ]]],
         centroid: null, index: 3, tipIdx: 4, metaIdx: 0, rowIdx: 1, valIdx: -1,
         hyperlinks: [], noselect: false, grouped: false, boundaryIdx: -1,
      },
      {
         segTypes: [[0, 1, 1, 1, 1]],
         pts: [[[
            [228, 75, 0, 0, 0, 0], [247, 75, 0, 0, 0, 0],
            [247, 253, 0, 0, 0, 0], [228, 253, 0, 0, 0, 0], [228, 75, 0, 0, 0, 0]
         ]]],
         centroid: null, index: 4, tipIdx: 5, metaIdx: 0, rowIdx: 0, valIdx: -1,
         hyperlinks: [], noselect: false, grouped: false, boundaryIdx: -1,
      },
      {
         segTypes: [[0, 1, 1, 1, 1]],
         pts: [[[
            [191, 126, 0, 0, 0, 0], [210, 126, 0, 0, 0, 0],
            [210, 253, 0, 0, 0, 0], [191, 253, 0, 0, 0, 0], [191, 126, 0, 0, 0, 0]
         ]]],
         centroid: null, index: 5, tipIdx: 6, metaIdx: 0, rowIdx: 2, valIdx: -1,
         hyperlinks: [], noselect: false, grouped: false, boundaryIdx: -1,
      },
      {
         segTypes: [[0, 1, 1, 1, 1]],
         pts: [[[
            [155, 187, 0, 0, 0, 0], [174, 187, 0, 0, 0, 0],
            [174, 253, 0, 0, 0, 0], [155, 253, 0, 0, 0, 0], [155, 187, 0, 0, 0, 0]
         ]]],
         centroid: null, index: 6, tipIdx: 7, metaIdx: 0, rowIdx: 9, valIdx: -1,
         hyperlinks: [], noselect: false, grouped: false, boundaryIdx: -1,
      },
      {
         segTypes: [[0, 1, 1, 1, 1]],
         pts: [[[
            [118, 145, 0, 0, 0, 0], [137, 145, 0, 0, 0, 0],
            [137, 253, 0, 0, 0, 0], [118, 253, 0, 0, 0, 0], [118, 145, 0, 0, 0, 0]
         ]]],
         centroid: null, index: 7, tipIdx: 8, metaIdx: 0, rowIdx: 4, valIdx: -1,
         hyperlinks: [], noselect: false, grouped: false, boundaryIdx: -1,
      },
      {
         segTypes: [[0, 1, 1, 1, 1]],
         pts: [[[
            [81, 133, 0, 0, 0, 0], [100, 133, 0, 0, 0, 0],
            [100, 253, 0, 0, 0, 0], [81, 253, 0, 0, 0, 0], [81, 133, 0, 0, 0, 0]
         ]]],
         centroid: null, index: 8, tipIdx: 9, metaIdx: 0, rowIdx: 3, valIdx: -1,
         hyperlinks: [], noselect: false, grouped: false, boundaryIdx: -1,
      },
      {
         segTypes: [[0, 1, 1, 1, 1]],
         pts: [[[
            [44, 168, 0, 0, 0, 0], [63, 168, 0, 0, 0, 0],
            [63, 203, 0, 0, 0, 0], [44, 203, 0, 0, 0, 0], [44, 168, 0, 0, 0, 0]
         ]]],
         centroid: null, index: 9, tipIdx: 10, metaIdx: 0, rowIdx: 6, valIdx: -1,
         hyperlinks: [], noselect: false, grouped: false, boundaryIdx: -1,
      },
      {
         segTypes: [[0, 1, 1, 1, 1]],
         pts: [[[
            [44, 203, 0, 0, 0, 0], [63, 203, 0, 0, 0, 0],
            [63, 253, 0, 0, 0, 0], [44, 253, 0, 0, 0, 0], [44, 203, 0, 0, 0, 0]
         ]]],
         centroid: null, index: 10, tipIdx: 11, metaIdx: 0, rowIdx: 5, valIdx: -1,
         hyperlinks: [], noselect: false, grouped: false, boundaryIdx: -1,
      },
      {
         segTypes: [[0, 1, 1, 1, 1]],
         pts: [[[
            [7, 190, 0, 0, 0, 0], [26, 190, 0, 0, 0, 0],
            [26, 253, 0, 0, 0, 0], [7, 253, 0, 0, 0, 0], [7, 190, 0, 0, 0, 0]
         ]]],
         centroid: null, index: 11, tipIdx: 12, metaIdx: 0, rowIdx: 11, valIdx: -1,
         hyperlinks: [], noselect: false, grouped: false, boundaryIdx: -1,
      },
   ] as unknown as ChartRegion[];
}

export function makeModel(overrides: Partial<ChartModel> = {}): ChartModel {
   return Object.assign({
      axes: [], legends: [], plot: null, titles: [], facets: [],
      stringDictionary: [], regionMetaDictionary: [],
      chartSelection: { chartObject: null, regions: null },
      chartType: 0, brushed: false, zoomed: false, multiStyles: false,
      showValues: false, genTime: 1, hasFlyovers: false, flyOnClick: false,
      dataTipOnClick: false, invalid: false, changedByScript: false, noData: false,
      contentBounds: new Rectangle(0, 0, 400, 300),
   }, overrides) as unknown as ChartModel;
}

export interface CreateComponentOpts {
   plot?: Plot;
   model?: ChartModel;
}

export function createComponent(opts: CreateComponentOpts = {}) {
   const chartService = { clearCanvas: vi.fn(), drawRectangle: vi.fn() };
   const changeRef = { detectChanges: vi.fn() };
   const zone = { run: vi.fn((fn: Function) => fn()) };
   const debounceService = {
      debounce: vi.fn((_key: string, fn: Function) => fn()),
      cancel: vi.fn(),
   };
   const modelService = {};
   const http = { get: vi.fn() };
   const modal = {} as NgbModal;
   const contextProvider = { embed: false, vsWizard: false, vsWizardPreview: false };
   const scaleSubject = new Subject<number>();
   const scaleService = {
      getScale: vi.fn(() => scaleSubject.asObservable()),
      getCurrentScale: vi.fn(() => 1),
   };

   const comp = TestBed.runInInjectionContext(() => new ChartPlotArea(
      chartService as unknown as ChartService,
      changeRef as unknown as ChangeDetectorRef,
      zone as unknown as NgZone,
      debounceService as unknown as DebounceService,
      modelService as unknown as ModelService,
      http as unknown as HttpClient,
      modal,
      scaleService as unknown as ScaleService,
      contextProvider as unknown as ContextProvider,
   ));

   comp.model = opts.model ?? makeModel();
   comp.chartObject = opts.plot ?? makePlot();

   return {
      comp, chartService, changeRef, zone, debounceService, modelService,
      http, modal, contextProvider, scaleService, scaleSubject,
   };
}
