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
import {HttpClient, HttpErrorResponse, HttpParams} from "@angular/common/http";
import {Component, EventEmitter, Input, OnChanges, Output, SimpleChanges} from "@angular/core";
import {MatSnackBar} from "@angular/material/snack-bar";
import {Tool} from "../../../../../../../shared/util/tool";
import {RepositoryEditorModel} from "../../../../../../../shared/util/model/repository-editor-model";
import { convertToKey, IdentityId } from "../../../security/users/identity-id";
import {RepositorySheetSettingsModel} from "../repository-worksheet-settings-page/repository-sheet-settings.model";
import {RepositoryTreeNode} from "../repository-tree-node";

export interface RepositoryViewsheetEditorModel extends RepositoryEditorModel {
   viewsheetSettings: RepositorySheetSettingsModel;
   owner: IdentityId;
}

@Component({
   selector: "em-repository-viewsheet-settings-page",
   templateUrl: "./repository-viewsheet-settings-page.component.html",
   styleUrls: ["./repository-viewsheet-settings-page.component.scss"]
})
export class RepositoryViewsheetSettingsPageComponent implements OnChanges {
   @Input() model: RepositoryViewsheetEditorModel;
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

   constructor(private http: HttpClient,
               private snackBar: MatSnackBar)
   {
   }

   ngOnChanges(changes: SimpleChanges): void {
      if(changes.model) {
         this.sheetModel = this.model.viewsheetSettings;
         this.sheetModel.oname = this.sheetModel.name;
      }
   }

   editSheet(model: RepositorySheetSettingsModel) {
      const url = "../api/em/content/repository/viewsheet";
      let params = new HttpParams()
         .set("path", Tool.byteEncode(this.model.path))
         .set("timeZone", Intl.DateTimeFormat().resolvedOptions().timeZone);
      params = this.model.owner != null ? params.set("owner", convertToKey(this.model.owner)) : params;
      this.http.post<RepositorySheetSettingsModel>(url, model, {params})
         .subscribe(
            (settings) => this.editorChanged.emit(settings ? settings.name : null),
            err => this.handleEditSheetError(err)
         );
   }

   private handleEditSheetError(error: HttpErrorResponse) {
      if(error.error.type === "DuplicateNameException") {
         this.snackBar.open("_#(js:Duplicate Name)", null, {duration: Tool.SNACKBAR_DURATION});
      }
      else if(error.error.type !== "MissingResourceException") {
         this.snackBar.open("_#(js:em.repository.viewsheet.editError)", null, {duration: Tool.SNACKBAR_DURATION});
         console.error("Failed to save changes to viewsheet: ", error);
      }

      this.editorChanged.emit(null);
   }

}
