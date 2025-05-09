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
import { Component, EventEmitter, Input, OnDestroy, Output } from "@angular/core";
import {
   UntypedFormBuilder,
   UntypedFormControl,
   UntypedFormGroup,
   FormGroupDirective,
   NgForm,
   Validators
} from "@angular/forms";
import { ErrorStateMatcher } from "@angular/material/core";
import { Subscription } from "rxjs";
import {
   OAuthAuthorizationService,
   OAuthParameters
} from "../../../../../../portal/src/app/common/services/oauth-authorization.service";
import { ScheduleUsersService } from "../../../../../../shared/schedule/schedule-users.service";
import { FormValidators } from "../../../../../../shared/util/form-validators";
import { Searchable } from "../../../searchable";
import { IdentityId } from "../../security/users/identity-id";
import { GeneralSettingsChanges } from "../general-settings-page/general-settings-page.component";
import { GeneralSettingsType } from "../general-settings-page/general-settings-type.enum";
import {
   EmailSettingsModel,
   SMTPAuthType,
} from "./email-settings-model";
import { ContextHelp } from "../../../context-help";

@Searchable({
   route: "/settings/general#email",
   title: "Email",
   keywords: ["em.settings", "em.settings.general", "em.settings.email"]
})
@ContextHelp({
   route: "/settings/general#email",
   link: "EMGeneralEmail"
})
@Component({
   selector: "em-email-settings-view",
   templateUrl: "./email-settings-view.component.html",
   styleUrls: ["./email-settings-view.component.scss"]
})
export class EmailSettingsViewComponent implements OnDestroy {
   @Output() modelChanged = new EventEmitter<GeneralSettingsChanges>();

   private _model: EmailSettingsModel;
   form: UntypedFormGroup;
   errorStateMatcher: ErrorStateMatcher;
   hidePassword: boolean = true;
   hideSecret: boolean = true;
   hideAccessToken: boolean = true;
   hideRefreshToken: boolean = true;
   emailUsers: IdentityId[] = [];
   groups: IdentityId[] = [];
   private subscriptions = new Subscription();

   @Input() set model(model: EmailSettingsModel) {
      this._model = model;

      if(this.model) {
         this.initForm();
         this.updateForm();
      }
   }

   get model(): EmailSettingsModel {
      return this._model;
   }

   constructor(private formBuilder: UntypedFormBuilder,
               private usersService: ScheduleUsersService,
               defaultErrorMatcher: ErrorStateMatcher,
               private httpClient: HttpClient,
               private oauthService: OAuthAuthorizationService,)
   {
      this.errorStateMatcher = {
         isErrorState: (control: UntypedFormControl | null, form: FormGroupDirective | NgForm | null) =>
            !!this.form.errors && !!this.form.errors.passwordsMatch ||
            defaultErrorMatcher.isErrorState(control, form)
      };

      this.subscriptions.add(usersService.getEmailUsers().subscribe(value => this.emailUsers = value));
      this.subscriptions.add(usersService.getEmailGroups().subscribe(value => this.groups = value));
   }

   ngOnDestroy() {
      this.subscriptions.unsubscribe();
   }

