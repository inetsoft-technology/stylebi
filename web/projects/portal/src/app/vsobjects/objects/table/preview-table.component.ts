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
   AfterContentChecked,
   AfterViewChecked,
   ChangeDetectorRef,
   Component,
   ElementRef,
   EventEmitter,
   HostListener,
   Input,
   OnDestroy,
   Output,
   QueryList,
   Renderer2,
   ViewChild,
   ViewChildren
} from "@angular/core";
import { NgbTooltip } from "@ng-bootstrap/ng-bootstrap";
import { Observable } from "rxjs";
import {
   FeatureFlagsService,
   FeatureFlagValue
} from "../../../../../../shared/feature-flags/feature-flags.service";
import { FormValidators } from "../../../../../../shared/util/form-validators";
import { Tool } from "../../../../../../shared/util/tool";
import { AssemblyActionGroup } from "../../../common/action/assembly-action-group";
import { BinarySearch } from "../../../common/data/binary-search";
import { FormatInfoModel } from "../../../common/data/format-info-model";
import { HyperlinkViewModel } from "../../../common/data/hyperlink-model";
import { Range } from "../../../common/data/range";
import { GuiTool } from "../../../common/util/gui-tool";
import { XConstants } from "../../../common/util/xconstants";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { FixedDropdownDirective } from "../../../widget/fixed-dropdown/fixed-dropdown.directive";
import { ActionsContextmenuComponent } from "../../../widget/fixed-dropdown/actions-contextmenu.component";
import { DropdownOptions } from "../../../widget/fixed-dropdown/dropdown-options";
import { DropdownRef } from "../../../widget/fixed-dropdown/fixed-dropdown-ref";
import { FixedDropdownService } from "../../../widget/fixed-dropdown/fixed-dropdown.service";
import { ModelService } from "../../../widget/services/model.service";
import { ContextProvider } from "../../context-provider.service";
import { BaseTableCellModel } from "../../model/base-table-cell-model";
import { ShowHyperlinkService } from "../../show-hyperlink.service";
import { DetailDndInfo } from "./detail-dnd-info";
import { SortInfo } from "./sort-info";

const CHART_DETAIL_COLWIDTH_URI: string = "../api/vs/showdetails/colwidth";
const CHART_DATA_COLWIDTH_URI: string = "../api/vs/showdata/colwidth";
const INITIAL_COLUMN_WIDTH: number = 80;

@Component({
   selector: "preview-table",
   templateUrl: "preview-table.component.html",
   styleUrls: ["preview-table.component.scss"]
})
export class PreviewTableComponent implements OnDestroy, AfterViewChecked, AfterContentChecked {
   @Input() sortEnabled: boolean = false;
   @Input() checkHeaderValid: boolean = false;
   @Input() sortInfo: SortInfo;
   @Input() linkUri: string;
   @Input() runtimeId: string;
   @Input() assemblyName: string;
   @Input() viewsheetClient: ViewsheetClientService;
   @Input() formattingEnabled: boolean = true;
   @Input() formatModel: FormatInfoModel;
   @Input() worksheetId: string;
   @Input() formatGetter: (wsId: string, column: number) => Observable<FormatInfoModel>;
   @Input() isDetails: boolean = true;
   @Input() draggable: boolean = true;
   @Input() hideEnabled: boolean = true;
   @Output() onSort = new EventEmitter<SortInfo>();
   @Output() onOrder = new EventEmitter<DetailDndInfo>();
   @Output() onFormatChange = new EventEmitter<{
      sortInfo: SortInfo,
      format: FormatInfoModel,
      column: number[],
      str: string,
      detailStyle: string
   }>();
   @Output() onRename = new EventEmitter<{
      column: number,
      newName: string
      }>();
   @Output() onHide = new EventEmitter<number[]>();
   @ViewChild("previewContainer", {static: true}) previewContainer: ElementRef;
   @ViewChild("table") table: ElementRef;
   @ViewChild("headerTable") headerTable: ElementRef;
   @ViewChild("tableContainer") tableContainer: ElementRef;
   @ViewChild("verticalScrollWrapper") verticalScrollWrapper: ElementRef;
   @ViewChild("verticalScrollTooltip") verticalScrollTooltip: NgbTooltip;
   @ViewChildren(FixedDropdownDirective) dropdowns: QueryList<FixedDropdownDirective>;
   currentRow: number = 0;
   _tableData: BaseTableCellModel[][] = [];
   _oheaders: string[] = [];
   _columnWidths: number[];
   _colWidths: number[];
   resizePositions: number[];
   scrollbarWidth: number;
   tableWidth: number;
   tableHeight: number;
   scrollY: number = 0;
   horizontalDist = 0;
   columnRightPositions: number[];
   columnIndexRange: Range;
   leftOfColRangeWidth: number;
   rightOfColRangeWidth: number;
   //Currently depends on font-size and line height.
   readonly cellHeight: number = 28;
   readonly borderWidth: number = 1;
   headerHeight: number = this.cellHeight;
   dragIndex: number = -1;
   dropIndex: number = -1;
   dropRect: any = {top: 0, left: 0, width: 6, height: 0};
   selectedData: Map<number, number[]> = new Map<number, number[]>();
   lastSelection: number[];
   columnSelection: number[] = [];
   renaming = false;
   hiddenColCount = 0;
   dropdownRef: DropdownRef = null;
   scrollXPos = 0;

