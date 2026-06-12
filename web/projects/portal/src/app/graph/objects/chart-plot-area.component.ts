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
import {
   ChangeDetectionStrategy,
   ChangeDetectorRef,
   Component,
   ElementRef,
   EventEmitter,
   HostBinding,
   HostListener,
   Input,
   NgZone,
   OnChanges,
   Output,
   QueryList,
   SimpleChanges,
   ViewChild,
   ViewChildren
} from "@angular/core";
import { SafeStyle } from "@angular/platform-browser";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ComponentTool } from "../../common/util/component-tool";
import { GuiTool } from "../../common/util/gui-tool";
import { ContextProvider } from "../../vsobjects/context-provider.service";
import { SelectionBoxEvent, SelectionBoxDirective } from "../../widget/directive/selection-box.directive";
import { DebounceService } from "../../widget/services/debounce.service";
import { ModelService } from "../../widget/services/model.service";
import { ScaleService } from "../../widget/services/scale/scale-service";
import { ChartRegion } from "../model/chart-region";
import { ChartSelection } from "../model/chart-selection";
import { ChartTile } from "../model/chart-tile";
import { ChartTool } from "../model/chart-tool";
import { FlyoverInfo } from "../model/flyover-info";
import { Plot } from "../model/plot";
import { PlotScaleInfo } from "../model/plot-scale-info";
import { TooltipInfo } from "../model/tooltip-info";
import { ChartConfigService } from "../services/chart-config.service";
import { ChartInlineSvgDirective } from "./chart-inline-svg.directive";
import { ChartService } from "../services/chart.service";
import { ChartObjectAreaBase } from "./chart-object-area-base";
import { Point } from "../../common/data/point";
import { ChartImageDirective } from "./chart-image.directive";
import { OutOfZoneDirective } from "../../widget/directive/out-of-zone.directive";


@Component({
    selector: "chart-plot-area",
    templateUrl: "chart-plot-area.component.html",
    styleUrls: ["chart-plot-area.component.scss"],
    providers: [{
            provide: ChartObjectAreaBase,
            useExisting: ChartPlotArea
        }],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [SelectionBoxDirective, OutOfZoneDirective, ChartImageDirective, ChartInlineSvgDirective]
})
export class ChartPlotArea extends ChartObjectAreaBase<Plot> implements OnChanges {
   @Input() dataTip: string;
   @Input() dataTipOnClick: boolean;
   @Input() flyover: boolean;
   @Input() flyOnClick: boolean;
   @Input() scrollbarWidth: number;
   @Input() selectionBoxWithTouch: boolean = false;
   @Input() viewerMode = false;
   @Input() previewMode = false;
   @Input() container: Element;
   @Input() selected: boolean = false;
   @Input() panMode: boolean = false;
   @Input() hasEmptyPlotLinkModel: boolean = false;
   @Input() set plotScaleInfo(plotScaleInfo: PlotScaleInfo) {
      if(plotScaleInfo) {
         const context = this.getContext();

         if(context) {
            this.chartService.clearCanvas(context);

            if(!plotScaleInfo.clear && this.model) {
               const drawRegions = this.chartObject.regions.filter(
                  (region) => !!region && !region.noselect &&
                     ChartTool.areaType(this.model, region) != "text");

               if(plotScaleInfo.vertical) {
                  ChartTool.drawRegions(context, drawRegions, this.canvasX, this.canvasY, this.viewsheetScale,
                                        1, plotScaleInfo.scale);
               }
               else {
                  ChartTool.drawRegions(context, drawRegions, this.canvasX, this.canvasY, this.viewsheetScale,
                                        plotScaleInfo.scale, 1);
               }
            }
         }
      }
   }

   @Output() sendFlyover = new EventEmitter<FlyoverInfo>();
   @Output() showDataTip = new EventEmitter<ChartSelection>();
   @Output() showTooltip = new EventEmitter<TooltipInfo>();
   @Output() selectRegion = new EventEmitter();
   @Output() showHyperlink = new EventEmitter<MouseEvent|TouchEvent>();
   @Output() brushChart = new EventEmitter();
   @Output() scrollArea = new EventEmitter<any>();
   @Output() changeCursor = new EventEmitter<string>();
   @Output() onLoad = new EventEmitter<boolean>();
   @Output() onLoading = new EventEmitter<any>();
   @Output() onPanMap = new EventEmitter<Point>();

