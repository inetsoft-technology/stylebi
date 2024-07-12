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
import { HttpParams } from "@angular/common/http";
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { Tool } from "../../../../../../shared/util/tool";
import { ModelService } from "../../../widget/services/model.service";
import { ReorderColumnsDialogModel } from "../../data/vs/reorder-columns-dialog-model";
import { ColumnRef } from "../../../binding/data/column-ref";
import { MultiSelectList } from "../../../common/util/multi-select-list";
import {
   FeatureFlagsService,
   FeatureFlagValue
} from "../../../../../../shared/feature-flags/feature-flags.service";

@Component({
   selector: "reorder-columns-dialog",
   templateUrl: "reorder-columns-dialog.component.html",
   styleUrls: ["reorder-columns-dialog.component.scss"]
})
export class ReorderColumnsDialog implements OnInit {
   @Input() tableName: string;
   @Input() runtimeId: string;
   @Input() showColumnName: boolean;
   @Output() onCommit: EventEmitter<any> = new EventEmitter<any>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   private readonly RESTController = "../api/composer/ws/dialog/reorder-columns-dialog-model/";
   private readonly socketController = "/events/ws/dialog/reorder-columns-dialog-model/";
   private readonly columnSelection = new MultiSelectList();
   model: ReorderColumnsDialogModel;

   constructor(private modelService: ModelService)
   {
   }

   ngOnInit() {
      const params = new HttpParams().set("table", Tool.byteEncode(this.tableName));
      const uri = this.RESTController + Tool.byteEncode(this.runtimeId);

      this.modelService.getModel(uri, params).subscribe(
         (data) => {
            this.model = <ReorderColumnsDialogModel>data;
            this.columnSelection.setSize(this.model?.columns?.length || 0);
         },
         () => {
            console.error("Could not fetch sort information for " + this.tableName);
            this.cancel();
         }
      );
   }

   getTooltip(index: number) {
      return this.model.columns.length ? ColumnRef.getTooltip(this.model.columns[index]) : "";
   }

   getColumn(index: number): string {
      if(index >= this.model.columns.length) {
         return "";
      }
      else if(this.showColumnName && !!this.model.columns[index].name) {
         return this.model.columns[index].name;
      }
      else if(this.model.columns[index].alias) {
         return this.model.columns[index].alias;
      }

      if(this.model.columns[index].attribute == "") {
         return "Column[" + index + "]";
      }

      return this.model.columns[index].attribute;
   }

   getColumnDisplayString(index: number): string {
      if(index >= this.model.columns.length) {
         return "";
      }

      return this.model.columns[index].alias ?
         this.model.columns[index].alias : this.model.columns[index].attribute;
   }

   swap(currIndex: number, destIndex: number): void {
      let temp = this.model.indexes[currIndex];
      this.model.indexes[currIndex] = this.model.indexes[destIndex];
      this.model.indexes[destIndex] = temp;
   }

   setValueToIndex(index: number, value: number): void {
      this.model.indexes[index] = value;
   }

   private checkSwapAble(currIndex: number, destIndex: number): boolean {
      if(currIndex < 0 || currIndex >= this.columnSelection.size() || destIndex < 0 ||
         destIndex >= this.columnSelection.size())
      {
         return false;
      }

      return true;
   }

   public moveColumnsUp() {
      let selectedIndices = this.columnSelection.getSelectedIndices();

      for(let selectedIndex of selectedIndices) {
         if(!this.checkSwapAble(selectedIndex, selectedIndex - 1)) {
            break;
         }

         this.swap(selectedIndex, selectedIndex - 1);
         this.columnSelection.deselect(selectedIndex);
         this.columnSelection.ctrlSelect(selectedIndex - 1);
      }
   }

   public moveColumnsDown() {
      let selectedIndices = this.columnSelection.getSelectedIndices();

      for(let i = selectedIndices.length - 1; i >= 0; i--) {
         let selectedIndex = selectedIndices[i];

         if(!this.checkSwapAble(selectedIndex, selectedIndex + 1)) {
            break;
         }

         this.swap(selectedIndex, selectedIndex + 1);
         this.columnSelection.deselect(selectedIndex);
         this.columnSelection.ctrlSelect(selectedIndex + 1);
      }
   }

   moveColumnsToTop() {
      let bottomIndex = this.columnSelection.getSelectedIndices().length;
      let index = 0;
      let oldIndexes = [...this.model.indexes];
      let selectedCount = this.columnSelection.getSelectedIndices().length;

      for(let i = 0; i < this.columnSelection.size(); i++) {
         let setIndex;
         let selected = this.columnSelection.isSelected(i);

         if(!selected) {
            setIndex = bottomIndex++;
         }
         else {
            setIndex = index++;
         }

         this.setValueToIndex(setIndex, oldIndexes[i]);
      }

      this.columnSelection.clear();

      for(let i = 0; i < selectedCount; i++) {
         this.columnSelection.ctrlSelect(i);
      }
   }

   moveColumnsToBottom() {
      let selectedBottomIndex =
         this.columnSelection.size() - this.columnSelection.getSelectedIndices().length;
      let index = 0;
      let oldIndexes = [...this.model.indexes];
      let selectedCount = this.columnSelection.getSelectedIndices().length;

      for(let i = 0; i < this.columnSelection.size(); i++) {
         let setIndex;
         let selected = this.columnSelection.isSelected(i);

         if(!selected) {
            setIndex = index++;
         }
         else {
            setIndex = selectedBottomIndex++;
         }

         this.setValueToIndex(setIndex, oldIndexes[i]);
      }

      this.columnSelection.clear();

      for(let i = 0; i < selectedCount; i++) {
         this.columnSelection.ctrlSelect(this.columnSelection.size() - 1 - i);
      }
   }

   get upDisabled(): boolean {
      return !this.columnSelection?.hasSelection() || this.columnSelection?.isSelected(0);
   }

   get toTopDisabled(): boolean {
      if(!this.columnSelection?.hasSelection()) {
         return true;
      }

      let selectedIndices = this.columnSelection.getSelectedIndices();

      if(selectedIndices[0] > 0) {
         return false;
      }

      let preIndex = -1;

      for(let selectedIndex of selectedIndices) {
         if(preIndex != -1 && selectedIndex - preIndex > 1) {
            return false;
         }

         preIndex = selectedIndex;
      }

      return true;
   }

   get downDisabled(): boolean {
      return !this.columnSelection?.hasSelection() ||
         this.columnSelection?.isSelected(this.columnSelection.size() - 1);
   }

   get toBottomDisabled(): boolean {
      if(!this.columnSelection?.hasSelection()) {
         return true;
      }

      let selectedIndices = this.columnSelection.getSelectedIndices();

      if(selectedIndices[selectedIndices.length - 1] < this.columnSelection.size() - 1) {
         return false;
      }

      let preIndex = selectedIndices[selectedIndices.length - 1];

      for(let i = selectedIndices.length - 2; i >= 0; i--) {
         let selectedIndex = selectedIndices[i];

         if(preIndex - selectedIndex > 1) {
            return false;
         }

         preIndex = selectedIndex;
      }

      return true;
   }

   selectItem(event: MouseEvent, index: number): void {
      this.columnSelection.selectWithEvent(index, event);
   }

   isSelectedItem(index: number): boolean {
      return this.columnSelection.isSelected(index);
   }

   ok(): void {
      this.model.columns = []; // not needed on server
      this.onCommit.emit({
         model: this.model,
         controller: this.socketController + Tool.byteEncode(this.tableName)
      });
   }

   cancel(): void {
      this.onCancel.emit("cancel");
   }
}
