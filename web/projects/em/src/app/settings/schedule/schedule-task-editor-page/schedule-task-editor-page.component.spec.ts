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
import { HttpClientTestingModule } from "@angular/common/http/testing";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { async, TestBed } from "@angular/core/testing";
import { UntypedFormBuilder, ReactiveFormsModule } from "@angular/forms";
import { MatDialog } from "@angular/material/dialog";
import { MatSnackBar } from "@angular/material/snack-bar";
import { ActivatedRoute, Router } from "@angular/router";
import { EMPTY } from "rxjs";
import { GeneralActionModel } from "../../../../../../shared/schedule/model/general-action-model";
import { ScheduleTaskDialogModel } from "../../../../../../shared/schedule/model/schedule-task-dialog-model";
import { TimeConditionModel, TimeConditionType } from "../../../../../../shared/schedule/model/time-condition-model";
import { ScheduleTaskNamesService } from "../../../../../../shared/schedule/schedule-task-names.service";
import { TimeZoneService } from "../../../../../../shared/schedule/time-zone.service";
import { PageHeaderService } from "../../../page-header/page-header.service";
import { ScheduleTaskEditorPageComponent, TaskItem } from "./schedule-task-editor-page.component";

const createCondition = (label: string = "Daily Condition"): TimeConditionModel => ({
   conditionType: "TimeCondition",
   label,
   type: TimeConditionType.EVERY_DAY,
   interval: 1,
   hour: 1,
   minute: 30,
   second: 0,
   timeZoneOffset: 0,
   timeZone: "America/New_York"
});

const createAction = (label: string = "test action"): GeneralActionModel => ({
   label,
   actionType: "ViewsheetAction",
   actionClass: "GeneralActionModel",
   bundledAsZip: false,
   deliverEmailsEnabled: false,
   format: "Excel",
   fromEmail: "reportserver@inetsoft.com",
   emailMatchLayout: true,
   notificationEnabled: false,
   sheet: "1^128^__NULL__^table1",
   printOnServerEnabled: false,
   saveToServerEnabled: false,
   ccAddress: "",
   bccAddress: ""
});

const createModel = (): ScheduleTaskDialogModel => ({
   name: "test-task",
   label: "Test Task",
   taskDefaultTime: false,
   timeZone: "America/New_York",
   timeZoneOptions: [],
   internalTask: false,
   timeRanges: [],
   startTimeEnabled: true,
   timeRangeEnabled: false,
   taskConditionPaneModel: {
      conditions: [createCondition()],
      timeProp: "",
      twelveHourSystem: false,
      userDefinedClasses: [],
      userDefinedClassLabels: []
   },
   taskActionPaneModel: {
      actions: [createAction()],
      securityEnabled: false,
      emailButtonVisible: false,
      endUser: null,
      administrator: true,
      defaultFromEmail: "reportserver@inetsoft.com",
      fromEmailEnabled: true,
      viewsheetEnabled: true,
      notificationEmailEnabled: false,
      saveToDiskEnabled: true,
      emailDeliveryEnabled: true,
      cvsEnabled: false,
      userDefinedClasses: [],
      userDefinedClassLabels: [],
      dashboardMap: {},
      printers: [],
      folderPaths: [],
      folderLabels: [],
      mailFormats: [],
      vsMailFormats: [],
      saveFileFormats: [],
      vsSaveFileFormats: [],
      expandEnabled: true
   },
   taskOptionsPaneModel: null
});

