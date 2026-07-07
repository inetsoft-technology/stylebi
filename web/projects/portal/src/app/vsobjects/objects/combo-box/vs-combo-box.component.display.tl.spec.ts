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
 * VSComboBox — Pass 3: Display
 *
 * Direct instantiation (no ATL render) — pure conditional/display computation and keyboard
 * navigation dispatch.
 *
 *   Group 1  [Risk 2] — inputPlaceholder: dataType-driven switch
 *   Group 2  [Risk 3] — navigate: entry-point dispatch from FocusRegions.NONE + directional moves
 *   Group 3  [Risk 2] — navigate: calendar/time index stepping bounds
 *   Group 4  [Risk 2] — focusOnRegion: per-region ViewChild focus + guarded no-op
 *   Group 5  [Risk 1] — clearNavSelection
 *   Group 6  [Risk 1] — showCalendar / showTime boolean getters
 *   Group 7  [Risk 2] — getDateString: raw vs formatted vs empty
 *   Group 8  [Risk 2] — getDate / getTimeInstant: string parsing, serverTZ branch
 *   Group 9  [Risk 3] — getDateTime: meridian/hour 24h conversion, serverTZ branch
 *   Group 10 [Risk 3] — isValidDate: range check (includes a confirmed off-by-one-month bug in
 *                        the default min/max Date construction — see it.fails)
 *   Group 11 [Risk 1] — isSelected boolean getter
 *   Group 12 [Risk 1] — getLabelIndex / getValueIndex
 *   Group 13 [Risk 2] — updateHours / updateMinutes / updateSeconds / updateMeridian (symmetric
 *                        field-update + applyDateSelection dispatch)
 *   Group 14 [Risk 3] — applyDateSelection: ctrlDown defer, invalid-date dialog, refresh vs
 *                        pending-value branches
 *   Group 15 [Risk 2] — Legacy DOM regressions ported verbatim from vs-combo-box.spec.ts (bugs
 *                        #17282, #18439): uses a real TestBed render (not direct instantiation)
 *                        because these assertions are about real template output (rendered
 *                        placeholder attribute, select element's text-align-last style) that
 *                        cannot be observed on a directly-instantiated component.
 *
 * Confirmed bugs (it.fails):
 *   Bug (no ticket found) — isValidDate default bounds (Group 10): defaultMinDate/defaultMaxDate
 *   are declared as {year:1900,month:1,day:1} / {year:2050,month:12,day:31} (1-based NgbDateStruct
 *   month), but isValidDate() passes those fields straight into `new Date(year, month, day)`,
 *   whose month parameter is 0-based. This shifts the effective default bounds to Feb 1, 1900 and
 *   Jan 31, 2051 — a date the UI documents as in-range (e.g. Jan 15, 1900) is incorrectly rejected.
 *
 * Out of scope this pass: ngOnInit, ngOnChanges, ngOnDestroy, model setter, onChange, onBlur,
 * onInputDate, onEnter, selectItem, toggleDropdown, applySelection, clearCalendar, updateDate,
 * selectLabel, clearLabelSelection, onKeyUp, onKeyDown, labelSearch — covered in
 * vs-combo-box.component.interaction.tl.spec.ts.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed, waitForAsync } from "@angular/core/testing";
import { FormsModule } from "@angular/forms";
import { of } from "rxjs";
import { XSchema } from "../../../common/data/xschema";
import { TestUtils } from "../../../common/test/test-utils";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { DebounceService } from "../../../widget/services/debounce.service";
import { ContextProvider } from "../../context-provider.service";
import { CheckFormDataService } from "../../util/check-form-data.service";
import { FormInputService } from "../../util/form-input.service";
import { DataTipService } from "../data-tip/data-tip.service";
import { PopComponentService } from "../data-tip/pop-component.service";
import { VSPopComponentDirective } from "../data-tip/vs-pop-component.directive";
import { FirstDayOfWeekService } from "../../../common/services/first-day-of-week.service";
import { MessageDialog } from "../../../widget/dialog/message-dialog/message-dialog.component";
import { NavigationKeys } from "../navigation-keys";
import { VSComboBox } from "./vs-combo-box.component";
import { createComboBoxComponent } from "./vs-combo-box.component.test-helpers";

afterEach(() => vi.restoreAllMocks());

// applyDateSelection() routes through ComponentTool.showMessageDialog, which has a 500ms dedup
// guard keyed on the message string — reset it so Group 14's dialog test is never silently skipped.
beforeEach(() => {
   MessageDialog.lastMessage = null;
   (MessageDialog as any).lastMessageTS = 0;
});

describe("VSComboBox — Pass 3: Display", () => {

   // ── Group 1 — inputPlaceholder ────────────────────────────────────────────
   describe("Group 1 — inputPlaceholder", () => {
      it("should return the model's dateFormat for a DATE column", () => {
         const { comp } = createComboBoxComponent({
            model: { dataType: XSchema.DATE, dateFormat: "MM/dd/yyyy" },
         });

         expect(comp.inputPlaceholder).toBe("MM/dd/yyyy");
      });

      // TIME_INSTANT shares the same switch case as DATE.
      it("should default to 'yyyy-MM-dd' for a DATE/TIME_INSTANT column with no dateFormat", () => {
         const { comp } = createComboBoxComponent({
            model: { dataType: XSchema.TIME_INSTANT, dateFormat: null },
         });

         expect(comp.inputPlaceholder).toBe("yyyy-MM-dd");
      });

      it("should return the fixed time placeholder for a TIME column", () => {
         const { comp } = createComboBoxComponent({ model: { dataType: XSchema.TIME } });

         expect(comp.inputPlaceholder).toBe("HH:mm:ss AM[PM]");
      });

      it("should return an empty string for a non-date/time column", () => {
         const { comp } = createComboBoxComponent({ model: { dataType: "string" } });

         expect(comp.inputPlaceholder).toBe("");
      });
   });

   // ── Group 2 — navigate: entry point + directional moves ──────────────────
   describe("Group 2 — navigate entry + directional moves", () => {
      it("should focus HOUR from NONE when dataType is TIME", () => {
         const { comp } = createComboBoxComponent({ model: { dataType: XSchema.TIME } });
         comp.hourRef = { nativeElement: { focus: vi.fn() } } as any;

         (comp as any).navigate(NavigationKeys.RIGHT);

         expect(comp.focused).toBe(comp.FocusRegions.HOUR);
         expect(comp.hourRef.nativeElement.focus).toHaveBeenCalled();
      });

      it("should focus INPUT from NONE when editable and an input element exists", () => {
         const { comp } = createComboBoxComponent({ model: { editable: true } });
         (comp as any).input = { nativeElement: { focus: vi.fn() } };

         (comp as any).navigate(NavigationKeys.RIGHT);

         expect(comp.focused).toBe(comp.FocusRegions.INPUT);
         expect((comp as any).input.nativeElement.focus).toHaveBeenCalled();
      });

      it("should focus CALENDAR from NONE when calendar is set (no focusOnRegion call)", () => {
         const { comp } = createComboBoxComponent({ model: { calendar: true, editable: false } });
         comp.calendarButton = { nativeElement: { focus: vi.fn() } } as any;

         (comp as any).navigate(NavigationKeys.RIGHT);

         expect(comp.focused).toBe(comp.FocusRegions.CALENDAR);
         expect(comp.calendarButton.nativeElement.focus).not.toHaveBeenCalled();
      });

      it("should move from INPUT to SELECTION on RIGHT when editable and not a calendar", () => {
         const { comp } = createComboBoxComponent({ model: { editable: true, calendar: false } });
         comp.focused = comp.FocusRegions.INPUT;
         comp.selection = { nativeElement: { focus: vi.fn() } } as any;

         (comp as any).navigate(NavigationKeys.RIGHT);

         expect(comp.focused).toBe(comp.FocusRegions.SELECTION);
         expect(comp.selection.nativeElement.focus).toHaveBeenCalled();
      });

      it("should move from SELECTION back to INPUT on LEFT when editable", () => {
         const { comp } = createComboBoxComponent({ model: { editable: true } });
         comp.focused = comp.FocusRegions.SELECTION;
         (comp as any).input = { nativeElement: { focus: vi.fn() } };

         (comp as any).navigate(NavigationKeys.LEFT);

         expect(comp.focused).toBe(comp.FocusRegions.INPUT);
         expect((comp as any).input.nativeElement.focus).toHaveBeenCalled();
      });

      it("should click the calendar button on SPACE when focused on CALENDAR", () => {
         const { comp } = createComboBoxComponent({ model: { calendar: true } });
         comp.focused = comp.FocusRegions.CALENDAR;
         const click = vi.fn();
         comp.calendarButton = { nativeElement: { click } } as any;

         (comp as any).navigate(NavigationKeys.SPACE);

         expect(click).toHaveBeenCalled();
      });
   });

   // ── Group 3 — navigate: index stepping bounds ─────────────────────────────
   describe("Group 3 — navigate index stepping", () => {
      it("should increment focused on RIGHT while below the CALENDAR bound", () => {
         const { comp } = createComboBoxComponent({ model: { calendar: true, dataType: XSchema.DATE } });
         comp.focused = comp.FocusRegions.SELECTION;

         (comp as any).navigate(NavigationKeys.RIGHT);

         expect(comp.focused).toBe(comp.FocusRegions.SELECTION + 1);
      });

      it("should increment focused on RIGHT up to the MERIDIAN bound for a time column", () => {
         const { comp } = createComboBoxComponent({ model: { calendar: true, dataType: XSchema.TIME } });
         comp.focused = comp.FocusRegions.SECOND;
         comp.meridianRef = { nativeElement: { focus: vi.fn() } } as any;

         (comp as any).navigate(NavigationKeys.RIGHT);

         expect(comp.focused).toBe(comp.FocusRegions.MERIDIAN);
      });

      it("should decrement focused on LEFT while above the lower bound", () => {
         const { comp } = createComboBoxComponent({ model: { calendar: true, dataType: XSchema.TIME } });
         comp.focused = comp.FocusRegions.MINUTE;
         comp.hourRef = { nativeElement: { focus: vi.fn() } } as any;

         (comp as any).navigate(NavigationKeys.LEFT);

         expect(comp.focused).toBe(comp.FocusRegions.HOUR);
      });

      it("should not decrement below the calendar's lower bound", () => {
         const { comp } = createComboBoxComponent({ model: { calendar: true, dataType: XSchema.DATE, editable: false } });
         comp.focused = comp.FocusRegions.CALENDAR;

         (comp as any).navigate(NavigationKeys.LEFT);

         expect(comp.focused).toBe(comp.FocusRegions.CALENDAR);
      });
   });

   // ── Group 4 — focusOnRegion ────────────────────────────────────────────────
   describe("Group 4 — focusOnRegion", () => {
      it("should focus the selection element for FocusRegions.SELECTION", () => {
         const { comp } = createComboBoxComponent();
         comp.focused = comp.FocusRegions.SELECTION;
         comp.selection = { nativeElement: { focus: vi.fn() } } as any;

         (comp as any).focusOnRegion();

         expect(comp.selection.nativeElement.focus).toHaveBeenCalled();
      });

      it("should focus the input element for FocusRegions.INPUT", () => {
         const { comp } = createComboBoxComponent();
         comp.focused = comp.FocusRegions.INPUT;
         (comp as any).input = { nativeElement: { focus: vi.fn() } };

         (comp as any).focusOnRegion();

         expect((comp as any).input.nativeElement.focus).toHaveBeenCalled();
      });

      it("should focus the calendar button for FocusRegions.CALENDAR", () => {
         const { comp } = createComboBoxComponent();
         comp.focused = comp.FocusRegions.CALENDAR;
         comp.calendarButton = { nativeElement: { focus: vi.fn() } } as any;

         (comp as any).focusOnRegion();

         expect(comp.calendarButton.nativeElement.focus).toHaveBeenCalled();
      });

      it("should not throw when the target ViewChild ref is absent", () => {
         const { comp } = createComboBoxComponent();
         comp.focused = comp.FocusRegions.HOUR;
         comp.hourRef = undefined;

         expect(() => (comp as any).focusOnRegion()).not.toThrow();
      });
   });

   // ── Group 5 — clearNavSelection ────────────────────────────────────────────
   describe("Group 5 — clearNavSelection", () => {
      it("should reset focus to NONE and blur the input/selection elements", () => {
         const { comp } = createComboBoxComponent();
         comp.focused = comp.FocusRegions.INPUT;
         const inputBlur = vi.fn();
         const selectionBlur = vi.fn();
         (comp as any).input = { nativeElement: { blur: inputBlur } };
         comp.selection = { nativeElement: { blur: selectionBlur } } as any;

         (comp as any).clearNavSelection();

         expect(comp.focused).toBe(comp.FocusRegions.NONE);
         expect(inputBlur).toHaveBeenCalled();
         expect(selectionBlur).toHaveBeenCalled();
      });
   });

   // ── Group 6 — showCalendar / showTime ──────────────────────────────────────
   describe("Group 6 — showCalendar / showTime", () => {
      it("should show the calendar for a DATE column", () => {
         const { comp } = createComboBoxComponent({ model: { calendar: true, dataType: XSchema.DATE } });
         expect(comp.showCalendar()).toBe(true);
      });

      it("should not show the calendar for a TIME column", () => {
         const { comp } = createComboBoxComponent({ model: { calendar: true, dataType: XSchema.TIME } });
         expect(comp.showCalendar()).toBe(false);
      });

      it("should show the time controls for a TIME column", () => {
         const { comp } = createComboBoxComponent({ model: { calendar: true, dataType: XSchema.TIME } });
         expect(comp.showTime()).toBe(true);
      });

      it("should not show the time controls for a DATE column", () => {
         const { comp } = createComboBoxComponent({ model: { calendar: true, dataType: XSchema.DATE } });
         expect(comp.showTime()).toBe(false);
      });
   });

   // ── Group 7 — getDateString ────────────────────────────────────────────────
   describe("Group 7 — getDateString", () => {
      it("should return an empty string when no date is selected", () => {
         const { comp } = createComboBoxComponent();
         comp.selectedDate = null;

         expect(comp.getDateString()).toBe("");
      });

      it("should return the raw y-m-d string when no dateFormat is set", () => {
         const { comp } = createComboBoxComponent({ model: { dateFormat: null } });
         comp.selectedDate = { year: 2024, month: 5, day: 6 };

         expect(comp.getDateString()).toBe("2024-5-6");
      });

      it("should format the date using the model's dateFormat when set", () => {
         const { comp } = createComboBoxComponent({ model: { dateFormat: "yyyy-MM-dd" } });
         comp.selectedDate = { year: 2024, month: 5, day: 6 };

         expect(comp.getDateString()).toBe("2024-05-06");
      });
   });

   // ── Group 8 — getDate / getTimeInstant ─────────────────────────────────────
   describe("Group 8 — getDate / getTimeInstant", () => {
      it("should parse a datetime string into an NgbDateStruct via getDate", () => {
         const { comp } = createComboBoxComponent();

         expect(comp.getDate("2024-05-06 00:00:00")).toEqual({ year: 2024, month: 5, day: 6 });
      });

      it("should parse a datetime string into a local Date via getTimeInstant", () => {
         const { comp } = createComboBoxComponent();

         const date = comp.getTimeInstant("2024-05-06 10:20:30");

         expect(date.getFullYear()).toBe(2024);
         expect(date.getMonth()).toBe(4);
         expect(date.getDate()).toBe(6);
         expect(date.getHours()).toBe(10);
      });

      it("should not throw and should return a valid Date when serverTZ is set", () => {
         const { comp } = createComboBoxComponent({
            model: { serverTZ: true, serverTZID: "America/New_York" },
         });

         const date = comp.getTimeInstant("2024-05-06 10:20:30");

         expect(date instanceof Date).toBe(true);
         expect(isNaN(date.getTime())).toBe(false);
      });
   });

   // ── Group 9 — getDateTime ──────────────────────────────────────────────────
   describe("Group 9 — getDateTime", () => {
      it("should convert 12 AM to hour 0", () => {
         const { comp } = createComboBoxComponent();
         comp.hours = 12; comp.minutes = 0; comp.seconds = 0; comp.meridian = "AM";

         const date = (comp as any).getDateTime({ year: 2024, month: 1, day: 1 });

         expect(date.getHours()).toBe(0);
      });

      it("should convert a PM hour below 12 to its 24-hour value", () => {
         const { comp } = createComboBoxComponent();
         comp.hours = 5; comp.minutes = 0; comp.seconds = 0; comp.meridian = "PM";

         const date = (comp as any).getDateTime({ year: 2024, month: 1, day: 1 });

         expect(date.getHours()).toBe(17);
      });

      it("should leave an AM hour below 12 unchanged", () => {
         const { comp } = createComboBoxComponent();
         comp.hours = 5; comp.minutes = 0; comp.seconds = 0; comp.meridian = "AM";

         const date = (comp as any).getDateTime({ year: 2024, month: 1, day: 1 });

         expect(date.getHours()).toBe(5);
      });

      it("should not throw and should return a valid Date when serverTZ is set", () => {
         const { comp } = createComboBoxComponent({
            model: { serverTZ: true, serverTZID: "America/New_York" },
         });
         comp.hours = 10; comp.minutes = 0; comp.seconds = 0; comp.meridian = "AM";

         const date = (comp as any).getDateTime({ year: 2024, month: 1, day: 1 });

         expect(date instanceof Date).toBe(true);
         expect(isNaN(date.getTime())).toBe(false);
      });
   });

   // ── Group 10 — isValidDate ─────────────────────────────────────────────────
   describe("Group 10 — isValidDate", () => {
      it("should return true for a date clearly within the default range", () => {
         const { comp } = createComboBoxComponent();
         comp.hours = 10; comp.minutes = 0; comp.seconds = 0;

         expect((comp as any).isValidDate({ year: 2024, month: 5, day: 6 })).toBe(true);
      });

      it("should return false for a date clearly outside the default range", () => {
         const { comp } = createComboBoxComponent();
         comp.hours = 10; comp.minutes = 0; comp.seconds = 0;

         expect((comp as any).isValidDate({ year: 1500, month: 1, day: 1 })).toBe(false);
      });

      // Bug: defaultMinDate is documented as Jan 1, 1900 (NgbDateStruct month=1 is January),
      // but isValidDate() constructs `new Date(1900, 1, 1)` — JS Date's month is 0-based, so
      // this actually evaluates to Feb 1, 1900. A date the UI treats as in-range is rejected.
      it.fails("should treat the documented default min date (Jan 1900) as valid", () => {
         const { comp } = createComboBoxComponent({ model: { minDate: null, maxDate: null } });
         comp.hours = 10; comp.minutes = 0; comp.seconds = 0;

         expect((comp as any).isValidDate({ year: 1900, month: 1, day: 15 })).toBe(true);
      });
   });

   // ── Group 11 — isSelected ──────────────────────────────────────────────────
   describe("Group 11 — isSelected", () => {
      it("should return true when the assembly is focused", () => {
         const { comp } = createComboBoxComponent();
         (comp.vsInfo as any).isAssemblyFocused = () => true;

         expect(comp.isSelected()).toBe(true);
      });

      it("should return false when the assembly is not focused", () => {
         const { comp } = createComboBoxComponent();
         (comp.vsInfo as any).isAssemblyFocused = () => false;

         expect(comp.isSelected()).toBe(false);
      });
   });

   // ── Group 12 — getLabelIndex / getValueIndex ──────────────────────────────
   describe("Group 12 — getLabelIndex / getValueIndex", () => {
      it("should return the matching index or -1 for getLabelIndex", () => {
         const { comp } = createComboBoxComponent({
            model: { labels: ["Label A", "Label B"], values: ["A", "B"] },
         });

         expect(comp.getLabelIndex("Label B")).toBe(1);
         expect(comp.getLabelIndex("Unknown")).toBe(-1);
      });

      it("should return the matching index or -1 for getValueIndex", () => {
         const { comp } = createComboBoxComponent({
            model: { labels: ["Label A", "Label B"], values: ["A", "B"] },
         });

         expect(comp.getValueIndex("B")).toBe(1);
         expect(comp.getValueIndex("Z")).toBe(-1);
      });
   });

   // ── Group 13 — updateHours / updateMinutes / updateSeconds / updateMeridian ─
   describe("Group 13 — time field updaters", () => {
      it("updateHours should set hours and trigger applyDateSelection", () => {
         const { comp } = createComboBoxComponent();
         const spy = vi.spyOn(comp as any, "applyDateSelection").mockImplementation(() => {});

         comp.updateHours(5);

         expect(comp.hours).toBe(5);
         expect(spy).toHaveBeenCalled();
      });

      it("updateMinutes should set minutes and trigger applyDateSelection", () => {
         const { comp } = createComboBoxComponent();
         const spy = vi.spyOn(comp as any, "applyDateSelection").mockImplementation(() => {});

         comp.updateMinutes(30);

         expect(comp.minutes).toBe(30);
         expect(spy).toHaveBeenCalled();
      });

      it("updateSeconds should set seconds and trigger applyDateSelection", () => {
         const { comp } = createComboBoxComponent();
         const spy = vi.spyOn(comp as any, "applyDateSelection").mockImplementation(() => {});

         comp.updateSeconds(45);

         expect(comp.seconds).toBe(45);
         expect(spy).toHaveBeenCalled();
      });

      it("updateMeridian should set meridian and trigger applyDateSelection", () => {
         const { comp } = createComboBoxComponent();
         const spy = vi.spyOn(comp as any, "applyDateSelection").mockImplementation(() => {});

         comp.updateMeridian("PM");

         expect(comp.meridian).toBe("PM");
         expect(spy).toHaveBeenCalled();
      });
   });

   // ── Group 14 — applyDateSelection ──────────────────────────────────────────
   describe("Group 14 — applyDateSelection", () => {
      it("should defer via pendingChange when ctrlDown is true, without side effects", () => {
         const { comp, socket, formInputService, ngbModal } = createComboBoxComponent({
            model: { refresh: true },
         });
         (comp as any).ctrlDown = true;

         (comp as any).applyDateSelection();

         expect((comp as any).pendingChange).toBe(true);
         expect(socket.sendEvent).not.toHaveBeenCalled();
         expect(formInputService.addPendingValue).not.toHaveBeenCalled();
         expect(ngbModal.open).not.toHaveBeenCalled();
      });

      it("should show an error dialog and send no event for an invalid selectedDate", () => {
         const { comp, socket, ngbModal } = createComboBoxComponent({ model: { refresh: true } });
         comp.hours = 10; comp.minutes = 0; comp.seconds = 0;
         comp.selectedDate = { year: 1500, month: 1, day: 1 };

         (comp as any).applyDateSelection();

         expect(ngbModal.open).toHaveBeenCalled();
         expect(socket.sendEvent).not.toHaveBeenCalled();
      });

      it("should debounce an applySelection event using today's date when refresh is true and no date is selected", () => {
         const { comp, debounceService, socket } = createComboBoxComponent({
            model: { refresh: true },
         });
         comp.hours = 10; comp.minutes = 0; comp.seconds = 0;
         comp.selectedDate = null;

         (comp as any).applyDateSelection();

         expect(debounceService.debounce).toHaveBeenCalledWith(
            "InputSelectionEvent.Combo1", expect.any(Function), 500,
            [expect.objectContaining({ assemblyName: "Combo1" }), socket]);
      });

      it("should queue a pending value when refresh is false", () => {
         const { comp, formInputService } = createComboBoxComponent({ model: { refresh: false } });
         comp.hours = 10; comp.minutes = 0; comp.seconds = 0;
         comp.selectedDate = { year: 2024, month: 5, day: 6 };

         (comp as any).applyDateSelection();

         expect(formInputService.addPendingValue).toHaveBeenCalled();
      });
   });

   // ── Group 15 — Legacy DOM regressions ported from vs-combo-box.spec.ts ────
   describe("Group 15 — legacy DOM regressions", () => {
      let fixture: ComponentFixture<VSComboBox>;
      let comboBox: VSComboBox;

      beforeEach(waitForAsync(() => {
         const formDataService: any = { checkFormData: vi.fn() };
         const debounceService: any = { debounce: vi.fn((key, fn, delay, args) => fn(...args)) };
         const dataTipService: any = { isDataTip: vi.fn() };
         const firstDayOfWeekService: any = { getFirstDay: vi.fn(() => of({})) };

         TestBed.configureTestingModule({
            imports: [FormsModule, VSComboBox, VSPopComponentDirective],
            providers: [
               PopComponentService,
               FormInputService,
               { provide: ContextProvider, useValue: {} },
               { provide: ViewsheetClientService, useValue: { sendEvent: vi.fn(), commands: of([]) } },
               { provide: CheckFormDataService, useValue: formDataService },
               { provide: DebounceService, useValue: debounceService },
               { provide: DataTipService, useValue: dataTipService },
               { provide: FirstDayOfWeekService, useValue: firstDayOfWeekService },
            ],
            schemas: [NO_ERRORS_SCHEMA],
         });
         TestBed.compileComponents();

         fixture = TestBed.createComponent(VSComboBox);
         comboBox = fixture.componentInstance;
         comboBox.model = TestUtils.createMockVSComboBoxModel("Combo1");
      }));

      // Bug #17282
      it("should show the date format placeholder for a date combobox", () => {
         comboBox.model.dataType = XSchema.DATE;
         comboBox.model.editable = true;
         fixture.detectChanges();
         let inputField: HTMLElement = fixture.nativeElement.querySelector("input.standard-input");
         expect(inputField.getAttribute("placeholder")).toBe("yyyy-MM-dd");

         comboBox.model.dataType = XSchema.TIME;
         fixture.detectChanges();
         expect(inputField.getAttribute("placeholder")).toBe("HH:mm:ss AM[PM]");

         comboBox.model.dataType = XSchema.TIME_INSTANT;
         fixture.detectChanges();
         expect(inputField.getAttribute("placeholder")).toBe("yyyy-MM-dd");
      });

      // Bug #18439
      it("should apply the model's H-alignment to the collapsed select element", () => {
         let vsformat = TestUtils.createMockVSFormatModel();
         vsformat.hAlign = "right";
         comboBox.model.objectFormat = vsformat;

         fixture.detectChanges();
         let select = fixture.nativeElement.querySelector("select");
         expect(select.style["text-align-last"]).toEqual("right");
      });
   });
});
