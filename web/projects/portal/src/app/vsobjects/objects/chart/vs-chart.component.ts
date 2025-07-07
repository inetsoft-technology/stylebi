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
import { HttpParams } from "@angular/common/http";
import {
   ChangeDetectorRef,
   Component,
   ElementRef,
   EventEmitter,
   HostListener,
   Injector,
   Input,
   NgZone,
   OnDestroy,
   OnInit,
   Optional,
   Output,
   Renderer2,
   ViewChild
} from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Observable, Subscription } from "rxjs";
import { DownloadService } from "../../../../../../shared/download/download.service";
import { Tool } from "../../../../../../shared/util/tool";
import { SourceInfoType } from "../../../binding/data/source-info-type";
import { AssemblyAction } from "../../../common/action/assembly-action";
import { AssemblyActionGroup } from "../../../common/action/assembly-action-group";
import { FormatInfoModel } from "../../../common/data/format-info-model";
import { HyperlinkModel } from "../../../common/data/hyperlink-model";
import { Point } from "../../../common/data/point";
import { Rectangle, Rectangular } from "../../../common/data/rectangle";
import { DndService } from "../../../common/dnd/dnd.service";
import { ChartConstants } from "../../../common/util/chart-constants";
import { ComponentTool } from "../../../common/util/component-tool";
import { DataPathConstants } from "../../../common/util/data-path-constants";
import { GuiTool } from "../../../common/util/gui-tool";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { Axis } from "../../../graph/model/axis";
import { ChartAreaName } from "../../../graph/model/chart-area-name";
import { ChartDrillInfo } from "../../../graph/model/chart-drill-info";
import { ChartModel } from "../../../graph/model/chart-model";
import { ChartRegion } from "../../../graph/model/chart-region";
import { ChartSelection } from "../../../graph/model/chart-selection";
import { ChartTool } from "../../../graph/model/chart-tool";
import { FlyoverData } from "../../../graph/model/flyover-data";
import { LegendContainer } from "../../../graph/model/legend-container";
import { PlotScaleInfo } from "../../../graph/model/plot-scale-info";
import { ChartArea } from "../../../graph/objects/chart-area.component";
import { VsWizardEditModes } from "../../../vs-wizard/model/vs-wizard-edit-modes";
import { FixedDropdownService } from "../../../widget/fixed-dropdown/fixed-dropdown.service";
import { DebounceService } from "../../../widget/services/debounce.service";
import { ModelService } from "../../../widget/services/model.service";
import { ScaleService } from "../../../widget/services/scale/scale-service";
import { DialogService } from "../../../widget/slide-out/dialog-service.service";
import { ChartActions } from "../../action/chart-actions";
import { ClearChartLoadingCommand } from "../../command/clear-chart-loading-command";
import { ClearMapPanCommand } from "../../command/clear-map-pan-command";
import { SetChartAreasCommand } from "../../command/set-chart-areas-command";
import { ContextProvider } from "../../context-provider.service";
import { ImageFormatSelectComponent } from "../../dialog/image-format-select.component";
import { RichTextService } from "../../dialog/rich-text-dialog/rich-text.service";
import { AddAnnotationEvent } from "../../event/annotation/add-annotation-event";
import { ChangeVSObjectTextEvent } from "../../event/change-vs-object-text-event";
import { FilterChartEvent } from "../../event/filter-chart-event";
import { AssemblyActionEvent } from "../../../common/action/assembly-action-event";
import {
   CancelEvent,
   VSChartEvent,
   VSChartPlotResizeEvent,
   VSChartRefreshEvent
} from "../../event/vs-chart-event";
import { VSChartModel } from "../../model/vs-chart-model";
import { ShowHyperlinkService } from "../../show-hyperlink.service";
import { ViewerResizeService } from "../../util/viewer-resize.service";
import { VSUtil } from "../../util/vs-util";
import { AbstractVSObject } from "../abstract-vsobject.component";
import { AdhocFilterService } from "../data-tip/adhoc-filter.service";
import { DataTipService } from "../data-tip/data-tip.service";
import { SelectableObject } from "../selectable-object";
import { DetailDndInfo } from "../table/detail-dnd-info";
import { SortInfo } from "../table/sort-info";
import { VSChartActionHandler } from "./services/vs-chart-action-handler";
import { VSChartService } from "./services/vs-chart.service";
import { GraphTypes } from "../../../common/graph-types";
import { FullScreenService } from "../../../common/services/full-screen.service";
import { Dimension } from "../../../common/data/dimension";
import { VSTabService } from "../../util/vs-tab.service";

const CHART_AREAS_URI: string = "/events/vschart/areas";
const CHART_PLOT_RESIZE_URL: string = "/events/vschart/resize-plot";
const CHART_CANCEL_QUERY_URL: string = "/events/vschart/cancel-query";
const CHART_REFRESH_URL: string = "/events/vschart/refresh-chart";
const CHART_MAX_MODE_URL: string = "/events/vschart/toggle-max-mode";
const CHART_ADD_DATA_ANNOTATION_URL: string = "/events/annotation/add-data-annotation";
const CHART_ADD_ASSEMBLY_ANNOTATION_URL: string = "/events/annotation/add-assembly-annotation";
const CHART_ADD_FILTER_URL: string = "/events/composer/viewsheet/chart/addFilter";
const CHART_CHANGE_TITLE_URL: string = "/events/composer/viewsheet/objects/changeTitle";
const CHART_DATA_FORMAT_URI: string = "../vschart/showdata/format-model";
const CHART_DETAIL_FORMAT_URI: string = "../vschart/showdetails/format-model";
const CHART_WIZARD_CHANGE_TITLE_URL = "/events/vswizard/preview/changeDescription";

