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

import inetsoft.graph.AxisSpec;
import inetsoft.graph.VGraph;
import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.graph.aesthetic.CategoricalFontFrame;
import inetsoft.graph.aesthetic.DefaultTextFrame;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.scale.*;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ticks, label values and label texts of {@link DefaultAxis}, built directly on a scale
 * without any data (user min/max/increment give fixed ticks).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome
@Tag("core")
class DefaultAxisTicksTest {
   private static final double L = 1000;
   private static final double EPS = 1e-6;

   // ---------------------------------------------------------------- fixtures

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

   private static Date date(int year, int month, int day) {
      return new GregorianCalendar(year, month - 1, day).getTime();
   }

   private static TimeScale time3() {
      TimeScale scale = new TimeScale(date(2021, 1, 1), date(2021, 4, 1));
      scale.setType(TimeScale.MONTH);
      return scale;
   }

   private static TimeScale daily() {
      TimeScale scale = new TimeScale(date(2021, 1, 1), date(2021, 3, 31));
      scale.setType(TimeScale.DAY);
      return scale;
   }

   private static DefaultAxis axis(Scale scale) {
      return axis(scale, new AffineTransform(), L);
   }

   private static DefaultAxis axis(Scale scale, AffineTransform transform, double length) {
      VGraph vg = new VGraph(new RectCoord());
      DefaultAxis axis = new DefaultAxis(scale, vg);
      axis.setLength(length);
      axis.setScreenTransform(transform);
      axis.setAxisSize(40);
      return axis;
   }

   private static void layout(DefaultAxis axis) {
      axis.layout(new Rectangle2D.Double(-100, -400, 1200, 800));
   }

