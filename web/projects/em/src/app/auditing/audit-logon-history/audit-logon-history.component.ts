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
import { FormBuilder, FormGroup } from "@angular/forms";
import { tap } from "rxjs/operators";
import { ContextHelp } from "../../context-help";
import { PageHeaderService } from "../../page-header/page-header.service";
import { Searchable } from "../../searchable";
import { Secured } from "../../secured";
import { AuditTableViewComponent } from "../audit-table-view/audit-table-view.component";
import { LogonHistory, LogonHistoryList, LogonHistoryParameters } from "./logon-history";
import { Subscription } from "rxjs";
import { ActivatedRoute } from "@angular/router";
import {convertToKey, IdentityId} from "../../settings/security/users/identity-id";

@Secured({
   route: "/auditing/logon-history",
   label: "Logon History"
})
@Searchable({
   route: "/auditing/logon-history",
   title: "Logon History",
   keywords: ["em.keyword.audit", "em.keyword.logon", "em.keyword.history"]
})
@ContextHelp({
   route: "/auditing/logon-history",
   link: "EMAuditingLogonHistory"
})
@Component({
   selector: "em-audit-logon-history",
   templateUrl: "./audit-logon-history.component.html",
   styleUrls: ["./audit-logon-history.component.scss"]
})
export class AuditLogonHistoryComponent implements OnInit, OnDestroy {
   users: string[] = [];
   groups: string[] = [];
   roles: string[] = [];
   form: FormGroup;
   systemAdministrator = false;
   private subscriptions = new Subscription();
   private _displayedColumns = [ "userId", "userHost", "logonTime", "opStatus", "opError", "serverHostName" ];
   columnRenderers = [
      { name: "userId", label: "_#(js:User)", value: (r: LogonHistory) => r.userId },
      { name: "userHost", label: "_#(js:User Host)", value: (r: LogonHistory) => r.userHost },
      { name: "logonTime", label: "_#(js:Logon Time)", value: (r: LogonHistory) => AuditTableViewComponent.getDisplayDate(r.logonTime) },
      { name: "opStatus", label: "_#(js:Status)", value: (r: LogonHistory) => r.opStatus },
      { name: "opError", label: "_#(js:Error)", value: (r: LogonHistory) => r.opError },
      { name: "serverHostName", label: "_#(js:Server)", value: (r: LogonHistory) => r.serverHostName },
      { name: "organizationId", label: "_#(js:Organization ID)", value: (r: LogonHistory) => r.organizationId }
   ];

   get displayedColumns(): string[] {
      return this._displayedColumns;
   }

   constructor(private http: HttpClient, private activatedRoute: ActivatedRoute,
               private pageTitle: PageHeaderService, fb: FormBuilder)
   {
      this.form = fb.group({
         selectedUsers: [[]],
         selectedGroups: [[]],
         selectedRoles: [[]]
      });
   }

   getRoleLabel(role: string): string {
      if(role.endsWith("__GLOBAL__")) {
         return role.substring(0, role.length - 11);
      }

      return role;
   }

   ngOnInit(): void {
      this.pageTitle.title = "_#(js:Logon History)";
   }

   // use arrow function instead of member method to hold the right context (i.e. this)
   fetchParameters = () => {
      return this.http.get<LogonHistoryParameters>("../api/em/monitoring/audit/logonHistoryParameters")
         .pipe(tap(params => {
            this.users = params.users;
            this.groups = params.groups;
            this.roles = params.roles;
            this.systemAdministrator = params.systemAdministrator;
         }));
   };

   // use arrow function instead of member method to hold the right context (i.e. this)
   fetchData = (httpParams: HttpParams, additional: { [key: string]: any; }) => {
      let params = httpParams;
      const selectedUsers: string[] = additional.selectedUsers;

      if(!!selectedUsers && selectedUsers.length > 0) {
         selectedUsers.forEach(u => params = params.append("users", u));
      }

      const selectedGroups: string[] = additional.selectedGroups;

      if(!!selectedGroups && selectedGroups.length > 0) {
         selectedGroups.forEach(g => params = params.append("groups", g));
      }

      const selectedRoles: string[] = additional.selectedRoles;

      if(!!selectedRoles && selectedRoles.length > 0) {
         selectedRoles.forEach(r => params = params.append("roles", r));
      }

      return this.http.get<LogonHistoryList>("../api/em/monitoring/audit/logonHistory", {params});
   };

   ngOnDestroy(): void {
      this.subscriptions.unsubscribe();
   }
}
