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

import { TestBed } from "@angular/core/testing";
import { DateValueEditorComponent } from "./date-value-editor.component";
import { stubWestOfUtcDate } from "./west-of-utc-date.test-helpers";

/**
 * Bug #78730: the [model] setter read "yyyy-MM-dd" with new Date(string), which is UTC midnight,
 * and took its local year/month/day, the previous day west of UTC. It then propagated that day,
 * and a host that writes the value back to [model] lost one more day on every pass.
 *
 * Each case runs with the real Date (fails on the old code only when run west of UTC) and with a
 * Date stub that acts west of UTC in any zone, so the CI run in UTC still catches the bug.
 */
const ZONES: [string, () => void][] = [
   ["the real time zone", () => {}],
   ["a west-of-UTC Date", stubWestOfUtcDate],
];

describe.each(ZONES)("DateValueEditorComponent model setter with %s", (_name, setUpZone) => {
   let comp: DateValueEditorComponent;
   let onChange: ReturnType<typeof vi.fn>;

   beforeEach(() => {
      setUpZone();
      TestBed.configureTestingModule({ imports: [DateValueEditorComponent] });
      comp = TestBed.createComponent(DateValueEditorComponent).componentInstance;
      onChange = vi.fn();
      comp.registerOnChange(onChange);
   });

   afterEach(() => {
      vi.unstubAllGlobals();
   });

   // the host writes each propagated value back to [model], as the auto-drill dialog does
   function reflect(value: string, passes: number = 4): void {
      for(let i = 0; i < passes; i++) {
         comp.model = onChange.mock.calls.length ? onChange.mock.lastCall[0] : value;
      }
   }

   it("reads a date-only model as that local date", () => {
      comp.model = "2026-05-06";

      expect(comp.date).toEqual({ year: 2026, month: 5, day: 6 });
      expect(comp.initDate).toEqual({ year: 2026, month: 5, day: 6 });
   });

   it("leaves a saved date unchanged when the same model is set", () => {
      comp.writeValue("2026-03-04");

      reflect("2026-03-04");

      expect(comp.date).toEqual({ year: 2026, month: 3, day: 4 });
      expect(onChange).not.toHaveBeenCalled();
   });

   it("keeps a typed date when it is reflected back over several passes", () => {
      comp.writeValue("2026-03-04");
      comp.change("2026-05-06");
      expect(onChange).toHaveBeenLastCalledWith("2026-05-06");

      reflect("2026-05-06");

      expect(comp.date).toEqual({ year: 2026, month: 5, day: 6 });
      expect(onChange).toHaveBeenCalledTimes(1);
   });

   it("keeps a picked date when it is reflected back over several passes", () => {
      comp.writeValue("2026-03-04");
      comp.changeDate({ year: 2026, month: 3, day: 10 });
      expect(onChange).toHaveBeenLastCalledWith("2026-03-10");

      reflect("2026-03-10");

      expect(comp.date).toEqual({ year: 2026, month: 3, day: 10 });
      expect(onChange).toHaveBeenCalledTimes(1);
   });

   it.each(["", undefined, null, "garbage"])(
      "leaves the date unchanged when the model is set to %p", (model: string) => {
         comp.writeValue("2026-03-04");

         comp.model = model;

         expect(comp.date).toEqual({ year: 2026, month: 3, day: 4 });
         expect(comp.initDate).toEqual({ year: 2026, month: 3, day: 4 });
         expect(onChange).not.toHaveBeenCalled();
      });
});
