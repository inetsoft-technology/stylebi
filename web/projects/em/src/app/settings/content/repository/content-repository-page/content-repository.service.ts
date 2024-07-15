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
import { HttpClient, HttpParams } from "@angular/common/http";
import { Injectable, OnDestroy } from "@angular/core";
import { MatDialog, MatDialogConfig } from "@angular/material/dialog";
import { MatSnackBar } from "@angular/material/snack-bar";
import { BehaviorSubject, merge, Observable, ObservableInput, of, Subject } from "rxjs";
import { catchError, map, share, switchMap } from "rxjs/operators";
import { RepositoryEntryType } from "../../../../../../../shared/data/repository-entry-type.enum";
import { DataSourceEditorModel } from "../../../../../../../shared/util/datasource/data-source-settings-page";
import { DataSourceSettingsModel } from "../../../../../../../shared/util/model/data-source-settings-model";
import { RepositoryEditorModel } from "../../../../../../../shared/util/model/repository-editor-model";
import { Tool } from "../../../../../../../shared/util/tool";
import { MessageDialog, MessageDialogType } from "../../../../common/util/message-dialog";
import { ResourcePermissionModel } from "../../../security/resource-permission/resource-permission-model";
import { ConnectionStatus } from "../../../security/security-provider/security-provider-model/connection-status";
import { convertToKey } from "../../../security/users/identity-id";
import {
   RepositoryFolderDashboardSettingsModel
} from "../dashboard/repository-dashboard-folder-settings-page/repository-folder-dashboard-settings-model";
import {
   RepositoryFolderDashboardEditorModel
} from "../dashboard/repository-dashboard-folder-settings-page/repository-folder-dashboard-settings-page.component";
import {
   RepositoryDashboardSettingsModel
} from "../dashboard/repository-dashboard-settings-page/repository-dashboard-settings-model";
import {
   RepositoryDashboardEditorModel
} from "../dashboard/repository-dashboard-settings-page/repository-dashboard-settings-page.component";
import { DeleteTreeNodesRequest } from "../model/delete-tree-nodes-request";
import { MoveCopyTreeNodesRequest } from "../model/move-copy-tree-nodes-request";
import {
   DataSourceFolderEditorModel
} from "../repository-data-source-folder-settings-page/data-source-folder-editor-model";
import {
   DataSourceFolderSettingsModel
} from "../repository-data-source-folder-settings-page/data-source-folder-settings-model";
import {
   RepositoryFolderRecycleBinModel
} from "../repository-folder-recycle-bin-page/repository-folder-recycle-bin-page.component";
import {
   RepositoryFolderRecycleBinSettingsModel
} from "../repository-folder-recycle-bin-page/repository-folder-recycle-bin-settings-model";
import {
   RepositoryFolderEditorModel
} from "../repository-folder-settings-page/repository-folder-settings-page.component";
import { RepositoryFolderSettingsModel } from "../repository-folder-settings-page/repository-folder-settings.model";
import {
   RepositoryFolderTrashcanSettingsModel
} from "../repository-folder-trashcan-settings-page/repository-folder-trashcan-settings-model";
import {
   RepositoryTrashcanFolderEditorModel
} from "../repository-folder-trashcan-settings-page/repository-folder-trashcan-settings-page.component";
import {
   RepositoryPermissionEditorModel
} from "../repository-permission-editor-page/repository-permission-editor-page.component";
import { RepositoryRecycleBinEntryModel } from "../repository-recycle-bin-page/repository-recycle-bin-enty-model";
import { RepositoryRecycleBinModel } from "../repository-recycle-bin-page/repository-recycle-bin-page.component";
import { RepositoryFlatNode, RepositoryTreeNode } from "../repository-tree-node";
import {
   RepositoryViewsheetEditorModel
} from "../repository-viewsheet-settings-page/repository-viewsheet-settings-page.component";
import { RepositorySheetSettingsModel } from "../repository-worksheet-settings-page/repository-sheet-settings.model";
import {
   RepositoryWorksheetEditorModel
} from "../repository-worksheet-settings-page/repository-worksheet-settings-page.component";
import { WSMVEnabledModel } from "../../../../../../../shared/util/model/mv/ws-mv-enabled-model";
import { MvHasPermissionModel } from "../../../../../../../shared/util/model/mv/mv-has-permission-model";
import { AutoSaveFolderModel } from "../auto-save-recycle-bin/auto-save-folder-model";
import { RepositoryEntryHelper, RepositoryFolderMeta } from "../../../../../../../shared/data/repository-entry-helper";
import {
   ScheduleTaskFolderEditorModel
} from "../repository-schedule-task-folder-settings-page/schedule-task-folder-editor-model";
import { ScriptSettingsModel } from "../repository-script-settings-page/script-settings.model";
import {
   RepositoryScriptEditorModel
} from "../repository-script-settings-page/repository-script-settings-page.component";
import { NewRepositoryEntryRequest } from "./new-repository-entry-request";


