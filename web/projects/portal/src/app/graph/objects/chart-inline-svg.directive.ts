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
import { HttpClient } from "@angular/common/http";
import { Directive, ElementRef, EventEmitter, Input, OnDestroy, Output } from "@angular/core";
import { Subject } from "rxjs";
import { takeUntil } from "rxjs/operators";

/**
 * Fetches an SVG URL as text and injects it as inline SVG into the host element's innerHTML.
 * Used in place of [chartImage] on <img> when graph.svg.inline=true, so that the SVG
 * participates in the page DOM and CSS animations play without sandboxing restrictions.
 *
 * Hover highlighting is handled entirely by server-injected CSS using the :has() selector.
 * JS only needs to toggle the "inetsoft-active" class on the hovered bar element.
 * Call activateKey(row, col) / deactivateKey() from the canvas overlay's mouse-event handler.
 */
@Directive({
   selector: "[chartInlineSvg]"
})
export class ChartInlineSvgDirective implements OnDestroy {
   private static idCounter = 0;

   @Input()
   get chartInlineSvg(): string {
      return this._url;
   }

   set chartInlineSvg(value: string) {
      if(value !== this._url) {
         this._url = value;
         this.loadSvg();
      }
   }

   @Output() onLoading = new EventEmitter<void>();
   @Output() onLoaded = new EventEmitter<void>();
   @Output() onError = new EventEmitter<void>();
   private _url: string = null;

   /**
    * Unified map from "rowIdx-colIdx" to the annotated SVG group for that data point,
    * regardless of chart type (bar, point, etc.). The CSS class on the stored element
    * determines which server-injected hover rule fires when inetsoft-active is toggled.
    */
   private elementGroupMap = new Map<string, Element>();
   /** Maps "rowIdx-colIdx" to all glyph elements of the label paired with that data element.
    *  Batik renders each character as a separate <g text-rendering> element with the same
    *  data-row/data-col, so every glyph must be stored and toggled together. */
   private labelGroupMap = new Map<string, Element[]>();
   /** Key of the currently active element, or null. */
   private _activeKey: string | null = null;
   /** Timer handle for debounced deactivation, so fast inter-bar moves don't flash. */
   private clearHandle: ReturnType<typeof setTimeout> | null = null;
   /** Timer handle for Retry-After retry, stored so it can be cancelled on destroy. */
   private retryHandle: ReturnType<typeof setTimeout> | null = null;
   /** Timer handle for adding .ready class to the SVG after animation completes. */
   private readyHandle: ReturnType<typeof setTimeout> | null = null;
   /** Maps mxCell id → node Element for relation/tree charts. */
   private relationNodeIdMap = new Map<string, Element>();
   /** Edge connectivity for relation/tree charts: each entry holds the element plus its source/target mxCell IDs. */
   private relationEdges: Array<{el: Element, sourceId: string, targetId: string}> = [];
   /** Elements activated as neighbors of the current relation node (cleared on deactivation). */
   private activeRelationNeighbors: Element[] = [];
   /** Ordered list of area series objects; one entry per (panel, series) pair. */
   private areaSeries: Array<{fillGroup: Element, lineGroup: Element, linePath: SVGGeometryElement}> = [];
   /** Pre-sampled SVG local-coordinate points per series for fast mousemove hit-testing. */
   private areaSeriesCache: Array<{localX: number, localY: number}[]> = [];
   /** Index into areaSeries of the currently highlighted series, or -1. */
   private activeSeriesIdx: number = -1;
   /** SVG element that holds area mousemove/mouseleave listeners, kept for cleanup. */
   private areaHoverSvgEl: SVGSVGElement | null = null;
   private areaMouseMoveHandler: ((e: MouseEvent) => void) | null = null;
   private areaMouseLeaveHandler: (() => void) | null = null;
   private static readonly CLEAR_DELAY_MS = 120;
   /** Milliseconds after SVG load before the .ready class is added (gates A1 hover CSS). */
   private static readonly READY_MS = 900;
   private readonly destroy$ = new Subject<void>();

   constructor(private element: ElementRef, private http: HttpClient) {
   }

