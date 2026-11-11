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
package inetsoft.report.composition.execution;

import inetsoft.report.TableLens;
import inetsoft.report.filter.Formula;
import inetsoft.report.filter.SumFormula;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.asset.DateRangeRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The worksheet's grouping and summing by a date level done in memory: the group column holds the
 * value of {@link DateRangeRef#getData(int, java.util.Date)} and {@code AssetQuery.SummaryFilter2}
 * sums the measure per group. The expected sums are added up by hand from the rows listed here.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DateLevelSummaryTest {
   /** the date, the amount: a null date, dates of two years and of one week */
   private static final Object[][] ROWS = {
      { ts(2023, 12, 31, 10), 1.0 },   // Sunday
      { ts(2024, 1, 1, 9), 2.0 },      // Monday
      { ts(2024, 1, 31, 23), 4.0 },
      { ts(2024, 4, 15, 12), 8.0 },
      { ts(2024, 12, 31, 8), 16.0 },   // Tuesday
      { ts(2025, 4, 15, 18), 32.0 },
      { null, 64.0 }
   };

   private static Timestamp ts(int y, int m, int d, int h) {
      return Timestamp.from(LocalDateTime.of(y, m, d, h, 0).atZone(ZoneId.systemDefault())
                               .toInstant());
   }

   /** The table of group value and amount, the group value computed for the level. */
   private static TableLens table(int level) {
      DefaultTableLens lens = new DefaultTableLens(ROWS.length + 1, 2);
      lens.setObject(0, 0, "group");
      lens.setObject(0, 1, "amount");

      for(int i = 0; i < ROWS.length; i++) {
         Object date = ROWS[i][0];
         lens.setObject(i + 1, 0, date == null ? null : DateRangeRef.getData(level, (Timestamp) date));
         lens.setObject(i + 1, 1, ROWS[i][1]);
      }

      lens.setHeaderRowCount(1);
      return lens;
   }

   /** group value to the sum of the amounts */
   private static Map<Object, Double> sums(int level) {
      // the filter groups the rows that are next to each other, AssetQuery sorts the table by the
      // group column first
      TableLens sorted = PostProcessor.sort(table(level), new int[] { 0 }, null, null);
      TableLens summary = new AssetQuery.SummaryFilter2(
         sorted, new int[] { 0 }, new int[] { 1 }, new Formula[] { new SumFormula() },
         false, null, new String[] { "amount" });
      Map<Object, Double> result = new HashMap<>();

      for(int r = 1; summary.moreRows(r); r++) {
         Object group = summary.getObject(r, 0);
         result.put(group, ((Number) summary.getObject(r, 1)).doubleValue());
      }

      return result;
   }

   @Test
   void monthOfYearMergesTheSameMonthOfDifferentYears() {
      // January: 2.0 + 4.0, April: 8.0 + 32.0, December: 1.0 + 16.0, no date: 64.0
      Map<Object, Double> sums = sums(DateRangeRef.MONTH_OF_YEAR_PART);

      assertEquals(Map.of(1, 6.0, 4, 40.0, 12, 17.0), withoutNull(sums));
      assertEquals(64.0, sums.get(null));
   }

   @Test
   void yearKeepsTheYearsApart() {
      // 2023: 1.0, 2024: 2 + 4 + 8 + 16, 2025: 32
      Map<Object, Double> sums = sums(DateRangeRef.YEAR_INTERVAL);

      assertEquals(4, sums.size());
      assertEquals(1.0, sums.get(DateRangeRef.getData(DateRangeRef.YEAR_INTERVAL, ts(2023, 6, 1, 0))));
      assertEquals(30.0, sums.get(DateRangeRef.getData(DateRangeRef.YEAR_INTERVAL, ts(2024, 6, 1, 0))));
      assertEquals(32.0, sums.get(DateRangeRef.getData(DateRangeRef.YEAR_INTERVAL, ts(2025, 6, 1, 0))));
      assertEquals(64.0, sums.get(null));
   }

   @Test
   void quarterOfYearMergesTheQuartersOfDifferentYears() {
      // Q1: 2.0 + 4.0, Q2: 8.0 + 32.0, Q4: 1.0 + 16.0
      assertEquals(Map.of(1, 6.0, 2, 40.0, 4, 17.0), withoutNull(sums(DateRangeRef.QUARTER_OF_YEAR_PART)));
   }

   // Sunday is 1: 2023-12-31 is a Sunday (1.0), 2024-01-01 a Monday (2.0), 2024-12-31 a Tuesday
   // (16.0), 2024-01-31 a Wednesday (4.0), 2024-04-15 a Monday (8.0), 2025-04-15 a Tuesday (32.0)
   @Test
   void dayOfWeekMergesTheSameWeekday() {
      assertEquals(Map.of(1, 1.0, 2, 10.0, 3, 48.0, 4, 4.0),
                   withoutNull(sums(DateRangeRef.DAY_OF_WEEK_PART)));
   }

   // the week of 2023-12-31 (Sunday) to 2024-01-06 holds the two dates 2023-12-31 and 2024-01-01
   @Test
   void weekGroupsTheDaysOfOneWeekTogether() {
      Map<Object, Double> sums = sums(DateRangeRef.WEEK_INTERVAL);
      Object week = DateRangeRef.getData(DateRangeRef.WEEK_INTERVAL, ts(2023, 12, 31, 0));

      assertEquals(3.0, sums.get(week));
      assertEquals(6, sums.size());
   }

   private static Map<Object, Double> withoutNull(Map<Object, Double> sums) {
      Map<Object, Double> copy = new HashMap<>(sums);
      copy.remove(null);
      return copy;
   }
}