@Injectable({
   providedIn: "root"
})
export class ContentRepositoryService implements OnDestroy {
   private readonly addDashboard$ = this.subFactory(
      (parentInfo) => this.http.post("../api/em/settings/content/repository/dashboard/add", parentInfo)
   );
   private readonly addFolder$ = <Subject<any>>this.subFactory(
      ([params, parentInfo]) =>
         this.http.post("../api/em/settings/content/repository/folder/add/", parentInfo, { params }));
   private readonly addDataSource$ = this.subFactory(
      (params: HttpParams) => this.http.post(
         "../api/em/settings/content/repository/data-source/add", null, { params }));
   private readonly deleteNodes$ = this.subFactory(
      (body) => this.http.post("../api/em/content/repository/tree/delete", body));
   private readonly moveNodes$ = this.subFactory(
      (body) => this.http.post("../api/em/content/repository/tree/move", body));

   private _selectedNodes = new BehaviorSubject<RepositoryFlatNode[]>([]);
   private deleteNodesSubscription = this.getDeleteNodesSubscription();
   private loading = new BehaviorSubject<boolean>(false);
   private _needRefreshAfterDelete = new Subject<any>();

   public needRefreshAfterDelete() {
      return this._needRefreshAfterDelete.asObservable();
   }

   private getDeleteNodesObservable() {
      this.deleteNodesSubscription.unsubscribe();
      this.deleteNodesSubscription = this.getDeleteNodesSubscription();
      return this.deleteNodes$;
   }

   private getDeleteNodesSubscription() {
      return this.deleteNodes$
         .pipe(catchError((() => this.getDeleteNodesObservable())))
         .subscribe((connectionStatus: ConnectionStatus) => {
            if(!!connectionStatus && connectionStatus.status) {
               let prompt = connectionStatus.status;
               const corrupt = /^corrupt:.+$/.test(connectionStatus.status);

               if(corrupt) {
                  prompt = prompt.substring(8);
               }

               this.dialog.open(MessageDialog, <MatDialogConfig>{
                  data: {
                     title: "_#(js:Confirm)",
                     content: prompt,
                     type: MessageDialogType.CONFIRMATION
                  }
               }).afterClosed().subscribe(confirmed => {
                  if(confirmed) {
                     const nodes = this.selectedNodes.map((node) => node.data);
                     const body = new DeleteTreeNodesRequest(nodes, true, corrupt);
                     this.deleteNodes$.next(body);
                  }
               });
            }
            else {
               if(!this.selectedNodes.some(node => !this.isLibraryNode(node.data))) {
                  this._needRefreshAfterDelete.next();
               }
            }
         });
   }

   private isLibraryNode(node: RepositoryTreeNode): boolean {
      if(!node) {
         return false;
      }

      return node.type == RepositoryEntryType.BEAN || node.type == RepositoryEntryType.META_TEMPLATE ||
         node.type == RepositoryEntryType.PARAMETER_SHEET || node.type == RepositoryEntryType.SCRIPT ||
         node.type == RepositoryEntryType.TABLE_STYLE ||
         node.type == (RepositoryEntryType.TABLE_STYLE | RepositoryEntryType.FOLDER);
   }

   /**
    * Synchronously return a copy of the current selected nodes
    */
   public get selectedNodes(): RepositoryFlatNode[] {
      return this._selectedNodes.value.slice();
   }

   public set selectedNodes(nodes: RepositoryFlatNode[]) {
      if(nodes != null) {
         this._selectedNodes.next(nodes);
      }
   }

   /**
    * Merged results when tree items are added/removed on the backend
    */
   public get changes(): Observable<any> {
      return merge(this.addDashboard$, this.addFolder$,
         this.addDataSource$, this.deleteNodes$, this.moveNodes$);
   }

