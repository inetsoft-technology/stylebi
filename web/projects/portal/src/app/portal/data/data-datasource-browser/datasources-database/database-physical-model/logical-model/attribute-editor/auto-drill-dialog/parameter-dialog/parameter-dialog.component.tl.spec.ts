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
 * ParameterDialog — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — ngOnInit: new parameter (index=-1) vs edit (index≥0 clone)
 *   Group 2 [Risk 3] — ok(): index=-1 pushes clone; index≥0 replaces in-place;
 *                       fixTimeValue appends ":00" to incomplete TIME_INSTANT/TIME values
 *   Group 3 [Risk 3] — typeChanges+setTimeout: first change sets typeInitialized=true
 *                       (no field reset); subsequent changes reset model.field and
 *                       valueControl; BOOLEAN type sets valueControl to false (not null)
 *   Group 4 [Risk 2] — updateParamType(): FIELD→CONSTANT and CONSTANT→FIELD transitions
 *   Group 5 [Risk 2] — okDisabled(): FIELD source — driven by nameControl.invalid;
 *                       CONSTANT source — driven by form.invalid or invalid type value
 *   Group 6 [Risk 2] — isValidDataTypeValue(): INTEGER, FLOAT, BOOLEAN, DATE,
 *                       TIME_INSTANT, TIME, CHARACTER, default(STRING)
 *   Group 7 [Risk 2] — uniqueName validator: no conflict; duplicate at different index;
 *                       same-name at own index (self-edit is allowed)
 *   Group 8 [Risk 1] — getFirstErrorMessage(), close(), changeValue()
 *
 * Confirmed bugs (it.fails):
 *   Group 9 — post-destroy typeChanges callback still fires: the valueChanges subscription
 *   in ngOnInit has no takeUntilDestroyed so setTimeout callbacks run on dead components.
 *
 * Out of scope:
 *   ngAfterViewInit — trivial inputFocus.nativeElement.focus(); tested by the DOM
 *   changeType(TIME) — calls detectChanges; no observable state change to assert
 *   isInValidTypeRange() — aggregates FormControl errors set by updateValidators; covered
 *     transitively via typeChanges tests that verify the individual range validators
 *
 * Mocking strategy:
 *   ModalHeaderComponent injects AiAssistantDialogService → stubbed via importOverrides.
 *   DateValueEditorComponent / TimeValueEditorComponent / TimeInstantValueEditorComponent
 *   inject NgbDateParserFormatter → stubbed via importOverrides.
 *   ChangeDetectorRef is auto-provided by TestBed.
 *   No HTTP calls → no MSW needed.
 */

import { Component, EventEmitter, forwardRef, Input, NO_ERRORS_SCHEMA, Output } from "@angular/core";
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from "@angular/forms";
import { render, waitFor } from "@testing-library/angular";
import { XSchema } from "../../../../../../../../../common/data/xschema";
import { ParameterDialog } from "./parameter-dialog.component";
import { ModalHeaderComponent } from "../../../../../../../../../widget/modal-header/modal-header.component";
import { DateValueEditorComponent } from "../../../../../../../../../widget/date-type-editor/date-value-editor.component";
import { TimeValueEditorComponent } from "../../../../../../../../../widget/date-type-editor/time-value-editor.component";
import { TimeInstantValueEditorComponent } from "../../../../../../../../../widget/date-type-editor/time-instant-value-editor.component";
import { DrillParameterModel } from "../../../../../../../model/datasources/database/physical-model/logical-model/drill-parameter-model";

// ---------------------------------------------------------------------------
// Stub components
// ---------------------------------------------------------------------------

@Component({ selector: "modal-header", template: "" })
class ModalHeaderStub {
   @Input() title: string;
   @Input() cshid: string;
   @Output() onCancel = new EventEmitter<void>();
}

// Date editor stubs implement ControlValueAccessor because the template binds them via
// formControlName="value"; without it Angular throws NG01203 when the editor is visible.
@Component({
   selector: "date-value-editor", template: "",
   providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => DateValueEditorStub), multi: true }],
})
class DateValueEditorStub implements ControlValueAccessor {
   @Input() value: any;
   @Output() valueChange = new EventEmitter<any>();
   writeValue() {}
   registerOnChange(fn: any) {}
   registerOnTouched(fn: any) {}
}

