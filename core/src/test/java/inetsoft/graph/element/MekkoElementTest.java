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
package inetsoft.graph.element;

import inetsoft.graph.EGraph;
import inetsoft.graph.Plotter;
import inetsoft.graph.VGraph;
import inetsoft.graph.coord.MekkoCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.geometry.MekkoGeometry;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.visual.MekkoVO;
import inetsoft.graph.visual.VisualObject;
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

import java.awt.geom.Rectangle2D;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link MekkoElement}: input order and row mapping, non-numeric measures, missing inner
 * dimension, number types and {@code equalsContent}, observed through the visual objects of a
 * marimekko chart plotted with {@code Plotter.plotAndLayout}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class MekkoElementTest {
   private static final double EPS = 1e-6;

   /** Rows (group, stack, value) in sorted order: East 40, North 100, West 60. */
   private static final Object[] SORTED = {
      "East", "x", 30.0, "East", "y", 10.0, "North", "x", 60.0, "North", "y", 40.0,
      "West", "x", 20.0, "West", "y", 40.0 };

   /** The same rows in another order. */
   private static final Object[] PERMUTED = {
      "West", "y", 40.0, "East", "x", 30.0, "North", "y", 40.0, "East", "y", 10.0,
      "West", "x", 20.0, "North", "x", 60.0 };

   private static class Chart {
      MekkoCoord coord = new MekkoCoord("R", "S");
      MekkoElement element = new MekkoElement("R", "P", "S");
      Object[] triples;
      VGraph vg;

      Chart(Object... triples) {
         this.triples = triples;
      }

      Chart plot() {
         EGraph graph = new EGraph();
         Object[][] rows = new Object[triples.length / 3 + 1][];
         rows[0] = new Object[] { "R", "P", "S" };

         for(int i = 0; i < triples.length / 3; i++) {
            rows[i + 1] = new Object[] { triples[3 * i], triples[3 * i + 1], triples[3 * i + 2] };
         }

         graph.setScale("R", coord.getScales()[0]);
         graph.addElement(element);
         graph.setCoordinate(coord);
         vg = Plotter.getPlotter(graph).plotAndLayout(new DefaultDataSet(rows), 0, 0, 600, 400);
         return this;
      }

      Rectangle2D plot0() {
         return vg.getPlotBounds();
      }

      /** Bounds of every visual object keyed by the index of its data row. */
      Map<Integer, Rectangle2D> boxes() {
         Map<Integer, Rectangle2D> map = new TreeMap<>();

         for(VisualObject vo : GTool.getVOs(vg)) {
            if(vo instanceof MekkoVO) {
               int row = ((MekkoGeometry) ((MekkoVO) vo).getGeometry()).getSubRowIndex();
               map.put(row, ((MekkoVO) vo).getBounds());
            }
         }

         return map;
      }
   }

   private static void assertSameBox(Rectangle2D expected, Rectangle2D actual, String what) {
      assertNotNull(actual, what);
      assertEquals(expected.getX(), actual.getX(), EPS, what + " x");
      assertEquals(expected.getY(), actual.getY(), EPS, what + " y");
      assertEquals(expected.getWidth(), actual.getWidth(), EPS, what + " width");
      assertEquals(expected.getHeight(), actual.getHeight(), EPS, what + " height");
   }

   /** Index of the row (group, stack) in a triple array. */
   private static int indexOf(Object[] triples, String group, String stack) {
      for(int i = 0; i < triples.length / 3; i++) {
         if(group.equals(triples[3 * i]) && stack.equals(triples[3 * i + 1])) {
            return i;
         }
      }

      throw new IllegalArgumentException(group + "/" + stack);
   }

   // ---------------------------------------------------------------- order and mapping

   @Test
   @DisplayName("group columns follow the order of first appearance in the input")
   void groupsInFirstAppearanceOrder() {
      Chart permuted = new Chart(PERMUTED).plot();
      List<String> groups = new ArrayList<>(List.of("East", "North", "West"));

      groups.sort(Comparator.comparingDouble(
         g -> permuted.boxes().get(indexOf(PERMUTED, g, "x")).getX()));

      assertEquals(List.of("West", "East", "North"), groups);
   }

   @Test
   @DisplayName("permuted input rows keep the width, height and y of every (group, stack) box "
      + "of the sorted input")
   void inputOrderKeepsSizeAndStackPosition() {
      Chart sorted = new Chart(SORTED).plot();
      Chart permuted = new Chart(PERMUTED).plot();

      for(String group : new String[] { "East", "North", "West" }) {
         for(String stack : new String[] { "x", "y" }) {
            Rectangle2D expected = sorted.boxes().get(indexOf(SORTED, group, stack));
            Rectangle2D actual = permuted.boxes().get(indexOf(PERMUTED, group, stack));
            String what = group + "/" + stack;

            assertEquals(expected.getWidth(), actual.getWidth(), EPS, what + " width");
            assertEquals(expected.getHeight(), actual.getHeight(), EPS, what + " height");
            assertEquals(expected.getY(), actual.getY(), EPS, what + " y");
         }
      }
   }

   @Test
   @DisplayName("each data row keeps its own width (group share) and height (value share)")
   void rowMapping() {
      Chart chart = new Chart(PERMUTED).plot();
      Map<Object, Double> totals = new HashMap<>();
      double all = 0;

      for(int i = 0; i < PERMUTED.length / 3; i++) {
         double v = (Double) PERMUTED[3 * i + 2];
         totals.merge(PERMUTED[3 * i], v, Double::sum);
         all += v;
      }

      for(int i = 0; i < PERMUTED.length / 3; i++) {
         Rectangle2D box = chart.boxes().get(i);
         double v = (Double) PERMUTED[3 * i + 2];
         double total = totals.get(PERMUTED[3 * i]);

         assertEquals(total / all * chart.plot0().getWidth(), box.getWidth(), EPS, "width " + i);
         assertEquals(v / total * chart.plot0().getHeight(), box.getHeight(), EPS, "height " + i);
      }
   }

   @Test
   @DisplayName("a string measure gives a zero-height segment and leaves the other groups as if "
      + "the row were removed")
   void stringMeasure() {
      Chart chart = new Chart("East", "x", 30.0, "East", "y", "n/a", "West", "x", 20.0,
                              "West", "y", 40.0, "North", "x", 60.0, "North", "y", 40.0).plot();
      Chart control = new Chart("East", "x", 30.0, "West", "x", 20.0, "West", "y", 40.0,
                                "North", "x", 60.0, "North", "y", 40.0).plot();

      assertNotNull(chart.boxes().get(1));
      assertEquals(0, chart.boxes().get(1).getHeight(), EPS);

      assertSameBox(control.boxes().get(0), chart.boxes().get(0), "East x");
      assertSameBox(control.boxes().get(1), chart.boxes().get(2), "West x");
      assertSameBox(control.boxes().get(2), chart.boxes().get(3), "West y");
      assertSameBox(control.boxes().get(3), chart.boxes().get(4), "North x");
      assertSameBox(control.boxes().get(4), chart.boxes().get(5), "North y");
   }

   @Test
   @DisplayName("without an inner dimension the segments of each group fill the plot height")
   void withoutInnerDimension() {
      Chart chart = new Chart(SORTED);
      chart.element = new MekkoElement();
      chart.element.addDim("R");
      chart.element.addVar("S");
      chart.element.setInnerDimension(null);

      assertDoesNotThrow(() -> chart.plot());

      Map<Integer, Rectangle2D> boxes = chart.boxes();
      assertEquals(6, boxes.size());

      for(int g = 0; g < 3; g++) {
         double sum = boxes.get(2 * g).getHeight() + boxes.get(2 * g + 1).getHeight();

         assertEquals(chart.plot0().getHeight(), sum, EPS, "group " + g);
      }
   }

   @Test
   @DisplayName("a NaN measure is treated like null: the other rows keep their boxes")
   void nanMeasure() {
      Chart withNull = new Chart("East", "x", 30.0, "East", "y", null, "West", "x", 20.0,
                                 "West", "y", 40.0, "North", "x", 60.0, "North", "y", 40.0).plot();
      Chart withNaN = new Chart("East", "x", 30.0, "East", "y", Double.NaN, "West", "x", 20.0,
                                "West", "y", 40.0, "North", "x", 60.0, "North", "y", 40.0).plot();

      assertFalse(withNull.boxes().isEmpty(), "no boxes were drawn");

      for(Map.Entry<Integer, Rectangle2D> entry : withNull.boxes().entrySet()) {
         assertSameBox(entry.getValue(), withNaN.boxes().get(entry.getKey()), "row " + entry.getKey());
      }

      Rectangle2D nan = withNaN.boxes().get(1);
      assertNull(nan, "NaN row is drawn: " + nan);
   }

   static Stream<Arguments> numberTypes() {
      return Stream.of(
         Arguments.of("Integer", (Function<Double, Object>) Double::intValue),
         Arguments.of("Long", (Function<Double, Object>) Double::longValue),
         Arguments.of("BigDecimal", (Function<Double, Object>) BigDecimal::valueOf));
   }

   @ParameterizedTest(name = "{0} values give the same boxes as Double values")
   @MethodSource("numberTypes")
   @DisplayName("number types")
   void numberTypes(String type, Function<Double, Object> convert) {
      Object[] converted = SORTED.clone();

      for(int i = 0; i < converted.length / 3; i++) {
         converted[3 * i + 2] = convert.apply((Double) SORTED[3 * i + 2]);
      }

      Chart expected = new Chart(SORTED).plot();
      Chart actual = new Chart(converted).plot();

      assertEquals(6, actual.boxes().size());

      for(int row = 0; row < 6; row++) {
         assertSameBox(expected.boxes().get(row), actual.boxes().get(row), type + " row " + row);
      }
   }

   @Test
   @DisplayName("equalsContent: same inner dimension is equal; another inner dimension or "
      + "another group dimension is not")
   void equalsContent() {
      MekkoElement base = new MekkoElement("R", "P", "S");

      assertTrue(base.equalsContent(new MekkoElement("R", "P", "S")));
      assertFalse(base.equalsContent(new MekkoElement("R", "Q", "S")));
      assertFalse(base.equalsContent(new MekkoElement("T", "P", "S")));
   }

   @Test
   @DisplayName("the first segment of every group starts at the plot edge")
   void stackStartsPerGroup() {
      Chart chart = new Chart("East", "x", 30.0, "East", "y", 10.0, "East", "z", 20.0,
                              "West", "x", 20.0, "West", "y", 40.0, "West", "z", 10.0).plot();
      double top = chart.plot0().getY();
      double bottom = chart.plot0().getMaxY();

      for(int g = 0; g < 2; g++) {
         double min = Double.MAX_VALUE;
         double max = -Double.MAX_VALUE;

         for(int s = 0; s < 3; s++) {
            Rectangle2D box = chart.boxes().get(3 * g + s);
            min = Math.min(min, box.getY());
            max = Math.max(max, box.getMaxY());
         }

         assertEquals(top, min, EPS, "group " + g + " top");
         assertEquals(bottom, max, EPS, "group " + g + " bottom");
      }
   }
}
