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
import { Injectable } from "@angular/core";
import { HttpClient, HttpErrorResponse } from "@angular/common/http";
import { convertToKey, equalsIdentity, IdentityId } from "../../../security/users/identity-id";
import { RepositoryTreeNode } from "../repository-tree-node";
import { RepositoryEntryType } from "../../../../../../../shared/data/repository-entry-type.enum";
import { Tool } from "../../../../../../../shared/util/tool";
import { RequiredAssetModel, RequiredAssetModelList } from "./required-asset-model";
import { SelectedAssetModel, SelectedAssetModelList } from "./selected-asset-model";
import { FlatTreeNode, TreeDataModel } from "../../../../common/util/tree/flat-tree-model";
import { Observable, throwError, timer } from "rxjs";
import { catchError, first, map, scan, switchMap, tap } from "rxjs/operators";
import { MessageDialog, MessageDialogType } from "../../../../common/util/message-dialog";
import { MatDialog } from "@angular/material/dialog";
import { CommonKVModel } from "../../../../../../../portal/src/app/common/data/common-kv-model";

export interface ExportStatusModel {
   ready: boolean;
}

@Injectable()
export class ExportAssetsService {
   constructor(private http: HttpClient, private dialog: MatDialog) {
   }

   public getExportableAssets(selectedNodes: FlatTreeNode<RepositoryTreeNode>[],
                              selectedAssets: SelectedAssetModel[] = []): Observable<SelectedAssetModelList>
   {
      const exportNodes = selectedNodes.filter(node => this.isExportableNode(node.data));
      const exportFolders = selectedNodes.filter(node => this.isExportableFolder(node.data));
      const assets = exportNodes
         .filter(node => this.isNotSelected(selectedAssets, node.data.path, node.data.owner))
         .map(node => ({
            path: node.data.path ? node.data.path : node.data.label,
            type: node.data.type,
            typeName: "",
            typeLabel: "",
            user: node.data.owner,
            description: node.data.description,
            icon: node.data.icon,
            lastModifiedTime: node.data.lastModifiedTime
         }));
      exportFolders
         .map(node => node.data)
         .forEach(folderData => {
            this.addChildNode([folderData], assets, selectedAssets);
         });

      const uri = "../api/em/content/repository/export/check-permission";
      const statusUri = "../api/em/content/repository/export/check-permission/status";
      const valueUri = "../api/em/content/repository/export/check-permission/value";
      const data = {selectedAssets: assets};

      return this.http.post(uri, data).pipe(
            catchError(err => this.handleCheckPermissionError(err)),
            switchMap(() => this.pollForStatus(statusUri, 1500, 1000)),
            switchMap(() => this.http.get<SelectedAssetModelList>(valueUri)),
            map(result => <SelectedAssetModelList> {
               selectedAssets: assets,
               allowedAssets: result.selectedAssets
            })
         );
   }

   private addChildNode(queue: RepositoryTreeNode[], assets: SelectedAssetModel[],
                        selectedAssets: SelectedAssetModel[])
   {
      while(queue.length > 0) {
         const node = queue.shift();

         node.children
            .forEach((child) => {
               let type = child.type;
               let path = child.path ? child.path : child.label;
               let update: boolean = false;

               if(this.isExportableFolder(child)) {
                  queue.push(child);

                  if((child.type & RepositoryEntryType.DATA_SOURCE) == RepositoryEntryType.DATA_SOURCE &&
                     assets.findIndex(asset => asset.path == child.path) == -1)
                  {
                     update = true;
                  }
               }
               else if(this.isExportableNode(child) &&
                  this.isNotSelected(selectedAssets, path, child.owner))
               {
                  //additional datasource connection and cube should not be exported
                  if(type == RepositoryEntryType.DATA_SOURCE || type == RepositoryEntryType.CUBE) {
                     return;
                  }

                  update = true;
               }

               if(update) {
                  assets.push({
                     path: path,
                     type: type,
                     typeName: "",
                     typeLabel: "",
                     user: child.owner,
                     description: child.description,
                     icon: child.icon,
                     lastModifiedTime: child.lastModifiedTime
                  });
               }
            });
      }
   }

   public getDependentAssets(selectedAssets: SelectedAssetModel[]): Observable<RequiredAssetModelList> {
      const data = { selectedAssets };
      const uri = "../api/em/content/repository/export/get-dependent-assets";
      const statusUri = "../api/em/content/repository/export/get-dependent-assets/status";
      const valueUri = "../api/em/content/repository/export/get-dependent-assets/value";
      return this.http.post(uri, data)
         .pipe(
            switchMap(() => this.pollForStatus(statusUri, 3000, 2500)),
            switchMap(() => this.http.get<RequiredAssetModelList>(valueUri))
         );
   }

   public createExport(selectedEntities: SelectedAssetModel[],
                       dependentAssets: RequiredAssetModel[], overwriting: boolean, name: string,
                       newerVersion: boolean): Observable<any> {
      const data = { selectedEntities, dependentAssets, overwriting, name, newerVersion };
      const uri = "../api/em/content/repository/export/create";
      const statusUri = "../api/em/content/repository/export/create/status";
      return this.http.post(uri, data)
         .pipe(
            switchMap(() => this.pollForStatus(statusUri, 3000, 2500)),
         );
   }