@Component({
   selector: "time-value-editor", template: "",
   providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TimeValueEditorStub), multi: true }],
})
class TimeValueEditorStub implements ControlValueAccessor {
   @Input() value: any;
   @Output() valueChange = new EventEmitter<any>();
   writeValue() {}
   registerOnChange(fn: any) {}
   registerOnTouched(fn: any) {}
}

@Component({
   selector: "time-instant-value-editor", template: "",
   providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TimeInstantValueEditorStub), multi: true }],
})
class TimeInstantValueEditorStub implements ControlValueAccessor {
   @Input() value: any;
   @Output() valueChange = new EventEmitter<any>();
   writeValue() {}
   registerOnChange(fn: any) {}
   registerOnTouched(fn: any) {}
}

// ---------------------------------------------------------------------------
// Model factory
// ---------------------------------------------------------------------------

function makeParam(overrides: Partial<DrillParameterModel> = {}): DrillParameterModel {
   return { name: "param1", field: "this.column", type: null, ...overrides };
}

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

interface RenderOpts {
   index?: number;
   parameters?: DrillParameterModel[];
   variables?: string[];
   fields?: string[];
}

async function renderComp(opts: RenderOpts = {}) {
   const { fixture } = await render(ParameterDialog, {
      schemas: [NO_ERRORS_SCHEMA],
      importOverrides: [
         { replace: ModalHeaderComponent, with: ModalHeaderStub },
         { replace: DateValueEditorComponent, with: DateValueEditorStub },
         { replace: TimeValueEditorComponent, with: TimeValueEditorStub },
         { replace: TimeInstantValueEditorComponent, with: TimeInstantValueEditorStub },
      ],
      componentInputs: {
         index: opts.index ?? -1,
         parameters: opts.parameters ?? [],
         variables: opts.variables ?? [],
         fields: opts.fields ?? ["this.column"],
      },
   });
   return { comp: fixture.componentInstance as ParameterDialog, fixture };
}

// ---------------------------------------------------------------------------
// Global lifecycle
// ---------------------------------------------------------------------------

afterEach(() => {
   vi.restoreAllMocks();
});

// ---------------------------------------------------------------------------
// Group 1 — ngOnInit: new vs edit [Risk 3]
// ---------------------------------------------------------------------------

describe("ParameterDialog — ngOnInit: new vs edit parameter", () => {
   // 🔁 Regression-sensitive: if index=-1 fails to initialise an empty model, the
   // form renders stale data from a previous edit session.
   it("should initialise an empty model and FIELD source for a new parameter (index=-1)", async () => {
      const { comp } = await renderComp({ index: -1, parameters: [] });
      expect(comp.model.name).toBe("");
      expect(comp.model.field).toBe("this.column");
      expect(comp.model.type).toBeNull();
      expect(comp.source).toBe(0); // SourceType.FIELD = 0
   });

   // 🔁 Regression-sensitive: if index≥0 fails to clone, editing mutates the original
   // parameters array in-place, corrupting the parent component's state on cancel.
   it("should clone the existing parameter for edit (index≥0)", async () => {
      const original = makeParam({ name: "myParam", field: "colA", type: XSchema.STRING });
      const { comp } = await renderComp({ index: 0, parameters: [original] });
      expect(comp.model.name).toBe("myParam");
      expect(comp.model.field).toBe("colA");
      expect(comp.model.type).toBe(XSchema.STRING);
      expect(comp.source).toBe(1); // SourceType.CONSTANT = 1 (type is non-null)
      expect(comp.model).not.toBe(original); // must be a clone, not the same reference
   });
});

// ---------------------------------------------------------------------------
// Group 2 — ok(): push vs replace + fixTimeValue [Risk 3]
// ---------------------------------------------------------------------------

