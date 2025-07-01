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
   ChangeDetectorRef,
   Directive,
   ElementRef,
   EventEmitter,
   Input,
   NgZone,
   OnChanges,
   OnDestroy,
   OnInit,
   Optional,
   Output,
   QueryList,
   Renderer2,
   SimpleChanges,
   ViewChild,
   ViewChildren
} from "@angular/core";
import { NgbModal, NgbTooltip } from "@ng-bootstrap/ng-bootstrap";
import { Observable, Subscription } from "rxjs";
import { DownloadService } from "../../../../../../shared/download/download.service";
import { Tool } from "../../../../../../shared/util/tool";
import { AssemblyActionGroup } from "../../../common/action/assembly-action-group";
import { FormatInfoModel } from "../../../common/data/format-info-model";
import { HyperlinkModel } from "../../../common/data/hyperlink-model";
import { Point } from "../../../common/data/point";
import { Rectangle } from "../../../common/data/rectangle";
import { TableDataPath } from "../../../common/data/table-data-path";
import { GuiTool } from "../../../common/util/gui-tool";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { SelectionBoxEvent } from "../../../widget/directive/selection-box.directive";
import { FixedDropdownService } from "../../../widget/fixed-dropdown/fixed-dropdown.service";
import { DebounceService } from "../../../widget/services/debounce.service";
import { ScaleService } from "../../../widget/services/scale/scale-service";
import { BaseTableActions } from "../../action/base-table-actions";
import { LoadTableDataCommand } from "../../command/load-table-data-command";
import { UpdateHighlightPasteCommand } from "../../command/update-highlight-paste-command";
import { ContextProvider } from "../../context-provider.service";
import { ChangeVSObjectTextEvent } from "../../event/change-vs-object-text-event";
import { FilterTableEvent } from "../../event/filter-table-event";
import { ResizeTableColumnEvent } from "../../event/resize-table-column-event";
import { ResizeTableRowEvent } from "../../event/resize-table-row-event";
import { TableCellEvent } from "../../event/table-cell-event";
import { FlyoverEvent } from "../../event/table/flyover-event";
import { LoadTableDataEvent } from "../../event/table/load-table-data-event";
import { MaxTableEvent } from "../../event/table/max-table-event";
import { ResizeTableCellEvent } from "../../event/table/resize-table-cell-event";
import { ShowDetailsEvent } from "../../event/table/show-details-event";
import { VSAnnotationModel } from "../../model/annotation/vs-annotation-model";
import { BaseTableCellModel } from "../../model/base-table-cell-model";
import { BaseTableModel } from "../../model/base-table-model";
import { GuideBounds } from "../../model/layout/guide-bounds";
import { ShowHyperlinkService } from "../../show-hyperlink.service";
import { CheckFormDataService } from "../../util/check-form-data.service";
import { ViewerResizeService } from "../../util/viewer-resize.service";
import { AbstractVSObject } from "../abstract-vsobject.component";
import { AdhocFilterService } from "../data-tip/adhoc-filter.service";
import { DataTipService } from "../data-tip/data-tip.service";
import { SelectableObject } from "../selectable-object";
import { DetailDndInfo } from "./detail-dnd-info";
import { SortInfo } from "./sort-info";
import {
   TableCellResizeDialogComponent,
   TableCellResizeDialogResult
} from "./table-cell-resize-dialog/table-cell-resize-dialog.component";
import { VSTableCell } from "./vs-table-cell.component";
import { PopComponentService } from "../data-tip/pop-component.service";
import { RefreshColWidthsCommand } from "../../command/refresh-col-widths-command";
import { PagingControlService } from "../../../common/services/paging-control.service";
import { VSTabService } from "../../util/vs-tab.service";
import { ComponentTool } from "../../../common/util/component-tool";

const TABLE_CHANGE_TITLE_URL: string = "/events/composer/viewsheet/objects/changeTitle";
const TABLE_DETAIL_FORMAT_URI: string = "../api/table/show-details/format-model";
const TABLE_MAX_MODE_URL: string = "/events/vstable/toggle-max-mode";
const TABLE_WIZARD_CHANGE_TITLE_URL: string = "/events/vswizard/preview/changeDescription";

/**
 * Convenient abstract base class for tables types with models that extend BaseTableModel
 */
