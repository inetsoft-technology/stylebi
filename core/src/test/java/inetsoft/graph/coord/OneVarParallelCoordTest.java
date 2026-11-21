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

import inetsoft.graph.data.DefaultDataSet;
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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.geom.Point2D;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Parallel coordinate with one measure split into axes by a group dimension.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class OneVarParallelCoordTest {
   private static final double EPS = 1e-6;

   private static DefaultDataSet data(Object... groupAndValue) {
      Object[][] rows = new Object[groupAndValue.length / 2 + 1][];
      rows[0] = new Object[] { "g", "m" };

      for(int i = 0; i < groupAndValue.length / 2; i++) {
         rows[i + 1] = new Object[] { groupAndValue[2 * i], groupAndValue[2 * i + 1] };
      }

      return new DefaultDataSet(rows);
   }

   private static DefaultDataSet abc() {
      return data("A", 10.0, "A", 20.0, "B", 30.0, "B", 40.0, "C", 50.0);
   }

   private static LinearScale range() {
      LinearScale scale = new LinearScale("m");
      scale.setMin(0);
      scale.setMax(100);
      return scale;
   }

   private static OneVarParallelCoord coord(DefaultDataSet data) {
      OneVarParallelCoord coord = new OneVarParallelCoord("m", "g", range());
      coord.init(data);
      return coord;
   }

   private static OneVarParallelCoord coordNoNull(DefaultDataSet data) {
      OneVarParallelCoord coord = new OneVarParallelCoord("m", "g", range());
      coord.getAxisLabelScale().setScaleOption(Scale.NO_NULL);
      coord.init(data);
      return coord;
   }

   private static void assertFinite(Point2D pt) {
      assertNotNull(pt);
      assertTrue(Double.isFinite(pt.getX()), "x " + pt.getX());
      assertTrue(Double.isFinite(pt.getY()), "y " + pt.getY());
   }

   @Test
   @DisplayName("after init there is one parallel scale per group, label scale first in getScales")
   void afterInit() {
      OneVarParallelCoord coord = coord(abc());

      assertEquals(3, coord.getParallelScales().length);
      assertEquals(4, coord.getScales().length);
      assertSame(coord.getAxisLabelScale(), coord.getScales()[0]);
      assertEquals(1, coord.getDimCount());
   }

   @Test
   @DisplayName("tuple {group index + 0.5, value} lands at the group slot centre and scaled height")
   void positionPerGroup() {
      OneVarParallelCoord coord = coord(abc());
      double[] expectedX = { 1000.0 / 6, 500, 5000.0 / 6 };

      for(int i = 0; i < 3; i++) {
         Point2D pt = coord.getPosition(new double[] { i + 0.5, 40 });
         assertEquals(expectedX[i], pt.getX(), EPS);
         assertEquals(400, pt.getY(), EPS);
      }
   }

   @Test
   @DisplayName("control: the group index is the integer part of the tuple entry")
   void indexTruncation() {
      OneVarParallelCoord coord = coord(abc());
      Point2D base = coord.getPosition(new double[] { 1.0, 40 });

      for(double idx : new double[] { 1.5, 1.9 }) {
         Point2D pt = coord.getPosition(new double[] { idx, 40 });
         assertEquals(base.getX(), pt.getX(), EPS);
         assertEquals(base.getY(), pt.getY(), EPS);
      }
   }

   @Test
   @DisplayName("a group index past the last group is rejected")
   void indexTooLarge() {
      OneVarParallelCoord coord = coord(abc());

      for(double idx : new double[] { 3.5, 3.0 }) {
         RuntimeException ex = assertThrows(
            RuntimeException.class, () -> coord.getPosition(new double[] { idx, 40 }));
         assertTrue(ex.getMessage().contains("Element not supported by ParallelCoord"));
      }
   }

   @Test
   @DisplayName("a negative group index gives no point and no exception")
   void negativeIndex() {
      assertNull(coord(abc()).getPosition(new double[] { -1, 40 }));
   }

   @Test
   @DisplayName("a NaN group index is no group: no point, like index -1")
   @Disabled("Not filed (not reachable: NaN tuples are filtered at GraphElement.java:585): expected null (NaN is no group, like index -1), actual Point2D(166.67, 400.0): (int) NaN becomes axis 0")
   void nanIndex() {
      assertNull(coord(abc()).getPosition(new double[] { Double.NaN, 40 }));
   }

   @Test
   @DisplayName("parallel axes share one value range that covers the data")
   void sharedRange() {
      OneVarParallelCoord coord = new OneVarParallelCoord("m", "g", new LinearScale("m"));
      coord.init(abc());
      Scale[] scales = coord.getParallelScales();

      assertEquals(3, scales.length);

      for(Scale scale : scales) {
         assertEquals(scales[0].getMin(), scale.getMin(), EPS);
         assertEquals(scales[0].getMax(), scale.getMax(), EPS);
      }

      assertTrue(scales[0].getMin() <= 10);
      assertTrue(scales[0].getMax() >= 50);
   }

   @Test
   @DisplayName("a null group keeps its own axis and every group index gives a finite point")
   void nullGroupHasAxis() {
      OneVarParallelCoord coord = coord(data(null, 10.0, "A", 20.0, "B", 30.0));

      assertEquals(3, coord.getParallelScales().length);
      assertEquals(3, coord.getAxisLabelScale().getValues().length);

      for(int i = 0; i < 3; i++) {
         assertFinite(coord.getPosition(new double[] { i + 0.5, 20 }));
      }
   }

   @Test
   @DisplayName("NO_NULL drops the null group: labels A,B and two axes")
   void noNullDropsNullGroup() {
      OneVarParallelCoord coord = coordNoNull(data(null, 10.0, "A", 20.0, "B", 30.0));

      assertArrayEquals(new Object[] { "A", "B" }, coord.getAxisLabelScale().getValues());
      assertEquals(2, coord.getParallelScales().length);

      for(int i = 0; i < 2; i++) {
         assertFinite(coord.getPosition(new double[] { i + 0.5, 20 }));
      }
   }

   @Test
   @DisplayName("NO_NULL with only null groups: one axis per label scale value")
   @Disabled("Bug #82034: Radar chart with Ignore Null and an all-null group fails")
   void noNullOnlyNullGroups() {
      OneVarParallelCoord coord = coordNoNull(data(null, 10.0, null, 20.0));

      assertEquals(coord.getAxisLabelScale().getValues().length,
                   coord.getParallelScales().length);
   }

   @Test
   @DisplayName("a single group gives one axis at the centre")
   void singleGroup() {
      OneVarParallelCoord coord = coord(data("A", 10.0, "A", 20.0));

      assertEquals(1, coord.getParallelScales().length);
      assertEquals(500, coord.getPosition(new double[] { 0.5, 40 }).getX(), EPS);
   }

   @Test
   @DisplayName("header-only data: no exception and no parallel scales")
   void emptyData() {
      OneVarParallelCoord coord = new OneVarParallelCoord("m", "g", range());

      assertDoesNotThrow(() -> coord.init(data()));
      assertEquals(0, coord.getParallelScales().length);
   }

   @Test
   @DisplayName("clone has its own sub scales and no axes; changing the clone leaves the original")
   void cloneIndependence() {
      OneVarParallelCoord coord = coord(abc());
      OneVarParallelCoord clone = (OneVarParallelCoord) coord.clone(false);
      Scale[] orig = coord.getParallelScales();
      Scale[] copy = clone.getParallelScales();

      assertEquals(3, copy.length);

      for(int i = 0; i < 3; i++) {
         assertNotSame(orig[i], copy[i]);
      }

      double max0 = orig[0].getMax();
      ((LinearScale) copy[0]).setMax(55);
      assertEquals(max0, orig[0].getMax(), EPS);

      Scale first = orig[1];
      copy[1] = new LinearScale("zzz");
      assertSame(first, coord.getParallelScales()[1]);
      assertEquals(0, clone.getAxes(false).length);
   }
}
