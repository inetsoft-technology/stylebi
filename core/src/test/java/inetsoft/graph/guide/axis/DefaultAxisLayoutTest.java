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
package inetsoft.graph.guide.axis;

import inetsoft.graph.VGraph;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.scale.Scale;
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

import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Layout geometry, sizes and orientation of {@link DefaultAxis}. Sizes are asserted as relations
 * to the label sizes read from the labels themselves, so they hold for any font.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class DefaultAxisLayoutTest {
   private static final double L = 1000;
   private static final double TOL = 0.01;
   private static final double[] LIN_TLOCS = { 0, 250, 500, 750, 1000 };

   // ---------------------------------------------------------------- fixtures

   private static AffineTransform hz() {
      return new AffineTransform();
   }

   private static AffineTransform vt() {
      return new AffineTransform(0, 1, 1, 0, 0, 0);
   }

   private static AffineTransform vr() {
      return AffineTransform.getRotateInstance(Math.PI / 2);
   }

   private static LinearScale lin() {
      LinearScale scale = new LinearScale("m");
      scale.setMin(0);
      scale.setMax(100);
      scale.setIncrement(25);
      return scale;
   }

   private static CategoricalScale cat(Object... values) {
      CategoricalScale scale = new CategoricalScale("Cat");
      scale.init(values);
      return scale;
   }

   private static CategoricalScale catFill(Object... values) {
      CategoricalScale scale = cat(values);
      scale.setFill(true);
      scale.setGrid(true);
      return scale;
   }

   private static TimeScale time3() {
      TimeScale scale = new TimeScale(new GregorianCalendar(2021, 0, 1).getTime(),
                                      new GregorianCalendar(2021, 3, 1).getTime());
      scale.setType(TimeScale.MONTH);
      return scale;
   }

   private static DefaultAxis axis(Scale scale, AffineTransform transform, double size) {
      VGraph vg = new VGraph(new RectCoord());
      DefaultAxis axis = new DefaultAxis(scale, vg);
      axis.setLength(L);
      axis.setScreenTransform(transform);
      axis.setAxisSize(size);
      return axis;
   }

   private static void layout(DefaultAxis axis) {
      axis.layout(new Rectangle2D.Double(-300, -300, 1600, 1600));
   }

   private static double maxLabelWidth(DefaultAxis axis) {
      double max = 0;

      for(VLabel label : axis.getLabels()) {
         max = Math.max(max, label.getPreferredWidth());
      }

      return max;
   }

   private static double maxLabelHeight(DefaultAxis axis) {
      double max = 0;

      for(VLabel label : axis.getLabels()) {
         max = Math.max(max, label.getPreferredHeight());
      }

      return max;
   }

   private static DecimalFormat decimal(String pattern) {
      return new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.US));
   }

   private static List<VLabel> visible(DefaultAxis axis) {
      List<VLabel> list = new ArrayList<>();

      for(VLabel label : axis.getLabels()) {
         if(label.getZIndex() >= 0) {
            list.add(label);
         }
      }

      return list;
   }

   // ---------------------------------------------------------------- label placement

   @Test
   @DisplayName("horizontal axis: label centers sit on their ticks, one fifth of the axis wide")
   void horizontalLabelCenters() {
      DefaultAxis axis = axis(lin(), hz(), 40);
      layout(axis);
      VLabel[] labels = axis.getLabels();

      for(int i = 0; i < labels.length; i++) {
         Rectangle2D box = labels[i].getBounds();
         assertEquals(LIN_TLOCS[i], box.getCenterX(), TOL, "label " + i);
         assertEquals(200, box.getWidth(), TOL, "label " + i);
      }
   }

   @Test
   @DisplayName("horizontal axis: labels lie below the axis line and tick, within the axis size")
   void horizontalLabelVerticalPosition() {
      DefaultAxis axis = axis(lin(), hz(), 40);
      layout(axis);

      for(VLabel label : axis.getLabels()) {
         Rectangle2D box = label.getBounds();
         assertEquals(-41, box.getY(), TOL);
         assertEquals(34, box.getHeight(), TOL);
      }
   }

   @Test
   @DisplayName("vertical axis, labels left: labels end 4 px left of the line (tick length)")
   void verticalLabelsLeft() {
      DefaultAxis axis = axis(lin(), vt(), 60);
      layout(axis);
      VLabel[] labels = axis.getLabels();

      for(int i = 0; i < labels.length; i++) {
         Rectangle2D box = labels[i].getBounds();
         assertEquals(-4, box.getMaxX(), TOL);
         assertEquals(53, box.getWidth(), TOL);
         assertEquals(LIN_TLOCS[i], box.getCenterY(), TOL);
      }
   }

   @Test
   @DisplayName("vertical axis, labels right: labels start 4 px right of the line")
   void verticalLabelsRight() {
      DefaultAxis axis = axis(lin(), vr(), 60);
      layout(axis);
      VLabel[] labels = axis.getLabels();

      for(int i = 0; i < labels.length; i++) {
         Rectangle2D box = labels[i].getBounds();
         assertEquals(4, box.getMinX(), TOL);
         assertEquals(53, box.getWidth(), TOL);
         assertEquals(LIN_TLOCS[i], box.getCenterY(), TOL);
      }
   }

   @Test
   @DisplayName("hidden ticks shrink the gap between line and labels to the label gap")
   void hiddenTickShrinksGap() {
      DefaultAxis axis = axis(lin(), vt(), 60);
      axis.setTickVisible(false);
      // the setter rebuilds the visuals, which start with an identity transform
      axis.setScreenTransform(vt());
      layout(axis);

      for(VLabel label : axis.getLabels()) {
         Rectangle2D box = label.getBounds();
         assertEquals(-2, box.getMaxX(), TOL);
         assertEquals(57, box.getWidth(), TOL);
      }
   }

   @Test
   @DisplayName("an explicit label gap replaces the default gap of 2")
   void explicitLabelGap() {
      LinearScale scale = lin();
      scale.getAxisSpec().setLabelGap(6);
      DefaultAxis axis = axis(scale, vt(), 60);
      axis.setTickVisible(false);
      axis.setScreenTransform(vt());
      layout(axis);

      for(VLabel label : axis.getLabels()) {
         Rectangle2D box = label.getBounds();
         assertEquals(-6, box.getMaxX(), TOL);
         assertEquals(53, box.getWidth(), TOL);
      }
   }

   @Test
   @DisplayName("ticks not pointing down: labels use the plain label gap, the line is 1 px high")
   void tickDownOff() {
      DefaultAxis axis = axis(lin(), vt(), 60);
      axis.setTickDown(false);
      layout(axis);

      assertEquals(1, axis.getAxisLine().getHeight(), TOL);

      for(VLabel label : axis.getLabels()) {
         assertEquals(-2, label.getBounds().getMaxX(), TOL);
      }
   }

   @Test
   @DisplayName("hiding the min and max tick hides the first and last label only")
   void minMaxTickHidden() {
      DefaultAxis axis = axis(lin(), hz(), 40);
      axis.setMinTickVisible(false);
      axis.setMaxTickVisible(false);
      layout(axis);
      VLabel[] labels = axis.getLabels();

      assertTrue(labels[0].getZIndex() < 0);
      assertTrue(labels[4].getZIndex() < 0);

      for(int i = 1; i < 4; i++) {
         assertTrue(labels[i].getZIndex() >= 0, "label " + i);
      }
   }

   @Test
   @DisplayName("center label on a filled categorical axis moves the ticks into the cell centers")
   void centerLabelFilled() {
      DefaultAxis centered = axis(catFill("A", "B", "C"), hz(), 40);
      centered.setCenterLabel(true);
      DefaultAxis plain = axis(catFill("A", "B", "C"), hz(), 40);

      assertArrayEquals(new double[] { L / 6, L / 2, 5 * L / 6 },
                        centered.getTickLocations(null), TOL);
      assertArrayEquals(new double[] { 0, L / 3, 2 * L / 3 },
                        plain.getTickLocations(null), TOL);
   }

   @Test
   @DisplayName("center label on a weighted filled axis centers each label in its weighted cell")
   void centerLabelWeighted() {
      CategoricalScale scale = catFill("A", "B", "C");
      scale.setWeight("B", 2);
      DefaultAxis axis = axis(scale, hz(), 40);
      axis.setCenterLabel(true);

      assertArrayEquals(new double[] { 0.125 * L, 0.5 * L, 0.875 * L },
                        axis.getTickLocations(null), TOL);
   }

   @Test
   @DisplayName("weighted categorical axis: label widths and centers follow the weights")
   void weightedLabelWidths() {
      CategoricalScale scale = cat("A", "B", "C");
      scale.setWeight("B", 2);
      DefaultAxis axis = axis(scale, hz(), 40);
      layout(axis);
      VLabel[] labels = axis.getLabels();
      double[] centers = { 125, 500, 875 };
      double[] widths = { 200, 400, 200 };

      for(int i = 0; i < 3; i++) {
         Rectangle2D box = labels[i].getBounds();
         assertEquals(centers[i], box.getCenterX(), TOL, "center " + i);
         assertEquals(widths[i], box.getWidth(), TOL, "width " + i);
      }
   }

   @Test
   @DisplayName("a single label fills the whole axis")
   void singleLabelFillsAxis() {
      DefaultAxis axis = axis(cat("A"), hz(), 40);
      layout(axis);
      Rectangle2D box = axis.getLabels()[0].getBounds();

      assertEquals(L, box.getWidth(), TOL);
      assertEquals(L / 2, box.getCenterX(), TOL);
   }

   @Test
   @DisplayName("an axis without categories lays out without error and is only line plus tick high")
   void emptyAxisLayout() {
      DefaultAxis axis = axis(cat(), hz(), 40);

      assertDoesNotThrow(() -> layout(axis));
      assertEquals(0, axis.getLabels().length);
      assertEquals(5, axis.getPreferredHeight(), TOL);
   }

   // ---------------------------------------------------------------- sizes

   @Test
   @DisplayName("hidden labels: min and preferred height are the line plus tick (5)")
   void hiddenLabelsHeight() {
      DefaultAxis axis = axis(lin(), hz(), 40);
      axis.setLabelVisible(false);

      assertEquals(5, axis.getPreferredHeight(), TOL);
      assertEquals(5, axis.getMinHeight(), TOL);
   }

   @Test
   @DisplayName("no labels and no line: preferred height is 0")
   void noLabelsNoLineHeight() {
      DefaultAxis axis = axis(lin(), hz(), 40);
      axis.setLabelVisible(false);
      axis.setLineVisible(false);

      assertEquals(0, axis.getPreferredHeight(), TOL);
   }

   @Test
   @DisplayName("horizontal height = tallest label + label gap 2 + line and tick 5")
   void horizontalHeight() {
      DefaultAxis axis = axis(lin(), hz(), 40);
      double expected = Math.ceil(maxLabelHeight(axis) + 2 + 5);

      assertEquals(expected, axis.getPreferredHeight(), TOL);
      assertEquals(expected, axis.getMinHeight(), TOL);
   }

   @Test
   @DisplayName("vertical width = widest label + label gap 2 + line and tick 5")
   void verticalWidth() {
      DefaultAxis axis = axis(lin(), vt(), 60);
      double expected = Math.ceil(maxLabelWidth(axis) + 2 + 5);

      assertEquals(expected, axis.getPreferredWidth(), TOL);
      assertEquals(expected, axis.getMinWidth(), TOL);
   }

   @Test
   @DisplayName("vertical height: 5 labels plus spacing 2 preferred, 3 labels plus spacing 1 minimum")
   void verticalHeight() {
      DefaultAxis axis = axis(lin(), vt(), 60);
      double h = maxLabelHeight(axis);

      assertEquals(Math.ceil(5 * h) + 8, axis.getPreferredHeight(), TOL);
      assertEquals(Math.ceil(3 * h) + 4, axis.getMinHeight(), TOL);
   }

   @Test
   @DisplayName("horizontal width: 5 labels plus spacing 2 preferred, 3 labels plus spacing 1 minimum")
   void horizontalWidth() {
      DefaultAxis axis = axis(lin(), hz(), 40);
      double w = maxLabelWidth(axis);

      assertEquals(Math.ceil(5 * w) + 8, axis.getPreferredWidth(), TOL);
      assertEquals(Math.ceil(3 * w) + 4, axis.getMinWidth(), TOL);
   }

   @Test
   @DisplayName("categorical axis keeps all labels in the minimum width")
   void categoricalKeepsAllLabels() {
      DefaultAxis axis = axis(cat("A", "B", "C"), hz(), 40);
      double w = maxLabelWidth(axis);

      assertEquals(Math.ceil(3 * w) + 4, axis.getPreferredWidth(), TOL);
      assertEquals(Math.ceil(3 * w) + 2, axis.getMinWidth(), TOL);
   }

   @Test
   @DisplayName("rotated labels add no spacing between labels")
   void rotatedLabelsNoSpacing() {
      LinearScale scale = lin();
      scale.getAxisSpec().getTextSpec().setRotation(90);
      DefaultAxis axis = axis(scale, vt(), 60);
      double hr = maxLabelHeight(axis);

      assertEquals(Math.ceil(5 * hr), axis.getPreferredHeight(), TOL);
   }

   @Test
   @DisplayName("a fixed axis size in the axis spec overrides the label based size")
   void fixedAxisSize() {
      LinearScale vscale = lin();
      vscale.getAxisSpec().setAxisSize(55);
      DefaultAxis vertical = axis(vscale, vt(), 60);
      assertEquals(55, vertical.getPreferredWidth(), TOL);
      assertEquals(55, vertical.getMinWidth(), TOL);

      LinearScale hscale = lin();
      hscale.getAxisSpec().setAxisSize(55);
      DefaultAxis horizontal = axis(hscale, hz(), 40);
      assertEquals(55, horizontal.getPreferredHeight(), TOL);
      assertEquals(55, horizontal.getMinHeight(), TOL);
      assertEquals(axis(lin(), hz(), 40).getPreferredWidth(), horizontal.getPreferredWidth(), TOL);
   }

   @Test
   @DisplayName("each extra label line adds one line height to the preferred axis height")
   void multiLineLabels() {
      DefaultAxis single = axis(cat("A"), hz(), 40);
      DefaultAxis multi = axis(cat("A\nB\nC"), hz(), 40);
      double line = single.getLabels()[0].getPreferredHeight();

      assertEquals(2 * line, multi.getPreferredHeight() - single.getPreferredHeight(), TOL);
   }

   static Stream<Arguments> scaleAndOrientation() {
      List<Arguments> list = new ArrayList<>();

      for(boolean horizontal : new boolean[] { true, false }) {
         list.add(Arguments.of("linear", (Supplier<Scale>) DefaultAxisLayoutTest::lin, horizontal));
         list.add(Arguments.of("categorical",
                               (Supplier<Scale>) () -> cat("A", "B", "C"), horizontal));
         list.add(Arguments.of("time", (Supplier<Scale>) DefaultAxisLayoutTest::time3, horizontal));
      }

      return list.stream();
   }

   @ParameterizedTest(name = "{0} horizontal={2}")
   @MethodSource("scaleAndOrientation")
   @DisplayName("minimum size never exceeds preferred size when labels are shown")
   void minNotAbovePreferred(String name, Supplier<Scale> scale, boolean horizontal) {
      DefaultAxis axis = axis(scale.get(), horizontal ? hz() : vt(), horizontal ? 40 : 60);

      assertTrue(axis.getMinWidth() <= axis.getPreferredWidth(), "width");
      assertTrue(axis.getMinHeight() <= axis.getPreferredHeight(), "height");
   }

   @Test
   @DisplayName("wider labels never give a smaller preferred width")
   void widerLabelsNotSmaller() {
      DefaultAxis narrow = axis(cat("1"), vt(), 60);
      DefaultAxis wide = axis(cat("1,000,000"), vt(), 60);

      assertTrue(wide.getPreferredWidth() >= narrow.getPreferredWidth());
   }

   // ---------------------------------------------------------------- orientation and bounds

   @Test
   @DisplayName("zero length axis: tick locations are all 0, label bounds are finite")
   void zeroLength() {
      DefaultAxis axis = axis(lin(), hz(), 40);
      axis.setLength(0);

      for(double loc : axis.getTickLocations(null)) {
         assertEquals(0, loc, TOL);
      }

      assertDoesNotThrow(() -> layout(axis));

      for(VLabel label : axis.getLabels()) {
         Rectangle2D box = label.getBounds();
         assertFalse(Double.isNaN(box.getX()) || Double.isNaN(box.getY()) ||
                     Double.isNaN(box.getWidth()) || Double.isNaN(box.getHeight()));
      }
   }

   static Stream<Arguments> transforms() {
      return Stream.of(
         Arguments.of("identity", new AffineTransform(), true, false),
         Arguments.of("swap x/y", new AffineTransform(0, 1, 1, 0, 0, 0), false, true),
         Arguments.of("rotate 90", AffineTransform.getRotateInstance(Math.PI / 2), false, true),
         Arguments.of("flip y", AffineTransform.getScaleInstance(1, -1), true, false),
         Arguments.of("rotate 45", AffineTransform.getRotateInstance(Math.PI / 4), false, false));
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("transforms")
   @DisplayName("horizontal and vertical flags follow the screen transform")
   void orientationFlags(String name, AffineTransform transform, boolean horizontal,
                         boolean vertical)
   {
      DefaultAxis axis = axis(lin(), transform, 40);

      assertEquals(horizontal, axis.isHorizontal());
      assertEquals(vertical, axis.isVertical());
   }

   @Test
   @DisplayName("default bounds are the axis length by the axis size, on the label side")
   void defaultBounds() {
      Rectangle2D h = axis(lin(), hz(), 40).getBounds();
      assertEquals(new Rectangle2D.Double(0, -40, 1000, 40), h);

      Rectangle2D v = axis(lin(), vt(), 60).getBounds();
      assertEquals(-60, v.getX(), TOL);
      assertEquals(0, v.getY(), TOL);
      assertEquals(60, v.getWidth(), TOL);
      assertEquals(1000, v.getHeight(), TOL);
   }

   @Test
   @DisplayName("setBounds overrides getBounds only, label positions stay the same")
   void setBoundsOverride() {
      DefaultAxis plain = axis(lin(), hz(), 40);
      layout(plain);
      DefaultAxis overridden = axis(lin(), hz(), 40);
      Rectangle2D forced = new Rectangle2D.Double(5, 6, 700, 80);
      overridden.setBounds(forced);
      layout(overridden);

      assertEquals(forced, overridden.getBounds());

      for(int i = 0; i < 5; i++) {
         assertEquals(plain.getLabels()[i].getBounds(), overridden.getLabels()[i].getBounds());
      }
   }

   // ---------------------------------------------------------------- label overlap handling

   @Test
   @DisplayName("visible labels stay inside the outer bounds after the labels are prepared")
   void labelsInsideOuterBounds() {
      DefaultAxis axis = axis(lin(), hz(), 40);
      axis.layout(new Rectangle2D.Double(0, -200, 1000, 400));
      axis.prepareLabels();

      for(VLabel label : visible(axis)) {
         Rectangle2D box = label.getTransformedBounds().getBounds2D();
         assertTrue(box.getMinX() >= -1, "minX " + box.getMinX());
         assertTrue(box.getMaxX() <= 1001, "maxX " + box.getMaxX());
      }
   }

   @Test
   @DisplayName("labels too wide for the axis are thinned out, the remaining ones do not overlap")
   void overlappingLabelsRemoved() {
      LinearScale scale = lin();
      scale.getAxisSpec().getTextSpec().setFormat(decimal("0000000000"));
      DefaultAxis axis = axis(scale, hz(), 40);
      axis.setLength(120);
      layout(axis);
      axis.prepareLabels();
      List<VLabel> shown = visible(axis);

      assertTrue(shown.size() < 5, "some label must be hidden");
      assertTrue(shown.size() >= 1, "some label must stay");

      for(int i = 1; i < shown.size(); i++) {
         Rectangle2D previous = shown.get(i - 1).getTransformedBounds().getBounds2D();
         Rectangle2D current = shown.get(i).getTransformedBounds().getBounds2D();
         assertTrue(previous.getMaxX() <= current.getMinX(),
                    "labels " + (i - 1) + " and " + i + " overlap");
      }
   }

   @Test
   @DisplayName("short labels with enough room are all kept")
   void noRemovalWhenRoom() {
      DefaultAxis axis = axis(lin(), hz(), 40);
      layout(axis);
      axis.prepareLabels();

      assertEquals(5, visible(axis).size());
   }

   @Test
   @DisplayName("preparing the labels twice changes nothing")
   void prepareLabelsIdempotent() {
      LinearScale scale = lin();
      scale.getAxisSpec().getTextSpec().setFormat(decimal("0000000000"));
      DefaultAxis axis = axis(scale, hz(), 40);
      axis.setLength(120);
      layout(axis);
      axis.prepareLabels();
      List<Boolean> shown = new ArrayList<>();
      List<Rectangle2D> boxes = new ArrayList<>();

      for(VLabel label : axis.getLabels()) {
         shown.add(label.getZIndex() >= 0);
         boxes.add(label.getBounds());
      }

      axis.prepareLabels();

      for(int i = 0; i < axis.getLabels().length; i++) {
         assertEquals(shown.get(i), axis.getLabels()[i].getZIndex() >= 0, "label " + i);
         assertEquals(boxes.get(i), axis.getLabels()[i].getBounds(), "label " + i);
      }
   }

   // ---------------------------------------------------------------- setters and size caches

   @Test
   @DisplayName("zero length weighted categorical axis lays out, zero-width labels are hidden")
   void zeroLengthCategorical() {
      CategoricalScale scale = cat("A", "B", "C");
      scale.setWeight("B", 2);
      DefaultAxis axis = axis(scale, hz(), 40);
      axis.setLength(0);

      assertDoesNotThrow(() -> layout(axis));

      for(VLabel label : axis.getLabels()) {
         if(label.getBounds().getWidth() == 0) {
            assertTrue(label.getZIndex() < 0);
         }
      }
   }

   static Stream<Arguments> lineHeights() {
      return Stream.of(
         Arguments.of("tick down, tick and line visible", true, true, true, 5.0),
         Arguments.of("tick down, tick visible, line hidden", true, true, false, 4.0),
         Arguments.of("tick not down, line visible", false, true, true, 1.0),
         Arguments.of("tick hidden, line visible", true, false, true, 1.0),
         Arguments.of("nothing visible", false, false, false, 0.0));
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("lineHeights")
   @DisplayName("the axis line height is the smaller of axis size and line plus tick height")
   void lineHeight(String name, boolean tickDown, boolean tickVisible, boolean lineVisible,
                   double expected)
   {
      DefaultAxis axis = axis(lin(), hz(), 40);
      axis.setTickDown(tickDown);
      axis.setTickVisible(tickVisible);
      axis.setLineVisible(lineVisible);
      layout(axis);

      assertEquals(expected, axis.getAxisLine().getHeight(), TOL);
   }
}
