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
import { Validators } from "@angular/forms";
import { ActivatedRoute, Router } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Observable } from "rxjs";
import { map } from "rxjs/operators";
import { AssetType } from "../../../../../../shared/data/asset-type";
import { FormValidators } from "../../../../../../shared/util/form-validators";
import { Tool } from "../../../../../../shared/util/tool";
import { ComponentTool } from "../../../common/util/component-tool";
import { InputNameDialog } from "../../../widget/dialog/input-name-dialog/input-name-dialog.component";
import { ExpandStringDirective } from "../../../widget/expand-string/expand-string.directive";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { AddFolderRequest } from "../commands/add-folder-request";
import { CheckDuplicateResponse } from "../commands/check-duplicate-response";
import { CheckDuplicateRequest } from "../commands/check-duplicate-request";
import { MoveCommand } from "../commands/move-command";
import { DatasourceBrowserService } from "../data-datasource-browser/datasource-browser.service";
import { DataBrowserService } from "../data-folder-browser/data-browser.service";
import { PortalDataBrowserModel } from "../data-folder-browser/portal-data-browser-model";
import { WorksheetBrowserInfo } from "../model/worksheet-browser-info";
import { DataSourceInfo } from "../model/data-source-info";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import {
   FAKE_ROOT_PATH,
   MoveAssetDialogComponent
} from "../move-asset-dialog/move-asset-dialog.component";
import { AnalyzeMVDialog } from "../../dialog/analyze-mv/analyze-mv-dialog.component";
import { MVTreeModel } from "../model/mv-tree-model";
import { RepositoryEntryType } from "../../../../../../shared/data/repository-entry-type.enum";
import { AssetEntryHelper } from "../../../common/data/asset-entry-helper";
import { DatasourceTreeAction } from "../model/datasources/database/datasource-tree-action";
import { PortalDataType } from "./portal-data-type";

const DATASOURCE_FOLDER_ADD = "../api/data/datasources/browser/folder/add";
const DATASOURCE_FOLDER_CHECKDUPLICATE = "../api/data/datasources/browser/folder/checkDuplicate";
const DATA_URI: string = "../api/data/datasets";
const FOLDER_URI: string = "../api/data/folders";
const SPECIFIED_FOLDER_URI: string = "../api/data/folders/folder/";
const PORTAL_DATA_BROWSER_URI = "../api/portal/data/browser";
const DATASOURCE_FOLDER_URI = "../api/data/datasources/folder/";
const DATASOURCE_URI = "../api/portal/data/datasources/";


@Injectable()
export class DataSourcesTreeActionsService {
   constructor(private httpClient: HttpClient,
               private route: ActivatedRoute,
               private router: Router,
               private modalService: NgbModal,
               private dataBrowserService: DataBrowserService,
               private dataSourceBrowserService: DatasourceBrowserService)
   {
   }

   addDataSource(currentFolderPath: string, currentFolderPathScope: string): void {
      let queryParams = {
         path: currentFolderPath,
         scope: currentFolderPathScope,
      };

      this.router.navigate(["/portal/tab/data/datasources/listing", currentFolderPath],
         {relativeTo: this.route, queryParams: queryParams});
   }

   addDataSourceFolder(currentFolderPath: string, callback?: Function): void {
      const dialog = ComponentTool.showDialog(this.modalService, InputNameDialog,
         (result: string) => {
            const request = new AddFolderRequest(result.trim(), currentFolderPath, null);
            this.httpClient.post(DATASOURCE_FOLDER_ADD, request)
               .subscribe(() => {
                  if(callback) {
                     callback();
                  }
               });
         });

      dialog.title = "_#(js:New Folder)";
      dialog.label = "_#(js:Folder Name)";
      dialog.helpLinkKey = "DataSourceNewFolder";
      dialog.validators = [
         FormValidators.required,
         FormValidators.isValidDataSourceFolderName
      ];
      dialog.validatorMessages = [
         {validatorName: "required", message: "_#(js:data.datasets.folderNameRequired)"},
         {validatorName: "containsSpecialCharsForName", message: "_#(js:data.datasets.folderNameInvalid)"},
         {
            validatorName: "isDefaultCubesName",
            message: ExpandStringDirective.expandString(
               "_#(js:common.cube.defaulecubesname)", ["Cubes"])
         }
      ];
      dialog.hasDuplicateCheck = (value: string) =>
         this.httpClient
            .post<CheckDuplicateResponse>(DATASOURCE_FOLDER_CHECKDUPLICATE,
               <CheckDuplicateRequest> {
                  path: currentFolderPath,
                  newName: value,
                  type: AssetType.DATA_SOURCE_FOLDER,
                  scope: null})
            .pipe(map(response => response.duplicate));
      dialog.duplicateMessage = "_#(js:common.datasource.newFolder.duplicateName)";
   }

