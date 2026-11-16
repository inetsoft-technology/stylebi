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
import { Component, Input, Output, EventEmitter } from "@angular/core";

import { ScrollableTableDirective } from "../../../../widget/scrollable-table/scrollable-table.directive";

@Component({
    selector: "editable-table",
    templateUrl: "editable-table.component.html",
    styleUrls: ["editable-table.component.scss"],
    imports: [ScrollableTableDirective]
})
export class EditableTableComponent {
   @Input() items: any[];
   @Input() title: string;
   @Input() selectedItems: number[] = [];
   @Output() selectedItemsChange: EventEmitter<number[]> = new EventEmitter<number[]>();
   // anchor of a shift-click range; moved by plain and ctrl clicks, not by shift clicks
   lastClickedItemIndex: number = -1;

   public selectItem(event: MouseEvent, index: number): void {
      if(event.ctrlKey || event.metaKey) {
         const placement: number = this.selectedItems.indexOf(index);
         this.lastClickedItemIndex = index;

         if(placement > -1) {
            this.selectedItems.splice(placement, 1);
         }
         else {
            this.selectedItems.push(index);
         }
      }
      else if(event.shiftKey) {
         if(this.selectedItems.length > 0) {
            // the selection can also be changed by the parent (e.g. a newly added row), so
            // fall back to the last selected row when the anchor is no longer selected
            const anchor: number = this.selectedItems.indexOf(this.lastClickedItemIndex) > -1 ?
               this.lastClickedItemIndex : this.selectedItems[this.selectedItems.length - 1];
            const smaller: number = Math.min(anchor, index);
            const larger: number = Math.max(anchor, index);
            const range: number[] = [];

            // replace the selection with the (duplicate free) range from the anchor
            for(let i = smaller; i <= larger; i++) {
               range.push(i);
            }

            this.selectedItems = range;
         }
         else {
            this.lastClickedItemIndex = index;
            this.selectedItems = [index];
         }
      }
      else {
         this.lastClickedItemIndex = index;
         this.selectedItems = [index];
      }

      this.selectedItemsChange.emit(this.selectedItems);
   }
}
