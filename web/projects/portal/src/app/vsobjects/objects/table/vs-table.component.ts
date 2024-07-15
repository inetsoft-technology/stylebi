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
import { HttpClient } from "@angular/common/http";
import {
   AfterViewInit,
   ChangeDetectionStrategy,
   ChangeDetectorRef,
   Component,
   ElementRef,
   EventEmitter,
   Input,
   NgZone,
   OnDestroy,
   OnInit,
   Optional,
   Output,
   Renderer2,
   ViewChild
} from "@angular/core";
import { NgbModal, NgbTooltip } from "@ng-bootstrap/ng-bootstrap";
import { DownloadService } from "../../../../../../shared/download/download.service";
import { Tool } from "../../../../../../shared/util/tool";
import { SourceInfoType } from "../../../binding/data/source-info-type";
import { BindingDropTarget, DataTransfer, TableTransfer } from "../../../common/data/dnd-transfer";
import { DragEvent } from "../../../common/data/drag-event";
import { DndService } from "../../../common/dnd/dnd.service";
import { ComponentTool } from "../../../common/util/component-tool";
import { DataPathConstants } from "../../../common/util/data-path-constants";
import { GuiTool } from "../../../common/util/gui-tool";
import { XConstants } from "../../../common/util/xconstants";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { Viewsheet } from "../../../composer/data/vs/viewsheet";
import { UpdateColumnsEvent } from "../../../vs-wizard/model/event/update-columns-event";
import { VsWizardEditModes } from "../../../vs-wizard/model/vs-wizard-edit-modes";
import { GetVSObjectModelEvent } from "../../../vsview/event/get-vs-object-model-event";
import { FixedDropdownService } from "../../../widget/fixed-dropdown/fixed-dropdown.service";
import { DebounceService } from "../../../widget/services/debounce.service";
import { DragService } from "../../../widget/services/drag.service";
import { ScaleService } from "../../../widget/services/scale/scale-service";
import { TableActions } from "../../action/table-actions";
import { LoadTableDataCommand } from "../../command/load-table-data-command";
import { UpdateSortInfoCommand } from "../../command/update-sort-info-command";
import { ContextProvider } from "../../context-provider.service";
import { RichTextService } from "../../dialog/rich-text-dialog/rich-text.service";
import { AddAnnotationEvent } from "../../event/annotation/add-annotation-event";
import { ApplyFormChangesEvent } from "../../event/table/apply-form-changes-event";
import { ChangeFormTableCellInput } from "../../event/table/change-form-table-cell-input-event";
import {
   ChangeVSTableCellsTextEvent,
   TableCellTextChange
} from "../../event/table/change-vs-table-cells-text-event";
import { DeleteTableRowsEvent } from "../../event/table/delete-table-rows-event";
import { InsertTableRowEvent } from "../../event/table/insert-table-row-event";
import { SortColumnEvent } from "../../event/table/sort-column-event";
import { BaseTableCellModel } from "../../model/base-table-cell-model";
import { VSTableModel } from "../../model/vs-table-model";
import { ShowHyperlinkService } from "../../show-hyperlink.service";
import { CheckFormDataService } from "../../util/check-form-data.service";
import { ViewerResizeService } from "../../util/viewer-resize.service";
import { VSUtil } from "../../util/vs-util";
import { DataTipService } from "../data-tip/data-tip.service";
import { BaseTable } from "./base-table";
import { PopComponentService } from "../data-tip/pop-component.service";
import { PagingControlModel } from "../../model/paging-control-model";
import { PagingControlService } from "../../../common/services/paging-control.service";
import { VSTabService } from "../../util/vs-tab.service";
import { HyperlinkModel } from "../../../common/data/hyperlink-model";

const ADD_DATA_URI: string = "/events/annotation/add-data-annotation";
const ADD_ASSEMBLY_URI: string = "/events/annotation/add-assembly-annotation";
const UPDATE_COLUMNS = "/events/vswizard/binding/update-columns";

@Component({
   selector: "vs-table",
   templateUrl: "vs-table.component.html",
   styleUrls: ["base-table.scss", "vs-table.component.scss"],
   changeDetection: ChangeDetectionStrategy.OnPush
})
export class VSTable extends BaseTable<VSTableModel> implements OnInit, OnDestroy, AfterViewInit {
   @Input()
   set model(model: VSTableModel) {
      if(this._model) {
         model.lastSelected = this._model.lastSelected;
      }

      this._model = model;

      if(!this._model.colNames || this._model.colNames.length == 0) {
         this.dropReplace = false;
      }

      this.updateLayout(false);
   }

   get model(): VSTableModel {
      return this._model;
   }

