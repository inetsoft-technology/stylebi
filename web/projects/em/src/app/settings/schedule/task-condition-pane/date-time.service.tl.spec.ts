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
 *   Group 6 [Risk 3] — applyTimeZoneOffsetDifference / applyDateTimeZoneOffsetDifference treat an
 *                       empty oldTZ as the local zone (used to throw RangeError)
 *   Group 7 [Risk 3] — Bug #78045: offsets do not depend on the browser's default locale
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
 * Group 6 (fixed with Bug #78045):
 *   When only ONE timezone is empty (e.g. oldTZ="" and newTZ="America/New_York"), the methods used
 *   to call `toLocaleString([], { timeZone: "" })`, which throws RangeError. The offsets now come
 *   from getTimeZoneOffset(), which treats an empty zone as the browser's local zone, the same as
 *   getLocalTimezoneOffset() always did.
 */

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

describe("DateTimeService — empty oldTZ is treated as the local zone", () => {

   // Boundary / defensive only (legacy data): normal "create new condition" UI initializes
   // TimeCondition.timeZone from timeZoneOptions[0] (non-empty). oldTZ=="" mainly comes from
   // upgrade/legacy tasks persisted before TZ support was added.
   // Trigger path in production:
   //   1. User opens an existing schedule task saved before TZ support was added (timeZone="").
   //   2. User selects any timezone in the dropdown.
   //   3. fireModelChanged() captures oldTZ="" and newTZ="America/New_York".
   //   4. updateStartTimeDataTimeZone → applyTimeZoneOffsetDifference("10:00:00", "", "America/New_York")
   // This used to throw RangeError; the empty side now means the browser's local zone.
   it("should not throw when oldTZ is empty string and newTZ is a valid timezone", () => {
      expect(() => service.applyTimeZoneOffsetDifference("10:00:00", "", "America/New_York"))
         .not.toThrow();
   });

   // Boundary / defensive only: the same for applyDateTimeZoneOffsetDifference, whose guard is
   // `oldTZ==null && newTZ==null` (null-only).
   it("applyDateTimeZoneOffsetDifference: should not throw when oldTZ='' and newTZ is valid", () => {
      const dateValue = new Date(2026, 3, 8, 10, 0, 0);
      expect(() => service.applyDateTimeZoneOffsetDifference("10:00:00", "", "America/New_York", dateValue))
         .not.toThrow();
   });

});

// ---------------------------------------------------------------------------
// Group 7 [Risk 3] — Bug #78045: offsets do not depend on the browser's default locale
// ---------------------------------------------------------------------------

describe("DateTimeService — Bug #78045: offsets with a non-US default locale", () => {

   // The old code parsed toLocaleString([], {timeZone}) back with new Date(). With a day-first
   // default locale that is NaN from the 13th (applyTimeZoneOffsetDifference returned null, and
   // applyDateTimeZoneOffsetDifference an Invalid Date, even for the same zone), and NaN every day
   // with ko-KR. The default locale is forced by wrapping Date.prototype.toLocaleString.
   function forceDefaultLocale(locale: string): void {
      const nativeToLocaleString = Date.prototype.toLocaleString;

      vi.spyOn(Date.prototype, "toLocaleString").mockImplementation(
         function(this: Date, locales?: Intl.LocalesArgument, options?: Intl.DateTimeFormatOptions) {
            const useDefault = locales == null || (Array.isArray(locales) && locales.length === 0);
            return nativeToLocaleString.call(this, useDefault ? locale : locales, options);
         });
   }

   function setNow(utcMillis: number): void {
      vi.useFakeTimers({ toFake: ["Date"] });
      vi.setSystemTime(utcMillis);
   }

   afterEach(() => {
      vi.useRealTimers();
      vi.restoreAllMocks();
   });

   const cases: [string, string, number][] = [
      ["en-US (control)", "en-US", Date.UTC(2026, 9, 20, 3, 0)],
      ["en-GB on day 20", "en-GB", Date.UTC(2026, 9, 20, 3, 0)],
      ["de-DE on day 20", "de-DE", Date.UTC(2026, 9, 20, 3, 0)],
      ["en-GB, 5th in UTC but 6th in Bangkok", "en-GB", Date.UTC(2026, 9, 5, 23, 30)],
      ["ko-KR on day 5", "ko-KR", Date.UTC(2026, 9, 5, 3, 0)],
   ];

   for(const [name, locale, now] of cases) {
      describe(name, () => {
         beforeEach(() => {
            forceDefaultLocale(locale);
            setNow(now);
         });

         it("applyTimeZoneOffsetDifference keeps the time for the same zone", () => {
            expect(service.applyTimeZoneOffsetDifference("09:30:00", "Asia/Bangkok", "Asia/Bangkok"))
               .toBe("09:30:00");
         });

         it("applyTimeZoneOffsetDifference shifts by the zone difference (half-hour zone)", () => {
            expect(service.applyTimeZoneOffsetDifference("10:00:00", "UTC", "Asia/Kolkata"))
               .toBe("15:30:00");
            expect(service.applyTimeZoneOffsetDifference("09:30:00", "Asia/Bangkok", "Asia/Kolkata"))
               .toBe("08:00:00");
         });

         it("applyDateTimeZoneOffsetDifference keeps the date and time for the same zone", () => {
            const result = service.applyDateTimeZoneOffsetDifference("09:30:00", "Asia/Bangkok",
               "Asia/Bangkok", new Date(2026, 10, 20));
            expect(result.getTime()).toBe(new Date(2026, 10, 20, 9, 30).getTime());
         });

         it("applyDateTimeZoneOffsetDifference moves to the next day across midnight", () => {
            // 22:00 UTC is 05:00 the next day in Bangkok
            const result = service.applyDateTimeZoneOffsetDifference("22:00:00", "UTC",
               "Asia/Bangkok", new Date(2026, 10, 20));
            expect(result.getFullYear()).toBe(2026);
            expect(result.getMonth()).toBe(10);
            expect(result.getDate()).toBe(21);
            expect(service.getTimeString(result)).toBe("05:00:00");
         });

         it("getLocalTimezoneOffset returns UTC minus the zone", () => {
            expect(service.getLocalTimezoneOffset("Asia/Bangkok")).toBe(-420 * 60000);
            expect(service.getLocalTimezoneOffset("UTC")).toBe(0);
         });
      });
   }

});
