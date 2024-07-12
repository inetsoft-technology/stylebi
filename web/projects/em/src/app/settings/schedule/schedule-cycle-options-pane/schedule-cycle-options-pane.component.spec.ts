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
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { MatButtonModule } from "@angular/material/button";
import { MatCardModule } from "@angular/material/card";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { MatDialogModule } from "@angular/material/dialog";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatInputModule } from "@angular/material/input";
import { MatSnackBarModule } from "@angular/material/snack-bar";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { ScheduleUsersService } from "../../../../../../shared/schedule/schedule-users.service";
import { EmailPickerModule } from "../../email-picker/email-picker.module";
import { ScheduleCycleOptionsPaneComponent } from "./schedule-cycle-options-pane.component";
import { BehaviorSubject } from "rxjs";

describe("ScheduleCycleOptionsPaneComponent", () => {
   let component: ScheduleCycleOptionsPaneComponent;
   let fixture: ComponentFixture<ScheduleCycleOptionsPaneComponent>;

   beforeEach(async(() => {
      const scheduleUsersService = {
         getGroups: jest.fn(() => new BehaviorSubject([]) ),
         getEmailUsers: jest.fn(() => new BehaviorSubject([]) ),
         getEmailGroups: jest.fn(() => new BehaviorSubject([]) ),
      };

      TestBed.configureTestingModule({
         imports: [
            HttpClientTestingModule,
            FormsModule,
            ReactiveFormsModule,
            NoopAnimationsModule,
            MatButtonModule,
            MatCheckboxModule,
            MatCardModule,
            MatDialogModule,
            MatFormFieldModule,
            MatInputModule,
            MatSnackBarModule,
            EmailPickerModule
         ],
         declarations: [
            ScheduleCycleOptionsPaneComponent
         ],
         providers: [
            { provide: ScheduleUsersService, useValue: scheduleUsersService}
         ],
         schemas: [
            NO_ERRORS_SCHEMA
         ]
      })
         .compileComponents();
   }));

   beforeEach(() => {
      fixture = TestBed.createComponent(ScheduleCycleOptionsPaneComponent);
      component = fixture.componentInstance;
      component.info = {
         name: "Cycle",
         startNotify: false,
         startEmail: null,
         endNotify: false,
         endEmail: null,
         failureNotify: false,
         failureEmail: null,
         exceedNotify: false,
         threshold: 0,
         exceedEmail: null
      };
      fixture.detectChanges();
   });

   it("should create", () => {
      expect(component).toBeTruthy();
   });
});