   @Input() viewsheet: Viewsheet;
   @Output() onPopupNotifications: EventEmitter<any> = new EventEmitter<any>();
   @Output() onOpenFormatPane = new EventEmitter<VSTableModel>();
   @ViewChild("zIndexWrapper", {read: ElementRef}) zIndexWrapper: ElementRef;
   @ViewChild("tableContainer") tableContainer: ElementRef;
   @ViewChild("myDrop") myDrop: any;
   @ViewChild("dataTable") dataTable: any;
   @ViewChild("tableHeaderDiv") tableHeaderDiv: any;

   @ViewChild("verticalScrollWrapper") verticalScrollWrapper: ElementRef;
   @ViewChild("horizontalScrollWrapper") horizontalScrollWrapper: ElementRef;

   @ViewChild("verticalScrollTooltip") verticalScrollTooltip: NgbTooltip;
   @ViewChild("horizontalScrollTooltip") horizontalScrollTooltip: NgbTooltip;

   @ViewChild("colResize") colResize0: ElementRef;
   @ViewChild("rowResize") rowResize0: ElementRef;
   @ViewChild("resizeLine") resizeLine0: ElementRef;

   get colResize(): ElementRef {
      return this.colResize0;
   }

   get rowResize(): ElementRef {
      return this.rowResize0;
   }

   get resizeLine(): ElementRef {
      return this.resizeLine0;
   }

   public tableHeaders: BaseTableCellModel[][];
   public tableData: BaseTableCellModel[][];
   public rowHyperlinks: HyperlinkModel[] = [];

   // Drag and drop
   dropRect: any = {top: 0, left: 0, width: 0, height: 0};
   dropIndex: number = -1;
   private dropReplace: boolean = false;
   private changedTableCells: TableCellTextChange[] = [];

   //The next cell to be focused on enter key
   nextCell: BaseTableCellModel;
   selectedDataIndex = {row: -1, column: -1};

   //actual width of the table element
   actualTableWidth: number;
   private lastStart = 0;
   public topRowHeight = 0;

   @Input()
   set actions(value: TableActions) {
      if(this.actionSubscription) {
         this.actionSubscription.unsubscribe();
         this.actionSubscription = null;
      }

      this._actions = value;

      if(value) {
         this.actionSubscription = value.onAssemblyActionEvent.subscribe((event) => {
            switch(event.id) {
            case "table export":
               this.exportTable();
               break;
            case "table multi-select":
               this.model.multiSelect = !this.model.multiSelect;
               break;
            case "table annotate title":
               this.subscriptions.add(this.richTextService.showAnnotationDialog((content) => {
                  this.addAssemblyAnnotation(content, event.event);
               }).subscribe());
               break;
            case "table annotate cell":
               this.subscriptions.add(this.richTextService.showAnnotationDialog((content) => {
                  this.addCellAnnotation(content);
               }).subscribe());
               break;
            case "table show-details":
               this.showDetails();
               break;
            case "table filter":
               this.addFilter(event.event,
                  this.tableContainer.nativeElement.getBoundingClientRect());
               break;
            case "table selection-reset":
               this.resetTable();
               break;
            case "table selection-apply":
               this.applyTable();
               break;
            case "table form-apply":
               this.applyFormChanges();
               break;
            case "table edit":
               if(this.openWizardEnabled()) {
                  this.openWizardPane(VsWizardEditModes.VIEWSHEET_PANE);
               }
               else {
                  this.openEditPane();
               }
               break;
            case "table highlight":
               this.openHighlightDialog();
               break;
            case "table conditions":
               this.openConditionDialog();
               break;
            case "table sort-column":
               this.sortClicked(false);
               break;
            case "table sort-column-aggregate":
               this.sortClicked(true);
               break;
            case "table insert-row":
               this.addRow(true);
               break;
            case "table append-row":
               this.addRow(false);
               break;
            case "table delete-rows":
               this.deleteRows();
               break;
            case "table open-max-mode":
               this.openMaxMode();
               break;
            case "table close-max-mode":
               this.closeMaxMode();
               break;
            case "table cell size":
               this.openCellSizeDialog(this.modalService);
               break;
            case "menu actions":
               VSUtil.showDropdownMenus(event.event, this.actions.menuActions, this.dropdownService);
               break;
            case "more actions":
               VSUtil.showDropdownMenus(event.event, this.actions.getMoreActions(),
                  this.dropdownService, []);
               break;
            case "table show-format-pane":
               this.onOpenFormatPane.emit(this.model);
               break;
            }
         });
      }
   }

   get actions(): TableActions {
      return <TableActions> this._actions;
   }

   constructor(viewsheetClient: ViewsheetClientService,
               private dndService: DndService,
               private dragService: DragService,
               dropdownService: FixedDropdownService,
               downloadService: DownloadService,
               private modalService: NgbModal,
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
               http: HttpClient,
               zone: NgZone,
               protected tabService: VSTabService,
               @Optional() viewerResizeService: ViewerResizeService,
               private richTextService: RichTextService)
   {
      super(viewsheetClient, dropdownService, downloadService, renderer,
            changeDetectorRef, contextProvider, formDataService, debounceService,
            scaleService, hyperlinkService, http, dataTipService, popComponentService,
            pagingControlService, zone, tabService, viewerResizeService);
   }

