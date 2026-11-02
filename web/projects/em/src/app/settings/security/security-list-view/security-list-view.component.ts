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
import { Component, EventEmitter, Input, Output, } from "@angular/core";
import { SecurityProviderStatus } from "../security-provider/security-provider-model/security-provider-status-list";
import { MatIcon } from "@angular/material/icon";
import { MatIconButton } from "@angular/material/button";

import { MatList, MatListItem } from "@angular/material/list";

@Component({
    selector: "em-security-list-view",
    templateUrl: "./security-list-view.component.html",
    styleUrls: ["./security-list-view.component.scss"],
    imports: [MatList, MatListItem, MatIconButton, MatIcon]
})
export class SecurityListViewComponent {
   @Input() providers: SecurityProviderStatus[];
   @Output() reorder = new EventEmitter<[number, number]>();
   @Output() moveProviderUp = new EventEmitter<number>(); //Probably should pass a non primitive value
   @Output() moveProviderDown = new EventEmitter<number>();
   @Output() removeProvider = new EventEmitter<number>();
   @Output() editProvider = new EventEmitter<string>();
   @Output() clearProviderCache = new EventEmitter<number>();
   @Output() copyProvider = new EventEmitter<number>();
   dragSource: any;
   fromIndex: number = -1;
   toIndex: number = -1;
   private dragSourceNext: Node;

   constructor() {
   }

   dragEnter(event: DragEvent): void {
      let target = <Element> event.currentTarget;

      // a row whose view was removed during the drag is never put back
      if(!this.dragSource || this.dragSource.parentNode !== target.parentNode) {
         return;
      }

      if(this.isBefore(this.dragSource, target)) {
         target.parentNode.insertBefore(this.dragSource, target);
      }
      else {
         target.parentNode.insertBefore(this.dragSource, target.nextSibling); //insert after
      }
   }

   dragStart(event: DragEvent): void {
      this.dragSource = event.currentTarget;
      this.dragSourceNext = this.dragSource.nextSibling;
      this.fromIndex = this.rowIndex(this.dragSource);
      event.dataTransfer.effectAllowed = "move";
   }

   // Bug #78723, take both indices from the row's position among the rows, not from its text,
   // which also holds the cache age. Put the row back and let the page move it once the server
   // accepts the new order, so a failed request leaves the rows in the providers' order.
   dragEnd(event: DragEvent): void {
      const row: Element = this.dragSource;

      if(row) {
         this.toIndex = this.rowIndex(row);

         const next = this.dragSourceNext;

         if(row.parentNode && (!next || next.parentNode === row.parentNode)) {
            row.parentNode.insertBefore(row, next);
         }

         if(this.fromIndex >= 0 && this.toIndex >= 0 && this.fromIndex != this.toIndex) {
            this.reorder.emit([this.fromIndex, this.toIndex]);
         }
      }

      this.fromIndex = -1;
      this.toIndex = -1;
      this.dragSource = null;
      this.dragSourceNext = null;
   }

   private rowIndex(row: Element): number {
      if(!row?.parentNode) {
         return -1;
      }

      return Array.from(row.parentNode.children)
         .filter(child => child.classList.contains("list-item"))
         .indexOf(row);
   }

   isBefore(a: Element, b: Element): boolean {
      if(a.parentNode == b.parentNode) {
         for(let cur = a; cur; cur = cur.previousElementSibling) {
            if(cur === b) {
               return true;
            }
         }
      }

      return false;
   }

}
