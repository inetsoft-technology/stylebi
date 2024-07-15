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
import { FormBuilder, FormGroup } from "@angular/forms";
import { tap } from "rxjs/operators";
import { ContextHelp } from "../../context-help";
import { PageHeaderService } from "../../page-header/page-header.service";
import { Searchable } from "../../searchable";
import { Secured } from "../../secured";
import { AuditTableViewComponent } from "../audit-table-view/audit-table-view.component";
import { IdentityInfo, IdentityInfoList, IdentityInfoParameters } from "./identity-info";
import { Subscription } from "rxjs";
import { ActivatedRoute } from "@angular/router";
import {IdentityId} from "../../settings/security/users/identity-id";

@Secured({
   route: "/auditing/identity-info",
   label: "Identity Information"
})
@Searchable({
   route: "/auditing/identity-info",
   title: "Identity Information",
   keywords: ["em.keyword.audit", "em.keyword.identity", "em.keyword.information"]
})
@ContextHelp({
   route: "/auditing/identity-info",
   link: "EMAuditingIdentityInfo"
})
@Component({
   selector: "em-audit-identity-info",
   templateUrl: "./audit-identity-info.component.html",
   styleUrls: ["./audit-identity-info.component.scss"]
})
export class AuditIdentityInfoComponent implements OnInit, OnDestroy {
   users: string[] = [];
   groups: string[] = [];
   roles: string[] = [];
   actions = [ "c", "d", "m", "r" ];
   states = [ "0", "1", "2" ];
   hosts: string[] = [];
   organizations: string[] = [];
   orgNames: string[] = [];
   form: FormGroup;
   systemAdministrator = false;
   organizationFilter = false;
   private subscriptions = new Subscription();
   private _displayedColumns = [
      "name", "type", "actionType", "actionTime", "actionDescription", "state", "server"
   ];
   columnRenderers = [
      { name: "name", label: "_#(js:Name)", value: (r: IdentityInfo) => r.name },
      { name: "type", label: "_#(js:Type)", value: (r: IdentityInfo) => this.getTypeLabel(r.type) },
      { name: "actionType", label: "_#(js:Action Type)", value: (r: IdentityInfo) => this.getActionTypeLabel(r.actionType) },
      { name: "actionTime", label: "_#(js:Action Time)", value: (r: IdentityInfo) => AuditTableViewComponent.getDisplayDate(r.actionTime) },
      { name: "server", label: "_#(js:Server)", value: (r: IdentityInfo) => r.server },
      { name: "actionDescription", label: "_#(js:Action Description)", value: (r: IdentityInfo) => r.actionDescription },
      { name: "state", label: "_#(js:State)", value: (r: IdentityInfo) => this.getStateLabel(r.state) },
      { name: "organizationId", label: "_#(js:Organization ID)", value: (r: IdentityInfo) => r.organizationId }
   ];

   get displayedColumns(): string[] {
      return this._displayedColumns;
   }

   constructor(private http: HttpClient, private activatedRoute: ActivatedRoute,
               private pageTitle: PageHeaderService, fb: FormBuilder)
   {
      this.form = fb.group({
         selectedUsers: [[]],
         selectedRoles: [[]],
         selectedGroups: [[]],
         selectedActions: [[]],
         selectedStates: [[]],
         selectedHosts: [[]],
         selectedOrganizations: [[]]
      });
   }

   ngOnInit(): void {
      this.pageTitle.title = "_#(js:Identity Information)";
      this.http.get<string[]>("../api/em/security/users/get-all-organizations/").subscribe(
          (orgList => this.orgNames = orgList)
       );

      this.form.get("selectedOrganizations").valueChanges
         .subscribe(() => this.onOrganizationChange());
   }

   private onOrganizationChange(): void {
      if(!!this.form.get("selectedOrganizations").value) {
         this.fetchParameters0().subscribe(() => {});
      }
   }

