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
   ChangeDetectionStrategy,
   ChangeDetectorRef,
   Component,
   ElementRef,
   EventEmitter,
   Input,
   NgZone,
   OnDestroy,
   Optional,
   Output,
   Renderer2,
   ViewChild
} from "@angular/core";
import { NgbModal, NgbTooltip } from "@ng-bootstrap/ng-bootstrap";
import { Observable } from "rxjs";
import { DownloadService } from "../../../../../../shared/download/download.service";
import { Tool } from "../../../../../../shared/util/tool";
import { AssemblyActionGroup } from "../../../common/action/assembly-action-group";
import { TableDataPath } from "../../../common/data/table-data-path";
import { DataPathConstants } from "../../../common/util/data-path-constants";
import { GuiTool } from "../../../common/util/gui-tool";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { FixedDropdownService } from "../../../widget/fixed-dropdown/fixed-dropdown.service";
import { DebounceService } from "../../../widget/services/debounce.service";
import { ScaleService } from "../../../widget/services/scale/scale-service";
import { CalcTableActions } from "../../action/calc-table-actions";
import { LoadTableDataCommand } from "../../command/load-table-data-command";
import { ContextProvider } from "../../context-provider.service";
import { RichTextDialog } from "../../dialog/rich-text-dialog/rich-text-dialog.component";
import { RichTextService } from "../../dialog/rich-text-dialog/rich-text.service";
import { AddAnnotationEvent } from "../../event/annotation/add-annotation-event";
import { BaseTableCellModel } from "../../model/base-table-cell-model";
import { VSCalcTableModel } from "../../model/vs-calctable-model";
import { ShowHyperlinkService } from "../../show-hyperlink.service";
import { CheckFormDataService } from "../../util/check-form-data.service";
import { ViewerResizeService } from "../../util/viewer-resize.service";
import { VSUtil } from "../../util/vs-util";
import { DataTipService } from "../data-tip/data-tip.service";
import { BaseTable } from "./base-table";
import { VSFormatModel } from "../../model/vs-format-model";
import { PopComponentService } from "../data-tip/pop-component.service";
import { PagingControlService } from "../../../common/services/paging-control.service";
import { PagingControlModel } from "../../model/paging-control-model";
import { VSTabService } from "../../util/vs-tab.service";

@Component({
   selector: "vs-calctable",
   templateUrl: "vs-calctable.component.html",
   styleUrls: ["base-table.scss", "vs-calctable.component.scss"],
   changeDetection: ChangeDetectionStrategy.OnPush
})
export class VSCalcTable extends BaseTable<VSCalcTableModel> implements OnDestroy {
   @Output() onOpenFormatPane = new EventEmitter<VSCalcTableModel>();
   @ViewChild("tableContainer")
   tableContainer: ElementRef;

   public table: BaseTableCellModel[][];
   public tableHeaderRows: BaseTableCellModel[][];
   public tableTranspose: BaseTableCellModel[][];
   public emptyHeaderCells: boolean[][];

   @ViewChild("verticalScrollWrapper") verticalScrollWrapper: ElementRef;
   //scroll wrapper's height and top, will be dynamically calculated in LoadData method
   public verticalScrollWrapperHeight: number = 0;
   public verticalScrollWrapperTop: number = 0;

   @ViewChild("horizontalScrollWrapper") horizontalScrollWrapper: ElementRef;
   @ViewChild("verticalScrollTooltip") verticalScrollTooltip: NgbTooltip;
   @ViewChild("horizontalScrollTooltip") horizontalScrollTooltip: NgbTooltip;

   @ViewChild("colResize") colResize0: ElementRef;
   @ViewChild("rowResize") rowResize0: ElementRef;
   @ViewChild("resizeLine") resizeLine0: ElementRef;

   @ViewChild("lowerTable") lowerTable: ElementRef;
   @ViewChild("rightTopDiv") rightTopDiv: ElementRef;
   @ViewChild("leftBottomDiv") leftBottomDiv: ElementRef;
   rightBorderMin: number = 0;

   get colResize(): ElementRef {
      return this.colResize0;
   }

   get rowResize(): ElementRef {
      return this.rowResize0;
   }

   get resizeLine(): ElementRef {
      return this.resizeLine0;
   }

   topRowHeight: number;

   @Input() set model(model: VSCalcTableModel) {
      this._model = model;
      this.updateLayout(false);
   }

   get model(): VSCalcTableModel {
      return this._model;
   }

