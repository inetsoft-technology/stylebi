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
import { Dimension } from "../../common/data/dimension";
import { Tool } from "../../../../../shared/util/tool";
import { ViewsheetClientService } from "../../common/viewsheet-client";
import { ChartSelection } from "../../graph/model/chart-selection";
import { ChartTool } from "../../graph/model/chart-tool";
import { FormatVSObjectEvent } from "../event/format-vs-object-event";
import { GetVSObjectFormatEvent } from "../event/get-vs-object-format-event";
import { RefreshVsAssemblyEvent } from "../event/refresh-vs-assembly-event";
import { BaseTableModel } from "../model/base-table-model";
import { VSCalendarModel } from "../model/calendar/vs-calendar-model";
import { GuideBounds } from "../model/layout/guide-bounds";
import { VSOutputModel } from "../model/output/vs-output-model";
import { VSTextModel } from "../model/output/vs-text-model";
import { VSCalcTableModel } from "../model/vs-calctable-model";
import { VSChartModel } from "../model/vs-chart-model";
import { VSCrosstabModel } from "../model/vs-crosstab-model";
import { VSObjectModel } from "../model/vs-object-model";
import { VSSelectionBaseModel } from "../model/vs-selection-base-model";
import { VSSelectionContainerModel } from "../model/vs-selection-container-model";
import { VSTableModel } from "../model/vs-table-model";
import { DropdownOptions } from "../../widget/fixed-dropdown/dropdown-options";
import { ActionsContextmenuComponent } from "../../widget/fixed-dropdown/actions-contextmenu.component";
import { AssemblyActionGroup } from "../../common/action/assembly-action-group";
import { FixedDropdownService } from "../../widget/fixed-dropdown/fixed-dropdown.service";
import { AssemblyAction } from "../../common/action/assembly-action";
import { GraphTypes } from "../../common/graph-types";

const INPUT_TYPES = {
   "VSTextInput": true,
   "VSCheckBox": true,
   "VSRadioButton": true,
   "VSComboBox": true,
   "VSSlider": true,
   "VSSpinner": true,
   "VSSelectionTree": true
};

const KEY_NAV_ENABLED = {
   "VSSelectionList": true,
   "VSSelectionTree": true,
   "VSSelectionContainer": true,
   "VSRangeSlider": true,
   "VSCalendar": true,
   "VSSlider": true,
   "VSSpinner": true,
   "VSCheckBox": true,
   "VSRadioButton": true,
   "VSComboBox": true,
   "VSTextInput": true,
   "VSSubmit": true,
   "VSTab": true,
   "VSViewsheet": true
};

/**
 * Common utility methods viewsheets
 */
export namespace VSUtil {
   /**
    * Get the variables for a specific object in a viewsheet.
    *
    * @param {objects} the full list of viewsheet objects
    * @param {objName} the name of the object for which to get the variables
    *
    * @returns {string[]} a list of variables
    */
   export function getVariableList(objects: VSObjectModel[], objName: string, allowAutoDrill?: boolean): string[] {
      let variables: string[] = [];

      if(objects) {
         for(let object of objects) {
            if(object.absoluteName != objName && !!INPUT_TYPES[object.objectType]) {

               if("VSSelectionTree" == object.objectType) {

                  if (allowAutoDrill) {
                     variables.push("$(" + object.absoluteName + ".drillMember)");
                  }
               }
               else {
                  variables.push("$(" + object.absoluteName + ")");
               }
            }
         }
      }

      variables.sort();

      return variables;
   }