   // use arrow function instead of member method to hold the right context (i.e. this)
   fetchParameters = () => {
      return this.http.get<IdentityInfoParameters>("../api/em/monitoring/audit/identityInfoOrganizations")
         .pipe(tap(params => {
            this.organizations = params.organizations;
            this.organizationFilter = params.organizationFilter;

            if(!this.organizationFilter) {
               this.fetchParameters0().subscribe(() => {});
            }
         }));
   };

   fetchParameters0() {
      let params = new HttpParams();
      const organizations = this.form.get("selectedOrganizations").value;

      if(!!organizations) {
         params = params.append("organizations", organizations);
      }

      return this.http.get<IdentityInfoParameters>("../api/em/monitoring/audit/identityInfoParameters", {params})
         .pipe(tap(params => {
            this.users = params.users;
            this.groups = params.groups;
            this.roles = params.roles;
            this.hosts = params.hosts;
            this.systemAdministrator = params.systemAdministrator;
         }));
   }
   // use arrow function instead of member method to hold the right context (i.e. this)
   fetchData = (httpParams: HttpParams, additional: { [key: string]: any; }) => {
      let params = httpParams;
      const selectedUsers: string[] = additional.selectedUsers;

      if(!!selectedUsers && selectedUsers.length > 0) {
         selectedUsers.forEach(u => params = params.append("users", u));
      }

      const selectedRoles: string[] = additional.selectedRoles;

      if(!!selectedRoles && selectedRoles.length > 0) {
         selectedRoles.forEach(r => params = params.append("roles", r));
      }

      const selectedGroups: string[] = additional.selectedGroups;

      if(!!selectedGroups && selectedGroups.length > 0) {
         selectedGroups.forEach(g => params = params.append("groups", g));
      }

      const selectedActions: string[] = additional.selectedActions;

      if(!!selectedActions && selectedActions.length > 0) {
         selectedActions.forEach(a => params = params.append("actions", a));
      }

      const selectedStates: string[] = additional.selectedStates;

      if(!!selectedStates && selectedStates.length > 0) {
         selectedStates.forEach(s => params = params.append("states", s));
      }

      const selectedHosts: string[] = additional.selectedHosts;

      if(!!selectedHosts && selectedHosts.length > 0) {
         selectedHosts.forEach(h => params = params.append("hosts", h));
      }

      const selectedOrgs: string[] = additional.selectedOrganizations;

      if(!!selectedOrgs && selectedOrgs.length > 0) {
         selectedOrgs.forEach(h => params = params.append("organizations", h));
      }

      return this.http.get<IdentityInfoList>("../api/em/monitoring/audit/identityInfo", {params});
   };

   getTypeLabel(type: string): string {
      if(type === "u") {
         return "_#(js:User)";
      }
      else if(type === "g") {
         return "_#(js:Group)";
      }
      else if(type === "r") {
         return "_#(js:Role)";
      }
      else if(type === "o") {
         return "_#(js:Organization)";
      }
      else {
         return type;
      }
   }

   getActionTypeLabel(type: string): string {
      if(type === "c") {
         return "_#(js:Create)";
      }
      else if(type == "d") {
         return "_#(js:Delete)";
      }
      else if(type == "m") {
         return "_#(js:Modify)";
      }
      else if(type == "r") {
         return "_#(js:Rename)";
      }
      else {
         return type;
      }
   }

   getRoleLabel(role: string): string {
      if(role.endsWith("__GLOBAL__")) {
         return role.substring(0, role.length - 11);
      }

      return role;
   }

   getStateLabel(state: string): string {
      if(state === "0") {
         return "_#(js:Active)";
      }
      else if(state === "1") {
         return "_#(js:Inactive)";
      }
      else if(state === "2") {
         return "_#(js:None)";
      }
      else {
         return state;
      }
   }

   ngOnDestroy(): void {
      this.subscriptions.unsubscribe();
   }
}