   @Input()
   set actions(value: CalcTableActions) {
      if(this.actionSubscription) {
         this.actionSubscription.unsubscribe();
         this.actionSubscription = null;
      }

      this._actions = value;

      if(value) {
         this.actionSubscription = value.onAssemblyActionEvent.subscribe((event) => {
            switch(event.id) {
            case "calc-table edit":
               this.openEditPane();
               break;
            case "calc-table export":
               this.exportTable();
               break;
            case "calc-table multi-select":
               this.model.multiSelect = !this.model.multiSelect;
               break;
            case "calc-table show-details":
               this.showDetails();
               break;
            case "calc-table annotate":
               this.subscriptions.add(this.showAnnotateDialog(event.event).subscribe());
               break;
            case "calc-table filter":
               this.addFilter(event.event,
                  this.tableContainer.nativeElement.getBoundingClientRect());
               break;
            case "calc-table highlight":
               this.openHighlightDialog();
               break;
            case "calc-table conditions":
               this.openConditionDialog();
               break;
            case "calc-table open-max-mode":
               this.openMaxMode();
               break;
            case "calc-table close-max-mode":
               this.closeMaxMode();
               break;
            case "menu actions":
               VSUtil.showDropdownMenus(event.event, this.actions.menuActions, this.dropdownService);
               break;
            case "table cell size":
               this.openCellSizeDialog(this.modalService);
               break;
            case "calc-table show-format-pane":
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

   get actions(): CalcTableActions {
      return <CalcTableActions> this._actions;
   }

   getToolbarActions(): AssemblyActionGroup[] {
      return this.actions.toolbarActions;
   }

   @Output() onEditTable: EventEmitter<VSCalcTableModel> = new EventEmitter<VSCalcTableModel>();

   constructor(viewsheetClient: ViewsheetClientService,
               dropdownService: FixedDropdownService,
               downloadService: DownloadService,
               private modalService: NgbModal,
               renderer: Renderer2,
               protected changeDetectorRef: ChangeDetectorRef,
               protected dataTipService: DataTipService,
               protected popComponentService: PopComponentService,
               contextProvider: ContextProvider,
               formDataService: CheckFormDataService,
               debounceService: DebounceService,
               scaleService: ScaleService,
               hyperlinkService: ShowHyperlinkService,
               pagingControlService: PagingControlService,
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
   }

   protected updateTableHeight(): void {
      let titleH = this.model.titleVisible ? this.model.titleFormat.height : 0;
      this.tableHeight = this.model.objectFormat.height - this.getHeaderHeight() - titleH;
   }

   openMaxMode(): void {
      super.openMaxMode();
      this.updateData(this.loadedRows.start);
   }

   closeMaxMode(): void {
      super.closeMaxMode();
      this.updateData(this.loadedRows.start);
   }

   protected updateLayout(loadOnDemand: boolean): void {
      this.setupVerticalScrollWrapper();
      // Sort not available on calc tables
      this.model.sortInfo = {sortable: false};
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
      this.resetLastColWidth();
   }

   ngOnDestroy(): void {
      super.ngOnDestroy();
      this.subscriptions.unsubscribe();

      if(this.actionSubscription) {
         this.actionSubscription.unsubscribe();
         this.actionSubscription = null;
      }
   }

   setupVerticalScrollWrapper(): void {
      let verticalScrollWrapperHeightThreshold: number = this.model.objectFormat.height -
         this.model.titleFormat.height - this.getHeaderHeight();
      this.verticalScrollWrapperHeight =
         Math.max(this.model.objectFormat.height - this.model.titleFormat.height -
            this.getHeaderHeight(), 0);
      this.verticalScrollWrapperTop = verticalScrollWrapperHeightThreshold > 0 ?
         this.getHeaderHeight() + this.model.titleFormat.height : this.getRowHeight(0);
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

   public getColumnWidthSum(from: number, to: number): number {
      return BaseTable.getSelectionWidth(this.displayColWidths, from, to);
   }

   public getCellWidth(idx: number, cell: BaseTableCellModel): number {
      if(cell.cellWidth == null) {
         if(this.model.colWidths != null && idx < this.model.colWidths.length &&
            this.model.colWidths[idx] == 0 && (!cell.colSpan || cell.colSpan == 1))
         {
            cell.cellWidth = 0;
            return 0;
         }

         const tableWidth = this.getObjectWidth();
         const colSpan = cell.colSpan - 1;
         const vsFormatModel = cell.vsFormatModel;
         let width: number = this.getColumnWidthSum(idx, idx + colSpan);

         // Expand last column to fill the table
         if(tableWidth && idx == this.model.colCount - 1 - colSpan &&
            !this.model.maxMode && !this.model.shrink && width != 0)
         {
            let selectionWidth = BaseTable.getSelectionWidth(this.model.colWidths, 0, idx - 1);

            if(tableWidth - selectionWidth > width) {
               width = tableWidth - selectionWidth;
            }

            // Bug 17102, the last column width should minus right border width
            if(vsFormatModel) {
               const rightBorderWidth = Tool.getMarginSize(vsFormatModel.border.right);

               // Bug 41547, when there is a mix of border styles on the right, the min
               // influences how table width is calculated (on FF).
               // checking rightBorderWidth to maintain same flow before this change.
               // otherwise it would drop into the following else-if, which would cause
               // a cell with null content to be missing (bg lost).
               if(rightBorderWidth) {
                  width -= this.rightBorderMin;
               }
            }
         }

         cell.cellWidth = Math.floor(width);
      }

      return cell.cellWidth;
   }

   public getColGroupColWidths(): number[] {
      // match the getCellWidth()
      return this.displayColWidths.map(w => Math.floor(w));
   }

   public getCellColSpan(cell: BaseTableCellModel, col: number): number | null {
      let colWidths = this.model.colWidths;

      if(cell.displayColSpan === undefined) {
         cell.displayColSpan = cell.colSpan;

         if(cell.displayColSpan != null) {
            for(let i = 0; i < cell.colSpan; i++) {
               if(col + i < colWidths.length && colWidths[col + i] === 0) {
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

   public linkClicked(payload: any): void {
      this.openClickDropdownMenu(payload);
   }

   public selectCell(event: MouseEvent, cell: BaseTableCellModel, header: boolean = false): void {
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

      // When right clicking on a selected cell, don't deselect anything.
      if(event.button === 2 && this.isSelected(cell)) {
         return;
      }

      let row = cell.row;
      let column = cell.col;
      let map: Map<number, number[]> = this.model.selectedData || new Map();
      const omap: Map<number, number[]> = new Map(map);

      if(event.ctrlKey || this.model.multiSelect) {
         // When ctl clicking or multi-selecting on mobile on a selected cell,
         // deselect the cell instead
         if(this.isSelected(cell)) {
            this.deselectCell(row, column, map);
         }
         else {
            let selectedColumns = map.get(row) || [];
            map.set(row, selectedColumns.concat(column));

            if(this.model.firstSelectedColumn == null || this.model.firstSelectedColumn == -1) {
               this.model.firstSelectedRow = row;
               this.model.firstSelectedColumn = column;
            }

            this.addDataPath(this.decorateDataPath(cell));
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

         for(let i = 0; i <= lengthRow; i++) {
            for(let j = 0; j <= lengthColumn; j++) {
               let currentRow = map.get(firstRow + i) || [];
               map.set(firstRow + i, currentRow.concat(firstCol + j));

               // Start row is the relative row while cell contains absolute row
               let relativeRow = firstRow - this.loadedRows.start;
               let tableRow = this.table[relativeRow + i];

               this.addDataPath(this.decorateDataPath(tableRow[firstCol + j]));
            }
         }
      }
      else {
         this.clearSelection();
         map = new Map<number, number[]>();
         map.set(row, [].concat(column));
         this.model.firstSelectedRow = row;
         this.model.firstSelectedColumn = column;
         this.addDataPath(this.decorateDataPath(cell));
      }

      this.model.selectedData = map;
      this.model.lastSelected = {row: row, column: column};

      if(this.model.hasFlyover) {
         // Set flyover cell selected flag so mouseenter no longer trigger flyovers
         // until the selection is cleared
         this.flyoverCellSelected = true;
         this.sendFlyover(this.model.selectedData);
      }

      if(this.model.dataTip && !this.dataTipService.isDataTipVisible(this.getAssemblyName())) {
         this.dataTipService.freeze();
      }

      const nmap: Map<number, number[]> = this.model.selectedData;

      // if table in container, the select sequence is: table -> cell -> container
      if(!Tool.isEquals(nmap, omap)) {
         if(!this.context.binding && event.button == 0) {
            (<any> event).ignoreClick = true;
         }

         this.onRefreshFormat.emit(event);
      }

      this.changeDetectorRef.detectChanges();
   }

   public isSelected(cell: BaseTableCellModel): boolean {
      let row = cell.row;
      let column = cell.col;
      let map: Map<number, number[]> = this.model.selectedData;
      return map && super.isColumnSelected(map.get(row), column);
   }

   public isHyperlink(cell: BaseTableCellModel): boolean {
      return cell.hyperlinks != null && cell.hyperlinks.length > 0;
   }

   public selectTitle(event: MouseEvent): void {
      // select vsobject before select parts
      if(!this.selected && !this.vsInfo.formatPainterMode) {
         return;
      }

      if(!event.shiftKey && !event.ctrlKey) {
         this.model.selectedRegions = [];
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

   /**
    * Update the header row width when cell width is changed.
    * @param width
    */
   protected resizeHeaderCellWidth(width: number) {
      if(this.tableHeaderRows) {
         for(let row = 0; row < this.tableHeaderRows.length; row++) {
            if(this.tableHeaderRows[row] && this.tableHeaderRows[row][this.resizeCol]) {
               this.tableHeaderRows[row][this.resizeCol].cellWidth = width;
            }
         }
      }
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

      if(command.start === 0 && this.currentRow > command.end) {
         this.verticalScrollWrapper.nativeElement.scrollTop = 0;
      }

      this.table = command.tableCells;
      this.emptyHeaderCells = [];
      this.tableTranspose = [];
      this.setupVerticalScrollWrapper();
      let maxHeaderRow = this.getHeaderMaxRow();
      this.rightBorderMin = Math.min(...this.table.map(row => this.getRightmostCellObjectFormat(row))
                                     .filter(fmt => fmt != null)
                                     .map(fmt => Tool.getMarginSize(fmt.border.right)));

      // Necessary fixes for ffOrEdge
      if(this.model.headerRowCount > 0) {
         this.emptyHeaderCells = new Array(maxHeaderRow);

         for(let i = 0; i < maxHeaderRow; i++) {
            this.emptyHeaderCells[i] = new Array(this.model.colCount);

            for(let j = 0; j < this.model.colCount; j++) {
               this.emptyHeaderCells[i][j] = i - this.model.headerRowCount >= 0;
            }
         }
      }

      this.loadHeaderRows(command, maxHeaderRow);

      this.tableTranspose = this.transposeArray(this.table);
      this.topRowHeight = this.getRowPosition(command.start - this.model.headerRowCount);
      this.updateVisibleRows(false);
      this.checkScroll();
   }

   /**
    * reset last column cached width so it can expand to fill the table
    */
   private resetLastColWidth() {
      if(this.table != null) {
         for(let row of this.table) {
            if(!row || row.length == 0) {
               continue;
            }

            row[row.length - 1].cellWidth = null;
         }
      }

      if(this.tableHeaderRows) {
         this.tableHeaderRows.forEach(r => r.forEach(c => c.cellWidth = null));
      }
   }

   private loadHeaderRows(command: LoadTableDataCommand, maxHeaderRow: number): void {
      if(command.start === 0) {
         this.tableHeaderRows = command.tableCells.slice(0, maxHeaderRow);
      }
      else if(command.tableHeaderCells != null) {
         this.tableHeaderRows = command.tableHeaderCells;
      }
   }

   /**
    * Return the header row count which considered the rowspan.
    */
   private getHeaderMaxRow(): number {
      if(this.model.headerRowCount == 0) {
         return 0;
      }

      let hcount: number = this.table.length == 1 && this.model.headerRowCount == 1 ?
          this.model.headerRowCount : Math.min(this.table.length, this.model.headerRowCount);
      let rowSpanArray: number[] = [];

      for(let r = 0; r < hcount; r++) {
         let rowCells: BaseTableCellModel[] = this.table[r];

         for(let c = 0; c < rowCells.length; c++) {
            let cell: BaseTableCellModel = rowCells[c];

            if(isNaN(rowSpanArray[c])) {
               rowSpanArray[c] = 0;
            }

            if(!cell.spanCell) {
               rowSpanArray[c] += cell.rowSpan;
            }
         }
      }

      return rowSpanArray.reduce((a, b) => Math.max(a, b), 0);
   }

   private showAnnotateDialog(event: MouseEvent): Observable<RichTextDialog> {
      return this.richTextService.showAnnotationDialog((content) => {
         this.addAnnotation(content, event);
      });
   }

   protected addAnnotation(content: string, event: MouseEvent): void {
      const tableBounds = this.tableContainer.nativeElement.getBoundingClientRect();
      const selectedCells = this.model.selectedData || this.model.selectedHeaders;

      if(selectedCells) {
         const row = selectedCells.keys().next().value;
         const col = selectedCells.get(row).values().next().value;
         const selectedCell = this.cells
                .filter((cell) => cell.isRendered)
                .filter((cell) => cell.selected)
                .find((cell) => {
                   return cell.cell.row === row && cell.cell.col === col;
                });

         if(selectedCell) {
            const selectedCellBounds = selectedCell.boundingClientRect;
            const cellX = selectedCellBounds.left + selectedCellBounds.width / 2;
            const cellY = selectedCellBounds.top + selectedCellBounds.height / 2;
            const x = (cellX - tableBounds.left + this.scrollX) / this.scale;
            const y = (cellY - tableBounds.top + this.scrollY) / this.scale;
            const annotateEvent = new AddAnnotationEvent(content, x, y, this.getAssemblyName());
            annotateEvent.setRow(row);
            annotateEvent.setCol(col);
            this.viewsheetClient.sendEvent("/events/annotation/add-data-annotation", annotateEvent);
         }
      }
      else {
         const  x = (event.clientX - tableBounds.left) / this.scale;
         const y = (event.clientY - tableBounds.top) / this.scale;
         const annotateEvent = new AddAnnotationEvent(content, x, y, this.getAssemblyName());
         this.viewsheetClient.sendEvent("/events/annotation/add-assembly-annotation", annotateEvent);
      }
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

   private transposeArray(array: any[][]): any[][] {
      if(array.length == 0) {
         return array;
      }

      return array[0]
         .slice(0, this.model.headerColCount)
         .map((col: any[], colIdx: number) => {
            return array.map((row: any) => {
               return row[colIdx];
            });
         });
   }

   private decorateDataPath(cell: BaseTableCellModel): TableDataPath {
      return Object.assign({bindingType: cell.bindingType}, cell.dataPath);
   }

   protected checkScroll(): void {
      // optimization, set style immediately and delay the change detection.
      // styles should match the styles set in html
      this.renderer.setStyle(this.lowerTable.nativeElement, "top",
                             this.getDetailTableTopPosition() + "px");
      const lowerLeft = (this.isFullHorizontalWrapper ? 0 : -this.scrollX) -
         this.getColumnWidthSum(0, this.model.headerColCount - 1);
      this.renderer.setStyle(this.lowerTable.nativeElement, "left", lowerLeft + "px");
      this.renderer.setStyle(this.rightTopDiv.nativeElement, "left", lowerLeft + "px");
      this.renderer.setStyle(this.leftBottomDiv.nativeElement, "top",
                             this.getDetailTableTopPosition() + "px");
   }

   get tableLeftBorder(): string {
      return this.getTableLeftBorder(this.table);
   }

   // if table border is none, use cell border since cell may be covered up a little on
   // the side. this way the cell border won't be lost on the edge of table.

   protected getTableLeftBorder(tableData: BaseTableCellModel[][]): string {
      const border = this.model.objectFormat.border.left;

      if(!border || border.includes(" none ")) {
         if(tableData && tableData.length && tableData[0].length && tableData[0][0].vsFormatModel) {
            return tableData[0][0].vsFormatModel.border.left;
         }
      }

      return null;
   }

   // get horizontalScrollbarWidth(): number {
   //    return this.getObjectWidth() - this.getColumnWidthSum(0, this.model.headerColCount - 1);
   // }

   get horizontalScrollWidth(): number {
      return this.isFullHorizontalWrapper ? this.getColumnWidthSum(0, this.model.colCount) :
         this.getColumnWidthSum(this.model.headerColCount, this.model.colCount);
   }

   get horizontalScrollbarWidth(): number {
      return this.isFullHorizontalWrapper ? this.getObjectWidth() :
         this.getObjectWidth() - this.getColumnWidthSum(0, this.model.headerColCount - 1);
   }

   public get isFullHorizontalWrapper(): boolean {
      if(this.model) {
         return this.getColumnWidthSum(0, this.model.headerColCount - 1) > (this.getObjectWidth() / 2);
      }

      return false;
   }

   private getRightmostCellObjectFormat(row: BaseTableCellModel[]): VSFormatModel | null {
      for(let i = row.length - 1; i >= 0; i--) {
         const cell = row[i];

         if(!cell.spanCell) {
            return cell.vsFormatModel;
         }
      }

      return null;
   }

   public getDetailTableTopPosition(): number {
      return !this.loadedRows || this.loadedRows.start == 0 ? -this.scrollY - this.getHeaderHeight()
         : -this.scrollY + this.topRowHeight;
   }

   showPagingControl() {
      if(this.mobileDevice) {
         const pagingControlModel: PagingControlModel = {
            assemblyName: this.model.absoluteName,
            viewportWidth: this.horizontalScrollbarWidth,
            viewportHeight: this.verticalScrollWrapperHeight,
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
