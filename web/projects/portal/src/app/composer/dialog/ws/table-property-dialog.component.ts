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
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { Tool } from "../../../../../../shared/util/tool";
import { FormValidators } from "../../../../../../shared/util/form-validators";
import { AbstractTableAssembly } from "../../data/ws/abstract-table-assembly";
import { EmbeddedTableAssembly } from "../../data/ws/embedded-table-assembly";
import { SnapshotEmbeddedTableAssembly } from "../../data/ws/snapshot-embedded-table-assembly";
import { TablePropertyDialogModel } from "../../data/ws/table-property-dialog-model";
import { TabularTableAssembly } from "../../data/ws/tabular-table-assembly";
import { Worksheet } from "../../data/ws/worksheet";

@Component({
   selector: "table-property-dialog",
   templateUrl: "table-property-dialog.component.html"
})
export class TablePropertyDialog implements OnInit {
   @Input() worksheet: Worksheet;
   @Input() model: TablePropertyDialogModel;
   @Input() set table(table: AbstractTableAssembly) {
      this._table = table;

      if(this.form != null && !!table && table.primary) {
         this.form.get("visibleInViewsheet").disable();
      }
   }

   get table() {
      return this._table;
   }

   @Output() onCommit = new EventEmitter<any>();
   @Output() onCancel = new EventEmitter<string>();
   private readonly socketController: string = "/events/ws/dialog/table-property-dialog-model";
   private _table: AbstractTableAssembly;
   form: UntypedFormGroup;
   formValid = () => !this.okDisabled();
   sqlMergePossible: boolean;

   ngOnInit(): void {
      this.form = this.createForm(this.model);
      this.sqlMergePossible = !(this.table instanceof EmbeddedTableAssembly ||
         this.table instanceof TabularTableAssembly);
   }

   createForm(model: TablePropertyDialogModel): UntypedFormGroup {
      const form = new UntypedFormGroup({
         newName: new UntypedFormControl(model.oldName, [
            Validators.required,
            FormValidators.nameSpecialCharacters,
            FormValidators.exists(this.worksheet.assemblyNames(model.oldName),
               {
                  trimSurroundingWhitespace: true,
                  ignoreCase: true,
                  originalValue: model.oldName
               })
         ]),
         description: new UntypedFormControl(model.description),
         visibleInViewsheet: new UntypedFormControl(model.visibleInViewsheet),
         maxRows: new UntypedFormControl(model.maxRows,
            FormValidators.positiveIntegerInRange
         ),
         returnDistinctValues: new UntypedFormControl(model.returnDistinctValues),
         mergeSql: new UntypedFormControl(model.mergeSql),
      });

      if(!!this.table && this.table.primary) {
         form.get("visibleInViewsheet").disable();
      }

      if(model.rowCount !== undefined && !this.isSnapshot()) {
         form.addControl("rowCount", new UntypedFormControl(model.rowCount,
            FormValidators.positiveIntegerInRange
         ));
      }

      return form;
   }

   isSnapshot(): boolean {
      return this.table instanceof SnapshotEmbeddedTableAssembly;
   }

   okDisabled(): boolean {
      return !this.form || !this.form.valid;
   }

   ok(): void {
      const model = Object.assign({}, this.model, this.form.getRawValue());
      this.onCommit.emit({model: model, controller: this.socketController});
   }

   cancel(): void {
      this.onCancel.emit("cancel");
   }
}
