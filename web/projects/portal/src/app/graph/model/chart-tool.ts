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
import { Tool } from "../../../../../shared/util/tool";
import { GuiTool } from "../../common/util/gui-tool";
import { GraphTypes } from "../../common/graph-types";
import { ChartConstants } from "../../common/util/chart-constants";
import { Axis } from "./axis";
import { ChartAreaName } from "./chart-area-name";
import { ChartModel } from "./chart-model";
import { ChartObject } from "./chart-object";
import { ChartRegion } from "./chart-region";
import { ChartSelection } from "./chart-selection";
import { Legend } from "./legend";
import { LegendContainer } from "./legend-container";
import { Title } from "./title";
import { GeoJSON } from "geojson";

export namespace ChartTool {
   export const RECT = 8; // defined in ChartRegion
   export const ELLIPSE = 9;
   export const LINE = 10;

   /**
    * Return this chart's selected regions
    *
    * @param chartSelection the chart selection model.
    * @param areaName       if passed, only return the specified area's selected regions.
    *
    * @return A list of the selected ChartRegions
    */
   export function getSelectedRegions(chartSelection: ChartSelection,
                                      areaName: ChartAreaName = null): ChartRegion[]
   {
      let selectedRegions: ChartRegion[] = null;

      if(chartSelection) {
         if(areaName) {
            if(chartSelection.chartObject &&
               chartSelection.chartObject.areaName === areaName)
            {
               selectedRegions = chartSelection.regions;
            }
         }
         else {
            selectedRegions = chartSelection.regions;
         }
      }

      return selectedRegions || [];
   }

   export function getFirstAvailableMeasure(model: ChartModel): string {
      let matchingRegion = model.plot.regions
         .find(region => ChartTool.hasMeasure(model, region));

      if(matchingRegion != null) {
         return ChartTool.getMea(model, matchingRegion);
      }

      const matchingAxis = model.axes.find((axis) => {
         return (axis.axisType === "y" && !axis.secondary && axis.axisFields.length > 0);
      });
      return matchingAxis ? matchingAxis.axisFields[0] : "";
   }

   export function getCurrentLegendContainer(chartSelection: ChartSelection,
                                             legends: LegendContainer[]): LegendContainer {
      let currentLegend: LegendContainer = null;

      if(chartSelection && legends) {
         let chartObject = chartSelection.chartObject;

         currentLegend = legends.find((legendContainer) => {
            return Tool.isEquals(legendContainer.targetFields,
                                 (<Legend> chartObject).targetFields) &&
               legendContainer.field == (<Legend> chartObject).field &&
               legendContainer.aestheticType == (<Legend>chartObject).aestheticType &&
               legendContainer.nodeAesthetic == (<Legend>chartObject).nodeAesthetic &&
               legendContainer.legendObjects.filter((legend) => {
                  return Tool.isEquals(legend.areaName, chartObject.areaName);
               }).length > 0;
         });
      }

      return currentLegend;
   }

   export function getCurrentLegendIndex(chartSelection: ChartSelection,
                                         legends: LegendContainer[]): number {
      let currentLegend: LegendContainer =
         ChartTool.getCurrentLegendContainer(chartSelection, legends);

      return currentLegend ? currentLegend.legendIndex : -1;
   }

   // Keep chart selection in sync when model is updated
   export function updateChartSelection(model: ChartModel, oldModel: ChartModel): ChartSelection {
      let area: ChartObject;
      let chartSelection: ChartSelection = oldModel.chartSelection;
      let areaName: ChartAreaName = chartSelection.chartObject?.areaName;
      let newRegions: ChartRegion[] = [];

      if(areaName == "plot_area") {
         area = model.plot;

         if(!!area) {
            // find new model's plot region based on row, col, and measure name
            chartSelection.regions.forEach((selectedRegion) => {
               for(let region of area.regions) {
                  if(!!region && !region.noselect &&
                     ChartTool.getMea(oldModel, selectedRegion) ==
                     ChartTool.getMea(model, region) &&
                     selectedRegion.rowIdx == region.rowIdx &&
                     ChartTool.colIdx(oldModel, selectedRegion) ==
                     ChartTool.colIdx(model, region) &&
                     selectedRegion.index == region.index)
                  {
                     newRegions.push(region);
                     break;
                  }
               }
            });
         }
      }
      else if(areaName == "x_title" || areaName == "x2_title" ||
              areaName == "y_title" || areaName == "y2_title")
      {
         area = model.titles ? model.titles.find((t) => t.areaName == areaName) : null;

         // titles only have one region, the whole title
         if(!!area && chartSelection.regions.length > 0 && !!area.regions && !!area.regions[0]) {
            newRegions.push(area.regions[0]);
         }
      }
      else if(areaName == "bottom_x_axis" || areaName == "top_x_axis" ||
              areaName == "left_y_axis" || areaName == "right_y_axis")
      {
         area = model.axes ? model.axes.find((a) => a.areaName == areaName) : null;

         if(!!area) {
            // find new model's axis region based on dimension name or measure name
            chartSelection.regions.forEach((selectedRegion) => {
               for(let region of area.regions) {
                  if(!!region && !region.noselect) {
                     if(ChartTool.getDim(oldModel, selectedRegion) ==
                        ChartTool.getDim(model, region) &&
                        ChartTool.getDim(model, region) != null &&
                        ChartTool.getVal(oldModel, selectedRegion) ==
                        ChartTool.getVal(model, region) &&
                        ChartTool.getParentVals(oldModel, selectedRegion) + "" ==
                        ChartTool.getParentVals(model, region) + "")
                     {
                        newRegions.push(region);
                        break;
                     }
                     else if(ChartTool.getMea(oldModel, selectedRegion) ==
                        ChartTool.getMea(model, region) &&
                        ChartTool.getMea(model, region) != null)
                     {
                        newRegions.push(region);
                        break;
                     }
                  }
               }
            });
         }
      }
      else if(areaName == "legend_content" || areaName == "legend_title") {
         if(!!model.legends) {
            for(let container of model.legends) {
               area = container.legendObjects.find((legend) => {
                  return legend.areaName == areaName &&
                     legend.aestheticType == (<Legend> chartSelection.chartObject).aestheticType &&
                     legend.titleLabel == (<Legend> chartSelection.chartObject).titleLabel;
               });

               if(!!area) {
                  break;
               }
            }
         }

         if(!!area) {
            // find new model's legend region based on dimension name and value
            chartSelection.regions.forEach((selectedRegion) => {
               for(let region of area.regions) {
                  if(!!region && !region.noselect) {
                     if((ChartTool.getDim(oldModel, selectedRegion) ==
                         ChartTool.getDim(model, region) ||
                         ChartTool.getMea(oldModel, selectedRegion) ==
                         ChartTool.getMea(model, region)) &&
                        ChartTool.getVal(oldModel, selectedRegion) ==
                        ChartTool.getVal(model, region))
                     {
                        newRegions.push(region);
                        break;
                     }
                  }
               }
            });
         }
      }

      if(newRegions.length == 0) {
         chartSelection.chartObject = null;
         chartSelection.regions = null;
      }
      else {
         chartSelection.chartObject = area;
         chartSelection.regions = newRegions;
      }

      return chartSelection;
   }

