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

import { HttpClient, provideHttpClient } from "@angular/common/http";
import { TestBed } from "@angular/core/testing";
import { FormBuilder } from "@angular/forms";
import { ActivatedRoute, convertToParamMap, ParamMap, Router } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { BehaviorSubject } from "rxjs";

import { IdentityType } from "../../../../../../shared/data/identity-type";
import { ScheduleConditionModel } from "../../../../../../shared/schedule/model/schedule-condition-model";
import { ScheduleTaskDialogModel } from "../../../../../../shared/schedule/model/schedule-task-dialog-model";
import { TaskActionPaneModel } from "../../../../../../shared/schedule/model/task-action-pane-model";
import { TaskConditionPaneModel } from "../../../../../../shared/schedule/model/task-condition-pane-model";
import { TaskOptionsPaneModel } from "../../../../../../shared/schedule/model/task-options-pane-model";
import { TimeZoneModel } from "../../../../../../shared/schedule/model/time-zone-model";
import { ScheduleTaskNamesService } from "../../../../../../shared/schedule/schedule-task-names.service";
import { ScheduleUsersService } from "../../../../../../shared/schedule/schedule-users.service";
import { TimeZoneService } from "../../../../../../shared/schedule/time-zone.service";
import { ScheduleTaskEditorComponent } from "./schedule-task-editor.component";

interface ScheduleTaskEditorNotifications {
   success: ReturnType<typeof vi.fn>;
   warning: ReturnType<typeof vi.fn>;
}

export interface ScheduleTaskEditorModalStub {
   open: ReturnType<typeof vi.fn>;
}

export interface ScheduleTaskEditorRouterStub {
   navigate: ReturnType<typeof vi.fn>;
}

export interface ScheduleTaskEditorRouteStub {
   paramMap$: BehaviorSubject<ParamMap>;
   paramMap: ReturnType<BehaviorSubject<ParamMap>["asObservable"]>;
}

export interface ScheduleTaskEditorTimeZoneServiceStub {
   updateTimeZoneOptions: ReturnType<typeof vi.fn>;
}

export interface ScheduleTaskEditorNamesServiceStub {
   loadScheduleTaskNames: ReturnType<typeof vi.fn>;
}

export function makeScheduleConditionModel(
   overrides: Partial<ScheduleConditionModel> = {},
): ScheduleConditionModel {
   return {
      label: "Daily",
      conditionType: "TimeCondition",
      ...overrides,
   };
}

export function makeTaskConditionPaneModel(
   overrides: Partial<TaskConditionPaneModel> = {},
): TaskConditionPaneModel {
   return {
      conditions: [makeScheduleConditionModel()],
      timeProp: "runAt",
      twelveHourSystem: false,
      userDefinedClasses: [],
      userDefinedClassLabels: [],
      taskDefaultTime: false,
      ...overrides,
   };
}

export function makeTaskActionPaneModel(
   overrides: Partial<TaskActionPaneModel> = {},
): TaskActionPaneModel {
   return {
      securityEnabled: true,
      emailButtonVisible: true,
      endUser: "user",
      administrator: true,
      defaultFromEmail: "noreply@example.com",
      fromEmailEnabled: true,
      viewsheetEnabled: true,
      notificationEmailEnabled: true,
      saveToDiskEnabled: true,
      emailDeliveryEnabled: true,
      cvsEnabled: true,
      actions: [],
      userDefinedClasses: [],
      userDefinedClassLabels: [],
      dashboardMap: {},
      printers: [],
      folderPaths: [],
      folderLabels: [],
      mailFormats: [],
      vsMailFormats: [],
      saveFileFormats: [],
      vsSaveFileFormats: [],
      expandEnabled: true,
      ...overrides,
   };
}

export function makeTaskOptionsPaneModel(
   overrides: Partial<TaskOptionsPaneModel> = {},
): TaskOptionsPaneModel {
   return {
      enabled: true,
      deleteIfNotScheduledToRun: false,
      startFrom: 0,
      stopOn: 0,
      locale: null,
      locales: ["en_US"],
      owner: "owner",
      description: "desc",
      securityEnabled: true,
      idName: null,
      idType: IdentityType.USER,
      idAlias: null,
      ownerAlias: "Owner",
      organizationName: "Org",
      selfOrg: false,
      timeZone: "UTC",
      ...overrides,
   };
}

