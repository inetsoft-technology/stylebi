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
import { HttpClient, HttpParams } from "@angular/common/http";
import { Component, OnDestroy, OnInit } from "@angular/core";
import { FormBuilder, FormGroup, Validators } from "@angular/forms";
import { tap } from "rxjs/operators";
import { ContextHelp } from "../../context-help";
import { PageHeaderService } from "../../page-header/page-header.service";
import { Searchable } from "../../searchable";
import { Secured } from "../../secured";
import { IdentityId } from "../../settings/security/users/identity-id";
import { AssetModel } from "../audit-dependent-assets/asset-model";
import {
   ASSET_REQUIRED_TYPES, ASSET_TYPES,
   AssetOption,
   getAssetLabel,
   IMPOSSIBLE_DEPENDENCIES,
   NONE_USER,
   USER_ASSET_TYPES
} from "../audit-dependent-assets/dependency-util";
import { DependentAsset } from "../audit-dependent-assets/dependent-assets";
import { RequiredAssetList, RequiredAssetParameters } from "./required-asset";
import { Subscription } from "rxjs";
import { ActivatedRoute } from "@angular/router";

@Secured({
   route: "/auditing/required-assets",
   label: "Required Assets"
})
@Searchable({
   route: "/auditing/required-assets",
   title: "Required Assets",
   keywords: ["em.keyword.audit", "em.keyword.required", "em.keyword.asset"]
})
@ContextHelp({
   route: "/auditing/required-assets",
   link: "EMAuditingRequiredAssets"
})
@Component({
  selector: "em-audit-required-assets",
  templateUrl: "./audit-required-assets.component.html",
  styleUrls: ["./audit-required-assets.component.scss"]
})
export class AuditRequiredAssetsComponent implements OnInit, OnDestroy {
   allUsers: AssetOption[] = [];
   dependentTypes = ASSET_REQUIRED_TYPES;
   dependentUsers = [ NONE_USER ];
   dependentAssets: AssetModel[] = [];
   targetTypes: AssetOption[] = [];
   targetUsers = this.allUsers;
   form: FormGroup;
   private subscriptions = new Subscription();
   displayedColumns = [
      "dependentType", "dependentName", "dependentUser", "targetType", "targetName", "targetUser",
      "description"
   ];

   columnRenderers = [
      { name: "dependentType", label: "_#(js:Target Type)", value: (r: DependentAsset) => r.dependentType },
      { name: "dependentName", label: "_#(js:Target Name)", value: (r: DependentAsset) => r.dependentName },
      { name: "dependentUser", label: "_#(js:Target User)", value: (r: DependentAsset) => r.dependentUser },
      { name: "targetType", label: "_#(js:Required Type)", value: (r: DependentAsset) => r.targetType },
      { name: "targetName", label: "_#(js:Required Name)", value: (r: DependentAsset) => r.targetName },
      { name: "targetUser", label: "_#(js:Required User)", value: (r: DependentAsset) => r.targetUser },
      { name: "description", label: "_#(js:Description)", value: (r: DependentAsset) => r.description },
      { name: "organizationId", label: "_#(js:Organization ID)", value: (r: DependentAsset) => r.organizationId }
   ];
   getAssetLabel = getAssetLabel;

   constructor(private http: HttpClient, private activatedRoute: ActivatedRoute,
               private pageTitle: PageHeaderService, fb: FormBuilder)
   {
      this.form = fb.group({
         selectedDependentType: ["DATA_SOURCE", [Validators.required]],
         selectedDependentUser: [""],
         selectedDependentAssets: [[]],
         selectedTargetTypes: [[]],
         selectedTargetUsers: [[]]
      });
   }

