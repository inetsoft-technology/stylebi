/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.report.composition.graph.calc;

import inetsoft.graph.data.CalcColumn;
import inetsoft.report.composition.graph.BrushDataSet;
import inetsoft.report.composition.graph.VSDataSet;
import inetsoft.report.filter.*;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.graph.AbstractCalc;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class RunningTotalColumnTest {
   private RunningTotalColumn runningTotalColumn;
   VSDataSet vsDataSet;

   /**
    * use date type dataset to calculate on reset column
    */
   @Test
   void tesCalculateWithVSDataSet() {
      DefaultTableLens tb2 = new DefaultTableLens ( new Object[][]{
         { "date", "id"},
         { toDate("2021-01-01"), 1},
         { toDate("2021-02-01"), 3},
         { toDate("2021-03-01"), null},
         { toDate("2022-04-01"), 6},
         { toDate("2022-05-01"), 4},
         { toDate("2022-07-01"), 0}
      });

      runningTotalColumn = new RunningTotalColumn("id", "sum(id)");
      runningTotalColumn.setInnerDim("date");
      runningTotalColumn.setResetLevel(RunningTotalColumn.YEAR);

      vsDataSet = createVSDataSet(tb2, new String[] { "date" });

      Object result = runningTotalColumn.calculate(vsDataSet, 1, false, false);
      assertEquals(3, result);  // if formula is null, return value 3

      SumFormula sumFormula = new SumFormula();
      runningTotalColumn.setFormula(sumFormula);

      result = runningTotalColumn.calculate(vsDataSet, 1, false, false);
      assertEquals(4.0, result); // 1+ 3 = 4

      result = runningTotalColumn.calculate(vsDataSet, 2, false, false);
      assertEquals(4.0, result); // 1 +3 + null = 4, null treat as 0

      result = runningTotalColumn.calculate(vsDataSet, 3, false, false);
      assertEquals(6.0, result); // reset by year, 2022 only have value 6, so 6 = 6

      result = runningTotalColumn.calculate(vsDataSet, 4, false, false);
      assertEquals(10.0, result); //reset by year, 2022  have value 6, 4, so 6 + 4 = 10

      assertEquals(sumFormula, runningTotalColumn.getFormula());
      assertEquals(RunningTotalColumn.YEAR, runningTotalColumn.getResetLevel());
   }

   /**
    * use brushDataSet to check calculate on break by column.
    */
   @Test
   void tesCalculateWithBrushData() {
      DefaultTableLens tableLens1 = new DefaultTableLens(new Object[][]{
         {"group", "name", "id"},
         {"A", "a", 1},
         {"A", "b", 3},
         {"A", "c", null},
         {"B", "d", 7},
         {"B", "e", 1},
         {"B", "f", 9}
      });

      runningTotalColumn = new RunningTotalColumn("id", "sum(id)");
      runningTotalColumn.setFormula(new SumFormula());
      runningTotalColumn.setInnerDim("name");

      vsDataSet = createVSDataSet(tableLens1, new String[] {"group", "name"});
      BrushDataSet brushDataSet = new BrushDataSet(vsDataSet, vsDataSet);

      //  unset break by column, same result with set  runningTotalColumn.setBreakBy("group");
      Object result = runningTotalColumn.calculate(brushDataSet, 2, false, false);
      assertEquals(4.0, result); //A: 1+3 = 4

      result = runningTotalColumn.calculate(brushDataSet, 3, false, false);
      assertEquals(7.0, result); //B: 7 = 7

      result = runningTotalColumn.calculate(brushDataSet, 4, false, false);
      assertEquals(8.0, result); //B: 7+1 =8

      runningTotalColumn.setBreakBy("name");
      result = runningTotalColumn.calculate(brushDataSet, 2, false, false);
      assertNull(result); // row-2 value is null

      result = runningTotalColumn.calculate(brushDataSet, 3, false, false);
      assertEquals(7.0, result); //row-2 value is 7

      assertEquals("name", runningTotalColumn.getBreakBy());
   }

   @Test
   void testCalculateWithInvalid() {
      DefaultTableLens tdata2 = new DefaultTableLens ( new Object[][]{
         { "date", "__all__"},
         { toDate("2021-01-01"), 1},
         { toDate("2021-03-01"), null},
         { toDate("2021-04-01"), 6},
         });
      VSDataSet dataSet2 = createVSDataSet(tdata2, new String[] {"date", "__all__"});
      BrushDataSet brushDataSet2= new BrushDataSet(dataSet2, dataSet2);

      runningTotalColumn = new RunningTotalColumn("__all__", "sum(__all__)");
      runningTotalColumn.setInnerDim("date");

      // if row <0, return null
      runningTotalColumn.setFormula(new SumFormula());
      Object result = runningTotalColumn.calculate(brushDataSet2, 2, false, false);
      assertNull(result);
   }

   /**
    * use crosstab filter to check calculate on break by filed.
    */
   @Test
   void testCalculateWithCrosstabFilter() {
      runningTotalColumn = new RunningTotalColumn("id", "sum(id)");
      runningTotalColumn.setFormula(new SumFormula());
      runningTotalColumn.setInnerDim("name");
      runningTotalColumn.setBreakBy(AbstractCalc.ROW_INNER);

      CrossFilter.Tuple rowTuple = createCrosstabFilterTuple("b");
      List<Object> values = Arrays.asList("a", "b", "c");
      CrossTabFilter.PairN pairB = createCrosstabFilterPairN("b", null);

      CrossTabFilter.CrosstabDataContext mockContext =
         mock(CrossTabFilter.CrosstabDataContext.class);
      when(mockContext.getRowHeaders()).thenReturn(Arrays.asList("name"));
      when(mockContext.getValues(rowTuple, "", 0, true)).thenReturn(values);

      when(mockContext.getValue(createCrosstabFilterTuple("a"),
                                createCrosstabFilterTuple(null), 0)).thenReturn(1);
      when(mockContext.getValue(pairB)).thenReturn(3);
      when(mockContext.getValue(createCrosstabFilterTuple("b"),
                                createCrosstabFilterTuple(null), 0)).thenReturn(3);
      when(mockContext.getValue(createCrosstabFilterTuple("c"),
                                createCrosstabFilterTuple(null), 0)).thenReturn(4);

      Object result = runningTotalColumn.calculate(mockContext, pairB);
      assertEquals(4.0, result);  // 1 + 3 = 4

      //check break by self.
      runningTotalColumn.setBreakBy("name");
      when(mockContext.getRowTupleByIndex(1)).thenReturn(rowTuple);
      when(mockContext.getRowTupleByIndex(0)).thenReturn(createCrosstabFilterTuple("a"));
      when(mockContext.getRowTupleIndex(rowTuple)).thenReturn(1);
      when(mockContext.getRowTupleIndex(createCrosstabFilterTuple("a"))).thenReturn(0);
      result = runningTotalColumn.calculate(mockContext, pairB);

      assertEquals(3.0, result);  // tuple-b value is 3
   }

   @Test
   void testCalculateWithCrosstabFilterOfInvalid() {
      CrossTabFilter.PairN pairB = createCrosstabFilterPairN("b", null);
      CrossTabFilter.CrosstabDataContext mockContext =
         mock(CrossTabFilter.CrosstabDataContext.class);
      when(mockContext.getRowHeaders()).thenReturn(Arrays.asList("name"));

      runningTotalColumn = new RunningTotalColumn("id", "sum(id)");

      Object result = runningTotalColumn.calculate(mockContext, pairB);
      assertNull(result); // no formula, return null

      runningTotalColumn.setFormula(new SumFormula());
      result = runningTotalColumn.calculate(mockContext, pairB);
      assertNull(result); // no dim, return null

      runningTotalColumn.setInnerDim("name");
      runningTotalColumn.setBreakBy(AbstractCalc.ROW_INNER);
      when(mockContext.isGrandTotalTuple(createCrosstabFilterTuple("b"))).thenReturn(true);

      result = runningTotalColumn.calculate(mockContext, pairB);
      assertEquals(CalcColumn.INVALID, result); // grand total is true, total row is false, return invalid

      when(mockContext.getRowHeaders()).thenReturn(Arrays.asList("name", "group_total"));
      when(mockContext.getValue(pairB)).thenReturn(3);
      result = runningTotalColumn.calculate(mockContext, pairB);
      assertEquals(3, result); // tuple.size() - 1 < dimIdx, return current value
   }

   private VSDataSet createVSDataSet(DefaultTableLens tableLens, String[] dNames) {
      VSDimensionRef[] mockDRefs = Arrays.stream(dNames)
         .map(name -> {
            VSDimensionRef mockDRef = mock(VSDimensionRef.class);
            when(mockDRef.getFullName()).thenReturn(name);
            return mockDRef;
         })
         .toArray(VSDimensionRef[]::new);

      vsDataSet = new VSDataSet(tableLens,  mockDRefs );

      return vsDataSet;
   }

   private java.util.Date toDate(String localDate) {
      ZoneId zoneId = ZoneId.systemDefault();
      return java.util.Date.from(LocalDate.parse(localDate)
                                    .atStartOfDay(zoneId)
                                    .toInstant());
   }

   private CrossTabFilter.PairN createCrosstabFilterPairN(Object rowValue, Object colValue) {
      CrossFilter.Tuple rowTuple = new CrossFilter.Tuple(new Object[] { rowValue });
      CrossFilter.Tuple colTuple = new CrossFilter.Tuple(new Object[] { colValue });
      CrossTabFilter.PairN pairN = new CrossTabFilter.PairN(rowTuple, colTuple, 0);

      return pairN;
   }

   /**
    * Running average over dates: uses date dim + NONE reset so no boundary breaks.
    * Each row returns the average of all values seen up to and including that row.
    */
   @Test
   void testRunningAverageWithDateDim() {
      DefaultTableLens tb = new DefaultTableLens(new Object[][]{
         {"date", "id"},
         {toDate("2021-01-01"), 10},
         {toDate("2021-02-01"), 20},
         {toDate("2021-03-01"), 30}
      });

      runningTotalColumn = new RunningTotalColumn("id", "avg(id)");
      runningTotalColumn.setInnerDim("date");
      runningTotalColumn.setResetLevel(RunningTotalColumn.NONE);
      runningTotalColumn.setFormula(new AverageFormula());

      vsDataSet = createVSDataSet(tb, new String[]{"date"});

      // row 0: avg(10) = 10
      Object result = runningTotalColumn.calculate(vsDataSet, 0, false, false);
      assertEquals(10.0, result);

      // row 1: avg(10, 20) = 15
      result = runningTotalColumn.calculate(vsDataSet, 1, false, false);
      assertEquals(15.0, result);

      // row 2: avg(10, 20, 30) = 20
      result = runningTotalColumn.calculate(vsDataSet, 2, false, false);
      assertEquals(20.0, result);
   }

   /**
    * Running min: always the minimum of all values seen so far.
    */
   @Test
   void testRunningMinWithDateDim() {
      DefaultTableLens tb = new DefaultTableLens(new Object[][]{
         {"date", "id"},
         {toDate("2021-01-01"), 30},
         {toDate("2021-02-01"), 10},
         {toDate("2021-03-01"), 20}
      });

      runningTotalColumn = new RunningTotalColumn("id", "min(id)");
      runningTotalColumn.setInnerDim("date");
      runningTotalColumn.setResetLevel(RunningTotalColumn.NONE);
      runningTotalColumn.setFormula(new MinFormula());

      vsDataSet = createVSDataSet(tb, new String[]{"date"});

      // row 0: min(30)
      Object result = runningTotalColumn.calculate(vsDataSet, 0, false, false);
      assertEquals(30, result);

      // row 1: min(30, 10) = 10
      result = runningTotalColumn.calculate(vsDataSet, 1, false, false);
      assertEquals(10, result);

      // row 2: min(30, 10, 20) = 10
      result = runningTotalColumn.calculate(vsDataSet, 2, false, false);
      assertEquals(10, result);
   }

   /**
    * Running max: always the maximum of all values seen so far.
    */
   @Test
   void testRunningMaxWithDateDim() {
      DefaultTableLens tb = new DefaultTableLens(new Object[][]{
         {"date", "id"},
         {toDate("2021-01-01"), 5},
         {toDate("2021-02-01"), 15},
         {toDate("2021-03-01"), 10}
      });

      runningTotalColumn = new RunningTotalColumn("id", "max(id)");
      runningTotalColumn.setInnerDim("date");
      runningTotalColumn.setResetLevel(RunningTotalColumn.NONE);
      runningTotalColumn.setFormula(new MaxFormula());

      vsDataSet = createVSDataSet(tb, new String[]{"date"});

      // row 0: max(5) = 5
      Object result = runningTotalColumn.calculate(vsDataSet, 0, false, false);
      assertEquals(5, result);

      // row 1: max(5, 15) = 15
      result = runningTotalColumn.calculate(vsDataSet, 1, false, false);
      assertEquals(15, result);

      // row 2: max(5, 15, 10) = 15
      result = runningTotalColumn.calculate(vsDataSet, 2, false, false);
      assertEquals(15, result);
   }

   /**
    * Running sum with no reset level (NONE=-1): accumulates across all rows
    * ignoring date boundaries.
    */
   @Test
   void testRunningSumNoReset() {
      DefaultTableLens tb = new DefaultTableLens(new Object[][]{
         {"date", "id"},
         {toDate("2021-01-01"), 5},
         {toDate("2021-06-01"), 3},
         {toDate("2022-01-01"), 10}
      });

      runningTotalColumn = new RunningTotalColumn("id", "sum(id)");
      runningTotalColumn.setInnerDim("date");
      runningTotalColumn.setResetLevel(RunningTotalColumn.NONE);
      runningTotalColumn.setFormula(new SumFormula());

      vsDataSet = createVSDataSet(tb, new String[]{"date"});

      // row 0: 5
      Object result = runningTotalColumn.calculate(vsDataSet, 0, false, false);
      assertEquals(5.0, result);

      // row 1: 5+3 = 8 (NONE means no reset)
      result = runningTotalColumn.calculate(vsDataSet, 1, false, false);
      assertEquals(8.0, result);

      // row 2: 5+3+10 = 18 (crosses year boundary but no reset)
      result = runningTotalColumn.calculate(vsDataSet, 2, false, false);
      assertEquals(18.0, result);
   }

   /**
    * Running sum with null values in the dataset. Null is treated as 0.
    */
   @Test
   void testRunningSumWithNullValues() {
      DefaultTableLens tb = new DefaultTableLens(new Object[][]{
         {"date", "id"},
         {toDate("2021-01-01"), 5},
         {toDate("2021-06-01"), null},
         {toDate("2021-12-01"), 3}
      });

      runningTotalColumn = new RunningTotalColumn("id", "sum(id)");
      runningTotalColumn.setInnerDim("date");
      runningTotalColumn.setResetLevel(RunningTotalColumn.NONE);
      runningTotalColumn.setFormula(new SumFormula());

      vsDataSet = createVSDataSet(tb, new String[]{"date"});

      // row 1: 5 + null = 5
      Object result = runningTotalColumn.calculate(vsDataSet, 1, false, false);
      assertEquals(5.0, result);

      // row 2: 5 + null + 3 = 8
      result = runningTotalColumn.calculate(vsDataSet, 2, false, false);
      assertEquals(8.0, result);
   }

   /**
    * Running sum resets at month boundary.
    */
   @Test
   void testRunningSumResetsAtMonth() {
      DefaultTableLens tb = new DefaultTableLens(new Object[][]{
         {"date", "id"},
         {toDate("2021-01-01"), 5},
         {toDate("2021-01-15"), 3},
         {toDate("2021-02-01"), 10}
      });

      runningTotalColumn = new RunningTotalColumn("id", "sum(id)");
      runningTotalColumn.setInnerDim("date");
      runningTotalColumn.setResetLevel(RunningTotalColumn.MONTH);
      runningTotalColumn.setFormula(new SumFormula());

      vsDataSet = createVSDataSet(tb, new String[]{"date"});

      // row 0: 5
      Object result = runningTotalColumn.calculate(vsDataSet, 0, false, false);
      assertEquals(5.0, result);

      // row 1: still Jan → 5+3 = 8
      result = runningTotalColumn.calculate(vsDataSet, 1, false, false);
      assertEquals(8.0, result);

      // row 2: Feb, reset → 10
      result = runningTotalColumn.calculate(vsDataSet, 2, false, false);
      assertEquals(10.0, result);
   }

   /**
    * Break-by running sum where several rows share the same break-by value, and the
    * break-by column is also the inner dimension (the shape produced by a point chart
    * whose second dimension is a colour aesthetic, so it is not part of innerDim).
    *
    * Each row must get its own running value in root row order -- not the group total.
    */
   @Test
   void testRunningSumBreakByRunsWithinGroup() {
      DefaultTableLens tb = new DefaultTableLens(new Object[][]{
         {"order_date", "hour", "id"},
         {toDate("2002-02-06"), null, 2},
         {toDate("2002-02-06"), toDate("2002-02-06"), 2},
         {toDate("2002-02-08"), toDate("2002-02-08"), 2}
      });

      runningTotalColumn = new RunningTotalColumn("id", "sum(id)");
      runningTotalColumn.setInnerDim("order_date");
      runningTotalColumn.setBreakBy("order_date");
      runningTotalColumn.setResetLevel(RunningTotalColumn.NONE);
      runningTotalColumn.setFormula(new SumFormula());

      vsDataSet = createVSDataSet(tb, new String[]{"order_date", "hour"});

      // first row of the 2002-02-06 group: its own value only, not the group total
      assertEquals(2.0, runningTotalColumn.calculate(vsDataSet, 0, false, false));

      // second row of the same group: 2+2
      assertEquals(4.0, runningTotalColumn.calculate(vsDataSet, 1, false, false));

      // break-by value changed, so the total resets
      assertEquals(2.0, runningTotalColumn.calculate(vsDataSet, 2, false, false));
   }

   /**
    * Break-by running sum over a non-date break-by value -- the "Others" bucket label a
    * top-N ranking produces on a date dimension -- including a null measure. The running
    * sum must be monotonically increasing across the bucket and reset when the label
    * changes.
    */
   @Test
   void testRunningSumBreakByOthersBucket() {
      DefaultTableLens tb = new DefaultTableLens(new Object[][]{
         {"order_date", "hour", "id"},
         {"Others", toDate("2002-01-05"), 4},
         {"Others", toDate("2002-01-06"), null},
         {"Others", toDate("2002-01-11"), 3},
         {"Others", toDate("2002-01-12"), 4},
         {toDate("2002-01-15"), toDate("2002-01-15"), 2}
      });

      runningTotalColumn = new RunningTotalColumn("id", "sum(id)");
      runningTotalColumn.setInnerDim("order_date");
      runningTotalColumn.setBreakBy("order_date");
      runningTotalColumn.setResetLevel(RunningTotalColumn.NONE);
      runningTotalColumn.setFormula(new SumFormula());

      vsDataSet = createVSDataSet(tb, new String[]{"order_date", "hour"});

      List<Object> results = Arrays.asList(
         runningTotalColumn.calculate(vsDataSet, 0, false, false),
         runningTotalColumn.calculate(vsDataSet, 1, false, false),
         runningTotalColumn.calculate(vsDataSet, 2, false, false),
         runningTotalColumn.calculate(vsDataSet, 3, false, false));

      // null counts as 0, so the sequence still never decreases
      assertEquals(Arrays.asList(4.0, 4.0, 7.0, 11.0), results);

      // the label changed, so the total resets
      assertEquals(2.0, runningTotalColumn.calculate(vsDataSet, 4, false, false));
   }

   /**
    * A break-by group spanning many rows must never produce a decreasing running sum
    * when all the accumulated values are non-negative.
    */
   @Test
   void testRunningSumBreakByIsMonotonic() {
      Object[][] rows = new Object[8][];
      rows[0] = new Object[]{"order_date", "hour", "id"};

      for(int i = 1; i < rows.length; i++) {
         rows[i] = new Object[]{ "Others", toDate("2002-03-0" + i), i };
      }

      DefaultTableLens tb = new DefaultTableLens(rows);

      runningTotalColumn = new RunningTotalColumn("id", "sum(id)");
      runningTotalColumn.setInnerDim("order_date");
      runningTotalColumn.setBreakBy("order_date");
      runningTotalColumn.setResetLevel(RunningTotalColumn.NONE);
      runningTotalColumn.setFormula(new SumFormula());

      vsDataSet = createVSDataSet(tb, new String[]{"order_date", "hour"});

      double previous = 0;

      for(int row = 0; row < rows.length - 1; row++) {
         double current = (Double) runningTotalColumn.calculate(vsDataSet, row, false, false);

         assertTrue(current >= previous,
                    "running sum decreased at row " + row + ": " + previous + " -> " + current);
         previous = current;
      }

      // 1+2+...+7
      assertEquals(28.0, previous);
   }

   /**
    * Break-by running max where the accumulation dimension (innerDim) is a date column
    * distinct from the break-by column, and the rows are NOT in chronological order
    * because the date dimension is sorted by value.
    *
    * The running value must follow the dates, not the row order. (74910)
    */
   @Test
   void testRunningMaxBreakByFollowsDateNotRowOrder() {
      // rows deliberately out of chronological order, as sort-by-value leaves them
      DefaultTableLens tb = new DefaultTableLens(new Object[][]{
         {"reseller", "quarter", "sd"},
         {"false", toDate("2003-01-01"), 13.4748},
         {"false", toDate("2002-01-01"), 11.3225},
         {"false", toDate("2002-10-01"), 8.2967},
         {"true", toDate("2005-07-01"), 11.6802},
         {"true", toDate("2002-01-01"), 9.8476},
         {"true", toDate("2002-04-01"), 9.3286}
      });

      runningTotalColumn = new RunningTotalColumn("sd", "max(sd)");
      runningTotalColumn.setInnerDim("quarter");
      runningTotalColumn.setBreakBy("reseller");
      runningTotalColumn.setResetLevel(RunningTotalColumn.NONE);
      runningTotalColumn.setFormula(new MaxFormula());

      vsDataSet = createVSDataSet(tb, new String[]{"reseller", "quarter"});

      // 2003-01-01 is chronologically last in its group, so it sees all three
      assertEquals(13.4748, (Double) runningTotalColumn.calculate(vsDataSet, 0, false, false),
                   1e-9);

      // 2002-01-01 is chronologically first: its own value only, even though a larger
      // value sits above it in row order
      assertEquals(11.3225, (Double) runningTotalColumn.calculate(vsDataSet, 1, false, false),
                   1e-9);

      // 2002-10-01 follows 2002-01-01, so the max is still 11.3225
      assertEquals(11.3225, (Double) runningTotalColumn.calculate(vsDataSet, 2, false, false),
                   1e-9);

      // second break-by group accumulates independently
      assertEquals(11.6802, (Double) runningTotalColumn.calculate(vsDataSet, 3, false, false),
                   1e-9);
      assertEquals(9.8476, (Double) runningTotalColumn.calculate(vsDataSet, 4, false, false),
                   1e-9);
      assertEquals(9.8476, (Double) runningTotalColumn.calculate(vsDataSet, 5, false, false),
                   1e-9);
   }

   private CrossFilter.Tuple createCrosstabFilterTuple(Object value) {
      return new CrossFilter.Tuple(new Object[] { value });
   }
}