   public ngOnInit(): void {
      super.ngOnInit();
      this.clearSelection();
   }

   public ngAfterViewInit(): void {
      this.actualTableWidth = this.dataTableWidth;
   }

   public ngOnDestroy(): void {
      super.ngOnDestroy();

      if(this.actionSubscription) {
         this.actionSubscription.unsubscribe();
         this.actionSubscription = null;
      }

      this.subscriptions.unsubscribe();
   }

   protected updateTableHeight() {
      this.tableHeight = this.model.objectFormat.height - this.getHeaderHeight();

      if((!this.viewer || this.model.titleVisible) && !this.isBinding) {
         this.tableHeight -= this.model.titleFormat.height;
      }
   }

   protected updateLayout(loadOnDemand: boolean): void {
      this.updateTableHeight();
      this.updateVisibleRows(loadOnDemand);

      if(this.model.colNames.length == 0 && this.model.colCount == 0) {
         this.tableHeaders = [];
      }

      if(this.model.dataAnnotationModels) {
         this.model.leftTopAnnotations =
            this.model.dataAnnotationModels.filter((annotation) => {
               return this.isHeaderRow(annotation.row);
            });

         this.model.leftBottomAnnotations =
            this.model.dataAnnotationModels.filter((annotation) => {
               return !this.isHeaderRow(annotation.row);
            });

         this.positionDataAnnotations();
      }

      if(this.viewer && this.model.shrink) {
         this.sumColWidths();
      }

      this.resetRowResize();
   }

   /**
    * table header cell's sort index(multiple cells be selected).
    */
   get sortPositions(): number[] {
      return this.model.sortPositions ? this.model.sortPositions : [];
   }

   set sortPositions(sortPositions: number[]) {
      this.model.sortPositions = sortPositions;
   }

