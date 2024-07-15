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
import {HttpClient, HttpParams} from "@angular/common/http";
import {Component, EventEmitter, Input, OnChanges, Output, SimpleChanges} from "@angular/core";
import {Tool} from "../../../../../../../shared/util/tool";
import { convertToKey, IdentityId } from "../../../security/users/identity-id";
import {RepositorySheetSettingsModel} from "./repository-sheet-settings.model";
import {RepositoryEditorModel} from "../../../../../../../shared/util/model/repository-editor-model";
import {MatSnackBar} from "@angular/material/snack-bar";
import {RepositoryTreeNode} from "../repository-tree-node";

export interface RepositoryWorksheetEditorModel extends RepositoryEditorModel {
   worksheetSettings: RepositorySheetSettingsModel;
   owner: IdentityId;
}

@Component({
   selector: "em-repository-worksheet-settings-page",
   templateUrl: "./repository-worksheet-settings-page.component.html",
   styleUrls: ["./repository-worksheet-settings-page.component.scss"]
})
export class RepositoryWorksheetSettingsPageComponent implements OnChanges {
   @Input() model: RepositoryWorksheetEditorModel;
   @Input() selectedTab = 0;
   @Input() editingNode: RepositoryTreeNode;
   @Input() smallDevice: boolean;
   @Input() hasMVPermission: boolean;
   @Output() cancel = new EventEmitter<void>();
   @Output() selectedTabChanged = new EventEmitter<number>();
   @Output() editorChanged = new EventEmitter<string>();
   @Output() mvChanged = new EventEmitter<void>();
   @Output() unsavedChanges = new EventEmitter<boolean>();
   sheetModel: RepositorySheetSettingsModel;

   constructor(private http: HttpClient, private snackBar: MatSnackBar) {
   }

   ngOnChanges(changes: SimpleChanges): void {
      if(changes.model) {
         this.sheetModel = this.model.worksheetSettings;
         this.sheetModel.oname = this.sheetModel.name;
      }
   }

   editSheet(model: RepositorySheetSettingsModel) {
      const url = "../api/em/content/repository/worksheet";
      const params = new HttpParams().set("path", Tool.byteEncode(this.model.path));
      this.http.post(url, model, {
         params: this.model.owner != null ? params.set("owner", convertToKey(this.model.owner)) : params
      }).subscribe(
         (newModel: RepositorySheetSettingsModel) => {
            this.editorChanged.emit(newModel ? newModel.name : null);
         },
         (error) => {
            if(error.error.type === "DuplicateNameException") {
               this.snackBar.open("_#(js:Duplicate Name)", null, {duration: Tool.SNACKBAR_DURATION});
            }

            this.editorChanged.emit(null);
      });
   }
}
