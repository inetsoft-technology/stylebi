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
import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from "@angular/core";
import { AbstractControl, UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { Subscription } from "rxjs";
import { mergeMap } from "rxjs/operators";
import { AppInfoService } from "../../../../../../../shared/util/app-info.service";
import { FormValidators } from "../../../../../../../shared/util/form-validators";
import { Tool } from "../../../../../../../shared/util/tool";
import { SecurityEnabledEvent } from "../../security-settings-page/security-enabled-event";
import { AuthenticationProviderModel } from "../security-provider-model/authentication-provider-model";
import { SecurityProviderType } from "../security-provider-model/security-provider-type.enum";

@Component({
   selector: "em-authentication-provider-detail-view",
   templateUrl: "./authentication-provider-detail-view.component.html",
   styleUrls: ["./authentication-provider-detail-view.component.scss"]
})
export class AuthenticationProviderDetailViewComponent implements OnInit, OnDestroy {
   SecurityProviderType = SecurityProviderType;
   form: UntypedFormGroup;
   @Output() onSubmit = new EventEmitter<UntypedFormGroup>();
   @Output() onChanged = new EventEmitter<boolean>();
   private _changed;
   private subscription: Subscription = new Subscription();
   private _model: AuthenticationProviderModel;
   private _original: AuthenticationProviderModel;
   @Input() isMultiTenant = false;

   @Input()
   set model(model: AuthenticationProviderModel) {
      this._model = model;
      this._original = Tool.clone(model);

      if(model && this.form) {
         this.providerName = this.model.providerName;
         this.providerType = this.model.providerType;
      }
   }

   get model(): AuthenticationProviderModel {
      return this._model;
   }

   set changed(changed: boolean) {
      this._changed = changed;
      this.onChanged.emit(changed);
   }

   get changed(): boolean {
      return this._changed;
   }

   constructor(private appInfoService: AppInfoService, private http: HttpClient) {
   }

   ngOnInit() {
      this.initNewProvider();
      this.initForm();
   }

   ngOnDestroy() {
      this.subscription.unsubscribe();
   }

   initNewProvider() {
      if(this.model == null) {
         this.appInfoService.isEnterprise().subscribe(isEnterprise => {
            if(this.model == null) {
               this.model = {
                  providerName: "", providerType: undefined,
                  dbProviderEnabled: isEnterprise,
                  customProviderEnabled: isEnterprise,
                  ldapProviderEnabled: !this.isMultiTenant
               }
            }
         });
      }
   }

   initForm() {
      this.form = new UntypedFormGroup({
         providerName: new UntypedFormControl("", [Validators.required,
            FormValidators.containsSpecialCharsForCommonName]),
         providerType: new UntypedFormControl(SecurityProviderType.FILE, [Validators.required])
      });

      this.subscription.add(
         this.form.controls["providerName"].valueChanges.subscribe((name) => {
            if(!this._original || name !== this._original.providerName) {
               this.changed = true;
            }
         }));

      this.subscription.add(
         this.form.controls["providerType"].valueChanges.subscribe((type) => {
            if(!this._original || type !== this._original.providerType) {
               this.changed = true;
            }
         }));
   }

   set providerName(name: string) {
      this.form.get("providerName").setValue(name);
   }

   get providerName(): string {
      return this.form.get("providerName").value;
   }

   set providerType(type: SecurityProviderType) {
      this.form.get("providerType").setValue(type);
   }

   get providerType(): SecurityProviderType {
      return this.form.get("providerType").value;
   }

   get isValid(): boolean {
      if(!!this.form.controls["providerName"].valid) {
         let control: AbstractControl;
         let current = Tool.clone(this.model);

         if(current) {
            current.providerName = this.providerName;
            current.providerType = this.providerType;
         }

         switch(this.providerType) {
         case SecurityProviderType.LDAP:
            control = this.form.get("ldapForm");
            break;
         case SecurityProviderType.DATABASE:
            control = this.form.get("dbForm");
            break;
         case SecurityProviderType.CUSTOM:
            control = this.form.get("customForm");
            break;
         default:
            return this.changed && (current == null || !Tool.isEquals(this._original, current));
         }

         return !!control && control.valid && this.changed;
      }

      return false;
   }

   reset() {
      if(this.model) {
         this.providerName = this.model.providerName;
         this.providerType = this.model.providerType;

         if(this.model.ldapProviderModel) {
            this.model.ldapProviderModel = Tool.clone(this.model.ldapProviderModel);
         }
         else if(this.model.dbProviderModel) {
            this.model.dbProviderModel = Tool.clone(this.model.dbProviderModel);
         }
         else if(this.model.customProviderModel) {
            this.model.customProviderModel = Tool.clone(this.model.customProviderModel);
         }
      }
      else {
         this.form.controls["providerName"].reset("");
         this.form.controls["providerType"].reset(SecurityProviderType.FILE);
      }

      this.changed = false;
   }
}