@Directive()
export abstract class BaseTable<T extends BaseTableModel> extends AbstractVSObject<T>
   implements OnDestroy, SelectableObject, OnInit, OnChanges
{
   @Input() scrollWrapper: boolean = false;
   @Input() modelTS: number;
   @Input() atBottom: boolean = false;
   @Input() container: Element;
   @Input() goToWizardVisible: boolean = false;
   @Input() guideType: GuideBounds = GuideBounds.GUIDES_NONE;
   @Input()
   set selected(selected: boolean) {
      // moved to editable object container subscription
      // for changed after checked exception
      if(this.viewer && !this.preserveSelection && !selected) {
         const cellSelected = this.model.selectedRegions && this.model.selectedRegions.length;

         if(selected != this._selected) {
            this.clearSelection();
         }

         if(cellSelected && !this.dataTipService.isDataTipVisible(this.getAssemblyName()) &&
            !this.dataTipService.isDataTipVisible(this.model.container) && !GuiTool.isMobileDevice())
         {
            this.dataTipService.unfreeze();
         }
      }

      this._selected = selected;
   }

   get selected(): boolean {
      return this._selected;
   }

   @Output() onTitleResizeMove = new EventEmitter<number>();
   @Output() onTitleResizeEnd = new EventEmitter<any>();
   @Output() onOpenEditPane = new EventEmitter<void>();
   @Output() onOpenConditionDialog = new EventEmitter<T>();
   @Output() onOpenHighlightDialog = new EventEmitter<T>();
   @Output() onLoadData = new EventEmitter<any>();
   @Output() onRefreshFormat = new EventEmitter<MouseEvent>();
   @Output() public maxModeChange = new EventEmitter<{assembly: string, maxMode: boolean}>();
   @Output() detectViewChange = new EventEmitter<any>();
   // WARNING! cells contains "placeholder" cells in crosstabs. When using this variable, you should
   // always filter by VSTableCell.isRendered
   @ViewChildren(VSTableCell) cells: QueryList<VSTableCell>;
   @ViewChild("loadingMessage") loadingMessage: ElementRef;
   static TIMEOUT_LIMIT = 100;
   private static MIN_COL_WIDTH = 10;
   private static MIN_ROW_HEIGHT = 10;
   // Height of data area visible to user.
   tableHeight: number;
   private cellsChangesSubscription: Subscription;
   private resizeSubscription: Subscription;
   private safari: boolean = GuiTool.isSafari();
   showHints: boolean = false;

   /**
    * Common to child classes and used in template
    */
   totalColWidth: number = 0;
   scrollY: number = 0;
   scrollX: number = 0;
   currentRow: number = 0;
   lastVisibleRow = 0;
   currentCol: number = 0;
   approxVisibleCols: number = 0;
   loadedRows: { start: number, end: number };
   tooltipString: string;
   private scaleContainer: Element;

   // Indicates a cell is clicked and selected. Used to determine if hovering
   // on a cell should cause a flyover or if it should be ignored
   flyoverCellSelected: boolean = false;

   // Width of vertical scrollbars for vertical scroll wrapper
   scrollbarWidth: number = 0;

   // flags that indicate whether the table is scrollable vertically / horizontally
   private vScrollable: boolean = true;
   private hScrollable: boolean = true;

   // flag that indicate whether the table is in binding mode
   isBinding: boolean = false;

   get borderDivHeight(): number {
      return this.getObjectHeight();
   }

   abstract get colResize(): ElementRef;

   abstract get rowResize(): ElementRef;

   abstract get resizeLine(): ElementRef;

   protected openWizardEnabled(): boolean {
      return this.goToWizardVisible && this.model.editedByWizard;
   }

   // top and height for table wrapper
   get tableWrapperHeight(): number {
      let height = this.getObjectHeight();

      if(!this.isBinding && (!this.viewer || this.model.titleVisible)) {
         //wrapper height should subtract wrapper top, which is titleFormat.height - 1
         height -= this.model.titleFormat.height - 1;
      }

      return height;
   }

   get tableDataWrapperHeight(): number {
      return this.tableWrapperHeight - this.getHeaderHeight();
   }

   get tableWrapperTop(): number {
      let top = 0;

      if((!this.isBinding || this.vsWizardPreview) && (!this.viewer || this.model.titleVisible)) {
         top = this.model.titleFormat.height - 1;
      }

      return top;
   }

   get titleZIndex(): number | undefined {
      if(this.viewer) {
         if(!!this.model && !!this.model.titleFormat && !!this.model.titleFormat.border &&
            !!this.model.titleFormat.border.bottom &&
            !this.model.titleFormat.border.bottom.startsWith("0px none "))
         {
            return 100;
         }
      }

      return undefined;
   }

   // To be implemented as a view child in subclasses and used to get the bounding
   // client rectangle of the whole table
   abstract tableContainer: ElementRef;
   abstract verticalScrollWrapper: ElementRef;
   abstract horizontalScrollWrapper: ElementRef;
   abstract verticalScrollTooltip: NgbTooltip;
   abstract horizontalScrollTooltip: NgbTooltip;

   /**
    * Common to child classes and not used in template
    */
   protected initialMousePos: number = -1;
   protected initialCellDim: number = -1;
   protected resizeCol: number = -1;
   protected resizeRow: number = -1;
   protected resizeListener: () => void;
   protected resizeEndListener: () => void;
   protected scale: number;

   protected _actions: BaseTableActions<T>;
   protected actionSubscription: Subscription;
   protected sortColumnVisible: boolean = true;
   protected subscriptions = Subscription.EMPTY;
   protected scrollTopSubscription = Subscription.EMPTY;
   protected scrollLeftSubscription = Subscription.EMPTY;
   private _selected: boolean;
   preserveSelection: boolean = false;
   private resizingRowHeight: number;
   rowResizeLabel: string = null;
   colResizeLabel: string = null;

   /**
    * Only used in base table (for calculations or command processing)
    */
   private reloadTimer: any;
   // the number of loading commands pending
   private loading: number = 0;
   private loadPending: boolean = false;
   private oheight: number = null;
   public displayColWidths: number[] = [];

   constructor(protected viewsheetClient: ViewsheetClientService,
               protected dropdownService: FixedDropdownService,
               private downloadService: DownloadService,
               protected renderer: Renderer2,
               protected changeDetectorRef: ChangeDetectorRef,
               protected contextProvider: ContextProvider,
               protected formDataService: CheckFormDataService,
               protected debounceService: DebounceService,
               protected scaleService: ScaleService,
               protected hyperlinkService: ShowHyperlinkService,
               private http: HttpClient,
               protected dataTipService: DataTipService,
               protected popComponentService: PopComponentService,
               protected pagingControlService: PagingControlService,
               protected zone: NgZone,
               protected tabService: VSTabService,
               @Optional() protected viewerResizeService: ViewerResizeService,
               protected adhocFilterService: AdhocFilterService)
   {
      super(viewsheetClient, zone, contextProvider, dataTipService);
      this.isBinding = contextProvider.binding;
      this.subscriptions = this.scaleService.getScale().subscribe((scale) => this.scale = scale);
   }

   ngOnInit() {
      this.updateTableHeight();
      this.ngOnChanges(null);

      if(this.viewerResizeService) {
         this.subscriptions.add(this.viewerResizeService.resized.subscribe(() => {
            const key = `${this.model.absoluteName}_resizeListener`;
            this.debounceService.debounce(key, () => {
               if(this.model.maxMode) {
                  this.openMaxMode();
               }
            }, 200, []);
         }));
      }

      this.scrollTopSubscription = this.pagingControlService.scrollTop().subscribe(changed => {
         if(this.pagingControlService.getCurrentAssembly() === this.model.absoluteName) {
            this.verticalScrollHandler(changed);
         }
      });

      this.scrollLeftSubscription = this.pagingControlService.scrollLeft().subscribe(changed => {
         if(this.pagingControlService.getCurrentAssembly() === this.model.absoluteName) {
            this.horizontalScrollHandler(changed);
         }
      });

      this.subscriptions.add(this.tabService.tabDeselected.subscribe(name => {
         if(name == this.model.absoluteName || name == this.model.container) {
            this.clearSelection();
         }
      }));
   }

   ngOnChanges(changes: SimpleChanges) {
      if(!changes || changes.model || changes.modelTS) {
         this.sortColumnVisible = this.isActionVisibleInViewer("Sort Column");
         this.updateDisplayColumnWidth();
         const reload = this.oheight != null && this.oheight != this.model.objectFormat.height;
         this.updateLayout(reload);
         this.oheight = this.model.objectFormat.height;
      }
   }

   openMaxMode(): void {
      this.resetScroll();
      let maxTableEvent = new MaxTableEvent(this.model, true, this.container);
      this.viewsheetClient.sendEvent(TABLE_MAX_MODE_URL, maxTableEvent);
      this.maxModeChange.emit({assembly: this.model.absoluteName, maxMode: true});
      this.dataTipService.hideDataTip();
      this.adhocFilterService.hideAdhocFilter();
      this.model.selectedAnnotations = [];
   }

   closeMaxMode(): void {
      this.resetScroll();
      let dim = GuiTool.getMaxModeSize(this.container);

      if(this.guideType != GuideBounds.GUIDES_NONE) {
         dim.width = 0;
         dim.height = 0;
      }

      let maxTableEvent = new MaxTableEvent(this.model, false, this.container,
         dim.width, dim.height);
      this.viewsheetClient.sendEvent(TABLE_MAX_MODE_URL, maxTableEvent);
      this.maxModeChange.emit({assembly: this.model.absoluteName, maxMode: false});
      this.dataTipService.hideDataTip();
      this.adhocFilterService.hideAdhocFilter();
   }

   protected abstract showPagingControl(): void;

   // calculate tableHeight
   protected abstract updateTableHeight();

   /**
    * Update the table layout after the model has been changed.
    */
   protected abstract updateLayout(loadOnDemand: boolean): void;

   /**
    * Enforce that OnDestroy is implemented in each subclass so they can call cleanup
    */
   public ngOnDestroy() {
      super.ngOnDestroy();
      this.subscriptions.unsubscribe();
      this.scrollTopSubscription.unsubscribe();
      this.scrollLeftSubscription.unsubscribe();
   }

   /**
    * Add a cell to the map of selected cells
    */
   public abstract selectCell(event: MouseEvent, cell: BaseTableCellModel, header?: boolean): void;

   deselectCell(row: number, col: number, map: Map<number, number[]>): void {
      let selectedColumns = map.get(row);
      let index = selectedColumns.indexOf(col);

      selectedColumns.splice(index, 1);

      if(selectedColumns.length == 0) {
         map.delete(row);
      }
      else {
         map.set(row, selectedColumns);
      }
   }

   protected updateScrolls() {
      if(this.horizontalScrollWrapper) {
         this.scrollX = this.horizontalScrollWrapper.nativeElement.scrollLeft;
      }

      if(this.verticalScrollWrapper) {
         let scrollYPercent = this.verticalScrollWrapper.nativeElement.scrollTop /
            (this.actualScrollHeight - this.verticalScrollbarHeight);
         this.scrollY = scrollYPercent * (this.model.scrollHeight - this.verticalScrollbarHeight);

         if(!Tool.isNumber(this.scrollY) || this.scrollY < 0) {
            this.scrollY = 0;
         }
      }
   }

   /**
    * Store the horizontal scroll amount
    *
    * @param event The mouse event that triggered the scroll. Casted to any to access
    *              the scrollLeft property
    */
   public horizontalScrollHandler(event: number) {
      this.scrollX = event;
      this.hScrolled();
      this.showPagingControl();
   }

   public touchHScroll(delta: number) {
      if(this.hScrollable) {
         let scrollH = Math.max(0, this.scrollX - delta);

         if(!this.forbidHScroll(scrollH)) {
            this.scrollX = scrollH;
            setTimeout(() => this.horizontalScrollWrapper.nativeElement.scrollLeft = this.scrollX, 0);
            this.hScrolled();
         }
      }
   }

   private forbidHScroll(scrollH: number): boolean {
      const headerColW: number = [...Array(this.model.headerColCount)]
         .map((_, c) => this.model.colWidths[c])
         .reduce((a, b) => a + b, 0);
      let bodyW: number = this.model.objectFormat.width - headerColW;
      let totalWidth = 0;

      for(let i = 0; i < this.model.colWidths.length; i++) {
         totalWidth += this.model.colWidths[i];
      }

      return bodyW + scrollH > totalWidth;
   }

   private hScrolled() {
      this.checkScroll();

      this.debounceService.debounce("base-table.hscroll", () => {
         this.updateVisibleCols();
         this.updateHorizontalScrollTooltip();
         this.changeDetectorRef.detectChanges();
      }, 400, []);
   }

   /**
    * Calculate the current row defined by the top of the table. If we scroll into a
    * position that contains rows that aren't loaded yet then we need to trigger a
    * reload of the next block of data
    *
    * @param event the mouse event, an 'any' type to support the scrolltop property
    *              that's not listed in the typescript definition
    */
   public verticalScrollHandler(event: number) {
      if(this.totalColWidth <= 0) {
         return;
      }

      if(this.actualScrollHeight - this.verticalScrollbarHeight <= 0) {
         this.scrollY = 0;
      }
      else {
         let scrollYPercent = event /
            (this.actualScrollHeight - this.verticalScrollbarHeight);
         this.scrollY = scrollYPercent *
            (this.model.scrollHeight - this.verticalScrollbarHeight);
      }

      this.vScrolled();
      this.showPagingControl();
   }

   public touchVScroll(delta: number) {
      if(this.vScrollable) {
         let scrollV = Math.min(Math.max(0, this.scrollY - delta),
            this.model.scrollHeight - this.verticalScrollbarHeight);

         if(!this.forbidVScroll(scrollV)) {
            this.scrollY = scrollV;
            this.vScrolled();
         }
      }
   }

   private forbidVScroll(scrollV: number): boolean {
      const headerRowH: number = [...Array(this.model.headerRowCount)]
         .map((_, c) => this.model.headerRowHeights[c])
         .reduce((a, b) => a + b, 0);
      let bodyH: number = this.model.objectFormat.height - headerRowH;
      let totalHeight = this.model.dataRowHeight * this.model.dataRowCount;

      return bodyH + scrollV > totalHeight;
   }

   private vScrolled() {
      if(!Tool.isNumber(this.scrollY)) {
         return;
      }

      this.checkScroll();
      this.updateVisibleRows();

      this.debounceService.debounce("base-table.vscroll", () => {
         // if loading, change detection triggered when table data is loaded
         if(this.loading == 0) {
            this.changeDetectorRef.detectChanges();
         }
      }, 400, []);
   }

   protected updateVisibleRows(loadOnDemand: boolean = true): void {
      if(!this.loadedRows) {
         return;
      }

      /**
       * When a table has wrapped text, the row heights can all be different.
       * In such a case we need a list of the row heights, and to find the current row index
       * need to iterate through the list.
       * This may need to be further optimized. Currently this is done by storing
       * all row positions and doing a binary search through them.
       */
      // for wrapped the approxVisibleRows by average of full table data maybe have great error.
      // so try to precise calculation the data row visible.
      if(this.model.wrapped) {
         this.currentRow = this.getCurrentRow(0, this.model.rowCount - 1);
         this.lastVisibleRow = this.getCurrentRow(this.currentRow, this.model.rowCount - 1,
            this.scrollY + this.tableHeight);
      }
      else {
         this.currentRow = Math.floor(this.scrollY / this.model.dataRowHeight);
         this.lastVisibleRow = Math.ceil((this.scrollY + this.tableHeight) / this.model.dataRowHeight)
            + this.model.headerRowCount;
      }

      if(loadOnDemand) {
         if(this.lastVisibleRow >= this.loadedRows.end &&
            this.loadedRows.end < this.model.rowCount ||
            this.currentRow < this.loadedRows.start ||
            // Loading flag is set when a request is sent to the server to update table data. The
            // flag is unset when this data request is fulfilled and returns. If the table is
            // loading and we scroll away then we should allow another load so the most up to date
            // scroll position is where the table data loads into.
            this.loading > 0)
         {
            let range = Math.floor((this.getBlockSize() - this.getVisibleRows()) / 2);
            let start = Math.max(0, this.currentRow - range);
            this.updateData(start);
         }
         else if(this.loadPending) {
            // Scrolled back into loaded data range, so don't need to fetch data.
            this.cancelDebouncedUpdateData();
         }
      }

      this.updateVerticalScrollTooltip();
   }

   private getVisibleRows(): number {
      return this.lastVisibleRow - this.currentRow;
   }

   protected updateVisibleCols(): void {
      this.currentCol = this.model.headerColCount;
      this.approxVisibleCols = 0;
      const headerColW: number = [...Array(this.model.headerColCount)]
         .map((_, c) => this.model.colWidths[c])
         .reduce((a, b) => a + b, 0);
      let bodyW: number = this.model.objectFormat.width - headerColW;
      let scrollX: number = this.scrollX;

      for(let i = this.model.headerColCount; i < this.model.colWidths.length && scrollX > 0; i++) {
         this.currentCol++;
         scrollX -= this.model.colWidths[i];
      }

      if(scrollX < 0) {
         this.currentCol--;
         bodyW -= scrollX;
      }

      for(let i = this.currentCol; i < this.model.colWidths.length && bodyW > 0; i++) {
         this.approxVisibleCols++;
         bodyW -= this.model.colWidths[i];
      }
   }

   // optimization, don't create table cell if not currently visible
   // @param idx this is the index in the row array, which starts at loadedRows.start
   isRowVisible(idx: number, _cell?: BaseTableCellModel): boolean {
      if(idx < this.model.headerRowCount) {
         return true;
      }

      const firstRow = this.currentRow - this.loadedRows.start;
      const lastRow = this.lastVisibleRow - this.loadedRows.start;
      let rowSpan: number = !!_cell ? _cell.rowSpan : 1;

      if(rowSpan > 1) {
         return !!this.loadedRows &&
            firstRow <= _cell.row + rowSpan &&
            idx <= lastRow + this.model.headerRowCount;
      }
      else {
         return !!this.loadedRows &&
            idx >= firstRow - 1 &&
            idx <= lastRow + this.model.headerRowCount;
      }
   }

   isColVisible(idx: number): boolean {
      if(idx < this.model.headerColCount) {
         return true;
      }

      return idx <= this.currentCol + this.approxVisibleCols;
   }

   /**
    * Binary search for the current row, based off of scrollY.
    *
    * @param start where to start looking
    * @param end   where to end looking
    * @param scrollPos the scroll position to get the row of
    */
   protected getCurrentRow(start: number, end: number, scrollPos = this.scrollY): number {
      let middle = 0;

      while(start <= end) {
         middle = Math.floor((end + start) / 2);

         if(middle == 0) {
            const startPosition: number = this.getRowPosition(start);
            const endPosition: number = this.getRowPosition(end);

            if(startPosition <= scrollPos && scrollPos < endPosition) {
               return start;
            }
            else if(scrollPos >= endPosition) {
               return end;
            }

            return 0;
         }

         const prevPosition: number = this.getRowPosition(middle - 1);
         const position: number = this.getRowPosition(middle);
         const nextPosition: number = this.getRowPosition(middle + 1);

         if(prevPosition <= scrollPos && scrollPos < position) {
            return middle - 1;
         }
         else if(position <= scrollPos && scrollPos < nextPosition) {
            return middle;
         }
         else if(scrollPos == nextPosition) {
            return middle + 1;
         }
         else if(scrollPos > nextPosition) {
            start = middle + 1;
         }
         else if(scrollPos < prevPosition) {
            end = middle - 1;
         }
         else if(nextPosition == undefined) {
            return middle;
         }
      }

      return middle;
   }

   /**
    * Update the display width for tooltip check when colWidths array changes
    */
   updateDisplayColumnWidth(): void {
      this.displayColWidths = this.model.colWidths.concat([]);

      if(!this.model.maxMode && !this.model.shrink) {
         this.updateLastDisplayColumnWidth(this.model.objectFormat.width);
      }
      else if(this.model.maxMode) {
         this.updateLastDisplayColumnWidth(this.model.maxModeOriginalWidth);
      }

      const border = this.model.objectFormat.border.left;

      if(border && border.includes("none")) {
         this.displayColWidths[this.displayColWidths.length - 1] -= 1;
      }

      this.sumColWidths();
   }

   tableCellDisplayWidth(col: number): number {
      return this.displayColWidths[col];
   }

   private updateLastDisplayColumnWidth(tableWidth: number): void {
      const allW = this.model.colWidths.reduce((v1, v2) => v1 + v2, 0);

      if(allW < tableWidth) {
         if(this.displayColWidths[this.displayColWidths.length - 1] > 0) {
            this.displayColWidths[this.displayColWidths.length - 1] += tableWidth - allW;
         }
      }
   }

   /**
    * Sum an array from an
    * @param source
    * @param from
    * @param to
    * @returns {number}
    */
   public static getSelectionWidth(source: number[], from: number, to: number): number {
      if(to < from || source.length === 0) {
         return 0;
      }

      if(from === to) {
         return source[from];
      }

      return source.slice(from, to + 1).reduce((a, b) => a + b);
   }

   /**
    * Update server with changed table title
    */
   public changeTableTitle(title: string): void {
      let event: ChangeVSObjectTextEvent = new ChangeVSObjectTextEvent(
         this.model.absoluteName, title);
      const url = this.contextProvider.vsWizardPreview
         ? TABLE_WIZARD_CHANGE_TITLE_URL
         : TABLE_CHANGE_TITLE_URL;

      this.model.title = title;
      this.viewsheetClient.sendEvent(url, event);
   }

   /**
    * Update server with changed header cell text
    *
    * @param cell the cell model with updated cell data to send to the server
    */
   public changeCellTitle(cell: BaseTableCellModel) {
      let event: ChangeVSObjectTextEvent =
         new ChangeVSObjectTextEvent(this.model.absoluteName, cell.cellData as string);

      this.viewsheetClient.sendEvent(
         "/events/composer/viewsheet/table/changeColumnTitle/" + cell.row + "/" + cell.col, event);
   }

   /**
    * Send a debounced flyover event to the server for a given cell
    */
   public sendFlyover(selectedCells: Map<number, number[]>): void {
      if(this.model.hasFlyover) {
         this.clearOtherSelections();
         this.debounceService.debounce(this.getAssemblyName() + "_sendFlyover", () => {
            this.formDataService.checkTableFormData(this.viewsheetClient.runtimeId,
               this.model.absoluteName, selectedCells,
               () => {
                  const event = new FlyoverEvent(this.getAssemblyName(), selectedCells);
                  this.viewsheetClient.sendEvent("/events/table/flyover", event);
               });
         }, BaseTable.TIMEOUT_LIMIT, []);
      }
   }

   /**
    * Called by an output from a table cell on mouseenter. Checks whether 'cellSelected'
    * is set to determine if the flyover should apply, clears the map of selected cells,
    * then puts this cell into the map. 'cellSelected' is a boolean that should be set
    * when click selecting a flyover cell. It essentially makes normal hover flyover
    * mode behave like flyOnClick mode until the flyover selection is deselected.
    *
    * @param cell the cell that triggered the flyover
    */
   public flyoverCell(cell: BaseTableCellModel, header: boolean = false): void {
      if(!this.flyoverCellSelected) {
         this.clearSelection();
         let map = new Map<number, number[]>();
         map.set(cell.row, [].concat(cell.col));

         if(header) {
            this.model.selectedHeaders = map;
            this.sendFlyover(this.model.selectedHeaders);
         }
         else {
            this.model.selectedData = map;
            this.sendFlyover(this.model.selectedData);
         }

         this.changeDetectorRef.detectChanges();
      }
   }

   /**
    * Clear the current flyover with empty selection map. Should be called from the
    * parent container on the appropriate UI action
    */
   public clearFlyover(force: boolean = false): void {
      if(this.model.hasFlyover &&
         (force || !this.model.isFlyOnClick && !this.flyoverCellSelected))
      {
         this.sendFlyover(new Map());

         if(!this.flyoverCellSelected) {
            this.model.selectedData = null;
         }

         this.changeDetectorRef.detectChanges();
      }
   }

   public mouseLeave(event: MouseEvent) {
      if(event && event.relatedTarget &&
         !(<Element> event.relatedTarget).classList.contains("fixed-dropdown"))
      {
         this.clearFlyover();
      }

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
    * @inheritDoc
    */
   public clearSelection(): void {
      if(this.model.hasFlyover) {
         if(this.flyoverCellSelected) {
            this.clearFlyover(true);
         }

         this.flyoverCellSelected = false;
      }

      this.model.selectedRegions = [];
      this.model.titleSelected = null;
      this.model.selectedHeaders = null;
      this.model.selectedData = null;
      this.model.firstSelectedRow = -1;
      this.model.firstSelectedColumn = -1;
      this.model.lastSelected = null;

      // clear annotations
      this.model.selectedAnnotations = [];
   }

   /**
    * Check if an array contains a column
    * @param columns the column array to check
    * @param column the column to search for
    * @returns {boolean} true if the column is found, false otherwise
    */
   public isColumnSelected(columns: number[], column: number): boolean {
      return columns && columns.find((col) => col === column) != null;
   }

   /**
    * Return if the cell that the annotation model is bound to is loaded
    *
    * @param annotationModel the annotation model with the row and col of the cell
    * @returns {boolean} true if the cell is loaded
    */
   public isCellLoaded(annotationModel: VSAnnotationModel): boolean {
      const row = annotationModel.row;
      const col = annotationModel.col;
      const colCount = this.model.colCount;
      return this.loadedRows &&
         row >= this.loadedRows.start &&
         row <= this.loadedRows.end &&
         col <= colCount;
   }

   /**
    * Determine if a part of the header rows
    * @param row the row to test
    * @returns {boolean} true if part of the header rows
    */
   public isHeaderRow(row: number): boolean {
      return row < this.model.headerRowCount;
   }

   /**
    * Determine if a part of the header columns
    * @param col the column to test
    * @returns {boolean} true if part of the header columns
    */
   public isHeaderCol(col: number): boolean {
      return col < this.model.headerColCount;
   }

   public isInLeftTopTable(row: number, col: number): boolean {
      return this.isHeaderRow(row) && this.isHeaderCol(col);
   }

   public isInLeftBottomTable(row: number, col: number): boolean {
      return !this.isHeaderRow(row) && this.isHeaderCol(col);
   }

   public isInRightTopTable(row: number, col: number): boolean {
      return this.isHeaderRow(row) && !this.isHeaderCol(col);
   }

   public isInRightBottomTable(row: number, col: number): boolean {
      return !this.isHeaderRow(row) && !this.isHeaderCol(col);
   }

   /**
    * Use the selection box client rectangle, compare it to the client rectangle of our cells,
    * get the cells that intersect and for each of them call selectCell. Pass ctrlKey true so
    * the method can add each cell to the previous selection.
    */
   public selectCells(event: SelectionBoxEvent): void {
      const selectionBox = Rectangle.fromClientRect(event.clientRect);

      if(!selectionBox.isEmpty()) {
         this.clearSelection();

         // new MouseEvent() causes problem on ie11
         const mouseEvent: any = {
            ctrlKey: true,
            stopPropagation: () => {}
         };

         this.cells.filter((cell) => cell.isRendered)
             .forEach((cell) => {
                const cellBox = cell.boundingClientRect;

                if(selectionBox.intersects(cellBox)) {
                   this.selectCell(mouseEvent, cell.cell, cell.isHeader);
                }
             });
      }
   }

   /**
    * Event handler attached to the table container. If the mousedown happens directly on the
    * container then clear the selection
    */
   public onDown(event: MouseEvent): void {
      if(event.target === event.currentTarget) {
         this.clearSelection();
      }
   }

   /**
    * Each type of table should define its own method for populating the necessary data
    * structures to display its data. This is called from this base class when a load
    * command is processed so we can wrap the call.
    *
    * @param command the load command to process
    */
   protected abstract loadTableData(command: LoadTableDataCommand): void;

   /**
    * Recalculate the positions of the cells. This should get called when the cells change or when
    * the annotation models change.
    */
   protected abstract positionDataAnnotations(): boolean;

   /**
    * Should be called from the subclass ngOnDestroy method to clean up. Stops any
    * currently running reload tasks
    */
   protected cleanup() {
      super.cleanup();

      if(this.cellsChangesSubscription) {
         this.cellsChangesSubscription.unsubscribe();
         this.cellsChangesSubscription = null;
      }

      clearTimeout(this.reloadTimer);

      if(this.resizeListener) {
         this.resizeListener();
      }

      if(this.resizeEndListener) {
         this.resizeEndListener();
      }
   }

   // manual detection in load table data
   protected isInZone(messageType: string): boolean {
      return messageType != "LoadTableDataCommand";
   }

   /**
    * Process the load command here instead of in each subclass
    *
    * @param command the load command to process
    */
   protected processLoadTableDataCommand(command: LoadTableDataCommand): void {
      // start loading
      this.processAssemblyLoadingCommand(null);
      // load data in the next cycle so the loading would be shown (otherwise it won't be shown
      // until data is fully loaded and rendered).
      this.zone.run(() => {
         setTimeout(() => this.processLoadTableDataCommand0(command), 0);
      });
   }

   protected processLoadTableDataCommand0(command: LoadTableDataCommand): void {
      if(!!command && !!this.model && command.scrollHeight < this.model.scrollHeight &&
         command.scrollHeight < this.scrollY + this.verticalScrollbarHeight)
      {
         this.scrollY = Math.max(0, command.scrollHeight - this.verticalScrollbarHeight);

         if(!!this.verticalScrollWrapper && !!this.verticalScrollWrapper.nativeElement) {
            this.verticalScrollWrapper.nativeElement.scrollTop = this.scrollY;
         }
      }

      this.model.colWidths = command.colWidths;
      this.model.rowCount = command.rowCount;
      this.model.colCount = command.colCount;
      this.model.dataRowCount = command.rowCount;
      this.model.headerRowCount = command.headerRowCount;
      this.model.headerColCount = command.headerColCount;
      this.model.headerRowHeights = command.headerRowHeights;
      this.model.dataRowHeight = command.dataRowHeight;
      this.model.headerRowPositions = command.headerRowPositions;
      this.model.dataRowPositions = command.dataRowPositions;
      this.model.scrollHeight = command.scrollHeight;
      this.model.wrapped = command.wrapped;
      this.model.limitMessage = command.limitMessage;
      this.updateLayout(false);
      this.updateDisplayColumnWidth();

      this.subscribeToCellChanges(() => {
         if(this.positionDataAnnotations()) {
            this.changeDetectorRef.detectChanges();
         }
      });

      if(command.tableCells) {
         const cellArrs = [command.tableCells, command.tableHeaderCells];

         for(let n = 0; n < cellArrs.length; n++) {
            const tableCells = cellArrs[n];

            if(!tableCells) {
               continue;
            }

            for(let r = 0; r < tableCells.length; r++) {
               for(let c = 0; c < tableCells[r].length; c++) {
                  const cell = tableCells[r][c];

                  // optimization, fill in the omitted fields
                  if(cell.cellLabel == null) {
                     cell.cellLabel = cell.cellData;
                  }

                  if(cell.rowSpan == null) {
                     cell.rowSpan = 1;
                  }

                  if(cell.colSpan == null) {
                     cell.colSpan = 1;
                  }

                  cell.row = r + (n == 0 ? command.start : 0);
                  cell.col = c;
               }
            }
         }

         if(Object.getOwnPropertyNames(command.prototypeCache).length > 0) {
            command.tableCells.forEach((cells) => {
               cells.filter((cell) => cell.protoIdx > -1)
                    .forEach((cell) => {
                       const prototype = command.prototypeCache[cell.protoIdx];
                       delete cell.protoIdx;

                       if(prototype != null) {
                          Object.assign(cell, prototype);
                       }
                       else {
                          throw new Error("Cell prototype expected but not found");
                       }
                    });
            });
         }

         this.loadTableData(command);
         this.processClearAssemblyLoadingCommand(null);
         delete command.prototypeCache;
      }

      this.model.runtimeDataRowCount = command.runtimeDataRowCount;
      this.scrollbarWidth = GuiTool.measureScrollbars();

      if(this.loadedRows.start == 0) {
         this.changeDetectorRef.detectChanges();

         if(this.updateScrollable()) {
            this.changeDetectorRef.markForCheck();
         }
      }

      if(this.verticalScrollTooltip.isOpen() && !this.vScrollable) {
         this.verticalScrollTooltip.close();
      }

      if(this.horizontalScrollTooltip.isOpen() && !this.hScrollable) {
         this.horizontalScrollTooltip.close();
      }

      // we could get load data command from other sources
      this.loading = Math.max(0, this.loading - 1);

      if(this.loading == 0) {
         this.changeDetectorRef.detectChanges();
      }

      this.updateLoadingMessage();
      this.onLoadData.emit(this);

      if(this.mobileDevice) {
         this.showHints = true;
         this.changeDetectorRef.detectChanges();
         setTimeout(() => {
            this.showHints = false;
            this.changeDetectorRef.detectChanges();
         }, 1000);
      }
   }

   /**
    * Update the table data and change the heights of empty rows at the top and bottom
    * of the table so our scrollbar will always show the correct size even though the
    * data may not be loaded
    * @param start
    */
   protected updateData(start: number): void {
      this.loadPending = true;
      this.updateLoadingMessage();

      if(this.model.visible || this.dataTipService.isDataTipVisible(this.getAssemblyName()) ||
         this.popComponentService.isPopComponentVisible(this.getAssemblyName()))
      {
         this.debounceService.debounce(this.getUpdateDataDebounceKey(), () => {
            this.loading++;
            this.loadPending = false;
            const rowCount = this.getRowCount(start, this.model.rowCount);
            const event = new LoadTableDataEvent(this.getAssemblyName(), start, rowCount);
            this.viewsheetClient.sendEvent("/events/table/reload-table-data", event);
         }, BaseTable.TIMEOUT_LIMIT * 2, []);
      }
   }

   private getUpdateDataDebounceKey(): string {
      return this.getAssemblyName() + "_updateData";
   }

   private cancelDebouncedUpdateData(): void {
      this.loadPending = false;
      this.updateLoadingMessage();
      this.debounceService.cancel(this.getUpdateDataDebounceKey());
   }

   // direct style manipulation since change detection is not performed
   private updateLoadingMessage() {
      if(this.loadingMessage && this.loadingMessage.nativeElement) {
         this.renderer.setStyle(this.loadingMessage.nativeElement, "display",
                                this.loading > 0 || this.loadPending ? "block" : "none");
      }
   }

   /**
    * Exports the table
    */
   protected exportTable(): void {
      const url = "../export/vs-table/" + Tool.encodeURIPath(this.viewsheetClient.runtimeId) +
         "/" + encodeURIComponent(this.getAssemblyName());
      this.downloadService.download(url);
   }

   /**
    * Show details on the selected table cells
    */
   public showDetails(sortInfo: SortInfo = null, format: FormatInfoModel = null,
                      column: number[] = [], worksheetId: string = null,
                      detailStyle: string = null, dndInfo: DetailDndInfo = null,
                      newColName: string = null, toggleHide: boolean = false): void
   {
      this.preserveSelection = true;
      this.debounceService.debounce(this.getAssemblyName() + "_showDetails", () => {
         const event = new ShowDetailsEvent(this.getAssemblyName(),
            this.model.selectedHeaders, this.model.selectedData, sortInfo, format,
            column, worksheetId, detailStyle, dndInfo, newColName, toggleHide);
         this.viewsheetClient.sendEvent("/events/table/show-details", event);
      }, BaseTable.TIMEOUT_LIMIT, []);
   }

   processLoadPreviewTableCommand(command: any): void {
      this.preserveSelection = false;
   }

   processRefreshColWidthsCommand(command: RefreshColWidthsCommand): void {
      this.model.colWidths = command.colWidths;
   }

   /**
    * For show detail dialog. Get the format model.
    */
   public get formatFunction(): (wsId: string, column: number) => Observable<any> {
      return (wsId: string, column: number): Observable<any> => {
         const params = new HttpParams()
            .set("vsId", Tool.byteEncode(this.viewsheetClient.runtimeId));
         const event = new ShowDetailsEvent(this.getAssemblyName(),
            this.model.selectedHeaders, this.model.selectedData, null, null,
            [column], wsId);

         return this.http.post<FormatInfoModel>(
            TABLE_DETAIL_FORMAT_URI, event, { params: params });
      };
   }

   /**
    * Store data for drag resizing of column widths.
    * @param xPos the starting x position
    * @param cell the cell to change the column width of
    */
   changeColumnWidth(xPos: number, cell: BaseTableCellModel): void {
      if(this.printLayout) {
         return;
      }

      this.initialMousePos = xPos;
      this.initialCellDim = this.getSpanWidth(this.displayColWidths, cell);
      this.resizeRow = cell.row;
      this.resizeCol = cell.col;
      this.scaleContainer = GuiTool.closest(this.tableContainer.nativeElement, ".scale-container");

      if(this.mobileDevice) {
         //init resize line position
         let initPos = this.initResizeLinePosition(xPos, cell);
         this.renderer.setStyle(this.resizeLine.nativeElement, "left", initPos + "px");
         this.renderer.setStyle(this.resizeLine.nativeElement, "visibility", "visible");
         this.colTouchListener(cell);
      }
      else {
         this.colMouseListener(cell);
      }
   }

   protected changeHeaderCellWidth(width: number) {
   }

   private colTouchListener(cell: BaseTableCellModel) {
      // touch move
      this.resizeListener = this.renderer.listen("document", "touchmove", (evt: TouchEvent) => {
         this.colMoveEventHandler(evt, cell);
      });

      // touch end
      this.resizeEndListener = this.renderer.listen("document", "touchend", (evt: TouchEvent) => {
         this.colResizeEndHandler(cell);
      });
   }

   private colMouseListener(cell: BaseTableCellModel) {
      //mouse move
      this.resizeListener = this.renderer.listen("document", "mousemove", (evt: MouseEvent) => {
         this.colMoveEventHandler(evt, cell);
      });

      //mouse up
      this.resizeEndListener = this.renderer.listen("document", "mouseup",
         (evt: MouseEvent) => {
            this.colResizeEndHandler(cell);
         });
   }

   private colMoveEventHandler(evt: any, cell: BaseTableCellModel) {
      // evt is MouseEvent or TouchEvent. can't reference TouchEvent since it is not defined
      // in FF. use any and check for targetTouches instead of 'instanceof TouchEvent'
      let resizeX: number = evt.targetTouches ? evt.targetTouches[0].pageX : evt.pageX;
      let labelY: number = evt.targetTouches ? evt.targetTouches[0].pageY : evt.pageY;


      if(!this.model.resizingCell) {
         this.model.resizingCell = true;
         this.detectViewChange.emit();
      }

      if(this.viewer && this.scaleContainer && !GuiTool.isIE()) {
         resizeX -= this.scaleContainer.getBoundingClientRect().left;
         labelY -= this.scaleContainer.getBoundingClientRect().top;
      }

      this.handleColResizeMove(evt, cell);
      this.renderer.setStyle(this.colResize.nativeElement, "left", resizeX + "px");
      this.renderer.setStyle(this.colResize.nativeElement, "visibility", "visible");

      if(this.mobileDevice) {
         let linePos: number = this.getLinePosition(this.model.colWidths, cell);
         this.renderer.setStyle(this.resizeLine.nativeElement, "left", linePos + "px");
         this.renderer.setStyle(this.resizeLine.nativeElement, "visibility", "visible");
      }

      const spanWidth = this.getSpanWidth(this.model.colWidths, cell);
      this.colResizeLabel = spanWidth + "";
      const label = this.colResize.nativeElement.querySelector(".resize-label");

      if(label) {
         this.renderer.setStyle(label, "top", labelY + "px");
         label.innerHTML = this.colResizeLabel;
      }
   }

   initResizeLinePosition(resizeX: number, cell: BaseTableCellModel): number {
      this.handleColResizeMove0(resizeX, cell);

      return this.getLinePosition(this.model.colWidths, cell);
   }

   private colResizeEndHandler(cell: BaseTableCellModel) {
      this.resizeListener();
      this.resizeEndListener();
      this.renderer.setStyle(this.colResize.nativeElement, "left", "unset");
      this.renderer.setStyle(this.colResize.nativeElement, "right", "0");
      this.renderer.setStyle(this.colResize.nativeElement, "visibility", "hidden");

      if(this.model.resizingCell) {
         this.model.resizingCell = false;
         this.detectViewChange.emit();
      }

      if(this.mobileDevice) {
         this.renderer.setStyle(this.resizeLine.nativeElement, "left", "unset");
         this.renderer.setStyle(this.resizeLine.nativeElement, "visibility", "hidden");
      }

      this.colResizeLabel = null;
      this.sendUpdatedColWidth(cell);
      this.hScrollable = this.isHScrollable();
   }

   /**
    * Column resize move handler.
    */
   protected handleColResizeMove(evt: any, cell: BaseTableCellModel): void {
      // evt is MouseEvent or TouchEvent. can't reference TouchEvent since it is not defined
      // in FF. use any and check for targetTouches instead of 'instanceof TouchEvent'
      let pageX: number = evt.targetTouches  ? evt.targetTouches[0].pageX : evt.pageX;
      this.handleColResizeMove0(pageX, cell);
   }

   private handleColResizeMove0(pageX: number, cell: BaseTableCellModel) {
      const delta = pageX - this.initialMousePos;
      const colSpan = cell.colSpan;
      const col = cell.col;

      if(colSpan > 1) {
         const currentColWidth = this.initialCellDim;
         const newColWidth = Math.max(currentColWidth + delta, BaseTable.MIN_COL_WIDTH * colSpan);
         this.displayColWidths[this.resizeCol] = newColWidth;

         for(let i = col; i < col + colSpan; i++) {
            this.model.colWidths[i] = newColWidth / colSpan;
         }
      }
      else {
         const width = Math.max(this.initialCellDim + delta, BaseTable.MIN_COL_WIDTH);
         this.model.colWidths[this.resizeCol] = this.displayColWidths[this.resizeCol] = width;
         this.resizeHeaderCellWidth(width);
      }
   }

   protected resizeHeaderCellWidth(width: number) {
   }

   /**
    * When resizing a column, send new column widths to the server on mouseup. Finally
    * reset previous mouse location
    */
   protected sendUpdatedColWidth(cell: BaseTableCellModel): void {
      const startCol = cell.col;
      const resizeRow = cell.row;
      const colSpan = cell.colSpan;

      if(this.initialMousePos != -1 && startCol != -1) {
         const end = startCol + colSpan;
         const event = new ResizeTableColumnEvent(this.model.absoluteName,
            resizeRow, startCol, end, this.model.colWidths.slice(startCol, end));

         this.viewsheetClient.sendEvent(
            "/events/composer/viewsheet/table/changeColumnWidth", event);
      }

      this.initialMousePos = -1;
      this.initialCellDim = -1;
      this.resizeCol = -1;
      this.resizeRow = -1;
   }

   /**
    * Store data for drag resizing of row height.
    * @param yPos the starting y position
    * @param cell the cell that's being resized
    * @param row the row to be changed
    */
   changeRowHeight(yPos: number, cell: BaseTableCellModel, row: number): void {
      if(this.printLayout) {
         return;
      }

      this.initialMousePos = yPos;
      const rowHeight = Math.max(cell.cellHeight || 0, this.getRowHeight(cell.row) || 0);
      this.initialCellDim = rowHeight;
      this.resizingRowHeight = rowHeight;
      this.resizeRow = row;

      // mouse move
      this.resizeListener = this.renderer.listen("document", "mousemove", (evt: MouseEvent) => {
         this.rowMoveEventHandler(evt, row);
      });

      // mouse up
      this.resizeEndListener = this.renderer.listen("document", "mouseup", (evt: MouseEvent) => {
         this.rowResizeEndHandler(rowHeight, cell);
      });
   }

   private rowMoveEventHandler(event: MouseEvent, row: number) {
      if(!this.model.resizingCell) {
         this.model.resizingCell = true;
         this.detectViewChange.emit();
      }

      this.handleRowResizeMove(event, row);
      this.renderer.setStyle(this.rowResize.nativeElement, "top", event.pageY + "px");
      this.renderer.setStyle(this.rowResize.nativeElement, "visibility", "visible");

      this.rowResizeLabel = Math.max(BaseTable.MIN_ROW_HEIGHT, this.resizingRowHeight) + "";
      const label = this.rowResize.nativeElement.querySelector(".resize-label");

      if(label) {
         this.renderer.setStyle(label, "left", event.pageX + "px");
         label.innerHTML = this.rowResizeLabel;
      }
   }

   private rowResizeEndHandler(rowHeight: number, cell: BaseTableCellModel) {
      this.resizeListener();
      this.resizeEndListener();

      if(this.model.resizingCell) {
         this.model.resizingCell = false;
         this.detectViewChange.emit();
      }

      if(rowHeight !== this.resizingRowHeight) {
         this.sendUpdatedRowHeight(cell);
         this.vScrollable = this.isVScrollable();
      }

      this.renderer.setStyle(this.rowResize.nativeElement, "top", "unset");
      this.renderer.setStyle(this.rowResize.nativeElement, "bottom", "0");
      this.renderer.setStyle(this.rowResize.nativeElement, "visibility", "hidden");
      this.rowResizeLabel = null;
      this.initialMousePos = -1;
      this.initialCellDim = -1;
      this.resizeRow = -1;
   }

   /**
    * Row resize move handler.
    */
   protected handleRowResizeMove(evt: MouseEvent, row: number): void {
      const delta: number = evt.pageY - this.initialMousePos;
      this.resizingRowHeight = Math.max(this.initialCellDim + delta, BaseTable.MIN_ROW_HEIGHT);
   }

   /**
    * When resizing a row, send new row height to the server on mouseup. Finally
    * reset previous mouse location
    */
   protected sendUpdatedRowHeight(cell: BaseTableCellModel): void {
      if(this.initialMousePos != -1 && this.resizeRow >= 0) {
         const headerRow = cell.row < this.model.headerRowCount;
         const event = new ResizeTableRowEvent(
            this.model.absoluteName,
            0,
            this.getRowCount(this.loadedRows.start, this.model.rowCount),
            this.resizeRow,
            this.calculateSpanHeight(cell, headerRow, this.resizingRowHeight),
            headerRow,
            cell.rowSpan);

         if(this.resizeRow < this.model.headerRowCount) {
            this.model.headerRowHeights[this.resizeRow] = this.resizingRowHeight;
         }
         else {
            this.model.dataRowHeight = this.resizingRowHeight;
         }

         this.resetScroll(false);
         this.viewsheetClient.sendEvent("/events/composer/viewsheet/table/changeRowHeight", event);
      }
   }

   /**
    * Once new values are received reset row resizing.
    */
   protected resetRowResize(): void {
      this.resizeRow = -1;
      this.resizingRowHeight = -1;
   }

   protected addDataPath(path: TableDataPath): void {
      if(this.model.selectedRegions && this.model.selectedRegions.indexOf(path) < 0 && path) {
         this.model.selectedRegions.push(path);
      }
   }

   /**
    * Get actions for hyperlinks based on the cell being clicked and open a
    * dropdown menu with the appropriate actions
    *
    * @param dropdownItems information emitted from base-table-cell to subclasses of base-table
    * @param row           row of selected link cell
    * @param col           col of selected link cell
    */
   protected openClickDropdownMenu(dropdownItems: any) {
      let links: HyperlinkModel[] = dropdownItems.hyperlinks;

      /* allow cell to be selected for further operation (e.g. show details, drill-filter)
      if(links.length == 1) {
         const link = Tool.clone(links[0]);

         if(this.context.preview && !link.targetFrame) {
            link.targetFrame = "previewTab";
         }

         this.hyperlinkService.clickLink(link, this.viewsheetClient.runtimeId,
            this.vsInfo.linkUri);

         return;
      }
      */

      const clickDropdownActions: AssemblyActionGroup[] = [];

      if(this.viewer) {
         clickDropdownActions.push(
            this.hyperlinkService.createHyperlinkActions(dropdownItems.hyperlinks,
            this.vsInfo.linkUri, this.viewsheetClient.runtimeId));
      }

      if(clickDropdownActions.length > 0) {
         this.updateDropdownTooltipProperty(true);
         let xPos = dropdownItems.xPos;
         let yPos = dropdownItems.yPos;
         this.hyperlinkService.createActionsContextmenu(this.dropdownService,
            clickDropdownActions, dropdownItems, xPos, yPos, this.isForceTab());
      }
   }

   get verticalScrollbarHeight(): number {
      if(this.totalColWidth <= 0) {
         return 0;
      }

      let height = this.getObjectHeight() - this.getHeaderHeight();

      if(!this.isBinding && (!this.viewer || this.model.titleVisible)) {
         height -= this.model.titleFormat.height;
      }

      return Math.max(0, height);
   }

   get verticalScrollbarTop(): number {
      let top = this.getHeaderHeight();

      if(!this.isBinding && (!this.viewer || this.model.titleVisible)) {
         top += this.model.titleFormat.height;
      }

      return top;
   }

   /**
    * Calculates the object height with the shrink to fit option in mind
    */
   public getObjectHeight(): number {
      if(this.model.shrink && this.viewer && this.model.scrollHeight < this.tableHeight) {
         // object height subtract table height is the title height + header height
         let height = this.model.objectFormat.height - this.tableHeight +
            // add data row height
            this.model.scrollHeight;

         return this.model.objectHeight = height;
      }
      else {
         return this.model.objectFormat.height;
      }
   }

   /**
    * Calculates the object width with the shrink to fit option in mind
    */
   public getObjectWidth(): number {
      if(this.viewer && this.model.shrink && !this.model.maxMode) {
         const width = Math.min(this.totalColWidth, this.model.objectFormat.width);
         return Math.floor(width);
      }
      else {
         return this.model.objectFormat.width;
      }
   }

   /**
    * Sum col widths.
    */
   protected sumColWidths(): void {
      this.totalColWidth = this.model.colWidths
         .reduce(((total: number, num: number) => total + num), 0);
      this.model.realWidth = this.getObjectWidth();
   }

   /**
    * Calculates the sum of the header heights
    */
   public getHeaderHeight(): number {
      if(this.model.wrapped) {
         return this.model.headerRowPositions[this.model.headerRowCount];
      }
      else {
         return this.model.headerRowHeights
            .reduce((total: number, value: number) => total + value, 0);
      }
   }

   /**
    * Adds the adhoc filter
    */
   protected addFilter(event: MouseEvent, componentRect: ClientRect): void {
      if(this.model.firstSelectedRow == -1 || this.model.firstSelectedRow === undefined ||
         this.model.firstSelectedColumn == -1 || this.model.firstSelectedColumn === undefined)
      {
         return;
      }

      const selectedCell = this.getSelectedCell(event);
      let x: number;
      let y: number;

      if(selectedCell) {
         const selectedCellBounds = selectedCell.boundingClientRect;
         const tableBounds = this.tableContainer.nativeElement.getBoundingClientRect();

         const cellX: number = this.getCellX(selectedCellBounds, tableBounds);
         const cellY: number = this.getCellY(selectedCellBounds, tableBounds);

         x = (cellX > tableBounds.left ? (cellX - tableBounds.left) : cellX) / this.scale;
         y = cellY - tableBounds.top / this.scale;
      }

      const top: number = y ? y : (event.clientY - componentRect.top) / this.scale;
      const left: number = x ? x : (event.clientX - componentRect.left) / this.scale;
      let vsEvent: TableCellEvent = new FilterTableEvent(
         this.model.absoluteName, this.model.firstSelectedColumn, top, left);
      vsEvent.row = this.model.firstSelectedRow;

      this.viewsheetClient.sendEvent("/events/composer/viewsheet/table/addFilter", vsEvent);
   }

   getCellX(selectedCellBounds: ClientRect, tableBounds: ClientRect) {
      let cellX: number;

      if(selectedCellBounds.left + selectedCellBounds.width <= tableBounds.left + tableBounds.width) {
         cellX = selectedCellBounds.left + selectedCellBounds.width / 2;
      }
      else if(selectedCellBounds.left > 0
         && selectedCellBounds.left + selectedCellBounds.width > tableBounds.top + tableBounds.width)
      {
         cellX = selectedCellBounds.left + (tableBounds.width + tableBounds.left - selectedCellBounds.left) / 2;
      }
      else {
         cellX = tableBounds.left + tableBounds.width / 2;
      }

      return cellX;
   }

   getCellY(selectedCellBounds: ClientRect, tableBounds: ClientRect): number {
      let cellY: number;
      let cellHeight = selectedCellBounds.height -
         (tableBounds.top - selectedCellBounds.top + this.model?.titleFormat?.height + this.getHeaderHeight());
      let tableContentHeight = tableBounds.height - this.model?.titleFormat?.height - this.getHeaderHeight();

     if(selectedCellBounds.top < tableBounds.top && cellHeight < tableBounds.height) {
        cellY = selectedCellBounds.top + selectedCellBounds.height - cellHeight / 2;
     }
     else if(selectedCellBounds.top + selectedCellBounds.height <= tableBounds.top + tableBounds.height) {
        cellY = selectedCellBounds.top + selectedCellBounds.height / 2;
     }
     else if(selectedCellBounds.top > tableBounds.top &&
        selectedCellBounds.top + selectedCellBounds.height > tableBounds.top + tableBounds.height)
     {
        cellY = selectedCellBounds.top + (tableBounds.height + tableBounds.top - selectedCellBounds.top) / 2;
     }
     else {
        cellY = tableBounds.top + this.model?.titleFormat?.height + this.getHeaderHeight() + tableContentHeight / 2;
     }

      return cellY;
   }

   protected getSelectedCell(event?: MouseEvent): VSTableCell{
      const selectedCells = this.model.selectedData || this.model.selectedHeaders;
      let selectedCell;

      if(selectedCells) {
         const row = selectedCells.keys().next().value;
         const col = selectedCells.get(row).values().next().value;
         selectedCell = this.cells
         .filter((cell) => cell.isRendered)
         .filter((cell) => cell.selected)
         .find((cell) => {
            return cell.cell.row === row && cell.cell.col === col;
         });
      }
      else if(event){
         selectedCell = this.cells
         .filter((cell) => cell.isRendered)
         .filter((cell) => cell.selected)
         .find((cell) => {
            const rect = Rectangle.fromClientRect(cell.boundingClientRect);
            return rect.contains(event.clientX, event.clientY);
         });
      }

      return selectedCell;
   }

   /**
    * Return the sum of row heights [from, to)
    */
   public getCellHeight(from: number, cell: BaseTableCellModel): number {
      if(cell.cellHeight == null) {
         cell.cellHeight = 0;
         const to = from + cell.rowSpan;

         for(let i = from; i < to; i++) {
            cell.cellHeight += this.getRowHeight(i);
         }
      }

      return cell.cellHeight;
   }

   public getCellHeightStyle(from: number, cell: BaseTableCellModel): number {
      if(this.safari) {
         return null;
      }

      return this.getCellHeight(from, cell);
   }

   /**
    * @inheritDoc
    */
   public getRowHeight(row: number): number {
      if(this.model.rowHeights && this.model.rowHeights[row] != null) {
         return this.model.rowHeights[row];
      }

      if(!this.model.wrapped) {
         if(this.resizeRow > -1 && this.resizeRow === row) {
            return this.resizingRowHeight;
         }
         else if(row < this.model.headerRowCount) {
            return this.model.headerRowHeights[row % this.model.headerRowHeights.length];
         }
         else {
            return this.model.dataRowHeight;
         }
      }

      if(row < this.model.headerRowCount) {
         return this.model.headerRowPositions[row + 1] - this.model.headerRowPositions[row];
      }

      const dataRow: number = row - this.model.headerRowCount;

      return dataRow + 1 < this.model.dataRowPositions.length ?
          this.model.dataRowPositions[dataRow + 1] - this.model.dataRowPositions[dataRow] :
          this.model.dataRowPositions[dataRow] - this.model.dataRowPositions[dataRow - 1];
   }

   /**
    * @inheritDoc
    */
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
            return this.model.dataRowHeight * row;
         }
      }

      if(row < this.model.headerRowCount) {
         return this.model.headerRowPositions[row];
      }

      return this.model.dataRowPositions[row];
   }

   public abstract getDetailTableTopPosition(): number;

   protected openEditPane(): void {
      this.onOpenEditPane.emit();
   }

   protected openConditionDialog(): void {
      this.onOpenConditionDialog.emit(this._model);
   }

   protected openHighlightDialog(): void {
      this.onOpenHighlightDialog.emit(this._model);
   }

   /**
    * Updates the content of the vertical scroll tooltip
    */
   public updateVerticalScrollTooltip(openTooltip: boolean = false) {
      if(this.vScrollable && (openTooltip || this.verticalScrollTooltip.isOpen())) {
         const currentRow = this.model.wrapped ?
            this.getCurrentRow(this.model.headerRowCount, this.model.rowCount - 1) :
            Math.floor(this.scrollY / this.model.dataRowHeight);
         let total = this.model.runtimeDataRowCount ||
            (this.model.dataRowCount - this.model.headerRowCount);
         const more = total < 0 ? "*" : "";

         if(total < 0) {
            total = -total - 1;
         }

         const text = (currentRow + 1) + " (" + total + more + ")";

         // approximate tooltip width
         const tooltipWidth = GuiTool.measureText(text, "10px Roboto") + 10;
         const x = this.verticalScrollWrapper.nativeElement.getBoundingClientRect().right +
            tooltipWidth;
         let placementChanged = false;
         let placement: string;

         // change placement if the tooltip is out of bounds
         if(x <= (window.innerWidth || document.documentElement.clientWidth) && !this.model.maxMode) {
            placement = "right";
         }
         else {
            placement = "left";
         }

         if(this.verticalScrollTooltip.placement !== placement) {
            this.verticalScrollTooltip.placement = placement;
            placementChanged = true;
         }

         this.verticalScrollTooltip.ngbTooltip = text;

         if(this.verticalScrollTooltip.isOpen()) {
            if(placementChanged) {
               // Placement changed, so can't easily modify DOM to edit tooltip. Recreate it instead.
               this.zone.run(() => {
                  this.verticalScrollTooltip.close();
                  this.verticalScrollTooltip.open();
               });
            }
            else {
               // Performance: edit the text node in-place to prevent excessive angular processing.
               const el = document.querySelector("ngb-tooltip-window > .tooltip-inner");

               if(el != null) {
                  el.textContent = text;
               }
            }
         }
         else {
            this.zone.run(() => this.verticalScrollTooltip.open());
         }
      }
      else if(this.verticalScrollTooltip.isOpen()) {
         this.zone.run(() => this.verticalScrollTooltip.close());
      }
   }

   /**
    * Updates the content of the horizontal scroll tooltip
    */
   public updateHorizontalScrollTooltip(openTooltip: boolean = false) {
      if(this.hScrollable && (openTooltip || this.horizontalScrollTooltip.isOpen())) {
         let x = 0;
         let currentCol = 0;

         for(let i = 0; i < this.model.colWidths.length; i++) {
            x += this.model.colWidths[i];
            currentCol = i;

            if(x > this.scrollX) {
               break;
            }
         }

         let scrollableCols = this.model.colCount - this.model.headerColCount;

         // if only one column scrollable, we actually scroll all columns
         if(scrollableCols < 2) {
            scrollableCols = this.model.colCount;
         }

         const text = (currentCol + 1) + " (" + scrollableCols + ")";

         // approximate tooltip height
         const tooltipHeight = GuiTool.measureText(text, "10px Roboto") + 10;
         const y = this.horizontalScrollWrapper.nativeElement.getBoundingClientRect().bottom
            + tooltipHeight;
         let placementChanged = false;
         let placement: string;

         // change placement if the tooltip is out of bounds
         if(y <= (window.innerHeight || document.documentElement.clientHeight)) {
            placement = "bottom";
         }
         else {
            placement = "top";
         }

         if(this.horizontalScrollTooltip.placement !== placement) {
            this.horizontalScrollTooltip.placement = placement;
            placementChanged = true;
         }

         this.horizontalScrollTooltip.ngbTooltip = text;

         if(this.horizontalScrollTooltip.isOpen()) {
            if(placementChanged) {
               // Placement changed, so can't easily modify DOM to edit tooltip. Recreate it instead.
               this.horizontalScrollTooltip.close();
               this.horizontalScrollTooltip.open();
            }
            else {
               // Performance: edit the text node in-place to prevent excessive angular processing.
               const el = document.querySelector("ngb-tooltip-window > .tooltip-inner");

               if(el != null) {
                  el.textContent = text;
               }
            }
         }
         else {
            this.horizontalScrollTooltip.open();
         }
      }
      else if(this.horizontalScrollTooltip.isOpen()) {
         this.horizontalScrollTooltip.close();
      }
   }

   /**
    * Update the scrollable flags
    */
   public updateScrollable(): boolean {
      const vScrollable = this.isVScrollable();
      const hScrollable = this.isHScrollable();
      this.vScrollable = vScrollable;
      this.hScrollable = hScrollable;
      return this.vScrollable != vScrollable || this.hScrollable != hScrollable;
   }

   /**
    * Determines whether it's possible to scroll the table horizontally
    */
   protected isHScrollable(): boolean {
      if(this.horizontalScrollWrapper && this.horizontalScrollWrapper.nativeElement) {
         let childElem = this.horizontalScrollWrapper.nativeElement.firstElementChild;
         let childWidth = parseFloat(childElem.style.width);
         let nativeWidth = parseFloat(this.horizontalScrollWrapper.nativeElement.style.width);
         return childWidth > nativeWidth;
      }

      return false;
   }

   /**
    * Determines whether it's possible to scroll the table vertically
    */
   protected isVScrollable(): boolean {
      if(this.verticalScrollWrapper && this.verticalScrollWrapper.nativeElement) {
         let childElem = this.verticalScrollWrapper.nativeElement.firstElementChild;
         let childHeight = parseFloat(childElem.style.height);
         let nativeHeight = parseFloat(this.verticalScrollWrapper.nativeElement.style.height);
         return childHeight > nativeHeight;
      }

      return false;
   }

   /**
    * Run a callback when the cells query list changes
    */
   protected subscribeToCellChanges(callback: () => void): void {
      if(this.cellsChangesSubscription) {
         this.cellsChangesSubscription.unsubscribe();
      }

      this.cellsChangesSubscription = this.cells.changes.subscribe(() => {
         // after the subscription returns wait a tick since we're in a change detection cycle
         this.zone.runOutsideAngular(() => setTimeout(() => callback()));
      });
   }

   /**
    * Sets the annotation model cell position on the given list of annotations. This position
    * is used as values for the annotation container to position relative to the cell instead
    * of to the assembly
    * @return true if annotations exists
    */
   protected positionAnnotationsToCell(annotations: VSAnnotationModel[],
                                       scrollTop: number, scrollLeft: number): boolean
   {
      let moved = false;

      if(annotations) {
         annotations
            .filter(annotation => !!annotation)
            .forEach(annotation => annotation.cellOffset = null);
      }

      if(annotations && this.cells && this.tableContainer) {
         const tableContainer = this.tableContainer.nativeElement.getBoundingClientRect();

         this.cells.forEach((cell) => {
            annotations
               .filter((annotationModel) => {
                  return cell.cell.row === annotationModel.row &&
                     cell.cell.col === annotationModel.col && cell.isRendered;
               })
               .filter((annotationModel) => !annotationModel.hidden && cell.cell.row <= this.lastVisibleRow)
               .forEach((annotationModel) => {
                  const cellRect = cell.boundingClientRect;
                  const cellTop = (cellRect.top - tableContainer.top + scrollTop) / this.scale;
                  const cellLeft = (cellRect.left - tableContainer.left + scrollLeft) / this.scale;
                  const cellHeight = cellRect.height / this.scale;
                  const cellWidth = cellRect.width / this.scale;
                  const cellRelTop = cellTop + cellHeight / 2;
                  const cellRelLeft = cellLeft + cellWidth / 2;
                  const lineModel = annotationModel.annotationLineModel;

                  // the offset is the amount needed to move to the center of the cell
                  if(lineModel) {
                     const endTop = cellRelTop - lineModel.objectFormat.top;
                     const endLeft = cellRelLeft - lineModel.objectFormat.left;
                     annotationModel.cellOffset = new Point(endLeft, endTop);
                     moved = true;
                  }
               });
         });
      }

      return moved;
   }

   protected processUpdateHighlightPasteCommand(command: UpdateHighlightPasteCommand): void {
      this.model.isHighlightCopied = command.pasteEnabled;
   }

   titleResizeMove(event: any): void {
      this.onTitleResizeMove.emit(event.rect.height);
   }

   titleResizeEnd(): void {
      this.onTitleResizeEnd.emit();
   }

   isForceTab(): boolean {
      return this.contextProvider.composer;
   }

   // Called after scroll event handler and offers a chance to optimize
   protected checkScroll(): void {
   }

   // Actual scroll height may be smaller than the expected scroll height in the model
   // due to browser's height limitations
   get actualScrollHeight(): number {
      let actualScrollHeight = this.model.scrollHeight;

      if(this.verticalScrollWrapper) {
         let childElem = this.verticalScrollWrapper.nativeElement.firstElementChild;

         if(childElem) {
            actualScrollHeight = childElem.getBoundingClientRect().height;
         }
      }

      return actualScrollHeight;
   }

   private getRowCount(start: number, rowCount: number): number {
      return Math.min(rowCount - start, this.getBlockSize());
   }

   public openCellSizeDialog(modalService: NgbModal) {
      const selected = this.getSelectedCell();

      if(!!selected) {
         const cell = Tool.clone(selected.cell);
         let width = Math.max(cell.cellWidth || 0, this.displayColWidths[cell.col] || 0);
         let height = Math.max(cell.cellHeight || 0, this.getRowHeight(cell.row) || 0);

         // remove the padding from the width/height
         width -= cell.colPadding || 0;
         height -= cell.rowPadding || 0;

         const dialog = ComponentTool.showDialog(modalService, TableCellResizeDialogComponent, (result) => {
            this.updateCellSize(result, cell);
         });

         dialog.width = width;
         dialog.height = height;
      }
   }

   /**
    * Update the cell size with the values from the resize dialog. Only send the events if the size
    * actually changed since the events are sent independently of each other and we would generate
    * multiple undo states
    */
   protected updateCellSize(result: TableCellResizeDialogResult, cell: BaseTableCellModel): void {
      const startCol = cell.col;
      const colSpan = cell.colSpan;
      const end = startCol + colSpan;
      let width;

      if(colSpan > 1) {
         width = Math.round(result.width / cell.colSpan);
      }
      else {
         width = result.width;
      }

      const colEvent = new ResizeTableColumnEvent(this.model.absoluteName,
         cell.row, startCol, end, new Array(cell.colSpan).fill(width));
      const headerRow = cell.row < this.model.headerRowCount;
      const height = this.calculateSpanHeight(cell, headerRow, result.height);

      const rowEvent = new ResizeTableRowEvent(
         this.model.absoluteName,
         0,
         this.getRowCount(this.loadedRows.start, this.model.rowCount),
         cell.row,
         height,
         headerRow,
         cell.rowSpan
      );

      this.resetScroll(false);
      const event = new ResizeTableCellEvent(colEvent, rowEvent);
      this.viewsheetClient.sendEvent("/events/composer/viewsheet/table/changeCellSize", event);
   }

   private calculateSpanHeight(cell: BaseTableCellModel, header: boolean, height: number): number {
      const rowSpan = cell.rowSpan;
      const row = cell.row;
      let result = height;

      if(rowSpan > 1) {
         // if the cell spans between header rows and data rows (freehand table) then calculate
         // the cell height to be the exact number we set in the dialog by taking the specified
         // height, subtracting the height of all the rows the cell is spanning, and then applying
         // that height to the single row height we're modifying so the sum of the row heights for
         // that span cell is equal to the value we set
         if(header && row + rowSpan > this.model.headerRowCount) {
            const spanHeight =
               (row + rowSpan - this.model.headerRowCount) * this.model.dataRowHeight;
            result = Math.round((height - spanHeight) / this.model.headerRowCount);
         }
         else {
            result = Math.round(height / rowSpan);
         }
      }

      return result;
   }

   private getBlockSize(): number {
      return Math.max(100, this.getVisibleRows());
   }

   private getSpanWidth(colWidths: number[], cell: BaseTableCellModel) {
      const colSpan = cell.colSpan;
      const col = cell.col;

      return colWidths
         .slice(col, col + colSpan)
         .reduce((a, b) => a + b);
   }

   private getLinePosition(colWidths: number[], cell: BaseTableCellModel): number {
      let col = cell.col;
      let width: number = 0;

      while(col >= 0) {
         width += colWidths[col];
         col--;
      }

      return width;
   }

   public resized(): void {
      super.resized();
      this.updateScrollable();
      this.model.titleFormat.width = this.model.objectFormat.width;
      // tables are OnPush so we need to explicitly call change detection
      // to make sure the table is redrawn to match the new size during resizing
      this.changeDetectorRef.detectChanges();
   }

   // map from (non-header row) index in table array to row number
   indexToRow(idx: number): number {
      return this.loadedRows ? idx + this.loadedRows.start : idx;
   }

   getTooltip(cell: BaseTableCellModel): string {
      if(cell.hyperlinks && cell.hyperlinks.length > 0) {
         let tip = "";

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

      return cell.cellLabel && cell.editorType != "Boolean" ? cell.cellLabel.toString() : "";
   }

   resetScroll(x: boolean = true, y: boolean = true) {
      if(x) {
         this.scrollX = 0;

         if(!!this.horizontalScrollWrapper && !!this.horizontalScrollWrapper.nativeElement) {
            this.horizontalScrollWrapper.nativeElement.scrollLeft = 0;
         }
      }

      if(y) {
         this.scrollY = 0;

         if(!!this.verticalScrollWrapper && !!this.verticalScrollWrapper.nativeElement) {
            this.verticalScrollWrapper.nativeElement.scrollTop = 0;
         }
      }
   }

   protected detectChanges(): void {
      this.changeDetectorRef.detectChanges();
   }

   initDropdownTooltipProperty(): void {
      this.updateDropdownTooltipProperty(this.isTooltipShowing());
   }

   updateDropdownTooltipProperty(exist: boolean): void {
      this.pagingControlService.hasDropdownOrTooltip = exist;
   }

   isTooltipShowing(): boolean {
      let tooltips = document.getElementsByClassName("tooltip-container");
      return tooltips && tooltips.length > 0;
   }

   protected isPopupOrDataTipSource(): boolean {
      return this.dataTipService.isDataTipSource(this.model.absoluteName);
   }
}