   @ViewChild("referenceLineCanvas") referenceLineCanvas: ElementRef;
   @ViewChildren(ChartInlineSvgDirective) inlineSvgTiles: QueryList<ChartInlineSvgDirective>;
   isResize: boolean = false;
   selectionWidth: number;
   selectionHeight: number;
   private oSrc: string;
   private altDown = false;
   private status: boolean = true;
   panning: Point = null;
   panBackground = null;
   panSnapshot = null;
   panX: number = 0;
   panY: number = 0;
   hideTile: boolean = false;
   destroyed: boolean = false;

   private readonly debounceKey: string = "chart_dataTipEvent";

   // Cross-tile area/line hover: the hovered tile reports the active series color; we mirror the
   // dim onto every tile so the hovered series stays highlighted across a split chart's SVGs.
   private seriesDimClearTimer: ReturnType<typeof setTimeout> | null = null;
   // Tile currently driving the cross-tile dim, so a stale null from a tile the cursor already
   // left (the events can arrive in either order) doesn't clear the dim the new tile just set.
   private activeDimTile: ChartTile | null = null;

   // Cross-tile relation/tree hover: the hovered tile reports the node id; we resolve neighbours
   // from the graph merged across every tile (a node's neighbours may be in sibling tiles) and
   // drive each tile's highlight. Same debounce + active-tile guard as the series dim above.
   private relationDimClearTimer: ReturnType<typeof setTimeout> | null = null;
   private activeRelationTile: ChartTile | null = null;

   // Snap: cached X ticks → region indices. Rebuilt on chartObject change.
   private snapXTicks: Array<{ pixelX: number, regionIndices: number[] }> = [];
   private snapXTicksFor: Plot = null;

   @HostBinding("style.cursor") hostCursor: string = "inherit";
   private cursorStyle: string = "inherit";

   private _clientRect: ClientRect;

   get clientRect(): ClientRect {
      return this._clientRect ? this._clientRect
         : this.objectCanvas.nativeElement.getBoundingClientRect();
   }

   constructor(protected chartService: ChartService,
               private changeRef: ChangeDetectorRef,
               private zone: NgZone,
               private debounceService: DebounceService,
               private modelService: ModelService,
               private http: HttpClient,
               private modal: NgbModal,
               scaleService: ScaleService,
               private contextProvider: ContextProvider,
               private chartConfigService: ChartConfigService)
   {
      super(chartService, scaleService);
   }

   get inlineSvg(): boolean {
      return this.chartConfigService.inlineSvg;
   }

   ngOnChanges(changes: SimpleChanges) {
      if(changes.scrollLeft || changes.scrollTop) {
         const context = this.getContext();

         if(context) {
            this.chartService.clearCanvas(context);
            this.debounceService.debounce("plot.scrolled", () => this.updateChartObject(), 500, []);
         }
      }
   }

   protected cleanup(): void {
      this.destroyed = true;

      if(this.seriesDimClearTimer !== null) {
         clearTimeout(this.seriesDimClearTimer);
         this.seriesDimClearTimer = null;
      }

      if(this.relationDimClearTimer !== null) {
         clearTimeout(this.relationDimClearTimer);
         this.relationDimClearTimer = null;
      }
   }

   private emitFlyover(chartSelection: ChartSelection): void {
      const flyoverPayload = Object.assign(chartSelection, {
         context: this.getContext(),
         canvasX: this.canvasX,
         canvasY: this.canvasY
      });
      this.sendFlyover.emit(flyoverPayload);
   }

   private emitTooltip(regions: ChartRegion[], primary?: ChartRegion): void {
      // A snap-selected primary (nearest the cursor) wins over the default
      // lowest-index ordering used for plain hover.
      if(primary && primary.tipIdx >= 0) {
         this.showTooltip.emit({ tipIndex: primary.tipIdx, region: primary });
         return;
      }

      const tipInfo = regions
         .filter(region => !!region)
         .sort((a, b) => a.index - b.index)
         .map(region => ({ tipIndex: region.tipIdx, region: region }))
         .find(ti => ti.tipIndex >= 0);
      this.showTooltip.emit(tipInfo);
   }

   // Group regions by rowIdx; pick the narrowest in each bucket as the snap-X
   // representative so point markers beat wide area polygons.
   private rebuildSnapIndex(): void {
      this.snapXTicks = [];
      this.snapXTicksFor = this.chartObject;

      if(!this.chartObject || !this.chartObject.regions) {
         return;
      }

      const buckets = new Map<number, { regionIndices: number[],
                                        bestX: number, bestWidth: number }>();

      this.chartObject.regions.forEach((r, i) => {
         if(!r || r.tipIdx < 0 || r.rowIdx < 0) {
            return;
         }

         const bounds = this.regionBoundsX(r);

         if(!bounds) {
            return;
         }

         const existing = buckets.get(r.rowIdx);

         if(existing) {
            existing.regionIndices.push(i);

            if(bounds.width < existing.bestWidth) {
               existing.bestWidth = bounds.width;
               existing.bestX = bounds.centerX;
            }
         }
         else {
            buckets.set(r.rowIdx, {
               regionIndices: [i],
               bestX: bounds.centerX,
               bestWidth: bounds.width
            });
         }
      });

      this.snapXTicks = Array.from(buckets.values())
         .map(b => ({ pixelX: Math.round(b.bestX), regionIndices: b.regionIndices }))
         .sort((a, b) => a.pixelX - b.pixelX);
   }

