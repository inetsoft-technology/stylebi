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

/**
 * HourlyConditionEditorComponent — Testing Library style
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — fireModelChanged: valid = form.valid
 *                       (timeChronological + weekdays required + interval > 0)
 *   Group 2 [Risk 3] — timeChronological: cross-field start-before-end validator
 *   Group 3 [Risk 2] — condition setter: startTime / endTime / interval / weekdays initialization
 *   Group 4 [Risk 2] — fireModelChanged: TZ change shifts BOTH startTime AND endTime form controls
 *
 * KEY contracts:
 *   - fireModelChanged emits valid = form.valid (no separate startTimeValid gate — unlike Daily/Weekly).
 *   - timeChronological sets {timeChronological: true} on the form group when
 *     startDate >= endDate (equal times also fail — strict less-than required).
 *   - weekdays control has Validators.required — empty array [] → form invalid.
 *   - interval uses positiveNonZeroInRange — 0 → invalid, 1 → valid.
 *   - TZ shift: applyTimeZoneOffsetDifference is called on BOTH startTime AND endTime form
 *     controls inside fireModelChanged.  A missing second call silently leaves endTime in
 *     the old timezone while startTime shifts, corrupting the execution window.
 *   - Asia/Kolkata (IST = UTC+5:30, no DST) gives deterministic +5h30m / -5h30m shifts.
 *   - Condition setter uses getStartTime / getEndTime which override h/m/s from condition
 *     fields, making the form values timezone-independent.
 */

import { Component, forwardRef, NO_ERRORS_SCHEMA } from "@angular/core";
import { ControlValueAccessor, NG_VALUE_ACCESSOR, ReactiveFormsModule } from "@angular/forms";
import { ErrorStateMatcher } from "@angular/material/core";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { render } from "@testing-library/angular";

import { HourlyConditionEditorComponent } from "./hourly-condition-editor.component";
import { DateTimeService } from "../date-time.service";
import { TimeConditionModel, TimeConditionType } from "../../../../../../../shared/schedule/model/time-condition-model";
import { TaskConditionChanges } from "../task-condition-pane.component";

// ---------------------------------------------------------------------------
// Stubs
// ---------------------------------------------------------------------------

@Component({
   selector: "em-time-zone-select",
   template: "",
   providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TimeZoneSelectStub), multi: true }]
})
class TimeZoneSelectStub implements ControlValueAccessor {
   writeValue() {} registerOnChange() {} registerOnTouched() {}
}

// mat-select used for formControlName="weekdays" — needs CVA stub.
@Component({
   selector: "mat-select",
   template: "",
   providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => MatSelectStub), multi: true }]
})
class MatSelectStub implements ControlValueAccessor {
   writeValue() {} registerOnChange() {} registerOnTouched() {}
}

@Component({
   selector: "em-time-picker",
   template: "",
   providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TimePickerStub), multi: true }]
})
class TimePickerStub implements ControlValueAccessor {
   writeValue() {} registerOnChange() {} registerOnTouched() {}
}

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

function makeCondition(overrides: Partial<TimeConditionModel> = {}): TimeConditionModel {
   return {
      type: TimeConditionType.EVERY_HOUR,
      conditionType: "TimeCondition",
      label: "",
      hour: 9,
      minute: 0,
      second: 0,
      hourEnd: 17,
      minuteEnd: 0,
      secondEnd: 0,
      date: 0,
      timeZoneOffset: 0,
      hourlyInterval: 1,
      daysOfWeek: [2, 3, 4, 5, 6], // Mon–Fri
      timeZone: "",
      timeZoneLabel: "",
      ...overrides,
   };
}

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

async function renderComponent(props: Partial<HourlyConditionEditorComponent> = {}) {
   const result = await render(HourlyConditionEditorComponent, {
      imports: [ReactiveFormsModule, NoopAnimationsModule],
      declarations: [TimeZoneSelectStub, MatSelectStub, TimePickerStub],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         DateTimeService,
         { provide: ErrorStateMatcher, useValue: { isErrorState: () => false } },
      ],
      componentProperties: {
         condition: makeCondition(),
         ...props,
      },
   });

   await result.fixture.whenStable();
   return { ...result, comp: result.fixture.componentInstance };
}

// ---------------------------------------------------------------------------
// Group 1 [Risk 3] — fireModelChanged: valid = form.valid
// ---------------------------------------------------------------------------

