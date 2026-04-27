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
 * TaskConditionPaneComponent — Testing Library style
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — ngOnChanges: selectedConditionType matched from condition.type
 *   Group 2 [Risk 3] — changeConditionType: default model creation, TZ seeded from timeZoneOptions[0],
 *                       valid=true only for EVERY_DAY and AT
 *   Group 3 [Risk 2] — conditionTypes getter: cycle / startTimeEnabled filtering
 *
 * KEY contracts:
 *   - ngOnChanges matches selectedConditionType by (conditionType, subtype) pair.
 *   - When condition is null/undefined, selectedConditionType defaults to _conditionTypes[0]
 *     (EVERY_DAY) and condition is built from getDefaultTimeConditionModel.
 *   - changeConditionType() seeds the new condition's timeZone/timeZoneLabel from
 *     timeZoneOptions[0] — if timeZoneOptions is empty/null, this crashes.
 *   - changeConditionType() emits valid=true for EVERY_DAY (subtype=1) and AT (subtype=0),
 *     valid=false for all other types (operator-precedence rule:
 *     (type==="TimeCondition" && subtype===AT) || subtype===EVERY_DAY).
 *   - conditionTypes with cycle=true: excludes AT (excludeCycle) and CompletionCondition (excludeCycle).
 *   - conditionTypes with startTimeEnabled=false: excludes EVERY_HOUR and AT.
 *   - onModelChanged updates this.condition and re-emits with the sub-editor's valid flag.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { render } from "@testing-library/angular";

import { TaskConditionPaneComponent, TaskConditionChanges } from "./task-condition-pane.component";
import {
   TimeConditionModel,
   TimeConditionType
} from "../../../../../../shared/schedule/model/time-condition-model";
import { TimeZoneModel } from "../../../../../../shared/schedule/model/time-zone-model";

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

function makeTimeCondition(type: TimeConditionType): TimeConditionModel {
   return {
      type,
      conditionType: "TimeCondition",
      label: "",
      hour: 8,
      minute: 0,
      second: 0,
   };
}

function makeTimeZoneOptions(): TimeZoneModel[] {
   return [
      { timeZoneId: "America/New_York", label: "Eastern Time", hourOffset: "-05:00", minuteOffset: -300 },
      { timeZoneId: "UTC", label: "UTC", hourOffset: "+00:00", minuteOffset: 0 },
   ];
}

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

async function renderComponent(props: Partial<TaskConditionPaneComponent> = {}) {
   const result = await render(TaskConditionPaneComponent, {
      imports: [CommonModule, FormsModule],
      schemas: [NO_ERRORS_SCHEMA],
      componentProperties: {
         condition: makeTimeCondition(TimeConditionType.EVERY_DAY),
         timeZoneOptions: makeTimeZoneOptions(),
         ...props,
      },
   });

   await result.fixture.whenStable();
   return { ...result, comp: result.fixture.componentInstance };
}

// ---------------------------------------------------------------------------
// Group 1 [Risk 3] — ngOnChanges: selectedConditionType matched from condition
// ---------------------------------------------------------------------------

describe("TaskConditionPaneComponent — ngOnChanges: selectedConditionType matched from condition", () => {

   // 🔁 Regression-sensitive: when a condition is passed in, ngOnChanges must find the matching
   // TaskConditionType by (conditionType, subtype) pair.  A mismatch renders the wrong sub-editor
   // and the user edits a different condition type than what is saved.
   it("should match selectedConditionType to EVERY_WEEK when condition.type is EVERY_WEEK", async () => {
      const { comp } = await renderComponent({
         condition: makeTimeCondition(TimeConditionType.EVERY_WEEK),
      });

      expect(comp.selectedConditionType).toBeDefined();
      expect(comp.selectedConditionType.type).toBe("TimeCondition");
      expect(comp.selectedConditionType.subtype).toBe(TimeConditionType.EVERY_WEEK);
   });

   // 🔁 Regression-sensitive: EVERY_MONTH condition must match its own type — not fallback to
   // EVERY_DAY because the find() loop short-circuits.
   it("should match selectedConditionType to EVERY_MONTH when condition.type is EVERY_MONTH", async () => {
      const { comp } = await renderComponent({
         condition: makeTimeCondition(TimeConditionType.EVERY_MONTH),
      });

      expect(comp.selectedConditionType.subtype).toBe(TimeConditionType.EVERY_MONTH);
   });

   // 🔁 Regression-sensitive: when no condition is provided (null), selectedConditionType must
   // default to the first entry (_conditionTypes[0] = EVERY_DAY) and a synthetic condition must
   // be constructed — without this, the sub-editor renders with no condition and crashes.
   it("should default selectedConditionType to EVERY_DAY when condition is null", async () => {
      const { comp } = await renderComponent({ condition: null });

      expect(comp.selectedConditionType.subtype).toBe(TimeConditionType.EVERY_DAY);
      expect(comp.condition).toBeDefined();
      expect((comp.condition as TimeConditionModel).type).toBe(TimeConditionType.EVERY_DAY);
   });

   // 🔁 Regression-sensitive: CompletionCondition must match by conditionType="CompletionCondition"
   // rather than by subtype — the type discriminant must be checked first.
   it("should match selectedConditionType to CompletionCondition for a completion condition", async () => {
      const { comp } = await renderComponent({
         condition: { conditionType: "CompletionCondition", label: "", taskName: "SomeTask" } as any,
      });

      expect(comp.selectedConditionType.type).toBe("CompletionCondition");
   });

});

// ---------------------------------------------------------------------------
// Group 2 [Risk 3] — changeConditionType: default model, TZ seeding, valid emission
// ---------------------------------------------------------------------------