   // Prefer server centroid; fall back to pts-derived bbox.
   private regionBoundsX(region: ChartRegion): { centerX: number, width: number } | null {
      if(region.centroid) {
         const pts = region.pts;

         if(pts && pts.length > 0) {
            const bbox = this.ptsBoundsX(region);

            if(bbox) {
               return { centerX: region.centroid.x, width: bbox.width };
            }
         }

         return { centerX: region.centroid.x, width: 0 };
      }

      return this.ptsBoundsX(region);
   }

   private ptsBoundsX(region: ChartRegion): { centerX: number, width: number } | null {
      const pts = region.pts;

      if(!pts || pts.length === 0) {
         return null;
      }

      // RECT_PATH(8) / ELLIPSE_PATH(9) store pts as [[x,y], [w,h]].
      const seg = region.segTypes && region.segTypes[0] ? region.segTypes[0][0] : -1;

      if((seg === 8 || seg === 9) && pts[0] && pts[0][0] && pts[0][0].length === 2) {
         const origin = pts[0][0][0];
         const size = pts[0][0][1];

         if(origin && size) {
            return { centerX: origin[0] + size[0] / 2, width: size[0] };
         }
      }

      // General path: scan all x coordinates for bbox.
      let minX = Infinity;
      let maxX = -Infinity;

      for(const polygon of pts) {
         for(const sub of polygon) {
            for(const pt of sub) {
               if(pt[0] < minX) {
                  minX = pt[0];
               }

               if(pt[0] > maxX) {
                  maxX = pt[0];
               }
            }
         }
      }

      if(minX === Infinity) {
         return null;
      }

      return { centerX: (minX + maxX) / 2, width: maxX - minX };
   }

   private findNearestSnap(eventX: number): { pixelX: number, regionIndices: number[] } {
      if(this.snapXTicks.length === 0) {
         return null;
      }

      let nearest = this.snapXTicks[0];
      let best = Math.abs(eventX - nearest.pixelX);

      for(let i = 1; i < this.snapXTicks.length; i++) {
         const d = Math.abs(eventX - this.snapXTicks[i].pixelX);

         if(d < best) {
            best = d;
            nearest = this.snapXTicks[i];
         }
      }

      return nearest;
   }

   // Within a snapped x-bucket, pick the region nearest the cursor so the header,
   // guideline and highlight track the hovered bar/series rather than the first.
   private findPrimarySnapRegion(regionIndices: number[], eventX: number,
                                 eventY: number): { region: ChartRegion, centerX: number }
   {
      const candidates: { region: ChartRegion, centerX: number, width: number }[] = [];
      let minWidth = Infinity;

      for(const i of regionIndices) {
         const region = this.chartObject.regions[i];

         if(!region || region.tipIdx < 0) {
            continue;
         }

         const bounds = this.regionBoundsX(region);

         if(!bounds) {
            continue;
         }

         candidates.push({ region, centerX: bounds.centerX, width: bounds.width });
         minWidth = Math.min(minWidth, bounds.width);
      }

      let best: { region: ChartRegion, centerX: number } = null;
      let bestDx = Infinity;
      let bestDy = Infinity;

      for(const c of candidates) {
         // Skip wide area polygons whose center is the series midpoint, not a
         // data point; only the narrowest tier (bars/markers) locates the cursor.
         if(c.width > minWidth + 1) {
            continue;
         }

         const dx = Math.abs(c.centerX - eventX);
         // Tie-break near-equal X (overlapping line/area points) by vertical distance.
         const dy = c.region.centroid ? Math.abs(c.region.centroid.y - eventY) : 0;

         if(dx < bestDx - 1 || (Math.abs(dx - bestDx) <= 1 && dy < bestDy)) {
            best = { region: c.region, centerX: c.centerX };
            bestDx = dx;
            bestDy = dy;
         }
      }

      return best;
   }

