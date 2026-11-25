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
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.data.SortedDataSet;
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

import java.util.Comparator;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the flags, equalsContent and the forced left-to-right sort of StackableElement, which
 * every line, area and stacked point chart relies on.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class StackableElementTest {
   static Stream<Arguments> elements() {
      return Stream.of(
         Arguments.of("line", (Supplier<StackableElement>) LineElement::new),
         Arguments.of("point", (Supplier<StackableElement>) PointElement::new),
         Arguments.of("area", (Supplier<StackableElement>) AreaElement::new));
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("elements")
   @DisplayName("stack flags default to not grouped and negative stacked, setters round trip")
   void flagDefaultsAndSetters(String name, Supplier<StackableElement> factory) {
      StackableElement elem = factory.get();
      assertFalse(elem.isStackGroup());
      assertTrue(elem.isStackNegative());

      elem.setStackGroup(true);
      elem.setStackNegative(false);
      assertTrue(elem.isStackGroup());
      assertFalse(elem.isStackNegative());

      elem.setStackGroup(false);
      elem.setStackNegative(true);
      assertFalse(elem.isStackGroup());
      assertTrue(elem.isStackNegative());
   }

   @Test
   @DisplayName("equalsContent is true for equal flags, both ways and for a clone")
   void equalsContentSameFlags() {
      LineElement a = new LineElement("x", "y");
      LineElement b = new LineElement("x", "y");
      a.setStackGroup(true);
      b.setStackGroup(true);

      assertTrue(a.equalsContent(b));
      assertTrue(b.equalsContent(a));
      assertTrue(a.equalsContent(a.clone()));
   }

   @Test
   @DisplayName("equalsContent is false when stackGroup differs, both ways")
   void equalsContentStackGroupDiffers() {
      LineElement a = new LineElement("x", "y");
      LineElement b = new LineElement("x", "y");
      b.setStackGroup(true);

      assertFalse(a.equalsContent(b));
      assertFalse(b.equalsContent(a));
   }

   @Test
   @DisplayName("equalsContent is false when stackNegative differs, both ways")
   void equalsContentStackNegativeDiffers() {
      LineElement a = new LineElement("x", "y");
      LineElement b = new LineElement("x", "y");
      b.setStackNegative(false);

      assertFalse(a.equalsContent(b));
      assertFalse(b.equalsContent(a));
   }

   @Test
   @DisplayName("sortData forces x ascending by the scale position of a numeric x")
   void sortDataNumericLeftToRight() {
      DefaultDataSet ds = new DefaultDataSet(new Object[][] {
         { "x", "y" }, { 3.0, 30.0 }, { 1.0, 10.0 }, { 2.0, 20.0 } });
      LinearScale xs = new LinearScale("x");
      xs.setMin(0);
      xs.setMax(4);
      LineElement elem = new LineElement("x", "y");

      SortedDataSet sdata = elem.sortData(ds, graph(elem, ds, xs, yScale()));

      assertNotNull(sdata);
      assertArrayEquals(new int[] { 1, 2, 0 }, baseRows(sdata));
      assertEquals(1.0, ((Number) sdata.getData("x", 0)).doubleValue());
      assertEquals(2.0, ((Number) sdata.getData("x", 1)).doubleValue());
      assertEquals(3.0, ((Number) sdata.getData("x", 2)).doubleValue());
   }

   @Test
   @DisplayName("sortData follows the order of the category scale, not the value order")
   void sortDataUsesScaleOrder() {
      DefaultDataSet order = new DefaultDataSet(new Object[][] {
         { "Cat" }, { "B" }, { "A" }, { "C" } });
      DefaultDataSet ds = new DefaultDataSet(new Object[][] {
         { "Cat", "y" }, { "A", 1.0 }, { "B", 2.0 }, { "C", 3.0 } });
      CategoricalScale xs = new CategoricalScale("Cat");
      xs.init(order);
      LineElement elem = new LineElement("Cat", "y");

      SortedDataSet sdata = elem.sortData(ds, graph(elem, ds, xs, yScale()));

      assertArrayEquals(new int[] { 1, 0, 2 }, baseRows(sdata));
   }

   @Test
   @DisplayName("sortData: a category the scale cannot map sorts first (pinned behavior)")
   void sortDataUnmappableXSortsFirst() {
      DefaultDataSet known = new DefaultDataSet(new Object[][] {
         { "Cat" }, { "A" }, { "B" } });
      DefaultDataSet ds = new DefaultDataSet(new Object[][] {
         { "Cat", "y" }, { "A", 1.0 }, { "B", 2.0 }, { "Z", 3.0 } });
      CategoricalScale xs = new CategoricalScale("Cat");
      xs.init(known);
      LineElement elem = new LineElement("Cat", "y");

      SortedDataSet sdata = elem.sortData(ds, graph(elem, ds, xs, yScale()));

      assertArrayEquals(new int[] { 2, 0, 1 }, baseRows(sdata));
   }

   @Test
   @DisplayName("sortData keeps a comparator set on the data set for x")
   void sortDataDataSetComparatorWins() {
      DefaultDataSet ds = new DefaultDataSet(new Object[][] {
         { "x", "y" }, { 1.0, 10.0 }, { 2.0, 20.0 }, { 3.0, 30.0 } });
      ds.setComparator("x", descending());
      LinearScale xs = new LinearScale("x");
      xs.setMin(0);
      xs.setMax(4);
      LineElement elem = new LineElement("x", "y");

      SortedDataSet sdata = elem.sortData(ds, graph(elem, ds, xs, yScale()));

      assertArrayEquals(new int[] { 2, 1, 0 }, baseRows(sdata));
   }

   @Test
   @DisplayName("sortData does not sort a radar element (dims only, no var, no comparator)")
   void sortDataRadarNotSorted() {
      DefaultDataSet ds = new DefaultDataSet(new Object[][] {
         { "d1", "d2" }, { 3.0, 30.0 }, { 1.0, 10.0 } });
      LinearScale s1 = new LinearScale("d1");
      s1.setMin(0);
      s1.setMax(10);
      LineElement elem = new LineElement();
      elem.addDim("d1");
      elem.addDim("d2");

      assertNull(elem.sortData(ds, graph(elem, ds, s1, yScale())));
   }

   @Test
   @DisplayName("sortData breaks a tie in x by a column that has a comparator")
   void sortDataTieBrokenByComparatorColumn() {
      DefaultDataSet ds = new DefaultDataSet(new Object[][] {
         { "x", "g", "y" }, { 1.0, "b", 10.0 }, { 1.0, "a", 20.0 } });
      ds.setComparator("g", Comparator.naturalOrder());
      LinearScale xs = new LinearScale("x");
      xs.setMin(0);
      xs.setMax(2);
      LineElement elem = new LineElement("x", "y");

      SortedDataSet sdata = elem.sortData(ds, graph(elem, ds, xs, yScale()));

      assertArrayEquals(new int[] { 1, 0 }, baseRows(sdata));
   }

   @Test
   @DisplayName("sortData orders the rows of one x by the comparator of a categorical color field")
   void sortDataCategoricalFrameField() {
      DefaultDataSet ds = new DefaultDataSet(new Object[][] {
         { "x", "S", "y" }, { 1.0, "a", 10.0 }, { 1.0, "b", 20.0 }, { 1.0, "c", 30.0 } });
      ds.setComparator("S", descending());
      LinearScale xs = new LinearScale("x");
      xs.setMin(0);
      xs.setMax(2);
      LineElement elem = new LineElement("x", "y");
      elem.setColorFrame(new CategoricalColorFrame("S"));

      SortedDataSet sdata = elem.sortData(ds, graph(elem, ds, xs, yScale()));

      assertArrayEquals(new int[] { 2, 1, 0 }, baseRows(sdata));
   }

   private static Comparator<Object> descending() {
      return (a, b) -> ((Comparable) b).compareTo(a);
   }

   private static Scale yScale() {
      LinearScale ys = new LinearScale("y");
      ys.setMin(0);
      ys.setMax(100);
      return ys;
   }

   private static GGraph graph(GraphElement elem, DefaultDataSet ds, Scale xs, Scale ys) {
      EGraph egraph = new EGraph();
      egraph.addElement(elem);
      RectCoord coord = new RectCoord(xs, ys);
      egraph.setCoordinate(coord);
      return egraph.createGGraph(coord, ds);
   }

   private static int[] baseRows(SortedDataSet sdata) {
      int[] rows = new int[sdata.getRowCount()];

      for(int i = 0; i < rows.length; i++) {
         rows[i] = sdata.getBaseRow(i);
      }

      return rows;
   }
}
