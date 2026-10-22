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
import inetsoft.test.dc.DcKnownGaps;
import inetsoft.test.dc.DcModel;
import inetsoft.test.dc.DcShape;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The interval's own End Date: when the interval does not end at the periods' End Date
 * ({@code endDayAsToDate=false} with an {@code intervalEndDate}), the place in each period where
 * To Date cuts, and the position of the unit in its context, come from that date, while the
 * periods still count back from the periods' End Date.
 *
 * <p>The days are checked against {@link DcModel} (the dialog wording) for every Standard Periods
 * shape, and against a few hand-computed days. The known gaps ({@link inetsoft.test.dc.DcKnownGaps}) are left out.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DateComparisonIntervalEndDateTest {
   private static final String END = "2021-11-10";
   /**
    * Before the End Date (2021-08-17 a Tuesday), at a quarter end, a month end that shorter
    * months lack (2021-01-31), the leap day, and after the End Date (2021-12-20).
    */
   private static final String[] INTERVAL_ENDS = {
      "2021-08-17", "2021-03-31", "2021-01-31", "2020-02-29", "2021-12-20" };

   @Test
   void keptDaysMatchTheDialogWording() {
      List<String> mismatches = new ArrayList<>();
      int checked = 0;

      for(DcShape shape : DcShape.all()) {
         if(shape.isCustom()) {
            continue;
         }

         String[] weekStarts = shape.usesWeeks() ? new String[] { "sunday", "monday" }
                                                 : new String[] { "sunday" };

         for(String weekStart : weekStarts) {
            for(String intervalEnd : INTERVAL_ENDS) {
               for(boolean inclusive : new boolean[] { false, true }) {
                  for(boolean change : new boolean[] { false, true }) {
                     if(change && inclusive ||
                        DcKnownGaps.knownGap(shape, weekStart, END, inclusive, change))
                     {
                        continue;
                     }

                     String message = WeekStartUtil.withWeekStart(weekStart, () -> {
                        Set<LocalDate> model = DcModel.expected(
                           shape, LocalDate.parse(END), LocalDate.parse(intervalEnd), inclusive,
                           change, 2);

                        if(model == null) {
                           return "";
                        }

                        Set<LocalDate> kept = new TreeSet<>(DcDays.kept(shape.toInfo(
                           END, intervalEnd, false, inclusive,
                           change ? DateComparisonInfo.CHANGE : DateComparisonInfo.VALUE, 2,
                           true)));

                        if(kept.equals(model)) {
                           return null;
                        }

                        return shape + " " + weekStart + " intervalEnd=" + intervalEnd +
                           (inclusive ? " incl" : "") + (change ? " change" : "") + ": " +
                           DcModel.difference(model, kept);
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
         }
      }

      assertTrue(checked > 1000, "the model covers only " + checked + " combinations");
      assertTrue(mismatches.isEmpty(),
                 mismatches.size() + " of " + (checked + mismatches.size()) + " differ, first:\n" +
                 String.join("\n", mismatches.subList(0, Math.min(40, mismatches.size()))));
   }

   /** Same Day of Each Year, interval ending 2021-08-17: the 17 August of 2019 and 2020. */
   @Test
   void sameDayOfEachYearIsTheDayOfTheIntervalEnd() {
      assertEquals(List.of(LocalDate.of(2019, 8, 17), LocalDate.of(2020, 8, 17)),
                   days("Y-sameD-gD-cY", "2021-08-17"));
   }

   /** Month to Date by Month in each year, interval ending 2021-08-17: 1..17 August. */
   @Test
   void monthToDateIsTheMonthAndDayOfTheIntervalEnd() {
      List<LocalDate> expected = new ArrayList<>();

      for(int year : new int[] { 2019, 2020 }) {
         for(int day = 1; day <= 17; day++) {
            expected.add(LocalDate.of(year, 8, day));
         }
      }

      assertEquals(expected, days("Y-mtd-gM-cY", "2021-08-17"));
   }

   /**
    * Same Day of Each Month for the quarters Q2 and Q3 of 2021, interval ending 2021-01-31: the
    * 31st, or the last day of a month that lacks it.
    */
   @Test
   void sameDayOfEachMonthIsClampedToTheMonth() {
      assertEquals(List.of(LocalDate.of(2021, 4, 30), LocalDate.of(2021, 5, 31),
                           LocalDate.of(2021, 6, 30), LocalDate.of(2021, 7, 31),
                           LocalDate.of(2021, 8, 31), LocalDate.of(2021, 9, 30)),
                   days("Q-sameD-gD-cM", "2021-01-31"));
   }

   /** Without a date the interval's own End Date is invalid, with one it is not. */
   @Test
   void intervalEndDateIsRequiredWhenItIsNotThePeriodsEndDate() {
      DateComparisonInfo dc = DcShape.byId("Y-sameD-gD-cY").toInfo(
         END, null, false, false, DateComparisonInfo.VALUE, 2, true);
      dc.getInterval().setEndDayAsToDate(false);

      assertTrue(dc.invalid());

      dc.getInterval().setIntervalEndDateValue("2021-08-17");

      assertFalse(dc.invalid());
   }

   private static List<LocalDate> days(String shapeId, String intervalEnd) {
      return DcDays.kept(DcShape.byId(shapeId).toInfo(
         END, intervalEnd, false, false, DateComparisonInfo.VALUE, 2, true));
   }
}
