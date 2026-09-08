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
package inetsoft.web.api.schedule;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.web.json.OffsetDateTimeDeserializer;
import inetsoft.web.json.OffsetDateTimeSerializer;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Objects;

/**
 * {@code ScheduleTimeCondition} describes a condition that is satisfied by some time-based
 * criteria.
 */
@Validated
@Schema(description = "A condition that is satisfied by some time-based criteria.")
public class TimeCondition extends ScheduleCondition {
   /**
    * Creates a new instance of {@code TimeCondition}.
    */
   public TimeCondition() {
      setConditionType(ConditionType.TIME);
   }

   /**
    * Creates a new instance of {@code TimeCondition}.
    *
    * @param condition the condition object being represented.
    */
   public TimeCondition(inetsoft.sree.schedule.TimeCondition condition) {
      if(condition.getDate() != null) {
         setDate(OffsetDateTime.ofInstant(condition.getDate().toInstant(), ZoneId.of("UTC")));
      }

      setConditionType(ConditionType.TIME);
      setType(Type.fromValue(condition.getType()));
      setDayOfMonth(condition.getDayOfMonth());
      setDayOfWeek(condition.getDayOfWeek());
      setWeekOfMonth(condition.getWeekOfMonth());
      setHour(condition.getHour());
      setMinute(condition.getMinute());
      setSecond(condition.getSecond());
      setInterval(condition.getInterval());
      setHourEnd(condition.getHourEnd());
      setMinuteEnd(condition.getMinuteEnd());
      setSecondEnd(condition.getSecondEnd());
      setHourlyInterval(condition.getHourlyInterval());
      setDaysOfWeek(condition.getDaysOfWeek());
      setMonthsOfYear(condition.getMonthsOfYear());
      setWeekdayOnly(condition.isWeekdayOnly());
      setTimeRange(condition.getTimeRange() == null ? null :
         condition.getTimeRange().getName());
      setTimeZone(condition.getTimeZone().getID());
   }

   /**
    * Gets the date and time at which the condition is satisfied.
    *
    * @return the date.
    */
   @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssZ")
   @JsonSerialize(using = OffsetDateTimeSerializer.class)
   @JsonDeserialize(using = OffsetDateTimeDeserializer.class)
   @Schema(description = "The date and time at which the condition is satisfied.", format = "date-time")
   public OffsetDateTime getDate() {
      return date;
   }

   /**
    * Sets the date and time at which the condition is satisfied.
    *
    * @param date the date.
    */
   public void setDate(OffsetDateTime date) {
      this.date = date;
   }

   /**
    * Gets the day of the month (1-31) on which the condition is satisfied.
    *
    * @return the day of the month.
    */
   @NotNull
   @Min(0L)
   @Max(31L)
   @Schema(
      description = "The day of the month on which the condition is satisfied. This must be be between 1 and 31, inclusive.",
      example = "0")
   public int getDayOfMonth() {
      return dayOfMonth;
   }

   /**
    * Sets the day of the month (1-31) on which the condition is satisfied.
    *
    * @param dayOfMonth the day of the month.
    */
   public void setDayOfMonth(int dayOfMonth) {
      this.dayOfMonth = dayOfMonth;
   }

   /**
    * Gets the day of the week (1-7) on which the condition is satisfied.
    *
    * @return the day of the week.
    */
   @NotNull
   @Min(0L)
   @Max(7L)
   @Schema(
      description = "The day of the week on which the condition is satisfied. This must be between 1 (Sunday) and 7 (Saturday), inclusive.",
      example = "6")
   public int getDayOfWeek() {
      return dayOfWeek;
   }

   /**
    * Sets the day of the week (1-7) on which the condition is satisfied.
    *
    * @param dayOfWeek the day of the week.
    */
   public void setDayOfWeek(int dayOfWeek) {
      this.dayOfWeek = dayOfWeek;
   }

   /**
    * Gets the days of the week (1-7) on which the condition is satisfied.
    *
    * @return the days of the week.
    */
   @NotNull
   @Min(1L)
   @Max(7L)
   @Schema(
      description = "The days of the week on which the condition is satisfied. The values must be a between 1 (Sunday) and 7 (Saturday), inclusive.",
      example = "[]")
   public int[] getDaysOfWeek() {
      return daysOfWeek;
   }

   /**
    * Sets the days of the week (1-7) on which the condition is satisfied.
    *
    * @param daysOfWeek the days of the week.
    */
   public void setDaysOfWeek(int[] daysOfWeek) {
      this.daysOfWeek = daysOfWeek;
   }

