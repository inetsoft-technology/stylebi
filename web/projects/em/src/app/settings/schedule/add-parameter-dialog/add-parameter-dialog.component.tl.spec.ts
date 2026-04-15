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
 * AddParameterDialogComponent — Testing Library style
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3]  — fixTimeValue(): TIME_INSTANT seconds lost when value has no ":ss"
 *                        (it.failing — confirmed bug)
 *   Group 2 [Risk 3]  — ok() Add mode: confirmMessage gates close vs. stay-open for duplicates
 *   Group 3 [Risk 2]  — initForm() name validators: required, variableSpecialCharacters,
 *                        duplicateName (edit mode)
 *   Group 4 [Risk 2]  — updateArrayStatus(): EXPRESSION value disables / re-enables array checkbox
 *   Group 5 [Risk 2]  — array valueChanges + convertToArray(): split-on-toggle-OFF and click timing
 *                        (it.failing — confirmed bug)
 *   Group 6 [Risk 2]  — getValueValidator(): type-based validator selection
 *   Group 7 [Risk 2]  — getScriptTester(): HTTP test call + confirmation dialog integration
 *
 * Confirmed bugs (it.failing — remove wrapper once fixed):
 *
 *   Bug A — fixTimeValue() stale `val` reference (Group 1):
 *     First if-block writes `this.model.value.value = val + ":00"` using the captured `const val`.
 *     Second if-block then overwrites with `val.replace("T", " ")` — the same original `val` —
 *     erasing the ":00" appended in the first block.
 *     Result: timeInstant "2024-01-15T10:30" becomes "2024-01-15 10:30" instead of
 *     "2024-01-15 10:30:00". Backend may reject the timestamp missing seconds.
 *
 *   Bug B — convertToArray() inverted click timing (Group 5):
 *     (click) fires before mat-checkbox toggles, so `!form.controls["array"].value` is inverted:
 *     – Clicking to enable array  (OFF→ON): current value is false → `!false` = true
 *       → fixTimeValue() fires incorrectly.
 *     – Clicking to disable array (ON→OFF): current value is true  → `!true` = false
 *       → fixTimeValue() is skipped; time values reach ok() without seconds appended.
 *     Result: time fixup runs on the wrong transition edge; submitted values may be malformed.
 *
 * KEY contracts:
 *   - ok() Add mode: first OK with a duplicate name → sets confirmMessage, dialog stays open.
 *   - ok() Add mode: second OK (confirmMessage truthy) → replaces duplicate, closes dialog.
 *   - name valueChanges clears confirmMessage to "" (resets the two-step duplicate flow).
 *   - array is disabled iff model.value.type === EXPRESSION && model.value.value !== "".
 *   - getValueValidator() applies integerInRange + isInteger for INTEGER; isFloatNumber for DOUBLE;
 *     only Validators.required for non-array DATE/TIME/TIME_INSTANT.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { HttpClientModule } from "@angular/common/http";
import { ReactiveFormsModule } from "@angular/forms";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from "@angular/material/dialog";
import { MatAutocompleteModule } from "@angular/material/autocomplete";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { MatInputModule } from "@angular/material/input";
import { MatSelectModule } from "@angular/material/select";
import { MatFormFieldModule } from "@angular/material/form-field";
import { it } from "@jest/globals"; // must import to enable it.failing
import { render, waitFor } from "@testing-library/angular";
import { of } from "rxjs";
import { http, HttpResponse } from "msw";

import { server } from "../../../../../../../mocks/server";
import { AddParameterDialogComponent } from "./add-parameter-dialog.component";
import { AddParameterDialogModel } from "../../../../../../shared/schedule/model/add-parameter-dialog-model";
import { ValueTypes } from "../../../../../../portal/src/app/vsobjects/model/dynamic-value-model";
import { XSchema } from "../../../../../../portal/src/app/common/data/xschema";

// ─────────────────────────────────────────────────────────────────────────────
// Fixtures
// ─────────────────────────────────────────────────────────────────────────────

const makeParam = (
   name = "param1",
   type = XSchema.STRING,
   value = "val1",
   array = false,
): AddParameterDialogModel => ({
   name,
   type,
   value: { value, type: ValueTypes.VALUE, dataType: type },
   array,
});

// ─────────────────────────────────────────────────────────────────────────────
// Render helper
// ─────────────────────────────────────────────────────────────────────────────

