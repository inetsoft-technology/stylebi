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
import { LogonError, LogonErrorList, LogonErrorParameters } from "./logon-error";
import { Subscription } from "rxjs";
import { ActivatedRoute } from "@angular/router";

@Secured({
   route: "/auditing/logon-error",
   label: "Logon Errors"
})
@Searchable({
   route: "/auditing/logon-error",
   title: "Logon Errors",
   keywords: ["em.keyword.audit", "em.keyword.logon", "em.keyword.error"]
})
@ContextHelp({
   route: "/auditing/logon-error",
   link: "EMAuditingLogonError"
})
@Component({
   selector: "em-audit-logon-error",
   templateUrl: "./audit-logon-error.component.html",
   styleUrls: ["./audit-logon-error.component.scss"]
})
export class AuditLogonErrorComponent implements OnInit, OnDestroy {
   users: string[] = [];
   hosts: string[] = [];
   form: FormGroup;
   systemAdministrator = false;
   private subscriptions = new Subscription();
   private _displayedColumns = [ "userName", "userHost", "logonTime", "errorMessage",  "server" ];
   columnRenderers = [
      { name: "server", label: "_#(js:Server)", value: (r: LogonError) => r.server },
      { name: "userName", label: "_#(js:User Name)", value: (r: LogonError) => r.userName },
      { name: "userHost", label: "_#(js:User Host)", value: (r: LogonError) => r.userHost },
      { name: "logonTime", label: "_#(js:Logon Time)", value: (r: LogonError) => AuditTableViewComponent.getDisplayDate(r.logonTime) },
      { name: "errorMessage", label: "_#(js:Error Message)", value: (r: LogonError) => r.errorMessage },
      { name: "organizationId", label: "_#(js:Organization ID)", value: (r: LogonError) => r.organizationId }
   ];

   get displayedColumns(): string[] {
      return this._displayedColumns;
   }

   constructor(private http: HttpClient, private activatedRoute: ActivatedRoute,
               private pageTitle: PageHeaderService, fb: FormBuilder)
   {
      this.form = fb.group({
         selectedUsers: [[]],
         selectedHosts: [[]]
      });
   }

   ngOnInit(): void {
      this.pageTitle.title = "_#(js:Logon Errors)";
   }

   // use arrow function instead of member method to hold the right context (i.e. this)
   fetchParameters = () => {
      return this.http.get<LogonErrorParameters>("../api/em/monitoring/audit/logonErrorParameters")
         .pipe(tap(params => {
            this.users = params.users;
            this.hosts = params.hosts;
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

      const selectedHosts: string[] = additional.selectedHosts;

      if(!!selectedHosts && selectedHosts.length > 0) {
         selectedHosts.forEach(h => params = params.append("hosts", h));
      }

      return this.http.get<LogonErrorList>("../api/em/monitoring/audit/logonErrors", {params});
   };

   ngOnDestroy(): void {
      this.subscriptions.unsubscribe();
   }
}
