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
package inetsoft.graph.data;

import inetsoft.graph.scale.TimeScale;
import inetsoft.test.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static inetsoft.graph.data.TimeSeriesRowFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link TimeSeriesRow} on a data set through the production path (addCalcRow and prepareCalc):
 * ordering, calc columns, projected rows and running totals.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TimeSeriesRowDataSetTest {
   /** Time series row whose measure v holds a running total. */
   private static TimeSeriesRow runningTotal(Object missing) {
      TimeSeriesRow row = new TimeSeriesRow("d", TimeScale.MONTH, missing) {
         @Override
         protected boolean isRunningTotal(String dim) {
            return "v".equals(dim);
         }
      };
      row.setGroupFields("g");
      return row;
   }

   /** Calc column c = 2 x v that records the rows it was calculated for. */
   private static class DoubleCalc implements CalcColumn {
      final List<Integer> rows = new ArrayList<>();

      @Override
      public Object calculate(DataSet data, int row, boolean first, boolean last) {
         rows.add(row);
         Object v = data.getData("v", row);
         return v instanceof Number ? 2 * ((Number) v).doubleValue() : null;
      }

      @Override
      public String getHeader() {
         return "c";
      }

      @Override
      public Class getType() {
         return Double.class;
      }

      @Override
      public boolean isMeasure() {
         return true;
      }

      @Override
      public String getField() {
         return "v";
      }
   }

   private static void prepare(DefaultDataSet d, String dim) {
      d.prepareCalc(dim, null, true);
   }

   private static DefaultDataSet baseData() {
      return ds(r(at(2023, 1, 10), "A", 10), r(at(2023, 4, 10), "A", 40));
   }

   /** The single row at index i, rendered. */
   private static String row(DataSet d, int i) {
      Object[] cells = new Object[d.getColCount()];

      for(int c = 0; c < cells.length; c++) {
         cells[c] = d.getData(c, i);
      }

      return render(Collections.singletonList(cells), "yyyy-MM-dd").get(0);
   }

   @Test
   void prepareCalcAppendsTheRowsOfCalculate() {
      DefaultDataSet d = baseData();
      TimeSeriesRow t = tsr(TimeScale.MONTH, 0);
      d.addCalcRow(t);
      prepare(d, null);
      assertEquals(4, d.getRowCount());
      assertEquals(2, d.getCalcRowCount());
      assertEquals(render(tsr(TimeScale.MONTH, 0).calculate(baseData())),
                   renderFrom(d, 2, "yyyy-MM-dd"));
   }

   @Test
   void prepareCalcWithADimensionGivesTheSameRows() {
      DefaultDataSet d = baseData();
      d.addCalcRow(tsr(TimeScale.MONTH, 0));
      prepare(d, "d");
      assertEquals(4, d.getRowCount());
      assertEquals(exp("2023-02-10|A|0", "2023-03-10|A|0"), renderFrom(d, 2, "yyyy-MM-dd"));
      assertEquals("2023-01-10|A|10", row(d, 0));
      assertEquals("2023-04-10|A|40", row(d, 1));
   }

   @Test
   void preparingTwiceDoesNotAddRowsAgain() {
      DefaultDataSet d = baseData();
      d.addCalcRow(tsr(TimeScale.MONTH, 0));
      prepare(d, null);
      prepare(d, null);
      assertEquals(4, d.getRowCount());
      assertEquals(2, d.getCalcRowCount());
   }

   @Test
   void inputRowsKeepTheirOrderAndTheFillerIsAppended() {
      DefaultDataSet d = ds(r(at(2023, 4, 10), "A", 40), r(at(2023, 1, 10), "A", 10),
                            r(at(2023, 3, 10), "A", 30));
      d.addCalcRow(tsr(TimeScale.MONTH, 0));
      prepare(d, null);
      assertEquals(4, d.getRowCount());
      assertEquals("2023-04-10|A|40", row(d, 0));
      assertEquals("2023-01-10|A|10", row(d, 1));
      assertEquals("2023-03-10|A|30", row(d, 2));
      assertEquals("2023-02-10|A|0", row(d, 3));
   }

   @Test
   void sortedViewPutsTheFillerInPlace() {
      DefaultDataSet d = baseData();
      d.addCalcRow(tsr(TimeScale.MONTH, 0));
      prepare(d, null);
      SortedDataSet sorted = new SortedDataSet(d, "d");
      sorted.setForceSort(true);
      assertEquals(4, sorted.getRowCount());
      List<String> months = new ArrayList<>();
      List<Object> values = new ArrayList<>();

      for(int i = 0; i < sorted.getRowCount(); i++) {
         months.add(fmt((Date) sorted.getData("d", i), "MM"));
         values.add(sorted.getData("v", i));
      }

      assertEquals(List.of("01", "02", "03", "04"), months);
      assertEquals(List.of(10, 0, 0, 40), values);
   }

   @Test
   void preColumnCalcColumnSeesTheFiller() {
      DefaultDataSet d = baseData();
      DoubleCalc c = new DoubleCalc();
      TimeSeriesRow t = tsr(TimeScale.MONTH, 0);
      t.setPreColumn(true);
      d.addCalcColumn(c);
      d.addCalcRow(t);
      prepare(d, null);
      assertEquals(List.of(0, 1, 2, 3), c.rows);
      assertEquals(0.0, d.getData("c", 2));
      assertEquals(20.0, d.getData("c", 0));
   }

   @Test
   void postColumnCalcColumnDoesNotSeeTheFiller() {
      DefaultDataSet d = baseData();
      DoubleCalc c = new DoubleCalc();
      d.addCalcColumn(c);
      d.addCalcRow(tsr(TimeScale.MONTH, 0));
      prepare(d, null);
      assertEquals(List.of(0, 1), c.rows);
   }

   @Test
   @DisplayName("projected rows are not treated as data when filling")
   void projectedRowsAreNotFilled() {
      // four consecutive months, so nothing is missing in the real data
      DefaultDataSet d = ds(concat(months("A", 1, 2, 3, 4)));
      d.setRowsProjectedForward(2);
      // the fixture does produce projected date values
      assertEquals(6, d.getRowCount());
      assertEquals(4, d.getRowCountUnprojected());
      assertNotNull(d.getData("d", 4));
      assertNotNull(d.getData("d", 5));
      assertEquals(List.of(), tsr(TimeScale.MONTH, 0).calculate(d));

      // control: same data without projection
      assertEquals(List.of(), tsr(TimeScale.MONTH, 0)
         .calculate(ds(concat(months("A", 1, 2, 3, 4)))));
   }

   @Test
   @DisplayName("projected rows are not treated as data when filling two series")
   void projectedRowsAreNotFilledForTwoSeries() {
      // a second series ending with the real data would be extended to the projected dates
      // if projected rows counted as data
      DefaultDataSet two = ds(concat(months("B", 1, 2, 3, 4), months("A", 1, 2, 3, 4)));
      two.setRowsProjectedForward(2);
      assertNotNull(two.getData("d", 8));
      assertEquals(List.of(), tsr(TimeScale.MONTH, 0).calculate(two));

      // control: same data without projection
      assertEquals(List.of(), tsr(TimeScale.MONTH, 0)
         .calculate(ds(concat(months("B", 1, 2, 3, 4), months("A", 1, 2, 3, 4)))));
   }

   @Test
   void runningTotalCarriesThePreviousValueIntoAMidGap() {
      DefaultDataSet d = baseData();
      d.addCalcRow(runningTotal(0));
      prepare(d, null);
      assertEquals(exp("2023-02-10|A|10", "2023-03-10|A|10"), renderFrom(d, 2, "yyyy-MM-dd"));
   }

   @Test
   void runningTotalCarriesTheLastValueIntoTrailingGaps() {
      DefaultDataSet d = ds(concat(months("A", 1, 2), months("B", 1, 2, 3, 4)));
      d.addCalcRow(runningTotal(0));
      prepare(d, null);
      assertEquals(8, d.getRowCount());
      assertEquals(exp("2023-03-10|A|20", "2023-04-10|A|20"), renderFrom(d, 6, "yyyy-MM-dd"));
   }

   @Test
   void runningTotalWithMissingNullLeavesAGap() {
      DefaultDataSet d = baseData();
      d.addCalcRow(runningTotal(null));
      prepare(d, null);
      assertEquals(exp("2023-02-10|A|null", "2023-03-10|A|null"), renderFrom(d, 2, "yyyy-MM-dd"));
   }

   @Test
   void runningTotalCarryStaysInsideTheGroup() {
      DefaultDataSet d = ds(r(at(2023, 1, 10), "A", 10), r(at(2023, 3, 10), "A", 30),
                            r(at(2023, 1, 10), "B", 100), r(at(2023, 3, 10), "B", 300));
      d.addCalcRow(runningTotal(0));
      prepare(d, null);
      assertEquals(exp("2023-02-10|A|10", "2023-02-10|B|100"), renderFrom(d, 4, "yyyy-MM-dd"));
   }

   @Test
   @Disabled("Not filed (not reachable: the group column always precedes the measure column): running total carry returns null when the measure column comes before the date: "
      + "expected filler v = 10, actual null (groups list is empty when v is reached)")
   @DisplayName("running total carry does not depend on the column order")
   void runningTotalCarryWorksWhenTheMeasureColumnComesFirst() {
      DefaultDataSet d = build(new Object[]{ "v", "d", "g" },
                               new Object[]{ 10, at(2023, 1, 10), "A" },
                               new Object[]{ 30, at(2023, 3, 10), "A" });
      TimeSeriesRow t = new TimeSeriesRow("d", TimeScale.MONTH, 0) {
         @Override
         protected boolean isRunningTotal(String dim) {
            return "v".equals(dim);
         }
      };
      t.setGroupFields("g");
      d.addCalcRow(t);
      prepare(d, null);
      assertEquals(exp("10|2023-02-10|A"), renderFrom(d, 2, "yyyy-MM-dd"));
   }

   @Test
   @DisplayName("pinned: a running total before the first value of a group is null")
   void runningTotalBeforeTheFirstValueIsNull() {
      DefaultDataSet d = ds(r(at(2023, 2, 10), "A", 20), r(at(2023, 3, 10), "A", 30),
                            r(at(2023, 1, 10), "B", 1), r(at(2023, 2, 10), "B", 2),
                            r(at(2023, 3, 10), "B", 3));
      d.addCalcRow(runningTotal(0));
      prepare(d, null);
      assertEquals(6, d.getRowCount());
      assertEquals("2023-01-10|A|null", row(d, 5));
   }

   @Test
   void fillerRowHasTheFullColumnCountWithACalcColumn() {
      DefaultDataSet d = baseData();
      d.addCalcColumn(new DoubleCalc());
      d.addCalcRow(tsr(TimeScale.MONTH, 0));
      prepare(d, null);
      assertEquals(4, d.getColCount());
      assertEquals(4, d.getRowCount());

      for(int i = 2; i < 4; i++) {
         for(int c = 0; c < 4; c++) {
            final int col = c;
            final int r = i;
            assertDoesNotThrow(() -> d.getData(col, r));
         }
      }

      assertEquals("2023-02-10", fmt((Date) d.getData("d", 2), "yyyy-MM-dd"));
      assertEquals("2023-03-10", fmt((Date) d.getData("d", 3), "yyyy-MM-dd"));
      assertEquals("A", d.getData("g", 2));
      assertEquals(0, d.getData("v", 2));
      assertDoesNotThrow(() -> d.getData("c", 2));
   }
}