   /** Column resizing fields */
   resizeLeft: number;
   private originalColumnWidth: number;
   private originalLeft: number;
   private resizeIndex: number;
   private pageXOrigin: number;
   private windowListeners: (() => void)[] = [];
   limited: boolean = false;
   col: number;
   isFirefox = GuiTool.isFF();
   mobileDevice: boolean = GuiTool.isMobileDevice();
   tableStyleApplied = false;

   private initialColWidth: number = INITIAL_COLUMN_WIDTH;
   private readonly minColWidth: number = 40;

   @Input()
   set tableData(tableData: BaseTableCellModel[][]) {
      if(!tableData) {
         tableData = [];
      }

      if((this.limited = tableData.length > 5001)) {
         tableData = tableData.slice(0, 5001);
      }

      const sameCols = this._tableData && this._tableData[0] &&
         tableData && tableData[0] &&
         this._tableData[0].length === tableData[0].length;

      let colVisibilityChanged = false;

      if(sameCols) {
         for(let i = 0; i < tableData[0].length; i ++) {
            if(!(this._tableData[0][i] == null && tableData[0][i] == null) &&
               !(this._tableData[0][i] != null && tableData[0][i] != null))
            {
               colVisibilityChanged = true;
               break;
            }
         }
      }

      this._tableData = tableData;

      if(tableData.length > 0) {
         for(let i = 0; i < tableData[0].length; i++) {
            this._oheaders[i] = this.getCellLabel(tableData[0][i]);
         }
      }

      if(!!tableData[0]) {
         this.hiddenColCount = tableData[0].filter(header => header == null).length;
      }

      if(this.scrollbarWidth == undefined) {
         this.scrollbarWidth = GuiTool.measureScrollbars();
      }

      if(tableData && tableData.length > 0) {
         this.tableHeight = tableData.length * this.cellHeight;
      }

      // if(!sameCols || colVisibilityChanged) {
      if(!sameCols || colVisibilityChanged) {
         this.scrollXPos = this.previewContainer.nativeElement.scrollLeft;
         this.initColumnWidths();

         Promise.resolve(null).then(() => {
            this.renderer.setProperty(this.previewContainer.nativeElement, "scrollLeft",
               this.scrollXPos);

            if(!sameCols) {
               this.scrollXPos = 0;
               this.renderer.setProperty(this.previewContainer.nativeElement, "scrollLeft", 0);
               this.initColumnWidths();
               this.updateHorizontalDist();
               this.updateColumnRange();
            }
         });
      }

      this.tableStyleApplied = this.isTableStyleApplied();
   }

   get tableData(): BaseTableCellModel[][] {
      return this._tableData;
   }

   isHeaderValid(cell: BaseTableCellModel) {
      if(!this.checkHeaderValid) {
         return true;
      }

      let label = this.getCellLabel(cell);

      if(FormValidators.matchCalcSpecialCharacters(label)) {
         return false;
      }

      return true;
   }

