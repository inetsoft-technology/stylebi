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
import { HttpClientTestingModule } from "@angular/common/http/testing";
import { Component, EventEmitter, NO_ERRORS_SCHEMA, Output, ViewChild } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormControl, FormsModule, ReactiveFormsModule } from "@angular/forms";
import { By } from "@angular/platform-browser";
import { NgbModal, NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { BehaviorSubject } from "rxjs";
import { TaskOptionsPaneModel } from "../../../../../../../shared/schedule/model/task-options-pane-model";
import { ScheduleUsersService } from "../../../../../../../shared/schedule/schedule-users.service";
import { TestUtils } from "../../../../common/test/test-utils";
import { TaskOptionsPane } from "./task-options-pane.component";
import { IdentityId} from "../../../../../../../em/src/app/settings/security/users/identity-id";

@Component({
   selector: "execute-as-dialog",
   template: "<div></div>"
})
class ExecuteAsDialog {
   @Output() onCommit: EventEmitter<{name: string, type: number}> =
      new EventEmitter<{name: string, type: number}>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
}


@Component({
   selector: "test-app",
   template: `<task-options-pane [model]="model" [taskName]="taskName" [parentForm]="form"></task-options-pane>`
})
class TestApp {
   @ViewChild(TaskOptionsPane, {static: false}) optionPane: TaskOptionsPane;
   model = createModel();
   taskName = "Task1";
   form = new FormControl();
}

let createModel: () => TaskOptionsPaneModel = () => {
   return {
      adminName: null,
      organizationName: null,
      deleteIfNotScheduledToRun: false,
      description: null,
      enabled: true,
      idName: null,
      idType: 0,
      locale: null,
      locales: null,
      owner: "anonymous",
      securityEnabled: true,
      startFrom: 0,
      stopOn: 0
   };
};

describe("task options pane componnet unit case: ", () => {
   let fixture: ComponentFixture<TestApp>;
   let optionPane: TaskOptionsPane;
   let modalService: any;
   let http: any;
   let scheduleUsersService = {
      init: jest.fn(),
      getOwners: jest.fn(() => new BehaviorSubject([]) ),
      getGroups: jest.fn(() => new BehaviorSubject([]) ),
      getRoles: jest.fn(() => new BehaviorSubject([]) ),
      getEmailUsers: jest.fn(() => new BehaviorSubject([]) ),
      getEmailGroups: jest.fn(() => new BehaviorSubject([]) ),
      getAdminName: jest.fn(() => new BehaviorSubject("admin") ),
      getGroupBaseNames: jest.fn(() => new BehaviorSubject([]) ),
   };

   beforeEach(() => {
      modalService = { get: jest.fn() };
      http = { open: jest.fn() };

      TestBed.configureTestingModule({
         imports: [ReactiveFormsModule, FormsModule, NgbModule, HttpClientTestingModule],
         declarations: [TestApp, TaskOptionsPane, ExecuteAsDialog],
         providers: [
            {provide: NgbModal, useValue: modalService},
            {provide: ScheduleUsersService, useValue: scheduleUsersService}
         ],
         schemas: [NO_ERRORS_SCHEMA]
      }).compileComponents();

      fixture = TestBed.createComponent(TestApp);
      fixture.detectChanges();
   });

   //Bug #19508
   //Bug #19745
   it("start date should less than end date", () => {
      let startDate: HTMLInputElement = fixture.debugElement.query(By.css("input[name='startDate']")).nativeElement;
      let endDate: HTMLInputElement = fixture.debugElement.query(By.css("input[name='endDate']")).nativeElement;
      startDate.value = "2017-11-08";
      startDate.dispatchEvent(new Event("input"));
      endDate.value = "2017-11-07";
      endDate.dispatchEvent(new Event("input"));
      fixture.detectChanges();

      let alert = fixture.debugElement.query(By.css(".invalid-feedback")).nativeElement;
      expect(alert).toBeDefined();
      expect(TestUtils.toString(alert.textContent)).toBe("stop.after.start.date");

      endDate.value = "2017-11-10";
      endDate.dispatchEvent(new Event("input"));
      fixture.detectChanges();

      //Bug #19508
      expect(fixture.componentInstance.model.startFrom).not.toBe(0);
      expect(fixture.componentInstance.model.stopOn).not.toBe(0);

      //Bug #21420 should get correct locale info when set 'Default'
      let locale = fixture.nativeElement.querySelectorAll(
         "div.form-row-float-label.row.form-group")[3];
      let defaultElement = locale.querySelectorAll("select")[0];
      expect(defaultElement.getAttribute("ng-reflect-model")).toBe("Default");
   });
});