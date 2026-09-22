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
import inetsoft.uql.XConstants;
import inetsoft.uql.viewsheet.VSDataRef;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.graph.AbstractCalc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class MovingColumnTest {
   private MovingColumn movingColumn;
   private VSDataSet vsDataSet;
   private DefaultTableLens tableLens;

   @BeforeEach
   void setUp() {
      tableLens = new DefaultTableLens(new Object[][]{
         {"group", "name", "id"},
         {"A", "a", 1},
         {"A", "b", 3},
         {"A", "c", null},
         {"B", "d", 7},
         {"B", "e", 1},
         {"B", "f", 9}
      });
   }

   @Test
   void testCalculateWithVSDataSet() {
      vsDataSet = createVSDataSet(tableLens, "name");
      movingColumn = new MovingColumn("id", "sum(id)");

      SumFormula sumFormula = new SumFormula();
      movingColumn.setFormula(sumFormula);
      movingColumn.setPreCnt(1);
      movingColumn.setNextCnt(1);

      assertEquals(sumFormula, movingColumn.getFormula());
      assertEquals(1, movingColumn.getPreCnt());
      assertEquals(1, movingColumn.getNextCnt());

      assertFalse(movingColumn.isIncludeCurrent());
      assertFalse(movingColumn.isShowNull());

      // check didn't include current value
      Object result = movingColumn.calculate(vsDataSet, 0, false, false);
      assertEquals(3.0, result);  //null + 3

      result = movingColumn.calculate(vsDataSet, 1, false, false);
      assertEquals(1.0, result);  //1 + null

      //check include current value
      movingColumn.setIncludeCurrent(true);
      movingColumn.setShowNull(true);
      result = movingColumn.calculate(vsDataSet, 1, false, false);
      assertEquals(4.0, result); // 1 + null + 3
   }

   @Test
   void testCalculateWithBrushData() {
      vsDataSet = createVSDataSet(tableLens, "group");
      BrushDataSet brushDataSet = new BrushDataSet(vsDataSet, vsDataSet);
      movingColumn = new MovingColumn("id", "average(id)");

      AverageFormula averageFormula = new AverageFormula();
      movingColumn.setFormula(averageFormula);
      movingColumn.setPreCnt(0);
      movingColumn.setNextCnt(1);
      movingColumn.setIncludeCurrent(true);

      Object result = movingColumn.calculate(brushDataSet, 0, false, false);
      assertEquals(2.0, result); // (1+3)/2=2

      result = movingColumn.calculate(brushDataSet, 1, false, false);
      assertEquals(3.0, result);  //(null + 3)/1 = 3

      result = movingColumn.calculate(brushDataSet, 2, false, false);
      assertEquals(7.0, result); // (null + 7) /1 = 7

      movingColumn.setInnerDim("name");
      result = movingColumn.calculate(brushDataSet, 2, false, false);
      assertNull(result);  // The last value in group A is null, with no next value, return empty.

      assertEquals("name", movingColumn.getInnerDim());
   }

   /**
    * test row size > 1
    */
   @Test
   void  testCalculateWithCrosstabFilter() {
      movingColumn = new MovingColumn("id", "sum(id)");
      movingColumn.setInnerDim(AbstractCalc.ROW_INNER);
      movingColumn.setFormula(new SumFormula());

      CrossTabFilter.CrosstabDataContext mockContext =
         mock(CrossTabFilter.CrosstabDataContext.class);
      when(mockContext.getRowHeaders()).thenReturn(Arrays.asList("group", "name"));

      CrossTabFilter.PairN pairN = createCrosstabFilterPairN("a", null);

      Object result = movingColumn.calculate(mockContext, pairN);
      assertEquals(CalcColumn.INVALID, result);   //isCalculateTotal is false

      movingColumn.setCalculateTotal(true);
      when(mockContext.getValue(pairN)).thenReturn(10);
      result = movingColumn.calculate(mockContext, pairN);
      assertEquals(10.0, result);  // check didn't set precnt and nextcnt

      movingColumn.setPreCnt(1);
      when(mockContext.getValue(pairN)).thenReturn(8);
      result = movingColumn.calculate(mockContext, pairN);
      assertEquals(8.0, result); // check set precnt and nextcnt
   }

   /**
    * test row size = 1
    */
   @Test
   void  testCalculateWithCrosstabFilter2() {
      movingColumn = new MovingColumn("id", "sum(id)");
      movingColumn.setInnerDim("name");
      movingColumn.setFormula(new SumFormula());
      movingColumn.setCalculateTotal(true);
      movingColumn.setPreCnt(1);
      movingColumn.setNextCnt(1);

      CrossFilter.Tuple rowTuple = new CrossFilter.Tuple(new Object[] { "a" });
      List<Object> values = Arrays.asList("b", "a", "c");
      CrossTabFilter.PairN pairA = createCrosstabFilterPairN("a", null);

      CrossTabFilter.CrosstabDataContext mockContext =
         mock(CrossTabFilter.CrosstabDataContext.class);
      when(mockContext.getRowHeaders()).thenReturn(Arrays.asList("name"));
      when(mockContext.getValues(rowTuple, "", 0, true)).thenReturn(values);
      when(mockContext.getValue(
         createCrosstabFilterTuple("b"),
         createCrosstabFilterTuple(null), 0)).thenReturn(1);
      when(mockContext.getValue(pairA)).thenReturn(2);
      when(mockContext.getValue(
         createCrosstabFilterTuple("c"),
         createCrosstabFilterTuple(null), 0)).thenReturn(2);

      Object result = movingColumn.calculate(mockContext, pairA);
      assertEquals(3.0, result);  //1+2=3

      // check include current value
      movingColumn.setIncludeCurrent(true);
      result = movingColumn.calculate(mockContext, pairA);
      assertEquals(5.0, result); //1+2+2=3
   }

   private VSDataSet createVSDataSet(DefaultTableLens tableLens, String name) {
      VSDimensionRef mockDRef = mock(VSDimensionRef.class);
      when(mockDRef.getFullName()).thenReturn(name);
      vsDataSet = new VSDataSet(tableLens, new VSDataRef[] { mockDRef });

      return vsDataSet;
   }

   private CrossTabFilter.PairN createCrosstabFilterPairN(Object rowValue, Object colValue) {
      CrossFilter.Tuple rowTuple = new CrossFilter.Tuple(new Object[] { rowValue });
      CrossFilter.Tuple colTuple = new CrossFilter.Tuple(new Object[] { colValue });
      CrossTabFilter.PairN pairN = new CrossTabFilter.PairN(rowTuple, colTuple, 0);

      return pairN;
   }

   private CrossFilter.Tuple createCrosstabFilterTuple(Object value) {
      return new CrossFilter.Tuple(new Object[] { value });
   }

   /**
    * Moving minimum over a window of previous+current+next using MinFormula.
    * MinFormula skips null values, so the result is the minimum of non-null entries.
    * The formula returns the same type as the input data (Integer).
    */
   @Test
   void testMovingMinFormula() {
      vsDataSet = createVSDataSet(tableLens, "name");
      movingColumn = new MovingColumn("id", "min(id)");

      MinFormula minFormula = new MinFormula();
      movingColumn.setFormula(minFormula);
      movingColumn.setPreCnt(1);
      movingColumn.setNextCnt(1);
      movingColumn.setIncludeCurrent(true);

      // row 0 (1): window = [1, 3] (next=row1=3) → min = 1
      Object result = movingColumn.calculate(vsDataSet, 0, false, false);
      assertEquals(1, result);

      // row 1 (3): window = [1, 3, null] → min of non-null = 1
      result = movingColumn.calculate(vsDataSet, 1, false, false);
      assertEquals(1, result);

      // row 3 (7): window = [null, 7, 1] → min of non-null = 1
      result = movingColumn.calculate(vsDataSet, 3, false, false);
      assertEquals(1, result);
   }

   /**
    * Moving maximum over a window using MaxFormula.
    * The formula returns the same type as the input data (Integer).
    */
   @Test
   void testMovingMaxFormula() {
      vsDataSet = createVSDataSet(tableLens, "name");
      movingColumn = new MovingColumn("id", "max(id)");

      MaxFormula maxFormula = new MaxFormula();
      movingColumn.setFormula(maxFormula);
      movingColumn.setPreCnt(1);
      movingColumn.setNextCnt(0);
      movingColumn.setIncludeCurrent(true);

      // row 1 (3): window = [1, 3] → max = 3
      Object result = movingColumn.calculate(vsDataSet, 1, false, false);
      assertEquals(3, result);

      // row 3 (7): window = [null, 7] → max = 7
      result = movingColumn.calculate(vsDataSet, 3, false, false);
      assertEquals(7, result);

      // row 4 (1): window = [7, 1] → max = 7
      result = movingColumn.calculate(vsDataSet, 4, false, false);
      assertEquals(7, result);
   }

   /**
    * When showNull=true and window is incomplete (< preCnt values before), result is null.
    */
   @Test
   void testShowNullWhenPartialWindow() {
      vsDataSet = createVSDataSet(tableLens, "name");
      movingColumn = new MovingColumn("id", "sum(id)");

      movingColumn.setFormula(new SumFormula());
      movingColumn.setPreCnt(2);
      movingColumn.setNextCnt(0);
      movingColumn.setIncludeCurrent(true);
      movingColumn.setShowNull(true);

      // row 0 has only 0 previous values but requires 2 → null
      assertNull(movingColumn.calculate(vsDataSet, 0, false, false));

      // row 1 has only 1 previous value but requires 2 → null
      assertNull(movingColumn.calculate(vsDataSet, 1, false, false));

      // row 2 has 2 previous values → result is non-null
      // [1, 3, null] where null treated as 0 → sum = 4
      Object result = movingColumn.calculate(vsDataSet, 2, false, false);
      assertEquals(4.0, result);
   }

   /**
    * When showNull=false (default), partial windows are still computed with available data.
    */
   @Test
   void testNoShowNullYieldsResultWithPartialWindow() {
      vsDataSet = createVSDataSet(tableLens, "name");
      movingColumn = new MovingColumn("id", "sum(id)");

      movingColumn.setFormula(new SumFormula());
      movingColumn.setPreCnt(2);
      movingColumn.setNextCnt(0);
      movingColumn.setIncludeCurrent(true);
      movingColumn.setShowNull(false);

      // row 0: no previous values → sum of just current: 1
      Object result = movingColumn.calculate(vsDataSet, 0, false, false);
      assertEquals(1.0, result);

      // row 1: 1 previous value available → 1 + 3 = 4
      result = movingColumn.calculate(vsDataSet, 1, false, false);
      assertEquals(4.0, result);
   }

   /**
    * Null values within the window are treated as 0 for numeric formulas.
    */
   @Test
   void testNullValuesWithinWindow() {
      vsDataSet = createVSDataSet(tableLens, "name");
      movingColumn = new MovingColumn("id", "sum(id)");

      movingColumn.setFormula(new SumFormula());
      movingColumn.setPreCnt(1);
      movingColumn.setNextCnt(1);
      movingColumn.setIncludeCurrent(true);

      // row 2 is null; window: [3, null, 7] → sum = 10 (null → 0)
      Object result = movingColumn.calculate(vsDataSet, 2, false, false);
      assertEquals(10.0, result);
   }

   /**
    * With no formula set, calculate simply returns the raw data value.
    */
   @Test
   void testNoFormulaReturnsRawValue() {
      vsDataSet = createVSDataSet(tableLens, "name");
      movingColumn = new MovingColumn("id", "id");
      // no formula set
      assertNull(movingColumn.getFormula());
      Object result = movingColumn.calculate(vsDataSet, 0, false, false);
      assertEquals(1, result);
   }

   /**
    * Average formula: moving average including current value.
    */
   @Test
   void testMovingAverageIncludingCurrent() {
      vsDataSet = createVSDataSet(tableLens, "name");
      movingColumn = new MovingColumn("id", "avg(id)");

      AverageFormula averageFormula = new AverageFormula();
      movingColumn.setFormula(averageFormula);
      movingColumn.setPreCnt(1);
      movingColumn.setNextCnt(0);
      movingColumn.setIncludeCurrent(true);

      // row 0 (1): window = [1] → avg = 1
      Object result = movingColumn.calculate(vsDataSet, 0, false, false);
      assertEquals(1.0, result);

      // row 1 (3): window = [1, 3] → avg = 2
      result = movingColumn.calculate(vsDataSet, 1, false, false);
      assertEquals(2.0, result);
   }


   /**
    * Regression test for the "Moving Average of N misaligned when the dimension's display
    * sort is not its calendar order" bug, reproduced by the datatest chart suite
    * (Binding_Spec / binding7).
    *
    * A part-date-group dimension (HourOfDay) under a Top-N "Sort By Value" ranking is
    * plotted in ranking order (5, 11, 1, 2), not calendar order (1, 2, 5, 11).
    *
    * This test originally (Bug #76514) asserted that the moving window must still navigate
    * in calendar order even under this ranking — "a moving average only means something over
    * calendar-adjacent neighbors." Investigating a related bug, #76911 (Moving Average of 5
    * on a value-sorted WeekOfYear dimension in viewsheet binding11), showed that premise was
    * wrong: the user settled the underlying design question as sort-first-then-calculate,
    * uniformly for ValueOfColumn, RunningTotalColumn, AND MovingColumn alike (Bug #76906,
    * round 2) — a moving average's "neighbor" is the adjacent bar in whatever order the chart
    * actually displays it in (ranking order included), not a calendar-adjacency concept
    * layered on top of that order. So this test now asserts the corrected expectation: the
    * window is built and walked in display/ranking order (5, 11, 1, 2), not calendar order.
    * Do not flip this back to calendar order without re-reading this history and
    * ValueOfColumnTest.testPreviousOnPartDateGroupFollowsRankingSortOrder's docstring.
    *
    * The row-index/window-walk-alignment fix from #76514 (MovingColumn.orderRowsByDim(),
    * walking the window in the router's actual order rather than raw sub-dataset row index)
    * is still correct and unchanged — it's needed regardless of what order the router
    * produces, and this test still exercises it (the sub data set keeps the physical/ranking
    * row order, so the window must be walked via the router's order, not raw row index).
    *
    * Centered 3-point average, null at the truncated ends. Display/ranking order is
    * 5(275), 11(320), 1(173), 2(166), so hour 11 = avg(275,320,173) and
    * hour 1 = avg(320,173,166), while the first and last hours in display order are null.
    */
   @Test
   void testMovingAverageFollowsDisplayOrderOnValueSortedPartDateDim() {
      final String dim = "HourOfDay(order_time)";
      // physical/display order is the Top-N ranking order, not calendar order
      DefaultTableLens tb = new DefaultTableLens(new Object[][]{
         { dim, "Sum(employee_id)" },
         { 5, 275 },
         { 11, 320 },
         { 1, 173 },
         { 2, 166 }
      });

      List<Integer> rank = Arrays.asList(5, 11, 1, 2);
      VSDimensionRef hourRef = mock(VSDimensionRef.class);
      when(hourRef.getFullName()).thenReturn(dim);
      when(hourRef.getDateLevel()).thenReturn(XConstants.HOUR_OF_DAY_DATE_GROUP);
      when(hourRef.getOrder()).thenReturn(XConstants.SORT_VALUE_DESC);
      when(hourRef.createComparator(org.mockito.ArgumentMatchers.any()))
         .thenReturn((Comparator) (a, b) -> Integer.compare(rank.indexOf(a), rank.indexOf(b)));

      vsDataSet = new VSDataSet(tb, new VSDataRef[]{ hourRef });

      movingColumn = new MovingColumn("Sum(employee_id)", "Moving Average of 3: Sum(employee_id)");
      movingColumn.setFormula(new AverageFormula());
      movingColumn.setPreCnt(1);
      movingColumn.setNextCnt(1);
      movingColumn.setIncludeCurrent(true);
      movingColumn.setShowNull(true);
      movingColumn.setInnerDim(dim);

      // row 0 = hour 5, first in display order -> truncated window -> null
      assertNull(movingColumn.calculate(vsDataSet, 0, true, false));

      // row 1 = hour 11; display window [5, 11, 1] -> (275 + 320 + 173) / 3
      assertEquals(256.0,
                   (Double) movingColumn.calculate(vsDataSet, 1, false, false), 0.0001);

      // row 2 = hour 1; display window [11, 1, 2] -> (320 + 173 + 166) / 3
      assertEquals(219.6667,
                   (Double) movingColumn.calculate(vsDataSet, 2, false, false), 0.0001);

      // row 3 = hour 2, last in display order -> truncated window -> null
      assertNull(movingColumn.calculate(vsDataSet, 3, false, true));
   }

   /**
    * Regression test for Bug #76911: "Moving Average of 5" on a value-sorted part-date
    * dimension (WeekOfYear(日期) in viewsheet binding11, under a Bottom/Top-N "Sort By Value"
    * ranking on Mode(成交金额) ascending) reported the boundary row (the first week in
    * display/ranking order) averaging a full 5-point window computed in calendar order
    * (weeks 9,10,11,12,13) instead of a boundary-clipped average of the first 3 rows in
    * display order. This is the same DataSetRouter mechanism as
    * {@link #testMovingAverageFollowsDisplayOrderOnValueSortedPartDateDim()}, exercised with a
    * centered window of 5 (previous=2, next=2, includeCurrentValue=true) and
    * nullIfNoEnoughValue=false, i.e. a truncated boundary window is averaged rather than
    * shown as null (unlike the showNull=true case above).
    *
    * The numbers here are a simplified synthetic scenario matching #76911's reported shape
    * (a value-sorted part-date dimension, boundary row first in display order, centered
    * window, boundary-clipped-not-null) rather than a digit-for-digit reproduction of the
    * report's real data, since the existing mock DataSet/XDimensionRef test harness doesn't
    * carry the original viewsheet's actual row values.
    *
    * Display/ranking order is week 11, 9, 13, 10, 12 (non-monotonic vs. calendar order
    * 9,10,11,12,13), with values 100, 200, 300, 400, 500 respectively.
    */
   @Test
   void testMovingAverageOf5FollowsDisplayOrderOnValueSortedWeekOfYear() {
      final String dim = "WeekOfYear(日期)";
      // physical/display order is the value-sort ranking order, not calendar order
      DefaultTableLens tb = new DefaultTableLens(new Object[][]{
         { dim, "Mode(成交金额)" },
         { 11, 100 },
         { 9, 200 },
         { 13, 300 },
         { 10, 400 },
         { 12, 500 }
      });

      List<Integer> rank = Arrays.asList(11, 9, 13, 10, 12);
      VSDimensionRef weekRef = mock(VSDimensionRef.class);
      when(weekRef.getFullName()).thenReturn(dim);
      when(weekRef.getDateLevel()).thenReturn(XConstants.WEEK_OF_YEAR_DATE_GROUP);
      when(weekRef.getOrder()).thenReturn(XConstants.SORT_VALUE_ASC);
      when(weekRef.createComparator(org.mockito.ArgumentMatchers.any()))
         .thenReturn((Comparator) (a, b) -> Integer.compare(rank.indexOf(a), rank.indexOf(b)));

      vsDataSet = new VSDataSet(tb, new VSDataRef[]{ weekRef });

      movingColumn = new MovingColumn("Mode(成交金额)", "Moving Average of 5: Mode(成交金额)");
      movingColumn.setFormula(new AverageFormula());
      movingColumn.setPreCnt(2);
      movingColumn.setNextCnt(2);
      movingColumn.setIncludeCurrent(true);
      movingColumn.setShowNull(false);
      movingColumn.setInnerDim(dim);

      // row 0 = week 11, first in display order -> clipped window [11, 9, 13] -> (100+200+300)/3
      assertEquals(200.0,
                   (Double) movingColumn.calculate(vsDataSet, 0, true, false), 0.0001);

      // row 1 = week 9 -> clipped window [11, 9, 13, 10] -> (100+200+300+400)/4
      assertEquals(250.0,
                   (Double) movingColumn.calculate(vsDataSet, 1, false, false), 0.0001);

      // row 2 = week 13, only row with a full 5-point window -> (100+200+300+400+500)/5
      assertEquals(300.0,
                   (Double) movingColumn.calculate(vsDataSet, 2, false, false), 0.0001);

      // row 3 = week 10 -> clipped window [9, 13, 10, 12] -> (200+300+400+500)/4
      assertEquals(350.0,
                   (Double) movingColumn.calculate(vsDataSet, 3, false, false), 0.0001);

      // row 4 = week 12, last in display order -> clipped window [13, 10, 12] -> (300+400+500)/3
      assertEquals(400.0,
                   (Double) movingColumn.calculate(vsDataSet, 4, false, true), 0.0001);
   }

}
