/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { Injectable } from "@angular/core";
import { ViewsheetClientService } from "../../../../common/viewsheet-client";
import { AreaResizeInfo } from "../../../../graph/model/area-resize-info";
import { Axis } from "../../../../graph/model/axis";
import { ChartDrillInfo } from "../../../../graph/model/chart-drill-info";
import { ChartSelection } from "../../../../graph/model/chart-selection";
import { ChartTool } from "../../../../graph/model/chart-tool";
import { FlyoverInfo } from "../../../../graph/model/flyover-info";
import { LegendContainer } from "../../../../graph/model/legend-container";
import { LegendOption } from "../../../../graph/model/legend-option";
import { LegendResizeInfo } from "../../../../graph/model/legend-resize-info";
import { ChartService } from "../../../../graph/services/chart.service";
import { InteractArea, InteractInfo } from "../../../../widget/resize/element-interact.directive";
import { DebounceService } from "../../../../widget/services/debounce.service";
import {
   VSChartEvent,
   VSChartAxesVisibilityEvent,
   VSChartAxisResizeEvent,
   VSChartBrushEvent,
   VSChartDrillActionEvent,
   VSChartDrillEvent,
   VSChartFlyoverEvent,
   VSChartLegendRelocateEvent,
   VSChartLegendResizeEvent,
   VSChartLegendsVisibilityEvent,
   VSChartShowDataEvent,
   VSChartShowDetailsEvent,
   VSChartSortAxisEvent,
   VSChartTitlesVisibilityEvent,
   VSChartZoomEvent,
   VSMapZoomEvent,
   VSMapPanEvent
} from "../../../event/vs-chart-event";
import { VSChartModel } from "../../../model/vs-chart-model";
import { CheckFormDataService } from "../../../util/check-form-data.service";
import { DataTipService } from "../../data-tip/data-tip.service";
import { SortInfo } from "../../table/sort-info";
import { FormatInfoModel } from "../../../../common/data/format-info-model";
import { ChartConstants } from "../../../../common/util/chart-constants";
import { DetailDndInfo } from "../../table/detail-dnd-info";
import { Dimension } from "../../../../common/data/dimension";

@Injectable()
export class VSChartService extends ChartService {
   constructor(private formDataService: CheckFormDataService,
               private debounceService: DebounceService)
   {
      super();
   }

   private zoom = 0;

   /**
    * JS Event Handlers
    */
   public drill(model: VSChartModel, payload: ChartDrillInfo,
                client: ViewsheetClientService, appSize: Dimension): void
   {
      let chartEvent = new VSChartDrillEvent(model, payload.axisType,
         payload.field, ChartConstants.DRILL_UP_OP == payload.drillOp, appSize.width, appSize.height);
      client.sendEvent("/events/vschart/drill", chartEvent);
   }

   public drillChart(model: VSChartModel, selectedString: string, rangeSelection: boolean,
                     drillOp: string, client: ViewsheetClientService, appSize: Dimension)
   {
      let chartEvent: VSChartDrillActionEvent = new VSChartDrillActionEvent(model, selectedString,
         rangeSelection, ChartConstants.DRILL_UP_OP == drillOp, appSize.width, appSize.height);
      client.sendEvent("/events/vschart/drill-action", chartEvent);
      model.chartSelection = null;
      this.clearFlyover(model, client);
   }

   public brushChart(model: VSChartModel,
                     selectedString: string,
                     rangeSelection: boolean,
                     client: ViewsheetClientService): void
   {
      let chartEvent: VSChartBrushEvent = new VSChartBrushEvent(
         model, selectedString, rangeSelection);
      client.sendEvent("/events/vschart/brush", chartEvent);
   }

   public clearBrush(model: VSChartModel, client: ViewsheetClientService): void {
      let chartEvent: VSChartBrushEvent = new VSChartBrushEvent(
         model, null, false);
      client.sendEvent("/events/vschart/brush", chartEvent);
   }

