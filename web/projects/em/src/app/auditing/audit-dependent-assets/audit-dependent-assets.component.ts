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
import { HttpClient, HttpParams } from "@angular/common/http";
import { Component, OnDestroy, OnInit } from "@angular/core";
import { FormBuilder, FormGroup, Validators } from "@angular/forms";
import { tap } from "rxjs/operators";
import { ContextHelp } from "../../context-help";
import { PageHeaderService } from "../../page-header/page-header.service";
import { Searchable } from "../../searchable";
import { Secured } from "../../secured";
import { AssetModel } from "./asset-model";
import {
   ASSET_TYPES,
   AssetOption,
   IMPOSSIBLE_DEPENDENCIES,
   NONE_USER,
   USER_ASSET_TYPES
} from "./dependency-util";
import { DependentAsset, DependentAssetList, DependentAssetParameters } from "./dependent-assets";
import { Subscription } from "rxjs";
import { ActivatedRoute } from "@angular/router";

@Secured({
   route: "/auditing/dependent-assets",
   label: "Dependent Assets"
})
@Searchable({
   route: "/auditing/dependent-assets",
   title: "Dependent Assets",
   keywords: ["em.keyword.audit", "em.keyword.dependent", "em.keyword.asset"]
})
@ContextHelp({
   route: "/auditing/dependent-assets",
   link: "EMAuditingDependentAssets"
})
@Component({
  selector: "em-audit-dependent-assets",
  templateUrl: "./audit-dependent-assets.component.html",
  styleUrls: ["./audit-dependent-assets.component.scss"]
})
export class AuditDependentAssetsComponent implements OnInit, OnDestroy {
   allUsers: AssetOption[] = [];
   targetTypes = ASSET_TYPES;
   targetUsers = [ NONE_USER ];
   targetAssets: AssetModel[] = [];
   dependentTypes = [];
   dependentUsers = this.allUsers;
   form: FormGroup;
   private subscriptions = new Subscription();
   displayedColumns = [
      "targetType", "targetName", "targetUser", "dependentType", "dependentName", "dependentUser",
      "description"
   ];
   columnRenderers = [
      { name: "targetType", label: "_#(js:Target Type)", value: (r: DependentAsset) => r.targetType },
      { name: "targetName", label: "_#(js:Target Name)", value: (r: DependentAsset) => r.targetName },
      { name: "targetUser", label: "_#(js:Target User)", value: (r: DependentAsset) => r.targetUser },
      { name: "dependentType", label: "_#(js:Dependent Type)", value: (r: DependentAsset) => r.dependentType },
      { name: "dependentName", label: "_#(js:Dependent Name)", value: (r: DependentAsset) => r.dependentName },
      { name: "dependentUser", label: "_#(js:Dependent User)", value: (r: DependentAsset) => r.dependentUser },
      { name: "description", label: "_#(js:Description)", value: (r: DependentAsset) => r.description },
      { name: "organizationId", label: "_#(js:Organization ID)", value: (r: DependentAsset) => r.organizationId }
   ];

   constructor(private http: HttpClient, private activatedRoute: ActivatedRoute,
               private pageTitle: PageHeaderService, fb: FormBuilder)
   {
      this.form = fb.group({
         selectedTargetType: ["DATA_SOURCE", [Validators.required]],
         selectedTargetUser: [""],
         selectedTargetAssets: [[]],
         selectedDependentTypes: [[]],
         selectedDependentUsers: [[]]
      });
   }

   ngOnInit(): void {
      this.pageTitle.title = "_#(js:Dependent Assets)";
      this.form.get("selectedTargetType").valueChanges
         .subscribe(() => this.onTargetTypeChange());
      this.form.get("selectedTargetUser").valueChanges
         .subscribe(() => this.onTargetUserChange());
      this.form.get("selectedDependentTypes").valueChanges
         .subscribe(() => this.onDependentTypesChange());

      this.dependentTypes = ASSET_TYPES.filter(t =>
         !IMPOSSIBLE_DEPENDENCIES.get(t.value).has("DATA_SOURCE"));
   }

   // use arrow function instead of member method to hold the right context (i.e. this)
   fetchParameters = () => {
      let params = new HttpParams();
      const type = this.form.get("selectedTargetType").value;

      if(!!type) {
         params = params.set("type", type);
      }

      const user = this.form.get("selectedTargetUser").value;

      if(!!user) {
         params = params.append("user", user);
      }

      return this.http.get<DependentAssetParameters>("../api/em/monitoring/audit/dependentAssetParameters", {params})
         .pipe(tap(p => {
            this.allUsers.push(NONE_USER);
            this.allUsers = p.users.map(u => ({value: u, label: u}));
            this.targetAssets = p.assets;
         }));
   };

   // use arrow function instead of member method to hold the right context (i.e. this)
   fetchData = (httpParams: HttpParams, additional: { [key: string]: any; }) => {
      const type: string = this.form.get("selectedTargetType").value;
      let params = httpParams;
      const selectedTargetAssets = additional.selectedTargetAssets;

      if(!!selectedTargetAssets && selectedTargetAssets.length > 0) {
         selectedTargetAssets.forEach((a) => {
            params = params.append("targetAssets", a);
         });
      }
      else {
         this.targetAssets.forEach((a) => {
            params = params.append("targetAssets", a.assetId);
         });
      }

      const selectedDependentTypes: string[] = additional.selectedDependentTypes;

      if(!!selectedDependentTypes && selectedDependentTypes.length > 0) {
         selectedDependentTypes.forEach(t => params = params.append("dependentTypes", t));
      }

      const selectedDependentUsers: string[] = additional.selectedDependentUsers;

      if(!!selectedDependentUsers && selectedDependentUsers.length > 0) {
         selectedDependentUsers.forEach(u => params = params.append("dependentUsers", u));
      }

      return this.http.get<DependentAssetList>("../api/em/monitoring/audit/dependentAssets", {params});
   };

   private onTargetTypeChange(): void {
      const type: string = this.form.get("selectedTargetType").value;

      if(!!type) {
         if(USER_ASSET_TYPES.has(type)) {
            this.targetUsers = [ NONE_USER ];
            this.allUsers.forEach(u => this.targetUsers.push(u));
         }
         else {
            this.targetUsers = [ NONE_USER ];
         }

         this.dependentTypes = ASSET_TYPES.filter(t =>
            !IMPOSSIBLE_DEPENDENCIES.get(t.value).has(type));
      }
      else {
         this.targetUsers = [ NONE_USER ];
         this.dependentTypes = [];
      }

      if(!!type) {
         this.fetchParameters().subscribe(() => {});
      }
   }

   private onTargetUserChange(): void {
      if(!!this.form.get("selectedTargetType").value) {
         this.fetchParameters().subscribe(() => {});
      }
   }

   private onDependentTypesChange(): void {
      const types: string[] = this.form.get("selectedDependentTypes").value;

      if(!!types && types.length > 0 && !types.find(t => USER_ASSET_TYPES.has(t))) {
         this.dependentUsers = [NONE_USER];
      }
      else {
         this.dependentUsers = this.allUsers;
      }
   }

   getDisplayedColumns() {
      return this.displayedColumns;
   }

   ngOnDestroy(): void {
      this.subscriptions.unsubscribe();
   }
}
