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
   AfterContentInit,
   Directive,
   ElementRef,
   OnDestroy,
   Renderer2
} from "@angular/core";

const MIN_COL_WIDTH = 40;

@Directive({
   selector: "[resizableTable]"
})
export class ResizableTableDirective implements AfterContentInit, OnDestroy {
   private columnWidths: number[] = [];
   private bodyTable: any;
   private headerTable: any;
   private headerColGroup: any;
   private bodyColGroup: any;
   private resizeHandles: HTMLDivElement[];
   private resizePositions: number[];

   private resizeLeft: number;
   private originalColumnWidth: number;
   private originalLeft: number;
   private resizeIndex: number;
   private pageXOrigin: number;

   private windowListeners: (() => void)[] = [];

   constructor(private hostRef: ElementRef, private renderer: Renderer2) {
   }

   ngAfterContentInit() {
      const outerDiv = this.renderer.createElement("div");
      const bodyDiv = this.renderer.createElement("div");
      const columnResizeContainer = this.renderer.createElement("div");

      this.renderer.addClass(outerDiv, "resizable-table-container");
      this.renderer.addClass(bodyDiv, "resizable-table-body-container");
      this.renderer.addClass(columnResizeContainer, "column-resize-container");

      this.bodyTable = this.hostRef.nativeElement;

      // create the header table and remove the thead from body table
      this.headerTable = this.renderer.createElement("table");
      this.renderer.setAttribute(this.headerTable, "class", this.bodyTable.className);
      let thead = this.bodyTable.querySelector("thead");
      this.renderer.appendChild(this.headerTable, thead);

      // get the parent and insert the outerDiv before the original table
      let parent: HTMLElement = this.bodyTable.parentNode;
      this.renderer.insertBefore(parent, outerDiv, this.bodyTable);

      // create the structure for resizable table
      this.renderer.appendChild(outerDiv, this.headerTable);
      this.renderer.appendChild(outerDiv, bodyDiv);
      this.renderer.appendChild(outerDiv, columnResizeContainer);
      this.renderer.appendChild(bodyDiv, this.bodyTable);

      // compute the dimensions after a tick to allow for the parent to set the styles
      setTimeout(() => {
         // get the cells and get their original width
         const headerCells = this.headerTable.querySelectorAll("thead > tr:first-of-type > th");
         this.headerColGroup = this.renderer.createElement("colgroup");

         for(let i = 0; i < headerCells.length; i++) {
            this.columnWidths.push(headerCells[i].clientWidth);
            const col = this.renderer.createElement("col");
            this.renderer.setStyle(col, "width", this.columnWidths[i] + "px");
            this.renderer.appendChild(this.headerColGroup, col);
         }

         this.renderer.appendChild(this.headerTable, this.headerColGroup);
         this.bodyColGroup = this.headerColGroup.cloneNode(true);
         this.renderer.appendChild(this.bodyTable, this.bodyColGroup);

         // set up the resize handles
         this.resizeHandles = [];

         for(let i = 0; i < this.columnWidths.length; i++) {
            const resizeHandle = this.renderer.createElement("div");
            this.renderer.addClass(resizeHandle, "resize-handle");
            this.resizeHandles.push(resizeHandle);
            this.renderer.appendChild(columnResizeContainer, resizeHandle);
            this.renderer.listen(resizeHandle, "mousedown", (event) => {
               if(event.button == 0) {
                  this.startResize(event, i);
               }
            });
         }

         // set the height and width
         const tableStyle = window.getComputedStyle(this.bodyTable);
         let tableHeight: string;

         if(tableStyle.maxHeight) {
            tableHeight = tableStyle.maxHeight;
         }
         else {
            tableHeight = tableStyle.height;
         }

         if(tableHeight) {
            bodyDiv.style.height = `calc(${tableHeight} - ${this.headerTable.offsetHeight}px`;
         }

         columnResizeContainer.style.height = `${this.headerTable.offsetHeight}px`;
         this.updateResizePositions();
         this.updateTableWidth();
      });
   }

   ngOnDestroy() {
      this.clearListeners();
   }

   private updateResizePositions() {
      this.resizePositions = [];
      let width = 0;

      for(let i = 0; i < this.columnWidths.length; i++) {
         width += this.columnWidths[i];
         this.renderer.setStyle(this.resizeHandles[i], "left", width + "px");
         this.resizePositions.push(width);
      }
   }

   private startResize(event: MouseEvent, index: number) {
      event.preventDefault();
      this.windowListeners = [
         this.renderer.listen("window", "mousemove", (e) => this.resizeMove(e)),
         this.renderer.listen("window", "mouseup", (e) => this.resizeEnd(e))
      ];

      this.resizeIndex = index;
      this.originalColumnWidth = this.columnWidths[index];
      this.originalLeft = this.resizePositions[index];
      this.resizeLeft = this.originalLeft;
      this.pageXOrigin = event.pageX;
   }

   private resizeMove(event: MouseEvent) {
      this.resizeLeft = this.originalLeft - this.pageXOrigin + event.pageX;
      const width = Math.max(this.resizeLeft - this.originalLeft + this.originalColumnWidth,
         MIN_COL_WIDTH);
      this.updateColumnSize(this.resizeIndex, width);
   }

   private resizeEnd(event: MouseEvent) {
      this.clearListeners();
      this.resizeLeft = this.originalLeft - this.pageXOrigin + event.pageX;
      const width = Math.max(this.resizeLeft - this.originalLeft + this.originalColumnWidth,
         MIN_COL_WIDTH);
      this.resizeLeft = undefined;
      this.updateColumnSize(this.resizeIndex, width);
      this.updateResizePositions();
   }

   private updateColumnSize(index: number, width: number) {
      this.columnWidths[index] = width;
      this.headerColGroup.childNodes[index].style.width = width + "px";
      this.bodyColGroup.childNodes[index].style.width = width + "px";
      this.updateTableWidth();
   }

   private clearListeners() {
      this.windowListeners.forEach((listener) => listener());
      this.windowListeners = [];
   }

   private updateTableWidth() {
      let tableWidth = this.columnWidths.reduce((acc, width) => acc + width) - 16;
      this.renderer.setStyle(this.bodyTable, "width", tableWidth + "px");
      this.renderer.setStyle(this.headerTable, "width", tableWidth + "px");
   }
}
