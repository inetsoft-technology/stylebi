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
 * YearCalendar – Pass 3: Display
 *
 * Coverage:
 *   Group 1  - getCalendarHeight: dropdown vs. non-dropdown formula
 *   Group 2  - getYear: calendarTitleView1 branch vs. computed year string
 *   Group 3  - getMonth: numeric default (no monthNames override) vs. model override
 *   Group 4  - getSelectionString: dateType 'y', 'm' no-arg, 'm' minimum=true, 'm' minimum=false
 *   Group 5  - getSelectedDates: empty for 'y' type; full array for 'm' type
 *   Group 6  - isYearSelected: true when dates[0].dateType='y'; false otherwise
 *   Group 7  - checkCurrentDate: clamp to maxYear, clamp to minYear
 *   Group 8  - isInRange: unlimited, below-min-month boundary, above-max-month boundary
 *   Group 9  - isBtnEnabled: unlimited range returns true; constrained returns false
 *   Group 10 - getDateArray: format for 'y' entries and 'm' entries
 *   Group 11 - getCurrentDateString: year-only when month=0; year-month when non-zero
 *   Group 12 - isCellFocused: matches selectedRow/Col
 *   Group 13 - vsWizard getter: reflects contextProvider.vsWizard
 *   Group 14 - resetOldDate: snapshots currentDate into ocurrentDate
 */

import { VSUtil } from "../../util/vs-util";
import { createYearCalendar } from "./year-calendar.component.test-helpers";

