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
 * VSRangeSlider – time increment mapping and the Edit dialog round-trip
 *
 * The server writes range slider tick values as JDBC-style escapes: {y 'yyyy'}, {m 'yyyy-MM'},
 * {d 'yyyy-MM-dd'}, {t 'HH:mm:ss'} (time of day, XSchema.TIME columns bound with the
 * HOUR_OF_DAY/MINUTE_OF_DAY range type) and {ts 'yyyy-MM-dd HH:mm:ss'} (timestamp).
 *
 * Coverage:
 *   Group 1 - extractTimeIncrement: one increment per value token, {t} kept apart from {ts}
 *   Group 2 - labelToTimestamp / toIncrementTimestamp: finite, comparable timestamps per increment
 *   Group 3 - showRangeSliderEditDialog: dialog seeded with valid Dates for a time-of-day binding
 *   Group 4 - commit round-trip: committed min/max snap to the matching tick indexes
 */

import { XSchema } from "../../../common/data/xschema";
import { ComponentTool } from "../../../common/util/component-tool";
import { GuiTool } from "../../../common/util/gui-tool";
import { TIME_BASE_DATE, TIME_OF_DAY_INCREMENT }
   from "../../dialog/range-slider-edit-dialog.component";
import { createVSRangeSlider } from "./vs-range-slider.component.test-helpers";

/** {t 'HH:mm:ss'} tick values, on the half hour from 00:00 to 23:30. */
const TIME_VALUES: string[] = [];
const TIME_LABELS: string[] = [];

for(let hour = 0; hour < 24; hour++) {
   for(const minute of ["00", "30"]) {
      const hh = String(hour).padStart(2, "0");
      TIME_VALUES.push("{t '" + hh + ":" + minute + ":00'}");
      TIME_LABELS.push(hh + ":" + minute);
   }
}

const timeOfDay = (hhmm: string) => new Date(TIME_BASE_DATE + "T" + hhmm);

