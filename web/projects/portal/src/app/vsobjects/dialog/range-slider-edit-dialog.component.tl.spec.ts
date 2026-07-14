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
 * RangeSliderEditDialog — single pass (+ memory-leak coverage)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] — initForm (numeric branch): control creation, initial values, validator wiring
 *   Group 2 [Risk 2] — initForm (date branch): control creation, formatDate seeding, valueChanges subscriptions
 *   Group 3 [Risk 3] — ngOnDestroy: subscription teardown via takeUntil(destroy$)
 *   Group 4 [Risk 1] — formatDate: date-only vs. datetime-local formatting, number input
 *   Group 5 [Risk 3] — dateRangeValidatorMin / dateRangeValidatorMax: symmetric boundary validators
 *   Group 6 [Risk 2] — minBeforeMax (group validator): numeric vs. date comparison mode
 *   Group 7 [Risk 2] — minMaxNotEqual (group validator): numeric vs. date comparison mode
 *   Group 8 [Risk 2] — close / ok: output emission contracts, numeric fallback-to-0, OK button disabled state
 *
 * Confirmed bugs (it.fails): none
 *
 * Out of scope: modal-header's own onCancel→close() delegation (library-level template wiring,
 * covered indirectly by calling close() directly in Group 8).
 */

import { Component, EventEmitter, Input, Output } from "@angular/core";
import { render } from "@testing-library/angular";
import { firstValueFrom } from "rxjs";

import { RangeSliderEditDialog } from "./range-slider-edit-dialog.component";
import { ModalHeaderComponent } from "../../widget/modal-header/modal-header.component";

afterEach(() => {
   vi.restoreAllMocks();
});

@Component({
   selector: "modal-header",
   standalone: true,
   template: "",
})
class ModalHeaderStub {
   @Input() title = "";
   @Input() cshid = "";
   @Output() onCancel = new EventEmitter<void>();
}

async function renderComponent(opts: {
   currentMin?: number | Date;
   currentMax?: number | Date;
   rangeMin?: number | Date;
   rangeMax?: number | Date;
   timeIncrement?: string;
   skipInitForm?: boolean;
} = {}) {
   const { fixture } = await render(RangeSliderEditDialog, {
      componentInputs: {
         currentMin: opts.currentMin ?? 5,
         currentMax: opts.currentMax ?? 10,
         rangeMin: opts.rangeMin ?? 0,
         rangeMax: opts.rangeMax ?? 100,
      },
      importOverrides: [
         { replace: ModalHeaderComponent, with: ModalHeaderStub },
      ],
      // The parent dialog host normally sets @Inputs then calls initForm() before the first
      // change-detection pass (this component has no ngOnInit of its own). Deferring the initial
      // detectChanges() and calling initForm() first avoids swapping `rangeForm` to a new
      // FormGroup instance mid-lifecycle, which would otherwise desync the formControlName
      // directives from the FormGroupDirective's known controls ("Cannot find control with
      // name" NG error).
      detectChangesOnRender: false,
   });

   const comp = fixture.componentInstance as RangeSliderEditDialog;

   if(opts.timeIncrement !== undefined) {
      comp.timeIncrement = opts.timeIncrement;
   }

   if(!opts.skipInitForm) {
      comp.initForm();
      fixture.detectChanges();
   }
   // skipInitForm tests target pure methods (formatDate, the validator factories) directly and
   // never touch the DOM, so change detection is intentionally never triggered — the default
   // empty rangeForm combined with isDateType=undefined would otherwise render the numeric
   // `@if(!isDateType)` input block with no matching "min"/"max" controls and crash.

   return { fixture, comp };
}

// ---------------------------------------------------------------------------
// Group 1: initForm — numeric branch
// ---------------------------------------------------------------------------

