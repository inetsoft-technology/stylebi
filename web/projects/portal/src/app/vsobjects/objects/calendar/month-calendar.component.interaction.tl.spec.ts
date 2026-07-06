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
 * MonthCalendar – Pass 1: Interaction
 *
 * Coverage:
 *   Group 1  - ngOnChanges: selectedRow/Col-only guard, selected-only guard, normal init
 *   Group 2  - ngAfterViewInit: inited flag, detectChanges call, firstDayOfWeek update
 *   Group 3  - dates getter/setter: secondCalendar=false delegates to model.dates1; secondCalendar=true delegates to model.dates2
 *   Group 4  - nextYear: inc/dec, applyCalendar vs dateChanged+titleChanged paths, syncDateChange guard
 *   Group 5  - nextMonth: inc/dec with wrap, applyCalendar path, formatPainterMode guard
 *   Group 6  - clickCell: formatPainterMode guard, vsWizard guard, disabled-day guard, week-type selection,
 *                          day-type selection, applyCalendar emit, syncPeriods emit
 *   Group 7  - clickDayTitle: formatPainterMode guard, preventDefault when !daySelection
 *   Group 8  - clickMonthTitle: select month, ctrl+click deselect
 *   Group 9  - updateSelected: syncRanges path (doubleCalendar range mode), direct week-mark path, direct day-mark path
 *   Group 10 - resetDays: clears all day selections
 *   Group 11 - syncDate: direction determined by secondCalendar flag
 *   Group 12 - syncPeriod: month-type values, day-type values, empty values
 *   Group 13 - paintRange: empty guard, single-value (primary), multi-value range, otherCalendarEmpty flag
 *   Group 14 - updateSelectedDates: propagates currentDate year+month to all selected entries
 */

import { SimpleChange } from "@angular/core";
import { createMonthCalendar } from "./month-calendar.component.test-helpers";

// ─── helpers ────────────────────────────────────────────────────────────────

/** Returns a minimal MouseEvent-like stub for clickCell/clickMonthTitle tests. */
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

