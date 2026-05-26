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
 * DateTimeService — pure unit tests (no Angular render required)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — setStartTime / getStartTime: HH:mm:ss parse-format round-trip
 *   Group 2 [Risk 2] — validateTimeValue: ":NaN" replacement and passthrough
 *   Group 3 [Risk 2] — getOffsetDate: epoch + offset arithmetic with UTC timezone
 *   Group 4 [Risk 2] — early returns: applyTimeZoneOffsetDifference (both empty) and
 *                       updateStartTimeDataTimeZone (both null)
 *   Group 5 [Risk 3] — applyTimeZoneOffsetDifference: real TZ shifts via Asia/Kolkata (no DST)
 *   Group 6 [Risk 3] — Bug: applyTimeZoneOffsetDifference / applyDateTimeZoneOffsetDifference crash
 *                       when oldTZ="" (empty) and newTZ is a valid timezone string
 *
 * KEY contracts:
 *   - setStartTime parses "HH:mm:ss" → condition.hour / minute / second.
 *   - getStartTime overrides date.h/m/s with condition.hour/minute/second, so the
 *     returned string equals the stored field values regardless of timezone offset.
 *   - getOffsetDate(date, tzOffset, "UTC") = new Date(date + tzOffset) because the
 *     UTC localOffset is 0 (UTC - UTC = 0).
 *   - applyTimeZoneOffsetDifference returns value UNCHANGED when
 *     Tool.isEmpty(oldTZ) && Tool.isEmpty(newTZ) (lodash isEmpty: "" → true, null → true).
 *   - updateStartTimeDataTimeZone returns startTimeData UNCHANGED (same reference)
 *     when oldTZ === null && newTZ === null.
 *   - applyTimeZoneOffsetDifference(t, TZ, TZ) = t (same TZ both sides → shift is 0).
 *   - Asia/Kolkata (IST) has no DST and is permanently UTC+5:30.  The delta is computed as
 *     (IST_localParse - UTC_localParse), which cancels out the test machine's local offset,
 *     making the +5h30m / -5h30m arithmetic deterministic across all environments.
 *   - updateStartTimeDataTimeZone always returns a NEW object (not the same reference) when a
 *     timezone shift is applied; timeRange and startTimeSelected are preserved unchanged.
 *
 * CONFIRMED BUG (Group 6):
 *   applyTimeZoneOffsetDifference early-return guard is `Tool.isEmpty(oldTZ) && Tool.isEmpty(newTZ)`.
 *   When only ONE timezone is empty (e.g. oldTZ="" and newTZ="America/New_York"), the guard is
 *   false, so execution falls through to `toLocaleString([], { timeZone: "" })` which throws
 *   RangeError: Invalid time zone specified: .
 *   Trigger: user opens an existing task saved before TZ support (timeZone="") and selects a TZ.
 *   Compare: getLocalTimezoneOffset() handles empty string with `if(!!timeZoneId)` — consistent
 *   fix would be to use the same guard in applyTimeZoneOffsetDifference.
 *   applyDateTimeZoneOffsetDifference has an analogous bug (guard is `oldTZ==null && newTZ==null`
 *   which also does not exclude the empty-string case).
 */

import { it } from "@jest/globals";

import { DateTimeService } from "./date-time.service";
import { TimeConditionModel, TimeConditionType } from "../../../../../../shared/schedule/model/time-condition-model";
import { StartTimeData } from "./start-time-editor/start-time-editor.component";

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

function makeCondition(overrides: Partial<TimeConditionModel> = {}): TimeConditionModel {
   return {
      type: TimeConditionType.EVERY_DAY,
      conditionType: "TimeCondition",
      label: "",
      hour: 0,
      minute: 0,
      second: 0,
      date: 0,
      timeZoneOffset: 0,
      ...overrides,
   };
}

function makeStartTimeData(overrides: Partial<StartTimeData> = {}): StartTimeData {
   return {
      startTime: "08:00:00",
      timeRange: null,
      startTimeSelected: true,
      ...overrides,
   };
}

// ---------------------------------------------------------------------------
// Setup
// ---------------------------------------------------------------------------

let service: DateTimeService;

beforeEach(() => {
   service = new DateTimeService();
});

// ---------------------------------------------------------------------------
// Group 1 [Risk 3] — setStartTime / getStartTime: round-trip
// ---------------------------------------------------------------------------

