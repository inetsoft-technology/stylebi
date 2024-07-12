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
import { Injectable } from "@angular/core";
import { EventEmitter } from "@angular/core";
import { NotificationData } from "../../../widget/repository-tree/repository-tree.service";
import { PortalDataType } from "../data-navigation-tree/portal-data-type";
import { Observable, Subject } from "rxjs";
import { map, shareReplay } from "rxjs/operators";
import { HttpClient, HttpErrorResponse, HttpParams } from "@angular/common/http";
import { DataSourceInfo } from "../model/data-source-info";
import { ComponentTool } from "../../../common/util/component-tool";
import {
   MoveDataSourceDialogComponent
} from "../move-datasource-dialog/move-datasource-dialog.component";
import { MoveCommand } from "../commands/move-command";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { AssetEntry } from "../../../../../../shared/data/asset-entry";
import { AssetEntryHelper } from "../../../common/data/asset-entry-helper";
import { AssetType } from "../../../../../../shared/data/asset-type";
import { InputNameDialog } from "../../../widget/dialog/input-name-dialog/input-name-dialog.component";
import { RenameFolderRequest } from "../commands/rename-folder-request";
import { FormValidators } from "../../../../../../shared/util/form-validators";
import { CheckDuplicateResponse } from "../commands/check-duplicate-response";
import { CheckDuplicateRequest } from "../commands/check-duplicate-request";
import { ExpandStringDirective } from "../../../widget/expand-string/expand-string.directive";
import {
   CheckDependenciesEvent
} from "../model/datasources/database/events/check-dependencies-event";
import { Tool } from "../../../../../../shared/util/tool";
import {
   ConnectionStatus
} from "../../../../../../em/src/app/settings/security/security-provider/security-provider-model/connection-status";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";

const PHYSICAL_TABLE_PERMISSION: string = "../api/data/datasources/physicalTable";
const DATASOURCE_MOVE_URI = "../api/data/datasources/move";
const DATASOURCE_FOLDER_URI = "../api/data/datasources/browser/folder";
const DATASOURCE_FOLDER_CHECKDUPLICATE = "../api/data/datasources/browser/folder/checkDuplicate";
const DATASOURCE_FOLDER_CHECK_DEPENDENCIES = "../api/data/datasources/browser/folder/checkOuterDependencies";
const DATASOURCE_CHECK_DEPENDENCIES: string = "../api/data/datasources/checkOuterDependencies";
const DATASOURCES_URI: string = "../api/data/datasources";

export interface CreateEventInfo {
   datasource: DataSourceInfo;
   vpm?: boolean;
}

@Injectable()
export class DatasourceBrowserService {
   /**
    * publish when the datasource of portal data has changed.
    */
   public datasourceChanged = new EventEmitter<void>();
   public folderChanged = new EventEmitter<any>();
   private physicalTablePermission: Observable<boolean>;
   onCreateEvent = new Subject<CreateEventInfo>();

   constructor(public http: HttpClient,
               private httpClient: HttpClient,
               private modalService: NgbModal) {
   }

   /**
    * refresh tree
    */
   refreshTree(): void {
      this.datasourceChanged.emit();
   }

   changeFolder(path: string): void {
      this.folderChanged.emit({path: path, type: PortalDataType.DATA_SOURCE_ROOT_FOLDER});
   }

   getPhysicalTablePermission(): Observable<boolean> {
      if(!this.physicalTablePermission) {
         this.physicalTablePermission = this.http.get<boolean>(PHYSICAL_TABLE_PERMISSION)
            .pipe(shareReplay(1));
      }

      return this.physicalTablePermission;
   }

   moveDataSource(datasource: DataSourceInfo, parentPath: string, callback?: Function,
                  errorCallback?: Function): void
   {
      if(!(datasource.editable && datasource.deletable)) {
         return;
      }

      const dialog = ComponentTool.showDialog(this.modalService, MoveDataSourceDialogComponent,
         (result: string) => {
            this.moveDataSourcesToFolder([datasource], result, callback, errorCallback);
         }, {size: "lg", backdrop: "static"});

      dialog.originalPaths = [datasource.path];
      dialog.items = [datasource];
      dialog.parentPath = parentPath || "/";
      dialog.grandparentFolder = dialog.parentPath.indexOf("/") !== -1 ?
         dialog.parentPath.substring(0, dialog.parentPath.lastIndexOf("/")) : "/";
   }

