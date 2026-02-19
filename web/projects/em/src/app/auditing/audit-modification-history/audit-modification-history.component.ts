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
import { IdentityInfo } from "../audit-identity-info/identity-info";
import { ResourceType } from "../audit-inactive-resource/resource-type";
import { AuditTableViewComponent } from "../audit-table-view/audit-table-view.component";
import {
   ModificationHistory,
   ModificationHistoryList,
   ModificationHistoryParameters
} from "./modification-history";
import { of, Subscription } from "rxjs";
import { ActivatedRoute } from "@angular/router";
import { Tool } from "../../../../../shared/util/tool";

@Secured({
   route: "/auditing/modification-history",
   label: "Modification History"
})
@Searchable({
   route: "/auditing/modification-history",
   title: "Modification History",
   keywords: ["em.keyword.audit", "em.keyword.modification", "em.keyword.history"]
})
@ContextHelp({
   route: "/auditing/modification-history",
   link: "EMViewAudit"
})
@Component({
  selector: "em-audit-modification-history",
  templateUrl: "./audit-modification-history.component.html",
  styleUrls: ["./audit-modification-history.component.scss"]
})
export class AuditModificationHistoryComponent implements OnInit, OnDestroy {
   users: string[] = [];
   objectTypes: string[] = [];
   hosts: string[] = [];
   organizationNames: string[] = [];
   modifyStatuses: string[] = [];
   form: FormGroup;
   organizationFilter = false;
   private subscriptions = new Subscription();
   private _displayedColumns = [
      "userName", "objectName", "objectType", "modifyType", "modifyTime", "modifyStatus", "message",
      "server"
   ];
   columnRenderers = [
      { name: "userName", label: "_#(js:User Name)", value: (r: ModificationHistory) => r.userName },
      { name: "objectName", label: "_#(js:Object Name)", value: (r: ModificationHistory) => r.objectName },
      { name: "objectType", label: "_#(js:Object Type)", value: (r: ModificationHistory) => r.objectType },
      { name: "modifyTime", label: "_#(js:Modify Time)", value: (r: ModificationHistory) => AuditTableViewComponent.getDisplayDate(r.modifyTime, r.dateFormat) },
      { name: "modifyStatus", label: "_#(js:Modify Status)", value: (r: ModificationHistory) => r.modifyStatus },
      { name: "message", label: "_#(js:Message)", value: (r: ModificationHistory) => r.message },
      { name: "modifyType", label: "_#(js:Modify Type)", value: (r: ModificationHistory) => r.modifyType },
      { name: "server", label: "_#(js:Server)", value: (r: ModificationHistory) => r.server },
      { name: "organizationId", label: "_#(js:Organization ID)", value: (r: IdentityInfo) => r.organizationId }
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
         selectedTypes: [[]],
         selectedHosts: [[]],
         selectedOrganizations: [[]],
         selectedStatuses: [[]],
      });
   }

   ngOnInit(): void {
      this.pageTitle.title = "_#(js:Modification History)";
   }

   // use arrow function instead of member method to hold the right context (i.e. this)
   fetchParameters = () => {
      return this.http.get<ModificationHistoryParameters>("../api/em/monitoring/audit/modificationHistoryParameters")
         .pipe(
            catchError(error => this.errorService.showSnackBar(error, "_#(js:Failed to get query parameters)", () => of({
               users: [],
               objectTypes: [],
               hosts: [],
               organizationFilter: true,
               organizationNames: [],
               modifyStatuses: [],
               startTime: 0,
               endTime: 0
            }))),
            tap(params => {
               this.users = params.users;
               this.objectTypes = params.objectTypes;
               this.hosts = params.hosts;
               this.organizationFilter = params.organizationFilter;
               this.organizationNames = params.organizationNames;
               this.modifyStatuses = params.modifyStatuses;
            }));
   };

   // use arrow function instead of member method to hold the right context (i.e. this)
   fetchData = (httpParams: HttpParams, additional: { [key: string]: any; }) => {
      let params = httpParams;
      const selectedUsers: string[] = additional.selectedUsers;

      if(!!selectedUsers && selectedUsers.length > 0) {
         selectedUsers.forEach(u => params = params.append("users", u));
      }

      const selectedTypes: string[] = additional.selectedTypes;

      if(!!selectedTypes && selectedTypes.length > 0) {
         selectedTypes.forEach(t => params = params.append("types", t));
      }

      const selectedHosts: string[] = additional.selectedHosts;

      if(!!selectedHosts && selectedHosts.length > 0) {
         selectedHosts.forEach(h => params = params.append("hosts", h));
      }

      const selectedOrgs: string[] = additional.selectedOrganizations;

      if(!!selectedOrgs && selectedOrgs.length > 0) {
         selectedOrgs.forEach(h => params = params.append("organizations", h));
      }

      const selectedStatuses: string[] = additional.selectedStatuses;

      if(!!selectedStatuses && selectedStatuses.length > 0) {
         selectedStatuses.forEach(h => params = params.append("modifyStatuses", h));
      }

      return this.http.get<ModificationHistoryList>("../api/em/monitoring/audit/modificationHistory", {params})
         .pipe(catchError(error => this.errorService.showSnackBar(error, "_#(js:Failed to run query)", () => of({
            totalRowCount: 0,
            rows: []
         }))));
   };

   getAssetTypeLabel(value: string): string {
      if(value === "dashboard") {
         return "_#(js:Dashboard)";
      }
      else if(value === "identity") {
         return "_#(js:Identity)";
      }
      else if(value === "permission") {
         return "_#(js:Permission)";
      }
      else if(value === "properties") {
         return "_#(js:Properties)";
      }
      else if(value === "folder") {
         return "_#(js:Folder)";
      }
      else if(value === "task") {
         return "_#(js:Task)";
      }
      else if(value === "cycle") {
         return "_#(js:Cycle)";
      }
      else if(value === "datasource") {
         return "_#(js:Datasource)";
      }
      else if(value === "device") {
         return "_#(js:Device)";
      }
      else if(value === "viewsheet") {
         return "_#(js:Viewsheet)";
      }
      else if(value === "worksheet") {
         return "_#(js:Worksheet)";
      }
      else if(value === "script") {
         return "_#(js:Script)";
      }
      else if(value === "table style") {
         return "_#(js:Table Style)";
      }
      else if(value === "logical model") {
         return "_#(js:Logical Model)";
      }
      else if(value === "physical view") {
         return "_#(js:Physical View)";
      }
      else if(value === "vpm") {
         return "_#(js:VPM)";
      }
      else if(value === "file") {
         return "_#(js:File)";
      }
      else if(value === "shape") {
         return "_#(js:Shape)";
      }
      else if(value === "query") {
         return "_#(js:Query)";
      }
      else if(value === "report") {
         return "_#(js:Report)";
      }
      else if(value === "theme") {
         return "_#(js:Theme)";
      }
      else if(value === "snapshot") {
         return "_#(js:Snapshot)";
      }
      else if(value === "plugin") {
         return "_#(js:Plugin)";
      }
      else if(value === "storage") {
         return "_#(js:Storage)";
      }
      else if(value === "security provider") {
         return "_#(js:Security Provider)";
      }
      else if(value === "password") {
         return "_#(js:Password)";
      }
      else if(value === "asset") {
         return "_#(js:Asset)";
      }
      else if(value === "prototype") {
         return "_#(js:Prototype)";
      }
      else {
         return value;
      }
   }

   getModifyStatusLabel(value: string): string {
      if(value === "success") {
         return "_#(js:Success)";
      }
      else if(value === "failure") {
         return "_#(js:Failure)";
      }
      else {
         return value;
      }
   }

   ngOnDestroy(): void {
      this.subscriptions.unsubscribe();
   }
}
