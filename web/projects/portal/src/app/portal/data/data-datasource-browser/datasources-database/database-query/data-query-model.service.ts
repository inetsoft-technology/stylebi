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
import { Observable, Subject } from "rxjs";
import { AssetEntry } from "../../../../../../../../shared/data/asset-entry";
import { AssetType } from "../../../../../../../../shared/data/asset-type";
import { GuiTool } from "../../../../../common/util/gui-tool";
import { TreeNodeModel } from "../../../../../widget/tree/tree-node-model";
import { HttpClient, HttpParams } from "@angular/common/http";
import { VariableInfo } from "../../../../../common/data/variable-info";
import {
   VariableInputDialog
} from "../../../../../widget/dialog/variable-input-dialog/variable-input-dialog.component";
import { ComponentTool } from "../../../../../common/util/component-tool";
import {
   VariableInputDialogModel
} from "../../../../../widget/dialog/variable-input-dialog/variable-input-dialog-model";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import {
   UpdateFreeFormSqlPaneEvent
} from "../../../model/datasources/database/query/free-form-sql-pane/update-free-form-sql-pane-event";
import {
   UpdateFreeFormSqlPaneResult
} from "../../../model/datasources/database/query/free-form-sql-pane/update-free-form-sql-pane-result";
import {
   FreeFormSqlPaneModel
} from "../../../model/datasources/database/query/free-form-sql-pane/free-form-sql-pane-model";
import {
   AdvancedSqlQueryModel
} from "../../../model/datasources/database/query/advanced-sql-query-model";

const COLLECT_QUERY_VARIABLES_URL = "../api/data/datasource/query/variables";
const PARSE_SQL_STRING_URL = "../api/data/datasource/query/save/freeSQLModel";
const UPDATE_QUERY_VARIABLES_URL = "../api/data/datasource/query/variables/update";

@Injectable()
export class DataQueryModelService {
   private _modelChange: Subject<() => void> = new Subject<any>();
   private _graphViewChange: Subject<boolean> = new Subject<any>();
   private _unjoinedTables: string[] = [];

   constructor(private http: HttpClient, private modalService: NgbModal) {
   }

   get modelChange(): Observable<() => void> {
      return this._modelChange.asObservable();
   }

   public emitModelChange(callback: () => void = null): void {
      this._modelChange.next(callback);
   }

   get graphViewChange(): Observable<boolean> {
      return this._graphViewChange.asObservable();
   }

   public emitGraphViewChange(): void {
      this._graphViewChange.next();
   }

   getFieldFunctionTree(): TreeNodeModel {
      return {
         children: [
            {
               label: "_#(js:Operator)",
               children: [
                  {
                     label: "+",
                     data: "+",
                     leaf: true,
                     type: "operator"
                  },
                  {
                     label: "-",
                     data: "-",
                     leaf: true,
                     type: "operator"
                  },
                  {
                     label: "*",
                     data: "*",
                     leaf: true,
                     type: "operator"
                  },
                  {
                     label: "/",
                     data: "/",
                     leaf: true,
                     type: "operator"
                  },
               ],
               leaf: false,
               expanded: true
            },
            {
               label: "_#(js:Function)",
               children: [
                  {
                     label: "Sum",
                     data: "Sum",
                     leaf: true,
                     type: "function"
                  },
                  {
                     label: "Avg",
                     data: "Avg",
                     leaf: true,
                     type: "function"
                  },
                  {
                     label: "Count",
                     data: "Count",
                     leaf: true,
                     type: "function"
                  },
                  {
                     label: "Max",
                     data: "Max",
                     leaf: true,
                     type: "function"
                  },
                  {
                     label: "Min",
                     data: "Min",
                     leaf: true,
                     type: "function"
                  }
               ],
               leaf: false,
               expanded: true
            }]
      };
   }

   getVariables(runtimeId: string, nSqlString: string, callback: () => void): void {
      let params = new HttpParams().set("runtimeId", runtimeId);

      if(!!nSqlString) {
         params = params.set("sqlString", nSqlString);
      }

      this.http.get(COLLECT_QUERY_VARIABLES_URL, { params: params })
         .subscribe((vars: VariableInfo[]) => {
            if(!vars || vars.length == 0) {
               if(callback != null) {
                  callback();
               }

               return;
            }

            const onCommit = (model: VariableInputDialogModel) => {
               let variables: VariableInfo[] = model.varInfos;

               if(!variables || variables.length == 0) {
                  if(callback != null) {
                     callback();
                  }
               }
               else {
                  const params = new HttpParams().set("runtimeId", runtimeId);

                  this.http.post(UPDATE_QUERY_VARIABLES_URL, variables, {params: params})
                     .subscribe(() => {
                        if(callback != null) {
                           callback();
                        }
                     });
               }
            };

            const dialog: VariableInputDialog = ComponentTool.showDialog<VariableInputDialog>(
                  this.modalService, VariableInputDialog, onCommit);
            dialog.model = <VariableInputDialogModel>{varInfos: vars};
         });
   }

