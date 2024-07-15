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
import { Input, AfterContentInit, AfterContentChecked, Directive, ElementRef } from "@angular/core";

declare const window;

@Directive({
   selector: "table[wScrollableTable]"
})
export class ScrollableTableDirective implements AfterContentInit, AfterContentChecked {
   @Input() fixedWidths: number[] = null;

   constructor(private element: ElementRef) {
      this.element.nativeElement.classList.add("scrollable-table");
   }

   setColumnWidths(init: boolean): void {
      const table = this.element.nativeElement;
      const thead = table.querySelector("thead");
      const tbody = table.querySelector("tbody");
      const headCells = table.querySelectorAll("thead > tr:first-of-type > th");
      const bodyCells = table.querySelectorAll("tbody > tr:first-of-type > td");

      if(init) {
         // apply min-widths from no wrap headers
         for(let i = 0; i < bodyCells.length; i++) {
            if(headCells[i].offsetWidth > bodyCells[i].offsetWidth) {
               bodyCells[i].style.width = `${headCells[i].offsetWidth}px`;
            }
         }
      }
      // body still empty after check content, restore header cells width.
      else if(bodyCells.length == 0) {
         for(let i = 0; i < headCells.length; i++) {
            headCells[i].style.width = `${thead.offsetWidth / headCells.length}px`;
         }

         return;
      }

      // clear and calculate preferred width
      for(let i = 0; i < headCells.length; i++) {
         headCells[i].style.width = null;
      }

      const colws: number[] = [];
      let totalw: number = 0;

      for(let i = 0; i < bodyCells.length; i++) {
         const w = Math.max(bodyCells[i].offsetWidth, headCells[i].offsetWidth);
         colws.push(w);
         totalw += w;
      }

      const tableStyle = window.getComputedStyle(table);

      if(tbody.style.height == null) {
         let tableHeight: string;

         if(tableStyle.maxHeight && tableStyle.maxHeight !== "none") {
            tableHeight = tableStyle.maxHeight;
         }
         else {
            tableHeight = tableStyle.height;
         }

         if(tableHeight) {
            tbody.style.height = `calc(${tableHeight} - ${thead.offsetHeight}px)`;
         }
      }

      const scroll = tbody.scrollHeight > table.offsetHeight - thead.offsetHeight;
      const bodyw = Math.min(tbody.offsetWidth, table.parentElement.offsetWidth);
      const contentw = scroll ? bodyw - 16 : bodyw;

      for(let i = 0; i < bodyCells.length; i++) {
         // distribute width
         const colw: number = this.fixedWidths
            ? Math.round(this.fixedWidths[i] * contentw)
            : Math.round(colws[i] * contentw / totalw);
         headCells[i].style.width = scroll && i == bodyCells.length - 1
            ? `${colw + 16}px` : `${colw}px`;
         headCells[i].style.maxWidth = scroll && i == bodyCells.length - 1
            ? `${colw + 16}px` : `${colw}px`;
         bodyCells[i].style.width = `${colw}px`;
         bodyCells[i].style.maxWidth = `${colw}px`;
      }
   }

   public ngAfterContentInit(): void {
      const table = this.element.nativeElement;
      const thead = table.querySelector("thead");
      const headCells = table.querySelectorAll("thead > tr:first-of-type > th");
      const bodyCells = table.querySelectorAll("tbody > tr:first-of-type > td");

      if(bodyCells.length == 0) {
         for(let i = 0; i < headCells.length; i++) {
            headCells[i].style.width = `${thead.offsetWidth / headCells.length}px`;
         }

         return;
      }

      this.setColumnWidths(true);
   }

   public ngAfterContentChecked(): void {
      this.setColumnWidths(false);
   }
}
