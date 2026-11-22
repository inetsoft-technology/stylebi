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
import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.graph.coord.ParallelCoord;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.geometry.LineGeometry;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.scale.Scale;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the geometry that LineElement.createGeometry() builds: stacking arithmetic, null gaps,
 * brush range, grouping into lines and the row indexes of each line. The geometries of a
 * line element come in HashMap order, so a line is looked up by its variable and the first
 * tuple index.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class LineElementGeometryTest {
   private static final double D = 1e-9;
   private static final double NAN = Double.NaN;

   // ---------------------------------------------------------------- stacking by x

   @Test
   @DisplayName("stack group: two measures stack per category, y of m2 sits on m1")
   void stackGroupTwoMeasures() {
      GGraph g = plot(stacked(fixA()), fixAData(), categories(fixAData()), lin(0, 100, "m1", "m2"));

      assertEquals(2, count(g));
      assertArrayEquals(new double[] { 10, 30 }, ys(line(g, "m1")), D);
      assertArrayEquals(new double[] { 30, 70 }, ys(line(g, "m2")), D);
      assertArrayEquals(new double[] { 0.5, 1.5 }, xs(line(g, "m1")), D);
   }

   @Test
   @DisplayName("stack group without the stack modifier plots the raw values")
   void stackGroupWithoutStackPlotsRaw() {
      LineElement elem = fixA();
      elem.setStackGroup(true);
      GGraph g = plot(elem, fixAData(), categories(fixAData()), lin(0, 100, "m1", "m2"));

      assertArrayEquals(new double[] { 10, 30 }, ys(line(g, "m1")), D);
      assertArrayEquals(new double[] { 20, 40 }, ys(line(g, "m2")), D);
   }

   @Test
   @DisplayName("stack by series: lines are keyed by the color field and stack per category")
   void stackBySeries() {
      DefaultDataSet ds = fixBData();
      LineElement elem = stacked(new LineElement("Cat", "m"));
      elem.setColorFrame(new CategoricalColorFrame("S"));
      GGraph g = plot(elem, ds, categories(ds), lin(0, 100, "m"));

      assertEquals(2, count(g));
      LineGeometry s1 = line(g, "m", 0);
      LineGeometry s2 = line(g, "m", 1);
      assertArrayEquals(new int[] { 0, 2 }, s1.getTupleIndexes());
      assertArrayEquals(new int[] { 1, 3 }, s2.getTupleIndexes());
      assertArrayEquals(new double[] { 10, 30 }, ys(s1), D);
      assertArrayEquals(new double[] { 30, 70 }, ys(s2), D);
   }

   @Test
   @DisplayName("stacked values are prefix sums of the series, the last series is the column total")
   void stackSumsAreCumulative() {
      Random rnd = new Random(6001);
      int series = 5;
      int cats = 6;
      Object[][] rows = new Object[series * cats + 1][];
      rows[0] = new Object[] { "Cat", "S", "m" };
      double[][] val = new double[series][cats];

      for(int c = 0; c < cats; c++) {
         for(int k = 0; k < series; k++) {
            val[k][c] = 1 + rnd.nextInt(50);
            rows[1 + c * series + k] = new Object[] { "c" + c, "s" + k, val[k][c] };
         }
      }

      DefaultDataSet ds = new DefaultDataSet(rows);
      LineElement elem = stacked(new LineElement("Cat", "m"));
      elem.setColorFrame(new CategoricalColorFrame("S"));
      GGraph g = plot(elem, ds, categories(ds), lin(0, 1000, "m"));

      assertEquals(series, count(g));
      double[] prev = new double[cats];

      for(int k = 0; k < series; k++) {
         double[] y = ys(line(g, "m", k));
         assertEquals(cats, y.length);

         for(int c = 0; c < cats; c++) {
            assertEquals(val[k][c], y[c] - prev[c], D, "value of series " + k + " at x " + c);
         }

         prev = y;
      }

      for(int c = 0; c < cats; c++) {
         double sum = 0;

         for(int k = 0; k < series; k++) {
            sum += val[k][c];
         }

         assertEquals(sum, prev[c], D, "column total at x " + c);
      }
   }

   @Test
   @DisplayName("negative values stack downward apart from the positive stack")
   void negativeStacksApart() {
      GGraph g = plotNeg(true);

      assertArrayEquals(new double[] { 10, -3 }, ys(line(g, "m1")), D);
      assertArrayEquals(new double[] { -5, 4 }, ys(line(g, "m2")), D);
      assertArrayEquals(new double[] { 30, -9 }, ys(line(g, "m3")), D);
   }

   @Test
   @DisplayName("stackNegative false accumulates negative values into the one stack")
   void stackNegativeFalse() {
      GGraph g = plotNeg(false);

      assertArrayEquals(new double[] { 10, -3 }, ys(line(g, "m1")), D);
      assertArrayEquals(new double[] { 5, 1 }, ys(line(g, "m2")), D);
      assertArrayEquals(new double[] { 25, -5 }, ys(line(g, "m3")), D);
   }

   @Test
   @DisplayName("a zero value joins the positive stack")
   void zeroJoinsPositiveStack() {
      DefaultDataSet ds = new DefaultDataSet(new Object[][] {
         { "Cat", "m1", "m2", "m3" }, { "A", 5.0, 0.0, 3.0 }, { "B", 0.0, 7.0, 1.0 } });
      LineElement elem = stacked(new LineElement());
      elem.addDim("Cat");
      elem.addVar("m1");
      elem.addVar("m2");
      elem.addVar("m3");
      GGraph g = plot(elem, ds, categories(ds), lin(-20, 100, "m1", "m2", "m3"));

      assertArrayEquals(new double[] { 5, 0 }, ys(line(g, "m1")), D);
      assertArrayEquals(new double[] { 5, 7 }, ys(line(g, "m2")), D);
      assertArrayEquals(new double[] { 8, 8 }, ys(line(g, "m3")), D);
   }

   @Test
   @DisplayName("a null in the lowest series leaves a gap there and the next series starts at zero")
   void nullInLowestSeries() {
      DefaultDataSet ds = new DefaultDataSet(new Object[][] {
         { "Cat", "m1", "m2" }, { "A", null, 20.0 }, { "B", 30.0, 40.0 } });
      GGraph g = plot(stacked(fixA()), ds, categories(ds), lin(0, 100, "m1", "m2"));

      assertArrayEquals(new double[] { NAN, 30 }, ys(line(g, "m1")), D);
      assertArrayEquals(new double[] { 20, 70 }, ys(line(g, "m2")), D);
   }

   @Test
   @DisplayName("a null in a middle series leaves a gap there and the stack continues above it")
   void nullInMiddleSeries() {
      DefaultDataSet ds = new DefaultDataSet(new Object[][] {
         { "Cat", "m1", "m2", "m3" }, { "A", 10.0, null, 5.0 }, { "B", 30.0, 40.0, 1.0 } });
      LineElement elem = stacked(new LineElement());
      elem.addDim("Cat");
      elem.addVar("m1");
      elem.addVar("m2");
      elem.addVar("m3");
      GGraph g = plot(elem, ds, categories(ds), lin(0, 100, "m1", "m2", "m3"));

      assertArrayEquals(new double[] { 10, 30 }, ys(line(g, "m1")), D);
      assertArrayEquals(new double[] { NAN, 70 }, ys(line(g, "m2")), D);
      assertArrayEquals(new double[] { 15, 71 }, ys(line(g, "m3")), D);
   }

   // ---------------------------------------------------------------- cumulative line

   @Test
   @DisplayName("cumulative line (Pareto): values accumulate along x, negatives with them")
   void cumulativeLine() {
      GGraph g = plotCumulative(GraphElement.STACK_SYMMETRIC, false, 5.0, 3.0, -2.0, 4.0);

      assertArrayEquals(new double[] { 5, 8, 6, 10 }, ys(line(g, "m")), D);
   }

   @Test
   @DisplayName("cumulative line with stackNegative: negatives accumulate apart")
   void cumulativeLineStackNegative() {
      GGraph g = plotCumulative(GraphElement.STACK_SYMMETRIC, true, 5.0, 3.0, -2.0, 4.0);

      assertArrayEquals(new double[] { 5, 8, -2, 12 }, ys(line(g, "m")), D);
   }

   @Test
   @DisplayName("cumulative line without the stack modifier plots the raw values")
   void cumulativeLineNoStack() {
      GGraph g = plotCumulative(GraphElement.MOVE_NONE, false, 5.0, 3.0, -2.0, 4.0);

      assertArrayEquals(new double[] { 5, 3, -2, 4 }, ys(line(g, "m")), D);
   }

   @Test
   @DisplayName("cumulative line: a null counts as 0 in the running total, the curve continues flat")
   void cumulativeLineNull() {
      GGraph g = plotCumulative(GraphElement.STACK_SYMMETRIC, false, 5.0, null, 3.0);

      assertArrayEquals(new double[] { 5, 5, 8 }, ys(line(g, "m")), D);
   }

   @Test
   @DisplayName("stack group on a numeric x that starts at zero stacks like a category x")
   void firstXEqualsZero() {
      DefaultDataSet ds = new DefaultDataSet(new Object[][] {
         { "x", "m1", "m2" }, { 0.0, 10.0, 20.0 }, { 1.0, 30.0, 40.0 } });
      LineElement elem = stacked(new LineElement());
      elem.addDim("x");
      elem.addVar("m1");
      elem.addVar("m2");
      GGraph g = plot(elem, ds, lin(0, 2, "x"), lin(0, 100, "m1", "m2"));

      assertArrayEquals(new double[] { 10, 30 }, ys(line(g, "m1")), D);
      assertArrayEquals(new double[] { 30, 70 }, ys(line(g, "m2")), D);
   }

   // ---------------------------------------------------------------- radar and rows

   @Test
   @DisplayName("radar: one geometry per row, tuple k holds the value of dimension k")
   void radarGeometryPerRow() {
      DefaultDataSet ds = new DefaultDataSet(new Object[][] {
         { "m1", "m2", "m3" }, { 10.0, 20.0, 30.0 }, { 50.0, 40.0, 60.0 }, { 5.0, 90.0, 15.0 } });
      GGraph g = plotRadar(ds, false);

      assertEquals(3, count(g));
      double[][] rows = { { 10, 20, 30 }, { 50, 40, 60 }, { 5, 90, 15 } };

      for(int r = 0; r < 3; r++) {
         LineGeometry geom = byFirstTuple(g, r);
         assertArrayEquals(new int[] { r }, geom.getTupleIndexes());
         assertEquals(3, geom.getTupleCount());

         for(int k = 0; k < 3; k++) {
            assertEquals(rows[r][k], geom.getTuple(k)[k], D, "row " + r + " tuple " + k);
         }
      }
   }

   @Test
   @DisplayName("brush range: nulls inside the range stay as gaps, nulls outside are dropped")
   void brushRangeKeepsInnerNulls() {
      DefaultDataSet ds = new DefaultDataSet(new Object[][] {
         { "x", "y" }, { 1.0, null }, { 2.0, 1.0 }, { 3.0, null }, { 4.0, null }, { 5.0, 5.0 } });
      LineElement elem = new LineElement("x", "y");
      elem.setHint("line.data.from", 1);
      elem.setHint("line.data.to", 3);
      GGraph g = plot(elem, ds, lin(0, 6, "x"), lin(0, 10, "y"));

      assertEquals(1, count(g));
      assertArrayEquals(new double[] { 1, NAN, 5 }, ys(line(g, "y")), D);
   }

   @Test
   @DisplayName("brush range compares base rows, not sorted positions")
   void brushRangeUsesBaseRows() {
      DefaultDataSet order = new DefaultDataSet(new Object[][] {
         { "Cat" }, { "A" }, { "B" }, { "C" } });
      DefaultDataSet ds = new DefaultDataSet(new Object[][] {
         { "Cat", "y" }, { "C", null }, { "A", 1.0 }, { "B", null } });
      CategoricalScale xs = new CategoricalScale("Cat");
      xs.init(order);
      LineElement elem = new LineElement("Cat", "y");
      elem.setHint("line.data.from", 2);
      elem.setHint("line.data.to", 3);
      GGraph g = plot(elem, ds, xs, lin(0, 10, "y"));

      assertArrayEquals(new double[] { 1, NAN }, ys(line(g, "y")), D);
      // B is at sorted position 1; the kept gap must be B, not the row at position 2
      assertArrayEquals(new int[] { 0, 1 }, line(g, "y").getTupleIndexes());
   }

   @Test
   @DisplayName("sort fields order the points of the line")
   void sortFieldsOrderPoints() {
      DefaultDataSet ds = new DefaultDataSet(new Object[][] {
         { "Cat", "y", "p" }, { "A", 10.0, 3.0 }, { "B", 20.0, 1.0 }, { "C", 30.0, 2.0 } });
      // a sort column without a comparator keeps the data order, so the field is given one
      ds.setComparator("p", Comparator.naturalOrder());
      LineElement elem = new LineElement("Cat", "y");
      elem.setSortFields("p");
      GGraph g = plot(elem, ds, categories(ds), lin(0, 100, "y"));

      assertArrayEquals(new double[] { 20, 30, 10 }, ys(line(g, "y")), D);
   }

   // ---------------------------------------------------------------- grouping into lines

   @Test
   @DisplayName("LineGroup.AESTHETIC: one line per value of the color field")
   void lineGroupAesthetic() {
      GGraph g = plotGroups(LineElement.LineGroup.AESTHETIC);

      assertEquals(2, count(g));
      assertArrayEquals(new int[] { 0, 2 }, line(g, "m", 0).getTupleIndexes());
      assertArrayEquals(new int[] { 1, 3 }, line(g, "m", 1).getTupleIndexes());
      assertArrayEquals(new double[] { 10, 30 }, ys(line(g, "m", 0)), D);
   }

   @Test
   @DisplayName("LineGroup.GROUP: one line per value of the group field")
   void lineGroupGroup() {
      GGraph g = plotGroups(LineElement.LineGroup.GROUP);

      assertEquals(2, count(g));
      assertArrayEquals(new int[] { 0, 1 }, line(g, "m", 0).getTupleIndexes());
      assertArrayEquals(new int[] { 2, 3 }, line(g, "m", 2).getTupleIndexes());
   }

   @Test
   @DisplayName("LineGroup.ALL: one line per combination of color and group value")
   void lineGroupAll() {
      GGraph g = plotGroups(LineElement.LineGroup.ALL);

      assertEquals(4, count(g));

      for(int i = 0; i < 4; i++) {
         assertArrayEquals(new int[] { i }, line(g, "m", i).getTupleIndexes());
         assertEquals(1, line(g, "m", i).getTupleCount());
      }
   }

   @Test
   @DisplayName("LineGroup.NONE: all rows form one line")
   void lineGroupNone() {
      GGraph g = plotGroups(LineElement.LineGroup.NONE);

      assertEquals(1, count(g));
      assertArrayEquals(new int[] { 0, 1, 2, 3 }, line(g, "m", 0).getTupleIndexes());
      assertArrayEquals(new double[] { 10, 20, 30, 40 }, ys(line(g, "m", 0)), D);
   }

   @Disabled("Not filed (not reachable in practice: needs two group dimensions whose comma-joined "
      + "text collides, LineElement.java:556): series values with a comma: expected 2 lines, "
      + "actual 1 (the line key joins the "
      + "color and group values with a comma, so 'a,b'+'c' and 'a'+'b,c' both give 'a,b,c', "
      + "LineElement.java:556)")
   @Test
   @DisplayName("series values that contain a comma still make distinct lines")
   void seriesValuesWithCommaStayDistinct() {
      DefaultDataSet ds = new DefaultDataSet(new Object[][] {
         { "Cat", "S", "G", "m" }, { "A", "a,b", "c", 10.0 }, { "A", "a", "b,c", 20.0 } });
      LineElement elem = stacked(new LineElement("Cat", "m"));
      elem.setColorFrame(new CategoricalColorFrame("S"));
      elem.setGroupFields("G");
      elem.setLineGroup(LineElement.LineGroup.ALL);
      GGraph g = plot(elem, ds, categories(ds), lin(0, 100, "m"));

      assertEquals(2, count(g));
   }

   @Test
   @DisplayName("comma-containing series values that do not collide stay separate lines")
   void commaSeriesValuesThatDoNotCollideStaySeparate() {
      DefaultDataSet ds = new DefaultDataSet(new Object[][] {
         { "Cat", "S", "G", "m" }, { "A", "a,b", "c", 10.0 }, { "A", "x", "y,z", 20.0 } });
      LineElement elem = stacked(new LineElement("Cat", "m"));
      elem.setColorFrame(new CategoricalColorFrame("S"));
      elem.setGroupFields("G");
      elem.setLineGroup(LineElement.LineGroup.ALL);
      GGraph g = plot(elem, ds, categories(ds), lin(0, 100, "m"));

      assertEquals(2, count(g));
      assertArrayEquals(new int[] { 0 }, line(g, "m", 0).getTupleIndexes());
      assertArrayEquals(new int[] { 1 }, line(g, "m", 1).getTupleIndexes());
   }

   @Test
   @DisplayName("a series value that contains a pipe makes its own line without an exception")
   void seriesValueWithPipe() {
      DefaultDataSet ds = new DefaultDataSet(new Object[][] {
         { "Cat", "S", "m" }, { "A", "p|q", 10.0 }, { "A", "r", 20.0 } });
      LineElement elem = stacked(new LineElement("Cat", "m"));
      elem.setColorFrame(new CategoricalColorFrame("S"));
      GGraph g = plot(elem, ds, categories(ds), lin(0, 100, "m"));

      assertEquals(2, count(g));
      assertArrayEquals(new double[] { 10 }, ys(line(g, "m", 0)), D);
      assertArrayEquals(new double[] { 30 }, ys(line(g, "m", 1)), D);
   }

   @Disabled("Bug #82050: Line/area chart fails to render when a measure name contains a pipe")
   @Test
   @DisplayName("a measure whose name contains a pipe still produces its geometry")
   void measureNameWithPipe() {
      DefaultDataSet ds = new DefaultDataSet(new Object[][] {
         { "Cat", "a|b" }, { "A", 10.0 } });
      LineElement elem = stacked(new LineElement("Cat", "a|b"));
      GGraph g = plot(elem, ds, categories(ds), lin(0, 100, "a|b"));

      assertEquals(1, count(g));
      assertArrayEquals(new double[] { 10 }, ys(line(g, "a|b")), D);
   }

   @ParameterizedTest(name = "{0}")
   @EnumSource(value = Branch.class)
   @DisplayName("setClosed(true) reaches every geometry of every createGeometry branch")
   void closedFlagInEveryBranch(Branch branch) {
      DefaultDataSet ds = new DefaultDataSet(new Object[][] {
         { "Cat", "m" }, { "A", 10.0 }, { "B", 20.0 } });
      GGraph g;

      if(branch == Branch.RADAR) {
         g = plotRadar(new DefaultDataSet(new Object[][] {
            { "m1", "m2", "m3" }, { 10.0, 20.0, 30.0 }, { 5.0, 9.0, 15.0 } }), true);
      }
      else {
         LineElement elem = new LineElement("Cat", "m");
         elem.setClosed(true);
         elem.setStackGroup(branch == Branch.STACK_GROUP);
         g = plot(elem, ds, categories(ds), lin(0, 100, "m"));
      }

      assertTrue(count(g) > 0);

      for(int i = 0; i < count(g); i++) {
         assertTrue(((LineGeometry) g.getGeometry(i)).isClosed(), "geometry " + i);
      }
   }

   enum Branch { RADAR, SINGLE_LINE, STACK_GROUP }

   @Test
   @DisplayName("tuple, sub row and root row indexes follow the sorted order of unsorted rows")
   void indexesWithUnsortedRows() {
      DefaultDataSet order = new DefaultDataSet(new Object[][] {
         { "Cat" }, { "A" }, { "B" }, { "C" } });
      DefaultDataSet ds = new DefaultDataSet(new Object[][] {
         { "Cat", "m1" }, { "C", 30.0 }, { "A", 10.0 }, { "B", 20.0 } });
      CategoricalScale xs = new CategoricalScale("Cat");
      xs.init(order);
      GGraph g = plot(new LineElement("Cat", "m1"), ds, xs, lin(0, 100, "m1"));

      LineGeometry geom = line(g, "m1");
      assertArrayEquals(new int[] { 0, 1, 2 }, geom.getTupleIndexes());
      assertArrayEquals(new int[] { 1, 2, 0 }, geom.getSubRowIndexes());
      assertArrayEquals(new int[] { 1, 2, 0 }, geom.getRowIndexes());
      assertEquals(1, geom.getColIndex());
      assertArrayEquals(new double[] { 10, 20, 30 }, ys(geom), D);
   }

   @Test
   @DisplayName("rows rejected by the data selector do not take part in the stack")
   void filteredRowsDoNotStack() {
      DefaultDataSet ds = fixBData();
      LineElement elem = stacked(new LineElement("Cat", "m"));
      elem.setColorFrame(new CategoricalColorFrame("S"));
      elem.setGraphDataSelector((data, row, fields) -> row != 0);
      GGraph g = plot(elem, ds, categories(ds), lin(0, 100, "m"));

      assertEquals(2, count(g));
      assertArrayEquals(new double[] { 30 }, ys(line(g, "m", 2)), D);
      assertArrayEquals(new double[] { 20, 70 }, ys(line(g, "m", 1)), D);
   }

   // ---------------------------------------------------------------- fixtures

   private static LineElement fixA() {
      LineElement elem = new LineElement();
      elem.addDim("Cat");
      elem.addVar("m1");
      elem.addVar("m2");
      return elem;
   }

   private static DefaultDataSet fixAData() {
      return new DefaultDataSet(new Object[][] {
         { "Cat", "m1", "m2" }, { "A", 10.0, 20.0 }, { "B", 30.0, 40.0 } });
   }

   private static DefaultDataSet fixBData() {
      return new DefaultDataSet(new Object[][] {
         { "Cat", "S", "m" }, { "A", "s1", 10.0 }, { "A", "s2", 20.0 },
         { "B", "s1", 30.0 }, { "B", "s2", 40.0 } });
   }

   private static LineElement stacked(LineElement elem) {
      elem.setCollisionModifier(GraphElement.MOVE_STACK);
      elem.setStackGroup(true);
      return elem;
   }

   private static GGraph plotNeg(boolean stackNegative) {
      DefaultDataSet ds = new DefaultDataSet(new Object[][] {
         { "Cat", "m1", "m2", "m3" }, { "A", 10.0, -5.0, 20.0 }, { "B", -3.0, 4.0, -6.0 } });
      LineElement elem = stacked(new LineElement());
      elem.addDim("Cat");
      elem.addVar("m1");
      elem.addVar("m2");
      elem.addVar("m3");
      elem.setStackNegative(stackNegative);
      return plot(elem, ds, categories(ds), lin(-50, 100, "m1", "m2", "m3"));
   }

   private static GGraph plotCumulative(int modifier, boolean stackNegative, Double... values) {
      Object[][] rows = new Object[values.length + 1][];
      rows[0] = new Object[] { "Cat", "m" };

      for(int i = 0; i < values.length; i++) {
         rows[i + 1] = new Object[] { "c" + i, values[i] };
      }

      DefaultDataSet ds = new DefaultDataSet(rows);
      LineElement elem = new LineElement("Cat", "m");
      elem.setCollisionModifier(modifier);
      elem.setStackGroup(false);
      elem.setStackNegative(stackNegative);
      return plot(elem, ds, categories(ds), lin(-50, 100, "m"));
   }

   private static GGraph plotGroups(LineElement.LineGroup group) {
      DefaultDataSet ds = new DefaultDataSet(new Object[][] {
         { "Cat", "S", "G", "m" }, { "A", "s1", "g1", 10.0 }, { "A", "s2", "g1", 20.0 },
         { "B", "s1", "g2", 30.0 }, { "B", "s2", "g2", 40.0 } });
      LineElement elem = new LineElement("Cat", "m");
      elem.setStackGroup(true);
      elem.setColorFrame(new CategoricalColorFrame("S"));
      elem.setGroupFields("G");
      elem.setLineGroup(group);
      return plot(elem, ds, categories(ds), lin(0, 100, "m"));
   }

   private static GGraph plotRadar(DefaultDataSet ds, boolean closed) {
      Scale[] scales = {
         lin(0, 100, "m1"), lin(0, 100, "m2"), lin(0, 100, "m3") };
      EGraph egraph = new EGraph();
      LineElement elem = new LineElement();
      elem.setClosed(closed);

      for(Scale scale : scales) {
         String field = scale.getFields()[0];
         elem.addDim(field);
         egraph.setScale(field, scale);
      }

      egraph.addElement(elem);
      ParallelCoord coord = new ParallelCoord(scales);
      egraph.setCoordinate(coord);
      return egraph.createGGraph(coord, ds);
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

   private static int count(GGraph g) {
      return g.getGeometryCount();
   }

   private static List<LineGeometry> lines(GGraph g) {
      List<LineGeometry> list = new ArrayList<>();

      for(int i = 0; i < g.getGeometryCount(); i++) {
         list.add((LineGeometry) g.getGeometry(i));
      }

      return list;
   }

   /** The only line of the variable. */
   private static LineGeometry line(GGraph g, String var) {
      List<LineGeometry> found = new ArrayList<>();

      for(LineGeometry geom : lines(g)) {
         if(var.equals(geom.getVar())) {
            found.add(geom);
         }
      }

      assertEquals(1, found.size(), "lines of " + var);
      return found.get(0);
   }

   /** The line of the variable whose first tuple index is given. */
   private static LineGeometry line(GGraph g, String var, int firstTuple) {
      for(LineGeometry geom : lines(g)) {
         int[] idx = geom.getTupleIndexes();

         if(var.equals(geom.getVar()) && idx.length > 0 && idx[0] == firstTuple) {
            return geom;
         }
      }

      return fail("no line of " + var + " starting at tuple " + firstTuple);
   }

   private static LineGeometry byFirstTuple(GGraph g, int firstTuple) {
      for(LineGeometry geom : lines(g)) {
         if(geom.getTupleIndexes()[0] == firstTuple) {
            return geom;
         }
      }

      return fail("no line starting at tuple " + firstTuple);
   }

   private static double[] ys(LineGeometry geom) {
      double[] y = new double[geom.getTupleCount()];

      for(int i = 0; i < y.length; i++) {
         double[] t = geom.getTuple(i);
         y[i] = t[t.length - 1];
      }

      return y;
   }

   private static double[] xs(LineGeometry geom) {
      double[] x = new double[geom.getTupleCount()];

      for(int i = 0; i < x.length; i++) {
         double[] t = geom.getTuple(i);
         x[i] = t[t.length - 2];
      }

      return x;
   }
}
