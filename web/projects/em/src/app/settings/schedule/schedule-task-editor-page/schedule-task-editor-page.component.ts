/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { HttpClient, HttpErrorResponse, HttpParams } from "@angular/common/http";
import { Component, OnInit, ViewEncapsulation } from "@angular/core";
import { UntypedFormBuilder, UntypedFormGroup, Validators } from "@angular/forms";
import { MatDialog } from "@angular/material/dialog";
import { MatSnackBar } from "@angular/material/snack-bar";
import { ActivatedRoute, Router } from "@angular/router";
import { Observable, throwError } from "rxjs";
import { catchError } from "rxjs/operators";
import { GuiTool } from "../../../../../../portal/src/app/common/util/gui-tool";
import { GeneralActionModel } from "../../../../../../shared/schedule/model/general-action-model";
import { ScheduleActionModel } from "../../../../../../shared/schedule/model/schedule-action-model";
import { ScheduleConditionModel } from "../../../../../../shared/schedule/model/schedule-condition-model";
import { ScheduleTaskDialogModel } from "../../../../../../shared/schedule/model/schedule-task-dialog-model";
import { ScheduleTaskEditorModel } from "../../../../../../shared/schedule/model/schedule-task-editor-model";
import {
   TimeConditionModel,
   TimeConditionType
} from "../../../../../../shared/schedule/model/time-condition-model";
import { FormValidators } from "../../../../../../shared/util/form-validators";
import { Tool } from "../../../../../../shared/util/tool";
import { ContextHelp } from "../../../context-help";
import { PageHeaderService } from "../../../page-header/page-header.service";
import { convertToKey } from "../../security/users/identity-id";
import { TaskActionChanges } from "../task-action-pane/task-action-pane.component";
import { TaskConditionChanges } from "../task-condition-pane/task-condition-pane.component";
import { TaskOptionChanges } from "../task-options-pane/task-options-pane.component";
import { MessageDialog, MessageDialogType } from "../../../common/util/message-dialog";

const EDIT_TASKS_URI = "../api/em/schedule/edit";
const SAVE_TASK_URI = "../api/em/schedule/task/save";

export class TaskItem {
   valid = true;

   constructor(public id: string, public label: string) {
   }
}

@ContextHelp({
   route: "/settings/schedule/tasks/*",
   link: "EMSettingsScheduleTask"
})
@Component({
   selector: "em-schedule-task-editor-page",
   templateUrl: "./schedule-task-editor-page.component.html",
   styleUrls: ["./schedule-task-editor-page.component.scss"],
   encapsulation: ViewEncapsulation.None,
   host: { // eslint-disable-line @angular-eslint/no-host-metadata-property
      "class": "schedule-task-editor"
   }
})
export class ScheduleTaskEditorPageComponent implements OnInit {
   originalModel: ScheduleTaskDialogModel;
   model: ScheduleTaskDialogModel;

   selectedConditionIndex = -1;
   conditionItems: TaskItem[] = [];
   selectedActionIndex = -1;
   actionItems: TaskItem[] = [];

   form: UntypedFormGroup;
   returnPath = "/";

   get canDeleteActions(): boolean {
      return this.selectedActionIndex >= 0 && this.actionItems.length > 1;
   }

   get action(): ScheduleActionModel {
      const index = this.selectedActionIndex;

      if(index >= 0 && this.model.taskActionPaneModel.actions.length > index) {
         return this.model.taskActionPaneModel.actions[index];
      }

      return null;
   }

   set action(val: ScheduleActionModel) {
      const index = this.selectedActionIndex;

      if(index >= 0) {
         for(let i = this.model.taskActionPaneModel.actions.length; i <= index; i++) {
            this.model.taskActionPaneModel.actions.push(null);
         }

         this.model.taskActionPaneModel.actions[index] = val;
      }
   }

   get canDeleteConditions(): boolean {
      return this.selectedConditionIndex >= 0 && this.conditionItems.length > 1;
   }

   get condition(): ScheduleConditionModel {
      const index = this.selectedConditionIndex;

      if(index >= 0 && this.model.taskConditionPaneModel.conditions.length > index) {
         return this.model.taskConditionPaneModel.conditions[index];
      }

      return null;
   }

   set condition(val: ScheduleConditionModel) {
      const index = this.selectedConditionIndex;

      if(index >= 0) {
         for(let i = this.model.taskConditionPaneModel.conditions.length; i <= index; i++) {
            this.model.taskConditionPaneModel.conditions.push(null);
         }

         this.model.taskConditionPaneModel.conditions[index] = val;
      }
   }

   get valid(): boolean {
      return (this.form.disabled ? true : this.form.valid) && this.taskChanged
         && this.conditionsValid && this.actionsValid && this.optionsValid;
   }

   get conditionsValid(): boolean {
      return this.conditionItems.reduce((valid: boolean, item: TaskItem) => {
         return valid && item.valid;
      }, true);
   }

