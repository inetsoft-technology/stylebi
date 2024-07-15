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
import { XConstants } from "./xconstants";
import { XSchema } from "../data/xschema";

export class DateRangeRef {
   /**
    * Year Option.
    */
   static YEAR_INTERVAL = XConstants.YEAR_DATE_GROUP;
   /**
    * Quarter Option.
    */
   static QUARTER_INTERVAL = XConstants.QUARTER_DATE_GROUP;
   /**
    * Month Option.
    */
   static MONTH_INTERVAL = XConstants.MONTH_DATE_GROUP;
   /**
    * Week Option.
    */
   static WEEK_INTERVAL = XConstants.WEEK_DATE_GROUP;
   /**
    * Day Option.
    */
   static DAY_INTERVAL = XConstants.DAY_DATE_GROUP;
   /**
    * Hour Option.
    */
   static HOUR_INTERVAL = XConstants.HOUR_DATE_GROUP;
   /**
    * Minute Option.
    */
   static MINUTE_INTERVAL = XConstants.MINUTE_DATE_GROUP;
   /**
    * Second Option.
    */
   static SECOND_INTERVAL = XConstants.SECOND_DATE_GROUP;
   /**
    * Quarter Of Year Part.
    */
   static QUARTER_OF_YEAR_PART = XConstants.QUARTER_OF_YEAR_DATE_GROUP;
   /**
    * Month Of Year Part.
    */
   static MONTH_OF_YEAR_PART = XConstants.MONTH_OF_YEAR_DATE_GROUP;
   /**
    * Week Of Year Part.
    */
   static WEEK_OF_YEAR_PART = XConstants.WEEK_OF_YEAR_DATE_GROUP;
   /**
    * Day Of Month Part.
    */
   static DAY_OF_MONTH_PART = XConstants.DAY_OF_MONTH_DATE_GROUP;
   /**
    * Day Of Week Part.
    */
   static DAY_OF_WEEK_PART = XConstants.DAY_OF_WEEK_DATE_GROUP;
   /**
    * Hour Of Day Part.
    */
   static HOUR_OF_DAY_PART = XConstants.HOUR_OF_DAY_DATE_GROUP;
   /**
    * Minute of hour date group.
    */
   static MINUTE_OF_HOUR_PART = XConstants.MINUTE_OF_HOUR_DATE_GROUP;
   /**
    * Second of minute date group.
    */
   static SECOND_OF_MINUTE_PART = XConstants.SECOND_OF_MINUTE_DATE_GROUP;
   /**
    * None Option. As-Is.
    */
   static NONE_INTERVAL = XConstants.NONE_DATE_GROUP;

   /**
    * Check a specified option is date time type.
    */
   static isDateTime(option: number): boolean {
      return DateRangeRef.getDataType(option) == XSchema.TIME_INSTANT ||
         DateRangeRef.getDataType(option) == XSchema.DATE;
   }

   /**
    * Get data type for a specfied option.
    */
   static getDataType(option: number): string {
      if((option & XConstants.PART_DATE_GROUP) != 0) {
         return XSchema.INTEGER;
      }

      switch(option) {
         case XConstants.YEAR_DATE_GROUP:
         case XConstants.QUARTER_DATE_GROUP:
         case XConstants.MONTH_DATE_GROUP:
         case XConstants.WEEK_DATE_GROUP:
         case XConstants.DAY_DATE_GROUP:
            return XSchema.DATE;
         default:
            return XSchema.TIME_INSTANT;
      }
   }

   /**
    * Get the next date level.
    * @param level date level.
    * @param type data type.
    */
   static getNextDateLevel(level: number, dType: string): number {
      if(level >= 0) {
         for(let i = 0; i < DateRangeRef.dateLevel.length; i++) {
            if(DateRangeRef.dateLevel[i][0] == level) {
               if(XSchema.DATE === dType &&
                  DateRangeRef.dateLevel[i][1] === DateRangeRef.HOUR_OF_DAY_PART)
               {
                  return level;
               }

               return DateRangeRef.dateLevel[i][1];
            }
         }

         return level;
      }

      if(XSchema.TIME === dType) {
         return DateRangeRef.HOUR_INTERVAL;
      }

      return DateRangeRef.YEAR_INTERVAL;
   }

   static get dateLevel() {
      return [
         [DateRangeRef.YEAR_INTERVAL, DateRangeRef.QUARTER_OF_YEAR_PART],
         [DateRangeRef.QUARTER_INTERVAL, DateRangeRef.MONTH_OF_YEAR_PART],
         [DateRangeRef.MONTH_INTERVAL, DateRangeRef.DAY_OF_MONTH_PART],
         [DateRangeRef.WEEK_INTERVAL, DateRangeRef.DAY_OF_WEEK_PART],
         [DateRangeRef.DAY_INTERVAL, DateRangeRef.HOUR_OF_DAY_PART],
         [DateRangeRef.HOUR_INTERVAL, DateRangeRef.MINUTE_INTERVAL],
         [DateRangeRef.MINUTE_INTERVAL, DateRangeRef.SECOND_INTERVAL],
         [DateRangeRef.SECOND_INTERVAL, DateRangeRef.SECOND_INTERVAL],
         [DateRangeRef.QUARTER_OF_YEAR_PART, DateRangeRef.MONTH_OF_YEAR_PART],
         [DateRangeRef.MONTH_OF_YEAR_PART, DateRangeRef.DAY_OF_MONTH_PART],
         [DateRangeRef.WEEK_OF_YEAR_PART, DateRangeRef.DAY_OF_WEEK_PART],
         [DateRangeRef.DAY_OF_MONTH_PART, DateRangeRef.HOUR_OF_DAY_PART],
         [DateRangeRef.DAY_OF_WEEK_PART, DateRangeRef.HOUR_OF_DAY_PART],
         [DateRangeRef.HOUR_OF_DAY_PART, DateRangeRef.MINUTE_OF_HOUR_PART],
         [DateRangeRef.MINUTE_OF_HOUR_PART, DateRangeRef.SECOND_OF_MINUTE_PART],
         [DateRangeRef.SECOND_OF_MINUTE_PART, DateRangeRef.SECOND_OF_MINUTE_PART],
      ];
   }
}
