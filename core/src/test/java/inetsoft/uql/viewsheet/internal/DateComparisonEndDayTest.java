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
package inetsoft.uql.viewsheet.internal;

import inetsoft.test.*;
import inetsoft.test.dc.DcDays;
import inetsoft.test.dc.DcPeriods;
import inetsoft.test.dc.DcShape;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The End Date of a Date Comparison, beyond the fixed dates of {@link DateComparisonRangeMatrixTest}:
 * <ul>
 *   <li>"Today" as the End Date, which is the dialog's default and the one setting every matrix
 *   case leaves out;</li>
 *   <li>the instants of a date-time column: a value is kept exactly when its date is, from
 *   00:00:00.000 to 23:59:59.999.</li>
 * </ul>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DateComparisonEndDayTest {
   private static final String[] WEEK_STARTS = { "sunday", "monday" };

   /**
    * With "Today" as the End Date the comparison keeps what it keeps for today's date as a fixed
    * End Date, for every shape, with the current period included or not.
    */
   @Test
   void todayAsTheEndDayIsTheSameAsTodaysDate() {
      LocalDate today = LocalDate.now();
      List<String> mismatches = new ArrayList<>();
      int checked = 0;

      for(DcShape shape : DcShape.all()) {
         if(shape.isCustom()) {
            continue;
         }

         for(String weekStart : weekStarts(shape)) {
            for(boolean inclusive : new boolean[] { false, true }) {
               String message = WeekStartUtil.withWeekStart(weekStart, () -> {
                  DateComparisonInfo dc = shape.toInfo(today.toString(), false, inclusive,
                                                       DateComparisonInfo.VALUE, 2, true);
                  ((StandardPeriods) dc.getPeriods()).setToDayAsEndDay(true);
                  Set<LocalDate> kept = new TreeSet<>(DcDays.kept(dc));
                  Set<LocalDate> fixed = new TreeSet<>(DcDays.kept(shape.toInfo(
                     today.toString(), false, inclusive, DateComparisonInfo.VALUE, 2, true)));

                  if(kept.equals(fixed)) {
                     return null;
                  }

                  return shape + " " + weekStart + (inclusive ? " incl" : "") + ": " +
                     kept.size() + " days for Today, " + fixed.size() + " for " + today;
               });

               checked++;

               if(message != null) {
                  mismatches.add(message);
               }
            }
         }
      }

      // the clock passed midnight while the loop ran: Today is another date than the fixed one
      if(!today.equals(LocalDate.now())) {
         return;
      }

      assertTrue(checked > 100, "only " + checked + " combinations were checked");
      assertTrue(mismatches.isEmpty(),
                 mismatches.size() + " differ, first:\n" +
                 String.join("\n", mismatches.subList(0, Math.min(20, mismatches.size()))));
   }

   /**
    * A date-time value is kept exactly when its date is kept: at the start of the first day of a
    * run of kept days, in the middle of a day, and at the last millisecond of the last day, and
    * not at the last millisecond before a run or the first one after it. The values are
    * {@code Timestamp}s, as a date-time column holds, and the days around the edges include the
    * daylight saving changes of the surefire zone.
    */
   @Test
   void aDateTimeValueIsKeptExactlyWhenItsDateIs() {
      List<String> mismatches = new ArrayList<>();
      int[] checked = { 0 };

      for(DcShape shape : DcShape.all()) {
         if(shape.isCustom()) {
            continue;
         }

         for(String weekStart : weekStarts(shape)) {
            for(String end : new String[] { "2021-01-02", "2021-03-14", "2021-11-07",
                                            "2021-11-10" })
            {
               WeekStartUtil.withWeekStart(weekStart, () -> {
                  DateComparisonInfo dc = shape.toInfo(end, false, true, DateComparisonInfo.VALUE,
                                                       2, true);
                  Set<LocalDate> kept = new TreeSet<>(DcDays.kept(dc));
                  Predicate<Date> keeps = DcDays.keeps(dc);

                  for(LocalDate day : edges(kept)) {
                     for(Date instant : instants(day)) {
                        checked[0]++;

                        if(keeps.test(instant) != kept.contains(day)) {
                           mismatches.add(shape + " " + weekStart + " end=" + end + ": " +
                                          instant + " is " + (kept.contains(day) ? "" : "not ") +
                                          "kept as a date");
                        }
                     }
                  }
               });
            }
         }
      }

      assertTrue(checked[0] > 1000, "only " + checked[0] + " instants were checked");
      assertTrue(mismatches.isEmpty(),
                 mismatches.size() + " differ, first:\n" +
                 String.join("\n", mismatches.subList(0, Math.min(20, mismatches.size()))));
   }

   private static String[] weekStarts(DcShape shape) {
      return shape.usesWeeks() ? WEEK_STARTS : new String[] { "sunday" };
   }

   /** The first and last day of each run of kept days, and the day before and after it. */
   private static Set<LocalDate> edges(Set<LocalDate> kept) {
      Set<LocalDate> edges = new TreeSet<>();

      for(LocalDate day : kept) {
         if(!kept.contains(day.minusDays(1)) || !kept.contains(day.plusDays(1))) {
            edges.add(day.minusDays(1));
            edges.add(day);
            edges.add(day.plusDays(1));
         }
      }

      return edges;
   }

   /**
    * The first millisecond, the middle and the last millisecond of a day, as date-time values. The
    * middle is noon, or the midpoint of a 23 or 25 hour day on a daylight saving change.
    */
   private static List<Date> instants(LocalDate day) {
      long start = DcPeriods.toDate(day).getTime();
      long next = DcPeriods.toDate(day.plusDays(1)).getTime();
      long middle = start + (next - start) / 2;

      return List.of(new Timestamp(start), new Timestamp(middle), new Timestamp(next - 1));
   }
}
