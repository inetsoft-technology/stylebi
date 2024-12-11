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
import { EventEmitter } from "@angular/core";
import { of as observableOf, Subject } from "rxjs";
import { AssetType } from "../../../../../shared/data/asset-type";
import { TimeConditionModel } from "../../../../../shared/schedule/model/time-condition-model";
import { StompMessage } from "../../../../../shared/stomp/stomp-message";
import { AbstractBindingRef } from "../../binding/data/abstract-binding-ref";
import { BAggregateRef } from "../../binding/data/b-aggregate-ref";
import { BDimensionRef } from "../../binding/data/b-dimension-ref";
import { BindingModel } from "../../binding/data/binding-model";
import { AestheticInfo } from "../../binding/data/chart/aesthetic-info";
import { ChartAggregateRef } from "../../binding/data/chart/chart-aggregate-ref";
import {
   ChartAestheticModel,
   ChartBindingModel
} from "../../binding/data/chart/chart-binding-model";
import { ChartDimensionRef } from "../../binding/data/chart/chart-dimension-ref";
import { ColumnRef } from "../../binding/data/column-ref";
import { BaseTableBindingModel } from "../../binding/data/table/base-table-binding-model";
import { CalcTableBindingModel } from "../../binding/data/table/calc-table-binding-model";
import { CellBindingInfo } from "../../binding/data/table/cell-binding-info";
import { CrosstabBindingModel } from "../../binding/data/table/crosstab-binding-model";

import { TableBindingModel } from "../../binding/data/table/table-binding-model";
import { VSLayoutModel } from "../../composer/data/vs/vs-layout-model";
import { VSLayoutObjectModel } from "../../composer/data/vs/vs-layout-object-model";
import { WSAssembly } from "../../composer/data/ws/ws-assembly";
import { ChartAreaName } from "../../graph/model/chart-area-name";
import { VSAnnotationModel } from "../../vsobjects/model/annotation/vs-annotation-model";
import { BaseTableModel } from "../../vsobjects/model/base-table-model";
import { VSCalendarModel } from "../../vsobjects/model/calendar/vs-calendar-model";
import { VSGaugeModel } from "../../vsobjects/model/output/vs-gauge-model";
import { VSImageModel } from "../../vsobjects/model/output/vs-image-model";
import { VSSubmitModel } from "../../vsobjects/model/output/vs-submit-model";
import { VSTextModel } from "../../vsobjects/model/output/vs-text-model";
import { SelectionValueModel } from "../../vsobjects/model/selection-value-model";
import { VSCalcTableModel } from "../../vsobjects/model/vs-calctable-model";
import { VSChartModel } from "../../vsobjects/model/vs-chart-model";
import { VSCheckBoxModel } from "../../vsobjects/model/vs-check-box-model";
import { VSComboBoxModel } from "../../vsobjects/model/vs-combo-box-model";
import { VSCompositeModel } from "../../vsobjects/model/vs-composite-model";
import { VSCrosstabModel } from "../../vsobjects/model/vs-crosstab-model";
import { VSFormatModel } from "../../vsobjects/model/vs-format-model";
import { VSGroupContainerModel } from "../../vsobjects/model/vs-group-container-model";
import { VSInputModel } from "../../vsobjects/model/vs-input-model";
import { VSLineModel } from "../../vsobjects/model/vs-line-model";
import { VSListInputModel } from "../../vsobjects/model/vs-list-input-model";
import { VSNumericRangeModel } from "../../vsobjects/model/vs-numeric-range-model";
import { VSObjectModel } from "../../vsobjects/model/vs-object-model";
import { VSOvalModel } from "../../vsobjects/model/vs-oval-model";
import { VSRadioButtonModel } from "../../vsobjects/model/vs-radio-button-model";
import { VSRangeSliderModel } from "../../vsobjects/model/vs-range-slider-model";
import { VSRectangleModel } from "../../vsobjects/model/vs-rectangle-model";
import { VSSelectionBaseModel } from "../../vsobjects/model/vs-selection-base-model";
import { VSSelectionContainerModel } from "../../vsobjects/model/vs-selection-container-model";
import { VSSelectionListModel } from "../../vsobjects/model/vs-selection-list-model";
import { VSSelectionTreeModel } from "../../vsobjects/model/vs-selection-tree-model";
import { VSSliderModel } from "../../vsobjects/model/vs-slider-model";
import { VSTabModel } from "../../vsobjects/model/vs-tab-model";
import { VSTableModel } from "../../vsobjects/model/vs-table-model";
import { VSTextInputModel } from "../../vsobjects/model/vs-text-input-model";
import { VSUploadModel } from "../../vsobjects/model/vs-upload-model";
import { VSViewsheetModel } from "../../vsobjects/model/vs-viewsheet-model";
import { ColorInfo, MeasureInfo, StrategyInfo, TargetInfo } from "../../widget/target/target-info";
import { TreeNodeModel } from "../../widget/tree/tree-node-model";
import { AbstractDataRef } from "../data/abstract-data-ref";
import { AggregateRef } from "../data/aggregate-ref";
import { BaseFormatModel } from "../data/base-format-model";
import { Condition } from "../data/condition/condition";
import { ConditionOperation } from "../data/condition/condition-operation";
import { ConditionValueType } from "../data/condition/condition-value-type";
import { FormatInfoModel } from "../data/format-info-model";
import { GroupRef } from "../data/group-ref";
import { HyperlinkModel } from "../data/hyperlink-model";
import { TableDataPath } from "../data/table-data-path";
import { CalcTableCell } from "../data/tablelayout/calc-table-cell";
import { VisualFrameModel } from "../data/visual-frame-model";
import { VSObjectFormatInfoModel } from "../data/vs-object-format-info-model";
import { VSObjectType } from "../data/vs-object-type";
import { GraphTypes } from "../graph-types";
import { StyleConstants } from "../util/style-constants";
import { XConstants } from "../util/xconstants";
import { SourceInfo } from "../../binding/data/source-info";

