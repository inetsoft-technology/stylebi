/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import {Inject, Injectable, InjectionToken, Optional} from "@angular/core";
import {DateAdapter, MAT_DATE_LOCALE} from "@angular/material/core";
import dayjs from "dayjs";
import {Dayjs} from "dayjs";
import utc from "dayjs/plugin/utc";
import localeData from "dayjs/plugin/localeData";
import LocalizedFormat from "dayjs/plugin/localizedFormat";
import customParseFormat from "dayjs/plugin/customParseFormat";
import {FirstDayOfWeekService} from "../../../../../../portal/src/app/common/services/first-day-of-week.service";

dayjs.extend(utc);
dayjs.extend(localeData);
dayjs.extend(LocalizedFormat);
dayjs.extend(customParseFormat);

export interface DayJsDateAdapterOptions {
   /**
    * Turns the use of utc dates on or off.
    * Changing this will change how Angular Material components like DatePicker output dates.
    * {@default false}
    */
   useUtc?: boolean;
}

/** InjectionToken for Dayjs date adapter to configure options. */
export const MAT_DAYJS_DATE_ADAPTER_OPTIONS = new InjectionToken<DayJsDateAdapterOptions>(
   "MAT_DAYJS_DATE_ADAPTER_OPTIONS", {
      providedIn: "root",
      factory: MAT_DAYJS_DATE_ADAPTER_OPTIONS_FACTORY
   });

export function MAT_DAYJS_DATE_ADAPTER_OPTIONS_FACTORY(): DayJsDateAdapterOptions {
   return {
      useUtc: false
   };
}

/** Creates an array and fills it with values. */
function range<T>(length: number, valueFunction: (index: number) => T): T[] {
   const valuesArray = Array(length);
   for(let i = 0; i < length; i++) {
      valuesArray[i] = valueFunction(i);
   }
   return valuesArray;
}

/** Adapts Dayjs Dates for use with Angular Material. */
@Injectable()
export class DayjsDateAdapter extends DateAdapter<Dayjs> {
   private localeData: {
      firstDayOfWeek: number,
      longMonths: string[],
      shortMonths: string[],
      dates: string[],
      longDaysOfWeek: string[],
      shortDaysOfWeek: string[],
      narrowDaysOfWeek: string[]
   };
   private firstDayOfWeek: number = 0;

   constructor(private firstDayOfWeekService: FirstDayOfWeekService,
               @Optional() @Inject(MAT_DATE_LOCALE) public dateLocale: string,
               @Optional() @Inject(MAT_DAYJS_DATE_ADAPTER_OPTIONS) private options?: DayJsDateAdapterOptions)
   {
      super();
      this.setLocale(dateLocale);
      this.firstDayOfWeekService.getFirstDay()
         .subscribe(value => this.firstDayOfWeek = value.javaFirstDay - 1);
   }

   setLocale(locale: string) {
      super.setLocale(locale);

      const dayJsLocaleData = this.dayJs().localeData();
      this.localeData = {
         firstDayOfWeek: dayJsLocaleData.firstDayOfWeek(),
         longMonths: dayJsLocaleData.months(),
         shortMonths: dayJsLocaleData.monthsShort(),
         dates: range(31, (i) => this.createDate(2017, 0, i + 1).format("D")),
         longDaysOfWeek: range(7, (i) => this.dayJs().set("day", i).format("dddd")),
         shortDaysOfWeek: dayJsLocaleData.weekdaysShort(),
         narrowDaysOfWeek: dayJsLocaleData.weekdaysMin(),
      };
   }

   getYear(date: Dayjs): number {
      return this.dayJs(date).year();
   }

   getMonth(date: Dayjs): number {
      return this.dayJs(date).month();
   }

   getDate(date: Dayjs): number {
      return this.dayJs(date).date();
   }

   getDayOfWeek(date: Dayjs): number {
      return this.dayJs(date).day();
   }

   getMonthNames(style: "long" | "short" | "narrow"): string[] {
      return style === "long" ? this.localeData.longMonths : this.localeData.shortMonths;
   }

   getDateNames(): string[] {
      return this.localeData.dates;
   }

