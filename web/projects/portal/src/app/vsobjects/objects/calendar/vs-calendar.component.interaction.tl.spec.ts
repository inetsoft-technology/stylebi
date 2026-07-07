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
 * VSCalendar - Pass 1: Interaction
 *
 * Coverage:
 *   Group 1  - ngOnInit / ngOnDestroy lifecycle
 *   Group 2  - actions setter: all 10 event ids dispatch to the correct handler
 *   Group 3  - clearCalendar (submitOnChange branches)
 *   Group 4  - toggleDropdown → onShow / onHide
 *   Group 5  - onShow / onHide direct behavior
 *   Group 6  - toggleDoubleCalendar (checkFormData → sendToggleDoubleCalendar)
 *   Group 7  - toggleYear (checkFormData → sendToggleYear)
 *   Group 8  - applyCalendar (checkFormData → sendApplyCalendar)
 *   Group 9  - applyCalendar0 (ctrlDown branch vs direct apply)
 *   Group 10 - updateTitle (non-viewer sends event)
 *   Group 11 - headerClick (mobile+dropdown toggle)
 *   Group 12 - miniMenuClosed / selectedDatesChange
 *   Group 13 - syncDateChange / syncPeriods / selectRange
 */

import { Subject } from "rxjs";

import { CalendarActions } from "../../action/calendar-actions";
import {
   createCalendarComponent,
   makeCalendarRef,
} from "./vs-calendar.component.test-helpers";
import { VSCalendar } from "./vs-calendar.component";

afterEach(() => vi.restoreAllMocks());

// ─── Helpers ────────────────────────────────────────────────────────────────

function makeActions() {
   const onAssemblyActionEvent = new Subject<{ id: string; event?: MouseEvent }>();
   const actions = {
      onAssemblyActionEvent,
      toolbarActions: [],
      menuActions: [],
      showingActions: [],
      getMoreActions: vi.fn(() => []),
   } as unknown as CalendarActions;
   return { actions, emit: (id: string, event?: MouseEvent) => onAssemblyActionEvent.next({ id, event }) };
}

function fireAction(comp: VSCalendar, id: string, event?: MouseEvent) {
   const { actions, emit } = makeActions();
   comp.actions = actions;
   emit(id, event);
}

// ─── Group 1 – lifecycle ─────────────────────────────────────────────────────