describe("RangeSliderEditDialog — initForm (numeric)", () => {
   it("should build min/max controls seeded from currentMin/currentMax and set isDateType false", async () => {
      const { comp } = await renderComponent({ currentMin: 5, currentMax: 10, rangeMin: 0, rangeMax: 100 });

      expect(comp.isDateType).toBe(false);
      expect(comp.rangeForm.get("min")?.value).toBe(5);
      expect(comp.rangeForm.get("max")?.value).toBe(10);
   });

   it("should mark min invalid when below rangeMin, and max invalid when above rangeMax", async () => {
      const { comp } = await renderComponent({ currentMin: -1, currentMax: 200, rangeMin: 0, rangeMax: 100 });

      expect(comp.rangeForm.get("min")?.errors?.["min"]).toBeTruthy();
      expect(comp.rangeForm.get("max")?.errors?.["max"]).toBeTruthy();
   });

   it("should mark min/max as required when empty", async () => {
      const { comp } = await renderComponent({ currentMin: 5, currentMax: 10 });

      comp.rangeForm.get("min")?.setValue(null);
      comp.rangeForm.get("max")?.setValue(null);

      expect(comp.rangeForm.get("min")?.errors?.["required"]).toBeTruthy();
      expect(comp.rangeForm.get("max")?.errors?.["required"]).toBeTruthy();
   });
});

// ---------------------------------------------------------------------------
// Group 2: initForm — date branch
// ---------------------------------------------------------------------------

describe("RangeSliderEditDialog — initForm (date)", () => {
   it("should build min/max controls seeded from formatDate(currentMin/currentMax) and set isDateType true", async () => {
      const { comp } = await renderComponent({
         currentMin: new Date(2024, 0, 15),
         currentMax: new Date(2024, 0, 20),
         rangeMin: new Date(2024, 0, 1),
         rangeMax: new Date(2024, 0, 31),
      });

      expect(comp.isDateType).toBe(true);
      expect(comp.rangeForm.get("min")?.value).toBe("2024-01-15");
      expect(comp.rangeForm.get("max")?.value).toBe("2024-01-20");
   });

   // 🔁 Regression-sensitive: the subscription writes back into `currentMin`/`currentMax`, which
   // ok() reads directly for the date branch — a dropped subscription would silently freeze the
   // committed value at its initial state.
   it("should update currentMin/currentMax when the min/max controls emit a new value", async () => {
      const { comp } = await renderComponent({
         currentMin: new Date(2024, 0, 15),
         currentMax: new Date(2024, 0, 20),
         rangeMin: new Date(2024, 0, 1),
         rangeMax: new Date(2024, 0, 31),
      });

      comp.rangeForm.get("min")?.setValue("2024-01-10");
      comp.rangeForm.get("max")?.setValue("2024-01-25");

      expect(comp.currentMin).toEqual(new Date("2024-01-10T00:00"));
      expect(comp.currentMax).toEqual(new Date("2024-01-25T00:00"));
   });

   it("should not update currentMin/currentMax when the emitted value is falsy", async () => {
      const initialMin = new Date(2024, 0, 15);
      const { comp } = await renderComponent({
         currentMin: initialMin,
         currentMax: new Date(2024, 0, 20),
         rangeMin: new Date(2024, 0, 1),
         rangeMax: new Date(2024, 0, 31),
      });

      comp.rangeForm.get("min")?.setValue("");

      expect(comp.currentMin).toBe(initialMin);
   });

   it("should parse the emitted value directly (no T00:00 append) when timeIncrement is 't'", async () => {
      const { comp } = await renderComponent({
         currentMin: new Date(2024, 0, 15, 9, 30),
         currentMax: new Date(2024, 0, 20, 9, 30),
         rangeMin: new Date(2024, 0, 1),
         rangeMax: new Date(2024, 0, 31),
         timeIncrement: "t",
      });

      comp.rangeForm.get("min")?.setValue("2024-01-10T14:45");

      expect(comp.currentMin).toEqual(new Date("2024-01-10T14:45"));
   });
});

// ---------------------------------------------------------------------------
// Group 3: ngOnDestroy — subscription teardown
// ---------------------------------------------------------------------------

describe("RangeSliderEditDialog — ngOnDestroy", () => {
   it("should stop updating currentMin/currentMax after the component is destroyed", async () => {
      const { fixture, comp } = await renderComponent({
         currentMin: new Date(2024, 0, 15),
         currentMax: new Date(2024, 0, 20),
         rangeMin: new Date(2024, 0, 1),
         rangeMax: new Date(2024, 0, 31),
      });
      const minControl = comp.rangeForm.get("min");
      const frozenMin = comp.currentMin;

      fixture.destroy();
      minControl?.setValue("2024-01-10");

      expect(comp.currentMin).toBe(frozenMin);
   });
});

