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

   describe("activateBoxGroup (box plot box + its outlier markers)", () => {
      // Two box groups, each a .inetsoft-box glyph plus outlier .inetsoft-point markers drawn over
      // it. Box and outliers are separate BoxDataSet rows (distinct data-row) tied together only by
      // the server-stamped data-group key.
      const html = `
         <svg>
            <g class="inetsoft-box" data-row="0" data-col="0" data-group="AK"></g>
            <g class="inetsoft-point" data-row="1" data-col="0" data-group="AK"></g>
            <g class="inetsoft-point" data-row="2" data-col="0" data-group="AK"></g>
            <g class="inetsoft-box" data-row="3" data-col="0" data-group="NY"></g>
            <g class="inetsoft-point" data-row="4" data-col="0" data-group="NY"></g>
         </svg>`;

      function isActive(host: HTMLElement, sel: string): boolean {
         return host.querySelector(sel)!.classList.contains("inetsoft-active");
      }

      it("activates the hovered box's own outlier points so they are not dimmed", () => {
         const { dir, host } = makeDirective(html);
         (dir as any).afterSvgInjected();
         dir.highlightElement(0, 0);
         expect(isActive(host, "[data-row='0']")).toBe(true);
         expect(isActive(host, "[data-row='1']")).toBe(true);
         expect(isActive(host, "[data-row='2']")).toBe(true);
         // The other group stays dimmable.
         expect(isActive(host, "[data-row='3']")).toBe(false);
         expect(isActive(host, "[data-row='4']")).toBe(false);
      });

      it("activates the owning box when an outlier point is hovered", () => {
         const { dir, host } = makeDirective(html);
         (dir as any).afterSvgInjected();
         dir.highlightElement(4, 0);
         expect(isActive(host, "[data-row='4']")).toBe(true);
         expect(isActive(host, "[data-row='3']")).toBe(true);
         expect(isActive(host, "[data-row='0']")).toBe(false);
      });

      it("clears the group's active classes when the hover ends", () => {
         vi.useFakeTimers();

         try {
            const { dir, host } = makeDirective(html);
            (dir as any).afterSvgInjected();
            dir.highlightElement(0, 0);
            dir.highlightElements([]);
            vi.advanceTimersByTime(ChartInlineSvgDirective["CLEAR_DELAY_MS"]);
            expect(host.querySelectorAll(".inetsoft-active").length).toBe(0);
         }
         finally {
            vi.useRealTimers();
         }
      });

      it("is a no-op for annotation groups carrying no data-group", () => {
         const { dir, host } = makeDirective(`
            <svg>
               <g class="inetsoft-point" data-row="0" data-col="0"></g>
               <g class="inetsoft-point" data-row="1" data-col="0"></g>
            </svg>`);
         (dir as any).afterSvgInjected();
         dir.highlightElement(0, 0);
         expect(isActive(host, "[data-row='0']")).toBe(true);
         expect(isActive(host, "[data-row='1']")).toBe(false);
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

   describe("multi-style chart (bars + line in one SVG)", () => {
      // A multi-style chart binds a chart type per measure, so one SVG holds bar VOs and a line
      // series. Both hover mechanisms must stay live: bars highlight by inetsoft-active (server
      // :has() dim), the line resolves from the cursor and dims by inline opacity.
      const html = `
         <svg>
            <g class="inetsoft-bar" data-row="0" data-col="0"></g>
            <g class="inetsoft-bar" data-row="1" data-col="0"></g>
            <g class="inetsoft-bar-label" data-row="0" data-col="0"></g>
            <g class="inetsoft-line" data-series="1" data-color="1,2,3"><path></path></g>
         </svg>`;

      function activeFlags(host: HTMLElement, sel: string): boolean[] {
         return Array.from(host.querySelectorAll(sel)).map(e => e.classList.contains("inetsoft-active"));
      }

      function makeMixedTile(): { dir: ChartInlineSvgDirective, host: HTMLElement } {
         const { dir, host } = makeDirective(html);
         (dir as any).afterSvgInjected();
         return { dir, host };
      }

      it("keeps the bar entries in elementGroupMap so a bar still highlights", () => {
         const { dir, host } = makeMixedTile();
         expect((dir as any).mixedHover).toBe(true);
         dir.highlightElement(0, 0);
         expect(activeFlags(host, ".inetsoft-bar")).toEqual([true, false]);
         expect(activeFlags(host, ".inetsoft-bar-label")).toEqual([true]);
      });

      it("dims the line series while a bar is highlighted, and releases it after", () => {
         vi.useFakeTimers();
         try {
            const { dir, host } = makeMixedTile();
            dir.highlightElement(0, 0);
            expect(opacities(host, ".inetsoft-line")).toEqual(["0.2"]);
            dir.highlightElement(null, null);
            vi.advanceTimersByTime(ChartInlineSvgDirective["CLEAR_DELAY_MS"]);
            expect(opacities(host, ".inetsoft-line")).toEqual([""]);
         }
         finally {
            vi.useRealTimers();
         }
      });

      it("activates every segment of a stacked column even though a line series is present", () => {
         const { dir, host } = makeMixedTile();
         // usesSeriesColorDim is true here (the line hover set it), but the keys are bars, so the
         // trim that protects a pure line tile's shared markers must not apply.
         expect((dir as any).usesSeriesColorDim).toBe(true);
         dir.highlightElements([{ row: 0, col: 0 }, { row: 1, col: 0 }]);
         expect(activeFlags(host, ".inetsoft-bar")).toEqual([true, true]);
      });

      it("dims the bars when the cursor resolves the line series", () => {
         const { dir, host } = makeMixedTile();
         // jsdom has no path geometry, so stub the per-series y at the cursor x.
         (dir as any).getScreenYAtX = () => 100;
         (dir as any).onAreaMouseMove({ clientX: 10, clientY: 100 } as MouseEvent);
         expect((dir as any).activeSeriesIdx).toBe(0);
         expect(opacities(host, ".inetsoft-bar")).toEqual(["0.2", "0.2"]);
         expect(opacities(host, ".inetsoft-bar-label")).toEqual(["0.2"]);
         // Cursor moves out of range of every series — bars come back.
         (dir as any).getScreenYAtX = () => NaN;
         (dir as any).onAreaMouseMove({ clientX: 10, clientY: 100 } as MouseEvent);
         expect(opacities(host, ".inetsoft-bar")).toEqual(["", ""]);
      });

      it("lets the hovered bar keep the highlight instead of a line passing nearby", () => {
         const { dir, host } = makeMixedTile();
         (dir as any).getScreenYAtX = () => 100;
         dir.highlightElement(0, 0);
         (dir as any).onAreaMouseMove({ clientX: 10, clientY: 100 } as MouseEvent);
         // The proximity hover is suppressed: the bar stays active and the line stays dimmed.
         expect((dir as any).activeSeriesIdx).toBe(-1);
         expect(activeFlags(host, ".inetsoft-bar")).toEqual([true, false]);
         expect(opacities(host, ".inetsoft-line")).toEqual(["0.2"]);
      });

      it("dims the bars when snap resolves the line series, and releases on a bar snap", () => {
         vi.useFakeTimers();
         try {
            // Snap needs a point marker to resolve the snapped row/col to a series color.
            const { dir, host } = makeDirective(`
               <svg>
                  <g class="inetsoft-bar" data-row="0" data-col="0"></g>
                  <g class="inetsoft-line" data-series="1" data-color="1,2,3"><path></path></g>
                  <g class="inetsoft-point" data-row="0" data-col="1" data-color="1,2,3"></g>
               </svg>`);
            (dir as any).afterSvgInjected();
            expect((dir as any).mixedHover).toBe(true);

            // Under snap, onAreaMouseMove stands down and the guideline drives the series dim.
            dir.snapTooltip = true;
            dir.highlightSnapSeries([{ row: 0, col: 1 }]);
            expect(opacities(host, ".inetsoft-bar")).toEqual(["0.2"]);

            // Snapping onto a bar column routes to highlightElements, which must lift the bar dim.
            dir.highlightElements([{ row: 0, col: 0 }]);
            expect(opacities(host, ".inetsoft-bar")).toEqual([""]);
            expect(activeFlags(host, ".inetsoft-bar")).toEqual([true]);

            // Guideline moves back onto the same series: the bars must dim again even though the
            // resolved color is unchanged.
            dir.highlightElements([]);
            dir.highlightSnapSeries([{ row: 0, col: 1 }]);
            expect(opacities(host, ".inetsoft-bar")).toEqual(["0.2"]);

            // Leaving the plot clears both mechanisms.
            dir.highlightElements([]);
            dir.highlightSnapSeries([]);
            vi.advanceTimersByTime(ChartInlineSvgDirective["CLEAR_DELAY_MS"]);
            expect(opacities(host, ".inetsoft-bar,.inetsoft-line")).toEqual(["", ""]);
            expect(activeFlags(host, ".inetsoft-bar")).toEqual([false]);
         }
         finally {
            vi.useRealTimers();
         }
      });

      it("leaves a pure line chart's canvas-driven highlight a no-op", () => {
         const { dir, host } = makeDirective(`
            <svg>
               <g class="inetsoft-line" data-series="1" data-color="1,2,3"><path></path></g>
               <g class="inetsoft-point" data-row="0" data-col="1" data-color="1,2,3"></g>
            </svg>`);
         (dir as any).afterSvgInjected();
         expect((dir as any).mixedHover).toBe(false);
         expect((dir as any).elementGroupMap.size).toBe(0);
         dir.highlightElement(0, 1);
         expect(activeFlags(host, ".inetsoft-point")).toEqual([false]);
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
         // setupLineHover samples SVG path geometry, which jsdom does not implement; stub it to
         // just set the flag. The .inetsoft-line scraping loop under test runs earlier in
         // afterSvgInjected.
         vi.spyOn(dir as any, "setupLineHover").mockImplementation(() => {
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

   describe("cursor-resolved series hover (onAreaMouseMove)", () => {
      // Faceted line chart: two panels, each drawing the same two-color palette, each line with its
      // own point marker. Series are resolved arithmetically from the cursor, so the entries are
      // built directly and getScreenYAtX is stubbed with the per-series y at the cursor x — the
      // sampling it normally reads needs SVG path geometry jsdom does not implement.
      const html = `
         <svg>
            <g>
               <g class="inetsoft-line" data-color="1,1,1"></g>
               <g class="inetsoft-line" data-color="2,2,2"></g>
               <g class="inetsoft-line" data-color="1,1,1"></g>
               <g class="inetsoft-line" data-color="2,2,2"></g>
               <g class="inetsoft-point" data-color="1,1,1"></g>
               <g class="inetsoft-point" data-color="2,2,2"></g>
               <g class="inetsoft-point" data-color="1,1,1"></g>
               <g class="inetsoft-point" data-color="2,2,2"></g>
            </g>
         </svg>`;
      const sel = ".inetsoft-line,.inetsoft-point";

      /**
       * Build a line tile whose four (panel, series) entries report the given screen y values at the
       * cursor x; null means "not in this panel" (what getScreenYAtX returns as NaN).
       */
      function makeLineTile(yValues: (number | null)[]):
         { dir: ChartInlineSvgDirective, host: HTMLElement }
      {
         const { dir, host } = makeDirective(html);
         const lines = Array.from(host.querySelectorAll(".inetsoft-line"));
         const points = Array.from(host.querySelectorAll(".inetsoft-point"));

         (dir as any).areaSeries = lines.map((lineGroup, i) => ({
            fillGroup: null, lineGroup, linePath: lineGroup, points: [points[i]]
         }));
         (dir as any).seriesHitMode = "nearest";
         (dir as any).isLineSeriesHover = true;
         (dir as any).getScreenYAtX = (idx: number) =>
            yValues[idx] === null ? NaN : yValues[idx];

         return { dir, host };
      }

      function move(dir: ChartInlineSvgDirective, clientY: number): void {
         (dir as any).onAreaMouseMove({ clientX: 10, clientY } as MouseEvent);
      }

      it("selects the nearest line even when series are only a few px apart", () => {
         // Series 0 and 1 are 4px apart in the hovered panel; the cursor at y=49 is closest to
         // series 1 (y=50), which no hit-test-based scheme could separate reliably.
         const { dir, host } = makeLineTile([46, 50, null, null]);
         move(dir, 49);
         expect(opacities(host, sel))
            .toEqual(["0.2", "", "0.2", "0.2", "0.2", "", "0.2", "0.2"]);
      });

      it("selects a line above or below the cursor", () => {
         const { dir, host } = makeLineTile([46, 50, null, null]);
         move(dir, 44);
         expect(opacities(host, ".inetsoft-line")).toEqual(["", "0.2", "0.2", "0.2"]);
      });

      it("dims the sibling facet panel, including its same-colored series", () => {
         // Entries 2 and 3 are the other panel (null y), so they cannot be selected — but they must
         // still dim, matching the whole-SVG dim scope bar charts get (Redmine #75691).
         const { dir, host } = makeLineTile([46, 50, null, null]);
         move(dir, 46);
         expect(opacities(host, ".inetsoft-line")).toEqual(["", "0.2", "0.2", "0.2"]);
      });

      it("clears the highlight past the max distance", () => {
         const { dir, host } = makeLineTile([46, 50, null, null]);
         move(dir, 46);
         move(dir, 46 + ChartInlineSvgDirective["NEAREST_MAX_DIST_PX"] + 5);
         expect(opacities(host, sel)).toEqual(["", "", "", "", "", "", "", ""]);
      });

      it("uses the ceiling rule for area charts (fill band under the line above the cursor)", () => {
         const { dir, host } = makeLineTile([46, 50, null, null]);
         (dir as any).seriesHitMode = "ceiling";
         (dir as any).isLineSeriesHover = false;
         // Both lines are above the cursor; the band belongs to the lower one (series 1, y=50).
         move(dir, 60);
         expect(opacities(host, ".inetsoft-line")).toEqual(["0.2", "", "0.2", "0.2"]);
      });

      it("emits the series color instead of dimming locally in cross-tile mode", () => {
         const { dir, host } = makeLineTile([46, 50, null, null]);
         (dir as any).crossTile = true;
         const emitted: (string | null)[] = [];
         dir.seriesDimChange.subscribe(v => emitted.push(v));
         move(dir, 50);
         expect(emitted).toEqual(["2,2,2"]);
         expect(opacities(host, sel)).toEqual(["", "", "", "", "", "", "", ""]);
      });

      it("clears the highlight when the cursor leaves the chart", () => {
         // Goes through the real SVG-level listeners rather than calling the handler directly, so
         // the mousemove → mouseleave lifecycle is covered end to end.
         const { dir, host } = makeLineTile([46, 50, null, null]);
         (dir as any).beginSeriesProximityHover();
         const svg = host.querySelector("svg") as SVGSVGElement;

         svg.dispatchEvent(new MouseEvent("mousemove", { clientX: 10, clientY: 49 }));
         expect(opacities(host, ".inetsoft-line")).toEqual(["0.2", "", "0.2", "0.2"]);

         svg.dispatchEvent(new MouseEvent("mouseleave"));
         expect(opacities(host, sel)).toEqual(["", "", "", "", "", "", "", ""]);
      });

      it("is suppressed on a line chart while the snap tooltip drives the dim", () => {
         const { dir, host } = makeLineTile([46, 50, null, null]);
         (dir as any).snapTooltip = true;
         move(dir, 48);
         expect(opacities(host, sel)).toEqual(["", "", "", "", "", "", "", ""]);
      });

      it("leaves the snap-driven dim alone when the cursor leaves a cross-tile line chart", () => {
         // Crossing into a sibling tile of a split chart fires mouseleave here while the snapped
         // series is still highlighted. Clearing then would emit null over the snap color, and
         // highlightSnapSeries keeps _snapSeriesColor set, so its dedup would never re-emit it.
         const { dir, host } = makeLineTile([46, 50, null, null]);
         (dir as any).crossTile = true;
         (dir as any).snapTooltip = true;
         (dir as any).seriesColorByKey = new Map([["1-2", "2,2,2"]]);
         const emitted: (string | null)[] = [];
         dir.seriesDimChange.subscribe(v => emitted.push(v));
         (dir as any).beginSeriesProximityHover();
         const svg = host.querySelector("svg") as SVGSVGElement;

         dir.highlightSnapSeries([{ row: 1, col: 2 }]);
         expect(emitted).toEqual(["2,2,2"]);

         svg.dispatchEvent(new MouseEvent("mouseleave"));
         expect(emitted).toEqual(["2,2,2"]);
      });
   });

   describe("matchLineSeries (point marker → its own panel's series)", () => {
      // Two panels under ONE DOM parent, same palette in each; point matching falls back to
      // data-color (all lines share data-series) and is disambiguated geometrically.
      const html = `
         <svg>
            <g>
               <g class="inetsoft-line" data-series="2" data-color="1,1,1"><path></path></g>
               <g class="inetsoft-line" data-series="2" data-color="2,2,2"><path></path></g>
               <g class="inetsoft-line" data-series="2" data-color="1,1,1"><path></path></g>
               <g class="inetsoft-line" data-series="2" data-color="2,2,2"><path></path></g>
               <g class="inetsoft-point" data-row="0" data-col="2" data-color="1,1,1"></g>
               <g class="inetsoft-point" data-row="1" data-col="2" data-color="2,2,2"></g>
               <g class="inetsoft-point" data-row="2" data-col="2" data-color="1,1,1"></g>
               <g class="inetsoft-point" data-row="3" data-col="2" data-color="2,2,2"></g>
            </g>
         </svg>`;

      // jsdom has no layout, so every getBoundingClientRect is empty; stub the rects to place
      // entries 0/1 (and their points) in the left panel and 2/3 in the right panel.
      function stubRect(el: Element, left: number): void {
         (el as any).getBoundingClientRect = () => ({
            left, right: left + 10, top: 0, bottom: 10, width: 10, height: 10, x: left, y: 0
         });
      }

      it("attaches each point to the series in its own panel", () => {
         const { dir, host } = makeDirective(html);
         const lines = Array.from(host.querySelectorAll(".inetsoft-line"));
         const points = Array.from(host.querySelectorAll(".inetsoft-point"));
         // Left panel at x=0, right panel at x=100.
         [0, 0, 100, 100].forEach((x, i) => stubRect(lines[i].querySelector("path"), x));
         [0, 0, 100, 100].forEach((x, i) => stubRect(points[i], x));

         (dir as any).matchLineSeries(lines);
         const series = (dir as any).areaSeries;

         expect(series.length).toBe(4);
         // One entry per line group — same-colored lines in different panels are never merged.
         expect(series.map((s: any) => s.lineGroup)).toEqual(lines);
         // Each entry holds only the marker from its own panel: rows 0,1 left and 2,3 right.
         expect(series.map((s: any) => s.points.map((p: Element) => p.getAttribute("data-row"))))
            .toEqual([["0"], ["1"], ["2"], ["3"]]);
      });

      it("collects a point that matches no series so it still dims", () => {
         // One line with a unique data-series, so points match by data-col; this marker's col
         // belongs to no line group.
         const { dir, host } = makeDirective(`
            <svg>
               <g>
                  <g class="inetsoft-line" data-series="2" data-color="1,1,1"><path></path></g>
                  <g class="inetsoft-point" data-row="0" data-col="9" data-color="9,9,9"></g>
               </g>
            </svg>`);
         const lines = Array.from(host.querySelectorAll(".inetsoft-line"));

         (dir as any).matchLineSeries(lines);

         expect((dir as any).areaSeries[0].points).toEqual([]);
         expect((dir as any).hoverDimOnlyElements.length).toBe(1);
      });
   });

   describe("nearestByRect", () => {
      function candidate(left: number, top: number): { el: Element, rect?: DOMRect } {
         const el = document.createElement("div");
         (el as any).getBoundingClientRect = () => ({
            left, right: left + 10, top, bottom: top + 10, width: 10, height: 10, x: left, y: top
         });
         return { el };
      }

      it("picks the candidate containing the target, in either facet direction", () => {
         const { dir } = makeDirective("");
         const target = candidate(102, 0).el;   // inside the x=100 candidate
         const columns = [candidate(0, 0), candidate(100, 0)];
         expect((dir as any).nearestByRect(target, columns)).toBe(columns[1]);

         const rowTarget = candidate(0, 202).el; // inside the y=200 candidate
         const rows = [candidate(0, 0), candidate(0, 200)];
         expect((dir as any).nearestByRect(rowTarget, rows)).toBe(rows[1]);
      });

      it("keeps the first candidate when no layout is available (all rects empty)", () => {
         const { dir } = makeDirective("");
         const flat = [{ el: document.createElement("div") }, { el: document.createElement("div") }];
         expect((dir as any).nearestByRect(document.createElement("div"), flat)).toBe(flat[0]);
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
