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
 * TaskConditionPane — Pass 1: Interaction
 * Source: task-condition-pane.component.ts
 *
 * Mocking strategy:
 *   - ScheduleTaskNamesService mocked — constructor fires HTTP (NG0205 risk).
 *   - TimeZoneService real — pure Intl-based functions, no HTTP.
 *   - NgbModal mocked — needed by deleteCondition / changeConditionType guard.
 *   - No MSW server — component makes no direct HTTP calls.
 *
 * Coverage groups (risk-first):
 *   Group 1  [Risk 3] — condition getter: conditionIndex bookkeeping, empty fallback
 *   Group 2  [Risk 3] — changeConditionType: isSaving guard; snapshot switching
 *   Group 3  [Risk 3] — initForm: correct FormGroup shape for all 6 condition types
 *   Group 4  [Risk 3] — save: async saveTask → emits updateTaskName, marks pristine, listView
 *   Group 5  [Risk 2] — model setter: serverTimeZoneOffset, conditionIndex reset
 *   Group 6  [Risk 2] — ngOnInit: task filtering, Custom option, enabledOptions
 *   Group 7  [Risk 2] — addCondition: appends condition, resets listView
 *   Group 8  [Risk 2] — copyCondition: prefix stripping, guard (0 / multiple selected)
 *   Group 9  [Risk 2] — changeMonthRadioOption: day-mode vs week-mode mutation
 *   Group 10 [Risk 2] — setLocalTimeZone: null guard, fallback, label assignment
 *   Group 11 [Risk 2] — changeServerTimeZone: no-op on same value, localStorage
 *   Group 12 [Risk 2] — updateWeekdayOnly: interval validators toggled
 *   Group 13 [Risk 2] — formStartTimeData / formStartTime / formEndTime setters
 *   Group 14 [baseline] — startTime setter: taskDefaultTime gate
 *   Group 15 [baseline] — currentTimeZoneOffset: server vs local dispatch
 *   Group 16 [baseline] — isPresent / updateList / selectAll utilities
 *   Group 17 [baseline] — conditionNames / showMeridian / loadingTasks getters
 *   Group 18 [baseline] — updateDaysOfWeekStatus / updateMonthsOfYearStatus
 *   Group 19 [baseline] — timeConditions getter Set membership
 *   Group 20 [baseline] — convertTime: UTC-offset arithmetic
 *   Group 21 [baseline] — ngOnChanges: listView false flip → addCondition
 *
 * Out of scope (Pass 2 / Pass 3):
 *   deleteCondition — destructive + async modal
 *   editCondition — async state reset
 *   formDate setter — dateChange arithmetic
 *   Private helpers — covered transitively via public callers
 */

import { SimpleChange } from "@angular/core";
import { waitFor } from "@testing-library/angular";
import { of } from "rxjs";
import { vi } from "vitest";
import { TimeConditionModel, TimeConditionType, TimeRange } from "../../../../../../../shared/schedule/model/time-condition-model";
import { TimeZoneModel } from "../../../../../../../shared/schedule/model/time-zone-model";
import {
   makeDailyCondition,
   makeWeeklyCondition,
   makeMonthlyCondition,
   makeModel,
   modalMock,
   taskNamesMock,
   resetMocks,
   renderTaskConditionPane,
} from "./task-condition-pane.test-helpers";

beforeEach(() => resetMocks());
afterEach(() => vi.restoreAllMocks());

// ===========================================================================
// Group 1 — condition getter: conditionIndex bookkeeping
// ===========================================================================
describe("Group 1 — condition getter: conditionIndex and empty fallback", () => {
   it("returns condition at conditionIndex=0 after init with two conditions", async () => {
      const model = makeModel({ conditions: [makeDailyCondition({ label: "First" }), makeWeeklyCondition({ label: "Second" })] });
      const { comp } = await renderTaskConditionPane({ model });
      expect(comp.condition.label).toBe("First");
   });

   it("returns last condition when conditionIndex is -1", async () => {
      const model = makeModel({ conditions: [makeDailyCondition({ label: "First" }), makeWeeklyCondition({ label: "Last" })] });
      const { comp } = await renderTaskConditionPane({ model });
      (comp as any).conditionIndex = -1;
      expect(comp.condition.label).toBe("Last");
   });

   it("pushes a default CompletionCondition when index is out of range", async () => {
      const { comp } = await renderTaskConditionPane();
      (comp as any).conditionIndex = 99;
      const pushed = comp.condition;
      expect(pushed.conditionType).toBe("CompletionCondition");
      expect(comp.model.conditions).toContain(pushed);
   });
});

