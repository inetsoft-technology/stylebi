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
import { HttpClient } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { ActivatedRouteSnapshot, Resolve, RouterStateSnapshot } from "@angular/router";
import { Observable } from "rxjs";
import { ReportTabModel } from "../report/report-tab-model";
import { ShowHyperlinkService } from "../../vsobjects/show-hyperlink.service";
import { map } from "rxjs/operators";

const REPORT_TAB_MODEL_URI: string = "../api/portal/report-tab-model";

@Injectable()
export class ReportTabResolver implements Resolve<ReportTabModel> {
   constructor(private http: HttpClient) {
   }

   resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<ReportTabModel> {
      const routeQueryParamMap = ShowHyperlinkService.getQueryParams(route.queryParamMap);
      return this.http.get<ReportTabModel>(REPORT_TAB_MODEL_URI)
         .pipe(map(model => {
            if(routeQueryParamMap.get("collapseTree")) {
               model.collapseTree = true;
            }

            return model;
         }));
   }
}
