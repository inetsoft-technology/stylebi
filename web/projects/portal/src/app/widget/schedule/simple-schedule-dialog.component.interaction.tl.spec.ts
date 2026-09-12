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
 * SimpleScheduleDialog — Pass 1: Interaction
 *
 * Method coverage:
 *   Group 1   ngOnInit — form initialization (emailDeliveryEnabled on/off, timeZone seeding)
 *   Group 2   cancel — emits onCancel
 *   Group 3   selectDayOfWeek — toggle add/remove a day from daysOfWeek
 *   Group 4   selectDaysOfWeek — bulk select-all / clear-all
 *   Group 5   changeEveryDay — weekdayOnly + interval pair
 *   Group 6   ok: guard validation — empty emails, invalid startTime, empty daysOfWeek
 *   Group 7   ok: happy path — calls http.get and emits onCommit when type="OK"
 *   Group 8   addEmail / addCCEmail / addBCCEmail — sets editingEmails, opens modal
 */

import { Subject, of } from "rxjs";
import {
   makeComponent,
   makeModel,
   makeModal,
   makeHttp,
   makeTimeCondition,
   makeEmailInfo,
} from "./simple-schedule-dialog.component.test-helpers";
import { TimeConditionType } from "../../../../../shared/schedule/model/time-condition-model";
import { ComponentTool } from "../../common/util/component-tool";
import { MessageDialog } from "../dialog/message-dialog/message-dialog.component";
import { ActionModel } from "./action-model";

afterEach(() => vi.restoreAllMocks());

// Reset dedup guard before every test so consecutive calls with the same message are not silently rejected.
beforeEach(() => {
   MessageDialog.lastMessage = null;
   (MessageDialog as any).lastMessageTS = 0;
});

// ---------------------------------------------------------------------------
// Group 1 — ngOnInit: form initialization
// ---------------------------------------------------------------------------

