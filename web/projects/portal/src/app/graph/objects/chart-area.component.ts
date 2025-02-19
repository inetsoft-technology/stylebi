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
   ChangeDetectorRef,
   Component,
   ElementRef,
   EventEmitter,
   Input,
   OnChanges,
   OnDestroy, OnInit,
   Output,
   QueryList,
   SimpleChanges,
   ViewChild,
   ViewChildren
} from "@angular/core";
import { Subject, Subscription } from "rxjs";
import { AssetEntry } from "../../../../../shared/data/asset-entry";
import { Tool } from "../../../../../shared/util/tool";
import { ChartBindingModel } from "../../binding/data/chart/chart-binding-model";
import { AssetUtil } from "../../binding/util/asset-util";
import { BaseFormatModel } from "../../common/data/base-format-model";
import { ChartViewDropTarget } from "../../common/data/dnd-transfer";
import { DragEvent } from "../../common/data/drag-event";
import { Point } from "../../common/data/point";
import { Rectangle, Rectangular } from "../../common/data/rectangle";
import { DndService } from "../../common/dnd/dnd.service";
import { GraphTypes } from "../../common/graph-types";
import { ChartConstants } from "../../common/util/chart-constants";
import { GuiTool } from "../../common/util/gui-tool";
import { VSChartModel } from "../../vsobjects/model/vs-chart-model";
import { VSFormatModel } from "../../vsobjects/model/vs-format-model";
import { InteractInfo } from "../../widget/resize/element-interact.directive";
import { ScaleService } from "../../widget/services/scale/scale-service";
import { AreaResizeInfo } from "../model/area-resize-info";
import { Axis } from "../model/axis";
import { ChartAreaName } from "../model/chart-area-name";
import { ChartDrillInfo } from "../model/chart-drill-info";
import { ChartModel } from "../model/chart-model";
import { ChartObject } from "../model/chart-object";
import { ChartRegion } from "../model/chart-region";
import { ChartSelection } from "../model/chart-selection";
import { ChartTool } from "../model/chart-tool";
import { FlyoverData } from "../model/flyover-data";
import { FlyoverInfo } from "../model/flyover-info";
import { Legend } from "../model/legend";
import { LegendContainer } from "../model/legend-container";
import { LegendResizeInfo } from "../model/legend-resize-info";
import { PlotScaleInfo } from "../model/plot-scale-info";
import { TooltipInfo } from "../model/tooltip-info";
import { ChartService } from "../services/chart.service";
import { ChartObjectAreaBase } from "./chart-object-area-base";
import { ChartPlotArea } from "./chart-plot-area.component";
import { Plot } from "../model/plot";
import { PagingControlModel } from "../../vsobjects/model/paging-control-model";
import { PagingControlService } from "../../common/services/paging-control.service";

@Component({
   selector: "chart-area",
   templateUrl: "chart-area.component.html",
   styleUrls: ["chart-area.component.scss"],
})
export class ChartArea implements OnInit, OnChanges, OnDestroy {
   @Input() zIndex: number;
   @Input() supportHyperlink = true;
   @Input() emptyChart = false;
   @Input() backgroundColor: string;
   @Input() urlPrefix: string;
   @Input() format: BaseFormatModel;
   @Input() titleFormat: BaseFormatModel;
   @Input() hideSortIcon: boolean;
   @Input() showEmptyArea: boolean = false;
   @Input() noChartData: boolean = false;
   @Input() plotScaleInfo: PlotScaleInfo;
   @Input() titleVisible: boolean;
   @Input() viewerMode = false;
   @Input() previewMode = false;
   @Input() isBinding = false;
   @Input() container: Element;
   @Input() modelTS: number = 0; // used to force change detection
   @Input() isResizeVisible: (name: string) => boolean;
   @Input() links: string[]; //link locations
   @Input() bindingModel: ChartBindingModel;
   @Input() isVSChart: boolean = false;
   @Input() isDataTip: boolean = false;
   @Input() pan: boolean = false;
   @Input() dateComparisonDefined: boolean = false;
   onTitle: boolean = false;

   panMode: boolean = false;
   private pinchStart = 0;
   private pinchMove = 0;
   _model: ChartModel;
   mobileDevice: boolean = GuiTool.isMobileDevice();
   imageError: boolean = false;
   _selected: boolean;
   private clearCanvasSubscription: Subscription;
   private scrollTopSubscription: Subscription;
   private scrollLeftSubscription: Subscription;
   private flyoverApplied = false;

   @Input() set model(model: ChartModel) {
      this._model = model;
      this.imageError = false;

      if(this.clearCanvasSubscription) {
         this.clearCanvasSubscription.unsubscribe();
         this.clearCanvasSubscription = null;
      }

      if(model) {
         if(model.clearCanvasSubject == null) {
            model.clearCanvasSubject = new Subject<any>();
            this.clearCanvasSubscription = model.clearCanvasSubject.subscribe((clearSelectionFlag) => {
               if(this.chartObjectRef) {
                  this.chartObjectRef.forEach((chartObject) => {
                     chartObject.clearCanvas();

                     if(!!clearSelectionFlag && (chartObject instanceof ChartPlotArea)) {
                        const plotArea = chartObject as ChartPlotArea;

                        // don't clear flyover if flyover is not applied. otherwise if chart1 and
                        // chart2 are each other's flyover target, selecting on one chart will
                        // cause the other to send a clear flyover event, causing multiple
                        // flyover events on a single selection. (60159)
                        if(plotArea.flyover && plotArea.flyOnClick && this.flyoverApplied) {
                           plotArea.clearSelection();
                           this.flyoverApplied = false;
                        }
                     }
                  });
               }
            });
         }

         if(model.chartSelection == null) {
            model.chartSelection = {
               chartObject: null,
               regions: null
            };
         }

         if(model.chartSelection.chartObject == null && model.chartSelection.regions == null) {
            model.clearCanvasSubject.next(null);
         }

         // When vs chart model change, need to re-init drop regions.
         this.initDropRegions();

         this.scrollTopSubscription = this.pagingControlService.scrollTop().subscribe(changed => {
            if(this.pagingControlService.getCurrentAssembly() === this.getAssemblyName()) {
               this.scrollTop = changed;
               this.showPagingControl();
            }
         });

         this.scrollLeftSubscription = this.pagingControlService.scrollLeft().subscribe(changed => {
            if(this.pagingControlService.getCurrentAssembly() === this.getAssemblyName()) {
               this.scrollLeft = changed;
               this.showPagingControl();
            }
         });
      }
   }

