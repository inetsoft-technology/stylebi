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
 * ScheduleTaskEditorComponent - unedited save keeps the condition's time zone (Bug #77511)
 *
 * End to end in the client: the real router matches the editor route, the real editor renders the
 * real TaskConditionPane with the real TimeZoneService, MSW serves the server's dialog model (with
 * TimeZoneModel's fixed option list, which doesn't contain the stored zone) and captures the body
 * of the save request posted when the user clicks Save without editing anything.
 */

import { provideHttpClient } from "@angular/common/http";
import { TestBed } from "@angular/core/testing";
import { provideRouter } from "@angular/router";
import { RouterTestingHarness } from "@angular/router/testing";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { waitFor } from "@testing-library/angular";
import { http, HttpResponse } from "msw";
import { of } from "rxjs";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { server } from "@test-mocks/server";
import { ScheduleTaskDialogModel } from "../../../../../../shared/schedule/model/schedule-task-dialog-model";
import { ScheduleTaskEditorModel } from "../../../../../../shared/schedule/model/schedule-task-editor-model";
import { TimeConditionModel, TimeConditionType } from "../../../../../../shared/schedule/model/time-condition-model";
import { ScheduleTaskNamesService } from "../../../../../../shared/schedule/schedule-task-names.service";
import { ScheduleUsersService } from "../../../../../../shared/schedule/schedule-users.service";
import { ScheduleTaskEditorComponent } from "./schedule-task-editor.component";
import {
   makeScheduleTaskDialogModel,
   makeTaskConditionPaneModel,
} from "./schedule-task-editor.component.test-helpers";

const TZ_LS_KEY = "__inetsoft__inetsoft_conditionServerTimeZone";

// TimeZoneModel.getTimeZoneOptions(): the server zone first, a fixed list without most IANA ids
const serverOptions = () => [
   { timeZoneId: "Etc/UTC", label: "Coordinated Universal Time (Server)", hourOffset: "(UTC+00:00)", minuteOffset: 0 },
   { timeZoneId: "US/Pacific", label: "Pacific Time", hourOffset: "(UTC-08:00)", minuteOffset: -480 },
   { timeZoneId: "US/Eastern", label: "Eastern Time", hourOffset: "(UTC-05:00)", minuteOffset: -300 },
];

function dailyCondition(timeZone: string, timeZoneLabel: string = null): TimeConditionModel {
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
      timeZone,
      timeZoneLabel,
   };
}

function dialogModel(condition: TimeConditionModel, taskDefaultTime = false): ScheduleTaskDialogModel {
   return makeScheduleTaskDialogModel({
      taskDefaultTime,
      name: "admin~;~host-org:TZTask",
      label: "TZTask",
      timeZone: "Etc/UTC",
      timeZoneOptions: serverOptions(),
      taskConditionPaneModel: makeTaskConditionPaneModel({
         conditions: [condition],
         serverTimeZoneId: "Etc/UTC",
         timeZoneOffset: 0,
         // TaskConditionPaneModel.java has no taskDefaultTime, the editor sets it
         taskDefaultTime: undefined,
      }),
   });
}

interface Opened {
   comp: ScheduleTaskEditorComponent;
   posted: () => ScheduleTaskEditorModel;
   root: HTMLElement;
}

async function openEditor(condition: TimeConditionModel, url: string,
                          taskDefaultTime = false): Promise<Opened>
{
   let posted: ScheduleTaskEditorModel = null;
   const model = dialogModel(condition, taskDefaultTime);

   server.use(
      http.get("*/api/portal/schedule/edit", () => HttpResponse.json(model)),
      http.post("*/api/portal/schedule/save", async ({ request }) => {
         posted = await request.json() as ScheduleTaskEditorModel;
         return HttpResponse.json(dialogModel(posted.conditions[0] as TimeConditionModel));
      }),
   );

   // the old code read window.location, so keep it the same as the router URL
   window.history.replaceState(null, "", "/app" + url);

   TestBed.configureTestingModule({
      providers: [
         provideHttpClient(),
         provideRouter([
            { path: "portal/tab/schedule/tasks/:task", component: ScheduleTaskEditorComponent },
            { path: "portal/tab/schedule/tasks", children: [] },
         ]),
         { provide: NgbModal, useValue: { open: vi.fn() } },
         { provide: ScheduleUsersService, useValue: {} },
         {
            provide: ScheduleTaskNamesService,
            useValue: { loadScheduleTaskNames: vi.fn(), getAllTasks: () => of([]), isLoading: false },
         },
      ],
   });

   const harness = await RouterTestingHarness.create();
   const comp = await harness.navigateByUrl(url, ScheduleTaskEditorComponent);
   await waitFor(() => expect(comp.model).toBeDefined());
   harness.detectChanges();
   await harness.fixture.whenStable();
   harness.detectChanges();

   return { comp, posted: () => posted, root: harness.routeNativeElement };
}

