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
