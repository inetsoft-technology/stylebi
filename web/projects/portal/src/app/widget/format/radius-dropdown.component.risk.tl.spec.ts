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
 * RadiusDropdown — Pass 2: Risk / Async
 *
 * Risk-first coverage:
 *   Group 1   Validator: positiveIntegerInRange — rejects negative, zero, fractions
 *   Group 2   Validator: max — rejects values above configured max
 *   Group 3   Validator: requiredNumber — rejects null / empty
 *   Group 4   radiusChange NOT emitted on empty string (edge guard in subscription)
 *   Group 5   Debounce reset — rapid changes only emit once (last value, fake timers)
 *   Group 6   updateRadiusStatus symmetry — disable then enable leaves control enabled
 *   Group 7   radius setter before form init does not throw or write to form
 */

import { makeComponent } from "./radius-dropdown.component.test-helpers";

// ---------------------------------------------------------------------------
// Group 1 — Validator: positiveIntegerInRange
// ---------------------------------------------------------------------------

describe("Group 1 — Validator: positiveIntegerInRange rejects non-positive integers", () => {
   it("should be valid for a positive integer radius", () => {
      const { comp } = makeComponent({ radius: 5, max: 100 });
      expect(comp.form.get("radius")?.valid).toBe(true);
   });

   it("should be invalid for radius=0", () => {
      const { comp } = makeComponent({ radius: 5, max: 100 });
      comp.form.get("radius")?.setValue(0);
      expect(comp.form.get("radius")?.invalid).toBe(true);
   });

   it("should be invalid for a negative radius", () => {
      const { comp } = makeComponent({ radius: 5, max: 100 });
      comp.form.get("radius")?.setValue(-1);
      expect(comp.form.get("radius")?.invalid).toBe(true);
   });

   it("should be invalid for a fractional radius (1.5)", () => {
      const { comp } = makeComponent({ radius: 5, max: 100 });
      comp.form.get("radius")?.setValue(1.5);
      expect(comp.form.get("radius")?.invalid).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 2 — Validator: max
// ---------------------------------------------------------------------------

describe("Group 2 — Validator: max rejects values above configured max", () => {
   it("should be valid when radius equals max", () => {
      const { comp } = makeComponent({ radius: 50, max: 50 });
      expect(comp.form.get("radius")?.valid).toBe(true);
   });

   it("should be invalid when radius exceeds max", () => {
      const { comp } = makeComponent({ radius: 51, max: 50 });
      expect(comp.form.get("radius")?.invalid).toBe(true);
   });

   it("should be valid after max is raised to accommodate current radius", () => {
      const { comp } = makeComponent({ radius: 80, max: 100 });
      comp.form.get("radius")?.setValue(90);
      comp.max = 100;
      expect(comp.form.get("radius")?.valid).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 3 — Validator: requiredNumber
// ---------------------------------------------------------------------------

describe("Group 3 — Validator: requiredNumber rejects null", () => {
   it("should be invalid when radius control value is null", () => {
      const { comp } = makeComponent({ radius: 5, max: 100 });
      comp.form.get("radius")?.setValue(null);
      expect(comp.form.get("radius")?.invalid).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 4 — radiusChange NOT emitted for empty string
// ---------------------------------------------------------------------------

describe("Group 4 — emission guard: not emitted when radius+'' == ''", () => {
   afterEach(() => vi.useRealTimers());

   it("should NOT emit radiusChange when value coerces to empty string", () => {
      vi.useFakeTimers();
      const { comp } = makeComponent({ radius: 5, max: 100 });
      const emitSpy = vi.spyOn(comp.radiusChange, "emit");

      // The subscription guard: `radius + "" != ""` — an empty string would fail
      // Setting the control to the value "" simulates this edge
      comp.form.get("radius")?.setValue("");
      vi.advanceTimersByTime(500);

      expect(emitSpy).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 5 — Debounce reset: rapid changes emit only once (last value)
// ---------------------------------------------------------------------------

describe("Group 5 — debounce reset: rapid successive values emit only the last one", () => {
   afterEach(() => vi.useRealTimers());

   it("should emit only the final value when multiple values are set within the debounce window", () => {
      vi.useFakeTimers();
      const { comp } = makeComponent({ radius: 5, max: 100 });
      const emitSpy = vi.spyOn(comp.radiusChange, "emit");

      comp.form.get("radius")?.setValue(10);
      vi.advanceTimersByTime(300);
      comp.form.get("radius")?.setValue(20);
      vi.advanceTimersByTime(300);
      comp.form.get("radius")?.setValue(30);
      vi.advanceTimersByTime(500);

      // Debounce window is reset each time; only the final value should emit
      expect(emitSpy).toHaveBeenCalledOnce();
      expect(emitSpy).toHaveBeenCalledWith(30);
   });
});

// ---------------------------------------------------------------------------
// Group 6 — updateRadiusStatus: disable then enable
// ---------------------------------------------------------------------------

describe("Group 6 — updateRadiusStatus: symmetric enable/disable", () => {
   it("should leave the control enabled after disable → enable round-trip", () => {
      const { comp } = makeComponent({ radius: 5, max: 100, disabled: false });
      comp.disabled = true;
      comp.disabled = false;
      expect(comp.form.get("radius")?.enabled).toBe(true);
   });

   it("should leave the control disabled after enable → disable round-trip", () => {
      const { comp } = makeComponent({ radius: 5, max: 100, disabled: true });
      comp.disabled = false;
      comp.disabled = true;
      expect(comp.form.get("radius")?.disabled).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 7 — radius setter safety before init
// ---------------------------------------------------------------------------

describe("Group 7 — radius setter before ngOnInit: no form interaction, no throw", () => {
   it("should set _radius without error before form exists", () => {
      const { comp } = makeComponent({ skipNgOnInit: true });
      expect(() => (comp.radius = 42)).not.toThrow();
      expect(comp.radius).toBe(42);
   });

   it("should have correct initial form value after ngOnInit when radius was set earlier", () => {
      const { comp } = makeComponent({ skipNgOnInit: true });
      comp.radius = 7;
      comp.max = 100;
      comp.ngOnInit();
      expect(comp.form.get("radius")?.value).toBe(7);
   });
});
