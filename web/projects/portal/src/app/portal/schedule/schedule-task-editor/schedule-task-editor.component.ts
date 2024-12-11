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
import { Component, OnInit, ViewChild } from "@angular/core";
import { UntypedFormBuilder, UntypedFormGroup, Validators } from "@angular/forms";
import { ActivatedRoute, ParamMap, Router } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { switchMap } from "rxjs/operators";
import {
   IdentityId
} from "../../../../../../em/src/app/settings/security/users/identity-id";
import { ScheduleConditionModel } from "../../../../../../shared/schedule/model/schedule-condition-model";
import { ScheduleTaskDialogModel } from "../../../../../../shared/schedule/model/schedule-task-dialog-model";
import { ScheduleTaskEditorModel } from "../../../../../../shared/schedule/model/schedule-task-editor-model";
import { ScheduleTaskModel } from "../../../../../../shared/schedule/model/schedule-task-model";
import { TaskActionPaneModel } from "../../../../../../shared/schedule/model/task-action-pane-model";
import { TaskConditionPaneModel } from "../../../../../../shared/schedule/model/task-condition-pane-model";
import { TaskOptionsPaneModel } from "../../../../../../shared/schedule/model/task-options-pane-model";
import {
   TimeConditionModel,
   TimeConditionType
} from "../../../../../../shared/schedule/model/time-condition-model";
import { ScheduleUsersService } from "../../../../../../shared/schedule/schedule-users.service";
import { FormValidators } from "../../../../../../shared/util/form-validators";
import { Tool } from "../../../../../../shared/util/tool";
import { ComponentTool } from "../../../common/util/component-tool";
import { GuiTool } from "../../../common/util/gui-tool";
import { NotificationsComponent } from "../../../widget/notifications/notifications.component";

const EDIT_TASKS_URI = "../api/portal/schedule/edit";
const SAVE_TASKS_URI = "../api/portal/schedule/save";
const SAVE_TASK_MESSAGE = "_#(js:em.schedule.task.saveSuccess)";
const REMOVE_TASKS_URI = "../api/portal/schedule/remove";

@Component({
   selector: "p-schedule-task-editor",
   templateUrl: "./schedule-task-editor.component.html",
   styleUrls: ["./schedule-task-editor.component.scss"]
})
export class ScheduleTaskEditorComponent implements OnInit {
   @ViewChild("notifications") notifications: NotificationsComponent;
   originalModel: ScheduleTaskDialogModel;
   model: ScheduleTaskDialogModel;
   form: UntypedFormGroup;
   selectedTab = "condition";
   conditionListView: boolean;
   returnPath = "";
   newTask: boolean;

   constructor(private http: HttpClient,
               private router: Router,
               private route: ActivatedRoute,
               private modalService: NgbModal,
               formBuilder: UntypedFormBuilder,
               private usersService: ScheduleUsersService)
   {
      this.form = formBuilder.group({
         "name": ["", Validators.compose([Validators.required, FormValidators.invalidTaskName])]
      });
      this.form.get("name").valueChanges.forEach(
         (name: string) => this.updateTaskName(name, false)
      );
   }

