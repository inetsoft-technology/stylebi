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
package inetsoft.uql.asset;

import inetsoft.sree.SreeEnv;
import inetsoft.uql.XConstants;
import inetsoft.uql.erm.*;
import inetsoft.uql.jdbc.SQLHelper;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.Tool;
import inetsoft.util.script.JavaScriptEngine;
import org.springframework.util.StringUtils;
import org.w3c.dom.Element;

import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * DateRangeRef represents a date range data ref.
 * if not full, only the date value of the date option will be returned.
 * Otherwise, the higher level date value of the date option will also be
 * included.
 * <p>
 * There is no higher level for year option.
 * For quarter option, the higher level is year, so the full value format
 * should be yyyyqq (year * 100 + quarter of year).
 * For month option, the higher level is year, so the full value format
 * should be yyyymm (year * 100 + month of year).
 * For week option, the higher level is year, so the full value format
 * should be yyyyww (year * 100 + week of year).
 * For day option, the higher level is year and month, so the full
 * value format should be yyyymmdd (year * 10000 + month of year * 100 + day
 * of month).
 *
 * @version 10.2
 * @author InetSoft Technology Corp
 */
public final class DateRangeRef extends ExpressionRef implements AssetObject,
   XConstants, SQLExpressionRef, RangeRef, DataRefWrapper
{
   /**
    * Year Option.
    */
   public static final int YEAR_INTERVAL = XConstants.YEAR_DATE_GROUP;
   /**
    * Quarter Option.
    */
   public static final int QUARTER_INTERVAL = XConstants.QUARTER_DATE_GROUP;
   /**
    * Month Option.
    */
   public static final int MONTH_INTERVAL = XConstants.MONTH_DATE_GROUP;
   /**
    * Week Option.
    */
   public static final int WEEK_INTERVAL = XConstants.WEEK_DATE_GROUP;
   /**
    * Day Option.
    */
   public static final int DAY_INTERVAL = XConstants.DAY_DATE_GROUP;
   /**
    * Hour Option.
    */
   public static final int HOUR_INTERVAL = XConstants.HOUR_DATE_GROUP;
   /**
    * Minute Option.
    */
   public static final int MINUTE_INTERVAL = XConstants.MINUTE_DATE_GROUP;
   /**
    * Second Option.
    */
   public static final int SECOND_INTERVAL = XConstants.SECOND_DATE_GROUP;
   /**
    * Quarter Of Year Part.
    */
   public static final int QUARTER_OF_YEAR_PART =
      XConstants.QUARTER_OF_YEAR_DATE_GROUP;
   /**
    * Month Of Year Part.
    */
   public static final int MONTH_OF_YEAR_PART =
      XConstants.MONTH_OF_YEAR_DATE_GROUP;
   /**
    * Week Of Year Part.
    */
   public static final int WEEK_OF_YEAR_PART =
      XConstants.WEEK_OF_YEAR_DATE_GROUP;
   /**
    * Day Of Month Part.
    */
   public static final int DAY_OF_MONTH_PART =
      XConstants.DAY_OF_MONTH_DATE_GROUP;
   /**
    * Day Of Week Part.
    */
   public static final int DAY_OF_WEEK_PART =
      XConstants.DAY_OF_WEEK_DATE_GROUP;
   /**
    * Hour Of Day Part.
    */
   public static final int HOUR_OF_DAY_PART =
      XConstants.HOUR_OF_DAY_DATE_GROUP;
   /**
    * Hour Of Day Part.
    */
   public static final int MINUTE_OF_HOUR_PART =
           XConstants.MINUTE_OF_HOUR_DATE_GROUP;
   /**
    * Hour Of Day Part.
    */
   public static final int SECOND_OF_MINUTE_PART =
           XConstants.SECOND_OF_MINUTE_DATE_GROUP;
   /**
    * None Option. As-Is.
    */
   public static final int NONE_INTERVAL = XConstants.NONE_DATE_GROUP;

   private static final int DC_PART = 1024 + PART_DATE_GROUP;
   private static final int DC_INTERVAL = 2048;

   /**
    * Day of Year Part (only used in date comparison).
    */
   public static final int DAY_OF_YEAR_PART = DC_PART + 1;
   /**
    * Day of Quarter Part (only used in date comparison).
    */
   public static final int DAY_OF_QUARTER_PART = DC_PART + 2;
   /**
    * Week of Month Part (only used in date comparison).
    */
   public static final int WEEK_OF_MONTH_PART = DC_PART + 3;
   /**
    * Week of Quarter Part (only used in date comparison).
    */
   public static final int WEEK_OF_QUARTER_PART = DC_PART + 4;
   /**
    * Month of Quarter Part (only used in date comparison).
    */
   public static final int MONTH_OF_QUARTER_PART = DC_PART + 5;
   /**
    * Month of Quarter Part, of a full week (only used in date comparison).
    */
   public static final int MONTH_OF_QUARTER_FULL_WEEK_PART = DC_PART + 6;
   /**
    * Month of a full week IF the week of the month is before the 1st week. For example,
    * 2024-02-01's week of month is 0 instead of 1, since the first full week
    * starts at 2024-02-04. So the month-of-week for 2024-02-01 is 2024-01 and
    * the week of month is 4.
    */
   public static final int MONTH_OF_FULL_WEEK = DC_INTERVAL + 7;
   /**
    * Month of a full week IF the week of the month is before the 1st of the month.
    */
   public static final int MONTH_OF_FULL_WEEK_PART = DC_PART + 8;
   /**
    * Quarter of a full week IF the week of the quarter is before the 1st of the quarter.
    */
   public static final int QUARTER_OF_FULL_WEEK = DC_INTERVAL + 9;
   /**
    * Quarter of a full week IF the week of the quarter is before the 1st of the quarter.
    */
   public static final int QUARTER_OF_FULL_WEEK_PART = DC_PART + 10;
   /**
    * Year of a full week IF the week of the quarter is before the 1st of the year.
    */
   public static final int YEAR_OF_FULL_WEEK = DC_INTERVAL + 11;

   /**
    * Check if is time option.
    */
   public static boolean isTimeOption(int option) {
      for(int i = 0; i < toptions.length; i++) {
         if(toptions[i] == option) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check a specified option is date time type.
    */
   public static boolean isDateTime(int option) {
      return XSchema.TIME_INSTANT.equals(getDataType(option)) ||
         XSchema.DATE.equals(getDataType(option));
   }

   /**
    * Get data type for a specfied option.
    */
   public static String getDataType(int option) {
      return getDataType(option, null);
   }

   /**
    * Get data type for a specfied option.
    */
   public static String getDataType(int option, String originalType) {
      if(option == NONE_INTERVAL && !StringUtils.isEmpty(originalType)) {
         return originalType;
      }

      if((option & PART_DATE_GROUP) != 0) {
         return XSchema.INTEGER;
      }

      switch(option) {
         case YEAR_DATE_GROUP:
         case QUARTER_DATE_GROUP:
         case MONTH_DATE_GROUP:
         case WEEK_DATE_GROUP:
         case DAY_DATE_GROUP:
            // this should match the getData() return value, and the sqlhelper.xml
            // year/quarter/month/week/day function return types. (54401)
            return XSchema.TIME_INSTANT;
         default:
            return XSchema.TIME.equals(originalType) ? XSchema.TIME : XSchema.TIME_INSTANT;
      }
   }

   /**
    * Get the name of an attribute with a date range option.
    */
   public static String getName(String attr, int option) {
      StringBuilder sb = new StringBuilder();
      sb.append(getRangeValue(option));
      sb.append('(');
      sb.append(attr);
      sb.append(')');

      return sb.toString();
   }

   /**
    * Get the value of a date range option.
    */
   public static String getRangeValue(int option) {
      String str = "";

      switch(option) {
      case YEAR_INTERVAL:
         str = "Year";
         break;
      case QUARTER_INTERVAL:
         str = "Quarter";
         break;
      case MONTH_INTERVAL:
         str = "Month";
         break;
      case WEEK_INTERVAL:
         str = "Week";
         break;
      case DAY_INTERVAL:
         str = "Day";
         break;
      case HOUR_INTERVAL:
         str = "Hour";
         break;
      case MINUTE_INTERVAL:
         str = "Minute";
         break;
      case SECOND_INTERVAL:
         str = "Second";
         break;
      case MONTH_OF_FULL_WEEK_PART:
         str = "MonthOfWeekN";
         break;
      case MONTH_OF_FULL_WEEK:
         str = "MonthOfWeek";
         break;
      case QUARTER_OF_YEAR_PART:
         str = "QuarterOfYear";
         break;
      case QUARTER_OF_FULL_WEEK_PART:
         str = "QuarterOfWeekN";
         break;
      case QUARTER_OF_FULL_WEEK:
         str = "QuarterOfWeek";
         break;
      case YEAR_OF_FULL_WEEK:
         str = "YearOfWeek";
         break;
      case MONTH_OF_YEAR_PART:
         str = "MonthOfYear";
         break;
      case WEEK_OF_YEAR_PART:
         str = "WeekOfYear";
         break;
      case DAY_OF_MONTH_PART:
         str = "DayOfMonth";
         break;
      case DAY_OF_WEEK_PART:
         str = "DayOfWeek";
         break;
      case HOUR_OF_DAY_PART:
         str = "HourOfDay";
         break;
      case MINUTE_OF_HOUR_PART:
         str = "MinuteOfHour";
         break;
      case SECOND_OF_MINUTE_PART:
         str = "SecondOfMinute";
         break;
      case NONE_INTERVAL:
         str = "None";
         break;
      case DAY_OF_YEAR_PART:
         str = "DayOfYear";
         break;
      case DAY_OF_QUARTER_PART:
         str = "DayOfQuarter";
         break;
      case WEEK_OF_MONTH_PART:
         str = "WeekOfMonth";
         break;
      case WEEK_OF_QUARTER_PART:
         str = "WeekOfQuarter";
         break;
      case MONTH_OF_QUARTER_PART:
         str = "MonthOfQuarter";
         break;
      case MONTH_OF_QUARTER_FULL_WEEK_PART:
         str = "MonthOfQuarterOfWeek";
         break;
      default:
         throw new RuntimeException("Unsupported option found: " + option);
      }

      return str;
   }

   public static String getBaseName(String name) {
      if(name.startsWith("Year(") && name.endsWith(")")) {
         return name.substring(5, name.length() - 1);
      }
      else if(name.startsWith("Quarter(") && name.endsWith(")")) {
         return name.substring(8, name.length() - 1);
      }
      else if(name.startsWith("Month(") && name.endsWith(")")) {
         return name.substring(6, name.length() - 1);
      }
      else if(name.startsWith("Week(") && name.endsWith(")")) {
         return name.substring(5, name.length() - 1);
      }
      else if(name.startsWith("Day(") && name.endsWith(")")) {
         return name.substring(4, name.length() - 1);
      }
      else if(name.startsWith("Hour(") && name.endsWith(")")) {
         return name.substring(5, name.length() - 1);
      }
      else if(name.startsWith("Minutes(") && name.endsWith(")")) {
         return name.substring(6, name.length() - 1);
      }
      else if(name.startsWith("Second(") && name.endsWith(")")) {
         return name.substring(7, name.length() - 1);
      }
      else if(name.startsWith("QuarterOfYear(") && name.endsWith(")")) {
         return name.substring(14, name.length() - 1);
      }
      else if(name.startsWith("MonthOfYear(") && name.endsWith(")")) {
         return name.substring(12, name.length() - 1);
      }
      else if(name.startsWith("WeekOfYear(") && name.endsWith(")")) {
         return name.substring(11, name.length() - 1);
      }
      else if(name.startsWith("DayOfWeek(") && name.endsWith(")")) {
         return name.substring(10, name.length() - 1);
      }
      else if(name.startsWith("HourOfWeek(") && name.endsWith(")")) {
         return name.substring(11, name.length() - 1);
      }
      else if(name.startsWith("MinuteOfWeek(") && name.endsWith(")")) {
         return name.substring(13, name.length() - 1);
      }
      else if(name.startsWith("SecondOfWeek(") && name.endsWith(")")) {
         return name.substring(13, name.length() - 1);
      }
      else if(name.startsWith("None(") && name.endsWith(")")) {
         return name.substring(5, name.length() - 1);
      }
      else if(name.startsWith("DayOfYear(") && name.endsWith(")")) {
         return name.substring(10, name.length() - 1);
      }
      else if(name.startsWith("DayOfQuarter(") && name.endsWith(")")) {
         return name.substring(13, name.length() - 1);
      }
      else if(name.startsWith("WeekOfMonth(") && name.endsWith(")")) {
         return name.substring(12, name.length() - 1);
      }
      else if(name.startsWith("WeekOfQuarter(") && name.endsWith(")")) {
         return name.substring(14, name.length() - 1);
      }
      else if(name.startsWith("MonthOfQuarter(") && name.endsWith(")")) {
         return name.substring(15, name.length() - 1);
      }

      return name;
   }

   /**
    * Get the option of a date range value.
    */
   public static int getDateRangeOption(String level) {
      int option = -1;

      switch(level) {
      case "Year":
         option = YEAR_INTERVAL;
         break;
      case "Quarter":
         option = QUARTER_INTERVAL;
         break;
      case "Month":
         option = MONTH_INTERVAL;
         break;
      case "Week":
         option = WEEK_INTERVAL;
         break;
      case "Day":
         option = DAY_INTERVAL;
         break;
      case "Hour":
         option = HOUR_INTERVAL;
         break;
      case "Minute":
         option = MINUTE_INTERVAL;
         break;
      case "Second":
         option = SECOND_INTERVAL;
         break;
      case "QuarterOfYear":
         option = QUARTER_OF_YEAR_PART;
         break;
      case "MonthOfYear":
         option = MONTH_OF_YEAR_PART;
         break;
      case "WeekOfYear":
         option = WEEK_OF_YEAR_PART;
         break;
      case "DayOfMonth":
         option = DAY_OF_MONTH_PART;
         break;
      case "DayOfWeek":
         option = DAY_OF_WEEK_PART;
         break;
      case "HourOfDay":
         option = HOUR_OF_DAY_PART;
         break;
      case "MinuteOfHour":
         option = MINUTE_OF_HOUR_PART;
         break;
      case "SecondOfMinute":
         option = SECOND_OF_MINUTE_PART;
         break;
      case "None":
         option = NONE_INTERVAL;
         break;
      case "DayOfYear":
         option = DAY_OF_YEAR_PART;
         break;
      case "DayOfQUARTER":
         option = DAY_OF_QUARTER_PART;
         break;
      case "WeekOfMonth":
         option = WEEK_OF_MONTH_PART;
         break;
      case "WeekOfQuarter":
         option = WEEK_OF_QUARTER_PART;
         break;
      case "MonthOfQuarter":
         option = MONTH_OF_QUARTER_PART;
         break;
      case "MonthOfQuarterOfWeek":
         option = MONTH_OF_QUARTER_FULL_WEEK_PART;
         break;
      case "MonthOfWeek":
         option = MONTH_OF_FULL_WEEK;
         break;
      case "MonthOfWeekN":
         option = MONTH_OF_FULL_WEEK_PART;
         break;
      case "QuarterOfWeekN":
         option = QUARTER_OF_FULL_WEEK_PART;
         break;
      case "QuarterOfWeek":
         option = QUARTER_OF_FULL_WEEK;
         break;
      case "YearOfWeek":
         option = YEAR_OF_FULL_WEEK;
         break;
      default:
         throw new RuntimeException("Unsupported option found: " + option);
      }

      return option;
   }

   /**
    * Get the integer value by the specified date value and date option. The
    * integer value is the result of a full DateRangeRef, and the option is
    * the date option of the full DateRangeRef. The DateRangeRef is full, so
    * the integer value needs to contain all parts(year, week, etc.).
    * @param option the date option of a full DateRangeRef.
    * @param date the date value.
    */
   public static Object getData(int option, Date date) {
      return getData(option, date, -1);
   }

   /**
    * Get the integer value by the specified date value and date option. The
    * integer value is the result of a full DateRangeRef, and the option is
    * the date option of the full DateRangeRef. The DateRangeRef is full, so
    * the integer value needs to contain all parts(year, week, etc.).
    * @param option the date option of a full DateRangeRef.
    * @param date the date value.
    */
   public static Object getData(int option, Date date, int forceDcToDateWeekOfMonth) {
      if(date == null) {
         return null;
      }

      DateTimeProcessor calendar = getDateTimeProcessor();
      calendar.setMillis(date.getTime());

      Object result;

      switch(option) {
      case YEAR_INTERVAL: {
         int year = calendar.getYear();

         if(year == 1900) {
            try {
               result = new SimpleDateFormat("yyyy-MM-dd").parse("1900-01-01");
               break;
            }
            catch(ParseException pe) {
            }
         }

         result = calendar.getTimestamp(year, 1, 1, 0, 0, 0);
         break;
      }
      case QUARTER_INTERVAL: {
         int year = calendar.getYear();
         int month = calendar.getMonthOfYear();
         result = calendar.getTimestamp(year, (month - 1) / 3 * 3 + 1, 1, 0, 0, 0);
         break;
      }
      case MONTH_INTERVAL: {
         int year = calendar.getYear();
         int month = calendar.getMonthOfYear();
         result = calendar.getTimestamp(year, month, 1, 0, 0, 0);
         break;
      }
      case WEEK_INTERVAL: {
         int year = calendar.getYear();
         int month = calendar.getMonthOfYear();
         int day = calendar.getDayOfMonth();
         result = calendar.getWeek(year, month, day);
         break;
      }
      case DAY_INTERVAL: {
         int year = calendar.getYear();
         int month = calendar.getMonthOfYear();
         int day = calendar.getDayOfMonth();
         result = calendar.getTimestamp(year, month, day, 0, 0, 0);
         break;
      }
      case HOUR_INTERVAL: {
         int hour = calendar.getHourOfDay();

         // maintain Time type for hour/minute/second. (50302)
         if(date instanceof java.sql.Time) {
            result = new java.sql.Time(hour, 0, 0);
         }
         else {
            int year = calendar.getYear();
            int month = calendar.getMonthOfYear();
            int day = calendar.getDayOfMonth();
            result = calendar.getTimestamp(year, month, day, hour, 0, 0);
         }
         break;
      }
      case MINUTE_INTERVAL: {
         int hour = calendar.getHourOfDay();
         int minute = calendar.getMinuteOfHour();

         if(date instanceof java.sql.Time) {
            result = new java.sql.Time(hour, minute, 0);
         }
         else {
            int year = calendar.getYear();
            int month = calendar.getMonthOfYear();
            int day = calendar.getDayOfMonth();
            result = calendar.getTimestamp(year, month, day, hour, minute, 0);
         }

         break;
      }
      case SECOND_INTERVAL: {
         int hour = calendar.getHourOfDay();
         int minute = calendar.getMinuteOfHour();
         int second = calendar.getSecondOfMinute();

         if(date instanceof java.sql.Time) {
            result = new java.sql.Time(hour, minute, second);
         }
         else {
            int year = calendar.getYear();
            int month = calendar.getMonthOfYear();
            int day = calendar.getDayOfMonth();
            result = calendar.getTimestamp(year, month, day, hour, minute, second);
         }

         break;
      }
      case QUARTER_OF_YEAR_PART: {
         int month = calendar.getMonthOfYear();
         result = (month + 2) / 3;
         break;
      }
      case MONTH_OF_YEAR_PART: {
         int month = calendar.getMonthOfYear();
         result = month;
         break;
      }
      case WEEK_OF_YEAR_PART: {
         // the joda uses iso definition where the first week is the week
         // with at least 4 days. Java calendar defaults to 1 day. so there
         // will be incompatibilities.
         // int week = calendar.getWeekOfWeekyear() + 1;
         result = calendar.getWeekOfYear();
         break;
      }
      case DAY_OF_MONTH_PART: {
         int day = calendar.getDayOfMonth();
         result = day;
         break;
      }
      case DAY_OF_WEEK_PART: {
         int weekday = calendar.getDayOfWeek() + 1;
         result = weekday > 7 ? weekday - 7 : weekday;
         break;
      }
      case HOUR_OF_DAY_PART: {
         int hour = calendar.getHourOfDay();
         result = hour;
         break;
      }
      case MINUTE_OF_HOUR_PART: {
         int minute = calendar.getMinuteOfHour();
         result = minute;
         break;
      }
      case SECOND_OF_MINUTE_PART: {
         int second = calendar.getSecondOfMinute();
         result = second;
         break;
      }
      case NONE_INTERVAL: {
         result = date;
         break;
      }
      case DAY_OF_YEAR_PART:
         result = (int) JavaScriptEngine.datePart("y", date, false);
         break;
      case DAY_OF_QUARTER_PART:
         result = (int) JavaScriptEngine.datePart("dq", date, false);
         break;
      case WEEK_OF_MONTH_PART:
         result = (int) JavaScriptEngine.datePartForceWeekOfMonth("wm", date,
            true, forceDcToDateWeekOfMonth);
         break;
      case WEEK_OF_QUARTER_PART:
         result = (int) JavaScriptEngine.datePart("wq", date, true);
         break;
      case MONTH_OF_QUARTER_PART:
         result = (int) JavaScriptEngine.datePart("mq", date, false);
         break;
      case MONTH_OF_QUARTER_FULL_WEEK_PART:
         result = (int) JavaScriptEngine.datePartForceWeekOfMonth("wmq", date, true, forceDcToDateWeekOfMonth);
         break;
      case MONTH_OF_FULL_WEEK:
         result = calendar.getMonthOfFullWeek(forceDcToDateWeekOfMonth);
         break;
      case MONTH_OF_FULL_WEEK_PART:
         result = calendar.getMonthPartOfFullWeek(forceDcToDateWeekOfMonth);
         break;
      case QUARTER_OF_FULL_WEEK_PART:
         result = calendar.getQuarterPartOfFullWeek(forceDcToDateWeekOfMonth);
         break;
      case QUARTER_OF_FULL_WEEK:
         result = calendar.getQuarterOfFullWeek(forceDcToDateWeekOfMonth);
         break;
      case YEAR_OF_FULL_WEEK:
         result = calendar.getYearOfFullWeek(forceDcToDateWeekOfMonth);
         break;
      default:
         throw new RuntimeException("Unsupported option found: " + option);
      }

      return result;
   }

   /**
    * Constructor.
    */
   public DateRangeRef() {
      super();
   }

   /**
    * Constructor.
    */
   public DateRangeRef(String attr) {
      this();
      this.attr = attr;
   }

   /**
    * Constructor.
    */
   public DateRangeRef(String attr, DataRef ref) {
      this(attr);
      this.ref = ref;
   }

   /**
    * Constructor.
    */
   public DateRangeRef(String attr, DataRef ref, int option) {
      this(attr, ref, option, null);
   }

   /**
    * Constructor.
    */
   public DateRangeRef(String attr, DataRef ref, int option, Integer forceWeekOfMonth) {
      this(attr, ref);
      this.option = option;
      this.forceDcToDateWeekOfMonth = forceWeekOfMonth;
   }

   /**
    * Get the database version.
    */
   @Override
   public String getDBVersion() {
      return dbversion;
   }

   /**
    * Set the database version.
    */
   @Override
   public void setDBVersion(String version) {
      this.dbversion = version;
   }

   /**
    * Get the date option.
    * @return the date option.
    */
   public int getDateOption() {
      return option;
   }

   /**
    * Set the date option, one of the option constants defined in this class.
    * @param option the specified date option.
    */
   public void setDateOption(int option) {
      this.option = option;
   }

   /**
    * Get the type of the field.
    * @return the type of the field.
    */
   @Override
   public int getRefType() {
      return ref == null ? NONE : ref.getRefType();
   }

   /**
    * Get the attribute's parent entity.
    * @return the name of the entity.
    */
   @Override
   public String getEntity() {
      return null;
   }

   /**
    * Get the referenced attribute.
    * @return the name of the attribute.
    */
   @Override
   public String getAttribute() {
      return attr;
   }

   /**
    * Get the contained data ref.
    * @return the contained data ref.
    */
   @Override
   public DataRef getDataRef() {
      return ref;
   }

   /**
    * Set the data ref.
    * @param ref the specified data ref.
    */
   @Override
   public void setDataRef(DataRef ref) {
      this.ref = ref;
   }

   /**
    * Get original data type.
    */
   public String getOriginalType() {
      return originalType;
   }

   /**
    * Set original data type.
    */
   public void setOriginalType(String originalType) {
      this.originalType = originalType == null ? XSchema.STRING : originalType;
   }

   /**
    * Get the data type.
    * @return the data type defined in XSchema.
    */
   @Override
   public String getDataType() {
      return getDataType(option, originalType);
   }

   /**
    * Get the data class.
    */
   public Class getDataClass() {
      return getDataClass(this.strictDataType);
   }

   /**
    * Get the data class.
    */
   public Class getDataClass(boolean strictDataType) {
      // shouldn't treat time as date when date level is none.
      // none is a noop so just keep the original value. (49987)
      if(option == NONE_INTERVAL && !StringUtils.isEmpty(originalType)) {
         switch(originalType) {
            case XSchema.TIME:
               return java.sql.Time.class;
            case XSchema.DATE:
               return java.sql.Date.class;
            default:
               return java.sql.Timestamp.class;
         }
      }

      if(strictDataType) {
         return getStrictDataType();
      }

      if((option & PART_DATE_GROUP) != 0) {
         return Integer.class;
      }

      return Date.class;
   }

   private Class getStrictDataType() {
      if((option & PART_DATE_GROUP) != 0) {
         return Integer.class;
      }

      switch(option) {
         case YEAR_DATE_GROUP:
         case QUARTER_DATE_GROUP:
         case MONTH_DATE_GROUP:
         case WEEK_DATE_GROUP:
         case DAY_DATE_GROUP:
            return java.sql.Date.class;
         default:
            return XSchema.TIME.equals(originalType) ? java.sql.Time.class : Date.class;
      }
   }

   /**
    * Write the attributes of this object.
    * @param writer the output stream to which to write the XML data.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      writer.print(" dateOption=\"" + option + "\"");
      writer.print(" originalType=\"" + originalType + "\"");
      writer.print(" autoCreate=\"" + autoCreate + "\"");
   }

   /**
    * Write the attributes of this object.
    * @param dos the output stream to which to write the OutputStream data.
    */
   @Override
   protected void writeAttributes2(DataOutputStream dos) {
      try {
         dos.writeInt(option);
         dos.writeUTF(originalType);
         dos.writeBoolean(autoCreate);
      }
      catch(IOException ex) {
      }
   }

   /**
    * Read in the attribute of this object from an XML tag.
    * @param tag the XML element representing this object.
    */
   @Override
   protected void parseAttributes(Element tag) throws Exception {
      option = Integer.parseInt(Tool.getAttribute(tag, "dateOption"));
      originalType = Tool.getAttribute(tag, "originalType");
      originalType = originalType == null ? XSchema.STRING : originalType;
      autoCreate = "true".equals(Tool.getAttribute(tag, "autoCreate"));
   }

   /**
    * Write the contents of this object.
    * @param writer the output stream to which to write the XML data.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      ref.writeXML(writer);
      writer.print("<attribute>");
      writer.print("<![CDATA[" + attr + "]]>");
      writer.println("</attribute>");
   }

   /**
    * Write the contents of this object.
    * @param dos the output stream to which to write the OutputStream data.
    */
   @Override
   protected void writeContents2(DataOutputStream dos) {
      try {
         dos.writeUTF(ref.getClass().getName());
         ref.writeData(dos);
         dos.writeUTF(attr);
      }
      catch(IOException ex) {
    // impossible
      }
   }

   /**
    * Read in the contents of this object from an xml tag.
    * @param tag the specified xml element.
    */
   @Override
   protected void parseContents(Element tag) throws Exception {
      Element dnode = Tool.getChildNodeByTagName(tag, "dataRef");
      ref = createDataRef(dnode);

      Element anode = Tool.getChildNodeByTagName(tag, "attribute");
      attr = Tool.getValue(anode);
   }

   /**
    * Get the name of this reference.
    * @return the reference name.
    */
   @Override
   public String getName() {
      return attr;
   }

   /**
    * Set the name of the field.
    * @param name the name of the field
    */
   @Override
   public void setName(String name) {
      this.attr = name;
      super.setName(name);
   }

   /**
    * Get the SQL expression of this reference.
    * @return a SQL expression.
    */
   @Override
   public String getExpression() {
      String type = getDBType();

      if(type == null || type.length() == 0) {
         return "";
      }

      return getExpression(type, ref, option, originalType);
   }

   /**
    * Get the SQL expression of this reference.
    * @return a SQL expression.
    */
   public static String getExpression(String type, DataRef ref, int option) {
      return getExpression(type, ref, option, null);
   }

   /**
    * Get the SQL expression of this reference.
    * @return a SQL expression.
    */
   public static String getExpression(String type, DataRef ref, int option, String otype) {
      SQLHelper helper = SQLHelper.getSQLHelper(type);

      if(helper == null) {
         return "";
      }

      if(ref == null) {
         return "";
      }

      // @by ankitmathur, For Bug #1126. In the case where we are doing "WEEK"
      // grouping and the User's locale considers Monday the first day of the
      // week, post-process the grouping via getScriptExpression() method.
      // 12-8-2015, NOTE: this will only occur if the user has set the
      // "user.locale.weekGrouping" property to true.
      // TODO: We should create new SQL expressions (sqlhelper.xml) for this case.
      if(option == WEEK_INTERVAL && Tool.getFirstDayOfWeek() != Calendar.SUNDAY &&
         Tool.getWeekStart() != null)
      {
         return "";
      }

      // do not get expression from DateRangeRef itself
      String field = !(ref instanceof DateRangeRef) &&
         ref instanceof ExpressionRef ?
         ((ExpressionRef) ref).getExpression() :
         "field['" + ref.getName() + "']";
      String func = "";
      boolean supportTime = XSchema.TIME.equals(otype) &&
         ("db2".equals(type) || "mysql".equals(type) ||
         "postgresql".equals(type)) ? true : false;
      boolean isDB2Time = XSchema.TIME.equals(otype) && "db2".equals(type);

      switch(option) {
      case YEAR_INTERVAL:
         func = SQLHelper.YEAR_FUNCTION;
         break;
      case QUARTER_INTERVAL:
         func = SQLHelper.QUARTER_FUNCTION;
         break;
      case MONTH_INTERVAL:
         func = SQLHelper.MONTH_FUNCTION;
         break;
      case WEEK_INTERVAL:
         func = SQLHelper.WEEK_FUNCTION;
         break;
      case DAY_INTERVAL:
         func = SQLHelper.DAY_FUNCTION;
         break;
      case HOUR_INTERVAL:
         func = supportTime ? isDB2Time ?
            SQLHelper.TIME_HOUR_FUNCTION2 : SQLHelper.TIME_HOUR_FUNCTION :
            SQLHelper.HOUR_FUNCTION;
         break;
      case MINUTE_INTERVAL:
         func = supportTime ? isDB2Time ?
            SQLHelper.TIME_MINUTE_FUNCTION2 : SQLHelper.TIME_MINUTE_FUNCTION :
            SQLHelper.MINUTE_FUNCTION;
         break;
      case SECOND_INTERVAL:
         func = supportTime ? isDB2Time ?
            SQLHelper.TIME_SECOND_FUNCTION2 : SQLHelper.TIME_SECOND_FUNCTION :
            SQLHelper.SECOND_FUNCTION;
         break;
      case QUARTER_OF_YEAR_PART:
         func = SQLHelper.QUARTER_PART_FUNCTION;
         break;
      case MONTH_OF_YEAR_PART:
         func = SQLHelper.MONTH_PART_FUNCTION;
         break;
      case WEEK_OF_YEAR_PART:
         func = SQLHelper.WEEK_PART_FUNCTION;
         break;
      case DAY_OF_MONTH_PART:
         func = SQLHelper.DAY_PART_FUNCTION;
         break;
      case DAY_OF_WEEK_PART:
         func = SQLHelper.DAY_OF_WEEK_FUNCTION;
         break;
      case HOUR_OF_DAY_PART:
         func = isDB2Time ? SQLHelper.HOUR_PART_FUNCTION2 :
            SQLHelper.HOUR_PART_FUNCTION;
         break;
      case MINUTE_OF_HOUR_PART:
         func = isDB2Time ? SQLHelper.MINUTE_PART_FUNCTION2 :
            SQLHelper.MINUTE_PART_FUNCTION;
         break;
      case SECOND_OF_MINUTE_PART:
         func = isDB2Time ? SQLHelper.SECOND_PART_FUNCTION2 :
            SQLHelper.SECOND_PART_FUNCTION;
         break;
      case NONE_INTERVAL:
         func = SQLHelper.NONE_FUNCTION;
         break;
      case DAY_OF_YEAR_PART:
      case DAY_OF_QUARTER_PART:
      case WEEK_OF_MONTH_PART:
      case WEEK_OF_QUARTER_PART:
      case MONTH_OF_QUARTER_PART:
      case MONTH_OF_QUARTER_FULL_WEEK_PART:
      case MONTH_OF_FULL_WEEK:
      case QUARTER_OF_FULL_WEEK_PART:
      case QUARTER_OF_FULL_WEEK:
      case YEAR_OF_FULL_WEEK:
      case MONTH_OF_FULL_WEEK_PART:
         throw new RuntimeException("Date grouping not supported in SQL: " + option);
      default:
         throw new RuntimeException("Unknown date grouping: " + option);
      }

      String expression;

      try {
         expression = helper.getFunction(func, field);
      }
      catch(UnsupportedOperationException uoe) {
         // @by ankitmathur, For Bug #1128, If the helper does not contain the
         // function, return an empty string so that post processing can take
         // care of the grouping. This logic is to treat this case the same
         // as if the helper did not exist at all since it is providing no help
         // in this case anyways.
         return "";
      }

      return expression;
   }

   /**
    * Check if this date range ref is mergeable.
    * @return <tt>true</tt> if mergeable, <tt>false</tt> otherwise.
    */
   public static boolean isMergeable(String dbtype, int option) {
      String func = "";

      try {
         func = getExpression(dbtype, new AttributeRef(), option);
      }
      catch(Exception e) {
      }

      return func.length() > 0;
   }

   /**
    * Check if this date range ref is mergeable.
    * @return <tt>true</tt> if mergeable, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isMergeable() {
      if(!isMergeable(getDBType(), option)) {
         return false;
      }

      if(!isMergeable(ref)) {
         return false;
      }

      try {
         // check for unsupported date function
         getExpression();
         return true;
      }
      // date grouping function not available in this database
      catch(UnsupportedOperationException ex) {
         return false;
      }
   }

   /**
    * Check if expression is editable.
    * @return <tt>true</tt> if editable, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isExpressionEditable() {
      return false;
   }

   /**
    * Get the script expression of this reference.
    * @return a script expression.
    */
   @Override
   public String getScriptExpression() {
      return getScriptExpression(ref, option, forceDcToDateWeekOfMonth);
   }

   /**
    * Get the script expression of this reference.
    * @return a script expression.
    */
   public static String getScriptExpression(DataRef ref, int option) {
      return getScriptExpression(ref, option, null);
   }

   /**
    * Get the script expression of this reference.
    * @return a script expression.
    */
   public static String getScriptExpression(DataRef ref, int option,
                                            Integer forceDcToDateWeekOfMonth)
   {
      if(ref == null) {
         return "";
      }

      // do not get expression from DateRangeRef itself
      String field;

      if(!(ref instanceof DateRangeRef) && ref instanceof ExpressionRef) {
         field = ((ExpressionRef) ref).getExpression();
         int idx = field != null ? field.lastIndexOf(";") : -1;

         if(idx != -1) {
            field = field.substring(0, idx);
         }
      }
      else {
         field = "field['" + ref.getName() + "']";
      }

      if(forceDcToDateWeekOfMonth == null) {
         forceDcToDateWeekOfMonth = -1;
      }

      return "inetsoft.uql.asset.DateRangeRef.getDataJS(" + option +
         ", " + field + ", " + forceDcToDateWeekOfMonth + " )";
   }

   /**
    * For use from js script.
    * @hidden
    */
   public static Object getDataJS(int option, Object date) {
      return getDataJS(option, date, -1);
   }

   /**
    * For use from js script.
    * @hidden
    */
   public static Object getDataJS(int option, Object date, int forceDcToDateWeekOfMonth) {
      if(option == DateRangeRef.NONE) {
         return date;
      }

      Date date0 = inetsoft.util.script.JavaScriptEngine.getDate(date);

      if(date0 == null && date instanceof String && !StringUtils.isEmpty(date + "")) {
         try {
            long time = Long.parseLong(date + "");
            date0 = new Date(time);
         }
         catch(NumberFormatException ignore) {
         }

         if(date0 == null) {
            date0 = inetsoft.util.script.JavaScriptEngine.parseDate((String) date, false);
         }
      }

      return getData(option, date0, forceDcToDateWeekOfMonth);
   }

   /**
    * Check if this expression is sql expression.
    * @return true if is, false otherwise.
    */
   @Override
   public boolean isSQL() {
      return true;
   }

   /**
    * Set this date range ref is manual create or not.
    */
   public void setAutoCreate(boolean autoCreate) {
      this.autoCreate = autoCreate;
   }

   /**
    * Check if this date range ref is manual create or not.
    * @return true if is, false otherwise.
    */
   public boolean isAutoCreate() {
      return autoCreate;
   }

   /**
    * Get a list of all attributes that are referenced by this object.
    * @return an Enumeration containing AttributeRef objects.
    */
   @Override
   public Enumeration getAttributes() {
      Vector list = new Vector();
      list.add(ref);
      return list.elements();
   }

   /**
    * Write the CDATA of this object.
    * @param writer the output stream to which to write the XML data
    */
   @Override
   protected void writeCDATA(PrintWriter writer) {
      writer.println("<![CDATA[ ]]>");
   }

   /**
    * Write the CDATA of this object.
    * @param dos the output stream to which to write the OutputStream data.
    */
   @Override
   protected void writeCDATA2(DataOutputStream dos) {
   }

   /**
    * Check if apply auto drill.
    */
   public boolean isApplyAutoDrill() {
      return drill;
   }

   /**
    * Set whether apply auto drill.
    */
   public void setApplyAutoDrill(boolean drill) {
      this.drill = drill;
   }

   /**
    * Check if use strict rule to get data type of this date range column.
    */
   public boolean isStrictDataType() {
      return strictDataType;
   }

   public void setStrictDataType(boolean strictDataType) {
      this.strictDataType = strictDataType;
   }

   /**
    * Check whether the inner data ref is mergable.
    */
   private boolean isMergeable(DataRef dref) {
      if(dref.isExpression()) {
         if(dref instanceof SQLExpressionRef) {
            if(!((SQLExpressionRef) dref).isMergeable()) {
               return false;
            }
         }
         else {
            return false;
         }
      }

      if(dref instanceof DataRefWrapper) {
         if(!isMergeable(((DataRefWrapper) dref).getDataRef())) {
            return false;
         }
      }

      return true;
   }

   private static final int[] toptions = new int[] {
      HOUR_INTERVAL, MINUTE_INTERVAL, SECOND_INTERVAL, HOUR_OF_DAY_PART, MINUTE_OF_HOUR_PART,
      SECOND_OF_MINUTE_PART
   };

   private static DateTimeProcessor getDateTimeProcessor() {
      int firstDayOfWeek = Tool.getFirstDayOfWeek();

      if(firstDayOfWeek != DateRangeRef.firstDayOfWeek) {
         calendars.remove();
         DateRangeRef.firstDayOfWeek = firstDayOfWeek;
      }

      return calendars.get();
   }

   private static ThreadLocal<DateTimeProcessor> calendars = new ThreadLocal() {
      @Override
      protected DateTimeProcessor initialValue() {
         return new DateTimeProcessor();
      }
   };

   private DataRef ref;
   private String attr;
   private String originalType = XSchema.STRING;
   private int option = DateRangeRef.YEAR_INTERVAL;
   private boolean autoCreate = true;
   private boolean drill = true;
   private boolean strictDataType = false;
   private Integer forceDcToDateWeekOfMonth;
   private transient String dbversion;
   private static int firstDayOfWeek = Integer.MIN_VALUE;
}
