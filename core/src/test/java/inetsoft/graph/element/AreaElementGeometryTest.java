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
import inetsoft.graph.GraphConstants;
import inetsoft.graph.coord.ParallelCoord;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.geometry.AreaGeometry;
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
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the geometry of AreaElement: the base line rules, the interval base, the drawing order
 * of the geometries and the upkeep of the base variable list.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AreaElementGeometryTest {
   private static final double D = 1e-9;
   private static final double NAN = Double.NaN;

   @ParameterizedTest(name = "axis {0}..{1}: base at {2}")
   @CsvSource({ "0, 100, 0", "20, 100, 20", "-50, 50, 0" })
   @DisplayName("base line is at zero, or at the axis minimum when the axis starts above zero")
   void baseLine(double min, double max, double base) {
      DefaultDataSet ds = new DefaultDataSet(new Object[][] {
         { "Cat", "m" }, { "A", 30.0 }, { "B", 60.0 } });
      GGraph g = plot(new AreaElement("Cat", "m"), ds, categories(ds), lin(min, max, "m"));

      AreaGeometry geom = area(g, "m");
      assertEquals(2, geom.getTupleCount());

      for(int i = 0; i < 2; i++) {
         double[] t = geom.getTuple(i);
         double[] b = geom.getBaseTuple(i);
         assertEquals(base, b[b.length - 1], D, "base y " + i);
         assertEquals(t[t.length - 2], b[b.length - 2], D, "base x equals tuple x " + i);
      }
   }

   @Test
   @DisplayName("an axis range below zero keeps the base at zero and the negative tuple as is")
   void negativeRangeKeepsZeroBase() {
      DefaultDataSet ds = new DefaultDataSet(new Object[][] {
         { "Cat", "m" }, { "A", 30.0 }, { "B", -10.0 } });
      GGraph g = plot(new AreaElement("Cat", "m"), ds, categories(ds), lin(-50, 50, "m"));

      AreaGeometry geom = area(g, "m");
      assertArrayEquals(new double[] { 30, -10 }, ys(geom), D);
      assertArrayEquals(new double[] { 0, 0 }, baseYs(geom), D);
   }

   @Test
   @DisplayName("a reversed scale puts the base at the same value as the unreversed one")
   void reversedScaleSameBase() {
      DefaultDataSet ds = new DefaultDataSet(new Object[][] {
         { "Cat", "m" }, { "A", 30.0 }, { "B", 60.0 } });
      LinearScale ys = lin(20, 100, "m");
      ys.setReversed(true);
      GGraph g = plot(new AreaElement("Cat", "m"), ds, categories(ds), ys);

      assertArrayEquals(new double[] { 20, 20 }, baseYs(area(g, "m")), D);
   }

   @Test
   @DisplayName("an interval area takes its base from the base column")
   void intervalBaseColumn() {
      DefaultDataSet ds = new DefaultDataSet(new Object[][] {
         { "Cat", "lo", "hi" }, { "A", 5.0, 20.0 }, { "B", 10.0, 30.0 } });
      AreaElement elem = new AreaElement();
      elem.addDim("Cat");
      elem.addInterval("lo", "hi");
      GGraph g = plot(elem, ds, categories(ds), lin(0, 100, "lo", "hi"));

      AreaGeometry geom = area(g, "hi");
      assertArrayEquals(new double[] { 20, 30 }, ys(geom), D);
      assertArrayEquals(new double[] { 5, 10 }, baseYs(geom), D);
      assertEquals(geom.getTuple(1)[0], geom.getBaseTuple(1)[0], D);
   }

   @Test
   @DisplayName("a null base value gives a NaN base while the tuple keeps its value")
   void nullBaseValue() {
      DefaultDataSet ds = new DefaultDataSet(new Object[][] {
         { "Cat", "lo", "hi" }, { "A", null, 20.0 }, { "B", 10.0, 30.0 } });
      AreaElement elem = new AreaElement();
      elem.addDim("Cat");
      elem.addInterval("lo", "hi");
      GGraph g = plot(elem, ds, categories(ds), lin(0, 100, "lo", "hi"));

      AreaGeometry geom = area(g, "hi");
      assertArrayEquals(new double[] { 20, 30 }, ys(geom), D);
      assertArrayEquals(new double[] { NAN, 10 }, baseYs(geom), D);
   }

   // ---------------------------------------------------------------- stacking and order

   @Test
   @DisplayName("stacked areas all keep the zero base, the base does not follow the stack")
   void stackedAreasShareZeroBase() {
      GGraph g = plotStacked();

      assertArrayEquals(new double[] { 10, 30 }, ys(area(g, "m1")), D);
      assertArrayEquals(new double[] { 30, 70 }, ys(area(g, "m2")), D);
      assertArrayEquals(new double[] { 0, 0 }, baseYs(area(g, "m1")), D);
      assertArrayEquals(new double[] { 0, 0 }, baseYs(area(g, "m2")), D);
   }

   @Test
   @DisplayName("stacked areas are ordered tallest first so the lower ones are not covered")
   void stackedGeometryOrder() {
      GGraph g = plotStacked();

      assertEquals("m2", ((AreaGeometry) g.getGeometry(0)).getVar());
      assertEquals("m1", ((AreaGeometry) g.getGeometry(1)).getVar());
   }

   @Test
   @DisplayName("areas that cross are ordered by the first x: the taller one first")
   void crossingAreasOrder() {
      DefaultDataSet ds = new DefaultDataSet(new Object[][] {
         { "Cat", "m1", "m2" }, { "A", 3.0, 2.0 }, { "B", 1.0, 2.0 } });
      AreaElement elem = new AreaElement();
      elem.addDim("Cat");
      elem.addVar("m1");
      elem.addVar("m2");
      GGraph g = plot(elem, ds, categories(ds), lin(0, 10, "m1", "m2"));

      assertEquals("m1", ((AreaGeometry) g.getGeometry(0)).getVar());
      assertEquals("m2", ((AreaGeometry) g.getGeometry(1)).getVar());
   }

   @Test
   @DisplayName("stacked area with a negative value: the tuple goes below, the base stays at zero")
   void stackedAreaNegativeValue() {
      DefaultDataSet ds = new DefaultDataSet(new Object[][] {
         { "Cat", "m1", "m2" }, { "A", 10.0, -5.0 } });
      AreaElement elem = new AreaElement();
      elem.setCollisionModifier(GraphElement.MOVE_STACK);
      elem.setStackGroup(true);
      elem.addDim("Cat");
      elem.addVar("m1");
      elem.addVar("m2");
      GGraph g = plot(elem, ds, categories(ds), lin(-50, 100, "m1", "m2"));

      assertArrayEquals(new double[] { -5 }, ys(area(g, "m2")), D);
      assertArrayEquals(new double[] { 0 }, baseYs(area(g, "m2")), D);
      assertArrayEquals(new double[] { 10 }, ys(area(g, "m1")), D);
      assertArrayEquals(new double[] { 0 }, baseYs(area(g, "m1")), D);
   }

   @Test
   @DisplayName("parallel area: the base of each axis is the axis minimum")
   void parallelBaseIsAxisMinimum() {
      DefaultDataSet ds = new DefaultDataSet(new Object[][] {
         { "d1", "d2", "d3" }, { 30.0, 50.0, 20.0 } });
      Scale[] scales = { lin(0, 100, "d1"), lin(20, 100, "d2"), lin(0, 50, "d3") };
      EGraph egraph = new EGraph();
      AreaElement elem = new AreaElement();

      for(Scale scale : scales) {
         elem.addDim(scale.getFields()[0]);
         egraph.setScale(scale.getFields()[0], scale);
      }

      egraph.addElement(elem);
      ParallelCoord coord = new ParallelCoord(scales);
      egraph.setCoordinate(coord);
      GGraph g = egraph.createGGraph(coord, ds);

      assertEquals(1, g.getGeometryCount());
      AreaGeometry geom = (AreaGeometry) g.getGeometry(0);
      assertEquals(3, geom.getTupleCount());
      assertEquals(0, geom.getBaseTuple(0)[0], D);
      assertEquals(20, geom.getBaseTuple(1)[1], D);
      assertEquals(0, geom.getBaseTuple(2)[2], D);
   }

   // ---------------------------------------------------------------- base list upkeep

   @Test
   @DisplayName("removeVar removes the base entry of the same index")
   void removeVarKeepsBasesAligned() {
      AreaElement elem = new AreaElement();
      elem.addInterval(null, "a");
      elem.addInterval("lo", "b");
      elem.removeVar(0);

      assertEquals(1, elem.getVarCount());
      assertEquals("lo", elem.getBaseVar(0));
      assertArrayEquals(new String[] { "b", "lo" }, elem.getVars());
   }

   @Test
   @DisplayName("clearVars clears the variables and the base entries")
   void clearVarsClearsBases() {
      AreaElement elem = new AreaElement();
      elem.addInterval("lo1", "a");
      elem.addInterval("lo2", "b");
      elem.clearVars();

      assertEquals(0, elem.getVars().length);
      assertNull(elem.getBaseVar(0));
   }

   @Test
   @DisplayName("setBaseVar applies to the variables that exist at that time only (pinned behavior)")
   void setBaseVarOnlyExistingVars() {
      AreaElement elem = new AreaElement();
      elem.addVar("a");
      elem.setBaseVar("lo");
      elem.addVar("b");

      assertEquals("lo", elem.getBaseVar(0));
      assertNull(elem.getBaseVar(1));
   }

   @Test
   @DisplayName("equalsContent compares the base variables")
   void equalsContentBases() {
      AreaElement a = new AreaElement();
      AreaElement b = new AreaElement();
      a.addInterval("lo", "hi");
      b.addInterval("lo", "hi");
      assertTrue(a.equalsContent(b));

      AreaElement c = new AreaElement();
      c.addVar("hi");
      assertFalse(a.equalsContent(c));
      assertFalse(c.equalsContent(a));
   }

   @Test
   @DisplayName("area defaults: alpha hint 0.8 and no fill line style, unlike a line element")
   void areaDefaults() {
      AreaElement area = new AreaElement();
      LineElement line = new LineElement();

      assertEquals(0.8, ((Number) area.getHint(GraphElement.HINT_ALPHA)).doubleValue(), D);
      assertEquals(0, area.getFillLineStyle());
      assertEquals(GraphConstants.DASH_LINE, line.getFillLineStyle());
   }

   // ---------------------------------------------------------------- fixtures

   private static GGraph plotStacked() {
      DefaultDataSet ds = new DefaultDataSet(new Object[][] {
         { "Cat", "m1", "m2" }, { "A", 10.0, 20.0 }, { "B", 30.0, 40.0 } });
      AreaElement elem = new AreaElement();
      elem.setCollisionModifier(GraphElement.MOVE_STACK);
      elem.setStackGroup(true);
      elem.addDim("Cat");
      elem.addVar("m1");
      elem.addVar("m2");
      return plot(elem, ds, categories(ds), lin(0, 100, "m1", "m2"));
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

   private static AreaGeometry area(GGraph g, String var) {
      List<AreaGeometry> found = new ArrayList<>();

      for(int i = 0; i < g.getGeometryCount(); i++) {
         AreaGeometry geom = (AreaGeometry) g.getGeometry(i);

         if(var.equals(geom.getVar())) {
            found.add(geom);
         }
      }

      assertEquals(1, found.size(), "areas of " + var);
      return found.get(0);
   }

   private static double[] ys(AreaGeometry geom) {
      double[] y = new double[geom.getTupleCount()];

      for(int i = 0; i < y.length; i++) {
         double[] t = geom.getTuple(i);
         y[i] = t[t.length - 1];
      }

      return y;
   }

   private static double[] baseYs(AreaGeometry geom) {
      double[] y = new double[geom.getTupleCount()];

      for(int i = 0; i < y.length; i++) {
         double[] t = geom.getBaseTuple(i);
         y[i] = t[t.length - 1];
      }

      return y;
   }
}
