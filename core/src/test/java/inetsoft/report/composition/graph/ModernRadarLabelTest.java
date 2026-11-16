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
import inetsoft.graph.Plotter;
import inetsoft.graph.VGraph;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.guide.axis.Axis;
import inetsoft.graph.guide.axis.DefaultAxis;
import inetsoft.graph.coord.AbstractParallelCoord;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.coord.PolarCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.VariableTable;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizMark;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The spoke-name rules GraphGenerator hands a radar under the modern mark.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ModernRadarLabelTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aMarkedRadarDropsSumOnItsSpokes() {
      AxisSpec spec = spokeSpec(GraphTypes.CHART_RADAR, VizMark.MODERN_LIGHT, r -> { });

      assertEquals(Map.of("Sum(Price)", "Price", "Sum(Discount)", "Discount"),
                   spec.getDisplayTexts());
      assertEquals(66, spec.getLabelLimit(), 0.01);
   }

   @Test
   void anAuthorSpokeAliasGetsNoDisplayText() {
      AxisSpec spec = spokeSpec(GraphTypes.CHART_RADAR, VizMark.MODERN_LIGHT, r -> r.getLabelAxisDescriptor().setLabelAlias("Sum(Price)", "Cost"));

      assertEquals(Map.of("Sum(Discount)", "Discount"), spec.getDisplayTexts());
   }

   @Test
   void aFilledRadarDoesTheSame() {
      AxisSpec spec = spokeSpec(GraphTypes.CHART_FILL_RADAR, VizMark.MODERN_LIGHT, r -> { });

      assertEquals(Map.of("Sum(Price)", "Price", "Sum(Discount)", "Discount"),
                   spec.getDisplayTexts());
      assertEquals(66, spec.getLabelLimit(), 0.01);
   }

   @Test
   void aOneMeasureRadarTakesTheLimitOnly() {
      RadarVSChartInfo radar = new RadarVSChartInfo();
      VSChartAggregateRef price = agg("Price", "Sum");
      VSChartDimensionRef state = dim("State");
      radar.addYField(price);
      radar.addGroupField(state);
      radar.setChartType(GraphTypes.CHART_RADAR);
      radar.setRTChartType(GraphTypes.CHART_RADAR);
      radar.updateChartType(false);
      Object[][] rows = {
         { "State", GraphUtil.getName(price) },
         { "CA", 1.0 },
         { "NY", 2.0 },
         { "TX", 3.0 },
      };
      AxisSpec spec = spec(radar, rows, VizMark.MODERN_LIGHT, new ChartDescriptor());

      assertEquals(66, spec.getLabelLimit(), 0.01);
      assertNull(spec.getDisplayTexts());
   }

   @Test
   void truncateOffKeepsTodaysCut() {
      AxisSpec spec = spokeSpec(GraphTypes.CHART_RADAR, VizMark.MODERN_LIGHT, r -> r.getLabelAxisDescriptor().setTruncate(false));

      assertEquals(0, spec.getLabelLimit(), 0.01);
   }

   @Test
   void classicRadarUnchanged() {
      AxisSpec spec = spokeSpec(GraphTypes.CHART_RADAR, null, r -> { });

      assertNull(spec.getDisplayTexts());
      assertEquals(0, spec.getLabelLimit(), 0.01);
   }

   @Test
   void aMarkedRadarTurnsOnTheSpokeRule() {
      assertTrue(((PolarCoord) threeMeasures(VizMark.MODERN_LIGHT, r -> { }).graph
                     .getCoordinate()).isSpokeLabelRule());
      assertFalse(((PolarCoord) threeMeasures(null, r -> { }).graph.getCoordinate())
                     .isSpokeLabelRule());

      RadarVSChartInfo radar = new RadarVSChartInfo();
      VSChartAggregateRef price = agg("Price", "Sum");
      radar.addYField(price);
      radar.addGroupField(dim("State"));
      radar.setChartType(GraphTypes.CHART_RADAR);
      radar.setRTChartType(GraphTypes.CHART_RADAR);
      radar.updateChartType(false);
      Object[][] rows = {
         { "State", GraphUtil.getName(price) },
         { "CA", 1.0 },
         { "NY", 2.0 },
         { "TX", 3.0 },
      };

      assertTrue(((PolarCoord) graph(radar, rows, VizMark.MODERN_LIGHT, new ChartDescriptor())
                     .getCoordinate()).isSpokeLabelRule());
      assertFalse(((PolarCoord) graph(radar, rows, null, new ChartDescriptor())
                      .getCoordinate()).isSpokeLabelRule());
   }

   @Test
   void authorMinAndMaxMakeSpokesShareLabels() {
      Built built = threeMeasures(VizMark.MODERN_LIGHT, r -> {
         for(var ref : r.getAggregateRefs()) {
            AxisDescriptor desc = ((VSChartAggregateRef) ref).getAxisDescriptor();
            desc.setMinimum(0);
            desc.setMaximum(100);
         }
      });
      VGraph vgraph = Plotter.getPlotter(built.graph)
         .plotAndLayout(new DefaultDataSet(built.rows), 0, 0, 400, 400);
      List<Integer> counts = new ArrayList<>();

      for(Axis axis : vgraph.getCoordinate().getAxes(false)) {
         if(axis instanceof DefaultAxis a) {
            counts.add(shown(a));
         }
      }

      assertEquals(3, counts.size());
      assertEquals(1, counts.stream().filter(c -> c > 0).count(), counts.toString());
   }

   private static int shown(DefaultAxis axis) {
      int n = 0;

      for(VLabel label : axis.getLabels()) {
         if(label.getZIndex() >= 0) {
            n++;
         }
      }

      return n;
   }

   private record Built(EGraph graph, Object[][] rows) {
   }

   // the generated graph of the three-measure radar
   private static Built threeMeasures(VizMark mark, Consumer<RadarChartInfo> setup) {
      RadarVSChartInfo radar = new RadarVSChartInfo();
      VSChartAggregateRef price = agg("Price", "Sum");
      VSChartAggregateRef qty = agg("Quantity", "Average");
      VSChartAggregateRef discount = agg("Discount", "Sum");
      radar.addYField(price);
      radar.addYField(qty);
      radar.addYField(discount);
      radar.setChartType(GraphTypes.CHART_RADAR);
      radar.setRTChartType(GraphTypes.CHART_RADAR);
      radar.updateChartType(false);
      setup.accept(radar);
      Object[][] rows = {
         { GraphUtil.getName(price), GraphUtil.getName(qty), GraphUtil.getName(discount) },
         { 10.0, 20.0, 30.0 },
         { 30.0, 20.0, 10.0 },
      };

      return new Built(graph(radar, rows, mark, new ChartDescriptor()), rows);
   }

   // the spoke-name spec of a radar of Sum(Price), Avg(Quantity) and Sum(Discount)
   private static AxisSpec spokeSpec(int type, VizMark mark, Consumer<RadarChartInfo> setup) {
      RadarVSChartInfo radar = new RadarVSChartInfo();
      VSChartAggregateRef price = agg("Price", "Sum");
      VSChartAggregateRef qty = agg("Quantity", "Average");
      VSChartAggregateRef discount = agg("Discount", "Sum");
      radar.addYField(price);
      radar.addYField(qty);
      radar.addYField(discount);
      radar.setChartType(type);
      radar.setRTChartType(type);
      radar.updateChartType(false);
      setup.accept(radar);
      Object[][] rows = {
         { GraphUtil.getName(price), GraphUtil.getName(qty), GraphUtil.getName(discount) },
         { 1.0, 2.0, 3.0 },
         { 3.0, 2.0, 1.0 },
      };

      return spec(radar, rows, mark, new ChartDescriptor());
   }

   private static AxisSpec spec(VSChartInfo cinfo, Object[][] rows, VizMark mark,
                                ChartDescriptor desc)
   {
      Coordinate coord = graph(cinfo, rows, mark, desc).getCoordinate();
      assertInstanceOf(PolarCoord.class, coord, "premise: a radar");
      Coordinate inner = ((PolarCoord) coord).getCoordinate();
      assertInstanceOf(AbstractParallelCoord.class, inner, "premise: a parallel coord");
      return ((AbstractParallelCoord) inner).getAxisLabelScale().getAxisSpec();
   }

   private static EGraph graph(VSChartInfo cinfo, Object[][] rows, VizMark mark,
                               ChartDescriptor desc)
   {
      ChartVSAssemblyInfo assemblyInfo = new ChartVSAssemblyInfo();
      assemblyInfo.setVSChartInfo(cinfo);
      assemblyInfo.setChartDescriptor(desc);
      assemblyInfo.setVizMark(mark);
      return GraphGenerator.getGenerator(assemblyInfo, null, new DefaultDataSet(rows),
                                         new VariableTable(), null, XSourceInfo.NONE,
                                         new Dimension(400, 400)).createEGraph();
   }

   private static VSChartDimensionRef dim(String name) {
      VSChartDimensionRef dim = new VSChartDimensionRef(new BaseField(name));
      dim.setGroupColumnValue(name);
      return dim;
   }

   private static VSChartAggregateRef agg(String name, String formula) {
      VSChartAggregateRef agg = new VSChartAggregateRef();
      BaseField field = new BaseField(name);
      field.setDataType(XSchema.DOUBLE);
      agg.setDataRef(field);
      agg.setFormulaValue(formula);
      return agg;
   }
}
