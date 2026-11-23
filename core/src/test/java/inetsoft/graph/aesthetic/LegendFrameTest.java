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
package inetsoft.graph.aesthetic;

import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.LinearScale;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.util.CoreTool;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.text.DecimalFormat;
import java.text.Format;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The parts of visual frames that the legend depends on: which values a frame shows, whether it
 * is visible as a legend, its ids and equality, its legend spec and title, and the legend frames
 * of the stacked measures frames.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class LegendFrameTest {
   private static CategoricalColorFrame color(String field, Object... vals) {
      CategoricalColorFrame frame = new CategoricalColorFrame();
      frame.setField(field);
      frame.init(vals);
      return frame;
   }

   private static LinearSizeFrame linear(int count) {
      Object[] vals = new Object[count];

      for(int i = 0; i < count; i++) {
         vals[i] = i;
      }

      LinearSizeFrame frame = new LinearSizeFrame();
      frame.setField("m");
      frame.setScale(new LinearScale("m") {
         @Override
         public Object[] getValues() {
            return vals;
         }
      });
      return frame;
   }

   // ----------------------------------------------------------------- values

   @Test
   void linearFrameWithUpToSixValuesShowsThemAll() {
      assertArrayEquals(new Object[] { 0, 1, 2, 3, 4, 5 }, linear(6).getValues());
   }

   @Test
   void linearFrameWithAnOddNumberOfValuesShowsEveryOtherOne() {
      assertArrayEquals(new Object[] { 0, 2, 4, 6 }, linear(7).getValues());
   }

   @Test
   void linearFrameWithAnEvenNumberOfValuesAveragesTheInnerPairs() {
      assertArrayEquals(new Object[] { 0, 1.5, 3.5, 5.5, 7 }, linear(8).getValues());
      assertArrayEquals(new Object[] { 0, 1.5, 3.5, 5.5, 7.5, 9 }, linear(10).getValues());
   }

   @Test
   void categoricalFrameKeepsAllItsValues() {
      Object[] vals = new Object[10];

      for(int i = 0; i < vals.length; i++) {
         vals[i] = "v" + i;
      }

      assertArrayEquals(vals, color("S", vals).getValues());
   }

   // ------------------------------------------------------------- visibility

   @Test
   void frameIsVisibleOnlyWhileItsLegendSpecIsVisible() {
      CategoricalColorFrame frame = color("S", "A", "B", "C");
      assertTrue(frame.isVisible());

      frame.getLegendSpec().setVisible(false);
      assertFalse(frame.isVisible());

      frame.getLegendSpec().setVisible(true);
      assertTrue(frame.isVisible());
   }

   @Test
   void frameWithoutFieldNeedsMoreThanOneValueToBeVisible() {
      assertFalse(color(null, "A").isVisible());
      assertTrue(color(null, "A", "B").isVisible());
      assertTrue(color("S", "A").isVisible());
   }

   // -------------------------------------------------------------------- ids

   @Test
   void shareIdIgnoresTheOrderOfTheValuesAndUniqueIdKeepsIt() {
      CategoricalColorFrame ab = color("S", "A", "B");
      CategoricalColorFrame ba = color("S", "B", "A");

      assertEquals(ab.getShareId(), ba.getShareId());
      assertNotEquals(ab.getUniqueId(), ba.getUniqueId());
   }

   @Test
   void framesOfTheSameFieldAndValuesAreEqualAndOtherFieldsAreNot() {
      assertEquals(color("S", "A", "B"), color("S", "A", "B"));
      assertNotEquals(color("S", "A", "B"), color("T", "A", "B"));
   }

   // ----------------------------------------------------------- legend spec

   @Test
   @Disabled("Bug #82108 (unused code, delete it or fix it: production sets the format on the legend spec directly): createLegendSpec sets the axis format on the original spec after cloning, the returned spec has text format null (expected the axis format 0.0)")
   void legendTextFormatIsTakenFromTheAxisFormatOfTheScale() {
      CategoricalColorFrame frame = new CategoricalColorFrame("S");
      CategoricalScale scale = new CategoricalScale();
      scale.init("A", "B");
      Format format = new DecimalFormat("0.0");
      scale.getAxisSpec().getTextSpec().setFormat(format);
      frame.setScale(scale);

      assertSame(format, frame.getLegendSpec().getTextSpec().getFormat());
   }

   @Test
   @Disabled("Bug #82108 (unused code, delete it or fix it: production sets the format on the legend spec directly): legend spec is cached before the scale is set and never updated, text format is null (expected the axis format 0.0)")
   void legendSpecReadBeforeTheScaleIsSetStillGetsTheAxisFormatLater() {
      CategoricalColorFrame frame = new CategoricalColorFrame("S");
      frame.getLegendSpec();

      CategoricalScale scale = new CategoricalScale();
      scale.init("A", "B");
      Format format = new DecimalFormat("0.0");
      scale.getAxisSpec().getTextSpec().setFormat(format);
      frame.setScale(scale);

      assertSame(format, frame.getLegendSpec().getTextSpec().getFormat());
   }

   @Test
   void titleIsTheLegendTitleThenTheFieldThenTheFrameType() {
      CategoricalColorFrame frame = color("State", "A", "B");
      assertEquals("State", frame.getTitle());

      frame.getLegendSpec().setTitle("T");
      assertEquals("T", frame.getTitle());

      assertEquals("Color", color(null, "A", "B").getTitle());

      CategoricalSizeFrame size = new CategoricalSizeFrame();
      CategoricalShapeFrame shape = new CategoricalShapeFrame();
      CategoricalTextureFrame texture = new CategoricalTextureFrame();
      CategoricalLineFrame line = new CategoricalLineFrame();

      assertEquals("Size", size.getTitle());
      assertEquals("Shape", shape.getTitle());
      assertEquals("Texture", texture.getTitle());
      assertEquals("Line", line.getTitle());
   }

   // ------------------------------------------------------- stacked measures

   private static StackedMeasuresColorFrame stacked(ColorFrame deflt, CategoricalColorFrame... frames) {
      StackedMeasuresColorFrame stacked = new StackedMeasuresColorFrame(deflt);

      for(int i = 0; i < frames.length; i++) {
         stacked.setFrame("m" + (i + 1), frames[i]);
      }

      return stacked;
   }

   @Test
   void stackedMeasuresOfOneCategoricalFieldAreCombinedIntoTheStackedFrame() {
      StackedMeasuresColorFrame stacked =
         stacked(null, color("F", "A", "B"), color("F", "A", "B"));
      VisualFrame[] legends = stacked.getLegendFrames();

      assertEquals(1, legends.length);
      assertSame(stacked, legends[0]);
   }

   @Test
   void stackedMeasuresOfDifferentFieldsAreSplitIntoTheirOwnFrames() {
      CategoricalColorFrame first = color("F1", "A", "B");
      CategoricalColorFrame second = color("F2", "A", "B");
      StackedMeasuresColorFrame stacked = stacked(null, first, second);
      Set<VisualFrame> legends = Arrays.stream(stacked.getLegendFrames())
         .collect(Collectors.toSet());

      assertEquals(Set.of(first, second), legends);
      assertFalse(legends.contains(stacked));
   }

   @Test
   void stackedMeasuresOfACategoricalAndALinearFrameAreSplitEvenForOneField() {
      CategoricalColorFrame categorical = color("F", "A", "B");
      HeatColorFrame linear = new HeatColorFrame("F");
      StackedMeasuresColorFrame stacked = new StackedMeasuresColorFrame(null);
      stacked.setFrame("m1", categorical);
      stacked.setFrame("m2", linear);
      Set<VisualFrame> legends = Arrays.stream(stacked.getLegendFrames())
         .collect(Collectors.toSet());

      assertEquals(Set.of(categorical, linear), legends);
   }

   @Test
   void stackedMeasuresWithoutFramesShowTheLinearDefaultOrTheStackedFrame() {
      HeatColorFrame heat = new HeatColorFrame("F");
      StackedMeasuresColorFrame withHeat = new StackedMeasuresColorFrame(heat);
      assertArrayEquals(new VisualFrame[] { heat }, withHeat.getLegendFrames());

      StackedMeasuresColorFrame withCategorical =
         new StackedMeasuresColorFrame(color("F", "A", "B"));
      assertArrayEquals(new VisualFrame[] { withCategorical }, withCategorical.getLegendFrames());
   }

   @Test
   void stackedMeasuresTitleAndValuesCombineTheMeasureFrames() {
      StackedMeasuresColorFrame stacked =
         stacked(null, color("a", "A", "B"), color("b", "B", "C"));

      assertEquals("a/b", stacked.getTitle());
      assertArrayEquals(new Object[] { "A", "B", "C" }, stacked.getValues());

      stacked.getLegendSpec().setTitle("Own");
      assertEquals("Own", stacked.getTitle());
   }

   @Test
   void stackedMeasuresVisibilityFollowsTheLegendSpecAndTheDefaultFrame() {
      StackedMeasuresColorFrame withFrames = stacked(null, color("a", "A", "B"));
      assertTrue(withFrames.isVisible());
      withFrames.getLegendSpec().setVisible(false);
      assertFalse(withFrames.isVisible());

      // a static default frame is not a legend
      assertFalse(new StackedMeasuresColorFrame(new StaticColorFrame()).isVisible());

      StackedMeasuresColorFrame noDefault = new StackedMeasuresColorFrame(null);
      noDefault.getLegendSpec().setVisible(false);
      assertFalse(noDefault.isVisible());
   }

   @Test
   void stackedMeasuresShareIdIsFixedWithoutDefaultFrameAndOwnWithOne() {
      assertEquals("stacked-measure", new StackedMeasuresColorFrame(null).getShareId());

      StackedMeasuresColorFrame withDefault =
         new StackedMeasuresColorFrame(color("S", "A", "B"));

      assertNotEquals("stacked-measure", withDefault.getShareId());
      assertEquals(withDefault.getField() + ":" + CoreTool.arrayToString(withDefault.getValues()),
                   withDefault.getShareId());
   }
}
