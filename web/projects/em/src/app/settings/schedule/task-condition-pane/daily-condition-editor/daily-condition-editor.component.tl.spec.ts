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
 * DailyConditionEditorComponent — Testing Library style
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — fireModelChanged: emitted valid = form.valid && startTimeValid
 *   Group 2 [Risk 2] — condition setter: form patching and startTimeData initialization
 *   Group 3 [Risk 2] — initFormState: weekdayOnly disables / enables the interval control
 *   Group 4 [Risk 2] — fireModelChanged: timezone change shifts condition.hour/minute/second
 *
 * KEY contracts:
 *   - fireModelChanged emits valid = form.valid && startTimeValid (both required).
 *   - When weekdayOnly=true, initFormState() disables the interval control — a disabled
 *     control is excluded from form.valid, so valid=true even if the interval value is 0.
 *   - The condition setter creates a shallow copy (Object.assign) and early-returns
 *     (Tool.isEquals) when the new value is identical to the current one.
 *   - startTimeSelected = !timeRangeEnabled || !condition.timeRange:
 *     false when timeRangeEnabled=true AND a timeRange is present (time-range mode).
 *   - When both old and new timezone are "" (empty), updateStartTimeDataTimeZone is a no-op
 *     (applyTimeZoneOffsetDifference early-returns), so startTime is preserved unchanged.
 *   - fireModelChanged captures oldTZ = condition.timeZone BEFORE reading the new TZ from the
 *     form, then calls updateStartTimeDataTimeZone(startTimeData, oldTZ, newTZ) → setStartTime,
 *     propagating the timezone delta into condition.hour/minute/second.
 *   - Asia/Kolkata (IST = UTC+5:30, no DST) is used for TZ shift tests because its offset is
 *     constant year-round, making the arithmetic deterministic in any test environment.
 */

import { Component, forwardRef, NO_ERRORS_SCHEMA } from "@angular/core";
import { ControlValueAccessor, NG_VALUE_ACCESSOR, ReactiveFormsModule } from "@angular/forms";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { render } from "@testing-library/angular";

import { DailyConditionEditorComponent } from "./daily-condition-editor.component";
import { DateTimeService } from "../date-time.service";
import { TimeConditionModel, TimeConditionType, TimeRange } from "../../../../../../../shared/schedule/model/time-condition-model";
import { TaskConditionChanges } from "../task-condition-pane.component";
import { StartTimeChange } from "../start-time-editor/start-time-editor.component";

// ---------------------------------------------------------------------------
// Stubs
// ---------------------------------------------------------------------------

// em-time-zone-select uses formControlName="timeZone" — needs a CVA stub.
@Component({
   selector: "em-time-zone-select",
   template: "",
   providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TimeZoneSelectStub), multi: true }]
})
class TimeZoneSelectStub implements ControlValueAccessor {
   writeValue() {} registerOnChange() {} registerOnTouched() {}
}

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

function makeCondition(overrides: Partial<TimeConditionModel> = {}): TimeConditionModel {
   return {
      type: TimeConditionType.EVERY_DAY,
      conditionType: "TimeCondition",
      label: "",
      hour: 8,
      minute: 30,
      second: 0,
      date: 0,
      timeZoneOffset: 0,
      interval: 3,
      weekdayOnly: false,
      timeZone: "",
      timeZoneLabel: "",
      ...overrides,
   };
}

function makeTimeRange(name = "Business Hours"): TimeRange {
   return { name, startTime: "09:00:00", endTime: "17:00:00", defaultRange: false };
}

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

async function renderComponent(props: Partial<DailyConditionEditorComponent> = {}) {
   const result = await render(DailyConditionEditorComponent, {
      imports: [ReactiveFormsModule, MatCheckboxModule, NoopAnimationsModule],
      declarations: [TimeZoneSelectStub],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [DateTimeService],
      componentProperties: {
         condition: makeCondition(),
         ...props,
      },
   });

   await result.fixture.whenStable();

   return { ...result, comp: result.fixture.componentInstance };
}

// ---------------------------------------------------------------------------
// Group 1 [Risk 3] — fireModelChanged: valid = form.valid && startTimeValid
// ---------------------------------------------------------------------------

