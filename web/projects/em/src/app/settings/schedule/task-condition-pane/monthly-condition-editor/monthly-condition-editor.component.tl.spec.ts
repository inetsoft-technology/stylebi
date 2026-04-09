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
 * MonthlyConditionEditorComponent — Testing Library style
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — fireModelChanged: valid = form.valid && startTimeValid
 *                       (months required + startTimeValid gate)
 *   Group 2 [Risk 3] — onTypeChanged: monthlyDaySelected toggles dayOfMonth / weekOfMonth+dayOfWeek
 *   Group 3 [Risk 2] — cross-field validator: dayOfMonthRequired / weekOfMonthRequired
 *   Group 4 [Risk 2] — fireModelChanged: TZ change shifts condition.hour/minute/second
 *
 * KEY contracts:
 *   - fireModelChanged emits valid = form.valid && startTimeValid (same gate as Daily/Weekly).
 *   - months control has Validators.required — empty array [] → form invalid.
 *   - onTypeChanged is called by the condition setter after patching form controls.
 *   - When monthlyDaySelected=true: dayOfMonth enabled; weekOfMonth and dayOfWeek disabled.
 *   - When monthlyDaySelected=false: dayOfMonth disabled; weekOfMonth and dayOfWeek enabled.
 *   - Disabled controls are excluded from form.valid, so only the active path's controls
 *     are validated by the cross-field validator.
 *   - Cross-field validator: monthlyDaySelected=true → checks dayOfMonth (Validators.required);
 *     false → checks weekOfMonth AND dayOfWeek (both required separately).
 *   - TZ shift: same updateStartTimeDataTimeZone → setStartTime pattern as Daily/Weekly.
 *   - Asia/Kolkata (IST = UTC+5:30, no DST) gives a deterministic +5h30m shift year-round.
 */

import { Component, forwardRef, NO_ERRORS_SCHEMA } from "@angular/core";
import { ControlValueAccessor, NG_VALUE_ACCESSOR, ReactiveFormsModule } from "@angular/forms";
import { ErrorStateMatcher } from "@angular/material/core";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { render } from "@testing-library/angular";

import { MonthlyConditionEditorComponent } from "./monthly-condition-editor.component";
import { DateTimeService } from "../date-time.service";
import { TimeConditionModel, TimeConditionType } from "../../../../../../../shared/schedule/model/time-condition-model";
import { TaskConditionChanges } from "../task-condition-pane.component";
import { StartTimeChange } from "../start-time-editor/start-time-editor.component";

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

// mat-select is used for months (and possibly weekOfMonth/dayOfWeek) — needs CVA stub.
/* eslint-disable @angular-eslint/component-selector */
@Component({
   selector: "mat-select",
   template: "",
   providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => MatSelectStub), multi: true }]
})
class MatSelectStub implements ControlValueAccessor {
   writeValue() {} registerOnChange() {} registerOnTouched() {}
}

@Component({
   selector: "mat-radio-group",
   template: "",
   providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => MatRadioGroupStub), multi: true }]
})
class MatRadioGroupStub implements ControlValueAccessor {
   writeValue() {} registerOnChange() {} registerOnTouched() {}
}
/* eslint-enable @angular-eslint/component-selector */

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

function makeCondition(overrides: Partial<TimeConditionModel> = {}): TimeConditionModel {
   return {
      type: TimeConditionType.EVERY_MONTH,
      conditionType: "TimeCondition",
      label: "",
      hour: 8,
      minute: 30,
      second: 0,
      date: 0,
      timeZoneOffset: 0,
      monthlyDaySelected: true,
      dayOfMonth: 1,
      weekOfMonth: 1,
      dayOfWeek: 1,
      monthsOfYear: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12],
      timeZone: "",
      timeZoneLabel: "",
      ...overrides,
   };
}

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

