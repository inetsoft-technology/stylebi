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
 * Shared test helpers for ActionAccordion multi-pass TL specs.
 * Consumed by:
 *   action-accordion.component.interaction.tl.spec.ts
 *   action-accordion.component.risk.tl.spec.ts
 *   action-accordion.component.display.tl.spec.ts
 *
 * Mocking strategy: ScheduleUsersService is mocked with vi.fn() to avoid
 * STOMP + HTTP constructor-fired side effects (NG0205 risk). NgbModal is mocked
 * to prevent real dialog opening. CkeditorWrapperComponent, ParameterTable,
 * EmailAddrDialog, and CSVConfigPane are stubbed via importOverrides because they
 * carry their own heavy DI trees (CKEditor5 browser APIs, nested NgbModal usage)
 * that are unrelated to the logic under test.
 */

import { Component, EventEmitter, forwardRef, Input, NO_ERRORS_SCHEMA, Output } from "@angular/core";
import { ControlValueAccessor, NG_VALUE_ACCESSOR, UntypedFormGroup } from "@angular/forms";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { render } from "@testing-library/angular";
import { Subject, of } from "rxjs";
import { vi } from "vitest";
import { GeneralActionModel } from "../../../../../../../../shared/schedule/model/general-action-model";
import { TaskActionPaneModel } from "../../../../../../../../shared/schedule/model/task-action-pane-model";
import { ScheduleUsersService } from "../../../../../../../../shared/schedule/schedule-users.service";
import { CkeditorWrapperComponent } from "../../../../../../../../shared/ckeditor-wrapper/ckeditor-wrapper.component";
import { CSVConfigPane } from "../../../../../widget/schedule/csv-config-pane.component";
import { ParameterTable } from "../../parameter-table/parameter-table.component";
import { EmailAddrDialog } from "../../../../../widget/email-dialog/email-addr-dialog.component";
import { ActionAccordion } from "./action-accordion.component";
import { ExportFormatModel } from "../../../../../../../../shared/schedule/model/export-format-model";
import { VSBookmarkInfoModel } from "../../../../../vsobjects/model/vs-bookmark-info-model";
import { ScheduleAlertModel } from "../../../model/schedule-alert-model";

// ---------------------------------------------------------------------------
// Child-component stubs
// ---------------------------------------------------------------------------

@Component({
   selector: "ckeditor-wrapper",
   template: "",
   standalone: true,
   providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => CkeditorWrapperStub), multi: true }],
})
class CkeditorWrapperStub implements ControlValueAccessor {
   @Input() advanced: boolean;
   writeValue(_: any): void {}
   registerOnChange(_: any): void {}
   registerOnTouched(_: any): void {}
}

@Component({ selector: "parameter-table", template: "", standalone: true })
class ParameterTableStub {
   @Input() parameters: any[];
   @Input() parameterNames: string[] = [];
   @Input() containsSheet: boolean;
   @Output() parametersChange = new EventEmitter<any[]>();
}

@Component({ selector: "email-addr-dialog", template: "", standalone: true })
class EmailAddrDialogStub {
   @Input() embeddedOnly: boolean;
   @Input() model: any;
   @Input() addresses: string;
   @Output() onCommit = new EventEmitter<any>();
   @Output() onCancel = new EventEmitter<void>();
}

@Component({ selector: "csv-config-pane", template: "", standalone: true })
class CSVConfigPaneStub {
   @Input() model: any;
   @Input() selectAssemblyEnable: boolean;
   @Input() tableDataAssemblies: string[];
   @Input() parentForm: UntypedFormGroup;
   @Input() formId: string;
}

// ---------------------------------------------------------------------------
// Factories
// ---------------------------------------------------------------------------

export const PDF_MAIL_FORMAT: ExportFormatModel = { type: "PDF", label: "PDF" };
export const EXCEL_MAIL_FORMAT: ExportFormatModel = { type: "Excel", label: "Excel" };
export const EXCEL_SAVE_FORMAT: ExportFormatModel = { type: "0", label: "Excel" };
export const PDF_SAVE_FORMAT: ExportFormatModel = { type: "9", label: "PDF" };

export function makeAction(overrides: Partial<GeneralActionModel> = {}): GeneralActionModel {
   return {
      actionType: "ViewsheetAction",
      notificationEnabled: false,
      deliverEmailsEnabled: false,
      printOnServerEnabled: false,
      saveToServerEnabled: false,
      bundledAsZip: false,
      format: "Excel",
      fromEmail: "test@example.com",
      emailMatchLayout: true,
      sheet: "1^128^__NULL__^Sheet1",
      ccAddress: "",
      bccAddress: "",
      parameters: [],
      filePaths: [],
      saveFormats: [],
      serverFilePaths: [],
      bookmarks: [],
      ...overrides,
   };
}

