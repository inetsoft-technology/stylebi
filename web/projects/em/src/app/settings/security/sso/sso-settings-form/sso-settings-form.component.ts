/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { Component, Input, OnChanges, SimpleChanges } from "@angular/core";
import { UntypedFormGroup } from "@angular/forms";

@Component({
   selector: "em-sso-settings-form",
   templateUrl: "./sso-settings-form.component.html",
   styleUrls: ["./sso-settings-form.component.scss"]
})
export class SSOSettingsFormComponent implements OnChanges {
   @Input() public form: UntypedFormGroup;
   public formControlNames: string[] = [];

   ngOnChanges(changes: SimpleChanges) {
      if(changes.form && this.form) {
         this.formControlNames = Object.keys(this.form.controls);
      }
   }

   labels = {
      "spEntityId": "_#(js:SP Entity ID)",
      "assertionUrl": "_#(js:Assertion URL)",
      "idpEntityId": "_#(js:IdP Entity ID)",
      "idpSignOnUrl": "_#(js:IdP Sign-On URL)",
      "idpLogoutUrl": "_#(js:IdP Logout URL)",
      "idpPublicKey": "_#(js:IdP Public Key)",
      "clientId": "_#(js:Client ID)",
      "clientSecret": "_#(js:Client Secret)",
      "issuer": "_#(js:Issuer)",
      "tokenEndpoint": "_#(js:Token Endpoint)",
      "authorizationEndpoint": "_#(js:Authorization Endpoint)",
      "nameClaim": "_#(js:Name Claim)",
      "roleClaim": "_#(js:Role Claim)",
      "groupClaim": "_#(js:Group Claim)",
      "orgIDClaim": "_#(js:Organization ID Claim)",
      "domain": "_#(js:Domain)",
      "jwkProvider": "_#(js:JWK Provider)",
      "logoutUrl": "_#(js:Logout URL)",
      "userInfoAudience": "_#(js:User Info Audience)",
      "claim": "_#(js:Claim)",
      "scope": "_#(js:Scope)"
   };
}