   get model(): ChartModel {
      return this._model;
   }

   @Input() set selected(selected: boolean) {
      this._selected = selected;

      if(selected) {
         this.showPagingControl();
      }
   }

   get selected(): boolean {
      return this._selected;
   }

   get vsChartModel(): VSChartModel {
      return this._model as VSChartModel;
   }

   paintNoDataChart(): boolean {
      if(this.isBinding) {
         return false;
      }

      if(this.previewMode || this.viewerMode) {
         return this.noChartData;
      }

      return false;
   }

   @Output() onSelectRegion = new EventEmitter<ChartSelection>();
   @Output() onDrill = new EventEmitter<ChartDrillInfo>();
   @Output() onSendFlyover = new EventEmitter<FlyoverData>();
   @Output() onShowDataTip = new EventEmitter<any>();
   @Output() onEndAxisResize = new EventEmitter<any>();
   @Output() onEndLegendResize = new EventEmitter<any>();
   @Output() onEndLegendMove = new EventEmitter<any>();
   @Output() onSortAxis = new EventEmitter<Axis>();
   @Output() onBrushChart = new EventEmitter<any>();
   @Output() onShowHyperlink = new EventEmitter<MouseEvent>();
   @Output() onScroll = new EventEmitter<Point>();
   @Output() onError = new EventEmitter<any>();
   @Output() onLoad = new EventEmitter<any>();
   @Output() onLoading = new EventEmitter<any>();
   @Output() onZoomIn = new EventEmitter<number>();
   @Output() onZoomOut = new EventEmitter<number>();
   @Output() onClearPanZoom = new EventEmitter<any>();
   @Output() onPanMap = new EventEmitter<Point>();
   @ViewChildren(ChartObjectAreaBase) chartObjectRef: QueryList<ChartObjectAreaBase<any>>;
   @ViewChild("dropRegionCanvas") dropRegionCanvas: ElementRef;
   @ViewChild("chartContainer") _chartContainer: ElementRef;
   @ViewChild("chartPlotArea") chartPlotArea: ChartPlotArea;

   private _clientRect: ClientRect;
   // x top area(contain x top axis and title)
   private xTopRegion: Rectangle;
   // x bottom area(contain x bottom axis)
   private xBottomRegion: Rectangle;
   // y left area(contain y left axis and title)
   private yLeftRegion: Rectangle;
   // y right area(contain y right axis and title)
   private yRightRegion: Rectangle;
   // plot area (used to position scrollbar)
   plotRegion: Rectangle = new Rectangle(0, 0, 0, 0);
   // drop type
   private dropType: number = -1;
   private _plotLoaded = true;
   private _axisLoaded = true;
   private devicePixelRatioMedia: MediaQueryList;

   // Mouse position
   eventXdown: number;
   eventYdown: number;

   // Tooltip Handling
   tooltipString: string = null;
   tooltipTop: number = 0;
   tooltipLeft: number = 0;

   // Area resizer
   areaResizeInfo: AreaResizeInfo = null;
   resizeVertical: boolean;
   resizeLineTop: number;
   resizeLineLeft: number;

   // Mouse cursor changing
   currentCursor: string;

   // Browser independent scrollbar size, scrollbar no displayed on mobile
   scrollbarWidth: number = this.mobileDevice ? 0 : GuiTool.measureScrollbars();
   scrollLeft: number = 0;
   scrollTop: number = 0;

   // Empty chart drag over highlight indicators
   xAxisHover: number = 0;
   yAxisHover: number = 0;

   mouseoverLegendRegion: ChartAreaName = "legend_content";

   get vsFormatModel(): VSFormatModel {
      return this.format as VSFormatModel;
   }

   // The currently selected regions and the chart object in which they are contained
   private get chartSelection(): ChartSelection {
      return this.model ? this.model.chartSelection : null;
   }

   constructor(private chartService: ChartService,
               private dndService: DndService,
               private scaleService: ScaleService,
               private changeDetectorRef: ChangeDetectorRef,
               private pagingControlService: PagingControlService)
   {
   }

   ngOnChanges(changes: SimpleChanges): void {
      if(changes["modelTS"]) {
         this.initDropRegions();

         // binding scrollLeft is ignored if the container is not yet scrollable.
         // we increment a tiny amount after the proper sizes have been applied, and force
         // the scroll to be assigned again.
         setTimeout(() => {
            if(this.scrollLeft != 0) {
               this.scrollLeft += 0.00000001;
            }

            if(this.scrollTop != 0) {
               this.scrollTop += 0.00000001;
            }
         });
      }
   }

   ngOnInit() {
      window.addEventListener("resize", this.onResize);

      this.devicePixelRatioMedia = window.matchMedia(`(resolution: ${window.devicePixelRatio}dppx)`);
      this.devicePixelRatioMedia.addEventListener("change", this.onDevicePixelRatioChange);
   }

   private onResize = (): void => {
      GuiTool.resetScrollbarWidth();
      this.scrollbarWidth = this.mobileDevice ? 0 : GuiTool.measureScrollbars();
      this.changeDetectorRef.detectChanges();
   };

   private onDevicePixelRatioChange = (): void => {
      GuiTool.resetScrollbarWidth();
      this.scrollbarWidth = this.mobileDevice ? 0 : GuiTool.measureScrollbars();
      this.changeDetectorRef.detectChanges();
   };

   ngOnDestroy(): void {
      window.removeEventListener("resize", this.onResize);
      this.devicePixelRatioMedia.removeEventListener("change", this.onDevicePixelRatioChange);

      if(this.clearCanvasSubscription) {
         this.clearCanvasSubscription.unsubscribe();
         this.clearCanvasSubscription = null;
      }

      if(this.scrollTopSubscription) {
         this.scrollTopSubscription.unsubscribe();
      }

      if(this.scrollLeftSubscription) {
         this.scrollLeftSubscription.unsubscribe();
      }
   }

