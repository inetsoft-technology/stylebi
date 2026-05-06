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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Observable } from "rxjs";
import { CheckItemsDuplicateCommand } from "../commands/check-items-duplicate-command";
import { DataFolderBrowserModel } from "../model/data-folder-browser-model";
import { WorksheetBrowserInfo } from "../model/worksheet-browser-info";
import { Tool } from "../../../../../../shared/util/tool";
import { map } from "rxjs/operators";
import { ComponentTool } from "../../../common/util/component-tool";
import { AssetType } from "../../../../../../shared/data/asset-type";
import { MoveAssetDialogDataConfig } from "../data-folder-browser/move-asset-dialog-data-config";
import { PortalDataBrowserModel } from "../data-folder-browser/portal-data-browser-model";
import { AssetEntryHelper } from "../../../common/data/asset-entry-helper";

export const FAKE_ROOT_PATH: string = "_fake_root_";
const DATA_WORKSHEET_ROOT_PATH: string = "_data_worksheet_root_";

@Component({
   selector: "move-asset-dialog",
   templateUrl: "move-asset-dialog.component.html",
   styleUrls: ["move-asset-dialog.component.scss"]
})
export class MoveAssetDialogComponent implements OnInit {
   @Input() originalPaths: string[] = [];
   @Input() parentPath: string = "/";
   @Input() parentScope: number = 1;
   @Input() grandparentFolder: string = "/";
   @Input() multi: boolean = false;
   @Input() items: WorksheetBrowserInfo[] = [];
   @Output() onCommit = new EventEmitter<[string, number]>();
   @Output() onCancel = new EventEmitter<string>();
   folderPath: string;
   folderScope: number;
   AssetType = AssetType;
   readonly fakeRootPath = FAKE_ROOT_PATH;

   private readonly fakeRootFolder: WorksheetBrowserInfo = {
      type: AssetType.FOLDER,
      path: FAKE_ROOT_PATH,
      name: "_#(js:data.datasets.home)",
      scope: this.parentScope,
      description: "",
      createdBy: "",
      createdDate: new Date().getDate(),
      createdDateLabel: "",
      modifiedDate: new Date().getDate(),
      modifiedDateLabel: "",
      dateFormat: "YYYY-MM-DD HH:mm:ss",
      editable: false,
      deletable: false,
      materialized: false,
      canMaterialize: false,
      canWorksheet: false
   };

   private readonly dataWorksheetFolder: WorksheetBrowserInfo = {
      type: AssetType.FOLDER,
      path: DATA_WORKSHEET_ROOT_PATH,
      name: "_#(Data Worksheet)",
      scope: AssetEntryHelper.GLOBAL_SCOPE,
      description: "",
      createdBy: "",
      createdDate: new Date().getDate(),
      createdDateLabel: "",
      modifiedDate: new Date().getDate(),
      modifiedDateLabel: "",
      dateFormat: "YYYY-MM-DD HH:mm:ss",
      editable: false,
      deletable: false,
      materialized: false,
      canMaterialize: false,
      canWorksheet: false,
      hasSubFolder: 1
   };

   constructor(private httpClient: HttpClient,
               private modalService: NgbModal,
               public config: MoveAssetDialogDataConfig) {
   }

   ngOnInit(): void {
      this.folderPath = this.parentPath;
      this.folderScope = this.parentScope;
   }

   public openFolderRequest: (path: string, assetType?: string, scope?: number) => Observable<DataFolderBrowserModel> =
      (value: string, assetType?: string, scope?: number) => {
         const folderConfig = this.config.openFolderRequestConfig;
         let params = new HttpParams();
         const isWorksheetRoot = value === DATA_WORKSHEET_ROOT_PATH;
         const requestPath = isWorksheetRoot ? "" : value;

         if(Tool.isNumber(scope)) {
            params = params.set("scope", "" + scope);
         }

         if(value === FAKE_ROOT_PATH) {
            params = params.set("home", true + "");
         }

         let moveFolders  = "";
         this.items.forEach((item, index) => {
            let op = index == 0 ? "" : ";";
            moveFolders  += op + item.path;
         });
         params = params.set("moveFolders", moveFolders);

         return this.httpClient.get(folderConfig.folderBrowserURI
            + (requestPath ? Tool.encodeURIComponentExceptSlash(requestPath) : ""), { params: params }).pipe(
            map((model: PortalDataBrowserModel) => {
               if(value === FAKE_ROOT_PATH) {
                  return <DataFolderBrowserModel> {
                     path: [this.fakeRootFolder],
                     root: true,
                     folders: [{...this.dataWorksheetFolder}],
                     files: []
                  };
               }

               if(isWorksheetRoot) {
                  const folders = model.folders
                     .filter((folder) => this.originalPaths.indexOf(folder.path) === -1
                        || folder.scope !== this.parentScope);

                  return <DataFolderBrowserModel> {
                     path: [this.fakeRootFolder, {...this.dataWorksheetFolder}],
                     root: false,
                     folders: folders,
                     files: []
                  };
               }

               // don't show folders that are being moved
               const folders = model.folders
                  .filter((folder) => this.originalPaths.indexOf(folder.path) === -1
                     || folder.scope !== this.parentScope);
               return <DataFolderBrowserModel> {
                  path: [this.fakeRootFolder, {...this.dataWorksheetFolder}]
                     .concat(model.currentFolder),
                  root: model.root,
                  folders: folders,
                  files: [] //only show folders
               };
            }));
      };

   /**
    * Set the folder path to move to, to the currently selected folder.
    * @param items   the selected items on the files browser
    */
   folderSelected(items: WorksheetBrowserInfo[]): void {
      if(items.length === 0) {
         this.folderPath = null;
         this.folderScope = null;
      }
      else {
         this.folderPath = items[0].path === DATA_WORKSHEET_ROOT_PATH ? "/" : items[0].path;
         this.folderScope = items[0].scope;
      }
   }

   /**
    * If the items being moved are folder types.
    * @returns {boolean}   true if folder type item or multi move
    */
   isFolder(): boolean {
      return this.multi || (this.items.length > 0 && this.items[0].type === AssetType.FOLDER);
   }

   /**
    * Check for duplicate names in the new folder path. If there are no duplicated close dialog
    * returning the new folder path to move to.
    */
   public ok(): void {
      const config = this.config.okConfig;
      let httpParams = new HttpParams();

      if(Tool.isNumber(this.parentScope)) {
         httpParams = httpParams.set("assetScope", "" + this.parentScope);
      }

      if(Tool.isNumber(this.folderScope)) {
         httpParams = httpParams.set("targetScope", "" + this.folderScope);
      }

      this.httpClient.post(config.checkDuplicateURI,
         new CheckItemsDuplicateCommand(this.items, this.folderPath), { params: httpParams })
         .subscribe(
            (duplicate: boolean) => {
               if(duplicate) {
                  let errorMessage: string;

                  if(this.multi) {
                     errorMessage = config.duplicateTargetFolderExists;
                  }
                  else {
                     errorMessage = this.isFolder() ? config.duplicateTargetFolder :
                        config.duplicateTargetAssetType;
                  }

                  ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)", errorMessage);
               }
               else {
                  this.onCommit.emit([this.folderPath, this.folderScope]);
               }
            }
         );
   }

   /**
    * Close dialog without changing anything.
    */
   cancel(): void {
      this.onCancel.emit("cancel");
   }
}