   ngOnDestroy(): void {
      this.destroy$.next();
      this.destroy$.complete();

      if(this.clearHandle !== null) {
         clearTimeout(this.clearHandle);
         this.clearHandle = null;
      }

      if(this.retryHandle !== null) {
         clearTimeout(this.retryHandle);
         this.retryHandle = null;
      }

      if(this.readyHandle !== null) {
         clearTimeout(this.readyHandle);
         this.readyHandle = null;
      }

      this.teardownAreaHover();
   }

   private loadSvg(reloading = false): void {
      if(!!this._url) {
         if(!reloading) {
            this.onLoading.emit();
         }

         const requestedUrl = this._url;

         this.http.get(this._url, { observe: "response", responseType: "text" })
            .pipe(takeUntil(this.destroy$))
            .subscribe(
            response => {
               if(response.headers?.has("Retry-After")) {
                  const interval = parseInt(response.headers.get("Retry-After"), 10) * 1000;
                  this.retryHandle = setTimeout(() => {
                     this.retryHandle = null;
                     this.loadSvg(true);
                  }, interval);
               }
               else if(requestedUrl === this._url) {
                  // SVG content comes from our own server (same origin, server-controlled).
                  // Direct innerHTML assignment is intentional — Angular's DomSanitizer only
                  // intercepts [innerHTML] template bindings, not programmatic ElementRef access.
                  // This must NOT be used with user-supplied or externally sourced SVG content.
                  this.element.nativeElement.innerHTML = this.uniquifyIds(response.body);
                  this.afterSvgInjected();
                  this.scheduleReady();
                  this.onLoaded.emit();
               }
            },
            error => {
               console.warn("Failed to load inline SVG: " + this._url + "\n", error);
               this.onError.emit();
            }
         );
      }
      else {
         this.element.nativeElement.innerHTML = "";
         this.elementGroupMap.clear();
         this.labelGroupMap.clear();
         this._activeKey = null;
      }
   }

   /**
    * Highlight the bar at rowIdx+colIdx by toggling the "inetsoft-active" CSS class.
    * The server-injected :has() rule dims all other bars/labels automatically.
    * Pass null to clear the highlight. Clearing is debounced so rapid inter-bar moves
    * don't cause a flash.
    */
   highlightElement(rowIdx: number | null, colIdx: number | null): void {
      const key = rowIdx != null ? `${rowIdx}-${colIdx}` : null;

      if(key !== null) {
         if(this.clearHandle !== null) {
            clearTimeout(this.clearHandle);
            this.clearHandle = null;
         }

         if(key !== this._activeKey) {
            this.deactivateCurrent();
            this._activeKey = key;
            this.activateKey(key);
         }
      }
      else {
         if(this._activeKey !== null && this.clearHandle === null) {
            this.clearHandle = setTimeout(() => {
               this.clearHandle = null;
               this.deactivateCurrent();
               this._activeKey = null;
            }, ChartInlineSvgDirective.CLEAR_DELAY_MS);
         }
      }
   }

   private activateKey(key: string): void {
      const el = this.elementGroupMap.get(key);
      if(el) {
         el.classList.add("inetsoft-active");
         if(el.classList.contains("inetsoft-relation")) {
            this.activateRelationNeighbors(el);
         }
      }
      const glyphs = this.labelGroupMap.get(key);
      if(glyphs) glyphs.forEach(g => g.classList.add("inetsoft-active"));
   }

   private deactivateCurrent(): void {
      if(this._activeKey === null) return;
      const el = this.elementGroupMap.get(this._activeKey);
      if(el) el.classList.remove("inetsoft-active");
      for(const n of this.activeRelationNeighbors) {
         n.classList.remove("inetsoft-active");
      }
      this.activeRelationNeighbors = [];
      const glyphs = this.labelGroupMap.get(this._activeKey);
      if(glyphs) glyphs.forEach(g => g.classList.remove("inetsoft-active"));
   }