   addDataWorksheet(currentFolderID: string): void {
      this.dataBrowserService.newWorksheet(currentFolderID);
   }

   addDataWorksheetFolder(currentFolderPath: string, currentFolderPathScope: string,
                          callback?: Function): void
   {
      const dialog = ComponentTool.showDialog(this.modalService, InputNameDialog,
         (result: string) => {
            const request = new AddFolderRequest(result, currentFolderPath,
               +currentFolderPathScope);
            this.httpClient.post(FOLDER_URI, request)
               .subscribe(() => {
                  if(callback) {
                     callback();
                  }
               });
         });

      dialog.title = "_#(js:New Folder)";
      dialog.label = "_#(js:Folder Name)";
      dialog.helpLinkKey = "DataSourceNewFolder";
      dialog.validators = [
         Validators.required,
         FormValidators.invalidAssetItemName,
         FormValidators.assetNameStartWithCharDigit,
      ];
      dialog.validatorMessages = [
         {validatorName: "required", message: "_#(js:data.datasets.folderNameRequired)"},
         {validatorName: "invalidAssetItemName", message: "_#(js:data.datasets.folderNameInvalid)"},
         {validatorName: "assetNameStartWithCharDigit", message: "_#(js:asset.tree.checkStart)"}
      ];
      dialog.hasDuplicateCheck = (value: string) =>
         this.httpClient
            .post<CheckDuplicateResponse>(DATA_URI + "/isDuplicate",
               <CheckDuplicateRequest> {
                  path: currentFolderPath != "/" ? currentFolderPath + "/" : currentFolderPath,
                  newName: value,
                  type: AssetType.FOLDER,
                  scope: currentFolderPathScope ?  +currentFolderPathScope : null})
            .pipe(map(response => response.duplicate));
   }

   renameDataSourceFolder(node: TreeNodeModel, callback?: Function): void {
      let path = node.data.path;
      let parentFolderPath = "";

      if(path && path != "/" && path.includes("/")) {
         parentFolderPath = path.substring(0, path.lastIndexOf("/"));
      }

      this.handleDataSourceFolder(node, (data) =>
         this.dataSourceBrowserService.renameDataSourceFolder(data, parentFolderPath, callback, node));
   }

   renameWorksheetFolder(node: TreeNodeModel, callback?: Function): void {
      this.handleWorksheetFolder(node, (data) =>
         this.dataBrowserService.renameAsset(data,
            (type: string, message: string) => {
               node.loading = false;
               this.handleResponse(type, message, callback);
            },
            () => node.loading = false,
            () => node.loading = true));
   }

   deleteDataSourceFolder(node: TreeNodeModel, callback?: Function): void {
      this.handleDataSourceFolder(node, (data) =>
         this.dataSourceBrowserService.deleteDataSourceFolder(data,
            (type: string, message: string) => this.handleResponse(type, message, callback)));
   }

   deleteWorksheetFolder(node: TreeNodeModel, callback?: Function): void {
      this.handleWorksheetFolder(node, (data) =>
         this.dataBrowserService.deleteAsset(data,
            (type: string, message: string) => this.handleResponse(type, message, callback)));
   }

   deleteDataSource(node: TreeNodeModel, callback?: Function): void {
      this.dataSourceBrowserService.deleteDataSourceByNode(node,
         (type: string, message: string) => this.handleResponse(type, message, callback));
   }

   editWorksheet(node: TreeNodeModel, clientService: ViewsheetClientService): void {
      this.dataBrowserService.openWorksheet(this.getWorksheetInfo(node).id, clientService);
   }

   renameWorksheet(node: TreeNodeModel, callback?: Function): void {
      const fallbackAsset = this.getWorksheetInfo(node);

      this.fetchWorksheetInfo(fallbackAsset).subscribe({
         next: (asset) => this.dataBrowserService.renameAsset(asset || fallbackAsset,
            (type: string, message: string) => this.handleResponse(type, message, callback)),
         error: () => this.dataBrowserService.renameAsset(fallbackAsset,
            (type: string, message: string) => this.handleResponse(type, message, callback))
      });
   }

   deleteWorksheet(node: TreeNodeModel, callback?: Function): void {
      this.dataBrowserService.deleteAsset(this.getWorksheetInfo(node),
         (type: string, message: string) => this.handleResponse(type, message, callback));
   }

