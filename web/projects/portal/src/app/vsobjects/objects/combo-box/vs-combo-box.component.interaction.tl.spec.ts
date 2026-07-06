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
 * VSComboBox — Pass 1: Interaction
 *
 * Direct instantiation (no ATL render) — all 9 constructor deps mocked; the only HTTP call
 * (FirstDayOfWeekService.getFirstDay()) is a swappable service mocked as an Observable.
 *
 *   Group 1  [Risk 1] — ngOnInit: firstDayOfWeek populated from the service
 *   Group 2  [Risk 3] — ngOnChanges: submittedForm subscription wiring + applySelection trigger
 *   Group 3  [Risk 3] — model setter: minDate/maxDate defaults/parsing, selectedObject ->
 *                        selectedDate/hours/minutes/seconds/meridian backfill, reset, no-resync
 *                        guard
 *   Group 4  [Risk 1] — model setter: serverTZ dayjs.tz parsing smoke test
 *   Group 5  [Risk 2] — model setter: selectedLabel backfill for editable combo boxes
 *   Group 6  [Risk 2] — onChange / onBlur
 *   Group 7  [Risk 2] — onInputDate / onEnter
 *   Group 8  [Risk 1] — selectItem / toggleDropdown
 *   Group 9  [Risk 3] — applySelection (via onChange refresh path): checkFormData confirm/cancel
 *   Group 10 [Risk 2] — clearCalendar
 *   Group 11 [Risk 3] — updateDate: happy path, unchanged guard (out-of-range boundary case is
 *                        covered by isValidDate's own it.fails in the display pass)
 *   Group 12 [Risk 1] — selectLabel / clearLabelSelection
 *   Group 13 [Risk 1] — onKeyUp / onKeyDown: ctrlDown tracking + pending change flush
 *   Group 14 [Risk 1] — labelSearch: debounced typeahead filter
 *   Group 15 [Risk 1] — ngOnDestroy
 *
 * Out of scope this pass: inputPlaceholder, navigate, focusOnRegion, clearNavSelection,
 * showCalendar, showTime, getDateString, getDate, getTimeInstant, getDateTime, isValidDate,
 * isSelected, getLabelIndex, getValueIndex, updateHours, updateMinutes, updateSeconds,
 * updateMeridian, applyDateSelection's own conditional branches — covered in
 * vs-combo-box.component.display.tl.spec.ts.
 */

import { SimpleChange } from "@angular/core";
import { Subject } from "rxjs";
import { MessageDialog } from "../../../widget/dialog/message-dialog/message-dialog.component";
import {
   createComboBoxComponent,
   makeMockComboBoxModel,
} from "./vs-combo-box.component.test-helpers";

afterEach(() => vi.restoreAllMocks());

// updateDate() routes through ComponentTool.showMessageDialog, which has a 500ms dedup guard
// keyed on the message string — reset it so Group 11's dialog test is never silently skipped.
beforeEach(() => {
   MessageDialog.lastMessage = null;
   (MessageDialog as any).lastMessageTS = 0;
});

describe("VSComboBox — Pass 1: Interaction", () => {

   // ── Group 1 — ngOnInit ────────────────────────────────────────────────────
   describe("Group 1 — ngOnInit", () => {
      it("should populate firstDayOfWeek from the service", () => {
         const { comp } = createComboBoxComponent();

         comp.ngOnInit();

         expect(comp.firstDayOfWeek).toBe(1);
      });
   });

   // ── Group 2 — ngOnChanges ─────────────────────────────────────────────────
   describe("Group 2 — ngOnChanges", () => {
      it("should call applySelection when submitted fires true with an unapplied selection", () => {
         const { comp } = createComboBoxComponent();
         const subject = new Subject<boolean>();
         comp.submitted = subject.asObservable();
         (comp as any).unappliedSelection = true;
         const spy = vi.spyOn(comp as any, "applySelection").mockImplementation(() => {});

         comp.ngOnChanges({ submitted: new SimpleChange(null, comp.submitted, true) });
         subject.next(true);

         expect(spy).toHaveBeenCalled();
      });

      it("should not call applySelection when there is no unapplied selection", () => {
         const { comp } = createComboBoxComponent();
         const subject = new Subject<boolean>();
         comp.submitted = subject.asObservable();
         (comp as any).unappliedSelection = false;
         const spy = vi.spyOn(comp as any, "applySelection").mockImplementation(() => {});

         comp.ngOnChanges({ submitted: new SimpleChange(null, comp.submitted, true) });
         subject.next(true);

         expect(spy).not.toHaveBeenCalled();
      });

      it("should unsubscribe the previous submittedForm subscription before resubscribing", () => {
         const { comp } = createComboBoxComponent();
         const firstSubject = new Subject<boolean>();
         comp.submitted = firstSubject.asObservable();
         comp.ngOnChanges({ submitted: new SimpleChange(null, comp.submitted, true) });
         const unsubSpy = vi.spyOn(comp.submittedForm, "unsubscribe");

         const secondSubject = new Subject<boolean>();
         comp.submitted = secondSubject.asObservable();
         comp.ngOnChanges({ submitted: new SimpleChange(null, comp.submitted, false) });

         expect(unsubSpy).toHaveBeenCalled();
      });

      it("should not subscribe to submitted when not in viewer", () => {
         const { comp } = createComboBoxComponent({
            contextProvider: { viewer: false, preview: false, composer: false, binding: false },
         });
         const subject = new Subject<boolean>();
         comp.submitted = subject.asObservable();

         comp.ngOnChanges({ submitted: new SimpleChange(null, comp.submitted, true) });

         expect(comp.submittedForm).toBeFalsy();
      });

      // Isolates the `changes.submitted` operand of the
      // `this.viewer && changes.submitted && this.submitted` guard.
      it("should not subscribe when ngOnChanges fires without a submitted change", () => {
         const { comp } = createComboBoxComponent();
         const subject = new Subject<boolean>();
         comp.submitted = subject.asObservable();

         comp.ngOnChanges({});

         expect(comp.submittedForm).toBeFalsy();
      });

      // Isolates the `this.submitted` operand of the same guard.
      it("should not subscribe when the submitted input is not set", () => {
         const { comp } = createComboBoxComponent();

         comp.ngOnChanges({ submitted: new SimpleChange(null, undefined, true) });

         expect(comp.submittedForm).toBeFalsy();
      });
   });

   // ── Group 3 — model setter: date/time backfill ───────────────────────────
   describe("Group 3 — model setter date/time backfill", () => {
      it("should default minDate/maxDate when calendar is set and no explicit bounds are given", () => {
         const { comp } = createComboBoxComponent();

         comp.model = makeMockComboBoxModel({ calendar: true, minDate: null, maxDate: null });

         expect(comp.minDate).toEqual({ year: 1900, month: 1, day: 1 });
         expect(comp.maxDate).toEqual({ year: 2050, month: 12, day: 31 });
      });

      it("should parse explicit minDate/maxDate strings into NgbDateStruct", () => {
         const { comp } = createComboBoxComponent();

         comp.model = makeMockComboBoxModel({
            calendar: true, minDate: "2020-05-10 00:00:00", maxDate: "2030-01-02 00:00:00",
         });

         expect(comp.minDate).toEqual({ year: 2020, month: 5, day: 10 });
         expect(comp.maxDate).toEqual({ year: 2030, month: 1, day: 2 });
      });

      it("should populate selectedDate/hours/minutes/seconds/meridian from a selectedObject", () => {
         const { comp } = createComboBoxComponent();
         const selected = new Date(2024, 0, 15, 14, 30, 45).getTime();

         comp.model = makeMockComboBoxModel({ calendar: true, selectedObject: selected });

         expect(comp.selectedDate).toEqual({ year: 2024, month: 1, day: 15 });
         expect(comp.hours).toBe(2);
         expect(comp.minutes).toBe(30);
         expect(comp.seconds).toBe(45);
         expect(comp.meridian).toBe("PM");
      });

      it("should convert a midnight hour (0) to a 12-hour AM value", () => {
         const { comp } = createComboBoxComponent();
         const selected = new Date(2024, 0, 15, 0, 5, 0).getTime();

         comp.model = makeMockComboBoxModel({ calendar: true, selectedObject: selected });

         expect(comp.hours).toBe(12);
         expect(comp.meridian).toBe("AM");
      });

      it("should reset date/time fields to defaults when selectedObject is cleared", () => {
         const { comp } = createComboBoxComponent();
         comp.model = makeMockComboBoxModel({
            calendar: true, selectedObject: new Date(2024, 0, 15, 14, 30, 45).getTime(),
         });

         comp.model = makeMockComboBoxModel({ calendar: true, selectedObject: null });

         expect(comp.selectedDate).toBeNull();
         expect(comp.hours).toBe(0);
         expect(comp.minutes).toBe(0);
         expect(comp.seconds).toBe(0);
         expect(comp.meridian).toBe("AM");
      });

      it("should not resync date/time fields when the value is unchanged and still editable", () => {
         const { comp } = createComboBoxComponent();
         const selected = new Date(2024, 0, 15, 14, 30, 45).getTime();
         comp.model = makeMockComboBoxModel({
            calendar: true, editable: true, selectedObject: selected,
         });
         comp.hours = 99; // sentinel to prove the resync block is skipped

         comp.model = makeMockComboBoxModel({
            calendar: true, editable: true, selectedObject: selected,
         });

         expect(comp.hours).toBe(99);
      });
   });

   // ── Group 4 — model setter: serverTZ smoke test ──────────────────────────
   describe("Group 4 — model setter serverTZ parsing", () => {
      it("should not throw and should populate selectedDate when serverTZ is set", () => {
         const { comp } = createComboBoxComponent();
         const selected = new Date(2024, 0, 15, 14, 30, 45).getTime();

         expect(() => {
            comp.model = makeMockComboBoxModel({
               calendar: true, serverTZ: true, serverTZID: "America/New_York",
               selectedObject: selected,
            });
         }).not.toThrow();

         expect(comp.selectedDate).not.toBeNull();
      });
   });

   // ── Group 5 — model setter: selectedLabel backfill ───────────────────────
   describe("Group 5 — model setter selectedLabel backfill", () => {
      it("should backfill selectedLabel from labels[] when selectedObject matches a known value", () => {
         const { comp } = createComboBoxComponent();

         comp.model = makeMockComboBoxModel({
            editable: true, selectedLabel: "", selectedObject: "B",
            labels: ["Label A", "Label B", "Label C"], values: ["A", "B", "C"],
         });

         expect(comp.model.selectedLabel).toBe("Label B");
      });

      it("should fall back to the raw selectedObject as selectedLabel when no matching value is found", () => {
         const { comp } = createComboBoxComponent();

         comp.model = makeMockComboBoxModel({
            editable: true, selectedLabel: "", selectedObject: "Z",
            labels: ["Label A", "Label B", "Label C"], values: ["A", "B", "C"],
         });

         expect(comp.model.selectedLabel).toBe("Z");
      });
   });

   // ── Group 6 — onChange / onBlur ───────────────────────────────────────────
   describe("Group 6 — onChange / onBlur", () => {
      it("should update selectedLabel/selectedObject and apply immediately when refresh is true", () => {
         const { comp, formInputService } = createComboBoxComponent({
            model: { refresh: true },
         });
         const spy = vi.spyOn(comp as any, "applySelection").mockImplementation(() => {});

         comp.onChange("Label B");

         expect(comp.model.selectedLabel).toBe("Label B");
         expect(comp.model.selectedObject).toBe("B");
         expect(spy).toHaveBeenCalled();
         expect(formInputService.addPendingValue).not.toHaveBeenCalled();
      });

      it("should queue a pending value via formInputService when refresh and writeBackDirectly are false", () => {
         const { comp, formInputService } = createComboBoxComponent({
            model: { refresh: false, writeBackDirectly: false, editable: true },
         });

         comp.onChange("Label C");

         expect(formInputService.addPendingValue).toHaveBeenCalledWith("Combo1", "C");
      });

      it("should ignore input when not editable and the option isn't a known label", () => {
         const { comp, formInputService } = createComboBoxComponent({
            model: { editable: false, selectedLabel: "Label A" },
         });

         comp.onChange("Unknown Option");

         expect(comp.model.selectedLabel).toBe("Label A");
         expect(formInputService.addPendingValue).not.toHaveBeenCalled();
      });

      // Isolates the `editable` side of the `this.model.editable || index != -1` guard: an
      // unknown option is still accepted (unlike the test above) because editable is true here.
      it("should accept an unknown option when editable is true", () => {
         const { comp, formInputService } = createComboBoxComponent({
            model: { editable: true, selectedLabel: "Label A" },
         });

         comp.onChange("Freeform Text");

         expect(comp.model.selectedLabel).toBe("Freeform Text");
         expect(comp.model.selectedObject).toBe("Freeform Text");
         expect(formInputService.addPendingValue).toHaveBeenCalledWith("Combo1", "Freeform Text");
      });

      it("should delegate to onChange only when the blurred value actually changed", () => {
         const { comp } = createComboBoxComponent({ model: { selectedLabel: "Label A" } });
         const spy = vi.spyOn(comp, "onChange").mockImplementation(() => {});

         comp.onBlur("Label A");
         expect(spy).not.toHaveBeenCalled();

         comp.onBlur("Label B");
         expect(spy).toHaveBeenCalledWith("Label B");
      });
   });

   // ── Group 7 — onInputDate / onEnter ───────────────────────────────────────
   describe("Group 7 — onInputDate / onEnter", () => {
      it("should parse a valid yyyy-MM-dd string and call updateDate", () => {
         const { comp } = createComboBoxComponent();
         const spy = vi.spyOn(comp, "updateDate").mockImplementation(() => {});

         comp.onInputDate("2024-05-06");

         expect(spy).toHaveBeenCalledWith({ year: 2024, month: 5, day: 6 });
      });

      it("should fall back to onChange for a non-date string", () => {
         const { comp } = createComboBoxComponent();
         const spy = vi.spyOn(comp, "onChange").mockImplementation(() => {});

         comp.onInputDate("not-a-date");

         expect(spy).toHaveBeenCalledWith("not-a-date");
      });

      it("should blur the input element before delegating in onEnter", () => {
         const { comp } = createComboBoxComponent();
         const blur = vi.fn();
         (comp as any).input = { nativeElement: { blur } };
         const spy = vi.spyOn(comp, "onInputDate").mockImplementation(() => {});

         comp.onEnter("2024-05-06");

         expect(blur).toHaveBeenCalled();
         expect(spy).toHaveBeenCalledWith("2024-05-06");
      });

      it("should not throw when onEnter is called without an input ViewChild", () => {
         const { comp } = createComboBoxComponent();
         const spy = vi.spyOn(comp, "onInputDate").mockImplementation(() => {});

         expect(() => comp.onEnter("2024-05-06")).not.toThrow();
         expect(spy).toHaveBeenCalledWith("2024-05-06");
      });
   });

   // ── Group 8 — selectItem / toggleDropdown ─────────────────────────────────
   describe("Group 8 — selectItem / toggleDropdown", () => {
      it("should close the dropdown and apply the change on selectItem", () => {
         const { comp } = createComboBoxComponent();
         comp.isDropdownOpen = true;
         const spy = vi.spyOn(comp, "onChange").mockImplementation(() => {});

         comp.selectItem("Label B");

         expect(comp.isDropdownOpen).toBe(false);
         expect(spy).toHaveBeenCalledWith("Label B");
      });

      it("should flip isDropdownOpen in both directions", () => {
         const { comp } = createComboBoxComponent();
         comp.isDropdownOpen = false;

         comp.toggleDropdown();
         expect(comp.isDropdownOpen).toBe(true);

         comp.toggleDropdown();
         expect(comp.isDropdownOpen).toBe(false);
      });
   });

   // ── Group 9 — applySelection (via onChange refresh path) ──────────────────
   describe("Group 9 — applySelection", () => {
      it("should emit comboBoxChanged and send the applySelection event when checkFormData confirms", () => {
         const { comp, socket } = createComboBoxComponent({ model: { refresh: true } });
         const emitted: string[] = [];
         comp.comboBoxChanged.subscribe((name: string) => emitted.push(name));

         comp.onChange("Label B");

         expect(emitted).toEqual(["Combo1"]);
         expect(socket.sendEvent).toHaveBeenCalledWith(
            "/events/comboBox/applySelection",
            expect.objectContaining({ assemblyName: "Combo1", value: "B" }));
      });

      it("should send a GetVSObjectModelEvent when checkFormData cancels", () => {
         const formDataService = {
            checkFormData: vi.fn((rt: string, name: string, sel: string,
                                   confirmed: Function, canceled: Function) => canceled()),
         };
         const { comp, socket } = createComboBoxComponent({
            formDataService, model: { refresh: true },
         });

         comp.onChange("Label B");

         expect(socket.sendEvent).toHaveBeenCalledWith(
            "/events/vsview/object/model", expect.objectContaining({ name: "Combo1" }));
      });
   });

   // ── Group 10 — clearCalendar ───────────────────────────────────────────────
   describe("Group 10 — clearCalendar", () => {
      it("should reset calendar fields and debounce an InputSelectionEvent when refresh is true", () => {
         const { comp, debounceService, socket } = createComboBoxComponent({
            model: { refresh: true, selectedLabel: "Label A", selectedObject: "A" },
         });
         comp.hours = 5; comp.minutes = 6; comp.seconds = 7;
         comp.selectedDate = { year: 2024, month: 1, day: 1 };

         comp.clearCalendar();

         expect(comp.model.selectedLabel).toBeNull();
         expect(comp.model.selectedObject).toBeNull();
         expect(comp.model.values).toEqual([null]);
         expect(comp.hours).toBe(0);
         expect(comp.minutes).toBe(0);
         expect(comp.seconds).toBe(0);
         expect(comp.selectedDate).toBeNull();
         expect(debounceService.debounce).toHaveBeenCalledWith(
            "InputSelectionEvent.Combo1", expect.any(Function), 500,
            [expect.objectContaining({ assemblyName: "Combo1", value: null }), socket]);
      });

      it("should queue a null pending value via formInputService when refresh is false", () => {
         const { comp, formInputService, debounceService } = createComboBoxComponent({
            model: { refresh: false },
         });

         comp.clearCalendar();

         expect(formInputService.addPendingValue).toHaveBeenCalledWith("Combo1", null);
         expect(debounceService.debounce).not.toHaveBeenCalled();
      });
   });

   // ── Group 11 — updateDate ──────────────────────────────────────────────────
   describe("Group 11 — updateDate", () => {
      // getDateTime() builds `new Date(y, m, d, this.hours, this.minutes, this.seconds)` —
      // hours/minutes/seconds must be numbers (normally backfilled by the model setter's
      // calendar branch) or the constructed Date is Invalid and every date looks out of range.
      function withValidTimeFields(comp: any) {
         comp.hours = 10;
         comp.minutes = 0;
         comp.seconds = 0;
         return comp;
      }

      it("should show an error dialog and not update selectedDate for a clearly out-of-range date", () => {
         const { comp, ngbModal } = createComboBoxComponent();
         withValidTimeFields(comp);
         comp.dropdown = { close: vi.fn() } as any;

         comp.updateDate({ year: 1500, month: 1, day: 1 });

         expect(ngbModal.open).toHaveBeenCalled();
         expect(comp.selectedDate).toBeFalsy();
         expect(comp.dropdown.close).not.toHaveBeenCalled();
      });

      it("should update selectedDate, apply the selection, and close the dropdown for a valid changed date", () => {
         const { comp, formInputService } = createComboBoxComponent();
         withValidTimeFields(comp);
         comp.dropdown = { close: vi.fn() } as any;

         comp.updateDate({ year: 2024, month: 5, day: 6 });

         expect(comp.selectedDate).toEqual({ year: 2024, month: 5, day: 6 });
         expect(comp.dropdown.close).toHaveBeenCalled();
         expect(formInputService.addPendingValue).toHaveBeenCalled();
      });

      it("should do nothing when the date is unchanged", () => {
         const { comp, formInputService, debounceService } = createComboBoxComponent();
         withValidTimeFields(comp);
         comp.selectedDate = { year: 2024, month: 5, day: 6 };
         comp.dropdown = { close: vi.fn() } as any;

         comp.updateDate({ year: 2024, month: 5, day: 6 });

         expect(comp.dropdown.close).not.toHaveBeenCalled();
         expect(formInputService.addPendingValue).not.toHaveBeenCalled();
         expect(debounceService.debounce).not.toHaveBeenCalled();
      });
   });

   // ── Group 12 — selectLabel / clearLabelSelection ──────────────────────────
   describe("Group 12 — selectLabel / clearLabelSelection", () => {
      it("should mark the label selected on selectLabel", () => {
         const { comp } = createComboBoxComponent({
            contextProvider: { viewer: true, preview: false, composer: false, binding: false },
         });

         comp.selectLabel({} as MouseEvent);

         expect(comp.model.labelSelected).toBe(true);
      });

      it("should ignore selectLabel while in preview context", () => {
         const { comp } = createComboBoxComponent({
            contextProvider: { viewer: false, preview: true, composer: false, binding: false },
         });

         comp.selectLabel({} as MouseEvent);

         expect(comp.model.labelSelected).toBeFalsy();
      });

      it("should clear labelSelected on clearLabelSelection", () => {
         const { comp } = createComboBoxComponent();
         comp.model.labelSelected = true;

         comp.clearLabelSelection();

         expect(comp.model.labelSelected).toBe(false);
      });
   });

   // ── Group 13 — onKeyUp / onKeyDown ─────────────────────────────────────────
   describe("Group 13 — onKeyUp / onKeyDown", () => {
      it("should set ctrlDown on keydown with keyCode 17", () => {
         const { comp } = createComboBoxComponent();

         comp.onKeyDown({ keyCode: 17 } as KeyboardEvent);

         expect((comp as any).ctrlDown).toBe(true);
      });

      it("should clear ctrlDown and flush a pending applyDateSelection on keyup", () => {
         const { comp } = createComboBoxComponent();
         (comp as any).ctrlDown = true;
         (comp as any).pendingChange = true;
         const spy = vi.spyOn(comp as any, "applyDateSelection").mockImplementation(() => {});

         comp.onKeyUp({ keyCode: 17 } as KeyboardEvent);

         expect((comp as any).ctrlDown).toBe(false);
         expect((comp as any).pendingChange).toBe(false);
         expect(spy).toHaveBeenCalled();
      });
   });

   // ── Group 14 — labelSearch ──────────────────────────────────────────────────
   describe("Group 14 — labelSearch", () => {
      beforeEach(() => vi.useFakeTimers());
      afterEach(() => vi.useRealTimers());

      it("should filter labels matching the search term (case-insensitive), limited to rowCount", () => {
         const { comp } = createComboBoxComponent({
            model: { labels: ["Label A", "Label B", "Label C"], rowCount: 2 },
         });
         const term$ = new Subject<string>();
         const results: string[][] = [];
         comp.labelSearch(term$.asObservable()).subscribe(r => results.push(r));

         term$.next("la");
         vi.advanceTimersByTime(200);

         expect(results[0]).toEqual(["Label A", "Label B"]);
      });

      it("should emit an empty array for search terms shorter than 2 characters", () => {
         const { comp } = createComboBoxComponent();
         const term$ = new Subject<string>();
         const results: string[][] = [];
         comp.labelSearch(term$.asObservable()).subscribe(r => results.push(r));

         term$.next("l");
         vi.advanceTimersByTime(200);

         expect(results[0]).toEqual([]);
      });
   });

   // ── Group 15 — ngOnDestroy ──────────────────────────────────────────────────
   describe("Group 15 — ngOnDestroy", () => {
      it("should unsubscribe the submittedForm subscription on destroy", () => {
         const { comp } = createComboBoxComponent();
         const subject = new Subject<boolean>();
         comp.submitted = subject.asObservable();
         comp.ngOnChanges({ submitted: new SimpleChange(null, comp.submitted, true) });
         const unsubSpy = vi.spyOn(comp.submittedForm, "unsubscribe");

         comp.ngOnDestroy();

         expect(unsubSpy).toHaveBeenCalled();
      });
   });
});