   get actionsValid(): boolean {
      return this.actionItems.reduce((valid: boolean, item: TaskItem) => {
         return valid && item.valid;
      }, true);
   }

   private nextActionId = 0;
   private nextConditionId = 0;
   private optionsValid = true;
   loading = true;
   taskChanged = false;

   constructor(private http: HttpClient, private dialog: MatDialog,
               private router: Router, private route: ActivatedRoute,
               private snackBar: MatSnackBar, formBuilder: UntypedFormBuilder,
               private pageTitle: PageHeaderService)
   {
      this.form = formBuilder.group({
         "taskName": ["", [Validators.required, FormValidators.invalidTaskName]]
      });
   }

   ngOnInit() {
      this.route.params.subscribe(params => {
         let taskParams = new HttpParams().set("taskName", encodeURIComponent(params.task));

         this.http.get(EDIT_TASKS_URI, {params: taskParams}).subscribe(
            (model: ScheduleTaskDialogModel) => {
               this.loading = false;
               this.model = model;
               this.form.controls["taskName"].setValue(this.model.label);

               this.model.timeZone = new Date().toLocaleDateString([],{
                  day: "2-digit",
                  timeZoneName: "long",
               }).slice(4);
               this.model.timeZoneOptions[0].timeZoneId = Intl.DateTimeFormat().resolvedOptions().timeZone;

               let queryParam = GuiTool.getQueryParameters().get("path");
               this.returnPath = queryParam != null ? queryParam[0] : "/";

               if(this.model.internalTask) {
                  this.form.controls["taskName"].disable({});
               }

               this.updateLists();

               // Clone the model for comparison when submitting. Changes to sub-components
               // should be reflected in the fields of the ScheduleTaskDialogModel
               this.originalModel = Tool.clone(model);
            },
            (error) => {
               this.loading = false;

               if(error.error.message) {
                  this.dialog.open(MessageDialog, {
                     width: "500px",
                     data: {
                        title: "_#(js:Error)",
                        content: error.error.message,
                        type: MessageDialogType.ERROR
                     }
                  });
               }

               this.close();
            }
         );
      });

      this.pageTitle.title = "_#(js:Edit Schedule Task)";
   }

   selectCondition(index: number): void {
      this.selectedConditionIndex = index;
   }

   addCondition(): void {
      this.appendCondition(true);
      this.selectedConditionIndex = this.conditionItems.length - 1;
      this.taskChanged = true;
   }

   deleteConditions(): void {
      this.dialog.open(MessageDialog, {
         width: "500px",
         data: {
            title: "_#(js:Confirm)",
            content: "_#(js:em.confirm.schedule.condition.delete)",
            type: MessageDialogType.CONFIRMATION
         }
      }).afterClosed().subscribe(confirmed => {
         if(confirmed) {
            this.model.taskConditionPaneModel.conditions.splice(this.selectedConditionIndex, 1);
            this.conditionItems.splice(this.selectedConditionIndex, 1);
            this.taskChanged = true;

            if(this.selectedConditionIndex == this.conditionItems.length) {
               this.selectedConditionIndex -= 1;
            }
         }
      });
   }

   selectAction(index: number): void {
      this.selectedActionIndex = index;
   }

   addAction(): void {
      this.appendAction(true);
      this.selectedActionIndex = this.actionItems.length - 1;
      this.taskChanged = true;
   }

   deleteActions(): void {
      this.dialog.open(MessageDialog, {
         width: "500px",
         data: {
            title: "_#(js:Confirm)",
            content: "_#(js:em.confirm.schedule.action.delete)",
            type: MessageDialogType.CONFIRMATION
         }
      }).afterClosed().subscribe(confirmed => {
         if(confirmed) {
            this.model.taskActionPaneModel.actions.splice(this.selectedActionIndex, 1);
            this.actionItems.splice(this.selectedActionIndex, 1);
            this.taskChanged = true;

            if(this.selectedActionIndex == this.actionItems.length) {
               this.selectedActionIndex -= 1;
            }
         }
      });
   }

   onActionChanged(change: TaskActionChanges) {
      this.actionItems[this.selectedActionIndex].valid = change.valid;
      this.actionItems[this.selectedActionIndex].label = change.model.label;
      this.action = change.model;
      this.taskChanged = true;
   }

   onConditionChanged(change: TaskConditionChanges) {
      this.conditionItems[this.selectedConditionIndex].valid = change.valid;
      this.conditionItems[this.selectedConditionIndex].label = change.model.label;
      this.condition = change.model;
      this.taskChanged = true;
   }

   onOptionsChanged(change: TaskOptionChanges) {
      this.optionsValid = change.valid;
      this.model.taskOptionsPaneModel = change.model;
      this.taskChanged = true;
   }