   private drawSnapGuideline(pixelX: number): void {
      if(!this.referenceLineCanvas) {
         return;
      }

      const canvas = this.referenceLineCanvas.nativeElement as HTMLCanvasElement;
      const ctx = canvas.getContext("2d");
      this.chartService.clearCanvas(ctx);
      ctx.save();
      ctx.setLineDash([4, 4]);
      ctx.strokeStyle = "rgba(120, 120, 120, 0.7)";
      ctx.lineWidth = 1;
      ctx.beginPath();
      // +0.5 keeps the 1px line crisp on integer pixel grid.
      ctx.moveTo(pixelX + 0.5, 0);
      ctx.lineTo(pixelX + 0.5, canvas.height);
      ctx.stroke();
      ctx.restore();
   }

   private clearSnapGuideline(): void {
      if(this.referenceLineCanvas) {
         this.chartService.clearCanvas(
            this.referenceLineCanvas.nativeElement.getContext("2d"));
      }
   }

   @HostListener("document:keydown.alt", ["$event"])
   onAltDown(event) {
      this.altDown = true;
      this.changeCursor0(this.cursorStyle);
   }

   @HostListener("document:keyup.alt", ["$event"])
   onAltUp(event: KeyboardEvent) {
      this.altDown = false;
      this.changeCursor0(this.cursorStyle);
      event.preventDefault();
   }

   private changeCursor0(cursor: string) {
      this.cursorStyle = cursor;
      // alt prevents hyperlink from being triggered
      const currentCursor = this.altDown ? "inherit" : this.cursorStyle;

      if(currentCursor != this.hostCursor) {
         this.zone.run(() => this.hostCursor = currentCursor);
      }
   }

   /**
    * Mousemove handling that does the following:
    *    1. Emit if on a flyover
    *    2. Emit if there's a datatip
    *    3. If no datatip, emit the currently selected tooltip string
    */
   onMove(event: MouseEvent | TouchEvent): void {
      if(this.panning) {
         this.panX = GuiTool.clientX(event) - this.panning.x;
         this.panY = GuiTool.clientY(event) - this.panning.y;
         this.changeRef.detectChanges();
         return;
      }

      // About 'button' and 'buttons' in event:
      // 1.'button': mouse left: 0 ; mouse middle: 1 ; mouse right: 2
      // 2.'buttons': mouse left: 1 ; mouse middle: 4 ; mouse right: 2
      // 'buttons' will do XOR on the values of all the mouse keys be pressed and
      // display different values in the mobile state in different browsers. (Bug #60440)
      if((<any> event).button === 0) {
         const mevent: MouseEvent = <MouseEvent> event;
         let eventX: number;
         let eventY: number;

         if(this.inlineSvg) {
            // Inline area/line/radar SVG tiles are raised above the overlay canvas with
            // pointer-events:all, so they — not the canvas — become the event target and
            // offsetX/offsetY are measured from the hovered tile's origin, not the plot. That
            // left-shifts the X for every tile past the first, putting the snap line and tooltip
            // in the wrong tile. Derive plot coordinates from the overlay canvas rect instead,
            // matching onContextMenu's convention.
            const rect = this.objectCanvas.nativeElement.getBoundingClientRect();
            eventX = (mevent.clientX - rect.left) / this.viewsheetScale + this.scrollLeft;
            eventY = (mevent.clientY - rect.top) / this.viewsheetScale + this.scrollTop;
         }
         else {
            eventX = mevent.offsetX + this.scrollLeft;
            eventY = mevent.offsetY + this.scrollTop;
         }
         let regions: ChartRegion[] = this.getTreeRegions(eventX, eventY);
         let snapPrimary: ChartRegion = null;

         // Snap: replace hit-tested regions with the nearest X tick's set, then
         // pick the region nearest the cursor as the header and draw a dashed
         // vertical line at it.
         if(this.model && this.model.snapTooltip) {
            if(this.snapXTicksFor !== this.chartObject) {
               this.rebuildSnapIndex();
            }

            const snap = this.findNearestSnap(eventX);

            if(snap) {
               regions = snap.regionIndices.map(i => this.chartObject.regions[i]);
               const primary = this.findPrimarySnapRegion(snap.regionIndices, eventX, eventY);
               snapPrimary = primary ? primary.region : null;
               this.drawSnapGuideline(primary ? Math.round(primary.centerX) : snap.pixelX);
            }
            else {
               this.clearSnapGuideline();
            }
         }

         let chartSelection = {
            chartObject: this.chartObject,
            regions: regions
         };

         if(this.flyover && !this.flyOnClick) {
            this.emitFlyover(chartSelection);
         }

         if(this.dataTip && !this.dataTipOnClick) {
            this.debounceService.debounce(this.debounceKey, () => {
               this.showDataTip.emit(chartSelection);
            }, 100, []);
         }
         else {
            this.emitTooltip(regions, snapPrimary);
         }

         if(this.viewerMode || this.previewMode) {
            let hasLinkPoint = false;

            if(!!this.links) {
               this.links.forEach((link) => {
                  let linkLoc: string[] = link.split("/");

                  if(linkLoc && linkLoc.length == 3 && regions.some((region) => {
                     const row = Number(linkLoc[1]);
                     const col = Number(linkLoc[2]);
                     return (ChartTool.colIdx(this.model, region) == col || col < 0) &&
                        (region.rowIdx == row || row < 0);
                  }))
                  {
                     this.changeCursor0("pointer");
                     hasLinkPoint = true;
                  }
               });
            }

            if(!hasLinkPoint && regions.some((region) =>
                  !!region && !!region.hyperlinks && region.hyperlinks.length > 0)) {
               this.changeCursor0("pointer");
            }
            else if (!hasLinkPoint && this.hasEmptyPlotLinkModel && regions.length == 0) {
               this.changeCursor0("pointer");
            }
            else if(!hasLinkPoint) {
               this.changeCursor0("inherit");
            }
         }

         // Snap owns the reference canvas while active; skip the per-region
         // reference-line draw to avoid clobbering the snap guideline.
         if(this.chartObject.showReferenceLine && !(this.model && this.model.snapTooltip)) {
            const context = this.referenceLineCanvas.nativeElement.getContext("2d");
            const region = regions.find((r) => r && ChartTool.refLine(this.model, r));
            this.chartService.clearCanvas(context);

            if(region) {
               ChartTool.drawReferenceLine(context, region, this.canvasX, this.canvasY, this.viewsheetScale);
            }
         }

         if(this.inlineSvg) {
            // Find the hovered VO region (bar, point, etc.) by row/col presence.
            // Snap supplies the nearest bar/series so the highlight tracks it.
            const voRegion = (snapPrimary && snapPrimary.rowIdx >= 0 &&
               ChartTool.colIdx(this.model, snapPrimary) >= 0)
               ? snapPrimary
               : regions?.find(r => r && r.rowIdx >= 0 &&
                  ChartTool.colIdx(this.model, r) >= 0);
            const rowIdx = voRegion != null ? voRegion.rowIdx : null;
            const colIdx = voRegion != null ? ChartTool.colIdx(this.model, voRegion) : null;
            this.inlineSvgTiles?.forEach(d => d.highlightElement(rowIdx, colIdx));
         }
      }
      else if(!this.dataTip) {
         this.showTooltip.emit(null);
      }
   }