describe("MonthCalendar – interaction", () => {
   afterEach(() => {
      vi.restoreAllMocks();
   });

   // ─── Group 1 – ngOnChanges guards ───────────────────────────────────────

   describe("Group 1 - ngOnChanges guards", () => {
      it("should ignore a change set that contains only selectedRow", () => {
         const { comp } = createMonthCalendar();
         const yearBefore = comp.currentDate.year;

         // numChanges==1 && only selectedRow → early return; currentDate must be unchanged
         comp.ngOnChanges({ selectedRow: new SimpleChange(-7, 1, false) } as any);

         expect(comp.currentDate.year).toBe(yearBefore);
      });

      it("should ignore a change set that contains only selectedRow and selectedCol", () => {
         const { comp } = createMonthCalendar();
         const yearBefore = comp.currentDate.year;

         comp.ngOnChanges({
            selectedRow: new SimpleChange(-7, 1, false),
            selectedCol: new SimpleChange(-1, 0, false),
         } as any);

         expect(comp.currentDate.year).toBe(yearBefore);
      });

      it("should ignore a selected-only change when currentDate is already set", () => {
         const { comp } = createMonthCalendar();
         // After factory createMonthCalendar, currentDate is already initialised.
         const yearBefore = comp.currentDate.year;

         comp.ngOnChanges({ selected: new SimpleChange(false, true, false) } as any);

         expect(comp.currentDate.year).toBe(yearBefore);
      });

      it("should initialise currentDate from model.currentDate1 when secondCalendar=false", () => {
         const { comp } = createMonthCalendar({ model: { currentDate1: { year: 2023, month: 5 } } });
         // ngOnChanges was called during createMonthCalendar
         expect(comp.currentDate.year).toBe(2023);
         expect(comp.currentDate.month).toBe(5);
      });

      it("should initialise currentDate from model.currentDate2 when secondCalendar=true", () => {
         const { comp } = createMonthCalendar({
            secondCalendar: true,
            model: { currentDate2: { year: 2024, month: 8 } },
         });
         expect(comp.currentDate.year).toBe(2024);
         expect(comp.currentDate.month).toBe(8);
      });

      it("should populate days[] after model change", () => {
         const { comp } = createMonthCalendar();
         // 6 weeks × 7 days = 42 cells in a month grid
         expect(comp.days.length).toBe(42);
      });
   });

   // ─── Group 2 – ngAfterViewInit ───────────────────────────────────────────

   describe("Group 2 - ngAfterViewInit", () => {
      it("should set inited=true", () => {
         const { comp } = createMonthCalendar();
         expect(comp.inited).toBeFalsy(); // not set by factory (no ngAfterViewInit call)

         comp.ngAfterViewInit();

         expect(comp.inited).toBe(true);
      });

      it("should call changeDetectorRef.detectChanges once", () => {
         const { comp, changeDetectorRef } = createMonthCalendar();
         changeDetectorRef.detectChanges.mockClear();

         comp.ngAfterViewInit();

         expect(changeDetectorRef.detectChanges).toHaveBeenCalledTimes(1);
      });

      it("should request firstDayOfWeek from the service and update the field", () => {
         const { comp, firstDayOfWeekService } = createMonthCalendar({ firstDayOfWeek: 7 });

         comp.ngAfterViewInit();

         expect(firstDayOfWeekService.getFirstDay).toHaveBeenCalledTimes(1);
         expect(comp.firstDayOfWeek).toBe(7);
      });
   });

   // ─── Group 3 – dates getter/setter ──────────────────────────────────────

   describe("Group 3 - dates getter/setter", () => {
      it("should read from model.dates1 when secondCalendar=false", () => {
         const { comp } = createMonthCalendar();
         const d = [{ year: 2025, month: 3, dateType: "d", value: 1 }] as any;
         comp.model.dates1 = d;
         expect(comp.dates).toBe(d);
      });

      it("should read from model.dates2 when secondCalendar=true", () => {
         const { comp } = createMonthCalendar({ secondCalendar: true });
         const d = [{ year: 2025, month: 4, dateType: "d", value: 2 }] as any;
         comp.model.dates2 = d;
         expect(comp.dates).toBe(d);
      });

      it("should write to model.dates1 when secondCalendar=false", () => {
         const { comp } = createMonthCalendar();
         const d = [{ year: 2025, month: 3, dateType: "d", value: 3 }] as any;
         comp.dates = d;
         expect(comp.model.dates1).toBe(d);
      });

      it("should write to model.dates2 when secondCalendar=true", () => {
         const { comp } = createMonthCalendar({ secondCalendar: true });
         const d = [{ year: 2025, month: 4, dateType: "d", value: 4 }] as any;
         comp.dates = d;
         expect(comp.model.dates2).toBe(d);
      });
   });

   // ─── Group 4 – nextYear ──────────────────────────────────────────────────

   describe("Group 4 - nextYear", () => {
      it("should increment year by 1", () => {
         const { comp } = createMonthCalendar({ model: { doubleCalendar: false, submitOnChange: false } });
         const before = comp.currentDate.year;
         comp.nextYear(1);
         expect(comp.currentDate.year).toBe(before + 1);
      });

      it("should decrement year by 1", () => {
         const { comp } = createMonthCalendar({ model: { doubleCalendar: false, submitOnChange: false } });
         const before = comp.currentDate.year;
         comp.nextYear(-1);
         expect(comp.currentDate.year).toBe(before - 1);
      });

      it("should emit applyCalendar when !doubleCalendar && submitOnChange", () => {
         const { comp } = createMonthCalendar({ model: { doubleCalendar: false, submitOnChange: true } });
         const applied: void[] = [];
         comp.applyCalendar.subscribe(() => applied.push(void 0));

         comp.nextYear(1);

         expect(applied.length).toBe(1);
      });

      it("should emit titleChanged but not applyCalendar when doubleCalendar", () => {
         const { comp } = createMonthCalendar({ model: { doubleCalendar: true, submitOnChange: true } });
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
         const { comp } = createMonthCalendar({ model: { doubleCalendar: true, period: false } });
         const synced: boolean[] = [];
         comp.syncDateChange.subscribe((v) => synced.push(v));

         comp.nextYear(1); // calledOnSync defaults to undefined/false

         expect(synced.length).toBe(1);
      });

      it("should NOT emit syncDateChange when calledOnSync=true", () => {
         const { comp } = createMonthCalendar({ model: { doubleCalendar: true, period: false } });
         const synced: boolean[] = [];
         comp.syncDateChange.subscribe((v) => synced.push(v));

         comp.nextYear(1, true); // calledOnSync=true

         expect(synced.length).toBe(0);
      });

      it("should do nothing when formatPainterMode is true", () => {
         const { comp } = createMonthCalendar();
         comp.formatPainterMode = true;
         const before = comp.currentDate.year;

         comp.nextYear(1);

         expect(comp.currentDate.year).toBe(before);
      });
   });

   // ─── Group 5 – nextMonth ─────────────────────────────────────────────────

   describe("Group 5 - nextMonth", () => {
      it("should increment month by 1", () => {
         const { comp } = createMonthCalendar({ model: { doubleCalendar: false, submitOnChange: false } });
         const before = comp.currentDate.month; // 3 (April)
         comp.nextMonth(1);
         expect(comp.currentDate.month).toBe(before + 1);
      });

      it("should wrap month 11→0 and increment year when incrementing past December", () => {
         const { comp } = createMonthCalendar({
            model: { currentDate1: { year: 2025, month: 11 }, doubleCalendar: false, submitOnChange: false },
         });
         comp.nextMonth(1);
         expect(comp.currentDate.month).toBe(0);
         expect(comp.currentDate.year).toBe(2026);
      });

      it("should wrap month 0→11 and decrement year when decrementing before January", () => {
         const { comp } = createMonthCalendar({
            model: { currentDate1: { year: 2025, month: 0 }, doubleCalendar: false, submitOnChange: false },
         });
         comp.nextMonth(-1);
         expect(comp.currentDate.month).toBe(11);
         expect(comp.currentDate.year).toBe(2024);
      });

      it("should emit applyCalendar when !doubleCalendar && submitOnChange", () => {
         const { comp } = createMonthCalendar({ model: { doubleCalendar: false, submitOnChange: true } });
         const applied: void[] = [];
         comp.applyCalendar.subscribe(() => applied.push(void 0));

         comp.nextMonth(1);

         expect(applied.length).toBe(1);
      });

      it("should do nothing when formatPainterMode is true", () => {
         const { comp } = createMonthCalendar();
         comp.formatPainterMode = true;
         const before = comp.currentDate.month;

         comp.nextMonth(1);

         expect(comp.currentDate.month).toBe(before);
      });
   });

   // ─── Group 6 – clickCell ────────────────────────────────────────────────

   describe("Group 6 - clickCell", () => {
      it("should do nothing when formatPainterMode is true", () => {
         const { comp } = createMonthCalendar();
         comp.formatPainterMode = true;
         const applied: void[] = [];
         comp.applyCalendar.subscribe(() => applied.push(void 0));

         comp.clickCell(0, 2, makeClick());

         expect(applied.length).toBe(0);
         expect(comp.dates.length).toBe(0);
      });

      it("should do nothing when vsWizard is true", () => {
         const { comp } = createMonthCalendar({ contextProvider: { vsWizard: true } });
         const applied: void[] = [];
         comp.applyCalendar.subscribe(() => applied.push(void 0));

         comp.clickCell(0, 2, makeClick());

         expect(applied.length).toBe(0);
         expect(comp.dates.length).toBe(0);
      });

      it("should do nothing when the clicked cell is disabled (previous-month day)", () => {
         const { comp } = createMonthCalendar();
         // April 2025 with firstDayOfWeek=1: days[0]=March 30 (disabled)
         // row=0, col=0 → days[0].disabled=true
         const applied: void[] = [];
         comp.applyCalendar.subscribe(() => applied.push(void 0));

         comp.clickCell(0, 0, makeClick());

         expect(applied.length).toBe(0);
         expect(comp.dates.length).toBe(0);
      });

      it("should create a week-type selection when daySelection=false", () => {
         const { comp } = createMonthCalendar({
            model: { daySelection: false, submitOnChange: false, doubleCalendar: false },
         });
         // row=0 → value = row+1 = 1
         comp.clickCell(0, 2, makeClick());

         expect(comp.dates.length).toBe(1);
         expect(comp.dates[0].dateType).toBe("w");
         expect(comp.dates[0].value).toBe(1);
      });

      it("should create a day-type selection when daySelection=true", () => {
         const { comp } = createMonthCalendar({
            model: { daySelection: true, submitOnChange: false, doubleCalendar: false },
         });
         // row=0, col=2, startweek=2: value = 0*7+2-2+1 = 1  (April 1)
         comp.clickCell(0, 2, makeClick());

         expect(comp.dates.length).toBe(1);
         expect(comp.dates[0].dateType).toBe("d");
         expect(comp.dates[0].value).toBe(1);
      });

      it("should emit applyCalendar when !doubleCalendar && submitOnChange", () => {
         const { comp } = createMonthCalendar({
            model: { submitOnChange: true, doubleCalendar: false },
         });
         const applied: void[] = [];
         comp.applyCalendar.subscribe(() => applied.push(void 0));

         comp.clickCell(0, 2, makeClick());

         expect(applied.length).toBe(1);
      });

      it("should emit syncPeriods when doubleCalendar=true && period=true && inited=true", () => {
         const { comp } = createMonthCalendar({
            model: { doubleCalendar: true, period: true },
         });
         // inited is a public field set by ngAfterViewInit; seed it directly for this test
         comp.inited = true;
         const synced: boolean[] = [];
         comp.syncPeriods.subscribe((v) => synced.push(v));

         comp.clickCell(0, 2, makeClick());

         expect(synced.length).toBe(1);
      });

      it("should remove an already-selected date on ctrl+click", () => {
         const { comp } = createMonthCalendar({
            model: { daySelection: false, submitOnChange: false, doubleCalendar: false },
         });
         // First plain click selects week 1
         comp.clickCell(0, 2, makeClick());
         expect(comp.dates.length).toBe(1);

         // Second ctrl+click on same cell deselects it
         comp.clickCell(0, 2, makeClick({ ctrlKey: true }));
         expect(comp.dates.length).toBe(0);
      });
   });

   // ─── Group 7 – clickDayTitle ─────────────────────────────────────────────

   describe("Group 7 - clickDayTitle", () => {
      it("should do nothing when formatPainterMode is true", () => {
         const { comp } = createMonthCalendar({ model: { daySelection: true } });
         comp.formatPainterMode = true;
         const applied: void[] = [];
         comp.applyCalendar.subscribe(() => applied.push(void 0));

         comp.clickDayTitle(0, makeClick());

         expect(applied.length).toBe(0);
         expect(comp.dates.length).toBe(0);
      });

      it("should call preventDefault and return without selecting when daySelection=false", () => {
         const { comp } = createMonthCalendar({ model: { daySelection: false } });
         const event = makeClick();

         comp.clickDayTitle(0, event);

         expect(event.preventDefault).toHaveBeenCalledTimes(1);
         expect(comp.dates.length).toBe(0);
      });
   });

   // ─── Group 8 – clickMonthTitle ───────────────────────────────────────────

   describe("Group 8 - clickMonthTitle", () => {
      it("should add a month-type entry for the current year+month", () => {
         const { comp } = createMonthCalendar({ model: { submitOnChange: false } });

         comp.clickMonthTitle(makeClick());

         expect(comp.dates.length).toBe(1);
         expect(comp.dates[0].dateType).toBe("m");
         expect(comp.dates[0].year).toBe(comp.currentDate.year);
         expect(comp.dates[0].month).toBe(comp.currentDate.month);
      });

      it("should deselect the month on ctrl+click when it is already selected", () => {
         const { comp } = createMonthCalendar({ model: { submitOnChange: false } });
         // First click: select month
         comp.clickMonthTitle(makeClick());
         expect(comp.dates.length).toBe(1);

         // Second ctrl+click on already-selected month: deselect
         comp.clickMonthTitle(makeClick({ ctrlKey: true }));
         expect(comp.dates.length).toBe(0);
      });

      it("should emit applyCalendar when !doubleCalendar && submitOnChange", () => {
         const { comp } = createMonthCalendar({ model: { submitOnChange: true, doubleCalendar: false } });
         const applied: void[] = [];
         comp.applyCalendar.subscribe(() => applied.push(void 0));

         comp.clickMonthTitle(makeClick());

         expect(applied.length).toBe(1);
      });

      it("should do nothing when formatPainterMode is true", () => {
         const { comp } = createMonthCalendar();
         comp.formatPainterMode = true;

         comp.clickMonthTitle(makeClick());

         expect(comp.dates.length).toBe(0);
      });
   });

   // ─── Group 9 – updateSelected ────────────────────────────────────────────

   describe("Group 9 - updateSelected", () => {
      it("should emit syncRanges when doubleCalendar=true and period=false", () => {
         const { comp } = createMonthCalendar({ model: { doubleCalendar: true, period: false } });
         const synced: boolean[] = [];
         comp.syncRanges.subscribe((v) => synced.push(v));

         comp.updateSelected();

         expect(synced.length).toBe(1);
         expect(synced[0]).toBe(false); // secondCalendar=false
      });

      it("should mark all 7 cells in the selected week when dateType='w'", () => {
         const { comp } = createMonthCalendar({ model: { doubleCalendar: false, daySelection: false } });
         // week value=1: cells (value-1)*7+0..6 = days[0..6]
         comp.dates = [{ year: 2025, month: 3, dateType: "w", value: 1 }] as any;
         comp.resetDays();

         comp.updateSelected();

         for(let i = 0; i < 7; i++) {
            expect(comp.days[i].selected).toBe(true);
         }
         expect(comp.days[7].selected).toBe(false);
      });

      it("should mark only the specific day cell when dateType='d'", () => {
         const { comp } = createMonthCalendar({ model: { doubleCalendar: false, daySelection: true } });
         // value=1, startweek=2 (April 2025, firstDayOfWeek=1): index = (1-1)+2 = 2
         comp.dates = [{ year: 2025, month: 3, dateType: "d", value: 1 }] as any;
         comp.resetDays();

         comp.updateSelected();

         expect(comp.days[2].selected).toBe(true);
         expect(comp.days[1].selected).toBe(false);
         expect(comp.days[3].selected).toBe(false);
      });
   });

   // ─── Group 10 – resetDays ────────────────────────────────────────────────

   describe("Group 10 - resetDays", () => {
      it("should set selected=false on every cell in days[]", () => {
         const { comp } = createMonthCalendar({ model: { daySelection: false } });
         // Manually mark some cells selected
         comp.days[0].selected = true;
         comp.days[5].selected = true;
         comp.days[41].selected = true;

         comp.resetDays();

         expect(comp.days.every((d) => !d.selected)).toBe(true);
      });
   });

   // ─── Group 11 – syncDate ─────────────────────────────────────────────────

   describe("Group 11 - syncDate", () => {
      it("should decrement year for the primary calendar when changeYear=true", () => {
         const { comp } = createMonthCalendar({ model: { doubleCalendar: true, submitOnChange: false } });
         // secondCalendar=false → nextYear(-1, true)
         const before = comp.currentDate.year;
         comp.syncDate(true);
         expect(comp.currentDate.year).toBe(before - 1);
      });

      it("should increment year for the secondary calendar when changeYear=true", () => {
         const { comp } = createMonthCalendar({
            secondCalendar: true,
            model: { doubleCalendar: true, submitOnChange: false },
         });
         // secondCalendar=true → nextYear(1, true)
         const before = comp.currentDate.year;
         comp.syncDate(true);
         expect(comp.currentDate.year).toBe(before + 1);
      });

      it("should decrement month for the primary calendar when changeYear=false", () => {
         const { comp } = createMonthCalendar({
            model: { doubleCalendar: true, submitOnChange: false, currentDate1: { year: 2025, month: 5 } },
         });
         // secondCalendar=false → nextMonth(-1, true)
         const before = comp.currentDate.month;
         comp.syncDate(false);
         expect(comp.currentDate.month).toBe(before - 1);
      });

      it("should increment month for the secondary calendar when changeYear=false", () => {
         const { comp } = createMonthCalendar({
            secondCalendar: true,
            model: { doubleCalendar: true, submitOnChange: false },
         });
         // secondCalendar=true → nextMonth(1, true)
         const before = comp.currentDate.month; // 4 (May)
         comp.syncDate(false);
         expect(comp.currentDate.month).toBe(before + 1);
      });
   });

   // ─── Group 12 – syncPeriod ───────────────────────────────────────────────

   describe("Group 12 - syncPeriod", () => {
      it("should do nothing when values is empty", () => {
         const { comp } = createMonthCalendar({ model: { submitOnChange: false } });
         comp.syncPeriod([]);
         expect(comp.dates.length).toBe(0);
      });

      it("should replace dates with a single currentMonth entry when values[0].dateType='m'", () => {
         const { comp } = createMonthCalendar({ model: { submitOnChange: false } });
         const values = [{ year: 2025, month: 3, dateType: "m", value: 0 }] as any;

         comp.syncPeriod(values);

         expect(comp.dates.length).toBe(1);
         expect(comp.dates[0].dateType).toBe("m");
         expect(comp.dates[0].year).toBe(comp.currentDate.year);
         expect(comp.dates[0].month).toBe(comp.currentDate.month);
      });

      it("should not call resetDays/updateSelected when onApply=true for month-type sync", () => {
         // onApply=false path (resetDays/updateSelected called) is covered by test 2 above
         const { comp } = createMonthCalendar({ model: { submitOnChange: false } });
         const resetSpy = vi.spyOn(comp, "resetDays");
         const updateSpy = vi.spyOn(comp, "updateSelected");
         const values = [{ year: 2025, month: 3, dateType: "m", value: 0 }] as any;

         comp.syncPeriod(values, true); // onApply=true

         expect(resetSpy).not.toHaveBeenCalled();
         expect(updateSpy).not.toHaveBeenCalled();
      });

      it("should extend dates to match values length when values contain day-type selections", () => {
         const { comp } = createMonthCalendar({ model: { submitOnChange: false } });
         const values = [
            { year: 2025, month: 3, dateType: "d", value: 1 },
            { year: 2025, month: 3, dateType: "d", value: 5 },
         ] as any;

         comp.syncPeriod(values);

         // dates was empty (0 < 2), so 2 entries are added
         expect(comp.dates.length).toBe(2);
         expect(comp.dates[0].dateType).toBe("d");
      });
   });

   // ─── Group 13 – paintRange ───────────────────────────────────────────────

   describe("Group 13 - paintRange", () => {
      it("should return early without modifying days when values is empty", () => {
         const { comp } = createMonthCalendar({ model: { daySelection: false } });
         comp.days[0].selected = false;

         comp.paintRange([]);

         expect(comp.days[0].selected).toBe(false);
      });

      it("should mark all cells from value-start to end-of-grid for primary calendar on single value", () => {
         const { comp } = createMonthCalendar({ model: { daySelection: false } });
         // secondCalendar=false, value=1: start=(1-1)*7=0, end=days.length+1
         const values = [{ year: 2025, month: 3, dateType: "w", value: 1 }] as any;

         comp.paintRange(values);

         // All 42 cells from index 0 onwards should be selected
         expect(comp.days[0].selected).toBe(true);
         expect(comp.days[41].selected).toBe(true);
      });

      it("should mark the range cells between first and last value on multi-value input", () => {
         const { comp } = createMonthCalendar({ model: { daySelection: false } });
         // values[0].value=1, values[1].value=3
         // start=(min-1)*7=0, end=(max+1-1)*7=3*7=21 → cells 0..20
         const values = [
            { year: 2025, month: 3, dateType: "w", value: 1 },
            { year: 2025, month: 3, dateType: "w", value: 3 },
         ] as any;

         comp.paintRange(values);

         expect(comp.days[0].selected).toBe(true);
         expect(comp.days[20].selected).toBe(true);
         expect(comp.days[21].selected).toBe(false); // exclusive end
      });

      it("should set dates to [first, last] when otherCalendarEmpty=true", () => {
         const { comp } = createMonthCalendar({ model: { daySelection: false } });
         const values = [
            { year: 2025, month: 3, dateType: "w", value: 1 },
            { year: 2025, month: 3, dateType: "w", value: 3 },
         ] as any;

         comp.paintRange(values, true);

         expect(comp.dates.length).toBe(2);
         expect(comp.dates[0].value).toBe(1);
         expect(comp.dates[1].value).toBe(3);
      });

      it("should set dates to [first] for primary calendar when otherCalendarEmpty=false", () => {
         const { comp } = createMonthCalendar({ model: { daySelection: false } });
         const values = [
            { year: 2025, month: 3, dateType: "w", value: 1 },
            { year: 2025, month: 3, dateType: "w", value: 3 },
         ] as any;

         comp.paintRange(values); // otherCalendarEmpty defaults to false

         expect(comp.dates.length).toBe(1);
         expect(comp.dates[0].value).toBe(1); // first, since secondCalendar=false
      });
   });

   // ─── Group 14 – updateSelectedDates ─────────────────────────────────────

   describe("Group 14 - updateSelectedDates", () => {
      it("should overwrite year and month on every selected date with currentDate values", () => {
         const { comp } = createMonthCalendar({ model: { submitOnChange: false } });
         // Seed two dates with stale year/month
         comp.dates = [
            { year: 2020, month: 5, dateType: "d", value: 1 },
            { year: 2021, month: 7, dateType: "d", value: 2 },
         ] as any;

         comp.updateSelectedDates();

         expect(comp.dates[0].year).toBe(comp.currentDate.year);
         expect(comp.dates[0].month).toBe(comp.currentDate.month);
         expect(comp.dates[1].year).toBe(comp.currentDate.year);
         expect(comp.dates[1].month).toBe(comp.currentDate.month);
      });
   });
});