   export function getLayoutPreviewSize(screenWidth: number, screenHeight: number,
                                        guideType: GuideBounds): Dimension
   {
      let layoutWidth: number = screenWidth - 15; // allow for vertical scroll bar
      let layoutHeight: number = screenHeight;

      if(guideType != GuideBounds.GUIDES_NONE && screenWidth > 0 && screenHeight > 0) {
         let widthFactor: number;
         let heightFactor: number;

         if(guideType == GuideBounds.GUIDES_16_9_PORTRAIT) {
            widthFactor = 9 / 16;
            heightFactor = 16 / 9;
         }
         else if(guideType == GuideBounds.GUIDES_4_3_PORTRAIT) {
            widthFactor = 3 / 4;
            heightFactor = 4 / 3;
         }
         else if(guideType == GuideBounds.GUIDES_16_9_LANDSCAPE) {
            widthFactor = 16 / 9;
            heightFactor = 9 / 16;
         }
         else if(guideType == GuideBounds.GUIDES_4_3_LANDSCAPE){
            widthFactor = 4 / 3;
            heightFactor = 3 / 4;
         }

         layoutHeight = layoutWidth * heightFactor;

         if(layoutHeight > screenHeight) {
            layoutHeight = screenHeight;
            layoutWidth = layoutHeight * widthFactor;
         }
      }

      return new Dimension(layoutWidth, layoutHeight);
   }

   function restoreSelectionContainerModel(
      omodel: VSSelectionContainerModel,
      nmodel: VSSelectionContainerModel): VSSelectionContainerModel
   {
      if(!nmodel.childObjects || nmodel.childObjects.length == 0) {
         nmodel.childObjects = nmodel.childrenNames
            .map(name => omodel.childObjects.find(obj => obj.absoluteName == name));
      }

      return nmodel;
   }

   /**
    * Update new model with transient properties from old models
    *
    * @param {VSObjectModel} oldModel the copy of the old model that shouldn't be modified
    * @param {VSObjectModel} newModel the new model that gets the new properties
    * @return {VSObjectModel} the new model with updated properties
    */
   export function replaceObject(oldModel: VSObjectModel, newModel: VSObjectModel): VSObjectModel {
      if(oldModel.objectType != newModel.objectType) {
         return newModel;
      }

      const objectType = oldModel.objectType;

      // Make sure to save anything related to state.
      newModel.selectedRegions = oldModel.selectedRegions;

      switch(objectType) {
         case "VSCalcTable":
            newModel = restoreCalcTableViewModel(
               <VSCalcTableModel> oldModel, <VSCalcTableModel> newModel);
            break;
         case "VSChart":
            newModel = restoreChartViewModel(<VSChartModel> oldModel, <VSChartModel> newModel);
            break;
         case "VSCrosstab":
            newModel = restoreCrosstabViewModel(
               <VSCrosstabModel> oldModel, <VSCrosstabModel> newModel);
            break;
         case "VSGauge":
         case "VSImage":
            newModel = restoreImageViewModel(<VSOutputModel> oldModel, <VSOutputModel> newModel);
            break;
         case "VSTable":
            newModel = restoreTableViewModel(<VSTableModel> oldModel, <VSTableModel> newModel);
            break;
         case "VSText":
            newModel = restoreTextViewModel(<VSTextModel> oldModel, <VSTextModel> newModel);
            break;
         case "VSCalendar":
            newModel = restoreCalendarViewModel(
               <VSCalendarModel> oldModel, <VSCalendarModel> newModel);
            break;
         case "VSSelectionList":
         case "VSSelectionTree":
            newModel = restoreSelectionModel(
               <VSSelectionBaseModel> oldModel, <VSSelectionBaseModel> newModel);
            break;
         case "VSSelectionContainer":
            newModel = restoreSelectionContainerModel(
               <VSSelectionContainerModel> oldModel, <VSSelectionContainerModel> newModel);
            break;
         default:
            // no changes to newModel
            break;
      }

      return newModel;
   }

   function restoreSelectionModel<T extends VSSelectionBaseModel>(oldModel: T, newModel: T): T {
      newModel.searchDisplayed = oldModel.searchDisplayed;

      return newModel;
   }

   function restoreCalendarViewModel<T extends VSCalendarModel>(oldModel: T, newModel: T): T {
      newModel.calendarsShown = oldModel.calendarsShown;
      newModel.multiSelect = oldModel.multiSelect;

      return newModel;
   }

   function restoreImageViewModel<T extends VSOutputModel>(oldModel: T, newModel: T): T {
      newModel.selectedAnnotations = oldModel.selectedAnnotations;
      return newModel;
   }

   function restoreTextViewModel<T extends VSTextModel>(oldModel: T, newModel: T): T {
      newModel.selectedAnnotations = oldModel.selectedAnnotations;
      return newModel;
   }

