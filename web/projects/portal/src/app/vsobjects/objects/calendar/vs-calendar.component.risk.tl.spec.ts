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
 * VSCalendar - Pass 2: Risk
 *
 * Coverage:
 *   Group 1 - updateFormatedSelectedString: POST to /api/calendar/formatdates
 *   Group 2 - updateCalendarTitleView + updateCalendarTitleView0: setTimeout + POST
 *   Group 3 - updateSelectionString: triggers format POST + globalSubmit state update
 *   Group 4 - ngOnInit globalSubmit: subscription fires applyCalendar on flush
 *   Group 5 - onKeyUp: pendingChange flush when ctrl-up and submitOnChange is true
 */

import { HttpClient } from "@angular/common/http";
import { HttpClientTestingModule, HttpTestingController } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";

import {
   createCalendarComponent,
   makeCalendarRef,
} from "./vs-calendar.component.test-helpers";
import { GlobalSubmitService } from "../../util/global-submit.service";

const FORMAT_DATES_URI = "../api/calendar/formatdates";
const FORMAT_TITLE_URI = "../api/calendar/formatTitle";

describe("VSCalendar - risk", () => {
   let http: HttpTestingController;

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [HttpClientTestingModule],
      });
      http = TestBed.inject(HttpTestingController);
   });

   afterEach(() => {
      http.verify();
      vi.restoreAllMocks();
      TestBed.resetTestingModule();
   });

   // ─── Group 1 – updateFormatedSelectedString ──────────────────────────────

   describe("Group 1 - updateFormatedSelectedString", () => {
      it("should POST to formatdates and set formatedSelectedString on success", () => {
         const httpClient = TestBed.inject(HttpClient);
         const { comp } = createCalendarComponent({ http: httpClient });

         comp.updateFormatedSelectedString("2025/4/1");

         const req = http.expectOne((r) => r.url === FORMAT_DATES_URI);
         expect(req.request.method).toBe("POST");
         expect(req.request.body).toMatchObject({
            assemblyName: "Calendar1",
            dates: "2025/4/1",
         });
         req.flush("Apr 1, 2025");

         expect(comp.formatedSelectedString).toBe("Apr 1, 2025");
      });

      it("should not update formatedSelectedString when the response is falsy", () => {
         const httpClient = TestBed.inject(HttpClient);
         const { comp } = createCalendarComponent({ http: httpClient });
         comp.formatedSelectedString = "original";

         comp.updateFormatedSelectedString("2025/4/1");

         http.expectOne(FORMAT_DATES_URI).flush(null);

         expect(comp.formatedSelectedString).toBe("original");
      });
   });

   // ─── Group 2 – updateCalendarTitleView ───────────────────────────────────

   describe("Group 2 - updateCalendarTitleView (setTimeout + POST)", () => {
      beforeEach(() => vi.useFakeTimers());
      afterEach(() => vi.useRealTimers());

      it("should POST to formatTitle and set calendarTitleView1 for the primary calendar", () => {
         const httpClient = TestBed.inject(HttpClient);
         const { comp } = createCalendarComponent({ http: httpClient });
         (comp as any).calendar1 = makeCalendarRef({
            getCurrentDateString: vi.fn().mockReturnValue("2025-3"),
         });

         comp.updateCalendarTitleView(false);
         vi.runAllTimers();

         const req = http.expectOne((r) => r.url === FORMAT_TITLE_URI);
         expect(req.request.method).toBe("POST");
         req.flush("April 2025");

         expect(comp.model.calendarTitleView1).toBe("April 2025");
         expect((comp as any).calendar1.resetOldDate).toHaveBeenCalledTimes(1);
      });

      it("should POST to formatTitle and set calendarTitleView2 for the second calendar", () => {
         const httpClient = TestBed.inject(HttpClient);
         const { comp } = createCalendarComponent({ http: httpClient });
         (comp as any).calendar2 = makeCalendarRef({
            getCurrentDateString: vi.fn().mockReturnValue("2025-4"),
         });

         comp.updateCalendarTitleView(true);
         vi.runAllTimers();

         const req = http.expectOne((r) => r.url === FORMAT_TITLE_URI);
         req.flush("May 2025");

         expect(comp.model.calendarTitleView2).toBe("May 2025");
         expect((comp as any).calendar2.resetOldDate).toHaveBeenCalledTimes(1);
      });

      it("should not issue any HTTP request when the target calendar ref is absent", () => {
         const httpClient = TestBed.inject(HttpClient);
         const { comp } = createCalendarComponent({ http: httpClient });
         (comp as any).calendar2 = null;

         comp.updateCalendarTitleView(true);
         vi.runAllTimers();

         http.expectNone(FORMAT_TITLE_URI);
      });

      it("should not update calendarTitleView when the response is falsy", () => {
         const httpClient = TestBed.inject(HttpClient);
         const { comp } = createCalendarComponent({ http: httpClient });
         (comp as any).calendar1 = makeCalendarRef();
         comp.model.calendarTitleView1 = "original";

         comp.updateCalendarTitleView(false);
         vi.runAllTimers();

         http.expectOne(FORMAT_TITLE_URI).flush(null);

         expect(comp.model.calendarTitleView1).toBe("original");
      });
   });

   // ─── Group 3 – updateSelectionString ────────────────────────────────────

   describe("Group 3 - updateSelectionString", () => {
      it("should trigger a formatdates POST when the selection string changes", () => {
         const httpClient = TestBed.inject(HttpClient);
         const { comp } = createCalendarComponent({ http: httpClient });
         (comp as any).calendar1 = makeCalendarRef({
            getSelectionString: vi.fn().mockReturnValue("2025/4/1"),
         });
         // Seed a previous value so the change-detection branch fires
         (comp as any).selectedString = "";

         comp.updateSelectionString();

         const req = http.expectOne((r) => r.url === FORMAT_DATES_URI);
         req.flush("Apr 1, 2025");

         expect(comp.formatedSelectedString).toBe("Apr 1, 2025");
      });

      it("should call globalSubmitService.updateState with the new selection and pending=true when submitOnChange is false", () => {
         const httpClient = TestBed.inject(HttpClient);
         const globalSubmitService = new GlobalSubmitService();
         const { comp } = createCalendarComponent({
            http: httpClient,
            globalSubmitService,
            model: { submitOnChange: false },
         });
         (comp as any).calendar1 = makeCalendarRef({
            getSelectionString: vi.fn().mockReturnValue("2025/4/1"),
         });
         (comp as any).selectedString = "";
         const updateStateSpy = vi.spyOn(globalSubmitService, "updateState");

         comp.updateSelectionString();
         http.expectOne(FORMAT_DATES_URI).flush("Apr 1, 2025");

         expect(updateStateSpy).toHaveBeenCalledWith("Calendar1", ["2025/4/1"], true);
      });

      it("should clear selectedString and update title when selection is null", () => {
         const httpClient = TestBed.inject(HttpClient);
         const { comp } = createCalendarComponent({ http: httpClient });
         (comp as any).calendar1 = makeCalendarRef({
            getSelectionString: vi.fn().mockReturnValue(""),
         });
         (comp as any).selectedString = "old value";

         comp.updateSelectionString();

         expect(comp.selectedString).toBeFalsy();
         http.expectNone(FORMAT_DATES_URI);
      });
   });

   // ─── Group 4 – ngOnInit globalSubmit ─────────────────────────────────────

   describe("Group 4 - ngOnInit globalSubmit subscription", () => {
      it("should call applyCalendar when globalSubmit fires and submitOnChange is false", () => {
         const globalSubmitService = new GlobalSubmitService();
         const { comp } = createCalendarComponent({
            globalSubmitService,
            model: { submitOnChange: false },
         });
         comp.ngOnInit();
         // mockImplementation prevents STOMP dispatch; we only verify the subscription fires
         const applySpy = vi.spyOn(comp, "applyCalendar").mockImplementation(() => {});

         globalSubmitService.submitGlobal("eventSource");

         expect(applySpy).toHaveBeenCalledWith("eventSource");
      });

      it("should not call applyCalendar when globalSubmit fires but submitOnChange is true", () => {
         const httpClient = TestBed.inject(HttpClient);
         const globalSubmitService = new GlobalSubmitService();
         const { comp } = createCalendarComponent({
            http: httpClient,
            globalSubmitService,
            model: { submitOnChange: true },
         });
         comp.ngOnInit();
         const applySpy = vi.spyOn(comp, "applyCalendar");

         globalSubmitService.submitGlobal("eventSource");

         expect(applySpy).not.toHaveBeenCalled();
      });
   });

   // ─── Group 5 – onKeyUp pendingChange flush ────────────────────────────────

   describe("Group 5 - onKeyUp pendingChange flush", () => {
      it("should call applyCalendar and clear pendingChange when ctrl-up with pending change and submitOnChange", () => {
         const httpClient = TestBed.inject(HttpClient);
         const { comp } = createCalendarComponent({
            http: httpClient,
            model: { submitOnChange: true },
         });
         (comp as any).calendar1 = makeCalendarRef();
         comp.pendingChange = true;
         const applySpy = vi.spyOn(comp, "applyCalendar");

         comp.onKeyUp({ keyCode: 17 } as KeyboardEvent);

         expect(applySpy).toHaveBeenCalledTimes(1);
         expect(comp.pendingChange).toBe(false);
      });

      it("should not call applyCalendar on ctrl-up when pendingChange is false", () => {
         const httpClient = TestBed.inject(HttpClient);
         const { comp } = createCalendarComponent({
            http: httpClient,
            model: { submitOnChange: true },
         });
         comp.pendingChange = false;
         const applySpy = vi.spyOn(comp, "applyCalendar");

         comp.onKeyUp({ keyCode: 17 } as KeyboardEvent);

         expect(applySpy).not.toHaveBeenCalled();
      });

      it("should not call applyCalendar when a non-ctrl key is released", () => {
         const httpClient = TestBed.inject(HttpClient);
         const { comp } = createCalendarComponent({
            http: httpClient,
            model: { submitOnChange: true },
         });
         comp.pendingChange = true;
         const applySpy = vi.spyOn(comp, "applyCalendar");

         comp.onKeyUp({ keyCode: 65 } as KeyboardEvent);

         expect(applySpy).not.toHaveBeenCalled();
      });
   });
});
