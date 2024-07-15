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
import { HttpClient } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { ActivatedRouteSnapshot, Resolve, Router, RouterStateSnapshot } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Observable, throwError } from "rxjs";
import { catchError } from "rxjs/operators";
import { ComponentTool } from "../../common/util/component-tool";
import { DashboardTabModel } from "../dashboard/dashboard-tab-model";

const DASHBOARD_TAB_ERROR_MSG = "_#(js:em.security.permit.dashboard)";

@Injectable()
export class DashboardTabResolver implements Resolve<DashboardTabModel> {
   constructor(private http: HttpClient, private modalService: NgbModal, private router: Router) {
   }

   resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<DashboardTabModel> {
      return this.http.get<DashboardTabModel>("../api/portal/dashboard-tab-model").pipe(
         catchError((error) =>  {
            ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)", DASHBOARD_TAB_ERROR_MSG)
               .then(() => this.router.navigate(["/portal/tab/report"]));
            return throwError(error);
         })
      );
   }
}
