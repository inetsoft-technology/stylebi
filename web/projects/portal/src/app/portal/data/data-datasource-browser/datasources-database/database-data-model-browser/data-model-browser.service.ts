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
import { EventEmitter, Injectable } from "@angular/core";
import { RenameFolderRequest } from "../../../commands/rename-folder-request";
import { PortalDataType } from "../../../data-navigation-tree/portal-data-type";
import {
  InputNameDescDialog,
  NameDescResult
} from "../../../input-name-desc-dialog/input-name-desc-dialog.component";
import { RenameModelEvent } from "../../../model/datasources/database/events/rename-model-event";
import { HttpClient, HttpParams } from "@angular/common/http";
import { ExpandStringDirective } from "../../../../../widget/expand-string/expand-string.directive";
import { ComponentTool } from "../../../../../common/util/component-tool";
import { StringWrapper } from "../../../model/datasources/database/string-wrapper";
import { CheckDependenciesEvent } from "../../../model/datasources/database/events/check-dependencies-event";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ActivatedRoute, Router } from "@angular/router";
import { Observable, of } from "rxjs";
import { InputNameDialog } from "../../../../../widget/dialog/input-name-dialog/input-name-dialog.component";
import { FormValidators } from "../../../../../../../../shared/util/form-validators";
import { ValidatorFn, Validators } from "@angular/forms";
import { MoveDataModelDialog } from "../../../move-data-model-dialog/move-data-model-dialog.component";
import { MoveDataModelEvent } from "../../../model/datasources/database/events/move-data-model-event";
import { ChoseAdditionalConnectionDialog } from "../database-physical-model/chose-additional-connection-dialog/chose-additional-connection-dialog.component";
import { Tool } from "../../../../../../../../shared/util/tool";
import { ChosePhysicalViewDialog } from "../database-physical-model/chose-physical-view-dialog/chose-physical-view-dialog.component";
import { AddFolderRequest } from "../../../commands/add-folder-request";
import { RemoveDataModelEvent } from "../../../model/datasources/database/events/remove-data-model-event";
import { DatabaseAsset } from "../../../model/datasources/database/database-asset";
import { AssetEntry } from "../../../../../../../../shared/data/asset-entry";
import { AssetEntryHelper } from "../../../../../common/data/asset-entry-helper";
import { TreeNodeModel } from "../../../../../widget/tree/tree-node-model";

const PHYSICAL_MODEL_CHECK_DUPLICATE_URI: string = "../api/data/logicalModel/checkDuplicate";
const LOGICAL_MODEL_CHECK_DUPLICATE_URI: string = "../api/data/logicalModel/checkDuplicate";
const VPM_CHECK_DUPLICATE_URI: string = "../api/data/vpm/checkDuplicate";
const PHYSICALMODEL_RENAME_URI: string = "../api/data/physicalmodel/rename";
const LOGICALMODEL_RENAME_URI: string = "../api/data/logicalmodel/rename";
const VPM_RENAME_URI: string = "../api/data/vpm/rename";
const PHYSICAL_MODELS_URI: string = "../api/data/physicalmodel/models";
const LOGICAL_MODELS_URI: string = "../api/data/logicalmodel/models";
const LOGICAL_MODEL_CHECK_DEPENDENCIES_URI: string = "../api/data/logicalmodel/checkOuterDependencies";
const DATA_MODEL_FOLDER_URI = "../api/portal/data/database/dataModelFolder";
const DATA_MODEL_FOLDER_CHECK_DEPENDENCIES_URI: string = "../api/data/database/dataModelFolder/checkOuterDependencies";
const DATA_MODEL_FOLDER_CHECK_DUPLICATE_URI = "../api/portal/data/database/dataModelFolder/duplicateCheck";
const MOVE_DATA_MODEL_URI = "../api/data/database/dataModel/move";
const REMOVE_DATA_MODEL_URI = "../api/data/database/dataModel/remove";

@Injectable()
export class DataModelBrowserService {
   ModelNameValidators: ValidatorFn[] = [
      Validators.required,
      FormValidators.notWhiteSpace,
      FormValidators.matchReservedModelName,
      FormValidators.invalidDataModelName
   ];