   /**
    * Gets the week of the month (1-5) in which the condition is satisfied.
    *
    * @return the week of the month.
    */
   @NotNull
   @Min(0L)
   @Max(5L)
   @Schema(
      description = "The week of the month in which the condition is satisfied. This must be between 1 and 5, inclusive",
      example = "0")
   public int getWeekOfMonth() {
      return weekOfMonth;
   }

   /**
    * Sets the week of the month (1-5) in which the condition is satisfied.
    *
    * @param weekOfMonth the week of the month.
    */
   public void setWeekOfMonth(int weekOfMonth) {
      this.weekOfMonth = weekOfMonth;
   }

   /**
    * Gets the months of the year (0-11) in which the condition is satisfied.
    *
    * @return the months of the year.
    */
   @NotNull
   @Min(0L)
   @Max(11L)
   @Schema(
      description = "The months of the year on which the condition is satisified. The values must be between 0 (January) and 11 (December), inclusive.",
      example = "[]")
   public int[] getMonthsOfYear() {
      return monthsOfYear;
   }

   /**
    * Sets the months of the year (0-11) in which the condition is satisfied.
    *
    * @param monthsOfYear the months of the year.
    */
   public void setMonthsOfYear(int[] monthsOfYear) {
      this.monthsOfYear = monthsOfYear;
   }

   /**
    * Gets the hour of the day (0-23) at which the condition is satisfied.
    *
    * @return the hour of the day.
    */
   @NotNull
   @Min(-1L)
   @Max(23L)
   @Schema(
      description = "The hour of the day at which the condition is satisfied. This must be between 0 and 23, inclusive. May be -1 if not used.",
      example = "0")
   public int getHour() {
      return hour;
   }

   /**
    * Sets the hour of the day (0-23) at which the condition is satisfied.
    *
    * @param hour the hour of the day.
    */
   public void setHour(int hour) {
      this.hour = hour;
   }

   /**
    * Gets the minute of the hour (0-59) at which the condition is satisfied.
    *
    * @return the minute.
    */
   @NotNull
   @Min(-1L)
   @Max(59L)
   @Schema(
      description = "The minute of the hour at which the condition is satisfied. This must be between 0 and 59, inclusive. May be -1 if not used.",
      example = "30")
   public int getMinute() {
      return minute;
   }

   /**
    * Sets the minute of the hour (0-59) at which the condition is satisfied.
    *
    * @param minute the minute.
    */
   public void setMinute(int minute) {
      this.minute = minute;
   }

   /**
    * Gets the second (0-59) at which the condition is satisfied.
    *
    * @return the second.
    */
   @NotNull
   @Min(-1L)
   @Max(59L)
   @Schema(
      description = "The second at which the condition is satisfied. This must be between 0 and 59, inclusive. May be -1 if not used.",
      example = "0")
   public int getSecond() {
      return second;
   }

   /**
    * Sets the second (0-59) at which the condition is satisfied.
    *
    * @param second the second.
    */
   public void setSecond(int second) {
      this.second = second;
   }

   /**
    * Gets the hour of the day (0-23) at which the hourly interval period ends.
    *
    * @return the hour of the day.
    */
   @Min(-1L)
   @Max(23L)
   @Schema(
      description = "The hour of the day at which the hourly interval period ends. This must be between 0 and 23, inclusive. May be -1 if not used.",
      example = "0",
      extensions = @Extension(properties = @ExtensionProperty(name = "x-since", value = "1.0")))
   public int getHourEnd() {
      return hourEnd;
   }

   /**
    * Sets the hour of the day (0-23) at which the hourly interval period ends.
    *
    * @param hour the hour of the day.
    */
   public void setHourEnd(int hour) {
      this.hourEnd = hour;
   }

   /**
    * Gets the minute of the hour (0-59) at which the hourly interval period ends.
    *
    * @return the minute.
    */
   @Min(-1L)
   @Max(59L)
   @Schema(
      description = "The minute of the hour at which the hourly interval period ends. This must be between 0 and 59, inclusive. May be -1 if not used.",
      example = "30",
      extensions = @Extension(properties = @ExtensionProperty(name = "x-since", value = "1.0")))
   public int getMinuteEnd() {
      return minuteEnd;
   }

   /**
    * Sets the minute of the hour (0-59) at which the hourly interval period ends.
    *
    * @param minute the minute.
    */
   public void setMinuteEnd(int minute) {
      this.minuteEnd = minute;
   }