describe("VSCalendar - interaction", () => {
   // ─── model setter – objectHeight ─────────────────────────────────────────

   describe("model setter - objectHeight", () => {
      it("should set model.objectHeight to titleFormat.height when dropdownCalendar is true", () => {
         const { comp } = createCalendarComponent({ model: { dropdownCalendar: true } });
         expect(comp.model.objectHeight).toBe(comp.model.titleFormat.height);
      });

      it("should set model.objectHeight to objectFormat.height when dropdownCalendar is false", () => {
         const { comp } = createCalendarComponent({ model: { dropdownCalendar: false } });
         expect(comp.model.objectHeight).toBe(comp.model.objectFormat.height);
      });
   });

   // ─── Group 1 – lifecycle ─────────────────────────────────────────────────

   describe("Group 1 - ngOnInit / ngOnDestroy", () => {
      it("should subscribe to globalSubmit on ngOnInit", () => {
         const { comp, globalSubmitService } = createCalendarComponent({
            model: { submitOnChange: false },
         });
         const subscribeSpy = vi.spyOn(globalSubmitService.submitAllSelections, "subscribe");

         comp.ngOnInit();

         expect(subscribeSpy).toHaveBeenCalledTimes(1);
      });

      it("should unsubscribe submit and action subscriptions on ngOnDestroy", () => {
         const { comp } = createCalendarComponent({ model: { submitOnChange: false } });
         comp.ngOnInit();
         const { actions } = makeActions();
         comp.actions = actions;

         // submitSubscription is private — cast needed to verify teardown
         const submitSub = (comp as any).submitSubscription;
         const actionSub = (comp as any).actionSubscription;
         const submitSpy = vi.spyOn(submitSub, "unsubscribe");
         const actionSpy = vi.spyOn(actionSub, "unsubscribe");

         comp.ngOnDestroy();

         expect(submitSpy).toHaveBeenCalledTimes(1);
         expect(actionSpy).toHaveBeenCalledTimes(1);
      });
   });

   // ─── Group 2 – actions setter ───────────────────────────────────────────

   describe("Group 2 - actions setter: event id dispatch", () => {
      it("should call toggleYear when event id is 'calendar toggle-year'", () => {
         const { comp } = createCalendarComponent();
         comp.model.yearView = false;
         (comp as any).calendar1 = makeCalendarRef();
         const spy = vi.spyOn(comp as any, "toggleYear");
         fireAction(comp, "calendar toggle-year");
         expect(spy).toHaveBeenCalledTimes(1);
      });

      it("should call toggleDoubleCalendar when event id is 'calendar toggle-double-calendar'", () => {
         const { comp } = createCalendarComponent();
         // mockImplementation prevents the real impl from running; we only test dispatch here
         const spy = vi.spyOn(comp, "toggleDoubleCalendar").mockImplementation(() => {});
         fireAction(comp, "calendar toggle-double-calendar");
         expect(spy).toHaveBeenCalledTimes(1);
      });

      it("should call clearCalendar when event id is 'calendar clear'", () => {
         const { comp } = createCalendarComponent();
         const spy = vi.spyOn(comp, "clearCalendar");
         fireAction(comp, "calendar clear");
         expect(spy).toHaveBeenCalledTimes(1);
      });

      it("should call toggleRangeComparison when event id is 'calendar toggle-range-comparison'", () => {
         const { comp } = createCalendarComponent();
         const spy = vi.spyOn(comp as any, "toggleRangeComparison");
         fireAction(comp, "calendar toggle-range-comparison");
         expect(spy).toHaveBeenCalledTimes(1);
      });

      it("should toggle model.multiSelect when event id is 'calendar multi-select'", () => {
         const { comp } = createCalendarComponent({ model: { multiSelect: false } });
         comp.model.multiSelect = false;
         fireAction(comp, "calendar multi-select");
         expect(comp.model.multiSelect).toBe(true);
      });

      it("should call applyCalendar when event id is 'calendar apply'", () => {
         const { comp } = createCalendarComponent();
         const spy = vi.spyOn(comp, "applyCalendar");
         fireAction(comp, "calendar apply");
         expect(spy).toHaveBeenCalledTimes(1);
      });

      it("should call toggleDropdown when event id is 'calendar show'", () => {
         const { comp } = createCalendarComponent();
         const spy = vi.spyOn(comp, "toggleDropdown");
         fireAction(comp, "calendar show");
         expect(spy).toHaveBeenCalledTimes(1);
      });

      it("should call toggleDropdown when event id is 'calendar hide'", () => {
         const { comp } = createCalendarComponent();
         const spy = vi.spyOn(comp, "toggleDropdown");
         fireAction(comp, "calendar hide");
         expect(spy).toHaveBeenCalledTimes(1);
      });

      it("should emit onOpenFormatPane when event id is 'calendar show-format-pane'", () => {
         const { comp } = createCalendarComponent();
         const emitSpy = vi.spyOn(comp.onOpenFormatPane, "emit");
         fireAction(comp, "calendar show-format-pane");
         expect(emitSpy).toHaveBeenCalledWith(comp.model);
      });

      it("should unsubscribe the old subscription when actions setter is called a second time", () => {
         const { comp } = createCalendarComponent();
         const { actions: actions1 } = makeActions();
         comp.actions = actions1;
         // actionSubscription is private — cast needed to verify teardown when replaced
         const oldSub = (comp as any).actionSubscription;
         const unsubSpy = vi.spyOn(oldSub, "unsubscribe");
         const { actions: actions2 } = makeActions();
         comp.actions = actions2;
         expect(unsubSpy).toHaveBeenCalledTimes(1);
      });
   });

   // ─── Group 3 – clearCalendar ────────────────────────────────────────────

   describe("Group 3 - clearCalendar", () => {
      it("should send a server event when submitOnChange is true", () => {
         const { comp, socket } = createCalendarComponent({ model: { submitOnChange: true } });
         comp.clearCalendar();
         expect(socket.sendEvent).toHaveBeenCalledWith(
            "/events/calendar/clearCalendar/Calendar1",
         );
      });

      it("should reset dates and call applyCalendar when submitOnChange is false", () => {
         const { comp } = createCalendarComponent({ model: { submitOnChange: false } });
         comp.model.dates1 = [{ year: 2025, month: 0, value: 1, dateType: "d" }];
         comp.model.dates2 = [{ year: 2025, month: 1, value: 10, dateType: "d" }];
         const applySpy = vi.spyOn(comp, "applyCalendar");

         comp.clearCalendar();

         expect(comp.model.dates1).toEqual([]);
         expect(comp.model.dates2).toEqual([]);
         expect(applySpy).toHaveBeenCalledTimes(1);
      });
   });

   // ─── Group 4 – toggleDropdown ────────────────────────────────────────────

   describe("Group 4 - toggleDropdown", () => {
      it("should call onHide when calendarsShown is true", () => {
         const { comp } = createCalendarComponent({ model: { calendarsShown: true } });
         const hideSpy = vi.spyOn(comp, "onHide");
         comp.toggleDropdown();
         expect(hideSpy).toHaveBeenCalledTimes(1);
      });

      it("should call onShow when calendarsShown is false", () => {
         const { comp } = createCalendarComponent({ model: { calendarsShown: false } });
         const showSpy = vi.spyOn(comp, "onShow");
         comp.toggleDropdown();
         expect(showSpy).toHaveBeenCalledTimes(1);
      });
   });

   // ─── Group 5 – onShow / onHide ───────────────────────────────────────────

   describe("Group 5 - onShow / onHide", () => {
      it("should set calendarsShown to false on onHide", () => {
         const { comp } = createCalendarComponent({ model: { calendarsShown: true } });
         comp.onHide();
         expect(comp.model.calendarsShown).toBe(false);
      });

      it("should set calendarsShown to true and register a mousedown listener on onShow", () => {
         const { comp, renderer } = createCalendarComponent();
         comp.onShow();
         expect(comp.model.calendarsShown).toBe(true);
         expect(renderer.listen).toHaveBeenCalledWith("document", "mousedown", expect.any(Function));
      });

      it("should call onHide and remove the listener when mousedown is outside the element", () => {
         const { comp } = createCalendarComponent();
         const cleanupFn = vi.fn();
         // renderer.listen returns a cleanup fn — override to capture it
         (comp as any).renderer.listen = vi.fn().mockReturnValue(cleanupFn);
         comp.onShow();

         // Trigger the document mousedown handler with a target not in elementRef
         const handler = (comp as any).renderer.listen.mock.calls[0][2] as Function;
         handler({ target: document.createElement("div") });

         expect(comp.model.calendarsShown).toBe(false);
         expect(cleanupFn).toHaveBeenCalledTimes(1);
      });
   });

   // ─── Group 6 – toggleDoubleCalendar ──────────────────────────────────────

   describe("Group 6 - toggleDoubleCalendar", () => {
      it("should check form data and then send toggle-double-calendar event", () => {
         const { comp, socket, formDataService } = createCalendarComponent({
            model: { doubleCalendar: false },
         });
         (comp as any).calendar1 = makeCalendarRef({
            getCurrentDateString: vi.fn().mockReturnValue("2025-0"),
         });
         comp.model.currentDate2 = { year: 2025, month: 1 };
         comp.model.range = { minYear: 2020, minMonth: 0, maxYear: 2030, maxMonth: 11 };

         comp.toggleDoubleCalendar();

         expect(formDataService.checkFormData).toHaveBeenCalledTimes(1);
         expect(socket.sendEvent).toHaveBeenCalledWith(
            "/events/calendar/toggleDoubleCalendar/Calendar1",
            expect.objectContaining({ doubleCalendar: true }),
         );
      });

      it("should emit onToggleDoubleCalendar with the new state", () => {
         const { comp } = createCalendarComponent({ model: { doubleCalendar: false } });
         (comp as any).calendar1 = makeCalendarRef();
         comp.model.currentDate2 = { year: 2025, month: 1 };
         comp.model.range = { minYear: 2020, minMonth: 0, maxYear: 2030, maxMonth: 11 };
         const emitSpy = vi.spyOn(comp.onToggleDoubleCalendar, "emit");

         comp.toggleDoubleCalendar();

         expect(emitSpy).toHaveBeenCalledWith(true);
      });
   });

   // ─── Group 7 – toggleYear ────────────────────────────────────────────────

   describe("Group 7 - toggleYear", () => {
      it("should check form data and send a toggle-year-view event", () => {
         const { comp, socket, formDataService } = createCalendarComponent({
            model: { yearView: false },
         });
         (comp as any).calendar1 = makeCalendarRef();

         comp.toggleYear();

         expect(formDataService.checkFormData).toHaveBeenCalledTimes(1);
         expect(socket.sendEvent).toHaveBeenCalledWith(
            "/events/calendar/toggleYearView/Calendar1",
            expect.objectContaining({ yearView: true }),
         );
      });
   });

   // ─── Group 8 – applyCalendar ──────────────────────────────────────────────

   describe("Group 8 - applyCalendar", () => {
      it("should check form data and send an applyCalendar event", () => {
         const { comp, socket, formDataService } = createCalendarComponent();
         (comp as any).calendar1 = makeCalendarRef({
            getCurrentDateString: vi.fn().mockReturnValue("2025-3"),
            getDateArray: vi.fn().mockReturnValue(["2025/4/1"]),
         });

         comp.applyCalendar();

         expect(formDataService.checkFormData).toHaveBeenCalledTimes(1);
         expect(socket.sendEvent).toHaveBeenCalledWith(
            "/events/calendar/applyCalendar/Calendar1",
            expect.objectContaining({ dates: ["2025/4/1"] }),
         );
      });

      it("should reset pendingChange to false before delegating to checkFormData", () => {
         const { comp } = createCalendarComponent();
         comp.pendingChange = true;
         (comp as any).calendar1 = makeCalendarRef();
         comp.applyCalendar();
         expect(comp.pendingChange).toBe(false);
      });
   });

   // ─── Group 9 – applyCalendar0 ────────────────────────────────────────────

   describe("Group 9 - applyCalendar0", () => {
      it("should set pendingChange and call updateSelected when ctrl key is held", () => {
         const { comp } = createCalendarComponent();
         // ctrlDown is private — it is set by the keyboard listener; seed it directly to test the branch
         (comp as any).ctrlDown = true;
         const updateSpy = vi.fn();
         (comp as any).calendar1 = makeCalendarRef({ updateSelected: updateSpy });

         comp.applyCalendar0();

         expect(comp.pendingChange).toBe(true);
         expect(updateSpy).toHaveBeenCalledTimes(1);
      });

      it("should call applyCalendar directly when ctrl key is not held", () => {
         const { comp } = createCalendarComponent();
         (comp as any).ctrlDown = false;
         (comp as any).calendar1 = makeCalendarRef();
         const applySpy = vi.spyOn(comp, "applyCalendar");

         comp.applyCalendar0();

         expect(applySpy).toHaveBeenCalledTimes(1);
      });
   });

   // ─── Group 10 – updateTitle ───────────────────────────────────────────────

   describe("Group 10 - updateTitle", () => {
      it("should send a changeTitle event when not in viewer context", () => {
         const { comp, socket } = createCalendarComponent({
            context: { viewer: false, preview: false, binding: false, composer: true,
               vsWizard: false, vsWizardPreview: false, embedAssembly: false },
         });
         comp.model.title = "My Calendar";

         comp.updateTitle();

         expect(socket.sendEvent).toHaveBeenCalledWith(
            "/events/composer/viewsheet/objects/changeTitle",
            expect.objectContaining({ text: "My Calendar" }),
         );
      });

      it("should not send an event when in viewer context", () => {
         const { comp, socket } = createCalendarComponent({
            context: { viewer: true, preview: false, binding: false, composer: false,
               vsWizard: false, vsWizardPreview: false, embedAssembly: false },
         });
         comp.updateTitle();
         expect(socket.sendEvent).not.toHaveBeenCalled();
      });

      it("should set selectionTitle to model.title when there is no active selection", () => {
         // Bug #16080: updateTitle() calls updateSelectedTitle() which falls back to model.title
         const { comp } = createCalendarComponent({
            context: { viewer: false, preview: false, binding: false, composer: true,
               vsWizard: false, vsWizardPreview: false, embedAssembly: false },
         });
         comp.model.title = "Calendar02";
         (comp as any).calendar1 = makeCalendarRef({ getSelectionString: vi.fn().mockReturnValue("") });

         comp.updateTitle();

         expect(comp.selectionTitle).toContain("Calendar02");
      });
   });

   // ─── Group 11 – headerClick ───────────────────────────────────────────────

   describe("Group 11 - headerClick", () => {
      it("should call onShow when mobile, dropdown, and calendarsShown is false", () => {
         const { comp } = createCalendarComponent({
            model: { dropdownCalendar: true, calendarsShown: false },
         });
         (comp as any).mobileDevice = true;
         const showSpy = vi.spyOn(comp, "onShow");

         comp.headerClick();

         expect(showSpy).toHaveBeenCalledTimes(1);
      });

      it("should call onHide when mobile, dropdown, and calendarsShown is true", () => {
         const { comp } = createCalendarComponent({
            model: { dropdownCalendar: true, calendarsShown: true },
         });
         (comp as any).mobileDevice = true;
         const hideSpy = vi.spyOn(comp, "onHide");

         comp.headerClick();

         expect(hideSpy).toHaveBeenCalledTimes(1);
      });

      it("should not toggle when not a mobile device", () => {
         const { comp } = createCalendarComponent({
            model: { dropdownCalendar: true, calendarsShown: false },
         });
         (comp as any).mobileDevice = false;
         const showSpy = vi.spyOn(comp, "onShow");

         comp.headerClick();

         expect(showSpy).not.toHaveBeenCalled();
      });

      it("should not toggle when not a dropdown calendar", () => {
         const { comp } = createCalendarComponent({
            model: { dropdownCalendar: false, calendarsShown: false },
         });
         (comp as any).mobileDevice = true;
         const showSpy = vi.spyOn(comp, "onShow");

         comp.headerClick();

         expect(showSpy).not.toHaveBeenCalled();
      });
   });

   // ─── Group 12 – miniMenuClosed / selectedDatesChange ────────────────────

   describe("Group 12 - miniMenuClosed and selectedDatesChange", () => {
      it("should set miniMenuOpen to false on miniMenuClosed", () => {
         const { comp } = createCalendarComponent();
         (comp as any).miniMenuOpen = true;
         comp.miniMenuClosed();
         // miniMenuOpen is private — cast needed to verify the flag is cleared
         expect((comp as any).miniMenuOpen).toBe(false);
      });

      it("should set pendingChange to true on selectedDatesChange", () => {
         const { comp } = createCalendarComponent();
         comp.pendingChange = false;
         comp.selectedDatesChange();
         // pendingChange → true here; the reset to false is covered in Group 8 applyCalendar tests
         expect(comp.pendingChange).toBe(true);
      });
   });

   // ─── Group 13 – syncDateChange / syncPeriods / selectRange ───────────────

   describe("Group 13 - syncDateChange, syncPeriods, selectRange", () => {
      it("should do nothing when either calendar is missing in syncDateChange", () => {
         const { comp } = createCalendarComponent();
         (comp as any).calendar2 = null;
         expect(() => comp.syncDateChange(true)).not.toThrow();
      });

      it("should call syncDate on the other calendar when years differ in syncDateChange", () => {
         const { comp } = createCalendarComponent();
         const cal1 = makeCalendarRef({ currentDate: { year: 2026, month: 3 } });
         const cal2 = makeCalendarRef({ currentDate: { year: 2025, month: 3 } });
         (comp as any).calendar1 = cal1;
         (comp as any).calendar2 = cal2;

         // secondCalendar=false means cal1 is current, cal2 is other
         // cal1.year(2026) > cal2.year(2025) → syncDate(true) on cal2
         comp.syncDateChange(false);

         expect(cal2.syncDate).toHaveBeenCalledWith(true);
      });

      it("should do nothing when either calendar is missing in syncPeriods", () => {
         const { comp } = createCalendarComponent();
         (comp as any).calendar2 = null;
         expect(() => comp.syncPeriods(false)).not.toThrow();
      });

      it("should call syncPeriod on calendar2 using calendar1.dates when secondCalendar is false", () => {
         const { comp } = createCalendarComponent();
         const dates = [{ year: 2025, month: 3, value: 5, dateType: "d" }] as any;
         const cal1 = makeCalendarRef({ dates });
         const cal2 = makeCalendarRef();
         (comp as any).calendar1 = cal1;
         (comp as any).calendar2 = cal2;

         comp.syncPeriods(false);

         expect(cal2.syncPeriod).toHaveBeenCalledWith(dates);
      });

      it("should do nothing when either calendar is missing in selectRange", () => {
         const { comp } = createCalendarComponent();
         (comp as any).calendar2 = null;
         const dateModel = { year: 2025, month: 3, value: 15, dateType: "d" } as any;
         expect(() => comp.selectRange(dateModel, false)).not.toThrow();
      });

      it("should update the other calendar dates on selectRange", () => {
         const { comp } = createCalendarComponent();
         const cal2 = makeCalendarRef();
         (comp as any).calendar2 = cal2;
         const selectDay = { year: 2025, month: 5, value: 10, dateType: "d" } as any;

         // secondCalendar=false → otherCalendar is calendar2
         comp.selectRange(selectDay, false);

         expect(cal2.currentDate.year).toBe(2025);
         expect(cal2.currentDate.month).toBe(5);
         expect(cal2.dates).toEqual([selectDay]);
         expect(cal2.dateChanged).toHaveBeenCalledTimes(1);
      });
   });
});
