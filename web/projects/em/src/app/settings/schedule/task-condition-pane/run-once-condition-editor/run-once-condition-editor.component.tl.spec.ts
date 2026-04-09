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
 * RunOnceConditionEditorComponent — Testing Library style
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — fireModelChanged: valid = form.valid (startTime Validators.required)
 *   Group 2 [Risk 2] — condition setter: startTime form control initialized from condition.date + TZ
 *   Group 3 [Risk 2] — startTime.valueChanges deduplication: only fires fireModelChanged on real change
 *
 * KEY contracts:
 *   - fireModelChanged emits valid = form.valid (startTime Validators.required — the only gate).
 *   - condition setter: dayjs(condition.date).utcOffset(calculateTimezoneOffset(timeZone)/60000)
 *     gives the local time in the condition's TZ; dateValue hours/minutes become form.startTime.
 *   - TimeZoneService.calculateTimezoneOffset("UTC") = 0 → dayjs(0).utcOffset(0) = 1970-01-01 00:00 UTC.
 *   - form.startTime.valueChanges subscription deduplicates via Tool.isEquals(value, this.timeValue):
 *     a setValue inside fireModelChanged sets this.timeValue first, so the subscription detects
 *     no change and does NOT call fireModelChanged() again — no infinite loop.
 *   - TZ NOTE: applyDateTimeZoneOffsetDifference handles DATE+TIME combined (not just time).
 *     Unlike hourly/daily/weekly, the date component also shifts when crossing midnight.
 *     Testing requires mocking TimeZoneService.calculateTimezoneOffset.
 */

import { Component, forwardRef, NO_ERRORS_SCHEMA } from "@angular/core";
import { ControlValueAccessor, NG_VALUE_ACCESSOR, ReactiveFormsModule } from "@angular/forms";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { render } from "@testing-library/angular";

import { RunOnceConditionEditorComponent } from "./run-once-condition-editor.component";
import { DateTimeService } from "../date-time.service";
import { TimeZoneService } from "../../../../../../../shared/schedule/time-zone.service";
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
      type: TimeConditionType.AT,
      conditionType: "TimeCondition",
      label: "",
      date: 0,          // epoch — 1970-01-01 00:00:00 UTC
      timeZoneOffset: 0,
      timeZone: "UTC",
      timeZoneLabel: "UTC",
      hour: 0,
      minute: 0,
      second: 0,
      ...overrides,
   };
}

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

