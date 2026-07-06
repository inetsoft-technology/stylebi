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
 * Shared test helpers for TaskConditionPane multi-pass TL specs.
 * Consumed by:
 *   task-condition-pane.component.interaction.tl.spec.ts
 *   task-condition-pane.component.risk.tl.spec.ts
 *   task-condition-pane.component.display.tl.spec.ts
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { UntypedFormGroup } from "@angular/forms";
import { provideHttpClient } from "@angular/common/http";
import { render } from "@testing-library/angular";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { BehaviorSubject, Subject } from "rxjs";
import { vi } from "vitest";
import { TaskConditionPane } from "./task-condition-pane.component";
import { TaskConditionPaneModel } from "../../../../../../../shared/schedule/model/task-condition-pane-model";
import {
   TimeConditionModel,
   TimeConditionType,
   TimeRange,
} from "../../../../../../../shared/schedule/model/time-condition-model";
import { CompletionConditionModel } from "../../../../../../../shared/schedule/model/completion-condition-model";
import { ScheduleTaskNamesService } from "../../../../../../../shared/schedule/schedule-task-names.service";
import { TimeZoneService } from "../../../../../../../shared/schedule/time-zone.service";
import { TimeZoneModel } from "../../../../../../../shared/schedule/model/time-zone-model";

// ---------------------------------------------------------------------------
// Condition factories
// ---------------------------------------------------------------------------

export function makeDailyCondition(overrides: Partial<TimeConditionModel> = {}): TimeConditionModel {
   return {
      conditionType: "TimeCondition",
      label: "Daily Condition",
      type: TimeConditionType.EVERY_DAY,
      hour: 1, minute: 30, second: 0,
      hourEnd: 2, minuteEnd: 30, secondEnd: 0,
      interval: 1,
      weekdayOnly: false,
      daysOfWeek: [],
      monthsOfYear: [],
      monthlyDaySelected: true,
      date: 0,
      timeZoneOffset: 0,
      timeZone: null,
      ...overrides,
   };
}

export function makeWeeklyCondition(overrides: Partial<TimeConditionModel> = {}): TimeConditionModel {
   return {
      conditionType: "TimeCondition",
      label: "Weekly Condition",
      type: TimeConditionType.EVERY_WEEK,
      hour: 1, minute: 30, second: 0,
      hourEnd: 2, minuteEnd: 30, secondEnd: 0,
      interval: 1,
      weekdayOnly: false,
      daysOfWeek: [1],
      monthsOfYear: [],
      monthlyDaySelected: true,
      date: 0,
      timeZoneOffset: 0,
      timeZone: null,
      ...overrides,
   };
}

export function makeMonthlyCondition(overrides: Partial<TimeConditionModel> = {}): TimeConditionModel {
   return {
      conditionType: "TimeCondition",
      label: "Monthly Condition",
      type: TimeConditionType.EVERY_MONTH,
      hour: 1, minute: 30, second: 0,
      hourEnd: 2, minuteEnd: 30, secondEnd: 0,
      weekdayOnly: false,
      daysOfWeek: [],
      monthsOfYear: [0],
      monthlyDaySelected: true,
      dayOfMonth: 1,
      interval: 1,
      date: 0,
      timeZoneOffset: 0,
      timeZone: null,
      ...overrides,
   };
}

export function makeHourlyCondition(overrides: Partial<TimeConditionModel> = {}): TimeConditionModel {
   return {
      conditionType: "TimeCondition",
      label: "Hourly Condition",
      type: TimeConditionType.EVERY_HOUR,
      hour: 1, minute: 0, second: 0,
      hourEnd: 2, minuteEnd: 0, secondEnd: 0,
      hourlyInterval: 1,
      weekdayOnly: false,
      daysOfWeek: [1],
      monthsOfYear: [],
      monthlyDaySelected: true,
      date: 0,
      timeZoneOffset: 0,
      timeZone: null,
      ...overrides,
   };
}