// ===========================================================================
// Group 2 — changeConditionType: guard and snapshot switching
// ===========================================================================
describe("Group 2 — changeConditionType: isSaving guard and snapshot switching", () => {
   it("emits showMessage and leaves selectedOption unchanged when isSaving=true", async () => {
      const { comp } = await renderTaskConditionPane();
      comp.isSaving = true;
      const messages: string[] = [];
      comp.showMessage.subscribe(m => messages.push(m));

      comp.changeConditionType({ value: TimeConditionType.EVERY_WEEK });

      expect(comp.selectedOption).toBe(TimeConditionType.EVERY_DAY);
      expect(messages).toEqual(["_#(js:portal.schedule.isSaving.warning)"]);
   });

   it("switches to EVERY_WEEK and restores weekly snapshot", async () => {
      const { comp } = await renderTaskConditionPane();
      comp.changeConditionType({ value: TimeConditionType.EVERY_WEEK });
      expect(comp.selectedOption).toBe(TimeConditionType.EVERY_WEEK);
      expect((comp.condition as TimeConditionModel).type).toBe(TimeConditionType.EVERY_WEEK);
   });

   it("switches to CompletionCondition type", async () => {
      const { comp } = await renderTaskConditionPane();
      comp.changeConditionType({ value: "CompletionCondition" });
      expect(comp.selectedOption).toBe("CompletionCondition");
      expect(comp.condition.conditionType).toBe("CompletionCondition");
   });

   it("switches to EVERY_HOUR type", async () => {
      const { comp } = await renderTaskConditionPane();
      comp.changeConditionType({ value: TimeConditionType.EVERY_HOUR });
      expect(comp.selectedOption).toBe(TimeConditionType.EVERY_HOUR);
      expect((comp.condition as TimeConditionModel).type).toBe(TimeConditionType.EVERY_HOUR);
   });

   it("sets condition label to New Condition after any type switch", async () => {
      const { comp } = await renderTaskConditionPane();
      comp.changeConditionType({ value: TimeConditionType.EVERY_WEEK });
      expect(comp.condition.label).toBe("_#(js:New Condition)");
   });

   it("no-ops (keeps original label) when same condition type is reselected", async () => {
      const { comp } = await renderTaskConditionPane({ model: makeModel({ conditions: [makeDailyCondition({ label: "Keep Me" })] }) });
      comp.changeConditionType({ value: TimeConditionType.EVERY_DAY });
      expect(comp.condition.label).toBe("Keep Me");
   });
});

// ===========================================================================
// Group 3 — initForm: FormGroup shape for each condition type
// ===========================================================================
describe("Group 3 — initForm: FormGroup shape for each condition type", () => {
   it("daily form contains startTime, timeZone, interval controls", async () => {
      const { comp } = await renderTaskConditionPane();
      expect(comp.form.contains("startTime")).toBe(true);
      expect(comp.form.contains("timeZone")).toBe(true);
      expect(comp.form.contains("interval")).toBe(true);
   });

   it("weekly form contains weekdays FormArray with 7 controls", async () => {
      const { comp } = await renderTaskConditionPane({ model: makeModel({ conditions: [makeWeeklyCondition()] }) });
      const weekdays = comp.form.get("weekdays") as any;
      expect(weekdays).toBeTruthy();
      expect(weekdays.controls.length).toBe(7);
   });

   it("monthly form has months FormArray plus dayOfMonth/weekOfMonth/dayOfWeek controls", async () => {
      const { comp } = await renderTaskConditionPane({ model: makeModel({ conditions: [makeMonthlyCondition()] }) });
      // use form.get() not form.contains(): contains() returns false for disabled controls,
      // and weekOfMonth/dayOfWeek are disabled when monthlyDaySelected=true
      expect(comp.form.get("months")).not.toBeNull();
      expect(comp.form.get("dayOfMonth")).not.toBeNull();
      expect(comp.form.get("weekOfMonth")).not.toBeNull();
      expect(comp.form.get("dayOfWeek")).not.toBeNull();
   });

   it("hourly form contains endTime, interval, weekdays controls", async () => {
      const { comp } = await renderTaskConditionPane();
      comp.changeConditionType({ value: TimeConditionType.EVERY_HOUR });
      expect(comp.form.contains("endTime")).toBe(true);
      expect(comp.form.contains("interval")).toBe(true);
      expect(comp.form.contains("weekdays")).toBe(true);
   });

   it("run-once (AT) form contains date and startTime controls", async () => {
      const { comp } = await renderTaskConditionPane();
      comp.changeConditionType({ value: TimeConditionType.AT });
      expect(comp.form.contains("date")).toBe(true);
      expect(comp.form.contains("startTime")).toBe(true);
   });

   it("chained form contains task control and no startTime control", async () => {
      const { comp } = await renderTaskConditionPane();
      comp.changeConditionType({ value: "CompletionCondition" });
      expect(comp.form.contains("task")).toBe(true);
      expect(comp.form.contains("startTime")).toBe(false);
   });

   it("daily form is invalid when interval is null", async () => {
      const { comp } = await renderTaskConditionPane();
      comp.form.get("interval")!.setValue(null);
      comp.form.get("interval")!.updateValueAndValidity();
      expect(comp.form.get("interval")!.invalid).toBe(true);
   });
});