   public resetScrollPosition(oplot: Plot, nplot: Plot) {
      if(oplot == null || nplot == null) {
         this.scrollLeft = 0;
         this.scrollTop = 0;
         return;
      }

      let oldBounds: Rectangular = oplot.bounds;
      let newBounds: Rectangular = nplot.bounds;
      let oldLayoutBounds: Rectangular = oplot.layoutBounds;
      let newLayoutBounds: Rectangular = nplot.layoutBounds;

      if(oldBounds == null || newBounds == null || oldBounds.width != newBounds.width
         || oldLayoutBounds == null || newLayoutBounds == null || oldLayoutBounds.width != newLayoutBounds.width)
      {
         this.scrollLeft = 0;
         this.onScroll.emit(new Point(this.scrollLeft, this.scrollTop));
      }

      if(oldBounds == null || newBounds == null || oldBounds.height != newBounds.height
         || oldLayoutBounds == null || newLayoutBounds == null || oldLayoutBounds.height != newLayoutBounds.height)
      {
         this.scrollTop = 0;
         this.onScroll.emit(new Point(this.scrollLeft, this.scrollTop));
      }
   }

   private getSelectedString(chartSelection: ChartSelection = this.chartSelection): string {
      return ChartTool.getSelectedString(this.model, chartSelection);
   }

   /**
    * Get client position of this chart area.
    */
   get clientRect(): ClientRect {
      return this._clientRect || !this._chartContainer ?
         this._clientRect : this._chartContainer.nativeElement.getBoundingClientRect();
   }

   /**
    * Store the position clicked on the chart for hyperlinks and axis resizers
    */
   onDown(event: MouseEvent): void {
      this.eventXdown = event.clientX;
      this.eventYdown = event.clientY;

      // startResize methods are called before onDown() so event x/y is not available in
      // time to initialize resizeLine's in those methods
      if(this.areaResizeInfo) {
         this.initResizeLine();
         event.stopPropagation();
      }
   }

   onMove(event: MouseEvent): void {
      this.tooltipLeft = event.clientX;
      this.tooltipTop = event.clientY;
   }

   /**
    * Emit when clicking on the empty space in the legend area
    *
    * @param {MouseEvent} event
    */
   selectLegendBackground(event: MouseEvent): void {
      if(event.target === event.currentTarget) {
         this.clearSelection();
      }
   }

   drill(payload: ChartDrillInfo): void {
      this.onDrill.emit(payload);
   }

   /**
    * Emitted from the chart objects. Clear all canvases then draw the emitted regions
    *
    * @param {Object}                     payload information about the selected region
    * @param {ChartObject}                payload.chartObject
    * @param {CanvasRenderingContext2D}   payload.context
    * @param {Array<ChartRegion>}         payload.regions
    * @param {boolean}                    payload.isCtrl
    */
   selectRegion(payload: any): void {
      let lastSelectedPlot = ChartTool.isPlotAreaSelected(this.model);

      if(((<any>this.model).multiSelect || payload.isCtrl) &&
         (Tool.isEquals(this.model.chartSelection.chartObject, payload.chartObject)))
      {
         this.model.chartSelection.regions =
            this.model.chartSelection.regions.concat(payload.regions);
      }
      else if(payload.isShift &&
              (Tool.isEquals(this.model.chartSelection.chartObject, payload.chartObject)))
      {
         const from = this.model.chartSelection.chartObject.regions.indexOf(
            this.model.chartSelection.regions[0]);
         const to = this.model.chartSelection.chartObject.regions.indexOf(payload.regions[0]);
         this.model.chartSelection.regions = [];

         for(let i = Math.min(from, to); i <= Math.max(from, to); i++) {
            this.model.chartSelection.regions.push(
               this.model.chartSelection.chartObject.regions[i]);
         }
      }
      else {
         this.model.chartSelection.regions = payload.regions;
      }

      // select all radar points to highlight the line (46142).
      if(GraphTypes.isRadar(this.model.chartType)) {
         const selectedLines = this.model.chartSelection.regions
            .filter(r => r.metaIdx == null)
            .map(r => r.rowIdx);
         this.model.plot.regions
            .filter(r => selectedLines.includes(r.rowIdx) && r.metaIdx == null &&
                    !this.model.chartSelection.regions.includes(r))
            .forEach(r => this.model.chartSelection.regions.push(r));
      }

      this.model.chartSelection.chartObject = payload.chartObject;
      this.model.chartSelection.rangeSelection = !!payload.rangeSelection;
      this.onSelectRegion.emit(this.model.chartSelection);
      this.chartObjectRef.forEach((chartObject) => chartObject.clearCanvas());
      let regions = this.model.chartSelection.regions;

      if(payload.chartObject.layoutBounds) {
         // this loop makes sure the selected region is drawn within the layout bounds.
         // for legend, the legend-content-bounds is the bounds without the border and padding
         // space, while legend item selection bounds may extends into padding. this
         // results in the selection bounds being clipped. to avoid this workaround, we
         // need to restructure legend-content-area include padding and shift the legend
         // items accordingly. that change is tricky so we will just workaround it here.
         regions = regions.map(region => {
            region = Tool.clone(region);

            if(ChartTool.areaType(this.model, region) == "legend") {
               for(let n = 0; n < region.pts.length; n++) {
                  for(let k = 0; k < region.pts[n].length; k++) {
                     if(region.segTypes[n].length == 1 && region.segTypes[n][0] == ChartTool.RECT) {
                        const y = region.pts[n][k][0][1];
                        if(y < payload.chartObject.layoutBounds.y) {
                           // height
                           region.pts[k][k][1][1] -= payload.chartObject.layoutBounds.y - y;
                           region.pts[n][k][0][1] = payload.chartObject.layoutBounds.y;
                        }
                     }
                  }
               }
            }

            return region;
         });
      }

      ChartTool.drawRegions(payload.context, regions, payload.canvasX, payload.canvasY,
         this.scaleService.getCurrentScale());
      let nowSelectedPlot = ChartTool.isPlotAreaSelected(this.model);

      if(!nowSelectedPlot && !!this.chartPlotArea && this.model.hasFlyovers) {
         this.chartPlotArea.clearSelection();
      }
   }

   /**
    * Clear the chart canvases and current selection
    */
   clearSelection(): void {
      this.model.chartSelection.regions = [];
      this.model.chartSelection.chartObject = null;

      if(this.chartObjectRef) {
         this.chartObjectRef.forEach((chartObject) => chartObject.clearCanvas());
      }

      this.onSelectRegion.emit();

      if(this.chartPlotArea != null) {
         this.chartPlotArea.clearSelection();
      }
   }

   clearPan() {
      if(this.chartPlotArea) {
         this.chartPlotArea.clearPan();
      }
   }