describe("YearCalendar – display", () => {
   afterEach(() => {
      vi.restoreAllMocks();
   });

   // ─── Group 1 – getCalendarHeight ────────────────────────────────────────

   describe("Group 1 - getCalendarHeight", () => {
      it("should return VSUtil.CALENDAR_BODY_HEIGHT when dropdownCalendar is true", () => {
         const { comp } = createYearCalendar({ model: { dropdownCalendar: true } });
         expect(comp.getCalendarHeight()).toBe(VSUtil.CALENDAR_BODY_HEIGHT);
      });

      it("should return objectFormat.height minus titleFormat.height when not dropdown", () => {
         const { comp } = createYearCalendar({ model: { dropdownCalendar: false } });
         // Defaults from makeYearCalendarModel: objectFormat.height=120, titleFormat.height=30
         expect(comp.getCalendarHeight()).toBe(90);
      });
   });

   // ─── Group 2 – getYear ───────────────────────────────────────────────────

   describe("Group 2 - getYear", () => {
      it("should return calendarTitleView1 when it is set and no year change has occurred", () => {
         const { comp } = createYearCalendar();
         comp.model.calendarTitleView1 = "Year 2025 (Custom)";
         // currentDate.year == ocurrentDate.year (no nextYear called) → no change
         expect(comp.getYear()).toBe("Year 2025 (Custom)");
      });

      it("should return the computed year string when calendarTitleView1 is null", () => {
         const { comp } = createYearCalendar(); // calendarTitleView1=null (default)
         expect(comp.getYear()).toBe("2025");
      });

      it("should return calendarTitleView2 for the secondary calendar when no change occurred", () => {
         const { comp } = createYearCalendar({ secondCalendar: true });
         comp.model.calendarTitleView2 = "Year 2026 (Custom)";
         expect(comp.getYear()).toBe("Year 2026 (Custom)");
      });

      it("should bypass calendarTitleView1 and return the current year after a year change", () => {
         const { comp } = createYearCalendar();
         comp.model.calendarTitleView1 = "Year 2025 (Custom)";
         // Simulate a year change by mutating currentDate
         comp.currentDate.year = 2027;
         // change=true → bypass cached view title
         expect(comp.getYear()).toBe("2027");
      });
   });

   // ─── Group 3 – getMonth ──────────────────────────────────────────────────

   describe("Group 3 - getMonth", () => {
      it("should return numeric month+1 when model.monthNames is not 12 entries", () => {
         const { comp } = createYearCalendar(); // monthNames=[] (length=0 ≠ 12)
         // row=0, col=0 → num=0 → returns 0+1=1
         expect(comp.getMonth(0, 0)).toBe(1);
         // row=1, col=3 → num=7 → returns 8
         expect(comp.getMonth(1, 3)).toBe(8);
      });

      it("should return model.monthNames[num] when monthNames has exactly 12 entries", () => {
         const monthNames = [
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
         ];
         const { comp } = createYearCalendar({ model: { monthNames: monthNames as any } });
         // row=0, col=2 → num=2 → "Mar"
         expect(comp.getMonth(0, 2)).toBe("Mar");
      });
   });

   // ─── Group 4 – getSelectionString ───────────────────────────────────────

   describe("Group 4 - getSelectionString", () => {
      it("should return empty string when dates is empty", () => {
         const { comp } = createYearCalendar();
         comp.dates = [];
         expect(comp.getSelectionString()).toBe("");
      });

      it("should return the year as a string for a year-type selection", () => {
         const { comp } = createYearCalendar();
         comp.dates = [{ year: 2025, dateType: "y", month: 0, value: 0 }] as any;
         expect(comp.getSelectionString()).toBe("2025");
      });

      it("should return 'year-rangeString' for month-type with no minimum argument", () => {
         const { comp } = createYearCalendar();
         comp.dates = [{ year: 2025, month: 3, dateType: "m", value: 0 }] as any;
         // getRangeString([{month:3}], true) → appendRange("", 3, 3, true) → "4" (zero=true adds 1)
         expect(comp.getSelectionString()).toBe("2025-4");
      });

      it("should return the first month for month-type when minimum=true", () => {
         const { comp } = createYearCalendar();
         comp.dates = [
            { year: 2025, month: 2, dateType: "m", value: 0 },
            { year: 2025, month: 7, dateType: "m", value: 0 },
         ] as any;
         // minimum=true → firstSelection: "2025-3" (month+1=3)
         expect(comp.getSelectionString(true)).toBe("2025-3");
      });

      it("should return the last month for month-type when minimum=false", () => {
         const { comp } = createYearCalendar();
         comp.dates = [
            { year: 2025, month: 2, dateType: "m", value: 0 },
            { year: 2025, month: 7, dateType: "m", value: 0 },
         ] as any;
         // minimum=false → lastSelection: "2025-8" (month+1=8)
         expect(comp.getSelectionString(false)).toBe("2025-8");
      });
   });

   // ─── Group 5 – getSelectedDates ─────────────────────────────────────────

   describe("Group 5 - getSelectedDates", () => {
      it("should return empty array when dates[0].dateType is 'y'", () => {
         const { comp } = createYearCalendar();
         comp.dates = [{ year: 2025, dateType: "y", month: 0, value: 0 }] as any;
         expect(comp.getSelectedDates()).toEqual([]);
      });

      it("should return the full dates array when dateType is 'm'", () => {
         const { comp } = createYearCalendar();
         comp.dates = [{ year: 2025, month: 3, dateType: "m", value: 0 }] as any;
         expect(comp.getSelectedDates().length).toBe(1);
         expect(comp.getSelectedDates()[0].dateType).toBe("m");
      });
   });

   // ─── Group 6 – isYearSelected ────────────────────────────────────────────

   describe("Group 6 - isYearSelected", () => {
      it("should return true when dates[0].dateType is 'y'", () => {
         const { comp } = createYearCalendar();
         comp.dates = [{ year: 2025, dateType: "y", month: 0, value: 0 }] as any;
         expect(comp.isYearSelected()).toBe(true);
      });

      it("should return false when dates is empty", () => {
         const { comp } = createYearCalendar();
         comp.dates = [];
         expect(comp.isYearSelected()).toBe(false);
      });

      it("should return false when dates contain only month-type selections", () => {
         const { comp } = createYearCalendar();
         comp.dates = [{ year: 2025, month: 3, dateType: "m", value: 0 }] as any;
         expect(comp.isYearSelected()).toBe(false);
      });
   });

   // ─── Group 7 – checkCurrentDate ─────────────────────────────────────────

   describe("Group 7 - checkCurrentDate", () => {
      it("should clamp year to maxYear when year exceeds range", () => {
         const { comp } = createYearCalendar();
         comp.currentDate = { year: 2030, month: 0 };
         comp.model.range = { minYear: -1, minMonth: -1, minDay: -1, maxYear: 2025, maxMonth: -1, maxDay: -1 };

         comp.checkCurrentDate();

         expect(comp.currentDate.year).toBe(2025);
      });

      it("should clamp year to minYear when year is below range", () => {
         const { comp } = createYearCalendar();
         comp.currentDate = { year: 2018, month: 0 };
         comp.model.range = { minYear: 2020, minMonth: -1, minDay: -1, maxYear: -1, maxMonth: -1, maxDay: -1 };

         comp.checkCurrentDate();

         expect(comp.currentDate.year).toBe(2020);
      });

      it("should not modify year when within range", () => {
         const { comp } = createYearCalendar();
         comp.currentDate = { year: 2025, month: 0 };
         comp.model.range = { minYear: 2020, minMonth: -1, minDay: -1, maxYear: 2030, maxMonth: -1, maxDay: -1 };

         comp.checkCurrentDate();

         expect(comp.currentDate.year).toBe(2025);
      });
   });

   // ─── Group 8 – isInRange ─────────────────────────────────────────────────

   describe("Group 8 - isInRange", () => {
      it("should return true for any month when range is unlimited (-1)", () => {
         const { comp } = createYearCalendar(); // range all -1
         expect(comp.isInRange(6)).toBe(true);
      });

      it("should return false for a month before the min boundary", () => {
         const { comp } = createYearCalendar({
            model: {
               range: { minYear: 2025, minMonth: 6, minDay: -1, maxYear: -1, maxMonth: -1, maxDay: -1 },
               currentDate1: { year: 2025, month: 0 },
            },
         });
         // currentDate.year == minYear, month=3 < minMonth=6 → false
         expect(comp.isInRange(3)).toBe(false);
      });

      it("should return true for a month at or above the min boundary", () => {
         const { comp } = createYearCalendar({
            model: {
               range: { minYear: 2025, minMonth: 6, minDay: -1, maxYear: -1, maxMonth: -1, maxDay: -1 },
               currentDate1: { year: 2025, month: 0 },
            },
         });
         expect(comp.isInRange(6)).toBe(true);
         expect(comp.isInRange(11)).toBe(true);
      });

      it("should return false for a month exceeding the max boundary", () => {
         const { comp } = createYearCalendar({
            model: {
               range: { minYear: -1, minMonth: -1, minDay: -1, maxYear: 2025, maxMonth: 8, maxDay: -1 },
               currentDate1: { year: 2025, month: 0 },
            },
         });
         // currentDate.year == maxYear, month=10 > maxMonth=8 → false
         expect(comp.isInRange(10)).toBe(false);
      });
   });

   // ─── Group 9 – isBtnEnabled ──────────────────────────────────────────────

   describe("Group 9 - isBtnEnabled", () => {
      it("should return true for both previous and next when range is unlimited (-1)", () => {
         const { comp } = createYearCalendar();
         expect(comp.isBtnEnabled(true)).toBe(true);
         expect(comp.isBtnEnabled(false)).toBe(true);
      });

      it("should return false for previous when currentDate.year is the minimum year", () => {
         const { comp } = createYearCalendar({
            model: {
               range: { minYear: 2025, minMonth: -1, minDay: -1, maxYear: 2030, maxMonth: -1, maxDay: -1 },
               currentDate1: { year: 2025, month: 0 },
            },
         });
         // previous=true, year-1=2024 < minYear=2025 → false
         expect(comp.isBtnEnabled(true)).toBe(false);
      });

      it("should return false for next when currentDate.year is the maximum year", () => {
         const { comp } = createYearCalendar({
            model: {
               range: { minYear: 2020, minMonth: -1, minDay: -1, maxYear: 2025, maxMonth: -1, maxDay: -1 },
               currentDate1: { year: 2025, month: 0 },
            },
         });
         // previous=false, year+1=2026 > maxYear=2025 → false
         expect(comp.isBtnEnabled(false)).toBe(false);
      });
   });

   // ─── Group 10 – getDateArray ─────────────────────────────────────────────

   describe("Group 10 - getDateArray", () => {
      it("should return 'y+year' strings for year-type entries", () => {
         const { comp } = createYearCalendar();
         comp.dates = [{ year: 2025, dateType: "y", month: 0, value: 0 }] as any;
         // format: dateType + year = "y2025" (no month appended for type "y")
         expect(comp.getDateArray()).toEqual(["y2025"]);
      });

      it("should return 'm+year-month' strings for month-type entries", () => {
         const { comp } = createYearCalendar();
         comp.dates = [{ year: 2025, month: 3, dateType: "m", value: 0 }] as any;
         // format: dateType + year + "-" + month = "m2025-3"
         expect(comp.getDateArray()).toEqual(["m2025-3"]);
      });
   });

   // ─── Group 11 – getCurrentDateString ────────────────────────────────────

   describe("Group 11 - getCurrentDateString", () => {
      it("should return just the year string when month is 0 (falsy)", () => {
         const { comp } = createYearCalendar(); // month reset to 0 by ngOnChanges
         expect(comp.getCurrentDateString()).toBe("2025");
      });

      it("should return 'year-month' when month is non-zero", () => {
         const { comp } = createYearCalendar();
         // currentDate is public; set month directly for this branch
         comp.currentDate.month = 5;
         expect(comp.getCurrentDateString()).toBe("2025-5");
      });
   });

   // ─── Group 12 – isCellFocused ────────────────────────────────────────────

   describe("Group 12 - isCellFocused", () => {
      it("should return true when row and col match selectedRow and selectedCol", () => {
         const { comp } = createYearCalendar();
         comp.selectedRow = 1;
         comp.selectedCol = 2;
         expect(comp.isCellFocused(1, 2)).toBe(true);
      });

      it("should return false when either row or col does not match", () => {
         const { comp } = createYearCalendar();
         comp.selectedRow = 1;
         comp.selectedCol = 2;
         expect(comp.isCellFocused(1, 3)).toBe(false);
         expect(comp.isCellFocused(0, 2)).toBe(false);
      });
   });

   // ─── Group 13 – vsWizard getter ──────────────────────────────────────────

   describe("Group 13 - vsWizard getter", () => {
      it("should return false when contextProvider.vsWizard is false", () => {
         const { comp } = createYearCalendar({ contextProvider: { vsWizard: false } });
         expect(comp.vsWizard).toBe(false);
      });

      it("should return true when contextProvider.vsWizard is true", () => {
         const { comp } = createYearCalendar({ contextProvider: { vsWizard: true } });
         expect(comp.vsWizard).toBe(true);
      });
   });

   // ─── Group 14 – resetOldDate ─────────────────────────────────────────────

   describe("Group 14 - resetOldDate", () => {
      it("should snapshot currentDate.year into ocurrentDate (deep clone)", () => {
         const { comp } = createYearCalendar();
         comp.currentDate.year = 2099;

         comp.resetOldDate();

         expect(comp.ocurrentDate.year).toBe(2099);
         // Verify it is a clone, not the same reference
         comp.currentDate.year = 2100;
         expect(comp.ocurrentDate.year).toBe(2099);
      });
   });
});
