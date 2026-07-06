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
 * StartTimeEditor - single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] - model/writeValue and option enablement branches
 *   Group 2 [Risk 2] - fireStartTimeChanged ControlValueAccessor contract
 *   Group 3 [Risk 1] - helper methods and disabled-state behavior
 */

import { UntypedFormBuilder } from "@angular/forms";

import { TimeRange } from "../../../../../shared/schedule/model/time-condition-model";
import { StartTimeData } from "./start-time-data";
import { StartTimeEditor } from "./start-time-editor.component";

function makeTimeRange(overrides: Partial<TimeRange> = {}): TimeRange {
   return {
      name: "morning",
      label: "Morning",
      ...overrides,
   } as TimeRange;
}

function makeStartTimeData(overrides: Partial<StartTimeData> = {}): StartTimeData {
   return {
      startTime: { hour: 9, minute: 15, second: 0 },
      timeRange: makeTimeRange(),
      startTimeSelected: true,
      ...overrides,
   };
}

function createComponent(overrides: Partial<StartTimeEditor> = {}) {
   const comp = new StartTimeEditor(new UntypedFormBuilder());
   comp.timeRanges = [makeTimeRange(), makeTimeRange({ name: "evening", label: "Evening" })];
   Object.assign(comp, overrides);
   return comp;
}

afterEach(() => vi.restoreAllMocks());

describe("StartTimeEditor - single pass", () => {
   describe("Group 1 - model and option state", () => {
      it("should copy a changed start time from the model setter", () => {
         const comp = createComponent();

         comp.model = makeStartTimeData();

         expect(comp.startTimeModel).toEqual({ hour: 9, minute: 15, second: 0 });
         expect(comp.form.get("startTime").value).toEqual({ hour: 9, minute: 15, second: 0 });
      });

      it("should default to TIME_RANGE when start time is disabled", () => {
         const comp = createComponent({
            startTimeEnabled: false,
            timeRangeEnabled: true,
         });

         comp.ngOnInit();

         expect(comp.form.get("selectedType").value).toBe("TIME_RANGE");
         expect(comp.form.get("startTime").disabled).toBe(true);
         expect(comp.form.get("timeRange").enabled).toBe(true);
         expect(comp.form.get("timeRange").value).toEqual(comp.timeRanges[0]);
      });

      it("should write incoming values through writeValue", () => {
         const comp = createComponent();

         comp.writeValue(makeStartTimeData({
            startTime: { hour: 13, minute: 45, second: 0 },
            startTimeSelected: false,
            timeRange: comp.timeRanges[1],
         }));

         expect(comp.form.get("selectedType").value).toBe("TIME_RANGE");
         expect(comp.form.get("timeRange").value).toEqual(comp.timeRanges[1]);
      });
   });

   describe("Group 2 - ControlValueAccessor contract", () => {
      it("should emit modelChange and call registered callbacks on fireStartTimeChanged", () => {
         const comp = createComponent();
         comp.ngOnInit();
         comp.form.get("startTime").setValue({ hour: 8, minute: 0, second: 0 });
         const onChange = vi.fn();
         const onTouched = vi.fn();
         const emitSpy = vi.spyOn(comp.modelChange, "emit");
         comp.registerOnChange(onChange);
         comp.registerOnTouched(onTouched);

         comp.fireStartTimeChanged();

         expect(emitSpy).toHaveBeenCalledWith(expect.objectContaining({
            startTime: { hour: 8, minute: 0, second: 0 },
            startTimeSelected: true,
            valid: true,
         }));
         expect(onChange).toHaveBeenCalledWith(expect.objectContaining({
            startTimeSelected: true,
            valid: true,
         }));
         expect(onTouched).toHaveBeenCalledTimes(1);
      });

      it("should emit a time-range payload when the selection mode changes", () => {
         const comp = createComponent();
         comp.ngOnInit();
         comp.model = makeStartTimeData();
         const emitSpy = vi.spyOn(comp.modelChange, "emit");
         comp.registerOnChange(vi.fn());
         comp.form.get("selectedType").setValue("TIME_RANGE");

         comp.fireStartTimeChanged();

         expect(emitSpy).toHaveBeenCalledWith(expect.objectContaining({
            startTimeSelected: false,
            timeRange: comp.timeRanges[0],
         }));
      });
   });

   describe("Group 3 - helpers", () => {
      it("should compare time ranges by name equality", () => {
         const comp = createComponent();

         expect(comp.compareTimeRange(makeTimeRange({ name: "a" }), makeTimeRange({ name: "a" }))).toBe(true);
         expect(comp.compareTimeRange(makeTimeRange({ name: "a" }), makeTimeRange({ name: "b" }))).toBe(false);
      });

      it("should report startTimeSelected as true when selectedType is START_TIME", () => {
         const comp = createComponent();
         comp.form.get("selectedType").setValue("START_TIME");

         expect(comp.startTimeSelected()).toBe(true);
      });

      it("should disable the entire form when setDisabledState(true) is called", () => {
         const comp = createComponent();

         comp.setDisabledState(true);

         expect(comp.form.disabled).toBe(true);
      });

      it("should re-enable controls according to the selected option after setDisabledState(false)", () => {
         const comp = createComponent();
         comp.form.get("selectedType").setValue("TIME_RANGE");
         comp.setDisabledState(true);

         comp.setDisabledState(false);

         expect(comp.form.enabled).toBe(true);
         expect(comp.form.get("startTime").disabled).toBe(true);
         expect(comp.form.get("timeRange").enabled).toBe(true);
      });
   });
});