function shownCondition(opened: Opened): TimeConditionModel {
   return opened.comp.model.taskConditionPaneModel.conditions[0] as TimeConditionModel;
}

async function saveUnedited(opened: Opened): Promise<TimeConditionModel> {
   const button = opened.root.querySelector<HTMLButtonElement>("task-condition-pane .save_button_id");
   expect(button).not.toBeNull();
   expect(button.disabled).toBe(false);
   button.click();
   await waitFor(() => expect(opened.posted()).not.toBeNull());
   return opened.posted().conditions[0] as TimeConditionModel;
}

const DEEP_LINK = "/portal/tab/schedule/tasks/TZTask";
const FROM_LIST = "/portal/tab/schedule/tasks/TZTask?taskDefaultTime=false&path=%2F&newTask=false";

describe("ScheduleTaskEditorComponent - unedited save keeps the time zone (Bug #77511)", () => {
   beforeEach(() => localStorage.clear());

   afterEach(() => {
      localStorage.clear();
      window.history.replaceState(null, "", "/");
      vi.restoreAllMocks();
   });

   for(const serverDisplay of [false, true]) {
      const mode = serverDisplay ? "server" : "local";

      for(const [how, url] of [["a deep link without a query", DEEP_LINK], ["the task list (newTask=false)", FROM_LIST]]) {
         it(`posts America/New_York for a daily 01:30 condition opened from ${how}, ${mode} display`, async () => {
            if(serverDisplay) {
               localStorage.setItem(TZ_LS_KEY, "true");
            }

            const opened = await openEditor(dailyCondition("America/New_York"), url);
            // opening the task must not change the condition (the old code wrote Etc/UTC here)
            expect(shownCondition(opened).timeZone).toBe("America/New_York");
            const saved = await saveUnedited(opened);

            expect(saved.timeZone).toBe("America/New_York");
            expect(saved.timeZoneLabel).toBeNull();
            expect([saved.hour, saved.minute]).toEqual([1, 30]);
         });
      }

      it(`posts a listed zone (US/Eastern) and its label unchanged from a deep link, ${mode} display`, async () => {
         if(serverDisplay) {
            localStorage.setItem(TZ_LS_KEY, "true");
         }

         const opened = await openEditor(dailyCondition("US/Eastern", "Eastern Time"), DEEP_LINK);
         const saved = await saveUnedited(opened);

         expect([saved.timeZone, saved.timeZoneLabel]).toEqual(["US/Eastern", "Eastern Time"]);
         expect([saved.hour, saved.minute]).toEqual([1, 30]);
      });
   }

   it("posts the zone the user explicitly picks", async () => {
      const opened = await openEditor(dailyCondition("America/New_York"), DEEP_LINK);
      const select = opened.root.querySelector<HTMLSelectElement>(
         "task-condition-pane select[formControlName='timeZone']");
      expect(select).not.toBeNull();
      const option = Array.from(select.options).find(o => o.value.endsWith("US/Pacific"));
      expect(option).toBeDefined();
      select.value = option.value;
      select.dispatchEvent(new Event("change"));

      const saved = await saveUnedited(opened);

      expect([saved.timeZone, saved.timeZoneLabel]).toEqual(["US/Pacific", "Pacific Time"]);
   });

   it("keeps the default zone of a new task (null zone filled from the first option, the browser zone)", async () => {
      const browserZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
      const opened = await openEditor(dailyCondition(null),
         "/portal/tab/schedule/tasks/TZTask?taskDefaultTime=true&path=%2F&newTask=true", true);
      const saved = await saveUnedited(opened);

      expect(opened.comp.newTask).toBe(true);
      expect(saved.timeZone).toBe(browserZone);
   });
});

describe("ScheduleTaskEditorComponent - save sends the item identity (Bug #77973)", () => {
   afterEach(() => {
      localStorage.clear();
      window.history.replaceState(null, "", "/");
      vi.restoreAllMocks();
   });

   it("posts the marker and the original index the condition was loaded with", async () => {
      const opened = await openEditor(
         { ...dailyCondition("America/New_York"), originalIndex: 0 }, DEEP_LINK);
      const saved = await saveUnedited(opened);

      expect(opened.posted().itemsIdentified).toBe(true);
      expect(saved.originalIndex).toBe(0);
   });
});
