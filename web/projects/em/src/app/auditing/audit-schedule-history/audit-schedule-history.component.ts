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
import { AssetModel } from "../audit-dependent-assets/asset-model";
import { AuditTableViewComponent } from "../audit-table-view/audit-table-view.component";
import {
   ScheduleHistory,
   ScheduleHistoryList,
   ScheduleHistoryParameters
} from "./schedule-history";
import { of, Subscription } from "rxjs";
import { ActivatedRoute } from "@angular/router";

@Secured({
   route: "/auditing/schedule-history",
   label: "Schedule History"
})
@Searchable({
   route: "/auditing/schedule-history",
   title: "Schedule History",
   keywords: ["em.keyword.audit", "em.keyword.schedule", "em.keyword.history"]
})
@ContextHelp({
   route: "/auditing/schedule-history",
   link: "EMViewAudit"
})
@Component({
  selector: "em-audit-schedule-history",
  templateUrl: "./audit-schedule-history.component.html",
  styleUrls: ["./audit-schedule-history.component.scss"]
})
export class AuditScheduleHistoryComponent implements OnInit, OnDestroy {
   tasks: AssetModel[] = [];
   users: string[] = [];
   hosts: string[] = [];
   folders: string[] = [];
   organizationNames: string[] = [];
   organizationFilter: boolean = false;
   form: FormGroup;
   private subscriptions = new Subscription();
   displayedColumns = [
      "objectName", "scheduleUser", "objectType", "modifyTime", "modifyStatus", "modifyType", "message", "server"
   ];
   columnRenderers = [
      { name: "objectName", label: "_#(js:Object Name)", value: (r: ScheduleHistory) => r.objectName },
      { name: "scheduleUser", label: "_#(js:Schedule User)", value: (r: ScheduleHistory) => r.objectUser },
      { name: "objectType", label: "_#(js:Object Type)", value: (r: ScheduleHistory) => r.objectType },
      { name: "modifyTime", label: "_#(js:Modify Time)", value: (r: ScheduleHistory) => AuditTableViewComponent.getDisplayDate(r.modifyTime, r.dateFormat) },
      { name: "modifyStatus", label: "_#(js:Modify Status)", value: (r: ScheduleHistory) => r.modifyStatus },
      { name: "modifyType", label: "_#(js:Modify Type)", value: (r: ScheduleHistory) => r.modifyType },
      { name: "message", label: "_#(js:Message)", value: (r: ScheduleHistory) => r.message },
      { name: "server", label: "_#(js:Server)", value: (r: ScheduleHistory) => r.server },
      { name: "organizationId", label: "_#(js:Organization ID)", value: (r: ScheduleHistory) => r.organizationId }
   ];

   constructor(private http: HttpClient, private activatedRoute: ActivatedRoute,
               private pageTitle: PageHeaderService, private errorService: ErrorHandlerService,
               fb: FormBuilder)
   {
      this.form = fb.group({
         selectedTasks: [[]],
         selectedUsers: [[]],
         selectedHosts: [[]],
         selectedFolders: [[]],
         selectOrganization: [[]],
      });
   }

   ngOnInit(): void {
      this.pageTitle.title = "_#(js:Schedule History)";
   }

   // use arrow function instead of member method to hold the right context (i.e. this)
   fetchParameters = () => {
      return this.http.get<ScheduleHistoryParameters>("../api/em/monitoring/audit/scheduleHistoryParameters")
         .pipe(
            catchError(error => this.errorService.showSnackBar(error, "_#(js:Failed to get query parameters)", () => of({
               tasks: [],
               users: [],
               hosts: [],
               folders: [],
               organizationNames: [],
               organizationFilter: true,
               startTime: 0,
               endTime: 0
            }))),
            tap(params => {
               this.tasks = params.tasks;
               this.users = params.users;
               this.hosts = params.hosts;
               this.folders = params.folders
               this.organizationNames = params.organizationNames;
               this.organizationFilter = params.organizationFilter;
            }));
   };

   // use arrow function instead of member method to hold the right context (i.e. this)
   fetchData = (httpParams: HttpParams, additional: { [key: string]: any; }) => {
      let params = httpParams;
      const selectedTasks: string[] = additional.selectedTasks;

      if(!!selectedTasks && selectedTasks.length > 0) {
         selectedTasks.forEach(t => params = params.append("tasks", t));
      }
      const selectedUsers: string[] = additional.selectedUsers;

      if(!!selectedUsers && selectedUsers.length > 0) {
         selectedUsers.forEach(t => params = params.append("users", t));
      }

      const selectedFolders: string[] = additional.selectedFolders;

      if(!!selectedFolders && selectedFolders.length > 0) {
         selectedFolders.forEach(f => params = params.append("folders", f));
      }

      const selectedHosts: string[] = additional.selectedHosts;

      if(!!selectedHosts && selectedHosts.length > 0) {
         selectedHosts.forEach(h => params = params.append("hosts", h));
      }

      const selectedOrgs: string[] = additional.selectOrganization;

      if(!!selectedOrgs && selectedOrgs.length > 0) {
         selectedOrgs.forEach(o => params = params.append("organizations", o));
      }

      return this.http.get<ScheduleHistoryList>("../api/em/monitoring/audit/scheduleHistory", {params})
         .pipe(catchError(error => this.errorService.showSnackBar(error, "_#(js:Failed to run query)", () => of({
            totalRowCount: 0,
            rows: []
         }))));
   };

   getDisplayedColumns() {
      return this.displayedColumns;
   }

   ngOnDestroy(): void {
      this.subscriptions.unsubscribe();
   }

   clearFolders(evt: any): void {
      if(evt.value != null && evt.value.length > 0) {
         this.form.get("selectedFolders").setValue([]);
      }
   }

   clearTasks(evt: any): void {
      if(evt.value != null && evt.value.length > 0) {
         this.form.get("selectedTasks").setValue([]);
      }
   }
}