/**
 * Namespace that provides utility methods that are useful in developing unit tests.
 */
export namespace TestUtils {
   /**
    * Creates a new, empty instance of VSFormatModel.
    */
   export function createMockVSFormatModel(): VSFormatModel {
      return {
         alpha: 1,
         foreground: "",
         background: "",
         font: "",
         decoration: "",
         hAlign: "",
         vAlign: "",
         justifyContent: "flex-start",
         alignItems: "stretch",
         border: {
            bottom: "",
            top: "",
            left: "",
            right: ""
         },
         wrapping: {
            whiteSpace: "",
            overflow: "",
            wordWrap: ""
         },
         top: 0,
         left: 0,
         width: 0,
         height: 0,
         zIndex: 0,
         bringToFrontEnabled: false,
         sendToBackEnabled: false,
         position: ""
      };
   }

   /**
    * Creates a new, empty instance of FormatInfoModel.
    *
    */
   export function createMockVSObjectFormatInfoModel(): VSObjectFormatInfoModel {
      return {
         shape: false,
         image: false,
         chart: true,
         colorType: "Static",
         backgroundColorType: "Static",
         backgroundAlpha: 100,
         wrapText: false,
         cssID: null,
         cssClass: "",
         cssIDs: [],
         cssClasses: [],
         cssType: null,
         presenter: null,
         presenterLabel: null,
         presenterHasDescriptors: false,
         ...createMockFromatInfo(),
         type: "inetsoft.web.composer.model.vs.VSObjectFormatInfoModel"
      };
   }

   /**
    * Creates a new, empty instance of FormatInfoModel.
    *
    */
   export function createMockFromatInfo(): FormatInfoModel {
      return {
         type: "",
         color: "",
         backgroundColor: "",
         font: {
            fontFamily: "",
            fontSize: "",
            fontStyle: "",
            fontUnderline: "",
            fontStrikethrough: "",
            fontWeight: ""
         },
         align: {
            valign: "",
            halign: ""
         },
         format: "",
         formatSpec: "",
         dateSpec: "",
         borderColor: "",
         borderTopStyle: "",
         borderTopColor: "",
         borderTopWidth: "",
         borderLeftStyle: "",
         borderLeftColor: "",
         borderLeftWidth: "",
         borderBottomStyle: "",
         borderBottomColor: "",
         borderBottomWidth: "",
         borderRightStyle: "",
         borderRightColor: "",
         borderRightWidth: "",
         halignmentEnabled: true,
         valignmentEnabled: false,
         formatEnabled: true,
         decimalFmts: null
      };
   }

   /**
    * Creates a new, empty instance of VSObjectModel that can be used as the base for
    * specific mock model instances.
    *
    * @param objectType   the specific object type.
    * @param absoluteName the name of the assembly.
    */
   export function createMockVSObjectModel(objectType: VSObjectType,
                                           absoluteName: string): VSObjectModel
   {
      return {
         objectFormat: TestUtils.createMockVSFormatModel(),
         objectType: objectType,
         enabled: true,
         description: "",
         script: null,
         scriptEnabled: false,
         hasCondition: false,
         visible: true,
         absoluteName: absoluteName,
         dataTip: null,
         popComponent: null,
         inEmbeddedViewsheet: false,
         assemblyAnnotationModels: null,
         dataAnnotationModels: null,
         actionNames: null,
         genTime: 1,
         adhocFilterEnabled: false,
         container: null,
         containerType: null,
         sheetMaxMode: false,
         hasDynamic: false,
         popLocation: null
      };
   }

   export function createMockVSCalendarModel(name: string): VSCalendarModel {
      return Object.assign({
         selectedDateFormat: null,
         titleFormat: TestUtils.createMockVSFormatModel(),
         calendarTitleFormat: TestUtils.createMockVSFormatModel(),
         monthFormat: TestUtils.createMockVSFormatModel(),
         yearFormat: TestUtils.createMockVSFormatModel(),
         title: "",
         titleVisible: true,
         dropdownCalendar: false,
         doubleCalendar: false,
         yearView: false,
         daySelection: false,
         singleSelection: false,
         submitOnChange: true,
         period: false,
         dates1: [],
         dates2: [],
         range: {
            minYear: 0,
            minMonth: 0,
            maxYear: 0,
            maxMonth: 0
         },
         rangeRefreshRequired: false,
         currentDate1: {
            year: 0,
            month: 0
         },
         currentDate2: null,
         calendarsShown: false,
         showName: false,
         monthNames: [],
         weekNames: [],
         dayNames: [],
         calendarTitleView1: null,
         calendarTitleView2: null,
         comparisonVisible: false,
      }, createMockVSObjectModel("VSCalendar", name));
   }

   export function createMockVSChartModel(name: string): VSChartModel {
      return Object.assign({
         notAuto: false,
         maxMode: false,
         showValues: false,
         brushed: false,
         zoomed: false,
         hasFlyovers: false,
         flyOnClick: false,
         axes: [],
         facets: [],
         legends: [],
         plot: null,
         titles: [],
         stringDictionary: [],
         regionMetaDictionary: [{meaIdx: 0, dimIdx: -1, colIdx: 0}],
         axisHidden: false,
         titleHidden: false,
         legendHidden: false,
         legendOption: null,
         chartSelection: null,
         initialWidthRatio: 1,
         widthRatio: 1,
         initialHeightRatio: 1,
         heightRatio: 1,
         resized: false,
         verticallyResizable: false,
         horizontallyResizable: false,
         plotHighlightEnabled: true,
         maxHorizontalResize: 20,
         maxVerticalResize: 20,
         mapInfo: false,
         hyperlinks: null,
         legendsBounds: null,
         contentBounds: null,
         chartType: 0,
         multiStyles: false,
         adhocContainerPresent: false,
         enableAdhoc: false,
         showPlotResizers: false,
         showResizePreview: false,
         titleFormat: TestUtils.createMockVSFormatModel(),
         title: "",
         titleVisible: false,
         titleSelected: false,
         clearCanvasSubject: null,
         editedByWizard: false,
         filterFields: [],
         axisFields: [],
         aestheticFields: [],
         trendComparisonAggrNames: [],
         wordCloud: false,
         customPeriod: false,
         dateComparisonEnabled: false,
         dateComparisonDefined: false,
         appliedDateComparison: false,
         dateComparisonDescription: ""
      }, createMockVSObjectModel("VSChart", name));
   }