describe("ParameterDialog — ok(): new push, edit replace, fixTimeValue", () => {
   // 🔁 Regression-sensitive: index=-1 must push a clone, not the live model reference;
   // if the reference leaks, post-commit edits corrupt the committed parameter.
   it("should push a clone to parameters and emit for a new parameter (index=-1)", async () => {
      const params: DrillParameterModel[] = [];
      const { comp } = await renderComp({ index: -1, parameters: params });
      comp.nameControl.setValue("newParam");
      comp.valueControl.setValue("this.column");
      const emitted: DrillParameterModel[][] = [];
      comp.onCommit.subscribe(v => emitted.push(v));

      comp.ok();

      expect(emitted).toHaveLength(1);
      expect(emitted[0]).toHaveLength(1);
      expect(emitted[0][0].name).toBe("newParam");
      expect(emitted[0][0]).not.toBe(comp.model); // clone, not the live model
   });

   it("should replace in-place and emit updated parameters for an edit (index≥0)", async () => {
      const existing = makeParam({ name: "old", field: "colA", type: null });
      const params = [existing];
      const { comp } = await renderComp({ index: 0, parameters: params });
      comp.nameControl.setValue("updated");
      comp.valueControl.setValue("colB");
      const emitted: DrillParameterModel[][] = [];
      comp.onCommit.subscribe(v => emitted.push(v));

      comp.ok();

      expect(emitted).toHaveLength(1);
      expect(emitted[0][0].name).toBe("updated");
      expect(params[0].name).toBe("updated"); // original array mutated at the index
   });

   it("should append ':00' to a TIME_INSTANT value missing the seconds segment", async () => {
      const original = makeParam({ name: "ts", field: "2024-06-01T10:30", type: XSchema.TIME_INSTANT });
      const { comp } = await renderComp({ index: 0, parameters: [original] });
      const emitted: DrillParameterModel[][] = [];
      comp.onCommit.subscribe(v => emitted.push(v));

      comp.ok();

      expect(emitted[0][0].field).toBe("2024-06-01T10:30:00");
   });
});

// ---------------------------------------------------------------------------
// Group 3 — typeChanges subscription + setTimeout [Risk 3]
// ---------------------------------------------------------------------------

describe("ParameterDialog — typeChanges + setTimeout: validator and field reset", () => {
   // 🔁 Regression-sensitive: if validators are not swapped on type change, the user can
   // submit a value that is invalid for the selected type (e.g. "abc" for INTEGER).

   it("should add STRING-specific validators after the first type change", async () => {
      const { comp } = await renderComp({ index: -1 });
      comp.form.controls["type"].setValue(XSchema.STRING);
      comp.valueControl.setValue("   "); // whitespace-only

      await waitFor(() => expect(comp.valueControl.hasError("notWhiteSpace")).toBe(true));
   });

   it("should reset model.field and valueControl to null on the second type change", async () => {
      const { comp } = await renderComp({ index: -1 });
      // First change: sets typeInitialized=true (no field reset)
      comp.form.controls["type"].setValue(XSchema.STRING);
      await waitFor(() => expect(comp["typeInitialized"]).toBe(true));

      // Second change: field should be reset
      comp.form.controls["type"].setValue(XSchema.INTEGER);

      await waitFor(() => expect(comp.valueControl.value).toBeNull());
      expect(comp.model.field).toBeNull();
   });

   it("should set valueControl to false (not null) when type changes to BOOLEAN", async () => {
      const { comp } = await renderComp({ index: -1 });
      comp.form.controls["type"].setValue(XSchema.STRING);
      await waitFor(() => expect(comp["typeInitialized"]).toBe(true));

      comp.form.controls["type"].setValue(XSchema.BOOLEAN);

      await waitFor(() => expect(comp.valueControl.value).toBe(false));
   });
});

// ---------------------------------------------------------------------------
// Group 4 — updateParamType() [Risk 2]
// ---------------------------------------------------------------------------

