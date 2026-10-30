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
 * ScheduleTaskEditorComponent - unsaved-change detection after a save (Bug #79750)
 *
 * End to end in the client: the real router with the real scheduleSaveGuard on the editor route,
 * the real editor and the real Condition, Action and Options panes, MSW for the edit and save
 * requests. The guard's confirm dialog is spied to count the "Task Changed" prompts.
 *
 * All three tabs save through the one saveTask() and every way out (Close, breadcrumb, tab
 * switch) goes through the one guard, so each scenario runs once per tab.
 */

import { provideHttpClient } from "@angular/common/http";
import { TestBed } from "@angular/core/testing";
import { By } from "@angular/platform-browser";
import { provideRouter } from "@angular/router";
import { RouterTestingHarness } from "@angular/router/testing";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { waitFor } from "@testing-library/angular";
import { http, HttpResponse } from "msw";
import { of } from "rxjs";
import { afterEach, describe, expect, it, vi } from "vitest";

import { server } from "@test-mocks/server";
import { ScheduleTaskDialogModel } from "../../../../../../shared/schedule/model/schedule-task-dialog-model";
import { ScheduleTaskEditorModel } from "../../../../../../shared/schedule/model/schedule-task-editor-model";
import { TimeConditionModel, TimeConditionType } from "../../../../../../shared/schedule/model/time-condition-model";
import { ScheduleTaskNamesService } from "../../../../../../shared/schedule/schedule-task-names.service";
import { ScheduleUsersService } from "../../../../../../shared/schedule/schedule-users.service";
import { ComponentTool } from "../../../common/util/component-tool";
import { PortalModelService } from "../../services/portal-model.service";
import { scheduleSaveGuard } from "../schedule-save.guard";
import { TaskActionPane } from "./actions/task-action-pane.component";
import { PORTAL_MODEL_MOCK } from "./actions/task-action-pane.test-helpers";
import { TaskConditionPane } from "./conditions/task-condition-pane.component";
import { TaskOptionsPane } from "./options/task-options-pane.component";
import { ScheduleTaskEditorComponent } from "./schedule-task-editor.component";
import {
   makeScheduleTaskDialogModel,
   makeTaskActionPaneModel,
   makeTaskConditionPaneModel,
} from "./schedule-task-editor.component.test-helpers";

const URL = "/portal/tab/schedule/tasks/T";
const LIST = "/portal/tab/schedule/tasks";

type Tab = "condition" | "action" | "options";
const TABS: Tab[] = ["condition", "action", "options"];

function dailyCondition(): TimeConditionModel {
   return {
      conditionType: "TimeCondition",
      label: "Daily 01:30",
      type: TimeConditionType.EVERY_DAY,
      hour: 1, minute: 30, second: 0,
      hourEnd: 2, minuteEnd: 30, secondEnd: 0,
      interval: 1,
      weekdayOnly: false,
      daysOfWeek: [],
      monthsOfYear: [],
      date: 0,
      timeZoneOffset: 0,
      timeZone: "Etc/UTC",
      timeZoneLabel: null,
   };
}

function taskModel(label = "T"): ScheduleTaskDialogModel {
   return makeScheduleTaskDialogModel({
      name: "admin~;~host-org:T",
      label,
      taskConditionPaneModel: makeTaskConditionPaneModel({
         conditions: [dailyCondition()],
         taskDefaultTime: undefined,
      }),
      taskActionPaneModel: makeTaskActionPaneModel({
         actions: [{ actionType: "BackupAction", label: "Backup" }],
      }),
   });
}

interface Opened {
   comp: ScheduleTaskEditorComponent;
   harness: RouterTestingHarness;
   prompt: ReturnType<typeof vi.spyOn>;
}

// serverLabel: the label the save response carries when it differs from the posted one
async function openEditor(serverLabel: string = null): Promise<Opened> {
   server.use(
      http.get("*/api/portal/schedule/edit", () => HttpResponse.json(taskModel())),
      http.post("*/api/portal/schedule/save", async ({ request }) => {
         const posted = await request.json() as ScheduleTaskEditorModel;
         const response = taskModel(serverLabel ?? posted.taskName);
         response.taskConditionPaneModel.conditions = posted.conditions as any;
         response.taskActionPaneModel.actions = posted.actions;
         response.taskOptionsPaneModel = posted.options;
         return HttpResponse.json(response);
      }),
   );

   window.history.replaceState(null, "", "/app" + URL);
   // "cancel" keeps the router on the editor, a prompt is counted either way
   const prompt = vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("cancel");

   TestBed.configureTestingModule({
      providers: [
         provideHttpClient(),
         provideRouter([
            {
               path: "portal/tab/schedule/tasks/:task",
               component: ScheduleTaskEditorComponent,
               canDeactivate: [scheduleSaveGuard]
            },
            { path: "portal/tab/schedule/tasks", children: [] },
         ]),
         { provide: NgbModal, useValue: { open: vi.fn() } },
         { provide: PortalModelService, useValue: PORTAL_MODEL_MOCK },
         {
            provide: ScheduleUsersService,
            useValue: {
               getEmailUsers: () => of([]), getEmailGroups: () => of([]),
               getOwners: () => of([]), getGroups: () => of([]), getAdminName: () => of("admin"),
               isLoading: false,
            },
         },
         {
            provide: ScheduleTaskNamesService,
            useValue: { loadScheduleTaskNames: vi.fn(), getAllTasks: () => of([]), isLoading: false },
         },
      ],
   });

   const harness = await RouterTestingHarness.create();
   const comp = await harness.navigateByUrl(URL, ScheduleTaskEditorComponent);
   await waitFor(() => expect(comp.model).toBeDefined());
   await settle(harness);
   return { comp, harness, prompt };
}

async function settle(harness: RouterTestingHarness): Promise<void> {
   harness.detectChanges();
   await harness.fixture.whenStable();
   harness.detectChanges();
}

async function showTab(opened: Opened, tab: Tab): Promise<void> {
   opened.comp.selectedTab = tab;
   await settle(opened.harness);
}

// the pane's own save(), the method its Save button calls
async function saveFrom(opened: Opened, tab: Tab): Promise<void> {
   const type = tab === "condition" ? TaskConditionPane
      : tab === "action" ? TaskActionPane : TaskOptionsPane;
   const pane = opened.harness.fixture.debugElement.query(By.directive(type));
   expect(pane).not.toBeNull();
   const before = opened.comp.model;
   (pane.componentInstance as any).save(true);
   await waitFor(() => expect(opened.comp.model).not.toBe(before));
   // the pane's post-save handlers (updateTaskName) run after the response was applied
   await settle(opened.harness);
}

// the panes edit the editor's model in place
function edit(comp: ScheduleTaskEditorComponent, tab: Tab): void {
   if(tab === "condition") {
      comp.model.taskConditionPaneModel.conditions[0].label = "Edited condition";
   }
   else if(tab === "action") {
      comp.model.taskActionPaneModel.actions[0].label = "Edited action";
   }
   else {
      comp.model.taskOptionsPaneModel.description = "Edited description";
   }
}

async function leave(opened: Opened): Promise<number> {
   opened.prompt.mockClear();
   await opened.harness.navigateByUrl(LIST);
   return opened.prompt.mock.calls
      .filter(c => c[2] === "_#(js:portal.schedule.unsave.confirm)").length;
}

describe("ScheduleTaskEditorComponent - unsaved changes after a save (Bug #79750)", () => {
   afterEach(() => {
      window.history.replaceState(null, "", "/");
      vi.restoreAllMocks();
   });

   for(const tab of TABS) {
      describe(`saving from the ${tab} tab`, () => {
         it("keeps the saved baseline independent of the live model", async () => {
            const opened = await openEditor();
            await showTab(opened, tab);
            await saveFrom(opened, tab);

            expect(opened.comp.originalModel).not.toBe(opened.comp.model);
            expect(opened.comp.originalModel.taskConditionPaneModel)
               .not.toBe(opened.comp.model.taskConditionPaneModel);
            expect(opened.comp.originalModel.taskActionPaneModel)
               .not.toBe(opened.comp.model.taskActionPaneModel);
            expect(opened.comp.originalModel.taskOptionsPaneModel)
               .not.toBe(opened.comp.model.taskOptionsPaneModel);
         });

         it("does not prompt when leaving right after the save", async () => {
            const opened = await openEditor();
            await showTab(opened, tab);
            await saveFrom(opened, tab);

            expect(await leave(opened)).toBe(0);
         });

         it("prompts when something is edited after the save", async () => {
            const opened = await openEditor();
            await showTab(opened, tab);
            await saveFrom(opened, tab);
            edit(opened.comp, tab);

            expect(await leave(opened)).toBe(1);
         });

         it("prompts for an edit on any tab after the save, not only the saved one", async () => {
            const opened = await openEditor();
            await showTab(opened, tab);
            await saveFrom(opened, tab);

            for(const other of TABS) {
               edit(opened.comp, other);
            }

            expect(await leave(opened)).toBe(1);
         });

         it("does not prompt falsely when the response label differs from the posted one",
            async () =>
         {
            const opened = await openEditor("T2");
            await showTab(opened, tab);
            await saveFrom(opened, tab);

            expect(await leave(opened)).toBe(0);
         });
      });
   }

   it("prompts for an edit made before the first save, as before", async () => {
      const opened = await openEditor();
      edit(opened.comp, "options");

      expect(await leave(opened)).toBe(1);
   });

   it("prompts for an edit made after a second save", async () => {
      const opened = await openEditor();
      await showTab(opened, "condition");
      edit(opened.comp, "condition");
      await saveFrom(opened, "condition");
      edit(opened.comp, "options");
      await saveFrom(opened, "condition");
      edit(opened.comp, "action");
      expect(await leave(opened)).toBe(1);
   });
});