   @Input()
   set colWidths(nws: number[]) {
      let isSameWidth = true;

      if(nws != null && !!this.tableData  && !!this.tableData[0] &&
      nws.length == this.tableData[0].length)
      {
         for(let i = 0; i < nws.length; i++) {
            if(this._tableData[0][i] == null) {
               nws[i] = 0;
            }
         }
      }

      if(this._colWidths == null || nws == null) {
         isSameWidth = false;
      }
      else if(nws.length != this._colWidths.length) {
         isSameWidth = false;
      }
      else {
         for(let i = 0; i < nws.length; i++) {
            if(nws[i] != this._colWidths[i]) {
               isSameWidth = false;
            }
         }
      }

      this._colWidths = nws;

      if(!isSameWidth) {
         this.scrollXPos = this.previewContainer.nativeElement.scrollLeft;

         Promise.resolve(null).then(() => {
            this.initColumnWidths();
            this.renderer.setProperty(this.previewContainer.nativeElement, "scrollLeft",
               this.scrollXPos);
         });
      }
   }

   get colWidths(): number[] {
      return this._colWidths;
   }

   constructor(private renderer: Renderer2,
               private hyperlinkService: ShowHyperlinkService,
               private contextProvider: ContextProvider,
               private dropdownService: FixedDropdownService,
               private modelService: ModelService,
               private changeRef: ChangeDetectorRef,
               private featureFlagsService: FeatureFlagsService,
               private hostElement: ElementRef)
   {
   }

   ngOnDestroy() {
      this.clearListeners();
   }

   ngAfterContentChecked(): void {
      this.updateHorizontalDist();
   }

   ngAfterViewChecked() {
      if(this.headerTable) {
         const headerH = this.headerTable.nativeElement.offsetHeight;

         if(headerH != this.headerHeight) {
            this.headerHeight = headerH;
            this.changeRef.detectChanges();
         }
      }

      if(this.table) {
         const tableH = this.table.nativeElement.offsetHeight;

         if(tableH != this.tableHeight) {
            this.tableHeight = tableH;
            this.changeRef.detectChanges();
         }
      }
   }

   @HostListener("document:click", ["$event"])
   onClick(event: MouseEvent): void {
      if(!this.hostElement.nativeElement.contains(event.target) && !this.renaming) {
         this.clearSelection();
      }
   }

   public touchVScroll(delta: number) {
      if(!this.isVScrollable()) {
         return;
      }

      this.scrollY = Math.max(0, this.scrollY - delta);
      this.scrollY = Math.min(this.scrollY,
         this.tableHeight -
         this.tableContainer.nativeElement.clientHeight);
   }

   public touchHScroll(delta: number) {
      const scrollLeft = this.previewContainer.nativeElement.scrollLeft;
      this.renderer.setProperty(this.previewContainer.nativeElement, "scrollLeft",
                                Math.max(0, scrollLeft - delta));
   }

   horizontalScroll(): void {
      this.scrollXPos = this.previewContainer.nativeElement.scrollLeft;
      this.updateHorizontalDist();
      this.updateColumnRange();
   }

   private updateHorizontalDist(): void {
      if(this.previewContainer.nativeElement.scrollLeft == 0 &&
         this.previewContainer.nativeElement.scrollLeft != this.scrollXPos) {
         this.renderer.setProperty(this.previewContainer.nativeElement, "scrollLeft",
            this.scrollXPos);
      }

      this.horizontalDist = this.previewContainer.nativeElement.clientWidth +
         this.previewContainer.nativeElement.scrollLeft;
   }

   get tableBodyWidth(): number {
      return this.tableWidth + this.borderWidth * 2;
   }

   isRowVisible(idx: number) {
      return idx >= this.currentRow && idx < this.currentRow + 100;
   }

   startResize(event: MouseEvent, index: number) {
      event.preventDefault();
      this.windowListeners = [
         this.renderer.listen("window", "mousemove", (e) => this.resizeMove(e)),
         this.renderer.listen("window", "mouseup", (e) => this.resizeEnd(e))
      ];

      this.resizeIndex = index;
      this.originalColumnWidth = this._columnWidths[index];
      this.originalLeft = this.columnRightPositions[index];
      this.resizeLeft = this.originalLeft;
      this.pageXOrigin = event.pageX;
   }

   private resizeMove(event: MouseEvent) {
      this.resizeLeft = this.originalLeft - this.pageXOrigin + event.pageX;
      const width = Math.max(this.resizeLeft - this.originalLeft + this.originalColumnWidth,
         this.minColWidth);
      this.updateColumnSize(this.resizeIndex, width);
      this.updateHorizontalDist();
   }

