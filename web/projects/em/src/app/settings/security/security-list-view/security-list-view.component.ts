/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { Component, EventEmitter, Input, Output, } from "@angular/core";
import { SecurityProviderStatus } from "../security-provider/security-provider-model/security-provider-status-list";

@Component({
   selector: "em-security-list-view",
   templateUrl: "./security-list-view.component.html",
   styleUrls: ["./security-list-view.component.scss"]
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

   constructor() {
   }

   dragEnter(event: DragEvent): void {
      let target = <Element> event.currentTarget;

      if(target != this.dragSource) {
         const targetName = target.textContent.trim();
         this.toIndex = this.providers.findIndex(p => p.name === targetName);
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
      event.dataTransfer.effectAllowed = "move";
   }

   dragEnd(event: DragEvent): void {
      this.fromIndex = this.providers.indexOf(this.dragSource.textContent.trim());

      if(this.fromIndex >= 0 && this.toIndex >= 0 && this.fromIndex != this.toIndex) {
         this.reorder.emit([this.fromIndex, this.toIndex]);
         this.fromIndex = -1;
         this.toIndex = -1;
      }
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
