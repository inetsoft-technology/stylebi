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
import { HttpClient, HttpParams } from "@angular/common/http";
import {
   ChangeDetectionStrategy,
   ChangeDetectorRef,
   Component,
   ElementRef,
   EventEmitter,
   Input,
   NgZone,
   OnChanges,
   OnDestroy,
   OnInit,
   Optional,
   Output,
   Renderer2,
   SimpleChanges,
   ViewChild
} from "@angular/core";
import { Validators } from "@angular/forms";
import { NgbModal, NgbTooltip } from "@ng-bootstrap/ng-bootstrap";
import { map } from "rxjs/operators";
import { DownloadService } from "../../../../../../shared/download/download.service";
import { Tool } from "../../../../../../shared/util/tool";
import { AssemblyActionGroup } from "../../../common/action/assembly-action-group";
import { TableTransfer } from "../../../common/data/dnd-transfer";
import { TableDataPathTypes } from "../../../common/data/table-data-path-types";
import { DndService } from "../../../common/dnd/dnd.service";
import { ChartConstants } from "../../../common/util/chart-constants";
import { ComponentTool } from "../../../common/util/component-tool";
import { DataPathConstants } from "../../../common/util/data-path-constants";
import { XConstants } from "../../../common/util/xconstants";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { VsWizardEditModes } from "../../../vs-wizard/model/vs-wizard-edit-modes";
import { InputNameDialog } from "../../../widget/dialog/input-name-dialog/input-name-dialog.component";
import { FixedDropdownService } from "../../../widget/fixed-dropdown/fixed-dropdown.service";
import { DebounceService } from "../../../widget/services/debounce.service";
import { ModelService } from "../../../widget/services/model.service";
import { ScaleService } from "../../../widget/services/scale/scale-service";
import { DialogService } from "../../../widget/slide-out/dialog-service.service";
import { CrosstabActions } from "../../action/crosstab-actions";
import { ClearSelectionCommand } from "../../command/clear-selection-command";
import { LoadTableDataCommand } from "../../command/load-table-data-command";
import { ContextProvider } from "../../context-provider.service";
import { RichTextService } from "../../dialog/rich-text-dialog/rich-text.service";
import { AddAnnotationEvent } from "../../event/annotation/add-annotation-event";
import { GroupFieldsEvent } from "../../event/group-fields-event";
import { BaseTableDrillEvent } from "../../event/table/base-table-drill-event";
import { DrillCellsEvent, DrillTarget } from "../../event/table/drill-cells-event";
import { DrillEvent } from "../../event/table/drill-event";
import { SortColumnEvent } from "../../event/table/sort-column-event";
import { BaseTableCellModel } from "../../model/base-table-cell-model";
import { VSCrosstabModel } from "../../model/vs-crosstab-model";
import { ShowHyperlinkService } from "../../show-hyperlink.service";
import { CheckFormDataService } from "../../util/check-form-data.service";
import { ViewerResizeService } from "../../util/viewer-resize.service";
import { VSUtil } from "../../util/vs-util";
import { DataTipService } from "../data-tip/data-tip.service";
import { BaseTable } from "./base-table";
import { VSTableCell } from "./vs-table-cell.component";
import { DrillLevel } from "../../../composer/data/vs/drill-level";
import { PopComponentService } from "../data-tip/pop-component.service";
import { DateComparisonDialogModel } from "../../model/date-comparison-dialog-model";
import { DateComparisonDialog } from "../../dialog/date-comparison-dialog/date-comparison-dialog.component";
import { CrosstabActionHandler } from "./crosstab-action-handler";
import { loadingScriptTreeModel } from "../../../widget/dialog/script-pane/script-pane-tree-model";
import { AssemblyActionEvent } from "../../../common/action/assembly-action-event";
import { ShowHideCrosstabColumnsEvent } from "../../event/show-hide-crosstab-columns-event";
import { PagingControlModel } from "../../model/paging-control-model";
import { PagingControlService } from "../../../common/services/paging-control.service";
import { VSTabService } from "../../util/vs-tab.service";

const CROSSTAB_ACTION_DRILL = "/events/crosstab/action/drill";
const CROSSTAB_DRILL_CELLS_URI = "/events/table/drill/cells";
const CROSSTAB_DRILL_URI = "/events/table/drill";
const GROUP_URI = "composer/viewsheet/groupFields";
const DATE_COMPARISON_DIALOG_URI: string = "composer/vs/date-comparison-dialog-model";
const DATE_COMPARISON_CLEAR_URI: string = "composer/vs/date-comparison-dialog-model/clear";
const SCRIPT_TREE_URL: string = "../api/vsscriptable/scriptTree";

@Component({
   selector: "vs-crosstab",
   templateUrl: "vs-crosstab.component.html",
   styleUrls: ["base-table.scss", "vs-crosstab.component.scss"],
   changeDetection: ChangeDetectionStrategy.OnPush
})
export class VSCrosstab extends BaseTable<VSCrosstabModel> implements OnInit, OnChanges, OnDestroy {
   @Input() set model(model: VSCrosstabModel) {
      this._model = model;
      this.updateLayout(false);
   }

   get model(): VSCrosstabModel {
      return this._model;
   }

   @Output() onOpenFormatPane = new EventEmitter<VSCrosstabModel>();

   @ViewChild("tableContainer") tableContainer: ElementRef;

   // Scroll Wrappers
   @ViewChild("verticalScrollWrapper") verticalScrollWrapper: ElementRef;
   @ViewChild("horizontalScrollWrapper") horizontalScrollWrapper: ElementRef;

   @ViewChild("verticalScrollTooltip") verticalScrollTooltip: NgbTooltip;
   @ViewChild("horizontalScrollTooltip") horizontalScrollTooltip: NgbTooltip;

   @ViewChild("colResize") colResize0: ElementRef;
   @ViewChild("rowResize") rowResize0: ElementRef;
   @ViewChild("resizeLine") resizeLine0: ElementRef;

   @ViewChild("lowerTable") lowerTable: ElementRef;
   @ViewChild("leftTopDiv") leftTopDiv: ElementRef;
   @ViewChild("leftBottomTable") leftBottomTable: ElementRef;
   @ViewChild("rightTopTable") rightTopTable: ElementRef;

   get colResize(): ElementRef {
      return this.colResize0;
   }

   get rowResize(): ElementRef {
      return this.rowResize0;
   }

   get resizeLine(): ElementRef {
      return this.resizeLine0;
   }

