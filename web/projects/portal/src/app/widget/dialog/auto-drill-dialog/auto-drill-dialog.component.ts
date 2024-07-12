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
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { XConstants } from "../../../common/util/xconstants";
import { VSAutoDrillDialogModel } from "../../../vsobjects/model/vs-auto-drill-dialog-model";
import { SortInfo } from "../../../vsobjects/objects/table/sort-info";

@Component({
   selector: "auto-drill-dialog",
   templateUrl: "auto-drill-dialog.component.html",
   styleUrls: ["auto-drill-dialog.component.scss"]
})
export class AutoDrillDialog {
   @Input() model: VSAutoDrillDialogModel;
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   @Output() onCommit: EventEmitter<any> = new EventEmitter<any>();
   @Output() onSort: EventEmitter<any> = new EventEmitter<any>();
   row: string[] = null;
   headers: string[] = [];
   bodyRows: string[][] = null;
   sortInfo: SortInfo = null;
   selectedRow: boolean = false;

   get drillHeader(): string[] {
      return this.model.drills[0];
   }

   get drillBody(): string[][] {
      return this.model.drills.slice(1);
   }

   isSelected(selRow: string[]): boolean {
      return this.row == selRow;
   }

   selectRow(selRow: string[]): void {
      this.row = selRow;
      this.selectedRow = true;
   }

   ok(): void {
      this.onCommit.emit(this.row);
   }

   cancel(): void {
      this.onCancel.emit("cancel");
   }

   sortClicked(col: number): void {
      if(this.sortInfo && this.sortInfo.col === col) {
         switch(this.sortInfo.sortValue) {
            case XConstants.SORT_NONE:
               this.sortInfo.sortValue = XConstants.SORT_ASC;
               break;
            case XConstants.SORT_ASC:
               this.sortInfo.sortValue = XConstants.SORT_DESC;
               break;
            case XConstants.SORT_DESC:
               this.sortInfo.sortValue = XConstants.SORT_NONE;
         }
      }
      else {
         this.sortInfo = <SortInfo> {
            col: col,
            field: this.drillHeader[col],
            sortValue: XConstants.SORT_ASC
         };
      }

      this.onSort.emit(this.sortInfo);
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
      else {
         return "sort";
      }

      return null;
   }
}
