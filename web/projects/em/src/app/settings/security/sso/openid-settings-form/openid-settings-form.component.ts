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
import { COMMA, ENTER, SPACE } from "@angular/cdk/keycodes";
import {
   Component,
   ElementRef,
   EventEmitter,
   Input,
   NgZone, OnDestroy,
   Output,
   ViewChild
} from "@angular/core";
import { UntypedFormBuilder, UntypedFormGroup } from "@angular/forms";
import { MatAutocompleteSelectedEvent } from "@angular/material/autocomplete";
import { MatChipInputEvent } from "@angular/material/chips";
import { MatDialog } from "@angular/material/dialog";
import { Observable, Subject } from "rxjs";
import { map, startWith, takeUntil } from "rxjs/operators";
import { MessageDialog, MessageDialogType } from "../../../../common/util/message-dialog";
import { OpenIdAttributesModel } from "../sso-settings-model";

interface OpenIdDiscovery {
   issuer?: string;
   authorization_endpoint?: string;
   token_endpoint?: string;
   jwks_uri?: string;
   scopes_supported?: string[];
   claims_supported?: string[];
}

@Component({
   selector: "em-openid-settings-form",
   templateUrl: "./openid-settings-form.component.html",
   styleUrls: ["./openid-settings-form.component.scss"]
})
export class OpenidSettingsFormComponent implements OnDestroy {
   supportedScopes: string[] = [];
   filteredScopes: Observable<string[]>;
   selectedScopes: string[] = [];
   separatorKeyCodes = [ ENTER, SPACE, COMMA ];
   supportedClaims: string[] = [];
   filteredNameClaims: Observable<string[]>;
   filteredGroupClaims: Observable<string[]>;
   filteredRoleClaims: Observable<string[]>;
   filteredOrganizationIDClaims: Observable<string[]>;
   form: UntypedFormGroup;
   @ViewChild("scopeInput") scopeInput: ElementRef<HTMLInputElement>;
   @Output() modelChange = new EventEmitter<OpenIdAttributesModel>();
   private destroy$ = new Subject<void>();
   disableDiscoveryApply: boolean = false;

   @Input()
   get model(): OpenIdAttributesModel {
      const formValue = this.form.value;
      formValue.scopes = this.selectedScopes.join(" ");
      const { discoveryUrl, scopeInput, ...model } = formValue;
      return model;
   }

   set model(value: OpenIdAttributesModel) {
      this.form.get("clientId").setValue(value?.clientId, { emitEvent: false });
      this.form.get("clientSecret").setValue(value?.clientSecret, { emitEvent: false });
      this.selectedScopes = !!value?.scopes ? value.scopes.split(" ") : [];
      this.form.get("issuer").setValue(value?.issuer, { emitEvent: false });
      this.form.get("audience").setValue(value?.audience, { emitEvent: false });
      this.form.get("authorizationEndpoint")
         .setValue(value?.authorizationEndpoint, { emitEvent: false });
      this.form.get("tokenEndpoint").setValue(value?.tokenEndpoint, { emitEvent: false });
      this.form.get("jwksUri").setValue(value?.jwksUri, { emitEvent: false });
      this.form.get("jwkCertificate").setValue(value?.jwkCertificate, { emitEvent: false });
      this.form.get("nameClaim").setValue(value?.nameClaim, { emitEvent: false });
      this.form.get("roleClaim").setValue(value?.roleClaim, { emitEvent: false });
      this.form.get("groupClaim").setValue(value?.groupClaim, { emitEvent: false });
      this.form.get("orgIDClaim").setValue(value?.orgIDClaim, { emitEvent: false });
   }

