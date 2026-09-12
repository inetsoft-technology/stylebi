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
 * VSCalendar - Pass 3: Display / Static
 *
 * Coverage:
 *   Group 1  - navigate() DOWN / UP / LEFT / RIGHT / SPACE row transitions
 *   Group 2  - clearNavSelection() resets all navigation state
 *   Group 3  - isMiniBarSelected getter
 *   Group 4  - syncRangeHighlight() branch logic
 *   Group 5  - topPosition / height / bottomTabFlipped getters
 *   Group 6  - static getIconColor()
 *   Group 7  - static getRangeString() + appendRange()
 *   Group 8  - static calendarComparator()
 *   Group 9  - getPendingIconPosition()
 *   Group 10 - getHTMLText()
 */

import { VSCalendar, SelectionRegions } from "./vs-calendar.component";
import { NavigationKeys } from "../navigation-keys";
import {
   createCalendarComponent,
   makeCalendarModel,
   makeCalendarRef,
} from "./vs-calendar.component.test-helpers";
import { SelectedDateModel } from "../../model/calendar/current-date-model";

afterEach(() => vi.restoreAllMocks());

// ─── Group 1 – navigate() ────────────────────────────────────────────────────

describe("VSCalendar - display", () => {
   describe("Group 1 - navigate()", () => {
      it("should move selectedCellRow to MENU region on first DOWN press when dropdown calendar is closed", () => {
         const { comp } = createCalendarComponent({
            model: { dropdownCalendar: true, calendarsShown: false },
         });
         comp.selectedCellRow = SelectionRegions.NONE;

         // navigate is protected — cast needed to call it directly
         (comp as any).navigate(NavigationKeys.DOWN);

         expect(comp.selectedCellRow).toBe(SelectionRegions.MENU);
         expect(comp.selectedCellCol).toBe(0);
      });

      it("should move selectedCellRow to 0 on first DOWN press when not a dropdown calendar", () => {
         const { comp } = createCalendarComponent({
            model: { dropdownCalendar: false },
         });
         comp.selectedCellRow = SelectionRegions.NONE;

         (comp as any).navigate(NavigationKeys.DOWN);

         expect(comp.selectedCellRow).toBe(0);
         expect(comp.selectedCellCol).toBe(0);
      });

      it("should increment selectedCellRow on DOWN when within the grid", () => {
         const { comp } = createCalendarComponent({ model: { yearView: false } });
         comp.selectedCellRow = 2;
         (comp as any).calendar1 = makeCalendarRef();

         (comp as any).navigate(NavigationKeys.DOWN);

         expect(comp.selectedCellRow).toBe(3);
      });

      it("should cap selectedCellRow at maxRow-1 on DOWN when at the last row", () => {
         const { comp } = createCalendarComponent({ model: { yearView: false } });
         const maxRow = 6;
         comp.selectedCellRow = maxRow - 1;
         (comp as any).calendar1 = makeCalendarRef();

         (comp as any).navigate(NavigationKeys.DOWN);

         expect(comp.selectedCellRow).toBe(maxRow - 1);
      });

      it("should decrement selectedCellRow on UP when within the grid", () => {
         const { comp } = createCalendarComponent();
         comp.selectedCellRow = 3;
         (comp as any).calendar1 = makeCalendarRef();

         (comp as any).navigate(NavigationKeys.UP);

         expect(comp.selectedCellRow).toBe(2);
      });

      it("should move to TITLE region on UP when at row 0", () => {
         const { comp } = createCalendarComponent();
         comp.selectedCellRow = 0;
         (comp as any).calendar1 = makeCalendarRef();

         (comp as any).navigate(NavigationKeys.UP);

         expect(comp.selectedCellRow).toBe(SelectionRegions.TITLE);
         expect(comp.selectedCellCol).toBe(0);
      });

      it("should move to DROPDOWN on RIGHT when at MENU region", () => {
         const { comp } = createCalendarComponent();
         comp.selectedCellRow = SelectionRegions.MENU;
         (comp as any).calendar1 = makeCalendarRef();

         (comp as any).navigate(NavigationKeys.RIGHT);

         expect(comp.selectedCellRow).toBe(SelectionRegions.DROPDOWN);
      });

      it("should move to MENU on LEFT when at DROPDOWN region", () => {
         const { comp } = createCalendarComponent();
         comp.selectedCellRow = SelectionRegions.DROPDOWN;
         (comp as any).calendar1 = makeCalendarRef();

         (comp as any).navigate(NavigationKeys.LEFT);

         expect(comp.selectedCellRow).toBe(SelectionRegions.MENU);
      });

      it("should call calendar.nextYear(-1) on SPACE when row is LAST_YEAR", () => {
         const { comp } = createCalendarComponent();
         comp.selectedCellRow = SelectionRegions.LAST_YEAR;
         const cal1 = makeCalendarRef();
         (comp as any).calendar1 = cal1;

         (comp as any).navigate(NavigationKeys.SPACE);

         expect(cal1.nextYear).toHaveBeenCalledWith(-1);
      });

      it("should call calendar.nextYear(1) on SPACE when row is NEXT_YEAR", () => {
         const { comp } = createCalendarComponent();
         comp.selectedCellRow = SelectionRegions.NEXT_YEAR;
         const cal1 = makeCalendarRef();
         (comp as any).calendar1 = cal1;

         (comp as any).navigate(NavigationKeys.SPACE);

         expect(cal1.nextYear).toHaveBeenCalledWith(1);
      });

      it("should call toggleDropdown on SPACE when row is DROPDOWN and calendar is shown", () => {
         const { comp } = createCalendarComponent({ model: { calendarsShown: true } });
         comp.selectedCellRow = SelectionRegions.DROPDOWN;
         (comp as any).calendar1 = makeCalendarRef();
         const hideSpy = vi.spyOn(comp, "onHide");

         (comp as any).navigate(NavigationKeys.SPACE);

         expect(hideSpy).toHaveBeenCalledTimes(1);
      });

      it("should call calendar.clickCell on SPACE when row is a data cell", () => {
         const { comp } = createCalendarComponent();
         comp.selectedCellRow = 2;
         comp.selectedCellCol = 3;
         const cal1 = makeCalendarRef();
         (comp as any).calendar1 = cal1;

         (comp as any).navigate(NavigationKeys.SPACE);

         expect(cal1.clickCell).toHaveBeenCalledWith(2, 3, expect.objectContaining({ ctrlKey: false }));
      });
   });

   // ─── Group 2 – clearNavSelection() ───────────────────────────────────────

   describe("Group 2 - clearNavSelection()", () => {
      it("should reset selectedCellRow/Col and keyNavFocused", () => {
         const { comp } = createCalendarComponent();
         comp.selectedCellRow = 3;
         comp.selectedCellCol = 2;
         comp.keyNavFocused = true;
         comp.leftCalendar = false;

         // clearNavSelection is protected — cast needed to call it
         (comp as any).clearNavSelection();

         expect(comp.selectedCellRow).toBe(SelectionRegions.NONE);
         expect(comp.selectedCellCol).toBe(SelectionRegions.NONE);
         expect(comp.keyNavFocused).toBe(false);
         expect(comp.leftCalendar).toBe(true);
      });
   });

   // ─── Group 3 – isMiniBarSelected ─────────────────────────────────────────

   describe("Group 3 - isMiniBarSelected", () => {
      it("should return true when selectedCellRow is MENU", () => {
         const { comp } = createCalendarComponent();
         comp.selectedCellRow = SelectionRegions.MENU;
         expect(comp.isMiniBarSelected).toBe(true);
      });

      it("should return true when selectedCellRow is DROPDOWN", () => {
         const { comp } = createCalendarComponent();
         comp.selectedCellRow = SelectionRegions.DROPDOWN;
         expect(comp.isMiniBarSelected).toBe(true);
      });

      it("should return false when selectedCellRow is a data cell row", () => {
         const { comp } = createCalendarComponent();
         comp.selectedCellRow = 2;
         expect(comp.isMiniBarSelected).toBe(false);
      });
   });

   // ─── Group 4 – syncRangeHighlight() ──────────────────────────────────────

   describe("Group 4 - syncRangeHighlight()", () => {
      it("should do nothing when either calendar is absent", () => {
         const { comp } = createCalendarComponent();
         (comp as any).calendar2 = null;
         expect(() => comp.syncRangeHighlight(true)).not.toThrow();
      });

      it("should call updateSelected(true) on currentCalendar when only currentCalendar has a single date", () => {
         const { comp } = createCalendarComponent();
         const currentDate: SelectedDateModel = { year: 2025, month: 3, value: 1, dateType: "d" } as any;
         const cal1 = makeCalendarRef({
            getSelectedDates: vi.fn().mockReturnValue([currentDate]),
            currentDate: { year: 2025, month: 3 },
         });
         const cal2 = makeCalendarRef({
            getSelectedDates: vi.fn().mockReturnValue([]),
            currentDate: { year: 2025, month: 4 },
         });
         (comp as any).calendar1 = cal1;
         (comp as any).calendar2 = cal2;

         // secondCalendar=false → currentCalendar=cal1
         comp.syncRangeHighlight(false);

         expect(cal1.updateSelected).toHaveBeenCalledWith(true);
      });

      it("should call paintRange on both calendars when each has at least one date", () => {
         const { comp } = createCalendarComponent();
         const date1: SelectedDateModel = { year: 2025, month: 3, value: 1, dateType: "d" } as any;
         const date2: SelectedDateModel = { year: 2025, month: 4, value: 15, dateType: "d" } as any;
         const cal1 = makeCalendarRef({
            getSelectedDates: vi.fn().mockReturnValue([date1]),
            currentDate: { year: 2025, month: 3 },
         });
         const cal2 = makeCalendarRef({
            getSelectedDates: vi.fn().mockReturnValue([date2]),
            currentDate: { year: 2025, month: 5 },
         });
         (comp as any).calendar1 = cal1;
         (comp as any).calendar2 = cal2;

         // secondCalendar=false → currentCalendar=cal1, otherCalendar=cal2.
         // Different currentDate month means the "same-month" branch is skipped;
         // the "both non-empty" branch fires: paintRange(currentDates) on cal1, paintRange(otherDates) on cal2.
         comp.syncRangeHighlight(false);

         expect(cal1.paintRange).toHaveBeenCalledWith([date1]);
         expect(cal2.paintRange).toHaveBeenCalledWith([date2]);
      });
   });

   // ─── Group 5 – topPosition / height / bottomTabFlipped getters ───────────

   describe("Group 5 - topPosition, height, bottomTabFlipped getters", () => {
      it("should return objectFormat.top in viewer mode when not a dropdown calendar", () => {
         const { comp } = createCalendarComponent({
            context: { viewer: true, preview: false, binding: false, composer: false,
               vsWizard: false, vsWizardPreview: false, embedAssembly: false },
            model: { dropdownCalendar: false },
         });
         expect(comp.topPosition).toBe(comp.model.objectFormat.top);
      });

      it("should return null for topPosition when not in viewer context", () => {
         const { comp } = createCalendarComponent({
            context: { viewer: false, preview: false, binding: false, composer: true,
               vsWizard: false, vsWizardPreview: false, embedAssembly: false },
         });
         expect(comp.topPosition).toBeNull();
      });

      it("should return objectFormat.height for height when not a dropdown calendar", () => {
         const { comp } = createCalendarComponent({ model: { dropdownCalendar: false } });
         expect(comp.height).toBe(comp.model.objectFormat.height);
      });

      it("should return titleFormat.height for height when dropdown calendar and calendars are hidden", () => {
         const { comp } = createCalendarComponent({
            model: { dropdownCalendar: true, calendarsShown: false },
         });
         expect(comp.height).toBe(comp.model.titleFormat.height);
      });

      it("should return false for bottomTabFlipped when vsInfo has no vsObjects list", () => {
         const { comp } = createCalendarComponent();
         // vsInfo is set to an empty viewsheet — not in a bottom tab container
         expect(comp.bottomTabFlipped).toBe(false);
      });
   });

   // ─── Group 6 – static getIconColor() ────────────────────────────────────

   describe("Group 6 - static getIconColor()", () => {
      it("should return a hex string derived from the model foreground color", () => {
         const model = makeCalendarModel();
         model.objectFormat.foreground = "#000000";
         const result = VSCalendar.getIconColor(model);
         expect(result).toMatch(/^#[0-9a-f]{6}$/i);
      });

      it("should return 'inherit' when the model has no objectFormat", () => {
         const model = makeCalendarModel();
         (model as any).objectFormat = null;
         expect(VSCalendar.getIconColor(model)).toBe("inherit");
      });

      it("should return 'inherit' when the model itself is null", () => {
         expect(VSCalendar.getIconColor(null)).toBe("inherit");
      });
   });

   // ─── Group 7 – static getRangeString() + appendRange() ──────────────────

   describe("Group 7 - static getRangeString() + appendRange()", () => {
      it("should return '-1' for an empty dates array (sentinel start/end values are always appended)", () => {
         expect(VSCalendar.getRangeString([], false)).toBe("-1");
      });

      it("should build a single-value range string for a lone date", () => {
         const dates: SelectedDateModel[] = [
            { year: 2025, month: 3, value: 5, dateType: "d" },
         ] as any;
         expect(VSCalendar.getRangeString(dates, false)).toBe("5");
      });

      it("should build a contiguous range string for consecutive dates", () => {
         const dates: SelectedDateModel[] = [
            { year: 2025, month: 3, value: 5, dateType: "d" },
            { year: 2025, month: 3, value: 6, dateType: "d" },
            { year: 2025, month: 3, value: 7, dateType: "d" },
         ] as any;
         expect(VSCalendar.getRangeString(dates, false)).toBe("5.7");
      });

      it("should split non-contiguous dates into separate range segments", () => {
         const dates: SelectedDateModel[] = [
            { year: 2025, month: 3, value: 1, dateType: "d" },
            { year: 2025, month: 3, value: 3, dateType: "d" },
         ] as any;
         expect(VSCalendar.getRangeString(dates, false)).toBe("1,3");
      });

      it("should offset values by 1 when zero is true in appendRange", () => {
         expect(VSCalendar.appendRange("", 0, 2, true)).toBe("1.3");
      });

      it("should return a single value when start equals end in appendRange", () => {
         expect(VSCalendar.appendRange("", 5, 5, false)).toBe("5");
      });

      it("should prepend comma when the accumulator is non-empty in appendRange", () => {
         expect(VSCalendar.appendRange("1.3", 5, 7, false)).toBe("1.3,5.7");
      });
   });

   // ─── Group 8 – static calendarComparator() ───────────────────────────────

   describe("Group 8 - static calendarComparator()", () => {
      it("should return negative when a.year < b.year", () => {
         const a = { year: 2024, month: 0, value: 1 } as SelectedDateModel;
         const b = { year: 2025, month: 0, value: 1 } as SelectedDateModel;
         expect(VSCalendar.calendarComparator(a, b)).toBeLessThan(0);
      });

      it("should return positive when a.year > b.year", () => {
         const a = { year: 2026, month: 0, value: 1 } as SelectedDateModel;
         const b = { year: 2025, month: 0, value: 1 } as SelectedDateModel;
         expect(VSCalendar.calendarComparator(a, b)).toBeGreaterThan(0);
      });

      it("should sort by month when years are equal", () => {
         const a = { year: 2025, month: 2, value: 1 } as SelectedDateModel;
         const b = { year: 2025, month: 5, value: 1 } as SelectedDateModel;
         expect(VSCalendar.calendarComparator(a, b)).toBeLessThan(0);
      });

      it("should sort by value when year and month are equal", () => {
         const a = { year: 2025, month: 3, value: 1 } as SelectedDateModel;
         const b = { year: 2025, month: 3, value: 10 } as SelectedDateModel;
         expect(VSCalendar.calendarComparator(a, b)).toBeLessThan(0);
      });

      it("should return 0 for identical dates", () => {
         const a = { year: 2025, month: 3, value: 5 } as SelectedDateModel;
         const b = { year: 2025, month: 3, value: 5 } as SelectedDateModel;
         expect(VSCalendar.calendarComparator(a, b)).toBe(0);
      });
   });

   // ─── Group 9 – getPendingIconPosition() ─────────────────────────────────

   describe("Group 9 - getPendingIconPosition()", () => {
      it("should return 3 when titleVisible is true", () => {
         const { comp } = createCalendarComponent({ model: { titleVisible: true } });
         expect(comp.getPendingIconPosition(comp.model)).toBe(3);
      });

      it("should return 40 when titleVisible is false", () => {
         const { comp } = createCalendarComponent({ model: { titleVisible: false } });
         expect(comp.getPendingIconPosition(comp.model)).toBe(40);
      });
   });

   // ─── Group 10 – getHTMLText() ─────────────────────────────────────────────

   describe("Group 10 - getHTMLText()", () => {
      it("should pass selectionTitle through GuiTool.getHTMLText", () => {
         const { comp } = createCalendarComponent();
         // GuiTool.getHTMLText: replaces \n→<br>, leading spaces→&nbsp;, double-space→" &nbsp;"
         // Plain text with no newlines or extra spaces is returned verbatim
         comp.selectionTitle = "A & B";
         expect(comp.getHTMLText()).toBe("A & B");
      });

      it("should return an empty-like string when selectionTitle is not set", () => {
         const { comp } = createCalendarComponent();
         comp.selectionTitle = undefined;
         const result = comp.getHTMLText();
         expect(result).toBeFalsy();
      });
   });
});
