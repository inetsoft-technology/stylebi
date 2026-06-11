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
package inetsoft.web.wiz.service;

import inetsoft.uql.XConstants;
import inetsoft.uql.asset.DateRangeRef;

final class WizDateLevelUtil {
   private WizDateLevelUtil() {
   }

   static int getDateGroupLevel(String level) {
      if(level == null) {
         return XConstants.NONE_DATE_GROUP;
      }

      String mappedLevel = switch(level.toLowerCase()) {
         case "year" -> "Year";
         case "quarter" -> "Quarter";
         case "month" -> "Month";
         case "week" -> "Week";
         case "day" -> "Day";
         case "hour" -> "Hour";
         case "minute" -> "Minute";
         case "second" -> "Second";
         case "quarter of year" -> "QuarterOfYear";
         case "month of year" -> "MonthOfYear";
         case "week of year" -> "WeekOfYear";
         case "week of month" -> "WeekOfMonth";
         case "day of year" -> "DayOfYear";
         case "day of month" -> "DayOfMonth";
         case "day of week" -> "DayOfWeek";
         case "hour of day" -> "HourOfDay";
         case "minute of hour" -> "MinuteOfHour";
         case "second of minute" -> "SecondOfMinute";
         case "year of week" -> "YearOfWeek";
         case "quarter of week" -> "QuarterOfWeek";
         case "month of week" -> "MonthOfWeek";
         case "quarter of week part" -> "QuarterOfWeekN";
         case "month of week part" -> "MonthOfWeekN";
         case "none" -> null;
         default -> throw new IllegalArgumentException("Unsupported date level: " + level);
      };

      return mappedLevel == null ? XConstants.NONE_DATE_GROUP :
         DateRangeRef.getDateRangeOption(mappedLevel);
   }

   static String getDateGroupLevelName(int level) {
      return switch(level) {
         case XConstants.YEAR_DATE_GROUP -> "year";
         case XConstants.QUARTER_DATE_GROUP -> "quarter";
         case XConstants.MONTH_DATE_GROUP -> "month";
         case XConstants.WEEK_DATE_GROUP -> "week";
         case XConstants.DAY_DATE_GROUP -> "day";
         case XConstants.HOUR_DATE_GROUP -> "hour";
         case XConstants.MINUTE_DATE_GROUP -> "minute";
         case XConstants.SECOND_DATE_GROUP -> "second";
         case XConstants.QUARTER_OF_YEAR_DATE_GROUP -> "quarter of year";
         case XConstants.MONTH_OF_YEAR_DATE_GROUP -> "month of year";
         case XConstants.WEEK_OF_YEAR_DATE_GROUP -> "week of year";
         case XConstants.WEEK_OF_MONTH_DATE_GROUP -> "week of month";
         case XConstants.DAY_OF_YEAR_DATE_GROUP -> "day of year";
         case XConstants.DAY_OF_MONTH_DATE_GROUP -> "day of month";
         case XConstants.DAY_OF_WEEK_DATE_GROUP -> "day of week";
         case XConstants.HOUR_OF_DAY_DATE_GROUP -> "hour of day";
         case XConstants.MINUTE_OF_HOUR_DATE_GROUP -> "minute of hour";
         case XConstants.SECOND_OF_MINUTE_DATE_GROUP -> "second of minute";
         default -> null;
      };
   }
}