interface RenderOpts {
   index?: number;
   parameters?: AddParameterDialogModel[];
   parameterNames?: string[];
   parameterType?: string;
   supportDynamic?: boolean;
   /** What MatDialog.afterClosed() emits for the script-tester confirmation dialog. */
   dialogClosesWith?: unknown;
}

async function renderComp(opts: RenderOpts = {}) {
   const dialogRefSpy = { close: jest.fn() };
   const matDialogSpy = {
      open: jest.fn().mockReturnValue({
         afterClosed: () =>
            of(opts.dialogClosesWith !== undefined ? opts.dialogClosesWith : true),
      }),
   };

   const result = await render(AddParameterDialogComponent, {
      imports: [
         HttpClientModule, ReactiveFormsModule, NoopAnimationsModule,
         MatAutocompleteModule, MatCheckboxModule, MatInputModule,
         MatSelectModule, MatFormFieldModule,
      ],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         { provide: MatDialogRef, useValue: dialogRefSpy },
         { provide: MatDialog, useValue: matDialogSpy },
         {
            provide: MAT_DIALOG_DATA,
            useValue: {
               index: opts.index ?? -1,
               parameters: opts.parameters ?? [],
               parameterNames: opts.parameterNames ?? [],
               parameterType: opts.parameterType ?? null,
               supportDynamic: opts.supportDynamic ?? false,
            },
         },
      ],
   });

   result.fixture.detectChanges();
   await result.fixture.whenStable();

   const comp = result.fixture.componentInstance as AddParameterDialogComponent;

   return { ...result, comp, dialogRefSpy, matDialogSpy };
}

// ════════════════════════════════════════════════════════════════════════════
// Group 1 [Risk 3] — fixTimeValue(): TIME_INSTANT seconds lost (Bug A)
// ════════════════════════════════════════════════════════════════════════════

describe("AddParameterDialogComponent — fixTimeValue(): time value formatting", () => {

   // Risk Point/Contract: TIME "HH:mm" → "HH:mm:00". Only one if-block runs for TIME,
   // so no stale-val issue. Verifies the seconds-append path in isolation.
   it("should append ':00' to a TIME value that is missing seconds", async () => {
      const { comp } = await renderComp({ index: -1, parameters: [] });
      comp.model.value.type = ValueTypes.VALUE;
      comp.model.type = XSchema.TIME;
      comp.model.value.value = "10:30";

      (comp as any).fixTimeValue();

      expect(comp.model.value.value).toBe("10:30:00");
   });

   // Risk Point/Contract: TIME_INSTANT with seconds already present ("HH:mm:ss") does NOT match
   // the timeInsPattern, so block 1 is skipped. Only block 2 runs (T→space).
   // Verifies the pattern guard prevents a double-append.
   it("should replace 'T' with ' ' for a TIME_INSTANT value that already has seconds", async () => {
      const { comp } = await renderComp({ index: -1, parameters: [] });
      comp.model.value.type = ValueTypes.VALUE;
      comp.model.type = XSchema.TIME_INSTANT;
      comp.model.value.value = "2024-01-15T10:30:45";

      (comp as any).fixTimeValue();

      expect(comp.model.value.value).toBe("2024-01-15 10:30:45");
   });

   // guard case: both if-blocks reference the original `const val`,
   // not the updated `this.model.value.value`. Block 1 appends ":00" to val, block 2
   // overwrites with `val.replace("T", " ")` — erasing the ":00".
   // Note: this is hard to verify in pure UI black-box flow because date-time editors may
   // normalize values and hide the stale-val overwrite. Keep this test as a code-level guard.
   // Risk Point/Contract: "2024-01-15T10:30" → must become "2024-01-15 10:30:00" (not "2024-01-15 10:30").
   // Why High Value: Silent data corruption — submitted timestamp is rejected by the backend
   // or silently stored with wrong seconds.
   it.failing("should produce 'YYYY-MM-DD HH:mm:00' for TIME_INSTANT missing seconds (Bug A — stale val)", async () => {
      const { comp } = await renderComp({ index: -1, parameters: [] });
      comp.model.value.type = ValueTypes.VALUE;
      comp.model.type = XSchema.TIME_INSTANT;
      comp.model.value.value = "2024-01-15T10:30";

      (comp as any).fixTimeValue();

      // Bug A: block 2 uses old `val` → "2024-01-15 10:30" (no ":00")
      expect(comp.model.value.value).toBe("2024-01-15 10:30:00");
   });

});