   onDown(event: MouseEvent | TouchEvent) {
      if(this.panMode && (event.which == 0 || event.which == 1)) {
         event.preventDefault();
         event.stopImmediatePropagation();
         this.panning = new Point(GuiTool.clientX(event), GuiTool.clientY(event));
         this.panSnapshot = this.getSrc(this.chartObject.tiles[0]);
         this.panBackground = this.getBackground();
      }
   }

   onUp(event: MouseEvent | TouchEvent) {
      if(this.panning) {
         // percentage of width/height
         const panX = -this.panX / this.chartObject.layoutBounds.width;
         const panY = this.panY / this.chartObject.layoutBounds.height;
         this.onPanMap.emit(new Point(panX, panY));
         this.panning = null;
         this.changeRef.detectChanges();
      }
   }

   onSelectionBox(event: SelectionBoxEvent) {
      const box = event.box;
      const x1 = box.x + this.scrollLeft;
      const x2 = x1 + box.width;
      const y1 = box.y + this.scrollTop;
      const y2 = y1 + box.height;

      if(this.mobile && (x1 != x2 || y1 != y2)) {
         this.showTooltip.emit(null);
      }

      this.selectChart(event, x1, y1, x2, y2);
   }

   private selectChart(event: MouseEvent|TouchEvent, x1: number, y1: number,
                       x2: number, y2: number): void
   {
      if(!this.isResize) {
         let regions = this.getTreeRegions(x1, y1, x2, y2)
            .filter((region) => !!region && !region.noselect);

         if(x1 == x2 && y1 == y2) {
            regions = this.getSingleClickRegions(regions);

            if(this.mobile) {
               const context = this.referenceLineCanvas.nativeElement.getContext("2d");
               ChartTool.drawTouch(context, x1, y1);
            }
         }

         // Select Region
         this.selectRegion.emit({
            chartObject: this.chartObject,
            context: this.getContext(),
            canvasX: this.canvasX,
            canvasY: this.canvasY,
            regions: this.selectIntersect(regions, this.chartObject.regions),
            rangeSelection: x1 != x2 || y1 != y2,
            isCtrl: event.ctrlKey || event.metaKey
         });

         // Show hyperlinks
         let hyperlinkRegion: ChartRegion;

         // Only allow a hyperlink to show if we don't click + drag
         if(x1 === x2 && y1 === y2) {
            hyperlinkRegion = regions.find((region) => region.hyperlinks != null);
         }

         // emit if there is hyperlink or link event (Replet.addLink) on the region
         if(hyperlinkRegion || this.hostCursor == "pointer") {
            this.showHyperlink.emit(event);
         }

         // Show flyover
         if(this.flyover) {
            const flyoverPayload = {
               chartObject: this.chartObject,
               areaName: this.getAreaName(),
               regions,
               context: this.getContext(),
               canvasX: this.canvasX,
               canvasY: this.canvasY
            };

            this.sendFlyover.emit(flyoverPayload);
         }

         if(this.dataTipOnClick && this.dataTip) {
            this.emitClickDataTip(!!hyperlinkRegion, event, x1, y1,
               s => this.showDataTip.emit(s));
         }

         this.selectionWidth = 0;
         this.selectionHeight = 0;
      }
      else {
         this.isResize = false;
         this.changeCursor.emit("default");
      }
   }

