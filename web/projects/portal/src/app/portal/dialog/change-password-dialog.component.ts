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
import { Component, EventEmitter, OnInit, Output } from "@angular/core";
import { ChangePasswordDialogModel } from "./change-password-dialog-model";
import { ModelService } from "../../widget/services/model.service";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { FormValidators } from "../../../../../shared/util/form-validators";

const CHANGE_PASSWORD_DIALOG_MODEL_URI: string = "../api/portal/change-password-dialog-model";

@Component({
   selector: "change-password-dialog",
   templateUrl: "change-password-dialog.component.html"
})
export class ChangePasswordDialog implements OnInit {
   @Output() onCommit: EventEmitter<string> = new EventEmitter<string>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   formGroup: UntypedFormGroup;
   model: ChangePasswordDialogModel;
   confirmNewPassword: string;

   constructor(private modelService: ModelService) {
   }

   ngOnInit(): void {
      this.modelService.getModel(CHANGE_PASSWORD_DIALOG_MODEL_URI).subscribe(
         (data) => {
            this.model = <ChangePasswordDialogModel> data;
         }
      );

      this.formGroup = new UntypedFormGroup({
         userName: new UntypedFormControl(null, Validators.required),
         oldPassword: new UntypedFormControl(null, Validators.required),
         newPassword: new UntypedFormControl(null, [
            Validators.required,
            Validators.minLength(8),
            FormValidators.containsNumberAndLetterOrNonAlphanumeric
         ]),
         confirmNewPassword: new UntypedFormControl(null, [
            Validators.required
         ])
      });
   }

   closeDialog(): void {
      this.onCancel.emit("cancel");
   }

   okClicked(): void {
      this.modelService.sendModel(CHANGE_PASSWORD_DIALOG_MODEL_URI, this.model).subscribe(
         (data) => {
            if(data) {
               this.onCommit.emit("ok");
            }
         },
         () => {
         }
      );
   }

   isValid(): boolean {
      return !!this.model && this.formGroup.valid &&
         this.model.newPassword === this.confirmNewPassword;
   }
}