   // Table Data
   public rtTable: BaseTableCellModel[][] = [];
   public lbTable: BaseTableCellModel[][] = [];
   public ltTable: BaseTableCellModel[][] = [];
   public table: BaseTableCellModel[][];

   public rbTableWidth: number = 0;
   public rtTableHeight: number = 0;
   public initialCellContent: string;

   // Paged Data Variables
   public topRowHeight: number;

   private keepScroll: boolean = false;

   @Input()
   set actions(value: CrosstabActions) {
      if(this.actionSubscription) {
         this.actionSubscription.unsubscribe();
         this.actionSubscription = null;
      }

      this._actions = value;

      if(value) {
         this.actionSubscription = value.onAssemblyActionEvent.subscribe((event) => {
            switch(event.id) {
            case "crosstab export":
               this.exportTable();
               break;
            case "crosstab multi-select":
               this.model.multiSelect = !this.model.multiSelect;
               break;
            case "crosstab show-details":
               this.showDetails();
               break;
            case "crosstab annotate":
               this.showAnnotateDialog(event.event);
               break;
            case "crosstab filter":
               this.addFilter(
                  event.event, this.tableContainer.nativeElement.getBoundingClientRect());
               break;
            case "crosstab edit":
               if(this.openWizardEnabled()) {
                  this.openWizardPane(VsWizardEditModes.VIEWSHEET_PANE);
               }
               else {
                  this.openEditPane();
               }
               break;
            case "crosstab highlight":
               this.openHighlightDialog();
               break;
            case "crosstab conditions":
               this.openConditionDialog();
               break;
            case "crosstab sort-column":
               this.sortClicked(false);
               break;
            case "crosstab sort-column-aggregate":
               this.sortClicked(true);
               break;
            case "crosstab group":
               this.showGroupDialog();
               break;
            case "crosstab rename":
               this.showRenameDialog();
               break;
            case "crosstab ungroup":
               this.ungroupCrosstab();
               break;
            case "crosstab open-max-mode":
               this.openMaxMode();
               break;
            case "crosstab close-max-mode":
               this.closeMaxMode();
               break;
            case "expand all":
               this.drillHandle(this.model);
               break;
            case "collapse all":
               this.drillHandle(this.model, true);
               break;
            case "expand field":
               this.drillHandle(this.model, false, true);
               break;
            case "collapse field":
               this.drillHandle(this.model, true, true);
               break;
            case "crosstab drilldown":
               this.drillAction();
               break;
            case "crosstab drillup":
               this.drillAction(true);
               break;
            case "menu actions":
               VSUtil.showDropdownMenus(event.event, this.actions.menuActions, this.dropdownService);
               break;
            case "table cell size":
               this.openCellSizeDialog(this.ngbModal);
               break;
            case "crosstab date-comparison":
               this.showCrosstabDateComparisonDialog(this.model);
               break;
            case "crosstab hide column":
               this.hideColumn(<VSCrosstabModel>event.model);
               break;
            case "crosstab show columns":
               this.showColumns(<VSCrosstabModel>event.model);
               break;
            case "crosstab show-format-pane":
               this.onOpenFormatPane.emit(this.model);
               break;
            case "more actions":
               VSUtil.showDropdownMenus(event.event, this.actions.getMoreActions(),
                  this.dropdownService, []);
               break;
            }
         });
      }
   }

   get actions(): CrosstabActions {
      return <CrosstabActions> this._actions;
   }

   @Output() onEditTable: EventEmitter<VSCrosstabModel> = new EventEmitter<VSCrosstabModel>();
   crosstabActionHandler: CrosstabActionHandler;

   constructor(viewsheetClient: ViewsheetClientService,
               dropdownService: FixedDropdownService,
               downloadService: DownloadService,
               private ngbModal: NgbModal,
               private dialogService: DialogService,
               private modelService: ModelService,
               renderer: Renderer2,
               protected changeDetectorRef: ChangeDetectorRef,
               protected dataTipService: DataTipService,
               protected popComponentService: PopComponentService,
               protected pagingControlService: PagingControlService,
               contextProvider: ContextProvider,
               formDataService: CheckFormDataService,
               debounceService: DebounceService,
               scaleService: ScaleService,
               hyperlinkService: ShowHyperlinkService,
               private dndService: DndService,
               http: HttpClient,
               zone: NgZone,
               protected tabService: VSTabService,
               @Optional() viewerResizeService: ViewerResizeService,
               private richTextService: RichTextService)
   {
      super(viewsheetClient, dropdownService, downloadService, renderer, changeDetectorRef,
            contextProvider, formDataService, debounceService, scaleService, hyperlinkService,
            http, dataTipService, popComponentService, pagingControlService,
            zone, tabService, viewerResizeService);
      this.crosstabActionHandler =
         new CrosstabActionHandler(modelService, viewsheetClient, dialogService, contextProvider);
   }

   protected updateTableHeight(): void {
      this.tableHeight = this.model.objectFormat.height - this.getHeaderHeight() -
         this.model.titleFormat.height;
   }

   protected updateLayout(loadOnDemand: boolean): void {
      this.setRBTableWidth();
      this.setRTTableHeight();
      this.updateTableHeight();
      this.updateVisibleRows(loadOnDemand);
      this.updateVisibleCols();

      if(this.model.dataAnnotationModels) {
         this.model.leftTopAnnotations = this.model.dataAnnotationModels.filter((annotation) => {
            return this.isInLeftTopTable(annotation.row, annotation.col);
         });

         this.model.leftBottomAnnotations = this.model.dataAnnotationModels.filter((annotation) => {
            return this.isInLeftBottomTable(annotation.row, annotation.col);
         });

         this.model.rightTopAnnotations = this.model.dataAnnotationModels.filter((annotation) => {
            return this.isInRightTopTable(annotation.row, annotation.col);
         });

         this.model.rightBottomAnnotations = this.model.dataAnnotationModels.filter((annotation) => {
            return this.isInRightBottomTable(annotation.row, annotation.col);
         });

         this.positionDataAnnotations();
      }

      if(this.viewer && this.model.shrink) {
         this.sumColWidths();
      }

      this.resetRowResize();
   }

   /**
    * Set the base table model, calculate the rbTableWidth for the horizontal scrollbar,
    * and calculate the visible rows for reloading data
    */
   ngOnInit(): void {
      super.ngOnInit();

      this.updateTableHeight();
      this.setRBTableWidth();
      this.setRTTableHeight();
      this.clearSelection();
   }

   ngOnChanges(changes: SimpleChanges): void {
      super.ngOnChanges(changes);

      if(changes != null && changes.hasOwnProperty("modelTS")) {
         this.checkScroll();
      }
   }