   ngOnInit(): void {
      this.pageTitle.title = "_#(js:Required Assets)";
      this.form.get("selectedDependentType").valueChanges
         .subscribe(() => this.onDependentTypeChange());
      this.form.get("selectedDependentUser").valueChanges
         .subscribe(() => this.onDependentUserChange());
      this.form.get("selectedTargetTypes").valueChanges
         .subscribe(() => this.onTargetTypesChange());

      const impossible = IMPOSSIBLE_DEPENDENCIES.get("DATA_SOURCE");
      this.targetTypes = ASSET_TYPES.filter(t => !impossible.has(t.value));
   }

   // use arrow function instead of member method to hold the right context (i.e. this)
   fetchParameters = () => {
      let params = new HttpParams();
      const type = this.form.get("selectedDependentType").value;

      if(!!type) {
         params = params.set("type", type);
      }

      const user = this.form.get("selectedDependentUser").value;

      if(!!user) {
         params = params.append("user", user);
      }

      return this.http.get<RequiredAssetParameters>("../api/em/monitoring/audit/requiredAssetParameters", {params})
         .pipe(tap(p => {
            this.allUsers = p.users.map(u => ({value: u, label: u}));
            this.dependentAssets = p.assets;
         }));
   };

   // use arrow function instead of member method to hold the right context (i.e. this)
   fetchData = (httpParams: HttpParams, additional: { [key: string]: any; }) => {
      let params = httpParams;
      const selectedDependentAssets: string[] = additional.selectedDependentAssets;

      const filteredAssets = selectedDependentAssets.filter(
         assetId => !!this.dependentAssets
            .find(asset => asset.assetId == assetId))

      if(!!filteredAssets && filteredAssets.length > 0) {
         filteredAssets.forEach(a => params = params.append("dependentAssets", a));
      }
      else {
         this.dependentAssets.forEach(a => params = params.append("dependentAssets", a.assetId));
      }

      const selectedTargetTypes: string[] = additional.selectedTargetTypes;

      if(!!selectedTargetTypes && selectedTargetTypes.length > 0) {
         selectedTargetTypes.forEach(t => params = params.append("targetTypes", t));
      }

      const selectedTargetUsers: string[] = additional.selectedTargetUsers;

      if(!!selectedTargetUsers && selectedTargetUsers.length > 0) {
         selectedTargetUsers.forEach(u => params = params.append("targetUsers", u));
      }

      return this.http.get<RequiredAssetList>("../api/em/monitoring/audit/requiredAssets", {params});
   };

   private onDependentTypeChange(): void {
      const type: string = this.form.get("selectedDependentType").value;
      this.form.get("selectedTargetTypes").setValue(null)

      if(!!type) {
         if(USER_ASSET_TYPES.has(type)) {
            this.dependentUsers = [ NONE_USER ];
            this.allUsers.forEach(u => this.dependentUsers.push(u));
         }
         else {
            this.dependentUsers = [ NONE_USER ];
         }

         if(IMPOSSIBLE_DEPENDENCIES.has(type)) {
            const impossible = IMPOSSIBLE_DEPENDENCIES.get(type);
            this.targetTypes = ASSET_TYPES.filter(t => !impossible.has(t.value));
         }
         else {
            this.targetTypes = ASSET_REQUIRED_TYPES;
         }
      }
      else {
         this.dependentUsers = [ NONE_USER ];
         this.targetTypes = [];
      }

      if(!!type) {
         this.fetchParameters().subscribe(() => {});
      }
   }

   private onDependentUserChange(): void {
      if(!!this.form.get("selectedDependentType").value) {
         this.fetchParameters().subscribe(() => {});
      }
   }

   private onTargetTypesChange(): void {
      const types: string[] = this.form.get("selectedTargetTypes").value;

      if(!!types && types.length > 0 && !types.find(t => USER_ASSET_TYPES.has(t))) {
         this.targetUsers = [];
      }

      this.targetUsers = [ NONE_USER ];
      this.allUsers.forEach(u => this.targetUsers.push(u));
   }

   getDisplayedColumns() {
      return this.displayedColumns;
   }

   ngOnDestroy(): void {
      this.subscriptions.unsubscribe();
   }
}