   moveWorksheet(node: TreeNodeModel, callback?: Function): void {
      const fallbackAsset = this.getWorksheetInfo(node);

      this.fetchWorksheetInfo(fallbackAsset).subscribe({
         next: (asset) => this.openMoveWorksheetDialog(asset || fallbackAsset, callback),
         error: () => this.openMoveWorksheetDialog(fallbackAsset, callback)
      });
   }

   moveWorksheetFolder(node: TreeNodeModel, callback?: Function): void {
      this.handleWorksheetFolder(node, (data) => this.openMoveWorksheetFolderDialog(data, callback));
   }

   moveDataSource(node: TreeNodeModel, callback?: Function): void {
      const onSuccess = () => this.handleResponse("success", "", callback);

      if(node.type === "DATA_SOURCE_FOLDER") {
         this.handleDataSourceFolder(node, (data) =>
            this.dataSourceBrowserService.moveDataSource(data, this.getParentPath(data.path), onSuccess));
      }
      else {
         this.handleDataSource(node, (data) =>
            this.dataSourceBrowserService.moveDataSource(data, this.getParentPath(data.path), onSuccess));
      }
   }

   materializeWorksheet(node: TreeNodeModel, callback?: Function): void {
      const worksheet = this.getWorksheetInfo(node);
      const dialog: AnalyzeMVDialog = ComponentTool.showDialog(this.modalService, AnalyzeMVDialog,
         () => {
            if(callback) {
               callback();
            }

            this.dataBrowserService.changeMV();
         },
         {backdrop: "static", windowClass: "analyze-mv-dialog"},
         () => this.dataBrowserService.changeMV());
      dialog.selectedNodes = [new MVTreeModel(worksheet.path, worksheet.id,
         RepositoryEntryType.WORKSHEET, worksheet.scope === AssetEntryHelper.USER_SCOPE)];
   }

   handleDataSourceFolder(node: TreeNodeModel, executeActionFun: Function): void {
      const uri = DATASOURCE_FOLDER_URI + Tool.encodeURIComponentExceptSlash(node.data.path);

      this.httpClient.get(uri).subscribe((data: DataSourceInfo) => {
         if(!!data && executeActionFun) {
            executeActionFun(data);
         }
      });
   }

   handleDataSource(node: TreeNodeModel, executeActionFun: Function): void {
      const uri = DATASOURCE_URI + Tool.encodeURIComponentExceptSlash(node.data.path);
      const fallbackData: DataSourceInfo = {
         name: node?.label,
         path: node?.data?.path,
         type: {
            name: node?.type || PortalDataType.DATA_SOURCE,
            label: node?.type || PortalDataType.DATA_SOURCE
         },
         createdBy: "",
         createdDate: 0,
         createdDateLabel: "",
         dateFormat: "YYYY-MM-DD HH:mm:ss",
         editable: node?.data?.properties?.[DatasourceTreeAction.EDIT] === "true",
         deletable: node?.data?.properties?.[DatasourceTreeAction.DELETE] === "true",
         queryCreatable: node?.data?.properties?.queryCreatable !== "false",
         hasSubFolder: false
      };

      this.httpClient.get(uri).subscribe(
         (data: DataSourceInfo) => {
            if(executeActionFun) {
               const mergedData: DataSourceInfo = {
                  ...fallbackData,
                  ...data,
                  path: node?.data?.path,
                  name: data?.name || fallbackData.name,
                  type: data?.type || fallbackData.type,
                  editable: data?.editable ?? fallbackData.editable,
                  deletable: data?.deletable ?? fallbackData.deletable
               };
               executeActionFun(mergedData);
            }
         },
         () => {
            if(executeActionFun) {
               executeActionFun(fallbackData);
            }
         }
      );
   }

   handleWorksheetFolder(node: TreeNodeModel, executeActionFun: Function): void {
      const uri = SPECIFIED_FOLDER_URI + Tool.encodeURIComponentExceptSlash(node.data.path);

      this.httpClient.get(uri, {params: new HttpParams().set("scope", node.data.scope)})
         .subscribe((data: WorksheetBrowserInfo) => {
            if(!!data && executeActionFun) {
               executeActionFun(data);
            }
         });
   }

   handleResponse(type: string, message: string, callback?: Function): void {
      if(type == "success" && callback) {
         callback();
      }
   }