   /**
    * for ie, empty value will cause value change when init form.
    * @param formVal
    */
   private valueChanged(formVal: any): boolean {
      return this.stringValueChanged(this.model.smtpHost, formVal.smtpHost.trim()) ||
         this.model.ssl !=  formVal.ssl || this.model.tls != formVal.tls ||
         this.stringValueChanged(this.model.jndiUrl, formVal.jndiUrl) ||
         this.model.smtpAuthentication != formVal.smtpAuthentication ||
         this.stringValueChanged(this.model.smtpUser, formVal.smtpUser) ||
         this.stringValueChanged(this.model.smtpPassword, formVal.smtpPassword) ||
         this.stringValueChanged(this.model.smtpSecretId, formVal.smtpSecretId) ||
         this.stringValueChanged(this.model.confirmSmtpPassword, formVal.confirmSmtpPassword) ||
         this.stringValueChanged(this.model.fromAddress, formVal.fromAddress) ||
         this.booleanValueChanged(this.model.fromAddressEnabled, formVal.fromAddressEnabled) ||
         this.stringValueChanged(this.model.deliveryMailSubjectFormat, formVal.deliveryMailSubjectFormat) ||
         this.stringValueChanged(this.model.notificationMailSubjectFormat, formVal.notificationMailSubjectFormat) ||
         this.model.historyEnabled != formVal.historyEnabled ||
         this.stringValueChanged(this.model.smtpClientId, formVal.smtpClientId) ||
         this.stringValueChanged(this.model.smtpClientSecret, formVal.smtpClientSecret) ||
         this.stringValueChanged(this.model.smtpAuthUri, formVal.smtpAuthUri) ||
         this.stringValueChanged(this.model.smtpTokenUri, formVal.smtpTokenUri) ||
         this.stringValueChanged(this.model.smtpOAuthScopes, formVal.smtpOAuthScopes) ||
         this.stringValueChanged(this.model.smtpOAuthFlags, formVal.smtpOAuthFlags) ||
         this.stringValueChanged(this.model.smtpAccessToken, formVal.smtpAccessToken) ||
         this.stringValueChanged(this.model.smtpRefreshToken, formVal.smtpRefreshToken) ||
         this.stringValueChanged(this.model.tokenExpiration, formVal.tokenExpiration);
   }

   initForm() {
      this.form = this.formBuilder.group({
            smtpHost: [
               "", [
                  Validators.required,
                  Validators.pattern("^(([\\w\\d\\-\\._]+)*[\\w\\d\\-\\_]+)(,([\\w\\d\\-\\._]+)*[\\w\\d\\-\\_]+)*$")
               ]
            ],
            ssl: [""],
            tls: [""],
            jndiUrl: [""],
            smtpAuthentication: [""],
            smtpUser: ["", Validators.required],
            historyEnabled: [""],
            fromAddress: ["", [Validators.required, FormValidators.emailSpecialCharacters]],
            deliveryMailSubjectFormat: [""],
            notificationMailSubjectFormat: [""],
            smtpClientId: ["", Validators.required],
            smtpClientSecret: ["", Validators.required],
            smtpAuthUri: ["", Validators.required],
            smtpTokenUri: ["", Validators.required],
            smtpOAuthScopes: ["", Validators.required],
            smtpOAuthFlags: [""],
            smtpAccessToken: ["", Validators.required],
            smtpRefreshToken: ["", Validators.required],
            tokenExpiration: ["", Validators.required]
         },
         {
            validator: FormValidators.passwordsMatch("smtpPassword", "confirmSmtpPassword")
         });

      if(this.model.secretIdVisible) {
         this.form.addControl("smtpSecretId", new UntypedFormControl("", Validators.required));
      }
      else {
         this.form.addControl("smtpPassword", new UntypedFormControl("", Validators.required));
         this.form.addControl("confirmSmtpPassword", new UntypedFormControl("", Validators.required));
      }
   }