   moveSelected(datasources: DataSourceInfo[], parentPath: string, callback?: Function,
               errorCallback?: Function): void
   {
      const dialog = ComponentTool.showDialog(this.modalService, MoveDataSourceDialogComponent,
         (result: string) => {
            this.moveDataSourcesToFolder(datasources, result, callback, errorCallback);
         }, {size: "lg", backdrop: "static"});

      let dsPaths = [];
      datasources.forEach( (ds) => {
         dsPaths.push(ds.path);
      });

      dialog.originalPaths = dsPaths;
      dialog.items = datasources;
   }

   moveDataSourcesToFolder(datasources: DataSourceInfo[], targetFolder: string,
                           callback?: Function,
                           errorCallback?: Function): void
   {
      const moveCommands: MoveCommand[] = [];

      for(let datasource of datasources) {
         let newPath = targetFolder == "/" || targetFolder == "" ? datasource.name : targetFolder + "/" + datasource.name;
         moveCommands.push(new MoveCommand(newPath, datasource.path, datasource.name,
            datasource.path, datasource.createdDate, datasource.type.name));
      }

      this.moveDataSources0(moveCommands, callback, errorCallback);
   }

   createDataSourceInfos(assets: AssetEntry[]): DataSourceInfo[] {
      const infos: DataSourceInfo[] = [];

      for(let movingEntry of assets) {
         const parent = this.getParentPath(movingEntry);
         const name = parent == "/" ? movingEntry.path :
            movingEntry.path.substr(parent.length + 1);
         let typeName;

         if(movingEntry.type == AssetType.DATA_SOURCE_FOLDER) {
            typeName = PortalDataType.DATA_SOURCE_FOLDER;
         }
         else if(movingEntry.type == AssetType.DATA_SOURCE) {
            typeName = PortalDataType.DATABASE;
         }
         else  {
            continue;
         }

         infos.push({
            connected: false,
            createdBy: "",
            createdDate: 0,
            createdDateLabel: "",
            deletable: false,
            editable: false,
            hasSubFolder: false,
            name: name,
            path: movingEntry.path,
            queryCreatable: false,
            statusMessage: "",
            type: {name: typeName, label: ""}
         });
      }

      return infos;
   }

   renameDataSourceFolder(datasource: DataSourceInfo, dsParentFolderPath: string,
                          callback?: Function, node?: TreeNodeModel): void
   {
      if(!(datasource.editable && datasource.deletable)) {
         return;
      }

      let dialog = ComponentTool.showDialog(this.modalService, InputNameDialog,
         (newName: string) => {
            newName = newName.trim();

            // check if name has changed
            if(datasource.name != newName) {
               const request = new RenameFolderRequest(newName, datasource.path);

               if(node) {
                  node.loading = true;
               }

               this.http.post(DATASOURCE_FOLDER_URI, request)
                  .subscribe(() => {
                     if(node) {
                        node.loading = false;
                     }

                     if(callback) {
                        callback();
                     }
                  },
                  () => {
                     if(node) {
                        node.loading = false;
                     }
                  });
            }
         },
         {backdrop: "static"}
      );
      dialog.title = "_#(js:Rename)";
      dialog.label = "_#(js:Name)";
      dialog.helpLinkKey = "DataSourceNewFolder";
      dialog.value = datasource.name;
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
         this.http
            .post<CheckDuplicateResponse>(DATASOURCE_FOLDER_CHECKDUPLICATE,
               <CheckDuplicateRequest> {
                  path: dsParentFolderPath,
                  newName: value,
                  type: AssetType.DATA_SOURCE_FOLDER,
                  scope: null})
            .pipe(map(response => response.duplicate));
   }

