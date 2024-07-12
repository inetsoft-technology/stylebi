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
import { HttpClient, HttpErrorResponse, HttpParams } from "@angular/common/http";
import { EventEmitter, Injectable } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { from, Observable } from "rxjs";
import { Tool } from "../../../../../../shared/util/tool";
import { AssetEntryHelper } from "../../../common/data/asset-entry-helper";
import { OpenComposerService } from "../../../common/services/open-composer.service";
import { ComponentTool } from "../../../common/util/component-tool";
import { GuiTool } from "../../../common/util/gui-tool";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { EditWorksheetEvent } from "../commands/edit-worksheet-event";
import { PortalDataType } from "../data-navigation-tree/portal-data-type";
import { AssetType } from "../../../../../../shared/data/asset-type";
import { CheckDuplicateRequest } from "../commands/check-duplicate-request";
import { CheckDuplicateResponse } from "../commands/check-duplicate-response";
import { catchError, filter, map, switchMap } from "rxjs/operators";
import { InputNameDialog } from "../../../widget/dialog/input-name-dialog/input-name-dialog.component";
import { FormValidators } from "../../../../../../shared/util/form-validators";
import { WorksheetBrowserInfo } from "../model/worksheet-browser-info";
import { AssetDependenciesResponse } from "../commands/asset-dependencies-response";
import { DeleteDataSetResponse } from "./data-folder-browser.component";

const FOLDER_URI: string = "../api/data/folders";
const DATA_URI: string = "../api/data/datasets";

@Injectable()
export class DataBrowserService {
   public folderChanged = new EventEmitter<any>();
   private _mvChanged = new EventEmitter<void>();

   constructor(private openComposerService: OpenComposerService,
               private modalService: NgbModal,
               private httpClient: HttpClient)
   {
   }

   /*
    * Open the worksheet in the composer
    */
   openWorksheet(wsId: string, client: ViewsheetClientService): void {
      this.openComposerService.composerOpen.subscribe(open => {
         if(open) {
            let event = new EditWorksheetEvent(wsId);
            client.sendEvent("/events/composer/editWorksheet", event);
         }
         else {
            let params: HttpParams = new HttpParams().set("wsId", decodeURIComponent(wsId));
            GuiTool.openBrowserTab("composer", params);
         }
      });
   }

   /*
    * Create a new worksheet in the composer
    */
   newWorksheet(folderId: string): void {
      this.openComposerService.composerOpen.subscribe(open => {
         if(open) {
            ComponentTool.showMessageDialog(this.modalService, "_#(js:Confirm)",
               "_#(js:composer.tabAlreadyOpened)");
         }
         else {
            const params: HttpParams = new HttpParams()
               .set("wsWizard", "true")
               .set("folder", Tool.byteEncode(folderId));
            GuiTool.openBrowserTab("composer", params);
         }
      });
   }

   get mvChanged(): EventEmitter<void> {
      return this._mvChanged;
   }

   changeMV(): void {
      this._mvChanged.emit();
   }

   changeFolder(path: string, scope: number): void {
      let folderType = scope == AssetEntryHelper.USER_SCOPE ?
         PortalDataType.PRIVATE_WORKSHEETS_FOLDER : PortalDataType.SHARED_WORKSHEETS_FOLDER;
      this.folderChanged.emit({path: path, type: folderType});
   }

