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
import {
   Component,
   EventEmitter,
   Input,
   OnChanges,
   Output,
   SimpleChanges
} from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { RepositoryEditorModel } from "../../../../../../../shared/util/model/repository-editor-model";
import { TableInfo } from "../../../../common/util/table/table-info";
import { RepositoryRecycleBinTableModel } from "./repository-folder-recycle-bin-table-model";
import { MessageDialog, MessageDialogType } from "../../../../common/util/message-dialog";
import { MatDialog, MatDialogConfig } from "@angular/material/dialog";
import { RepositoryFolderRecycleBinSettingsModel } from "./repository-folder-recycle-bin-settings-model";

export interface RepositoryFolderRecycleBinModel extends RepositoryEditorModel {
   recycleNodes: RepositoryRecycleBinTableModel[];
   overwrite: boolean;
}

const RESTORE_RECYCLE_BIN_ENTRYS: string = "../api/em/content/repository/folder/recycleBin/restore";
const DELETE_RECYCLE_BIN_ENTRYS: string = "../api/em/content/repository/folder/recycleBin/delete";

@Component({
   selector: "em-repository-folder-recycle-bin-page",
   templateUrl: "./repository-folder-recycle-bin-page.component.html"
})
export class RepositoryFolderRecycleBinPageComponent implements OnChanges {
   @Input() model: RepositoryFolderRecycleBinModel;
   @Input() smallDevice: boolean;
   @Output() cancel = new EventEmitter<void>();
   @Output() editorChanged = new EventEmitter<void>();
   @Output() unsavedChanges = new EventEmitter<boolean>();
   recycleNodes: RepositoryRecycleBinTableModel[];
   reportsTableInfo: TableInfo = {
      selectionEnabled: true,
      title: "",
      columns: [
         {header: "_#(js:Original Path)", field: "originalPath"},
         {header: "_#(js:Delete) _#(js:Date)", field: "dateDeleted"},
         {header: "_#(js:Type)", field: "type"},
         {header: "_#(js:Original User)", field: "originalUser"}],
      actions: []
   };

   constructor(private http: HttpClient, private dialog: MatDialog) {
   }

   ngOnChanges(changes: SimpleChanges): void {
      if(changes.model) {
         if(this.model.recycleNodes) {
            this.recycleNodes = this.model.recycleNodes;
         }
      }
   }

   restoreAssets(reports: RepositoryRecycleBinTableModel[]) {
      const model: RepositoryFolderRecycleBinSettingsModel =
         {table: reports, overwrite: this.model.overwrite};
      this.http.post(RESTORE_RECYCLE_BIN_ENTRYS, model).subscribe(
         (message) => {
            if(message) {
               this.dialog.open(MessageDialog, <MatDialogConfig>{
                  data: {
                     title: "_#(js:Warning)",
                     content: message,
                     type: MessageDialogType.WARNING
                  }
               });
            }

            this.editorChanged.emit();
         }
      );
   }

   setOverwrite(overwrite: boolean) {
      this.model.overwrite = overwrite;
   }

   removeAssets(reports: RepositoryRecycleBinTableModel[]) {
      let content = "_#(js:em.common.items.deleteRecycleBinNode)";

      this.dialog.open(MessageDialog, <MatDialogConfig>{
         width: "350px",
         data: {
            title: "_#(js:Confirm)",
            content: content,
            type: MessageDialogType.CONFIRMATION
         }
      }).afterClosed().subscribe(confirmed => {
         if(confirmed) {
            this.removeAssetsDirectly(reports);
         }
      });
   }

   removeAssetsDirectly(reports: RepositoryRecycleBinTableModel[]) {
      const model: RepositoryFolderRecycleBinSettingsModel =
         {table: reports, overwrite: this.model.overwrite};
      this.http.post(DELETE_RECYCLE_BIN_ENTRYS, model).subscribe(
         (message) => {
            if(message) {
               this.dialog.open(MessageDialog, <MatDialogConfig>{
                  data: {
                     title: "_#(js:Warning)",
                     content: message,
                     type: MessageDialogType.WARNING
                  }
               });
            }

            this.editorChanged.emit();
         }
      );
   }
}
