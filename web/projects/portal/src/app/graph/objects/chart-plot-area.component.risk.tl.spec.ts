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
 * ChartPlotArea — Pass 2: Risk
 *
 * Covers the items explicitly deferred from Pass 1 (see that file's header), skipping
 * anything already exercised there (the basic destroyed-guard and debounce-key-format
 * checks in `loaded()` — see interaction spec Group 8) to avoid duplicate coverage [B6]:
 *   - getSrc's oSrc string-comparison dedup gate: src-changed-and-visible fires
 *     fireOnLoading only; src-unchanged or not-visible falls through to fireOnLoad instead;
 *     ignoreLoadEvent bypasses both.
 *   - fireOnLoading/fireOnLoad's multi-tile aggregate gate: must wait for every visible
 *     tile, invisible tiles don't block completion.
 *   - plotScaleInfo setter: clear-vs-draw branch, vertical-vs-horizontal scale argument
 *     order, and the noselect/text region filter feeding into drawRegions.
 *   - destroyed flag wiring through ngOnDestroy -> cleanup() (Pass 1 only set the private
 *     field directly; this confirms the actual lifecycle wiring).
 */

import { ChartTool } from "../model/chart-tool";
import { createComponent, makeModel, makeTile } from "./chart-plot-area.component.test-helpers";

afterEach(() => vi.restoreAllMocks());

function setupCanvasWithContext(comp: any) {
   const canvas = document.createElement("canvas");
   const ctx = {} as CanvasRenderingContext2D;
   vi.spyOn(canvas, "getContext").mockReturnValue(ctx as any);
   comp._objectCanvas = { nativeElement: canvas };
   return ctx;
}

// ---------------------------------------------------------------------------
// Group 1: getSrc oSrc dedup gate [Risk 3]
// ---------------------------------------------------------------------------

describe("ChartPlotArea — getSrc oSrc dedup gate", () => {
   function primeVisibleUrlPrefix(comp: any, visible: boolean) {
      comp.urlPrefix = "prefix";
      vi.spyOn(comp, "isTileVisible").mockReturnValue(visible);
   }

   it("should fire onLoading (not onLoad) when the (0,0) tile's src changes while visible", () => {
      const { comp } = createComponent();
      primeVisibleUrlPrefix(comp, true);
      const tile = makeTile({ row: 0, col: 0 });
      comp.chartObject.tiles = [tile];
      const loadingEmitted: any[] = [];
      comp.onLoading.subscribe(() => loadingEmitted.push(true));
      const loadEmitted: any[] = [];
      comp.onLoad.subscribe((v: any) => loadEmitted.push(v));

      comp.getSrc(tile);

      expect(tile.loaded).toBe(false);
      expect(loadingEmitted).toHaveLength(1);
      expect(loadEmitted).toHaveLength(0);
   });

   it("should fire onLoad (not onLoading) on a second call with the same src", () => {
      const { comp } = createComponent();
      primeVisibleUrlPrefix(comp, true);
      const tile = makeTile({ row: 0, col: 0 });
      comp.chartObject.tiles = [tile];
      comp.getSrc(tile); // primes oSrc; also sets tile.loaded=false as a side effect
      tile.loaded = true; // simulate the tile having since finished loading
      const loadingEmitted: any[] = [];
      comp.onLoading.subscribe(() => loadingEmitted.push(true));
      const loadEmitted: any[] = [];
      comp.onLoad.subscribe((v: any) => loadEmitted.push(v));

      comp.getSrc(tile);

      expect(loadingEmitted).toHaveLength(0);
      expect(loadEmitted).toEqual([true]);
   });

   it("should fire onLoad (not onLoading) when the (0,0) tile's src changes while NOT visible", () => {
      const { comp } = createComponent();
      primeVisibleUrlPrefix(comp, false);
      const tile = makeTile({ row: 0, col: 0 });
      comp.chartObject.tiles = [tile];
      const loadingEmitted: any[] = [];
      comp.onLoading.subscribe(() => loadingEmitted.push(true));
      const loadEmitted: any[] = [];
      comp.onLoad.subscribe((v: any) => loadEmitted.push(v));

      comp.getSrc(tile);

      expect(loadingEmitted).toHaveLength(0);
      expect(loadEmitted).toEqual([true]);
   });

   it("should always fire onLoad and never touch oSrc for a tile that isn't (0,0)", () => {
      const { comp } = createComponent();
      primeVisibleUrlPrefix(comp, true);
      const tile = makeTile({ row: 1, col: 0 });
      comp.chartObject.tiles = [tile];
      const loadingEmitted: any[] = [];
      comp.onLoading.subscribe(() => loadingEmitted.push(true));
      const loadEmitted: any[] = [];
      comp.onLoad.subscribe((v: any) => loadEmitted.push(v));

      comp.getSrc(tile);

      expect(loadingEmitted).toHaveLength(0);
      expect(loadEmitted).toEqual([true]);
      expect((comp as any).oSrc).toBeUndefined();
   });

   it("should bypass the load-event bookkeeping entirely when ignoreLoadEvent is set", () => {
      const { comp } = createComponent();
      primeVisibleUrlPrefix(comp, true);
      const tile = makeTile({ row: 0, col: 0 });
      comp.chartObject.tiles = [tile];
      const loadingEmitted: any[] = [];
      comp.onLoading.subscribe(() => loadingEmitted.push(true));
      const loadEmitted: any[] = [];
      comp.onLoad.subscribe((v: any) => loadEmitted.push(v));

      comp.getSrc(tile, null, true);

      expect(loadingEmitted).toHaveLength(0);
      expect(loadEmitted).toHaveLength(0);
      expect((comp as any).oSrc).toBeUndefined();
   });
});

// ---------------------------------------------------------------------------
// Group 2: fireOnLoading / fireOnLoad multi-tile aggregate gate [Risk 2]
// ---------------------------------------------------------------------------

describe("ChartPlotArea — fireOnLoading / fireOnLoad across multiple tiles", () => {
   it("should NOT fire onLoad while another visible tile is still loading", () => {
      const { comp } = createComponent();
      const tile1 = makeTile({ row: 0, col: 0 });
      const tile2 = makeTile({ row: 0, col: 1 });
      (tile2 as any).loaded = false;
      comp.chartObject.tiles = [tile1, tile2];
      vi.spyOn(comp, "isTileVisible").mockReturnValue(true);
      const emitted: any[] = [];
      comp.onLoad.subscribe((v: any) => emitted.push(v));

      comp.loaded(true, tile1);

      expect(emitted).toHaveLength(0);
   });

   it("should fire onLoad once every visible tile has finished, ignoring a still-loading tile that is off-screen", () => {
      const { comp } = createComponent();
      const tile1 = makeTile({ row: 0, col: 0 });
      const offscreenTile = makeTile({ row: 0, col: 1 });
      (offscreenTile as any).loaded = false;
      comp.chartObject.tiles = [tile1, offscreenTile];
      vi.spyOn(comp, "isTileVisible").mockImplementation((t: any) => t === tile1);
      const emitted: any[] = [];
      comp.onLoad.subscribe((v: any) => emitted.push(v));

      comp.loaded(true, tile1);

      expect(emitted).toEqual([true]);
   });

   it("should stop scanning and fire onLoading once it finds the first unloaded visible tile", () => {
      const { comp } = createComponent();
      const loadedTile = makeTile({ row: 0, col: 0 });
      (loadedTile as any).loaded = true;
      const targetTile = makeTile({ row: 0, col: 1 });
      comp.chartObject.tiles = [loadedTile, targetTile];
      vi.spyOn(comp, "isTileVisible").mockReturnValue(true);
      const emitted: any[] = [];
      comp.onLoading.subscribe(() => emitted.push(true));

      comp.loading(targetTile);

      expect(emitted).toEqual([true]);
   });

   it("should NOT fire onLoading when every visible tile is already loaded", () => {
      const { comp } = createComponent();
      const tile1 = makeTile({ row: 0, col: 0 });
      (tile1 as any).loaded = true;
      const tile2 = makeTile({ row: 0, col: 1 });
      (tile2 as any).loaded = true;
      comp.chartObject.tiles = [tile1, tile2];
      vi.spyOn(comp, "isTileVisible").mockReturnValue(true);
      const emitted: any[] = [];
      comp.onLoading.subscribe(() => emitted.push(true));

      // Bypass: fireOnLoading is private; the only public entry point (loading(tile)) always
      // forces its target tile to loaded=false first, which would contradict this
      // already-all-loaded setup, so it's invoked directly.
      (comp as any).fireOnLoading();

      expect(emitted).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 3: plotScaleInfo setter [Risk 2]
// ---------------------------------------------------------------------------

describe("ChartPlotArea — plotScaleInfo setter", () => {
   it("should do nothing when set to a falsy value", () => {
      const { comp, chartService } = createComponent();
      setupCanvasWithContext(comp);

      expect(() => comp.plotScaleInfo = null).not.toThrow();
      expect(chartService.clearCanvas).not.toHaveBeenCalled();
   });

   it("should do nothing when there is no canvas context", () => {
      const { comp, chartService } = createComponent();
      const drawSpy = vi.spyOn(ChartTool, "drawRegions").mockImplementation(() => {});

      comp.plotScaleInfo = { scale: 2, vertical: true, clear: false };

      expect(chartService.clearCanvas).not.toHaveBeenCalled();
      expect(drawSpy).not.toHaveBeenCalled();
   });

   it("should only clear the canvas and skip drawing when clear is true", () => {
      const { comp, chartService } = createComponent();
      const ctx = setupCanvasWithContext(comp);
      const drawSpy = vi.spyOn(ChartTool, "drawRegions").mockImplementation(() => {});

      comp.plotScaleInfo = { scale: 2, vertical: true, clear: true };

      expect(chartService.clearCanvas).toHaveBeenCalledWith(ctx);
      expect(drawSpy).not.toHaveBeenCalled();
   });

   it("should skip drawing when clear is false but there is no model yet", () => {
      const { comp } = createComponent();
      setupCanvasWithContext(comp);
      (comp as any)._model = undefined;
      const drawSpy = vi.spyOn(ChartTool, "drawRegions").mockImplementation(() => {});

      comp.plotScaleInfo = { scale: 2, vertical: true, clear: false };

      expect(drawSpy).not.toHaveBeenCalled();
   });

   it("should draw with (1, scale) for a vertical scale", () => {
      const { comp } = createComponent({ model: makeModel() });
      // Mutate regions directly post-construction to avoid re-triggering the chartObject
      // setter's updateRegionTree(), which needs full region geometry (segTypes/pts) to
      // build a real region tree — a minimal { noselect: false } stub would crash it.
      comp.chartObject.regions = [{ noselect: false }] as any;
      const ctx = setupCanvasWithContext(comp);
      const drawSpy = vi.spyOn(ChartTool, "drawRegions").mockImplementation(() => {});
      comp.scrollLeft = 3;
      comp.scrollTop = 7;

      comp.plotScaleInfo = { scale: 2, vertical: true, clear: false };

      expect(drawSpy).toHaveBeenCalledWith(
         ctx, comp.chartObject.regions, 3, 7, 1, 1, 2
      );
   });

   it("should draw with (scale, 1) for a horizontal scale", () => {
      const { comp } = createComponent({ model: makeModel() });
      comp.chartObject.regions = [{ noselect: false }] as any;
      const ctx = setupCanvasWithContext(comp);
      const drawSpy = vi.spyOn(ChartTool, "drawRegions").mockImplementation(() => {});

      comp.plotScaleInfo = { scale: 2, vertical: false, clear: false };

      expect(drawSpy).toHaveBeenCalledWith(
         ctx, comp.chartObject.regions, 0, 0, 1, 2, 1
      );
   });

   it("should exclude noselect and text-area regions from the drawn set", () => {
      const keep = { noselect: false, metaIdx: 0 };
      const noselectRegion = { noselect: true, metaIdx: 0 };
      const textRegion = { noselect: false, metaIdx: 1 };
      const { comp } = createComponent({
         model: makeModel({ regionMetaDictionary: [{ areaType: "vo" }, { areaType: "text" }] as any }),
      });
      comp.chartObject.regions = [keep, noselectRegion, textRegion] as any;
      const ctx = setupCanvasWithContext(comp);
      const drawSpy = vi.spyOn(ChartTool, "drawRegions").mockImplementation(() => {});

      comp.plotScaleInfo = { scale: 2, vertical: false, clear: false };

      expect(drawSpy).toHaveBeenCalledWith(ctx, [keep], 0, 0, 1, 2, 1);
   });
});

// ---------------------------------------------------------------------------
// Group 4: destroyed flag wiring through ngOnDestroy [Risk 2]
// ---------------------------------------------------------------------------

describe("ChartPlotArea — ngOnDestroy sets the destroyed flag", () => {
   it("should mark the component destroyed via cleanup() when torn down", () => {
      const { comp } = createComponent();
      expect(comp.destroyed).toBe(false);

      comp.ngOnDestroy();

      expect(comp.destroyed).toBe(true);
   });
});
