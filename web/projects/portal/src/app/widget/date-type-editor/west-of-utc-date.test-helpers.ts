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

const RealDate = Date;
const DATE_ONLY = /^(\d{4})-(\d{2})-(\d{2})$/;

/**
 * A Date that behaves like a browser west of UTC for date-only ISO strings, in any time zone.
 * new Date("yyyy-MM-dd") is UTC midnight, which is the previous local day west of UTC, so this
 * returns noon of the previous local day for such a string. Every other call is the real Date.
 *
 * Setting process.env.TZ inside a spec has no effect under the Angular Vitest runner, and CI
 * runs in UTC, so a spec that must fail on the west-of-UTC bug installs this with
 * stubWestOfUtcDate() instead.
 */
class WestOfUtcDate extends RealDate {
   constructor(...args: any[]) {
      const match = args.length === 1 && typeof args[0] === "string" ? DATE_ONLY.exec(args[0]) : null;

      if(match) {
         super(+match[1], +match[2] - 1, +match[3] - 1, 12);
      }
      else {
         super(...(args as []));
      }
   }
}

/**
 * Replace the global Date with WestOfUtcDate. Undo it with vi.unstubAllGlobals().
 */
export function stubWestOfUtcDate(): void {
   vi.stubGlobal("Date", WestOfUtcDate);
}