   export function createMockVSCrosstabModel(name: string): VSCrosstabModel {
      return Object.assign({
         headerHeights: [],
         cellHeight: 0,
         rowNames: [],
         colNames: [],
         aggrNames: [],
         sortTypeMap: {},
         dataTypeMap: {},
         sortOnHeader: false,
         sortAggregate: false,
         sortDimension: false,
         containsFakeAggregate: false,
         dateRangeNames: ["Year(orderdate)", "QuarterOfYear(orderdate)",
            "MonthOfYear(orderdate)", "DayOfMonth(orderdate)", "HourOfDay(orderdate)"],
         cells: [],
         timeSeriesNames: [],
         hasHiddenColumn: false,
         metadata: false,
         trendComparisonAggrNames: [],
         customPeriod: false,
         dateComparisonEnabled: false,
         dateComparisonDefined: false,
         appliedDateComparison: false,
         dcMergedColumn: null
      }, createMockBaseTableModel(name));
   }

   export function createMockVSCalcTableModel(name: string): VSCalcTableModel {
      return Object.assign({}, createMockBaseTableModel(name));
   }

   /**
    * Creates a new, empty instance of CalcTableCell.
    *
    * @param text  the text.
    */
   export function createMockCalcTableCell(text?: string): CalcTableCell {
      return {
         bindingType: 1,
         cellPath: TestUtils.createMockTableDataPath(),
         col: 0,
         row: 0,
         vsFormat: TestUtils.createMockVSFormatModel(),
         text: text,
         span: null,
         baseInfo: null
      };
   }

   /**
    * Creates a new, empty instance of CellBindingInfo.
    *
    * @param name  the text.
    */
   export function createMockCellBindingInfo(name?: string): CellBindingInfo {
      return {
         type: 2,
         btype: 1,
         name: name,
         expansion: 2,
         mergeCells: false,
         mergeRowGroup: "(default)",
         mergeColGroup: "(default)",
         rowGroup: "(default)",
         colGroup: "(default)",
         value: "",
         formula: null,
         order: null,
         topn: null,
         timeSeries: false,
         runtimeName: null
      };
   }

   /**
    * Creates a new, empty instance of TableDataPath.
    */
   export function createMockTableDataPath(): TableDataPath {
      return {
         col: false,
         colIndex: -1,
         dataType: "string",
         index: 0,
         level: -1,
         path: null,
         row: false,
         type: null
      };
   }

   export function createMockVSTableModel(name: string): VSTableModel {
      return Object.assign({
         headerHeight: 0,
         cellHeight: 0,
         colNames: [],
         headersSortType: [],
         insert: false,
         del: false,
         edit: false,
         form: false,
         embedded: false,
         summary: false,
         submitOnChange: false,
         columnEditorEnabled: [],
         formChanged: false,
         sortPositions: [],
         writeBack: false,
      }, createMockBaseTableModel(name));
   }

   export function createMockBaseTableModel(name: string): BaseTableModel {
      return Object.assign({
         colWidths: [],
         rowCount: 0,
         colCount: 0,
         dataRowCount: 0,
         dataColCount: 0,
         headerRowCount: 0,
         headerColCount: 0,
         scrollHeight: 0,
         headerRowPositions: [],
         dataRowPositions: [],
         wrapped: false,
         metadata: false,
         headerRowHeights: [0],
         dataRowHeight: 0,
         visibleRows: 0,
         title: "Table",
         titleFormat: TestUtils.createMockVSFormatModel(),
         titleVisible: true,
         shrink: false,
         hasFlyover: false,
         isFlyOnClick: false,
         lastSelected: {row: 0, column: 0},
         firstSelectedRow: 0,
         firstSelectedColumn: 0,
         adhocFilterEnabled: false,
         enableAdhoc: false,
         enableAdvancedFeatures: false,
         sortInfo: null,
         explicitTableWidth: false,
         highlightedCells: [],
         isHighlightCopied: false,
         maxMode: false,
         editedByWizard: false
      }, createMockVSObjectModel("VSTable", name));
   }

   /**
    * Creates a new, empty instance of GroupRef.
    *
    * @param name  the GroupRef name.
    */
   export function createMockGroupRef(name?: string): GroupRef {
      return Object.assign({
         assemblyName: null,
         ref: null,
         dgroup: 0,
      }, createMockDataRef(name));
   }

   /**
    * Creates a new, empty instance of AggregateRef.
    *
    * @param name  the AggregateRef name.
    */
   export function createMockAggregateRef(name?: string): AggregateRef {
      return Object.assign({
         ref: null,
         ref2: null,
         formulaName: null,
         percentage: false
      }, createMockDataRef());
   }

   /**
    * Creates a new, empty instance of DataRef.
    *
    * @param name  the dataref name.
    */
   export function createMockDataRef(name?: string): AbstractDataRef {
      return {
         name: name,
         view: null,
         attribute: name,
         dataType: "string",
         defaultFormula: null,
         expression: null,
         entity: null,
         entityBlank: null,
         classType: null,
         refType: 0
      };
   }

   /**
    * Creates a new, empty instance of ColumnRef.
    *
    * @param name  the ColumnRef name.
    */
   export function createMockColumnRef(name?: string): ColumnRef {
      return {
         alias: null,
         attribute: name,
         dataRefModel: createMockDataRef(name),
         dataType: "string",
         defaultFormula: null,
         description: "",
         entity: null,
         name: name,
         refType: 0,
         entityBlank: null,
         width: 1,
         visible: true,
         valid: true,
         sql: true
      };
   }