export function makeTimeZoneModel(
   overrides: Partial<TimeZoneModel> = {},
): TimeZoneModel {
   return {
      timeZoneId: "UTC",
      label: "UTC",
      hourOffset: "",
      minuteOffset: 0,
      ...overrides,
   };
}

export function makeScheduleTaskDialogModel(
   overrides: Partial<ScheduleTaskDialogModel> = {},
): ScheduleTaskDialogModel {
   return {
      name: "owner:Nightly",
      label: "Nightly",
      taskDefaultTime: false,
      timeZone: "UTC",
      timeZoneOptions: [makeTimeZoneModel()],
      taskActionPaneModel: makeTaskActionPaneModel(),
      taskConditionPaneModel: makeTaskConditionPaneModel(),
      taskOptionsPaneModel: makeTaskOptionsPaneModel(),
      internalTask: false,
      timeRanges: [],
      startTimeEnabled: true,
      timeRangeEnabled: true,
      ...overrides,
   };
}

export function createScheduleTaskEditorModalStub(): ScheduleTaskEditorModalStub {
   return {
      open: vi.fn(),
   };
}

export function createScheduleTaskEditorRouterStub(): ScheduleTaskEditorRouterStub {
   return {
      navigate: vi.fn().mockResolvedValue(true),
   };
}

export function createScheduleTaskEditorRouteStub(taskName = "Nightly"): ScheduleTaskEditorRouteStub {
   const paramMap$ = new BehaviorSubject<ParamMap>(convertToParamMap({ task: taskName }));

   return {
      paramMap$,
      paramMap: paramMap$.asObservable(),
   };
}

export function createScheduleTaskEditorTimeZoneServiceStub(): ScheduleTaskEditorTimeZoneServiceStub {
   return {
      updateTimeZoneOptions: vi.fn((timeZoneOptions: TimeZoneModel[]) => timeZoneOptions),
   };
}

export function createScheduleTaskEditorNamesServiceStub(): ScheduleTaskEditorNamesServiceStub {
   return {
      loadScheduleTaskNames: vi.fn(),
   };
}

export function createScheduleTaskEditor(options: {
   route?: ScheduleTaskEditorRouteStub;
   router?: ScheduleTaskEditorRouterStub;
   modalService?: ScheduleTaskEditorModalStub;
   timeZoneService?: ScheduleTaskEditorTimeZoneServiceStub;
   namesService?: ScheduleTaskEditorNamesServiceStub;
} = {}) {
   const route = options.route ?? createScheduleTaskEditorRouteStub();
   const router = options.router ?? createScheduleTaskEditorRouterStub();
   const modalService = options.modalService ?? createScheduleTaskEditorModalStub();
   const timeZoneService = options.timeZoneService ?? createScheduleTaskEditorTimeZoneServiceStub();
   const namesService = options.namesService ?? createScheduleTaskEditorNamesServiceStub();

   TestBed.resetTestingModule();
   TestBed.configureTestingModule({
      providers: [
         provideHttpClient(),
         FormBuilder,
         { provide: Router, useValue: router },
         { provide: ActivatedRoute, useValue: route },
         { provide: NgbModal, useValue: modalService },
         { provide: ScheduleUsersService, useValue: {} },
         { provide: TimeZoneService, useValue: timeZoneService },
         { provide: ScheduleTaskNamesService, useValue: namesService },
      ],
   });

   const comp = new ScheduleTaskEditorComponent(
      TestBed.inject(HttpClient),
      TestBed.inject(Router),
      TestBed.inject(ActivatedRoute),
      TestBed.inject(NgbModal),
      TestBed.inject(FormBuilder),
      TestBed.inject(ScheduleUsersService),
      TestBed.inject(TimeZoneService),
      TestBed.inject(ScheduleTaskNamesService),
   );

   return {
      comp,
      http: TestBed.inject(HttpClient),
      route,
      router,
      modalService,
      timeZoneService,
      namesService,
   };
}

export function attachScheduleTaskNotifications(
   comp: ScheduleTaskEditorComponent,
): ScheduleTaskEditorNotifications {
   const notifications = {
      success: vi.fn(),
      warning: vi.fn(),
   };

   // Tests inject the minimal @ViewChild surface directly because saveSuccess and updateOldTaskName
   // call notifications methods without a template-backed child in direct-instantiation tests.
   (comp as unknown as { notifications: ScheduleTaskEditorNotifications }).notifications = notifications;
   return notifications;
}