// ════════════════════════════════════════════════════════════════════════════
// Group 2 [Risk 3] — ok() Add mode: duplicate-name confirmMessage flow
// ════════════════════════════════════════════════════════════════════════════

describe("AddParameterDialogComponent — ok() Add mode: duplicate-name flow", () => {

   // Happy path: unique name → pushes, sorts, closes in one call.
   // Risk Point/Contract: no confirmMessage cycle needed for non-duplicate names.
   it("should push a new parameter and close the dialog immediately for a unique name", async () => {
      const { comp, dialogRefSpy } = await renderComp({
         index: -1,
         parameters: [makeParam("beta")],
      });
      comp.form.controls["name"].setValue("alpha");
      comp.form.controls["value"]?.setValue("v1");

      comp.ok();

      expect(dialogRefSpy.close).toHaveBeenCalledTimes(1);
      const result: AddParameterDialogModel[] = dialogRefSpy.close.mock.calls[0][0];
      expect(result.length).toBe(2);
      // sorted: alpha < beta
      expect(result[0].name).toBe("alpha");
      expect(result[1].name).toBe("beta");
   });

   // 🔁 Regression-sensitive: First OK with a duplicate name must NOT close the dialog.
   // If this guard is removed, the duplicate confirmation step is silently skipped and the
   // original parameter is overwritten without user awareness.
   // Risk Point/Contract: confirmMessage set to a non-empty warning; dialogRef.close NOT called.
   it("should set confirmMessage and NOT close the dialog on first ok() with a duplicate name", async () => {
      const { comp, dialogRefSpy } = await renderComp({
         index: -1,
         parameters: [makeParam("existing")],
      });
      comp.form.controls["name"].setValue("existing");
      comp.form.controls["value"]?.setValue("newVal");

      comp.ok();

      expect(comp.confirmMessage).toBeTruthy();
      expect(comp.confirmMessage).toContain("existing");
      expect(dialogRefSpy.close).not.toHaveBeenCalled();
   });

   // 🔁 Regression-sensitive: Second OK (confirmMessage truthy) must replace the duplicate entry
   // and close the dialog. Losing the confirmMessage guard on the replace branch means a second
   // ok() would push a NEW entry instead of replacing, silently duplicating the parameter.
   // Risk Point/Contract: parameters[duplicateIndex] updated with new model, sorted list returned.
   it("should replace the duplicate and close the dialog on second ok() when confirmMessage is set", async () => {
      const { comp, dialogRefSpy } = await renderComp({
         index: -1,
         parameters: [makeParam("existing", XSchema.STRING, "old")],
      });
      comp.form.controls["name"].setValue("existing");
      comp.form.controls["value"]?.setValue("newVal");

      comp.ok(); // first OK — sets confirmMessage
      comp.ok(); // second OK — replaces and closes

      expect(dialogRefSpy.close).toHaveBeenCalledTimes(1);
      const closed: AddParameterDialogModel[] = dialogRefSpy.close.mock.calls[0][0];
      const replaced = closed.find(p => p.name === "existing");
      expect(replaced).toBeDefined();
      expect(replaced!.value.value).toBe("newVal");
   });

   // 🔁 Regression-sensitive: Changing the name after the first duplicate warning must reset
   // confirmMessage to "". If not cleared, a subsequent ok() would use a stale copyIndex from
   // the FIRST duplicate search — potentially replacing the wrong parameter.
   // Risk Point/Contract: name valueChanges → confirmMessage becomes "".
   it("should clear confirmMessage when the name input changes after a duplicate warning", async () => {
      const { comp } = await renderComp({
         index: -1,
         parameters: [makeParam("existing")],
      });
      comp.form.controls["name"].setValue("existing");
      comp.form.controls["value"]?.setValue("v1");
      comp.ok(); // triggers confirmMessage
      expect(comp.confirmMessage).toBeTruthy();

      comp.form.controls["name"].setValue("differentName");

      expect(comp.confirmMessage).toBe("");
   });

});

// ════════════════════════════════════════════════════════════════════════════
// Group 3 [Risk 2] — initForm() name validators
// ════════════════════════════════════════════════════════════════════════════

