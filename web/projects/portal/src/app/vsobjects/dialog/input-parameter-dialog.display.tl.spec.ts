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
 * InputParameterDialog — Pass 3: Display
 *
 * Scope (per prescan): changeType() (6 branches), updateValue() (5 branches),
 * changeValidators() (9 branches), isInvalid() (4 branches via switch), isGrayedOut(),
 * isFormInvalid(), hasViewsheetParameters(), invalidDate().
 *
 * updateDateTime()'s own branches were already fully exercised in Pass 2 (as part of the
 * updateDate/updateTime/updateDateTime sequencing risk coverage) and are not repeated here
 * to avoid duplicate coverage [B6].
 *
 * changeType()/changeValidators() drive real Angular FormControl validators (not mocked),
 * so their branches are verified behaviorally — setting a representative valid/invalid
 * control value and checking `.invalid` — rather than by comparing validator function
 * references, which wouldn't be reference-equal across closures anyway.
 */

import { XSchema } from "../../common/data/xschema";
import { createComponent, makeField, makeModel } from "./input-parameter-dialog.component.test-helpers";

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: changeType [Risk 2]
// ---------------------------------------------------------------------------

describe("InputParameterDialog — changeType", () => {
   it("should clear alphaNumericValue for a numeric type", () => {
      const { comp } = createComponent({ model: makeModel({ type: XSchema.INTEGER }) });
      comp.ngOnInit();
      comp.form.controls["alphaNumericValue"].setValue("stale");

      comp.changeType();

      expect(comp.form.controls["alphaNumericValue"].value).toBe("");
   });

   it("should clear alphaNumericValue for STRING", () => {
      const { comp } = createComponent({ model: makeModel({ type: XSchema.STRING }) });
      comp.ngOnInit();
      comp.form.controls["alphaNumericValue"].setValue("stale");

      comp.changeType();

      expect(comp.form.controls["alphaNumericValue"].value).toBe("");
   });

   it("should clear alphaNumericValue for CHARACTER", () => {
      const { comp } = createComponent({ model: makeModel({ type: XSchema.CHARACTER }) });
      comp.ngOnInit();
      comp.form.controls["alphaNumericValue"].setValue("stale");

      comp.changeType();

      expect(comp.form.controls["alphaNumericValue"].value).toBe("");
   });

   it("should sync dateValue from the stored date for DATE", () => {
      const { comp } = createComponent({ model: makeModel({ type: XSchema.DATE }) });
      comp.ngOnInit();
      comp.date = { year: 2024, month: 3, day: 15 };

      comp.changeType();

      expect(comp.form.controls["dateValue"].value).toEqual({ year: 2024, month: 3, day: 15 });
   });

   it("should sync timeValue from the stored time for TIME", () => {
      const { comp } = createComponent({ model: makeModel({ type: XSchema.TIME }) });
      comp.ngOnInit();
      comp.time = "10:30:00";

      comp.changeType();

      expect(comp.form.controls["timeValue"].value).toBe("10:30:00");
   });

   it("should sync both dateValue and timeValue for TIME_INSTANT", () => {
      const { comp } = createComponent({ model: makeModel({ type: XSchema.TIME_INSTANT }) });
      comp.ngOnInit();
      comp.date = { year: 2024, month: 3, day: 15 };
      comp.time = "10:30:00";

      comp.changeType();

      expect(comp.form.controls["dateValue"].value).toEqual({ year: 2024, month: 3, day: 15 });
      expect(comp.form.controls["timeValue"].value).toBe("10:30:00");
   });

   it("should default alphaNumericValue to true for BOOLEAN", () => {
      const { comp } = createComponent({ model: makeModel({ type: XSchema.BOOLEAN }) });
      comp.ngOnInit();

      comp.changeType();

      expect(comp.form.controls["alphaNumericValue"].value).toBe(true);
   });

   it("should always run changeValidators for the current type", () => {
      const { comp } = createComponent({ model: makeModel({ type: XSchema.INTEGER }) });
      comp.ngOnInit();
      const spy = vi.spyOn(comp, "changeValidators");

      comp.changeType();

      expect(spy).toHaveBeenCalledWith(XSchema.INTEGER);
   });
});

// ---------------------------------------------------------------------------
// Group 2: updateValue [Risk 2]
// ---------------------------------------------------------------------------

