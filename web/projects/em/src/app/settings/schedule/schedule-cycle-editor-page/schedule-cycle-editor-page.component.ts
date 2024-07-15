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
import { Component, OnDestroy, OnInit, ViewEncapsulation } from "@angular/core";
import { UntypedFormControl, Validators } from "@angular/forms";
import { ActivatedRoute, Router } from "@angular/router";
import { HttpClient, HttpHeaders } from "@angular/common/http";
import { ContextHelp } from "../../../context-help";
import { ScheduleCycleDialogModel } from "../model/schedule-cycle-dialog-model";
import { TaskItem } from "../schedule-task-editor-page/schedule-task-editor-page.component";
import {
   TimeConditionModel,
   TimeConditionType
} from "../../../../../../shared/schedule/model/time-condition-model";
import { ScheduleConditionModel } from "../../../../../../shared/schedule/model/schedule-condition-model";
import { TaskConditionChanges } from "../task-condition-pane/task-condition-pane.component";
import { MatDialog } from "@angular/material/dialog";
import { MatSnackBar } from "@angular/material/snack-bar";
import { MessageDialog, MessageDialogType } from "../../../common/util/message-dialog";
import { HttpErrorResponse } from "@angular/common/http";
import { Tool } from "../../../../../../shared/util/tool";
import { FormValidators } from "../../../../../../shared/util/form-validators";
import { Subscription } from "rxjs";

const GET_DATA_CYCLE_DIALOG_MODEL_URI = "../api/em/schedule/cycle-dialog-model/";
const EDIT_DATA_CYCLE_URI = "../api/em/schedule/edit-cycle";

@ContextHelp({
   route: "/settings/schedule/cycles/*",
   link: "EMSettingsScheduleCycle"
})
@Component({
   selector: "em-schedule-cycle-editor-page",
   templateUrl: "./schedule-cycle-editor-page.component.html",
   styleUrls: ["./schedule-cycle-editor-page.component.scss"],
   encapsulation: ViewEncapsulation.None,
   host: { // eslint-disable-line @angular-eslint/no-host-metadata-property
      "class": "schedule-cycle-editor"
   }
})
export class ScheduleCycleEditorPageComponent implements OnInit, OnDestroy {
   model: ScheduleCycleDialogModel;
   originalModel: ScheduleCycleDialogModel;
   name: UntypedFormControl;
   selectedConditionIndex = -1;
   conditionItems: TaskItem[] = [];
   conditionsValid: boolean = true;
   optionsValid: boolean = true;
   taskChanged: boolean = false;
   valueChangesSubscription = Subscription.EMPTY;

   private nextConditionId: number = 0;

   get valid(): boolean {
      return this.conditionsValid && this.optionsValid && this.name && this.name.valid && this.taskChanged;
   }

   constructor(private route: ActivatedRoute,
               private http: HttpClient,
               private router: Router,
               private dialog: MatDialog,
               private snackBar: MatSnackBar)
   {
   }

   ngOnInit() {
      this.route.params.subscribe(params => this.loadModel(params.cycle));
      this.name = new UntypedFormControl(this.model ? this.model.name : "",
         [Validators.required, FormValidators.isValidReportName]);
      this.valueChangesSubscription =  this.name.valueChanges.subscribe(
         (value: string) => {
            this.model.label = value;
            this.taskChanged = true;
         });
   }

   ngOnDestroy() {
      this.valueChangesSubscription.unsubscribe();
   }

   private loadModel(cycleName: string): void {
      this.http.get(GET_DATA_CYCLE_DIALOG_MODEL_URI + cycleName).subscribe(
         (model: ScheduleCycleDialogModel) => {
            this.model = model;
            this.model.cycleInfo.name = cycleName;
            this.name.setValue(this.model.name, {emitEvent: false});
            this.originalModel = Tool.clone(model);
            this.updateList();
         });
   }

   private updateList(): void {
      this.nextConditionId = 0;
      this.selectedConditionIndex = 0;

      if(this.model.conditionPaneModel.conditions.length === 0) {
         this.appendCondition();
      }

      this.conditionItems = this.model.conditionPaneModel.conditions.map((condition) => {
         return new TaskItem(`condition-${this.nextConditionId++}`, condition.label);
      });
   }

   get condition(): ScheduleConditionModel {
      const index = this.selectedConditionIndex;

      if(index >= 0 && this.model.conditionPaneModel.conditions.length > index) {
         return this.model.conditionPaneModel.conditions[index];
      }

      return null;
   }

   set condition(val: ScheduleConditionModel) {
      const index = this.selectedConditionIndex;

      if(index >= 0) {
         for(let i = this.model.conditionPaneModel.conditions.length; i <= index; i++) {
            this.model.conditionPaneModel.conditions.push(null);
         }

         this.model.conditionPaneModel.conditions[index] = val;
      }
   }

   get canDeleteConditions(): boolean {
      return this.selectedConditionIndex >= 0 && this.conditionItems.length > 1;
   }

   selectCondition(index: number): void {
      this.selectedConditionIndex = index;
   }

   addCondition(): void {
      this.appendCondition(true);
      this.selectedConditionIndex = this.conditionItems.length - 1;
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
            this.taskChanged = true;
            this.model.conditionPaneModel.conditions.splice(this.selectedConditionIndex, 1);
            this.conditionItems.splice(this.selectedConditionIndex, 1);

            if(this.selectedConditionIndex == this.conditionItems.length) {
               this.selectedConditionIndex -= 1;
            }
         }
      });

   }

   private appendCondition(appendItem: boolean = false): void {
      this.taskChanged = true;
      const condition: TimeConditionModel = {
         label: "_#(js:New Condition)",
         hour: 1,
         minute: 30,
         second: 0,
         interval: 1,
         conditionType: "TimeCondition",
         type: TimeConditionType.EVERY_DAY,
         timeZoneOffset: this.model.conditionPaneModel.timeZoneOffset || 0
      };

      this.model.conditionPaneModel.conditions.push(condition);

      if(appendItem) {
         const item = new TaskItem(`condition-${this.nextConditionId++}`, condition.label);
         this.conditionItems.push(item);
      }
   }

   onConditionChanged(change: TaskConditionChanges) {
      this.conditionItems[this.selectedConditionIndex].valid = change.valid;
      this.conditionItems[this.selectedConditionIndex].label = change.model.label;
      this.conditionsValid = !this.conditionItems.map(item => item.valid).includes(false);

      this.taskChanged = true;
      this.condition = change.model;
   }

   onOptionsChanged(valid: boolean) {
      this.optionsValid = valid;
      this.taskChanged = true;
   }

   save(): void {
      const headers = new HttpHeaders({"Content-Type": "application/json"});

      this.http.post<ScheduleCycleDialogModel>(EDIT_DATA_CYCLE_URI, this.model, {headers}).subscribe(
         (newModel) => {
            this.model = newModel;
            this.originalModel = Tool.clone(this.model);

            for(let i = 0; i < this.model.conditionPaneModel.conditions.length; i++) {
               this.conditionItems[i].label = this.model.conditionPaneModel.conditions[i].label;
            }

            this.snackBar.open("_#(js:em.scheduler.cycle.saveSuccess)", null, {
               duration: Tool.SNACKBAR_DURATION
            });

            this.taskChanged = false;
         },
         (error: HttpErrorResponse) => {
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
         }
      );
   }

   close(): void {
      this.router.navigate(["/settings/schedule/cycles"]);
   }
}
