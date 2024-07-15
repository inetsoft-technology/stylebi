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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { AssetType } from "../../../../../../shared/data/asset-type";
import { HttpClient, HttpParams } from "@angular/common/http";
import { Observable } from "rxjs";
import { map } from "rxjs/operators";
import { FAKE_ROOT_PATH } from "../move-datasource-dialog/move-datasource-dialog.component";
import { AssetItem } from "../model/datasources/database/asset-item";
import { DataModelBrowserModel } from "../data-datasource-browser/datasources-database/database-data-model-browser/data-model-browser-model";
import { DataModelBrowserViewModel } from "../model/data-model-browser-view-model";

const ROOT_LABEL: string = "_#(js:Data Model)";
const GET_DATA_MODEL_URI: string = "../api/data/database/dataModel/folder/browser";

@Component({
  selector: "move-data-model-dialog",
  templateUrl: "./move-data-model-dialog.component.html"
})
export class MoveDataModelDialog implements OnInit {
  @Input() database: string;
  @Output() onCommit = new EventEmitter<string>();
  @Output() onCancel = new EventEmitter<string>();
  folderPath: string;
  AssetType = AssetType;

  private readonly fakeRootFolder: AssetItem = {
    type: "data_model_folder",
    id: null,
    path: FAKE_ROOT_PATH,
    urlPath: null,
    name: "_#(js:Data Model)",
    createdBy: "",
    description: "",
    createdDate: new Date().getDate(),
    editable: false,
    deletable: false,
    createdDateLabel: ""
  };

  constructor(private httpClient: HttpClient)
  {
  }

  ngOnInit(): void {
  }

  public openFolderRequest: (path: string, assetType?: string, scope?: number) => Observable<DataModelBrowserViewModel> =
     (value: string, assetType?: string, scope?: number) => {
       let params = new HttpParams().set("database", this.database);

       if(value === FAKE_ROOT_PATH) {
         params = params.set("root", "true");
       }

       return this.httpClient.get(GET_DATA_MODEL_URI, { params: params }).pipe(
          map((model: DataModelBrowserModel) => {
            return <DataModelBrowserViewModel> {
              path: [this.fakeRootFolder].concat(model.currentFolder),
              folders: model.dataModelList,
            };
          }));
     };

  /**
   * Set the folder path to move to, to the currently selected folder.
   * @param items   the selected items on the files browser
   */
  folderSelected(items: AssetItem[]): void {
    this.folderPath = items?.length > 0 ? items[0].path : null;
  }

  get rootLabel(): string {
    return ROOT_LABEL;
  }

  /**
   * returning the new folder path to move to.
   */
  public ok(): void {
    this.onCommit.emit(this.folderPath);
  }

  /**
   * Close dialog without changing anything.
   */
  cancel(): void {
    this.onCancel.emit("cancel");
  }
}