describe("InputParameterDialog — updateValue", () => {
   // updateValue() always ends by calling changeValidators(), which needs a real form to
   // exist (it calls this.form.controls[...].setValidators(...)) — so every test here
   // initializes the form first, even though updateValue's own dispatch doesn't need it.
   it("should assign the raw value directly for a numeric/STRING/CHARACTER type", () => {
      const { comp } = createComponent({ model: makeModel({ type: XSchema.STRING }) });
      comp.ngOnInit();
      comp.updateValue("hello");
      expect(comp.model.value).toBe("hello");
   });

   it("should delegate to updateDate for DATE", () => {
      const { comp } = createComponent({ model: makeModel({ type: XSchema.DATE }) });
      comp.ngOnInit();
      const spy = vi.spyOn(comp, "updateDate");
      const dateStruct = { year: 2024, month: 3, day: 15 };

      comp.updateValue(dateStruct);

      expect(spy).toHaveBeenCalledWith(dateStruct);
   });

   it("should delegate to updateTime for TIME", () => {
      const { comp } = createComponent({ model: makeModel({ type: XSchema.TIME }) });
      comp.ngOnInit();
      const spy = vi.spyOn(comp, "updateTime");

      comp.updateValue("10:30:00");

      expect(spy).toHaveBeenCalledWith("10:30:00");
   });

   it("should delegate to updateDate (not updateTime) for TIME_INSTANT", () => {
      const { comp } = createComponent({ model: makeModel({ type: XSchema.TIME_INSTANT }) });
      comp.ngOnInit();
      const updateDateSpy = vi.spyOn(comp, "updateDate");
      const updateTimeSpy = vi.spyOn(comp, "updateTime");
      const dateStruct = { year: 2024, month: 3, day: 15 };

      comp.updateValue(dateStruct);

      expect(updateDateSpy).toHaveBeenCalledWith(dateStruct);
      expect(updateTimeSpy).not.toHaveBeenCalled();
   });

   it("should fall back to assigning the raw value directly for an unrecognized type", () => {
      const { comp } = createComponent({ model: makeModel({ type: "unknown" as any }) });
      comp.updateValue("raw");
      expect(comp.model.value).toBe("raw");
   });

   it("should always run changeValidators for the current type", () => {
      const { comp } = createComponent({ model: makeModel({ type: XSchema.STRING }) });
      comp.ngOnInit();
      const spy = vi.spyOn(comp, "changeValidators");

      comp.updateValue("x");

      expect(spy).toHaveBeenCalledWith(XSchema.STRING);
   });
});

// ---------------------------------------------------------------------------
// Group 3: changeValidators [Risk 2]
// ---------------------------------------------------------------------------