   validatorMessages = [
      {
         validatorName: "required",
         message: "_#(js:data.model.nameRequired)"
      },
      {
         validatorName: "notWhiteSpace",
         message: "_#(js:viewer.notWhiteSpace)"
      },
      {
         validatorName: "invalidDataModelName",
         message: "_#(js:data.model.nameInvalid)"
      },
      {
         validatorName: "matchReservedModelName",
         message: "_#(js:common.datasource.reservedWords)"
      }
   ];

   private _modelChanged = new EventEmitter<any>();

   constructor(private httpClient: HttpClient,
               protected modalService: NgbModal,
               private route: ActivatedRoute,
               private router: Router)
   {
   }

   changed(): Observable<any> {
      return this._modelChanged.asObservable();
   }

   emitChanged(): void {
      this._modelChanged.emit();
   }

  renameDataModelFolder(databaseName: string, folderName: string, callback?: Function,
                        withFolderName?: boolean, node?: TreeNodeModel): void
  {
    let title: string = "_#(js:Rename)";
    let label: string = "_#(js:Name)";
    let onCommit = (data) => {
       if(node) {
          node.loading = true;
       }

      let req: RenameFolderRequest = new RenameFolderRequest(data,
         withFolderName ? databaseName : databaseName + "/" + folderName);

      this.httpClient.put(DATA_MODEL_FOLDER_URI, req)
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
    };

    let index: number = databaseName.lastIndexOf("/" + folderName);
    let databasePath = withFolderName && index != -1 ? databaseName.substring(0, index) : databaseName;
    let dialogProcess = this.getDataModelNameDialogProcess(databasePath, folderName, title, label);
    this.showNameInputDialog(onCommit, dialogProcess, false);
  }

  renamePhysicalView(originalName: string, database: string, desc: string,
                     folder?: string, callback?: Function): void
  {
    const onCommit: (value: any) => any = this.getRenameModelCommit(originalName, database,
       PortalDataType.PARTITION, folder, callback);
    const processDialog: (dialog: InputNameDescDialog) => void = (dialog) => {
      dialog.value = originalName;
      dialog.validators = this.ModelNameValidators;
      dialog.hasDuplicateCheck = this.hasModelDuplicateCheck(originalName, database,
         PortalDataType.PARTITION);
      dialog.title = "_#(js:data.datasources.renamePhysicalModel)";
      dialog.label = "_#(js:data.physicalmodel.modelName)";
      dialog.description = desc;
      dialog.validatorMessages = this.validatorMessages;
      dialog.duplicateMessage = "_#(js:data.physicalmodel.modelNameDuplicate)";
    };

    this.showNameInputDialog(onCommit, processDialog);
  }

  renameLogicalModel(originalName: string, database: string, desc: string,
                     folder: string, callback?: Function)
  {
    const onCommit: (value: any) => any = this.getRenameModelCommit(originalName, database,
       PortalDataType.LOGIC_MODEL, folder, callback);

    const processDialog: (dialog: InputNameDescDialog) => void = (dialog) => {
      dialog.value = originalName;
      dialog.validators = this.ModelNameValidators;
      dialog.hasDuplicateCheck = this.hasModelDuplicateCheck(originalName, database,
         PortalDataType.LOGIC_MODEL);
      dialog.title = "_#(js:data.logicalmodel.renameLogicalModel)";
      dialog.label = "_#(js:data.logicalmodel.modelName)";
      dialog.description = desc;
      dialog.validatorMessages = this.validatorMessages;
      dialog.duplicateMessage = "_#(js:data.logicalmodel.modelNameDuplicate)";
    };

    this.showNameInputDialog(onCommit, processDialog);
  }

   deleteModels(databaseName: string, items: DatabaseAsset[], callback?: Function): void {
      if(items?.length == 0) {
         return;
      }
      let title = "_#(js:Remove)";
      let message = "_#(js:data.dataModel.confirmRemoveItems)";

      ComponentTool.showConfirmDialog(this.modalService, title, message)
         .then((buttonClicked) => {
            if(buttonClicked === "ok") {
               let event = <RemoveDataModelEvent> {
                 database: databaseName,
                 items: items
               };

               this.httpClient.post(REMOVE_DATA_MODEL_URI, event)
                  .subscribe((msg: StringWrapper) => {
                     if(callback) {
                        callback();
                     }

                     if(msg?.body) {
                        ComponentTool.showMessageDialog(this.modalService, "_#(js:Warning)",
                           msg.body);
                     }
                  });
            }
         });
   }

