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
 * InputParameterDialog — Pass 1: Interaction
 *
 * Direct instantiation (see test-helpers.ts) — two constructor dependencies
 * (NgbDateParserFormatter, ChangeDetectorRef), neither with `inject()` calls.
 *
 * Scope (per prescan Pass 1 method list): ngOnInit (form wiring + all valueChanges
 * subscriptions), ok(), close(), changeValueSource(), changeName(), model setter
 * (initial branch guards).
 *
 * Risk-first coverage:
 *   Group 2 [Risk 3] — ngOnInit: default-model construction, valueSource reset guard,
 *                       and every one of the 5 valueChanges subscriptions actually firing
 *   Group 3 [Risk 2] — ok(): the field-fallback re-resolution logic + CHARACTER truncation
 *   Group 5 [Risk 2] — changeValueSource: constant vs field transitions
 *   Group 1 [Risk 2] — model setter: DATE/TIME/TIME_INSTANT parsing branches
 *   Remaining groups [Risk 1] — single-purpose emitters/setters
 *
 * Confirmed bugs (it.fails): none
 *
 * Out of scope this pass (covered in
 * input-parameter-dialog.component.risk/display.tl.spec.ts):
 *   timeValue debounce race/dedup, concurrent updateDate/updateTime/updateDateTime
 *   sequencing, model setter side effects when form not yet initialized — Pass 2.
 *   changeType/updateValue/changeValidators/updateDateTime dispatch branches, isInvalid/
 *   isFormInvalid/isGrayedOut/hasViewsheetParameters/invalidDate — Pass 3.
 */

import { XSchema } from "../../common/data/xschema";
import { DateTypeFormatter } from "../../../../../shared/util/date-type-formatter";
import { createComponent, makeField, makeModel } from "./input-parameter-dialog.component.test-helpers";

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: model setter [Risk 2]
// ---------------------------------------------------------------------------

describe("InputParameterDialog — model setter", () => {
   it("should clone the assigned model rather than keep the same reference", () => {
      const { comp } = createComponent();
      const model = makeModel();

      comp.model = model;

      expect(comp.model).toEqual(model);
      expect(comp.model).not.toBe(model);
   });

   it("should force the type to STRING when the value source is 'field'", () => {
      const { comp } = createComponent();
      comp.model = makeModel({ valueSource: "field", type: XSchema.INTEGER });
      expect(comp.model.type).toBe(XSchema.STRING);
   });

   it("should parse a DATE value into the date struct and clear the time", () => {
      const { comp, ngbDateParserFormatter } = createComponent();
      comp.time = "10:00:00";

      comp.model = makeModel({ type: XSchema.DATE, value: "2024-03-15" });

      expect(ngbDateParserFormatter.parse).toHaveBeenCalledWith("2024-03-15");
      expect(comp.date).toEqual({ year: 2024, month: 3, day: 15 });
      expect(comp.time).toBeNull();
   });

   it("should store a TIME value directly and clear the date", () => {
      const { comp } = createComponent();
      comp.date = { year: 2024, month: 1, day: 1 };

      comp.model = makeModel({ type: XSchema.TIME, value: "10:30:00" });

      expect(comp.time).toBe("10:30:00");
      expect(comp.date).toBeNull();
   });

   it("should split a TIME_INSTANT value into its date and time parts", () => {
      const { comp } = createComponent();
      const instant = "2024-03-15T10:30:00";

      comp.model = makeModel({ type: XSchema.TIME_INSTANT, value: instant });

      const expectedDateStr = DateTypeFormatter.transformValue(
         instant, DateTypeFormatter.ISO_8601_TIME_INSTANT_FORMAT, DateTypeFormatter.ISO_8601_DATE_FORMAT);
      const expectedTime = DateTypeFormatter.transformValue(
         instant, DateTypeFormatter.ISO_8601_TIME_INSTANT_FORMAT, DateTypeFormatter.ISO_8601_TIME_FORMAT);

      expect(comp.date).toEqual({ year: 2024, month: 3, day: 15 });
      expect(comp.time).toBe(expectedTime);
      expect(expectedDateStr).toBe("2024-03-15");
   });

   it("should sync the name and type form controls when the form already exists", () => {
      const { comp } = createComponent();
      comp.ngOnInit();

      comp.model = makeModel({ name: "newName", type: XSchema.INTEGER, value: "42" });

      expect(comp.form.controls["name"].value).toBe("newName");
      expect(comp.form.controls["type"].value).toBe(XSchema.INTEGER);
   });

   // Bug: the setter syncs the "type" control before assigning `this.alphaNumericValue =
   // this.model.value`. Setting "type" fires its own ngOnInit subscription, which calls
   // changeType() — and for numeric/STRING/CHARACTER types, changeType() resets
   // alphaNumericValue to "". That reset cascades back through the alphaNumericValue
   // control's own subscription into updateValue(""), which overwrites model.value with ""
   // — silently discarding the value the caller just assigned via the model setter, any
   // time the assigned model changes both `type` and `value` together.
   it.fails("should sync the alphaNumericValue form control to the new model's value when the form already exists", () => {
      const { comp } = createComponent();
      comp.ngOnInit();

      comp.model = makeModel({ name: "newName", type: XSchema.INTEGER, value: "42" });

      expect(comp.form.controls["alphaNumericValue"].value).toBe("42");
   });
});

