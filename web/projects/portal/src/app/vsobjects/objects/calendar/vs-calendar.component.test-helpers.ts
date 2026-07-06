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
 * Shared test helpers for VSCalendar multi-pass TL specs.
 *
 * VSCalendar extends NavigationComponent<VSCalendarModel> and has an 11-parameter
 * constructor. Direct instantiation (not ATL render()) is used throughout because
 * the DI chain is too deep for render() without a full Angular module.
 */

import { of, Subject } from "rxjs";
import { TestUtils } from "../../../common/test/test-utils";
import { ViewsheetInfo } from "../../data/viewsheet-info";
import { VSCalendarModel } from "../../model/calendar/vs-calendar-model";
import { VSCalendar } from "./vs-calendar.component";
import { GlobalSubmitService } from "../../util/global-submit.service";

// ─── Model factory ──────────────────────────────────────────────────────────

export function makeCalendarModel(
   overrides: Partial<VSCalendarModel> = {},
): VSCalendarModel {
   const base = TestUtils.createMockVSCalendarModel("Calendar1");
   return Object.assign(base, {
      absoluteName: "Calendar1",
      objectFormat: {
         ...TestUtils.createMockVSFormatModel(),
         width: 200,
         height: 120,
         top: 50,
         left: 30,
         border: { bottom: "1px solid", top: "1px solid", left: "1px solid", right: "1px solid" },
         foreground: "#000000",
      },
   } as any, overrides) as VSCalendarModel;
}

// ─── Calendar ViewChild mock factory ────────────────────────────────────────

/** Creates a mock MonthCalendar / YearCalendar ViewChild reference. */
export function makeCalendarRef(overrides: any = {}) {
   const el = () => ({ nativeElement: { focus: vi.fn() } });
   return {
      getSelectionString: vi.fn().mockReturnValue(""),
      getDateArray: vi.fn().mockReturnValue([]),
      getCurrentDateString: vi.fn().mockReturnValue("2025-0"),
      dates: [],
      currentDate: { year: 2025, month: 0 },
      resetOldDate: vi.fn(),
      paintRange: vi.fn(),
      syncPeriod: vi.fn(),
      updateSelected: vi.fn(),
      nextYear: vi.fn(),
      nextMonth: vi.fn(),
      listRef: el(),
      lastYearRef: el(),
      titleRef: el(),
      nextYearRef: el(),
      lastMonthRef: el(),
      nextMonthRef: el(),
      getSelectedDates: vi.fn().mockReturnValue([]),
      syncDate: vi.fn(),
      dateChanged: vi.fn(),
      resetDays: vi.fn(),
      clearSelection: vi.fn(),
      clickCell: vi.fn(),
      clickYearTitle: vi.fn(),
      clickMonthTitle: vi.fn(),
      ...overrides,
   };
}

// ─── Component factory ───────────────────────────────────────────────────────

export interface CalendarTestOverrides {
   model?: Partial<VSCalendarModel>;
   http?: any;
   globalSubmitService?: GlobalSubmitService | any;
   context?: any;
   formDataService?: any;
   submitOnChange?: boolean;
}

export interface CalendarTestContext {
   comp: VSCalendar;
   socket: any;
   renderer: any;
   formDataService: any;
   globalSubmitService: GlobalSubmitService;
   changeDetectorRef: any;
}

/**
 * Creates a fully-wired VSCalendar instance with both calendar ViewChild mocks
 * pre-installed. Pass `overrides.http` when you need to intercept HTTP via
 * HttpTestingController (P2 risk tests).
 */
export function createCalendarComponent(
   overrides: CalendarTestOverrides = {},
): CalendarTestContext {
   const socket = {
      sendEvent: vi.fn(),
      runtimeId: "vs1",
      commands: new Subject<any>().asObservable(),
   };

   const renderer = {
      listen: vi.fn().mockReturnValue(vi.fn()),
      setStyle: vi.fn(),
      removeStyle: vi.fn(),
      setAttribute: vi.fn(),
      addClass: vi.fn(),
      removeClass: vi.fn(),
   };

   const changeDetectorRef = {
      detectChanges: vi.fn(),
      markForCheck: vi.fn(),
   };

   // By default immediately invoke the confirmed callback (no form tables in play).
   const formDataService = overrides.formDataService ?? {
      checkFormData: vi.fn((rId: any, nm: any, sel: any, cb: Function) => cb()),
   };

   const dataTipService = {
      isDataTip: vi.fn().mockReturnValue(false),
      isDataTipVisible: vi.fn().mockReturnValue(false),
   };

   const context = overrides.context ?? {
      viewer: true,
      preview: false,
      binding: false,
      composer: false,
      vsWizard: false,
      vsWizardPreview: false,
      embedAssembly: false,
   };

   const zone = {
      run: (fn: Function) => fn(),
      runOutsideAngular: (fn: Function) => fn(),
   };

   const dropdownService = { open: vi.fn() };

   // Default http mock returns an empty string observable — override for HTTP tests.
   const http = overrides.http ?? {
      post: vi.fn().mockReturnValue(of("")),
   };

   const globalSubmitService: GlobalSubmitService =
      overrides.globalSubmitService ?? new GlobalSubmitService();

   const comp = new VSCalendar(
      { nativeElement: { contains: vi.fn().mockReturnValue(false) } } as any,
      socket as any,
      renderer as any,
      changeDetectorRef as any,
      formDataService as any,
      dataTipService as any,
      context as any,
      zone as any,
      dropdownService as any,
      http as any,
      globalSubmitService,
   );

   comp.vsInfo = new ViewsheetInfo([], null, false, "vs1");
   comp.model = makeCalendarModel(overrides.model ?? {});

   // Pre-install calendar ViewChild mocks so callers can use them directly.
   (comp as any).calendar1 = makeCalendarRef();
   (comp as any).calendar2 = null;

   return {
      comp,
      socket,
      renderer,
      formDataService,
      globalSubmitService,
      changeDetectorRef,
   };
}