   ngOnInit(): void {
      this.route.paramMap.pipe(
         switchMap((params: ParamMap) => {
            const options = {
               params: new HttpParams().set("name", params.get("task"))
            };
            return this.http.get(EDIT_TASKS_URI, options);
         })
      ).subscribe(
         (model: ScheduleTaskDialogModel) => {
            this.model = model;
            let param = GuiTool.getQueryParameters().get("taskDefaultTime");
            model.taskDefaultTime = param[0] !== "false";
            param = GuiTool.getQueryParameters().get("path");
            this.returnPath = param && param.length > 0 ? param[0] : null;
            param = GuiTool.getQueryParameters().get("newTask");
            this.newTask = param[0] == "true";
            this.resetConditionListView();
            this.form.patchValue({name: model.label});
            this.model.timeZoneOptions[0].timeZoneId = Intl.DateTimeFormat().resolvedOptions().timeZone;
            this.originalModel = Tool.clone(model);
         },
         (error) => {
            if(error.statusText && error.statusText.toLowerCase() == "forbidden") {
               ComponentTool.showMessageDialog(
                  this.modalService, "_#(js:Error)",
                  "You have no schedule permission, please contact administrator");
            }
            else {
               ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
                  error.error, {"ok": "OK"}, {backdrop: "static" })
                  .then(() => this.onCloseEditor());
            }

            this.onCloseEditor();
         }
      );
   }

   resetConditionListView(): void {
      this.conditionListView = !!this.model && this.model.taskConditionPaneModel.conditions.length > 1;
   }

   updateTaskName(taskName: string, external: boolean = true): void {
      this.model.label = taskName;

      if(external) {
         this.form.patchValue({"name": taskName});
      }
   }

   updateOldTaskName(taskName: string, external: boolean = true): void {
      this.notifications.success(SAVE_TASK_MESSAGE);
      let oldName = this.model.name;

      if(oldName == null || oldName.indexOf(":") == -1) {
         this.model.name = taskName;
         return;
      }

      if(external) {
         this.form.patchValue({"name": taskName});
      }
   }

   updateConditionModel(event: TaskConditionPaneModel): void {
      this.originalModel.taskConditionPaneModel = Tool.clone(event);
   }

   updateActionModel(event: TaskActionPaneModel): void {
      this.originalModel.taskActionPaneModel = Tool.clone(event);
   }

   updateOptionsModel(event: TaskOptionsPaneModel): void {
      this.originalModel.taskOptionsPaneModel = Tool.clone(event);
   }

   saveTask = () => {
      let taskName: string = this.model.label;

      const model: ScheduleTaskEditorModel = {
         taskName: taskName,
         oldTaskName: this.model.name,
         conditions: this.getConditionModelsForServer(),
         actions: this.model.taskActionPaneModel.actions,
         options: this.model.taskOptionsPaneModel
      };

      return new Promise((resolve) => this.http.post<ScheduleTaskDialogModel>(SAVE_TASKS_URI, model)
         .toPromise()
         .then((m) => {
            this.model = m;
            this.updateConditionModel(m.taskConditionPaneModel);
            this.updateActionModel(m.taskActionPaneModel);
            this.updateOptionsModel(m.taskOptionsPaneModel);
            this.saveSuccess();

            resolve(m);
      }).catch((error: any) => {
            ComponentTool.showConfirmDialog(this.modalService, "_#(js:Error)",
               error.error.message, {"ok": "OK"});
         }));
   };

   onCloseEditor(): void {
      if(this.returnPath != null && this.returnPath != "/") {
         this.router.navigate(["/portal/tab/schedule/tasks"], {queryParams: { path: this.returnPath}});
      }
      else {
         this.router.navigate(["/portal/tab/schedule/tasks"]);
      }
   }

   saveSuccess() {
      this.notifications.success(SAVE_TASK_MESSAGE);
      this.originalModel = this.model;
   }

   get executeAsGroup(): boolean {
      const taskOption = this.model.taskOptionsPaneModel;
      return !!taskOption.idName && taskOption.idType == 1;
   }

   onCancelTask() {
      ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)",
         "_#(js:portal.schedule.cancel.confirm)").then(
         (result: string) => {
            if(result === "ok") {
               const idx = this.model.name.indexOf(":");
               const owner: IdentityId = idx > -1 ? {name: this.model.name.substring(0, idx), orgID: null} : {name: "", orgID: ""};
               const taskModel = <ScheduleTaskModel> {
                  name: this.model.name,
                  label: this.model.label,
                  description: "",
                  owner: owner,
                  schedule: "",
                  editable: true,
                  removable: true,
                  enabled: true
               };
               this.http.post(REMOVE_TASKS_URI, [taskModel]).subscribe(
                  () => {
                     this.originalModel = this.model;
                     this.onCloseEditor();
                  },
                  (error) => {
                     ComponentTool.showHttpError("_#(js:em.schedule.task.removeFailed)",
                        error, this.modalService);
                  });
            }
         });
   }

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
}