   parseSql(model: FreeFormSqlPaneModel, runtimeId: string, executeQuery: boolean = false,
            callback: (res: AdvancedSqlQueryModel) => void)
   {
      let event = new UpdateFreeFormSqlPaneEvent(runtimeId, model);
      event.executeQuery = executeQuery;

      this.http.post<UpdateFreeFormSqlPaneResult>(PARSE_SQL_STRING_URL, event)
         .subscribe((res: UpdateFreeFormSqlPaneResult) => {
            if(!!res?.model?.freeFormSQLPaneModel) {
               model = res.model.freeFormSQLPaneModel;
            }

            if(!!callback && !!res.model && (!res.errorMsg || executeQuery)) {
               callback(res.model);
            }

            if(!!res.errorMsg && !executeQuery) {
               ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)", res.errorMsg);
               // execute query to parse sql.
               this.getVariables(runtimeId, model.sqlString,
                     () => this.parseSql(model, runtimeId, true, callback));
            }
         });
   }

   getUnjoinedTables(): string[] {
      return this._unjoinedTables;
   }

   setUnjoinedTables(unjoinedTables: string[]) {
      this._unjoinedTables = unjoinedTables;
   }
}

export enum DatabaseQueryTabs {
   LINKS = "links",
   FIELDS = "fields",
   CONDITIONS = "conditions",
   SORT = "sort",
   GROUPING = "grouping",
   SQL_STRING = "sql-string",
   PREVIEW = "preview"
}

export enum GroupingPaneTabs {
   GROUP_BY = "group-by",
   HAVING = "having"
}

export function getFieldFullName(node: TreeNodeModel, quote: boolean = false) {
   if(!node?.data) {
      return "";
   }

   return getFieldFullNameByEntry(<AssetEntry> node.data, quote)
}

export function getFieldFullNameByEntry(entry: AssetEntry, quote: boolean = false) {
   if(!entry?.properties) {
      return "";
   }

   let properties = entry.properties;
   let source = quote ? properties["quoteTableName"] : properties["source_alias"];
   source = !!source || quote ? source : entry.properties["source_with_no_quote"];
   source = !!source ? source : entry.properties["source"];

   let column = quote ? properties["quoteColumnName"] : properties["attribute"];
   return source ? source + "." + column : column;
}

/**
 * Find the sibling of node.
 */
export function findNextNode(root: TreeNodeModel, node: TreeNodeModel): TreeNodeModel {
   if(!root || !node) {
      return null;
   }

   // check if node is a child of p
   function isChild(p: TreeNodeModel, c: TreeNodeModel): boolean {
      return p.children && p.children.some(n => JSON.stringify(n.data) == JSON.stringify(c.data));
   }

   const parent = GuiTool.findNode(root, n => isChild(n, node));

   if(parent) {
      for(let i = 0; i < parent.children.length; i++) {
         if(JSON.stringify(parent.children[i].data) == JSON.stringify(node.data)) {
            if(i < parent.children.length - 1) {
               return parent.children[i + 1];
            }
         }
      }
   }

   return null;
}

export function findTableChildren(parent: TreeNodeModel, tableEntrys: AssetEntry[]): TreeNodeModel[] {
   const nodes: TreeNodeModel[] = [];

   if(!parent || !parent.children || parent.children.length === 0) {
      return nodes;
   }

   const queue: TreeNodeModel[] = [...parent.children];
   let foundTableLevel = false;

   while(queue.length > 0) {
      const currentNode = queue.shift();

      if(currentNode?.data?.type === AssetType.PHYSICAL_TABLE &&
         tableEntrys.findIndex(entry => entry.identifier === currentNode.data.identifier) != -1)
      {
         nodes.push(...currentNode.children);
         foundTableLevel = true;
      }

      if(!foundTableLevel && currentNode?.children && currentNode.children?.length > 0) {
         queue.push(...currentNode.children);
      }
   }

   return nodes;
}

export function getShiftIndexesRange(shiftStartInde: number, currentIndex: number): number[] {
   const range: number[] = [];

   if(shiftStartInde == -1 || shiftStartInde == currentIndex) {
      range.push(currentIndex);
   }
   else if(shiftStartInde < currentIndex) {
      for(let i = shiftStartInde; i <= currentIndex; i++) {
         range.push(i);
      }
   }
   else {
      for(let i = shiftStartInde; i >= currentIndex; i--) {
         range.push(i);
      }
   }

   return range;
}
