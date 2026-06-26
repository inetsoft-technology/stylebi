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
 * SimpleScheduleDialog — Pass 2: Risk / Async
 *
 * Risk-first coverage:
 *   Group 1   ok: non-OK HTTP response → shows error dialog, does not emit onCommit
 *   Group 2   ok: startTimeSelected=true — copies hour/minute/second to timeConditionModel
 *   Group 3   ok: startTimeSelected=false — copies timeRange, clears time fields
 *   Group 4   ok: monthly day special values (dayOfMonth 30→-2, 31→-1)
 *   Group 5   ok: monthly weekOfMonth path (monthlyDaySelected=false)
 *   Group 6   ngOnDestroy — unsubscribes and nulls subscriptions
 *   Group 7   okDisabled — all condition branches
 *   Group 8   search — email history autocomplete filtering
 *   Group 9   updateOnlyDataComponents — clears onlyDataComponents when matchLayout=true
 */

import { of } from "rxjs";
import {
   makeComponent,
   makeModel,
   makeHttp,
   makeTimeCondition,
   makeEmailInfo,
} from "./simple-schedule-dialog.component.test-helpers";
import { TimeConditionType } from "../../../../../shared/schedule/model/time-condition-model";
import { FileFormatType } from "../../vsobjects/model/file-format-type";
import { ComponentTool } from "../../common/util/component-tool";
import { MessageDialog } from "../dialog/message-dialog/message-dialog.component";
import { ActionModel } from "./action-model";
import { LocalStorage } from "../../common/util/local-storage.util";

afterEach(() => vi.restoreAllMocks());

// Reset dedup guard; also block localStorage reads so a successful ok() in one test
// cannot write a stored condition that replaces timeConditionModel in a later ngOnInit.
beforeEach(() => {
   MessageDialog.lastMessage = null;
   (MessageDialog as any).lastMessageTS = 0;
   vi.spyOn(LocalStorage, "getItem").mockReturnValue(null);
});

// ---------------------------------------------------------------------------
// Group 1 — ok: non-OK HTTP response
// ---------------------------------------------------------------------------

describe("Group 1 — ok: non-OK HTTP response shows error dialog and does not emit", () => {
   it("should call ComponentTool.showMessageDialog when server returns type != 'OK'", () => {
      const http = makeHttp();
      http.get.mockReturnValue(
         of({ messageCommand: { type: "WARNING", message: "Invalid address" }, addressHistory: [] }),
      );
      const { comp } = makeComponent({ http });
      const msgSpy = vi.spyOn(ComponentTool, "showMessageDialog").mockReturnValue(Promise.resolve("ok"));
      try {
         comp.ok();
         expect(msgSpy).toHaveBeenCalledWith(
            expect.anything(),
            expect.any(String),
            "Invalid address",
         );
      } finally {
         msgSpy.mockRestore();
      }
   });

   it("should not emit onCommit when server returns non-OK type", () => {
      const http = makeHttp();
      http.get.mockReturnValue(
         of({ messageCommand: { type: "ERROR", message: "Bad addr" }, addressHistory: [] }),
      );
      const { comp } = makeComponent({ http });
      const msgSpy = vi.spyOn(ComponentTool, "showMessageDialog").mockReturnValue(Promise.resolve("ok"));
      const emitSpy = vi.spyOn(comp.onCommit, "emit");
      try {
         comp.ok();
         expect(emitSpy).not.toHaveBeenCalled();
      } finally {
         msgSpy.mockRestore();
         emitSpy.mockRestore();
      }
   });
});

// ---------------------------------------------------------------------------
// Group 2 — ok: startTimeSelected=true copies time fields to timeConditionModel
// ---------------------------------------------------------------------------

