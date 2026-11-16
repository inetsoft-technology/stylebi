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

import inetsoft.graph.AxisSpec;
import inetsoft.graph.EGraph;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.scale.Scale;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XConstants;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizMark;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The axis label rotation and length limit GraphGenerator picks for a marked chart.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ModernAxisLabelRuleTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void eightShortNamesStayFlat() {
      Scale x = xScale(barChart(names("", 8, false)), VizMark.MODERN_LIGHT);

      assertEquals(0, rotation(x));
      assertEquals(66, x.getAxisSpec().getLabelLimit(), 0.01);
   }

   @Test
   void thirtyStoresTurnTo45() {
      Scale x = xScale(barChart(names("Store ", 30, true)), VizMark.MODERN_LIGHT);

      assertEquals(45, rotation(x));
      assertEquals(66, x.getAxisSpec().getLabelLimit(), 0.01);
   }

   @Test
   void classicThirtyStoresStillTurn90() {
      Scale x = xScale(barChart(names("Store ", 30, true)), null);

      assertEquals(90, rotation(x));
      assertEquals(0, x.getAxisSpec().getLabelLimit(), 0.01);
   }

   @Test
   void dailyDatesStayFlat() {
      VSChartDimensionRef day = dim("Day");
      day.setDataType(XSchema.DATE);
      day.setDateLevelValue(String.valueOf(XConstants.DAY_DATE_GROUP));
      long start = 1_700_000_000_000L;
      Object[][] rows = new Object[106][];
      rows[0] = new Object[2];

      for(int i = 1; i <= 105; i++) {
         rows[i] = new Object[] { new java.sql.Date(start + i * 86_400_000L), (double) i };
      }

      Scale x = xScale(barChart(day, rows), VizMark.MODERN_LIGHT);

      assertEquals(0, rotation(x));
   }

   @Test
   void aHorizontalBarsCategoriesGetTheColumnLimit() {
      VSChartDimensionRef store = dim("Store");
      VSChartAggregateRef total = agg("Total");
      VSChartInfo cinfo = new DefaultVSChartInfo();
      cinfo.setChartType(GraphTypes.CHART_BAR);
      cinfo.setRTChartType(GraphTypes.CHART_BAR);
      cinfo.addXField(total);
      cinfo.addYField(store);
      cinfo.updateChartType(false);
      Object[][] rows = new Object[7][];
      rows[0] = new Object[] { GraphUtil.getName(store), GraphUtil.getName(total) };

      for(int i = 1; i < 7; i++) {
         rows[i] = new Object[] { "Store " + i, i * 10.0 };
      }

      EGraph graph = generator(new Chart(cinfo, rows), VizMark.MODERN_LIGHT).createEGraph();
      Scale y = scaleOf(graph, "Store");

      assertEquals(0, rotation(y));
      assertEquals(110, y.getAxisSpec().getLabelLimit(), 0.01);
   }

   @Test
   void facetInnerLabelsFollowTheRule() {
      EGraph graph = generator(facetChart(), VizMark.MODERN_LIGHT).createEGraph();
      Scale inner = scaleOf(graph, "State");

      assertNotEquals(90, rotation(inner));
      assertEquals(66, inner.getAxisSpec().getLabelLimit(), 0.01);
      Scale outer = scaleOf(graph, "Region");
      assertNotNull(outer, "premise: a facet outer axis");
      assertEquals(0, rotation(outer));
      assertEquals(0, outer.getAxisSpec().getLabelLimit(), 0.01);
   }

   @Test
   void classicFacetInnerStillTurns90() {
      EGraph graph = generator(facetChart(), null).createEGraph();

      assertEquals(90, rotation(scaleOf(graph, "State")));
   }

   @Test
   void cjkLabelsTurn45Too() {
      String[] cjk = new String[30];

      for(int i = 0; i < cjk.length; i++) {
         cjk[i] = "商店名称" + (i + 1);
      }

      assertEquals(45, rotation(xScale(barChart(cjk), VizMark.MODERN_LIGHT)));
   }

   @Test
   void anAuthorsRotationKeepsTodaysLabels() {
      Chart chart = barChart(names("Store ", 30, true));
      chart.info().getXField(0).getAxisDescriptor().getAxisLabelTextFormat().setRotation(30);
      Scale x = xScale(chart, VizMark.MODERN_LIGHT);

      assertEquals(30, rotation(x));
      assertEquals(0, x.getAxisSpec().getLabelLimit(), 0.01);
   }

   @Test
   void truncateOffKeepsWrapping() {
      Chart chart = barChart(names("Store ", 30, true));
      chart.info().getXField(0).getAxisDescriptor().setTruncate(false);
      Scale x = xScale(chart, VizMark.MODERN_LIGHT);

      assertEquals(0, x.getAxisSpec().getLabelLimit(), 0.01);
   }

   @Test
   void aDateComparisonChartKeepsTodaysXLabels() {
      Chart chart = barChart(names("Store ", 30, true));
      VSChartInfo spy = Mockito.spy(chart.info());
      Mockito.doReturn(true).when(spy).isAppliedDateComparison();
      GraphGenerator gen = generator(new Chart(spy, chart.rows()), VizMark.MODERN_LIGHT);

      assertFalse(gen.isModernLabelAxis(new AxisSpec(), true));
      assertTrue(gen.isModernLabelAxis(new AxisSpec(), false));
   }

   private static String[] names(String prefix, int count, boolean numbered) {
      String[] names = new String[count];

      for(int i = 0; i < count; i++) {
         names[i] = numbered ? String.format("%s%02d", prefix, i + 1)
            : String.valueOf((char) ('A' + i));
      }

      return names;
   }

   private static Scale scaleOf(EGraph graph, String field) {
      for(Scale scale : graph.getCoordinate().getScales()) {
         if(scale.getFields().length > 0 && scale.getFields()[0].contains(field)) {
            return scale;
         }
      }

      return null;
   }

   private static int rotation(Scale scale) {
      return (int) scale.getAxisSpec().getTextSpec().getRotation();
   }

   private static Scale xScale(Chart chart, VizMark mark) {
      EGraph graph = generator(chart, mark).createEGraph();
      return ((RectCoord) graph.getCoordinate()).getXScale();
   }

   private record Chart(VSChartInfo info, Object[][] rows) {
   }

   private static Chart barChart(String[] categories) {
      VSChartDimensionRef store = dim("Store");
      Object[][] rows = new Object[categories.length + 1][];
      rows[0] = new Object[2];

      for(int i = 0; i < categories.length; i++) {
         rows[i + 1] = new Object[] { categories[i], (i + 1) * 10.0 };
      }

      return barChart(store, rows);
   }

   private static Chart barChart(VSChartDimensionRef dim, Object[][] rows) {
      VSChartAggregateRef total = agg("Total");
      VSChartInfo cinfo = new DefaultVSChartInfo();
      cinfo.setChartType(GraphTypes.CHART_BAR);
      cinfo.setRTChartType(GraphTypes.CHART_BAR);
      cinfo.addXField(dim);
      cinfo.addYField(total);
      cinfo.updateChartType(false);
      rows[0][0] = GraphUtil.getName(dim);
      rows[0][1] = GraphUtil.getName(total);
      return new Chart(cinfo, rows);
   }

   private static Chart facetChart() {
      VSChartDimensionRef region = dim("Region");
      VSChartDimensionRef state = dim("State");
      VSChartAggregateRef total = agg("Total");
      VSChartInfo cinfo = new DefaultVSChartInfo();
      cinfo.setChartType(GraphTypes.CHART_BAR);
      cinfo.setRTChartType(GraphTypes.CHART_BAR);
      cinfo.addXField(region);
      cinfo.addXField(state);
      cinfo.addYField(total);
      cinfo.updateChartType(false);
      List<Object[]> rows = new ArrayList<>();
      rows.add(new Object[] { GraphUtil.getName(region), GraphUtil.getName(state),
                              GraphUtil.getName(total) });

      for(int r = 0; r < 2; r++) {
         for(int s = 0; s < 4; s++) {
            rows.add(new Object[] { "R" + r, "State " + s, 10.0 * (s + 1) });
         }
      }

      return new Chart(cinfo, rows.toArray(new Object[0][]));
   }

   private static GraphGenerator generator(Chart chart, VizMark mark) {
      ChartVSAssemblyInfo assemblyInfo = new ChartVSAssemblyInfo();
      assemblyInfo.setVSChartInfo(chart.info());
      assemblyInfo.setChartDescriptor(new ChartDescriptor());
      assemblyInfo.setVizMark(mark);

      return GraphGenerator.getGenerator(assemblyInfo, null, new DefaultDataSet(chart.rows()),
                                         new VariableTable(), null, XSourceInfo.NONE,
                                         new Dimension(400, 240));
   }

   private static VSChartDimensionRef dim(String name) {
      VSChartDimensionRef dim = new VSChartDimensionRef(new BaseField(name));
      dim.setGroupColumnValue(name);
      return dim;
   }

   private static VSChartAggregateRef agg(String name) {
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setDataRef(new BaseField(name));
      agg.setFormulaValue("Sum");
      return agg;
   }
}
