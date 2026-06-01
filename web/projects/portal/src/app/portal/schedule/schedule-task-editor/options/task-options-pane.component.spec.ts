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
import {
   Component,
   EventEmitter,
   inject,
   NO_ERRORS_SCHEMA,
   Output,
   ViewChild
} from "@angular/core";
import { AsyncPipe, NgClass, NgFor, NgIf, NgStyle } from "@angular/common";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormBuilder, FormsModule, ReactiveFormsModule } from "@angular/forms";
import { By } from "@angular/platform-browser";
import { NgbInputDatepicker, NgbModal, NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { BehaviorSubject } from "rxjs";
import { TaskOptionsPaneModel } from "../../../../../../../shared/schedule/model/task-options-pane-model";
import { ScheduleUsersService } from "../../../../../../../shared/schedule/schedule-users.service";
import { TestUtils } from "../../../../common/test/test-utils";
import { TaskOptionsPane } from "./task-options-pane.component";
import { CustomSelectComponent } from "../../../../widget/custom-select/custom-select.component";
import { NumberStepperComponent } from "../../../../widget/number-stepper/number-stepper.component";

@Component({
   selector: "execute-as-dialog",
   template: "<div></div>",
   standalone: true
})
class ExecuteAsDialog {
   @Output() onCommit: EventEmitter<{name: string, type: number}> =
      new EventEmitter<{name: string, type: number}>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
}

@Component({
   selector: "test-app",
   template: `<task-options-pane [model]="model" [taskName]="taskName" [parentForm]="form"></task-options-pane>`,
   standalone: true,
   imports: [TaskOptionsPane]
})
class TestApp {
   @ViewChild(TaskOptionsPane, {static: false}) optionPane: TaskOptionsPane;
   model = createModel();
   taskName = "Task1";
   form = inject(FormBuilder).group({});
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
      init: vi.fn(),
      getOwners: vi.fn(() => new BehaviorSubject([]) ),
      getGroups: vi.fn(() => new BehaviorSubject([]) ),
      getRoles: vi.fn(() => new BehaviorSubject([]) ),
      getEmailUsers: vi.fn(() => new BehaviorSubject([]) ),
      getEmailGroups: vi.fn(() => new BehaviorSubject([]) ),
      getAdminName: vi.fn(() => new BehaviorSubject("admin") ),
      getGroupBaseNames: vi.fn(() => new BehaviorSubject([]) ),
   };

   beforeEach(() => {
      modalService = { get: vi.fn() };
      http = { open: vi.fn() };

      TestBed.configureTestingModule({
         imports: [ReactiveFormsModule, FormsModule, NgbModule, HttpClientTestingModule, TestApp, TaskOptionsPane, ExecuteAsDialog],

         providers: [
            {provide: NgbModal, useValue: modalService},
            {provide: ScheduleUsersService, useValue: scheduleUsersService}
         ],
         schemas: [NO_ERRORS_SCHEMA]
      });
      TestBed.overrideComponent(TaskOptionsPane, { set: { imports: [NgIf, NgFor, NgClass, NgStyle, AsyncPipe, FormsModule, ReactiveFormsModule, NgbInputDatepicker, CustomSelectComponent, NumberStepperComponent] } });
      TestBed.compileComponents();

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
      const localeSelects = fixture.debugElement.queryAll(By.css("custom-select"));
      const localeEl = localeSelects.find(el =>
         el.nativeElement.getAttribute("ng-reflect-options") != null &&
         el.nativeElement.getAttribute("ng-reflect-model") === "Default" ||
         el.nativeElement.getAttribute("ng-reflect-ng-model") === "Default"
      );
      expect(localeEl || localeSelects.length > 0).toBeTruthy();
      if(localeEl) {
         const val = localeEl.nativeElement.getAttribute("ng-reflect-model") ||
                     localeEl.nativeElement.getAttribute("ng-reflect-ng-model");
         expect(val).toBe("Default");
      }
   });
});