describe("AddParameterDialogComponent — initForm() name validators", () => {

   // Risk Point/Contract: empty name → required error, form invalid → OK button disabled.
   it("should mark the name control invalid with 'required' error when value is empty", async () => {
      const { comp } = await renderComp({ index: -1, parameters: [] });

      comp.form.controls["name"].setValue("");
      comp.form.controls["name"].markAsTouched();

      expect(comp.form.controls["name"].errors?.["required"]).toBeTruthy();
      expect(comp.form.invalid).toBe(true);
   });

   // Risk Point/Contract: special chars (^!%) → variableSpecialCharacters error.
   // Why High Value: names with invalid chars reach the server and may cause 500s or key mismatches.
   it("should report variableSpecialCharacters error for a name containing '^!%'", async () => {
      const { comp } = await renderComp({ index: -1, parameters: [] });

      comp.form.controls["name"].setValue("^!%");
      comp.form.controls["name"].markAsTouched();

      expect(comp.form.controls["name"].errors?.["variableSpecialCharacters"]).toBeTruthy();
   });

   // 🔁 Regression-sensitive: In Edit mode the duplicateName validator excludes the param's own
   // name via modelName. If this capture is broken, keeping the original name shows a spurious
   // duplicate error and blocks the user from saving the parameter unchanged.
   // Risk Point/Contract: editing param at index 0 and keeping its own name → no duplicate error.
   it("should NOT report duplicateName when editing and keeping the parameter's own name", async () => {
      const { comp } = await renderComp({
         index: 0,
         parameters: [makeParam("existing"), makeParam("other")],
      });

      comp.form.controls["name"].setValue("existing");
      comp.form.controls["name"].updateValueAndValidity();

      expect(comp.form.controls["name"].errors?.["duplicateName"]).toBeFalsy();
   });

   // Risk Point/Contract: editing to use a different existing name → duplicateName error.
   it("should report duplicateName error when editing a parameter to use another existing name", async () => {
      const { comp } = await renderComp({
         index: 0,
         parameters: [makeParam("first"), makeParam("second")],
      });

      comp.form.controls["name"].setValue("second");
      comp.form.controls["name"].updateValueAndValidity();

      expect(comp.form.controls["name"].errors?.["duplicateName"]).toBeTruthy();
   });

});

// ════════════════════════════════════════════════════════════════════════════
// Group 4 [Risk 2] — updateArrayStatus(): EXPRESSION disables/re-enables array
// ════════════════════════════════════════════════════════════════════════════

describe("AddParameterDialogComponent — updateArrayStatus(): array checkbox state", () => {

   // Risk Point/Contract: array must be disabled when value.type == EXPRESSION && value.value != "".
   // Allowing array + expression together could produce malformed requests the server doesn't support.
   it("should disable the array control when value type is EXPRESSION with a non-empty expression", async () => {
      const { comp } = await renderComp({ index: -1, parameters: [] });

      comp.model.value.type = ValueTypes.EXPRESSION;
      comp.model.value.value = "=NOW()";
      (comp as any).updateArrayStatus();

      expect(comp.form.controls["array"].disabled).toBe(true);
   });

   // 🔁 Regression-sensitive: Clearing the expression must re-enable array. If updateArrayStatus()
   // is not called after clearing, the array checkbox stays locked and the user cannot set
   // array=true for subsequent VALUE-type entries.
   // Risk Point/Contract: EXPRESSION with empty value → array re-enabled.
   it("should re-enable the array control when the expression value is cleared to empty string", async () => {
      const { comp } = await renderComp({ index: -1, parameters: [] });

      comp.model.value.type = ValueTypes.EXPRESSION;
      comp.model.value.value = "=NOW()";
      (comp as any).updateArrayStatus();
      expect(comp.form.controls["array"].disabled).toBe(true); // confirm disabled first

      comp.model.value.value = "";
      (comp as any).updateArrayStatus();

      expect(comp.form.controls["array"].disabled).toBe(false);
   });

   // Boundary: VALUE type must never disable array, regardless of the value content.
   it("should keep the array control enabled when value type is VALUE", async () => {
      const { comp } = await renderComp({ index: -1, parameters: [] });

      comp.model.value.type = ValueTypes.VALUE;
      comp.model.value.value = "someNonEmptyValue";
      (comp as any).updateArrayStatus();

      expect(comp.form.controls["array"].disabled).toBe(false);
   });

});