   /**
    * Creates a new, empty instance of AbstractBindingRef.
    *
    * @param name  the bindingref name.
    */
   export function createMockBindingRef(name?: string): AbstractBindingRef {
      return Object.assign({
         fullName: name,
         dataRefModel: createMockDataRef(name)
      }, createMockDataRef(name));
   }

   /**
    * Creates a new, empty instance of BDimensionRef.
    *
    * @param name  the BDimensionRef name.
    */
   export function createMockBDimensionRef(name?: string): BDimensionRef {
      return Object.assign({
         refType: 0,
         order: XConstants.SORT_ASC,
         sortByCol: null,
         rankingOption: ConditionOperation.NONE + "",
         rankingN: "3",
         rankingCol: null,
         groupOthers: false,
         others: false,
         dateLevel: "-1",
         dateInterval: 0,
         timeSeries: false,
         namedGroupInfo: null,
         summarize: null,
         pageBreak: false,
         columnValue: name,
         caption: null,
         manualOrder: [],
         classType: "dimension",
         dataType: "string",
         sortOptionModel: {
            aggregateRefs: []
         }
      }, createMockBindingRef(name));
   }

   /**
    * Creates a new, empty instance of ChartDimensionRef.
    *
    * @param name  the ChartDimensionRef name.
    */
   export function createMockChartDimensionRef(name?: string): ChartDimensionRef {
      return Object.assign({
         otherEnable: false,
         refConvertEnabled: true,
         measure: false,
         original: null
      }, createMockBDimensionRef(name));
   }

   /**
    * Creates a new, empty instance of AestheticInfo.
    *
    * @param name  the AestheticInfo name.
    */
   export function createMockAestheticInfo(name?: string): AestheticInfo {
      return {
         fullName: name,
         dataInfo: null,
         frame: null,
      };
   }

   /**
    * Creates a new, empty instance of VisualFrameModel.
    *
    * @param field  the VisualFrameModel field.
    */
   export function createMockVisualFrameModel(field?: string): VisualFrameModel {
      return Object.assign({
         name: null,
         field: field,
         summary: false,
         changed: false,
      });
   }

   /**
    * Creates a new, empty instance of BAggregateRef.
    *
    * @param name  the BAggregateRef name.
    */
   export function createMockBAggregateRef(name?: string): BAggregateRef {
      return Object.assign({
         refType: 0,
         formula: null,
         formulaValue: null,
         percentage: "-1",
         num: 0,
         numValue: "0",
         secondaryColumn: null,
         secondaryColumnValue: null,
         columnValue: name,
         caption: name,
         classType: "aggregate",
         dataType: "integer",
         formulaOptionModel: {
            aggregateStatus: false
         }
      }, createMockBindingRef(name));
   }

   /**
    * Creates a new, empty instance of ChartAggregateRef.
    *
    * @param name  the ChartAggregateRef name.
    */
   export function createMockChartAggregateRef(name?: string): ChartAggregateRef {
      return Object.assign({
            discrete: false,
            summaryColorFrame: null,
            summaryTextureFrame: null,
            calculateInfo: null,
            secondaryY: false,
            secondaryAxisSupported: true,
            aggregated: true,
            oriFullName: name,
            oriView: name,
            refConvertEnabled: true,
            measure: true,
            defaultFormula: "",
            original: null,
            classType: "aggregate",
            buildInCalcs: []
         },
         createMockChartAestheticModel(),
         createMockBAggregateRef(name)
      );
   }

   export function createMockBindingModel(type: string): BindingModel {
      return {
         source: createMockSourceInfo(type),
         sqlMergeable: true,
         availableFields: null,
         type: type
      };
   }

   export function createMockSourceInfo(type: string): SourceInfo {
      return {
         source: "",
         prefix: "",
         type: SourceInfo.NONE,
         dataSourceType: null,
         supportFullOutJoin: true,
         rest: false,
         browsable: true,
         joinSources: null
      };
   }

   export function createMockBaseBindingModel(type: string): BaseTableBindingModel {
      return Object.assign({
         aggregates: [],
         allRows: [],
         name2Labels: new Map<string, string>(),
      }, createMockBindingModel(type));
   }

   export function createMockCrosstabBindingModel(): CrosstabBindingModel {
      return Object.assign({
         option: null,
         rows: [],
         cols: [],
         suppressGroupTotal: {},
         hasDateComparison: false
      }, createMockBaseBindingModel("crosstab"));
   }

   export function createMockCalcTableBindingModel(): CalcTableBindingModel {
      return Object.assign({}, createMockBaseBindingModel("calctable"));
   }

   /**
    * [createMockTableBindingModel create a new, empty instance of TableBindingModel]
    * @return {TableBindingModel} [description]
    */
   export function createMockTableBindingModel(): TableBindingModel {
      return Object.assign({
         option: null,
         groups: [],
         details: [],
         embedded: false,
      }, createMockBaseBindingModel("table"));
   }

   /**
    * Creates a new, empty instance of ChartAestheticModel.
    */
   export function createMockChartAestheticModel(): ChartAestheticModel {
      return {
         chartType: GraphTypes.CHART_AUTO,
         rtchartType: GraphTypes.CHART_AUTO,
         colorField: null,
         shapeField: null,
         sizeField: null,
         textField: null,
         colorFrame: null,
         shapeFrame: null,
         lineFrame: null,
         textureFrame: null,
         sizeFrame: null
      };
   }

   /**
    * Creates a new, empty instance of ChartBindingModel.
    */
   export function createMockChartBindingModel(): ChartBindingModel {
      return Object.assign({
         waterfall: false,
         multiStyles: false,
         separated: true,
         mapType: "",
         allChartAggregate: null,
         xfields: [],
         yfields: [],
         geoFields: [],
         groupFields: [],
         supportsGroupFields: false,
         openField: null,
         closeField: null,
         highField: null,
         lowField: null,
         pathField: null,
         supportsPathField: false,
         pointLine: false,
         geoCols: [],
         type: "chart",
         stackMeasures: false,
         hasDateComparison: false,
         nodeColorField: null,
         nodeSizeField: null
      }, createMockChartAestheticModel(),  createMockBindingModel("chart"));
   }