   export function getSelectedAxisRegions(model: ChartModel): ChartRegion[] {
      const getRegions = (name: ChartAreaName) => {
         const regions = getSelectedRegions(model.chartSelection, name);
         return regions.length == 0 ? null : regions;
      };

      return getRegions("bottom_x_axis") || getRegions("top_x_axis") ||
         getRegions("left_y_axis") || getRegions("right_y_axis");
   }

   export function isPlotAreaSelected(model: ChartModel): boolean {
      if(!model || !model.chartSelection || !model.chartSelection.chartObject ||
         model.chartSelection.chartObject.areaName != "plot_area")
      {
         return false;
      }

      const regions: ChartRegion[] = model.chartSelection.regions
      // ignore votext that is not brushable. (58236)
         .filter(r => !(ChartTool.meaIdx(model, r) >= 0 && r.rowIdx < 0));

      return regions.length > 0 && !regions.some(
         r => ChartTool.dimIdx(model, r) >= 0 &&
            (ChartTool.areaType(model, r) != "axis" || !model.plotHighlightEnabled) ||
            ChartTool.areaType(model, r) == "fake" ||
            ChartTool.areaType(model, r) == "label" &&
            ChartTool.colIdx(model, r) < 0 && r.rowIdx < 0);
   }

   export function isPlotPointSelected(model: ChartModel): boolean {
      return plotSelected(model) && model.chartSelection.regions.length > 0;
   }

   export function isLegendSelected(model: ChartModel): boolean {
      return model &&
         getSelectedRegions(model.chartSelection, "legend_content").length > 0 ||
         getSelectedRegions(model.chartSelection, "legend_title").length > 0;
   }

   export function isAxisSelected(model: ChartModel): boolean {
      return model &&
         getSelectedRegions(model.chartSelection, "bottom_x_axis").length > 0 ||
         getSelectedRegions(model.chartSelection, "top_x_axis").length > 0 ||
         getSelectedRegions(model.chartSelection, "left_y_axis").length > 0 ||
         getSelectedRegions(model.chartSelection, "right_y_axis").length > 0;
   }

   export function isBrushable(model: ChartModel): boolean {
      if(!model || model.changedByScript) {
         return false;
      }

      if(isPlotAreaSelected(model)) {
         return true;
      }

      let regions = getSelectedRegions(model.chartSelection, "legend_content");

      // if dimension legend is selected
      for(let i = 0; i < regions.length; i++) {
         if(!!ChartTool.getDim(model, regions[i])) {
            return true;
         }
      }

      regions = getSelectedAxisRegions(model) || [];

      // if dimension axis is selected
      for(let i = 0; i < regions.length; i++) {
         if(!!ChartTool.getDim(model, regions[i])) {
            return true;
         }
      }

      return false;
   }

   export function isHighlightable(model: ChartModel): boolean {
      const texts = model?.chartSelection?.regions &&
         model.chartSelection.regions.filter(r => ChartTool.areaType(model, r) == "text");
      const textSelected = texts && texts.some(r => !!ChartTool.getMea(model, r));
      return ChartTool.isAxisHighlightVisible(model) && !ChartTool.isGanttMeasureSelected(model) ||
         (model.plotHighlightEnabled &&
            (model.mapInfo && ChartTool.isPlotSelected(model) ||
               ChartTool.isPlotMeasureSelected(model, false))) ||
            // votext highlighting
            textSelected && !ChartTool.isScatterPlot(model);
   }

   export function isAxisHighlightVisible(model: ChartModel): boolean {
      if(!model) {
         return false;
      }

      const regions = ChartTool.getSelectedAxisRegions(model);

      if(!regions || regions.length == 0) {
         return false;
      }

      if(ChartTool.isScatterPlotAxisSelected(model)) {
         return false;
      }

      // top x axis of mekko chart
      if(GraphTypes.isMekko(model.chartType) &&
         model.chartSelection.chartObject.areaName == "top_x_axis")
      {
         return false;
      }

      if((<any> model).dateComparisonEnabled && (<any> model).appliedDateComparison &&
         (<any> model).customPeriod && this.selectedCustomDcMergeRef)
      {
         return false;
      }

      return ChartTool.dimIdx(model, regions[0]) >= 0
         && regions.every((region) => !region.period);
   }

