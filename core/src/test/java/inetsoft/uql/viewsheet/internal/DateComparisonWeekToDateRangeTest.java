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
import inetsoft.test.dc.DcShape;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.asset.AssetCondition;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79347: the ranges of a week-aligned To Date interval (Quarter / Month / Year to Date by
 * Week). Each period's range starts at its first week start and must end at the same week of the
 * period, on the same weekday, as the End Date; the calendar day of the End Date as a cutoff
 * gave inverted ranges (an empty chart) and periods of unequal length.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DateComparisonWeekToDateRangeTest {
   /**
    * Expected ranges are the last periods of the shape, in ascending order. Month To Date with
    * Quarter context and Quarter to Date with Year-period shapes compare the End Date's week
    * start month/quarter (Dec 27 -> December, month 3 of the quarter), not its calendar date.
    */
   static Stream<Arguments> expectedRanges() {
      return Stream.of(
         // End Date in week 13 of Q4 (its week starts Dec 27 / Dec 28)
         args("Q-qtd-gW-cQ", "sunday", "2021-01-02",
              "2020-01-05..2020-04-04", "2020-04-05..2020-07-04", "2020-07-05..2020-10-03"),
         args("Q-qtd-gW-cQ", "monday", "2021-01-02",
              "2020-01-06..2020-04-04", "2020-04-06..2020-07-04", "2020-07-06..2020-10-03"),
         // week 1 of Q1: the same three days for every quarter
         args("Q-qtd-gW-cQ", "sunday", "2021-01-05",
              "2020-04-05..2020-04-07", "2020-07-05..2020-07-07", "2020-10-04..2020-10-06"),
         args("Q-qtd-gW-cQ", "monday", "2021-01-05",
              "2020-04-06..2020-04-07", "2020-07-06..2020-07-07", "2020-10-05..2020-10-06"),
         args("M-mtd-gW-cM", "sunday", "2021-01-02",
              "2020-09-06..2020-10-03", "2020-10-04..2020-10-31", "2020-11-01..2020-11-28"),
         args("M-mtd-gW-cM", "monday", "2021-01-02",
              "2020-09-07..2020-10-03", "2020-10-05..2020-10-31", "2020-11-02..2020-11-28"),
         args("M-mtd-gW-cM", "sunday", "2021-01-05",
              "2020-10-04..2020-10-06", "2020-11-01..2020-11-03", "2020-12-06..2020-12-08"),
         args("M-mtd-gW-cM", "monday", "2021-01-05",
              "2020-10-05..2020-10-06", "2020-11-02..2020-11-03", "2020-12-07..2020-12-08"),
         args("Y-ytd-gW-cY", "sunday", "2021-01-02",
              "2017-01-01..2017-12-30", "2018-01-07..2019-01-05", "2019-01-06..2020-01-04"),
         args("Y-ytd-gW-cY", "sunday", "2021-01-05",
              "2018-01-07..2018-01-09", "2019-01-06..2019-01-08", "2020-01-05..2020-01-07"),
         args("Q-mtd-gW-cQ", "sunday", "2021-01-02",
              "2020-03-01..2020-03-28", "2020-06-07..2020-07-04", "2020-09-06..2020-10-03"),
         args("Q-mtd-gW-cQ", "sunday", "2021-01-05",
              "2020-04-05..2020-04-07", "2020-07-05..2020-07-07", "2020-10-04..2020-10-06"),
         args("Q-mtd-gW-cQ", "monday", "2021-01-02",
              "2020-03-02..2020-03-28", "2020-06-01..2020-06-27", "2020-09-07..2020-10-03"),
         args("Q-mtd-gW-cQ", "monday", "2021-01-05",
              "2020-04-06..2020-04-07", "2020-07-06..2020-07-07", "2020-10-05..2020-10-06"),
         args("Y-qtd-gW-cQ", "sunday", "2021-01-02",
              "2019-01-06..2019-04-06", "2019-04-07..2019-07-06", "2019-07-07..2019-10-05", "2019-10-06..2020-01-04"),
         args("Y-qtd-gW-cQ", "sunday", "2021-01-05",
              "2020-01-05..2020-01-07", "2020-04-05..2020-04-07", "2020-07-05..2020-07-07", "2020-10-04..2020-10-06"),
         args("Y-qtd-gW-cQ", "monday", "2021-01-02",
              "2019-01-07..2019-03-30", "2019-04-01..2019-06-29", "2019-07-01..2019-09-28", "2019-10-07..2020-01-04"),
         args("Y-qtd-gW-cQ", "monday", "2021-01-05",
              "2020-01-06..2020-01-07", "2020-04-06..2020-04-07", "2020-07-06..2020-07-07", "2020-10-05..2020-10-06"),
         args("Y-mtd-gW-cM", "sunday", "2021-01-02"),
         args("Y-mtd-gW-cM", "sunday", "2021-01-05"),
         args("Y-mtd-gW-cM", "monday", "2021-01-02"),
         args("Y-mtd-gW-cM", "monday", "2021-01-05"),
         args("Y-mtd-gW-cQ", "sunday", "2021-01-02",
              "2019-03-03..2019-03-30", "2019-06-02..2019-06-29", "2019-09-01..2019-09-28", "2019-12-01..2019-12-28"),
         args("Y-mtd-gW-cQ", "sunday", "2021-01-05",
              "2020-01-05..2020-01-07", "2020-04-05..2020-04-07", "2020-07-05..2020-07-07", "2020-10-04..2020-10-06"),
         args("Y-mtd-gW-cQ", "monday", "2021-01-02",
              "2019-03-04..2019-03-30", "2019-06-03..2019-06-29", "2019-09-02..2019-09-28", "2019-12-02..2019-12-28"),
         args("Y-mtd-gW-cQ", "monday", "2021-01-05",
              "2020-01-06..2020-01-07", "2020-04-06..2020-04-07", "2020-07-06..2020-07-07", "2020-10-05..2020-10-06"),
         // 2021-06-01 (Tuesday) is in the week of Sunday May 30, the 5th week of May; Feb, Mar
         // and Apr have only 4 weeks, so the range is clamped to their last full week
         args("M-mtd-gW-cM", "sunday", "2021-06-01",
              "2021-02-07..2021-03-02", "2021-03-07..2021-03-30", "2021-04-04..2021-04-27"));
   }

   private static Arguments args(String shape, String weekStart, String endDay,
                                 String... expected)
   {
      return Arguments.of(shape, weekStart, endDay, List.of(expected));
   }

   @ParameterizedTest(name = "{0} {1} end={2}")
   @MethodSource("expectedRanges")
   void rangesEndAtTheSameWeekAndWeekdayAsTheEndDate(String shape, String weekStart,
                                                     String endDay, List<String> expected)
   {
      List<String> actual = WeekStartUtil.withWeekStart(weekStart, () -> ranges(shape, endDay));

      for(String range : actual) {
         String[] parts = range.split(java.util.regex.Pattern.quote(".."));
         assertTrue(parts[0].compareTo(parts[1]) <= 0, "inverted range " + range);
      }

      assertTrue(actual.size() >= expected.size(), "too few ranges: " + actual);
      assertEquals(expected, actual.subList(actual.size() - expected.size(), actual.size()),
                   shape + " " + weekStart + " " + endDay + ", all: " + actual);
   }

   /** The interval (level 1) ranges as start..end, in ascending order. */
   private List<String> ranges(String shapeId, String endDay) {
      DateComparisonInfo dc = DcShape.byId(shapeId)
         .toInfo(endDay, false, false, DateComparisonInfo.CHANGE, 2, true);
      ColumnRef ref = new ColumnRef(new AttributeRef(null, "Order Date"));
      ref.setDataType(XSchema.DATE);
      ConditionList conditions = dc.getDateComparisonConditions(ref);
      assertNotNull(conditions);

      SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
      List<String> ranges = new ArrayList<>();

      for(int i = 0; i < conditions.getSize(); i++) {
         Object item = conditions.getItem(i);

         if(item instanceof ConditionItem && ((ConditionItem) item).getLevel() == 1) {
            List<?> values = ((AssetCondition) ((ConditionItem) item).getXCondition()).getValues();
            ranges.add(format.format((Date) values.get(0)) + ".." +
                       format.format((Date) values.get(1)));
         }
      }

      Collections.sort(ranges);

      return ranges;
   }
}
