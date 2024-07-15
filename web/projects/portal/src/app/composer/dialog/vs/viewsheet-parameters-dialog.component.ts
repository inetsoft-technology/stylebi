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
import { Component, Input, Output, EventEmitter, OnInit } from "@angular/core";
import { ViewsheetParametersDialogModel } from "../../data/vs/viewsheet-parameters-dialog-model";

@Component({
   selector: "viewsheet-parameters-dialog",
   templateUrl: "viewsheet-parameters-dialog.component.html",
   styleUrls: ["viewsheet-parameters-dialog.component.scss"]
})
export class ViewsheetParametersDialog implements OnInit{
   @Input() model: ViewsheetParametersDialogModel;
   @Output() onCommit: EventEmitter<ViewsheetParametersDialogModel> =
      new EventEmitter<ViewsheetParametersDialogModel>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   selectedEnabledIndexes: number[] = [];
   selectedDisabledIndexes: number[] = [];

   ngOnInit() {
      this.selectedEnabledIndexes = this.model.enabledParameters.length > 0 ? [0] : [];
      this.selectedDisabledIndexes = this.model.disabledParameters.length > 0 ? [0] : [];
   }

   disableParameter(): void {
      this.selectedEnabledIndexes = this.selectedEnabledIndexes.sort((a, b) => b - a);
      this.selectedEnabledIndexes.forEach((index) => {
         const disabledParameter: string[] = this.model.enabledParameters.splice(index, 1);
         this.model.disabledParameters.push(disabledParameter[0]);
      });
      this.selectedEnabledIndexes = this.model.enabledParameters.length == 0 ? [] : [0];
      this.selectedDisabledIndexes = [this.model.disabledParameters.length - 1];
   }

   enableParameter(): void {
      this.selectedDisabledIndexes = this.selectedDisabledIndexes.sort((a, b) => b - a);
      this.selectedDisabledIndexes.forEach((index) => {
         const enabledParameter: string[] = this.model.disabledParameters.splice(index, 1);
         this.model.enabledParameters.push(enabledParameter[0]);
      });
      this.selectedDisabledIndexes = this.model.disabledParameters.length == 0 ? [] : [0];
      this.selectedEnabledIndexes = [this.model.enabledParameters.length - 1];
   }

   disableAll(): void {
      this.model.disabledParameters = this.model.disabledParameters.concat(this.model.enabledParameters);
      this.model.enabledParameters = [];
      this.selectedEnabledIndexes = [];
      this.selectedDisabledIndexes = [this.model.disabledParameters.length - 1];
   }

   enableAll(): void {
      this.model.enabledParameters = this.model.enabledParameters.concat(this.model.disabledParameters);
      this.model.disabledParameters = [];
      this.selectedDisabledIndexes = [];
      this.selectedEnabledIndexes = [this.model.enabledParameters.length - 1];
   }

   select(enable: boolean, index: number, evt: MouseEvent) {
      if(enable) {
         const posInSelected = this.selectedEnabledIndexes.indexOf(index);

         if(!evt.ctrlKey && !evt.metaKey && !evt.shiftKey) {
            this.selectedEnabledIndexes = [];
         }

         if(evt.shiftKey) {
            if(this.selectedEnabledIndexes == null || this.selectedEnabledIndexes.length == 0) {
               this.selectedEnabledIndexes.push(index);
               return;
            }

            const last = this.selectedEnabledIndexes[this.selectedEnabledIndexes.length - 1];
            this.selectedEnabledIndexes = [];

            // First add all values between new selected index and last
            for (let i = Math.min(index, last) + 1; i < Math.max(index, last); i++) {
               this.selectedEnabledIndexes.push(i);
            }

            // Then add the new selected index
            this.selectedEnabledIndexes.push(index);

            // Keep the last index unchanged by pushing it in the end
            if(last != index) {
               this.selectedEnabledIndexes.push(last);
            }
         }
         else if(evt.ctrlKey) {
            if(posInSelected >= 0) {
               this.selectedEnabledIndexes.splice(posInSelected, 1);
            }
            else {
               this.selectedEnabledIndexes.push(index);
            }
         }
         else {
            this.selectedEnabledIndexes.push(index);
         }
      }
      else {
         const posInSelected = this.selectedDisabledIndexes.indexOf(index);

         if(!evt.ctrlKey && !evt.metaKey && !evt.shiftKey) {
            this.selectedDisabledIndexes = [];
         }

         if(evt.shiftKey) {
            if(this.selectedDisabledIndexes == null || this.selectedDisabledIndexes.length == 0) {
               this.selectedDisabledIndexes.push(index);
               return;
            }

            const last = this.selectedDisabledIndexes[this.selectedDisabledIndexes.length - 1];
            this.selectedDisabledIndexes = [];

            for (let i = Math.min(index, last) + 1; i < Math.max(index, last); i++) {
               this.selectedDisabledIndexes.push(i);
            }

            this.selectedDisabledIndexes.push(index);

            if(last != index) {
               this.selectedDisabledIndexes.push(last);
            }
         }
         else if(evt.ctrlKey) {
            if(posInSelected >= 0) {
               this.selectedDisabledIndexes.splice(posInSelected, 1);
            }
            else {
               this.selectedDisabledIndexes.push(index);
            }
         }
         else {
            this.selectedDisabledIndexes.push(index);
         }
      }
   }

   isSelected(enable: boolean, index: number) {
      return enable ? this.selectedEnabledIndexes.indexOf(index) != -1 : this.selectedDisabledIndexes.indexOf(index) != -1;
   }

   cancelChanges(): void {
      this.onCancel.emit("cancel");
   }

   saveChanges(): void {
      this.onCommit.emit(this.model);
   }

   moveParameter(fromRow: number, toRow: number): void {
      const fromElem = this.model.enabledParameters[fromRow];
      const toElem = this.model.enabledParameters[toRow];
      this.model.enabledParameters.splice(fromRow, 1, toElem);
      this.model.enabledParameters.splice(toRow, 1, fromElem);
   }

   canMoveDown(index: number): boolean {
      return index !== this.model.enabledParameters.length - 1;
   }

   canMoveUp(index: number): boolean {
      return index !== 0;
   }
}