   private activateRelationNeighbors(nodeEl: Element): void {
      const nodeId = nodeEl.getAttribute("data-id");
      if(!nodeId) return;
      this.activeRelationNeighbors = [];

      for(const edge of this.relationEdges) {
         if(edge.sourceId !== nodeId && edge.targetId !== nodeId) continue;

         edge.el.classList.add("inetsoft-active");
         this.activeRelationNeighbors.push(edge.el);

         const neighborId = edge.sourceId === nodeId ? edge.targetId : edge.sourceId;
         const neighborEl = this.relationNodeIdMap.get(neighborId);
         if(neighborEl) {
            neighborEl.classList.add("inetsoft-active");
            this.activeRelationNeighbors.push(neighborEl);

            const nRow = neighborEl.getAttribute("data-row");
            const nCol = neighborEl.getAttribute("data-col");
            if(nRow != null && nCol != null) {
               const nGlyphs = this.labelGroupMap.get(`${nRow}-${nCol}`);
               if(nGlyphs) {
                  nGlyphs.forEach(g => {
                     g.classList.add("inetsoft-active");
                     this.activeRelationNeighbors.push(g);
                  });
               }
            }
         }
      }
   }

   /**
    * Schedule adding the {@code .ready} class to the SVG root element shortly after load.
    * The {@code .ready} class gates hover CSS rules for A1 chart types (point, candle, box,
    * treemap, mekko, radar) — types where the animated group is also the hover target, so
    * setting opacity via hover during fill-mode can conflict. Bar hover is never gated.
    *
    * The gate fires {@link ChartInlineSvgDirective.READY_MS} after SVG injection — long enough
    * for the first animated element to complete, short enough not to frustrate users. If the
    * Java injector did not set {@code data-animated} (no animation was injected), {@code .ready}
    * is added immediately.
    */
   private scheduleReady(): void {
      if(this.readyHandle !== null) {
         clearTimeout(this.readyHandle);
         this.readyHandle = null;
      }

      const svgEl = this.element.nativeElement.querySelector("svg");

      if(!svgEl) {
         return;
      }

      // data-animated is a boolean presence flag set by the Java injector when any animation
      // was added. Only presence matters — the value is not read.
      const hasAnimation = svgEl.hasAttribute("data-animated");

      if(!hasAnimation) {
         // No animation injected — add .ready immediately so A1 hover works.
         svgEl.classList.add("ready");
         return;
      }

      // Gate A1 hover rules for READY_MS after the SVG loads.  Long enough for the first
      // animated element to finish, without locking out hover for the full stagger window
      // (which can be 2+ seconds on busy charts).
      this.readyHandle = setTimeout(() => {
         this.readyHandle = null;
         svgEl.classList.add("ready");
      }, ChartInlineSvgDirective.READY_MS);
   }