   updateForm() {
      this.model.confirmSmtpPassword = this.model.smtpPassword;
      this.form.patchValue(this.model, {emitEvent: false});
      this.smtpAuthenticationChanged(this.model.smtpAuthentication);

      this.subscriptions.add(this.form.valueChanges.subscribe(formVal => {
         if(!this.valueChanged(formVal)) {
            return;
         }

         if(formVal.smtpAuthentication == SMTPAuthType.GOOGLE_AUTH) {
            if(formVal.smtpUser != this.model.smtpUser) {
               formVal.fromAddress = formVal.smtpUser;
               this.form.get("fromAddress").setValue(formVal.smtpUser, {emitEvent: false});
            }
            else {
               formVal.smtpUser = formVal.fromAddress;
               this.form.get("smtpUser").setValue(formVal.fromAddress, {emitEvent: false});
            }
         }

         this.model.smtpHost = formVal.smtpHost.trim();
         this.model.ssl = formVal.ssl;
         this.model.tls = formVal.tls;
         this.model.jndiUrl = formVal.jndiUrl.trim();
         this.model.smtpAuthentication = formVal.smtpAuthentication;
         this.model.smtpUser = formVal.smtpUser;
         this.model.smtpSecretId = formVal.smtpSecretId;
         this.model.smtpPassword = formVal.smtpPassword;
         this.model.confirmSmtpPassword = formVal.confirmSmtpPassword;
         this.model.historyEnabled = formVal.historyEnabled;
         this.model.fromAddress = formVal.fromAddress;
         this.model.deliveryMailSubjectFormat = formVal.deliveryMailSubjectFormat;
         this.model.notificationMailSubjectFormat = formVal.notificationMailSubjectFormat;
         this.model.smtpClientId = formVal.smtpClientId;
         this.model.smtpClientSecret = formVal.smtpClientSecret;
         this.model.smtpAuthUri = formVal.smtpAuthUri;
         this.model.smtpTokenUri = formVal.smtpTokenUri;
         this.model.smtpOAuthScopes = formVal.smtpOAuthScopes;
         this.model.smtpOAuthFlags = formVal.smtpOAuthFlags;
         this.model.smtpAccessToken = formVal.smtpAccessToken;
         this.model.smtpRefreshToken = formVal.smtpRefreshToken;
         this.model.tokenExpiration = formVal.tokenExpiration;
         this.onModelChanged();
      }));

      this.subscriptions.add(
         this.form.controls["smtpAuthentication"].valueChanges.subscribe((change) => {
            this.smtpAuthenticationChanged(change);
         }));
   }

   smtpAuthenticationChanged(authType: SMTPAuthType) {
      if(authType == SMTPAuthType.SMTP_AUTH) {
         this.form.get("smtpUser").enable({emitEvent: false});
         this.toggleSecretId(true);

         this.form.get("smtpClientId").disable({emitEvent: false});
         this.form.get("smtpClientSecret").disable({emitEvent: false});
         this.form.get("smtpAccessToken").disable({emitEvent: false});
         this.form.get("smtpRefreshToken").disable({emitEvent: false});
         this.form.get("tokenExpiration").disable({emitEvent: false});
         this.form.get("smtpAuthUri").disable({emitEvent: false});
         this.form.get("smtpTokenUri").disable({emitEvent: false});
         this.form.get("smtpOAuthScopes").disable({emitEvent: false});
         this.form.get("smtpOAuthFlags").disable({emitEvent: false});
      }
      else if(authType == SMTPAuthType.SASL_XOAUTH2 || authType == SMTPAuthType.GOOGLE_AUTH) {
         this.form.get("smtpUser").enable({emitEvent: false});
         this.form.get("smtpClientId").enable({emitEvent: false});
         this.form.get("smtpClientSecret").enable({emitEvent: false});
         this.form.get("smtpAccessToken").enable({emitEvent: false});
         this.form.get("smtpRefreshToken").enable({emitEvent: false});
         this.form.get("tokenExpiration").enable({emitEvent: false});

         this.toggleSecretId(false);

         if(authType == SMTPAuthType.SASL_XOAUTH2) {
            this.form.get("smtpAuthUri").enable({emitEvent: false});
            this.form.get("smtpTokenUri").enable({emitEvent: false});
            this.form.get("smtpOAuthScopes").enable({emitEvent: false});
            this.form.get("smtpOAuthFlags").enable({emitEvent: false});
         }
         else {
            this.form.get("smtpAuthUri").disable({emitEvent: false});
            this.form.get("smtpTokenUri").disable({emitEvent: false});
            this.form.get("smtpOAuthScopes").disable({emitEvent: false});
            this.form.get("smtpOAuthFlags").disable({emitEvent: false});
         }
      }
      else {
         this.form.get("smtpUser").disable({emitEvent: false});
         this.toggleSecretId(false);
         this.form.get("smtpClientId").disable({emitEvent: false});
         this.form.get("smtpClientSecret").disable({emitEvent: false});
         this.form.get("smtpAccessToken").disable({emitEvent: false});
         this.form.get("smtpRefreshToken").disable({emitEvent: false});
         this.form.get("tokenExpiration").disable({emitEvent: false});
         this.form.get("smtpAuthUri").disable({emitEvent: false});
         this.form.get("smtpTokenUri").disable({emitEvent: false});
         this.form.get("smtpOAuthScopes").disable({emitEvent: false});
         this.form.get("smtpOAuthFlags").disable({emitEvent: false});
      }
   }

