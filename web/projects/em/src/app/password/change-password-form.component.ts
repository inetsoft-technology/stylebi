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
import {
   Component, EventEmitter, Input, OnInit, Output
} from "@angular/core";
import { UntypedFormBuilder, UntypedFormControl, UntypedFormGroup, FormGroupDirective, NgForm, Validators, FormsModule, ReactiveFormsModule } from "@angular/forms";
import { ErrorStateMatcher } from "@angular/material/core";
import { FormValidators } from "../../../../shared/util/form-validators";
import { NgIf } from "@angular/common";
import { MatIcon } from "@angular/material/icon";
import { MatIconButton, MatButton } from "@angular/material/button";
import { MatInput } from "@angular/material/input";
import { MatFormField, MatSuffix, MatLabel, MatError } from "@angular/material/form-field";
import { MatCard, MatCardTitle, MatCardContent, MatCardActions } from "@angular/material/card";

@Component({
    selector: "em-change-password-form",
    templateUrl: "./change-password-form.component.html",
    styleUrls: ["./change-password-form.component.scss"],
    standalone: true,
    imports: [MatCard, MatCardTitle, MatCardContent, FormsModule, ReactiveFormsModule, MatFormField, MatInput, MatIconButton, MatSuffix, MatIcon, MatLabel, NgIf, MatError, MatCardActions, MatButton]
})
export class ChangePasswordFormComponent implements OnInit {
   @Input()
   set loading(value: boolean) {
      if(value) {
         this.changePasswordForm.disable();
      }
      else {
         this.changePasswordForm.enable();
      }
   }

   @Output() passwordChanged = new EventEmitter<{oldPwd: string; newPwd: string}>();
   changePasswordForm: UntypedFormGroup;
   errorStateMatcher: ErrorStateMatcher;
   showPwd: boolean[] = [];

   constructor(formBuilder: UntypedFormBuilder,
               defaultErrorMatcher: ErrorStateMatcher)
   {
      this.changePasswordForm = formBuilder.group(
         {
            oldPassword: ["", [Validators.required]],
            password: ["", [Validators.required, FormValidators.passwordComplexity]],
            verifyPassword: [""]
         },
         {
            validator: FormValidators.passwordsMatch("password", "verifyPassword")
         }
      );

      this.errorStateMatcher = {
         isErrorState: (control: UntypedFormControl | null, form: FormGroupDirective | NgForm | null) =>
            !!this.changePasswordForm.errors && !!this.changePasswordForm.errors.passwordsMatch ||
               defaultErrorMatcher.isErrorState(control, form)

      };

   }

   ngOnInit() {
   }

   changePassword(): void {
      if(this.changePasswordForm.valid) {
         this.passwordChanged.emit({
            oldPwd: this.changePasswordForm.controls.oldPassword.value,
            newPwd: this.changePasswordForm.controls.password.value
         });
      }
   }

   triggerPassword(index: number): void {
      this.showPwd[index] = !this.showPwd[index];
   }

   get formDisabled(): boolean {
      return this.changePasswordForm.disabled;
   }
}
