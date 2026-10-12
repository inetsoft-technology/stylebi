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
 * getTimeZoneOffset — unit tests (Bug #78045)
 *
 * The schedule editors used to compute a zone offset with
 * new Date(date.toLocaleString([], {timeZone})), which formats with the browser's default locale.
 * In a day-first locale (en-GB) that is NaN once the day of month is 13 or later, about a month off
 * when UTC and the zone are on different days, and NaN on every day in ko-KR. getTimeZoneOffset
 * must give the same result whatever the default locale is.
 *
 * The default locale is forced by wrapping Date.prototype.toLocaleString for an empty/undefined
 * locale, which is what the old round-trip used. A control case checks that the forcing breaks the
 * old round-trip, so the locale cases are not vacuous.
 */
import { getTimeZoneOffset } from "./time-zone-offset";

const MINUTE = 60000;

function forceDefaultLocale(locale: string): void {
   const nativeToLocaleString = Date.prototype.toLocaleString;

   vi.spyOn(Date.prototype, "toLocaleString").mockImplementation(
      function(this: Date, locales?: Intl.LocalesArgument, options?: Intl.DateTimeFormatOptions) {
         const useDefault = locales == null || (Array.isArray(locales) && locales.length === 0);
         return nativeToLocaleString.call(this, useDefault ? locale : locales, options);
      });
}

// the round-trip the schedule editors used before the fix, in minutes
function oldRoundTripOffset(tzId: string, at: Date): number {
   const utc = new Date(at.toLocaleString([], { timeZone: "UTC" }));
   const zone = new Date(at.toLocaleString([], { timeZone: tzId }));
   return (zone.getTime() - utc.getTime()) / MINUTE;
}

describe("getTimeZoneOffset", () => {
   afterEach(() => {
      vi.restoreAllMocks();
   });

   const DAY_20 = new Date(Date.UTC(2026, 9, 20, 3, 0));        // 20th in UTC and in Bangkok
   const DAY_5 = new Date(Date.UTC(2026, 9, 5, 3, 0));          // 5th in UTC and in Bangkok
   const CROSS_DAY_12 = new Date(Date.UTC(2026, 9, 12, 20, 0)); // 12th UTC, 13th in Bangkok
   const CROSS_DAY_5 = new Date(Date.UTC(2026, 9, 5, 23, 30));  // 5th UTC, 6th in Bangkok

   it("control: a forced en-GB default locale breaks the old round-trip on day 20", () => {
      forceDefaultLocale("en-GB");
      expect(oldRoundTripOffset("Asia/Bangkok", DAY_20)).toBeNaN();
      expect(oldRoundTripOffset("Asia/Bangkok", CROSS_DAY_5)).toBe(43620);
   });

   it("control: a forced ko-KR default locale breaks the old round-trip on every day", () => {
      forceDefaultLocale("ko-KR");
      expect(oldRoundTripOffset("Asia/Bangkok", DAY_5)).toBeNaN();
   });

   for(const locale of ["en-US", "en-GB", "de-DE", "th-TH", "ko-KR"]) {
      describe(`with default locale ${locale}`, () => {
         beforeEach(() => forceDefaultLocale(locale));

         it("returns +7h for Asia/Bangkok on day 20", () => {
            expect(getTimeZoneOffset("Asia/Bangkok", DAY_20)).toBe(420 * MINUTE);
         });

         it("returns +7h for Asia/Bangkok on day 5", () => {
            expect(getTimeZoneOffset("Asia/Bangkok", DAY_5)).toBe(420 * MINUTE);
         });

         it("returns +7h when UTC and the zone are on different days", () => {
            expect(getTimeZoneOffset("Asia/Bangkok", CROSS_DAY_12)).toBe(420 * MINUTE);
            expect(getTimeZoneOffset("Asia/Bangkok", CROSS_DAY_5)).toBe(420 * MINUTE);
         });

         it("returns 0 for UTC", () => {
            expect(getTimeZoneOffset("UTC", DAY_20)).toBe(0);
         });

         it("returns half-hour and quarter-hour offsets", () => {
            expect(getTimeZoneOffset("Asia/Kolkata", DAY_20)).toBe(330 * MINUTE);
            expect(getTimeZoneOffset("Asia/Kathmandu", DAY_20)).toBe(345 * MINUTE);
         });

         it("returns a west offset across a day boundary (America/St_Johns, -2:30 in DST)", () => {
            // 2026-10-20T01:00Z is still 10-19 in St. John's
            expect(getTimeZoneOffset("America/St_Johns", new Date(Date.UTC(2026, 9, 20, 1, 0))))
               .toBe(-150 * MINUTE);
         });
      });
   }

   it("returns the offset at the given instant, across a DST change", () => {
      // America/New_York switches from EST to EDT at 2026-03-08T07:00Z
      expect(getTimeZoneOffset("America/New_York", new Date(Date.UTC(2026, 2, 8, 6, 59))))
         .toBe(-300 * MINUTE);
      expect(getTimeZoneOffset("America/New_York", new Date(Date.UTC(2026, 2, 8, 7, 0))))
         .toBe(-240 * MINUTE);
      expect(getTimeZoneOffset("America/St_Johns", new Date(Date.UTC(2026, 10, 20, 3, 0))))
         .toBe(-210 * MINUTE);
   });

   it("handles midnight and the last millisecond of the day", () => {
      expect(getTimeZoneOffset("UTC", new Date(Date.UTC(2026, 9, 20, 0, 5)))).toBe(0);
      expect(getTimeZoneOffset("Asia/Tokyo", new Date(Date.UTC(2026, 9, 20, 15, 0)))).toBe(540 * MINUTE);
      expect(getTimeZoneOffset("Asia/Bangkok", new Date(Date.UTC(2026, 9, 20, 23, 59, 59, 999))))
         .toBe(420 * MINUTE);
   });

   it("uses the browser's local zone for an empty, null or undefined id", () => {
      const local = (-DAY_20.getTimezoneOffset() * MINUTE) || 0;
      expect(getTimeZoneOffset("", DAY_20)).toBe(local);
      expect(getTimeZoneOffset(null, DAY_20)).toBe(local);
      expect(getTimeZoneOffset(undefined, DAY_20)).toBe(local);
   });

   it("throws for an id Intl does not know, as before the fix", () => {
      expect(() => getTimeZoneOffset("Invalid/Zone", DAY_20)).toThrow(RangeError);
   });
});
