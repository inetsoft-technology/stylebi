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
 * Shared test helpers for MonthCalendar multi-pass TL specs.
 *
 * MonthCalendar has a 3-parameter constructor:
 *   (ChangeDetectorRef, FirstDayOfWeekService, ContextProvider)
 *
 * Direct instantiation (not ATL render()) is used throughout. After construction,
 * ngOnChanges is called with a model SimpleChange to populate days[], currentDate, etc.
 *
 * Default model uses:
 *   - range: all -1 (unlimited) to prevent checkCurrentDate from clamping year 2025
 *   - currentDate1: { year: 2025, month: 3 }  →  April 2025
 *   - dayNames: null  →  getDay() returns numeric day (not undefined array lookup)
 */

import { SimpleChange } from "@angular/core";
import { of } from "rxjs";
import { TestUtils } from "../../../common/test/test-utils";
import { VSCalendarModel } from "../../model/calendar/vs-calendar-model";
import { MonthCalendar } from "./month-calendar.component";

export interface MonthCalendarTestContext {
   comp: MonthCalendar;
   changeDetectorRef: any;
   firstDayOfWeekService: any;
   contextProvider: any;
}

export interface MonthCalendarTestOverrides {
   model?: Partial<VSCalendarModel>;
   /** Sets firstDayOfWeek returned by the service mock (default: 1 = Monday) */
   firstDayOfWeek?: number;
   secondCalendar?: boolean;
   contextProvider?: Partial<{ vsWizard: boolean }>;
}

export function makeMonthCalendarModel(
   overrides: Partial<VSCalendarModel> = {},
): VSCalendarModel {
   const base = TestUtils.createMockVSCalendarModel("Calendar1");
   return Object.assign(base, {
      absoluteName: "Calendar1",
      // All -1: unlimited range so year=2025 is not clamped by checkCurrentDate
      range: { minYear: -1, minMonth: -1, minDay: -1, maxYear: -1, maxMonth: -1, maxDay: -1 },
      currentDate1: { year: 2025, month: 3 }, // April 2025
      currentDate2: { year: 2025, month: 4 }, // May 2025 (for secondCalendar tests)
      objectFormat: {
         ...TestUtils.createMockVSFormatModel(),
         width: 200,
         height: 120,
         foreground: "#000000",
      },
      monthFormat: { ...TestUtils.createMockVSFormatModel() },
      titleFormat: { ...TestUtils.createMockVSFormatModel(), height: 30 },
      // null so getDay() returns the numeric day, not undefined from empty-array lookup
      dayNames: null,
   } as any, overrides) as VSCalendarModel;
}

/**
 * Creates a ready-to-test MonthCalendar instance.
 *
 * After returning, comp.days[] is populated (42 entries), comp.currentDate points to
 * model.currentDate1 (or currentDate2 when secondCalendar=true), and comp.changed=true.
 */
export function createMonthCalendar(
   overrides: MonthCalendarTestOverrides = {},
): MonthCalendarTestContext {
   const changeDetectorRef = {
      detectChanges: vi.fn(),
      markForCheck: vi.fn(),
   };

   const javaFirstDay = overrides.firstDayOfWeek ?? 1;
   const firstDayOfWeekService = {
      getFirstDay: vi.fn().mockReturnValue(of({ javaFirstDay: javaFirstDay, isoFirstDay: javaFirstDay })),
   };

   const contextProvider = {
      vsWizard: false,
      ...(overrides.contextProvider ?? {}),
   };

   const comp = new MonthCalendar(
      changeDetectorRef as any,
      firstDayOfWeekService as any,
      contextProvider as any,
   );

   comp.secondCalendar = overrides.secondCalendar ?? false;
   comp.model = makeMonthCalendarModel(overrides.model ?? {});
   // Simulate Angular passing a new model @Input: initializes currentDate, populates days[]
   comp.ngOnChanges({ model: new SimpleChange(null, comp.model, true) } as any);

   return { comp, changeDetectorRef, firstDayOfWeekService, contextProvider };
}
