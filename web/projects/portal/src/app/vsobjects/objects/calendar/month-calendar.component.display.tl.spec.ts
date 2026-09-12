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
 * MonthCalendar – Pass 3: Display
 *
 * Coverage:
 *   Group 1  - getCalendarHeight: dropdown vs. non-dropdown formula
 *   Group 2  - dateChanged + isLeapYear: daysInMonth[1]=29 for leap year, 28 for non-leap
 *   Group 3  - getStartWeek: offset computed from firstDayOfWeek + JS Date.getDay()
 *   Group 4  - getSelectionString: dateType 'm', dateType 'd' (no minimum), 'd' minimum=true, 'd' minimum=false
 *   Group 5  - isBtnEnabled: unlimited range returns true; constrained range returns false
 *   Group 6  - checkCurrentDate: clamp to maxYear, clamp to minYear, clamp to minMonth
 *   Group 7  - isInRange: unlimited range, below-min-day boundary, above-max-day boundary
 *   Group 8  - isSelectedDayCell / getDayCellBackground: all 3 &&-guard paths + background delegates
 *   Group 9  - isMonthSelected: true when single 'm' entry, false otherwise
 *   Group 10 - isRowSelectable: true when !daySelection && both endpoints disabled; false otherwise
 *   Group 11 - getDay / getWeekName: default values and model override
 *   Group 12 - getMonth: calendarTitleView1 branch vs. computed monthTitle
 *   Group 13 - ariaDateLabel: composed label string
 *   Group 14 - getDateArray: format for dateType 'd' entries
 *   Group 15 - getCurrentDateString: year-month format
 *   Group 16 - getSelectedDates: empty when dateType 'm'; full array otherwise
 *   Group 17 - resetOldDate: copies currentDate into ocurrentDate
 *   Group 18 - isCellFocused: matches selectedRow/Col
 *   Group 19 - vsWizard getter: reflects contextProvider.vsWizard
 */

import { VSUtil } from "../../util/vs-util";
import { createMonthCalendar } from "./month-calendar.component.test-helpers";