   /** Called after new SVG content is injected. Builds element and label lookup maps. */
   private afterSvgInjected(): void {
      if(this.clearHandle !== null) {
         clearTimeout(this.clearHandle);
         this.clearHandle = null;
      }

      this.teardownAreaHover();
      this.areaSeries = [];
      this.activeSeriesIdx = -1;

      this.elementGroupMap.clear();
      this.labelGroupMap.clear();
      this.relationNodeIdMap.clear();
      this.relationEdges = [];
      this.activeRelationNeighbors = [];
      this._activeKey = null;

      // Populate the unified element map from all annotated VO groups.
      // Each CSS class corresponds to a different chart type; the CSS class on the stored
      // element determines which server-injected hover rule fires on inetsoft-active toggle.
      for(const cssClass of [".inetsoft-bar", ".inetsoft-point", ".inetsoft-candle", ".inetsoft-box", ".inetsoft-radar", ".inetsoft-treemap", ".inetsoft-mekko", ".inetsoft-relation"]) {
         const elements = Array.from(
            this.element.nativeElement.querySelectorAll(cssClass) as NodeListOf<Element>);

         for(const el of elements) {
            const row = el.getAttribute("data-row");
            const col = el.getAttribute("data-col");

            if(row != null && col != null) {
               this.elementGroupMap.set(`${row}-${col}`, el);
            }
         }
      }

      // Radar hover is driven entirely by CSS :hover on polygon groups (hit paths injected
      // server-side have pointer-events:all so the filled interior is reactive). Vertex points
      // intentionally produce no dim effect — no JS activation and no inetsoft-active toggling.
      // The elementGroupMap is cleared so canvas-reported hover events are a no-op.
      const radarGroups = Array.from(
         this.element.nativeElement.querySelectorAll(".inetsoft-radar") as NodeListOf<Element>);

      const areaFillGroups = Array.from(
         this.element.nativeElement.querySelectorAll(".inetsoft-area") as NodeListOf<Element>);

      if(radarGroups.length > 0) {
         this.elementGroupMap.clear();

         // Raise the SVG container above the canvas overlay so CSS :hover fires on polygon
         // interior areas. pointer-events:none on the container is kept (set in the host
         // component's SCSS); events outside polygon hit areas pass through to the canvas.
         this.element.nativeElement.style.zIndex = "1";
      }
      else if(areaFillGroups.length > 0) {
         // Area chart — raise SVG above canvas overlay and enable pointer-events on the SVG
         // element so the mousemove proximity handler can receive raw mouse coordinates.
         this.element.nativeElement.style.zIndex = "1";
         this.setupAreaHover(areaFillGroups);
      }
      else {
         // Non-radar, non-area chart — reset in case SVG was replaced after one of those.
         this.element.nativeElement.style.zIndex = "";
      }

      // Build relation connectivity maps so activateKey can highlight connected edges/neighbors.
      const relationNodes = Array.from(
         this.element.nativeElement.querySelectorAll(".inetsoft-relation") as NodeListOf<Element>);
      for(const n of relationNodes) {
         const nodeId = n.getAttribute("data-id");
         if(nodeId) this.relationNodeIdMap.set(nodeId, n);
      }
      const relationEdgeEls = Array.from(
         this.element.nativeElement.querySelectorAll(".inetsoft-relation-edge") as NodeListOf<Element>);
      for(const e of relationEdgeEls) {
         const src = e.getAttribute("data-source");
         const tgt = e.getAttribute("data-target");
         if(src && tgt) this.relationEdges.push({el: e, sourceId: src, targetId: tgt});
      }

      // Build label map from server-annotated label elements for all chart types that have
      // external text groups matched to data elements (bar, treemap/sunburst/icicle, mekko).
      for(const labelClass of [".inetsoft-bar-label", ".inetsoft-treemap-label", ".inetsoft-mekko-label", ".inetsoft-relation-label"]) {
         const labels = Array.from(
            this.element.nativeElement.querySelectorAll(labelClass) as NodeListOf<Element>);

         for(const label of labels) {
            const row = label.getAttribute("data-row");
            const col = label.getAttribute("data-col");

            if(row != null && col != null) {
               const k = `${row}-${col}`;
               const arr = this.labelGroupMap.get(k);
               if(arr) arr.push(label);
               else this.labelGroupMap.set(k, [label]);
            }
         }
      }
   }

   /**
    * Build area-hover structures and attach mousemove/mouseleave to the SVG element.
    * Called when .inetsoft-area annotation groups are present (area charts only).
    *
    * Area fill and line annotation groups are emitted in parallel DOM order by the server
    * (same series index = same position in both NodeLists). Pairing by index handles faceted
    * charts correctly — the same color appears once per panel, but each pair is a distinct
    * (panel, series) entry in areaSeries so all panels respond to hover independently.
    */
   private setupAreaHover(areaFillGroups: Element[]): void {
      const lineAnnotGroups = Array.from(
         this.element.nativeElement.querySelectorAll(".inetsoft-line") as NodeListOf<Element>);

      if(areaFillGroups.length !== lineAnnotGroups.length) {
         console.warn(`[ChartInlineSvgDirective] area fill/line group count mismatch: ` +
            `${areaFillGroups.length} fill, ${lineAnnotGroups.length} line — hover pairing may be wrong`);
      }

      // Pair each area fill group with its matching line group by parallel index.
      // Both lists have one entry per (panel × series); the i-th entry in each list
      // is always the same series in the same panel.
      for(let i = 0; i < areaFillGroups.length; i++) {
         const fillGroup = areaFillGroups[i];
         if(i >= lineAnnotGroups.length) break;
         const lineGroup = lineAnnotGroups[i];
         const path = lineGroup.querySelector("path");
         if(path instanceof SVGGeometryElement) {
            this.areaSeries.push({fillGroup, lineGroup, linePath: path});
         }
      }

      if(this.areaSeries.length === 0) return;

      // Pre-sample each line path in SVG local coordinates so mousemove hit-testing can avoid
      // repeated getPointAtLength() calls. getPointAtLength is O(path-length) in all browsers;
      // at 60 fps with multiple series that compounds quickly. Pre-sampling once at load time
      // and doing a binary search on the cached array per mousemove is far cheaper.
      const SAMPLES = 100;
      this.areaSeriesCache = [];
      for(const s of this.areaSeries) {
         const pts: {localX: number, localY: number}[] = [];
         const total = s.linePath.getTotalLength();
         if(total > 0) {
            for(let j = 0; j <= SAMPLES; j++) {
               const lp = s.linePath.getPointAtLength(j / SAMPLES * total);
               pts.push({localX: lp.x, localY: lp.y});
            }
         }
         // Sort ascending by localX so the binary search in getScreenYAtX is correct for
         // non-monotone paths (step charts, RTL series, reversed axes).
         pts.sort((a, b) => a.localX - b.localX);
         this.areaSeriesCache.push(pts);
      }

      const svgEl = this.element.nativeElement.querySelector("svg") as SVGSVGElement | null;
      if(!svgEl) return;

      // Allow the SVG to receive its own mouse events (parent container has pointer-events:none
      // from host SCSS; setting pointer-events on the SVG element overrides that for the SVG).
      svgEl.style.pointerEvents = "all";

      this.areaHoverSvgEl = svgEl;
      this.areaMouseMoveHandler = (e: MouseEvent) => this.onAreaMouseMove(e);
      this.areaMouseLeaveHandler = () => this.deactivateArea();
      svgEl.addEventListener("mousemove", this.areaMouseMoveHandler);
      svgEl.addEventListener("mouseleave", this.areaMouseLeaveHandler);
   }