// ===========================================================================
// Group 4 — save: async saveTask flow
// ===========================================================================
describe("Group 4 — save: saveTask → updateTaskName, pristine, listView flip", () => {
   it("emits taskName via updateTaskName after saveTask resolves", async () => {
      const saveTask = vi.fn().mockResolvedValue(undefined);
      const { comp } = await renderTaskConditionPane({ saveTask, taskName: "MyTask" });
      const emitted: string[] = [];
      comp.updateTaskName.subscribe(v => emitted.push(v));

      comp.save(false);

      await waitFor(() => expect(emitted).toHaveLength(1));
      expect(emitted[0]).toBe("MyTask");
   });

   it("marks form pristine after saveTask resolves", async () => {
      const saveTask = vi.fn().mockResolvedValue(undefined);
      const { comp } = await renderTaskConditionPane({ saveTask });
      comp.form.markAsDirty();

      comp.save(false);

      await waitFor(() => expect(comp.form.pristine).toBe(true));
   });

   it("switches to listView when ok=true and model has more than one condition", async () => {
      const saveTask = vi.fn().mockResolvedValue(undefined);
      const model = makeModel({ conditions: [makeDailyCondition(), makeWeeklyCondition()] });
      const { comp } = await renderTaskConditionPane({ model, saveTask });
      comp.listView = false;

      comp.save(true);

      await waitFor(() => expect(comp.listView).toBe(true));
   });

   it("does NOT switch to listView when ok=false even with multiple conditions", async () => {
      const saveTask = vi.fn().mockResolvedValue(undefined);
      const model = makeModel({ conditions: [makeDailyCondition(), makeWeeklyCondition()] });
      const { comp } = await renderTaskConditionPane({ model, saveTask });
      comp.listView = false;

      comp.save(false);

      await waitFor(() => expect(comp.form.pristine).toBe(true));
      expect(comp.listView).toBe(false);
   });
});

// ===========================================================================
// Group 5 — model setter: serverTimeZoneOffset and conditionIndex reset
// ===========================================================================
describe("Group 5 — model setter: serverTimeZoneOffset and conditionIndex reset", () => {
   it("captures serverTimeZoneOffset from model.timeZoneOffset", async () => {
      const { comp } = await renderTaskConditionPane({ model: makeModel({ timeZoneOffset: 3600000 }) });
      expect((comp as any).serverTimeZoneOffset).toBe(3600000);
   });

   it("resets conditionIndex to -1 when new model.conditions is empty (setter-only, no CD)", async () => {
      const { comp } = await renderTaskConditionPane();
      (comp as any).conditionIndex = 0;
      // Invoke setter via prototype descriptor to avoid triggering Angular CD.
      // CD would crash: condition getter pushes a default CompletionCondition when conditions=[],
      // but the form is still the daily form, so template accessing form.controls['task'].valid throws.
      const setter = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(comp), "model")!.set!;
      setter.call(comp, { ...makeModel(), conditions: [] });
      expect((comp as any).conditionIndex).toBe(-1);
   });

   it("resets conditionIndex to -1 when new model.conditions is null (setter-only, no CD)", async () => {
      const { comp } = await renderTaskConditionPane();
      (comp as any).conditionIndex = 0;
      const setter = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(comp), "model")!.set!;
      setter.call(comp, { ...makeModel(), conditions: null as any });
      expect((comp as any).conditionIndex).toBe(-1);
   });
});

