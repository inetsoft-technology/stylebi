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
import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from "@angular/core";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { FormValidators } from "../../../../../../shared/util/form-validators";
import { TableUnpivotDialogModel } from "../../data/ws/table-unpivot-dialog-model";

let LEVEL: number = 1;
const UNPIVOT_SOCKET_URI = "/events/ws/dialog/table-unpivot-dialog";
const UNPIVOT_LEVEL_SOCKET_URI = "/events/ws/dialog/table-unpivot-rowHeaders";

@Component({
   selector: "table-unpivot-dialog",
   templateUrl: "table-unpivot-dialog.component.html"
})
export class TableUnpivotDialog implements OnInit, OnDestroy {
   @Input() set level(data: number) {
      if(data >= 0) {
         this._level = data;
      }

      if(this.form && this.edit) {
         this.form.get("level")?.setValue(this._level);
      }
   }

   @Input() edit: boolean = false;
   @Output() onCommit: EventEmitter<any> = new EventEmitter<any>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   private _level: number = -1;
   form: UntypedFormGroup;
   formValid = () => this.form && this.form.valid;

   initForm(): void {
      this.form = new UntypedFormGroup({
         level: new UntypedFormControl(LEVEL, [
            FormValidators.positiveIntegerInRange,
            Validators.required,
         ])
      });

      if(this.edit) {
         this.form.get("level")?.setValue(this._level);
      }
   }

   ngOnInit(): void {
      this.initForm();
   }

   ngOnDestroy(): void {
      LEVEL = this.form.get("level").value || 1;
   }

   close(): void {
      this.onCancel.emit("cancel");
   }

   ok(): void {
      let model: TableUnpivotDialogModel = this.form.getRawValue();
      this.onCommit.emit({model: model,
         controller: this.edit ? UNPIVOT_LEVEL_SOCKET_URI : UNPIVOT_SOCKET_URI});
   }
}