   private teardownAreaHover(): void {
      if(this.areaHoverSvgEl) {
         if(this.areaMouseMoveHandler) {
            this.areaHoverSvgEl.removeEventListener("mousemove", this.areaMouseMoveHandler);
         }
         if(this.areaMouseLeaveHandler) {
            this.areaHoverSvgEl.removeEventListener("mouseleave", this.areaMouseLeaveHandler);
         }
         this.areaHoverSvgEl.style.pointerEvents = "";
         this.areaHoverSvgEl = null;
      }
      this.areaMouseMoveHandler = null;
      this.areaMouseLeaveHandler = null;
      this.areaSeriesCache = [];
   }

   private onAreaMouseMove(e: MouseEvent): void {
      // Collect screen y for every series. getScreenYAtX returns NaN when the mouse x is
      // outside that path's x-range (> 20px beyond path endpoints) — cross-panel paths
      // always return NaN so they can never be activated or receive a dim style.
      const yValues: (number | null)[] = [];

      for(let i = 0; i < this.areaSeries.length; i++) {
         const y = this.getScreenYAtX(i, e.clientX);
         yValues.push(isNaN(y) ? null : y);
      }

      // Ceiling algorithm: the fill band between line A (above) and line B (below) belongs to A.
      // "Above the mouse" = lineY < mouseY (smaller screen y = higher on screen).
      // Among all lines above the mouse, pick the one with the LARGEST lineY (the ceiling —
      // the line immediately above the cursor). This correctly maps the cursor into its fill band.
      // If no line is above the mouse (cursor is above every series or between panels), nearestIdx
      // stays -1 and deactivateArea() clears all highlighting — intentional reset.
      let nearestIdx = -1;
      let ceilingY   = -Infinity; // largest lineY that is still < mouseY

      for(let i = 0; i < this.areaSeries.length; i++) {
         const y = yValues[i];
         if(y === null) continue;
         if(y < e.clientY && y > ceilingY) { ceilingY = y; nearestIdx = i; }
      }

      if(nearestIdx !== this.activeSeriesIdx) {
         this.deactivateArea();
         this.activeSeriesIdx = nearestIdx;

         if(nearestIdx >= 0) {
            // Dim only same-panel series (those with a valid y at this mouse x).
            // Cross-panel series (yValues[i] === null) keep full opacity — no contamination.
            for(let i = 0; i < this.areaSeries.length; i++) {
               if(i === nearestIdx || yValues[i] === null) continue;
               (this.areaSeries[i].fillGroup as HTMLElement).style.opacity = "0.2";
               (this.areaSeries[i].lineGroup as HTMLElement).style.opacity = "0.2";
            }
         }
      }
   }