   constructor(private dialog: MatDialog, private zone: NgZone, fb: UntypedFormBuilder) {
      this.form = fb.group({
         discoveryUrl: [""],
         clientId: [""],
         clientSecret: [""],
         scopeInput: [""],
         issuer: [""],
         audience: [""],
         authorizationEndpoint: [""],
         tokenEndpoint: [""],
         jwksUri: [""],
         jwkCertificate: [""],
         nameClaim: [""],
         roleClaim: [""],
         groupClaim: [""],
         orgIDClaim: [""]
      });

      [
         "clientId", "clientSecret", "issuer", "audience", "authorizationEndpoint", "tokenEndpoint",
         "jwksUri", "jwkCertificate", "nameClaim", "roleClaim", "groupClaim", "orgIDClaim"
      ].forEach(key => this.form.get(key).valueChanges
         .pipe(takeUntil(this.destroy$))
         .subscribe(() => Promise.resolve(null).then(() => this.modelChange.emit(this.model))));

      this.filteredScopes = this.form.get("scopeInput").valueChanges.pipe(
         startWith(null, null),
         map((scope: string | null) => (scope ? this.filterScopes(scope) : this.supportedScopes.slice()))
      );
      this.filteredNameClaims = this.form.get("nameClaim").valueChanges.pipe(
         startWith(null, null),
         map((claim: string | null) => (claim ? this.filterClaims(claim) : this.supportedClaims.slice()))
      );
      this.filteredRoleClaims = this.form.get("roleClaim").valueChanges.pipe(
         startWith(null, null),
         map((claim: string | null) => (claim ? this.filterClaims(claim) : this.supportedClaims.slice()))
      );
      this.filteredGroupClaims = this.form.get("groupClaim").valueChanges.pipe(
         startWith(null, null),
         map((claim: string | null) => (claim ? this.filterClaims(claim) : this.supportedClaims.slice()))
      );
      this.filteredOrganizationIDClaims = this.form.get("orgIDClaim").valueChanges.pipe(
         startWith(null, null),
         map((claim: string | null) => (claim ? this.filterClaims(claim) : this.supportedClaims.slice()))
      );
   }

   ngOnDestroy(): void {
      this.destroy$.next();
      this.destroy$.unsubscribe();
   }

   loadDiscovery(): void {
      this.disableDiscoveryApply = true;
      const url = this.form?.get("discoveryUrl").value;

      if(!!url) {
         // use fetch because HTTP client adds headers that may fail pre-flight checks for certain
         // servers like Auth0
         this.zone.runOutsideAngular(() => fetch(url)
            .then(response => response.json())
            .then(data => data as OpenIdDiscovery)
            .then(
               (discovery) => this.zone.run(() => this.applyDiscovery(discovery)),
               () => this.zone.run(() => this.showDiscoveryError())));
      }
   }

   enableDiscoveryApply() {
      this.disableDiscoveryApply = false;
   }

   private applyDiscovery(discovery: OpenIdDiscovery): void {
      if(!!this.form) {
         this.form.get("issuer").setValue(discovery.issuer);
         this.form.get("authorizationEndpoint").setValue(discovery.authorization_endpoint);
         this.form.get("tokenEndpoint").setValue(discovery.token_endpoint);
         this.form.get("jwksUri").setValue(discovery.jwks_uri);
         this.supportedScopes = discovery.scopes_supported || [];
         this.supportedClaims = discovery.claims_supported || [];
      }
   }

   private showDiscoveryError(): void {
      this.dialog.open(MessageDialog, {
         width: "350px",
         data: {
            title: "_#(js:Error)",
            content: "_#(js:openid.discovery.error)",
            type: MessageDialogType.ERROR
         }
      });
   }

   addScope(event: MatChipInputEvent): void {
      const value = (event.value || "").trim();

      if(value) {
         this.selectedScopes.push(value);
      }

      event.chipInput?.clear();
      this.form?.get("scopeInput")?.setValue(null);
      this.modelChange.emit(this.model);
   }

   removeScope(scope: string): void {
      const index = this.selectedScopes.indexOf(scope);

      if(index >= 0) {
         this.selectedScopes.splice(index, 1);
         this.modelChange.emit(this.model);
      }
   }

   scopeSelected(event: MatAutocompleteSelectedEvent): void {
      this.selectedScopes.push(event.option.viewValue);
      this.scopeInput.nativeElement.value = "";
      this.form?.get("scopeInput")?.setValue(null);
      this.modelChange.emit(this.model);
   }

   private filterScopes(value: string): string[] {
      const filterValue = value.toLowerCase();
      return this.supportedScopes.filter(scope => scope.toLowerCase().includes(filterValue));
   }

   private filterClaims(value: string): string[] {
      const filterValue = value.toLowerCase();
      return this.supportedClaims.filter(claim => claim.toLowerCase().includes(filterValue));
   }
}