   private resizeEnd(event: MouseEvent) {
      this.clearListeners();
      this.resizeLeft = this.originalLeft - this.pageXOrigin + event.pageX;
      const width = Math.max(this.resizeLeft - this.originalLeft + this.originalColumnWidth,
         this.minColWidth);
      this.resizeLeft = undefined;
      this.updateColumnSize(this.resizeIndex, width);
      this.updateWidths();
      this.updateHorizontalDist();
      const containerWidth = this.previewContainer.nativeElement.clientWidth;
      const adjustedTableWidth = this.tableWidth + this.scrollbarWidth;

      if(adjustedTableWidth < this.horizontalDist && adjustedTableWidth > containerWidth) {
         this.renderer.setProperty(this.previewContainer.nativeElement, "scrollLeft",
            adjustedTableWidth - containerWidth);
      }

      const params: HttpParams = new HttpParams()
         .set("vsId", Tool.byteEncode(this.runtimeId))
         .set("assemblyName", this.assemblyName)
         .set("columnIndex", this.resizeIndex + "");

      if(this.isDetails) {
         this.modelService.putModel(CHART_DETAIL_COLWIDTH_URI, width + "", params).subscribe();
      }
      else {
         this.modelService.putModel(CHART_DATA_COLWIDTH_URI, width + "", params).subscribe();
      }
   }

   private initColumnWidths() {
      if(this._tableData && this._tableData[0] && this._tableData[0].length > 0) {
         const numCols = this._tableData[0].length;
         const width = this.previewContainer.nativeElement.clientWidth - this.scrollbarWidth;
         this.initialColWidth = Math.max(INITIAL_COLUMN_WIDTH, Math.floor(width / numCols));
         this._columnWidths = this.calcColWidths();
      }
      else {
         this._columnWidths = [];
      }

      for(let i = 0; this._colWidths && i < this._colWidths.length; i++) {
         if(this._colWidths[i]) {
            this._columnWidths[i] = this._colWidths[i];
         }
      }

      this.updateWidths();
   }

   private calcColWidths(): number[] {
      const numCols = this._tableData[0].length;
      const arr = Array(numCols).fill(this.initialColWidth);

      for(let i = 0; i < this._tableData.length; i++) {
         for(let j = 0; j < this._tableData[i].length; j++) {
            let cell = this._tableData[i][j];
            let data = this.getCellLabel(cell);

            if(i == 0 && !cell) {
               arr[j] = (data + "").length * 7;
            }
            else {
               arr[j] = Math.max(arr[j], (data + "").length * 7);
            }

            arr[j] = Math.min(arr[j], 150);
         }
      }

      return arr;
   }

   private updateWidths() {
      this.columnRightPositions = [];
      this.tableWidth = 0;

      for(let width of this._columnWidths) {
         this.tableWidth += width;
         this.columnRightPositions.push(this.tableWidth);
      }

      setTimeout(() => {
         this.updateColumnRange();
      }, 0);
   }

   private updateColumnRange(): void {
      const leftViewBound = this.previewContainer.nativeElement.scrollLeft;
      const rightViewBound = leftViewBound + this.previewContainer.nativeElement.clientWidth;
      const search = BinarySearch.numbers(this.columnRightPositions);

      let leftmostColumnIndex = search.ceiling(leftViewBound);

      if(leftmostColumnIndex == null) {
         if(leftViewBound >= this.columnRightPositions[this.columnRightPositions.length - 1]) {
            leftmostColumnIndex = this._columnWidths.length - 10;
         }
         else {
            leftmostColumnIndex = 0;
         }
      }

      let rightmostColumnIndex = search.ceiling(rightViewBound);

      if(rightmostColumnIndex == null) {
         rightmostColumnIndex = this._columnWidths.length - 1;
      }

      this.columnIndexRange = new Range(leftmostColumnIndex, rightmostColumnIndex);
      this.leftOfColRangeWidth = 0;
      this.rightOfColRangeWidth = 0;

      if(leftmostColumnIndex > 0) {
         this.leftOfColRangeWidth = this.columnRightPositions[leftmostColumnIndex - 1];
      }

      if(rightmostColumnIndex < this.columnRightPositions.length - 1) {
         this.rightOfColRangeWidth =
            this.columnRightPositions[this.columnRightPositions.length - 1] -
            this.columnRightPositions[rightmostColumnIndex];
      }
   }

   private clearListeners() {
      this.windowListeners.forEach((listener) => listener());
      this.windowListeners = [];
   }