async function renderComponent(props: Partial<RunOnceConditionEditorComponent> = {}) {
   // calculateTimezoneOffset("UTC") = 0ms; any real TZ value is mocked for determinism.
   const timeZoneServiceMock = {
      calculateTimezoneOffset: jest.fn().mockReturnValue(0),
   };

   const result = await render(RunOnceConditionEditorComponent, {
      imports: [ReactiveFormsModule, NoopAnimationsModule],
      declarations: [TimeZoneSelectStub, TimePickerStub],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         DateTimeService,
         { provide: TimeZoneService, useValue: timeZoneServiceMock },
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

describe("RunOnceConditionEditorComponent — fireModelChanged: valid = form.valid", () => {

   // 🔁 Regression-sensitive: a non-empty startTime must satisfy Validators.required → valid=true.
   // This is the only validity gate for run-once — if it is missing, a task with no execution
   // time is silently saved and the server uses a null date.
   it("should emit valid=true when startTime is non-empty", async () => {
      const { comp } = await renderComponent();

      const emitted: TaskConditionChanges[] = [];
      comp.modelChanged.subscribe(e => emitted.push(e));

      // Ensure startTime has a value before firing
      comp.form.get("startTime").setValue("09:00:00");
      comp.fireModelChanged();

      expect(emitted.length).toBeGreaterThan(0);
      expect(emitted.at(-1).valid).toBe(true);
   });

   // 🔁 Regression-sensitive: when startTime is cleared (null or ""), Validators.required fails
   // → valid=false.  The parent must receive this to block the save button.
   it("should emit valid=false when startTime is empty (required fails)", async () => {
      const { comp } = await renderComponent();

      const emitted: TaskConditionChanges[] = [];
      comp.modelChanged.subscribe(e => emitted.push(e));

      comp.form.get("startTime").setValue(null);
      comp.form.updateValueAndValidity();
      comp.fireModelChanged();

      expect(emitted.length).toBeGreaterThan(0);
      expect(emitted.at(-1).valid).toBe(false);
      expect(comp.form.get("startTime").invalid).toBe(true);
   });

   // 🔁 Regression-sensitive: fireModelChanged must include the condition model in the emission
   // with condition.timeZone updated from the form.  A plain {valid, model: null} emission would
   // cause the parent to save without any date/time data.
   it("should include the condition model in the emitted event", async () => {
      const { comp } = await renderComponent({
         condition: makeCondition({ timeZone: "UTC" }),
      });

      const emitted: TaskConditionChanges[] = [];
      comp.modelChanged.subscribe(e => emitted.push(e));

      comp.form.get("startTime").setValue("10:00:00");
      comp.fireModelChanged();

      expect(emitted.at(-1).model).toBeDefined();
      expect((emitted.at(-1).model as TimeConditionModel).conditionType).toBe("TimeCondition");
   });

});

// ---------------------------------------------------------------------------
// Group 2 [Risk 2] — condition setter: startTime form control from condition.date + TZ
// ---------------------------------------------------------------------------

describe("RunOnceConditionEditorComponent — condition setter: startTime initialized from condition.date and TZ", () => {

   // 🔁 Regression-sensitive: the condition setter computes a local time from condition.date using
   // dayjs.utcOffset(calculateTimezoneOffset(timeZone)/60000).  With calculateTimezoneOffset("UTC")=0,
   // date=0 (epoch) in UTC → 1970-01-01 00:00:00 → startTime="00:00:00".
   // If the utcOffset calculation is wrong, the displayed time is shifted by the server timezone,
   // causing the user to unknowingly edit a different time than what was saved.
   it("should set form.startTime to 00:00:00 when condition.date=0 and timeZone=UTC", async () => {
      const { comp } = await renderComponent({
         condition: makeCondition({ date: 0, timeZone: "UTC" }),
      });

      expect(comp.form.get("startTime").value).toBe("00:00:00");
   });

   // 🔁 Regression-sensitive: the condition setter must also patch the timeZone form control
   // so that fireModelChanged reads the correct oldTZ and can apply TZ shift arithmetic.
   it("should set timeZone form control to condition.timeZone on initialization", async () => {
      const { comp } = await renderComponent({
         condition: makeCondition({ timeZone: "UTC", timeZoneLabel: "UTC" }),
      });

      const tzValue = comp.form.get("timeZone").value;
      expect(tzValue?.timeZoneId).toBe("UTC");
   });

});

// ---------------------------------------------------------------------------
// Group 3 [Risk 2] — startTime.valueChanges deduplication
// ---------------------------------------------------------------------------

describe("RunOnceConditionEditorComponent — startTime.valueChanges: deduplication prevents re-entrant fireModelChanged", () => {

   // 🔁 Regression-sensitive: fireModelChanged calls form.startTime.setValue(newTimeValue)
   // which triggers valueChanges.  The subscription checks Tool.isEquals(value, this.timeValue)
   // to detect whether the value truly changed.  Because fireModelChanged sets this.timeValue
   // BEFORE calling setValue, the subscription sees no change and returns early — preventing
   // an infinite loop of modelChanged emissions.
   it("should emit modelChanged exactly once (not recursively) when fireModelChanged is called", async () => {
      const { comp } = await renderComponent();

      const emitted: TaskConditionChanges[] = [];
      comp.modelChanged.subscribe(e => emitted.push(e));

      // Prepare a valid startTime WITHOUT triggering the valueChanges subscription.
      // The purpose of this test is to ensure fireModelChanged() does not recursively re-enter
      // itself via its own startTime.setValue(...) call.
      comp.form.get("startTime").setValue("08:00:00", { emitEvent: false });
      comp.fireModelChanged();

      // Allow any micro-task queue to flush
      await new Promise(resolve => setTimeout(resolve, 0));

      // Should be exactly 1 emission from the direct fireModelChanged call,
      // not 2+ from recursive valueChanges → fireModelChanged chains.
      expect(emitted.length).toBe(1);
   });

   // 🔁 Regression-sensitive: a genuine change via form control setValue (not from within
   // fireModelChanged) must trigger fireModelChanged once through the valueChanges subscription.
   it("should call fireModelChanged via valueChanges when startTime changes to a genuinely different value", async () => {
      const { comp } = await renderComponent();

      // Set an initial timeValue that differs from what we'll set next
      comp.timeValue = "07:00:00";

      const emitted: TaskConditionChanges[] = [];
      comp.modelChanged.subscribe(e => emitted.push(e));

      // Set a different value — subscription detects change and calls fireModelChanged
      comp.form.get("startTime").setValue("09:00:00");
      await new Promise(resolve => setTimeout(resolve, 0));

      expect(emitted.length).toBeGreaterThan(0);
   });

});