describe("Group 1 — ngOnInit: form initialization", () => {
   it("should create form with emails, cc, bcc, startTime and timeZone controls when emailDeliveryEnabled=true", () => {
      const { comp } = makeComponent();
      expect(comp.form.contains("emails")).toBe(true);
      expect(comp.form.contains("cc")).toBe(true);
      expect(comp.form.contains("bcc")).toBe(true);
      expect(comp.form.contains("startTime")).toBe(true);
      expect(comp.form.contains("timeZone")).toBe(true);
   });

   it("should create form with only startTime and timeZone controls when emailDeliveryEnabled=false", () => {
      const { comp } = makeComponent({ model: makeModel({ emailDeliveryEnabled: false }) });
      expect(comp.form.contains("startTime")).toBe(true);
      expect(comp.form.contains("timeZone")).toBe(true);
      expect(comp.form.contains("emails")).toBe(false);
   });

   it("should seed timeZoneId from first timeZoneOptions entry", () => {
      const { comp } = makeComponent();
      expect(comp.timeZoneId).toBe("America/New_York");
   });

   it("should set startTimeData with hour=1, minute=30 when startTimeEnabled=true and no localStorage history", () => {
      const { comp } = makeComponent();
      // no stored condition in jsdom — defaults apply
      expect(comp.startTimeData.startTime?.hour).toBe(1);
      expect(comp.startTimeData.startTime?.minute).toBe(30);
      expect(comp.startTimeData.startTimeSelected).toBe(true);
   });

   it("should set startTimeData.timeRange from timeRanges[0] when startTimeEnabled=false", () => {
      const model = makeModel({ startTimeEnabled: false, timeRangeEnabled: true });
      const { comp } = makeComponent({ model });
      expect(comp.startTimeData.startTime).toBeNull();
      expect(comp.startTimeData.timeRange?.name).toBe("Morning");
   });

   it("should call timeZoneService.updateTimeZoneOptions during init", () => {
      const { timeZoneSvc } = makeComponent();
      expect(timeZoneSvc.updateTimeZoneOptions).toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 2 — cancel
// ---------------------------------------------------------------------------

describe("Group 2 — cancel: emits onCancel", () => {
   it("should emit 'cancel' via onCancel when cancel() is called", () => {
      const { comp } = makeComponent();
      const emitSpy = vi.spyOn(comp.onCancel, "emit");

      comp.cancel();

      expect(emitSpy).toHaveBeenCalledWith("cancel");
   });
});

// ---------------------------------------------------------------------------
// Group 3 — selectDayOfWeek: toggle logic
// ---------------------------------------------------------------------------

describe("Group 3 — selectDayOfWeek: toggle add/remove", () => {
   it("should add a day to daysOfWeek when it is not already present", () => {
      const model = makeModel({ timeConditionModel: makeTimeCondition({ daysOfWeek: [1, 2] }) });
      const { comp } = makeComponent({ model });

      comp.selectDayOfWeek(3);

      expect(comp.model.timeConditionModel.daysOfWeek).toContain(3);
   });

   it("should remove a day from daysOfWeek when it is already present", () => {
      const model = makeModel({ timeConditionModel: makeTimeCondition({ daysOfWeek: [1, 2, 3] }) });
      const { comp } = makeComponent({ model });

      comp.selectDayOfWeek(2);

      expect(comp.model.timeConditionModel.daysOfWeek).not.toContain(2);
   });

   it("should leave other days untouched when toggling one day off", () => {
      const model = makeModel({ timeConditionModel: makeTimeCondition({ daysOfWeek: [1, 2, 3] }) });
      const { comp } = makeComponent({ model });

      comp.selectDayOfWeek(2);

      expect(comp.model.timeConditionModel.daysOfWeek).toEqual([1, 3]);
   });
});

// ---------------------------------------------------------------------------
// Group 4 — selectDaysOfWeek: bulk select/clear
// ---------------------------------------------------------------------------

describe("Group 4 — selectDaysOfWeek: bulk select-all and clear-all", () => {
   it("should set daysOfWeek to [1,2,3,4,5,6,7] when isSelectAll=true", () => {
      const model = makeModel({ timeConditionModel: makeTimeCondition({ daysOfWeek: [] }) });
      const { comp } = makeComponent({ model });

      comp.selectDaysOfWeek(true);

      expect(comp.model.timeConditionModel.daysOfWeek).toEqual([1, 2, 3, 4, 5, 6, 7]);
   });

   it("should clear daysOfWeek to [] when isSelectAll=false", () => {
      const model = makeModel({ timeConditionModel: makeTimeCondition({ daysOfWeek: [1, 2, 3, 4, 5, 6, 7] }) });
      const { comp } = makeComponent({ model });

      comp.selectDaysOfWeek(false);

      expect(comp.model.timeConditionModel.daysOfWeek).toEqual([]);
   });
});

// ---------------------------------------------------------------------------
// Group 5 — changeEveryDay: weekdayOnly + interval pair
// ---------------------------------------------------------------------------

describe("Group 5 — changeEveryDay: sets weekdayOnly and adjusts interval", () => {
   it("should set weekdayOnly=true and interval=null when weekday=true", () => {
      const { comp } = makeComponent();

      comp.changeEveryDay(true);

      expect(comp.model.timeConditionModel.weekdayOnly).toBe(true);
      expect(comp.model.timeConditionModel.interval).toBeNull();
   });

   it("should set weekdayOnly=false and interval=1 when weekday=false", () => {
      const model = makeModel({ timeConditionModel: makeTimeCondition({ weekdayOnly: true, interval: null }) });
      const { comp } = makeComponent({ model });

      comp.changeEveryDay(false);

      expect(comp.model.timeConditionModel.weekdayOnly).toBe(false);
      expect(comp.model.timeConditionModel.interval).toBe(1);
   });
});

// ---------------------------------------------------------------------------
// Group 6 — ok: guard validation
// ---------------------------------------------------------------------------

describe("Group 6 — ok: guard validation", () => {
   it("should show error dialog and not call http.get when emails is empty", () => {
      const model = makeModel({
         emailDeliveryEnabled: true,
         actionModel: Object.assign(new ActionModel(), {
            type: "ViewsheetAction",
            emailInfoModel: makeEmailInfo({ emails: "" }),
         }),
      });
      const { comp, http } = makeComponent({ model });
      const msgSpy = vi.spyOn(ComponentTool, "showMessageDialog").mockReturnValue(Promise.resolve("ok"));
      try {
         http.get.mockClear();
         comp.ok();
         expect(msgSpy).toHaveBeenCalled();
         expect(http.get).not.toHaveBeenCalled();
      } finally {
         msgSpy.mockRestore();
      }
   });

   it("should show error dialog and not call http.get when startTimeSelected=true and valid=false", () => {
      const { comp, http } = makeComponent();
      comp.startTimeData = { startTime: { hour: -1, minute: -1, second: 0 }, timeRange: null, startTimeSelected: true, valid: false };
      const msgSpy = vi.spyOn(ComponentTool, "showMessageDialog").mockReturnValue(Promise.resolve("ok"));
      try {
         http.get.mockClear();
         comp.ok();
         expect(msgSpy).toHaveBeenCalled();
         expect(http.get).not.toHaveBeenCalled();
      } finally {
         msgSpy.mockRestore();
      }
   });

   it("should show error dialog and not call http.get when EVERY_WEEK with no days selected", () => {
      const model = makeModel({
         timeConditionModel: makeTimeCondition({ type: TimeConditionType.EVERY_WEEK, daysOfWeek: [] }),
      });
      const { comp, http } = makeComponent({ model });
      const msgSpy = vi.spyOn(ComponentTool, "showMessageDialog").mockReturnValue(Promise.resolve("ok"));
      try {
         http.get.mockClear();
         comp.ok();
         expect(msgSpy).toHaveBeenCalled();
         expect(http.get).not.toHaveBeenCalled();
      } finally {
         msgSpy.mockRestore();
      }
   });
});

// ---------------------------------------------------------------------------
// Group 7 — ok: happy path HTTP call + onCommit emit
// ---------------------------------------------------------------------------

describe("Group 7 — ok: happy path", () => {
   it("should call http.get with CHECK_EMAIL_VALID_URI", () => {
      const { comp, http } = makeComponent();
      http.get.mockClear();

      comp.ok();

      expect(http.get).toHaveBeenCalledWith(
         expect.stringContaining("check-email-valid"),
         expect.anything(),
      );
   });

   it("should emit onCommit when server returns type='OK'", () => {
      const { comp, http } = makeComponent();
      http.get.mockReturnValue(
         of({ messageCommand: { type: "OK", message: "", events: null }, addressHistory: [] }),
      );
      const emitSpy = vi.spyOn(comp.onCommit, "emit");
      try {
         comp.ok();
         expect(emitSpy).toHaveBeenCalledOnce();
         expect(emitSpy.mock.calls[0][0]).toBe(comp.model);
      } finally {
         emitSpy.mockRestore();
      }
   });

   it("should set model.timeConditionModel.conditionType to 'TimeCondition' on success", () => {
      const { comp } = makeComponent();

      comp.ok();

      expect(comp.model.timeConditionModel.conditionType).toBe("TimeCondition");
   });
});

// ---------------------------------------------------------------------------
// Group 8 — addEmail / addCCEmail / addBCCEmail
// ---------------------------------------------------------------------------

describe("Group 8 — addEmail / addCCEmail / addBCCEmail: editingEmails + modal open", () => {
   it("should set editingEmails from emailInfoModel.emails before opening modal for addEmail()", () => {
      const { comp, modal } = makeComponent();
      // Ensure modal result never resolves so form setValue doesn't run
      modal.open.mockReturnValue({ componentInstance: { onCommit: new Subject<string>() }, result: new Promise(() => {}) });

      comp.addEmail();

      expect(comp.editingEmails).toBe("test@example.com");
   });

   it("should call modal.open when addEmail() is invoked", () => {
      const { comp, modal } = makeComponent();
      modal.open.mockReturnValue({ componentInstance: { onCommit: new Subject<string>() }, result: new Promise(() => {}) });

      comp.addEmail();

      expect(modal.open).toHaveBeenCalled();
   });

   it("should set editingEmails from ccAddresses before opening modal for addCCEmail()", () => {
      const model = makeModel({
         actionModel: Object.assign(new ActionModel(), {
            type: "ViewsheetAction",
            emailInfoModel: makeEmailInfo({ emails: "a@b.com", ccAddresses: "cc@b.com" }),
         }),
      });
      const { comp, modal } = makeComponent({ model });
      modal.open.mockReturnValue({ componentInstance: { onCommit: new Subject<string>() }, result: new Promise(() => {}) });

      comp.addCCEmail();

      expect(comp.editingEmails).toBe("cc@b.com");
   });

   it("should set editingEmails from bccAddresses before opening modal for addBCCEmail()", () => {
      const model = makeModel({
         actionModel: Object.assign(new ActionModel(), {
            type: "ViewsheetAction",
            emailInfoModel: makeEmailInfo({ emails: "a@b.com", bccAddresses: "bcc@b.com" }),
         }),
      });
      const { comp, modal } = makeComponent({ model });
      modal.open.mockReturnValue({ componentInstance: { onCommit: new Subject<string>() }, result: new Promise(() => {}) });

      comp.addBCCEmail();

      expect(comp.editingEmails).toBe("bcc@b.com");
   });

   it("should update form emails control after addEmail() dialog resolves with new address", async () => {
      const { comp, modal } = makeComponent();
      modal.open.mockReturnValue({
         componentInstance: { onCommit: new Subject<string>() },
         result: Promise.resolve({ emails: "new@example.com" }),
      });

      comp.addEmail();
      await Promise.resolve();
      await Promise.resolve();

      expect(comp.form.get("emails")?.value).toBe("new@example.com");
   });
});