   export function isGanttMeasureSelected(model: ChartModel): boolean {
      if(!GraphTypes.isGantt(model.chartType)) {
         return false;
      }

      let axisType = ChartTool.getSelectedAxisType(model);
      return axisType == "bottom_x_axis";
   }

   export function isPlotSelected(model: ChartModel): boolean {
      const regions = ChartTool.getSelectedRegions(model.chartSelection, "plot_area");
      return regions && regions.length > 0;
   }

   export function isPlotMeasureSelected(model: ChartModel, measureRequired: boolean): boolean {
      let regions: ChartRegion[] = ChartTool.getSelectedRegions(model.chartSelection, "plot_area");

      if(regions && regions.length > 0) {
         return (!measureRequired || ChartTool.hasMeasure(model, regions[0])) &&
            ChartTool.areaType(model, regions[0]) !== "axis" ||
            // radar1
            GraphTypes.isRadar(model.chartType) &&
            ChartTool.areaType(model, regions[0]) == "axis" ||
            // word cloud
            model.chartType === GraphTypes.CHART_POINT &&
            ChartTool.meaIdx(model, regions[0]) >= 0 &&
            ChartTool.areaType(model, regions[0]) == "text" &&
            model.wordCloud;
      }

      return false;
   }

   export function isScatterPlotAxisSelected(model: ChartModel): Boolean {
      const dim = ChartTool.getSelectedAxisColumnName(model);
      return dim == "_XMeasureName_" || dim == "_YMeasureName_";
   }

   export function isScatterPlot(model: ChartModel): boolean {
      return model.stringDictionary?.includes("_YMeasureName_");
   }

   export function getChartSelectionString(model: ChartModel): string {
      let chartObject = model.chartSelection.chartObject;
      let str = "";

      switch(chartObject.areaName) {
         case "bottom_x_axis":
         case "top_x_axis":
         case "left_y_axis":
         case "right_y_axis":
            let region0 = model.chartSelection.regions[0];

            if(ChartTool.meaIdx(model, region0) !== -1) {
               str = "axisLabel[" + ChartTool.getMea(model, region0) + "]";
            }
            else {
               str = "axisLabel[" + ChartTool.getDim(model, region0) + "]";
            }

            break;
         case "x_title":
            str = "axisTitle[x]";
            break;
         case "x2_title":
            str = "axisTitle[x2]";
            break;
         case "y_title":
            str = "axisTitle[y]";
            break;
         case "y2_title":
            str = "axisTitle[y2]";
            break;
         case "legend_title":
            str = "legendTitle[" + (<Legend> chartObject).aestheticType + "]";
            break;
         case "legend_content":
            str = "legendContent[" + (<Legend> chartObject).aestheticType + "]";
            break;
         case "plot_area":
            let regionLabels = new Set<string>();

            for(let region of model.chartSelection.regions) {
               let label = "";

               switch(ChartTool.areaType(model, region)) {
                  case "axis":
                     if(ChartTool.meaIdx(model, region) !== -1) {
                        label = "axisLabel[" + ChartTool.getMea(model, region) + "]";
                     }
                     else {
                        label = "axisLabel[" + ChartTool.getDim(model, region) + "]";
                     }

                     break;
                  case "label":
                     label = "targetLabel[" + ChartTool.getVal(model, region) + "]";
                     break;
                  case "text":
                     if(ChartTool.meaIdx(model, region) >= 0) {
                        label = "text[" + ChartTool.getMea(model, region) + "]";
                     }
                     else {
                        label = "text";
                     }
                     break;
                  default:
                     const measure = ChartTool.getMea(model, region);
                     label = "elementVO" + (measure ? (" (" + measure + ")") : "");
               }

               regionLabels.add(label);
            }

            regionLabels.forEach((label) => {
               str += str === "" ? label : ", " + label;
            });

            break;
         default:
            str = chartObject.areaName;
      }

      return str;
   }

   export function titleSelected(model: ChartModel): boolean {
      if(!model) {
         return false;
      }

      return ChartTool.getSelectedRegions(model.chartSelection, "x_title").length > 0 ||
         ChartTool.getSelectedRegions(model.chartSelection, "x2_title").length > 0 ||
         ChartTool.getSelectedRegions(model.chartSelection, "y_title").length > 0 ||
         ChartTool.getSelectedRegions(model.chartSelection, "y2_title").length > 0;
   }

   export function legendSelected(model: ChartModel): boolean {
      return model &&
         ChartTool.getSelectedRegions(model.chartSelection, "legend_content").length > 0
         || ChartTool.getSelectedRegions(model.chartSelection, "legend_title").length > 0;
   }

   export function isNonEditableVOSelected(model: ChartModel): boolean {
      if(model.chartType == GraphTypes.CHART_CIRCLE_PACKING) {
         return false;
      }

      return model.chartSelection && model.chartSelection.chartObject &&
         model.chartSelection.chartObject.areaName == "plot_area" &&
         model.chartSelection.regions.length > 0 &&
         !ChartTool.isRegionAreaTypePresent(model, model.chartSelection, "text") &&
         !ChartTool.isRegionAreaTypePresent(model, model.chartSelection, "axis") &&
         !ChartTool.isRegionAreaTypePresent(model, model.chartSelection, "label");
   }

   export function plotSelected(model: ChartModel): boolean {
      return model && model.chartSelection && model.chartSelection.chartObject &&
         model.chartSelection.chartObject.areaName == "plot_area" &&
         // support annotation on wordcloud
         (!ChartTool.isRegionAreaTypePresent(model, model.chartSelection, "text") ||
          !model.showValues) &&
         !ChartTool.isRegionAreaTypePresent(model, model.chartSelection, "axis");
   }

   export function isRegionAreaTypePresent(model: ChartModel, chartSelection: ChartSelection,
                                           type: string): boolean
   {
      return chartSelection && chartSelection.regions && chartSelection.regions.length > 0
         && chartSelection.regions.some((region) => ChartTool.areaType(model, region) == type);
   }

