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
import inetsoft.graph.guide.axis.GridLine;
import inetsoft.graph.VGraph;
import inetsoft.graph.Plotter;
import inetsoft.graph.guide.axis.DefaultAxis;
import inetsoft.graph.internal.ICoordinate;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.coord.FacetCoord;
import inetsoft.graph.coord.RectCoord;
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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The zero line GraphGenerator turns on for a value axis under the modern mark.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ModernZeroLineTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void typesFollowTheFamilyPages() {
      int[] on = { GraphTypes.CHART_BAR, GraphTypes.CHART_BAR_STACK, GraphTypes.CHART_INTERVAL,
                   GraphTypes.CHART_LINE, GraphTypes.CHART_STEP, GraphTypes.CHART_AREA,
                   GraphTypes.CHART_STEP_AREA, GraphTypes.CHART_POINT,
                   GraphTypes.CHART_SCATTER_CONTOUR, GraphTypes.CHART_WATERFALL,
                   GraphTypes.CHART_PARETO, GraphTypes.CHART_CANDLE, GraphTypes.CHART_STOCK,
                   GraphTypes.CHART_BOXPLOT };
      int[] off = { GraphTypes.CHART_3D_BAR, GraphTypes.CHART_GANTT, GraphTypes.CHART_FUNNEL,
                    GraphTypes.CHART_MEKKO, GraphTypes.CHART_RADAR, GraphTypes.CHART_PIE,
                    GraphTypes.CHART_TREEMAP, GraphTypes.CHART_TREE, GraphTypes.CHART_MAP };

      for(int type : on) {
         assertTrue(ModernZeroLine.appliesTo(type), "type " + type);
      }

      for(int type : off) {
         assertFalse(ModernZeroLine.appliesTo(type), "type " + type);
      }
   }

   @Test
   void aMarkedBarGetsTheAxisLineColour() {
      Built built = build(GraphTypes.CHART_BAR, VizMark.MODERN_LIGHT, false, dim("State"),
                          measure("Quantity"));
      AxisSpec y = built.rect().getYScale().getAxisSpec();

      assertEquals(new Color(0x85827A), y.getZeroLineColor());
      assertFalse(y.isZeroLineCrossing());

      built = build(GraphTypes.CHART_BAR, VizMark.MODERN_DARK, false, dim("State"),
                    measure("Quantity"));
      assertEquals(new Color(0x7E7B86), built.rect().getYScale().getAxisSpec().getZeroLineColor());
   }

   @Test
   void anAuthorsAxisColourReachesIt() {
      Built built = build(GraphTypes.CHART_BAR, VizMark.MODERN_LIGHT, false,
                          c -> c.getAxisDescriptor().setLineColor(Color.RED), dim("State"),
                          measure("Quantity"));

      assertEquals(Color.RED, built.rect().getYScale().getAxisSpec().getZeroLineColor());
   }

   @Test
   void theXOfAScatterNeedsBothAxes() {
      Built built = build(GraphTypes.CHART_POINT, VizMark.MODERN_LIGHT, false,
                          measure("Quantity"), measure("Price"));
      AxisSpec x = built.rect().getXScale().getAxisSpec();
      AxisSpec y = built.rect().getYScale().getAxisSpec();

      assertNotNull(x.getZeroLineColor());
      assertTrue(x.isZeroLineCrossing());
      assertNotNull(y.getZeroLineColor());
      assertFalse(y.isZeroLineCrossing());
   }

   @Test
   void onlyThePrimaryValueAxisGetsIt() {
      VSChartAggregateRef second = measure("Price");
      second.setSecondaryY(true);
      Built built = build(GraphTypes.CHART_BAR, VizMark.MODERN_LIGHT, false, dim("State"),
                          measure("Quantity"), second);
      RectCoord coord = built.rect();

      assertNotNull(coord.getYScale().getAxisSpec().getZeroLineColor());
      assertNotNull(coord.getYScale2(), "premise: a secondary scale");
      assertNull(coord.getYScale2().getAxisSpec().getZeroLineColor());
   }

   @Test
   void aMultiStyleChartUsesTheMeasuresType() {
      VSChartAggregateRef line = measure("Quantity");
      line.setChartType(GraphTypes.CHART_LINE);
      Built built = build(GraphTypes.CHART_3D_BAR, VizMark.MODERN_LIGHT, true, dim("State"),
                          line);

      assertFalse(ModernZeroLine.appliesTo(built.cinfo.getRTChartType()),
                  "premise: the chart's own type has no zero line");
      assertNotNull(built.rect().getYScale().getAxisSpec().getZeroLineColor());
   }

   @Test
   void classicChartsGetNone() {
      Built built = build(GraphTypes.CHART_BAR, null, false, dim("State"), measure("Quantity"));
      assertNull(built.rect().getYScale().getAxisSpec().getZeroLineColor());

      built = build(GraphTypes.CHART_POINT, null, false, measure("Quantity"), measure("Price"));
      assertNull(built.rect().getXScale().getAxisSpec().getZeroLineColor());
      assertNull(built.rect().getYScale().getAxisSpec().getZeroLineColor());
      assertFalse(built.rect().getXScale().getAxisSpec().isZeroLineCrossing());
   }

   @Test
   void aMarkedHorizontalBarDrawsItVertically() {
      for(int type : new int[] { GraphTypes.CHART_BAR, GraphTypes.CHART_LINE }) {
         VSChartInfo cinfo = new DefaultVSChartInfo();
         cinfo.setChartType(type);
         cinfo.setRTChartType(type);
         cinfo.setSeparatedGraph(false);
         VSChartAggregateRef qty = measure("Quantity");
         VSChartDimensionRef state = dim("State");
         cinfo.addXField(qty);
         cinfo.addYField(state);
         cinfo.updateChartType(false);
         Object[][] rows = { { GraphUtil.getName(qty), "State" }, { -50.0, "A" },
                             { 100.0, "B" }, { 25.0, "C" } };
         ChartVSAssemblyInfo assemblyInfo = new ChartVSAssemblyInfo();
         assemblyInfo.setVSChartInfo(cinfo);
         assemblyInfo.setChartDescriptor(new ChartDescriptor());
         assemblyInfo.setVizMark(VizMark.MODERN_LIGHT);
         EGraph graph = GraphGenerator.getGenerator(
            assemblyInfo, null, new DefaultDataSet(rows), new VariableTable(), null,
            XSourceInfo.NONE, new Dimension(400, 300)).createEGraph();
         assertTrue(cinfo.isInvertedGraph(), "premise: a horizontal chart");
         VGraph vgraph = Plotter.getPlotter(graph)
            .plotAndLayout(new DefaultDataSet(rows), 0, 0, 400, 300);
         RectCoord coord = (RectCoord) vgraph.getCoordinate();
         GridLine line = null;

         for(int pos : new int[] { ICoordinate.BOTTOM_AXIS, ICoordinate.TOP_AXIS }) {
            DefaultAxis axis = coord.getAxisAt(pos);

            if(axis != null && axis.getZeroLine() != null) {
               line = axis.getZeroLine();
            }
         }

         assertNotNull(line, "type " + type);
         Rectangle2D shape = line.getShape().getBounds2D();
         Rectangle2D plot = vgraph.getPlotBounds();
         assertEquals(0, shape.getWidth(), 0.01);
         assertTrue(shape.getHeight() > 0);
         assertTrue(shape.getX() > plot.getMinX() && shape.getX() < plot.getMaxX());
      }
   }

   private record Built(EGraph graph, VSChartInfo cinfo) {
      RectCoord rect() {
         Coordinate coord = graph.getCoordinate();

         if(coord instanceof FacetCoord facet) {
            coord = facet.getInnerCoordinates()[0];
         }

         assertInstanceOf(RectCoord.class, coord, "premise: a rect coord");
         return (RectCoord) coord;
      }
   }

   // x is the X field; the rest are Y measures
   private static Built build(int type, VizMark mark, boolean multi, ChartRef x,
                              VSChartAggregateRef... rest)
   {
      return build(type, mark, multi, c -> { }, x, rest);
   }

   private static Built build(int type, VizMark mark, boolean multi, Consumer<VSChartInfo> setup,
                              ChartRef x, VSChartAggregateRef... rest)
   {
      VSChartInfo cinfo = new DefaultVSChartInfo();
      cinfo.setChartType(type);
      cinfo.setRTChartType(type);
      cinfo.setSeparatedGraph(false);
      setup.accept(cinfo);
      List<String> names = new ArrayList<>();
      cinfo.addXField(x);
      names.add(GraphUtil.getName(x));

      for(VSChartAggregateRef y : rest) {
         cinfo.addYField(y);
         names.add(GraphUtil.getName(y));
      }

      if(multi) {
         cinfo.setMultiStyles(true);
      }

      cinfo.updateChartType(false);

      // the permission check in updateChartType has no principal here
      if(multi) {
         cinfo.setRTChartType(type);
         for(VSChartAggregateRef y : rest) {
            y.setRTChartType(y.getChartType());
         }
      }

      List<Object[]> rows = new ArrayList<>();
      rows.add(names.toArray());

      for(int i = 0; i < 4; i++) {
         Object[] data = new Object[names.size()];
         Arrays.fill(data, 10.0 * (i + 1));

         if(x instanceof VSChartDimensionRef) {
            data[0] = "S" + i;
         }

         rows.add(data);
      }

      ChartVSAssemblyInfo assemblyInfo = new ChartVSAssemblyInfo();
      assemblyInfo.setVSChartInfo(cinfo);
      assemblyInfo.setChartDescriptor(new ChartDescriptor());
      assemblyInfo.setVizMark(mark);
      EGraph graph = GraphGenerator.getGenerator(
         assemblyInfo, null, new DefaultDataSet(rows.toArray(new Object[0][])),
         new VariableTable(), null, XSourceInfo.NONE, new Dimension(400, 300)).createEGraph();
      return new Built(graph, cinfo);
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
}