describe("ScheduleTaskEditorPageComponent", () => {
   let component: ScheduleTaskEditorPageComponent;

   beforeEach(async(() => {
      TestBed.configureTestingModule({
         imports: [HttpClientTestingModule, ReactiveFormsModule],
         declarations: [ScheduleTaskEditorPageComponent],
         providers: [
            UntypedFormBuilder,
            { provide: ActivatedRoute, useValue: { params: EMPTY } },
            { provide: Router, useValue: { navigate: jest.fn() } },
            { provide: MatDialog, useValue: { open: jest.fn() } },
            { provide: MatSnackBar, useValue: { open: jest.fn() } },
            { provide: PageHeaderService, useValue: { title: "" } },
            { provide: TimeZoneService, useValue: { updateTimeZoneOptions: jest.fn() } },
            { provide: ScheduleTaskNamesService, useValue: { loadScheduleTaskNames: jest.fn() } }
         ],
         schemas: [NO_ERRORS_SCHEMA]
      });
      TestBed.compileComponents();
   }));

   beforeEach(() => {
      component = TestBed.createComponent(ScheduleTaskEditorPageComponent).componentInstance;
      component.model = createModel();
      component.conditionItems = [new TaskItem("condition-0", "Daily Condition")];
      component.actionItems = [new TaskItem("action-0", "test action")];
      component.selectedConditionIndex = 0;
      component.selectedActionIndex = 0;
      component.taskChanged = false;
   });

   describe("copyCondition()", () => {
      it("should copy the selected condition", () => {
         component.copyCondition();

         expect(component.model.taskConditionPaneModel.conditions.length).toBe(2);
         expect(component.model.taskConditionPaneModel.conditions[1].label).toContain("Copy of");
         expect(component.model.taskConditionPaneModel.conditions[1].label).toContain("Daily Condition");
         expect((<TimeConditionModel> component.model.taskConditionPaneModel.conditions[1]).interval).toBe(1);
      });

      it("should add a corresponding item to conditionItems", () => {
         component.copyCondition();

         expect(component.conditionItems.length).toBe(2);
         expect(component.conditionItems[1].label).toContain("Copy of");
      });

      it("should select the newly copied condition", () => {
         component.copyCondition();

         expect(component.selectedConditionIndex).toBe(1);
      });

      it("should set taskChanged to true", () => {
         component.copyCondition();

         expect(component.taskChanged).toBe(true);
      });

      it("should deep clone the condition so modifying the copy does not affect the original", () => {
         component.model.taskConditionPaneModel.conditions = [
            { ...createCondition("Daily Condition"), daysOfWeek: [1, 2] } as TimeConditionModel
         ];
         component.copyCondition();

         (<TimeConditionModel> component.model.taskConditionPaneModel.conditions[1]).daysOfWeek.push(3);
         expect((<TimeConditionModel> component.model.taskConditionPaneModel.conditions[0]).daysOfWeek)
            .toEqual([1, 2]);
      });

      it("should not copy when no condition is selected", () => {
         component.selectedConditionIndex = -1;
         component.copyCondition();

         expect(component.model.taskConditionPaneModel.conditions.length).toBe(1);
         expect(component.taskChanged).toBe(false);
      });

      it("should not copy when the selected condition does not exist in the model", () => {
         component.selectedConditionIndex = 5;
         component.copyCondition();

         expect(component.model.taskConditionPaneModel.conditions.length).toBe(1);
         expect(component.taskChanged).toBe(false);
      });

      it("should not stack Copy of prefix when copying a copy", () => {
         component.model.taskConditionPaneModel.conditions = [
            createCondition("_#(js:Copy of) Daily Condition")
         ];
         component.conditionItems = [new TaskItem("condition-0", "_#(js:Copy of) Daily Condition")];
         component.copyCondition();

         expect(component.model.taskConditionPaneModel.conditions[1].label)
            .toBe("_#(js:Copy of) Daily Condition");
      });

      it("should strip multiple stacked Copy of prefixes", () => {
         component.model.taskConditionPaneModel.conditions = [
            createCondition("_#(js:Copy of) _#(js:Copy of) Daily Condition")
         ];
         component.conditionItems = [new TaskItem("condition-0", "_#(js:Copy of) _#(js:Copy of) Daily Condition")];
         component.copyCondition();

         expect(component.model.taskConditionPaneModel.conditions[1].label)
            .toBe("_#(js:Copy of) Daily Condition");
      });
   });

   describe("copyAction()", () => {
      it("should copy the selected action", () => {
         component.copyAction();

         expect(component.model.taskActionPaneModel.actions.length).toBe(2);
         expect(component.model.taskActionPaneModel.actions[1].label).toContain("Copy of");
         expect(component.model.taskActionPaneModel.actions[1].label).toContain("test action");
         expect((<GeneralActionModel> component.model.taskActionPaneModel.actions[1]).sheet)
            .toBe("1^128^__NULL__^table1");
      });

      it("should add a corresponding item to actionItems", () => {
         component.copyAction();

         expect(component.actionItems.length).toBe(2);
         expect(component.actionItems[1].label).toContain("Copy of");
      });

      it("should select the newly copied action", () => {
         component.copyAction();

         expect(component.selectedActionIndex).toBe(1);
      });

      it("should set taskChanged to true", () => {
         component.copyAction();

         expect(component.taskChanged).toBe(true);
      });

      it("should deep clone the action so modifying the copy does not affect the original", () => {
         component.copyAction();

         (<GeneralActionModel> component.model.taskActionPaneModel.actions[1]).sheet = "changed";
         expect((<GeneralActionModel> component.model.taskActionPaneModel.actions[0]).sheet)
            .toBe("1^128^__NULL__^table1");
      });

      it("should not copy when no action is selected", () => {
         component.selectedActionIndex = -1;
         component.copyAction();

         expect(component.model.taskActionPaneModel.actions.length).toBe(1);
         expect(component.taskChanged).toBe(false);
      });

      it("should not copy when the selected action does not exist in the model", () => {
         component.selectedActionIndex = 5;
         component.copyAction();

         expect(component.model.taskActionPaneModel.actions.length).toBe(1);
         expect(component.taskChanged).toBe(false);
      });

      it("should not stack Copy of prefix when copying a copy", () => {
         component.model.taskActionPaneModel.actions = [createAction("_#(js:Copy of) test action")];
         component.actionItems = [new TaskItem("action-0", "_#(js:Copy of) test action")];
         component.copyAction();

         expect(component.model.taskActionPaneModel.actions[1].label)
            .toBe("_#(js:Copy of) test action");
      });

      it("should strip multiple stacked Copy of prefixes", () => {
         component.model.taskActionPaneModel.actions = [
            createAction("_#(js:Copy of) _#(js:Copy of) test action")
         ];
         component.actionItems = [new TaskItem("action-0", "_#(js:Copy of) _#(js:Copy of) test action")];
         component.copyAction();

         expect(component.model.taskActionPaneModel.actions[1].label)
            .toBe("_#(js:Copy of) test action");
      });
   });
});
