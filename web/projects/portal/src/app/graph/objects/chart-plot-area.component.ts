/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
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
   SimpleChanges,
   ViewChild
} from "@angular/core";
import { SafeStyle } from "@angular/platform-browser";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ComponentTool } from "../../common/util/component-tool";
import { GuiTool } from "../../common/util/gui-tool";
import { SelectionBoxEvent } from "../../widget/directive/selection-box.directive";
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
import { ChartService } from "../services/chart.service";
import { ChartObjectAreaBase } from "./chart-object-area-base";
import { Point } from "../../common/data/point";

@Component({
   selector: "chart-plot-area",
   templateUrl: "chart-plot-area.component.html",
   styleUrls: ["chart-plot-area.component.scss"],
   providers: [{
      provide: ChartObjectAreaBase,
      useExisting: ChartPlotArea
   }],
   changeDetection: ChangeDetectionStrategy.OnPush
})
export class ChartPlotArea extends ChartObjectAreaBase<Plot> implements OnChanges {
   @Input() dataTip: string;
   @Input() flyover: boolean;
   @Input() flyOnClick: boolean;
   @Input() scrollbarWidth: number;
   @Input() selectionBoxWithTouch: boolean = false;
   @Input() viewerMode = false;
   @Input() previewMode = false;
   @Input() container: Element;
   @Input() selected: boolean = false;
   @Input() panMode: boolean = false;
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

   private readonly debounceKey: string = "chart_dataTipEvent";

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
               scaleService: ScaleService)
   {
      super(chartService, scaleService);
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
      // no-op
   }

   private emitFlyover(chartSelection: ChartSelection): void {
      const flyoverPayload = Object.assign(chartSelection, {
         context: this.getContext(),
         canvasX: this.canvasX,
         canvasY: this.canvasY
      });
      this.sendFlyover.emit(flyoverPayload);
   }

   private emitTooltip(regions: ChartRegion[]): void {
      const tipInfo = regions
         .filter(region => !!region)
         .sort((a, b) => a.index - b.index)
         .map(region => ({ tipIndex: region.tipIdx, region: region }))
         .find(ti => ti.tipIndex >= 0);
      this.showTooltip.emit(tipInfo);
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
         let eventX = mevent.offsetX + this.scrollLeft;
         let eventY = mevent.offsetY + this.scrollTop;
         let regions: ChartRegion[] = this.getTreeRegions(eventX, eventY);

         let chartSelection = {
            chartObject: this.chartObject,
            regions: regions
         };

         if(this.flyover && !this.flyOnClick) {
            this.emitFlyover(chartSelection);
         }

         if(this.dataTip) {
            this.debounceService.debounce(this.debounceKey, () => {
               this.showDataTip.emit(chartSelection);
            }, 100, []);
         }
         else {
            this.emitTooltip(regions);
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
            else if(!hasLinkPoint) {
               this.changeCursor0("inherit");
            }
         }

         if(this.chartObject.showReferenceLine) {
            const context = this.referenceLineCanvas.nativeElement.getContext("2d");
            const region = regions.find((r) => r && ChartTool.refLine(this.model, r));
            this.chartService.clearCanvas(context);

            if(region) {
               ChartTool.drawReferenceLine(context, region, this.canvasX, this.canvasY);
            }
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
      const x = event.clientX - objLeft + this.scrollLeft;
      const y = event.clientY - objTop + this.scrollTop;
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

      if(this.dataTip && !this.mobile) {
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
   }

   public updateChartObject(): void {
      const context = this.getContext();

      if(context) {
         this.chartService.clearCanvas(context);

         if(this.chartSelection && this.chartSelection.regions &&
            this.chartSelection.chartObject &&
            this.chartSelection.chartObject.areaName === "plot_area")
         {
            ChartTool.drawRegions(this.getContext(), this.chartSelection.regions,
                                  this.canvasX, this.canvasY, this.viewsheetScale);
         }
      }
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

   public getSrc(tile: ChartTile, container: any = null): SafeStyle {
      const src = super.getSrc(tile, container);
      let loading = false;

      if(tile.row == 0 && tile.col == 0) {
         if(this.oSrc != src + "") {
            this.oSrc = src + "";

            if(this.isTileVisible(tile)) {
               loading = true;
               this.onLoading.emit(true);
            }
         }
      }

      // if src is same, (load) event won't be called so we need to explicitly fire it
      // to clear the loading icon.
      if(!loading) {
         this.onLoad.emit(this.status);
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

   loaded(status: boolean, tile: ChartTile) {
      this.clearPan();

      if(this.isTileVisible(tile)) {
         tile.loaded = true;

         if(status || !this.isMaxModeHidden()) {
            this.status = status;
            this.onLoad.emit(status);
         }

         if(!status) {
            // if loading image failed, try the url with regular http.get to get the
            // error message. (44119)
            const uri = this.getSrc(this.chartObject.tiles[0], this.container);
            this.http.get(uri + "", { responseType: "text" }).subscribe(
               data => {},
               err => ComponentTool.showHttpError("_#(js:Error)", err, this.modal)
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

   // double tap to lasso on mobile (mousedown-mouseup-mousedown to start dragging).
   click(event: MouseEvent) {
      if(this.mobile && !this.selectionBoxWithTouch) {
         this.selectionBoxWithTouch = true;
         setTimeout(() => this.selectionBoxWithTouch = false, 500);
      }
   }

   get scrollContainerWidth(): number {
      return this.chartObject.layoutBounds.width +
         (this.chartObject.bounds.height > this.chartObject.layoutBounds.height
          // 1 pixel is added to the image to account for 0.5 pixel border line. (57682)
          ? this.scrollbarWidth + 1 : 0);
   }

   get scrollContainerHeight(): number {
      return this.chartObject.layoutBounds.height +
         (this.chartObject.bounds.width > this.chartObject.layoutBounds.width
          ? this.scrollbarWidth + 1 : 0);
   }

   isMaxModeHidden(): boolean {
      return (<any> this.model)?.sheetMaxMode && !this.model?.maxMode;
   }
}