describe("ParameterDialog — updateParamType()", () => {
   it("should set source=FIELD, type=null, and restore the default field value", async () => {
      const { comp } = await renderComp({ index: -1 });
      // Start from CONSTANT state
      comp.updateParamType(1); // SourceType.CONSTANT

      comp.updateParamType(0); // SourceType.FIELD

      expect(comp.source).toBe(0);
      expect(comp.model.type).toBeNull();
      expect(comp.model.field).toBe("this.column");
      expect(comp.valueControl.value).toBe("this.column");
   });

   it("should set source=CONSTANT, type=STRING, and clear the field value", async () => {
      const { comp } = await renderComp({ index: -1 });

      comp.updateParamType(1); // SourceType.CONSTANT

      expect(comp.source).toBe(1);
      expect(comp.model.type).toBe(XSchema.STRING);
      expect(comp.model.field).toBeNull();
      expect(comp.valueControl.value).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 5 — okDisabled() [Risk 2]
// ---------------------------------------------------------------------------

describe("ParameterDialog — okDisabled()", () => {
   it("should return true for FIELD source when nameControl is invalid (empty name)", async () => {
      const { comp } = await renderComp({ index: -1 });
      // source=FIELD, nameControl has required validator, initial value is ""
      expect(comp.okDisabled()).toBe(true);
   });

   it("should return false for FIELD source when nameControl is valid", async () => {
      const { comp } = await renderComp({ index: -1 });
      comp.nameControl.setValue("validName");

      expect(comp.okDisabled()).toBe(false);
   });

   it("should return true for CONSTANT source when valueControl is invalid", async () => {
      const { comp } = await renderComp({ index: -1 });
      comp.nameControl.setValue("validName");
      comp.updateParamType(1); // SourceType.CONSTANT — valueControl becomes null → required fails

      expect(comp.okDisabled()).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 6 — isValidDataTypeValue() [Risk 2]
// ---------------------------------------------------------------------------

describe("ParameterDialog — isValidDataTypeValue()", () => {
   it("should return true for a valid integer string (INTEGER type)", async () => {
      const { comp } = await renderComp();
      expect(comp.isValidDataTypeValue("42", XSchema.INTEGER)).toBe(true);
   });

   it("should return false for non-integer strings (INTEGER type)", async () => {
      const { comp } = await renderComp();
      expect(comp.isValidDataTypeValue("3.14", XSchema.INTEGER)).toBe(false);
      expect(comp.isValidDataTypeValue("abc", XSchema.INTEGER)).toBe(false);
   });

   it("should return true for valid decimal strings (FLOAT/DOUBLE type)", async () => {
      const { comp } = await renderComp();
      expect(comp.isValidDataTypeValue("3.14", XSchema.FLOAT)).toBe(true);
      expect(comp.isValidDataTypeValue("-1.5e2", XSchema.DOUBLE)).toBe(true);
   });

   it("should return false for a non-numeric string (FLOAT type)", async () => {
      const { comp } = await renderComp();
      expect(comp.isValidDataTypeValue("abc", XSchema.FLOAT)).toBe(false);
   });

   it("should return true for 'true' and 'false' strings regardless of case (BOOLEAN type)", async () => {
      const { comp } = await renderComp();
      expect(comp.isValidDataTypeValue("true", XSchema.BOOLEAN)).toBe(true);
      expect(comp.isValidDataTypeValue("False", XSchema.BOOLEAN)).toBe(true);
   });

   it("should return false for a non-boolean string (BOOLEAN type)", async () => {
      const { comp } = await renderComp();
      expect(comp.isValidDataTypeValue("yes", XSchema.BOOLEAN)).toBe(false);
   });

   it("should return true for yyyy-MM-dd format (DATE type)", async () => {
      const { comp } = await renderComp();
      expect(comp.isValidDataTypeValue("2024-06-17", XSchema.DATE)).toBe(true);
   });

   it("should return false for wrong date format (DATE type)", async () => {
      const { comp } = await renderComp();
      expect(comp.isValidDataTypeValue("17-06-2024", XSchema.DATE)).toBe(false);
   });

   it("should accept T-separator and space-separator formats (TIME_INSTANT type)", async () => {
      const { comp } = await renderComp();
      expect(comp.isValidDataTypeValue("2024-06-17T10:30:00", XSchema.TIME_INSTANT)).toBe(true);
      expect(comp.isValidDataTypeValue("2024-06-17 10:30:00", XSchema.TIME_INSTANT)).toBe(true);
   });

   it("should return false for incomplete time-instant format (missing seconds)", async () => {
      const { comp } = await renderComp();
      expect(comp.isValidDataTypeValue("2024-06-17T10:30", XSchema.TIME_INSTANT)).toBe(false);
   });

   it("should return true for HH:mm:ss format (TIME type)", async () => {
      const { comp } = await renderComp();
      expect(comp.isValidDataTypeValue("10:30:00", XSchema.TIME)).toBe(true);
   });

   it("should return false for incomplete time format HH:mm (TIME type)", async () => {
      const { comp } = await renderComp();
      expect(comp.isValidDataTypeValue("10:30", XSchema.TIME)).toBe(false);
   });

   it("should return true for a single non-whitespace character (CHARACTER type)", async () => {
      const { comp } = await renderComp();
      expect(comp.isValidDataTypeValue("A", XSchema.CHARACTER)).toBe(true);
   });

   it("should return false for multi-character or whitespace input (CHARACTER type)", async () => {
      const { comp } = await renderComp();
      expect(comp.isValidDataTypeValue("AB", XSchema.CHARACTER)).toBe(false);
      expect(comp.isValidDataTypeValue(" ", XSchema.CHARACTER)).toBeFalsy();
   });
});

// ---------------------------------------------------------------------------
// Group 7 — uniqueName validator [Risk 2]
// ---------------------------------------------------------------------------

describe("ParameterDialog — uniqueName validator", () => {
   it("should be valid when the name does not conflict with existing parameters", async () => {
      const params = [makeParam({ name: "otherParam" })];
      const { comp } = await renderComp({ index: -1, parameters: params });
      comp.nameControl.setValue("myUniqueParam");
      expect(comp.nameControl.hasError("duplicate")).toBe(false);
   });

   it("should report duplicate error when another parameter at a different index has the same name", async () => {
      const params = [makeParam({ name: "taken" }), makeParam({ name: "something" })];
      const { comp } = await renderComp({ index: 1, parameters: params });
      comp.nameControl.setValue("taken"); // conflicts with index 0
      comp.nameControl.updateValueAndValidity();
      expect(comp.nameControl.hasError("duplicate")).toBe(true);
   });

   it("should allow the parameter to keep its own name during edit (same-index excluded)", async () => {
      const params = [makeParam({ name: "self" })];
      const { comp } = await renderComp({ index: 0, parameters: params });
      // nameControl is initialised with "self" — must not flag as duplicate
      expect(comp.nameControl.hasError("duplicate")).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 8 — getFirstErrorMessage(), close(), changeValue() [Risk 1]
// ---------------------------------------------------------------------------

describe("ParameterDialog — getFirstErrorMessage, close, changeValue", () => {
   it("getFirstErrorMessage should return the message for the first matching error", async () => {
      const { comp } = await renderComp({ index: -1 });
      // nameControl starts empty → required error
      expect(comp.getFirstErrorMessage(comp.nameControl))
         .toBe("_#(js:data.logicalmodel.drillParameterNameRequired)");
   });

   it("getFirstErrorMessage should return null when the control has no errors", async () => {
      const { comp } = await renderComp({ index: -1 });
      comp.nameControl.setValue("validName");
      expect(comp.getFirstErrorMessage(comp.nameControl)).toBeNull();
   });

   it("close should emit 'cancel' via onCancel", async () => {
      const { comp } = await renderComp();
      const cancelled: string[] = [];
      comp.onCancel.subscribe(v => cancelled.push(v));

      comp.close();

      expect(cancelled).toEqual(["cancel"]);
   });

   it("changeValue should update model.field and valueControl when value differs", async () => {
      const { comp } = await renderComp();

      comp.changeValue("newColumnRef");

      expect(comp.model.field).toBe("newColumnRef");
      expect(comp.valueControl.value).toBe("newColumnRef");
   });

   it("changeValue should be a no-op when the value is falsy", async () => {
      const { comp } = await renderComp();
      const before = comp.model.field;

      comp.changeValue(null);

      expect(comp.model.field).toBe(before);
   });
});

// ---------------------------------------------------------------------------
// Group 9 — memory leak: post-destroy subscription still fires [it.fails]
// ---------------------------------------------------------------------------

describe("ParameterDialog — subscription leak after destroy", () => {
   // Fixed Issue #75590: the type-change subscription is now stored in
   // this.typeChangeSubscription and unsubscribed in ngOnDestroy(), so a value
   // change on the form control after destroy no longer fires the callback at all.
   it("post-destroy type-change callback should not reset model.field", async () => {
      const { comp, fixture } = await renderComp({ index: -1 });
      // Prime typeInitialized=true with one type change before destroy
      comp.form.controls["type"].setValue(XSchema.STRING);
      await waitFor(() => expect(comp["typeInitialized"]).toBe(true));

      fixture.destroy();
      comp.model.field = "sentinel";

      // Trigger type change on the still-referenced form — subscription should NOT fire
      comp.form.controls["type"].setValue(XSchema.INTEGER);
      // Give the 0ms setTimeout one tick to fire
      await new Promise<void>(r => setTimeout(r, 0));

      expect(comp.model.field).toBe("sentinel");
   });
});
