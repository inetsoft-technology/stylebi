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
import {
   ChangeDetectionStrategy,
   ChangeDetectorRef,
   Component,
   ElementRef,
   EventEmitter,
   Input,
   Output,
   Renderer2,
   ViewChild,
   HostListener, OnChanges, SimpleChanges
} from "@angular/core";
import { ScaleService } from "../../widget/services/scale/scale-service";
import { DebounceService } from "../../widget/services/debounce.service";
import { AreaResizeInfo } from "../model/area-resize-info";
import { Axis } from "../model/axis";
import { ChartAreaName } from "../model/chart-area-name";
import { ChartDrillInfo } from "../model/chart-drill-info";
import { ChartModel } from "../model/chart-model";
import { ChartRegion } from "../model/chart-region";
import { ChartSelection } from "../model/chart-selection";
import { ChartTool } from "../model/chart-tool";
import { ChartService } from "../services/chart.service";
import { ChartObjectAreaBase } from "./chart-object-area-base";
import { GuiTool } from "../../common/util/gui-tool";
import { ContextProvider } from "../../vsobjects/context-provider.service";
import { TooltipInfo } from "../model/tooltip-info";
import { VSChartModel } from "../../vsobjects/model/vs-chart-model";
import { FlyoverInfo } from "../model/flyover-info";
import { ChartTile } from "../model/chart-tile";

@Component({
   selector: "chart-axis-area",
   templateUrl: "chart-axis-area.component.html",
   styleUrls: ["chart-axis-area.component.scss"],
   providers: [{
      provide: ChartObjectAreaBase,
      useExisting: ChartAxisArea
   }],
   changeDetection: ChangeDetectionStrategy.OnPush
})
export class ChartAxisArea extends ChartObjectAreaBase<Axis> implements OnChanges {
   @Input() scrollbarWidth: number;
   @Input() hideSortIcon: boolean = false;
   @Input() supportHyperlink: boolean;
   @Input() resizeEnable: boolean;
   @Input() container: Element;
   @Input() onTitle: boolean = false;
   @Input() dateComparisonDefined: boolean = false;
   @Input() flyover: boolean;
   @Input() flyOnClick: boolean;
   @Input() dataTip: string;
   @Output() showTooltip = new EventEmitter<TooltipInfo>();
   @Output() selectRegion = new EventEmitter();
   @Output() startAxisResize = new EventEmitter<ChartSelection>();
   @Output() changeCursor = new EventEmitter<string>();
   @Output() showHyperlink = new EventEmitter<MouseEvent>();
   @Output() sortAxis = new EventEmitter<void>();
   @Output() drill = new EventEmitter<ChartDrillInfo>();
   @Output() brushChart = new EventEmitter();
   @Output() scrollAxis: EventEmitter<any> = new EventEmitter<any>();
   @Output() sendFlyover = new EventEmitter<FlyoverInfo>();
   @Output() showDataTip = new EventEmitter<ChartSelection>();
   @Output() onLoad = new EventEmitter<boolean>();
   @ViewChild("axisArea") axisArea: ElementRef;
   private mouseUpResizeListener: () => void;

   isMouseDown: boolean = false;
   eventXdown: number;
   eventYdown: number;
   drillVisible: boolean = false;
   drillHover: boolean = false;
   isShowPointer: boolean;
   resizeCursor: string = null;
   resizeIdx: number = 0;
   altDown = false;
   private readonly debounceKey: string = "chart_dataTipEvent";

   @Input() set model(model: ChartModel) {
      this._model = model;
      this.updateCanvas();
      this.updateRegionTree();
   }

   get model(): ChartModel {
      return this._model;
   }

   isActionVisibleInViewer(actionName: string): boolean {
      let model: VSChartModel = <VSChartModel> this._model;
      return !(model?.actionNames && model.actionNames?.indexOf(actionName) >= 0);
   }

   @Input()
   set scrollTop(value: number) {
      if(this.chartObject && this.chartObject.axisType == "y" && this.scrollTop != value) {
         super.scrollTop = value;
         this.updateCanvas();
      }
   }

   get scrollTop(): number {
      return super.scrollTop;
   }