   /**
    * Listen to the stream of selection change
    */
   public get selectedNodeChanges(): Observable<RepositoryFlatNode[]> {
      return this._selectedNodes;
   }

   /**
    * Get the data of the first current selected node or null
    */
   public get selectedNode(): RepositoryTreeNode {
      return this.selectedNodes[0] && this.selectedNodes[0].data;
   }

   public get deletionChanges(): Observable<any> {
      return this.deleteNodes$;
   }

   constructor(private http: HttpClient, private snackBar: MatSnackBar, private dialog: MatDialog) {
   }

   ngOnDestroy() {
      if(this.deleteNodesSubscription) {
         this.deleteNodesSubscription.unsubscribe();
      }
   }

   /**
    * Clear the current selected nodes
    */
   public clearSelectedNodes(): void {
      this.selectedNodes = [];
   }

   /**
    * method for hiding recycle bin
    */
   public hideTrash(data: RepositoryFlatNode[]): RepositoryFlatNode[] {
      return data.filter(node => node.data.type !== RepositoryEntryType.RECYCLEBIN_FOLDER && node.data.type !== RepositoryEntryType.TRASHCAN_FOLDER);
   }

   /**
    * Select a single node
    */
   public selectNode(node: RepositoryFlatNode): void {
      if(node != null && !this.selectedNodes.some((n) => n.equals(node))) {
         this.selectedNodes = [...this.selectedNodes, node];
      }
   }

