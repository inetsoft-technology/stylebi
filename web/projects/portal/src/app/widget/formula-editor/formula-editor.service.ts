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
import { Observable, throwError } from "rxjs";
import { catchError } from "rxjs/operators";
import { TreeNodeModel } from "../tree/tree-node-model";
import { ScriptTreeNodeData } from "./script-tree-node-data";

@Injectable({
   providedIn: "root"
})
export class FormulaEditorService {
   constructor(private http: HttpClient) {
   }

   public static get returnTypes(): {label: string, data: string}[] {
      return [
         {label: "_#(js:String)", data: "string"},
         {label: "_#(js:Boolean)", data: "boolean"},
         {label: "_#(js:Float)", data: "float"},
         {label: "_#(js:Double)", data: "double"},
         {label: "_#(js:Character)", data: "character"},
         {label: "_#(js:Byte)", data: "byte"},
         {label: "_#(js:Short)", data: "short"},
         {label: "_#(js:Integer)", data: "integer"},
         {label: "_#(js:Long)", data: "long"},
         {label: "_#(js:TimeInstant)", data: "timeInstant"},
         {label: "_#(js:Date)", data: "date"},
         {label: "_#(js:Time)", data: "time"}
      ];
   }

   public static get sqlFunctions(): {label: string, data: string}[] {
      return [
         {label: "_#(js:Sum)", data: "SUM()"},
         {label: "_#(js:Count)", data: "COUNT()"},
         {label: "_#(js:Average)", data: "AVG()"},
         {label: "_#(js:Minimum)", data: "MIN()"},
         {label: "_#(js:Maximum)", data: "MAX()"}
      ];
   }

   public static get sqlOperators(): {label: string, data: string}[] {
      return [
         {label: "+", data: "+"},
         {label: "-", data: "-"},
         {label: "*", data: "*"},
         {label: "/", data: "/"},
         {label: "<", data: "<"},
         {label: ">", data: ">"},
         {label: "<=", data: "<="},
         {label: ">=", data: ">="},
         {label: "=", data: "="},
         {label: "<>", data: "<>"}
      ];
   }

   getFunctionTreeNode(vsId: string = null, task: boolean = false): Observable<TreeNodeModel> {
      if(task) {
         return this.http.get<TreeNodeModel>("../api/portal/schedule/parameters/formula/function").pipe(
            catchError((error) => this.handleError(error))
         );
      }
      else if(vsId) {
         const params = new HttpParams().set("viewsheet", "true");
         return this.http.get<TreeNodeModel>("../api/vsscriptable/functionTree", {params}).pipe(
            catchError((error) => this.handleError(error))
         );
      }
      else {
         return this.http.get<TreeNodeModel>("../api/ws/formula/function").pipe(
            catchError((error) => this.handleError(error))
         );
      }
   }

   getOperationTreeNode(vsId: string = null, task: boolean = false): Observable<TreeNodeModel> {
      if(task) {
         return this.http.get<TreeNodeModel>("../api/portal/schedule/parameters/formula/operationTree").pipe(
            catchError((error) => this.handleError(error))
         );
      }
      else if(vsId) {
         return this.http.get<TreeNodeModel>("../api/vsscriptable/operationTree").pipe(
            catchError((error) => this.handleError(error))
         );
      }
      else {
         return this.http.get<TreeNodeModel>("../api/ws/formula/operation").pipe(
            catchError((error) => this.handleError(error))
         );
      }
   }

   getColumnTreeNode(vsId: string, assemblyName: string, isCondition?: boolean):
      Observable<TreeNodeModel>
   {
      const uri = "../api/vsscriptable/columnTree";
      let params = new HttpParams().set("vsId", vsId);

      if(assemblyName) {
         params = params.set("assemblyName", assemblyName);
      }

      if(isCondition) {
         params = params.set("isCondition", "true");
      }

      return this.http.get<TreeNodeModel>(uri, {params}).pipe(
         catchError((error) => this.handleError(error))
      );
   }

   getGlobalClassesTree(): Observable<TreeNodeModel> {
      return this.http.get<TreeNodeModel>("../api/vsscriptable/globalClassesTree").pipe(
         catchError((error) => this.handleError(error))
      );
   }

   getTaskScriptDefinitions(): Observable<any> {
      const uri = "../api/portal/schedule/parameters/formula/scriptDefinition";

      return this.http.get(uri).pipe(
         catchError((error) => this.handleError(error))
      );
   }

   getScriptDefinitions(vsId: string, assemblyName: string, isCondition?: boolean): Observable<any> {
      const uri = "../api/vsscriptable/scriptDefinition";
      let params = new HttpParams().set("vsId", vsId);

      if(assemblyName) {
         params = params.set("assemblyName", assemblyName);
      }

      if(isCondition) {
         params = params.set("isCondition", "true");
      }

      return this.http.get(uri, {params}).pipe(
         catchError((error) => this.handleError(error))
      );
   }

   private handleError<T>(error: any): Observable<T> {
      let errMsg = (error.message) ? error.message :
         error.status ? `${error.status} - ${error.statusText}` : "Server error";
      console.error(errMsg); // log to console instead
      return throwError(errMsg);
   }

   /**
    * Refine the dataProvider for the columnTree.
    */
   //fix for bug1351481747666
   private refineData(root: TreeNodeModel): void {
      let arrayNames: string[] = ["axis", "valueFormats", "colorLegends",
         "shapeLegends", "sizeLegends"];

      for(let name of arrayNames) {
         let idx: number = this.getNodeIndex(name, root.children);

         if(idx != -1) {
            root.children.splice(idx, 1);
         }
      }
   }

   private getNodeIndex(name: string, children: TreeNodeModel[]): number {
      for(let i = 0; children && i < children.length; i++) {
         const child: TreeNodeModel  = <TreeNodeModel> children[i];
         const data: ScriptTreeNodeData = <ScriptTreeNodeData> child.data;

         if(data.name === name) {
            return i;
         }
      }

      return -1;
   }
}