   function restoreBaseTableViewModel<T extends BaseTableModel>(oldModel: T, newModel: T): T {
      newModel.selectedAnnotations = oldModel.selectedAnnotations;
      newModel.titleSelected = oldModel.titleSelected;
      newModel.isHighlightCopied = oldModel.isHighlightCopied;
      newModel.colWidths = oldModel.colWidths;
      newModel.headerRowPositions = oldModel.headerRowPositions;
      newModel.dataRowPositions = oldModel.dataRowPositions;
      newModel.rowCount = oldModel.rowCount;
      newModel.colCount = oldModel.colCount;
      newModel.dataRowCount = oldModel.dataRowCount;
      newModel.dataColCount = oldModel.dataColCount;
      newModel.scrollHeight = oldModel.scrollHeight;
      newModel.headerRowCount = oldModel.headerRowCount;
      newModel.headerColCount = oldModel.headerColCount;
      newModel.headerRowHeights = oldModel.headerRowHeights;
      newModel.dataRowHeight = oldModel.dataRowHeight;
      newModel.wrapped = oldModel.wrapped;
      newModel.limitMessage = oldModel.limitMessage;

      // colCount is always 0 when vsobj is refreshed since the colCount is only
      // set in the load table data command
      //if(newModel.colCount > 0) {
      newModel.selectedHeaders = oldModel.selectedHeaders;
      newModel.selectedData = oldModel.selectedData;
      newModel.firstSelectedRow = oldModel.firstSelectedRow;
      newModel.firstSelectedColumn = oldModel.firstSelectedColumn;

      return newModel;
   }

   function restoreTableViewModel<T extends VSTableModel>(oldModel: T, newModel: T): T {
      newModel = restoreBaseTableViewModel(oldModel, newModel);
      return newModel;
   }

   function restoreCrosstabViewModel<T extends VSCrosstabModel>(oldModel: T, newModel: T): T {
      newModel = restoreBaseTableViewModel(oldModel, newModel);
      newModel.runtimeRowHeaderCount = oldModel.runtimeRowHeaderCount;
      newModel.runtimeColHeaderCount = oldModel.runtimeColHeaderCount;
      newModel.cells = oldModel.cells;
      return newModel;
   }

   function restoreCalcTableViewModel<T extends VSCalcTableModel>(oldModel: T, newModel: T): T {
      newModel = restoreBaseTableViewModel(oldModel, newModel);
      return newModel;
   }

   function restoreChartViewModel<T extends VSChartModel>(oldModel: T, newModel: T): T {
      newModel.showPlotResizers = oldModel.showPlotResizers;
      newModel.chartSelection = oldModel.chartSelection;
      newModel.selectedAnnotations = oldModel.selectedAnnotations;
      newModel.titleSelected = oldModel.titleSelected;
      newModel.invalid = oldModel.invalid;
      newModel.verticallyResizable = oldModel.verticallyResizable;
      newModel.horizontallyResizable = oldModel.horizontallyResizable;
      newModel.maxHorizontalResize = oldModel.maxHorizontalResize;
      newModel.maxVerticalResize = oldModel.maxVerticalResize;
      newModel.initialWidthRatio = oldModel.initialWidthRatio;
      newModel.widthRatio = oldModel.widthRatio;
      newModel.initialHeightRatio = oldModel.initialHeightRatio;
      newModel.heightRatio = oldModel.heightRatio;
      newModel.resized = oldModel.resized;
      newModel.plot = oldModel.plot;
      newModel.titles = oldModel.titles;
      newModel.stringDictionary = oldModel.stringDictionary;
      newModel.regionMetaDictionary = oldModel.regionMetaDictionary;
      newModel.axes = oldModel.axes;
      newModel.legends = oldModel.legends;
      newModel.facets = oldModel.facets;
      newModel.legendsBounds = oldModel.legendsBounds;
      newModel.contentBounds = oldModel.contentBounds;
      newModel.legendOption = oldModel.legendOption;
      // chartSelection will hold the old info at this point, which will be updated when
      // SetChartAreascommand is processed and updated with new information.
      newModel.chartSelection = oldModel.chartSelection;
      newModel.notAuto = oldModel.notAuto;
      newModel.lastFlyover = oldModel.lastFlyover;
      return newModel;
   }