// ===========================================================================
// Group 6 — ngOnInit: task filtering, Custom option, enabledOptions
// ===========================================================================
describe("Group 6 — ngOnInit: task filtering, Custom option removal, enabledOptions", () => {
   it("filters oldTaskName out of allTasks when subscription fires", async () => {
      taskNamesMock.getAllTasks.mockReturnValue(
         of([{ name: "OldTask", label: "OldTask" }, { name: "Task2", label: "Task2" }])
      );
      const { comp } = await renderTaskConditionPane({ oldTaskName: "OldTask" });
      await waitFor(() => expect(comp.allTasks.length).toBe(1));
      expect(comp.allTasks[0].name).toBe("Task2");
   });

   it("removes Custom option from allOptions when userDefinedClasses is empty", async () => {
      const { comp } = await renderTaskConditionPane({ model: makeModel({ userDefinedClasses: [] }) });
      expect(comp.allOptions.some(o => o.value === "UserCondition")).toBe(false);
   });

   it("keeps Custom option when userDefinedClasses is non-empty", async () => {
      const { comp } = await renderTaskConditionPane({
         model: makeModel({ userDefinedClasses: ["MyClass"], userDefinedClassLabels: ["My Label"] }),
      });
      expect(comp.allOptions.some(o => o.value === "UserCondition")).toBe(true);
   });

   it("date is null after init when condition.date=0 (updateDate overwrites the UTC guard)", async () => {
      // makeDailyCondition has date:0; updateDate sets this.date=null when date is falsy
      const { comp } = await renderTaskConditionPane();
      expect(comp.date).toBeNull();
   });

   it("excludes AT and EVERY_HOUR from enabledOptions when startTimeEnabled=false", async () => {
      const { comp } = await renderTaskConditionPane({ startTimeEnabled: false });
      expect(comp.enabledOptions.some(o => o.value === TimeConditionType.AT)).toBe(false);
      expect(comp.enabledOptions.some(o => o.value === TimeConditionType.EVERY_HOUR)).toBe(false);
   });

   it("includes AT and EVERY_HOUR in enabledOptions when startTimeEnabled=true", async () => {
      const { comp } = await renderTaskConditionPane({ startTimeEnabled: true });
      expect(comp.enabledOptions.some(o => o.value === TimeConditionType.AT)).toBe(true);
      expect(comp.enabledOptions.some(o => o.value === TimeConditionType.EVERY_HOUR)).toBe(true);
   });
});

// ===========================================================================
// Group 7 — addCondition: append and listView reset
// ===========================================================================
describe("Group 7 — addCondition: appends condition and resets listView", () => {
   it("pushes a new condition and sets listView=false", async () => {
      const { comp } = await renderTaskConditionPane();
      comp.listView = true;
      const before = comp.model.conditions.length;

      comp.addCondition();

      expect(comp.model.conditions.length).toBe(before + 1);
      expect(comp.listView).toBe(false);
   });

   it("falls back to current condition when no stored condition exists in localStorage", async () => {
      // localStorage is cleared in beforeEach; getStoredCondition() returns null
      const { comp } = await renderTaskConditionPane({ model: makeModel({ conditions: [makeDailyCondition({ label: "Current" })] }) });
      comp.addCondition();
      // No stored condition → pushes current condition (label "Current")
      expect(comp.model.conditions[comp.model.conditions.length - 1].label).toBe("Current");
   });
});

// ===========================================================================
// Group 8 — copyCondition: prefix stripping and guards
// ===========================================================================
describe("Group 8 — copyCondition: prefix handling and guards", () => {
   it("strips existing Copy-of prefix then prepends a fresh one", async () => {
      const alreadyCopied = makeDailyCondition({ label: "_#(js:Copy of) Daily" });
      const { comp } = await renderTaskConditionPane({ model: makeModel({ conditions: [alreadyCopied] }) });
      comp.selectedConditions = [0];
      comp.copyCondition();
      expect(comp.model.conditions[1].label).toBe("_#(js:Copy of) Daily");
   });

   it("prepends Copy-of prefix to a condition without one", async () => {
      const { comp } = await renderTaskConditionPane({ model: makeModel({ conditions: [makeDailyCondition({ label: "My Cond" })] }) });
      comp.selectedConditions = [0];
      comp.copyCondition();
      expect(comp.model.conditions[1].label).toBe("_#(js:Copy of) My Cond");
   });

   it("no-ops when no condition is selected", async () => {
      const { comp } = await renderTaskConditionPane();
      comp.selectedConditions = [];
      comp.copyCondition();
      expect(comp.model.conditions.length).toBe(1);
   });

   it("no-ops when multiple conditions are selected", async () => {
      const model = makeModel({ conditions: [makeDailyCondition(), makeWeeklyCondition()] });
      const { comp } = await renderTaskConditionPane({ model });
      comp.selectedConditions = [0, 1];
      comp.copyCondition();
      expect(comp.model.conditions.length).toBe(2);
   });

   it("updates selectedConditions to the index of the newly added copy", async () => {
      const model = makeModel({
         conditions: [makeDailyCondition({ label: "Cond A" }), makeWeeklyCondition({ label: "Cond B" })],
      });
      const { comp } = await renderTaskConditionPane({ model });
      comp.selectedConditions = [0];
      comp.copyCondition();
      expect(comp.selectedConditions).toEqual([2]);
   });

   it("deep-clones the condition so mutating the copy's daysOfWeek does not affect the original", async () => {
      const { comp } = await renderTaskConditionPane({
         model: makeModel({ conditions: [makeWeeklyCondition({ daysOfWeek: [1, 2] })] }),
      });
      comp.selectedConditions = [0];
      comp.copyCondition();
      (comp.model.conditions[1] as TimeConditionModel).daysOfWeek!.push(3);
      expect((comp.model.conditions[0] as TimeConditionModel).daysOfWeek).toEqual([1, 2]);
   });
});