   @Input()
   set scrollLeft(value: number) {
      if(this.chartObject && this.chartObject.axisType == "x" && this.scrollLeft != value) {
         super.scrollLeft = value;
         this.updateCanvas();
      }
   }

   get scrollLeft(): number {
      return super.scrollLeft;
   }

   private updateCanvas(): void {
      const context = this.getContext();

      if(context) {
         this.chartService.clearCanvas(context);
         this.debounceService.debounce("axis.scrolled", () => this.ngAfterViewInit(), 500, []);
      }
   }

   constructor(protected chartService: ChartService,
               private renderer: Renderer2,
               private debounceService: DebounceService,
               private changeRef: ChangeDetectorRef,
               private contextProvider: ContextProvider,
               scaleService: ScaleService)
   {
      super(chartService, scaleService);
   }

   ngOnChanges(changes: SimpleChanges): void {
      let updateCanvas = false;

      if(changes.hasOwnProperty("scrollTop")) {
         if(this.chartObject && this.chartObject.axisType == "y") {
            updateCanvas = true;
         }
      }

      if(changes.hasOwnProperty("scrollLeft")) {
         if(this.chartObject && this.chartObject.axisType == "x") {
            updateCanvas = true;
         }
      }

      if(updateCanvas) {
         this.updateCanvas();
      }
   }

   get vsWizard(): boolean {
      return this.contextProvider.vsWizard;
   }

   get vsWizardPreview(): boolean {
      return this.contextProvider.vsWizardPreview;
   }

   protected cleanup(): void {
      this.clearDocMouseListener();
   }

   get cursor(): string {
      if(this.chartObject.axisFields && this.chartObject.axisFields.length == 0) {
         return null;
      }

      let cursor: string;

      switch(this.getAreaName()) {
         case "bottom_x_axis":
         case "top_x_axis":
            cursor = "row-resize";
            break;
         case "right_y_axis":
         case "left_y_axis":
            cursor = "col-resize";
            break;
         default:
            cursor = null;
            break;
      }

      return cursor;
   }

   showDrill(): void {
      if(!this.drillVisible) {
         this.drillVisible = true;
         this.changeRef.detectChanges();
      }
   }

   hideDrill(leaveDrillArea: boolean): void {
      if(leaveDrillArea) {
         this.drillHover = false;
      }

      if(this.drillVisible && !this.drillHover) {
         this.drillVisible = false;
         this.changeRef.detectChanges();
      }
   }

   hoverOverDrill(): void {
      if(!this.drillHover) {
         this.drillHover = true;
         this.changeRef.detectChanges();
      }
   }

   private emitTooltip(regions: ChartRegion[]): void {
      const tipInfo = regions
         .filter(r => r != null)
         .map(region => ({ tipIndex: region.tipIdx, region: region }))
         .find(ti => ti.tipIndex >= 0);
      this.showTooltip.emit(tipInfo);
   }

   private emitCursor(eventX: number, eventY: number): void {
      let cursor = null;
      const margin = 5;
      this.resizeIdx = 0;

      switch(this.getAreaName()) {
      case "bottom_x_axis":
         if(eventY < margin) {
            cursor = this.cursor;
         }
         break;
      case "top_x_axis":
         for(let i = this.chartObject.axisSizes.length - 1; i >= 0; i--) {
            if(Math.abs(eventY - this.chartObject.axisSizes[i]) < margin) {
               cursor = this.cursor;
               this.resizeIdx = i;
               break;
            }

            eventY -= this.chartObject.axisSizes[i];
         }
         break;
      case "right_y_axis":
         if(eventX < margin) {
            cursor = this.cursor;
         }
         break;
      case "left_y_axis":
         for(let i = 0; i < this.chartObject.axisSizes.length; i++) {
            if(Math.abs(eventX - this.chartObject.axisSizes[i]) < margin) {
               let period = false;

               if(i == 0) {
                  period = this.chartObject.regions.some(r => r.period);
               }

               // resizing of period axis is not supported
               if(!period) {
                  cursor = this.cursor;
                  this.resizeIdx = i;
               }

               break;
            }

            eventX -= this.chartObject.axisSizes[i];
         }
         break;
      }

      if(this.resizeCursor != cursor) {
         this.resizeCursor = this.resizeEnable ? cursor : null;
         this.changeCursor.emit(this.resizeCursor);
      }
   }