   export function refreshCalendarRanges(obj: VSObjectModel, vsRuntimeId: string,
                                         viewsheetClient: ViewsheetClientService): void
   {
      // Bug #56350, for non-period calendars with no range set, refresh the range from the data
      if(!!(obj as any).rangeRefreshRequired) {
         const event: RefreshVsAssemblyEvent = {
            vsRuntimeId: vsRuntimeId,
            assemblyName: obj.absoluteName
         };

         viewsheetClient.sendEvent("/events/vs/refresh/assembly", event);
      }
   }

   export function prepareFormatEvent(selectedObjects: VSObjectModel[]): FormatVSObjectEvent {
      let event: FormatVSObjectEvent = new FormatVSObjectEvent();
      event.objects = [];
      event.data = [];
      event.charts = [];
      event.regions = [];
      event.indexes = [];
      event.columnNames = [];
      let data: any = null;
      // group container format only changed if selected by itself
      const ignoreGroupContainer: boolean = selectedObjects.length > 1 && selectedObjects.some(
         obj => obj.objectType != "VSGroupContainer");

      for(let obj of selectedObjects) {
         if(ignoreGroupContainer && obj.objectType == "VSGroupContainer") {
            continue;
         }

         if(obj.objectType === "VSChart" && !(<VSChartModel> obj).titleSelected) {
            const chart: VSChartModel = <VSChartModel> obj;
            event.charts.push(obj.absoluteName);

            let selection: ChartSelection = (<VSChartModel> obj).chartSelection;

            if(!selection) {
               event.regions.push(null);
               event.indexes.push(null);
               event.columnNames.push(null);
               continue;
            }

            let indexes: number[] = [];
            let columnNames: string[] = [];

            if(selection.chartObject && selection.chartObject.areaName == "plot_area" &&
               !!selection.regions && selection.regions.length > 0 &&
               (ChartTool.areaType(chart, selection.regions[0]) == "label" ||
                ChartTool.areaType(chart, selection.regions[0]) == "vo" ||
                ChartTool.areaType(chart, selection.regions[0]) == "text"))
            {
               event.regions.push(ChartTool.areaType(chart, selection.regions[0]));
               indexes.push(selection.regions[0].index);
               event.indexes.push(indexes);
               event.valueText = ChartTool.valueText(chart, selection.regions[0]);

               if(ChartTool.areaType(chart, selection.regions[0]) == "text" ||
                  // circle packing container column name is needed to map to level.
                  ChartTool.areaType(chart, selection.regions[0]) == "vo" &&
                  chart.chartType == GraphTypes.CHART_CIRCLE_PACKING)
               {
                  const columnName: string = ChartTool.dimIdx(chart, selection.regions[0]) >= 0
                     ? ChartTool.getDim(chart, selection.regions[0])
                     : ChartTool.getMea(chart, selection.regions[0]);
                  event.columnNames.push([columnName]);
               }

               continue;
            }

            event.regions.push(selection.chartObject ?
               selection.chartObject.areaName : null);

            if(!selection.regions) {
               event.indexes.push(null);
               event.columnNames.push(null);
               continue;
            }

            for(let region of selection.regions) {
               if(selection.chartObject.areaName === "legend_content") {
                  let index: number = ChartTool
                     .getCurrentLegendIndex(selection, (<VSChartModel> obj).legends);
                  indexes.push(index);
               }
               else {
                  indexes.push(region.index);
               }

               const columnName: string = ChartTool.dimIdx(chart, region) >= 0
                  ? ChartTool.getDim(chart, region)
                  : ChartTool.getMea(chart, region);
               columnNames.push(columnName);
            }

            event.indexes.push(indexes);
            event.columnNames.push(columnNames);
         }
         else {
            event.objects.push(obj.absoluteName);
            data = Tool.clone(obj.selectedRegions);

            //shouldn't setFormat for multiple regions
            if(obj.objectType === "VSCalendar" && obj.selectedRegions && obj.selectedRegions.length > 0) {
               let selectRegion = obj.selectedRegions[obj.selectedRegions.length - 1];
               data = Tool.clone([selectRegion]);
            }

            if(data) {
               event.data.push(data);
            }
         }
      }

      return event;
   }