   /**
    * Given a selection send a flyover event. If there are already currently selected
    * regions on the plot then those should supersede the hover selection so use those
    * instead. Debounces every 100ms.
    */
   sendFlyover(payload: FlyoverInfo): void {
      const regions = ChartTool.getSelectedAxisRegions(this.model);
      let noAxisDimSelection = regions == null || regions.length == 0 ||
         regions.some(r => ChartTool.getDim(this.model, r) == null);
      let noPlotSelection = this.getSelectedRegions("plot_area").length === 0;
      let noCurrentSelection = noAxisDimSelection && noPlotSelection;
      const chartSelection = noCurrentSelection ? payload : undefined;
      const plotCondition = this.getSelectedString(chartSelection);
      this.flyoverApplied = true;

      this.onSendFlyover.emit({
         noCurrentSelection: noCurrentSelection,
         plotCondition: plotCondition,
         payload: payload
      });
   }

   /**
    * Show a datatip at the current mouse position.
    */
   showDataTip(payload: ChartSelection): void {
      this.onShowDataTip.emit({
         payload: payload,
         tooltipLeft: this.tooltipLeft,
         tooltipTop: this.tooltipTop,
      });
   }

   showTooltip(tipInfo: TooltipInfo): void {
      const tipIndex = tipInfo ? tipInfo.tipIndex : -1;
      let tooltipString = Tool.unescapeHTML(this.model.stringDictionary[tipIndex]);

      if(!this.mobileDevice && tooltipString && tipInfo.region.hyperlinks &&
         tipInfo.region.hyperlinks.length == 1)
      {
         tooltipString += "_#(js:composer.graph.ctrlSelect)";
      }

      if(tooltipString != this.tooltipString) {
         this.tooltipString = tooltipString;
         this.detectChanges();
      }
   }

   /**
    * When mousedown on a resizeable part of an axis save the axis that is being resized
    * and the starting mouse position to calculate the difference later
    */
   startAxisResize(payload: ChartSelection): void {
      this.areaResizeInfo = <AreaResizeInfo> payload;
      this.resizeVertical = (<Axis> payload.chartObject).axisType == "y";
      this.detectChanges();
   }

   /**
    * When mousedown on a resizeable part of a legend area, save the area that is being resized
    * and the starting mouse position to calculate the difference later
    */
   startLegendResize(payload: AreaResizeInfo): void {
      this.areaResizeInfo = payload;
      this.resizeVertical = payload.vertical;
      this.detectChanges();
   }

   /**
    * Calculate the difference in the new position of the mouse and the old position
    * of the axis to figure out the new size of the resized axis. Then send the position
    * to the server
    */
   endAxisResize(event: MouseEvent): void {
      // get x0,x1 and y0,y1 relative to chart so we can compare to max and min positions
      // that relative to chart
      this.onEndAxisResize.emit({
         axisResizeInfo: this.areaResizeInfo,
         eventXdown: this.eventXdown - this.clientRect.left,
         eventYdown: this.eventYdown - this.clientRect.top,
         clientX: event.clientX - this.clientRect.left,
         clientY: event.clientY - this.clientRect.top
      });

      this.areaResizeInfo = null;
      this.detectChanges();
   }

   /**
    * Emit the resized legend boundary with its start and end position so the legend resize
    * event can be created.
    */
   endLegendResize(event: MouseEvent, legend: LegendContainer): void {
      this.onEndLegendResize.emit({
         legendResizeInfo: this.areaResizeInfo,
         eventXdown: this.eventXdown - this.clientRect.left,
         eventYdown: this.eventYdown - this.clientRect.top,
         clientX: event.clientX - this.clientRect.left,
         clientY: event.clientY - this.clientRect.top,
         aestheticType: legend.aestheticType,
         field: legend.field,
         targetFields: legend.targetFields,
         nodeAesthetic: legend.nodeAesthetic
      });

      this.areaResizeInfo = null;
      this.detectChanges();
   }

   initResizeLine(): void {
      if(this.resizeVertical) {
         this.resizeLineLeft = this.eventXdown - this.clientRect.left;
      }
      else {
         this.resizeLineTop = this.eventYdown - this.clientRect.top;
      }
   }

   // call correct method to handle ending of area resizing
   endAreaResize(event: MouseEvent): void {
      if(this.areaResizeInfo.type == "axis") {
         this.endAxisResize(event);
      }
      else if(this.areaResizeInfo.type == "legend") {
         this.endLegendResize(event, (<LegendResizeInfo> this.areaResizeInfo).legend);
      }
   }

   /**
    * Update position of resize indication line.
    * @param event   the mouse move event
    */
   onResizeMove(event: MouseEvent): void {
      event.stopPropagation();
      let mouseX: number = event.clientX;
      let mouseY: number = event.clientY;

      if(this.resizeVertical) {
         // For axis area/legend just check max and min position which are relative to entire chart
         mouseX = mouseX - this.clientRect.left;
         mouseX = mouseX > this.areaResizeInfo.maxPosition ?
            this.areaResizeInfo.maxPosition : mouseX;
         mouseX = mouseX < this.areaResizeInfo.minPosition ?
            this.areaResizeInfo.minPosition : mouseX;
         this.resizeLineLeft = mouseX;
      }
      else {
         mouseY = mouseY - this.clientRect.top;
         mouseY = mouseY > this.areaResizeInfo.maxPosition ?
            this.areaResizeInfo.maxPosition : mouseY;
         mouseY = mouseY < this.areaResizeInfo.minPosition ?
            this.areaResizeInfo.minPosition : mouseY;
         this.resizeLineTop = mouseY;
      }

      this.detectChanges();
   }

   endLegendMove(interactInfo: InteractInfo, legend: LegendContainer): void {
      this.onEndLegendMove.emit({
         interactInfo: interactInfo,
         aestheticType: legend.aestheticType,
         field: legend.field,
         targetFields: legend.targetFields,
         nodeAesthetic: legend.nodeAesthetic
      });
   }

   changeCursor(cursor: string): void {
      if(this.currentCursor !== cursor) {
         this.currentCursor = cursor;
         // Change cursor event comes from outside the zone
         this.detectChanges();
      }
   }

   /**
    * Get the selected chart region's hyperlinks and bubble it up to vs-chart
    */
   showHyperlink(event: MouseEvent): void {
      if(this.mobileDevice) {
         try {
            const selRegion = ChartTool.getSelectedRegions(this.model.chartSelection)[0];

            if(selRegion.hyperlinks && selRegion.hyperlinks.length > 0) {
               // on mobile, don't show tooltip and hyperlink at same time on click
               // or they will cover each other
               this.tooltipString = null;
            }
         }
         catch(e) {
         }
      }

      this.onShowHyperlink.emit(event);
   }