// ---------------------------------------------------------------------------
// Group 2: ngOnInit [Risk 3]
// ---------------------------------------------------------------------------

describe("InputParameterDialog — ngOnInit default model construction", () => {
   it("should build a fresh field-sourced model when not in edit mode and fields exist", () => {
      const { comp } = createComponent({ selectEdit: false, fields: [makeField()] });
      comp.ngOnInit();
      expect(comp.model).toEqual(expect.objectContaining({ name: "", value: "", valueSource: "field", type: XSchema.STRING }));
   });

   it("should build a fresh constant-sourced model when not in edit mode and there are no fields", () => {
      const { comp } = createComponent({ selectEdit: false, fields: [] });
      comp.ngOnInit();
      expect(comp.model.valueSource).toBe("constant");
   });

   it("should reset an edited field-sourced model back to constant when there are no fields", () => {
      const { comp } = createComponent({
         selectEdit: true, fields: [],
         model: makeModel({ valueSource: "field", type: XSchema.STRING }),
      });
      comp.ngOnInit();
      expect(comp.model.valueSource).toBe("constant");
   });

   it("should leave a field-sourced model alone in edit mode when fields exist", () => {
      const { comp } = createComponent({
         selectEdit: true, fields: [makeField()],
         model: makeModel({ valueSource: "field" }),
      });
      comp.ngOnInit();
      expect(comp.model.valueSource).toBe("field");
   });
});

describe("InputParameterDialog — ngOnInit form wiring", () => {
   it("should initialize the form from the current model", () => {
      const { comp } = createComponent({ model: makeModel({ name: "para1", type: XSchema.STRING, value: "abc" }) });
      comp.ngOnInit();
      expect(comp.form).toBeTruthy();
      expect(comp.form.controls["name"].value).toBe("para1");
      expect(comp.form.controls["type"].value).toBe(XSchema.STRING);
   });

   it("should sync model.name when the name control changes", () => {
      const { comp } = createComponent();
      comp.ngOnInit();
      comp.form.controls["name"].setValue("renamed");
      expect(comp.model.name).toBe("renamed");
   });

   it("should sync model.type, run changeType, and request change detection when the type control changes", () => {
      const { comp, changeDetectionRef } = createComponent();
      comp.ngOnInit();
      const changeTypeSpy = vi.spyOn(comp, "changeType");

      comp.form.controls["type"].setValue(XSchema.INTEGER);

      expect(comp.model.type).toBe(XSchema.INTEGER);
      expect(changeTypeSpy).toHaveBeenCalled();
      expect(changeDetectionRef.detectChanges).toHaveBeenCalled();
   });

   it("should route alphaNumericValue control changes through updateValue", () => {
      const { comp } = createComponent({ model: makeModel({ type: XSchema.STRING }) });
      comp.ngOnInit();
      const updateValueSpy = vi.spyOn(comp, "updateValue");

      comp.form.controls["alphaNumericValue"].setValue("hello");

      expect(updateValueSpy).toHaveBeenCalledWith("hello");
   });

   it("should route dateValue control changes through updateValue", () => {
      const { comp } = createComponent({ model: makeModel({ type: XSchema.DATE }) });
      comp.ngOnInit();
      const updateValueSpy = vi.spyOn(comp, "updateValue");
      const dateStruct = { year: 2024, month: 3, day: 15 };

      comp.form.controls["dateValue"].setValue(dateStruct);

      expect(updateValueSpy).toHaveBeenCalledWith(dateStruct);
   });

   it("should debounce timeValue control changes into updateTime + updateDateTime", () => {
      vi.useFakeTimers();

      try {
         const { comp } = createComponent({ model: makeModel({ type: XSchema.TIME }) });
         comp.ngOnInit();
         const updateTimeSpy = vi.spyOn(comp, "updateTime");
         const updateDateTimeSpy = vi.spyOn(comp, "updateDateTime");

         comp.form.controls["timeValue"].setValue("10:30:00");
         expect(updateTimeSpy).not.toHaveBeenCalled(); // not yet — still within the debounce window

         vi.advanceTimersByTime(1000);

         expect(updateTimeSpy).toHaveBeenCalledWith("10:30:00");
         expect(updateDateTimeSpy).toHaveBeenCalled();
      }
      finally {
         vi.useRealTimers();
      }
   });
});

// ---------------------------------------------------------------------------
// Group 3: ok() [Risk 2]
// ---------------------------------------------------------------------------

