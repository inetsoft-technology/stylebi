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
   Component,
   EventEmitter,
   Input,
   OnDestroy,
   OnInit,
   Output
} from "@angular/core";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { Subscription } from "rxjs";
import { AppInfoService } from "../../../../../../../shared/util/app-info.service";
import { FormValidators } from "../../../../../../../shared/util/form-validators";
import { Tool } from "../../../../../../../shared/util/tool";
import { AuthenticationProviderModel } from "../security-provider-model/authentication-provider-model";
import { AuthorizationProviderModel } from "../security-provider-model/authorization-provider-model";
import { SecurityProviderType } from "../security-provider-model/security-provider-type.enum";

@Component({
   selector: "em-authorization-provider-detail-view",
   templateUrl: "./authorization-provider-detail-view.component.html",
   styleUrls: ["./authorization-provider-detail-view.component.scss"]
})
export class AuthorizationProviderDetailViewComponent implements OnInit, OnDestroy {
   @Output() onSubmit = new EventEmitter<UntypedFormGroup>();
   @Output() onChanged = new EventEmitter<boolean>();
   form: UntypedFormGroup;
   private subscription: Subscription = new Subscription();
   private _model: AuthorizationProviderModel;
   private _original: AuthenticationProviderModel;
   private _changed: boolean = false;
   isEnterprise: boolean = false;

   con
   constructor(private appInfoService: AppInfoService) {
      this.appInfoService.isEnterprise().subscribe(val => {
         this.isEnterprise = val;
      });
   }

   @Input()
   set model(model: AuthorizationProviderModel) {
      this._model = model;
      this._original = Tool.clone(model);

      if(model && this.form) {
         this.form.controls["providerName"].setValue(this.model.providerName);
         this.form.controls["providerType"].setValue(this.model.providerType);
      }
   }

   get model(): AuthorizationProviderModel {
      return this._model;
   }

   get changed(): boolean {
      return this._changed;
   }

   set changed(changed: boolean) {
      this.onChanged.emit(changed);
      this._changed = changed;
   }

   ngOnInit() {
      this.initForm();
   }

   ngOnDestroy() {
      this.subscription.unsubscribe();
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

   get isValid(): boolean {
      if(!!this.form.controls["providerName"].valid) {
         let current = Tool.clone(this.model);

         if(current) {
            current.providerName = this.form.value["providerName"];
            current.providerType = this.form.value["providerType"];
         }

         return this.form.value["providerType"] === SecurityProviderType.CUSTOM ?
            this.form.controls["customForm"].valid && this.changed :
            this.changed && (current == null ? true : !Tool.isEquals(this._original, current));
      }

      return false;
   }

   reset() {
      if(this.model) {
         this.form.controls["providerName"].setValue(this.model.providerName);
         this.form.controls["providerType"].setValue(this.model.providerType);

         if(this.model.customProviderModel) {
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