   public zoomChart(model: VSChartModel,
                    selectedString: string,
                    rangeSelection: boolean,
                    exclude: boolean = false,
                    client: ViewsheetClientService): void
   {
      let chartEvent: VSChartZoomEvent =
         new VSChartZoomEvent(model, selectedString, rangeSelection, exclude);
      // Bug #10362 clear selection on zoom
      model.chartSelection = null;
      client.sendEvent("/events/vschart/zoom", chartEvent);
      this.clearFlyover(model, client);
   }

   public clearZoom(model: VSChartModel, client: ViewsheetClientService): void {
      let chartEvent: VSChartZoomEvent =
         new VSChartZoomEvent(model, null, false, false);
      // Bug #10362 clear selection on zoom
      model.chartSelection = null;
      client.sendEvent("/events/vschart/zoom", chartEvent);
      this.clearFlyover(model, client);
   }

   public zoomMap(model: VSChartModel, client: ViewsheetClientService, steps: number): void {
      // accummulate multiple events into one
      this.zoom += steps;

      this.debounceService.debounce("zoomMap", () => {
         if(this.zoom != 0) {
            let chartEvent = new VSMapZoomEvent(model, this.zoom);
            this.zoom = 0;
            client.sendEvent("/events/vsmap/zoom", chartEvent);
         }
      }, 300);
   }

   public panMap(model: VSChartModel, client: ViewsheetClientService,
                 panX: number, panY: number): void
   {
      let chartEvent = new VSMapPanEvent(model, panX, panY);
      client.sendEvent("/events/vsmap/pan", chartEvent);
   }

   public clearPanZoomMap(model: VSChartModel, client: ViewsheetClientService) {
      let chartEvent = new VSChartEvent(model);
      client.sendEvent("/events/vsmap/clear", chartEvent);
   }

   public clearFlyover(model: VSChartModel, client: ViewsheetClientService): void {
      model.lastFlyover = null;
      this.sendFlyover(model, "", client);
   }

   public sendFlyover(model: VSChartModel, plotCondition: string,
                      client: ViewsheetClientService): void
   {
      this.debounceService.debounce(model.absoluteName + "_flyover", () => {
         this.formDataService.checkFormData(
            client.runtimeId, model.absoluteName, plotCondition,
            () => {
               const chartEvent = new VSChartFlyoverEvent(model, plotCondition);
               client.sendEvent("/events/vschart/flyover", chartEvent);
            });
      }, 100, []);
   }

   public showDataTip(dataTipService: DataTipService, x: number, y: number, assembly: string,
                      tip: string, plotCondition: string, alpha: number): void
   {
      dataTipService.showDataTip(assembly, tip, x, y, plotCondition, alpha);
   }

   public endAxisResize(axisInfo: AreaResizeInfo, stringDictionary: string[], x0: number,
                        y0: number, x1: number, y1: number, model: VSChartModel,
                        client: ViewsheetClientService): ChartSelection {
      if(axisInfo) {
         let axisField = null;

         if(axisInfo.fields && axisInfo.fields.length) {
            axisField = axisInfo.fields[axisInfo.resizeIdx];
         }
         else {
            const region = axisInfo.regions
               .find(r => ChartTool.axisFldIdx(model, r) != null);

            if(region) {
               const axisFieldIndex = ChartTool.axisFldIdx(model, region);
               axisField = stringDictionary[axisFieldIndex];
            }
         }

         if(!axisField) {
            return null;
         }

         let chartEvent: VSChartAxisResizeEvent = null;
         let xType: boolean = (<Axis> axisInfo.chartObject).axisType == "x";
         let endPos: number = xType ? y1 : x1;
         endPos = Math.max(Math.min(endPos, axisInfo.maxPosition), axisInfo.minPosition);
         // if setting axis to smallest size, reset axis to default size
         let resetDefault: boolean = false;
         let diff: number = xType ? endPos - y0 : endPos - x0;
         let sizeIndex: number = (<Axis> axisInfo.chartObject).axisFields.indexOf(axisField);
         let size: number = (<Axis> axisInfo.chartObject).axisSizes[sizeIndex];

         if(axisInfo.chartObject.areaName === "bottom_x_axis") {
            resetDefault = endPos == axisInfo.maxPosition;
            size = size - diff;
            chartEvent = new VSChartAxisResizeEvent(model, "x", axisField, resetDefault ? 0 : size);
         }
         else if(axisInfo.chartObject.areaName === "top_x_axis") {
            resetDefault = endPos == axisInfo.minPosition;
            size = size + diff;
            chartEvent = new VSChartAxisResizeEvent(model, "x", axisField, resetDefault ? 0 : size);
         }
         else if(axisInfo.chartObject.areaName === "left_y_axis") {
            resetDefault = endPos == axisInfo.minPosition;
            size = size + diff;
            chartEvent = new VSChartAxisResizeEvent(model, "y", axisField, resetDefault ? 0 : size);
         }
         else if(axisInfo.chartObject.areaName === "right_y_axis") {
            resetDefault = endPos == axisInfo.maxPosition;
            size = size - diff;
            chartEvent = new VSChartAxisResizeEvent(model, "y", axisField, resetDefault ? 0 : size);
         }

         client.sendEvent("/events/vschart/resize-axis", chartEvent);
         axisInfo = null;
      }

      return axisInfo;
   }

