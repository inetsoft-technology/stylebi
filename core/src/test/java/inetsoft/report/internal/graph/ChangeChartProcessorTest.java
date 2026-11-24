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
package inetsoft.report.internal.graph;

import inetsoft.test.*;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.NamedRangeRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.util.CoreTool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Small, self-contained operations of {@link ChangeChartProcessor} that the chart type,
 * data and swap processors share: whether a chart is "fake" (no measure), keeping a single
 * measure for charts that allow only one, ordering dimensions before measures, the default
 * Pareto sorting and the group option of dimension refs.
 *
 * Callers: ChangeChartTypeProcessor, ChangeChartDataProcessor, SwapXYBindingProcessor and the
 * web services that drive them; the same helper methods are used by every one of them, so a
 * change here shows up in all the chart-edit operations.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ChangeChartProcessorTest {
   private final ChangeChartProcessor processor = new ChangeChartProcessor();
   private VSChartInfo info;

   @BeforeEach
   void setUp() {
      CoreTool.clearUserMessage();
      info = new DefaultVSChartInfo();
      info.setChartType(GraphTypes.CHART_BAR);
   }

   @AfterEach
   void tearDown() {
      CoreTool.clearUserMessage();
   }

   private static VSChartDimensionRef dim(String column) {
      return new VSChartDimensionRef(new ColumnRef(new AttributeRef(null, column)));
   }

   private static VSChartAggregateRef measure(String column) {
      VSChartAggregateRef ref = new VSChartAggregateRef();
      ref.setDataRef(new ColumnRef(new AttributeRef(null, column)));
      return ref;
   }

   private static List<String> names(ChartRef[] refs) {
      List<String> list = new ArrayList<>();

      for(ChartRef ref : refs) {
         list.add(ref.getName());
      }

      return list;
   }

   // ---- isFake ----

   @Test
   void aChartWithOnlyDimensionsIsFake() {
      info.addXField(dim("Region"));
      info.addYField(dim("State"));

      assertTrue(ChangeChartProcessor.isFake(info));
   }

   @Test
   void aChartWithoutFieldsIsFake() {
      assertTrue(ChangeChartProcessor.isFake(info));
   }

   @Test
   void aMeasureOnYMakesTheChartReal() {
      info.addXField(dim("Region"));
      info.addYField(measure("Sales"));

      assertFalse(ChangeChartProcessor.isFake(info));
   }

   @Test
   void aMeasureOnXMakesTheChartReal() {
      info.addXField(measure("Sales"));
      info.addYField(dim("Region"));

      assertFalse(ChangeChartProcessor.isFake(info));
   }

   @Test
   void treemapRelationAndContourChartsAreNeverFake() {
      for(int type : new int[] { GraphTypes.CHART_TREEMAP, GraphTypes.CHART_TREE,
                                 GraphTypes.CHART_SCATTER_CONTOUR })
      {
         VSChartInfo other = new DefaultVSChartInfo();
         other.setChartType(type);
         other.addXField(dim("Region"));

         assertFalse(ChangeChartProcessor.isFake(other), "chart type " + type);
      }
   }

   // ---- checkMeasureRefs ----

   /** A waterfall or pie keeps one measure: the first of the consecutive measures on Y. */
   @Test
   void consecutiveMeasuresOnYAreReducedToTheFirst() {
      info.addYField(measure("Sales"));
      info.addYField(measure("Profit"));
      info.addYField(measure("Cost"));

      processor.checkMeasureRefs(info);

      assertEquals(Arrays.asList("Sales"), names(info.getYFields()));
      assertNotNull(CoreTool.getUserMessage(), "the user is told measures were dropped");
   }

   @Test
   void measuresSeparatedByADimensionAreAllKept() {
      info.addYField(measure("Sales"));
      info.addYField(dim("Region"));
      info.addYField(measure("Profit"));

      processor.checkMeasureRefs(info);

      assertEquals(Arrays.asList("Sales", "Region", "Profit"), names(info.getYFields()));
   }

   @Test
   void aMeasureOnXIsDroppedWhenYAlreadyHasOne() {
      info.addXField(measure("Profit"));
      info.addYField(measure("Sales"));

      processor.checkMeasureRefs(info);

      assertEquals(0, info.getXFieldCount());
      assertEquals(Arrays.asList("Sales"), names(info.getYFields()));
   }

   @Test
   void consecutiveMeasuresOnXAreReducedToTheFirst() {
      info.addXField(measure("Sales"));
      info.addXField(measure("Profit"));

      processor.checkMeasureRefs(info);

      assertEquals(Arrays.asList("Sales"), names(info.getXFields()));
   }

   @Test
   void oneMeasureAndNoMessageWhenNothingHasToBeDropped() {
      info.addXField(dim("Region"));
      info.addYField(measure("Sales"));

      processor.checkMeasureRefs(info);

      assertEquals(1, info.getYFieldCount());
      assertNull(CoreTool.getUserMessage());
   }

   // ---- fixXYRefsOrder ----

   @Test
   void dimensionsAreMovedBeforeMeasuresOnBothAxes() {
      info.addXField(measure("Sales"));
      info.addXField(dim("Region"));
      info.addYField(measure("Profit"));
      info.addYField(dim("State"));
      info.addYField(dim("City"));

      processor.fixXYRefsOrder(info);

      assertEquals(Arrays.asList("Region", "Sales"), names(info.getXFields()));
      assertEquals(Arrays.asList("State", "City", "Profit"), names(info.getYFields()));
   }

   // ---- fixParetoSorting ----

   @Test
   void paretoSortsTheLastDimensionByTheLastMeasureDescending() {
      info.setChartType(GraphTypes.CHART_PARETO);
      VSChartDimensionRef region = dim("Region");
      VSChartDimensionRef state = dim("State");
      info.addXField(region);
      info.addXField(state);
      VSChartAggregateRef profit = measure("Profit");
      info.addYField(measure("Sales"));
      info.addYField(profit);

      processor.fixParetoSorting(info);

      assertEquals(profit.getFullName(false), state.getSortByCol());
      assertEquals(XConstants.SORT_VALUE_DESC, state.getOrder());
      assertEquals(XConstants.SORT_ASC, region.getOrder(), "the other dimension is not touched");
   }

   /** The dimension is looked up on X first, then on Y; the measure on Y first, then on X. */
   @Test
   void paretoPrefersTheDimensionOnXAndTheMeasureOnY() {
      info.setChartType(GraphTypes.CHART_PARETO);
      VSChartDimensionRef onX = dim("Region");
      VSChartDimensionRef onY = dim("State");
      VSChartAggregateRef xMeasure = measure("Cost");
      VSChartAggregateRef yMeasure = measure("Sales");
      info.addXField(onX);
      info.addXField(xMeasure);
      info.addYField(onY);
      info.addYField(yMeasure);

      processor.fixParetoSorting(info);

      assertEquals(yMeasure.getFullName(false), onX.getSortByCol());
      assertEquals(XConstants.SORT_VALUE_DESC, onX.getOrder());
      assertEquals(XConstants.SORT_ASC, onY.getOrder(), "the dimension on Y is not touched");
   }

   @Test
   void paretoKeepsTheOrderOfADimensionWithASpecificNamedGroupOrder() {
      info.setChartType(GraphTypes.CHART_PARETO);
      VSChartDimensionRef state = dim("State");
      state.setOrder(XConstants.SORT_SPECIFIC);
      state.setNamedGroupInfo(new inetsoft.uql.asset.SNamedGroupInfo());
      info.addXField(state);
      info.addYField(measure("Sales"));

      processor.fixParetoSorting(info);

      assertEquals(XConstants.SORT_SPECIFIC, state.getOrder());
   }

   @Test
   void otherChartTypesAreNotSortedForPareto() {
      VSChartDimensionRef state = dim("State");
      info.addXField(state);
      info.addYField(measure("Sales"));

      processor.fixParetoSorting(info);

      assertEquals(XConstants.SORT_ASC, state.getOrder());
      assertNull(state.getSortByCol());
   }

   @Test
   void paretoWithoutADimensionOrWithoutAMeasureChangesNothing() {
      info.setChartType(GraphTypes.CHART_PARETO);
      VSChartDimensionRef state = dim("State");
      info.addXField(state);

      processor.fixParetoSorting(info);

      assertEquals(XConstants.SORT_ASC, state.getOrder());
   }

   // ---- fixGroupOption ----

   @Test
   void everyDimensionOfTheChartGetsTheDataGroupType() {
      VSChartDimensionRef x = dim("Region");
      VSChartDimensionRef y = dim("State");
      x.setGroupType("" + NamedRangeRef.DATA_GROUP);
      y.setGroupType("1");
      info.addXField(x);
      info.addYField(y);

      ChangeChartProcessor.fixGroupOption(info);

      assertEquals("" + NamedRangeRef.DATA_GROUP, x.getGroupType());
      assertEquals("" + NamedRangeRef.DATA_GROUP, y.getGroupType());
   }
}
