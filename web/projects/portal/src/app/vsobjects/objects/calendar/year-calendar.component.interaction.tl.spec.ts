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
 * YearCalendar – Pass 1: Interaction
 *
 * Coverage:
 *   Group 1  - ngOnChanges guards: selectedRow/Col-only, no-model change ignored
 *   Group 2  - ngAfterViewInit: inited flag, detectChanges call
 *   Group 3  - nextYear: inc/dec, applyCalendar vs selectedDatesChange+titleChanged, syncDateChange guard
 *   Group 4  - clickCell: formatPainterMode guard, vsWizard guard, month-type selection,
 *                          range-mode plain click, ctrl+click deselect, shift-click range, syncPeriods
 *   Group 5  - clickYearTitle: selects current year; ctrl+click deselects
 *   Group 6  - clearSelection: empties dates and selectedMonth
 *   Group 7  - syncDate: direction determined by secondCalendar flag
 *   Group 8  - syncPeriod: year-type values, month-type values, empty values
 *   Group 9  - paintRange: empty guard, single-value (primary), multi-value range, otherCalendarEmpty
 *   Group 10 - updateSelected: syncRanges path (doubleCalendar range mode), direct selectedMonth mark
 */

import { SimpleChange } from "@angular/core";
import { createYearCalendar } from "./year-calendar.component.test-helpers";

// ─── helpers ────────────────────────────────────────────────────────────────

function makeClick(opts: { ctrlKey?: boolean; metaKey?: boolean; shiftKey?: boolean } = {}): MouseEvent {
   return {
      ctrlKey: opts.ctrlKey ?? false,
      metaKey: opts.metaKey ?? false,
      shiftKey: opts.shiftKey ?? false,
      stopPropagation: vi.fn(),
      preventDefault: vi.fn(),
   } as any;
}

// ─── Tests ──────────────────────────────────────────────────────────────────