   ngOnDestroy(): void {
      super.ngOnDestroy();

      if(this.actionSubscription) {
         this.actionSubscription.unsubscribe();
         this.actionSubscription = null;
      }
   }

   get showDCIcon(): boolean {
      return !this.dataTipService.isDataTipVisible(this.getAssemblyName()) && !this.mobileDevice
         && !this.contextProvider.vsWizardPreview && !!this.model.dateComparisonDescription;
   }

   public get isFullHorizontalWrapper(): boolean {
      if(this.model) {
         return this.rbTableWidth < (this.getObjectWidth() / 2);
      }

      return false;
   }

   getTranslateY(): number {
      return !this.loadedRows || this.loadedRows.start == 0 ? -this.scrollY - this.rtTableHeight
         : -this.scrollY + this.topRowHeight;
   }

   public get rbTableX(): number {
      return this.isFullHorizontalWrapper ?
         -(this.getObjectWidth() - this.rbTableWidth) :
         -this.scrollX - (this.getObjectWidth() - this.rbTableWidth);
   }

   public getObjectHeight(): number {
      let h  = super.getObjectHeight();

      if(this.model.shrink && this.viewer) {
         let titleH = this.model.titleVisible ? 0 : this.model.titleFormat.height;

         return h - titleH;
      }

      return h;
   }

   /**
    * Called by name from CommandProcessor
    * @param {LoadTableDataCommand} command
    */
   protected loadTableData(command: LoadTableDataCommand): void {
      this.loadedRows = {
         start: command.start,
         end: command.end
      };

      this.loadHeaderRows(command);
      this.refreshHeaderSelection(command);

      let numRows = command.end - command.start;
      let cells: BaseTableCellModel[];
      this.lbTable.length = 0;

      for(let i = 0; i < numRows; i++) {
         const row = command.tableCells[i];

         if(row != null) {
            if(this.model.hasHiddenColumn) {
               for(let j = 0; j < this.model.headerColCount; j++) {
                  const width = this.displayColWidths[j];
                  const cell = row[j];

                  if(width === 0 && cell.colSpan === 1) {
                     cell.spanCell = true;
                  }
               }
            }

            cells = Tool.clone(row.slice(0, this.model.headerColCount));
            this.lbTable[i] = cells;
         }
      }

      this.table = Tool.clone(command.tableCells);
      this.model.cells = this.table;
      this.refreshSelectedCells();
      this.model.tableHeaderCells = this.ltTable;
      this.topRowHeight = this.getRowPosition(command.start - this.model.headerRowCount);
      this.setRBTableWidth();
      this.setRTTableHeight();
      this.model.runtimeRowHeaderCount = command.runtimeRowHeaderCount;
      this.model.runtimeColHeaderCount = command.runtimeColHeaderCount;
      this.updateVisibleRows(false);

      if(command.start == 0 && this.lastVisibleRow > command.end) {
         this.scrollY = 0;
         this.verticalScrollWrapper.nativeElement.scrollTop = 0;
      }

      this.keepScroll = false;
      this.checkScroll();
   }

   private refreshSelectedCells(): void {
      if(!this.model || !this.model.cells) {
         this.model.firstSelectedRow = null;
         this.model.firstSelectedColumn = null;
      }

      if(this.model.firstSelectedRow >= this.model.cells.length) {
         this.model.firstSelectedRow = null;
      }

      if(this.model.cells.length > 0 &&
         this.model.firstSelectedColumn >= this.model.cells[0].length)
      {
         this.model.firstSelectedColumn = null;
      }
   }

   public getRowPosition(row: number): number {
      if(!this.model.wrapped) {
         if(row < this.model.headerRowCount) {
            let sum: number = 0;

            for(let i = 0; i < row; i++) {
               sum += this.model.headerRowHeights[i];
            }

            return sum;
         }
         else {
            return this.model.dataRowHeight * (row - 1);
         }
      }

      if(row < this.model.headerRowCount) {
         return this.model.headerRowPositions[row];
      }

      const dataRow: number = row - this.model.headerRowCount;
      return this.model.dataRowPositions[dataRow];
   }

   private loadHeaderRows(command: LoadTableDataCommand): void {
      let headerCells: BaseTableCellModel[][];

      if(command.start === 0) {
         headerCells = command.tableCells;
      }
      else if(command.tableHeaderCells != null) {
         headerCells = command.tableHeaderCells;
      }

      if(headerCells == null) {
         return;
      }

      this.ltTable.length = 0;

      for(let i = 0; i < this.model.headerRowCount; i++) {
         if(command.tableCells[i] != null) {
            this.ltTable[i] = Tool.clone(headerCells[i].slice(0, this.model.headerColCount));
         }
      }

      this.rtTable.length = 0;

      for(let i = 0; i < this.model.headerRowCount; i++) {
         if(command.tableCells[i] != null) {
            this.rtTable[i] = Tool.clone(headerCells[i]);
         }
      }
   }

   private refreshHeaderSelection(command: LoadTableDataCommand): void {
      const selectedHeaders = this.model.selectedHeaders;

      if(selectedHeaders != null) {
         const keyIterator = selectedHeaders.keys();

         for(let selectedRow of keyIterator) {
            if(selectedRow >= command.rowCount) {
               selectedHeaders.delete(selectedRow);
            }
         }
      }
   }

   /**
    *  Calculates the wheel scroll value and changes scroll bar deltaX and deltaY
    *  respectively.
    * @param event
    */
   public wheelScrollHandler(event: any): void {
      if(event.shiftKey) {
         this.horizontalScrollWrapper.nativeElement.scrollLeft += event.deltaY;
      }
      else {
         this.verticalScrollWrapper.nativeElement.scrollTop += event.deltaY;
         this.horizontalScrollWrapper.nativeElement.scrollLeft += event.deltaX;
      }

      event.preventDefault();
   }

   drillClicked(row: number, col: number, cell: BaseTableCellModel, direction: string): void {
      let event = DrillEvent.builder(this.getAssemblyName())
         .row(row)
         .col(col)
         .drillOp(cell.drillOp)
         .direction(direction)
         .field(cell.field)
         .build();

      this.keepScroll = true;
      this.viewsheetClient.sendEvent(CROSSTAB_DRILL_URI, event);
   }

   public linkClicked(payload: any): void {
      this.openClickDropdownMenu(payload);
   }