async function renderComponent(props: Partial<MonthlyConditionEditorComponent> = {}) {
   const result = await render(MonthlyConditionEditorComponent, {
      imports: [ReactiveFormsModule, NoopAnimationsModule],
      declarations: [TimeZoneSelectStub, MatSelectStub, MatRadioGroupStub],
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
// Group 1 [Risk 3] — fireModelChanged: valid = form.valid && startTimeValid
// ---------------------------------------------------------------------------

describe("MonthlyConditionEditorComponent — fireModelChanged: valid = form.valid && startTimeValid", () => {

   // 🔁 Regression-sensitive: all required fields present and startTimeValid=true → valid=true.
   it("should emit valid=true when months are selected and startTimeValid is true", async () => {
      const { comp } = await renderComponent({
         condition: makeCondition({ monthsOfYear: [1, 6, 12] }),
      });

      const emitted: TaskConditionChanges[] = [];
      comp.modelChanged.subscribe(e => emitted.push(e));

      comp.fireModelChanged();

      expect(emitted.length).toBeGreaterThan(0);
      expect(emitted.at(-1).valid).toBe(true);
   });

   // 🔁 Regression-sensitive: months=[] fails Validators.required (isEmptyInputValue([]) = true).
   // A missing required validator on months would allow a schedule that never runs on any month.
   it("should emit valid=false when monthsOfYear is empty (months required fails)", async () => {
      const { comp } = await renderComponent({
         condition: makeCondition({ monthsOfYear: [] }),
      });

      const emitted: TaskConditionChanges[] = [];
      comp.modelChanged.subscribe(e => emitted.push(e));

      comp.fireModelChanged();

      expect(emitted.length).toBeGreaterThan(0);
      expect(emitted.at(-1).valid).toBe(false);
      expect(comp.form.get("months").invalid).toBe(true);
   });

   // 🔁 Regression-sensitive: startTimeValid=false must produce valid=false even when form is
   // fully valid — the && gate must hold.  A drop of startTimeValid from the expression would
   // let a monthly task with no execution time reach the server.
   it("should emit valid=false when onStartTimeChanged reports valid=false", async () => {
      const { comp } = await renderComponent();

      const emitted: TaskConditionChanges[] = [];
      comp.modelChanged.subscribe(e => emitted.push(e));

      const event: StartTimeChange = { startTime: "", timeRange: null, startTimeSelected: true, valid: false };
      comp.onStartTimeChanged(event);

      expect(emitted.length).toBeGreaterThan(0);
      expect(emitted.at(-1).valid).toBe(false);
   });

   // 🔁 Regression-sensitive: emitted model must include the updated condition fields
   // (monthsOfYear, monthlyDaySelected, dayOfMonth) — not just a validity flag.
   it("should include monthsOfYear in the emitted model", async () => {
      const { comp } = await renderComponent({
         condition: makeCondition({ monthsOfYear: [3, 6, 9, 12] }),
      });

      const emitted: TaskConditionChanges[] = [];
      comp.modelChanged.subscribe(e => emitted.push(e));

      comp.fireModelChanged();

      expect(emitted.at(-1).model).toBeDefined();
      expect((emitted.at(-1).model as TimeConditionModel).monthsOfYear).toEqual([3, 6, 9, 12]);
   });

});

// ---------------------------------------------------------------------------
// Group 2 [Risk 3] — onTypeChanged: control enable/disable per monthlyDaySelected
// ---------------------------------------------------------------------------

describe("MonthlyConditionEditorComponent — onTypeChanged: monthlyDaySelected toggles control states", () => {

   // 🔁 Regression-sensitive: when monthlyDaySelected=true, dayOfMonth must be enabled so the
   // user can set which calendar day to run on.  weekOfMonth and dayOfWeek must be disabled so
   // their (potentially null/0) values do not invalidate the form or pollute the emitted model.
   it("should enable dayOfMonth and disable weekOfMonth/dayOfWeek when monthlyDaySelected=true", async () => {
      const { comp } = await renderComponent({
         condition: makeCondition({ monthlyDaySelected: true }),
      });

      expect(comp.form.get("dayOfMonth").enabled).toBe(true);
      expect(comp.form.get("weekOfMonth").disabled).toBe(true);
      expect(comp.form.get("dayOfWeek").disabled).toBe(true);
   });

   // 🔁 Regression-sensitive: when monthlyDaySelected=false (week-of-month mode), dayOfMonth
   // must be disabled and weekOfMonth+dayOfWeek must be enabled.  If weekOfMonth stays disabled
   // after switching modes, the user cannot select the run week, and the form silently emits
   // the stale value from the previous mode.
   it("should disable dayOfMonth and enable weekOfMonth/dayOfWeek when monthlyDaySelected=false", async () => {
      const { comp } = await renderComponent({
         condition: makeCondition({ monthlyDaySelected: false }),
      });

      expect(comp.form.get("dayOfMonth").disabled).toBe(true);
      expect(comp.form.get("weekOfMonth").enabled).toBe(true);
      expect(comp.form.get("dayOfWeek").enabled).toBe(true);
   });

   // 🔁 Regression-sensitive: calling onTypeChanged() directly must immediately reflect the
   // current form value — the control states must update synchronously without a lifecycle cycle.
   it("should update control states synchronously when onTypeChanged is called directly", async () => {
      const { comp } = await renderComponent({
         condition: makeCondition({ monthlyDaySelected: true }),
      });

      comp.form.get("monthlyDaySelected").setValue(false);
      comp.onTypeChanged();

      expect(comp.form.get("dayOfMonth").disabled).toBe(true);
      expect(comp.form.get("weekOfMonth").enabled).toBe(true);
   });

});

// ---------------------------------------------------------------------------
// Group 3 [Risk 2] — cross-field validator: dayOfMonthRequired / weekOfMonthRequired
// ---------------------------------------------------------------------------

describe("MonthlyConditionEditorComponent — cross-field validator: dayOfMonthRequired and weekOfMonthRequired", () => {

   // 🔁 Regression-sensitive: when monthlyDaySelected=true, the validator checks dayOfMonth
   // with Validators.required.  A null dayOfMonth must produce {dayOfMonthRequired: true}.
   // Without this check, a monthly task with no target day reaches the server and silently
   // uses the server's null handling (often day=0 → invalid trigger).
   it("should add dayOfMonthRequired form error when monthlyDaySelected=true and dayOfMonth is null", async () => {
      const { comp } = await renderComponent({
         condition: makeCondition({ monthlyDaySelected: true, dayOfMonth: 1 }),
      });

      comp.form.get("dayOfMonth").setValue(null);

      expect(comp.form.errors?.dayOfMonthRequired).toBe(true);
   });

   // 🔁 Regression-sensitive: when monthlyDaySelected=false, weekOfMonth and dayOfWeek are
   // both required.  A null weekOfMonth must add {weekOfMonthRequired: true}.
   it("should add weekOfMonthRequired form error when monthlyDaySelected=false and weekOfMonth is null", async () => {
      const { comp } = await renderComponent({
         condition: makeCondition({ monthlyDaySelected: false, weekOfMonth: 1, dayOfWeek: 1 }),
      });

      comp.form.get("weekOfMonth").setValue(null);

      expect(comp.form.errors?.weekOfMonthRequired).toBe(true);
   });

});

// ---------------------------------------------------------------------------
// Group 4 [Risk 2] — fireModelChanged: TZ change shifts condition.hour/minute/second
// ---------------------------------------------------------------------------

describe("MonthlyConditionEditorComponent — fireModelChanged: timezone change shifts condition.hour/minute/second", () => {

   // 🔁 Regression-sensitive: the monthly editor uses the same updateStartTimeDataTimeZone →
   // setStartTime chain as Daily and Weekly.
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

   // 🔁 Regression-sensitive: backward shift (IST→UTC, -5h30m) confirms symmetry.
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
