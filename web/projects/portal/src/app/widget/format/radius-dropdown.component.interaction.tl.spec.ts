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
 * RadiusDropdown — Pass 1: Interaction
 *
 * Method / property coverage:
 *   Group 1   ngOnInit / initForm — creates form with radius control
 *   Group 2   radius @Input setter — updates form control value after init
 *   Group 3   disabled @Input setter — enables / disables the radius control
 *   Group 4   max @Input setter — re-applies validators on form control
 *   Group 5   radiusChange emission — debounced emit after 500 ms (fake timers)
 */

import { UntypedFormGroup } from "@angular/forms";
import { makeComponent } from "./radius-dropdown.component.test-helpers";

// ---------------------------------------------------------------------------
// Group 1 — ngOnInit / initForm
// ---------------------------------------------------------------------------

describe("Group 1 — ngOnInit / initForm: creates form with radius control", () => {
   it("should create comp.form when none is provided", () => {
      const { comp } = makeComponent({ radius: 5, max: 100 });
      expect(comp.form).toBeInstanceOf(UntypedFormGroup);
   });

   it("should add a 'radius' control to the form", () => {
      const { comp } = makeComponent({ radius: 5, max: 100 });
      expect(comp.form.get("radius")).toBeTruthy();
   });

   it("should initialize the radius control value from the @Input radius", () => {
      const { comp } = makeComponent({ radius: 12, max: 100 });
      expect(comp.form.get("radius")?.value).toBe(12);
   });

   it("should use an externally provided form instead of creating a new one", () => {
      const externalForm = new UntypedFormGroup({});
      const { comp } = makeComponent({ radius: 5, max: 100, externalForm });
      expect(comp.form).toBe(externalForm);
   });

   it("should add radius control to the externally provided form", () => {
      const externalForm = new UntypedFormGroup({});
      makeComponent({ radius: 5, max: 100, externalForm });
      expect(externalForm.get("radius")).toBeTruthy();
   });
});

// ---------------------------------------------------------------------------
// Group 2 — radius @Input setter
// ---------------------------------------------------------------------------

describe("Group 2 — radius setter: updates form control value after init", () => {
   it("should set _radius before form exists (no-op on form)", () => {
      const { comp } = makeComponent({ skipNgOnInit: true });
      comp.radius = 8;
      expect(comp.radius).toBe(8);
      // form not yet created, no error
   });

   it("should update form control value when radius is set after ngOnInit", () => {
      const { comp } = makeComponent({ radius: 5, max: 100 });

      comp.radius = 20;

      expect(comp.form.get("radius")?.value).toBe(20);
   });

   it("should keep radius getter returning the latest value", () => {
      const { comp } = makeComponent({ radius: 5, max: 100 });
      comp.radius = 15;
      expect(comp.radius).toBe(15);
   });
});

// ---------------------------------------------------------------------------
// Group 3 — disabled @Input setter
// ---------------------------------------------------------------------------

describe("Group 3 — disabled setter: enables / disables the radius control", () => {
   it("should disable the radius control when disabled=true is set after ngOnInit", () => {
      const { comp } = makeComponent({ radius: 5, max: 100, disabled: false });
      comp.disabled = true;
      expect(comp.form.get("radius")?.disabled).toBe(true);
   });

   it("should enable the radius control when disabled=false is set", () => {
      const { comp } = makeComponent({ radius: 5, max: 100, disabled: true });
      comp.disabled = false;
      expect(comp.form.get("radius")?.enabled).toBe(true);
   });

   it("should disable the control when disabled=true is passed at init time", () => {
      const { comp } = makeComponent({ radius: 5, max: 100, disabled: true });
      expect(comp.form.get("radius")?.disabled).toBe(true);
   });

   it("should not throw when disabled is set before ngOnInit (no form yet)", () => {
      const { comp } = makeComponent({ skipNgOnInit: true });
      expect(() => (comp.disabled = true)).not.toThrow();
   });
});

// ---------------------------------------------------------------------------
// Group 4 — max @Input setter
// ---------------------------------------------------------------------------

describe("Group 4 — max setter: updates validators on the radius control", () => {
   it("should make form invalid when radius exceeds the new max", () => {
      const { comp } = makeComponent({ radius: 50, max: 100 });

      comp.max = 30;

      expect(comp.form.get("radius")?.invalid).toBe(true);
   });

   it("should make form valid when radius is within the new max", () => {
      const { comp } = makeComponent({ radius: 10, max: 100 });

      comp.max = 20;

      expect(comp.form.get("radius")?.valid).toBe(true);
   });

   it("should not throw when max is set before ngOnInit", () => {
      const { comp } = makeComponent({ skipNgOnInit: true });
      expect(() => (comp.max = 50)).not.toThrow();
   });
});

// ---------------------------------------------------------------------------
// Group 5 — radiusChange: debounced emit after 500 ms
// ---------------------------------------------------------------------------

describe("Group 5 — radiusChange: debounced emission via debounceTime(500)", () => {
   afterEach(() => vi.useRealTimers());

   it("should emit radiusChange after 500 ms when the value changes", () => {
      vi.useFakeTimers();
      const { comp } = makeComponent({ radius: 5, max: 100 });
      const emitSpy = vi.spyOn(comp.radiusChange, "emit");

      comp.form.get("radius")?.setValue(10);
      vi.advanceTimersByTime(500);

      expect(emitSpy).toHaveBeenCalledWith(10);
   });

   it("should NOT emit before 500 ms elapses", () => {
      vi.useFakeTimers();
      const { comp } = makeComponent({ radius: 5, max: 100 });
      const emitSpy = vi.spyOn(comp.radiusChange, "emit");

      comp.form.get("radius")?.setValue(10);
      vi.advanceTimersByTime(499);

      expect(emitSpy).not.toHaveBeenCalled();
   });

   it("should NOT emit when the new value equals the current radius", () => {
      vi.useFakeTimers();
      const { comp } = makeComponent({ radius: 5, max: 100 });
      const emitSpy = vi.spyOn(comp.radiusChange, "emit");

      comp.form.get("radius")?.setValue(5); // same as current
      vi.advanceTimersByTime(500);

      expect(emitSpy).not.toHaveBeenCalled();
   });

   it("should NOT emit when the new value is null", () => {
      vi.useFakeTimers();
      const { comp } = makeComponent({ radius: 5, max: 100 });
      const emitSpy = vi.spyOn(comp.radiusChange, "emit");

      comp.form.get("radius")?.setValue(null);
      vi.advanceTimersByTime(500);

      expect(emitSpy).not.toHaveBeenCalled();
   });

   it("should update comp.radius when emitting radiusChange", () => {
      vi.useFakeTimers();
      const { comp } = makeComponent({ radius: 5, max: 100 });

      comp.form.get("radius")?.setValue(25);
      vi.advanceTimersByTime(500);

      expect(comp.radius).toBe(25);
   });
});
