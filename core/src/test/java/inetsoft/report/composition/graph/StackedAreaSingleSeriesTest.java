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

import inetsoft.graph.Plotter;
import inetsoft.graph.VGraph;
import inetsoft.graph.Visualizable;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.geometry.AreaGeometry;
import inetsoft.graph.scale.Scale;
import inetsoft.graph.visual.*;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.VariableTable;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.CategoricalColorFrameWrapper;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizMark;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** A stacked area with no series dimension draws like a plain area on a marked chart. */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class StackedAreaSingleSeriesTest {
   private static final String[] MONTHS = { "Jan", "Feb", "Mar", "Apr", "May", "Jun" };
   private static final double[] SALES = { 120, 80, 150, 95, 170, 130 };
   private static final double[] COST = { 60, 70, 40, 85, 90, 55 };
   private static final int WIDTH = 400;
   private static final int HEIGHT = 240;

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void oneMeasureDrawsTheSame() {
      Drawing plain = draw(GraphTypes.CHART_AREA, false, false);
      Drawing stacked = draw(GraphTypes.CHART_AREA_STACK, false, false);

      assertEquals(1, plain.areas.size());
      assertEquals(plain.areas, stacked.areas);
      assertEquals(plain.yRanges, stacked.yRanges);
   }

   @Test
   void twoMeasuresWithoutStackMeasuresDrawTheSame() {
      Drawing plain = draw(GraphTypes.CHART_AREA, true, false);
      Drawing stacked = draw(GraphTypes.CHART_AREA_STACK, true, false);

      assertEquals(2, plain.areas.size());
      assertEquals(plain.areas, stacked.areas);
      assertEquals(plain.yRanges, stacked.yRanges);
   }

   @Test
   void valuesShownDrawTheSame() {
      Drawing plain = draw(GraphTypes.CHART_AREA, false, true);
      Drawing stacked = draw(GraphTypes.CHART_AREA_STACK, false, true);

      assertEquals(MONTHS.length, plain.labels.size());
      assertEquals(plain.labels, stacked.labels);
      assertEquals(plain.areas, stacked.areas);
      assertEquals(plain.yRanges, stacked.yRanges);
   }

   // the fill as painted, which the shapes above do not cover
   @Test
   void oneMeasureFillsTheSame() {
      Drawing plain = draw(GraphTypes.CHART_AREA, false, false);
      Drawing stacked = draw(GraphTypes.CHART_AREA_STACK, false, false);

      assertArrayEquals(plain.pixels, stacked.pixels, () -> pixelDiff(plain, stacked));
   }

   @Test
   void aMarkedStepAreaFillsTheSameStacked() {
      Drawing plain = draw(GraphTypes.CHART_STEP_AREA, false, false);
      Drawing stacked = draw(GraphTypes.CHART_STEP_AREA_STACK, false, false);

      assertEquals(plain.areas, stacked.areas);
      assertArrayEquals(plain.pixels, stacked.pixels, () -> pixelDiff(plain, stacked));
   }

   @Test
   void aClassicStackedAreaStaysOpaque() {
      Drawing stacked = draw(GraphTypes.CHART_AREA_STACK, null, false, false, false);
      Drawing plain = draw(GraphTypes.CHART_AREA, null, false, false, false);

      // no alpha hint paints the fill opaque
      assertEquals(Arrays.asList((Object) null), stacked.alphas);
      assertEquals(List.of(0.8), plain.alphas);
   }

   @Test
   void aMarkedStackedAreaWithAColourDimensionStaysOpaque() {
      Drawing stacked = draw(GraphTypes.CHART_AREA_STACK, VizMark.MODERN_LIGHT, false, false,
                             true);

      assertEquals(2, stacked.alphas.size());
      stacked.alphas.forEach(Assertions::assertNull);
   }

   @Test
   void aMarkedStackedAreaStackingMeasuresStaysOpaque() {
      VSChartDimensionRef month = dim("Month");
      VSChartAggregateRef sales = agg("Sales");
      VSChartAggregateRef cost = agg("Cost");
      VSChartInfo cinfo = new DefaultVSChartInfo();
      cinfo.setChartType(GraphTypes.CHART_AREA_STACK);
      cinfo.setRTChartType(GraphTypes.CHART_AREA_STACK);
      // measures share one plot, as only a combined chart stacks them
      cinfo.setSeparatedGraph(false);
      cinfo.addXField(month);
      cinfo.addYField(sales);
      cinfo.addYField(cost);
      cinfo.updateChartType(false);
      List<Object[]> data = new ArrayList<>();
      data.add(new Object[] { GraphUtil.getName(month), GraphUtil.getName(sales),
                              GraphUtil.getName(cost) });

      for(int i = 0; i < MONTHS.length; i++) {
         data.add(new Object[] { MONTHS[i], SALES[i], COST[i] });
      }

      ChartDescriptor desc = new ChartDescriptor();
      desc.getPlotDescriptor().setStackMeasures(true);
      Drawing drawing = plot(new Chart(cinfo, desc, data.toArray(new Object[0][])),
                             VizMark.MODERN_LIGHT);

      // one element stacks both measures as layers
      assertEquals(2, drawing.alphas.size());
      drawing.alphas.forEach(Assertions::assertNull);
   }

   @Test
   void aMultiStyleAreaFollowsItsOwnColour() {
      VSChartDimensionRef month = dim("Month");
      VSChartDimensionRef region = dim("Region");
      VSChartAggregateRef sales = agg("Sales");
      VSChartAggregateRef cost = agg("Cost");
      VSChartInfo cinfo = new DefaultVSChartInfo();
      cinfo.setMultiStyles(true);
      cinfo.addXField(month);

      for(VSChartAggregateRef measure : new VSChartAggregateRef[] { sales, cost }) {
         measure.setChartType(GraphTypes.CHART_AREA_STACK);
         cinfo.addYField(measure);
      }

      sales.setColorField(colour(region));
      cinfo.updateChartType(false);
      // after the update, which resolves no style without a logged-in user
      sales.setRTChartType(GraphTypes.CHART_AREA_STACK);
      cost.setRTChartType(GraphTypes.CHART_AREA_STACK);
      List<Object[]> data = new ArrayList<>();
      data.add(new Object[] { GraphUtil.getName(month), GraphUtil.getName(region),
                              GraphUtil.getName(sales), GraphUtil.getName(cost) });

      for(int i = 0; i < MONTHS.length; i++) {
         data.add(new Object[] { MONTHS[i], "East", SALES[i], COST[i] });
      }

      ChartDescriptor desc = new ChartDescriptor();
      desc.getPlotDescriptor().setStackMeasures(false);
      Drawing drawing = plot(new Chart(cinfo, desc, data.toArray(new Object[0][])),
                             VizMark.MODERN_LIGHT);

      // the measure split by its own colour stacks opaque; the other keeps the plain fill
      assertEquals(Arrays.asList((Object) null), drawing.alphasOf(GraphUtil.getName(sales)));
      assertEquals(List.of(0.8), drawing.alphasOf(GraphUtil.getName(cost)));
   }

   private static String pixelDiff(Drawing plain, Drawing stacked) {
      int count = 0;
      int first = -1;

      for(int i = 0; i < plain.pixels.length; i++) {
         if(plain.pixels[i] != stacked.pixels[i]) {
            count++;
            first = first < 0 ? i : first;
         }
      }

      return String.format("%d pixels differ, first at (%d,%d): plain %08x, stacked %08x; " +
                              "alpha hints plain %s, stacked %s", count, first % WIDTH,
                           first / WIDTH, plain.pixels[first], stacked.pixels[first],
                           plain.alphas, stacked.alphas);
   }

   private static Drawing draw(int type, boolean twoMeasures, boolean values) {
      return draw(type, VizMark.MODERN_LIGHT, twoMeasures, values, false);
   }

   private static Drawing draw(int type, VizMark mark, boolean twoMeasures, boolean values,
                               boolean colorDimension)
   {
      VSChartDimensionRef month = dim("Month");
      VSChartDimensionRef region = dim("Region");
      VSChartAggregateRef sales = agg("Sales");
      VSChartAggregateRef cost = agg("Cost");
      VSChartInfo cinfo = new DefaultVSChartInfo();
      cinfo.setChartType(type);
      cinfo.setRTChartType(type);
      cinfo.addXField(month);
      cinfo.addYField(sales);

      if(twoMeasures) {
         cinfo.addYField(cost);
      }

      if(colorDimension) {
         cinfo.setColorField(colour(region));
      }

      cinfo.updateChartType(false);
      List<Object[]> data = new ArrayList<>();

      if(colorDimension) {
         data.add(new Object[] { GraphUtil.getName(month), GraphUtil.getName(region),
                                 GraphUtil.getName(sales) });

         for(int i = 0; i < MONTHS.length; i++) {
            data.add(new Object[] { MONTHS[i], "East", SALES[i] });
            data.add(new Object[] { MONTHS[i], "West", COST[i] });
         }
      }
      else if(twoMeasures) {
         data.add(new Object[] { GraphUtil.getName(month), GraphUtil.getName(sales),
                                 GraphUtil.getName(cost) });

         for(int i = 0; i < MONTHS.length; i++) {
            data.add(new Object[] { MONTHS[i], SALES[i], COST[i] });
         }
      }
      else {
         data.add(new Object[] { GraphUtil.getName(month), GraphUtil.getName(sales) });

         for(int i = 0; i < MONTHS.length; i++) {
            data.add(new Object[] { MONTHS[i], SALES[i] });
         }
      }

      Object[][] rows = data.toArray(new Object[0][]);

      ChartDescriptor desc = new ChartDescriptor();
      desc.getPlotDescriptor().setValuesVisible(values);

      if(twoMeasures) {
         desc.getPlotDescriptor().setStackMeasures(false);
      }

      return plot(new Chart(cinfo, desc, rows), mark);
   }

   private static Drawing plot(Chart chart, VizMark mark) {
      GraphGenerator gen = generator(chart, mark, new Dimension(WIDTH, HEIGHT));
      VGraph vgraph = Plotter.getPlotter(gen.createEGraph())
         .plotAndLayout(gen.getData(), 0, 0, WIDTH, HEIGHT);
      Drawing drawing = new Drawing();
      collect(vgraph, drawing);
      drawing.pixels = paintFills(drawing.areaVOs);
      return drawing;
   }

   // the fills only: painting text needs the SVG support missing from core tests
   private static int[] paintFills(List<AreaVO> areas) {
      BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = image.createGraphics();

      try {
         for(AreaVO area : areas) {
            area.paint(g);
         }
      }
      finally {
         g.dispose();
      }

      return image.getRGB(0, 0, WIDTH, HEIGHT, null, 0, WIDTH);
   }

   private static void collect(VGraph vgraph, Drawing drawing) {
      if(vgraph.getCoordinate() instanceof RectCoord coord && coord.getYScale() != null) {
         Scale y = coord.getYScale();
         drawing.yRanges.add(Arrays.toString(y.getFields()) + " " + y.getMin() + ".." +
                                y.getMax());
      }

      for(int i = 0; i < vgraph.getVisualCount(); i++) {
         Visualizable visual = vgraph.getVisual(i);

         if(visual instanceof GraphVO graph) {
            collect(graph.getVGraph(), drawing);
         }
         else if(visual instanceof AreaVO area) {
            drawing.areas.add(describe(area));
            drawing.areaVOs.add(area);
            drawing.alphas.add(area.getHint(GraphElement.HINT_ALPHA));
            drawing.measures.add(area.getMeasureName());
         }

         if(visual instanceof ElementVO elem && elem.getVOTexts() != null) {
            for(VOText text : elem.getVOTexts()) {
               if(text != null) {
                  drawing.labels.add(text.getText() + " @ " + text.getBounds());
               }
            }
         }
      }
   }

   // bounds, data points, base tuples, and each shape's bounds and path
   private static String describe(AreaVO area) {
      StringBuilder sb = new StringBuilder();
      sb.append("bounds ").append(area.getBounds()).append("\npoints");

      for(Point2D pt : area.getTransformedPoints()) {
         sb.append(' ').append(pt);
      }

      AreaGeometry geometry = (AreaGeometry) area.getGeometry();
      sb.append("\nbase");

      for(int i = 0; i < geometry.getTupleCount(); i++) {
         sb.append(' ').append(Arrays.toString(geometry.getBaseTuple(i)));
      }

      for(Shape shape : area.getShapes()) {
         sb.append("\nshape ").append(shape.getBounds2D()).append(" path");
         double[] coords = new double[6];

         for(PathIterator it = shape.getPathIterator(null); !it.isDone(); it.next()) {
            int seg = it.currentSegment(coords);
            sb.append(' ').append(seg).append(Arrays.toString(coords));
         }
      }

      return sb.toString();
   }

   private static final class Drawing {
      final List<String> areas = new ArrayList<>();
      final List<AreaVO> areaVOs = new ArrayList<>();
      final List<Object> alphas = new ArrayList<>();
      final List<String> measures = new ArrayList<>();
      final List<String> yRanges = new ArrayList<>();
      final List<String> labels = new ArrayList<>();
      int[] pixels;

      List<Object> alphasOf(String measure) {
         List<Object> list = new ArrayList<>();

         for(int i = 0; i < measures.size(); i++) {
            if(measure.equals(measures.get(i))) {
               list.add(alphas.get(i));
            }
         }

         return list;
      }
   }

   private record Chart(VSChartInfo info, ChartDescriptor desc, Object[][] rows) {
   }

   private static GraphGenerator generator(Chart chart, VizMark mark, Dimension size) {
      ChartVSAssemblyInfo assemblyInfo = new ChartVSAssemblyInfo();
      assemblyInfo.setVSChartInfo(chart.info());
      assemblyInfo.setChartDescriptor(chart.desc());
      assemblyInfo.setVizMark(mark);

      return GraphGenerator.getGenerator(assemblyInfo, null, new DefaultDataSet(chart.rows()),
                                         new VariableTable(), null, XSourceInfo.NONE, size);
   }

   private static VSAestheticRef colour(VSChartDimensionRef dim) {
      VSAestheticRef color = new VSAestheticRef();
      color.setDataRef(dim);
      color.setRTDataRef(dim);
      color.setVisualFrameWrapper(new CategoricalColorFrameWrapper());
      return color;
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
