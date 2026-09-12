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
 * StartTimeEditorComponent — Testing Library style
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — enableOptions: startTime/timeRange mutual disable based on selectedType
 *                       and timeRangeEnabled
 *   Group 2 [Risk 3] — optionRequired cross-validator: startTime required only in START_TIME mode,
 *                       timeRange required only in TIME_RANGE mode
 *   Group 3 [Risk 2] — data setter: form patching for non-null and null input
 *
 * KEY contracts:
 *   - When selectedType="START_TIME": startTime is enabled and required; timeRange is disabled.
 *   - When selectedType="TIME_RANGE" AND timeRangeEnabled=true: timeRange is enabled and required;
 *     startTime is disabled.
 *   - When selectedType="TIME_RANGE" AND timeRangeEnabled=false: startTime remains enabled
 *     (enableOptions falls through to the else branch) — the TIME_RANGE option is only activated
 *     when timeRangeEnabled is also true.
 *   - optionRequired(type) returns Validators.required only when selectedType === type;
 *     it is null otherwise.  This means validation of the inactive control is always skipped,
 *     regardless of its current value.
 *   - data setter (null): defaults startTime to "01:30:00", timeRange to null,
 *     selectedType to startTimeEnabled ? "START_TIME" : "TIME_RANGE".
 *   - data setter (non-null): patches form.startTime, form.timeRange, form.selectedType from value.
 *   - fireStartTimeChanged calls enableOptions() THEN emits with correct {valid, startTimeSelected}.
 *   - No timezone logic — StartTimeEditorComponent does not perform TZ conversions.
 */

import { Component, forwardRef, NO_ERRORS_SCHEMA } from "@angular/core";
import { ControlValueAccessor, NG_VALUE_ACCESSOR, ReactiveFormsModule } from "@angular/forms";
import { MatRadioModule } from "@angular/material/radio";
import { MatSelectModule } from "@angular/material/select";
import { render } from "@testing-library/angular";

import { StartTimeEditorComponent, StartTimeData } from "./start-time-editor.component";
import { TimeRange } from "../../../../../../../shared/schedule/model/time-condition-model";

// ---------------------------------------------------------------------------
// Stubs
// ---------------------------------------------------------------------------

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

function makeTimeRange(name = "Business Hours"): TimeRange {
   return { name, startTime: "09:00:00", endTime: "17:00:00", defaultRange: false };
}

function makeStartTimeData(overrides: Partial<StartTimeData> = {}): StartTimeData {
   return {
      startTime: "09:00:00",
      timeRange: null,
      startTimeSelected: true,
      ...overrides,
   };
}

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

async function renderComponent(props: Partial<StartTimeEditorComponent> = {}) {
   const result = await render(StartTimeEditorComponent, {
      imports: [ReactiveFormsModule, MatRadioModule, MatSelectModule],
      declarations: [TimePickerStub],
      schemas: [NO_ERRORS_SCHEMA],
      componentProperties: {
         startTimeEnabled: true,
         timeRangeEnabled: true,
         ...props,
      },
   });

   await result.fixture.whenStable();
   return { ...result, comp: result.fixture.componentInstance };
}

// ---------------------------------------------------------------------------
// Group 1 [Risk 3] — enableOptions: mutual disable based on selectedType + timeRangeEnabled
// ---------------------------------------------------------------------------