// ===========================================================================
// Group 9 — changeMonthRadioOption: day-mode vs week-mode mutation
// ===========================================================================
describe("Group 9 — changeMonthRadioOption: day-mode and week-mode mutation", () => {
   it("day mode (true): clears weekOfMonth/dayOfWeek, sets dayOfMonth to default", async () => {
      const { comp } = await renderTaskConditionPane({ model: makeModel({ conditions: [makeMonthlyCondition({ monthlyDaySelected: false })] }) });
      comp.changeMonthRadioOption(true);
      const tc = comp.condition as TimeConditionModel;
      expect(tc.monthlyDaySelected).toBe(true);
      expect(tc.weekOfMonth).toBeNull();
      expect(tc.dayOfWeek).toBeNull();
      expect(tc.dayOfMonth).toBe(comp.defaultDayOfMonth);
   });

   it("week mode (false): clears dayOfMonth, sets weekOfMonth/dayOfWeek to defaults", async () => {
      const { comp } = await renderTaskConditionPane({ model: makeModel({ conditions: [makeMonthlyCondition({ monthlyDaySelected: true })] }) });
      comp.changeMonthRadioOption(false);
      const tc = comp.condition as TimeConditionModel;
      expect(tc.monthlyDaySelected).toBe(false);
      expect(tc.dayOfMonth).toBeNull();
      expect(tc.weekOfMonth).toBe(comp.defaultWeekOfMonth);
      expect(tc.dayOfWeek).toBe(comp.defaultDayOfWeek);
   });
});

// ===========================================================================
// Group 10 — setLocalTimeZone: null guard, fallback, label
// ===========================================================================
describe("Group 10 — setLocalTimeZone: null guard, fallback, label assignment", () => {
   it("returns early when timeZoneOptions is null — localTimeZoneId unchanged", async () => {
      const { comp } = await renderTaskConditionPane();
      const prevId = (comp as any).localTimeZoneId;
      comp.timeZoneOptions = null as any;
      comp.setLocalTimeZone("Anything/Gone");
      expect((comp as any).localTimeZoneId).toBe(prevId);
   });

   it("falls back to first option ID when requested ID is not in the list", async () => {
      const { comp } = await renderTaskConditionPane({
         timeZoneOptions: [{ timeZoneId: "UTC", label: "UTC", hourOffset: "+00:00", minuteOffset: 0 }],
      });
      comp.setLocalTimeZone("Nonexistent/Timezone");
      expect((comp as any).localTimeZoneId).toBe("UTC");
   });

   it("assigns tz.label to localTimeZoneLabel for a non-first matched option", async () => {
      const options: TimeZoneModel[] = [
         { timeZoneId: "UTC", label: "Local TZ", hourOffset: "+00:00", minuteOffset: 0 },
         { timeZoneId: "America/New_York", label: "Eastern Time", hourOffset: "-05:00", minuteOffset: -300 },
      ];
      const { comp } = await renderTaskConditionPane({ timeZoneOptions: options });
      comp.setLocalTimeZone("America/New_York");
      expect((comp as any).localTimeZoneLabel).toBe("Eastern Time");
   });
});

// ===========================================================================
// Group 11 — changeServerTimeZone: no-op and localStorage
// ===========================================================================
// LocalStorage utility wraps keys with "__inetsoft__" prefix; read via same prefix
const TZ_LS_KEY = "__inetsoft__inetsoft_conditionServerTimeZone";

