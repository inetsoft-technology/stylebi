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
   Input, OnChanges,
   Output, SimpleChanges
} from "@angular/core";
import {
   ScheduleTaskFolderSettingsModel
} from "../repository-schedule-task-folder-settings-page/schedule-task-folder-settings-model";
import { Tool } from "../../../../../../../shared/util/tool";

@Component({
   selector: "em-repository-schedule-task-folder-settings-view",
   templateUrl: "./repository-schedule-task-folder-settings-view.component.html"
})
export class RepositoryScheduleTaskFolderSettingsViewComponent implements OnChanges {
   @Input() model: ScheduleTaskFolderSettingsModel;
   @Output() cancel = new EventEmitter<void>();
   @Output() unsavedChanges = new EventEmitter<boolean>();
   @Output() folderSettingsChanged = new EventEmitter<ScheduleTaskFolderSettingsModel>();
   private _oldModel: ScheduleTaskFolderSettingsModel;

   ngOnChanges(changes: SimpleChanges): void {
      if(changes.model) {
         this._oldModel = Tool.clone(this.model);
      }
   }

   get disabled(): boolean {
      return Tool.isEquals(this.model, this._oldModel);
   }

   reset(): void {
      this.model = Tool.clone(this._oldModel);
   }

   apply(): void {
      this.folderSettingsChanged.emit(this.model);
   }
}
