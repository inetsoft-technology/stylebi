/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
import { provideHttpClient } from "@angular/common/http";
import { provideHttpClientTesting } from "@angular/common/http/testing";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import dayjs from "dayjs";
import { TimeConditionModel, TimeConditionType } from "../../../../../../../shared/schedule/model/time-condition-model";
import { TimeZoneService } from "../../../../../../../shared/schedule/time-zone.service";
import { DateTimeService } from "../date-time.service";
import { TaskConditionChanges } from "../task-condition-pane.component";
import { RunOnceConditionEditorComponent } from "./run-once-condition-editor.component";

/**
 * Bug #78728: fireModelChanged runs inside a control's valueChanges (the datepicker's date
 * control or startTime), before the parent groups recompute their status. The emitted valid
 * flag must reflect the form after the edit, not before it.
 */
describe("RunOnceConditionEditorComponent — Bug #78728: emitted valid is not stale", () => {
   let fixture: ComponentFixture<RunOnceConditionEditorComponent>;
   let comp: RunOnceConditionEditorComponent;
   let emitted: TaskConditionChanges[];

   beforeEach(async () => {
      await TestBed.configureTestingModule({
         imports: [RunOnceConditionEditorComponent, NoopAnimationsModule],
         providers: [
            provideHttpClient(),
            provideHttpClientTesting(),
            DateTimeService,
            { provide: TimeZoneService, useValue: { calculateTimezoneOffset: () => 0 } },
         ],
      }).compileComponents();

      fixture = TestBed.createComponent(RunOnceConditionEditorComponent);
      comp = fixture.componentInstance;
      comp.condition = {
         type: TimeConditionType.AT,
         conditionType: "TimeCondition",
         label: "",
         date: Date.UTC(2026, 9, 7, 9, 30),
         timeZoneOffset: 0,
         timeZone: "UTC",
         timeZoneLabel: "UTC",
         hour: 9,
         minute: 30,
         second: 0,
      } as TimeConditionModel;
      fixture.detectChanges();

      emitted = [];
      comp.modelChanged.subscribe(e => emitted.push(e));
   });

   it("emits valid=true when a date is entered into an empty date field", () => {
      const dateControl = comp.form.get("date.date");

      // An empty or incomplete date leaves the required control null (no emit).
      dateControl.setValue(null);
      expect(emitted.length).toBe(0);

      dateControl.setValue(dayjs("2026-10-20", "YYYY-MM-DD"));

      expect(emitted.length).toBe(1);
      expect(emitted[0].valid).toBe(true);
      expect(new Date((emitted[0].model as TimeConditionModel).date).toISOString())
         .toBe("2026-10-20T09:30:00.000Z");
   });

   it("emits valid=false when the start time is cleared", () => {
      comp.form.get("startTime").setValue(null);

      expect(emitted.length).toBe(1);
      expect(emitted[0].valid).toBe(false);
   });
});