   sortAxis(axis: Axis): void {
      this.onSortAxis.emit(axis);
   }

   /**
    * Capture the scroll amount to dispatch to axis/plot for synchronized scrolling
    *
    * @param event {UIEvent} the event that contains the scroll amount
    */
   onWrapperScroll(event: any, horizontal: boolean): void {
      event.preventDefault();

      if(horizontal) {
         this.scrollLeft = event.target.scrollLeft;
      }
      else {
         this.scrollTop = event.target.scrollTop;
      }
      this.onScroll.emit(new Point(this.scrollLeft, this.scrollTop));
   }

   /**
    * Apply scroll to all areas when one area is scrolled to keep them all synchronized.
    *
    * @param event   the scroll event on the child area
    */
   onScrollArea(event: any): void {
      this.scrollLeft = event.target.scrollLeft;
      this.scrollTop = event.target.scrollTop;
      this.tooltipString = null;
      this.onScroll.emit(new Point(this.scrollLeft, this.scrollTop));
   }

   /**
    * Apply scroll to all areas when one axis is scrolled to keep them all synchronized.
    *
    * @param event   the scroll event on the child axis
    */
   onScrollAxis(event: any, axis: Axis): void {
      // only update relevant scroll side
      if(axis.axisType == "x") {
         this.scrollLeft = event.target.scrollLeft;
      }
      else {
         this.scrollTop = event.target.scrollTop;
      }
      this.onScroll.emit(new Point(this.scrollLeft, this.scrollTop));
   }

   /**
    * Return this chart's selected regions
    * @param areaName if passed, only return the specified area's selected regions
    * @returns {ChartRegion[]} A list of the selected ChartRegions
    */
   public getSelectedRegions(areaName: ChartAreaName = null): ChartRegion[] {
      return ChartTool.getSelectedRegions(this.model.chartSelection, areaName);
   }

   private getDropRegionContext(): CanvasRenderingContext2D {
      return this.dropRegionCanvas.nativeElement.getContext("2d");
   }

   /**
    * Init drop regions.
    * @return true if internal value changed and requires a change detection
    */
   private initDropRegions(): void {
      let xTitle: ChartObject = ChartTool.getXTitle(this.model);
      let x2Title: ChartObject = ChartTool.getX2Title(this.model);
      let yTitle: ChartObject = ChartTool.getYTitle(this.model);
      let y2Title: ChartObject = ChartTool.getY2Title(this.model);
      let xBottomAxis: ChartObject = ChartTool.getXBottomAxis(this.model);
      let xTopAxis: ChartObject = ChartTool.getXTopAxis(this.model);
      let yLeftAxis: ChartObject = ChartTool.getYLeftAxis(this.model);
      let yRightAxis: ChartObject = ChartTool.getYRightAxis(this.model);

      this.xTopRegion = x2Title ? new Rectangle(
         x2Title.bounds.x,
         x2Title.bounds.y,
         xTopAxis.bounds.width,
         xTopAxis.bounds.height + x2Title.bounds.height) : new Rectangle(0, 0, 0, 0);

      this.xBottomRegion = xTitle ? new Rectangle(
         xBottomAxis.layoutBounds.x,
         xBottomAxis.layoutBounds.y,
         xBottomAxis.layoutBounds.width,
         xBottomAxis.layoutBounds.height + xTitle.bounds.height) : new Rectangle(0, 0, 0, 0);

      this.yLeftRegion = yTitle ? new Rectangle(
         yTitle.bounds.x,
         yTitle.bounds.y,
         yTitle.bounds.width + yLeftAxis.layoutBounds.width,
         yTitle.bounds.height) : new Rectangle(0, 0, 0, 0);

      this.yRightRegion = y2Title ? new Rectangle(
         yRightAxis.layoutBounds.x,
         yRightAxis.layoutBounds.y,
         yRightAxis.layoutBounds.width + y2Title.bounds.width,
         yRightAxis.layoutBounds.height) : new Rectangle(0, 0, 0, 0);

      this.plotRegion = this.model.plot ? new Rectangle(
         this.model.plot.layoutBounds.x,
         this.model.plot.layoutBounds.y,
         this.model.plot.layoutBounds.width,
         this.model.plot.layoutBounds.height) : new Rectangle(0, 0, 0, 0);
   }

   /**
    * Check whether the chart has any data (viewer/preview).
    */
   get noData(): boolean {
      return !this.showEmptyArea && !this.model.invalid &&
         (!this.model.axes || this.model.axes.length == 0);
   }

   /**
    * Used to highlight drop areas of empty chart.
    */
   public onAxisEnter(xAxis: boolean): void {
      xAxis ? this.xAxisHover++ : this.yAxisHover++;
   }

   /**
    * Used to highlight drop areas of empty chart.
    */
   public onAxisLeave(xAxis: boolean): void {
      xAxis ? this.xAxisHover-- : this.yAxisHover--;
   }

   /**
    * Drop data onto empty chart.
    */
   public onAxisDrop(event: DragEvent, xAxis: boolean): void {
      this.xAxisHover = 0;
      this.yAxisHover = 0;
      this.dropType = xAxis ? ChartConstants.DROP_REGION_X : ChartConstants.DROP_REGION_Y;
      this.onDrop(event);
   }

   getAllAxisNames(): string[] {
      let allAxis: string[] = [];
      let i: number = 0;

      for(let axis of this.model.axes) {
         for(let field of axis.axisFields) {
            allAxis[i++] = field;
         }
      }

      return allAxis;
   }

   /**
    * Drag dimension col to charView's axis area when X/Y pane has same col
    * Shouldn't draw border.
    */
   isDrawAxisBorder(): boolean {
      const dragCols = this.dndService.getTransfer().column;
      const axesNames = this.getAllAxisNames();

      if(dragCols && axesNames) {
         for(let col of dragCols) {
            let paths = col.path.split("/");
            let colParentLabel = paths[paths.length - 2];
            let colLabel = paths[paths.length - 1];

            for(let axesName of axesNames) {
               if("_#(js:Dimensions)" == colParentLabel && axesName == colLabel) {
                  return false;
               }
            }
         }
      }

      return true;
   }