   deleteDataModelFolder(databaseName: string, folderName: string, callback?: Function): void {
      let title = "_#(js:data.dataModelFolder.remove)";
      let message = ExpandStringDirective.expandString(
         "_#(js:data.dataModelFolder.confirmDeleteFolder)", [folderName]);

      ComponentTool.showConfirmDialog(this.modalService, title, message)
         .then((buttonClicked) => {
            if(buttonClicked !== "ok") {
               return;
            }

            let event = new CheckDependenciesEvent();
            event.databaseName = databaseName;
            event.dataModelFolder = folderName;

            this.httpClient.post(DATA_MODEL_FOLDER_CHECK_DEPENDENCIES_URI, event)
               .subscribe((result: any) => {
                  if(!!result && !!result.body) {
                     message = result.body;

                     ComponentTool.showConfirmDialog(this.modalService, title, message)
                        .then((button) => {
                           if(button === "ok") {
                              this.deleteDataModelFolder0(databaseName, folderName, callback);
                           }
                        });
                  }
                  else {
                     this.deleteDataModelFolder0(databaseName, folderName, callback);
                  }
               });
         });
   }

  private deleteDataModelFolder0(databaseName: string, folderName: string, callback?: Function): void {
     let params: HttpParams = new HttpParams()
        .set("databasePath", databaseName)
        .set("folderName", folderName);

     this.httpClient.delete(DATA_MODEL_FOLDER_URI, { params: params})
        .subscribe((msg: StringWrapper) => {
           if(callback) {
              callback();
           }

           if(msg?.body) {
              ComponentTool.showMessageDialog(this.modalService, "_#(js:Warning)", msg.body);
           }
        });
  }

  deletePhysicalView(originalName: string, database: string, parent?: string,
                     folder?: string, callback?: Function): void
  {
    let title = !parent ? "_#(js:data.physicalmodel.removeModel)" :
       "_#(js:data.physicalmodel.removeExtendedModel)";
    let msg = ExpandStringDirective.expandString(
       "_#(js:data.physicalmodel.confirmRemoveModel)", [originalName]);
    ComponentTool.showConfirmDialog(this.modalService, title, msg)
       .then((buttonClicked) => {
         if(buttonClicked === "ok") {
           let params: HttpParams = new HttpParams()
              .set("database", database)
              .set("name", originalName);

           if(parent) {
             params = params.set("parent", parent);
           }

           if(!!folder) {
              params = params.set("folder", folder);
           }

           this.httpClient.delete<boolean>(PHYSICAL_MODELS_URI, { params: params })
              .subscribe(
                 data => {
                   if(data) {
                     if(callback) {
                        callback();
                     }
                   }
                   else {
                     ComponentTool.showMessageDialog(this.modalService, title,
                        "_#(js:data.physicalmodel.removeFailed)");
                   }
                 },
                 err => {
                 }
              );
         }
       });
  }