   toggleSecretId(checked: boolean) {
      if(checked) {
         if(this.model.secretIdVisible) {
            this.form.get("smtpSecretId").enable({emitEvent: false});
         }
         else {
            this.form.get("smtpPassword").enable({emitEvent: false});
            this.form.get("confirmSmtpPassword").enable({emitEvent: false});
         }
      }
      else {
         if(this.model.secretIdVisible) {
            this.form.get("smtpSecretId").disable({emitEvent: false});
         }
         else {
            this.form.get("smtpPassword").disable({emitEvent: false});
            this.form.get("confirmSmtpPassword").disable({emitEvent: false});
         }
      }
   }

   onModelChanged() {
      this.modelChanged.emit({
         model: this.model,
         modelType: GeneralSettingsType.EMAIL_SETTINGS_MODEL,
         valid: this.form.valid
      });
   }

   private booleanValueChanged(value1: boolean, value2: boolean): boolean {
      if((value1 == false || value1 == null || value1 == undefined) &&
         (value1 == false || value1 == null || value1 == undefined))
      {
         return false;
      }

      return value1 != value2;
   }

   private stringValueChanged(str1: string, str2: string): boolean {
      if((str1 == "" || str1 == null || str1 == undefined)
         && (str2 == "" || str2 == null || str2 == undefined))
      {
         return false;
      }

      return str1 != str2;
   }

   public authorize() {
      const authUri = this.form.get("smtpAuthUri").value;
      const tokenUri = this.form.get("smtpTokenUri").value;
      const scope = this.form.get("smtpOAuthScopes").value;
      const flags = this.form.get("smtpOAuthFlags").value;

      this.authorize0(authUri, tokenUri, scope, flags);
   }

   public authorizeGoogle() {
      const authUri = "https://accounts.google.com/o/oauth2/v2/auth?access_type=offline&prompt=consent";
      const tokenUri = "https://oauth2.googleapis.com/token";
      const scope = "https://mail.google.com/";
      const flags = null;

      this.authorize0(authUri, tokenUri, scope, flags);
   }

   private authorize0(authUri: string, tokenUri: string, scope: string, flags: string) {
      const paramsRequest = {
         user: this.form.get("smtpUser").value,
         clientId: this.form.get("smtpClientId").value,
         clientSecret: this.form.get("smtpClientSecret").value,
         scope: scope,
         authorizationUri: authUri,
         tokenUri: tokenUri,
         flags: flags
      };

      // The specific parameter we need from the server is the license key
      this.httpClient.post<OAuthParameters>("../api/em/general/settings/email/oauth-params", paramsRequest)
         .subscribe((authParams) => {
            this.oauthService.authorize(authParams).subscribe((oAuthTokens) => {
               const expiration = new Date(oAuthTokens.expiration).getTime();
               this.form.get("smtpAccessToken").setValue(oAuthTokens.accessToken);
               this.form.get("smtpRefreshToken").setValue(oAuthTokens.refreshToken);
               this.form.get("tokenExpiration").setValue(expiration);
            });
         });
   }

   protected readonly SMTPAuthType = SMTPAuthType;
}