   /**
    * Drag over to draw drop border.
    */
   public dragOver(event: DragEvent): void {
      if(!this.emptyChart) {
         const canvas: HTMLCanvasElement = this.dropRegionCanvas.nativeElement;
         const containerBounds = (<Element> event.currentTarget).getBoundingClientRect();
         let eventX = event.clientX - containerBounds.left;
         let eventY = event.clientY - containerBounds.top;
         canvas.width = containerBounds.width;
         canvas.height = containerBounds.height;

         this.drawDropRegion(eventX, eventY);
         event.preventDefault();
      }
   }

   public dragLeave(event: DragEvent): void {
      if(!this.emptyChart) {
         this.dropType = -1;
         this.chartService.clearCanvas(this.getDropRegionContext());
      }
   }

   public onDrop(event: DragEvent): void {
      event.preventDefault();

      if(this.dropType == -1 || !this.isDropAcceptable(event)) {
         if(!this.emptyChart) {
            this.chartService.clearCanvas(this.getDropRegionContext());
         }

         return;
      }

      let entriesValue: string = null;

      if(!!event.dataTransfer.getData("text")) {
         const data: any = JSON.parse(event.dataTransfer.getData("text"));
         entriesValue = data.column;
      }

      // Only support drag from tree.
      if(entriesValue == null || entriesValue == "") {
         if(!this.emptyChart) {
            this.chartService.clearCanvas(this.getDropRegionContext());
         }

         return;
      }

      const elem: string = this.getAssemblyName();
      let dtarget: ChartViewDropTarget = new ChartViewDropTarget(this.dropType + "", elem);
      this.dndService.processOnDrop(event, dtarget);

      if(!this.emptyChart) {
         this.chartService.clearCanvas(this.getDropRegionContext());
      }
   }

   private getAssemblyName(): string {
      return this.model.hasOwnProperty("absoluteName") ?
         this.model["absoluteName"] : this.model["objectName"];
   }

   private isDropAcceptable(event: DragEvent): boolean {
      if(this.model.multiStyles || !event.dataTransfer.getData("text")) {
         return true;
      }

      const data: any = JSON.parse(event.dataTransfer.getData("text"));
      let entriesValue: any[] = data.column;

      if(!entriesValue || entriesValue.length == 0) {
         return false;
      }

      let entry: any = entriesValue[0];
      let cubeType: string = entry.properties["cube.column.type"];
      let ctype: number = cubeType ? parseInt(cubeType, 10) : 0;
      let isBindMeasure: boolean = false;
      const isDim: boolean = !AssetUtil.isMeasure(entry);

      if(cubeType) {
         isBindMeasure = (ctype & 1) == 1;
      }
      else if(entry.path && this.bindingModel) {
         const name = entry.path.substring(entry.path.lastIndexOf("/") + 1);
         const allfields = this.bindingModel.xfields.concat(this.bindingModel.yfields);

         if(this.bindingModel.colorField != null) {
            allfields.push(this.bindingModel.colorField.dataInfo);
         }

         if(this.bindingModel.shapeField != null) {
            allfields.push(this.bindingModel.shapeField.dataInfo);
         }

         if(this.bindingModel.sizeField != null) {
            allfields.push(this.bindingModel.sizeField.dataInfo);
         }

         if(this.bindingModel.textField != null) {
            allfields.push(this.bindingModel.textField.dataInfo);
         }

         allfields.push(this.bindingModel.openField);
         allfields.push(this.bindingModel.closeField);
         allfields.push(this.bindingModel.lowField);
         allfields.push(this.bindingModel.highField);

         isBindMeasure = allfields
            .filter(a => a && a.measure)
            .some(a => a.name == name);
      }

      if(this.dropType == ChartConstants.DROP_REGION_Y2 && !this.isY2Supported()) {
         return false;
      }

      if(this.dropType == ChartConstants.DROP_REGION_X ||
         this.dropType == ChartConstants.DROP_REGION_X2)
      {
         return this.isXAcceptable(isBindMeasure, isDim);
      }
      else if(this.dropType == ChartConstants.DROP_REGION_Y ||
         this.dropType == ChartConstants.DROP_REGION_Y2)
      {
         return this.isYAcceptable(isBindMeasure, isDim);
      }
      else if(this.dropType == ChartConstants.DROP_REGION_GEO) {
         return this.isGeoAcceptable(entry);
      }

      return true;
   }

   private isXAcceptable(isBindMeasure: boolean, isDim): boolean {
      return !isBindMeasure || GraphTypes.supportsInvertedChart(this.model.chartType);
   }

   private isYAcceptable(isBindMeasure: boolean, isDim: boolean = false): boolean {
      return !isBindMeasure || !GraphTypes.isCandle(this.model.chartType)
         && !GraphTypes.isStock(this.model.chartType)
         && !GraphTypes.isTreemap(this.model.chartType);
   }

   private isGeoAcceptable(entry: AssetEntry): boolean {
      if(entry && entry.properties) {
         return entry.properties["mappingStatus"] !== undefined;
      }

      return false;
   }

