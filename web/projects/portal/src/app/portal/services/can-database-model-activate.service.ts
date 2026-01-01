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
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Observable, of } from "rxjs";
import { map } from "rxjs/operators";
import { ComponentTool } from "../../common/util/component-tool";

const CHECK_DATA_MODEL_EDITABLE_URI = "../api/data/model/checkEditable/";
const CHECK_LOGICAL_MODEL_EDITABLE_URI = "../api/data/logicalmodel/permission/editable";

export const canDatabaseModelActivate: CanActivateFn = (next: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<boolean> => {
   const http = inject(HttpClient);
   const modalService = inject(NgbModal);
   const routeUrl = state.url;
   let modelPath;
   let logicalModel = false;
   let databasePath;

   if(/datasources\/database\/[\s\S]+\/physicalModel\/[\s\S]+/.test(routeUrl)) {
      databasePath = next.params["databasePath"];
   }
   else if(routeUrl.startsWith("/portal/tab/data/datasources/database/vpm")) {
      modelPath = next.params["vpmPath"];
      let idx = !modelPath ? -1 : modelPath.lastIndexOf("/");

      if(idx == -1 || idx >= modelPath.length) {
         return of(false);
      }

      databasePath = modelPath.substring(0, idx);
   }

   if(/datasources\/database\/[\s\S]+\/physicalModel\/[\s\S]+\/logicalModel\/[\s\S]+/.test(routeUrl)){
      databasePath = next.params["databasePath"];
      logicalModel = true;
   }

   const folderName = next.params["folder"];
   const parent = next.params["parent"];
   let params = new HttpParams();

   if(!!folderName) {
      params = params.set("folder", folderName);
   }

   if(!!parent) {
      params = params.set("parent", parent);
   }

   if(!logicalModel) {
      return http.get<boolean>(CHECK_DATA_MODEL_EDITABLE_URI + databasePath,
         {params}).pipe(map((result) => {
         if(!result) {
            ComponentTool.showMessageDialog(modalService, "Unauthorized",
               "_#(js:data.databases.noEditPermissionError)");
         }

         return result;
      }));
   }
   else {
      let logicalName = next.params["logicalModelName"];
      params = params
         .set("database", databasePath)
         .set("name", logicalName);
      return http.get<boolean>(CHECK_LOGICAL_MODEL_EDITABLE_URI,
         { params: params }).pipe();
   }
};