   sortClicked(multi: boolean): void {
      let event = SortColumnEvent.builder(this.getAssemblyName())
         .row(this.model.sortInfo.row)
         .col(this.model.sortInfo.col)
         .colName(this.model.sortInfo.field)
         .multi(multi)
         .build();
      this.viewsheetClient.sendEvent("/events/crosstab/sort-column", event);
   }

   sortColumn(cell: BaseTableCellModel, evt: MouseEvent): void {
      evt.stopPropagation();

      let event = SortColumnEvent.builder(this.getAssemblyName())
         .row(cell.row)
         .col(cell.col)
         .colName(cell.field)
         .multi(false)
         .build();
      this.viewsheetClient.sendEvent("/events/crosstab/sort-column", event);
   }

   isSorted(field: string, type: string): boolean {
      const sortValue = this.model.sortTypeMap[field];

      switch(sortValue) {
      case XConstants.SORT_ASC:
         return type == "asc";
      case XConstants.SORT_DESC:
         return type == "desc";
      case XConstants.SORT_VALUE_ASC:
         return type == "val-asc";
      case XConstants.SORT_VALUE_DESC:
         return type == "val-desc";
      default:
         return type == "none";
      }
   }

   isColSorted(cell: BaseTableCellModel): boolean {
      return this.model.sortTypeMap[cell.field] != XConstants.SORT_NONE;
   }

   getColumnWidthSum(from: number, to: number): number {
      return BaseTable.getSelectionWidth(this.model.colWidths, from, to);
   }

   public selectCell(event: MouseEvent,
                     cell: BaseTableCellModel,
                     header: boolean = false,
                     rowIndex: number = 0,
                     colIndex: number = 0,
                     sortable: boolean = false): void
   {
      this.model.metadata = this.vsInfo.metadata;
      this.initDropdownTooltipProperty();

      // select vsobject before select parts
      if(!this.selected && !this.vsInfo.formatPainterMode && !this.viewer) {
         return;
      }

      // if middle click then just return
      if(event.button === 1) {
         return;
      }

      if(this.viewer && !this.model.enabled) {
         return;
      }

      const sortValue: number = this.model.sortTypeMap[cell.field];

      if(sortable && sortValue != null) {
         this.model.sortInfo = {
            row: rowIndex,
            col: colIndex,
            field: cell.field,
            sortValue: sortValue,
            sortable: true
         };
      }
      else {
         this.model.sortInfo = {
            sortable: false
         };
      }

      this.initialCellContent = cell.cellData as string;
      let row = cell.row;
      let column = cell.col;
      let columnMap: Map<number, number[]> =
         (header ? this.model.selectedHeaders : this.model.selectedData) || new Map();
      const omap: Map<number, number[]> = new Map(columnMap);

      if(header) {
         this.model.selectedHeaders = columnMap;
      }
      else {
         this.model.selectedData = columnMap;
      }

      // When right clicking on a selected cell, don't deselect anything.
      if(!(event.button === 2 && this.isSelected(cell, header))) {
         if(event.ctrlKey || this.model.multiSelect) {
            // When ctl clicking or multi-selecting on mobile on a selected cell,
            // deselect the cell instead
            if(this.isSelected(cell, header)) {
               this.deselectCell(row, column, columnMap);
            }
            else {
               let selectedColumns = columnMap.get(row) || [];
               columnMap.set(row, selectedColumns.concat(column));

               if(this.model.firstSelectedColumn == null || this.model.firstSelectedColumn == -1) {
                  this.model.firstSelectedRow = row;
                  this.model.firstSelectedColumn = column;
               }

               this.addDataPath(cell.dataPath);
            }

            event.stopPropagation();
         }
         else if(event.shiftKey && this.model.lastSelected) {
            let firstRow: number = 0;
            let lastRow: number = 0;

            if(row > this.model.lastSelected.row) {
               firstRow = this.model.lastSelected.row;
               lastRow = row;
            }
            else {
               firstRow = row;
               lastRow = this.model.lastSelected.row;
            }

            let firstCol: number = 0;
            let lastCol: number = 0;

            if(column > this.model.lastSelected.column) {
               firstCol = this.model.lastSelected.column;
               lastCol = column;
            }
            else {
               firstCol = column;
               lastCol = this.model.lastSelected.column;
            }

            let lengthRow: number = lastRow - firstRow;
            let lengthColumn: number = lastCol - firstCol;
            let selectedHeaders: Map<number, number[]> = this.model.selectedHeaders || new Map();
            let selectedData: Map<number, number[]> = this.model.selectedData || new Map();

            this.model.selectedHeaders = selectedHeaders;
            this.model.selectedData = selectedData;

            for(let i = 0; i <= lengthRow; i++) {
               for(let j = 0; j <= lengthColumn; j++) {
                  const header0 = firstRow + i < this.model.headerRowCount ||
                                  firstCol + j < this.model.headerColCount;
                  const map0 = header0 ? selectedHeaders : selectedData;
                  let currentRow = map0.get(firstRow + i) || [];

                  if(currentRow.indexOf(firstCol + j) < 0 &&
                     !!this.table[firstRow + i - this.loadedRows.start][firstCol + j].dataPath)
                  {
                     map0.set(firstRow + i, currentRow.concat(firstCol + j));
                     this.addDataPath(this.table[firstRow + i - this.loadedRows.start][firstCol + j]
                        .dataPath);
                  }

                  // Start row is the relative row while cell contains absolute row
                  let relativeRow = firstRow - this.loadedRows.start;
                  let tableRow = this.table[relativeRow + i];

                  if(row) {
                     this.addDataPath(tableRow[firstCol + j].dataPath);
                  }
               }
            }
         }
         else {
            // Regular click, clear any other selections
            this.clearSelection();
            // eslint-disable-next-line @typescript-eslint/no-shadow
            const nmap = new Map();

            if(header) {
               this.model.selectedHeaders = nmap;
            }
            else {
               this.model.selectedData = nmap;
            }

            nmap.set(row, [column]);

            this.model.firstSelectedRow = row;
            this.model.firstSelectedColumn = column;
            this.addDataPath(cell.dataPath);
         }
      }

      if(!header) {
         if(this.model.hasFlyover) {
            // Set flyover cell selected flag so mouseenter no longer trigger flyovers
            // until the selection is cleared
            this.flyoverCellSelected = true;
            this.sendFlyover(this.model.selectedData);
         }
      }
      else {
         if(this.model.hasFlyover) {
            this.flyoverCellSelected = true;
            this.sendFlyover(this.model.selectedHeaders);
         }
      }

      const nmap: Map<number, number[]> = header ? this.model.selectedHeaders
         : this.model.selectedData;

      // if table in container, the select sequence is: table -> cell -> container
      if(!Tool.isEquals(nmap, omap)) {
         if(!this.context.binding && event.button == 0) {
            (<any> event).ignoreClick = true;
         }

         this.onRefreshFormat.emit(event);
      }

      this.model.lastSelected = {row: row, column: column};
      this.changeDetectorRef.detectChanges();

      if(this.dialogService.hasSlideout(this.getAssemblyName() + "_showDetails")) {
         this.showDetails();
      }

      if(this.model.dataTip && !this.dataTipService.isDataTipVisible(this.getAssemblyName())) {
         this.dataTipService.freeze();
      }
   }