   private static DecimalFormat decimal(String pattern) {
      return new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.US));
   }

   private static SimpleDateFormat dateFormat(String pattern) {
      return new SimpleDateFormat(pattern, Locale.US);
   }

   private static List<String> texts(DefaultAxis axis) {
      List<String> list = new ArrayList<>();

      for(VLabel label : axis.getLabels()) {
         list.add(label.getText());
      }

      return list;
   }

   private static List<Object> values(DefaultAxis axis) {
      List<Object> list = new ArrayList<>();

      for(VLabel label : axis.getLabels()) {
         list.add(label.getValue());
      }

      return list;
   }

   private static List<String> visibleTexts(DefaultAxis axis) {
      List<String> list = new ArrayList<>();

      for(VLabel label : axis.getLabels()) {
         if(label.getZIndex() >= 0) {
            list.add(label.getText());
         }
      }

      return list;
   }

   // ---------------------------------------------------------------- ticks and labels

   @Test
   @DisplayName("axis ticks equal the scale ticks")
   void ticksEqualScaleTicks() {
      LinearScale scale = lin();
      DefaultAxis axis = axis(scale);

      assertArrayEquals(new double[] { 0, 25, 50, 75, 100 }, axis.getTicks(), EPS);
      assertArrayEquals(scale.getTicks(), axis.getTicks(), EPS);
   }

   @Test
   @DisplayName("a secondary axis returns the ticks of its primary axis, not of its own scale")
   void secondaryAxisUsesPrimaryTicks() {
      DefaultAxis primary = axis(lin());
      DefaultAxis secondary = axis(cat("A", "B", "C"));
      secondary.setPrimaryAxis(primary);

      assertArrayEquals(new double[] { 0, 25, 50, 75, 100 }, secondary.getTicks(), EPS);
   }

   @Test
   @DisplayName("linear labels: one per tick, values in tick order, none hidden")
   void linearLabelValuesInOrder() {
      DefaultAxis axis = axis(lin());
      VLabel[] labels = axis.getLabels();

      assertEquals(5, labels.length);
      double[] expected = { 0, 25, 50, 75, 100 };

      for(int i = 0; i < labels.length; i++) {
         assertEquals(expected[i], ((Number) labels[i].getValue()).doubleValue(), EPS);
         assertTrue(labels[i].getZIndex() >= 0, "label " + i + " must not be hidden");
      }
   }

   @Test
   @DisplayName("label text follows the number format of the axis text spec")
   void labelTextUsesNumberFormat() {
      LinearScale grouped = lin();
      grouped.getAxisSpec().getTextSpec().setFormat(decimal("#,##0"));
      assertEquals(List.of("0", "25", "50", "75", "100"), texts(axis(grouped)));

      LinearScale oneDecimal = lin();
      oneDecimal.getAxisSpec().getTextSpec().setFormat(decimal("0.0"));
      assertEquals(List.of("0.0", "25.0", "50.0", "75.0", "100.0"), texts(axis(oneDecimal)));
   }

   @Test
   @DisplayName("label text without a format has no decimal part for whole numbers")
   void labelTextDefaultFormat() {
      assertEquals(List.of("0", "25", "50", "75", "100"), texts(axis(lin())));
   }

   @Test
   @DisplayName("categorical labels keep the value order, ticks sit at the cell centers")
   void categoricalLabels() {
      DefaultAxis axis = axis(cat("A", "B", "C"));

      assertEquals(List.of("A", "B", "C"), values(axis));
      assertArrayEquals(new double[] { 0.5, 1.5, 2.5 }, axis.getTicks(), EPS);
   }

   @Test
   @DisplayName("reversed categorical scale lists the values reversed, ticks are unchanged")
   void reversedCategorical() {
      CategoricalScale scale = new CategoricalScale("Cat");
      scale.setReversed(true);
      scale.init("A", "B", "C");
      DefaultAxis axis = axis(scale);

      assertEquals(List.of("C", "B", "A"), values(axis));
      assertArrayEquals(new double[] { 0.5, 1.5, 2.5 }, axis.getTicks(), EPS);
   }

   @Test
   @DisplayName("reversed linear scale: label values are the reverse of the normal axis, "
      + "tick locations still ascend inside the axis")
   void reversedLinear() {
      DefaultAxis normal = axis(lin());
      LinearScale reversedScale = lin();
      reversedScale.setReversed(true);
      DefaultAxis reversed = axis(reversedScale);

      List<Object> expected = new ArrayList<>(values(normal));
      Collections.reverse(expected);
      assertEquals(expected.size(), values(reversed).size());

      for(int i = 0; i < expected.size(); i++) {
         assertEquals(((Number) expected.get(i)).doubleValue(),
                      ((Number) values(reversed).get(i)).doubleValue(), EPS);
      }

      double[] tlocs = reversed.getTickLocations(null);

      for(int i = 0; i < tlocs.length; i++) {
         assertTrue(tlocs[i] >= -EPS && tlocs[i] <= L + EPS, "inside the axis: " + tlocs[i]);

         if(i > 0) {
            assertTrue(tlocs[i] > tlocs[i - 1], "strictly ascending");
         }
      }
   }

   @Test
   @DisplayName("time labels: one per month tick, dates ascending")
   void timeLabels() {
      TimeScale scale = time3();
      scale.getAxisSpec().getTextSpec().setFormat(dateFormat("yyyy-MM"));
      DefaultAxis axis = axis(scale);

      assertEquals(List.of("2021-01", "2021-02", "2021-03", "2021-04"), texts(axis));
      assertEquals(4, axis.getTicks().length);
      Date previous = null;

      for(Object value : values(axis)) {
         Date current = (Date) value;
         assertTrue(previous == null || current.after(previous));
         previous = current;
      }
   }

   @Test
   @DisplayName("abbreviate: the year is kept once, the following month labels drop it")
   void abbreviateTimeLabels() {
      TimeScale scale = time3();
      scale.getAxisSpec().getTextSpec().setFormat(dateFormat("yyyy MMM"));
      scale.getAxisSpec().setAbbreviate(true);

      assertEquals(List.of("2021", "Feb", "Mar", "Apr"), texts(axis(scale)));
   }

   @Test
   @DisplayName("abbreviate off: every time label shows the full formatted date")
   void abbreviateOffKeepsFullLabels() {
      TimeScale scale = time3();
      scale.getAxisSpec().getTextSpec().setFormat(dateFormat("yyyy MMM"));
      scale.getAxisSpec().setAbbreviate(false);

      assertEquals(List.of("2021 Jan", "2021 Feb", "2021 Mar", "2021 Apr"),
                   texts(axis(scale)));
   }

   @Test
   @DisplayName("abbreviate on numbers: the repeated currency prefix is dropped after the first")
   void abbreviateNumbers() {
      LinearScale scale = lin();
      scale.getAxisSpec().getTextSpec().setFormat(decimal("$#,##0"));
      scale.getAxisSpec().setAbbreviate(true);

      assertEquals(List.of("$0", "25", "50", "75", "100"), texts(axis(scale)));
   }

   @Test
   @DisplayName("daily ticks formatted as year-month leave one tick and one label per month "
      + "(#80213)")
   void dailyTicksFormattedAsMonthAreDeduplicated() {
      TimeScale scale = daily();
      scale.getAxisSpec().getTextSpec().setFormat(dateFormat("yyyy-MM"));
      DefaultAxis axis = axis(scale);
      layout(axis);
      axis.prepareLabels();

      assertEquals(3, axis.getTicks().length);
      assertEquals(List.of("2021-01", "2021-02", "2021-03"), visibleTexts(axis));
   }

   @Test
   @DisplayName("all ticks: every day stays a tick, the visible labels are still the 3 months")
   void allTicksKeepsDuplicateTicks() {
      TimeScale scale = daily();
      scale.getAxisSpec().getTextSpec().setFormat(dateFormat("yyyy-MM"));
      scale.getAxisSpec().setAllTicks(true);
      DefaultAxis axis = axis(scale);
      layout(axis);
      axis.prepareLabels();

      assertEquals(90, axis.getTicks().length);
      assertEquals(List.of("2021-01", "2021-02", "2021-03"), visibleTexts(axis));
   }

   @Test
   @DisplayName("label text comes from the alias text frame of the axis spec")
   void aliasText() {
      CategoricalScale scale = cat("A", "B", "C");
      DefaultTextFrame frame = new DefaultTextFrame();
      frame.setText("B", "Bee");
      scale.getAxisSpec().setTextFrame(frame);

      assertEquals(List.of("A", "Bee", "C"), texts(axis(scale)));
   }

   @Test
   @DisplayName("an empty label keeps its index but is hidden")
   void emptyLabelIsHidden() {
      DefaultAxis axis = axis(cat("A", "", "C"));
      VLabel[] labels = axis.getLabels();

      assertEquals(3, labels.length);
      assertTrue(labels[0].getZIndex() >= 0);
      assertTrue(labels[1].getZIndex() < 0);
      assertTrue(labels[2].getZIndex() >= 0);
   }

   @Test
   @DisplayName("an axis without categories has no ticks or labels and lays out without error")
   void noCategories() {
      DefaultAxis axis = axis(cat());

      assertEquals(0, axis.getTicks().length);
      assertEquals(0, axis.getLabels().length);
      assertEquals(0, axis.getTickLocations(null).length);
      assertDoesNotThrow(() -> layout(axis));
   }

   @Test
   @DisplayName("a single category is centered on the axis and fills one cell")
   void singleCategory() {
      DefaultAxis axis = axis(cat("A"));

      assertEquals(List.of("A"), values(axis));
      assertArrayEquals(new double[] { 0.5 }, axis.getTicks(), EPS);
      assertArrayEquals(new double[] { L / 2 }, axis.getTickLocations(null), EPS);
      assertEquals(1.0, axis.getScale().getMax(), EPS);
   }

   @Test
   @DisplayName("explicit zero-range linear scale: tick locations are finite, one label per tick")
   void zeroRangeLinear() {
      LinearScale scale = new LinearScale("m");
      scale.setMin(5);
      scale.setMax(5);
      DefaultAxis axis = axis(scale);

      for(double loc : axis.getTickLocations(null)) {
         assertFalse(Double.isNaN(loc) || Double.isInfinite(loc), "location " + loc);
      }

      assertEquals(axis.getTicks().length, axis.getLabels().length);
   }

   @Test
   @DisplayName("setScale with fewer categories rebuilds labels and ticks, layout still works")
   void shrinkCategories() {
      DefaultAxis axis = axis(cat("A", "B", "C"));
      axis.setScale(cat("A", "B"));

      assertEquals(List.of("A", "B"), values(axis));
      assertArrayEquals(new double[] { 0.5, 1.5 }, axis.getTicks(), EPS);
      assertDoesNotThrow(() -> layout(axis));
   }

   @Test
   @DisplayName("each label gets its own copy of the axis text spec")
   void labelSpecIsCloned() {
      CategoricalScale scale = cat("A", "B");
      AxisSpec spec = scale.getAxisSpec();
      spec.getTextSpec().setRotation(0);
      DefaultAxis axis = axis(scale);
      VLabel label = axis.getLabels()[0];
      Color before = spec.getTextSpec().getColor();

      assertNotSame(spec.getTextSpec(), label.getTextSpec());
      assertEquals(spec.getTextSpec().getRotation(), label.getTextSpec().getRotation(), EPS);
      assertEquals(spec.getTextSpec().getFormat(), label.getTextSpec().getFormat());

      label.getTextSpec().setColor(Color.MAGENTA);
      assertEquals(before, spec.getTextSpec().getColor());
   }

   @Test
   @DisplayName("label color and font come from the per-value frames of the axis spec")
   void labelColorAndFontFrames() {
      CategoricalScale scale = cat("A", "B");
      CategoricalColorFrame colors = new CategoricalColorFrame();
      colors.setColor("B", Color.RED);
      CategoricalFontFrame fonts = new CategoricalFontFrame();
      Font bold = new Font("Dialog", Font.BOLD, 17);
      fonts.setFont("B", bold);
      scale.getAxisSpec().setColorFrame(colors);
      scale.getAxisSpec().setFontFrame(fonts);
      DefaultAxis axis = axis(scale);

      VLabel b = axis.getLabels()[1];
      assertEquals(Color.RED, b.getTextSpec().getColor());
      assertEquals(bold, b.getTextSpec().getFont());

      for(VLabel label : axis.getLabels()) {
         assertEquals(scale.getAxisSpec().getColor(label.getValue()),
                      label.getTextSpec().getColor());
         assertEquals(scale.getAxisSpec().getFont(label.getValue()),
                      label.getTextSpec().getFont());
      }
   }

   @Test
   @DisplayName("truncate flag of the axis spec: truncate on means no wrapping, off means wrapping")
   void truncateAndWrappingFlags() {
      CategoricalScale truncated = cat("A");
      truncated.getAxisSpec().setTruncate(true);
      VLabel on = axis(truncated).getLabels()[0];
      assertTrue(on.isTruncate());
      assertFalse(on.isWrapping());

      CategoricalScale wrapped = cat("A");
      wrapped.getAxisSpec().setTruncate(false);
      VLabel off = axis(wrapped).getLabels()[0];
      assertFalse(off.isTruncate());
      assertTrue(off.isWrapping());
   }

   @Test
   @DisplayName("abbreviate on a categorical scale of dates still shows the formatted date")
   void abbreviateOnNonTimeScale() {
      CategoricalScale scale = cat(date(2021, 1, 1), date(2021, 2, 1), date(2021, 3, 1));
      scale.getAxisSpec().getTextSpec().setFormat(dateFormat("yyyy-MM-dd"));
      scale.getAxisSpec().setAbbreviate(true);
      DefaultAxis[] axis = new DefaultAxis[1];

      assertDoesNotThrow(() -> axis[0] = axis(scale));
      assertEquals("2021-01-01", axis[0].getLabels()[0].getText());
   }

   // ---------------------------------------------------------------- tick locations

   @Test
   @DisplayName("tick locations are scaled to the axis length")
   void tickLocations() {
      assertArrayEquals(new double[] { 0, 250, 500, 750, 1000 },
                        axis(lin()).getTickLocations(null), EPS);
   }

   @Test
   @DisplayName("tick locations follow a changed axis length")
   void tickLocationsAfterSetLength() {
      DefaultAxis axis = axis(lin());
      axis.setLength(500);

      assertArrayEquals(new double[] { 0, 125, 250, 375, 500 },
                        axis.getTickLocations(null), EPS);
   }

   @Test
   @DisplayName("computing tick locations does not change the ticks of the scale")
   void tickLocationsDoNotMutateScale() {
      LinearScale scale = lin();
      DefaultAxis axis = axis(scale);
      double[] first = axis.getTickLocations(null);
      double[] second = axis.getTickLocations(null);

      assertArrayEquals(first, second, EPS);
      assertArrayEquals(new double[] { 0, 25, 50, 75, 100 }, scale.getTicks(), EPS);
   }

   @Test
   @DisplayName("setAxisType reaches the existing labels")
   void axisTypeReachesLabels() {
      DefaultAxis axis = axis(lin());
      axis.setAxisType("y");

      assertEquals("y", axis.getAxisType());

      for(VLabel label : axis.getLabels()) {
         assertEquals("y", ((VDimensionLabel) label).getAxisType());
      }
   }

   @Test
   @DisplayName("log axis: labels are the powers of the base, ticks are the exponents")
   void logAxisLabels() {
      LogScale scale = new LogScale(1, 1000);
      scale.setScaleOption(Scale.TICKS);
      DefaultAxis axis = axis(scale);

      assertArrayEquals(new double[] { 0, 1, 2, 3 }, axis.getTicks(), EPS);
      assertEquals(4, axis.getLabels().length);
      double[] expected = { 1, 10, 100, 1000 };

      for(int i = 0; i < expected.length; i++) {
         assertEquals(expected[i], ((Number) axis.getLabels()[i].getValue()).doubleValue(), EPS);
      }
   }
}