describe("HourlyConditionEditorComponent — fireModelChanged: valid = form.valid", () => {

   // 🔁 Regression-sensitive: three independent validity gates must ALL pass:
   // startTime < endTime, weekdays non-empty, interval > 0.
   it("should emit valid=true when startTime < endTime, interval=1, and weekdays are non-empty", async () => {
      const { comp } = await renderComponent({
         condition: makeCondition({ hour: 9, hourEnd: 17, hourlyInterval: 1, daysOfWeek: [2, 3, 4, 5, 6] }),
      });

      const emitted: TaskConditionChanges[] = [];
      comp.modelChanged.subscribe(e => emitted.push(e));

      comp.fireModelChanged();

      expect(emitted.length).toBeGreaterThan(0);
      expect(emitted.at(-1).valid).toBe(true);
   });

   // 🔁 Regression-sensitive: startTime === endTime violates the strict less-than requirement
   // of timeChronological.  A >= instead of > guard would silently allow zero-width windows,
   // causing the scheduler to fire the task on every poll tick rather than once per interval.
   it("should emit valid=false when startTime equals endTime (timeChronological violation)", async () => {
      const { comp } = await renderComponent({
         condition: makeCondition({ hour: 9, minute: 0, second: 0, hourEnd: 9, minuteEnd: 0, secondEnd: 0 }),
      });

      const emitted: TaskConditionChanges[] = [];
      comp.modelChanged.subscribe(e => emitted.push(e));

      comp.fireModelChanged();

      expect(emitted.length).toBeGreaterThan(0);
      expect(emitted.at(-1).valid).toBe(false);
      expect(comp.form.errors?.timeChronological).toBe(true);
   });

   // 🔁 Regression-sensitive: empty weekdays fails Validators.required (isEmptyInputValue([]) = true).
   // Removing this guard would allow scheduling an hourly task that never runs on any day.
   it("should emit valid=false when weekdays is empty", async () => {
      const { comp } = await renderComponent({
         condition: makeCondition({ daysOfWeek: [] }),
      });

      const emitted: TaskConditionChanges[] = [];
      comp.modelChanged.subscribe(e => emitted.push(e));

      comp.fireModelChanged();

      expect(emitted.length).toBeGreaterThan(0);
      expect(emitted.at(-1).valid).toBe(false);
      expect(comp.form.get("weekdays").invalid).toBe(true);
      expect(comp.form.errors?.timeChronological).toBeFalsy(); // isolated to weekdays
   });

   // 🔁 Regression-sensitive: interval=0 fails positiveNonZeroInRange.
   // A zero interval would cause the scheduler to execute the task in an infinite tight loop.
   it("should emit valid=false when interval is 0", async () => {
      const { comp } = await renderComponent({
         condition: makeCondition({ hourlyInterval: 0 }),
      });

      const emitted: TaskConditionChanges[] = [];
      comp.modelChanged.subscribe(e => emitted.push(e));

      comp.fireModelChanged();

      expect(emitted.length).toBeGreaterThan(0);
      expect(emitted.at(-1).valid).toBe(false);
      expect(comp.form.get("interval").invalid).toBe(true);
   });

});

// ---------------------------------------------------------------------------
// Group 2 [Risk 3] — timeChronological: cross-field start-before-end validator
// ---------------------------------------------------------------------------

describe("HourlyConditionEditorComponent — timeChronological: cross-field start-before-end validator", () => {

   // 🔁 Regression-sensitive: startTime strictly before endTime must produce no form-level error.
   // This is the happy path — any regression here blocks all valid hourly schedules.
   it("should produce no timeChronological error when startTime is strictly before endTime", async () => {
      const { comp } = await renderComponent({
         condition: makeCondition({ hour: 9, minute: 0, hourEnd: 17, minuteEnd: 0 }),
      });

      expect(comp.form.errors?.timeChronological).toBeFalsy();
      expect(comp.form.valid).toBe(true);
   });

   // 🔁 Regression-sensitive: equal times use >= comparison → must fail.  A > comparison
   // would let a zero-duration window pass validation and reach the server scheduler.
   it("should set timeChronological error when startTime equals endTime (boundary: >= not >)", async () => {
      const { comp } = await renderComponent({
         condition: makeCondition({ hour: 12, minute: 0, second: 0, hourEnd: 12, minuteEnd: 0, secondEnd: 0 }),
      });

      expect(comp.form.errors?.timeChronological).toBe(true);
   });

   // Error: start after end → timeChronological error.
   it("should set timeChronological error when startTime is after endTime", async () => {
      const { comp } = await renderComponent({
         condition: makeCondition({ hour: 17, minute: 0, hourEnd: 9, minuteEnd: 0 }),
      });

      expect(comp.form.errors?.timeChronological).toBe(true);
   });

});

