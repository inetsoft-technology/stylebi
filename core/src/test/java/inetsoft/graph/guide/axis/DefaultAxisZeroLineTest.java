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
package inetsoft.graph.guide.axis;

import inetsoft.graph.*;
import inetsoft.graph.coord.*;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.IntervalElement;
import inetsoft.graph.element.PointElement;
import inetsoft.graph.internal.GDefaults;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.internal.ICoordinate;
import inetsoft.graph.scale.*;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.geom.Rectangle2D;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A value axis that crosses zero draws a zero line when the spec has a zero line color.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DefaultAxisZeroLineTest {
   private static final Color ZERO = Color.decode("#85827A");
   private static final int[][] SIZES = { { 400, 200 }, { 1200, 800 } };
   private static int width = 400;
   private static int height = 200;

   @AfterEach
   void resetSize() {
      width = 400;
      height = 200;
   }

   private static void atEachSize(Runnable test) {
      for(int[] size : SIZES) {
         width = size[0];
         height = size[1];
         test.run();
      }
   }

   private static void assertInside(GridLine line, Rectangle2D plot) {
      Rectangle2D painted = line.getAxis().getGridLineTransform()
         .createTransformedShape(line.getShape()).getBounds2D();

      assertTrue(painted.getMinX() >= plot.getMinX() - 1 && painted.getMaxX() <= plot.getMaxX() + 1
                 && painted.getMinY() >= plot.getMinY() - 1
                 && painted.getMaxY() <= plot.getMaxY() + 1,
                 "zero line " + painted + " outside plot " + plot);
      assertTrue(painted.getWidth() > 0 || painted.getHeight() > 0);
   }

   @Test
   void aValueAxisCrossingZeroDrawsTheZeroLine() {
      Setup s = bar(-50, 100, ZERO, GraphConstants.THIN_LINE);
      GridLine line = s.left().getZeroLine();

      assertNotNull(line);
      assertEquals(ZERO, line.getColor());
      assertEquals(GDefaults.AXIS_Z_INDEX, line.getZIndex());
      Rectangle2D shape = line.getShape().getBounds2D();
      Rectangle2D plot = s.vgraph.getPlotBounds();
      assertEquals(0, shape.getHeight(), 0.01);
      assertTrue(shape.getWidth() > 0);
      assertTrue(shape.getY() > plot.getMinY() + 1 && shape.getY() < plot.getMaxY() - 1);
   }

   @Test
   void zeroAtTheEdgeDrawsNone() {
      for(double[] range : new double[][] { { 0, 100 }, { -100, 0 } }) {
         Setup s = bar(range[0], range[1], ZERO, GraphConstants.THIN_LINE);
         Setup classic = bar(range[0], range[1], null, GraphConstants.THIN_LINE);
         assertNull(s.left().getZeroLine());
         assertEquals(classic.grid().getGridLineCount(), s.grid().getGridLineCount());
         assertEquals(countColor(classic.grid(), darkGrid()), countColor(s.grid(), darkGrid()));
      }

   }

   @Test
   void theZeroGridLineIsLeftOut() {
      Setup with = bar(-50, 100, ZERO, GraphConstants.THIN_LINE);
      Setup without = bar(-50, 100, null, GraphConstants.THIN_LINE);

      assertEquals(without.grid().getGridLineCount() - 1, with.grid().getGridLineCount());
      double zeroY = with.left().getZeroLine().getShape().getBounds2D().getY();

      for(int i = 0; i < with.grid().getGridLineCount(); i++) {
         assertNotEquals(zeroY, with.grid().getGridLine(i).getShape().getBounds2D().getY(), 0.5);
      }
   }

   @Test
   void itDrawsWithTheGridOff() {
      atEachSize(() -> {
         Setup s = bar(-50, 100, ZERO, GraphConstants.NONE);

         assertNotNull(s.left().getZeroLine());
         assertTrue(s.grid() == null || s.grid().getGridLineCount() == 0);
         assertEquals(0, s.left().getGridLineCount());
         assertInside(s.left().getZeroLine(), s.vgraph.getPlotBounds());
      });
   }

   @Test
   void itDrawsWithTheValueAxisHidden() {
      atEachSize(() -> {
         LinearScale y = new LinearScale("Quantity");
         y.getAxisSpec().setZeroLineColor(ZERO);
         y.getAxisSpec().setGridStyle(GraphConstants.NONE);
         y.getAxisSpec().setAxisStyle(AxisSpec.AXIS_NONE);
         EGraph graph = new EGraph();
         graph.addElement(new IntervalElement("State", "Quantity"));
         graph.setCoordinate(new RectCoord(new CategoricalScale("State"), y));
         Setup s = layout(graph, rows(-50, 100));
         DefaultAxis axis = s.axisWithZeroLine();

         assertNotNull(axis);
         assertInside(axis.getZeroLine(), s.vgraph.getPlotBounds());
      });
   }

   @Test
   void aLogScaleDrawsNone() {
      EGraph graph = new EGraph();
      graph.addElement(new IntervalElement("State", "Quantity"));
      LogScale y = new LogScale("Quantity");
      y.getAxisSpec().setZeroLineColor(ZERO);
      y.getAxisSpec().setGridStyle(GraphConstants.THIN_LINE);
      graph.setCoordinate(new RectCoord(new CategoricalScale("State"), y));
      Setup s = layout(graph, rows(10, 1000));

      assertNull(s.left().getZeroLine());
   }

   @Test
   void aReversedScaleStillDraws() {
      EGraph graph = new EGraph();
      graph.addElement(new IntervalElement("State", "Quantity"));
      LinearScale y = new LinearScale("Quantity");
      y.setReversed(true);
      y.getAxisSpec().setZeroLineColor(ZERO);
      y.getAxisSpec().setGridStyle(GraphConstants.THIN_LINE);
      graph.setCoordinate(new RectCoord(new CategoricalScale("State"), y));
      Setup s = layout(graph, rows(-50, 100));

      assertNotNull(s.left().getZeroLine());
   }

   @Test
   void crossingRequiresTheOtherAxis() {
      atEachSize(() -> {
         Setup flat = points(-10, 5);
         assertNull(flat.axisWithZeroLine());

         Setup crossing = points(-10, -5);
         GridLine line = crossing.bottom().getZeroLine();
         assertNotNull(line);
         assertEquals(0, line.getShape().getBounds2D().getWidth(), 0.01);
         assertTrue(line.getShape().getBounds2D().getHeight() > 0);
         assertInside(line, crossing.vgraph.getPlotBounds());
      });
   }

   @Test
   void crossingAgainstACategoricalAxisNeedsNoMore() {
      LinearScale x = new LinearScale("Quantity");
      x.getAxisSpec().setZeroLineColor(ZERO);
      x.getAxisSpec().setZeroLineCrossing(true);
      EGraph graph = new EGraph();
      graph.addElement(new PointElement("Quantity", "State"));
      graph.setCoordinate(new RectCoord(x, new CategoricalScale("State")));
      Setup s = layout(graph, rows(-50, 100));
      GridLine line = s.bottom().getZeroLine();

      assertNotNull(line);
      assertEquals(0, line.getShape().getBounds2D().getWidth(), 0.01);
      assertTrue(line.getShape().getBounds2D().getHeight() > 0);
   }

   @Test
   void aHorizontalChartDrawsItVertically() {
      EGraph graph = new EGraph();
      graph.addElement(new IntervalElement("State", "Quantity"));
      RectCoord coord = coord(ZERO, GraphConstants.THIN_LINE);
      coord.transpose();
      graph.setCoordinate(coord);
      Setup s = layout(graph, rows(-50, 100));
      DefaultAxis axis = s.axisWithZeroLine();

      assertNotNull(axis);
      Rectangle2D shape = axis.getZeroLine().getShape().getBounds2D();
      assertEquals(0, shape.getWidth(), 0.01);
      assertTrue(shape.getHeight() > 0);
   }

   @Test
   void eachFacetPanelDrawsItsOwn() {
      atEachSize(DefaultAxisZeroLineTest::facetPanels);
   }

   private static void facetPanels() {
      Object[][] rows = { { "Region", "State", "Quantity" }, { "E", "A", -50.0 },
                          { "E", "B", 100.0 }, { "W", "A", -50.0 }, { "W", "B", 100.0 } };
      RectCoord outer = new RectCoord(new CategoricalScale("Region"), GTool.createFakeScale(null));
      RectCoord inner = coord(ZERO, GraphConstants.THIN_LINE);
      FacetCoord coord = new FacetCoord(outer, inner);
      EGraph graph = new EGraph();
      graph.addElement(new IntervalElement("State", "Quantity"));
      graph.setCoordinate(coord);
      Plotter.getPlotter(graph).plotAndLayout(new DefaultDataSet(rows), 0, 0, width, height);
      Coordinate[][] cells = coord.getExpandedInnerCoords();

      assertEquals(2, cells.length * cells[0].length);

      for(Coordinate[] row : cells) {
         for(Coordinate c : row) {
            GridLine line = ((RectCoord) c).getAxisAt(ICoordinate.LEFT_AXIS).getZeroLine();
            assertNotNull(line);
            assertInside(line, c.getVGraph().getPlotBounds());
         }
      }
   }

   @Test
   void aSeparateSecondaryScaleKeepsItsZeroGridLine() {
      Object[][] rows = { { "State", "Quantity", "Other" }, { "A", -50.0, -10.0 },
                          { "B", 100.0, 10.0 }, { "C", 20.0, 5.0 } };
      RectCoord coord = coord(ZERO, GraphConstants.THIN_LINE);
      LinearScale y2 = new LinearScale("Other");
      y2.getAxisSpec().setGridStyle(GraphConstants.THIN_LINE);
      coord.setYScale2(y2);
      EGraph graph = new EGraph();
      graph.addElement(new IntervalElement("State", "Quantity"));
      graph.addElement(new IntervalElement("State", "Other"));
      graph.setCoordinate(coord);
      Setup s = layout(graph, rows);

      assertNotNull(s.left().getZeroLine());
      assertEquals(1, countColor(s.grid(), darkGrid()));
   }

   @Test
   void classicDrawsNoZeroLine() {
      Setup s = bar(-50, 100, null, GraphConstants.THIN_LINE);

      assertNull(s.left().getZeroLine());
      assertNull(s.grid().getZeroLine());
      assertEquals(1, countColor(s.grid(), darkGrid()));
   }

   private static Color darkGrid() {
      return GTool.darken(GDefaults.DEFAULT_GRIDLINE_COLOR, 0.85);
   }

   private static int countColor(Axis axis, Color color) {
      int count = 0;

      for(int i = 0; i < axis.getGridLineCount(); i++) {
         if(color.equals(axis.getGridLine(i).getColor())) {
            count++;
         }
      }

      return count;
   }

   private static Object[][] rows(double min, double max) {
      return new Object[][] { { "State", "Quantity" }, { "A", min }, { "B", max },
                              { "C", (min + max) / 2 } };
   }

   private static RectCoord coord(Color zero, int grid) {
      LinearScale y = new LinearScale("Quantity");
      y.getAxisSpec().setZeroLineColor(zero);
      y.getAxisSpec().setGridStyle(grid);
      return new RectCoord(new CategoricalScale("State"), y);
   }

   private static Setup bar(double min, double max, Color zero, int grid) {
      EGraph graph = new EGraph();
      graph.addElement(new IntervalElement("State", "Quantity"));
      graph.setCoordinate(coord(zero, grid));
      return layout(graph, rows(min, max));
   }

   private static Setup points(double xmin, double ymin) {
      Object[][] rows = { { "X", "Y" }, { xmin, ymin }, { 10.0, 20.0 }, { 0.0, 15.0 } };
      LinearScale x = new LinearScale("X");
      LinearScale y = new LinearScale("Y");
      x.getAxisSpec().setZeroLineColor(ZERO);
      x.getAxisSpec().setZeroLineCrossing(true);
      EGraph graph = new EGraph();
      graph.addElement(new PointElement("X", "Y"));
      graph.setCoordinate(new RectCoord(x, y));
      return layout(graph, rows);
   }

   private static Setup layout(EGraph graph, Object[][] rows) {
      VGraph vgraph = Plotter.getPlotter(graph).plotAndLayout(new DefaultDataSet(rows),
                                                              0, 0, width, height);
      return new Setup((RectCoord) graph.getCoordinate(), vgraph);
   }

   private static final class Setup {
      Setup(RectCoord coord, VGraph vgraph) {
         this.coord = coord;
         this.vgraph = vgraph;
      }

      DefaultAxis left() {
         return coord.getAxisAt(ICoordinate.LEFT_AXIS);
      }

      DefaultAxis grid() {
         return coord.getAxisAt(ICoordinate.RIGHT_AXIS);
      }

      DefaultAxis bottom() {
         return coord.getAxisAt(ICoordinate.BOTTOM_AXIS);
      }

      DefaultAxis axisWithZeroLine() {
         int[] positions = { ICoordinate.LEFT_AXIS, ICoordinate.BOTTOM_AXIS,
                             ICoordinate.TOP_AXIS, ICoordinate.RIGHT_AXIS };

         for(int pos : positions) {
            DefaultAxis axis = coord.getAxisAt(pos);

            if(axis != null && axis.getZeroLine() != null) {
               return axis;
            }
         }

         return null;
      }

      final RectCoord coord;
      final VGraph vgraph;
   }
}
