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
import {Component, EventEmitter, Input, OnInit, Output} from "@angular/core";
import {UntypedFormBuilder, UntypedFormControl, UntypedFormGroup, ValidationErrors, Validators} from "@angular/forms";
import {Observable} from "rxjs";
import {map, startWith, take} from "rxjs/operators";
import {ConnectionStatus} from "../security-provider-model/connection-status";
import {DatabaseAuthenticationProviderModel} from "../security-provider-model/database-authentication-provider-model";
import {SecurityProviderService} from "../security-provider.service";
import {Tool} from "../../../../../../../shared/util/tool";

const HASH_ALGORITHMS: string[] = ["BCRYPT", "MD2", "MD4", "MD5",
   "GOST3411", "GOST3411-2012-256", "GOST3411-2012-512",
   "KECCAK-224", "KECCAK-288", "KECCAK-256", "KECCAK-384", "KECCAK-512",
   "RIPEMD128", "RIPEMD160", "RIPEMD256", "RIPEMD320",
   "SHA-224", "SHA-256", "SHA-384", "SHA-512", "SHA-512/224", "SHA-512/256",
   "SHA3-224", "SHA3-256", "SHA3-384", "SHA3-512",
   "Skein-256-128", "Skein-256-160", "Skein-256-224", "Skein-256-256", "Skein-512-128",
   "Skein-512-160", "Skein-512-224", "Skein-512-256", "Skein-512-384", "Skein-512-512",
   "Skein-1024-384", "Skein-1024-512", "Skein-1024-1024",
   "SHA-1", "SM3", "TIGER", "WHIRLPOOL",
   "BLAKE2B-512", "BLAKE2B-384", "BLAKE2B-256", "BLAKE2B-160",
   "BLAKE2S-256", "BLAKE2S-224", "BLAKE2S-160", "BLAKE2S-128",
   "DSTU7564-256", "DSTU7564-384", "DSTU7564-512", "None"];

@Component({
   selector: "em-database-provider-view",
   templateUrl: "./database-provider-view.component.html",
   styleUrls: ["./database-provider-view.component.scss"]
})
export class DatabaseProviderViewComponent implements OnInit {
   @Input() form: UntypedFormGroup;
   connectionStatus: string = "_#(js:em.security.testlogin.note4)";
   filteredAlgorithms: Observable<string[]>;
   @Output() changed = new EventEmitter<void>();
   private _model: DatabaseAuthenticationProviderModel;

   @Input()
   set model(model: DatabaseAuthenticationProviderModel) {
      this._model = model;

      if(model && this.form) {
         if(!this.dbForm) {
            this.initForm();
         }

         this.dbForm.patchValue(model);
         this.dbForm.controls["sysAdminRoles"].setValue(model.sysAdminRoles);
         this.dbForm.controls["orgAdminRoles"].setValue(model.orgAdminRoles);
         this.dbForm.valueChanges.subscribe((val) => {
            if(!Tool.isEquals(this.model, val)) {
               this.changed.emit();
            }
         });
      }
   }

   get model(): DatabaseAuthenticationProviderModel {
      return this._model;
   }

   @Input() isMultiTenant = false;
   @Input() isCloudSecrets = false;

   dbFormItems: any[] = [
      {
         label: "_#(js:User List Query)",
         formControlName: "userListQuery",
         hint: "_#(js:em.security.database.userListQueryDesc)",
         btnLabel: "_#(js:Show User List)",
         validators: [Validators.required],
         requiresMultiTenant: false,
         callback: () => this.securityProviderService.triggerUserListQuery(this.form, this.isMultiTenant)
      },
      {
         label: "_#(js:Group List Query)",
         formControlName: "groupListQuery",
         hint: "_#(js:em.security.database.groupListQueryDesc)",
         btnLabel: "_#(js:Show Group List)",
         validators: [Validators.required],
         requiresMultiTenant: false,
         callback: () => this.securityProviderService.triggerGroupListQuery(this.form, this.isMultiTenant)
      },
      {
         label: "_#(js:Role List Query)",
         formControlName: "roleListQuery",
         hint: "_#(js:em.security.database.roleListQueryDesc)",
         btnLabel: "_#(js:Show Role List)",
         validators: [Validators.required],
         requiresMultiTenant: false,
         callback: () => this.securityProviderService.triggerRoleListQuery(this.form, this.isMultiTenant)
      },
      {label: "_#(js:Organization List Query)",
         formControlName: "organizationListQuery",
         hint: "_#(js:em.security.database.organizationListQueryDesc)",
         btnLabel: "_#(js:Show Organization List)",
         validators: [],
         requiresMultiTenant: true,
         callback: () => this.securityProviderService.triggerOrganizationListQuery(this.form)
      },
      {
         label: "_#(js:Users Query)",
         formControlName: "userQuery",
         hint: "_#(js:em.security.database.userQueryDesc)",
         btnLabel: "_#(js:Show User)",
         validators: [Validators.required],
         requiresMultiTenant: false,
         callback: () => this.securityProviderService.triggerUsersQuery(this.form, this.isMultiTenant)
      },
      {
         label: "_#(js:User Roles Query)",
         formControlName: "userRolesQuery",
         hint: "_#(js:em.security.database.userRolesQueryDesc)",
         btnLabel: "_#(js:Show User Roles)",
         validators: [Validators.required],
         requiresMultiTenant: false,
         callback: () => this.securityProviderService.triggerUserRolesQuery(this.form, this.isMultiTenant)
      },
      {
         label: "_#(js:User Emails Query)",
         formControlName: "userEmailsQuery",
         hint: "_#(js:em.security.database.userEmailsQueryDesc)",
         btnLabel: "_#(js:Show User Emails)",
         validators: [],
         requiresMultiTenant: false,
         callback: () => this.securityProviderService.triggerUserEmailsQuery(this.form, this.isMultiTenant)
      },
      {
         label: "_#(js:Group Users Query)",
         formControlName: "groupUsersQuery",
         hint: "_#(js:em.security.database.groupUsersQueryDesc)",
         btnLabel: "_#(js:Show Group Users)",
         validators: [Validators.required],
         requiresMultiTenant: false,
         callback: () => this.securityProviderService.triggerGroupUsersQuery(this.form, this.isMultiTenant)
      },
      {
         label: "_#(js:Organization Members Query)",
         formControlName: "organizationMembersQuery",
         hint: "_#(js:em.security.database.organizationMembersQueryDesc)",
         btnLabel: "_#(js:Show Organization Members)",
         validators: [],
         requiresMultiTenant: true,
         callback: () => this.securityProviderService.triggerOrganizationMembersQuery(this.form)
      },
      {
         label: "_#(js:Organization Name Query)",
         formControlName: "organizationNameQuery",
         hint: "_#(js:em.security.database.organizationNamesQueryDesc)",
         btnLabel: "_#(js:Show Organization Name)",
         validators: [],
         requiresMultiTenant: true,
         callback: () => this.securityProviderService.triggerOrganizationNameQuery(this.form)
      }
   ];

