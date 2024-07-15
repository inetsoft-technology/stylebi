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
import { HttpClient, HttpErrorResponse, HttpParams } from "@angular/common/http";
import { Component, Inject, OnInit, ViewEncapsulation } from "@angular/core";
import { UntypedFormBuilder, UntypedFormGroup, Validators } from "@angular/forms";
import { MAT_DIALOG_DATA, MatDialogRef } from "@angular/material/dialog";
import { Observable, of, throwError } from "rxjs";
import { catchError, map } from "rxjs/operators";
import { FormValidators } from "../../../../../../../shared/util/form-validators";
import { FlatTreeNode, TreeDataModel } from "../../../../common/util/tree/flat-tree-model";
import {
   RestoreAssetTreeListModel,
   RestoreAssetTreeModel
} from "./restore-asset-tree-list-model";
import { FlatTreeDataSource } from "../../../../common/util/tree/flat-tree-data-source";
import { FlatTreeControl } from "@angular/cdk/tree";
import { ViewsheetActionService } from "../../../schedule/task-action-pane/viewsheet-action.service";
import { MatSnackBar } from "@angular/material/snack-bar";
import { Tool } from "../../../../../../../shared/util/tool";

export class RestoreAssetFlatNode extends FlatTreeNode<RestoreAssetTreeModel> {
   constructor(public expandable: boolean, public id: string, public name: string,
               public level: number, public data: RestoreAssetTreeModel, public folder: boolean)
   {
      super(name, level, expandable, data, false);
   }
}

export class RestoreDataSource extends FlatTreeDataSource<RestoreAssetFlatNode, RestoreAssetTreeModel> {
   constructor(treeControl: FlatTreeControl<RestoreAssetFlatNode>, private snackBar: MatSnackBar,
               private http: HttpClient, isVS: boolean)
   {
      super(treeControl);

      this.getFolders(isVS)
         .pipe(
            catchError(error => this.handleFoldersError(error)),
            map(model => this.transform(model, 0))
         )
         .subscribe(nodes => this.setData(nodes));
   }

   getFolders(isVS): Observable<RestoreAssetTreeListModel> {
      const params = new HttpParams().set("isvs", isVS + "");

      return this.http.get<RestoreAssetTreeListModel>(RESTORE_AUTO_SAVE_TREE, {params});
   }

   private setData(nodes: RestoreAssetFlatNode[]){
      this.data = nodes.sort((a, b) => {
         const aFolder = a.folder ? 1 : 0;
         const bFolder = b.folder ? 1 : 0;
         return bFolder - aFolder;
      });

      this.treeControl.expandAll();
   }

   protected getChildren(node: RestoreAssetFlatNode): Observable<TreeDataModel<RestoreAssetTreeModel>> {
      return of({nodes: node.data.children});
   }

   protected transform(model: TreeDataModel<RestoreAssetTreeModel>,
                       level: number): RestoreAssetFlatNode[]
   {
      return model.nodes.map( (node) =>
         new RestoreAssetFlatNode(node.folder, node.id, node.label, level, node, node.folder));
   }

   private handleFoldersError(error: HttpErrorResponse): Observable<RestoreAssetTreeListModel> {
      this.snackBar.open(error.message, null, {
         duration: Tool.SNACKBAR_DURATION,
      });
      console.error("Failed to list folders: ", error);
      return throwError(error);
   }
}

const RESTORE_AUTO_SAVE_TREE: string = "../api/em/content/repository/autosave/tree";
const RECOVER_AUTO_SAVE_ENTRY: string = "../api/em/content/repository/autosave/restore";

@Component({
   selector: "em-restore-asset-dialog",
   templateUrl: "./restore-asset-dialog.component.html",
   styleUrls: ["./restore-asset-dialog.component.scss"],
})

export class RestoreAssetDialogComponent implements OnInit {
   form: UntypedFormGroup;
   loading: boolean = true;
   treeControl: FlatTreeControl<RestoreAssetFlatNode>;
   dataSource: RestoreDataSource;
   selectedFolder: string = "/";
   private getLevel = (node: RestoreAssetFlatNode) => node.level;
   private isExpandable = (node: RestoreAssetFlatNode) => node.expandable;
   hasChild = (n: number, nodeData: RestoreAssetFlatNode) => nodeData.expandable;

   constructor(private http: HttpClient, private viewsheetService: ViewsheetActionService,
               private snackBar: MatSnackBar, fb: UntypedFormBuilder,
               private dialogRef: MatDialogRef<RestoreAssetDialogComponent>,
               @Inject(MAT_DIALOG_DATA) private data: any)
   {
      this.treeControl = new FlatTreeControl<RestoreAssetFlatNode>(this.getLevel, this.isExpandable);
      this.dataSource = new RestoreDataSource(this.treeControl, this.snackBar, this.http, this.data.isVS);

      this.form = fb.group({
         fileName: [null, [Validators.required,
            FormValidators.assetEntryBannedCharacters,
            FormValidators.assetNameStartWithCharDigit]],
         overwrite: [true]
      });
   }

   ngOnInit() {
      this.loading = false;
   }

   selectNode(node: RestoreAssetFlatNode) {
      this.selectedFolder = node.id;
   }

   restore() {
      let ids = this.data.ids.join(",");

      const body = {"ids": ids, "name": this.form.get("fileName").value,
         "overwrite": this.form.get("overwrite").value,
         "folder": this.selectedFolder};

      this.http.post(RECOVER_AUTO_SAVE_ENTRY, body)
         .pipe(catchError((error: HttpErrorResponse) => {
            return throwError(error);
         }))
         .subscribe(() => {
            this.dialogRef.close(null);
         });
   }

   cancel() {
      this.dialogRef.close(null);
   }
}
