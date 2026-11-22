/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.graph.element;

import inetsoft.graph.EGraph;
import inetsoft.graph.GGraph;
import inetsoft.graph.aesthetic.LinearSizeFrame;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.geometry.PointGeometry;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.scale.Scale;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the geometry that PointElement.createGeometry() builds: stacking by value, the stack
 * key by sign and group, null skipping, the cap on over-crowded points, row order and the
 * element's equalsContent.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class PointElementGeometryTest {
   private static final double D = 1e-9;
   private static final double NAN = Double.NaN;

   @Test
   @DisplayName("unstacked points: one geometry per row and measure, raw y, x at the category")
   void unstackedPoints() {
      DefaultDataSet ds = fixAData();
      PointElement elem = fixA();
      elem.setCollisionModifier(GraphElement.MOVE_NONE);
      GGraph g = plot(elem, ds, categories(ds), lin(0, 100, "m1", "m2"));

      assertArrayEquals(new double[] { 10, 20, 30, 40 }, ys(g), D);
      assertArrayEquals(new double[] { 0.5, 0.5, 1.5, 1.5 }, xs(g), D);
   }

   @Test
   @DisplayName("a null measure is skipped: the point is absent instead of a gap")
   void nullMeasureSkipped() {
      DefaultDataSet ds = new DefaultDataSet(new Object[][] {
         { "Cat", "y" }, { "A", 1.0 }, { "B", null }, { "C", 3.0 } });
      GGraph g = plot(new PointElement("Cat", "y"), ds, categories(ds), lin(0, 10, "y"));

      assertEquals(2, g.getGeometryCount());
      assertEquals(0, point(g, 0).getRowIndex());
      assertEquals(2, point(g, 1).getRowIndex());
   }

   // ---------------------------------------------------------------- stack by value

   @Test
   @DisplayName("stack by value, grouped: values stack per x, the order is row by measure")
   void stackByValuePerX() {
      DefaultDataSet ds = fixAData();
      GGraph g = plot(stackValue(fixA(), true), ds, categories(ds), lin(0, 100, "m1", "m2"));

      assertArrayEquals(new double[] { 10, 30, 30, 70 }, ys(g), D);
   }

   @Test
   @DisplayName("stack by value, not grouped: values accumulate across x")
   void stackByValueAcrossX() {
      DefaultDataSet ds = fixAData();
      GGraph g = plot(stackValue(fixA(), false), ds, categories(ds), lin(0, 200, "m1", "m2"));

      assertArrayEquals(new double[] { 10, 30, 60, 100 }, ys(g), D);
   }

   @Test
   @DisplayName("stack by value: negative values stack apart from positive ones")
   void negativeStacksApart() {
      GGraph g = plotNeg(true);

      assertArrayEquals(new double[] { 10, -5, 30, -3, 4, -9 }, ys(g), D);
   }

   @Test
   @DisplayName("stack by value with stackNegative false: one stack for all values")
   void stackNegativeFalse() {
      GGraph g = plotNeg(false);

      assertArrayEquals(new double[] { 10, 5, 25, -3, 1, -5 }, ys(g), D);
   }

   @Test
   @DisplayName("stack by value: a zero joins the positive stack")
   void zeroJoinsPositiveStack() {
      DefaultDataSet ds = new DefaultDataSet(new Object[][] {
         { "Cat", "m1", "m2", "m3" }, { "A", 5.0, 0.0, 3.0 }, { "B", 0.0, 7.0, 1.0 } });
      PointElement elem = stackValue(new PointElement(), true);
      elem.addDim("Cat");
      elem.addVar("m1");
      elem.addVar("m2");
      elem.addVar("m3");
      GGraph g = plot(elem, ds, categories(ds), lin(-20, 100, "m1", "m2", "m3"));

      assertArrayEquals(new double[] { 5, 5, 8, 0, 7, 8 }, ys(g), D);
   }

   @Test
   @DisplayName("stack by value: a null is skipped and the stack continues above it")
   void nullSkippedInsideStack() {
      DefaultDataSet ds = new DefaultDataSet(new Object[][] {
         { "Cat", "m1", "m2", "m3" }, { "A", 10.0, null, 5.0 } });
      PointElement elem = stackValue(new PointElement(), true);
      elem.addDim("Cat");
      elem.addVar("m1");
      elem.addVar("m2");
      elem.addVar("m3");
      GGraph g = plot(elem, ds, categories(ds), lin(0, 100, "m1", "m2", "m3"));

      assertEquals(2, g.getGeometryCount());
      assertArrayEquals(new double[] { 10, 15 }, ys(g), D);
      assertEquals(1, point(g, 0).getColIndex());
      assertEquals(3, point(g, 1).getColIndex());
   }

   @Test
   @DisplayName("stack modifier alone stacks the values (the stacked dot plot), as stackValue does")
   void stackModifierAloneStacksValues() {
      DefaultDataSet ds = fixAData();
      PointElement elem = fixA();
      elem.setCollisionModifier(GraphElement.MOVE_STACK);
      elem.setStackGroup(true); // as GraphGenerator does for stacked points
      GGraph g = plot(elem, ds, categories(ds), lin(0, 100, "m1", "m2"));

      assertArrayEquals(new double[] { 10, 30, 30, 70 }, ys(g), D);
   }

   @Test
   @DisplayName("stack modifier without value stacking plots raw values and is not stacked")
   void stackModifierWithoutValueStacking() {
      DefaultDataSet ds = fixAData();
      PointElement elem = fixA();
      elem.setCollisionModifier(GraphElement.MOVE_STACK, false);
      GGraph g = plot(elem, ds, categories(ds), lin(0, 100, "m1", "m2"));

      assertFalse(elem.isStack());
      assertArrayEquals(new double[] { 10, 20, 30, 40 }, ys(g), D);
   }

   static Stream<Arguments> isStackMatrix() {
      Consumer<PointElement> none = e -> e.setCollisionModifier(GraphElement.MOVE_NONE);
      Consumer<PointElement> stack = e -> e.setCollisionModifier(GraphElement.MOVE_STACK);
      Consumer<PointElement> stackOff =
         e -> e.setCollisionModifier(GraphElement.MOVE_STACK, false);
      return Stream.of(
         Arguments.of("none", none, false, false),
         Arguments.of("none", none, true, true),
         Arguments.of("stack", stack, false, true),
         Arguments.of("stack", stack, true, true),
         Arguments.of("stack without value stacking", stackOff, false, false),
         Arguments.of("stack without value stacking", stackOff, true, true));
   }

   @ParameterizedTest(name = "modifier {0}, stackValue {2} -> {3}")
   @MethodSource("isStackMatrix")
   @DisplayName("isStack is true when the modifier stacks or stackValue is set")
   void isStackMatrixTest(String name, Consumer<PointElement> modifier, boolean stackValue,
                          boolean expected)
   {
      PointElement elem = new PointElement();
      modifier.accept(elem);
      elem.setStackValue(stackValue);

      assertEquals(expected, elem.isStack());
   }

   // ---------------------------------------------------------------- tracker, order

   @Test
   @DisplayName("over-crowded points are capped: 21 points kept at a spot of 30 identical points")
   void overCrowdedPointsCapped() {
      int grid = 100;
      int hot = 30;
      Object[][] rows = new Object[1 + grid * grid + hot][];
      rows[0] = new Object[] { "x", "y" };
      int n = 1;

      for(int i = 0; i < grid; i++) {
         for(int j = 0; j < grid; j++) {
            rows[n++] = new Object[] { (double) i, (double) j };
         }
      }

      for(int k = 0; k < hot; k++) {
         rows[n++] = new Object[] { 99.5, 99.5 };
      }

      DefaultDataSet ds = new DefaultDataSet(rows);
      GGraph g = plot(new PointElement("x", "y"), ds, lin(0, 100, "x"), lin(0, 100, "y"));

      assertEquals(grid * grid + 21, g.getGeometryCount());
   }

   @Test
   @DisplayName("sortData true sorts the rows by x, false keeps the data order")
   void sortDataFlag() {
      DefaultDataSet ds = new DefaultDataSet(new Object[][] {
         { "x", "y" }, { 3.0, 30.0 }, { 1.0, 10.0 }, { 2.0, 20.0 } });

      PointElement sorted = new PointElement("x", "y");
      sorted.setSortData(true);
      GGraph g1 = plot(sorted, ds, lin(0, 4, "x"), lin(0, 100, "y"));
      assertArrayEquals(new int[] { 1, 2, 0 }, rowIndexes(g1));

      PointElement unsorted = new PointElement("x", "y");
      unsorted.setSortData(false);
      GGraph g2 = plot(unsorted, ds, lin(0, 4, "x"), lin(0, 100, "y"));
      assertArrayEquals(new int[] { 0, 1, 2 }, rowIndexes(g2));
   }

   @Test
   @DisplayName("a linear size frame draws the small bubbles last, so they stay on top")
   void smallBubblesOnTop() {
      DefaultDataSet ds = new DefaultDataSet(new Object[][] {
         { "y", "s" }, { 1.0, 30.0 }, { 2.0, 10.0 }, { 3.0, 20.0 } });
      PointElement elem = new PointElement();
      elem.addVar("y");
      elem.setSizeFrame(new LinearSizeFrame("s"));
      GGraph g = plot(elem, ds, null, lin(0, 10, "y"));

      assertArrayEquals(new int[] { 0, 2, 1 }, rowIndexes(g));
   }

   @Test
   @DisplayName("without a measure there is one point per dimension, NaN at the other dimension")
   void noVarOnePointPerDim() {
      DefaultDataSet ds = new DefaultDataSet(new Object[][] {
         { "d1", "d2" }, { 1.0, 2.0 }, { 3.0, 4.0 } });
      PointElement elem = new PointElement();
      elem.addDim("d1");
      elem.addDim("d2");
      GGraph g = plot(elem, ds, lin(0, 10, "d1"), lin(0, 10, "d2"));

      assertEquals(4, g.getGeometryCount());
      String[] vars = { "d1", "d2", "d1", "d2" };
      double[][] tuples = { { 1, NAN }, { NAN, 2 }, { 3, NAN }, { NAN, 4 } };

      for(int i = 0; i < 4; i++) {
         assertEquals(vars[i], point(g, i).getVar(), "var " + i);
         assertArrayEquals(tuples[i], point(g, i).getTuple(), D, "tuple " + i);
      }
   }

   static Stream<Arguments> contentChanges() {
      return Stream.of(
         Arguments.of("stackValue", (Consumer<PointElement>) e -> e.setStackValue(true)),
         Arguments.of("wordCloud", (Consumer<PointElement>) e -> e.setWordCloud(true)),
         Arguments.of("contour", (Consumer<PointElement>) e -> e.setContour(true)),
         Arguments.of("sortData", (Consumer<PointElement>) e -> e.setSortData(false)),
         Arguments.of("fontScale", (Consumer<PointElement>) e -> e.setFontScale(2)),
         Arguments.of("outlineColor",
                      (Consumer<PointElement>) e -> e.setOutlineColor(Color.RED)));
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("contentChanges")
   @DisplayName("equalsContent differs for each point option and matches when identical")
   void equalsContentFields(String name, Consumer<PointElement> change) {
      PointElement a = new PointElement("x", "y");
      PointElement b = new PointElement("x", "y");
      assertTrue(a.equalsContent(b));

      change.accept(b);
      assertFalse(a.equalsContent(b));
      assertFalse(b.equalsContent(a));

      change.accept(a);
      assertTrue(a.equalsContent(b));
   }

   // ---------------------------------------------------------------- fixtures

   private static PointElement fixA() {
      PointElement elem = new PointElement();
      elem.addDim("Cat");
      elem.addVar("m1");
      elem.addVar("m2");
      return elem;
   }

   private static DefaultDataSet fixAData() {
      return new DefaultDataSet(new Object[][] {
         { "Cat", "m1", "m2" }, { "A", 10.0, 20.0 }, { "B", 30.0, 40.0 } });
   }

   private static PointElement stackValue(PointElement elem, boolean stackGroup) {
      elem.setCollisionModifier(GraphElement.MOVE_NONE);
      elem.setStackValue(true);
      elem.setStackGroup(stackGroup);
      return elem;
   }

   private static GGraph plotNeg(boolean stackNegative) {
      DefaultDataSet ds = new DefaultDataSet(new Object[][] {
         { "Cat", "m1", "m2", "m3" }, { "A", 10.0, -5.0, 20.0 }, { "B", -3.0, 4.0, -6.0 } });
      PointElement elem = stackValue(new PointElement(), true);
      elem.addDim("Cat");
      elem.addVar("m1");
      elem.addVar("m2");
      elem.addVar("m3");
      elem.setStackNegative(stackNegative);
      return plot(elem, ds, categories(ds), lin(-50, 100, "m1", "m2", "m3"));
   }

   private static CategoricalScale categories(DefaultDataSet ds) {
      CategoricalScale xs = new CategoricalScale("Cat");
      xs.init(ds);
      return xs;
   }

   private static LinearScale lin(double min, double max, String... fields) {
      LinearScale scale = new LinearScale(fields);
      scale.setMin(min);
      scale.setMax(max);
      return scale;
   }

   private static GGraph plot(GraphElement elem, DefaultDataSet ds, Scale xs, Scale ys) {
      EGraph egraph = new EGraph();
      egraph.addElement(elem);
      RectCoord coord = new RectCoord(xs, ys);
      egraph.setCoordinate(coord);
      return egraph.createGGraph(coord, ds);
   }

   private static PointGeometry point(GGraph g, int i) {
      return (PointGeometry) g.getGeometry(i);
   }

   private static double[] ys(GGraph g) {
      double[] y = new double[g.getGeometryCount()];

      for(int i = 0; i < y.length; i++) {
         double[] t = point(g, i).getTuple();
         y[i] = t[t.length - 1];
      }

      return y;
   }

   private static double[] xs(GGraph g) {
      double[] x = new double[g.getGeometryCount()];

      for(int i = 0; i < x.length; i++) {
         double[] t = point(g, i).getTuple();
         x[i] = t[t.length - 2];
      }

      return x;
   }

   private static int[] rowIndexes(GGraph g) {
      int[] rows = new int[g.getGeometryCount()];

      for(int i = 0; i < rows.length; i++) {
         rows[i] = point(g, i).getRowIndex();
      }

      return rows;
   }
}
