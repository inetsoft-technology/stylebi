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

import inetsoft.graph.EGraph;
import inetsoft.graph.GraphConstants;
import inetsoft.graph.Plotter;
import inetsoft.graph.VGraph;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.coord.FacetCoord;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.guide.axis.DefaultAxis;
import inetsoft.graph.guide.axis.GridLine;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.internal.ICoordinate;
import inetsoft.graph.scale.Scale;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.VariableTable;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizMark;
import inetsoft.uql.viewsheet.internal.VizModernizeUtil;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The horizontal grid of a chart with a secondary value axis: a marked chart draws it on the
 * primary value axis ticks, a classic chart on the secondary axis ticks.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ModernDualAxisGridTest {
   @Test
   void aMarkedParetoDrawsTheGridOnTheValueTicks() {
      for(Object[][] rows : new Object[][][] { PARETO_8, PARETO_ROUND }) {
         Drawn drawn = draw(pareto(VizMark.MODERN_LIGHT, rows));
         assertEndsAreTicks(drawn.rect().getYScale(), rows == PARETO_ROUND);
         assertGridOnPrimary(drawn.rect(), drawn.vgraph.getPlotBounds());
      }
   }

   @Test
   void aMarkedBarAndLineOnY2DrawsTheGridOnTheValueTicks() {
      Drawn drawn = draw(dual(VizMark.MODERN_LIGHT, DUAL, false));
      assertEndsAreTicks(drawn.rect().getYScale(), true);
      assertGridOnPrimary(drawn.rect(), drawn.vgraph.getPlotBounds());
   }

   // premise: the scale's min (and its max when asked) is a tick, so it would draw an edge line
   private static void assertEndsAreTicks(Scale scale, boolean max) {
      double[] ticks = scale.getTicks();
      assertEquals(scale.getMin(), ticks[0], 1e-9, "premise: a tick at the min");

      if(max) {
         assertEquals(scale.getMax(), ticks[ticks.length - 1], 1e-9, "premise: a tick at the max");
      }
   }

   @Test
   void aMarkedChartWithOnlySecondaryMeasuresKeepsItsGrid() {
      Drawn drawn = draw(secondaryOnly(VizMark.MODERN_LIGHT));
      RectCoord rect = drawn.rect();

      assertFalse(rect.getYScale().getAxisSpec().isLabelVisible(),
                  "premise: the primary is the unlabelled copy of the secondary");
      assertEndsAreTicks(rect.getYScale(), true);
      assertGridOnPrimary(rect, drawn.vgraph.getPlotBounds());
      assertArrayEquals(rect.getYScale2().getTicks(), rect.getYScale().getTicks(), 1e-9);
   }

   @Test
   void eachMarkedFacetPanelDrawsTheGridOnItsValueTicks() {
      Drawn drawn = draw(dual(VizMark.MODERN_LIGHT, DUAL_FACET, true));
      FacetCoord facet = assertInstanceOf(FacetCoord.class, drawn.vgraph.getCoordinate(),
                                          "premise: a facet");
      int panels = 0;

      for(Coordinate[] row : facet.getExpandedInnerCoords()) {
         for(Coordinate cell : row) {
            assertGridOnPrimary((RectCoord) cell, cell.getVGraph().getPlotBounds());
            panels++;
         }
      }

      assertEquals(2, panels);
   }

   @Test
   void theZeroLineReplacesTheZeroGridLineOfAMarkedDualAxisChart() {
      Drawn drawn = draw(dual(VizMark.MODERN_LIGHT, DUAL_NEGATIVE, false));
      RectCoord rect = drawn.rect();
      DefaultAxis primary = rect.getAxisAt(ICoordinate.LEFT_AXIS);
      DefaultAxis secondary = rect.getAxisAt(ICoordinate.RIGHT_AXIS);
      GridLine zero = primary.getZeroLine();

      assertNotNull(zero, "the zero line stays");
      double zeroY = zero.getShape().getBounds2D().getY();
      Color dark = GTool.darken(primary.getScale().getAxisSpec().getGridColor(), 0.85);

      for(DefaultAxis axis : new DefaultAxis[] { primary, secondary }) {
         for(int i = 0; i < axis.getGridLineCount(); i++) {
            GridLine line = axis.getGridLine(i);
            assertNotEquals(zeroY, line.getShape().getBounds2D().getY(), 0.5);
            assertNotEquals(dark, line.getColor(), "a darkened 0 grid line");
         }
      }

      assertGridOnPrimary(rect, drawn.vgraph.getPlotBounds());
   }

   @Test
   void aScriptsSecondaryGridStillDrawsOnAMarkedChart() {
      GraphGenerator gen = dual(VizMark.MODERN_LIGHT, DUAL, false);
      EGraph graph = gen.createEGraph();
      // what a chart script setting the secondary grid does
      ((RectCoord) graph.getCoordinate()).getYScale2().getAxisSpec()
         .setGridStyle(GraphConstants.THIN_LINE);
      Drawn drawn = plot(gen, graph);

      assertGridOnSecondary(drawn.rect());
   }

   @Test
   void aClassicParetoKeepsTheGridOnThePercentTicks() {
      for(Object[][] rows : new Object[][][] { PARETO_8, PARETO_ROUND }) {
         assertGridOnSecondary(draw(pareto(null, rows)).rect());
      }
   }

   @Test
   void aClassicBarAndLineOnY2KeepsTheGridOnTheSecondaryTicks() {
      RectCoord rect = draw(dual(null, DUAL, false)).rect();
      assertGridOnSecondary(rect);
      assertFalse(rect.getYScale().getAxisSpec().isEdgeGridTrimmed());
   }

   // every horizontal grid line sits on a primary tick, and none on the plot edges
   private static void assertGridOnPrimary(RectCoord rect, Rectangle2D plot) {
      assertNotNull(rect.getYScale2(), "premise: a secondary scale");
      DefaultAxis primary = rect.getAxisAt(ICoordinate.LEFT_AXIS);
      DefaultAxis secondary = rect.getAxisAt(ICoordinate.RIGHT_AXIS);

      assertTrue(secondary == null || secondary.getGridLineCount() == 0,
                 "the secondary axis draws no grid");
      assertTrue(primary.getGridLineCount() > 0, "the primary axis draws the grid");
      List<Double> ticks = tickYs(primary);

      for(double y : gridYs(primary)) {
         assertTrue(ticks.stream().anyMatch(t -> Math.abs(t - y) < 0.5),
                    "line at " + y + " is off the primary ticks " + ticks);
         assertTrue(y > plot.getMinY() + 1 && y < plot.getMaxY() - 1,
                    "line at " + y + " on the edge of " + plot);
      }
   }

   private static void assertGridOnSecondary(RectCoord rect) {
      assertNotNull(rect.getYScale2(), "premise: a secondary scale");
      DefaultAxis primary = rect.getAxisAt(ICoordinate.LEFT_AXIS);
      DefaultAxis secondary = rect.getAxisAt(ICoordinate.RIGHT_AXIS);

      assertEquals(0, primary.getGridLineCount(), "the primary axis draws no grid");
      assertTrue(secondary.getGridLineCount() > 0, "the secondary axis draws the grid");
      List<Double> ticks = tickYs(secondary);

      for(double y : gridYs(secondary)) {
         assertTrue(ticks.stream().anyMatch(t -> Math.abs(t - y) < 0.5),
                    "line at " + y + " is off the secondary ticks " + ticks);
      }
   }

   private static List<Double> gridYs(DefaultAxis axis) {
      List<Double> ys = new ArrayList<>();

      for(int i = 0; i < axis.getGridLineCount(); i++) {
         Rectangle2D box = axis.getGridLine(i).getShape().getBounds2D();
         assertEquals(0, box.getHeight(), 0.01, "premise: a horizontal grid line");
         ys.add(box.getY());
      }

      return ys;
   }

   private static List<Double> tickYs(DefaultAxis axis) {
      Scale scale = axis.getScale();
      List<Double> ys = new ArrayList<>();

      for(double tick : scale.getTicks()) {
         double pos = (tick - scale.getMin()) * axis.getLength() /
            (scale.getMax() - scale.getMin());
         ys.add(axis.getScreenTransform().transform(new Point2D.Double(pos, 0), null).getY());
      }

      return ys;
   }

   private record Drawn(VGraph vgraph) {
      RectCoord rect() {
         return assertInstanceOf(RectCoord.class, vgraph.getCoordinate(), "premise: a rect");
      }
   }

   private static Drawn draw(GraphGenerator gen) {
      return plot(gen, gen.createEGraph());
   }

   private static Drawn plot(GraphGenerator gen, EGraph graph) {
      return new Drawn(Plotter.getPlotter(graph).plotAndLayout(gen.getData(), 0, 0, 400, 240));
   }

   private static GraphGenerator pareto(VizMark mark, Object[][] data) {
      VSChartInfo cinfo = new DefaultVSChartInfo();
      cinfo.setChartType(GraphTypes.CHART_PARETO);
      cinfo.setRTChartType(GraphTypes.CHART_PARETO);
      VSChartDimensionRef defect = dim("Defect");
      VSChartAggregateRef count = measure("Count");
      cinfo.addXField(defect);
      cinfo.addYField(count);
      cinfo.updateChartType(false);

      return generator(cinfo, mark, rows(data, defect, count));
   }

   // Revenue a bar, Margin a line on the secondary axis, by Region when faceted
   private static GraphGenerator dual(VizMark mark, Object[][] data, boolean facet) {
      VSChartInfo cinfo = new DefaultVSChartInfo();
      cinfo.setChartType(GraphTypes.CHART_BAR);
      cinfo.setRTChartType(GraphTypes.CHART_BAR);
      cinfo.setSeparatedGraph(false);
      cinfo.setMultiStyles(true);
      List<ChartRef> refs = new ArrayList<>();

      if(facet) {
         VSChartDimensionRef region = dim("Region");
         cinfo.addXField(region);
         refs.add(region);
      }

      VSChartDimensionRef cat = dim("DCat");
      VSChartAggregateRef revenue = measure("Revenue");
      VSChartAggregateRef margin = measure("Margin");
      revenue.setChartType(GraphTypes.CHART_BAR);
      margin.setChartType(GraphTypes.CHART_LINE);
      margin.setSecondaryY(true);
      cinfo.addXField(cat);
      cinfo.addYField(revenue);
      cinfo.addYField(margin);
      cinfo.updateChartType(false);

      // the permission check in updateChartType has no principal here
      cinfo.setRTChartType(GraphTypes.CHART_BAR);
      revenue.setRTChartType(GraphTypes.CHART_BAR);
      margin.setRTChartType(GraphTypes.CHART_LINE);
      refs.add(cat);
      refs.add(revenue);
      refs.add(margin);

      return generator(cinfo, mark, rows(data, refs.toArray(new ChartRef[0])));
   }

   // only a Margin line, on the secondary axis
   private static GraphGenerator secondaryOnly(VizMark mark) {
      VSChartInfo cinfo = new DefaultVSChartInfo();
      cinfo.setChartType(GraphTypes.CHART_LINE);
      cinfo.setRTChartType(GraphTypes.CHART_LINE);
      cinfo.setSeparatedGraph(false);
      VSChartDimensionRef cat = dim("DCat");
      VSChartAggregateRef margin = measure("Margin");
      margin.setSecondaryY(true);
      cinfo.addXField(cat);
      cinfo.addYField(margin);
      cinfo.updateChartType(false);
      Object[][] data = new Object[DUAL.length][];

      for(int i = 0; i < DUAL.length; i++) {
         data[i] = new Object[] { DUAL[i][0], DUAL[i][2] };
      }

      return generator(cinfo, mark, rows(data, cat, margin));
   }

   private static GraphGenerator generator(VSChartInfo cinfo, VizMark mark, Object[][] rows) {
      ChartVSAssemblyInfo assemblyInfo = new ChartVSAssemblyInfo();
      assemblyInfo.setVSChartInfo(cinfo);
      assemblyInfo.setChartDescriptor(new ChartDescriptor());
      assemblyInfo.setVizMark(mark);
      // the seed a chart is created with
      VizModernizeUtil.reseedAfterRestore(assemblyInfo);

      return GraphGenerator.getGenerator(
         assemblyInfo, null, new DefaultDataSet(rows), new VariableTable(), null,
         XSourceInfo.NONE, null);
   }

   // the data under a header of the binding's column names
   private static Object[][] rows(Object[][] data, ChartRef... refs) {
      Object[][] rows = new Object[data.length + 1][];
      rows[0] = new Object[refs.length];

      for(int i = 0; i < refs.length; i++) {
         rows[0][i] = GraphUtil.getName(refs[i]);
      }

      System.arraycopy(data, 0, rows, 1, data.length);
      return rows;
   }

   private static VSChartDimensionRef dim(String name) {
      VSChartDimensionRef dim = new VSChartDimensionRef(new BaseField(name));
      dim.setGroupColumnValue(name);
      return dim;
   }

   private static VSChartAggregateRef measure(String name) {
      VSChartAggregateRef agg = new VSChartAggregateRef();
      BaseField field = new BaseField(name);
      field.setDataType(XSchema.DOUBLE);
      agg.setDataRef(field);
      agg.setFormulaValue("Sum");
      return agg;
   }

   // the total is not a value tick
   private static final Object[][] PARETO_8 = {
      { "Scratch", 120.0 }, { "Dent", 85.0 }, { "Misalignment", 60.0 }, { "Crack", 38.0 },
      { "Discolour", 22.0 }, { "Burr", 14.0 }, { "Warp", 9.0 }, { "Other", 6.0 } };
   // the total is a value tick, at the plot top
   private static final Object[][] PARETO_ROUND = {
      { "A", 200.0 }, { "B", 100.0 }, { "C", 60.0 }, { "D", 40.0 } };
   private static final Object[][] DUAL = {
      { "A", 120.0, 0.12 }, { "B", 340.0, 0.27 }, { "C", 260.0, 0.18 }, { "D", 410.0, 0.33 },
      { "E", 180.0, 0.09 }, { "F", 290.0, 0.21 } };
   // both axes cross zero
   private static final Object[][] DUAL_NEGATIVE = {
      { "A", -100.0, -0.1 }, { "B", 340.0, 0.27 }, { "C", 260.0, 0.18 }, { "D", 400.0, 0.3 },
      { "E", 180.0, 0.09 }, { "F", 290.0, 0.21 } };
   private static final Object[][] DUAL_FACET = {
      { "East", "A", 120.0, 0.12 }, { "East", "B", 340.0, 0.27 }, { "East", "C", 260.0, 0.18 },
      { "West", "A", 410.0, 0.33 }, { "West", "B", 180.0, 0.09 }, { "West", "C", 290.0, 0.21 } };
}