   public isSelected(cell: BaseTableCellModel, header: boolean = false): boolean {
      const row = cell.row;
      const column = cell.col;
      const columnMap = header ? this.model.selectedHeaders : this.model.selectedData;
      return columnMap && super.isColumnSelected(columnMap.get(row), column);
   }

   public isHyperlink(cell: BaseTableCellModel): boolean {
      return cell.hyperlinks != null && cell.hyperlinks.length > 0;
   }

   selectTitle(event: MouseEvent): void {
      // select vsobject before select parts
      if(!this.selected && !this.vsInfo.formatPainterMode) {
         return;
      }

      if(!event.shiftKey && !event.ctrlKey) {
         this.model.selectedRegions = [];
         this.model.selectedHeaders = null;
         this.model.selectedData = null;
         this.clearFlyover(true);

         if(!this.dataTipService.isDataTipVisible(this.getAssemblyName())) {
            this.dataTipService.unfreeze();
         }
      }

      if(this.viewer) {
         this.clearSelection();

         return;
      }

      this.model.titleSelected = true;
      this.addDataPath(DataPathConstants.TITLE);
   }

   setRBTableWidth(): void {
      let columnWidths = this.model.maxMode ? this.displayColWidths : this.model.colWidths;
      this.rbTableWidth = Math.max(this.getObjectWidth() -
         columnWidths.slice(0, this.model.headerColCount).reduce((a, b) => a + b, 0), 0);
   }

   getLBTableWidth(): number {
      let columnWidths = this.model.maxMode ? this.displayColWidths : this.model.colWidths;
      // add one pixel to allow the right border line to show, otherwise it will be missing
      // when scroll to the right
      const rightBorder = 1;

      return columnWidths.slice(0, this.model.headerColCount).reduce((a, b) => a + b, rightBorder);
   }

   setRTTableHeight(): void {
      this.rtTableHeight = 0;

      for(let i = 0; i < this.rtTable.length; i++) {
         this.rtTableHeight += this.getRowHeight(i);
      }
   }

   getToolbarActions(): AssemblyActionGroup[] {
      return this.actions.toolbarActions;
   }

   /** @inheritDoc */
   protected positionDataAnnotations(): boolean {
      const lt = this.positionAnnotationsToCell(this.model.leftTopAnnotations, 0, 0);
      const lb = this.positionAnnotationsToCell(this.model.leftBottomAnnotations, this.scrollY,
         this.isFullHorizontalWrapper ? this.scrollX : 0);
      const rt = this.positionAnnotationsToCell(this.model.rightTopAnnotations, 0, this.scrollX);
      const rb = this.positionAnnotationsToCell(this.model.rightBottomAnnotations, this.scrollY,
                                                this.scrollX);

      return lt || lb || rt || rb;
   }

   private showAnnotateDialog(event: MouseEvent): void {
      const selectedCell = this.getSelectedCell(event);

      this.richTextService.showAnnotationDialog((content) => {
         if(selectedCell) {
            this.addCellAnnotation(content, selectedCell);
         }
         else {
            this.addAssemblyAnnotation(content, event);
         }
      }).subscribe(dialog => {
         // If we selected aggregates then create a tooltip
         if(selectedCell && !selectedCell.isHeader) {
            dialog.initialContent = this.getAnnotationMarkup(
               this.getCellTooltip(selectedCell.cell, true));
         }
      });
   }

   getCellTooltipFunc(cell: BaseTableCellModel): () => string {
      return () => this.getCellTooltip(cell, false);
   }

   /**
    * Get the tooltip for a cell
    *
    * @param {BaseTableCellModel} cell the cell used to create the tooltip
    * @returns {string} the cell tooltip string
    */
   private getCellTooltip(cell: BaseTableCellModel, isAnnotation: boolean = false): string {
      let tip: string = null;

      if(cell.hyperlinks && cell.hyperlinks.length > 0) {
         tip = "";

         for(let i = 0; i < cell.hyperlinks.length; i++) {
            let tipItem = cell.hyperlinks[i].tooltip;

            if(tipItem) {
               tip = tip.length != 0 ? (tip + "/" + tipItem) : tipItem;
            }
         }

         if(tip.length != 0) {
            return tip;
         }
      }

      if(cell && (isAnnotation || !this.dataTipService.isDataTipVisible(this.model.dataTip))) {
         const headerCount = this.model.runtimeRowHeaderCount + this.model.runtimeColHeaderCount;
         tip = "";

         for(let i = 0; i < headerCount; i++) {
            if(i < this.model.runtimeRowHeaderCount) {
               if(this.model?.colWidths?.length > i && this.model.colWidths[i] == 0) {
                  continue;
               }

               tip += this.model.rowNames[i] + ": ";
               const rowHeaderCell =
                  this.getCell(this.model.cells, cell.row - this.loadedRows.start, i, false);
               tip += rowHeaderCell ? rowHeaderCell.cellLabel : "";
            }
            else {
               if(this.model?.colWidths?.length > cell.col && this.model.colWidths[cell.col] == 0) {
                  continue;
               }

               tip += this.model.colNames[i - this.model.runtimeRowHeaderCount] + ": ";
               // if scrolled, the model.cells may not have the header cells, so use rtTable.
               let cells = this.rtTable ? this.rtTable : this.model.cells;
               const colHeaderCell = this.getCell(this.model.cells,
                                                  i - this.model.runtimeRowHeaderCount,
                                                  cell.col, true);
               tip += colHeaderCell ? colHeaderCell.cellLabel : "";
            }

            tip += "\n";
         }

         tip += cell.field + ": " + (cell.cellLabel != null ? cell.cellLabel : "") + "\n";
      }

      return tip;
   }