describe("InputParameterDialog — ok()", () => {
   // Bug: both branches of the `valueSource === "field"` fallback (empty-value and
   // no-longer-matches) unconditionally dereference `this.fields[0].name` without checking
   // that `fields` is non-empty first. ngOnInit() normally resets valueSource back to
   // "constant" when there are no fields, but ok() itself doesn't re-check that invariant,
   // so calling it with a stale/manually-set field-sourced model and zero fields crashes.
   it.fails("should not crash for a field-sourced model with an empty value and zero available fields", () => {
      const { comp } = createComponent({
         fields: [], model: makeModel({ valueSource: "field", value: "" }),
      });

      expect(() => comp.ok()).not.toThrow();
   });

   it("should resolve an empty field-sourced value to the first available field", () => {
      const field = makeField({ name: "city" });
      const { comp } = createComponent({
         fields: [field], model: makeModel({ valueSource: "field", value: "" }),
      });

      comp.ok();

      expect(comp.model.value).toBe("city");
   });

   it("should fall back to the first field when the current value no longer matches any field", () => {
      const field = makeField({ name: "city" });
      const { comp } = createComponent({
         fields: [field], model: makeModel({ valueSource: "field", value: "stale_field" }),
      });

      comp.ok();

      expect(comp.model.value).toBe("city");
   });

   it("should keep the value unchanged when it matches an existing field", () => {
      const field = makeField({ name: "city" });
      const { comp } = createComponent({
         fields: [field], model: makeModel({ valueSource: "field", value: "city" }),
      });

      comp.ok();

      expect(comp.model.value).toBe("city");
   });

   it("should truncate a CHARACTER value down to its first character", () => {
      const { comp } = createComponent({
         model: makeModel({ valueSource: "constant", type: XSchema.CHARACTER, value: "hello" }),
      });

      comp.ok();

      expect(comp.model.value).toBe("h");
   });

   it("should emit onCommit with the final model", () => {
      const { comp } = createComponent({ model: makeModel({ valueSource: "constant", value: "42" }) });
      const emitted: any[] = [];
      comp.onCommit.subscribe((v: any) => emitted.push(v));

      comp.ok();

      expect(emitted).toEqual([comp.model]);
   });
});

// ---------------------------------------------------------------------------
// Group 4: close() [Risk 1]
// ---------------------------------------------------------------------------

describe("InputParameterDialog — close()", () => {
   it("should emit onCancel with 'cancel'", () => {
      const { comp } = createComponent();
      const emitted: string[] = [];
      comp.onCancel.subscribe((v: string) => emitted.push(v));

      comp.close();

      expect(emitted).toEqual(["cancel"]);
   });
});

// ---------------------------------------------------------------------------
// Group 5: changeValueSource [Risk 2]
// ---------------------------------------------------------------------------

describe("InputParameterDialog — changeValueSource", () => {
   it("should switch to a constant value source and clear the value", () => {
      const { comp } = createComponent({ model: makeModel({ valueSource: "field", value: "city" }) });
      comp.ngOnInit();

      comp.changeValueSource("constant");

      expect(comp.model.valueSource).toBe("constant");
      expect(comp.model.value).toBe("");
   });

   it("should switch to a field value source and force type STRING", () => {
      const field = makeField({ attribute: "city" });
      const { comp } = createComponent({ fields: [field], model: makeModel({ valueSource: "constant", type: XSchema.INTEGER }) });
      comp.ngOnInit();

      comp.changeValueSource("field");

      expect(comp.model.valueSource).toBe("field");
      expect(comp.model.type).toBe(XSchema.STRING);
   });

   // Bug: same root cause as the model-setter bug above. changeValueSource() sets
   // model.value to the first field's attribute, then unconditionally calls changeType()
   // as its last step. For the new STRING type, changeType() resets alphaNumericValue to
   // "" — which cascades through the alphaNumericValue control's ngOnInit subscription into
   // updateValue(""), overwriting the field-derived value with "" immediately after it was set.
   it.fails("should default the value to the first field when switching to a field value source", () => {
      const field = makeField({ attribute: "city" });
      const { comp } = createComponent({ fields: [field], model: makeModel({ valueSource: "constant", type: XSchema.INTEGER }) });
      comp.ngOnInit();

      comp.changeValueSource("field");

      expect(comp.model.value).toBe("city");
   });

   it("should default to an empty value when switching to field with no fields available", () => {
      const { comp } = createComponent({ fields: [], model: makeModel({ valueSource: "constant" }) });
      comp.ngOnInit();

      comp.changeValueSource("field");

      expect(comp.model.value).toBe("");
   });

   it("should re-run changeType as the last step", () => {
      const { comp } = createComponent();
      comp.ngOnInit();
      const spy = vi.spyOn(comp, "changeType");

      comp.changeValueSource("constant");

      expect(spy).toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 6: changeName [Risk 1]
// ---------------------------------------------------------------------------

describe("InputParameterDialog — changeName", () => {
   it("should update the name form control", () => {
      const { comp } = createComponent();
      comp.ngOnInit();

      comp.changeName("newName");

      expect(comp.form.controls["name"].value).toBe("newName");
   });
});