// ════════════════════════════════════════════════════════════════════════════
// Group 5 [Risk 2] — array valueChanges + convertToArray() click timing
// ════════════════════════════════════════════════════════════════════════════

describe("AddParameterDialogComponent — array toggle: value split and click timing", () => {

   // Risk Point/Contract: Turning OFF array for a non-string type takes only the first
   // comma-separated token. Keeping "1,2,3" for INTEGER would cause a type-validation error
   // on the single-value field.
   // Why High Value: The split relies on stale form validity — if the validators were applied in
   // split mode while array was ON, the form is VALID and the split first-element is used.
   it("should keep only the first comma-separated element when array is turned OFF for INTEGER type", async () => {
      const { comp } = await renderComp({ index: -1, parameters: [] });

      // Set INTEGER type (also sets model.value.dataType = "integer" via subscription)
      comp.form.controls["type"].setValue(XSchema.INTEGER);
      // Enable array mode, then enter a comma-separated value (valid in split mode)
      comp.form.controls["array"].setValue(true);
      comp.form.controls["value"]?.setValue("1,2,3"); // VALID with isInteger(split=true)

      // Toggle array OFF — triggers the split logic in valueChanges with a setTimeout
      comp.form.controls["array"].setValue(false);
      // Wait for the component's internal setTimeout(0) callback to flush.
      // Use a short timeout so hangs fail fast while still allowing CI scheduling jitter.
      await waitFor(() => expect(comp.model.value.value).toBe("1"), { timeout: 500 });
   });

   // Risk Point/Contract: For STRING type the split is skipped entirely (dataType === STRING).
   // Splitting "a,b,c" by comma would corrupt a legitimate comma-containing string parameter.
   it("should preserve the full value without splitting when array is turned OFF for STRING type", async () => {
      const { comp } = await renderComp({ index: -1, parameters: [] });

      comp.form.controls["type"].setValue(XSchema.STRING);
      comp.form.controls["array"].setValue(true);
      comp.form.controls["value"]?.setValue("a,b,c"); // VALID (required only)

      comp.form.controls["array"].setValue(false);
      // Same reason as above: this assertion depends on async setTimeout(0) inside valueChanges.
      await waitFor(() => expect(comp.model.value.value).toBe("a,b,c"), { timeout: 500 });
   });

   // guard case: convertToArray() uses (click) which fires BEFORE
   // mat-checkbox toggles. When disabling array (ON→OFF), !form.controls["array"].value
   // = !true = false → fixTimeValue() is skipped at click-time.
   // Note: this timing bug is hard to observe in pure UI black-box flow because ok() also
   // calls fixTimeValue(), which can mask the missed conversion from convertToArray().
   // Risk Point/Contract: disabling array for TIME type must call fixTimeValue() to append ":00".
   // Why High Value: submitted TIME "10:30" (missing seconds) may fail backend parsing silently.
   it.failing("should call fixTimeValue() when disabling array for TIME type (Bug B — inverted click timing)", async () => {
      const { comp } = await renderComp({ index: -1, parameters: [] });

      comp.model.type = XSchema.TIME;
      comp.model.value.type = ValueTypes.VALUE;
      comp.model.value.value = "10:30";
      // Simulate array currently enabled — the click will try to disable it
      comp.form.controls["array"].setValue(true, { emitEvent: false });

      // (click) fires BEFORE the checkbox value toggles; array is still true at this point
      comp.convertToArray();

      // Expected: fixTimeValue() ran → seconds appended
      // Actual: !true = false → fixTimeValue() skipped
      expect(comp.model.value.value).toBe("10:30:00");
   });

});

// ════════════════════════════════════════════════════════════════════════════
// Group 6 [Risk 2] — getValueValidator(): type-based validator set
// ════════════════════════════════════════════════════════════════════════════

