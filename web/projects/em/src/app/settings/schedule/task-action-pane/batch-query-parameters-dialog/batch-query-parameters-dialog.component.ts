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
import { FlatTreeControl } from "@angular/cdk/tree";
import { HttpClient, HttpErrorResponse } from "@angular/common/http";
import { Component, Inject, OnInit } from "@angular/core";
import { MAT_DIALOG_DATA, MatDialogRef } from "@angular/material/dialog";
import { MatSnackBar } from "@angular/material/snack-bar";
import { Observable, of, throwError } from "rxjs";
import { catchError, map, tap } from "rxjs/operators";
import { QueryColumnsModel } from "../../../../../../../portal/src/app/widget/email-dialog/query-columns-model";
import { AssetEntry } from "../../../../../../../shared/data/asset-entry";
import { AssetType } from "../../../../../../../shared/data/asset-type";
import { AddParameterDialogModel } from "../../../../../../../shared/schedule/model/add-parameter-dialog-model";
import { Tool } from "../../../../../../../shared/util/tool";
import { FlatTreeDataSource } from "../../../../common/util/tree/flat-tree-data-source";
import { FlatTreeNode, TreeDataModel } from "../../../../common/util/tree/flat-tree-model";
import {
   LabeledAssetEntries,
   LabeledAssetEntryModel
} from "../../model/labeled-asset-entry-list-model";
import { ValueTypes } from "../../../../../../../portal/src/app/vsobjects/model/dynamic-value-model";

const BATCH_QUERY_TREE = "../api/em/schedule/batch-action/query-tree";
const BATCH_QUERY_COLUMNS = "../api/em/schedule/batch-action/query-columns";

export interface BatchQueryParametersDialogResult {
   queryEntry: AssetEntry;
   queryParameters: AddParameterDialogModel[];
}

export class BatchAssetFlatNode extends FlatTreeNode<LabeledAssetEntryModel> {
   constructor(public expandable: boolean, public entry: AssetEntry, public name: string,
               public level: number, public data: LabeledAssetEntryModel,
               public loading: boolean = false)
   {
      super(name, level, expandable, data, loading);
   }
}

export class BatchAssetDataSource extends FlatTreeDataSource<BatchAssetFlatNode, LabeledAssetEntryModel> {
   constructor(treeControl: FlatTreeControl<BatchAssetFlatNode>)
   {
      super(treeControl);

   }

   protected getChildren(node: BatchAssetFlatNode): Observable<TreeDataModel<LabeledAssetEntryModel>> {
      return of({
         nodes: node.data.children
      });
   }

   transform(model: TreeDataModel<LabeledAssetEntryModel>, level: number): BatchAssetFlatNode[] {
      if(model.nodes) {
         return model.nodes.map((entryModel: LabeledAssetEntryModel) => {
            const entry = entryModel.entry;
            const expandable = entryModel.children && entryModel.children.length > 0;
            return new BatchAssetFlatNode(expandable, entry, entryModel.label, level, entryModel);
         });
      }

      return [];
   }

   public isEntrySelectable(entry: AssetEntry): boolean {
      return entry.type === AssetType.TABLE || entry.type === AssetType.WORKSHEET;
   }
}

@Component({
   selector: "em-batch-query-parameters-dialog",
   templateUrl: "./batch-query-parameters-dialog.component.html",
   styleUrls: ["./batch-query-parameters-dialog.component.scss"]
})
export class BatchQueryParametersDialogComponent implements OnInit {
   treeControl: FlatTreeControl<BatchAssetFlatNode>;
   treeDataSource: BatchAssetDataSource;

   queryEntry: AssetEntry;
   queryColumns: QueryColumnsModel;
   queryParameters: AddParameterDialogModel[];
   parameterNames: string[];

   constructor(private dialogRef: MatDialogRef<BatchQueryParametersDialogComponent>,
               @Inject(MAT_DIALOG_DATA) public data: any,
               private http: HttpClient, private snackBar: MatSnackBar)
   {
      this.queryEntry = data.queryEntry;
      this.queryParameters = data.queryParameters;
      this.parameterNames = data.parameterNames;
      this.treeControl = new FlatTreeControl<BatchAssetFlatNode>(this.getLevel, this.isExpandable);
      this.treeDataSource = new BatchAssetDataSource(this.treeControl);
      this.selectedEntryChanged(true);
   }

   ngOnInit() {
      this.http.get<LabeledAssetEntries>(BATCH_QUERY_TREE)
         .pipe(
            catchError(error => this.handleQueriesError(error)),
            map((entries) => ({nodes: entries.entries})),
            map((model) => this.treeDataSource.transform(model, 0)),
            tap(nodes => this.treeDataSource.data = nodes)
         )
         .subscribe(() => this.initializeSelections());
   }

   private getLevel = (node: BatchAssetFlatNode) => node.level;
   private isExpandable = (node: BatchAssetFlatNode) => node.expandable;
   hasChild = (n: number, nodeData: BatchAssetFlatNode) => nodeData.expandable;

   isSelectable(entry: AssetEntry): boolean {
      return this.treeDataSource.isEntrySelectable(entry);
   }