describe("DailyConditionEditorComponent — fireModelChanged: valid = form.valid && startTimeValid", () => {

   // 🔁 Regression-sensitive: both gates must be true for the parent to allow saving.
   // A valid interval and a non-empty start time are the two required conditions.
   it("should emit valid=true when form is valid and startTimeValid is true", async () => {
      const { comp } = await renderComponent({
         condition: makeCondition({ interval: 3, timeZone: "UTC", timeZoneLabel: "UTC" })
      });

      const emitted: TaskConditionChanges[] = [];
      comp.modelChanged.subscribe(e => emitted.push(e));

      // Also verify timezone fields are propagated from form to emitted model.
      comp.form.get("timeZone").setValue({
         timeZoneId: "UTC",
         timeZoneLabel: "UTC"
      });

      comp.fireModelChanged();

      expect(emitted.length).toBeGreaterThan(0);
      expect(emitted.at(-1).valid).toBe(true);
      const model = emitted.at(-1).model as TimeConditionModel;
      expect(model.timeZone).toBe("UTC");
      expect(model.timeZoneLabel).toBe("UTC");
   });

   // 🔁 Regression-sensitive: interval=0 violates positiveNonZeroIntegerInRange (value ≤ 0),
   // making form.valid=false.  A broken validator or missing && would let an invalid schedule
   // through to the server.
   it("should emit valid=false when interval is 0 (form invalid)", async () => {
      const { comp } = await renderComponent({ condition: makeCondition({ interval: 0 }) });

      const emitted: TaskConditionChanges[] = [];
      comp.modelChanged.subscribe(e => emitted.push(e));

      comp.fireModelChanged();

      expect(emitted.length).toBeGreaterThan(0);
      expect(emitted.at(-1).valid).toBe(false);
      expect(comp.form.get("interval").invalid).toBe(true);  // interval is the cause
      expect(comp.form.get("weekdayOnly").invalid).toBe(false); // not a weekday issue
   });

   // 🔁 Regression-sensitive: startTimeValid=false (no start time and no time range) must
   // produce valid=false even when the form controls are all valid.  The && must not be dropped.
   it("should emit valid=false when onStartTimeChanged reports valid=false", async () => {
      const { comp } = await renderComponent();

      const emitted: TaskConditionChanges[] = [];
      comp.modelChanged.subscribe(e => emitted.push(e));

      // Trigger startTimeValid=false via onStartTimeChanged (different from current startTimeData
      // so Tool.isEquals check does not early-return)
      const event: StartTimeChange = { startTime: "", timeRange: null, startTimeSelected: true, valid: false };
      comp.onStartTimeChanged(event);

      expect(emitted.length).toBeGreaterThan(0);
      expect(emitted.at(-1).valid).toBe(false);
   });

   // 🔁 Regression-sensitive: when weekdayOnly=true, initFormState() disables the interval
   // control — disabled controls are excluded from form.valid.  Even with interval=0 the form
   // must be valid, so the emission is valid=true (as long as startTimeValid=true).
   // Breaking this: if interval were re-enabled, interval=0 would make form.valid=false and
   // block saving despite "weekdays only" being a perfectly valid daily schedule.
   it("should emit valid=true when weekdayOnly=true disables interval (interval value is irrelevant)", async () => {
      const { comp } = await renderComponent({
         condition: makeCondition({ weekdayOnly: true, interval: 0 }),
      });

      const emitted: TaskConditionChanges[] = [];
      comp.modelChanged.subscribe(e => emitted.push(e));

      comp.fireModelChanged();

      expect(comp.form.get("interval").disabled).toBe(true);  // disabled = excluded from validity
      expect(emitted.length).toBeGreaterThan(0);
      expect(emitted.at(-1).valid).toBe(true);
   });

});

// ---------------------------------------------------------------------------
// Group 2 [Risk 2] — condition setter: form patching and startTimeData initialization
// ---------------------------------------------------------------------------