   /**
    * Calculates the wheel scroll value and changes scroll bar deltaX and deltaY
    * respectively.
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

   public getColumnWidthSum(from: number, to: number): number {
      return BaseTable.getSelectionWidth(this.model.colWidths, from, to);
   }

   public linkClicked(payload: any, header: boolean, row: number = -1): void {
      if(!header && !!this.rowHyperlinks && this.rowHyperlinks.length > 0) {
         const link = this.rowHyperlinks[row];

         if(payload.hyperlinks && payload.hyperlinks.length > 0 &&
            !payload.hyperlinks.some((hyperlink) => hyperlink == link))
         {
            payload.hyperlinks.unshift(link);
         }
      }

      this.openClickDropdownMenu(payload);
   }

   public rowLinkClicked(cell: BaseTableCellModel, rowIndex: number): void {
      if(!this.viewer || !this.rowHyperlinks || this.rowHyperlinks.length == 0) {
         return;
      }

      // cell hyperlinks and row hyperlinks cannot exist at the same time.
      // if cell exist hyperlinks, that means the hyperlinks is auto drill hyperlinks.
      // for a cell with auto drill hyperlinks, do not navigate directly to row hyperlink.
      if(!!cell.hyperlinks && cell.hyperlinks.length > 0) {
         return;
      }

      let link = this.rowHyperlinks[rowIndex];
      this.hyperlinkService.clickLink(link, this.viewsheetClient.runtimeId, this.vsInfo.linkUri);
   }

   sortClicked(multi: boolean): void {
      let event = SortColumnEvent.builder(this.getAssemblyName())
         .row(this.model.sortInfo.row)
         .col(this.model.sortInfo.col)
         .colName(this.model.sortInfo.field)
         .multi(multi)
         .build();
      this.viewsheetClient.sendEvent("/events/table/sort-column", event);
   }

   displaySortNumber(cell: BaseTableCellModel): number {
      if(this.sortPositions && this.sortPositionNum() > 1 &&
         this.sortPositions[cell.col] > -1)
      {
         return this.sortPositions[cell.col] + 1;
      }

      return 0;
   }

   sortPositionNum(): number {
      let num: number = 0;

      for(let column of this.sortPositions) {
         if(column > -1) {
            num++;
         }
      }

      return num;
   }

   public getWrapperWidth(): string {
      if(this.scrollWrapper) {
         return "100%";
      }
      else {
         let objectWidth = this.getObjectWidth();

         // fix the overflow wrapper when zoomed
         if(window.devicePixelRatio > 1 &&
            GuiTool.isChrome() &&
            this.zIndexWrapper != null &&
            this.model.objectFormat.border != null &&
            Tool.getMarginSize(this.model.objectFormat.border.right) === 0)
         {
            const atRight = this.scrollX + this.getObjectWidth() >=
               this.getColumnWidthSum(0, this.model.colCount);

            if(atRight) {
               objectWidth += 1;
            }
         }

         return objectWidth + "px";
      }
   }

   public getObjectWidth(): number {
      if(!this.scrollWrapper) {
         return super.getObjectWidth();
      }
      else {
         return Math.max(this.actualTableWidth, super.getObjectWidth());
      }
   }

   updateDisplayColumnWidth(): void {
      super.updateDisplayColumnWidth();
      this.actualTableWidth = this.dataTableWidth;
   }

   sortColumn(mouseEvent: MouseEvent, cell: BaseTableCellModel): void {
      mouseEvent.stopPropagation();

      let event = SortColumnEvent.builder(this.getAssemblyName())
         .row(cell.row)
         .col(cell.col)
         .colName(cell.field)
         .multi(mouseEvent.ctrlKey)
         .build();
      this.viewsheetClient.sendEvent("/events/table/sort-column", event);
   }

   focusNext(event: any) {
      if(this.nextCell != null) {
         this.selectCell(event, this.nextCell);
      }
   }

   nextCellChange(event: BaseTableCellModel) {
      let r = event.row;
      let c = event.col;

      if(r < this.model.rowCount - 1) {
         this.nextCell = this.tableData[r + 1][c];
      }
      else if(r == this.model.rowCount - 1) {
         this.nextCell = this.tableData[0][c];
      }
   }

   isSorted(column: number, type: string): boolean {
      if(!this.model.headersSortType) {
         return null;
      }

      const sortValue = this.model.headersSortType[column];

      switch(sortValue) {
      case XConstants.SORT_ASC:
         return type == "asc";
      case XConstants.SORT_DESC:
         return type == "desc";
      default:
         return type == "none";
      }
   }

   public selectCell(event: any, cell: BaseTableCellModel, header?: boolean): void {
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

      const row: number = cell.row;
      const column: number = cell.col;

      this.selectedDataIndex = {row: row - this.model.headerRowCount,
                                column: column - this.model.headerColCount};

      if(header && !this.model.embedded) {
         this.model.sortInfo = {
            row: row,
            col: column,
            field: cell.field,
            sortValue: this.model.headersSortType[column],
            sortable: true,
            sortPosition: this.sortPositions[column]
         };
      }
      else {
         this.model.sortInfo = {
            sortable: false
         };
      }

      // When right clicking on a selected cell, don't deselect anything.
      if(event.button === 2 && this.isSelected(cell, header)) {
         return;
      }

      const map: Map<number, number[]> =
         (header ? this.model.selectedHeaders : this.model.selectedData) || new Map();
      const omap: Map<number, number[]> = new Map(map);

      if(header) {
         this.model.selectedHeaders = map;
      }
      else {
         this.model.selectedData = map;
      }

      if(event.ctrlKey || this.model.multiSelect) {
         // When ctl clicking or multi-selecting on mobile on a selected cell,
         // deselect the cell instead
         if(this.isSelected(cell, header)) {
            this.deselectCell(row, column, map);
         }
         else {
            let selectedColumns = map.get(row) || [];
            map.set(row, selectedColumns.concat(column));

            if(this.model.firstSelectedColumn == null || this.model.firstSelectedColumn == -1) {
               this.model.firstSelectedRow = row;
               this.model.firstSelectedColumn = column;
            }

            this.addDataPath(cell.dataPath);
         }
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
               const header0 = firstRow + i < this.model.headerRowCount;
               const map0 = header0 ? selectedHeaders : selectedData;
               let currentRow = map0.get(firstRow + i) || [];

               if(currentRow.indexOf(firstCol + j) == -1) {
                  map0.set(firstRow + i, currentRow.concat(firstCol + j));
               }

               // Start row is the relative row while cell contains absolute row
               let relativeRow = firstRow - this.loadedRows.start;
               let tableRow = header0 ?
                  this.tableHeaders[relativeRow + i] : this.tableData[relativeRow + i];

               if(tableRow) {
                  this.addDataPath(tableRow[firstCol + j].dataPath);
               }
            }
         }
      }
      else {
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

      if(!header) {
         if(this.model.hasFlyover) {
            // Set flyover cell selected flag so mouseenter no longer trigger flyovers
            // until the selection is cleared
            this.flyoverCellSelected = true;
            this.sendFlyover(this.model.selectedData);
         }
      }

      this.model.lastSelected = {row: row, column: column};

      if(!this.dataTipService.isDataTipVisible(this.getAssemblyName())) {
         //Fixed bug#24120 There is no mouseenter event on the phone mode,
         //so no onEnter event of vs-table-cell is triggered.
         if(this.mobileDevice && this.viewer && this.model.dataTip && !header) {
            setTimeout(() => {
               if(!this.dataTipService.isFrozen()) {
                  this.dataTipService.hideDataTip();
               }

               this.dataTipService.showDataTip(
                  this.getAssemblyName(), this.model.dataTip, event.targetTouches[0]?.clientX,
                  event.targetTouches[0]?.clientY, cell.row + "X" + cell.col, this.model.dataTipAlpha);
            }, 0);
         }

         if(this.model.dataTip) {
            this.dataTipService.freeze();
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

      this.changeDetectorRef.detectChanges();
   }

   public isSelected(cell: BaseTableCellModel, header: boolean = false): boolean {
      let row = cell.row;
      let column = cell.col;
      let map: Map<number, number[]> =
         header ? this.model.selectedHeaders : this.model.selectedData;
      return map && super.isColumnSelected(map.get(row), column);
   }

   public isHyperlink(cell: BaseTableCellModel): boolean {
      return cell.hyperlinks != null && cell.hyperlinks.length > 0 && !this.model.form;
   }

   public selectTitle(event: MouseEvent): void {
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
         return;
      }

      this.model.titleSelected = true;
      this.addDataPath(DataPathConstants.TITLE);
   }

   private lastX: number;
   private lastY: number;

   public dragOverTable(event: DragEvent, idx: number): void {
      event.preventDefault();

      if(this.viewer && this.model.absoluteName != this.dndService.dragTableSource) {
         return;
      }

      const dragTypes: string[] = Object.keys(this.dragService.getDragData());

      if(dragTypes.length > 0 && dragTypes.some(type => type != "column")) {
         return;
      }

      if(this.lastX != event.clientX || this.lastY != event.clientY) {
         const odrop = this.dropIndex + ":"
            + this.dropReplace + JSON.stringify(this.dropRect);
         this.drawDropRect(event, idx);
         this.lastX = event.clientX;
         this.lastY = event.clientY;
         const ndrop = this.dropIndex + ":"
            + this.dropReplace + JSON.stringify(this.dropRect);

         if(ndrop != odrop) {
            this.dndService.setDragOverStyle(event, !this.dndService.isCalcAggregate());
            this.changeDetectorRef.detectChanges();
         }
      }
   }

   public dragLeave(event: DragEvent): void {
      this.dropReplace = false;
      this.dropRect = {top: 0, left: 0, width: 0, height: 0};
   }

   public dropOverEmptyTable(event: any, idx: number): void {
      if(this.tableHeaders == null || this.tableHeaders.length == 0) {
         event.preventDefault();
         this.dropIndex = 0;
         this.dropRect = {top: 0, left: 0, width: 0, height: 0};
      }
   }

   private parseData(event: DragEvent): any {
      let data: any = null;

      try {
         data = JSON.parse(event.dataTransfer.getData("text"));
      }
      catch(e) {
         console.warn("Invalid drop event on vsTable : ", e);
      }

      return data;
   }

   public dropEmptyTable(event: DragEvent): void {
      event.preventDefault();
      let data: any = this.parseData(event);
      const dragName: string = data.dragName[0];

      if((dragName === "column" || dragName == "tableBinding") &&
         (this.tableHeaders == null || this.tableHeaders.length == 0))
      {
         this.dropOnTable(event);
      }
   }

   public dropOnTable(event: DragEvent): void {
      if(this.dropReplace && this.vsWizardPreview) {
         this.dropIndex = -1;
         return;
      }

      let data: any = this.parseData(event);

      if(!data.column && !data.dragSource ||
         data.column && !!data.column[0].properties["cube.column.type"] &&
         data.column[0].properties["type"] != SourceInfoType.VS_ASSEMBLY)
      {
         return;
      }

      event.preventDefault();
      event.stopPropagation();

      if(this.viewer && this.model.absoluteName != this.dndService.dragTableSource) {
         return;
      }

      if(!!data && !!data.column && !!this.model && this.model.embedded &&
         this.dndService.containsCalc(data.column))
      {
         this.dragLeave(event);
         this.zone.run(() => {
            ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
               "_#(js:common.viewsheet.embeddedTable.bindingCalc)");
         });

         return;
      }
      else if(!!this.model && this.model.embedded &&
         !this.dndService.isAllEmbeddedColumn(event))
      {
         this.dragLeave(event);
         this.zone.run(() => {
            ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
               "_#(js:common.viewsheet.embeddedTable.binding)");
         });

         return;
      }

      if(data && data.column && data.column[0] &&
         data.column[0].properties.isCalc === "true" &&
         data.column[0].properties.basedOnDetail === "false")
      {
         this.dropIndex = -1;

         this.zone.run(() => {
            ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
               "_#(js:common.viewsheet.table.notDragAgg)");
         });

         return;
      }

      const dtarget: BindingDropTarget = new BindingDropTarget("details", this.dropIndex,
         this.dropReplace, "vstable", this.getAssemblyName());
      dtarget.transferType = "table";

      if(this.contextProvider.vsWizardPreview) {
         const transferData: any = JSON.parse(event.dataTransfer.getData("text"));

         if(!transferData) {
            return;
         }

         let transfer: DataTransfer = transferData.dragSource;
         let wizardEvent
            = new UpdateColumnsEvent(+transfer["dragIndex"], dtarget.dropIndex);

         if(dtarget.dropIndex !== (+transfer["dragIndex"]) &&
            dtarget.dropIndex !== (+transfer["dragIndex"]) + 1)
         {
            this.viewsheetClient.sendEvent(UPDATE_COLUMNS, wizardEvent);
         }
      }
      else {
         this.dndService.processOnDrop(event, dtarget);
      }

      this.dropIndex = -1;
   }

   public dragStart(event: any, idx: number): void {
      this.dndService.setDragStartStyle(event, this.model.colNames[idx]);
      this.dndService.dragTableSource = this.model.absoluteName;
      const tableTransfer = new TableTransfer("details", idx, this.getAssemblyName());
      tableTransfer.transferType = "table";

      Tool.setTransferData(event.dataTransfer,
         {
            dragName: ["tableBinding"],
            dragSource: tableTransfer
         });
   }

   public isDraggable(header: boolean): boolean {
      return (header || !this.viewer) && this.initialMousePos == -1 &&
         this.resizeCol == -1 && !this.model.editing && !this.vsWizard && !this.vsWizardPreview;
   }

   public changeCellText(data: string, cell: BaseTableCellModel): void {
      let textChange: TableCellTextChange = {
         row: cell.row - this.model.headerRowCount,
         col: cell.col,
         text: data
      };

      if(this.model.embedded && !this.model.submitOnChange) {
         this.changedTableCells.push(textChange);
         cell.cellLabel = data;
      }
      else {
         this.formDataService.checkFormData0(
            this.viewsheetClient.runtimeId, this.model.absoluteName, null,
            () => {
               let event: ChangeVSTableCellsTextEvent =
                  new ChangeVSTableCellsTextEvent(this.model.absoluteName);
               event.changes = [textChange];
               this.viewsheetClient.sendEvent(
                  "/events/composer/viewsheet/table/changeCellText", event);
            },
            () => {
               let event: GetVSObjectModelEvent =
                  new GetVSObjectModelEvent(this.model.absoluteName);
               this.viewsheetClient.sendEvent("/events/vsview/object/model", event);
            }
            , false
         );
      }
   }

   public formInputChanged(data: string, cell: BaseTableCellModel): void {
      let event: ChangeFormTableCellInput =
         new ChangeFormTableCellInput(this.model.absoluteName, cell.row, cell.col, data,
            this.loadedRows.start);

      this.viewsheetClient.sendEvent("/events/formTable/edit", event);
   }

   /**
    * Processes an update sort info command.
    *
    * @param command the command to process.
    */
   protected processUpdateSortInfoCommand(command: UpdateSortInfoCommand): void {
      if(this.model) {
         this.model.headersSortType = command.sortOrders;
         this.sortPositions = command.sortPositions;
      }
   }

