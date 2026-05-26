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
 * ScheduleTaskNamesService — unit tests
 *
 * Risk-first coverage (3 groups, 11 cases):
 *   Group 1 [Risk 3, 3, 2, 2, 2, 2, 2, 2, 2] — loadScheduleTaskNames (9 cases)
 *   Group 2 [Risk 2]                           — getAllTasks (1 case)
 *   Group 3 [Risk 2]                           — ngOnDestroy (1 case)
 *
 * KEY contracts:
 *   - allTasks starts as null and emits the task list only after a successful HTTP response
 *   - loadScheduleTaskNames() is re-entrant-safe: a second call while in-flight sets reload=true and defers
 *   - PORTAL=true → portal URL; PORTAL=false → EM URL
 *
 * Design gaps:
 *   - HTTP error handler is empty (()=>{}); loading never resets — service is permanently stuck after a network error
 *   - @Optional PORTAL=null (token not provided) is semantically distinct from false but exercises the same code path
 */
import { HttpClientTestingModule, HttpTestingController } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import { NameLabelTuple } from "../util/name-label-tuple";
import { ScheduleTaskNamesService } from "./schedule-task-names.service";
import { PORTAL } from "./schedule-users.service";

const SAMPLE_TASKS: NameLabelTuple[] = [
   { name: "task1", label: "Task One" },
   { name: "task2", label: "Task Two" },
];