   export function axisSelected(model: ChartModel): boolean {
      if(!model) {
         return false;
      }

      if(ChartTool.getSelectedRegions(model.chartSelection, "bottom_x_axis").length > 0 ||
         ChartTool.getSelectedRegions(model.chartSelection, "top_x_axis").length > 0 ||
         ChartTool.getSelectedRegions(model.chartSelection, "left_y_axis").length > 0 ||
         ChartTool.getSelectedRegions(model.chartSelection, "right_y_axis").length > 0)
      {
         return true;
      }

      if(GraphTypes.isTreemap(model.chartType)) {
         return false;
      }

      const plot = ChartTool.getSelectedRegions(model.chartSelection, "plot_area");
      return plot.some(p => ChartTool.dimIdx(model, p) >= 0 || ChartTool.meaIdx(model, p) >= 0 &&
         ChartTool.areaType(model, p) != "text" && p.rowIdx < 0);
   }

   export function getSelectedTitleType(model: ChartModel): string {
      if(ChartTool.getSelectedRegions(model.chartSelection, "x_title").length > 0) {
         return "x";
      }

      if(ChartTool.getSelectedRegions(model.chartSelection, "x2_title").length > 0) {
         return "x2";
      }

      if(ChartTool.getSelectedRegions(model.chartSelection, "y_title").length > 0) {
         return "y";
      }

      if(ChartTool.getSelectedRegions(model.chartSelection, "y2_title").length > 0) {
         return "y2";
      }

      return null;
   }

   export function getSelectedAxisColumnName(model: ChartModel): string {
      let colName: string;
      let axisRegion: ChartRegion = model.chartSelection.regions
         .find((region) => {
            return (ChartTool.axisFldIdx(model, region) >= 0) ||
               ChartTool.dimIdx(model, region) >= 0 ||
               ChartTool.meaIdx(model, region) >= 0;
         });

      if(axisRegion) {
         if(ChartTool.axisFldIdx(model, axisRegion) >= 0) {
            colName = ChartTool.getAxisFld(model, axisRegion);
         }
         else if(ChartTool.dimIdx(model, axisRegion) >= 0) {
            colName = ChartTool.getDim(model, axisRegion);
         }
         else {
            colName = ChartTool.getMea(model, axisRegion);
         }
      }

      return colName;
   }

   export function getSelectedAxisType(model: ChartModel): string {
      let bottomAxisRegions = ChartTool.getSelectedRegions(model.chartSelection,
         "bottom_x_axis");
      let topAxisRegions = ChartTool.getSelectedRegions(model.chartSelection,
         "top_x_axis");
      let leftAxisRegions = ChartTool.getSelectedRegions(model.chartSelection,
         "left_y_axis");
      let rightAxisRegions = ChartTool.getSelectedRegions(model.chartSelection,
         "right_y_axis");

      if(bottomAxisRegions.length > 0) {
         return "bottom_x_axis";
      }
      else if(topAxisRegions.length > 0) {
         return "top_x_axis";
      }
      else if(leftAxisRegions.length > 0) {
         return "left_y_axis";
      }
      else if(rightAxisRegions.length > 0) {
         return "right_y_axis";
      }

      return "axis";
   }

   export function getSelectedLegendIndex(model: ChartModel): number {
      let chartObject = model.chartSelection.chartObject;

      let currentLegend = model.legends.find((legendContainer) => {
         return legendContainer.legendObjects.filter((legend) => {
               return Tool.isEquals(legend, chartObject);
            }).length > 0;
      });

      return currentLegend ? currentLegend.legendIndex : -1;
   }

   export function getSelectedAxisIndex(model: ChartModel): number {
      let bottomAxisRegions = ChartTool.getSelectedRegions(model.chartSelection,
         "bottom_x_axis");
      let topAxisRegions = ChartTool.getSelectedRegions(model.chartSelection,
         "top_x_axis");
      let leftAxisRegions = ChartTool.getSelectedRegions(model.chartSelection,
         "left_y_axis");
      let rightAxisRegions = ChartTool.getSelectedRegions(model.chartSelection,
         "right_y_axis");

      if(bottomAxisRegions.length > 0) {
         return bottomAxisRegions[0].index;
      }
      else if(topAxisRegions.length > 0) {
         return topAxisRegions[0].index;
      }
      else if(leftAxisRegions.length > 0) {
         return leftAxisRegions[0].index;
      }
      else if(rightAxisRegions.length > 0) {
         return rightAxisRegions[0].index;
      }

      const axis: ChartRegion = model.chartSelection.regions
         .find(r => ChartTool.areaType(model, r) == "axis");
      return axis ? ChartTool.meaIdx(model, axis) : -1;
   }

   export function getSelectedAxisField(model: ChartModel): string {
      const regions = model.chartSelection.regions;

      if(regions && regions.length > 0) {
         if(ChartTool.dimIdx(model, regions[0]) >= 0) {
            return ChartTool.getDim(model, regions[0]);
         }
         else if(ChartTool.meaIdx(model, regions[0]) >= 0) {
            return ChartTool.getMea(model, regions[0]);
         }
      }

      return "";
   }

   /**
    * Return this chart's selected regions
    * @param model the chart model
    * @param areaName if passed, only return the specified area's selected regions
    * @returns {ChartRegion[]} A list of the selected ChartRegions
    */
   export function getSelectedRegion(model: ChartModel,
                                     areaName: ChartAreaName = null): ChartRegion[] {
      return !model ? [] : ChartTool.getSelectedRegions(model.chartSelection, areaName);
   }