describe("MonthCalendar – display", () => {
   afterEach(() => {
      vi.restoreAllMocks();
   });

   // ─── Group 1 – getCalendarHeight ────────────────────────────────────────

   describe("Group 1 - getCalendarHeight", () => {
      it("should return VSUtil.CALENDAR_BODY_HEIGHT when dropdownCalendar is true", () => {
         const { comp } = createMonthCalendar({ model: { dropdownCalendar: true } });
         expect(comp.getCalendarHeight()).toBe(VSUtil.CALENDAR_BODY_HEIGHT);
      });

      it("should return objectFormat.height minus titleFormat.height when not dropdown", () => {
         const { comp } = createMonthCalendar({ model: { dropdownCalendar: false } });
         // Defaults from makeMonthCalendarModel: objectFormat.height=120, titleFormat.height=30
         expect(comp.getCalendarHeight()).toBe(90);
      });
   });

   // ─── Group 2 – dateChanged + isLeapYear ─────────────────────────────────

   describe("Group 2 - dateChanged + isLeapYear", () => {
      it("should set daysInMonth[1]=29 for a leap year (2024)", () => {
         // 2024 is divisible by 4 and not a century year → leap
         const { comp } = createMonthCalendar({
            model: { currentDate1: { year: 2024, month: 1 } },
         });
         // ngOnChanges called dateChanged() which sets daysInMonth
         expect(comp.daysInMonth[1]).toBe(29);
      });

      it("should set daysInMonth[1]=28 for a non-leap year (2025)", () => {
         const { comp } = createMonthCalendar(); // default year=2025
         expect(comp.daysInMonth[1]).toBe(28);
      });

      it("should set daysInMonth[1]=29 for year 2000 (century divisible by 400)", () => {
         const { comp } = createMonthCalendar({
            model: { currentDate1: { year: 2000, month: 1 } },
         });
         expect(comp.daysInMonth[1]).toBe(29);
      });

      it("should set daysInMonth[1]=28 for year 1900 (century NOT divisible by 400)", () => {
         const { comp } = createMonthCalendar({
            model: { currentDate1: { year: 1900, month: 1 } },
         });
         expect(comp.daysInMonth[1]).toBe(28);
      });
   });

   // ─── Group 3 – getStartWeek ──────────────────────────────────────────────

   describe("Group 3 - getStartWeek", () => {
      it("should return 2 for April 2025 with firstDayOfWeek=1 (Monday-based)", () => {
         // April 1, 2025 is Tuesday: getDay()=2
         // formula: (7 - firstDayOfWeek + getDay() + 1) % 7 = (7-1+2+1)%7 = 9%7 = 2
         const { comp } = createMonthCalendar();
         expect(comp.getStartWeek()).toBe(2);
      });

      it("should return 3 for April 2025 when firstDayOfWeek=7 (Sunday as 7)", () => {
         // formula: (7-7+2+1)%7 = 3%7 = 3
         const { comp } = createMonthCalendar();
         // firstDayOfWeek is a public field — set directly to test this branch
         comp.firstDayOfWeek = 7;
         expect(comp.getStartWeek()).toBe(3);
      });

      it("should return 0 for April 2025 when firstDayOfWeek=3 (Wednesday-based)", () => {
         // formula: (7-3+2+1)%7 = 7%7 = 0
         const { comp } = createMonthCalendar();
         comp.firstDayOfWeek = 3;
         expect(comp.getStartWeek()).toBe(0);
      });
   });

   // ─── Group 4 – getSelectionString ───────────────────────────────────────

   describe("Group 4 - getSelectionString", () => {
      it("should return empty string when dates is empty", () => {
         const { comp } = createMonthCalendar();
         comp.dates = [];
         expect(comp.getSelectionString()).toBe("");
      });

      it("should return 'year-(month+1)' for a month-type selection", () => {
         const { comp } = createMonthCalendar();
         comp.dates = [{ year: 2025, month: 3, dateType: "m", value: 0 }] as any;
         // month+1 = 4
         expect(comp.getSelectionString()).toBe("2025-4");
      });

      it("should return 'year-month-value' for a single day-type selection", () => {
         const { comp } = createMonthCalendar();
         comp.dates = [{ year: 2025, month: 3, dateType: "d", value: 5 }] as any;
         // getRangeString([{value:5}], false) → appendRange("", 5, 5, false) → "5"
         expect(comp.getSelectionString()).toBe("2025-4-5");
      });

      it("should return the first date start when minimum=true for day-type", () => {
         const { comp } = createMonthCalendar();
         comp.dates = [
            { year: 2025, month: 3, dateType: "d", value: 3 },
            { year: 2025, month: 3, dateType: "d", value: 7 },
         ] as any;
         expect(comp.getSelectionString(true)).toBe("2025-4-3");
      });

      it("should return the last date end when minimum=false for day-type", () => {
         const { comp } = createMonthCalendar();
         comp.dates = [
            { year: 2025, month: 3, dateType: "d", value: 3 },
            { year: 2025, month: 3, dateType: "d", value: 7 },
         ] as any;
         expect(comp.getSelectionString(false)).toBe("2025-4-7");
      });
   });

   // ─── Group 5 – isBtnEnabled ──────────────────────────────────────────────

   describe("Group 5 - isBtnEnabled", () => {
      it("should return true in both directions when range is unlimited (-1)", () => {
         const { comp } = createMonthCalendar(); // range all -1
         expect(comp.isBtnEnabled(true, true)).toBe(true);
         expect(comp.isBtnEnabled(false, true)).toBe(true);
      });

      it("should return false for previous-year when currentDate.year is the minimum year", () => {
         const { comp } = createMonthCalendar({
            model: {
               range: { minYear: 2025, minMonth: 0, minDay: -1, maxYear: 2030, maxMonth: 11, maxDay: -1 },
               currentDate1: { year: 2025, month: 3 },
            },
         });
         // previous=true, year=true, year-1=2024 < minYear=2025 → false
         expect(comp.isBtnEnabled(true, true)).toBe(false);
      });

      it("should return false for next-year when currentDate.year is the maximum year", () => {
         const { comp } = createMonthCalendar({
            model: {
               range: { minYear: 2020, minMonth: 0, minDay: -1, maxYear: 2025, maxMonth: 11, maxDay: -1 },
               currentDate1: { year: 2025, month: 3 },
            },
         });
         // previous=false, year=true, year+1=2026 > maxYear=2025 → false
         expect(comp.isBtnEnabled(false, true)).toBe(false);
      });

      it("should return false for previous-month when at the minimum month within the minimum year", () => {
         const { comp } = createMonthCalendar({
            model: {
               range: { minYear: 2025, minMonth: 3, minDay: -1, maxYear: -1, maxMonth: -1, maxDay: -1 },
               currentDate1: { year: 2025, month: 3 },
            },
         });
         // previous=true, year=false, currentYear==minYear → month-1=2 < minMonth=3 → false
         expect(comp.isBtnEnabled(true, false)).toBe(false);
      });
   });

   // ─── Group 6 – checkCurrentDate ─────────────────────────────────────────

   describe("Group 6 - checkCurrentDate", () => {
      it("should clamp year to maxYear (and month to maxMonth) when year exceeds range", () => {
         const { comp } = createMonthCalendar();
         comp.currentDate = { year: 2030, month: 6 };
         comp.model.range = { minYear: -1, minMonth: -1, minDay: -1, maxYear: 2025, maxMonth: 11, maxDay: -1 };

         comp.checkCurrentDate();

         expect(comp.currentDate.year).toBe(2025);
         expect(comp.currentDate.month).toBe(11); // maxMonth
      });

      it("should clamp year to minYear (and month to minMonth) when year is below range", () => {
         const { comp } = createMonthCalendar();
         comp.currentDate = { year: 2020, month: 6 };
         comp.model.range = { minYear: 2025, minMonth: 0, minDay: -1, maxYear: -1, maxMonth: -1, maxDay: -1 };

         comp.checkCurrentDate();

         expect(comp.currentDate.year).toBe(2025);
         expect(comp.currentDate.month).toBe(0); // minMonth
      });

      it("should clamp month to minMonth when year equals minYear but month is below", () => {
         const { comp } = createMonthCalendar();
         comp.currentDate = { year: 2025, month: 2 };
         comp.model.range = { minYear: 2025, minMonth: 6, minDay: -1, maxYear: -1, maxMonth: -1, maxDay: -1 };

         comp.checkCurrentDate();

         expect(comp.currentDate.year).toBe(2025); // year unchanged
         expect(comp.currentDate.month).toBe(6);   // clamped to minMonth
      });
   });

   // ─── Group 7 – isInRange ─────────────────────────────────────────────────

   describe("Group 7 - isInRange", () => {
      it("should return true for any day when range is unlimited (-1)", () => {
         const { comp } = createMonthCalendar(); // range all -1
         expect(comp.isInRange(2025, 3, 15)).toBe(true);
      });

      it("should return false for a day before the min boundary", () => {
         const { comp } = createMonthCalendar({
            model: {
               range: { minYear: 2025, minMonth: 3, minDay: 10, maxYear: -1, maxMonth: -1, maxDay: -1 },
               currentDate1: { year: 2025, month: 3 },
            },
         });
         // year==minYear, month==minMonth, day=5 < minDay=10 → false
         expect(comp.isInRange(2025, 3, 5)).toBe(false);
      });

      it("should return true for a day on the min boundary", () => {
         const { comp } = createMonthCalendar({
            model: {
               range: { minYear: 2025, minMonth: 3, minDay: 10, maxYear: -1, maxMonth: -1, maxDay: -1 },
               currentDate1: { year: 2025, month: 3 },
            },
         });
         expect(comp.isInRange(2025, 3, 10)).toBe(true);
      });

      it("should return false for a day after the max boundary", () => {
         const { comp } = createMonthCalendar({
            model: {
               range: { minYear: -1, minMonth: -1, minDay: -1, maxYear: 2025, maxMonth: 3, maxDay: 20 },
               currentDate1: { year: 2025, month: 3 },
            },
         });
         // year==maxYear, month==maxMonth, day=25 > maxDay=20 → false
         expect(comp.isInRange(2025, 3, 25)).toBe(false);
      });
   });

   // ─── Group 8 – isSelectedDayCell / getDayCellBackground ─────────────────

   describe("Group 8 - isSelectedDayCell / getDayCellBackground", () => {
      // isSelectedDayCell: first &&-guard (selected)
      it("should return true for a selected non-disabled cell when daySelection=true", () => {
         const { comp } = createMonthCalendar({ model: { daySelection: true } });
         // April 1: value=1, startweek=2 → days[2] selected; (row=1,col=2) → idx=(1-1)*7+2=2
         comp.dates = [{ year: 2025, month: 3, dateType: "d", value: 1 }] as any;
         comp.resetDays();
         comp.updateSelected();

         expect(comp.isSelectedDayCell(1, 2)).toBe(true);
      });

      // isSelectedDayCell: first &&-guard (selected) falsy path
      it("should return false for an unselected cell", () => {
         const { comp } = createMonthCalendar();
         comp.resetDays();

         // days[(1-1)*7+0] = days[0] = March 30 — not selected after resetDays
         expect(comp.isSelectedDayCell(1, 0)).toBe(false);
      });

      // isSelectedDayCell: second &&-guard (!daySelection || !disabled) falsy path
      it("should return false when the cell is selected but disabled and daySelection=true", () => {
         const { comp } = createMonthCalendar({ model: { daySelection: true } });
         comp.resetDays();
         // days[0] = March 30: disabled=true (previous-month day) — force selected to isolate the disabled guard
         comp.days[0].selected = true;
         expect(comp.isSelectedDayCell(1, 0)).toBe(false);
      });

      // getDayCellBackground: selected path
      it("should return selectedBgColor for a selected non-disabled cell", () => {
         const { comp } = createMonthCalendar({ model: { daySelection: true } });
         comp.dates = [{ year: 2025, month: 3, dateType: "d", value: 1 }] as any;
         comp.resetDays();
         comp.updateSelected();

         expect(comp.getDayCellBackground(1, 2)).toBe(comp.selectedBgColor);
      });

      // getDayCellBackground: unselected path
      it("should return monthFormat.background for an unselected cell", () => {
         const { comp } = createMonthCalendar();
         comp.resetDays();

         expect(comp.getDayCellBackground(1, 0)).toBe(comp.model.monthFormat.background);
      });
   });

   // ─── Group 9 – isMonthSelected ───────────────────────────────────────────

   describe("Group 9 - isMonthSelected", () => {
      it("should return true when dates has exactly one month-type selection", () => {
         const { comp } = createMonthCalendar();
         comp.dates = [{ year: 2025, month: 3, dateType: "m", value: 0 }] as any;
         expect(comp.isMonthSelected()).toBe(true);
      });

      it("should return false when dates is empty", () => {
         const { comp } = createMonthCalendar();
         comp.dates = [];
         expect(comp.isMonthSelected()).toBe(false);
      });

      it("should return false when dates contain a day-type selection (not month)", () => {
         const { comp } = createMonthCalendar();
         comp.dates = [{ year: 2025, month: 3, dateType: "d", value: 1 }] as any;
         expect(comp.isMonthSelected()).toBe(false);
      });
   });

   // ─── Group 10 – isRowSelectable ──────────────────────────────────────────

   describe("Group 10 - isRowSelectable", () => {
      it("should return true for a row where both endpoint cells are disabled and daySelection=false", () => {
         const { comp } = createMonthCalendar({ model: { daySelection: false } });
         // April 2025 startweek=2: days[32..41] = May 1..10 (all disabled)
         // Row 5: cells 35..41; days[35] and days[41] are both disabled
         expect(comp.isRowSelectable(5)).toBe(true);
      });

      it("should return false when daySelection=true regardless of disabled status", () => {
         const { comp } = createMonthCalendar({ model: { daySelection: true } });
         // !daySelection is false → isRowSelectable short-circuits to false
         expect(comp.isRowSelectable(5)).toBe(false);
      });
   });

   // ─── Group 11 – getDay / getWeekName ────────────────────────────────────

   describe("Group 11 - getDay / getWeekName", () => {
      it("getDay should return the numeric day from days[] when model.dayNames is null", () => {
         const { comp } = createMonthCalendar(); // dayNames=null
         // row=1, col=0 → idx=0 → days[0] = March 30 (day=30)
         expect(comp.getDay(1, 0)).toBe(30);
      });

      it("getDay should return the override label from model.dayNames when set", () => {
         // dayNames[30] should be returned for a cell with day=30
         const dayNames = Array.from({ length: 32 }, (_, i) => (i === 30 ? "Thirtieth" : String(i)));
         const { comp } = createMonthCalendar({ model: { dayNames: dayNames as any } });
         // row=1, col=0 → idx=0 → days[0].day=30 → dayNames[30] = "Thirtieth"
         expect(comp.getDay(1, 0)).toBe("Thirtieth");
      });

      it("getWeekName should return dayTitles[index] when model.weekNames is not 7 entries", () => {
         const { comp } = createMonthCalendar(); // weekNames=[] (length=0 ≠ 7)
         // dayTitles[0] = "_#(js:Sun)"
         expect(comp.getWeekName(0)).toBe(comp.dayTitles[0]);
      });

      it("getWeekName should return model.weekNames[index] when weekNames has exactly 7 entries", () => {
         const weekNames = ["Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"];
         const { comp } = createMonthCalendar({ model: { weekNames: weekNames as any } });
         expect(comp.getWeekName(1)).toBe("Mo");
      });
   });

   // ─── Group 12 – getMonth ─────────────────────────────────────────────────

   describe("Group 12 - getMonth", () => {
      it("should return model.calendarTitleView1 when it is set and no date change has occurred", () => {
         const { comp } = createMonthCalendar();
         comp.model.calendarTitleView1 = "April 2025 (Formatted)";
         // currentDate == ocurrentDate (no nextYear/nextMonth called) → no change
         expect(comp.getMonth()).toBe("April 2025 (Formatted)");
      });

      it("should return the computed monthTitle when calendarTitleView1 is null", () => {
         const { comp } = createMonthCalendar();
         // calendarTitleView1=null (default) → returns monthTitle
         // monthTitle = monthNames[3] + " " + 2025 = "_#(js:April) 2025"
         expect(comp.getMonth()).toContain("2025");
      });

      it("should return the computed monthTitle when a year change has occurred", () => {
         const { comp } = createMonthCalendar();
         comp.model.calendarTitleView1 = "April 2025 (Formatted)";
         // Simulate a navigation to next year: update year and rebuild monthTitle via dateChanged()
         comp.currentDate.year = 2026;
         comp.dateChanged(); // re-populates monthTitle with new year before getMonth() is called
         // change=true (2026 != ocurrentDate.year=2025) → bypass calendarTitleView1
         expect(comp.getMonth()).toContain("2026");
      });
   });

   // ─── Group 13 – ariaDateLabel ────────────────────────────────────────────

   describe("Group 13 - ariaDateLabel", () => {
      it("should return a label combining month name, ordinal day, and year", () => {
         const { comp } = createMonthCalendar(); // April 2025
         // ariaDateLabel(1): monthNames[3] + " " + monthDays[0] + " " + 2025
         const label = comp.ariaDateLabel(1);
         expect(label).toContain("2025");
         expect(label).toContain(comp.monthNames[3]); // "_#(js:April)"
         expect(label).toContain(comp.monthDays[0]);  // "_#(js:1st)"
      });
   });

   // ─── Group 14 – getDateArray ─────────────────────────────────────────────

   describe("Group 14 - getDateArray", () => {
      it("should return 'dateType+year-month-value' strings for day/week entries", () => {
         const { comp } = createMonthCalendar();
         comp.dates = [
            { year: 2025, month: 3, dateType: "d", value: 5 },
         ] as any;
         // format: dateType + year + "-" + month + "-" + value = "d2025-3-5"
         expect(comp.getDateArray()).toEqual(["d2025-3-5"]);
      });

      it("should return 'dateType+year-month' strings without value for month entries", () => {
         const { comp } = createMonthCalendar();
         comp.dates = [
            { year: 2025, month: 3, dateType: "m", value: 0 },
         ] as any;
         // dateType != "w" and != "d": format = dateType + year + "-" + month = "m2025-3"
         // But wait: month dateType "m" is not w/d so no value appended: "m2025-3"
         expect(comp.getDateArray()).toEqual(["m2025-3"]);
      });
   });

   // ─── Group 15 – getCurrentDateString ────────────────────────────────────

   describe("Group 15 - getCurrentDateString", () => {
      it("should return 'year-month' for the current date", () => {
         const { comp } = createMonthCalendar(); // year=2025, month=3
         expect(comp.getCurrentDateString()).toBe("2025-3");
      });
   });

   // ─── Group 16 – getSelectedDates ────────────────────────────────────────

   describe("Group 16 - getSelectedDates", () => {
      it("should return empty array when dates[0].dateType is 'm'", () => {
         const { comp } = createMonthCalendar();
         comp.dates = [{ year: 2025, month: 3, dateType: "m", value: 0 }] as any;
         expect(comp.getSelectedDates()).toEqual([]);
      });

      it("should return the full dates array when dateType is not 'm'", () => {
         const { comp } = createMonthCalendar();
         comp.dates = [{ year: 2025, month: 3, dateType: "d", value: 1 }] as any;
         expect(comp.getSelectedDates().length).toBe(1);
         expect(comp.getSelectedDates()[0].dateType).toBe("d");
      });
   });

   // ─── Group 17 – resetOldDate ─────────────────────────────────────────────

   describe("Group 17 - resetOldDate", () => {
      it("should snapshot currentDate into ocurrentDate (deep clone)", () => {
         const { comp } = createMonthCalendar();
         comp.currentDate.year = 2030;

         comp.resetOldDate();

         expect(comp.ocurrentDate.year).toBe(2030);
         // Verify it is a clone, not the same reference
         comp.currentDate.year = 2031;
         expect(comp.ocurrentDate.year).toBe(2030);
      });
   });

   // ─── Group 18 – isCellFocused ────────────────────────────────────────────

   describe("Group 18 - isCellFocused", () => {
      it("should return true when row and col match selectedRow and selectedCol", () => {
         const { comp } = createMonthCalendar();
         comp.selectedRow = 2;
         comp.selectedCol = 3;
         expect(comp.isCellFocused(2, 3)).toBe(true);
      });

      it("should return false when row or col does not match", () => {
         const { comp } = createMonthCalendar();
         comp.selectedRow = 2;
         comp.selectedCol = 3;
         expect(comp.isCellFocused(2, 4)).toBe(false);
         expect(comp.isCellFocused(1, 3)).toBe(false);
      });
   });

   // ─── Group 19 – vsWizard getter ──────────────────────────────────────────

   describe("Group 19 - vsWizard getter", () => {
      it("should return false when contextProvider.vsWizard is false", () => {
         const { comp } = createMonthCalendar({ contextProvider: { vsWizard: false } });
         expect(comp.vsWizard).toBe(false);
      });

      it("should return true when contextProvider.vsWizard is true", () => {
         const { comp } = createMonthCalendar({ contextProvider: { vsWizard: true } });
         expect(comp.vsWizard).toBe(true);
      });
   });
});