export function makeRunOnceCondition(overrides: Partial<TimeConditionModel> = {}): TimeConditionModel {
   return {
      conditionType: "TimeCondition",
      label: "Run Once Condition",
      type: TimeConditionType.AT,
      hour: -1, minute: -1, second: -1,
      hourEnd: -1, minuteEnd: -1, secondEnd: -1,
      weekdayOnly: false,
      daysOfWeek: [],
      monthsOfYear: [],
      monthlyDaySelected: true,
      date: 1_700_000_000_000, // fixed reference timestamp — avoids non-determinism across runs
      timeZoneOffset: 0,
      timeZone: null,
      ...overrides,
   };
}

export function makeChainedCondition(overrides: Partial<CompletionConditionModel> = {}): CompletionConditionModel {
   return {
      conditionType: "CompletionCondition",
      label: "Chained Condition",
      taskName: null,
      ...overrides,
   };
}

export function makeModel(overrides: Partial<TaskConditionPaneModel> = {}): TaskConditionPaneModel {
   return {
      conditions: [makeDailyCondition()],
      timeProp: "",
      twelveHourSystem: false,
      userDefinedClasses: [],
      userDefinedClassLabels: [],
      taskDefaultTime: true,
      timeZoneOffset: 0,
      serverTimeZoneId: "UTC",
      ...overrides,
   };
}

// ---------------------------------------------------------------------------
// Shared timezone fixture
// ---------------------------------------------------------------------------

export const UTC_TZ_OPTION: TimeZoneModel = {
   timeZoneId: "UTC",
   label: "UTC (Local)",
   hourOffset: "+00:00",
   minuteOffset: 0,
};

// ---------------------------------------------------------------------------
// Shared mocks
// (callers must reset these in beforeEach — see resetMocks())
// ---------------------------------------------------------------------------

export const taskNamesSubject = new BehaviorSubject<any>(null);

export const taskNamesMock = {
   getAllTasks: vi.fn().mockReturnValue(taskNamesSubject.asObservable()),
   isLoading: false,
};

export const modalMock = {
   open: vi.fn().mockImplementation(() => ({
      result: new Promise<any>(() => {}),
      componentInstance: { onCommit: new Subject<string>(), onCancel: new Subject<void>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   })),
};

/** Call in beforeEach to return all shared mocks to a clean default state. */
export function resetMocks(): void {
   localStorage.clear();
   taskNamesSubject.next(null);
   taskNamesMock.getAllTasks.mockReturnValue(taskNamesSubject.asObservable());
   taskNamesMock.isLoading = false;
   modalMock.open.mockClear();
   modalMock.open.mockImplementation(() => ({
      result: new Promise<any>(() => {}),
      componentInstance: { onCommit: new Subject<string>(), onCancel: new Subject<void>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   }));
}

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

export interface RenderOpts {
   model?: TaskConditionPaneModel;
   taskName?: string;
   oldTaskName?: string;
   timeZoneOptions?: TimeZoneModel[];
   startTimeEnabled?: boolean;
   timeRangeEnabled?: boolean;
   timeRanges?: TimeRange[];
   saveTask?: () => Promise<any>;
}

export async function renderTaskConditionPane(opts: RenderOpts = {}) {
   const saveTask = opts.saveTask ?? vi.fn().mockResolvedValue(undefined);
   const { fixture } = await render(TaskConditionPane, {
      componentProperties: {
         model: opts.model ?? makeModel(),
         taskName: opts.taskName ?? "Task1",
         oldTaskName: opts.oldTaskName ?? "OldTask",
         timeZoneOptions: opts.timeZoneOptions ?? [UTC_TZ_OPTION],
         taskDefaultTimeProperty: true,
         startTimeEnabled: opts.startTimeEnabled ?? true,
         timeRangeEnabled: opts.timeRangeEnabled ?? true,
         timeRanges: opts.timeRanges ?? [],
         parentForm: new UntypedFormGroup({}),
         saveTask,
      },
      providers: [
         provideHttpClient(),
         { provide: NgbModal, useValue: modalMock },
         { provide: ScheduleTaskNamesService, useValue: taskNamesMock },
         TimeZoneService,
      ],
      schemas: [NO_ERRORS_SCHEMA],
   });
   const comp = fixture.componentInstance as TaskConditionPane;
   return { comp, fixture, saveTask };
}