   /**
    * Check whether the provided region is selected.
    * @param chartSelection the chart selection to check
    * @param areaName       the chart area to check for
    * @param region         the region we are looking for
    * @returns {boolean} Whether the region is selected or not
    */
   export function isRegionSelected(chartSelection: ChartSelection,
                   areaName: ChartAreaName, region: ChartRegion): boolean
   {
      if(!region) {
         return false;
      }

      let regions: ChartRegion[] = ChartTool.getSelectedRegions(chartSelection, areaName);
      let found: ChartRegion = regions.find((selectedRegion) => {
         return selectedRegion.index === region.index &&
            selectedRegion.valIdx === region.valIdx;
      });

      return !!found;
   }

   /**
    * Get aesthetic type of the legend area.
    */
   export function getAestheticType(legend: LegendContainer): number {
      let aestheticType: string = legend.aestheticType;

      if(aestheticType == "Color") {
         return ChartConstants.DROP_REGION_COLOR;
      }
      else if(aestheticType == "Shape" || aestheticType == "Texture") {
         return ChartConstants.DROP_REGION_SHAPE;
      }
      else if(aestheticType == "Size") {
         return ChartConstants.DROP_REGION_SIZE;
      }

      return -1;
   }

   export function drawReferenceLine(context: CanvasRenderingContext2D, region: ChartRegion,
                                     canvasX: number, canvasY: number, scale: number = 1): void
   {
      if(context && region && region.centroid) {
         // draw the reference point region
         ChartTool.drawRegions(context,
            [].concat(region), canvasX, canvasY, scale, undefined, undefined, true);
      }
   }

   /**
    * Draw a shape on a given canvas
    *
    * @param context the 2d canvas rendering context on which to draw the shape
    * @param regions a list of {@link ChartRegion} that contain the coordinates
    *                and segmentTypes required to draw the shapes
    * @param scaleX  the amount of scaling to apply to the x-coordinates
    * @param scaleY  the amount of scaling to apply to the y-coordinates
    * @param offsetX the distance this context is shifted from its origin.
    * @param offsetY the distance this context is shifted from its origin.
    *
    * @throws        TypeError if the context or regions are null
    */
   export function drawRegions(context: CanvasRenderingContext2D, regions: ChartRegion[],
                               offsetX: number, offsetY: number, currentScale?: number,
                               scaleX?: number, scaleY?: number,
                               drawReferLine: boolean = false): void
   {
      if(context && regions) {
         let deviceRatio = window.devicePixelRatio;
         let nativeElement = context.canvas;
         let rect = nativeElement.getBoundingClientRect();
         let dpr = deviceRatio || 1;
         nativeElement.width = rect.width * dpr;
         nativeElement.height = rect.height * dpr;

         const bodyCssStyle = window.getComputedStyle(document.body);
         const canvasCssStyle = window.getComputedStyle(context.canvas);
         let fillStyle = "rgba(220, 88, 30, 0.3)"; //#dc581e
         let strokeStyle = "#dc581e";
         let canvasScaleX = deviceRatio;
         let canvasScaleY = deviceRatio;

         if(scaleX) {
            canvasScaleX = scaleX * deviceRatio;
         }

         if(scaleY) {
            canvasScaleY = scaleY * deviceRatio;
         }

         if(currentScale) {
            canvasScaleX *= currentScale;
            canvasScaleY *= currentScale;
         }

         let canvasSizeScale = deviceRatio * currentScale;

         // Check if the computed color and borderColor of the canvas aren't the same as the body.
         // If they are it would mean that the class is not defined in the css and we should use
         // the original colors.
         if(canvasCssStyle.color && canvasCssStyle.borderColor &&
            !(bodyCssStyle.color == canvasCssStyle.color &&
               bodyCssStyle.borderColor == canvasCssStyle.borderColor))
         {
            fillStyle = `${canvasCssStyle.color}`;
            strokeStyle = `${canvasCssStyle.borderColor}`;
         }

         if(canvasScaleX != null && canvasScaleY != null) {
            context.transform(canvasScaleX, 0, 0, canvasScaleY, 0, 0);
         }

         offsetX = offsetX ? offsetX : 0;
         offsetY = offsetY ? offsetY : 0;
         scaleX = scaleX ? scaleX : 1;
         scaleY = scaleY ? scaleY : 1;
         context.translate(-offsetX, -offsetY);

         if(drawReferLine && regions[0]) {
            // draw a reference line to the point
            const point = regions[0].centroid;
            context.beginPath();
            context.lineWidth = 2;
            context.strokeStyle = "#66DD66";
            context.moveTo(0, point.y);
            context.lineTo(point.x, point.y);
            context.lineTo(point.x, Math.round(context.canvas.height / deviceRatio));
            context.stroke();
         }

         context.beginPath();
         context.lineWidth = 2;
         context.fillStyle = fillStyle;
         context.strokeStyle = strokeStyle;

         const dregions = Tool.clone(regions);

         for(let m = 0; m < dregions.length; m++) {
            const region: ChartRegion = dregions[m];
            const coordinates = region.pts;
            const segmentTypes = region.segTypes;

            for(let n = 0; n < coordinates.length; n++) {
               for(let k = 0; k < coordinates[n].length; k++) {
                  if(segmentTypes[n].length == 1 && segmentTypes[n][0] == RECT) {
                     // [[x, y], [w, h]]
                     const x = coordinates[n][k][0][0];
                     const y = coordinates[n][k][0][1];
                     const w = coordinates[n][k][1][0];
                     const h = coordinates[n][k][1][1];
                     context.moveTo(x, y);
                     context.rect(x, y, w, h);
                     continue;
                  }
                  else if(segmentTypes[n].length == 1 && segmentTypes[n][0] == ELLIPSE) {
                     // [[x, y], [w, h]]
                     const x = coordinates[n][k][0][0];
                     const y = coordinates[n][k][0][1];
                     const w = coordinates[n][k][1][0];
                     const h = coordinates[n][k][1][1];
                     context.moveTo(x + w, y + h / 2);
                     context.ellipse(x + w / 2, y + h / 2, w / 2, h / 2, 0, 0, Math.PI * 2);
                     continue;
                  }
                  else if(segmentTypes[n].length == 1 && segmentTypes[n][0] == LINE) {
                     // [[x1, y1], [x2, y2]]
                     const x1 = coordinates[n][k][0][0];
                     const y1 = coordinates[n][k][0][1];
                     const x2 = coordinates[n][k][1][0];
                     const y2 = coordinates[n][k][1][1];
                     context.moveTo(x1, y1);
                     context.lineTo(x2, y2);
                     continue;
                  }

                  const hasCurves = segmentTypes[n].some((segmentType) => {
                     return segmentType === 3 || segmentType === 4;
                  });

                  if(hasCurves) {
                     context.setTransform(canvasSizeScale, 0, 0, canvasSizeScale, 0, 0);
                     const x = region.centroid.x * (scaleX - 1);
                     const y = region.centroid.y * (scaleY - 1);
                     context.translate(x - offsetX, y - offsetY);
                  }

                  for(let l = 0; l < coordinates[n][k].length; l++) {
                     // optimization on server, identical segmentTypes values at the end
                     // are collapsed
                     const segmentType = segmentTypes[n][Math.min(l, segmentTypes[n].length - 1)];
                     let path = coordinates[n][k][l];

                     switch(segmentType) {
                        case 0:
                           // eslint-disable-next-line prefer-spread
                           context.moveTo.apply(context, path);
                           break;
                        case 1:
                           // eslint-disable-next-line prefer-spread
                           context.lineTo.apply(context, path);
                           break;
                        case 2:
                           // eslint-disable-next-line prefer-spread
                           context.quadraticCurveTo.apply(context, path);
                           break;
                        case 3:
                           // eslint-disable-next-line prefer-spread
                           context.bezierCurveTo.apply(context, path);
                           break;
                        case 4:
                           context.closePath();
                           break;
                        default:
                           console.warn("Invalid segment type found");
                           break;
                     }
                  }
               }
            }
         }

         // reset transformation before drawing - paths have already been transformed
         context.setTransform(1, 0, 0, 1, 0, 0);
         context.fill();
         context.stroke();
      }
      else {
         throw new TypeError("drawShape parameters cannot be null");
      }
   }

