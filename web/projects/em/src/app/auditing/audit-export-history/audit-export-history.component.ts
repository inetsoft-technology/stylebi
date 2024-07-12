/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
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
import { LogonHistoryList } from "../audit-logon-history/logon-history";
import { AuditTableViewComponent } from "../audit-table-view/audit-table-view.component";
import { ExportHistory, ExportHistoryList, ExportHistoryParameters } from "./export-history";
import { Subscription } from "rxjs";
import { ActivatedRoute } from "@angular/router";

@Secured({
   route: "/auditing/export-history",
   label: "Export History"
})
@Searchable({
   route: "/auditing/export-history",
   title: "Export History",
   keywords: ["em.keyword.audit", "em.keyword.export", "em.keyword.history"]
})
@ContextHelp({
   route: "/auditing/export-history",
   link: "EMAuditingExportHistory"
})
@Component({
   selector: "em-audit-export-history",
   templateUrl: "./audit-export-history.component.html",
   styleUrls: ["./audit-export-history.component.scss"]
})
export class AuditExportHistoryComponent implements OnInit, OnDestroy {
   objectTypes: string[] = [];
   users: string[] = [];
   hosts: string[] = [];
   folders: string[] = [];
   form: FormGroup;
   systemAdministrator = false;
   private subscriptions = new Subscription();
   private _displayedColumns = [
      "userName", "objectType", "objectName", "objectFolder", "exportType", "exportTime", "server"
   ];
   columnRenderers = [
      { name: "objectType", label: "_#(js:Object Type)", value: (r: ExportHistory) => r.objectType },
      { name: "objectFolder", label: "_#(js:Object Folder)", value: (r: ExportHistory) => r.objectFolder },
      { name: "exportTime", label: "_#(js:Export Time)", value: (r: ExportHistory) => AuditTableViewComponent.getDisplayDate(r.exportTime) },
      { name: "server", label: "_#(js:Server)", value: (r: ExportHistory) => r.server },
      { name: "userName", label: "_#(js:User Name)", value: (r: ExportHistory) => r.userName },
      { name: "objectName", label: "_#(js:Object Name)", value: (r: ExportHistory) => r.objectName },
      { name: "exportType", label: "_#(js:Export Type)", value: (r: ExportHistory) => r.exportType },
      { name: "organizationId", label: "_#(js:Organization ID)", value: (r: ExportHistory) => r.organizationId }
   ];

   get displayedColumns(): string[] {
      return this._displayedColumns;
   }

   constructor(private http: HttpClient, private activatedRoute: ActivatedRoute,
               private pageTitle: PageHeaderService, fb: FormBuilder)
   {
      this.form = fb.group({
         selectedTypes: [[]],
         selectedUsers: [[]],
         selectedHosts: [[]],
         selectedFolders: [[]]
      });
   }

   ngOnInit(): void {
      this.pageTitle.title = "_#(js:Export History)";
   }

   // use arrow function instead of member method to hold the right context (i.e. this)
   fetchParameters = () => {
      return this.http.get<ExportHistoryParameters>("../api/em/monitoring/audit/exportHistoryParameters")
         .pipe(tap(params => {
            this.objectTypes = params.objectTypes;
            this.users = params.users;
            this.hosts = params.hosts;
            this.folders = params.folders;
            this.systemAdministrator = params.systemAdministrator;
         }));
   };

   // use arrow function instead of member method to hold the right context (i.e. this)
   fetchData = (httpParams: HttpParams, additional: { [key: string]: any; }) => {
      let params = httpParams;
      const selectedTypes: string[] = additional.selectedTypes;

      if(!!selectedTypes && selectedTypes.length > 0) {
         selectedTypes.forEach(t => params = params.append("types", t));
      }

      const selectedUsers: string[] = additional.selectedUsers;

      if(!!selectedUsers && selectedUsers.length > 0) {
         selectedUsers.forEach(u => params = params.append("users", u));
      }

      const selectedHosts: string[] = additional.selectedHosts;

      if(!!selectedHosts && selectedHosts.length > 0) {
         selectedHosts.forEach(h => params = params.append("hosts", h));
      }

      const selectedFolders: string[] = additional.selectedFolders;

      if(!!selectedFolders && selectedFolders.length > 0) {
         selectedFolders.forEach(f => params = params.append("folders", f));
      }

      return this.http.get<ExportHistoryList>("../api/em/monitoring/audit/exportHistory", {params});
   };

   ngOnDestroy(): void {
      this.subscriptions.unsubscribe();
   }
}