   export function prepareGetFormatEvent(object: VSObjectModel): GetVSObjectFormatEvent {
      let vsevent: GetVSObjectFormatEvent = new GetVSObjectFormatEvent(object ? object.absoluteName : null);

      if(object && object.objectType == "VSChart") {
         let chart: VSChartModel = <VSChartModel> object;

         if(chart.chartSelection && !chart.titleSelected) {
            const selection = chart.chartSelection;

            if(selection.chartObject && selection.chartObject.areaName === "plot_area" &&
               !!selection.regions && selection.regions.length > 0 &&
               (ChartTool.areaType(chart, selection.regions[0]) === "label" ||
                ChartTool.areaType(chart, selection.regions[0]) === "vo" ||
                ChartTool.areaType(chart, selection.regions[0]) === "text"))
            {
               vsevent.region = ChartTool.areaType(chart, selection.regions[0]);
               vsevent.valueText = ChartTool.valueText(chart, selection.regions[0]);
            }
            else {
               vsevent.region = !!selection.chartObject ? selection.chartObject.areaName : null;
            }

            if(vsevent.region == "legend_content") {
               vsevent.index = ChartTool.getCurrentLegendIndex(chart.chartSelection,
                                                               chart.legends);
            }
            else if(!!chart.chartSelection.regions && chart.chartSelection.regions.length > 0) {
               vsevent.index = chart.chartSelection.regions[0].index;
            }

            let columnName: string = null;
            let dimensionColumn: boolean = false;

            if(!!chart.chartSelection.regions && chart.chartSelection.regions.length > 0) {
               columnName = ChartTool.dimIdx(chart, chart.chartSelection.regions[0]) >= 0
                  ? ChartTool.getDim(chart, chart.chartSelection.regions[0])
                  : ChartTool.getMea(chart, selection.regions[0]);
               dimensionColumn = chart.chartSelection.regions[0].valIdx != null &&
                  chart.chartSelection.regions[0].valIdx != -1;
            }

            vsevent.columnName = columnName;
            vsevent.dimensionColumn = dimensionColumn;
         }
      }
      else if(object && (object.objectType == "VSTable" ||
              object.objectType == "VSCalcTable" ||
              object.objectType == "VSCrosstab"))
      {
         let table: BaseTableModel = <BaseTableModel> object;
         vsevent.row = table.firstSelectedRow;
         vsevent.column = table.firstSelectedColumn;
      }

      vsevent.dataPath = object?.selectedRegions?.[object.selectedRegions.length - 1];
      return vsevent;
   }

   /**
    * Check if you can navigate through the given object via keyboard.
    * @param {VSObjectModel} object
    * @returns {boolean}
    */
   export function isKeyNavEnabled(object: VSObjectModel): boolean {
      return !!KEY_NAV_ENABLED[object.objectType];
   }

   /**
    * Same as VSUtil.getTableName.
    */
   export function getTableName(table: string): string {
      if(table && table.startsWith("___inetsoft_cube_")) {
         return table;
      }

      return table ? table.replace(/\./g, "_") : table;
   }

   export function showDropdownMenus(event: MouseEvent, actions: AssemblyActionGroup[],
                                     dropdownService: FixedDropdownService,
                                     inVisibleActions?: string[])
   {
      let options: DropdownOptions = {
         // add 1 to avoid mini-toolbar getting a mouse out on openning ... menu
         position: {x: event.clientX + 1, y: event.clientY},
         contextmenu: true
      };

      let contextmenu: ActionsContextmenuComponent =
         dropdownService.open(ActionsContextmenuComponent, options).componentInstance;
      contextmenu.sourceEvent = event;

      if(inVisibleActions) {
         contextmenu.actionVisible = (action: AssemblyAction) => {
            for(let actionName of inVisibleActions) {
               if(action.id() == actionName) {
                  return false;
               }
            }

            return true;
         };
      }

      contextmenu.actions = actions;
   }
}
