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
import { InactiveUser, InactiveUserList, InactiveUserParameters } from "./inactive-user";
import { Subscription } from "rxjs";
import { ActivatedRoute } from "@angular/router";

@Secured({
   route: "/auditing/inactive-user",
   label: "Inactive Users"
})
@Searchable({
   route: "/auditing/inactive-user",
   title: "Inactive Users",
   keywords: ["em.keyword.audit", "em.keyword.inactive", "em.keyword.user"]
})
@ContextHelp({
   route: "/auditing/inactive-user",
   link: "EMAuditingInactiveUsers"
})
@Component({
   selector: "em-audit-inactive-user",
   templateUrl: "./audit-inactive-user.component.html",
   styleUrls: ["./audit-inactive-user.component.scss"]
})
export class AuditInactiveUserComponent implements OnInit, OnDestroy {
   hosts: string[] = [];
   minDuration = 0;
   maxDuration = 30;
   form: FormGroup;
   private subscriptions = new Subscription();
   displayedColumns = [ "userName", "lastAccessTime", "duration", "server" ];
   columnRenderers = [
      { name: "userName", label: "_#(js:User Name)", value: (r: InactiveUser) => r.userID.name },
      { name: "lastAccessTime", label: "_#(js:Last Access Time)", value: (r: InactiveUser) => AuditTableViewComponent.getDisplayDate(r.lastAccessTime) },
      { name: "duration", label: "_#(js:Duration)", value: (r: InactiveUser) => r.duration },
      { name: "server", label: "_#(js:Server)", value: (r: InactiveUser) => r.server },
      { name: "organizationId", label: "_#(js:Organization ID)", value: (r: InactiveUser) => r.organizationId }
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

   constructor(private http: HttpClient, private activatedRoute: ActivatedRoute,
               private pageTitle: PageHeaderService, fb: FormBuilder)
   {
      this.form = fb.group({
         selectedHosts: [[]],
         minDuration: [0],
         maxDuration: [30]
      });
   }

   ngOnInit(): void {
      this.pageTitle.title = "_#(js:Inactive Users)";
   }

   // use arrow function instead of member method to hold the right context (i.e. this)
   fetchParameters = () => {
      return this.http.get<InactiveUserParameters>("../api/em/monitoring/audit/inactiveUserParameters")
         .pipe(tap(params => {
            this.hosts = params.hosts;
            this.minDuration = params.minDuration;
            this.maxDuration = params.maxDuration;
            this.form.get("minDuration").setValue(this.minDuration, {emitEvent: false});
            this.form.get("maxDuration").setValue(this.maxDuration, {emitEvent: false});
         }));
   };

   // use arrow function instead of member method to hold the right context (i.e. this)
   fetchData = (httpParams: HttpParams, additional: { [key: string]: any; }) => {
      let params = httpParams;
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

      return this.http.get<InactiveUserList>("../api/em/monitoring/audit/inactiveUsers", {params});
   };

   getDisplayedColumns() {
      return this.displayedColumns;
   }

   ngOnDestroy(): void {
      this.subscriptions.unsubscribe();
   }
}
