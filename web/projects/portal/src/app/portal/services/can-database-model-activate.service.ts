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
import { Injectable } from "@angular/core";
import { ActivatedRouteSnapshot, CanActivate, RouterStateSnapshot } from "@angular/router";
import { Observable, of } from "rxjs";
import { HttpClient, HttpParams } from "@angular/common/http";
import { Tool } from "../../../../../shared/util/tool";
import { map } from "rxjs/operators";
import { ComponentTool } from "../../common/util/component-tool";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";

const CHECK_DATA_MODEL_EDITABLE_URI = "../api/data/model/checkEditable/";
const CHECK_LOGICAL_MODEL_EDITABLE_URI = "../api/data/logicalmodel/permission/editable";

@Injectable()
export class CanDatabaseModelActivateService implements CanActivate {
   constructor(public http: HttpClient,
               private modalService: NgbModal) { }

   canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<boolean> {
      let routeUrl = state.url;
      let modelPath;
      let logicalModel: boolean = false;
      let databasePath;

      if(/datasources\/database\/[\s\S]+\/physicalModel\/[\s\S]+/.test(routeUrl)) {
         databasePath = Tool.byteDecode(route.params["databasePath"]);
      }
      else if(routeUrl.startsWith("/portal/tab/data/datasources/database/vpm")) {
         modelPath = Tool.byteDecode(route.params["vpmPath"]);
         let idx = !modelPath ? -1 : modelPath.lastIndexOf("/");

         if(idx == -1 || idx >= modelPath.length) {
            return of(false);
         }

         databasePath = modelPath.substring(0, idx);
      }

      if(/datasources\/database\/[\s\S]+\/physicalModel\/[\s\S]+\/logicalModel\/[\s\S]+/.test(routeUrl)){
         databasePath = Tool.byteDecode(route.params["databasePath"]);
         logicalModel = true;
      }

      const folderName = route.params["folder"];
      const parent = route.params["parent"];
      let params: HttpParams = new HttpParams();

      if(!!folderName) {
         params = params.set("folder", folderName);
      }

      if(!!parent) {
         params = params.set("parent", parent);
      }

      if(!logicalModel) {
         return this.http.get<boolean>(CHECK_DATA_MODEL_EDITABLE_URI + databasePath,
            {params}).pipe(map((result) => {
               if(!result) {
                  ComponentTool.showMessageDialog(this.modalService, "Unauthorized",
                     "_#(js:data.databases.noEditPermissionError)");
               }

               return result;
         }));
      }
      else {
         let logicalName = Tool.byteDecode(route.params["logicalModelName"]);
         params = params
            .set("database", databasePath)
            .set("name", logicalName);
         return this.http.get<boolean>(CHECK_LOGICAL_MODEL_EDITABLE_URI,
            { params: params }).pipe();
      }
   }
}
