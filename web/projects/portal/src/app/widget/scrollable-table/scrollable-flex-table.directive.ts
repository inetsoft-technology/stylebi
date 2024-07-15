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
import { AfterContentInit, AfterContentChecked, Directive, ElementRef, Input } from "@angular/core";

declare const window;

@Directive({
   selector: "table[wScrollableFlexTable]"
})
export class ScrollableFlexTableDirective implements AfterContentChecked {
   @Input()
   wScrollableSetBodyWidths = false;

   constructor(private element: ElementRef) {
   }

   setColumnWidths(): void {
      const table = this.element.nativeElement;
      const thead = table.querySelector("thead");
      const tbody = table.querySelector("tbody");
      const headCells = table.querySelectorAll("thead > tr:first-of-type > th");
      const bodyCells = table.querySelectorAll("tbody > tr:first-of-type > td");
      const scroll = tbody.scrollHeight > table.offsetHeight - thead.offsetHeight;
      let x = 0;

      if(scroll && bodyCells.length == headCells.length) {
         for(let i = 0; i < headCells.length - 1; i++) {
            x += bodyCells[i].offsetWidth;
            headCells[i].style.width = `${(bodyCells[i].offsetWidth / thead.offsetWidth) * 100}%`;
         }

         headCells[headCells.length - 1].style.width = `${(1 - x / thead.offsetWidth) * 100}%`;

         if(this.wScrollableSetBodyWidths) {
            for(let row of table.querySelectorAll("tbody > tr")) {
               const cols = Math.min(headCells.length, row.cells.length);

               for(let i = 0; i < cols; i++) {
                  row.cells[i].style.width = headCells[i].style.width;
               }
            }
         }
      }
      else if(!scroll && bodyCells.length == headCells.length) {
         for(let i = 0; i < headCells.length; i++) {
            headCells[i].style.width = `${(bodyCells[i].offsetWidth / thead.offsetWidth) * 100}%`;
         }

         if(this.wScrollableSetBodyWidths) {
            for(let row of table.querySelectorAll("tbody > tr")) {
               const cols = Math.min(headCells.length, row.cells.length);

               for(let i = 0; i < cols; i++) {
                  row.cells[i].style.width = headCells[i].style.width;
               }
            }
         }
      }
   }

   public ngAfterContentChecked(): void {
      this.setColumnWidths();
   }
}