   private updateColumnSize(index: number, width: number) {
      this._columnWidths[index] = width;
      this.updateWidths();
   }

   getSortLabel(col: number): string {
      if(this.sortInfo && this.sortInfo.col === col) {
         switch(this.sortInfo.sortValue) {
            case XConstants.SORT_ASC:
               return "sort-ascending";
            case XConstants.SORT_DESC:
               return "sort-descending";
            case XConstants.SORT_NONE:
            default:
               return "sort";
         }
      }
      else {
         return "sort";
      }
   }

   sortClicked(mouseEvent: MouseEvent, col: number): void {
      mouseEvent.stopPropagation();

      if(this.sortInfo && this.sortInfo.col === col) {
         switch(this.sortInfo.sortValue) {
            case XConstants.SORT_NONE:
               this.sortInfo.sortValue = XConstants.SORT_ASC;
               break;
            case XConstants.SORT_ASC:
               this.sortInfo.sortValue = XConstants.SORT_DESC;
               break;
            case XConstants.SORT_DESC:
               this.sortInfo.sortValue = XConstants.SORT_NONE;
         }
      }
      else {
         this.sortInfo = <SortInfo> {
            col: col,
            sortValue: XConstants.SORT_ASC
         };
      }

      this.onSort.emit(this.sortInfo);
   }

   /**
    * Open the formatting-pane to edit the format of a specific column.
    * @param {number} col
    */
   formatClicked(col: number, open: boolean = true): void {
      if(open) {
         this.col = col;
         this.formatGetter(this.worksheetId, col)
            .subscribe((model: FormatInfoModel) => {
               this.formatModel = model;
            });
      }
      else {
         this.onFormatChange.emit({
            sortInfo: this.sortInfo,
            format: this.formatModel,
            column: [col],
            str: this.worksheetId,
            detailStyle: null
         });
      }
   }

   /**
    * Handle vertical scroll
    */
   public verticalScrollHandler(event: any) {
      this.scrollY = event.target.scrollTop;
      this.updateVerticalScrollTooltip();
   }

   /**
    * Handle wheel scrolling
    */
   public wheelScrollHandler(event: any): void {
      this.verticalScrollWrapper.nativeElement.scrollTop += event.deltaY;
      event.preventDefault();
   }

   /**
    * Updates the content of the vertical scroll tooltip
    */
   updateVerticalScrollTooltip(openTooltip: boolean = false) {
      this.currentRow = Math.floor(this.scrollY / this.cellHeight);

      if(this.isVScrollable() && (openTooltip || this.verticalScrollTooltip.isOpen())) {
         const total = this.tableData && this.tableData.length > 0 ?
            this.tableData.length - 1 : 0;
         const text = (this.currentRow + 1) + " (" + total + ")";

         // approximate tooltip width
         const tooltipWidth = GuiTool.measureText(text, "10px Roboto") + 10;
         const x = this.verticalScrollWrapper.nativeElement.getBoundingClientRect().right +
            tooltipWidth;

         // change placement if the tooltip is out of bounds
         if(x <= (window.innerWidth || document.documentElement.clientWidth)) {
            this.verticalScrollTooltip.placement = "right";
         }
         else {
            this.verticalScrollTooltip.placement = "left";
         }

         this.verticalScrollTooltip.ngbTooltip = text;
         this.verticalScrollTooltip.close();
         this.verticalScrollTooltip.open();
         this.changeRef.detectChanges();
      }
   }

   /**
    * Determines whether it's possible to scroll the table vertically
    */
   private isVScrollable(): boolean {
      if(this.verticalScrollWrapper && this.verticalScrollWrapper.nativeElement) {
         let childElem = this.verticalScrollWrapper.nativeElement.firstElementChild;
         return childElem.getBoundingClientRect().height >
            this.verticalScrollWrapper.nativeElement.getBoundingClientRect().height;
      }

      return false;
   }

   clickLink(cell: BaseTableCellModel, event): void {
      let dropdownItems = {
            hyperlinks: cell.hyperlinks,
            xPos: event.pageX,
            yPos: event.pageY,
            numLinks: cell.hyperlinks.length
      };

      this.openClickDropdownMenu(dropdownItems);
   }

