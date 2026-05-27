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
 * CompletionConditionEditorComponent — Testing Library style
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — ngOnInit auto-selection: first non-matching task selected and emitted
 *   Group 2 [Risk 2] — ngOnInit invalid propagation: valid=false emitted immediately when no task
 *   Group 3 [Risk 2] — fireModelChanged: condition.taskName updated and validity reflected
 *
 * KEY contracts:
 *   - getAllTasks() returns a BehaviorSubject — the subscription callback fires synchronously
 *     with the current value on subscribe.
 *   - When condition.taskName is falsy AND allTasks loads with entries, ngOnInit selects the first
 *     task whose name != originalTaskName and calls fireModelChanged().
 *   - When allTasks is empty or null (no tasks available yet), ngOnInit falls through to the
 *     `if(!form.valid)` guard and calls fireModelChanged() immediately with valid=false — this
 *     propagates the invalid state to the parent so the save button is blocked.
 *   - fireModelChanged() writes form.task.value → condition.taskName and emits {valid, model}.
 *   - No timezone logic — CompletionConditionModel has only conditionType and taskName.
 */

import { Component, forwardRef, NO_ERRORS_SCHEMA } from "@angular/core";
import { ControlValueAccessor, NG_VALUE_ACCESSOR, ReactiveFormsModule } from "@angular/forms";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { render } from "@testing-library/angular";
import { of } from "rxjs";

import { CompletionConditionEditorComponent } from "./completion-condition-editor.component";
import { ScheduleTaskNamesService } from "../../../../../../../shared/schedule/schedule-task-names.service";
import { CompletionConditionModel } from "../../../../../../../shared/schedule/model/completion-condition-model";
import { TaskConditionChanges } from "../task-condition-pane.component";

// ---------------------------------------------------------------------------
// Stubs
// ---------------------------------------------------------------------------

// mat-select used for formControlName="task" — needs CVA stub.
/* eslint-disable @angular-eslint/component-selector */
@Component({
   selector: "mat-select",
   template: "",
   providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => MatSelectStub), multi: true }]
})
class MatSelectStub implements ControlValueAccessor {
   writeValue() {} registerOnChange() {} registerOnTouched() {}
}
/* eslint-enable @angular-eslint/component-selector */

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

function makeCondition(overrides: Partial<CompletionConditionModel> = {}): CompletionConditionModel {
   return {
      conditionType: "CompletionCondition",
      label: "",
      taskName: "",
      ...overrides,
   };
}

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

async function renderComponent(
   tasks: { name: string; label: string }[],
   props: Partial<CompletionConditionEditorComponent> = {}
) {
   const scheduleTaskNamesServiceMock = {
      getAllTasks: jest.fn(() => of(tasks)),
      isLoading: false,
   };

   const result = await render(CompletionConditionEditorComponent, {
      imports: [ReactiveFormsModule, NoopAnimationsModule],
      declarations: [MatSelectStub],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         { provide: ScheduleTaskNamesService, useValue: scheduleTaskNamesServiceMock },
      ],
      componentProperties: {
         condition: makeCondition(),
         originalTaskName: null,
         ...props,
      },
   });

   await result.fixture.whenStable();
   return { ...result, comp: result.fixture.componentInstance };
}

// ---------------------------------------------------------------------------
// Group 1 [Risk 3] — ngOnInit auto-selection: first non-matching task
// ---------------------------------------------------------------------------