  deleteLogicalModel(originalName: string, database: string,
                     extendModel: DatabaseAsset[],
                     parent?: string,
                     folder?: string,
                     callback?: Function): void
  {
    let event = new CheckDependenciesEvent();
    event.databaseName = database;
    event.parent = parent;
    event.modelName = originalName;

    this.httpClient.post(LOGICAL_MODEL_CHECK_DEPENDENCIES_URI, event)
       .subscribe((result: any) => {
         if(!!result) {
           ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)", result.body,
              {"yes": "_#(js:Yes)", "no": "_#(js:No)"})
              .then((btn) => {
                if(btn == "yes") {
                  if(extendModel?.length > 0) {
                    let extendedNames = extendModel.map((child) => child.name).join(",");
                    ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)",
                       "_#(js:Extended Logical Model)" + "_#(js:common.datasource.goonAndmodelsDeleted)"
                       + "_*" + extendedNames, {"yes": "_#(js:Yes)", "no": "_#(js:No)"})
                       .then((button) => {
                         if(button == "yes") {
                           this.deleteLogicalModel0(originalName, database, parent, null,
                              callback);
                         }
                       });
                  }
                  else {
                    this.deleteLogicalModel0(originalName, database, parent, folder, callback);
                  }
                }
              });
         }
         else {
           let title = !parent ? "_#(js:data.logicalmodel.removeModel)" :
              "_#(js:data.logicalmodel.removeExtendedModel)";
           let msg = ExpandStringDirective.expandString(
              "_#(js:data.logicalmodel.confirmRemoveModel)", [originalName]);
           ComponentTool.showConfirmDialog(this.modalService, title, msg)
              .then((buttonClicked) => {
                if(buttonClicked === "ok") {
                   this.deleteLogicalModel0(originalName, database, parent, folder, callback);
                }
              });
         }
       });
  }

   private deleteLogicalModel0(originalName: string, database: string,
                               parent?: string, folder?: string, callback?: Function)
   {
      let params: HttpParams = new HttpParams()
         .set("database", database)
         .set("name", originalName);

      if(!!folder) {
         params = params.set("folder", folder);
      }

      if(parent) {
         params = params.set("parent", parent);
      }

      this.httpClient.delete(LOGICAL_MODELS_URI, { params: params })
         .subscribe(
            data => {
               if(callback) {
                  callback();
               }
            },
            err => {
            }
         );
   }

   addDataModelFolder(databaseName: string, callback?: Function) {
      let title: string = "_#(js:New Folder)";
      let label: string = "_#(js:Folder Name)";

      let onCommit = (data) => {
         let req = new AddFolderRequest(data, databaseName, null);

         this.httpClient.post(DATA_MODEL_FOLDER_URI, req).subscribe(() => {
            if(callback) {
               callback();
            }
         });
      };

      let dialogProcess = this.getDataModelNameDialogProcess(databaseName, "", title, label);
      this.showNameInputDialog(onCommit, dialogProcess, false);
   }

   addLogicalModel(databaseName: string, physicalModel?: string, folder?: string) {
      const commit: (value: any) => any  = this.getAddModelCommit(databaseName,
         PortalDataType.LOGIC_MODEL, physicalModel, folder);
      const dialogProcess: (dilog: InputNameDescDialog) => void = dialog => {
         dialog.hasDuplicateCheck = this.hasModelDuplicateCheck(null, databaseName,
            PortalDataType.VPM);
         dialog.validators = this.ModelNameValidators;
         dialog.title = "_#(js:data.physicalmodel.newLogicalModel)";
         dialog.label = "_#(js:data.logicalmodel.modelName)";
         dialog.validatorMessages = this.validatorMessages;
         dialog.duplicateMessage = "_#(js:data.logicalmodel.modelNameDuplicate)";
         dialog.helpLinkKey = "CreateLogicalModel";
      };
      this.showNameInputDialog(commit, dialogProcess);
   }

   addPhysicalView(databaseName: string, folder?: string) {
      const commit: (value: any) => any =
         this.getAddModelCommit(databaseName, PortalDataType.PARTITION, null, folder);
      const dialogProcess: (dilog: InputNameDescDialog) => void = dialog => {
         dialog.hasDuplicateCheck = this.hasModelDuplicateCheck(null, databaseName,
            PortalDataType.VPM);
         dialog.validators = this.ModelNameValidators;
         dialog.title = "_#(js:data.datasources.newPhysicalView)";
         dialog.label = "_#(js:data.physicalmodel.modelName)";
         dialog.validatorMessages = this.validatorMessages;
         dialog.duplicateMessage = "_#(js:data.physicalmodel.modelNameDuplicate)";
         dialog.helpLinkKey = "CreatePhysicalView";
      };

      this.showNameInputDialog(commit, dialogProcess);
   }

   addVPM(database: string) {
      const commit: (value: NameDescResult) => any  = this.getAddModelCommit(database, PortalDataType.VPM);
      const dialogProcess: (dilog: InputNameDescDialog) => void = dialog => {
         dialog.hasDuplicateCheck = this.hasModelDuplicateCheck(null, database,
            PortalDataType.VPM);
         dialog.title = "_#(js:data.datasources.newVPM)";
         dialog.label = "_#(js:data.vpm.modelName)";
         dialog.validators = this.ModelNameValidators;
         dialog.validatorMessages = this.validatorMessages;
         dialog.duplicateMessage = "_#(js:data.vpm.modelNameDuplicate)";
         dialog.helpLinkKey = "CreateVPM";
      };
      this.showNameInputDialog(commit, dialogProcess);
   }

   addExtendModel(databaseName: string, parent: string, physicalModel: string,
                  isView: boolean, folder: string): void
   {
      if(!databaseName || !parent) {
         return;
      }

      const dialog = ComponentTool.showDialog(this.modalService, ChoseAdditionalConnectionDialog,
         (con: string) => {
            let routePath: string = "/portal/tab/data/datasources/database";
            let isDefault = ChoseAdditionalConnectionDialog.default_connection === con;

            let params = {
               create: true,
               desc: "",
               parent: parent,
               folder: folder
            };

            if(!isDefault) {
               params["connection"] = con;
            }

            if(isView) {
               this.router.navigate([routePath, Tool.byteEncode(databaseName),
                  "physicalModel", Tool.byteEncode(con), params]);
            }
            else {
               this.router.navigate([routePath, Tool.byteEncode(databaseName),
                  "physicalModel", Tool.byteEncode(physicalModel), "logicalModel",
                  Tool.byteEncode(con), params]);
            }
         },
         { backdrop: "static" });

      dialog.database = databaseName;
      dialog.physicalView = physicalModel;
      dialog.parent = parent;
      dialog.isView = isView;
   }

   moveModels(items: DatabaseAsset[], callback?: Function): void {
     if(items?.length == 0) {
        return;
     }

     let database = items[0]?.databaseName;
     let dialog = ComponentTool.showDialog(this.modalService, MoveDataModelDialog,
         (folderName) => {
            this.moveModelsToTarget(items, folderName, callback);

         }, { size: "lg", backdrop: "static" });

      dialog.database = database;
   }

   moveModelsToTarget(items: DatabaseAsset[], folder: string, callback?: Function): void {
      if(items?.length == 0) {
         return;
      }

      let database = items[0]?.databaseName;
      let event: MoveDataModelEvent  = {
         items: items,
         folder: folder,
         database: database
      };

      this.httpClient.post(MOVE_DATA_MODEL_URI, event).subscribe(() => {
            if(callback) {
               callback();
            }
         },
         (error) => {
            let message = "_#(js:data.dataModelFolder.moveItemError)";

            if(error?.status === 403) {
               message = "_#(js:data.datasets.moveTargetPermissionError)";
            }

            ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)", message);
         });
   }

   createDataBaseAssets(assets: AssetEntry[]): DatabaseAsset[] {
      const items: DatabaseAsset[] = [];

      for(let asset of assets) {
         items.push({
            databaseName: "",
            type: "",
            id: asset.identifier,
            path: asset.path,
            urlPath: "",
            name: AssetEntryHelper.getEntryName(asset),
            createdBy: asset.createdUsername,
            description: "",
            createdDate: 0,
            editable: false,
            deletable: false,
            createdDateLabel: ""
         });
      }

      return items;
   }

   getRenameModelCommit(originalName: string, database: string, type: string,
                        folder?: string, callback?: Function): (value: NameDescResult) => any
   {
      const onCommit: (value: NameDescResult) => any =
         (result: NameDescResult) => {
            let renameUri: string;

            if(type == PortalDataType.PARTITION) {
               renameUri = PHYSICALMODEL_RENAME_URI;
            }
            else if(type == PortalDataType.LOGIC_MODEL) {
               renameUri = LOGICALMODEL_RENAME_URI;
            }
            else if(type == PortalDataType.VPM) {
               renameUri = VPM_RENAME_URI;
            }

            let renameEvent: RenameModelEvent = new RenameModelEvent(database, folder,
               originalName, result.name.trim(), result.description);
            this.httpClient.put(renameUri, renameEvent)
               .subscribe(
                  data => {
                     if(callback) {
                        callback();
                     }
                  },
                  err => {
                  }
               );
         };

      return onCommit;
   }

   private showNameInputDialog<D>(onCommit: (value: any) => any, process: (dilog) => void,
                                  desc: boolean = true): void
   {
      let comp: any = desc ? InputNameDescDialog : InputNameDialog;
      const dialog: any = ComponentTool.showDialog(this.modalService, comp, onCommit,
         { backdrop: "static" });
      dialog.helpLinkKey = "DataSourceNewFolder";

      if(!!process) {
         process(dialog);
      }
   }

   hasModelDuplicateCheck(originalName: string, database: string, type: string): (value: string) => Observable<boolean> {
      const duplicateCheck: (value: string) => Observable<boolean> = (value: string) => {
         let checkUri = this.getDuplicateCheckUri(type);
         let params: HttpParams = new HttpParams()
            .set("database", database)
            .set("name", value);

         return this.httpClient.get<boolean>(checkUri, { params: params });
      };

      const hasDuplicateCheck: (value: string) => Observable<boolean> =
         (value: string) => {
            if(value != originalName) {
               return duplicateCheck(value);
            }
            else {
               return of(false);
            }
         };

      return hasDuplicateCheck;
   }

   getDataModelNameDialogProcess(databasePath?: string, value?: string, title?: string,
                                 label?: string): (dialog: any) => void {
      return (dialog) => {
         dialog.value = value;
         dialog.title = title;
         dialog.label = label;
         dialog.validators = [
            FormValidators.required,
            FormValidators.isValidDataSourceFolderName
         ];
         dialog.validatorMessages = [
            {
               validatorName: "required",
               message: "_#(js:data.datasets.folderNameRequired)"
            },
            {
               validatorName: "containsSpecialCharsForName",
               message: "_#(js:data.datasets.folderNameInvalid)"
            }
         ];

         dialog.hasDuplicateCheck = (name) => {
            let params = new HttpParams()
               .set("databasePath", databasePath)
               .set("name", name);

            return this.httpClient.get(DATA_MODEL_FOLDER_CHECK_DUPLICATE_URI,
               { params: params});
         };
      };
   }

   getAddModelCommit(database: string, type: string, physicalView?: string,
                     folder?: string): (value: NameDescResult) => any
   {
      const onCommit: (value: NameDescResult) => any =
         (result: NameDescResult) => {
            let routePath: string = "/portal/tab/data/datasources/database";
            let routeParams: any = {
               create: true,
               desc: result.description
            };

            if(folder) {
               routeParams.folder = folder;
            }

            if(type == PortalDataType.VPM) {
               this.router.navigate([routePath, "vpm",
                     Tool.byteEncode(database) + "/" + Tool.byteEncode(result.name), routeParams],
                  { relativeTo: this.route });
            }
            else if(type == PortalDataType.PARTITION) {
               this.router.navigate([routePath, Tool.byteEncode(database), "physicalModel",
                  Tool.byteEncode(result.name), routeParams ]);
            }
            else if(type == PortalDataType.LOGIC_MODEL) {
               this.routeToNewPhysicalView(result.name, database, physicalView, routePath,
                  routeParams);
            }
         };

      return onCommit;
   }

   private routeToNewPhysicalView(name: string, database: string, physicalView: string,
                                  routePath: string, routeParams: any)
   {
      let routeFun = (view) => {
         if(view) {
            this.router.navigate([routePath, Tool.byteEncode(database),
               "physicalModel", Tool.byteEncode(view), "logicalModel",
               Tool.byteEncode(name), routeParams]);
         }
      };

      if(!physicalView) {
         let dialog =
            ComponentTool.showDialog(this.modalService, ChosePhysicalViewDialog, routeFun);
         dialog.database = database;
      }
      else {
         routeFun(physicalView);
      }
   }

   private getDuplicateCheckUri(type: string): string {
      if(type == PortalDataType.PARTITION) {
         return PHYSICAL_MODEL_CHECK_DUPLICATE_URI;
      }
      else if(type == PortalDataType.LOGIC_MODEL) {
         return LOGICAL_MODEL_CHECK_DUPLICATE_URI;
      }
      else if(type == PortalDataType.VPM) {
         return VPM_CHECK_DUPLICATE_URI;
      }

      return null;
   }
}
