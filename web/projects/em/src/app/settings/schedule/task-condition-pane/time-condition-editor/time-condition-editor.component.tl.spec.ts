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
 * TimeConditionEditorComponent — Testing Library style
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — onModelChanged: this.condition update and modelChanged re-emission atomicity
 *   Group 2 [Risk 2] — ngSwitch: correct sub-editor element rendered per condition.type
 *
 * KEY contracts:
 *   - onModelChanged() MUST update this.condition BEFORE emitting modelChanged — subscribers
 *     that read comp.condition from within the emit callback must see the new value.
 *   - modelChanged is re-emitted with the SAME change object reference received from the sub-editor.
 *   - modelChanged is emitted regardless of change.valid — the parent decides how to handle invalid state.
 *   - Mapped condition types → sub-editor elements:
 *       AT(0)          → em-run-once-condition-editor
 *       EVERY_DAY(1)   → em-daily-condition-editor
 *       EVERY_WEEK(6)  → em-weekly-condition-editor
 *       EVERY_MONTH(7) → em-monthly-condition-editor
 *       EVERY_HOUR(8)  → em-hourly-condition-editor
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { CommonModule } from "@angular/common";
import { render } from "@testing-library/angular";

import { TimeConditionEditorComponent } from "./time-condition-editor.component";
import { TimeConditionModel, TimeConditionType } from "../../../../../../../shared/schedule/model/time-condition-model";
import { TaskConditionChanges } from "../task-condition-pane.component";

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

function makeCondition(type: TimeConditionType): TimeConditionModel {
   return {
      type,
      conditionType: "TimeCondition",
      label: "",
      hour: 8,
      minute: 0,
      second: 0,
   };
}

function makeChange(condition: TimeConditionModel, valid = true): TaskConditionChanges {
   return { valid, model: condition };
}

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

async function renderComponent(props: Partial<TimeConditionEditorComponent> = {}) {
   const result = await render(TimeConditionEditorComponent, {
      imports: [CommonModule],
      schemas: [NO_ERRORS_SCHEMA],
      componentProperties: {
         condition: makeCondition(TimeConditionType.EVERY_DAY),
         ...props,
      },
   });

   await result.fixture.whenStable();

   return { ...result, comp: result.fixture.componentInstance };
}

// ---------------------------------------------------------------------------
// Group 1 [Risk 3] — onModelChanged: condition update + modelChanged re-emission
// ---------------------------------------------------------------------------

describe("TimeConditionEditorComponent — onModelChanged: condition update and re-emission", () => {

   // 🔁 Regression-sensitive: both this.condition update and modelChanged emission must happen
   // on every call.  If either is dropped during refactoring, the parent silently loses the
   // change (missing emit) or the sub-editor receives stale props on the next cycle (missing update).
   it("should update this.condition and emit modelChanged with the same change object", async () => {
      const { comp } = await renderComponent();

      const newCondition = makeCondition(TimeConditionType.EVERY_WEEK);
      const change = makeChange(newCondition, true);

      const emitted: TaskConditionChanges[] = [];
      comp.modelChanged.subscribe(e => emitted.push(e));

      comp.onModelChanged(change);

      expect(comp.condition).toBe(newCondition);           // local state updated
      expect(emitted).toHaveLength(1);
      expect(emitted[0]).toBe(change);                     // same object reference — no clone
   });

   // 🔁 Regression-sensitive: this.condition must already hold the NEW value by the time the
   // modelChanged EventEmitter fires — any subscriber that reads comp.condition during the emit
   // callback (e.g. a parent that immediately queries the child) must see the updated condition.
   it("should have this.condition updated by the time the modelChanged subscriber runs", async () => {
      const { comp } = await renderComponent();

      const newCondition = makeCondition(TimeConditionType.EVERY_MONTH);
      let conditionDuringEmit: TimeConditionModel | null = null;

      comp.modelChanged.subscribe(() => {
         conditionDuringEmit = comp.condition as TimeConditionModel;
      });

      comp.onModelChanged(makeChange(newCondition));

      expect(conditionDuringEmit).toBe(newCondition);
   });

   // 🔁 Regression-sensitive: valid=false must NOT suppress the emission — the parent needs
   // invalid-state notifications to block saving and display error cues.
   it("should emit modelChanged even when change.valid is false", async () => {
      const { comp } = await renderComponent();

      const emitted: TaskConditionChanges[] = [];
      comp.modelChanged.subscribe(e => emitted.push(e));

      comp.onModelChanged(makeChange(makeCondition(TimeConditionType.EVERY_HOUR), false));

      expect(emitted).toHaveLength(1);
      expect(emitted[0].valid).toBe(false);
   });

   // Boundary: new model has a different type than the current condition — this.condition must
   // still be updated without any type-guard blocking the assignment.
   it("should update this.condition even when the new model type differs from the current type", async () => {
      const { comp } = await renderComponent({ condition: makeCondition(TimeConditionType.EVERY_DAY) });

      const differentTypeCondition = makeCondition(TimeConditionType.AT);
      comp.onModelChanged(makeChange(differentTypeCondition));

      expect((comp.condition as TimeConditionModel).type).toBe(TimeConditionType.AT);
   });

});

// ---------------------------------------------------------------------------
// Group 2 [Risk 2] — ngSwitch: sub-editor routing per condition.type
// ---------------------------------------------------------------------------

describe("TimeConditionEditorComponent — ngSwitch: sub-editor routing", () => {

   // 🔁 Regression-sensitive: EVERY_DAY must render the daily editor and no other editor —
   // ngSwitch exclusivity ensures the user sees exactly one editor at a time.
   it("should render only em-daily-condition-editor for EVERY_DAY type", async () => {
      const { container } = await renderComponent({ condition: makeCondition(TimeConditionType.EVERY_DAY) });

      expect(container.querySelector("em-daily-condition-editor")).toBeTruthy();
      expect(container.querySelector("em-weekly-condition-editor")).toBeFalsy();
      expect(container.querySelector("em-monthly-condition-editor")).toBeFalsy();
      expect(container.querySelector("em-hourly-condition-editor")).toBeFalsy();
      expect(container.querySelector("em-run-once-condition-editor")).toBeFalsy();
   });

   // 🔁 Regression-sensitive: AT type uses a dedicated run-once editor distinct from the
   // repeating-schedule editors — rendering the wrong editor would expose irrelevant fields.
   it("should render only em-run-once-condition-editor for AT type", async () => {
      const { container } = await renderComponent({ condition: makeCondition(TimeConditionType.AT) });

      expect(container.querySelector("em-run-once-condition-editor")).toBeTruthy();
      expect(container.querySelector("em-daily-condition-editor")).toBeFalsy();
      expect(container.querySelector("em-weekly-condition-editor")).toBeFalsy();
      expect(container.querySelector("em-monthly-condition-editor")).toBeFalsy();
      expect(container.querySelector("em-hourly-condition-editor")).toBeFalsy();
   });

});