describe("DateTimeService — setStartTime / getStartTime round-trip", () => {

   // 🔁 Regression-sensitive: setStartTime parses the ISO time string and writes h/m/s fields;
   // getStartTime re-formats them.  Any mismatch causes the scheduler to store one time and
   // display a different time — a silent data-corruption bug for the user.
   it("should recover the original HH:mm:ss string through a set/get round-trip", () => {
      const condition = makeCondition();
      service.setStartTime("08:30:45", condition);

      expect(condition.hour).toBe(8);
      expect(condition.minute).toBe(30);
      expect(condition.second).toBe(45);
      expect(service.getStartTime(condition)).toBe("08:30:45");
   });

   // 🔁 Regression-sensitive: midnight "00:00:00" is a common default and boundary value.
   // If leading zeros are dropped during format, the stored and displayed times diverge.
   it("should correctly handle midnight (00:00:00) in the round-trip", () => {
      const condition = makeCondition();
      service.setStartTime("00:00:00", condition);

      expect(service.getStartTime(condition)).toBe("00:00:00");
   });

   // Boundary: last second of the day tests that no component overflows or rolls over.
   it("should correctly handle the last second of the day (23:59:59)", () => {
      const condition = makeCondition();
      service.setStartTime("23:59:59", condition);

      expect(condition.hour).toBe(23);
      expect(condition.minute).toBe(59);
      expect(condition.second).toBe(59);
      expect(service.getStartTime(condition)).toBe("23:59:59");
   });

});

// ---------------------------------------------------------------------------
// Group 2 [Risk 2] — validateTimeValue: ":NaN" replacement
// ---------------------------------------------------------------------------

describe("DateTimeService — validateTimeValue: ':NaN' replacement", () => {

   // 🔁 Regression-sensitive: invalid date arithmetic can produce "HH:MM:NaN" strings.
   // If these reach the form or the save payload, downstream parsing fails silently.
   it("should replace ':NaN' with ':00' in a time string", () => {
      expect(service.validateTimeValue("08:30:NaN")).toBe("08:30:00");
   });

   // Happy: a well-formed time string must pass through unchanged.
   it("should return the string unchanged when no ':NaN' is present", () => {
      expect(service.validateTimeValue("08:30:00")).toBe("08:30:00");
   });

});

// ---------------------------------------------------------------------------
// Group 3 [Risk 2] — getOffsetDate with UTC timezone
// ---------------------------------------------------------------------------

describe("DateTimeService — getOffsetDate: epoch + offset arithmetic (UTC timezone)", () => {

   // 🔁 Regression-sensitive: with timeZoneId="UTC" the local offset is 0 (UTC − UTC = 0),
   // so getOffsetDate(date, tzOffset, "UTC") = new Date(date + tzOffset).
   // Any sign error in the arithmetic causes displayed dates to be shifted by hours.
   it("should return the epoch when date=0, timeZoneOffset=0, and timeZoneId is UTC", () => {
      const result = service.getOffsetDate(0, 0, "UTC");
      expect(result.getTime()).toBe(0);
   });

   // Boundary: a positive timeZoneOffset must shift the result date forward by the same amount.
   it("should shift the date forward by a positive timeZoneOffset", () => {
      const oneHour = 3_600_000;
      const result = service.getOffsetDate(0, oneHour, "UTC");
      expect(result.getTime()).toBe(oneHour);
   });

});

// ---------------------------------------------------------------------------
// Group 4 [Risk 2] — early returns for empty / null timezone arguments
// ---------------------------------------------------------------------------

describe("DateTimeService — early returns for empty / null timezone arguments", () => {

   // 🔁 Regression-sensitive: when no timezone is configured (both null), no time adjustment
   // should be made.  Removing this guard would cause a runtime error (toLocaleString with null).
   it("should return startTimeData unchanged (same reference) when both timezones are null", () => {
      const data = makeStartTimeData({ startTime: "09:15:00" });
      const result = service.updateStartTimeDataTimeZone(data, null, null);
      expect(result).toBe(data);
   });

   // 🔁 Regression-sensitive: when both timezones are empty strings (lodash isEmpty("") = true),
   // applyTimeZoneOffsetDifference must return the value unchanged — no toLocaleString call
   // is made, avoiding a RangeError for invalid timezone identifier "".
   it("should return the time value unchanged when both oldTZ and newTZ are empty strings", () => {
      const result = service.applyTimeZoneOffsetDifference("10:00:00", "", "");
      expect(result).toBe("10:00:00");
   });

});

// ---------------------------------------------------------------------------
// Group 5 [Risk 3] — applyTimeZoneOffsetDifference: real timezone shifts
// ---------------------------------------------------------------------------

