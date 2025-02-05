/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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


import { Injectable } from "@angular/core";
import { ScheduleConditionModel } from "./model/schedule-condition-model";
import { TimeConditionModel } from "./model/time-condition-model";
import { TimeZoneModel } from "./model/time-zone-model";

@Injectable({
   providedIn: "root"
})
export class TimeZoneService {
   /**
    * Add the local (client) time zone option and add the time zones that are in the conditions
    * that are missing
    * @param timeZoneOptions
    */
   public updateTimeZoneOptions(timeZoneOptions: TimeZoneModel[],
                                conditions: ScheduleConditionModel[]): TimeZoneModel[]
   {
      if(!timeZoneOptions) {
         return timeZoneOptions;
      }

      // add local time zone
      const localTimeZoneId = Intl.DateTimeFormat().resolvedOptions().timeZone;

      const localTimeZone = <TimeZoneModel>{
         timeZoneId: localTimeZoneId,
         label: this.getTimeZoneName(localTimeZoneId) + " (_#(js:em.scheduler.localtimezone))",
         hourOffset: ""
      };

      if(timeZoneOptions[0].label != localTimeZone.label) {
         timeZoneOptions.unshift(localTimeZone);
      }

      if(!conditions) {
         return timeZoneOptions;
      }

      // add missing time zones
      for(let condition of conditions) {
         if(!condition || condition.conditionType != "TimeCondition") {
            continue;
         }

         const condTimeZoneId = (condition as TimeConditionModel).timeZone;

         // check if id already exists in the list
         if(!condTimeZoneId ||
            timeZoneOptions.find((opt) => opt.timeZoneId == condTimeZoneId))
         {
            continue;
         }

         // add missing time zone to the list of available choices
         // for example a user added a condition with a local time zone option and then opened
         // the same task in another time zone, making the time zone option no longer available
         const missingTimeZone = <TimeZoneModel>{
            timeZoneId: condTimeZoneId,
            label: this.getTimeZoneName(condTimeZoneId),
            hourOffset: this.getUTCOffset(condTimeZoneId)
         };

         timeZoneOptions.push(missingTimeZone);
      }

      return timeZoneOptions;
   }

   /**
    * Calculates the time zone offset from utc in milliseconds
    * @param tzId time zone id
    */
   public calculateTimezoneOffset(tzId: string): number {
      let offset = 0;
      let date = new Date();

      if(!!tzId) {
         const UTC = new Date(date.toLocaleString([], { timeZone: "UTC" }));
         const selectedTZ = new Date(date.toLocaleString([], { timeZone: tzId }));
         offset = (selectedTZ.getTime() - UTC.getTime());
      }
      else {
         offset = -new Date().getTimezoneOffset() * 60000;
      }

      return offset;
   }

   private getUTCOffset(timeZoneId: string): string {
      const now = new Date();

      // calculate UTC offset by computing milliseconds difference
      const utcDate = new Date(now.toLocaleString("en-US", {timeZone: "UTC"}));
      const tzDate = new Date(now.toLocaleString("en-US", {timeZone: timeZoneId}));
      const offsetMinutes = (tzDate.getTime() - utcDate.getTime()) / 60000;

      // calculate hours and minutes from offset
      const offsetHours = Math.floor(Math.abs(offsetMinutes) / 60);
      const minutes = Math.abs(offsetMinutes) % 60;
      const sign = offsetMinutes >= 0 ? "+" : "-";

      return `(UTC${sign}${String(offsetHours).padStart(2, "0")}:${String(minutes).padStart(2, "0")})`;
   }

   private getTimeZoneName(timeZoneId: string) {
      const formatter = new Intl.DateTimeFormat(undefined, {
         timeZone: timeZoneId,
         timeZoneName: "long"
      });

      const parts = formatter.formatToParts(new Date());
      return parts.find(part => part.type === "timeZoneName")?.value;
   }
}