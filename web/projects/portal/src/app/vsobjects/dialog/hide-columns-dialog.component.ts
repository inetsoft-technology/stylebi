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
import { Component, Input, Output, EventEmitter } from "@angular/core";
import { HideColumnsDialogModel } from "../model/hide-columns-dialog-model";

@Component({
   selector: "hide-columns-dialog",
   templateUrl: "hide-columns-dialog.component.html",
   styleUrls: ["./hide-columns-dialog.component.scss"]
})
export class HideColumnsDialog {
   @Input() model: HideColumnsDialogModel;
   selectedAvailableColumnIndexes: number[] = [];
   selectedHiddenColumnIndexes: number[] = [];
   private prevSelectedAvailable: number = -1;
   private prevSelectedHidden: number = -1;
   @Output() onCommit: EventEmitter<HideColumnsDialogModel> =
      new EventEmitter<HideColumnsDialogModel>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   @Output() onApply = new EventEmitter<{collapse: boolean, result: any}>();

   selectAvailable(event: MouseEvent, index: number) {
      this.prevSelectedAvailable = index;

      let list: number[] = this.selectedAvailableColumnIndexes;

      if(list.indexOf(index) != -1) {
         return;
      }

      if(event.shiftKey && this.prevSelectedAvailable) {
         let start: number = index < this.prevSelectedAvailable ? index : this.prevSelectedAvailable;
         let end: number = index < this.prevSelectedAvailable ? this.prevSelectedAvailable : index;

         for(let i = start; i <= end; i++) {
            if(list.indexOf(i) == -1) {
               list.push(i);
            }
         }
      }
      else if(event.ctrlKey || event.metaKey || event.shiftKey) {
         list.push(index);
      }
      else {
         list = [index];
      }

      this.selectedAvailableColumnIndexes = list;
   }

   selectHidden(event: MouseEvent, index: number) {
      this.prevSelectedHidden = index;

      let list: number[] = this.selectedHiddenColumnIndexes;

      if(list.indexOf(index) != -1) {
         return;
      }

      if(event.shiftKey && this.prevSelectedHidden) {
         let start: number = index < this.prevSelectedHidden ? index : this.prevSelectedHidden;
         let end: number = index < this.prevSelectedHidden ? this.prevSelectedHidden : index;

         for(let i = start; i <= end; i++) {
            if(list.indexOf(i) == -1) {
               list.push(i);
            }
         }
      }
      else if(event.ctrlKey || event.metaKey || event.shiftKey) {
         list.push(index);
      }
      else {
         list = [index];
      }

      this.selectedHiddenColumnIndexes = list;
   }

   isAvailableSelected(index: number): boolean {
      return this.selectedAvailableColumnIndexes.indexOf(index) != -1;
   }

   isHiddenSelected(index: number): boolean {
      return this.selectedHiddenColumnIndexes.indexOf(index) != -1;
   }

   add(): void {
      this.selectedAvailableColumnIndexes.sort(function(a, b){ return b - a; });

      for(let index of this.selectedAvailableColumnIndexes) {
         let item: string = this.model.availableColumns[index];
         this.model.hiddenColumns.push(item);
      }

      const lastIdx: number = this.selectedAvailableColumnIndexes[0];
      this.selectedAvailableColumnIndexes = [];

      // auto select next index
      if(this.model.hiddenColumns.length < this.model.availableColumns.length) {
         for(let i = lastIdx + 1; i < this.model.availableColumns.length; i++) {
            if(this.model.hiddenColumns.indexOf(this.model.availableColumns[i]) < 0) {
               this.selectedAvailableColumnIndexes = [i];
               break;
            }
         }

         if(this.selectedAvailableColumnIndexes.length == 0) {
            this.selectedAvailableColumnIndexes = [this.model.availableColumns.reverse().findIndex(
               v => this.model.hiddenColumns.indexOf(v) < 0)];
         }
      }
   }

   remove(): void {
      this.selectedHiddenColumnIndexes.sort(function(a, b){ return b - a; });

      for(let index of this.selectedHiddenColumnIndexes) {
         if(this.model.availableColumns.indexOf(this.model.hiddenColumns[index]) == -1) {
            this.model.availableColumns.push(this.model.hiddenColumns[index]);
         }

         this.model.hiddenColumns.splice(index, 1);
      }

      if(this.model.hiddenColumns.length > 0) {
         const lastIdx: number = this.selectedHiddenColumnIndexes[0];
         this.selectedHiddenColumnIndexes = [
            Math.min(lastIdx - this.selectedHiddenColumnIndexes.length + 1,
                     this.model.hiddenColumns.length - 1)];
      }
      else {
         this.selectedHiddenColumnIndexes = [];
      }
   }

   ok(): void {
      this.onCommit.emit(this.model);
   }

   apply(event: boolean): void {
      this.onApply.emit({collapse: event, result: this.model});
   }

   cancel(): void {
      this.onCancel.emit("cancel");
   }
}
