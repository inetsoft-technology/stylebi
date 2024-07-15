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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { ListValuesPaneModel } from "../../data/vs/list-values-pane-model";
import { ComboboxGeneralPaneModel } from "../../data/vs/combobox-general-pane-model";

@Component({
   selector: "list-values-pane",
   templateUrl: "list-values-pane.component.html",
})
export class ListValuesPane implements OnInit {
   @Input() model: ListValuesPaneModel;
   @Input() variableValues: string[];
   @Input() runtimeId: string;
   @Input() general: ComboboxGeneralPaneModel;
   @Input() isComboBox: boolean = false;
   @Input() isCheckBox: boolean = false;
   @Output() isInputValid: EventEmitter<boolean> = new EventEmitter<boolean>();

   constructor() {
   }

   ngOnInit() {
      if(!this.model.sortType) {
         this.model.sortType = 0;
         this.toggleSort(0);
      }
   }

   toggleSort(sort: number): void {
      if(sort != 0) {
         this.model.embeddedDataDown = false;
      }
   }

   onValidChange(valid: boolean) {
      this.isInputValid.emit(valid);
   }
}