   private openClickDropdownMenu(dropdownItems: any): void {
      const actions: AssemblyActionGroup[] = [];
      actions.push(this.hyperlinkService.createHyperlinkActions(
         dropdownItems.hyperlinks, this.linkUri, this.viewsheetClient.runtimeId));

      if(actions.length > 0) {
         let xPos = dropdownItems.xPos;
         let yPos = dropdownItems.yPos;
         this.hyperlinkService.createActionsContextmenu(this.dropdownService,
             actions, dropdownItems, xPos, yPos, this.isForceTab());
      }
   }

   getTarget(cell: BaseTableCellModel): string {
      const _model = HyperlinkViewModel.fromHyperlinkModel(cell.hyperlinks[0], this.linkUri,
                                                           null, this.runtimeId, false);
      return _model == null ? null : _model.target;
   }

   isForceTab(): boolean {
      return this.contextProvider.composer;
   }

   public apply(open: boolean): void {
      if(open) {
         this.onFormatChange.emit({
            sortInfo: this.sortInfo,
            format: this.formatModel,
            column: [this.col],
            str: this.worksheetId,
            detailStyle: null
         });

         this.dropdowns.forEach(a => a.close());
      }
   }

   public dragStart(event: DragEvent, col: number): void {
      this.dragIndex = col;
      event.dataTransfer.effectAllowed  = "move";
      Tool.setTransferData(event.dataTransfer, "detail");
   }

   private isDragAccaptable(): boolean {
      return this.dragIndex >= 0;
   }

   public dragOverTable(event: DragEvent, idx: number): void {
      if(!this.isDragAccaptable()) {
         return;
      }

      event.preventDefault();
      let containerBounds = (<Element> event.currentTarget).getBoundingClientRect();
      let posx = event.clientX - containerBounds.left;
      let colWidth = this._columnWidths[idx];
      // If in the left of cell, drop left of current column.
      // If in the right of cell, drop right of current column.
      if(posx < colWidth / 2) {
         this.dropIndex = idx;
      }
      // If idx is last cell, drop on the end of table.
      else {
         this.dropIndex = idx + 1;
      }

      let borderLeft: number = 0;

      for(let i = 0; i < this._columnWidths.length; i++) {
         if(i < this.dropIndex) {
            borderLeft += this._columnWidths[i];
         }
      }

      this.dropRect.left = borderLeft;
      this.changeRef.detectChanges();
   }

   public onLeave(event: MouseEvent): void {
      const tableRect = this.table.nativeElement.getBoundingClientRect();

      if(event.clientX < tableRect.left || event.clientX > tableRect.right - 2 ||
         event.clientY < tableRect.top || event.clientY > tableRect.bottom)
      {
         this.dropIndex = -1;
         this.changeRef.detectChanges();
      }
   }

   private isDropAccaptable(): boolean {
      return this.isDragAccaptable()
         && this.dropIndex !== -1 && this.dragIndex !== this.dropIndex;
   }

   public dropOnTable(event: DragEvent): void {
      if(!this.isDropAccaptable()) {
         this.dragIndex = -1;
         this.dropIndex = -1;
         return;
      }

      const dragIndex = this.dragIndex;
      const dropIndex = this.dropIndex;
      this.dragIndex = -1;
      this.dropIndex = -1;

      event.preventDefault();
      event.stopPropagation();

      const dinfo = new DetailDndInfo();
      dinfo.dragIndexes = [dragIndex];
      dinfo.dropIndex = dropIndex;
      this.onOrder.emit(dinfo);
   }

   getCellLabel(cell: BaseTableCellModel): string {
      const label = cell == null ? "" : cell.cellLabel != null ? cell.cellLabel : cell.cellData;
      return label != null ? label.toString().replace(/\n/g, "\\n") : "";
   }

   isTableStyleApplied(): boolean {
      for(let i = 0; i < this._tableData.length; i++) {
         for(let j = 0; j < this._tableData[i].length; j++) {
            let cell = this._tableData[i][j];

            if(!!cell && !!cell.vsFormatModel) {
               return true;
            }
         }
      }

      return false;
   }

