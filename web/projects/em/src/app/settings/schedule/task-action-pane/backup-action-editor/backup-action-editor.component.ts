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
   Output
} from "@angular/core";
import { ServerPathInfoModel } from "../../../../../../../portal/src/app/vsobjects/model/server-path-info-model";
import { TaskActionPaneModel } from "../../../../../../../shared/schedule/model/task-action-pane-model";
import { TaskActionChanges } from "../task-action-pane.component";
import { BackupActionModel } from "../../../../../../../shared/schedule/model/backup-action-model";
import { BackupPathsSave } from "../backup-file/backup-file.component";

@Component({
   selector: "em-backup-action-editor",
   templateUrl: "./backup-action-editor.component.html",
   styleUrls: ["./backup-action-editor.component.scss"]
})
export class BackupActionEditorComponent {
   @Output() modelChanged = new EventEmitter<TaskActionChanges>();

   @Input()
   set model(value: TaskActionPaneModel) {
      this._taskModel = Object.assign({}, value);
   }

   get model(): TaskActionPaneModel {
      return this._taskModel;
   }

   @Input()
   set actionModel(value: BackupActionModel) {
      this._actionModel = Object.assign({}, value);
   }

   get actionModel(): BackupActionModel {
      return this._actionModel;
   }

   private _taskModel: TaskActionPaneModel;
   private _actionModel: BackupActionModel;
   private backupPathsValid = true;

   get modelValid(): boolean {
      return !!this._actionModel.assets &&
         this._actionModel.assets.length > 0 && this.backupPathsValid;
   }

   onBackupPathsChanged(change: BackupPathsSave): void {
      this.backupPathsValid = change.valid;
      this.actionModel.backupPathsEnabled = change.enabled;
      this.actionModel.backupPath = change.path;
      this.actionModel.assets = change.assets;

      let serverPathModel: ServerPathInfoModel = {
         path: change.path,
         ftp: change.ftp,
         useCredential: change.useCredential,
         secretId: change.secretId,
         username: change.username,
         password: change.password,
         oldFormat: -1,
      };

      this.actionModel.backupServerPath = serverPathModel;
      this.fireModelChanged();
   }

   fireModelChanged(): void {
      this.modelChanged.emit({
         valid: this.modelValid,
         model: this.actionModel
      });
   }
}