describe("YearCalendar – interaction", () => {
   afterEach(() => {
      vi.restoreAllMocks();
   });

   // ─── Group 1 – ngOnChanges guards ───────────────────────────────────────

   describe("Group 1 - ngOnChanges guards", () => {
      it("should ignore a change set with only selectedRow", () => {
         const { comp } = createYearCalendar();
         const yearBefore = comp.currentDate.year;

         comp.ngOnChanges({ selectedRow: new SimpleChange(undefined, 1, false) } as any);

         expect(comp.currentDate.year).toBe(yearBefore);
      });

      it("should ignore a change set with only selectedRow and selectedCol", () => {
         const { comp } = createYearCalendar();
         const yearBefore = comp.currentDate.year;

         comp.ngOnChanges({
            selectedRow: new SimpleChange(undefined, 1, false),
            selectedCol: new SimpleChange(undefined, 0, false),
         } as any);

         expect(comp.currentDate.year).toBe(yearBefore);
      });

      it("should NOT re-initialise on a change set that has no model key", () => {
         const { comp } = createYearCalendar();
         const yearBefore = comp.currentDate.year;
         // YearCalendar.ngOnChanges only enters its body when changes["model"] is present
         comp.ngOnChanges({ selected: new SimpleChange(false, true, false) } as any);
         expect(comp.currentDate.year).toBe(yearBefore);
      });

      it("should initialise currentDate from model.currentDate1 when secondCalendar=false", () => {
         const { comp } = createYearCalendar({ model: { currentDate1: { year: 2020, month: 0 } } });
         expect(comp.currentDate.year).toBe(2020);
      });

      it("should initialise currentDate from model.currentDate2 when secondCalendar=true", () => {
         const { comp } = createYearCalendar({
            secondCalendar: true,
            model: { currentDate2: { year: 2022, month: 0 } },
         });
         expect(comp.currentDate.year).toBe(2022);
      });

      it("should always reset currentDate.month to 0 in YearCalendar", () => {
         const { comp } = createYearCalendar({ model: { currentDate1: { year: 2025, month: 5 } } });
         // ngOnChanges does: this.currentDate.month = 0
         expect(comp.currentDate.month).toBe(0);
      });
   });

   // ─── Group 2 – ngAfterViewInit ───────────────────────────────────────────

   describe("Group 2 - ngAfterViewInit", () => {
      it("should set inited=true", () => {
         const { comp } = createYearCalendar();
         expect(comp.inited).toBeFalsy();

         comp.ngAfterViewInit();

         expect(comp.inited).toBe(true);
      });

      it("should call changeDetectorRef.detectChanges once", () => {
         const { comp, changeDetectorRef } = createYearCalendar();
         changeDetectorRef.detectChanges.mockClear();

         comp.ngAfterViewInit();

         expect(changeDetectorRef.detectChanges).toHaveBeenCalledTimes(1);
      });
   });

   // ─── Group 3 – nextYear ──────────────────────────────────────────────────

   describe("Group 3 - nextYear", () => {
      it("should increment year by 1", () => {
         const { comp } = createYearCalendar({ model: { doubleCalendar: false, submitOnChange: false } });
         const before = comp.currentDate.year;
         comp.nextYear(1);
         expect(comp.currentDate.year).toBe(before + 1);
      });

      it("should decrement year by 1", () => {
         const { comp } = createYearCalendar({ model: { doubleCalendar: false, submitOnChange: false } });
         const before = comp.currentDate.year;
         comp.nextYear(-1);
         expect(comp.currentDate.year).toBe(before - 1);
      });

      it("should update all existing dates to the new year", () => {
         const { comp } = createYearCalendar({ model: { doubleCalendar: false, submitOnChange: false } });
         comp.dates = [{ year: 2025, month: 3, dateType: "m", value: 0 }] as any;
         comp.nextYear(1);
         expect(comp.dates[0].year).toBe(2026);
      });

      it("should emit applyCalendar when !doubleCalendar && submitOnChange", () => {
         const { comp } = createYearCalendar({ model: { doubleCalendar: false, submitOnChange: true } });
         const applied: void[] = [];
         comp.applyCalendar.subscribe(() => applied.push(void 0));

         comp.nextYear(1);

         expect(applied.length).toBe(1);
      });

      it("should emit titleChanged but not applyCalendar when doubleCalendar", () => {
         const { comp } = createYearCalendar({ model: { doubleCalendar: true, submitOnChange: true } });
         const applied: void[] = [];
         const titled: boolean[] = [];
         comp.applyCalendar.subscribe(() => applied.push(void 0));
         comp.titleChanged.subscribe((v) => titled.push(v));

         comp.nextYear(1);

         expect(applied.length).toBe(0);
         expect(titled.length).toBe(1);
         expect(titled[0]).toBe(false); // secondCalendar=false
      });

      it("should emit syncDateChange when doubleCalendar && !period && !calledOnSync", () => {
         const { comp } = createYearCalendar({ model: { doubleCalendar: true, period: false } });
         const synced: boolean[] = [];
         comp.syncDateChange.subscribe((v) => synced.push(v));

         comp.nextYear(1);

         expect(synced.length).toBe(1);
      });

      it("should NOT emit syncDateChange when calledOnSync=true", () => {
         const { comp } = createYearCalendar({ model: { doubleCalendar: true, period: false } });
         const synced: boolean[] = [];
         comp.syncDateChange.subscribe((v) => synced.push(v));

         comp.nextYear(1, true);

         expect(synced.length).toBe(0);
      });

      it("should do nothing when formatPainterMode is true", () => {
         const { comp } = createYearCalendar();
         comp.formatPainterMode = true;
         const before = comp.currentDate.year;

         comp.nextYear(1);

         expect(comp.currentDate.year).toBe(before);
      });
   });

   // ─── Group 4 – clickCell ────────────────────────────────────────────────

   describe("Group 4 - clickCell", () => {
      it("should do nothing when formatPainterMode is true", () => {
         const { comp } = createYearCalendar();
         comp.formatPainterMode = true;
         comp.clickCell(0, 0, makeClick());
         expect(comp.dates.length).toBe(0);
      });

      it("should do nothing when vsWizard is true", () => {
         const { comp } = createYearCalendar({ contextProvider: { vsWizard: true } });
         comp.clickCell(0, 0, makeClick());
         expect(comp.dates.length).toBe(0);
      });

      it("should create a month-type selection for the clicked cell (row=0,col=0 → month=0)", () => {
         const { comp } = createYearCalendar({
            model: { submitOnChange: false, doubleCalendar: false },
         });
         // month = row*4 + col = 0*4+0 = 0
         comp.clickCell(0, 0, makeClick());

         expect(comp.dates.length).toBe(1);
         expect(comp.dates[0].dateType).toBe("m");
         expect(comp.dates[0].month).toBe(0);
         expect(comp.dates[0].year).toBe(comp.currentDate.year);
      });

      it("should compute correct month index from row*4+col (row=1,col=2 → month=6)", () => {
         const { comp } = createYearCalendar({
            model: { submitOnChange: false, doubleCalendar: false },
         });
         comp.clickCell(1, 2, makeClick());
         expect(comp.dates[0].month).toBe(6);
      });

      it("should emit applyCalendar when !doubleCalendar && submitOnChange", () => {
         const { comp } = createYearCalendar({ model: { submitOnChange: true, doubleCalendar: false } });
         const applied: void[] = [];
         comp.applyCalendar.subscribe(() => applied.push(void 0));

         comp.clickCell(0, 0, makeClick());

         expect(applied.length).toBe(1);
      });

      it("should remove an already-selected month on ctrl+click (deselect)", () => {
         const { comp } = createYearCalendar({
            model: { submitOnChange: false, doubleCalendar: false },
         });
         // First click: select month 0
         comp.clickCell(0, 0, makeClick());
         expect(comp.dates.length).toBe(1);

         // Ctrl+click on same cell: deselect
         comp.clickCell(0, 0, makeClick({ ctrlKey: true }));
         expect(comp.dates.length).toBe(0);
      });

      it("should emit syncPeriods when doubleCalendar=true && period=true && inited=true", () => {
         const { comp } = createYearCalendar({ model: { doubleCalendar: true, period: true } });
         comp.inited = true;
         const synced: boolean[] = [];
         comp.syncPeriods.subscribe((v) => synced.push(v));

         comp.clickCell(0, 0, makeClick());

         expect(synced.length).toBe(1);
      });

      it("should shift-click to range-select all months between first and clicked", () => {
         const { comp } = createYearCalendar({
            model: { submitOnChange: false, doubleCalendar: false },
         });
         // Select month 2 first
         comp.clickCell(0, 2, makeClick());
         expect(comp.dates.length).toBe(1);

         // Shift+click on month 5 (row=1, col=1): selects months 2..5
         comp.clickCell(1, 1, makeClick({ shiftKey: true }));
         expect(comp.dates.length).toBe(4); // months 2,3,4,5
      });
   });

   // ─── Group 5 – clickYearTitle ────────────────────────────────────────────

   describe("Group 5 - clickYearTitle", () => {
      it("should select the current year as a year-type entry", () => {
         const { comp } = createYearCalendar({ model: { submitOnChange: false } });

         comp.clickYearTitle(makeClick());

         expect(comp.dates.length).toBe(1);
         expect(comp.dates[0].dateType).toBe("y");
         expect(comp.dates[0].year).toBe(comp.currentDate.year);
      });

      it("should deselect the year on ctrl+click when it is already selected", () => {
         const { comp } = createYearCalendar({ model: { submitOnChange: false } });
         comp.clickYearTitle(makeClick());
         expect(comp.dates.length).toBe(1);

         // Ctrl+click deselects
         comp.clickYearTitle(makeClick({ ctrlKey: true }));
         expect(comp.dates.length).toBe(0);
      });

      it("should emit applyCalendar when !doubleCalendar && submitOnChange", () => {
         const { comp } = createYearCalendar({ model: { submitOnChange: true, doubleCalendar: false } });
         const applied: void[] = [];
         comp.applyCalendar.subscribe(() => applied.push(void 0));

         comp.clickYearTitle(makeClick());

         expect(applied.length).toBe(1);
      });

      it("should do nothing when formatPainterMode is true", () => {
         const { comp } = createYearCalendar();
         comp.formatPainterMode = true;

         comp.clickYearTitle(makeClick());

         expect(comp.dates.length).toBe(0);
      });
   });

   // ─── Group 6 – clearSelection ────────────────────────────────────────────

   describe("Group 6 - clearSelection", () => {
      it("should empty dates and selectedMonth", () => {
         const { comp } = createYearCalendar({ model: { submitOnChange: false } });
         comp.dates = [{ year: 2025, month: 0, dateType: "m", value: 0 }] as any;
         comp.selectedMonth = [true, false, true];

         comp.clearSelection();

         expect(comp.dates.length).toBe(0);
         expect(comp.selectedMonth.length).toBe(0);
      });
   });

   // ─── Group 7 – syncDate ──────────────────────────────────────────────────

   describe("Group 7 - syncDate", () => {
      it("should decrement year for the primary calendar (secondCalendar=false)", () => {
         const { comp } = createYearCalendar({ model: { doubleCalendar: true, submitOnChange: false } });
         // syncDate always calls nextYear; secondCalendar=false → nextYear(-1, true)
         const before = comp.currentDate.year;
         comp.syncDate(true);
         expect(comp.currentDate.year).toBe(before - 1);
      });

      it("should increment year for the secondary calendar (secondCalendar=true)", () => {
         const { comp } = createYearCalendar({
            secondCalendar: true,
            model: { doubleCalendar: true, submitOnChange: false },
         });
         // secondCalendar=true → nextYear(1, true)
         const before = comp.currentDate.year;
         comp.syncDate(true);
         expect(comp.currentDate.year).toBe(before + 1);
      });
   });

   // ─── Group 8 – syncPeriod ────────────────────────────────────────────────

   describe("Group 8 - syncPeriod", () => {
      it("should do nothing when values is empty", () => {
         const { comp } = createYearCalendar();
         comp.syncPeriod([]);
         expect(comp.dates.length).toBe(0);
      });

      it("should replace dates with a single currentYear entry when values[0].dateType='y'", () => {
         const { comp } = createYearCalendar({ model: { submitOnChange: false } });
         const values = [{ year: 2020, dateType: "y", month: 0, value: 0 }] as any;

         comp.syncPeriod(values);

         expect(comp.dates.length).toBe(1);
         expect(comp.dates[0].dateType).toBe("y");
         expect(comp.dates[0].year).toBe(comp.currentDate.year);
      });

      it("should not call updateSelected when onApply=true for year-type sync", () => {
         // onApply=false path (updateSelected called) is covered by test 2 above
         const { comp } = createYearCalendar();
         const updateSpy = vi.spyOn(comp, "updateSelected");
         const values = [{ year: 2020, dateType: "y", month: 0, value: 0 }] as any;

         comp.syncPeriod(values, true); // onApply=true

         expect(updateSpy).not.toHaveBeenCalled();
      });

      it("should extend dates to match values length when values contain month-type selections", () => {
         const { comp } = createYearCalendar({ model: { submitOnChange: false } });
         const values = [
            { year: 2025, month: 2, dateType: "m", value: 0 },
            { year: 2025, month: 5, dateType: "m", value: 0 },
         ] as any;

         comp.syncPeriod(values);

         expect(comp.dates.length).toBe(2);
         expect(comp.dates[0].dateType).toBe("m");
      });
   });

   // ─── Group 9 – paintRange ────────────────────────────────────────────────

   describe("Group 9 - paintRange", () => {
      it("should return early without modifying selectedMonth when values is empty", () => {
         const { comp } = createYearCalendar();
         comp.selectedMonth = [];

         comp.paintRange([]);

         expect(comp.selectedMonth.length).toBe(0);
      });

      it("should mark months from first.month to end (11) for primary calendar on single value", () => {
         const { comp } = createYearCalendar();
         // secondCalendar=false, first.month=3: start=3, end=12
         const values = [{ year: 2025, month: 3, dateType: "m", value: 0 }] as any;

         comp.paintRange(values);

         // selectedMonth[3..11] should be true
         for(let i = 3; i <= 11; i++) {
            expect(comp.selectedMonth[i]).toBe(true);
         }
         expect(comp.selectedMonth[2]).toBeFalsy();
      });

      it("should mark months 0 to end for secondary calendar on single value", () => {
         const { comp } = createYearCalendar({ secondCalendar: true });
         // secondCalendar=true, first.month=5: start=0, end=5+1=6
         const values = [{ year: 2025, month: 5, dateType: "m", value: 0 }] as any;

         comp.paintRange(values);

         for(let i = 0; i <= 5; i++) {
            expect(comp.selectedMonth[i]).toBe(true);
         }
         expect(comp.selectedMonth[6]).toBeFalsy();
      });

      it("should mark the range between first and last months for multi-value input", () => {
         const { comp } = createYearCalendar();
         const values = [
            { year: 2025, month: 2, dateType: "m", value: 0 },
            { year: 2025, month: 7, dateType: "m", value: 0 },
         ] as any;

         comp.paintRange(values);

         // start=min(2,7)=2, end=max(2,7)+1=8 → selectedMonth[2..7] true
         for(let i = 2; i <= 7; i++) {
            expect(comp.selectedMonth[i]).toBe(true);
         }
         expect(comp.selectedMonth[1]).toBeFalsy();
         expect(comp.selectedMonth[8]).toBeFalsy();
      });

      it("should set dates to [first, last] when otherCalendarEmpty=true", () => {
         const { comp } = createYearCalendar();
         const values = [
            { year: 2025, month: 2, dateType: "m", value: 0 },
            { year: 2025, month: 7, dateType: "m", value: 0 },
         ] as any;

         comp.paintRange(values, true);

         expect(comp.dates.length).toBe(2);
      });
   });

   // ─── Group 10 – updateSelected ───────────────────────────────────────────

   describe("Group 10 - updateSelected", () => {
      it("should emit syncRanges when doubleCalendar=true and period=false", () => {
         const { comp } = createYearCalendar({ model: { doubleCalendar: true, period: false } });
         const synced: boolean[] = [];
         comp.syncRanges.subscribe((v) => synced.push(v));

         comp.updateSelected();

         expect(synced.length).toBe(1);
      });

      it("should mark selectedMonth[month]=true for each 'm' entry when !doubleCalendar", () => {
         const { comp } = createYearCalendar({ model: { doubleCalendar: false } });
         comp.dates = [
            { year: 2025, month: 3, dateType: "m", value: 0 },
            { year: 2025, month: 7, dateType: "m", value: 0 },
         ] as any;

         comp.updateSelected();

         expect(comp.selectedMonth[3]).toBe(true);
         expect(comp.selectedMonth[7]).toBe(true);
         expect(comp.selectedMonth[0]).toBeFalsy();
      });
   });
});
