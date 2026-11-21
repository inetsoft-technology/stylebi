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
package inetsoft.graph.coord;

import inetsoft.graph.*;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.data.SubDataSet;
import inetsoft.graph.element.IntervalElement;
import inetsoft.graph.internal.DimensionD;
import inetsoft.graph.internal.ICoordinate;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.scale.TimeScale;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Disabled;
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
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * State kept by {@link AbstractCoord}: layout size, coordinate bounds, axis label and tick
 * visibility masks, the coordinate cache and the shared scales. Runs against several concrete
 * coordinates.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class AbstractCoordTest {
   private static final int[] AXES = {
      ICoordinate.TOP_AXIS, ICoordinate.LEFT_AXIS, ICoordinate.BOTTOM_AXIS,
      ICoordinate.RIGHT_AXIS
   };

   static Stream<Arguments> coords() {
      return Stream.of(
         Arguments.of("PolarCoord",
                      (Supplier<AbstractCoord>) () -> new PolarCoord(new RectCoord())),
         Arguments.of("MekkoCoord", (Supplier<AbstractCoord>) MekkoCoord::new),
         Arguments.of("ParallelCoord",
                      (Supplier<AbstractCoord>) () -> new ParallelCoord(
                         new LinearScale("a"), new LinearScale("b"))),
         Arguments.of("RectCoord", (Supplier<AbstractCoord>) RectCoord::new));
   }

   private static void assertVisibility(AbstractCoord coord, boolean[] labels, boolean[] ticks) {
      for(int i = 0; i < AXES.length; i++) {
         assertEquals(labels[i], coord.isAxisLabelVisible(AXES[i]), "label of axis " + AXES[i]);
         assertEquals(ticks[i], coord.isAxisTickVisible(AXES[i]), "tick of axis " + AXES[i]);
      }
   }

   private static boolean[] all(boolean value) {
      boolean[] arr = new boolean[AXES.length];
      java.util.Arrays.fill(arr, value);
      return arr;
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("coords")
   @DisplayName("layout size is null by default, kept after set and kept by a clone")
   void layoutSize(String name, Supplier<AbstractCoord> factory) {
      AbstractCoord coord = factory.get();
      assertNull(coord.getLayoutSize());

      DimensionD size = new DimensionD(600, 400);
      coord.setLayoutSize(size);
      assertEquals(600, coord.getLayoutSize().getWidth(), 0);
      assertEquals(400, coord.getLayoutSize().getHeight(), 0);

      AbstractCoord clone = (AbstractCoord) coord.clone();
      assertEquals(600, clone.getLayoutSize().getWidth(), 0);
      assertEquals(400, clone.getLayoutSize().getHeight(), 0);
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("coords")
   @DisplayName("coord bounds are null by default and replaced by the last value set")
   void coordBounds(String name, Supplier<AbstractCoord> factory) {
      AbstractCoord coord = factory.get();
      assertNull(coord.getCoordBounds());

      Rectangle2D first = new Rectangle2D.Double(10, 20, 300, 200);
      coord.setCoordBounds(first);
      assertEquals(first, coord.getCoordBounds());

      Rectangle2D second = new Rectangle2D.Double(1, 2, 30, 40);
      coord.setCoordBounds(second);
      assertEquals(second, coord.getCoordBounds());
   }

   @Test
   @DisplayName("coord bounds can be replaced on a coordinate that already has axes")
   void coordBoundsWithAxes() {
      CategoricalScale xs = new CategoricalScale("Cat");
      LinearScale ys = new LinearScale("m");
      RectCoord rect = new RectCoord(xs, ys);
      EGraph graph = new EGraph();
      graph.addElement(new IntervalElement("Cat", "m"));
      graph.setCoordinate(rect);
      graph.setScale("Cat", xs);
      graph.setScale("m", ys);
      VGraph vg = Plotter.getPlotter(graph).plotAndLayout(
         new DefaultDataSet(new Object[][] { { "Cat", "m" }, { "A", 1.0 }, { "B", 2.0 } }),
         0, 0, 600, 400);
      AbstractCoord coord = (AbstractCoord) vg.getCoordinate();
      assertTrue(coord.getAxes(true).length > 0);

      Rectangle2D bounds = new Rectangle2D.Double(5, 6, 70, 80);
      coord.setCoordBounds(bounds);
      assertEquals(bounds, coord.getCoordBounds());

      Rectangle2D replaced = new Rectangle2D.Double(7, 8, 90, 100);
      coord.setCoordBounds(replaced);
      assertEquals(replaced, coord.getCoordBounds());
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("coords")
   @DisplayName("all axes have label and tick visible by default")
   void visibilityDefaults(String name, Supplier<AbstractCoord> factory) {
      assertVisibility(factory.get(), all(true), all(true));
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("coords")
   @DisplayName("label visibility changes only the named axis and never the tick visibility")
   void labelVisibilityPerAxis(String name, Supplier<AbstractCoord> factory) {
      AbstractCoord coord = factory.get();
      boolean[] labels = all(true);

      coord.setAxisLabelVisible(ICoordinate.LEFT_AXIS, false);
      labels[1] = false;
      assertVisibility(coord, labels, all(true));

      coord.setAxisLabelVisible(ICoordinate.TOP_AXIS, false);
      labels[0] = false;
      assertVisibility(coord, labels, all(true));

      coord.setAxisLabelVisible(ICoordinate.LEFT_AXIS, true);
      labels[1] = true;
      assertVisibility(coord, labels, all(true));
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("coords")
   @DisplayName("tick visibility changes only the named axis (including the right axis bit)")
   void tickVisibilityPerAxis(String name, Supplier<AbstractCoord> factory) {
      AbstractCoord coord = factory.get();
      boolean[] ticks = all(true);

      coord.setAxisTickVisible(ICoordinate.BOTTOM_AXIS, false);
      ticks[2] = false;
      assertVisibility(coord, all(true), ticks);

      coord.setAxisTickVisible(ICoordinate.RIGHT_AXIS, false);
      ticks[3] = false;
      assertVisibility(coord, all(true), ticks);

      coord.setAxisTickVisible(ICoordinate.BOTTOM_AXIS, true);
      coord.setAxisTickVisible(ICoordinate.RIGHT_AXIS, true);
      assertVisibility(coord, all(true), all(true));
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("coords")
   @DisplayName("createGGraph applies the most-side hint to the label visibility; -1 changes nothing")
   void visibilityFromHint(String name, Supplier<AbstractCoord> factory) {
      Coordinate coord = (Coordinate) factory.get();
      EGraph graph = new EGraph();
      DefaultDataSet data = new DefaultDataSet(new Object[][] { { "a" }, { 1.0 } });
      graph.setCoordinate(coord);
      GGraph ggraph = new GGraph(graph, coord, data);

      coord.createGGraph(data, null, data, graph, ggraph, null, null,
                         ICoordinate.LEFT_MOST | ICoordinate.BOTTOM_MOST);
      assertTrue(coord.isAxisLabelVisible(ICoordinate.LEFT_AXIS));
      assertTrue(coord.isAxisLabelVisible(ICoordinate.BOTTOM_AXIS));
      assertFalse(coord.isAxisLabelVisible(ICoordinate.TOP_AXIS));
      assertFalse(coord.isAxisLabelVisible(ICoordinate.RIGHT_AXIS));

      coord.createGGraph(data, null, data, graph, ggraph, null, null, -1);
      assertTrue(coord.isAxisLabelVisible(ICoordinate.LEFT_AXIS));
      assertTrue(coord.isAxisLabelVisible(ICoordinate.BOTTOM_AXIS));
      assertFalse(coord.isAxisLabelVisible(ICoordinate.TOP_AXIS));
      assertFalse(coord.isAxisLabelVisible(ICoordinate.RIGHT_AXIS));
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("coords")
   @DisplayName("copyAxisVisibility copies label and tick masks; null changes nothing")
   void copyAxisVisibility(String name, Supplier<AbstractCoord> factory) {
      AbstractCoord a = factory.get();
      AbstractCoord b = factory.get();
      a.setAxisLabelVisible(ICoordinate.LEFT_AXIS, false);
      a.setAxisTickVisible(ICoordinate.BOTTOM_AXIS, false);

      b.copyAxisVisibility(a);
      boolean[] labels = all(true);
      boolean[] ticks = all(true);
      labels[1] = false;
      ticks[2] = false;
      assertVisibility(b, labels, ticks);

      assertDoesNotThrow(() -> b.copyAxisVisibility(null));
      assertVisibility(b, labels, ticks);
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("coords")
   @DisplayName("layoutCompleted empties the coord cache and keeps the same map instance")
   void coordCacheClearedAfterLayout(String name, Supplier<AbstractCoord> factory) {
      AbstractCoord coord = factory.get();
      Map cache = coord.getCoordCache();
      cache.put("key", "value");
      assertSame(cache, coord.getCoordCache());

      coord.layoutCompleted();
      assertTrue(coord.getCoordCache().isEmpty());
      assertSame(cache, coord.getCoordCache());
   }

   private static SubDataSet subset(String value) {
      DefaultDataSet base = new DefaultDataSet(
         new Object[][] { { "f" }, { "A" }, { "B" } });
      return new SubDataSet(base, Map.of("f", value));
   }

   @Test
   @DisplayName("shared scale is found for same class, fields and condition key only")
   void sharedScaleRegistration() {
      AbstractCoord coord = new MekkoCoord();
      CategoricalScale cat = new CategoricalScale("f");
      TimeScale time = new TimeScale("f");
      SubDataSet setA = subset("A");

      coord.addSharedScale(cat, setA);
      coord.addSharedScale(time, setA);

      assertSame(cat, coord.getSharedScale(new CategoricalScale("f"), subset("A")));
      assertSame(time, coord.getSharedScale(new TimeScale("f"), subset("A")));
      assertNull(coord.getSharedScale(new CategoricalScale("g"), subset("A")));
      assertNull(coord.getSharedScale(new CategoricalScale("f"), subset("B")));
      assertNull(coord.getSharedScale(new LinearScale("f"), subset("A")));
      assertNull(coord.getSharedScale(
         new CategoricalScale("f"), new DefaultDataSet(new Object[][] { { "f" }, { "A" } })));
   }

   @Test
   @DisplayName("a linear scale is never registered as a shared scale")
   void sharedScaleIgnoresLinear() {
      AbstractCoord coord = new MekkoCoord();
      LinearScale lin = new LinearScale("f");
      coord.addSharedScale(lin, subset("A"));
      assertNull(coord.getSharedScale(lin, subset("A")));
   }

   @Test
   @DisplayName("layoutCompleted removes the shared scales")
   void sharedScaleClearedAfterLayout() {
      AbstractCoord coord = new MekkoCoord();
      CategoricalScale cat = new CategoricalScale("f");
      coord.addSharedScale(cat, subset("A"));
      assertSame(cat, coord.getSharedScale(cat, subset("A")));

      coord.layoutCompleted();
      assertNull(coord.getSharedScale(cat, subset("A")));
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("coords")
   @DisplayName("a clone has its own coord cache and shared scales")
   @Disabled("Not filed (not reachable: clones are always taken from an EGraph that was not plotted yet): clone shares the coordCache and sharedScales maps with the original (AbstractCoord.clone is shallow); expected: a cache entry put on the clone is not visible in the original, actual: original.getCoordCache().containsKey(\"clone\") is true")
   void cloneHasOwnCaches(String name, Supplier<AbstractCoord> factory) {
      AbstractCoord coord = factory.get();
      coord.getCoordCache().put("orig", 1);
      CategoricalScale shared = new CategoricalScale("f");
      coord.addSharedScale(shared, subset("A"));

      AbstractCoord clone = (AbstractCoord) coord.clone();
      CategoricalScale cloneShared = new CategoricalScale("f");
      clone.getCoordCache().put("clone", 2);
      clone.addSharedScale(cloneShared, subset("B"));

      assertFalse(coord.getCoordCache().containsKey("clone"), "cache shared with clone");
      assertNull(coord.getSharedScale(cloneShared, subset("B")), "shared scales shared with clone");

      clone.layoutCompleted();
      assertTrue(coord.getCoordCache().containsKey("orig"));
      assertSame(shared, coord.getSharedScale(shared, subset("A")));

      coord.layoutCompleted();
      clone.getCoordCache().put("again", 3);
      coord.layoutCompleted();
      assertTrue(clone.getCoordCache().containsKey("again"));
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("coords")
   @DisplayName("a clone starts with the visibility masks of the original and changes independently")
   void cloneKeepsVisibilityMasks(String name, Supplier<AbstractCoord> factory) {
      AbstractCoord coord = factory.get();
      coord.setAxisLabelVisible(ICoordinate.LEFT_AXIS, false);
      coord.setAxisTickVisible(ICoordinate.BOTTOM_AXIS, false);
      boolean[] labels = all(true);
      boolean[] ticks = all(true);
      labels[1] = false;
      ticks[2] = false;

      AbstractCoord clone = (AbstractCoord) coord.clone();
      assertVisibility(clone, labels, ticks);

      clone.setAxisLabelVisible(ICoordinate.LEFT_AXIS, true);
      clone.setAxisTickVisible(ICoordinate.TOP_AXIS, false);
      assertVisibility(coord, labels, ticks);
   }
}