   private emitDrill(index: number, drillOp: string): void {
      if(this.chartObject.axisType == "x") {
         index = this.chartObject.axisOps.length - index - 1;
      }

      let axisType = this.chartObject.axisType;
      let field = this.chartObject.axisFields[index];
      this.drill.emit({
         axisType: axisType,
         field: field,
         drillOp: drillOp
      });
   }

   private emitFlyover(chartSelection: ChartSelection): void {
      let regions = chartSelection.regions;
      let containsDim = this.selectionContaisDim(chartSelection);

      if(!containsDim && regions && regions?.length != 0) {
         return;
      }

      const flyoverPayload = Object.assign(chartSelection, {
         context: this.getContext(),
         canvasX: this.canvasX,
         canvasY: this.canvasY
      });

      this.sendFlyover.emit(flyoverPayload);
   }

   private emitDataTip(chartSelection: ChartSelection): void {
      let regions = chartSelection.regions;
      let containsDim = this.selectionContaisDim(chartSelection);

      if(!containsDim && regions && regions?.length != 0) {
         return;
      }

      this.showDataTip.emit(chartSelection);
   }

   private selectionContaisDim(chartSelection: ChartSelection): boolean {
      let regions = chartSelection.regions;

      for(let region of regions) {
         let dimensionName = ChartTool.getDim(this.model, region);

         if(!!dimensionName) {
            return true;
         }
      }

      return false;
   }

   drillDown(index: number): void {
      this.emitDrill(index, "+");
   }

   drillUp(index: number): void {
      this.emitDrill(index, "-");
   }

   clickSort(event: MouseEvent): void {
      event.stopPropagation();
      this.sortAxis.emit();
   }

   /**
    * Mousemove emit tooltip and cusor style
    */
   onMove(event: MouseEvent): void {
      let target: any = event.target;

      if(!!target && target.className == "sort-ascending-icon") {
         event.stopPropagation();
         return;
      }

      if(this.resizeHandling()) {
         return;
      }

      const eventX = event.offsetX + this.scrollLeft;
      const eventY = event.offsetY + this.scrollTop;
      const regions = this.getTreeRegions(eventX, eventY);

      this.isLinkPointer(regions);
      let chartSelection = {
         chartObject: this.chartObject,
         regions: regions
      };

      if(this.dataTip) {
         this.debounceService.debounce(this.debounceKey, () => {
            this.emitDataTip(chartSelection);
         }, 100, []);
      }
      else {
         this.emitTooltip(regions);
      }

      this.emitCursor(eventX, eventY);

      if(this.flyover && !this.flyOnClick) {
         this.emitFlyover(chartSelection);
      }
   }

   @HostListener("document:keydown.alt", ["$event"])
   onAltDown(event) {
      this.altDown = true;
   }

   @HostListener("document:keyup.alt", ["$event"])
   onAltUp(event: KeyboardEvent) {
      this.altDown = false;
      event.preventDefault();
   }

   private resizeHandling(): boolean {
      return this.isMouseDown && !!this.resizeCursor;
   }

   private isLinkPointer(regions: ChartRegion[]): void {
      let findlink = null;

      if(!!this.links) {
         this.links.forEach((link) => {
         let linkPoint: string[] = link.split("/");
            findlink = linkPoint && linkPoint.length == 3 && regions.some(region => {
               let row = Number(linkPoint[2]);
               let col = Number(linkPoint[1]);
               let currCol = ChartTool.colIdx(this.model, region);

               return col == -1 && region.rowIdx == row || row == -1 && currCol == col ||
                   col == currCol &&  region.rowIdx == row;
            });
         });
      }

      let hyperlinkRegion: ChartRegion =
         regions.find(r => !!r && !!r.hyperlinks && r.hyperlinks.length > 0);
      this.isShowPointer = !!findlink || (!!hyperlinkRegion && !!this.supportHyperlink);
   }

