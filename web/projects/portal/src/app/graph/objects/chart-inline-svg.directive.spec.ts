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
import { ElementRef } from "@angular/core";
import { ChartInlineSvgDirective } from "./chart-inline-svg.directive";

function makeDirective(html: string): { dir: ChartInlineSvgDirective, host: HTMLElement } {
   const host = document.createElement("div");
   host.innerHTML = html;
   const dir = new ChartInlineSvgDirective(new ElementRef(host), {} as any);
   return { dir, host };
}

function opacities(host: HTMLElement, selector: string): string[] {
   return Array.from(host.querySelectorAll<HTMLElement>(selector)).map(e => e.style.opacity);
}

describe("ChartInlineSvgDirective cross-tile dim", () => {
   describe("setExternalSeriesDim (area/line, keyed on data-color)", () => {
      const html = `
         <div class="inetsoft-area" data-color="1,2,3"></div>
         <div class="inetsoft-line" data-color="1,2,3"></div>
         <div class="inetsoft-area" data-color="9,9,9"></div>
         <div class="inetsoft-point" data-color="9,9,9"></div>`;
      const sel = ".inetsoft-area,.inetsoft-line,.inetsoft-point";

      it("dims only the elements whose data-color differs from the active value", () => {
         const { dir, host } = makeDirective(html);
         dir.setExternalSeriesDim("1,2,3");
         // matching series stays at full opacity; the other series is dimmed.
         expect(opacities(host, sel)).toEqual(["", "", "0.2", "0.2"]);
      });

      it("clears all opacity when passed null", () => {
         const { dir, host } = makeDirective(html);
         dir.setExternalSeriesDim("1,2,3");
         dir.setExternalSeriesDim(null);
         expect(opacities(host, sel)).toEqual(["", "", "", ""]);
      });

      it("re-targets the active series when the value changes", () => {
         const { dir, host } = makeDirective(html);
         dir.setExternalSeriesDim("1,2,3");
         dir.setExternalSeriesDim("9,9,9");
         expect(opacities(host, sel)).toEqual(["0.2", "0.2", "", ""]);
      });
   });

   describe("setExternalSeriesDim (radar, keyed on data-row)", () => {
      it("dims by data-row when configured for radar", () => {
         const { dir, host } = makeDirective(`
            <div class="inetsoft-radar" data-row="0"></div>
            <div class="inetsoft-radar" data-row="1"></div>
            <div class="inetsoft-point" data-row="1"></div>`);
         (dir as any).dimKeyAttr = "data-row";
         (dir as any).dimTargetSelector = ".inetsoft-radar,.inetsoft-point";
         dir.setExternalSeriesDim("1");
         expect(opacities(host, ".inetsoft-radar,.inetsoft-point")).toEqual(["0.2", "", ""]);
      });
   });

   describe("emitSeriesDim dedup", () => {
      it("emits only when the value actually changes", () => {
         const { dir } = makeDirective("");
         const emitted: (string | null)[] = [];
         dir.seriesDimChange.subscribe(v => emitted.push(v));
         (dir as any).emitSeriesDim("a");
         (dir as any).emitSeriesDim("a");
         (dir as any).emitSeriesDim(null);
         (dir as any).emitSeriesDim(null);
         (dir as any).emitSeriesDim("a");
         // A leave (null) between two hovers of the same series re-emits, so a re-enter is
         // never swallowed by the dedup — the parent's debounce relies on this.
         expect(emitted).toEqual(["a", null, "a"]);
      });
   });

   describe("cross-tile dim-all lifecycle", () => {
      it("clears inetsoft-dim-all on a sibling tile when the hover ends", () => {
         vi.useFakeTimers();
         try {
            const { dir, host } = makeDirective(
               `<div class="inetsoft-bar" data-row="1" data-col="0"></div>`);
            // A loaded sibling tile: holds bar elements but not the element being hovered.
            (dir as any).svgRootEl = host;
            (dir as any).elementGroupMap = new Map([["1-0", host.querySelector(".inetsoft-bar")]]);

            // Hover a data point that lives in another tile (key absent from this tile's map).
            dir.highlightElement(0, 0);
            expect(host.classList.contains("inetsoft-dim-all")).toBe(true);

            // Mouse leaves the chart: the parent calls highlightElement(null, null) on every tile.
            dir.highlightElement(null, null);
            vi.advanceTimersByTime(200);
            expect(host.classList.contains("inetsoft-dim-all")).toBe(false);
         }
         finally {
            vi.useRealTimers();
         }
      });
   });

   describe("setExternalRelationHighlight", () => {
      const html = `
         <div class="inetsoft-relation" data-id="A" data-row="0" data-col="0"></div>
         <div class="inetsoft-relation" data-id="B" data-row="1" data-col="0"></div>
         <div class="inetsoft-relation" data-id="C" data-row="2" data-col="0"></div>
         <div class="inetsoft-relation-edge" data-source="A" data-target="B"></div>
         <div class="inetsoft-relation-edge" data-source="B" data-target="C"></div>
         <div class="inetsoft-relation-label" data-row="1" data-col="0"></div>
         <div class="inetsoft-relation-label" data-row="2" data-col="0"></div>`;

      function activeFlags(host: HTMLElement, sel: string): boolean[] {
         return Array.from(host.querySelectorAll(sel)).map(e => e.classList.contains("inetsoft-active"));
      }

      it("activates the hovered node, its neighbours, incident edges and their labels", () => {
         const { dir, host } = makeDirective(html);
         (dir as any).svgRootEl = host;
         dir.setExternalRelationHighlight(new Set(["B", "A", "C"]), "B");
         expect(activeFlags(host, ".inetsoft-relation")).toEqual([true, true, true]);
         expect(activeFlags(host, ".inetsoft-relation-edge")).toEqual([true, true]);
         expect(activeFlags(host, ".inetsoft-relation-label")).toEqual([true, true]);
         expect(host.classList.contains("inetsoft-dim-all")).toBe(false);
      });

      it("dim-alls a tile that holds no active element", () => {
         const { dir, host } = makeDirective(`
            <div class="inetsoft-relation" data-id="X" data-row="0" data-col="0"></div>
            <div class="inetsoft-relation" data-id="Y" data-row="1" data-col="0"></div>`);
         (dir as any).svgRootEl = host;
         dir.setExternalRelationHighlight(new Set(["A", "B"]), "A");
         expect(activeFlags(host, ".inetsoft-relation")).toEqual([false, false]);
         expect(host.classList.contains("inetsoft-dim-all")).toBe(true);
      });

      it("does not dim-all a tile holding only an edge incident to the hovered node", () => {
         // The neighbour node lives in another tile; here only node Q (unrelated) and the
         // passing-through A->Z edge exist. The active edge must keep the tile un-dim-alled so
         // the server :has() rule dims Q while the edge stays lit.
         const { dir, host } = makeDirective(`
            <div class="inetsoft-relation" data-id="Q" data-row="0" data-col="0"></div>
            <div class="inetsoft-relation-edge" data-source="A" data-target="Z"></div>`);
         (dir as any).svgRootEl = host;
         dir.setExternalRelationHighlight(new Set(["A", "Z"]), "A");
         expect(host.querySelector(".inetsoft-relation")!.classList.contains("inetsoft-active")).toBe(false);
         expect(host.querySelector(".inetsoft-relation-edge")!.classList.contains("inetsoft-active")).toBe(true);
         expect(host.classList.contains("inetsoft-dim-all")).toBe(false);
      });

      it("clears all active classes and dim-all on null", () => {
         const { dir, host } = makeDirective(html);
         (dir as any).svgRootEl = host;
         dir.setExternalRelationHighlight(new Set(["A", "B", "C"]), "B");
         host.classList.add("inetsoft-dim-all");
         dir.setExternalRelationHighlight(null, null);
         expect(activeFlags(host, ".inetsoft-relation,.inetsoft-relation-edge,.inetsoft-relation-label")
            .some(Boolean)).toBe(false);
         expect(host.classList.contains("inetsoft-dim-all")).toBe(false);
      });
   });

   describe("activateTreemapDescendants (hierarchical hover)", () => {
      // 3-level tree (leaf=0, root=highest): a top container P (level 2) with two mid containers
      // C (level 1) and C2 (level 1), each holding one leaf (level 0). P, C and its leaf were all
      // created from the same source row so they share data-subrow="0" (the "first-descended"
      // chain); C2 and its leaf share data-subrow="1". Keys are unique by data-row/data-col.
      const html = `
         <svg>
            <g class="inetsoft-treemap" data-row="0" data-col="0" data-level="2" data-subrow="0" data-childrows="0,1"></g>
            <g class="inetsoft-treemap" data-row="0" data-col="1" data-level="1" data-subrow="0" data-childrows="0"></g>
            <g class="inetsoft-treemap" data-row="0" data-col="2" data-level="0" data-subrow="0"></g>
            <g class="inetsoft-treemap" data-row="1" data-col="1" data-level="1" data-subrow="1" data-childrows="1"></g>
            <g class="inetsoft-treemap" data-row="1" data-col="2" data-level="0" data-subrow="1"></g>
            <g class="inetsoft-treemap-label" data-row="0" data-col="2"></g>
         </svg>`;

      function isActive(host: HTMLElement, sel: string): boolean {
         return host.querySelector(sel)!.classList.contains("inetsoft-active");
      }

      it("keeps the hovered mid container's subtree undimmed without activating its ancestor", () => {
         const { dir, host } = makeDirective(html);
         (dir as any).afterSvgInjected();
         // Hover the mid container C (row 0, col 1).
         dir.highlightElement(0, 1);
         // C itself and its leaf (+ the leaf's label) stay undimmed.
         expect(isActive(host, "[data-col='1'][data-row='0'].inetsoft-treemap")).toBe(true);
         expect(isActive(host, "[data-col='2'][data-row='0'].inetsoft-treemap")).toBe(true);
         expect(isActive(host, ".inetsoft-treemap-label")).toBe(true);
         // The ancestor P shares C's data-subrow but must NOT be activated (it is not nested inside
         // C) — the data-level guard excludes it. Unrelated sibling subtree stays dimmable too.
         expect(isActive(host, "[data-col='0'][data-row='0'].inetsoft-treemap")).toBe(false);
         expect(isActive(host, "[data-col='1'][data-row='1'].inetsoft-treemap")).toBe(false);
         expect(isActive(host, "[data-col='2'][data-row='1'].inetsoft-treemap")).toBe(false);
      });

      it("activates the entire subtree when hovering the top container", () => {
         const { dir, host } = makeDirective(html);
         (dir as any).afterSvgInjected();
         // Hover the top container P (row 0, col 0).
         dir.highlightElement(0, 0);
         expect(isActive(host, "[data-col='1'][data-row='0'].inetsoft-treemap")).toBe(true);
         expect(isActive(host, "[data-col='2'][data-row='0'].inetsoft-treemap")).toBe(true);
         expect(isActive(host, "[data-col='1'][data-row='1'].inetsoft-treemap")).toBe(true);
         expect(isActive(host, "[data-col='2'][data-row='1'].inetsoft-treemap")).toBe(true);
      });

      it("activates only the leaf when hovering a leaf node", () => {
         const { dir, host } = makeDirective(html);
         (dir as any).afterSvgInjected();
         // Hover the leaf under C (row 0, col 2) — no data-childrows, so nothing else lights up.
         dir.highlightElement(0, 2);
         expect(isActive(host, "[data-col='2'][data-row='0'].inetsoft-treemap")).toBe(true);
         expect(isActive(host, "[data-col='1'][data-row='0'].inetsoft-treemap")).toBe(false);
         expect(isActive(host, "[data-col='0'][data-row='0'].inetsoft-treemap")).toBe(false);
      });
   });

   describe("milestone point label activation (gantt)", () => {
      // <svg> sets svgRootEl; two milestone markers + labels keyed by data-row/col.
      const html = `
         <svg>
            <g class="inetsoft-point" data-row="0" data-col="0"></g>
            <g class="inetsoft-point-label" data-row="0" data-col="0"></g>
            <g class="inetsoft-point" data-row="1" data-col="0"></g>
            <g class="inetsoft-point-label" data-row="1" data-col="0"></g>
         </svg>`;

      function activeFlags(host: HTMLElement, sel: string): boolean[] {
         return Array.from(host.querySelectorAll(sel)).map(e => e.classList.contains("inetsoft-active"));
      }

      it("activates the hovered point's own label so it is not dimmed", () => {
         const { dir, host } = makeDirective(html);
         (dir as any).afterSvgInjected();

         dir.highlightElement(0, 0);
         // The hovered marker and its label are active; the other row stays inactive (dimmable).
         expect(activeFlags(host, ".inetsoft-point")).toEqual([true, false]);
         expect(activeFlags(host, ".inetsoft-point-label")).toEqual([true, false]);
      });

      it("clears the label active class when the hover ends", () => {
         vi.useFakeTimers();
         try {
            const { dir, host } = makeDirective(html);
            (dir as any).afterSvgInjected();
            dir.highlightElement(0, 0);
            dir.highlightElement(null, null);
            vi.advanceTimersByTime(ChartInlineSvgDirective["CLEAR_DELAY_MS"]);
            expect(activeFlags(host, ".inetsoft-point,.inetsoft-point-label").some(Boolean)).toBe(false);
         }
         finally {
            vi.useRealTimers();
         }
      });
   });

   describe("highlightElements (stacked bar column)", () => {
      const html = `
         <svg>
            <g class="inetsoft-bar" data-row="0" data-col="0"></g>
            <g class="inetsoft-bar" data-row="1" data-col="0"></g>
            <g class="inetsoft-bar" data-row="2" data-col="0"></g>
            <g class="inetsoft-bar-label" data-row="0" data-col="0"></g>
            <g class="inetsoft-bar-label" data-row="1" data-col="0"></g>
         </svg>`;

      function activeFlags(host: HTMLElement, sel: string): boolean[] {
         return Array.from(host.querySelectorAll(sel)).map(e => e.classList.contains("inetsoft-active"));
      }

      it("activates every segment of the column and its labels", () => {
         const { dir, host } = makeDirective(html);
         (dir as any).afterSvgInjected();
         dir.highlightElements([{ row: 0, col: 0 }, { row: 1, col: 0 }]);
         // Both hovered segments are active; the third segment stays dimmable.
         expect(activeFlags(host, ".inetsoft-bar")).toEqual([true, true, false]);
         expect(activeFlags(host, ".inetsoft-bar-label")).toEqual([true, true]);
      });

      it("clears the whole active set when the hover ends", () => {
         vi.useFakeTimers();
         try {
            const { dir, host } = makeDirective(html);
            (dir as any).afterSvgInjected();
            dir.highlightElements([{ row: 0, col: 0 }, { row: 1, col: 0 }]);
            dir.highlightElements([]);
            vi.advanceTimersByTime(ChartInlineSvgDirective["CLEAR_DELAY_MS"]);
            expect(activeFlags(host, ".inetsoft-bar,.inetsoft-bar-label").some(Boolean)).toBe(false);
         }
         finally {
            vi.useRealTimers();
         }
      });

      it("re-targets the active set without leaving stale active segments", () => {
         const { dir, host } = makeDirective(html);
         (dir as any).afterSvgInjected();
         dir.highlightElements([{ row: 0, col: 0 }, { row: 1, col: 0 }]);
         dir.highlightElements([{ row: 2, col: 0 }]);
         expect(activeFlags(host, ".inetsoft-bar")).toEqual([false, false, true]);
      });

      it("treats a reordered key set as unchanged (no deactivate/reactivate)", () => {
         const { dir, host } = makeDirective(html);
         (dir as any).afterSvgInjected();
         dir.highlightElements([{ row: 0, col: 0 }, { row: 1, col: 0 }]);
         const deactivateSpy = vi.spyOn(dir as any, "deactivateCurrent");
         // Same set, different order — sameActiveKeys is order-independent so nothing re-runs.
         dir.highlightElements([{ row: 1, col: 0 }, { row: 0, col: 0 }]);
         expect(deactivateSpy).not.toHaveBeenCalled();
         expect(activeFlags(host, ".inetsoft-bar")).toEqual([true, true, false]);
      });

      it("honors only the primary on a series-color-dimmed (area/line) tile", () => {
         const { dir, host } = makeDirective(html);
         (dir as any).afterSvgInjected();
         (dir as any).usesSeriesColorDim = true;
         dir.highlightElements([{ row: 0, col: 0 }, { row: 1, col: 0 }]);
         expect(activeFlags(host, ".inetsoft-bar")).toEqual([true, false, false]);
      });
   });

   describe("highlightSnapSeries (line chart snap dim)", () => {
      // Stacked line chart: one measure (data-series 2) split into two color groups, each with a
      // line and a point marker. Points carry row+col+color; the snapped point resolves to a color.
      const html = `
         <div class="inetsoft-line" data-color="1,1,1"></div>
         <div class="inetsoft-line" data-color="2,2,2"></div>
         <div class="inetsoft-point" data-row="0" data-col="2" data-color="1,1,1"></div>
         <div class="inetsoft-point" data-row="1" data-col="2" data-color="2,2,2"></div>`;
      const sel = ".inetsoft-line,.inetsoft-point";

      // Configure the directive as a loaded line tile. afterSvgInjected wires the abort-signal
      // hover listeners, which jsdom can't attach, so set the state it would have produced: the
      // line-hover flag plus the point-marker color maps (built from the .inetsoft-point markers).
      function makeLineTile(): { dir: ChartInlineSvgDirective, host: HTMLElement } {
         const { dir, host } = makeDirective(html);
         (dir as any).isLineSeriesHover = true;
         (dir as any).seriesColorByKey = new Map([["0-2", "1,1,1"], ["1-2", "2,2,2"]]);
         (dir as any).seriesColorByCol = new Map([["2", "1,1,1"]]);
         return { dir, host };
      }

      it("undims only the snapped point's series, dimming the others", () => {
         const { dir, host } = makeLineTile();
         dir.highlightSnapSeries([{ row: 1, col: 2 }]);
         // Series 2,2,2 (line + its point) stays full; series 1,1,1 dims.
         expect(opacities(host, sel)).toEqual(["0.2", "", "0.2", ""]);
      });

      it("re-targets the undimmed series as the snap moves between series", () => {
         const { dir, host } = makeLineTile();
         dir.highlightSnapSeries([{ row: 1, col: 2 }]);
         dir.highlightSnapSeries([{ row: 0, col: 2 }]);
         expect(opacities(host, sel)).toEqual(["", "0.2", "", "0.2"]);
      });

      it("clears the dim when the snap ends (empty pairs)", () => {
         vi.useFakeTimers();
         try {
            const { dir, host } = makeLineTile();
            dir.highlightSnapSeries([{ row: 1, col: 2 }]);
            dir.highlightSnapSeries([]);
            vi.advanceTimersByTime(ChartInlineSvgDirective["CLEAR_DELAY_MS"]);
            expect(opacities(host, sel)).toEqual(["", "", "", ""]);
         }
         finally {
            vi.useRealTimers();
         }
      });

      it("uses the col fallback only when cols distinguish series (multi-measure)", () => {
         const { dir, host } = makeLineTile();
         // Single-measure stack: one col, so the col map can't tell series apart — no fallback so
         // an unknown row-col leaves everything undimmed rather than dimming to an arbitrary color.
         dir.highlightSnapSeries([{ row: 9, col: 2 }]);
         expect(opacities(host, sel)).toEqual(["", "", "", ""]);

         // Multi-measure: distinct cols map to distinct colors, so an unknown row-col falls back
         // by col (col 3 → 2,2,2), dimming the other series.
         (dir as any).seriesColorByCol = new Map([["2", "1,1,1"], ["3", "2,2,2"]]);
         dir.highlightSnapSeries([{ row: 9, col: 3 }]);
         expect(opacities(host, sel)).toEqual(["0.2", "", "0.2", ""]);
      });

      it("is a no-op on a non-line tile (area keeps its own cursor-band hover)", () => {
         const { dir, host } = makeLineTile();
         (dir as any).isLineSeriesHover = false;
         dir.highlightSnapSeries([{ row: 1, col: 2 }]);
         expect(opacities(host, sel)).toEqual(["", "", "", ""]);
      });

      it("emits seriesDimChange in cross-tile mode instead of dimming locally", () => {
         vi.useFakeTimers();
         try {
            const { dir, host } = makeLineTile();
            (dir as any).crossTile = true;
            const emitted: (string | null)[] = [];
            dir.seriesDimChange.subscribe(v => emitted.push(v));

            // The parent applies the dim across tiles, so this tile emits the color and leaves its
            // own opacity untouched.
            dir.highlightSnapSeries([{ row: 1, col: 2 }]);
            expect(emitted).toEqual(["2,2,2"]);
            expect(opacities(host, sel)).toEqual(["", "", "", ""]);

            // Clearing emits null once the debounce elapses.
            dir.highlightSnapSeries([]);
            vi.advanceTimersByTime(ChartInlineSvgDirective["CLEAR_DELAY_MS"]);
            expect(emitted).toEqual(["2,2,2", null]);
         }
         finally {
            vi.useRealTimers();
         }
      });

      it("resolves the snap color from .inetsoft-line when the chart has no point markers", () => {
         // Multi-measure line chart with no point markers. afterSvgInjected scrapes data-series
         // (the colIdx) and data-color off the lines into seriesColorByCol, since seriesColorByKey
         // stays empty without points. A snapped row-col with no exact match then resolves by col.
         const lineHtml = `
            <svg>
               <g class="inetsoft-line" data-series="0" data-color="1,1,1"></g>
               <g class="inetsoft-line" data-series="1" data-color="2,2,2"></g>
            </svg>`;
         const { dir, host } = makeDirective(lineHtml);
         // setupLineSeriesHover wires abort-signal listeners jsdom can't attach; stub it to just
         // set the flag. The .inetsoft-line scraping loop under test runs earlier in afterSvgInjected.
         vi.spyOn(dir as any, "setupLineSeriesHover").mockImplementation(() => {
            (dir as any).isLineSeriesHover = true;
         });
         (dir as any).afterSvgInjected();

         expect((dir as any).isLineSeriesHover).toBe(true);
         expect(Array.from((dir as any).seriesColorByCol.entries()))
            .toEqual([["0", "1,1,1"], ["1", "2,2,2"]]);

         dir.highlightSnapSeries([{ row: 9, col: 1 }]);
         // Col 1 → series 2,2,2 stays full; the col-0 line dims.
         expect(opacities(host, ".inetsoft-line")).toEqual(["0.2", ""]);
      });
   });

   describe("getRelationEdges + emitRelationHover dedup", () => {
      it("returns this tile's edges as source/target pairs", () => {
         const { dir } = makeDirective("");
         (dir as any).relationEdges = [
            { el: document.createElement("div"), sourceId: "A", targetId: "B" },
            { el: document.createElement("div"), sourceId: "B", targetId: "C" }
         ];
         expect(dir.getRelationEdges()).toEqual([
            { source: "A", target: "B" }, { source: "B", target: "C" }]);
      });

      it("emits relationHover only when the node id changes", () => {
         const { dir } = makeDirective("");
         const emitted: (string | null)[] = [];
         dir.relationHover.subscribe(v => emitted.push(v));
         (dir as any).emitRelationHover("A");
         (dir as any).emitRelationHover("A");
         (dir as any).emitRelationHover(null);
         (dir as any).emitRelationHover("A");
         expect(emitted).toEqual(["A", null, "A"]);
      });
   });
});
