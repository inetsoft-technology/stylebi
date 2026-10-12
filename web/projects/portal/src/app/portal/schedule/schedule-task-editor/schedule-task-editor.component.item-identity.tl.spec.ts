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
 * ScheduleTaskEditorComponent - a save after a delete sends the original index of each kept
 * action (Bug #77973)
 *
 * End to end in the client: the real router opens the real editor, which renders the real
 * TaskActionPane. MSW serves the dialog model with the original indexes the server sets and
 * captures the body of each save. The server pairs the saved actions with the stored actions by
 * these indexes, so a kept action must still carry its own index after the first action is
 * deleted, and the indexes of the save response must be used by the next save.
 */

import { provideHttpClient } from "@angular/common/http";
import { TestBed } from "@angular/core/testing";
import { By } from "@angular/platform-browser";
import { provideRouter } from "@angular/router";
import { RouterTestingHarness } from "@angular/router/testing";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { waitFor } from "@testing-library/angular";
import { http, HttpResponse } from "msw";
import { of, Subject } from "rxjs";
import { afterEach, describe, expect, it, vi } from "vitest";

import { server } from "@test-mocks/server";
import { GeneralActionModel } from "../../../../../../shared/schedule/model/general-action-model";
import { ScheduleActionModel } from "../../../../../../shared/schedule/model/schedule-action-model";
import { ScheduleTaskDialogModel } from "../../../../../../shared/schedule/model/schedule-task-dialog-model";
import { ScheduleTaskEditorModel } from "../../../../../../shared/schedule/model/schedule-task-editor-model";
import { ScheduleTaskNamesService } from "../../../../../../shared/schedule/schedule-task-names.service";
import { ScheduleUsersService } from "../../../../../../shared/schedule/schedule-users.service";
import { PortalModelService } from "../../services/portal-model.service";
import { TaskActionPane } from "./actions/task-action-pane.component";
import { makeAction, PORTAL_MODEL_MOCK } from "./actions/task-action-pane.test-helpers";
import { ScheduleTaskEditorComponent } from "./schedule-task-editor.component";
import {
   makeScheduleTaskDialogModel,
   makeTaskActionPaneModel,
} from "./schedule-task-editor.component.test-helpers";

function action(label: string, originalIndex: number): GeneralActionModel {
   return makeAction({
      label,
      sheet: "1^128^__NULL__^Examples/Census",
      to: label + "@x.com",
      password: "**********",
      originalIndex,
   });
}

function dialogModel(actions: ScheduleActionModel[]): ScheduleTaskDialogModel {
   return makeScheduleTaskDialogModel({
      name: "admin~;~host-org:IdTask",
      label: "IdTask",
      taskActionPaneModel: makeTaskActionPaneModel({ actions }),
   });
}

/**
 * The stored task the save response is built from: the server sets each saved action's index
 * to its new stored position.
 */
function storedAfterSave(posted: ScheduleTaskEditorModel): ScheduleTaskDialogModel {
   return dialogModel(posted.actions.map((a, i) => ({ ...a, originalIndex: i })));
}

describe("ScheduleTaskEditorComponent - saves send the action identity (Bug #77973)", () => {
   afterEach(() => {
      localStorage.clear();
      window.history.replaceState(null, "", "/");
      vi.restoreAllMocks();
   });

   it("sends the kept action's own index after the first action is deleted", async () => {
      const posted: ScheduleTaskEditorModel[] = [];
      const modal = { open: vi.fn() };

      server.use(
         http.get("*/api/portal/schedule/edit", () =>
            HttpResponse.json(dialogModel([action("A", 0), action("B", 1)]))),
         http.post("*/api/portal/schedule/save", async ({ request }) => {
            const body = await request.json() as ScheduleTaskEditorModel;
            posted.push(body);
            return HttpResponse.json(storedAfterSave(body));
         }),
         http.get("*/api/portal/schedule/task/action/*", () => HttpResponse.json([])),
      );

      const url = "/portal/tab/schedule/tasks/IdTask";
      window.history.replaceState(null, "", "/app" + url);

      TestBed.configureTestingModule({
         providers: [
            provideHttpClient(),
            provideRouter([
               { path: "portal/tab/schedule/tasks/:task", component: ScheduleTaskEditorComponent },
               { path: "portal/tab/schedule/tasks", children: [] },
            ]),
            { provide: NgbModal, useValue: modal },
            { provide: PortalModelService, useValue: PORTAL_MODEL_MOCK },
            {
               provide: ScheduleUsersService,
               useValue: { getEmailUsers: () => of([]), getEmailGroups: () => of([]) },
            },
            {
               provide: ScheduleTaskNamesService,
               useValue: { loadScheduleTaskNames: vi.fn(), getAllTasks: () => of([]), isLoading: false },
            },
         ],
      });

      const harness = await RouterTestingHarness.create();
      const editor = await harness.navigateByUrl(url, ScheduleTaskEditorComponent);
      await waitFor(() => expect(editor.model).toBeDefined());
      editor.selectedTab = "action";
      harness.detectChanges();
      await harness.fixture.whenStable();

      const paneOf = () => harness.fixture.debugElement
         .query(By.directive(TaskActionPane)).componentInstance as TaskActionPane;
      expect(paneOf().model.actions.map(a => a.originalIndex)).toEqual([0, 1]);

      // the user deletes A in the action list and confirms
      modal.open.mockImplementationOnce(() => ({
         result: Promise.resolve("ok"),
         componentInstance: { onCommit: new Subject<string>() },
         close: vi.fn(),
         dismiss: vi.fn(),
      }));
      paneOf().selectedActions = [0];
      paneOf().deleteAction();
      await waitFor(() => expect(paneOf().model.actions).toHaveLength(1));

      // the pane saves through the editor's saveTask
      await paneOf().saveTask();
      expect(posted).toHaveLength(1);
      expect(posted[0].itemsIdentified).toBe(true);
      expect(posted[0].actions.map(a => [a.label, a.originalIndex])).toEqual([["B", 1]]);

      // the pane gets the save response, so the next save sends B's new stored position
      harness.detectChanges();
      await harness.fixture.whenStable();
      expect(paneOf().model.actions.map(a => [a.label, a.originalIndex])).toEqual([["B", 0]]);
      await paneOf().saveTask();
      expect(posted[1].actions.map(a => [a.label, a.originalIndex])).toEqual([["B", 0]]);
   });
});