describe("AddParameterDialogComponent — getValueValidator(): type-based validators", () => {

   // Risk Point/Contract: INTEGER must reject non-integer and out-of-range values.
   // Behavior assertions are more robust than validator-count assertions.
   it("should enforce integer validators for non-array INTEGER", async () => {
      const { comp } = await renderComp({ index: -1, parameters: [] });
      comp.model.type = XSchema.INTEGER;
      comp.model.array = false;
      comp.model.value.type = ValueTypes.VALUE;

      const valueCtrl = comp.form.controls["value"];
      valueCtrl.setValidators((comp as any).getValueValidator());

      valueCtrl.setValue("abc");
      valueCtrl.updateValueAndValidity();
      expect(valueCtrl.hasError("isInteger")).toBe(true);
      expect(valueCtrl.hasError("integerInRange")).toBe(true);
   });

   // Risk Point/Contract: DOUBLE must reject non-numeric strings.
   it("should enforce float validator for non-array DOUBLE", async () => {
      const { comp } = await renderComp({ index: -1, parameters: [] });
      comp.model.type = XSchema.DOUBLE;
      comp.model.array = false;
      comp.model.value.type = ValueTypes.VALUE;

      const valueCtrl = comp.form.controls["value"];
      valueCtrl.setValidators((comp as any).getValueValidator());

      valueCtrl.setValue("abc");
      valueCtrl.updateValueAndValidity();
      expect(valueCtrl.hasError("isFloatNumber")).toBe(true);
   });

   // Boundary: non-array DATE gets required only; date format is handled by child control.
   it("should only require value for non-array DATE type", async () => {
      const { comp } = await renderComp({ index: -1, parameters: [] });
      comp.model.type = XSchema.DATE;
      comp.model.array = false;
      comp.model.value.type = ValueTypes.VALUE;

      const valueCtrl = comp.form.controls["value"];
      valueCtrl.setValidators((comp as any).getValueValidator());

      valueCtrl.setValue("");
      valueCtrl.updateValueAndValidity();
      expect(valueCtrl.hasError("required")).toBe(true);

      valueCtrl.setValue("not-a-date");
      valueCtrl.updateValueAndValidity();
      expect(valueCtrl.hasError("required")).toBe(false);
      expect(valueCtrl.hasError("isDate")).toBe(false);
   });

});

// ════════════════════════════════════════════════════════════════════════════
// Group 7 [Risk 2] — getScriptTester(): HTTP + confirmation dialog
// ════════════════════════════════════════════════════════════════════════════

describe("AddParameterDialogComponent — getScriptTester(): HTTP + dialog integration", () => {

   const TEST_SCRIPT_MSW = "*/api/em/schedule/parameters/formula/test-script";

   // Risk Point/Contract: null or empty expression returns Promise.resolve(true) immediately
   // without making an HTTP call. Removing this guard would send spurious POST requests for
   // empty expressions and may show unexpected confirmation dialogs.
   it("should resolve true immediately without an HTTP call for an empty expression", async () => {
      const { comp, matDialogSpy } = await renderComp({ index: -1, parameters: [] });
      const tester = comp.getScriptTester();

      const result = await tester({ expression: "" } as any);

      expect(result).toBe(true);
      expect(matDialogSpy.open).not.toHaveBeenCalled();
   });

   // 🔁 Regression-sensitive: Non-null API response must open a MessageDialog so the user can
   // review the script output and decide whether to continue. Removing the `!!result` check
   // silently ignores the server's test output.
   // Risk Point/Contract: result non-null → dialog.open() called with result in content.
   it("should open a confirmation dialog containing the API result when POST returns a non-null message", async () => {
      server.use(
         http.post(TEST_SCRIPT_MSW, () => HttpResponse.json("Script error: division by zero")),
      );

      const { comp, matDialogSpy } = await renderComp({
         index: -1,
         parameters: [],
         dialogClosesWith: true,
      });
      const tester = comp.getScriptTester();

      await tester({ expression: "=1/0" } as any);

      expect(matDialogSpy.open).toHaveBeenCalledTimes(1);
      const [, config] = matDialogSpy.open.mock.calls[0];
      expect(config.data.content).toContain("Script error: division by zero");
   });

   // Risk Point/Contract: null API result (script ran without output) → no dialog opened,
   // tester resolves true. Popping a dialog for a clean run would confuse users.
   it("should resolve true without opening a dialog when POST returns null", async () => {
      server.use(
         http.post(TEST_SCRIPT_MSW, () => HttpResponse.json(null)),
      );

      const { comp, matDialogSpy } = await renderComp({ index: -1, parameters: [] });
      const tester = comp.getScriptTester();

      const result = await tester({ expression: "=validScript()" } as any);

      expect(matDialogSpy.open).not.toHaveBeenCalled();
      expect(result).toBe(true);
   });

});
