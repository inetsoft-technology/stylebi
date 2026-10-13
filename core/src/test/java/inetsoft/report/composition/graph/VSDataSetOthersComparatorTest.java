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

package inetsoft.report.composition.graph;

import inetsoft.graph.aesthetic.CategoricalSizeFrame;
import inetsoft.graph.data.CalcColumn;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.StackRange;
import inetsoft.report.composition.graph.calc.ValueOfCalc;
import inetsoft.report.composition.graph.calc.ValueOfColumn;
import inetsoft.report.composition.graph.data.DataSetRouter;
import inetsoft.report.filter.OthersComparator;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.XCondition;
import inetsoft.uql.XConstants;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.VSDataRef;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.util.DefaultComparator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #78469: a dimension with Sort None/Original + Top/Bottom N + Group Others creates a bare
 * OthersComparator (#76413), which ties every non-Others value. Installed as the VSDataSet
 * column comparator, it broke the readers that binary search with it (axis scale, calc
 * routers) or treat a null comparator as data order (part-date order, brushing, stacking).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VSDataSetOthersComparatorTest {
   private static final String[] STATES =
      { "CA", "NY", "TX", "FL", "IL", "NJ", "PA", "OH", "GA", "MA", "Others" };

   static Stream<Arguments> rankings() {
      return Stream.of(
         Arguments.of(XConstants.SORT_NONE, XCondition.TOP_N),
         Arguments.of(XConstants.SORT_ORIGINAL, XCondition.TOP_N),
         Arguments.of(XConstants.SORT_NONE, XCondition.BOTTOM_N),
         Arguments.of(XConstants.SORT_ORIGINAL, XCondition.BOTTOM_N));
   }

   @ParameterizedTest
   @MethodSource("rankings")
   void datasetComparatorIsNullButLegendComparatorKeepsOthersLast(int order, int ranking) {
      VSDimensionRef ref = rankedRef(order, ranking);
      VSDataSet data = createDataSet(ref, 3);

      assertNull(data.getComparator("State"));
      // the legend/frame sites call the dimension directly and still get Others last (#76413)
      assertInstanceOf(OthersComparator.class, ref.createComparator(data));
   }

   @Test
   void datasetComparatorKeptForAscendingSort() {
      VSDataSet data = createDataSet(rankedRef(XConstants.SORT_ASC, XCondition.TOP_N), 3);
      assertNotNull(data.getComparator("State"));
   }

   // the axis binary searches the scale values when there are more than 9 of them
   @ParameterizedTest
   @MethodSource("rankings")
   void axisMapsEveryValueToItsOwnSlot(int order, int ranking) {
      VSDataSet data = createDataSet(rankedRef(order, ranking), STATES.length);
      CategoricalScale scale = new CategoricalScale("State");
      scale.init(data);

      assertArrayEquals(STATES, scale.getValues());

      for(int i = 0; i < STATES.length; i++) {
         assertEquals(scale.mapValue(STATES[0]) + i, scale.mapValue(STATES[i]), STATES[i]);
      }
   }

   @ParameterizedTest
   @MethodSource("rankings")
   void routerAndValueOfPreviousFollowDataOrder(int order, int ranking) {
      int n = 6;
      VSDataSet data = createDataSet(rankedRef(order, ranking), n);
      DataSetRouter router = new DataSetRouter(data, "State");

      for(int i = 0; i < n; i++) {
         assertEquals(i, router.getIndex(data.getData("State", i)));
      }

      ValueOfColumn column = new ValueOfColumn("Sales", "sum(Sales)");
      column.setChangeType(ValueOfCalc.PREVIOUS);
      column.setDim("State");

      // CA is the first value and has no previous value
      assertEquals(CalcColumn.INVALID, column.calculate(data, 0, false, false));

      for(int i = 1; i < n; i++) {
         assertEquals(i * 100, column.calculate(data, i, false, false), STATES[i]);
      }
   }

   // a part-date dimension without a display sort navigates in calendar order (76039)
   @Test
   void partDateRouterKeepsCalendarOrder() {
      Comparator others = rankedRef(XConstants.SORT_NONE, XCondition.TOP_N).createComparator(null);
      VSDimensionRef month = mock(VSDimensionRef.class);
      when(month.getFullName()).thenReturn("MonthOfYear(Date)");
      when(month.getDateLevel()).thenReturn(XConstants.MONTH_OF_YEAR_DATE_GROUP);
      when(month.createComparator(any())).thenReturn(others);

      VSDataSet data = new VSDataSet(new DefaultTableLens(new Object[][] {
         { "MonthOfYear(Date)", "Sales" },
         { 5, 100 },
         { 1, 200 },
         { 3, 300 },
         { "Others", 400 }
      }), new VSDataRef[] { month });

      assertArrayEquals(new Object[] { 1, 3, 5, "Others" },
                        new DataSetRouter(data, "MonthOfYear(Date)").getValues());
   }

   // brushing must not reorder the axis (60087)
   @ParameterizedTest
   @MethodSource("rankings")
   void brushingKeepsAllDataOrder(int order, int ranking) {
      VSDataSet adata = new VSDataSet(new DefaultTableLens(new Object[][] {
         { "State", "Sales" },
         { "CA", 100 },
         { "NY", 200 },
         { "TX", 300 },
         { "Others", 400 }
      }), new VSDataRef[] { rankedRef(order, ranking) });
      VSDataSet data = new VSDataSet(new DefaultTableLens(new Object[][] {
         { "State", "Sales" },
         { "TX", 300 },
         { "CA", 100 },
         { "Others", 400 }
      }), new VSDataRef[] { rankedRef(order, ranking) });

      CategoricalScale scale = new CategoricalScale("State");
      scale.init(new BrushDataSet(adata, data));

      assertArrayEquals(new Object[] { "CA", "NY", "TX", "Others" }, scale.getValues());
   }

   // rows of one x value are grouped before stacking, even when they are not adjacent
   @Test
   void stackRangeSumsNonAdjacentRowsOfOneValue() {
      VSDataSet data = new VSDataSet(new DefaultTableLens(new Object[][] {
         { "State", "Sales" },
         { "CA", 100.0 },
         { "NY", 100.0 },
         { "CA", 500.0 },
         { "NY", 100.0 },
         { "Others", 50.0 }
      }), new VSDataRef[] { rankedRef(XConstants.SORT_NONE, XCondition.TOP_N) });

      StackRange range = new StackRange();
      range.setGroupField("State");

      assertEquals(600.0, range.calculate(data, new String[] { "Sales" }, null)[1]);
   }

   // a frame gets the dimension comparator directly, not from the data set (node size)
   @Test
   void frameWithOthersComparatorMapsEveryValueToItsOwnSize() {
      VSDimensionRef ref = rankedRef(XConstants.SORT_NONE, XCondition.TOP_N);
      VSDataSet data = createDataSet(ref, STATES.length);
      CategoricalSizeFrame frame = new CategoricalSizeFrame("State");
      frame.setComparator(ref.createComparator(data));
      frame.init(data);

      Set<Double> sizes = new HashSet<>();

      for(String state : STATES) {
         sizes.add(frame.getSize(state));
      }

      assertEquals(STATES.length, sizes.size());
   }

   // a binary search hit across number types must not be lost by the exact-match lookup
   @Test
   void mapValueFindsLongInIntegerScale() {
      Object[][] rows = new Object[12][];
      rows[0] = new Object[] { "v" };

      for(int i = 0; i < 11; i++) {
         rows[i + 1] = new Object[] { i };
      }

      CategoricalScale scale = new CategoricalScale("v");
      scale.setComparator(new DefaultComparator());
      scale.init(new DefaultDataSet(rows));

      assertEquals(scale.mapValue(3), scale.mapValue(3L));
      assertFalse(Double.isNaN(scale.mapValue(3L)));
   }

   private static VSDataSet createDataSet(VSDimensionRef ref, int n) {
      Object[][] rows = new Object[n + 1][];
      rows[0] = new Object[] { "State", "Sales" };

      // the last row is always Others, as the summary filter appends it after the cut
      for(int i = 0; i < n; i++) {
         rows[i + 1] = new Object[] { i == n - 1 ? "Others" : STATES[i], (i + 1) * 100 };
      }

      return new VSDataSet(new DefaultTableLens(rows), new VSDataRef[] { ref });
   }

   private static VSDimensionRef rankedRef(int order, int ranking) {
      VSDimensionRef ref = new VSDimensionRef();
      ref.setDataRef(new AttributeRef("State"));
      ref.setOrder(order);
      ref.setRankingOptionValue(ranking + "");
      ref.setRankingNValue("10");
      ref.setGroupOthersValue("true");
      return ref;
   }
}
