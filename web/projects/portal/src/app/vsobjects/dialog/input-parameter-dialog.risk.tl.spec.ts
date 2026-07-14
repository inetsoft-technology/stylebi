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
 * InputParameterDialog — Pass 2: Risk
 *
 * Covers the items explicitly deferred from Pass 1 (see that file's header):
 *   - timeValue debounce (1000ms) + distinctUntilChanged race/dedup behavior.
 *   - concurrent updateDate/updateTime/updateDateTime sequencing for TIME_INSTANT,
 *     including the "1970-01-01"/"00:00:00" fallback defaults.
 *   - model setter's guarded form-sync when the form doesn't exist yet.
 */

import { XSchema } from "../../common/data/xschema";
import { createComponent, makeModel } from "./input-parameter-dialog.component.test-helpers";

afterEach(() => {
   vi.restoreAllMocks();
   vi.useRealTimers();
});

// ---------------------------------------------------------------------------
// Group 1: timeValue debounce + distinctUntilChanged [Risk 3]
// ---------------------------------------------------------------------------

describe("InputParameterDialog — timeValue debounce", () => {
   it("should reset the debounce window on every keystroke, only firing 1000ms after the last one", () => {
      vi.useFakeTimers();
      const { comp } = createComponent({ model: makeModel({ type: XSchema.TIME }) });
      comp.ngOnInit();
      const updateTimeSpy = vi.spyOn(comp, "updateTime");

      comp.form.controls["timeValue"].setValue("10:3");
      vi.advanceTimersByTime(600);
      comp.form.controls["timeValue"].setValue("10:30");
      vi.advanceTimersByTime(600);

      expect(updateTimeSpy).not.toHaveBeenCalled(); // second edit reset the 1000ms window

      vi.advanceTimersByTime(400);

      expect(updateTimeSpy).toHaveBeenCalledTimes(1);
      expect(updateTimeSpy).toHaveBeenCalledWith("10:30");
   });

   it("should NOT re-fire for a debounced value that is unchanged from the last emitted one", () => {
      vi.useFakeTimers();
      const { comp } = createComponent({ model: makeModel({ type: XSchema.TIME }) });
      comp.ngOnInit();
      comp.form.controls["timeValue"].setValue("10:30:00");
      vi.advanceTimersByTime(1000);
      const updateTimeSpy = vi.spyOn(comp, "updateTime");

      comp.form.controls["timeValue"].setValue("10:30:00"); // same value re-applied
      vi.advanceTimersByTime(1000);

      expect(updateTimeSpy).not.toHaveBeenCalled();
   });

   it("should fire again once the debounced value actually changes", () => {
      vi.useFakeTimers();
      const { comp } = createComponent({ model: makeModel({ type: XSchema.TIME }) });
      comp.ngOnInit();
      comp.form.controls["timeValue"].setValue("10:30:00");
      vi.advanceTimersByTime(1000);
      const updateTimeSpy = vi.spyOn(comp, "updateTime");

      comp.form.controls["timeValue"].setValue("11:45:00");
      vi.advanceTimersByTime(1000);

      expect(updateTimeSpy).toHaveBeenCalledWith("11:45:00");
   });
});

// ---------------------------------------------------------------------------
// Group 2: updateDate / updateTime / updateDateTime sequencing [Risk 3]
// ---------------------------------------------------------------------------

describe("InputParameterDialog — updateDate/updateTime/updateDateTime sequencing", () => {
   it("should recompute the DATE model value from the stored date struct", () => {
      const { comp, ngbDateParserFormatter } = createComponent({ model: makeModel({ type: XSchema.DATE }) });
      ngbDateParserFormatter.format.mockReturnValue("2024-03-15");

      comp.updateDate({ year: 2024, month: 3, day: 15 });

      expect(ngbDateParserFormatter.format).toHaveBeenCalledWith({ year: 2024, month: 3, day: 15 });
      expect(comp.model.value).toBe("2024-03-15");
   });

   it("should recompute the TIME model value from the transformed time string", () => {
      const { comp } = createComponent({ model: makeModel({ type: XSchema.TIME }) });

      comp.updateTime("10:30:00");

      expect(comp.model.value).toBe("10:30:00");
   });

   it("should combine the date-only default with a real time for TIME_INSTANT when only time has been set", () => {
      const { comp } = createComponent({ model: makeModel({ type: XSchema.TIME_INSTANT }) });
      comp.date = null;

      comp.updateTime("10:30:00");

      expect(comp.model.value).toBe("1970-01-01 10:30:00");
   });

   it("should combine a real date with the time-only default for TIME_INSTANT when only date has been set", () => {
      const { comp, ngbDateParserFormatter } = createComponent({ model: makeModel({ type: XSchema.TIME_INSTANT }) });
      comp.time = null;
      ngbDateParserFormatter.format.mockReturnValue("2024-03-15");

      comp.updateDate({ year: 2024, month: 3, day: 15 });

      expect(comp.model.value).toBe("2024-03-15 00:00:00");
   });

   it("should combine both real date and time once both have been set in sequence", () => {
      const { comp, ngbDateParserFormatter } = createComponent({ model: makeModel({ type: XSchema.TIME_INSTANT }) });
      ngbDateParserFormatter.format.mockReturnValue("2024-03-15");

      comp.updateDate({ year: 2024, month: 3, day: 15 });
      comp.updateTime("10:30:00");

      expect(comp.model.value).toBe("2024-03-15 10:30:00");
   });

   it("should leave the model value untouched for a type that isn't DATE/TIME/TIME_INSTANT", () => {
      const { comp } = createComponent({ model: makeModel({ type: XSchema.STRING, value: "unchanged" }) });

      comp.updateDateTime();

      expect(comp.model.value).toBe("unchanged");
   });
});

// ---------------------------------------------------------------------------
// Group 3: model setter guard when the form does not exist yet [Risk 2]
// ---------------------------------------------------------------------------

describe("InputParameterDialog — model setter before ngOnInit", () => {
   it("should not crash and should not attempt to touch form controls before the form exists", () => {
      const { comp } = createComponent();
      expect(comp.form).toBeNull();

      expect(() => comp.model = makeModel({ name: "para1", type: XSchema.INTEGER, value: "5" })).not.toThrow();
      expect(comp.model.name).toBe("para1");
   });

   it("should skip the guarded form-sync block without crashing while still running the unguarded date-parsing step", () => {
      // Distinct from the interaction-spec DATE test (form present): this proves the
      // `this._model && this.form` guard on the form-control-sync block is what's actually
      // skipping that code when form is null — not that parsing "just happens to work" — by
      // asserting form stays null (never touched) alongside the parse result.
      const { comp, ngbDateParserFormatter } = createComponent();
      expect(comp.form).toBeNull();

      comp.model = makeModel({ type: XSchema.DATE, value: "2024-03-15" });

      expect(ngbDateParserFormatter.parse).toHaveBeenCalledWith("2024-03-15");
      expect(comp.date).toEqual({ year: 2024, month: 3, day: 15 });
      expect(comp.form).toBeNull();
   });
});