   /**
    * Get the editor model for a given node
    */
   public getEditor(node: RepositoryFlatNode): Observable<RepositoryEditorModel> {
      const data = node.data;

      if(data.readOnly) {
         return of(null);
      }

      if(data.type == RepositoryEntryType.AUTO_SAVE_FOLDER) {
         return of(null);
      }

      if(data.type == RepositoryEntryType.VS_AUTO_SAVE_FOLDER ||
         data.type == RepositoryEntryType.WS_AUTO_SAVE_FOLDER) {
         return of(<AutoSaveFolderModel>{
            path: data.path,
            type: data.type,
            assets: data.children,
            override: false
         });
      }

      if(data.type === RepositoryEntryType.RECYCLEBIN_FOLDER) {
         return this.http.get<RepositoryFolderRecycleBinSettingsModel>(
            "../api/em/content/repository/folder/recycleBin")
            .pipe(map(model => {
               return <RepositoryFolderRecycleBinModel>{
                  path: data.path,
                  type: data.type,
                  recycleNodes: (!!model ? model.table : null),
                  overwrite: false
               };
            }));
      }
      else if(this.isInRecyleBin(data.path)) {
         const params: HttpParams = new HttpParams().set("path", data.path);
         return this.http.get<RepositoryRecycleBinEntryModel>(
            "../api/em/content/repository/recycle/node", { params })
            .pipe(map(model => {
               return <RepositoryRecycleBinModel>{
                  path: data.path,
                  originalPath: model ? model.originalPath : "",
                  originalName: model ? model.originalName : "",
                  time: model ? model.timeLabel : "",
                  type: RepositoryEntryType.ALL,
                  originalType: model ? model.originalType : "",
                  overwrite: false
               };
            }));
      }

      if(data.type === RepositoryEntryType.VIEWSHEET) {
         let params = new HttpParams().set("path", Tool.byteEncode(data.path));
         params = data.owner != null ? params.set("owner", convertToKey(data.owner)) : params;

         return this.http.get<RepositorySheetSettingsModel>(
            "../api/em/content/repository/viewsheet", { params })
            .pipe(
               catchError((error) => {
                  if(!!error.error && error.error.type === "MissingResourceException") {
                     this.showMessage("_#(js:em.repository.missingResource)");
                  }

                  return of(null);
               }),
               map(viewsheetSettings => {
                  return <RepositoryViewsheetEditorModel>{
                     path: data.path, type: data.type,
                     owner: data.owner,
                     viewsheetSettings: viewsheetSettings
                  };
               }));
      }

      if(data.type === RepositoryEntryType.WORKSHEET) {
         let params = new HttpParams().set("path", Tool.byteEncode(data.path));
         params = data.owner != null ? params.set("owner", convertToKey(data.owner)) : params;

         return this.http.get<RepositorySheetSettingsModel>(
            "../api/em/content/repository/worksheet", { params })
            .pipe(map(worksheetSettings => {
               return <RepositoryWorksheetEditorModel>{
                  path: data.path, type: data.type,
                  owner: data.owner,
                  worksheetSettings: worksheetSettings
               };
            }));
      }

      if(data.type === RepositoryEntryType.DASHBOARD) {
         let params = new HttpParams().set("path", Tool.byteEncode(data.path));
         params = data.owner != null ? params.set("owner", convertToKey(data.owner)) : params;

         return this.http.get<RepositoryDashboardSettingsModel>(
            "../api/em/content/repository/dashboard", { params })
            .pipe(map(dashboardSettings => {
               return <RepositoryDashboardEditorModel>{
                  path: data.path, type: data.type,
                  owner: data.owner,
                  dashboardSettings: dashboardSettings
               };
            }));
      }

      if(this.isDataSource(data.type)) {
         if(data.label === "_#(js:Data Model)") {
            return of(null);
         }

         const params = new HttpParams().set("path", data.path);
         return this.http.get<DataSourceSettingsModel>("../api/data/databases", { params }).pipe(
            catchError((error) => {
               if(error.error != null && error.error.message == "Datasource does not exist!") {
                  this.showMessage("_#(js:data.datasources.findDataSourceError)");
               }
               else {
                  this.showMessage("_#(js:em.data.databases.getDatabaseError)");
               }

               return of(null);
            }),
            map(model => {
               return <DataSourceEditorModel>{
                  path: data.path, type: data.type,
                  fullPath: data.fullPath,
                  settings: model
               };
            })
         );
      }

      if(data.type === RepositoryEntryType.TRASHCAN_FOLDER) {
         return this.http.get<RepositoryFolderTrashcanSettingsModel>(
            "../api/em/content/repository/folder/trashcan")
            .pipe(map(model => {
               return <RepositoryTrashcanFolderEditorModel>{
                  path: data.path, type: data.type,
                  reports: (!!model ? model.table : null)
               };
            }));
      }
      else if(data.type === RepositoryEntryType.DASHBOARD_FOLDER) {
         return this.http.get<RepositoryFolderDashboardSettingsModel>(
            "../api/em/content/repository/folder/dashboard")
            .pipe(map(model => {
               return <RepositoryFolderDashboardEditorModel>{
                  path: data.path, type: data.type,
                  dashboardFolderSettings: model
               };
            }));
      }
      else if(this.isDataSourceEntry(data.type) ||
         data.type === RepositoryEntryType.DATA_SOURCE_FOLDER && data.path === "/" ||
         data.type === RepositoryEntryType.SCHEDULE_TASK_FOLDER && data.path === "/" ||
         this.isDataModelFolderEntry(data.type)) {
         //no editor for physical view, extended logical model and vpm
         if(data.type === RepositoryEntryType.LOGIC_MODEL ||
            (data.type & RepositoryEntryType.PARTITION) === RepositoryEntryType.PARTITION ||
            data.type === RepositoryEntryType.VPM) {
            return of(null);
         }

         const params: HttpParams = new HttpParams()
            .set("path", data.path)
            .set("type", data.type + "");
         return this.http.get<ResourcePermissionModel>(
            "../api/em/content/repository/tree/node/permission", { params })
            .pipe(map(model => {
               return <RepositoryPermissionEditorModel>{
                  path: data.path, type: data.type,
                  fullPath: data.fullPath,
                  label: data.label,
                  permissionModel: model ? model : null
               };
            }));
      }
      else if(data.type === RepositoryEntryType.SCHEDULE_TASK_FOLDER) {
         const params = new HttpParams().set("path", data.path);
         const uri = "../api/em/settings/content/repository/scheduleTaskFolder";

         return this.http.get<ScheduleTaskFolderEditorModel>(uri, { params })
            .pipe(
               catchError((error) => {
                  if(!!error.error && error.error.type === "MessageException") {
                     this.showMessage(error.error.message);
                  }

                  return of(null);
               }),
               map(model => {
                  model.folderMeta = this.getEditNodeMetaMap(data);

                  return <ScheduleTaskFolderEditorModel>{
                     folder: model,
                     path: data.path,
                     type: data.type,
                     label: data.label
                  };
               })
            );
      }
      else if(data.type === RepositoryEntryType.DATA_SOURCE_FOLDER) {
         const params = new HttpParams().set("path", data.path);
         const uri = "../api/em/settings/content/repository/dataSourceFolder";
         return this.http.get<DataSourceFolderSettingsModel>(uri, { params })
            .pipe(
               catchError((error) => {
                  if(!!error.error && error.error.type === "MessageException") {
                     this.showMessage(error.error.message);
                  }

                  return of(null);
               }),
               map(model => {
                  model.folderMeta = this.getEditNodeMetaMap(data);

                  return <DataSourceFolderEditorModel>{
                     path: data.path,
                     fullPath: data.fullPath,
                     type: data.type,
                     folder: model
                  };
               })
            );
      }
      else if(this.isFolder(data.type) && !this.isLibraryEntry(data.type)) {
         const isWorksheetFolder: boolean =
            (data.type & RepositoryEntryType.WORKSHEET_FOLDER) === RepositoryEntryType.WORKSHEET_FOLDER;
         let params = new HttpParams().set("path", Tool.byteEncode(data.path));
         params = params.set("isWorksheetFolder", isWorksheetFolder + "");
         params = data.owner != null ? params.set("owner", Tool.byteEncode(convertToKey(data.owner))) : params;

         return this.http.get<RepositoryFolderSettingsModel>(
            "../api/em/content/repository/folder/", { params })
            .pipe(
               catchError((error) => {
                  if(!!error.error && error.error.type === "MessageException") {
                     this.showMessage(error.error.message);
                  }

                  return of(null);
               }),
               map(folderModel => {

                  folderModel.folderMeta = this.getEditNodeMetaMap(data);

                  return <RepositoryFolderEditorModel>{
                     path: data.path, type: data.type,
                     folderModel: folderModel,
                     owner: data.owner,
                     label: data.label
                  };
               }));
      }
      else if(data.type == RepositoryEntryType.SCRIPT) {
         const params: HttpParams = new HttpParams()
            .set("path", data.path)
            .set("type", data.type + "");
         const uri = "../api/em/settings/content/repository/script";
         return this.http.get<ScriptSettingsModel>(uri, { params })
            .pipe(
               catchError((error) => {
                  if(!!error.error && error.error.type === "MessageException") {
                     this.showMessage(error.error.message);
                  }

                  return of(null);
               }),
               map(model => {
                  return <RepositoryScriptEditorModel>{
                     path: data.path, type: data.type,
                     owner: data.owner,
                     scriptSettings: model
                  };
               })
            );
      }
      else {
         const params: HttpParams = new HttpParams()
            .set("path", data.path)
            .set("type", data.type + "");
         return this.http.get<ResourcePermissionModel>(
            "../api/em/content/repository/permission", { params }).pipe(
               map(model => {
                  return <RepositoryPermissionEditorModel>{
                     label: data.label,
                     path: data.path,
                     fullPath: data.fullPath,
                     type: data.type,
                     permissionModel: model
                  };
               })
            );
      }
   }

