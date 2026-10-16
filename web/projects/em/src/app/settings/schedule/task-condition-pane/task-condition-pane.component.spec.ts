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
import { waitForAsync, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { MatButtonToggleModule } from "@angular/material/button-toggle";
import { MatCardModule } from "@angular/material/card";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { MatNativeDateModule, MatOptionModule } from "@angular/material/core";
import { MatDatepickerModule } from "@angular/material/datepicker";
import { MatDividerModule } from "@angular/material/divider";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatIconModule } from "@angular/material/icon";
import { MatInputModule } from "@angular/material/input";
import { MatRadioModule } from "@angular/material/radio";
import { MatSelectModule } from "@angular/material/select";
import { MatTableModule } from "@angular/material/table";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { ParameterTableComponent } from "../parameter-table/parameter-table.component";
import {
   TimeConditionModel,
   TimeConditionType
} from "../../../../../../shared/schedule/model/time-condition-model";
import {
   TaskConditionChanges,
   TaskConditionPaneComponent,
   TaskConditionType
} from "./task-condition-pane.component";

describe("TaskConditionPaneComponent", () => {
   let component: TaskConditionPaneComponent;
   let fixture: ComponentFixture<TaskConditionPaneComponent>;

   beforeEach(waitForAsync(() => {
      TestBed.configureTestingModule({
         imports: [
            HttpClientTestingModule,
            FormsModule,
            ReactiveFormsModule,
            NoopAnimationsModule,
            MatButtonToggleModule,
            MatCardModule,
            MatCheckboxModule,
            MatDatepickerModule,
            MatDividerModule,
            MatFormFieldModule,
            MatIconModule,
            MatInputModule,
            MatOptionModule,
            MatNativeDateModule,
            MatRadioModule,
            MatSelectModule,
            MatTableModule,
            TaskConditionPaneComponent, ParameterTableComponent],
         schemas: [
            NO_ERRORS_SCHEMA
         ]
      })
      .overrideTemplate(TaskConditionPaneComponent, "")
      .compileComponents();
   }));

   beforeEach(() => {
      fixture = TestBed.createComponent(TaskConditionPaneComponent);
      component = fixture.componentInstance;
      component.selectedConditionType = component.conditionTypes[0];
      fixture.detectChanges();
   });

   it("should create", () => {
      expect(component).toBeTruthy();
   });

   // Bug #78224, a type change must keep the stored condition's originalIndex, or the save
   // treats it as a new condition and drops its stored start time and time range.
   describe("changeConditionType keeps the condition's originalIndex", () => {
      let emitted: TaskConditionChanges[];

      beforeEach(() => {
         emitted = [];
         component.timeZoneOptions = [
            { timeZoneId: "UTC", label: "UTC", hourOffset: "+00", minuteOffset: 0 }
         ];
         component.modelChanged.subscribe((change: TaskConditionChanges) => emitted.push(change));
      });

      function storedDaily(originalIndex?: number): TimeConditionModel {
         return <TimeConditionModel> {
            label: "Daily 09:00",
            conditionType: "TimeCondition",
            type: TimeConditionType.EVERY_DAY,
            hour: 9,
            minute: 0,
            second: 0,
            interval: 1,
            originalIndex
         };
      }

      function selectType(predicate: (type: TaskConditionType) => boolean): void {
         component.selectedConditionType = component.conditionTypes.find(predicate);
         component.changeConditionType();
      }

      const weekly = (t: TaskConditionType) => t.subtype === TimeConditionType.EVERY_WEEK;
      const daily = (t: TaskConditionType) => t.subtype === TimeConditionType.EVERY_DAY;
      const chained = (t: TaskConditionType) => t.type === "CompletionCondition";

      it("keeps it on Daily -> Weekly -> Daily", () => {
         component.condition = storedDaily(0);

         selectType(weekly);
         expect(emitted[0].model.originalIndex).toBe(0);
         expect((<TimeConditionModel> emitted[0].model).type).toBe(TimeConditionType.EVERY_WEEK);

         selectType(daily);
         const model = <TimeConditionModel> emitted[1].model;
         expect(model.originalIndex).toBe(0);
         expect(model.type).toBe(TimeConditionType.EVERY_DAY);
      });

      it("keeps it on Daily -> Chained -> Daily", () => {
         component.condition = storedDaily(2);

         selectType(chained);
         expect(emitted[0].model.conditionType).toBe("CompletionCondition");
         expect(emitted[0].model.originalIndex).toBe(2);

         selectType(daily);
         expect(emitted[1].model.conditionType).toBe("TimeCondition");
         expect(emitted[1].model.originalIndex).toBe(2);
      });

      it("leaves a new condition without an originalIndex", () => {
         component.condition = storedDaily(undefined);

         selectType(weekly);
         expect(emitted[0].model.originalIndex).toBeUndefined();

         selectType(chained);
         expect(emitted[1].model.originalIndex).toBeUndefined();
         expect("originalIndex" in emitted[1].model).toBe(false);
      });
   });
});
