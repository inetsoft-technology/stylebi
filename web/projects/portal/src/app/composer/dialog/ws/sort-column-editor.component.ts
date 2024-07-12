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
   ChangeDetectionStrategy,
   Component,
   EventEmitter,
   Input,
   OnChanges,
   Output,
   SimpleChanges
} from "@angular/core";
import { SortColumnEditorModel } from "../../data/ws/sort-column-editor-model";
import { Tool } from "../../../../../../shared/util/tool";

enum SortEnum {
   NONE = 0,
   ASC = 1,
   DESC = 2
}

interface Column {
   readonly name: string;
   readonly label: string;
   readonly tooltip: string;
}

interface SortRef {
   name: string;
   order: number;
}

@Component({
   selector: "sort-column-editor",
   templateUrl: "sort-column-editor.component.html",
   changeDetection: ChangeDetectionStrategy.OnPush
})
export class SortColumnEditor implements OnChanges {
   public SortEnum = SortEnum;
   @Input() model: SortColumnEditorModel;
   @Input() showColumnName: boolean;
   @Output() modelChange = new EventEmitter<SortColumnEditorModel>();
   readonly empty = " ";
   columnList: Column[];
   sortRefs: SortRef[];

   ngOnChanges(changes: SimpleChanges): void {
      this.resetSortRefs();
      this.resetColumnList();
   }

   private resetSortRefs(): void {
      this.sortRefs = [];

      for(let i = 0; i < this.model.selectedColumns.length; i++) {
         this.sortRefs.push({
            name: this.model.selectedColumns[i],
            order: this.model.orders[i]
         });
      }

      this.sortRefs.push({name: this.empty, order: SortEnum.NONE});
   }

   private resetColumnList(): void {
      this.columnList = [{name: this.empty, label: "", tooltip: ""}];

      for(let i = 0; i < this.model.availableColumns.length; i++) {
         let name = this.model.availableColumns[i];
         let label = name;

         if(!this.showColumnName && this.model.aliasMap[name]) {
            label = this.model.aliasMap[name];
         }

         this.columnList.push({
            name, label: label.replace("OUTER_", ""),
            tooltip: this.getTooltip(name, this.model.originalNames[i],
               this.model.columnDescriptions[i], this.model.rangeColumns[i] == "true")
         });
      }
   }

   getTooltip(name: string, originalName: string, description: string,
              isRange: boolean): string
   {
      let tooltip: string = "";

      if(isRange) {
         tooltip = name + " (" + originalName + ")" + (!Tool.isEmpty(description) ?
            "\n" + description : "");
      }
      else {
         tooltip = "Alias: " + name + " (" + originalName + ")" + (!Tool.isEmpty(description) ?
            "\nDescription: " + description : "");
      }

      return tooltip;
   }

   updateModel(): void {
      const orders: number[] = [];
      const selectedColumns: string[] = [];

      for(let sortRef of this.sortRefs) {
         if(sortRef.name !== this.empty) {
            orders.push(sortRef.order);
            selectedColumns.push(sortRef.name);
         }
      }

      this.modelChange.emit({...this.model, orders, selectedColumns});
   }

   swap(currIndex: number, destIndex: number): void {
      let temp = this.sortRefs[currIndex];
      this.sortRefs[currIndex] = this.sortRefs[destIndex];
      this.sortRefs[destIndex] = temp;
      this.updateModel();
   }

   updateRow(name: string, index: number): void {
      const sortRef = this.sortRefs[index];
      sortRef.name = name;

      if(sortRef.order === SortEnum.NONE) {
         sortRef.order = SortEnum.ASC;
      }

      this.updateModel();
   }

   updateOrder(order: number, index: number): void {
      this.sortRefs[index].order = order;
      this.updateModel();
   }

   isOptionDisabled(optionName: string, sortRefName: string): boolean {
      let disabled: boolean;

      if(optionName === this.empty || optionName === sortRefName) {
         disabled = false;
      }
      else {
         disabled = this.model.selectedColumns.indexOf(optionName) >= 0;
      }

      return disabled;
   }
}