   selectCell(event: MouseEvent, row: number, col: number) {
      if(this.isSelected(row, col)) {
         if(!!event && !!event.ctrlKey) {
            this.deselectCell(row, col);
            return;
         }
         else if(row == 0) {
            this.renaming = true;
            this.selectedData.clear();
         }
      }
      else if(!!event && (event.shiftKey || event.ctrlKey)) {
         if(event.shiftKey && !!this.lastSelection) {
            window.getSelection().empty();
            this.selectRectangle(row, col, this.lastSelection[0], this.lastSelection[1]);
            return;
         }
      }
      else {
         this.selectedData.clear();
         this.lastSelection = null;

         if(col == -1 || row == -1) {
            this.columnSelection = [];
            return;
         }
      }

      let currentCol = this.selectedData.get(col) || [];

      if(currentCol.indexOf(row) == -1) {
         this.selectedData.set(col, currentCol.concat(row));
      }

      this.columnSelection = Array.from(this.selectedData.keys());
      this.lastSelection = [row, col];
   }

   selectRectangle(x1: number, y1: number, x2: number, y2: number) {
      const minX = Math.min(x1, x2);
      const minY = Math.min(y1, y2);
      const maxX = Math.max(x1, x2);
      const maxY = Math.max(y1, y2);

      for(let x = minX; x <= maxX; x++) {
         for(let y = minY; y <= maxY; y++) {
            let currentCol = this.selectedData.get(y) || [];

            if(currentCol.indexOf(x) == -1) {
               this.selectedData.set(y, currentCol.concat(x));
            }
         }
      }

      this.columnSelection = Array.from(this.selectedData.keys());
      this.lastSelection = [x2, y2];
   }

   deselectCell(row: number, col: number) {
      let currentCol = this.selectedData.get(col) || [];
      let idx = currentCol.indexOf(row);

      if(idx != -1) {
         currentCol.splice(idx, 1);

         if(currentCol.length == 0) {
            this.selectedData.delete(col);
         }
         else {
            this.selectedData.set(col, currentCol);
         }
      }

      this.columnSelection = Array.from(this.selectedData.keys());
      this.lastSelection = null;
   }

   isSelected(row: number, col: number): boolean {
      let currentCol = this.selectedData.get(col) || [];
      return currentCol.indexOf(row) != -1;
   }

   clearSelection() {
      this.selectCell(null, -1, -1);
   }

   changeCellText(event: KeyboardEvent): void {
      if(event && event.keyCode !== 13) {
         return;
      }

      let selectedIndex = this.selectedData.keys().next();

      if(selectedIndex.value != null) {
         const nlabel = this.getCellLabel(this.tableData[0][selectedIndex.value]);

         if(nlabel.trim() == "") {
            this.tableData[0][selectedIndex.value].cellLabel = this._oheaders[selectedIndex.value];
         }
         else {
            if(this._oheaders[selectedIndex.value] == nlabel) {
               this.renaming = false;
               this.clearSelection();
               return;
            }

            this.onRename.emit({
               column: selectedIndex.value,
               newName: nlabel
            });
         }
      }

      this.renaming = false;
      this.clearSelection();
   }

   openVisibilityContextMenu(event: MouseEvent, row: number, column: number) {
      if(!this.isSelected(row, column)) {
         this.selectCell(null, row, column);
      }

      let options: DropdownOptions = {
         position: {x: event.clientX, y: event.clientY},
         contextmenu: true,
         closeOnWindowResize: true,
      };

      let actions = [new AssemblyActionGroup([
         {
            id: () => "preview-table hide-column",
            label: () => "_#(js:Hide Column)",
            icon: () => "place-holder-icon icon-hyperlink",
            enabled: () => true,
            visible: () => this.hideEnabled && this.columnSelection.length > 0 &&
               !!this._tableData[0] && this.columnSelection.length < this._tableData[0].length - this.hiddenColCount,
            action: () => this.onHide.emit(this.columnSelection)
         },
         {
            id: () => "preview-table show-columns",
            label: () => "_#(js:Show Columns)",
            icon: () => "place-holder-icon icon-highlight",
            enabled: () => true,
            visible: () => this.hiddenColCount > 0,
            action: () => this.onHide.emit([-1])
         },
      ])];

      if(!this.dropdownRef || this.dropdownRef.closed) {
         this.dropdownRef = this.dropdownService.open(ActionsContextmenuComponent, options);
         let contextmenu: ActionsContextmenuComponent = this.dropdownRef.componentInstance;
         contextmenu.sourceEvent = event;
         contextmenu.forceTab = false;
         contextmenu.actions = actions;
      }
   }

   @HostListener("window:resize")
   resizeListener(): void {
      this.horizontalScroll();
   }
}