   /**
    * Apply an embedded tables changes.
    */
   public applyTable(): void {
      if(this.changedTableCells.length > 0) {
         this.formDataService.checkFormData(
            this.viewsheetClient.runtimeId, this.model.absoluteName, null,
            () => {
               let event: ChangeVSTableCellsTextEvent =
                  new ChangeVSTableCellsTextEvent(this.model.absoluteName);
               event.changes = this.changedTableCells;
               this.viewsheetClient.sendEvent(
                  "/events/composer/viewsheet/table/changeCellText", event);
               this.changedTableCells = [];
            }
         );
      }
   }

   public applyFormChanges() {
      const event = new ApplyFormChangesEvent(this.model.absoluteName);
      this.viewsheetClient.sendEvent("/events/formTable/apply", event);
   }

   /**
    * Reset embedded tables changes.
    */
   public resetTable(): void {
      if(this.changedTableCells.length > 0) {
         this.updateData(this.loadedRows.start);
         this.changedTableCells = [];
      }
   }

   public getHeaderRowHeight(row: number): number {
      if(this.model.wrapped && this.model.headerRowCount == 1) {
         return this.getHeaderHeight();
      }
      else {
         return this.getRowHeight(row);
      }
   }

   /**
    * @inheritDoc
    */
   public getRowHeight(row: number): number {
      if(row >= 1 && this.model.rowHeights && !isNaN(this.model.rowHeights[1])) {
         // tables inherit their height from the first data row height. When set through
         // script we put the height in key 1 from TableDataVSAScriptable#setRowHeight
         return this.model.rowHeights[1];
      }

      return super.getRowHeight(row);
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

      if(command.start === 0) {
         if(this.verticalScrollWrapper.nativeElement.scrollTop < 0 && this.lastStart != 0) {
            this.verticalScrollWrapper.nativeElement.scrollTop = 0;
         }

         this.tableHeaders = command.tableCells.slice(0, this.model.headerRowCount);
         this.tableData = command.tableCells;

         for(let i = 0; i < this.model.headerRowCount && i < command.tableCells.length; i++) {
            let cells: BaseTableCellModel[] = command.tableCells[i];

            for(let j = 0; j < this.model.colCount && cells[j]; j++) {
               cells[j].field = this.model.colNames[j];
            }
         }
      }
      else {
         if(command.tableHeaderCells) {
            this.tableHeaders = command.tableHeaderCells;
         }

         for(let i = 0; i < this.model.headerRowCount; i++) {
            let headers: BaseTableCellModel[] = this.tableHeaders[i];

            for(let j = 0; j < this.model.colCount && headers[j]; j++) {
               headers[j].field = this.model.colNames[j];
            }
         }
      }

      this.tableData = command.tableCells;
      this.rowHyperlinks = command.rowHyperlinks;
      this.topRowHeight = this.getRowPosition(command.start - 1);
      this.updateVisibleRows(false);

      // reset scroll if loading data from top
      if(this.topRowHeight == 0 && this.scrollY > 0 && this.lastStart != 0 && !this.vsInfo.formatPainterMode) {
         this.scrollY = 0;
         this.verticalScrollWrapper.nativeElement.scrollTop = 0;
      }

      if(command.start == 0 && this.currentRow > command.end) {
         this.scrollY = 0;
         this.verticalScrollWrapper.nativeElement.scrollTop = 0;
      }

      this.lastStart = command.start;
      this.model.formChanged = command.formChanged;
      this.checkScroll();
   }