   private getSingleClickRegions(regions: ChartRegion[]): ChartRegion[] {
      const hasText = regions.some(r => ChartTool.areaType(this.model, r) === "text");

      // When selecting text, regions should not contain below vo.
      if(hasText) {
         regions = regions.filter(r => ChartTool.areaType(this.model, r) !== "vo");
      }
      else if(regions.length > 0) {
         regions = [regions[0]];
      }

      return regions;
   }

   // select all regions with intersecting selectRows
   private selectIntersect(selected: ChartRegion[], all: ChartRegion[]): ChartRegion[] {
      const selectedRows = new Set();

      selected.filter(r => r && r.selectRows)
         .forEach(r => r.selectRows.forEach(i => selectedRows.add(i)));

      return all.filter(r => !selected.includes(r) && r.selectRows)
         .filter(r => r.selectRows.some(i => selectedRows.has(i)))
         .concat(selected);
   }

   onContextMenu(event: any): void {
      const objLeft = this.clientRect.left;
      const objTop = this.clientRect.top;
      const x = (event.clientX - objLeft) / this.viewsheetScale + this.scrollLeft;
      const y = (event.clientY - objTop) / this.viewsheetScale + this.scrollTop;
      const regions = this.getTreeRegions(x, y).filter((region) => {
         return !!region && !region.noselect;
      });

      const anySelected = regions
         .some(r => ChartTool.isRegionSelected(this.chartSelection, this.chartObject.areaName, r));

      if(!anySelected) {
         // Select Region
         this.selectRegion.emit({
            chartObject: this.chartObject,
            context: this.getContext(),
            canvasX: this.canvasX,
            canvasY: this.canvasY,
            regions: this.getSingleClickRegions(regions),
            rangeSelection: false,
            isCtrl: false
         });
      }
   }

   /**
    * When the mouse leaves a chart object reset the tooltip
    */
   onLeave(event: MouseEvent): void {
      this.debounceService.cancel(this.debounceKey);
      this.showTooltip.emit(null);

      if(this.model && this.model.snapTooltip) {
         this.clearSnapGuideline();
      }

      if(this.dataTip && !this.mobile && !this.dataTipOnClick) {
         const cls = "current-datatip-" + this.dataTip.replace(/ /g, "_");
         const tipElement: HTMLElement = document.getElementsByClassName(cls)[0] as HTMLElement;

         if(!tipElement || !GuiTool.isMouseIn(tipElement, event) &&
            !GuiTool.parentContainsClass((event as any).toElement, cls))
         {
            this.showDataTip.emit({
               chartObject: this.chartObject,
               regions: []
            });
         }
      }

      if(this.flyover && !this.flyOnClick) {
         this.clearSelection();
      }

      if(this.inlineSvg) {
         this.inlineSvgTiles?.forEach(d => d.highlightElement(null, null));
      }
   }

