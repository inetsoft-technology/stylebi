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
 * Shared test helpers for ScheduleTaskListComponent multi-pass TL specs.
 * Consumed by:
 *   schedule-task-list.component.interaction.tl.spec.ts
 *   schedule-task-list.component.risk.tl.spec.ts
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { ActivatedRoute, Router, convertToParamMap } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { render } from "@testing-library/angular";
import { BehaviorSubject, EMPTY, Subject } from "rxjs";
import { vi } from "vitest";

import { ScheduleTaskListComponent } from "./schedule-task-list.component";
import { ScheduleTaskModel } from "../../../../../../shared/schedule/model/schedule-task-model";
import { ScheduleTaskList } from "../../../../../../shared/schedule/model/schedule-task-list";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { AssetEntry } from "../../../../../../shared/data/asset-entry";
import { AssetType } from "../../../../../../shared/data/asset-type";
import { FixedDropdownService } from "../../../widget/fixed-dropdown/fixed-dropdown.service";
import { DragService } from "../../../widget/services/drag.service";
import { DomService } from "../../../widget/dom-service/dom.service";
import { ScheduleUsersService } from "../../../../../../shared/schedule/schedule-users.service";
import { StompClientService } from "../../../common/viewsheet-client";

// ---------------------------------------------------------------------------
// Factory functions
// ---------------------------------------------------------------------------

export function makeTask(overrides: Partial<ScheduleTaskModel> = {}): ScheduleTaskModel {
   return {
      name: "Task1",
      label: "Task 1",
      description: "",
      owner: null,
      path: "/",
      status: null,
      schedule: "Daily",
      editable: true,
      removable: true,
      canDelete: true,
      enabled: true,
      ...overrides,
   };
}

export function makeTaskList(
   tasks: ScheduleTaskModel[],
   overrides: Partial<ScheduleTaskList> = {}
): ScheduleTaskList {
   return {
      tasks,
      timeZone: "UTC",
      timeZoneId: "UTC",
      timeZoneOffset: 0,
      dateTimeFormat: "yyyy-MM-dd HH:mm:ss",
      showOwners: false,
      ...overrides,
   };
}

export function makeAssetEntry(path: string, overrides: Partial<AssetEntry> = {}): AssetEntry {
   return {
      scope: 0,
      type: AssetType.SCHEDULE_TASK_FOLDER,
      user: null,
      path,
      alias: null,
      identifier: path,
      properties: {},
      organization: "host_org",
      ...overrides,
   };
}

export function makeTreeNode(
   label: string,
   path: string,
   overrides: Partial<TreeNodeModel> = {}
): TreeNodeModel {
   return {
      label,
      data: makeAssetEntry(path),
      children: [],
      leaf: false,
      expanded: false,
      ...overrides,
   };
}

// ---------------------------------------------------------------------------
// Shared mocks
// ---------------------------------------------------------------------------

export const queryParamMapSubject = new BehaviorSubject(convertToParamMap({}));

export const ROUTER_MOCK = {
   navigate: vi.fn(),
};

export const MODAL_MOCK = {
   open: vi.fn().mockImplementation(() => ({
      result: new Promise<any>(() => {}),
      componentInstance: { onCommit: new Subject<string>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   })),
};

export const DROPDOWN_SERVICE_MOCK = {
   open: vi.fn().mockReturnValue({
      componentInstance: { actions: [], sourceEvent: null },
   }),
};

export const DRAG_SERVICE_MOCK = {
   put: vi.fn(),
   getDragData: vi.fn().mockReturnValue({}),
};

export const DOM_SERVICE_MOCK = {
   requestRead: vi.fn().mockImplementation((fn: () => void) => fn()),
   requestWrite: vi.fn().mockImplementation((fn: () => void) => fn()),
};

/** Prevents ScheduleChangeService from opening a real WebSocket connection. */
export const STOMP_CLIENT_MOCK = {
   connect: vi.fn().mockReturnValue(EMPTY),
};

export const USERS_SERVICE_MOCK = {
   getOwners:        vi.fn().mockReturnValue(EMPTY),
   getGroups:        vi.fn().mockReturnValue(EMPTY),
   getEmailGroups:   vi.fn().mockReturnValue(EMPTY),
   getEmailUsers:    vi.fn().mockReturnValue(EMPTY),
   getAdminName:     vi.fn().mockReturnValue(EMPTY),
   getSSOEnable:     vi.fn().mockReturnValue(EMPTY),
   loadScheduleUsers: vi.fn(),
};

/** Reset all shared mocks to clean state between tests. */
export function resetMocks(): void {
   localStorage.clear();
   queryParamMapSubject.next(convertToParamMap({}));
   ROUTER_MOCK.navigate.mockClear();
   MODAL_MOCK.open.mockClear();
   MODAL_MOCK.open.mockImplementation(() => ({
      result: new Promise<any>(() => {}),
      componentInstance: { onCommit: new Subject<string>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   }));
   DROPDOWN_SERVICE_MOCK.open.mockClear();
   DRAG_SERVICE_MOCK.put.mockClear();
   DRAG_SERVICE_MOCK.getDragData.mockReturnValue({});
}

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

/**
 * Tracks the most recently rendered fixture so spec afterEach hooks can
 * destroy it before MSW resets its handlers.  Destroying the fixture calls
 * ngOnDestroy → subscriptions.unsubscribe(), which cancels any in-flight HTTP
 * requests and prevents their error callbacks from firing as uncaught
 * exceptions in the test runner.
 */
export let lastRenderedFixture: { destroy(): void } | null = null;

/**
 * Renders ScheduleTaskListComponent with all dependencies mocked.
 *
 * Notes:
 * - ScheduleChangeService is a component-level provider; StompClientService is mocked so
 *   no WebSocket connection is attempted.
 * - Default MSW handlers (portal.handlers.ts + em.handlers.ts) cover all ngOnInit HTTP calls:
 *   GET get-enable-security → { enable: false }
 *   GET change-show-type    → true (showTasksAsList=true)
 *   GET checkRootPermission → true
 *   POST scheduledTasks     → empty list
 */
export async function renderScheduleTaskList() {
   const result = await render(ScheduleTaskListComponent, {
      providers: [
         provideHttpClient(),
         { provide: Router, useValue: ROUTER_MOCK },
         { provide: ActivatedRoute, useValue: { queryParamMap: queryParamMapSubject.asObservable() } },
         { provide: NgbModal, useValue: MODAL_MOCK },
         { provide: FixedDropdownService, useValue: DROPDOWN_SERVICE_MOCK },
         { provide: DragService, useValue: DRAG_SERVICE_MOCK },
         { provide: DomService, useValue: DOM_SERVICE_MOCK },
         { provide: StompClientService, useValue: STOMP_CLIENT_MOCK },
         { provide: ScheduleUsersService, useValue: USERS_SERVICE_MOCK },
      ],
      schemas: [NO_ERRORS_SCHEMA],
   });

   // Wait for all ngOnInit HTTP calls to complete so their subscriptions are
   // done (success) before MSW's afterEach resets handlers.  Without this, the
   // subscriptions remain in-flight when MSW resets, causing request failures
   // that propagate as uncaught exceptions in the test runner.
   await result.fixture.whenStable();

   lastRenderedFixture = result.fixture;
   const comp = result.fixture.componentInstance as ScheduleTaskListComponent;
   return { comp, fixture: result.fixture };
}