@Component({
   selector: "vs-chart",
   templateUrl: "vs-chart.component.html",
   styleUrls: ["vs-chart.component.scss"]
})
export class VSChart extends AbstractVSObject<VSChartModel>
   implements OnInit, OnDestroy, SelectableObject
{
   @Input() container: Element;
   @Input() goToWizardVisible: boolean = false;
   @Input() variableValues: (objName: string) => string[];
   @Output() onTitleResizeMove: EventEmitter<number> = new EventEmitter<number>();
   @Output() onTitleResizeEnd: EventEmitter<any> = new EventEmitter<any>();
   @Input() appSize: Dimension = new Dimension(0, 0);
   @Input()
   public set selected(selected: boolean) {
      this._selected = selected;

      // if deselected clear selected annotations
      if(!selected) {
         this.model.selectedAnnotations = [];
         this.model.showPlotResizers = false;
         this.model.multiSelect = false;
      }
   }

   public get selected(): boolean {
      return this._selected;
   }

   @Input() viewsheetLoading: boolean = false;
   @Output() public onOpenEditPane = new EventEmitter<any>();
   @Output() public maxModeChange = new EventEmitter<{assembly: string, maxMode: boolean}>();
   @Output() public onOpenFormatPane = new EventEmitter<VSChartModel>();
   @Output() public onLoadFormatModel = new EventEmitter<VSChartModel>();
   @ViewChild("chartContainer") chartContainer: ElementRef;
   @ViewChild("chartArea") chartArea: ChartArea;
   public scrollTop = 0;
   public scrollLeft = 0;
   public plotScaleInfo: PlotScaleInfo;
   private _selected: boolean;
   private _actions: ChartActions;
   private actionSubscription: Subscription;
   private clickAction: AssemblyAction;
   private scale: number;
   private tabSubscription: Subscription;
   previewTableType: "Data" | "Details";
   chartLoading: boolean = false;
   chartLoadingIcon: boolean = false;
   showEmptyArea: boolean = false;
   noChartData: boolean = false;
   isBinding: boolean = false;
   modelTS: number = 0;
   showHints: boolean = false;
   private resizeSubscription: Subscription;
   private lastClick: {clientX: number, clientY: number} = null;
   private subscriptions = Subscription.EMPTY;
   private isIE: boolean = GuiTool.isIE();
   private isIFrame: boolean = GuiTool.isIFrame();

   @Input()
   set model(m: VSChartModel) {
      this._model = m;

      if(this.vsWizardPreview) {
         this.detectChanges();
      }

      if(!m.sheetMaxMode || m.maxMode || this.dataTipService.isDataTip(m.absoluteName)) {
         const event = new VSChartEvent(this._model, this._model.maxMode, this.container);

         if(this.vsInfo?.orgId) {
            event.setOrgId(this.vsInfo.orgId);
         }

         this.showChartLoading(true, 2);
         this.viewsheetClient.sendEvent(CHART_AREAS_URI, event);
      }
   }

   get model(): VSChartModel {
      return this._model;
   }

   private openWizardEnabled(): boolean {
      return this.goToWizardVisible &&
         (this.model.editedByWizard || this.model.sourceType == SourceInfoType.NONE);
   }

   @Input()
   get actions(): ChartActions {
      return this._actions;
   }

   set actions(value: ChartActions) {
      if(this.actionSubscription) {
         this.actionSubscription.unsubscribe();
         this.actionSubscription = null;
      }

      const propertiesHandler =
         new VSChartActionHandler(this.modelService, this.viewsheetClient,
                                  this.modalService, this.injector, this.viewer,
                                  this.dataTipService, this.contextProvider);
      this._actions = value;

      if(value) {
         this.actionSubscription = value.onAssemblyActionEvent.subscribe((event) => {
            switch(event.id) {
            case "chart edit":
               if(this.isIFrame) {
                  this.fullScreenService.enterFullScreen();
               }

               if(this.openWizardEnabled()) {
                  this.openWizardPane(VsWizardEditModes.VIEWSHEET_PANE);
               }
               else {
                  this.onOpenEditPane.emit(this.model);
               }
               break;
            case "chart drill-up":
               this.drillAction(ChartConstants.DRILL_UP_OP);
               break;
            case "chart drill-down":
               this.drillAction(ChartConstants.DRILL_DOWN_OP);
               break;
            case "chart brush":
               this.brushChart();
               break;
            case "chart clear-brush":
               this.clearBrush();
               break;
            case "chart zoom":
               this.zoomChart();
               break;
            case "chart clear-zoom":
               this.clearZoom();
               break;
            case "chart exclude-data":
               this.zoomChart(true);
               break;
            case "chart reset-size":
               this.viewsheetClient.sendEvent(
                  CHART_PLOT_RESIZE_URL, new VSChartPlotResizeEvent(this.model, true, 0, false));
               break;
            case "chart resize-plot":
               this.model.showPlotResizers = true;
               break;
            case "chart show-data":
               this.showData();
               break;
            case "chart show-details":
               this.showDetails();
               break;
            case "chart open-max-mode":
               this.openMaxMode();
               break;
            case "chart close-max-mode":
               this.closeMaxMode();
               break;
            case "chart show-titles":
               this.showAllTitles();
               break;
            case "chart show-axes":
               this.showAllAxes();
               break;
            case "chart show-legends":
               this.showAllLegends();
               break;
            case "chart hide-title":
               this.hideTitle();
               break;
            case "chart show-title":
               this.showTitle();
               break;
            case "chart hide-axis":
               this.hideAxis();
               break;
            case "chart hide-legend":
               this.hideLegend();
               break;
            case "chart annotate":
               this.showAnnotationDialog(event.event);
               break;
            case "chart show-hyperlink":
               this.hyperlinkService.showHyperlinks(
                  event.event, this.getHyperlinks(),
                  this.dropdownService,
                  this.viewsheetClient.runtimeId,
                  this.vsInfo ? this.vsInfo.linkUri : null,
                  this.isForceTab());
               break;
            case "chart auto-refresh":
            case "chart manual-refresh":
               this.changeAutoRefresh();
               break;
            case "chart refresh":
               this.refresh();
               break;
            case "chart save-image-as":
               this.saveImageAs();
               break;
            case "chart filter":
               this.addFilter(event.event);
               break;
            case "chart show-format-pane":
               this.onOpenFormatPane.emit(this.model);
               break;
            case "chart multi-select":
               this.model.multiSelect = !this.model.multiSelect;
               break;
            case "menu actions":
               VSUtil.showDropdownMenus(event.event, this.getMenuActions(),
                  this.dropdownService, []);
               break;
            case "more actions":
               VSUtil.showDropdownMenus(event.event, this.getMoreActions(),
                  this.dropdownService, []);
               break;
            default:
               propertiesHandler.handleEvent(event, this.variableValues(this.getAssemblyName()), (<any> this.vsInfo).id);
            }
         });
         this.clickAction = value.clickAction;
      }
   }

   isResizeVisible = (name: string) => {
      return this.isActionVisible(name);
   };

   // The currently selected regions and the chart object in which they are contained
   private get chartSelection(): ChartSelection {
      return this.model ? this.model.chartSelection : null;
   }

   constructor(protected viewsheetClient: ViewsheetClientService,
               private chartService: VSChartService,
               private dndService: DndService,
               protected dataTipService: DataTipService,
               private changeDetectorRef: ChangeDetectorRef,
               private modalService: DialogService,
               private ngbModalService: NgbModal,
               private ngbModal: NgbModal,
               private dropdownService: FixedDropdownService,
               private modelService: ModelService,
               private downloadService: DownloadService,
               public contextProvider: ContextProvider,
               private scaleService: ScaleService,
               private injector: Injector,
               protected hyperlinkService: ShowHyperlinkService,
               private renderer: Renderer2,
               private debounceService: DebounceService,
               @Optional() private viewerResizeService: ViewerResizeService,
               private tabService: VSTabService,
               protected zone: NgZone,
               private richTextService: RichTextService,
               private fullScreenService: FullScreenService,
               private adhocFilterService: AdhocFilterService)
   {
      super(viewsheetClient, zone, contextProvider, dataTipService);
      this.resetShowEmptyAreaStatus();
      this.isBinding = contextProvider.binding;
      this.subscriptions = this.scaleService.getScale().subscribe((scale) => this.scale = scale);
   }

   private resetShowEmptyAreaStatus(): void {
      this.showEmptyArea = this.contextProvider.binding || this.contextProvider.composer;
   }

   changeAutoRefresh(): void {
      this.model.notAuto = !this.model.notAuto;

      if(!this.model.notAuto) {
         //refresh chart
         this.refreshChart();
      }
      else {
         //cancelEvent
         let chartEvent = new CancelEvent(this.model);
         this.viewsheetClient.sendEvent(CHART_CANCEL_QUERY_URL, chartEvent);
      }

      this.detectChanges();
   }

   refresh(): void {
      this.refreshChart();
   }

   refreshChart(): void {
      // show chart loading with icon
      this.showChartLoading(true, 3);
      let chartEvent = new VSChartRefreshEvent(this.model);
      this.viewsheetClient.sendEvent(CHART_REFRESH_URL, chartEvent);
   }

   brushChart(): void {
      const selectedString: string = this.getSelectedString();

      if(selectedString && (!this.popupShowing || this.mobileDevice)) {
         if(this.model.enabled) {
            // fade and disable actions immediately while chart is loading
            this.showChartLoading(false, 4);
         }

         const rangeSelection = this.model.chartSelection.rangeSelection;
         this.chartService.brushChart(this.model, selectedString, rangeSelection,
                                      this.viewsheetClient);
      }
   }

   brushChartArea(): void {
      if(this.vsWizard) {
         return;
      }

      if(!this.isActionVisibleInViewer("Brush")) {
         return;
      }

      if(ChartTool.isBrushable(this.model)) {
         this.brushChart();
      }
   }

   clearBrush(): void {
      this.chartService.clearBrush(this.model, this.viewsheetClient);
   }

   /**
    * Zoom regions or exclude regions from the chart
    *
    * @param exclude true to exclude, false to zoom
    */
   zoomChart(exclude: boolean = false): void {
      const selectedString: string = this.getSelectedString();

      if(selectedString) {
         this.dataTipService.hideDataTip();
         const rangeSelection = this.model.chartSelection.rangeSelection;
         this.chartService.zoomChart(this.model, selectedString, rangeSelection, exclude,
                                     this.viewsheetClient);
      }
   }

   clearZoom(): void {
      this.chartService.clearZoom(this.model, this.viewsheetClient);
      this.dataTipService.hideDataTip();
   }

   showData(sortInfo: SortInfo = null, format: FormatInfoModel = null,
            column: number[] = [], worksheetId: string = null, detailStyle: string = null,
            dndInfo: DetailDndInfo = null, newColName: string = null,
            toggleHide: boolean = false): void
   {
      if(sortInfo != null) {
         this.model.summarySortCol = sortInfo.col;
         this.model.summarySortVal = sortInfo.sortValue;
      }
      else if(this.model.summarySortVal != null && this.model.summarySortCol != null &&
         this.model.summarySortCol > -1)
      {
         sortInfo = {col: this.model.summarySortCol, sortValue: this.model.summarySortVal};
      }

      if(this.isDataTip()) {
         this.dataTipService.freeze();
      }

      this.previewTableType = "Data";
      this.chartService.showData(this.model, this.viewsheetClient, sortInfo, format,
                                 column, worksheetId, detailStyle, dndInfo, newColName, toggleHide);
   }

   showDetails(sortInfo: SortInfo = null, format: FormatInfoModel = null,
               column: number[] = [], worksheetId: string = null, detailStyle: string = null,
               dndInfo: DetailDndInfo = null, newColName: string = null,
               toggleHide: boolean = false): void
   {
      this.previewTableType = "Details";
      const selectedString = this.getSelectedString();
      const rangeSelection = this.model.chartSelection.rangeSelection;

      if(this.isDataTip()) {
         this.dataTipService.freeze();
      }

      this.chartService.showDetails(this.model, selectedString, rangeSelection,
                                    this.viewsheetClient, sortInfo, format, column,
                                    worksheetId, detailStyle, dndInfo, newColName, toggleHide);
   }

   /**
    * For show detail/show data dialog. Get the format model.
    * @returns {(wsId: string, column: number) => Observable<any>}
    */
   get formatFunction(): (wsId: string, column: number) => Observable<any> {
      const dataType: boolean = this.previewTableType === "Data";

      if(dataType) {
         return (wsId: string, column: number): Observable<any> => {
            const params = new HttpParams()
               .set("vsId", Tool.byteEncode(this.viewsheetClient.runtimeId))
               .set("wsId", Tool.byteEncode(wsId))
               .set("assemblyName", this.getAssemblyName())
               .set("columnIndex", column + "");

            return this.modelService.getModel(CHART_DATA_FORMAT_URI, params);
         };
      }
      else {
         return (wsId: string, column: number): Observable<any> => {
            const selectedString: string = this.getSelectedString();
            const rangeSelection = this.model.chartSelection.rangeSelection;

            const params = new HttpParams()
               .set("vsId", Tool.byteEncode(this.viewsheetClient.runtimeId))
               .set("wsId", Tool.byteEncode(wsId))
               .set("assemblyName", this.getAssemblyName())
               .set("columnIndex", column + "")
               .set("selected", selectedString)
               .set("rangeSelection", rangeSelection + "");

            return this.modelService.getModel(CHART_DETAIL_FORMAT_URI, params);
         };
      }
   }

   openMaxMode(): void {
      // make sure dom is fully initialized so the max-size is correct.
      setTimeout(() => {
         let chartEvent = new VSChartEvent(this.model, true, this.container);
         this.viewsheetClient.sendEvent(CHART_MAX_MODE_URL, chartEvent);
         this.maxModeChange.emit({assembly: this.model.absoluteName, maxMode: true});

         if(this.model.notAuto) {
            this.refreshChart();
         }
      }, 100);

      this.dataTipService.hideDataTip();
      this.adhocFilterService.hideAdhocFilter();
      this.model.selectedAnnotations = [];
   }

   closeMaxMode(): void {
      let chartEvent = new VSChartEvent(this.model, false, this.container);
      this.viewsheetClient.sendEvent(CHART_MAX_MODE_URL, chartEvent);
      this.maxModeChange.emit({assembly: this.model.absoluteName, maxMode: false});
      this.dataTipService.hideDataTip();
      this.adhocFilterService.hideAdhocFilter();
      this.onScroll(new Point(0, 0));
   }

   showAllTitles(): void {
      this.chartService.showAllTitles(this.model, this.viewsheetClient);
   }

   showAllAxes(): void {
      this.chartService.showAllAxes(this.model, this.viewsheetClient);
   }

   showAllLegends(): void {
      this.chartService.showAllLegends(this.model, this.viewsheetClient);
   }

   hideTitle(): void {
      let titleType: string = this.model.titleSelected ? "chart-title" : this.model.chartSelection.chartObject.areaName;
      this.chartService.hideTitle(this.model, this.viewsheetClient, titleType);
      this.clearSelection();
   }

   showTitle(): void {
      this.chartService.showTitle(this.model, this.viewsheetClient);
      this.clearSelection();
   }

   hideAxis(): void {
      let colName: string = ChartTool.getSelectedAxisColumnName(this.model);
      let secondary: boolean = (<Axis> this.model.chartSelection.chartObject).secondary;
      this.chartService.hideAxis(this.model, this.viewsheetClient, colName, secondary);
      this.model.chartSelection.chartObject = null;
      this.model.chartSelection.regions = null;
   }

   hideLegend(): void {
      let legendContainer: LegendContainer =
         ChartTool.getCurrentLegendContainer(this.model.chartSelection, this.model.legends);
      let field: string = legendContainer.field;
      let targetFields: string[] = legendContainer.targetFields;
      let aestheticType: string = legendContainer.aestheticType;
      this.chartService.hideLegend(this.model, this.viewsheetClient, field,
                                   targetFields, aestheticType, false, legendContainer.nodeAesthetic);

      if((<any> this.model.chartSelection.chartObject).aestheticType ==
         legendContainer.aestheticType)
      {
         this.model.selectedRegions = [];
         this.onLoadFormatModel.emit(this.model);
      }

      this.model.chartSelection.chartObject = null;
      this.model.chartSelection.regions = null;
   }

   showAnnotationDialog(event: MouseEvent): void {
      this.richTextService.showAnnotationDialog((content) => {
         this.addAnnotation(content, event);
      }).subscribe(dialog => {

         if(ChartTool.isPlotPointSelected(this.model)) {
            const tipIndex = this.getSelectedRegions("plot_area")
               .map((region) => region.tipIdx)
               .reverse()
               .find((index) => index >= 0);
            dialog.initialContent = this.getAnnotationMarkup(this.model.stringDictionary[tipIndex]);
         }
      });
   }

   addAnnotation(content: string, event: MouseEvent): void {
      const selectedRegion = this.getSelectedRegions("plot_area")
         .reverse()
         .find((region) => region != null);
      const chartContainerBounds = this.chartContainer.nativeElement.getBoundingClientRect();
      let x = (event.clientX - chartContainerBounds.left) / this.scale;
      let y = (event.clientY - chartContainerBounds.top) / this.scale;

      // triggered from mini menu
      if(event.button == 0) {
         x = this.model.objectFormat.width / 2;
         y = this.model.objectFormat.height / 2;
      }
      const chartEvent = new AddAnnotationEvent(content, x, y, this.getAssemblyName());

      if(ChartTool.isPlotPointSelected(this.model) && selectedRegion &&
         ChartTool.getMea(this.model, selectedRegion) != null)
      {
         chartEvent.setRow(selectedRegion.rowIdx);
         chartEvent.setCol(ChartTool.colIdx(this.model, selectedRegion));
         chartEvent.setMeasureName(ChartTool.getMea(this.model, selectedRegion));
         this.viewsheetClient.sendEvent(CHART_ADD_DATA_ANNOTATION_URL, chartEvent);
      }
      else {
         this.viewsheetClient.sendEvent(CHART_ADD_ASSEMBLY_ANNOTATION_URL, chartEvent);
      }
   }

   drill(payload: ChartDrillInfo): void {
      this.chartService.drill(this.model, payload, this.viewsheetClient, this.appSize);
   }

   drillAction(drillOp: string) {
      const selectedString: string = this.getSelectedString(this.chartSelection, false);

      if(!!selectedString) {
         if(this.model.enabled) {
            // fade and disable actions immediately while chart is loading
            this.showChartLoading(false, 5);
         }

         const rangeSelection = this.model.chartSelection.rangeSelection;
         this.chartService.drillChart(this.model, selectedString, rangeSelection, drillOp,
                                      this.viewsheetClient, this.appSize);
      }
   }

   /**
    * Given a selection send a flyover event. If there are already currently selected
    * regions on the plot then those should superceded the hover selection so use those
    * instead. Debounces every 100ms.
    */
   sendFlyover(flyData: FlyoverData): void {
      // no need to do flyover in max mode as others are not visible.
      if(this.model.maxMode) {
         return;
      }

      if(flyData.noCurrentSelection && flyData.payload.context) {
         this.chartService.clearCanvas(flyData.payload.context);
         ChartTool.drawRegions(flyData.payload.context, flyData.payload.regions,
                               flyData.payload.canvasX, flyData.payload.canvasY, this.scale);
      }

      // sending flyover can cause graph to be cancelled. avoid unnecessary event (48645).
      if(this.model.lastFlyover != null && this.model.lastFlyover == flyData.plotCondition) {
         return;
      }

      // prevent infinite recursion when clear other selections
      if(!this.model.sendingFlyover) {
         this.model.sendingFlyover = true;
         this.model.lastFlyover = flyData.plotCondition;
         // if flyover is defined, having multiple selected chart/table is confusing. (51095)
         this.clearOtherSelections();
         this.chartService.sendFlyover(this.model, flyData.plotCondition, this.viewsheetClient);
         this.model.sendingFlyover = false;
      }
   }

   /**
    * Show a datatip at the current mouse position.
    */
   showDataTip(tipData: any): void {
      if(this.viewer && this.dataTipService.dataTipName != this.getAssemblyName() &&
         !this.model.changedByScript)
      {
         if(this.isDataTip() && this.mobileDevice) {
            return;
         }

         let plotCondition = this.getSelectedString(tipData.payload);
         let tip = plotCondition ? this.model.dataTip : null;

         if(tip != null && this.dataTipService.isFrozen()) {
            this.dataTipService.unfreeze();
         }

         this.chartService.showDataTip(this.dataTipService, tipData.tooltipLeft,
                                       tipData.tooltipTop, this.getAssemblyName(),
                                       tip, plotCondition, this.model.dataTipAlpha);
      }
   }

   /**
    * Calculate the difference in the new position of the mouse and the old position
    * of the axis to figure out the new size of the resized axis. Then send the position
    * to the server
    */
   endAxisResize(resizeData: any): void {
      this.chartService.endAxisResize(
         resizeData.axisResizeInfo, this.model.stringDictionary,
         resizeData.eventXdown, resizeData.eventYdown,
         resizeData.clientX, resizeData.clientY,
         this.model, this.viewsheetClient);
   }

   /**
    * End the resize and call chart service resize
    */
   endLegendResize(resizeData: any): void {
      this.chartService.resizeLegend(
         resizeData.legendResizeInfo, this.model,
         resizeData.eventXdown, resizeData.eventYdown,
         resizeData.clientX, resizeData.clientY,
         resizeData.aestheticType, resizeData.field,
         resizeData.targetFields, resizeData.nodeAesthetic, this.viewsheetClient);
   }

   endLegendMove(resizeData: any): void {
      this.chartService.moveLegendByInfo(this.model, resizeData.interactInfo,
                                         resizeData.aestheticType, resizeData.field,
                                         resizeData.targetFields, resizeData.nodeAesthetic,
                                         this.viewsheetClient);
   }

   sortAxis(axis: Axis): void {
      this.chartService.sortAxis(this.model, axis, this.viewsheetClient);
   }

   onScroll(point: Point) {
      this.scrollLeft = point.x;
      this.scrollTop = point.y;
   }

   mouseLeave(event: MouseEvent) {
      if(this.model.dataTip && this.dataTipService.isDataTipVisible(this.model.dataTip) &&
         !this.dataTipService.isFrozen())
      {
         this.debounceService.debounce(DataTipService.DEBOUNCE_KEY, () => {
            const tipElement: HTMLElement = document.getElementsByClassName(
               "current-datatip-" + this.model.dataTip.replace(/ /g, "_"))[0] as HTMLElement;

            if(!tipElement || !GuiTool.isMouseIn(tipElement, event)) {
               this.zone.run(() => this.dataTipService.hideDataTip(true));
            }
         }, 300, []);
      }
   }

   /**
    * Return this chart's selected regions
    * @param areaName if passed, only return the specified area's selected regions
    * @returns {ChartRegion[]} A list of the selected ChartRegions
    */
   public getSelectedRegions(areaName: ChartAreaName = null): ChartRegion[] {
      return ChartTool.getSelectedRegions(this.model.chartSelection, areaName);
   }

   private getSelectedString(chartSelection: ChartSelection = this.chartSelection,
                             includeParent: boolean = true): string
   {
      return ChartTool.getSelectedString(this.model, chartSelection, includeParent);
   }

   clickHyperlink(event: MouseEvent) {
      if(this.clickAction && event.button === 0) {
         this.clickAction.action(event);
         event.stopPropagation();
      }
   }

   /**
    * hide resizers and clear selected annotations when selecting part of the chart
    */
   selectRegion(selection: ChartSelection): void {
      if(!this.isDataTip()) {
         if(selection && selection.regions.length) {
            this.dataTipService.freeze();
         }
         else {
            this.dataTipService.unfreeze();
         }
      }

      this.model.showPlotResizers = false;
      this.model.selectedAnnotations = [];
      this.model.titleSelected = false;
      this.model.selectedRegions = [];
   }

   selectTitle(): void {
      if(this.context.preview) {
         return;
      }

      this.clearSelection();
      this.model.selectedRegions = [DataPathConstants.TITLE];
      this.model.titleSelected = true;
   }

   protected getHyperlinks(): HyperlinkModel[] {
      const hyperlinkRegion = this.getSelectedRegions();
      return hyperlinkRegion.length > 0 && hyperlinkRegion[0].hyperlinks ? hyperlinkRegion[0].hyperlinks : [];
   }

   ngOnInit(): void {
      // this may be set in binding pane to start in max mode
      if(this.model.maxMode && !this.context.embed) {
         this.openMaxMode();
      }

      if(this.viewerResizeService) {
         this.resizeSubscription = this.viewerResizeService.resized.subscribe(() => {
            const key = `${this.model.absoluteName}_resizeListener`;

            this.debounceService.debounce(key, () => {
               if(this.model.maxMode) {
                  this.openMaxMode();
               }
            }, 200, []);
         });
      }

      this.tabSubscription = this.tabService.tabDeselected.subscribe(name => {
         if(name == this.model.absoluteName || name == this.model.container) {
            this.clearSelection();
         }
      });
   }

   ngOnDestroy(): void {
      super.ngOnDestroy();

      this.subscriptions.unsubscribe();

      if(this.actionSubscription) {
         this.actionSubscription.unsubscribe();
         this.actionSubscription = null;
      }

      if(this.resizeSubscription) {
         this.resizeSubscription.unsubscribe();
         this.resizeSubscription = null;
      }

      if(this.tabSubscription) {
         this.tabSubscription.unsubscribe();
         this.tabSubscription = null;
      }
   }

   clearSelection(): void {
      this.model.selectedRegions = [];
      this.model.titleSelected = null;

      if(!!this.chartArea) {
         this.chartArea.clearSelection();
      }
   }

   get runtimeId(): string {
      return this.viewsheetClient.runtimeId;
   }

   getActions(): AssemblyActionGroup[] {
      return this._actions ? this._actions.showingActions : [];
   }

   getMenuActions(): AssemblyActionGroup[] {
      let groups =  this._actions ? this._actions.menuActions : [];
      return groups;
   }

   getMoreActions(): AssemblyActionGroup[] {
      let groups =  this._actions ? this._actions.getMoreActions() : [];
      return groups;
   }

   getImageUrlPrefix(): string {
      return this.vsInfo && this.vsInfo.linkUri ?
         this.vsInfo.linkUri + "getAssemblyImage" +
         "/" + Tool.byteEncode(this.runtimeId) +
         "/" + Tool.byteEncode(this.getAssemblyName()) : null;
   }

   saveImageAs(): void {
      let dialog: ImageFormatSelectComponent = ComponentTool.showDialog(this.ngbModalService, ImageFormatSelectComponent, (isSvg: boolean) => {
            if(this.vsInfo && this.vsInfo.linkUri) {
               const url = this.vsInfo.linkUri + "downloadAssemblyImage" +
               "/" + Tool.byteEncode(this.runtimeId) +
               "/" + Tool.byteEncode(this.getAssemblyName()) +
               "/" + this.model.objectFormat.width +
               "/" + this.model.objectFormat.height +
               "/" + isSvg;
            this.downloadService.download(url);
         }
      },
      {backdrop: "static"}, () => {
      });
   }

   private addFilter(event: MouseEvent): void {
      let colName: string;
      let dimension: boolean = false;

      if(this.model.chartSelection && this.model.chartSelection.regions &&
         this.model.chartSelection.regions.length > 0)
      {
         let index: number = -1;

         if(ChartTool.dimIdx(this.model, this.model.chartSelection.regions[0]) >= 0) {
            index = ChartTool.dimIdx(this.model, this.model.chartSelection.regions[0]);
            dimension = true;
         }
         else {
            index = ChartTool.meaIdx(this.model, this.model.chartSelection.regions[0]);
         }

         colName = this.model.stringDictionary[index];
      }

      const componentRect: ClientRect =
         this.chartContainer.nativeElement.getBoundingClientRect();
      const mouseLoc: {clientX: number, clientY: number} = this.mobileDevice && this.lastClick
         ? this.lastClick : event;
      const top: number = Math.max(mouseLoc.clientY - componentRect.top, 1);
      const left: number = Math.max(mouseLoc.clientX - componentRect.left, 1);
      const vsEvent = new FilterChartEvent(this.model.absoluteName, colName, top, left, dimension);
      vsEvent.legend = ChartTool.isLegendSelected(this.model);
      this.viewsheetClient.sendEvent(CHART_ADD_FILTER_URL, vsEvent);
   }

   @HostListener("click", ["$event"])
   getFormats(event: MouseEvent): void {
      this.onLoadFormatModel.emit(this.model);
   }

   showChartLoading(iconVisible: boolean = false, f: any): void {
      this.chartLoadingIcon = iconVisible;

      if(!this.chartLoading) {
         this.chartLoading = true;
         this.loadingStateChanged(this.chartLoading);
         this.detectChanges();
      }
   }

   clearChartLoading(): void {
      if(this.chartLoading) {
         this.noChartData = this.emptyChart || this.model.noData;
         this.chartLoading = false;
         this.loadingStateChanged(this.chartLoading);
         this.detectChanges();
      }
   }

   processClearChartLoadingCommand(command: ClearChartLoadingCommand): void {
      this.clearChartLoading();
   }

   processClearMapPanCommand(command: ClearMapPanCommand): void {
      this.chartArea.clearPan();
   }

   processSetChartAreasCommand(command: SetChartAreasCommand): void {
      let oldModel = Tool.clone(this.model);

      if(this.chartArea != null && (!this.dataTipService.isDataTip(this.model.absoluteName) ||
         this.dataTipService.isDataTip(this.model.absoluteName) &&
         this.dataTipService.dataTipName == this.model.absoluteName))
      {
         this.chartArea.resetScrollPosition(oldModel == null ? null : oldModel.plot, command.plot);
      }

      Object.assign(this.model, command);
      ChartTool.fillIndex(this.model);

      if(this.chartSelection && this.chartSelection.chartObject && this.chartSelection.regions) {
         const oldRegions = oldModel.chartSelection?.regions;
         const hadSelection = oldRegions && oldRegions.length > 0;
         // make sure the same values (e.g. axis labels) are selected
         this.model.chartSelection = ChartTool.updateChartSelection(this.model, oldModel);
         const newRegions = this.model.chartSelection?.regions;
         const hasSelection = newRegions && newRegions.length > 0;

         // if existing selection is cleared because the previous data is no longer on the chart,
         // cleart the flyover conditions so we don't have flyover applied with a condition
         // that doesn't have a corresponding selection on the view. (60175)
         if(hadSelection && !hasSelection) {
            this.chartService.clearFlyover(this.model, this.viewsheetClient);
         }

         this.model.clearCanvasSubject.next(null);
      }

      // Check if chart has no axes
      if(command.axes.every(axis => axis.tiles.length == 0)) {
         this.chartArea.axisLoaded(true);
      }

      this.checkNoData();
      this.resetShowEmptyAreaStatus();
      this.modelTS = (new Date()).getTime();
      this.detectChanges();

      if(this.mobileDevice) {
         this.showHints = true;
         setTimeout(() => this.showHints = false, 1000);
      }
   }

   checkNoData() {
      // for vs, if model is invalid, it will show loading and not clear loading.
      if(this.chartLoading && (this.model?.invalid || this.model.noData)) {
         this.clearChartLoading();
         return;
      }

      if(this.isBinding) {
         this.noChartData = false;
         return;
      }

      if(!this.chartLoading) {
         // 1. when chart is loading, waiting for loading is finish and then to check no data or not
         // 2. for chart is not plot and no data is all true, show no data available.
         //    if only no data, but have some plot. we should not show no data label
         //    if only no plot but have some data on some places, should not show no data label.
         // 3. if brush, no data will also be false, for it should show gray chart if no data.
         this.noChartData = this.emptyChart || this.model.noData;
      }
      else if(!this.model.noData) {
         this.noChartData = false;
      }
   }

   /**
    * Check whether the chart has any data (composer).
    */
   get emptyChart(): boolean {
      return this.showEmptyArea && !this.model.invalid &&
         (!this.model.axes || this.model.axes.length == 0);
   }

   /**
    * When using the zoom slider, display the chart regions as they would appear if
    * applied with the selected zoom level
    */
   sizeSliderTick(sizeRatio: number, vertical: boolean) {
      if(this.model.showResizePreview) {
         const currentScale = vertical ? this.model.heightRatio : this.model.widthRatio;
         const scale = sizeRatio / currentScale;
         // epsilon should be less than the tick size of the input
         const epsilon = 0.001;
         let clear = false;

         if(Math.abs(scale - 1) < epsilon) {
            clear = true;
         }

         this.plotScaleInfo = {
            scale,
            vertical,
            clear
         };
      }
   }

   changeSizeRatio(sizeRatio: number, vertical: boolean): void {
      const chartEvent = new VSChartPlotResizeEvent(this.model, false, sizeRatio, vertical);

      if(this.isIE) {
         let key = this.getAssemblyName() + (vertical ? "vertical-resize-slider" : "horizontal-resize-slider");
         this.debounceService.debounce(key, () => {
            this.viewsheetClient.sendEvent(CHART_PLOT_RESIZE_URL, chartEvent);
         }, 400, []);
      }
      else {
         this.viewsheetClient.sendEvent(CHART_PLOT_RESIZE_URL, chartEvent);
      }
   }

   public changeTitle(title: string): void {
      let event: ChangeVSObjectTextEvent = new ChangeVSObjectTextEvent(
         this.model.absoluteName, title);
      const url = this.contextProvider.vsWizardPreview
         ? CHART_WIZARD_CHANGE_TITLE_URL
         : CHART_CHANGE_TITLE_URL;

      this.model.title = title;
      this.viewsheetClient.sendEvent(url, event);
   }

   titleResizeMove(event: any): void {
      this.onTitleResizeMove.emit(event.rect.height);
   }

   titleResizeEnd(): void {
      this.onTitleResizeEnd.emit();
   }

   isDataTip(): boolean {
      return this.viewer && this.dataTipService && this.model &&
         (this.dataTipService.isDataTip(this.model.absoluteName) ||
          this.model.container && this.dataTipService.isDataTip(this.model.container));
   }

   isForceTab(): boolean {
      return this.contextProvider.composer || this.contextProvider.preview;
   }

   /**
    * Clears state that needs to be cleared when the chart is recreated.
    */
   private clearChartState(): void {
      this.model.chartSelection = null;
      this.dataTipService.hideDataTip();
   }

   // AnnotationVSUtil.resetAnnotationPosition set the relative position of annotation to
   // bounds including legends, but contentBounds doesn't include lgends. Merge the content
   // and legends bounds here to match.
   get annotationContainerBounds(): Rectangle {
      const contentBounds: Rectangular = this.model.plot.layoutBounds;
      let titleHeight = 0;

      if(this.model.titleVisible) {
         titleHeight = this.model.titleFormat.height;
      }

      const chartContainerBounds = this.chartContainerBounds;

      return new Rectangle(chartContainerBounds.x + contentBounds.x + this.model.paddingLeft,
         chartContainerBounds.y + contentBounds.y + this.model.paddingTop + titleHeight,
         contentBounds.width, contentBounds.height);
   }

   get chartContainerBounds(): Rectangle {
      return new Rectangle(this.model.objectFormat.left, this.model.objectFormat.top,
         this.model.objectFormat.width, this.model.objectFormat.height);
   }

   get showDCIcon(): boolean {
      return !this.dataTipService.isDataTipVisible(this.getAssemblyName()) &&
         !this.mobileDevice && !this.contextProvider.vsWizardPreview && !!this.model.dateComparisonDescription;
   }

   click(event: MouseEvent) {
      this.lastClick = {clientX: event.clientX, clientY: event.clientY};
   }

   protected detectChanges(): void {
      this.changeDetectorRef.detectChanges();
   }

   zoomInMap(steps: number) {
      this.chartService.zoomMap(this.model, this.viewsheetClient, steps);
   }

   zoomOutMap(steps: number) {
      this.chartService.zoomMap(this.model, this.viewsheetClient, -steps);
   }

   zoomByWheel(event: WheelEvent) {
      if(!GraphTypes.isGeo(this.model?.chartType)) {
         return;
      }

      event.preventDefault(); // don't scroll pane
      // up (zoom-in) is negative
      const steps = -event.deltaY / 100;
      this.chartService.zoomMap(this.model, this.viewsheetClient, steps);
   }

   clearPanZoomMap() {
      this.chartService.clearPanZoomMap(this.model, this.viewsheetClient);
   }

   panMap(pan: Point) {
      this.chartService.panMap(this.model, this.viewsheetClient, pan.x, pan.y);
   }

   get dateComparisonTipStyle(): string {
      return "right: " + (this.model.drillTip ? 20 : 3) + "px";
   }

   isTipOverlapToolbar(): boolean {
      if(this.contextProvider.vsWizardPreview) {
         return true;
      }

      return !this.embeddedVS && !this.contextProvider.binding && this.model.objectFormat.top <= 20 ||
         this.embeddedVS && this.embeddedVSBounds.y <= 20;
   }

   showDateComparisonDialog() {
      this.actions.onAssemblyActionEvent.emit(
         new AssemblyActionEvent("chart date-comparison", this.model, null));
   }

   protected isPopupOrDataTipSource(): boolean {
      return this.dataTipService.isDataTipSource(this.model.absoluteName);
   }
}