   private addCellAnnotation(content: string, cell: VSTableCell): void {
      const selectedCellBounds = cell.boundingClientRect;
      const cellX = selectedCellBounds.left + selectedCellBounds.width / 2;
      const cellY = selectedCellBounds.top + selectedCellBounds.height / 2;
      const tableBounds = this.tableContainer.nativeElement.getBoundingClientRect();
      const x = (cellX - tableBounds.left + this.scrollX) / this.scale;
      const y = (cellY - tableBounds.top + this.scrollY) / this.scale;
      const annotateEvent = new AddAnnotationEvent(content, x, y, this.getAssemblyName());
      annotateEvent.setRow(cell.cell.row);
      annotateEvent.setCol(cell.cell.col);
      this.viewsheetClient.sendEvent("/events/annotation/add-data-annotation", annotateEvent);
   }

   private addAssemblyAnnotation(content: string, event: MouseEvent): void {
      const tableBounds = this.tableContainer.nativeElement.getBoundingClientRect();
      const x = (event.clientX - tableBounds.left) / this.scale;
      const y = (event.clientY - tableBounds.top) / this.scale;
      const annotateEvent = new AddAnnotationEvent(content, x, y, this.getAssemblyName());
      this.viewsheetClient.sendEvent("/events/annotation/add-assembly-annotation", annotateEvent);
   }

   private ungroupCrosstab(): void {
      if(this.model.firstSelectedRow == -1 || this.model.firstSelectedColumn == -1) {
         return;
      }

      const row: number = this.model.firstSelectedRow;
      const col: number = this.model.firstSelectedColumn;
      const labels: string[] = [
         row + "," + col
      ];

      const event: GroupFieldsEvent =
         new GroupFieldsEvent(this.model.absoluteName, row, col, null, labels, null);

      this.viewsheetClient.sendEvent("/events/" + GROUP_URI, event);
      this.clearSelection();
   }

   private showGroupDialog(): void {
      this.showInputNameDialog(false);
   }

   private showRenameDialog(): void {
      this.showInputNameDialog(true);
   }

   private showInputNameDialog(rename: boolean): void {
      let table: VSCrosstabModel = this.model;
      const row = this.model.firstSelectedRow;
      const col = this.model.firstSelectedColumn;
      const cells: string[] = [];
      let prevName: string = null;
      const columnMap = this.model.selectedHeaders;

      if(columnMap) {
         columnMap.forEach((cols, actualRow) => {
            for(let j = 0; j < cols.length; j++) {
               const relativeRow = actualRow - this.loadedRows.start;
               const c = cols[j];
               const cell = table.cells[relativeRow][c];

               if(cell.grouped) {
                  prevName = <string> cell.cellData;
               }

               cells.push(actualRow + "," + c);
            }
         });
      }

      let dialogName: string;

      if(rename) {
         dialogName = <string> this.model.cells[row][col].cellData;
      }
      else if(prevName) {
         dialogName = prevName;
      }

      const dialog = ComponentTool.showDialog(this.ngbModal, InputNameDialog,
         (result: string) => {
            const event = new GroupFieldsEvent(
               this.model.absoluteName, row, col, null, cells, result);

            if(rename) {
               if(dialogName == result.trim()) {
                  return;
               }

               event.prevGroupName = prevName;
            }

            this.viewsheetClient.sendEvent("/events/" + GROUP_URI, event);
            this.clearSelection();
         });

      dialog.title = "_#(js:Group Name)";
      dialog.label = "_#(js:Group Name)";
      dialog.value = dialogName;
      dialog.helpLinkKey = "AddCrosstabCreateNamedGroups";
      dialog.validators = [
         Validators.required
      ];
      dialog.validatorMessages = [
         {validatorName: "required", message: "_#(js:vs.group.nameRequired)"},
         {validatorName: "pattern", message: "_#(js:vs.group.nameValid)"},
      ];
      dialog.hasDuplicateCheck = (value: string) => {
         const event = new GroupFieldsEvent(
            this.model.absoluteName, row, col, null, cells, value.trim());
         return this.modelService.sendModel<boolean>("../api/" + GROUP_URI + "/checkDuplicates/"
            + Tool.encodeURIPath(this.viewsheetClient.runtimeId), event).pipe(
            map((res) => res.body));
      };
   }

   /**
    * Get the first cell in a row span or a col span which has the prototypal information
    * @param colHeader true if get column header cell, else get row header.
    */
   private getCell(cells: BaseTableCellModel[][], row: number, col: number, colHeader: boolean): BaseTableCellModel {
      const cell = cells[row] ? cells[row][col] : null;

      if(cell) {
         if(!cell.spanCell || (this.displayColWidths[col] === 0 && cell.colSpan === 1)) {
            return cell;
         }
      }

      if(row < 0 || col < 0) {
         return null;
      }

      let isRowSpan = !colHeader ;
      let aggrCount = this.model?.aggrNames ? this.model.aggrNames.length : 0;
      let colHeaderCount = Math.max(1, this.model.runtimeColHeaderCount);

      // for column total and grand total
      if (colHeader) {
         if(cell.totalCol) {
            let leftColInSpan = (cell.col - this.model.runtimeRowHeaderCount) % aggrCount == 0;

            if(leftColInSpan) {
               if(cell.spanCell && cell.rowSpan > 1 || cell.row == colHeaderCount - 1) {
                  isRowSpan = true;
               }
            }
         }

         if (!this.model.summarySideBySide && cell.grandTotalCol) {
            isRowSpan = true;
         }
      }

      // for row total and grand  total
      if (!colHeader) {
         if(!this.model.summarySideBySide && aggrCount != 0) {
            if(cell.totalRow) {
               let topRowInSpan = (cell.row - colHeaderCount) % aggrCount == 0;

               if(topRowInSpan) {
                  // for total and grand total
                  if(cell.colSpan > 1 || cell.col == this.model.runtimeRowHeaderCount - 1) {
                     isRowSpan = false;
                  }
               }
            }
         }

         if(this.model.summarySideBySide && cell.grandTotalRow) {
            isRowSpan = false;
         }
      }

      if(row == 0 && cell.spanCell) {
         isRowSpan = false;
      }

      if(isRowSpan) {
         return this.getCell(cells, row - 1, col, colHeader);
      }
      else {
         return this.getCell(cells, row, col - 1, colHeader);
      }
   }

   /** @inheritDoc */
   protected handleColResizeMove(evt: MouseEvent | TouchEvent, cell: BaseTableCellModel): void {
      super.handleColResizeMove(evt, cell);
      this.setRBTableWidth();
      this.setRTTableHeight();
   }

   public isDraggable(header: boolean): boolean {
      return (header || !this.viewer) && this.initialMousePos == -1 && this.resizeCol == -1;
   }