   /**
    * Draw drop border by canvas and ensure drop type.
    */
   private drawDropRegion(eventX: number, eventY: number): void {
      let minAxis: number = 4;
      let edge: number = 15;
      let box: Rectangle = null;

      if(this.xTopRegion.contains(eventX, eventY) && this.isDrawAxisBorder()) {
         this.dropType = ChartConstants.DROP_REGION_X2;

         if(this.xTopRegion.height > minAxis) {
            box = this.xTopRegion;
         }
         else {
            box = new Rectangle(this.plotRegion.x, 0, this.plotRegion.width, edge);
         }
      }
      else if(this.xBottomRegion.contains(eventX, eventY) && this.isDrawAxisBorder()) {
         this.dropType = ChartConstants.DROP_REGION_X;

         if(this.xBottomRegion.height > minAxis) {
            box = new Rectangle(this.xBottomRegion.x,
                                this.xBottomRegion.y,
                                this.xBottomRegion.width,
                                this.xBottomRegion.height - 2);
         }
         else {
            box = new Rectangle(this.plotRegion.x,
                                this.plotRegion.y + this.plotRegion.height - edge,
                                this.plotRegion.width, edge);
         }
      }
      else if(this.yLeftRegion.contains(eventX, eventY) && this.isDrawAxisBorder()) {
         this.dropType = ChartConstants.DROP_REGION_Y;

         if(this.yLeftRegion.width > minAxis) {
            box = this.yLeftRegion;
         }
         else {
            box = new Rectangle(this.plotRegion.x, this.plotRegion.y,
               edge, this.plotRegion.height);
         }
      }
      else if(this.yRightRegion.contains(eventX, eventY) && this.isDrawAxisBorder()) {
         this.dropType = ChartConstants.DROP_REGION_Y2;

         if(!this.isY2Supported()) {
            box = null;
         }
         else if(this.yRightRegion.width > minAxis) {
            box = this.yRightRegion;
         }
         else {
            box = new Rectangle(this.plotRegion.x + this.plotRegion.width - edge,
               this.plotRegion.y, edge, this.plotRegion.height);
         }
      }
      else if(this.plotRegion.contains(eventX, eventY)) {
         this.dropType = ChartConstants.DROP_REGION_PLOT;

         box = new Rectangle(this.plotRegion.x, this.plotRegion.y,
                             this.plotRegion.width, this.plotRegion.height);

         let inLegend: boolean = false;

         for(let i = 0; this.model.legends && i < this.model.legends.length; i++) {
            let legend: LegendContainer = this.model.legends[i];
            let box2: Rectangle = new Rectangle(legend.bounds.x, legend.bounds.y,
               legend.bounds.width, legend.bounds.height);

            if(box2.contains(eventX, eventY)) {
               inLegend = true;
               box = box2;
               this.dropType = ChartTool.getAestheticType(legend);

               break;
            }
         }

         if(inLegend) {
            // keep legend
         }

         let xBottomAxis: ChartObject = ChartTool.getXBottomAxis(this.model);
         let xTopAxis: ChartObject = ChartTool.getXTopAxis(this.model);
         let yLeftAxis: ChartObject = ChartTool.getYLeftAxis(this.model);
         let yRightAxis: ChartObject = ChartTool.getYRightAxis(this.model);

         // y axis
         if(eventX < edge && this.yLeftRegion.width <= minAxis && yLeftAxis
            && this.isDrawAxisBorder()) {
            this.dropType = ChartConstants.DROP_REGION_Y;
            box.width = edge;
         }
         // y2 axis
         else if((eventX > this.plotRegion.x + this.plotRegion.width - edge &&
                 this.yRightRegion.width <= minAxis && yRightAxis)
                 && this.isY2Supported())
         {
            this.dropType = ChartConstants.DROP_REGION_Y2;
            box.x = box.x + box.width - edge;
            box.width = edge;
         }
         // x2 axis
         else if(eventY < edge && this.xTopRegion.height <= minAxis && xTopAxis) {
            this.dropType = ChartConstants.DROP_REGION_X2;
            box.y = 0;
            box.height = edge;
         }
         // x axis
         else if(eventY > this.plotRegion.y + this.plotRegion.height - edge &&
                 this.xBottomRegion.height <= minAxis && xBottomAxis
                 && this.isDrawAxisBorder())
         {
            this.dropType = ChartConstants.DROP_REGION_X;
            box.y = box.y + box.height - edge;
            box.height = edge;
         }

         box.width -= 2; // allow space for border
         box.height -= 2;
      }

      if(this.model.legends) {
         for(let i = 0; i < this.model.legends.length; i++) {
            let legend: LegendContainer = this.model.legends[i];
            let box2: Rectangle = new Rectangle(legend.bounds.x, legend.bounds.y,
                                                legend.bounds.width, legend.bounds.height);

            if(box2.contains(eventX, eventY)) {
               box = new Rectangle(box2.x + 1, box2.y + 1, box2.width - 2, box2.height - 2);
               this.dropType = ChartTool.getAestheticType(legend);
               break;
            }
         }
      }
      // clear not supported drop area
//      else if(dragArea == yrightAxis.getDataArea() && !this.isY2Supported() ||
//              dragArea is AxisArea && polar)
//      {
//         box = null;
//         dragArea = null;
//      }
//      else {
//         if(dragArea is AxisArea) {
//            box = (dragArea as AxisArea).getRegion() == null ?
//               (dragArea as AxisArea).getLayoutBounds() :
//               (dragArea as AxisArea).getRegion().getBounds();
//         }
//         else {
//            box = (dragArea as AbstractArea).getRegion() == null ? null :
//               (dragArea as AbstractArea).getRegion().getBounds();
//         }
//      }

      if(box == null) {
         return;
      }

      if(box.width < edge - 2 || box.height < edge - 2) {
         box = new Rectangle(box.x, box.y, Math.max(box.width, edge - 2),
                             Math.max(box.height, edge - 2));
      }

      this.chartService.drawRectangle(this.getDropRegionContext(), box);
   }

   /**
    * Check if dnd on Y2 is allowed.
    */
   isY2Supported(): boolean {
      return !GraphTypes.is3DBar(this.model.chartType) &&
             !GraphTypes.isPolar(this.model.chartType) &&
             !GraphTypes.isWaterfall(this.model.chartType) &&
             !GraphTypes.isPareto(this.model.chartType) &&
             !GraphTypes.isStock(this.model.chartType) &&
             !GraphTypes.isMap(this.model.chartType) &&
             !GraphTypes.isCandle(this.model.chartType) &&
             !GraphTypes.isTreemap(this.model.chartType) &&
             !GraphTypes.isMekko(this.model.chartType) &&
             !GraphTypes.isBoxplot(this.model.chartType) &&
             !GraphTypes.isTree(this.model.chartType) &&
             !GraphTypes.isNetwork(this.model.chartType) &&
             !GraphTypes.isCircularNetwork(this.model.chartType) &&
             !GraphTypes.isGantt(this.model.chartType) &&
             !GraphTypes.isFunnel(this.model.chartType);
   }

   detectChanges(): void {
      this.changeDetectorRef.detectChanges();
   }

   get chartContainerWidth(): number {
      let paddingLeft: number = 0;
      let paddingRight: number = 0;

      if(this.isVSChart) {
         const chart = <VSChartModel> this.model;
         paddingLeft = chart.paddingLeft +
            this.getBorderWidth(chart.objectFormat.border.left);
         paddingRight = chart.paddingRight +
            this.getBorderWidth(chart.objectFormat.border.right);
      }

      return this.format.width - paddingLeft - paddingRight;
   }

   get chartContainerHeight(): number {
      let paddingTop: number = 0;
      let paddingBottom: number = 0;

      if(this.isVSChart) {
         const chart = <VSChartModel> this.model;
         paddingTop = chart.paddingTop +
            this.getBorderWidth(chart.objectFormat.border.top);
         paddingBottom = chart.paddingBottom +
            this.getBorderWidth(chart.objectFormat.border.bottom);
      }

      return (this.titleVisible
         ? this.format.height - this.titleFormat.height
         : this.format.height) - paddingTop - paddingBottom;
   }

