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
   Component,
   ElementRef,
   EventEmitter,
   Input,
   Output,
   QueryList,
   ViewChildren
} from "@angular/core";
import { Tool } from "../../../../../../shared/util/tool";
import { DragEvent } from "../../../common/data/drag-event";
import { DragService } from "../../services/drag.service";

@Component({
   selector: "sql-query-dialog-list",
   templateUrl: "sql-query-dialog-list.component.html",
   styleUrls: ["sql-query-dialog-list.component.scss"]
})
export class SQLQueryDialogListComponent {
   @Input() items: any[];
   @Input() labelFunction: (item: any) => string;
   @Input() multipleSelection: boolean = false;
   @Input() showEdit: boolean = false;
   @Input() showDelete: boolean = false;
   @Input() dragNames: string[] = [];
   @Input() reorderName: string;
   @Output() itemsChange = new EventEmitter<any[]>();
   @Output() itemEdited = new EventEmitter<any>();
   @Output() itemDeleted = new EventEmitter<number>();
   @Output() itemSelected = new EventEmitter<any>();
   @Output() itemsDropped = new EventEmitter<[any, number]>();
   @ViewChildren("item") itemsQuery: QueryList<ElementRef>;
   selectedIndexes: number[] = [];
   insertPosition: number;

   constructor(public dragService: DragService) {
   }

   selectAll(): void {
      this.selectedIndexes = [];

      for(let i = 0; i < this.items.length; i++) {
         this.selectedIndexes.push(i);
      }
   }

   clearSelection(): void {
      this.selectedIndexes = [];
   }

   getSelectedItems(): any[] {
      let selectedItems: any[] = [];

      for(let i = 0; i < this.selectedIndexes.length; i++) {
         selectedItems.push(this.items[this.selectedIndexes[i]]);
      }

      return selectedItems;
   }

   getLabel(item: string): string {
      return this.labelFunction ? this.labelFunction(item) : item;
   }

   deleteItem(index: number): void {
      this.itemDeleted.emit(index);

      // remove the index from the array
      if(this.selectedIndexes.indexOf(index) >= 0) {
         this.selectedIndexes.splice(this.selectedIndexes.indexOf(index), 1);
      }

      for(let i = 0; i < this.selectedIndexes.length; i++) {
         if(this.selectedIndexes[i] > index) {
            this.selectedIndexes[i]--;
         }
      }
   }

   indexSelected(index: number): void {
      if(this.selectedIndexes.indexOf(index) < 0) {
         if(this.multipleSelection) {
            this.selectedIndexes.push(index);
         }
         else {
            this.selectedIndexes = [index];
         }

         this.itemSelected.emit(this.items[index]);
      }
   }

   dragOverContainer(event: DragEvent) {
      if(this.allowDragOver()) {
         event.preventDefault();
         this.insertPosition = this.insertPositionDragOverContainer(event);
      }
   }

   drop(event: DragEvent, index: number) {
      event.preventDefault();
      event.stopPropagation();

      if(this.dragService.get(this.reorderName)) {
         this.reorderItems(index);
      }
      else {
         this.itemsDropped.emit([this.dragService.getDragData(), index]);
      }

      this.clearDragState();
   }

   /**
    * Move selected items to new index in the list.
    *
    * @param index the index to move the items to
    */
   private reorderItems(index: number) {
      let oldIndices: number[] = this.dragService.get(this.reorderName);
      let arrayCopy = [...this.items];
      let itemsToInsert = oldIndices.map((oldIndex) => this.items[oldIndex]);
      let deletePlaceholder = {};
      oldIndices.forEach((oldIndex) => arrayCopy[oldIndex] = deletePlaceholder);
      arrayCopy = [
         ...arrayCopy.slice(0, index),
         ...itemsToInsert,
         ...arrayCopy.slice(index)
      ];

      for(let i = arrayCopy.length - 1; i >= 0; i--) {
         if(arrayCopy[i] === deletePlaceholder) {
            arrayCopy.splice(i, 1);
         }
      }

      this.itemsChange.emit(arrayCopy);
   }

   dragOverItem(event: DragEvent, index: number) {
      if(this.allowDragOver()) {
         event.stopPropagation();
         event.preventDefault();
         this.insertPosition = this.insertPositionDragOverItem(event, index);
      }
   }

   dragLeave() {
      this.clearDragState();
   }

   insertPositionDragOverItem(event: DragEvent, index: number): number {
      let itemHeight = (event.target as HTMLElement).offsetHeight;
      let mouseY = event.offsetY;
      return mouseY > itemHeight / 2 ? index + 1 : index;
   }

   insertPositionDragOverContainer(event: DragEvent): number {
      if((this.itemsQuery.first &&
         event.offsetY <= this.itemsQuery.first.nativeElement.offsetTop) ||
         !this.items)
      {
         return 0;
      }
      else {
         return this.items.length;
      }
   }

   dragListItems(event: DragEvent) {
      Tool.setTransferData(event.dataTransfer, {});
      this.dragService.put(this.reorderName, this.selectedIndexes);
   }

   private allowDragOver(): boolean {
      let dragNames = [...this.dragNames];

      if(this.reorderName != null) {
         dragNames.push(this.reorderName);
      }

      let containedNames = dragNames.filter((name) => this.dragService.get(name) !== undefined);
      return containedNames.length > 0;
   }

   private clearDragState() {
      this.insertPosition = null;
   }
}
