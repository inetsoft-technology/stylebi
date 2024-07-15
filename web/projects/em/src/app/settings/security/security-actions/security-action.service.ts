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
import { Injectable } from "@angular/core";
import { Observable } from "rxjs";
import { ResourcePermissionModel } from "../resource-permission/resource-permission-model";
import { ActionTreeNode } from "./action-tree-node";

@Injectable({
   providedIn: "root"
})
export class SecurityActionService {
   constructor(private http: HttpClient) {
   }

   getActionTree(): Observable<ActionTreeNode> {
      return this.http.get<ActionTreeNode>("../api/em/security/actions");
   }

   getPermissions(type: string, path: string, isGrant: boolean): Observable<ResourcePermissionModel> {
      path = path != null ? path.replace("*", "%2A") : path;
      const uri = `../api/em/security/actions/${type}/${path}`;
      const params = new HttpParams().set("isGrant", isGrant + "");
      return this.http.get<ResourcePermissionModel>(uri, {params});
   }

   setPermissions(type: string, path: string, isGrant: boolean, permissions: ResourcePermissionModel): Observable<ResourcePermissionModel> {
      path = path != null ? path.replace("*", "%2A") : path;
      const uri = `../api/em/security/actions/${type}/${path}`;
      const params = new HttpParams().set("isGrant", isGrant + "");
      return this.http.post<ResourcePermissionModel>(uri, permissions, {params});
   }
}
