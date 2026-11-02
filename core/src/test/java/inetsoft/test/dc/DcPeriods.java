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
package inetsoft.test.dc;

import inetsoft.uql.XConstants;
import inetsoft.util.Tool;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Date;

/**
 * Counts the calendar periods of one level ({@code XConstants.*_DATE_GROUP}), worked out here
 * and never taken from the product. A week starts on the configured first day of week
 * ({@code week.start}, read when this is created), and a week belongs to the period its first
 * day is in, like the {@code *_OF_FULL_WEEK} labels.
 */
public final class DcPeriods {
   private static final ZoneId ZONE = ZoneId.systemDefault();

   private final int level;
   private final DayOfWeek first;
   private final long weekOffset;

   public DcPeriods(int level) {
      this.level = level;
      // Calendar.SUNDAY is 1, DayOfWeek.SUNDAY is 7
      this.first = DayOfWeek.of((Tool.getFirstDayOfWeek() + 5) % 7 + 1);
      this.weekOffset = Math.floorMod(weekStart(LocalDate.of(2021, 1, 1)).toEpochDay(), 7);
   }

   public LocalDate weekStart(LocalDate day) {
      return day.with(TemporalAdjusters.previousOrSame(first));
   }

   /** The index of the period a day is in; consecutive periods have consecutive indexes. */
   public long ordinal(LocalDate day) {
      switch(level) {
      case XConstants.YEAR_DATE_GROUP:
         return day.getYear();
      case XConstants.QUARTER_DATE_GROUP:
         return day.getYear() * 4L + (day.getMonthValue() - 1) / 3;
      case XConstants.MONTH_DATE_GROUP:
         return day.getYear() * 12L + day.getMonthValue() - 1;
      case XConstants.WEEK_DATE_GROUP:
         return Math.floorDiv(weekStart(day).toEpochDay() - weekOffset, 7);
      default:
         return day.toEpochDay();
      }
   }

   /** The first day of a period; a week starts on the first day of week. */
   public LocalDate start(long ordinal) {
      switch(level) {
      case XConstants.YEAR_DATE_GROUP:
         return LocalDate.of((int) ordinal, 1, 1);
      case XConstants.QUARTER_DATE_GROUP:
         return LocalDate.of((int) Math.floorDiv(ordinal, 4),
                             (int) Math.floorMod(ordinal, 4) * 3 + 1, 1);
      case XConstants.MONTH_DATE_GROUP:
         return LocalDate.of((int) Math.floorDiv(ordinal, 12),
                             (int) Math.floorMod(ordinal, 12) + 1, 1);
      case XConstants.WEEK_DATE_GROUP:
         return LocalDate.ofEpochDay(ordinal * 7 + weekOffset);
      default:
         return LocalDate.ofEpochDay(ordinal);
      }
   }

   public String label(long ordinal) {
      switch(level) {
      case XConstants.YEAR_DATE_GROUP:
         return String.valueOf(ordinal);
      case XConstants.QUARTER_DATE_GROUP:
         return Math.floorDiv(ordinal, 4) + "Q" + (Math.floorMod(ordinal, 4) + 1);
      case XConstants.MONTH_DATE_GROUP:
         return String.format("%d-%02d", Math.floorDiv(ordinal, 12), Math.floorMod(ordinal, 12) + 1);
      case XConstants.WEEK_DATE_GROUP:
         return "wk" + LocalDate.ofEpochDay(ordinal * 7 + weekOffset);
      default:
         return LocalDate.ofEpochDay(ordinal).toString();
      }
   }

   public static LocalDate toLocal(Date date) {
      // java.sql.Date has no toInstant()
      return date == null ? null : Instant.ofEpochMilli(date.getTime()).atZone(ZONE).toLocalDate();
   }

   public static Date toDate(LocalDate day) {
      return Date.from(day.atStartOfDay(ZONE).toInstant());
   }
}
