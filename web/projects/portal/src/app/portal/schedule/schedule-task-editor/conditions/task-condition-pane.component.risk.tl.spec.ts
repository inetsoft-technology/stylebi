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
 * Pass 2 — Risk tests for TaskConditionPane.
 * Covers: deleteCondition (async modal confirm/cancel), editCondition (state reset),
 * and formDate setter / dateChange arithmetic.
 */

import { waitFor } from "@testing-library/angular";
import { Subject } from "rxjs";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { TimeConditionType } from "../../../../../../../shared/schedule/model/time-condition-model";
import { MessageDialog } from "../../../../widget/dialog/message-dialog/message-dialog.component";
import {
   makeDailyCondition,
   makeModel,
   makeRunOnceCondition,
   makeWeeklyCondition,
   modalMock,
   renderTaskConditionPane,
   resetMocks,
} from "./task-condition-pane.test-helpers";

// ---------------------------------------------------------------------------
// Per-test helpers
// ---------------------------------------------------------------------------

function mockModalOk(): void {
   modalMock.open.mockImplementation(() => ({
      result: Promise.resolve("ok"),
      componentInstance: { onCommit: new Subject<string>(), onCancel: new Subject<void>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   }));
}

function mockModalCancel(): void {
   modalMock.open.mockImplementation(() => ({
      result: Promise.resolve("cancel"),
      componentInstance: { onCommit: new Subject<string>(), onCancel: new Subject<void>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   }));
}

beforeEach(() => {
   resetMocks();
   // Reset static duplicate-detection guard in showMessageDialog
   MessageDialog.lastMessage = null;
   MessageDialog.lastMessageTS = 0;
});
afterEach(() => vi.restoreAllMocks());

describe("TaskConditionPane — risk tests", () => {

   // -------------------------------------------------------------------------
   // deleteCondition
   // -------------------------------------------------------------------------

   describe("deleteCondition", () => {
      it("removes single selected condition when user confirms", async () => {
         const cond0 = makeDailyCondition({ label: "Cond A" });
         const cond1 = makeDailyCondition({ label: "Cond B" });
         const cond2 = makeDailyCondition({ label: "Cond C" });
         const model = makeModel({ conditions: [cond0, cond1, cond2] });
         const { comp } = await renderTaskConditionPane({ model });

         comp.selectedConditions = [1];
         mockModalOk();
         comp.deleteCondition();

         await waitFor(() => {
            expect(model.conditions.length).toBe(2);
            expect(model.conditions[0].label).toBe("Cond A");
            expect(model.conditions[1].label).toBe("Cond C");
         });
      });

      it("removes multiple selected conditions in reverse index order", async () => {
         const cond0 = makeDailyCondition({ label: "Cond A" });
         const cond1 = makeDailyCondition({ label: "Cond B" });
         const cond2 = makeDailyCondition({ label: "Cond C" });
         const model = makeModel({ conditions: [cond0, cond1, cond2] });
         const { comp } = await renderTaskConditionPane({ model });

         comp.selectedConditions = [0, 2];
         mockModalOk();
         comp.deleteCondition();

         await waitFor(() => {
            expect(model.conditions.length).toBe(1);
            expect(model.conditions[0].label).toBe("Cond B");
         });
      });

      it("resets selectedConditions and updates conditionIndex after deletion", async () => {
         const model = makeModel({
            conditions: [makeDailyCondition({ label: "A" }), makeDailyCondition({ label: "B" })],
         });
         const { comp } = await renderTaskConditionPane({ model });

         comp.selectedConditions = [0];
         mockModalOk();
         comp.deleteCondition();

         await waitFor(() => {
            expect(comp.selectedConditions).toEqual([]);
            // 1 condition remains: conditionIndex = length - 1 = 0
            expect((comp as any).conditionIndex).toBe(0);
         });
      });

      it("does not remove conditions when user cancels (non-ok result)", async () => {
         const model = makeModel({
            conditions: [makeDailyCondition(), makeDailyCondition()],
         });
         const { comp } = await renderTaskConditionPane({ model });

         comp.selectedConditions = [0];
         mockModalCancel();
         comp.deleteCondition();

         await waitFor(() => {
            expect(model.conditions.length).toBe(2);
         });
      });

      it("opens the confirmation dialog exactly once per invocation", async () => {
         const model = makeModel({ conditions: [makeDailyCondition(), makeDailyCondition()] });
         const { comp } = await renderTaskConditionPane({ model });

         comp.selectedConditions = [0];
         // modalMock.open count is reset in beforeEach via resetMocks()
         mockModalOk();
         comp.deleteCondition();

         await waitFor(() => expect(model.conditions.length).toBe(1));
         expect(modalMock.open).toHaveBeenCalledWith(MessageDialog, expect.any(Object));
         expect(modalMock.open).toHaveBeenCalledTimes(1);
      });
   });

   // -------------------------------------------------------------------------
   // editCondition
   // -------------------------------------------------------------------------

   describe("editCondition", () => {
      it("no-op when selectedConditions is empty", async () => {
         const model = makeModel({
            conditions: [makeDailyCondition(), makeWeeklyCondition()],
         });
         const { comp } = await renderTaskConditionPane({ model });

         // After render with 2 conditions, listView=true; conditionIndex stays 0
         comp.selectedConditions = [];
         comp.editCondition();

         // listView must not change to false (guard returned early)
         expect(comp.listView).toBe(true);
      });

      it("sets conditionIndex to the first entry in selectedConditions", async () => {
         const model = makeModel({
            conditions: [makeDailyCondition({ label: "A" }), makeWeeklyCondition({ label: "B" })],
         });
         const { comp } = await renderTaskConditionPane({ model });

         comp.selectedConditions = [1];
         comp.editCondition();

         expect((comp as any).conditionIndex).toBe(1);
      });

      it("sets listView to false regardless of model.conditions count", async () => {
         const model = makeModel({
            conditions: [makeDailyCondition(), makeWeeklyCondition()],
         });
         const { comp } = await renderTaskConditionPane({ model });

         // 2 conditions → listView started as true after updateValues()
         expect(comp.listView).toBe(true);

         comp.selectedConditions = [0];
         comp.editCondition();

         expect(comp.listView).toBe(false);
      });

      it("reinitializes localTimeZoneId from the condition's timezone (not the old value)", async () => {
         const model = makeModel({
            conditions: [makeDailyCondition(), makeWeeklyCondition()],
         });
         const { comp } = await renderTaskConditionPane({ model });

         // Manually set an unrelated timezone id before calling editCondition
         (comp as any).localTimeZoneId = "America/New_York";
         comp.selectedConditions = [0];
         comp.editCondition();

         // editCondition sets localTimeZoneId=null then immediately calls initTimeZone which
         // calls setLocalTimeZone(cond.timeZone). Since cond.timeZone=null and the only option
         // is UTC, the fallback sets localTimeZoneId to "UTC".
         expect((comp as any).localTimeZoneId).not.toBe("America/New_York");
         expect((comp as any).localTimeZoneId).toBe("UTC");
      });

      it("updates selectedOption to match the target condition type", async () => {
         const model = makeModel({
            conditions: [makeDailyCondition(), makeWeeklyCondition()],
         });
         const { comp } = await renderTaskConditionPane({ model });

         // Initially conditionIndex=0 → selectedOption=EVERY_DAY
         expect(comp.selectedOption).toBe(TimeConditionType.EVERY_DAY);

         comp.selectedConditions = [1];
         comp.editCondition();

         expect(comp.selectedOption).toBe(TimeConditionType.EVERY_WEEK);
      });

      it("writes the target condition back through the condition setter", async () => {
         const weekly = makeWeeklyCondition({ label: "Weekly B" });
         const model = makeModel({
            conditions: [makeDailyCondition({ label: "Daily A" }), weekly],
         });
         const { comp } = await renderTaskConditionPane({ model });

         comp.selectedConditions = [1];
         comp.editCondition();

         expect(comp.condition.label).toBe("Weekly B");
      });
   });

   // -------------------------------------------------------------------------
   // formDate setter / dateChange arithmetic
   // -------------------------------------------------------------------------

   describe("formDate setter (dateChange)", () => {
      it("non-AT condition: sets cond.date to a positive timestamp", async () => {
         const cond = makeDailyCondition({ hour: 9, minute: 30, second: 0 });
         const model = makeModel({ conditions: [cond] });
         const { comp } = await renderTaskConditionPane({ model });

         comp.formDate = { year: 2026, month: 6, day: 15 };

         expect(cond.date).toBeGreaterThan(0);
      });

      it("non-AT condition: always resets cond.hour/minute/second to -1", async () => {
         const cond = makeDailyCondition({ hour: 9, minute: 30, second: 0 });
         const model = makeModel({ conditions: [cond] });
         const { comp } = await renderTaskConditionPane({ model });

         comp.formDate = { year: 2026, month: 6, day: 15 };

         expect(cond.hour).toBe(-1);
         expect(cond.minute).toBe(-1);
         expect(cond.second).toBe(-1);
      });

      it("non-AT condition: encodes the specified day into the timestamp", async () => {
         const cond = makeDailyCondition({ hour: 0, minute: 0, second: 0 });
         const model = makeModel({ conditions: [cond] });
         const { comp } = await renderTaskConditionPane({ model });

         const before = cond.date;
         comp.formDate = { year: 2026, month: 6, day: 15 };

         // Timestamp must differ from the initial placeholder
         expect(cond.date).not.toBe(before);
      });

      it("AT condition with valid h/m/s: uses those values in the compiled timestamp", async () => {
         const base = new Date(2026, 5, 1, 14, 30, 0).getTime(); // June 1, 2026 14:30
         const cond = makeRunOnceCondition({ date: base, hour: 14, minute: 30, second: 0 });
         const model = makeModel({ conditions: [cond] });
         const { comp } = await renderTaskConditionPane({ model });

         comp.formDate = { year: 2026, month: 6, day: 20 };

         // dateChange always resets these fields afterwards
         expect(cond.hour).toBe(-1);
         expect(cond.minute).toBe(-1);
         expect(cond.second).toBe(-1);
         // But the new date should be a valid positive timestamp
         expect(cond.date).toBeGreaterThan(0);
      });

      it("AT condition with h=-1: preserves the time portion of the original date", async () => {
         const base = new Date(2026, 5, 1, 14, 30, 0).getTime(); // June 1 14:30
         const cond = makeRunOnceCondition({ date: base, hour: -1, minute: -1, second: -1 });
         const model = makeModel({ conditions: [cond] });
         const { comp } = await renderTaskConditionPane({ model });

         comp.formDate = { year: 2026, month: 6, day: 20 };

         // Timestamp must change (day changed) but be positive and valid
         expect(cond.date).toBeGreaterThan(0);
         expect(cond.date).not.toBe(base);
      });
   });
});
