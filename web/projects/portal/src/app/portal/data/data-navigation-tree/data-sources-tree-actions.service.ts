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
import { Observable, Subject } from "rxjs";
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
import { DatasourceBrowserService } from "../data-datasource-browser/datasource-browser.service";
import { DataBrowserService } from "../data-folder-browser/data-browser.service";
import { WorksheetBrowserInfo } from "../model/worksheet-browser-info";
import { DataSourceInfo } from "../model/data-source-info";

const DATASOURCE_FOLDER_ADD = "../api/data/datasources/browser/folder/add";
const DATASOURCE_FOLDER_CHECKDUPLICATE = "../api/data/datasources/browser/folder/checkDuplicate";
const DATA_URI: string = "../api/data/datasets";
const FOLDER_URI: string = "../api/data/folders";
const SPECIFIED_FOLDER_URI: string = "../api/data/folders/folder/";
const DATASOURCE_FOLDER_URI = "../api/data/datasources/folder/";
const DATASOURCE_URI = "../api/portal/data/datasources/";


@Injectable()
export class DataSourcesTreeActionsService {
   private worksheetShowDetails: Subject<any> = new Subject<any>();

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

      this.httpClient.get(uri).subscribe((data: DataSourceInfo) => {
         if(!!data && executeActionFun) {
            data.path = node.data.path;
            executeActionFun(data);
         }
      });
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

   showWSFolderDetailsSubject(): Observable<any> {
      return this.worksheetShowDetails.asObservable();
   }

   showWSFolderDetails(path: any): void {
      return this.worksheetShowDetails.next(path);
   }
}