export function makeModel(overrides: Partial<TaskActionPaneModel> = {}): TaskActionPaneModel {
   return {
      securityEnabled: true,
      emailButtonVisible: false,
      endUser: null,
      administrator: false,
      defaultFromEmail: "from@example.com",
      fromEmailEnabled: true,
      viewsheetEnabled: true,
      notificationEmailEnabled: true,
      saveToDiskEnabled: true,
      emailDeliveryEnabled: true,
      expandEnabled: true,
      cvsEnabled: false,
      actions: [],
      userDefinedClasses: [],
      userDefinedClassLabels: [],
      dashboardMap: {},
      printers: [],
      folderPaths: [],
      folderLabels: [],
      mailFormats: [PDF_MAIL_FORMAT],
      vsMailFormats: [PDF_MAIL_FORMAT],
      saveFileFormats: [PDF_SAVE_FORMAT],
      vsSaveFileFormats: [EXCEL_SAVE_FORMAT],
      ...overrides,
   };
}

export function makeBookmark(label: string, type: number = 0, name?: string): VSBookmarkInfoModel {
   return { label, type, name: name ?? label } as VSBookmarkInfoModel;
}

export function makeHighlight(element: string, highlight: string, condition = ""): ScheduleAlertModel {
   return { element, highlight, condition, count: 1 };
}

// ---------------------------------------------------------------------------
// Shared mocks
// ---------------------------------------------------------------------------

// Do NOT reassign properties of these mocks (e.g. scheduleUsersMock.getEmailUsers = vi.fn(...)).
// resetMocks() calls mockClear() and re-applies the default implementation each beforeEach;
// a property reassignment would bypass that reset and corrupt subsequent tests.
// Per-test overrides must use mockReturnValueOnce() / mockImplementationOnce() instead.
export const scheduleUsersMock = {
   getEmailUsers: vi.fn().mockReturnValue(of([])),
   getEmailGroups: vi.fn().mockReturnValue(of([])),
};

export const modalMock = {
   open: vi.fn().mockImplementation(() => ({
      result: new Promise<any>(() => {}),
      componentInstance: { onCommit: new Subject<string>(), onCancel: new Subject<void>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   })),
};

export function resetMocks(): void {
   Object.values(scheduleUsersMock).forEach(m => typeof m.mockClear === "function" && m.mockClear());
   scheduleUsersMock.getEmailUsers.mockReturnValue(of([]));
   scheduleUsersMock.getEmailGroups.mockReturnValue(of([]));
   Object.values(modalMock).forEach(m => typeof m.mockClear === "function" && m.mockClear());
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
   model?: TaskActionPaneModel;
   action?: GeneralActionModel;
   parentForm?: UntypedFormGroup;
   requiredParameters?: string[];
   optionalParameters?: string[];
   bookmarks?: VSBookmarkInfoModel[];
   bookmarkList?: string[];
   highlights?: ScheduleAlertModel[];
   containsSheet?: boolean;
   hasPrintLayout?: boolean;
   tableDataAssemblies?: string[];
}

export async function renderActionAccordion(opts: RenderOpts = {}) {
   const model = opts.model ?? makeModel();
   const action = opts.action ?? makeAction();
   const parentForm = opts.parentForm ?? new UntypedFormGroup({});

   const { fixture } = await render(ActionAccordion, {
      componentProperties: {
         parentForm,
         model,
         action,
         requiredParameters: opts.requiredParameters ?? [],
         optionalParameters: opts.optionalParameters ?? [],
         bookmarks: opts.bookmarks ?? [],
         bookmarkList: opts.bookmarkList ?? [],
         highlights: opts.highlights ?? [],
         containsSheet: opts.containsSheet ?? false,
         hasPrintLayout: opts.hasPrintLayout ?? false,
         tableDataAssemblies: opts.tableDataAssemblies ?? [],
      },
      providers: [
         { provide: NgbModal, useValue: modalMock },
         { provide: ScheduleUsersService, useValue: scheduleUsersMock },
      ],
      importOverrides: [
         { replace: CkeditorWrapperComponent, with: CkeditorWrapperStub },
         { replace: ParameterTable, with: ParameterTableStub },
         { replace: EmailAddrDialog, with: EmailAddrDialogStub },
         { replace: CSVConfigPane, with: CSVConfigPaneStub },
      ],
      schemas: [NO_ERRORS_SCHEMA],
   });

   const comp = fixture.componentInstance as ActionAccordion;
   return { comp, fixture, model, action, parentForm };
}
