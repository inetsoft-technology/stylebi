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
import { catchError, tap } from "rxjs/operators";
import { ErrorHandlerService } from "../../common/util/error/error-handler.service";
import { ContextHelp } from "../../context-help";
import { PageHeaderService } from "../../page-header/page-header.service";
import { Searchable } from "../../searchable";
import { Secured } from "../../secured";
import { AuditTableViewComponent } from "../audit-table-view/audit-table-view.component";
import { UserSession, UserSessionList, UserSessionParameters } from "./user-session";
import { ActivatedRoute } from "@angular/router";
import { of, Subscription } from "rxjs";

@Secured({
   route: "/auditing/user-session",
   label: "User Sessions"
})
@Searchable({
   route: "/auditing/user-session",
   title: "User Sessions",
   keywords: ["em.keyword.audit", "em.keyword.user", "em.keyword.session"]
})
@ContextHelp({
   route: "/auditing/user-session",
   link: "EMViewAudit"
})
@Component({
   selector: "em-audit-user-session",
   templateUrl: "./audit-user-session.component.html",
   styleUrls: ["./audit-user-session.component.scss"]
})
export class AuditUserSessionComponent implements OnInit, OnDestroy {
   users: string[] = [];
   minDuration = 0;
   maxDuration = 600;
   form: FormGroup;
   systemAdministrator = false;
   private subscriptions = new Subscription();
   private _displayedColumns = [ "userName", "logonTime", "duration", "logoffReason", "server"];
   columnRenderers = [
      { name: "logoffReason", label: "_#(js:Logoff Reason)", value: (r: UserSession) => r.logoffReason },
      { name: "duration", label: "_#(js:Duration)", value: (r: UserSession) => r.duration },
      { name: "server", label: "_#(js:Server)", value: (r: UserSession) => r.server },
      { name: "userName", label: "_#(js:User Name)", value: (r: UserSession) => r.userName },
      { name: "logonTime", label: "_#(js:Logon Time)", value: (r: UserSession) => AuditTableViewComponent.getDisplayDate(r.logonTime, r.dateFormat) },
      { name: "organizationId", label: "_#(js:Organization ID)", value: (r: UserSession) => r.organizationId }
   ];

   get minStartDuration(): number {
      return this.minDuration;
   }

   get maxStartDuration(): number {
      const duration = this.form.get("maxDuration").value || 0;
      return Math.max(duration, this.maxDuration);
   }

   get minEndDuration(): number {
      const duration = this.form.get("minDuration").value || 0;
      return Math.min(duration, this.minDuration);
   }

   get maxEndDuration(): number {
      return this.maxDuration;
   }

   get displayedColumns(): string[] {
      return this._displayedColumns;
   }

   constructor(private http: HttpClient, private activatedRoute: ActivatedRoute,
               private pageTitle: PageHeaderService, private errorService: ErrorHandlerService,
               fb: FormBuilder)
   {
      this.form = fb.group({
         selectedUsers: [[]],
         selectedOrganizations: [[]],
         minDuration: [0],
         maxDuration: [600]
      });
   }

   ngOnInit(): void {
      this.pageTitle.title = "_#(js:User Sessions)";
   }

   // use arrow function instead of member method to hold the right context (i.e. this)
   fetchParameters = () => {
      return this.http.get<UserSessionParameters>("../api/em/monitoring/audit/userSessionParameters")
         .pipe(
            catchError(error => this.errorService.showSnackBar(error, "_#(js:Failed to get query parameters)", () => of({
               users: [],
               systemAdministrator: false,
               maxDuration: 0,
               startTime: 0,
               endTime: 0
            }))),
            tap(params => {
               this.users = params.users;
               this.systemAdministrator = params.systemAdministrator;

               if(params.maxDuration > this.maxDuration && this.form.get("maxDuration").value == this.maxDuration) {
                  this.maxDuration = params.maxDuration;
                  this.form.get("maxDuration").setValue(this.maxDuration, {emitEvent: false});
               }
            }));
   };

   // use arrow function instead of member method to hold the right context (i.e. this)
   fetchData = (httpParams: HttpParams, additional: { [key: string]: any; }) => {
      let params = httpParams;
      const selectedUsers: string[] = additional.selectedUsers;

      if(!!selectedUsers && selectedUsers.length > 0) {
         selectedUsers.forEach(u => params = params.append("users", u));
      }

      let duration: number = additional.minDuration;

      if(!!duration && duration) {
         params = params.set("minDuration", duration);
      }

      duration = additional.maxDuration;

      if(!!duration && duration) {
         params = params.set("maxDuration", duration);
      }

      return this.http.get<UserSessionList>("../api/em/monitoring/audit/userSessions", {params})
         .pipe(catchError(error => this.errorService.showSnackBar(error, "_#(js:Failed to run query)", () => of({
            totalRowCount: 0,
            rows: []
         }))));
   };

   ngOnDestroy(): void {
      this.subscriptions.unsubscribe();
   }
}
