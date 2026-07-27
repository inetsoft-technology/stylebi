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
 * Call highlightElement(row, col) / highlightElements(pairs) from the canvas overlay's
 * mouse-event handler; pass null / an empty array to clear.
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
   /** Emits the data-color of the hovered area/line series (null on clear) so the parent can
    *  mirror the dim across sibling tiles of a split chart. Only emitted in cross-tile mode. */
   @Output() seriesDimChange = new EventEmitter<string | null>();
   /** Emits the data-id of the hovered relation/tree node (null on clear). The parent resolves
    *  neighbours from the merged cross-tile graph and drives every tile, since a node's neighbours
    *  may be rendered in sibling tiles this tile's connectivity maps can't see. Cross-tile only. */
   @Output() relationHover = new EventEmitter<string | null>();
   /** True when this chart is split into multiple SVG tiles. In that mode area/line hover only
    *  detects the active series and emits its color; opacity is applied solely by the parent via
    *  setExternalSeriesDim so every tile dims consistently (geometry-based local dim can't reach
    *  sibling SVGs). */
   @Input() crossTile = false;
   /** Snap-to-nearest tooltip active. On line charts the snapped series drives the dim via
    *  highlightSnapSeries, so onAreaMouseMove suppresses its own dim to avoid fighting it. Area
    *  charts ignore it — their cursor-band hover already resolves a series. */
   @Input() snapTooltip = false;
   private _url: string = null;
   /** Last color emitted via seriesDimChange, to suppress duplicate emits. */
   private _emittedSeriesColor: string | null = null;
   /** Last node id emitted via relationHover, to suppress duplicate emits. */
   private _emittedRelationId: string | null = null;
   /** True when this tile holds relation/tree nodes; gates the cross-tile relation path. */
   private isRelationChart = false;

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
   /** Keys of the currently active elements; one for a plain hover, many for a stacked bar
    *  column (every segment activated together so the whole stack stays undimmed). */
   private _activeKeys: string[] = [];
   /** True when _activeKeys resolved to at least one element in elementGroupMap. On a mixed tile the
    *  canvas also reports the line measure's regions, whose keys are absent from the map; those must
    *  not count as a class hover or they would lock out the series proximity hover. */
   private _activeFound = false;
   /** Cached SVG root of this tile, used to toggle the cross-tile inetsoft-dim-all class. */
   private svgRootEl: SVGSVGElement | null = null;
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
   /** All treemap/circle-packing/sunburst/icicle node groups, used to find a hovered container's
    *  descendants (matched by data-subrow against the container's data-childrows). */
   private treemapGroups: Element[] = [];
   /** Treemap descendant nodes + labels activated for the current hover (cleared on deactivation). */
   private activeTreemapDescendants: Element[] = [];
   /** Ordered list of hover series; one entry per (panel, series) pair. fillGroup is null on a pure
    *  line chart (no area fills); points is empty on an area chart, whose markers are not part of
    *  the cursor-band hover. */
   private areaSeries: Array<{fillGroup: Element | null, lineGroup: Element,
                              linePath: SVGGeometryElement, points: Element[]}> = [];
   /** Point markers that matched no series (a color/col value no line group carries). They dim
    *  whenever any series is active, so a stray marker cannot stay bright on its own. */
   private hoverDimOnlyElements: Element[] = [];
   /** How a mousemove resolves to a series: "ceiling" for area charts (the cursor belongs to the
    *  fill band under the line immediately above it), "nearest" for line charts (the series whose
    *  line is closest to the cursor). Lines need "nearest" because they have no band to fall into,
    *  and closely spaced series cannot be separated by element hit-testing — overlapping hit areas
    *  are always awarded to whichever element comes last in DOM order. */
   private seriesHitMode: "ceiling" | "nearest" = "ceiling";
   /** Pre-sampled SVG local-coordinate points per series for fast mousemove hit-testing. */
   private areaSeriesCache: Array<{localX: number, localY: number}[]> = [];
   /** Index into areaSeries of the currently highlighted series, or -1. */
   private activeSeriesIdx: number = -1;
   /** True for area/line/radar charts, which dim by series key (setExternalSeriesDim) rather than
    *  the inetsoft-active/inetsoft-dim-all class mechanism. Prevents the class-based cross-tile
    *  dim from fighting the key-based one over shared .inetsoft-point markers. */
   private usesSeriesColorDim = false;
   /** True when this tile mixes both hover mechanisms: line/area series (cursor-resolved, dimmed by
    *  inline opacity) alongside class-hover VOs such as bars (dimmed by the server :has() rule on
    *  inetsoft-active). A multi-style chart binds a different chart type per measure, so one SVG can
    *  hold e.g. bars and a line. Both mechanisms stay live and dim each other. */
   private mixedHover = false;
   /** Class-hover groups (and their labels) present in a mixed tile, dimmed inline while a line/area
    *  series owns the highlight. Empty unless mixedHover. */
   private mixedClassHoverElements: Element[] = [];
   /** True while activateKeys holds every series dimmed for a mixed tile's class hover. Gates the
    *  release in deactivateCurrent so it cannot wipe a dim the proximity hover owns instead. */
   private mixedSeriesDimmed = false;
   /** Attribute identifying a series for cross-tile dim: data-color for area/line, data-row for
    *  radar. The value is stable across a split chart's SVG tiles. */
   private dimKeyAttr = "data-color";
   /** Selector for the elements setExternalSeriesDim dims (per chart type). */
   private dimTargetSelector = ".inetsoft-area,.inetsoft-line,.inetsoft-point";
   /** SVG element that holds area mousemove/mouseleave listeners, kept for cleanup. */
   private areaHoverSvgEl: SVGSVGElement | null = null;
   private areaMouseMoveHandler: ((e: MouseEvent) => void) | null = null;
   private areaMouseLeaveHandler: (() => void) | null = null;
   /** AbortController that cancels the mouseenter/mouseleave listeners added by
    *  setupRadarSeriesHover (cross-tile radar). Line and area hover use the SVG-level
    *  mousemove listeners tracked by areaHoverSvgEl instead. */
   private lineSeriesAbortController: AbortController | null = null;
   /** Debounce timer for clearing the cross-tile radar series dim. */
   private lineSeriesClearTimer: ReturnType<typeof setTimeout> | null = null;
   /** True for pure line charts (setupLineHover ran). Gates the snap-driven series dim so it
    *  applies only to line charts; area/radar tiles keep their own hover mechanism. */
   private isLineSeriesHover = false;
   /** Maps "rowIdx-colIdx" → data-color for point markers, so a snapped data point resolves to its
    *  series color for the snap-driven dim. Line groups carry data-color but not row/col. */
   private seriesColorByKey = new Map<string, string>();
   /** Maps colIdx → data-color, a fallback for a snapped point with no exact row-col match. */
   private seriesColorByCol = new Map<string, string>();
   /** data-color of the series currently undimmed by snap; suppresses redundant re-dims. */
   private _snapSeriesColor: string | null = null;
   /** Debounce timer for clearing the snap-driven series dim. */
   private snapClearTimer: ReturnType<typeof setTimeout> | null = null;
   private static readonly CLEAR_DELAY_MS = 120;
   /** Max distance (px) from a line to the cursor for the "nearest" hit mode to select it. Beyond
    *  this the highlight clears, so empty plot area does not hold the last series lit. */
   private static readonly NEAREST_MAX_DIST_PX = 50;
   /** Milliseconds after SVG load before the .ready class is added (gates A1 hover CSS). */
   private static readonly READY_MS = 900;
   /** Annotation classes whose hover is driven by toggling inetsoft-active, in the order they
    *  populate elementGroupMap (a later class wins a row-col collision). */
   private static readonly HOVER_CLASSES = [".inetsoft-bar", ".inetsoft-point", ".inetsoft-candle",
      ".inetsoft-box", ".inetsoft-radar", ".inetsoft-treemap", ".inetsoft-mekko",
      ".inetsoft-relation"];
   /** Label classes paired with the HOVER_CLASSES groups by data-row/data-col. */
   private static readonly HOVER_LABEL_CLASSES = [".inetsoft-bar-label", ".inetsoft-point-label",
      ".inetsoft-treemap-label", ".inetsoft-mekko-label", ".inetsoft-relation-label"];
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

      if(this.snapClearTimer !== null) {
         clearTimeout(this.snapClearTimer);
         this.snapClearTimer = null;
      }

      this.teardownAreaHover();
      this.teardownLineSeriesHover();
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
         this._activeKeys = [];
         this._activeFound = false;
      }
   }

   /**
    * Highlight a single data element by rowIdx+colIdx. Thin wrapper over
    * {@link highlightElements}; pass null to clear.
    */
   highlightElement(rowIdx: number | null, colIdx: number | null): void {
      this.highlightElements(rowIdx != null ? [{ row: rowIdx, col: colIdx }] : []);
   }

   /**
    * Highlight one or more data elements by toggling the "inetsoft-active" CSS class on each.
    * The server-injected :has() rule dims every other bar/label automatically. A stacked bar
    * column passes one pair per segment so the whole stack stays undimmed; a plain hover passes
    * a single pair. Pass an empty array to clear. Clearing is debounced so rapid inter-bar
    * moves don't cause a flash.
    */
   highlightElements(pairs: { row: number, col: number }[]): void {
      if(pairs.length > 0) {
         if(this.clearHandle !== null) {
            clearTimeout(this.clearHandle);
            this.clearHandle = null;
         }

         const keys = pairs.map(p => `${p.row}-${p.col}`);

         // Area/line/radar tiles dim by series color (setExternalSeriesDim), not the
         // inetsoft-active class; toggling active on their shared point markers would fight
         // that, so honor only the primary when a multi-element (stacked) set arrives on such
         // a tile. Trim here so _activeKeys is written once and sameActiveKeys compares the
         // value that was actually applied. A mixed tile is exempt: its keys resolve to bars (or
         // other class-hover VOs), never to the series' own markers, so every segment of a stacked
         // column must stay active.
         const activeKeys = this.usesSeriesColorDim && !this.mixedHover && keys.length > 1
            ? keys.slice(0, 1) : keys;

         if(!this.sameActiveKeys(activeKeys)) {
            this.deactivateCurrent();
            this._activeKeys = activeKeys;
            this.activateKeys(activeKeys);
         }
      }
      else {
         if(this._activeKeys.length > 0 && this.clearHandle === null) {
            this.clearHandle = setTimeout(() => {
               this.clearHandle = null;
               this.deactivateCurrent();
               this._activeKeys = [];
            }, ChartInlineSvgDirective.CLEAR_DELAY_MS);
         }
      }
   }

   /**
    * Drive the per-series dim from a snapped data point so the hovered series stays undimmed
    * regardless of cursor Y. Line charts only: geometry hover fires only when the cursor is exactly
    * on a thin line/point, which rarely lines up with the snap guideline. No-op on area (keeps its
    * cursor-band hover) and bar/radar (don't dim by series color). Cross-tile mode emits the color
    * so the parent mirrors the dim onto sibling tiles holding the rest of the series. Pass an empty
    * array to clear; clearing is debounced so moving between snap columns of one series doesn't flash.
    */
   highlightSnapSeries(pairs: { row: number, col: number }[]): void {
      if(!this.isLineSeriesHover) {
         return;
      }

      if(pairs.length === 0) {
         if(this._snapSeriesColor !== null && this.snapClearTimer === null) {
            this.snapClearTimer = setTimeout(() => {
               this.snapClearTimer = null;
               this._snapSeriesColor = null;

               if(this.crossTile) {
                  this.emitSeriesDim(null);
               }
               else {
                  this.setExternalSeriesDim(null);
                  this.setMixedClassHoverDim(false);
               }
            }, ChartInlineSvgDirective.CLEAR_DELAY_MS);
         }

         return;
      }

      if(this.snapClearTimer !== null) {
         clearTimeout(this.snapClearTimer);
         this.snapClearTimer = null;
      }

      const color = this.resolveSnapSeriesColor(pairs);

      // Only the tile holding the snapped point resolves a color; a tile that can't leaves its dim
      // untouched rather than clearing it, which would fight the resolving tile in cross-tile mode.
      // Not stranding a dim on a single tile: one that dims at all resolves every column (markers
      // fill seriesColorByKey, or multi-measure lines fill seriesColorByCol), and a marker-less
      // single-measure chart resolves to null on every column, so it never sets a color to begin with.
      if(color == null || color === this._snapSeriesColor) {
         return;
      }

      this._snapSeriesColor = color;

      if(this.crossTile) {
         this.emitSeriesDim(color);
      }
      else {
         this.setExternalSeriesDim(color);
         // Multi-style chart: under snap the guideline drives the series highlight and
         // onAreaMouseMove stands down, so this is the only place that can fade the bars drawn
         // beside the snapped series. setExternalSeriesDim reaches series elements only.
         this.setMixedClassHoverDim(true);
      }
   }

   /**
    * Resolve a snapped point to its series data-color: exact row-col first, then col. The col
    * fallback is gated on map size > 1 (multi-measure); in a single-measure stacked chart every
    * point shares one col, so it can't distinguish series and would dim to an arbitrary color.
    */
   private resolveSnapSeriesColor(pairs: { row: number, col: number }[]): string | null {
      for(const p of pairs) {
         const c = this.seriesColorByKey.get(`${p.row}-${p.col}`);
         if(c != null) return c;
      }

      if(this.seriesColorByCol.size > 1) {
         for(const p of pairs) {
            const c = this.seriesColorByCol.get(String(p.col));
            if(c != null) return c;
         }
      }

      return null;
   }

   /** True when keys match the current active set, order-independent. */
   private sameActiveKeys(keys: string[]): boolean {
      if(keys.length !== this._activeKeys.length) {
         return false;
      }

      const active = new Set(this._activeKeys);
      return keys.every(k => active.has(k));
   }

   private activateKeys(keys: string[]): void {
      let anyFound = false;

      for(const key of keys) {
         const el = this.elementGroupMap.get(key);

         if(el) {
            anyFound = true;
            // Relation in a split chart: the hovered node's neighbours may live in sibling tiles
            // this tile's connectivity maps can't see. Emit the node id and let the parent resolve
            // neighbours from the merged graph and drive every tile via setExternalRelationHighlight.
            // (Relation hover is always single-key, so returning here is safe.)
            if(this.isRelationChart && this.crossTile) {
               this.emitRelationHover(el.getAttribute("data-id"));
               return;
            }
            el.classList.add("inetsoft-active");
            if(el.classList.contains("inetsoft-relation")) {
               this.activateRelationNeighbors(el);
            }
            else if(el.classList.contains("inetsoft-treemap")) {
               this.activateTreemapDescendants(el);
            }
         }

         const glyphs = this.labelGroupMap.get(key);
         if(glyphs) glyphs.forEach(g => g.classList.add("inetsoft-active"));
      }

      this._activeFound = anyFound;

      // Multi-style chart: a class-hover VO (e.g. a bar) took the highlight, so the line/area series
      // drawn beside it must fade with the other bars. The server :has() rule only reaches elements
      // of the same chart type, and the series carry no inetsoft-active class at all. Any series dim
      // left over from a preceding cursor hover is dropped first, so activeSeriesIdx is reset and a
      // later mousemove re-applies its own dim rather than deduping against a stale index.
      if(this.mixedHover && anyFound && !this.crossTile) {
         this.deactivateArea();
         // Explicit release as well: under snap the bars are dimmed by highlightSnapSeries with no
         // series active, so deactivateArea's activeSeriesIdx guard skips them. Snapping from the
         // line onto a bar column never calls highlightSnapSeries again (the plot area routes a
         // bar-like region to highlightElements), so nothing else would lift that dim. Dropping the
         // remembered snap color with it lets the same series re-dim the bars when the guideline
         // moves back onto it, which its unchanged-color early return would otherwise skip.
         this.setMixedClassHoverDim(false);
         this._snapSeriesColor = null;
         this.setAllSeriesDim(true);
         this.mixedSeriesDimmed = true;
      }

      // A large chart is split into multiple SVG tiles. The hovered data point lives in
      // only one tile, so its inetsoft-active class (and the server's :has() dim rule, which
      // is scoped to a single SVG) never reaches sibling tiles. When this tile holds
      // hover-managed elements but none of the active set, flag its root so server CSS dims them.
      // Skipped for area/line charts (they dim by series color) and cross-tile relation charts
      // (the parent drives per-tile highlight + dim via setExternalRelationHighlight). Mixed tiles
      // are covered by the series-color-dim skip too: there, !anyFound means the cursor is on the
      // line measure, which the proximity hover handles — dimming every bar here would instead
      // blank the plot for the whole time the cursor spends off the bars.
      if(!anyFound && this.elementGroupMap.size > 0 && this.svgRootEl && !this.usesSeriesColorDim &&
         !(this.isRelationChart && this.crossTile)) {
         this.svgRootEl.classList.add("inetsoft-dim-all");
      }
   }

   private deactivateCurrent(): void {
      this._activeFound = false;

      if(this._activeKeys.length === 0) return;
      // Cross-tile relation: the parent set inetsoft-active/dim-all across tiles, so clearing is
      // its job too — emit the clear and don't strip classes locally (that would fight the parent).
      if(this.isRelationChart && this.crossTile) {
         this.emitRelationHover(null);
         return;
      }
      if(this.svgRootEl) this.svgRootEl.classList.remove("inetsoft-dim-all");

      // Release the series dim applied by activateKeys on a mixed tile. deactivateArea() cannot do
      // it — activateKeys reset activeSeriesIdx to -1, which is its own no-op guard.
      if(this.mixedSeriesDimmed) {
         this.setAllSeriesDim(false);
         this.mixedSeriesDimmed = false;
      }

      for(const key of this._activeKeys) {
         const el = this.elementGroupMap.get(key);
         if(el) el.classList.remove("inetsoft-active");
         const glyphs = this.labelGroupMap.get(key);
         if(glyphs) glyphs.forEach(g => g.classList.remove("inetsoft-active"));
      }

      for(const n of this.activeRelationNeighbors) {
         n.classList.remove("inetsoft-active");
      }
      this.activeRelationNeighbors = [];

      for(const n of this.activeTreemapDescendants) {
         n.classList.remove("inetsoft-active");
      }
      this.activeTreemapDescendants = [];
   }

   // Activates neighbor nodes, their connecting edges, and neighbor labels.
   // The hovered node's own label is activated separately by activateKeys() via labelGroupMap,
   // and cleared by deactivateCurrent(). Neighbor labels pushed into activeRelationNeighbors
   // are cleared by the activeRelationNeighbors loop in deactivateCurrent().
   private activateRelationNeighbors(nodeEl: Element): void {
      const nodeId = nodeEl.getAttribute("data-id");
      if(!nodeId) return;
      this.activeRelationNeighbors = [];

      for(const edge of this.relationEdges) {
         if(edge.sourceId !== nodeId && edge.targetId !== nodeId) continue;

         edge.el.classList.add("inetsoft-active");
         this.activeRelationNeighbors.push(edge.el);

         const neighborId = edge.sourceId === nodeId ? edge.targetId : edge.sourceId;
         if(neighborId === nodeId) continue; // skip self-loops — hovered node already activated
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

   // Keep the whole subtree of a hovered treemap/circle-packing/sunburst/icicle container undimmed.
   // Each node is a separate sibling group, so hovering a container activates only itself and the
   // server :has() dim rule would dim its descendants along with unrelated nodes. data-childrows
   // lists the container's descendant leaf sub-rows; because an intermediate container shares its
   // data-subrow with its first leaf descendant, matching data-subrow against that set activates the
   // full subtree — leaves and intermediate containers alike. Descendant labels are activated via
   // labelGroupMap (keyed by the descendant's data-row/data-col, as tagged server-side).
   // Cleared by deactivateCurrent() via the activeTreemapDescendants array.
   private activateTreemapDescendants(containerEl: Element): void {
      const childRowsAttr = containerEl.getAttribute("data-childrows");
      if(!childRowsAttr) return; // leaf node — nothing nested to keep undimmed

      const childRows = new Set(childRowsAttr.split(","));
      // data-subrow is shared along a "first-descended" chain (root→leaf created in one pass share
      // one sub-row), so a container's subrow can also equal an ancestor's. Requiring a strictly
      // smaller data-level (leaf=0, root=highest) keeps only genuine descendants and excludes those
      // ancestors (and same-level siblings), which would otherwise stay undimmed.
      const containerLevel = Number(containerEl.getAttribute("data-level"));

      for(const node of this.treemapGroups) {
         if(node === containerEl) continue; // already activated by activateKeys

         const sub = node.getAttribute("data-subrow");
         const level = Number(node.getAttribute("data-level"));
         if(sub == null || Number.isNaN(level) || level >= containerLevel || !childRows.has(sub)) {
            continue;
         }

         node.classList.add("inetsoft-active");
         this.activeTreemapDescendants.push(node);

         const row = node.getAttribute("data-row");
         const col = node.getAttribute("data-col");
         if(row != null && col != null) {
            const glyphs = this.labelGroupMap.get(`${row}-${col}`);
            if(glyphs) {
               glyphs.forEach(g => {
                  g.classList.add("inetsoft-active");
                  this.activeTreemapDescendants.push(g);
               });
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
      this.teardownLineSeriesHover();
      this.areaSeries = [];
      this.hoverDimOnlyElements = [];
      this.activeSeriesIdx = -1;
      this.seriesHitMode = "ceiling";
      this.usesSeriesColorDim = false;
      this.mixedHover = false;
      this.mixedClassHoverElements = [];
      this.mixedSeriesDimmed = false;
      this._activeFound = false;
      this.isLineSeriesHover = false;
      this.dimKeyAttr = "data-color";
      this.dimTargetSelector = ".inetsoft-area,.inetsoft-line,.inetsoft-point";
      this._emittedSeriesColor = null;
      this.isRelationChart = false;
      this._emittedRelationId = null;
      this.seriesColorByKey.clear();
      this.seriesColorByCol.clear();
      this._snapSeriesColor = null;

      if(this.snapClearTimer !== null) {
         clearTimeout(this.snapClearTimer);
         this.snapClearTimer = null;
      }

      this.elementGroupMap.clear();
      this.labelGroupMap.clear();
      this.relationNodeIdMap.clear();
      this.relationEdges = [];
      this.activeRelationNeighbors = [];
      this.treemapGroups = [];
      this.activeTreemapDescendants = [];
      this._activeKeys = [];
      this.svgRootEl = this.element.nativeElement.querySelector("svg");

      // Populate the unified element map from all annotated VO groups.
      // Each CSS class corresponds to a different chart type; the CSS class on the stored
      // element determines which server-injected hover rule fires on inetsoft-active toggle.
      this.populateElementGroupMap(ChartInlineSvgDirective.HOVER_CLASSES);

      // Map each point marker's row/col → data-color for snap resolution. Built from .inetsoft-point,
      // the only annotation carrying row, col and color together (line/area groups omit row/col).
      const pointColorEls = Array.from(
         this.element.nativeElement.querySelectorAll(".inetsoft-point") as NodeListOf<Element>);

      for(const p of pointColorEls) {
         const color = p.getAttribute("data-color");

         if(color == null) {
            continue;
         }

         const row = p.getAttribute("data-row");
         const col = p.getAttribute("data-col");

         if(row != null && col != null) {
            this.seriesColorByKey.set(`${row}-${col}`, color);
         }

         if(col != null && !this.seriesColorByCol.has(col)) {
            this.seriesColorByCol.set(col, color);
         }
      }

      // Lines carry data-series (the colIdx) and data-color but no row/col, so they fill the col
      // fallback for line charts drawn without point markers, where the loop above leaves both maps
      // empty and the snap dim would otherwise find no color.
      const lineColorEls = Array.from(
         this.element.nativeElement.querySelectorAll(".inetsoft-line") as NodeListOf<Element>);

      for(const l of lineColorEls) {
         const color = l.getAttribute("data-color");
         const col = l.getAttribute("data-series");

         if(color != null && col != null && !this.seriesColorByCol.has(col)) {
            this.seriesColorByCol.set(col, color);
         }
      }

      // Radar hover is driven entirely by CSS :hover on polygon groups. The server injects a
      // transparent outline hit band per series (pointer-events:stroke) so each series is reachable
      // by its edge regardless of how the filled areas overlap, plus per-series rules keyed on
      // vertex-point hover so a series can also be picked precisely by its vertex when polygons are
      // near-identical. No JS activation and no inetsoft-active toggling are involved here; the
      // elementGroupMap is cleared so canvas-reported hover events are a no-op.
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
         this.setupRadarSeriesHover(radarGroups);
      }
      else if(areaFillGroups.length > 0) {
         // Area chart — raise SVG above canvas overlay and enable pointer-events on the SVG
         // element so the mousemove proximity handler can receive raw mouse coordinates.
         this.element.nativeElement.style.zIndex = "1";
         this.setupAreaHover(areaFillGroups);
      }
      else {
         const lineOnlyGroups = Array.from(
            this.element.nativeElement.querySelectorAll(".inetsoft-line") as NodeListOf<Element>);

         if(lineOnlyGroups.length > 0) {
            // Line chart (step/jump/regular, no area fills), possibly one measure of a multi-style
            // chart that also draws bars/candles/etc.
            // Raise SVG above canvas overlay so pointer events reach SVG elements.
            // Drop the point markers from elementGroupMap: they belong to the line series, and the
            // inetsoft-active CSS dims by individual point, which would fight the series-level JS
            // hover (same pattern as radar charts). Rebuilding without them rather than deleting
            // keys also restores a bar that a same-key point marker had overwritten. On a pure line
            // chart nothing else populates the map, so it ends up empty and canvas-driven
            // highlightElement() stays a no-op.
            this.elementGroupMap.clear();
            this.populateElementGroupMap(ChartInlineSvgDirective.HOVER_CLASSES
               .filter(c => c !== ".inetsoft-point"));
            this.element.nativeElement.style.zIndex = "1";
            this.setupLineHover(lineOnlyGroups);

            // Multi-style chart: a line measure drawn beside class-hover VOs (bars, candles, ...).
            // Both mechanisms must stay live, and each must dim the other's elements so the hovered
            // VO is the only bright thing in the plot.
            if(this.areaSeries.length > 0 && this.elementGroupMap.size > 0) {
               this.mixedHover = true;
               this.mixedClassHoverElements = ChartInlineSvgDirective.HOVER_CLASSES
                  .concat(ChartInlineSvgDirective.HOVER_LABEL_CLASSES)
                  .filter(c => c !== ".inetsoft-point" && c !== ".inetsoft-point-label")
                  .flatMap(c => Array.from(
                     this.element.nativeElement.querySelectorAll(c) as NodeListOf<Element>));
            }
         }
         else {
            // Non-radar, non-area, non-line chart — reset in case SVG was replaced.
            this.element.nativeElement.style.zIndex = "";
         }
      }

      // Build relation connectivity maps so activateKeys can highlight connected edges/neighbors.
      const relationNodes = Array.from(
         this.element.nativeElement.querySelectorAll(".inetsoft-relation") as NodeListOf<Element>);
      this.isRelationChart = relationNodes.length > 0;
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

      // Cache treemap/circle-packing/sunburst/icicle node groups so activateTreemapDescendants can
      // keep the hovered container's whole subtree undimmed (its descendants are separate sibling
      // groups that the server :has() dim rule would otherwise dim along with unrelated nodes).
      this.treemapGroups = Array.from(
         this.element.nativeElement.querySelectorAll(".inetsoft-treemap") as NodeListOf<Element>);

      // Build label map from server-annotated label elements for all chart types that have
      // external text groups matched to data elements (bar, point/gantt-milestone, treemap/sunburst/icicle, mekko).
      for(const labelClass of ChartInlineSvgDirective.HOVER_LABEL_CLASSES) {
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
    * Add every annotation group matching one of the given classes to elementGroupMap, keyed by
    * "data-row-data-col". Classes are applied in order, so a later class wins a key collision.
    */
   private populateElementGroupMap(cssClasses: string[]): void {
      for(const cssClass of cssClasses) {
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
            this.areaSeries.push({fillGroup, lineGroup, linePath: path, points: []});
         }
      }

      this.seriesHitMode = "ceiling";
      this.beginSeriesProximityHover();
   }

   /**
    * Sample every series path and attach the mousemove/mouseleave handlers that resolve the hovered
    * series from the cursor position. Shared by area (ceiling rule) and line (nearest rule) charts;
    * areaSeries and seriesHitMode must be populated by the caller.
    */
   private beginSeriesProximityHover(): void {
      if(this.areaSeries.length === 0) {
         return;
      }

      this.usesSeriesColorDim = true;

      // Pre-sample each line path in SVG local coordinates so mousemove hit-testing can avoid
      // repeated getPointAtLength() calls. getPointAtLength is O(path-length) in all browsers;
      // at 60 fps with multiple series that compounds quickly. Pre-sampling once at load time
      // and doing a binary search on the cached array per mousemove is far cheaper.
      const SAMPLES = 100;
      this.areaSeriesCache = [];
      for(const s of this.areaSeries) {
         const pts: {localX: number, localY: number}[] = [];
         // A path with no geometry API (nothing to measure) leaves the sample list empty, so
         // getScreenYAtX reports NaN for it and the series is simply never selected.
         const total = typeof s.linePath.getTotalLength === "function"
            ? s.linePath.getTotalLength() : 0;
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
      this.areaMouseLeaveHandler = () => {
         // Same guard as onAreaMouseMove: while snap drives the dim on a line chart, clearing here
         // would wipe the snap-driven color — and since highlightSnapSeries leaves _snapSeriesColor
         // set, its own dedup would then keep it from ever being re-emitted for that series.
         if(this.snapTooltip && this.isLineSeriesHover) {
            return;
         }

         this.deactivateArea();
         this.emitSeriesDim(null);
      };
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

   /**
    * Set up hover for a pure line chart: resolve the hovered series from the cursor position, the
    * same way area charts do, rather than from element hit-testing.
    *
    * Hit-testing cannot work here. A line is painted 1-2px wide, and widening its hit area (a
    * transparent stroke band, as radar uses) breaks down as soon as series run close together —
    * in a facet chart seven series can sit within 30px, so their bands overlap and every hit is
    * awarded to whichever band comes last in DOM order. The `r=3` point markers overlap the same
    * way. Computing each series' y at the cursor x and taking the closest instead always yields
    * exactly one winner, no matter how tight the spacing.
    *
    * Series identity is one entry per inetsoft-line group. A faceted chart emits one group per
    * (panel, series), so this also gives per-panel hover scope: hovering the 2017 panel's series
    * leaves the same-colored series in the 2018 panel dimmed, matching the whole-SVG dim scope bar
    * charts get from the server :has() rule.
    *
    * Point groups are matched to their series in two steps, scoped per parent element:
    * - Candidate series are picked by identity attribute, using one of two strategies depending on
    *   the chart structure:
    *   - If all lines in the panel have unique data-series values (distinct colIndex = multi-measure
    *     chart), points are matched by data-col (= getColIndex()) for robustness against
    *     user-configured same-color series.
    *   - If data-series values are not unique (multi-group single-measure chart, where all groups
    *     share the same colIndex, and faceted charts, which repeat the same palette in every panel),
    *     color-based matching is used instead, since each group's palette color is distinct by
    *     construction within one panel.
    * - When several series match (a faceted chart repeats every identity value once per panel), the
    *   geometrically nearest one wins, measured between the point's and the line path's bounding
    *   rects. Panels never overlap, so the nearest match is always the point's own panel. This
    *   replaces DOM-order matching, which attached every panel's points to the first panel's series.
    */
   private setupLineHover(lineGroups: Element[]): void {
      this.matchLineSeries(lineGroups);

      if(this.areaSeries.length === 0) {
         return;
      }

      this.isLineSeriesHover = true;
      this.seriesHitMode = "nearest";
      this.beginSeriesProximityHover();
   }

   /**
    * Build one areaSeries entry per inetsoft-line group and attach each point marker to the entry
    * it belongs to. See setupLineHover for the matching rules.
    */
   private matchLineSeries(lineGroups: Element[]): void {
      // Group line groups by their parent element so each facet panel gets its own
      // matching scope. Faceted charts repeat the same palette in each panel, so a
      // flat map would overwrite earlier entries and attach same-color points from
      // different panels to the same series — causing cross-panel dimming.
      const linesByParent = new Map<Element, Element[]>();
      for(const line of lineGroups) {
         const parent = line.parentElement;
         if(!parent) continue;
         if(!linesByParent.has(parent)) linesByParent.set(parent, []);
         (linesByParent.get(parent) as Element[]).push(line);
      }

      for(const [parent, parentLines] of linesByParent) {
         // Collect only the point groups within this panel so matching is scoped
         // to the same facet cell rather than the whole SVG.
         const parentPoints = Array.from(
            parent.querySelectorAll(".inetsoft-point") as NodeListOf<Element>);

         // Choose matching strategy based on whether data-series (colIndex) is unique
         // across all lines in this panel.
         // - Unique → multi-measure chart: use data-col (= colIndex) for robustness.
         //   Two measures can be assigned the same explicit color; colIndex never collides.
         // - Not unique → multi-group single-measure chart: all lines share the same colIndex
         //   (e.g. jumpLine.svg has 7 groups all with data-series="2"). Use data-color instead;
         //   each group's palette color is distinct so color-based matching is safe.
         const seriesAttrValues = parentLines
            .map(l => l.getAttribute("data-series"))
            .filter((v): v is string => v != null);
         // Both conditions are required:
         //   (1) Every line must carry data-series (backward compat: older cached SVGs may not).
         //   (2) Values must be distinct (multi-group single-measure: all share the same colIndex).
         const useIndexMatching = seriesAttrValues.length === parentLines.length &&
            new Set(seriesAttrValues).size === parentLines.length;

         // One entry per line group. Its identity value (data-series in index mode, data-color
         // otherwise) is what point groups are matched against; the same value legitimately repeats
         // across facet panels, so the rect is kept to break those ties geometrically below.
         const candidates: Array<{idx: number, matchValue: string | null, el: Element,
                                  rect?: DOMRect}> = [];

         for(const line of parentLines) {
            const path = line.querySelector("path") as SVGGeometryElement | null;

            if(path == null) {
               continue;
            }

            candidates.push({
               idx: this.areaSeries.length,
               // Measure the painted path rather than the group: the group's rect also covers its
               // point markers, which would blur the panel boundary the tie-break relies on.
               matchValue: line.getAttribute(useIndexMatching ? "data-series" : "data-color"),
               el: path
            });
            this.areaSeries.push({fillGroup: null, lineGroup: line, linePath: path, points: []});
         }

         // Match each point in this panel to its series: filter by identity value, then, when a
         // faceted chart offers one candidate per panel, take the geometrically nearest.
         for(const point of parentPoints) {
            const value = point.getAttribute(useIndexMatching ? "data-col" : "data-color");

            if(value == null) {
               this.hoverDimOnlyElements.push(point);
               continue;
            }

            const matches = candidates.filter(c => c.matchValue === value);

            if(matches.length === 0) {
               this.hoverDimOnlyElements.push(point);
               continue;
            }

            const match = matches.length === 1 ? matches[0] : this.nearestByRect(point, matches);
            this.areaSeries[match.idx].points.push(point);
         }
      }
   }

   /**
    * Return the candidate whose bounding rect is closest to the target element's center, so a point
    * marker is matched to the line series of its own facet panel rather than to a same-colored
    * series in a sibling panel. Candidate rects are measured once and cached on the candidate.
    * Distance is the gap between the target center and the candidate rect (0 when inside), so the
    * containing panel always wins. Ties keep the first candidate — the DOM-order behavior used when
    * no layout is available (e.g. a detached or headless DOM, where every rect is empty).
    */
   private nearestByRect<T extends {el: Element, rect?: DOMRect}>(target: Element,
                                                                 candidates: T[]): T
   {
      const t = target.getBoundingClientRect();
      const tx = (t.left + t.right) / 2;
      const ty = (t.top + t.bottom) / 2;
      let best = candidates[0];
      let bestDist = Number.POSITIVE_INFINITY;

      for(const c of candidates) {
         if(!c.rect) {
            c.rect = c.el.getBoundingClientRect();
         }

         const dx = Math.max(c.rect.left - tx, 0, tx - c.rect.right);
         const dy = Math.max(c.rect.top - ty, 0, ty - c.rect.bottom);
         const dist = dx * dx + dy * dy;

         if(dist < bestDist) {
            bestDist = dist;
            best = c;
         }
      }

      return best;
   }

   private teardownLineSeriesHover(): void {
      if(this.lineSeriesAbortController) {
         this.lineSeriesAbortController.abort();
         this.lineSeriesAbortController = null;
      }
      if(this.lineSeriesClearTimer !== null) {
         clearTimeout(this.lineSeriesClearTimer);
         this.lineSeriesClearTimer = null;
      }
      // Note: the pointer-events inline style set on radar polygon/point groups in
      // setupRadarSeriesHover is intentionally NOT reset here. In the afterSvgInjected() call
      // path, innerHTML is replaced before this method runs, so the old elements are already
      // detached from the DOM. In the ngOnDestroy() call path, Angular is about to remove the
      // host element entirely. In both cases, resetting the style would be a no-op.
   }

   /**
    * Cross-tile radar hover. Single-tile radar dims entirely via server CSS :hover, which is
    * scoped to one SVG and cannot reach sibling tiles of a split chart. In cross-tile mode we add
    * JS listeners that emit the hovered series' data-row (radar's stable series key); the parent
    * mirrors the dim onto every tile via setExternalSeriesDim. Listeners cover both polygon groups
    * and their vertex points (points sit on top, so the mouse may enter a point without crossing
    * the polygon interior). Reuses the line-series abort controller/timer — radar and line charts
    * never coexist in one SVG.
    */
   private setupRadarSeriesHover(radarGroups: Element[]): void {
      // Single-tile radar dims via server CSS :hover alone; no JS coordination needed.
      if(!this.crossTile) {
         return;
      }

      this.usesSeriesColorDim = true;
      this.dimKeyAttr = "data-row";
      // Radar has no external label annotation class (labels render inside the polygon/point
      // groups), so dimming the polygons and vertex points covers every radar element.
      this.dimTargetSelector = ".inetsoft-radar,.inetsoft-point";

      const points = Array.from(
         this.element.nativeElement.querySelectorAll(".inetsoft-point") as NodeListOf<Element>);
      const targets = [...radarGroups, ...points];

      if(targets.length === 0) {
         return;
      }

      const ac = new AbortController();
      this.lineSeriesAbortController = ac;

      const enter = (el: Element) => {
         if(this.lineSeriesClearTimer !== null) {
            clearTimeout(this.lineSeriesClearTimer);
            this.lineSeriesClearTimer = null;
         }
         this.emitSeriesDim(el.getAttribute("data-row"));
      };

      const leave = () => {
         if(this.lineSeriesClearTimer !== null) {
            clearTimeout(this.lineSeriesClearTimer);
         }
         this.lineSeriesClearTimer = setTimeout(() => {
            this.lineSeriesClearTimer = null;
            this.emitSeriesDim(null);
         }, ChartInlineSvgDirective.CLEAR_DELAY_MS);
      };

      for(const el of targets) {
         (el as HTMLElement).style.pointerEvents = "all";
         el.addEventListener("mouseenter", () => enter(el), { signal: ac.signal } as AddEventListenerOptions);
         el.addEventListener("mouseleave", leave, { signal: ac.signal } as AddEventListenerOptions);
      }
   }

   private onAreaMouseMove(e: MouseEvent): void {
      // Under snap the snapped point drives the dim (highlightSnapSeries); resolving a series from
      // the cursor as well would fight it. Area charts ignore snap — their own cursor-band hover
      // already resolves a series — so this only applies to line charts.
      if(this.snapTooltip && this.isLineSeriesHover) {
         return;
      }

      // Multi-style chart: the cursor is on a class-hover VO (the canvas overlay resolved a bar
      // region and activated it), so that VO owns the highlight. The "nearest series within 50px"
      // rule would otherwise steal it for a line that merely passes near the bar.
      if(this.mixedHover && this._activeFound) {
         return;
      }

      // Collect screen y for every series. getScreenYAtX returns NaN when the mouse x is
      // outside that path's x-range (> 20px beyond path endpoints) — cross-panel paths
      // always return NaN so they can never be activated or receive a dim style.
      const yValues: (number | null)[] = [];

      for(let i = 0; i < this.areaSeries.length; i++) {
         const y = this.getScreenYAtX(i, e.clientX);
         yValues.push(isNaN(y) ? null : y);
      }

      const nearestIdx = this.seriesHitMode === "nearest"
         ? this.findNearestSeries(yValues, e.clientY) : this.findCeilingSeries(yValues, e.clientY);

      if(nearestIdx !== this.activeSeriesIdx) {
         this.deactivateArea();
         this.activeSeriesIdx = nearestIdx;

         // Cross-tile: don't dim locally — emit the active color so the parent dims every tile
         // (including this one) uniformly. The geometry-based local dim below cannot reach the
         // sibling SVGs that hold the rest of each series.
         if(this.crossTile) {
            const active = nearestIdx >= 0 ? this.areaSeries[nearestIdx] : null;
            this.emitSeriesDim(active
               ? (active.fillGroup ?? active.lineGroup).getAttribute("data-color") : null);
         }
         else if(nearestIdx >= 0) {
            // Dim every other (panel, series) entry, including those in sibling facet panels.
            // Cross-panel entries can never be *selected* (getScreenYAtX returns NaN for them, so
            // yValues[i] === null keeps them out of the searches above), but they must still dim:
            // a facet panel is a separate coordinate space, so hovering a series in one panel
            // highlights that panel's series only and darkens the rest of the chart — the same
            // scope the server :has() rule gives bar charts.
            for(let i = 0; i < this.areaSeries.length; i++) {
               if(i === nearestIdx) continue;
               this.setSeriesOpacity(this.areaSeries[i], "0.2");
            }

            for(const el of this.hoverDimOnlyElements) {
               (el as HTMLElement).style.setProperty("opacity", "0.2", "important");
            }

            // Multi-style chart: fade the bars (or other class-hover VOs) too, so a series hover
            // dims the whole plot the same way a bar hover does. Done inline rather than with
            // inetsoft-dim-all, whose A1 rule would also dim the active series' own point markers.
            this.setMixedClassHoverDim(true);
         }
      }
   }

   /**
    * Ceiling rule (area charts): the fill band between line A (above) and line B (below) belongs to
    * A. "Above the mouse" = lineY < mouseY (smaller screen y = higher on screen). Among all lines
    * above the mouse, pick the one with the LARGEST lineY (the ceiling — the line immediately above
    * the cursor). This maps the cursor into its fill band. If no line is above the mouse (cursor is
    * above every series or between panels), -1 clears all highlighting — an intentional reset.
    */
   private findCeilingSeries(yValues: (number | null)[], mouseY: number): number {
      let idx = -1;
      let ceilingY = -Infinity; // largest lineY that is still < mouseY

      for(let i = 0; i < yValues.length; i++) {
         const y = yValues[i];
         if(y === null) continue;
         if(y < mouseY && y > ceilingY) { ceilingY = y; idx = i; }
      }

      return idx;
   }

   /**
    * Nearest rule (line charts): the series whose line is closest to the cursor wins, above or
    * below. This separates series no matter how tightly they are packed, which hit-testing on the
    * elements themselves cannot do. Beyond NEAREST_MAX_DIST_PX nothing is selected, so moving into
    * empty plot area releases the highlight instead of holding the last series lit.
    */
   private findNearestSeries(yValues: (number | null)[], mouseY: number): number {
      let idx = -1;
      let bestDist = ChartInlineSvgDirective.NEAREST_MAX_DIST_PX;

      for(let i = 0; i < yValues.length; i++) {
         const y = yValues[i];
         if(y === null) continue;
         const dist = Math.abs(y - mouseY);
         if(dist < bestDist) { bestDist = dist; idx = i; }
      }

      return idx;
   }

   /** Apply (or clear, with "") an opacity to every element of one series entry. */
   private setSeriesOpacity(series: {fillGroup: Element | null, lineGroup: Element,
                                     points: Element[]}, opacity: string): void {
      // setProperty with "important" so the value overrides animation fill-mode, which holds
      // opacity:1 on point markers after inetsoft-line-fade completes and sits above normal
      // inline styles in the CSS cascade.
      for(const el of [series.fillGroup, series.lineGroup, ...series.points]) {
         if(el == null) {
            continue;
         }

         if(opacity === "") {
            (el as HTMLElement).style.removeProperty("opacity");
         }
         else {
            (el as HTMLElement).style.setProperty("opacity", opacity, "important");
         }
      }
   }

   /** Dim (or release) every series entry at once, plus the markers that matched no series. */
   private setAllSeriesDim(dim: boolean): void {
      for(const s of this.areaSeries) {
         this.setSeriesOpacity(s, dim ? "0.2" : "");
      }

      for(const el of this.hoverDimOnlyElements) {
         if(dim) {
            (el as HTMLElement).style.setProperty("opacity", "0.2", "important");
         }
         else {
            (el as HTMLElement).style.removeProperty("opacity");
         }
      }
   }

   /**
    * Dim (or release) the class-hover VOs of a mixed tile while a line/area series is highlighted.
    * No-op on a tile that holds only one hover mechanism (mixedClassHoverElements is empty).
    */
   private setMixedClassHoverDim(dim: boolean): void {
      for(const el of this.mixedClassHoverElements) {
         if(dim) {
            (el as HTMLElement).style.setProperty("opacity", "0.2", "important");
         }
         else {
            (el as HTMLElement).style.removeProperty("opacity");
         }
      }
   }

   private deactivateArea(): void {
      // In cross-tile mode opacity is managed only by setExternalSeriesDim, so leave it alone.
      if(this.activeSeriesIdx >= 0 && !this.crossTile) {
         this.setAllSeriesDim(false);
         this.setMixedClassHoverDim(false);
      }
      this.activeSeriesIdx = -1;
   }

   /** Emit seriesDimChange only when the active color actually changes, to suppress duplicates. */
   private emitSeriesDim(color: string | null): void {
      if(color !== this._emittedSeriesColor) {
         this._emittedSeriesColor = color;
         this.seriesDimChange.emit(color);
      }
   }

   /** Emit relationHover only when the active node id changes, to suppress duplicates. */
   private emitRelationHover(id: string | null): void {
      if(id !== this._emittedRelationId) {
         this._emittedRelationId = id;
         this.relationHover.emit(id);
      }
   }

   /** This tile's relation edges as source/target id pairs, for the parent to merge into the
    *  cross-tile connectivity graph. */
   getRelationEdges(): { source: string, target: string }[] {
      return this.relationEdges.map(e => ({ source: e.sourceId, target: e.targetId }));
   }

   /**
    * Highlight this tile's share of a relation hover, driven by the parent so neighbours render
    * correctly across a split chart's SVGs. Nodes whose data-id is in activeIds (the hovered node
    * plus its neighbours) and edges incident to hoveredId get inetsoft-active; their labels too.
    * A tile with at least one active element relies on the server :has() rule to dim the rest; a
    * tile with none gets inetsoft-dim-all so it dims fully. Pass null to clear.
    */
   setExternalRelationHighlight(activeIds: Set<string> | null, hoveredId: string | null): void {
      const nodes = Array.from(this.element.nativeElement
         .querySelectorAll(".inetsoft-relation") as NodeListOf<Element>);
      const edges = Array.from(this.element.nativeElement
         .querySelectorAll(".inetsoft-relation-edge") as NodeListOf<Element>);
      const labels = Array.from(this.element.nativeElement
         .querySelectorAll(".inetsoft-relation-label") as NodeListOf<Element>);

      if(activeIds == null) {
         for(const el of [...nodes, ...edges, ...labels]) el.classList.remove("inetsoft-active");
         this.svgRootEl?.classList.remove("inetsoft-dim-all");
         return;
      }

      let anyActive = false;
      const activeRowCols = new Set<string>();

      for(const node of nodes) {
         const id = node.getAttribute("data-id");
         if(id != null && activeIds.has(id)) {
            node.classList.add("inetsoft-active");
            anyActive = true;
            const r = node.getAttribute("data-row"), c = node.getAttribute("data-col");
            if(r != null && c != null) activeRowCols.add(`${r}-${c}`);
         }
         else {
            node.classList.remove("inetsoft-active");
         }
      }

      for(const edge of edges) {
         const s = edge.getAttribute("data-source"), t = edge.getAttribute("data-target");
         if(hoveredId != null && (s === hoveredId || t === hoveredId)) {
            edge.classList.add("inetsoft-active");
            anyActive = true;
         }
         else {
            edge.classList.remove("inetsoft-active");
         }
      }

      for(const label of labels) {
         const r = label.getAttribute("data-row"), c = label.getAttribute("data-col");
         if(r != null && c != null && activeRowCols.has(`${r}-${c}`)) {
            label.classList.add("inetsoft-active");
         }
         else {
            label.classList.remove("inetsoft-active");
         }
      }

      if(this.svgRootEl) {
         this.svgRootEl.classList.toggle("inetsoft-dim-all", !anyActive);
      }
   }

   /**
    * Dim this tile's series elements whose dimKeyAttr value differs from activeValue, leaving the
    * matching (hovered) series at full opacity. Pass null to clear. Called by the parent for every
    * tile so the hovered series stays highlighted consistently across a split chart's SVGs. The
    * key is data-color for area/line and data-row for radar — both stable across tiles.
    */
   setExternalSeriesDim(activeValue: string | null): void {
      const elems = this.element.nativeElement.querySelectorAll(
         this.dimTargetSelector) as NodeListOf<Element>;

      for(const el of Array.from(elems) as Element[]) {
         if(activeValue != null && el.getAttribute(this.dimKeyAttr) !== activeValue) {
            (el as HTMLElement).style.setProperty("opacity", "0.2", "important");
         }
         else {
            (el as HTMLElement).style.removeProperty("opacity");
         }
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
         // Lookbehind for '-' is required: \b would also match data-id="..." (word boundary between
         // '-' and 'i'), rewriting it to data-id="uid-..." while data-source/data-target keep the
         // original IDs, breaking edge lookup in relationNodeIdMap.
         .replace(/(?<![a-zA-Z0-9_-])id="([^"]+)"/g, `id="${uid}-$1"`)
         .replace(/\burl\(#([^)]+)\)/g, `url(#${uid}-$1)`)
         .replace(/\bhref="#([^"]+)"/g, `href="#${uid}-$1"`)
         // Batik 1.17 emits xlink:href="#id" for <use> elements (marker/symbol references).
         // The colon prevents \bhref= from matching, so this must be a separate replace.
         .replace(/\bxlink:href="#([^"]+)"/g, `xlink:href="#${uid}-$1"`);
   }
}
