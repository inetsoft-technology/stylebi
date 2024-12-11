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
import {Component, EventEmitter, Inject, Input, OnDestroy, OnInit, Output} from "@angular/core";
import {
   AbstractControl,
   FormGroupDirective,
   NgForm,
   UntypedFormControl,
   UntypedFormGroup,
   ValidatorFn,
   Validators
} from "@angular/forms";
import {MAT_BOTTOM_SHEET_DATA, MatBottomSheet, MatBottomSheetRef} from "@angular/material/bottom-sheet";
import {ErrorStateMatcher} from "@angular/material/core";
import {merge, Subscription} from "rxjs";
import { AppInfoService } from "../../../../../../../shared/util/app-info.service";
import {FormValidators} from "../../../../../../../shared/util/form-validators";
import {LdapAuthenticationProviderModel} from "../security-provider-model/ldap-authentication-provider-model";
import {SecurityProviderType} from "../security-provider-model/security-provider-type.enum";
import {SecurityProviderService} from "../security-provider.service";
import {ConnectionStatus} from "../security-provider-model/connection-status";
import {take} from "rxjs/operators";
import {SortTypes} from "../../../../../../../shared/util/sort/sort-types";
import {SortOptions} from "../../../../../../../shared/util/sort/sort-options";
import {Tool} from "../../../../../../../shared/util/tool";

@Component({
   selector: "em-ldap-provider-view",
   templateUrl: "./ldap-provider-view.component.html",
   styleUrls: ["./ldap-provider-view.component.scss"]
})
export class LdapProviderViewComponent implements OnInit, OnDestroy {
   @Input() form: UntypedFormGroup;
   @Input() userList: string[];
   @Input() groupList: string[];
   @Input() roleList: string[];
   @Input() isCloudSecrets: boolean;
   @Output() changed = new EventEmitter<void>();
   private _model: LdapAuthenticationProviderModel;
   private _status: string = "_#(js:em.security.testlogin.note4)";
   private subscriptions = new Subscription();
   private valueChangesSubscription = Subscription.EMPTY;

   isAD = true;
   statusReceived: boolean = true;
   errorStateMatcher: ErrorStateMatcher;
   enterprise: boolean;

   @Input()
   set model(model: LdapAuthenticationProviderModel) {
      this._model = model;

      if(model && this.form) {
         this.valueChangesSubscription.unsubscribe();
         this.subscriptions.remove(this.valueChangesSubscription);
         this.ldapForm.patchValue(model);
         this.ldapForm.controls["confirmPassword"].setValue(model.password);
         this.ldapForm.controls["sysAdminRoles"].setValue(
            this.securityProviderService.formatAdminRolesString(model.sysAdminRoles));
         this.userSearchForm.patchValue(this.model);
         this.groupSearchForm.patchValue(this.model);
         this.roleSearchForm.patchValue(this.model);

         this.valueChangesSubscription =
            merge(this.ldapForm.valueChanges, this.groupSearchForm.valueChanges,
               this.roleSearchForm.valueChanges, this.userSearchForm.valueChanges).pipe(take(1))
               .subscribe(() => this.changed.emit());
         this.subscriptions.add(this.valueChangesSubscription);
      }
   }

   get model(): LdapAuthenticationProviderModel {
      return this._model;
   }

   @Input()
   set connectionStatus(status: string) {
      if(status) {
         this._status = status;
         this.statusReceived = true;
      }
      else {
         this._status = "_#(js:em.security.testlogin.note4)";
      }
   }

   get connectionStatus(): string {
      return this._status;
   }

   get ldapForm(): UntypedFormGroup {
      return <UntypedFormGroup>this.form.controls["ldapForm"];
   }

   get userSearchForm(): UntypedFormGroup {
      return <UntypedFormGroup>this.form.controls["userSearch"];
   }

   get groupSearchForm(): UntypedFormGroup {
      return <UntypedFormGroup>this.form.controls["groupSearch"];
   }

   get roleSearchForm(): UntypedFormGroup {
      return <UntypedFormGroup>this.form.controls["roleSearch"];
   }

   constructor(private securityProviderService: SecurityProviderService,
               private bottomSheet: MatBottomSheet,
               private appInfoService: AppInfoService,
               defaultErrorMatcher: ErrorStateMatcher) {
      this.errorStateMatcher = {
         isErrorState: (control: UntypedFormControl | null, form: FormGroupDirective | NgForm | null) =>
            !!this.ldapForm && !!this.ldapForm.errors && !!this.ldapForm.errors.passwordsMatch ||
            defaultErrorMatcher.isErrorState(control, form)
      };

      this.appInfoService.isEnterprise().subscribe(value => this.enterprise = value);
   }

   ngOnInit() {
      this.initForm();
   }

   ngOnDestroy() {
      this.subscriptions.unsubscribe();
   }

   openLDAPQueryResults(itemList: string[]): void {
      this.bottomSheet.open(LDAPQueryResult, {data: {queryResult: itemList}});
   }

