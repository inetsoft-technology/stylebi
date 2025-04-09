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
import { HttpClient } from "@angular/common/http";
import {
   Component,
   EventEmitter,
   forwardRef,
   Input,
   OnChanges,
   OnInit,
   Output,
   SimpleChanges
} from "@angular/core";
import {
   ControlValueAccessor,
   UntypedFormControl,
   NG_VALIDATORS,
   NG_VALUE_ACCESSOR,
   Validator,
   ValidatorFn,
   Validators
} from "@angular/forms";
import { MatDialog } from "@angular/material/dialog";
import { MatSnackBar } from "@angular/material/snack-bar";
import { ScheduleUsersService } from "../../../../../shared/schedule/schedule-users.service";
import { FormValidators } from "../../../../../shared/util/form-validators";
import { CheckMailInfo } from "../schedule/schedule-configuration-view/check-mail-info";
import { IdentityId } from "../security/users/identity-id";
import { EmailListDialogComponent } from "./email-list-dialog/email-list-dialog.component";
import { Tool } from "../../../../../shared/util/tool";
import { GuiTool } from "../../../../../portal/src/app/common/util/gui-tool";

const SCHEDULE_CHECK_MAIL_URL = "../api/em/settings/schedule/check-mail";

export const EMAIL_PICKER_VALUE_ACCESSOR: any = {
   provide: NG_VALUE_ACCESSOR,
   useExisting: forwardRef(() => EmailPickerComponent), // eslint-disable-line @typescript-eslint/no-use-before-define
   multi: true
};

export const EMAIL_PICKER_VALIDATOR: any = {
   provide: NG_VALIDATORS,
   useExisting: forwardRef(() => EmailPickerComponent), // eslint-disable-line @typescript-eslint/no-use-before-define
   multi: true
};

@Component({
   selector: "em-email-picker",
   templateUrl: "./email-picker.component.html",
   styleUrls: ["./email-picker.component.scss"],
   providers: [EMAIL_PICKER_VALUE_ACCESSOR, EMAIL_PICKER_VALIDATOR]
})
export class EmailPickerComponent implements ControlValueAccessor, Validator, OnInit, OnChanges {
   @Input() required = false;
   @Input() placeholder = "_#(js:Email Addresses)";
   @Input() editable = true;
   @Input() autocompleteEmails = true;
   @Input() isEmailBrowserEnabled = true;
   @Input() users: IdentityId[] = [];
   @Input() groups: IdentityId[] = [];
   @Input() splitByColon: boolean = false;
   @Input() ignoreWhitespace: boolean = true;
   @Input() allowVariable = false;
   @Output() onChangeEmails: EventEmitter<string> = new EventEmitter<string>();
   emailsControl: UntypedFormControl;
   userAliases: Map<IdentityId, string>;
   internalValidators: ValidatorFn[];
   mobile: boolean;

   get emails(): string {
      return this.emailsControl.value;
   }

   set emails(val: string) {
      this.emailsControl.setValue(val);
      this.onChangeEmails.emit(val);
   }

   get emailRequiredError() {
      return this.emailsControl?.errors?.required;
   }

   get emailInvalid() {
      return !this.emailRequiredError &&  this.emailsControl?.errors?.email;
   }

   get duplicateEmail() {
      return !this.emailInvalid && this.emailsControl?.errors?.duplicateTokens;
   }

   onChange: (value: any) => void = () => {
   };

   onTouched = () => {
   };

   writeValue(val: string): void {
     this.emailsControl.setValue(val, {emitEvent: false});
   }

   registerOnChange(fn: (value: any) => void): void {
      this.onChange = fn;
   }

   registerOnTouched(fn: () => {}): void {
      this.onTouched = fn;
   }

   setDisabledState(isDisabled: boolean): void {
   }

   validate() {
      return this.emailsControl.errors;
   }

   constructor(private userService: ScheduleUsersService,
               private dialog: MatDialog,
               private snackBar: MatSnackBar,
               private http: HttpClient)
   {
      userService.getEmailUserAliases().subscribe(aliasMap => this.userAliases = aliasMap);
   }

   ngOnInit() {
      this.initForm();
      this.mobile = GuiTool.isMobileDevice();
   }

   ngOnChanges(changes: SimpleChanges) {
      if((changes.users || changes.groups) && this.emailsControl) {
         this.changeInternalValidators();
         this.emailsControl.setValidators(this.internalValidators);
         this.emailsControl.updateValueAndValidity();
      }
   }

   initForm() {
      this.changeInternalValidators();
      this.emailsControl = new UntypedFormControl("", this.internalValidators);
      this.emailsControl.markAsTouched();
      this.emailsControl.valueChanges.subscribe((val) => this.onChange(val ? val.trim() : null));

      if(!this.editable) {
         this.emailsControl.disable();
      }
   }

   changeInternalValidators() {
      this.internalValidators
         = [FormValidators.emailList(",;", this.ignoreWhitespace, this.splitByColon, this.getEmailIdentities(), this.allowVariable),
         FormValidators.duplicateTokens()];

      if(this.required) {
         this.internalValidators.push(Validators.required);
      }
   }

   openEmailDialog(): void {
      this.onTouched();
      const ref = this.dialog.open(EmailListDialogComponent, {
         width: "70vw",
         height: "75vh",
         data: {
            emails: this.emails ? this.emails.split(",") : [],
            emailIdentities: this.getEmailIdentities(),
            autocompleteEmails: this.autocompleteEmails,
         }
      });
      ref.afterClosed().subscribe((result: string[]) => {
         if(result) {
            this.emails = result.join(",");
            this.onChange(this.emails);
         }
      });
   }

   clearEmails(): void {
      this.onTouched();
      this.emails = null;
      this.onChange(this.emails);
   }

   testMail() {
      this.onTouched();
      this.http.post(SCHEDULE_CHECK_MAIL_URL, new CheckMailInfo(this.emails)).subscribe(
         (result: CheckMailInfo) => this.snackBar.open(result.resultMessage, "", {duration: Tool.SNACKBAR_DURATION}));
   }

   isVariableEmail(): boolean {
      return this.allowVariable && FormValidators.matchesVariable(this.emailsControl.value);
   }

   private getEmailIdentities(): string[] {
      let identities: string[] = [];

      if(this.users) {
         identities = identities.concat(this.userService.populateEmailUserAliases(this.users, this.userAliases));
      }

      if(this.groups) {
         identities = identities.concat(this.groups.map(user => user.name + Tool.GROUP_SUFFIX));
      }

      return identities;
   }
}
