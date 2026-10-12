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
 * Returns the offset of a time zone from UTC at the given instant, in milliseconds east of UTC
 * (e.g. 25200000 for Asia/Bangkok, -18000000 for America/New_York in winter).
 *
 * The wall time of the zone is read from Intl.DateTimeFormat parts with a fixed "en-US"
 * (Gregorian) format, so the result does not depend on the browser's display language. Do not
 * compute an offset by parsing toLocaleString() output with new Date(): the default locale's date
 * order breaks the parse (Bug #78045).
 *
 * @param timeZoneId the time zone id. An empty, null or undefined id means the browser's local
 *                   time zone.
 * @param at         the instant to compute the offset at. Defaults to now.
 */
export function getTimeZoneOffset(timeZoneId: string, at: Date = new Date()): number {
   if(!timeZoneId) {
      return (-at.getTimezoneOffset() * 60000) || 0;
   }

   // hourCycle h23 (without hour12, which would override it) gives hours 0-23; % 24 also guards
   // engines that write midnight as "24"
   const parts = new Intl.DateTimeFormat("en-US", {
      timeZone: timeZoneId,
      hourCycle: "h23",
      year: "numeric",
      month: "numeric",
      day: "numeric",
      hour: "numeric",
      minute: "numeric",
      second: "numeric"
   }).formatToParts(at);
   const part = (type: Intl.DateTimeFormatPartTypes): number =>
      parseInt(parts.find(p => p.type === type)?.value, 10);
   const wallTime = Date.UTC(part("year"), part("month") - 1, part("day"),
      part("hour") % 24, part("minute"), part("second"));

   // the parts have no milliseconds, so round to the minute; || 0 turns -0 into 0
   return (Math.round((wallTime - at.getTime()) / 60000) * 60000) || 0;
}
