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
import { DOCUMENT } from "@angular/common";
import { HttpParams } from "@angular/common/http";
import {
   AfterContentInit,
   ChangeDetectionStrategy,
   ChangeDetectorRef,
   Component,
   ElementRef,
   EventEmitter,
   HostListener,
   Inject,
   Input,
   NgZone,
   OnChanges,
   OnDestroy,
   Output,
   Renderer2,
   SimpleChange,
   SimpleChanges,
   ViewChild
} from "@angular/core";
import { UntypedFormControl, Validators } from "@angular/forms";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Observable, Subscription } from "rxjs";
import { delay } from "rxjs/operators";
import { AssetType } from "../../../../../../../shared/data/asset-type";
import { FormValidators } from "../../../../../../../shared/util/form-validators";
import { Tool } from "../../../../../../../shared/util/tool";
import { ColumnRef } from "../../../../binding/data/column-ref";
import { AssemblyActionGroup } from "../../../../common/action/assembly-action-group";
import { BinarySearch } from "../../../../common/data/binary-search";
import { DragEvent } from "../../../../common/data/drag-event";
import { Range } from "../../../../common/data/range";
import { XSchema } from "../../../../common/data/xschema";
import { ComponentTool } from "../../../../common/util/component-tool";
import { GuiTool } from "../../../../common/util/gui-tool";
import { CommandProcessor, ViewsheetClientService } from "../../../../common/viewsheet-client";
import { AssetTreeService } from "../../../../widget/asset-tree/asset-tree.service";
import { ActionsContextmenuComponent } from "../../../../widget/fixed-dropdown/actions-contextmenu.component";
import { DropdownOptions } from "../../../../widget/fixed-dropdown/dropdown-options";
import { FixedDropdownService } from "../../../../widget/fixed-dropdown/fixed-dropdown.service";
import { DebounceService } from "../../../../widget/services/debounce.service";
import { DragService } from "../../../../widget/services/drag.service";
import { ModelService } from "../../../../widget/services/model.service";
import { AbstractTableAssembly } from "../../../data/ws/abstract-table-assembly";
import { ColumnInfo } from "../../../data/ws/column-info";
import { ComposedTableAssembly } from "../../../data/ws/composed-table-assembly";
import { EmbeddedTableAssembly } from "../../../data/ws/embedded-table-assembly";
import { ColumnMouseEvent } from "../../../data/ws/column-mouse.event";
import { SelectColumnSourceEvent } from "../../../data/ws/select-column-source.event";
import { Worksheet } from "../../../data/ws/worksheet";
import { WSTableData } from "../../../data/ws/ws-table-data";
import { selectingHeaderSource } from "../../../util/worksheet-util";
import { ResizeHandlerService } from "../../resize-handler.service";
import { WSDeleteColumnsEvent } from "../socket/ws-delete-columns-event";
import { WSDeleteDataEvent } from "../socket/ws-delete-data-event";
import { WSEditTableDataEvent } from "../socket/ws-edit-table-data-event";
import { WSInsertColumnsEvent } from "../socket/ws-insert-columns/ws-insert-columns-event";
import { WSInsertDataEvent } from "../socket/ws-insert-data-event";
import { WSLoadTableDataCommand } from "../socket/ws-load-table-data/ws-load-table-data-command";
import { WSLoadTableDataEvent } from "../socket/ws-load-table-data/ws-load-table-data-event";
import { WSRenameColumnEvent } from "../socket/ws-rename-column/ws-rename-column-event";
import { WSRenameColumnEventValidator } from "../socket/ws-rename-column/ws-rename-column-event-validator";
import { WSResizeColumnEvent } from "../socket/ws-resize-column-event";
import { WSSetColumnIndexEvent } from "../socket/ws-set-column-index-event";
import { EditCellPosition } from "./edit-cell-position";
import { NotificationsComponent } from "../../../../widget/notifications/notifications.component";
import { WSReplaceColumnsEvent } from "../socket/ws-replace-column/ws-replace-columns-event";
import { AssetEntry } from "../../../../../../../shared/data/asset-entry";
import { DomService } from "../../../../widget/dom-service/dom.service";
import { WSAssemblyEvent } from "../socket/ws-assembly-event";
import { WSTableMode } from "../../../data/ws/ws-table-assembly";
import {SQLBoundTableAssembly} from "../../../data/ws/sql-bound-table-assembly";

const CONTROLLER_SET_COLUMN_INDEX = "/events/composer/worksheet/set-column-index";
const CONTROLLER_EDIT_TABLE_DATA = "/events/composer/worksheet/edit-table-data";
const CONTROLLER_RESIZE_COLUMN = "/events/composer/worksheet/resize-column";
const CONTROLLER_DELETE_COLUMNS_CHECK = "../api/composer/worksheet/delete-columns/check-dependency/";
const CONTROLLER_DELETE_COLUMNS = "/events/composer/worksheet/delete-columns";
const CONTROLLER_RELOAD_TABLE_DATA = "/events/ws/table/reload-table-data/";
const CONTROLLER_INSERT_DATA = "/events/composer/worksheet/insert-data";
const CONTROLLER_DELETE_DATA = "/events/composer/worksheet/delete-data";
const CONTROLLER_RENAME_COLUMN = "/events/composer/worksheet/rename-column";
const CONTROLLER_RENAME_COLUMN_VALIDATOR = "../api/composer/worksheet/rename-column/";
const TABLE_MODE_SOCKET_URI = "/events/composer/worksheet/table-mode/";

let tableDataRequestId = 0;