describe("InputParameterDialog — changeValidators", () => {
   // ngOnInit() alone never calls changeValidators — that only happens as a side effect of
   // changeType(), itself only triggered by the "type" control's own valueChanges
   // subscription. changeValidators() is public and independently testable, so it's called
   // directly here rather than indirectly through the type-control cascade.
   function setup(type: string) {
      const { comp } = createComponent({ model: makeModel({ type }) });
      comp.ngOnInit();
      comp.changeValidators(type);
      return comp;
   }

   it("should require a non-blank alphaNumericValue for STRING", () => {
      const comp = setup(XSchema.STRING);
      comp.form.controls["alphaNumericValue"].setValue("  ");
      expect(comp.form.controls["alphaNumericValue"].invalid).toBe(true);
      comp.form.controls["alphaNumericValue"].setValue("ok");
      expect(comp.form.controls["alphaNumericValue"].invalid).toBe(false);
   });

   it("should require a non-blank alphaNumericValue for CHARACTER", () => {
      const comp = setup(XSchema.CHARACTER);
      comp.form.controls["alphaNumericValue"].setValue("");
      expect(comp.form.controls["alphaNumericValue"].invalid).toBe(true);
   });

   it("should require an integer pattern for INTEGER", () => {
      const comp = setup(XSchema.INTEGER);
      comp.form.controls["alphaNumericValue"].setValue("12.5");
      expect(comp.form.controls["alphaNumericValue"].invalid).toBe(true);
      comp.form.controls["alphaNumericValue"].setValue("125");
      expect(comp.form.controls["alphaNumericValue"].invalid).toBe(false);
   });

   it("should require an integer pattern for SHORT", () => {
      const comp = setup(XSchema.SHORT);
      comp.form.controls["alphaNumericValue"].setValue("abc");
      expect(comp.form.controls["alphaNumericValue"].invalid).toBe(true);
   });

   it("should require an integer pattern for BYTE", () => {
      const comp = setup(XSchema.BYTE);
      comp.form.controls["alphaNumericValue"].setValue("abc");
      expect(comp.form.controls["alphaNumericValue"].invalid).toBe(true);
   });

   it("should require an integer pattern for LONG", () => {
      const comp = setup(XSchema.LONG);
      comp.form.controls["alphaNumericValue"].setValue("abc");
      expect(comp.form.controls["alphaNumericValue"].invalid).toBe(true);
   });

   it("should require a numeric pattern for FLOAT/DOUBLE", () => {
      const comp = setup(XSchema.FLOAT);
      comp.form.controls["alphaNumericValue"].setValue("abc");
      expect(comp.form.controls["alphaNumericValue"].invalid).toBe(true);
      comp.form.controls["alphaNumericValue"].setValue("12.5");
      expect(comp.form.controls["alphaNumericValue"].invalid).toBe(false);
   });

   it("should require a non-blank dateValue for DATE, leaving alphaNumericValue/timeValue unvalidated", () => {
      const comp = setup(XSchema.DATE);
      comp.form.controls["dateValue"].setValue("");
      expect(comp.form.controls["dateValue"].invalid).toBe(true);
      expect(comp.form.controls["alphaNumericValue"].valid).toBe(true);
      expect(comp.form.controls["timeValue"].valid).toBe(true);
   });

   it("should require a non-empty timeValue for TIME", () => {
      const comp = setup(XSchema.TIME);
      comp.form.controls["timeValue"].setValue(null);
      expect(comp.form.controls["timeValue"].invalid).toBe(true);
   });

   it("should require both dateValue and timeValue for TIME_INSTANT", () => {
      const comp = setup(XSchema.TIME_INSTANT);
      comp.form.controls["dateValue"].setValue("");
      comp.form.controls["timeValue"].setValue(null);
      expect(comp.form.controls["dateValue"].invalid).toBe(true);
      expect(comp.form.controls["timeValue"].invalid).toBe(true);
   });

   it("should clear all three controls' validators for BOOLEAN", () => {
      const comp = setup(XSchema.BOOLEAN);
      comp.form.controls["alphaNumericValue"].setValue("");
      comp.form.controls["dateValue"].setValue("");
      comp.form.controls["timeValue"].setValue(null);
      expect(comp.form.controls["alphaNumericValue"].valid).toBe(true);
      expect(comp.form.controls["dateValue"].valid).toBe(true);
      expect(comp.form.controls["timeValue"].valid).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 4: isInvalid [Risk 2]
// ---------------------------------------------------------------------------

describe("InputParameterDialog — isInvalid", () => {
   it("should be true when the form does not exist yet", () => {
      const { comp } = createComponent();
      expect(comp.isInvalid()).toBe(true);
   });

   it("should check dateValue validity and date parseability for DATE", () => {
      const { comp } = createComponent({ model: makeModel({ type: XSchema.DATE }) });
      comp.ngOnInit();
      comp.changeValidators(XSchema.DATE);
      comp.form.controls["dateValue"].setValue({ year: 2024, month: 3, day: 15 });
      expect(comp.form.controls["dateValue"].invalid).toBe(false);
      // dateValue's own control is valid, but a stale/unparseable model.value (set directly,
      // bypassing the dateValue -> updateDate -> updateDateTime pipeline that would otherwise
      // recompute it) is still caught by invalidDate().
      comp.model.value = "not-a-date";
      expect(comp.isInvalid()).toBe(true);
   });

   it("should check timeValue validity for TIME", () => {
      const { comp } = createComponent({ model: makeModel({ type: XSchema.TIME }) });
      comp.ngOnInit();
      comp.changeValidators(XSchema.TIME);
      comp.form.controls["timeValue"].setValue(null);
      expect(comp.isInvalid()).toBe(true);
      comp.form.controls["timeValue"].setValue("10:30:00");
      expect(comp.isInvalid()).toBe(false);
   });

   it("should check dateValue, invalidDate, and timeValue together for TIME_INSTANT", () => {
      const { comp } = createComponent({ model: makeModel({ type: XSchema.TIME_INSTANT }) });
      comp.ngOnInit();
      comp.changeValidators(XSchema.TIME_INSTANT);
      comp.form.controls["dateValue"].setValue({ year: 2024, month: 3, day: 15 });
      comp.form.controls["timeValue"].setValue("10:30:00");
      expect(comp.isInvalid()).toBe(false);
   });

   it("should be valid for the default case when the control passes and model.value is truthy", () => {
      const { comp } = createComponent({ model: makeModel({ type: XSchema.STRING, value: "" }) });
      comp.ngOnInit();
      comp.changeValidators(XSchema.STRING);

      comp.form.controls["alphaNumericValue"].setValue("ok");

      expect(comp.isInvalid()).toBe(false);
   });

   it("should be invalid for the default case when the control itself fails validation, regardless of model.value", () => {
      const { comp } = createComponent({ model: makeModel({ type: XSchema.STRING }) });
      comp.ngOnInit();
      comp.changeValidators(XSchema.STRING);
      comp.form.controls["alphaNumericValue"].setValue("   "); // fails notWhiteSpace
      expect(comp.form.controls["alphaNumericValue"].invalid).toBe(true);
      // bypass the control->model cascade to prove the control-invalid operand alone drives
      // isInvalid() to true, independent of model.value's own truthiness
      comp.model.value = "some truthy value";

      expect(comp.isInvalid()).toBe(true);
   });

   it("should be invalid for the default case when model.value is falsy, even though the control itself is valid", () => {
      const { comp } = createComponent({ model: makeModel({ type: XSchema.STRING }) });
      comp.ngOnInit();
      comp.changeValidators(XSchema.STRING);
      comp.form.controls["alphaNumericValue"].setValue("ok");
      expect(comp.form.controls["alphaNumericValue"].invalid).toBe(false);
      // bypass the control->model cascade to prove the !model.value operand alone drives
      // isInvalid() to true, independent of the control's own reported validity
      comp.model.value = "";

      expect(comp.isInvalid()).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 5: isFormInvalid [Risk 1]
// ---------------------------------------------------------------------------

describe("InputParameterDialog — isFormInvalid", () => {
   it("should be invalid when the name control is invalid even if the value is fine", () => {
      const { comp } = createComponent({ model: makeModel({ type: XSchema.STRING, value: "ok" }) });
      comp.ngOnInit();
      comp.form.controls["alphaNumericValue"].setValue("ok");
      comp.form.controls["name"].setValue("");

      expect(comp.isFormInvalid()).toBe(true);
   });

   it("should be valid when both the value and the name are valid", () => {
      const { comp } = createComponent({ model: makeModel({ type: XSchema.STRING, name: "para1" }) });
      comp.ngOnInit();
      comp.form.controls["alphaNumericValue"].setValue("ok");

      expect(comp.isFormInvalid()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 6: isGrayedOut [Risk 2]
// ---------------------------------------------------------------------------

describe("InputParameterDialog — isGrayedOut", () => {
   it("should be false for a null field", () => {
      const { comp } = createComponent();
      expect(comp.isGrayedOut(null)).toBe(false);
   });

   it("should be true when the field matches a grayed-out entry", () => {
      const { comp } = createComponent();
      comp.grayedOutFields = [makeField({ name: "city" }) as any];
      expect(comp.isGrayedOut("city")).toBe(true);
   });

   it("should normalize a ':' in the field name to '.' before comparing", () => {
      const { comp } = createComponent();
      comp.grayedOutFields = [makeField({ name: "a.b" }) as any];
      expect(comp.isGrayedOut("a:b")).toBe(true);
   });

   it("should be false when there are no grayed-out fields", () => {
      const { comp } = createComponent();
      comp.grayedOutFields = null;
      expect(comp.isGrayedOut("city")).toBe(false);
   });

   it("should be false when nothing matches", () => {
      const { comp } = createComponent();
      comp.grayedOutFields = [makeField({ name: "other" }) as any];
      expect(comp.isGrayedOut("city")).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 7: hasViewsheetParameters [Risk 1]
// ---------------------------------------------------------------------------

describe("InputParameterDialog — hasViewsheetParameters", () => {
   it("should be true when there is at least one viewsheet parameter", () => {
      const { comp } = createComponent({ viewsheetParameters: ["p1"] });
      expect(comp.hasViewsheetParameters()).toBe(true);
   });

   it("should be false when there are none", () => {
      const { comp } = createComponent({ viewsheetParameters: [] });
      expect(comp.hasViewsheetParameters()).toBeFalsy();
   });

   it("should be false when null", () => {
      const { comp } = createComponent({ viewsheetParameters: null });
      expect(comp.hasViewsheetParameters()).toBeFalsy();
   });
});

// ---------------------------------------------------------------------------
// Group 8: invalidDate [Risk 1]
// ---------------------------------------------------------------------------

describe("InputParameterDialog — invalidDate", () => {
   it("should be false for a value that parses to a real date", () => {
      const { comp } = createComponent({ model: makeModel({ value: "2024-03-15" }) });
      expect(comp.invalidDate()).toBe(false);
   });

   it("should be true for an unparseable value", () => {
      const { comp } = createComponent({ model: makeModel({ value: "not-a-date" }) });
      expect(comp.invalidDate()).toBe(true);
   });
});
