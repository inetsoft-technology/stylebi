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
   Directive,
   ElementRef, EventEmitter,
   Input, OnChanges,
   OnInit, Output,
   Renderer2, SimpleChanges
} from "@angular/core";

@Directive({
   selector: "[sortColumn]"
})
export class SortColumnDirective implements OnInit, OnChanges {
   @Input() data: any[];
   @Input() sortKey: any;
   @Input() sortType: any;
   @Input() enableUnsorted = false;
   @Output() sortTypeChanged: EventEmitter<any> = new EventEmitter();
   private toggleSort: boolean = false;

   constructor(private el: ElementRef, private renderer: Renderer2) {
   }

   ngOnChanges(changes: SimpleChanges) {
      if(changes.hasOwnProperty("sortType") && this.sortType != this.sortKey) {
         this.hideSortIcon();
      }
      else if(changes["data"]) {
         if(this.sortType == this.sortKey) {
            this.sortArray();
         }
      }
   }

   ngOnInit(): void {
      this.renderer.listen(this.el.nativeElement, "click", () => {
         if(this.data && this.sortKey) {
            if(this.enableUnsorted && !this.toggleSort && this.sortType == this.sortKey) {
               this.hideSortIcon();
               this.sortTypeChanged.emit("");
            }
            else {
               this.toggleSort = !this.toggleSort;
               this.sortArray();
            }
         }
      });
   }

   sortArray(): Array<any> {
      let tempArray: Array<any> = this.data;
      tempArray.sort((a, b) => {
         let str1: string = a[this.sortKey];
         let str2: string = b[this.sortKey];

         str1 = str1 ? (str1 + "").toLowerCase() : "";
         str2 = str2 ? (str2 + "").toLowerCase() : "";

         if(this.toggleSort) {
            if(str1 < str2) {
               return -1;
            }

            if(str1 > str2) {
               return 1;
            }
         }
         else {
            if(str1 > str2) {
               return -1;
            }

            if(str1 < str2) {
               return 1;
            }
         }

         return this.toggleSort ?
            a[this.sortKey] > b[this.sortKey] ? 1 : -1 :
            a[this.sortKey] > b[this.sortKey] ? -1 : 1;
      });

      this.updateSortIcon();
      this.sortTypeChanged.emit(this.sortKey);
      return tempArray;
   }

   private updateSortIcon() {
      const element = this.el.nativeElement;
      let iconElement = element.querySelector(".sort-column-sort-icon");

      if(iconElement) {
         this.renderer.removeClass(iconElement,
            this.toggleSort ? "sort-descending-icon" : "sort-ascending-icon");
         this.renderer.addClass(iconElement,
            this.toggleSort ? "sort-ascending-icon" : "sort-descending-icon");
         this.renderer.setStyle(iconElement, "display", "inline-block");
      }
      else {
         iconElement = this.renderer.createElement("i");
         this.renderer.addClass(iconElement, "sort-column-sort-icon");
         this.renderer.addClass(iconElement,
            this.toggleSort ? "sort-ascending-icon" : "sort-descending-icon");
         this.renderer.setStyle(iconElement, "display", "inline-block");
         this.renderer.setStyle(iconElement, "vertical-align", "middle");
         this.renderer.appendChild(element, iconElement);
      }
   }

   private hideSortIcon() {
      const element = this.el.nativeElement;
      let iconElement = element.querySelector(".sort-column-sort-icon");
      this.toggleSort = false;

      if(iconElement) {
         this.renderer.setStyle(iconElement, "display", "none");
      }
   }
}
