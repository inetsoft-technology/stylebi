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
package inetsoft.graph.data;

import inetsoft.graph.data.CalcTestFixtures.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Timestamp;
import java.util.*;

import static inetsoft.graph.data.CalcTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests of project forward ({@link AbstractDataSet#setRowsProjectedForward(int)}) for
 * numbers, dates and other dimension values, directly and through filters.
 */
@Tag("core")
class AbstractDataSetProjectionTest {
   /**
    * Dimension n (Integer, not a measure) and measure m. The first row is the header.
    */
   private static DefaultDataSet numbers(int... n) {
      Object[][] rows = new Object[n.length + 1][];
      rows[0] = new Object[] { "n", "m" };

      for(int i = 0; i < n.length; i++) {
         rows[i + 1] = new Object[] { n[i], n[i] * 1.5 };
      }

      return nDimension(rows);
   }

   private static DefaultDataSet nDimension(Object[][] rows) {
      return new DefaultDataSet(rows) {
         @Override
         protected boolean isMeasure0(String col) {
            return !"n".equals(col) && super.isMeasure0(col);
         }
      };
   }

   private static Date date(int year, int month, int day) {
      Calendar cal = Calendar.getInstance();
      cal.clear();
      cal.set(year, month - 1, day, 0, 0, 0);
      cal.set(Calendar.MILLISECOND, 0);
      return cal.getTime();
   }

   private static DefaultDataSet dates(Date... dates) {
      Object[][] rows = new Object[dates.length + 1][];
      rows[0] = new Object[] { "d" };

      for(int i = 0; i < dates.length; i++) {
         rows[i + 1] = new Object[] { dates[i] };
      }

      return new DefaultDataSet(rows);
   }

   @Test
   @DisplayName("numeric dimension is projected by its step, the measure projects null")
   void numericDimensionIsProjected() {
      DefaultDataSet ds = numbers(1, 2, 3);
      assertFalse(ds.isMeasure("n"));
      ds.setRowsProjectedForward(2);

      assertEquals(5, ds.getRowCount());
      assertEquals(4, ds.getData("n", 3));
      assertEquals(5, ds.getData("n", 4));
      assertNull(ds.getData("m", 3));
      assertNull(ds.getData("m", 4));
   }

   @Test
   @DisplayName("projected counts: unprojected rows stay, projected rows are reported, default is none")
   void projectedCounts() {
      DefaultDataSet ds = numbers(1, 2, 3);

      assertEquals(3, ds.getRowCount());
      assertEquals(0, ds.getRowsProjectedForward());

      ds.setRowsProjectedForward(2);

      assertEquals(3, ds.getRowCountUnprojected());
      assertEquals(2, ds.getRowsProjectedForward());
      assertEquals(5, ds.getRowCount());
   }

   @Test
   @DisplayName("irregular numbers are projected with the smallest gap")
   void irregularNumbersUseSmallestGap() {
      DefaultDataSet ds = numbers(1, 2, 4);
      ds.setRowsProjectedForward(2);

      assertEquals(5, ds.getData("n", 3));
      assertEquals(6, ds.getData("n", 4));
   }

   @Test
   @DisplayName("a repeating series (facet) continues from the last value of its first series")
   void repeatingSeriesContinuesFirstSeries() {
      DefaultDataSet ds = numbers(1, 2, 3, 1, 2, 3);
      ds.setRowsProjectedForward(2);

      assertEquals(4, ds.getData("n", 6));
      assertEquals(5, ds.getData("n", 7));
   }

   @Test
   @DisplayName("monthly date series is projected by months, as java.util.Date at local midnight")
   void monthlyDateSeries() {
      DefaultDataSet ds = dates(date(2021, 1, 1), date(2021, 2, 1), date(2021, 3, 1));
      ds.setRowsProjectedForward(2);

      assertEquals(date(2021, 4, 1), ds.getData("d", 3));
      assertEquals(date(2021, 5, 1), ds.getData("d", 4));
      assertEquals(Date.class, ds.getData("d", 3).getClass());
   }

   @ParameterizedTest
   @ValueSource(strings = { "Europe/Berlin", "America/New_York", "Asia/Kolkata", "UTC" })
   @DisplayName("monthly date series gives midnight on the 1st in every time zone")
   void monthlyDateSeriesInEveryZone(String zone) {
      TimeZone saved = TimeZone.getDefault();

      try {
         TimeZone.setDefault(TimeZone.getTimeZone(zone));
         DefaultDataSet ds = dates(date(2021, 1, 1), date(2021, 2, 1), date(2021, 3, 1));
         ds.setRowsProjectedForward(2);

         assertEquals(date(2021, 4, 1), ds.getData("d", 3));
         assertEquals(date(2021, 5, 1), ds.getData("d", 4));
      }
      finally {
         TimeZone.setDefault(saved);
      }
   }

   @Test
   @DisplayName("yearly and daily (DST week) date series are projected by their step")
   void yearlyAndDailyDateSeries() {
      DefaultDataSet yearly = dates(date(2019, 1, 1), date(2020, 1, 1), date(2021, 1, 1));
      yearly.setRowsProjectedForward(2);

      assertEquals(date(2022, 1, 1), yearly.getData("d", 3));
      assertEquals(date(2023, 1, 1), yearly.getData("d", 4));

      DefaultDataSet daily = dates(date(2021, 3, 12), date(2021, 3, 13), date(2021, 3, 14));
      daily.setRowsProjectedForward(2);

      assertEquals(date(2021, 3, 15), daily.getData("d", 3));
      assertEquals(date(2021, 3, 16), daily.getData("d", 4));
   }

   private static DefaultDataSet timestamps() {
      DefaultDataSet ds = new DefaultDataSet(new Object[][] {
         { "ts" },
         { Timestamp.valueOf("2021-01-01 00:00:00") },
         { Timestamp.valueOf("2021-02-01 00:00:00") },
         { Timestamp.valueOf("2021-03-01 00:00:00") }
      });
      ds.setRowsProjectedForward(1);
      return ds;
   }

   @Test
   @DisplayName("a Timestamp column projects the right instant")
   void timestampColumnProjectsRightInstant() {
      Object projected = timestamps().getData("ts", 3);

      assertInstanceOf(Date.class, projected);
      assertEquals(Timestamp.valueOf("2021-04-01 00:00:00").getTime(), ((Date) projected).getTime());
   }

   @Test
   @DisplayName("pinned: the projected value of a Timestamp column is a plain java.util.Date")
   void timestampProjectionIsPlainDate() {
      assertEquals(Date.class, timestamps().getData("ts", 3).getClass());
   }

   @Test
   @DisplayName("a string dimension projects its last value, a measure projects null")
   void stringDimensionRepeatsLastValue() {
      DefaultDataSet ds = new DefaultDataSet(new Object[][] {
         { "s", "m" },
         { "A", 1.0 },
         { "B", 2.0 },
         { "C", 3.0 }
      });
      ds.setRowsProjectedForward(2);

      assertEquals("C", ds.getData("s", 3));
      assertEquals("C", ds.getData("s", 4));
      assertNull(ds.getData("m", 3));
      assertNull(ds.getData("m", 4));
   }

   @Test
   @DisplayName("setProjectColumn limits projection to one column, null projects all")
   void projectColumnLimitsProjection() {
      Date mar = date(2021, 3, 1);
      DefaultDataSet ds = nDimension(new Object[][] {
         { "n", "m", "d" },
         { 1, 1.0, date(2021, 1, 1) },
         { 2, 2.0, date(2021, 2, 1) },
         { 3, 3.0, mar }
      });
      ds.setRowsProjectedForward(2);
      ds.setProjectColumn("n");

      assertEquals(4, ds.getData("n", 3));
      assertEquals(5, ds.getData("n", 4));
      assertEquals(mar, ds.getData("d", 3));
      assertEquals(mar, ds.getData("d", 4));

      ds.setProjectColumn(null);

      assertEquals(4, ds.getData("n", 3));
      assertEquals(date(2021, 4, 1), ds.getData("d", 3));
      assertEquals(date(2021, 5, 1), ds.getData("d", 4));
   }

   @Test
   @DisplayName("pinned: a date column may start with null, a numeric dimension that starts with null projects null")
   void leadingNullBehavior() {
      DefaultDataSet withDates = dates(null, date(2021, 1, 1), date(2021, 2, 1),
                                       date(2021, 3, 1));
      withDates.setRowsProjectedForward(1);

      assertEquals(date(2021, 4, 1), withDates.getData("d", 4));

      DefaultDataSet withNumbers = nDimension(new Object[][] {
         { "n", "m" },
         { null, 0.5 },
         { 1, 1.0 },
         { 2, 2.0 },
         { 3, 3.0 }
      });
      withNumbers.setRowsProjectedForward(1);

      assertNull(withNumbers.getData("n", 4));
   }

   @Test
   @DisplayName("pinned: a single row cannot be projected, projected cells are null")
   void singleRowProjectsNull() {
      DefaultDataSet ds = numbers(5);
      ds.setRowsProjectedForward(2);

      assertNull(ds.getData("n", 1));
      assertNull(ds.getData("n", 2));
      assertNull(ds.getData("m", 1));
      assertNull(ds.getData("m", 2));
   }

   @Test
   @DisplayName("an empty data set stays empty when projecting")
   void emptyDataSetStaysEmpty() {
      DefaultDataSet ds = new DefaultDataSet(new Object[][] { { "n", "m" } });
      ds.setRowsProjectedForward(3);

      assertEquals(0, ds.getRowCount());
   }

   @Test
   @DisplayName("calc rows count as unprojected rows before the projected ones")
   void calcRowsAreUnprojectedRows() {
      DefaultDataSet ds = numbers(1, 2, 3);
      ds.addCalcRow(new AddRows(false, new Object[] { 9, 0.5 }));
      ds.prepareCalc(null, null, true);
      ds.setRowsProjectedForward(2);

      assertEquals(4, ds.getRowCountUnprojected());
      assertEquals(6, ds.getRowCount());
   }

   @Test
   @DisplayName("a filter over a projected base counts the rows once and projects the dimension")
   void filterOverProjectedBase() {
      DefaultDataSet base = numbers(1, 2, 3);
      SubDataSet filter = new SubDataSet(base, new HashMap<>());
      filter.setRowsProjectedForward(2);

      assertEquals(2, filter.getRowsProjectedForward());
      assertEquals(5, filter.getRowCount());
      assertEquals(4, filter.getData("n", 3));
      assertEquals(5, filter.getData("n", 4));
   }

   @Test
   @DisplayName("an outer dimension of a facet repeats its last value instead of projecting")
   void outerDimensionIsNotProjected() {
      DefaultDataSet base = numbers(1, 2, 3);
      SubDataSet filter = new SubDataSet(base, new HashMap<>());
      filter.setInnermost(false);
      filter.setRowsProjectedForward(2);

      assertEquals(3, filter.getData("n", 3));
      assertEquals(3, filter.getData("n", 4));
   }

   @Test
   @DisplayName("a filter passes the project column to its base")
   void filterDelegatesProjectColumn() {
      DefaultDataSet base = numbers(1, 2, 3);
      SubDataSet filter = new SubDataSet(base, new HashMap<>());

      filter.setProjectColumn("n");

      assertEquals("n", filter.getProjectColumn());
      assertEquals("n", base.getProjectColumn());
   }
}
