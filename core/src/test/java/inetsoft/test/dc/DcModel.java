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

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The days a Standard Periods Date Comparison keeps, worked out from the wording of the dialog
 * ("Quarter to Date for Same Quarter of Each Year As Of", "Same Week of Month As", ...) and not
 * from the product's code.
 *
 * <p>A period of level P (the periods back from the End Date, as the invariants of the range
 * test count them) holds context units of level C; in each context unit the interval takes one
 * unit of level X, the k-th one, where k is the position of the End Date's own unit in the End
 * Date's context unit. To Date takes that unit from its start to the End Date's offset into its
 * own unit (clamped to the unit), Same takes the whole unit; All takes the whole period. Nothing
 * after the End Date is kept. A week belongs to the period and context unit it starts in, and
 * the weeks of a unit are the weeks that start in it.</p>
 *
 * <p>Not modelled ({@link #expected} returns null): a unit coarser than a week that is shown by
 * week (Year / Quarter / Month to Date by Week, whose wording leaves open how the last partial
 * week is cut), the week of a Year context (Bug #79061, finding 3: week of year or week of
 * month), and a week that a month or quarter of the periods does not have (the End Date is in
 * the 5th week and another month has four: the product takes another week there, the calendar
 * variation bucket of #76391, which the wording does not define).</p>
 */
public final class DcModel {
   private DcModel() {
   }

   /**
    * @param change whether the comparison is Change or Percent, which compares one more period
    *               back.
    * @return the days, or null when the combination is not modelled.
    */
   public static Set<LocalDate> expected(DcShape shape, LocalDate end, boolean inclusive,
                                         boolean change, int preCount)
   {
      int periodLevel = shape.periodLevel();
      int unit = shape.intervalUnit();
      int context = shape.contextLevel();
      boolean aligned = shape.usesWeeks() && periodLevel != XConstants.WEEK_DATE_GROUP;

      if(unit > XConstants.WEEK_DATE_GROUP && aligned ||
         unit == XConstants.WEEK_DATE_GROUP && context == XConstants.YEAR_DATE_GROUP)
      {
         return null;
      }

      DcPeriods periods = new DcPeriods(periodLevel);
      long current = periods.ordinal(aligned ? periods.weekStart(end) : end);
      long first = current - preCount - (change ? 1 : 0);
      long last = inclusive ? current : current - 1;
      Set<LocalDate> days = new TreeSet<>();

      for(long ordinal = first; ordinal <= last; ordinal++) {
         LocalDate start = periods.start(ordinal);
         LocalDate finish = next(periodLevel, start).minusDays(1);

         if(unit < 0) {
            LocalDate from = aligned ? firstWeekStart(start, periods) : start;
            LocalDate to = aligned ? periods.weekStart(finish).plusDays(6) : finish;

            for(LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
               days.add(day);
            }
         }
         else {
            List<LocalDate> unitDays = intervalDays(shape, start, finish, end, periods);

            if(unitDays == null) {
               return null;
            }

            days.addAll(unitDays);
         }
      }

      days.removeIf(day -> day.isAfter(end));

      return days;
   }

   private static List<LocalDate> intervalDays(DcShape shape, LocalDate periodStart,
                                               LocalDate periodEnd, LocalDate end,
                                               DcPeriods periods)
   {
      int unit = shape.intervalUnit();
      int context = shape.contextLevel();
      LocalDate endUnit = start(unit, end, periods);
      LocalDate endContext = start(context, endUnit, periods);
      int k = 0;

      for(LocalDate s = firstUnit(unit, endContext, periods); s.isBefore(endUnit);
          s = next(unit, s))
      {
         k++;
      }

      // a day is placed by its offset into the context unit, the others into their own unit
      long offset = ChronoUnit.DAYS.between(
         unit == XConstants.DAY_DATE_GROUP ? endContext : endUnit, end);
      List<LocalDate> days = new ArrayList<>();

      for(LocalDate c = firstUnit(context, periodStart, periods); !c.isAfter(periodEnd);
          c = next(context, c))
      {
         LocalDate contextEnd = next(context, c);

         if(unit == XConstants.DAY_DATE_GROUP) {
            // the same day of the context unit: the same month and day (the month's last day if
            // it lacks that day), or the same weekday
            days.add(toDateEnd(context, c, end, offset));
            continue;
         }

         LocalDate s = firstUnit(unit, c, periods);
         int index = 0;

         while(s.isBefore(contextEnd) && index < k) {
            s = next(unit, s);
            index++;
         }

         if(!s.isBefore(contextEnd)) {
            if(unit == XConstants.WEEK_DATE_GROUP) {
               return null; // a month or quarter with fewer weeks: see expected()
            }

            continue; // the context unit has no k-th unit
         }

         LocalDate unitEnd = next(unit, s).minusDays(1);
         LocalDate to = shape.isSame() ? unitEnd : min(toDateEnd(unit, s, end, offset), unitEnd);

         for(LocalDate day = s; !day.isAfter(to); day = day.plusDays(1)) {
            days.add(day);
         }
      }

      return days;
   }

   /**
    * The last day To Date takes of a unit that starts on {@code unitStart}: the End Date's own
    * place in its unit. A year, quarter or month is cut at the same month and day (a day the
    * month lacks is the month's last day), a week or a day at the same offset in days.
    */
   private static LocalDate toDateEnd(int unit, LocalDate unitStart, LocalDate end, long offset) {
      int months;

      switch(unit) {
      case XConstants.YEAR_DATE_GROUP:
         months = end.getMonthValue() - 1;
         break;
      case XConstants.QUARTER_DATE_GROUP:
         months = (end.getMonthValue() - 1) % 3;
         break;
      case XConstants.MONTH_DATE_GROUP:
         months = 0;
         break;
      default:
         return unitStart.plusDays(offset);
      }

      LocalDate month = unitStart.plusMonths(months);

      return month.withDayOfMonth(Math.min(end.getDayOfMonth(), month.lengthOfMonth()));
   }

   /** The first unit of a level that starts on or after a day that begins a larger unit. */
   private static LocalDate firstUnit(int level, LocalDate from, DcPeriods periods) {
      return level == XConstants.WEEK_DATE_GROUP ? firstWeekStart(from, periods) : from;
   }

   private static LocalDate firstWeekStart(LocalDate from, DcPeriods periods) {
      LocalDate start = periods.weekStart(from);

      return start.isBefore(from) ? start.plusDays(7) : start;
   }

   private static LocalDate start(int level, LocalDate day, DcPeriods periods) {
      switch(level) {
      case XConstants.YEAR_DATE_GROUP:
         return LocalDate.of(day.getYear(), 1, 1);
      case XConstants.QUARTER_DATE_GROUP:
         return LocalDate.of(day.getYear(), (day.getMonthValue() - 1) / 3 * 3 + 1, 1);
      case XConstants.MONTH_DATE_GROUP:
         return LocalDate.of(day.getYear(), day.getMonthValue(), 1);
      case XConstants.WEEK_DATE_GROUP:
         return periods.weekStart(day);
      default:
         return day;
      }
   }

   private static LocalDate next(int level, LocalDate start) {
      switch(level) {
      case XConstants.YEAR_DATE_GROUP:
         return start.plusYears(1);
      case XConstants.QUARTER_DATE_GROUP:
         return start.plusMonths(3);
      case XConstants.MONTH_DATE_GROUP:
         return start.plusMonths(1);
      case XConstants.WEEK_DATE_GROUP:
         return start.plusDays(7);
      default:
         return start.plusDays(1);
      }
   }

   private static LocalDate min(LocalDate a, LocalDate b) {
      return a.isBefore(b) ? a : b;
   }
}