   private getEditNodeMetaMap(node: RepositoryTreeNode): RepositoryFolderMeta {
      let metaMap = new Map<string, number>();
      this.getEditNodeMetaMap0(node, metaMap);
      let metaItems = [];

      for(let entry of metaMap.entries()) {
         metaItems.push({
            assetTypeLabel: entry[0],
            assetCount: entry[1]
         });
      }

      return {
         metaItems: metaItems
      };
   }

   private getEditNodeMetaMap0(node: RepositoryTreeNode, metaMap: Map<string, number>): void {
      if(!node?.children) {
         return;
      }

      for(let item of node.children) {
         let typeMetaLabel = RepositoryEntryHelper.getNodeTypeMetaLabel(item, node);

         if(typeMetaLabel) {
            let sameTypeItemsCount = metaMap.get(typeMetaLabel);

            if(!sameTypeItemsCount) {
               sameTypeItemsCount = 1;
            }
            else {
               sameTypeItemsCount += 1;
            }

            metaMap.set(typeMetaLabel, sameTypeItemsCount);
         }

         if(this.isFolder(item.type)) {
            this.getEditNodeMetaMap0(item, metaMap);
         }
      }
   }

   public addDashboard(node: RepositoryTreeNode): void {
      const parentFolder = node.path;
      const owner = node.owner;
      this.addDashboard$.next({ parentFolder, owner });
   }

