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
import {
   InactiveResource,
   InactiveResourceList,
   InactiveResourceParameters
} from "./inactive-resource";
import { ActivatedRoute } from "@angular/router";
import { Subscription } from "rxjs";
import { Tool } from "../../../../../shared/util/tool";

@Secured({
   route: "/auditing/inactive-resource",
   label: "Inactive Resources"
})
@Searchable({
   route: "/auditing/inactive-resource",
   title: "Inactive Resources",
   keywords: ["em.keyword.audit", "em.keyword.inactive", "em.keyword.resource"]
})
@ContextHelp({
   route: "/auditing/inactive-resource",
   link: "EMAuditingInactiveResource"
})
@Component({
   selector: "em-audit-inactive-resource",
   templateUrl: "./audit-inactive-resource.component.html",
   styleUrls: ["./audit-inactive-resource.component.scss"]
})
export class AuditInactiveResourceComponent implements OnInit, OnDestroy {
   objectTypes: string[] = [];
   hosts: string[] = [];
   organizations: string[] = [];
   organizationFilter: boolean = false;
   minDuration = 0;
   maxDuration = 30;
   form: FormGroup;
   isSiteAdmin: boolean = false;
   orgNames: string[] = [];
   private subscriptions = new Subscription();
   displayedColumns = [ "objectName", "objectType", "lastAccessTime", "duration", "server"];
   columnRenderers = [
      { name: "objectName", label: "_#(js:Object Name)", value: (r: InactiveResource) => r.objectName },
      { name: "objectType", label: "_#(js:Object Type)", value: (r: InactiveResource) => r.objectType },
      { name: "lastAccessTime", label: "_#(js:Last Access Time)", value: (r: InactiveResource) => AuditTableViewComponent.getDisplayDate(r.lastAccessTime) },
      { name: "duration", label: "_#(js:Duration)", value: (r: InactiveResource) => r.duration },
      { name: "server", label: "_#(js:Server)", value: (r: InactiveResource) => r.server },
      { name: "organizationId", label: "_#(js:Organization ID)", value: (r: InactiveResource) => r.organizationId }
   ];
   get minStartDuration(): number {
      return this.minDuration;
   }
   get maxStartDuration(): number {
      return this.form.get("maxDuration").value || 0;
   }

   get minEndDuration(): number {
      return this.form.get("minDuration").value || 0;
   }

   get maxEndDuration(): number {
      return this.maxDuration;
   }

   constructor(private http: HttpClient, private activatedRoute: ActivatedRoute,
               private pageTitle: PageHeaderService, fb: FormBuilder)
   {
      this.form = fb.group({
         selectedTypes: [[]],
         selectedHosts: [[]],
         minDuration: [0],
         maxDuration: [30],
         selectedOrganizations: [[]]
      });
   }

   ngOnInit(): void {
      this.pageTitle.title = "_#(js:Inactive Resources)";
      this.http.get<string[]>("../api/em/security/users/get-all-organizations/").subscribe(
         (orgList => this.orgNames = orgList)
      );
      this.http.get<boolean>("../api/em/navbar/isSiteAdmin").subscribe(
         (isAdmin => this.isSiteAdmin = isAdmin)
      );
   }

   // use arrow function instead of member method to hold the right context (i.e. this)
   fetchParameters = () => {
      return this.http.get<InactiveResourceParameters>("../api/em/monitoring/audit/inactiveResourceParameters")
         .pipe(tap(params => {
            this.objectTypes = params.objectTypes;
            this.hosts = params.hosts;
            this.organizations = params.organizations;
            this.organizationFilter = params.organizationFilter;
            this.form.get("minDuration").setValue(params.minDuration, {emitEvent: false});
            this.form.get("maxDuration").setValue(params.maxDuration, {emitEvent: false});
         }));
   };

   // use arrow function instead of member method to hold the right context (i.e. this)
   fetchData = (httpParams: HttpParams, additional: { [key: string]: any; }) => {
      let params = httpParams;
      const selectedTypes: string[] = additional.selectedTypes;

      if(!!selectedTypes && selectedTypes.length > 0) {
         selectedTypes.forEach(t => params = params.append("types", t));
      }
      const selectedHosts: string[] = additional.selectedHosts;

      if(!!selectedHosts && selectedHosts.length > 0) {
         selectedHosts.forEach(h => params = params.append("hosts", h));
      }

      let duration: number = additional.minDuration;

      if(!!duration && duration) {
         params = params.set("minDuration", duration);
      }

      duration = additional.maxDuration;

      if(!!duration && duration) {
         params = params.set("maxDuration", duration);
      }

      const selectedOrgs: string[] = additional.selectedOrganizations;

      if(!!selectedOrgs && selectedOrgs.length > 0) {
         selectedOrgs.forEach(h => params = params.append("organizations", h));
      }

      return this.http.get<InactiveResourceList>("../api/em/monitoring/audit/inactiveResource", {params});
   };

   getDisplayedColumns() {
      return this.displayedColumns;
   }

   getAssetTypeLabel(value: string): string {
      return Tool.startCase(value);
   }

   ngOnDestroy(): void {
      this.subscriptions.unsubscribe();
   }
}