// ---------------------------------------------------------------------------
// Group 3 [Risk 2] — condition setter: form control initialization
// ---------------------------------------------------------------------------

describe("HourlyConditionEditorComponent — condition setter: form control initialization", () => {

   // 🔁 Regression-sensitive: startTime and endTime form controls must be initialized from
   // condition.hour/minute/second and condition.hourEnd/minuteEnd/secondEnd respectively.
   // A missed initialization leaves stale defaults — subsequent fireModelChanged() would
   // emit the wrong window boundaries to the server.
   it("should initialize startTime and endTime form controls from condition fields", async () => {
      const { comp } = await renderComponent({
         condition: makeCondition({
            hour: 9, minute: 0, second: 0,
            hourEnd: 17, minuteEnd: 30, secondEnd: 0
         }),
      });

      expect(comp.form.get("startTime").value).toBe("09:00:00");
      expect(comp.form.get("endTime").value).toBe("17:30:00");
   });

   // 🔁 Regression-sensitive: interval and weekdays must also be patched from the condition.
   it("should initialize interval and weekdays form controls from condition fields", async () => {
      const { comp } = await renderComponent({
         condition: makeCondition({ hourlyInterval: 2, daysOfWeek: [2, 4] }),
      });

      expect(comp.form.get("interval").value).toBe(2);
      expect(comp.form.get("weekdays").value).toEqual([2, 4]);
   });

});

// ---------------------------------------------------------------------------
// Group 4 [Risk 2] — fireModelChanged: TZ change shifts BOTH startTime AND endTime
// ---------------------------------------------------------------------------

describe("HourlyConditionEditorComponent — fireModelChanged: TZ change shifts BOTH startTime and endTime", () => {

   // 🔁 Regression-sensitive: unlike Daily/Weekly which only shift startTime, the hourly editor
   // must call applyTimeZoneOffsetDifference on BOTH form controls.
   // TODO: after the applyTimeZoneOffsetDifference empty-TZ bug is fixed, change timeZone
   // from "UTC" to "" — expected values remain the same if "" is treated as UTC internally.
   it("should shift both startTime and endTime forward by 5h30m when TZ changes from UTC to Asia/Kolkata", async () => {
      const { comp } = await renderComponent({
         condition: makeCondition({
            // Using "UTC" instead of "" to avoid the empty-TZ crash bug (see date-time.service Group 6).
            timeZone: "UTC",
            hour: 9, minute: 0, second: 0,
            hourEnd: 17, minuteEnd: 0, secondEnd: 0,
         }),
      });

      comp.form.get("timeZone").setValue({ timeZoneId: "Asia/Kolkata", timeZoneLabel: "IST" });
      comp.fireModelChanged();

      expect(comp.form.get("startTime").value).toBe("14:30:00"); // 09:00 + 5:30
      expect(comp.form.get("endTime").value).toBe("22:30:00");   // 17:00 + 5:30
   });

   // 🔁 Regression-sensitive: the reverse shift confirms symmetry — IST→UTC must subtract 5h30m
   // from both controls, proving neither endTime nor startTime is accidentally skipped.
   it("should shift both startTime and endTime back by 5h30m when TZ changes from Asia/Kolkata to UTC", async () => {
      const { comp } = await renderComponent({
         condition: makeCondition({
            timeZone: "Asia/Kolkata",
            hour: 14, minute: 30, second: 0,
            hourEnd: 22, minuteEnd: 30, secondEnd: 0,
         }),
      });

      comp.form.get("timeZone").setValue({ timeZoneId: "UTC", timeZoneLabel: "UTC" });
      comp.fireModelChanged();

      expect(comp.form.get("startTime").value).toBe("09:00:00"); // 14:30 - 5:30
      expect(comp.form.get("endTime").value).toBe("17:00:00");   // 22:30 - 5:30
   });

});
