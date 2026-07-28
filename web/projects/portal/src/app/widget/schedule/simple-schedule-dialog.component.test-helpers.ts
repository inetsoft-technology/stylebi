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
 * Shared test helpers for SimpleScheduleDialog P1/P2/P3 spec files.
 *
 * Uses direct class instantiation — the constructor takes (HttpClient, NgbModal,
 * TimeZoneService).  The @ViewChild emailAddrDialogModel is undefined in this mode;
 * tests that open the email dialog receive undefined as the first arg to modal.open(),
 * which the mock accepts without error.
 */

import { of, Subject } from "rxjs";
import { SimpleScheduleDialog } from "./simple-schedule-dialog.component";
import { SimpleScheduleDialogModel } from "../../vsobjects/model/schedule/simple-schedule-dialog-model";
import { EmailInfoModel } from "../../vsobjects/model/schedule/email-info-model";
import { ActionModel } from "./action-model";
import { TimeConditionModel, TimeConditionType } from "../../../../../shared/schedule/model/time-condition-model";
import { FileFormatType } from "../../vsobjects/model/file-format-type";
import { clearStoredCondition } from "../../common/util/schedule-condition.util";

// ---------------------------------------------------------------------------
// Model factories
// ---------------------------------------------------------------------------

export function makeTimeCondition(overrides: Partial<TimeConditionModel> = {}): TimeConditionModel {
   return {
      conditionType: "TimeCondition",
      label: "New Time Condition",
      type: TimeConditionType.EVERY_DAY,
      hour: 1,
      minute: 30,
      second: 0,
      interval: 1,
      weekdayOnly: false,
      daysOfWeek: [1, 2, 3, 4, 5],
      monthsOfYear: [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11],
      dayOfMonth: 1,
      weekOfMonth: 1,
      dayOfWeek: 1,
      monthlyDaySelected: false,
      timeZone: "America/New_York",
      timeZoneOffset: 0,
      ...overrides,
   };
}

export function makeEmailInfo(overrides: Partial<EmailInfoModel> = {}): EmailInfoModel {
   return {
      emails: "test@example.com",
      fromAddress: "",
      formatType: FileFormatType.EXPORT_TYPE_EXCEL,
      formatStr: "",
      attachmentName: "",
      subject: "",
      message: "",
      matchLayout: false,
      onlyDataComponents: false,
      expandSelections: false,
      csvConfigModel: {
         delimiter: ",",
         quote: null,
         keepHeader: false,
         tabDelimited: false,
         selectedAssemblies: null,
      },
      ccAddresses: "",
      bccAddresses: "",
      ...overrides,
   };
}

export function makeModel(overrides: Partial<SimpleScheduleDialogModel> = {}): SimpleScheduleDialogModel {
   const actionModel = new ActionModel();
   actionModel.type = "ViewsheetAction";
   actionModel.emailInfoModel = makeEmailInfo();

   return {
      userDialogEnabled: false,
      timeProp: null,
      twelveHourSystem: false,
      taskName: null,
      isSecurity: false,
      formatTypes: [],
      expandEnabled: false,
      emailButtonVisible: true,
      emailDeliveryEnabled: true,
      timeConditionModel: makeTimeCondition(),
      actionModel,
      emailAddrDialogModel: {
         rootTree: { label: "Root", leaf: false, children: [], data: null },
      },
      timeRanges: [
         { name: "Morning", label: "Morning", startTime: "08:00", endTime: "12:00", defaultRange: true },
      ],
      timeZoneOptions: [
         { timeZoneId: "America/New_York", label: "(UTC-05:00) Eastern Time", hourOffset: "-05", minuteOffset: -300 },
      ],
      startTimeEnabled: true,
      timeRangeEnabled: false,
      users: [],
      groups: [],
      emailGroups: [],
      ...overrides,
   };
}

// ---------------------------------------------------------------------------
// Service mocks
// ---------------------------------------------------------------------------

export function makeHttp() {
   return {
      get: vi.fn().mockReturnValue(
         of({
            messageCommand: { type: "OK", message: "", events: null },
            addressHistory: [],
         }),
      ),
      post: vi.fn().mockReturnValue(of(null)),
   };
}

export function makeModal() {
   return {
      open: vi.fn().mockReturnValue({
         componentInstance: { onCommit: new Subject<string>() },
         result: Promise.reject("dismissed"),
      }),
   };
}

export function makeTimeZoneSvc() {
   return {
      // return the options array unchanged so initForm() can access [0]
      updateTimeZoneOptions: vi.fn().mockImplementation((options: any) => options ?? []),
   };
}

// ---------------------------------------------------------------------------
// Component factory
// ---------------------------------------------------------------------------

export interface ComponentResult {
   comp: SimpleScheduleDialog;
   http: ReturnType<typeof makeHttp>;
   modal: ReturnType<typeof makeModal>;
   timeZoneSvc: ReturnType<typeof makeTimeZoneSvc>;
}

export function makeComponent(opts: {
   model?: SimpleScheduleDialogModel;
   http?: ReturnType<typeof makeHttp>;
   modal?: ReturnType<typeof makeModal>;
   timeZoneSvc?: ReturnType<typeof makeTimeZoneSvc>;
   exportTypes?: { label: string; value: string }[];
   isReport?: boolean;
   securityEnabled?: boolean;
   skipNgOnInit?: boolean;
} = {}): ComponentResult {
   const http = opts.http ?? makeHttp();
   const modal = opts.modal ?? makeModal();
   const timeZoneSvc = opts.timeZoneSvc ?? makeTimeZoneSvc();

   // ok() stores the condition in localStorage; without clearing, later suites' ngOnInit
   // replaces the carefully built model via getHistoryModel() (full-suite order flake).
   clearStoredCondition();

   const comp = new SimpleScheduleDialog(http as any, modal as any, timeZoneSvc as any);

   comp.model = opts.model ?? makeModel();
   comp.exportTypes = opts.exportTypes ?? [];
   comp.isReport = opts.isReport ?? false;
   comp.securityEnabled = opts.securityEnabled ?? false;

   if(!opts.skipNgOnInit) {
      comp.ngOnInit();
   }

   return { comp, http, modal, timeZoneSvc };
}