   /**
    * Mirror an area/line series hover (reported by the tile under the cursor) onto every tile so
    * the hovered series stays highlighted across a split chart's separate SVGs. A non-null color
    * applies immediately; a clear is debounced so moving the cursor between adjacent tiles does
    * not flash the dim off. A null from a tile that is no longer the active source is ignored —
    * when crossing a tile boundary the leave (null) and enter (color) can arrive in either order.
    */
   onSeriesDimChange(color: string | null, tile: ChartTile): void {
      if(color != null) {
         if(this.seriesDimClearTimer !== null) {
            clearTimeout(this.seriesDimClearTimer);
            this.seriesDimClearTimer = null;
         }

         this.activeDimTile = tile;
         this.inlineSvgTiles?.forEach(d => d.setExternalSeriesDim(color));
      }
      else if(tile === this.activeDimTile && this.seriesDimClearTimer === null) {
         this.seriesDimClearTimer = setTimeout(() => {
            this.seriesDimClearTimer = null;
            this.activeDimTile = null;
            this.inlineSvgTiles?.forEach(d => d.setExternalSeriesDim(null));
         }, 150);
      }
   }

   /**
    * Mirror a relation/tree node hover across a split chart's tiles. The hovered tile only reports
    * the node id; neighbours are resolved from the edge graph merged across every tile (a node's
    * neighbours may be rendered in sibling tiles), then each tile highlights its share. Clearing is
    * debounced and guarded by the active source tile, mirroring onSeriesDimChange — the leave (null)
    * and the next enter (id) can arrive in either order when crossing a tile boundary.
    */
   onRelationHover(nodeId: string | null, tile: ChartTile): void {
      if(nodeId != null) {
         if(this.relationDimClearTimer !== null) {
            clearTimeout(this.relationDimClearTimer);
            this.relationDimClearTimer = null;
         }

         const adjacency = this.buildRelationAdjacency();
         const activeIds = new Set<string>([nodeId]);
         adjacency.get(nodeId)?.forEach(n => activeIds.add(n));
         this.activeRelationTile = tile;
         this.inlineSvgTiles?.forEach(d => d.setExternalRelationHighlight(activeIds, nodeId));
      }
      else if(tile === this.activeRelationTile && this.relationDimClearTimer === null) {
         this.relationDimClearTimer = setTimeout(() => {
            this.relationDimClearTimer = null;
            this.activeRelationTile = null;
            this.inlineSvgTiles?.forEach(d => d.setExternalRelationHighlight(null, null));
         }, 150);
      }
   }

   // Merge every tile's relation edges into one undirected adjacency map. Each tile holds only its
   // own slice of the graph, so neighbour resolution must span all tiles. Rebuilt per node change
   // (emitRelationHover dedups, so this is not per-mousemove) to stay correct after data refresh.
   private buildRelationAdjacency(): Map<string, Set<string>> {
      const adjacency = new Map<string, Set<string>>();

      const link = (a: string, b: string) => {
         let set = adjacency.get(a);
         if(!set) {
            set = new Set();
            adjacency.set(a, set);
         }
         set.add(b);
      };

      this.inlineSvgTiles?.forEach(d => {
         for(const edge of d.getRelationEdges()) {
            link(edge.source, edge.target);
            link(edge.target, edge.source);
         }
      });

      return adjacency;
   }

   public updateChartObject(oldObj?: Plot): void {
      if(this.titlesSrcIsSame(oldObj, this.chartObject)) {
         for(let i = 0; i < oldObj.tiles.length; i++) {
            if(oldObj.tiles[i]?.loaded) {
               this.chartObject.tiles[i].loaded = true;
            }
         }
      }

      if(this.chartObject && (!this.chartObject.tiles || this.chartObject.tiles.length == 0)) {
         this.fireOnLoad();
      }

      // Plot replaced: referenceLineCanvas isn't auto-cleared, and the snap
      // index is keyed on Plot identity so it must be invalidated explicitly.
      if(oldObj && oldObj !== this.chartObject) {
         this.clearSnapGuideline();
         this.snapXTicksFor = null;
      }

      const context = this.getContext();

      if(context) {
         this.chartService.clearCanvas(context);

         if(this.chartSelection && this.chartSelection.regions &&
            this.chartSelection.chartObject &&
            this.chartSelection.chartObject.areaName === "plot_area" &&
            this.chartSelection.chartObject === this.chartObject)
         {
            ChartTool.drawRegions(this.getContext(), this.chartSelection.regions,
                                  this.canvasX, this.canvasY, this.viewsheetScale);
         }
      }
   }

   private titlesSrcIsSame(plot1: Plot, plot2: Plot): boolean {
      if(!plot1 || !plot2 || plot1.tiles?.length != plot2.tiles?.length) {
         return false;
      }

      for(let i = 0; i < plot1.tiles.length; i++) {
         if(this.getSrc(plot1.tiles[i], this.container, true) !==
            this.getSrc(plot2.tiles[i], this.container, true))
         {
            return false;
         }
      }

      return true;
   }

