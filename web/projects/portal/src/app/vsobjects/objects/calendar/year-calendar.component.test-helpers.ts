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
 * Shared test helpers for YearCalendar multi-pass TL specs.
 *
 * YearCalendar has a 2-parameter constructor:
 *   (ChangeDetectorRef, ContextProvider)
 *
 * Direct instantiation (not ATL render()) is used throughout. After construction,
 * ngOnChanges is called with a model SimpleChange. YearCalendar.ngOnChanges ONLY
 * initializes when changes["model"] is present, so we always pass a model change.
 *
 * Default model uses:
 *   - range: all -1 (unlimited)
 *   - currentDate1: { year: 2025, month: 0 }  (month is reset to 0 by ngOnChanges)
 */

import { SimpleChange } from "@angular/core";
import { TestUtils } from "../../../common/test/test-utils";
import { VSCalendarModel } from "../../model/calendar/vs-calendar-model";
import { YearCalendar } from "./year-calendar.component";

export interface YearCalendarTestContext {
   comp: YearCalendar;
   changeDetectorRef: any;
   contextProvider: any;
}

export interface YearCalendarTestOverrides {
   model?: Partial<VSCalendarModel>;
   secondCalendar?: boolean;
   contextProvider?: Partial<{ vsWizard: boolean }>;
}

export function makeYearCalendarModel(
   overrides: Partial<VSCalendarModel> = {},
): VSCalendarModel {
   const base = TestUtils.createMockVSCalendarModel("Calendar1");
   return Object.assign(base, {
      absoluteName: "Calendar1",
      range: { minYear: -1, minMonth: -1, minDay: -1, maxYear: -1, maxMonth: -1, maxDay: -1 },
      currentDate1: { year: 2025, month: 0 },
      currentDate2: { year: 2026, month: 0 },
      objectFormat: {
         ...TestUtils.createMockVSFormatModel(),
         width: 200,
         height: 120,
         foreground: "#000000",
      },
      yearFormat: { ...TestUtils.createMockVSFormatModel() },
      titleFormat: { ...TestUtils.createMockVSFormatModel(), height: 30 },
   } as any, overrides) as VSCalendarModel;
}

/**
 * Creates a ready-to-test YearCalendar instance.
 *
 * After returning, comp.currentDate points to model.currentDate1 (or currentDate2 when
 * secondCalendar=true), comp.currentDate.month is 0 (YearCalendar always resets it),
 * comp.changed=true, and comp.selectedMonth[] has been updated.
 */
export function createYearCalendar(
   overrides: YearCalendarTestOverrides = {},
): YearCalendarTestContext {
   const changeDetectorRef = {
      detectChanges: vi.fn(),
      markForCheck: vi.fn(),
   };

   const contextProvider = {
      vsWizard: false,
      ...(overrides.contextProvider ?? {}),
   };

   const comp = new YearCalendar(
      changeDetectorRef as any,
      contextProvider as any,
   );

   comp.secondCalendar = overrides.secondCalendar ?? false;
   comp.model = makeYearCalendarModel(overrides.model ?? {});
   comp.ngOnChanges({ model: new SimpleChange(null, comp.model, true) } as any);

   return { comp, changeDetectorRef, contextProvider };
}