   public addFolder(node: RepositoryTreeNode, name?: string): void {
      const parentInfo: NewRepositoryEntryRequest = {
         parentFolder: node.path,
         type: node.type,
         owner: node.owner,
         folderName: name
      };
      const isWorksheetFolder: boolean =
         (node.type & RepositoryEntryType.WORKSHEET_FOLDER) === RepositoryEntryType.WORKSHEET_FOLDER;
      const params = new HttpParams().set("isWorksheetFolder", isWorksheetFolder + "");
      this.addFolder$.next([params, parentInfo]);
   }

   public deleteSelectedNodes(): void {
      const nodes = this.selectedNodes.map((node) => node.data);
      const nodeNames = this.selectedNodes.map((node) => node.data.label).sort().join(", ");
      let content = "_#(js:em.common.items.delete) " + nodeNames + "?";
      content += this.getDeleteNodesDescription();

      this.dialog.open(MessageDialog, <MatDialogConfig>{
         width: "350px",
         data: {
            title: "_#(js:Confirm)",
            content: content,
            type: MessageDialogType.CONFIRMATION
         }
      }).afterClosed().subscribe(confirmed => {
         if(confirmed) {
            const requestBody = new DeleteTreeNodesRequest(nodes, false);
            this.deleteNodes$.next(requestBody);
         }
      });
   }

   getDeleteNodesDescription(): string {
      let description = "";
      let componentNode = false;
      let recycleNode = false;

      for(let i = 0; i < this.selectedNodes.length; i++) {
         const node = this.selectedNodes[0];
         const type = node.data.type;

         if((type & RepositoryEntryType.BEAN) === RepositoryEntryType.BEAN ||
            (type & RepositoryEntryType.META_TEMPLATE) === RepositoryEntryType.META_TEMPLATE ||
            (type & RepositoryEntryType.PARAMETER_SHEET) === RepositoryEntryType.PARAMETER_SHEET ||
            (type & RepositoryEntryType.SCRIPT) === RepositoryEntryType.SCRIPT ||
            (type & RepositoryEntryType.TABLE_STYLE) === RepositoryEntryType.TABLE_STYLE) {
            componentNode = true;
         }

         if(!!node.data.path && node.data.path.indexOf(Tool.RECYCLE_BIN) != -1) {
            recycleNode = true;
         }

         if(type == RepositoryEntryType.WS_AUTO_SAVE_FOLDER ||
            type == RepositoryEntryType.VS_AUTO_SAVE_FOLDER ||
            type == RepositoryEntryType.AUTO_SAVE_VS ||
            type == RepositoryEntryType.AUTO_SAVE_WS) {
            componentNode = false;
            recycleNode = true;
         }
      }

      if(componentNode) {
         description += "\n\n_#(js:em.common.items.deleteLibraryEntry)";
      }

      if(recycleNode) {
         description += "\n\n_#(js:em.common.items.deleteTrashItems)";
      }

      return description;
   }