   /**
    * Server use the client and server timeZoneOffsets to calculate the right date, and
    * the timeZoneOffset of Java and JavaScript are the opposite values, so need to
    * fix the timeZoneOffset for the run once conditions which have not be updated(timeZoneOffset
    * will be updated when setDate)
    */
   getConditionModelsForServer(): ScheduleConditionModel[] {
      let conditions = Tool.clone(this.model.taskConditionPaneModel.conditions);
      conditions.filter((v) => {
         return v.conditionType == "TimeCondition" &&
            (<TimeConditionModel> v).type == TimeConditionType.AT;
      })
      .forEach((cond: TimeConditionModel) => {
         if(!cond.changed || !!cond.timeZone) {
            cond.timeZoneOffset = -cond.timeZoneOffset;
         }
      });

      return conditions;
   }

   save(): void {
      let taskName: string = this.form.controls["taskName"].value;

      const model: ScheduleTaskEditorModel = {
         taskName: taskName,
         oldTaskName: this.model.name,
         conditions: this.getConditionModelsForServer(),
         actions: this.model.taskActionPaneModel.actions,
         options: this.model.taskOptionsPaneModel
      };

      // remove folderPermission property from actions as it's only used on the portal side
      // and iterferes with archivePermission property
      for(let action of model.actions) {
         if(action.actionClass === "GeneralActionModel") {
            delete (<GeneralActionModel> action).folderPermission;
         }
      }

      this.http.post<ScheduleTaskDialogModel>(SAVE_TASK_URI, model)
         .pipe(
            catchError(error => this.handleSaveError(error))
         )
         .subscribe((newModel) => {
            this.model = newModel;
            this.updateLists();
            this.originalModel = Tool.clone(this.model);

            for(let i = 0; i < this.model.taskConditionPaneModel.conditions.length; i++) {
               this.conditionItems[i].label = this.model.taskConditionPaneModel.conditions[i].label;
            }

            for(let i = 0; i < this.model.taskActionPaneModel.actions.length; i++) {
               this.actionItems[i].label = this.model.taskActionPaneModel.actions[i].label;
            }

            this.snackBar.open("_#(js:em.schedule.task.saveSuccess)", null, {
               duration: Tool.SNACKBAR_DURATION
            });
         });

      this.taskChanged = false;
   }

   close(): void {
      const params = {};

      if(this.model) {
         params["taskName"] = this.model.name;
      }

      if(this.returnPath != null && this.returnPath != "/") {
         params["path"] = this.returnPath;
      }

      this.router.navigate(["/settings/schedule/tasks"], {queryParams: params});
   }

   private updateLists(): void {
      this.nextConditionId = 0;
      this.selectedConditionIndex = this.selectedConditionIndex == -1 ? 0 : this.selectedConditionIndex;

      if(this.model.taskConditionPaneModel.conditions.length === 0) {
         this.appendCondition();
      }

      this.conditionItems = this.model.taskConditionPaneModel.conditions.map((condition) => {
         return new TaskItem(`condition-${this.nextConditionId++}`, condition.label);
      });

      this.nextActionId = 0;
      this.selectedActionIndex = this.selectedActionIndex == -1 ? 0 : this.selectedActionIndex;

      if(this.model.taskActionPaneModel.actions.length === 0) {
         this.appendAction();
      }

      this.actionItems = this.model.taskActionPaneModel.actions.map((action) => {
         return new TaskItem(`action-${this.nextActionId++}`, action.label);
      });
   }

   private appendCondition(appendItem: boolean = false): void {
      const defaultTimeZone = this.model.timeZoneOptions[0].timeZoneId;
      const condition: TimeConditionModel = {
         label: "_#(js:New Condition)",
         hour: 1,
         minute: 30,
         second: 0,
         interval: 1,
         conditionType: "TimeCondition",
         type: TimeConditionType.EVERY_DAY,
         timeZoneOffset: this.model.taskConditionPaneModel.timeZoneOffset || 0,
         timeZone: defaultTimeZone
      };

      this.model.taskConditionPaneModel.conditions.push(condition);

      if(appendItem) {
         const item = new TaskItem(`condition-${this.nextConditionId++}`, condition.label);
         this.conditionItems.push(item);
      }
   }

   private appendAction(appendItem: boolean = false): void {
      let action: ScheduleActionModel;

      action = {
         label: "_#(js:New Action)",
         actionType: "ViewsheetAction",
         actionClass: "GeneralActionModel"
      };

      this.model.taskActionPaneModel.actions.push(action);

      if(appendItem) {
         const item = new TaskItem(`action-${this.nextActionId++}`, action.label);
         item.valid = false;
         this.actionItems.push(item);
      }
   }

   private handleSaveError(error: HttpErrorResponse): Observable<any> {
      this.snackBar.open("_#(js:em.schedule.task.saveFailed) More details: " + error.error.message, "_#(js:Close)", {
         duration: Tool.SNACKBAR_DURATION,
         panelClass: ["max-width"]
      });

      console.error("Failed to save task: ", error);
      return throwError(error);
   }
}
