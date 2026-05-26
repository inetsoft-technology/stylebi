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
 * TimeZoneService — unit tests
 *
 * Risk-first coverage (2 groups, 19 cases):
 *   Group 1 [Risk 3, 3, 3, 3, 2, 2, 2, 2, 2, 2, 2, 2, 2] — updateTimeZoneOptions (13 cases)
 *   Group 2 [Risk 3, 2, 2, 2, 2, 2]                       — calculateTimezoneOffset (6 cases)
 *
 * Confirmed bugs (it.failing — remove wrapper once fixed):
 *   - calculateTimezoneOffset("Invalid/Zone") throws RangeError; no guard in the implementation
 *     Steps to reproduce: N/A via dropdown; possible when opening tasks with legacy/saved timeZone.
 *
 * KEY contracts:
 *   - updateTimeZoneOptions always prepends the local TZ as the first entry (exactly once)
 *   - Same IANA ID + same label → deduped; same IANA ID + different label → both entries coexist
 *   - Mutates and returns the same array reference it received
 *   - calculateTimezoneOffset returns ms offset from UTC; negative = west, positive = east, 0 = UTC
 *
 * Design gaps:
 *   - No guard on invalid IANA IDs passed to calculateTimezoneOffset (RangeError bubbles uncaught)
 */
import { ScheduleConditionModel } from "./model/schedule-condition-model";
import { TimeConditionModel } from "./model/time-condition-model";
import { TimeZoneModel } from "./model/time-zone-model";
import { TimeZoneService } from "./time-zone.service";
import { it } from "@jest/globals";