// ---------------------------------------------------------------------------
// Group 4: formatDate
// ---------------------------------------------------------------------------

describe("RangeSliderEditDialog — formatDate", () => {
   it("should format as YYYY-MM-DD when timeIncrement is not 't'", async () => {
      const { comp } = await renderComponent({ skipInitForm: true });

      expect(comp.formatDate(new Date(2024, 2, 5))).toBe("2024-03-05");
   });

   it("should format as YYYY-MM-DDTHH:mm when timeIncrement is 't'", async () => {
      const { comp } = await renderComponent({ skipInitForm: true, timeIncrement: "t" });

      expect(comp.formatDate(new Date(2024, 2, 5, 8, 5))).toBe("2024-03-05T08:05");
   });

   it("should accept a numeric epoch timestamp", async () => {
      const { comp } = await renderComponent({ skipInitForm: true });
      const date = new Date(2024, 5, 1);

      expect(comp.formatDate(date.getTime())).toBe("2024-06-01");
   });
});

// ---------------------------------------------------------------------------
// Group 5: dateRangeValidatorMin / dateRangeValidatorMax
// ---------------------------------------------------------------------------

describe("RangeSliderEditDialog — dateRangeValidatorMin", () => {
   it("should return null when the control value is falsy, regardless of min", async () => {
      const { comp } = await renderComponent({ skipInitForm: true });
      const validator = comp.dateRangeValidatorMin(new Date(2024, 0, 15, 10, 0));

      expect(validator({ value: "" } as any)).toBeNull();
   });

   it("should return dateMinError when the value (with T00:00 appended) is before min", async () => {
      const { comp } = await renderComponent({ skipInitForm: true });
      const min = new Date(2024, 0, 15, 10, 0);
      const validator = comp.dateRangeValidatorMin(min);

      // "2024-01-15" + "T00:00" = midnight, which is before 10:00 on the same day.
      const result = validator({ value: "2024-01-15" } as any);

      expect(result).toEqual({ dateMinError: { requiredMin: min, actual: new Date("2024-01-15T00:00").getTime() } });
   });

   it("should return null when the value is at or after min", async () => {
      const { comp } = await renderComponent({ skipInitForm: true });
      const validator = comp.dateRangeValidatorMin(new Date(2024, 0, 10, 0, 0));

      expect(validator({ value: "2024-01-15" } as any)).toBeNull();
   });

   it("should parse the raw value without appending T00:00 when timeIncrement is 't'", async () => {
      const { comp } = await renderComponent({ skipInitForm: true, timeIncrement: "t" });
      const min = new Date(2024, 0, 15, 10, 0);
      const validator = comp.dateRangeValidatorMin(min);

      // Exactly equal to min when parsed as a full datetime — not before it.
      expect(validator({ value: "2024-01-15T10:00" } as any)).toBeNull();
   });
});

