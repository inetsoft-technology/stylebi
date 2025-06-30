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
import { Component, ViewChild } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormControl, FormGroup, FormsModule, ReactiveFormsModule } from "@angular/forms";
import { By } from "@angular/platform-browser";
import { NgbModal, NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { BehaviorSubject, Subject } from "rxjs";
import { CompletionConditionModel } from "../../../../../../../shared/schedule/model/completion-condition-model";
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
   selector: "test-app",
   template: `<task-condition-pane [model]="model" [taskName]="taskName" [parentForm]="form" [taskDefaultTime]="true"></task-condition-pane>`
})
class TestApp {
   @ViewChild(TaskConditionPane, {static: true}) taskConditionPane: TaskConditionPane;
   model = {
      conditions: [{conditionType: "TimeCondition", label: "test condition"}],
      userDefinedClasses: [],
      userDefinedClassLabels: [],
      allTasks: ["Task1", "Task2", "Task3"]
   };
   taskName = "Task1";
   form = new FormControl();
}

describe("Task Condition Pane Unit Test", () => {
   let ngbService = { open: jest.fn() };
   let httpService = { get: jest.fn(), post: jest.fn() };
   let responseObservable = new BehaviorSubject(new Subject());
   httpService.get.mockImplementation(() => responseObservable);
   httpService.post.mockImplementation(() => responseObservable);

   let fixture: ComponentFixture<TestApp>;
   let taskConditionPane: TaskConditionPane;

   beforeEach(async(() => {
      TestBed.configureTestingModule({
         imports: [
            FormsModule, ReactiveFormsModule, NgbModule, HttpClientModule
         ],
         declarations: [
            TestApp, ReplaceAllPipe, TaskConditionPane, ParameterTable, EditableTableComponent,
            AddParameterDialog, EnterSubmitDirective, DateValueEditorComponent,
            TimeValueEditorComponent, TimeInstantValueEditorComponent, TimepickerComponent,
            NotificationsComponent, StartTimeEditor
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
      TestBed.compileComponents();
   }));

   beforeEach(() => {
      fixture = TestBed.createComponent(TestApp);
      taskConditionPane = <TaskConditionPane>fixture.componentInstance.taskConditionPane;
      taskConditionPane.form = new FormGroup({});
      taskConditionPane.convertTime = jest.fn().mockImplementation((a, b, c) => {
         return a;
      });
      fixture.detectChanges();
   });

   //Bug #19519 should show current date when not set
   //Bug #19687 should show set date
   xit("should show correct date in run once", () => {
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
   xit("select and deselect all should work in weekly condition", () => {
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
   xit("select and deselect all should work in monthly condition", () => {
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
   xit("select and deselect all should work in hourly condition", () => {
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
   xit("should not load self in chaind condition", () => { // broken test
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
   xit("should pop up warning when to delete condition", () => {
      let showConfirmDialog = jest.spyOn(ComponentTool, "showConfirmDialog");
      showConfirmDialog.mockImplementation(() => Promise.resolve("ok"));
      taskConditionPane.deleteCondition();

      expect(showConfirmDialog).toHaveBeenCalled();
   });

   //Bug #19891 should not pop up warning when select all
   xit("should not pop up warning when select all", () => {
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
   xit("should load default value for user defined condition", () => {
      taskConditionPane.model.userDefinedClassLabels = ["Test1", "Test2"];
      taskConditionPane.changeConditionType(4);
      fixture.detectChanges();

      let conditions = fixture.nativeElement.querySelectorAll("select#conditionClass option");
      expect(TestUtils.toString(conditions[0].textContent.trim())).toBe("Choose a condition");
      expect(conditions[1].textContent.trim()).toBe("Test1");
      expect(conditions[1].value).toBe("Test1");
   });

   //Bug #19899 should disable delete when no condition selected for multiple schedule
   xit("should disable delete when no condition selected for multiple schedule", () => {
      fixture.componentInstance.model.conditions =
         [{conditionType: "TimeCondition", label: "TimeCondition: 05:30:00, every 1 day(s)"},
            {conditionType: "TimeCondition", label: "TimeCondition: Sunday of Week, every 1 week(s)"}];
      taskConditionPane.listView = true;
      taskConditionPane.selectedConditions = [];
      fixture.detectChanges();

      let delBtn = fixture.debugElement.query(By.css("button.delete-button-id")).nativeElement;
      expect(delBtn.hasAttribute("disabled")).toBeTruthy();
   });

   //Bug #19860 should keep last condition
   xit("should keep last condition", () => {
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