   export function createMockBaseFormatModel(): BaseFormatModel {
      return {
         top: 0,
         left: 0,
         width: 0,
         height: 0,
         zIndex: 0,
         border: null,
         wrapping: null,
         position: ""
      };
   }

   export function createMockStompClientService(): any {
      const stompMessages = new Subject<StompMessage>();
      const whenDisconnected = new Subject<void>();
      const stompConnection = {
         subscribe: jest.fn(),
         send: jest.fn(),
         disconnect: jest.fn(),
         onHeartbeat: new EventEmitter<any>()
      };
      stompConnection.subscribe.mockImplementation((destination: string, next?: (value: StompMessage) => void, error?: (error: any) => void, complete?: () => void) => {
         return stompMessages.subscribe(next, error, complete);
      });
      return {
         connect: jest.fn(() => observableOf(stompConnection)),
         whenDisconnected: jest.fn(() => observableOf(whenDisconnected))
      };
   }

   export function createMockVSComboBoxModel(name: string): VSComboBoxModel {
      return Object.assign({
         selectedLabel: "",
         selectedObject: null,
         rowCount: 0,
         editable: false,
         refresh: false,
         dataType: "",
         calendar: false,
         labels: null,
         values: null
      }, createMockVSObjectModel("VSComboBox", name));
   }

   /**
    * Creates a new, empty instance of VSSelectionList.
    *
    * @param name  the VSSelectionList name.
    */
   export function createMockVSSelectionListModel(name: string): VSSelectionListModel {
      return Object.assign({
         selectionList: {
            formats: null,
            selectionValues: [],
            measureMin: 0,
            measureMax: 0
         },
         supportRemoveChild: true,
         adhocFilter: false,
         numCols: 0,
         selectRegions: "",
      }, createMockVSSelectionBaseModel("VSSelectionList", name));
   }

   /**
    * Creates a new, empty instance of VSSelectionTree.
    *
    * @param name  the VSSelectionTree name.
    */
   export function createMockVSSelectionTreeModel(name: string): VSSelectionTreeModel {
      return Object.assign({
         root: null,
         mode: 1,
         selectChildren: false,
         expandAll: false,
         levels: 1
      }, createMockVSSelectionBaseModel("VSSelectionTree", name));
   }

   /**
    * Creates a new, empty instance of VSSelectionBase.
    *
    * @param name  the VSSelection name.
    * @param type  the VSComposite type, VSSelectionList or VSSelectionTree
    */
   export function createMockVSSelectionBaseModel(type: VSObjectType, name: string): VSSelectionBaseModel {
      return Object.assign({
         measureFormats: null,
         dropdown: false,
         hidden: false,
         listHeight: 6,
         cellHeight: 18,
         titleRatio: 1,
         singleSelection: false,
         showText: false,
         showBar: false,
         barWidth: 0,
         textWidth: 0,
         submitOnChange: true,
         sortType: 8
      }, createMockVSCompositeModel(type, name));
   }

   /**
    * Creates a new, empty instance of VSSelectionContainerModel.
    *
    * @param name  the VSSelectionContainer name.
    */
   export function createMockVSSelectionContainerModel(name: string): VSSelectionContainerModel {
      return Object.assign({
         title: "",
         titleRatio: 0,
         dataRowHeight: 1,
         outerSelections: [],
         childObjects: [],
         childrenNames: ["a", "b"],
         isDropTarget: false,
         supportRemoveChild: true,
      }, createMockVSCompositeModel("VSSelectionContainer", name));
   }

   /**
    * Creates a new, empty instance of VSComposite.
    *
    * @param name  the VSComposite name.
    * @param type  the VSComposite type, VSSelectionList or VSSelectionTree
    */
   export function createMockVSCompositeModel(type: VSObjectType, name: string): VSCompositeModel {
      return Object.assign({
         title: "",
         titleFormat: TestUtils.createMockVSFormatModel(),
         titleVisible: true
      }, createMockVSObjectModel(type, name));
   }

   /**
    * Creates a new, empty instance of VSRadioButtonModel.
    *
    * @param name  the VSRadioButton name.
    */
   export function createMockVSRadioButtonModel(name: string): VSRadioButtonModel {
      return Object.assign({
         detailFormat: TestUtils.createMockVSFormatModel(),
         titleFormat: TestUtils.createMockVSFormatModel(),
         title: "",
         titleVisible: true,
         selectedLabel: "",
         selectedObject: "",
         refresh: true,
         dataRowCount: 0,
         dataColCount: 0,
         cellHeight: 18
      }, createMockVSListInputModel("VSRadioButton", name));
   }

   /**
    * Creates a new, empty instance of VSCheckBoxModel.
    *
    * @param name  the VSCheckBox name.
    */
   export function createMockVSCheckBoxModel(name: string): VSCheckBoxModel {
      return Object.assign({
         detailFormat: TestUtils.createMockVSFormatModel(),
         titleFormat: TestUtils.createMockVSFormatModel(),
         title: "",
         titleVisible: true,
         selectedLabels: [],
         selectedObjects: [],
         refresh: true,
         dataRowCount: 0,
         dataColCount: 0,
      }, createMockVSListInputModel("VSCheckBox", name));
   }

   /**
    * Creates a new, empty instance of VSListInputModel.
    *
    * @param name  the VSListInput name.
    * @param type  the VSListInput type, VSRadioButton, VSCheckBox or VSComboBox
    */
   export function createMockVSListInputModel(type, name): VSListInputModel {
      return Object.assign({
         labels: [],
         values: []
      }, createMockVSInputModel(type, name));
   }

   /**
    * Creates a new, empty instance of VSInputModel.
    *
    * @param name  the VSInput name.
    * @param type  the VSInput type, VSListInput, VSNumericRange
    */
   export function createMockVSInputModel(type, name): VSInputModel {
      return Object.assign({
         refresh: true
      }, createMockVSObjectModel(type, name));
   }