describe("Group 11 — changeServerTimeZone: no-op on same value, localStorage", () => {
   it("does NOT write localStorage when value is unchanged", async () => {
      const { comp } = await renderTaskConditionPane();
      comp.serverTimeZone = false;
      comp.changeServerTimeZone(false);
      expect(localStorage.getItem(TZ_LS_KEY)).toBeNull();
   });

   it("writes 'true' to localStorage when switching from false to server time zone", async () => {
      const { comp } = await renderTaskConditionPane();
      comp.serverTimeZone = false;
      comp.changeServerTimeZone(true);
      expect(localStorage.getItem(TZ_LS_KEY)).toBe("true");
   });

   it("writes 'false' to localStorage when switching back from server to local", async () => {
      const { comp } = await renderTaskConditionPane();
      comp.serverTimeZone = true;
      comp.changeServerTimeZone(false);
      expect(localStorage.getItem(TZ_LS_KEY)).toBe("false");
   });

   // Bug #75325: toggling "Show Server Time Zone" while editing one condition must also convert
   // all sibling conditions so that when the user later edits them they already show server time.
   it("converts ALL conditions (including siblings) when switching timezone in both directions (Bug #75325)", async () => {
      const utcOffset = 0;
      const easternOffset = -4 * 60 * 60 * 1000; // EDT = UTC-4

      const cond1 = makeDailyCondition({ timeZone: "America/New_York", hour: 2, minute: 30, second: 0 });
      const cond2 = makeDailyCondition({ timeZone: "America/New_York", hour: 2, minute: 30, second: 0 });
      const { comp } = await renderTaskConditionPane({ model: makeModel({ conditions: [cond1, cond2] }) });

      // Deterministic stub: shift hour by (newTz − oldTz) expressed in ms
      comp.convertTime = vi.fn().mockImplementation((value, oldTz, newTz) => {
         const shifted = value.hour + (newTz - oldTz) / (60 * 60 * 1000);
         return { hour: ((shifted % 24) + 24) % 24, minute: value.minute, second: value.second };
      });

      (comp as any).conditionIndex = 0;
      comp.serverTimeZone = false;
      (comp as any).serverTimeZoneOffset = utcOffset;
      (comp as any).localTimeZoneOffset = easternOffset;
      comp["timeZoneService"].calculateTimezoneOffset = vi.fn(() => easternOffset);

      // Switch to server timezone: 02:30 EDT → 06:30 UTC — both conditions must convert
      comp.changeServerTimeZone(true);
      expect(cond1.hour).toBe(6);
      expect(cond1.minute).toBe(30);
      expect(cond2.hour).toBe(6);
      expect(cond2.minute).toBe(30);

      // Switch back to local: 06:30 UTC → 02:30 EDT — both conditions must restore
      comp.changeServerTimeZone(false);
      expect(cond1.hour).toBe(2);
      expect(cond2.hour).toBe(2);
   });
});

// ===========================================================================
// Group 12 — updateWeekdayOnly: interval validator toggle
// ===========================================================================
describe("Group 12 — updateWeekdayOnly: interval control disabled/enabled", () => {
   it("weekdayOnly=true disables interval control (exempt from validation)", async () => {
      const { comp } = await renderTaskConditionPane();
      comp.updateWeekdayOnly(true);
      // removeIntervalControl() resets with disabled:true — Angular DISABLED !== VALID,
      // but disabled controls are excluded from form validity aggregation
      expect(comp.form.get("interval")!.disabled).toBe(true);
   });

   it("weekdayOnly=false re-enables interval with required validator so null value is invalid", async () => {
      const { comp } = await renderTaskConditionPane();
      comp.updateWeekdayOnly(true);
      comp.updateWeekdayOnly(false);
      comp.form.get("interval")!.setValue(null);
      comp.form.get("interval")!.updateValueAndValidity();
      expect(comp.form.get("interval")!.invalid).toBe(true);
   });
});

// ===========================================================================
// Group 13 — form setters: formStartTimeData, formStartTime, formEndTime
// ===========================================================================
describe("Group 13 — form setters: formStartTimeData, formStartTime, formEndTime", () => {
   it("formStartTimeData with startTimeSelected=false stores timeRange on condition", async () => {
      const timeRange: TimeRange = { name: "Morning", label: "Morning", startTime: "08:00", endTime: "12:00", defaultRange: true };
      const { comp } = await renderTaskConditionPane();
      comp.formStartTimeData = { startTime: null as any, timeRange, startTimeSelected: false, valid: true };
      expect((comp.condition as TimeConditionModel).timeRange).toEqual(timeRange);
   });

   it("formStartTimeData with startTimeSelected=true clears timeRange on condition", async () => {
      const { comp } = await renderTaskConditionPane();
      comp.formStartTimeData = { startTime: { hour: 9, minute: 0, second: 0 }, timeRange: null as any, startTimeSelected: true, valid: true };
      expect((comp.condition as TimeConditionModel).timeRange).toBeNull();
   });

   it("formStartTime null sets model.taskDefaultTime=false", async () => {
      const { comp } = await renderTaskConditionPane();
      comp.formStartTime = null as any;
      expect(comp.model.taskDefaultTime).toBe(false);
   });

   it("formStartTime with a time value sets model.taskDefaultTime=true", async () => {
      const { comp } = await renderTaskConditionPane();
      comp.formStartTime = { hour: 10, minute: 0, second: 0 };
      expect(comp.model.taskDefaultTime).toBe(true);
   });

   it("formEndTime setter updates hourEnd/minuteEnd/secondEnd on condition", async () => {
      const { comp } = await renderTaskConditionPane();
      comp.formEndTime = { hour: 15, minute: 45, second: 30 };
      const tc = comp.condition as TimeConditionModel;
      expect(tc.hourEnd).toBe(15);
      expect(tc.minuteEnd).toBe(45);
      expect(tc.secondEnd).toBe(30);
   });
});