   onDown(event: MouseEvent): void {
      if(event.type === "pointerdown" ||
         event.type === "mousedown" && !GuiTool.supportPointEvent())
      {
         this.isMouseDown = true;
         this.eventYdown = event.clientY;
         this.eventXdown = event.clientX;

         if(this.resizeHandling()) {
            const regions = this.getTreeRegions(this.eventXdown, this.eventYdown);
            this.startAxisResize.emit(this.createAxisResizeInfo(regions));

            if(!this.mouseUpResizeListener) {
               this.mouseUpResizeListener = this.renderer.listen(
                  "document", "mouseup", (evt: MouseEvent) => {
                     this.clearResizeStatus();
                  });
            }
         }
      }
   }

   onUp(event: MouseEvent): void {
      if(this.isMouseDown && event.clientX == this.eventXdown && event.clientY == this.eventYdown &&
         (event.type === "pointerup" || event.type === "contextmenu"
         || event.type === "mouseup" && !GuiTool.supportPointEvent()))
      {
         let clientRect = this.objectCanvas.nativeElement.getBoundingClientRect();
         let objLeft = clientRect.left;
         let objTop = clientRect.top;
         let x1 = (event.clientX - objLeft) / this.viewsheetScale + this.canvasX;
         let y1 = (event.clientY - objTop) / this.viewsheetScale + this.canvasY;

         const regions = this.getTreeRegions(x1, y1, x1, y1)
            .filter((region) => !!region && !region.noselect);

         // Don't do anything when right clicking on a selected region.
         if(event.which != 3 ||
            !ChartTool.isRegionSelected(this.chartSelection, this.chartObject.areaName,
                                        regions[0]))
         {
            // Select Region
            this.selectRegion.emit({
               chartObject: this.chartObject,
               context: this.getContext(),
               canvasX: this.canvasX,
               canvasY: this.canvasY,
               regions: regions,
               isCtrl: event.ctrlKey || event.metaKey,
               isShift: event.shiftKey
            });

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
         }
      }

      this.clearResizeStatus();
   }

   onClick(event: MouseEvent): void {
      const eventX = event.offsetX + this.scrollLeft;
      const eventY = event.offsetY + this.scrollTop;
      const regions = this.getTreeRegions(eventX, eventY);
      // Show hyperlinks
      let hyperlinkRegion: ChartRegion;

      // Only allow a hyperlink to show if we don't click + drag
      hyperlinkRegion = regions.find((region) => region && region.hyperlinks != null);

      if(this.isShowPointer || hyperlinkRegion) {
         this.showHyperlink.emit(event);
      }
   }

   private clearResizeStatus(): void {
      this.changeCursor.emit(this.resizeCursor = null);
      this.isMouseDown = false;
      this.clearDocMouseListener();
   }

   private clearDocMouseListener(): void {
      if(!!this.mouseUpResizeListener) {
         this.mouseUpResizeListener();
         this.mouseUpResizeListener = null;
      }
   }

   onDblClick(event: MouseEvent): void {
      this.brushChart.emit();
   }

   /**
    * When the mouse leaves a chart object reset the tooltip and cursor
    */
   onLeave(event: MouseEvent): void {
      if(!this.resizeHandling()) {
         this.changeCursor.emit(this.resizeCursor = null);
      }

      if(this.drillVisible) {
         // don't hide icon when moving mouse onto the icons
         if((<any> event.relatedTarget)?.classList?.contains("floating-icon")) {
            return;
         }

         if(this.dataTip) {
            const cls = "current-datatip-" + this.dataTip.replace(/ /g, "_");
            const tipElement: HTMLElement = document.getElementsByClassName(cls)[0] as HTMLElement;

            if(!tipElement || !GuiTool.isMouseIn(tipElement, event) &&
               !GuiTool.parentContainsClass((event as any).toElement, cls))
            {
               let selection = {
                  chartObject: this.chartObject,
                  regions: []
               };

               this.debounceService.debounce(this.debounceKey, () => {
                  this.emitDataTip(selection);
               }, 100, []);
            }
         }

         // title may be on top of the axis. (55606)
         if(GuiTool.isMouseIn(this.axisArea.nativeElement, event, -1)) {
            return;
         }

         this.hideDrill(false);
         this.showTooltip.emit(null);
         this.changeRef.detectChanges();
      }

      if(this.flyover && !this.flyOnClick) {
         this.emitFlyover({
            chartObject: this.chartObject,
            regions: []
         });
      }
   }