   public moveSelectedNodes(target: RepositoryFlatNode): void {
      const fromNodes: RepositoryTreeNode[] = [];
      //prevent from dropping on file folder(ReportFiles)

      const toFolder: RepositoryTreeNode = target.data;
      let warning;

      if((toFolder.type & RepositoryEntryType.FOLDER) === 0) {
         return;
      }

      if((toFolder.type & RepositoryEntryType.TRASHCAN_FOLDER) === RepositoryEntryType.TRASHCAN_FOLDER ||
         (toFolder.type & RepositoryEntryType.RECYCLEBIN_FOLDER) === RepositoryEntryType.RECYCLEBIN_FOLDER) {
         warning = "_#(js:em.reports.drag.trashcan.note)";
      }
      else if(((toFolder.type & RepositoryEntryType.USER_FOLDER) === RepositoryEntryType.USER_FOLDER &&
         fromNodes.some((from) => from.owner !== toFolder.owner)) || toFolder.path == Tool.USERS_REPORTS) {
         warning = "_#(js:em.reports.drag.userfolder.note)";
      }

      if(warning) {
         this.showMessage(warning);
         return;
      }

      let prevDataType = 0;

      for(let i = 0; i < this.selectedNodes.length; i++) {
         const fromNode: RepositoryTreeNode = this.selectedNodes[i].data;
         const curDataType = fromNode.type;
         const curPath = fromNode.path;

         if((curDataType & RepositoryEntryType.WORKSHEET) !== RepositoryEntryType.WORKSHEET &&
            (curDataType & RepositoryEntryType.VIEWSHEET) !== RepositoryEntryType.VIEWSHEET &&
            (curDataType & RepositoryEntryType.WORKSHEET_FOLDER) !== RepositoryEntryType.WORKSHEET_FOLDER &&
            (curDataType & RepositoryEntryType.REPOSITORY_FOLDER) !== RepositoryEntryType.REPOSITORY_FOLDER &&
            (curDataType & RepositoryEntryType.DATA_SOURCE) !== RepositoryEntryType.DATA_SOURCE &&
            (curDataType & RepositoryEntryType.DATA_SOURCE_FOLDER) !== RepositoryEntryType.DATA_SOURCE_FOLDER &&
            curDataType != (RepositoryEntryType.LOGIC_MODEL | RepositoryEntryType.FOLDER) &&
            curDataType != (RepositoryEntryType.PARTITION | RepositoryEntryType.FOLDER) &&
            curDataType != RepositoryEntryType.QUERY &&
            !((curDataType & RepositoryEntryType.FOLDER) === RepositoryEntryType.FOLDER && fromNode.owner != null)) {
            warning = "_#(js:em.reports.drag.datatype.note)";
         }

         if(warning) {
            this.showMessage(warning);
            return;
         }

         prevDataType = curDataType;

         if(curPath === "Repository/Worksheets" &&
            (curDataType & RepositoryEntryType.WORKSHEET_FOLDER) === RepositoryEntryType.WORKSHEET_FOLDER) {
            warning = "_#(js:em.reports.drag.defaultfolder.note)";
         }
         else if(((curDataType & RepositoryEntryType.WORKSHEET_FOLDER) === RepositoryEntryType.WORKSHEET_FOLDER ||
            (curDataType & RepositoryEntryType.WORKSHEET) === RepositoryEntryType.WORKSHEET) &&
            (toFolder.type & RepositoryEntryType.WORKSHEET_FOLDER) !== RepositoryEntryType.WORKSHEET_FOLDER) {
            warning = "_#(js:em.reports.drag.unworksheetfolder.note)";
         }
         else if(((curDataType & RepositoryEntryType.WORKSHEET_FOLDER) !== RepositoryEntryType.WORKSHEET_FOLDER &&
            (curDataType & RepositoryEntryType.WORKSHEET) !== RepositoryEntryType.WORKSHEET) &&
            (toFolder.type & RepositoryEntryType.WORKSHEET_FOLDER) === RepositoryEntryType.WORKSHEET_FOLDER) {
            warning = "_#(js:em.reports.drag.unworksheetfolder.note)";
         }
         else if(curPath.startsWith(Tool.RECYCLE_BIN)) {
            warning = "_#(js:em.reports.drag.recycle.note)";
         }
         else if(((curDataType & RepositoryEntryType.DATA_SOURCE) === RepositoryEntryType.DATA_SOURCE ||
            (curDataType & RepositoryEntryType.DATA_SOURCE_FOLDER) === RepositoryEntryType.DATA_SOURCE_FOLDER) &&
            (toFolder.type & RepositoryEntryType.DATA_SOURCE_FOLDER) !== RepositoryEntryType.DATA_SOURCE_FOLDER) {
            warning = "_#(js:em.reports.drag.datasource.note)";
         }
         else if((curDataType == (RepositoryEntryType.LOGIC_MODEL | RepositoryEntryType.FOLDER) ||
            curDataType == (RepositoryEntryType.PARTITION | RepositoryEntryType.FOLDER)) &&
            toFolder.type !== RepositoryEntryType.DATA_MODEL_FOLDER &&
            toFolder.type !== (RepositoryEntryType.DATA_SOURCE | RepositoryEntryType.FOLDER)) {
            warning = "_#(js:em.database.drag.dataModel.note)";
         }
         else if(curDataType == RepositoryEntryType.QUERY &&
            toFolder.type !== (RepositoryEntryType.QUERY | RepositoryEntryType.FOLDER) &&
            toFolder.type != (RepositoryEntryType.DATA_SOURCE | RepositoryEntryType.FOLDER)) {
            warning = "_#(js:em.database.drag.query.note)";
         }

         if(fromNode.type == RepositoryEntryType.REPOSITORY_FOLDER) {
            let name = curPath.lastIndexOf("/") != -1 ?
               curPath.substring(curPath.lastIndexOf("/") + 1) : curPath;
            let npath = toFolder.path + "/" + name;

            if(this.isDuplicatedName(curPath, npath, toFolder)) {
               warning = "_#(js:em.folder.exist)" + toFolder.path;
            }
         }

         if(warning) {
            this.showMessage(warning);
            return;
         }

         if((curPath === toFolder.path || toFolder.children && toFolder.children.indexOf(fromNode) >= 0) &&
            fromNode.owner === toFolder.owner) {
            continue;
         }

         if(fromNode.path === "/" || fromNode.path.endsWith("/Worksheets")) {
            continue;
         }

         //prevent from dragging file folder(ReportFiles)

         fromNodes.push(fromNode);
      }

      const request = <MoveCopyTreeNodesRequest>{
         source: fromNodes,
         destination: toFolder
      };

      this.loading.next(true);
      let subscription = this.moveNodes$.subscribe(
         () => {
            this.loading.next(false);
            subscription.unsubscribe();
         }, () => {
            this.loading.next(false);
            subscription.unsubscribe();
         });
      this.moveNodes$.next(request);
   }

