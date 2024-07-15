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

import {
   ChangeDetectorRef,
   Component, ElementRef, EventEmitter, Input, Output, Renderer2, ViewChild,
} from "@angular/core";

import { TableDataEvent } from "./table-data-event";
import { GuiTool } from "../../common/util/gui-tool";
import { SortInfo } from "../../vsobjects/objects/table/sort-info";
import { BaseTableCellModel } from "../../vsobjects/model/base-table-cell-model";
import { ModelService } from "../services/model.service";
import { NgbTooltip } from "@ng-bootstrap/ng-bootstrap";
import { XConstants } from "../../common/util/xconstants";

@Component({
   selector: "simple-table",
   templateUrl: "simple-table.component.html",
   styleUrls: ["simple-table.component.scss"]
})
export class SimpleTableComponent {
   @Input() sortEnabled: boolean = true;
   isFirefox = GuiTool.isFF();
   mobileDevice: boolean = GuiTool.isMobileDevice();
   sortInfo: SortInfo;
   currentRow: number = 0;
   scrollbarWidth: number;
   tableWidth: number;
   tableHeight: number;
   scrollY: number = 0;
   _tableData: BaseTableCellModel[][] = [];
   //Currently depends on font-size and line height.
   readonly cellHeight: number = 28;
   headerHeight: number = this.cellHeight;
   @Output() onSort: EventEmitter<TableDataEvent> = new EventEmitter<TableDataEvent>();
   @ViewChild("previewContainer") previewContainer: ElementRef;
   @ViewChild("table") table: ElementRef;
   @ViewChild("tableContainer") tableContainer: ElementRef;
   @ViewChild("verticalScrollWrapper") verticalScrollWrapper: ElementRef;
   @ViewChild("verticalScrollTooltip") verticalScrollTooltip: NgbTooltip;

   constructor(private modelService: ModelService,
               private renderer: Renderer2,
               private changeRef: ChangeDetectorRef) {}

   @Input()
   set tableData(tableData: BaseTableCellModel[][]) {
      if(!tableData) {
         tableData = [];
      }

      this._tableData = tableData;

      if(this.scrollbarWidth == undefined) {
         this.scrollbarWidth = GuiTool.measureScrollbars();
      }

      if(tableData && tableData.length > 0) {
         this.tableHeight = tableData.length * this.cellHeight;
      }
   }

   get tableData(): BaseTableCellModel[][] {
      return this._tableData;
   }

   getSortLabel(col: number): string {
      if(this.sortInfo && this.sortInfo.col === col) {
         switch(this.sortInfo.sortValue) {
         case XConstants.SORT_ASC:
            return "sort-ascending";
         case XConstants.SORT_DESC:
            return "sort-descending";
         case XConstants.SORT_NONE:
            return "sort";
         }
      }

      return "sort";
   }

   sortClicked(col: number): void {
      if(!this.sortInfo) {
         this.sortInfo = <SortInfo> {
            sortValue: XConstants.SORT_ASC
         };
      }

      this.sortInfo.col = col;

      switch(this.sortInfo.sortValue) {
         case XConstants.SORT_NONE:
            this.sortInfo.sortValue = XConstants.SORT_ASC;
            break;
         case XConstants.SORT_ASC:
            this.sortInfo.sortValue = XConstants.SORT_DESC;
            break;
         case XConstants.SORT_DESC:
            this.sortInfo.sortValue = XConstants.SORT_NONE;
            break;
      }

      let event = <TableDataEvent> {
         sortValue: this.sortInfo.sortValue,
         sortCol: this.sortInfo.col
      };

      this.onSort.emit(event);
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

   horizontalScroll() {
      // no-op, just trigger change detection
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

   /**
    * Handle wheel scrolling
    */
   public wheelScrollHandler(event: any): void {
      this.verticalScrollWrapper.nativeElement.scrollTop += event.deltaY;
      event.preventDefault();
   }

   /**
    * Handle vertical scroll
    */
   public verticalScrollHandler(event: any) {
      this.scrollY = event.target.scrollTop;
      this.updateVerticalScrollTooltip();
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
}
