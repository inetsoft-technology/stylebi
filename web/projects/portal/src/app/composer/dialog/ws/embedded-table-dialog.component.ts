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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { FormValidators } from "../../../../../../shared/util/form-validators";
import { Tool } from "../../../../../../shared/util/tool";
import { ModelService } from "../../../widget/services/model.service";
import { EmbeddedTableDialogModel } from "../../data/ws/embedded-table-dialog-model";
import { Worksheet } from "../../data/ws/worksheet";

const EMBEDDED_TABLE_DIALOG_URI = "../api/composer/ws/dialog/embedded-table-dialog-model/";

@Component({
   selector: "embedded-table-dialog",
   templateUrl: "embedded-table-dialog.component.html"
})
export class EmbeddedTableDialog implements OnInit {
   @Input() worksheet: Worksheet;
   @Output() onCommit = new EventEmitter<EmbeddedTableDialogModel>();
   @Output() onCancel = new EventEmitter<string>();
   model: EmbeddedTableDialogModel;
   form: UntypedFormGroup;
   readonly formValid = () => this.form != null && this.form.valid;

   constructor(private modelService: ModelService) {
   }

   ngOnInit(): void {
      const URI = EMBEDDED_TABLE_DIALOG_URI + Tool.byteEncode(this.worksheet.runtimeId);

      this.modelService.getModel(URI)
         .subscribe((data) => {
            this.model = <EmbeddedTableDialogModel>data;
            this.initForm();
         });
   }

   initForm(): void {
      this.form = new UntypedFormGroup({
         name: new UntypedFormControl(this.model.name, [
            Validators.required,
            FormValidators.nameSpecialCharacters,
            FormValidators.exists(this.worksheet.assemblyNames(),
               {trimSurroundingWhitespace: true, ignoreCase: true})
         ]),
         rows: new UntypedFormControl(this.model.rows, [
            FormValidators.positiveNonZeroIntegerInRange,
            Validators.max(10000)
         ]),
         cols: new UntypedFormControl(this.model.cols, [
            FormValidators.positiveNonZeroIntegerInRange,
            Validators.max(1000)
         ])
      });
   }

   saveChanges(): void {
      this.model = this.form.value;
      this.onCommit.emit(this.model);
   }

   cancelChanges(): void {
      this.onCancel.emit();
   }
}