describe("RangeSliderEditDialog — dateRangeValidatorMax", () => {
   it("should return null when the control value is falsy, regardless of max", async () => {
      const { comp } = await renderComponent({ skipInitForm: true });
      const validator = comp.dateRangeValidatorMax(new Date(2024, 0, 15, 10, 0));

      expect(validator({ value: "" } as any)).toBeNull();
   });

   it("should return dateMaxError when the value (with T00:00 appended) is after max", async () => {
      const { comp } = await renderComponent({ skipInitForm: true });
      const max = new Date(2024, 0, 15, 0, 0);
      const validator = comp.dateRangeValidatorMax(max);

      // "2024-01-15" + "T00:00" is the same as max's midnight, so use a later day.
      const result = validator({ value: "2024-01-16" } as any);

      expect(result).toEqual({ dateMaxError: { requiredMax: max, actual: new Date("2024-01-16T00:00").getTime() } });
   });

   it("should return null when the value is at or before max", async () => {
      const { comp } = await renderComponent({ skipInitForm: true });
      const validator = comp.dateRangeValidatorMax(new Date(2024, 0, 20, 0, 0));

      expect(validator({ value: "2024-01-15" } as any)).toBeNull();
   });

   it("should parse the raw value without appending T00:00 when timeIncrement is 't'", async () => {
      const { comp } = await renderComponent({ skipInitForm: true, timeIncrement: "t" });
      const max = new Date(2024, 0, 15, 10, 0);
      const validator = comp.dateRangeValidatorMax(max);

      // Exactly equal to max when parsed as a full datetime — not after it.
      expect(validator({ value: "2024-01-15T10:00" } as any)).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 6: minBeforeMax (group validator)
// ---------------------------------------------------------------------------

describe("RangeSliderEditDialog — minBeforeMax", () => {
   it("should return null when min is null", async () => {
      const { comp } = await renderComponent({ currentMin: 5, currentMax: 10 });
      comp.rangeForm.get("min")?.setValue(null);

      expect(comp.rangeForm.errors?.["minAfterMax"]).toBeFalsy();
   });

   it("should return null when max is null", async () => {
      const { comp } = await renderComponent({ currentMin: 5, currentMax: 10 });
      comp.rangeForm.get("max")?.setValue(null);

      expect(comp.rangeForm.errors?.["minAfterMax"]).toBeFalsy();
   });

   it("should flag minAfterMax when min is greater than max in numeric mode", async () => {
      const { comp } = await renderComponent({ currentMin: 5, currentMax: 10, rangeMin: 0, rangeMax: 100 });

      comp.rangeForm.get("min")?.setValue(50);
      comp.rangeForm.get("max")?.setValue(20);

      expect(comp.rangeForm.errors?.["minAfterMax"]).toBe(true);
   });

   it("should not flag minAfterMax when min is less than or equal to max in numeric mode", async () => {
      const { comp } = await renderComponent({ currentMin: 5, currentMax: 10, rangeMin: 0, rangeMax: 100 });

      comp.rangeForm.get("min")?.setValue(20);
      comp.rangeForm.get("max")?.setValue(50);

      expect(comp.rangeForm.errors?.["minAfterMax"]).toBeFalsy();
   });

   it("should flag minAfterMax when min date-string is greater than max date-string", async () => {
      const { comp } = await renderComponent({
         currentMin: new Date(2024, 0, 5),
         currentMax: new Date(2024, 0, 10),
         rangeMin: new Date(2024, 0, 1),
         rangeMax: new Date(2024, 0, 31),
      });

      comp.rangeForm.get("min")?.setValue("2024-01-20");
      comp.rangeForm.get("max")?.setValue("2024-01-10");

      expect(comp.rangeForm.errors?.["minAfterMax"]).toBe(true);
   });

   it("should not flag minAfterMax when min date-string is less than or equal to max date-string", async () => {
      const { comp } = await renderComponent({
         currentMin: new Date(2024, 0, 5),
         currentMax: new Date(2024, 0, 10),
         rangeMin: new Date(2024, 0, 1),
         rangeMax: new Date(2024, 0, 31),
      });

      comp.rangeForm.get("min")?.setValue("2024-01-05");
      comp.rangeForm.get("max")?.setValue("2024-01-10");

      expect(comp.rangeForm.errors?.["minAfterMax"]).toBeFalsy();
   });
});

// ---------------------------------------------------------------------------
// Group 7: minMaxNotEqual (group validator)
// ---------------------------------------------------------------------------

describe("RangeSliderEditDialog — minMaxNotEqual", () => {
   it("should return null when min is null", async () => {
      const { comp } = await renderComponent({ currentMin: 5, currentMax: 10 });
      comp.rangeForm.get("min")?.setValue(null);

      expect(comp.rangeForm.errors?.["minMaxEqual"]).toBeFalsy();
   });

   it("should return null when max is null", async () => {
      const { comp } = await renderComponent({ currentMin: 5, currentMax: 10 });
      comp.rangeForm.get("max")?.setValue(null);

      expect(comp.rangeForm.errors?.["minMaxEqual"]).toBeFalsy();
   });

   it("should flag minMaxEqual when min numerically equals max", async () => {
      const { comp } = await renderComponent({ currentMin: 5, currentMax: 10, rangeMin: 0, rangeMax: 100 });

      comp.rangeForm.get("min")?.setValue(30);
      comp.rangeForm.get("max")?.setValue(30);

      expect(comp.rangeForm.errors?.["minMaxEqual"]).toBe(true);
   });

   it("should not flag minMaxEqual when min and max are numerically different", async () => {
      const { comp } = await renderComponent({ currentMin: 5, currentMax: 10, rangeMin: 0, rangeMax: 100 });

      comp.rangeForm.get("min")?.setValue(30);
      comp.rangeForm.get("max")?.setValue(31);

      expect(comp.rangeForm.errors?.["minMaxEqual"]).toBeFalsy();
   });

   it("should flag minMaxEqual when min and max date-strings are exactly equal", async () => {
      const { comp } = await renderComponent({
         currentMin: new Date(2024, 0, 5),
         currentMax: new Date(2024, 0, 10),
         rangeMin: new Date(2024, 0, 1),
         rangeMax: new Date(2024, 0, 31),
      });

      comp.rangeForm.get("min")?.setValue("2024-01-15");
      comp.rangeForm.get("max")?.setValue("2024-01-15");

      expect(comp.rangeForm.errors?.["minMaxEqual"]).toBe(true);
   });

   it("should not flag minMaxEqual when min and max date-strings differ", async () => {
      const { comp } = await renderComponent({
         currentMin: new Date(2024, 0, 5),
         currentMax: new Date(2024, 0, 10),
         rangeMin: new Date(2024, 0, 1),
         rangeMax: new Date(2024, 0, 31),
      });

      comp.rangeForm.get("min")?.setValue("2024-01-15");
      comp.rangeForm.get("max")?.setValue("2024-01-16");

      expect(comp.rangeForm.errors?.["minMaxEqual"]).toBeFalsy();
   });
});

// ---------------------------------------------------------------------------
// Group 8: close / ok
// ---------------------------------------------------------------------------

describe("RangeSliderEditDialog — close", () => {
   it("should emit onCancel with 'cancel'", async () => {
      const { comp } = await renderComponent({ currentMin: 5, currentMax: 10 });
      const promise = firstValueFrom(comp.onCancel);

      comp.close();

      expect(await promise).toBe("cancel");
   });
});

describe("RangeSliderEditDialog — ok", () => {
   it("should emit onCommit with numeric min/max parsed from the form in numeric mode", async () => {
      const { comp } = await renderComponent({ currentMin: 5, currentMax: 10, rangeMin: 0, rangeMax: 100 });
      comp.rangeForm.get("min")?.setValue(20);
      comp.rangeForm.get("max")?.setValue(80);
      const promise = firstValueFrom(comp.onCommit);

      comp.ok();

      expect(await promise).toEqual({ min: 20, max: 80 });
   });

   it("should fall back to 0 for a falsy numeric form value", async () => {
      const { comp } = await renderComponent({ currentMin: 5, currentMax: 10, rangeMin: 0, rangeMax: 100 });
      comp.rangeForm.get("min")?.setValue(null);
      const promise = firstValueFrom(comp.onCommit);

      comp.ok();

      expect((await promise).min).toBe(0);
   });

   it("should emit onCommit with the raw currentMin/currentMax Date objects in date mode", async () => {
      const currentMin = new Date(2024, 0, 15);
      const currentMax = new Date(2024, 0, 20);
      const { comp } = await renderComponent({
         currentMin, currentMax,
         rangeMin: new Date(2024, 0, 1),
         rangeMax: new Date(2024, 0, 31),
      });
      const promise = firstValueFrom(comp.onCommit);

      comp.ok();

      expect(await promise).toEqual({ min: currentMin, max: currentMax });
   });

   it("should disable the OK button in the DOM when the form is invalid", async () => {
      const { fixture, comp } = await renderComponent({ currentMin: 5, currentMax: 10, rangeMin: 0, rangeMax: 100 });
      comp.rangeForm.get("min")?.setValue(null);
      fixture.detectChanges();

      const okButton = fixture.nativeElement.querySelector("button.btn-primary") as HTMLButtonElement;

      expect(okButton.disabled).toBe(true);
   });

   it("should enable the OK button in the DOM when the form is valid", async () => {
      const { fixture } = await renderComponent({ currentMin: 5, currentMax: 10, rangeMin: 0, rangeMax: 100 });
      fixture.detectChanges();

      const okButton = fixture.nativeElement.querySelector("button.btn-primary") as HTMLButtonElement;

      expect(okButton.disabled).toBe(false);
   });
});