   public dragStart(event: any, cell: BaseTableCellModel): void {
      if(cell == null || cell.dataPath == null ||
         cell.dataPath.type != TableDataPathTypes.HEADER &&
         cell.dataPath.type != TableDataPathTypes.GROUP_HEADER &&
         cell.dataPath.type != TableDataPathTypes.DETAIL &&
         cell.dataPath.type != TableDataPathTypes.SUMMARY)
      {
         return;
      }

      let fieldName: string = cell.field ? cell.field : <string> cell.cellData;

      if(fieldName == null) {
         return;
      }

      let idx: number = -1;
      let dragType: string = null;

      if(this.model.rowNames.indexOf(fieldName) != -1) {
         idx = this.model.rowNames.indexOf(fieldName);
         dragType = "rows";
      }
      else if(this.model.colNames.indexOf(fieldName) != -1) {
         idx = this.model.colNames.indexOf(fieldName);
         dragType = "cols";
      }
      else if(this.model.aggrNames.indexOf(fieldName) != -1) {
         idx = this.model.aggrNames.indexOf(fieldName);
         dragType = "aggregates";
      }

      this.dndService.setDragStartStyle(event, fieldName);
      this.dndService.dragTableSource = this.model.absoluteName;
      let transfer: TableTransfer = new TableTransfer(dragType, idx,
         this.getAssemblyName());
      transfer.objectType = "vscrosstab";

      Tool.setTransferData(event.dataTransfer,
         {
            dragName: ["tableBinding"],
            dragSource: transfer
         });
   }

   protected handleRowResizeMove(evt: MouseEvent, row: number): void {
      super.handleRowResizeMove(evt, row);
      this.setRTTableHeight();
   }

   /**
    * The cell width accounting for cell span.
    */
   public getCellWidth(cell: BaseTableCellModel, col: number): number {
      if(this.vsWizardPreview || cell.cellWidth == null) {
         cell.cellWidth = 0;

         for(let i = 0; i < cell.colSpan; i++) {
            cell.cellWidth += this.displayColWidths[col + i];
         }
      }

      return cell.cellWidth;
   }

   public getCellColSpan(cell: BaseTableCellModel, col: number): number | null {
      if(cell.displayColSpan === undefined) {
         cell.displayColSpan = cell.colSpan;

         if(cell.displayColSpan != null) {
            for(let i = 0; i < cell.colSpan; i++) {
               if(this.displayColWidths[col + i] === 0) {
                  cell.displayColSpan--;
               }
            }
         }
         else {
            cell.displayColSpan = null;
         }
      }

      return cell.displayColSpan;
   }

   public isShowCol(cell: BaseTableCellModel, col: number): boolean {
      if(cell.cellWidth == null) {
         cell.cellWidth = 0;

         for(let i = 0; i < cell.colSpan; i++) {
            cell.cellWidth += this.displayColWidths[col + i];
         }
      }

      return cell.cellWidth > 0;
   }

   protected checkScroll(): void {
      // optimization, set style immediately and delay the change detection.
      // styles should match the styles set in html
      let trans = "translate(" + this.rbTableX + "px," +
         this.getDetailTableTopPosition() + "px)";

      if(this.lowerTable != null) {
         this.renderer.setStyle(this.lowerTable.nativeElement, "transform", trans);
      }

      if(this.isFullHorizontalWrapper && this.leftTopDiv != null) {
         this.renderer.setStyle(this.leftTopDiv.nativeElement, "left", -this.scrollX);
      }

      trans = "translate(0px," +
         this.getDetailTableTopPosition() + "px)";

      if(this.leftBottomTable != null) {
         this.renderer.setStyle(this.leftBottomTable.nativeElement, "transform", trans);
      }

      trans = "translate(" + this.rbTableX + "px,0px)";

      if(this.rightTopTable != null) {
         this.renderer.setStyle(this.rightTopTable.nativeElement, "transform", trans);
      }
   }

   isAggregate(field: string): boolean {
      return this.model.aggrNames.some((name) => name === field);
   }

   sortEnable(cell: BaseTableCellModel): boolean {
      return cell.field && this.sortColumnVisible && !cell.hasCalc && !cell.period &&
         this.model.timeSeriesNames.indexOf(cell.field) < 0;
   }

   sortDimensionEnable(cell: BaseTableCellModel): boolean {
      return this.sortEnable(cell) &&
         (this.model.sortOnHeader || (!this.isAggregate(cell.field) && this.model.sortDimension));
   }

   rename(cell: BaseTableCellModel): void {
      if(this.initialCellContent !== cell.cellData) {
         this.changeCellTitle(cell);
      }
   }

   public resized(): void {
      super.resized();
      this.setRBTableWidth();
   }

   private drillHandle(model: VSCrosstabModel, isCollapse: boolean = false,
                       drillField: boolean = false): void
   {
      if(!!!model.cells || model.cells.length <= 0) {
         return;
      }

      // row is not start with 0 after loading data
      const startRow = this.model.cells[0][0].row;
      const row: number = +model.firstSelectedRow - startRow;
      const col: number = +model.firstSelectedColumn;
      const absoluteName = model.absoluteName;
      let drillEvent: DrillEvent;
      let drillCells: BaseTableCellModel[] = [];
      let direction: string;
      const events = [];
      let drillTarget = DrillTarget.NONE;
      let field: string = null;

      if(drillField) {
         drillTarget = DrillTarget.FIELD;
         field = model.cells[row][col].field;
      }
      else if(row < 0 || col < 0 || model.titleSelected) {
         drillTarget = DrillTarget.CROSSTAB;
      }
      else {
         // muti-selected or single selected cell.
         // drillCells need sort by row index and col index.
         drillCells = Tool.getSortedSelectedHeaderCell(model);
      }

      this.buildDrillEvent(drillCells, events, model, isCollapse, drillField);

      if(drillTarget == DrillTarget.CROSSTAB) {
         this.resetScroll();
      }

      this.keepScroll = true;
      this.viewsheetClient.sendEvent(CROSSTAB_DRILL_CELLS_URI,
         new DrillCellsEvent(model.absoluteName, events, isCollapse, drillTarget, field));
   }

