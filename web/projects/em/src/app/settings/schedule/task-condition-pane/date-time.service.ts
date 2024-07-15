/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
import { TimeConditionModel } from "../../../../../../shared/schedule/model/time-condition-model";
import { TimeZoneModel } from "../../../../../../shared/schedule/model/time-zone-model";
import { DateTypeFormatter } from "../../../../../../shared/util/date-type-formatter";
import { StartTimeData } from "./start-time-editor/start-time-editor.component";

@Injectable()
export class DateTimeService {
   constructor() {
   }

   getStartTime(condition: TimeConditionModel): string {
      const date = this.getOffsetDate(condition.date || 0, condition.timeZoneOffset || 0);
      date.setHours(condition.hour);
      date.setMinutes(condition.minute);
      date.setSeconds(condition.second);
      return this.getTimeString(date);
   }

   setStartTime(value: string, condition: TimeConditionModel): void {
      const time = DateTypeFormatter.toTimeInstant(value, DateTypeFormatter.ISO_8601_TIME_FORMAT);
      condition.hour = time.hours;
      condition.minute = time.minutes;
      condition.second = time.seconds;
   }

   getEndTime(condition: TimeConditionModel): string {
      const date = this.getOffsetDate(condition.date || 0, condition.timeZoneOffset || 0);
      date.setHours(condition.hourEnd);
      date.setMinutes(condition.minuteEnd);
      date.setSeconds(condition.secondEnd);
      return this.getTimeString(date);
   }

   setEndTime(value: string, condition: TimeConditionModel): void {
      const time = DateTypeFormatter.toTimeInstant(value, DateTypeFormatter.ISO_8601_TIME_FORMAT);
      condition.hourEnd = time.hours;
      condition.minuteEnd = time.minutes;
      condition.secondEnd = time.seconds;
   }

   getDate(condition: TimeConditionModel): Date {
      return this.getOffsetDate(condition.date, condition.timeZoneOffset, condition.timeZone);
   }

   setDate(date: string, time: string, condition: TimeConditionModel): void {
      const dateValue = DateTypeFormatter.toTimeInstant(date, DateTypeFormatter.ISO_8601_DATE_FORMAT);
      const timeValue = DateTypeFormatter.toTimeInstant(time, DateTypeFormatter.ISO_8601_TIME_FORMAT);
      const compiled = new Date(
         dateValue.years, dateValue.months, dateValue.date,
         timeValue.hours, timeValue.minutes, timeValue.seconds);
      condition.date = compiled.getTime();

      if(condition.timeZone == null) {
         condition.timeZoneOffset = this.getLocalTimezoneOffset(condition.timeZone);
      }

      condition.changed = true;
   }

   /**
    * Return the offset date, to make sure the time from server display same in client.
    */
   getOffsetDate(date: number, timeZoneOffset: number, timeZoneId: string = null): Date {
      const localOffset = !!timeZoneId ?
         this.getLocalTimezoneOffset(timeZoneId) :
         new Date().getTimezoneOffset() * 60000;
      const time = date + (localOffset + timeZoneOffset);
      return new Date(time);
   }

   getLocalTimezoneOffset(timeZoneId: string): number {
      let localTimeZoneOffset;
      let date = new Date();

      if(!!timeZoneId) {
         const UTC = new Date(date.toLocaleString([], { timeZone: "UTC" }));
         const selectedTZ = new Date(date.toLocaleString([], { timeZone: timeZoneId }));
         localTimeZoneOffset = (UTC.getTime() - selectedTZ.getTime());
      }
      else {
         localTimeZoneOffset = new Date().getTimezoneOffset() * 60000;
      }

      return localTimeZoneOffset;
   }

   getTimeZoneLabel(timeZoneOptions: TimeZoneModel[], timeZoneID: string,
                    defaultTimeZone: string): string
   {
      let tz = timeZoneOptions.find(option => option.timeZoneId == timeZoneID);

      if(!tz) {
         tz = timeZoneOptions[0];
      }

      if (tz == timeZoneOptions[0]) {
         return defaultTimeZone;
      }
      else {
         return  tz.label;
      }
   }

   updateStartTimeDataTimeZone(startTimeData: StartTimeData, oldTZ: string, newTZ: string): StartTimeData {
      if(oldTZ == null && newTZ == null) {
         return startTimeData;
      }

      let newTime = this.applyTimeZoneOffsetDifference(this.validateTimeValue(startTimeData.startTime), oldTZ, newTZ);
      return {
         startTime: newTime,
         timeRange: startTimeData.timeRange,
         startTimeSelected: startTimeData.startTimeSelected
      };
   }

   validateTimeValue(value: string): string {
      if(value?.indexOf(":NaN") >= 0) {
         return value.replace(":NaN", ":00");
      }

      return value;
   }

   applyTimeZoneOffsetDifference(value: string, oldTZ: string, newTZ: string): string {
      if(oldTZ == null && newTZ == null) {
         return value;
      }

      let time = DateTypeFormatter.timeInstantToDate(
         DateTypeFormatter.toTimeInstant(this.validateTimeValue(value),
            DateTypeFormatter.ISO_8601_TIME_FORMAT)).getTime();

      let date = new Date();
      const oldTZOffset = new Date(date.toLocaleString([], { timeZone: oldTZ })).getTime();
      const newTZOffset = new Date(date.toLocaleString([], { timeZone: newTZ })).getTime();
      time += newTZOffset - oldTZOffset;

      return this.getTimeString(new Date(time));
   }

   applyDateTimeZoneOffsetDifference(timeStr: string, oldTZ: string, newTZ: string, dateValue?: Date): Date {
      if(oldTZ == null && newTZ == null) {
         return null;
      }

      let timePart = 0;

      if(!!timeStr) {
         timePart = DateTypeFormatter.timeInstantToDate(
            DateTypeFormatter.toTimeInstant(timeStr, DateTypeFormatter.ISO_8601_TIME_FORMAT)).getTime();
         let valueTime = new Date(timePart);
         this.resetTimeOfDate(valueTime);
         timePart = timePart - valueTime.getTime();
      }

      let datePart = 0;

      if(dateValue) {
         let valueDate = new Date(dateValue.getTime());
         this.resetTimeOfDate(valueDate);
         datePart = valueDate.getTime();
      }

      let time = datePart + timePart;
      let date = new Date();
      const oldTZOffset = new Date(date.toLocaleString([], { timeZone: oldTZ })).getTime();
      const newTZOffset = new Date(date.toLocaleString([], { timeZone: newTZ })).getTime();
      time += newTZOffset - oldTZOffset;

      return new Date(time);
   }

   public resetTimeOfDate(dateValue: Date) {
      dateValue.setHours(0);
      dateValue.setMinutes(0);
      dateValue.setSeconds(0);
      dateValue.setMilliseconds(0);
   }

   getTimeString(time: Date): string {
      return DateTypeFormatter.format(time, DateTypeFormatter.ISO_8601_TIME_FORMAT);
   }
}
