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
import { Component, Input, Renderer2 } from "@angular/core";
import { VSSortingPaneModel } from "../../data/vs/vs-sorting-pane-model";
import { VSSortRefModel } from "../../data/vs/vs-sort-ref-model";

enum SortEnum {
   NONE,
   ASC,
   DESC
}

@Component({
   selector: "vs-sorting-pane",
   templateUrl: "vs-sorting-pane.component.html",
   styleUrls: ["./vs-sorting-pane.component.scss"]
})
export class VSSortingPane {
   @Input() model: VSSortingPaneModel;
   selectedField: VSSortRefModel;
   SortEnum = SortEnum;
   dragIndex: number = -1;
   dragCounter: number = 0;

   constructor(private renderer: Renderer2) {
   }

   swap(up: boolean): void {
      let index: number = this.model.columnSortList.indexOf(this.selectedField);
      let swapIndex: number = index + (up ? -1 : 1);
      let temp = this.model.columnSortList[swapIndex];
      this.model.columnSortList[swapIndex] = this.model.columnSortList[index];
      this.model.columnSortList[index] = temp;
   }

   moveSortColEnabled(up: boolean): boolean {
      if(this.selectedField && this.selectedField.order != SortEnum.NONE) {
         let index = this.model.columnSortList.indexOf(this.selectedField);

         if(up && index != 0) {
            return true;
         }
         else if(!up && index != this.model.columnSortList.length - 1) {
            return true;
         }
      }

      return false;
   }

   move(index: number, src: VSSortRefModel[], dest: VSSortRefModel[]): void {
      let temp: VSSortRefModel = src[index];
      this.selectedField = temp;

      if(dest === this.model.columnSortList) {
         this.model.columnSortList.push(temp);
      }
      else {
         this.model.columnNoneList.unshift(temp);
      }

      src.splice(index, 1);
   }

   fieldDragStart(evt: any, field: VSSortRefModel, dragIndex: number): void {
      this.selectedField = field;
      this.dragIndex = dragIndex;
      this.dragCounter = 0;
   }

   fieldDragEnter(evt: any, fieldIndex: number): void {
      if(this.dragIndex == fieldIndex) {
         return;
      }

      this.dragCounter++;

      if(this.dragIndex > fieldIndex) {
         this.renderer.addClass(evt.target, "bt-highlight");
      }
      else {
         this.renderer.addClass(evt.target, "bb-highlight");
      }
   }

   fieldDragLeave(evt: any, fieldIndex: number): void {
      if(this.dragIndex == fieldIndex) {
         return;
      }

      this.dragCounter--;

      if(this.dragCounter == 0) {
         if(this.dragIndex > fieldIndex) {
            this.renderer.removeClass(evt.target, "bt-highlight");
         }
         else {
            this.renderer.removeClass(evt.target, "bb-highlight");
         }
      }
   }

   fieldDrop(evt: any, fieldIndex: number): void {
      event.preventDefault();

      if(this.dragIndex == fieldIndex) {
         return;
      }

      let target: any = evt.target;

      if(target.parentNode.id == "sort-list-item") {
         target = target.parentNode;
      }

      if(this.dragIndex > fieldIndex) {
         this.renderer.removeClass(target, "bt-highlight");
      }
      else {
         this.renderer.removeClass(target, "bb-highlight");
      }

      this.model.columnSortList.splice(this.dragIndex, 1);
      this.model.columnSortList.splice(fieldIndex, 0, this.selectedField);
   }
}
