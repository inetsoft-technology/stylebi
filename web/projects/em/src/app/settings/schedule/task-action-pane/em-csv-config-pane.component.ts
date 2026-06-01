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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { CSVConfigModel } from "../../../../../../shared/schedule/model/csv-config-model";
import { MatDialog } from "@angular/material/dialog";
import { FormBuilder, FormGroup, Validators, FormsModule } from "@angular/forms";
import { MatDivider } from "@angular/material/divider";
import { MatList, MatListItem } from "@angular/material/list";
import { MatLine } from "@angular/material/core";
import { MatCard, MatCardContent } from "@angular/material/card";

import { MatCheckbox } from "@angular/material/checkbox";
import { MatInput } from "@angular/material/input";
import { MatFormField, MatLabel, MatError } from "@angular/material/form-field";

@Component({
    selector: "em-csv-config-pane",
    templateUrl: "./em-csv-config-pane.component.html",
    styleUrls: ["./em-csv-config-pane.component.scss"],
    imports: [MatFormField, MatLabel, MatInput, FormsModule, MatCheckbox, MatCard, MatCardContent, MatLine, MatList, MatListItem, MatDivider, MatError]
})
export class EmCSVConfigPaneComponent implements OnInit{
   @Input() csvConfigModel: CSVConfigModel = new CSVConfigModel();
   @Input() showTitle: boolean = false;
   @Input() showSelectedAssemblies: boolean = false;
   @Input() tableDataAssemblies: string[] = [];
   @Output() csvConfigChanged = new EventEmitter<any>();

   ngOnInit(): void {
   }

   addAssembly(newAssembly: string) {
      if(!this.csvConfigModel.selectedAssemblies) {
         this.csvConfigModel.selectedAssemblies = [];
      }

      if(newAssembly && !this.csvConfigModel.selectedAssemblies.includes(newAssembly)) {
         this.csvConfigModel.selectedAssemblies.push(newAssembly);
         this.fireConfigChange();
      }
   }

   removeAssembly(item: string): void {
      if(!this.csvConfigModel?.selectedAssemblies) {
         return;
      }

      let index = this.csvConfigModel.selectedAssemblies.indexOf(item);

      if(index >= 0) {
         this.csvConfigModel.selectedAssemblies.splice(index, 1);
         this.fireConfigChange();
      }
   }

   isSelectedAllTables(): boolean {
      return this.csvConfigModel?.selectedAssemblies == null;
   }

   selectAllChanged(value: boolean): void {
      this.csvConfigModel.selectedAssemblies = value ? null : [];
      this.fireConfigChange();
   }

   isSelectedTable(tableName: string): boolean {
      return this.csvConfigModel?.selectedAssemblies?.includes(tableName);
   }

   selectedItemChanged(tableName: string, value: boolean): void {
      if(value) {
         this.addAssembly(tableName);
      }
      else {
         this.removeAssembly(tableName);
      }
   }

   fireConfigChange(): void {
      this.csvConfigChanged.emit();
   }
}