describe("TimeZoneService", () => {
   let service: TimeZoneService;

   beforeEach(() => {
      service = new TimeZoneService();
   });

   // ---------------------------------------------------------------------------
   // Group 1 [Risk 3, 3, 3, 3, 2, 2, 2, 2, 2, 2, 2, 2, 2] — updateTimeZoneOptions (13 cases)
   // ---------------------------------------------------------------------------
   describe("updateTimeZoneOptions()", () => {
      it("[Risk 2] returns null as-is when timeZoneOptions is null", () => {
         expect(service.updateTimeZoneOptions(null as any, [])).toBeNull();
      });

      it("[Risk 2] returns undefined as-is when timeZoneOptions is undefined", () => {
         expect(service.updateTimeZoneOptions(undefined as any, [])).toBeUndefined();
      });

      it("[Risk 2] prepends the local time zone as the first entry when the list is empty", () => {
         const options: TimeZoneModel[] = [];
         const result = service.updateTimeZoneOptions(options, []);

         expect(result.length).toBe(1);
         expect(result[0].timeZoneId).toBe(Intl.DateTimeFormat().resolvedOptions().timeZone);
         expect(result[0].label).toContain("_#(js:em.scheduler.localtimezone)");
      });

      it("[Risk 3] does not prepend a duplicate local time zone on a second call", () => {
         // 🔁 Regression-sensitive: dedup check is label-based on index 0; second call must not grow the list
         const options: TimeZoneModel[] = [];
         service.updateTimeZoneOptions(options, []);
         const lengthAfterFirst = options.length;

         service.updateTimeZoneOptions(options, []);

         expect(options.length).toBe(lengthAfterFirst);
      });

      it("[Risk 2] adds a missing TimeCondition time zone to the end of the list", () => {
         const options: TimeZoneModel[] = [];
         const condition: TimeConditionModel = {
            conditionType: "TimeCondition",
            label: "",
            type: 0,
            timeZone: "America/Chicago",
            timeZoneLabel: "Central Standard Time (America/Chicago)",
         };

         const result = service.updateTimeZoneOptions(options, [condition]);

         const chicagoEntry = result.find(o => o.timeZoneId === "America/Chicago");
         expect(chicagoEntry).toBeDefined();
         expect(chicagoEntry?.label).toBe("Central Standard Time (America/Chicago)");
      });

      it("[Risk 3] does not duplicate a time zone that is already present with the same label", () => {
         // 🔁 Regression-sensitive: dedup requires both timeZoneId and label to match
         const existing: TimeZoneModel = {
            timeZoneId: "America/Chicago",
            label: "Central Standard Time (America/Chicago)",
            hourOffset: "",
            minuteOffset: 0,
         };
         const options: TimeZoneModel[] = [existing];
         const condition: TimeConditionModel = {
            conditionType: "TimeCondition",
            label: "",
            type: 0,
            timeZone: "America/Chicago",
            timeZoneLabel: "Central Standard Time (America/Chicago)",
         };

         service.updateTimeZoneOptions(options, [condition]);

         expect(options.filter(o => o.timeZoneId === "America/Chicago").length).toBe(1);
      });

      it("[Risk 2] skips non-TimeCondition conditions and does not add their time zones", () => {
         const options: TimeZoneModel[] = [];
         const condition: ScheduleConditionModel = {
            conditionType: "CompletionCondition",
            label: "after task A",
         };

         const result = service.updateTimeZoneOptions(options, [condition]);

         expect(result.length).toBe(1);
      });

      it("[Risk 3] handles a null conditions array without throwing", () => {
         // 🔁 Regression-sensitive: null guard must short-circuit before the conditions loop
         const options: TimeZoneModel[] = [];
         const result = service.updateTimeZoneOptions(options, null as any);

         expect(result.length).toBe(1);
      });

      it("[Risk 2] returns the same array reference it received (mutates in place)", () => {
         const options: TimeZoneModel[] = [];
         const result = service.updateTimeZoneOptions(options, []);

         expect(result).toBe(options);
      });

      it("[Risk 3] allows two entries with the same IANA ID but different labels to coexist", () => {
         // 🔁 Regression-sensitive: dedup uses both id + label; different label must produce a second entry
         const existing: TimeZoneModel = {
            timeZoneId: "America/Chicago",
            label: "Label A",
            hourOffset: "",
            minuteOffset: 0,
         };
         const options: TimeZoneModel[] = [existing];
         const condition: TimeConditionModel = {
            conditionType: "TimeCondition",
            label: "",
            type: 0,
            timeZone: "America/Chicago",
            timeZoneLabel: "Label B",
         };

         service.updateTimeZoneOptions(options, [condition]);

         expect(options.filter(o => o.timeZoneId === "America/Chicago").length).toBe(2);
      });

      it("[Risk 2] skips a TimeCondition whose timeZone field is null", () => {
         const options: TimeZoneModel[] = [];
         const condition: TimeConditionModel = {
            conditionType: "TimeCondition",
            label: "",
            type: 0,
            timeZone: null as any,
            timeZoneLabel: "Some Label",
         };

         const result = service.updateTimeZoneOptions(options, [condition]);

         expect(result.length).toBe(1);
      });

      it("[Risk 2] falls back to getTimeZoneName when timeZoneLabel is null", () => {
         const options: TimeZoneModel[] = [];
         const condition: TimeConditionModel = {
            conditionType: "TimeCondition",
            label: "",
            type: 0,
            timeZone: "America/Chicago",
            timeZoneLabel: null as any,
         };

         const result = service.updateTimeZoneOptions(options, [condition]);

         const chicagoEntry = result.find(o => o.timeZoneId === "America/Chicago");
         expect(chicagoEntry).toBeDefined();
         expect(chicagoEntry?.label).toBeTruthy();
      });

      it("[Risk 2] processes multiple conditions, adding new zones and deduplicating existing ones", () => {
         const options: TimeZoneModel[] = [];
         const conditions: TimeConditionModel[] = [
            { conditionType: "TimeCondition", label: "", type: 0, timeZone: "America/Chicago", timeZoneLabel: "Central" },
            { conditionType: "TimeCondition", label: "", type: 0, timeZone: "America/Chicago", timeZoneLabel: "Central" },
            { conditionType: "TimeCondition", label: "", type: 0, timeZone: "Asia/Tokyo", timeZoneLabel: "Japan" },
         ];

         const result = service.updateTimeZoneOptions(options, conditions);

         // localTZ + Chicago (once) + Tokyo = 3
         expect(result.length).toBe(3);
         expect(result.filter(o => o.timeZoneId === "America/Chicago").length).toBe(1);
         expect(result.find(o => o.timeZoneId === "Asia/Tokyo")).toBeDefined();
      });
   });

   // ---------------------------------------------------------------------------
   // Group 2 [Risk 3, 2, 2, 2, 2, 2] — calculateTimezoneOffset (6 cases)
   // ---------------------------------------------------------------------------
   describe("calculateTimezoneOffset()", () => {
      it("[Risk 2] returns a finite number in milliseconds for a valid IANA time zone ID", () => {
         const offset = service.calculateTimezoneOffset("America/New_York");
         expect(typeof offset).toBe("number");
         expect(isFinite(offset)).toBe(true);
      });

      it("[Risk 2] returns a finite number for an empty timezone ID (falls back to local offset)", () => {
         const offset = service.calculateTimezoneOffset("");
         expect(typeof offset).toBe("number");
         expect(isFinite(offset)).toBe(true);
      });

      it("[Risk 2] returns 0 for the UTC time zone", () => {
         expect(service.calculateTimezoneOffset("UTC")).toBe(0);
      });

      it("[Risk 2] returns a positive offset for time zones east of UTC (e.g. Asia/Tokyo UTC+9)", () => {
         expect(service.calculateTimezoneOffset("Asia/Tokyo")).toBeGreaterThan(0);
      });

      it("[Risk 2] returns a negative offset for time zones west of UTC (e.g. America/Los_Angeles UTC-7/8)", () => {
         expect(service.calculateTimezoneOffset("America/Los_Angeles")).toBeLessThan(0);
      });

      // toLocaleString() throws RangeError for unknown timezone IDs; no guard in the implementation
      // Steps to reproduce: N/A via dropdown; possible when opening tasks with legacy/saved timeZone.
      it.failing("[Risk 3] does not throw for an invalid IANA timezone ID (no error handling)", () => {
         expect(() => service.calculateTimezoneOffset("Invalid/Zone")).not.toThrow();
      });
   });
});
