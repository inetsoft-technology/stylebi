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
import { inject } from "@angular/core";
import { ActivatedRouteSnapshot, CanActivateFn, RouterStateSnapshot } from "@angular/router";
import { Observable } from "rxjs";
import { map } from "rxjs/operators";
import { Tool } from "../../../../../shared/util/tool";
import { SourcePermissionModel } from "../data/model/source-permission-model";

const GET_DATA_FOLDER_PERMISSION_URI = "../api/data/datasources/folderPermission";

export const canDatabaseCreateActivate: CanActivateFn = (next: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<boolean> => {
   const http = inject(HttpClient);
   const parentPath = next.paramMap.get("parentPath");

   return http.get<SourcePermissionModel>(GET_DATA_FOLDER_PERMISSION_URI,
      {params: new HttpParams().set("path", Tool.byteEncode(parentPath))})
      .pipe(map(model => model.writable));
};