   renameAsset(asset: WorksheetBrowserInfo, handleResponseFun?: Function,
               handleErrorFun?: Function, preFun?: Function): void
   {
      const isFolder: boolean = asset.type === AssetType.FOLDER;
      const baseUri = (isFolder ? FOLDER_URI : DATA_URI);

      let onCommit: (value: string) => void = (value: string) => {
         if(preFun) {
            preFun();
         }

         const body: CheckDuplicateRequest = {path: asset.path, newName: value,
            type: asset.type, scope: asset.scope};

         this.httpClient.post<CheckDuplicateResponse>(DATA_URI + "/isDuplicate", body)
            .pipe(
               catchError(error => !!handleErrorFun ? handleErrorFun(error) : null),
               filter((response: CheckDuplicateResponse) => !response.duplicate),
               switchMap(() => {
                  let url = baseUri + "/rename/" + Tool.encodeURIComponentExceptSlash(value);
                  return this.httpClient.post(url, asset);
               }),
            )
            .subscribe(
               () => {
                  if(handleResponseFun) {
                     handleResponseFun("success", "_#(js:data.datasets.renameSuccess)");
                  }
               },
               () =>  {
                  if(handleResponseFun) {
                     handleResponseFun("danger", "_#(js:data.datasets.renameError)");
                  }
               }
            );
      };

      const dialog = ComponentTool.showDialog(this.modalService, InputNameDialog, onCommit,
         {size: "lg", backdrop: "static"});
      dialog.value = asset.name;
      dialog.title = "_#(js:Rename)";
      dialog.label = isFolder ? "_#(js:Folder Name)" : "_#(js:Worksheet Name)";
      dialog.helpLinkKey = "DataSourceNewFolder";
      dialog.validators = [
         FormValidators.required,
         FormValidators.invalidAssetItemName,
         FormValidators.assetNameStartWithCharDigit,
         FormValidators.duplicateName(() => [asset.name])
      ];
      dialog.hasDuplicateCheck = (value: string) =>
         this.httpClient.post<CheckDuplicateResponse>(DATA_URI + "/isDuplicate",
            <CheckDuplicateRequest> {
               path: asset.path, newName: value,
               type: asset.type, scope: asset.scope
            })
            .pipe(map(response => response.duplicate));
      dialog.validatorMessages = [
         {
            validatorName: "required",
            message: isFolder ? "_#(js:data.datasets.folderNameRequired)" :
               "_#(js:data.datasets.dataSetNameRequired)"
         },
         {
            validatorName: "invalidAssetItemName",
            message: isFolder ? "_#(js:data.datasets.folderNameInvalid)" : "_#(js:data.datasets.dataSetNameInvalid)"
         },
         {
            validatorName: "assetNameStartWithCharDigit",
            message: "_#(js:asset.tree.checkStart)"
         }
      ];
   }

   deleteAsset(asset: WorksheetBrowserInfo, handleResponseFun?: Function,
               handleDeleteErrorFun?: Function): void
   {
      const isFolder: boolean = asset.type === AssetType.FOLDER;
      const encodedPath: string = Tool.encodeURIComponentExceptSlash(asset.path);
      const removableURI = (isFolder ? FOLDER_URI : DATA_URI) + "/removeableStatus/" + encodedPath;
      const removeURI = (isFolder ? FOLDER_URI : DATA_URI) + "/" + encodedPath;
      const params = new HttpParams().set("scope", asset.scope + "");

      this.httpClient.get<AssetDependenciesResponse>(removableURI, {params})
         .pipe(
            catchError((error: HttpErrorResponse) => handleDeleteErrorFun(error, isFolder)),
            switchMap((response: AssetDependenciesResponse) =>
               this.confirmDeletion(response.dependencies, isFolder)),
            filter(confirmation => !!confirmation && confirmation == "ok"))
         .subscribe(() => this.doDeleteAsset(removeURI, params, isFolder, handleResponseFun));
   }

   private doDeleteAsset(removeURI: string, params: HttpParams, isFolder: boolean,
                         handleResponseFun?: Function): void
   {
      if(isFolder) {
         this.httpClient.delete(removeURI, {params}).subscribe(() => {
            if(handleResponseFun) {
               handleResponseFun("success", "_#(js:data.datasets.deleteFolderSuccess)");
            }
         });
      }
      else {
         this.httpClient.delete<DeleteDataSetResponse>(removeURI, {params}).subscribe(response => {
            if(response.successful && handleResponseFun) {
               handleResponseFun("success", "_#(js:data.datasets.deleteWorksheetSuccess)");
            }
            else if(response.corrupt) {
               ComponentTool.showConfirmDialog(this.modalService, "_#(js:data.datasets.delete)",
                  "_#(js:data.datasets.deleteWorksheetCorrupt)")
                  .then(button => {
                     if(button === "ok") {
                        params = params.set("force", "true");
                        this.doDeleteAsset(removeURI, params, isFolder, handleResponseFun);
                     }
                  });
            }
            else if(handleResponseFun) {
               handleResponseFun("danger", "_#(js:data.datasets.deleteWorksheetError)");
            }
         });
      }
   }

   private confirmDeletion(dependencies: string[], isFolder: boolean): Observable<any> {
      let prompt: string = isFolder ? "_#(js:data.datasets.confirmDeleteFolder)" :
         "_#(js:data.datasets.confirmDeleteDataSet)";

      if(dependencies.length) {
         prompt += (isFolder ? "_#(js:data.datasets.folderUsedBy)" : "_#(js:data.datasets.sheetUsedBy)")
            + " " + dependencies.join(", ");
      }

      return from(ComponentTool.showConfirmDialog(this.modalService, "_#(js:data.datasets.delete)", prompt));
   }
}
