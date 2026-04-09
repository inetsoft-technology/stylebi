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

   /** Maps "rowIdx-colIdx" to the corresponding {@code <g class="inetsoft-bar">} element. */
   private barGroupMap = new Map<string, Element>();
   /** Maps "rowIdx-colIdx" to the value label group paired with that bar. */
   private barLabelMap = new Map<string, Element>();
   /** Key of the currently active bar, or null. */
   private _activeKey: string | null = null;
   /** Timer handle for debounced deactivation, so fast inter-bar moves don't flash. */
   private clearHandle: ReturnType<typeof setTimeout> | null = null;
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
                  setTimeout(() => this.loadSvg(true), interval);
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
         this.barGroupMap.clear();
         this.barLabelMap.clear();
         this._activeKey = null;
      }
   }

   /**
    * Highlight the bar at rowIdx+colIdx by toggling the "inetsoft-active" CSS class.
    * The server-injected :has() rule dims all other bars/labels automatically.
    * Pass null to clear the highlight. Clearing is debounced so rapid inter-bar moves
    * don't cause a flash.
    */
   highlightBar(rowIdx: number | null, colIdx: number | null): void {
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
      const bar = this.barGroupMap.get(key);
      if(bar) bar.classList.add("inetsoft-active");
      const label = this.barLabelMap.get(key);
      if(label) label.classList.add("inetsoft-active");
   }

   private deactivateCurrent(): void {
      if(this._activeKey === null) return;
      const bar = this.barGroupMap.get(this._activeKey);
      if(bar) bar.classList.remove("inetsoft-active");
      const label = this.barLabelMap.get(this._activeKey);
      if(label) label.classList.remove("inetsoft-active");
   }

   /** Called after new SVG content is injected. Builds bar/label lookup maps. */
   private afterSvgInjected(): void {
      if(this.clearHandle !== null) {
         clearTimeout(this.clearHandle);
         this.clearHandle = null;
      }

      this.barGroupMap.clear();
      this.barLabelMap.clear();
      this._activeKey = null;

      // Build bar map from server-annotated inetsoft-bar elements.
      const bars = Array.from(
         this.element.nativeElement.querySelectorAll(".inetsoft-bar") as NodeListOf<Element>);

      for(const bar of bars) {
         const row = bar.getAttribute("data-row");
         const col = bar.getAttribute("data-col");
         if(row != null && col != null) {
            this.barGroupMap.set(`${row}-${col}`, bar);
         }
      }

      // Build label map from server-annotated inetsoft-bar-label elements.
      const labels = Array.from(
         this.element.nativeElement.querySelectorAll(".inetsoft-bar-label") as NodeListOf<Element>);

      for(const label of labels) {
         const row = label.getAttribute("data-row");
         const col = label.getAttribute("data-col");
         if(row != null && col != null) {
            this.barLabelMap.set(`${row}-${col}`, label);
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
         .replace(/\bhref="#([^"]+)"/g, `href="#${uid}-$1"`);
   }
}