   /**
    * Emit the scroll amount to chart area for synchronized scrolling
    *
    * @param event {UIEvent} the event that contains the scroll amount
    */
   onScroll(event: any): void {
      event.preventDefault();

      // if scrolltop/left are not in sync, emit event
      if(this.scrollTop != event.target.scrollTop || this.scrollLeft != event.target.scrollLeft) {
         this.scrollArea.emit(event);
      }
   }

   protected get canvasX(): number {
      return this.scrollLeft;
   }

   protected get canvasY(): number {
      return this.scrollTop;
   }

   public getBackground(): SafeStyle {
      if(this.chartObject.geoPadding == null) {
         return null;
      }

      const src = this.getSrc(this.chartObject.tiles[0]);
      return (src + "").replace("/plot_area/", "/plot_background/");
   }

   public getSrc(tile: ChartTile, container: any = null, ignoreLoadEvent: boolean = false): SafeStyle {
      const src = super.getSrc(tile, container);

      if(!ignoreLoadEvent) {
         let loading = false;

         if(tile.row == 0 && tile.col == 0) {
            if(this.oSrc != src + "") {
               this.oSrc = src + "";

               if(this.isTileVisible(tile)) {
                  loading = true;
                  tile.loaded = false;
                  this.fireOnLoading();
               }
            }
         }

         // if src is same, (load) event won't be called so we need to explicitly fire it
         // to clear the loading icon.
         if(!loading) {
            this.fireOnLoad();
         }
      }

      return src;
   }

   panImgLoaded() {
      this.hideTile = true;
   }

   clearPan() {
      this.hideTile = false;
      this.panX = this.panY = 0;
      this.panBackground = null;
      this.changeRef.detectChanges();
   }

   loading(tile: ChartTile): void {
      tile.loaded = false;
      this.fireOnLoading();
   }

   loaded(status: boolean, tile: ChartTile) {
      this.clearPan();

      if(this.isTileVisible(tile)) {
         tile.loaded = true;

         if(status || !this.isMaxModeHidden()) {
            this.status = status;
            this.fireOnLoad();
         }

         if(!status && !this.destroyed) {
            // if loading image failed, try the url with regular http.get to get the
            // error message. (44119)
            const uri = this.getSrc(this.chartObject.tiles[0], this.container);
            this.http.get(uri + "", { responseType: "text" }).subscribe(
               data => {},
               err => {
                  if(this.destroyed) {
                     return;
                  }

                  if(this.contextProvider.embed) {
                     console.error(err);
                  }
                  else {
                     this.debounceService.debounce("chart-plot-error-" + uri, () => {
                        if(!this.destroyed) {
                           ComponentTool.showHttpError("_#(js:Error)", err, this.modal);
                        }
                     }, 1000);
                  }
               }
            );
         }
      }
   }

   clearSelection(): void {
      this.emitFlyover({
         chartObject: null,
         regions: []
      });
   }

   private fireOnLoading(): void {
      for(const tile of this.chartObject.tiles) {
         if(this.isTileVisible(tile) && !(tile as any).loaded) {
            this.onLoading.emit(true);
            break;
         }
      }
   }

   private fireOnLoad(): void {
      let load_done = true;

      for(const tile of this.chartObject.tiles) {
         if(this.isTileVisible(tile) && (tile as any).loaded === false) {
            load_done = false;
         }
      }

      if(load_done) {
         this.onLoad.emit(this.status);
      }
   }

   // double tap to lasso on mobile (mousedown-mouseup-mousedown to start dragging).
   click(event: MouseEvent) {
      if(this.mobile && !this.selectionBoxWithTouch) {
         this.selectionBoxWithTouch = true;
         setTimeout(() => this.selectionBoxWithTouch = false, 500);
      }
   }

   get scrollContainerWidth(): number {
      // 1 pixel is added to account for 0.5 pixel border stroke that bleeds outside plot bounds. (57682)
      return this.chartObject.layoutBounds.width + 1 +
         (this.chartObject.bounds.height > this.chartObject.layoutBounds.height
          ? this.scrollbarWidth : 0);
   }

   get scrollContainerHeight(): number {
      // 1 pixel is added to account for 0.5 pixel border stroke that bleeds outside plot bounds. (57682)
      return this.chartObject.layoutBounds.height + 1 +
         (this.chartObject.bounds.width > this.chartObject.layoutBounds.width
          ? this.scrollbarWidth : 0);
   }

   isMaxModeHidden(): boolean {
      return (<any> this.model)?.sheetMaxMode && !this.model?.maxMode;
   }
}