   private getWorksheetInfo(node: TreeNodeModel): WorksheetBrowserInfo {
      return {
         name: node?.label,
         path: node?.data?.path,
         type: AssetType.WORKSHEET,
         scope: node?.data?.scope,
         description: "",
         id: node?.data?.identifier,
         createdBy: "",
         createdDate: 0,
         createdDateLabel: "",
         modifiedDate: 0,
         dateFormat: "",
         modifiedDateLabel: "",
         editable: node?.data?.properties?.[DatasourceTreeAction.EDIT] === "true",
         deletable: node?.data?.properties?.[DatasourceTreeAction.DELETE] === "true",
         materialized: !!node?.materialized,
         canMaterialize: node?.data?.properties?.[DatasourceTreeAction.MATERIALIZE] === "true",
         canWorksheet: node?.data?.properties?.[DatasourceTreeAction.EDIT] === "true",
         parentPath: this.getParentPath(node?.data?.path),
         parentFolderCount: 0,
         hasSubFolder: 0,
         workSheetType: 0
      };
   }

   private fetchWorksheetInfo(asset: WorksheetBrowserInfo): Observable<WorksheetBrowserInfo> {
      let uri = PORTAL_DATA_BROWSER_URI;
      const parentPath = asset.parentPath && asset.parentPath !== "/" ? asset.parentPath : "";

      if(parentPath) {
         uri += "/" + Tool.encodeURIComponentExceptSlash(parentPath);
      }

      const params = new HttpParams().set("scope", "" + asset.scope);

      return this.httpClient.get<PortalDataBrowserModel>(uri, {params}).pipe(
         map((model) => model?.files?.find((file) =>
            file.path === asset.path && file.scope === asset.scope && file.type === AssetType.WORKSHEET) || asset)
      );
   }

   private openMoveWorksheetDialog(asset: WorksheetBrowserInfo, callback?: Function): void {
      const parentPath = asset.parentPath || this.getParentPath(asset.path);
      const grandparentFolder = parentPath === "/" ? FAKE_ROOT_PATH :
         parentPath.indexOf("/") !== -1 ? parentPath.substring(0, parentPath.lastIndexOf("/")) : "/";
      const dialog = ComponentTool.showDialog(this.modalService, MoveAssetDialogComponent,
         (result: [string, number]) => {
            const movePath = result[0];
            const moveScope = result[1];

            if((movePath !== asset.path && movePath !== parentPath) || moveScope !== asset.scope) {
               const params = {
                  params: new HttpParams()
                     .set("assetScope", "" + asset.scope)
                     .set("targetScope", "" + moveScope)
               };

               this.httpClient.post(DATA_URI + "/move",
                  new MoveCommand(movePath, asset.path, asset.name, asset.id, asset.modifiedDate),
                  params)
                  .subscribe({
                     next: () => this.handleResponse("success", "", callback),
                     error: () => ComponentTool.showMessageDialog(this.modalService,
                        "_#(js:Error)", "_#(js:data.datasets.moveDataSetError)")
                  });
            }
         }, {size: "lg", backdrop: "static"});
      dialog.originalPaths = [asset.path];
      dialog.items = [asset];
      dialog.parentPath = parentPath;
      dialog.grandparentFolder = grandparentFolder;
      dialog.parentScope = asset.scope;
   }

   private openMoveWorksheetFolderDialog(asset: WorksheetBrowserInfo, callback?: Function): void {
      const parentPath = asset.parentPath || this.getParentPath(asset.path);
      const grandparentFolder = parentPath === "/" ? FAKE_ROOT_PATH :
         parentPath.indexOf("/") !== -1 ? parentPath.substring(0, parentPath.lastIndexOf("/")) : "/";
      const dialog = ComponentTool.showDialog(this.modalService, MoveAssetDialogComponent,
         (result: [string, number]) => {
            const movePath = result[0];
            const moveScope = result[1];

            if((movePath !== asset.path && movePath !== parentPath) || moveScope !== asset.scope) {
               const params = {
                  params: new HttpParams()
                     .set("assetScope", "" + asset.scope)
                     .set("targetScope", "" + moveScope)
               };

               this.httpClient.post(FOLDER_URI + "/move",
                  new MoveCommand(movePath, asset.path, asset.name, asset.id, asset.modifiedDate),
                  params)
                  .subscribe({
                     next: () => this.handleResponse("success", "", callback),
                     error: () => ComponentTool.showMessageDialog(this.modalService,
                        "_#(js:Error)", "_#(js:data.datasets.moveFolderError)")
                  });
            }
         }, {size: "lg", backdrop: "static"});
      dialog.originalPaths = [asset.path];
      dialog.items = [asset];
      dialog.parentPath = parentPath;
      dialog.grandparentFolder = grandparentFolder;
      dialog.parentScope = asset.scope;
   }

   private getParentPath(path: string): string {
      if(!path || path === "/" || !path.includes("/")) {
         return "/";
      }

      return path.substring(0, path.lastIndexOf("/")) || "/";
   }
}
