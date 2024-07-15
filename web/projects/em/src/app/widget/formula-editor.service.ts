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
import { HttpClient, HttpParams } from "@angular/common/http";
import { Observable, throwError } from "rxjs";
import { TreeNodeModel } from "../../../../portal/src/app/widget/tree/tree-node-model";
import { catchError } from "rxjs/operators";

@Injectable()
export class FormulaEditorService {
   constructor(private http: HttpClient) {
   }

   getFunctionTreeNode(vsId: string = null, task: boolean = false): Observable<TreeNodeModel> {
      if(task) {
         return this.http.get<TreeNodeModel>("../api/em/schedule/parameters/formula/function").pipe(
            catchError((error) => this.handleError(error))
         );
      }
      else {
         const params = new HttpParams().set("viewsheet", "true");
         return this.http.get<TreeNodeModel>("../api/vsscriptable/functionTree", {params}).pipe(
            catchError((error) => this.handleError(error))
         );
      }
   }

   getOperationTreeNode(vsId: string = null, task: boolean = false): Observable<TreeNodeModel> {
      if(task) {
         return this.http.get<TreeNodeModel>("../api/em/schedule/parameters/formula/operationTree").pipe(
            catchError((error) => this.handleError(error))
         );
      }
      else {
         return this.http.get<TreeNodeModel>("../api/vsscriptable/operationTree").pipe(
            catchError((error) => this.handleError(error))
         );
      }
   }

   getScriptDefinitions(vsId: string): Observable<any> {
      const uri = "../api/vsscriptable/scriptDefinition";
      let params = new HttpParams().set("vsId", vsId);

      return this.http.get(uri, {params}).pipe(
         catchError((error) => this.handleError(error))
      );
   }

   getTaskScriptDefinitions(): Observable<any> {
      const uri = "../api/em/schedule/parameters/formula/scriptDefinition";

      return this.http.get(uri).pipe(
         catchError((error) => this.handleError(error))
      );
   }

   private handleError<T>(error: any): Observable<T> {
      let errMsg = (error.message) ? error.message :
         error.status ? `${error.status} - ${error.statusText}` : "Server error";
      console.error(errMsg); // log to console instead
      return throwError(errMsg);
   }
}