   getDrillIconPosition(index: number): any {
      return this.chartObject.axisSizes.slice(0, index).reduce((a, b) => a + b +
                                                               (Math.abs(18 % Math.min(b , 18))), 0);
   }

   getDrillIconLeft(i: number): number {
      if(this.chartObject.axisType == "y") {
         return this.getDrillIconPosition(i);
      }
      else if(this.chartObject.axisType == "x") {
         return -Math.min(this.chartObject.layoutBounds.x, 18);
      }

      return null;
   }

   public updateChartObject(): void {
      if(this.chartObject.axisType == "x") {
         this.chartObject.axisOps = this.chartObject.axisOps.reverse();
      }
   }

   /**
    * Emit the scroll amount to chart area for synchronized scrolling
    *
    * @param event {UIEvent} the event that contains the scroll amount
    */
   onScroll(event: any): void {
      event.preventDefault();

      if((this.chartObject.axisType == "x" && this.scrollLeft != event.target.scrollLeft) ||
         (this.chartObject.axisType == "y" && this.scrollTop != event.target.scrollTop))
      {
         this.scrollAxis.emit(event);
      }
   }

   /**
    * Gather needed information for resizing a chart axis.
    * Logic taken from ResizeGrid.as createAxisRegions()
    *
    * @param regions the selected axis regions
    * @returns the AreaResizeInfo object
    */
   private createAxisResizeInfo(regions: ChartRegion[]): AreaResizeInfo {
      // minimum end position of cursor, equal start of axis
      let min: number;
      // max end position of cursor, equal to edge of chart content area
      let max: number;
      let areaName: ChartAreaName = this.getAreaName();
      let axisBounds = this.chartObject.layoutBounds;
      let vertical = false;

      if(areaName == "bottom_x_axis") {
         min = this.model.contentBounds.y + 1;
         max = axisBounds.y + axisBounds.height + 2;
      }
      else if(areaName == "top_x_axis") {
         min = axisBounds.y - 2;
         max = this.model.contentBounds.y + this.model.contentBounds.height - 1;
      }
      else if(areaName == "left_y_axis") {
         min = axisBounds.x - 2;
         max = this.model.contentBounds.x + this.model.contentBounds.width - 1;
         vertical = true;
      }
      else if(areaName == "right_y_axis") {
         min = this.model.contentBounds.x + 1;
         max = axisBounds.x + axisBounds.width + 2;
         vertical = true;
      }

      return {
         chartObject: this.chartObject,
         fields: this.chartObject.axisFields,
         resizeIdx: this.resizeIdx,
         regions: regions,
         minPosition: min,
         maxPosition: max,
         vertical: vertical,
         type: "axis"
      };
   }

   protected get canvasX(): number {
      return this.scrollLeft ? this.scrollLeft : 0;
   }

   protected get canvasY(): number {
      return this.scrollTop ? this.scrollTop : 0;
   }

   get showSortIcon(): boolean {
      return !this.hideSortIcon && (this.drillVisible || this.onTitle) &&
         (!!this.chartObject ? !this.chartObject.sortFieldIsCalc : true);
   }

   get minWidth(): number {
      if(this.showSortIcon) {
         return Math.max(this.chartObject.layoutBounds.width, 20);
      }

      return this.chartObject.layoutBounds.width;
   }

   loaded(status: boolean, tile: ChartTile) {
      if(this.isTileVisible(tile)) {
         this.onLoad.emit(status);
      }
   }

   getSortIconTop(axisOps: string[]): number {
      const drillIconHeight: number = this.chartObject.axisType == "y" ? 22 : 0;
      const drillIcon = axisOps.findIndex(a => a !== "") != -1;

      if(this.drillVisible && drillIcon) {
         return this.chartObject.layoutBounds.height - drillIconHeight - 20;
      }
      else {
         return this.chartObject.layoutBounds.height - drillIconHeight;
      }
   }
}