   protected addAssemblyAnnotation(content: string, event: MouseEvent): void {
      const tableBounds = this.tableContainer.nativeElement.getBoundingClientRect();
      let x = (event.clientX - tableBounds.left) / this.scale;
      let y = (event.clientY - tableBounds.top) / this.scale;
      const annotateEvent =
         new AddAnnotationEvent(content, x, y, this.getAssemblyName());
      this.viewsheetClient.sendEvent(ADD_ASSEMBLY_URI, annotateEvent);
   }

   protected addCellAnnotation(content: string): void {
      const selectedCells = this.model.selectedData || this.model.selectedHeaders;

      if(selectedCells) {
         const row = selectedCells.keys().next().value;
         const col = selectedCells.get(row).values().next().value;
         const selectedCell =
            this.cells
                .filter((cell) => cell.isRendered)
                .filter((cell) => cell.selected)
                .find((cell) => {
                   return cell.cell.row === row && cell.cell.col === col;
                });

         if(selectedCell) {
            const selectedCellBounds = selectedCell.boundingClientRect;
            const cellX = selectedCellBounds.left + selectedCellBounds.width / 2;
            const cellY = selectedCellBounds.top + selectedCellBounds.height / 2;
            const tableBounds =
               this.tableContainer.nativeElement.getBoundingClientRect();
            const x = (cellX - tableBounds.left + this.scrollX) / this.scale;
            const y = (cellY - tableBounds.top + this.scrollY) / this.scale;
            const annotateEvent =
               new AddAnnotationEvent(content, x, y, this.getAssemblyName());
            annotateEvent.setRow(row);
            annotateEvent.setCol(col);
            this.viewsheetClient.sendEvent(ADD_DATA_URI, annotateEvent);
         }
      }
   }