describe("DateTimeService — applyTimeZoneOffsetDifference: real timezone shifts (Asia/Kolkata = UTC+5:30, no DST)", () => {

   // 🔁 Regression-sensitive: when oldTZ === newTZ the computed delta is always 0.  Violating
   // this would silently drift a task's stored time by the double-offset amount on every save.
   it("should return time unchanged when oldTZ and newTZ are the same non-empty timezone", () => {
      expect(service.applyTimeZoneOffsetDifference("10:00:00", "Asia/Kolkata", "Asia/Kolkata"))
         .toBe("10:00:00");
   });

   // 🔁 Regression-sensitive: UTC→IST(+5:30) must advance the time by exactly 5h30m.
   // This mirrors what the UI does when the user changes a task's timezone — the displayed
   // start time shifts forward to represent the same absolute UTC moment in the new zone.
   // Asia/Kolkata is chosen because its UTC+5:30 offset is DST-free and constant year-round,
   // making the arithmetic deterministic regardless of test environment or calendar date.
   it("should advance time by 5h30m when converting from UTC to Asia/Kolkata", () => {
      expect(service.applyTimeZoneOffsetDifference("10:00:00", "UTC", "Asia/Kolkata"))
         .toBe("15:30:00");
   });

   // 🔁 Regression-sensitive: IST→UTC must subtract 5h30m (inverse of the forward shift).
   // A sign error in the arithmetic would make the round-trip asymmetric, silently shifting
   // the stored schedule by 11 hours after a round-trip through the timezone picker.
   it("should shift time back by 5h30m when converting from Asia/Kolkata to UTC", () => {
      expect(service.applyTimeZoneOffsetDifference("15:30:00", "Asia/Kolkata", "UTC"))
         .toBe("10:00:00");
   });

   // 🔁 Regression-sensitive: updateStartTimeDataTimeZone must return a NEW StartTimeData
   // object (not mutate the original) and carry timeRange / startTimeSelected unchanged.
   // A reference reuse would leave the caller's copy stale after the next fireModelChanged cycle.
   it("should return a new StartTimeData with shifted startTime and all other fields preserved", () => {
      const data = makeStartTimeData({ startTime: "10:00:00", startTimeSelected: true });
      const result = service.updateStartTimeDataTimeZone(data, "UTC", "Asia/Kolkata");

      expect(result).not.toBe(data);                      // new object — not same reference
      expect(result.startTime).toBe("15:30:00");          // time shifted forward by 5h30m
      expect(result.timeRange).toBe(data.timeRange);      // null preserved
      expect(result.startTimeSelected).toBe(true);        // flag preserved
   });

});

// ---------------------------------------------------------------------------
// Group 6 [Risk 3] — Bug: empty-string oldTZ crashes toLocaleString
// ---------------------------------------------------------------------------

describe("DateTimeService — Bug: applyTimeZoneOffsetDifference crashes when oldTZ='' and newTZ is valid", () => {

   // Boundary / defensive only (legacy data): normal "create new condition" UI initializes
   // TimeCondition.timeZone from timeZoneOptions[0] (non-empty). oldTZ=="" mainly comes from
   // upgrade/legacy tasks persisted before TZ support was added.
   // Known bug: the early-return guard is `Tool.isEmpty(oldTZ) && Tool.isEmpty(newTZ)`.
   // When only ONE side is empty (oldTZ="" from a legacy condition, newTZ="America/New_York"),
   // the guard is false and execution falls through to:
   //   new Date(date.toLocaleString([], { timeZone: "" }))
   // which throws RangeError: Invalid time zone specified:
   //
   // Trigger path in production:
   //   1. User opens an existing schedule task saved before TZ support was added (timeZone="").
   //   2. User selects any timezone in the dropdown.
   //   3. fireModelChanged() captures oldTZ="" and newTZ="America/New_York".
   //   4. updateStartTimeDataTimeZone → applyTimeZoneOffsetDifference("10:00:00", "", "America/New_York") → 💥
   //
   // Fix: mirror getLocalTimezoneOffset's guard: `if(!!oldTZ)` before calling toLocaleString,
   // treating empty string as "system timezone" (or as "no conversion needed from empty side").
   it.failing("should not throw when oldTZ is empty string and newTZ is a valid timezone (bug: RangeError)", () => {
      // This currently throws: RangeError: Invalid time zone specified:
      expect(() => service.applyTimeZoneOffsetDifference("10:00:00", "", "America/New_York"))
         .not.toThrow();
   });

   // Boundary / defensive only: analogous behavior in applyDateTimeZoneOffsetDifference — its guard
   // is `oldTZ==null && newTZ==null` (null-only), so oldTZ=="" also falls through and crashes.
   it.failing("applyDateTimeZoneOffsetDifference: should not throw when oldTZ='' and newTZ is valid (bug: RangeError)", () => {
      const dateValue = new Date(2026, 3, 8, 10, 0, 0);
      expect(() => service.applyDateTimeZoneOffsetDifference("10:00:00", "", "America/New_York", dateValue))
         .not.toThrow();
   });

});