   private isNotSelected(selectedAssets: SelectedAssetModel[], path: string, user: IdentityId) {
      return !selectedAssets.some(asset => asset.path === path && asset.user === user);
   }

   private isExportableNode(node: RepositoryTreeNode): boolean {
      const type = node.type;

      //Additional data source connections are not exportable
      if((type & RepositoryEntryType.DATA_SOURCE) == RepositoryEntryType.DATA_SOURCE) {
         return (node.type & RepositoryEntryType.FOLDER) ==
            RepositoryEntryType.FOLDER && node.label !== "_#(js:Data Model)";
      }

      if((type & RepositoryEntryType.PARTITION) == RepositoryEntryType.PARTITION ||
         (type & RepositoryEntryType.LOGIC_MODEL) == RepositoryEntryType.LOGIC_MODEL)
      {
         return true;
      }

      if(node.path.indexOf(Tool.BUILT_IN_ADMIN_REPORTS) != -1) {
         return false;
      }

      return (type & RepositoryEntryType.FOLDER) != RepositoryEntryType.FOLDER &&
         (type & RepositoryEntryType.TRASHCAN) != RepositoryEntryType.TRASHCAN &&
         (node.path.indexOf(Tool.RECYCLE_BIN) != 0);
   }

   private isExportableFolder(node: RepositoryTreeNode): boolean {
      return (node.type & RepositoryEntryType.FOLDER) == RepositoryEntryType.FOLDER &&
         (node.type & RepositoryEntryType.TRASHCAN_FOLDER) != RepositoryEntryType.TRASHCAN_FOLDER &&
         !(node.label == Tool.RECYCLE_BIN && node.path == "/") &&
         (node.path.indexOf(Tool.RECYCLE_BIN) != 0) &&
         node.path.indexOf(Tool.BUILT_IN_ADMIN_REPORTS) != 0 &&
         !!node.children && node.children.length != 0;
   }

   private pollForStatus(statusUri: string, pollInterval: number, maxAttempts: number): Observable<ExportStatusModel>
   {
      const checkAttempts: (attempts: number) => void = attempts => {
         if(attempts > maxAttempts) {
            throw new Error("Maximum number of attempts exceeded");
         }
      };

      return timer(0, pollInterval)
         .pipe(
            scan(attempts => ++attempts, 0),
            tap(attempts => checkAttempts(attempts)),
            switchMap(() => this.http.get<ExportStatusModel>(statusUri)),
            first(status => status.ready)
         );
   }

   private handleCheckPermissionError(error: HttpErrorResponse): Observable<SelectedAssetModelList> {
      this.dialog.open(MessageDialog, {
         data: {
            title: "_#(js:Error)",
            content: error.error.message,
            type: MessageDialogType.ERROR
         }
      });
      return throwError(error);
   }

   public updateUserNodes(selectedNodes: FlatTreeNode<RepositoryTreeNode>[], data: RepositoryTreeNode[]) {
      if(!data) {
         return;
      }

      for(let selectNode of selectedNodes) {
         this.addUserNode(selectNode, data);
      }
   }

   private addUserNode(selectNode: FlatTreeNode<RepositoryTreeNode>, nodes: RepositoryTreeNode[]) {
      if(this.isUserNode(selectNode) && (selectNode.data.path == "Users' Reports" ||
         selectNode.data.path == "Users' Dashboards" ||
         selectNode.data.path == "Schedule Tasks"))
      {
         let users: RepositoryTreeNode[] = selectNode.data.children;

         users.forEach((user: RepositoryTreeNode) => {
            nodes.forEach((node: RepositoryTreeNode) => {
               if(!user.children && equalsIdentity(user.owner, node.owner)) {
                  user.children = [node];
               }
               else if(equalsIdentity(user.owner, node.owner)) {
                  user.children.push(node);
               }
            });

            if(!user.children) {
               user.children = [];
            }
         });
      }
      else if(this.isUserNode(selectNode)) {
         selectNode.data.children = nodes;
      }
   }

   public getUsers(selectedNodes: FlatTreeNode<RepositoryTreeNode>[]): CommonKVModel<string, string>[] {
      let owners: CommonKVModel<string, string>[] = [];

      for(let selectNode of selectedNodes) {
         if(this.isUserNode(selectNode) && (selectNode.data.path == "Users' Reports" ||
            selectNode.data.path == "Users' Dashboards" ||
            selectNode.data.path == "Schedule Tasks"))
         {
            let users: RepositoryTreeNode[] = selectNode.data.children.filter(node => !node.children);
            users.forEach((user) => owners.push({key: convertToKey(user.owner), value: user.path}));
         }
         else if(this.isUserNode(selectNode)) {
            owners.push({key: convertToKey(selectNode.data.owner), value: selectNode.data.path});
         }
      }

      return owners;
   }

   public loadUserNode(owners: CommonKVModel<string, string>[]): Observable<TreeDataModel<RepositoryTreeNode>> {
      return this.http.post<TreeDataModel<RepositoryTreeNode>>("../api/em/content/repository/private/tree", owners);
   }

   private isUserNode(node: FlatTreeNode<RepositoryTreeNode>): boolean {
      return node.data.type === (RepositoryEntryType.USER | RepositoryEntryType.FOLDER);
   }
}