   private drawDropRect(event: DragEvent, idx: number): void {
      let containerBounds = (<Element> event.currentTarget).getBoundingClientRect();
      let eventX = event.clientX - containerBounds.left;
      let eventY = event.clientY - containerBounds.top;
      let colwidth = this.displayColWidths[idx];

      if(eventX < 10) {
         this.dropIndex = idx;
         this.dropReplace = false;
         this.dropRect.left = this.getWidthSum(event, idx) - 3;
         this.dropRect.width = 3;
      }
      // dnd to replace a column in viewer seems not useful and could be confusing
      // when a casual user does this accidentally. (51036)
      else if(eventX > colwidth - 10 || this.viewer) {
         this.dropIndex = idx + 1;
         this.dropReplace = false;
         this.dropRect.left = this.getWidthSum(event, idx) + colwidth - 3;
         this.dropRect.width = 3;
      }
      else if(this.vsWizardPreview) {
         this.dropReplace = true;
         this.dropIndex = idx;
      }
      else {
         this.dropIndex = idx;
         this.dropReplace = true;
         this.dropRect.left = this.getWidthSum(event, idx);
         this.dropRect.width = colwidth;
      }

      this.dropRect.left -= this.scrollX;
      this.dropRect.top = 0;
      this.dropRect.height = this.getObjectHeight();

      if(!this.viewer || this.model.titleVisible) {
         this.dropRect.top = this.model.titleFormat.height;
         this.dropRect.height -= this.model.titleFormat.height;
      }
   }