   getDayOfWeekNames(style: "long" | "short" | "narrow"): string[] {
      if(style === "long") {
         return this.localeData.longDaysOfWeek;
      }

      if(style === "short") {
         return this.localeData.shortDaysOfWeek;
      }

      return this.localeData.narrowDaysOfWeek;
   }

   getYearName(date: Dayjs): string {
      return this.dayJs(date).format("YYYY");
   }

   getFirstDayOfWeek(): number {
      return this.firstDayOfWeek;
   }

   getNumDaysInMonth(date: Dayjs): number {
      return this.dayJs(date).daysInMonth();
   }

   clone(date: Dayjs): Dayjs {
      return date.clone();
   }

   createDate(year: number, month: number, date: number): Dayjs {
      const returnDayjs = this.dayJs()
         .set("year", year)
         .set("month", month)
         .set("date", date);
      return returnDayjs;
   }

   today(): Dayjs {
      return this.dayJs();
   }

   parse(value: any, parseFormat: string): Dayjs | null {
      if(value && typeof value === "string") {
         const str: string = <string> value;

         // error parsing incomplete date string. (62716)
         if(str.length == parseFormat.length) {
            let fmt = parseFormat;

            try {
               fmt = dayjs().localeData().longDateFormat(parseFormat);
            }
            catch(e) {
               // ignore
            }

            return this.dayJs(value, fmt, this.locale);
         }

         return null;
      }

      return value ? this.dayJs(value).locale(this.locale) : null;
   }

   format(date: Dayjs, displayFormat: string): string {
      if(!this.isValid(date)) {
         throw Error("DayjsDateAdapter: Cannot format invalid date.");
      }

      return date.locale(this.locale).format(displayFormat);
   }

   addCalendarYears(date: Dayjs, years: number): Dayjs {
      return date.add(years, "year");
   }

   addCalendarMonths(date: Dayjs, months: number): Dayjs {
      return date.add(months, "month");
   }

   addCalendarDays(date: Dayjs, days: number): Dayjs {
      return date.add(days, "day");
   }

   toIso8601(date: Dayjs): string {
      return date.toISOString();
   }

   /**
    * Attempts to deserialize a value to a valid date object. This is different from parsing in that
    * deserialize should only accept non-ambiguous, locale-independent formats (e.g. a ISO 8601
    * string). The default implementation does not allow any deserialization, it simply checks that
    * the given value is already a valid date object or null. The `<mat-datepicker>` will call this
    * method on all of it's `@Input()` properties that accept dates. It is therefore possible to
    * support passing values from your backend directly to these properties by overriding this method
    * to also deserialize the format used by your backend.
    * @param value The value to be deserialized into a date object.
    * @returns The deserialized date object, either a valid date, null if the value can be
    *     deserialized into a null date (e.g. the empty string), or an invalid date.
    */
   deserialize(value: any): Dayjs | null {
      let date;

      if(value instanceof Date) {
         date = this.dayJs(value);
      }
      else if(this.isDateInstance(value)) {
         // NOTE: assumes that cloning also sets the correct locale.
         return this.clone(value);
      }

      if(typeof value === "string") {
         if(!value) {
            return null;
         }
         date = this.dayJs(value).toISOString();
      }

      if(date && this.isValid(date)) {
         return this.dayJs(date); // NOTE: Is this necessary since Dayjs is immutable and Moment was not?
      }

      return super.deserialize(value);
   }

   isDateInstance(obj: any): boolean {
      return dayjs.isDayjs(obj);
   }

   isValid(date: Dayjs): boolean {
      return this.dayJs(date).isValid();
   }

   invalid(): Dayjs {
      return this.dayJs(null);
   }

   private dayJs(input?: any, format?: string, locale?: string): Dayjs {
      if(!this.shouldUseUtc) {
         return dayjs(input, {format, locale}, locale);
      }

      return dayjs(input, {format, locale, utc: this.shouldUseUtc}, locale).utc();
   }

   private get shouldUseUtc(): boolean {
      const {useUtc}: DayJsDateAdapterOptions = this.options || {};
      return !!useUtc;
   }
}