   /**
    * Creates a new, empty instance of ChartRegion.
    *
    */
   export function createMockChartRegion() {
      return {
         segTypes: [],
         pts: [],
         centroid: null,
         index: -1,
         tipIdx: -1,
         metaIdx: 0,
         dimIdx: -1,
         rowIdx: 0,
         valIdx: -1,
         axisFldIdx: -1,
         hyperlinks: [],
         noselect: false,
         grouped: false,
         dType: "",
         hasMeasure: false,
         boundaryIdx: -1,
         vertical: false,
         refLine: false
      };
   }

   /**
    * Creates a new, empty instance of ChartObject.
    */
   export function createMockChartObject(name: ChartAreaName) {
      return {
         areaName: name,
         bounds: null,
         layoutBounds: null,
         tiles: null,
         regions: [],
      };
   }

   /**
    * Creates a new, empty instance of Condition.
    */
   export function createMockCondition(): Condition {
      return {
         jsonType: "condition",
         field: {
            attribute: "",
            dataType: "",
         },
         operation: ConditionOperation.EQUAL_TO,
         values: [{
            type: ConditionValueType.VALUE,
            value: ""
         }],
         level: 0,
         equal: true,
         negated: false
      };
   }

   /**
    * Creates a new, empty instance of selectedRegion.
    */
   export function createMockselectedRegion() {
      return {
         col: false,
         colIndex: -1,
         dataType: "string",
         index: 0,
         level: -1,
         path: [],
         row: false,
         type: null
      };
   }

   /**
    * Creates a new, empty instance of VSLine.
    */
   export function createMockVSLineModel(name: string): VSLineModel {
      return Object.assign({
         startLeft: 10,
         startTop: 10,
         startAnchorStyle: 0,
         endLeft: 0,
         endTop: 0,
         endAnchorStyle: 1,
         color: "#55555",
         lineStyle: StyleConstants.THIN_LINE,
         locked: false,
         shadow: false
      }, createMockVSObjectModel("VSLine", name));
   }

   /**
    * Creates a new, empty instance of VSRectangle.
    */
   export function createMockVSRectangleModel(name: string): VSRectangleModel {
      return Object.assign({
         roundCornerValue: 0,
         locked: false,
         lineStyle: StyleConstants.THIN_LINE,
         shadow: false
      }, createMockVSObjectModel("VSRectangle", name));
   }

   /**
    * Creates a new, empty instance of VSOval.
    */
   export function createMockVSOvalModel(name: string): VSOvalModel {
      return Object.assign({
         locked: false,
         lineStyle: StyleConstants.THIN_LINE,
         shadow: false
      }, createMockVSObjectModel("VSOval", name));
   }

   /**
    * Creates a new, empty instance of VSSliderModel.
    *
    * @param name  the VSSlider name.
    */
   export function createMockVSSliderModel(name: string): VSSliderModel {
      return Object.assign({
         labels: [],
         minVisible: true,
         maxVisible: true,
         ticksVisible: true,
         labelVisible: true,
         currentVisible: true
      }, createMockVSNumericRangeModel("VSSlider", name));
   }

   /**
    * Creates a new, empty instance of VSNumericRangeModel.
    *
    * @param name  the VSNumericRange name.
    * @param type  the VSNumericRange type, VSSlider, VSSpinner
    */
   export function createMockVSNumericRangeModel(type, name): VSNumericRangeModel {
      return Object.assign({
         min: 0,
         max: 100,
         increment: 20,
         value: 0,
         currentLabel: ""
      }, createMockVSInputModel(type, name));
   }

   /**
    * Creates a new, empty instance of VSSubmitModel.
    *
    * @param name  the VSSubmit name.
    */
   export function createMockVSSubmitModel(name): VSSubmitModel {
      return Object.assign({
         label: "Submit",
         refresh: true,
         clickable: false
      }, createMockVSObjectModel("VSSubmit", name));
   }

   /**
    * Creates a new, empty instance of VSUploadModel.
    *
    * @param name  the VSUpload name.
    */
   export function createMockVSUploadModel(name): VSUploadModel {
      return Object.assign({
         label: "Upload",
         fileName: "",
         refresh: true,
         submitOnChange: true
      }, createMockVSObjectModel("VSUpload", name));
   }

   /**
    * Creates a new, empty instance of VSRangeSliderModel.
    *
    * @param name  the VSRangeSlider name.
    */
   export function createMockVSRangeSliderModel(name): VSRangeSliderModel {
      return Object.assign({
         minVisible: true,
         maxVisible: true,
         tickVisible: true,
         currentVisible: true,
         labels: [],
         values: [],
         selectStart: 1,
         selectEnd: 19,
         supportRemoveChild: true,
         hidden: false,
         upperInclusive: true,
         composite: false,
         dataType: "integer",
         title: "title",
         titleRatio: 0,
         adhocFilter: false,
         submitOnChange: true,
         titleFormat: TestUtils.createMockVSFormatModel(),
         maxRangeBarWidth: 0
      }, createMockVSObjectModel("VSRangeSlider", name));
   }

   /**
    * Creates a new, empty instance of VSTextInputModel.
    *
    * @param name  the VSTextInput name.
    */
   export function createMockVSTextInputModel(name): VSTextInputModel {
      return Object.assign({
         text: "TextInput",
         pattern: "",
         message: "",
         prompt: "",
         multiLine: false,
         option: "",
         max: "",
         min: "",
         insetStyle: false,
         defaultText: ""
      }, createMockVSInputModel("VSTextInput", name));
   }

   /**
    * Creates a new, empty instance of VSTextModel.
    *
    * @param name  the VSText name.
    */
   export function createMockVSTextModel(name): VSTextModel {
      return Object.assign({
         text: "hello",
         shadow: false,
         autoSize: false,
         url: false,
         hyperlinks: null,
         presenter: false,
         clickable: false
      }, createMockVSObjectModel("VSText", name));
   }