   get chartContainerTop(): number {
      let paddingTop: number;

      if(this.isVSChart) {
         const chartModel = <VSChartModel> this.model;
         // chart border is subtracted from chart size in VSUtil.getContentSize,
         // so it should be accounted here too
         paddingTop = chartModel.paddingTop +
            this.getBorderWidth(chartModel.objectFormat.border.top);
      }
      else {
         paddingTop = this.getBorderWidth(this.format.border.top);
      }

      return (this.titleVisible ? this.titleFormat.height : 0) + paddingTop;
   }

   get chartContainerLeft(): number {
      let paddingLeft: number;

      if(this.isVSChart) {
         const chartModel = <VSChartModel> this.model;
         paddingLeft = chartModel.paddingLeft +
            this.getBorderWidth(chartModel.objectFormat.border.left);
      }
      else {
         paddingLeft = this.getBorderWidth(this.format.border.left);
      }

      return paddingLeft;
   }

   private getBorderWidth(border: string): number {
      if(!border) {
         return 0;
      }

      const px = border.indexOf("px");
      const num = parseInt(border.substring(0, px), 10);
      return num > 0 ? num : 0;
   }

   getLegendContentHeight(legend: LegendContainer, legendObject: Legend): number {
// this is really the maximum height for legend. don't subtract border. (61451)
      return legend.bounds.height - legendObject.layoutBounds.y;
   }

   onMouseoverLegendRegion(legendRegion: ChartAreaName): void {
      this.mouseoverLegendRegion = legendRegion;
   }

   axisLoading(): void {
      this._axisLoaded = false;
      this.fireLoading();
   }

   public axisLoaded(success: boolean) {
      this.imageError = !success;
      this._axisLoaded = true;
      this.fireLoaded();
   }

   plotLoading(): void {
      this._plotLoaded = false;
      this.fireLoading();
   }

   plotLoaded(success: boolean): void {
      this.imageError = !success;
      this._plotLoaded = true;
      this.fireLoaded();
   }

   private fireLoading(): void {
      if(!(this._axisLoaded || this._plotLoaded)) {
         this.onLoading.emit(true);
      }
   }

   private fireLoaded(): void {
      if(this.imageError) {
         this.onError.emit(true);
      }
      else if(this._axisLoaded && this._plotLoaded) {
         this.onLoad.emit(true);
      }
   }

   trackByFn(index: number, item: any): number {
      return index;
   }

   get chartContainerVisible(): boolean {
      return !!this.model && (this.noData || this.model.invalid || this.emptyChart ||
         this.paintNoDataChart() || this.vsChartModel.empty) || !(this.model && this.model.invalid) && this.imageError;
   }

   get downScrollEnabled(): boolean {
      return this.scrollTop + this.plotRegion.height < this.model.plot.bounds.height;
   }

   get upScrollEnabled(): boolean {
      return this.scrollTop > 0;
   }

   get rightScrollEnabled(): boolean {
      return this.scrollLeft + this.plotRegion.width < this.model.plot.bounds.width;
   }

   get leftScrollEnabled(): boolean {
      return this.scrollLeft > 0;
   }

   pageDown() {
      this.scrollTop = Math.min(this.scrollTop + this.plotRegion.height - 5,
                                this.model.plot.bounds.height - this.plotRegion.height);
   }

   pageRight() {
      this.scrollLeft = Math.min(this.scrollLeft + this.plotRegion.width - 5,
                                this.model.plot.bounds.width - this.plotRegion.width);
   }

   pageUp() {
      this.scrollTop = Math.max(this.scrollTop - this.plotRegion.height + 5, 0);
   }

   pageLeft() {
      this.scrollLeft = Math.max(this.scrollLeft - this.plotRegion.width + 5, 0);
   }

   isNavMap(): boolean {
      return GraphTypes.isGeo(this.model.chartType) && this.model.navEnabled &&
         (!this.model.facets || this.model.facets.length == 0);
   }

   // pinch zoom
   touchStart(event: TouchEvent) {
      if(event.touches.length == 2) {
         event.stopImmediatePropagation();
         event.preventDefault();
         this.pinchStart = Math.hypot(event.touches[0].pageX - event.touches[1].pageX,
                                      event.touches[0].pageY - event.touches[1].pageY);
      }
   }

   touchMove(event: TouchEvent) {
      if(event.touches.length == 2) {
         this.pinchMove = Math.hypot(event.touches[0].pageX - event.touches[1].pageX,
                                      event.touches[0].pageY - event.touches[1].pageY);
      }
   }

   touchEnd(event: TouchEvent) {
      if(this.pinchStart && this.pinchMove) {
         if(this.pinchMove > this.pinchStart) {
            this.onZoomIn.emit(Math.ceil((this.pinchMove - this.pinchStart) / 10));
         }
         else {
            this.onZoomOut.emit(Math.ceil((this.pinchStart - this.pinchMove) / 10));
         }
      }

      this.pinchStart = this.pinchMove = 0;
   }

   showPagingControl(): void {
      if(this.mobileDevice) {
         const pagingControlModel: PagingControlModel = {
            assemblyName: this.getAssemblyName(),
            viewportWidth: this.plotRegion.width,
            viewportHeight: this.plotRegion.height,
            contentWidth: this.model.plot.bounds.width,
            contentHeight: this.model.plot.bounds.height,
            scrollTop: this.scrollTop,
            scrollLeft: this.scrollLeft,
            enabled: true
         };
         this.pagingControlService.setPagingControlModel(pagingControlModel);
      }
   }

   getPlotErrorStyle() {
      if(!this.model.errorFormat) {
         return null;
      }

      return {
         "color": this.model.errorFormat.color,
         "background-color": this.model.errorFormat.backgroundColor,
         "align-items": GuiTool.getFlexVAlign(this.model.errorFormat?.align?.valign),
         "justify-content": GuiTool.getFlexHAlign(this.model.errorFormat?.align?.halign),
         "font-family": this.model.errorFormat?.font?.fontFamily,
         "font-style": this.model.errorFormat?.font?.fontStyle,
         "font-size": this.model.errorFormat?.font?.fontSize + "px",
         "font-weight": this.model.errorFormat?.font?.fontWeight
      };
   }
}