   private deactivateArea(): void {
      if(this.activeSeriesIdx >= 0) {
         for(const s of this.areaSeries) {
            (s.fillGroup as HTMLElement).style.opacity = "";
            (s.lineGroup as HTMLElement).style.opacity = "";
         }
         this.activeSeriesIdx = -1;
      }
   }

   /**
    * Return the screen y for the given series at the given screen x, using the pre-sampled
    * local-coordinate cache built in setupAreaHover.
    *
    * Approach: convert targetScreenX to SVG local x via the inverse CTM (one cheap matrix
    * multiply), binary-search the cached sample array by local x, linearly interpolate local y,
    * then convert back to screen y with one forward matrixTransform.  This replaces the previous
    * 25-iteration getPointAtLength bisection (expensive — O(path-length) per call in all engines).
    *
    * Returns NaN when the mouse x falls outside this path's sampled x-range: the nearest
    * endpoint is mapped back to screen x via the CTM; if that screen x is >20px from
    * targetScreenX the path is in a different facet panel.  The round-trip approach is used
    * because converting targetLocalX back to screen x via the CTM always yields exactly
    * targetScreenX (a mathematical identity of the CTM/inverse pair), so a direct comparison
    * at the extrapolated point would never detect an out-of-range position.
    */
   private getScreenYAtX(seriesIdx: number, targetScreenX: number): number {
      const pts = this.areaSeriesCache[seriesIdx];
      const s   = this.areaSeries[seriesIdx];
      if(!pts || pts.length === 0 || !s) return NaN;

      const ctm = s.linePath.getScreenCTM();
      if(!ctm) return NaN;

      const svgEl = s.linePath.ownerSVGElement;
      if(!svgEl) return NaN;
      const pt = svgEl.createSVGPoint();

      // Convert targetScreenX to SVG local x via the inverse CTM.
      let invCtm: DOMMatrix;
      try { invCtm = ctm.inverse(); }
      catch { return NaN; }
      pt.x = targetScreenX; pt.y = 0;
      const targetLocalX = pt.matrixTransform(invCtm).x;

      // Guard: if targetLocalX is outside this path's sampled x-range, map the nearest
      // endpoint back to screen x. Cross-panel paths are 60+ px away so they return NaN.
      const minX = pts[0].localX, maxX = pts[pts.length - 1].localX;
      if(targetLocalX < minX || targetLocalX > maxX) {
         pt.x = targetLocalX < minX ? minX : maxX;
         pt.y = 0;
         if(Math.abs(pt.matrixTransform(ctm).x - targetScreenX) > 20) return NaN;
      }

      // Binary-search the pre-sampled array by local x (sorted ascending by setupAreaHover).
      let lo = 0, hi = pts.length - 1;
      while(lo < hi - 1) {
         const mid = (lo + hi) >> 1;
         if(pts[mid].localX < targetLocalX) lo = mid;
         else hi = mid;
      }

      // Linearly interpolate y between the two nearest cached samples.
      const p0 = pts[lo], p1 = pts[hi];
      const dx  = p1.localX - p0.localX;
      const t   = dx === 0 ? 0 : (targetLocalX - p0.localX) / dx;
      const localY = p0.localY + t * (p1.localY - p0.localY);

      pt.x = targetLocalX; pt.y = localY;
      return pt.matrixTransform(ctm).y;
   }

   /**
    * Rewrites all id="..." attributes and their references (url(#...), href="#...") with a
    * per-instance unique prefix. Prevents ID conflicts when multiple SVGs are inlined in the
    * same HTML document (Batik generates non-unique IDs like clipPath1, clipPath2, etc.).
    */
   private uniquifyIds(svg: string): string {
      const uid = `isvg${ChartInlineSvgDirective.idCounter++}`;
      return svg
         .replace(/(?<![a-zA-Z0-9_-])id="([^"]+)"/g, `id="${uid}-$1"`)
         .replace(/\burl\(#([^)]+)\)/g, `url(#${uid}-$1)`)
         .replace(/\bhref="#([^"]+)"/g, `href="#${uid}-$1"`)
         // Batik 1.17 emits xlink:href="#id" for <use> elements (marker/symbol references).
         // The colon prevents \bhref= from matching, so this must be a separate replace.
         .replace(/\bxlink:href="#([^"]+)"/g, `xlink:href="#${uid}-$1"`);
   }
}