   /**
    * Gets the second (0-59) at which the hourly interval period ends.
    *
    * @return the second.
    */
   @Min(-1L)
   @Max(59L)
   @Schema(
      description = "The second at which the hourly interval period ends. This must be between 0 and 59, inclusive. May be -1 if not used.",
      example = "0",
      extensions = @Extension(properties = @ExtensionProperty(name = "x-since", value = "1.0")))
   public int getSecondEnd() {
      return secondEnd;
   }

   /**
    * Sets the second (0-59) at which the hourly interval period ends.
    *
    * @param second the second.
    */
   public void setSecondEnd(int second) {
      this.secondEnd = second;
   }

   /**
    * Gets the hourly interval within the period by which the condition is satisfied.
    *
    * @return the range interval.
    *
    * @since 2019
    */
   @Min(0L)
   @Schema(
      description = "The hourly interval within the time period by which the condition is satisfied.",
      example = "0",
      extensions = @Extension(properties = @ExtensionProperty(name = "x-since", value = "1.0")))
   public float getHourlyInterval() {
      return hourlyInterval;
   }

   /**
    * Sets the hourly interval within the time period by which the condition is satisfied.
    *
    * @param interval the range interval.
    *
    * @since 2019
    */
   public void setHourlyInterval(float interval) {
      this.hourlyInterval = interval;
   }

   /**
    * Gets the time range in which the condition is satisfied.
    *
    * @return the name of the time range.
    */
   @Schema(description = "The name of a configured time range in which the condition is satisfied.")
   public String getTimeRange() {
      return timeRange;
   }

   /**
    * Sets the time range in which the condition is satisfied.
    *
    * @param timeRange the name of the time range.
    */
   public void setTimeRange(String timeRange) {
      this.timeRange = timeRange;
   }

   /**
    * Gets the type of time condition to apply. The value of this property determines which fields
    * are valid.
    *
    * @return the time condition type.
    */
   @NotNull
   @Schema(
      description = "The type of time condition to apply. The value of this field determines which fields are valid.",
      example = "DAY_OF_WEEK")
   public Type getType() {
      return type;
   }

   /**
    * Sets the type of time condition to apply. The value of this property determines which fields
    * are valid.
    *
    * @param type the time condition type.
    */
   public void setType(Type type) {
      this.type = type;
   }

   /**
    * Gets the interval by which the range is repeated.
    *
    * @return the range interval.
    *
    * @since 2019
    */
   @Min(0L)
   @Schema(
      description = "The interval by which the condition is satisfied.",
      example = "0",
      extensions = @Extension(properties = @ExtensionProperty(name = "x-since", value = "1.0")))
   public int getInterval() {
      return interval;
   }

   /**
    * Sets the interval by which the range is repeated.
    *
    * @param interval the range interval.
    *
    * @since 2019
    */
   public void setInterval(int interval) {
      this.interval = interval;
   }

   /**
    * Gets whether the condition is restricted to weekdays.
    *
    * @return <i>true</i> if the condition is restricted to weekdays.
    *
    * @since 2019
    */
   @NotNull
   @Schema(
      description = "A flag that indicates if the condition should only be satisified on weekdays.",
      example = "false",
      extensions = @Extension(properties = @ExtensionProperty(name = "x-since", value = "1.0")))
   public boolean isWeekdayOnly() {
      return weekdayOnly;
   }

   /**
    * Sets whether the condition is restricted to weekdays.
    *
    * @param weekdayOnly <i>true</i> if the condition is restricted to weekdays.
    *
    * @since 2019
    */
   public void setWeekdayOnly(boolean weekdayOnly) {
      this.weekdayOnly = weekdayOnly;
   }

   /**
    * Gets the string ID of the condition's time zone.
    *
    * @return the time zone's string ID.
    *
    * @since 2023
    */
   @Schema(
      description = "The time zone's string ID. Will use condition's current time zone if null.",
      example = "America/New_York",
      extensions = @Extension(properties = @ExtensionProperty(name = "x-since", value = "1.0")))
   public String getTimeZone() {
      return timeZone;
   }

   /**
    * Sets the condition time zone using the string ID.
    *
    * @param timeZone the time zone's string ID.
    *
    * @since 2023
    */
   public void setTimeZone(String timeZone) {
      this.timeZone = timeZone;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass() || !super.equals(o)) {
         return false;
      }

