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
 * Shared fixtures for chart-axis-area.component.{interaction,risk,display}.tl.spec.ts.
 *
 * Direct instantiation via `new ChartAxisArea(...)`, matching the ChartArea/VSTextInput/VSSlider
 * convention — EXCEPT the base class `ChartObjectAreaBase`'s constructor calls Angular's
 * `inject(NgZone)` directly, which throws "must be called from an injection context" outside of
 * Angular's own factory machinery. `TestBed.runInInjectionContext(...)` provides that context for
 * a plain `new` call without pulling in full ATL render().
 */

import { ChangeDetectorRef, Renderer2 } from "@angular/core";
import { TestBed } from "@angular/core/testing";
import { Subject } from "rxjs";
import { ChartAxisArea } from "./chart-axis-area.component";
import { ChartService } from "../services/chart.service";
import { DebounceService } from "../../widget/services/debounce.service";
import { ContextProvider } from "../../vsobjects/context-provider.service";
import { ScaleService } from "../../widget/services/scale/scale-service";
import { ChartModel } from "../model/chart-model";
import { Axis } from "../model/axis";
import { Rectangle } from "../../common/data/rectangle";

export function makeAxis(overrides: Partial<Axis> = {}): Axis {
   return Object.assign({
      areaName: "bottom_x_axis",
      layoutBounds: new Rectangle(0, 0, 200, 30),
      bounds: new Rectangle(0, 0, 200, 30),
      regions: [],
      tiles: [],
      axisType: "x",
      sortOp: "",
      axisOps: ["", ""],
      axisFields: ["Field1"],
      axisSizes: [100, 100],
      secondary: false,
   }, overrides) as Axis;
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
   axis?: Axis;
   model?: ChartModel;
}

export function createComponent(opts: CreateComponentOpts = {}) {
   const chartService = { clearCanvas: vi.fn(), drawRectangle: vi.fn() };
   const renderer = { listen: vi.fn(() => vi.fn()) };
   const debounceService = { debounce: vi.fn((_key: string, fn: Function) => fn()) };
   const changeRef = { detectChanges: vi.fn() };
   const contextProvider = { vsWizard: false, vsWizardPreview: false };
   const scaleSubject = new Subject<number>();
   const scaleService = {
      getScale: vi.fn(() => scaleSubject.asObservable()),
      getCurrentScale: vi.fn(() => 1),
   };

   const comp = TestBed.runInInjectionContext(() => new ChartAxisArea(
      chartService as unknown as ChartService,
      renderer as unknown as Renderer2,
      debounceService as unknown as DebounceService,
      changeRef as unknown as ChangeDetectorRef,
      contextProvider as unknown as ContextProvider,
      scaleService as unknown as ScaleService,
   ));

   comp.model = opts.model ?? makeModel();
   comp.chartObject = opts.axis ?? makeAxis();

   return { comp, chartService, renderer, debounceService, changeRef, contextProvider, scaleService, scaleSubject };
}
