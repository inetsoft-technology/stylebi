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

   /** Called after new SVG content is injected. Builds element and label lookup maps. */
   private afterSvgInjected(): void {
      if(this.clearHandle !== null) {
         clearTimeout(this.clearHandle);
         this.clearHandle = null;
      }

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

      if(radarGroups.length > 0) {
         this.elementGroupMap.clear();

         // Raise the SVG container above the canvas overlay so CSS :hover fires on polygon
         // interior areas. pointer-events:none on the container is kept (set in the host
         // component's SCSS); events outside polygon hit areas pass through to the canvas.
         this.element.nativeElement.style.zIndex = "1";
      }
      else {
         // Non-radar chart — reset in case SVG was replaced after a radar chart.
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