   /**
    * Creates a new, empty instance of VSImageModel.
    *
    * @param name  the VSImage name.
    */
   export function createMockVSImageModel(name): VSImageModel {
      return Object.assign({
         noImageFlag: true,
         animateGif: false,
         alpha: "0.6",
         locked: false,
         hyperlinks: null,
         shadow: false,
         clickable: false,
         scaleInfo: {
            scaleImage: false,
            tiled: false,
            preserveAspectRatio: false
         }
      }, createMockVSObjectModel("VSImage", name));
   }

   /**
    * Creates a new, empty instance of VSGaugeModel.
    *
    * @param name  the VSGauge name.
    */
   export function createMockVSGaugeModel(name): VSGaugeModel {
      return Object.assign({
         locked: false,
         hyperlinks: null,
         clickable: false
      }, createMockVSObjectModel("VSGauge", name));
   }

   /**
    * Creates a new, empty instance of VSGroupContainerModel.
    *
    * @param name  the VSGroupContainer name.
    */
   export function createMockVSGroupContainerModel(name): VSGroupContainerModel {
      return Object.assign({
         noImageFlag: true,
         tile: false,
         imageAlpha: null,
         scaleInfo: {
            scaleImage: false,
            tiled: false,
            preserveAspectRatio: false
         }
      }, createMockVSObjectModel("VSGroupContainer", name));
   }

   /**
    * Creates a new, empty instance of VSTabModel.
    *
    * @param name  the VSTab name.
    */
   export function createMockVSTabModel(name): VSTabModel {
      return Object.assign({
         labels: ["table1", "table2"],
         childrenNames: ["table1", "table2"],
         selected: "table1",
         activeFormat: createMockVSFormatModel(),
         roundTopCornersOnly: true
      }, createMockVSObjectModel("VSTab", name));
   }

   /**
    * Creates a new, empty instance of VSViewsheetModel.
    *
    * @param name  the VSViewsheet name.
    */
   export function createMockVSViewsheetModel(name): VSViewsheetModel {
      return Object.assign({
         bounds: {
            x: 0,
            y: 0,
            width: 0,
            height: 0
         },
         iconHeight: 0,
         id: "vs1",
         embeddedIconVisible: false,
         embeddedOpenIconVisible: true,
         embeddedIconTooltip: "",
         hyperlinkModel: null
      }, createMockVSObjectModel("VSViewsheet", name));
   }

   /**
    * Creates a new, empty instance of VSAnnotationModel.
    *
    * @param name  the VSAnnotation name.
    */
   export function createMockVSAnnotationModel(name): VSAnnotationModel {
      return Object.assign({
         annotationRectangleModel: createMockVSRectangleModel("rec1"),
         annotationLineModel: createMockVSLineModel("line1"),
         contentModel: {
            content: "test"
         },
         row: 0,
         col: 0,
         hidden: false,
         annotationType: -1
      }, createMockVSObjectModel("VSAnnotation", name));
   }

   /**
    * Creates a new, empty instance of HyperlinkModel.
    */
   export function createMockHyperlinkModel(): HyperlinkModel[] {
      return [{
         name: "aaa",
         label: "aaa",
         link: "www.baidu.com",
         query: null,
         wsIdentifier: null,
         targetFrame: "bb",
         tooltip: "",
         bookmarkName: null,
         bookmarkUser: null,
         parameterValues: [],
         sendReportParameters: false,
         sendSelectionParameters: false,
         disablePrompting: false,
         linkType: 1
      }];
   }

   /**
    * Creates a new, empty instance of SelectionValueModel.
    */
   export function createMockSelectionValues(): SelectionValueModel {
      return {
         label: "",
         value: "",
         state: 8,
         level: 0,
         measureLabel: "",
         measureValue: 0,
         maxLines: 0,
         formatIndex: 0,
         others: false,
         more: false,
         excluded: false,
         parentNode: null,
         path: "",
      };
   }

   export function createMockWSAssemblyModel(): WSAssembly {
      return {
         name: "WS Assembly",
         description: null,
         top: 0,
         left: 0,
         width: 0,
         height: 0,
         dependeds: [],
         dependings: [],
         primary: false,
         info: {
            editable: true,
            mirrorInfo: null
         },
         classType: null
      };
   }

   /**
    * Creates a new, empty instance of VSLayoutModel.
    */
   export function createMockVSLayoutModel(name: string): VSLayoutModel {
      return Object.assign({
         name: name,
         objects: null,
         selectedObjects: [],
         printLayout: false,
         guideType: 0,
         // Print Layout Info
         currentPrintSection: 1,
         unit: "",
         width: 1,
         height: 1,
         marginTop: 0,
         marginLeft: 0,
         marginRight: 0,
         marginBottom: 0,
         headerFromEdge: 0,
         footerFromEdge: 0,
         headerObjects: [],
         footerObjects: [],
         horizontal: false,
         runtimeID: "",

         socketConnection: null,
         focusedObjects: null,
         focusedObjectsSubject: null
      }, new VSLayoutModel());
   }

   /**
    * Creates a new, empty instance of VSLayoutObjectModel.
    */
   export function createMockVSLayoutObjectModel(objectName: string): VSLayoutObjectModel {
      return {
         editable: false,
         name: objectName,
         left: 0,
         top: 0,
         width: 2,
         height: 2,
         tableLayout: 0,
         supportTableLayout: false,
         objectModel: null,
         childModels: null,
      };
   }

   /**
    * Creates a new, empty instance of TargetInfo.
    */
   export function createMockTargetInfo(): TargetInfo {
      return {
         measure: createMockTargetMeasureInfo(),
         fieldLabel: "",
         genericLabel: "",
         value: "",
         label: "",
         toValue: "",
         toLabel: "",
         labelFormats: "",
         lineStyle: 0,
         lineColor: createMockTargetColorInfo(),
         fillAboveColor: createMockTargetColorInfo(),
         fillBelowColor: createMockTargetColorInfo(),
         alpha: "",
         fillBandColor: createMockTargetColorInfo(),
         chartScope: false,
         index: 0,
         tabFlag: 0,
         changed: false,
         targetString: "",
         strategyInfo: createMockTargetStrategyInfo(),
         bandFill: null,
         supportFill: false
      };
   }