   deleteDataSourceFolder(datasource: DataSourceInfo, callback?: Function): void {
      let event = new CheckDependenciesEvent();
      event.datasourceFolderPath = datasource.path;

      this.http.post(DATASOURCE_FOLDER_CHECK_DEPENDENCIES, event).subscribe((result: any) => {
         if(!!result) {
            ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)", result.body,
               {"yes": "_#(js:Yes)", "no": "_#(js:No)"})
               .then((btn) => {
                  if(btn == "yes") {
                     this.doDeleteFolder(datasource, true, callback);
                  }
               });
         }
         else {
            ComponentTool.showConfirmDialog(this.modalService, "_#(js:Delete)",
               "_#(js:data.datasets.confirmDeleteFolder)")
               .then(buttonClicked => {
                  if (buttonClicked === "ok") {
                     this.doDeleteFolder(datasource, false, callback);
                  }
               });
         }
      });
   }

   private doDeleteFolder(datasource: DataSourceInfo, force: boolean = false, callback?: Function): void {
      let params = new HttpParams().set("force", force + "");
      this.http.delete(DATASOURCE_FOLDER_URI + "/"
         + Tool.encodeURIComponentExceptSlash(datasource.path), {
         params: params
      }).subscribe(
         (connectionStatus: ConnectionStatus) => {
            if(!!connectionStatus && !!connectionStatus.status && !force) {
               ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)",
                  connectionStatus.status).then((confirm) => {
                  if(confirm == "ok") {
                     // force delete
                     this.doDeleteFolder(datasource, true, callback);
                  }
               });
            }
            else if(!!connectionStatus && !!connectionStatus.status && force) {
               if(callback) {
                  callback("danger", "_#(js:data.datasources.deleteDataSourceFolderError)");
               }
            }
            else {
               if(callback) {
                  callback("success", "_#(js:data.datasources.deleteDataSourceFolderSuccess)");
               }
            }
         },
         () => {
            if(callback) {
               callback("danger", "_#(js:data.datasources.deleteDataSourceFolderError)");
            }
         }
      );
   }

   /**
    * Confirm then send request to delete a datasource.
    * @param datasource the data source to delete
    * @param callback
    */
   deleteDataSourceByInfo(datasource: DataSourceInfo, callback?: Function): void {
      this.deleteDataSource(datasource.name, datasource.path, callback);
   }

   /**
    * Confirm then send request to delete a datasource.
    * @param datasource the data source to delete
    * @param callback
    */
   deleteDataSourceByNode(node: TreeNodeModel, callback?: Function): void {
      let entry: AssetEntry = node.data;

      this.deleteDataSource(AssetEntryHelper.getEntryName(entry), entry.path, callback);
   }

   /**
    * Confirm then send request to delete a datasource.
    * @param datasource the data source to delete
    * @param callback
    */
   deleteDataSource(name: string, path: string, callback?: Function): void {
      let event = new CheckDependenciesEvent();
      event.databaseName = name;

      this.httpClient.post(DATASOURCE_CHECK_DEPENDENCIES, event).subscribe((result: any) => {
         if(!!result) {
            ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)", result.body,
               {"yes": "_#(js:Yes)", "no": "_#(js:No)"})
               .then((btn) => {
                  if(btn == "yes") {
                     this.deleteDataSource0(name, path, true, callback);
                  }
               });
         }
         else {
            ComponentTool.showConfirmDialog(this.modalService, "_#(js:Delete)",
               "_#(js:data.datasources.confirmDeleteDataSource)")
               .then(buttonClicked => {
                  if (buttonClicked === "ok") {
                     this.deleteDataSource0(name, path, false, callback);
                  }
               });
         }
      });
   }

   /**
    * Confirm then send request to delete a datasource.
    * @param datasource the data source to delete
    * @param force
    * @param callback
    */
   deleteDataSource0(name: string, path: string, force: boolean = false, callback): void {
      let params = new HttpParams().set("force", force + "");
      params = params.set("name", name);

      this.httpClient.delete(DATASOURCES_URI + "/"
         + Tool.encodeURIComponentExceptSlash(path), {params: params})
         .subscribe(
            (connectionStatus: ConnectionStatus) => {
               if(!!connectionStatus && !!connectionStatus.status && !force) {
                  ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)",
                     connectionStatus.status).then((confirm) => {
                     if(confirm == "ok") {
                        // force delete
                        this.deleteDataSource0(name, path, true, callback);
                     }
                  });
               }
               else if(!!connectionStatus && !!connectionStatus.status && force) {
                  if(callback) {
                     callback("danger", "_#(js:data.datasources.deleteDataSourceError)");
                  }
               }
               else {
                  if(callback) {
                     callback("success", "_#(js:data.datasources.deleteDataSourceSuccess)");
                  }
               }

            },
            () => {
               if(callback) {
                  callback("danger", "_#(js:data.datasources.deleteDataSourceError)");
               }
            }
         );
   }

   private getParentPath(entry: AssetEntry): string {
      return this.getParentPath0(entry.path);
   }

   private getParentPath0(path: string): string {
      if(path === "/") {
         return null;
      }

      let index = path.lastIndexOf("/");
      return index >= 0 ? path.substring(0, index) : "/";
   }

   private moveDataSources0(moveCommands: MoveCommand[], callback?: Function,
                            errorCallback?: Function)
   {
      this.http.post(DATASOURCE_MOVE_URI, moveCommands)
         .subscribe((res) => {
               if((<any> res)?.message) {
                  this.showMessage((<any> res)?.message);
               }
               else if(callback) {
                  callback();
               }
            },
            (error: HttpErrorResponse) => {
               if(errorCallback) {
                  errorCallback(error);
               }
            });
   }

   private showMessage(message: string) {
      ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)", message, {"ok": "_#(js:OK)"},
         {backdrop: false });
   }
}
