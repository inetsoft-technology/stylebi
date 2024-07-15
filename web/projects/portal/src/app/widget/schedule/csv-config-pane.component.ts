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
import { Component, Input, OnDestroy, OnInit, ViewChild } from "@angular/core";
import { CSVConfigModel } from "../../../../../shared/schedule/model/csv-config-model";
import { UntypedFormControl, UntypedFormGroup, ValidationErrors, ValidatorFn } from "@angular/forms";
import { GenericSelectableList } from "../generic-selectable-list/generic-selectable-list.component";

@Component({
   selector: "csv-config-pane",
   templateUrl: "./csv-config-pane.component.html",
   styleUrls: ["./csv-config-pane.component.scss"]

})
export class CSVConfigPane implements OnInit, OnDestroy {
   @Input() parentForm: UntypedFormGroup;
   @Input() tableDataAssemblies: string[] = [];
   @Input() model: CSVConfigModel;
   @Input() selectAssemblyEnable: boolean = false;
   @Input() formId: string = "csvConfig";
   form: UntypedFormGroup = null;

   ngOnInit(): void {
      this.resetForm();
   }

   ngOnDestroy(): void {
      if(this.parentForm) {
         this.parentForm.removeControl(this.formId);
      }
   }

   private resetForm(): void {
      if(!this.formId) {
         this.formId = "csvConfig";
      }

      if(this.selectAssemblyEnable) {
         this.form = new UntypedFormGroup({
            tableDataAssemblies: new UntypedFormControl(this.model.selectedAssemblies, [
               this.selectedTableDataValidator(this.model.selectedAssemblies)
            ])
         });

         if(!!this.parentForm && !!this.form) {
            this.parentForm.removeControl(this.formId);
            this.parentForm.addControl(this.formId, this.form);
         }
      }
   }

   isExportAllTable(): boolean {
      return this.model?.selectedAssemblies == null;
   }

   isSelectedTable(tableName: string): boolean {
      return this.model?.selectedAssemblies?.includes(tableName);
   }

   tableItemChange(selected: boolean, tableName: string): void {
      if(selected) {
         this.addAssembly(tableName);
      }
      else {
         this.deleteAssembly(tableName);
      }
   }

   selectAllTableChange(value: boolean): void {
      this.model.selectedAssemblies = value ? null : [];
      this.resetForm();
   }

   public setTabDelimited(event): void {
      this.model.tabDelimited = event.currentTarget.checked;
   }

   public setKeepHeader(event): void {
      this.model.keepHeader = event.currentTarget.checked;
   }

   addAssembly(tableName: string): void {
      if(tableName && this.model) {
         if(!this.model.selectedAssemblies) {
            this.model.selectedAssemblies = [];
         }

         if(!this.model.selectedAssemblies.includes(tableName)) {
            this.model.selectedAssemblies.push(tableName);
         }

         this.resetForm();
      }
   }

   deleteAssembly(item: string): void {
      if(!!this.model?.selectedAssemblies) {
         let index = this.model.selectedAssemblies.indexOf(item);

         if(index >= 0 && index < this.model.selectedAssemblies.length) {
            this.model.selectedAssemblies.splice(index, 1);
         }
      }

      this.resetForm();
   }

   private selectedTableDataValidator(names: string[]): ValidatorFn {
      return (control: UntypedFormControl): ValidationErrors => {
         return !!names && names.length == 0 ? {emptyTable: true} : null;
      };
   }
}