describe("Group 2 — ok: startTimeSelected=true copies hour/minute/second to timeConditionModel", () => {
   it("should set hour, minute, second on timeConditionModel from startTimeData when startTimeSelected=true", () => {
      const { comp } = makeComponent();
      comp.startTimeData = { startTime: { hour: 9, minute: 15, second: 0 }, timeRange: null, startTimeSelected: true, valid: true };

      comp.ok();

      expect(comp.model.timeConditionModel.hour).toBe(9);
      expect(comp.model.timeConditionModel.minute).toBe(15);
      expect(comp.model.timeConditionModel.second).toBe(0);
   });

   it("should set timeConditionModel.timeRange to null when startTimeSelected=true", () => {
      const { comp } = makeComponent();
      comp.startTimeData = { startTime: { hour: 9, minute: 0, second: 0 }, timeRange: null, startTimeSelected: true, valid: true };

      comp.ok();

      expect(comp.model.timeConditionModel.timeRange).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 3 — ok: startTimeSelected=false copies timeRange, clears time fields
// ---------------------------------------------------------------------------

describe("Group 3 — ok: startTimeSelected=false copies timeRange and clears hour/minute/second", () => {
   it("should set hour, minute, second to -1 when startTimeSelected=false", () => {
      const model = makeModel({ startTimeEnabled: false, timeRangeEnabled: true });
      const { comp } = makeComponent({ model });
      const range = { name: "Morning", label: "Morning", startTime: "08:00", endTime: "12:00", defaultRange: true };
      comp.startTimeData = { startTime: null, timeRange: range, startTimeSelected: false, valid: true };

      comp.ok();

      expect(comp.model.timeConditionModel.hour).toBe(-1);
      expect(comp.model.timeConditionModel.minute).toBe(-1);
      expect(comp.model.timeConditionModel.second).toBe(-1);
   });

   it("should copy timeRange to timeConditionModel when startTimeSelected=false", () => {
      const model = makeModel({ startTimeEnabled: false, timeRangeEnabled: true });
      const { comp } = makeComponent({ model });
      const range = { name: "Morning", label: "Morning", startTime: "08:00", endTime: "12:00", defaultRange: true };
      comp.startTimeData = { startTime: null, timeRange: range, startTimeSelected: false, valid: true };

      comp.ok();

      expect(comp.model.timeConditionModel.timeRange).toBe(range);
   });
});

// ---------------------------------------------------------------------------
// Group 4 — ok: monthly dayOfMonth special values
// ---------------------------------------------------------------------------

describe("Group 4 — ok: monthly dayOfMonth special values (30→-2, 31→-1)", () => {
   it("should convert dayOfMonth=30 to -2 when EVERY_MONTH and monthlyDaySelected=true", () => {
      const model = makeModel({
         timeConditionModel: makeTimeCondition({ type: TimeConditionType.EVERY_MONTH, monthlyDaySelected: true }),
      });
      const { comp } = makeComponent({ model });
      // ngOnInit defaults set dayOfMonth=1; override after init to test the special-value conversion
      comp.model.timeConditionModel.dayOfMonth = 30;

      comp.ok();

      expect(comp.model.timeConditionModel.dayOfMonth).toBe(-2);
   });

   it("should convert dayOfMonth=31 to -1 when EVERY_MONTH and monthlyDaySelected=true", () => {
      const model = makeModel({
         timeConditionModel: makeTimeCondition({ type: TimeConditionType.EVERY_MONTH, monthlyDaySelected: true }),
      });
      const { comp } = makeComponent({ model });
      // ngOnInit defaults set dayOfMonth=1; override after init to test the special-value conversion
      comp.model.timeConditionModel.dayOfMonth = 31;

      comp.ok();

      expect(comp.model.timeConditionModel.dayOfMonth).toBe(-1);
   });

   it("should clear weekOfMonth and dayOfWeek when EVERY_MONTH and monthlyDaySelected=true", () => {
      const model = makeModel({
         timeConditionModel: makeTimeCondition({ type: TimeConditionType.EVERY_MONTH, monthlyDaySelected: true }),
      });
      const { comp } = makeComponent({ model });

      comp.ok();

      expect(comp.model.timeConditionModel.weekOfMonth).toBeNull();
      expect(comp.model.timeConditionModel.dayOfWeek).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 5 — ok: monthly weekOfMonth path
// ---------------------------------------------------------------------------

describe("Group 5 — ok: monthly weekOfMonth path (monthlyDaySelected=false)", () => {
   it("should set dayOfMonth to null when EVERY_MONTH and monthlyDaySelected=false", () => {
      const model = makeModel({
         timeConditionModel: makeTimeCondition({
            type: TimeConditionType.EVERY_MONTH,
            monthlyDaySelected: false,
            dayOfMonth: 5,
         }),
      });
      const { comp } = makeComponent({ model });

      comp.ok();

      expect(comp.model.timeConditionModel.dayOfMonth).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 6 — ngOnDestroy
// ---------------------------------------------------------------------------

describe("Group 6 — ngOnDestroy: unsubscribes and nulls subscriptions", () => {
   it("should set subscriptions to null after destroy", () => {
      const { comp } = makeComponent();

      comp.ngOnDestroy();

      expect((comp as any).subscriptions).toBeNull();
   });

   it("should not throw when ngOnDestroy is called after subscriptions is already null", () => {
      const { comp } = makeComponent();
      comp.ngOnDestroy();

      // Second call: subscriptions is null, guard `if(!!this.subscriptions)` prevents double-unsubscribe
      expect(() => comp.ngOnDestroy()).not.toThrow();
   });
});

// ---------------------------------------------------------------------------
// Group 7 — okDisabled: all branches
// ---------------------------------------------------------------------------

describe("Group 7 — okDisabled: all condition branches", () => {
   it("should return false when form is valid and startTimeEnabled=true with startTimeSelected=true", () => {
      const { comp } = makeComponent();
      // form has valid emails from factory; startTimeData.startTimeSelected=true from ngOnInit
      expect(comp.okDisabled()).toBe(false);
   });

   it("should return true when required emails control is empty (form invalid)", () => {
      const { comp } = makeComponent();
      comp.form.get("emails")?.setValue("");
      expect(comp.okDisabled()).toBe(true);
   });

   it("should return true when isEmptyTable() returns true (CSV with empty selectedAssemblies)", () => {
      const model = makeModel({
         actionModel: Object.assign(new ActionModel(), {
            type: "ViewsheetAction",
            emailInfoModel: makeEmailInfo({
               formatType: FileFormatType.EXPORT_TYPE_CSV,
               csvConfigModel: { delimiter: ",", quote: null, keepHeader: false, tabDelimited: false, selectedAssemblies: [] },
            }),
         }),
      });
      const { comp } = makeComponent({ model });
      expect(comp.okDisabled()).toBe(true);
   });

   it("should return false when !startTimeEnabled and !timeRangeEnabled", () => {
      const model = makeModel({ startTimeEnabled: false, timeRangeEnabled: false });
      const { comp } = makeComponent({ model });
      expect(comp.okDisabled()).toBe(false);
   });

   it("should return true when timeRangeEnabled=true and timeRanges is empty", () => {
      const model = makeModel({ startTimeEnabled: false, timeRangeEnabled: true, timeRanges: [] });
      const { comp } = makeComponent({ model });
      // startTimeData.startTimeSelected=false (because startTimeEnabled=false), timeRangeEnabled=true, timeRanges=[]
      expect(comp.okDisabled()).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 8 — search: email history autocomplete filtering
// ---------------------------------------------------------------------------

describe("Group 8 — search: email history autocomplete filtering", () => {
   it("should return emails matching the search term (case-insensitive)", async () => {
      const { comp } = makeComponent();
      comp.emailHistory = ["alice@example.com", "bob@example.com", "carol@example.com"];

      const results = await new Promise<string[]>((resolve) => {
         comp.search(of("alice")).subscribe((res: string[]) => resolve(res));
      });

      expect(results).toEqual(["alice@example.com"]);
   });

   it("should return empty array when search term is empty string", async () => {
      const { comp } = makeComponent();
      comp.emailHistory = ["alice@example.com"];

      const results = await new Promise<string[]>((resolve) => {
         comp.search(of("")).subscribe((res: string[]) => resolve(res));
      });

      expect(results).toEqual([]);
   });

   it("should return at most 10 results when history is large", async () => {
      const { comp } = makeComponent();
      comp.emailHistory = Array.from({ length: 20 }, (_, i) => `user${i}@example.com`);

      const results = await new Promise<string[]>((resolve) => {
         comp.search(of("user")).subscribe((res: string[]) => resolve(res));
      });

      expect(results.length).toBeLessThanOrEqual(10);
   });
});

// ---------------------------------------------------------------------------
// Group 9 — updateOnlyDataComponents
// ---------------------------------------------------------------------------

describe("Group 9 — updateOnlyDataComponents: clears onlyDataComponents when matchLayout=true", () => {
   it("should set onlyDataComponents=false when matchLayout=true", () => {
      const model = makeModel({
         actionModel: Object.assign(new ActionModel(), {
            type: "ViewsheetAction",
            emailInfoModel: makeEmailInfo({ matchLayout: true, onlyDataComponents: true }),
         }),
      });
      const { comp } = makeComponent({ model });

      comp.updateOnlyDataComponents();

      expect(comp.model.actionModel.emailInfoModel.onlyDataComponents).toBe(false);
   });

   it("should leave onlyDataComponents unchanged when matchLayout=false", () => {
      const model = makeModel({
         actionModel: Object.assign(new ActionModel(), {
            type: "ViewsheetAction",
            emailInfoModel: makeEmailInfo({ matchLayout: false, onlyDataComponents: true }),
         }),
      });
      const { comp } = makeComponent({ model });

      comp.updateOnlyDataComponents();

      expect(comp.model.actionModel.emailInfoModel.onlyDataComponents).toBe(true);
   });
});