   // draw a plus at the point (x, y), and clears it after a slight delay.
   // this helps user orient where the touch is registered and can adjust accordingly to
   // tap the correct spot.
   export function drawTouch(context: CanvasRenderingContext2D, x: number, y: number) {
      if(context) {
         context.lineWidth = 2;
         context.strokeStyle = "#dc581e";
         const len = 5;

         context.beginPath();
         context.moveTo(x - len, y);
         context.lineTo(x + len, y);
         context.moveTo(x, y - len);
         context.lineTo(x, y + len);
         context.stroke();

         setTimeout(() => context.clearRect(0, 0, context.canvas.width, context.canvas.height),
                    500);
      }
   }

   export function getSelectedString(model: ChartModel, chartSelection: ChartSelection,
                                     includeParent: boolean = true): string
   {
      if(!model || !chartSelection) {
         return "";
      }

      let regions = chartSelection.regions;
      let chartObject = chartSelection.chartObject;
      let areaName = chartSelection.chartObject ? chartSelection.chartObject.areaName : null;
      let selectedString = "";

      if(areaName === "plot_area") {
         if(GraphTypes.isWaterfall(model.chartType) && regions.length == 0) {
            return "all";
         }

         const selStrs = regions.map(region => {
            let measureName = ChartTool.getMea(model, region);
            let dim = ChartTool.getDim(model, region);
            let index = region.rowIdx;

            return (measureName && index >= 0) ? measureName + "^INDEX:" + index :
               (dim && ChartTool.areaType(model, region) == "axis"
                  ? dim + "^VALUE:" + ChartTool.getVal(model, region) : null);
         }).filter(r => r != null);
         selectedString = [... new Set(selStrs)].join("``");
      }
      else if(areaName === "bottom_x_axis" ||
              areaName === "top_x_axis" ||
              areaName === "left_y_axis" ||
              areaName === "right_y_axis")
      {
         if(GraphTypes.isWaterfall(model.chartType) && regions.length == 0) {
            return "all";
         }

         regions.forEach((region) => {
            let dimensionName = ChartTool.getDim(model, region);
            let value = ChartTool.getVal(model, region);

            if(selectedString.length > 0) {
               selectedString += "``";
            }

            selectedString += dimensionName + "^VALUE:" + value;
            selectedString += "^INDEX:" + region.rowIdx;

            if(region.parentVals && includeParent) {
               for(let i = 0; i < region.parentVals.length; i += 2) {
                  selectedString += "^AND^" + model.stringDictionary[region.parentVals[i]];
                  selectedString += "^VALUE:" + model.stringDictionary[region.parentVals[i + 1]];
               }
            }
         });
      }
      else if(areaName === "legend_title" || areaName === "legend_content") {
         regions.forEach((region) => {
            let currentLegend = model.legends.find((legendContainer) => {
               return legendContainer.legendObjects.filter((legend) => {
                     return Tool.isEquals(legend, chartObject);
                  }).length > 0;
            });

            let value = ChartTool.getVal(model, region);

            if(selectedString.length > 0) {
               selectedString += "``";
            }

            selectedString += currentLegend.field + "^VALUE:" + value;
         });
      }

      return selectedString;
   }

   /**
    * Get y right axis of the vs chart model.
    *
    * @param model the specific vs chart model.
    * @return the y right axis of vs chart model.
    */
   export function getYRightAxis(model: ChartModel): Axis {
      return model.axes.find((axis) => {
         return axis.areaName === "right_y_axis";
      });
   }

