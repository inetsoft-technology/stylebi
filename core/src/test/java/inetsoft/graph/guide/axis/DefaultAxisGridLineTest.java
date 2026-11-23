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

import inetsoft.graph.GraphConstants;
import inetsoft.graph.VGraph;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.scale.Scale;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Grid line creation of DefaultAxis (createGridLines, facet grid extension, crowded line
 * hiding) and the grid line bookkeeping of Axis.
 *
 * Fixture: X1 is a horizontal axis of length 1000 (identity transform), X2 is a second axis on
 * the same scale shifted by translate(0, 1000); the lines are created by X2.createGridLines(X1).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class DefaultAxisGridLineTest {
   private static final double L = 1000;
   private static final double EPS = 1e-6;

   private RectCoord coord;
   private VGraph vg;

   @BeforeEach
   void setUp() {
      coord = new RectCoord();
      vg = new VGraph(coord);
   }

   private static LinearScale lin(double min, double max, double inc) {
      LinearScale scale = new LinearScale("m");
      scale.setMin(min);
      scale.setMax(max);
      scale.setIncrement(inc);
      return scale;
   }

   private static LinearScale lin() {
      return lin(0, 100, 25);
   }

   private static CategoricalScale cat3() {
      CategoricalScale scale = new CategoricalScale("Cat");
      scale.init("A", "B", "C");
      return scale;
   }

   private DefaultAxis axis(Scale scale, AffineTransform transform, double length) {
      DefaultAxis axis = new DefaultAxis(scale, vg);
      axis.setLength(length);
      axis.setScreenTransform(transform);
      return axis;
   }

   private DefaultAxis x1(Scale scale) {
      return axis(scale, new AffineTransform(), L);
   }

   private DefaultAxis x2(Scale scale) {
      return axis(scale, AffineTransform.getTranslateInstance(0, 1000), L);
   }

   private static List<Line2D> shapes(DefaultAxis axis) {
      List<Line2D> result = new ArrayList<>();

      for(int i = 0; i < axis.getGridLineCount(); i++) {
         result.add((Line2D) axis.getGridLine(i).getShape());
      }

      return result;
   }

   private static void assertVerticalLines(List<Line2D> lines, double... xs) {
      assertEquals(xs.length, lines.size());

      for(int i = 0; i < xs.length; i++) {
         assertEquals(xs[i], lines.get(i).getX1(), EPS, "line " + i + " x1");
         assertEquals(1000, lines.get(i).getY1(), EPS, "line " + i + " y1");
         assertEquals(xs[i], lines.get(i).getX2(), EPS, "line " + i + " x2");
         assertEquals(0, lines.get(i).getY2(), EPS, "line " + i + " y2");
      }
   }

   private long gridLinesInGraph() {
      return vg.stream().filter(v -> v instanceof GridLine).count();
   }

   private static void style(Scale scale) {
      scale.getAxisSpec().setGridStyle(GraphConstants.THIN_LINE);
   }

   @Test
   @DisplayName("no grid style creates no lines and reports false")
   void noStyleNoLines() {
      Scale scale = lin();
      DefaultAxis x1 = x1(scale);
      DefaultAxis x2 = x2(scale);

      assertFalse(x2.createGridLines(x1, true));
      assertEquals(0, x2.getGridLineCount());
   }

   @Test
   @DisplayName("linear scale gives one vertical line per tick between the two axes")
   void linearGridLines() {
      Scale scale = lin();
      style(scale);
      DefaultAxis x1 = x1(scale);
      DefaultAxis x2 = x2(scale);

      assertTrue(x2.createGridLines(x1, true));
      assertVerticalLines(shapes(x2), 0, 250, 500, 750, 1000);
   }

   @Test
   @DisplayName("grid lines are registered with the graph and know their axis")
   void linesRegistered() {
      Scale scale = lin();
      style(scale);
      DefaultAxis x1 = x1(scale);
      DefaultAxis x2 = x2(scale);

      x2.createGridLines(x1, true);

      assertEquals(5, gridLinesInGraph());

      for(int i = 0; i < 5; i++) {
         GridLine line = x2.getGridLine(i);
         assertSame(x2, line.getAxis());
         assertTrue(vg.stream().anyMatch(v -> v == line));
      }
   }

   @Test
   @DisplayName("grid between ticks puts a line in the middle of each interval")
   void gridBetween() {
      Scale scale = lin();
      style(scale);
      scale.getAxisSpec().setGridBetween(true);
      DefaultAxis x1 = x1(scale);
      DefaultAxis x2 = x2(scale);

      x2.createGridLines(x1, true);

      assertVerticalLines(shapes(x2), 125, 375, 625, 875);
   }

   @Test
   @DisplayName("categorical grid lines go through the cell centers")
   void categoricalGrid() {
      Scale scale = cat3();
      style(scale);
      DefaultAxis x1 = x1(scale);
      DefaultAxis x2 = x2(scale);

      x2.createGridLines(x1, true);

      assertVerticalLines(shapes(x2), L / 6, L / 2, 5 * L / 6);
   }

   @Test
   @DisplayName("categorical grid between puts the lines on the cell boundaries")
   void categoricalGridBetween() {
      Scale scale = cat3();
      style(scale);
      scale.getAxisSpec().setGridBetween(true);
      DefaultAxis x1 = x1(scale);
      DefaultAxis x2 = x2(scale);

      x2.createGridLines(x1, true);

      assertVerticalLines(shapes(x2), L / 3, 2 * L / 3);
   }

   @Test
   @DisplayName("descending grid pairs tick i of this axis with tick n-1-i of the other axis")
   void descendingGrid() {
      Scale scale = lin();
      style(scale);
      DefaultAxis x1 = x1(scale);
      DefaultAxis x2 = x2(scale);

      assertTrue(x2.createGridLines(x1, false));

      List<Line2D> lines = shapes(x2);
      assertEquals(5, lines.size());

      for(int i = 0; i < 5; i++) {
         assertEquals(250 * i, lines.get(i).getX1(), EPS);
         assertEquals(1000, lines.get(i).getY1(), EPS);
         assertEquals(250 * (4 - i), lines.get(i).getX2(), EPS);
         assertEquals(0, lines.get(i).getY2(), EPS);
      }
   }

   @Test
   @DisplayName("Axis2D and DefaultAxis pair the ticks the same way for descending grid lines")
   void descendingGridMatchesAxis2D() {
      Scale scale = lin();
      style(scale);
      DefaultAxis x1 = x1(scale);
      DefaultAxis x2 = x2(scale);
      x2.createGridLines(x1, false);

      Scale scale2 = lin();
      style(scale2);
      Axis2D a1 = new Axis2D(scale2, vg);
      a1.setLength(L);
      a1.setScreenTransform(new AffineTransform());
      Axis2D a2 = new Axis2D(scale2, vg);
      a2.setLength(L);
      a2.setScreenTransform(AffineTransform.getTranslateInstance(0, 1000));
      assertTrue(a2.createGridLines(a1, false));

      List<Line2D> expected = shapes(x2);
      List<Line2D> actual = shapes(a2);
      double depth = a2.getDepth();

      assertEquals(expected.size(), actual.size());

      for(int i = 0; i < expected.size(); i++) {
         assertEquals(expected.get(i).getX1(), actual.get(i).getX1() - depth, EPS);
         assertEquals(expected.get(i).getY1(), actual.get(i).getY1() - depth, EPS);
         assertEquals(expected.get(i).getX2(), actual.get(i).getX2() - depth, EPS);
         assertEquals(expected.get(i).getY2(), actual.get(i).getY2() - depth, EPS);
      }
   }

   @Test
   @DisplayName("the line at value zero is darker than the other lines of the same grid")
   void zeroLineDarker() {
      Scale scale = lin(-50, 50, 25);
      style(scale);
      Color base = scale.getAxisSpec().getGridColor();
      DefaultAxis x1 = x1(scale);
      DefaultAxis x2 = x2(scale);

      x2.createGridLines(x1, true);

      assertEquals(5, x2.getGridLineCount());

      for(int i = 0; i < 5; i++) {
         Color color = x2.getGridLine(i).getColor();

         if(i == 2) {
            assertTrue(color.getRed() < base.getRed(), "red " + color);
            assertTrue(color.getGreen() < base.getGreen(), "green " + color);
            assertTrue(color.getBlue() < base.getBlue(), "blue " + color);
         }
         else {
            assertEquals(base, color, "line " + i);
         }
      }
   }

   @Test
   @DisplayName("with only three ticks the zero line keeps the grid color")
   void zeroLineNotDarkenedWithThreeTicks() {
      Scale scale = lin(-50, 50, 50);
      style(scale);
      Color base = scale.getAxisSpec().getGridColor();
      DefaultAxis x1 = x1(scale);
      DefaultAxis x2 = x2(scale);

      x2.createGridLines(x1, true);

      assertEquals(3, x2.getGridLineCount());

      for(int i = 0; i < 3; i++) {
         assertEquals(base, x2.getGridLine(i).getColor(), "line " + i);
      }
   }

   @Test
   @DisplayName("grid on top uses the top z index, otherwise the grid z index")
   void gridZIndex() {
      for(boolean top : new boolean[] { true, false }) {
         Scale scale = lin();
         style(scale);
         scale.getAxisSpec().setGridOnTop(top);
         DefaultAxis x1 = x1(scale);
         DefaultAxis x2 = x2(scale);

         x2.createGridLines(x1, true);

         for(int i = 0; i < x2.getGridLineCount(); i++) {
            assertEquals(top ? 80 : 30, x2.getGridLine(i).getZIndex(), "top=" + top);
         }
      }
   }

   @Test
   @DisplayName("grid lines take color and style from the axis spec")
   void colorAndStyle() {
      Scale scale = lin();
      style(scale);
      scale.getAxisSpec().setGridColor(Color.RED);
      DefaultAxis x1 = x1(scale);
      DefaultAxis x2 = x2(scale);

      x2.createGridLines(x1, true);

      assertEquals(5, x2.getGridLineCount());

      for(int i = 0; i < 5; i++) {
         assertEquals(Color.RED, x2.getGridLine(i).getColor());
         assertEquals(GraphConstants.THIN_LINE, x2.getGridLine(i).getStyle());
      }
   }

   @Test
   @DisplayName("creating the grid lines again replaces the old ones")
   void recreateReplacesLines() {
      Scale scale = lin();
      style(scale);
      DefaultAxis x1 = x1(scale);
      DefaultAxis x2 = x2(scale);

      x2.createGridLines(x1, true);
      x2.createGridLines(x1, true);

      assertEquals(5, x2.getGridLineCount());
      assertEquals(5, gridLinesInGraph());
   }

   @Test
   @DisplayName("a line closer than four pixels to the last visible line is hidden")
   void closeLinesHidden() {
      Scale scale = lin();
      style(scale);
      DefaultAxis x1 = axis(scale, new AffineTransform(), 8);
      DefaultAxis x2 = axis(scale, AffineTransform.getTranslateInstance(0, 1000), 8);

      x2.createGridLines(x1, true);

      assertEquals(5, x2.getGridLineCount());
      int[] expected = { 30, -1, 30, -1, 30 };

      for(int i = 0; i < 5; i++) {
         assertEquals(expected[i], x2.getGridLine(i).getZIndex(), "line " + i);
      }
   }

   @Test
   @DisplayName("facet grid lines are extended across the label area of the axis")
   void facetGridExtendsOverLabels() {
      Scale scale = lin();
      style(scale);
      scale.getAxisSpec().setFacetGrid(true);
      DefaultAxis x1 = x1(scale);
      DefaultAxis x2 = x2(scale);
      x2.setAxisSize(40);
      x2.createGridLines(x1, true);

      x2.layout(new Rectangle2D.Double(0, -200, L, 1400));

      assertEquals(5, x2.getGridLineCount());

      for(int i = 0; i < 5; i++) {
         GridLine line = x2.getGridLine(i);
         Rectangle2D bounds = line.getShape().getBounds2D();

         assertTrue(line.isFacetGrid());
         assertEquals(1034, bounds.getHeight(), EPS, "line " + i);
         assertEquals(-34, bounds.getMinY(), EPS, "line " + i);
         assertEquals(1000, bounds.getMaxY(), EPS, "line " + i);
      }
   }

   @Test
   @DisplayName("laying out twice extends the facet grid lines only once")
   void facetGridExtendedOnce() {
      Scale scale = lin();
      style(scale);
      scale.getAxisSpec().setFacetGrid(true);
      DefaultAxis x1 = x1(scale);
      DefaultAxis x2 = x2(scale);
      x2.setAxisSize(40);
      x2.createGridLines(x1, true);
      Rectangle2D outer = new Rectangle2D.Double(0, -200, L, 1400);

      x2.layout(outer);
      x2.layout(outer);

      for(int i = 0; i < x2.getGridLineCount(); i++) {
         assertEquals(1034, x2.getGridLine(i).getShape().getBounds2D().getHeight(), EPS);
      }
   }

   @Test
   @DisplayName("a secondary axis without labels extends its facet grid lines by the primary axis size")
   void facetGridSecondaryAxis() {
      Scale scale = lin();
      style(scale);
      scale.getAxisSpec().setFacetGrid(true);
      DefaultAxis x1 = x1(scale);
      x1.setAxisSize(40);
      DefaultAxis x2 = x2(scale);
      x2.setLabelVisible(false);
      x2.setPrimaryAxis(x1);
      x2.createGridLines(x1, true);

      x2.layout(new Rectangle2D.Double(0, -200, L, 1400));

      assertEquals(5, x2.getGridLineCount());

      for(int i = 0; i < 5; i++) {
         Rectangle2D bounds = x2.getGridLine(i).getShape().getBounds2D();

         assertTrue(x2.getGridLine(i).isFacetGrid());
         assertEquals(1040, bounds.getHeight(), EPS, "line " + i);
         assertEquals(-40, bounds.getMinY(), EPS, "line " + i);
      }
   }

   @Test
   @DisplayName("a vertical axis extends its facet grid lines to the left by the label width")
   void facetGridVertical() {
      Scale scale = lin();
      style(scale);
      scale.getAxisSpec().setFacetGrid(true);
      // (x,y)->(y,x): vertical axis at x = 0, labels on the left; partner at x = 1000
      DefaultAxis left = axis(scale, new AffineTransform(0, 1, 1, 0, 0, 0), L);
      DefaultAxis right = axis(scale, new AffineTransform(0, 1, 1, 0, 1000, 0), L);
      left.setAxisSize(60);
      left.createGridLines(right, true);

      left.layout(new Rectangle2D.Double(-200, 0, 1400, L));

      assertEquals(5, left.getGridLineCount());

      for(int i = 0; i < 5; i++) {
         Rectangle2D bounds = left.getGridLine(i).getShape().getBounds2D();

         assertTrue(left.getGridLine(i).isFacetGrid());
         // axis size 60 minus the label gap (2) and the axis line (5)
         assertEquals(1053, bounds.getWidth(), EPS, "line " + i);
         assertEquals(-53, bounds.getMinX(), EPS, "line " + i);
         assertEquals(1000, bounds.getMaxX(), EPS, "line " + i);
      }
   }

   @Test
   @DisplayName("grid lines of a y axis continue at tick spacing up to the trend line maximum")
   void trendLineProjectsGridLines() {
      Scale scale = lin();
      style(scale);
      DefaultAxis x1 = x1(scale);
      DefaultAxis x2 = x2(scale);
      x1.setAxisType("y");
      x2.setAxisType("y");
      x2.setCoordinate(coord);
      coord.setTrendLineMax(1600);

      x2.createGridLines(x1, true);

      List<Line2D> lines = shapes(x2);
      assertTrue(lines.size() > 5, "no projected lines");

      for(int i = 0; i < lines.size(); i++) {
         assertEquals(250 * i, lines.get(i).getX1(), EPS, "line " + i);
         assertTrue(lines.get(i).getX1() <= 1600, "line " + i + " beyond the trend line max");
      }
   }

   @Test
   @DisplayName("grid lines are kept in order and removing one removes it from the graph")
   void addAndRemoveGridLines() {
      Scale scale = lin();
      DefaultAxis x2 = x2(scale);
      GridLine l0 = new GridLine(new Line2D.Double(0, 0, 0, 10), x2, 30);
      GridLine l1 = new GridLine(new Line2D.Double(100, 0, 100, 10), x2, 30);
      GridLine l2 = new GridLine(new Line2D.Double(200, 0, 200, 10), x2, 30);

      x2.addGridLine(l0);
      x2.addGridLine(l1);
      x2.addGridLine(l2);

      assertEquals(3, x2.getGridLineCount());
      assertSame(l0, x2.getGridLine(0));
      assertSame(l1, x2.getGridLine(1));
      assertSame(l2, x2.getGridLine(2));

      x2.removeGridLine(1);

      assertEquals(2, x2.getGridLineCount());
      assertSame(l0, x2.getGridLine(0));
      assertSame(l2, x2.getGridLine(1));
      assertTrue(vg.stream().noneMatch(v -> v == l1));
      assertTrue(vg.stream().anyMatch(v -> v == l0));
      assertTrue(vg.stream().anyMatch(v -> v == l2));
   }
}