describe("TaskConditionPaneComponent — changeConditionType: default model creation and valid emission", () => {

   // 🔁 Regression-sensitive: changeConditionType must seed the new condition's timeZone from
   // timeZoneOptions[0].  If this assignment is skipped, the condition is saved without a timezone
   // — the server may apply an incorrect default (typically UTC) regardless of the user's locale.
   it("should seed condition.timeZone from timeZoneOptions[0] when type is changed", async () => {
      const { comp } = await renderComponent({
         condition: makeTimeCondition(TimeConditionType.EVERY_DAY),
         timeZoneOptions: makeTimeZoneOptions(),
      });

      comp.selectedConditionType = comp._conditionTypes.find(
         t => t.subtype === TimeConditionType.EVERY_WEEK
      );
      comp.changeConditionType();

      expect((comp.condition as TimeConditionModel).timeZone).toBe("America/New_York");
      expect((comp.condition as TimeConditionModel).timeZoneLabel).toBe("Eastern Time");
   });

   // 🔁 Regression-sensitive: EVERY_DAY must emit valid=true immediately after type change —
   // the parent must not block saving a newly-created daily schedule before any sub-editor
   // interaction.  Due to operator precedence:
   //   (type==="TimeCondition" && AT==subtype) || EVERY_DAY==subtype
   // EVERY_DAY satisfies the second clause → valid=true.
   it("should emit valid=true when changeConditionType selects EVERY_DAY", async () => {
      const emitted: TaskConditionChanges[] = [];
      const { comp } = await renderComponent({ condition: makeTimeCondition(TimeConditionType.EVERY_WEEK) });

      comp.modelChanged.subscribe(e => emitted.push(e));
      comp.selectedConditionType = comp._conditionTypes.find(
         t => t.subtype === TimeConditionType.EVERY_DAY
      );
      comp.changeConditionType();

      expect(emitted.length).toBeGreaterThan(0);
      expect(emitted.at(-1).valid).toBe(true);
   });

   // 🔁 Regression-sensitive: EVERY_WEEK must emit valid=false — a freshly-created weekly
   // condition has no days selected (daysOfWeek=[]), which fails the weekdays required validator.
   // Emitting valid=true here would unblock the parent's save guard prematurely.
   it("should emit valid=false when changeConditionType selects EVERY_WEEK", async () => {
      const emitted: TaskConditionChanges[] = [];
      const { comp } = await renderComponent({ condition: makeTimeCondition(TimeConditionType.EVERY_DAY) });

      comp.modelChanged.subscribe(e => emitted.push(e));
      comp.selectedConditionType = comp._conditionTypes.find(
         t => t.subtype === TimeConditionType.EVERY_WEEK
      );
      comp.changeConditionType();

      expect(emitted.length).toBeGreaterThan(0);
      expect(emitted.at(-1).valid).toBe(false);
   });

   // 🔁 Regression-sensitive: AT (Run Once) must also emit valid=true — it satisfies the first
   // clause of the operator-precedence rule.
   it("should emit valid=true when changeConditionType selects AT (Run Once)", async () => {
      const emitted: TaskConditionChanges[] = [];
      const { comp } = await renderComponent({ condition: makeTimeCondition(TimeConditionType.EVERY_DAY) });

      comp.modelChanged.subscribe(e => emitted.push(e));
      comp.selectedConditionType = comp._conditionTypes.find(t => t.subtype === TimeConditionType.AT);
      comp.changeConditionType();

      expect(emitted.at(-1).valid).toBe(true);
   });

});

// ---------------------------------------------------------------------------
// Group 3 [Risk 2] — conditionTypes getter: cycle / startTimeEnabled filtering
// ---------------------------------------------------------------------------

describe("TaskConditionPaneComponent — conditionTypes getter: cycle and startTimeEnabled filtering", () => {

   // 🔁 Regression-sensitive: when cycle=true, AT (excludeCycle) and CompletionCondition
   // (excludeCycle) must be excluded.  Showing these types in the cycle dropdown would let the
   // user create a cyclic condition that the server cannot represent in a cycle context.
   it("should exclude AT and CompletionCondition from conditionTypes when cycle=true", async () => {
      const { comp } = await renderComponent({ cycle: true });

      const types = comp.conditionTypes;
      expect(types.find(t => t.subtype === TimeConditionType.AT)).toBeUndefined();
      expect(types.find(t => t.type === "CompletionCondition")).toBeUndefined();
      // EVERY_DAY, EVERY_WEEK, EVERY_MONTH, EVERY_HOUR must still be present
      expect(types.find(t => t.subtype === TimeConditionType.EVERY_DAY)).toBeDefined();
      expect(types.find(t => t.subtype === TimeConditionType.EVERY_HOUR)).toBeDefined();
   });

   // 🔁 Regression-sensitive: when startTimeEnabled=false, EVERY_HOUR and AT must be excluded.
   // These condition types require a start time, which the parent has signalled is not applicable.
   // Showing them would let the user create a condition that cannot be configured.
   it("should exclude EVERY_HOUR and AT from conditionTypes when startTimeEnabled=false", async () => {
      const { comp } = await renderComponent({ startTimeEnabled: false });

      const types = comp.conditionTypes;
      expect(types.find(t => t.subtype === TimeConditionType.EVERY_HOUR)).toBeUndefined();
      expect(types.find(t => t.subtype === TimeConditionType.AT)).toBeUndefined();
      // Daily, Weekly, Monthly, Chained must still be available
      expect(types.find(t => t.subtype === TimeConditionType.EVERY_DAY)).toBeDefined();
      expect(types.find(t => t.type === "CompletionCondition")).toBeDefined();
   });

});
