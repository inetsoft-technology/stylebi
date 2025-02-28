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
package inetsoft.sree.schedule;

import inetsoft.sree.SreeEnv;
import inetsoft.util.*;
import org.apache.ignite.binary.*;
import org.w3c.dom.Element;

import java.io.*;
import java.text.DateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * TimeCondition is time based schedule condition. It's possible to
 * specify time in a number of different formats: exact time and day,
 * day of month, day of week, and week of month. Time condition and
 * be repeating condition (other than exact time and day). If a time
 * condition is declared to be repeating, the associated task will
 * be rescheduled at the next cycle after it's run.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class TimeCondition implements ScheduleCondition, XMLSerializable, Binarylizable {
   /**
    * Time condition type for exact time.
    */
   public static final int AT = 0;
   /**
    * Time condition type for every day.
    */
   public static final int EVERY_DAY = 1;
   /**
    * Time condition type for day of month.
    */
   public static final int DAY_OF_MONTH = 2;
   /**
    * Time condition type for day of week.
    */
   public static final int DAY_OF_WEEK = 3;
   /**
    * Time condition type for week of month.
    */
   public static final int WEEK_OF_MONTH = 4;
   /**
    * Time condition type for week of year.
    */
   public static final int WEEK_OF_YEAR = 5;
   /**
    * Time condition type for every week.
    */
   public static final int EVERY_WEEK = 6;
   /**
    * Time condition type for every month.
    */
   public static final int EVERY_MONTH = 7;
   /**
    * Hour condition type for every month.
    */
   public static final int EVERY_HOUR = 8;
   /**
    * Last day of a month. Other days can be derived from this value.
    * For example, second day of end of month is LAST_DAY_OF_MONTH-1.
    */
   public static final int LAST_DAY_OF_MONTH = -1;

   /**
    * Create a default time condition.
    */
   public TimeCondition() {
   }

   private void setCurrTimeformat() {
      // @by jasonshobe, don't cache date formats, they are not thread safe
      dateFmt = Tool.getDateFormat();
      // @by jasonshobe, don't cache date formats, they are not thread safe
      timeFmt = Tool.getTimeFormat(true);
   }

   /**
    * Get type of this condition.
    */
   public int getType() {
      return type;
   }

   /**
    * Sets the type of this condition.
    *
    * @param type the condition type.
    */
   public void setType(int type) {
      this.type = type;
   }

   /**
    * Create a time condition at exact time and date.
    * @param time date and time of this condition.
    */
   public static TimeCondition at(Date time) {
      TimeCondition cond = new TimeCondition();

      cond.time = time;
      cond.type = AT;
      return cond;
   }

   /**
    * Create a time condition at specified time everyday.
    * @param hour hour of day.
    * @param minute minute in the hour.
    * @param second second in the minute.
    */
   public static TimeCondition at(int hour, int minute, int second) {
      TimeCondition cond = new TimeCondition();

      cond.hour = hour;
      cond.minute = minute;
      cond.second = second;
      cond.type = EVERY_DAY;
      return cond;
   }

   /**
    * Create a time condition at day of month.
    * @param day_of_month day of month.
    * @param hour hour of day.
    * @param minute minute in the hour.
    * @param second second in the minute.
    */
   public static TimeCondition atDayOfMonth(int day_of_month, int hour, int minute, int second) {
      TimeCondition cond = new TimeCondition();

      cond.day_of_month = day_of_month;
      cond.hour = hour;
      cond.minute = minute;
      cond.second = second;
      cond.type = EVERY_MONTH;
      return cond;
   }

   /**
    * Create a time condition at day of week.
    * @param day_of_week day of week.
    * @param hour hour of day.
    * @param minute minute in the hour.
    * @param second second in the minute.
    */
   public static TimeCondition atDayOfWeek(int day_of_week, int hour, int minute, int second) {
      return atDaysOfWeek(new int[] {day_of_week}, hour, minute, second);
   }

   /**
    * Create a time condition at days of week.
    * @param days_of_week day of week.
    * @param hour hour of day.
    * @param minute minute in the hour.
    * @param second second in the minute.
    */
   public static TimeCondition atDaysOfWeek(int[] days_of_week, int hour, int minute, int second) {
      return getTimeCondition(days_of_week, hour, minute, second, EVERY_WEEK);
   }

   /**
    * Create a time condition at hours.
    * @param days_of_week day of week.
    * @param hour hour of day.
    * @param minute minute in the hour.
    * @param second second in the minute.
    */
   public static TimeCondition atHours(int[] days_of_week, int hour, int minute, int second) {
      return getTimeCondition(days_of_week, hour, minute, second, EVERY_HOUR);
   }

   private static TimeCondition getTimeCondition(int[] days_of_week, int hour, int minute,
                                                 int second, int everyWeek)
   {
      TimeCondition cond = new TimeCondition();

      cond.days_of_week = days_of_week;
      cond.hour = hour;
      cond.minute = minute;
      cond.second = second;
      cond.type = everyWeek;
      return cond;
   }

   /**
    * Create a time condition at week of month.
    * @param week_of_month week of month.
    * @param day_of_week day of week.
    * @param hour hour of day.
    * @param minute minute in the hour.
    * @param second second in the minute.
    */
   public static TimeCondition atWeekOfMonth(int week_of_month, int day_of_week, int hour,
                                             int minute, int second)
   {
      TimeCondition cond = new TimeCondition();

      cond.week_of_month = week_of_month;
      return getTimeCondition(day_of_week, hour, minute, second, cond, EVERY_MONTH);
   }

   /**
    * Create a time condition at week of year.
    * @param week_of_year week of year.
    * @param day_of_week day of week.
    * @param hour hour of day.
    * @param minute minute in the hour.
    * @param second second in the minute.
    */
   public static TimeCondition atWeekOfYear(int week_of_year, int day_of_week, int hour, int minute,
                                            int second)
   {
      TimeCondition cond = new TimeCondition();

      cond.week_of_year = week_of_year;
      return getTimeCondition(day_of_week, hour, minute, second, cond, WEEK_OF_YEAR);
   }

   private static TimeCondition getTimeCondition(int day_of_week, int hour, int minute, int second,
                                                 TimeCondition cond, int everyMonth)
   {
      cond.day_of_week = day_of_week;
      cond.hour = hour;
      cond.minute = minute;
      cond.second = second;
      cond.type = everyMonth;
      return cond;
   }

   /**
    * Set the timezone of this server.
    * @param tz time zone.
    */
   public void setTimeZone(TimeZone tz) {
      if(tz == null) {
         this.tz = TimeZone.getDefault();
      }
      else {
         this.tz = tz;
      }
   }

   /**
    * Get the time zone of this time condition.
    */
   public TimeZone getTimeZone() {
      return tz;
   }

   /**
    * Check the condition.
    * @param curr current time.
    * @return true if the condition is met.
    */
   @Override
   public boolean check(long curr) {
      return curr >= scheduleTime;
   }

   @Override
   public long getRetryTime(long curr) {
      return getRetryTime(curr, curr);
   }

   // this method should be added to ScheduleCondition as a default
   // method in 13.1 so we can remove the special logic for calling
   // getRetryTime in ScheduleTask
   /**
    * Get the next time to retry the condition.
    * @param curr current time.
    * @param lastRun last time the task is run.
    * @return the next time to retry. Negative value to stop retry.
    */
   public long getRetryTime(long curr, long lastRun) {
      if(type == AT) {
         if(time.getTime() >= curr) {
            scheduleTime = time.getTime();
            return time.getTime();
         }
         else {
            return -1;
         }
      }

      curr = toLocalTimeZone(curr);
      lastRun = toLocalTimeZone(lastRun);
      Calendar cal1 = Calendar.getInstance(getTimeZone());

      if(getInterval() > 1 && lastRun > 0) {
         cal1.setTimeInMillis(lastRun);
      }

      cal1.set(Calendar.HOUR_OF_DAY, hour);
      cal1.set(Calendar.MINUTE, minute);
      cal1.set(Calendar.SECOND, second);

      // cal1 contains current date and scheduled time
      switch(type) {
      case EVERY_DAY:
         if(isWeekdayOnly()) {
            // if weekdays, do not run on weekend
            while(cal1.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
                  cal1.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY ||
                  cal1.getTimeInMillis() < curr)
            {
               cal1.add(Calendar.DATE, 1);
            }
         }
         else{
            if(getInterval() <= 0) {
               return -1;
            }

            if(cal1.getTimeInMillis() < curr) {
               cal1.add(Calendar.DATE, getInterval());
            }
         }

         break;
      case EVERY_WEEK:
         if(days_of_week.length <= 0) {
            return -1;
         }

         boolean satisfied = false;

         for(int day = cal1.get(Calendar.DAY_OF_WEEK); day < 8; day++) {
            cal1.set(Calendar.DAY_OF_WEEK, day);

            if(containsIn(days_of_week, day) && cal1.getTimeInMillis() >= curr)
            {
               satisfied = true;
               break;
            }
         }

         if(satisfied) {
            break;
         }

         // increase every week to next
         if(getInterval() > 1) {
            cal1.add(Calendar.DATE, (getInterval() - 1) * 7);
         }

         while(containsIn(days_of_week, cal1.get(Calendar.DAY_OF_WEEK)) &&
            cal1.get(Calendar.DAY_OF_WEEK) != days_of_week[0])
         {
            cal1.add(Calendar.DATE, 1);
         }

         while(!containsIn(days_of_week, cal1.get(Calendar.DAY_OF_WEEK)) ||
            cal1.getTimeInMillis() < curr)
         {
            cal1.add(Calendar.DATE, 1);
         }

         break;
      case EVERY_HOUR:
         if(days_of_week.length <= 0) {
            return -1;
         }

         boolean found = false;
         long start = cal1.getTimeInMillis();

         if(containsIn(days_of_week,  cal1.get(Calendar.DAY_OF_WEEK))) {
            Calendar calEnd = Calendar.getInstance(getTimeZone());
            calEnd.setTime(new Date(curr));
            calEnd.set(Calendar.HOUR_OF_DAY, hour_end);
            calEnd.set(Calendar.MINUTE, minute_end);
            calEnd.set(Calendar.SECOND, second_end);
            long end = calEnd.getTimeInMillis();
            long interval = (long) (getHourlyInterval() * 3600000);

            while(start < end && end > curr) {
               if(start >= curr) {
                  found = true;
                  break;
               }
               else {
                  start += interval;
               }
            }
         }

         if(found) {
            cal1.setTimeInMillis(start);
         }
         else {
            while(true) {
               cal1.add(Calendar.DATE, 1);

               if(containsIn(days_of_week, cal1.get(Calendar.DAY_OF_WEEK))) {
                  break;
               }
            }
         }

         break;
      case EVERY_MONTH:
         if(months_of_year.length <= 0) {
            return -1;
         }

         if(getWeekOfMonth() >= 0) {
            int maxDays = 2 * 365;

            while(!isNthSpecifiedDayOfMonth(cal1) ||
               cal1.getTimeInMillis() < curr ||
               !containsIn(months_of_year, cal1.get(Calendar.MONTH)))
            {
               cal1.add(Calendar.DATE, 1);

               // a condition may not be possible (e.g. 6th week of a month
               // and friday), avoid infinite loop
               if(maxDays-- < 0) {
                  break;
               }
            }
         }
         else {
            while(!containsIn(months_of_year, cal1.get(Calendar.MONTH)) ||
               cal1.getTimeInMillis() < curr ||
               day_of_month < 0 && !isNthLastDayOfMonth(-day_of_month, cal1) ||
               day_of_month > 0 && !isNthFirstDayOfMonth(day_of_month, cal1))
            {
               cal1.add(Calendar.DATE, 1);
            }
         }

         break;
      }

      scheduleTime = cal1.getTimeInMillis();

      return toServerTimeZone(scheduleTime);
   }

   private long toLocalTimeZone(long timemillis) {
      Calendar calendar = Calendar.getInstance();
      calendar.setTimeInMillis(timemillis);
      calendar.setTimeZone(getTimeZone());
      long ntimemillis = calendar.getTimeInMillis();

      return ntimemillis;
   }

   private long toServerTimeZone(long timemillis) {
      Calendar calendar = Calendar.getInstance();
      calendar.setTimeInMillis(timemillis);
      calendar.setTimeZone(TimeZone.getDefault());
      long ntimemillis = calendar.getTimeInMillis();

      return ntimemillis;
   }

   public String toString() {
      Catalog catalog = Catalog.getCatalog();
      setCurrTimeformat();
      boolean twelveHourSystem = SreeEnv.getBooleanProperty("schedule.time.12hours");

      if(type == AT) {
         TimeZone serverTZ = TimeZone.getDefault();

         Calendar cal = Calendar.getInstance(serverTZ);
         long now = new Date().getTime();
         Date displayTime = new Date(time.getTime() + tz.getOffset(now) - serverTZ.getOffset(now));

         cal.setTime(displayTime);

         boolean dst = tz.inDaylightTime(displayTime);
         return catalog.getString("TimeCondition") + ": at " +
            dateFmt.format(displayTime) + " " + timeFmt.format(displayTime) +
            (twelveHourSystem ? cal.get(Calendar.AM_PM) > 0 ? "PM" : "AM" : "") +
            "(" + tz.getDisplayName(dst, TimeZone.LONG) + ")";
      }
      else if(type == EVERY_DAY) {
         StringBuilder buffer = new StringBuilder();
         Calendar cal = Calendar.getInstance(TimeZone.getDefault());
         cal.setTime(new Date());
         cal.set(Calendar.HOUR_OF_DAY, hour);
         cal.set(Calendar.MINUTE, minute);
         cal.set(Calendar.SECOND, second);
         buffer.append(catalog.getString("TimeCondition")).append(": ")
            .append(timeFmt.format(cal.getTime()) +
               (twelveHourSystem ? cal.get(Calendar.AM_PM) > 0 ? "PM" : "AM" : ""));

         boolean dst = tz.inDaylightTime(cal.getTime());
         buffer.append("(").append(tz.getDisplayName(dst, TimeZone.LONG)).append(")");

         if(isWeekdayOnly()) {
            buffer.append(", ").append(catalog.getString("em.scheduler.timeCondition.weekday"));
         }
         else if(interval > 0) {
            buffer.append(", ").append(catalog.getString(
               "em.scheduler.timeCondition.daysInterval", interval + ""));
         }
         else {
            buffer.append(", ").append(catalog.getString("Every Day"));
         }

         return buffer.toString();
      }
      else if(type == EVERY_WEEK) {
         StringBuilder buffer = new StringBuilder();
         buffer.append(catalog.getString("TimeCondition")).append(": ");

         if(days_of_week.length > 0) {
            buffer.append(Arrays.stream(days_of_week)
                             .mapToObj(this::getWeekDayName)
                             .collect(Collectors.joining(", ")))
               .append(" ").append(catalog.getString("of Week"));
         }

         if(interval > 0) {
            buffer.append(", ").append(catalog.getString(
               "em.scheduler.timeCondition.weeksInterval", interval + ""));
         }

         return buffer.toString();
      }
      else if(type == EVERY_HOUR) {
         StringBuilder buffer = new StringBuilder();
         buffer.append(catalog.getString("TimeCondition")).append(": ");

         if(days_of_week.length > 0) {
            buffer.append(catalog.getString("Hour of")).append(" ")
               .append(Arrays.stream(days_of_week)
                          .mapToObj(this::getWeekDayName)
                          .collect(Collectors.joining(", ")));
         }

         if(hourlyInterval > 0) {
            buffer.append(", ").append(catalog.getString(
               "em.scheduler.timeCondition.hourlyInterval", hourlyInterval + ""));
         }

         return buffer.toString();
      }
      else if(type == EVERY_MONTH) {
         StringBuilder buffer = new StringBuilder();
         buffer.append(catalog.getString("TimeCondition")).append(": ");

         if(week_of_month > 0) {
            String[] weeks = {"1st", "2nd", "3rd", "4th", "5th", "6th"};
            buffer.append(weeks[week_of_month - 1]).append(" ");
            buffer.append(getWeekDayName(day_of_week)).append(" ");
            buffer.append(catalog.getString("of month")).append(" ");
         }
         else {
            if(day_of_month == -1) {
               buffer.append(" ").append(catalog.getString("Last day of month"));
            }
            else if(day_of_month == -2) {
               buffer.append(" ").append(catalog.getString(
                  "em.scheduler.timeCondition.dayBeforeLast"));
            }
            else {
               buffer.append(" ").append(catalog.getString(
                  "em.scheduler.timeCondition.dayOfMonth", day_of_month + ""));
            }

            buffer.append(" ");
         }

         if(months_of_year.length > 0) {
            for(int i = 0; i< months_of_year.length; i++) {
               if(i > 0) {
                  buffer.append(", ");
               }

               buffer.append(getMonthName(months_of_year[i]));
            }

            buffer.append(" ").append(catalog.getString("of year"));
         }

         return buffer.toString();
      }

      return catalog.getString("TimeCondition");
   }

   /**
    * Get the specified time and day.
    * @return date or null if not exact date.
    */
   public Date getDate() {
      return time;
   }

   /**
    * Sets the date and time at which the task should be executed.
    *
    * @param date the date and time.
    */
   public void setDate(Date date) {
      this.time = date;
   }

   /**
    * Get day of month.
    * @return day of month or -1 if not specified.
    */
   public int getDayOfMonth() {
      return day_of_month;
   }

   /**
    * Sets the day of the month on which the task should be executed.
    *
    * @param dayOfMonth the day of the month.
    */
   public void setDayOfMonth(int dayOfMonth) {
      this.day_of_month = dayOfMonth;
   }

   /**
    * Get day of week.
    * @return day of week or -1 if not specified.
    */
   public int getDayOfWeek() {
      return day_of_week;
   }

   /**
    * Sets the week day on which the task should be executed.
    *
    * @param dayOfWeek the week day.
    */
   public void setDayOfWeek(int dayOfWeek) {
      this.day_of_week = dayOfWeek;
   }

   /**
    * Get week of month.
    * @return week of month or -1 if not specified.
    */
   public int getWeekOfMonth() {
      return week_of_month;
   }

   /**
    * Sets the week of the month in which the task should be executed.
    *
    * @param weekOfMonth the week of the month.
    */
   public void setWeekOfMonth(int weekOfMonth) {
      this.week_of_month = weekOfMonth;
   }

   /**
    * Get week of year.
    * @return week of year or -1 if not specified.
    */
   public int getWeekOfYear() {
      return week_of_year;
   }

   /**
    * Sets the week of the year in which the task should be executed.
    *
    * @param weekOfYear the week of the year.
    */
   public void setWeekOfYear(int weekOfYear) {
      this.week_of_year = weekOfYear;
   }

   /**
    * Get hour of day.
    */
   public int getHour() {
      return hour;
   }

   /**
    * Sets the hour of the day at which the task should be executed.
    *
    * @param hour the hour of the day.
    */
   public void setHour(int hour) {
      this.hour = hour;
   }

   /**
    * Get minute.
    */
   public int getMinute() {
      return minute;
   }

   /**
    * Sets the minute of the hour at which the task should be executed.
    *
    * @param minute the minute of the hour.
    */
   public void setMinute(int minute) {
      this.minute = minute;
   }

   /**
    * Get second.
    */
   public int getSecond() {
      return second;
   }

   /**
    * Sets the second at which the task should be executed.
    *
    * @param second the second value.
    */
   public void setSecond(int second) {
      this.second = second;
   }

   /**
    * Get end hour of day.
    */
   public int getHourEnd() {
      return hour_end;
   }

   /**
    * Sets the end hour of the day at which the task should be executed.
    *
    * @param hour the end hour of the day.
    */
   public void setHourEnd(int hour) {
      this.hour_end = hour;
   }

   /**
    * Get end minute.
    */
   public int getMinuteEnd() {
      return minute_end;
   }

   /**
    * Sets the end minute of the hour at which the task should be executed.
    *
    * @param minute the end minute of the hour.
    */
   public void setMinuteEnd(int minute) {
      this.minute_end = minute;
   }

   /**
    * Get end second.
    */
   public int getSecondEnd() {
      return second_end;
   }

   /**
    * Sets the end second at which the task should be executed.
    *
    * @param second the end second value.
    */
   public void setSecondEnd(int second) {
      this.second_end = second;
   }

   /**
    * Get the interval.
    */
   public int getInterval() {
      return this.interval;
   }

   /**
    * Set the interval of days or weeks to repeat the condition.
    */
   public void setInterval(int interval) {
      this.interval = interval;
   }

   /**
    * Get the interval of hour.
    */
   public float getHourlyInterval() {
      return hourlyInterval;
   }

   /**
    * Set the interval of hour to repeat the condition.
    */
   public void setHourlyInterval(float hourlyInterval) {
      this.hourlyInterval = hourlyInterval;
   }

   /**
    * Get if the condition would be skipped if the day is not weekday.
    */
   public boolean isWeekdayOnly() {
      return this.weekdayOnly;
   }

   /**
    * Set if the condition should be skipped if the day is Saturday or Sunday.
    */
   public void setWeekdayOnly(boolean weekdayOnly) {
      this.weekdayOnly = weekdayOnly;
   }

   /**
    * Get days of week.
    */
   public int[] getDaysOfWeek() {
      return days_of_week;
   }

   /**
    * Set days of week.
    */
   public void setDaysOfWeek(int[] days_of_week) {
       this.days_of_week = days_of_week;
   }

   /**
    * Get months of year.
    */
   public int[] getMonthsOfYear() {
      return months_of_year;
   }

   /**
    * Set months of year.
    */
   public void setMonthsOfYear(int[] months_of_year) {
      this.months_of_year = months_of_year;
   }

   /**
    * Gets the time range from which to calculate the start time.
    *
    * @return the time range.
    */
   public TimeRange getTimeRange() {
      return timeRange;
   }

   /**
    * Sets the time range from which to calculate the start time.
    *
    * @param timeRange the time range.
    */
   public void setTimeRange(TimeRange timeRange) {
      this.timeRange = timeRange;
   }

   /**
    * Check if is ajax.
    * @hidden
    */
   public boolean isAJAX() {
      return ajax;
   }

   /**
    * Set whether is ajax.
    * @hidden
    */
   public void setAJAX(boolean ajax) {
      this.ajax = ajax;
   }

   /**
    * Get the hour.
    */
   protected int getHour(int hour, int diff) {
      hour += diff;

      if(hour >= 24) {
         hour -= 24;
      }
      else if(hour < 0) {
         hour += 24;
      }

      return hour;
   }

   private void fixDateDiff(Calendar calendar, int hour, int diff) {
      hour += diff;

      if(hour >= 24) {
         calendar.add(Calendar.DAY_OF_MONTH, 1);
      }
      else if(hour < 0) {
         calendar.add(Calendar.DAY_OF_MONTH, -1);
      }
   }

   /**
    * Get the hour.
    */
   protected int getMinute(int minute, int diff) {
      minute += diff;

      if(minute >= 60) {
         minute -= 60;
      }
      else if(minute < 0) {
         minute += 60;
      }

      return minute;
   }

   /**
    * Write itself to an xml file.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<Condition type=\"TimeCondition\" timeType=\"" +
         type + "\"");
      writeAttributes(writer);
      writer.print(">");

      if(timeRange != null) {
         timeRange.writeXML(writer);
      }

      writer.print("</Condition>");
   }

   protected int getHourOffset() {
      TimeZone cur = TimeZone.getDefault();
      long coff = cur.getRawOffset();
      long off = tz.getRawOffset();

      return (int) ((coff - off) / ONE_HOUR);
   }

   // get offset hours and minutes
   protected int[] getTimeZoneOffset() {
      TimeZone cur = TimeZone.getDefault();
      long coff = cur.getRawOffset();
      long off = tz.getRawOffset();

      return new int[] { (int) ((coff - off) / ONE_HOUR),
         (int) (((coff - off) % ONE_HOUR) / ONE_MINUTE) };
   }

   protected void writeAttributes(PrintWriter writer) {
      StringBuilder buffer = new StringBuilder();
      // at web side, timezone is meaningless now
      int hoff = ajax ? getHourOffset() : 0;

      if(tz != null) {
         buffer.append(" timezone=\"").append(tz.getRawOffset()).append(":")
            .append(tz.getID()).append("\"");
      }

      if(type == AT) {
         buffer.append(" time=\"").append(getDate().getTime());

         if(ajax) {
            buffer.append("\" tzoffset=\"")
               .append(TimeZone.getDefault().getOffset(getDate().getTime()));
         }

         buffer.append("\"");
      }
      else {
         buffer.append(" hour=\"").append(getHour(getHour(), hoff)).append("\" minute=\"")
            .append(getMinute()).append("\" second=\"").append(getSecond()).append("\"");

         buffer.append(" hour_end=\"").append(getHour(getHourEnd(), hoff))
            .append("\" minute_end=\"").append(getMinuteEnd()).append("\" second_end=\"")
            .append(getSecondEnd()).append("\"");

         if(type == EVERY_MONTH) {
            if(getWeekOfMonth() > 0) {
               buffer.append(" dayOfWeek=\"").append(getDayOfWeek()).append("\" weekOfMonth=\"")
                  .append(getWeekOfMonth()).append("\"");
            }
            else {
               buffer.append(" dayOfMonth=\"").append(getDayOfMonth()).append("\"");
            }

            if(months_of_year.length > 0) {
               buffer.append(" monthsOfYear=\"").append(Tool.arrayToString(getMonthsOfYear()))
                  .append("\"");
            }
         }
         else if(type == EVERY_WEEK) {
            if(days_of_week.length > 0) {
               buffer.append(" daysOfWeek=\"").append(Tool.arrayToString(days_of_week))
                  .append("\"");
            }

            if(interval > 0) {
               buffer.append(" interval=\"").append(interval).append("\"");
            }
         }
         else if(type == EVERY_HOUR) {
            if(days_of_week.length > 0) {
               buffer.append(" daysOfWeek=\"").append(Tool.arrayToString(days_of_week))
                  .append("\"");
            }

            if(hourlyInterval > 0) {
               buffer.append(" hourlyInterval=\"").append(hourlyInterval).append("\"");
            }
         }
         else if(type == EVERY_DAY) {
            buffer.append(" weekday=\"").append(weekdayOnly).append("\"");

            if(interval > 0) {
               buffer.append(" interval=\"").append(interval).append("\"");
            }
         }
      }

      writer.print(buffer.toString());
   }

   /**
    * Parse itself from an xml file.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      String typeStr = Tool.getAttribute(tag, "timeType");

      if(typeStr == null) {
         throw new Exception(Catalog.getCatalog().getString(
            "em.scheduler.timeCondition.typeMissing"));
      }

      this.type = Integer.parseInt(typeStr);

      if(type == TimeCondition.AT) {
         long time = Long.parseLong(Objects.requireNonNull(Tool.getAttribute(tag, "time")));

         String tzoffset = Tool.getAttribute(tag, "tzoffset");

         if(tzoffset != null) {
            long client = Long.parseLong(tzoffset);
            long server = TimeZone.getDefault().getOffset(time);
            time -= client + server;
         }

         this.time = new Date(time);
      }
      else if(type == TimeCondition.EVERY_WEEK) {
         String daysOfWeek = Tool.getAttribute(tag, "daysOfWeek");
         String[] days = daysOfWeek == null ?
            new String[0] : Tool.split(daysOfWeek, ',');
         int[] days_of_week = new int[days.length];

         for(int x = 0; x < days_of_week.length; x++) {
            days_of_week[x] = Integer.parseInt(days[x]);
         }

         int hour = Tool.getCompatibleHour(Integer.parseInt
            (Objects.requireNonNull(Tool.getAttribute(tag, "hour"))));
         int minute = Integer.parseInt(Objects.requireNonNull(Tool.getAttribute(tag, "minute")));
         int second = Integer.parseInt(Objects.requireNonNull(Tool.getAttribute(tag, "second")));
         String intervalStr = Tool.getAttribute(tag, "interval");

         if(Tool.getAttribute(tag, "hour_end") != null) {
            this.hour_end = Tool.getCompatibleHour(Integer.parseInt(
                                                      Tool.getAttribute(tag, "hour_end")));
            this.minute_end = Integer.parseInt(Tool.getAttribute(tag, "minute_end"));
            this.second_end = Integer.parseInt(Tool.getAttribute(tag, "second_end"));
         }

         this.days_of_week = days_of_week;
         this.hour = hour;
         this.minute = minute;
         this.second = second;

         if(intervalStr != null && !intervalStr.trim().equals("")) {
            this.interval = Integer.parseInt(intervalStr);
         }
      }
      else if(type == TimeCondition.EVERY_HOUR) {
         String daysOfWeek = Tool.getAttribute(tag, "daysOfWeek");
         String[] days = daysOfWeek == null ?
            new String[0] : Tool.split(daysOfWeek, ',');
         int[] days_of_week = new int[days.length];

         for(int x = 0; x < days_of_week.length; x++) {
            days_of_week[x] = Integer.parseInt(days[x]);
         }

         int hour = Tool.getCompatibleHour(Integer.parseInt
            (Objects.requireNonNull(Tool.getAttribute(tag, "hour"))));
         int minute = Integer.parseInt(Objects.requireNonNull(Tool.getAttribute(tag, "minute")));
         int second = Integer.parseInt(Objects.requireNonNull(Tool.getAttribute(tag, "second")));
         String intervalStr = Tool.getAttribute(tag, "hourlyInterval");

         if(Tool.getAttribute(tag, "hour_end") != null) {
            this.hour_end = Tool.getCompatibleHour(Integer.parseInt(
                                                      Tool.getAttribute(tag, "hour_end")));
            this.minute_end = Integer.parseInt(Tool.getAttribute(tag, "minute_end"));
            this.second_end = Integer.parseInt(Tool.getAttribute(tag, "second_end"));
         }

         this.days_of_week = days_of_week;
         this.hour = hour;
         this.minute = minute;
         this.second = second;

         if(intervalStr != null && !intervalStr.trim().equals("")) {
            this.hourlyInterval = Float.parseFloat(intervalStr);
         }
      }
      else if(type == TimeCondition.EVERY_MONTH) {
         int hour = Tool.getCompatibleHour(Integer.parseInt
            (Tool.getAttribute(tag, "hour")));
         int minute = Integer.parseInt(Tool.getAttribute(tag, "minute"));
         int second = Integer.parseInt(Tool.getAttribute(tag, "second"));

         if(Tool.getAttribute(tag, "hour_end") != null) {
            this.hour_end = Tool.getCompatibleHour(Integer.parseInt(
                                                      Tool.getAttribute(tag, "hour_end")));
            this.minute_end = Integer.parseInt(Tool.getAttribute(tag, "minute_end"));
            this.second_end = Integer.parseInt(Tool.getAttribute(tag, "second_end"));
         }

         this.hour = hour;
         this.minute = minute;
         this.second = second;

         String day_of_monthStr = Tool.getAttribute(tag, "dayOfMonth");

         if(day_of_monthStr != null) {
            this.day_of_month = Integer.parseInt(day_of_monthStr);
         }
         else {
            this.day_of_week =
               Integer.parseInt(Objects.requireNonNull(Tool.getAttribute(tag, "dayOfWeek")));
            this.week_of_month =
               Integer.parseInt(Objects.requireNonNull(Tool.getAttribute(tag, "weekOfMonth")));
         }

         String monthsOfYear = Tool.getAttribute(tag, "monthsOfYear");
         String[] months = monthsOfYear == null ?
            new String[0] : Tool.split(monthsOfYear, ',');
         int[] months_of_year = new int[months.length];

         for(int x = 0; x < months_of_year.length; x++) {
            months_of_year[x] = Integer.parseInt(months[x]);
         }

         this.months_of_year = months_of_year;
      }
      else if(type == TimeCondition.EVERY_DAY) {
         int hour = Tool.getCompatibleHour(Integer.parseInt
            (Tool.getAttribute(tag, "hour")));
         int minute = Integer.parseInt(Tool.getAttribute(tag, "minute"));
         int second = Integer.parseInt(Tool.getAttribute(tag, "second"));

         if(Tool.getAttribute(tag, "hour_end") != null) {
            this.hour_end = Tool.getCompatibleHour(Integer.parseInt(
                                                      Tool.getAttribute(tag, "hour_end")));
            this.minute_end = Integer.parseInt(Tool.getAttribute(tag, "minute_end"));
            this.second_end = Integer.parseInt(Tool.getAttribute(tag, "second_end"));
         }

         this.hour = hour;
         this.minute = minute;
         this.second = second;

         String intervalStr = Tool.getAttribute(tag, "interval");

         if(intervalStr != null && !intervalStr.trim().equals("")) {
            this.interval = Integer.parseInt(intervalStr);
         }

         this.weekdayOnly = "true".equals(Tool.getAttribute(tag, "weekday"));
      }
      else {
         throw new Exception(Catalog.getCatalog().getString(
            "em.scheduler.timeCondition.unknown", type + ""));
      }

      String prop = Tool.getAttribute(tag, "timezone");

      if(prop != null && prop.length() > 0) {
         int idx = prop.indexOf(":");
//         int offset = Integer.parseInt((idx > 0) ?
//            prop.substring(0, idx) :
//            prop);
         String id = (idx > 0) ? prop.substring(idx + 1) : "";
         //this.tz = new SimpleTimeZone(offset, id);
         // fix the Daylight Savings problem, @see bug1192125481196
         this.tz = TimeZone.getTimeZone(id);
      }

      Element element;

      if((element = Tool.getChildNodeByTagName(tag, "timeRange")) != null) {
         timeRange = new TimeRange();
         timeRange.parseXML(element);
      }
      else {
         timeRange = null;
      }
   }

   /**
    * Get the text week day name.
    * @param day day of week.
    */
   protected String getWeekDayName(int day) {
      switch(day) {
      case Calendar.SUNDAY:
         return Catalog.getCatalog().getString("Sunday");
      case Calendar.MONDAY:
         return Catalog.getCatalog().getString("Monday");
      case Calendar.TUESDAY:
         return Catalog.getCatalog().getString("Tuesday");
      case Calendar.WEDNESDAY:
         return Catalog.getCatalog().getString("Wednesday");
      case Calendar.THURSDAY:
         return Catalog.getCatalog().getString("Thursday");
      case Calendar.FRIDAY:
         return Catalog.getCatalog().getString("Friday");
      case Calendar.SATURDAY:
         return Catalog.getCatalog().getString("Saturday");
      }

      return Integer.toString(day);
   }

   /**
    * Get the text month name.
    * @param month month of year.
    */
   protected String getMonthName(int month) {
      switch(month) {
      case Calendar.JANUARY:
         return Catalog.getCatalog().getString("January");
      case Calendar.FEBRUARY:
         return Catalog.getCatalog().getString("February");
      case Calendar.MARCH:
         return Catalog.getCatalog().getString("March");
      case Calendar.APRIL:
         return Catalog.getCatalog().getString("April");
      case Calendar.MAY:
         return Catalog.getCatalog().getString("May");
      case Calendar.JUNE:
         return Catalog.getCatalog().getString("June");
      case Calendar.JULY:
         return Catalog.getCatalog().getString("July");
      case Calendar.AUGUST:
         return Catalog.getCatalog().getString("August");
      case Calendar.SEPTEMBER:
         return Catalog.getCatalog().getString("September");
      case Calendar.OCTOBER:
         return Catalog.getCatalog().getString("October");
      case Calendar.NOVEMBER:
         return Catalog.getCatalog().getString("November");
      case Calendar.DECEMBER:
         return Catalog.getCatalog().getString("December");
      }

      return Integer.toString(month);
   }

   public boolean equals(Object val) {
      if(!(val instanceof TimeCondition)) {
         return false;
      }

      TimeCondition tc = (TimeCondition) val;

      return (tc.getType() == type && Tool.equals(tc.getDate(), time) &&
         tc.getDayOfMonth() == day_of_month &&
         tc.getDayOfWeek() == day_of_week &&
         tc.getWeekOfMonth() == week_of_month &&
         tc.getWeekOfYear() == week_of_year && tc.getHour() == hour &&
         tc.getMinute() == minute && tc.getSecond() == second) &&
         isEquals(tc.getDaysOfWeek(), days_of_week) &&
         isEquals(tc.getMonthsOfYear(), months_of_year) &&
         tc.getInterval() == interval &&
         tc.getHourlyInterval() == hourlyInterval &&
         tc.isWeekdayOnly() == weekdayOnly;
   }

   /**
    * Check if two object equals, null equals null.
    */
   private boolean isEquals(int[] arr1, int[] arr2) {
      if(arr1.length != arr2.length) {
         return false;
      }

      for(int i = 0; i < arr1.length; i++) {
         if(arr1[i] != arr2[i]) {
            return false;
         }
      }

      return true;
   }

   /**
    * Check if the array contains the specified element.
    */
   protected boolean containsIn(int[] arr, int n) {
      for(int i1 : arr) {
         if(i1 == n) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if is the last nth day of a month.
    */
   private boolean isNthLastDayOfMonth(int n, Calendar calendar) {
      Calendar cal1 = Calendar.getInstance(tz);
      Calendar cal2 = Calendar.getInstance(tz);
      cal1.setTime(calendar.getTime());
      cal2.setTime(calendar.getTime());

      int month = calendar.get(Calendar.MONTH);

      cal1.add(Calendar.DATE, n - 1);
      int month1 = cal1.get(Calendar.MONTH);

      cal2.add(Calendar.DATE, n);
      int month2 = cal2.get(Calendar.MONTH);

      return (month1 == month && month2 != month) ||
         (month1 != month && month2 == month);
   }

   /**
    * Check if is the nth day of a month.
    */
   private boolean isNthFirstDayOfMonth(int n, Calendar calendar) {
      return n == calendar.get(Calendar.DATE);
   }

   /**
    * Check if is the nth secified day of a month. For example, 2008-12-07 is
    * the first sunday of the december. The specified day is from day_of_week.
    * The Nth is from week_of_month.
    * @param calendar the time needed to check.
    */
   private boolean isNthSpecifiedDayOfMonth(Calendar calendar) {
      if(week_of_month <= 0) {
         return true;
      }

      int date = calendar.get(Calendar.DATE); // get the day of month
      boolean isInNthWeek =
         (date <= 7 * week_of_month) && (date > 7 * (week_of_month -1));

      return isInNthWeek && calendar.get(Calendar.DAY_OF_WEEK) == day_of_week;
   }

   private void writeObject(ObjectOutputStream stream) throws IOException {
      stream.defaultWriteObject();
      stream.writeUTF(tz.getID());
   }

   private void readObject(java.io.ObjectInputStream s)
      throws ClassNotFoundException, java.io.IOException
   {
      s.defaultReadObject();
      tz = TimeZone.getTimeZone(s.readUTF());
      // Bug #62748, do not call setCurrTimeformat() here as it makes a call
      // to SreeEnv to get a property which requires hazelcast to be
      // initialized first. If it's called from hazelcast migration operation
      // during hazelcast initialization then it will enter a deadlocked state
   }

   @Override
   public void writeBinary(BinaryWriter writer) throws BinaryObjectException {
      writer.writeLong("scheduleTime", scheduleTime);
      writer.writeInt("type", type);
      writer.writeDate("time", time);
      writer.writeInt("day_of_month", day_of_month);
      writer.writeInt("day_of_week", day_of_week);
      writer.writeInt("week_of_month", week_of_month);
      writer.writeInt("week_of_year", week_of_year);
      writer.writeInt("hour", hour);
      writer.writeInt("minute", minute);
      writer.writeInt("second", second);
      writer.writeInt("hour_end", hour_end);
      writer.writeInt("minute_end", minute_end);
      writer.writeInt("second_end", second_end);
      writer.writeIntArray("days_of_week", days_of_week);
      writer.writeIntArray("months_of_year", months_of_year);
      writer.writeInt("interval", interval);
      writer.writeFloat("hourlyInterval", hourlyInterval);
      writer.writeBoolean("weekdayOnly", weekdayOnly);
      writer.writeBoolean("ajax", ajax);
      writer.writeObject("timeRange", timeRange);
      writer.writeString("tz", tz.getID());
   }

   @Override
   public void readBinary(BinaryReader reader) throws BinaryObjectException {
      this.scheduleTime = reader.readLong("scheduleTime");
      this.type = reader.readInt("type");
      this.time = reader.readDate("time");
      this.day_of_month = reader.readInt("day_of_month");
      this.day_of_week = reader.readInt("day_of_week");
      this.week_of_month = reader.readInt("week_of_month");
      this.week_of_year = reader.readInt("week_of_year");
      this.hour = reader.readInt("hour");
      this.minute = reader.readInt("minute");
      this.second = reader.readInt("second");
      this.hour_end = reader.readInt("hour_end");
      this.minute_end = reader.readInt("minute_end");
      this.second_end = reader.readInt("second_end");
      this.days_of_week = reader.readIntArray("days_of_week");
      this.months_of_year = reader.readIntArray("months_of_year");
      this.interval = reader.readInt("interval");
      this.hourlyInterval = reader.readFloat("hourlyInterval");
      this.weekdayOnly = reader.readBoolean("weekdayOnly");
      this.ajax = reader.readBoolean("ajax");
      this.timeRange = reader.readObject("timeRange");
      this.tz = TimeZone.getTimeZone(reader.readString("tz"));
   }

   private static final long ONE_HOUR = 1000 * 60 * 60;
   private static final long ONE_MINUTE = 1000 * 60;

   private Date time = null;
   // negative value is used for 'last day of month'
   private int day_of_month = 0;
   private int day_of_week = -1;
   private int week_of_month = -1;
   private int week_of_year = -1;
   private int hour = -1;
   private int minute = -1;
   private int second = -1;
   private int hour_end = -1;
   private int minute_end = -1;
   private int second_end = -1;
   private transient TimeZone tz = TimeZone.getDefault();
   private transient DateFormat dateFmt;
   private transient DateFormat timeFmt;
   private int type; // condition type
   private long scheduleTime = Long.MAX_VALUE;
   private int[] days_of_week =  new int[0];
   private int[] months_of_year =  new int[0];
   private int interval = -1;
   private float hourlyInterval = -1;
   private boolean weekdayOnly = false;
   private boolean ajax;
   private TimeRange timeRange;
}
