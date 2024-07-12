/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import {
   AfterContentChecked,
   AfterViewChecked,
   ChangeDetectorRef,
   Component,
   ElementRef,
   Input,
   OnChanges,
   Renderer2,
   SimpleChanges,
   ViewChild
} from "@angular/core";
import { NgbTooltip } from "@ng-bootstrap/ng-bootstrap";
import { Rectangle } from "../../../../../../common/data/rectangle";
import { GuiTool } from "../../../../../../common/util/gui-tool";
import { BinarySearch } from "../../../../../../common/data/binary-search";
import { Range } from "../../../../../../common/data/range";

const INITIAL_COLUMN_WIDTH: number = 80;

@Component({
   selector: "query-preview-table",
   templateUrl: "query-preview-table.component.html",
   styleUrls: ["query-preview-table.component.scss"]
})
export class QueryPreviewTableComponent implements OnChanges, AfterViewChecked, AfterContentChecked {
   @Input() containerSize: Rectangle;
   @ViewChild("previewContainer", {static: true}) previewContainer: ElementRef;
   @ViewChild("table") table: ElementRef;
   @ViewChild("headerTable") headerTable: ElementRef;
   @ViewChild("tableContainer") tableContainer: ElementRef;
   @ViewChild("verticalScrollWrapper") verticalScrollWrapper: ElementRef;
   @ViewChild("verticalScrollTooltip") verticalScrollTooltip: NgbTooltip;
   currentRow: number = 0;
   _tableData: string[][] = [];
   _columnWidths: number[];
   _colWidths: number[];
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
   scrollXPos = 0;

   limited: boolean = false;
   col: number;
   isFirefox = GuiTool.isFF();
   mobileDevice: boolean = GuiTool.isMobileDevice();
   containerHeight: number = 0;
   containerWidth: number = 0;

   private initialColWidth: number = INITIAL_COLUMN_WIDTH;

   @Input()
   set tableData(tableData: string[][]) {
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
   }

   get tableData(): string[][] {
      return this._tableData;
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
               private changeRef: ChangeDetectorRef)
   {
   }

   ngOnChanges(changes: SimpleChanges) {
      if(changes.containerSize) {
         this.containerHeight = changes.containerSize.currentValue.height - 2;
         this.containerWidth = changes.containerSize.currentValue.width - 2;
      }
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

   public touchVScroll(delta: number) {
      this.scrollY = Math.max(0, this.scrollY - delta);
      this.scrollY = Math.min(this.scrollY,
         this.tableContainer.nativeElement.scrollHeight -
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

   /**
    * Handle vertical scroll
    */
   public verticalScrollHandler(event: any) {
      this.scrollY = event.target.scrollTop;
      this.currentRow = Math.floor(this.scrollY / this.cellHeight);
   }

   /**
    * Handle wheel scrolling
    */
   public wheelScrollHandler(event: any): void {
      this.verticalScrollWrapper.nativeElement.scrollTop += event.deltaY;
      event.preventDefault();
   }

   getCellLabel(value: string): string {
      return value != null ? value.toString().replace(/\n/g, "\\n") : "";
   }
}