   initForm() {
      const type = new UntypedFormControl(SecurityProviderType.ACTIVE_DIRECTORY, [Validators.required]);
      this.form.addControl("ldapForm", new UntypedFormGroup({
         ldapServer: type,
         protocol: new UntypedFormControl("", [Validators.required]),
         startTls: new UntypedFormControl(),
         hostName: new UntypedFormControl("", [Validators.required]),
         hostPort: new UntypedFormControl("", [Validators.required]),
         rootDN: new UntypedFormControl("", [Validators.required]),
         useCredential: new UntypedFormControl(false),
         secretId: new UntypedFormControl("", this.model?.useCredential ? [Validators.required] : []),
         adminID: new UntypedFormControl("", !this.model?.useCredential ? [Validators.required] : []),
         password: new UntypedFormControl("", !this.model?.useCredential ? [Validators.required] : []),
         confirmPassword: new UntypedFormControl("", !this.model?.useCredential ? [Validators.required] : []),
         userRoleFilter: new UntypedFormControl(),
         roleRoleFilter: new UntypedFormControl(),
         groupRoleFilter: new UntypedFormControl(),
         searchTree: new UntypedFormControl(),
         sysAdminRoles: new UntypedFormControl(),
      }, FormValidators.passwordsMatch("password", "confirmPassword")));

      this.ldapForm.controls["useCredential"].valueChanges.subscribe(val => {
         if(val) {
            this.ldapForm.controls["secretId"].setValidators([Validators.required]);
            this.ldapForm.controls["adminID"].clearValidators();
            this.ldapForm.controls["password"].clearValidators();
            this.ldapForm.controls["confirmPassword"].clearValidators();
         }
         else {
            this.ldapForm.controls["adminID"].setValidators([Validators.required]);
            this.ldapForm.controls["password"].setValidators([Validators.required]);
            this.ldapForm.controls["confirmPassword"].setValidators([Validators.required]);
            this.ldapForm.controls["secretId"].clearValidators();
         }

         this.ldapForm.controls["secretId"].updateValueAndValidity();
         this.ldapForm.controls["adminID"].updateValueAndValidity();
         this.ldapForm.controls["password"].updateValueAndValidity();
         this.ldapForm.controls["confirmPassword"].updateValueAndValidity();
      });

      const userSearchForm = new UntypedFormGroup({
         userFilter: new UntypedFormControl("", [Validators.required]),
         userBase: new UntypedFormControl(""),
         userAttr: new UntypedFormControl({
            value: "",
            disabled: this.isAD
         }, this.requiredForGeneric),
         mailAttr: new UntypedFormControl(""),
      });
      this.form.addControl("userSearch", userSearchForm);

      const groupSearchForm = new UntypedFormGroup({
         groupFilter: new UntypedFormControl({
            value: "",
            disabled: this.isAD
         }, this.requiredForGeneric),
         groupBase: new UntypedFormControl(""),
         groupAttr: new UntypedFormControl({
            value: "",
            disabled: this.isAD
         }, this.requiredForGeneric),
      });
      this.form.addControl("groupSearch", groupSearchForm);

      const roleSearchForm = new UntypedFormGroup({
         roleFilter: new UntypedFormControl({
            value: "",
            disabled: this.isAD
         }, this.requiredForGeneric),
         roleBase: new UntypedFormControl(""),
         roleAttr: new UntypedFormControl({
            value: "",
            disabled: this.isAD
         }, this.requiredForGeneric),
      });
      this.form.addControl("roleSearch", roleSearchForm);

      this.subscriptions.add(type.valueChanges.subscribe((value) => {
         this.isAD = value === SecurityProviderType.ACTIVE_DIRECTORY;
         const controls = [
            userSearchForm.controls.userAttr,
            groupSearchForm.controls.groupFilter,
            groupSearchForm.controls.groupAttr,
            roleSearchForm.controls.roleFilter,
            roleSearchForm.controls.roleAttr
         ];

         controls.forEach(control => this.isAD ? control.disable() : control.enable());
      }));
   }

   testConnection() {
      this.statusReceived = false;
      this.securityProviderService.testConnection(this.form).subscribe(
         (connectionStatus: ConnectionStatus) => this.connectionStatus = connectionStatus.status);
   }

   getUsers() {
      this.securityProviderService.getUsers(this.form)
         .subscribe(users => {
            this.userList = users.ids.map(id => id.name);
            this.openLDAPQueryResults(this.userList);
         });
   }

   getGroups() {
      this.securityProviderService.getGroups(this.form)
         .subscribe(groups => {
            this.groupList = groups.ids.map(id => id.name);
            this.openLDAPQueryResults(this.groupList);
         });
   }

   getRoles() {
      this.securityProviderService.getRoles(this.form)
         .subscribe(roles => {
            this.roleList = roles.ids.map(id => id.name);
            this.openLDAPQueryResults(this.roleList);
         });
   }

   editRoles(sysAdmin: boolean): void {
      if(sysAdmin) {
         const control = this.ldapForm.get("sysAdminRoles");
         this.subscriptions.add(this.securityProviderService.getAdminRoles(control.value, this.form, true)
            .pipe(take(1))
            .subscribe((rolesString: string) => {
               control.setValue(rolesString);
               this.changed.emit();
            }));
      }
   }

   private readonly requiredForGeneric = (): ValidatorFn => {
      return (control: AbstractControl) => {
         if(this.isAD) {
            return null;
         }

         return Validators.required(control);
      };
   };
}

@Component({
  selector: "em-ldap-query-result",
  templateUrl: "ldap-query-result.html",
})
export class LDAPQueryResult {
   queryResult: string[];

   constructor(private bottomSheetRef: MatBottomSheetRef<LDAPQueryResult>,
              @Inject(MAT_BOTTOM_SHEET_DATA) public data: any)
   {
      this.queryResult = Tool.sortObjects(data.queryResult, new SortOptions([], SortTypes.ASCENDING));
   }
}
