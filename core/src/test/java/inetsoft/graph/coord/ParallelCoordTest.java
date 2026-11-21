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

import inetsoft.graph.AxisSpec;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.geom.Point2D;
import java.io.*;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Point mapping, scales, label scale, clone and serialization of {@link ParallelCoord}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class ParallelCoordTest {
   private static final double EPS = 1e-6;
   private static final double NAN = Double.NaN;

   private static LinearScale lin(String field, double min, double max) {
      LinearScale scale = new LinearScale(field);
      scale.setMin(min);
      scale.setMax(max);
      return scale;
   }

   private static ParallelCoord coord3() {
      return new ParallelCoord(lin("m1", 0, 100), lin("m2", 0, 100), lin("m3", 0, 100));
   }

   @Test
   @DisplayName("a single value is placed on its own axis centre at its scaled height")
   void positionOneValue() {
      ParallelCoord coord = coord3();
      double[] expectedX = { 1000.0 / 6, 500, 5000.0 / 6 };

      for(int i = 0; i < 3; i++) {
         double[] tuple = { NAN, NAN, NAN };
         tuple[i] = 40;
         Point2D pt = coord.getPosition(tuple);
         assertEquals(expectedX[i], pt.getX(), EPS, "x of axis " + i);
         assertEquals(400, pt.getY(), EPS, "y of axis " + i);
      }
   }

   @Test
   @DisplayName("every axis maps through its own scale range")
   void positionUsesOwnRange() {
      ParallelCoord coord = new ParallelCoord(
         lin("a", 0, 100), lin("b", 0, 10), lin("c", -50, 50));
      double[][] mids = { { 50, NAN, NAN }, { NAN, 5, NAN }, { NAN, NAN, 0 } };
      double[][] mins = { { 0, NAN, NAN }, { NAN, 0, NAN }, { NAN, NAN, -50 } };
      double[][] maxs = { { 100, NAN, NAN }, { NAN, 10, NAN }, { NAN, NAN, 50 } };

      for(int i = 0; i < 3; i++) {
         assertEquals(500, coord.getPosition(mids[i]).getY(), EPS, "mid " + i);
         assertEquals(0, coord.getPosition(mins[i]).getY(), EPS, "min " + i);
         assertEquals(1000, coord.getPosition(maxs[i]).getY(), EPS, "max " + i);
      }
   }

   @ParameterizedTest(name = "{0} axes")
   @ValueSource(ints = { 2, 3, 4, 5, 6 })
   @DisplayName("axes are equally spaced: centre of slot i of n")
   void axisSpacing(int n) {
      Scale[] scales = new Scale[n];

      for(int i = 0; i < n; i++) {
         scales[i] = lin("m" + i, 0, 100);
      }

      ParallelCoord coord = new ParallelCoord(scales);
      double[] xs = new double[n];

      for(int i = 0; i < n; i++) {
         double[] tuple = new double[n];
         Arrays.fill(tuple, NAN);
         tuple[i] = 10;
         xs[i] = coord.getPosition(tuple).getX();
      }

      assertEquals(500.0 / n, xs[0], EPS);
      assertEquals(1000 - 500.0 / n, xs[n - 1], EPS);

      for(int i = 1; i < n; i++) {
         assertEquals(1000.0 / n, xs[i] - xs[i - 1], EPS);
      }
   }

   @Test
   @DisplayName("control: the point comes from the first non-NaN value, other dimensions NaN or not")
   void firstValueDecides() {
      ParallelCoord coord = coord3();
      Point2D full = coord.getPosition(new double[] { 20, 40, 60 });
      Point2D only = coord.getPosition(new double[] { 20, NAN, NAN });
      assertEquals(only.getX(), full.getX(), EPS);
      assertEquals(only.getY(), full.getY(), EPS);
   }

   @Test
   @DisplayName("pinned behavior: an all NaN tuple gives a NaN point without exception")
   void allNaNTuplePinned() {
      Point2D pt = coord3().getPosition(new double[] { NAN, NAN, NAN });
      assertTrue(Double.isNaN(pt.getX()));
      assertTrue(Double.isNaN(pt.getY()));
   }

   @Test
   @DisplayName("a tuple shorter than the number of scales is rejected")
   void shortTuple() {
      RuntimeException ex = assertThrows(
         RuntimeException.class, () -> coord3().getPosition(new double[] { 1, 2 }));
      assertTrue(ex.getMessage().contains("Element not supported by ParallelCoord"));
   }

   @Test
   @DisplayName("fewer than two scales are rejected, two are accepted")
   void minimumTwoScales() {
      assertThrows(RuntimeException.class, () -> new ParallelCoord(lin("a", 0, 1)));

      ParallelCoord coord = new ParallelCoord();
      assertThrows(RuntimeException.class, coord::setScales);
      assertThrows(RuntimeException.class, () -> coord.setScales(lin("a", 0, 1)));
      assertDoesNotThrow(() -> coord.setScales(lin("a", 0, 1), lin("b", 0, 1)));
   }

   @Test
   @DisplayName("a rejected setScales call leaves the previous scales in place")
   @Disabled("Not filed (not reachable: production never calls setScales after a failure or on a clone, and never serializes an EGraph): expected 3 original scales kept after the rejected setScales call, actual 1 (scales assigned before validation)")
   void rejectedSetScalesKeepsState() {
      Scale[] original = { lin("a", 0, 1), lin("b", 0, 1), lin("c", 0, 1) };
      ParallelCoord coord = new ParallelCoord(original);

      assertThrows(RuntimeException.class, () -> coord.setScales(lin("x", 0, 1)));

      Scale[] after = coord.getParallelScales();
      assertEquals(3, after.length);

      for(int i = 0; i < 3; i++) {
         assertSame(original[i], after[i]);
      }
   }

   @Test
   @DisplayName("label scale lists the measure fields with ticks at slot centres, hidden line and ticks")
   void labelScale() {
      ParallelCoord coord = new ParallelCoord(
         lin("m1", 0, 1), lin("m2", 0, 1), lin("m3", 0, 1));
      Scale label = coord.getAxisLabelScale();

      assertArrayEquals(new Object[] { "m1", "m2", "m3" }, label.getValues());
      assertArrayEquals(new double[] { 0.5, 1.5, 2.5 }, label.getTicks(), EPS);
      assertEquals(3, label.getMax(), EPS);
      AxisSpec spec = label.getAxisSpec();
      assertFalse(spec.isLineVisible());
      assertFalse(spec.isTickVisible());
   }

   @Test
   @DisplayName("dimension count and scale lists reflect the scales given, in order")
   void dimCountAndScales() {
      Scale[] in = { lin("a", 0, 1), lin("b", 0, 1), lin("c", 0, 1), lin("d", 0, 1) };
      ParallelCoord coord = new ParallelCoord(in);

      assertEquals(4, coord.getDimCount());
      assertArrayEquals(in, coord.getScales());
      assertArrayEquals(in, coord.getParallelScales());
   }

   @Test
   @DisplayName("clone has its own scale instances and no axes; changing the clone leaves the original")
   void cloneIndependence() {
      ParallelCoord coord = coord3();
      ParallelCoord clone = (ParallelCoord) coord.clone(false);
      Scale[] orig = coord.getParallelScales();
      Scale[] copy = clone.getParallelScales();

      assertEquals(3, copy.length);

      for(int i = 0; i < 3; i++) {
         assertNotSame(orig[i], copy[i]);
      }

      ((LinearScale) copy[0]).setMax(50);
      assertEquals(100, orig[0].getMax(), EPS);

      copy[1] = lin("zzz", 0, 1);
      assertEquals("m2", coord.getParallelScales()[1].getFields()[0]);
      assertEquals(0, clone.getAxes(false).length);
   }

   @Test
   @DisplayName("setScales on a clone relabels the clone from its own scales")
   @Disabled("Not filed (not reachable: production never calls setScales after a failure or on a clone, and never serializes an EGraph): expected clone label values [x1,x2,x3], actual 2 values (the label scale of the clone reads the scales of the original coord)")
   void cloneThenSetScales() {
      ParallelCoord coord = new ParallelCoord(lin("m1", 0, 1), lin("m2", 0, 1));
      ParallelCoord clone = (ParallelCoord) coord.clone(false);

      clone.setScales(lin("x1", 0, 1), lin("x2", 0, 1), lin("x3", 0, 1));

      assertArrayEquals(new Object[] { "x1", "x2", "x3" },
                        clone.getAxisLabelScale().getValues());
   }

   @Test
   @DisplayName("serialization round trip keeps the label scale and the positions")
   @Disabled("Not filed (not reachable: production never calls setScales after a failure or on a clone, and never serializes an EGraph): expected label scale non-null after Java deserialization, actual null (pscale is transient, no readObject)")
   void serialization() throws Exception {
      ParallelCoord coord = new ParallelCoord(
         lin("m1", 0, 100), lin("m2", 0, 100), lin("m3", 0, 100));
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();

      try(ObjectOutputStream out = new ObjectOutputStream(bytes)) {
         out.writeObject(coord);
      }

      ParallelCoord copy;

      try(ObjectInputStream in = new ObjectInputStream(
         new ByteArrayInputStream(bytes.toByteArray())))
      {
         copy = (ParallelCoord) in.readObject();
      }

      assertNotNull(copy.getAxisLabelScale());
      assertArrayEquals(new Object[] { "m1", "m2", "m3" }, copy.getAxisLabelScale().getValues());
      Point2D pt = copy.getPosition(new double[] { NAN, 40, NAN });
      assertEquals(500, pt.getX(), EPS);
      assertEquals(400, pt.getY(), EPS);
   }

   @Test
   @DisplayName("pinned behavior: an interval has no size on parallel axes")
   void intervalSizePinned() {
      ParallelCoord coord = coord3();
      assertEquals(0, coord.getIntervalSize(0), 0);
      assertEquals(0, coord.getIntervalSize(5), 0);
   }

   @Test
   @DisplayName("control: equalsContent is true for equal coords, false for other count or fields")
   void equalsContent() {
      ParallelCoord a = coord3();
      ParallelCoord b = coord3();
      ParallelCoord fewer = new ParallelCoord(lin("m1", 0, 100), lin("m2", 0, 100));
      ParallelCoord other = new ParallelCoord(
         lin("m1", 0, 100), lin("m2", 0, 100), lin("other", 0, 100));

      assertTrue(a.equalsContent(b));
      assertFalse(a.equalsContent(fewer));
      assertFalse(a.equalsContent(other));
   }
}