@Component({
   selector: "ws-details-table-data",
   templateUrl: "ws-details-table-data.component.html",
   styleUrls: ["ws-details-table-data.component.scss"],
   changeDetection: ChangeDetectionStrategy.OnPush
})
export class WSDetailsTableDataComponent extends CommandProcessor
   implements OnDestroy, AfterContentInit, OnChanges
{
   @Input() table: AbstractTableAssembly;
   @Input() worksheet: Worksheet;
   @Input() selectingColumnSource: boolean;
   @Input() showName: boolean;
   @Input() wrapColumnHeaders: boolean;
   @Output() onEditFormulaColumn = new EventEmitter<number>();
   @Output() onEditAggregateColumn = new EventEmitter<void>();
   @Output() onEditGroupColumn = new EventEmitter<void>();
   @Output() onEditDateColumn = new EventEmitter<[string, boolean]>();
   @Output() onEditNumericColumn = new EventEmitter<[string, boolean]>();
   @Output() onChangeColumnType = new EventEmitter<number>();
   @Output() onChangeColumnDescription = new EventEmitter<number>();
   @Output() onInsertColumns = new EventEmitter<WSInsertColumnsEvent>();
   @Output() onReplaceColumns = new EventEmitter<WSReplaceColumnsEvent>();
   @Output() onRowRangeChange = new EventEmitter<Range>();
   @Output() onSelectColumnSource = new EventEmitter<SelectColumnSourceEvent>();
   @Output() onOozColumnMouseEvent = new EventEmitter<ColumnMouseEvent>();
   @Output() onSearchResultUpdate = new EventEmitter<[number, number]>();
   @ViewChild("tableDataContainer", {static: true}) tableDataContainer: ElementRef<HTMLDivElement>;
   @ViewChild("inputEditHeader") inputEditHeader: ElementRef<HTMLInputElement>;
   @ViewChild("inputEditCell") inputEditCell: ElementRef<HTMLInputElement>;
   @ViewChild("tableDataHeightPillar") tableDataHeightPillar: ElementRef<HTMLDivElement>;
   @ViewChild("notifications") notifications: NotificationsComponent;
   readonly RESIZE_BORDER_OFFSET = 3;
   readonly HEADER_CELL_HEIGHT = 30;
   readonly TABLE_CELL_HEIGHT = 21;
   readonly scrollbarWidth = GuiTool.measureScrollbars();
   tableData: WSTableData;
   rowIndexRange: Range;
   columnIndexRange: Range;
   contentInit: boolean = false; // true if afterContentInit has already happened.
   horizontalDist: number;
   scrollLeft: number;
   tableHeight: number;
   dropColumnIndicator: number;
   dropReplace: boolean = false;
   dragging: Observable<boolean>;
   tableWidth: number;
   columnRightPositions: number[] = [];

   /** Edit cell fields */
   currentEditPosition: EditCellPosition;
   editCellArtificialBlur: boolean; // If true, next blur event should be treated as artificial

   /** Edit header fields */
   editHeaderIndex: number;
   editHeaderControl: UntypedFormControl;
   headerDisableSubmitOnBlur: boolean;
   editHeaderArtificialBlur: boolean;

   /** Column resize fields */
   private lastPageX: number = 0;
   private resizeColumnIndex: number;
   private originalLeft: number;
   resizeLeft: number;
   private resizePageXOrigin: number;
   private moveResizeListener = this.moveResize.bind(this);
   private endResizeListener = this.endResize.bind(this);

   /** Header selection */
   selectedHeaderIndices: number[] = [];
   canRemoveSelectedHeaders: boolean = false;
   primarySelectedHeader: number;
   simpleHeaderClick: boolean;

   private readonly TABLE_SCROLL_DEBOUNCE_TIME = 100;
   private readonly FINISH_LOADING_TABLE_DEBOUNCE_TIME = 200;
   private readonly MIN_COL_WIDTH = 40;
   private readonly SCROLL_OFFSET = 3;
   private readonly BLOCK_SIZE: number = 100;

   private readonly MAX_TABLE_HEIGHT = this.getMaxTableHeight();
   private readonly NUM_ROWS_TO_SCROLL_FALLBACK = 3;
   private visibleRows: number;
   private targetStartRow: number;
   private resizeSub: Subscription;
   private isFocused: Subscription;
   private lastRequestedTableData: WSLoadTableDataEvent | null;
   private _searchQuery: string;
   private _searchIndex: number;
   private keepScroll: boolean = false;
   searchIndices: number[];
   searchMatches: number[];

   @Input() set searchQuery(value: string) {
      this._searchQuery = value;

      if(!!value) {
         this.updateSearch();
      }
      else {
         this.searchMatches = [];
         this.searchIndices = [];
         this.onSearchResultUpdate.emit([0, 0]);

         if(!value) {
            this.renderer.setProperty(this.hostRef.nativeElement, "scrollLeft", 0);
         }
      }
   }

    get searchQuery(): string {
      return this._searchQuery;
   }


   @Input() set searchIndex(value: number) {
      this._searchIndex = value;

      if(value > 0) {
         this.focusSearchHeader();
      }
   }

   get searchIndex() {
      return this._searchIndex;
   }

   constructor(private hostRef: ElementRef,
               private RHService: ResizeHandlerService,
               private cd: ChangeDetectorRef,
               private worksheetClient: ViewsheetClientService,
               private renderer: Renderer2,
               private domService: DomService,
               private dragService: DragService,
               private dropdownService: FixedDropdownService,
               private modalService: NgbModal,
               private modelService: ModelService,
               private debounceService: DebounceService,
               private zone: NgZone,
               @Inject(DOCUMENT) private document: any)
   {
      super(worksheetClient, zone);
   }

   ngOnChanges(changes: SimpleChanges): void {
      if(changes.hasOwnProperty("table")) {
         this.lastRequestedTableData = null;
         const tableChanges = changes["table"];
         const currentTable: AbstractTableAssembly = tableChanges.currentValue;
         const previousTable: AbstractTableAssembly = tableChanges.previousValue;

         this.populateWidths();
         this.selectedHeaderIndices = [];
         this.updateCanRemoveSelectedHeaders();
         this.primarySelectedHeader = undefined;
         let resetScrollTop: boolean = false;

         if(tableChanges.isFirstChange() || currentTable.name !== previousTable.name) {
            this.tableData = null;
            this.requestTableData(true);
            this.horizontalDist = this.hostRef.nativeElement.clientWidth;
            this.scrollLeft = 0;
            this.rowIndexRange = null;
            this.columnIndexRange = null;
            this.renderer.setProperty(this.hostRef.nativeElement, "scrollLeft", 0);
            resetScrollTop = true;
            this.currentEditPosition = null;

            this.editHeaderControl = new UntypedFormControl("",
               [Validators.required, FormValidators.notWhiteSpace,
                  FormValidators.calcSpecialCharacters]);
         }
         else {
            if(currentTable.colInfos.length !== previousTable.colInfos.length) {
               this.tableData = null;
               this.rowIndexRange = null;
               this.columnIndexRange = null;
               this.editHeaderIndex = null;
            }

            // If row count is unchanged or insert and remove, load from same position.
            if(!!this.tableData && currentTable.totalRows === previousTable.totalRows ||
               (!previousTable.rowsCompleted && currentTable.totalRows > previousTable.totalRows) ||
                this.keepScroll)
            {
               this.requestTableData(false, this.targetStartRow);
               this.updateCurrentEditPosition(tableChanges);
               this.keepScroll = false;
            }
            else {
               this.requestTableData();
               resetScrollTop = true;
               this.currentEditPosition = null;
            }

            const upperBound = Math.min(this.tableWidth + this.scrollbarWidth,
               this.horizontalDist);
            this.horizontalDist = Math.max(upperBound, this.hostRef.nativeElement.clientWidth);
         }

         if(!!previousTable) {
            previousTable.clearFocusedAttribute();
         }

         if(resetScrollTop) {
            this.resetScrollTop();
         }
      }
   }

   ngAfterContentInit(): void {
      this.resizeSub = this.RHService.anyResizeSubject.subscribe(() => {
         if(this.worksheet.isFocused) {
            this.updateDimensions();
            this.cd.markForCheck();
            this.cd.detectChanges();
         }
      });

       this.isFocused = this.worksheet.focused.subscribe( () => {
        if(this.worksheet.isFocused) {
           this.resetColumnRange();
           this.cd.markForCheck();
           this.cd.detectChanges();
        }
      });

      this.updateDimensions();
      this.dragging = this.dragService.currentDraggingObservable.pipe(delay(0));
      this.contentInit = true;
   }

   ngOnDestroy(): void {
      super.cleanup();
      this.resizeSub.unsubscribe();
      this.table.clearFocusedAttribute();
   }

   @HostListener("scroll")
   updateHorizontalWidth(): void {
      this.scrollLeft = this.hostRef.nativeElement.scrollLeft;
      this.horizontalDist = this.hostRef.nativeElement.clientWidth + this.scrollLeft;
      this.updateColumnRange();
   }

   private updateColumnRange(): void {
      const leftViewBound = this.hostRef.nativeElement.scrollLeft;
      const rightViewBound = leftViewBound + this.hostRef.nativeElement.clientWidth;
      const search = BinarySearch.numbers(this.columnRightPositions);

      let leftmostColumnIndex = search.ceiling(leftViewBound);

      if(leftmostColumnIndex == null) {
         leftmostColumnIndex = 0;
         this.hostRef.nativeElement.scrollLeft = 0;
      }

      let rightmostColumnIndex = search.ceiling(rightViewBound);

      if(rightmostColumnIndex == null) {
         rightmostColumnIndex = this.table.colInfos.length - 1;
      }

      this.columnIndexRange = new Range(leftmostColumnIndex, rightmostColumnIndex);
   }

   private resetColumnRange(): void {
      let leftmostColumnIndex= 0;
      this.hostRef.nativeElement.scrollLeft = 0;
      let rightmostColumnIndex = this.table.colInfos.length - 1;

      this.columnIndexRange = new Range(leftmostColumnIndex, rightmostColumnIndex);
   }

   getAssemblyName(): string {
      return this.table ? this.table.name : null;
   }

   columnTypeIsNumeric(columnIndex: number): boolean {
      const colInfo = this.table.colInfos[columnIndex];

      if(colInfo == null) {
         return false;
      }

      return XSchema.isNumericType(colInfo.ref.dataType);
   }

   private updateCurrentEditPosition(tableChanges: SimpleChange): void {
      if(this.currentEditPosition == null) {
         return;
      }

      const oldColInfo = tableChanges.previousValue.colInfos[this.currentEditPosition.col];
      const newIndex = tableChanges.currentValue.colInfos
         .findIndex((info: ColumnInfo) => info.name === oldColInfo.name);

      if(newIndex >= 0) {
         const row = this.currentEditPosition.row;
         const col = newIndex;
         this.currentEditPosition = new EditCellPosition(this.table as EmbeddedTableAssembly);
         this.currentEditPosition.setCurrentPosition(row, col);
      }
      else {
         this.currentEditPosition = null;
      }
   }

   private updateDimensions(): void {
      this.refreshTableHeight();
      this.visibleRows = Math.ceil(this.tableDataHeight / this.TABLE_CELL_HEIGHT);

      if(this.tableData) {
         this.visibleRows = Math.min(this.visibleRows,
                                     this.tableData.endRow - this.tableData.startRow);
      }

      this.updateHorizontalWidth();
      this.verticalScrollHandler(); // viewport size may have changed.
   }

   verticalScrollHandler(): void {
      const currentRow = Math.floor(this.scrollY / this.TABLE_CELL_HEIGHT);
      this.updateRowRange();

      if(this.tableData && (currentRow + this.visibleRows > this.tableData.endRow ||
                            currentRow < this.tableData.startRow))
      {
         const unseenHalf = Math.floor((this.BLOCK_SIZE - this.visibleRows) / 2);
         const end = Math.min(this.table.totalRows, currentRow + this.visibleRows + unseenHalf);
         const start = Math.max(0, end - this.BLOCK_SIZE);

         // Check in case the browser is displaying the table strangely and tries to fetch
         // the same data block. Firefox would sometimes do this with a large table when scrolled
         // to the bottom of the view.
         if(start !== this.tableData.startRow && end !== this.tableData.endRow) {
            this.targetStartRow = start;
            this.updateData(start);
         }
      }

      this.cd.detectChanges();
   }

   pillarScroll(): void {
      const scrollLeftHeightPillar = this.tableDataHeightPillar.nativeElement.scrollLeft;

      // Transform table data horizontal scroll into host scroll.
      if(scrollLeftHeightPillar !== 0) {
         const scrollLeftHost = this.hostRef.nativeElement.scrollLeft;
         this.renderer.setProperty(this.tableDataHeightPillar.nativeElement, "scrollLeft", 0);
         this.renderer.setProperty(this.hostRef.nativeElement, "scrollLeft",
            scrollLeftHeightPillar + scrollLeftHost);
         this.updateHorizontalWidth();
      }
   }

   private updateData(start: number): void {
      this.requestTableData(false, start, this.TABLE_SCROLL_DEBOUNCE_TIME);
   }

   private requestTableData(firstChange = false, start = 0, debounceTime = 0, blockSize = this.BLOCK_SIZE,
                            continuation = false): void
   {
      const fn = () => {
         // modulo to prevent number from getting excessively large.
         tableDataRequestId = (tableDataRequestId + 1) % 1000;
         const event = new WSLoadTableDataEvent(start, blockSize, continuation, firstChange, tableDataRequestId);
         const uri = CONTROLLER_RELOAD_TABLE_DATA + this.table.name;
         this.lastRequestedTableData = event;
         this.worksheetClient.sendEvent(uri, event);
      };

      const key = `Request table data ${this.table.name}`;

      if(debounceTime > 0) {
         this.debounceService.debounce(key, fn, debounceTime, []);
      }
      else {
         this.debounceService.cancel(key);
         fn();
      }
   }

   /**
    * Called by name from CommandProcessor
    * @param {WSLoadTableDataCommand} command
    */
   private processWSLoadTableDataCommand(command: WSLoadTableDataCommand): void {
      if(this.lastRequestedTableData == null ||
         command.requestId !== this.lastRequestedTableData.requestId)
      {
         // Delayed/out-of-date command, ignore.
         return;
      }

      this.lastRequestedTableData = null;

      if(this.tableData != null && command.continuation) {
         this.tableData.endRow = command.tableData.endRow;
         this.tableData.loadedRows.push(...command.tableData.loadedRows);
         this.tableData.completed = command.tableData.completed;
      }
      else {
         this.tableData = command.tableData;
      }

      if(!this.tableData.completed) {
         const loadedRowCount = this.tableData.endRow - this.tableData.startRow;
         const remainingBlockSize = this.BLOCK_SIZE - loadedRowCount;
         this.requestTableData(false, this.tableData.endRow, this.FINISH_LOADING_TABLE_DEBOUNCE_TIME,
            remainingBlockSize, true);
      }

      if(this.currentEditPosition && !!this.tableData) {
         let row = this.currentEditPosition.row - this.tableData.startRow;

         if(row >= 0 && row < this.tableData.loadedRows.length &&
            this.currentEditPosition.col < this.table.colCount)
         {
            this.beginEditCell(row + this.tableData.startRow, this.currentEditPosition.col, false);
         }
         else {
            this.currentCellData = null;
         }
      }

      this.updateDimensions();
      this.cd.markForCheck();
   }

   private updateRowRange(): void {
      if(this.contentInit) {
         const visibleRowsExact = this.tableDataHeight / this.TABLE_CELL_HEIGHT;
         const beginRowExact = this.scrollY / this.TABLE_CELL_HEIGHT;
         const beginRowLowerBound = Math.floor(beginRowExact);
         const endRowUpperBound = Math.ceil(beginRowExact + visibleRowsExact);
         const endRow = Math.min(endRowUpperBound, this.table.totalRows);
         const boundedStartRow = Math.min(beginRowLowerBound, endRow);

         // Increment 1 to switch from 0-indexed rows to 1-indexed rows.
         const startRow = Math.min(boundedStartRow + 1, endRow);
         this.onRowRangeChange.emit(new Range(startRow, endRow));

         if(this.tableData != null) {
            const start = Math.max(beginRowLowerBound - this.tableData.startRow, 0);
            const end = Math.min(endRow, this.tableData.endRow) - 1 - this.tableData.startRow;
            this.rowIndexRange = new Range(start, end);
         }
      }
   }

   private populateWidths(): void {
      this.columnRightPositions = [];
      this.tableWidth = 0;

      for(let info of this.table.colInfos) {
         this.tableWidth += info.width;
         this.columnRightPositions.push(this.tableWidth);
      }
   }

   dragLeave(event: DragEvent, table: HTMLElement): void {
      if(event.relatedTarget != null && !table.contains(event.relatedTarget as HTMLElement)) {
         this.dropColumnIndicator = null;
         this.cd.detectChanges();
      }
   }

   dragoverTable(event: DragEvent, index: number): void {
      const canDropColumns = this.table.canDropAssetColumns;
      const canReorderColumns =
         this.dragService.get("runtimeId") === this.worksheetClient.runtimeId &&
         this.dragService.get("columnsOfTable") === this.table;

      // it's risky and not significant to update sql string when change column order,
      // do don't support change order for sql edited table(59268).
      if(this.table.tableClassType == "SQLBoundTableAssembly" &&
          (this.table as SQLBoundTableAssembly).sqlEdited)
      {
         return;
      }

      if(canDropColumns || canReorderColumns) {
         const newDropColumnIndicator = this.getDropColumn(event, index);

         if(!this.dropReplace || this.dropReplace && (!this.table || !this.table.isEmbeddedTable()))
         {
            event.preventDefault();
            this.dropColumnIndicator = newDropColumnIndicator;
            this.cd.detectChanges();
         }
         else {
            this.dropReplace = false;
            this.dropColumnIndicator = null;
            this.cd.detectChanges();
         }
      }
   }

   dropColumns(event: DragEvent, index: number): void {
      event.preventDefault();
      GuiTool.clearDragImage();
      let oldIndices: number[] = this.dragService.get("selectedColumnIndices");
      let newIndex: number = this.getDropColumn(event, index);

      if(!this.dropReplace) {
         newIndex = newIndex + 1;
      }

      if(oldIndices === undefined) {
         let data = this.dragService.get(AssetTreeService.getDragName(AssetType.COLUMN)) ||
            this.dragService.get(AssetTreeService.getDragName(AssetType.PHYSICAL_COLUMN));
         let entries = JSON.parse(data);

         if(!(entries instanceof Array)) {
            entries = [entries];
         }

         if(this.dropReplace) {
            this.replaceColumn(newIndex, oldIndices, entries);
         }
         else {
            let insertColsEvent: WSInsertColumnsEvent = new WSInsertColumnsEvent();
            insertColsEvent.setName(this.table.name);
            insertColsEvent.setEntries(entries);
            insertColsEvent.setIndex(newIndex);
            this.onInsertColumns.emit(insertColsEvent);
         }
      }
      else if(oldIndices.length > 0 && (oldIndices[0] !== index || oldIndices.length > 1)) {
         if(this.dropReplace) {
            this.replaceColumn(newIndex, oldIndices);
         }
         else {
            let setColumnIndexEvent = new WSSetColumnIndexEvent();
            setColumnIndexEvent.setName(this.table.name);
            setColumnIndexEvent.setOldIndices(oldIndices);
            setColumnIndexEvent.setNewIndex(newIndex);
            this.worksheetClient.sendEvent(CONTROLLER_SET_COLUMN_INDEX, setColumnIndexEvent);
         }
      }

      this.dropColumnIndicator = undefined;
   }

   replaceColumn(newIndex: number, oldIndices: number[], entries?: AssetEntry[]): void {
      if(this.table.isCrosstabColumn(this.table.colInfos[newIndex])) {
         return;
      }

      let replaceColumnsEvent = new WSReplaceColumnsEvent();
      replaceColumnsEvent.setTableName(this.table.name);
      replaceColumnsEvent.setIndex(newIndex);
      replaceColumnsEvent.setTargetColumn(this.table.colInfos[newIndex].name);
      replaceColumnsEvent.setColumnIndices(oldIndices);
      replaceColumnsEvent.setEntries(entries);
      this.onReplaceColumns.emit(replaceColumnsEvent);
   }

   isCrosstabColumn(index: number): boolean {
      return this.table.isCrosstabColumn(this.table.colInfos[index]);
   }

   getDropColumn(event: DragEvent, index: number): number {
      if(index === this.table.colCount) {
         return index - 1;
      }

      let dropIndex = index;
      let clientWidth = event.target["clientWidth"];

      if(event.offsetX <= 10) {
         dropIndex = index - 1;
         this.dropReplace = false;
      }
      else if(event.offsetX < clientWidth - 10) {
         this.dropReplace = true;
      }
      else if(event.offsetX >= clientWidth - 10) {
         this.dropReplace = false;
      }

      return dropIndex;
   }

   refreshTableHeight(): void {
      if(!this.tableData || !this.hostRef) {
         return;
      }

      const loadedRows = this.tableData.endRow - this.tableData.startRow;
      const height = this.hostRef.nativeElement.clientHeight;
      this.tableHeight =
         Math.min(this.HEADER_CELL_HEIGHT + this.TABLE_CELL_HEIGHT * loadedRows, height);
   }

   oozColumnMousemove(event: MouseEvent, colInfo: ColumnInfo): void {
      this.onOozColumnMouseEvent.emit({event, colInfo});
   }

   oozColumnMouseLeave(event: MouseEvent): void {
      this.onOozColumnMouseEvent.emit({event, colInfo: null});
   }

   dragHeader(event: DragEvent, index: number): void {
      if(!this.headerDraggable(this.table.colInfos[index])) {
         return;
      }

      Tool.setTransferData(event.dataTransfer, {});
      let selectedColumnIndices: number[] = [...this.selectedHeaderIndices];
      this.dragService.put("selectedColumnIndices", selectedColumnIndices);
      this.dragService.put("columnsOfTable", this.table);
      this.dragService.put("runtimeId", this.worksheetClient.runtimeId);
      let columns = [];
      selectedColumnIndices.forEach(columnIndices => columns.push(this.table.colInfos[columnIndices].name));

      if(columns.length > 0) {
         const elem = GuiTool.createDragImage(columns);
         GuiTool.setDragImage(event, elem, this.zone, this.domService);
      }
   }

   updateCanRemoveSelectedHeaders(): void {
      if(this.selectedHeaderIndices.length === 0) {
         this.canRemoveSelectedHeaders = false;
         return;
      }

      // only expression column can be removed on snapshot
      if(this.table.isSnapshotTable()) {
         if(this.selectedHeaderIndices
            .filter(i => !this.table.colInfos[i].ref.expression).length > 0)
         {
            this.canRemoveSelectedHeaders = false;
            return;
         }
      }

      // Filter removeable columns
      let removeableColumnIndices = this.selectedHeaderIndices.filter((i) => {
         return this.table.colInfos[i].ref.expression ||
            !(this.table instanceof ComposedTableAssembly);
      });
      // Find leftover columns
      let leftoverColumns = this.table.info.privateSelection.filter((ref) => {
         return removeableColumnIndices
            .find((i) => ColumnRef.equal(ref, this.table.colInfos[i].ref)) == undefined;
      });

      this.canRemoveSelectedHeaders = removeableColumnIndices.length > 0 &&
         leftoverColumns.length > 0;
   }

   leaveBaseColumns(): boolean {
      if(this.selectedHeaderIndices.length === 0) {
         return true;
      }

      // Filter removeable columns
      let removeableColumnIndices = this.selectedHeaderIndices.filter((i) => {
         return this.table.colInfos[i].ref.expression ||
            !(this.table instanceof ComposedTableAssembly);
      });
      // Find leftover columns
      let leftoverColumns = this.table.info.privateSelection.filter((ref) => {
         return removeableColumnIndices
            .find((i) => ColumnRef.equal(ref, this.table.colInfos[i].ref)) == undefined;
      });

      return !this.table.isWSEmbeddedTable() || !!leftoverColumns.find((column) => !column.expression);
   }

   mousedownHeader(event: MouseEvent, index: number): void {
      this.simpleHeaderClick = false;

      if(event.ctrlKey) {
         let indexLocation = this.selectedHeaderIndices.indexOf(index);

         if(indexLocation === -1) {
            this.selectedHeaderIndices.push(index);
         }
         else if(event.button !== 2) {
            this.selectedHeaderIndices.splice(indexLocation, 1);
         }
      }

      if(event.shiftKey) {
         if(this.primarySelectedHeader != undefined && this.primarySelectedHeader !== index) {
            let start = Math.min(this.primarySelectedHeader, index) + 1;
            let end = Math.max(this.primarySelectedHeader, index);

            for(let i = start; i < end; i++) {
               if(this.selectedHeaderIndices.indexOf(i) === -1) {
                  this.selectedHeaderIndices.push(i);
               }
            }
         }
      }

      if(!event.ctrlKey && event.shiftKey) {
         if(this.selectedHeaderIndices.indexOf(index) === -1) {
            this.selectedHeaderIndices.push(index);
         }
      }
      else if(!event.ctrlKey && !event.shiftKey) {
         if(this.selectedHeaderIndices.indexOf(index) !== -1) {
            this.simpleHeaderClick = true;
         }
         else {
            this.selectedHeaderIndices = [];
            this.selectedHeaderIndices.push(index);
         }
      }

      this.primarySelectedHeader = index;
      this.updateCanRemoveSelectedHeaders();
   }

   clickHeader(event: MouseEvent, index: number, headerRef: ElementRef): void {
      if(selectingHeaderSource(event)) {
         const header = this.table.colInfos[index];
         this.selectColumnSource(header, headerRef);
         return;
      }

      if(this.simpleHeaderClick) {
         this.selectedHeaderIndices = [];
         this.selectedHeaderIndices.push(index);
      }

      this.updateCanRemoveSelectedHeaders();
   }

   headerInputKeyup(event: KeyboardEvent): void {
      const value = (<HTMLInputElement> event.target).value;
      const keyCode = Tool.getKeyCode(event);

      if(keyCode === 27) { // escape
         this.editHeaderIndex = null;
         this.headerDisableSubmitOnBlur = true;
         this.inputEditHeader.nativeElement.blur();
      }
      else if(keyCode === 13) { // enter
         this.updateHeaderAlias(value);
         this.headerDisableSubmitOnBlur = true;
         this.editHeaderIndex = null;
      }
      else if(keyCode === 9) { // tab
         event.preventDefault();
         this.updateHeaderAlias(value);
         this.headerDisableSubmitOnBlur = true;
         const offset = event.shiftKey ? -1 : 1;
         const newIndex = Tool.mod(this.editHeaderIndex + offset,
            this.table.colInfos.length);
         this.startEditHeader(newIndex);
      }
      else if(this.editHeaderControl.errors) {
         if(this.editHeaderControl.errors["calcSpecialCharacters"]) {
            this.notifications.danger("_#(js:formula.editor.charValid)");
         }
      }
   }

   public headerInputBlur(): void {
      if(this.editHeaderArtificialBlur) {
         this.editHeaderArtificialBlur = false;
         return;
      }

      if(!this.headerDisableSubmitOnBlur) {
         this.updateHeaderAlias(this.inputEditHeader.nativeElement.value);
      }

      this.headerDisableSubmitOnBlur = false;
      this.editHeaderIndex = null;
   }

   private updateHeaderAlias(newAlias: string): void {
      newAlias = newAlias.trim();
      const colInfo = this.table.colInfos[this.editHeaderIndex];

      if(this.editHeaderControl.valid && !!colInfo && newAlias !== colInfo.header) {
         const event = new WSRenameColumnEvent();
         event.setTableName(this.table.name);
         event.setNewAlias(newAlias);
         event.setColumnName(colInfo.name);
         const URI = CONTROLLER_RENAME_COLUMN_VALIDATOR +
            Tool.byteEncode(this.worksheetClient.runtimeId);

         if(colInfo.ref.expression && FormValidators.matchCalcSpecialCharacters(newAlias)) {
            setTimeout(() =>
                       ComponentTool.showMessageDialog(this.modalService, "_#(js:Warning)",
                                                       "_#(js:formula.editor.charValid)")
                       , 0);
            return;
         }

         this.modelService.sendModel<WSRenameColumnEventValidator>(URI, event)
            .subscribe((res) => {
               let promise = Promise.resolve(null);

               if(!!res.body) {
                  const validator = res.body;

                  /** Blur active elements to avoid this angular issue:
                   * {@link https://github.com/angular/angular/issues/16820} */
                  if(this.document.activeElement) {
                     this.document.activeElement.blur();
                  }

                  this.zone.run(() => {
                     promise = this.confirm(validator.modifyDependencies);
                  });

                  promise = promise.then((val: boolean) => {
                     event.setModifyDependencies(val);
                  }, () => {});
               }

               promise.then(() =>
                  this.worksheetClient.sendEvent(CONTROLLER_RENAME_COLUMN, event)
               );
            });
      }
      else if(this.editHeaderControl.errors) {
         if(this.editHeaderControl.errors["calcSpecialCharacters"]) {
            this.notifications.danger("_#(js:formula.editor.charValid)");
         }
      }
   }

   startEditHeader(index: number, focus: boolean = true): void {
      // As a side effect of Bug #23507, header columns will not update unless the query is re-run
      // or the table mode is changed.
      if(this.table.isRuntime() && !this.table.isWSEmbeddedTable()) {
         ComponentTool.showConfirmDialog(this.modalService, "_#(js:Info)",
            "_#(js:composer.ws.runtimeMode.renameColumn.message)").then(value => {
               if(value === "ok") {
                  const event = new WSAssemblyEvent();
                  event.setAssemblyName(this.table.name);
                  this.worksheetClient.sendEvent(TABLE_MODE_SOCKET_URI + "default", event);
               }
         });

         return;
      }

      const header = this.table.colInfos[index].name != "" ? this.table.colInfos[index].header : "";
      this.editHeaderIndex = index;
      this.editHeaderControl.reset(header);

      if(focus) {
         setTimeout(() => {
            if(this.document.activeElement === this.inputEditHeader.nativeElement) {
               this.editHeaderArtificialBlur = true;
               this.inputEditHeader.nativeElement.blur();
            }

            this.inputEditHeader.nativeElement.focus();
         }, 0);
      }

      this.inputEditHeader.nativeElement.select();
   }

   deleteColumns(): void {
      if(this.selectedHeaderIndices.length === 0) {
         return;
      }

      if(!this.leaveBaseColumns()) {
         ComponentTool.showMessageDialog(this.modalService, "_#(js:Warning)",
            "_#(js:composer.ws.deleteColumns)");
         return;
      }

      let columns: ColumnRef[] = [];

      for(let selectedIndex of this.selectedHeaderIndices) {
         columns.push(this.table.colInfos[selectedIndex].ref);
      }

      let event = new WSDeleteColumnsEvent();
      event.setTableName(this.table.name);
      event.setColumns(columns);

      let params = new HttpParams()
         .set("all", "false");

      this.modelService.sendModel(CONTROLLER_DELETE_COLUMNS_CHECK
         + this.worksheetClient.runtimeId, event, params).subscribe(msg =>
      {
         if(msg.body === "true") {
            ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)",
               "_#(js:common.confirmColumnDependency)",
               {"yes": "_#(js:Yes)",
                  "no": "_#(js:No)",
                  "cancel": "_#(js:Cancel)"}).then(result =>
            {
               if(result == "yes") {
                  params = params.set("all", "true");

                  this.modelService.sendModel(CONTROLLER_DELETE_COLUMNS_CHECK
                     + this.worksheetClient.runtimeId, event, params).subscribe(msg2 =>
                  {
                     ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)",
                        msg2.body as string).then(result2 =>
                     {
                        if(result2 == "ok") {
                           this.worksheetClient.sendEvent(CONTROLLER_DELETE_COLUMNS, event);
                        }
                     });
                  });
               }
               else if(result == "no") {
                  this.worksheetClient.sendEvent(CONTROLLER_DELETE_COLUMNS, event);
               }
            });
         }
         else {
            this.worksheetClient.sendEvent(CONTROLLER_DELETE_COLUMNS, event);
         }
      });
   }

   insertData(index: number, type: "column" | "row", insert: boolean): void {
      let event = new WSInsertDataEvent();
      event.setTableName(this.table.name);
      event.setIndex(index);
      event.setType(type);
      event.setInsert(insert);
      this.worksheetClient.sendEvent(CONTROLLER_INSERT_DATA, event);
      this.keepScroll = true;
   }

   deleteData(index: number): void {
      let event = new WSDeleteDataEvent();
      event.setTableName(this.table.name);
      event.setIndex(index);
      this.worksheetClient.sendEvent(CONTROLLER_DELETE_DATA, event);
      this.keepScroll = true;
   }

   contextmenuRow(event: MouseEvent, relativeRow: number): void {
      if(this.table.isWSEmbeddedTable() && this.table.info.editMode) {
         event.preventDefault();
         event.stopPropagation();

         let options: DropdownOptions = {
            position: {x: event.clientX, y: event.clientY},
            contextmenu: true
         };

         let contextmenu: ActionsContextmenuComponent =
            this.dropdownService.open(ActionsContextmenuComponent, options).componentInstance;
         contextmenu.sourceEvent = event;
         contextmenu.actions = [
            new AssemblyActionGroup([
               {
                  id: () => "worksheet table-row insert-row",
                  label: () => "_#(js:Insert Row)",
                  icon: () => "fa arrow-circle-o-up",
                  enabled: () => true,
                  visible: () => true,
                  action: () => this.insertData(this.tableData.startRow + relativeRow, "row", true)
               },
               {
                  id: () => "worksheet table-row append-row",
                  label: () => "_#(js:Append Row)",
                  icon: () => "fa arrow-circle-o-down",
                  enabled: () => true,
                  visible: () => true,
                  action: () => this.insertData(this.tableData.startRow + relativeRow, "row", false)
               },
               {
                  id: () => "worksheet table-row remove-row",
                  label: () => "_#(js:Remove Row)",
                  icon: () => "fa fa-times",
                  enabled: () => this.table && this.table.totalRows > 1,
                  visible: () => true,
                  action: () => this.deleteData(this.tableData.startRow + relativeRow)
               },
            ])];
      }
   }

   contextmenuCell(event: MouseEvent, row: number, col: number): void {
      // show row menu instead of cell for edit mode
      if(this.table.isWSEmbeddedTable() && this.table.info.editMode) {
         return;
      }

      event.preventDefault();
      event.stopPropagation();

      let options: DropdownOptions = {
         position: {x: event.clientX, y: event.clientY},
         contextmenu: true
      };

      let contextmenu: ActionsContextmenuComponent =
         this.dropdownService.open(ActionsContextmenuComponent, options).componentInstance;
      contextmenu.sourceEvent = event;
      contextmenu.actions = [
         new AssemblyActionGroup([
            {
               id: () => "worksheet table-cell copy",
               label: () => "_#(js:Copy)",
               icon: () => "fa arrow-circle-o-up",
               enabled: () => true,
               visible: () => true,
               action: () => this.copy(row, col)
            }
         ])
      ];
   }

   private copy(row: number, col: number) {
      const data = this.tableData.loadedRows[row][col];
      const textArea = document.createElement("textarea");
      textArea.style.position = "fixed";
      textArea.style.top = "0";
      textArea.style.left = "0";
      textArea.style.width = "2em";
      textArea.style.height = "2em";
      textArea.style.padding = "0";
      textArea.style.border = "none";
      textArea.style.outline = "none";
      textArea.style.boxShadow = "none";
      textArea.style.background = "transparent";

      textArea.value = data;

      document.body.appendChild(textArea);
      textArea.focus();
      textArea.select();

      document.execCommand("copy");
      document.body.removeChild(textArea);
   }

   /**
    * Effectively an imperative variant of ngModel.
    * Removes the problems associated with waiting a tick and then performing operations
    * on the data.
    * @param value the value of the current cell.
    */
   private set currentCellData(value: string | null) {
      this.renderer.setProperty(this.inputEditCell.nativeElement, "value", value ? value : "");
   }

   private get currentCellData(): string {
      return this.inputEditCell != null ? this.inputEditCell.nativeElement.value : "";
   }

   isColumnEditable(col: number): boolean {
      if(col >= this.table.colCount) {
         console.error("column out of bounds");
         return false;
      }

      return this.table.isWSEmbeddedTable() && this.table.info.editMode &&
         !this.table.colInfos[col].ref.expression;
   }

   /** Edit cell through mouse interaction. */
   mousedownEditCell(event: MouseEvent, relativeRow: number, col: number): void {
      if(event.button === 0 && this.isColumnEditable(col)) {
         event.preventDefault();

         if(this.currentEditPosition && this.inputEditCell) {
            this.editTableData(this.currentCellData,
               this.currentEditPosition.row, this.currentEditPosition.col);
         }

         this.beginEditCell(this.tableData.startRow + relativeRow, col);
      }
   }

   /**
    * Begin editing the cell at the given position.
    *
    * @param row the row of the cell
    * @param col the column of the cell
    * @param focus whether or not to focus input
    */
   beginEditCell(row: number, col: number, focus: boolean = true): void {
      if(!this.isColumnEditable(col)) {
         return;
      }

      this.currentEditPosition = new EditCellPosition(this.table as EmbeddedTableAssembly);
      this.currentEditPosition.setCurrentPosition(row, col);
      const adjustedRow = row - this.tableData.startRow;

      if(adjustedRow >= 0 && adjustedRow < this.tableData.loadedRows.length) {
         this.currentCellData = this.tableData.loadedRows[adjustedRow][col];
      }
      else {
         this.currentCellData = null;
      }

      if(focus) {
         const visibleRowsExact = this.tableDataHeight / this.TABLE_CELL_HEIGHT;
         const beginRowExact = this.scrollY / this.TABLE_CELL_HEIGHT;
         const endRowExact = Math.min(beginRowExact + visibleRowsExact, this.table.totalRows);

         // If the row to edit is at least partially out of view, scroll to center the row.
         if(row < beginRowExact || (row + 1) > endRowExact) {
            const viewTopRow = Math.min(row - (visibleRowsExact - 1) / 2, row);
            const scrollTop = this.getRowTopScaled(viewTopRow);
            this.renderer.setProperty(
               this.tableDataContainer.nativeElement, "scrollTop", scrollTop);
         }

         setTimeout(() => {
            if(this.document.activeElement === this.inputEditCell.nativeElement) {
               this.editCellArtificialBlur = true;
               this.inputEditCell.nativeElement.blur();
            }

            this.inputEditCell.nativeElement.focus();
         }, 0);
      }

      this.inputEditCell.nativeElement.select();
   }

   editCellBlur(event: FocusEvent): void {
      if(this.editCellArtificialBlur) {
         this.editCellArtificialBlur = false;
      }
      else if(this.currentEditPosition !== null) {
         this.editTableData(this.currentCellData,
            this.currentEditPosition.row, this.currentEditPosition.col);

         // Wait a tick to avoid modal focus causing changed after checked errors.
         Promise.resolve(null).then(() => this.currentEditPosition = null);
      }
   }

   /** Handle special KeyboardEvents while editing a cell. */
   editKeydown(event: KeyboardEvent): void {
      const keyCode = Tool.getKeyCode(event);

      if(keyCode === 27) { // escape
         this.currentEditPosition = null;
         return;
      }

      const offset: 1 | -1 = event.shiftKey ? -1 : 1;

      // Enter or tab
      if(keyCode === 13 || keyCode === 9) {
         event.preventDefault();
         this.editTableData((<any> event.target).value,
            this.currentEditPosition.row, this.currentEditPosition.col);
         const [oldRow, oldCol] = [this.currentEditPosition.row, this.currentEditPosition.col];

         if(keyCode === 13) {
            this.currentEditPosition.offsetRow(offset);
         }
         else if(keyCode === 9) {
            this.currentEditPosition.offsetCol(offset);
         }

         if(oldRow !== this.currentEditPosition.row || oldCol !== this.currentEditPosition.col) {
            this.beginEditCell(this.currentEditPosition.row, this.currentEditPosition.col);
         }
         else {
            this.currentEditPosition = null;
         }
      }
   }

   getEditTableCellTop(): number {
      let top: number;

      if(this.currentEditPosition != null) {
         const row = this.currentEditPosition.row;
         let startRow: number;

         if(this.tableData != null && this.tableData.startRow <= row &&
            this.tableData.endRow >= row)
         {
            startRow = this.tableData.startRow;
         }
         else {
            startRow = this.targetStartRow;
         }

         top = this.getDataTableTopPosition(startRow) + (row - startRow) * this.TABLE_CELL_HEIGHT;
      }
      else {
         top = 0;
      }

      return top;
   }

   /** Attempt to create and send an edit table data event. */
   editTableData(data: string, row: number, col: number): void {
      const relativeRow = row - this.tableData.startRow;

      if(!!this.tableData && relativeRow >= 0 && relativeRow < this.tableData.loadedRows.length &&
         this.tableData.loadedRows[relativeRow][col] !== data &&
         this.tableData.loadedRows[relativeRow][col] !== undefined)
      {
         const event = new WSEditTableDataEvent();
         event.setTableName(this.table.name);
         event.setEditData(data);
         event.setX(col);
         event.setY(row + 1);
         this.worksheetClient.sendEvent(CONTROLLER_EDIT_TABLE_DATA, event);
      }
   }

   findResizeHandleLeftPosition(columnIndex: number): number {
      return this.columnRightPositions[columnIndex] - this.RESIZE_BORDER_OFFSET;
   }

   isColumnResizeHandleDisplayed(columnIndex: number): boolean {
      const scrollbarLeftEdge = this.horizontalDist - this.scrollbarWidth;
      return this.findResizeHandleLeftPosition(columnIndex) < scrollbarLeftEdge;
   }

   /** Begin resizing a column. */
   startResize(event: MouseEvent, index: number): void {
      event.preventDefault();
      this.document.addEventListener("mousemove", this.moveResizeListener);
      this.document.addEventListener("mouseup", this.endResizeListener);
      this.lastPageX = event.pageX;
      this.resizeColumnIndex = index;
      this.originalLeft = this.columnRightPositions[index] - this.RESIZE_BORDER_OFFSET;
      this.resizeLeft = this.originalLeft;
      this.resizePageXOrigin = event.pageX;
      this.scrollLeft = this.hostRef.nativeElement.scrollLeft;
      let colStart = this.originalLeft - this.table.colInfos[this.resizeColumnIndex].width + this.RESIZE_BORDER_OFFSET;

      let cb = (timestamp: any) => {
         if(this.resizeLeft != undefined) {
            /** Increasing column width & scrolling to the right. */
            if(this.resizeLeft > this.horizontalDist - this.scrollbarWidth)
            {
               this.resizePageXOrigin -= this.SCROLL_OFFSET;
               this.horizontalDist += this.SCROLL_OFFSET;
               this.scrollLeft += this.SCROLL_OFFSET;
               let minLeft = this.MIN_COL_WIDTH + this.originalLeft - this.table.colInfos[this.resizeColumnIndex].width;
               this.resizeLeft = Math.max(this.originalLeft - this.resizePageXOrigin + this.lastPageX, minLeft);
               setTimeout(() => this.renderer.setProperty(this.hostRef.nativeElement, "scrollLeft", this.scrollLeft), 0);
               this.cd.markForCheck();
            }
            /** Decreasing column width & scrolling to the left. */
            else if(this.resizeLeft < this.horizontalDist - this.hostRef.nativeElement.clientWidth + this.MIN_COL_WIDTH && this.scrollLeft > 0) {
               let calculatedOffset = this.scrollLeft - this.SCROLL_OFFSET >= colStart ?
                  this.SCROLL_OFFSET : this.scrollLeft - colStart;
               this.resizePageXOrigin += calculatedOffset;
               this.horizontalDist -= calculatedOffset;
               this.scrollLeft -= calculatedOffset;
               let minLeft = this.MIN_COL_WIDTH + this.originalLeft - this.table.colInfos[this.resizeColumnIndex].width;
               this.resizeLeft = Math.max(this.originalLeft - this.resizePageXOrigin + this.lastPageX, minLeft);
               this.renderer.setProperty(this.hostRef.nativeElement, "scrollLeft", this.scrollLeft);
               this.cd.markForCheck();
            }

            window.requestAnimationFrame(cb);
         }
      };

      window.requestAnimationFrame(cb);
   }

   /** Move while resizing a column. */
   moveResize(event: MouseEvent): void {
      event.preventDefault();
      let minLeft = this.MIN_COL_WIDTH + this.originalLeft - this.table.colInfos[this.resizeColumnIndex].width;
      this.resizeLeft = Math.max(this.originalLeft - this.resizePageXOrigin + event.pageX, minLeft);
      this.lastPageX = event.pageX;
      this.cd.markForCheck();
   }

   /** Finish resizing a column. */
   endResize(event: MouseEvent): void {
      this.document.removeEventListener("mousemove", this.moveResizeListener);
      this.document.removeEventListener("mouseup", this.endResizeListener);
      this.moveResize(event);
      let width = this.resizeLeft - this.originalLeft + this.table.colInfos[this.resizeColumnIndex].width;
      this.resizeLeft = undefined;

      if(width === this.table.colInfos[this.resizeColumnIndex].width) {
         return;
      }

      let resizeEvent = new WSResizeColumnEvent();
      resizeEvent.setTableName(this.table.name);
      resizeEvent.setColumnRef(this.table.colInfos[this.resizeColumnIndex].ref);
      resizeEvent.setWidth(width);
      this.worksheetClient.sendEvent(CONTROLLER_RESIZE_COLUMN, resizeEvent);
   }

   /**
    * Focuses the given header. Uses offsetLeft to scroll the pane so that the new header position
    * matches the old header position, if possible.
    *
    * @param header the header to focus
    * @param offsetLeft the desired left offset of this header
    */
   focusHeader(header: ElementRef, offsetLeft: number): void {
      if(this.contentInit) {
         // Wait a tick so that template bindings are properly applied before elements are modified
         Promise.resolve(null).then(() => {
            const headerOffset = header.nativeElement.offsetLeft;
            this.renderer.setProperty(this.hostRef.nativeElement, "scrollLeft",
               headerOffset - offsetLeft);
         });
      }
   }

   wheelScrollHandler(event: WheelEvent): void {
      if(this.pillarIsMaxHeight() && !event.shiftKey) {
         const ratio = this.getTableHeightFactor();
         let scrollPixels: number;

         if(event.deltaMode !== WheelEvent.DOM_DELTA_PIXEL) {
            const sign = Math.sign(event.deltaY);
            scrollPixels = sign * this.NUM_ROWS_TO_SCROLL_FALLBACK * this.TABLE_CELL_HEIGHT;
         }
         else {
            scrollPixels = event.deltaY;
         }

         const scaledScroll = scrollPixels * ratio;
         let actualScroll: number;

         if(scaledScroll > 0) {
            actualScroll = Math.ceil(scaledScroll);
         }
         else {
            actualScroll = Math.floor(scaledScroll);
         }

         this.scrollTableY(actualScroll);
         event.preventDefault();
      }
   }

   scrollTableY(deltaY: number): void {
      const scrollTop = this.actualScrollTop + deltaY;
      this.renderer.setProperty(this.tableDataContainer.nativeElement, "scrollTop", scrollTop);
   }

   scrollTableX(deltaX: number): void {
      const scrollLeft = this.hostRef.nativeElement.scrollLeft + deltaX;
      this.renderer.setProperty(this.hostRef.nativeElement, "scrollLeft", scrollLeft);
   }

   /**
    * Selects the column's source table and column if it has one.
    *
    * @param colInfo the column to select the source of
    * @param headerRef the element ref of the header
    */
   private selectColumnSource(colInfo: ColumnInfo, headerRef: ElementRef): void {
      if(this.table instanceof ComposedTableAssembly) {
         const entity = colInfo.ref.dataRefModel.entity;

         if(entity != null) {
            const sourceTable = this.worksheet.tables.find((table) => table.name === entity);
            const outerAttribute = colInfo.ref.dataRefModel.attribute;

            if(sourceTable != null) {
               const headerBox = headerRef.nativeElement.getBoundingClientRect();
               const tableDataBox = this.hostRef.nativeElement.getBoundingClientRect();
               const offsetLeft =  headerBox.left - tableDataBox.left;
               this.onSelectColumnSource.emit({sourceTable, outerAttribute, offsetLeft});
            }
         }
      }
   }

   private confirm(text: string): Promise<boolean> {
      return ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)", text,
         {"yes": "Yes", "no": "No"})
         .then((result: string) => {
            return result === "yes";
         });
   }

   private resetScrollTop(): void {
      this.targetStartRow = 0;
      this.renderer.setProperty(this.tableDataContainer.nativeElement, "scrollTop", 0);
   }

   get scrollY(): number {
      const scrollTop = this.actualScrollTop;
      const scrollYPercent = Math.min(
         scrollTop / (this.getPillarHeight() - this.tableDataHeight) || 0, 1);
      const expectedScrollHeight = this.table.totalRows * this.TABLE_CELL_HEIGHT;
      return scrollYPercent * (expectedScrollHeight - this.tableDataHeight);
   }

   get actualScrollTop(): number {
      return this.tableDataContainer ? this.tableDataContainer.nativeElement.scrollTop : 0;
   }

   getDataTableTopPosition(startRow: number): number {
      const actualScrollTop = this.actualScrollTop;
      const expectedScrollTop = this.scrollY;
      const diffScrollTop = expectedScrollTop - actualScrollTop;
      return startRow * this.TABLE_CELL_HEIGHT - diffScrollTop;
   }

   getTableDataWidth(): number {
      return this.tableWidth - this.getTableDataLeftPosition();
   }

   getTableDataLeftPosition(): number {
      const leftmostColumnIndex = this.columnIndexRange == null ? 0 : this.columnIndexRange.start;

      if(leftmostColumnIndex === 0 || this.columnRightPositions.length < leftmostColumnIndex - 1) {
         return 0;
      }
      else {
         return this.columnRightPositions[leftmostColumnIndex - 1];
      }
   }

   isFirstRowEven(): boolean {
      const rangeStart = this.rowIndexRange == null ? 0 : this.rowIndexRange.start;
      return (this.tableData.startRow + rangeStart) % 2 === 0;
   }

   getRowTopScaled(row: number): number {
      const factor = this.getTableHeightFactor();
      return row * this.TABLE_CELL_HEIGHT * factor;
   }

   get tableDataHeight(): number {
      return this.tableHeight != null ? this.tableHeight - this.HEADER_CELL_HEIGHT : 0;
   }
   isTableWaitingForData(): boolean {
      return this.lastRequestedTableData != null;
   }

   getPillarHeight(): number {
      const tableHeight = this.table.totalRows * this.TABLE_CELL_HEIGHT;
      return Math.min(tableHeight, this.MAX_TABLE_HEIGHT);
   }

   private pillarIsMaxHeight(): boolean {
      return this.getPillarHeight() === this.MAX_TABLE_HEIGHT;
   }

   private getTableHeightFactor(): number {
      const actualHeight = this.getPillarHeight();
      const expectedHeight = this.table.totalRows * this.TABLE_CELL_HEIGHT;
      const actualAdjusted = actualHeight - this.tableDataHeight;
      const expectedAdjusted = expectedHeight - this.tableDataHeight;
      let factor: number;

      if(actualAdjusted > 0 && expectedAdjusted > 0) {
         factor = actualAdjusted / expectedAdjusted;
      }
      else {
         factor = 1;
      }

      return factor;
   }

   private getMaxTableHeight(): number {
      // Divide by 2 to better ensure the browser behaves as expected
      return Math.floor(GuiTool.measureBrowserElementMaxHeight() / 2);
   }

   private updateSearch() {
      this.searchMatches = [];
      this.searchIndices = [];
      let currOffset = this.hostRef.nativeElement.scrollLeft;
      let foundFirst = false;

      for(let i = 0; i < this.table.colInfos.length; i ++) {
         const displayName = this.showName ?
            ColumnRef.getCaption(this.table.colInfos[i].ref) : this.table.colInfos[i].header;
         const position = displayName && this.searchQuery ?
            displayName.toLowerCase().indexOf(this.searchQuery.toLowerCase()) : -1;
         this.searchMatches[i] = position;

         if(position != -1) {
            this.searchIndices.push(i);

            if(!foundFirst) {
               //Select first matching column from the current scroll position
               const scrollOffset = i == 0 ? 0 : this.columnRightPositions[i - 1];

               if(scrollOffset >= currOffset) {
                  this.searchIndex = this.searchIndices.length;
                  foundFirst = true;
               }
            }
         }
      }

      if(!foundFirst) {
         if(this.searchIndices.length > 0) {
            this.searchIndex = 1;
         }
         else {
            this.searchIndex = 0;
         }
      }

      this.onSearchResultUpdate.emit([this.searchIndex, this.searchIndices.length]);
   }

   private focusSearchHeader() {
      const colIndex = this.searchIndices[this.searchIndex - 1];
      const scrollOffset = colIndex == 0 ? 0 : this.columnRightPositions[colIndex - 1];
      this.renderer.setProperty(this.hostRef.nativeElement, "scrollLeft", scrollOffset);
   }

   public isSearchTarget(columnIndex: number): boolean {
      return this.searchIndices[this.searchIndex - 1] == columnIndex;
   }

   trackByFn(iterIndex: number, rangeIndex: number): ColumnInfo | undefined {
      return this.table?.colInfos[rangeIndex];
   }

   headerDraggable(colInfo: ColumnInfo): boolean {
      return !colInfo.crosstab || !this.table.info.aggregate;
   }

   getCellLabel(label: string): string {
      return label.replace(/\n/g, "\\n");
   }
}