   public showData(model: VSChartModel, client: ViewsheetClientService,
                   sortInfo: SortInfo, format: FormatInfoModel, column: number[],
                   worksheetId: string, detailStyle: string = null,
                   dndInfo: DetailDndInfo = null, newColName: string = null,
                   toggleHide: boolean = false): void
   {
      let chartEvent = new VSChartShowDataEvent(model, sortInfo, format, column, worksheetId,
         detailStyle, dndInfo, newColName, toggleHide);
      client.sendEvent("/events/vschart/showdata", chartEvent);
   }

   public showDetails(model: VSChartModel, selectedString: string, rangeSelection: boolean,
                      client: ViewsheetClientService, sortInfo: SortInfo,
                      format: FormatInfoModel, column: number[], worksheetId: string,
                      detailStyle: string = null, dndInfo: DetailDndInfo = null,
                      newColName: string = null, toggleHide: boolean = false): void
   {
      let chartEvent = new VSChartShowDetailsEvent(model, selectedString, rangeSelection,
         sortInfo, format, column, worksheetId, detailStyle, dndInfo, newColName, toggleHide);
      client.sendEvent("/events/vschart/showdetails", chartEvent);
   }

   public resizeLegend(legendInfo: LegendResizeInfo, model: VSChartModel,
                       x0: number, y0: number, x1: number, y1: number,
                       aestheticType: string, field: string, targetFields: string[],
                       nodeAesthetic: boolean, client: ViewsheetClientService): void
   {
      if(x0 == x1 && y0 == y1) {
         return;
      }

      let legendContainer: LegendContainer = legendInfo.legend;
      let width = legendContainer.bounds.width;
      let height = legendContainer.bounds.height;
      let xType: boolean = !legendInfo.vertical;
      let endPos: number = xType ? y1 : x1;
      endPos = Math.max(Math.min(endPos, legendInfo.maxPosition), legendInfo.minPosition);
      let diff: number = xType ? endPos - y0 : endPos - x0;

      switch(model.legendOption) {
         case LegendOption.BOTTOM:
            height -= diff;
            break;
         case LegendOption.TOP:
            height += diff;
            break;
         case LegendOption.RIGHT:
            width -= diff;
            break;
         case LegendOption.LEFT:
            width += diff;
            break;
         case LegendOption.IN_PLACE:
            if(legendInfo.vertical) {
               width += diff;
            }
            else {
               height += diff;
            }

            break;
         default:
            break;
      }

      let chartEvent = new VSChartLegendResizeEvent(model, width, height, aestheticType, field,
                                                    targetFields, nodeAesthetic);
      let controller = "/events/vschart/resize-legend";
      client.sendEvent(controller, chartEvent);
   }