   /**
    * Get y left axis of the vs chart model.
    *
    * @param model the specific vs chart model.
    * @return the y left axis of vs chart model.
    */
   export function getYLeftAxis(model: ChartModel): Axis {
      return model.axes.find((axis) => {
         return axis.areaName === "left_y_axis";
      });
   }

   /**
    * Get x top axis of the vs chart model.
    *
    * @param model the specific vs chart model.
    * @return the x top axis of vs chart model.
    */
   export function getXTopAxis(model: ChartModel): Axis {
      return model.axes.find((axis) => {
         return axis.areaName === "top_x_axis";
      });
   }

   /**
    * Get x bottom axis of the vs chart model.
    *
    * @param model the specific vs chart model.
    * @return the x bottom axis of vs chart model.
    */
   export function getXBottomAxis(model: ChartModel): Axis {
      return model.axes.find((axis) => {
         return axis.areaName === "bottom_x_axis";
      });
   }

   /**
    * Get x title of the vs chart model.
    *
    * @param model the specific vs chart model.
    * @return the x title of vs chart model.
    */
   export function getXTitle(model: ChartModel): Title {
      return model.titles.find((title) => {
         return title.areaName === "x_title";
      });
   }

   /**
    * Get x2 title of the vs chart model.
    *
    * @param model the specific vs chart model.
    * @return the x2 title of vs chart model.
    */
   export function getX2Title(model: ChartModel): Title {
      return model.titles.find((title) => {
         return title.areaName === "x2_title";
      });
   }

   /**
    * Get y title of the vs chart model.
    *
    * @param model the specific vs chart model.
    * @return the y title of vs chart model.
    */
   export function getYTitle(model: ChartModel): Title {
      return model.titles.find((title) => {
         return title.areaName === "y_title";
      });
   }

   /**
    * Get y2 title of the vs chart model.
    *
    * @param model the specific vs chart model.
    * @return the y2 title of vs chart model.
    */
   export function getY2Title(model: ChartModel): Title {
      return model.titles.find((title) => {
         return title.areaName === "y2_title";
      });
   }

   /**
    * Create a GeoJSON collection
    *
    * @param regions The regions to use to create the collection
    * @returns       A GeoJSON feature collection with the coordinates given
    */
   export function createRegionTree(model: ChartModel, regions: ChartRegion[]): any {
      // eslint-disable-next-line @typescript-eslint/no-var-requires
      let WhichPolygon = require("which-polygon");
      let collection: GeoJSON.FeatureCollection<GeoJSON.MultiPolygon> = null;

      if(regions && regions.length > 0) {
         collection = {
            type: "FeatureCollection",
            features: <GeoJSON.Feature<GeoJSON.MultiPolygon>[]> []
         };

         for(let i = 0; i < regions.length; i++) {
            let region = regions[i];
            const segTypes: number[][] = region.segTypes;
            const pts: number[][][][] = Tool.clone(region.pts);

            // expand rectangle and ellipse into polygon
            for(let n = 0; n < pts.length; n++) {
               for(let k = 0; k < pts[n].length; k++) {
                  // rectangle
                  if(segTypes[n].length == 1 && (segTypes[n][0] == RECT ||
                                                 segTypes[n][0] == ELLIPSE))
                  {
                     const x = pts[n][k][0][0];
                     const y = pts[n][k][0][1];
                     const w = pts[n][k][1][0];
                     const h = pts[n][k][1][1];

                     if(segTypes[k][0] == RECT) {
                        const rect = [];
                        rect.push([x, y]);
                        rect.push([x + w, y]);
                        rect.push([x + w, y + h]);
                        rect.push([x, y + h]);
                        rect.push([x, y]);
                        pts[n][k] = rect;
                     }
                     // ellipse
                     else if(segTypes[k][0] == ELLIPSE) {
                        const centerx = x + w / 2;
                        const centery = y + h / 2;
                        const xr = w / 2;
                        const yr = h / 2;
                        const ellipse = [];

                        for(let theta = 0; theta < Math.PI * 2; theta += 0.1) {
                           const ex = centerx + xr * Math.cos(theta);
                           const ey = centery + yr * Math.sin(theta);
                           ellipse.push([ex, ey]);
                        }

                        pts[n][k] = ellipse;
                     }
                  }
                  // expand curve into points
                  else {
                     const segPts = [];

                     for(let pi = 0; pi < pts[n][k].length; pi++) {
                        const segType = pi < segTypes[k].length ? segTypes[k][pi]
                           : segTypes[k][segTypes[k].length - 1];

                        if(segType == 3) {
                           const p0x = pts[n][k][pi][0];
                           const p0y = pts[n][k][pi][1];
                           const p1x = pts[n][k][pi][2];
                           const p1y = pts[n][k][pi][3];
                           const p2x = pts[n][k][pi][4];
                           const p2y = pts[n][k][pi][5];

                           // bezier curve equation:
                           // B(t) = (1 - t) ** 2 * p0 + 2 * (1 - t) * t * p1 + t**2 * p2
                           for(let t = 0; t < 1; t += 0.1) {
                              const x = (1 - t) * (1 - t) * p0x + 2 * (1 - t) * t * p1x +
                                 t * t * p2x;
                              const y = (1 - t) * (1 - t) * p0y + 2 * (1 - t) * t * p1y +
                                 t * t * p2y;
                              segPts.push([x, y]);
                           }
                        }
                        else {
                           segPts.push(pts[n][k][pi]);
                        }
                     }

                     pts[n][k] = segPts;
                  }
               }
            }

            collection.features.push({
               type: "Feature",
               properties: {
                  index: ChartTool.areaType(model, region) == "legend" &&
                     region.legendItemIdx != null &&
                     region.legendItemIdx != -1 ? region.legendItemIdx : region.index
               },
               geometry: {
                  type: "MultiPolygon",
                  coordinates: pts // requires 4d array
               }
            });
         }

         return WhichPolygon(collection);
      }
      else {
         throw new TypeError("createRegionTree parameters cannot be null");
      }
   }

