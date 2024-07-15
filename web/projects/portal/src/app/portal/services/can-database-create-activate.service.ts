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
import { Injectable } from "@angular/core";
import { ActivatedRouteSnapshot, CanActivate, RouterStateSnapshot } from "@angular/router";
import { HttpClient, HttpParams } from "@angular/common/http";
import { Tool } from "../../../../../shared/util/tool";
import { SourcePermissionModel } from "../data/model/source-permission-model";
import { map } from "rxjs/operators";
import { Observable } from "rxjs";

const GET_DATA_FOLDER_PERMISSION_URI = "../api/data/datasources/folderPermission";

@Injectable()
export class CanDatabaseCreateActivateService implements CanActivate {

   constructor(public http: HttpClient) { }

   canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<boolean> {
      let parentPath = route.paramMap.get("parentPath");

      return this.http.get<SourcePermissionModel>(GET_DATA_FOLDER_PERMISSION_URI,
         {params: new HttpParams().set("path", Tool.byteEncode(parentPath))})
         .pipe(map(model => model.writable));
   }
}
