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
 * Shared test helpers for TaskActionPane multi-pass TL specs.
 * Consumed by:
 *   task-action-pane.component.interaction.tl.spec.ts
 *   task-action-pane.component.risk.tl.spec.ts  (future)
 *
 * Mocking strategy:
 *   NgbModal is mocked (MODAL_MOCK) so that ComponentTool dialogs can be controlled
 *   per-test with mockImplementationOnce.  PortalModelService is stubbed with its
 *   two public methods.  HttpClient is provided via provideHttpClient() — required
 *   for DI — but no HTTP calls fire when the model carries actions with no sheet.
 *   MSW is NOT imported here; the global vitest-setup-tl.ts starts the server.
 */

import { Component, EventEmitter, Input, NO_ERRORS_SCHEMA, Output } from "@angular/core";
import { UntypedFormGroup } from "@angular/forms";
import { provideHttpClient } from "@angular/common/http";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { render } from "@testing-library/angular";
import { Subject } from "rxjs";
import { vi } from "vitest";

import { TaskActionPane } from "./task-action-pane.component";
import { ActionAccordion } from "./action-accordian/action-accordion.component";
import { EditableTableComponent } from "../editable-table/editable-table.component";
import { NotificationsComponent } from "../../../../widget/notifications/notifications.component";
import { GeneralActionModel } from "../../../../../../../shared/schedule/model/general-action-model";
import { TaskActionPaneModel } from "../../../../../../../shared/schedule/model/task-action-pane-model";
import { PortalModelService } from "../../../services/portal-model.service";

// ---------------------------------------------------------------------------
// Import-override stubs
// ActionAccordion pulls in ScheduleUsersService and a large DI tree; stub it
// (and its siblings) so ATL does not need every transitive provider.
// ---------------------------------------------------------------------------

@Component({ selector: "action-accordion", template: "", standalone: true })
class ActionAccordionStub {
   @Input() action: any;
   @Input() model: any;
   @Input() printers: any;
   @Input() highlights: any;
   @Input() requiredParameters: any;
   @Input() parentForm: any;
   @Input() optionalParameters: any;
   @Input() bookmarks: any;
   @Input() bookmarkList: any;
   @Input() selectedBookmark: any;
   @Input() containsSheet: any;
   @Input() autoCompleteModel: any;
   @Input() executeAsGroup: any;
   @Input() hasPrintLayout: any;
   @Input() tableDataAssemblies: any;
}

@Component({ selector: "editable-table", template: "", standalone: true })
class EditableTableStub {
   @Input() title: any;
   @Input() items: any;
   @Input() selectedItems: any;
   @Output() selectedItemsChange = new EventEmitter<any>();
}

@Component({ selector: "notifications", template: "", standalone: true })
class NotificationsStub {
   @Input() timeout: any;
}

// ---------------------------------------------------------------------------
// Factory functions
// ---------------------------------------------------------------------------

export function makeAction(overrides: Partial<GeneralActionModel> = {}): GeneralActionModel {
   return {
      actionType: "ViewsheetAction",
      actionClass: "GeneralActionModel",
      label: "Test Action",
      notificationEnabled: false,
      deliverEmailsEnabled: false,
      printOnServerEnabled: false,
      saveToServerEnabled: false,
      ccAddress: "",
      bccAddress: "",
      ...overrides,
   };
}

export function makeModel(overrides: Partial<TaskActionPaneModel> = {}): TaskActionPaneModel {
   return {
      securityEnabled: true,
      emailButtonVisible: false,
      endUser: null,
      administrator: true,
      defaultFromEmail: "reportserver@inetsoft.com",
      fromEmailEnabled: true,
      viewsheetEnabled: true,
      notificationEmailEnabled: false,
      saveToDiskEnabled: true,
      emailDeliveryEnabled: true,
      cvsEnabled: false,
      actions: [makeAction()],
      userDefinedClasses: [],
      userDefinedClassLabels: [],
      dashboardMap: {},
      printers: [],
      folderPaths: [],
      folderLabels: [],
      mailFormats: [],
      vsMailFormats: [{ type: "PDF", label: "PDF" }],
      saveFileFormats: [],
      vsSaveFileFormats: [],
      expandEnabled: true,
      mailHistoryEnabled: false,
      ...overrides,
   };
}

// ---------------------------------------------------------------------------
// Shared mocks
// ---------------------------------------------------------------------------

export const MODAL_MOCK = {
   open: vi.fn().mockImplementation(() => ({
      result: new Promise<any>(() => {}),
      componentInstance: { onCommit: new Subject<string>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   })),
};

export const PORTAL_MODEL_MOCK = {
   isDashboardEnabled: vi.fn(() => true),
   isReportEnabled: vi.fn(() => true),
};

export function resetMocks(): void {
   localStorage.clear();
   MODAL_MOCK.open.mockClear();
   MODAL_MOCK.open.mockImplementation(() => ({
      result: new Promise<any>(() => {}),
      componentInstance: { onCommit: new Subject<string>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   }));
   PORTAL_MODEL_MOCK.isDashboardEnabled.mockClear();
   PORTAL_MODEL_MOCK.isReportEnabled.mockClear();
}

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

/**
 * Tracks the most recently rendered fixture so spec afterEach hooks can
 * destroy it before MSW resets handlers.
 */
export let lastRenderedFixture: { destroy(): void } | null = null;

/**
 * Renders TaskActionPane with all external dependencies mocked.
 *
 * - model: defaults to makeModel() — a single ViewsheetAction with no sheet,
 *   so ngOnInit fires no HTTP calls.
 * - parentForm: a fresh UntypedFormGroup({}) is created and returned so callers
 *   can manipulate it (add invalid controls, etc.) in isValid() tests.
 * - extraProperties: forwarded to componentProperties for one-off @Input overrides.
 */
export async function renderTaskActionPane(
   model = makeModel(),
   extraProperties: Record<string, any> = {}
) {
   const parentForm = new UntypedFormGroup({});
   const result = await render(TaskActionPane, {
      providers: [
         provideHttpClient(),
         { provide: NgbModal, useValue: MODAL_MOCK },
         { provide: PortalModelService, useValue: PORTAL_MODEL_MOCK },
      ],
      importOverrides: [
         { replace: ActionAccordion, with: ActionAccordionStub },
         { replace: EditableTableComponent, with: EditableTableStub },
         { replace: NotificationsComponent, with: NotificationsStub },
      ],
      componentProperties: {
         model,
         parentForm,
         ...extraProperties,
      },
      schemas: [NO_ERRORS_SCHEMA],
   });

   // Drain any ngOnInit work (defensive; no HTTP fires for the default no-sheet model).
   await result.fixture.whenStable();
   lastRenderedFixture = result.fixture;
   const comp = result.fixture.componentInstance as TaskActionPane;
   return { comp, fixture: result.fixture, parentForm };
}
