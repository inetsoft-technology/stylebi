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
import { HttpClient } from "@angular/common/http";
import { Component, NO_ERRORS_SCHEMA, ViewChild } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { By } from "@angular/platform-browser";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { BehaviorSubject, Subject } from "rxjs";
import { TestUtils } from "../../../common/test/test-utils";
import { EnterSubmitDirective } from "../../../widget/directive/enter-submit.directive";
import { ReplaceAllPipe } from "../../../widget/pipe/replace-all.pipe";
import { TaskActionPane } from "./actions/task-action-pane.component";
import { AddParameterDialog } from "./add-parameter-dialog/add-parameter-dialog.component";
import { TaskConditionPane } from "./conditions/task-condition-pane.component";
import { EditableTableComponent } from "./editable-table/editable-table.component";
import { TaskOptionsPane } from "./options/task-options-pane.component";
import { ParameterTable } from "./parameter-table/parameter-table.component";
import { ScheduleTaskDialog } from "./schedule-task-dialog.component";

@Component({
   selector: "test-app",
   template: `<schedule-task-dialog [model]="model"></schedule-task-dialog>`
})
class TestApp {
   @ViewChild(ScheduleTaskDialog, {static: false}) scheduleTaskDialog: ScheduleTaskDialog;
   model = {
      name: "",
      label: "",
      timeZone: "",
      taskActionPaneModel: null,
      taskConditionPaneModel: {
         conditions: [{taskName: "Task1", conditionType: "CompletionCondition", label: "TimeCondition: 01:30:00, every 1 day(s)"}],
         userDefinedClasses: [],
         userDefinedClassLabels: [],
         allTasks: ["Task1", "Task2", "Task3"]
      },
      taskOptionsPaneModel: null
      };
}

describe("Schedule Task Dialog Unit Test", () => {
   let fixture: ComponentFixture<TestApp>;
   let scheduleTaskDialog: ScheduleTaskDialog;

   let httpService = { get: jest.fn(), post: jest.fn() };
   let responseObservable = new BehaviorSubject(new Subject());
   httpService.get.mockImplementation(() => responseObservable);
   httpService.post.mockImplementation(() => responseObservable);

   beforeEach(async(() => {
      TestBed.configureTestingModule({
         imports: [
            FormsModule, ReactiveFormsModule, NgbModule
         ],
         declarations: [
            TestApp, ReplaceAllPipe, ScheduleTaskDialog, TaskActionPane, TaskConditionPane,
            TaskOptionsPane, EnterSubmitDirective, ParameterTable, EditableTableComponent,
            AddParameterDialog
         ],
         providers: [{ provide: HttpClient, useValue: httpService }],
         schemas: [ NO_ERRORS_SCHEMA ]
      });
      TestBed.compileComponents();

      fixture = TestBed.createComponent(TestApp);
      scheduleTaskDialog = <ScheduleTaskDialog>fixture.componentInstance.scheduleTaskDialog;
      fixture.detectChanges();
   }));

   //Bug #21217 task name control
   it("task name control", () => { // broken
      let name = fixture.debugElement.query(By.css("input#taskName")).nativeElement;
      name.value = "Task4.1";
      name.dispatchEvent(new Event("input"));
      fixture.detectChanges();

      let warning = fixture.debugElement.query(By.css("div.alert.alert-danger"));
      expect(warning).toBeNull();

      name.value = "Task4.1%";
      name.dispatchEvent(new Event("input"));
      fixture.detectChanges();
      let warning0 = fixture.debugElement.query(By.css("div.alert.alert-danger")).nativeElement;
      expect(TestUtils.toString(warning0.textContent.trim())).toBe("vs.basicGeneral.nameCheck");
   });
});