   selectedEntryChanged(init: boolean = false) {
      if(this.queryEntry != null) {
         this.http.post<QueryColumnsModel>(BATCH_QUERY_COLUMNS, this.queryEntry)
            .pipe(
               catchError(error => this.handleColumnsError(error)),
            )
            .subscribe((model) => {
               this.queryColumns = model;

               if(!init) {
                  this.queryParameters = [];
               }

               // select the columns with names matching the parameter names
               for(let param of this.parameterNames) {
                  if(this.queryColumns.columns.indexOf(param) >= 0) {
                     this.setParamValue(param, param);
                  }
               }
            });
      }
      else {
         this.queryColumns = null;
      }
   }

   private initializeSelections(): void {
      if(this.queryEntry != null) {
         let numNodes = 0;

         // expandALl expands one level at a time so keep calling it until no more nodes are added
         while(numNodes < this.treeControl.dataNodes.length) {
            numNodes = this.treeControl.dataNodes.length;
            this.treeControl.expandAll();
         }

         let queryNode = this.treeControl.dataNodes.find((node) => !!node.entry &&
            node.entry.identifier == this.queryEntry.identifier);

         if(queryNode) {
            let parents = [];
            this.getParents(queryNode, parents);
            this.treeControl.collapseAll();
            parents = parents.reverse();
            parents.forEach((node) => {
               // after collapsing the tree that particular instance of the parent node may not
               // exist anymore so need to find the new node instance
               const treeNode = this.treeControl.dataNodes.find((n) => !!n.entry &&
                  n.entry.identifier == node.entry.identifier);
               this.treeControl.expand(treeNode);
            });
         }
      }
   }

   /**
    * Recursively get all parents of the passed node.
    */
   private getParents(node: BatchAssetFlatNode, parents: BatchAssetFlatNode[]) {
      const parent = this.getParent(node);

      if(parent) {
         parents.push(parent);
      }

      if(parent && parent.level > 0) {
         this.getParents(parent, parents);
      }
   }

   /**
    * Iterate over each node in reverse order and return the first node that
    * has a lower level than the passed node.
    */
   private getParent(node: BatchAssetFlatNode) {
      const currentLevel = this.treeControl.getLevel(node);

      if(currentLevel < 1) {
         return null;
      }

      const startIndex = this.treeControl.dataNodes.indexOf(node) - 1;

      for(let i = startIndex; i >= 0; i--) {
         const currentNode = this.treeControl.dataNodes[i];

         if(this.treeControl.getLevel(currentNode) < currentLevel) {
            return currentNode;
         }
      }

      return null;
   }

   setParamValue(name: string, value: any) {
      if(this.queryParameters == null) {
         this.queryParameters = [];
      }

      const index = this.queryParameters.findIndex((p) => p.name === name);

      if(index >= 0) {
         if(value == null) {
            this.queryParameters.splice(index, 1);
         }
         else {
            this.queryParameters[index].value = {
               value: value,
               type: ValueTypes.VALUE
            };
         }

         return;
      }
      else if(value == null) {
         return;
      }

      this.queryParameters.push(<AddParameterDialogModel>{
         name: name,
         value: {
            value: value,
            type: ValueTypes.VALUE
         }
      });
   }

   getParamValue(name: string): any {
      if(!this.queryParameters) {
         return null;
      }

      let param = this.queryParameters.find((p) => p.name === name);

      if(param != null) {
         return param.value.value;
      }

      return null;
   }

   getIcon(node: BatchAssetFlatNode) {
      if(node.entry) {
         if(node.entry.type === AssetType.TABLE) {
            return "table-icon";
         }
         else if(node.entry.type === AssetType.WORKSHEET) {
            return "worksheet-icon";
         }
      }

      return "folder-icon";
   }

   isValid(): boolean {
      return this.queryEntry && this.queryParameters && this.queryParameters.length > 0;
   }

   queryEqual(a: AssetEntry, b: AssetEntry): boolean {
      return a && b && a.identifier === b.identifier;
   }

   clearAll() {
      this.queryParameters = [];
      this.queryEntry = null;
      this.queryColumns = null;
   }

   ok() {
      let result: BatchQueryParametersDialogResult = {
         queryEntry: this.queryEntry,
         queryParameters: this.queryParameters
      };

      this.dialogRef.close(result);
   }

   cancel() {
      this.dialogRef.close();
   }

   private handleQueriesError(error: HttpErrorResponse): Observable<LabeledAssetEntries> {
      this.snackBar.open("_#(js:em.schedule.burst.listQueriesError))", null, {
         duration: Tool.SNACKBAR_DURATION,
      });
      console.error("Failed to list queries: ", error);
      return throwError(error);
   }

   private handleColumnsError(error: HttpErrorResponse): Observable<QueryColumnsModel> {
      this.snackBar.open("_#(js:em.schedule.burst.listColumnsError))", null, {
         duration: Tool.SNACKBAR_DURATION,
      });
      console.error("Failed to list query columns: ", error);
      return throwError(error);
   }
}
