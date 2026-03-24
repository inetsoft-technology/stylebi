/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
import { HttpClientTestingModule, HttpTestingController } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import {
   EDIT_TASKS_URI,
   SAVE_TASK_URI,
   ScheduleTaskEditorDataService
} from "./schedule-task-editor-data.service";

const MOCK_DIALOG_MODEL = {
   name: "Daily Report",
   actions: [],
   conditions: []
} as any;

const MOCK_EDITOR_MODEL = {
   name: "Daily Report",
   taskDefaultTime: false,
   actions: [],
   conditions: []
} as any;

describe("ScheduleTaskEditorDataService", () => {
   let service: ScheduleTaskEditorDataService;
   let http: HttpTestingController;

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [HttpClientTestingModule],
         providers: [ScheduleTaskEditorDataService]
      });
      service = TestBed.inject(ScheduleTaskEditorDataService);
      http = TestBed.inject(HttpTestingController);
   });

   afterEach(() => {
      http.verify();
   });

   // ── loadTask ───────────────────────────────────────────────────────────────

   it("loadTask calls GET on the correct URI", () => {
      service.loadTask("Daily Report").subscribe();
      const req = http.expectOne(r => r.url === EDIT_TASKS_URI);
      expect(req.request.method).toBe("GET");
      req.flush(MOCK_DIALOG_MODEL);
   });

   it("loadTask passes the taskName as a query parameter", () => {
      service.loadTask("My Task").subscribe();
      const req = http.expectOne(r => r.url === EDIT_TASKS_URI);
      expect(req.request.params.get("taskName")).toBe("My Task");
      req.flush(MOCK_DIALOG_MODEL);
   });

   it("loadTask returns the dialog model from the server", () => {
      let result: any;
      service.loadTask("Daily Report").subscribe(m => { result = m; });
      http.expectOne(r => r.url === EDIT_TASKS_URI).flush(MOCK_DIALOG_MODEL);
      expect(result).toEqual(MOCK_DIALOG_MODEL);
   });

   it("loadTask URL-encodes a task name with special characters", () => {
      service.loadTask("Report: Q1 & Q2").subscribe();
      const req = http.expectOne(r => r.url === EDIT_TASKS_URI);
      // Angular's HttpParams encodes the value correctly
      expect(req.request.params.get("taskName")).toBe("Report: Q1 & Q2");
      req.flush(MOCK_DIALOG_MODEL);
   });

   // ── saveTask ───────────────────────────────────────────────────────────────

   it("saveTask calls POST on the correct URI", () => {
      service.saveTask(MOCK_EDITOR_MODEL).subscribe();
      const req = http.expectOne(SAVE_TASK_URI);
      expect(req.request.method).toBe("POST");
      req.flush(MOCK_DIALOG_MODEL);
   });

   it("saveTask sends the editor model as the request body", () => {
      service.saveTask(MOCK_EDITOR_MODEL).subscribe();
      const req = http.expectOne(SAVE_TASK_URI);
      expect(req.request.body).toEqual(MOCK_EDITOR_MODEL);
      req.flush(MOCK_DIALOG_MODEL);
   });

   it("saveTask returns the updated dialog model from the server", () => {
      let result: any;
      service.saveTask(MOCK_EDITOR_MODEL).subscribe(m => { result = m; });
      http.expectOne(SAVE_TASK_URI).flush(MOCK_DIALOG_MODEL);
      expect(result).toEqual(MOCK_DIALOG_MODEL);
   });
});
