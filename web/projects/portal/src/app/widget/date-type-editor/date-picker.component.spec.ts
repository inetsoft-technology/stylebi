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

/**
 * DatePickerComponent: changing month or year while the day is 29-31 must clamp the day to
 * the last day of the target month instead of committing a rolled-over date (Bug #79749).
 *
 * Drives the real <select> change events: the options use [value], so the component receives
 * the month and year as strings.
 */
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { DateTypeFormatter } from "../../../../../shared/util/date-type-formatter";
import { TimeInstant } from "../../common/data/time-instant";
import { DatePickerComponent } from "./date-picker.component";

describe("DatePickerComponent month/year change clamps the day", () => {
   let fixture: ComponentFixture<DatePickerComponent>;
   let comp: DatePickerComponent;
   let emitted: string[];

   async function create(start: string): Promise<void> {
      fixture = TestBed.createComponent(DatePickerComponent);
      comp = fixture.componentInstance;
      comp.dateTime = DateTypeFormatter.toTimeInstant(start, DateTypeFormatter.ISO_8601_DATE_FORMAT);
      emitted = [];
      comp.onCommit.subscribe((value: string) => emitted.push(value));
      fixture.detectChanges();
      await fixture.whenStable();
   }

   async function choose(index: number, value: number): Promise<void> {
      const select: HTMLSelectElement = fixture.nativeElement.querySelectorAll("select")[index];
      select.value = String(value);
      select.dispatchEvent(new Event("change"));
      fixture.detectChanges();
      await fixture.whenStable();
   }

   const chooseMonth = (month: number) => choose(0, month);
   const chooseYear = (year: number) => choose(1, year);

   function expectState(committed: string, date: number): void {
      expect(emitted[emitted.length - 1]).toBe(committed);
      expect((comp.dateTime as TimeInstant).date).toBe(date);
   }

   beforeEach(() => {
      TestBed.configureTestingModule({ imports: [DatePickerComponent] });
   });

   it("should clamp Jan 31 to Feb 28 when the month changes to February", async () => {
      await create("2025-01-31");
      await chooseMonth(1);
      expectState("2025-02-28", 28);
      expect(comp.dateTime.months).toBe(1);
   });

   it("should clamp Feb 29 to Feb 28 when the year changes to a non-leap year", async () => {
      await create("2024-02-29");
      await chooseYear(2025);
      expectState("2025-02-28", 28);
      expect(comp.dateTime.years).toBe(2025);
   });

   it("should clamp Mar 31 to Apr 30 when the month changes to April", async () => {
      await create("2025-03-31");
      await chooseMonth(3);
      expectState("2025-04-30", 30);
   });

   it("should keep Feb 29 when the year changes to another leap year", async () => {
      await create("2024-02-29");
      await chooseYear(2028);
      expectState("2028-02-29", 29);
   });

   it("should keep a day that exists in the target month (Jan 15 to Feb)", async () => {
      await create("2025-01-15");
      await chooseMonth(1);
      expectState("2025-02-15", 15);
   });

   it("should keep day 31 when the target month has 31 days (Jan 31 to Mar)", async () => {
      await create("2025-01-31");
      await chooseMonth(2);
      expectState("2025-03-31", 31);
   });

   it("should leave the day at 28 on a later move to March (Jan 31 to Feb to Mar)", async () => {
      await create("2025-01-31");
      await chooseMonth(1);
      await chooseMonth(2);
      expectState("2025-03-28", 28);
   });
});