   /**
    * Creates a new, empty instance of MeasureInfo.
    */
   export function createMockTargetMeasureInfo(): MeasureInfo {
      return {
         name: "",
         label: "",
         dateField: false
      };
   }

   /**
    * Creates a new, empty instance of StrategyInfo.
    */
   export function createMockTargetStrategyInfo(): StrategyInfo {
      return {
         name: "",
         value: "",
         label: "",
         percentageAggregateVal: "",
         standardIsSample: true
      };
   }

   /**
    * Creates a new, empty instance of ColorInfo.
    */
   export function createMockTargetColorInfo(): ColorInfo {
      return {
         color: "",
         type: ""
      };
   }

   /**
    * Creates a new, empty instance of TimeConditionModel.
    */
   export function createMockTimeConditionModel(): TimeConditionModel {
      return {
         hour: null,
         minute: null,
         second: null,
         hourEnd: null,
         minuteEnd: null,
         secondEnd: null,
         dayOfMonth: 1,
         dayOfWeek: 1,
         weekOfMonth: 1,
         type: 1,
         interval: null,
         hourlyInterval: null,
         weekdayOnly: false,
         daysOfWeek: [],
         monthsOfYear: [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11],
         monthlyDaySelected: null,
         date: null,
         dateEnd: null,
         conditionType: null,
         label: null
      };
   }

   /**
    * Creates a function to change value type.
    * index: 0: value, 1: variable, 2 Expression
    */
   export function changeDynamicComboValueType(index: number) {
      let fixs = document.getElementsByTagName("fixed-dropdown");
      if(fixs.length != 0) {
         let temp = fixs[0].querySelectorAll("a");
         temp[index].click();
      }
      else {
         console.log("LOG: dynimic combox dropdown did not pop up");
      }
   }

   /**
    * Creates a new, worksheet data tree.
    */
   export function createMockWorksheetDataTree(): TreeNodeModel {
      return {
         children: [
            {
               children: [
                  {
                     children: [
                        {
                           children: [],
                           data: {
                              attribute: "state",
                              dataType: "string",
                              path: "/baseWorksheet/Query1/state",
                              table: "Query1",
                              properties: {}
                           },
                           label: "state",
                           leaf: true,
                           type: "columnNode"
                        },
                        {
                           children: [],
                           data: {
                              attribute: "id",
                              dataType: "integer",
                              path: "/baseWorksheet/Query1/id",
                              table: "Query1",
                              properties: {}
                           },
                           label: "id",
                           leaf: true,
                           type: "columnNode"
                        }],
                     data: {
                        folder: true,
                        type: AssetType.TABLE,
                        properties: {
                           CUBE_TABLE: false
                        },
                        path: "/baseWorksheet/Query1",
                        identifier: "0^21^__NULL__^/baseWorksheet/Query1"
                     },
                     label: "Query1",
                     expanded: true,
                     leaf: false,
                     type: "table"
                  }
               ],
               data: {
                  folder: false,
                  type: AssetType.WORKSHEET,
                  properties: {},
                  identifier: "1^2^__NULL__^chartUI",
                  path: "chartUI"
               },
               label: "chartUI",
               leaf: false,
               type: "worksheet",
               expanded: true
            }
         ],
         leaf: false,
         expanded: true
      };
   }

   /**
    * Creates a new, Logical Model data tree.
    */
   export function createMockLMDataTree(): TreeNodeModel {
      return {
         children: [
            {
               children: [{
                  children: [
                     {
                        children: [],
                        data: {
                           attribute: "state",
                           dataType: "string",
                           path: "/baseWorksheet/entity1/state",
                           table: "entity1",
                           properties: {}
                        },
                        label: "state",
                        leaf: true,
                        type: "columnNode"
                     },
                     {
                        children: [],
                        data: {
                           attribute: "id",
                           dataType: "integer",
                           path: "/baseWorksheet/entity1/id",
                           table: "entity1",
                           properties: {}
                        },
                        label: "id",
                        leaf: true,
                        type: "columnNode"
                     }
                  ],
                  data: {
                     folder: true,
                     identifier: "0^21^__NULL__^/baseWorksheet/entity1",
                     path: "/baseWorksheet/entity1",
                     type: AssetType.TABLE,
                     properties: {
                        CUBE_TABLE: false
                     }
                  },
                  expanded: true,
                  label: "entity1",
                  leaf: false,
                  type: "table"
               },
                  {
                     children: null,
                     data: {
                        attribute: "CalcField",
                        dataType: "string",
                        path: "/baseWorksheet/LM/CalcField",
                        table: "LM",
                        properties: {}
                     },
                     expanded: true,
                     label: "CalcField",
                     leaf: true,
                     type: "columnNode"
                  }],
               data: {
                  folder: true,
                  identifier: "0^101^__NULL__^/baseWorksheet/LM",
                  path: "/baseWorksheet/LM",
                  type: AssetType.LOGIC_MODEL,
                  properties: {}
               },
               expanded: true,
               label: "LM",
               leaf: false,
               type: "folder"
            }
         ],
         leaf: false,
         expanded: true
      };
   }

   /**
    * Description: get the div element for dynamic combo box element if it is valueOnly.
    * parentElement: the parement element of dynamic combo box
    */
   export function getDynamicComboDiv(parentElement: any): HTMLElement {
      return parentElement.querySelector("div.dynamic-combo-box-body > div:not(.input-group-append)");
   }

   export function toString(localStr: string): string {
      let str = localStr.slice(localStr.indexOf("(") + 1, localStr.lastIndexOf(")"));

      if(str.startsWith("js:")) {
         str = str.substring(3);
      }

      return str;
   }
}
