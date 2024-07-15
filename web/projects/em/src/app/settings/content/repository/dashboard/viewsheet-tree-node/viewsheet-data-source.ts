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
import { HttpClient, HttpErrorResponse, HttpParams } from "@angular/common/http";
import { MatSnackBar } from "@angular/material/snack-bar";
import { BehaviorSubject, Observable, of, throwError } from "rxjs";
import { catchError, map } from "rxjs/operators";
import { Tool } from "../../../../../../../../shared/util/tool";
import { FlatTreeDataSource } from "../../../../../common/util/tree/flat-tree-data-source";
import { TreeDataModel } from "../../../../../common/util/tree/flat-tree-model";
import {
   ViewsheetTreeListModel,
   ViewsheetTreeModel
} from "../../../../schedule/model/viewsheet-tree-list-model";
import { ViewsheetFlatNode } from "./viewsheet-flat-node";

export class ViewsheetDataSource extends FlatTreeDataSource<ViewsheetFlatNode, ViewsheetTreeModel> {
   protected viewsheetDataSource = new BehaviorSubject<ViewsheetFlatNode[]>([]);

   constructor(treeControl: FlatTreeControl<ViewsheetFlatNode>,
               private http: HttpClient,
               private snackBar: MatSnackBar,
               public owner: string) {
      super(treeControl);

      this.getFolders().pipe(
            catchError(error => this.handleFoldersError(error)),
            map(model => this.transform(model, 0))
         )
         .subscribe(nodes => {
            this.setData(nodes);
            this.viewsheetDataSource.next(nodes);
         });
   }

   private setData(nodes: ViewsheetFlatNode[]){
      this.data = nodes.sort((a, b) => {
         const aFolder = a.folder ? 1 : 0;
         const bFolder = b.folder ? 1 : 0;
         return bFolder - aFolder;
      });
   }

   protected getChildren(node: ViewsheetFlatNode): Observable<TreeDataModel<ViewsheetTreeModel>> {
      return of({
         nodes: node.data.children || []
      });
   }

   protected transform(model: TreeDataModel<ViewsheetTreeModel>,
                       level: number): ViewsheetFlatNode[] {
      return model.nodes.map(
         (node) => new ViewsheetFlatNode(node.folder, node.id, node.label, level,
            node.folder, false,  node));
   }

   private handleFoldersError(error: HttpErrorResponse): Observable<ViewsheetTreeListModel> {
      this.snackBar.open(error.message, null, {
         duration: Tool.SNACKBAR_DURATION,
      });
      console.error("Failed to list folders: ", error);
      return throwError(error);
   }

   getFolders(): Observable<ViewsheetTreeListModel> {
      return this.http.get<ViewsheetTreeListModel>(
         "../api/em/settings/repository/dashboard/viewsheet/folders");
   }

   getDataChange(): Observable<ViewsheetFlatNode[]> {
      return this.viewsheetDataSource;
   }
}
