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
import inetsoft.test.dc.DcModel;
import inetsoft.test.dc.DcPeriods;
import inetsoft.test.dc.DcShape;
import inetsoft.uql.XConstants;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.Timestamp;
import java.time.DayOfWeek;
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
 *   00:00:00.000 to 23:59:59.999;</li>
 *   <li>random End Dates, with the model of the dialog wording.</li>
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
      List<String> mismatches = new ArrayList<>();

      for(DcShape shape : DcShape.all()) {
         if(shape.isCustom()) {
            continue;
         }

         for(String weekStart : weekStarts(shape)) {
            for(boolean inclusive : new boolean[] { false, true }) {
               LocalDate today = LocalDate.now();
               String message = WeekStartUtil.withWeekStart(weekStart, () -> {
                  DateComparisonInfo dc = shape.toInfo(today.toString(), false, inclusive,
                                                       DateComparisonInfo.VALUE, 2, true);
                  ((StandardPeriods) dc.getPeriods()).setToDayAsEndDay(true);
                  Set<LocalDate> kept = new TreeSet<>(DcDays.kept(dc));
                  Set<LocalDate> fixed = new TreeSet<>(DcDays.kept(shape.toInfo(
                     today.toString(), false, inclusive, DateComparisonInfo.VALUE, 2, true)));

                  if(!today.equals(LocalDate.now()) || kept.equals(fixed)) {
                     return null; // or the clock passed midnight while it ran
                  }

                  return shape + " " + weekStart + (inclusive ? " incl" : "") + ": " +
                     kept.size() + " days for Today, " + fixed.size() + " for " + today;
               });

               if(message != null) {
                  mismatches.add(message);
               }
            }
         }
      }

      assertTrue(mismatches.isEmpty(),
                 mismatches.size() + " differ, first:\n" +
                 String.join("\n", mismatches.subList(0, Math.min(20, mismatches.size()))));
   }

   /**
    * A date-time value is kept exactly when its date is kept: at the start of the first day of a
    * run of kept days, at noon, and at the last millisecond of the last day, and not at the last
    * millisecond before a run or the first one after it. The values are {@code Timestamp}s, as a
    * date-time column holds, and the days around the edges include the daylight saving changes of
    * the surefire zone.
    */
   @Test
   void aDateTimeValueIsKeptExactlyWhenItsDateIs() {
      List<String> mismatches = new ArrayList<>();

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

      assertTrue(mismatches.isEmpty(),
                 mismatches.size() + " differ, first:\n" +
                 String.join("\n", mismatches.subList(0, Math.min(20, mismatches.size()))));
   }

   /**
    * Random End Dates (a fixed seed), with the model of the dialog wording and both first days of
    * week. The combinations that are findings of Bug #79061 are left out, as in the range matrix.
    */
   @Test
   void randomEndDatesKeepWhatTheDialogWordingSays() {
      Random random = new Random(79055L);
      List<LocalDate> ends = new ArrayList<>(List.of(LocalDate.now()));
      LocalDate first = LocalDate.of(2019, 1, 1);

      for(int i = 0; i < 40; i++) {
         ends.add(first.plusDays(random.nextInt(4 * 365)));
      }

      List<String> mismatches = new ArrayList<>();
      int checked = 0;

      for(DcShape shape : DcShape.all()) {
         if(shape.isCustom()) {
            continue;
         }

         for(String weekStart : weekStarts(shape)) {
            for(LocalDate end : ends) {
               boolean inclusive = random.nextBoolean();

               if(knownGap(shape, weekStart, end, inclusive)) {
                  continue;
               }

               String message = WeekStartUtil.withWeekStart(weekStart, () -> {
                  Set<LocalDate> model = DcModel.expected(shape, end, inclusive, false, 2);

                  if(model == null) {
                     return "";
                  }

                  Set<LocalDate> kept = new TreeSet<>(DcDays.kept(shape.toInfo(
                     end.toString(), false, inclusive, DateComparisonInfo.VALUE, 2, true)));

                  if(kept.equals(model)) {
                     return null;
                  }

                  Set<LocalDate> missing = new TreeSet<>(model);
                  missing.removeAll(kept);
                  Set<LocalDate> extra = new TreeSet<>(kept);
                  extra.removeAll(model);

                  return shape + " " + weekStart + " end=" + end + " (" + end.getDayOfWeek() + ")" +
                     (inclusive ? " incl" : "") + ": missing " + missing.size() +
                     (missing.isEmpty() ? "" : " from " + ((TreeSet<LocalDate>) missing).first()) +
                     ", extra " + extra.size() +
                     (extra.isEmpty() ? "" : " from " + ((TreeSet<LocalDate>) extra).first());
               });

               if(message == null) {
                  checked++;
               }
               else if(!message.isEmpty()) {
                  mismatches.add(message);
               }
            }
         }
      }

      assertTrue(checked > 3000, "the model covers only " + checked + " combinations");
      assertTrue(mismatches.isEmpty(),
                 mismatches.size() + " of " + (checked + mismatches.size()) + " differ, first:\n" +
                 String.join("\n", mismatches.subList(0, Math.min(40, mismatches.size()))));
   }

   /**
    * Findings of Bug #79061 that the model test leaves out: a week that starts in a period and
    * ends in the next one loses its days there (finding 7), and Same Day by Week misses the End
    * Date's own week when the End Date is the last day of its week (finding 2).
    */
   private static boolean knownGap(DcShape shape, String weekStart, LocalDate end,
                                   boolean inclusive)
   {
      boolean sameDayByWeek = shape.intervalUnit() == XConstants.DAY_DATE_GROUP &&
         shape.contextLevel() == XConstants.WEEK_DATE_GROUP;
      DayOfWeek lastDay = "monday".equals(weekStart) ? DayOfWeek.SUNDAY : DayOfWeek.SATURDAY;

      return sameDayByWeek && shape.periodLevel() != XConstants.WEEK_DATE_GROUP ||
         sameDayByWeek && inclusive && end.getDayOfWeek() == lastDay ||
         shape.intervalUnit() == XConstants.WEEK_DATE_GROUP &&
            shape.contextLevel() != XConstants.WEEK_DATE_GROUP;
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

   /** The first millisecond, noon and the last millisecond of a day, as date-time values. */
   private static List<Date> instants(LocalDate day) {
      long start = DcPeriods.toDate(day).getTime();
      long next = DcPeriods.toDate(day.plusDays(1)).getTime();
      long noon = DcPeriods.toDate(day).getTime() +
         (next - start) / 2;

      return List.of(new Timestamp(start), new Timestamp(noon), new Timestamp(next - 1));
   }
}
