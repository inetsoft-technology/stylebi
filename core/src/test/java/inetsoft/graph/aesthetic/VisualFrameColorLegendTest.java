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
package inetsoft.graph.aesthetic;

import inetsoft.graph.LegendSpec;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.scale.LinearScale;
import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.text.DecimalFormat;
import java.text.Format;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the legend side of {@link VisualFrame} as seen through a colour frame: reduction of
 * the legend values, title, text format merged from the scale, labels, validity, share ids,
 * clone independence and the consistency of legend colours with value colours.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class VisualFrameColorLegendTest {
   /** Linear scale (0..100) whose legend values are fixed. */
   private static LinearScale scaleWithValues(Object... fixed) {
      return new LinearScale(0, 100) {
         @Override
         public Object[] getValues() {
            return fixed;
         }
      };
   }

   private static GradientColorFrame frameWithValues(Object... fixed) {
      GradientColorFrame f = new GradientColorFrame("v");
      f.setScale(scaleWithValues(fixed));
      return f;
   }

   private static Object[] range(int from, int to, int step) {
      return Stream.iterate(from, i -> i <= to, i -> i + step).toArray();
   }

   private static void assertNumbers(double[] expected, Object[] actual) {
      assertEquals(expected.length, actual.length, "length");

      for(int i = 0; i < expected.length; i++) {
         assertEquals(expected[i], ((Number) actual[i]).doubleValue(), 0, "value " + i);
      }
   }

   static Stream<Arguments> reducedValues() {
      return Stream.of(
         // up to six values are unchanged
         Arguments.of(range(0, 5, 1), new double[] { 0, 1, 2, 3, 4, 5 }),
         // odd count: every second value
         Arguments.of(range(0, 60, 10), new double[] { 0, 20, 40, 60 }),
         Arguments.of(range(0, 8, 1), new double[] { 0, 2, 4, 6, 8 }),
         // even count: ends kept, the pairs in between averaged
         Arguments.of(range(0, 70, 10), new double[] { 0, 15, 35, 55, 70 }),
         Arguments.of(range(0, 7, 1), new double[] { 0, 1.5, 3.5, 5.5, 7 }),
         Arguments.of(range(0, 9, 1), new double[] { 0, 1.5, 3.5, 5.5, 7.5, 9 }));
   }

   @ParameterizedTest
   @MethodSource("reducedValues")
   @DisplayName("legend values of a linear scale are reduced when there are more than six")
   void legendValuesAreReduced(Object[] values, double[] expected) {
      assertNumbers(expected, frameWithValues(values).getValues());
   }

   @ParameterizedTest
   @org.junit.jupiter.params.provider.ValueSource(ints = { 7, 8, 9, 10, 11, 12, 15, 20 })
   @DisplayName("reduction keeps the first and last value, order, and shortens the list")
   void reductionKeepsEndsAndOrder(int n) {
      Object[] values = range(0, (n - 1) * 10, 10);
      Object[] reduced = frameWithValues(values).getValues();
      assertTrue(reduced.length < values.length);
      assertEquals(0, ((Number) reduced[0]).doubleValue(), 0);
      assertEquals((n - 1) * 10, ((Number) reduced[reduced.length - 1]).doubleValue(), 0);

      for(int i = 1; i < reduced.length; i++) {
         assertTrue(((Number) reduced[i]).doubleValue() > ((Number) reduced[i - 1]).doubleValue());
      }
   }

   @Test
   void titleIsFrameTypeThenFieldThenLegendTitle() {
      GradientColorFrame f = new GradientColorFrame();
      assertEquals("Color", f.getTitle());
      f.setField("sales");
      assertEquals("sales", f.getTitle());
      LegendSpec spec = new LegendSpec();
      spec.setTitle("My Title");
      f.setLegendSpec(spec);
      assertEquals("My Title", f.getTitle());
   }

   private static GradientColorFrame frameWithAxisFormat(Format fmt) {
      LinearScale scale = new LinearScale(0, 100);
      scale.getAxisSpec().getTextSpec().setFormat(fmt);
      GradientColorFrame f = new GradientColorFrame("v");
      f.setScale(scale);
      return f;
   }

   @Test
   @DisplayName("legend text format is merged from the scale format")
   @Disabled("Bug #82073: Frame legend ignores the scale format on the first getLegendSpec() call")
   void legendFormatComesFromScale() {
      Format fmt = new DecimalFormat("0.0");
      GradientColorFrame f = frameWithAxisFormat(fmt);
      assertEquals(fmt, f.getLegendSpec().getTextSpec().getFormat());
   }

   @Test
   @DisplayName("legend text format of a clone is the scale format on the first call and later")
   @Disabled("Bug #82073: Frame legend ignores the scale format on the first getLegendSpec() call")
   void legendFormatOfCloneDoesNotDependOnCallHistory() {
      Format fmt = new DecimalFormat("0.0");
      GradientColorFrame f = frameWithAxisFormat(fmt);
      GradientColorFrame copy1 = (GradientColorFrame) f.clone();
      Format first = copy1.getLegendSpec().getTextSpec().getFormat();
      GradientColorFrame copy2 = (GradientColorFrame) f.clone();
      Format later = copy2.getLegendSpec().getTextSpec().getFormat();
      assertEquals(fmt, first);
      assertEquals(fmt, later);
   }

   @Test
   void labelsAreTheValuesWithoutTextFrame() {
      GradientColorFrame f = frameWithValues(0, 50, 100);
      Object[] values = f.getValues();
      Object[] labels = f.getLabels();
      assertEquals(3, values.length);
      assertEquals(values.length, labels.length);

      for(int i = 0; i < values.length; i++) {
         assertSame(values[i], labels[i]);
      }
   }

   @Test
   void isValidNeedsAnInitialisedScale() {
      assertFalse(new GradientColorFrame("v").isValid());

      GradientColorFrame flat = new GradientColorFrame("v");
      flat.setScale(new LinearScale(5, 5));
      assertFalse(flat.isValid());

      GradientColorFrame ok = new GradientColorFrame("v");
      ok.setScale(new LinearScale(0, 100));
      assertTrue(ok.isValid());
   }

   @Test
   void shareIdIgnoresValueOrderUniqueIdDoesNot() {
      GradientColorFrame a = frameWithValues(1, 2, 3);
      GradientColorFrame b = frameWithValues(3, 2, 1);
      assertEquals(a.getShareId(), b.getShareId());
      assertNotEquals(a.getUniqueId(), b.getUniqueId());
   }

   @Test
   void cloneDoesNotShareLegendOrScaleInstance() {
      GradientColorFrame f = new GradientColorFrame("v");
      LinearScale scale = new LinearScale(0, 100);
      f.setScale(scale);
      LegendSpec spec = new LegendSpec();
      spec.setTitle("Original");
      f.setLegendSpec(spec);

      GradientColorFrame copy = (GradientColorFrame) f.clone();
      assertNotSame(scale, copy.getScale());
      LegendSpec copySpec = new LegendSpec();
      copySpec.setTitle("Changed");
      copy.setLegendSpec(copySpec);

      assertEquals("Original", f.getLegendSpec().getTitle());
      assertSame(scale, f.getScale());
   }

   @Test
   @DisplayName("clone shares the scale range by design (facets), clone(false) does not")
   void cloneSharesScaleRangeOnlyWithDefaultClone() {
      GradientColorFrame f = new GradientColorFrame("v");
      LinearScale scale = new LinearScale(0, 100);
      f.setScale(scale);
      GradientColorFrame copy = (GradientColorFrame) f.clone();
      ((LinearScale) copy.getScale()).setMax(200);
      assertEquals(200, scale.getMax(), 0);

      LinearScale scale2 = new LinearScale(0, 100);
      LinearScale independent = scale2.clone(false);
      independent.setMax(200);
      assertEquals(100, scale2.getMax(), 0);
      assertEquals(200, independent.getMax(), 0);
   }

   @Test
   @DisplayName("legend item colours equal the colours of the same values")
   void legendColoursMatchValueColours() {
      DataSet data = new DefaultDataSet(new Object[][] {
         { "v" }, { 0.0 }, { 20.0 }, { 55.0 }, { 100.0 }
      });
      GradientColorFrame f = new GradientColorFrame("v");
      f.setFromColor(new Color(0, 0, 0));
      f.setToColor(new Color(200, 100, 40));
      f.init(data);
      Object[] values = f.getValues();
      double min = f.getScale().getMin();
      double max = f.getScale().getMax();
      assertTrue(values.length > 1);

      for(Object value : values) {
         double ratio = (((Number) value).doubleValue() - min) / (max - min);
         assertEquals(f.getColor(ratio), f.getColor(value), "value " + value);
      }
   }
}
