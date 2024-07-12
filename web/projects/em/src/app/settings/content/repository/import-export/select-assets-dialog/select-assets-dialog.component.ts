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
import { HttpClient } from "@angular/common/http";
import { Component, HostListener, Inject, OnInit } from "@angular/core";
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from "@angular/material/dialog";
import { CommonKVModel } from "../../../../../../../../portal/src/app/common/data/common-kv-model";
import { RepositoryEntryType } from "../../../../../../../../shared/data/repository-entry-type.enum";
import { Tool } from "../../../../../../../../shared/util/tool";
import { MessageDialog, MessageDialogType } from "../../../../../common/util/message-dialog";
import { FlatTreeNode } from "../../../../../common/util/tree/flat-tree-model";
import { RepositoryTreeDataSource } from "../../repository-tree-data-source";
import { RepositoryFlatNode, RepositoryTreeNode } from "../../repository-tree-node";
import { SelectedAssetModel } from "../selected-asset-model";
import { ExportAssetsService } from "../export-assets.service";

export interface SelectAssetsDialogData {
   selectedAssets: SelectedAssetModel[];
}

@Component({
   selector: "em-select-assets-dialog",
   templateUrl: "./select-assets-dialog.component.html",
   styleUrls: ["./select-assets-dialog.component.scss"],
   providers: [RepositoryTreeDataSource]
})
export class SelectAssetsDialogComponent implements OnInit {
   selectedNodes: FlatTreeNode<RepositoryTreeNode>[] = [];
   loading = false;
   private selectedAssets: SelectedAssetModel[] = [];

   constructor(public dataSource: RepositoryTreeDataSource, private http: HttpClient,
               private dialog: MatDialog,
               private dialogRef: MatDialogRef<SelectAssetsDialogComponent>,
               private service: ExportAssetsService,
               @Inject(MAT_DIALOG_DATA) data: SelectAssetsDialogData)
   {
      this.selectedAssets = data.selectedAssets || [];
      this.dataSource.dataSubject.subscribe(() => {
         this.dataSource.data = this.filterDataSource();
      });
      this.dataSource.loading.subscribe((loading) => this.loading = loading);
   }

   private filterDataSource(): RepositoryFlatNode[] {
      let dataSource: RepositoryFlatNode[] = this.dataSource.data.filter((data) => {
         return data.data.type != RepositoryEntryType.TRASHCAN_FOLDER;
      });

      for(let node of dataSource) {
         if(node.data.type === RepositoryEntryType.DATA_SOURCE_FOLDER) {
            this.filterDataSourceFolder(node.data.children);
            break;
         }
      }

      for(let node of dataSource) {
         if(node.data.type === RepositoryEntryType.REPOSITORY_FOLDER) {
            this.filterRepositoryFolder(node.data.children);
            break;
         }
      }

      for(let node of dataSource) {
         if(node.data.type === RepositoryEntryType.RECYCLEBIN_FOLDER) {
            this.filterRecycleFolder(node.data.children);
            break;
         }
      }

      return dataSource;
   }

   private filterDataSourceFolder(nodes: RepositoryTreeNode[]) {
      for(let i = nodes.length - 1; i >= 0; i--) {
         if(nodes[i].type === RepositoryEntryType.DATA_SOURCE) {
            nodes.splice(i, 1);
         }
         else if(!!nodes[i].children && nodes[i].children.length > 0) {
            this.filterDataSourceFolder(nodes[i].children);
         }
      }
   }

   private filterRepositoryFolder(nodes: RepositoryTreeNode[]) {
      for(let i = nodes.length - 1; i >= 0; i--) {
         if(!!nodes[i].children && nodes[i].children.length > 0) {
            this.filterRepositoryFolder(nodes[i].children);
         }
      }
   }

   private filterRecycleFolder(nodes: RepositoryTreeNode[]) {
      for(let i = nodes.length - 1; i >= 0; i--) {
         if(nodes[i].type === RepositoryEntryType.TRASHCAN_FOLDER) {
            nodes.splice(i, 1);
         }
         else if(nodes[i].type === RepositoryEntryType.RECYCLEBIN_FOLDER &&
            nodes[i].path.trim().length != 0)
         {
            nodes.splice(i, 1);
         }
         else if(!!nodes[i].children && nodes[i].children.length > 0) {
            this.filterRepositoryFolder(nodes[i].children);
         }
      }
   }

   ngOnInit() {
   }

   selectNodes(nodes: FlatTreeNode<RepositoryTreeNode>[]): void {
      this.selectedNodes = nodes;
   }

   onDBClicked(node: FlatTreeNode) {
      if(!node.expandable) {
         this.finish();
      }
   }

   finish(): void {
      let users: CommonKVModel<string, string>[] = this.service.getUsers(this.selectedNodes);

      if(users.length == 0) {
         this.updateEntities();
      }
      else {
         this.service.loadUserNode(users).subscribe((model) => {
            this.service.updateUserNodes(this.selectedNodes, model.nodes);
            this.updateEntities();
         });
      }
   }

   @HostListener("window:keyup.esc", [])
   onKeyUp() {
      this.cancel();
   }

   cancel(): void {
      this.dialogRef.close(null);
   }

   private updateEntities() {
      this.loading = true;
      this.service.getExportableAssets(this.selectedNodes, this.selectedAssets).subscribe(
         (model) => {
            this.submitSelected(model.allowedAssets, model.selectedAssets);
         },
         () => {
            this.loading = false;
         });
   }

   private submitSelected(allowed: SelectedAssetModel[], selected: SelectedAssetModel[]): void {
      const all = this.selectedAssets.concat(allowed);
      const denied = selected.filter(s => (!allowed.some(a => this.assetsEqual(a, s))));

      if(denied.length > 0) {
         const message = "_#(js:em.export.nopermission): " +
            denied.map(d => d.path).join(", ");
         this.dialog.open(MessageDialog, {
            data: {
               title: "_#(js:Warning)",
               content: message,
               type: MessageDialogType.WARNING
            }
         }).afterClosed().subscribe(() => {
            this.loading = false;
            this.dialogRef.close(all);
         });
      }
      else {
         this.loading = false;
         this.dialogRef.close(all);
      }
   }

   private assetsEqual(a: SelectedAssetModel, b: SelectedAssetModel): boolean {
      return a.path == b.path && a.type === b.type && Tool.isEquals(a.user, b.user);
   }
}