   /**
    * Same as getTreeRegion but for a bounding box instead of a single point
    */
   export function getTreeRegions(regionTree: any, x1: number, y1: number,
                                  x2: number, y2: number): number[]
   {
      // swap x1/x2 if needed for min/max
      if(x1 > x2) {
         let x = x2;
         x2 = x1;
         x1 = x;
      }

      // swap y1/y2 if needed for min/max
      if(y1 > y2) {
         let y = y2;
         y2 = y1;
         y1 = y;
      }

      if(regionTree) {
         let results = regionTree.bbox([x1, y1, x2, y2]);

         if(x1 == x2 && y1 == y2) {
            const maxMoe = GuiTool.isMobileDevice() ? 6 : 2;
            let fuzzy = false;

            for(let moe = 0; results.length == 0 && moe <= maxMoe; moe++) {
               results = regionTree.bbox([x1 - moe, y1 - moe, x2 + moe * 2, y2 + moe * 2]);
               fuzzy = true;
            }

            // if detected more than one hit when the hit regions is increased,
            // only use the first one
            if(fuzzy && results.length > 1) {
               results = [results[0]];
            }
         }

         return results.map((e: any) => e.index);
      }

      return [];
   }

   // methods for accessing region fields stored in dictionary

   export function getVal(model: ChartModel, region: ChartRegion): string {
      if(region.valIdx != null && region.valIdx >= 0) {
         return model.stringDictionary[region.valIdx];
      }

      return null;
   }

   export function getParentVals(model: ChartModel, region: ChartRegion): string[] {
      if(region.parentVals) {
         return region.parentVals.map(vidx => model.stringDictionary[vidx]);
      }

      return [];
   }

   export function meaIdx(model: ChartModel, region: ChartRegion): number {
      const metaIdx = region.metaIdx || 0;
      const meta = model.regionMetaDictionary[metaIdx];
      return meta ? meta.meaIdx : -1;
   }

   export function getMea(model: ChartModel, region: ChartRegion): string {
      const meaIdx0 = ChartTool.meaIdx(model, region);
      return meaIdx0 >= 0 ? model.stringDictionary[meaIdx0] : null;
   }

   export function dimIdx(model: ChartModel, region: ChartRegion): number {
      const metaIdx = region.metaIdx || 0;
      const meta = model.regionMetaDictionary[metaIdx];
      return meta ? meta.dimIdx : -1;
   }

   export function getDim(model: ChartModel, region: ChartRegion): string {
      const dimIdx0 = ChartTool.dimIdx(model, region);
      return dimIdx0 >= 0 ? model.stringDictionary[dimIdx0] : null;
   }

   export function colIdx(model: ChartModel, region: ChartRegion): number {
      const metaIdx = region.metaIdx || 0;
      return model.regionMetaDictionary[metaIdx].colIdx;
   }

   export function areaType(model: ChartModel, region: ChartRegion): string {
      const metaIdx = region.metaIdx || 0;
      return model.regionMetaDictionary[metaIdx]
         ? model.regionMetaDictionary[metaIdx].areaType : null;
   }

   export function refLine(model: ChartModel, region: ChartRegion): boolean {
      const metaIdx = region.metaIdx || 0;
      return model.regionMetaDictionary[metaIdx].refLine;
   }

   export function hasMeasure(model: ChartModel, region: ChartRegion): boolean {
      const metaIdx = region.metaIdx || 0;
      return model.regionMetaDictionary[metaIdx].hasMeasure;
   }

   export function dataType(model: ChartModel, region: ChartRegion): string {
      const metaIdx = region.metaIdx || 0;
      return model.regionMetaDictionary[metaIdx].dataType;
   }

   export function axisFldIdx(model: ChartModel, region: ChartRegion): number {
      const metaIdx = region.metaIdx || 0;
      return model.regionMetaDictionary[metaIdx].axisFldIdx;
   }

   export function getAxisFld(model: ChartModel, region: ChartRegion): string {
      const axisFldIdx0 = ChartTool.axisFldIdx(model, region);
      return axisFldIdx0 >= 0 ? model.stringDictionary[axisFldIdx0] : null;
   }

   export function valueText(model: ChartModel, region: ChartRegion): boolean {
      const metaIdx = region.metaIdx || 0;
      return model.regionMetaDictionary[metaIdx].valueText;
   }

   export function fillIndex(model: ChartModel) {
      // optimization, fill in index on client side instead of sending as part of json
      model.axes
         .filter(axis => axis.regions)
         .forEach(axis => axis.regions.forEach((r, idx) => r.index = idx));
      model.facets
         .filter(facet => facet.regions)
         .forEach(facet => facet.regions.forEach((r, idx) => r.index = idx));
      model.legends
         .filter(container => container && container.legendObjects)
         .forEach(container =>
            container.legendObjects.forEach(legend =>
                                            legend.regions.forEach((r, idx) => r.index = idx)));
      model.titles
         .filter(title => title)
         .forEach(title => title.regions.forEach((r, idx) => r.index = idx));

      if(model.plot) {
         model.plot.regions.forEach((r, idx) => {
            if(!r.segTypes && idx > 0) {
               r.segTypes = model.plot.regions[idx - 1].segTypes;
            }

            r.index = idx;
         });
      }
   }

   export function isAxis(areaName: string): boolean {
      return areaName === "bottom_x_axis" || areaName === "top_x_axis" || areaName === "left_y_axis" ||
         areaName === "right_y_axis";
   }
}
