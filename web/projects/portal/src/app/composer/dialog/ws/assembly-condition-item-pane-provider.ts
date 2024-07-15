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
import { HttpClient, HttpHeaders, HttpParams } from "@angular/common/http";
import { Observable, of as observableOf } from "rxjs";
import { BrowseDataModel } from "../../../common/data/browse-data-model";
import { AssetConditionItemPaneProvider } from "../../../common/data/condition/asset-condition-item-pane-provider";
import { Condition } from "../../../common/data/condition/condition";
import { ConditionOperation } from "../../../common/data/condition/condition-operation";
import { ExpressionValue } from "../../../common/data/condition/expression-value";
import { DataRef } from "../../../common/data/data-ref";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { ColumnRef } from "../../../binding/data/column-ref";
import { XSchema } from "../../../common/data/xschema";
import { Tool } from "../../../../../../shared/util/tool";

export abstract class AssemblyConditionItemPaneProvider extends AssetConditionItemPaneProvider {
   protected _fields: DataRef[];
   protected _variableNames: string[];
   private _grayedOutFields: DataRef[];
   private _scriptDefinitions: any = null;
   private _showOriginalName: boolean = false;

   constructor(protected http: HttpClient, protected runtimeId: string,
               protected assemblyName: string)
   {
      super();
   }

   public set showOriginalName(val: boolean) {
      this._showOriginalName = val;
   }

   public set fields(fields: DataRef[]) {
      this._fields = fields;
   }

   public set variableNames(names: string[]) {
      this._variableNames = names;
   }

   public set grayedOutFields(fields: DataRef[]) {
      this._grayedOutFields = fields;
   }

   public set scriptDefinitions(value: any) {
      this._scriptDefinitions = value;
   }

   getData(condition: Condition): Observable<BrowseDataModel> {
      if(condition.operation == ConditionOperation.DATE_IN) {
         const params = new HttpParams().set("runtimeId", this.runtimeId);

      return this.http.get<BrowseDataModel>(
        "../api/composer/ws/assembly-condition-dialog/date-ranges", {params});
      }
      else {
         const headers = new HttpHeaders({
            "Content-Type": "application/json"
         });
         const params = new HttpParams()
            .set("runtimeId", this.runtimeId)
            .set("assemblyName", this.assemblyName);
         const uri = "../api/composer/ws/assembly-condition-dialog/browse-data";
         return this.http.post<BrowseDataModel>(uri, condition.field, {headers, params});
      }
   }

   getVariables(condition: Condition): Observable<any[]> {
      return observableOf(this._variableNames);
   }

   getColumnTree(value: ExpressionValue, variableNames?: string[]): Observable<TreeNodeModel> {
      if(!!variableNames) {
         this.variableNames = variableNames;
      }

      return observableOf(this.getColumnTreeModel(value));
   }

   public getGrayedOutFields(): DataRef[] {
      return this._grayedOutFields;
   }

   protected getColumnTreeModel(value: ExpressionValue): TreeNodeModel {
      let root: TreeNodeModel = {children: []};

      // fields should be acccessible in JS
      //if(value.type !== ExpressionType.JS) {
      root.children.push(this.getFieldTreeModel());

      let variableTreeModel = this.getVariableTreeModel();

      if(!!variableTreeModel) {
         root.children.push(variableTreeModel);
      }

      if(root.children.length >= 1) {
         root.children[0].expanded = true;
      }
      else {
        return null;
      }

      return root;
   }

   protected getFieldTreeModel(): TreeNodeModel {
      const fieldTreeNodes: TreeNodeModel[] = [];

      for(let field of this._fields) {
         let node: TreeNodeModel = <TreeNodeModel> {
            label: field.name ? field.name : field.view,
            data: field.name ? field.name : field.view,
            icon: "column-icon",
            leaf: true
         };

         if(XSchema.isDateType(field.dataType)) {
            this.addDateParts(node, field);
         }

         if(this._showOriginalName) {
            node.tooltip = ColumnRef.getTooltip(<ColumnRef> field);
         }

         fieldTreeNodes.push(node);
      }

      return <TreeNodeModel> {
         label: "_#(js:Fields)",
         children: fieldTreeNodes,
         icon: "data-table-icon",
         leaf: false
      };
   }

   private addDateParts(node: TreeNodeModel, field: DataRef) {
      node.children = [];
      let nodeData = field.name ? field.name : field.view;
      let levels = Tool.getDateParts(field.dataType);
      let funcs = Tool.getDatePartFuncs(field.dataType);

      for(let i = 0; i < levels.length; i++) {
         let label = levels[i] + "(" + nodeData + ")";
         let data = funcs[i] + "(" + nodeData + ")";

         let child: TreeNodeModel = <TreeNodeModel> {
            label: label,
            data: data,
            icon: "column-icon",
            type: "date_part_column",
            leaf: true
         };

         node.children.push(child);
      }
   }

   protected getVariableTreeModel(): TreeNodeModel {
      const variableTreeNodes: TreeNodeModel[] = [];

      if(!this._variableNames || this._variableNames.length == 0) {
         return null;
      }

      for(let variable of this._variableNames) {
         variableTreeNodes.push(<TreeNodeModel> {
            label: variable,
            data: "parameter." + variable,
            icon: "variable-icon",
            leaf: true,
         });
      }

      return <TreeNodeModel> {
         label: "_#(js:Variables)",
         children: variableTreeNodes,
         leaf: false
      };
   }

   getScriptDefinitions(value: ExpressionValue): Observable<any> {
      return observableOf(this._scriptDefinitions);
   }
}
