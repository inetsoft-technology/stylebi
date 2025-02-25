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
import { DataSourceInfo } from "../model/data-source-info";
import { Tool } from "../../../../../../shared/util/tool";
import { map } from "rxjs/operators";
import { ComponentTool } from "../../../common/util/component-tool";
import { CheckMoveDuplicateRequest } from "../commands/check-move-duplicate-request";
import { CheckDuplicateResponse } from "../commands/check-duplicate-response";
import { AssetType } from "../../../../../../shared/data/asset-type";
import { MoveAssetDialogDataConfig } from "../data-folder-browser/move-asset-dialog-data-config";
import { DataSourceBrowserModel } from "../data-datasource-browser/data-source-browser-model";
import { DataSourceBrowserViewModel } from "../model/data-source-browser-view-model";
import { PortalDataType } from "../data-navigation-tree/portal-data-type";

export const FAKE_ROOT_PATH: string = "_fake_root_";
const ROOT_LABEL: string = "_#(js:Data Source)";
const GET_DATA_SOURCE_URI: string = "../api/data/datasources/browser";
const CHECK_MOVE_DUPLICATE_URI: string = "../api/data/datasources/move/checkDuplicate";

@Component({
   selector: "move-datasource-dialog",
   templateUrl: "move-datasource-dialog.component.html"
})
export class MoveDataSourceDialogComponent implements OnInit {
   @Input() originalPaths: string[] = [];
   @Input() parentPath: string = "/";
   @Input() parentScope: number = 1;
   @Input() grandparentFolder: string = "/";
   @Input() multi: boolean = false;
   @Input() items: DataSourceInfo[] = [];
   @Output() onCommit = new EventEmitter<string>();
   @Output() onCancel = new EventEmitter<string>();
   folderPath: string;
   folderScope: number;
   AssetType = AssetType;

   private readonly fakeRootFolder: DataSourceInfo = {
      name: "_#(js:data.datasets.home)",
      path: FAKE_ROOT_PATH,
      type: {
         name: PortalDataType.DATA_SOURCE_FOLDER,
         label: PortalDataType.DATA_SOURCE_FOLDER
      },
      createdBy: "",
      createdDate: new Date().getDate(),
      createdDateLabel: "",
      dateFormat: "YYYY-MM-DD HH:mm:ss",
      editable: false,
      deletable: false,
   };

   constructor(private httpClient: HttpClient,
               private modalService: NgbModal,
               public config: MoveAssetDialogDataConfig) {
   }

   ngOnInit(): void {
      this.folderPath = null;
      this.folderScope = this.parentScope;
   }

   public openFolderRequest: (path: string, assetType?: string, scope?: number) => Observable<DataSourceBrowserViewModel> =
      (value: string, assetType?: string, scope?: number) => {
         let params = new HttpParams();

         if(value === FAKE_ROOT_PATH) {
            params = params.set("root", "true");
         }
         else if(!!value && value != "/") {
            params = params.set("path", value);
         }

         let moveFolders = "";
         this.items.forEach((item, index) => {
            let op = index == 0 ? "" : ";";
            moveFolders += op + item.path;
         });
         params = params.set("moveFolders", moveFolders);

         return this.httpClient.get(GET_DATA_SOURCE_URI, { params: params }).pipe(
            map((model: DataSourceBrowserModel) => {
               // don't show folders that are being moved and files
               const folders = model.dataSourceList.filter(
                  (folder) => {
                     return this.originalPaths.indexOf(folder.path) === -1 &&
                        folder.type.name === PortalDataType.DATA_SOURCE_FOLDER;
                  });
               return <DataSourceBrowserViewModel> {
                  path: [this.fakeRootFolder].concat(model.currentFolder),
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
   folderSelected(items: DataSourceInfo[]): void {
      if(items.length === 0) {
         this.folderPath = null;
         this.folderScope = null;
      }
      else {
         this.folderPath = items[0].path;
      }
   }

   /**
    * If the items being moved are folder types.
    * @returns {boolean}   true if folder type item or multi move
    */
   isFolder(): boolean {
      return this.multi ||
         (this.items.length > 0 && this.items[0].type.name === PortalDataType.DATA_SOURCE_FOLDER);
   }

   get rootLabel(): string {
      return ROOT_LABEL;
   }

   /**
    * Check for duplicate names in the new folder path. If there are no duplicated close dialog
    * returning the new folder path to move to.
    */
   public ok(): void {
      let checkMoveDuplicateRequest: CheckMoveDuplicateRequest = {
         items: this.items
      };

      if(this.folderPath !== "/") {
         checkMoveDuplicateRequest.path = this.folderPath;
      }

      this.httpClient.post(CHECK_MOVE_DUPLICATE_URI, checkMoveDuplicateRequest)
         .subscribe(
            (res: CheckDuplicateResponse) => {
               if(res.duplicate) {
                  let errorMessage: string = "_#(js:common.duplicateName)";
                  ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)", errorMessage);
               }
               else {
                  this.onCommit.emit(this.folderPath);
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