describe("DailyConditionEditorComponent — condition setter: form and startTimeData initialization", () => {

   // 🔁 Regression-sensitive: the condition setter must patch the form with the incoming
   // condition values.  If patchValue is skipped, the form retains stale values and the next
   // fireModelChanged() emits an incorrect model.
   it("should patch form interval from the new condition", async () => {
      const { comp } = await renderComponent({ condition: makeCondition({ interval: 7 }) });

      expect(comp.form.get("interval").value).toBe(7);
      expect(comp.startTimeData.startTime).toBe("08:30:00"); // derived from condition.hour/minute/second
      expect(comp.form.get("timeZone").value.timeZoneId).toBe(""); // default timezone patched
      expect(comp.form.get("timeZone").value.timeZoneLabel).toBe("");
   });

   // 🔁 Regression-sensitive: when timeRangeEnabled=true and a timeRange is present on the
   // condition, startTimeSelected must be false (time-range mode).  A bug here causes the
   // timezone control to remain visible when it should be hidden, and fireModelChanged uses
   // the wrong field to compute the emitted model.
   it("should set startTimeSelected=false and timeZoneEnabled=false when a timeRange is present", async () => {
      const range = makeTimeRange();
      const { comp } = await renderComponent({
         condition: makeCondition({ timeRange: range }),
         timeRangeEnabled: true,
      });

      expect(comp.startTimeData.startTimeSelected).toBe(false);
      expect(comp.timeZoneEnabled).toBe(false);
      expect(comp.startTimeData.timeRange).toEqual(range);
      expect(comp.form.get("timeZone").value.timeZoneId).toBe(""); // timezone form still initialized
   });

});

// ---------------------------------------------------------------------------
// Group 3 [Risk 2] — initFormState: weekdayOnly disables / enables interval
// ---------------------------------------------------------------------------

describe("DailyConditionEditorComponent — initFormState: weekdayOnly controls interval enable/disable", () => {

   // 🔁 Regression-sensitive: interval must be disabled when weekdayOnly=true so that
   // form.valid is true (disabled controls are excluded).  If enable() is mistakenly called,
   // an invalid interval blocks the parent's save action.
   it("should disable the interval control when weekdayOnly is true", async () => {
      const { comp } = await renderComponent({ condition: makeCondition({ weekdayOnly: true }) });

      expect(comp.form.get("interval").disabled).toBe(true);
   });

   // 🔁 Regression-sensitive: interval must be enabled when weekdayOnly=false so the user
   // can set the repeat frequency and the validator runs correctly.
   it("should enable the interval control when weekdayOnly is false", async () => {
      const { comp } = await renderComponent({ condition: makeCondition({ weekdayOnly: false }) });

      expect(comp.form.get("interval").enabled).toBe(true);
   });

});

// ---------------------------------------------------------------------------
// Group 4 [Risk 2] — fireModelChanged: timezone change shifts condition fields
// ---------------------------------------------------------------------------

describe("DailyConditionEditorComponent — fireModelChanged: timezone change shifts condition.hour/minute/second", () => {

   // 🔁 Regression-sensitive: the REAL default for a newly-created or legacy condition is
   // timeZone="" (empty string), not "UTC".  However, applyTimeZoneOffsetDifference currently
   // crashes when oldTZ="" and newTZ is non-empty (see date-time.service.tl.spec.ts Group 6).
   //
   // TODO: once the bug is fixed (treat "" as UTC internally), change timeZone from "UTC" to ""
   // here — the expected values (hour=15, minute=30) remain identical because "" → UTC is the
   // natural fix direction, making "" and "UTC" produce the same shift arithmetic.
   it("should advance condition.hour/minute/second by 5h30m when timezone changes from UTC to Asia/Kolkata", async () => {
      const { comp } = await renderComponent({
         // Using "UTC" instead of "" to avoid the applyTimeZoneOffsetDifference empty-TZ crash.
         // After bug fix: replace "UTC" with "" — expected values unchanged.
         condition: makeCondition({ timeZone: "UTC", hour: 10, minute: 0, second: 0 }),
      });

      comp.form.get("timeZone").setValue({ timeZoneId: "Asia/Kolkata", timeZoneLabel: "IST" });
      comp.fireModelChanged();

      expect(comp.condition.hour).toBe(15);
      expect(comp.condition.minute).toBe(30);
      expect(comp.condition.second).toBe(0);
   });

   // 🔁 Regression-sensitive: reverse shift (IST→UTC) proves symmetry.
   // This test's starting point is "Asia/Kolkata" (non-empty) — does NOT trigger the bug.
   it("should shift condition.hour/minute/second back by 5h30m when timezone changes from Asia/Kolkata to UTC", async () => {
      const { comp } = await renderComponent({
         condition: makeCondition({ timeZone: "Asia/Kolkata", hour: 15, minute: 30, second: 0 }),
      });

      comp.form.get("timeZone").setValue({ timeZoneId: "UTC", timeZoneLabel: "UTC" });
      comp.fireModelChanged();

      expect(comp.condition.hour).toBe(10);
      expect(comp.condition.minute).toBe(0);
      expect(comp.condition.second).toBe(0);
   });

});