   isDuplicatedName(name: string, npath: string, pathTo: RepositoryTreeNode) {
      return pathTo.children?.length && pathTo.children.some((children) => children.path == npath);
   }

   public isInRecyleBin(path: String): boolean {
      return path && path !== Tool.RECYCLE_BIN && (path.indexOf(Tool.RECYCLE_BIN) === 0 ||
         path.indexOf(Tool.MY_REPORTS_RECYCLE_BIN) === 0);
   }

   public isFolder(type: RepositoryEntryType): boolean {
      return (type & RepositoryEntryType.FOLDER) === RepositoryEntryType.FOLDER;
   }

   public isDataSource(type: RepositoryEntryType): boolean {
      return (type & RepositoryEntryType.DATA_SOURCE) === RepositoryEntryType.DATA_SOURCE;
   }

   public isDataSourceEntry(type: RepositoryEntryType): boolean {
      return (type & RepositoryEntryType.QUERY) === RepositoryEntryType.QUERY ||
         (type & RepositoryEntryType.LOGIC_MODEL) === RepositoryEntryType.LOGIC_MODEL ||
         (type & RepositoryEntryType.PARTITION) === RepositoryEntryType.PARTITION ||
         type === RepositoryEntryType.VPM;
   }

   public isDataModelFolderEntry(type: RepositoryEntryType) {
      return (type & RepositoryEntryType.DATA_MODEL) === RepositoryEntryType.DATA_MODEL &&
         (type & RepositoryEntryType.FOLDER) === RepositoryEntryType.FOLDER;
   }

   public isTrashcanArchiveEntry(type: RepositoryEntryType): boolean {
      return (type & RepositoryEntryType.TRASHCAN) === RepositoryEntryType.TRASHCAN &&
         !((type & RepositoryEntryType.FOLDER) === RepositoryEntryType.FOLDER);
   }

   public isLibraryEntry(type: RepositoryEntryType): boolean {
      return (type & RepositoryEntryType.LIBRARY_FOLDER) === RepositoryEntryType.LIBRARY_FOLDER ||
         (type & RepositoryEntryType.BEAN) === RepositoryEntryType.BEAN ||
         (type & RepositoryEntryType.META_TEMPLATE) === RepositoryEntryType.META_TEMPLATE ||
         (type & RepositoryEntryType.PARAMETER_SHEET) === RepositoryEntryType.PARAMETER_SHEET ||
         (type & RepositoryEntryType.SCRIPT) === RepositoryEntryType.SCRIPT ||
         (type & RepositoryEntryType.TABLE_STYLE) === RepositoryEntryType.TABLE_STYLE;
   }

   private showMessage(msg: string): void {
      this.snackBar.open(msg, null, { duration: Tool.SNACKBAR_DURATION });
   }

   /**
    * Return a subject that switches to an inner observable returned by a callback function
    */
   private subFactory<T, U>(project: (value: T) => ObservableInput<U>): Subject<U> {
      return <Subject<U>>new Subject<T>().pipe(switchMap(project), share());
   }

   public isWSMVEnabled(): Observable<boolean> {
      return this.http.get<WSMVEnabledModel>("../api/em/content/repository/mv/ws-mv-enabled").pipe(
         map(model => model.enabled));
   }

   public hasMVPermission(): Observable<boolean> {
      return this.http.get<MvHasPermissionModel>("../api/em/content/repository/mv/permission").pipe(
         map(model => model.allow));
   }

   public get loadingChanges(): Observable<boolean> {
      return this.loading;
   }
}
