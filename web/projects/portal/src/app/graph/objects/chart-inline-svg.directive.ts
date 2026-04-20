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
   /** Maps "rowIdx-colIdx" to the external text label group paired with that data element. */
   private labelGroupMap = new Map<string, Element>();
   /** Key of the currently active element, or null. */
   private _activeKey: string | null = null;
   /** Timer handle for debounced deactivation, so fast inter-bar moves don't flash. */
   private clearHandle: ReturnType<typeof setTimeout> | null = null;
   /** Timer handle for Retry-After retry, stored so it can be cancelled on destroy. */
   private retryHandle: ReturnType<typeof setTimeout> | null = null;
   /** Timer handle for adding .ready class to the SVG after animation completes. */
   private readyHandle: ReturnType<typeof setTimeout> | null = null;
   /** Ordered list of area series objects; one entry per (panel, series) pair. */
   private areaSeries: Array<{fillGroup: Element, lineGroup: Element, linePath: SVGGeometryElement}> = [];
   /** Index into areaSeries of the currently highlighted series, or -1. */
   private activeSeriesIdx: number = -1;
   /** SVG element that holds area mousemove/mouseleave listeners, kept for cleanup. */
   private areaHoverSvgEl: Element | null = null;
   private areaMouseMoveHandler: ((e: MouseEvent) => void) | null = null;
   private areaMouseLeaveHandler: (() => void) | null = null;
   private static readonly CLEAR_DELAY_MS = 120;
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
      if(el) el.classList.add("inetsoft-active");
      const label = this.labelGroupMap.get(key);
      if(label) label.classList.add("inetsoft-active");
   }

   private deactivateCurrent(): void {
      if(this._activeKey === null) return;
      const el = this.elementGroupMap.get(this._activeKey);
      if(el) el.classList.remove("inetsoft-active");
      const label = this.labelGroupMap.get(this._activeKey);
      if(label) label.classList.remove("inetsoft-active");
   }

   /**
    * Schedule adding the {@code .ready} class to the SVG root element shortly after load.
    * The {@code .ready} class gates hover CSS rules for A1 chart types (point, candle, box,
    * treemap, mekko, radar) — types where the animated group is also the hover target, so
    * setting opacity via hover during fill-mode can conflict. Bar hover is never gated.
    *
    * The gate fires {@code DURATION + READY_BUFFER = 900ms} after SVG injection — long enough
    * for the first animated element to complete, short enough not to frustrate users. If the
    * Java injector did not set {@code data-last-delay} (no animation), {@code .ready} is added
    * immediately.
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

      const hasAnimation = svgEl.hasAttribute("data-last-delay");

      if(!hasAnimation) {
         // No animation injected — add .ready immediately so A1 hover works.
         svgEl.classList.add("ready");
         return;
      }

      // Gate A1 hover rules for DURATION + READY_BUFFER = 900ms after the SVG loads.
      // This is long enough for the first animated element to complete, preventing
      // fill-mode conflicts on the first hover attempt, without locking out hover for
      // the full stagger window (which can be 2+ seconds on busy charts).
      const readyMs = (0.8 + 0.1) * 1000;

      this.readyHandle = setTimeout(() => {
         this.readyHandle = null;
         svgEl.classList.add("ready");
      }, readyMs);
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
      this._activeKey = null;

      // Populate the unified element map from all annotated VO groups.
      // Each CSS class corresponds to a different chart type; the CSS class on the stored
      // element determines which server-injected hover rule fires on inetsoft-active toggle.
      for(const cssClass of [".inetsoft-bar", ".inetsoft-point", ".inetsoft-candle", ".inetsoft-box", ".inetsoft-radar", ".inetsoft-treemap", ".inetsoft-mekko"]) {
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

      // Build label map from server-annotated label elements for all chart types that have
      // external text groups matched to data elements (bar, treemap/sunburst/icicle, mekko).
      for(const labelClass of [".inetsoft-bar-label", ".inetsoft-treemap-label", ".inetsoft-mekko-label"]) {
         const labels = Array.from(
            this.element.nativeElement.querySelectorAll(labelClass) as NodeListOf<Element>);

         for(const label of labels) {
            const row = label.getAttribute("data-row");
            const col = label.getAttribute("data-col");

            if(row != null && col != null) {
               this.labelGroupMap.set(`${row}-${col}`, label);
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

      // Pair each area fill group with its matching line group by parallel index.
      // Both lists have one entry per (panel × series); the i-th entry in each list
      // is always the same series in the same panel.
      for(let i = 0; i < areaFillGroups.length; i++) {
         const fillGroup = areaFillGroups[i];
         if(i >= lineAnnotGroups.length) break;
         const lineGroup = lineAnnotGroups[i];
         const path = lineGroup.querySelector("path");
         if(path instanceof SVGGeometryElement && path.getTotalLength) {
            this.areaSeries.push({fillGroup, lineGroup, linePath: path as SVGGeometryElement});
         }
      }

      if(this.areaSeries.length === 0) return;

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
         (this.areaHoverSvgEl as HTMLElement).style.pointerEvents = "";
         this.areaHoverSvgEl = null;
      }
      this.areaMouseMoveHandler = null;
      this.areaMouseLeaveHandler = null;
   }

   private onAreaMouseMove(e: MouseEvent): void {
      // Collect screen y for every series. getScreenYAtX returns NaN when the mouse x is
      // outside that path's x-range (> 20px beyond path endpoints) — cross-panel paths
      // always return NaN so they can never be activated or receive a dim style.
      const yValues: (number | null)[] = [];

      for(let i = 0; i < this.areaSeries.length; i++) {
         const y = this.getScreenYAtX(this.areaSeries[i].linePath, e.clientX);
         yValues.push(isNaN(y) ? null : y);
      }

      // Ceiling algorithm: the fill band between line A (above) and line B (below) belongs to A.
      // "Above the mouse" = lineY < mouseY (smaller screen y = higher on screen).
      // Among all lines above the mouse, pick the one with the LARGEST lineY (the ceiling —
      // the line immediately above the cursor). This correctly maps the mouse into its fill band.
      // If no line is above the mouse (cursor is above every series), fall back to the topmost
      // series so the area above the top line still activates the right series.
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
    * Binary-search the SVG path for the point whose screen x ≈ targetScreenX, then return
    * its screen y. Works correctly for monotonically left-to-right paths (typical line series).
    *
    * Returns NaN if the converged screen x is more than 20px from targetScreenX. This occurs
    * when the target x is outside the path's x-range (e.g. a different facet panel), so the
    * search clamps to the nearest endpoint. The 20px guard is well below the typical
    * inter-panel gap (~60px), reliably preventing cross-panel series from becoming candidates.
    */
   private getScreenYAtX(path: SVGGeometryElement, targetScreenX: number): number {
      const total = path.getTotalLength();
      const ctm = path.getScreenCTM();
      if(!ctm || total === 0) return NaN;

      const svgEl = path.ownerSVGElement;
      if(!svgEl) return NaN;
      const pt = svgEl.createSVGPoint();

      let lo = 0, hi = total;
      for(let i = 0; i < 25; i++) {
         const mid = (lo + hi) * 0.5;
         const lp = path.getPointAtLength(mid);
         pt.x = lp.x; pt.y = lp.y;
         if(pt.matrixTransform(ctm).x < targetScreenX) lo = mid;
         else hi = mid;
      }

      const lp = path.getPointAtLength((lo + hi) * 0.5);
      pt.x = lp.x; pt.y = lp.y;
      const sp = pt.matrixTransform(ctm);
      if(Math.abs(sp.x - targetScreenX) > 20) return NaN;
      return sp.y;
   }

   /**
    * Rewrites all id="..." attributes and their references (url(#...), href="#...") with a
    * per-instance unique prefix. Prevents ID conflicts when multiple SVGs are inlined in the
    * same HTML document (Batik generates non-unique IDs like clipPath1, clipPath2, etc.).
    */
   private uniquifyIds(svg: string): string {
      const uid = `isvg${ChartInlineSvgDirective.idCounter++}`;
      return svg
         .replace(/\bid="([^"]+)"/g, `id="${uid}-$1"`)
         .replace(/\burl\(#([^)]+)\)/g, `url(#${uid}-$1)`)
         .replace(/\bhref="#([^"]+)"/g, `href="#${uid}-$1"`)
         // Batik 1.17 emits xlink:href="#id" for <use> elements (marker/symbol references).
         // The colon prevents \bhref= from matching, so this must be a separate replace.
         .replace(/\bxlink:href="#([^"]+)"/g, `xlink:href="#${uid}-$1"`);
   }
}