   private buildDrillEvent(drillCells: BaseTableCellModel[], events: DrillEvent[],
                           model: VSCrosstabModel, isDrillUp: boolean,
                           drillField = true, isDrillFilter?: boolean): void
   {
      let drillEvent: DrillEvent;

      drillCells
         .filter(c => c && !c.period &&
            (isDrillFilter ? c.drillLevel != null && c.drillLevel != DrillLevel.None : true))
         .forEach((c) => {
         drillEvent = DrillEvent.builder(model.absoluteName)
            .row(c.row)
            .col(c.col)
            .direction(VSCrosstab.getDrillDirection(model, c.row, c.col))
            .drillOp(isDrillUp ? ChartConstants.DRILL_UP_OP : ChartConstants.DRILL_DOWN_OP)
            .drillAll(!drillField)
            .field(c.field)
            .build();
         events.push(drillEvent);
      });
   }

   public static getDrillDirection(model: VSCrosstabModel, row: number, col: number): string {
      return row >= model.headerRowCount && col < model.headerColCount
         ? ChartConstants.DRILL_DIRECTION_Y
         : ChartConstants.DRILL_DIRECTION_X;
   }

   private drillAction(isDrillUp: boolean = false): void {
      const drillCells = Tool.getSortedSelectedHeaderCell(this.model, true, false);
      let drillEvents: DrillEvent[] = [];

      this.buildDrillEvent(drillCells, drillEvents, this.model, isDrillUp, true, true);

      const drillActionsEvent: BaseTableDrillEvent
         = new BaseTableDrillEvent(this.model.absoluteName, drillEvents, isDrillUp, true);

      this.viewsheetClient.sendEvent(CROSSTAB_ACTION_DRILL, drillActionsEvent);
      this.clearSelection();
   }

   processClearSelectionCommand(command: ClearSelectionCommand) {
      this.clearSelection();
   }

   get horizontalScrollbarWidth(): number {
      return this.isFullHorizontalWrapper ? this.getObjectWidth() : this.rbTableWidth;
   }

   get horizontalScrollWidth(): number {
      return this.isFullHorizontalWrapper ? this.getColumnWidthSum(0, this.model.colCount)
         : this.getColumnWidthSum(this.model.headerColCount, this.model.colCount);
   }

   updateDisplayColumnWidth(): void {
      super.updateDisplayColumnWidth();

      if(this.model.cells) {
         for(let r = 0; r < this.model.cells.length; r++) {
            for(let c = 0; c < this.model.cells[r].length; c++) {
               this.model.cells[r][c].cellWidth = null;
            }
         }
      }

      this.ltTable.forEach(row => row.forEach(cell => cell.cellWidth = null));
      this.rtTable.forEach(row => row.forEach(cell => cell.cellWidth = null));
   }

   public getDetailTableTopPosition(): number {
      return !this.loadedRows || this.loadedRows.start == 0 ? -this.scrollY - this.rtTableHeight
         : -this.scrollY + this.topRowHeight;
   }

   private showCrosstabDateComparisonDialog(model: VSCrosstabModel) {
      const variableValues = VSUtil.getVariableList(this.vsInfo.vsObjects, model.absoluteName);
      const modelUri = "../api/" + DATE_COMPARISON_DIALOG_URI + "/" +
         Tool.encodeURIPath(model.absoluteName) + "/" +
         Tool.encodeURIPath(this.viewsheetClient.runtimeId);
      const params = new HttpParams()
         .set("vsId", this.viewsheetClient.runtimeId)
         .set("assemblyName", model.absoluteName);

      this.modelService.getModel(modelUri).subscribe((data: DateComparisonDialogModel) => {
         const options = {
            windowClass: "property-dialog-window",
            objectId: model.absoluteName
         };
         const dialog: DateComparisonDialog = ComponentTool.showDialog(
            this.dialogService, DateComparisonDialog,
            (result: DateComparisonDialogModel) => {
               const result0: DateComparisonDialogModel = Tool.clone(result);
               const eventUri: string = "/events/" + DATE_COMPARISON_DIALOG_URI + "/" + model.absoluteName;
               this.viewsheetClient.sendEvent(eventUri, result0);
            }, options);
         dialog.onClear.asObservable().subscribe(() => {
            this.viewsheetClient.sendEvent("/events/" + DATE_COMPARISON_CLEAR_URI + "/" +
               model.absoluteName);
            dialog.close();
         });
         dialog.dateComparisonDialogModel = data;
         dialog.variableValues = variableValues;
         dialog.assemblyName = this.getAssemblyName();
         dialog.runtimeId = this.viewsheetClient.runtimeId;
         dialog.assemblyType = "VSCrosstab";
         dialog.scriptTreeModel = loadingScriptTreeModel;
         this.modelService.getModel(SCRIPT_TREE_URL, params).subscribe(res => dialog.scriptTreeModel = res);
      });
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
         new AssemblyActionEvent("crosstab date-comparison", this.model, null));
   }

   private hideColumn(model: VSCrosstabModel): void {
      let colIndex: number[] = [];

      if(model.selectedData) {
         model.selectedData.forEach((value) => {
            colIndex = colIndex.concat(value);
         });
      }

      if(model.selectedHeaders) {
         model.selectedHeaders.forEach((values, key) => {
            values.forEach((value) => {
               let rowNumber = Math.max(0, key - this.loadedRows.start);
               let cellmodel = model.cells[rowNumber][value];
               let colspan = cellmodel.colSpan;

               for(let i = 0; i < colspan; i++) {
                  colIndex.push(value + i);
               }
            });
         });
      }

      colIndex = Array.from(new Set(colIndex));

      let event: ShowHideCrosstabColumnsEvent = new ShowHideCrosstabColumnsEvent(
         model.absoluteName, false, colIndex);
      this.viewsheetClient.sendEvent("/events/composer/viewsheet/table/showHideColumns", event);

      model.selectedRegions = null;
      model.selectedHeaders = null;
      model.selectedData = null;
   }

   private showColumns(model: VSCrosstabModel): void {
      let event: ShowHideCrosstabColumnsEvent = new ShowHideCrosstabColumnsEvent(
         model.absoluteName, true);

      this.viewsheetClient.sendEvent("/events/composer/viewsheet/table/showHideColumns", event);
   }

   showPagingControl() {
      if(this.mobileDevice) {
         const pagingControlModel: PagingControlModel = {
            assemblyName: this.model.absoluteName,
            viewportWidth: this.horizontalScrollbarWidth,
            viewportHeight: this.verticalScrollbarHeight,
            contentWidth: this.horizontalScrollWidth,
            contentHeight: this.model.scrollHeight,
            scrollTop: this.scrollY,
            scrollLeft: this.scrollX,
            enabled: true
         };
         this.pagingControlService.setPagingControlModel(pagingControlModel);
      }
   }
}
