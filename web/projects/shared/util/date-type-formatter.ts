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
import { TimeInstant } from "../../portal/src/app/common/data/time-instant";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import timezone from "dayjs/plugin/timezone";
import duration from "dayjs/plugin/duration";
import toObject from "dayjs/plugin/toObject";
import customParseFormat from "dayjs/plugin/customParseFormat";
import advancedFormat from "dayjs/plugin/advancedFormat";
import { TimeZoneModel } from "../schedule/model/time-zone-model";

dayjs.extend(utc);
dayjs.extend(timezone);
dayjs.extend(duration);
dayjs.extend(toObject);
dayjs.extend(customParseFormat);
dayjs.extend(advancedFormat);

/**
 * Wrapper class for moment.js. Handles date/time format conversions.
 */
export class DateTypeFormatter {
   public static ISO_8601_TIME_INSTANT_FORMAT: string = "YYYY-MM-DDTHH:mm:ss";
   public static ISO_8601_TIME_FORMAT: string = "HH:mm:ss";
   public static ISO_8601_DATE_FORMAT: string = "YYYY-MM-DD";

   /**
    * Transforms a value from one format to another.
    * @param value the value to be transformed
    * @param oldFormat the format the value is in
    * @param newFormat the format the value is to be outputted as
    * @returns the newly formatted value
    */
   public static transformValue(value: string, oldFormat: string, newFormat: string): string | null {
      let date = null;

      if(DateTypeFormatter.hasAMPM(oldFormat)) {
         // strict parsing with upper case AM/PM first
         date = dayjs(value, DateTypeFormatter.toUpperCaseAMPM(oldFormat), true);

         // then try lower case
         if(!date) {
            date = dayjs(value, DateTypeFormatter.toLowerCaseAMPM(oldFormat), true);
         }
      }

      if(!date) {
         date = dayjs(value, oldFormat);
      }

      // always use upper case AM/PM
      if(DateTypeFormatter.hasAMPM(newFormat)) {
         newFormat = DateTypeFormatter.toUpperCaseAMPM(newFormat);
      }

      if(!date.isValid()) {
         // in case the date can be parsed with new format
         date = dayjs(value, newFormat);

         if(date.isValid()) {
            return date.format(newFormat);
         }

         if(value) {
            date = dayjs(value);

            if(date.isValid()) {
               return date.format(newFormat);
            }
         }

         return null;
      }
      else {
         return date.format(newFormat);
      }
   }

   public static toTimeInstant(value: string, format: string, alternativeFormat?: string): TimeInstant {
      let result = dayjs(value, DateTypeFormatter.fixFormatToMoment(format));

      if(alternativeFormat && value && !result.isValid()) {
         result = dayjs(value, DateTypeFormatter.fixFormatToMoment(alternativeFormat));
      }

      if(value && !result.isValid()) {
         result = dayjs(value);
      }

      return result ? result.toObject() : null;
   }

   public static currentTimeInstantInFormat(format: string): string {
      return dayjs().format(DateTypeFormatter.fixFormatToMoment(format));
   }

   public static getLocalTime(value: number, format: string): string {
      if(value == 0 || value == null) {
         return null;
      }

      if(format == null) {
         format = "YYYY-MM-DD HH:mm:ss";
      }
console.log(value+"=======4444======="+format);
      return DateTypeFormatter.format(value,  format, true);
   }

   public static convertToLocalTimeZone(time1: number, tz: string, timeZoneOptions: TimeZoneModel[]): number {
      let diff = this.getLocalMinuteOffset(timeZoneOptions) - this.getMinuteOffset(tz, timeZoneOptions);
      const timezoneDiffInMilliseconds = diff * 60 * 1000;

      return time1 - timezoneDiffInMilliseconds;
   }

   public static convertToOtherTimeZone(time1: number, tz: string, timeZoneOptions: TimeZoneModel[]): number {
      let diff = this.getLocalMinuteOffset(timeZoneOptions) - this.getMinuteOffset(tz, timeZoneOptions);
      const timezoneDiffInMilliseconds = diff * 60 * 1000;

      return time1 + timezoneDiffInMilliseconds;
   }

   public static getLocalMinuteOffset(timeZoneOptions: TimeZoneModel[]): number {
      const localTimeZoneId = Intl.DateTimeFormat().resolvedOptions().timeZone;

      return this.getMinuteOffset(localTimeZoneId, timeZoneOptions);
   }

   public static getMinuteOffset(tz: string, timeZoneOptions: TimeZoneModel[]): number {
      let result = timeZoneOptions.find((opt) => opt.timeZoneId == tz &&
         opt.minuteOffset != null);

      return result.minuteOffset;
   }

   public static format(value: number | Date, format: string, autoFix: boolean = true): string | null {
      const date = dayjs(value);

      if(date.isValid()) {
         return date.format(autoFix ? DateTypeFormatter.fixFormatToMoment(format) : format);
      }

      return null;
   }

   public static formatInTimeZone(value: number | Date, tz: string, format: string): string | null {
      const date = dayjs(value);

      if(date.isValid()) {
         return date.tz(tz).format(format);
      }

      return null;
   }

   public static formatDuration(value: number, format: string): string {
      return dayjs.utc(dayjs.duration(value, "ms").asMilliseconds()).format(format);
   }

   /**
    * fix a format to moment format. e.g. yyyy-MM-dd to YYYY-MM-DD
    * @param format date format
    */
   public static fixFormatToMoment(format: string): string {
      return !!format ? format.replace(/y/g, "Y")
                              .replace(/d/g, "D")
                              .replace(/a/g, "A")
                      : "";
   }

   /**
    * translate format to 24 hours.
    * @param format
    */
   public static translateTo24Hours(format: string) {
      return !!format ? format.replace(/h/g, "H") : "";
   }

   /**
    * transform timeInstant to date object
    * @param instant TimeInstant
    */
   public static timeInstantToDate(instant: TimeInstant): Date {
      return new Date(instant.years, instant.months, instant.date,
         instant.hours, instant.minutes, instant.seconds, instant.milliseconds);
   }

   /**
    * format timeInstant to specified format
    * @param instant TimeInstant object
    * @param format specified format
    */
   public static formatInstant(instant: TimeInstant, format: string): string {
      const date = DateTypeFormatter.timeInstantToDate(instant);

      return DateTypeFormatter.format(date, DateTypeFormatter.fixFormatToMoment(format));
   }

   /**
    * format the date string to specified format
    * @param dateTime date string
    * @param format specified format
    */
   public static formatStr(dateTime: string, format: string): string {
      const instant = DateTypeFormatter.toTimeInstant(dateTime, format);

      return DateTypeFormatter.formatInstant(instant, format);
   }

   private static hasAMPM(format: string): boolean {
      return format && (format.indexOf("a") >= 0 || format.indexOf("A") >= 0);
   }

   private static toUpperCaseAMPM(format: string): string {
      if(!format) {
         return format;
      }

      return format.replace(/a/g, "A");
   }

   private static toLowerCaseAMPM(format: string): string {
      if(!format) {
         return format;
      }

      return format.replace(/A/g, "a");
   }
}
