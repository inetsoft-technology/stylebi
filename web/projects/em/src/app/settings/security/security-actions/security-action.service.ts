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
import { Injectable } from "@angular/core";
import { MatDialog, MatDialogConfig } from "@angular/material/dialog";
import { Observable, of } from "rxjs";
import { catchError } from "rxjs/operators";
import { MessageDialog, MessageDialogType } from "../../../common/util/message-dialog";
import { ResourcePermissionModel } from "../resource-permission/resource-permission-model";
import { ActionTreeNode } from "./action-tree-node";

@Injectable({
   providedIn: "root"
})
export class SecurityActionService {
   emptyModel: ResourcePermissionModel = {
      displayActions: [],
      hasOrgEdited: false,
      securityEnabled: false,
      requiresBoth: false,
      permissions: [],
      derivePermissionLabel: "",
      grantReadToAllVisible: false,
      changed: false
   };

   constructor(private http: HttpClient, private dialog: MatDialog) {
   }

   getActionTree(): Observable<ActionTreeNode> {
      return this.http.get<ActionTreeNode>("../api/em/security/actions");
   }

   getPermissions(type: string, path: string, isGrant: boolean): Observable<ResourcePermissionModel> {
      path = path != null ? path.replace("*", "%2A") : path;
      const uri = `../api/em/security/actions/${type}/${path}`;
      const params = new HttpParams().set("isGrant", isGrant + "");
      return this.http.get<ResourcePermissionModel>(uri, { params }).pipe(
         catchError(error => {
            const orgInvalid = error.error.type === "InvalidOrgException";
            const errContent: string = orgInvalid ? error.error.message :
                                       "_#(js:em.security.orgAdmin.identityPermissionDenied)";

            this.dialog.open(MessageDialog, <MatDialogConfig>{
               width: "350px",
               data: {
                  title: "_#(js:Error)",
                  content: errContent,
                  type: MessageDialogType.ERROR
               }
            });

            return of(this.emptyModel);
         })
      );
   }

   setPermissions(type: string, path: string, isGrant: boolean, permissions: ResourcePermissionModel): Observable<ResourcePermissionModel> {
      path = path != null ? path.replace("*", "%2A") : path;
      const uri = `../api/em/security/actions/${type}/${path}`;
      const params = new HttpParams().set("isGrant", isGrant + "");
      return this.http.post<ResourcePermissionModel>(uri, permissions, {params})
         .pipe(
            catchError(error => {
               const orgInvalid = error.error.type === "InvalidOrgException";

               const errContent: string = orgInvalid ? error.error.message :
                                          "_#(js:em.security.orgAdmin.identityPermissionDenied)";

               this.dialog.open(MessageDialog, <MatDialogConfig>{
                  width: "350px",
                  data: {
                     title: "_#(js:Error)",
                     content: errContent,
                     type: MessageDialogType.ERROR
                  }
               });

               return of(this.emptyModel);
            })
         );
   }
}