describe("ScheduleTaskNamesService", () => {
   // ---------------------------------------------------------------------------
   // Group 1 [Risk 3, 3, 2, 2, 2, 2, 2, 2, 2] — loadScheduleTaskNames (9 cases)
   // ---------------------------------------------------------------------------
   describe("loadScheduleTaskNames()", () => {
      describe("EM context (PORTAL=false)", () => {
         let service: ScheduleTaskNamesService;
         let httpMock: HttpTestingController;

         beforeEach(() => {
            TestBed.configureTestingModule({
               imports: [HttpClientTestingModule],
               providers: [
                  ScheduleTaskNamesService,
                  { provide: PORTAL, useValue: false },
               ],
            });
            service = TestBed.inject(ScheduleTaskNamesService);
            httpMock = TestBed.inject(HttpTestingController);
         });

         afterEach(() => httpMock.verify());

         it("[Risk 2] requests the EM task-names endpoint", () => {
            const req = httpMock.expectOne("../api/em/schedule/task-names");
            expect(req.request.method).toBe("GET");
            req.flush({ allTasks: [] });
         });

         it("[Risk 2] allTasks emits null before the HTTP response arrives", () => {
            let current: NameLabelTuple[] | null | undefined;
            service.allTasks.subscribe(v => (current = v));
            expect(current).toBeNull();
            httpMock.expectOne("../api/em/schedule/task-names").flush({ allTasks: [] });
         });

         it("[Risk 2] allTasks emits the returned tasks after a successful response", () => {
            let emitted: NameLabelTuple[] | null | undefined;
            service.allTasks.subscribe(v => (emitted = v));
            httpMock.expectOne("../api/em/schedule/task-names").flush({ allTasks: SAMPLE_TASKS });
            expect(emitted).toEqual(SAMPLE_TASKS);
         });

         it("[Risk 2] allTasks emits an empty array when the server returns no tasks", () => {
            let emitted: NameLabelTuple[] | null | undefined;
            service.allTasks.subscribe(v => (emitted = v));
            httpMock.expectOne("../api/em/schedule/task-names").flush({ allTasks: [] });
            expect(emitted).toEqual([]);
         });

         it("[Risk 2] isLoading is true while in-flight and false after completion", () => {
            expect(service.isLoading).toBe(true);
            httpMock.expectOne("../api/em/schedule/task-names").flush({ allTasks: [] });
            expect(service.isLoading).toBe(false);
         });

         it("[Risk 3] reload mechanism: a call while in-flight defers and fires after the first completes", () => {
            // 🔁 Regression-sensitive: reload flag must be consumed exactly once per in-flight cycle
            service.loadScheduleTaskNames();
            const req = httpMock.expectOne("../api/em/schedule/task-names");
            req.flush({ allTasks: [] });
            httpMock.expectOne("../api/em/schedule/task-names").flush({ allTasks: [] });
         });

         it("[Risk 3] isLoading stays true and no new request fires after an HTTP error", () => {
            // 🔁 Regression-sensitive: empty error handler leaves loading=true; service becomes permanently stuck
            httpMock.expectOne("../api/em/schedule/task-names").error(new ErrorEvent("network"));
            expect(service.isLoading).toBe(true);
            service.loadScheduleTaskNames();
            httpMock.expectNone("../api/em/schedule/task-names");
         });
      });

      describe("Portal context (PORTAL=true)", () => {
         let service: ScheduleTaskNamesService;
         let httpMock: HttpTestingController;

         beforeEach(() => {
            TestBed.configureTestingModule({
               imports: [HttpClientTestingModule],
               providers: [
                  ScheduleTaskNamesService,
                  { provide: PORTAL, useValue: true },
               ],
            });
            service = TestBed.inject(ScheduleTaskNamesService);
            httpMock = TestBed.inject(HttpTestingController);
         });

         afterEach(() => httpMock.verify());

         it("[Risk 2] requests the portal task-names endpoint", () => {
            const req = httpMock.expectOne("../api/portal/schedule/task-names");
            expect(req.request.method).toBe("GET");
            req.flush({ allTasks: [] });
         });

         it("[Risk 2] populates allTasks from the portal endpoint response", () => {
            let emitted: NameLabelTuple[] | null | undefined;
            service.allTasks.subscribe(v => (emitted = v));
            httpMock.expectOne("../api/portal/schedule/task-names").flush({ allTasks: SAMPLE_TASKS });
            expect(emitted).toEqual(SAMPLE_TASKS);
         });
      });
   });

   // ---------------------------------------------------------------------------
   // Group 2 [Risk 2] — getAllTasks (1 case)
   // ---------------------------------------------------------------------------
   describe("getAllTasks()", () => {
      let service: ScheduleTaskNamesService;
      let httpMock: HttpTestingController;

      beforeEach(() => {
         TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [
               ScheduleTaskNamesService,
               { provide: PORTAL, useValue: false },
            ],
         });
         service = TestBed.inject(ScheduleTaskNamesService);
         httpMock = TestBed.inject(HttpTestingController);
      });

      afterEach(() => httpMock.verify());

      it("[Risk 2] returns an observable that mirrors allTasks", () => {
         let result: NameLabelTuple[] | null | undefined;
         service.getAllTasks().subscribe(v => (result = v));
         httpMock
            .expectOne("../api/em/schedule/task-names")
            .flush({ allTasks: [{ name: "t", label: "T" }] });
         expect(result).toEqual([{ name: "t", label: "T" }]);
      });
   });

   // ---------------------------------------------------------------------------
   // Group 3 [Risk 2] — ngOnDestroy (1 case)
   // ---------------------------------------------------------------------------
   describe("ngOnDestroy()", () => {
      let service: ScheduleTaskNamesService;
      let httpMock: HttpTestingController;

      beforeEach(() => {
         TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [
               ScheduleTaskNamesService,
               { provide: PORTAL, useValue: false },
            ],
         });
         service = TestBed.inject(ScheduleTaskNamesService);
         httpMock = TestBed.inject(HttpTestingController);
         httpMock.expectOne("../api/em/schedule/task-names").flush({ allTasks: [] });
      });

      afterEach(() => httpMock.verify());

      it("[Risk 2] completes the allTasks subject on ngOnDestroy", () => {
         let completed = false;
         service.allTasks.subscribe({ complete: () => (completed = true) });
         service.ngOnDestroy();
         expect(completed).toBe(true);
      });
   });
});