   private getWidthSum(event: any, idx: number): number {
      if(idx == 0) {
         return 0;
      }

      let w: number = 0;

      for(let i = 0; i < idx; i++) {
         w += this.displayColWidths[i];
      }

      return w;
   }

   private addRow(insert: boolean): void {
      const row = insert ? this.model.firstSelectedRow : this.model.lastSelected.row;
      const event = new InsertTableRowEvent(this.model.absoluteName, insert, row,
         this.loadedRows.start);
      this.viewsheetClient.sendEvent("/events/formTable/addRow", event);
   }

   private deleteRows(): void {
      const rows = Array.from(this.model.selectedData.keys());
      const event = new DeleteTableRowsEvent(this.model.absoluteName, rows,
         this.loadedRows.start);
      this.viewsheetClient.sendEvent("/events/formTable/deleteRows", event);
   }

   /** @inheritDoc */
   protected positionDataAnnotations(): boolean {
      // Since *ngFor was changed to use trackByConstant, this component's cells QueryList no longer
      // updates on scroll events or loadTableData commands. Added a setTimeout to wait an extra tick
      // for the cell values to update to correctly display annotations.
      this.zone.run(() => {
         setTimeout(() => {
            const lt = this.positionAnnotationsToCell(this.model.leftTopAnnotations,
               0, this.scrollX);
            const lb = this.positionAnnotationsToCell(this.model.leftBottomAnnotations,
               this.scrollY, this.scrollX);

            if(lt || lb) {
               this.changeDetectorRef.detectChanges();
            }
         });
      });

      return false;
   }

   isHeaderSortEnabled(): boolean {
      return (!this.model.embedded || this.model.embedded && this.viewer
         && this.model.form && this.model.enabled) && this.sortColumnVisible;
   }

   isHeaderSortVisible(cell: BaseTableCellModel): boolean {
      return !!this.model.headersSortType
         && this.model.headersSortType[cell.col] != 0;
   }

   /** @inheritDoc */
   public changeCellTitle(cell: BaseTableCellModel) {
      cell.cellLabel = cell.cellData;
      super.changeCellTitle(cell);
   }

   protected checkScroll(): void {
      // optimization, set style immediately and delay the change detection.
      // styles should match the styles set in html
      this.renderer.setStyle(this.dataTable.nativeElement,
         "left", -this.scrollX + "px");
      this.renderer.setStyle(this.dataTable.nativeElement,
         "top", this.getDetailTableTopPosition() + "px");
      this.renderer.setStyle(this.tableHeaderDiv.nativeElement,
         "left", -this.scrollX + "px");
   }

   // get the row visibility:
   // 1: row is visible
   // 2: row should be created for quick scrolling
   // 0: row should be ignored
   getRowVisibility(idx: number): number {
      if(this.isRowVisible(idx)) {
         return 1;
      }
      else if(idx < 200) {
         return 2;
      }

      return 0;
   }

   get dataTableHeight(): number {
      if(this.dataTable) {
         return this.dataTable.nativeElement.offsetHeight;
      }

      return 0;
   }

   get dataTableWidth(): number {
      if(this.dataTable) {
         return this.dataTable.nativeElement.offsetWidth;
      }

      return 0;
   }

   public getDetailTableTopPosition(): number {
      return !this.loadedRows || this.loadedRows.start == 0 ?
         -this.scrollY - this.getHeaderHeight() :
         -this.scrollY + this.topRowHeight;
   }

   get scrollHeight(): number {
      // firefox bug, if div height is >17,000,000, the div height is assigned 0
      if(GuiTool.isFF()) {
         return Math.min(17000000, this.model.scrollHeight);
      }

      return this.model.scrollHeight;
   }

   showPagingControl(): void {
      if(this.mobileDevice) {
         const pagingControlModel: PagingControlModel = {
            assemblyName: this.model.absoluteName,
            viewportWidth: this.getObjectWidth(),
            viewportHeight: this.verticalScrollbarHeight,
            contentWidth: this.getColumnWidthSum(0, this.model.colCount),
            contentHeight: this.model.scrollHeight,
            scrollTop: this.scrollY,
            scrollLeft: this.scrollX,
            enabled: true
         };
         this.pagingControlService.setPagingControlModel(pagingControlModel);
      }
   }

   rowHoverable(): boolean {
      return !!this.rowHyperlinks && this.rowHyperlinks.length > 0;
   }

   getTooltip(cell: BaseTableCellModel, row: number = -1): string {
      let tooltip: string = null;

      if(!!this.rowHyperlinks && this.rowHyperlinks.length > 0 && row > -1) {
         tooltip = this.rowHyperlinks[row]?.tooltip;
      }

      return !!tooltip ? tooltip : super.getTooltip(cell);
   }
}