   public moveLegendByInfo(model: VSChartModel, interactInfo: InteractInfo,
                           aestheticType: string, field: string, targetFields: string[],
                           nodeAesthetic: boolean, client: ViewsheetClientService): void
   {
      let legendOption: LegendOption;

      switch(interactInfo.area) {
      case InteractArea.TOP_EDGE:
         legendOption = LegendOption.TOP;
         break;
      case InteractArea.RIGHT_EDGE:
         legendOption = LegendOption.RIGHT;
         break;
      case InteractArea.BOTTOM_EDGE:
         legendOption = LegendOption.BOTTOM;
         break;
      case InteractArea.LEFT_EDGE:
         legendOption = LegendOption.LEFT;
         break;
      case InteractArea.CENTER:
         legendOption = LegendOption.IN_PLACE;
         break;
      default:
         // Shouldn't happen since this event gets triggered from the legend itself
         // so there must be some type of legend option. Otherwise it was improperly
         // set somewhere
         legendOption = LegendOption.NO_LEGEND;
         break;
      }

      this.moveLegend(model, legendOption, interactInfo.x, interactInfo.y,
                      aestheticType, field, targetFields, nodeAesthetic, client);
   }

   public moveLegend(model: VSChartModel, legendOption: LegendOption, x: number, y: number,
                     aestheticType: string, field: string, targetFields: string[],
                     nodeAesthetic: boolean, client: ViewsheetClientService): void
   {
      const xpos = x / model.objectFormat.width;
      const ypos = y / model.objectFormat.height;
      let chartEvent = new VSChartLegendRelocateEvent(model, legendOption, xpos, ypos,
                                                      aestheticType, field, targetFields,
                                                      nodeAesthetic);
      client.sendEvent("/events/vschart/move-legend", chartEvent);
   }

   public sortAxis(model: VSChartModel, axis: Axis, client: ViewsheetClientService): void {
      let sortField = axis.sortField;
      let sortOp = axis.sortOp;
      let chartEvent = new VSChartSortAxisEvent(model, sortOp, sortField);
      client.sendEvent("/events/vschart/sort-axis", chartEvent);
   }

   public showAllTitles(model: VSChartModel, client: ViewsheetClientService): void {
      let chartEvent = new VSChartTitlesVisibilityEvent(model, false, null);
      client.sendEvent("/events/vschart/titles-visibility", chartEvent);
   }

   public showAllAxes(model: VSChartModel, client: ViewsheetClientService): void {
      let chartEvent = new VSChartAxesVisibilityEvent(model, false, null, false);
      client.sendEvent("/events/vschart/axes-visibility", chartEvent);
   }

   public showAllLegends(model: VSChartModel, client: ViewsheetClientService, wizard?: boolean): void {
      let chartEvent = new VSChartLegendsVisibilityEvent(model, false, null,
         null, null, wizard);
      client.sendEvent("/events/vschart/legends-visibility", chartEvent);
   }

   public hideTitle(model: VSChartModel, client: ViewsheetClientService, titleType: string): void {
      let chartEvent = new VSChartTitlesVisibilityEvent(model, true, titleType);
      client.sendEvent("/events/vschart/titles-visibility", chartEvent);
   }

   public showTitle(model: VSChartModel, client: ViewsheetClientService): void {
         let chartEvent = new VSChartTitlesVisibilityEvent(model, true, "chart-title-true");
         client.sendEvent("/events/vschart/titles-visibility", chartEvent);
      }

   public hideAxis(model: VSChartModel, client: ViewsheetClientService,
                   colName: string, secondary: boolean): void
   {
      let chartEvent = new VSChartAxesVisibilityEvent(model, true, colName, secondary);
      client.sendEvent("/events/vschart/axes-visibility", chartEvent);
   }

   public hideLegend(model: VSChartModel, client: ViewsheetClientService, field: string,
                     targetFields: string[], aestheticType: string, wizard?: boolean,
                     nodeAesthetic?: boolean): void
   {
      let colorCount = model.legends.filter(legend => legend.aestheticType == "Color").length;
      let colorMerged = colorCount < ("Color" == aestheticType ? 2 : 1);
      let chartEvent = new VSChartLegendsVisibilityEvent(model, true, field,
         targetFields, aestheticType, wizard, colorMerged, nodeAesthetic);
      client.sendEvent("/events/vschart/legends-visibility", chartEvent);
   }
}
