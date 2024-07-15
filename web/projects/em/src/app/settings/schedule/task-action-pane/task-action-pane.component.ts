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
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { BatchActionModel } from "../../../../../../shared/schedule/model/batch-action-model";
import { GeneralActionModel } from "../../../../../../shared/schedule/model/general-action-model";
import { ScheduleActionModel } from "../../../../../../shared/schedule/model/schedule-action-model";
import { TaskActionPaneModel } from "../../../../../../shared/schedule/model/task-action-pane-model";
import { BackupActionModel } from "../../../../../../shared/schedule/model/backup-action-model";

export interface TaskActionChanges {
   valid: boolean;
   model: ScheduleActionModel;
}

@Component({
   selector: "em-schedule-task-action-pane",
   templateUrl: "./task-action-pane.component.html",
   styleUrls: ["./task-action-pane.component.scss"]
})
export class TaskActionPaneComponent {
   @Output() modelChanged = new EventEmitter<TaskActionChanges>();
   @Input() originalTaskName: string;

   @Input()
   set model(value: TaskActionPaneModel) {
      this._model = value;
   }

   get model(): TaskActionPaneModel {
      return this._model;
   }

   @Input()
   get action(): ScheduleActionModel {
      return this._action;
   }

   set action(value: ScheduleActionModel) {
      if(value) {
         this.selectedActionType = value.actionType;
         this._action = Object.assign({}, value);
      }
      else {
         this.selectedActionType = "RepletAction";
         this._action = <GeneralActionModel>{
            label: "_#(js:New Action)",
            actionType: this.selectedActionType,
            actionClass: "GeneralActionModel"
         };
      }
   }

   selectedActionType = "RepletAction";
   private _model: TaskActionPaneModel;
   private _action: ScheduleActionModel;

   changeActionType() {
      if(this.selectedActionType === "BackupAction") {
         this._action = <BackupActionModel>{
            label: "_#(js:New Action)",
            actionType: this.selectedActionType,
            actionClass: "BackupActionModel",
            backupPathsEnabled: true,
            backupPath: null,
            assets: []
         };
      }
      else if(this.selectedActionType === "BatchAction") {
         this._action = <BatchActionModel>{
            label: "_#(js:New Action)",
            actionType: this.selectedActionType,
            actionClass: "BatchActionModel",
            taskName: null
         };
      }
      else {
         this._action = <GeneralActionModel>{
            label: "_#(js:New Action)",
            actionType: this.selectedActionType,
            actionClass: "GeneralActionModel"
         };
      }

      this.fireModelChanged(false);
   }

   onModelChanged(value: TaskActionChanges) {
      this.action = value.model;
      this.fireModelChanged(value.valid);
   }

   fireModelChanged(valid: boolean): void {
      this.modelChanged.emit({
         valid,
         model: this.action
      });
   }
}
