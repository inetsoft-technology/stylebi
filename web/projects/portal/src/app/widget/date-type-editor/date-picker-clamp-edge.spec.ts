/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
import { DateTypeFormatter } from "../../../../../shared/util/date-type-formatter";
import { DatePickerComponent } from "./date-picker.component";
import { DateTimePickerComponent } from "./date-time-picker.component";

describe("Bug #79749 month/year clamp edge cases and date-time-picker host", () => {
   beforeEach(() => TestBed.configureTestingModule({ imports: [DatePickerComponent, DateTimePickerComponent] }));

   async function pick(fixture: any, idx: number, v: number) {
      const s: HTMLSelectElement = fixture.nativeElement.querySelectorAll("select")[idx];
      s.value = String(v);
      s.dispatchEvent(new Event("change"));
      fixture.detectChanges();
      await fixture.whenStable();
   }

   async function dp(start: string) {
      const f = TestBed.createComponent(DatePickerComponent);
      f.componentInstance.dateTime = DateTypeFormatter.toTimeInstant(start, DateTypeFormatter.ISO_8601_DATE_FORMAT);
      const out: string[] = [];
      f.componentInstance.onCommit.subscribe((x: string) => out.push(x));
      f.detectChanges(); await f.whenStable();
      return {f, out};
   }

   const cases: [string, number | null, number | null, string][] = [
      ["2025-12-31", 10, null, "2025-11-30"],
      ["2024-03-31", 1, null, "2024-02-29"],
      ["2024-03-30", 1, null, "2024-02-29"],
      ["2025-03-30", 1, null, "2025-02-28"],
      ["2024-02-29", null, 2028, "2028-02-29"],
      ["2024-02-29", null, 2025, "2025-02-28"],
      ["2024-07-31", null, 2030, "2030-07-31"],
      ["2024-05-31", 5, null, "2024-06-30"],
      ["2024-01-31", 3, null, "2024-04-30"],
      ["2024-10-31", 8, null, "2024-09-30"],
      ["2000-02-29", null, 2100, "2100-02-28"],
      ["2025-01-31", 11, null, "2025-12-31"],
   ];
   for(const [start, m, y, exp] of cases) {
      it(`${start} m=${m} y=${y} -> ${exp}`, async () => {
         const {f, out} = await dp(start);
         if(m !== null) { await pick(f, 0, m); }
         if(y !== null) { await pick(f, 1, y); }
         expect(out[out.length - 1]).toBe(exp);
      });
   }

   it("date-time-picker host: time change after clamp re-emits clamped date", async () => {
      const f = TestBed.createComponent(DateTimePickerComponent);
      const c = f.componentInstance;
      c.promptTime = true;
      c.format = DateTypeFormatter.ISO_8601_TIME_INSTANT_FORMAT;
      c.date = "2025-01-31T10:20:30";
      const out: string[] = [];
      c.onCommit.subscribe((x: string) => out.push(x));
      f.detectChanges(); await f.whenStable();
      await pick(f, 0, 1);
      expect(out[out.length - 1]).toBe("2025-02-28T10:20:30");
      c.timeChange("11:22:33");
      expect(out[out.length - 1]).toBe("2025-02-28T11:22:33");
      expect(c.dateTime.date).toBe(28);
   });
});