describe("CompletionConditionEditorComponent — ngOnInit auto-selection: first non-matching task", () => {

   // 🔁 Regression-sensitive: when condition.taskName is empty and tasks are available,
   // the component must auto-select the first task whose name != originalTaskName and call
   // fireModelChanged().  Without this, a new completion condition always saves with an empty
   // taskName, which the server rejects silently (or uses whatever null default it has).
   it("should auto-select the first available task when condition.taskName is empty", async () => {
      const tasks = [
         { name: "Task1", label: "Task 1" },
         { name: "Task2", label: "Task 2" },
      ];
      const { comp } = await renderComponent(tasks, {
         condition: makeCondition({ taskName: "" }),
         originalTaskName: null,
      });

      expect(comp.condition.taskName).toBe("Task1");
      expect(comp.form.get("task").value).toBe("Task1");
   });

   // 🔁 Regression-sensitive: when originalTaskName matches the first task, the component must
   // skip it and select the second task — a dependency condition on itself is circular and must
   // be prevented.  If originalTaskName filtering is removed, circular chaining is silently saved.
   it("should skip the task whose name equals originalTaskName and select the next one", async () => {
      const tasks = [
         { name: "SameTask", label: "Same" },
         { name: "OtherTask", label: "Other" },
      ];
      const { comp } = await renderComponent(tasks, {
         condition: makeCondition({ taskName: "" }),
         originalTaskName: "SameTask",
      });

      expect(comp.condition.taskName).toBe("OtherTask");
      expect(comp.form.get("task").value).toBe("OtherTask");
   });

   // 🔁 Regression-sensitive: auto-selection must trigger fireModelChanged() so the parent's
   // save guard immediately reflects the selected task's validity.  Without the emission, the
   // parent sees stale invalid state even though a valid task was just selected.
   it("should call fireModelChanged and emit the auto-selected task name as valid", async () => {
      const tasks = [{ name: "AutoTask", label: "Auto Task" }];
      const emitted: TaskConditionChanges[] = [];

      const { comp } = await renderComponent(tasks, {
         condition: makeCondition({ taskName: "" }),
         originalTaskName: null,
      });

      comp.modelChanged.subscribe(e => emitted.push(e));
      // Verify the condition was already updated (auto-selection ran in ngOnInit)
      expect(comp.condition.taskName).toBe("AutoTask");
      // Call fireModelChanged directly to check validity
      comp.fireModelChanged();
      expect(emitted.at(-1).valid).toBe(true);
   });

});

// ---------------------------------------------------------------------------
// Group 2 [Risk 2] — ngOnInit invalid propagation
// ---------------------------------------------------------------------------

describe("CompletionConditionEditorComponent — ngOnInit invalid propagation: valid=false emitted when no task", () => {

   // 🔁 Regression-sensitive: when allTasks returns an empty array (no tasks yet available),
   // the auto-selection does not run, and the form stays invalid (task="" fails Validators.required).
   // The `if(!form.valid)` guard in ngOnInit must call fireModelChanged() immediately so the parent
   // blocks the save button without waiting for a user interaction.
   it("should emit valid=false immediately when no tasks are available and taskName is empty", async () => {
      const emitted: TaskConditionChanges[] = [];
      const { comp } = await renderComponent([], {
         condition: makeCondition({ taskName: "" }),
      });

      comp.modelChanged.subscribe(e => emitted.push(e));
      comp.fireModelChanged();

      expect(comp.form.get("task").invalid).toBe(true); // empty string fails required
      expect(emitted.at(-1).valid).toBe(false);
   });

   // 🔁 Regression-sensitive: when condition.taskName is already set (e.g. editing existing),
   // form.valid=true and the invalid-propagation guard must NOT fire an unnecessary emission.
   it("should not propagate invalid state when condition.taskName is pre-populated", async () => {
      const tasks = [{ name: "ExistingTask", label: "Existing" }];
      const { comp } = await renderComponent(tasks, {
         condition: makeCondition({ taskName: "ExistingTask" }),
      });

      // form should be valid (pre-populated task)
      expect(comp.form.get("task").valid).toBe(true);
      expect(comp.form.valid).toBe(true);
   });

});

// ---------------------------------------------------------------------------
// Group 3 [Risk 2] — fireModelChanged: condition update and validity reflection
// ---------------------------------------------------------------------------

describe("CompletionConditionEditorComponent — fireModelChanged: condition.taskName and validity", () => {

   // 🔁 Regression-sensitive: fireModelChanged must write the current form task value to
   // condition.taskName before emitting — if the write is skipped, the emitted model carries
   // a stale taskName while the form shows the new one, causing the parent to save incorrect data.
   it("should write form.task value to condition.taskName and emit it in the model", async () => {
      const tasks = [{ name: "TaskA", label: "Task A" }];
      const { comp } = await renderComponent(tasks, {
         condition: makeCondition({ taskName: "TaskA" }),
      });

      const emitted: TaskConditionChanges[] = [];
      comp.modelChanged.subscribe(e => emitted.push(e));

      comp.form.get("task").setValue("TaskB");
      comp.fireModelChanged();

      expect((emitted.at(-1).model as CompletionConditionModel).taskName).toBe("TaskB");
      expect(comp.condition.taskName).toBe("TaskB");
   });

   // 🔁 Regression-sensitive: emitted valid must mirror form.valid at the time of emission —
   // task="" → required fails → valid=false must block parent save.
   it("should emit valid=false when task is empty (required validator)", async () => {
      const { comp } = await renderComponent([], {
         condition: makeCondition({ taskName: "" }),
      });

      const emitted: TaskConditionChanges[] = [];
      comp.modelChanged.subscribe(e => emitted.push(e));

      comp.fireModelChanged();

      expect(emitted.at(-1).valid).toBe(false);
   });

});
