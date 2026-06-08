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
import { HttpClient, HttpClientModule } from "@angular/common/http";
import { Component, inject, ViewChild } from "@angular/core";
import { waitForAsync, ComponentFixture, TestBed } from "@angular/core/testing";
import {
   FormBuilder,
   FormControl,
   FormGroup,
   FormsModule,
   ReactiveFormsModule
} from "@angular/forms";
import { By } from "@angular/platform-browser";
import { NgbModal, NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { BehaviorSubject, Subject } from "rxjs";
import { CompletionConditionModel } from "../../../../../../../shared/schedule/model/completion-condition-model";
import { TaskConditionPaneModel } from "../../../../../../../shared/schedule/model/task-condition-pane-model";
import {
   TimeConditionModel,
   TimeConditionType
} from "../../../../../../../shared/schedule/model/time-condition-model";
import { ScheduleTaskNamesService } from "../../../../../../../shared/schedule/schedule-task-names.service";
import { TestUtils } from "../../../../common/test/test-utils";
import { ComponentTool } from "../../../../common/util/component-tool";
import { DateValueEditorComponent } from "../../../../widget/date-type-editor/date-value-editor.component";
import { TimeInstantValueEditorComponent } from "../../../../widget/date-type-editor/time-instant-value-editor.component";
import { TimeValueEditorComponent } from "../../../../widget/date-type-editor/time-value-editor.component";
import { TimepickerComponent } from "../../../../widget/date-type-editor/timepicker.component";
import { EnterSubmitDirective } from "../../../../widget/directive/enter-submit.directive";
import { NotificationsComponent } from "../../../../widget/notifications/notifications.component";
import { ReplaceAllPipe } from "../../../../widget/pipe/replace-all.pipe";
import { StartTimeEditor } from "../../../../widget/schedule/start-time-editor.component";
import { AddParameterDialog } from "../add-parameter-dialog/add-parameter-dialog.component";
import { EditableTableComponent } from "../editable-table/editable-table.component";
import { ParameterTable } from "../parameter-table/parameter-table.component";
import { TaskConditionPane } from "./task-condition-pane.component";

@Component({
   standalone: true,
   selector: "test-app",
   imports: [TaskConditionPane],
   template: `<task-condition-pane [model]="model" [taskName]="taskName" [parentForm]="form" [taskDefaultTimeProperty]="true"></task-condition-pane>`
})
class TestApp {
   @ViewChild(TaskConditionPane, {static: true}) taskConditionPane: TaskConditionPane;
   model: TaskConditionPaneModel = {
      timeProp: "",
      twelveHourSystem: false,
      conditions: [{conditionType: "TimeCondition", label: "test condition"}],
      userDefinedClasses: [],
      userDefinedClassLabels: []
   };
   taskName = "Task1";
   form = inject(FormBuilder).group({});
}

describe("Task Condition Pane Unit Test", () => {
   let ngbService = { open: vi.fn() };
   let httpService = { get: vi.fn(), post: vi.fn() };
   let responseObservable = new BehaviorSubject(new Subject());
   httpService.get.mockImplementation(() => responseObservable);
   httpService.post.mockImplementation(() => responseObservable);

   let fixture: ComponentFixture<TestApp>;
   let taskConditionPane: TaskConditionPane;

   beforeEach(waitForAsync(() => {
      TestBed.configureTestingModule({
         imports: [
            FormsModule,
            ReactiveFormsModule,
            NgbModule,
            HttpClientModule,
            TestApp,
            ReplaceAllPipe,
            TaskConditionPane,
            ParameterTable,
            EditableTableComponent,
            AddParameterDialog,
            EnterSubmitDirective,
            DateValueEditorComponent,
            TimeValueEditorComponent,
            TimeInstantValueEditorComponent,
            TimepickerComponent,
            NotificationsComponent,
            StartTimeEditor,
         ],
         
         providers: [
            {
               provide: NgbModal, useValue: ngbService
            },
            {
               provide: HttpClient, useValue: httpService
            },
            ScheduleTaskNamesService
         ]
      });
      TestBed.overrideComponent(TaskConditionPane, { set: { imports: [] } });
      TestBed.compileComponents();
   }));

   beforeEach(() => {
      fixture = TestBed.createComponent(TestApp);
      taskConditionPane = <TaskConditionPane>fixture.componentInstance.taskConditionPane;
      taskConditionPane.form = new FormGroup({});
      taskConditionPane.convertTime = vi.fn().mockImplementation((a, b, c) => {
         return a;
      });
      fixture.detectChanges();
   });

   //Bug #19519 should show current date when not set
   //Bug #19687 should show set date
   it.skip("should show correct date in run once", () => {
      taskConditionPane.changeConditionType(0);
      fixture.detectChanges();

      let dateInput = fixture.debugElement.query(By.css("input#date")).nativeElement;
      const currentDate = new Date();
      const yearStr = currentDate.getUTCFullYear();
      const monthStr = (currentDate.getUTCMonth() + 1) < 10 ?
         ("0" + (currentDate.getUTCMonth() + 1)) : (currentDate.getUTCMonth() + 1);
      const dayStr = currentDate.getUTCDate() < 10 ?
         ("0" + currentDate.getUTCDate()) : currentDate.getUTCDate();
      const dateStr = yearStr + "-" + monthStr + "-" + dayStr;
      expect(dateInput.value).toBe(dateStr);

      taskConditionPane.date = {year: 2017, month: 12, day: 1};
      fixture.detectChanges();
      dateInput = fixture.debugElement.query(By.css("input#date")).nativeElement;
      expect(dateInput.value).toBe("2017-12-01");
   });

   //Bug #19517 select and deselect all function for weekly
   it.skip("select and deselect all should work in weekly condition", () => {
      taskConditionPane.changeConditionType(TimeConditionType.EVERY_WEEK);
      taskConditionPane.timeCondition.type = TimeConditionType.EVERY_WEEK;
      fixture.detectChanges();

      let selectAll = fixture.nativeElement.querySelector(
         "button.select_all_weeklyWeekdays_id");
      let deSelectAll = fixture.nativeElement.querySelector(
         "button.deselect_all_weeklyWeekdays_id");
      selectAll.click();
      fixture.detectChanges();

      let days: any = fixture.nativeElement.querySelectorAll("input[type=checkbox].weeklyWeekday_id");
      days.forEach((day: HTMLInputElement) => {
         expect(day.checked).toBeTruthy();
      });

      deSelectAll.click();
      fixture.detectChanges();

      days = fixture.nativeElement.querySelectorAll("input[type=checkbox].weeklyWeekday_id");
      days.forEach((day: HTMLInputElement) => {
         expect(day.checked).toBeFalsy();
      });

      //Bug #19505 change weekdays
      let every = fixture.debugElement.query(By.css("input#weeklyInterval")).nativeElement;
      every.value = "1";
      every.dispatchEvent(new Event("input"));
      fixture.detectChanges();
      days[0].click();
      days[1].click();
      fixture.detectChanges();
      let saveBtn = fixture.debugElement.query(By.css("button.save_button_id")).nativeElement;
      saveBtn.click();
      fixture.detectChanges();

      let condition = <TimeConditionModel>fixture.componentInstance.model.conditions[0];
      expect(condition.daysOfWeek.length).toBe(2);
      expect(condition.daysOfWeek[0]).toBe(1);
      expect(condition.daysOfWeek[1]).toBe(2);
   });

   //Bug #19517 select and deselect all function for monthly
   //Bug #19518 should has default value for monthly
   it.skip("select and deselect all should work in monthly condition", () => {
      taskConditionPane.changeConditionType(2);
      fixture.detectChanges();

      let optionsRadio = fixture.nativeElement.querySelectorAll("input[name=options]");
      expect(optionsRadio[0].getAttribute("ng-reflect-value")).toBe("true");
      expect(optionsRadio[1].getAttribute("ng-reflect-value")).toBe("false");

      let selectAll = fixture.debugElement.query(By.css("button.select_all_month_id")).nativeElement;
      let deSelectAll = fixture.debugElement.query(By.css("button.deselect_all_month_id")).nativeElement;
      selectAll.click();
      fixture.detectChanges();

      let month: any = fixture.nativeElement.querySelectorAll("input[type=checkbox][name=month]");
      month.forEach((perMonth: HTMLInputElement) => {
         expect(perMonth.checked).toBeTruthy();
      });

      deSelectAll.click();
      fixture.detectChanges();

      month = fixture.nativeElement.querySelectorAll("input[type=checkbox][name=month]");
      month.forEach((perMonth: HTMLInputElement) => {
         expect(perMonth.checked).toBeFalsy();
      });

      //Bug #19505 change month
      let dayOfMonth = fixture.nativeElement.querySelector(
         "select[ng-reflect-name=dayOfMonth]");
      dayOfMonth.value = "1";
      dayOfMonth.dispatchEvent(new Event("change"));
      fixture.detectChanges();
      month[3].click();
      fixture.detectChanges();
      let saveBtn = fixture.debugElement.query(By.css("button.save_button_id")).nativeElement;
      saveBtn.click();
      fixture.detectChanges();

      let condition = <TimeConditionModel>fixture.componentInstance.model.conditions[0];
      expect(condition.monthsOfYear.length).toBe(1);
      expect(condition.monthsOfYear[0]).toBe(3);
   });

   //Bug #19517 select and deselect all function for hourly
   it.skip("select and deselect all should work in hourly condition", () => {
      taskConditionPane.changeConditionType(3);
      fixture.detectChanges();

      let selectAll = fixture.debugElement.query(By.css("button.select_all_weekdays_id")).nativeElement;
      let deSelectAll = fixture.debugElement.query(By.css("button.deselect_all_weekdays_id")).nativeElement;
      selectAll.click();
      fixture.detectChanges();

      let weekdays: any = fixture.nativeElement.querySelectorAll("input[type=checkbox].weekday_id");
      weekdays.forEach((weekday: HTMLInputElement) => {
         expect(weekday.checked).toBeTruthy();
      });

      deSelectAll.click();
      fixture.detectChanges();

      weekdays = fixture.nativeElement.querySelectorAll("input[type=checkbox].weekday_id");
      weekdays.forEach((weekday: HTMLInputElement) => {
         expect(weekday.checked).toBeFalsy();
      });

      //Bug #19505 change hourly weekdays
      //Bug #21417 should allow decimal point value for hour interval
      taskConditionPane.startTime = {hour: 11, minute: 30, second: 25};
      taskConditionPane.endTime = {hour: 13, minute: 30, second: 25};
      let every = fixture.debugElement.query(By.css("input#interval")).nativeElement;
      every.value = "0.1";
      every.dispatchEvent(new Event("input"));
      fixture.detectChanges();
      weekdays[2].click();
      fixture.detectChanges();

      let warning = fixture.debugElement.query(By.css("div.alert.alert-danger")).nativeElement;
      expect(warning).toBeNull();

      let saveBtn = fixture.debugElement.query(By.css("button.save_button_id")).nativeElement;
      saveBtn.click();
      fixture.detectChanges();

      let condition = <TimeConditionModel>fixture.componentInstance.model.conditions[0];
      expect(condition.daysOfWeek.length).toBe(1);
      expect(condition.daysOfWeek[0]).toBe(3);

      //Bug #19878 start time should be before end time
      taskConditionPane.startTime = {hour: 11, minute: 30, second: 25};
      taskConditionPane.endTime = {hour: 10, minute: 30, second: 25};
      fixture.detectChanges();

      warning = fixture.debugElement.query(By.css("div.alert.alert-danger")).nativeElement;
      expect(warning.textContent.trim()).toContain(
         "_#(js:viewer.schedule.condition.startEndValid)");
   });

   //Bug #19522 should not load self in chaind condition
   it.skip("should not load self in chaind condition", () => { // broken test
      taskConditionPane.form.addControl("task", new FormControl({}));
      taskConditionPane.taskName = "Task1";
      taskConditionPane.model.conditions[0].conditionType = "CompletionCondition";
      taskConditionPane.changeConditionType(6);
      fixture.detectChanges();

      let runAfter = fixture.nativeElement.querySelectorAll("select#taskName option");
      expect(runAfter.length).toBe(2);
      expect(runAfter[0].textContent.trim()).toBe("Task2");
      expect(runAfter[1].textContent.trim()).toBe("Task3");

      //Bug #19505 change run after value
      let runAfterTask = fixture.debugElement.query(By.css("select#taskName")).nativeElement;
      runAfterTask.value = "Task2";
      runAfterTask.dispatchEvent(new Event("change"));
      fixture.detectChanges();
      let saveBtn = fixture.debugElement.query(By.css("button.save_button_id")).nativeElement;
      saveBtn.click();
      fixture.detectChanges();

      let condition = <CompletionConditionModel>fixture.componentInstance.model.conditions[0];
      expect(condition).toBeTruthy();
      expect(condition.taskName).toBe("Task2");
   });

   //Bug #19890 should pop up warning when to delete condition
   it.skip("should pop up warning when to delete condition", () => {
      let showConfirmDialog = vi.spyOn(ComponentTool, "showConfirmDialog");
      showConfirmDialog.mockImplementation(() => Promise.resolve("ok"));
      taskConditionPane.deleteCondition();

      expect(showConfirmDialog).toHaveBeenCalled();
   });

   //Bug #19891 should not pop up warning when select all
   it.skip("should not pop up warning when select all", () => {
      //weekly
      taskConditionPane.startTime = {hour: 10, minute: 30, second: 55};
      taskConditionPane.endTime = {hour: 11, minute: 30, second: 55};
      taskConditionPane.changeConditionType(1);
      fixture.detectChanges();

      let every = fixture.debugElement.query(By.css("input#weeklyInterval")).nativeElement;
      every.value = "1";
      every.dispatchEvent(new Event("input"));
      fixture.detectChanges();

      let warning1 = fixture.debugElement.query(By.css("div.alert.alert-danger")).nativeElement;
      expect(warning1.textContent.trim()).toContain("_#(js:viewer.schedule.condition.weeklyValid)");

      let selectAll1 = fixture.nativeElement.querySelector(
         "button.select_all_weeklyWeekdays_id");
      selectAll1.click();
      fixture.detectChanges();
      warning1 = fixture.debugElement.query(By.css("div.alert.alert-danger")).nativeElement;
      expect(warning1).toBeNull();

      //monthly
      taskConditionPane.changeConditionType(2);
      fixture.detectChanges();

      let dayOfMonth = fixture.nativeElement.querySelector(
         "select[ng-reflect-name=dayOfMonth]");
      dayOfMonth.value = "1";
      dayOfMonth.dispatchEvent(new Event("change"));
      fixture.detectChanges();

      let warning2 = fixture.debugElement.query(By.css("div.alert.alert-danger")).nativeElement;
      expect(warning2.textContent.trim()).toContain("_#(js:viewer.schedule.condition.monthlyValid)");

      let selectAll2 = fixture.debugElement.query(By.css("button.select_all_month_id")).nativeElement;
      selectAll2.click();
      fixture.detectChanges();
      warning2 = fixture.debugElement.query(By.css("div.alert.alert-danger")).nativeElement;
      expect(warning2).toBeNull();

      //hourly
      taskConditionPane.changeConditionType(3);
      fixture.detectChanges();
      taskConditionPane.startTime = {hour: 10, minute: 30, second: 55};
      taskConditionPane.endTime = {hour: 11, minute: 30, second: 55};

      let every3 = fixture.debugElement.query(By.css("input#interval")).nativeElement;
      every3.value = "1";
      every3.dispatchEvent(new Event("input"));
      fixture.detectChanges();

      let warning3 = fixture.debugElement.query(By.css("div.alert.alert-danger")).nativeElement;
      expect(TestUtils.toString(warning3.textContent.trim())).toContain("viewer.schedule.condition.weeklyValid");

      let selectAll3 = fixture.debugElement.query(By.css("button.select_all_weekdays_id")).nativeElement;
      selectAll3.click();
      fixture.detectChanges();
      warning3 = fixture.debugElement.query(By.css("div.alert.alert-danger")).nativeElement;
      expect(warning3).toBeNull();
   });

   //Bug #19849 should load default value for user defined condition
   //Bug #21421
   it.skip("should load default value for user defined condition", () => {
      taskConditionPane.model.userDefinedClassLabels = ["Test1", "Test2"];
      taskConditionPane.changeConditionType(4);
      fixture.detectChanges();

      let conditions = fixture.nativeElement.querySelectorAll("select#conditionClass option");
      expect(TestUtils.toString(conditions[0].textContent.trim())).toBe("Choose a condition");
      expect(conditions[1].textContent.trim()).toBe("Test1");
      expect(conditions[1].value).toBe("Test1");
   });

   //Bug #19899 should disable delete when no condition selected for multiple schedule
   it.skip("should disable delete when no condition selected for multiple schedule", () => {
      fixture.componentInstance.model.conditions =
         [{conditionType: "TimeCondition", label: "TimeCondition: 05:30:00, every 1 day(s)"},
            {conditionType: "TimeCondition", label: "TimeCondition: Sunday of Week, every 1 week(s)"}];
      taskConditionPane.listView = true;
      taskConditionPane.selectedConditions = [];
      fixture.detectChanges();

      let delBtn = fixture.debugElement.query(By.css("button.delete-button-id")).nativeElement;
      expect(delBtn.hasAttribute("disabled")).toBeTruthy();
   });

   it("should copy selected condition", () => {
      taskConditionPane.model.conditions = [
         {conditionType: "TimeCondition", label: "Daily Condition", type: TimeConditionType.EVERY_DAY, interval: 3} as TimeConditionModel,
         {conditionType: "TimeCondition", label: "Weekly Condition", type: TimeConditionType.EVERY_WEEK, interval: 2} as TimeConditionModel
      ];
      taskConditionPane.selectedConditions = [0];
      taskConditionPane.copyCondition();

      expect(taskConditionPane.model.conditions.length).toBe(3);
      expect(taskConditionPane.model.conditions[2].label).toContain("Copy of");
      expect(taskConditionPane.model.conditions[2].label).toContain("Daily Condition");
      expect((<TimeConditionModel>taskConditionPane.model.conditions[2]).interval).toBe(3);
      expect(taskConditionPane.selectedConditions).toEqual([2]);
   });

   it("should deep clone condition so modifying copy does not affect original", () => {
      taskConditionPane.model.conditions = [
         {conditionType: "TimeCondition", label: "Daily Condition", type: TimeConditionType.EVERY_DAY, daysOfWeek: [1, 2]} as TimeConditionModel
      ];
      taskConditionPane.selectedConditions = [0];
      taskConditionPane.copyCondition();

      (<TimeConditionModel>taskConditionPane.model.conditions[1]).daysOfWeek.push(3);
      expect((<TimeConditionModel>taskConditionPane.model.conditions[0]).daysOfWeek).toEqual([1, 2]);
   });

   it("should not copy when no condition is selected", () => {
      taskConditionPane.model.conditions = [
         {conditionType: "TimeCondition", label: "Daily Condition"} as TimeConditionModel
      ];
      taskConditionPane.selectedConditions = [];
      taskConditionPane.copyCondition();

      expect(taskConditionPane.model.conditions.length).toBe(1);
   });

   it("should not copy when multiple conditions are selected", () => {
      taskConditionPane.model.conditions = [
         {conditionType: "TimeCondition", label: "Condition 1"} as TimeConditionModel,
         {conditionType: "TimeCondition", label: "Condition 2"} as TimeConditionModel
      ];
      taskConditionPane.selectedConditions = [0, 1];
      taskConditionPane.copyCondition();

      expect(taskConditionPane.model.conditions.length).toBe(2);
   });

   // Bug #75325: toggling "Show Server Time Zone" while editing one condition must also
   // convert the other conditions of the task so their times display in the server zone
   // when later edited; otherwise a sibling shows its raw local time labeled as the server
   // zone.
   it("should convert all conditions when switching to server time zone (bug #75325)", () => {
      // deterministic offset-shift stub (the default harness stubs convertTime to identity,
      // which would hide the conversion). value shifts by (newTz - oldTz).
      taskConditionPane.convertTime = vi.fn().mockImplementation((value, oldTz, newTz) => {
         const shifted = value.hour + (newTz - oldTz) / (60 * 60 * 1000);
         return { hour: ((shifted % 24) + 24) % 24, minute: value.minute, second: value.second };
      });

      const utcOffset = 0;
      const easternOffset = -4 * 60 * 60 * 1000; // EDT = UTC-4
      // both conditions stored at 02:30 in Eastern; server zone is UTC
      const cond1 = {
         conditionType: "TimeCondition", type: TimeConditionType.EVERY_DAY,
         label: "Condition 1", timeZone: "America/New_York",
         hour: 2, minute: 30, second: 0
      } as TimeConditionModel;
      const cond2 = {
         conditionType: "TimeCondition", type: TimeConditionType.EVERY_DAY,
         label: "Condition 2", timeZone: "America/New_York",
         hour: 2, minute: 30, second: 0
      } as TimeConditionModel;

      taskConditionPane.model.conditions = [cond1, cond2];
      (taskConditionPane as any).conditionIndex = 0;
      taskConditionPane.serverTimeZone = false;
      (taskConditionPane as any).serverTimeZoneOffset = utcOffset;
      (taskConditionPane as any).localTimeZoneOffset = easternOffset;

      // calculateTimezoneOffset is environment dependent; force Eastern for the siblings
      taskConditionPane["timeZoneService"].calculateTimezoneOffset = vi.fn(() => easternOffset);

      taskConditionPane.changeServerTimeZone(true);

      // both the edited condition and the sibling must convert 02:30 EDT -> 06:30 UTC
      expect(cond1.hour).toBe(6);
      expect(cond1.minute).toBe(30);
      expect(cond2.hour).toBe(6);
      expect(cond2.minute).toBe(30);

      // toggling back to local must run the reverse path (convertOtherConditions(false))
      // and restore every condition to its original 02:30 EDT
      taskConditionPane.changeServerTimeZone(false);

      expect(cond1.hour).toBe(2);
      expect(cond1.minute).toBe(30);
      expect(cond2.hour).toBe(2);
      expect(cond2.minute).toBe(30);
   });

   //Bug #19860 should keep last condition
   it.skip("should keep last condition", () => {
      taskConditionPane.changeConditionType(0);
      fixture.detectChanges();

      let startHour = fixture.debugElement.query(By.css("input[placeholder=HH]")).nativeElement;
      let startMinute = fixture.debugElement.query(By.css("input[placeholder=MM]")).nativeElement;
      let every = fixture.debugElement.query(By.css("input#dailyInterval")).nativeElement;

      startHour.value = "11";
      startMinute.value = "29";
      every.value = "2";
      startHour.dispatchEvent(new Event("change"));
      startMinute.dispatchEvent(new Event("change"));
      every.dispatchEvent(new Event("change"));
      fixture.detectChanges();

      taskConditionPane.changeConditionType(1);
      fixture.detectChanges();
      taskConditionPane.changeConditionType(0);
      fixture.detectChanges();

      startHour = fixture.debugElement.query(By.css("input[placeholder=HH]")).nativeElement;
      startMinute = fixture.debugElement.query(By.css("input[placeholder=MM]")).nativeElement;
      every = fixture.debugElement.query(By.css("input#dailyInterval")).nativeElement;

      expect(every.value).toBe("2");
      expect(startHour.value).toBe("11");
      expect(startMinute.value).toBe("29");
   });
});