   constructor(private securityProviderService: SecurityProviderService,
               private fb: UntypedFormBuilder)
   {
   }

   ngOnInit() {
      if(!this.dbForm) {
         this.initForm();
      }
   }

   private filterHashAlgorithms(input: string): string[] {
      const filterValue: string = input.toLowerCase();
      return HASH_ALGORITHMS.filter(algo => algo.toLowerCase().startsWith(filterValue));
   }

   get dbForm(): UntypedFormGroup {
      return <UntypedFormGroup>this.form.controls["dbForm"];
   }

   initForm() {
      this.form.addControl("dbForm", this.fb.group({
         driver: this.fb.control("", [Validators.required]),
         url: this.fb.control("", [Validators.required]),
         requiresLogin: this.fb.control(true),
         useCredential: this.fb.control(false),
         secretId: this.fb.control("", this.model?.useCredential ? [Validators.required] : []),
         user: this.fb.control("", !this.model?.useCredential ? [Validators.required] : []),
         password: this.fb.control("", !this.model?.useCredential ? [Validators.required] : []),
         hashAlgorithm: this.fb.control("", [Validators.required, this.validAlgorithm]),
         appendSalt: this.fb.control(""),
         sysAdminRoles: this.fb.control(""),
         orgAdminRoles: this.fb.control("")
      }));

      this.filteredAlgorithms = this.dbForm.controls["hashAlgorithm"].valueChanges
         .pipe(
            startWith(""),
            map((input: string) => input ? this.filterHashAlgorithms(input) : HASH_ALGORITHMS)
         );

      this.dbForm.controls["requiresLogin"].valueChanges.subscribe(val => {
         if(val) {
            if(this.model?.useCredential) {
               this.dbForm.controls["secretId"].setValidators([Validators.required]);
            }
            else {
               this.dbForm.controls["user"].setValidators([Validators.required]);
               this.dbForm.controls["password"].setValidators([Validators.required]);
            }
         }
         else {
            this.dbForm.controls["secretId"].clearValidators();
            this.dbForm.controls["user"].clearValidators();
            this.dbForm.controls["password"].clearValidators();
         }

         this.dbForm.controls["secretId"].updateValueAndValidity();
         this.dbForm.controls["user"].updateValueAndValidity();
         this.dbForm.controls["password"].updateValueAndValidity();
      });

      this.dbForm.controls["useCredential"].valueChanges.subscribe(val => {
         if(val) {
            this.dbForm.controls["secretId"].setValidators([Validators.required]);
            this.dbForm.controls["user"].clearValidators();
            this.dbForm.controls["password"].clearValidators();
         }
         else {
            this.dbForm.controls["user"].setValidators([Validators.required]);
            this.dbForm.controls["password"].setValidators([Validators.required]);
            this.dbForm.controls["secretId"].clearValidators();
         }

         this.dbForm.controls["secretId"].updateValueAndValidity();
         this.dbForm.controls["user"].updateValueAndValidity();
         this.dbForm.controls["password"].updateValueAndValidity();
      });
   }

   get canTestConnection(): boolean {
      return this.dbForm && this.dbForm.controls["driver"].valid &&
         this.dbForm.controls["url"].valid && this.dbForm.controls["user"].valid &&
         this.dbForm.controls["password"].valid && this.dbForm.controls["hashAlgorithm"].valid;
   }

   testConnection(): void {
      this.securityProviderService.testDatabaseConnection(this.form).subscribe(
         (connectionStatus: ConnectionStatus) => this.connectionStatus = connectionStatus.status);
   }

   editRoles(sysAdmin: boolean): void {
      if(sysAdmin) {
         const control = this.dbForm.get("sysAdminRoles");
         this.securityProviderService.getAdminRoles(control.value, this.form, true)
            .pipe(take(1))
            .subscribe((rolesString: string) => control.setValue(rolesString));
      }
      else {
         const control = this.dbForm.get("orgAdminRoles");
         this.securityProviderService.getAdminRoles(control.value, this.form, false)
            .pipe(take(1))
            .subscribe((rolesString: string) => control.setValue(rolesString));
      }
   }

   private validAlgorithm(control: UntypedFormControl): ValidationErrors {
      return HASH_ALGORITHMS.indexOf(control.value) === -1 ? {unsupported: true} : null;
   }
}