describe("VSRangeSlider – time of day (XSchema.TIME) binding", () => {
   beforeEach(() => {
      vi.spyOn(GuiTool, "measureText").mockReturnValue(10);
   });

   afterEach(() => {
      vi.restoreAllMocks();
   });

   // ─── Group 1: extractTimeIncrement ────────────────────────────────────────

   describe("Group 1 – extractTimeIncrement", () => {
      // 🔁 Regression-sensitive: {t ...} and {ts ...} used to collapse to the same "t"
      // increment, which made a time-of-day slider open a datetime-local editor seeded
      // from an Invalid Date.
      it("should map {t ...} to the time-of-day increment and {ts ...} to 't'", () => {
         const { comp } = createVSRangeSlider();

         expect(comp["extractTimeIncrement"]("{t '01:30:00'}")).toBe(TIME_OF_DAY_INCREMENT);
         expect(comp["extractTimeIncrement"]("{ts '2024-01-05 01:30:00'}")).toBe("t");
      });

      it("should keep the year/month/day increments unchanged", () => {
         const { comp } = createVSRangeSlider();

         expect(comp["extractTimeIncrement"]("{y '2024'}")).toBe("y");
         expect(comp["extractTimeIncrement"]("{m '2024-01'}")).toBe("m");
         expect(comp["extractTimeIncrement"]("{d '2024-01-05'}")).toBe("d");
      });

      it("should distinguish time-only from date-time in the unescaped fallback", () => {
         const { comp } = createVSRangeSlider();

         expect(comp["extractTimeIncrement"]("01:30:00")).toBe(TIME_OF_DAY_INCREMENT);
         expect(comp["extractTimeIncrement"]("2024-01-05 01:30:00")).toBe("t");
         expect(comp["extractTimeIncrement"]("2024")).toBe("y");
         expect(comp["extractTimeIncrement"]("2024-01")).toBe("m");
         expect(comp["extractTimeIncrement"]("2024-01-05")).toBe("d");
      });
   });

   // ─── Group 2: label / increment timestamps ────────────────────────────────

   describe("Group 2 – labelToTimestamp and toIncrementTimestamp", () => {
      it("should anchor a {t ...} value to the base date instead of returning NaN", () => {
         const { comp } = createVSRangeSlider();

         const timestamp = comp["labelToTimestamp"](TIME_OF_DAY_INCREMENT, "{t '01:30:00'}");

         expect(Number.isFinite(timestamp)).toBe(true);
         expect(timestamp).toBe(timeOfDay("01:30").getTime());
      });

      it("should return the raw time for a time-of-day Date", () => {
         const { comp } = createVSRangeSlider();
         const date = timeOfDay("06:30");

         expect(comp["toIncrementTimestamp"](TIME_OF_DAY_INCREMENT, date)).toBe(date.getTime());
      });

      it("should keep {ts ...} parsing on the 't' increment", () => {
         const { comp } = createVSRangeSlider();

         expect(comp["labelToTimestamp"]("t", "{ts '2024-01-05 01:30:00'}"))
            .toBe(new Date("2024-01-05T01:30:00").getTime());
      });
   });

   // ─── Group 3 & 4: Edit dialog seeding and commit ──────────────────────────

   describe("Groups 3 & 4 – Edit dialog round-trip", () => {
      /**
       * Stubs ComponentTool.showDialog so the dialog instance the component configures can be
       * inspected, and the commit callback it registers can be fired directly.
       */
      function openEditDialog(overrides: any = {}) {
         const { comp, viewsheetClient } = createVSRangeSlider({
            labels: TIME_LABELS,
            values: TIME_VALUES,
            dataType: XSchema.TIME,
            selectStart: TIME_LABELS.indexOf("01:30"),
            selectEnd: TIME_LABELS.indexOf("06:30"),
            ...overrides,
         }, { viewer: true });

         const dialog: any = { initForm: vi.fn() };
         let commit: (range: { min: number | Date, max: number | Date }) => void;
         const showDialog = vi.spyOn(ComponentTool, "showDialog")
            .mockImplementation((_modal: any, _type: any, onCommit: any) => {
               commit = onCommit;
               return dialog;
            });

         comp["showRangeSliderEditDialog"]();

         expect(showDialog).toHaveBeenCalledTimes(1);

         return { comp, dialog, viewsheetClient, commit: (range: any) => commit(range) };
      }

      it("should use the time-of-day increment and valid Dates for the current selection", () => {
         const { dialog } = openEditDialog();

         expect(dialog.timeIncrement).toBe(TIME_OF_DAY_INCREMENT);
         expect(dialog.currentMin).toEqual(timeOfDay("01:30"));
         expect(dialog.currentMax).toEqual(timeOfDay("06:30"));
         expect(dialog.initForm).toHaveBeenCalledTimes(1);
      });

      it("should seed the dialog range from the first and last tick", () => {
         const { dialog } = openEditDialog();

         expect(dialog.rangeMin).toEqual(timeOfDay("00:00"));
         expect(dialog.rangeMax).toEqual(timeOfDay("23:30"));
      });

      it("should snap a committed time range onto the matching tick indexes", () => {
         const { comp, commit } = openEditDialog({ selectStart: 0, selectEnd: 1 });

         commit({ min: timeOfDay("03:00"), max: timeOfDay("08:30") });

         expect(comp.model.labels[comp.model.selectStart]).toBe("03:00");
         expect(comp.model.labels[comp.model.selectEnd]).toBe("08:30");
      });

      it("should send the updated selection to the server after a commit", () => {
         const { commit, viewsheetClient } = openEditDialog({ selectStart: 0, selectEnd: 1 });

         commit({ min: timeOfDay("03:00"), max: timeOfDay("08:30") });

         expect(viewsheetClient.sendEvent).toHaveBeenCalled();
      });
   });
});
