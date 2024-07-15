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
import { MatDialog } from "@angular/material/dialog";
import { finalize } from "rxjs/operators";
import { BatchActionModel } from "../../../../../../../shared/schedule/model/batch-action-model";
import { ScheduleTaskList } from "../../../../../../../shared/schedule/model/schedule-task-list";
import { ScheduleTaskModel } from "../../../../../../../shared/schedule/model/schedule-task-model";
import { TaskActionPaneModel } from "../../../../../../../shared/schedule/model/task-action-pane-model";
import { Tool } from "../../../../../../../shared/util/tool";
import { BatchParameterListModel } from "../../model/batch-parameter-list-model";
import {
   BatchEmbeddedParametersDialogComponent,
   BatchEmbeddedParametersDialogResult
} from "../batch-embedded-parameters-dialog/batch-embedded-parameters-dialog.component";
import {
   BatchQueryParametersDialogComponent,
   BatchQueryParametersDialogResult
} from "../batch-query-parameters-dialog/batch-query-parameters-dialog.component";
import { TaskActionChanges } from "../task-action-pane.component";


@Component({
   selector: "em-batch-action-editor",
   templateUrl: "./batch-action-editor.component.html",
   styleUrls: ["./batch-action-editor.component.scss"]
})
export class BatchActionEditorComponent implements OnInit {
   @Input() originalTaskName: string;
   @Output() modelChanged = new EventEmitter<TaskActionChanges>();

   @Input()
   set model(value: TaskActionPaneModel) {
      this._taskModel = Object.assign({}, value);
   }

   get model(): TaskActionPaneModel {
      return this._taskModel;
   }

   @Input()
   set actionModel(value: BatchActionModel) {
      this._actionModel = Object.assign({}, value);
      const oldTaskName = this.selectedTaskName;
      this.selectedTaskName = this._actionModel.taskName;

      if(oldTaskName != this.selectedTaskName) {
         this.fetchParameters();
      }
   }

   get actionModel(): BatchActionModel {
      return this._actionModel;
   }

   private _taskModel: TaskActionPaneModel;
   private _actionModel: BatchActionModel;
   tasks: ScheduleTaskModel[];
   selectedTaskName: string;
   parameterNames: string[];
   loadingParameterNames: boolean;

   constructor(private http: HttpClient, private dialog: MatDialog) {
   }

   ngOnInit(): void {
      if(!!this.originalTaskName) {
         let params = new HttpParams().set("taskName", this.originalTaskName);
         this.http.get<ScheduleTaskList>("../api/em/schedule/batch-action/scheduled-tasks", {params: params}).subscribe(
            model => {
               this.tasks = model.tasks;
            });
      }
   }

   get modelValid(): boolean {
      return this._actionModel.taskName != null &&
         ((this._actionModel.queryEnabled && this._actionModel.queryEntry &&
            this._actionModel.queryParameters && this._actionModel.queryParameters.length > 0)
            || (this._actionModel.embeddedEnabled && this._actionModel.embeddedParameters &&
               this._actionModel.embeddedParameters.length > 0));
   }

   fireModelChanged(): void {
      this.actionModel.taskName = this.selectedTaskName;
      this.modelChanged.emit({
         valid: this.modelValid,
         model: this.actionModel
      });
   }

   selectedTaskNameChange(selected: string): void {
      this.selectedTaskName = selected;
      this.fireModelChanged();
      this.fetchParameters();
   }

   editQuery(): void {
      let dialogRef = this.dialog.open(BatchQueryParametersDialogComponent, {
         width: "40vw",
         height: "75vh",
         data: {
            queryEntry: this.actionModel.queryEntry,
            queryParameters: Tool.clone(this.actionModel.queryParameters),
            parameterNames: this.parameterNames
         }
      });

      dialogRef.afterClosed().subscribe((result: BatchQueryParametersDialogResult) => {
         if(!!result) {
            this.actionModel.queryEntry = result.queryEntry;
            this.actionModel.queryParameters = result.queryParameters;
            this.fireModelChanged();
         }
      });
   }

   editEmbedded() {
      let dialogRef = this.dialog.open(BatchEmbeddedParametersDialogComponent, {
         width: "40vw",
         height: "75vh",
         data: {
            embeddedParameters: Tool.clone(this.actionModel.embeddedParameters),
            parameterNames: this.parameterNames
         }
      });

      dialogRef.afterClosed().subscribe((result: BatchEmbeddedParametersDialogResult) => {
         if(!!result) {
            this.actionModel.embeddedParameters = result.embeddedParameters;
            this.fireModelChanged();
         }
      });
   }

   fetchParameters() {
      if(!!this.selectedTaskName) {
         this.loadingParameterNames = true;
         let params = new HttpParams().set("taskName", this.selectedTaskName);
         this.http.get<BatchParameterListModel>("../api/em/schedule/batch-action/parameters", {params: params})
            .pipe(finalize(() => this.loadingParameterNames = false))
            .subscribe(model => {
               this.parameterNames = model.parameterNames;
            });
      }
   }
}
