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
import { ActivatedRoute } from "@angular/router";
import { of, Subscription } from "rxjs";
import { catchError, tap } from "rxjs/operators";
import { ErrorHandlerService } from "../../common/util/error/error-handler.service";
import { ContextHelp } from "../../context-help";
import { PageHeaderService } from "../../page-header/page-header.service";
import { Searchable } from "../../searchable";
import { Secured } from "../../secured";
import { AuditTableViewComponent } from "../audit-table-view/audit-table-view.component";
import {
   BookmarkHistory,
   BookmarkHistoryList,
   BookmarkHistoryParameters
} from "./bookmark-history";

@Secured({
   route: "/auditing/bookmark-history",
   label: "Bookmark History"
})
@Searchable({
   route: "/auditing/bookmark-history",
   title: "Bookmark History",
   keywords: ["em.keyword.audit", "em.keyword.bookmark", "em.keyword.history"]
})
@ContextHelp({
   route: "/auditing/bookmark-history",
   link: "EMViewAudit"
})
@Component({
   selector: "em-audit-bookmark-history",
   templateUrl: "./audit-bookmark-history.component.html",
   styleUrls: ["./audit-bookmark-history.component.scss"]
})
export class AuditBookmarkHistoryComponent implements OnInit, OnDestroy {
   users: string[] = [];
   actionTypes: string[] = [];
   hosts: string[] = [];
   dashboards: string[] = [];
   form: FormGroup;
   systemAdministrator = false;
   private subscriptions = new Subscription();
   private _displayedColumns = [
      "userName", "userRole", "userEmail", "userLastLogin", "actionType", "execTime",
      "dashboardName", "dashboardAlias", "name", "type", "readOnly", "createTime", "updateTime",
      "activeStatus", "server"
   ];
   columnRenderers = [
      { name: "userName", label: "_#(js:User Name)", value: (r: BookmarkHistory) => r.userName },
      { name: "userRole", label: "_#(js:User Role)", value: (r: BookmarkHistory) => r.userRole },
      { name: "userEmail", label: "_#(js:User Email)", value: (r: BookmarkHistory) => r.userEmail },
      { name: "userLastLogin", label: "_#(js:Last Login)", value: (r: BookmarkHistory) => AuditTableViewComponent.getDisplayDate(r.userLastLogin, r.dateFormat) },
      { name: "actionType", label: "_#(js:Action Type)", value: (r: BookmarkHistory) => r.actionType },
      { name: "execTime", label: "_#(js:Exec Time)", value: (r: BookmarkHistory) => AuditTableViewComponent.getDisplayDate(r.execTime, r.dateFormat) },
      { name: "dashboardName", label: "_#(js:Dashboard Name)", value: (r: BookmarkHistory) => r.dashboardName },
      { name: "dashboardAlias", label: "_#(js:Dashboard Alias)", value: (r: BookmarkHistory) => r.dashboardAlias },
      { name: "name", label: "_#(js:Bookmark Name)", value: (r: BookmarkHistory) => r.name },
      { name: "type", label: "_#(js:Type)", value: (r: BookmarkHistory) => r.type },
      { name: "readOnly", label: "_#(js:Read Only)", value: (r: BookmarkHistory) => r.readOnly },
      { name: "createTime", label: "_#(js:Create Time)", value: (r: BookmarkHistory) => AuditTableViewComponent.getDisplayDate(r.createTime, r.dateFormat) },
      { name: "updateTime", label: "_#(js:Update Time)", value: (r: BookmarkHistory) => AuditTableViewComponent.getDisplayDate(r.updateTime, r.dateFormat) },
      { name: "activeStatus", label: "_#(js:Active Status)", value: (r: BookmarkHistory) => r.activeStatus },
      { name: "server", label: "_#(js:Server)", value: (r: BookmarkHistory) => r.server },
      { name: "organizationId", label: "_#(js:Organization ID)", value: (r: BookmarkHistory) => r.organization }
   ];

   get displayedColumns(): string[] {
      return this._displayedColumns;
   }

   constructor(private http: HttpClient, private activatedRoute: ActivatedRoute,
               private pageTitle: PageHeaderService, private errorService: ErrorHandlerService,
               fb: FormBuilder)
   {
      this.form = fb.group({
         selectedUsers: [[]],
         selectedActionTypes: [[]],
         selectedDashboards: [[]],
         selectedHosts: [[]]
      });
   }

   ngOnInit(): void {
      this.pageTitle.title = "_#(js:Bookmark History)";
   }

   // use arrow function instead of member method to hold the right context (i.e. this)
   fetchParameters = () => {
      return this.http.get<BookmarkHistoryParameters>("../api/em/monitoring/audit/bookmarkHistoryParameters")
         .pipe(
            catchError(error => this.errorService.showSnackBar(error, "_#(js:Failed to get query parameters)", () => of({
               users: [],
               actionTypes: [],
               dashboards: [],
               hosts: [],
               systemAdministrator: false,
               startTime: 0,
               endTime: 0
            }))),
            tap(params => {
               this.users = params.users;
               this.actionTypes = params.actionTypes;
               this.dashboards = params.dashboards;
               this.hosts = params.hosts;
               this.systemAdministrator = params.systemAdministrator;
            })
         );
   };

   // use arrow function instead of member method to hold the right context (i.e. this)
   fetchData = (httpParams: HttpParams, additional: { [key: string]: any; }) => {
      let params = httpParams;
      const selectedUsers: string[] = additional.selectedUsers;

      if(!!selectedUsers && selectedUsers.length > 0) {
         selectedUsers.forEach(u => params = params.append("users", u));
      }
      const selectedActionTypes: string[] = additional.selectedActionTypes;

      if(!!selectedActionTypes && selectedActionTypes.length > 0) {
         selectedActionTypes.forEach(u => params = params.append("actions", u));
      }

      const selectedDashboards: string[] = additional.selectedDashboards;

      if(!!selectedDashboards && selectedDashboards.length > 0) {
         selectedDashboards.forEach(h => params = params.append("dashboards", h));
      }

      const selectedHosts: string[] = additional.selectedHosts;

      if(!!selectedHosts && selectedHosts.length > 0) {
         selectedHosts.forEach(h => params = params.append("hosts", h));
      }

      return this.http.get<BookmarkHistoryList>("../api/em/monitoring/audit/bookmarkHistory", {params})
         .pipe(catchError(error => this.errorService.showSnackBar(error, "_#(js:Failed to run query)", () => of({
            totalRowCount: 0,
            rows: []
         }))));
   };

   ngOnDestroy(): void {
      this.subscriptions.unsubscribe();
   }
}
