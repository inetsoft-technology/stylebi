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
   Component, EventEmitter, Input, OnDestroy, OnInit, Output, Renderer2
} from "@angular/core";
import {
   UntypedFormBuilder, UntypedFormControl,
   UntypedFormGroup, FormGroupDirective, NgForm,
   Validators
} from "@angular/forms";
import { ErrorStateMatcher } from "@angular/material/core";
import { FormValidators } from "../../../../shared/util/form-validators";

@Component({
   selector: "em-change-password-form",
   templateUrl: "./change-password-form.component.html",
   styleUrls: ["./change-password-form.component.scss"]
})
export class ChangePasswordFormComponent implements OnInit, OnDestroy {
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
   mouseupListener: () => void;

   constructor(formBuilder: UntypedFormBuilder,
               defaultErrorMatcher: ErrorStateMatcher,
               private renderer: Renderer2)
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

      this.mouseupListener = this.renderer.listen("document", "mouseup",
         (evt: MouseEvent) => {
            this.showPwd.fill(false);
         });
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

   triggerPassword(event: MouseEvent, index: number, show: boolean = true): void {
      event.preventDefault();
      event.stopPropagation();
      this.showPwd[index] = show;
   }

   ngOnDestroy(): void {
      if(this.mouseupListener) {
         this.mouseupListener();
         this.mouseupListener = null;
      }
   }

   get formDisabled(): boolean {
      return this.changePasswordForm.disabled;
   }
}
