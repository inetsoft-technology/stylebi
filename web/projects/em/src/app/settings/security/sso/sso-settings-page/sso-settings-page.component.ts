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
import { HttpClient } from "@angular/common/http";
import { Component, OnDestroy, ViewChild } from "@angular/core";
import {
   UntypedFormBuilder,
   UntypedFormControl,
   UntypedFormGroup,
   Validators
} from "@angular/forms";
import { MatSelect } from "@angular/material/select";
import { ActivatedRoute } from "@angular/router";
import { Subscription } from "rxjs";
import { map } from "rxjs/operators";
import { NameLabelTuple } from "../../../../../../../shared/util/name-label-tuple";
import { ContextHelp } from "../../../../context-help";
import { PageHeaderService } from "../../../../page-header/page-header.service";
import { Searchable } from "../../../../searchable";
import { Secured } from "../../../../secured";
import { TopScrollService } from "../../../../top-scroll/top-scroll.service";
import {
   CustomSSOAttributesModel,
   OpenIdAttributesModel,
   SSOFormModel,
   SSOSettingsModel
} from "../sso-settings-model";

/**
 * Matches enum from backend
 */
export enum SSOType {
   NONE = "NONE",
   SAML = "SAML",
   OPENID = "OPENID",
   CUSTOM = "CUSTOM"
}

@Secured({
   route: "/settings/security/sso",
   label: "SSO",
   hiddenForMultiTenancy: true
})
@Searchable({
   route: "/settings/security/sso",
   title: "Security Settings SSO",
   keywords: ["em.security.sso"]
})
@ContextHelp({
   route: "/settings/security/sso",
   link: "EMSettingsSecuritySSO"
})
@Component({
   selector: "em-sso-settings-page",
   templateUrl: "./sso-settings-page.component.html",
   styleUrls: ["./sso-settings-page.component.scss"]
})
export class SsoSettingsPageComponent implements OnDestroy {
   @ViewChild("roleSelection") roleSelectionRef: MatSelect;
   public samlForm: UntypedFormGroup;
   public customModel: CustomSSOAttributesModel;
   public roles: NameLabelTuple[];
   public selectedRoles: string[];
   public logoutUrl: string;
   public logoutPath: string;
   public fallbackLogin = false;
   public readonly ssoTypes = SSOType;
   public selection = SSOType.NONE;
   public isMultiTenant: boolean = false;
   private readonly subscription: Subscription;
   private _openIdModel: OpenIdAttributesModel;

   get openIdModel(): OpenIdAttributesModel {
      return this._openIdModel;
   }

   set openIdModel(model: OpenIdAttributesModel) {
      this._openIdModel = model;
   }

   readonly editorStyle = {
      "display": "flex",
      "flex-direction": "row",
      "align-items": "stretch",
      "overflow": "hidden",
   };

   readonly requiredSAMLFields = [
      "spEntityId",
      "assertionUrl",
      "idpEntityId",
      "idpSignOnUrl",
      "idpLogoutUrl",
      "idpPublicKey"
   ];

   constructor(private httpClient: HttpClient, activatedRoute: ActivatedRoute,
               private scrollService: TopScrollService, private formBuilder: UntypedFormBuilder,
               pageHeader: PageHeaderService)
   {
      pageHeader.title = "_#(js:Security Settings: SSO)";
      this.subscription = activatedRoute.data.pipe(
         map((data: Record<"model", SSOSettingsModel>) => data.model),
      ).subscribe(model => this.applySSOSettings(model));

      this.httpClient.get<boolean>("../api/em/navbar/isMultiTenant")
         .subscribe(multiTenant => this.isMultiTenant = multiTenant);
   }

   ngOnDestroy(): void {
      this.subscription.unsubscribe();
   }

   public changeSelection(): void {
      this.scrollService.scroll("up");
   }

   public clearRoles(): void {
      this.roleSelectionRef.options.forEach(option => option.deselect());
   }

   public submit(): void {
      let ssoFormModel: SSOFormModel;

      switch(this.selection) {
      case SSOType.NONE:
         ssoFormModel = null;
         break;
      case SSOType.SAML:
         ssoFormModel = {
            samlAttributesModel: this.samlForm.value
         };
         break;
      case SSOType.OPENID:
         ssoFormModel = {
            openIdAttributesModel: this.openIdModel
         };
         break;
      case SSOType.CUSTOM:
         ssoFormModel = {
            customAttributesModel: this.customModel
         };
         break;
      default:
         ssoFormModel = null;
         break;
      }

      const model = Object.assign({}, ssoFormModel);
      model.activeFilterType = this.selection;
      model.selectedRoles = this.selectedRoles;
      model.logoutUrl = this.logoutUrl;
      model.logoutPath = this.logoutPath;
      model.fallbackLogin = this.fallbackLogin;
      this.httpClient.post("../api/sso/settings", model).subscribe();
   }

   reset(): void {
      this.httpClient.get<SSOSettingsModel>("../api/sso/settings")
         .subscribe(model => this.applySSOSettings(model));
   }

   private applySSOSettings(model: SSOSettingsModel) {
      this.selection = model.activeFilterType;
      this.roles = model.roles.sort();
      this.selectedRoles = model.selectedRoles;
      this.logoutUrl = model.logoutUrl;
      this.logoutPath = model.logoutPath;
      this.fallbackLogin = model.fallbackLogin;
      this.samlForm = this.ssoModelToForm(model.samlAttributesModel);
      this.openIdModel = model.openIdAttributesModel;
      this.customModel = Object.assign(
         {useJavaClass: true, useInlineGroovy: false}, model.customAttributesModel);
      this.customModel.useJavaClass = !this.customModel.useInlineGroovy;
   }

   private ssoModelToForm<T>(model: T): UntypedFormGroup {
      const keys = Object.keys(model);
      const formControls = keys.reduce((newObj, key) => {
         newObj[key] = new UntypedFormControl(model[key],
            this.requiredSAMLFields.includes(key) ? [Validators.required] : null);
         return newObj;
      }, {});

      return this.formBuilder.group(formControls);
   }
}