describe("StartTimeEditorComponent — enableOptions: startTime/timeRange mutual disable", () => {

   // 🔁 Regression-sensitive: the default START_TIME mode must leave startTime enabled and
   // timeRange disabled.  If this is reversed, the required validator fires on an unreachable
   // control and the user can never submit a valid start time.
   it("should enable startTime and disable timeRange when selectedType is START_TIME (default)", async () => {
      const { comp } = await renderComponent();

      expect(comp.form.get("startTime").enabled).toBe(true);
      expect(comp.form.get("timeRange").disabled).toBe(true);
   });

   // 🔁 Regression-sensitive: switching to TIME_RANGE mode while timeRangeEnabled=true must
   // disable startTime and enable timeRange.  If startTime stays enabled its required validator
   // would block form submission even in time-range mode — the parent would never receive valid=true.
   it("should disable startTime and enable timeRange when selectedType=TIME_RANGE and timeRangeEnabled=true", async () => {
      const { comp } = await renderComponent({
         timeRangeEnabled: true,
         timeRanges: [makeTimeRange()],
         data: makeStartTimeData({ startTimeSelected: false, timeRange: makeTimeRange() }),
      });

      // Manually set selectedType to TIME_RANGE and trigger enableOptions
      comp.form.get("selectedType").setValue("TIME_RANGE");
      comp.fireStartTimeChanged();

      expect(comp.form.get("startTime").disabled).toBe(true);
      expect(comp.form.get("timeRange").enabled).toBe(true);
   });

   // 🔁 Regression-sensitive: when selectedType=TIME_RANGE but timeRangeEnabled=false,
   // enableOptions falls through to the else branch → startTime stays ENABLED.
   // This preserves the start-time value in edge-case transitions where the parent toggles
   // timeRangeEnabled off after the user had switched to TIME_RANGE mode.
   it("should keep startTime enabled when selectedType=TIME_RANGE but timeRangeEnabled=false", async () => {
      const { comp } = await renderComponent({ timeRangeEnabled: false });

      comp.form.get("selectedType").setValue("TIME_RANGE");
      comp.fireStartTimeChanged();

      expect(comp.form.get("startTime").enabled).toBe(true);
   });

   // 🔁 Regression-sensitive: fireStartTimeChanged must call enableOptions() before emitting
   // so the emitted valid flag reflects the post-enable/disable state.
   it("should emit startTimeChanged with correct startTimeSelected after fireStartTimeChanged", async () => {
      const events: any[] = [];
      const { comp } = await renderComponent();

      comp.startTimeChanged.subscribe(e => events.push(e));
      comp.form.get("startTime").setValue("10:00:00");
      comp.fireStartTimeChanged();

      expect(events.length).toBeGreaterThan(0);
      expect(events.at(-1).startTimeSelected).toBe(true);
   });

});

// ---------------------------------------------------------------------------
// Group 2 [Risk 3] — optionRequired: cross-validator fires only for active mode
// ---------------------------------------------------------------------------

describe("StartTimeEditorComponent — optionRequired cross-validator: mode-specific validation", () => {

   // 🔁 Regression-sensitive: in START_TIME mode, a null startTime must fail the required
   // validator — the user must enter a time before saving.  Without this, a null time reaches
   // the server and triggers an unpredictable server-side error.
   it("should mark startTime invalid when value is null in START_TIME mode", async () => {
      const { comp } = await renderComponent();

      comp.form.get("startTime").setValue(null);
      comp.form.updateValueAndValidity();

      expect(comp.form.get("startTime").invalid).toBe(true);
   });

   // 🔁 Regression-sensitive: in TIME_RANGE mode, startTime is disabled so its validator
   // does not run — even a null startTime must not make the overall form invalid.
   // If optionRequired fires on disabled controls, the user in time-range mode can never save.
   it("should not require startTime when selectedType is TIME_RANGE (startTime is disabled)", async () => {
      const { comp } = await renderComponent({ timeRangeEnabled: true });

      comp.form.get("selectedType").setValue("TIME_RANGE");
      comp.fireStartTimeChanged(); // triggers enableOptions → startTime.disable()

      // startTime is disabled — its validator is suppressed
      expect(comp.form.get("startTime").disabled).toBe(true);
      // NOTE: Angular marks disabled controls with status "DISABLED" (not "VALID"),
      // so AbstractControl.valid is false even though validators are not applied.
      expect(comp.form.get("startTime").status).toBe("DISABLED");
   });

});

// ---------------------------------------------------------------------------
// Group 3 [Risk 2] — data setter: form patching
// ---------------------------------------------------------------------------

describe("StartTimeEditorComponent — data setter: form patching for non-null and null input", () => {

   // 🔁 Regression-sensitive: when data is set to a non-null StartTimeData, all three form
   // controls (startTime, timeRange, selectedType) must be patched.  If any is skipped, the
   // form renders an incorrect state and fireStartTimeChanged emits wrong data.
   it("should patch startTime, timeRange, and selectedType from a non-null StartTimeData", async () => {
      const range = makeTimeRange();
      const { comp } = await renderComponent();

      comp.data = makeStartTimeData({ startTime: "14:00:00", timeRange: range, startTimeSelected: false });

      expect(comp.form.get("startTime").value).toBe("14:00:00");
      expect(comp.form.get("timeRange").value).toEqual(range);
      expect(comp.form.get("selectedType").value).toBe("TIME_RANGE");
   });

   // 🔁 Regression-sensitive: when data setter is called with null, defaults must be applied
   // ("01:30:00" and startTimeEnabled → "START_TIME").  Without defaults, the form stays in
   // an indeterminate state that crashes the template when it tries to bind a null startTime.
   it("should apply defaults when data is set to null with startTimeEnabled=true", async () => {
      const { comp } = await renderComponent({ startTimeEnabled: true });

      comp.data = null;

      expect(comp.form.get("startTime").value).toBe("01:30:00");
      expect(comp.form.get("timeRange").value).toBeNull();
      expect(comp.form.get("selectedType").value).toBe("START_TIME");
   });

});