      TimeCondition that = (TimeCondition) o;
      return ((date == null && that.date == null) ||
            (date != null && date.equals(that.date))) &&
         ((timeRange == null && that.timeRange == null) ||
            (timeRange != null && timeRange.equals(that.timeRange))) &&
         ((timeZone == null && that.timeZone == null) ||
            (timeZone != null && timeZone.equals(that.timeZone))) &&
         dayOfMonth == that.dayOfMonth &&
         dayOfWeek == that.dayOfWeek &&
         weekOfMonth == that.weekOfMonth &&
         hour == that.hour &&
         minute == that.minute &&
         second == that.second &&
         hourEnd == that.hourEnd &&
         minuteEnd == that.minuteEnd &&
         secondEnd == that.secondEnd &&
         interval == that.interval &&
         hourlyInterval == that.hourlyInterval &&
         type == that.type &&
         weekdayOnly == that.weekdayOnly;
   }

   @Override
   public int hashCode() {
      return Objects.hash(
         getConditionType(),
         date,
         dayOfMonth,
         dayOfWeek,
         Arrays.hashCode(daysOfWeek),
         Arrays.hashCode(monthsOfYear),
         weekOfMonth,
         hour,
         minute,
         second,
         hourEnd,
         minuteEnd,
         secondEnd,
         timeRange,
         interval,
         hourlyInterval,
         type,
         weekdayOnly,
         timeZone
      );
   }

   @Override
   public String toString() {
      return "TimeCondition{" +
         "conditionType=" + getConditionType() +
         ", date=" + date +
         ", dayOfMonth=" + dayOfMonth +
         ", dayOfWeek=" + dayOfWeek +
         ", daysOfWeek=" + Arrays.toString(daysOfWeek) +
         ", monthsOfYear=" + Arrays.toString(monthsOfYear) +
         ", weekOfMonth=" + weekOfMonth +
         ", hour=" + hour +
         ", minute=" + minute +
         ", second=" + second +
         ", hourEnd=" + hourEnd +
         ", minuteEnd=" + minuteEnd +
         ", secondEnd=" + secondEnd +
         ", timeRange='" + timeRange + "'" +
         ", interval=" + interval +
         ", hourlyInterval=" + hourlyInterval +
         ", type=" + type +
         ", weekdayOnly=" + weekdayOnly +
         ", timeZone=" + timeZone +
         '}';
   }

   private OffsetDateTime date;
   private int dayOfMonth;
   private int dayOfWeek;
   private int[] daysOfWeek = new int[0];
   private int[] monthsOfYear = new int[0];
   private int weekOfMonth;
   private int hour;
   private int minute;
   private int second;
   private int hourEnd;
   private int minuteEnd;
   private int secondEnd;
   private float hourlyInterval;
   private String timeRange;
   private int interval;
   private Type type;
   private boolean weekdayOnly;
   private String timeZone;

   /**
    * Enumeration of the types of time conditions.
    */
   public enum Type {
      /**
       * Time condition that is satisfied at one date and time.
       */
      AT(inetsoft.sree.schedule.TimeCondition.AT),

      /**
       * @deprecated Use {@link Type#EVERY_MONTH} with the appropriate values
       *
       * Time condition that is satisfied on a particular day of the month.
       */
      @Deprecated
      DAY_OF_MONTH(inetsoft.sree.schedule.TimeCondition.EVERY_MONTH),

      /**
       * Time condition that is satisfied on a particular day of the week.
       */
      DAY_OF_WEEK(inetsoft.sree.schedule.TimeCondition.EVERY_WEEK),

      /**
       * Time condition that is satisfied every day at a particular time.
       */
      EVERY_DAY(inetsoft.sree.schedule.TimeCondition.EVERY_DAY),

      /**
       * Time condition that is satisfied every week at a particular time.
       */
      EVERY_WEEK(inetsoft.sree.schedule.TimeCondition.EVERY_WEEK),

      /**
       * Time condition that is satisfied every month at a particular time.
       */
      EVERY_MONTH(inetsoft.sree.schedule.TimeCondition.EVERY_MONTH),

      /**
       * @deprecated Use {@link Type#EVERY_MONTH} with the appropriate values
       *
       * Time condition that is satisfied during a particular week of every month.
       */
      @Deprecated
      WEEK_OF_MONTH(inetsoft.sree.schedule.TimeCondition.EVERY_MONTH),

      /**
       * Time condition that is satisfied every interval within a particular time period of certain days of the week.
       */
      EVERY_HOUR(inetsoft.sree.schedule.TimeCondition.EVERY_HOUR);

      private int value;

      Type(int value) {
         this.value = value;
      }

      public int value() {
         return value;
      }

      public static Type fromValue(int value) {

         for(Type type : values()) {
            if(value == type.value) {
               if(EVERY_MONTH.value == value) {
                  return EVERY_MONTH;
               }

               return type;
            }
         }

         return null;
      }
   }
}
