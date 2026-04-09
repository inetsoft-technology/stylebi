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
 * WeeklyConditionEditorComponent — Testing Library style
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — fireModelChanged: emitted valid = form.valid && startTimeValid
 *   Group 2 [Risk 2] — condition setter: daysOfWeek / interval form initialization
 *   Group 3 [Risk 2] — fireModelChanged: timezone change shifts condition.hour/minute/second
 *
 * KEY contracts:
 *   - fireModelChanged emits valid = form.valid && startTimeValid (both required).
 *   - weekdays control has Validators.required — an empty array [] is treated as empty
 *     by Angular's isEmptyInputValue, making form.valid=false.
 *   - Unlike DailyConditionEditorComponent, onStartTimeChanged does NOT deduplicate via
 *     Tool.isEquals — every call unconditionally updates startTimeData and fires modelChanged.
 *   - The condition setter shallow-copies the value (Object.assign) and early-returns
 *     (Tool.isEquals) when old === new, preventing unnecessary form re-patching.
 *   - startTimeSelected = !timeRangeEnabled || !condition.timeRange.
 *   - fireModelChanged captures oldTZ = condition.timeZone BEFORE reading the new TZ from the
 *     form, then calls updateStartTimeDataTimeZone(startTimeData, oldTZ, newTZ) → setStartTime,
 *     propagating the timezone delta into condition.hour/minute/second.
 *   - Asia/Kolkata (IST = UTC+5:30, no DST) is used for TZ shift tests because its offset is
 *     constant year-round, making the arithmetic deterministic in any test environment.
 */

import { Component, forwardRef, NO_ERRORS_SCHEMA } from "@angular/core";
import { ControlValueAccessor, NG_VALUE_ACCESSOR, ReactiveFormsModule } from "@angular/forms";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { render } from "@testing-library/angular";

import { WeeklyConditionEditorComponent } from "./weekly-condition-editor.component";
import { DateTimeService } from "../date-time.service";
import { TimeConditionModel, TimeConditionType } from "../../../../../../../shared/schedule/model/time-condition-model";
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

// mat-select uses formControlName="weekdays" — needs a CVA stub.
@Component({
   selector: "mat-select",
   template: "",
   providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => MatSelectStub), multi: true }]
})
class MatSelectStub implements ControlValueAccessor {
   writeValue() {} registerOnChange() {} registerOnTouched() {}
}

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

function makeCondition(overrides: Partial<TimeConditionModel> = {}): TimeConditionModel {
   return {
      type: TimeConditionType.EVERY_WEEK,
      conditionType: "TimeCondition",
      label: "",
      hour: 8,
      minute: 30,
      second: 0,
      date: 0,
      timeZoneOffset: 0,
      interval: 1,
      daysOfWeek: [2, 4, 6], // Mon, Wed, Fri
      timeZone: "",
      timeZoneLabel: "",
      ...overrides,
   };
}

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

