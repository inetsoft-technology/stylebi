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
import { HttpClient, HttpHeaders, HttpParams } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { Observable, Subject, throwError } from "rxjs";
import { catchError } from "rxjs/operators";
import { AssetType } from "../../../../../shared/data/asset-type";
import { CollectParametersOverEvent } from "../../common/event/collect-parameters-over-event";
import { SheetType } from "../../composer/data/sheet";
import { LoadAssetTreeNodesEvent } from "./load-asset-tree-nodes-event";
import { LoadAssetTreeNodesValidator } from "./load-asset-tree-nodes-validator";

@Injectable({
   providedIn: "root"
})
export class AssetTreeService {
   headers: HttpHeaders;
   _loadAssetTreeSubject = new Subject<string>();

   private static URLPrefix: string = "../api/composer/asset_tree";

   constructor(private _http: HttpClient) {
      this.headers = new HttpHeaders({
         "Content-Type": "application/json",
         "X-Requested-With": "XMLHttpRequest"
      });
   }

   get loadAssetTreeSubject() {
      return this._loadAssetTreeSubject;
   }

   /**
    * Query the server for the children of entry.
    * @param event the event to send to the server
    * @param datasources whether or not to retrieve data sources
    * @param columns     whether or not data sources should contain columns as leaves
    * @param worksheets  whether or not to retrieve worksheets
    * @param viewsheets  whether or not to retrieve viewsheets
    * @return an observable containing a TreeNodeModel, the children of the given entry
    */
   public getAssetTreeNode(event: LoadAssetTreeNodesEvent, datasources: boolean,
                           columns: boolean, worksheets: boolean, viewsheets: boolean,
                           tableStyles: boolean, scripts: boolean, library: boolean,
                           reportRepositoryEnabled: boolean = false,
                           readOnly: boolean = false, physical: boolean = true): Observable<LoadAssetTreeNodesValidator>
   {
      if(!event) {
         event = new LoadAssetTreeNodesEvent();
      }

      let params = new HttpParams()
         .set("includeDatasources", datasources + "")
         .set("includeColumns", columns + "")
         .set("includeWorksheets", worksheets + "")
         .set("includeViewsheets", viewsheets + "")
         .set("includeTableStyles", tableStyles + "")
         .set("includeScripts", scripts + "")
         .set("includeLibrary", library + "")
         .set("reportRepositoryEnabled", reportRepositoryEnabled + "")
         .set("readOnly", readOnly + "")
         .set("physical", physical + "");

      const options = {headers: this.headers, params: params};

      return this._http.post<LoadAssetTreeNodesValidator>(AssetTreeService.URLPrefix, event, options).pipe(
         catchError((error) => this.handleError<LoadAssetTreeNodesValidator>(error))
      );
   }

   public setConnectionVariables(event: CollectParametersOverEvent): Observable<any> {
      return this._http.post(AssetTreeService.URLPrefix + "/set-connection-variables", event, {headers: this.headers});
   }

   private handleError<T>(error: any): Observable<T> {
      let errMsg = (error.message) ? error.message :
         error.status ? `${error.status} - ${error.statusText}` : "Server error: could not load asset children";
      console.error(errMsg); // log to console instead
      return throwError(errMsg);
   }

   /**
    * Method for retrieving the "dragname" of an AssetType.
    * This dragname is required for html5 drag functionality
    * @param type AssetType to check the dragName of
    * @return dragname of type if it is draggable, undefined otherwise
    */
   public static getDragName(type: AssetType): string {
      let str: string = type + "";
      return str.toLowerCase();
   }

   /**
    * Return sheet type if asset is openable; i.e. if it is either a worksheet or
    * viewsheet
    */
   public getSheetTypeFromDragName(dragName: string): SheetType {
      let type: SheetType;

      switch(dragName) {
         case AssetTreeService.getDragName(AssetType.WORKSHEET):
            type = "worksheet";
            break;
         case AssetTreeService.getDragName(AssetType.VIEWSHEET):
            type = "viewsheet";
            break;
         default:
            type = undefined;
      }

      return type;
   }
}
