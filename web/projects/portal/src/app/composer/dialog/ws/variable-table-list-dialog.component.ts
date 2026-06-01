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
import { UntypedFormGroup, Validators, UntypedFormControl, FormsModule, ReactiveFormsModule } from "@angular/forms";
import { VariableTableListDialogModel } from "../../data/ws/variable-table-list-dialog-model";
import { AbstractTableAssembly, getHeader } from "../../data/ws/abstract-table-assembly";
import { ColumnRef } from "../../../binding/data/column-ref";
import { CustomSelectOption, CustomSelectComponent } from "../../../widget/custom-select/custom-select.component";

import { EnterSubmitDirective } from "../../../widget/directive/enter-submit.directive";
import { ModalHeaderComponent } from "../../../widget/modal-header/modal-header.component";

@Component({
    selector: "variable-table-list-dialog",
    templateUrl: "variable-table-list-dialog.component.html",
    styleUrls: ["variable-table-list-dialog.component.scss"],
    imports: [ModalHeaderComponent, EnterSubmitDirective, FormsModule, ReactiveFormsModule, CustomSelectComponent]
})

export class VariableTableListDialog implements OnInit {
   @Input() model: VariableTableListDialogModel;
   @Input() tables: AbstractTableAssembly[];
   @Output() onCommit: EventEmitter<VariableTableListDialogModel> =
      new EventEmitter<VariableTableListDialogModel>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   form: UntypedFormGroup;
   currentTable: AbstractTableAssembly;
   formValid = () => this.form && this.form.valid;
   headers: string[] = [];

   get columnSelectOptions(): CustomSelectOption<string>[] {
      return (this.headers ?? []).map((column, index) => ({
         value: column,
         label: this.currentTable ? `${this.currentTable.name}.${column}` : column,
         title: this.getColumnTooltip(index)
      }));
   }

   ngOnInit() {
      if(!!this.model.tableName) {
         this.currentTable = this.tables.find((t) => this.model.tableName === t.name);
         this.headers = this.currentTable.colInfos.filter((col) => col.visible).map(getHeader);
      }

      this.form = new UntypedFormGroup({
         tableName: new UntypedFormControl(this.model.tableName, [
            Validators.required
         ]),
         label: new UntypedFormControl(this.model.label, [
            Validators.required
         ]),
         value: new UntypedFormControl(this.model.value, [
            Validators.required
         ])
      });
   }

   getColumnTooltip(index: number) {
      let columnRef: ColumnRef = this.currentTable.colInfos[index].ref;

      return ColumnRef.getTooltip(columnRef);
   }

   public get modelIndex(): number {
      return this.tables.findIndex((t) => t.name === this.form.get("tableName").value);
   }

   public updateTableName(index: number) {
      this.currentTable = this.tables[index];
      this.form.get("tableName").patchValue(this.currentTable.name);
      this.form.get("label").patchValue(this.tables[index].headers[0]);
      this.form.get("value").patchValue(this.tables[index].headers[0]);
      this.headers = this.currentTable.colInfos.filter((col) => col.visible).map(getHeader);
   }

   private prepareModel(): VariableTableListDialogModel {
      let model: VariableTableListDialogModel = this.form.getRawValue();
      return model;
   }

   ok(): void {
      this.onCommit.emit(this.prepareModel());
   }

   close(): void {
      this.onCancel.emit("cancel");
   }
}