// ===========================================================================
// Group 14 — startTime setter: taskDefaultTime gate
// ===========================================================================
describe("Group 14 — startTime setter: taskDefaultTime gate", () => {
   it("stores null when model.taskDefaultTime is false", async () => {
      const { comp } = await renderTaskConditionPane({ model: makeModel({ taskDefaultTime: false }) });
      comp.startTime = { hour: 10, minute: 0, second: 0 };
      expect(comp.startTime).toBeNull();
   });

   it("stores the given time when model.taskDefaultTime is true", async () => {
      const { comp } = await renderTaskConditionPane({ model: makeModel({ taskDefaultTime: true }) });
      comp.startTime = { hour: 10, minute: 30, second: 0 };
      expect(comp.startTime).toEqual({ hour: 10, minute: 30, second: 0 });
   });
});

// ===========================================================================
// Group 15 — currentTimeZoneOffset: server vs local
// ===========================================================================
describe("Group 15 — currentTimeZoneOffset: server vs local dispatch", () => {
   it("returns serverTimeZoneOffset when serverTimeZone=true", async () => {
      const { comp } = await renderTaskConditionPane();
      comp.serverTimeZone = true;
      (comp as any).serverTimeZoneOffset = 7200000;
      expect(comp.currentTimeZoneOffset).toBe(7200000);
   });

   it("returns localTimeZoneOffset when serverTimeZone=false", async () => {
      const { comp } = await renderTaskConditionPane();
      comp.serverTimeZone = false;
      comp.localTimeZoneOffset = -18000000;
      expect(comp.currentTimeZoneOffset).toBe(-18000000);
   });
});

// ===========================================================================
// Group 16 — isPresent / updateList / selectAll utilities
// ===========================================================================
describe("Group 16 — isPresent / updateList / selectAll utilities", () => {
   it("isPresent returns true when item is in array", async () => {
      const { comp } = await renderTaskConditionPane();
      expect(comp.isPresent([1, 2, 3], 2)).toBe(true);
   });

   it("isPresent returns false when item is absent", async () => {
      const { comp } = await renderTaskConditionPane();
      expect(comp.isPresent([1, 2, 3], 5)).toBe(false);
   });

   it("isPresent returns false for null array", async () => {
      const { comp } = await renderTaskConditionPane();
      expect(comp.isPresent(null as any, 1)).toBeFalsy();
   });

   it("updateList adds item when absent", async () => {
      const { comp } = await renderTaskConditionPane();
      const arr = [1, 3];
      comp.updateList(arr, 2);
      expect(arr).toContain(2);
   });

   it("updateList removes item when already present", async () => {
      const { comp } = await renderTaskConditionPane();
      const arr = [1, 2, 3];
      comp.updateList(arr, 2);
      expect(arr).not.toContain(2);
   });

   it("selectAll fills array with startIndex..startIndex+length-1", async () => {
      const { comp } = await renderTaskConditionPane();
      const arr = new Array(3);
      comp.selectAll(arr, 3, 1);
      expect(arr).toEqual([1, 2, 3]);
   });

   it("selectAll defaults startIndex to 0", async () => {
      const { comp } = await renderTaskConditionPane();
      const arr = new Array(4);
      comp.selectAll(arr, 4);
      expect(arr).toEqual([0, 1, 2, 3]);
   });
});

// ===========================================================================
// Group 17 — simple getters
// ===========================================================================
describe("Group 17 — conditionNames / showMeridian / loadingTasks", () => {
   it("conditionNames maps conditions to their labels", async () => {
      const model = makeModel({
         conditions: [makeDailyCondition({ label: "A" }), makeWeeklyCondition({ label: "B" })],
      });
      const { comp } = await renderTaskConditionPane({ model });
      expect(comp.conditionNames).toEqual(["A", "B"]);
   });

   it("conditionNames returns empty array when conditions is null", async () => {
      const { comp } = await renderTaskConditionPane();
      (comp as any)._model = { ...comp.model, conditions: null };
      expect(comp.conditionNames).toEqual([]);
   });

   it("showMeridian is false when twelveHourSystem=false", async () => {
      const { comp } = await renderTaskConditionPane({ model: makeModel({ twelveHourSystem: false }) });
      expect(comp.showMeridian).toBe(false);
   });

   it("showMeridian is true when twelveHourSystem=true", async () => {
      const { comp } = await renderTaskConditionPane({ model: makeModel({ twelveHourSystem: true }) });
      expect(comp.showMeridian).toBe(true);
   });

   it("loadingTasks reflects service.isLoading", async () => {
      const { comp } = await renderTaskConditionPane();
      taskNamesMock.isLoading = true;
      expect(comp.loadingTasks).toBe(true);
      taskNamesMock.isLoading = false;
      expect(comp.loadingTasks).toBe(false);
   });
});