async function renderComponent(props: Partial<WeeklyConditionEditorComponent> = {}) {
   const result = await render(WeeklyConditionEditorComponent, {
      imports: [ReactiveFormsModule, NoopAnimationsModule],
      declarations: [TimeZoneSelectStub, MatSelectStub],
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

describe("WeeklyConditionEditorComponent — fireModelChanged: valid = form.valid && startTimeValid", () => {

   // 🔁 Regression-sensitive: a non-empty weekdays selection, a valid interval, and a
   // non-empty start time are the three conditions for a valid weekly schedule.
   it("should emit valid=true when weekdays are selected, interval is valid, and startTimeValid is true", async () => {
      const { comp } = await renderComponent({
         condition: makeCondition({ daysOfWeek: [2, 4, 6], interval: 1 }),
      });

      const emitted: TaskConditionChanges[] = [];
      comp.modelChanged.subscribe(e => emitted.push(e));

      comp.fireModelChanged();

      expect(emitted.length).toBeGreaterThan(0);
      expect(emitted.at(-1).valid).toBe(true);
   });

   // 🔁 Regression-sensitive: an empty weekdays array fails Validators.required — Angular's
   // isEmptyInputValue treats [] as empty.  If this check were removed, a weekly task with
   // no days selected would be silently saved and never execute.
   it("should emit valid=false when daysOfWeek is empty (required fails)", async () => {
      const { comp } = await renderComponent({
         condition: makeCondition({ daysOfWeek: [] }),
      });

      const emitted: TaskConditionChanges[] = [];
      comp.modelChanged.subscribe(e => emitted.push(e));

      comp.fireModelChanged();

      expect(emitted.length).toBeGreaterThan(0);
      expect(emitted.at(-1).valid).toBe(false);
      expect(comp.form.get("weekdays").invalid).toBe(true);  // weekdays is the cause
      expect(comp.form.get("interval").invalid).toBe(false); // not an interval issue
   });

   // 🔁 Regression-sensitive: interval=0 violates positiveNonZeroIntegerInRange (value ≤ 0).
   // A broken validator guard would allow a 0-week interval to reach the server scheduler.
   it("should emit valid=false when interval is 0 (form invalid)", async () => {
      const { comp } = await renderComponent({
         condition: makeCondition({ interval: 0 }),
      });

      const emitted: TaskConditionChanges[] = [];
      comp.modelChanged.subscribe(e => emitted.push(e));

      comp.fireModelChanged();

      expect(emitted.length).toBeGreaterThan(0);
      expect(emitted.at(-1).valid).toBe(false);
      expect(comp.form.get("interval").invalid).toBe(true);
   });

   // 🔁 Regression-sensitive: unlike DailyConditionEditorComponent, onStartTimeChanged in
   // WeeklyConditionEditorComponent does NOT deduplicate — every call sets startTimeValid and
   // fires fireModelChanged.  The && contract must hold: form.valid=true but startTimeValid=false
   // → emitted valid=false.
   it("should emit valid=false when onStartTimeChanged reports valid=false", async () => {
      const { comp } = await renderComponent();

      const emitted: TaskConditionChanges[] = [];
      comp.modelChanged.subscribe(e => emitted.push(e));

      const event: StartTimeChange = { startTime: "", timeRange: null, startTimeSelected: true, valid: false };
      comp.onStartTimeChanged(event);

      expect(emitted.length).toBeGreaterThan(0);
      expect(emitted.at(-1).valid).toBe(false);
   });

});

// ---------------------------------------------------------------------------
// Group 2 [Risk 2] — condition setter: daysOfWeek / interval form initialization
// ---------------------------------------------------------------------------

describe("WeeklyConditionEditorComponent — condition setter: form initialization", () => {

   // 🔁 Regression-sensitive: the condition setter must write daysOfWeek to the form's
   // weekdays control so Validators.required can run on the correct value.  If the patch
   // is skipped, the control retains its initial empty-array value → form always invalid.
   it("should initialize the weekdays form control from condition.daysOfWeek", async () => {
      const { comp } = await renderComponent({
         condition: makeCondition({ daysOfWeek: [2, 4, 6] }),
      });

      expect(comp.form.get("weekdays").value).toEqual([2, 4, 6]);
      expect(comp.form.get("weekdays").valid).toBe(true);
   });

   // 🔁 Regression-sensitive: if condition.daysOfWeek is empty, the weekdays control must
   // be set to [] so the required validator correctly marks it invalid — the parent's save
   // guard depends on this to block submission of a schedule with no run days.
   it("should mark weekdays control invalid when condition.daysOfWeek is empty", async () => {
      const { comp } = await renderComponent({
         condition: makeCondition({ daysOfWeek: [] }),
      });

      expect(comp.form.get("weekdays").value).toEqual([]);
      expect(comp.form.get("weekdays").invalid).toBe(true);
   });

});

// ---------------------------------------------------------------------------
// Group 3 [Risk 2] — fireModelChanged: timezone change shifts condition fields
// ---------------------------------------------------------------------------

describe("WeeklyConditionEditorComponent — fireModelChanged: timezone change shifts condition.hour/minute/second", () => {

   // 🔁 Regression-sensitive: same contract as DailyConditionEditorComponent.
   // TODO: after the applyTimeZoneOffsetDifference empty-TZ bug is fixed, change timeZone
   // from "UTC" to "" — expected values remain the same if "" is treated as UTC internally.
   it("should advance condition.hour/minute/second by 5h30m when timezone changes from UTC to Asia/Kolkata", async () => {
      const { comp } = await renderComponent({
         // Using "UTC" instead of "" to avoid the empty-TZ crash bug (see date-time.service Group 6).
         condition: makeCondition({ timeZone: "UTC", hour: 10, minute: 0, second: 0 }),
      });

      comp.form.get("timeZone").setValue({ timeZoneId: "Asia/Kolkata", timeZoneLabel: "IST" });
      comp.fireModelChanged();

      expect(comp.condition.hour).toBe(15);
      expect(comp.condition.minute).toBe(30);
      expect(comp.condition.second).toBe(0);
   });

   // 🔁 Regression-sensitive: backward shift (IST→UTC, -5h30m) confirms the conversion is
   // symmetric.  Combined with the forward test, both directions of the shift are guarded.
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