// ===========================================================================
// Group 18 — updateDaysOfWeekStatus / updateMonthsOfYearStatus
// ===========================================================================
describe("Group 18 — updateDaysOfWeekStatus / updateMonthsOfYearStatus", () => {
   it("resets weekday controls from condition.daysOfWeek (1-based index)", async () => {
      // daysOfWeek [1,3]: Sunday (i+1=1) and Tuesday (i+1=3)
      const { comp } = await renderTaskConditionPane({
         model: makeModel({ conditions: [makeWeeklyCondition({ daysOfWeek: [1, 3] })] }),
      });
      comp.updateDaysOfWeekStatus();
      const weekdays = comp.form.get("weekdays") as any;
      expect(weekdays.at(0).value).toBe(true);  // Sunday  (i+1=1) present
      expect(weekdays.at(1).value).toBe(false); // Monday  (i+1=2) absent
      expect(weekdays.at(2).value).toBe(true);  // Tuesday (i+1=3) present
   });

   it("resets month controls from condition.monthsOfYear (0-based index)", async () => {
      // monthsOfYear [0,5]: January (i=0) and June (i=5)
      const { comp } = await renderTaskConditionPane({
         model: makeModel({ conditions: [makeMonthlyCondition({ monthsOfYear: [0, 5] })] }),
      });
      comp.updateMonthsOfYearStatus();
      const months = comp.form.get("months") as any;
      expect(months.at(0).value).toBe(true);  // January  (i=0) present
      expect(months.at(1).value).toBe(false); // February (i=1) absent
      expect(months.at(5).value).toBe(true);  // June     (i=5) present
   });
});

// ===========================================================================
// Group 19 — timeConditions getter
// ===========================================================================
describe("Group 19 — timeConditions getter: Set membership", () => {
   it("includes dailyCondition in the set after init", async () => {
      const { comp } = await renderTaskConditionPane();
      expect(comp.timeConditions.has(comp.dailyCondition)).toBe(true);
   });

   it("includes current TimeCondition when conditionType is TimeCondition", async () => {
      const { comp } = await renderTaskConditionPane();
      expect(comp.timeConditions.has(comp.condition as TimeConditionModel)).toBe(true);
   });

   it("does not include null in the set", async () => {
      const { comp } = await renderTaskConditionPane();
      expect(comp.timeConditions.has(null as any)).toBe(false);
   });
});

// ===========================================================================
// Group 20 — convertTime: UTC-offset arithmetic
// ===========================================================================
describe("Group 20 — convertTime: UTC-offset arithmetic", () => {
   it("shifts hour forward by the positive offset delta", async () => {
      const { comp } = await renderTaskConditionPane();
      const result = comp.convertTime({ hour: 10, minute: 30, second: 0 }, 0, 4 * 60 * 60 * 1000);
      expect(result.hour).toBe(14);
      expect(result.minute).toBe(30);
   });

   it("wraps around midnight correctly for negative delta", async () => {
      const { comp } = await renderTaskConditionPane();
      const result = comp.convertTime({ hour: 1, minute: 0, second: 0 }, 0, -3 * 60 * 60 * 1000);
      expect(result.hour).toBe(22);
   });

   it("returns same time when old and new offsets are equal", async () => {
      const { comp } = await renderTaskConditionPane();
      const result = comp.convertTime({ hour: 12, minute: 0, second: 0 }, 0, 0);
      expect(result.hour).toBe(12);
      expect(result.minute).toBe(0);
   });
});

// ===========================================================================
// Group 21 — ngOnChanges: listView false flip → addCondition
// ===========================================================================
describe("Group 21 — ngOnChanges: listView false flip triggers addCondition", () => {
   it("calls addCondition when listView changes from true to false (non-first change)", async () => {
      const { comp } = await renderTaskConditionPane();
      const spy = vi.spyOn(comp, "addCondition").mockImplementation(() => {});
      try {
         comp.ngOnChanges({
            listView: { currentValue: false, previousValue: true, firstChange: false, isFirstChange: () => false } as SimpleChange,
         });
         expect(spy).toHaveBeenCalled();
      } finally {
         spy.mockRestore();
      }
   });

   it("does not call addCondition on the first change", async () => {
      const { comp } = await renderTaskConditionPane();
      const spy = vi.spyOn(comp, "addCondition").mockImplementation(() => {});
      try {
         comp.ngOnChanges({
            listView: { currentValue: false, previousValue: undefined, firstChange: true, isFirstChange: () => true } as SimpleChange,
         });
         expect(spy).not.toHaveBeenCalled();
      } finally {
         spy.mockRestore();
      }
   });

   it("does not call addCondition when listView changes to true", async () => {
      const { comp } = await renderTaskConditionPane();
      const spy = vi.spyOn(comp, "addCondition").mockImplementation(() => {});
      try {
         comp.ngOnChanges({
            listView: { currentValue: true, previousValue: false, firstChange: false, isFirstChange: () => false } as SimpleChange,
         });
         expect(spy).not.toHaveBeenCalled();
      } finally {
         spy.mockRestore();
      }
   });
});
